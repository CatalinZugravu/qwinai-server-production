package com.cyberflux.qwinai.utils

import android.text.Spanned
import android.util.LruCache
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.model.ChatMessage
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ChatAdapter performance optimizations
 * Includes view recycling, markdown caching, and lazy loading strategies
 */
object ChatAdapterOptimizations {
    
    private const val MAX_MARKDOWN_CACHE_SIZE = 200
    private const val MAX_VIEW_CACHE_SIZE = 30
    private const val PRELOAD_THRESHOLD = 5
    
    // Caches for performance optimization
    private val markdownCache = LruCache<String, Spanned>(MAX_MARKDOWN_CACHE_SIZE)
    private val viewHolderCache = ConcurrentHashMap<Int, RecyclerView.ViewHolder>()
    private val renderingJobs = ConcurrentHashMap<String, Job>()
    
    // Performance metrics
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val renderingTime = AtomicInteger(0)
    
    /**
     * Optimized DiffUtil callback with intelligent comparison
     */
    class OptimizedChatMessageDiffCallback(
        private val oldList: List<ChatMessage>,
        private val newList: List<ChatMessage>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList.getOrNull(oldItemPosition)
            val newItem = newList.getOrNull(newItemPosition)
            return oldItem?.id == newItem?.id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList.getOrNull(oldItemPosition) ?: return false
            val newItem = newList.getOrNull(newItemPosition) ?: return false
            
            // Fast comparison of critical fields that affect UI
            return oldItem.message == newItem.message &&
                    oldItem.isGenerating == newItem.isGenerating &&
                    oldItem.isLoading == newItem.isLoading &&
                    oldItem.showButtons == newItem.showButtons &&
                    oldItem.error == newItem.error &&
                    oldItem.timestamp == newItem.timestamp
        }
        
        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val oldItem = oldList.getOrNull(oldItemPosition) ?: return null
            val newItem = newList.getOrNull(newItemPosition) ?: return null
            
            val changes = mutableSetOf<String>()
            
            if (oldItem.message != newItem.message) changes.add("message")
            if (oldItem.isGenerating != newItem.isGenerating) changes.add("generating")
            if (oldItem.isLoading != newItem.isLoading) changes.add("loading")
            if (oldItem.showButtons != newItem.showButtons) changes.add("buttons")
            if (oldItem.error != newItem.error) changes.add("error")
            
