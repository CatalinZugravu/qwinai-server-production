package com.cyberflux.qwinai.utils

/**
 * Result classes for message validation in the production token system
 * Used to provide clear validation outcomes for UI handling
 */
sealed class MessageValidationResult {
    
    /**
     * Message validation successful - can proceed with sending
     */
    data class Success(
        val calculation: ContextCalculationResult
    ) : MessageValidationResult()
    
    /**
     * Message validation warning - show warning but allow proceeding
     */
    data class Warning(
        val message: String,
        val calculation: ContextCalculationResult
    ) : MessageValidationResult()
    
    /**
     * Message validation error - cannot proceed with sending
     */
    data class Error(
        val message: String
    ) : MessageValidationResult()
    
    companion object {
        /**
         * Create a default empty calculation result for fallback scenarios
         */
        fun createEmptyCalculation(): ContextCalculationResult {
            return ContextCalculationResult(
                usage = ContextWindowUsage(
                    inputTokens = 0,
                    systemTokens = 500,
                    conversationTokens = 0,
                    totalTokens = 500
                ),
                usagePercentage = 0.0f,
                modelLimits = ModelLimits(
                    maxInputTokens = 16000,
                    maxOutputTokens = 4000
                )
            )
        }
    }
}

/**
 * Context calculation result for comprehensive token management
 */
data class ContextCalculationResult(
    val usage: ContextWindowUsage,
    val usagePercentage: Float,
    val modelLimits: ModelLimits
)

/**
 * Detailed context window usage breakdown
 */
data class ContextWindowUsage(
    val inputTokens: Int,
    val systemTokens: Int,
    val conversationTokens: Int,
    val totalTokens: Int
)

/**
 * Model token limits
 */
data class ModelLimits(
    val maxInputTokens: Int,
    val maxOutputTokens: Int
)