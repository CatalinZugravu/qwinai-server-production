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
    private var dialog: androidx.appcompat.app.AlertDialog? = null

    interface OnColorSelectedListener {
        fun onColorSelected(accentColor: Int)
    }

    fun show(currentAccentColor: Int, listener: OnColorSelectedListener) {
        val colorOptions = ThemeManager.getAvailableAccentColors()
        
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 5) // 5 columns for 40 colors
            adapter = ColorAdapter(colorOptions, currentAccentColor) { selectedColor ->
                listener.onColorSelected(selectedColor)
                dismiss() // Close dialog after selection
            }
            setPadding(32, 24, 32, 24)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Choose accent color")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .create()
        dialog?.show()
    }
    
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
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
                    // Extended accent colors
                    ThemeManager.ACCENT_LIME -> R.color.accent_lime
                    ThemeManager.ACCENT_AMBER -> R.color.accent_amber
                    ThemeManager.ACCENT_ROSE -> R.color.accent_rose
                    ThemeManager.ACCENT_VIOLET -> R.color.accent_violet
                    ThemeManager.ACCENT_EMERALD -> R.color.accent_emerald
                    ThemeManager.ACCENT_SKY -> R.color.accent_sky
                    ThemeManager.ACCENT_FUCHSIA -> R.color.accent_fuchsia
                    ThemeManager.ACCENT_SLATE -> R.color.accent_slate
                    ThemeManager.ACCENT_CORAL -> R.color.accent_coral
                    ThemeManager.ACCENT_MINT -> R.color.accent_mint
                    ThemeManager.ACCENT_LAVENDER -> R.color.accent_lavender
                    ThemeManager.ACCENT_PEACH -> R.color.accent_peach
                    ThemeManager.ACCENT_FOREST -> R.color.accent_forest
                    ThemeManager.ACCENT_CRIMSON -> R.color.accent_crimson
                    ThemeManager.ACCENT_OCEAN -> R.color.accent_ocean
                    // Premium accent colors
                    ThemeManager.ACCENT_TURQUOISE -> R.color.accent_turquoise
                    ThemeManager.ACCENT_MAGENTA -> R.color.accent_magenta
                    ThemeManager.ACCENT_GOLD -> R.color.accent_gold
                    ThemeManager.ACCENT_SAPPHIRE -> R.color.accent_sapphire
                    ThemeManager.ACCENT_RUBY -> R.color.accent_ruby
                    ThemeManager.ACCENT_JADE -> R.color.accent_jade
                    ThemeManager.ACCENT_BRONZE -> R.color.accent_bronze
                    ThemeManager.ACCENT_SILVER -> R.color.accent_silver
                    ThemeManager.ACCENT_COPPER -> R.color.accent_copper
                    ThemeManager.ACCENT_PEARL -> R.color.accent_pearl
                    ThemeManager.ACCENT_ONYX -> R.color.accent_onyx
                    ThemeManager.ACCENT_IVORY -> R.color.accent_ivory
                    ThemeManager.ACCENT_CHAMPAGNE -> R.color.accent_champagne
                    ThemeManager.ACCENT_PLUM -> R.color.accent_plum
                    ThemeManager.ACCENT_ORCHID -> R.color.accent_orchid
                    ThemeManager.ACCENT_STORM -> R.color.accent_storm
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