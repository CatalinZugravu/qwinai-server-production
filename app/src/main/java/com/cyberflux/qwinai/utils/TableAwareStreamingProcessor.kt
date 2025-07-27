package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * TABLE-AWARE Streaming Processor
 * 
 * Eliminates table flickering through:
 * - Table boundary detection
 * - Complete table buffering
 * - Progressive table rendering
 * - Anti-flicker table state management
 */
class TableAwareStreamingProcessor(private val context: Context) {
    
    companion object {
        // Table detection patterns
        private val TABLE_START_PATTERN = Pattern.compile("\\|.*\\|\\s*\\n\\|[-:\\s|]+\\|")
        private val TABLE_ROW_PATTERN = Pattern.compile("\\|.*\\|")
        private val TABLE_SEPARATOR_PATTERN = Pattern.compile("\\|[-:\\s|]+\\|")
        
        // Processing thresholds
        private const val MIN_TABLE_ROWS = 2 // Minimum rows to consider a table
        private const val TABLE_BUFFER_TIMEOUT = 150L // Wait for complete table
    }
    
    // Cached Markwon instance
    private var markwonInstance: Markwon? = null
    
    // Table state tracking
    private val currentTableBuffer = AtomicReference<StringBuilder>(null)
    private val tableProcessingJob = AtomicReference<Job?>(null)
    private val isProcessingTable = AtomicBoolean(false)
    
    // Content caching
    private val tableCache = ConcurrentHashMap<String, Spanned>(16)
    
