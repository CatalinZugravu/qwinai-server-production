package com.cyberflux.qwinai.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.cyberflux.qwinai.R

/**
 * Simplified UI Design Manager for Material Design 3
 * 
 * This has been simplified to support only the standard Material Design 3 layout.
 * The old multiple design style system (glass, neural, gradient) has been replaced
 * with Material Design 3 theming that adapts through theme attributes.
 * 
 * @deprecated This class is maintained for compatibility but the complex design
 * switching has been replaced with Material Design 3 theme attributes.
 */
@Deprecated("Use Material Design 3 theme attributes instead of layout switching")
object UIDesignManager {

    private const val PREF_KEY = "ui_design_style"
    private const val DEFAULT_STYLE = "standard"

    // Only standard design is now supported
    const val STYLE_STANDARD = "standard"

    /**
     * Gets the current UI design style from preferences
     * Always returns "standard" since we've migrated to Material Design 3
     */
    fun getCurrentDesignStyle(context: Context): String {
        // Always return standard since we've migrated to MD3
        return STYLE_STANDARD
    }

    /**
     * Gets the layout resource ID for StartActivity
     * Always returns the standard layout since we've migrated to Material Design 3
     */
    fun getStartActivityLayoutId(context: Context): Int {
        return R.layout.activity_start
    }

    /**
     * Gets the layout resource ID for HomeFragment
     * Always returns the standard layout since we've migrated to Material Design 3
     */
    fun getHomeFragmentLayoutId(context: Context): Int {
        return R.layout.fragment_home
    }

    /**
     * Inflates the layout for StartActivity
     */
    fun inflateStartActivityLayout(context: Context, inflater: LayoutInflater): android.view.View {
        return inflater.inflate(R.layout.activity_start, null)
    }

    /**
     * Inflates the layout for HomeFragment
     */
    fun inflateHomeFragmentLayout(
        context: Context, 
        inflater: LayoutInflater, 
        container: ViewGroup?
    ): android.view.View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    /**
     * Always returns false since we only use standard Material Design 3 layout
     */
    fun usesAlternativeLayout(context: Context): Boolean {
        return false
    }

    /**
     * Gets display name for UI design style
     */
    fun getDisplayName(style: String): String {
        return "Standard"
    }

    /**
     * Sets the UI design style preference
     * Note: This has no effect since we've migrated to Material Design 3
     */
    fun setDesignStyle(context: Context, style: String) {
        // No-op since we only support standard Material Design 3
    }

    /**
     * Always returns false since we don't switch layouts anymore
     */
    fun requiresRestart(context: Context, newStyle: String): Boolean {
        return false
    }
}