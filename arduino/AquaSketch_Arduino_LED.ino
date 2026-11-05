// ===========================================================
// AquaSmart — Sketch para Arduino UNO (R3 con HC-05 o R4/Serial1)
// TI3042 · Proyecto AquaSmart · Demo Sumativa Unidad 2/3
//
// MÓDULO BLUETOOTH: HC-05 conectado a RX/TX.
//  - Arduino UNO R4 (Minima/WiFi): usa Serial1 (pines 0/1).
//  - Arduino UNO R3 clásico: cambia las 3 líneas marcadas por
//    SoftwareSerial bt(10, 11);  (RX=D10, TX=D11, divisor 1k/2k).
//
// COMANDOS QUE LA APP ENVÍA (con \n al final):
//   LED_ON   → prende el LED (D13 integrado o LED externo en D12)
//   LED_OFF  → apaga el LED
//   PUMP_ON / PUMP_OFF → controla la bomba (relé en D8)
//   THR=n    → cambia el umbral de humedad de suelo (riego automático)
//
// LECTURAS: higrómetro (humedad suelo) en A0, mapeado 0-100 %.
// Cada 2 s envía la trama que la app parsea:
//   T=23.5;HA=52.0;HS=61.3;B=0;U=45
// ===========================================================

#include <SoftwareSerial.h>  // ← coméntalo si usas R4 con Serial1

const int PIN_LED   = 12;      // LED externo con resistencia 220Ω a GND
const int PIN_BOMBA = 8;       // IN del módulo relé
const int PIN_SOIL  = A0;      // higrómetro (humedad suelo)

// Arduino UNO R3 + HC-05:
SoftwareSerial bt(10, 11);     // RX, TX
#define CONNE bt               // ← canal de comandos
// Arduino UNO R4 (usa Serial1 en pines 0/1):
// #define CONNE Serial1      // ← descomenta esta línea y borra bt

float umbral = 35.0;
bool  ledOn  = false;
bool  bombaOn = false;
unsigned long tBombaMs = 0;
unsigned long tEnvio   = 0;

// calibración típica del higrómetro capacitivo (ajustar con el real)
const float SOIL_AIRE = 620.0;
const float SOIL_AGUA = 290.0;

void setup() {
  Serial.begin(9600);
  bt.begin(9600);              // ← si usas Serial1, borra esta línea
  pinMode(PIN_LED, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT); // LED L integrado de la placa (D13)
  digitalWrite(PIN_LED, LOW);
  digitalWrite(LED_BUILTIN, LOW);
  digitalWrite(PIN_BOMBA, LOW);
}

float leerHumedadSuelo() {
  float raw = analogRead(PIN_SOIL);           // 0-1023
  float pct = (SOIL_AIRE - raw) * 100.0 / (SOIL_AIRE - SOIL_AGUA);
  return constrain(pct, 0.0, 100.0);
}

void loop() {
  // ---- comandos de la app ----
  if (CONNE.available()) {
    String cmd = CONNE.readStringUntil('\n');
    cmd.trim();
    procesarComando(cmd);
  }

  // ---- riego automático por umbral (segunda de seguridad: 4 s máx) ----
  float hs = leerHumedadSuelo();
  if (!bombaOn && hs < umbral) {
    prenderBomba();
  }
  if (bombaOn && millis() - tBombaMs > 4000) apagarBomba();

  // ---- envío de lecturas cada 2 s (trama estándar de la app) ----
  if (millis() - tEnvio > 2000) {
    tEnvio = millis();
    float t  = 20.0 + (millis() % 400) / 40.0;      // temperatura simulada (o reemplaza por DHT11)
    float ha = 50.0 + (millis() % 300) / 30.0;      // humedad aire simulada (o DHT11)
    String trama = "T=" + String(t, 1) +
                   ";HA=" + String(ha, 1) +
                   ";HS=" + String(hs, 1) +
                   ";B=" + String(bombaOn ? 1 : 0) +
                   ";U=" + String(umbral, 0) +
                   ";L=" + String(ledOn ? 1 : 0);
    CONNE.println(trama);
    Serial.println(trama);      // copia al monitor serial del PC
  }
}

void procesarComando(String cmd) {
  if (cmd == "LED_ON") {
    ledOn = true;
    digitalWrite(PIN_LED, HIGH);
    digitalWrite(LED_BUILTIN, HIGH);   // también el L de la placa (visual)
    CONNE.println("OK LED_ON");
  } else if (cmd == "LED_OFF") {
    ledOn = false;
    digitalWrite(PIN_LED, LOW);
    digitalWrite(LED_BUILTIN, LOW);
    CONNE.println("OK LED_OFF");
  } else if (cmd == "PUMP_ON") {
    prenderBomba();
    CONNE.println("OK PUMP_ON");
  } else if (cmd == "PUMP_OFF") {
    apagarBomba();
    CONNE.println("OK PUMP_OFF");
  } else if (cmd.startsWith("THR=")) {
    float v = cmd.substring(4).toFloat();
    if (v >= 5 && v <= 90) {
      umbral = v;
      CONNE.println("OK THR=" + String(umbral, 0));
    }
  } else {
    CONNE.println("ERR cmd");
  }
}

void prenderBomba() {
  bombaOn = true;
  tBombaMs = millis();
  digitalWrite(PIN_BOMBA, HIGH);   // (si el relé es activo-bajo: usa LOW)
}

void apagarBomba() {
  bombaOn = false;
  digitalWrite(PIN_BOMBA, LOW);
}
