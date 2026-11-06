// ===========================================================
// AquaSmart — UNO R4 WiFi: servidor HTTP (AP) + módulo Bluetooth
// TI3042 · Sumativa Unidad 2 · doble canal: WiFi + HC-05
//
// 🔀 ESTE sketch une los dos canales que ya funcionaron:
//    1) WiFi   : Access Point AquaSmartAP + servidor HTTP
//                (app "Monitor por WiFi" → 192.168.4.1)
//    2) HC-05  : Bluetooth SPP clásico por UART
//                (app "Modo Monitor" → dispositivo emparejado "HC-05")
//    Ambos quedan VIVOS al mismo tiempo: puedes demostrar el LED
//    con la app por WiFi y CAMBIAR al canal BT en la misma demo.
//
// MÓDULO Bluetooth conectado a la placa (HC-05, 9600 baud):
//   HC-05 VCC → 5V        HC-05 GND → GND
//   HC-05 TXD → pin 0 (RX de la placa, Serial1)
//   HC-05 RXD → pin 1 (TX de la placa) CON DIVISOR 1k/2k (a 3.3V!)
//   Nota: en la R4 el "flash" se hace por USB-C, así que NO hace
//         falta desconectar el HC-05 de 0/1 al subir el sketch.
//   Ojo: usar Serial1 (hardware). SoftwareSerial no viene en el
//        core Renesas de la R4.
//
// COMANDOS (idénticos en ambos canales):
//   LED_ON / LED_OFF     → LED rojo (D12) + "L" de la placa (D13)
//   LED2_ON / LED2_OFF   → LED amarillo (D9)
//   PUMP_ON / PUMP_OFF   → relé/bomba (D8, opcional)
//   STATUS               → responde con el estado
//   WiFi responde SIEMPRE JSON;_BT responde "OK <comando>" (texto)
// ===========================================================

#include "WiFiS3.h"

const int PIN_LED_ROJO     = 12;
const int PIN_LED_AMARILLO = 9;
const int PIN_BOMBA        = 8;
const int PIN_SOIL         = A0;

char ssid[] = "AquaSmartAP";
char pass[] = "aquasmart123";

WiFiServer server(80);

bool ledRojo = false;
bool ledAmarillo = false;
bool bombaOn = false;

const float SOIL_AIRE = 620.0;
const float SOIL_AGUA = 290.0;

String lineaBT = "";   // buffer del canal Bluetooth

void setup() {
  Serial.begin(115200);
  Serial1.begin(9600);          // ← UART del HC-05 (pines 0/1)

  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_LED_AMARILLO, OUTPUT);
  pinMode(PIN_BOMBA, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  aplicarEstado();

  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Sin modulo WiFi :(");
    while (true) delay(100);
  }
  WiFi.config(IPAddress(192, 168, 4, 1));
  if (WiFi.beginAP(ssid, pass) != WL_AP_LISTENING) {
    Serial.println("Fallo creando AP");
    while (true) delay(100);
  }
  server.begin();
  Serial.print("AP listo: SSID ");
  Serial.print(ssid);
  Serial.println(" · http://192.168.4.1");
  Serial.println("Canal BT activo: HC-05 en 9600 baud.");
}

float humedadSuelo() {
  float raw = analogRead(PIN_SOIL);
  float pct = (SOIL_AIRE - raw) * 100.0 / (SOIL_AIRE - SOIL_AGUA);
  return constrain(pct, 0.0, 100.0);
}

void aplicarEstado() {
  digitalWrite(PIN_LED_ROJO, ledRojo ? HIGH : LOW);
  digitalWrite(LED_BUILTIN,  ledRojo ? HIGH : LOW);
  digitalWrite(PIN_LED_AMARILLO, ledAmarillo ? HIGH : LOW);
  digitalWrite(PIN_BOMBA, bombaOn ? HIGH : LOW);
}

// ── lógica compartida (la usan los dos canales) ──
void ejecutarComando(String valor) {
  if      (valor == "LED_ON")  ledRojo = true;
  else if (valor == "LED_OFF") ledRojo = false;
  else if (valor == "LED2_ON") ledAmarillo = true;
  else if (valor == "LED2_OFF") ledAmarillo = false;
  else if (valor == "PUMP_ON")  bombaOn = true;
  else if (valor == "PUMP_OFF") bombaOn = false;
  aplicarEstado();
}

