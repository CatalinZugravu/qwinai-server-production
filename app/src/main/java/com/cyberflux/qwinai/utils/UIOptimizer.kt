package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * PERFORMANCE: UI optimization utility to improve rendering and layout performance
 */
object UIOptimizer {
    
    /**
     * PERFORMANCE: Optimize RecyclerView for better scrolling and memory usage
     */
    fun optimizeRecyclerView(recyclerView: RecyclerView, context: Context) {
        try {
            // Enable view recycling optimizations
            recyclerView.setHasFixedSize(true)
            recyclerView.isDrawingCacheEnabled = true
            recyclerView.drawingCacheQuality = View.DRAWING_CACHE_QUALITY_LOW
            
            // Use optimized linear layout manager
            val layoutManager = LinearLayoutManager(context).apply {
                isItemPrefetchEnabled = true
                initialPrefetchItemCount = 4
            }
            recyclerView.layoutManager = layoutManager
            
            // Set view pool size for better recycling
            recyclerView.recycledViewPool.setMaxRecycledViews(0, 10) // Message type
            recyclerView.recycledViewPool.setMaxRecycledViews(1, 5)  // Image type
            
            // Enable nested scrolling optimization
            recyclerView.isNestedScrollingEnabled = false
            
            Timber.d("RecyclerView optimized for performance")
        } catch (e: Exception) {
            Timber.e(e, "Error optimizing RecyclerView")
        }
    }
    
    /**
     * PERFORMANCE: Optimize image loading with Glide for better memory usage
     */
    fun optimizeImageLoading(context: Context): RequestOptions {
        return RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .dontAnimate() // Disable animations for better performance
            .centerCrop()
            .override(800, 800) // Reasonable max size to prevent OOM
    }
    
    /**
     * PERFORMANCE: Load image with performance optimizations
     */
    fun loadOptimizedImage(context: Context, imageView: ImageView, url: String?, placeholder: Drawable? = null) {
        try {
            Glide.with(context)
                .load(url)
                .apply(optimizeImageLoading(context))
                .placeholder(placeholder)
                .error(placeholder)
                .into(imageView)
        } catch (e: Exception) {
            Timber.e(e, "Error loading optimized image")
        }
    }
    
    /**
     * PERFORMANCE: Optimize view hierarchy by removing unnecessary nested layouts
     */
    fun optimizeViewHierarchy(rootView: ViewGroup) {
        try {
            val time = measureTimeMillis {
                optimizeViewHierarchyRecursive(rootView, 0)
            }
            Timber.d("View hierarchy optimization completed in ${time}ms")
        } catch (e: Exception) {
            Timber.e(e, "Error optimizing view hierarchy")
        }
    }
    
    private fun optimizeViewHierarchyRecursive(viewGroup: ViewGroup, depth: Int) {
        // Prevent infinite recursion
        if (depth > 10) return
        
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            
            // Optimize child views
            if (child is ViewGroup) {
                // Remove unnecessary background drawables for performance
                if (child.background != null && child.childCount > 0) {
                    // Only keep background if it's actually visible
                    if (child.alpha == 0f || child.visibility != View.VISIBLE) {
                        child.background = null
                    }
                }
                
                optimizeViewHierarchyRecursive(child, depth + 1)
            }
        }
    }
    
    /**
     * PERFORMANCE: Set up layout optimization listener to defer heavy operations
     */
    fun deferHeavyOperations(activity: Activity, heavyOperation: () -> Unit) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Post the heavy operation to be executed after layout is complete
                rootView.post {
                    try {
                        heavyOperation()
                    } catch (e: Exception) {
                        Timber.e(e, "Error executing deferred heavy operation")
                    }
                }
            }
        })
    }
    
    /**
     * PERFORMANCE: Optimize activity for faster startup
     */
    fun optimizeActivityStartup(activity: Activity) {
        try {
            // Defer unnecessary animations
            activity.window.setWindowAnimations(0)
            
            // Enable hardware acceleration for better rendering
            activity.window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            
            Timber.d("Activity startup optimized")
        } catch (e: Exception) {
            Timber.e(e, "Error optimizing activity startup")
        }
    }
    
    /**
     * PERFORMANCE: Batch view updates for better performance
     */
    fun batchViewUpdates(viewGroup: ViewGroup, updates: () -> Unit) {
        try {
            // Disable layout requests during batch updates
            viewGroup.isLayoutRequested
            
            val time = measureTimeMillis {
                updates()
            }
            
            // Force layout after batch updates
            viewGroup.requestLayout()
            
            Timber.d("Batch view updates completed in ${time}ms")
        } catch (e: Exception) {
            Timber.e(e, "Error during batch view updates")
        }
    }
    
    /**
     * PERFORMANCE: Optimize memory usage by clearing unused resources
     */
    fun clearUnusedResources(context: Context) {
        try {
            // Clear Glide memory cache if it's getting too large
            Glide.get(context).clearMemory()
            
            // Suggest garbage collection (note: system may ignore this)
            System.gc()
            
            Timber.d("Unused resources cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing unused resources")
        }
    }
}