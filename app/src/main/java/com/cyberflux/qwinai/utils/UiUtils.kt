package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsetsController

object UiUtils {
    /**
     * Configure status bar appearance based on current theme
     * Note: This is now handled by ThemeManager.applySystemUIAppearance()
     */
    @Deprecated("Use ThemeManager.applySystemUIAppearance() instead")
    fun configureStatusBar(activity: Activity) {
        // This method is deprecated - use ThemeManager.applySystemUIAppearance() instead
        ThemeManager.applySystemUIAppearance(activity)
    }
}