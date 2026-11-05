// ===========================================================
// AquaSmart — Arduino UNO R4 WiFi · servidor WiFi + 2 LEDs
// TI3042 · Sumativa Unidad 2 · D-FB50-N4-P13-C1 (Valdivia)
//
// ⚙️ ESTE es el sketch correcto para conectar el CELULAR a la
//    placa R4 WiFi (la R4 no hace Bluetooth "SPP" del HC-05:
//    el celular se conecta por WiFi con el AP de la placa).
//
// CABLEADO (el montaje del protoboard):
//   LED rojo     → D12 (pata larga) → resistencia 220Ω → riel azul → GND
//   LED amarillo → D9  (pata larga) → resistencia 220Ω → riel azul → GND
//   (el LED "L" integrado de la placa responde junto al rojo)
//   Opcional: higrómetro en A0 · bomba/relé en D8
//
// CÓMO SE CONECTA EL CELULAR:
//   1) Sube este sketch con el Arduino IDE (Board: UNO R4 WiFi).
//   2) La placa crea su Access Point:
//        SSID: AquaSmartAP     Contraseña: aquasmart123
//        IP del servidor: http://192.168.4.1
//   3) Teléfono: WiFi → AquaSmartAP → conectarse
//      (Android avisa "sin internet" → igual mantenlo conectado).
//   4) App AquaSmart Mobile → "Monitor por WiFi (Arduino UNO R4 WiFi)"
//      → IP 192.168.4.1 → Conectar → botones 💡/🌑/💡💡/🌑🌑.
//
// ENDPOINTS HTTP:
//   GET /status        → JSON {LED, LED2, bomba, T, HS, U, sensor}
//   GET /cmd?value=X   → LED_ON | LED_OFF | LED2_ON | LED2_OFF |
//                        PUMP_ON | PUMP_OFF | STATUS
//   (Sin riego automático: la demo es manual, así el estado no
//    salta solo mientras el higrómetro está desconectado.)
// ===========================================================

#include "WiFiS3.h"

const int PIN_LED_ROJO     = 12;
const int PIN_LED_AMARILLO = 9;
const int PIN_BOMBA        = 8;    // opcional (relé); sin uso en esta demo
const int PIN_SOIL         = A0;   // opcional (higrómetro) para HS real

char ssid[] = "AquaSmartAP";
char pass[] = "aquasmart123";

WiFiServer server(80);

bool ledRojo = false;
bool ledAmarillo = false;
bool bombaOn = false;

// Calibración del higrómetro (cuando lo conectes, ajusta estos dos):
const float SOIL_AIRE = 620.0;
const float SOIL_AGUA = 290.0;

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_LED_AMARILLO, OUTPUT);
  pinMode(PIN_BOMBA, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(PIN_LED_ROJO, LOW);
  digitalWrite(PIN_LED_AMARILLO, LOW);
  digitalWrite(PIN_BOMBA, LOW);

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
}

float humedadSuelo() {
  float raw = analogRead(PIN_SOIL);
  float pct = (SOIL_AIRE - raw) * 100.0 / (SOIL_AIRE - SOIL_AGUA);
  return constrain(pct, 0.0, 100.0);
}

void aplicarEstado() {
  digitalWrite(PIN_LED_ROJO, ledRojo ? HIGH : LOW);
  digitalWrite(LED_BUILTIN,  ledRojo ? HIGH : LOW);   // "L" de la placa con el rojo
  digitalWrite(PIN_LED_AMARILLO, ledAmarillo ? HIGH : LOW);
  digitalWrite(PIN_BOMBA, bombaOn ? HIGH : LOW);
}

void loop() {
  WiFiClient cliente = server.available();
  if (!cliente) return;

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

  String json = procesarPeticion(peticion);
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

String procesarPeticion(String peticion) {
  int i = peticion.indexOf("/cmd?value=");
  if (i > -1) {
    int fin = i + 11;
    while (fin < (int)peticion.length() && peticion[fin] != ' ' && peticion[fin] != '&') fin++;
    String valor = peticion.substring(i + 11, fin);

    if      (valor == "LED_ON")  ledRojo = true;
    else if (valor == "LED_OFF") ledRojo = false;
    else if (valor == "LED2_ON") ledAmarillo = true;
    else if (valor == "LED2_OFF") ledAmarillo = false;
    else if (valor == "PUMP_ON")  bombaOn = true;
    else if (valor == "PUMP_OFF") bombaOn = false;
    // "STATUS" no cambia nada, solo responde con el JSON
  }
  aplicarEstado();
  return construirJSON();
}

String construirJSON() {
  String json = "{";
  json += "\"LED\":";    json += (ledRojo     ? "true" : "false");
  json += ",\"LED2\":";  json += (ledAmarillo ? "true" : "false");
  json += ",\"bomba\":"; json += (bombaOn     ? "true" : "false");
  json += ",\"T\":20.5";                        // demo: fijo (reemplazar por DHT11 real)
  json += ",\"HS\":";    json += String(humedadSuelo(), 1);
  json += ",\"U\":35";
  json += ",\"sensor\":\"operativo\"";
  json += "}";
  return json;
}
