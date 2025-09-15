package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.model.ModelConfig

object ModelConfigManager {

    private val modelConfigs = mutableMapOf<String, ModelConfig>()

    init {
        // === OPENAI MODELS ===

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
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024, // 10GB per user
            maxTokensPerFile = 2_000_000L, // 2 million tokens per file
            supportedFileTypes = listOf("pdf", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("pdf"),
            supportsMultipleFileSelection = true,
            supportsFileUpload = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // GPT-4o Audio Preview
        register(ModelConfig(
            id = "gpt-4o-audio-preview",
            displayName = "GPT-4o Audio Preview",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = false, // Audio model doesn't support function calling
            supportsSystemMessages = true,
            maxOutputTokens = 16384, // Conservative limit for audio responses
            maxInputTokens = 128000, // Same as GPT-4o for input context
            defaultMaxTokens = 4096,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = false,
            supportsTopLogprobs = false,
            supportsSeed = true,
            supportsPrediction = false,
            supportsResponseFormat = false,
            supportsJsonMode = false,
            supportsJsonSchema = false,
            supportsImages = false, // Audio-only model
            supportsDocuments = false,
            supportsAudio = true,
            supportedAudioFormats = listOf("mp3", "opus", "aac", "flac", "wav", "pcm"),
            supportedVoices = listOf("alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer"),
            maxFiles = 0, // Audio input only
            maxFileSizeBytes = 25L * 1024 * 1024, // 25MB audio limit
            maxTotalStorageBytes = 0L,
            maxTokensPerFile = 0L,
            supportedFileTypes = emptyList(),
            supportedImageFormats = emptyList(),
            supportedDocumentFormats = emptyList(),
            supportsMultipleFileSelection = false,
            supportsFileUpload = false,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = false,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // GPT-4o mini
        register(ModelConfig(
            id = "gpt-4o-mini",
            displayName = "GPT-4o mini",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 16384,
            maxInputTokens = 128000,
            defaultMaxTokens = 4096,
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
            supportsAudio = false,
            supportedAudioFormats = emptyList(),
            supportedVoices = emptyList(),
            maxFiles = 20,
            maxFileSizeBytes = 512L * 1024 * 1024,
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024,
            maxTokensPerFile = 2_000_000L,
            supportedFileTypes = listOf("pdf", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("pdf"),
            supportsMultipleFileSelection = true,
            supportsFileUpload = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsN = true,
            supportsWebSearchOptions = true,
            supportsTopP = true,
            isFree = true
        ))

        // GPT-4.1
        register(ModelConfig(
            id = "gpt-4.1-preview",
            displayName = "GPT-4.1",
            provider = "openai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 32768,
            maxInputTokens = 200000,
            defaultMaxTokens = 8192,
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
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024,
            maxTokensPerFile = 2_000_000L,
            supportedFileTypes = listOf("pdf", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("pdf"),
            supportsMultipleFileSelection = true,
            supportsFileUpload = true,
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
            maxTotalStorageBytes = 10L * 1024 * 1024 * 1024, // 10GB per user
            maxTokensPerFile = 2_000_000L, // 2 million tokens per file
            supportedFileTypes = listOf("pdf", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("pdf"),
            supportsMultipleFileSelection = true,
            supportsFileUpload = true,
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

        // Llama 3.3
        register(ModelConfig(
            id = "meta-llama/llama-3.3-70b-instruct",
            displayName = "Llama 3.3",
            provider = "meta",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 131072,
            maxInputTokens = 128000,
            defaultMaxTokens = 4096,
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
            supportsImages = false,
            supportsDocuments = false,
            supportsAudio = false,
            supportedAudioFormats = emptyList(),
            supportedVoices = emptyList(),
            maxFiles = 0,
            maxFileSizeBytes = 0L,
            maxTotalStorageBytes = 0L,
            maxTokensPerFile = 0L,
            supportedFileTypes = emptyList(),
            supportedImageFormats = emptyList(),
            supportedDocumentFormats = emptyList(),
            supportsMultipleFileSelection = false,
            supportsFileUpload = false,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // AI Image (Special image generation handler)
        register(ModelConfig(
            id = "ai-image-generator",
            displayName = "AI Image",
            provider = "ai-chat", // Use ai-chat provider to avoid image model filtering
            supportsStreaming = false,
            supportsFunctionCalling = false,
            supportsSystemMessages = false,
            maxOutputTokens = 1024,
            maxInputTokens = 4096,
            defaultMaxTokens = 512,
            supportsTopK = false,
            supportsFrequencyPenalty = false,
            supportsPresencePenalty = false,
            supportsRepetitionPenalty = false,
            supportsLogprobs = false,
            supportsTopLogprobs = false,
            supportsSeed = false,
            supportsMinP = false,
            supportsPrediction = false,
            supportsResponseFormat = false,
            supportsJsonMode = false,
            supportsJsonSchema = false,
            supportsImages = false,
            supportsDocuments = false,
            supportsAudio = false,
            supportedAudioFormats = emptyList(),
            supportedVoices = emptyList(),
            maxFiles = 0,
            maxFileSizeBytes = 0L,
            maxTotalStorageBytes = 0L,
            maxTokensPerFile = 0L,
            supportedFileTypes = emptyList(),
            supportedImageFormats = emptyList(),
            supportedDocumentFormats = emptyList(),
            supportsMultipleFileSelection = false,
            supportsFileUpload = false,
            supportsStreamOptions = false,
            streamIncludeUsage = false,
            supportsStopSequences = false,
            supportsWebSearchOptions = false,
            supportsTopP = false
        ))

        // === GOOGLE MODELS ===


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

        // Google Gemini
        register(ModelConfig(
            id = "google/gemini-1.5-pro",
            displayName = "Google Gemini",
            provider = "google",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 8192,
            maxInputTokens = 1048576,
            defaultMaxTokens = 4096,
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
            supportsAudio = false,
            supportedAudioFormats = emptyList(),
            supportedVoices = emptyList(),
            maxFiles = 20,
            maxFileSizeBytes = 20L * 1024 * 1024,
            maxTotalStorageBytes = 1L * 1024 * 1024 * 1024,
            maxTokensPerFile = 1_000_000L,
            supportedFileTypes = listOf("pdf", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("pdf"),
            supportsMultipleFileSelection = true,
            supportsFileUpload = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = false
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
            // NOTE: Qwen has built-in thinking - no reasoning button needed
            supportsReasoning = false, // Button-controlled reasoning not supported  
            supportsWebSearchOptions = false // Qwen doesn't use web search options
        ))


        // === XAI MODELS ===

        // Grok 2
        register(ModelConfig(
            id = "x-ai/grok-2",
            displayName = "Grok 2",
            provider = "xai",
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsSystemMessages = true,
            maxOutputTokens = 32768,
            maxInputTokens = 131072,
            defaultMaxTokens = 8192,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsLogprobs = false,
            supportsTopLogprobs = false,
            supportsSeed = true,
            supportsPrediction = false,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = false,
            supportsImages = true,
            supportsDocuments = false,
            supportsAudio = false,
            supportedAudioFormats = emptyList(),
            supportedVoices = emptyList(),
            maxFiles = 10,
            maxFileSizeBytes = 100L * 1024 * 1024,
            maxTotalStorageBytes = 1L * 1024 * 1024 * 1024,
            maxTokensPerFile = 1_000_000L,
            supportedFileTypes = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = emptyList(),
            supportsMultipleFileSelection = true,
            supportsFileUpload = true,
            supportsStreamOptions = true,
            streamIncludeUsage = false,
            supportsN = false,
            supportsWebSearchOptions = true,
            supportsTopP = true
        ))

        // Grok 3
        register(ModelConfig(
            id = "x-ai/grok-3-beta",
            displayName = "Grok 3",
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


        // === PERPLEXITY MODELS ===

        // Perplexity Sonar Pro
        register(ModelConfig(
            id = "perplexity/sonar-pro",
            displayName = "Perplexity Sonar Pro",
            provider = "perplexity",
            supportsStreaming = true,
            supportsFunctionCalling = false, // Perplexity uses built-in web search instead of tools
            supportsSystemMessages = false, // Specialized web search model - no system instructions needed
            maxOutputTokens = 512,
            maxInputTokens = 28000,
            defaultMaxTokens = 512,
            supportsTopP = true,
            supportsTopK = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsSeed = true,
            supportsLogprobs = false,
            supportsTopLogprobs = false,
            supportsResponseFormat = true,
            supportsJsonMode = true,
            supportsJsonSchema = true,
            supportsImages = true,
            supportsDocuments = false,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsStopSequences = true,
            supportsWebSearchOptions = true // Perplexity has built-in web search
        ))

        // === ZHIPU MODELS ===

        // GLM-4.5 with built-in search engine and thinking support
        register(ModelConfig(
            id = "zhipu/glm-4.5",
            displayName = "GLM-4.5 (ZhiPu)",
            provider = "zhipu",
            supportsStreaming = true,
            supportsFunctionCalling = true, // Supports built-in web search via tools
            supportsSystemMessages = true,
            maxOutputTokens = 512,
            maxInputTokens = 128000,
            defaultMaxTokens = 512,
            supportsTopP = true,
            supportsFrequencyPenalty = true,
            supportsPresencePenalty = true,
            supportsSeed = false,
            supportsLogprobs = false,
            supportsTopLogprobs = false,
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
            supportedFileTypes = listOf("txt", "pdf", "csv", "xlsx", "jpg", "jpeg", "png", "gif", "webp"),
            supportedImageFormats = listOf("jpg", "jpeg", "png", "gif", "webp"),
            supportedDocumentFormats = listOf("txt", "pdf", "csv", "xlsx"),
            supportsMultipleFileSelection = true,
            supportsStreamOptions = true,
            streamIncludeUsage = true,
            supportsParallelToolCalls = true,
            supportsToolChoice = true,
            supportsStopSequences = true,
            supportsReasoning = true,
            reasoningParameter = "thinking", // ZhiPu uses "thinking" parameter
            reasoningOptions = listOf("enabled", "disabled"), // ZhiPu thinking options
            supportsWebSearchOptions = false, // ZhiPu has built-in web search via tools instead of options
            isFree = false
        ))
    }

    fun register(config: ModelConfig) {
        modelConfigs[config.id] = config
    }

    fun getConfig(modelId: String): ModelConfig? = modelConfigs[modelId]

    fun getAllConfigs(): List<ModelConfig> = modelConfigs.values.toList()

    fun supportsWebSearch(modelId: String): Boolean {
        // Web search is implemented via function calling
        return getConfig(modelId)?.supportsFunctionCalling == true
    }

    fun supportsStreaming(modelId: String): Boolean {
        return getConfig(modelId)?.supportsStreaming == true
    }

    fun supportsReasoning(modelId: String): Boolean {
        // Return true only for models that support button-controlled reasoning
        // Models with built-in thinking (DeepSeek, Qwen) have supportsReasoning = false in their config
        return getConfig(modelId)?.supportsReasoning == true
    }
}