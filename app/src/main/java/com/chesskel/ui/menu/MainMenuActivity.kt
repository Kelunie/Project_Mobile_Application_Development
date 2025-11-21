package com.chesskel.ui.menu

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.chesskel.R
import com.chesskel.game.AiMode
import com.chesskel.ui.pvp.PvpLobbyActivity
import com.chesskel.ui.theme.ThemeUtils
import com.chesskel.ui.theme.CenteredActivity
import com.chesskel.ui.profile.ProfileActivity
import com.chesskel.ui.learn.LearningActivity

class MainMenuActivity : CenteredActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme globally BEFORE inflating layout
        ThemeUtils.applySavedTheme(this)

        setCenteredContentView(R.layout.activity_main_menu)

        val optAi = findViewById<LinearLayout>(R.id.option_ai)
        val optPvp = findViewById<LinearLayout>(R.id.option_pvp)
        val optProfile = findViewById<LinearLayout>(R.id.option_profile)
        val optLearn = findViewById<LinearLayout>(R.id.option_learn)
        val btnTheme = findViewById<ImageButton>(R.id.btnTheme)
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val optDonate = findViewById<LinearLayout>(R.id.option_donate)

        // Asegura que el botón queda por encima del ScrollView y recibe toques
        btnTheme.bringToFront()
        btnTheme.elevation = 16f
        btnTheme.isClickable = true
        btnTheme.isFocusable = true

        // Icono inicial según el modo actual
        syncThemeToggleIcon(btnTheme)

        btnTheme.setOnClickListener {
            ThemeUtils.toggleAndPersist(this)
            syncThemeToggleIcon(btnTheme)
        }

        optAi.setOnClickListener { showAiModeDialog() }
        optPvp.setOnClickListener { startActivity(Intent(this, PvpLobbyActivity::class.java)) }
        optProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        optLearn.setOnClickListener { startActivity(Intent(this, LearningActivity::class.java)) }

        optDonate.setOnClickListener {
            openExternalLink("https://www.paypal.com/donate/?hosted_button_id=8BQJZBXGAPTFA")
        }

        tvVersion.text = getString(R.string.version_text)
    }

    private fun openExternalLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No se encontró una app para abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncThemeToggleIcon(btn: ImageButton) {
        val dark = ThemeUtils.isDarkMode(this)

        // Icono del botón de tema
        btn.setImageResource(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
        btn.contentDescription = if (dark) getString(R.string.switch_to_light) else getString(R.string.switch_to_dark)

        // Fondo del icono principal: con tile en oscuro, sin borde en claro
        val appIcon = findViewById<android.widget.ImageView>(R.id.ivAppIcon)
        if (dark) {
            appIcon.setBackgroundResource(R.drawable.bg_icon_tile)
        } else {
            appIcon.setBackgroundResource(android.R.color.transparent)
        }

        // Ajustar colores de la status bar para que los iconos sean legibles
        val window = window
        val statusColor = if (dark) {
            // en modo oscuro, fondo oscuro para status bar
            getColor(R.color.surface_bg_dark)
        } else {
            // en modo claro, fondo claro
            getColor(R.color.surface_bg)
        }
        window.statusBarColor = statusColor

        // En modo claro, pedir iconos oscuros; en modo oscuro, iconos claros
        val decor = window.decorView
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = if (!dark) {
            decor.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            decor.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
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