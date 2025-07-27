package com.cyberflux.qwinai.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView

/**
 * Custom TextView that shows an animated "Thinking..." text with wave character
 */
class PulsingGlowTextView(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    private var pulseAnimator: ValueAnimator? = null
    private var glowAnimator: ValueAnimator? = null
    private var shadowRadius = 0f
    private var shadowColor = Color.parseColor("#006db3") // Blue theme

    init {
        // Set initial shadow properties
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setShadowLayer(0f, 0f, 0f, shadowColor)
    }

    fun startAnimation() {
        stopAnimation()

        // Create the pulse animation (subtle size change)
        pulseAnimator = ValueAnimator.ofFloat(0.95f, 1.05f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }

            start()
        }

        // Create the glow animation (shadow radius change)
        glowAnimator = ValueAnimator.ofFloat(0f, 10f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                shadowRadius = animator.animatedValue as Float
                setShadowLayer(shadowRadius, 0f, 0f, shadowColor)
                invalidate()
            }

            start()
        }
    }

    fun stopAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null

        glowAnimator?.cancel()
        glowAnimator = null

        // Reset properties
        scaleX = 1f
        scaleY = 1f
        setShadowLayer(0f, 0f, 0f, shadowColor)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    // Change glow color
    fun setGlowColor(color: Int) {
        shadowColor = color
        invalidate()
    }

}