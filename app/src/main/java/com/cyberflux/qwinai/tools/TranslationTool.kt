package com.cyberflux.qwinai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tool for translating text between languages
 */
class TranslationTool(private val context: Context) : Tool {
    override val id: String = "translator"
    override val name: String = "Language Translator"
    override val description: String = "Translates text between different languages"

    // Translation API configuration (using LibreTranslate)
    private val BASE_URL = "https://libretranslate.de/translate"
    // Or use "https://translate.googleapis.com/translate_a/single" for Google Translate

    // HTTP Client for API requests
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Supported language codes and names
    private val supportedLanguages = mapOf(
        "auto" to "Detect Language",
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "zh" to "Chinese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "hi" to "Hindi"
    )

    // Patterns to recognize translation requests
    private val translationPatterns = listOf(
        Pattern.compile("\\btranslate\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bconvert\\b.+\\bto\\b.+\\blanguage\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bhow\\s+to\\s+say\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwhat\\s+is\\b.+\\bin\\b.+(?:language|spanish|french|german|chinese|japanese|russian|italian|portuguese|arabic|hindi)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bfrom\\b.+\\bto\\b.+\\blanguage\\b", Pattern.CASE_INSENSITIVE)
    )

    override fun canHandle(message: String): Boolean {
        return translationPatterns.any { it.matcher(message).find() }
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("TranslationTool: Executing with message: ${message.take(50)}...")

            // Extract text and languages from message or parameters
            val textToTranslate = parameters["text"] as? String ?: extractTextToTranslate(message)
            val sourceLanguage = parameters["sourceLanguage"] as? String ?: extractSourceLanguage(message)
            val targetLanguage = parameters["targetLanguage"] as? String ?: extractTargetLanguage(message)

            if (textToTranslate.isBlank()) {
                return@withContext ToolResult.error(
                    "No text to translate",
                    "I couldn't determine what text you want to translate. Please specify the text more clearly."
                )
            }

            if (targetLanguage.isBlank()) {
                return@withContext ToolResult.error(
                    "No target language specified",
                    "I couldn't determine which language you want to translate to. Please specify the target language."
                )
            }

            Timber.d("TranslationTool: Translating '$textToTranslate' from $sourceLanguage to $targetLanguage")

            // Perform the translation
            val translationResult = translateText(textToTranslate, sourceLanguage, targetLanguage)

            if (translationResult.first == null) {
                return@withContext ToolResult.error(
                    "Translation failed: ${translationResult.second}",
                    "There was an error translating the text: ${translationResult.second}"
                )
            }

            val translatedText = translationResult.first!!
            val sourceLanguageName = getLanguageName(sourceLanguage)
            val targetLanguageName = getLanguageName(targetLanguage)

            // Format the result
            val content = buildString {
                append("Translation from $sourceLanguageName to $targetLanguageName:\n\n")
                append("Original: $textToTranslate\n\n")
                append("Translation: $translatedText")
            }

            return@withContext ToolResult.success(
                content = content,
                data = mapOf(
                    "originalText" to textToTranslate,
                    "translatedText" to translatedText,
                    "sourceLanguage" to sourceLanguage,
                    "targetLanguage" to targetLanguage
                ),
                metadata = mapOf(
                    "sourceLanguageName" to sourceLanguageName,
                    "targetLanguageName" to targetLanguageName
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "TranslationTool: Error during translation: ${e.message}")
            return@withContext ToolResult.error(
                "Translation error: ${e.message}",
                "There was an error performing the translation: ${e.message}"
            )
        }
    }

