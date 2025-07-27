package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.cyberflux.qwinai.R
import com.google.android.material.card.MaterialCardView

/**
 * Comprehensive theme resource utility that handles all theme-related color, drawable,
 * and styling operations. This single class centralizes theme logic and provides
 * easy extension functions for applying themes to UI components.
 */
object ThemeResources {

    /**
     * Main color groups used throughout the app
     */
    enum class ColorType {
        // Base theme colors
        PRIMARY,
        PRIMARY_VARIANT,
        SECONDARY,
        ACCENT,
        BACKGROUND,
        SURFACE,
        ERROR,

        // Text colors
        TEXT_PRIMARY,
        TEXT_SECONDARY,
        TEXT_TERTIARY,
        TEXT_DISABLED,
        HINT_COLOR,

        // Card colors
        CARD_BACKGROUND,
        CARD_STROKE,
        CARD_GREETING_BACKGROUND,
        CARD_TIP_BACKGROUND,
        CARD_WEATHER_BACKGROUND,
        CARD_WHATS_NEW_BACKGROUND,
        CARD_UPGRADE_BACKGROUND,
        CARD_MODEL_BACKGROUND,
        DISABLED_CARD_BACKGROUND,

        // Feature card colors
        FEATURE_WEB_SEARCH,
        FEATURE_IMAGE_UPLOAD,
        FEATURE_ASK_BY_LINK,
        FEATURE_PROMPT_DAY,
        FEATURE_IMAGE_GEN,
        FEATURE_OCR,
        FEATURE_TRANSLATOR,

        // UI element colors
        NOTIFICATION_COLOR,
        STATUS_ACTIVE,
        BUTTON_SECONDARY,
        LINK_COLOR,
        TAB_ICON_COLOR,
        TAB_RIPPLE_COLOR,

        // Code syntax highlighting colors
        CODE_KEYWORD,
        CODE_FUNCTION,
        CODE_CLASS,
        CODE_PROPERTY,
        CODE_ANNOTATION,
        CODE_GENERIC,
        CODE_BUILTIN,
        CODE_STRING,
        CODE_NUMBER,
        CODE_COMMENT,
        CODE_OPERATOR,
        CODE_PUNCTUATION,
        CODE_TAG,
        CODE_ATTRIBUTE,
        CODE_DOCTYPE,
        CODE_SELECTOR,
        CODE_VALUE,
        CODE_COLOR,
        CODE_IMPORTANT,
        CODE_AT_RULE,
        CODE_SELF,
        CODE_DECORATOR,
        CODE_LINQ,
        CODE_PREPROCESSOR,
        CODE_TYPE,
        CODE_INTERPOLATION,
        CODE_TEXT,

        // Special colors
        ACTIVE_BRANCH,
        INACTIVE_BRANCH_BG,
        INACTIVE_BRANCH_TEXT,
        VERSION_INDICATOR,
        ROUNDED_VERSION_CONTROL_BACKGROUND,

        // Gradient colors (for vibrant themes)
        GRADIENT_START,
        GRADIENT_MID,
        GRADIENT_END,
        GRADIENT_ACCENT_START,
        GRADIENT_ACCENT_END
    }

    /**
     * Feature types for feature-specific colors
     */
    enum class FeatureType {
        WEB_SEARCH,
        IMAGE_UPLOAD,
        ASK_BY_LINK,
        PROMPT_DAY,
        IMAGE_GEN,
        OCR,
        TRANSLATOR
    }

    /**
     * Card types for card-specific colors
     */
    enum class CardType {
        STANDARD,
        GREETING,
        TIP,
        WEATHER,
        WHATS_NEW,
        UPGRADE,
        MODEL,
        DISABLED
    }

