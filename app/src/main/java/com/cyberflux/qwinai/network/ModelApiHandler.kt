// Complete Production-Ready ModelApiHandler.kt with ALL parameters
package com.cyberflux.qwinai.network

import android.content.Context
import com.cyberflux.qwinai.model.ModelConfig
import com.cyberflux.qwinai.utils.LocationService
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PerplexityPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

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

        // Note: Perplexity models have built-in search, others get web search tools
        val isPerplexityModel = modelId.contains("perplexity", ignoreCase = true)
        
        Timber.d("Web search tools: enabled=${!isPerplexityModel && config.supportsFunctionCalling}, explicit=$isWebSearch")

        // Location handling
        val userLocation = try {
            LocationService.getApproximateLocation(context)
        } catch (e: Exception) {
            Timber.e(e, "Location error: ${e.message}")
            LocationService.getSystemDefaultLocation()
        }

        // Smart tool inclusion - only add tools when likely needed to reduce token usage  
        val webSearchTools = if (config.supportsFunctionCalling && !isPerplexityModel) {
            val tools = mutableListOf<AimlApiRequest.Tool>()

            // Only add web search tool if web search is explicitly requested or message indicates need for current info
            if (isWebSearch || messageText?.let { text ->
                text.contains("current", ignoreCase = true) ||
                text.contains("latest", ignoreCase = true) ||
                text.contains("recent", ignoreCase = true) ||
                text.contains("news", ignoreCase = true) ||
                text.contains("today", ignoreCase = true) ||
                text.contains("now", ignoreCase = true)
            } == true) {
                tools.add(createWebSearchTool())
            }

            // Selectively add other tools based on message content to reduce token usage
            messageText?.let { text ->
                val lowerText = text.lowercase()
                if (ModelValidator.supportsMultipleTools(modelId)) {
                    if (lowerText.contains("calculate") || lowerText.contains("math") || lowerText.contains("equation")) {
                        tools.add(createCalculatorTool())
                    }
                    if (lowerText.contains("weather") || lowerText.contains("temperature") || lowerText.contains("forecast")) {
                        tools.add(createWeatherTool())
                    }
                    if (lowerText.contains("translate") || lowerText.contains("language")) {
                        tools.add(createTranslationTool())
                    }
                    if (lowerText.contains("wikipedia") || lowerText.contains("definition") || lowerText.contains("who is") || lowerText.contains("what is")) {
                        tools.add(createWikipediaTool())
                    }
                    if (lowerText.contains("spreadsheet") || lowerText.contains("table") || lowerText.contains("csv") || lowerText.contains("excel")) {
                        tools.add(createSpreadsheetTool())
                    }
                }
            }

            tools.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        // Build request with common parameters
        AimlApiRequest(
            model = modelId,
            messages = messages,
            thinking = if (config.requiresClaudeThinking && config.supportsReasoning && isReasoningEnabled) {
                // CLAUDE: Button-controlled thinking - user can enable/disable
                val safeMaxTokens = minOf(config.defaultMaxTokens, 100000)
                val thinkingBudget = minOf((safeMaxTokens * 0.6).toInt(), 60000) // Cap at 60k thinking tokens
                Timber.d("Claude thinking ENABLED by button - reasoning enabled: $isReasoningEnabled, budget: $thinkingBudget")
                AimlApiRequest.ClaudeThinking.create(thinkingBudget)
            } else {
                Timber.d("Claude thinking DISABLED - reasoning button: $isReasoningEnabled")
                null
            },
            zhipuThinking = if (modelId == "zhipu/glm-4.5" && config.supportsReasoning && isReasoningEnabled) {
                // ZHIPU: Button-controlled thinking - user can enable/disable
                Timber.d("🧠 ZhiPu thinking ENABLED by button - modelId: $modelId, supportsReasoning: ${config.supportsReasoning}, reasoningEnabled: $isReasoningEnabled")
                AimlApiRequest.ZhipuThinking.enabled()
            } else {
                Timber.d("🧠 ZhiPu thinking DISABLED - modelId: $modelId, supportsReasoning: ${config.supportsReasoning}, reasoningEnabled: $isReasoningEnabled")
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
            topLogprobs = if (config.supportsLogprobs && config.supportsTopLogprobs) 1 else null,
            seed = if (config.supportsSeed) System.currentTimeMillis() else null,
            tools = webSearchTools,
            toolChoice = if (config.supportsFunctionCalling && config.supportsToolChoice) "auto" else null,
            webSearchOptions = null, // Removed - let AI decide when/how to search
            reasoningEffort = if (config.reasoningParameter == "reasoning_effort" && config.supportsReasoning) {
                // Use the provided reasoning level from UI
                reasoningLevel
            } else null,
            // NOTE: chainOfThought removed - was for Qwen which doesn't provide separate thinking content
            audio = if (audioEnabled && config.supportsAudio) {
                AimlApiRequest.AudioOptions(format = audioFormat, voice = voiceType)
            } else null,
            modalities = if (audioEnabled && config.supportsAudio) {
                listOf("text", "audio")
            } else null,
            responseFormat = if (config.supportsResponseFormat) null else null,
            topA = if (config.supportsTopA) null else null,
            parallelToolCalls = if (config.supportsParallelToolCalls && config.supportsFunctionCalling) true else null,
            minP = if (config.supportsMinP) null else null,
            useWebSearch = isWebSearch
        )
    }
    fun createRequestBody(
        apiRequest: AimlApiRequest,
        modelId: String,
        context: Context? = null,
        audioEnabled: Boolean = false
    ): RequestBody {
        val config = ModelConfigManager.getConfig(modelId)
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        Timber.d("Creating request body for model: $modelId, provider: ${config.provider}")

        // Build request map with only supported parameters
        val requestMap = buildSupportedParametersMap(apiRequest, config, audioEnabled)
        
        // NOTE: Qwen thinking parameters removed - Qwen doesn't provide separate thinking content
        // Qwen includes reasoning in regular response content, can't be separated into thinking layout

        // Apply model-specific customizations
        try {
            when (modelId) {
                // === OPENAI MODELS ===
                ModelManager.GPT_4O_ID -> handleGPT4OModel(requestMap, config)
                ModelManager.GPT_4_TURBO_ID -> handleGPT4TurboModel(requestMap, config)

                // === ANTHROPIC MODELS ===
                ModelManager.CLAUDE_3_7_SONNET_ID -> handleClaudeModel(requestMap, apiRequest, config)

                // === META MODELS ===
                ModelManager.LLAMA_4_MAVERICK_ID -> handleLlama4Model(requestMap, config)

                // === GOOGLE MODELS ===
                ModelManager.GEMMA_27B_INSTRUCT_ID -> handleGemmaModel(requestMap, config)

                // === COHERE MODELS ===
                ModelManager.COHERE_COMMAND_R_PLUS_ID -> handleCohereModel(requestMap, config)

                // === DEEPSEEK MODELS ===
                ModelManager.DEEPSEEK_R1_ID -> handleDeepSeekModel(requestMap, config)

                // === QWEN MODELS ===
                ModelManager.QWEN_3_235B_ID -> handleQwen3Model(requestMap, config)

                // === XAI MODELS ===
                ModelManager.GROK_3_BETA_ID -> handleGrokModel(requestMap, config)

                // === MISTRAL MODELS ===
                ModelManager.MISTRAL_OCR_ID -> handleMistralOCRModel(requestMap, config)

                // === PERPLEXITY MODELS ===
                ModelManager.PERPLEXITY_SONAR_PRO_ID -> handlePerplexityModel(requestMap, config, context)

                // === ZHIPU MODELS ===
                ModelManager.ZHIPU_GLM_4_5_ID -> handleZhipuModel(requestMap, config)

                else -> {
                    Timber.w("Using default handling for unknown model: $modelId")
                    // No additional customization needed since we only include supported params
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in model-specific handler for $modelId")
            // Continue with basic request map
        }

        // Convert requestMap to JSON manually to avoid Moshi serialization issues
        val jsonString = convertMapToJson(requestMap)
        
        // Enhanced logging for thinking models
        if (modelId.contains("zhipu", ignoreCase = true) || modelId.contains("qwen", ignoreCase = true)) {
            Timber.d("🧠 THINKING MODEL Request for $modelId:")
            Timber.d("🧠 Full JSON: ${jsonString.take(1000)}...")
            // Extract thinking-related parameters for clear visibility
            val thinkingParams = requestMap.filterKeys { key ->
                key.contains("thinking", ignoreCase = true) || 
                key.contains("reasoning", ignoreCase = true) ||
                key.contains("chain_of_thought", ignoreCase = true)
            }
            Timber.d("🧠 Thinking parameters: $thinkingParams")
        } else {
            Timber.d("Final request body for $modelId: ${jsonString.take(500)}...")
        }

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
            if (config.supportsStreamOptions && apiRequest.streamOptions != null) {
                // Convert StreamOptions data class to Map for API compatibility
                val streamOptionsMap = mutableMapOf<String, Boolean>()
                apiRequest.streamOptions.includeUsage?.let { 
                    if (it) streamOptionsMap["include_usage"] = it 
                }
                apiRequest.streamOptions.includeReasoning?.let { 
                    if (it) streamOptionsMap["include_reasoning"] = it 
                }
                apiRequest.streamOptions.includeMetadata?.let { 
                    if (it) streamOptionsMap["include_metadata"] = it 
                }
                if (streamOptionsMap.isNotEmpty()) {
                    requestMap["stream_options"] = streamOptionsMap
                }
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
                "thinking" -> {
                    // Handle Claude thinking
                    apiRequest.thinking?.let { 
                        Timber.d("Adding Claude thinking to request: $it")
                        requestMap["thinking"] = it 
                    }
                    // Handle ZhiPu thinking - send as object 
                    apiRequest.zhipuThinking?.let { zhipuThinking ->
                        Timber.d("🧠 Adding ZhiPu thinking to request: $zhipuThinking")
                        // ZhiPu API expects thinking parameter as object {"type": "enabled"}
                        requestMap["thinking"] = mapOf("type" to zhipuThinking.type)
                    }
                    
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
            apiRequest.logprobs?.let { logprobs ->
                requestMap["logprobs"] = logprobs
                // Only add top_logprobs if logprobs is actually enabled (true)
                if (logprobs == true && config.supportsTopLogprobs) {
                    apiRequest.topLogprobs?.let { requestMap["top_logprobs"] = it }
                }
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


    private fun handleGPT4OModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
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
            requestMap["min_p"] = 0.001
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


    private fun handleMistralOCRModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        // Mistral OCR using config
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming
    }

    private fun handlePerplexityModel(requestMap: MutableMap<String, Any?>, config: ModelConfig, context: Context?) {
        // Perplexity Sonar Pro with built-in web search
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming

        // Remove function calling tools - Perplexity uses built-in web search
        requestMap.remove("tools")
        requestMap.remove("tool_choice")

        // Configure Perplexity-specific web search options
        val webSearchOptions = mutableMapOf<String, Any>()
        
        // Get user preferences
        val searchMode = if (context != null) {
            PerplexityPreferences.getSearchMode(context)
        } else {
            PerplexityPreferences.SEARCH_MODE_WEB
        }
        
        val contextSize = if (context != null) {
            PerplexityPreferences.getContextSize(context)
        } else {
            PerplexityPreferences.CONTEXT_SIZE_MEDIUM
        }
        
        // Set context size based on user preference
        webSearchOptions["search_context_size"] = contextSize
        webSearchOptions["return_images"] = true
        webSearchOptions["return_related_questions"] = true
        
        // Location for better search results
        if (context != null) {
            try {
                val userLocation = LocationService.getSystemDefaultLocation()
                val locationMap = mapOf(
                    "approximate" to mapOf(
                        "city" to userLocation.approximate.city,
                        "country" to userLocation.approximate.country,
                        "region" to userLocation.approximate.region,
                        "timezone" to userLocation.approximate.timezone
                    ),
                    "type" to "approximate"
                )
                webSearchOptions["user_location"] = locationMap
            } catch (e: Exception) {
                Timber.w("Could not get location for Perplexity search: ${e.message}")
            }
        }

        requestMap["web_search_options"] = webSearchOptions
        requestMap["search_mode"] = searchMode
        
        Timber.d("Perplexity request configured: search_mode=$searchMode, context_size=$contextSize")
    }

    /**
     * Configure ZhiPu GLM-4.5 with built-in web search and thinking support
     */
    private fun handleZhipuModel(requestMap: MutableMap<String, Any?>, config: ModelConfig) {
        Timber.d("Configuring ZhiPu GLM-4.5 model")
        
        // Set ZhiPu-specific parameters
        requestMap["max_completion_tokens"] = requestMap["max_completion_tokens"] ?: config.defaultMaxTokens
        requestMap["max_tokens"] = requestMap["max_tokens"] ?: config.defaultMaxTokens
        requestMap["temperature"] = requestMap["temperature"] ?: config.defaultTemperature
        requestMap["stream"] = config.supportsStreaming
        
        // Configure ZhiPu's built-in web search tool
        val zhipuTools = mutableListOf<Map<String, Any>>()
        
        // Add ZhiPu's web search tool
        val webSearchTool = mapOf(
            "type" to "web_search",
            "web_search" to mapOf(
                "search_engine" to "search_pro_jina",
                "enable" to true,
                "search_query" to "",
                "count" to 5,
                "search_result" to true,
                "require_search" to false
            )
        )
        zhipuTools.add(webSearchTool)
        
        // Also add function tools for other capabilities
        requestMap["tools"]?.let { existingTools ->
            if (existingTools is List<*>) {
                // Convert function tools to ZhiPu format
                existingTools.filterIsInstance<AimlApiRequest.Tool>().forEach { tool ->
                    if (tool.type == "function") {
                        val zhipuFunctionTool = mapOf(
                            "type" to "function",
                            "function" to mapOf(
                                "name" to tool.function.name,
                                "description" to tool.function.description,
                                "parameters" to tool.function.parameters
                            )
                        )
                        zhipuTools.add(zhipuFunctionTool)
                    }
                }
            }
        }
        
        requestMap["tools"] = zhipuTools
        requestMap["tool_choice"] = "auto"
        requestMap["parallel_tool_calls"] = true
        
        // Configure stream options
        if (config.supportsStreamOptions) {
            requestMap["stream_options"] = mapOf("include_usage" to true)
        }
        
        Timber.d("ZhiPu GLM-4.5 configured with ${zhipuTools.size} tools")
    }

    // === HELPER METHODS (unchanged) ===



    private fun createWebSearchTool(): AimlApiRequest.Tool {
        val parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf(
                    "type" to "string",
                    "description" to "The search query to find current information on the web"
                )
            ),
            "required" to listOf("query")
        )

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "web_search",
                description = "Search the web for current information.",
                parameters = parameters
            )
        )
    }    /**
     * Create the calculator tool for mathematical operations
     */
    private fun createCalculatorTool(): AimlApiRequest.Tool {
        val parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "expression" to mapOf(
                    "type" to "string",
                    "description" to "The mathematical expression to calculate, like '2+2', '5*10/2', 'sqrt(16)', etc."
                ),
                "mode" to mapOf(
                    "type" to "string",
                    "enum" to listOf("basic", "scientific", "conversion"),
                    "description" to "The calculation mode: 'basic' for simple operations, 'scientific' for advanced functions, 'conversion' for unit conversions"
                )
            ),
            "required" to listOf("expression")
        )

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
        val parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf(
                    "type" to "string",
                    "description" to "The title of the spreadsheet"
                ),
                "headers" to mapOf(
                    "type" to "string",
                    "description" to "Comma-separated list of column headers"
                ),
                "data" to mapOf(
                    "type" to "string",
                    "description" to "Structured data for the spreadsheet; can be sample/random data if not specified"
                ),
                "format" to mapOf(
                    "type" to "string",
                    "enum" to listOf("table", "budget", "schedule", "inventory"),
                    "description" to "The type of spreadsheet to create"
                )
            ),
            "required" to listOf("title")
        )

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
        val parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "location" to mapOf(
                    "type" to "string",
                    "description" to "The city, region, or location to get weather information for"
                ),
                "forecast" to mapOf(
                    "type" to "boolean",
                    "description" to "Set to true to get forecast for upcoming days, false for current weather only"
                ),
                "units" to mapOf(
                    "type" to "string",
                    "enum" to listOf("metric", "imperial"),
                    "description" to "Temperature units: 'metric' for Celsius, 'imperial' for Fahrenheit"
                )
            ),
            "required" to listOf("location")
        )

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
        val parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "text" to mapOf(
                    "type" to "string",
                    "description" to "The text to translate"
                ),
                "sourceLanguage" to mapOf(
                    "type" to "string",
                    "description" to "Source language code (e.g., 'en', 'fr', 'es') or 'auto' for automatic detection"
                ),
                "targetLanguage" to mapOf(
                    "type" to "string",
                    "description" to "Target language code (e.g., 'en', 'fr', 'es')"
                )
            ),
            "required" to listOf("text", "targetLanguage")
        )

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
        val parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf(
                    "type" to "string",
                    "description" to "The topic, concept, person, place, or entity to search for information about"
                ),
                "language" to mapOf(
                    "type" to "string",
                    "description" to "Language code for Wikipedia (default is 'en' for English)"
                ),
                "summaryLength" to mapOf(
                    "type" to "string",
                    "enum" to listOf("short", "medium", "full"),
                    "description" to "Length of the summary to retrieve"
                )
            ),
            "required" to listOf("query")
        )

        return AimlApiRequest.Tool(
            type = "function",
            function = AimlApiRequest.ToolFunction(
                name = "wikipedia",
                description = "Retrieves information from Wikipedia for topics, people, places, and concepts. Provides factual, encyclopedic knowledge.",
                parameters = parameters
            )
        )
    }


    /**
     * Helper function to recursively convert Maps to JSONObjects
     */
    private fun convertMapToJsonObject(map: Map<String, Any?>): JSONObject {
        val jsonObj = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is String -> jsonObj.put(key, value)
                is Number -> jsonObj.put(key, value)
                is Boolean -> jsonObj.put(key, value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedMap = value as Map<String, Any?>
                    jsonObj.put(key, convertMapToJsonObject(nestedMap))
                }
                is List<*> -> {
                    val jsonArray = JSONArray()
                    for (item in value) {
                        when (item) {
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                val itemMap = item as Map<String, Any?>
                                jsonArray.put(convertMapToJsonObject(itemMap))
                            }
                            else -> jsonArray.put(item)
                        }
                    }
                    jsonObj.put(key, jsonArray)
                }
                null -> {
                    // Skip null values
                }
                else -> jsonObj.put(key, value)
            }
        }
        return jsonObj
    }

    /**
     * Convert request map to JSON manually to avoid Moshi serialization issues
     * This ensures proper JSON format without requiring KotlinJsonAdapterFactory
     */
    private fun convertMapToJson(requestMap: Map<String, Any?>): String {
        val json = JSONObject()
        
        for ((key, value) in requestMap) {
            when (value) {
                is String -> json.put(key, value)
                is Number -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is List<*> -> {
                    if (key == "messages") {
                        // Handle messages array specially
                        val messagesArray = JSONArray()
                        @Suppress("UNCHECKED_CAST")
                        val messages = value as List<AimlApiRequest.Message>
                        for (message in messages) {
                            val messageObj = JSONObject()
                            messageObj.put("role", message.role)
                            messageObj.put("content", message.content)
                            messagesArray.put(messageObj)
                        }
                        json.put(key, messagesArray)
                    } else if (key == "tools") {
                        // Handle tools array specially - support Tool objects, Claude-format Maps, and ZhiPu tools
                        val toolsArray = JSONArray()
                        @Suppress("UNCHECKED_CAST")
                        val toolsList = value as List<*>
                        
                        for (toolItem in toolsList) {
                            when (toolItem) {
                                is AimlApiRequest.Tool -> {
                                    // Handle OpenAI-style Tool objects
                                    val toolObj = JSONObject()
                                    toolObj.put("type", toolItem.type)
                                    val functionObj = JSONObject()
                                    functionObj.put("name", toolItem.function.name)
                                    functionObj.put("description", toolItem.function.description)
                                    
                                    val parametersJson = when (val params = toolItem.function.parameters) {
                                        is String -> {
                                            try {
                                                JSONObject(params as String)
                                            } catch (e: Exception) {
                                                Timber.w("Invalid JSON string for parameters: $params")
                                                JSONObject()
                                            }
                                        }
                                        is Map<*, *> -> {
                                            try {
                                                @Suppress("UNCHECKED_CAST")
                                                val stringMap = params as Map<String, Any?>
                                                convertMapToJsonObject(stringMap)
                                            } catch (e: Exception) {
                                                Timber.w("Failed to convert Map to JSONObject: $params")
                                                JSONObject()
                                            }
                                        }
                                        else -> {
                                            try {
                                                JSONObject(params.toString() as String)
                                            } catch (e: Exception) {
                                                Timber.w("Failed to parse parameters: $params")
                                                JSONObject()
                                            }
                                        }
                                    }
                                    functionObj.put("parameters", parametersJson)
                                    toolObj.put("function", functionObj)
                                    toolsArray.put(toolObj)
                                }
                                is Map<*, *> -> {
                                    // Handle Map-based tools (Claude format, ZhiPu format, etc.)
                                    @Suppress("UNCHECKED_CAST")
                                    val toolMap = toolItem as Map<String, Any?>
                                    
                                    if (toolMap.containsKey("name") && toolMap.containsKey("input_schema")) {
                                        // Claude format: { "name": "...", "description": "...", "input_schema": {...} }
                                        val claudeToolObj = JSONObject()
                                        claudeToolObj.put("name", toolMap["name"])
                                        if (toolMap.containsKey("description")) {
                                            claudeToolObj.put("description", toolMap["description"])
                                        }
                                        
                                        val inputSchema = toolMap["input_schema"]
                                        when (inputSchema) {
                                            is Map<*, *> -> {
                                                @Suppress("UNCHECKED_CAST")
                                                val schemaMap = inputSchema as Map<String, Any?>
                                                claudeToolObj.put("input_schema", convertMapToJsonObject(schemaMap))
                                            }
                                            is String -> {
                                                try {
                                                    claudeToolObj.put("input_schema", JSONObject(inputSchema as String))
                                                } catch (e: Exception) {
                                                    Timber.e(e, "Failed to parse input_schema JSON: $inputSchema")
                                                    claudeToolObj.put("input_schema", JSONObject())
                                                }
                                            }
                                            else -> {
                                                claudeToolObj.put("input_schema", JSONObject())
                                            }
                                        }
                                        toolsArray.put(claudeToolObj)
                                    } else if (toolMap["type"] == "web_search") {
                                        // ZhiPu web search tool format
                                        val toolObj = JSONObject()
                                        toolObj.put("type", "web_search")
                                        val webSearchData = toolMap["web_search"]
                                        if (webSearchData is Map<*, *>) {
                                            @Suppress("UNCHECKED_CAST")
                                            val webSearchMap = webSearchData as Map<String, Any?>
                                            val webSearchObj = convertMapToJsonObject(webSearchMap)
                                            toolObj.put("web_search", webSearchObj)
                                        }
                                        toolsArray.put(toolObj)
                                    } else {
                                        // Handle other Map-based function tools
                                        val toolObj = JSONObject()
                                        toolObj.put("type", toolMap["type"] ?: "function")
                                        
                                        val functionData = toolMap["function"]
                                        if (functionData is Map<*, *>) {
                                            @Suppress("UNCHECKED_CAST")
                                            val functionMap = functionData as Map<String, Any?>
                                            val functionObj = JSONObject()
                                            functionObj.put("name", functionMap["name"])
                                            functionObj.put("description", functionMap["description"])
                                            
                                            val parameters = functionMap["parameters"]
                                            if (parameters != null) {
                                                when (parameters) {
                                                    is Map<*, *> -> {
                                                        @Suppress("UNCHECKED_CAST")
                                                        val paramsMap = parameters as Map<String, Any?>
                                                        functionObj.put("parameters", convertMapToJsonObject(paramsMap))
                                                    }
                                                    is String -> {
                                                        try {
                                                            functionObj.put("parameters", JSONObject(parameters as String))
                                                        } catch (e: Exception) {
                                                            Timber.e(e, "Failed to parse parameters JSON: $parameters")
                                                            functionObj.put("parameters", JSONObject())
                                                        }
                                                    }
                                                    else -> {
                                                        functionObj.put("parameters", JSONObject())
                                                    }
                                                }
                                            }
                                            toolObj.put("function", functionObj)
                                        }
                                        toolsArray.put(toolObj)
                                    }
                                }
                                else -> {
                                    // Fallback: try to convert to JSON directly
                                    Timber.w("Unknown tool type: ${toolItem?.javaClass?.simpleName}, attempting direct conversion")
                                    val toolObj = JSONObject()
                                    toolObj.put("raw", toolItem.toString())
                                    toolsArray.put(toolObj)
                                }
                            }
                        }
                        json.put(key, toolsArray)
                    } else {
                        // Handle other arrays as JSONArray
                        val array = JSONArray()
                        for (item in value) {
                            array.put(item)
                        }
                        json.put(key, array)
                    }
                }
                is Map<*, *> -> {
                    // Handle nested maps using recursive helper function
                    @Suppress("UNCHECKED_CAST")
                    val map = value as Map<String, Any?>
                    val nestedObj = convertMapToJsonObject(map)
                    json.put(key, nestedObj)
                }
                null -> {
                    // Skip null values
                }
                is AimlApiRequest.ZhipuThinking -> {
                    // Handle ZhiPu thinking object
                    val thinkingObj = JSONObject()
                    thinkingObj.put("type", value.type)
                    json.put(key, thinkingObj)
                }
                is AimlApiRequest.ClaudeThinking -> {
                    // Handle Claude thinking object
                    val thinkingObj = JSONObject()
                    thinkingObj.put("type", value.type as String)
                    thinkingObj.put("budget_tokens", value.budgetTokens as Int)
                    json.put(key, thinkingObj)
                }
                else -> {
                    // For any other type, convert to string
                    json.put(key, value.toString())
                }
            }
        }
        
        return json.toString()
    }
}