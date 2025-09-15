package com.cyberflux.qwinai.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.TranslationUtils
import timber.log.Timber

/**
 * Enhanced ModelGridAdapter with better visuals
 * Designed to work with the CustomSpinnerDialog
 */
/**
 * Clean ModelGridAdapter - NO LayoutParams manipulation
 */
class ModelGridAdapter(
    private val context: Context,
    private val models: List<AIModel>,
    private var selectedPosition: Int,
    private val dialogWindow: Window? = null, // Made nullable to avoid issues
    private val viewPagerRef: ViewPager2? = null, // Made nullable to avoid issues
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ModelGridAdapter.ViewHolder>() {

    init {
        Timber.d("SPINNER: ModelGridAdapter created with selectedPosition=$selectedPosition")
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.modelCard)
        val textView: TextView = view.findViewById(R.id.tvModelName)
        val providerTextView: TextView = view.findViewById(R.id.tvProviderName)
        val providerIcon: ImageView = view.findViewById(R.id.providerIcon)
        val circleIcon: ImageView = view.findViewById(R.id.circleIcon)
        val proChip: com.google.android.material.chip.Chip = view.findViewById(R.id.chipPro)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_grid, parent, false)

        // DON'T touch LayoutParams - let RecyclerView handle everything

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = models.size

    fun updateSelectedPosition(position: Int) {
        if (position != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = position

            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)

            Timber.d("SPINNER: ModelGridAdapter updated selectedPosition from $oldPosition to $position")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]

        // Set model name with ultrathink font
        holder.textView.text = model.displayName
        
        // Apply Ultrathink font to model name
        try {
            holder.textView.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ultrathink)
            holder.providerTextView.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ultrathink)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load ultrathink font, using default")
            holder.textView.typeface = null
            holder.providerTextView.typeface = null
        }

        // Get model config to access provider and free status
        val modelConfig = ModelConfigManager.getConfig(model.id)
        
        // Set provider name and icon
        val providerName = when (modelConfig?.provider) {
            "openai" -> "OpenAI"
            "anthropic" -> "Anthropic"  
            "meta" -> "Meta"
            "google" -> "Google"
            "cohere" -> "Cohere"
            "deepseek" -> "DeepSeek"
            "qwen" -> "Alibaba"
            "x-ai" -> "xAI"
            "mistral" -> "Mistral"
            "perplexity" -> "Perplexity"
            "zhipu" -> "ZhiPu"
            else -> "AI Chat"
        }
        holder.providerTextView.text = providerName
        
        // Set provider icon based on provider
        val providerIconResource = when (modelConfig?.provider) {
            "openai" -> R.drawable.ic_gpt
            "anthropic" -> R.drawable.ic_claude
            "meta" -> R.drawable.ic_llama
            "google" -> R.drawable.ic_gemma
            "cohere" -> R.drawable.ic_ai_chip
            "deepseek" -> R.drawable.ic_ai_brain
            "qwen" -> R.drawable.ic_qwen
            "x-ai" -> R.drawable.ic_default_model
            "mistral" -> R.drawable.ic_mistral
            "perplexity" -> R.drawable.ic_default_model
            "zhipu" -> R.drawable.ic_default_model
            else -> R.drawable.ic_default_model
        }
        holder.providerIcon.setImageResource(providerIconResource)

        // Show/hide pro badge based on whether model is free
        val isFree = modelConfig?.isFree ?: true
        holder.proChip.visibility = if (isFree) View.GONE else View.VISIBLE

        // Set model icon 
        val iconResource = ModelIconUtils.getIconResourceForChatModel(model.id)
        holder.circleIcon.setImageResource(iconResource)
        
        // Update selection state with dynamic accent color
        val isSelected = (position == selectedPosition)
        com.cyberflux.qwinai.utils.DynamicColorManager.updateCardSelectionState(context, holder.card, isSelected)
        
        // Always show original icon colors for main model icon
        holder.circleIcon.clearColorFilter()
        
        // Apply text colors and provider icon styling based on selection state
        if (isSelected) {
            // Check if we're in dark mode to determine text color
            val isDarkMode = (context.resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
                
            if (isDarkMode) {
                // In dark mode, use white text and white provider icon for selected items
                val whiteColor = ContextCompat.getColor(context, android.R.color.white)
                holder.textView.setTextColor(whiteColor)
                holder.providerTextView.setTextColor(whiteColor)
                holder.providerIcon.setColorFilter(whiteColor, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                // In light mode, use standard text colors and match provider icon to text
                val typedArray = context.obtainStyledAttributes(intArrayOf(
                    android.R.attr.textColorPrimary,
                    android.R.attr.textColorSecondary
                ))
                val primaryTextColor = typedArray.getColor(0, Color.BLACK)
                val secondaryTextColor = typedArray.getColor(1, Color.GRAY)
                typedArray.recycle()
                
                holder.textView.setTextColor(primaryTextColor)
                holder.providerTextView.setTextColor(secondaryTextColor)
                // Make provider icon match the provider text color
                holder.providerIcon.setColorFilter(secondaryTextColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        } else {
            // When not selected, show theme colors and match provider icon to text
            val typedArray = context.obtainStyledAttributes(intArrayOf(
                android.R.attr.textColorPrimary,
                android.R.attr.textColorSecondary
            ))
            val primaryTextColor = typedArray.getColor(0, Color.BLACK)
            val secondaryTextColor = typedArray.getColor(1, Color.GRAY)
            typedArray.recycle()
            
            holder.textView.setTextColor(primaryTextColor)
            holder.providerTextView.setTextColor(secondaryTextColor)
            // Make provider icon match the provider text color
            holder.providerIcon.setColorFilter(secondaryTextColor, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        
        // Check translation mode
        val isTranslationMode = (context as? TranslationModeFetcher)?.isTranslationMode() == true
        val isDisabledInTranslationMode = isTranslationMode && !TranslationUtils.supportsTranslation(model.id)

        if (isDisabledInTranslationMode) {
            // Disabled state
            holder.view.alpha = 0.5f
            holder.view.isClickable = false
            holder.view.isFocusable = false
            holder.view.setOnClickListener(null)
        } else {
            // Normal state
            holder.view.alpha = 1.0f
            holder.view.isClickable = true
            holder.view.isFocusable = true

            // Click listener with selection handling
            holder.view.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = position

                // Update UI
                notifyItemChanged(oldPosition)
                notifyItemChanged(position)

                // Simple animation
                holder.card.animate()
                    .scaleX(1.03f)
                    .scaleY(1.03f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        holder.card.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start()
                    }
                    .start()

                onItemClick(position)
            }
        }

        // Selection state is already handled above with stroke and background colors
    }
    
    // Removed old provider and PRO badge methods since they're not needed in the simplified design

    // Interface for translation mode
    interface TranslationModeFetcher {
        fun isTranslationMode(): Boolean
    }
}