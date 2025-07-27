package com.cyberflux.qwinai.network

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Complete API request class supporting all AI model parameters
 * Compatible with OpenAI, Claude, Gemini, and other providers
 * Updated with proper Claude thinking support
 */
data class AimlApiRequest(
    // Core parameters
    val model: String,
    val messages: List<Message>,
    val thinking: ClaudeThinking? = null, // ✅ NEW: Claude thinking parameter

    // Generation parameters
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerializedName("top_p")
    val topP: Double? = null,
    @SerializedName("top_k")
    val topK: Int? = null,

    // Alternative generation names
    @SerializedName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    @SerializedName("max_output_tokens")
    val maxOutputTokens: Int? = null,

    // Streaming
    var stream: Boolean? = null,
    @SerializedName("stream_options")
    val streamOptions: StreamOptions? = null,

    // Stop sequences
    val stop: Any? = null,  // Can be String or List<String>
    @SerializedName("stop_sequences")
    val stopSequences: List<String>? = null,

    // Sampling parameters
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @SerializedName("presence_penalty")
    val presencePenalty: Double? = null,
    @SerializedName("repetition_penalty")
    val repetitionPenalty: Double? = null,
    @SerializedName("length_penalty")
    val lengthPenalty: Double? = null,

    // Advanced sampling
    @SerializedName("logit_bias")
    val logitBias: Map<String, Double>? = null,
    val logprobs: Boolean? = null,
    @SerializedName("top_logprobs")
    val topLogprobs: Int? = null,

    // Seeding and determinism
    val seed: Long? = null,
    @SerializedName("random_seed")
    val randomSeed: Long? = null,

    // Function/Tool calling
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice")
    val toolChoice: Any? = null,  // Can be String or Object
    val functions: List<Function>? = null,
    @SerializedName("function_call")
    val functionCall: Any? = null,

    // Web search
    @SerializedName("web_search")
    val webSearch: Boolean? = null,
    @SerializedName("web_search_options")
    val webSearchOptions: WebSearchOptions? = null,
    @SerializedName("search_enabled")
    val searchEnabled: Boolean? = null,

    // Reasoning/thinking for non-Claude models
    val reasoning: Reasoning? = null,
    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = null,
    @SerializedName("chain_of_thought")
    val chainOfThought: Boolean? = null,
    @SerializedName("include_reasoning_in_response")
    val includeReasoningInResponse: Boolean? = null,
    @SerializedName("stream_reasoning")
    val streamReasoning: Boolean? = null,

    // Audio
    val audio: AudioOptions? = null,
    @SerializedName("voice_settings")
    val voiceSettings: VoiceSettings? = null,
    @SerializedName("tts_settings")
    val ttsSettings: TTSSettings? = null,

    // Image generation
    @SerializedName("image_generation")
    val imageGeneration: ImageGenerationOptions? = null,
    @SerializedName("image_settings")
    val imageSettings: ImageSettings? = null,

    // Multimodal
    val modalities: List<String>? = null,
    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null,

    // Safety and content filtering
    @SerializedName("safety_settings")
    val safetySettings: List<SafetySetting>? = null,
    @SerializedName("content_filter")
    val contentFilter: Boolean? = null,

    // Context and memory
    @SerializedName("context_length")
    val contextLength: Int? = null,
    @SerializedName("memory_alpha")
    val memoryAlpha: Double? = null,

    // Performance options
    @SerializedName("use_cache")
    val useCache: Boolean? = null,
    @SerializedName("cache_control")
    val cacheControl: CacheControl? = null,
    val timeout: Int? = null,

    // Provider specific
    @SerializedName("anthropic_version")
    val anthropicVersion: String? = null,
    @SerializedName("openai_organization")
    val openaiOrganization: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,

    // Transform options
    @SerializedName("transform_options")
    val transformOptions: Map<String, Any>? = null,

    // Metadata
    val metadata: Map<String, Any>? = null,
    val tags: List<String>? = null,

    // Legacy/alternative parameters
    val prompt: String? = null,  // For completion-style APIs
    val input: String? = null,   // Alternative input format
    @SerializedName("system_message")
    val systemMessage: String? = null,

    // Experimental features
    @SerializedName("experimental_features")
    val experimentalFeatures: Map<String, Any>? = null,

    // Rate limiting
    @SerializedName("priority")
    val priority: String? = null,
    @SerializedName("rate_limit_group")
    val rateLimitGroup: String? = null,
    val topA: Double?,
    val parallelToolCalls: Boolean?,
    val minP: Double?,
    val useWebSearch: Boolean
) {

    data class Message(
        val role: String,
        val content: Any,  // Remove the nullable and default value
        val name: String? = null,

        // Alternative content fields
        @SerializedName("text_content")
        val textContent: String? = null,
        val text: String? = null,

        // Function calling
        @SerializedName("tool_calls")
        val toolCalls: List<ToolCall>? = null,
        @SerializedName("function_call")
        val functionCall: FunctionCall? = null,
        @SerializedName("tool_call_id")
        val toolCallId: String? = null,

        // Audio
        val audio: AudioContent? = null,

        // Metadata
        val metadata: Map<String, Any>? = null,
        val timestamp: Long? = null
    )

    data class ContentPart(
        val type: String,
        val text: String? = null,
        @SerializedName("image_url")
        val imageUrl: ImageUrl? = null,
        val image: String? = null,  // Base64 or URL
        val audio: AudioContent? = null,
        val data: String? = null,
        val url: String? = null,

        // Additional metadata
        val metadata: Map<String, Any>? = null
    )

    data class ImageUrl(
        val url: String,
        val detail: String? = null,
        val description: String? = null
    )

    data class AudioContent(
        val data: String,  // Base64 encoded
        val format: String,
        val duration: Double? = null,
        @SerializedName("sample_rate")
        val sampleRate: Int? = null
    )

    data class Tool(
        val type: String,
        val function: ToolFunction,

        // Tool metadata
        val description: String? = null,
        val version: String? = null,
        val required: Boolean? = null,

    )

    data class ToolFunction(
        val name: String,
        val description: String,
        val parameters: JsonObject,

        // Function metadata
        val version: String? = null,
        val examples: List<String>? = null
    )

    // ✅ NEW: Claude-specific thinking configuration
    data class ClaudeThinking(
        val type: String = "enabled",
        @SerializedName("budget_tokens")
        val budgetTokens: Int
    ) {
        companion object {
            fun create(budgetTokens: Int = 4096): ClaudeThinking {
                // Ensure minimum budget is met (Claude requires at least 1024)
                val actualBudget = maxOf(budgetTokens, 1024)
                return ClaudeThinking(
                    type = "enabled",
                    budgetTokens = actualBudget
                )
            }
        }
    }

    data class Function(
        val name: String,
        val description: String,
        val parameters: JsonObject,

        // Legacy function fields
        val required: List<String>? = null
    )

    data class ToolCall(
        val id: String,
        val type: String,
        val function: FunctionCall
    )

    data class FunctionCall(
        val name: String,
        val arguments: String,

        // Results (for assistant messages)
        val result: String? = null
    )

    data class StreamOptions(
        @SerializedName("include_usage")
        val includeUsage: Boolean? = null,
        @SerializedName("include_reasoning")
        val includeReasoning: Boolean? = null,
        @SerializedName("include_metadata")
        val includeMetadata: Boolean? = null
    )

    data class WebSearchOptions(
        @SerializedName("search_context_size")
        val searchContextSize: String? = null,
        @SerializedName("max_results")
        val maxResults: Int? = null,
        @SerializedName("user_location")
        val userLocation: UserLocation? = null,

        // Time parameters
        val freshness: String? = null,
        @SerializedName("force_fresh")
        val forceFresh: Boolean? = null,
        @SerializedName("max_freshness")
        val maxFreshness: String? = null,
        @SerializedName("time_range")
        val timeRange: String? = null,

        // Advanced options
        val domains: List<String>? = null,
        @SerializedName("exclude_domains")
        val excludeDomains: List<String>? = null,
        val language: String? = null,
        val region: String? = null,
        @SerializedName("safe_search")
        val safeSearch: String? = null
    )

    data class UserLocation(
        val approximate: ApproximateLocation,
        val timezone: String
    ) {
        data class ApproximateLocation(
            val city: String,
            val region: String,
            val country: String,
            val timezone: String
        )
    }

    data class Reasoning(
        val effort: String? = null,
        @SerializedName("max_tokens")
        val maxTokens: Int? = null,
        val enabled: Boolean? = null,
        val visible: Boolean? = null,

        // Advanced reasoning
        val depth: String? = null,
        val strategy: String? = null,
        @SerializedName("chain_length")
        val chainLength: Int? = null
    )

    data class AudioOptions(
        val format: String,
        val voice: String,

        // Audio quality
        val quality: String? = null,
        val speed: Double? = null,
        @SerializedName("sample_rate")
        val sampleRate: Int? = null,

        // Voice settings
        val pitch: Double? = null,
        val volume: Double? = null,
        val emotion: String? = null,
        val style: String? = null
    )

    data class VoiceSettings(
        val voice: String,
        val speed: Double? = null,
        val pitch: Double? = null,
        val volume: Double? = null,
        val emotion: String? = null
    )

    data class TTSSettings(
        val voice: String,
        val format: String,
        val quality: String? = null,
        @SerializedName("sample_rate")
        val sampleRate: Int? = null
    )

    data class ImageGenerationOptions(
        val prompt: String,
        val size: String? = null,
        val quality: String? = null,
        val style: String? = null,
        val n: Int? = null,

        // Advanced options
        val seed: Long? = null,
        val steps: Int? = null,
        val guidance: Double? = null,
        val scheduler: String? = null
    )

    data class ImageSettings(
        val width: Int? = null,
        val height: Int? = null,
        val format: String? = null,
        val quality: String? = null
    )

    data class ResponseFormat(
        val type: String,
        @SerializedName("json_schema")
        val jsonSchema: JsonObject? = null,
        val schema: JsonObject? = null
    )

    data class SafetySetting(
        val category: String,
        val threshold: String,
        val enabled: Boolean? = null
    )

    data class CacheControl(
        val type: String,
        @SerializedName("max_age")
        val maxAge: Int? = null,
        val ephemeral: Boolean? = null
    )
}