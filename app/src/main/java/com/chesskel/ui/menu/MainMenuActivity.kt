// app/src/main/java/com/chesskel/ui/menu/MainMenuActivity.kt
package com.chesskel.ui.menu

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.chesskel.R

class MainMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        findViewById<Button>(R.id.btnVsAi).setOnClickListener {
            Toast.makeText(this, "Play vs AI (TODO)", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPvp).setOnClickListener {
            Toast.makeText(this, "Player vs Player (TODO)", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            Toast.makeText(this, "Profile (TODO)", Toast.LENGTH_SHORT).show()
        }
    }
}
