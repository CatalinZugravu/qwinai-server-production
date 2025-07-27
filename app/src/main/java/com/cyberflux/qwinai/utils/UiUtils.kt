package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R

object UiUtils {
    /**
     * Configure status bar appearance based on current theme
     * Note: This is now mostly handled by the theme itself
     */
    fun configureStatusBar(activity: Activity) {
        // The theme system now handles most of this automatically
        // This method is kept for backward compatibility

        // Only handle special cases like forcing light/dark status bar icons
        val isDarkMode = activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        // For some themes, we might need to override the default behavior
        val themeStyle = ThemeManager.getSavedThemeStyle(activity)

        // Example: Monochrome theme might need special handling
        if (themeStyle == ThemeManager.THEME_MONOCHROME) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.setSystemBarsAppearance(
                    if (!isDarkMode) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = if (!isDarkMode) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    0
                }
            }
        }
    }
}