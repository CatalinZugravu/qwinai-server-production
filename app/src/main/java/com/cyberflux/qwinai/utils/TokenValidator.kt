package com.cyberflux.qwinai.utils

import android.annotation.SuppressLint
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Enhanced TokenValidator with better token estimation
 */
object TokenValidator {

    // Model-specific token estimation constants
    private val MODEL_TOKEN_PATTERNS = mapOf(
        // OpenAI models (using tiktoken-like estimation)
        "gpt" to ModelTokenPattern(3.8, 0.75, 1.10), // GPT models are more efficient
        "o1" to ModelTokenPattern(3.9, 0.75, 1.12), // O1 models similar to GPT
        "o3" to ModelTokenPattern(3.9, 0.75, 1.12), // O3 models similar to GPT
        "chatgpt" to ModelTokenPattern(3.8, 0.75, 1.10),
        
        // Anthropic models (Claude tokenizer)
        "claude" to ModelTokenPattern(4.2, 0.80, 1.15), // Claude is less efficient
        
        // Meta models (Llama tokenizer)
        "llama" to ModelTokenPattern(4.5, 0.85, 1.20), // Llama less efficient
        "meta" to ModelTokenPattern(4.5, 0.85, 1.20),
        
        // Google models (SentencePiece)
        "gemma" to ModelTokenPattern(5.0, 0.90, 1.25), // Gemma least efficient
        "gemini" to ModelTokenPattern(4.8, 0.85, 1.20),
        
        // Other models
        "qwen" to ModelTokenPattern(4.3, 0.80, 1.18),
        "mistral" to ModelTokenPattern(4.1, 0.78, 1.15),
        "deepseek" to ModelTokenPattern(4.4, 0.82, 1.18),
        "grok" to ModelTokenPattern(4.0, 0.77, 1.12),
        "cohere" to ModelTokenPattern(4.2, 0.80, 1.15),
        "perplexity" to ModelTokenPattern(4.0, 0.77, 1.12)
    )
    
    // Default pattern for unknown models
    private val DEFAULT_TOKEN_PATTERN = ModelTokenPattern(4.2, 0.80, 1.15)
    
    data class ModelTokenPattern(
        val avgCharsPerToken: Double,
        val whitespaceAdjustment: Double,
        val safetyMargin: Double
    )

    // Cache token counts for better performance
    private val tokenCountCache = mutableMapOf<String, Int>()
    private const val MAX_CACHE_SIZE = 1000

    /**
     * Get the effective maximum input tokens based on subscription status
     * Free users get exactly 1000 input tokens as per new requirements
     */
    fun getEffectiveMaxInputTokens(modelId: String, isSubscribed: Boolean): Int {
        // Get model configuration from ModelConfigManager
        val config = ModelConfigManager.getConfig(modelId)

        // If model not found, use a reasonable default
        if (config == null) {
            Timber.w("Model configuration not found for $modelId, using default fallback")
            return if (isSubscribed) 16000 else 1000  // New: 1000 for free users
        }

        // For free users, use the new unified limit of 1000 tokens
        val effectiveTokens = if (isSubscribed) {
            config.maxInputTokens
        } else {
            1000  // New: Fixed 1000 input tokens for free users
        }

        Timber.d("Effective input tokens for $modelId (subscribed: $isSubscribed): $effectiveTokens")
        return effectiveTokens
    }

