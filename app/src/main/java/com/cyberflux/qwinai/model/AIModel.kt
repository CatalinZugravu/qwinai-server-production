package com.cyberflux.qwinai.model

/**
 * Data class representing an AI model configuration
 *
 * @property id The unique identifier for the model
 * @property displayName The human-readable name to display in the UI
 * @property maxTokens The maximum number of tokens the model can generate
 * @property temperature The temperature parameter controlling randomness (0.0-1.0)
 * @property apiName The name of the API service to use ("aimlapi")
 * @property isFree Whether this model is available without spending credits
 */
data class AIModel(
    val id: String,
    val displayName: String,
    val maxTokens: Int,
    val temperature: Double,
    val apiName: String,
    val isFree: Boolean,
    val isImageGenerator: Boolean = false, // Add this parameter with a default value
    val maxInputTokens: Int = maxTokens / 2, // Default to half of context window if not specified
    val isOcrModel: Boolean = false,           // Added for OCR models
    val isImageToImage: Boolean = false, // New property
)

data class ConversationGroup(
    val title: String,
    val conversations: List<Conversation>
)