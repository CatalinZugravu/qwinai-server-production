// Complete Production-Ready ModelApiHandler.kt with ALL parameters
package com.cyberflux.qwinai.network

import android.content.Context
import com.cyberflux.qwinai.model.ModelConfig
import com.cyberflux.qwinai.utils.LocationService
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ModelApiHandler {

    /**
     * Creates request for AI models with proper parameter handling
     */
    suspend fun createRequest(
        modelId: String,
        messages: List<AimlApiRequest.Message>,
        isWebSearch: Boolean,
        context: Context,
        messageText: String? = null,
        audioEnabled: Boolean = false,
        audioFormat: String = "mp3",
        voiceType: String = "alloy",
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low"
    ) = withContext(Dispatchers.Default) {

        val config = ModelConfigManager.getConfig(modelId)
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        // Auto-detect web search need
        val (autoDetectedNeedsWebSearch, timeParams) = if (
            messageText != null &&
            !isWebSearch &&
            config.supportsFunctionCalling
        ) {
            WebSearchUtils.analyzeMessage(messageText)
        } else {
            Pair(false, mapOf<String, String>())
        }

        // IMPROVED: Check for explicit web search requests
        val containsExplicitSearchRequest = messageText?.let { msg ->
            val lowerMsg = msg.lowercase()
            lowerMsg.contains("search the internet") ||
                    lowerMsg.contains("search the web") ||
                    lowerMsg.contains("web search") ||
                    lowerMsg.contains("google") ||
                    lowerMsg.contains("look up") ||
                    lowerMsg.contains("find information") ||
                    lowerMsg.contains("search for") ||
                    (lowerMsg.contains("search") && (lowerMsg.contains("online") || lowerMsg.contains("for")))
        } ?: false

        // Combine all detection methods for maximum coverage
        val useWebSearch = (isWebSearch || autoDetectedNeedsWebSearch || containsExplicitSearchRequest) &&
                config.supportsFunctionCalling

        // Log decision
        Timber.d("Web search decision: enabled=$useWebSearch (explicit=$isWebSearch, autoDetected=$autoDetectedNeedsWebSearch, explicitRequest=$containsExplicitSearchRequest)")

        // Location handling
        val userLocation = try {
            LocationService.getApproximateLocation(context)
        } catch (e: Exception) {
            Timber.e(e, "Location error: ${e.message}")
            LocationService.getSystemDefaultLocation()
        }

        // System message handling
        val systemMessage = when {
            !config.supportsSystemMessages -> null
            config.requiresClaudeThinking -> null // Claude handles system differently
            config.supportsFunctionCalling -> createWebSearchSystemMessage(userLocation)
            else -> createBasicSystemMessage(userLocation)
        }

        // Build final messages
        val finalMessages = when {
            config.requiresClaudeThinking -> messages
            systemMessage != null && config.supportsSystemMessages ->
                listOf(systemMessage) + messages
            systemMessage != null ->
                transformMessagesForCompatibility(systemMessage, messages)
            else -> messages
        }

// Always include tools for models that support function calling - let model decide when to use them
        val webSearchTools = if (config.supportsFunctionCalling) {
            val tools = mutableListOf<AimlApiRequest.Tool>()

            // Always add web search tool
            tools.add(createWebSearchTool())

            // Add other tools for models that support them
            if (ModelValidator.supportsMultipleTools(modelId)) {
                tools.add(createCalculatorTool())
                tools.add(createWeatherTool())
                tools.add(createTranslationTool())
                tools.add(createWikipediaTool())
                tools.add(createSpreadsheetTool())
            }

            tools
        } else {
            null
        }
        // Build request with common parameters
        AimlApiRequest(
            model = modelId,
            messages = finalMessages,
            thinking = if (config.requiresClaudeThinking && config.supportsReasoning && isReasoningEnabled) {
                // IMPORTANT: Use safe thinking budget - ensure we don't exceed Claude's limits
                val safeMaxTokens = minOf(config.defaultMaxTokens, 100000)
                val thinkingBudget = minOf((safeMaxTokens * 0.6).toInt(), 60000) // Cap at 60k thinking tokens
                Timber.d("Claude thinking ENABLED - reasoning enabled: $isReasoningEnabled, budget: $thinkingBudget, safeMaxTokens: $safeMaxTokens")
                AimlApiRequest.ClaudeThinking.create(thinkingBudget)
            } else {
                Timber.d("Claude thinking DISABLED - reasoning enabled: $isReasoningEnabled, requiresClaudeThinking: ${config.requiresClaudeThinking}, supportsReasoning: ${config.supportsReasoning}")
                null
            },
            maxTokens = if (!config.requiresMaxCompletionTokens) config.defaultMaxTokens else null,
            temperature = config.defaultTemperature,
            topP = if (config.supportsTopP) config.defaultTopP else null,
            topK = if (config.supportsTopK) 40 else null,
            maxCompletionTokens = if (config.requiresMaxCompletionTokens) config.defaultMaxTokens else null,
            stream = config.supportsStreaming,
            streamOptions = if (config.supportsStreamOptions) {
                AimlApiRequest.StreamOptions(includeUsage = config.streamIncludeUsage)
            } else null,
            frequencyPenalty = if (config.supportsFrequencyPenalty) 0.0 else null,
            presencePenalty = if (config.supportsPresencePenalty) 0.0 else null,
            repetitionPenalty = if (config.supportsRepetitionPenalty) 1.0 else null,
            logprobs = if (config.supportsLogprobs) false else null,
            topLogprobs = if (config.supportsTopLogprobs) null else null,
            seed = if (config.supportsSeed) System.currentTimeMillis() else null,
            tools = webSearchTools,
            toolChoice = if (config.supportsFunctionCalling && config.supportsToolChoice) "auto" else null,
            webSearchOptions = if (config.supportsFunctionCalling) {
                val searchContentSize = getOptimalSearchContentSize(modelId, timeParams)
                AimlApiRequest.WebSearchOptions(
                    searchContextSize = searchContentSize,
                    userLocation = userLocation,
                    maxResults = getOptimalSearchResults(modelId),
                    freshness = timeParams["freshness"] ?: "recent"
                )
            } else null,
            reasoningEffort = if (config.reasoningParameter == "reasoning_effort" && config.supportsReasoning) {
                // Use the provided reasoning level from UI
                reasoningLevel
            } else null,
            audio = if (audioEnabled && config.supportsAudio) {
                AimlApiRequest.AudioOptions(format = audioFormat, voice = voiceType)
            } else null,
            modalities = if (audioEnabled && config.supportsAudio) {
                listOf("text", "audio")
            } else null,
            responseFormat = if (config.supportsResponseFormat) null else null,
            topA = if (config.supportsTopA) null else null,
            parallelToolCalls = if (config.supportsParallelToolCalls && useWebSearch) true else null,
            minP = if (config.supportsMinP) null else null,
            useWebSearch = false
        )
    }
    fun createRequestBody(
        apiRequest: AimlApiRequest,
        modelId: String,
        useWebSearch: Boolean,
        messageText: String? = null,
        context: Context? = null,
        audioEnabled: Boolean = false,
        audioFormat: String = "mp3",
        voiceType: String = "alloy"
    ): RequestBody {
        val config = ModelConfigManager.getConfig(modelId)
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        Timber.d("Creating request body for model: $modelId, provider: ${config.provider}")

        // Build request map with only supported parameters
        val requestMap = buildSupportedParametersMap(apiRequest, config, audioEnabled)

        // Apply model-specific customizations
        try {
            when (modelId) {
                // === OPENAI MODELS ===
                ModelManager.O1_ID -> handleO1Model(requestMap, config)
                ModelManager.O3_MINI_ID -> handleO3MiniModel(requestMap, config)
                ModelManager.GPT_4O_ID,
                ModelManager.CHATGPT_4O_LATEST_ID -> handleGPT4OModel(requestMap, config, audioEnabled)
                ModelManager.GPT_4O_2024_05_13_ID -> handleGPT4O20240513Model(requestMap, config, audioEnabled)
                ModelManager.GPT_4O_2024_08_06_ID -> handleGPT4O20240806Model(requestMap, config, audioEnabled)
                ModelManager.GPT_4o_MINI_ID -> handleGPT4OMiniModel(requestMap, config)
                ModelManager.GPT_4_1_MINI_ID -> handleGPT41MiniModel(requestMap, config, audioEnabled)
                ModelManager.GPT_4_TURBO_ID -> handleGPT4TurboModel(requestMap, config)
                ModelManager.GPT_4_TURBO_2024_04_09_ID -> handleGPT4Turbo20240409Model(requestMap, config)

                // === ANTHROPIC MODELS ===
                ModelManager.CLAUDE_3_7_SONNET_ID -> handleClaudeModel(requestMap, apiRequest, config)

                // === META MODELS ===
                ModelManager.LLAMA_3_2_3B_INSTRUCT_TURBO_ID -> handleLlama32Model(requestMap, config)
                ModelManager.LLAMA_4_MAVERICK_ID -> handleLlama4Model(requestMap, config)

                // === GOOGLE MODELS ===
                ModelManager.GEMMA_3B_INSTRUCT_ID,
                ModelManager.GEMMA_27B_INSTRUCT_ID,
                ModelManager.GEMMA_4B_INSTRUCT_ID -> handleGemmaModel(requestMap, config)

                // === COHERE MODELS ===
                ModelManager.COHERE_COMMAND_R_PLUS_ID -> handleCohereModel(requestMap, config)

                // === DEEPSEEK MODELS ===
                ModelManager.DEEPSEEK_R1_ID -> handleDeepSeekModel(requestMap, config)

                // === QWEN MODELS ===
                ModelManager.QWEN_3_235B_ID -> handleQwen3Model(requestMap, config)
                ModelManager.QWEN_2_5_72B_ID -> handleQwen25Model(requestMap, config)

                // === XAI MODELS ===
                ModelManager.GROK_3_BETA_ID -> handleGrokModel(requestMap, config)

                // === MISTRAL MODELS ===
                ModelManager.MIXTRAL_8X7B_ID -> handleMixtralModel(requestMap, config)
                ModelManager.MISTRAL_OCR_ID -> handleMistralOCRModel(requestMap, config)

                else -> {
                    Timber.w("Using default handling for unknown model: $modelId")
                    // No additional customization needed since we only include supported params
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in model-specific handler for $modelId")
            // Continue with basic request map
        }

        val gson = GsonBuilder().disableHtmlEscaping().create()
        val jsonString = gson.toJson(requestMap)
        Timber.d("Final request body for $modelId: ${jsonString.take(500)}...")

        return jsonString.toRequestBody("application/json".toMediaTypeOrNull())
    }

    /**
     * COMPLETE: Build request map with ALL supported parameters based on ModelConfig
     *
     * NOTE: Some parameters are commented out because they don't exist in AimlApiRequest yet:
     * - prediction: For GPT-4o prediction feature
     * - stopSequences: For custom stop sequences
     * - n: For multiple completions (added directly in handlers where needed)
     *
     * To enable full functionality, add these properties to AimlApiRequest:
     * val prediction: Any? = null
     * val stopSequences: List<String>? = null
     * val n: Int? = null
     */
    private fun buildSupportedParametersMap(
        apiRequest: AimlApiRequest,
        config: ModelConfig,
        audioEnabled: Boolean
    ): MutableMap<String, Any?> {
        val requestMap = mutableMapOf<String, Any?>()

        // === CORE REQUIRED PARAMETERS ===
        requestMap["model"] = apiRequest.model
        requestMap["messages"] = apiRequest.messages

        // === TOKEN LIMITS (mutually exclusive) ===
        if (config.requiresMaxCompletionTokens) {
            apiRequest.maxCompletionTokens?.let { requestMap["max_completion_tokens"] = it }
        } else {
            apiRequest.maxTokens?.let { requestMap["max_tokens"] = it }
        }

        // === BASIC SAMPLING PARAMETERS ===
        requestMap["temperature"] = apiRequest.temperature
        if (config.supportsTopP) {
            apiRequest.topP?.let { requestMap["top_p"] = it }
        }

        // === STREAMING ===
        if (config.supportsStreaming) {
            requestMap["stream"] = apiRequest.stream
            if (config.supportsStreamOptions) {
                apiRequest.streamOptions?.let { requestMap["stream_options"] = it }
            }
        }

        // === ADVANCED SAMPLING PARAMETERS ===
        if (config.supportsTopK) {
            apiRequest.topK?.let { requestMap["top_k"] = it }
        }
        if (config.supportsFrequencyPenalty) {
            apiRequest.frequencyPenalty?.let { requestMap["frequency_penalty"] = it }
        }
        if (config.supportsPresencePenalty) {
            apiRequest.presencePenalty?.let { requestMap["presence_penalty"] = it }
        }
        if (config.supportsRepetitionPenalty) {
            apiRequest.repetitionPenalty?.let { requestMap["repetition_penalty"] = it }
        }
        if (config.supportsMinP) {
            apiRequest.minP?.let { requestMap["min_p"] = it }
        }
        if (config.supportsTopA) {
            apiRequest.topA?.let { requestMap["top_a"] = it }
        }

        // === FUNCTION CALLING / TOOLS ===
        if (config.supportsFunctionCalling) {
            apiRequest.tools?.let { tools ->
                if (tools.isNotEmpty()) {
                    // Transform tools to Claude format if needed
                    if (config.requiresClaudeThinking) {
                        // Claude requires different tool format
                        val claudeTools = tools.map { tool ->
                            mapOf(
                                "name" to tool.function.name,
                                "description" to tool.function.description,
                                "input_schema" to tool.function.parameters
                            )
                        }
                        requestMap["tools"] = claudeTools
                    } else {
                        // Standard OpenAI format
                        requestMap["tools"] = tools
                    }
                    
                    if (config.supportsToolChoice) {
                        apiRequest.toolChoice?.let { toolChoice ->
                            // Transform tool choice for Claude
                            if (config.requiresClaudeThinking) {
                                when (toolChoice) {
                                    "auto" -> requestMap["tool_choice"] = mapOf("type" to "auto")
                                    "none" -> requestMap["tool_choice"] = mapOf("type" to "none")
                                    is String -> requestMap["tool_choice"] = mapOf("type" to "tool", "name" to toolChoice)
                                    else -> requestMap["tool_choice"] = toolChoice
                                }
                            } else {
                                requestMap["tool_choice"] = toolChoice
                            }
                        }
                    }
                    if (config.supportsParallelToolCalls) {
                        apiRequest.parallelToolCalls?.let { requestMap["parallel_tool_calls"] = it }
                    }
                }
            }
        }

        // === REASONING FEATURES ===
        if (config.supportsReasoning) {
            when (config.reasoningParameter) {
                "reasoning_effort" -> apiRequest.reasoningEffort?.let { 
                    Timber.d("Adding reasoning_effort to request: $it")
                    requestMap["reasoning_effort"] = it 
                }
                "thinking" -> apiRequest.thinking?.let { 
                    Timber.d("Adding thinking to request: $it")
                    requestMap["thinking"] = it 
                }
            }
        }

        // === AUDIO SUPPORT ===
        if (config.supportsAudio && audioEnabled) {
            apiRequest.audio?.let { requestMap["audio"] = it }
            apiRequest.modalities?.let { requestMap["modalities"] = it }
        }

        // === RESPONSE FORMAT ===
        if (config.supportsResponseFormat) {
            apiRequest.responseFormat?.let { requestMap["response_format"] = it }
        }

        // === JSON MODE/SCHEMA (part of response format) ===
        if (config.supportsJsonMode || config.supportsJsonSchema) {
            // JSON mode is typically handled via response_format
            if (!requestMap.containsKey("response_format")) {
                if (config.supportsJsonMode) {
                    // Can be set by specific handlers if needed
                }
            }
        }

        // === LOGPROBS ===
        if (config.supportsLogprobs) {
            apiRequest.logprobs?.let { requestMap["logprobs"] = it }
            if (config.supportsTopLogprobs) {
                apiRequest.topLogprobs?.let { requestMap["top_logprobs"] = it }
            }
        }

        // === SEED ===
        if (config.supportsSeed) {
            apiRequest.seed?.let { requestMap["seed"] = it }
        }

        // === PREDICTION ===
        // Note: prediction property not yet implemented in AimlApiRequest
        // if (config.supportsPrediction) {
        //     apiRequest.prediction?.let { requestMap["prediction"] = it }
        // }

        // === STOP SEQUENCES ===
        // Note: stopSequences property not yet implemented in AimlApiRequest
        // if (config.supportsStopSequences) {
        //     apiRequest.stopSequences?.let { stops ->
        //         val limitedStops = stops.take(config.maxStopSequences)
        //         requestMap["stop"] = limitedStops
        //     }
        // }

        // === N PARAMETER (number of completions) ===
        // Note: n property not yet implemented in AimlApiRequest
        // if (config.supportsN) {
        //     apiRequest.n?.let { requestMap["n"] = it }
        // }

        // === WEB SEARCH OPTIONS ===
        if (config.supportsWebSearchOptions) {
            apiRequest.webSearchOptions?.let { requestMap["web_search_options"] = it }
        }

        return requestMap
    }

    // === MODEL-SPECIFIC HANDLERS (all using config values) ===

    private fun handleO1Model(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // O1 specific: no streaming support, use max_completion_tokens
        requestMap.remove("stream")
        requestMap.remove("stream_options")

        // Ensure reasoning effort is set for O1
        if (config.supportsReasoning && !requestMap.containsKey("reasoning_effort")) {
            requestMap["reasoning_effort"] = "medium"
        }

        // Use max token limit for O1 (typically larger)
        if (config.requiresMaxCompletionTokens) {
            requestMap["max_completion_tokens"] = config.maxOutputTokens
        }
    }

    private fun handleO3MiniModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // O3-mini supports streaming
        requestMap["stream"] = config.supportsStreaming
        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Ensure reasoning effort for O3
        if (config.supportsReasoning && !requestMap.containsKey("reasoning_effort")) {
            requestMap["reasoning_effort"] = "medium"
        }
    }

    private fun handleGPT4OModel(requestMap: MutableMap<String, Any?>, config: ModelConfig, audioEnabled: Boolean) {
        // GPT-4o optimizations using config values
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature

        // Streaming
        requestMap["stream"] = config.supportsStreaming
        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // N parameter for multiple completions (if needed, add directly)
        if (config.supportsN) {
            requestMap["n"] = 1
        }
    }

    private fun handleGPT4O20240513Model(requestMap: MutableMap<String, Any?>, config: ModelConfig, audioEnabled: Boolean) {
        // Version-specific optimizations
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Default response format for this version
        if (config.supportsResponseFormat && !requestMap.containsKey("response_format")) {
            requestMap["response_format"] = mapOf("type" to "text")
        }

        // Add n parameter directly for OpenAI models
        requestMap["n"] = 1
    }

    private fun handleGPT4O20240806Model(requestMap: MutableMap<String, Any?>, config: ModelConfig, audioEnabled: Boolean) {
        // Latest GPT-4o version with enhanced features
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Enhanced tool support
        if (requestMap.containsKey("tools") && (requestMap["tools"] as? List<*>)?.isNotEmpty() == true) {
            if (config.supportsToolChoice) {
                requestMap["tool_choice"] = requestMap["tool_choice"] ?: "auto"
            }
            if (config.supportsParallelToolCalls) {
                requestMap["parallel_tool_calls"] = true
            }
        }

        // Auto-generate seed if not provided
        if (config.supportsSeed && !requestMap.containsKey("seed")) {
            requestMap["seed"] = System.currentTimeMillis()
        }

        if (config.supportsN) {
            requestMap["n"] = 1
        }
    }

    private fun handleGPT4OMiniModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // GPT-4o-mini optimizations
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        if (config.supportsN) {
            requestMap["n"] = 1
        }
    }

    private fun handleGPT41MiniModel(requestMap: MutableMap<String, Any?>, config: ModelConfig, audioEnabled: Boolean) {
        // GPT-4.1-mini with audio support
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        if (config.supportsN) {
            requestMap["n"] = 1
        }
    }

    private fun handleGPT4TurboModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // GPT-4-turbo settings
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        if (config.supportsN) {
            requestMap["n"] = 1
        }
    }

    private fun handleGPT4Turbo20240409Model(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Version-specific GPT-4 Turbo
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Tool support
        if (requestMap.containsKey("tools") && (requestMap["tools"] as? List<*>)?.isNotEmpty() == true) {
            if (config.supportsToolChoice) {
                requestMap["tool_choice"] = requestMap["tool_choice"] ?: "auto"
            }
        }

        if (config.supportsN) {
            requestMap["n"] = 1
        }
    }

    private fun handleClaudeModel(requestMap: MutableMap<String, Any?>, apiRequest: AimlApiRequest, config: ModelConfig) {
        // Claude-specific handling
        requestMap["anthropic_version"] = "2023-06-01"

        // Handle system messages differently for Claude
        val messages = requestMap["messages"] as? List<*>
        val systemMessage = messages?.firstOrNull {
            (it as? Map<*, *>)?.get("role") == "system"
        } as? Map<*, *>

        if (systemMessage != null) {
            requestMap["system"] = systemMessage["content"] as Any
            requestMap["messages"] = messages.filter {
                (it as? Map<*, *>)?.get("role") != "system"
            }
        }

        // Claude streaming
        requestMap["stream"] = config.supportsStreaming

        // Note: Tool transformation is now handled in buildSupportedParametersMap

        // Check if thinking is enabled (from createRequest)
        val isThinkingEnabled = requestMap.containsKey("thinking") ||
                (apiRequest.thinking != null && apiRequest.thinking.type == "enabled")

        if (isThinkingEnabled) {
            // Thinking mode: use safe token limit for max_tokens (thinking budget already set correctly in createRequest)
            // Ensure we don't exceed Claude's 100,000 completion token limit
            val safeMaxTokens = minOf(config.defaultMaxTokens, 100000)
            requestMap["max_tokens"] = safeMaxTokens
            requestMap["temperature"] = 1.0
            requestMap["top_p"] = 1.0

            // CRITICAL: Remove top_k when thinking is enabled (Claude API requirement)
            requestMap.remove("top_k")

            // Note: thinking configuration already set correctly in createRequest with proper budget
        } else {
            // Normal mode: standard settings
            val safeMaxTokens = minOf(config.defaultMaxTokens, 100000)
            requestMap["max_tokens"] = safeMaxTokens
            requestMap["temperature"] = config.defaultTemperature
            requestMap["top_p"] = config.defaultTopP
        }
    }

    private fun handleLlama32Model(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Llama 3.2 optimizations using config values
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Llama-specific parameters with config-based checks
        if (config.supportsTopK && !requestMap.containsKey("top_k")) {
            requestMap["top_k"] = 40
        }
        if (config.supportsRepetitionPenalty && !requestMap.containsKey("repetition_penalty")) {
            requestMap["repetition_penalty"] = 1.0
        }
    }

    private fun handleLlama4Model(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Llama 4 settings using config
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        if (config.supportsTopK && !requestMap.containsKey("top_k")) {
            requestMap["top_k"] = 40
        }
        if (config.supportsMinP && !requestMap.containsKey("min_p")) {
            requestMap["min_p"] = 0.05
        }
    }

    private fun handleGemmaModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Google Gemma settings using config
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Gemma-specific parameters
        if (config.supportsTopK && !requestMap.containsKey("top_k")) {
            requestMap["top_k"] = 40
        }
        if (config.supportsTopA && !requestMap.containsKey("top_a")) {
            requestMap["top_a"] = 0.0
        }
    }

    private fun handleCohereModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Cohere Command R+ using config values
        requestMap["max_tokens"] = config.defaultMaxTokens
        requestMap["temperature"] = config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming
        requestMap["top_p"] = config.defaultTopP

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Cohere-specific optimizations
        if (config.supportsTopK) {
            requestMap["top_k"] = 0
        }
        if (config.supportsMinP && !requestMap.containsKey("min_p")) {
            requestMap["min_p"] = 0.0
        }
        if (config.supportsTopA && !requestMap.containsKey("top_a")) {
            requestMap["top_a"] = 0.0
        }
    }

    private fun handleDeepSeekModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // DeepSeek R1 using config
        requestMap["max_tokens"] = config.defaultMaxTokens
        requestMap["temperature"] = config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }
    }

    private fun handleQwen3Model(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Qwen 3 235B using config values
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        // Tool handling
        if (requestMap.containsKey("tools") && (requestMap["tools"] as? List<*>)?.isNotEmpty() == true) {
            if (config.supportsToolChoice) {
                requestMap["tool_choice"] = "auto"
            }
            if (config.supportsParallelToolCalls) {
                requestMap["parallel_tool_calls"] = true
            }
        }
    }

    private fun handleQwen25Model(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Qwen 2.5 72B using config
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        if (requestMap.containsKey("tools")) {
            if (config.supportsToolChoice) {
                requestMap["tool_choice"] = "auto"
            }
            if (config.supportsParallelToolCalls) {
                requestMap["parallel_tool_calls"] = true
            }
        }
    }

    private fun handleGrokModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // xAI Grok settings using config values
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to config.streamIncludeUsage)
        }

        if (requestMap.containsKey("tools")) {
            if (config.supportsToolChoice) {
                requestMap["tool_choice"] = "auto"
            }
        }
    }

    private fun handleMixtralModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Mistral Mixtral using config
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        // VALIDATION: Remove empty tools (prevents API errors)
        // This is data validation, not capability filtering
        if (requestMap.containsKey("tools")) {
            val tools = requestMap["tools"] as? List<*>
            if (tools.isNullOrEmpty()) {
                requestMap.remove("tools")      // Empty tools array is meaningless
                requestMap.remove("tool_choice") // Can't choose from empty tools
            }
        }
    }

    private fun handleMistralOCRModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Mistral OCR using config
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming
    }

    // === HELPER METHODS (unchanged) ===

    private fun getOptimalSearchContentSize(modelId: String, timeParams: Map<String, String>): String {
        val isRecentSearch = timeParams["freshness"] in listOf("hour", "day", "recent")

        return when {
            isRecentSearch -> "high"
            modelId.contains("gpt-4", ignoreCase = true) -> "high"
            modelId.contains("o1", ignoreCase = true) -> "medium"
            modelId.contains("claude", ignoreCase = true) -> "high"
            modelId.contains("llama", ignoreCase = true) -> "medium"
            else -> "medium"
        }
    }

    private fun getOptimalSearchResults(modelId: String): Int {
        return when {
            modelId.contains("gpt-4", ignoreCase = true) -> 5
            modelId.contains("o1", ignoreCase = true) -> 3
            modelId.contains("grok", ignoreCase = true) -> 5
            else -> 4
        }
    }

    private fun createWebSearchTool(): AimlApiRequest.Tool {
        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("query", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The search query to find current information on the web")
                })
            })
            add("required", JsonArray().apply { add("query") })
        }

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "web_search",
                description = """Search the web for current information. After receiving search results, 
                synthesize them into a natural, conversational response. Do not show raw search results. 
                Use inline citations [1], [2], etc. when referencing specific sources.""",
                parameters = parameters
            )
        )
    }    /**
     * Create the calculator tool for mathematical operations
     */
    private fun createCalculatorTool(): AimlApiRequest.Tool {
        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("expression", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The mathematical expression to calculate, like '2+2', '5*10/2', 'sqrt(16)', etc.")
                })
                add("mode", JsonObject().apply {
                    addProperty("type", "string")
                    // Create a proper JSON array for enum values
                    add("enum", JsonArray().apply {
                        add("basic")
                        add("scientific")
                        add("conversion")
                    })
                    addProperty("description", "The calculation mode: 'basic' for simple operations, 'scientific' for advanced functions, 'conversion' for unit conversions")
                })
            })
            add("required", JsonArray().apply { add("expression") })
        }

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "calculator",
                description = "Calculate mathematical expressions, solve equations, evaluate formulas, and perform unit conversions.",
                parameters = parameters
            )
        )
    }

    /**
     * Create the spreadsheet tool for creating structured data
     */
    private fun createSpreadsheetTool(): AimlApiRequest.Tool {
        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("title", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The title of the spreadsheet")
                })
                add("headers", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Comma-separated list of column headers")
                })
                add("data", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Structured data for the spreadsheet; can be sample/random data if not specified")
                })
                add("format", JsonObject().apply {
                    addProperty("type", "string")
                    // Proper JSON array
                    add("enum", JsonArray().apply {
                        add("table")
                        add("budget")
                        add("schedule")
                        add("inventory")
                    })
                    addProperty("description", "The type of spreadsheet to create")
                })
            })
            add("required", JsonArray().apply { add("title") })
        }

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "spreadsheet_creator",
                description = "Creates spreadsheets with tables, data, and formulas based on user requirements. Can generate sample data if needed.",
                parameters = parameters
            )
        )
    }

    /**
     * Create the weather tool for weather information
     */
    private fun createWeatherTool(): AimlApiRequest.Tool {
        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("location", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The city, region, or location to get weather information for")
                })
                add("forecast", JsonObject().apply {
                    addProperty("type", "boolean")
                    addProperty("description", "Set to true to get forecast for upcoming days, false for current weather only")
                })
                add("units", JsonObject().apply {
                    addProperty("type", "string")
                    // Proper JSON array
                    add("enum", JsonArray().apply {
                        add("metric")
                        add("imperial")
                    })
                    addProperty("description", "Temperature units: 'metric' for Celsius, 'imperial' for Fahrenheit")
                })
            })
            add("required", JsonArray().apply { add("location") })
        }

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "weather",
                description = "Retrieves current weather conditions and forecasts for specified locations. Provides temperature, conditions, humidity, and wind information.",
                parameters = parameters
            )
        )
    }
    /**
     * Create the translator tool for language translation
     */
    private fun createTranslationTool(): AimlApiRequest.Tool {
        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("text", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The text to translate")
                })
                add("sourceLanguage", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Source language code (e.g., 'en', 'fr', 'es') or 'auto' for automatic detection")
                })
                add("targetLanguage", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Target language code (e.g., 'en', 'fr', 'es')")
                })
            })
            add("required", JsonArray().apply {
                add("text")
                add("targetLanguage")
            })
        }

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "translator",
                description = "Translates text between different languages. Can automatically detect the source language if not specified.",
                parameters = parameters
            )
        )
    }

    /**
     * Create the Wikipedia tool for knowledge retrieval
     */
    private fun createWikipediaTool(): AimlApiRequest.Tool {
        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("query", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The topic, concept, person, place, or entity to search for information about")
                })
                add("language", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Language code for Wikipedia (default is 'en' for English)")
                })
                add("summaryLength", JsonObject().apply {
                    addProperty("type", "string")
                    // Proper JSON array
                    add("enum", JsonArray().apply {
                        add("short")
                        add("medium")
                        add("full")
                    })
                    addProperty("description", "Length of the summary to retrieve")
                })
            })
            add("required", JsonArray().apply { add("query") })
        }

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "wikipedia",
                description = "Retrieves information from Wikipedia for topics, people, places, and concepts. Provides factual, encyclopedic knowledge.",
                parameters = parameters
            )
        )
    }

    private fun createBasicSystemMessage(
        userLocation: AimlApiRequest.UserLocation? = null
    ): AimlApiRequest.Message {
        val currentDate = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())
        val locationContext = if (userLocation != null) {
            "The user is in ${userLocation.approximate.city}, ${userLocation.approximate.region}, ${userLocation.approximate.country}."
        } else ""

        val systemPrompt = """You are a helpful AI assistant.
Today is $currentDate.
$locationContext
Provide detailed and accurate responses to the user's questions."""

        return AimlApiRequest.Message("system", systemPrompt.trim())
    }

    // In ModelApiHandler.kt - Update createWebSearchSystemMessage
    private fun createWebSearchSystemMessage(
        userLocation: AimlApiRequest.UserLocation? = null
    ): AimlApiRequest.Message {
        val currentDate = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())

        val locationContext = if (userLocation != null) {
            """The user is located in ${userLocation.approximate.city}, ${userLocation.approximate.region}, ${userLocation.approximate.country}.
User's timezone is ${userLocation.approximate.timezone}."""
        } else {
            "Location information is unavailable."
        }

        val systemPrompt = """You are a helpful AI assistant with real-time web search capabilities.
$locationContext
Today is $currentDate.

IMPORTANT WEB SEARCH INSTRUCTIONS:
- When you call the web_search tool, you will receive search results directly in the conversation
- Use these results to provide current, accurate information
- ALWAYS cite sources using [1], [2], [3] etc. inline when referencing search results
- Synthesize the information naturally - don't just list the results
- If web search is triggered but you don't see results, continue based on your knowledge

Example: If searching for news, after receiving results, say something like:
"Based on the latest news, here's what's happening: [specific information from search] [1]. Additionally, [more info] [2]."

Never say you cannot access real-time information if web search has been performed."""

        return AimlApiRequest.Message("system", systemPrompt)
    }




    private fun transformMessagesForCompatibility(
        systemMessage: AimlApiRequest.Message,
        messages: List<AimlApiRequest.Message>
    ): List<AimlApiRequest.Message> {
        val transformedMessages = mutableListOf<AimlApiRequest.Message>()
        var systemAdded = false

        for (message in messages) {
            when (message.role) {
                "user" -> {
                    if (!systemAdded) {
                        transformedMessages.add(AimlApiRequest.Message(
                            role = "user",
                            content = "SYSTEM INSTRUCTIONS: ${systemMessage.content}\n\nUSER QUERY: ${message.content}"
                        ))
                        systemAdded = true
                    } else {
                        transformedMessages.add(message)
                    }
                }
                else -> transformedMessages.add(message)
            }
        }

        if (!systemAdded) {
            transformedMessages.add(0, AimlApiRequest.Message(
                role = "user",
                content = "SYSTEM INSTRUCTIONS: ${systemMessage.content}"
            ))
        }

        return transformedMessages
    }
}