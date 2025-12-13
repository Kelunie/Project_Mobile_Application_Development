// app/src/main/java/com/chesskel/ui/auth/LoginActivity.kt
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class LoginActivity : CenteredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.applySavedTheme(this)
        setCenteredContentView(R.layout.activity_login)

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

            // Try remote login first
            lifecycleScope.launch {
                val remote = try {
                    com.chesskel.net.ApiClient.login(email, hash)
                } catch (e: Exception) {
                    null
                }

                if (remote != null) {
                    // got remote user object
                    val remoteEmail = remote.optString("email", email)
                    val remoteName = remote.optString("name", email)
                    val remoteId = remote.optLong("id", -1)
                    val remoteProfileImageUrl = remote.optString("profileImageUrl").let { if (it == JSONObject.NULL) null else it }
                    val remoteLocation = remote.optString("location").let { if (it == JSONObject.NULL) null else it }

                    // ensure local user exists: try to find by email
                    var localId = -1L
                    val existing = db.getUserById(1) // quick check not useful - instead query by email
                    // We'll query using SQL
                    db.readableDatabase.rawQuery("SELECT id FROM usuarios WHERE email=? LIMIT 1", arrayOf(remoteEmail)).use { c ->
                        if (c.moveToFirst()) localId = c.getLong(0)
                    }
                    if (localId <= 0) {
                        localId = db.insertUser(remoteName, remoteEmail, hash)
                    }

                    // Update local profile with remote data
                    if (remoteProfileImageUrl != null) {
                        val imagePath = downloadAndSaveImage(remoteProfileImageUrl, localId)
                        if (imagePath != null) {
                            db.updateUserProfile(localId, imagePath, remoteLocation)
                        } else {
                            // Keep existing image if download failed
                            val existingUser = db.getUserById(localId)
                            val existingPath = existingUser?.profileImagePath
                            db.updateUserProfile(localId, existingPath, remoteLocation)
                        }
                    } else {
                        // Keep existing image, only update location
                        val existingUser = db.getUserById(localId)
                        val existingPath = existingUser?.profileImagePath
                        db.updateUserProfile(localId, existingPath, remoteLocation)
                    }

                    // Save the remote profileImageUrl
                    db.updateUserProfileImageUrl(localId, remoteProfileImageUrl)

                    getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                        .edit().putLong("current_user_id", localId).apply()
                    // Also persist email and name for fallback lookups
                    getSharedPreferences("chesskel_prefs", MODE_PRIVATE).edit()
                        .putString("current_user_email", remoteEmail)
                        .putString("current_user_name", remoteName)
                        .apply()

                    Toast.makeText(this@LoginActivity, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, com.chesskel.ui.menu.MainMenuActivity::class.java))
                    finish()
                    return@launch
                }

                // Remote login failed or unavailable -> fall back to local DB
                val sql = "SELECT id FROM usuarios WHERE email=? AND password_hash=? LIMIT 1"
                db.readableDatabase.rawQuery(sql, arrayOf(email, hash)).use { c ->
                    if (c.moveToFirst()) {
                        val userId = c.getLong(0)
                        getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                            .edit().putLong("current_user_id", userId).apply()
                        // persist email locally as well
                        getSharedPreferences("chesskel_prefs", MODE_PRIVATE).edit()
                            .putString("current_user_email", email)
                            .apply()
                        Toast.makeText(this@LoginActivity, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, com.chesskel.ui.menu.MainMenuActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private suspend fun downloadAndSaveImage(imageUrl: String, userId: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                val input = connection.inputStream
                val imagesDir = File(filesDir, "profile_images").apply { mkdirs() }
                val imageFile = File(imagesDir, "${userId}.jpg")
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                "profile_images/${userId}.jpg"
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
