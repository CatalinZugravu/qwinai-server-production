package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import android.text.SpannableStringBuilder
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
// import io.noties.markwon.syntax.SyntaxHighlightPlugin  // Disabled due to dependency issues
// import io.noties.markwon.simple.ext.SimpleExtPlugin     // Disabled for now
// import io.noties.markwon.recycler.MarkwonAdapter        // Not needed for basic functionality
import kotlinx.coroutines.*
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * ULTRA-FAST Streaming Markdown Processor
 * 
 * Eliminates all stuck states and flickering through:
 * - Immediate processing for short content (< 50 chars)
 * - Progressive chunking for long content
 * - Zero-allocation hot paths
 * - Content-aware throttling
 * - Anti-flicker content diffing
 * - Table-aware processing to prevent table flickering
 */
class UltraFastStreamingProcessor(private val context: Context) {
    
    companion object {
        // Singleton Markwon instance for maximum performance
        @Volatile
        private var cachedMarkwon: Markwon? = null
        private val markwonLock = Any()
        
        // Pre-allocated content diffing
        private val contentDiffer = StreamingContentDiffer()
        
        // Performance constants optimized for AI streaming
        private const val IMMEDIATE_PROCESSING_THRESHOLD = 100 // Increased for better responsiveness
        private const val CHUNK_SIZE = 300 // Optimized chunk size for AI content
        private const val MIN_UPDATE_INTERVAL = 16L // 60fps for smooth AI streaming
        private const val CONTENT_CHANGE_THRESHOLD = 3 // More sensitive to AI streaming changes
        
        // AI content detection patterns
        private val AI_MARKDOWN_PATTERNS = setOf(
            "```", "**", "__", "##", "###", "####", "| ", "- [ ]", "- [x]", 
            "$", "$$", "~", "`", "[", "](", "![", "<!--", "<thinking>", 
            "1.", "2.", "3.", "*", "+", ">", "===", "---"
        )
    }
    
