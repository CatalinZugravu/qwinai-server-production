package com.cyberflux.qwinai.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R

class PersistentLoadingIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ProgressBar(context, attrs, defStyleAttr) {

    private var colorAnimator: ValueAnimator? = null
    private var currentColorIndex = 0
    private var isAnimating = false

    // Beautiful colors for cycling animation - vibrant and distinct
    private val animationColors = listOf(
        0xFF6366F1.toInt(), // Indigo - Modern and professional
        0xFF8B5CF6.toInt(), // Purple - Creative and elegant  
        0xFF06B6D4.toInt(), // Cyan - Fresh and modern
        0xFF10B981.toInt(), // Emerald - Natural and calming
        0xFFF59E0B.toInt(), // Amber - Warm and energetic
        0xFFEF4444.toInt(), // Red - Bold and attention-grabbing
        0xFFEC4899.toInt()  // Pink - Vibrant and friendly
    )

    init {
        // Set initial style and make it always visible but transparent when not needed
        isIndeterminate = true
        alpha = 0f // Start invisible
        visibility = View.VISIBLE // Always keep in layout to prevent flickering
    }

    /**
     * Shows the loading indicator with smooth fade-in and starts color animation
     */
    fun show(targetColor: Int? = null) {
        // Stop any existing animations
        stopColorAnimation()
        
        // Set initial color - prefer beautiful animation colors over target color for better effect
        val initialColor = if (targetColor != null && !isAnimating) {
            // Use target color initially, but animation will take over
            targetColor
        } else {
            animationColors[currentColorIndex]
        }
        indeterminateTintList = ColorStateList.valueOf(initialColor)
        
        // Start animation immediately for better visual feedback
        startColorAnimation()
        
        // Ensure visibility
        if (alpha < 1f) {
            // Fade in smoothly while animation is already running
            animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    /**
     * Hides the loading indicator with smooth fade-out
     */
    fun hide() {
        if (alpha == 0f) return // Already hidden
        
        stopColorAnimation()
        
        // Fade out smoothly
        animate()
            .alpha(0f)
            .setDuration(150)
            .start()
    }

    /**
     * Updates the target color without hiding/showing (for smooth transitions)
     */
    fun updateColor(color: Int) {
        if (alpha > 0f) {
            // Animate to new color smoothly
            colorAnimator?.cancel()
            val currentColor = indeterminateTintList?.defaultColor ?: color
            
            ValueAnimator.ofArgb(currentColor, color).apply {
                duration = 300
                addUpdateListener { animator ->
                    val animatedColor = animator.animatedValue as Int
                    indeterminateTintList = ColorStateList.valueOf(animatedColor)
                }
                start()
            }
        }
    }

    private fun startColorAnimation() {
        if (isAnimating) return
        
        isAnimating = true
        // Start from random color index for variety
        currentColorIndex = (0 until animationColors.size).random()
        
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500 // 1.5 seconds per color - faster, more engaging
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val nextColorIndex = (currentColorIndex + 1) % animationColors.size
                
                // Smooth interpolation between current and next color
                val currentColor = animationColors[currentColorIndex]
                val nextColor = animationColors[nextColorIndex]
                
                val interpolatedColor = interpolateColor(currentColor, nextColor, progress)
                indeterminateTintList = ColorStateList.valueOf(interpolatedColor)
                
                // Move to next color when cycle completes
                if (progress >= 0.95f) {
                    currentColorIndex = nextColorIndex
                }
            }
            
            start()
        }
    }

    private fun stopColorAnimation() {
        colorAnimator?.cancel()
        colorAnimator = null
        isAnimating = false
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val startA = (startColor shr 24) and 0xFF
        val startR = (startColor shr 16) and 0xFF
        val startG = (startColor shr 8) and 0xFF
        val startB = startColor and 0xFF

        val endA = (endColor shr 24) and 0xFF
        val endR = (endColor shr 16) and 0xFF
        val endG = (endColor shr 8) and 0xFF
        val endB = endColor and 0xFF

        return ((startA + (fraction * (endA - startA)).toInt()) shl 24) or
               ((startR + (fraction * (endR - startR)).toInt()) shl 16) or
               ((startG + (fraction * (endG - startG)).toInt()) shl 8) or
               (startB + (fraction * (endB - startB)).toInt())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopColorAnimation()
    }
}