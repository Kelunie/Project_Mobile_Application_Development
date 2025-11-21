// app/src/main/java/com/chesskel/ui/auth/LoginActivity.kt
package com.chesskel.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chesskel.R
import com.chesskel.data.DBHelper
import com.chesskel.ui.menu.MainMenuActivity
import com.chesskel.util.Security
import com.chesskel.ui.theme.ThemeUtils

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.applySavedTheme(this)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoRegister = findViewById<TextView>(R.id.btnGoRegister)
        val db = DBHelper(this)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, getString(R.string.login_fields_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hash = Security.sha256(pass)
            val sql = "SELECT id FROM usuarios WHERE email=? AND password_hash=? LIMIT 1"
            db.readableDatabase.rawQuery(sql, arrayOf(email, hash)).use { c ->
                if (c.moveToFirst()) {
                    val userId = c.getLong(0)
                    getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                        .edit().putLong("current_user_id", userId).apply()
                    // will apears a toeast "login successful" before main menu
                    Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainMenuActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }
}
