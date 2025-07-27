package com.cyberflux.qwinai.utils

import android.annotation.SuppressLint
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Enhanced TokenValidator with better token estimation
 */
object TokenValidator {

    // Improved token estimation constants
    private const val AVG_CHARS_PER_TOKEN = 4.0
    private const val WHITESPACE_ADJUSTMENT = 0.8 // Whitespace tends to be more efficient in tokenization
    private const val SAFETY_MARGIN = 1.05 // 5% safety margin for token estimation

    // Cache token counts for better performance
    private val tokenCountCache = mutableMapOf<String, Int>()
    private const val MAX_CACHE_SIZE = 1000

    /**
     * Get the effective maximum input tokens based on subscription status
     * Non-subscribers get half the maximum token limit
     */
    fun getEffectiveMaxInputTokens(modelId: String, isSubscribed: Boolean): Int {
        // Get model configuration from ModelConfigManager
        val config = ModelConfigManager.getConfig(modelId)

        // If model not found, use a reasonable default
        if (config == null) {
            Timber.w("Model configuration not found for $modelId, using default fallback")
            return if (isSubscribed) 16000 else 8000  // Default fallback
        }

        // Get the explicitly defined maxInputTokens
        val maxInputTokens = config.maxInputTokens

        // For non-subscribers, limit to half of the regular limit
        val effectiveTokens = if (isSubscribed) {
            maxInputTokens
        } else {
            maxInputTokens / 2
        }

        Timber.d("Effective input tokens for $modelId (subscribed: $isSubscribed): $effectiveTokens")
        return effectiveTokens
    }

    /**
     * Get the effective maximum output tokens based on subscription status
     * Non-subscribers get half the maximum token limit
     */
    fun getEffectiveMaxOutputTokens(modelId: String, isSubscribed: Boolean): Int {
        // Get model configuration from ModelConfigManager
        val config = ModelConfigManager.getConfig(modelId)

        // If model not found, use a reasonable default
        if (config == null) {
            Timber.w("Model configuration not found for $modelId, using default fallback")
            return if (isSubscribed) 4000 else 2000  // Default fallback
        }

        // Get the explicitly defined maxOutputTokens
        val maxOutputTokens = config.maxOutputTokens

        // For non-subscribers, limit to half of the regular limit
        val effectiveTokens = if (isSubscribed) {
            maxOutputTokens
        } else {
            maxOutputTokens / 2
        }

        Timber.d("Effective output tokens for $modelId (subscribed: $isSubscribed): $effectiveTokens")
        return effectiveTokens
    }

    /**
     * Get both input and output token limits for a model
     */
    fun getModelTokenLimits(modelId: String, isSubscribed: Boolean): Pair<Int, Int> {
        val inputTokens = getEffectiveMaxInputTokens(modelId, isSubscribed)
        val outputTokens = getEffectiveMaxOutputTokens(modelId, isSubscribed)
        return Pair(inputTokens, outputTokens)
    }

    /**
     * Improved token count estimation that better matches real tokenizers
     */
    fun estimateTokenCount(text: String): Int {
        if (text.isEmpty()) {
            return 0
        }

        // Check cache first
        tokenCountCache[text]?.let { return it }

        // If text is too long, split and estimate
        if (text.length > 1000) {
            val chunks = text.chunked(1000)
            val total = chunks.sumOf { estimateTokenCount(it) }

            // Don't cache very long texts
            return total
        }

        // Count words - this correlates well with many tokenizers
        val words = text.trim().split(Pattern.compile("\\s+")).size

        // Count characters without whitespace
        val charsNoWhitespace = text.replace("\\s".toRegex(), "").length

        // Count total characters
        val totalChars = text.length

        // Calculate whitespace
        val whitespace = totalChars - charsNoWhitespace

        // Estimated token calculation based on GPT-style tokenization patterns
        // This is more accurate than just chars/4 or word count alone
        val charBasedEstimate = charsNoWhitespace / AVG_CHARS_PER_TOKEN +
                (whitespace * WHITESPACE_ADJUSTMENT) / AVG_CHARS_PER_TOKEN

        // For languages like English, words correlate well with tokens
        // For others, character-based estimation works better
        val estimate = maxOf(words, charBasedEstimate.toInt())

        // Apply safety margin to avoid underestimation
        val finalEstimate = (estimate * SAFETY_MARGIN).toInt()

        // Cache result if cache isn't too large
        if (tokenCountCache.size < MAX_CACHE_SIZE) {
            tokenCountCache[text] = finalEstimate
        }

        return finalEstimate
    }

