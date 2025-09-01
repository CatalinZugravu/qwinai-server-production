package com.cyberflux.qwinai.model

/**
 * Complete model configuration data class
 */
data class ModelConfig(
    val id: String,
    val displayName: String,
    val provider: String,

    // Core capabilities
    val supportsStreaming: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsSystemMessages: Boolean = true,

    // Token limits
    val maxOutputTokens: Int = 4096,
    val maxInputTokens: Int = 4096,
    val defaultMaxTokens: Int = 2048,

    // Generation parameters
    val defaultTemperature: Double = 0.7,
    val defaultTopP: Double = 0.95,
    val supportsTopK: Boolean = false,
    val supportsFrequencyPenalty: Boolean = false,
    val supportsPresencePenalty: Boolean = false,
    val supportsRepetitionPenalty: Boolean = false,
    val supportsLogprobs: Boolean = false,
    val supportsTopLogprobs: Boolean = false,
    val supportsSeed: Boolean = false,
    val supportsMinP: Boolean = false,
    val supportsTopA: Boolean = false,

    // Advanced features
    val supportsReasoning: Boolean = false,
    val reasoningParameter: String? = null, // "reasoning_effort" or "thinking"
    val reasoningOptions: List<String>? = null, // ["low", "medium", "high"] or budget_tokens
    val supportsPrediction: Boolean = false,
    val supportsResponseFormat: Boolean = false,
    val supportsJsonMode: Boolean = false,
    val supportsJsonSchema: Boolean = false,

    // Multimodal
    val supportsImages: Boolean = false,
    val supportsDocuments: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsTts: Boolean = false,
    val supportedAudioFormats: List<String> = emptyList(),
    val supportedVoices: List<String> = emptyList(),

    // File handling
    val maxFiles: Int = 0,
    val maxFileSizeBytes: Long = 0L,
    val maxTotalStorageBytes: Long = 0L,
    val maxTokensPerFile: Long = 0L,
    val supportedFileTypes: List<String> = emptyList(),
    val supportedImageFormats: List<String> = emptyList(),
    val supportedDocumentFormats: List<String> = emptyList(),
    val supportsMultipleFileSelection: Boolean = false,
    val supportsFileUpload: Boolean = false,

    // Streaming options
    val supportsStreamOptions: Boolean = false,
    val streamIncludeUsage: Boolean = false,

    // Special parameters
    val supportsParallelToolCalls: Boolean = true,
    val supportsToolChoice: Boolean = true,
    val supportsStopSequences: Boolean = true,
    val maxStopSequences: Int = 4,
    val supportsN: Boolean = true, // n parameter for number of completions

    // Model-specific quirks
    val requiresMaxCompletionTokens: Boolean = false, // For O1/O3 models
    val requiresClaudeThinking: Boolean = false, // For Claude models
    val isOcrModel: Boolean = false,

    // Web search
    val supportsWebSearchOptions: Boolean = false,

    // Pricing
    val isFree: Boolean = false,
    val supportsTopP: Boolean
)
