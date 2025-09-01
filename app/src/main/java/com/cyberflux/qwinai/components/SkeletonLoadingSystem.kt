package com.cyberflux.qwinai.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced skeleton loading system with multiple animation types
 * Provides smooth loading animations for various UI components
 */
@Singleton
class SkeletonLoadingSystem @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val SHIMMER_DURATION_MS = 1500L
        private const val PULSE_DURATION_MS = 1000L
        private const val WAVE_DURATION_MS = 2000L
        private const val TYPING_SPEED_MS = 100L
        private const val DEFAULT_CORNER_RADIUS = 8f
    }
    
    private val activeAnimations = mutableMapOf<String, SkeletonAnimation>()
    private val animationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Skeleton animation types
     */
    enum class AnimationType {
        SHIMMER,        // Classic shimmer effect
        PULSE,          // Pulsing opacity
        WAVE,           // Wave motion
        TYPING,         // Typing indicator
        BREATHING,      // Breathing scale effect
        SLIDE           // Sliding highlight
    }
    
    /**
     * Skeleton configuration
     */
    data class SkeletonConfig(
        val animationType: AnimationType = AnimationType.SHIMMER,
        val duration: Long = SHIMMER_DURATION_MS,
        val baseColor: Int = Color.parseColor("#E0E0E0"),
        val highlightColor: Int = Color.parseColor("#F5F5F5"),
        val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
        val shimmerWidth: Float = 0.3f,
        val shimmerAngle: Float = 20f,
        val enableAutoStart: Boolean = true,
        val repeatCount: Int = ValueAnimator.INFINITE
    )
    
    /**
     * Base skeleton animation interface
     */
    interface SkeletonAnimation {
        fun start()
        fun stop()
        fun pause()
        fun resume()
        val isRunning: Boolean
    }
    
    /**
     * Shimmer skeleton animation
     */
    class ShimmerAnimation(
        private val view: View,
        private val config: SkeletonConfig
    ) : SkeletonAnimation {
        
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shimmerDrawable = ShimmerDrawable(config)
        private var animator: ValueAnimator? = null
        private val _isRunning = AtomicBoolean(false)
        
        override val isRunning: Boolean get() = _isRunning.get()
        
        init {
            view.background = shimmerDrawable
        }
        
        override fun start() {
            if (_isRunning.compareAndSet(false, true)) {
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = config.duration
                    repeatCount = config.repeatCount
                    interpolator = LinearInterpolator()
                    addUpdateListener { animation ->
                        shimmerDrawable.setProgress(animation.animatedValue as Float)
                        view.invalidate()
                    }
                    start()
                }
            }
        }
        
        override fun stop() {
            if (_isRunning.compareAndSet(true, false)) {
                animator?.cancel()
                animator = null
                view.background = null
            }
        }
        
        override fun pause() {
            animator?.pause()
        }
        
        override fun resume() {
            animator?.resume()
        }
    }
    
    /**
     * Pulse skeleton animation
     */
    class PulseAnimation(
        private val view: View,
        private val config: SkeletonConfig
    ) : SkeletonAnimation {
        
        private var animator: ValueAnimator? = null
        private val _isRunning = AtomicBoolean(false)
        private val originalAlpha = view.alpha
        
        override val isRunning: Boolean get() = _isRunning.get()
        
        override fun start() {
            if (_isRunning.compareAndSet(false, true)) {
                view.setBackgroundColor(config.baseColor)
                animator = ValueAnimator.ofFloat(0.3f, 1.0f).apply {
                    duration = config.duration
                    repeatCount = config.repeatCount
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { animation ->
                        view.alpha = animation.animatedValue as Float
                    }
                    start()
                }
            }
        }
        
        override fun stop() {
            if (_isRunning.compareAndSet(true, false)) {
                animator?.cancel()
                animator = null
                view.alpha = originalAlpha
                view.background = null
            }
        }
        
        override fun pause() {
            animator?.pause()
        }
        
        override fun resume() {
            animator?.resume()
        }
    }
    
    /**
     * Typing indicator animation
     */
    class TypingAnimation(
        private val textView: TextView,
        private val config: SkeletonConfig
    ) : SkeletonAnimation {
        
        private var job: Job? = null
        private val _isRunning = AtomicBoolean(false)
        private val typingTexts = listOf("●", "●●", "●●●")
        private val originalText = textView.text
        
        override val isRunning: Boolean get() = _isRunning.get()
        
        override fun start() {
            if (_isRunning.compareAndSet(false, true)) {
                job = CoroutineScope(Dispatchers.Main).launch {
                    var index = 0
                    while (isActive && _isRunning.get()) {
                        textView.text = typingTexts[index]
                        index = (index + 1) % typingTexts.size
                        delay(TYPING_SPEED_MS)
                    }
                }
            }
        }
        
        override fun stop() {
            if (_isRunning.compareAndSet(true, false)) {
                job?.cancel()
                job = null
                textView.text = originalText
            }
        }
        
        override fun pause() {
            job?.cancel()
        }
        
        override fun resume() {
            if (_isRunning.get()) {
                start()
            }
        }
    }
    
    /**
     * Custom shimmer drawable
     */
    private class ShimmerDrawable(private val config: SkeletonConfig) : Drawable() {
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var progress = 0f
        
        init {
            paint.color = config.baseColor
            shimmerPaint.shader = createShimmerShader()
        }
        
        fun setProgress(progress: Float) {
            this.progress = progress
            invalidateSelf()
        }
        
        private fun createShimmerShader(): LinearGradient {
            val colors = intArrayOf(
                config.baseColor,
                config.highlightColor,
                config.baseColor
            )
            val positions = floatArrayOf(0f, 0.5f, 1f)
            
            return LinearGradient(
                0f, 0f, 100f, 0f,
                colors, positions,
                Shader.TileMode.CLAMP
            )
        }
        
        override fun draw(canvas: Canvas) {
            val bounds = bounds
            
            // Draw base color
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                config.cornerRadius,
                config.cornerRadius,
                paint
            )
            
            // Draw shimmer effect
            val shimmerWidth = bounds.width() * config.shimmerWidth
            val shimmerLeft = bounds.left + (bounds.width() - shimmerWidth) * progress
            
            canvas.save()
            canvas.clipRect(
                shimmerLeft,
                bounds.top.toFloat(),
                shimmerLeft + shimmerWidth,
                bounds.bottom.toFloat()
            )
            
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                config.cornerRadius,
                config.cornerRadius,
                shimmerPaint
            )
            
            canvas.restore()
        }
        
        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            shimmerPaint.alpha = alpha
        }
        
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            shimmerPaint.colorFilter = colorFilter
        }
        
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
    
    /**
     * Create skeleton for message loading
     */
    fun createMessageSkeleton(
        parent: ViewGroup,
        isUserMessage: Boolean = false,
        config: SkeletonConfig = SkeletonConfig()
    ): View {
        val skeletonView = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 12, 16, 12)
        }
        
        val messageContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                if (isUserMessage) (parent.width * 0.8).toInt() else (parent.width * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUserMessage) android.view.Gravity.END else android.view.Gravity.START
            }
        }
        
        // Add skeleton lines
        repeat(if (isUserMessage) 2 else 3) { index ->
            val line = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    when (index) {
                        0 -> ViewGroup.LayoutParams.MATCH_PARENT
                        1 -> (parent.width * 0.7).toInt()
                        else -> (parent.width * 0.4).toInt()
                    },
                    40
                ).apply {
                    setMargins(0, 0, 0, 8)
                }
            }
            messageContainer.addView(line)
            
            // Apply skeleton animation
            val animation = createAnimation(line, config.copy(
                duration = config.duration + (index * 200L) // Stagger animations
            ))
            animation.start()
        }
        
        skeletonView.addView(messageContainer)
        return skeletonView
    }
    
    /**
     * Create skeleton for conversation list
     */
    fun createConversationSkeleton(
        parent: ViewGroup,
        itemCount: Int = 5,
        config: SkeletonConfig = SkeletonConfig()
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        repeat(itemCount) { index ->
            val itemView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    120
                ).apply {
                    setMargins(16, 8, 16, 8)
                }
                setPadding(16, 12, 16, 12)
            }
            
            // Avatar skeleton
            val avatar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    setMargins(0, 0, 12, 0)
                }
            }
            
            // Content skeleton
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            // Title line
            val title = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    24
                ).apply {
                    setMargins(0, 0, 0, 8)
                }
            }
            
            // Subtitle line
            val subtitle = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (parent.width * 0.6).toInt(),
                    20
                )
            }
            
            content.addView(title)
            content.addView(subtitle)
            itemView.addView(avatar)
            itemView.addView(content)
            container.addView(itemView)
            
            // Apply animations with staggered timing
            listOf(avatar, title, subtitle).forEachIndexed { animIndex, view ->
                val animation = createAnimation(view, config.copy(
                    duration = config.duration + (index * 100L) + (animIndex * 50L)
                ))
                animation.start()
            }
        }
        
        return container
    }
    
    /**
     * Create skeleton for loading state
     */
    fun createLoadingSkeleton(
        type: LoadingSkeletonType,
        parent: ViewGroup,
        config: SkeletonConfig = SkeletonConfig()
    ): View {
        return when (type) {
            LoadingSkeletonType.MESSAGE -> createMessageSkeleton(parent, false, config)
            LoadingSkeletonType.USER_MESSAGE -> createMessageSkeleton(parent, true, config)
            LoadingSkeletonType.CONVERSATION_LIST -> createConversationSkeleton(parent, 5, config)
            LoadingSkeletonType.TYPING -> createTypingSkeleton(parent, config)
        }
    }
    
    /**
     * Create typing indicator skeleton
     */
    private fun createTypingSkeleton(
        parent: ViewGroup,
        config: SkeletonConfig
    ): View {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 8)
        }
        
        val typingView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.START
            }
            text = "●●●"
            textSize = 24f
            setTextColor(config.baseColor)
        }
        
        container.addView(typingView)
        
        // Apply typing animation
        val animation = TypingAnimation(typingView, config)
        animation.start()
        
        return container
    }
    
    /**
     * Create animation based on type
     */
    private fun createAnimation(view: View, config: SkeletonConfig): SkeletonAnimation {
        return when (config.animationType) {
            AnimationType.SHIMMER -> ShimmerAnimation(view, config)
            AnimationType.PULSE -> PulseAnimation(view, config)
            AnimationType.TYPING -> if (view is TextView) {
                TypingAnimation(view, config)
            } else {
                ShimmerAnimation(view, config)
            }
            else -> ShimmerAnimation(view, config) // Default to shimmer
        }
    }
    
    /**
     * Start skeleton animation for a view
     */
    fun startSkeleton(
        view: View,
        animationId: String,
        config: SkeletonConfig = SkeletonConfig()
    ) {
        stopSkeleton(animationId) // Stop any existing animation
        
        val animation = createAnimation(view, config)
        activeAnimations[animationId] = animation
        
        if (config.enableAutoStart) {
            animation.start()
        }
    }
    
    /**
     * Stop skeleton animation
     */
    fun stopSkeleton(animationId: String) {
        activeAnimations.remove(animationId)?.stop()
    }
    
    /**
     * Stop all skeleton animations
     */
    fun stopAllSkeletons() {
        activeAnimations.values.forEach { it.stop() }
        activeAnimations.clear()
    }
    
    /**
     * Pause all skeleton animations
     */
    fun pauseAllSkeletons() {
        activeAnimations.values.forEach { it.pause() }
    }
    
    /**
     * Resume all skeleton animations
     */
    fun resumeAllSkeletons() {
        activeAnimations.values.forEach { it.resume() }
    }
    
    /**
     * Get skeleton statistics
     */
    fun getSkeletonStats(): SkeletonStats {
        val activeCount = activeAnimations.size
        val runningCount = activeAnimations.values.count { it.isRunning }
        
        return SkeletonStats(
            activeSkeletons = activeCount,
            runningSkeletons = runningCount,
            memoryUsage = activeCount * 100 // Rough estimate in bytes
        )
    }
    
    enum class LoadingSkeletonType {
        MESSAGE,
        USER_MESSAGE,
        CONVERSATION_LIST,
        TYPING
    }
    
    data class SkeletonStats(
        val activeSkeletons: Int,
        val runningSkeletons: Int,
        val memoryUsage: Int
    )
}

