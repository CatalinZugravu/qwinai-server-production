package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import java.lang.ref.WeakReference

/**
 * A TextView that displays animated dots, commonly used for loading indicators
 * Shows "Processing..." with animated dots
 */
class DotLoadingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var baseText = "Processing"
    private val dots = "..."
    private val dotCount = 3
    private val animationDelay = 400L // ms between frames

    private val dotColors = arrayOf(
        Color.parseColor("#4285F4"), // Google Blue
        Color.parseColor("#34A853"), // Google Green
        Color.parseColor("#FBBC04"), // Google Yellow
        Color.parseColor("#EA4335")  // Google Red
    )

    private var colorIndex = 0
    private var isAnimating = false
    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = AnimationRunnable(this)

    /**
     * Start the dot animation
     */
    fun startAnimation() {
        if (isAnimating) return

        isAnimating = true
        handler.post(animationRunnable)
    }

    /**
     * Stop the dot animation
     */
    fun stopAnimation() {
        isAnimating = false
        handler.removeCallbacks(animationRunnable)

        // Reset the text
        text = baseText + dots
    }

    /**
     * Set the base text before the dots (e.g., "Loading", "Processing")
     */
    fun setBaseText(text: String) {
        baseText = text
        updateText()
    }

    /**
     * Update the animation frame
     */
    private fun updateText() {
        val fullText = baseText + dots
        val spannable = SpannableString(fullText)

        // Calculate starting position for dots
        val dotsStart = baseText.length

        // Apply spans to each dot
        for (i in 0 until dotCount) {
            if (dotsStart + i < fullText.length) {
                val currentColor = dotColors[(colorIndex + i) % dotColors.size]
                spannable.setSpan(
                    ForegroundColorSpan(currentColor),
                    dotsStart + i,
                    dotsStart + i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Update the text view
        text = spannable
    }

    /**
     * Animation Runnable with WeakReference to avoid memory leaks
     */
    private class AnimationRunnable(view: DotLoadingTextView) : Runnable {
        private val viewRef = WeakReference(view)

        override fun run() {
            val view = viewRef.get() ?: return

            if (view.isAnimating) {
                view.colorIndex = (view.colorIndex + 1) % view.dotColors.size
                view.updateText()
                view.handler.postDelayed(this, view.animationDelay)
            }
        }
    }

    /**
     * Clean up when view is detached
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}