package com.chesskel.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {

    private const val PREFS_NAME = "chesskel_prefs"
    private const val KEY_THEME = "ui_theme"

    // Enum to represent the available theme modes
    enum class ThemeMode {
        LIGHT, DARK, SYSTEM
    }

    /**
     * Applies the saved theme preference. This should be called in each Activity's onCreate,
     * before setContentView().
     */
    fun applySavedTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeValue = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val mode = when (ThemeMode.valueOf(themeValue)) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /**
     * Toggles between light and dark mode and persists the choice.
     * Returns true if the new mode is dark, false otherwise.
     */
    fun toggleAndPersist(context: Context): Boolean {
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val newNightMode = if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }

        val newTheme = if (newNightMode == AppCompatDelegate.MODE_NIGHT_YES) ThemeMode.DARK else ThemeMode.LIGHT
        persistTheme(context, newTheme)
        AppCompatDelegate.setDefaultNightMode(newNightMode)
        return newTheme == ThemeMode.DARK
    }

    /**
     * Saves the chosen theme to SharedPreferences.
     */
    private fun persistTheme(context: Context, theme: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    /**
     * Checks if the app is currently in dark mode.
     */
    fun isDarkMode(context: Context): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}