    /**
     * Gets the color resource ID for a specific color type based on the current theme
     * @param context The context used to access resources
     * @param colorType The type of color to retrieve
     * @return The color resource ID for the requested color type
     */
    @ColorRes
    fun getColorResourceId(context: Context, colorType: ColorType): Int {
        val currentTheme = ThemeManager.getSavedThemeStyle(context)

        return when (colorType) {
            // Base theme colors
            ColorType.PRIMARY -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_primary
                ThemeManager.THEME_OLIVE -> R.color.olive_primary
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_primary
                ThemeManager.THEME_COPPER -> R.color.copper_primary
                ThemeManager.THEME_CARBON -> R.color.carbon_primary
                ThemeManager.THEME_TEAL -> R.color.teal_primary
                ThemeManager.THEME_SERENITY -> R.color.serenity_primary
                ThemeManager.THEME_NEON -> R.color.neon_primary
                ThemeManager.THEME_AURORA -> R.color.aurora_primary
                ThemeManager.THEME_ROYAL -> R.color.royal_primary
                ThemeManager.THEME_TROPICAL -> R.color.tropical_primary
                ThemeManager.THEME_MONOCHROME -> R.color.mono_primary
                ThemeManager.THEME_COSMIC -> R.color.cosmic_primary
                ThemeManager.THEME_VIVID -> R.color.vivid_primary
                else -> R.color.colorPrimary
            }
            ColorType.PRIMARY_VARIANT -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_primary_variant
                ThemeManager.THEME_OLIVE -> R.color.olive_primary_variant
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_primary_variant
                ThemeManager.THEME_COPPER -> R.color.copper_primary_variant
                ThemeManager.THEME_CARBON -> R.color.carbon_primary_variant
                ThemeManager.THEME_TEAL -> R.color.teal_primary_variant
                ThemeManager.THEME_SERENITY -> R.color.serenity_primary_variant
                ThemeManager.THEME_NEON -> R.color.neon_primary_variant
                ThemeManager.THEME_AURORA -> R.color.aurora_primary_variant
                ThemeManager.THEME_ROYAL -> R.color.royal_primary_variant
                ThemeManager.THEME_TROPICAL -> R.color.tropical_primary_variant
                ThemeManager.THEME_MONOCHROME -> R.color.mono_primary_variant
                ThemeManager.THEME_COSMIC -> R.color.cosmic_primary_variant
                ThemeManager.THEME_VIVID -> R.color.vivid_primary_variant
                else -> R.color.colorPrimaryVariant
            }
            ColorType.SECONDARY -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_secondary
                ThemeManager.THEME_OLIVE -> R.color.olive_secondary
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_secondary
                ThemeManager.THEME_COPPER -> R.color.copper_secondary
                ThemeManager.THEME_CARBON -> R.color.carbon_secondary
                ThemeManager.THEME_TEAL -> R.color.teal_secondary
                ThemeManager.THEME_SERENITY -> R.color.serenity_secondary
                ThemeManager.THEME_NEON -> R.color.neon_secondary
                ThemeManager.THEME_AURORA -> R.color.aurora_secondary
                ThemeManager.THEME_ROYAL -> R.color.royal_secondary
                ThemeManager.THEME_TROPICAL -> R.color.tropical_secondary
                ThemeManager.THEME_MONOCHROME -> R.color.mono_secondary
                ThemeManager.THEME_COSMIC -> R.color.cosmic_secondary
                ThemeManager.THEME_VIVID -> R.color.vivid_secondary
                else -> R.color.colorSecondary
            }
            ColorType.ACCENT -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_accent
                ThemeManager.THEME_OLIVE -> R.color.olive_accent
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_accent
                ThemeManager.THEME_COPPER -> R.color.copper_accent
                ThemeManager.THEME_CARBON -> R.color.carbon_accent
                ThemeManager.THEME_TEAL -> R.color.teal_accent
                ThemeManager.THEME_SERENITY -> R.color.serenity_accent
                ThemeManager.THEME_NEON -> R.color.neon_accent
                ThemeManager.THEME_AURORA -> R.color.aurora_accent
                ThemeManager.THEME_ROYAL -> R.color.royal_accent
                ThemeManager.THEME_TROPICAL -> R.color.tropical_accent
                ThemeManager.THEME_MONOCHROME -> R.color.mono_accent
                ThemeManager.THEME_COSMIC -> R.color.cosmic_accent
                ThemeManager.THEME_VIVID -> R.color.vivid_accent
                else -> R.color.colorAccent
            }
            ColorType.BACKGROUND -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_background
                ThemeManager.THEME_OLIVE -> R.color.olive_background
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_background
                ThemeManager.THEME_COPPER -> R.color.copper_background
                ThemeManager.THEME_CARBON -> R.color.carbon_background
                ThemeManager.THEME_TEAL -> R.color.teal_background
                ThemeManager.THEME_SERENITY -> R.color.serenity_background
                ThemeManager.THEME_NEON -> R.color.neon_background
                ThemeManager.THEME_AURORA -> R.color.aurora_background
                ThemeManager.THEME_ROYAL -> R.color.royal_background
                ThemeManager.THEME_TROPICAL -> R.color.tropical_background
                ThemeManager.THEME_MONOCHROME -> R.color.mono_background
                ThemeManager.THEME_COSMIC -> R.color.cosmic_background
                ThemeManager.THEME_VIVID -> R.color.vivid_background
                else -> R.color.background
            }
            ColorType.SURFACE -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_surface
                ThemeManager.THEME_OLIVE -> R.color.olive_surface
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_surface
                ThemeManager.THEME_COPPER -> R.color.copper_surface
                ThemeManager.THEME_CARBON -> R.color.carbon_surface
                ThemeManager.THEME_TEAL -> R.color.teal_surface
                ThemeManager.THEME_SERENITY -> R.color.serenity_surface
                ThemeManager.THEME_NEON -> R.color.neon_surface
                ThemeManager.THEME_AURORA -> R.color.aurora_surface
                ThemeManager.THEME_ROYAL -> R.color.royal_surface
                ThemeManager.THEME_TROPICAL -> R.color.tropical_surface
                ThemeManager.THEME_MONOCHROME -> R.color.mono_surface
                ThemeManager.THEME_COSMIC -> R.color.cosmic_surface
                ThemeManager.THEME_VIVID -> R.color.vivid_surface
                else -> R.color.surface
            }
            ColorType.ERROR -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_error
                ThemeManager.THEME_OLIVE -> R.color.olive_error
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_error
                ThemeManager.THEME_COPPER -> R.color.copper_error
                ThemeManager.THEME_CARBON -> R.color.carbon_error
                ThemeManager.THEME_TEAL -> R.color.teal_error
                ThemeManager.THEME_SERENITY -> R.color.serenity_error
                ThemeManager.THEME_NEON -> R.color.neon_error
                ThemeManager.THEME_AURORA -> R.color.aurora_error
                ThemeManager.THEME_ROYAL -> R.color.royal_error
                ThemeManager.THEME_TROPICAL -> R.color.tropical_error
                ThemeManager.THEME_MONOCHROME -> R.color.mono_error
                ThemeManager.THEME_COSMIC -> R.color.cosmic_error
                ThemeManager.THEME_VIVID -> R.color.vivid_error
                else -> R.color.error
            }

            // Text colors
            ColorType.TEXT_PRIMARY -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_text_primary
                ThemeManager.THEME_OLIVE -> R.color.olive_text_primary
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_text_primary
                ThemeManager.THEME_COPPER -> R.color.copper_text_primary
                ThemeManager.THEME_CARBON -> R.color.carbon_text_primary
                ThemeManager.THEME_TEAL -> R.color.teal_text_primary
                ThemeManager.THEME_SERENITY -> R.color.serenity_text_primary
                ThemeManager.THEME_NEON -> R.color.neon_text_primary
                ThemeManager.THEME_AURORA -> R.color.aurora_text_primary
                ThemeManager.THEME_ROYAL -> R.color.royal_text_primary
                ThemeManager.THEME_TROPICAL -> R.color.tropical_text_primary
                ThemeManager.THEME_MONOCHROME -> R.color.mono_text_primary
                ThemeManager.THEME_COSMIC -> R.color.cosmic_text_primary
                ThemeManager.THEME_VIVID -> R.color.vivid_text_primary
                else -> R.color.text_primary
            }
            ColorType.TEXT_SECONDARY -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_text_secondary
                ThemeManager.THEME_OLIVE -> R.color.olive_text_secondary
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_text_secondary
                ThemeManager.THEME_COPPER -> R.color.copper_text_secondary
                ThemeManager.THEME_CARBON -> R.color.carbon_text_secondary
                ThemeManager.THEME_TEAL -> R.color.teal_text_secondary
                ThemeManager.THEME_SERENITY -> R.color.serenity_text_secondary
                ThemeManager.THEME_NEON -> R.color.neon_text_secondary
                ThemeManager.THEME_AURORA -> R.color.aurora_text_secondary
                ThemeManager.THEME_ROYAL -> R.color.royal_text_secondary
                ThemeManager.THEME_TROPICAL -> R.color.tropical_text_secondary
                ThemeManager.THEME_MONOCHROME -> R.color.mono_text_secondary
                ThemeManager.THEME_COSMIC -> R.color.cosmic_text_secondary
                ThemeManager.THEME_VIVID -> R.color.vivid_text_secondary
                else -> R.color.text_secondary
            }

            // For additional color types like TEXT_TERTIARY, etc., use a similar pattern or implement
            // a fallback strategy to derive them from existing theme colors

            // For card colors, handle specific card types
            ColorType.CARD_BACKGROUND -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_card_background
                ThemeManager.THEME_OLIVE -> R.color.olive_card_background
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_card_background
                ThemeManager.THEME_COPPER -> R.color.copper_card_background
                ThemeManager.THEME_CARBON -> R.color.carbon_card_background
                ThemeManager.THEME_TEAL -> R.color.teal_card_background
                ThemeManager.THEME_SERENITY -> R.color.serenity_card_background
                ThemeManager.THEME_NEON -> R.color.neon_card_background
                ThemeManager.THEME_AURORA -> R.color.aurora_card_background
                ThemeManager.THEME_ROYAL -> R.color.royal_card_background
                ThemeManager.THEME_TROPICAL -> R.color.tropical_card_background
                ThemeManager.THEME_MONOCHROME -> R.color.mono_card_background
                ThemeManager.THEME_COSMIC -> R.color.cosmic_card_background
                ThemeManager.THEME_VIVID -> R.color.vivid_card_background
                else -> R.color.card_background
            }
            ColorType.CARD_STROKE -> when (currentTheme) {
                ThemeManager.THEME_SLATE -> R.color.slate_card_stroke
                ThemeManager.THEME_OLIVE -> R.color.olive_card_stroke
                ThemeManager.THEME_BURGUNDY -> R.color.burgundy_card_stroke
                ThemeManager.THEME_COPPER -> R.color.copper_card_stroke
                ThemeManager.THEME_CARBON -> R.color.carbon_card_stroke
                ThemeManager.THEME_TEAL -> R.color.teal_card_stroke
                ThemeManager.THEME_SERENITY -> R.color.serenity_card_stroke
                ThemeManager.THEME_NEON -> R.color.neon_card_stroke
                ThemeManager.THEME_AURORA -> R.color.aurora_card_stroke
                ThemeManager.THEME_ROYAL -> R.color.royal_card_stroke
                ThemeManager.THEME_TROPICAL -> R.color.tropical_card_stroke
                ThemeManager.THEME_MONOCHROME -> R.color.mono_card_stroke
                ThemeManager.THEME_COSMIC -> R.color.cosmic_card_stroke
                ThemeManager.THEME_VIVID -> R.color.vivid_card_stroke
                else -> R.color.card_stroke
            }

            // Add cases for other specialized card types (greeting, tip, etc.)
            // For example:
            ColorType.CARD_GREETING_BACKGROUND -> {
                // For themes that don't define a specific greeting card background,
                // fall back to the standard card background
                when (currentTheme) {
                    // Add theme-specific greeting card colors if available
                    else -> getColorResourceId(context, ColorType.CARD_BACKGROUND)
                }
            }

            // Feature card colors
            ColorType.FEATURE_WEB_SEARCH -> {
                when (currentTheme) {
                    // Add theme-specific feature colors when available
                    ThemeManager.THEME_DEFAULT -> R.color.feature_web_search
                    else -> {
                        // If no specific color is defined, derive from primary color
                        // with a slight adjustment to make it suitable for the feature
                        getColorResourceId(context, ColorType.PRIMARY)
                    }
                }
            }
            // Similar pattern for other feature colors

            // For code syntax highlighting
            ColorType.CODE_KEYWORD -> {
                // These colors can follow a similar pattern, or for code highlighting,
                // you might want to keep consistent colors across themes for readability
                R.color.code_keyword
            }

            // Gradient colors for vibrant themes
            ColorType.GRADIENT_START -> when (currentTheme) {
                ThemeManager.THEME_NEON -> R.color.neon_gradient_start
                ThemeManager.THEME_AURORA -> R.color.aurora_gradient_start
                ThemeManager.THEME_ROYAL -> R.color.royal_gradient_start
                ThemeManager.THEME_TROPICAL -> R.color.tropical_gradient_start
                ThemeManager.THEME_COSMIC -> R.color.cosmic_gradient_start
                ThemeManager.THEME_VIVID -> R.color.vivid_gradient_start
                else -> getColorResourceId(context, ColorType.PRIMARY) // Fallback
            }

            // For other color types, implement fallback mechanisms
            else -> {
                // Default fallback logic - try to derive from existing theme colors
                deriveColorForType(context, colorType)
            }
        }
    }

    /**
     * Helper method to derive colors that aren't explicitly defined for a theme
     */
    private fun deriveColorForType(context: Context, colorType: ColorType): Int {
        return when (colorType) {
            // Text colors
            ColorType.TEXT_TERTIARY -> getColorResourceId(context, ColorType.TEXT_SECONDARY)
            ColorType.TEXT_DISABLED -> getColorResourceId(context, ColorType.TEXT_SECONDARY)
            ColorType.HINT_COLOR -> getColorResourceId(context, ColorType.TEXT_SECONDARY)

            // Card backgrounds
            ColorType.CARD_TIP_BACKGROUND,
            ColorType.CARD_WEATHER_BACKGROUND,
            ColorType.CARD_WHATS_NEW_BACKGROUND,
            ColorType.CARD_UPGRADE_BACKGROUND,
            ColorType.CARD_MODEL_BACKGROUND -> getColorResourceId(context, ColorType.CARD_BACKGROUND)

            ColorType.DISABLED_CARD_BACKGROUND -> {
                // For disabled cards, use a lighter/darker version of card background
                val isDark = isUsingDarkTheme(context)
                if (isDark) {
                    // Darken the card background for dark theme
                    getColorResourceId(context, ColorType.CARD_BACKGROUND)
                } else {
                    // Lighten for light theme
                    R.color.disabled_card_background
                }
            }

            // Feature colors - if not defined, derive from primary or accent
            ColorType.FEATURE_IMAGE_UPLOAD,
            ColorType.FEATURE_ASK_BY_LINK,
            ColorType.FEATURE_PROMPT_DAY,
            ColorType.FEATURE_IMAGE_GEN,
            ColorType.FEATURE_OCR,
            ColorType.FEATURE_TRANSLATOR -> getColorResourceId(context, ColorType.PRIMARY)

            // Status colors
            ColorType.STATUS_ACTIVE -> R.color.status_active
            ColorType.NOTIFICATION_COLOR -> R.color.notification_color

            // UI elements
            ColorType.BUTTON_SECONDARY -> {
                val isDark = isUsingDarkTheme(context)
                if (isDark) {
                    // Darker button background for dark theme
                    getColorResourceId(context, ColorType.SURFACE)
                } else {
                    // Light button background for light theme
                    R.color.button_secondary
                }
            }

            ColorType.LINK_COLOR -> getColorResourceId(context, ColorType.PRIMARY)

            // Tab colors
            ColorType.TAB_ICON_COLOR -> getColorResourceId(context, ColorType.TEXT_SECONDARY)
            ColorType.TAB_RIPPLE_COLOR -> {
                // Semi-transparent version of primary color
                getColorResourceId(context, ColorType.PRIMARY)
            }

            // For code syntax highlighting, default to standard colors if not theme-specific
            ColorType.CODE_FUNCTION,
            ColorType.CODE_CLASS,
            ColorType.CODE_PROPERTY,
            ColorType.CODE_ANNOTATION,
            ColorType.CODE_GENERIC,
            ColorType.CODE_BUILTIN,
            ColorType.CODE_STRING,
            ColorType.CODE_NUMBER,
            ColorType.CODE_COMMENT,
            ColorType.CODE_OPERATOR,
            ColorType.CODE_PUNCTUATION,
            ColorType.CODE_TAG,
            ColorType.CODE_ATTRIBUTE,
            ColorType.CODE_DOCTYPE,
            ColorType.CODE_SELECTOR,
            ColorType.CODE_VALUE,
            ColorType.CODE_COLOR,
            ColorType.CODE_IMPORTANT,
            ColorType.CODE_AT_RULE,
            ColorType.CODE_SELF,
            ColorType.CODE_DECORATOR,
            ColorType.CODE_LINQ,
            ColorType.CODE_PREPROCESSOR,
            ColorType.CODE_TYPE,
            ColorType.CODE_INTERPOLATION,
            ColorType.CODE_TEXT -> {
                // Use default code highlighting colors
                when (colorType) {
                    ColorType.CODE_FUNCTION -> R.color.code_function
                    ColorType.CODE_CLASS -> R.color.code_class
                    ColorType.CODE_PROPERTY -> R.color.code_property
                    ColorType.CODE_ANNOTATION -> R.color.code_annotation
                    ColorType.CODE_GENERIC -> R.color.code_generic
                    ColorType.CODE_BUILTIN -> R.color.code_builtin
                    ColorType.CODE_STRING -> R.color.code_string
                    ColorType.CODE_NUMBER -> R.color.code_number
                    ColorType.CODE_COMMENT -> R.color.code_comment
                    ColorType.CODE_OPERATOR -> R.color.code_operator
                    ColorType.CODE_PUNCTUATION -> R.color.code_punctuation
                    ColorType.CODE_TAG -> R.color.code_tag
                    ColorType.CODE_ATTRIBUTE -> R.color.code_attribute
                    ColorType.CODE_DOCTYPE -> R.color.code_doctype
                    ColorType.CODE_SELECTOR -> R.color.code_selector
                    ColorType.CODE_VALUE -> R.color.code_value
                    ColorType.CODE_COLOR -> R.color.code_color
                    ColorType.CODE_IMPORTANT -> R.color.code_important
                    ColorType.CODE_AT_RULE -> R.color.code_at_rule
                    ColorType.CODE_SELF -> R.color.code_self
                    ColorType.CODE_DECORATOR -> R.color.code_decorator
                    ColorType.CODE_LINQ -> R.color.code_linq
                    ColorType.CODE_PREPROCESSOR -> R.color.code_preprocessor
                    ColorType.CODE_TYPE -> R.color.code_type
                    ColorType.CODE_INTERPOLATION -> R.color.code_interpolation
                    ColorType.CODE_TEXT -> R.color.code_text
                    else -> R.color.code_text
                }
            }

            // Other specialized colors
            ColorType.ACTIVE_BRANCH -> getColorResourceId(context, ColorType.PRIMARY)
            ColorType.INACTIVE_BRANCH_BG -> R.color.inactive_branch_bg
            ColorType.INACTIVE_BRANCH_TEXT -> R.color.inactive_branch_text
            ColorType.VERSION_INDICATOR -> R.color.version_indicator
            ColorType.ROUNDED_VERSION_CONTROL_BACKGROUND -> R.color.rounded_version_control_background

            // Gradient fallbacks
            ColorType.GRADIENT_MID,
            ColorType.GRADIENT_END,
            ColorType.GRADIENT_ACCENT_START,
            ColorType.GRADIENT_ACCENT_END -> getColorResourceId(context, ColorType.PRIMARY)

            // If we haven't handled a color type, fall back to primary
            else -> getColorResourceId(context, ColorType.PRIMARY)
        }
    }

    /**
     * Gets the actual color value for a color type in the current theme
     */
    @ColorInt
    fun getColor(context: Context, colorType: ColorType): Int {
        val colorResId = getColorResourceId(context, colorType)
        return ContextCompat.getColor(context, colorResId)
    }

    /**
     * Checks if the current theme mode is dark
     */
    fun isUsingDarkTheme(context: Context): Boolean {
        return when (ThemeManager.getSavedThemeMode(context)) {
            ThemeManager.MODE_DARK -> true
            ThemeManager.MODE_LIGHT -> false
            else -> {
                // Check system night mode
                context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /**
     * Gets the appropriate gradient drawable for the current theme
     */
    @DrawableRes
    fun getGradientDrawableRes(context: Context, isAccent: Boolean = false): Int? {
        if (!ThemeManager.areGradientsEnabled(context)) return null

        val currentTheme = ThemeManager.getSavedThemeStyle(context)

        return when {
            isAccent -> when (currentTheme) {
                ThemeManager.THEME_NEON -> R.drawable.bg_neon_accent_gradient
                ThemeManager.THEME_COSMIC -> R.drawable.bg_cosmic_accent_gradient
                ThemeManager.THEME_VIVID -> R.drawable.bg_vivid_accent_gradient
                else -> null
            }
            else -> when (currentTheme) {
                ThemeManager.THEME_NEON -> R.drawable.bg_neon_gradient
                ThemeManager.THEME_AURORA -> R.drawable.bg_aurora_gradient
                ThemeManager.THEME_ROYAL -> R.drawable.bg_royal_gradient
                ThemeManager.THEME_TROPICAL -> R.drawable.bg_tropical_gradient
                ThemeManager.THEME_COSMIC -> R.drawable.bg_cosmic_gradient
                ThemeManager.THEME_VIVID -> R.drawable.bg_vivid_gradient
                else -> null
            }
        }
    }

    /**
     * Creates a dynamic gradient drawable using the current theme's colors
     */
    fun createGradientDrawable(context: Context, isAccent: Boolean = false): GradientDrawable? {
        if (!ThemeManager.areGradientsEnabled(context)) return null

        val startColor: Int
        val endColor: Int
        var centerColor: Int? = null

        if (isAccent) {
            startColor = getColor(context, ColorType.GRADIENT_ACCENT_START)
            endColor = getColor(context, ColorType.GRADIENT_ACCENT_END)
        } else {
            startColor = getColor(context, ColorType.GRADIENT_START)
            endColor = getColor(context, ColorType.GRADIENT_END)

            // Check if we need a 3-color gradient (like Aurora theme)
            val currentTheme = ThemeManager.getSavedThemeStyle(context)
            if (currentTheme == ThemeManager.THEME_AURORA) {
                centerColor = getColor(context, ColorType.GRADIENT_MID)
            }
        }

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (centerColor != null)
                intArrayOf(startColor, centerColor, endColor)
            else
                intArrayOf(startColor, endColor)
        )

        gradient.cornerRadius = context.resources.getDimension(R.dimen.standard_corner_radius)
        return gradient
    }

    /**
     * Gets the feature-specific color for the current theme
     */
    @ColorInt
    fun getFeatureColor(context: Context, featureType: FeatureType): Int {
        val colorType = when (featureType) {
            FeatureType.WEB_SEARCH -> ColorType.FEATURE_WEB_SEARCH
            FeatureType.IMAGE_UPLOAD -> ColorType.FEATURE_IMAGE_UPLOAD
            FeatureType.ASK_BY_LINK -> ColorType.FEATURE_ASK_BY_LINK
            FeatureType.PROMPT_DAY -> ColorType.FEATURE_PROMPT_DAY
            FeatureType.IMAGE_GEN -> ColorType.FEATURE_IMAGE_GEN
            FeatureType.OCR -> ColorType.FEATURE_OCR
            FeatureType.TRANSLATOR -> ColorType.FEATURE_TRANSLATOR
        }

        return getColor(context, colorType)
    }

    /**
     * Gets the card-specific background color for the current theme
     */
    @ColorInt
    fun getCardBackgroundColor(context: Context, cardType: CardType): Int {
        val colorType = when (cardType) {
            CardType.STANDARD -> ColorType.CARD_BACKGROUND
            CardType.GREETING -> ColorType.CARD_GREETING_BACKGROUND
            CardType.TIP -> ColorType.CARD_TIP_BACKGROUND
            CardType.WEATHER -> ColorType.CARD_WEATHER_BACKGROUND
            CardType.WHATS_NEW -> ColorType.CARD_WHATS_NEW_BACKGROUND
            CardType.UPGRADE -> ColorType.CARD_UPGRADE_BACKGROUND
            CardType.MODEL -> ColorType.CARD_MODEL_BACKGROUND
            CardType.DISABLED -> ColorType.DISABLED_CARD_BACKGROUND
        }

        return getColor(context, colorType)
    }

    /**
     * Gets code syntax highlighting color for the current theme
     */
    @ColorInt
    fun getCodeSyntaxColor(context: Context, syntaxElement: String): Int {
        val colorType = when (syntaxElement) {
            "keyword" -> ColorType.CODE_KEYWORD
            "function" -> ColorType.CODE_FUNCTION
            "class" -> ColorType.CODE_CLASS
            "property" -> ColorType.CODE_PROPERTY
            "annotation" -> ColorType.CODE_ANNOTATION
            "generic" -> ColorType.CODE_GENERIC
            "builtin" -> ColorType.CODE_BUILTIN
            "string" -> ColorType.CODE_STRING
            "number" -> ColorType.CODE_NUMBER
            "comment" -> ColorType.CODE_COMMENT
            "operator" -> ColorType.CODE_OPERATOR
            "punctuation" -> ColorType.CODE_PUNCTUATION
            "tag" -> ColorType.CODE_TAG
            "attribute" -> ColorType.CODE_ATTRIBUTE
            "doctype" -> ColorType.CODE_DOCTYPE
            "selector" -> ColorType.CODE_SELECTOR
            "value" -> ColorType.CODE_VALUE
            "color" -> ColorType.CODE_COLOR
            "important" -> ColorType.CODE_IMPORTANT
            "at-rule" -> ColorType.CODE_AT_RULE
            "self" -> ColorType.CODE_SELF
            "decorator" -> ColorType.CODE_DECORATOR
            "linq" -> ColorType.CODE_LINQ
            "preprocessor" -> ColorType.CODE_PREPROCESSOR
            "type" -> ColorType.CODE_TYPE
            "interpolation" -> ColorType.CODE_INTERPOLATION
            "text" -> ColorType.CODE_TEXT
            else -> ColorType.TEXT_PRIMARY
        }

        return getColor(context, colorType)
    }

    /**
     * Applies themed background to a view, using gradients if enabled
     */
    fun applyThemedBackground(view: View, colorType: ColorType, useGradient: Boolean = true) {
        val context = view.context

        // If gradients are enabled and requested, try to apply a gradient background
        if (useGradient && ThemeManager.areGradientsEnabled(context)) {
            val gradientDrawable = when (colorType) {
                ColorType.PRIMARY, ColorType.PRIMARY_VARIANT, ColorType.BACKGROUND, ColorType.SURFACE ->
                    createGradientDrawable(context, false)
                ColorType.ACCENT, ColorType.SECONDARY ->
                    createGradientDrawable(context, true)
                else -> null
            }

            if (gradientDrawable != null) {
                view.background = gradientDrawable
                return
            }
        }

        // If no gradient or gradients disabled, apply solid color
        view.setBackgroundColor(getColor(context, colorType))
    }

    /**
     * Applies a theme to a TextView
     */
    fun applyThemeToTextView(textView: TextView, textColorType: ColorType = ColorType.TEXT_PRIMARY) {
        val context = textView.context
        textView.setTextColor(getColor(context, textColorType))
    }

    /**
     * Applies a theme to a Button
     */
    fun applyThemeToButton(button: Button, isPrimary: Boolean = true, useGradient: Boolean = true) {
        val context = button.context

        // Choose color type based on button role
        val colorType = if (isPrimary) ColorType.PRIMARY else ColorType.SECONDARY

        // Apply background
        applyThemedBackground(button, colorType, useGradient)

        // Set text color (white for colored buttons)
        button.setTextColor(ContextCompat.getColor(context, android.R.color.white))

        // Apply ripple effect
        val rippleColor = ColorUtils.setAlphaComponent(
            getColor(context, if (isPrimary) ColorType.PRIMARY_VARIANT else ColorType.SECONDARY),
            128 // Semi-transparent
        )
        ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(rippleColor))
    }

    /**
     * Applies a theme to a CardView
     */
    fun applyThemeToCardView(cardView: CardView, cardType: CardType = CardType.STANDARD) {
        val context = cardView.context

        // Set card background color
        cardView.setCardBackgroundColor(getCardBackgroundColor(context, cardType))

        // If it's a MaterialCardView, also apply stroke
        if (cardView is MaterialCardView) {
            cardView.strokeColor = getColor(context, ColorType.CARD_STROKE)
            cardView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_width)
        }
    }

    /**
     * Applies a theme to a feature card/icon
     */
    fun applyThemeToFeatureView(view: View, featureType: FeatureType, useGradient: Boolean = true) {
        val context = view.context

        // For ImageView tinting
        if (view is ImageView) {
            view.imageTintList = ColorStateList.valueOf(getFeatureColor(context, featureType))
            return
        }

        // For background coloring
        if (useGradient && ThemeManager.areGradientsEnabled(context)) {
            // Try to create a feature-specific gradient if available
            val gradient = createGradientDrawable(context, featureType == FeatureType.WEB_SEARCH)
            if (gradient != null) {
                view.background = gradient
                return
            }
        }

        // Fall back to solid color
        view.setBackgroundColor(getFeatureColor(context, featureType))
    }

    /**
     * Apply the current theme to an activity
     */
    fun applyThemeToActivity(activity: AppCompatActivity) {
        // Apply status bar configuration
        UiUtils.configureStatusBar(activity)

        // Apply background color to root view
        val rootView = activity.findViewById<View>(android.R.id.content)
        rootView.setBackgroundColor(getColor(activity, ColorType.BACKGROUND))
    }
}

