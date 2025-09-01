package com.cyberflux.qwinai.components

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.utils.UnifiedMarkdownProcessor
import android.widget.LinearLayout
import timber.log.Timber

/**
 * Custom view for displaying AI thinking content with streaming capabilities
 * Matches the design from screenshots with auto-scrolling, timer, and state management
 */
class ThinkingContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // UI Components
    private lateinit var headerContainer: View
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var completedIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var toggleButton: ImageButton
    private lateinit var contentArea: View
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewScrollView: ScrollView
    private lateinit var fullContentScrollView: ScrollView
    private lateinit var topFadeOverlay: View
    private lateinit var bottomFadeOverlay: View

    // State management
    private var isThinking = false
    private var isExpanded = false
    private var thinkingStartTime = 0L
    private var thinkingEndTime = 0L
    private var currentContent = StringBuilder()
    
    // Handlers and runnables
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var autoScrollRunnable: Runnable? = null
    
    // Auto-scroll settings
    private val autoScrollDelay = 100L // Scroll every 100ms during streaming
    private val scrollSpeed = 2 // Pixels per scroll
    
    // Callbacks
    var onToggleExpansion: ((isExpanded: Boolean) -> Unit)? = null
    
    // Unified span-free markdown processing
    private lateinit var unifiedMarkdownProcessor: UnifiedMarkdownProcessor
    
    // Content containers for the new unified processor
    private lateinit var previewContentContainer: LinearLayout
    private lateinit var fullContentContainer: LinearLayout

    init {
        setupView()
        setupProcessor()
    }

    private fun setupView() {
        // Inflate the layout
        LayoutInflater.from(context).inflate(R.layout.component_thinking_content, this, true)
        
        // Initialize views
        headerContainer = findViewById(R.id.thinkingHeaderContainer)
        loadingIndicator = findViewById(R.id.thinkingLoadingIndicator)
        completedIcon = findViewById(R.id.thinkingCompletedIcon)
        statusText = findViewById(R.id.thinkingStatusText)
        timerText = findViewById(R.id.thinkingTimer)
        toggleButton = findViewById(R.id.thinkingToggleButton)
        contentArea = findViewById(R.id.thinkingContentArea)
        previewContainer = findViewById(R.id.thinkingPreviewContainer)
        previewScrollView = findViewById(R.id.thinkingPreviewScrollView)
        fullContentScrollView = findViewById(R.id.thinkingFullContentScrollView)
        topFadeOverlay = findViewById(R.id.topFadeOverlay)
        bottomFadeOverlay = findViewById(R.id.bottomFadeOverlay)

        setupListeners()
        updateUIState()
    }
    
    private fun setupProcessor() {
        // Get the UnifiedMarkdownProcessor instance
        unifiedMarkdownProcessor = UnifiedMarkdownProcessor.create(context)
        
        // Replace the TextViews with LinearLayouts for full markdown support including code blocks
        setupMarkdownContainers()
    }
    
    private fun setupMarkdownContainers() {
        // Create LinearLayout containers for full markdown support
        previewContentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(12, 8, 12, 8)
        }
        
        fullContentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(12, 8, 12, 8)
        }
        
        // Replace the TextViews in ScrollViews with our LinearLayouts
        previewScrollView.removeAllViews()
        previewScrollView.addView(previewContentContainer)
        
        fullContentScrollView.removeAllViews()
        fullContentScrollView.addView(fullContentContainer)
        
        Timber.d("🧠 Setup markdown containers for thinking content")
    }

    private fun setupListeners() {
        // Header click toggles expansion when thinking is complete
        headerContainer.setOnClickListener {
            if (!isThinking) {
                toggleExpansion()
            }
        }

        // Toggle button click
        toggleButton.setOnClickListener {
            if (!isThinking) {
                toggleExpansion()
            }
        }
    }

    /**
     * Start the thinking process with timer and auto-scroll
     */
    fun startThinking() {
        if (isThinking) return
        
        isThinking = true
        isExpanded = false
        thinkingStartTime = System.currentTimeMillis()
        currentContent.clear()
        
        Timber.d("🧠 Starting thinking process")
        
        startTimer()
        startAutoScroll()
        updateUIState()
    }

    /**
     * Stop the thinking process and show completion state - FORCE COLLAPSE
     */
    fun stopThinking() {
        Timber.w("🧠 stopThinking() called - isThinking: $isThinking, isExpanded: $isExpanded")
        
        // FORCE completion even if state is inconsistent
        val wasThinking = isThinking
        isThinking = false
        thinkingEndTime = System.currentTimeMillis()
        
        Timber.w("🧠 FORCE stopping thinking process after ${getElapsedTimeFormatted()}")
        
        stopTimer()
        stopAutoScroll()
        
        // Update status text to "Thought for X"
        val elapsed = getElapsedTimeFormatted()
        statusText.text = "Thought for"
        timerText.text = elapsed
        
        // FORCE auto-collapse when thinking completes (like in the images)
        isExpanded = false
        showPreviewContent()
        
        // Set toggle button to collapsed state (arrow pointing down)
        toggleButton.rotation = 0f
        
        // Animate to completed state
        animateToCompletedState()
        updateUIState()
        
        Timber.w("🧠 ✅ FORCE auto-collapsed thinking content: 'Thought for $elapsed' (wasThinking: $wasThinking)")
    }

    /**
     * Append new thinking content and trigger auto-scroll with real-time markdown processing
     */
    fun appendThinkingContent(content: String) {
        if (content.isEmpty()) return
        
        currentContent.append(content)
        val fullText = currentContent.toString()
        
        // Process markdown in real-time for both preview and full content with FULL markdown support
        try {
            // Use streaming processing for real-time updates during thinking
            unifiedMarkdownProcessor.renderStreaming(
                content = fullText,
                container = previewContentContainer,
                webSearchSources = null // No web search in thinking content
            )
            
            unifiedMarkdownProcessor.renderStreaming(
                content = fullText,
                container = fullContentContainer,
                webSearchSources = null // No web search in thinking content
            )
            
            Timber.v("🧠 Updated thinking content with full markdown: ${fullText.length} chars")
        } catch (e: Exception) {
            // Fallback to plain text if any issues
            Timber.w(e, "🧠 Markdown processing failed, using plain text fallback")
            createFallbackTextView(previewContentContainer, fullText)
            createFallbackTextView(fullContentContainer, fullText)
        }
        
        Timber.v("🧠 Appending thinking content with markdown: ${content.length} chars, total: ${fullText.length}")
        
        // Trigger auto-scroll if thinking
        if (isThinking) {
            triggerAutoScroll()
        }
    }

    /**
     * Set the complete thinking content (for when thinking is done) with markdown processing
     */
    fun setThinkingContent(content: String) {
        currentContent.clear()
        currentContent.append(content)
        
        // Set content for both preview and full content with FULL markdown support including code blocks
        try {
            // Use simple rendering for final content (not streaming)
            unifiedMarkdownProcessor.renderSimple(
                content = content,
                container = previewContentContainer,
                webSearchSources = null // No web search in thinking content
            )
            
            unifiedMarkdownProcessor.renderSimple(
                content = content,
                container = fullContentContainer,
                webSearchSources = null // No web search in thinking content
            )
            
            Timber.d("🧠 Set complete thinking content with full markdown: ${content.length} chars")
        } catch (e: Exception) {
            // Fallback to plain text if any issues
            Timber.w(e, "🧠 Markdown processing failed for complete content, using plain text")
            createFallbackTextView(previewContentContainer, content)
            createFallbackTextView(fullContentContainer, content)
        }
        
        Timber.d("🧠 Set complete thinking content with markdown: ${content.length} chars")
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isThinking) {
                    val elapsed = getElapsedTimeFormatted()
                    timerText.text = elapsed
                    handler.postDelayed(this, 1000) // Update every second
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun startAutoScroll() {
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (isThinking && previewContainer.visibility == View.VISIBLE) {
                    scrollToBottom()
                    handler.postDelayed(this, autoScrollDelay)
                }
            }
        }
        handler.post(autoScrollRunnable!!)
    }

    private fun stopAutoScroll() {
        autoScrollRunnable?.let { handler.removeCallbacks(it) }
        autoScrollRunnable = null
    }

    private fun scrollToBottom() {
        previewScrollView.post {
            val maxScroll = previewContentContainer.height - previewScrollView.height
            if (maxScroll > 0) {
                val currentScroll = previewScrollView.scrollY
                val targetScroll = maxScroll
                
                // Smooth scroll animation
                val scrollAnimator = ObjectAnimator.ofInt(currentScroll, targetScroll)
                scrollAnimator.duration = 200
                scrollAnimator.interpolator = AccelerateDecelerateInterpolator()
                scrollAnimator.addUpdateListener { animation ->
                    val value = animation.animatedValue as Int
                    previewScrollView.scrollTo(0, value)
                }
                scrollAnimator.start()
            }
        }
    }

    private fun triggerAutoScroll() {
        // Scroll to show the latest content with automatic scrolling behavior
        previewScrollView.post {
            previewScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun toggleExpansion() {
        isExpanded = !isExpanded
        
        Timber.d("🧠 Toggling expansion: $isExpanded")
        
        onToggleExpansion?.invoke(isExpanded)
        
        if (isExpanded) {
            showFullContent()
        } else {
            showPreviewContent()
        }
        
        animateToggleButton()
    }

    private fun showFullContent() {
        previewContainer.visibility = View.GONE
        fullContentScrollView.visibility = View.VISIBLE
        
        // Animate expansion
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 300
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            fullContentScrollView.alpha = value
        }
        animator.start()
    }

    private fun showPreviewContent() {
        fullContentScrollView.visibility = View.GONE
        previewContainer.visibility = View.VISIBLE
        
        // Animate collapse
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 300
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            previewContainer.alpha = value
        }
        animator.start()
    }

    private fun animateToggleButton() {
        val rotation = if (isExpanded) 180f else 0f
        
        val rotateAnimator = ObjectAnimator.ofFloat(toggleButton, "rotation", rotation)
        rotateAnimator.duration = 300
        rotateAnimator.interpolator = AccelerateDecelerateInterpolator()
        rotateAnimator.start()
    }

    private fun animateToCompletedState() {
        // Fade out loading indicator and fade in completed icon
        val fadeOut = ObjectAnimator.ofFloat(loadingIndicator, "alpha", 0f)
        fadeOut.duration = 200
        
        val fadeIn = ObjectAnimator.ofFloat(completedIcon, "alpha", 1f)
        fadeIn.duration = 200
        fadeIn.startDelay = 200
        
        fadeOut.start()
        fadeIn.start()
        
        // Update visibility after animation
        handler.postDelayed({
            loadingIndicator.visibility = View.GONE
            completedIcon.visibility = View.VISIBLE
        }, 400)
    }

    private fun updateUIState() {
        if (isThinking) {
            // Thinking state
            statusText.text = "Thinking"
            loadingIndicator.visibility = View.VISIBLE
            completedIcon.visibility = View.GONE
            toggleButton.isEnabled = false
            toggleButton.alpha = 0.5f
            
            // Show preview container, hide full content
            previewContainer.visibility = View.VISIBLE
            fullContentScrollView.visibility = View.GONE
            
            // Show fade overlays for preview
            topFadeOverlay.visibility = View.VISIBLE
            bottomFadeOverlay.visibility = View.VISIBLE
            
        } else {
            // Completed state
            loadingIndicator.visibility = View.GONE
            completedIcon.visibility = View.VISIBLE
            toggleButton.isEnabled = true
            toggleButton.alpha = 1f
            
            // Allow expansion/collapse
            if (isExpanded) {
                showFullContent()
            } else {
                showPreviewContent()
                // Hide fade overlays when collapsed and thinking is done
                topFadeOverlay.visibility = View.GONE
                bottomFadeOverlay.visibility = View.GONE
            }
        }
    }

    private fun getElapsedTimeFormatted(): String {
        val startTime = if (thinkingStartTime > 0) thinkingStartTime else System.currentTimeMillis()
        val endTime = if (thinkingEndTime > 0) thinkingEndTime else System.currentTimeMillis()
        val elapsed = endTime - startTime
        
        val totalSeconds = elapsed / 1000
        
        // Always use "Xs" format like in the images (0s, 37s, 49s, etc.)
        return "${totalSeconds}s"
    }

    /**
     * Check if currently in thinking state
     */
    fun isCurrentlyThinking(): Boolean = isThinking

    /**
     * Check if currently expanded
     */
    fun isCurrentlyExpanded(): Boolean = isExpanded

    /**
     * Get the current thinking content
     */
    fun getCurrentContent(): String = currentContent.toString()

    /**
     * Get thinking duration in milliseconds
     */
    fun getThinkingDuration(): Long {
        return if (thinkingEndTime > 0 && thinkingStartTime > 0) {
            thinkingEndTime - thinkingStartTime
        } else if (thinkingStartTime > 0) {
            System.currentTimeMillis() - thinkingStartTime
        } else {
            0L
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up handlers to prevent memory leaks
        stopTimer()
        stopAutoScroll()
    }
    
    
    /**
     * Create a fallback TextView when markdown processing fails
     */
    private fun createFallbackTextView(container: LinearLayout, content: String) {
        container.removeAllViews()
        val fallbackTextView = TextView(context).apply {
            text = content
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.primary_text))
            setPadding(8, 8, 8, 8)
            setLineSpacing(0f, 1.2f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(fallbackTextView)
    }
}