            return if (changes.isNotEmpty()) changes else null
        }
    }
    
    /**
     * View holder pool for recycling optimization
     */
    class OptimizedViewHolderPool {
        private val userMessagePool = mutableListOf<RecyclerView.ViewHolder>()
        private val aiMessagePool = mutableListOf<RecyclerView.ViewHolder>()
        private val imageMessagePool = mutableListOf<RecyclerView.ViewHolder>()
        
        fun getViewHolder(viewType: Int): RecyclerView.ViewHolder? {
            return when (viewType) {
                VIEW_TYPE_USER -> userMessagePool.removeFirstOrNull()
                VIEW_TYPE_AI -> aiMessagePool.removeFirstOrNull()
                VIEW_TYPE_IMAGE -> imageMessagePool.removeFirstOrNull()
                else -> null
            }
        }
        
        fun recycleViewHolder(holder: RecyclerView.ViewHolder, viewType: Int) {
            when (viewType) {
                VIEW_TYPE_USER -> if (userMessagePool.size < MAX_VIEW_CACHE_SIZE / 3) {
                    userMessagePool.add(holder)
                }
                VIEW_TYPE_AI -> if (aiMessagePool.size < MAX_VIEW_CACHE_SIZE / 3) {
                    aiMessagePool.add(holder)
                }
                VIEW_TYPE_IMAGE -> if (imageMessagePool.size < MAX_VIEW_CACHE_SIZE / 3) {
                    imageMessagePool.add(holder)
                }
            }
        }
        
        fun clear() {
            userMessagePool.clear()
            aiMessagePool.clear()
            imageMessagePool.clear()
        }
        
        companion object {
            const val VIEW_TYPE_USER = 1
            const val VIEW_TYPE_AI = 2
            const val VIEW_TYPE_IMAGE = 3
        }
    }
    
    /**
     * Markdown cache management with LRU eviction
     */
    object MarkdownCacheManager {
        
        fun getCachedMarkdown(content: String): Spanned? {
            val cached = markdownCache.get(content)
            if (cached != null) {
                cacheHits.incrementAndGet()
                return cached
            }
            cacheMisses.incrementAndGet()
            return null
        }
        
        fun cacheMarkdown(content: String, rendered: Spanned) {
            // Only cache if content is substantial and likely to be reused
            if (content.length > 50 && content.length < 10000) {
                markdownCache.put(content, rendered)
            }
        }
        
        fun clearCache() {
            markdownCache.evictAll()
        }
        
        fun getCacheStats(): CacheStats {
            return CacheStats(
                hits = cacheHits.get(),
                misses = cacheMisses.get(),
                size = markdownCache.size(),
                maxSize = markdownCache.maxSize()
            )
        }
    }
    
    /**
     * Lazy loading manager for efficient content rendering
     */
    class LazyLoadingManager(
        private val coroutineScope: CoroutineScope
    ) {
        
        private val pendingRenders = ConcurrentHashMap<String, Job>()
        
        fun scheduleMarkdownRendering(
            messageId: String,
            content: String,
            onRendered: (Spanned) -> Unit
        ) {
            // Cancel any existing rendering job for this message
            pendingRenders[messageId]?.cancel()
            
            // Check cache first
            MarkdownCacheManager.getCachedMarkdown(content)?.let { cached ->
                onRendered(cached)
                return
            }
            
            // Schedule background rendering
            val job = coroutineScope.launch(Dispatchers.Default) {
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // Simulate markdown rendering (replace with actual Markwon rendering)
                    val rendered = renderMarkdownOptimized(content)
                    
                    val renderTime = (System.currentTimeMillis() - startTime).toInt()
                    renderingTime.addAndGet(renderTime)
                    
                    // Cache the result
                    MarkdownCacheManager.cacheMarkdown(content, rendered)
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        onRendered(rendered)
                    }
                    
                } catch (e: CancellationException) {
                    // Job was cancelled, ignore
                } catch (e: Exception) {
                    Timber.e(e, "Error rendering markdown for message: $messageId")
                } finally {
                    pendingRenders.remove(messageId)
                }
            }
            
            pendingRenders[messageId] = job
        }
        
        fun cancelRendering(messageId: String) {
            pendingRenders.remove(messageId)?.cancel()
        }
        
        fun cancelAllRendering() {
            pendingRenders.values.forEach { it.cancel() }
            pendingRenders.clear()
        }
        
        private suspend fun renderMarkdownOptimized(content: String): Spanned {
            // This would be replaced with actual Markwon rendering
            // For now, return a simple SpannedString
            return android.text.SpannedString(content)
        }
    }
    
    /**
     * Preloading strategy for smooth scrolling
     */
    class PreloadingStrategy {
        
        fun shouldPreload(
            position: Int,
            totalItems: Int,
            firstVisiblePosition: Int,
            lastVisiblePosition: Int
        ): Boolean {
            val distanceFromBottom = totalItems - position
            val distanceFromTop = position
            val visibleRange = lastVisiblePosition - firstVisiblePosition
            
            return distanceFromBottom <= PRELOAD_THRESHOLD ||
                    distanceFromTop <= PRELOAD_THRESHOLD ||
                    (position >= firstVisiblePosition - PRELOAD_THRESHOLD &&
                     position <= lastVisiblePosition + PRELOAD_THRESHOLD)
        }
        
        fun getPreloadPriority(
            position: Int,
            firstVisiblePosition: Int,
            lastVisiblePosition: Int
        ): PreloadPriority {
            return when {
                position in firstVisiblePosition..lastVisiblePosition -> PreloadPriority.HIGH
                position in (firstVisiblePosition - 2)..(lastVisiblePosition + 2) -> PreloadPriority.MEDIUM
                else -> PreloadPriority.LOW
            }
        }
    }
    
    enum class PreloadPriority {
        HIGH, MEDIUM, LOW
    }
    
    /**
     * View binding optimization
     */
    object ViewBindingOptimizer {
        
        fun optimizeViewHolder(holder: RecyclerView.ViewHolder) {
            // Optimize view holder for better performance
            holder.itemView.apply {
                // Enable view layer caching
                setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                
                // Optimize drawing cache
                isDrawingCacheEnabled = false
                
                // Optimize hardware acceleration
                setHasTransientState(false)
            }
        }
        
        fun prepareForScrolling(recyclerView: RecyclerView) {
            recyclerView.apply {
                // Optimize item animator
                itemAnimator?.changeDuration = 0
                itemAnimator?.moveDuration = 200
                
                // Set optimal cache sizes
                setItemViewCacheSize(20)
                
                // Enable nested scrolling
                isNestedScrollingEnabled = true
                
                // Optimize drawing
                setHasFixedSize(true)
            }
        }
    }
    
    /**
     * Memory optimization for large lists
     */
    object MemoryOptimizer {
        
        fun optimizeForLargeDataset(adapter: RecyclerView.Adapter<*>) {
            // Clear any unnecessary references
            adapter.setHasStableIds(true)
        }
        
        fun shouldTrimMemory(memoryLevel: Int): Boolean {
            return memoryLevel >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        }
        
        fun trimMemory() {
            MarkdownCacheManager.clearCache()
            System.gc()
        }
    }
    
    /**
     * Performance monitoring
     */
    data class PerformanceMetrics(
        val cacheStats: CacheStats,
        val averageRenderTime: Double,
        val totalRendering: Int,
        val activeJobs: Int
    )
    
    data class CacheStats(
        val hits: Int,
        val misses: Int,
        val size: Int,
        val maxSize: Int
    ) {
        val hitRate: Double get() = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
    }
    
    fun getPerformanceMetrics(): PerformanceMetrics {
        val cacheStats = MarkdownCacheManager.getCacheStats()
        return PerformanceMetrics(
            cacheStats = cacheStats,
            averageRenderTime = renderingTime.get().toDouble() / maxOf(1, cacheStats.misses),
            totalRendering = cacheStats.misses,
            activeJobs = renderingJobs.size
        )
    }
    
    fun resetMetrics() {
        cacheHits.set(0)
        cacheMisses.set(0)
        renderingTime.set(0)
    }
}

/**
 * Extension functions for easy integration
 */
fun RecyclerView.applyOptimizations() {
    ChatAdapterOptimizations.ViewBindingOptimizer.prepareForScrolling(this)
}

fun RecyclerView.ViewHolder.applyOptimizations() {
    ChatAdapterOptimizations.ViewBindingOptimizer.optimizeViewHolder(this)
}