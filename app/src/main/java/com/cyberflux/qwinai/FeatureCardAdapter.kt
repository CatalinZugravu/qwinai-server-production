// 1. Updated FeatureCardAdapter.kt
package com.cyberflux.qwinai

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.model.AIFeature
import com.google.android.material.card.MaterialCardView

/**
 * Enhanced adapter for feature cards shown on the start screen
 * with improved visual styling and animations
 */
class FeatureCardAdapter(
    private val features: List<AIFeature>,
    private val onFeatureClick: (AIFeature) -> Unit
) : RecyclerView.Adapter<FeatureCardAdapter.FeatureViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feature_card, parent, false)
        return FeatureViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        holder.bind(features[position], onFeatureClick)

        // Add staggered animation for items
        holder.itemView.alpha = 0.8f
        holder.itemView.translationY = 30f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setStartDelay((position * 50).toLong())
            .start()
    }

    override fun getItemCount() = features.size

    class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val featureIconContainer: View = itemView.findViewById(R.id.iconContainer)
        private val featureIcon: ImageView = itemView.findViewById(R.id.imgFeatureIcon)
        private val featureTitle: TextView = itemView.findViewById(R.id.tvFeatureTitle)
        private val featureDescription: TextView = itemView.findViewById(R.id.tvFeatureDescription)

        fun bind(feature: AIFeature, onFeatureClick: (AIFeature) -> Unit) {
            // Set feature details
            featureTitle.text = feature.title
            featureDescription.text = feature.description

            // Set icon
            featureIcon.setImageResource(feature.iconResId)

            // Use layout-defined purple hint background, just set icon color
            val iconColor = ContextCompat.getColor(itemView.context, feature.colorResId)
            featureIcon.setColorFilter(iconColor)

            // Set press animation
            itemView.setOnClickListener {
                // Provide tactile feedback
                val scaleDown = ObjectAnimator.ofFloat(itemView, "scaleX", 0.98f)
                scaleDown.duration = 100
                scaleDown.start()

                val scaleDownY = ObjectAnimator.ofFloat(itemView, "scaleY", 0.98f)
                scaleDownY.duration = 100
                scaleDownY.start()

                // Reset after animation
                itemView.postDelayed({
                    val scaleUp = ObjectAnimator.ofFloat(itemView, "scaleX", 1.0f)
                    scaleUp.duration = 100
                    scaleUp.start()

                    val scaleUpY = ObjectAnimator.ofFloat(itemView, "scaleY", 1.0f)
                    scaleUpY.duration = 100
                    scaleUpY.start()

                    // Call the click handler
                    onFeatureClick(feature)
                }, 100)
            }
        }

    }
}