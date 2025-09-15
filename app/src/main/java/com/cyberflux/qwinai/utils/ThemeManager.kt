package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
    // Extended accent colors (15 additional)
    const val ACCENT_LIME = 9
    const val ACCENT_AMBER = 10
    const val ACCENT_ROSE = 11
    const val ACCENT_VIOLET = 12
    const val ACCENT_EMERALD = 13
    const val ACCENT_SKY = 14
    const val ACCENT_FUCHSIA = 15
    const val ACCENT_SLATE = 16
    const val ACCENT_CORAL = 17
    const val ACCENT_MINT = 18
    const val ACCENT_LAVENDER = 19
    const val ACCENT_PEACH = 20
    const val ACCENT_FOREST = 21
    const val ACCENT_CRIMSON = 22
    const val ACCENT_OCEAN = 23
    // Premium accent colors (16 additional)
    const val ACCENT_TURQUOISE = 24
    const val ACCENT_MAGENTA = 25
    const val ACCENT_GOLD = 26
    const val ACCENT_SAPPHIRE = 27
    const val ACCENT_RUBY = 28
    const val ACCENT_JADE = 29
    const val ACCENT_BRONZE = 30
    const val ACCENT_SILVER = 31
    const val ACCENT_COPPER = 32
    const val ACCENT_PEARL = 33
    const val ACCENT_ONYX = 34
    const val ACCENT_IVORY = 35
    const val ACCENT_CHAMPAGNE = 36
    const val ACCENT_PLUM = 37
    const val ACCENT_ORCHID = 38
    const val ACCENT_STORM = 39

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

        // Broadcast theme change event with extra data
        val intent = Intent(ACTION_THEME_CHANGED).apply {
            putExtra("theme_mode", themeMode)
            putExtra("accent_color", accentColor)
        }
        context.sendBroadcast(intent)

        // If this is an activity, recreate it to apply the new theme
        if (context is Activity) {
            Timber.tag(TAG).d("Recreating activity to apply theme")
            // Small delay to ensure preferences are saved
            context.window.decorView.postDelayed({
                // Apply dynamic colors before recreating
                applyDynamicColors(context as Activity)
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
            // Extended accent colors
            ACCENT_LIME -> "Lime"
            ACCENT_AMBER -> "Amber"
            ACCENT_ROSE -> "Rose"
            ACCENT_VIOLET -> "Violet"
            ACCENT_EMERALD -> "Emerald"
            ACCENT_SKY -> "Sky"
            ACCENT_FUCHSIA -> "Fuchsia"
            ACCENT_SLATE -> "Slate"
            ACCENT_CORAL -> "Coral"
            ACCENT_MINT -> "Mint"
            ACCENT_LAVENDER -> "Lavender"
            ACCENT_PEACH -> "Peach"
            ACCENT_FOREST -> "Forest"
            ACCENT_CRIMSON -> "Crimson"
            ACCENT_OCEAN -> "Ocean"
            // Premium accent colors
            ACCENT_TURQUOISE -> "Turquoise"
            ACCENT_MAGENTA -> "Magenta"
            ACCENT_GOLD -> "Gold"
            ACCENT_SAPPHIRE -> "Sapphire"
            ACCENT_RUBY -> "Ruby"
            ACCENT_JADE -> "Jade"
            ACCENT_BRONZE -> "Bronze"
            ACCENT_SILVER -> "Silver"
            ACCENT_COPPER -> "Copper"
            ACCENT_PEARL -> "Pearl"
            ACCENT_ONYX -> "Onyx"
            ACCENT_IVORY -> "Ivory"
            ACCENT_CHAMPAGNE -> "Champagne"
            ACCENT_PLUM -> "Plum"
            ACCENT_ORCHID -> "Orchid"
            ACCENT_STORM -> "Storm"
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
            // Extended accent colors
            ACCENT_LIME -> R.color.accent_lime
            ACCENT_AMBER -> R.color.accent_amber
            ACCENT_ROSE -> R.color.accent_rose
            ACCENT_VIOLET -> R.color.accent_violet
            ACCENT_EMERALD -> R.color.accent_emerald
            ACCENT_SKY -> R.color.accent_sky
            ACCENT_FUCHSIA -> R.color.accent_fuchsia
            ACCENT_SLATE -> R.color.accent_slate
            ACCENT_CORAL -> R.color.accent_coral
            ACCENT_MINT -> R.color.accent_mint
            ACCENT_LAVENDER -> R.color.accent_lavender
            ACCENT_PEACH -> R.color.accent_peach
            ACCENT_FOREST -> R.color.accent_forest
            ACCENT_CRIMSON -> R.color.accent_crimson
            ACCENT_OCEAN -> R.color.accent_ocean
            // Premium accent colors
            ACCENT_TURQUOISE -> R.color.accent_turquoise
            ACCENT_MAGENTA -> R.color.accent_magenta
            ACCENT_GOLD -> R.color.accent_gold
            ACCENT_SAPPHIRE -> R.color.accent_sapphire
            ACCENT_RUBY -> R.color.accent_ruby
            ACCENT_JADE -> R.color.accent_jade
            ACCENT_BRONZE -> R.color.accent_bronze
            ACCENT_SILVER -> R.color.accent_silver
            ACCENT_COPPER -> R.color.accent_copper
            ACCENT_PEARL -> R.color.accent_pearl
            ACCENT_ONYX -> R.color.accent_onyx
            ACCENT_IVORY -> R.color.accent_ivory
            ACCENT_CHAMPAGNE -> R.color.accent_champagne
            ACCENT_PLUM -> R.color.accent_plum
            ACCENT_ORCHID -> R.color.accent_orchid
            ACCENT_STORM -> R.color.accent_storm
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
     * Apply dynamic accent colors to an activity with enhanced status bar theming
     */
    fun applyDynamicColors(activity: Activity) {
        try {
            // Apply status bar theming based on user preference
            applyAccentStatusBarTheming(activity)
            
            // Apply system UI appearance
            applySystemUIAppearance(activity)
            
            Log.d(TAG, "Applied dynamic accent colors and status bar theming")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dynamic colors", e)
        }
    }
    
    /**
     * Apply accent color to status bar with appropriate text contrast
     */
    fun applyAccentStatusBarTheming(activity: Activity) {
        try {
            // Check if user has enabled accent status bar theming
            if (!isAccentStatusBarEnabled(activity)) {
                // Apply default system status bar theming instead
                applySystemUIAppearance(activity)
                return
            }
            
            val window = activity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val accentColor = getCurrentAccentColor(activity)
                val isDarkMode = isDarkMode(activity)
                
                // Create a darker variant of accent color for status bar
                val statusBarColor = if (isDarkMode) {
                    // In dark mode, use a slightly darker accent
                    darkenColor(accentColor, 0.3f)
                } else {
                    // In light mode, use a moderately darker accent
                    darkenColor(accentColor, 0.2f)
                }
                
                window.statusBarColor = statusBarColor
                
                // Set appropriate status bar content color
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    controller?.let {
                        // Determine if we need light or dark content based on accent color brightness
                        if (isColorLight(statusBarColor)) {
                            // Dark content on light background
                            it.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        } else {
                            // Light content on dark background
                            it.setSystemBarsAppearance(
                                0,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        }
                    }
                } else {
                    // Legacy approach for older APIs
                    @Suppress("DEPRECATION")
                    val flags = window.decorView.systemUiVisibility
                    window.decorView.systemUiVisibility = if (isColorLight(statusBarColor)) {
                        flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying accent status bar theming", e)
        }
    }
    
    /**
     * Get the current accent color as an integer
     */
    fun getCurrentAccentColor(context: Context): Int {
        val colorRes = getAccentColorResource(context)
        return ContextCompat.getColor(context, colorRes)
    }
    
    /**
     * Apply accent color to a view (for dynamic theming)
     */
    fun applyAccentColorToView(context: Context, view: View) {
        val accentColor = getCurrentAccentColor(context)
        
        when (view) {
            is com.google.android.material.button.MaterialButton -> {
                view.backgroundTintList = ColorStateList.valueOf(accentColor)
            }
            is androidx.appcompat.widget.AppCompatImageView -> {
                view.imageTintList = ColorStateList.valueOf(accentColor)
            }
            else -> {
                // For other views, try to apply as background tint
                view.backgroundTintList = ColorStateList.valueOf(accentColor)
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
        
        // Use surface color for navigation bar
        window.navigationBarColor = surface
        
        // Status bar color is handled by applyAccentStatusBarTheming method
        // Only fall back to surface if accent theming is not applied
        if (!isAccentStatusBarEnabled(activity)) {
            window.statusBarColor = surface
        }
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
            ACCENT_CYAN to "Cyan",
            // Extended accent colors
            ACCENT_LIME to "Lime",
            ACCENT_AMBER to "Amber",
            ACCENT_ROSE to "Rose",
            ACCENT_VIOLET to "Violet",
            ACCENT_EMERALD to "Emerald",
            ACCENT_SKY to "Sky",
            ACCENT_FUCHSIA to "Fuchsia",
            ACCENT_SLATE to "Slate",
            ACCENT_CORAL to "Coral",
            ACCENT_MINT to "Mint",
            ACCENT_LAVENDER to "Lavender",
            ACCENT_PEACH to "Peach",
            ACCENT_FOREST to "Forest",
            ACCENT_CRIMSON to "Crimson",
            ACCENT_OCEAN to "Ocean",
            // Premium accent colors
            ACCENT_TURQUOISE to "Turquoise",
            ACCENT_MAGENTA to "Magenta",
            ACCENT_GOLD to "Gold",
            ACCENT_SAPPHIRE to "Sapphire",
            ACCENT_RUBY to "Ruby",
            ACCENT_JADE to "Jade",
            ACCENT_BRONZE to "Bronze",
            ACCENT_SILVER to "Silver",
            ACCENT_COPPER to "Copper",
            ACCENT_PEARL to "Pearl",
            ACCENT_ONYX to "Onyx",
            ACCENT_IVORY to "Ivory",
            ACCENT_CHAMPAGNE to "Champagne",
            ACCENT_PLUM to "Plum",
            ACCENT_ORCHID to "Orchid",
            ACCENT_STORM to "Storm"
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
    
    /**
     * Darken a color by a given factor
     */
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] *= (1.0f - factor) // Reduce brightness
        return android.graphics.Color.HSVToColor(hsv)
    }
    
    /**
     * Determine if a color is considered light (for contrast purposes)
     */
    private fun isColorLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 
                            0.587 * android.graphics.Color.green(color) + 
                            0.114 * android.graphics.Color.blue(color)) / 255
        return darkness < 0.5
    }
    
    /**
     * Check if accent status bar theming should be enabled
     */
    private fun isAccentStatusBarEnabled(context: Context): Boolean {
        return PrefsManager.isAccentStatusBarEnabled(context)
    }

    /**
     * Enable or disable accent color theming for status bar
     */
    fun setAccentStatusBarEnabled(context: Context, enabled: Boolean) {
        PrefsManager.setAccentStatusBarEnabled(context, enabled)
        
        // If context is an Activity, immediately apply the change
        if (context is Activity) {
            applyAccentStatusBarTheming(context)
        }
    }

}