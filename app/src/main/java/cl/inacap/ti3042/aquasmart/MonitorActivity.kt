package cl.inacap.ti3042.aquasmart

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID
import android.bluetooth.BluetoothSocket
import androidx.core.app.ActivityCompat

/**
 * Modo Monitor: se conecta por Bluetooth RFCOMM seguro al Dispositivo
 * AquaSmart, muestra las lecturas en vivo, manda comandos (regar / apagar /
 * umbral) y PERSISTE cada lectura en SQLite para su consulta posterior.
 */
@Suppress("DEPRECATION_ERROR")
class MonitorActivity : AppCompatActivity() {

    private val db by lazy { DbHelper(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var socket: BluetoothSocket? = null
    private var writerOut: PrintWriter? = null
    private var conectado = false
    private var umbralSelecc = 35
    private var ultimoDispositivo = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val adapter: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        actualizarUmbralUI()

        val list = findViewById<ListView>(R.id.listDevices)
        findViewById<Button>(R.id.btnWater).setOnClickListener { enviarComando("PUMP_ON") }
        findViewById<Button>(R.id.btnStop).setOnClickListener { enviarComando("PUMP_OFF") }

        findViewById<Button>(R.id.btnUmbralUp).setOnClickListener {
            umbralSelecc = (umbralSelecc + 5).coerceAtMost(90)
            actualizarUmbralUI()
            enviarComando("THR=$umbralSelecc")
        }
        findViewById<Button>(R.id.btnUmbralDown).setOnClickListener {
            umbralSelecc = (umbralSelecc - 5).coerceIn(5, 90)
            actualizarUmbralUI()
            enviarComando("THR=$umbralSelecc")
        }
        findViewById<Button>(R.id.btnHistorial).setOnClickListener { mostrarHistorial() }

        if (!hasConnectPerm()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERM_REQ
            )
        }
        cargarDispositivosEmparejados(list)
    }

    /** true si se concedió BLUETOOTH_CONNECT (en Android 12+ es runtime). */
    private fun hasConnectPerm(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun actualizarUmbralUI() {
        findViewById<TextView>(R.id.tvUmbral).text = "$umbralSelecc"
    }

    private fun cargarDispositivosEmparejados(list: ListView) {
        if (!hasConnectPerm()) {
            log("⚠️ Da permiso BLUETOOTH_CONNECT y vuelve a entrar.")
            return
        }
        val emparejados = adapter.adapter.bondedDevices.toList()
        if (emparejados.isEmpty()) {
            log("ℹ️ No hay dispositivos emparejados. Empareja primero el otro teléfono.")
            return
        }
        val nombreDe: Map<String, BluetoothDevice> = emparejados.associateBy(
            { obtenerNombre(it) }, { it }
        )
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, nombreDe.keys.toList()
        )
        list.setOnItemClickListener { _, _, position, _ ->
            val nombre = list.adapter.getItem(position) as String
            val device = nombreDe[nombre]
            if (device != null) conectar(device)
        }
    }

    private fun obtenerNombre(device: BluetoothDevice): String =
        try {
            if (hasConnectPerm())
                device.name ?: device.address
            else device.address
        } catch (e: SecurityException) {
            device.address
        }

    private fun conectar(device: BluetoothDevice) {
        if (!hasConnectPerm()) {
            log("⚠️ Falta permiso BLUETOOTH_CONNECT para conectar.")
            return
        }
        log("🔗 Conectando con ${obtenerNombre(device)}…")
        scope.launch {
            try {
                // RFCOMM secure → canal cifrado + autenticado (ISO 27400)
                val s = device.createRfcommSocketToServiceRecord(DeviceActivity.SPP_UUID)
                adapter.adapter.cancelDiscovery()
                s.connect()
                socket = s
                writerOut = PrintWriter(s.outputStream, true)
                conectado = true
                ultimoDispositivo = obtenerNombre(device)
                conUI {
                    log("✅ Conectado a $ultimoDispositivo. Esperando lecturas…")
                    Toast.makeText(
                        this@MonitorActivity,
                        "Conectado a $ultimoDispositivo", Toast.LENGTH_SHORT
                    ).show()
                }
                escucharLecturas(s)
            } catch (e: SecurityException) {
                conUI { log("❌ Permiso denegado: ${e.message}") }
            } catch (e: Exception) {
                conUI { log("❌ No se pudo conectar: ${e.message}") }
            }
        }
    }

