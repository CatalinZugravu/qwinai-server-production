package com.cyberflux.qwinai.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.service.WebSearchService
import com.cyberflux.qwinai.tools.ToolServiceHelper
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.SimplifiedTokenManager
import com.cyberflux.qwinai.utils.UnifiedStreamingManager
import com.cyberflux.qwinai.utils.StreamingCancellationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import java.util.Locale


object StreamingHandler {

    // ULTRA-FAST STREAMING: Dynamic buffer sizing for optimal first-token latency
    private const val INITIAL_BUFFER_SIZE = 1024  // Small for immediate first token
    private const val STANDARD_BUFFER_SIZE = 8192  // Balanced for sustained streaming
    private const val LARGE_BUFFER_SIZE = 16384   // For heavy content streams
    private const val MAX_BUFFER_SIZE = 32768     // For very large responses
    
    // REAL-TIME UI: 60 FPS updates for fluid streaming
    private const val UI_UPDATE_INTERVAL_MS = 16L  // ~60 FPS for ultra-smooth streaming
    private const val FIRST_TOKEN_FAST_INTERVAL_MS = 8L  // Even faster for first token
    private const val CONTENT_GROWTH_THRESHOLD = 25  // Lower threshold for more responsive updates
    
    /**
     * ULTRA-FAST STREAMING: Dynamic buffer size calculation for optimal performance
     */
    private fun getOptimalBufferSize(contentLength: Int, isFirstToken: Boolean): Long {
        return when {
            isFirstToken -> INITIAL_BUFFER_SIZE.toLong()  // Smallest for immediate response
            contentLength < 500 -> INITIAL_BUFFER_SIZE.toLong()  // Keep small for quick responses
            contentLength < 5000 -> STANDARD_BUFFER_SIZE.toLong()  // Standard for most content
            contentLength < 25000 -> LARGE_BUFFER_SIZE.toLong()  // Larger for heavy content
            else -> MAX_BUFFER_SIZE.toLong()  // Max for very large responses
        }
    }
    
    /**
     * REAL-TIME MARKDOWN: Get update interval based on content state
     */
    private fun getUpdateInterval(contentLength: Int, isFirstToken: Boolean): Long {
        return when {
            isFirstToken -> FIRST_TOKEN_FAST_INTERVAL_MS  // Ultra-fast for first token
            contentLength < 1000 -> UI_UPDATE_INTERVAL_MS  // Standard 60 FPS
            contentLength < 10000 -> UI_UPDATE_INTERVAL_MS + 4L  // Slightly slower for larger content
            else -> UI_UPDATE_INTERVAL_MS + 8L  // Conservative for very large content
        }
    }
    
    /**
     * IMPROVED CANCELLATION CHECK: Consolidated cancellation check to avoid race conditions
     */
    private suspend fun isStreamingCancelled(streamingState: StreamingState): Boolean {
        return try {
            !coroutineContext.isActive || 
            streamingState.isCancelled || 
            streamingState.streamingSession.isCancelled.get()
        } catch (e: Exception) {
            Timber.w("Error checking cancellation state: ${e.message}")
            true // Assume cancelled on error
        }
    }
    
    /**
     * STREAMING VALIDATION: Real-time markdown validation during streaming
     * Ensures content won't break rendering and maintains streaming fluidity
     */
    private fun validateStreamingMarkdown(content: String): Boolean {
        return try {
            // PERFORMANCE OPTIMIZATION: Skip validation for very small content
            if (content.length < 100) return true
            
            // REAL-TIME VALIDATION: Quick checks for common markdown issues that break rendering
            val backtickCount = content.count { it == '`' }
            val hasUnclosedCodeBlock = backtickCount % 6 != 0 && backtickCount >= 3
            
            // Check for malformed code blocks that could break real-time rendering
            val codeBlockPattern = "```[\\w]*\\s*\\n[\\s\\S]*?```"
            val validCodeBlocks = codeBlockPattern.toRegex().findAll(content).count() * 6
            val expectedBackticks = validCodeBlocks
            
            when {
                // CRITICAL: Unclosed code blocks can break streaming markdown processing
                hasUnclosedCodeBlock && content.length > 500 -> {
                    Timber.w("⚠️ Unclosed code block detected in streaming content")
                    false
                }
                // PERFORMANCE: Too many headers can slow down parsing
                content.count { it == '#' } > 50 -> {
                    Timber.w("⚠️ Excessive headers detected in streaming content")
                    false  
                }
                // STABILITY: Very unbalanced brackets can cause parsing issues
                kotlin.math.abs(content.count { it == '[' } - content.count { it == ']' }) > 20 -> {
                    Timber.w("⚠️ Unbalanced brackets detected in streaming content")
                    false
                }
                else -> true // Content appears safe for streaming
            }
        } catch (e: Exception) {
            Timber.w(e, "Error validating streaming markdown - defaulting to safe")
            true // Default to valid to avoid breaking streaming
        }
    }
    
