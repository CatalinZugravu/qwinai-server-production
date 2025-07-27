package com.cyberflux.qwinai.network

import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * ULTRA-PERFORMANCE streaming optimizations
 * 
 * Key improvements:
 * - Zero-allocation StringBuilder operations
 * - Predictive buffering and batching
 * - Context-aware throttling
 * - Memory pool management
 */
object StreamingOptimizations {
    
    // OPTIMIZATION: Reusable StringBuilder pool for zero-allocation streaming
    private val stringBuilderPool = ArrayDeque<StringBuilder>(10)
    private val poolLock = Any()
    
    // OPTIMIZATION: Predictive content analysis for smart processing
    private val contentAnalyzer = ContentAnalyzer()
    
    // OPTIMIZATION: High-performance batch processor
    private val batchProcessor = BatchProcessor()
    
    /**
     * OPTIMIZATION: Zero-allocation StringBuilder management
     */
    fun getPooledStringBuilder(initialCapacity: Int = 1024): StringBuilder {
        return synchronized(poolLock) {
            stringBuilderPool.removeFirstOrNull()?.apply { 
                clear()
                ensureCapacity(initialCapacity)
            } ?: StringBuilder(initialCapacity)
        }
    }
    
    fun returnStringBuilder(sb: StringBuilder) {
        if (sb.capacity() <= 4096) { // Don't pool oversized builders
            synchronized(poolLock) {
                if (stringBuilderPool.size < 10) {
                    stringBuilderPool.addLast(sb)
                }
            }
        }
    }
    
    /**
     * OPTIMIZATION: Smart content analysis for predictive processing
     */
    class ContentAnalyzer {
        private val formatDetectionPatterns = mapOf(
            "code_block" to "```",
            "bold" to "**",
            "italic" to "*",
            "link" to "[",
            "math" to "$",
            "list" to "- "
        )
        
