package cl.inacap.ti3042.aquasmart

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Monitor por WiFi para el Arduino UNO R4 WiFi (que trae ESP32-S3 con WiFi).
 *
 * El Arduino crea un Access Point "AquaSmartAP" y sirve un server HTTP:
 *   GET  /status        → JSON {led, bomba, temperatura, humedad_suelo, sensor}
 *   GET  /cmd?value=X   → comandos: LED_ON / LED_OFF / PUMP_ON / PUMP_OFF / THR=n
 *
 * Cabecera de demo: cumple lo mismo que el canal Bluetooth (conectar + prender
 * LED), pero por WiFi — necesario si en la clase se usa el R4 WiFi sin HC-05.
 */
class WifiArduinoActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var ip = "192.168.4.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_arduino)

        findViewById<Button>(R.id.btnWiFiConnect).setOnClickListener {
            ip = findViewById<TextView>(R.id.etIp).text.toString().trim()
            pedirEstado()
        }
        findViewById<Button>(R.id.btnLedOnWiFi).setOnClickListener { comandar("LED_ON") }
        findViewById<Button>(R.id.btnLedOffWiFi).setOnClickListener { comandar("LED_OFF") }
        findViewById<Button>(R.id.btnLed2OnWiFi).setOnClickListener { comandar("LED2_ON") }
        findViewById<Button>(R.id.btnLed2OffWiFi).setOnClickListener { comandar("LED2_OFF") }

        log("💡 Conecta el teléfono al AP del Arduino (AquaSmartAP) y toca «Conectar».")
        log("(snip) Ruta del sketch: arduino/AquaSketch_R4_WiFi.ino en el repo.")
    }

    private fun pedirEstado() {
        log("🌐 Consultando http://$ip/status …")
        scope.launch {
            try {
                val json = httpGet("http://$ip/status")
                val o = JSONObject(json)
                val estado =
                    "💡 LED=${if (o.optBoolean("LED", false)) "ON" else "off"}   " +
                        "💡💡 LED2=${if (o.optBoolean("LED2", false)) "ON" else "off"}   " +
                        "🚿 bomba=${if (o.optBoolean("bomba", false)) "ON" else "off"}\n" +
                        "🌡️ T=${o.optDouble("T", 0.0)}°C 🌱 HS=${o.optDouble("HS", 0.0)}% " +
                        "(sensor: ${o.optString("sensor", "—")})"
                conUI {
                    findViewById<TextView>(R.id.tvEstadoArduino).text = estado
                    log("✅ Estado recibido del Arduino.")
                }
            } catch (e: Exception) {
                conUI { log("❌ No se pudo conectar a http://$ip — ${e.message}")}
            }
        }
    }

    private fun comandar(cmd: String) {
        log("➡️ /cmd?value=$cmd …")
        scope.launch {
            try {
                val resp = httpGet("http://$ip/cmd?value=$cmd")
                conUI { log("💬 Arduino respondió: ${resp.trim()}") }
                pedirEstado()
            } catch (e: Exception) {
                conUI { log("❌ Error enviando $cmd: ${e.message}") }
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection()) as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.requestMethod = "GET"
        try {
            val inp = conn.inputStream.bufferedReader().readText()
            return inp
        } finally {
            conn.disconnect()
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.tvWiFiLog).append("$msg\n")
        }
    }

    private fun conUI(b: () -> Unit) {
        runOnUiThread(b)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
