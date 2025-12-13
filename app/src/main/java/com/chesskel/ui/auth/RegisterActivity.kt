package com.chesskel.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.chesskel.R
import com.chesskel.data.DBHelper
import com.chesskel.ui.menu.MainMenuActivity
import com.chesskel.util.Security
import com.chesskel.ui.theme.ThemeUtils
import com.chesskel.ui.theme.CenteredActivity
import kotlinx.coroutines.launch

class RegisterActivity : CenteredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.applySavedTheme(this)
        setCenteredContentView(R.layout.activity_register)


        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val btnGoLogin = findViewById<TextView>(R.id.btnGoLogin)
        val db = DBHelper(this)

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            val confirmPass = etConfirmPassword.text.toString()
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, getString(R.string.all_fields_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hash = com.chesskel.util.Security.sha256(pass)

            // Try to create remote user first
            lifecycleScope.launch {
                val remote = try {
                    com.chesskel.net.ApiClient.createUser(name, email, hash)
                } catch (e: Exception) {
                    null
                }

                if (remote != null) {
                    // remote created - ensure local
                    val id = db.insertUser(
                        nombre = name,
                        email = email,
                        passwordHash = hash
                    )
                    if (id > 0) {
                        getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                            .edit().putLong("current_user_id", id).apply()
                        getSharedPreferences("chesskel_prefs", MODE_PRIVATE).edit()
                            .putString("current_user_email", email)
                            .putString("current_user_name", name)
                            .apply()
                        Toast.makeText(this@RegisterActivity, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, com.chesskel.ui.menu.MainMenuActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@RegisterActivity, getString(R.string.email_exists), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // remote failed or unavailable, fallback to local creation
                    val id = db.insertUser(
                        nombre = name,
                        email = email,
                        passwordHash = hash
                    )
                    if (id > 0) {
                        getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                            .edit().putLong("current_user_id", id).apply()
                        getSharedPreferences("chesskel_prefs", MODE_PRIVATE).edit()
                            .putString("current_user_email", email)
                            .putString("current_user_name", name)
                            .apply()
                        Toast.makeText(this@RegisterActivity, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, com.chesskel.ui.menu.MainMenuActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@RegisterActivity, getString(R.string.email_exists), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
