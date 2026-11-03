package cl.inacap.ti3042.aquasmart

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Lectura de sensor transportada por el canal Bluetooth. */
data class Lectura(
    val ts: Long = 0L,
    val temperatura: Double = 0.0,
    val humedadAire: Double = 0.0,
    val humedadSuelo: Double = 0.0,
    val bomba: Boolean = false,
    val umbral: Double = 0.0,
    val dispositivo: String = ""
)

/**
 * Base de datos local SQLite (OPCIÓN "SQLite local" de la guía).
 *
 * Tablas:
 *  - users    → credenciales (PBKDF2 + sal). Nada en texto claro.
 *  - lecturas → almacenamiento y consulta de datos de sensores recibidos
 *               por Bluetooth desde el dispositivo IoT simulado.
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, "aquasmart.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user TEXT UNIQUE NOT NULL, " +
                "salt TEXT NOT NULL, " +
                "passwordHash TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE lecturas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "temperatura REAL NOT NULL, " +
                "humedad_aire REAL NOT NULL, " +
                "humedad_suelo REAL NOT NULL, " +
                "bomba INTEGER NOT NULL, " +
                "umbral REAL NOT NULL, " +
                "dispositivo TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        // v1: sin migraciones aún
    }

    // ---------- usuarios ----------
    fun insertUser(user: String, salt: String, passwordHash: String): Boolean {
        val cv = ContentValues().apply {
            put("user", user)
            put("salt", salt)
            put("passwordHash", passwordHash)
        }
        return try {
            writableDatabase.insertOrThrow("users", null, cv) > 0
        } catch (e: Exception) {
            false // usuario duplicado UNIQUE
        }
    }

    fun userExists(user: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT id FROM users WHERE user = ?", arrayOf(user)
        ).use { it.moveToFirst() }

    /** Devuelve (salt, hash) del usuario o null si no existe. */
    fun saltAndPassword(user: String): Pair<String, String>? =
        readableDatabase.rawQuery(
            "SELECT salt, passwordHash FROM users WHERE user = ?", arrayOf(user)
        ).use { c ->
            if (c.moveToFirst()) Pair(c.getString(0), c.getString(1)) else null
        }

    /** Validación con hash PBKDF2 (control de acceso ISO 27400). */
    fun validateLogin(user: String, password: String): Boolean {
        val stored = saltAndPassword(user) ?: return false
        val computed = Security.hashPassword(password, stored.first)
        // comparación en tiempo roughly constante (evi Strings.equals)
        return hashEquals(computed, stored.second)
    }

    private fun hashEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    // ---------- lecturas ----------
    fun insertLectura(l: Lectura): Long {
        val cv = ContentValues().apply {
            put("ts", l.ts)
            put("temperatura", l.temperatura)
            put("humedad_aire", l.humedadAire)
            put("humedad_suelo", l.humedadSuelo)
            put("bomba", if (l.bomba) 1 else 0)
            put("umbral", l.umbral)
            put("dispositivo", l.dispositivo)
        }
        return writableDatabase.insert("lecturas", null, cv)
    }

    /** Consulta de las últimas N lecturas (más reciente primero). */
    fun latestLecturas(limit: Int = 50): List<Lectura> {
        val out = mutableListOf<Lectura>()
        readableDatabase.rawQuery(
            "SELECT ts, temperatura, humedad_aire, humedad_suelo, bomba, umbral, dispositivo " +
                "FROM lecturas ORDER BY ts DESC LIMIT ?", arrayOf("$limit")
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Lectura(
                        ts = c.getLong(0),
                        temperatura = c.getDouble(1),
                        humedadAire = c.getDouble(2),
                        humedadSuelo = c.getDouble(3),
                        bomba = c.getInt(4) == 1,
                        umbral = c.getDouble(5),
                        dispositivo = c.getString(6)
                    )
                )
            }
        }
        return out
    }
}
