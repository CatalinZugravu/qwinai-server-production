package com.cyberflux.qwinai.utils

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object ImageGenerationUtils {

    /**
     * Create DALL-E 3 request with exact API parameters
     */
    fun createDalle3Request(
        prompt: String,
        size: String = "1024x1024",
        style: String = "vivid",
        quality: String = "standard",
        responseFormat: String = "url",
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "dall-e-3")
            put("prompt", prompt)
            put("n", 1) // DALL-E 3 only supports n=1
            put("size", size)
            put("quality", quality)
            put("style", style)
            put("response_format", responseFormat)
        }
    }

    /**
     * Create SeedDream 3.0 request with exact API parameters
     */
    fun createSeedDream3Request(
        prompt: String,
        size: String = "1024x1024",
        seed: Int? = null,
        guidanceScale: Float = 7.5f,
        responseFormat: String = "url",
        watermark: Boolean = false,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "bytedance/seedream-3.0")
            put("prompt", prompt)
            put("size", size)
            put("response_format", responseFormat)
            put("guidance_scale", guidanceScale)
            put("watermark", watermark)

            // Optional parameters
            seed?.let { put("seed", it) }
        }
    }

    /**
     * Create Stable Diffusion request with exact API parameters
     */
    fun createStableDiffusionRequest(
        prompt: String,
        negativePrompt: String = "",
        imageSize: String = "square_hd",
        guidanceScale: Float = 7.5f,
        numInferenceSteps: Int = 25,
        enableSafetyChecker: Boolean = true,
        outputFormat: String = "jpeg",
        numImages: Int = 1,
        seed: Int? = null,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "stable-diffusion-v35-large")
            put("prompt", prompt)
            put("image_size", imageSize)

            if (negativePrompt.isNotBlank()) {
                put("negative_prompt", negativePrompt)
            }

            put("guidance_scale", guidanceScale)
            put("num_inference_steps", numInferenceSteps)
            put("enable_safety_checker", enableSafetyChecker)
            put("output_format", outputFormat)
            put("num_images", numImages)

            seed?.let { put("seed", it) }
        }
    }

    /**
     * Create Flux Schnell request with exact API parameters
     */
    fun createFluxSchnellRequest(
        prompt: String,
        imageSize: String = "landscape_4_3",
        numInferenceSteps: Int = 1,
        enableSafetyChecker: Boolean = true,
        numImages: Int = 1,
        seed: Int? = null,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "flux/schnell")
            put("prompt", prompt)
            put("image_size", imageSize)
            put("num_inference_steps", numInferenceSteps)
            put("enable_safety_checker", enableSafetyChecker)
            put("num_images", numImages)

            seed?.let { put("seed", it) }
        }
    }

    /**
     * Create Flux Realism request with exact API parameters
     */
    fun createFluxRealismRequest(
        prompt: String,
        imageSize: String = "landscape_4_3",
        guidanceScale: Float = 7.5f,
        numInferenceSteps: Int = 25,
        enableSafetyChecker: Boolean = true,
        outputFormat: String = "jpeg",
        numImages: Int = 1,
        seed: Int? = null,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "flux-realism")
            put("prompt", prompt)
            put("image_size", imageSize)
            put("guidance_scale", guidanceScale)
            put("num_inference_steps", numInferenceSteps)
            put("enable_safety_checker", enableSafetyChecker)
            put("output_format", outputFormat)
            put("num_images", numImages)

            seed?.let { put("seed", it) }
        }
    }

    /**
     * Create Recraft v3 request with exact API parameters and complete style options
     */
    fun createRecraftRequest(
        prompt: String,
        imageSize: String = "square_hd",
        style: String = "realistic_image",
        colors: List<Triple<Int, Int, Int>> = emptyList(),
        numImages: Int = 1,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "recraft-v3")
            put("prompt", prompt)
            put("image_size", imageSize)
            put("style", style)
            put("num_images", numImages)

            if (colors.isNotEmpty()) {
                val colorsArray = JSONArray()
                for (color in colors) {
                    val colorObj = JSONObject().apply {
                        put("r", color.first)
                        put("g", color.second)
                        put("b", color.third)
                    }
                    colorsArray.put(colorObj)
                }
                put("colors", colorsArray)
            }
        }
    }

    /**
     * Create Flux Dev Image-to-Image request with base64 data URL for image_url parameter
     * Based on AIMLAPI documentation - uses image_url parameter with base64 data URL
     */
    fun createFluxDevImageToImageRequest(
        prompt: String,
        imageBase64DataUrl: String,
        guidanceScale: Float = 7.5f,
        numInferenceSteps: Int = 25,
        enableSafetyChecker: Boolean = true,
        strength: Float = 0.95f,
        numImages: Int = 1,
        seed: Int? = null,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", "flux/dev/image-to-image")
            put("prompt", prompt)

            // AIMLAPI expects base64 data URL in image_url parameter
            put("image_url", imageBase64DataUrl)

            put("guidance_scale", guidanceScale)
            put("num_inference_steps", numInferenceSteps)
            put("enable_safety_checker", enableSafetyChecker)
            put("strength", strength)
            put("num_images", numImages)

            seed?.let { put("seed", it) }

            Timber.d("Created Flux Dev I2I request with image_url length: ${imageBase64DataUrl.length}")
        }
    }

    /**
     * Create Flux Kontext Max/Pro Image-to-Image request with correct AIMLAPI parameters
     * Based on the API example provided by the user
     */
    fun createFluxKontextImageToImageRequest(
        modelId: String,
        prompt: String,
        imageBase64DataUrls: List<String>,
        guidanceScale: Float = 7.5f,
        safetyTolerance: String = "2",
        outputFormat: String = "jpeg",
        aspectRatio: String = "16:9",
        numImages: Int = 1,
        seed: Int? = null,
        enableProgressiveLoading: Boolean = true
    ): JSONObject {
        return JSONObject().apply {
            put("model", modelId)
            put("prompt", prompt)
            put("guidance_scale", guidanceScale)
            put("safety_tolerance", safetyTolerance)
            put("output_format", outputFormat)
            put("aspect_ratio", aspectRatio)
            put("num_images", numImages)

            // Handle single or multiple images with base64 data URLs
            // Based on AIMLAPI format, use image_url for single image
            if (imageBase64DataUrls.size == 1) {
                put("image_url", imageBase64DataUrls[0])
                Timber.d("Created Flux Kontext request with single image_url (${imageBase64DataUrls[0].length} chars)")
            } else {
                // For multiple images, we might need to use a different parameter
                // Based on documentation, some models support multiple images
                val imagesArray = JSONArray()
                imageBase64DataUrls.forEach { imagesArray.put(it) }
                put("image_urls", imagesArray) // Using plural for multiple images
                Timber.d("Created Flux Kontext request with ${imageBase64DataUrls.size} images")
            }

            seed?.let { put("seed", it) }
        }
    }

    /**
     * Get model display name for UI
     */
    fun getModelDisplayName(modelId: String): String {
        return when (modelId) {
            "dall-e-3" -> "DALL-E 3"
            "stable-diffusion-v35-large" -> "Stable Diffusion v3.5"
            "flux/schnell" -> "Flux Schnell"
            "flux-realism" -> "Flux Realism"
            "recraft-v3" -> "Recraft v3"
            "flux/dev/image-to-image" -> "Flux Dev I2I"
            "flux/kontext-max/image-to-image" -> "Flux Kontext Max I2I"
            "flux/kontext-pro/image-to-image" -> "Flux Kontext Pro I2I"
            "bytedance/seedream-3.0" -> "SeedDream 3.0"
            else -> modelId
        }
    }

    /**
     * Get supported sizes for a specific model
     */
    fun getSupportedSizes(modelId: String): List<String> {
        return when (modelId) {
            "dall-e-3" -> listOf("1024x1024", "1024x1792", "1792x1024")
            "bytedance/seedream-3.0" -> listOf("1024x1024", "1024x1792", "1792x1024", "512x512", "768x768")
            "stable-diffusion-v35-large", "flux/schnell", "flux-realism", "recraft-v3" -> {
                listOf("square_hd", "square", "portrait_4_3", "portrait_16_9", "landscape_4_3", "landscape_16_9")
            }
            else -> listOf("1024x1024")
        }
    }

    /**
     * Get supported styles for a specific model
     */
    fun getSupportedStyles(modelId: String): List<String> {
        return when (modelId) {
            "dall-e-3" -> listOf("vivid", "natural")
            "recraft-v3" -> listOf(
                "any",
                "realistic_image",
                "digital_illustration",
                "vector_illustration",
                "realistic_image/b_and_w",
                "realistic_image/hard_flash",
                "realistic_image/hdr",
                "realistic_image/natural_light",
                "realistic_image/studio_portrait",
                "realistic_image/enterprise",
                "realistic_image/motion_blur",
                "digital_illustration/pixel_art",
                "digital_illustration/hand_drawn",
                "digital_illustration/grain",
                "digital_illustration/infantile_sketch",
                "digital_illustration/2d_art_poster",
                "digital_illustration/handmade_3d",
                "digital_illustration/hand_drawn_outline",
                "digital_illustration/engraving_color",
                "digital_illustration/2d_art_poster_2",
                "vector_illustration/engraving",
                "vector_illustration/line_art",
                "vector_illustration/line_circuit",
                "vector_illustration/linocut"
            )
            else -> emptyList()
        }
    }

    /**
     * Get supported quality options for a specific model
     */
    fun getSupportedQualities(modelId: String): List<String> {
        return when (modelId) {
            "dall-e-3" -> listOf("standard", "hd")
            else -> listOf("standard")
        }
    }

    /**
     * Get supported output formats for a specific model
     */
    fun getSupportedOutputFormats(modelId: String): List<String> {
        return when (modelId) {
            "dall-e-3" -> listOf("url", "b64_json")
            "bytedance/seedream-3.0" -> listOf("url", "b64_json")
            "stable-diffusion-v35-large", "flux-realism" -> listOf("jpeg", "png")
            "flux/kontext-max/image-to-image", "flux/kontext-pro/image-to-image" -> listOf("jpeg", "png")
            else -> listOf("url")
        }
    }

    /**
     * Get supported aspect ratios for image-to-image models
     */
    fun getSupportedAspectRatios(modelId: String): List<String> {
        return when (modelId) {
            "flux/kontext-max/image-to-image",
            "flux/kontext-pro/image-to-image" -> listOf(
                "21:9", "16:9", "4:3", "3:2", "1:1", "2:3", "3:4", "9:16", "9:21"
            )
            else -> listOf("16:9", "1:1", "4:3", "3:4")
        }
    }

    /**
     * Get guidance scale range for a specific model
     */
    fun getGuidanceScaleRange(modelId: String): Pair<Float, Float> {
        return when (modelId) {
            "bytedance/seedream-3.0" -> Pair(1.0f, 10.0f)
            "stable-diffusion-v35-large", "flux-realism", "flux/dev/image-to-image",
            "flux/kontext-max/image-to-image", "flux/kontext-pro/image-to-image" -> Pair(1.0f, 20.0f)
            else -> Pair(1.0f, 20.0f)
        }
    }

    /**
     * Get inference steps range for a specific model
     */
    fun getInferenceStepsRange(modelId: String): Pair<Int, Int> {
        return when (modelId) {
            "flux/schnell" -> Pair(1, 12)
            "stable-diffusion-v35-large", "flux-realism", "flux/dev/image-to-image" -> Pair(1, 50)
            else -> Pair(1, 50)
        }
    }

    /**
     * Get maximum number of images for a specific model
     */
    fun getMaxNumImages(modelId: String): Int {
        return when (modelId) {
            "dall-e-3" -> 1 // DALL-E 3 only supports 1 image
            "recraft-v3" -> 1 // Recraft v3 only supports 1 image
            else -> 4
        }
    }

    /**
     * Check if a model supports negative prompts
     */
    fun supportsNegativePrompt(modelId: String): Boolean {
        return when (modelId) {
            "stable-diffusion-v35-large" -> true
            else -> false
        }
    }

    /**
     * Check if a model supports custom colors
     */
    fun supportsCustomColors(modelId: String): Boolean {
        return when (modelId) {
            "recraft-v3" -> true
            else -> false
        }
    }

    /**
     * Check if a model supports watermarks
     */
    fun supportsWatermark(modelId: String): Boolean {
        return when (modelId) {
            "bytedance/seedream-3.0" -> true
            else -> false
        }
    }

    /**
     * Check if a model supports safety checker
     */
    fun supportsSafetyChecker(modelId: String): Boolean {
        return when (modelId) {
            "stable-diffusion-v35-large", "flux/schnell", "flux-realism", "flux/dev/image-to-image" -> true
            else -> false
        }
    }

    /**
     * Check if a model supports seed parameter
     */
    fun supportsSeed(modelId: String): Boolean {
        return when (modelId) {
            "dall-e-3" -> false // DALL-E 3 doesn't support custom seeds
            else -> true
        }
    }

    /**
     * Check if a model supports strength parameter (for image-to-image)
     */
    fun supportsStrength(modelId: String): Boolean {
        return when (modelId) {
            "flux/dev/image-to-image" -> true
            else -> false
        }
    }

    /**
     * Check if a model supports safety tolerance
     */
    fun supportsSafetyTolerance(modelId: String): Boolean {
        return when (modelId) {
            "flux/kontext-max/image-to-image", "flux/kontext-pro/image-to-image" -> true
            else -> false
        }
    }

    /**
     * Get style display name for UI
     */
    fun getStyleDisplayName(styleId: String): String {
        return when (styleId) {
            "realistic_image" -> "Realistic Image"
            "digital_illustration" -> "Digital Illustration"
            "vector_illustration" -> "Vector Illustration"
            "realistic_image/b_and_w" -> "Realistic - Black & White"
            "realistic_image/hard_flash" -> "Realistic - Hard Flash"
            "realistic_image/hdr" -> "Realistic - HDR"
            "realistic_image/natural_light" -> "Realistic - Natural Light"
            "realistic_image/studio_portrait" -> "Realistic - Studio Portrait"
            "realistic_image/enterprise" -> "Realistic - Enterprise"
            "realistic_image/motion_blur" -> "Realistic - Motion Blur"
            "digital_illustration/pixel_art" -> "Digital - Pixel Art"
            "digital_illustration/hand_drawn" -> "Digital - Hand Drawn"
            "digital_illustration/grain" -> "Digital - Grain"
            "digital_illustration/infantile_sketch" -> "Digital - Infantile Sketch"
            "digital_illustration/2d_art_poster" -> "Digital - 2D Art Poster"
            "digital_illustration/handmade_3d" -> "Digital - Handmade 3D"
            "digital_illustration/hand_drawn_outline" -> "Digital - Hand Drawn Outline"
            "digital_illustration/engraving_color" -> "Digital - Engraving Color"
            "digital_illustration/2d_art_poster_2" -> "Digital - 2D Art Poster 2"
            "vector_illustration/engraving" -> "Vector - Engraving"
            "vector_illustration/line_art" -> "Vector - Line Art"
            "vector_illustration/line_circuit" -> "Vector - Line Circuit"
            "vector_illustration/linocut" -> "Vector - Linocut"
            "vivid" -> "Vivid"
            "natural" -> "Natural"
            "any" -> "Any Style"
            else -> styleId.replace("_", " ").replaceFirstChar { it.titlecase() }
        }
    }

    /**
     * Get aspect ratio display name for UI
     */
    fun getAspectRatioDisplayName(aspectRatio: String): String {
        return when (aspectRatio) {
            "21:9" -> "Ultra Wide (21:9)"
            "16:9" -> "Widescreen (16:9)"
            "4:3" -> "Standard (4:3)"
            "3:2" -> "Photo (3:2)"
            "1:1" -> "Square (1:1)"
            "2:3" -> "Photo Portrait (2:3)"
            "3:4" -> "Standard Portrait (3:4)"
            "9:16" -> "Vertical (9:16)"
            "9:21" -> "Ultra Tall (9:21)"
            "square_hd" -> "Square HD"
            "square" -> "Square"
            "portrait_4_3" -> "Portrait 4:3"
            "portrait_16_9" -> "Portrait 16:9"
            "landscape_4_3" -> "Landscape 4:3"
            "landscape_16_9" -> "Landscape 16:9"
            else -> aspectRatio
        }
    }

    /**
     * Validate model parameters before making API request
     */
    fun validateModelParameters(
        modelId: String,
        prompt: String,
        size: String? = null,
        style: String? = null,
        quality: String? = null,
        outputFormat: String? = null,
        numImages: Int = 1,
        guidanceScale: Float? = null,
        numInferenceSteps: Int? = null
    ): List<String> {
        val errors = mutableListOf<String>()

        // Check prompt length
        if (prompt.length > 4000) {
            errors.add("Prompt must be 4000 characters or less")
        }

        // Check size/aspect ratio
        size?.let { s ->
            val supportedSizes = getSupportedSizes(modelId)
            if (supportedSizes.isNotEmpty() && !supportedSizes.contains(s)) {
                errors.add("Size '$s' is not supported for model '$modelId'")
            }
        }

        // Check style
        style?.let { st ->
            val supportedStyles = getSupportedStyles(modelId)
            if (supportedStyles.isNotEmpty() && !supportedStyles.contains(st)) {
                errors.add("Style '$st' is not supported for model '$modelId'")
            }
        }

        // Check quality
        quality?.let { q ->
            val supportedQualities = getSupportedQualities(modelId)
            if (supportedQualities.isNotEmpty() && !supportedQualities.contains(q)) {
                errors.add("Quality '$q' is not supported for model '$modelId'")
            }
        }

        // Check output format
        outputFormat?.let { of ->
            val supportedFormats = getSupportedOutputFormats(modelId)
            if (supportedFormats.isNotEmpty() && !supportedFormats.contains(of)) {
                errors.add("Output format '$of' is not supported for model '$modelId'")
            }
        }

        // Check number of images
        val maxImages = getMaxNumImages(modelId)
        if (numImages > maxImages) {
            errors.add("Model '$modelId' supports maximum $maxImages images, requested $numImages")
        }

        // Check guidance scale
        guidanceScale?.let { gs ->
            val (min, max) = getGuidanceScaleRange(modelId)
            if (gs < min || gs > max) {
                errors.add("Guidance scale must be between $min and $max for model '$modelId'")
            }
        }

        // Check inference steps
        numInferenceSteps?.let { steps ->
            val (min, max) = getInferenceStepsRange(modelId)
            if (steps < min || steps > max) {
                errors.add("Inference steps must be between $min and $max for model '$modelId'")
            }
        }

        return errors
    }
}