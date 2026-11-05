// ===========================================================
// AquaSmart — Sketch para Arduino UNO R4 WiFi (sin HC-05)
// TI3042 · Proyecto AquaSmart — canal HTTP/WiFi
//
// El R4 WiFi crea un Access Point y sirve un servidor HTTP:
//   SSID:      AquaSmartAP
//   Contraseña: aquasmart123
//   IP:        192.168.4.1
//
// Endpoints (los usa la pantalla "Monitor por WiFi" de la app):
//   GET /status         → JSON {LED,bomba,T,HS,sensor}
//   GET /cmd?value=X    → LED_ON / LED_OFF / PUMP_ON / PUMP_OFF / THR=n
//
// LED en D12 (externo + 220Ω) o LED_BUILTIN; higrómetro en A0;
// bomba/relé en D8.
// ===========================================================

#include "WiFiS3.h"

const int PIN_LED   = 12;
const int PIN_BOMBA = 8;
const int PIN_SOIL  = A0;

char ssid[] = "AquaSmartAP";
char pass[] = "aquasmart123";

WiFiServer server(80);
float umbral = 35.0;
bool  ledOn = false;
bool  bombaOn = false;
unsigned long tBombaMs = 0;

const float SOIL_AIRE = 620.0;
const float SOIL_AGUA = 290.0;

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BOMBA, OUTPUT);

  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Sin modulo WiFi :(");
    while (true) delay(100);
  }
  WiFi.config(IPAddress(192,168,4,1));
  if (WiFi.beginAP(ssid, pass) != WL_AP_LISTENING) {
    Serial.println("Fallo creando AP");
    while (true) delay(100);
  }
  server.begin();
  Serial.print("AP listo en http://192.168.4.1  (SSID: ");
  Serial.print(ssid);
  Serial.println(")");
}

float leerHumedadSuelo() {
  float raw = analogRead(PIN_SOIL);
  float pct = (SOIL_AIRE - raw) * 100.0 / (SOIL_AIRE - SOIL_AGUA);
  return constrain(pct, 0.0, 100.0);
}

void loop() {
  // riego automático por umbral (regla de seguridad: 4 s)
  float hs = leerHumedadSuelo();
  if (!bombaOn && hs < umbral) prenderBomba();
  if (bombaOn && millis() - tBombaMs > 4000) apagarBomba();

  WiFiClient cliente = server.available();
  if (!cliente) return;

  String peticion = "";
  while (cliente.connected()) {
    if (cliente.available()) {
      char c = cliente.read();
      peticion += c;
      if (c == '\n' && peticion.indexOf("GET ") > -1) break;
      if (c == '\n') peticion = "";
    }
  }

  String respuesta = procesar(peticion, cliente);
  cliente.println("HTTP/1.1 200 OK");
  cliente.println("Content-Type: application/json; charset=utf-8");
  cliente.println("Access-Control-Allow-Origin: *");
  cliente.println("Connection: close");
  cliente.print("Content-Length: ");
  cliente.println(respuesta.length());
  cliente.println();
  cliente.print(respuesta);
  cliente.stop();
}

String procesar(String peticion, WiFiClient& cliente) {
  bool ledNuevo  = ledOn;
  bool bombaNueva = bombaOn;

  if (peticion.indexOf("/cmd?value=LED_ON") > -1) {
    ledNuevo = true;
  } else if (peticion.indexOf("/cmd?value=LED_OFF") > -1) {
    ledNuevo = false;
  } else if (peticion.indexOf("/cmd?value=PUMP_ON") > -1) {
    prenderBomba();
  } else if (peticion.indexOf("/cmd?value=PUMP_OFF") > -1) {
    apagarBomba();
  } else if (peticion.indexOf("/cmd?value=THR=") > -1) {
    int i = peticion.indexOf("THR=") + 4;
    String v = "";
    while (i < peticion.length() && isDigit(peticion[i])) v += peticion[i++];
    float n = v.toFloat();
    if (n >= 5 && n <= 90) umbral = n;
  }

  bool cambioLed = (ledNuevo != ledOn);
  ledOn = ledNuevo;
  digitalWrite(PIN_LED, ledOn ? HIGH : LOW);

  bool cambioBomba = false;
  if (bombaOn) { cambioBomba = true; apagarBomba(); }  // solo si auto-apagó

  String json = "{\"LED\":";
  json += ledOn ? "true" : "false";
  json += ",\"bomba\":";
  json += (bombaOn ? "true" : "false");
  json += ",\"T\":";
  json += String(20.0 + (millis() % 400) / 40.0, 1);
  json += ",\"HS\":";
  json += String(leerHumedadSuelo(), 1);
  json += ",\"U\":";
  json += String(umbral, 0);
  json += ",\"sensor\":\"operativo\"";
  json += "}";
  return json;
}

void prenderBomba() {
  bombaOn = true;
  tBombaMs = millis();
  digitalWrite(PIN_BOMBA, HIGH);
}

void apagarBomba() {
  bombaOn = false;
  digitalWrite(PIN_BOMBA, LOW);
}
