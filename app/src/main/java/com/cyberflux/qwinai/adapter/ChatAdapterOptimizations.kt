package com.cyberflux.qwinai.adapter

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference

/**
 * Performance optimizations for ChatAdapter
 */
object ChatAdapterOptimizations {
    
    /**
     * Configure RecyclerView for optimal chat performance
     */
    fun optimizeRecyclerView(recyclerView: RecyclerView, context: Context) {
        // Enable view recycling pool optimization
        val recycledViewPool = RecyclerView.RecycledViewPool().apply {
            // Set maximum recycled views for each view type
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_MESSAGE, 10)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_MESSAGE, 10)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_IMAGE, 5)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_IMAGE, 5)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_DOCUMENT, 5)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_DOCUMENT, 5)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_CAMERA_PHOTO, 3)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_CAMERA_PHOTO, 3)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_GROUPED_FILES, 3)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_OCR_DOCUMENT, 3)
            setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_GENERATED_IMAGE, 5)
        }
        
        recyclerView.setRecycledViewPool(recycledViewPool)
        
        // Configure layout manager for better performance
        (recyclerView.layoutManager as? LinearLayoutManager)?.apply {
            isItemPrefetchEnabled = true
            initialPrefetchItemCount = 4
            recycleChildrenOnDetach = true
        }
        
        // Set has fixed size if possible (improves performance)
        recyclerView.setHasFixedSize(false) // Chat messages vary in size
        
        // Enable drawing cache
        recyclerView.isDrawingCacheEnabled = true
        recyclerView.drawingCacheQuality = android.view.View.DRAWING_CACHE_QUALITY_HIGH
        
        // Optimize scrolling
        recyclerView.isNestedScrollingEnabled = false
    }
    
    /**
     * Lifecycle-aware adapter with proper cleanup
     */
    abstract class LifecycleAwareChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var recyclerViewRef: WeakReference<RecyclerView>? = null
        private var callbacksRef: WeakReference<Any>? = null
        
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recyclerViewRef = WeakReference(recyclerView)
            optimizeRecyclerView(recyclerView, recyclerView.context)
        }
        
        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            cleanup()
        }
        
        fun cleanup() {
            recyclerViewRef?.clear()
            callbacksRef?.clear()
            recyclerViewRef = null
            callbacksRef = null
        }
    }
    
    /**
     * Optimized image loading for chat messages
     */
    object ImageLoadingOptimizer {
        
        fun loadOptimizedImage(
            context: Context,
            imageView: android.widget.ImageView,
            uri: String,
            maxWidth: Int = 800,
            maxHeight: Int = 600
        ) {
            com.bumptech.glide.Glide.with(context)
                .load(uri)
                .override(maxWidth, maxHeight) // Constrain size
                .centerCrop()
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imageView)
        }
        
        fun preloadImage(context: Context, uri: String) {
            com.bumptech.glide.Glide.with(context)
                .load(uri)
                .preload(400, 300) // Smaller preload size
        }
    }
    
    /**
     * Memory management for chat messages
     */
    object MemoryOptimizer {
        private val textCache = android.util.LruCache<String, CharSequence>(50)
        
        fun getCachedText(key: String, generator: () -> CharSequence): CharSequence {
            return textCache.get(key) ?: run {
                val text = generator()
                textCache.put(key, text)
                text
            }
        }
        
        fun clearCache() {
            textCache.evictAll()
        }
    }
}

/**
 * Extension functions for better performance
 */
fun RecyclerView.optimizeForChat() {
    ChatAdapterOptimizations.optimizeRecyclerView(this, this.context)
}

fun ChatAdapter.addPerformanceOptimizations() {
    // Add any adapter-specific optimizations here
    this.setHasStableIds(true) // Enable stable IDs if messages have unique IDs
}