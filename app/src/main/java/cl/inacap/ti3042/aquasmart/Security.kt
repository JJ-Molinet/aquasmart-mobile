package cl.inacap.ti3042.aquasmart

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Utilidades de seguridad — AquaSmart Mobile.
 *
 * Lineamientos aplicados (ISO/IEC 27400 — Ciberseguridad IoT):
 *  - [hashPassword]: cifrado de credenciales. La contraseña NUNCA se guarda en
 *    texto claro: se guarda una derivación PBKDF2-HMAC-SHA-256 con sal aleatoria.
 *  - [session]: la sesión del usuario se guarda en EncryptedSharedPreferences
 *    (clave AES-256 protegida por Android Keystore, datos cifrados en reposo).
 *  - El control de acceso se resuelve en LoginActivity + DbHelper.validate():
 *    sin credenciales válidas no hay conexión ni comandos disponibles.
 */
object Security {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private const val FILE_SECURE = "aquasmart_secure"

    /** Sal aleatoria por usuario (16 bytes) en Base64. */
    fun newSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /** Derivación PBKDF2-HmacSHA-256 — contraseña en claro nunca persistida. */
    fun hashPassword(password: String, saltB64: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.decode(saltB64, Base64.NO_WRAP),
            ITERATIONS,
            KEY_LENGTH
        )
        val digest = SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        spec.clearPassword()
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** SharedPreferences cifrado con Android Keystore (AES-256). */
    fun securePrefs(ctx: Context): android.content.SharedPreferences {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            FILE_SECURE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Guarda la sesión cifrada después del login (control de acceso). */
    fun saveSession(ctx: Context, user: String, tsMillis: Long) {
        securePrefs(ctx).edit()
            .putString("user", user)
            .putLong("ts", tsMillis)
            .apply()
    }

    fun currentUser(ctx: Context): String? = securePrefs(ctx).getString("user", null)

    fun sessionTs(ctx: Context): Long = securePrefs(ctx).getLong("ts", 0L)

    fun clearSession(ctx: Context) {
        securePrefs(ctx).edit().clear().apply()
    }
}
