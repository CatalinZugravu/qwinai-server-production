package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import timber.log.Timber

/**
 * Simplified Material Design 3 Theme Manager
 * Supports Light/Dark/System modes with customizable accent colors
 */
object ThemeManager {
    private const val TAG = "ThemeManager"

    // Broadcast action for theme changes
    const val ACTION_THEME_CHANGED = "com.cyberflux.qwinai.ACTION_THEME_CHANGED"

    // Theme mode constants
    const val MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    const val MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
    const val MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES

    // Accent color constants
    const val ACCENT_INDIGO = 0     // Default
    const val ACCENT_BLUE = 1
    const val ACCENT_PURPLE = 2
    const val ACCENT_PINK = 3
    const val ACCENT_GREEN = 4
    const val ACCENT_ORANGE = 5
    const val ACCENT_RED = 6
    const val ACCENT_TEAL = 7
    const val ACCENT_CYAN = 8

    // Theme style constants for compatibility
    const val THEME_NEON = 0
    const val THEME_SLATE = 1
    const val THEME_OLIVE = 2
    const val THEME_BURGUNDY = 3
    const val THEME_COPPER = 4
    const val THEME_CARBON = 5
    const val THEME_TEAL = 6
    const val THEME_SERENITY = 7
    const val THEME_AURORA = 8
    const val THEME_ROYAL = 9
    const val THEME_TROPICAL = 10
    const val THEME_MONOCHROME = 11
    const val THEME_COSMIC = 12
    const val THEME_VIVID = 13
    const val THEME_DEFAULT = 0

    // Preferences
    private const val THEME_PREFS = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_COLOR = "accent_color"

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
     * Set theme mode with optional accent color
     */
    fun setTheme(context: Context, themeMode: Int, accentColor: Int = ACCENT_INDIGO) {
        Log.d(TAG, "Setting theme: mode=$themeMode, accent=$accentColor")

        // Save preferences
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, themeMode)
            .putInt(KEY_ACCENT_COLOR, accentColor)
            .apply()

        // Apply theme mode
        AppCompatDelegate.setDefaultNightMode(themeMode)

        // Broadcast theme change event
        val intent = Intent(ACTION_THEME_CHANGED)
        context.sendBroadcast(intent)

        // If this is an activity, recreate it to apply the new theme
        if (context is Activity) {
            Timber.tag(TAG).d("Recreating activity to apply theme")
            // Small delay to ensure preferences are saved
            context.window.decorView.postDelayed({
                context.recreate()
            }, 100)
        }
    }

    /**
     * Set only accent color without changing theme mode
     */
    fun setAccentColor(context: Context, accentColor: Int) {
        val currentMode = getSavedThemeMode(context)
        setTheme(context, currentMode, accentColor)
    }

    /**
     * Get saved theme mode
     */
    fun getSavedThemeMode(context: Context): Int {
        return context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    /**
     * Get saved accent color
     */
    fun getSavedAccentColor(context: Context): Int {
        return context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT_COLOR, ACCENT_INDIGO)
    }

    /**
     * Get display name for current theme mode
     */
    fun getThemeModeDisplayName(context: Context): String {
        return when (getSavedThemeMode(context)) {
            MODE_LIGHT -> "Light"
            MODE_DARK -> "Dark"
            else -> "System default"
        }
    }

    /**
     * Get display name for accent color
     */
    fun getAccentColorDisplayName(context: Context): String {
        return when (getSavedAccentColor(context)) {
            ACCENT_INDIGO -> "Indigo"
            ACCENT_BLUE -> "Blue"
            ACCENT_PURPLE -> "Purple"
            ACCENT_PINK -> "Pink"
            ACCENT_GREEN -> "Green"
            ACCENT_ORANGE -> "Orange"
            ACCENT_RED -> "Red"
            ACCENT_TEAL -> "Teal"
            ACCENT_CYAN -> "Cyan"
            else -> "Indigo"
        }
    }

    /**
     * Get accent color resource
     */
    fun getAccentColorResource(context: Context): Int {
        return when (getSavedAccentColor(context)) {
            ACCENT_BLUE -> R.color.accent_blue
            ACCENT_PURPLE -> R.color.accent_purple
            ACCENT_PINK -> R.color.accent_pink
            ACCENT_GREEN -> R.color.accent_green
            ACCENT_ORANGE -> R.color.accent_orange
            ACCENT_RED -> R.color.accent_red
            ACCENT_TEAL -> R.color.accent_teal
            ACCENT_CYAN -> R.color.accent_cyan
            else -> R.color.md_theme_primary_seed // Default indigo
        }
    }

    /**
     * Check if currently in dark mode
     */
    fun isDarkMode(context: Context): Boolean {
        val savedMode = getSavedThemeMode(context)
        return when (savedMode) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> {
                // System mode - check current configuration
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /**
     * Apply proper status bar appearance for Material Design 3
     */
    fun applySystemUIAppearance(activity: Activity) {
        val window = activity.window
        val isDark = isDarkMode(activity)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use new WindowInsetsController for API 30+
            val controller = window.insetsController
            if (controller != null) {
                if (isDark) {
                    // Dark mode - light status bar content
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                } else {
                    // Light mode - dark status bar content
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            // Use legacy approach for older APIs
            @Suppress("DEPRECATION")
            val flags = window.decorView.systemUiVisibility
            if (isDark) {
                // Dark mode
                window.decorView.systemUiVisibility = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                // Light mode
                window.decorView.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        // Set status bar and navigation bar colors from theme
        val surface = if (isDark) {
            ContextCompat.getColor(activity, R.color.md_theme_dark_surface)
        } else {
            ContextCompat.getColor(activity, R.color.md_theme_light_surface)
        }
        
        window.statusBarColor = surface
        window.navigationBarColor = surface
    }

    /**
     * Get all available accent color options
     */
    fun getAvailableAccentColors(): List<Pair<Int, String>> {
        return listOf(
            ACCENT_INDIGO to "Indigo",
            ACCENT_BLUE to "Blue",
            ACCENT_PURPLE to "Purple",
            ACCENT_PINK to "Pink",
            ACCENT_GREEN to "Green",
            ACCENT_ORANGE to "Orange",
            ACCENT_RED to "Red",
            ACCENT_TEAL to "Teal",
            ACCENT_CYAN to "Cyan"
        )
    }

    /**
     * Get all available theme mode options
     */
    fun getAvailableThemeModes(): List<Pair<Int, String>> {
        return listOf(
            MODE_SYSTEM to "System default",
            MODE_LIGHT to "Light",
            MODE_DARK to "Dark"
        )
    }
    
    /**
     * Compatibility methods for missing functionality
     */
    fun getSavedThemeStyle(context: Context): Int {
        return getSavedThemeMode(context)
    }
    
    fun areGradientsEnabled(context: Context): Boolean {
        return false // Gradients disabled for simplicity
    }
    
    fun applyGradientBackground(context: Context, view: android.view.View) {
        // No-op for simplicity
    }
}