//
// Extension functions for easier use
//

/**
 * Extension function to get themed color
 */
@ColorInt
fun Context.getThemeColor(colorType: ThemeResources.ColorType): Int {
    return ThemeResources.getColor(this, colorType)
}

/**
 * Extension function to get themed color resource ID
 */
@ColorRes
fun Context.getThemeColorResourceId(colorType: ThemeResources.ColorType): Int {
    return ThemeResources.getColorResourceId(this, colorType)
}

/**
 * Extension function to apply themed background to a view
 */
fun View.applyThemedBackground(colorType: ThemeResources.ColorType, useGradient: Boolean = true) {
    ThemeResources.applyThemedBackground(this, colorType, useGradient)
}

/**
 * Extension function to apply themed text color to a TextView
 */
fun TextView.applyThemedTextColor(colorType: ThemeResources.ColorType = ThemeResources.ColorType.TEXT_PRIMARY) {
    ThemeResources.applyThemeToTextView(this, colorType)
}

/**
 * Extension function to apply theme to a Button
 */
fun Button.applyTheme(isPrimary: Boolean = true, useGradient: Boolean = true) {
    ThemeResources.applyThemeToButton(this, isPrimary, useGradient)
}

/**
 * Extension function to apply theme to a CardView
 */
fun CardView.applyTheme(cardType: ThemeResources.CardType = ThemeResources.CardType.STANDARD) {
    ThemeResources.applyThemeToCardView(this, cardType)
}

/**
 * Extension function to apply theme to a feature view
 */
fun View.applyFeatureTheme(featureType: ThemeResources.FeatureType, useGradient: Boolean = true) {
    ThemeResources.applyThemeToFeatureView(this, featureType, useGradient)
}

/**
 * Extension function to get code syntax color
 */
@ColorInt
fun Context.getCodeSyntaxColor(syntaxElement: String): Int {
    return ThemeResources.getCodeSyntaxColor(this, syntaxElement)
}