String construirJSON() {
  String json = "{";
  json += "\"LED\":";    json += (ledRojo     ? "true" : "false");
  json += ",\"LED2\":";  json += (ledAmarillo ? "true" : "false");
  json += ",\"bomba\":"; json += (bombaOn     ? "true" : "false");
  json += ",\"T\":20.5";
  json += ",\"HS\":";    json += String(humedadSuelo(), 1);
  json += ",\"U\":35";
  json += ",\"sensor\":\"operativo\"";
  json += "}";
  return json;
}

String respuestaBT(String cmd) {
  if (cmd == "LED_ON")  { bool was = ledRojo;     ledRojo = true;     aplicarEstado(); return was ? "OK LED_ON (ya estaba ON)" : "OK LED_ON"; }
  if (cmd == "LED_OFF") { bool was = !ledRojo;    ledRojo = false;    aplicarEstado(); return was ? "OK LED_OFF (ya estaba OFF)" : "OK LED_OFF"; }
  if (cmd == "LED2_ON") { bool was = ledAmarillo; ledAmarillo = true; aplicarEstado(); return was ? "OK LED2_ON (ya estaba ON)" : "OK LED2_ON"; }
  if (cmd == "LED2_OFF"){ bool was = !ledAmarillo;  ledAmarillo = false; aplicarEstado(); return was ? "OK LED2_OFF (ya estaba OFF)" : "OK LED2_OFF"; }
  if (cmd == "PUMP_ON") { bool was = bombaOn;     bombaOn = true;     aplicarEstado(); return was ? "OK PUMP_ON (ya estaba ON)" : "OK PUMP_ON"; }
  if (cmd == "PUMP_OFF"){ bool was = !bombaOn;    bombaOn = false;    aplicarEstado(); return was ? "OK PUMP_OFF (ya estaba OFF)" : "OK PUMP_OFF"; }
  if (cmd == "STATUS") {
    return "STATUS;LED=" + String(ledRojo ? 1 : 0) + ";LED2=" +
           String(ledAmarillo ? 1 : 0) + ";B=" + String(bombaOn ? 1 : 0);
  }
  return "ERR comando: " + cmd;
}

void loop() {
  // ══════ CANAL 1: WiFi (app Monitor por WiFi) ══════
  WiFiClient cliente = server.available();
  if (cliente) {
    String peticion = "";
    bool primera = true;
    while (cliente.connected()) {
      if (!cliente.available()) continue;
      char c = cliente.read();
      if (c == '\n') {
        if (primera && peticion.indexOf("GET ") > -1) break;
        peticion = "";
        primera = false;
      } else if (c != '\r') {
        peticion += c;
      }
    }
    int i = peticion.indexOf("/cmd?value=");
    if (i > -1) {
      int fin = i + 11;
      while (fin < (int)peticion.length() && peticion[fin] != ' ' && peticion[fin] != '&') fin++;
      ejecutarComando(peticion.substring(i + 11, fin));
    }
    String json = construirJSON();
    cliente.println("HTTP/1.1 200 OK");
    cliente.println("Content-Type: application/json; charset=utf-8");
    cliente.println("Access-Control-Allow-Origin: *");
    cliente.println("Connection: close");
    cliente.print("Content-Length: ");
    cliente.println(json.length());
    cliente.println();
    cliente.print(json);
    cliente.flush();
    cliente.stop();
  }

  // ══════ CANAL 2: Bluetooth (HC-05 · app Modo Monitor) ══════
  while (Serial1.available()) {
    char c = (char)Serial1.read();
    if (c == '\n' || c == '\r') {
      if (lineaBT.length() > 0) {
        lineaBT.trim();
        String resp = respuestaBT(lineaBT);
        Serial1.println(resp);
        Serial.println("[BT] \"" + lineaBT + "\" -> " + resp);
        lineaBT = "";
      }
    } else {
      lineaBT += c;
      if (lineaBT.length() > 60) lineaBT = "";   // buffer saturado → descarta
    }
  }
}
