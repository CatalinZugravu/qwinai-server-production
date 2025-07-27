package com.cyberflux.qwinai.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.PromptChip
import com.google.android.material.chip.Chip
import timber.log.Timber
import kotlin.random.Random

/**
 * Adapter for quick prompt chips shown on the start screen
 */
class PromptChipAdapter(
    private val prompts: List<PromptChip>,
    private val onPromptClick: (PromptChip) -> Unit
) : RecyclerView.Adapter<PromptChipAdapter.PromptChipViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptChipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_chip, parent, false)
        return PromptChipViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptChipViewHolder, position: Int) {
        holder.bind(prompts[position], onPromptClick)
    }

    override fun getItemCount() = prompts.size

    class PromptChipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chip: Chip = itemView as Chip

        fun bind(promptChip: PromptChip, onPromptClick: (PromptChip) -> Unit) {
            // Set chip text using the text property of PromptChip
            chip.text = promptChip.text

            // Use neutral card background color - no custom colors
            chip.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, R.color.card_background)
            )
            
            // Set text color with subtle accent based on position
            val textColor = when (promptChip.category) {
                "creative" -> ContextCompat.getColor(itemView.context, R.color.accent_primary)
                "productivity" -> ContextCompat.getColor(itemView.context, R.color.accent_green)
                "education" -> ContextCompat.getColor(itemView.context, R.color.accent_blue)
                "tech" -> ContextCompat.getColor(itemView.context, R.color.accent_orange)
                "everyday" -> ContextCompat.getColor(itemView.context, R.color.accent_gold)
                else -> ContextCompat.getColor(itemView.context, R.color.text_primary)
            }
            chip.setTextColor(textColor)

            // Set click listener
            chip.setOnClickListener {
                onPromptClick(promptChip)
            }
        }
    }
}