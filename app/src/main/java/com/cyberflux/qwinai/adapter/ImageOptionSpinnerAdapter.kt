package com.cyberflux.qwinai.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.ImageGenerationOption

/**
 * Adapter for image generation option spinners
 * Enhanced to support model styles, sizes, and more
 */
class ImageOptionSpinnerAdapter(
    private val context: Context,
    private val options: List<ImageGenerationOption>,
    private val showProBadges: Boolean = false
) : BaseAdapter() {

    private var selectedPosition = 0

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getCount(): Int = options.size

    override fun getItem(position: Int): ImageGenerationOption = options[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_option_spinner, parent, false
        )

        val option = getItem(position)

        // Bind option data to views
        val textView = view.findViewById<TextView>(R.id.tvOptionName)
        val imageView = view.findViewById<ImageView>(R.id.ivOptionIcon)
        val proBadge = view.findViewById<TextView>(R.id.tvProBadge)

        textView.text = option.displayName

        // Show icon if resource is provided
        if (option.iconResourceId != 0) {
            imageView.visibility = View.VISIBLE
            option.iconResourceId?.let { imageView.setImageResource(it) }
        } else {
            imageView.visibility = View.GONE
        }

        // Show Pro badge if needed and enabled
        if (showProBadges && option.isPremium) {
            proBadge.visibility = View.VISIBLE
        } else {
            proBadge.visibility = View.GONE
        }

        // Highlight selected item
        if (position == selectedPosition) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            view.setBackgroundResource(R.drawable.selected_spinner_item_background)
        } else {
            textView.setTextColor(ContextCompat.getColor(context, R.color.black))
            view.setBackgroundResource(android.R.color.transparent)
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = LayoutInflater.from(context).inflate(
            R.layout.item_option_spinner_dropdown, parent, false
        )

        val option = getItem(position)

        // Bind option data to views
        val textView = view.findViewById<TextView>(R.id.tvDropdownOptionName)
        val imageView = view.findViewById<TextView>(R.id.ivDropdownOptionIcon)
        val descriptionView = view.findViewById<TextView>(R.id.tvDropdownOptionDescription)
        val proBadge = view.findViewById<TextView>(R.id.tvDropdownProBadge)

        textView.text = option.displayName

        // Show icon if resource is provided
        if (option.iconResourceId != 0) {
            imageView.visibility = View.VISIBLE
            option.iconResourceId?.let { imageView.setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0) }
        } else {
            imageView.visibility = View.GONE
        }

        // Show description if available
        if (option.description.isNotEmpty()) {
            descriptionView.visibility = View.VISIBLE
            descriptionView.text = option.description
        } else {
            descriptionView.visibility = View.GONE
        }

        // Show Pro badge if needed and enabled
        if (showProBadges && option.isPremium) {
            proBadge.visibility = View.VISIBLE
        } else {
            proBadge.visibility = View.GONE
        }

        // Highlight selected item
        if (position == selectedPosition) {
            view.setBackgroundResource(R.drawable.selected_spinner_dropdown_background)
        } else {
            view.setBackgroundResource(android.R.color.transparent)
        }

        return view
    }
}