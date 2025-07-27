package com.cyberflux.qwinai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R

/**
 * Enhanced adapter for response options (especially tones) with grid layout support
 */
class ResponseOptionAdapter<T>(
    private val items: List<T>,
    private var selectedPosition: Int = 0,
    private val getName: (T) -> String,
    private val getDescription: (T) -> String,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<ResponseOptionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view as CardView
        val name: TextView = view.findViewById(R.id.tvOptionName)
        val description: TextView = view.findViewById(R.id.tvOptionDescription)
        val checkmark: ImageView = view.findViewById(R.id.ivCheckmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_response_option_compact, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // Set the name and description
        holder.name.text = getName(item)
        holder.description.text = getDescription(item)

        // Handle selection state
        if (position == selectedPosition) {
            holder.checkmark.visibility = View.VISIBLE
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_background))
        } else {
            holder.checkmark.visibility = View.GONE
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            if (position != selectedPosition) {
                val oldPosition = selectedPosition
                selectedPosition = position

                // Update UI
                notifyItemChanged(oldPosition)
                notifyItemChanged(position)

                // Notify listener
                onItemSelected(position)
            }
        }
    }

    fun updateSelectedPosition(position: Int) {
        if (position != selectedPosition && position in 0 until itemCount) {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(position)
        }
    }

    fun getSelectedItem(): T = items[selectedPosition]
}