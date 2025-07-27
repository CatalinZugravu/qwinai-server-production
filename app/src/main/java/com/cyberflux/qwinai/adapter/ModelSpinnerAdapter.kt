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
            // Use a more elegant layout for the closed spinner
            view = inflater.inflate(R.layout.spinner_selected_item, parent, false)
            holder = ViewHolder()
            holder.textView = view.findViewById(R.id.tvModelName)
            holder.iconView = view.findViewById(R.id.ivModelIcon)
            holder.proBadge = view.findViewById(R.id.tvProBadge)
            holder.checkmark = view.findViewById(R.id.ivCheckmark)
            holder.background = view.findViewById(R.id.selectedItemBackground)
            holder.warningIndicator = view.findViewById(R.id.ivWarningIndicator)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        // Use the selectedPosition for the main view
        val model = getItem(selectedPosition)

        // Set model name with proper styling
        holder.textView.text = model.displayName
        holder.textView.setTypeface(Typeface.DEFAULT_BOLD)
        holder.textView.maxLines = 1  // Always single line for the closed spinner

        // Set the icon with proper size and padding
        val iconResource = ModelIconUtils.getIconResourceForModel(model.id)
        holder.iconView.setImageResource(iconResource)
        holder.iconView.visibility = View.VISIBLE

        // Add subtle scaling animation on first display
        if (holder.iconView.scaleX == 1.0f) {
            holder.iconView.scaleX = 0.8f
            holder.iconView.scaleY = 0.8f
            holder.iconView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }

        // Show PRO badge for non-free models with enhanced gradient
        if (!model.isFree) {
            holder.proBadge.visibility = View.VISIBLE
            holder.proBadge.background = ContextCompat.getDrawable(context, R.drawable.premium_badge_gradient)
        } else {
            holder.proBadge.visibility = View.GONE
        }

        // Never show checkmark in the closed spinner
        holder.checkmark.visibility = View.GONE

        // Keep text with proper contrast - FIXED: Using direct color value instead of reference
        holder.textView.setTypeface(holder.textView.typeface, Typeface.BOLD)
        holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

        // Handle translation mode styling
        if (isTranslationMode()) {
            val isSupported = TranslationUtils.supportsTranslation(model.id)
            if (!isSupported) {
                // Dim the spinner if selected model doesn't support translation
                view.alpha = 0.5f
                holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_disabled))
                holder.iconView.alpha = 0.5f

                // Add warning indicator
                holder.warningIndicator?.visibility = View.VISIBLE
            } else {
                view.alpha = 1.0f
                holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                holder.iconView.alpha = 1.0f

                // Hide warning indicator
                holder.warningIndicator?.visibility = View.GONE
            }
        } else {
            // Reset styling for normal mode
            view.alpha = 1.0f
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            holder.iconView.alpha = 1.0f

            // Hide warning indicator
            holder.warningIndicator?.visibility = View.GONE
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // This is the view for each item in the dropdown when OPEN
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            // Use enhanced dropdown item layout with proper spacing and dividers
            view = inflater.inflate(R.layout.spinner_dropdown_item, parent, false)
            holder = ViewHolder()
            holder.textView = view.findViewById(R.id.tvModelName)
            holder.iconView = view.findViewById(R.id.ivModelIcon)
            holder.proBadge = view.findViewById(R.id.tvProBadge)
            holder.checkmark = view.findViewById(R.id.ivCheckmark)
            holder.divider = view.findViewById(R.id.itemDivider)
            holder.rootLayout = view.findViewById(R.id.dropdownItemRoot)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val model = getItem(position)

        // Check translation compatibility
        val translationActive = isTranslationMode()
        val isCompatibleWithTranslation = TranslationUtils.supportsTranslation(model.id)

        // Set model name with enhanced typography
        holder.textView.text = model.displayName
        holder.textView.maxLines = 2 // Allow two lines for dropdown items

        // Set icon with better sizing and padding
        val iconResource = ModelIconUtils.getIconResourceForModel(model.id)
        holder.iconView.setImageResource(iconResource)
        holder.iconView.visibility = View.VISIBLE
        holder.iconView.setPadding(8, 8, 8, 8)

        // Show divider for all items except the last one
        holder.divider?.visibility = if (position < models.size - 1) View.VISIBLE else View.GONE

        // Handle translation mode styling with enhanced visuals
        if (translationActive && !isCompatibleWithTranslation) {
            // DISABLE non-compatible models with clear visual indication
            view.alpha = 0.5f
            holder.textView.alpha = 0.5f
            holder.iconView.alpha = 0.3f

            // Set disabled appearance with gray background
            holder.rootLayout?.setBackgroundColor(ContextCompat.getColor(context, R.color.disabled_card_background))
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_disabled))

            // Add incompatibility indicator with icon
            val warningIcon = AppCompatResources.getDrawable(context, R.drawable.ic_warning)
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(null, null, warningIcon, null)
            holder.textView.compoundDrawablePadding = 8

            // Add explanation text
            holder.textView.text = "${model.displayName}\n(Not for translation)"

            // Hide selection indicators
            holder.proBadge.visibility = View.GONE
            holder.checkmark.visibility = View.GONE

            // Make non-clickable
            view.isEnabled = false
            view.isClickable = false
        } else {
            // Normal styling for compatible models with enhanced visuals
            view.alpha = 1.0f
            holder.textView.alpha = 1.0f
            holder.iconView.alpha = 1.0f
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

            // Remove any warning icons
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

            // Make clickable
            view.isEnabled = true
            view.isClickable = true

            // Handle selection state with enhanced visuals
            if (position == selectedPosition) {
                // Selected item styling
                holder.checkmark.visibility = View.VISIBLE
                holder.rootLayout?.setBackgroundResource(R.drawable.selected_item_background)
                holder.textView.setTypeface(holder.textView.typeface, Typeface.BOLD)

                // Show PRO badge if needed with enhanced styling
                if (!model.isFree) {
                    holder.proBadge.visibility = View.VISIBLE
                    holder.proBadge.background = ContextCompat.getDrawable(context, R.drawable.premium_badge_gradient)
                } else {
                    holder.proBadge.visibility = View.GONE
                }
            } else {
                // Unselected item styling
                holder.checkmark.visibility = View.GONE
                holder.textView.setTypeface(holder.textView.typeface, Typeface.NORMAL)

                // Show PRO badge if needed
                if (!model.isFree) {
                    holder.proBadge.visibility = View.VISIBLE
                    holder.proBadge.background = ContextCompat.getDrawable(context, R.drawable.premium_badge_gradient)
                } else {
                    holder.proBadge.visibility = View.GONE
                }

                // Set normal background with hover effect
                holder.rootLayout?.setBackgroundResource(R.drawable.dropdown_item_hover_background)
            }
        }

        return view
    }

    private class ViewHolder {
        lateinit var textView: TextView
        lateinit var iconView: ImageView
        lateinit var proBadge: TextView
        lateinit var checkmark: ImageView
        var divider: View? = null
        var background: View? = null
        var rootLayout: ViewGroup? = null
        var warningIndicator: ImageView? = null
    }
}