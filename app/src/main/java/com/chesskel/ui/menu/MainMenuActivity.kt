package com.chesskel.ui.menu

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.chesskel.R
import com.chesskel.game.AiMode
import com.chesskel.ui.pvp.PvpLobbyActivity

class MainMenuActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("chesskel_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplica el modo guardado a ESTA Activity antes de inflar vistas
        val savedDark = prefs.getBoolean("dark_mode", true)
        delegate.localNightMode = if (savedDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

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
            val currentlyDark = isDarkMode()
            val newMode = if (currentlyDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            // Guarda preferencia
            prefs.edit().putBoolean("dark_mode", newMode == AppCompatDelegate.MODE_NIGHT_YES).apply()
            // Aplica a esta Activity; AppCompat recrea automáticamente si es necesario
            delegate.localNightMode = newMode
            // Actualiza icono inmediatamente
            syncThemeToggleIcon(btnTheme)
        }

        optAi.setOnClickListener { showAiModeDialog() }
        optPvp.setOnClickListener { startActivity(Intent(this, PvpLobbyActivity::class.java)) }
        optProfile.setOnClickListener { Toast.makeText(this, getString(R.string.profile_todo), Toast.LENGTH_SHORT).show() }

        tvVersion.text = getString(R.string.version_text, "1.0")
    }

    private fun isDarkMode(): Boolean = when (delegate.localNightMode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun syncThemeToggleIcon(btn: ImageButton) {
        val dark = isDarkMode()
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