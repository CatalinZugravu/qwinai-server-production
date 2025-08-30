package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R

/**
 * Simplified Material Design 3 Theme Resource Utility
 * 
 * This replaces the complex old theme system with Material Design 3 theme attributes.
 * Instead of mapping specific theme styles to colors, we now use theme attributes
 * that automatically adapt to light/dark mode.
 * 
 * @deprecated Most functionality should use theme attributes directly (e.g., ?attr/colorPrimary)
 */
@Deprecated("Use theme attributes directly in layouts and ContextCompat.getColor() in code")
object ThemeResources {

    /**
     * Simplified color types that map to Material Design 3 theme attributes
     */
    enum class ColorType {
        PRIMARY,
        SECONDARY,
        TERTIARY,
        SURFACE,
        BACKGROUND,
        ERROR,
        ON_PRIMARY,
        ON_SECONDARY,
        ON_SURFACE,
        ON_BACKGROUND,
        OUTLINE
    }

    /**
     * Get color from Material Design 3 theme attributes
     * @deprecated Use ContextCompat.getColor() with theme attributes instead
     */
    @Deprecated("Use ContextCompat.getColor() with theme attributes")
    @ColorInt
    fun getColor(context: Context, colorType: ColorType): Int {
        return when (colorType) {
            ColorType.PRIMARY -> ContextCompat.getColor(context, R.color.md_theme_light_primary)
            ColorType.SECONDARY -> ContextCompat.getColor(context, R.color.md_theme_light_secondary)
            ColorType.TERTIARY -> ContextCompat.getColor(context, R.color.md_theme_light_tertiary)
            ColorType.SURFACE -> ContextCompat.getColor(context, R.color.md_theme_light_surface)
            ColorType.BACKGROUND -> ContextCompat.getColor(context, R.color.md_theme_light_background)
            ColorType.ERROR -> ContextCompat.getColor(context, R.color.md_theme_light_error)
            ColorType.ON_PRIMARY -> ContextCompat.getColor(context, R.color.md_theme_light_onPrimary)
            ColorType.ON_SECONDARY -> ContextCompat.getColor(context, R.color.md_theme_light_onSecondary)
            ColorType.ON_SURFACE -> ContextCompat.getColor(context, R.color.md_theme_light_onSurface)
            ColorType.ON_BACKGROUND -> ContextCompat.getColor(context, R.color.md_theme_light_onBackground)
            ColorType.OUTLINE -> ContextCompat.getColor(context, R.color.md_theme_light_outline)
        }
    }

    /**
     * Get ColorStateList for Material Design 3 colors
     * @deprecated Use theme attributes in layouts instead
     */
    @Deprecated("Use theme attributes in layouts")
    fun getColorStateList(context: Context, colorType: ColorType): ColorStateList? {
        val color = getColor(context, colorType)
        return ColorStateList.valueOf(color)
    }

    /**
     * Note: Gradient functionality has been removed in favor of Material Design 3 simplicity
     */
    @Deprecated("Gradients are no longer supported in the simplified theme system")
    fun getGradientDrawable(context: Context): Drawable? {
        // Gradients are no longer supported
        return null
    }
}