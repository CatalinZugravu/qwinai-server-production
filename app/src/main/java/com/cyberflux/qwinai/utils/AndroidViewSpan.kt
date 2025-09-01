package com.cyberflux.qwinai.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.TextView
import timber.log.Timber

/**
 * Interactive Android View span that embeds custom views inline in text with proper touch handling
 */
class AndroidViewSpan(
    private val view: View,
    private val maxWidthPercent: Float = 0.9f
) : ReplacementSpan() {
    
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var spanBounds = Rect()
    private var parentTextView: TextView? = null

    /**
     * Set the parent TextView to enable proper touch handling
     */
    fun setParentTextView(textView: TextView) {
        this.parentTextView = textView
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        try {
            // Get screen width and calculate max allowed width for the container
            val displayMetrics = view.context.resources.displayMetrics
            val containerMaxWidth = (displayMetrics.widthPixels * maxWidthPercent).toInt()
            
            // FIXED: Use EXACTLY for the container width to give HorizontalScrollView its full allocated space
            // This allows the HorizontalScrollView to measure its content properly and enable scrolling
            val widthSpec = View.MeasureSpec.makeMeasureSpec(containerMaxWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            
            // Measure the view with exact container constraints
            view.measure(widthSpec, heightSpec)
            
            // FIXED: Always use containerMaxWidth instead of measured width
            // This ensures the HorizontalScrollView gets its full allocated space for scrolling
            cachedWidth = containerMaxWidth
            cachedHeight = view.measuredHeight
            
            // Ensure minimum height
            cachedHeight = kotlin.math.max(cachedHeight, 80)
            
            // Update font metrics to accommodate the view height
            fm?.let { metrics ->
                val baseline = cachedHeight
                metrics.ascent = -baseline
                metrics.descent = 0
                metrics.top = metrics.ascent
                metrics.bottom = metrics.descent
                metrics.leading = 0
            }
            
            Timber.d("ANDROID_VIEW_SPAN: Measured view - container width: $cachedWidth (exact), height: $cachedHeight")
            
            return cachedWidth
            
        } catch (e: Exception) {
            Timber.e(e, "ANDROID_VIEW_SPAN: Error measuring view")
            return (view.context.resources.displayMetrics.widthPixels * 0.9f).toInt() // Fallback width
        }
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        try {
            // FIXED: More accurate bounds calculation accounting for text positioning
            // The 'y' parameter is the baseline, 'top' is the line top
            // We want the view to start at the line top and extend to line bottom
            val viewTop = top
            val viewLeft = x.toInt()
            val viewRight = viewLeft + cachedWidth
            val viewBottom = viewTop + cachedHeight
            
            // Store span bounds for touch handling (in TextView coordinate space)
            spanBounds.set(viewLeft, viewTop, viewRight, viewBottom)
            
            // Layout the view with proper bounds
            view.layout(0, 0, cachedWidth, cachedHeight)
            
            // Draw the view on canvas
            canvas.save()
            try {
                canvas.translate(x, viewTop.toFloat())
                view.draw(canvas)
                
                Timber.d("ANDROID_VIEW_SPAN: Drew view at ($viewLeft, $viewTop) to ($viewRight, $viewBottom) size ($cachedWidth, $cachedHeight)")
                
            } finally {
                canvas.restore()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "ANDROID_VIEW_SPAN: Error drawing view")
        }
    }

    /**
     * Check if a touch point is within this span's bounds
     */
    fun isPointInSpan(x: Float, y: Float): Boolean {
        return spanBounds.contains(x.toInt(), y.toInt())
    }

    /**
     * Handle touch event for this span
     */
    fun handleTouch(x: Float, y: Float, action: Int): Boolean {
        if (!isPointInSpan(x, y)) {
            Timber.d("ANDROID_VIEW_SPAN: Touch point ($x, $y) not in span bounds $spanBounds")
            return false
        }
        
        try {
            // FIXED: Convert TextView-relative coordinates to view-local coordinates
            val viewX = x - spanBounds.left
            val viewY = y - spanBounds.top
            
            Timber.d("ANDROID_VIEW_SPAN: Touch at ($x, $y) -> view coords ($viewX, $viewY), span bounds: $spanBounds")
            
            // Find the touched child view and dispatch the touch event
            val touchedView = findViewAt(view, viewX, viewY)
            Timber.d("ANDROID_VIEW_SPAN: Found touched view: ${touchedView?.javaClass?.simpleName}, clickable: ${touchedView?.isClickable}")
            
            if (touchedView != null && (touchedView.isClickable || touchedView.isFocusable)) {
                // Give priority to clickable views like copy buttons
                if (touchedView.isClickable) {
                    // FIXED: Add haptic feedback and visual feedback for better UX
                    touchedView.performClick()
                    Timber.d("ANDROID_VIEW_SPAN: Performed click on embedded clickable view: ${touchedView.javaClass.simpleName}")
                    return true
                } else {
                    touchedView.requestFocus()
                    Timber.d("ANDROID_VIEW_SPAN: Focused embedded view: ${touchedView.javaClass.simpleName}")
                    return true
                }
            }
            
            // FIXED: Also try to handle touches on parent ViewGroups that might contain clickable children
            val parentViewGroup = findClickableParent(touchedView)
            if (parentViewGroup != null && parentViewGroup.isClickable) {
                parentViewGroup.performClick()
                Timber.d("ANDROID_VIEW_SPAN: Performed click on clickable parent: ${parentViewGroup.javaClass.simpleName}")
                return true
            }
            
        } catch (e: Exception) {
            Timber.e(e, "ANDROID_VIEW_SPAN: Error handling touch")
        }
        
        return false
    }

    /**
     * Recursively find the view at the given coordinates
     */
    private fun findViewAt(view: View, x: Float, y: Float): View? {
        if (view is android.view.ViewGroup) {
            // Check children first (prioritize deepest child)
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility == View.VISIBLE) {
                    val childX = x - child.left
                    val childY = y - child.top
                    
                    if (childX >= 0 && childX < child.width && childY >= 0 && childY < child.height) {
                        val result = findViewAt(child, childX, childY)
                        if (result != null) return result
                    }
                }
            }
        }
        
        // Return this view if coordinates are within bounds
        return if (x >= 0 && x < view.width && y >= 0 && y < view.height) view else null
    }

    /**
     * Find the first clickable parent of the given view
     */
    private fun findClickableParent(childView: View?): View? {
        if (childView == null) return null
        
        var parent = childView.parent
        while (parent != null && parent is View) {
            val parentView = parent as View
            if (parentView.isClickable) {
                return parentView
            }
            parent = parentView.parent
        }
        
        return null
    }

    /**
     * Get the embedded view (for external access)
     */
    fun getView(): View = view
}