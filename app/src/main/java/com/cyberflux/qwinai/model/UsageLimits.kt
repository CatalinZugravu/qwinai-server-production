package com.cyberflux.qwinai.model

/**
 * Data classes and enums for the new comprehensive usage limits system
 */

/**
 * Token limits for input and output
 */
data class TokenLimits(
    val maxInputTokens: Int,
    val maxOutputTokens: Int
) {
    companion object {
        // Standard limits for free users as requested
        val FREE_USER_LIMITS = TokenLimits(
            maxInputTokens = 1000,
            maxOutputTokens = 1500
        )
        
        // Unlimited token limits (use model's max capacity)
        fun unlimited(modelConfig: ModelConfig): TokenLimits {
            return TokenLimits(
                maxInputTokens = modelConfig.maxInputTokens,
                maxOutputTokens = modelConfig.maxOutputTokens
            )
        }
    }
}

/**
 * Usage categories for different types of models
 */
enum class UsageCategory {
    /**
     * Free unlimited access - no restrictions
     * Example: Basic educational models, demo models
     */
    FREE_UNLIMITED,
    
    /**
     * Uses the existing credit system for free users
     * Example: GPT-4, Claude, premium models that free users can access with credits
     */
    CREDIT_BASED,
    
    /**
     * Limited premium access - 30 requests per day for premium users
     * Most expensive/advanced models
     * Example: GPT-4o, Claude 3.7 Sonnet, Grok 3 Beta
     */
    LIMITED_PREMIUM,
    
    /**
     * Unlimited premium access - no daily limits for premium users
     * Stable, widely used premium models
     * Example: GPT-4 Turbo, DeepSeek R1, Gemma 3
     */
    UNLIMITED_PREMIUM,
    
    /**
     * Image generation models
     * Free users: credit-based, Premium users: 25 generations per day
     */
    IMAGE_GENERATION
}

/**
 * Validation result for usage limit checks
 */
data class UsageValidationResult(
    val isAllowed: Boolean,
    val reason: String = "",
    val limitType: LimitType = LimitType.NONE,
    val remainingCount: Int = 0,
    val upgradeRequired: Boolean = false,
    val creditsRequired: Int = 0
) {
    companion object {
        fun allowed() = UsageValidationResult(isAllowed = true)
        
        fun denied(reason: String, limitType: LimitType, remainingCount: Int = 0, upgradeRequired: Boolean = false) = 
            UsageValidationResult(
                isAllowed = false, 
                reason = reason, 
                limitType = limitType, 
                remainingCount = remainingCount,
                upgradeRequired = upgradeRequired
            )
        
        fun needsCredits(creditsRequired: Int) = 
            UsageValidationResult(
                isAllowed = false,
                reason = "Insufficient credits. Need $creditsRequired credits.",
                limitType = LimitType.CREDITS,
                creditsRequired = creditsRequired
            )
        
        fun needsUpgrade(reason: String) = 
            UsageValidationResult(
                isAllowed = false,
                reason = reason,
                limitType = LimitType.SUBSCRIPTION,
                upgradeRequired = true
            )
    }
}

/**
 * Types of limits that can be hit
 */
enum class LimitType {
    NONE,
    INPUT_TOKENS,
    OUTPUT_TOKENS,
    DAILY_REQUESTS,
    CREDITS,
    SUBSCRIPTION,
    IMAGE_GENERATION
}

/**
 * Daily usage data for tracking requests
 */
data class DailyUsageData(
    val modelId: String,
    val requestCount: Int,
    val totalInputTokens: Int,
    val totalOutputTokens: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val date: String = getCurrentDateString()
) {
    companion object {
        fun getCurrentDateString(): String {
            val cal = java.util.Calendar.getInstance()
            return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
        }
    }
}

/**
 * Usage summary for UI display
 */
data class UsageSummary(
    val modelId: String,
    val usageCategory: UsageCategory,
    val tokenLimits: TokenLimits,
    val dailyRequestLimit: Int, // -1 for unlimited
    val remainingRequests: Int, // -1 for unlimited
    val creditsRequired: Int, // 0 if no credits needed
    val availableCredits: Int, // current credits available
    val isSubscriptionRequired: Boolean
)