    private suspend fun escucharLecturas(s: BluetoothSocket) {
        val reader = BufferedReader(InputStreamReader(s.inputStream))
        try {
            while (scope.isActive && conectado) {
                val line = reader.readLine() ?: break
                val l = parsearTrama(line) ?: continue
                // almacenamiento de datos de sensores en SQLite
                db.insertLectura(l.copy(dispositivo = ultimoDispositivo))
                conUI {
                    findViewById<TextView>(R.id.tvLectura).text =
                        "🌡️ T=${l.temperatura} °C   💧 HA=${l.humedadAire} %\n" +
                            "🌱 HS=${l.humedadSuelo} %   🚿 ${if (l.bomba) "BOMBA ON" else "bomba off"}\n" +
                            "🎯 umbral < ${l.umbral} %   (el monitor ya guardó esto en SQLite)"
                }
            }
        } catch (e: Exception) {
            conUI { log("🔌 Canal cerrado: ${e.message}") }
        } finally {
            try { s.close() } catch (_: Exception) {}
            conectado = false
            socket = null
            writerOut = null
        }
    }

    /** Trama: T=23.5;HA=52.3;HS=58.9;B=0;U=35 */
    private fun parsearTrama(line: String): Lectura? {
        if (!line.startsWith("T=")) {
            conUI { log("💬 ${line.trim()}") }
            return null
        }
        return try {
            val mapa = line.split(";").associate {
                val kv = it.split("=", limit = 2)
                kv[0].trim() to (kv.getOrNull(1)?.trim() ?: "")
            }
            Lectura(
                ts = System.currentTimeMillis(),
                temperatura = mapa["T"]?.toDouble() ?: 0.0,
                humedadAire = mapa["HA"]?.toDouble() ?: 0.0,
                humedadSuelo = mapa["HS"]?.toDouble() ?: 0.0,
                bomba = mapa["B"] == "1",
                umbral = mapa["U"]?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun enviarComando(cmd: String) {
        if (!conectado || writerOut == null) {
            log("⚠️ Primero conéctate a un dispositivo AquaSmart.")
            return
        }
        scope.launch {
            try {
                writerOut?.println(cmd)
                conUI { log("➡️ $cmd") }
            } catch (e: Exception) {
                conUI { log("❌ Error enviando $cmd: ${e.message}") }
            }
        }
    }

    private fun mostrarHistorial() {
        val fila = db.latestLecturas(50)
        if (fila.isEmpty()) {
            Toast.makeText(this, "Sin lecturas guardadas todavía.", Toast.LENGTH_SHORT).show()
            return
        }
        val texto = fila.joinToString("\n") { l ->
            val fecha = java.text.SimpleDateFormat("dd-MM HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(l.ts))
            "$fecha   T=${l.temperatura}°C  HA=${l.humedadAire}%  HS=${l.humedadSuelo}%  bomba=${if (l.bomba) "ON" else "off"}  (${l.dispositivo})"
        }
        AlertDialog.Builder(this)
            .setTitle("Historial de ${fila.count()} lecturas (SQLite)")
            .setMessage(texto)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun log(msg: String) {
        handler.post {
            findViewById<TextView>(R.id.tvMonitorLog).append("$msg\n")
        }
    }

    private fun conUI(b: () -> Unit) {
        handler.post(b)
    }

    override fun onDestroy() {
        super.onDestroy()
        conectado = false
        try { socket?.close() } catch (_: Exception) {}
        scope.cancel()
    }

    companion object {
        const val PERM_REQ = 102
    }
}