    /**
     * Get a user-friendly token limit message
     */
    fun getTokenLimitMessage(modelId: String, isSubscribed: Boolean): String {
        val maxTokens = getEffectiveMaxInputTokens(modelId, isSubscribed)
        val subscriberTokens = getEffectiveMaxInputTokens(modelId, true)

        val limit = formatTokenCount(maxTokens)
        val subscriberLimit = formatTokenCount(subscriberTokens)

        return if (isSubscribed) {
            "Token limit: $limit"
        } else {
            "Token limit: $limit (Pro: $subscriberLimit)"
        }
    }

    /**
     * Format token count for display (K/M for large numbers)
     */
    @SuppressLint("DefaultLocale")
    fun formatTokenCount(tokens: Int): String {
        return when {
            tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }

    /**
     * Truncate text to fit within specified token count
     */
    fun truncateToTokenCount(text: String, maxTokens: Int): String {
        val currentTokens = estimateTokenCount(text)

        // ✅ FIX: Nu trunca dacă nu e nevoie
        if (currentTokens <= maxTokens) {
            Timber.d("Text fits within $maxTokens tokens (has $currentTokens), no truncation needed")
            return text  // Return original text without truncation message
        }

        Timber.d("Truncating text from $currentTokens tokens to $maxTokens tokens")

        // Reserve tokens for truncation indicator ONLY when actually truncating
        val truncationIndicator = "\n\n[Message truncated to fit $maxTokens token limit]"
        val truncationTokens = estimateTokenCount(truncationIndicator)
        val targetTokens = maxTokens - truncationTokens

        if (targetTokens <= 0) {
            // Extremă situație - măcar să returneze ceva
            return "[Message too long for available token limit]"
        }

        var remainingTokens = targetTokens
        val paragraphs = text.split("\n\n")
        val resultParagraphs = mutableListOf<String>()

        for (paragraph in paragraphs) {
            val paragraphTokens = estimateTokenCount(paragraph)
            if (paragraphTokens <= remainingTokens) {
                resultParagraphs.add(paragraph)
                remainingTokens -= paragraphTokens
            } else if (remainingTokens > 50) { // Minimum pentru un paragraf parțial
                val truncatedParagraph = truncateTextToTokens(paragraph, remainingTokens)
                if (truncatedParagraph.isNotBlank()) {
                    resultParagraphs.add(truncatedParagraph)
                }
                break
            } else {
                break
            }
        }

        val result = if (resultParagraphs.isNotEmpty()) {
            resultParagraphs.joinToString("\n\n") + truncationIndicator
        } else {
            // Fallback dacă nu putem salva nimic
            text.take(maxTokens * 4) + truncationIndicator // Aproximare brută
        }

        val finalTokens = estimateTokenCount(result)
        Timber.d("Truncated result: $finalTokens tokens (target: $maxTokens)")

        return result
    }

/**
     * Truncate a single block of text to fit token count
     */
private fun truncateTextToTokens(text: String, maxTokens: Int): String {
    if (maxTokens <= 0) return ""

    // Try to break at sentence
    val sentences = text.split(Regex("(?<=[.!?]\\s)"))
    val resultSentences = mutableListOf<String>()
    var remainingTokens = maxTokens

    for (sentence in sentences) {
        val sentenceTokens = estimateTokenCount(sentence)
        if (sentenceTokens <= remainingTokens) {
            resultSentences.add(sentence)
            remainingTokens -= sentenceTokens
        } else if (remainingTokens > 10) {
            // Try to include partial sentence by words
            val words = sentence.split(" ")
            val resultWords = mutableListOf<String>()

            for (word in words) {
                val wordTokens = estimateTokenCount(word + " ")
                if (wordTokens <= remainingTokens) {
                    resultWords.add(word)
                    remainingTokens -= wordTokens
                } else {
                    break
                }
            }

            if (resultWords.isNotEmpty()) {
                resultSentences.add(resultWords.joinToString(" ") + "...")
            }
            break
        } else {
            break
        }
    }

    return resultSentences.joinToString("")
}
}