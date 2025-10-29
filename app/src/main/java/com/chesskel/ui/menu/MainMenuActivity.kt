package com.chesskel.ui.menu

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.chesskel.R
import com.chesskel.game.AiMode

class MainMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        findViewById<Button>(R.id.btnVsAi).setOnClickListener {
            showAiModeDialog()
        }

        findViewById<Button>(R.id.btnPvp).setOnClickListener {
            // keep existing behavior or start PvP activity
            Toast.makeText(this, "Player vs Player (TODO)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            Toast.makeText(this, "Profile (TODO)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAiModeDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_ai_modes, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val modeEasy = dialogView.findViewById<LinearLayout>(R.id.mode_easy)
        val modeNormal = dialogView.findViewById<LinearLayout>(R.id.mode_normal)
        val modeHard = dialogView.findViewById<LinearLayout>(R.id.mode_hard)
        val modePro = dialogView.findViewById<LinearLayout>(R.id.mode_pro)

        fun pickSideAndStart(mode: AiMode) {
            // First close the mode dialog so we don’t stack dialogs
            dialog.dismiss()
            showChooseSideDialog(mode)
        }

        modeEasy.setOnClickListener { pickSideAndStart(AiMode.EASY) }
        modeNormal.setOnClickListener { pickSideAndStart(AiMode.NORMAL) }
        modeHard.setOnClickListener { pickSideAndStart(AiMode.HARD) }
        modePro.setOnClickListener { pickSideAndStart(AiMode.PRO) }

        dialog.show()
    }

    private fun showChooseSideDialog(mode: AiMode) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_side))
            .setItems(arrayOf(getString(R.string.play_as_white), getString(R.string.play_as_black))) { _, which ->
                val humanIsWhite = (which == 0)
                val intent = Intent(this, com.chesskel.ui.game.GameActivity::class.java).apply {
                    putExtra("ai_mode", mode.name)
                    putExtra("ai_min_elo", mode.minElo)
                    putExtra("ai_max_elo", mode.maxElo)
                    putExtra("human_plays_white", humanIsWhite)   // preferred
                    putExtra("ai_plays_white", !humanIsWhite)     // fallback for older code
                }
                startActivity(intent)
            }
            .setCancelable(true)
            .show()
    }
}