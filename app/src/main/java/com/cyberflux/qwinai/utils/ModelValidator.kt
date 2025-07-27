package com.cyberflux.qwinai.utils

import timber.log.Timber

/**
 * Complete ModelValidator that provides all capability checks
 * Delegates to ModelConfigManager for actual configuration
 */
object ModelValidator {

    fun init() {
        // Configuration is handled by ModelConfigManager
        Timber.d("ModelValidator initialized with ModelConfigManager")
    }

    // === CORE CAPABILITIES ===
// În ModelValidator.kt
    fun supportsMultipleTools(modelId: String): Boolean {
        return when {
            modelId.contains("gpt-4", ignoreCase = true) -> true
            modelId.contains("claude-3", ignoreCase = true) &&
                    (modelId.contains("opus", ignoreCase = true) ||
                            modelId.contains("sonnet", ignoreCase = true)) -> true
            modelId.contains("o1", ignoreCase = true) -> true
            // Adaugă alte modele care suportă multiple tool-uri
            else -> false
        }
    }
    fun supportsWebSearch(modelId: String): Boolean =
        ModelConfigManager.supportsWebSearch(modelId)

    fun supportsFunctionCalling(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsFunctionCalling ?: false

    fun supportsSystemMessages(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsSystemMessages ?: true

    fun supportsAudio(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsAudio ?: false

    fun supportsImageUpload(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsImages ?: false

    fun supportsDocuments(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsDocuments ?: false

    // === MODEL TYPE CHECKS ===

    fun isImageGenerator(modelId: String): Boolean = false // Image models excluded as requested

    fun isOcrModel(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.isOcrModel ?: false

    fun isClaudeModel(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.requiresClaudeThinking ?: false ||
                modelId.contains("claude", ignoreCase = true)

    // === FILE HANDLING ===

    fun getMaxFilesForModel(modelId: String): Int {
        val maxFiles = ModelConfigManager.getConfig(modelId)?.maxFiles ?: 0
        // CRITICAL: Ensure limit never exceeds 3 for safety (as per original code)
        return minOf(maxFiles, 3)
    }

    fun getMaxFileSizeBytes(modelId: String = ""): Long {
        // Handle empty or null model ID
        if (modelId.isBlank()) {
            Timber.w("getMaxFileSizeBytes called with empty modelId, using default fallback")
            return 100L * 1024 * 1024 // 100MB default fallback
        }
        
        val config = ModelConfigManager.getConfig(modelId)
        if (config == null) {
            Timber.w("No configuration found for model '$modelId', using 100MB fallback")
        }
        
        val maxSize = config?.maxFileSizeBytes ?: (100L * 1024 * 1024) // 100MB fallback
        
        // Ensure we never return 0 or negative values
        val safeMaxSize = if (maxSize <= 0) {
            Timber.w("Model '$modelId' has invalid maxFileSizeBytes ($maxSize), using 100MB fallback")
            100L * 1024 * 1024
        } else {
            maxSize
        }
        
        Timber.d("Max file size for model '$modelId': ${FileUtil.formatFileSize(safeMaxSize)} (config found: ${config != null})")
        return safeMaxSize
    }

    fun requiresTextWithImages(modelId: String): Boolean =
        when (modelId) {
            ModelManager.GROK_3_BETA_ID -> true
            else -> false
        }

    fun supportsMinP(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsMinP ?: false

    fun supportsTopA(modelId: String): Boolean =
        ModelConfigManager.getConfig(modelId)?.supportsTopA ?: false


    // === LEGACY COMPATIBILITY METHODS ===

    fun hasNativeDocumentSupport(modelId: String): Boolean = supportsDocuments(modelId)

    fun supportsPhotoCapture(modelId: String): Boolean = supportsImageUpload(modelId)

    fun getMaxDocumentPages(modelId: String): Int =
        when (modelId) {
            ModelManager.COHERE_COMMAND_R_PLUS_ID -> 500
            ModelManager.MISTRAL_OCR_ID -> 1000
            else -> 0
        }

    fun supportsRealTimeAudioConversation(modelId: String): Boolean = false // Not implemented

    /**
     * Clear cache method for compatibility
     */
    fun clearCache() {
        Timber.d("ModelValidator cache cleared (no-op with new system)")
    }

    private val BASE_CREDIT_COSTS = mapOf(
        "dall-e-3" to 10,                                               // Premium OpenAI model
        "bytedance/seedream-3.0" to 8,                                  // Advanced ByteDance model
        "stable-diffusion-v35-large" to 6,                             // Standard Stability AI
        "flux/schnell" to 4,                                            // Fastest, lowest cost
        "flux-realism" to 7,                                            // Specialized realism
        "recraft-v3" to 12,                                             // Most versatile with many styles
        "flux/dev/image-to-image" to 8,                                 // Standard I2I
        "flux/kontext-max/image-to-image" to 10,                       // Advanced I2I
        "flux/kontext-pro/image-to-image" to 12                        // Premium I2I with multi-image
    )

    // Quality multipliers
    private val QUALITY_MULTIPLIERS = mapOf(
        "standard" to 1.0f,
        "hd" to 1.5f
    )

    /**
     * Calculate the total credit cost for image generation
     * @param modelId The AI model being used
     * @param quality The quality setting (standard, hd)
     * @param numImages Number of images to generate
     * @return Total credit cost
     */
    fun getImageGenerationCost(modelId: String, quality: String = "standard", numImages: Int = 1): Int {
        try {
            val baseCost = BASE_CREDIT_COSTS[modelId] ?: run {
                Timber.w("Unknown model ID: $modelId, using default cost")
                5 // Default fallback cost
            }

            val qualityMultiplier = QUALITY_MULTIPLIERS[quality] ?: run {
                Timber.w("Unknown quality: $quality, using standard multiplier")
                1.0f
            }

            val totalCost = (baseCost.toFloat() * qualityMultiplier * numImages.toFloat()).toInt()

            Timber.d("Credit calculation: model=$modelId, base=$baseCost, quality=$quality (${qualityMultiplier}x), images=$numImages, total=$totalCost")

            return totalCost.coerceAtLeast(1) // Minimum 1 credit
        } catch (e: Exception) {
            Timber.e(e, "Error calculating credit cost, using fallback")
            return (5 * numImages).coerceAtLeast(1) // Fallback cost
        }
    }

    /**
     * Get base credit cost for a model (without quality/quantity multipliers)
     */
    fun getBaseCreditCost(modelId: String): Int {
        return BASE_CREDIT_COSTS[modelId] ?: 5
    }

    /**
     * Get quality multiplier for display purposes
     */
    fun getQualityMultiplier(quality: String): Float {
        return QUALITY_MULTIPLIERS[quality] ?: 1.0f
    }

    /**
     * Get all supported models with their base costs for UI display
     */
    fun getAllModelCosts(): Map<String, Int> {
        return BASE_CREDIT_COSTS.toMap()
    }

    /**
     * Check if model is image-to-image
     */
    fun isImageToImageModel(modelId: String): Boolean {
        return when (modelId) {
            "flux/dev/image-to-image",
            "flux/kontext-max/image-to-image",
            "flux/kontext-pro/image-to-image" -> true
            else -> false
        }
    }

    /**
     * Get maximum number of source images for image-to-image models
     */
    fun getMaxSourceImages(modelId: String): Int {
        return when (modelId) {
            "flux/kontext-max/image-to-image",
            "flux/kontext-pro/image-to-image" -> 4
            "flux/dev/image-to-image" -> 1
            else -> 1
        }
    }

    /**
     * Check if model supports multiple source images
     */
    fun supportsMultipleSourceImages(modelId: String): Boolean {
        return getMaxSourceImages(modelId) > 1
    }

    /**
     * Get maximum number of output images for a model
     */
    fun getMaxOutputImages(modelId: String): Int {
        return when (modelId) {
            "dall-e-3" -> 1 // DALL-E 3 only supports 1 image
            "recraft-v3" -> 1 // Recraft v3 only supports 1 image
            else -> 4
        }
    }

    /**
     * Validate if the requested number of images is supported
     */
    fun validateNumImages(modelId: String, numImages: Int): Boolean {
        val maxImages = getMaxOutputImages(modelId)
        return numImages in 1..maxImages
    }

    /**
     * Get model complexity tier for UI grouping
     */
    fun getModelTier(modelId: String): ModelTier {
        return when (getBaseCreditCost(modelId)) {
            in 1..5 -> ModelTier.BASIC
            in 6..8 -> ModelTier.STANDARD
            in 9..10 -> ModelTier.PREMIUM
            else -> ModelTier.ENTERPRISE
        }
    }

    /**
     * Get recommended use case for each model
     */
    fun getModelRecommendation(modelId: String): String {
        return when (modelId) {
            "dall-e-3" -> "Best for creative, artistic images with high quality"
            "bytedance/seedream-3.0" -> "Great for realistic images with watermark protection"
            "stable-diffusion-v35-large" -> "Versatile model with negative prompts support"
            "flux/schnell" -> "Fastest generation, perfect for quick iterations"
            "flux-realism" -> "Specialized in photorealistic imagery"
            "recraft-v3" -> "Most versatile with 24+ artistic styles and custom colors"
            "flux/dev/image-to-image" -> "Transform existing images with AI"
            "flux/kontext-max/image-to-image" -> "Advanced image transformation with multi-image support"
            "flux/kontext-pro/image-to-image" -> "Professional image editing with maximum control"
            else -> "AI-powered image generation"
        }
    }

    /**
     * Check if user has enough credits for generation
     */
    fun hasEnoughCredits(
        currentCredits: Int,
        modelId: String,
        quality: String = "standard",
        numImages: Int = 1,
        isSubscribed: Boolean = false
    ): Boolean {
        if (isSubscribed) return true

        val requiredCredits = getImageGenerationCost(modelId, quality, numImages)
        return currentCredits >= requiredCredits
    }

    /**
     * Calculate credits needed for user to afford generation
     */
    fun getCreditsNeeded(
        currentCredits: Int,
        modelId: String,
        quality: String = "standard",
        numImages: Int = 1
    ): Int {
        val requiredCredits = getImageGenerationCost(modelId, quality, numImages)
        return maxOf(0, requiredCredits - currentCredits)
    }

    /**
     * Get cost breakdown for display
     */
    fun getCostBreakdown(
        modelId: String,
        quality: String = "standard",
        numImages: Int = 1
    ): CostBreakdown {
        val baseCost = getBaseCreditCost(modelId)
        val qualityMultiplier = getQualityMultiplier(quality)
        val totalCost = getImageGenerationCost(modelId, quality, numImages)

        return CostBreakdown(
            baseCost = baseCost,
            qualityMultiplier = qualityMultiplier,
            numImages = numImages,
            totalCost = totalCost
        )
    }

    enum class ModelTier {
        BASIC,      // 1-5 credits
        STANDARD,   // 6-8 credits
        PREMIUM,    // 9-10 credits
        ENTERPRISE  // 11+ credits
    }

    data class CostBreakdown(
        val baseCost: Int,
        val qualityMultiplier: Float,
        val numImages: Int,
        val totalCost: Int
    ) {
        fun getBreakdownText(): String {
            val parts = mutableListOf<String>()
            parts.add("$baseCost base")

            if (qualityMultiplier != 1.0f) {
                parts.add("×${qualityMultiplier} quality")
            }

            if (numImages > 1) {
                parts.add("×$numImages images")
            }

            return parts.joinToString(" ") + " = $totalCost credits"
        }
    }
}