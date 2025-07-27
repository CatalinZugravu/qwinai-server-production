package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.ModelConfig

/**
 * Complete ModelManager with all model IDs and helper methods
 */
object ModelManager {

    // Model IDs - OpenAI
    const val DEFAULT_MODEL_ID = "o1"
    const val O1_ID = "o1"
    const val O3_MINI_ID = "o3-mini"
    const val GPT_4O_ID = "gpt-4o"
    const val CHATGPT_4O_LATEST_ID = "chatgpt-4o-latest"
    const val GPT_4o_MINI_ID = "gpt-4o-mini"
    const val GPT_4_1_MINI_ID = "openai/gpt-4.1-mini-2025-04-14"
    const val GPT_4_TURBO_ID = "gpt-4-turbo"
    const val GPT_4_TURBO_2024_04_09_ID = "gpt-4-turbo-2024-04-09"
    const val GPT_4O_2024_05_13_ID = "gpt-4o-2024-05-13"
    const val GPT_4O_2024_08_06_ID = "gpt-4o-2024-08-06"

    // Anthropic Models
    const val CLAUDE_3_7_SONNET_ID = "claude-3-7-sonnet-20250219"

    // Meta Models
    const val LLAMA_3_2_3B_INSTRUCT_TURBO_ID = "meta-llama/Llama-3.2-3B-Instruct-Turbo"
    const val LLAMA_4_MAVERICK_ID = "meta-llama/llama-4-maverick"

    // Google Models
    const val GEMMA_3B_INSTRUCT_ID = "google/gemma-3-12b-it"
    const val GEMMA_27B_INSTRUCT_ID = "google/gemma-3-27b-it"
    const val GEMMA_4B_INSTRUCT_ID = "google/gemma-3-4b-it"

    // Cohere Models
    const val COHERE_COMMAND_R_PLUS_ID = "cohere/command-r-plus"

    // DeepSeek Models
    const val DEEPSEEK_R1_ID = "deepseek/deepseek-r1"

    // Qwen Models
    const val QWEN_3_235B_ID = "Qwen/Qwen3-235B-A22B-fp8-tput"
    const val QWEN_2_5_72B_ID = "Qwen/Qwen2.5-72B-Instruct-Turbo"

    // xAI Models
    const val GROK_3_BETA_ID = "x-ai/grok-3-beta"

    // Mistral Models
    const val MIXTRAL_8X7B_ID = "mistralai/Mixtral-8x7B-Instruct-v0.1"
    const val MISTRAL_OCR_ID = "mistral/mistral-ocr-latest"

    // Image generation models (kept for compatibility but not used in ModelApiHandler)
    const val DALLE_3_ID = "dall-e-3"
    const val STABLE_DIFFUSION_V35_LARGE_ID = "stable-diffusion-v35-large"
    const val FLUX_SCHNELL_ID = "flux/schnell"
    const val FLUX_REALISM_ID = "flux-realism"
    const val RECRAFT_V3_ID = "recraft-v3"
    const val FLUX_DEV_IMAGE_TO_IMAGE_ID = "flux/dev/image-to-image"
    const val FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID = "flux/kontext-max/image-to-image"
    const val FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID = "flux/kontext-pro/image-to-image"
    const val SEEDREAM_3_ID = "bytedance/seedream-3.0"
    /**
     * Generate AIModel list from ModelConfigs
     */
    val models: List<AIModel> by lazy {
        ModelConfigManager.getAllConfigs()
            .filter { !isImageGenerationModel(it.id) } // Exclude image models as requested
            .map { config ->
                AIModel(
                    id = config.id,
                    displayName = config.displayName,
                    maxTokens = config.maxOutputTokens,
                    temperature = config.defaultTemperature,
                    apiName = config.provider,
                    isFree = config.isFree,
                    maxInputTokens = config.maxInputTokens,
                    isImageGenerator = false,
                    isImageToImage = false,
                    isOcrModel = config.isOcrModel
                )
            }
    }

    var selectedModel: AIModel = models.firstOrNull() ?: throw IllegalStateException("No models configured")

    /**
     * Helper method to check if a model is an image generation model
     */
    private fun isImageGenerationModel(modelId: String): Boolean {
        return modelId in listOf(
            DALLE_3_ID,
            STABLE_DIFFUSION_V35_LARGE_ID,
            FLUX_SCHNELL_ID,
            FLUX_REALISM_ID,
            RECRAFT_V3_ID,
            FLUX_DEV_IMAGE_TO_IMAGE_ID,
            FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID,
            FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID
        )
    }

    /**
     * Get model configuration
     */
    fun getModelConfig(modelId: String): ModelConfig? = ModelConfigManager.getConfig(modelId)

    /**
     * Quick capability checks
     */
    fun supportsWebSearch(modelId: String): Boolean = ModelConfigManager.supportsWebSearch(modelId)

    fun supportsStreaming(modelId: String): Boolean = ModelConfigManager.supportsStreaming(modelId)

    fun supportsReasoning(modelId: String): Boolean = ModelConfigManager.supportsReasoning(modelId)

    /**
     * Get text generation models only
     */
    fun getTextGenerationModels(): List<AIModel> = models

    /**
     * Get free models
     */
    fun getFreeModels(): List<AIModel> = models.filter { it.isFree }

    /**
     * Get models by provider
     */
    fun getModelsByProvider(provider: String): List<AIModel> = models.filter {
        getModelConfig(it.id)?.provider == provider
    }

    /**
     * Get model by ID
     */
    fun getModelById(modelId: String): AIModel? = models.find { it.id == modelId }

    /**
     * Get models that support audio
     */
    fun getAudioModels(): List<AIModel> = models.filter {
        getModelConfig(it.id)?.supportsAudio == true
    }

    /**
     * Get models that support function calling (web search)
     */
    fun getWebSearchModels(): List<AIModel> = models.filter {
        getModelConfig(it.id)?.supportsFunctionCalling == true
    }

    /**
     * Get models that support images
     */
    fun getImageSupportModels(): List<AIModel> = models.filter {
        getModelConfig(it.id)?.supportsImages == true
    }

    /**
     * Get models that support documents
     */
    fun getDocumentSupportModels(): List<AIModel> = models.filter {
        getModelConfig(it.id)?.supportsDocuments == true
    }

    /**
     * Get OCR models
     */
    fun getOcrModels(): List<AIModel> = models.filter {
        getModelConfig(it.id)?.isOcrModel == true
    }
}