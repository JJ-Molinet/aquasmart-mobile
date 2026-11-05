package cl.inacap.ti3042.aquasmart

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Menú principal: elige el rol de este teléfono en la demo.
 *
 * - Modo Dispositivo IoT : este teléfono SIMULA los sensores y la bomba.
 * - Modo Monitor         : este teléfono se conecta, consulta y guarda datos.
 * Los dos instalados en dispositivos Android distintos forman la interconexión
 * inalámbrica que pide la evaluación (Bluetooth RFCOMM seguro).
 */
class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val user = intent.getStringExtra("user") ?: Security.currentUser(this) ?: "invitado"
        findViewById<TextView>(R.id.tvWelcome).text = "Hola, $user 👋"

        findViewById<Button>(R.id.btnDevice).setOnClickListener {
            startActivity(Intent(this, DeviceActivity::class.java))
        }
        findViewById<Button>(R.id.btnMonitor).setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }
        findViewById<Button>(R.id.btnWifiArduino).setOnClickListener {
            startActivity(Intent(this, WifiArduinoActivity::class.java))
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            Security.clearSession(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
