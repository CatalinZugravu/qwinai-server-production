package com.cyberflux.qwinai.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.TranslationUtils

/**
 * Enhanced spinner adapter that shows model icons and names with beautiful styling.
 * Features a checkmark indicator for selected models ONLY in the dropdown,
 * gradient badges for Pro models, and translation mode support.
 */
/**
 * Enhanced spinner adapter that shows model icons and names with beautiful styling.
 * Features a checkmark indicator for selected models ONLY in the dropdown,
 * gradient badges for Pro models, and translation mode support.
 *
 * FIXED: Replaced text_primary_dark with #212121
 */
class ModelSpinnerAdapter(
    private val context: Context,
    private val models: List<AIModel>,
    private val isTranslationMode: () -> Boolean = { false }
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var selectedPosition: Int = 0

    override fun getCount(): Int = models.size

    override fun getItem(position: Int): AIModel = models[position]

    override fun getItemId(position: Int): Long = position.toLong()

    // Override isEnabled to BLOCK selection of incompatible models
    override fun isEnabled(position: Int): Boolean {
        return if (isTranslationMode()) {
            TranslationUtils.supportsTranslation(models[position].id)
        } else {
            true
        }
    }

    fun setSelectedPosition(position: Int) {
        this.selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // This is the view shown in the spinner when CLOSED (the selected item)
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            // Use custom transparent layout for the closed spinner
            view = inflater.inflate(R.layout.spinner_item_custom, parent, false)
            holder = ViewHolder()
            holder.textView = view.findViewById(R.id.text1)
            // For the custom layout, we don't need all these components
            holder.iconView = null
            holder.proBadge = null
            holder.checkmark = null
            holder.background = null
            holder.warningIndicator = null
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        // Use the selectedPosition for the main view
        val model = getItem(selectedPosition)

        // Set model name with clean ultrathink styling
        holder.textView.text = model.displayName
        holder.textView.maxLines = 1

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // This is the view for each item in the dropdown when OPEN
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            // Use custom dropdown item layout
            view = inflater.inflate(R.layout.spinner_dropdown_item_custom, parent, false)
            holder = ViewHolder()
            holder.textView = view.findViewById(R.id.text1)
            // Simplified dropdown - just text for clean appearance
            holder.iconView = null
            holder.proBadge = null
            holder.checkmark = null
            holder.divider = null
            holder.rootLayout = null
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val model = getItem(position)

        // Set model name with clean ultrathink styling
        holder.textView.text = model.displayName
        holder.textView.maxLines = 1

        // Handle translation mode compatibility
        if (isTranslationMode() && !TranslationUtils.supportsTranslation(model.id)) {
            holder.textView.text = "${model.displayName} (Not for translation)"
            view.alpha = 0.5f
            view.isEnabled = false
        } else {
            view.alpha = 1.0f
            view.isEnabled = true

            // Simple selection highlighting
            if (position == selectedPosition) {
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_background))
            } else {
                view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            }
        }

        return view
    }


    private class ViewHolder {
        lateinit var textView: TextView
        var iconView: ImageView? = null
        var proBadge: TextView? = null
        var checkmark: ImageView? = null
        var divider: View? = null
        var background: View? = null
        var rootLayout: ViewGroup? = null
        var warningIndicator: ImageView? = null
    }
}