        // Cache for content analysis results
        internal val analysisCache = object : LinkedHashMap<Int, ContentMetrics>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ContentMetrics>?): Boolean {
                return size > 100
            }
        }
        
        data class ContentMetrics(
            val hasFormatting: Boolean,
            val complexity: Int,
            val estimatedProcessingTime: Long,
            val priority: ProcessingPriority
        )
        
        enum class ProcessingPriority { LOW, NORMAL, HIGH, CRITICAL }
        
        fun analyzeContent(content: String): ContentMetrics {
            val contentHash = content.hashCode()
            
            return analysisCache[contentHash] ?: run {
                val hasFormatting = formatDetectionPatterns.values.any { content.contains(it) }
                val complexity = calculateComplexity(content)
                val estimatedTime = estimateProcessingTime(content.length, complexity, hasFormatting)
                val priority = determinePriority(content, hasFormatting, complexity)
                
                val metrics = ContentMetrics(hasFormatting, complexity, estimatedTime, priority)
                analysisCache[contentHash] = metrics
                metrics
            }
        }
        
        private fun calculateComplexity(content: String): Int {
            var complexity = 0
            complexity += formatDetectionPatterns.values.sumOf { pattern ->
                content.split(pattern).size - 1
            }
            complexity += content.count { it == '\n' } / 10 // Line complexity
            complexity += if (content.length > 1000) content.length / 1000 else 0
            return complexity
        }
        
        private fun estimateProcessingTime(length: Int, complexity: Int, hasFormatting: Boolean): Long {
            var baseTime = length / 100L // ~1ms per 100 chars
            if (hasFormatting) baseTime *= 2
            baseTime += complexity * 2L
            return baseTime.coerceAtLeast(1L)
        }
        
        private fun determinePriority(content: String, hasFormatting: Boolean, complexity: Int): ProcessingPriority {
            return when {
                content.contains("```") || complexity > 50 -> ProcessingPriority.CRITICAL
                hasFormatting || complexity > 20 -> ProcessingPriority.HIGH
                content.length > 500 -> ProcessingPriority.NORMAL
                else -> ProcessingPriority.LOW
            }
        }
    }
    
    /**
     * OPTIMIZATION: Advanced batch processing for smooth streaming
     */
    class BatchProcessor {
        private val batchQueue = ArrayDeque<BatchItem>(20)
        private val queueLock = Any()
        private var processingJob: Job? = null
        private val lastProcessTime = AtomicLong(0)
        
        data class BatchItem(
            val content: String,
            val priority: ContentAnalyzer.ProcessingPriority,
            val timestamp: Long,
            val callback: (String) -> Unit
        )
        
        fun submitForBatching(
            content: String, 
            priority: ContentAnalyzer.ProcessingPriority,
            callback: (String) -> Unit
        ) {
            val item = BatchItem(content, priority, System.currentTimeMillis(), callback)
            
            synchronized(queueLock) {
                // Remove outdated items with same callback (streaming updates)
                batchQueue.removeAll { it.callback === callback }
                batchQueue.addLast(item)
                
                // Limit queue size
                while (batchQueue.size > 15) {
                    batchQueue.removeFirst()
                }
            }
            
            scheduleProcessing(priority)
        }
        
        private fun scheduleProcessing(priority: ContentAnalyzer.ProcessingPriority) {
            processingJob?.cancel()
            
            val delay = when (priority) {
                ContentAnalyzer.ProcessingPriority.CRITICAL -> 1L
                ContentAnalyzer.ProcessingPriority.HIGH -> 8L
                ContentAnalyzer.ProcessingPriority.NORMAL -> 16L
                ContentAnalyzer.ProcessingPriority.LOW -> 33L
            }
            
            processingJob = CoroutineScope(Dispatchers.Default).launch {
                delay(delay)
                processBatch()
            }
        }
        
        private suspend fun processBatch() {
            val itemsToProcess = synchronized(queueLock) {
                val items = batchQueue.toList()
                batchQueue.clear()
                items
            }
            
            if (itemsToProcess.isEmpty()) return
            
            // Process in priority order
            val sortedItems = itemsToProcess.sortedByDescending { it.priority.ordinal }
            
            for (item in sortedItems) {
                try {
                    withContext(Dispatchers.Main) {
                        item.callback(item.content)
                    }
                    // Small delay between items to prevent overwhelming the UI
                    delay(1L)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing batch item: ${e.message}")
                }
            }
            
            lastProcessTime.set(System.currentTimeMillis())
        }
        
        fun getQueueSize(): Int = synchronized(queueLock) { batchQueue.size }
        
        fun clearQueue() {
            synchronized(queueLock) {
                batchQueue.clear()
            }
            processingJob?.cancel()
        }
    }
    
    /**
     * OPTIMIZATION: Context-aware coroutine dispatcher for streaming
     */
    class StreamingDispatcher : CoroutineDispatcher() {
        private val defaultDispatcher = Dispatchers.Default
        private val ioDispatcher = Dispatchers.IO
        private val unconfinedDispatcher = Dispatchers.Unconfined
        
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            // Route based on context and content characteristics
            val dispatcher = when {
                context[CoroutineName]?.name?.contains("Markdown") == true -> defaultDispatcher
                context[CoroutineName]?.name?.contains("IO") == true -> ioDispatcher
                context[CoroutineName]?.name?.contains("UI") == true -> unconfinedDispatcher
                else -> defaultDispatcher
            }
            
            dispatcher.dispatch(context, block)
        }
        
        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            return true // Always allow optimization routing
        }
    }
    
    // OPTIMIZATION: Shared high-performance dispatcher instance
    val streamingDispatcher = StreamingDispatcher()
    
    /**
     * OPTIMIZATION: Smart throttling with content awareness
     */
    fun shouldThrottleUpdate(
        content: String, 
        lastUpdate: Long, 
        isStreaming: Boolean
    ): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastUpdate
        
        if (!isStreaming) return false
        
        val metrics = contentAnalyzer.analyzeContent(content)
        
        return when (metrics.priority) {
            ContentAnalyzer.ProcessingPriority.CRITICAL -> false // Never throttle critical content
            ContentAnalyzer.ProcessingPriority.HIGH -> timeSinceLast < 8L
            ContentAnalyzer.ProcessingPriority.NORMAL -> timeSinceLast < 16L
            ContentAnalyzer.ProcessingPriority.LOW -> timeSinceLast < 33L
        }
    }
    
    /**
     * OPTIMIZATION: Memory-efficient content diffing
     */
    fun calculateContentDiff(oldContent: String, newContent: String): ContentDiff {
        if (oldContent == newContent) return ContentDiff.IDENTICAL
        
        val oldLength = oldContent.length
        val newLength = newContent.length
        
        return when {
            newLength == 0 -> ContentDiff.CLEARED
            oldLength == 0 -> ContentDiff.INITIAL
            newLength < oldLength -> ContentDiff.TRUNCATED
            newContent.startsWith(oldContent) -> ContentDiff.APPENDED
            else -> ContentDiff.MODIFIED
        }
    }
    
    enum class ContentDiff {
        IDENTICAL, INITIAL, APPENDED, MODIFIED, TRUNCATED, CLEARED
    }
    
    /**
     * Get performance statistics
     */
    fun getOptimizationStats(): String {
        val poolSize = synchronized(poolLock) { stringBuilderPool.size }
        val cacheSize = contentAnalyzer.analysisCache.size
        val queueSize = batchProcessor.getQueueSize()
        
        return "Pool: $poolSize, Cache: $cacheSize, Queue: $queueSize"
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        synchronized(poolLock) {
            stringBuilderPool.clear()
        }
        contentAnalyzer.analysisCache.clear()
        batchProcessor.clearQueue()
    }
}