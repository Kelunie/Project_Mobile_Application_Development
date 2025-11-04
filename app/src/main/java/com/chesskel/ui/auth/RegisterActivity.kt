package com.chesskel.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.chesskel.R
import com.chesskel.data.DBHelper
import com.chesskel.ui.menu.MainMenuActivity
import com.chesskel.util.Security

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val btnGoLogin = findViewById<TextView>(R.id.btnGoLogin)
        val db = DBHelper(this)

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, getString(R.string.all_fields_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val id = db.insertUser(
                nombre = name,
                email = email,
                passwordHash = Security.sha256(pass)
            )
            if (id > 0) {
                getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                    .edit().putLong("current_user_id", id).apply()
                Toast.makeText(this, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainMenuActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, getString(R.string.email_exists), Toast.LENGTH_SHORT).show()
            }
        }

        btnGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
