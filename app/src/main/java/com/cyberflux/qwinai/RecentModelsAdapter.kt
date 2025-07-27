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
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.model.RecentModel
import com.cyberflux.qwinai.model.TrendingPrompt
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.google.android.material.card.MaterialCardView
import timber.log.Timber
import androidx.core.graphics.toColorInt

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
        holder.bind(models[position], onModelClick)
    }

    override fun getItemCount() = models.size

    class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modelCard: MaterialCardView = itemView as MaterialCardView
        private val modelIcon: ImageView = itemView.findViewById(R.id.modelIcon)
        private val modelName: TextView = itemView.findViewById(R.id.modelName)

        fun bind(model: RecentModel, onModelClick: (RecentModel) -> Unit) {
            // Set model details
            modelName.text = model.displayName
            modelIcon.setImageResource(model.iconResId)
            
            // Use layout-defined purple hint background, just set icon color
            val modelColor = ModelIconUtils.getColorForModel(model.id)
            modelIcon.setColorFilter(modelColor)

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