    /**
     * Cancel all active streaming operations immediately using per-stream tokens
     */
    fun cancelAllStreaming() {
        Timber.d("🛑🛑🛑 CANCELLING ALL STREAMING - Using per-stream cancellation!")
        StreamingCancellationManager.cancelAllStreams("User requested cancel all")
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun processStreamingResponse(
        responseBody: ResponseBody,
        adapter: ChatAdapter,
        messagePosition: Int,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
        isWebSearchEnabled: Boolean = false,
        context: Context? = null,
        apiService: AimlApiService? = null,
        modelId: String = ""
    ) {
        // PRODUCTION: Feature flag check
        if (!UnifiedStreamingManager.isStreamingContinuationEnabled) {
            Timber.d("🔒 Streaming continuation disabled, using legacy processing")
            onError("Streaming continuation feature is disabled")
            return
        }
        
        // PRODUCTION: Circuit breaker check
        if (UnifiedStreamingManager.isCircuitBreakerOpen()) {
            Timber.w("⚠️ Streaming circuit breaker is OPEN, blocking new streaming requests")
            onError("Streaming service temporarily unavailable")
            return
        }
        Timber.d("🚀 Starting streaming for model: $modelId, position: $messagePosition")
        
        // Create new streaming session with cancellation token
        val streamingSession = StreamingCancellationManager.createStreamingSession(
            adapter.currentList.getOrNull(messagePosition)?.id ?: "unknown_${System.currentTimeMillis()}"
        )

        // IMPORTANT: Always log the actual web search status
        val actualWebSearchEnabled = isWebSearchEnabled
        Timber.d("WebSearch: $actualWebSearchEnabled")

        withContext(Dispatchers.IO) {
            val streamingState = StreamingState(
                modelId = modelId,
                isWebSearchEnabled = actualWebSearchEnabled,  // NEW: Store the flag in streaming state
                webSearchExecutionLock = AtomicBoolean(false), // NEW: Add lock to prevent duplicate search executions
                streamingSession = streamingSession // NEW: Store the streaming session for cancellation
            )
            val isProcessing = AtomicBoolean(true)
            var uiUpdateJob: Job? = null

            try {
                withContext(Dispatchers.Main) {
                    initializeStreaming(
                        adapter,
                        messagePosition,
                        streamingState,
                        modelId,
                        actualWebSearchEnabled  // Pass the actual flag
                    )
                }

                uiUpdateJob = launch {
                    var lastUpdateContent = ""
                    var isFirstUpdate = true

                    while (isProcessing.get() && !isStreamingCancelled(streamingState)) {
                        // IMPROVED: Use consolidated cancellation check in UI update loop
                        if (isStreamingCancelled(streamingState)) {
                            Timber.d("🛑 UI update loop cancelled - stopping immediately")
                            break
                        }
                        
                        val currentContent = streamingState.mainContent.toString()

                        // ULTRA-FAST FIRST TOKEN: Immediate update for first content
                        if (currentContent.isNotEmpty() && (isFirstUpdate || currentContent != lastUpdateContent)) {
                            updateStreamingUIRealTime(adapter, messagePosition, streamingState, isFirstUpdate)
                            lastUpdateContent = currentContent
                            
                            if (isFirstUpdate) {
                                isFirstUpdate = false
                                Timber.d("🚀 FIRST TOKEN delivered - ultra-fast latency achieved")
                            }
                        }

                        // DYNAMIC INTERVALS: Adaptive update rate based on content size and first token
                        val updateInterval = getUpdateInterval(currentContent.length, isFirstUpdate)
                        delay(updateInterval)
                    }
                }

                processStream(
                    responseBody,
                    streamingState,
                    context,
                    apiService,
                    adapter,
                    messagePosition
                )

                isProcessing.set(false)
                uiUpdateJob.cancel()

                withContext(Dispatchers.Main) {
                    finalizeStreaming(adapter, messagePosition, streamingState, context)
                    onComplete()
                }

            } catch (_: CancellationException) {
                Timber.d("Streaming cancelled")
                isProcessing.set(false)
                uiUpdateJob?.cancel()
                
                // CRITICAL FIX: Clean up StreamingCancellationManager session on cancellation
                streamingState.streamingSession.streamId.let { streamId ->
                    StreamingCancellationManager.cancelStream(streamId, "Streaming cancelled by user")
                }

            } catch (e: Exception) {
                Timber.e(e, "Streaming error: ${e.message}")
                isProcessing.set(false)
                uiUpdateJob?.cancel()

                // CRITICAL FIX: Clean up StreamingCancellationManager session on error
                streamingState.streamingSession.streamId.let { streamId ->
                    StreamingCancellationManager.cancelStream(streamId, "Streaming error: ${e.message}")
                }

                // REFUND CREDITS: Refund chat credits on API error for non-subscribers
                context?.let { ctx ->
                    try {
                        if (!PrefsManager.isSubscribed(ctx)) {
                            val creditManager = CreditManager.getInstance(ctx)
                            if (creditManager.refundCredits(1, CreditManager.CreditType.CHAT, "API error: ${e.message}")) {
                                Timber.d("💰 Successfully refunded 1 chat credit due to streaming error")
                            } else {
                                Timber.w("⚠️ Failed to refund chat credit - user may be at maximum credits")
                            }
                        }
                    } catch (refundError: Exception) {
                        Timber.e(refundError, "❌ Error refunding chat credits: ${refundError.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    handleStreamingError(adapter, messagePosition, e.message ?: "Unknown error")
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }
    // OPTIMIZED: Initialize streaming with background operations
    private suspend fun initializeStreaming(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState,
        modelId: String,
        isWebSearchEnabled: Boolean
    ) {
        val message = adapter.currentList.getOrNull(messagePosition) ?: return

        // Move expensive operations to background thread
        withContext(Dispatchers.Default) {
            streamingState.modelId = modelId
            streamingState.messageId = message.id
            streamingState.conversationId = message.conversationId
            
            // CRITICAL: Start streaming session in state manager (background)
            UnifiedStreamingManager.createSession(
                messageId = message.id,
                conversationId = message.conversationId,
                sessionId = "stream_${message.id}_${System.currentTimeMillis()}",
                modelId = modelId
            )
        }

        val initialWebSearch = isWebSearchEnabled || message.isForceSearch

        if (initialWebSearch) {
            streamingState.isWebSearching = true
            streamingState.hasStartedWebSearch = true
            streamingState.webSearchStartTime = System.currentTimeMillis()
        }

        // Check if this model supports thinking content
        val isThinkingModel = isThinkingCapableModel(modelId)
        
        // For Perplexity models, always use generating state (will show "Web searching" in UI)
        val isPerplexityModel = modelId.contains("perplexity", ignoreCase = true)
        
        val initialMessage = message.copy(
            isGenerating = when {
                isThinkingModel -> false  // Don't show "Generating response" for thinking models initially
                isPerplexityModel -> true
                else -> !initialWebSearch
            },
            isLoading = when {
                isThinkingModel -> false  // No loading indicator for thinking models initially  
                isPerplexityModel -> true
                else -> !initialWebSearch
            },
            isWebSearchActive = if (isPerplexityModel) false else initialWebSearch,
            message = "",
            // Set thinking-related fields for thinking models
            hasThinkingProcess = isThinkingModel,
            isThinkingActive = isThinkingModel,
            // Clear any existing content
            thinkingProcess = if (isThinkingModel) "" else message.thinkingProcess
        )

        val currentList = adapter.currentList.toMutableList()
        if (messagePosition < currentList.size) {
            currentList[messagePosition] = initialMessage
            adapter.submitList(currentList)
        }
        adapter.startStreamingMode()
        Timber.d("Initialized - WebSearch: $initialWebSearch")
    }    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processStream(
        responseBody: ResponseBody,
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        val buffer = Buffer()

        responseBody.source().use { source ->
            while (!source.exhausted() && !isStreamingCancelled(streamingState)) {
                // IMPROVED: Use consolidated cancellation check
                if (isStreamingCancelled(streamingState)) {
                    Timber.d("🛑 Streaming cancelled during active processing - stopping immediately")
                    streamingState.isCancelled = true
                    break
                }

                // ULTRA-FAST STREAMING: Dynamic buffer size based on content length and first-token optimization
                val currentContentLength = streamingState.mainContent.length
                val isFirstRead = currentContentLength == 0
                val optimalBufferSize = getOptimalBufferSize(currentContentLength, isFirstRead)
                
                val bytesRead = source.read(buffer, optimalBufferSize)
                if (bytesRead > 0) {
                    // IMPROVED: Use consolidated cancellation check
                    if (isStreamingCancelled(streamingState)) {
                        Timber.d("🛑 Streaming cancelled before chunk processing - aborting")
                        streamingState.isCancelled = true
                        break
                    }
                    
                    val chunk = buffer.readUtf8()
                    processChunkDirectly(
                        chunk,
                        streamingState,
                        context,
                        apiService,
                        adapter,
                        messagePosition
                    )
                    
                    // IMPROVED: Use consolidated cancellation check after processing
                    if (isStreamingCancelled(streamingState)) {
                        Timber.d("🛑 Streaming cancelled after chunk processing - stopping")
                        streamingState.isCancelled = true
                        break
                    }
                }
            }
        }

        if (streamingState.accumulationBuffer.isNotEmpty()) {
            processRemainingData(streamingState)
        }

        // CRITICAL: Handle tool execution after stream ends if we're awaiting tool responses
        if (streamingState.awaitingToolResponse && !streamingState.hasCompletedToolCalls) {
            Timber.d("🔧 TOOL EXECUTION PHASE: Stream ended but still awaiting tool responses")
            Timber.d("🔧 Tool calls pending: ${streamingState.toolCalls.size}")
            
            // Execute any pending tool calls
            if (streamingState.toolCalls.isNotEmpty()) {
                executeToolCalls(streamingState, context)
                
                // After tools execute, trigger continuation if we have results
                if (streamingState.searchResults?.isNotEmpty() == true && adapter != null && context != null) {
                    Timber.d("🔧 CONTINUATION PHASE: Found ${streamingState.searchResults?.size} search results, starting continuation")
                    val searchResultsForAI = formatSearchResultsForAI(streamingState)
                    if (searchResultsForAI.isNotEmpty()) {
                        continueStreamingWithSearchResults(searchResultsForAI, streamingState, context, apiService!!, adapter, messagePosition)
                    } else {
                        Timber.w("🔧 Search results formatting failed")
                    }
                } else {
                    Timber.w("🔧 No search results found after tool execution (context: ${context != null}, adapter: ${adapter != null})")
                }
            } else {
                Timber.w("🔧 No tool calls to execute")
            }
        } else {
            Timber.d("🔧 No tool execution needed - awaitingTool:${streamingState.awaitingToolResponse}, completed:${streamingState.hasCompletedToolCalls}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processChunkDirectly(
        chunk: String,
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        streamingState.accumulationBuffer.append(chunk)

        val data = streamingState.accumulationBuffer.toString()
        val lines = data.split("\n")

        streamingState.accumulationBuffer.clear()

        if (lines.isNotEmpty() && !data.endsWith("\n")) {
            streamingState.accumulationBuffer.append(lines.last())
        }

        val linesToProcess = if (!data.endsWith("\n")) lines.dropLast(1) else lines

        for (line in linesToProcess) {
            if (line.startsWith("data: ")) {
                val eventData = line.substring(6).trim()

                when (eventData) {
                    "[DONE]" -> {
                        // Don't complete stream if we're still awaiting tool responses
                        if (streamingState.awaitingToolResponse && !streamingState.hasCompletedToolCalls) {
                            Timber.d("🔧 Stream received [DONE] but still awaiting tool responses - continuing...")
                            // Don't call handleStreamComplete yet, wait for tools to finish
                        } else {
                            handleStreamComplete(streamingState)
                        }
                    }
                    else -> {
                        if (eventData.isNotEmpty()) {
                            processEventData(
                                eventData,
                                streamingState,
                                context,
                                apiService,
                                adapter,
                                messagePosition
                            )

                            // OPTIMIZED: Debounced UI update to reduce flickering during rapid streaming
                            // Remove immediate updates that cause flickering
                        }
                    }
                }
            }
        }
    }

    // In StreamingHandler.kt - Update the processEventData method
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processEventData(
        data: String,
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        try {
            if (!isValidJson(data)) {
                Timber.w("Invalid JSON data: ${data.take(100)}")
                return
            }

            val jsonObject = JSONObject(data)

            // Check for tool calls FIRST
            val hasToolCallData = hasToolCalls(jsonObject)
            if (hasToolCallData) {
                Timber.d("🔧 TOOL DETECTED: Processing tool call data for model: ${streamingState.modelId}")
                processToolCallData(jsonObject, streamingState, context, adapter, messagePosition)

                // Check if tool calls are complete
                val finishReason = jsonObject.optJSONArray("choices")?.getJSONObject(0)?.optString("finish_reason")
                if (finishReason == "tool_calls" && streamingState.toolCalls.isNotEmpty()) {
                    // Execute tool calls and wait for results
                    executeToolCallsAndContinue(streamingState, context, apiService, adapter, messagePosition)
                    return
                }
                return
            }

            // Extract thinking content for supported models (Claude, DeepSeek R1, Qwen 3)
            val thinkingContent = extractThinkingContent(jsonObject, streamingState.modelId)
            if (thinkingContent.isNotEmpty()) {
                // First time receiving thinking content - ensure thinking UI is started
                if (streamingState.thinkingContent.isEmpty()) {
                    Timber.d("🧠 FIRST thinking content received for: ${streamingState.messageId}")
                }
                
                streamingState.thinkingContent.append(thinkingContent)
                streamingState.hasReceivedContent = true
                streamingState.isInThinkingPhase = true  // Track that we're in thinking phase
                
                // Update thinking content in the adapter for real-time display
                adapter?.updateThinkingContent(
                    messageId = streamingState.messageId,
                    thinkingContent = thinkingContent,
                    isThinkingActive = true
                )
                
                Timber.d("🧠 Thinking content chunk: ${thinkingContent.length} chars, total: ${streamingState.thinkingContent.length}")
                return // Skip regular content processing when we have thinking content
            }
            
            // Check for transition: we were in thinking phase but now getting regular content
            val extractedContent = extractContentAdvanced(jsonObject, streamingState.modelId)
            
            // CRITICAL TRANSITION DETECTION
            if (extractedContent.isNotEmpty()) {
                if (streamingState.isInThinkingPhase && !streamingState.hasCompletedThinking) {
                    // We were thinking and now we have regular content - TRANSITION!
                    Timber.w("🧠 ⚡ CRITICAL TRANSITION DETECTED! Thinking → Response")
                    Timber.w("🧠 MessageId: ${streamingState.messageId}")
                    Timber.w("🧠 Thinking content length: ${streamingState.thinkingContent.length}")
                    Timber.w("🧠 First regular content: '${extractedContent.take(50)}...'")
                    
                    streamingState.hasCompletedThinking = true
                    streamingState.isInThinkingPhase = false
                    
                    // IMMEDIATE thinking completion
                    adapter?.completeThinkingForMessage(streamingState.messageId)
                    
                    // Update UI to show regular generation - no text, just loading indicator
                    adapter?.let { adapterRef ->
                        withContext(Dispatchers.Main) {
                            adapterRef.updateLoadingStateDirect(
                                messageId = streamingState.messageId,
                                isGenerating = true,
                                isWebSearching = false,
                                customStatusText = null, // No text for generating state
                                customStatusColor = android.graphics.Color.parseColor("#757575")
                            )
                        }
                    }
                    
                    Timber.w("🧠 ✅ Thinking phase completed, response generation started")
                } else if (!streamingState.isInThinkingPhase) {
                    // We're already in response phase - normal content processing
                    Timber.d("📝 Regular content continues: ${extractedContent.length} chars")
                }
            }

            // Process regular content (if any) - already extracted above
            if (extractedContent.isNotEmpty()) {
                streamingState.hasReceivedContent = true
                
                // STREAMING VALIDATION: Real-time markdown validation during streaming
                val currentContent = streamingState.mainContent.toString()
                val newContentTotal = currentContent + extractedContent
                
                // Validate markdown syntax in real-time to prevent rendering issues
                val isValidMarkdown = validateStreamingMarkdown(newContentTotal)
                if (!isValidMarkdown) {
                    Timber.w("⚠️ Invalid markdown detected during streaming - applying safe content processing")
                    // For malformed markdown, we still add the content but may need to escape certain characters
                    streamingState.mainContent.append(extractedContent)
                } else {
                    // Content is valid, proceed normally
                    streamingState.mainContent.append(extractedContent)
                }
                
                val finalCurrentContent = streamingState.mainContent.toString()
                
                // CRITICAL: Update streaming state for continuation (with memory protection)
                if (finalCurrentContent.length < 50_000) { // Only update if content is reasonable size
                    UnifiedStreamingManager.updateSessionContent(
                        streamingState.messageId, 
                        finalCurrentContent
                    )
                } else {
                    Timber.w("Skipping streaming state update - content too large: ${finalCurrentContent.length} chars")
                }

                // Process citations in real-time if we have search results
                if (streamingState.searchResults?.isNotEmpty() == true) {
                    // Track citations as they appear
                    processCitationsInContent(extractedContent, streamingState)
                }

                Timber.d("📝 Regular content: ${extractedContent.length} chars, total: ${finalCurrentContent.length}, valid markdown: $isValidMarkdown")
            }

            // Extract related questions, images, and citations for Perplexity models
            if (streamingState.modelId.contains("perplexity", ignoreCase = true)) {
                extractRelatedQuestions(jsonObject, streamingState)
                extractSearchImages(jsonObject, streamingState)
                extractPerplexityCitations(jsonObject, streamingState)
            }
            
            // Check for usage data in the streaming response
            val usage = jsonObject.optJSONObject("usage")
            if (usage != null) {
                // Parse usage data into our Usage object
                val promptTokens = usage.optInt("prompt_tokens")
                val completionTokens = usage.optInt("completion_tokens") 
                val totalTokens = usage.optInt("total_tokens")
                val reasoningTokens = usage.optInt("reasoning_tokens", 0)
                
                streamingState.usageData = AimlApiResponse.Usage(
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = totalTokens,
                    reasoningTokens = reasoningTokens
                )
                
                Timber.d("📊 Usage data received - Input: $promptTokens, Output: $completionTokens, Total: $totalTokens")
            }

            checkFinishReason(jsonObject, streamingState)

        } catch (e: Exception) {
            Timber.e(e, "Error processing event data: ${e.message}")
        }
    }

    // Add new method to execute tool calls and continue streaming
// In StreamingHandler.kt - Update executeToolCallsAndContinue
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun executeToolCallsAndContinue(
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        if (context == null || apiService == null) return

        try {
            // Mark that we're processing tool calls
            streamingState.hasCompletedToolCalls = false

            // Execute all tool calls
            val toolResults = mutableListOf<ToolCallResult>()

            for (toolCall in streamingState.toolCalls) {
                toolCall.isComplete = true

                when (toolCall.function.name) {
                    "web_search" -> {
                        // Execute web search
                        executeWebSearchImproved(toolCall, streamingState, adapter)

                        // Add result if we got search results
                        if (streamingState.searchResults?.isNotEmpty() == true) {
                            val searchResultsText = buildString {
                                appendLine("Web search results:")
                                streamingState.searchResults?.forEachIndexed { index, result ->
                                    appendLine("${index + 1}. ${result.title}")
                                    appendLine("   ${result.snippet}")
                                    appendLine("   URL: ${result.url}")
                                    appendLine()
                                }
                            }
                            
                            toolResults.add(ToolCallResult(
                                toolCallId = toolCall.id,
                                functionName = "web_search",
                                content = searchResultsText
                            ))
                        }
                    }
                    else -> {
                        // Handle other tools
                        executeToolCall(toolCall, streamingState, context, adapter, messagePosition)
                    }
                }
            }

            streamingState.hasCompletedToolCalls = true

            // RESTORED: Continuation logic is ESSENTIAL for tool calling to work
            // After executing tools, we need to feed results back to AI for final response
            if (toolResults.isNotEmpty() && adapter != null) {
                // Format search results for the AI to process
                val searchResultsForAI = formatSearchResultsForAI(streamingState)
                if (searchResultsForAI.isNotEmpty()) {
                    Timber.d("🔧 Tool execution complete, continuing stream to let AI process results")
                    continueStreamingWithSearchResults(searchResultsForAI, streamingState, context, apiService, adapter, messagePosition)
                } else {
                    Timber.d("No search results to process, continuing with current stream")
                }
            } else {
                Timber.d("No tool results to process, continuing with current stream")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error executing tool calls and continuing: ${e.message}")
            streamingState.hasCompletedToolCalls = true
        }
    }
    private suspend fun performWebSearchWithService(
        query: String
    ): List<ChatAdapter.WebSearchSource> = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 Performing single web search API call for: '$query'")
            
            // Use WebSearchService to perform the search - SINGLE API CALL
            val searchResponse = WebSearchService.performSearch(
                query = query,
                freshness = "recent",
                count = 7
            )

            if (searchResponse.success && searchResponse.results.isNotEmpty()) {
                Timber.d("✓ Web search API returned ${searchResponse.results.size} results")
                
                // Convert WebSearchService results to ChatAdapter.WebSearchSource
                searchResponse.results.map { result ->
                    ChatAdapter.WebSearchSource(
                        title = result.title,
                        url = result.url,
                        snippet = result.snippet,
                        displayLink = result.displayLink,
                        favicon = "https://www.google.com/s2/favicons?domain=${result.displayLink}&sz=64"
                    )
                }
            } else {
                Timber.w("Web search returned no results for query: $query")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error performing web search: ${e.message}")
            emptyList()
        }
    }






    // Add method to process citations as content streams
    private fun processCitationsInContent(content: String, streamingState: StreamingState) {
        if (streamingState.searchResults.isNullOrEmpty()) return

        // Pattern to find [1], [2], etc.
        val citationPattern = Pattern.compile("\\[(\\d+)]")
        val matcher = citationPattern.matcher(content)

        while (matcher.find()) {
            val citationNumber = matcher.group(1)?.toIntOrNull() ?: continue
            val sourceIndex = citationNumber - 1

            if (sourceIndex in streamingState.searchResults!!.indices) {
                val source = streamingState.searchResults!![sourceIndex]
                streamingState.citedSources.add(source.url)
            }
        }
    }

    /**
     * Extract related questions from Perplexity API response
     */
    private fun extractRelatedQuestions(jsonObject: JSONObject, streamingState: StreamingState) {
        try {
            // Look for related_questions in the response
            val relatedQuestionsArray = jsonObject.optJSONArray("related_questions")
            if (relatedQuestionsArray != null && relatedQuestionsArray.length() > 0) {
                val questions = mutableListOf<String>()
                for (i in 0 until relatedQuestionsArray.length()) {
                    val question = relatedQuestionsArray.optString(i)
                    if (question.isNotBlank()) {
                        questions.add(question)
                    }
                }
                if (questions.isNotEmpty()) {
                    streamingState.relatedQuestions = questions
                    Timber.d("✓ Extracted ${questions.size} related questions from Perplexity response")
                }
            }

            // Also check in choices array (alternative location)
            val choices = jsonObject.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                
                // Check in delta
                val delta = choice.optJSONObject("delta")
                val deltaRelatedQuestions = delta?.optJSONArray("related_questions")
                if (deltaRelatedQuestions != null && deltaRelatedQuestions.length() > 0) {
                    val questions = mutableListOf<String>()
                    for (i in 0 until deltaRelatedQuestions.length()) {
                        val question = deltaRelatedQuestions.optString(i)
                        if (question.isNotBlank()) {
                            questions.add(question)
                        }
                    }
                    if (questions.isNotEmpty()) {
                        streamingState.relatedQuestions = questions
                        Timber.d("✓ Extracted ${questions.size} related questions from delta")
                    }
                }

                // Check in message
                val message = choice.optJSONObject("message")
                val messageRelatedQuestions = message?.optJSONArray("related_questions")
                if (messageRelatedQuestions != null && messageRelatedQuestions.length() > 0) {
                    val questions = mutableListOf<String>()
                    for (i in 0 until messageRelatedQuestions.length()) {
                        val question = messageRelatedQuestions.optString(i)
                        if (question.isNotBlank()) {
                            questions.add(question)
                        }
                    }
                    if (questions.isNotEmpty()) {
                        streamingState.relatedQuestions = questions
                        Timber.d("✓ Extracted ${questions.size} related questions from message")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting related questions: ${e.message}")
        }
    }

    /**
     * Extract search images from Perplexity API response
     */
    private fun extractSearchImages(jsonObject: JSONObject, streamingState: StreamingState) {
        try {
            // Look for images in the response
            val imagesArray = jsonObject.optJSONArray("images")
            if (imagesArray != null && imagesArray.length() > 0) {
                val images = mutableListOf<SearchImage>()
                for (i in 0 until imagesArray.length()) {
                    val imageObj = imagesArray.optJSONObject(i)
                    if (imageObj != null) {
                        val url = imageObj.optString("url")
                        if (url.isNotBlank()) {
                            images.add(SearchImage(
                                url = url,
                                alt = imageObj.optString("alt").takeIf { it.isNotBlank() },
                                width = imageObj.optInt("width").takeIf { it > 0 },
                                height = imageObj.optInt("height").takeIf { it > 0 },
                                thumbnail = imageObj.optString("thumbnail").takeIf { it.isNotBlank() },
                                title = imageObj.optString("title").takeIf { it.isNotBlank() },
                                source = imageObj.optString("source").takeIf { it.isNotBlank() }
                            ))
                        }
                    }
                }
                if (images.isNotEmpty()) {
                    streamingState.searchImages = images
                    Timber.d("✓ Extracted ${images.size} search images from Perplexity response")
                }
            }

            // Also check in choices array (alternative location)
            val choices = jsonObject.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                
                // Check in delta
                val delta = choice.optJSONObject("delta")
                val deltaImages = delta?.optJSONArray("images")
                if (deltaImages != null && deltaImages.length() > 0) {
                    val images = mutableListOf<SearchImage>()
                    for (i in 0 until deltaImages.length()) {
                        val imageObj = deltaImages.optJSONObject(i)
                        if (imageObj != null) {
                            val url = imageObj.optString("url")
                            if (url.isNotBlank()) {
                                images.add(SearchImage(
                                    url = url,
                                    alt = imageObj.optString("alt").takeIf { it.isNotBlank() },
                                    width = imageObj.optInt("width").takeIf { it > 0 },
                                    height = imageObj.optInt("height").takeIf { it > 0 },
                                    thumbnail = imageObj.optString("thumbnail").takeIf { it.isNotBlank() },
                                    title = imageObj.optString("title").takeIf { it.isNotBlank() },
                                    source = imageObj.optString("source").takeIf { it.isNotBlank() }
                                ))
                            }
                        }
                    }
                    if (images.isNotEmpty()) {
                        streamingState.searchImages = images
                        Timber.d("✓ Extracted ${images.size} search images from delta")
                    }
                }

                // Check in message
                val message = choice.optJSONObject("message")
                val messageImages = message?.optJSONArray("images")
                if (messageImages != null && messageImages.length() > 0) {
                    val images = mutableListOf<SearchImage>()
                    for (i in 0 until messageImages.length()) {
                        val imageObj = messageImages.optJSONObject(i)
                        if (imageObj != null) {
                            val url = imageObj.optString("url")
                            if (url.isNotBlank()) {
                                images.add(SearchImage(
                                    url = url,
                                    alt = imageObj.optString("alt").takeIf { it.isNotBlank() },
                                    width = imageObj.optInt("width").takeIf { it > 0 },
                                    height = imageObj.optInt("height").takeIf { it > 0 },
                                    thumbnail = imageObj.optString("thumbnail").takeIf { it.isNotBlank() },
                                    title = imageObj.optString("title").takeIf { it.isNotBlank() },
                                    source = imageObj.optString("source").takeIf { it.isNotBlank() }
                                ))
                            }
                        }
                    }
                    if (images.isNotEmpty()) {
                        streamingState.searchImages = images
                        Timber.d("✓ Extracted ${images.size} search images from message")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting search images: ${e.message}")
        }
    }

    /**
     * Extract citations/sources from Perplexity API response
     */
    private fun extractPerplexityCitations(jsonObject: JSONObject, streamingState: StreamingState) {
        try {
            // Look for citations in the response
            val citationsArray = jsonObject.optJSONArray("citations")
            if (citationsArray != null && citationsArray.length() > 0) {
                val sources = mutableListOf<ChatAdapter.WebSearchSource>()
                for (i in 0 until citationsArray.length()) {
                    val citationObj = citationsArray.optJSONObject(i)
                    if (citationObj != null) {
                        val url = citationObj.optString("url")
                        val title = citationObj.optString("title")
                        if (url.isNotBlank() && title.isNotBlank()) {
                            sources.add(ChatAdapter.WebSearchSource(
                                title = title,
                                url = url,
                                snippet = citationObj.optString("snippet", ""),
                                displayLink = citationObj.optString("displayLink") 
                                    ?: extractDomainFromUrl(url),
                                favicon = "https://www.google.com/s2/favicons?domain=${extractDomainFromUrl(url)}&sz=64"
                            ))
                        }
                    }
                }
                if (sources.isNotEmpty()) {
                    streamingState.perplexityCitations = sources
                    Timber.d("✓ Extracted ${sources.size} citations from Perplexity response")
                }
            }

            // Also check for sources in choices array (alternative location)
            val choices = jsonObject.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                
                // Check in message for citations
                val message = choice.optJSONObject("message")
                val messageCitations = message?.optJSONArray("citations")
                if (messageCitations != null && messageCitations.length() > 0) {
                    val sources = mutableListOf<ChatAdapter.WebSearchSource>()
                    for (i in 0 until messageCitations.length()) {
                        val citationObj = messageCitations.optJSONObject(i)
                        if (citationObj != null) {
                            val url = citationObj.optString("url")
                            val title = citationObj.optString("title")
                            if (url.isNotBlank() && title.isNotBlank()) {
                                sources.add(ChatAdapter.WebSearchSource(
                                    title = title,
                                    url = url,
                                    snippet = citationObj.optString("snippet", ""),
                                    displayLink = citationObj.optString("displayLink") 
                                        ?: extractDomainFromUrl(url),
                                    favicon = "https://www.google.com/s2/favicons?domain=${extractDomainFromUrl(url)}&sz=64"
                                ))
                            }
                        }
                    }
                    if (sources.isNotEmpty()) {
                        streamingState.perplexityCitations = sources
                        Timber.d("✓ Extracted ${sources.size} citations from message")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting Perplexity citations: ${e.message}")
        }
    }

    /**
     * Extract domain from URL for display purposes
     */
    private fun extractDomainFromUrl(url: String): String {
        return try {
            val uri = url.toUri()
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }


    private fun hasToolCalls(jsonObject: JSONObject): Boolean {
        try {
            val choices = jsonObject.optJSONArray("choices") ?: return false
            if (choices.length() == 0) return false

            // Check all possible locations for tool calls
            for (i in 0 until choices.length()) {
                val choice = choices.getJSONObject(i)

                // Check finish_reason first - this is the most reliable indicator
                val finishReason = choice.optString("finish_reason")
                if (finishReason == "tool_calls") {
                    Timber.d("Found tool call completion indicator in finish_reason")
                    return true
                }

                // Check delta for streaming tool calls
                val delta = choice.optJSONObject("delta")
                if (delta != null && delta.has("tool_calls")) {
                    val toolCalls = delta.optJSONArray("tool_calls")
                    if (toolCalls != null && toolCalls.length() > 0) {
                        // This is a streaming tool call chunk
                        Timber.d("Found streaming tool_calls in delta")
                        return true
                    }
                }

                // Check message for complete tool calls
                val message = choice.optJSONObject("message")
                if (message != null && message.has("tool_calls")) {
                    val toolCalls = message.optJSONArray("tool_calls")
                    if (toolCalls != null && toolCalls.length() > 0) {
                        Timber.d("Found complete tool_calls in message")
                        return true
                    }
                }

                // Check for function_call (older OpenAI format)
                if (delta?.has("function_call") == true || message?.has("function_call") == true) {
                    Timber.d("Found function_call (legacy format)")
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            Timber.e(e, "Error checking for tool calls: ${e.message}")
            return false
        }
    }

    /**
     * Check if the model supports thinking/reasoning content
     */
    private fun isThinkingCapableModel(modelId: String): Boolean {
        return when {
            ModelValidator.isClaudeModel(modelId) -> true  // Claude 3.7+ supports thinking
            modelId.contains("deepseek", ignoreCase = true) -> true  // DeepSeek R1 supports reasoning
            modelId.contains("zhipu", ignoreCase = true) -> true  // ZhiPu GLM-4.5 supports thinking
            // NOTE: Qwen removed - doesn't provide separate thinking content fields
            // modelId.contains("qwen", ignoreCase = true) -> true  // Qwen mixes thinking with response
            else -> false
        }
    }

    /**
     * Extract thinking content from supported models (Claude 3.7, DeepSeek R1, ZhiPu GLM-4.5)
     */
    private fun extractThinkingContent(jsonObject: JSONObject, modelId: String): String {
        return try {
            val choices = jsonObject.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""

            val choice = choices.getJSONObject(0)

            when {
                // CLAUDE 3.7: Uses "reasoning_content" for thinking
                ModelValidator.isClaudeModel(modelId) -> {
                    val reasoningContent = choice.optJSONObject("delta")?.optString("reasoning_content")
                        ?: choice.optJSONObject("message")?.optString("reasoning_content")
                        ?: choice.optString("reasoning_content")
                    
                    if (!reasoningContent.isNullOrEmpty()) {
                        Timber.v("🧠 Claude thinking: ${reasoningContent.length} chars")
                        return reasoningContent
                    }
                }

                // DEEPSEEK R1: Uses "reasoning_content" for thinking
                modelId.contains("deepseek", ignoreCase = true) -> {
                    val reasoningContent = choice.optJSONObject("delta")?.optString("reasoning_content")
                        ?: choice.optJSONObject("message")?.optString("reasoning_content")
                        ?: choice.optString("reasoning_content")
                    
                    if (!reasoningContent.isNullOrEmpty()) {
                        Timber.v("🧠 DeepSeek thinking: ${reasoningContent.length} chars")
                        return reasoningContent
                    }
                }


                // ZHIPU GLM-4.5: May use "thinking_content", "reasoning_content", or "thought_content"
                modelId.contains("zhipu", ignoreCase = true) -> {
                    val thinkingContent = choice.optJSONObject("delta")?.optString("thinking_content")
                        ?: choice.optJSONObject("delta")?.optString("reasoning_content")
                        ?: choice.optJSONObject("delta")?.optString("thought_content")
                        ?: choice.optJSONObject("message")?.optString("thinking_content")
                        ?: choice.optJSONObject("message")?.optString("reasoning_content")
                        ?: choice.optJSONObject("message")?.optString("thought_content")
                        ?: choice.optString("thinking_content")
                        ?: choice.optString("reasoning_content")
                        ?: choice.optString("thought_content")
                    
                    if (!thinkingContent.isNullOrEmpty()) {
                        Timber.d("🧠 ZhiPu thinking found: ${thinkingContent.length} chars - '${thinkingContent.take(100)}...'")
                        return thinkingContent
                    } else {
                        // Debug: Log available fields for ZhiPu when no thinking content found
                        val deltaFields = choice.optJSONObject("delta")?.keys()?.asSequence()?.toList()
                        val messageFields = choice.optJSONObject("message")?.keys()?.asSequence()?.toList()
                        val choiceFields = choice.keys().asSequence().toList()
                        Timber.d("🧠 ZhiPu no thinking - delta: $deltaFields, message: $messageFields, choice: $choiceFields")
                    }
                }
            }

            return ""
        } catch (e: Exception) {
            Timber.e(e, "🧠 Error extracting thinking content for $modelId: ${e.message}")
            ""
        }
    }

    private fun extractClaudeThinkingContent(jsonObject: JSONObject): String {
        return try {
            val choices = jsonObject.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""

            val choice = choices.getJSONObject(0)

            // CORRECT: Claude uses "reasoning_content" for thinking content
            val reasoningContent = choice.optJSONObject("delta")?.optString("reasoning_content")
            if (!reasoningContent.isNullOrEmpty()) {
                Timber.d("Found Claude reasoning_content: ${reasoningContent.length} chars - '$reasoningContent'")
                return reasoningContent
            }

            // Also check message object (fallback)
            val messageReasoning = choice.optJSONObject("message")?.optString("reasoning_content")
            if (!messageReasoning.isNullOrEmpty()) {
                Timber.d("Found Claude reasoning in message: ${messageReasoning.length} chars")
                return messageReasoning
            }

            // Direct check (fallback)
            val directReasoning = choice.optString("reasoning_content")
            if (!directReasoning.isNullOrEmpty()) {
                Timber.d("Found Claude reasoning direct: ${directReasoning.length} chars")
                return directReasoning
            }

            return ""
        } catch (e: Exception) {
            Timber.e(e, "Error extracting Claude reasoning content: ${e.message}")
            ""
        }
    }

    private fun extractContentAdvanced(jsonObject: JSONObject, modelId: String): String {
        return try {
            val choices = jsonObject.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""

            val choice = choices.getJSONObject(0)

            // IMPORTANT: Log the full choice structure for debugging
            Timber.d("Choice structure for $modelId: $choice")

            // CLAUDE SPECIFIC: Handle Claude's response format
            if (ModelValidator.isClaudeModel(modelId)) {
                return extractClaudeContent(choice)
            }

            // DEEPSEEK SPECIFIC: Only extract regular content, not reasoning content
            // (reasoning content is handled separately in extractThinkingContent)
            if (modelId.contains("deepseek", ignoreCase = true)) {
                val regularContent = choice.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() }
                if (regularContent != null) {
                    Timber.v("Extracted DeepSeek regular content: $regularContent")
                    return regularContent
                }
            }

            // QWEN SPECIFIC: Only extract regular content, not thinking content
            // (thinking content is handled separately in extractThinkingContent)
            if (modelId.contains("qwen", ignoreCase = true)) {
                val regularContent = choice.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() }
                if (regularContent != null) {
                    Timber.v("Extracted Qwen regular content: $regularContent")
                    return regularContent
                }
            }

            // STANDARD EXTRACTION: Try common paths for other models
            val standardContent = extractStandardContent(choice)
            if (standardContent.isNotEmpty()) {
                Timber.v("Extracted standard content: $standardContent")
                return standardContent
            }

            // FALLBACK: Try alternative extraction methods
            return extractFallbackContent(choice, modelId)

        } catch (e: Exception) {
            Timber.e(e, "Error extracting content for model $modelId: ${e.message}")
            ""
        }
    }
    // In StreamingHandler.kt - Update extractStandardContent
    private fun extractStandardContent(choice: JSONObject): String {
        return try {
            // Try standard OpenAI-style paths
            val content = choice.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" } ?:
            choice.optJSONObject("message")?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" } ?: choice.optString("text")
                .takeIf { it.isNotEmpty() && it != "null" }
            ?:
            choice.optJSONObject("delta")?.optJSONObject("content")?.optString("text")?.takeIf { it.isNotEmpty() && it != "null" } ?:
            ""

            // Don't return "null" string
            if (content == "null") "" else content
        } catch (e: Exception) {
            Timber.e(e, "Error extracting standard content: ${e.message}")
            ""
        }
    }
    private fun extractClaudeContent(choice: JSONObject): String {
        return try {
            // CLAUDE PRIORITY ORDER:
            // 1. Regular content in delta
            val deltaContent = choice.optJSONObject("delta")?.optString("content")
            if (!deltaContent.isNullOrEmpty()) {
                Timber.d("Found Claude delta content: ${deltaContent.length} chars")
                return deltaContent
            }

            // 2. Content in message
            val messageContent = choice.optJSONObject("message")?.optString("content")
            if (!messageContent.isNullOrEmpty()) {
                Timber.d("Found Claude message content: ${messageContent.length} chars")
                return messageContent
            }

            // 3. Direct text field
            val textContent = choice.optString("text")
            if (!textContent.isNullOrEmpty()) {
                Timber.d("Found Claude text content: ${textContent.length} chars")
                return textContent
            }

            // 4. Check for empty content field (normal for some chunks)
            val delta = choice.optJSONObject("delta")
            if (delta?.has("content") == true) {
                val content = delta.getString("content")
                if (content.isEmpty()) {
                    // This is normal for initial/final chunks
                    Timber.v("Empty content field in delta - normal for Claude")
                    return ""
                }
            }

            // 5. Final check - log what we actually have
            Timber.d("No content found in Claude response. Available fields in choice: ${choice.keys().asSequence().toList()}")
            if (choice.has("delta")) {
                val deltaObj = choice.getJSONObject("delta")
                Timber.d("Available fields in delta: ${deltaObj.keys().asSequence().toList()}")
            }

            return ""
        } catch (e: Exception) {
            Timber.e(e, "Error extracting Claude content: ${e.message}")
            ""
        }
    }

    private fun extractFallbackContent(choice: JSONObject, modelId: String): String {
        return try {
            // Try various fallback paths based on model
            when {
                modelId.contains("qwen", ignoreCase = true) -> {
                    choice.optJSONObject("delta")?.optString("content") ?:
                    choice.optString("content") ?:
                    choice.optJSONObject("message")?.optString("text") ?:
                    ""
                }
                else -> {
                    // Generic fallback
                    choice.optString("content") ?:
                    choice.optJSONObject("output")?.optString("text") ?:
                    ""
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in fallback content extraction: ${e.message}")
            ""
        }
    }


    // ULTRA-FAST REAL-TIME: Optimized UI updates for maximum streaming fluidity and first-token speed
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun updateStreamingUIRealTime(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState,
        isFirstUpdate: Boolean = false
    ) {
        withContext(Dispatchers.Main) {
            try {
                adapter.currentList.getOrNull(messagePosition)?.let { message ->
                    val currentContent = streamingState.mainContent.toString()
                    
                    // FIRST TOKEN OPTIMIZATION: Always update immediately for first content
                    if (isFirstUpdate && currentContent.isNotEmpty()) {
                        Timber.d("🚀 FIRST TOKEN IMMEDIATE: ${currentContent.length} chars")
                        
                        adapter.updateStreamingContentDirect(
                            messageId = message.id,
                            content = currentContent,
                            processMarkdown = true,
                            isStreaming = true
                        )
                        
                        streamingState.lastMainContentLength = currentContent.length
                        streamingState.lastContentUpdateTime = System.currentTimeMillis()
                        return@withContext
                    }
                    
                    // REAL-TIME UPDATES: Lower threshold for ultra-responsive streaming
                    val contentGrowth = currentContent.length - streamingState.lastMainContentLength
                    val timeSinceLastUpdate = System.currentTimeMillis() - streamingState.lastContentUpdateTime
                    
                    // OPTIMIZED BATCHING: Much lower thresholds for fluid real-time experience
                    val shouldUpdate = when {
                        // Always update if significant content growth
                        contentGrowth >= CONTENT_GROWTH_THRESHOLD -> true
                        // Update for any content change if enough time has passed (60 FPS target)
                        contentGrowth > 0 && timeSinceLastUpdate >= UI_UPDATE_INTERVAL_MS -> true
                        // Force update if too much time has passed (prevents stalling)
                        timeSinceLastUpdate >= 100L -> true
                        else -> false
                    }
                    
                    if (shouldUpdate) {
                        // REAL-TIME MARKDOWN: Process markdown during streaming with performance optimization
                        adapter.updateStreamingContentDirect(
                            messageId = message.id,
                            content = currentContent,
                            processMarkdown = true,
                            isStreaming = true
                        )
                        
                        streamingState.lastMainContentLength = currentContent.length
                        streamingState.lastContentUpdateTime = System.currentTimeMillis()
                        
                        Timber.v("📝 Real-time update: ${currentContent.length} chars (+${contentGrowth})")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to update streaming UI in real-time")
            }
        }
    }    // In StreamingHandler.kt - Update finalizeStreaming method
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun finalizeStreaming(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState,
        context: Context?
    ) {
        try {
            val message = adapter.currentList.getOrNull(messagePosition) ?: return

            val finalContent = streamingState.mainContent.toString().trim()

            // Complete streaming session
            UnifiedStreamingManager.completeSession(streamingState.messageId, true)
            
            // CRITICAL FIX: Complete the StreamingCancellationManager session as well
            streamingState.streamingSession.streamId.let { streamId ->
                StreamingCancellationManager.cancelStream(streamId, "Streaming completed successfully")
            }

            // Complete thinking if we have thinking content
            if (streamingState.thinkingContent.isNotEmpty()) {
                adapter.completeThinkingForMessage(streamingState.messageId)
                Timber.d("🧠 Completed thinking for message: ${streamingState.messageId}")
            }
            
            // Record API usage data if available
            if (streamingState.usageData != null && streamingState.conversationId.isNotEmpty()) {
                try {
                    // Create a minimal AimlApiResponse with usage data
                    val apiResponse = AimlApiResponse(
                        choices = listOf(
                            AimlApiResponse.Choice(
                                message = AimlApiResponse.Message(
                                    role = "assistant",
                                    content = finalContent
                                )
                            )
                        ),
                        usage = streamingState.usageData
                    )
                    
                    // Get context from function parameter (passed from MainActivity)
                    
                    if (context != null) {
                        val tokenManager = SimplifiedTokenManager(context)
                        val isSubscribed = com.cyberflux.qwinai.utils.PrefsManager.isSubscribed(context)
                        CoroutineScope(Dispatchers.IO).launch {
                            tokenManager.recordApiResponse(
                                conversationId = streamingState.conversationId,
                                modelId = streamingState.modelId,
                                apiResponse = apiResponse,
                                isSubscribed = isSubscribed
                            )
                        }
                        Timber.d("📊 RECORDED usage data: ${streamingState.usageData}")
                    } else {
                        Timber.w("⚠️ Could not get context to record usage data")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error recording API usage: ${e.message}")
                }
            }

            val completedMessage = message.copy(
                message = finalContent,
                isGenerating = false,
                isLoading = false,
                showButtons = true,
                isWebSearchActive = false,
                webSearchResults = streamingState.webSearchContent.toString(),
                hasWebSearchResults = streamingState.searchResults?.isNotEmpty() == true,
                // Update thinking-related fields
                hasThinkingProcess = streamingState.thinkingContent.isNotEmpty(),
                thinkingProcess = streamingState.thinkingContent.toString(),
                isThinkingActive = false
            )

            withContext(Dispatchers.Main) {
                // Update the message first
                val currentList = adapter.currentList.toMutableList()
                if (messagePosition < currentList.size) {
                    currentList[messagePosition] = completedMessage
                    adapter.submitList(currentList)
                }

                // CRITICAL: Remove all loading indicators after streaming completes
                adapter.updateLoadingStateDirect(
                    messageId = completedMessage.id,
                    isGenerating = false,
                    isWebSearching = false,
                    customStatusText = null,
                    customStatusColor = null
                )

                // Stop streaming mode gradually
                delay(50) // Short delay for content to settle
                adapter.stopStreamingModeGradually()

                // Trigger final update with ENSURE_CODE_BLOCKS_PERSIST payload
                adapter.notifyItemChanged(messagePosition, "ENSURE_CODE_BLOCKS_PERSIST")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error finalizing streaming: ${e.message}")
        }
    }
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun processEnhancedStreamingResponse(
        responseBody: ResponseBody,
        adapter: ChatAdapter,
        messagePosition: Int,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
        isDeepSearchEnabled: Boolean = false,
        context: Context? = null,
        apiService: AimlApiService? = null,
        modelId: String = ""
    ) {
        processStreamingResponse(
            responseBody = responseBody,
            adapter = adapter,
            messagePosition = messagePosition,
            onComplete = onComplete,
            onError = onError,
            isWebSearchEnabled = isDeepSearchEnabled,
            context = context,
            apiService = apiService,
            modelId = modelId
        )
    }

    // In StreamingHandler.kt - Replace processToolCallData method
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processToolCallData(
        jsonObject: JSONObject,
        streamingState: StreamingState,
        context: Context?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        try {
            val choices = jsonObject.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                Timber.w("No choices found in tool call data")
                return
            }

            val choice = choices.getJSONObject(0)
            val delta = choice.optJSONObject("delta")
            val message = choice.optJSONObject("message")
            val finishReason = choice.optString("finish_reason")

            // Check if this is streaming tool call data
            val deltaToolCalls = delta?.optJSONArray("tool_calls")
            if (deltaToolCalls != null && deltaToolCalls.length() > 0) {
                processStreamingToolCallChunks(deltaToolCalls, streamingState)
            }

            // Check if this is a complete tool call
            val messageToolCalls = message?.optJSONArray("tool_calls")
            if (messageToolCalls != null && messageToolCalls.length() > 0) {
                processCompleteToolCalls(messageToolCalls, streamingState, context
                )
                return
            }

            // IMPORTANT: When we get finish_reason="tool_calls", execute the tools
            if (finishReason == "tool_calls" && streamingState.toolCalls.isNotEmpty()) {
                Timber.d("Finish reason is tool_calls, executing ${streamingState.toolCalls.size} tool calls")

                // Mark tool calls as complete
                streamingState.toolCalls.forEach { it.isComplete = true }

                // Execute tool calls
                for (toolCall in streamingState.toolCalls) {
                    when (toolCall.function.name) {
                        "web_search" -> {
                            // Execute web search and store results
                            executeWebSearchImproved(toolCall, streamingState, adapter)

                            // IMPORTANT: Don't append raw search results to main content
                            // The AI will use the search results to generate a natural response
                            if (streamingState.searchResults?.isNotEmpty() == true) {
                                // Just update UI to show content is being generated
                                adapter?.let {
                                    withContext(Dispatchers.Main) {
                                        val currentMessage = it.currentList.getOrNull(messagePosition)
                                        if (currentMessage != null) {
                                            it.updateLoadingStateDirect(
                                                currentMessage.id,
                                                isGenerating = true,
                                                isWebSearching = false,
                                                customStatusText = null, // No text for generating state
                                                customStatusColor = "#757575".toColorInt()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            executeToolCall(toolCall, streamingState, context,
                                adapter, messagePosition)
                        }
                    }
                }

                streamingState.hasCompletedToolCalls = true

                // IMPORTANT: Don't add raw search results to main content
                // The AI should process the search results and generate a natural response
                // with proper citations. The raw results will be available in webSearchContent
                if (streamingState.searchResults?.isNotEmpty() == true) {
                    Timber.d("Search results available for AI processing: ${streamingState.searchResults?.size} results")
                }

                // Don't try to continue with a new API call - let the current stream complete
                Timber.d("Tool execution complete, continuing with current stream")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing tool calls: ${e.message}")
        }
    }

    private fun executeToolCalls(
        streamingState: StreamingState,
        context: Context?
    ) {
        if (context == null || streamingState.toolCalls.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                streamingState.awaitingToolResponse = true

                for (toolCall in streamingState.toolCalls) {
                    when (toolCall.function.name) {
                        "web_search" -> {
                            executeWebSearch(
                                toolCall,
                                streamingState,
                                context
                            )
                        }
                    }
                }

                streamingState.awaitingToolResponse = false
                streamingState.hasCompletedToolCalls = true

            } catch (e: Exception) {
                Timber.e(e, "Error executing tool calls: ${e.message}")
                streamingState.awaitingToolResponse = false
            }
        }
    }

    private fun processStreamingToolCallChunks(
        toolCallsArray: JSONArray,
        streamingState: StreamingState
    ) {
        for (i in 0 until toolCallsArray.length()) {
            val toolCallDelta = toolCallsArray.getJSONObject(i)
            val index = toolCallDelta.optInt("index", 0)

            // Ensure we have enough tool calls in the list
            while (streamingState.toolCalls.size <= index) {
                streamingState.toolCalls.add(
                    ToolCall("", "function", ToolFunction(""), false)
                )
            }

            val toolCall = streamingState.toolCalls[index]

            // Update tool call ID if provided
            toolCallDelta.optString("id").takeIf { it.isNotEmpty() }?.let { id ->
                streamingState.toolCalls[index] = toolCall.copy(id = id)
            }

            // Update tool call type if provided
            toolCallDelta.optString("type").takeIf { it.isNotEmpty() }?.let { type ->
                streamingState.toolCalls[index] = toolCall.copy(type = type)
            }

            // Process function delta
            toolCallDelta.optJSONObject("function")?.let { functionDelta ->
                functionDelta.optString("name").takeIf { it.isNotEmpty() }?.let { name ->
                    streamingState.toolCalls[index] = toolCall.copy(
                        function = toolCall.function.copy(name = name)
                    )
                }

                // Accumulate arguments
                functionDelta.optString("arguments").let { args ->
                    toolCall.function.arguments.append(args)
                }
            }

            Timber.d("Updated tool call $index: id=${streamingState.toolCalls[index].id}, name=${streamingState.toolCalls[index].function.name}, args_length=${streamingState.toolCalls[index].function.arguments.length}")
        }
    }

    // In StreamingHandler.kt - Update executeWebSearchImproved
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun executeWebSearchImproved(
        toolCall: ToolCall,
        streamingState: StreamingState,
        adapter: ChatAdapter?
    ) {
        try {
            // CRITICAL: Prevent multiple simultaneous web search executions
            if (!streamingState.webSearchExecutionLock.compareAndSet(false, true)) {
                Timber.w("⚠️ Web search already in progress, skipping duplicate execution")
                return
            }

            // Check if we already have search results to avoid duplicate searches
            if (streamingState.hasCompletedWebSearch && streamingState.searchResults?.isNotEmpty() == true) {
                Timber.d("✓ Web search already completed with ${streamingState.searchResults?.size} results")
                streamingState.webSearchExecutionLock.set(false)
                return
            }

            val argumentsString = toolCall.function.arguments.toString()
            var query = ""

            // Parse query from arguments
            try {
                if (argumentsString.trim().startsWith("{")) {
                    val jsonArgs = JSONObject(argumentsString)
                    query = jsonArgs.optString("query", "")
                }
            } catch (e: Exception) {
                Timber.w("Failed to parse arguments as JSON: ${e.message}")
            }

            if (query.isBlank()) {
                query = argumentsString.trim('"', ' ', '\n', '\r', '\t')
            }

            // Translate common Romanian terms to improve search results
            query = translateCommonTermsForSearch(query)

            Timber.d("🔍 Executing web search for: '$query'")

            // Update UI to show searching with smooth transition
            streamingState.isWebSearching = true
            streamingState.hasStartedWebSearch = true
            streamingState.webSearchStartTime = System.currentTimeMillis()

            adapter?.let {
                updateStreamingStatus(
                    adapter = it,
                    messageId = streamingState.messageId,
                    statusText = "Web search",
                    statusColor = "#2563EB".toColorInt(),
                    isWebSearching = true,
                    isGenerating = false,
                    streamingState = streamingState
                )
            }

            // Perform the web search asynchronously to avoid blocking streaming
            withContext(Dispatchers.IO) {
                val searchResults = performWebSearchWithService(query)
                
                // Process results on main thread
                withContext(Dispatchers.Main) {
                    processSearchResults(searchResults, streamingState, adapter)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Web search error: ${e.message}")
            streamingState.isWebSearching = false
            streamingState.hasCompletedWebSearch = true
        } finally {
            // CRITICAL: Always release the lock
            streamingState.webSearchExecutionLock.set(false)
        }
    }
    
    private suspend fun processSearchResults(
        searchResults: List<ChatAdapter.WebSearchSource>,
        streamingState: StreamingState,
        adapter: ChatAdapter?
    ) {
        if (searchResults.isNotEmpty()) {
            streamingState.searchResults = searchResults

            // Store results for citations
            val resultsJson = JSONArray()
            searchResults.forEach { source ->
                val sourceJson = JSONObject()
                sourceJson.put("title", source.title)
                sourceJson.put("url", source.url)
                sourceJson.put("snippet", source.snippet)
                sourceJson.put("displayLink", source.displayLink?.ifEmpty {
                    try {
                        source.url.toUri().host ?: source.url
                    } catch (_: Exception) {
                        source.url
                    }
                })
                sourceJson.put("favicon", source.favicon ?: "")
                resultsJson.put(sourceJson)
            }

            streamingState.webSearchContent.clear()
            streamingState.webSearchContent.append(resultsJson.toString())

            // CRITICAL FIX: Don't clear content during active streaming - causes word replacement
            // Instead, only prevent adding duplicate error messages
            if (streamingState.mainContent.toString().contains("unable to access real-time news")) {
                // Remove only the error message, not all content
                val content = streamingState.mainContent.toString()
                val cleanContent = content.replace("unable to access real-time news", "").trim()
                streamingState.mainContent.clear()
                streamingState.mainContent.append(cleanContent)
            }

            Timber.d("Web search completed: ${searchResults.size} results found")

            // Smooth transition to generating state
            adapter?.let {
                updateStreamingStatus(
                    adapter = it,
                    messageId = streamingState.messageId,
                    statusText = null, // No text for generating state
                    statusColor = "#757575".toColorInt(),
                    isWebSearching = false,
                    isGenerating = true,
                    streamingState = streamingState
                )
            }
        } else {
            // No results found
            Timber.d("No web search results found")
        }

        // Update state
        streamingState.isWebSearching = false
        streamingState.hasCompletedWebSearch = true
        streamingState.webSearchEndTime = System.currentTimeMillis()
    }

    private fun translateCommonTermsForSearch(query: String): String {
        // Basic query cleaning - let the AI handle optimization
        return query
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .trim()
            .take(200) // Limit query length
    }
    private fun processCompleteToolCalls(
        toolCallsArray: JSONArray,
        streamingState: StreamingState,
        context: Context?
    ) {
        streamingState.toolCalls.clear()

        for (i in 0 until toolCallsArray.length()) {
            val toolCallObj = toolCallsArray.getJSONObject(i)

            val toolCall = ToolCall(
                id = toolCallObj.getString("id"),
                type = toolCallObj.optString("type", "function"),
                function = ToolFunction(
                    name = toolCallObj.getJSONObject("function").getString("name"),
                    arguments = StringBuilder(
                        toolCallObj.getJSONObject("function").getString("arguments")
                    )
                ),
                isComplete = true
            )

            streamingState.toolCalls.add(toolCall)
        }

        executeToolCalls(streamingState, context)
    }

    @SuppressLint("UseKtx")
    private suspend fun executeToolCall(
        toolCall: ToolCall,
        streamingState: StreamingState,
        context: Context?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        if (context == null) return

        try {
            val toolManager = ToolServiceHelper.getToolManager(context)

            // Set appropriate indicator text
            val (indicatorText, indicatorColor) = when (toolCall.function.name) {
                "web_search" -> Pair("Web search", Color.parseColor("#2563EB"))
                else -> Pair(null, "#757575".toColorInt()) // No text for other tool calls
            }

            // Update streaming state to show proper indicator
            streamingState.isWebSearching = toolCall.function.name == "web_search"

            // Notify UI about the tool status
            adapter?.let {
                withContext(Dispatchers.Main) {
                    val message = it.currentList.getOrNull(messagePosition)
                    if (message != null) {
                        it.updateLoadingStateDirect(
                            message.id,
                            isGenerating = true,
                            isWebSearching = toolCall.function.name == "web_search",
                            customStatusText = indicatorText,
                            customStatusColor = indicatorColor
                        )
                    }
                }
            }

            // Execute the tool via ToolManager
            val argumentsString = toolCall.function.arguments.toString()
            val parameters = parseArgumentsToMap(argumentsString)

            val result = toolManager.executeToolById(
                id = toolCall.function.name,
                message = "", // This would normally be user's message
                parameters = parameters
            )

            if (result.success) {
                streamingState.toolCallResults.append(result.content)

                // Append to main content with appropriate heading
                streamingState.mainContent.append("\n\n**${toolCall.function.name.capitalize(Locale.ROOT)} Results:**\n")
                streamingState.mainContent.append(result.content)
            } else {
                streamingState.mainContent.append("\n\n*Error executing ${toolCall.function.name}: ${result.error}*\n")
            }

            // Update completion state
            if (toolCall.function.name == "web_search") {
                streamingState.hasCompletedWebSearch = true
            }

        } catch (e: Exception) {
            Timber.e(e, "Error executing tool call: ${e.message}")
            streamingState.mainContent.append("\n\n*Error executing tool: ${e.message}*\n")
        } finally {
            // Always reset search/analysis state when done
            streamingState.isWebSearching = false
        }
    }

    private fun parseArgumentsToMap(argumentsJson: String): Map<String, Any> {
        return try {
            if (argumentsJson.trim().startsWith("{") && argumentsJson.trim().endsWith("}")) {
                val jsonObject = JSONObject(argumentsJson)
                val result = mutableMapOf<String, Any>()

                jsonObject.keys().forEach { key ->
                    val value = jsonObject.opt(key)
                    if (value != null && value != JSONObject.NULL) {
                        result[key] = when(value) {
                            is JSONObject -> parseArgumentsToMap(value.toString())
                            is JSONArray -> {
                                val list = mutableListOf<Any>()
                                for (i in 0 until value.length()) {
                                    list.add(value.get(i))
                                }
                                list
                            }
                            else -> value
                        }
                    }
                }

                result
            } else {
                mapOf("value" to argumentsJson.trim())
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing arguments JSON: ${e.message}")
            mapOf("text" to argumentsJson)
        }
    }
    private suspend fun executeWebSearch(
        toolCall: ToolCall,
        streamingState: StreamingState,
        context: Context
    ) {
        try {
            val arguments = toolCall.function.arguments.toString()
            val query = if (arguments.startsWith("{")) {
                JSONObject(arguments).optString("query", arguments)
            } else {
                arguments
            }

            Timber.d("🔍 Executing web search: $query")

            streamingState.isWebSearching = true
            streamingState.hasStartedWebSearch = true
            streamingState.webSearchStartTime = System.currentTimeMillis()

            // Use ToolServiceHelper to execute the web search
            val freshness = determineFreshness(query)
            val toolParams = mutableMapOf<String, Any>(
                "query" to query,
                "count" to 5 as Any
            )

            // Only add freshness if it's not null
            if (freshness != null) {
                toolParams["freshness"] = freshness
            }

            val toolResult = ToolServiceHelper.executeWebSearch(
                context = context,
                message = query,
                parameters = toolParams
            )

            if (toolResult != null && toolResult.success) {
                streamingState.webSearchContent.append(toolResult.content)
                streamingState.toolCallResults.append(toolResult.content)

                streamingState.mainContent.append("\n\n**Web Search Results:**\n")
                streamingState.mainContent.append(toolResult.content)

                Timber.d("Web search completed successfully using ToolServiceHelper")
            } else {
                // Fall back to WebSearchService if the tool fails
                val searchResponse = WebSearchService.performSearch(
                    query = query,
                    freshness = determineFreshness(query),
                    count = 5
                )

                if (searchResponse.success && searchResponse.results.isNotEmpty()) {
                    val formattedResults = WebSearchService.formatSearchResultsForAI(
                        searchResponse.results,
                        query
                    )

                    streamingState.webSearchContent.append(formattedResults)
                    streamingState.toolCallResults.append(formattedResults)

                    streamingState.mainContent.append("\n\n**Web Search Results:**\n")
                    streamingState.mainContent.append(formattedResults)

                    Timber.d("Web search completed successfully using fallback WebSearchService")
                } else {
                    val errorMessage = searchResponse.error ?: "No relevant results found."
                    streamingState.mainContent.append("\n\n**Web Search Note:** $errorMessage\n\n")

                    Timber.d("Web search found no results: $errorMessage")
                }
            }

            streamingState.isWebSearching = false
            streamingState.hasCompletedWebSearch = true
            streamingState.webSearchEndTime = System.currentTimeMillis()

        } catch (e: Exception) {
            Timber.e(e, "Web search error: ${e.message}")
            streamingState.isWebSearching = false
            streamingState.mainContent.append("\n\n*Web search failed: ${e.message}*\n")
        }
    }    private suspend fun handleStreamingError(
        adapter: ChatAdapter,
        messagePosition: Int,
        errorMessage: String
    ) {
        try {
            val message = adapter.currentList.getOrNull(messagePosition) ?: return

            val errorMsg = message.copy(
                message = "Error: $errorMessage\n\nPlease try again.",
                isGenerating = false,
                isLoading = false,
                showButtons = true,
                error = true
            )

            val currentList = adapter.currentList.toMutableList()
            if (messagePosition < currentList.size) {
                currentList[messagePosition] = errorMsg
                adapter.submitList(currentList)
            }

            // CRITICAL: Remove all loading indicators after error
            withContext(Dispatchers.Main) {
                adapter.updateLoadingStateDirect(
                    messageId = errorMsg.id,
                    isGenerating = false,
                    isWebSearching = false,
                    customStatusText = null,
                    customStatusColor = null
                )
                adapter.stopStreamingModeGradually()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error handling streaming error: ${e.message}")
        }
    }

    private fun handleStreamComplete(streamingState: StreamingState) {
        Timber.d("Stream completed signal received")

        if (streamingState.isWebSearching) {
            streamingState.isWebSearching = false
            streamingState.hasCompletedWebSearch = true
            streamingState.webSearchEndTime = System.currentTimeMillis()
        }

        // Check if we have any content at all
        if (streamingState.mainContent.isEmpty() &&
            streamingState.thinkingContent.isEmpty() &&
            streamingState.hasCompletedToolCalls) {
            // This might be a case where tool calls completed but no follow-up content was generated
            Timber.w("Stream completed with tool calls but no content - model may need prompting")

            // Add a fallback message if we have search results but no response
            if (streamingState.searchResults?.isNotEmpty() == true) {
                streamingState.mainContent.append("Based on my web search, here's what I found:\n\n")
                // Add a summary of the search results with proper formatting
                streamingState.searchResults?.forEachIndexed { index, source ->
                    streamingState.mainContent.append("**${index + 1}. ${source.title}**\n")
                    streamingState.mainContent.append("${source.snippet}\n")
                    streamingState.mainContent.append("Source: [${source.displayLink}](${source.url})\n\n")
                }
                
                // Store the search results for proper source display
                val searchResultsJson = buildString {
                    append("[")
                    streamingState.searchResults?.forEachIndexed { index, source ->
                        if (index > 0) append(",")
                        append("{")
                        append("\"title\":\"${source.title.replace("\"", "\\\"")}\",")
                        append("\"url\":\"${source.url}\",")
                        append("\"snippet\":\"${source.snippet.replace("\"", "\\\"")}\",")
                        append("\"displayLink\":\"${source.displayLink}\"")
                        append("}")
                    }
                    append("]")
                }
                streamingState.webSearchContent.clear()
                streamingState.webSearchContent.append(searchResultsJson)
            }
        }
    }

    private fun processRemainingData(streamingState: StreamingState) {
        val remaining = streamingState.accumulationBuffer.toString()
        if (remaining.isNotEmpty()) {
            streamingState.mainContent.append(remaining)
        }
    }

    private fun isValidJson(data: String): Boolean {
        return try {
            when {
                data.trim().startsWith("{") -> JSONObject(data)
                data.trim().startsWith("[") -> JSONArray(data)
                else -> return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun checkFinishReason(jsonObject: JSONObject, streamingState: StreamingState) {
        try {
            val choices = jsonObject.optJSONArray("choices") ?: return
            if (choices.length() == 0) return

            val choice = choices.getJSONObject(0)
            val finishReason = choice.optString("finish_reason")

            when (finishReason) {
                "stop" -> Timber.d("🔧 Finish reason: stop")
                "tool_calls" -> {
                    Timber.d("🔧 Finish reason: tool_calls - SETTING awaitingToolResponse = true")
                    streamingState.awaitingToolResponse = true
                }
                "length" -> Timber.d("🔧 Finish reason: length limit reached")
                else -> if (finishReason.isNotEmpty()) Timber.d("🔧 Finish reason: $finishReason")
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun determineFreshness(query: String): String? {
        val lowerQuery = query.lowercase()

        return when {
            lowerQuery.contains("today") ||
                    lowerQuery.contains("current") ||
                    lowerQuery.contains("latest") ||
                    lowerQuery.contains("now") ||
                    lowerQuery.contains("breaking") ||
                    lowerQuery.contains("live") -> "day"

            lowerQuery.contains("this week") ||
                    lowerQuery.contains("recent") ||
                    lowerQuery.contains("recently") -> "week"

            lowerQuery.contains("this month") ||
                    lowerQuery.contains("last month") -> "month"

            lowerQuery.contains("news") ||
                    lowerQuery.contains("updates") ||
                    lowerQuery.contains("developments") -> "day" // Default news to daily

            else -> null // Let the search service decide
        }
    }
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun updateStreamingStatus(
        adapter: ChatAdapter,
        messageId: String,
        statusText: String?,
        statusColor: Int,
        isWebSearching: Boolean,
        isGenerating: Boolean,
        streamingState: StreamingState
    ) {
        // Cancel any pending status update
        streamingState.statusUpdateJob?.cancel()

        val currentTime = System.currentTimeMillis()
        currentTime - streamingState.lastStatusUpdateTime

        // CRITICAL: Always run UI updates on Main thread
        withContext(Dispatchers.Main) {
            adapter.updateLoadingStateDirect(
                messageId = messageId,
                isGenerating = isGenerating || isWebSearching, // Always show loading during any activity
                isWebSearching = isWebSearching,
                customStatusText = statusText,
                customStatusColor = statusColor
            )
        }
        streamingState.lastStatusUpdateTime = currentTime
    }

    /**
     * Format search results for AI processing
     */
    private fun formatSearchResultsForAI(streamingState: StreamingState): String {
        return if (streamingState.searchResults?.isNotEmpty() == true) {
            buildString {
                appendLine("I found the following web search results:")
                appendLine()
                streamingState.searchResults?.forEachIndexed { index, result ->
                    appendLine("[${index + 1}] ${result.title}")
                    appendLine("URL: ${result.url}")
                    appendLine("Summary: ${result.snippet}")
                    appendLine("Source: ${result.displayLink}")
                    appendLine()
                }
                appendLine("Please provide a comprehensive response based on these search results.")
                appendLine("Use inline citations [1], [2], etc. when referencing specific information.")
                appendLine("Make the response natural and conversational.")
            }
        } else {
            ""
        }
    }

    /**
     * Continue streaming with search results - simplified version to avoid infinite loops
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun continueStreamingWithSearchResults(
        searchResults: String,
        streamingState: StreamingState,
        context: Context,
        apiService: AimlApiService,
        adapter: ChatAdapter,
        messagePosition: Int
    ) {
        try {
            // Get the current message
            val currentMessage = adapter.currentList.getOrNull(messagePosition) ?: return
            
            // Get the original user message that triggered this response
            val originalUserMessage = if (messagePosition > 0) {
                adapter.currentList.getOrNull(messagePosition - 1)?.message ?: "Please provide information"
            } else {
                "Please provide information"
            }
            
            // SIMPLIFIED: Build continuation with search results as context
            val continuationMessages = listOf(
                AimlApiRequest.Message(
                    role = "user",
                    content = "$originalUserMessage\n\nSearch Results:\n$searchResults\n\nPlease provide a comprehensive response based on the search results above, using inline citations [1], [2], etc."
                )
            )

            // Create the continuation request
            val continuationRequest = ModelApiHandler.createRequest(
                modelId = streamingState.modelId,
                messages = continuationMessages,
                isWebSearch = false,
                context = context,
                messageText = null,
                audioEnabled = false,
                isReasoningEnabled = false, // Tool continuation doesn't need reasoning
                reasoningLevel = "low"
            )

            // Create request body
            val requestBody = ModelApiHandler.createRequestBody(
                apiRequest = continuationRequest,
                modelId = streamingState.modelId,
                context = context,
                audioEnabled = false
            )

            Timber.d("🔧 Making continuation request to process search results")
            
            // Continue streaming
            val response = withContext(Dispatchers.IO) {
                apiService.sendRawMessageStreaming(requestBody)
            }

            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    Timber.d("🔧 Continuation request successful, processing response stream")
                    
                    // Continue processing the stream with existing content intact
                    processStream(responseBody, streamingState, context, apiService, adapter, messagePosition)
                }
            } else {
                Timber.e("🔧 Failed to continue streaming with search results: ${response.code()}")
                // Fall back to showing raw results
                showFallbackSearchResults(streamingState)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error continuing stream with search results: ${e.message}")
            // Fall back to showing raw results
            showFallbackSearchResults(streamingState)
        }
    }

    /**
     * Fallback method to show search results when continuation fails
     */
    private fun showFallbackSearchResults(streamingState: StreamingState) {
        if (streamingState.searchResults?.isNotEmpty() == true) {
            val fallbackContent = buildString {
                appendLine("Based on my search, here's what I found:")
                appendLine()
                streamingState.searchResults?.forEachIndexed { index, result ->
                    appendLine("**${index + 1}. ${result.title}**")
                    appendLine(result.snippet)
                    appendLine("Source: [${result.displayLink}](${result.url})")
                    appendLine()
                }
            }
            // CRITICAL FIX: Don't clear content during streaming - append fallback instead
            if (streamingState.mainContent.isEmpty()) {
                streamingState.mainContent.append(fallbackContent)
            } else {
                // Only append if we don't already have content to avoid duplication
                streamingState.mainContent.append("\n\n").append(fallbackContent)
            }
        }
    }

    data class StreamingState(
        // Content tracking - ADDED thinkingContent for Claude
        var mainContent: StringBuilder = StringBuilder(),
        var thinkingContent: StringBuilder = StringBuilder(),
        var webSearchContent: StringBuilder = StringBuilder(),
        var toolCallResults: StringBuilder = StringBuilder(),
        var accumulationBuffer: StringBuilder = StringBuilder(),
        
        // Streaming session for cancellation management
        var streamingSession: StreamingCancellationManager.StreamingSession = StreamingCancellationManager.createStreamingSession("default"),

        // Perplexity related questions tracking
        var relatedQuestions: List<String>? = null,
        
        // Perplexity search images tracking
        var searchImages: List<SearchImage>? = null,
        
        // Perplexity citations/sources tracking
        var perplexityCitations: List<ChatAdapter.WebSearchSource>? = null,

        var lastStatusUpdateTime: Long = 0,
        var pendingStatusUpdate: StatusUpdate? = null,
        var statusUpdateJob: Job? = null,

        // State flags
        var isWebSearching: Boolean = false,
        var isProcessingToolCalls: Boolean = false,
        var hasStartedWebSearch: Boolean = false,
        var hasCompletedWebSearch: Boolean = false,
        var hasCompletedThinking: Boolean = false,  // Track if thinking phase is completed
        var isInThinkingPhase: Boolean = false,  // Track if currently in thinking phase
        var isCancelled: Boolean = false,
        var hasReceivedContent: Boolean = false,

        var hasDetectedToolCalls: Boolean = false,
        var extractedToolCallData: Boolean = false,

        // Message tracking - ADD THIS
        var messageId: String = "",
        var conversationId: String = "",
        // Timing
        var webSearchStartTime: Long = 0,
        var webSearchEndTime: Long = 0,
        var lastUpdateTime: Long = 0,
        var streamStartTime: Long = System.currentTimeMillis(),
        var isWebSearchEnabled: Boolean = false,  // NEW: Flag to track whether web search is enabled
        var searchResults: List<ChatAdapter.WebSearchSource>? = null,
        var citedSources: MutableSet<String> = mutableSetOf(),
        // Tool calls
        var toolCalls: MutableList<ToolCall> = mutableListOf(),
        var currentToolCallIndex: Int = -1,
        var awaitingToolResponse: Boolean = false,
        var hasCompletedToolCalls: Boolean = false,

        // Model info
        var modelId: String = "",
        
        // API usage data for token tracking
        var usageData: AimlApiResponse.Usage? = null,

        // UI state tracking  
        var updateCount: Int = 0,
        
        // CRITICAL: Add execution lock to prevent multiple simultaneous searches
        var webSearchExecutionLock: AtomicBoolean = AtomicBoolean(false),
        
        // UI update debouncing  
        var pendingContentUpdate: Job? = null,
        
        // Real-time markdown processing
        var lastProcessedMarkdownLength: Int = 0,
        var markdownProcessingEnabled: Boolean = true,
        
        // ULTRA-SMOOTH: Content batching for flicker-free streaming
        var lastMainContentLength: Int = 0,
        var lastContentUpdateTime: Long = 0L
    )

    data class ToolCall(
        val id: String,
        val type: String,
        val function: ToolFunction,
        var isComplete: Boolean = false
    )

    data class ToolFunction(
        val name: String,
        var arguments: StringBuilder = StringBuilder()
    )
    data class StatusUpdate(
        val text: String,
        val color: Int,
        val isWebSearching: Boolean,
        val isGenerating: Boolean
    )
    data class ToolCallResult(
        val toolCallId: String,
        val functionName: String,
        val content: String
    )
    
    data class SearchImage(
        val url: String,
        val alt: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val thumbnail: String? = null,
        val title: String? = null,
        val source: String? = null
    )
    
    /**
     * Process streaming response in background without UI updates
     * Saves progress to database for later retrieval
     */
    suspend fun processBackgroundStreamingResponse(
        responseBody: ResponseBody,
        messageId: String,
        database: AppDatabase,
        onProgress: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Load existing partial content from database
        val existingMessage = database.chatMessageDao().getMessageById(messageId)
        val existingContent = existingMessage?.message ?: ""
        val accumulatedContent = StringBuilder(existingContent)
        var lastSavedLength = existingContent.length
        
        try {
            Timber.d("🔄 Starting background streaming for message: $messageId with existing content: ${existingContent.length} chars")
            
            // If we have existing content, immediately update the UI
            if (existingContent.isNotEmpty()) {
                onProgress(existingContent)
                Timber.d("📤 Sending existing content to UI: ${existingContent.length} chars")
            }
            
            withContext(Dispatchers.IO) {
                val source = responseBody.source()
                val buffer = Buffer()
                
                while (!source.exhausted()) {
                    // Check if coroutine is still active
                    if (!coroutineContext.isActive) {
                        Timber.d("⏹️ Background streaming cancelled for message: $messageId")
                        break
                    }
                    
                    source.read(buffer, STANDARD_BUFFER_SIZE.toLong())
                    val chunk = buffer.readUtf8()
                    
                    processStreamingChunk(chunk) { content ->
                        if (content.isNotBlank()) {
                            accumulatedContent.append(content)
                            
                            // Save progress to database periodically (every 50 characters)
                            if (accumulatedContent.length - lastSavedLength >= 50) {
                                val currentContent = accumulatedContent.toString()
                                onProgress(currentContent)
                                lastSavedLength = accumulatedContent.length
                            }
                        }
                    }
                    
                    // Minimal delay to prevent overwhelming the system
                    delay(2)
                }
            }
            
            val finalContent = accumulatedContent.toString()
            
            // Save final result to database
            saveCompletedMessageToDatabase(database, messageId, finalContent)
            onComplete(finalContent)
            
            Timber.d("✅ Background streaming completed for message: $messageId")
            
        } catch (e: CancellationException) {
            Timber.d("⏹️ Background streaming cancelled: ${e.message}")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in background streaming: ${e.message}")
            onError("Background generation failed: ${e.message}")
        }
    }

    private suspend fun saveCompletedMessageToDatabase(database: AppDatabase, messageId: String, content: String) {
        try {
            val message = database.chatMessageDao().getMessageById(messageId)
            if (message != null) {
                val completedMessage = message.copy(
                    message = content,
                    isGenerating = false,
                    showButtons = true,
                    lastModified = System.currentTimeMillis(),
                    completionTime = System.currentTimeMillis()
                )
                database.chatMessageDao().update(completedMessage)
                Timber.d("💾 Saved completed message to database: $messageId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving completed message to database: ${e.message}")
        }
    }
    
    private fun processStreamingChunk(chunk: String, onContent: (String) -> Unit) {
        // Process SSE format: data: {...}
        val lines = chunk.split("\n")
        
        for (line in lines) {
            if (line.startsWith("data: ")) {
                val jsonData = line.substring(6).trim()
                
                if (jsonData == "[DONE]") {
                    break
                }
                
                try {
                    val jsonObject = JSONObject(jsonData)
                    val choices = jsonObject.optJSONArray("choices")
                    
                    if (choices != null && choices.length() > 0) {
                        val choice = choices.getJSONObject(0)
                        val delta = choice.optJSONObject("delta")
                        
                        if (delta != null && delta.has("content")) {
                            val content = delta.getString("content")
                            onContent(content)
                        }
                    }
                } catch (_: Exception) {
                    // Skip invalid JSON chunks
                    continue
                }
            }
        }
    }



}
