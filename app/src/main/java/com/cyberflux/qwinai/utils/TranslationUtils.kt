package com.cyberflux.qwinai.utils

object TranslationUtils {

    // Models that are excellent for translation tasks
    private val TRANSLATION_COMPATIBLE_MODELS = listOf(
        "claude-3-sonnet-20240229",
        "claude-3-opus-20240229",
        "gpt-4",
        "gpt-4-turbo",
        "gpt-4o"
        // Add any other models that work well with translation
    )

    fun supportsTranslation(modelId: String): Boolean {
        return TRANSLATION_COMPATIBLE_MODELS.any {
            modelId.contains(it, ignoreCase = true)
        }
    }

    // Default model for translation
    const val DEFAULT_TRANSLATION_MODEL = ModelManager.GPT_4_TURBO_ID
}