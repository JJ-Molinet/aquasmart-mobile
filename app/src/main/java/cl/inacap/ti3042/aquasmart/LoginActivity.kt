package cl.inacap.ti3042.aquasmart

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/**
 * Pantalla de Login — autenticación local con SQLite + PBKDF2.
 *
 * (ISO 27400: control de acceso con credenciales, contraseñas cifradas
 * con derivación PBKDF2 y enfoque anti fuerza bruta por bloqueo temporal.)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var db: DbHelper
    private var intentosFallidos = 0
    private var bloqueoHastaMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // si ya hay sesión cifrada vigente → directo al menú
        val user = Security.currentUser(this)
        if (user != null && (System.currentTimeMillis() - Security.sessionTs(this)) <
            MAX_SESION_MS
        ) {
            irAlMenu(user)
            return
        }
        Security.clearSession(this)

        setContentView(R.layout.activity_login)
        db = DbHelper(this)

        val etUser = findViewById<TextInputEditText>(R.id.etUser)
        val etPass = findViewById<TextInputEditText>(R.id.etPass)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvMsg = findViewById<TextView>(R.id.tvLoginMsg)

        // precarga usuario demo la primera vez que corre la app
        if (!db.userExists(USUARIO_DEMO)) {
            val salt = Security.newSalt()
            db.insertUser(USUARIO_DEMO, salt, Security.hashPassword(CLAVE_DEMO, salt))
        }

        btnLogin.setOnClickListener {
            val userL = etUser.text?.toString()?.trim().orEmpty()
            val passL = etPass.text?.toString()?.trim().orEmpty()

            if (System.currentTimeMillis() < bloqueoHastaMs) {
                val quedan = ((bloqueoHastaMs - System.currentTimeMillis()) / 1000) + 1
                tvMsg.text = "Demasiados intentos. Espera ${quedan}s."
                return@setOnClickListener
            }
            if (userL.isEmpty() || passL.isEmpty()) {
                tvMsg.text = "Usuario y contraseña son obligatorios."
                return@setOnClickListener
            }
            if (db.validateLogin(userL, passL)) {
                intentosFallidos = 0
                Security.saveSession(this, userL, System.currentTimeMillis())
                Toast.makeText(this, "¡Bienvenido, $userL!", Toast.LENGTH_SHORT).show()
                irAlMenu(userL)
            } else {
                intentosFallidos++
                if (intentosFallidos >= MAX_INTENTOS) {
                    bloqueoHastaMs = System.currentTimeMillis() + BLOQUEO_MS
                    intentosFallidos = 0
                    tvMsg.text = "Cuenta bloqueada por $BLOQUEO_SEG s (fuerza bruta)."
                } else {
                    tvMsg.text = "Credenciales inválidas (intento $intentosFallidos/$MAX_INTENTOS)."
                }
            }
        }

        // registro de cuenta nueva (también almacenada cifrada con PBKDF2)
        btnRegister.setOnClickListener {
            val u = etUser.text?.toString()?.trim().orEmpty()
            val p = etPass.text?.toString()?.trim().orEmpty()
            when {
                u.length < 4 -> tvMsg.text = "El usuario debe tener al menos 4 caracteres."
                p.length < 6 -> tvMsg.text = "La contraseña debe tener al menos 6 caracteres."
                db.userExists(u) -> tvMsg.text = "Ese usuario ya existe."
                else -> {
                    val salt = Security.newSalt()
                    if (db.insertUser(u, salt, Security.hashPassword(p, salt))) {
                        tvMsg.text = ""
                        Toast.makeText(this, "Cuenta creada. ¡Ahora ingresa!", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        tvMsg.text = "No se pudo crear la cuenta."
                    }
                }
            }
        }
    }

    private fun irAlMenu(user: String) {
        startActivity(
            Intent(this, MenuActivity::class.java).putExtra("user", user)
        )
        finish()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setMessage("¿Salir de AquaSmart?")
            .setPositiveButton("Salir") { _, _ -> finishAffinity() }
            .setNegativeButton("Quedarse", null)
            .show()
    }

    companion object {
        const val USUARIO_DEMO = "juanjo"
        const val CLAVE_DEMO = "aquasmart123"
        const val MAX_INTENTOS = 3
        const val BLOQUEO_SEG = 30
        const val BLOQUEO_MS = BLOQUEO_SEG * 1000L
        const val MAX_SESION_MS = 30 * 60 * 1000L // sesión cifrada vence a los 30 min
    }
}
