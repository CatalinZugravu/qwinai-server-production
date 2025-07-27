package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.model.ChatMessage

/**
 * Handles conversation summarization for token management
 */
object ConversationSummarizer {

    /**
     * Creates a summarization prompt for the AI
     */
    fun createSummarizationPrompt(messages: List<ChatMessage>): String {
        // Extract conversation content
        val conversationText = messages.joinToString("\n\n") { message ->
            val role = if (message.isUser) "User" else "AI"
            "$role: ${message.message}"
        }

        // Create the prompt
        return """
        |I need to continue our conversation in a new chat due to token limitations.
        |Please summarize our conversation so far, focusing on:
        |
        |1. Key points discussed
        |2. Any decisions or conclusions reached
        |3. Questions that are still pending
        |4. The most recent context so we can continue smoothly
        |
        |Here's the conversation to summarize:
        |
        |$conversationText
        """.trimMargin()
    }

    /**
     * Creates a continuation message based on the summary
     */
    fun createContinuationMessage(summary: String): String {
        return """
        |This is a continuation from a previous conversation. Here's a summary of what we discussed:
        |
        |$summary
        |
        |Let's continue from where we left off.
        """.trimMargin()
    }

    /**
     * Gets the most appropriate model for summarization
     */
    fun getBestModelForSummarization(tokensNeeded: Int, isSubscribed: Boolean): String {
        // Find models that can handle this many tokens with 20% reserved for output
        val viableModels = ModelManager.models.filter { model ->
            val available = (TokenValidator.getEffectiveMaxInputTokens(model.id, isSubscribed) * 0.8).toInt()
            available >= tokensNeeded
        }

        // Return the first viable model or the one with highest capacity
        return viableModels.maxByOrNull { it.maxTokens }?.id ?: ModelManager.DEFAULT_MODEL_ID
    }
}