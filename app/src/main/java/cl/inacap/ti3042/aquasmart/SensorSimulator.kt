package cl.inacap.ti3042.aquasmart

import kotlin.random.Random

/**
 * Simulador del dispositivo IoT AquaSmart.
 *
 * Genera lecturas "de sensores" (temperatura °C, humedad aire %, humedad
 * suelo %) con caminata aleatoria y maneja la lógica de la bomba de riego:
 *  - si humedad suelo < umbral  → riego automático (bomba ON 3 s)
 *  - mientras riega, la humedad del suelo sube gradualmente
 */
class SensorSimulator {

    var temperatura = 19.5      // °C
    var humedadAire = 55.0      // %
    var humedadSuelo = 72.0     // %
    var umbral = 35.0           // % : bajo esto se riega
    var bombaOn = false
        private set

    private var bombaDesdeMs = 0L
    private val rnd = Random.Default

    fun step(): Lectura {
        // caminata aleatoria de sensores (siempre en rango físico)
        temperatura = clamp(temperatura + rnd.nextDouble(-0.4, 0.4), 12.0, 32.0)
        humedadAire = clamp(humedadAire + rnd.nextDouble(-1.5, 1.5), 25.0, 95.0)

        val now = System.currentTimeMillis()

        // riego manual entre comandos PUMP_ON / PUMP_OFF
        if (bombaOn) {
            humedadSuelo = clamp(humedadSuelo + rnd.nextDouble(0.5, 2.0), 0.0, 100.0)
            if (now - bombaDesdeMs >= 3000 && bombaAuto) stopBomba()
        } else {
            // el suelo se va secando lentamente
            humedadSuelo = clamp(humedadSuelo - rnd.nextDouble(0.0, 0.8), 5.0, 100.0)
            // riego automático por umbral
            if (humedadSuelo < umbral) startBomba()
        }
        return Lectura(
            ts = now,
            temperatura = round1(temperatura),
            humedadAire = round1(humedadAire),
            humedadSuelo = round1(humedadSuelo),
            bomba = bombaOn,
            umbral = round1(umbral),
            dispositivo = "AquaSmart-01"
        )
    }

    private var bombaAuto = true

    /** Comandos que llegan por Bluetooth desde el Monitor. */
    fun onCommand(cmd: String): String {
        return when {
            cmd.equals("PUMP_ON", true) -> { startBomba(auto = false); "OK PUMP_ON" }
            cmd.equals("PUMP_OFF", true) -> { stopBomba(); "OK PUMP_OFF" }
            cmd.startsWith("THR=", true) -> {
                val v = cmd.substringAfter('=').trim().toDoubleOrNull()
                if (v != null && v in 5.0..90.0) {
                    umbral = v
                    "OK THR=$v"
                } else "ERR THR fuera de rango"
            }
            cmd.equals("STATUS", true) -> "OK STATUS"
            else -> "ERR comando desconocido"
        }
    }

    private fun startBomba(auto: Boolean = true) {
        bombaOn = true
        bombaAuto = auto
        bombaDesdeMs = System.currentTimeMillis()
    }

    private fun stopBomba() {
        bombaOn = false
        bombaAuto = true
    }

    private fun clamp(v: Double, min: Double, max: Double) = v.coerceIn(min, max)
    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0
}
