package com.cyberflux.qwinai.utils

import android.app.Activity
import android.util.Log
import android.view.View

/**
 * Helper for applying gradient backgrounds to activities
 * Note: Theme changes should be done through setTheme() before onCreate()
 */
object DirectThemeApplier {
    private const val TAG = "DirectThemeApplier"

    /**
     * Apply gradient background to activity if enabled
     * This should be called AFTER setContentView()
     */
    fun applyGradientToActivity(activity: Activity) {
        if (!ThemeManager.areGradientsEnabled(activity)) {
            Log.d(TAG, "Gradients not enabled, skipping")
            return
        }

        val themeStyle = ThemeManager.getSavedThemeStyle(activity)

        // Only vibrant themes support gradients
        if (themeStyle < ThemeManager.THEME_NEON) {
            Log.d(TAG, "Theme $themeStyle doesn't support gradients")
            return
        }

        try {
            // Apply gradient to root view
            val rootView = activity.findViewById<View>(android.R.id.content)
            if (rootView != null) {
                ThemeManager.applyGradientBackground(activity, rootView)
                Log.d(TAG, "Gradient background applied for theme: $themeStyle")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying gradient: ${e.message}", e)
        }
    }
}