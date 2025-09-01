package com.cyberflux.qwinai.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
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

    private var currentColor: Int = 0xFF6366F1.toInt() // Default indigo color
    private var isVisible = false

    // Color cycling for smooth transitions
    private val colorCycleAnimator: ValueAnimator? = null
    private val colorPalette = arrayOf(
        0xFF6366F1.toInt(), // Indigo
        0xFF8B5CF6.toInt(), // Violet  
        0xFF3B82F6.toInt(), // Blue
        0xFF06B6D4.toInt(), // Cyan
        0xFF10B981.toInt(), // Emerald
        0xFF84CC16.toInt(), // Lime
    )
    private var colorAnimator: ValueAnimator? = null
    private var colorIndex = 0

    // Fixed, stable color that never changes - no flickering
    private val DEFAULT_INDICATOR_COLOR = 0xFF6366F1.toInt() // Professional indigo

    init {
        // Set initial style and make it always visible but transparent when not needed
        isIndeterminate = true
        alpha = 0f // Start invisible
        visibility = VISIBLE // Always keep in layout to prevent flickering
        
        // Set initial stable color
        indeterminateTintList = ColorStateList.valueOf(DEFAULT_INDICATOR_COLOR)
    }

    /**
     * FIXED: Shows the loading indicator with thread-safe UI updates
     */
    fun show(targetColor: Int? = null) {
        // FIXED: Ensure UI operations run on main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                showOnMainThread(targetColor)
            }
            return
        }
        showOnMainThread(targetColor)
    }
    
    private fun showOnMainThread(targetColor: Int?) {
        // Use provided color as the starting color, or use default
        val initialColor = targetColor?.let { ensureVisibleColor(it) } ?: DEFAULT_INDICATOR_COLOR
        
        currentColor = initialColor
        indeterminateTintList = ColorStateList.valueOf(initialColor)
        
        isVisible = true
        
        // Start color cycling animation for smooth color changes
        startColorCycling()
        
        // Ensure visibility with smooth fade in
        if (alpha < 1f) {
            animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    /**
     * FIXED: Hides the loading indicator with thread-safe UI updates
     */
    fun hide() {
        // FIXED: Ensure UI operations run on main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                hideOnMainThread()
            }
            return
        }
        hideOnMainThread()
    }
    
    private fun hideOnMainThread() {
        if (alpha == 0f && !isVisible) return // Already hidden
        
        isVisible = false
        
        // Stop color cycling animation
        stopColorCycling()
        
        // Fade out smoothly
        animate()
            .alpha(0f)
            .setDuration(150)
            .start()
    }


    /**
     * Ensures the color is visible (not white/transparent/too light)
     */
    private fun ensureVisibleColor(color: Int): Int {
        // Extract RGB components
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF  
        val blue = color and 0xFF
        
        // Calculate brightness (perceived luminance)
        val brightness = (0.299 * red + 0.587 * green + 0.114 * blue)
        
        // If too bright (close to white), use default color
        return if (brightness > 200) { // Threshold for "too bright"
            DEFAULT_INDICATOR_COLOR
        } else {
            color
        }
    }

    /**
     * Start smooth color cycling animation with proper timing
     */
    private fun startColorCycling() {
        stopColorCycling() // Stop any existing animation
        
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500L // Slower transitions for pleasant effect
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                if (isVisible) {
                    val progress = animator.animatedValue as Float
                    
                    // Create smooth transitions between colors
                    val fromColor = colorPalette[colorIndex % colorPalette.size]
                    val toColor = colorPalette[(colorIndex + 1) % colorPalette.size]
                    
                    // Use ArgbEvaluator for smooth color interpolation
                    val interpolatedColor = android.animation.ArgbEvaluator().evaluate(
                        progress, fromColor, toColor
                    ) as Int
                    
                    currentColor = interpolatedColor
                    indeterminateTintList = ColorStateList.valueOf(interpolatedColor)
                }
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    // Move to next color in the palette
                    colorIndex = (colorIndex + 1) % colorPalette.size
                }
            })
            
            start()
        }
    }
    
    /**
     * Stop color cycling animation
     */
    private fun stopColorCycling() {
        colorAnimator?.cancel()
        colorAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up any running animations
        animate().cancel()
        stopColorCycling()
    }
}