/**
 * Skeleton loading view for easy XML integration
 */
class SkeletonLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private var skeletonAnimation: SkeletonLoadingSystem.SkeletonAnimation? = null
    private val config = SkeletonLoadingSystem.SkeletonConfig()
    
    fun startSkeleton() {
        skeletonAnimation = SkeletonLoadingSystem.ShimmerAnimation(this, config)
        skeletonAnimation?.start()
    }
    
    fun stopSkeleton() {
        skeletonAnimation?.stop()
        skeletonAnimation = null
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopSkeleton()
    }
}

/**
 * Extension functions for easy integration
 */
fun View.startSkeletonLoading(
    animationType: SkeletonLoadingSystem.AnimationType = SkeletonLoadingSystem.AnimationType.SHIMMER,
    duration: Long = 1500L
) {
    val config = SkeletonLoadingSystem.SkeletonConfig(
        animationType = animationType,
        duration = duration
    )
    
    val animation = when (animationType) {
        SkeletonLoadingSystem.AnimationType.SHIMMER -> SkeletonLoadingSystem.ShimmerAnimation(this, config)
        SkeletonLoadingSystem.AnimationType.PULSE -> SkeletonLoadingSystem.PulseAnimation(this, config)
        else -> SkeletonLoadingSystem.ShimmerAnimation(this, config)
    }
    
    animation.start()
    setTag(R.id.skeleton_animation_tag, animation)
}

fun View.stopSkeletonLoading() {
    val animation = getTag(R.id.skeleton_animation_tag) as? SkeletonLoadingSystem.SkeletonAnimation
    animation?.stop()
    setTag(R.id.skeleton_animation_tag, null)
}