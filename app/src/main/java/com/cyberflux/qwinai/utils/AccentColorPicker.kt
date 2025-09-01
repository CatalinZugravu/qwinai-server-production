package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Accent Color Picker Dialog for Material Design 3 theming
 */
class AccentColorPicker(private val context: Context) {

    interface OnColorSelectedListener {
        fun onColorSelected(accentColor: Int)
    }

    fun show(currentAccentColor: Int, listener: OnColorSelectedListener) {
        val colorOptions = ThemeManager.getAvailableAccentColors()
        
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = ColorAdapter(colorOptions, currentAccentColor) { selectedColor ->
                listener.onColorSelected(selectedColor)
            }
            setPadding(32, 24, 32, 24)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Choose accent color")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class ColorAdapter(
        private val colors: List<Pair<Int, String>>,
        private val currentSelection: Int,
        private val onColorSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color_picker, parent, false)
            return ColorViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val (colorId, colorName) = colors[position]
            holder.bind(colorId, colorName, colorId == currentSelection, onColorSelected)
        }

        override fun getItemCount(): Int = colors.size

        class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val colorView: ImageView = itemView.findViewById(R.id.color_preview)
            private val selectedIndicator: ImageView = itemView.findViewById(R.id.selected_indicator)

            fun bind(
                colorId: Int,
                colorName: String,
                isSelected: Boolean,
                onColorSelected: (Int) -> Unit
            ) {
                // Set the color preview
                val colorRes = getColorResource(colorId)
                val color = ContextCompat.getColor(itemView.context, colorRes)
                
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(4, ContextCompat.getColor(itemView.context, R.color.md_theme_light_outline))
                }
                colorView.setImageDrawable(drawable)
                colorView.contentDescription = colorName

                // Show/hide selection indicator
                selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE

                // Set click listener
                itemView.setOnClickListener {
                    onColorSelected(colorId)
                }
            }

            private fun getColorResource(accentColorId: Int): Int {
                return when (accentColorId) {
                    ThemeManager.ACCENT_BLUE -> R.color.accent_blue
                    ThemeManager.ACCENT_PURPLE -> R.color.accent_purple
                    ThemeManager.ACCENT_PINK -> R.color.accent_pink
                    ThemeManager.ACCENT_GREEN -> R.color.accent_green
                    ThemeManager.ACCENT_ORANGE -> R.color.accent_orange
                    ThemeManager.ACCENT_RED -> R.color.accent_red
                    ThemeManager.ACCENT_TEAL -> R.color.accent_teal
                    ThemeManager.ACCENT_CYAN -> R.color.accent_cyan
                    else -> R.color.md_theme_primary_seed // Default indigo
                }
            }
        }
    }
}

/**
 * Theme Mode Picker Dialog
 */
class ThemeModePicker(private val context: Context) {

    interface OnThemeModeSelectedListener {
        fun onThemeModeSelected(themeMode: Int)
    }

    fun show(currentThemeMode: Int, listener: OnThemeModeSelectedListener) {
        val themeModes = ThemeManager.getAvailableThemeModes()
        val options = themeModes.map { it.second }.toTypedArray()
        val selectedIndex = themeModes.indexOfFirst { it.first == currentThemeMode }

        MaterialAlertDialogBuilder(context)
            .setTitle("Choose theme")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val selectedThemeMode = themeModes[which].first
                listener.onThemeModeSelected(selectedThemeMode)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}