    /**
     * Extract the text to translate from the message
     */
    private fun extractTextToTranslate(message: String): String {
        // Try to find text between quotes first
        val quotePatterns = listOf(
            "translate\\s+[\"'](.*?)[\"']\\s+(?:from|to|into)",
            "translate\\s+[\"'](.*?)[\"']",
            "say\\s+[\"'](.*?)[\"']\\s+in",
            "what\\s+is\\s+[\"'](.*?)[\"']\\s+in"
        )

        for (pattern in quotePatterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(message)
            if (matcher.find()) {
                return matcher.group(1).trim()
            }
        }

        // Try specific translation patterns
        val translatePatterns = listOf(
            "translate\\s+(.*?)\\s+(?:from|to|into)",
            "translate\\s+(.*?)$",
            "say\\s+(.*?)\\s+in",
            "what\\s+is\\s+(.*?)\\s+in"
        )

        for (pattern in translatePatterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(message)
            if (matcher.find()) {
                val extracted = matcher.group(1).trim()

                // Don't include language names in the text
                for ((_, languageName) in supportedLanguages) {
                    if (extracted.contains(languageName, ignoreCase = true)) {
                        return extracted.replace(languageName, "", ignoreCase = true).trim()
                            .replace(Regex("\\s+"), " ")
                    }
                }

                return extracted
            }
        }

        // Fallback: treat the entire message as text to translate
        return message
    }

    /**
     * Extract the source language from the message
     */
    private fun extractSourceLanguage(message: String): String {
        // Try to find patterns like "from [language]" or "in [language]"
        val fromPattern = Pattern.compile("from\\s+(\\w+)", Pattern.CASE_INSENSITIVE)
        val matcher = fromPattern.matcher(message)

        if (matcher.find()) {
            val language = matcher.group(1).trim().lowercase()
            return getLanguageCode(language)
        }

        // Default to auto-detection
        return "auto"
    }

    /**
     * Extract the target language from the message
     */
    private fun extractTargetLanguage(message: String): String {
        // Try to find patterns like "to [language]" or "into [language]"
        val patterns = listOf(
            "to\\s+(\\w+)(?:\\s|$|\\.|,)",
            "into\\s+(\\w+)(?:\\s|$|\\.|,)",
            "in\\s+(\\w+)(?:\\s|$|\\.|,)"
        )

        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(message)

            if (matcher.find()) {
                val language = matcher.group(1).trim().lowercase()
                return getLanguageCode(language)
            }
        }

        // Try to find any supported language name in the message
        for ((code, name) in supportedLanguages) {
            if (code != "auto" && message.contains(name, ignoreCase = true)) {
                return code
            }
        }

        // Default to English if no target language found
        return "en"
    }

    /**
     * Get language code from language name
     */
    private fun getLanguageCode(language: String): String {
        // Direct match with code
        if (supportedLanguages.containsKey(language)) {
            return language
        }

        // Match with language name
        for ((code, name) in supportedLanguages) {
            if (name.equals(language, ignoreCase = true)) {
                return code
            }
        }

        // Try partial matches
        for ((code, name) in supportedLanguages) {
            if (name.startsWith(language, ignoreCase = true) ||
                name.contains(language, ignoreCase = true)) {
                return code
            }
        }

        // If not found, try to match with locale
        val locales = Locale.getAvailableLocales()
        for (locale in locales) {
            if (locale.displayLanguage.equals(language, ignoreCase = true) ||
                locale.displayLanguage.contains(language, ignoreCase = true)) {

                val localeCode = locale.language
                if (supportedLanguages.containsKey(localeCode)) {
                    return localeCode
                }
            }
        }

        // Default to auto if not found
        return "auto"
    }

    /**
     * Get language name from code
     */
    private fun getLanguageName(code: String): String {
        return supportedLanguages[code] ?: code
    }

    /**
     * Translate text using the translation API
     * @return Pair<translatedText, errorMessage>
     */
    private suspend fun translateText(text: String, sourceLanguage: String, targetLanguage: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            // Create request body
            val jsonBody = JSONObject().apply {
                put("q", text)
                put("source", sourceLanguage)
                put("target", targetLanguage)
                put("format", "text")
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

            // Create request
            val request = Request.Builder()
                .url(BASE_URL)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            // Execute request
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Pair(null, "API request failed with code ${response.code}")
                }

                val responseBody = response.body?.string() ?: return@withContext Pair(null, "Empty response from API")

                // Parse response
                val responseJson = JSONObject(responseBody)

                // Check for error
                if (responseJson.has("error")) {
                    return@withContext Pair(null, responseJson.getString("error"))
                }

                // Get translated text
                val translatedText = responseJson.getString("translatedText")

                return@withContext Pair(translatedText, null)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in translation API call: ${e.message}")
            return@withContext Pair(null, e.message)
        }
    }
}