    /**
     * Get the effective maximum output tokens based on subscription status
     * Free users get exactly 1500 output tokens as per new requirements
     */
    fun getEffectiveMaxOutputTokens(modelId: String, isSubscribed: Boolean): Int {
        // Get model configuration from ModelConfigManager
        val config = ModelConfigManager.getConfig(modelId)

        // If model not found, use a reasonable default
        if (config == null) {
            Timber.w("Model configuration not found for $modelId, using default fallback")
            return if (isSubscribed) 4000 else 1500  // New: 1500 for free users
        }

        // For free users, use the new unified limit of 1500 tokens
        val effectiveTokens = if (isSubscribed) {
            config.maxOutputTokens
        } else {
            1500  // New: Fixed 1500 output tokens for free users
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
     * Model-aware token count estimation that matches real tokenizers
     */
    fun estimateTokenCount(text: String, modelId: String = ""): Int {
        if (text.isEmpty()) {
            return 0
        }

        // Create cache key including model info for accuracy
        val cacheKey = if (modelId.isNotEmpty()) "$modelId:$text" else text
        tokenCountCache[cacheKey]?.let { return it }

        // If text is too long, split and estimate
        if (text.length > 1000) {
            val chunks = text.chunked(1000)
            val total = chunks.sumOf { estimateTokenCount(it, modelId) }
            return total
        }

        // Get model-specific pattern
        val pattern = getModelTokenPattern(modelId)
        
        // Count words - correlates well with many tokenizers
        val words = text.trim().split(Pattern.compile("\\s+")).size

        // Count characters without whitespace
        val charsNoWhitespace = text.replace("\\s".toRegex(), "").length

        // Count total characters
        val totalChars = text.length

        // Calculate whitespace
        val whitespace = totalChars - charsNoWhitespace

        // Model-specific token calculation
        val charBasedEstimate = charsNoWhitespace / pattern.avgCharsPerToken +
                (whitespace * pattern.whitespaceAdjustment) / pattern.avgCharsPerToken

        // Account for special tokens and encoding differences
        val estimate = when {
            // Code or structured text (more tokens due to symbols)
            text.contains("{") || text.contains("[") || text.contains("<") -> 
                maxOf(words * 1.2, charBasedEstimate * 1.1).toInt()
            // Natural language text
            else -> maxOf(words, charBasedEstimate.toInt())
        }

        // Apply model-specific safety margin
        val finalEstimate = (estimate * pattern.safetyMargin).toInt()

        // Cache result if cache isn't too large
        if (tokenCountCache.size < MAX_CACHE_SIZE) {
            tokenCountCache[cacheKey] = finalEstimate
        }

        return finalEstimate
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun estimateTokenCount(text: String): Int = estimateTokenCount(text, "")
    
    /**
     * Get model-specific token pattern
     */
    private fun getModelTokenPattern(modelId: String): ModelTokenPattern {
        if (modelId.isEmpty()) return DEFAULT_TOKEN_PATTERN
        
        val lowerModelId = modelId.lowercase()
        return MODEL_TOKEN_PATTERNS.entries.find { (key, _) ->
            lowerModelId.contains(key)
        }?.value ?: DEFAULT_TOKEN_PATTERN
    }
    
    /**
     * Get more accurate token count for specific model
     */
    fun getAccurateTokenCount(text: String, modelId: String): Int {
        return estimateTokenCount(text, modelId)
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
     * Truncate text to fit within specified token count with model awareness
     */
    fun truncateToTokenCount(text: String, maxTokens: Int, modelId: String = ""): String {
        val currentTokens = estimateTokenCount(text, modelId)

        // ✅ FIX: Nu trunca dacă nu e nevoie
        if (currentTokens <= maxTokens) {
            Timber.d("Text fits within $maxTokens tokens (has $currentTokens), no truncation needed")
            return text  // Return original text without truncation message
        }

        Timber.d("Truncating text from $currentTokens tokens to $maxTokens tokens")

        // Reserve tokens for truncation indicator ONLY when actually truncating
        val truncationIndicator = "\n\n[Message truncated to fit $maxTokens token limit]"
        val truncationTokens = estimateTokenCount(truncationIndicator, modelId)
        val targetTokens = maxTokens - truncationTokens

        if (targetTokens <= 0) {
            // Extremă situație - măcar să returneze ceva
            return "[Message too long for available token limit]"
        }

        var remainingTokens = targetTokens
        val paragraphs = text.split("\n\n")
        val resultParagraphs = mutableListOf<String>()

        for (paragraph in paragraphs) {
            val paragraphTokens = estimateTokenCount(paragraph, modelId)
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

        val finalTokens = estimateTokenCount(result, modelId)
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