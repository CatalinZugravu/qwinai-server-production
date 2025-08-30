package com.cyberflux.qwinai.utils

import android.animation.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import timber.log.Timber
import kotlin.math.*
import kotlin.random.Random

/**
 * Manages beautiful file transfer animations from selection view to chat messages
 * Creates a magical "file flying" effect with curves, scaling, and particle effects
 */
class FileAnimationManager(private val activity: MainActivity) {
    
    companion object {
        private const val BASE_DURATION = 1200L
        private const val STAGGER_DELAY = 150L
        private const val OVERSHOOT_FACTOR = 1.4f
        private const val MIN_CURVE_HEIGHT = 200f
        private const val MAX_CURVE_HEIGHT = 400f
        private const val ROTATION_VARIANCE = 30f
        private const val SCALE_BOUNCE = 1.3f
    }

    private val animatingViews = mutableListOf<View>()
    private val rootView: ViewGroup by lazy { 
        activity.findViewById<ViewGroup>(android.R.id.content) 
    }

    /**
     * Data class to hold file animation info
     */
    data class FileAnimationData(
        val sourceView: View,
        val fileName: String,
        val isImage: Boolean,
        val index: Int,
        val totalFiles: Int
    )

    /**
     * Start the beautiful file transfer animation
     * @param files List of file data to animate
     * @param onAnimationComplete Callback when all animations finish
     */
    fun startFileTransferAnimation(
        files: List<FileAnimationData>,
        onAnimationComplete: () -> Unit
    ) {
        if (files.isEmpty()) {
            onAnimationComplete()
            return
        }

        Timber.d("🎬 Starting beautiful file transfer animation for ${files.size} files")
        
        // Get target position (where new message will appear in chat)
        val targetPosition = getTargetPosition()
        
        var completedAnimations = 0
        val totalAnimations = files.size

        files.forEachIndexed { index, fileData ->
            // Stagger the animations for a cascading effect
            val delay = index * STAGGER_DELAY
            
            activity.binding.root.postDelayed({
                animateFileToChat(
                    fileData = fileData,
                    targetPosition = targetPosition,
                    onComplete = {
                        completedAnimations++
                        if (completedAnimations >= totalAnimations) {
                            Timber.d("✨ All file animations completed!")
                            cleanupAnimations()
                            onAnimationComplete()
                        }
                    }
                )
            }, delay)
        }
    }

    /**
     * Animate a single file from selection to chat area
     */
    private fun animateFileToChat(
        fileData: FileAnimationData,
        targetPosition: PointF,
        onComplete: () -> Unit
    ) {
        try {
            // Create animated overlay view
            val animatedView = createAnimatedFileView(fileData)
            rootView.addView(animatedView)
            animatingViews.add(animatedView)

            // Get source position
            val sourcePosition = getViewCenterPosition(fileData.sourceView)
            
            // Position the animated view at source
            animatedView.x = sourcePosition.x - animatedView.width / 2
            animatedView.y = sourcePosition.y - animatedView.height / 2

            // Create beautiful curved path animation
            val pathAnimator = createCurvedPathAnimation(
                animatedView, 
                sourcePosition, 
                targetPosition,
                fileData
            )

            // Create scale and rotation animations
            val scaleAnimator = createScaleAnimation(animatedView, fileData)
            val rotationAnimator = createRotationAnimation(animatedView, fileData)
            val fadeAnimator = createFadeAnimation(animatedView)

            // Create particle effect animation
            val particleAnimator = createParticleEffect(animatedView, fileData)

            // Combine all animations
            val masterAnimator = AnimatorSet().apply {
                playTogether(pathAnimator, scaleAnimator, rotationAnimator, fadeAnimator)
                duration = BASE_DURATION + Random.nextLong(-200, 400)
                interpolator = AccelerateDecelerateInterpolator()
            }

            masterAnimator.doOnStart {
                Timber.d("🚀 File animation started: ${fileData.fileName}")
                
                // Add subtle vibration for feedback
                HapticManager.lightVibration(activity)
                
                // Start particle effect slightly after main animation
                particleAnimator.startDelay = 100
                particleAnimator.start()
            }

            masterAnimator.doOnEnd {
                Timber.d("🎯 File animation completed: ${fileData.fileName}")
                
                // Create impact effect at destination
                createImpactEffect(targetPosition)
                
                // Remove animated view
                rootView.removeView(animatedView)
                animatingViews.remove(animatedView)
                
                onComplete()
            }

            masterAnimator.start()

        } catch (e: Exception) {
            Timber.e(e, "Error in file animation: ${e.message}")
            onComplete()
        }
    }

