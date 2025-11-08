package com.chesskel.ui.menu

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chesskel.R
import com.chesskel.game.AiMode
import com.chesskel.ui.pvp.PvpLobbyActivity
import com.chesskel.ui.theme.ThemeUtils
import com.chesskel.ui.game.GameActivity

class MainMenuActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("chesskel_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme globally BEFORE inflating layout
        ThemeUtils.applySavedTheme(this)

        setContentView(R.layout.activity_main_menu)

        val optAi = findViewById<LinearLayout>(R.id.option_ai)
        val optPvp = findViewById<LinearLayout>(R.id.option_pvp)
        val optProfile = findViewById<LinearLayout>(R.id.option_profile)
        val btnTheme = findViewById<ImageButton>(R.id.btnTheme)
        val tvVersion = findViewById<TextView>(R.id.tvVersion)

        // Asegura que el botón queda por encima del ScrollView y recibe toques
        btnTheme.bringToFront()
        btnTheme.elevation = 16f
        btnTheme.isClickable = true
        btnTheme.isFocusable = true

        // Icono inicial según el modo actual
        syncThemeToggleIcon(btnTheme)

        btnTheme.setOnClickListener {
            val newDark = ThemeUtils.toggleAndPersist(this)
            syncThemeToggleIcon(btnTheme)
        }

        optAi.setOnClickListener { showAiModeDialog() }
        optPvp.setOnClickListener { startActivity(Intent(this, PvpLobbyActivity::class.java)) }
        optProfile.setOnClickListener { Toast.makeText(this, getString(R.string.profile_todo), Toast.LENGTH_SHORT).show() }

        tvVersion.text = getString(R.string.version_text, "1.0")
    }

    private fun syncThemeToggleIcon(btn: ImageButton) {
        val dark = ThemeUtils.isDarkMode(this)
        btn.setImageResource(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
        btn.contentDescription = if (dark) getString(R.string.switch_to_light) else getString(R.string.switch_to_dark)
    }

    private fun showAiModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_modes, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val modeEasy = dialogView.findViewById<LinearLayout>(R.id.mode_easy)
        val modeNormal = dialogView.findViewById<LinearLayout>(R.id.mode_normal)
        val modeHard = dialogView.findViewById<LinearLayout>(R.id.mode_hard)
        val modePro = dialogView.findViewById<LinearLayout>(R.id.mode_pro)

        fun select(mode: AiMode) {
            Toast.makeText(applicationContext, getString(R.string.mode_selected_fmt, mode.label), Toast.LENGTH_SHORT).show()
            showAiColorChoice(mode)
            dialog.dismiss()
        }

        modeEasy.setOnClickListener { select(AiMode.EASY) }
        modeNormal.setOnClickListener { select(AiMode.NORMAL) }
        modeHard.setOnClickListener { select(AiMode.HARD) }
        modePro.setOnClickListener { select(AiMode.PRO) }

        dialog.show()
    }

    private fun showAiColorChoice(mode: AiMode) {
        val items = arrayOf(getString(R.string.play_as_white), getString(R.string.play_as_black))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_color))
            .setItems(items) { _, which ->
                val humanPlaysWhite = (which == 0)
                val intent = Intent(this, com.chesskel.ui.game.GameActivity::class.java).apply {
                    putExtra("ai_mode", mode.name)
                    putExtra("ai_min_elo", mode.minElo)
                    putExtra("ai_max_elo", mode.maxElo)
                    putExtra("human_plays_white", humanPlaysWhite)
                }
                startActivity(intent)
            }
            .setCancelable(true)
            .show()
    }
}