package com.cyberflux.qwinai

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.model.RecentModel
import com.cyberflux.qwinai.model.TrendingPrompt
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.DynamicColorManager
import com.cyberflux.qwinai.utils.ThemeManager
import com.google.android.material.card.MaterialCardView
import timber.log.Timber
import androidx.core.graphics.toColorInt
import android.util.TypedValue

/**
 * Enhanced adapter for displaying recently used AI models with
 * modern animations and visual styling
 */
class RecentModelsAdapter(
    private val models: List<RecentModel>,
    private val onModelClick: (RecentModel) -> Unit
) : RecyclerView.Adapter<RecentModelsAdapter.ModelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(models[position], onModelClick, position == 0)
    }

    override fun getItemCount() = models.size

    class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modelCard: MaterialCardView = itemView as MaterialCardView
        private val modelIcon: ImageView = itemView.findViewById(R.id.modelIcon)
        private val modelName: TextView = itemView.findViewById(R.id.modelName)

        fun bind(model: RecentModel, onModelClick: (RecentModel) -> Unit, isFirstCard: Boolean = false) {
            // Set model details
            modelName.text = model.displayName
            modelIcon.setImageResource(model.iconResId)
            
            // Use layout-defined purple hint background, just set icon color
            val modelColor = ModelIconUtils.getColorForModel(model.id, itemView.context)
            modelIcon.setColorFilter(modelColor)

            // Apply special styling to first card with dynamic accent color
            if (isFirstCard) {
                val accentColor = ThemeManager.getCurrentAccentColor(itemView.context)
                
                // Apply prominent accent border
                modelCard.strokeColor = accentColor
                modelCard.strokeWidth = 6 // 3dp stroke - prominent but not overwhelming
                
                // Subtle accent background tint
                val lightAccent = ColorUtils.setAlphaComponent(accentColor, 50) // ~20% opacity for more visibility
                modelCard.setCardBackgroundColor(lightAccent)
                
                // Enhanced elevation for premium feel
                modelCard.cardElevation = 4f
                
                // Custom ripple effect with accent color
                val rippleColor = ColorUtils.setAlphaComponent(accentColor, 60) // ~25% opacity
                modelCard.rippleColor = ColorStateList.valueOf(rippleColor)
                
                Timber.d("🎨 Applied accent color styling to first card: #${Integer.toHexString(accentColor)}")
                
            } else {
                // Style other cards to match model grid cards
                modelCard.strokeWidth = 0 // No stroke like model grid cards
                modelCard.cardElevation = 1f // 1dp elevation like model grid cards
                
                // Use model card background color resource (same as model grid)
                val modelCardBackground = ContextCompat.getColorStateList(itemView.context, R.color.model_card_background)
                modelCard.setCardBackgroundColor(modelCardBackground)
                
                // Default ripple effect with proper color
                val typedValue = TypedValue()
                val theme = itemView.context.theme
                
                // Get the default text color and make it semi-transparent for ripple
                theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                val defaultRippleColor = ColorUtils.setAlphaComponent(typedValue.data, 30) // ~12% opacity
                modelCard.rippleColor = ColorStateList.valueOf(defaultRippleColor)
            }

            // Setup click listener
            modelCard.setOnClickListener {
                // Apply card animation
                modelCard.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        modelCard.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()

                        // IMPORTANT: Log the model being clicked for debugging
                        Timber.d("Recent model clicked: ${model.displayName} (${model.id})")

                        // Trigger the callback with the model
                        onModelClick(model)
                    }
                    .start()
            }
        }
    }
}
/**
 * Enhanced adapter for displaying trending prompts with
 * modern animations and visual styling
 */
class TrendingPromptsAdapter(
    private val prompts: List<TrendingPrompt>,
    private val onPromptClick: (TrendingPrompt) -> Unit
) : RecyclerView.Adapter<TrendingPromptsAdapter.PromptViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trending_prompt, parent, false)
        return PromptViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        holder.bind(prompts[position], onPromptClick)
    }

    override fun getItemCount() = prompts.size

    class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val promptCard: MaterialCardView = itemView as MaterialCardView
        private val promptIconBackground: FrameLayout = itemView.findViewById(R.id.promptIconBackground)
        private val promptIcon: ImageView = itemView.findViewById(R.id.promptIcon)
        private val promptTitle: TextView = itemView.findViewById(R.id.promptTitle)
        private val promptDescription: TextView = itemView.findViewById(R.id.promptDescription)

        fun bind(prompt: TrendingPrompt, onPromptClick: (TrendingPrompt) -> Unit) {
            // Set prompt details
            promptTitle.text = prompt.title
            promptDescription.text = prompt.description
            promptIcon.setImageResource(prompt.iconResId)

            // Set background color
            try {
                val color = Color.parseColor(prompt.colorHex)
                promptIconBackground.backgroundTintList = ColorStateList.valueOf(color)
            } catch (e: Exception) {
                // Fallback to a default color if parsing fails
                promptIconBackground.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.colorPrimary)
                )
            }

            // Setup click listener
            promptCard.setOnClickListener {
                promptCard.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(100)
                    .withEndAction {
                        promptCard.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                        onPromptClick(prompt)
                    }
                    .start()
            }
        }
    }
}