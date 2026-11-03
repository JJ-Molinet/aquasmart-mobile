package cl.inacap.ti3042.aquasmart

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

/**
 * Modo Dispositivo IoT: este teléfono SIMULA los sensores de AquaSmart y la
 * bomba de riego, y queda a la espera de la conexión inalámbrica del Monitor.
 *
 * Canal: Bluetooth RFCOMM (SPP) con `listenUsingRfcommWithServiceRecord`
 * → socket cifrado y autenticado (enlace seguro, ISO 27400).
 */
class DeviceActivity : AppCompatActivity() {

    private val sim = SensorSimulator()
    private val db by lazy { DbHelper(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var writerOut: PrintWriter? = null
    private var running = false

    private val adapter: BluetoothAdapter? by lazy {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)
        log("En modo dispositivo. Toca «Activar» para abrir el canal seguro.")

        findViewById<Button>(R.id.btnDeviceStart).setOnClickListener {
            iniciarServidorBluetooth()
        }

        // generador de lecturas locales (aunque nadie esté conectado)
        val tick = object : Runnable {
            override fun run() {
                val l = sim.step()
                findViewById<TextView>(R.id.tvSensores).text =
                    "🌡️ T=${l.temperatura} °C\n💧 HA=${l.humedadAire} %\n🌱 HS=${l.humedadSuelo} %\n" +
                        "🚿 ${if (l.bomba) "BOMBA ON" else "bomba off"}\n🎯 umbral < ${l.umbral} %"
                if (clientSocket != null && writerOut != null) {
                    enviarTrama(l)
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.postDelayed(tick, 3000)
    }

    private fun iniciarServidorBluetooth() {
        val a = adapter
        if (a == null) {
            log("❌ Este dispositivo no tiene Bluetooth.")
            return
        }
        if (!hasConnectPermission()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERM_REQ
            )
            return
        }
        if (!a.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        // que la demo sea encontrable 5 min
        val discoverable = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverable)

        Thread {
            try {
                val s = a.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME, SPP_UUID
                )
                serverSocket = s
                log("🛰️ Servidor RFCOMM seguro abierto. Esperando al Monitor…")
                val socket = s.accept() // bloqueante hasta que conecte el Monitor
                if (!hasConnectPermission()) {
                    log("❌ Permiso BLUETOOTH_CONNECT revocado.")
                    socket.close()
                    return@Thread
                }
                clientSocket = socket
                writerOut = PrintWriter(socket.outputStream, true)
                log("✅ Monitor conectado: ${socket.remoteDevice.name}")
                running = true

                // lector de comandos del Monitor
                val reader: BufferedReader =
                    BufferedReader(InputStreamReader(socket.inputStream))
                while (running) {
                    val line = reader.readLine() ?: break
                    val respuesta = sim.onCommand(line.trim())
                    log("⬅️ ${line.trim()} → $respuesta")
                    writerOut?.println(respuesta)
                }
            } catch (e: SecurityException) {
                log("❌ Permiso denegado: ${e.message}")
            } catch (e: Exception) {
                log("🔌 Conexión terminada: ${e.message}")
            } finally {
                cerrarTodo()
                log("ℹ️ Servidor detenido.")
            }
        }.start()
    }

    private fun enviarTrama(l: Lectura) {
        val trama = "T=${l.temperatura};HA=${l.humedadAire};HS=${l.humedadSuelo};B=${if (l.bomba) 1 else 0};U=${l.umbral}"
        writerOut?.println(trama)
    }

    private fun log(msg: String) {
        handler.post {
            val tv = findViewById<TextView>(R.id.tvDeviceLog)
            tv.append("$msg\n")
        }
    }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun cerrarTodo() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        clientSocket = null
        writerOut = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cerrarTodo()
    }

    companion object {
        const val SERVICE_NAME = "AquaSmartRiego"
        /** UUID del perfil SPP (Serial Port Profile) estándar Bluetooth. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val PERM_REQ = 101
    }
}
