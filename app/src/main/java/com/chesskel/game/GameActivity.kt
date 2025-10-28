// app/src/main/java/com/chesskel/ui/game/GameActivity.kt
package com.chesskel.ui.game

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_game) // add layout when ready

        val mode = intent.getStringExtra("ai_mode") ?: "UNKNOWN"
        val minElo = intent.getIntExtra("ai_min_elo", -1)
        val maxElo = intent.getIntExtra("ai_max_elo", -1)

        Toast.makeText(this, "AI: $mode ($minElo - $maxElo)", Toast.LENGTH_SHORT).show()

        // TODO: initialize game board / AI using the passed ELO range
    }
}
