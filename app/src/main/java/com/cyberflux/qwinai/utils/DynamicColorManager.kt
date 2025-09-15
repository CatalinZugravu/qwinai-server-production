package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import timber.log.Timber

/**
 * Manager for applying dynamic colors to UI components
 * Integrates with ThemeManager for consistent theming
 */
object DynamicColorManager {
    private const val TAG = "DynamicColorManager"

    /**
     * Apply accent color to a ViewGroup recursively
     */
    fun applyAccentColorToViewGroup(activity: Activity, viewGroup: ViewGroup) {
        try {
            val accentColor = ThemeManager.getCurrentAccentColor(activity)
            val accentColorStateList = ColorStateList.valueOf(accentColor)

            applyAccentColorRecursively(viewGroup, accentColorStateList)

            Timber.tag(TAG).d("Applied accent color to ViewGroup with ${viewGroup.childCount} children")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error applying accent color to ViewGroup")
        }
    }

    /**
     * Apply accent colors to common UI elements in a ViewGroup
     */
    fun applyAccentColorToCommonElements(activity: Activity, viewGroup: ViewGroup) {
        try {
            val accentColor = ThemeManager.getCurrentAccentColor(activity)
            val accentColorStateList = ColorStateList.valueOf(accentColor)

            // Apply to MaterialButtons
            findViewsByType<MaterialButton>(viewGroup).forEach { button ->
                button.backgroundTintList = accentColorStateList
            }

            // Apply to FloatingActionButtons
            findViewsByType<FloatingActionButton>(viewGroup).forEach { fab ->
                fab.backgroundTintList = accentColorStateList
            }

            Timber.tag(TAG).d("Applied accent color to common elements")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error applying accent color to common elements")
        }
    }

    /**
     * Apply accent color to a specific view
     */
    fun applyAccentColorToView(context: Context, view: View) {
        try {
            val accentColor = ThemeManager.getCurrentAccentColor(context)
            val accentColorStateList = ColorStateList.valueOf(accentColor)

            when (view) {
                is MaterialButton -> {
                    view.backgroundTintList = accentColorStateList
                }
                is FloatingActionButton -> {
                    view.backgroundTintList = accentColorStateList
                }
                else -> {
                    // Generic approach
                    view.backgroundTintList = accentColorStateList
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error applying accent color to view")
        }
    }

    /**
     * Recursively apply accent color to views that support it
     */
    private fun applyAccentColorRecursively(viewGroup: ViewGroup, accentColor: ColorStateList) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            when (child) {
                is MaterialButton -> {
                    child.backgroundTintList = accentColor
                }
                is FloatingActionButton -> {
                    child.backgroundTintList = accentColor
                }
                is ViewGroup -> {
                    // Recursively apply to nested ViewGroups
                    applyAccentColorRecursively(child, accentColor)
                }
            }
        }
    }

    /**
     * Find all views of a specific type in a ViewGroup
     */
    private inline fun <reified T : View> findViewsByType(viewGroup: ViewGroup): List<T> {
        val result = mutableListOf<T>()

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            if (child is T) {
                result.add(child)
            } else if (child is ViewGroup) {
                result.addAll(findViewsByType<T>(child))
            }
        }

        return result
    }
}