package com.cyberflux.qwinai.utils

import android.content.Context
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.utils.DynamicColorManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Theme Settings Bottom Sheet Dialog for Material Design 3
 */
class ThemeSettingsDialog(private val context: Context) {
    private var bottomSheetDialog: BottomSheetDialog? = null

    fun show() {
        // Dismiss any existing dialog first
        dismiss()
        
        bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_theme_settings, null)
        
        setupThemeModeButtons(view)
        setupAccentColorButton(view)
        
        bottomSheetDialog?.setContentView(view)
        bottomSheetDialog?.show()
    }
    
    fun dismiss() {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    private fun setupThemeModeButtons(view: View) {
        val currentMode = ThemeManager.getSavedThemeMode(context)
        
        val lightButton = view.findViewById<MaterialButton>(R.id.btn_theme_light)
        val darkButton = view.findViewById<MaterialButton>(R.id.btn_theme_dark)
        val systemButton = view.findViewById<MaterialButton>(R.id.btn_theme_system)
        
        // Update button states
        updateThemeModeButton(lightButton, currentMode == ThemeManager.MODE_LIGHT)
        updateThemeModeButton(darkButton, currentMode == ThemeManager.MODE_DARK)
        updateThemeModeButton(systemButton, currentMode == ThemeManager.MODE_SYSTEM)
        
        // Set click listeners
        lightButton.setOnClickListener {
            dismiss() // Close dialog before theme change
            ThemeManager.setTheme(context, ThemeManager.MODE_LIGHT)
            updateAllThemeButtons(lightButton, darkButton, systemButton, 0)
        }
        
        darkButton.setOnClickListener {
            dismiss() // Close dialog before theme change
            ThemeManager.setTheme(context, ThemeManager.MODE_DARK)
            updateAllThemeButtons(lightButton, darkButton, systemButton, 1)
        }
        
        systemButton.setOnClickListener {
            dismiss() // Close dialog before theme change
            ThemeManager.setTheme(context, ThemeManager.MODE_SYSTEM)
            updateAllThemeButtons(lightButton, darkButton, systemButton, 2)
        }
    }
    
    private fun setupAccentColorButton(view: View) {
        val accentColorButton = view.findViewById<MaterialCardView>(R.id.accent_color_button)
        val accentColorPreview = view.findViewById<View>(R.id.accent_color_preview)
        val accentColorText = view.findViewById<TextView>(R.id.accent_color_text)
        
        // Update current accent color display
        val currentAccentColor = ThemeManager.getSavedAccentColor(context)
        val colorRes = ThemeManager.getAccentColorResource(context)
        val color = ContextCompat.getColor(context, colorRes)
        
        accentColorPreview.setBackgroundColor(color)
        accentColorText.text = ThemeManager.getAccentColorDisplayName(context)
        
        // Set click listener
        accentColorButton.setOnClickListener {
            val colorPicker = AccentColorPicker(context)
            colorPicker.show(currentAccentColor, object : AccentColorPicker.OnColorSelectedListener {
                override fun onColorSelected(selectedColor: Int) {
                    // Close both dialogs before theme change
                    dismiss()
                    
                    // Delay the theme change slightly to allow dialogs to close
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        ThemeManager.setAccentColor(context, selectedColor)
                    }, 100)
                    
                    // Update preview
                    val newColorRes = ThemeManager.getAccentColorResource(context)
                    val newColor = ContextCompat.getColor(context, newColorRes)
                    accentColorPreview.setBackgroundColor(newColor)
                    accentColorText.text = ThemeManager.getAccentColorDisplayName(context)
                }
            })
        }
    }
    
    private fun updateThemeModeButton(button: MaterialButton, isSelected: Boolean) {
        if (isSelected) {
            button.icon = ContextCompat.getDrawable(context, R.drawable.ic_checkmark)
            button.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_primaryContainer))
            button.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onPrimaryContainer))
        } else {
            button.icon = null
            button.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_surface))
            button.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
        }
    }
    
    private fun updateAllThemeButtons(
        lightButton: MaterialButton,
        darkButton: MaterialButton,
        systemButton: MaterialButton,
        selectedIndex: Int
    ) {
        updateThemeModeButton(lightButton, selectedIndex == 0)
        updateThemeModeButton(darkButton, selectedIndex == 1)
        updateThemeModeButton(systemButton, selectedIndex == 2)
    }
}