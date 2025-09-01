package com.cyberflux.qwinai.network

import com.squareup.moshi.Json

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
    @Json(name = "max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @Json(name = "top_p")
    val topP: Double? = null,
    @Json(name = "top_k")
    val topK: Int? = null,

    // Alternative generation names
    @Json(name = "max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    @Json(name = "max_output_tokens")
    val maxOutputTokens: Int? = null,

    // Streaming
    var stream: Boolean? = null,
    @Json(name = "stream_options")
    val streamOptions: StreamOptions? = null,

    // Stop sequences
    val stop: Any? = null,  // Can be String or List<String>
    @Json(name = "stop_sequences")
    val stopSequences: List<String>? = null,

    // Sampling parameters
    @Json(name = "frequency_penalty")
    val frequencyPenalty: Double? = null,
    @Json(name = "presence_penalty")
    val presencePenalty: Double? = null,
    @Json(name = "repetition_penalty")
    val repetitionPenalty: Double? = null,
    @Json(name = "length_penalty")
    val lengthPenalty: Double? = null,

    // Advanced sampling
    @Json(name = "logit_bias")
    val logitBias: Map<String, Double>? = null,
    val logprobs: Boolean? = null,
    @Json(name = "top_logprobs")
    val topLogprobs: Int? = null,

    // Seeding and determinism
    val seed: Long? = null,
    @Json(name = "random_seed")
    val randomSeed: Long? = null,

    // Function/Tool calling
    val tools: List<Tool>? = null,
    @Json(name = "tool_choice")
    val toolChoice: Any? = null,  // Can be String or Object
    val functions: List<Function>? = null,
    @Json(name = "function_call")
    val functionCall: Any? = null,

    // Web search
    @Json(name = "web_search")
    val webSearch: Boolean? = null,
    @Json(name = "web_search_options")
    val webSearchOptions: WebSearchOptions? = null,
    @Json(name = "search_enabled")
    val searchEnabled: Boolean? = null,
    @Json(name = "search_mode")
    val searchMode: String? = null,

    // Reasoning/thinking for non-Claude models
    val reasoning: Reasoning? = null,
    @Json(name = "reasoning_effort")
    val reasoningEffort: String? = null,
    @Json(name = "chain_of_thought")
    val chainOfThought: Boolean? = null,
    @Json(name = "include_reasoning_in_response")
    val includeReasoningInResponse: Boolean? = null,
    @Json(name = "stream_reasoning")
    val streamReasoning: Boolean? = null,
    
    // ZhiPu GLM thinking parameter
    @Json(name = "thinking")
    val zhipuThinking: ZhipuThinking? = null,

    // Audio
    val audio: AudioOptions? = null,
    @Json(name = "voice_settings")
    val voiceSettings: VoiceSettings? = null,
    @Json(name = "tts_settings")
    val ttsSettings: TTSSettings? = null,

    // Image generation
    @Json(name = "image_generation")
    val imageGeneration: ImageGenerationOptions? = null,
    @Json(name = "image_settings")
    val imageSettings: ImageSettings? = null,

    // Multimodal
    val modalities: List<String>? = null,
    @Json(name = "response_format")
    val responseFormat: ResponseFormat? = null,

    // Safety and content filtering
    @Json(name = "safety_settings")
    val safetySettings: List<SafetySetting>? = null,
    @Json(name = "content_filter")
    val contentFilter: Boolean? = null,

    // Context and memory
    @Json(name = "context_length")
    val contextLength: Int? = null,
    @Json(name = "memory_alpha")
    val memoryAlpha: Double? = null,

    // Performance options
    @Json(name = "use_cache")
    val useCache: Boolean? = null,
    @Json(name = "cache_control")
    val cacheControl: CacheControl? = null,
    val timeout: Int? = null,

    // Provider specific
    @Json(name = "anthropic_version")
    val anthropicVersion: String? = null,
    @Json(name = "openai_organization")
    val openaiOrganization: String? = null,
    @Json(name = "user_id")
    val userId: String? = null,

    // Transform options
    @Json(name = "transform_options")
    val transformOptions: Map<String, Any>? = null,

    // Metadata
    val metadata: Map<String, Any>? = null,
    val tags: List<String>? = null,

    // Legacy/alternative parameters
    val prompt: String? = null,  // For completion-style APIs
    val input: String? = null,   // Alternative input format
    @Json(name = "system_message")
    val systemMessage: String? = null,

    // Experimental features
    @Json(name = "experimental_features")
    val experimentalFeatures: Map<String, Any>? = null,

    // Rate limiting
    @Json(name = "priority")
    val priority: String? = null,
    @Json(name = "rate_limit_group")
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
        @Json(name = "text_content")
        val textContent: String? = null,
        val text: String? = null,

        // Function calling
        @Json(name = "tool_calls")
        val toolCalls: List<ToolCall>? = null,
        @Json(name = "function_call")
        val functionCall: FunctionCall? = null,
        @Json(name = "tool_call_id")
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
        @Json(name = "image_url")
        val imageUrl: ImageUrl? = null,
        val image: String? = null,  // Base64 or URL
        val audio: AudioContent? = null,
        val data: String? = null,
        val url: String? = null,

        // File support for GPT-4o
        @Json(name = "file")
        val file: FileContent? = null,

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
        @Json(name = "sample_rate")
        val sampleRate: Int? = null
    )

    data class FileContent(
        @Json(name = "file_data")
        val fileData: String,  // Base64 encoded file content
        val filename: String   // File name for reference
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
        val parameters: Map<String, Any>,

        // Function metadata
        val version: String? = null,
        val examples: List<String>? = null
    )

    // ✅ NEW: Claude-specific thinking configuration
    data class ClaudeThinking(
        val type: String = "enabled",
        @Json(name = "budget_tokens")
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
        val parameters: Map<String, Any>,

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
        @Json(name = "include_usage")
        val includeUsage: Boolean? = null,
        @Json(name = "include_reasoning")
        val includeReasoning: Boolean? = null,
        @Json(name = "include_metadata")
        val includeMetadata: Boolean? = null
    )

    data class WebSearchOptions(
        @Json(name = "search_context_size")
        val searchContextSize: String? = null,
        @Json(name = "max_results")
        val maxResults: Int? = null,
        @Json(name = "user_location")
        val userLocation: UserLocation? = null,

        // Time parameters
        val freshness: String? = null,
        @Json(name = "force_fresh")
        val forceFresh: Boolean? = null,
        @Json(name = "max_freshness")
        val maxFreshness: String? = null,
        @Json(name = "time_range")
        val timeRange: String? = null,

        // Advanced options
        val domains: List<String>? = null,
        @Json(name = "exclude_domains")
        val excludeDomains: List<String>? = null,
        val language: String? = null,
        val region: String? = null,
        @Json(name = "safe_search")
        val safeSearch: String? = null,

        // Perplexity-specific parameters
        @Json(name = "return_images")
        val returnImages: Boolean? = null,
        @Json(name = "return_related_questions")
        val returnRelatedQuestions: Boolean? = null,
        @Json(name = "search_recency_filter")
        val searchRecencyFilter: String? = null,
        @Json(name = "search_after_date_filter")
        val searchAfterDateFilter: String? = null,
        @Json(name = "search_before_date_filter")
        val searchBeforeDateFilter: String? = null,
        @Json(name = "last_updated_after_filter")
        val lastUpdatedAfterFilter: String? = null,
        @Json(name = "last_updated_before_filter")
        val lastUpdatedBeforeFilter: String? = null,
        @Json(name = "search_domain_filter")
        val searchDomainFilter: List<String>? = null
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
        @Json(name = "max_tokens")
        val maxTokens: Int? = null,
        val enabled: Boolean? = null,
        val visible: Boolean? = null,

        // Advanced reasoning
        val depth: String? = null,
        val strategy: String? = null,
        @Json(name = "chain_length")
        val chainLength: Int? = null
    )

    data class AudioOptions(
        val format: String,
        val voice: String,

        // Audio quality
        val quality: String? = null,
        val speed: Double? = null,
        @Json(name = "sample_rate")
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
        @Json(name = "sample_rate")
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
        @Json(name = "json_schema")
        val jsonSchema: Map<String, Any>? = null,
        val schema: Map<String, Any>? = null
    )

    data class SafetySetting(
        val category: String,
        val threshold: String,
        val enabled: Boolean? = null
    )

    data class CacheControl(
        val type: String,
        @Json(name = "max_age")
        val maxAge: Int? = null,
        val ephemeral: Boolean? = null
    )

    /**
     * ZhiPu GLM-specific thinking configuration
     */
    data class ZhipuThinking(
        val type: String = "enabled"
    ) {
        companion object {
            fun enabled(): ZhipuThinking {
                return ZhipuThinking(type = "enabled")
            }

            fun disabled(): ZhipuThinking {
                return ZhipuThinking(type = "disabled")
            }
        }
    }
}