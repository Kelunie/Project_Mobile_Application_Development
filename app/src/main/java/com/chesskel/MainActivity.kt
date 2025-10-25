// app/src/main/java/com/chesskel/MainActivity.kt
package com.chesskel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.chesskel.data.DBHelper
import com.chesskel.ui.auth.LoginActivity
import com.chesskel.ui.auth.RegisterActivity
import com.chesskel.ui.menu.MainMenuActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
        val hasSession = prefs.getLong("current_user_id", -1L) > 0L
        val db = DBHelper(this)
        val hasAnyUser = db.readableDatabase
            .rawQuery("SELECT 1 FROM usuarios LIMIT 1", null)
            .use { it.moveToFirst() }

        when {
            hasSession -> startActivity(Intent(this, MainMenuActivity::class.java))
            hasAnyUser -> startActivity(Intent(this, LoginActivity::class.java))
            else -> startActivity(Intent(this, RegisterActivity::class.java))
        }
        finish()
    }
}
