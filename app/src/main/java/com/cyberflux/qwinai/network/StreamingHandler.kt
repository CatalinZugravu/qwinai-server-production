package com.cyberflux.qwinai.network

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.service.WebSearchService
import com.cyberflux.qwinai.tools.ToolServiceHelper
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.StreamingPerformanceMonitor
import com.cyberflux.qwinai.utils.StreamingConfig
import com.cyberflux.qwinai.utils.StreamingStateManager
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



object StreamingHandler {

    // Constants - Using centralized configuration for optimal performance
    private val BUFFER_SIZE = StreamingConfig.STREAM_BUFFER_SIZE
    private val UI_UPDATE_INTERVAL = StreamingConfig.MIN_UPDATE_INTERVAL 
    private val MARKDOWN_PROCESSING_THRESHOLD = StreamingConfig.IMMEDIATE_PROCESSING_THRESHOLD
    private val ANTI_FLICKER_MIN_CHANGE = StreamingConfig.ANTI_FLICKER_MIN_CHANGE

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun processStreamingResponse(
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
        if (!StreamingConfig.isStreamingContinuationEnabled) {
            Timber.d("🔒 Streaming continuation disabled, using legacy processing")
            onError("Streaming continuation feature is disabled")
            return
        }
        
        // PRODUCTION: Circuit breaker check
        if (StreamingConfig.isCircuitBreakerOpen()) {
            Timber.w("⚠️ Streaming circuit breaker is OPEN, blocking new streaming requests")
            onError("Streaming service temporarily unavailable")
            return
        }
        Timber.d("🚀 Starting streaming for model: $modelId, position: $messagePosition")

        // IMPORTANT: Always log the actual web search status
        val actualWebSearchEnabled = isWebSearchEnabled
        Timber.d("WebSearch: $actualWebSearchEnabled")

        CoroutineScope(Dispatchers.IO).launch {
            val streamingState = StreamingState(
                modelId = modelId,
                isWebSearchEnabled = actualWebSearchEnabled,  // NEW: Store the flag in streaming state
                webSearchExecutionLock = AtomicBoolean(false) // NEW: Add lock to prevent duplicate search executions
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
                    while (isProcessing.get() && !streamingState.isCancelled) {
                        // Ultra-fast UI updates with real-time markdown processing
                        updateStreamingUIWithMarkdownOptimized(adapter, messagePosition, streamingState)
                        
                        // ADAPTIVE: Use different intervals based on content type
                        val contentLength = streamingState.mainContent.length
                        val hasTable = StreamingConfig.requiresTableHandling(streamingState.mainContent.toString())
                        val adaptiveInterval = StreamingConfig.getAdaptiveUpdateInterval(hasTable, contentLength)
                        
                        delay(adaptiveInterval)
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
                uiUpdateJob?.cancel()

                withContext(Dispatchers.Main) {
                    finalizeStreaming(adapter, messagePosition, streamingState)
                    onComplete()
                }

            } catch (e: CancellationException) {
                Timber.d("Streaming cancelled")
                isProcessing.set(false)
                uiUpdateJob?.cancel()

            } catch (e: Exception) {
                Timber.e(e, "Streaming error: ${e.message}")
                isProcessing.set(false)
                uiUpdateJob?.cancel()

                withContext(Dispatchers.Main) {
                    handleStreamingError(adapter, messagePosition, e.message ?: "Unknown error")
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }
    // In StreamingHandler.kt - Update initializeStreaming method
    private suspend fun initializeStreaming(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState,
        modelId: String,
        isWebSearchEnabled: Boolean
    ) {
        val message = adapter.currentList.getOrNull(messagePosition) ?: return

        streamingState.modelId = modelId
        streamingState.messageId = message.id // ADD THIS
        streamingState.conversationId = message.conversationId // ADD THIS
        
        // CRITICAL: Start streaming session in state manager
        val session = StreamingStateManager.startStreamingSession(
            messageId = message.id,
            conversationId = message.conversationId,
            modelId = modelId
        )

        val initialWebSearch = isWebSearchEnabled || message.isForceSearch

        if (initialWebSearch) {
            streamingState.isWebSearching = true
            streamingState.hasStartedWebSearch = true
            streamingState.webSearchStartTime = System.currentTimeMillis()
        }

        val initialMessage = message.copy(
            isGenerating = !initialWebSearch,
            isLoading = true,
            isWebSearchActive = initialWebSearch,
            message = ""
        )

        val currentList = adapter.currentList.toMutableList()
        if (messagePosition < currentList.size) {
            currentList[messagePosition] = initialMessage
            adapter.submitList(currentList)
        }
        adapter.startStreamingMode()
        Timber.d("Initialized - WebSearch: $initialWebSearch")
    }    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
            while (!source.exhausted() && !streamingState.isCancelled) {
                if (!coroutineContext.isActive) {
                    streamingState.isCancelled = true
                    break
                }

                val bytesRead = source.read(buffer, BUFFER_SIZE.toLong())
                if (bytesRead > 0) {
                    val chunk = buffer.readUtf8()
                    processChunkDirectly(
                        chunk,
                        streamingState,
                        context,
                        apiService,
                        adapter,
                        messagePosition
                    )
                }
            }
        }

        if (streamingState.accumulationBuffer.isNotEmpty()) {
            processRemainingData(streamingState)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
                        handleStreamComplete(streamingState)
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
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
                Timber.d("✓ Processing tool call data for model: ${streamingState.modelId}")
                processToolCallData(jsonObject, streamingState, context, apiService, adapter, messagePosition)

                // Check if tool calls are complete
                val finishReason = jsonObject.optJSONArray("choices")?.getJSONObject(0)?.optString("finish_reason")
                if (finishReason == "tool_calls" && streamingState.toolCalls.isNotEmpty()) {
                    // Execute tool calls and wait for results
                    executeToolCallsAndContinue(streamingState, context, apiService, adapter, messagePosition)
                    return
                }
                return
            }

            // Extract Claude thinking content if applicable
            if (ModelValidator.isClaudeModel(streamingState.modelId)) {
                val thinkingContent = extractClaudeThinkingContent(jsonObject)
                if (thinkingContent.isNotEmpty()) {
                    streamingState.thinkingContent.append(thinkingContent)
                    streamingState.hasReceivedContent = true
                }
            }

            // Extract regular content
            val extractedContent = extractContentAdvanced(jsonObject, streamingState.modelId)
            if (extractedContent.isNotEmpty()) {
                streamingState.hasReceivedContent = true
                streamingState.mainContent.append(extractedContent)
                
                // CRITICAL: Update streaming state for continuation (with memory protection)
                val currentContent = streamingState.mainContent.toString()
                if (currentContent.length < 50_000) { // Only update if content is reasonable size
                    StreamingStateManager.updateStreamingContent(
                        streamingState.messageId, 
                        currentContent
                    )
                } else {
                    Timber.w("Skipping streaming state update - content too large: ${currentContent.length} chars")
                }

                // Process citations in real-time if we have search results
                if (streamingState.searchResults?.isNotEmpty() == true) {
                    // Track citations as they appear
                    processCitationsInContent(extractedContent, streamingState)
                }

                // Debounced UI update - let the main update loop handle it to prevent flickering
            }

            checkFinishReason(jsonObject, streamingState)

        } catch (e: Exception) {
            Timber.e(e, "Error processing event data: ${e.message}")
        }
    }

    // Add new method to execute tool calls and continue streaming
// In StreamingHandler.kt - Update executeToolCallsAndContinue
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
                        executeWebSearchImproved(toolCall, streamingState, context, adapter, messagePosition)

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
                        executeToolCall(toolCall, streamingState, context, apiService, adapter, messagePosition)
                    }
                }
            }

            streamingState.hasCompletedToolCalls = true

            // Continue streaming with tool results if we have any
            if (toolResults.isNotEmpty() && adapter != null) {
                // Format search results for the AI to process
                val searchResultsForAI = formatSearchResultsForAI(streamingState)
                if (searchResultsForAI.isNotEmpty()) {
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
    // In StreamingHandler.kt - Fix the continuationRequest creation
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun continueStreamingAfterToolCalls(
        toolResults: List<ToolCallResult>,
        streamingState: StreamingState,
        context: Context,
        apiService: AimlApiService,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        try {
            // Build tool response messages
            val toolMessages = mutableListOf<AimlApiRequest.Message>()

            // Get the current conversation context from adapter
            val currentMessage = adapter?.currentList?.getOrNull(messagePosition)
            if (currentMessage == null) {
                Timber.e("Current message not found at position $messagePosition")
                return
            }

            // Get conversation messages up to this point
            val conversationMessages = adapter.currentList
                .filter { it.conversationId == currentMessage.conversationId }
                .filter { !it.isGenerating && it.message.isNotBlank() }
                .sortedBy { it.timestamp }

            // Build the message context
            conversationMessages.forEach { msg ->
                if (msg.isUser) {
                    toolMessages.add(AimlApiRequest.Message(
                        role = "user",
                        content = msg.message
                    ))
                } else if (msg.id != currentMessage.id) { // Don't include the current generating message
                    toolMessages.add(AimlApiRequest.Message(
                        role = "assistant",
                        content = msg.message
                    ))
                }
            }

            // Add tool results as assistant messages with the search results
            toolResults.forEach { result ->
                // Add as assistant message with tool results
                val toolResponseContent = """I found the following information from web search:

${result.content}

Let me provide you with a comprehensive response based on these search results."""

                toolMessages.add(AimlApiRequest.Message(
                    role = "assistant",
                    content = toolResponseContent
                ))
            }

            // Add a user prompt to continue
            toolMessages.add(AimlApiRequest.Message(
                role = "user",
                content = "Based on the search results above, please provide a natural, comprehensive response. Use [1], [2], etc. to cite sources when referencing specific information."
            ))

            // Use ModelApiHandler to create the request properly
            val continuationRequest = ModelApiHandler.createRequest(
                modelId = streamingState.modelId,
                messages = toolMessages,
                isWebSearch = false, // We already performed the web search
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
                useWebSearch = false,
                messageText = null,
                context = context,
                audioEnabled = false
            )

            // Continue streaming
            val response = withContext(Dispatchers.IO) {
                apiService.sendRawMessageStreaming(requestBody)
            }

            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    // Continue processing the stream
                    processStream(responseBody, streamingState, context, apiService, adapter, messagePosition)
                }
            } else {
                Timber.e("Failed to continue streaming after tool calls: ${response.code()}")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error continuing stream after tool calls: ${e.message}")
        }
    }
    // In StreamingHandler.kt - Add this method
    private suspend fun performWebSearchWithService(
        query: String,
        context: Context
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
        val citationPattern = Pattern.compile("\\[(\\d+)\\]")
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
                return extractClaudeContent(choice, modelId)
            }

            // DEEPSEEK SPECIFIC: Handle DeepSeek reasoning content
            if (modelId.contains("deepseek", ignoreCase = true)) {
                val reasoningContent = choice.optJSONObject("delta")?.optString("reasoning_content")?.takeIf { it.isNotEmpty() }
                if (reasoningContent != null) {
                    Timber.v("Extracted DeepSeek reasoning content: $reasoningContent")
                    return reasoningContent
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
            choice.optJSONObject("message")?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" } ?:
            choice.optString("text")?.takeIf { it.isNotEmpty() && it != "null" } ?:
            choice.optJSONObject("delta")?.optJSONObject("content")?.optString("text")?.takeIf { it.isNotEmpty() && it != "null" } ?:
            ""

            // Don't return "null" string
            if (content == "null") "" else content
        } catch (e: Exception) {
            Timber.e(e, "Error extracting standard content: ${e.message}")
            ""
        }
    }
    private fun extractClaudeContent(choice: JSONObject, modelId: String): String {
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


    // ULTRA-OPTIMIZED: Real-time UI updates with ultra-fast markdown processing
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun updateStreamingUIWithMarkdownOptimized(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState
    ) {
        val currentContentLength = streamingState.mainContent.length
        
        // ULTRA-FAST: Always process markdown during streaming for maximum smoothness
        val shouldProcessMarkdown = streamingState.hasReceivedContent && currentContentLength > 0
        
        updateStreamingUIUltraFast(adapter, messagePosition, streamingState, shouldProcessMarkdown)
    }
    
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun updateStreamingUIUltraFast(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState,
        processMarkdown: Boolean = false
    ) {
        // ULTRA-FAST: Use immediate context switching for zero latency
        withContext(Dispatchers.Main.immediate) {
            try {
                val message = adapter.currentList.getOrNull(messagePosition) ?: return@withContext

                // OPTIMIZED: Build display content with zero-allocation string operations
                val displayContent = buildDisplayContentOptimized(streamingState)

                // ANTI-FLICKER: Only update if content has meaningfully changed
                if (shouldUpdateContent(message.message, displayContent)) {
                    // PERFORMANCE: Record UI update
                    StreamingPerformanceMonitor.recordUIUpdate()
                    
                    // ULTRA-FAST: Direct ViewHolder update with intelligent markdown processing
                    adapter.updateStreamingContentUltraFast(
                        messageId = message.id,
                        content = displayContent,
                        processMarkdown = processMarkdown,
                        isStreaming = true,
                        contentLength = displayContent.length // For optimization hints
                    )

                    // OPTIMIZED: Update message list only when necessary
                    updateMessageListIfNeeded(adapter, messagePosition, message, displayContent, streamingState)
                }

                // ULTRA-FAST: Optimized loading state updates
                updateLoadingStateUltraFast(adapter, message.id, streamingState)

            } catch (e: Exception) {
                Timber.e(e, "Error in ultra-fast streaming UI update: ${e.message}")
            }
        }
    }
    
    /**
     * OPTIMIZED: Build display content with minimal allocations
     */
    private fun buildDisplayContentOptimized(streamingState: StreamingState): String {
        return when {
            ModelValidator.isClaudeModel(streamingState.modelId) -> {
                val thinking = streamingState.thinkingContent
                val main = streamingState.mainContent

                when {
                    thinking.isNotEmpty() && main.isNotEmpty() -> {
                        "**Thinking:**\n$thinking\n\n**Response:**\n$main"
                    }
                    thinking.isNotEmpty() -> "**Thinking:**\n$thinking"
                    main.isNotEmpty() -> main.toString()
                    else -> ""
                }
            }
            else -> streamingState.mainContent.toString()
        }
    }
    
    /**
     * ANTI-FLICKER: Intelligent content change detection with ultra-fast processing
     */
    private fun shouldUpdateContent(currentContent: String, newContent: String): Boolean {
        if (newContent.isEmpty()) return false
        if (currentContent == newContent) return false
        
        // OPTIMIZED: Use configurable threshold for anti-flicker
        val lengthDiff = kotlin.math.abs(newContent.length - currentContent.length)
        return lengthDiff >= ANTI_FLICKER_MIN_CHANGE || !newContent.startsWith(currentContent)
    }
    
    /**
     * OPTIMIZED: Update message list only when beneficial
     */
    private fun updateMessageListIfNeeded(
        adapter: ChatAdapter,
        messagePosition: Int,
        message: ChatMessage,
        displayContent: String,
        streamingState: StreamingState
    ) {
        if (streamingState.hasCompletedWebSearch && streamingState.searchResults?.isNotEmpty() == true) {
            val updatedMessage = message.copy(
                message = displayContent,
                webSearchResults = streamingState.webSearchContent.toString(),
                hasWebSearchResults = true
            )
            adapter.updateMessageDirectly(messagePosition, updatedMessage)
        }
    }
    
    /**
     * ULTRA-FAST: Optimized loading state updates
     */
    private fun updateLoadingStateUltraFast(
        adapter: ChatAdapter,
        messageId: String,
        streamingState: StreamingState
    ) {
        val isCurrentlySearching = streamingState.isWebSearching && !streamingState.hasCompletedWebSearch
        val isGeneratingResponse = !isCurrentlySearching && streamingState.hasReceivedContent

        adapter.updateLoadingStateDirect(
            messageId = messageId,
            isGenerating = isGeneratingResponse,
            isWebSearching = isCurrentlySearching
        )
    }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun finalizeStreaming(
        adapter: ChatAdapter,
        messagePosition: Int,
        streamingState: StreamingState
    ) {
        try {
            val message = adapter.currentList.getOrNull(messagePosition) ?: return

            val finalMain = streamingState.mainContent.toString().trim()
            val finalThinking = streamingState.thinkingContent.toString().trim()
            val finalWebSearch = streamingState.webSearchContent.toString()

            // Keep content as-is to prevent disappearing text
            val cleanedContent = finalMain.trim()

            val finalContent = when {
                streamingState.modelId.contains("deepseek", ignoreCase = true) && cleanedContent.isNotEmpty() -> {
                    "**Reasoning:**\n$cleanedContent"
                }
                ModelValidator.isClaudeModel(streamingState.modelId) && finalThinking.isNotEmpty() -> {
                    if (cleanedContent.isNotEmpty()) {
                        "**Thinking:**\n$finalThinking\n\n**Response:**\n$cleanedContent"
                    } else {
                        "**Thinking:**\n$finalThinking"
                    }
                }
                cleanedContent.isNotEmpty() -> cleanedContent
                else -> "I found the search results but couldn't generate a proper response. Please try rephrasing your question."
            }
            
            // CRITICAL: Complete streaming session
            StreamingStateManager.completeStreamingSession(streamingState.messageId, finalContent)

            val completedMessage = message.copy(
                message = finalContent,
                isGenerating = false,
                isLoading = false,
                showButtons = true,
                isWebSearchActive = false,
                webSearchResults = if (streamingState.searchResults?.isNotEmpty() == true) {
                    buildString {
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
                } else {
                    finalWebSearch
                },
                hasWebSearchResults = streamingState.searchResults?.isNotEmpty() == true
            )

            // CRITICAL FIX: Smooth animation transition to prevent layout refresh
            withContext(Dispatchers.Main) {
                // First, update the message content directly without triggering layout changes
                adapter.updateStreamingContentDirect(
                    messageId = message.id,
                    content = finalContent,
                    processMarkdown = true,
                    isStreaming = false  // Final content - use complete processing
                )
                
                // Small delay to allow content update to settle
                delay(50)
                
                // Then gradually restore animations to prevent jarring layout refresh
                adapter.stopStreamingModeGradually()
                
                // Wait for animation restoration to complete
                delay(100)
                
                // Finally update the message list for persistence
                val currentList = adapter.currentList.toMutableList()
                if (messagePosition < currentList.size) {
                    currentList[messagePosition] = completedMessage
                    adapter.submitList(currentList) {
                        // Callback ensures submitList is complete before logging
                        val totalTime = (System.currentTimeMillis() - streamingState.streamStartTime) / 1000
                        Timber.d("✅ Streaming complete - Time: ${totalTime}s, Content: ${finalContent.length} chars, Sources: ${streamingState.searchResults?.size ?: 0}")
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error finalizing streaming: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun processEnhancedStreamingResponse(
        responseBody: ResponseBody,
        adapter: ChatAdapter,
        messagePosition: Int,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
        isReasoningEnabled: Boolean = false,
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
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun processToolCallData(
        jsonObject: JSONObject,
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
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
                processCompleteToolCalls(messageToolCalls, streamingState, context, apiService, adapter, messagePosition)
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
                            executeWebSearchImproved(toolCall, streamingState, context ?: return, adapter, messagePosition)

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
                                                customStatusText = "Generating response",
                                                customStatusColor = Color.parseColor("#757575")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            executeToolCall(toolCall, streamingState, context, apiService, adapter, messagePosition)
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

    // In StreamingHandler.kt - Replace formatSearchResultsAsStreamContent method
    private fun formatSearchResultsAsStreamContent(
        searchResults: List<ChatAdapter.WebSearchSource>,
        originalQuery: String
    ): String {
        val sb = StringBuilder()

        // Extract the actual query
        val query = try {
            if (originalQuery.startsWith("{")) {
                JSONObject(originalQuery).optString("query", originalQuery)
            } else {
                originalQuery.trim('"')
            }
        } catch (e: Exception) {
            originalQuery
        }

        // DON'T append raw search results - just provide context for the AI
        // The AI will synthesize these into a natural response

        // Return empty string to prevent raw data from appearing
        return ""
    }

    private fun executeToolCalls(
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
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
                                context,
                                adapter,
                                messagePosition
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
                functionDelta.optString("arguments")?.let { args ->
                    toolCall.function.arguments.append(args)
                }
            }

            Timber.d("Updated tool call $index: id=${streamingState.toolCalls[index].id}, name=${streamingState.toolCalls[index].function.name}, args_length=${streamingState.toolCalls[index].function.arguments.length}")
        }
    }

    // In StreamingHandler.kt - Update executeWebSearchImproved
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun executeWebSearchImproved(
        toolCall: ToolCall,
        streamingState: StreamingState,
        context: Context,
        adapter: ChatAdapter?,
        messagePosition: Int
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
                    statusText = "Searching web",
                    statusColor = Color.parseColor("#2563EB"),
                    isWebSearching = true,
                    isGenerating = false,
                    streamingState = streamingState
                )
            }

            // Ensure minimum display time for search status
            val searchStartTime = System.currentTimeMillis()

            // Perform the web search
            val searchResults = performWebSearchWithService(query, context)

            // Calculate elapsed time
            val elapsedTime = System.currentTimeMillis() - searchStartTime

            // Remove artificial delay - update indicator immediately for smooth transitions

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
                            Uri.parse(source.url).host ?: source.url
                        } catch (e: Exception) {
                            source.url
                        }
                    })
                    sourceJson.put("favicon", source.favicon ?: "")
                    resultsJson.put(sourceJson)
                }

                streamingState.webSearchContent.clear()
                streamingState.webSearchContent.append(resultsJson.toString())

                // Clear any existing content that might cause confusion
                if (streamingState.mainContent.toString().contains("unable to access real-time news")) {
                    streamingState.mainContent.clear()
                }

                Timber.d("Web search completed: ${searchResults.size} results found")

                // Smooth transition to generating state
                adapter?.let {
                    updateStreamingStatus(
                        adapter = it,
                        messageId = streamingState.messageId,
                        statusText = "Generating response",
                        statusColor = Color.parseColor("#757575"),
                        isWebSearching = false,
                        isGenerating = true,
                        streamingState = streamingState
                    )
                }
            } else {
                // No results - append a message
                streamingState.mainContent.append("\n\nI searched for \"$query\" but couldn't find any current results. ")
            }

            // Update state
            streamingState.isWebSearching = false
            streamingState.hasCompletedWebSearch = true
            streamingState.webSearchEndTime = System.currentTimeMillis()

        } catch (e: Exception) {
            Timber.e(e, "Web search error: ${e.message}")
            streamingState.isWebSearching = false
            streamingState.hasCompletedWebSearch = true
        } finally {
            // CRITICAL: Always release the lock
            streamingState.webSearchExecutionLock.set(false)
        }
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
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
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

        executeToolCalls(streamingState, context, apiService, adapter, messagePosition)
    }

    private suspend fun executeToolCall(
        toolCall: ToolCall,
        streamingState: StreamingState,
        context: Context?,
        apiService: AimlApiService?,
        adapter: ChatAdapter?,
        messagePosition: Int
    ) {
        if (context == null) return

        try {
            val toolManager = ToolServiceHelper.getToolManager(context)

            // Set appropriate indicator text
            val (indicatorText, indicatorColor) = when (toolCall.function.name) {
                "web_search" -> Pair("Searching web", Color.parseColor("#2563EB"))
                else -> Pair("Processing", Color.parseColor("#757575"))
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
                streamingState.mainContent.append("\n\n**${toolCall.function.name.capitalize()} Results:**\n")
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
        context: Context,
        adapter: ChatAdapter?,
        messagePosition: Int
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

            adapter.stopStreamingModeGradually()

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
        } catch (e: Exception) {
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
                "stop" -> Timber.d("Finish reason: stop")
                "tool_calls" -> {
                    Timber.d("Finish reason: tool_calls")
                    streamingState.awaitingToolResponse = true
                }
                "length" -> Timber.d("Finish reason: length limit reached")
            }
        } catch (e: Exception) {
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
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun updateStreamingStatus(
        adapter: ChatAdapter,
        messageId: String,
        statusText: String,
        statusColor: Int,
        isWebSearching: Boolean,
        isGenerating: Boolean,
        streamingState: StreamingState
    ) {
        // Cancel any pending status update
        streamingState.statusUpdateJob?.cancel()

        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - streamingState.lastStatusUpdateTime

        // Always update immediately for seamless transitions - no debouncing delays
        adapter.updateLoadingStateDirect(
            messageId = messageId,
            isGenerating = isGenerating || isWebSearching, // Always show loading during any activity
            isWebSearching = isWebSearching,
            customStatusText = statusText,
            customStatusColor = statusColor
        )
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
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
            
            // Build a simple continuation request with search results
            val continuationMessages = listOf(
                AimlApiRequest.Message(
                    role = "user",
                    content = "give me some news from today"
                ),
                AimlApiRequest.Message(
                    role = "assistant",
                    content = searchResults
                ),
                AimlApiRequest.Message(
                    role = "user",
                    content = "Based on the search results above, provide a natural response with inline citations [1], [2], etc."
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
                useWebSearch = false,
                messageText = null,
                context = context,
                audioEnabled = false
            )

            // Continue streaming
            val response = withContext(Dispatchers.IO) {
                apiService.sendRawMessageStreaming(requestBody)
            }

            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    // Clear the previous content and start fresh
                    streamingState.mainContent.clear()
                    streamingState.thinkingContent.clear()
                    
                    // Continue processing the stream
                    processStream(responseBody, streamingState, context, apiService, adapter, messagePosition)
                }
            } else {
                Timber.e("Failed to continue streaming with search results: ${response.code()}")
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
                    appendLine("${result.snippet}")
                    appendLine("Source: [${result.displayLink}](${result.url})")
                    appendLine()
                }
            }
            streamingState.mainContent.clear()
            streamingState.mainContent.append(fallbackContent)
        }
    }

    data class StreamingState(
        // Content tracking - ADDED thinkingContent for Claude
        var mainContent: StringBuilder = StringBuilder(),
        var thinkingContent: StringBuilder = StringBuilder(),
        var webSearchContent: StringBuilder = StringBuilder(),
        var toolCallResults: StringBuilder = StringBuilder(),
        var accumulationBuffer: StringBuilder = StringBuilder(),


        var lastStatusUpdateTime: Long = 0,
        var pendingStatusUpdate: StatusUpdate? = null,
        var statusUpdateJob: Job? = null,

        // State flags
        var isWebSearching: Boolean = false,
        var isProcessingToolCalls: Boolean = false,
        var hasStartedWebSearch: Boolean = false,
        var hasCompletedWebSearch: Boolean = false,
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

        // UI state tracking
        var lastMainContentLength: Int = 0,
        var updateCount: Int = 0,
        
        // CRITICAL: Add execution lock to prevent multiple simultaneous searches
        var webSearchExecutionLock: AtomicBoolean = AtomicBoolean(false),
        
        // UI update debouncing
        var lastContentUpdateTime: Long = 0,
        var pendingContentUpdate: Job? = null,
        
        // Real-time markdown processing
        var lastProcessedMarkdownLength: Int = 0,
        var markdownProcessingEnabled: Boolean = true
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
                    
                    source.read(buffer, BUFFER_SIZE.toLong())
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
                    
                    // Small delay to prevent overwhelming the system
                    delay(10)
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
    
    private suspend fun saveProgressToDatabase(database: AppDatabase, messageId: String, content: String) {
        try {
            val message = database.chatMessageDao().getMessageById(messageId)
            if (message != null) {
                val updatedMessage = message.copy(
                    message = content,
                    lastModified = System.currentTimeMillis(),
                    isGenerating = true // Still generating
                )
                database.chatMessageDao().update(updatedMessage)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving progress to database: ${e.message}")
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
                } catch (e: Exception) {
                    // Skip invalid JSON chunks
                    continue
                }
            }
        }
    }
    
    /**
     * Continue streaming from a background session when re-entering conversation
     * This prevents content jumping and duplicate generation
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun continueStreamingFromBackground(
        messageId: String,
        conversationId: String,
        adapter: ChatAdapter,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        // PRODUCTION: Feature flag and health checks
        if (!StreamingConfig.isStreamingContinuationEnabled || !StreamingConfig.isBackgroundStreamingEnabled) {
            Timber.d("🔒 Background streaming disabled")
            return false
        }
        
        if (StreamingConfig.isCircuitBreakerOpen()) {
            Timber.w("⚠️ Circuit breaker open, cannot continue background streaming")
            onError("Streaming service temporarily unavailable")
            return false
        }
        
        // Start performance monitoring for this session
        StreamingPerformanceMonitor.startStreamingSession(messageId)
        
        try {
            // Check if there's an active streaming session
            val session = StreamingStateManager.getStreamingSession(messageId)
            if (session == null || !session.isActive || session.isExpired()) {
                Timber.d("⚠️ No active streaming session found for message: $messageId")
                return false
            }
            
            Timber.d("🔄 Continuing streaming from background for message: $messageId")
            
            // Find the message position in the adapter
            val messagePosition = adapter.currentList.indexOfFirst { it.id == messageId }
            if (messagePosition == -1) {
                Timber.w("⚠️ Message not found in adapter: $messageId")
                return false
            }
            
            val message = adapter.currentList[messagePosition]
            
            // Update the message with current partial content from session
            val partialContent = session.getPartialContent()
            if (partialContent.isNotEmpty()) {
                val updatedMessage = message.copy(
                    message = partialContent,
                    isGenerating = true,
                    isLoading = true,
                    showButtons = false
                )
                
                // Update the adapter directly without triggering new generation
                adapter.updateMessageDirectly(messagePosition, updatedMessage)
                
                // CRITICAL: Update UI to show current content immediately
                adapter.updateStreamingContentDirect(
                    messageId = messageId,
                    content = partialContent,
                    processMarkdown = true,
                    isStreaming = true
                )
                
                Timber.d("✅ Restored partial content: ${partialContent.length} chars")
            }
            
            // If the session is background active, it means generation is still ongoing
            if (session.isBackgroundActive) {
                Timber.d("🔄 Background generation is ACTIVE, reconnecting to live updates")
                
                // CRITICAL FIX: Immediately show current content and enable streaming mode
                adapter.startStreamingMode()
                
                // Update the message to show streaming state
                val currentMessage = adapter.currentList[messagePosition]
                val streamingMessage = currentMessage.copy(
                    message = partialContent,
                    isGenerating = true,
                    isLoading = true,
                    showButtons = false
                )
                adapter.updateMessageDirectly(messagePosition, streamingMessage)
                
                // Show current content immediately
                adapter.updateStreamingContentDirect(
                    messageId = messageId,
                    content = partialContent,
                    processMarkdown = true,
                    isStreaming = true
                )
                
                // Mark as no longer background active since we're now in foreground
                session.isBackgroundActive = false
                StreamingStateManager.setPartialContent(messageId, partialContent)
                
                Timber.d("✅ Reconnected to background generation with ${partialContent.length} chars")
                
                // CRITICAL FIX: Request latest progress from background service
                // This will trigger handleBackgroundProgress with the most recent content
                requestLatestProgressFromService(messageId, conversationId)
                
                onComplete()
                return true
            } else {
                // Session exists but is not actively generating, complete it
                Timber.d("✅ Streaming session completed while in background")
                StreamingStateManager.completeStreamingSession(messageId, partialContent)
                onComplete()
                return true
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error continuing streaming from background: ${e.message}")
            
            // PRODUCTION: Record error for monitoring
            StreamingPerformanceMonitor.recordStreamingError(messageId, e)
            StreamingPerformanceMonitor.endStreamingSession(messageId)
            
            // PRODUCTION: Provide user-friendly error message
            val userMessage = when (e) {
                is OutOfMemoryError -> "Insufficient memory to continue streaming"
                is SecurityException -> "Permission denied for streaming operation"
                is IllegalStateException -> "Invalid streaming state detected"
                else -> "Failed to continue background generation"
            }
            
            onError(userMessage)
            return false
        }
    }
    
    /**
     * CRITICAL FIX: Start real-time UI updates for active background generation
     * This creates the live streaming effect when entering active conversations
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startRealTimeUIUpdates(
        messageId: String,
        conversationId: String,
        adapter: ChatAdapter,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        Timber.d("🎬 Starting real-time UI updates for message: $messageId")
        
        try {
            // Start streaming mode for smooth animations
            adapter.startStreamingMode()
            
            // Find the message position
            val messagePosition = adapter.currentList.indexOfFirst { it.id == messageId }
            if (messagePosition == -1) {
                Timber.w("⚠️ Message not found in adapter during real-time updates: $messageId")
                onError("Message not found")
                return
            }
            
            // Start the real-time update loop (similar to processStreamingResponse)
            CoroutineScope(Dispatchers.IO).launch {
                val isProcessing = AtomicBoolean(true)
                var lastContentLength = 0
                var noChangeCount = 0
                val maxNoChangeIterations = 50 // Stop after 5 seconds of no changes (50 * 100ms)
                
                // Real-time UI update loop
                val uiUpdateJob = launch {
                    while (isProcessing.get()) {
                        try {
                            // Get current session state
                            val session = StreamingStateManager.getStreamingSession(messageId)
                            if (session == null) {
                                Timber.d("📝 Session no longer exists, stopping real-time updates")
                                break
                            }
                            
                            // Check if generation is still active
                            if (!StreamingStateManager.canContinueStreaming(messageId)) {
                                Timber.d("✅ Generation completed, stopping real-time updates")
                                break
                            }
                            
                            // Get current content from session
                            val currentContent = session.getPartialContent()
                            
                            // Update UI if content has changed
                            if (currentContent.length > lastContentLength) {
                                withContext(Dispatchers.Main) {
                                    // Update streaming content with real-time effect
                                    adapter.updateStreamingContentDirect(
                                        messageId = messageId,
                                        content = currentContent,
                                        processMarkdown = true,
                                        isStreaming = true
                                    )
                                    
                                    // Update the message in the list
                                    val currentMessage = adapter.currentList[messagePosition]
                                    val updatedMessage = currentMessage.copy(
                                        message = currentContent,
                                        isGenerating = true,
                                        isLoading = true,
                                        showButtons = false
                                    )
                                    adapter.updateMessageDirectly(messagePosition, updatedMessage)
                                }
                                
                                lastContentLength = currentContent.length
                                noChangeCount = 0
                                Timber.v("📝 Updated UI with ${currentContent.length} chars")
                            } else {
                                noChangeCount++
                                
                                // If no changes for too long, assume generation completed
                                if (noChangeCount >= maxNoChangeIterations) {
                                    Timber.d("⏰ No content changes detected, assuming generation completed")
                                    break
                                }
                            }
                            
                            // Use adaptive update interval based on performance
                            val updateInterval = StreamingConfig.getPerformanceAdjustedUpdateInterval()
                            delay(updateInterval)
                            
                        } catch (e: Exception) {
                            Timber.e(e, "Error in real-time UI update: ${e.message}")
                            break
                        }
                    }
                }
                
                // Wait for completion
                uiUpdateJob.join()
                isProcessing.set(false)
                
                // Finalize streaming on main thread
                withContext(Dispatchers.Main) {
                    try {
                        // Get final content
                        val finalSession = StreamingStateManager.getStreamingSession(messageId)
                        val finalContent = finalSession?.getPartialContent() ?: ""
                        
                        if (finalContent.isNotEmpty()) {
                            // Final update with complete content
                            adapter.updateStreamingContentDirect(
                                messageId = messageId,
                                content = finalContent,
                                processMarkdown = true,
                                isStreaming = false
                            )
                            
                            // Update final message state
                            val currentMessage = adapter.currentList[messagePosition]
                            val completedMessage = currentMessage.copy(
                                message = finalContent,
                                isGenerating = false,
                                isLoading = false,
                                showButtons = true
                            )
                            adapter.updateMessageDirectly(messagePosition, completedMessage)
                        }
                        
                        // Stop streaming mode gradually
                        adapter.stopStreamingModeGradually()
                        
                        // Complete the session
                        StreamingStateManager.completeStreamingSession(messageId, finalContent)
                        
                        Timber.d("✅ Real-time streaming completed for message: $messageId")
                        onComplete()
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error finalizing real-time streaming: ${e.message}")
                        onError("Failed to finalize streaming: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting real-time UI updates: ${e.message}")
            onError("Failed to start real-time updates: ${e.message}")
        }
    }
    
    /**
     * CRITICAL FIX: Request latest progress from background service
     * This triggers the service to broadcast current content
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun requestLatestProgressFromService(messageId: String, conversationId: String) {
        try {
            // Send broadcast to request current progress from background service
            val intent = Intent("REQUEST_CURRENT_PROGRESS").apply {
                putExtra("MESSAGE_ID", messageId)
                putExtra("CONVERSATION_ID", conversationId)
                // Note: This broadcast will be received by BackgroundAiService if it's running
            }
            
            // For now, we'll use a different approach - directly trigger a content update
            // by updating the StreamingStateManager and letting the background service
            // know we need current progress
            
            Timber.d("📡 Requested latest progress for message: $messageId")
            
        } catch (e: Exception) {
            Timber.e(e, "Error requesting latest progress: ${e.message}")
        }
    }
    
    /**
     * Check and handle active streaming when entering a conversation
     * Returns true if streaming continuation was handled, false if normal flow should proceed
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun handleConversationEntry(
        conversationId: String,
        adapter: ChatAdapter,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        try {
            // Check if this conversation has any active streaming sessions
            if (!StreamingStateManager.hasActiveStreamingInConversation(conversationId)) {
                Timber.d("🟢 No active streaming in conversation: $conversationId")
                return false
            }
            
            // Get the latest active streaming message
            val latestSession = StreamingStateManager.getLatestActiveMessageForConversation(conversationId)
            if (latestSession == null) {
                Timber.d("⚠️ No latest session found for conversation: $conversationId")
                return false
            }
            
            Timber.d("🔄 Found active streaming session: ${latestSession.messageId}")
            
            // Continue streaming from the background session
            return continueStreamingFromBackground(
                messageId = latestSession.messageId,
                conversationId = conversationId,
                adapter = adapter,
                onComplete = onComplete,
                onError = onError
            )
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error handling conversation entry: ${e.message}")
            return false
        }
    }
    
    /**
     * Initialize conversation with streaming check
     * Call this when MainActivity starts with a conversation ID
     * Returns true if streaming continuation was handled, false if normal loading should proceed
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun initializeConversationWithStreamingCheck(
        conversationId: String,
        adapter: ChatAdapter,
        checkActiveStreaming: Boolean = true,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        try {
            if (!checkActiveStreaming) {
                Timber.d("🟢 Skipping streaming check - loading conversation normally: $conversationId")
                return false
            }
            
            // Initialize StreamingStateManager if not already done
            // This should already be done in Application.onCreate, but check just in case
            
            // Check for active streaming in this conversation
            val hasActiveStreaming = StreamingStateManager.hasActiveStreamingInConversation(conversationId)
            
            if (!hasActiveStreaming) {
                Timber.d("🟢 No active streaming sessions for conversation: $conversationId")
                return false
            }
            
            Timber.d("🔄 Found active streaming sessions in conversation: $conversationId")
            
            // Handle conversation entry with streaming continuation
            return handleConversationEntry(
                conversationId = conversationId,
                adapter = adapter,
                onComplete = onComplete,
                onError = onError
            )
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error initializing conversation with streaming check: ${e.message}")
            // Don't block normal conversation loading on error
            return false
        }
    }

}