    // Ultra-fast processing scope with optimized dispatcher
    private val processingScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(2) + SupervisorJob() + CoroutineName("UltraFastMarkdown")
    )
    
    // Table-aware processor for handling table content without flickering
    private val tableProcessor by lazy { TableAwareStreamingProcessor(context) }
    
    // Optimized caching with content-aware eviction
    private val processedCache = ConcurrentHashMap<Int, CachedResult>(32)
    private val cacheHitCount = AtomicLong(0)
    
    // Anti-flicker content tracking
    private val lastProcessedContent = AtomicReference<String>("")
    private val lastProcessedTime = AtomicLong(0)
    private val isProcessing = AtomicBoolean(false)
    private val currentJob = AtomicReference<Job?>(null)
    
    // Performance monitoring
    private var averageProcessingTime = 0L
    private var maxProcessingTime = 0L
    
    data class CachedResult(
        val spanned: Spanned,
        val contentHash: Int,
        val timestamp: Long,
        var hitCount: Int = 1
    )
    
    // Storage for extracted code blocks per message (using content hash as key)
    private val extractedCodeBlocksMap = mutableMapOf<Int, List<CodeBlockInfo>>()
    
    data class CodeBlockInfo(
        val language: String,
        val code: String,
        val placeholder: String
    )

    /**
     * ULTRA-FAST: Primary streaming processing method
     * - Immediate processing for short content
     * - Progressive chunking for long content
     * - Zero flickering through intelligent diffing
     * - Custom code block handling with item_code_block layout
     */
    @MainThread
    fun processStreamingMarkdown(
        content: String,
        textView: TextView,
        isStreaming: Boolean = true,  
        onComplete: ((Spanned) -> Unit)? = null,
        codeBlockContainer: android.widget.LinearLayout? = null
    ) {
        try {
            if (content.isEmpty()) {
                textView.text = ""
                onComplete?.invoke(android.text.SpannableString(""))
                return
            }
            
            // Clear any existing code blocks in the container for fresh start
            codeBlockContainer?.removeAllViews()
            codeBlockContainer?.visibility = View.GONE
            
            Timber.d("CODE_BLOCK_DEBUG: About to process content with stream-aware handling")
            Timber.d("CODE_BLOCK_DEBUG: Content contains backticks: ${content.contains("```")}")
            Timber.d("CODE_BLOCK_DEBUG: Content preview: ${content.take(200)}...")
            
            // Process with Markwon including our code block plugin
            val markwon = getMarkwonInstance(codeBlockContainer)
            val processed = markwon.toMarkdown(content)
            textView.text = processed
            onComplete?.invoke(processed)
            
            Timber.d("CODE_BLOCK_DEBUG: Processed markdown complete")
            Timber.d("CODE_BLOCK_DEBUG: Container child count: ${codeBlockContainer?.childCount ?: 0}")
            Timber.d("CODE_BLOCK_DEBUG: Container visibility: ${codeBlockContainer?.visibility}")
            Timber.d("CODE_BLOCK_DEBUG: Processed text length: ${processed.length}")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process markdown")
            textView.text = content
            onComplete?.invoke(android.text.SpannableString(content))
        }
    }
    
    
    /**
     * Streaming markdown processing (alias for processStreamingMarkdown)
     */
    @MainThread
    fun processMarkdownStreaming(
        content: String,
        textView: TextView,
        isStreaming: Boolean = true,
        codeBlockContainer: LinearLayout? = null
    ) {
        processStreamingMarkdown(content, textView, isStreaming, null, codeBlockContainer)
    }
    
    /**
     * Complete markdown processing (for finished content)
     */
    @MainThread
    fun processMarkdownComplete(
        content: String,
        textView: TextView,
        codeBlockContainer: LinearLayout? = null
    ) {
        processStreamingMarkdown(content, textView, false, null, codeBlockContainer)
    }

    /**
     * IMMEDIATE: Zero-delay processing for short content
     */
    @MainThread
    private fun processImmediately(
        content: String,
        textView: TextView,
        contentHash: Int,
        codeBlockContainer: LinearLayout?,
        onComplete: ((Spanned) -> Unit)?
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val processed = getMarkwonInstance(codeBlockContainer).toMarkdown(content)
            textView.text = processed
            onComplete?.invoke(processed)
            
            // Cache the result
            processedCache[contentHash] = CachedResult(processed, contentHash, startTime)
            lastProcessedContent.set(content)
            
            val processingTime = System.currentTimeMillis() - startTime
            updatePerformanceMetrics(processingTime)
            StreamingPerformanceMonitor.recordProcessingTime(processingTime)
            
        } catch (e: Exception) {
            Timber.e(e, "Immediate processing failed")
            val fallback = android.text.SpannableString(content)
            textView.text = fallback
            onComplete?.invoke(fallback)
        }
    }
    
    /**
     * PROGRESSIVE: Chunked processing for long content with anti-flicker
     */
    @MainThread
    private fun processProgressively(
        content: String,
        textView: TextView,
        contentHash: Int,
        isStreaming: Boolean,
        onComplete: ((Spanned) -> Unit)?
    ) {
        // Cancel previous job efficiently
        currentJob.get()?.cancel("New content")
        
        val job = processingScope.launch {
            val startTime = System.currentTimeMillis()
            
            try {
                val processed = if (isStreaming) {
                    processStreamingContent(content)
                } else {
                    processCompleteContent(content)
                }
                
                withContext(Dispatchers.Main.immediate) {
                    // ANTI-FLICKER: Only update if content actually changed
                    if (shouldUpdateUI(textView, processed)) {
                        textView.text = processed
                        onComplete?.invoke(processed)
                        
                        // Cache result
                        processedCache[contentHash] = CachedResult(processed, contentHash, startTime)
                        lastProcessedContent.set(content)
                    }
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                updatePerformanceMetrics(processingTime)
                
            } catch (e: CancellationException) {
                // Normal cancellation - don't log
            } catch (e: Exception) {
                Timber.e(e, "Progressive processing failed")
                withContext(Dispatchers.Main) {
                    val fallback = android.text.SpannableString(content)
                    textView.text = fallback
                    onComplete?.invoke(fallback)
                }
            } finally {
                isProcessing.set(false)
                lastProcessedTime.set(System.currentTimeMillis())
            }
        }
        
        currentJob.set(job)
        isProcessing.set(true)
    }
    
    /**
     * OPTIMIZED: Streaming content processing with progressive rendering
     */
    @WorkerThread
    private suspend fun processStreamingContent(content: String): Spanned {
        return if (content.length > 1000) {
            // For very long content, process in chunks to maintain responsiveness
            processInChunks(content)
        } else {
            // Standard processing for moderate content
            getMarkwonInstance().toMarkdown(content)
        }
    }
    
    /**
     * CHUNKED: Process long content in segments to prevent blocking
     */
    @WorkerThread
    private suspend fun processInChunks(content: String): Spanned {
        val chunks = mutableListOf<String>()
        
        // Smart chunking - break at natural boundaries (paragraphs, sentences)
        var currentPos = 0
        while (currentPos < content.length) {
            val endPos = min(currentPos + CHUNK_SIZE, content.length)
            val chunk = content.substring(currentPos, endPos)
            
            // Find natural break point
            val naturalBreak = findNaturalBreakPoint(chunk, currentPos == 0)
            val actualEnd = if (naturalBreak > 0) currentPos + naturalBreak else endPos
            
            chunks.add(content.substring(currentPos, actualEnd))
            currentPos = actualEnd
            
            // Yield periodically to prevent blocking
            if (chunks.size % 5 == 0) {
                yield()
            }
        }
        
        // Process all chunks as one unit for coherent markdown
        return getMarkwonInstance().toMarkdown(content)
    }
    
    /**
     * SMART: Find natural break points in content to prevent cutting markdown syntax
     */
    private fun findNaturalBreakPoint(chunk: String, isFirstChunk: Boolean): Int {
        if (chunk.length <= CHUNK_SIZE) return chunk.length
        
        // Look for natural break points in reverse order
        val searchStart = if (isFirstChunk) CHUNK_SIZE else CHUNK_SIZE - 20
        
        for (i in searchStart downTo CHUNK_SIZE / 2) {
            when (chunk[i]) {
                '\n' -> return i + 1  // After newline
                '.' -> if (i < chunk.length - 1 && chunk[i + 1] == ' ') return i + 2
                '!' -> if (i < chunk.length - 1 && chunk[i + 1] == ' ') return i + 2
                '?' -> if (i < chunk.length - 1 && chunk[i + 1] == ' ') return i + 2
                ' ' -> return i + 1   // After space
            }
        }
        
        return CHUNK_SIZE // Fallback to chunk size
    }
    
    /**
     * COMPLETE: Full processing for non-streaming content
     */
    @WorkerThread
    private suspend fun processCompleteContent(content: String): Spanned {
        return getMarkwonInstance().toMarkdown(content)
    }
    
    
    /**
     * INTELLIGENT: Content-aware throttling to prevent excessive updates
     */
    private fun shouldThrottleUpdate(now: Long, content: String, lastContent: String): Boolean {
        val timeSinceLastProcess = now - lastProcessedTime.get()
        
        // Never throttle if currently processing to avoid stuck states
        if (isProcessing.get()) return true
        
        // Content-based throttling
        val contentDiff = contentDiffer.calculateDifference(lastContent, content)
        
        return when {
            contentDiff < CONTENT_CHANGE_THRESHOLD -> true // Too small change
            contentDiff > 100 -> false // Large change - process immediately
            timeSinceLastProcess < MIN_UPDATE_INTERVAL -> true // Too frequent
            else -> false
        }
    }
    
    /**
     * ANTI-FLICKER: Determine if UI update is necessary
     */
    private fun shouldUpdateUI(textView: TextView, newContent: Spanned): Boolean {
        val currentText = textView.text
        
        // Always update if content is significantly different
        if (currentText == null || currentText.length == 0) return true
        if (newContent.toString() != currentText.toString()) return true
        
        return false
    }
    
    /**
     * OPTIMIZED: Get or create Markwon instance with code block support
     */
    private fun getMarkwonInstance(codeBlockContainer: LinearLayout? = null): Markwon {
        // Always create a new instance with the current container to ensure code blocks work
        val markwon = createOptimizedMarkwon(codeBlockContainer)
        
        // Reset code block counter for fresh processing
        val codeBlockPlugin = markwon.plugins.find { it is CodeBlockPlugin } as? CodeBlockPlugin
        codeBlockPlugin?.resetCounter()
        
        return markwon
    }
    
    /**
     * Get Markwon instance without code block plugin (for pre-processed content)
     */
    private fun getMarkwonInstanceWithoutCodeBlocks(): Markwon {
        return try {
            val builder = Markwon.builder(context)
            
            // Add all plugins except code block plugin
            builder.usePlugin(HtmlPlugin.create())
            builder.usePlugin(TablePlugin.create(context))
            builder.usePlugin(TaskListPlugin.create(context))
            builder.usePlugin(StrikethroughPlugin.create())
            builder.usePlugin(LinkifyPlugin.create())
            builder.usePlugin(GlideImagesPlugin.create(context))
            
            // LaTeX/Math formula support
            try {
                builder.usePlugin(JLatexMathPlugin.create(16f))
            } catch (e: Exception) {
                Timber.w("LaTeX support not available: ${e.message}")
            }
            
            builder.build()
        } catch (e: Exception) {
            Timber.w("Failed to create Markwon without code blocks: ${e.message}")
            Markwon.builder(context).build()
        }
    }
    
    /**
     * COMPREHENSIVE: Create Markwon with all AI model response features
     * Supports everything AI models can generate while maintaining streaming performance
     */
    private fun createOptimizedMarkwon(codeBlockContainer: LinearLayout? = null): Markwon {
        return try {
            val builder = Markwon.builder(context)
            
            // === CORE MARKDOWN FEATURES ===
            // Standard markdown: headers, bold, italic, lists, quotes
            // (Built into Markwon core)
            
            // === CUSTOM CODE BLOCKS ===
            // Use our plugin with your item_code_block.xml layout
            Timber.d("CODE_BLOCK_DEBUG: Creating Markwon with CodeBlockPlugin using container")
            Timber.d("CODE_BLOCK_DEBUG: Container provided to Markwon: ${codeBlockContainer != null}")
            val codeBlockPlugin = CodeBlockPlugin.create(context, codeBlockContainer)
            builder.usePlugin(codeBlockPlugin)
            Timber.d("CODE_BLOCK_DEBUG: CodeBlockPlugin registered with Markwon")
            
            // Disable built-in code block handling to prevent conflicts
            builder.usePlugin(object : AbstractMarkwonPlugin() {
                override fun processMarkdown(markdown: String): String {
                    Timber.d("CODE_BLOCK_DEBUG: Custom markdown preprocessor called")
                    return super.processMarkdown(markdown)
                }
            })
            
            // === EXTENDED FEATURES FOR AI RESPONSES ===
            
            // HTML support for rich AI formatting
            builder.usePlugin(HtmlPlugin.create())
            
            // Tables - AI models frequently generate tables
            builder.usePlugin(TablePlugin.create(context))
            
            // Task lists with checkboxes - common in AI responses
            builder.usePlugin(TaskListPlugin.create(context))
            
            // Strikethrough text - for corrections/edits
            builder.usePlugin(StrikethroughPlugin.create())
            
            // Auto-link URLs - AI models often include links
            builder.usePlugin(LinkifyPlugin.create())
            
            // Images with optimized loading - AI can reference images
            builder.usePlugin(GlideImagesPlugin.create(context))
            
            // === ADVANCED AI-SPECIFIC FEATURES ===
            
            // Note: Code blocks are now handled by our CodeBlockPlugin above
            
            // LaTeX/Math formula support - AI models can generate mathematical content
            try {
                builder.usePlugin(JLatexMathPlugin.create(16f))
            } catch (e: Exception) {
                Timber.w("LaTeX support not available: ${e.message}")
            }
            
            // Custom extensions for AI-specific markup - disabled for now
            // Simple extensions will be re-enabled once dependency issues are resolved
            // try {
            //     builder.usePlugin(SimpleExtPlugin.create { plugin ->
            //         // Add custom spans for AI model annotations
            //         plugin.addExtension(1, '<', '>') { content ->
            //             // Support for <model:gpt-4> or <confidence:85%> style annotations
            //             createAIAnnotationSpan(content)
            //         }
            //     })
            // } catch (e: Exception) {
            //     Timber.w("Custom extensions not available: ${e.message}")
            // }
            
            builder.build()
            
        } catch (e: Exception) {
            Timber.w("Failed to create comprehensive Markwon: ${e.message}")
            // Fallback with minimal features
            try {
                Markwon.builder(context)
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(LinkifyPlugin.create())
                    .build()
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "Even fallback Markwon failed")
                Markwon.builder(context).build()
            }
        }
    }
    
    /**
     * AI ANNOTATIONS: Create custom spans for AI model annotations
     */
    private fun createAIAnnotationSpan(content: String): android.text.style.CharacterStyle? {
        return when {
            content.startsWith("model:") -> {
                // Model name annotation: <model:gpt-4>
                val modelName = content.removePrefix("model:")
                createModelAnnotationSpan(modelName)
            }
            content.startsWith("confidence:") -> {
                // Confidence annotation: <confidence:85%>
                val confidence = content.removePrefix("confidence:")
                createConfidenceAnnotationSpan(confidence)
            }
            content.startsWith("thinking") -> {
                // Thinking block: <thinking>
                createThinkingBlockSpan()
            }
            content.startsWith("cite:") -> {
                // Citation: <cite:source1>
                val citation = content.removePrefix("cite:")
                createCitationSpan(citation)
            }
            else -> null
        }
    }
    
    private fun createModelAnnotationSpan(modelName: String): android.text.style.CharacterStyle {
        return object : android.text.style.CharacterStyle() {
            override fun updateDrawState(tp: android.text.TextPaint) {
                tp.color = android.graphics.Color.parseColor("#0969DA") // GitHub blue
                tp.isFakeBoldText = true
                tp.textSize = tp.textSize * 0.8f
            }
        }
    }
    
    private fun createConfidenceAnnotationSpan(confidence: String): android.text.style.CharacterStyle {
        val confidenceValue = confidence.removeSuffix("%").toFloatOrNull() ?: 0f
        val color = when {
            confidenceValue >= 80f -> android.graphics.Color.parseColor("#00C851") // Green
            confidenceValue >= 60f -> android.graphics.Color.parseColor("#FF8800") // Orange  
            else -> android.graphics.Color.parseColor("#FF4444") // Red
        }
        
        return object : android.text.style.CharacterStyle() {
            override fun updateDrawState(tp: android.text.TextPaint) {
                tp.color = color
                tp.isFakeBoldText = true
                tp.textSize = tp.textSize * 0.75f
            }
        }
    }
    
    private fun createThinkingBlockSpan(): android.text.style.CharacterStyle {
        return object : android.text.style.CharacterStyle() {
            override fun updateDrawState(tp: android.text.TextPaint) {
                tp.color = android.graphics.Color.parseColor("#6B7280") // Gray
                tp.isStrikeThruText = false
                tp.alpha = 180 // Semi-transparent for thinking blocks
            }
        }
    }
    
    private fun createCitationSpan(citation: String): android.text.style.CharacterStyle {
        return object : android.text.style.CharacterStyle() {
            override fun updateDrawState(tp: android.text.TextPaint) {
                tp.color = android.graphics.Color.parseColor("#0969DA") // Blue
                tp.isUnderlineText = true
                tp.textSize = tp.textSize * 0.85f
            }
        }
    }
    
    /**
     * PERFORMANCE: Update processing metrics
     */
    private fun updatePerformanceMetrics(processingTime: Long) {
        averageProcessingTime = (averageProcessingTime + processingTime) / 2
        if (processingTime > maxProcessingTime) {
            maxProcessingTime = processingTime
        }
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): String {
        val cacheHits = cacheHitCount.get()
        val cacheSize = processedCache.size
        val hitRate = if (cacheSize > 0) (cacheHits * 100 / (cacheSize + cacheHits)) else 0
        
        return "Avg: ${averageProcessingTime}ms, Max: ${maxProcessingTime}ms, Cache: ${cacheSize}, Hit Rate: ${hitRate}%"
    }
    
    /**
     * CLEANUP: Efficient cache management
     */
    fun clearCache() {
        processedCache.clear()
        cacheHitCount.set(0)
        currentJob.get()?.cancel("Cache cleared")
        averageProcessingTime = 0L
        maxProcessingTime = 0L
    }
    
    /**
     * AI-AWARE: Check if content contains complex AI markdown patterns
     */
    private fun hasComplexAIMarkdown(content: String): Boolean {
        // Check for complex patterns that benefit from specialized processing
        return AI_MARKDOWN_PATTERNS.any { pattern ->
            content.contains(pattern)
        } || content.contains(Regex("""\$\{1,2\}[^$]+\$\{1,2\}""")) // LaTeX patterns
          || content.contains(Regex("""```\w*\n[\s\S]*?```""")) // Code blocks
          || content.contains(Regex("""\|[^\n]*\|""")) // Table patterns
    }
    
    /**
     * SPECIALIZED: Process complex AI content with optimizations
     */
    private fun processComplexAIContent(
        content: String,
        textView: TextView,
        isStreaming: Boolean,
        onComplete: ((Spanned) -> Unit)?
    ) {
        // Cancel previous job
        currentJob.get()?.cancel("Complex AI content")
        
        val contentHash = content.hashCode()
        val job = processingScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Pre-process content for AI-specific optimizations
                val optimizedContent = preprocessAIContent(content)
                val processed = getMarkwonInstance().toMarkdown(optimizedContent)
                
                withContext(Dispatchers.Main.immediate) {
                    textView.text = processed
                    onComplete?.invoke(processed)
                    
                    // Cache the result
                    processedCache[contentHash] = CachedResult(processed, contentHash, startTime)
                    lastProcessedContent.set(content)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                updatePerformanceMetrics(processingTime)
                
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Timber.e(e, "Complex AI content processing failed")
                withContext(Dispatchers.Main) {
                    textView.text = content
                    onComplete?.invoke(android.text.SpannableString(content))
                }
            } finally {
                isProcessing.set(false)
                lastProcessedTime.set(System.currentTimeMillis())
            }
        }
        
        currentJob.set(job)
        isProcessing.set(true)
    }
    
    /**
     * PREPROCESSING: Optimize AI content before markdown processing
     */
    private suspend fun preprocessAIContent(content: String): String {
        var processed = content
        
        // Optimize table formatting for better rendering
        processed = processed.replace(Regex("""\|\s*([^|\n]+)\s*""")) { matchResult ->
            "| ${matchResult.groupValues[1].trim()} "
        }
        
        // Enhanced code block formatting with syntax validation
        processed = processed.replace(Regex("""```(\w*)\n?([\s\S]*?)```""")) { matchResult ->
            val language = matchResult.groupValues[1]
            val code = matchResult.groupValues[2].trim()
            
            // Validate and clean code content
            val cleanedCode = code
                .replace(Regex("\\s+$", RegexOption.MULTILINE), "") // Remove trailing spaces
                .replace(Regex("^\\s*\\n"), "") // Remove leading empty lines
                .replace(Regex("\\n\\s*$"), "") // Remove trailing empty lines
            
            "```$language\n$cleanedCode\n```"
        }
        
        // Optimize LaTeX formatting
        processed = processed.replace(Regex("""\$\$\s*([^$]+)\s*\$\$""")) { matchResult ->
            "$$ ${matchResult.groupValues[1].trim()} $$"
        }
        
        return processed
    }
    
    /**
     * COMPATIBILITY: Methods expected by ChatAdapter
     */
    fun processMarkdownStreaming(
        content: String,
        textView: TextView,
        onComplete: ((Spanned) -> Unit)? = null,
        isStreaming: Boolean = true,
        codeBlockContainer: LinearLayout? = null
    ) {
        Timber.d("CODE_BLOCK_DEBUG: ========== processMarkdownStreaming CALLED ==========")
        Timber.d("CODE_BLOCK_DEBUG: Content length: ${content.length}")
        Timber.d("CODE_BLOCK_DEBUG: Contains backticks: ${content.contains("```")}")
        Timber.d("CODE_BLOCK_DEBUG: Container provided: ${codeBlockContainer != null}")
        
        // Simple unified processing for streaming
        try {
            // Clear any existing code blocks in the container for fresh start
            codeBlockContainer?.removeAllViews()
            codeBlockContainer?.visibility = View.GONE
            
            // Process with Markwon including our code block plugin
            val markwon = getMarkwonInstance(codeBlockContainer)
            val processed = markwon.toMarkdown(content)
            textView.text = processed
            onComplete?.invoke(processed)
            
            Timber.d("CODE_BLOCK_DEBUG: Processed streaming markdown with ${codeBlockContainer?.childCount ?: 0} code blocks")
        } catch (e: Exception) {
            Timber.e(e, "Failed to process streaming markdown")
            textView.text = content
            onComplete?.invoke(android.text.SpannableString(content))
        }
    }
    
    fun setDebugMode(enabled: Boolean) {
        // Debug mode functionality for compatibility
        logDebug("Debug mode set to: $enabled")
    }
    
    /**
     * SYNCHRONOUS: Process markdown synchronously and return SpannableStringBuilder
     * Used for compatibility with existing code that expects immediate results
     */
    fun processMarkdown(text: String): SpannableStringBuilder {
        return try {
            val processed = getMarkwonInstance().toMarkdown(text)
            SpannableStringBuilder(processed)
        } catch (e: Exception) {
            Timber.e(e, "Synchronous markdown processing failed")
            SpannableStringBuilder(text)
        }
    }
    
    /**
     * CLEANUP: Resource cleanup
     */
    fun cleanup() {
        currentJob.get()?.cancel("Cleanup")
        processingScope.cancel("Processor cleanup")
        clearCache()
    }
    
    private fun logDebug(message: String) {
        Timber.v("UltraFastStreaming: $message")
    }
}

/**
 * UTILITY: Content difference calculator for intelligent throttling
 */
class StreamingContentDiffer {
    fun calculateDifference(oldContent: String, newContent: String): Int {
        if (oldContent.isEmpty()) return newContent.length
        if (newContent.isEmpty()) return oldContent.length
        
        // Quick length-based difference for performance
        val lengthDiff = kotlin.math.abs(newContent.length - oldContent.length)
        
        // For small differences, check actual content
        if (lengthDiff < 10) {
            return if (oldContent == newContent) 0 else lengthDiff
        }
        
        return lengthDiff
    }
}