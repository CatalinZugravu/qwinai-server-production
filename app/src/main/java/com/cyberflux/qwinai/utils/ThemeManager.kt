package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowInsetsController

/**
 * Enhanced theme manager that applies complete themes instead of overlays
 */
object ThemeManager {
    private const val TAG = "ThemeManager"

    // Broadcast action for theme changes
    const val ACTION_THEME_CHANGED = "com.cyberflux.qwinai.ACTION_THEME_CHANGED"

    // Theme mode constants
    const val MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    const val MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
    const val MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES

    // Theme style constants
    const val THEME_DEFAULT = 0

    // Elegant Themes
    const val THEME_SLATE = 1
    const val THEME_OLIVE = 2
    const val THEME_BURGUNDY = 3
    const val THEME_COPPER = 4
    const val THEME_CARBON = 5
    const val THEME_TEAL = 6
    const val THEME_SERENITY = 7

    // Vibrant Themes
    const val THEME_NEON = 8
    const val THEME_AURORA = 9
    const val THEME_ROYAL = 10
    const val THEME_TROPICAL = 11
    const val THEME_MONOCHROME = 12
    const val THEME_COSMIC = 13
    const val THEME_VIVID = 14

    // Optional features
    private const val GRADIENTS_ENABLED = "gradients_enabled"

    // Preferences
    private const val THEME_PREFS = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_STYLE = "theme_style"
    private const val KEY_THEME_FEATURES = "theme_features"

    /**
     * Initialize theme from saved preferences
     * This should be called in Application.onCreate()
     */
    fun initialize(context: Context) {
        val savedMode = getSavedThemeMode(context)
        AppCompatDelegate.setDefaultNightMode(savedMode)
        Log.d(TAG, "Initialized with mode: $savedMode")
    }

    /**
     * Set theme mode and style
     * This should be called when the user selects a new theme
     */
    fun setTheme(context: Context, themeMode: Int, themeStyle: Int = THEME_DEFAULT, enableGradients: Boolean = false) {
        Log.d(TAG, "Setting theme: mode=$themeMode, style=$themeStyle, gradients=$enableGradients")

        // Save preferences
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, themeMode)
            .putInt(KEY_THEME_STYLE, themeStyle)
            .putBoolean(GRADIENTS_ENABLED, enableGradients)
            .apply()

        // Apply theme mode
        AppCompatDelegate.setDefaultNightMode(themeMode)

        // Broadcast theme change event
        val intent = Intent(ACTION_THEME_CHANGED)
        context.sendBroadcast(intent)

        // If this is an activity, recreate it to apply the new theme
        if (context is Activity) {
            Log.d(TAG, "Recreating activity to apply theme")
            // Small delay to ensure preferences are saved
            context.window.decorView.postDelayed({
                context.recreate()
            }, 100)
        }
    }

    /**
     * Get saved theme mode
     */
    fun getSavedThemeMode(context: Context): Int {
        return context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    /**
     * Get saved theme style
     */
    fun getSavedThemeStyle(context: Context): Int {
        return context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_STYLE, THEME_DEFAULT)
    }

    /**
     * Check if gradients are enabled
     */
    fun areGradientsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getBoolean(GRADIENTS_ENABLED, false)
    }

    /**
     * Get the theme resource ID for the current theme style
     * This is used to apply the theme to activities
     */
    fun getThemeResourceId(context: Context): Int {
        val themeStyle = getSavedThemeStyle(context)
        return when (themeStyle) {
            // Elegant Themes
            THEME_SLATE -> R.style.Theme_Slate
            THEME_OLIVE -> R.style.Theme_Olive
            THEME_BURGUNDY -> R.style.Theme_Burgundy
            THEME_COPPER -> R.style.Theme_Copper
            THEME_CARBON -> R.style.Theme_Carbon
            THEME_TEAL -> R.style.Theme_Teal
            THEME_SERENITY -> R.style.Theme_Serenity

            // Vibrant Themes
            THEME_NEON -> R.style.Theme_Neon
            THEME_AURORA -> R.style.Theme_Aurora
            THEME_ROYAL -> R.style.Theme_Royal
            THEME_TROPICAL -> R.style.Theme_Tropical
            THEME_MONOCHROME -> R.style.Theme_Monochrome
            THEME_COSMIC -> R.style.Theme_Cosmic
            THEME_VIVID -> R.style.Theme_Vivid

            // Default Theme
            else -> R.style.AppTheme
        }
    }

    /**
     * Get display name for current theme
     */
    fun getThemeDisplayName(context: Context): String {
        val mode = when (getSavedThemeMode(context)) {
            MODE_LIGHT -> "Light"
            MODE_DARK -> "Dark"
            else -> "System default"
        }

        val style = when (getSavedThemeStyle(context)) {
            // Elegant Themes
            THEME_SLATE -> "Slate Blue"
            THEME_OLIVE -> "Olive Garden"
            THEME_BURGUNDY -> "Burgundy"
            THEME_COPPER -> "Rustic Copper"
            THEME_CARBON -> "Carbon"
            THEME_TEAL -> "Teal Harbor"
            THEME_SERENITY -> "Serenity"

            // Vibrant Themes
            THEME_NEON -> "Neon Cyberpunk"
            THEME_AURORA -> "Aurora Borealis"
            THEME_ROYAL -> "Royal Jewel"
            THEME_TROPICAL -> "Tropical Paradise"
            THEME_MONOCHROME -> "Monochrome Punch"
            THEME_COSMIC -> "Cosmic Gradient"
            THEME_VIVID -> "Vivid Gradient"

            // Default Theme
            else -> "Default"
        }

        val gradientsText = if (
            areGradientsEnabled(context) &&
            (getSavedThemeStyle(context) in arrayOf(
                THEME_NEON, THEME_AURORA, THEME_ROYAL,
                THEME_TROPICAL, THEME_COSMIC, THEME_VIVID
            ))
        ) {
            " with Gradients"
        } else {
            ""
        }

        return if (getSavedThemeStyle(context) == THEME_DEFAULT) {
            mode
        } else {
            "$style$gradientsText ($mode)"
        }
    }

    /**
     * Apply gradient background to a view if gradients are enabled
     */
    fun applyGradientBackground(context: Context, view: View) {
        if (!areGradientsEnabled(context)) return

        val themeStyle = getSavedThemeStyle(context)
        val isDarkMode = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Only apply gradients to vibrant themes
        if (themeStyle >= THEME_NEON) {
            val gradientDrawable = when (themeStyle) {
                THEME_NEON -> if (isDarkMode) R.drawable.bg_neon_gradient else R.drawable.bg_neon_gradient_light
                THEME_AURORA -> if (isDarkMode) R.drawable.bg_aurora_gradient else R.drawable.bg_aurora_gradient_light
                THEME_ROYAL -> if (isDarkMode) R.drawable.bg_royal_gradient else R.drawable.bg_royal_gradient_light
                THEME_TROPICAL -> if (isDarkMode) R.drawable.bg_tropical_gradient else R.drawable.bg_tropical_gradient_light
                THEME_COSMIC -> if (isDarkMode) R.drawable.bg_cosmic_gradient else R.drawable.bg_cosmic_gradient_light
                THEME_VIVID -> if (isDarkMode) R.drawable.bg_vivid_gradient else R.drawable.bg_vivid_gradient_light
                else -> null
            }

            gradientDrawable?.let {
                view.setBackgroundResource(it)
            }
        }
    }
}