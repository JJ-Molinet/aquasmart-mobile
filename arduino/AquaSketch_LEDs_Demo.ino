// ==========================================================
// AquaSmart — Sketch de demo: 2 LEDs controlados desde la app
// TI3042 · Sumativa Unidad 2 · D-FB50-N4-P13-C1 (Valdivia)
//
//   LED rojo      → pin D12   (comandos LED_ON / LED_OFF)
//   LED amarillo  → pin D9    (comandos LED2_ON / LED2_OFF)
//
// CABLEADO (el de tu foto, ajustado al código):
//   - cable NARANJA  → D12 → tira superior → resistencia 220Ω
//     → pata del LED rojo (cruzando el canal central)
//   - cable VERDE    → D9  → tira superior → resistencia 220Ω
//     → pata del LED amarillo
//   - la pata que quedó en la tira INFERIOR de cada LED
//     → jumper al riel azul (−) del protoboard
//   - riel azul (−)  → pin GND del Arduino
//
// CANAL: la app se conecta por Bluetooth (SPP). Configura MODO_HC05:
//   MODO_HC05 = 1 → Arduino UNO R3 + módulo HC-05:
//       TXD(HC-05) → D10 (RX del Arduino, con SoftwareSerial)
//       RXD(HC-05) ← D11 (TX del Arduino, por divisor 1k/2k)
//       VCC → 5V, GND → GND
//   MODO_HC05 = 0 → Arduino UNO R4 WiFi por Serial1 (RX0/TX0),
//       por ejemplo si conectas el HC-05 directo a 0/1.
// ==========================================================

#define MODO_HC05 1

const int PIN_LED_ROJO     = 12;
const int PIN_LED_AMARILLO = 9;

#if MODO_HC05
  #include <SoftwareSerial.h>
  SoftwareSerial bt(10, 11);     // RX = D10, TX = D11
  #define CANAL bt
#else
  #define CANAL Serial1
#endif

bool ledRojo = false;
bool ledAmarillo = false;

void setup() {
  Serial.begin(9600);
  CANAL.begin(9600);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_LED_AMARILLO, OUTPUT);
  digitalWrite(PIN_LED_ROJO, LOW);
  digitalWrite(PIN_LED_AMARILLO, LOW);
  Serial.println("AquaSmart LEDs listo. Comandos: LED_ON/LED_OFF/LED2_ON/LED2_OFF/STATUS");
}

void loop() {
  if (CANAL.available()) {
    String cmd = CANAL.readStringUntil('\n');
    cmd.trim();
    String resp = procesar(cmd);
    CANAL.println(resp);
    Serial.println(resp);
  }
}

String procesar(String cmd) {
  if (cmd == "LED_ON") {
    ledRojo = true;
    digitalWrite(PIN_LED_ROJO, HIGH);
    return "OK LED_ON";
  }
  if (cmd == "LED_OFF") {
    ledRojo = false;
    digitalWrite(PIN_LED_ROJO, LOW);
    return "OK LED_OFF";
  }
  if (cmd == "LED2_ON") {
    ledAmarillo = true;
    digitalWrite(PIN_LED_AMARILLO, HIGH);
    return "OK LED2_ON";
  }
  if (cmd == "LED2_OFF") {
    ledAmarillo = false;
    digitalWrite(PIN_LED_AMARILLO, LOW);
    return "OK LED2_OFF";
  }
  if (cmd == "STATUS") {
    String s = "STATUS;";
    s += "LED=";   s += (ledRojo ? "1" : "0");     s += ";";
    s += "LED2=";  s += (ledAmarillo ? "1" : "0");
    return s;
  }
  return "ERR comando: " + cmd;
}
