package com.cyberflux.qwinai.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.text.style.ReplacementSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.utils.CodeSyntaxHighlighter
import timber.log.Timber
import java.lang.ref.WeakReference

class CodeBlockSpan(
    context: Context,
    private val codeContent: String,
    private val language: String,
    container: View
) : ReplacementSpan() {

    // Use weak references to prevent memory leaks
    private val contextRef = WeakReference(context)
    private val containerRef = WeakReference(container)

    private var codeBlockView: View? = null
    private var measuredWidth = 0
    private var measuredHeight = 0
    private var isViewInitialized = false
    private var availableWidth = 0

    private val spanBounds = Rect(0, 0, 0, 0)
    private val copyBounds = Rect()

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val container = containerRef.get() ?: return 0
        calculateAvailableWidth(container)
        if (!isViewInitialized) createAndMeasureView()
        fm?.let {
            it.ascent = -measuredHeight
            it.descent = 0
            it.top = it.ascent
            it.bottom = 0
        }
        return availableWidth
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val container = containerRef.get() ?: return

        // Ensure proper measurement
        if (!isViewInitialized || availableWidth != container.width) {
            isViewInitialized = false
            createAndMeasureView()
        }

        // Draw with proper anti-aliasing
        canvas.save()
        try {
            canvas.translate(x, top.toFloat())
            codeBlockView?.draw(canvas)
        } finally {
            canvas.restore()
        }
    }


    private fun calculateAvailableWidth(container: View) {
        val w = container.width - container.paddingLeft - container.paddingRight
        availableWidth = w.coerceAtLeast(100)
    }

    private fun createAndMeasureView() {
        val context = contextRef.get() ?: return

        try {
            if (availableWidth <= 0) return

            // Clean up previous view
            codeBlockView = null

            // Inflate WITHOUT parent so we measure ourselves
            codeBlockView = LayoutInflater.from(context)
                .inflate(R.layout.item_code_block, null, false)

            val view = codeBlockView ?: return

            // Set up content
            view.findViewById<TextView>(R.id.tvLanguage).text =
                language.ifEmpty { "code" }

            val codeTextView = view.findViewById<TextView>(R.id.tvCodeContent)
            // Apply syntax highlighting to the code content
            val highlightedContent = if (codeContent.isNotBlank()) {
                CodeSyntaxHighlighter.highlight(context, codeContent, language)
            } else {
                android.text.SpannableString("// Empty code block")
            }
            codeTextView.text = highlightedContent

            // FIXED: Proper setup for horizontal scrolling
            codeTextView.setHorizontallyScrolling(true) // Enable horizontal scrolling
            codeTextView.isSingleLine = false
            codeTextView.maxLines = Int.MAX_VALUE
            codeTextView.minWidth = estimateContentWidth(codeTextView) // Set minimum width based on content
            codeTextView.ellipsize = null
            
            // Add accessibility
            codeTextView.contentDescription = "Code block in $language: $codeContent"

            val copyButton = view.findViewById<LinearLayout>(R.id.btnCopyCode)
            setupCopyButton(context, copyButton)

            // FIXED: Set up HorizontalScrollView properly
            val horizontalScrollView = view.findViewById<HorizontalScrollView>(R.id.scrollView) 
                ?: view.findViewById<HorizontalScrollView>(android.R.id.custom) // Fallback
            
            horizontalScrollView?.let { hsv ->
                hsv.isHorizontalScrollBarEnabled = true
                hsv.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                hsv.isSmoothScrollingEnabled = true
                hsv.isHorizontalFadingEdgeEnabled = true
                hsv.setFadingEdgeLength(16)
            }

            // IMPROVED: Measure with proper width constraints
            val contentWidth = estimateContentWidth(codeTextView)
            val actualWidth = maxOf(availableWidth, contentWidth)
            
            val widthSpec = View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthSpec, heightSpec)
            measuredWidth = view.measuredWidth
            measuredHeight = view.measuredHeight
            
            // Layout with proper bounds
            view.layout(0, 0, measuredWidth, measuredHeight)

            // Record copy-icon bounds inside the view
            copyBounds.set(
                copyButton.left,
                copyButton.top,
                copyButton.right,
                copyButton.bottom
            )

            // Set span bounds for the entire view
            spanBounds.set(0, 0, availableWidth, measuredHeight)

            isViewInitialized = true
        } catch (e: Exception) {
            Timber.e(e, "Error initializing code block view: ${e.message}")
            isViewInitialized = false
        }
    }
    
    private fun estimateContentWidth(textView: TextView): Int {
        val paint = textView.paint
        val lines = codeContent.split("\n")
        var maxWidth = 0f
        
        for (line in lines) {
            val lineWidth = paint.measureText(line)
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth
            }
        }
        
        return (maxWidth + textView.paddingStart + textView.paddingEnd).toInt()
    }

    private fun setupCopyButton(context: Context, copyButton: LinearLayout) {
        // Ensure the button is clickable and focusable
        copyButton.isClickable = true
        copyButton.isFocusable = true
        copyButton.isEnabled = true
        
        // Set up click listener with animation
        copyButton.setOnClickListener {
            Timber.d("Copy button clicked for code content: ${codeContent.take(50)}...")
            performCopyAction(context, copyButton)
        }

        // Also add touch listener for better feedback
        copyButton.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.7f
                    true
                }
                android.view.MotionEvent.ACTION_UP, 
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1.0f
                    false
                }
                else -> false
            }
        }

        // Add accessibility delegate
        ViewCompat.setAccessibilityDelegate(copyButton, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.roleDescription = "Copy code button"
                info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            }
        })
    }

    private fun performCopyAction(context: Context, view: View) {
        copyToClipboard()

        // Animation feedback
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()

        // Haptic feedback
        provideHapticFeedback(context)
    }

    private fun provideHapticFeedback(context: Context) {
        try {
            HapticManager.mediumVibration(context)
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    private fun copyToClipboard() {
        val context = contextRef.get() ?: return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("code", codeContent)
            cm.setPrimaryClip(clip)
            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
            Timber.d("Code copied to clipboard: ${codeContent.take(50)}...")
        } catch (e: Exception) {
            Timber.e(e, "Clipboard error: ${e.message}")
            Toast.makeText(context, "Failed to copy code", Toast.LENGTH_SHORT).show()
        }
    }

    // Clean up resources
    fun cleanup() {
        codeBlockView = null
        contextRef.clear()
        containerRef.clear()
    }
}