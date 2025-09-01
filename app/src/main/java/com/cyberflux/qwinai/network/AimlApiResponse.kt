package com.cyberflux.qwinai.network

import com.squareup.moshi.Json

/**
 * Complete API response class handling ALL possible response formats
 * from OpenAI, Claude, Gemini, and other AI providers
 */
data class AimlApiResponse(
    // Basic response fields
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val error: Error? = null,

    // Alternative content fields for different APIs
    val completion: String? = null,
    val response: String? = null,
    val text: String? = null,
    val output: String? = null,
    val generation: String? = null,
    val content: String? = null,
    val answer: String? = null,

    // Streaming specific
    @Json(name = "finish_reason")
    val finishReason: String? = null,
    val done: Boolean? = null,

    // Audio responses
    val audio: AudioData? = null,
    @Json(name = "audio_data")
    val audioData: AudioData? = null,
    @Json(name = "audio_url")
    val audioUrl: String? = null,

    // Image generation responses
    val data: List<ImageData>? = null,
    val images: List<ImageData>? = null,
    @Json(name = "image_data")
    val imageData: List<ImageData>? = null,

    // Web search results
    @Json(name = "web_search_results")
    val webSearchResults: List<WebSearchResult>? = null,
    @Json(name = "search_results")
    val searchResults: List<WebSearchResult>? = null,
    val results: List<WebSearchResult>? = null,

    // Reasoning/thinking
    val reasoning: String? = null,
    val thinking: String? = null,
    @Json(name = "thought_process")
    val thoughtProcess: String? = null,
    @Json(name = "reasoning_content")
    val reasoningContent: String? = null,
    @Json(name = "chain_of_thought")
    val chainOfThought: String? = null,

    // Tool usage
    @Json(name = "tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @Json(name = "function_calls")
    val functionCalls: List<FunctionCall>? = null,

    // System information
    val system: String? = null,
    val status: String? = null,
    val type: String? = null,

    // Rate limiting
    @Json(name = "rate_limit")
    val rateLimit: RateLimit? = null,

    // Metadata
    val metadata: Map<String, Any>? = null,
    val headers: Map<String, String>? = null,

    // Provider specific
    @Json(name = "anthropic_type")
    val anthropicType: String? = null,
    @Json(name = "openai_type")
    val openaiType: String? = null,

    // Safety and moderation
    val safety: SafetyData? = null,
    val moderation: ModerationResult? = null,

    // Conversation metadata
    @Json(name = "conversation_id")
    val conversationId: String? = null,
    @Json(name = "message_id")
    val messageId: String? = null,
    @Json(name = "parent_id")
    val parentId: String? = null,

    // Processing information
    @Json(name = "processing_time")
    val processingTime: Double? = null,
    val latency: Double? = null,
    val timestamp: Long? = null
) {

    data class Choice(
        val index: Int? = null,
        val message: Message? = null,
        val delta: Message? = null,
        @Json(name ="finish_reason")
        val finishReason: String? = null,
        val text: String? = null,
        val content: String? = null,

        // Reasoning
        val reasoning: String? = null,
        val thinking: String? = null,

        // Tool usage
        @Json(name ="tool_calls")
        val toolCalls: List<ToolCall>? = null,

        // Logprobs
        val logprobs: LogProbs? = null,

        // Safety
        @Json(name ="content_filter_results")
        val contentFilterResults: ContentFilterResults? = null
    )

    data class Message(
        val role: String? = null,
        var content: String? = null,
        val name: String? = null,

        // Audio
        val audio: AudioData? = null,
        @Json(name ="audio_data")
        val audioData: AudioData? = null,

        // Function/tool calling
        @Json(name ="tool_calls")
        val toolCalls: List<ToolCall>? = null,
        @Json(name ="function_call")
        val functionCall: FunctionCall? = null,

        // Reasoning
        val reasoning: String? = null,
        val thinking: String? = null,
        @Json(name ="thought_process")
        val thoughtProcess: String? = null,

        // Web search
        @Json(name ="web_search")
        val webSearch: String? = null,
        @Json(name ="search_results")
        val searchResults: List<WebSearchResult>? = null,

        // Multimodal content
        val parts: List<ContentPart>? = null,

        // Alternative content formats
        val text: String? = null,
        val completion: String? = null,
        val response: String? = null,

        // Metadata
        val metadata: Map<String, Any>? = null,
        val timestamp: Long? = null,

        // Safety
        @Json(name ="content_filter_result")
        val contentFilterResult: ContentFilterResult? = null
    )

    data class AudioData(
        val format: String,
        val content: String,  // Base64 encoded
        val data: String? = null,  // Alternative to content
        val url: String? = null,
        @Json(name ="audio_url")
        val audioUrl: String? = null,

        // Audio metadata
        val duration: Double? = null,
        val size: Long? = null,
        @Json(name ="sample_rate")
        val sampleRate: Int? = null,
        val channels: Int? = null,
        @Json(name ="bit_rate")
        val bitRate: Int? = null,
        val encoding: String? = null,
        val mime_type: String? = null,

        // Voice metadata
        val voice: String? = null,
        val speed: Double? = null,
        val pitch: Double? = null,
        val volume: Double? = null
    )

    data class ImageData(
        val url: String? = null,
        @Json(name ="b64_json")
        val b64Json: String? = null,
        val data: String? = null,  // Base64

        // Image metadata
        val width: Int? = null,
        val height: Int? = null,
        val format: String? = null,
        @Json(name ="mime_type")
        val mimeType: String? = null,
        val size: Long? = null,

        // Generation metadata
        val prompt: String? = null,
        @Json(name ="revised_prompt")
        val revisedPrompt: String? = null,
        val seed: Long? = null,
        val steps: Int? = null,
        val guidance: Double? = null
    )

    data class ToolCall(
        val id: String? = null,
        val type: String? = null,
        val function: FunctionCall? = null,

        // Web search
        @Json(name ="web_search")
        val webSearch: WebSearchCall? = null,

        // Results
        val results: List<Any>? = null,
        val result: String? = null,
        val output: String? = null,

        // Metadata
        val status: String? = null,
        val error: String? = null,
        @Json(name ="execution_time")
        val executionTime: Double? = null
    )

    data class FunctionCall(
        val name: String? = null,
        val arguments: String? = null,
        val parameters: Map<String, Any>? = null,

        // Results
        val result: String? = null,
        val output: String? = null,
        val returns: Any? = null,

        // Metadata
        val status: String? = null,
        val error: String? = null,
        @Json(name ="execution_time")
        val executionTime: Double? = null
    )

    data class WebSearchCall(
        val query: String? = null,
        val results: List<WebSearchResult>? = null,
        @Json(name ="search_results")
        val searchResults: List<WebSearchResult>? = null,
        val status: String? = null,
        val error: String? = null,
        @Json(name ="search_time")
        val searchTime: Double? = null,
        @Json(name ="result_count")
        val resultCount: Int? = null
    )

    data class WebSearchResult(
        val title: String? = null,
        val url: String? = null,
        val snippet: String? = null,
        val description: String? = null,
        val content: String? = null,
        val summary: String? = null,
        val text: String? = null,

        // Metadata
        val domain: String? = null,
        val favicon: String? = null,
        val timestamp: String? = null,
        val score: Double? = null,
        val rank: Int? = null,
        val relevance: Double? = null,

        // Alternative names
        val link: String? = null,
        val href: String? = null,
        val body: String? = null,

        // Rich data
        val image: String? = null,
        val images: List<String>? = null,
        val videos: List<String>? = null,

        // Source metadata
        val source: String? = null,
        val author: String? = null,
        @Json(name ="publish_date")
        val publishDate: String? = null,
        val language: String? = null,
        val country: String? = null
    )

    data class ContentPart(
        val type: String? = null,
        val text: String? = null,
        @Json(name ="image_url")
        val imageUrl: ImageUrl? = null,
        val audio: AudioData? = null,
        val image: String? = null,  // Base64 or URL
        val data: String? = null,
        val url: String? = null
    )

    data class ImageUrl(
        val url: String,
        val detail: String? = null,
        val description: String? = null
    )

    data class Usage(
        // Standard tokens
        @Json(name ="prompt_tokens")
        val promptTokens: Int? = null,
        @Json(name ="completion_tokens")
        val completionTokens: Int? = null,
        @Json(name ="total_tokens")
        val totalTokens: Int? = null,

        // Alternative names
        @Json(name ="input_tokens")
        val inputTokens: Int? = null,
        @Json(name ="output_tokens")
        val outputTokens: Int? = null,
        @Json(name ="context_tokens")
        val contextTokens: Int? = null,
        @Json(name ="generated_tokens")
        val generatedTokens: Int? = null,

        // Reasoning tokens
        @Json(name ="reasoning_tokens")
        val reasoningTokens: Int? = null,
        @Json(name ="thinking_tokens")
        val thinkingTokens: Int? = null,

        // Cost information
        @Json(name ="prompt_cost")
        val promptCost: Double? = null,
        @Json(name ="completion_cost")
        val completionCost: Double? = null,
        @Json(name ="total_cost")
        val totalCost: Double? = null,

        // Time information
        @Json(name ="processing_time")
        val processingTime: Double? = null,
        @Json(name ="inference_time")
        val inferenceTime: Double? = null,

        // Model specific
        @Json(name ="audio_tokens")
        val audioTokens: Int? = null,
        @Json(name ="image_tokens")
        val imageTokens: Int? = null,
        @Json(name ="cache_tokens")
        val cacheTokens: Int? = null
    )

    data class LogProbs(
        val tokens: List<String>? = null,
        @Json(name ="token_logprobs")
        val tokenLogprobs: List<Double>? = null,
        @Json(name ="top_logprobs")
        val topLogprobs: List<Map<String, Double>>? = null,
        @Json(name ="text_offset")
        val textOffset: List<Int>? = null
    )

    data class RateLimit(
        @Json(name ="requests_per_minute")
        val requestsPerMinute: Int? = null,
        @Json(name ="tokens_per_minute")
        val tokensPerMinute: Int? = null,
        @Json(name ="requests_remaining")
        val requestsRemaining: Int? = null,
        @Json(name ="tokens_remaining")
        val tokensRemaining: Int? = null,
        @Json(name ="reset_time")
        val resetTime: Long? = null,
        @Json(name ="retry_after")
        val retryAfter: Int? = null
    )

    data class SafetyData(
        val blocked: Boolean? = null,
        val category: String? = null,
        val probability: String? = null,
        val severity: String? = null,
        val details: String? = null
    )

    data class ModerationResult(
        val flagged: Boolean? = null,
        val categories: Map<String, Boolean>? = null,
        @Json(name ="category_scores")
        val categoryScores: Map<String, Double>? = null
    )

    data class ContentFilterResults(
        val hate: ContentFilterResult? = null,
        @Json(name ="self_harm")
        val selfHarm: ContentFilterResult? = null,
        val sexual: ContentFilterResult? = null,
        val violence: ContentFilterResult? = null
    )

    data class ContentFilterResult(
        val filtered: Boolean? = null,
        val severity: String? = null
    )

    data class Error(
        val message: String,
        val type: String,
        val code: String? = null,
        val param: String? = null,

        // Additional error info
        val details: String? = null,
        val suggestion: String? = null,
        @Json(name ="error_code")
        val errorCode: Int? = null,
        val status: Int? = null,

        // Nested error
        val error: InnerError? = null,

        // Provider specific
        @Json(name ="anthropic_error")
        val anthropicError: String? = null,
        @Json(name ="openai_error")
        val openaiError: String? = null,

        // Rate limiting errors
        @Json(name ="retry_after")
        val retryAfter: Int? = null
    )

    data class InnerError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null,
        val details: Map<String, Any>? = null
    )

    // Helper methods - renamed to avoid naming conflicts
    fun extractContent(): String? {
        return choices?.firstOrNull()?.message?.content
            ?: choices?.firstOrNull()?.delta?.content
            ?: choices?.firstOrNull()?.text
            ?: completion
            ?: response
            ?: text
            ?: output
            ?: generation
            ?: content
            ?: answer
    }

    fun extractAudioData(): AudioData? {
        return audioData
            ?: audio
            ?: choices?.firstOrNull()?.message?.audioData
            ?: choices?.firstOrNull()?.message?.audio
            ?: choices?.firstOrNull()?.delta?.audioData
            ?: choices?.firstOrNull()?.delta?.audio
    }

    fun extractImageData(): List<ImageData> {
        return data ?: images ?: imageData ?: emptyList()
    }

    fun extractWebSearchResults(): List<WebSearchResult> {
        return webSearchResults
            ?: searchResults
            ?: results
            ?: choices?.firstOrNull()?.toolCalls?.mapNotNull { it.webSearch?.results }?.flatten()
            ?: choices?.firstOrNull()?.message?.searchResults
            ?: emptyList()
    }

    fun extractReasoningContent(): String? {
        return reasoning
            ?: thinking
            ?: thoughtProcess
            ?: reasoningContent
            ?: chainOfThought
            ?: choices?.firstOrNull()?.reasoning
            ?: choices?.firstOrNull()?.thinking
            ?: choices?.firstOrNull()?.message?.reasoning
            ?: choices?.firstOrNull()?.message?.thinking
            ?: choices?.firstOrNull()?.message?.thoughtProcess
    }

    fun isError(): Boolean = error != null
    fun hasAudio(): Boolean = extractAudioData() != null
    fun hasImages(): Boolean = extractImageData().isNotEmpty()
    fun hasWebSearchResults(): Boolean = extractWebSearchResults().isNotEmpty()
    fun hasReasoning(): Boolean = !extractReasoningContent().isNullOrBlank()
    fun isComplete(): Boolean {
        val reason = extractFinishReason()
        return reason == "stop" || reason == "end_turn" || reason == "complete" || done == true
    }

    fun extractFinishReason(): String? {
        return finishReason ?: choices?.firstOrNull()?.finishReason
    }
}