    /**
     * Create an animated copy of the file view
     */
    private fun createAnimatedFileView(fileData: FileAnimationData): View {
        val originalView = fileData.sourceView
        
        // Create a bitmap of the original view
        val bitmap = createViewBitmap(originalView)
        
        // Create ImageView with the bitmap
        val animatedView = ImageView(activity).apply {
            setImageBitmap(bitmap)
            layoutParams = FrameLayout.LayoutParams(
                originalView.width,
                originalView.height
            )
            elevation = 20f // High elevation for overlay effect
            
            // Add subtle glow effect
            setBackgroundResource(R.drawable.stunning_animation_container_gradient)
            alpha = 0.95f
        }
        
        return animatedView
    }

    /**
     * Create a bitmap snapshot of a view
     */
    private fun createViewBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    /**
     * Create curved path animation using bezier curves
     */
    private fun createCurvedPathAnimation(
        view: View,
        start: PointF,
        end: PointF,
        fileData: FileAnimationData
    ): ValueAnimator {
        
        // Calculate control points for beautiful curve
        val curveHeight = MIN_CURVE_HEIGHT + Random.nextFloat() * (MAX_CURVE_HEIGHT - MIN_CURVE_HEIGHT)
        val midX = (start.x + end.x) / 2
        val midY = min(start.y, end.y) - curveHeight
        
        // Add slight horizontal variance for natural feel
        val variance = (-100..100).random()
        val controlPoint1 = PointF(midX + variance, midY)
        val controlPoint2 = PointF(midX - variance, midY + 50)

        return ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val point = getBezierPoint(start, controlPoint1, controlPoint2, end, progress)
                
                view.x = point.x - view.width / 2
                view.y = point.y - view.height / 2
            }
        }
    }

    /**
     * Calculate point on cubic bezier curve
     */
    private fun getBezierPoint(p0: PointF, p1: PointF, p2: PointF, p3: PointF, t: Float): PointF {
        val u = 1f - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t

        val x = uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x
        val y = uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y

        return PointF(x, y)
    }

    /**
     * Create scale animation with bounce effect
     */
    private fun createScaleAnimation(view: View, fileData: FileAnimationData): AnimatorSet {
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.8f).apply {
            duration = 200
        }
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.8f).apply {
            duration = 200
        }
        
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.8f, SCALE_BOUNCE).apply {
            duration = 300
            startDelay = 200
            interpolator = OvershootInterpolator(OVERSHOOT_FACTOR)
        }
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.8f, SCALE_BOUNCE).apply {
            duration = 300
            startDelay = 200
            interpolator = OvershootInterpolator(OVERSHOOT_FACTOR)
        }
        
        val scaleToNormal = ObjectAnimator.ofFloat(view, "scaleX", SCALE_BOUNCE, 1.2f).apply {
            duration = 500
            startDelay = 500
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleToNormalY = ObjectAnimator.ofFloat(view, "scaleY", SCALE_BOUNCE, 1.2f).apply {
            duration = 500
            startDelay = 500
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        return AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY, scaleUp, scaleUpY, scaleToNormal, scaleToNormalY)
        }
    }

    /**
     * Create rotation animation for dynamic movement
     */
    private fun createRotationAnimation(view: View, fileData: FileAnimationData): ObjectAnimator {
        val targetRotation = (Random.nextFloat() - 0.5f) * ROTATION_VARIANCE
        
        return ObjectAnimator.ofFloat(view, "rotation", 0f, targetRotation, 0f).apply {
            duration = BASE_DURATION
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /**
     * Create fade animation
     */
    private fun createFadeAnimation(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0.95f, 1f, 0.3f).apply {
            duration = BASE_DURATION
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /**
     * Create particle effect animation
     */
    private fun createParticleEffect(view: View, fileData: FileAnimationData): AnimatorSet {
        val particles = mutableListOf<View>()
        val animators = mutableListOf<Animator>()

        // Create multiple small particles
        repeat(8) { index ->
            val particle = createParticle(fileData.isImage)
            rootView.addView(particle)
            particles.add(particle)

            // Position at source view center
            val sourcePos = getViewCenterPosition(view)
            particle.x = sourcePos.x
            particle.y = sourcePos.y

            // Create random movement for each particle
            val angle = (index * 45f) + Random.nextFloat() * 30f
            val distance = 100f + Random.nextFloat() * 150f
            
            val targetX = sourcePos.x + cos(Math.toRadians(angle.toDouble())).toFloat() * distance
            val targetY = sourcePos.y + sin(Math.toRadians(angle.toDouble())).toFloat() * distance

            val moveAnimator = ObjectAnimator.ofFloat(
                particle, "translationX", 0f, targetX - sourcePos.x
            ).apply { duration = 800 }
            
            val moveYAnimator = ObjectAnimator.ofFloat(
                particle, "translationY", 0f, targetY - sourcePos.y
            ).apply { duration = 800 }

            val fadeAnimator = ObjectAnimator.ofFloat(particle, "alpha", 1f, 0f).apply {
                duration = 800
                startDelay = 200
            }

            val scaleAnimator = ObjectAnimator.ofFloat(particle, "scaleX", 1f, 0.1f).apply {
                duration = 800
                startDelay = 200
            }
            
            val scaleYAnimator = ObjectAnimator.ofFloat(particle, "scaleY", 1f, 0.1f).apply {
                duration = 800
                startDelay = 200
            }

            animators.add(
                AnimatorSet().apply {
                    playTogether(moveAnimator, moveYAnimator, fadeAnimator, scaleAnimator, scaleYAnimator)
                    doOnEnd {
                        rootView.removeView(particle)
                    }
                }
            )
        }

        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    /**
     * Create a small particle view
     */
    private fun createParticle(isImage: Boolean): View {
        return View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(12, 12)
            background = if (isImage) {
                ContextCompat.getDrawable(activity, R.drawable.stunning_breathing_glow)
            } else {
                ContextCompat.getDrawable(activity, R.drawable.neural_pulse_ring_outer)
            }
            elevation = 15f
        }
    }

    /**
     * Create impact effect at destination
     */
    private fun createImpactEffect(position: PointF) {
        val impactView = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(60, 60)
            background = ContextCompat.getDrawable(activity, R.drawable.stunning_breathing_glow)
            alpha = 0.8f
            elevation = 25f
            x = position.x - 30
            y = position.y - 30
        }

        rootView.addView(impactView)

        val scaleAnimator = ObjectAnimator.ofFloat(impactView, "scaleX", 0.2f, 2f).apply {
            duration = 500
            interpolator = AnticipateOvershootInterpolator(2f)
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(impactView, "scaleY", 0.2f, 2f).apply {
            duration = 500
            interpolator = AnticipateOvershootInterpolator(2f)
        }

        val fadeAnimator = ObjectAnimator.ofFloat(impactView, "alpha", 0.8f, 0f).apply {
            duration = 500
            startDelay = 100
        }

        AnimatorSet().apply {
            playTogether(scaleAnimator, scaleYAnimator, fadeAnimator)
            doOnEnd {
                rootView.removeView(impactView)
            }
            start()
        }
    }

    /**
     * Get the center position of a view relative to root
     */
    private fun getViewCenterPosition(view: View): PointF {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        
        return PointF(
            location[0] + view.width / 2f,
            location[1] + view.height / 2f
        )
    }

    /**
     * Get target position where new message will appear
     */
    private fun getTargetPosition(): PointF {
        val recyclerView = activity.binding.chatRecyclerView
        val location = IntArray(2)
        recyclerView.getLocationInWindow(location)
        
        // Target the bottom of the RecyclerView where new message appears
        return PointF(
            location[0] + recyclerView.width / 2f,
            location[1] + recyclerView.height - 100f
        )
    }

    /**
     * Clean up any remaining animated views
     */
    private fun cleanupAnimations() {
        animatingViews.forEach { view ->
            try {
                rootView.removeView(view)
            } catch (e: Exception) {
                Timber.w("Error removing animated view: ${e.message}")
            }
        }
        animatingViews.clear()
    }

    /**
     * Check if animations are currently running
     */
    fun isAnimating(): Boolean = animatingViews.isNotEmpty()

    /**
     * Stop all running animations
     */
    fun stopAllAnimations() {
        Timber.d("🛑 Stopping all file animations")
        cleanupAnimations()
    }
}