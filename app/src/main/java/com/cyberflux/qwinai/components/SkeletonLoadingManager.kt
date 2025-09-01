package com.cyberflux.qwinai.components

import android.animation.ValueAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import timber.log.Timber

/**
 * Enhanced skeleton loading manager with shimmer animations
 * Provides beautiful loading states for better user experience
 */
class SkeletonLoadingManager(private val context: Context) {
    
    private val skeletonViews = mutableListOf<View>()
    private var shimmerAnimator: ValueAnimator? = null
    
    /**
     * Create skeleton loading view for messages
     */
    fun createMessageSkeleton(parent: ViewGroup): View {
        val skeletonView = LayoutInflater.from(context)
            .inflate(R.layout.skeleton_message_loading, parent, false)
        
        skeletonViews.add(skeletonView)
        startShimmerAnimation(skeletonView)
        
        return skeletonView
    }
    
    /**
     * Create skeleton loading view for conversations
     */
    fun createConversationSkeleton(parent: ViewGroup): View {
        val skeletonView = LayoutInflater.from(context)
            .inflate(R.layout.skeleton_conversation_loading, parent, false)
        
        skeletonViews.add(skeletonView)
        startShimmerAnimation(skeletonView)
        
        return skeletonView
    }
    
    /**
     * Show skeleton loading in RecyclerView
     */
    fun showSkeletonInRecyclerView(
        recyclerView: RecyclerView,
        itemCount: Int = 3,
        skeletonType: SkeletonType = SkeletonType.MESSAGE
    ) {
        try {
            val adapter = SkeletonAdapter(context, itemCount, skeletonType)
            recyclerView.adapter = adapter
            
            Timber.d("Showing skeleton loading with $itemCount items")
        } catch (e: Exception) {
            Timber.e(e, "Error showing skeleton loading: ${e.message}")
        }
    }
    
    /**
     * Hide skeleton loading and restore original adapter
     */
    fun hideSkeletonInRecyclerView(recyclerView: RecyclerView, originalAdapter: RecyclerView.Adapter<*>) {
        try {
            recyclerView.adapter = originalAdapter
            clearSkeletonViews()
            
            Timber.d("Hidden skeleton loading, restored original adapter")
        } catch (e: Exception) {
            Timber.e(e, "Error hiding skeleton loading: ${e.message}")
        }
    }
    
    /**
     * Start shimmer animation on skeleton views
     */
    private fun startShimmerAnimation(skeletonView: View) {
        val shimmerViews = listOf(
            skeletonView.findViewById<View>(R.id.skeletonAvatar),
            skeletonView.findViewById<View>(R.id.skeletonLine1),
            skeletonView.findViewById<View>(R.id.skeletonLine2),
            skeletonView.findViewById<View>(R.id.skeletonLine3)
        ).filterNotNull()
        
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val alpha = 0.3f + (progress * 0.4f) // Animate between 0.3 and 0.7 alpha
                
                shimmerViews.forEach { view ->
                    view.alpha = alpha
                }
            }
            
            start()
        }
    }
    
    /**
     * Clear all skeleton views and animations
     */
    fun clearSkeletonViews() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        skeletonViews.clear()
    }
    
    /**
     * Skeleton adapter for RecyclerView
     */
    private class SkeletonAdapter(
        private val context: Context,
        private val itemCount: Int,
        private val skeletonType: SkeletonType
    ) : RecyclerView.Adapter<SkeletonViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
            val layoutRes = when (skeletonType) {
                SkeletonType.MESSAGE -> R.layout.skeleton_message_loading
                SkeletonType.CONVERSATION -> R.layout.skeleton_conversation_loading
            }
            
            val view = LayoutInflater.from(context).inflate(layoutRes, parent, false)
            return SkeletonViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
            holder.startShimmer()
        }
        
        override fun getItemCount(): Int = itemCount
    }
    
    /**
     * ViewHolder for skeleton items
     */
    private class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        fun startShimmer() {
            val shimmerViews = mutableListOf<View>()
            
            // Find all skeleton views recursively
            findSkeletonViews(itemView as ViewGroup, shimmerViews)
            
            // Start shimmer animation
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    val alpha = 0.3f + (progress * 0.4f)
                    
                    shimmerViews.forEach { view ->
                        view.alpha = alpha
                    }
                }
                
                start()
            }
        }
        
        private fun findSkeletonViews(parent: ViewGroup, shimmerViews: MutableList<View>) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child.id != View.NO_ID && child.id.toString().contains("skeleton")) {
                    shimmerViews.add(child)
                }
                if (child is ViewGroup) {
                    findSkeletonViews(child, shimmerViews)
                }
            }
        }
    }
    
    enum class SkeletonType {
        MESSAGE,
        CONVERSATION
    }
    
    companion object {
        /**
         * Show skeleton loading with fade in animation
         */
        fun showWithFadeIn(view: View, duration: Long = 300) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .setDuration(duration)
                .start()
        }
        
        /**
         * Hide skeleton loading with fade out animation
         */
        fun hideWithFadeOut(view: View, duration: Long = 300, onComplete: (() -> Unit)? = null) {
            view.animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    view.visibility = View.GONE
                    onComplete?.invoke()
                }
                .start()
        }
    }
}