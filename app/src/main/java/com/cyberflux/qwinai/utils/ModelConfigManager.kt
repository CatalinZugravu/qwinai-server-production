package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.model.ModelConfig

object ModelConfigManager {

    private val modelConfigs = mutableMapOf<String, ModelConfig>()

    init {
        // === OPENAI MODELS ===

        // O1 Model
        register(ModelConfig(
            id = "o1",
            displayName = "O1",
            provider = "openai",
            supportsStreaming = false, // Explicitly no streaming
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 200000,
            maxInputTokens = 100000,
            defaultMaxTokens = 100000,
            supportsSeed = true,
            supportsReasoning = true,
            reasoningParameter = "reasoning_effort",
            reasoningOptions = listOf("low", "medium", "high"),
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsFileUpload = true,
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024,
            maxTokensPerFile = 2_000_000L,
            supportedFileTypes = listOf("pdf", "txt", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("pdf", "txt"),
            supportsMultipleFileSelection = true,
            supportsParallelToolCalls = true,
            supportsToolChoice = true,
            supportsStopSequences = true,
            requiresMaxCompletionTokens = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // O3-mini Model
        register(ModelConfig(
            id = "o3-mini",
            displayName = "O3 Mini",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 200000,
            maxInputTokens = 90000,
            defaultMaxTokens = 65536,
            supportsSeed = true,
            supportsReasoning = true,
            reasoningParameter = "reasoning_effort",
            reasoningOptions = listOf("low", "medium", "high"),
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsParallelToolCalls = true,
            supportsToolChoice = true,
            supportsStopSequences = true,
            requiresMaxCompletionTokens = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // GPT-4o
        register(ModelConfig(
            id = "gpt-4o",
            displayName = "GPT-4o",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 128000,
            maxInputTokens = 128000,
            defaultMaxTokens = 16384,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsPrediction = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsAudio = true,
            supportedAudioFormats = listOf("mp3", "opus", "aac", "flac", "wav", "pcm"),
            supportedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // ChatGPT-4o Latest
        register(ModelConfig(
            id = "chatgpt-4o-latest",
            displayName = "ChatGPT-4o Latest",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 128000,
            maxInputTokens = 128000,
            defaultMaxTokens = 16384,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsPrediction = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // GPT-4o Mini
        register(ModelConfig(
            id = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 128000,
            maxInputTokens = 128000,
            defaultMaxTokens = 16384,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsPrediction = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // GPT-4.1 Mini
        register(ModelConfig(
            id = "openai/gpt-4.1-mini-2025-04-14",
            displayName = "GPT-4.1 Mini",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 128000,
            maxInputTokens = 50000,
            defaultMaxTokens = 16384,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsPrediction = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsAudio = true,
            supportsTts = true,
            supportsFileUpload = true,
            supportedAudioFormats = listOf("mp3", "opus", "aac", "flac", "wav", "pcm"),
            supportedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024,
            maxTokensPerFile = 2_000_000L,
            supportedFileTypes = listOf("txt", "pdf", "csv", "xlsx", "jpg", "jpeg", "png", "gif", "webp", "mp3", "opus", "aac", "flac", "wav", "pcm"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("txt", "pdf", "csv", "xlsx"),
            supportsMultipleFileSelection = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // GPT-4 Turbo
        register(ModelConfig(
            id = "gpt-4-turbo",
            displayName = "GPT-4 Turbo",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 128000,
            maxInputTokens = 128000,
            defaultMaxTokens = 4096,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // === ANTHROPIC MODELS ===

        // Claude 3.7 Sonnet
        register(ModelConfig(
            id = "claude-3-7-sonnet-20250219",
            displayName = "Claude 3.7 Sonnet",
            provider = "anthropic",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = false, // Claude uses different system format
            maxOutputTokens = 100000, // Claude API limit is 100,000 completion tokens
            maxInputTokens = 512000,
            defaultMaxTokens = 8192,
            defaultTemperature = 1.0,
            defaultTopP = 1.0,
            supportsTopK = true,
            supportsFrequencyPenalty = false,
            supportsReasoning = true,
            reasoningParameter = "thinking",
            supportsImages = true,
            maxFiles = 5,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsToolChoice = true,
            supportsStopSequences = true,
            requiresClaudeThinking = true,
            supportsWebSearchOptions = false,
            supportsTopP = true // Claude doesn't use web search the same way
        ))

        // === META MODELS ===

        // Llama 3.2 3B Instruct Turbo
        register(ModelConfig(
            id = "meta-llama/Llama-3.2-3B-Instruct-Turbo",
            displayName = "Llama 3.2 3B Turbo",
            provider = "meta",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 16000,
            maxInputTokens = 8000,
            defaultMaxTokens = 2048,
            supportsTopK = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsRepetitionPenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsMinP = true,
            supportsPrediction = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsParallelToolCalls = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = true,
            isFree = true,
            supportsTopP = true
        ))

        // Llama 4 Maverick
        register(ModelConfig(
            id = "meta-llama/llama-4-maverick",
            displayName = "Llama-4 Maverick",
            provider = "meta",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 256000,
            maxInputTokens = 85000,
            defaultMaxTokens = 8192,
            supportsTopK = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsRepetitionPenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsSeed = true,
            supportsMinP = true,
            supportsPrediction = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // === GOOGLE MODELS ===

        // Gemma 3 12B
        register(ModelConfig(
            id = "google/gemma-3-12b-it",
            displayName = "Gemma 3 12B Instruct",
            provider = "google",
            supportsStreaming = true,
            supportsFunctionCalling = false, // Gemma doesn't support tools
            supportsSystemMessages = true,
            maxOutputTokens = 8192,
            maxInputTokens = 4096,
            defaultMaxTokens = 2048,
            supportsTopK = true,
            supportsTopP = true,
            supportsMinP = true,
            supportsTopA = true,
            supportsRepetitionPenalty = true,
            supportsSeed = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsPrediction = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = false,
            isFree = true
        ))

        // Gemma 3 27B
        register(ModelConfig(
            id = "google/gemma-3-27b-it",
            displayName = "Gemma 3 27B Instruct",
            provider = "google",
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsSystemMessages = true,
            maxOutputTokens = 8192,
            maxInputTokens = 4096,
            defaultMaxTokens = 2048,
            supportsTopK = true,
            supportsTopP = true,
            supportsMinP = true,
            supportsTopA = true,
            supportsRepetitionPenalty = true,
            supportsSeed = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsPrediction = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = false
        ))

        // Gemma 3 4B
        register(ModelConfig(
            id = "google/gemma-3-4b-it",
            displayName = "Gemma 3 4B Instruct",
            provider = "google",
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsSystemMessages = true,
            maxOutputTokens = 8192,
            maxInputTokens = 4096,
            defaultMaxTokens = 2048,
            supportsTopK = true,
            supportsTopP = true,
            supportsMinP = true,
            supportsTopA = true,
            supportsRepetitionPenalty = true,
            supportsSeed = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsPrediction = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = false,
            isFree = true
        ))

        // === COHERE MODELS ===

        // Command R+
        register(ModelConfig(
            id = "cohere/command-r-plus",
            displayName = "Cohere Command R+",
            provider = "cohere",
            supportsStreaming = true,
            supportsFunctionCalling = false, // Cohere doesn't support tools
            supportsSystemMessages = true,
            maxOutputTokens = 4096,
            maxInputTokens = 128000,
            defaultMaxTokens = 2048,
            supportsTopK = true,
            supportsTopP = true,
            supportsMinP = true,
            supportsTopA = true,
            supportsRepetitionPenalty = true,
            supportsSeed = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsPrediction = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsDocuments = true,
            supportsFileUpload = true,
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024,
            maxTokensPerFile = 2_000_000L,
            supportedFileTypes = listOf("pdf"),
            supportedImageFormats = emptyList(),
            supportedDocumentFormats = listOf("pdf"),
            supportsMultipleFileSelection = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = false,
            isFree = true
        ))

        // === DEEPSEEK MODELS ===

        // DeepSeek R1
        register(ModelConfig(
            id = "deepseek/deepseek-r1",
            displayName = "DeepSeek R1",
            provider = "deepseek",
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsSystemMessages = true,
            maxOutputTokens = 128000,
            maxInputTokens = 64000,
            defaultMaxTokens = 8192,
            supportsTopP = true,
            supportsSeed = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsMinP = true,
            supportsTopK = true,
            supportsRepetitionPenalty = true,
            supportsPrediction = true,
            supportsStopSequences = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsFileUpload = true,
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024,
            maxTokensPerFile = 2_000_000L,
            supportedFileTypes = listOf("txt", "pdf", "csv", "xlsx", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("txt", "pdf", "csv", "xlsx"),
            supportsMultipleFileSelection = true,
            supportsWebSearchOptions = false
        ))

        // === QWEN MODELS ===

        // Qwen 3 235B
        register(ModelConfig(
            id = "Qwen/Qwen3-235B-A22B-fp8-tput",
            displayName = "Qwen 3 235B",
            provider = "qwen",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 100000,
            maxInputTokens = 50000,
            defaultMaxTokens = 8192,
            defaultTemperature = 0.8,
            supportsTopP = true,
            supportsParallelToolCalls = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsToolChoice = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsMinP = true,
            supportsTopK = true,
            supportsRepetitionPenalty = true,
            supportsPrediction = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = false // Qwen doesn't use web search options
        ))

        // Qwen 2.5 72B
        register(ModelConfig(
            id = "Qwen/Qwen2.5-72B-Instruct-Turbo",
            displayName = "Qwen 2.5 72B",
            provider = "qwen",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 32000,
            maxInputTokens = 16000,
            defaultMaxTokens = 4096,
            supportsTopP = true,
            supportsParallelToolCalls = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsToolChoice = true,
            supportsWebSearchOptions = true
        ))

        // === XAI MODELS ===

        // Grok 3 Beta
        register(ModelConfig(
            id = "x-ai/grok-3-beta",
            displayName = "Grok 3 Beta",
            provider = "xai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 131000,
            maxInputTokens = 43000,
            defaultMaxTokens = 8192,
            supportsTopP = true,
            supportsParallelToolCalls = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsToolChoice = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = true,
            supportsTopLogprobs = true,
            supportsMinP = true,
            supportsTopK = true,
            supportsRepetitionPenalty = true,
            supportsTopA = true,
            supportsPrediction = true,
            supportsStopSequences = true,
            supportsImages = true,
            supportsDocuments = true,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsWebSearchOptions = true
        ))

        // === MISTRAL MODELS ===

        // Mixtral 8x22B
        register(ModelConfig(
            id = "mistralai/Mixtral-8x7B-Instruct-v0.1",
            displayName = "Mixtral 8x22B",
            provider = "mistral",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 64000,
            maxInputTokens = 32000,
            defaultMaxTokens = 4096,
            supportsTopP = true,
            supportsTopK = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsToolChoice = true,
            supportsWebSearchOptions = true
        ))

        // Mistral OCR
        register(ModelConfig(
            id = "mistral/mistral-ocr-latest",
            displayName = "Mistral OCR",
            provider = "mistral",
            supportsStreaming = false, // OCR is not a streaming API
            supportsFunctionCalling = false, // OCR doesn't need tools
            supportsSystemMessages = false, // OCR doesn't support system messages
            maxOutputTokens = 100000, // API supports max 100,000 completion tokens
            maxInputTokens = 1000000, // Large input for PDFs
            defaultMaxTokens = 100000, // Use max by default for OCR
            supportsImages = true,
            supportsDocuments = true,
            maxFiles = 1,
            maxFileSizeBytes = 50L * 1024 * 1024, // 50MB max file size
            isOcrModel = true,
            supportsWebSearchOptions = false,
            supportsTopP = false, // OCR doesn't use these parameters
            requiresMaxCompletionTokens = true // OCR requires max_tokens parameter
        ))
    }

    fun register(config: ModelConfig) {
        modelConfigs[config.id] = config
    }

    fun getConfig(modelId: String): ModelConfig? = modelConfigs[modelId]

    fun getAllConfigs(): List<ModelConfig> = modelConfigs.values.toList()

    fun supportsWebSearch(modelId: String): Boolean {
        // Web search is implemented via function calling
        return getConfig(modelId)?.supportsFunctionCalling ?: false
    }

    fun supportsStreaming(modelId: String): Boolean {
        return getConfig(modelId)?.supportsStreaming ?: false
    }

    fun supportsReasoning(modelId: String): Boolean {
        return getConfig(modelId)?.supportsReasoning ?: false
    }
}