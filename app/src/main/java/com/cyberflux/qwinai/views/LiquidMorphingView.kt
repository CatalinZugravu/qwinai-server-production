package com.cyberflux.qwinai.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.*

class LiquidMorphingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects
    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // Reduced blur for better performance
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // Reduced blur for better performance
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }

    // Animation properties
    private var time = 0f
    private var volumeLevel = 0f
    private var targetVolumeLevel = 0f
    private var morphIntensity = 0f

    // Deformation waves - Reduced for performance
    private val waveCount = 4 // Reduced from 8 to 4
    private val waveAmplitudes = FloatArray(waveCount) { 0f }
    private val waveFrequencies = FloatArray(waveCount) { 1f + it * 0.5f }
    private val wavePhases = FloatArray(waveCount) { it * PI.toFloat() / 2f }

    // Ripple effects - Reduced for performance
    private val ripples = mutableListOf<Ripple>()
    private val maxRipples = 3 // Reduced from 5 to 3

    // Shape properties
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f

    // Colors
    private val primaryColor = Color.parseColor("#FFA726") // Warm amber
    private val secondaryColor = Color.parseColor("#FFB74D")
    private val glowColor = Color.parseColor("#FFD54F")
    private val shadowColor = Color.parseColor("#80000000")

    // Path for morphing shape
    private val morphPath = Path()
    private val tempPath = Path()

    // State
    private var currentState = State.IDLE

    enum class State {
        IDLE, LISTENING, SPEAKING, PROCESSING
    }

    data class Ripple(
        var radius: Float = 0f,
        var alpha: Float = 1f,
        var speed: Float = 2f,
        var maxRadius: Float = 100f
    )

    // Animation control
    private var animator: ValueAnimator? = null
    private var isAnimationRunning = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null) // Use hardware acceleration
        startAnimation()
    }

    private fun startAnimation() {
        // Stop existing animation
        stopAnimation()
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 8000 // 8 seconds cycle instead of infinite
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                if (isAnimationRunning) {
                    time += 0.016f // ~60 FPS instead of arbitrary increment
                    updateWaves()
                    updateRipples()
                    smoothVolume()
                    invalidate()
                }
            }
        }
        
        isAnimationRunning = true
        animator?.start()
    }
    
    private fun stopAnimation() {
        isAnimationRunning = false
        animator?.cancel()
        animator = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 2f * 0.7f

        // Update gradients
        updateGradients()
    }

    private fun updateGradients() {
        spherePaint.shader = RadialGradient(
            centerX - baseRadius * 0.3f,
            centerY - baseRadius * 0.3f,
            baseRadius * 1.5f,
            intArrayOf(
                secondaryColor,
                primaryColor,
                Color.parseColor("#FF8F00")
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun updateWaves() {
        for (i in waveAmplitudes.indices) {
            val targetAmplitude = when (currentState) {
                State.IDLE -> 0.02f + sin(time * 0.5f + i) * 0.01f
                State.LISTENING -> 0.1f + (volumeLevel / 100f) * 0.3f
                State.SPEAKING -> 0.15f + (volumeLevel / 100f) * 0.4f
                State.PROCESSING -> 0.08f + sin(time * 2f + i) * 0.05f
            }

            // Smooth transition
            waveAmplitudes[i] += (targetAmplitude - waveAmplitudes[i]) * 0.1f
        }
    }

    private fun updateRipples() {
            // Add new ripples based on volume spikes - Less frequent
        if (volumeLevel > 70f && ripples.size < maxRipples && Math.random() < 0.05) { // Reduced probability
            ripples.add(Ripple(
                radius = 0f,
                alpha = 0.6f, // Reduced alpha
                speed = 2f + volumeLevel / 50f,
                maxRadius = baseRadius * (1.3f + volumeLevel / 150f) // Reduced size
            ))
        }

        // Update existing ripples
        ripples.removeAll { ripple ->
            ripple.radius += ripple.speed
            ripple.alpha = 1f - (ripple.radius / ripple.maxRadius)
            ripple.radius > ripple.maxRadius
        }
    }

    private fun smoothVolume() {
        volumeLevel += (targetVolumeLevel - volumeLevel) * 0.2f
        morphIntensity = volumeLevel / 100f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw shadow
        canvas.save()
        canvas.translate(0f, baseRadius * 0.1f)
        drawMorphingSphere(canvas, shadowPaint, shadowColor, 0.9f, true)
        canvas.restore()

        // Draw glow effect
        if (currentState != State.IDLE) {
            glowPaint.alpha = (50 + volumeLevel * 2).toInt().coerceIn(0, 255)
            glowPaint.color = glowColor
            drawMorphingSphere(canvas, glowPaint, glowColor, 1.2f, false)
        }

        // Draw main sphere
        drawMorphingSphere(canvas, spherePaint, primaryColor, 1f, false)

        // Draw ripples
        drawRipples(canvas)

        // Draw inner glow
        drawInnerGlow(canvas)
    }

    private fun drawMorphingSphere(
        canvas: Canvas,
        paint: Paint,
        color: Int,
        scale: Float,
        isShadow: Boolean
    ) {
        morphPath.reset()

        val points = 32 // Reduced from 64 to 32 for better performance
        val angleStep = (2 * PI / points).toFloat()

        for (i in 0 until points) {
            val angle = i * angleStep

            // Calculate base radius with deformation
            var radius = baseRadius * scale

            if (!isShadow) {
                // Apply wave deformations
                for (j in waveAmplitudes.indices) {
                    val waveAngle = angle * waveFrequencies[j] + time * (j + 1) * 0.5f + wavePhases[j]
                    radius += sin(waveAngle) * waveAmplitudes[j] * baseRadius
                }

                // Apply volume-based deformation
                val volumeDeform = sin(angle * 3 + time * 2) * morphIntensity * baseRadius * 0.2f
                radius += volumeDeform

                // Add noise for organic feel
                val noise = (sin(angle * 7 + time * 3) * cos(angle * 5 - time * 2)) * 0.02f * baseRadius
                radius += noise
            }

            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius

            if (i == 0) {
                morphPath.moveTo(x, y)
            } else {
                // Use quadratic bezier for smooth curves
                val prevAngle = (i - 1) * angleStep
                val prevRadius = calculateRadius(prevAngle, scale, isShadow)
                val prevX = centerX + cos(prevAngle) * prevRadius
                val prevY = centerY + sin(prevAngle) * prevRadius

                val cpX = (prevX + x) / 2
                val cpY = (prevY + y) / 2

                morphPath.quadTo(prevX, prevY, cpX, cpY)
            }
        }

        morphPath.close()

        // Apply gradient based on state
        if (!isShadow && currentState == State.SPEAKING) {
            paint.shader = RadialGradient(
                centerX,
                centerY,
                baseRadius * scale,
                intArrayOf(
                    Color.parseColor("#FFE082"),
                    primaryColor,
                    Color.parseColor("#FF6F00")
                ),
                floatArrayOf(0f, 0.5f + morphIntensity * 0.3f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawPath(morphPath, paint)
    }

    private fun calculateRadius(angle: Float, scale: Float, isShadow: Boolean): Float {
        var radius = baseRadius * scale

        if (!isShadow) {
            for (j in waveAmplitudes.indices) {
                val waveAngle = angle * waveFrequencies[j] + time * (j + 1) * 0.5f + wavePhases[j]
                radius += sin(waveAngle) * waveAmplitudes[j] * baseRadius
            }

            val volumeDeform = sin(angle * 3 + time * 2) * morphIntensity * baseRadius * 0.2f
            radius += volumeDeform

            val noise = (sin(angle * 7 + time * 3) * cos(angle * 5 - time * 2)) * 0.02f * baseRadius
            radius += noise
        }

        return radius
    }

    private fun drawRipples(canvas: Canvas) {
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        ripples.forEach { ripple ->
            ripplePaint.alpha = (ripple.alpha * 255).toInt()
            ripplePaint.color = glowColor

            tempPath.reset()
            tempPath.addCircle(centerX, centerY, ripple.radius, Path.Direction.CW)

            // Apply distortion to ripple
            val matrix = Matrix()
            val scaleX = 1f + sin(time * 2) * 0.1f * morphIntensity
            val scaleY = 1f + cos(time * 2) * 0.1f * morphIntensity
            matrix.setScale(scaleX, scaleY, centerX, centerY)
            tempPath.transform(matrix)

            canvas.drawPath(tempPath, ripplePaint)
        }
    }

    private fun drawInnerGlow(canvas: Canvas) {
        if (volumeLevel > 20f) {
            val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    centerX,
                    centerY,
                    baseRadius * 0.5f,
                    intArrayOf(
                        Color.argb((volumeLevel * 2.55f).toInt(), 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                alpha = (volumeLevel * 1.5f).toInt().coerceIn(0, 255)
            }

            canvas.drawCircle(centerX, centerY, baseRadius * 0.8f, innerGlowPaint)
        }
    }

    fun setVolumeLevel(level: Float) {
        targetVolumeLevel = level.coerceIn(0f, 100f)
    }

    fun setState(state: State) {
        currentState = state

        // Add impact ripple on state change
        if (state != State.IDLE) {
            ripples.add(Ripple(
                radius = baseRadius * 0.5f,
                alpha = 1f,
                speed = 4f,
                maxRadius = baseRadius * 2f
            ))
        }
    }

    fun animateToState(state: State) {
        setState(state)

        // Create state transition animation
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                // Add any state-specific transition effects here
            }
            start()
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isAnimationRunning) {
            startAnimation()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
    
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            if (!isAnimationRunning) {
                startAnimation()
            }
        } else {
            stopAnimation()
        }
    }
}