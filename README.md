# AquaSmart Smart Riego — AquaSmart Mobile (TI3042 · Unidad 2 · Sumativa)

> **Ramo:** Aplicaciones Móviles para IoT (TI3042) — Sección D-FB50-N4-P13-C1 · Sede Valdivia
> **Estudiante:** Juanjo (individual)
> **Unidad:** 2 — Aplicaciones Móviles Android · **Sumativa (25%)**
> **Demo:** el 06-11-2026 en clase, con dos teléfonos Android (uno hace de dispositivo IoT, otro de monitor).

Aplicación Android nativa (Android Studio, Kotlin) que continúa el proyecto
**AquaSmart** (sistema de riego IoT de Valdivia validado con el docente en la
Unidad 1): simula los sensores y la bomba de riego en un teléfono y mono
telecontrol de los datos en otro, interconectados **interconectados
inalámbricamente por Bluetooth RFCOMM (SPP)**.

## Requisitos de la guía y dónde se cumplen

| Requisito de la guía U2 | Implementación |
|---|---|
| App Android Studio + Kotlin | Proyecto Gradle (AGP 8.5.2, Kotlin 1.9.24), minSdk 24 / target 34 |
| Login + autenticación (SQLite local) | `LoginActivity` + tabla `users` en SQLite — PBKDF2-HMAC-SHA-256 e intendencia anti fuerza bruta |
| Interconexión de al menos 2 dispositivos Android | `DeviceActivity` (servidor RFCOMM SPP, segúro) ↔ `MonitorActivity` (cliente RFCOMM SPP) |
| Almacenamiento y consulta de datos de sensores (SQLite) | `DbHelper.insertLectura / latestLecturas` — pantalla "Historial (SQLite)" |
| Seguridad según ISO 27400 | `Security.kt` (PBKDF2 + EncryptedSharedPreferences) + canal cifrado Bluetooth + control de acceso |

## Cómo ejecutar la demo (3 min)

1. Instala el APK en **dos teléfonos Android** (Android 7.0+).
2. Empareja los teléfonos por Bluetooth (Ajustes → Bluetooth).
3. Teléfono **A**: Ingresar (usuario demo `juanjo` / clave `aquasmart123`) →
   "Modo Dispositivo IoT" → "Activar servidor Bluetooth" (queda visible 5 min).
4. Teléfono **B**: Ingresar → "Modo Monitor" → toca el nombre de A en la lista
   → llegarán las lecturas `T=… ; HA=… ; HS=… ; B=…` cada 3 segundos.
5. Desde el Monitor: "Regar ahora" (activa la bomba), "Apagar bomba", ± umbral
   (envía `THR=n`) y "Historial (SQLite)" abre la consulta de lecturas guardadas.
   La bomba también se dispara sola si humedad suelo < umbral (riego automático).

## Seguridad implementada (ISO/IEC 27400 — infographic de la industrial)

| Control ISO 27400 | Implementación en código |
|---|---|
| Protección de credenciales y datos sensibles | Contraseñas nunca en texto claro: **PBKDF2-HMAC-SHA-256 (120 000 iteraciones, 256 bits) con sal aleatoria por usuario** → `Security.hashPassword`. |
| Almacenamiento seguro "en reposo" | La sesión va en **EncryptedSharedPreferences** (clave AES-256-GCM derivada en **Android Keystore**, diseñada para no exportarse) → `Security.securePrefs`. |
| Control de acceso y permisos | Login obligatorio con bloqueo temporal tras 3 intentos (anti fuerza bruta); sesión con expiración (30 min); comandos solo desde sesión autenticada. |
| Comunicaciones seguras | Enlace **Bluetooth RFCOMM encryptado y autenticado** (createRfcommSocketToServiceRecord / listenUsingRfcommWithServiceRecord); trama solo acepta comandos con formato validado. |
| Superficie de attack reducida | `allowBackup=false`, no redes abiertas, sin datos sensibles en logs, permisos Bluetooth solo runtime necesarios. |

Brechas residuales (honestamente, para la demo): SQLite de lecturas no está
SQLCipher (recomendación en v3), y el emparejamiento Bluetooth previo lo hace
el usuario en Ajustes — se justifica por alcance de la unidad (app Android,
no backend).

## Estructura

```
app/src/main/java/cl/inacap/ti3042/aquasmart/
├── LoginActivity.kt   → Login + registro (SQLite + PBKDF2)
├── MenuActivity.kt    → Sección rol: Dispositivo IoT / Monitor
├── DeviceActivity.kt  → Servidor Bluetooth seguro + simulador de sensores/bomba
├── MonitorActivity.kt → Cliente seguro + almacenamiento SQLite + historial
├── SensorSimulator.kt → Modelo físico simplificado (temperatura, humedad, bomba)
├── Security.kt        → PBKDF2 + EncryptedSharedPreferences (ISO 27400)
└── DbHelper.kt        → SQLite: tabla users + lecturas (almacenamiento/consulta)
```

## Demo con Arduino (según indicaciones del docente, audio 05-11)

La app además **conecta con el Arduino real y controla su LED**:

| Canal | Cómo | Comandos |
|---|---|---|
| Bluetooth (HC-05 / otro te leýfono como dispositivo) | Monitor → conectar → botones 💡/🌑 | `LED_ON`, `LED_OFF` (+ `PUMP_ON/OFF`, `THR=n`) |
| WiFi (UNO R4 WiFi) | Menú → Monitor por WiFi → IP 192.168.4.1 | `GET /cmd?value=LED_ON/OFF`, `GET /status` (JSON) |

Sketches listos para subir con el IDE de Arduino desde `arduino/`:
- `AquaSketch_Arduino_LED.ino` — LED (D12/D13), higrómetro (A0), bomba (relé D8), trama de lecturas estándar de la app.
- `AquaSketch_R4_WiFi.ino` — AP `AquaSmartAP` / `aquasmart123`, servidor HTTP con `/status` y `/cmd`.

## Build

```bash
# abre con Android Studio (JDK 17+) o desde consola:
./gradlew assembleDebug
```
Contacto: jm