    // Processing scope
    private val processingScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("TableAwareProcessor")
    )
    
    data class TableInfo(
        val startIndex: Int,
        val endIndex: Int,
        val isComplete: Boolean,
        val rowCount: Int
    )
    
    /**
     * MAIN: Process streaming content with table awareness
     */
    @MainThread
    fun processStreamingContent(
        content: String,
        textView: TextView,
        isStreaming: Boolean = true,
        onComplete: ((Spanned) -> Unit)? = null
    ) {
        if (content.isEmpty()) {
            textView.text = ""
            onComplete?.invoke(android.text.SpannableString(""))
            return
        }
        
        // Check for table content
        val tableInfo = detectTableContent(content)
        
        when {
            tableInfo != null && isStreaming -> {
                // Handle table content with special processing
                processTableContent(content, textView, tableInfo, onComplete)
            }
            else -> {
                // Use standard ultra-fast processing for non-table content
                processNonTableContent(content, textView, isStreaming, onComplete)
            }
        }
    }
    
    /**
     * DETECTION: Identify table content and boundaries using advanced detector
     */
    private fun detectTableContent(content: String): TableInfo? {
        val detection = AdvancedTableDetector.detectTable(content)
        
        return if (detection.hasTable) {
            TableInfo(
                startIndex = detection.startLine,
                endIndex = detection.endLine,
                isComplete = detection.isComplete,
                rowCount = detection.rowCount
            )
        } else null
    }
    
    /**
     * TABLE: Process table content with anti-flicker buffering
     */
    @MainThread
    private fun processTableContent(
        content: String,
        textView: TextView,
        tableInfo: TableInfo,
        onComplete: ((Spanned) -> Unit)?
    ) {
        val contentHash = content.hashCode().toString()
        
        // Check cache first
        tableCache[contentHash]?.let { cached ->
            textView.text = cached
            onComplete?.invoke(cached)
            return
        }
        
        if (tableInfo.isComplete) {
            // Table is complete - process immediately
            processCompleteTable(content, textView, contentHash, onComplete)
        } else {
            // Table is incomplete - buffer until complete or timeout
            bufferIncompleteTable(content, textView, tableInfo, contentHash, onComplete)
        }
    }
    
    /**
     * COMPLETE: Process complete table immediately
     */
    @MainThread
    private fun processCompleteTable(
        content: String,
        textView: TextView,
        contentHash: String,
        onComplete: ((Spanned) -> Unit)?
    ) {
        processingScope.launch {
            try {
                val processed = processMarkdownSafely(content)
                
                withContext(Dispatchers.Main) {
                    textView.text = processed
                    onComplete?.invoke(processed)
                    
                    // Cache the result
                    tableCache[contentHash] = processed
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing complete table")
                handleProcessingError(content, textView, onComplete)
            }
        }
    }
    
    /**
     * BUFFER: Handle incomplete table with timeout
     */
    @MainThread
    private fun bufferIncompleteTable(
        content: String,
        textView: TextView,
        tableInfo: TableInfo,
        contentHash: String,
        onComplete: ((Spanned) -> Unit)?
    ) {
        // Cancel previous buffering job
        tableProcessingJob.get()?.cancel("New table content")
        
        // Extract non-table content for immediate display
        val nonTableContent = extractNonTableContent(content, tableInfo)
        if (nonTableContent.isNotEmpty()) {
            // Show non-table content immediately without flickering
            processingScope.launch {
                try {
                    val processed = processMarkdownSafely(nonTableContent)
                    withContext(Dispatchers.Main) {
                        textView.text = processed
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing non-table content")
                }
            }
        }
        
        // Buffer table content with timeout
        val job = processingScope.launch {
            try {
                // Wait for potential table completion
                delay(TABLE_BUFFER_TIMEOUT)
                
                // Process whatever we have after timeout
                val processed = processMarkdownSafely(content)
                
                withContext(Dispatchers.Main) {
                    textView.text = processed
                    onComplete?.invoke(processed)
                    
                    // Cache the result
                    tableCache[contentHash] = processed
                }
                
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Timber.e(e, "Error processing buffered table")
                withContext(Dispatchers.Main) {
                    handleProcessingError(content, textView, onComplete)
                }
            }
        }
        
        tableProcessingJob.set(job)
    }
    
    /**
     * EXTRACT: Get non-table content for immediate display
     */
    private fun extractNonTableContent(content: String, tableInfo: TableInfo): String {
        val lines = content.split('\n')
        val nonTableLines = mutableListOf<String>()
        
        // Add content before table
        for (i in 0 until tableInfo.startIndex) {
            if (i < lines.size) {
                nonTableLines.add(lines[i])
            }
        }
        
        // Add content after table (if any)
        for (i in (tableInfo.endIndex + 1) until lines.size) {
            nonTableLines.add(lines[i])
        }
        
        return nonTableLines.joinToString("\n").trim()
    }
    
    /**
     * CHECK: Determine if table is complete
     */
    private fun checkTableCompleteness(content: String, tableStart: Int, tableEnd: Int): Boolean {
        val lines = content.split('\n')
        
        // Check if table ends naturally (empty line or non-table content)
        val nextLineIndex = tableEnd + 1
        if (nextLineIndex < lines.size) {
            val nextLine = lines[nextLineIndex].trim()
            return nextLine.isEmpty() || !nextLine.startsWith("|")
        }
        
        // If it's the last content, consider it potentially incomplete during streaming
        return false
    }
    
    /**
     * STANDARD: Process non-table content normally
     */
    @MainThread
    private fun processNonTableContent(
        content: String,
        textView: TextView,
        isStreaming: Boolean,
        onComplete: ((Spanned) -> Unit)?
    ) {
        processingScope.launch {
            try {
                val processed = processMarkdownSafely(content)
                
                withContext(Dispatchers.Main) {
                    textView.text = processed
                    onComplete?.invoke(processed)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing non-table content")
                withContext(Dispatchers.Main) {
                    handleProcessingError(content, textView, onComplete)
                }
            }
        }
    }
    
    /**
     * SAFE: Process markdown with error handling
     */
    @WorkerThread
    private suspend fun processMarkdownSafely(content: String): Spanned {
        return try {
            val markwon = getMarkwonInstance()
            markwon.toMarkdown(content)
        } catch (e: Exception) {
            Timber.w("Markdown processing failed, using plain text: ${e.message}")
            android.text.SpannableString(content)
        }
    }
    
    /**
     * ERROR: Handle processing errors gracefully
     */
    @MainThread
    private fun handleProcessingError(
        content: String,
        textView: TextView,
        onComplete: ((Spanned) -> Unit)?
    ) {
        val fallback = android.text.SpannableString(content)
        textView.text = fallback
        onComplete?.invoke(fallback)
    }
    
    /**
     * INSTANCE: Get or create Markwon instance
     */
    private fun getMarkwonInstance(): Markwon {
        return markwonInstance ?: createMarkwonInstance().also { markwonInstance = it }
    }
    
    /**
     * CREATE: Initialize Markwon with table support
     */
    private fun createMarkwonInstance(): Markwon {
        return try {
            Markwon.builder(context)
                .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
                .usePlugin(io.noties.markwon.html.HtmlPlugin.create())
                .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
                .usePlugin(io.noties.markwon.ext.tasklist.TaskListPlugin.create(context))
                .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
                .build()
        } catch (e: Exception) {
            Timber.w("Failed to create table-aware Markwon: ${e.message}")
            try {
                Markwon.builder(context).build()
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "Even basic Markwon failed")
                throw IllegalStateException("Cannot create Markwon instance", fallbackError)
            }
        }
    }
    
    /**
     * CLEANUP: Clear resources and cancel jobs
     */
    fun cleanup() {
        tableProcessingJob.get()?.cancel("Processor cleanup")
        processingScope.cancel("Table processor cleanup")
        tableCache.clear()
        markwonInstance = null
        currentTableBuffer.set(null)
        isProcessingTable.set(false)
    }
    
    /**
     * STATS: Get processing statistics
     */
    fun getStats(): String {
        val cacheSize = tableCache.size
        val isProcessing = isProcessingTable.get()
        return "TableProcessor - Cache: $cacheSize, Processing: $isProcessing"
    }
}