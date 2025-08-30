package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

/**
 * Unified Usage Manager - Central hub for all usage limits
 * 
 * Handles:
 * - Token limits (1000 input / 1500 output for free users)  
 * - Daily request limits (30/day for premium limited models)
 * - Credit system integration (existing system)
 * - Image generation limits (25/day for premium users)
 * - Subscription-based access control
 */
class UnifiedUsageManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "unified_usage_limits"
        
        // Daily request limits
        const val DAILY_REQUEST_LIMIT_LIMITED_PREMIUM = 30
        const val DAILY_IMAGE_GENERATION_LIMIT_PREMIUM = 25
        
        @Volatile
        private var INSTANCE: UnifiedUsageManager? = null
        
        fun getInstance(context: Context): UnifiedUsageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedUsageManager(context.applicationContext).also { 
                    INSTANCE = it
                }
            }
        }
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private val creditManager by lazy { CreditManager.getInstance(context) }
    private val prefsManager by lazy { PrefsManager }
    
    /**
     * Main validation function - checks all applicable limits
     */
    suspend fun validateRequest(
        modelId: String,
        inputPrompt: String,
        isSubscribed: Boolean
    ): UsageValidationResult = withContext(Dispatchers.IO) {
        
        try {
            val modelConfig = ModelConfigManager.getConfig(modelId)
            if (modelConfig == null) {
                return@withContext UsageValidationResult.denied(
                    "Unknown model: $modelId",
                    LimitType.NONE
                )
            }
            
            val usageCategory = getModelUsageCategory(modelId)
            val inputTokens = TokenValidator.estimateTokenCount(inputPrompt, modelId)
            
            Timber.d("🔍 Validating request - Model: $modelId, Category: $usageCategory, Input Tokens: $inputTokens, Subscribed: $isSubscribed")
            
            // 1. Check token limits first
            val tokenValidation = validateTokenLimits(modelId, inputTokens, isSubscribed)
            if (!tokenValidation.isAllowed) {
                return@withContext tokenValidation
            }
            
            // 2. Check usage category specific limits
            when (usageCategory) {
                UsageCategory.FREE_UNLIMITED -> {
                    // No additional restrictions
                    return@withContext UsageValidationResult.allowed()
                }
                
                UsageCategory.CREDIT_BASED -> {
                    return@withContext validateCreditBasedAccess(modelId, isSubscribed)
                }
                
                UsageCategory.LIMITED_PREMIUM -> {
                    return@withContext validateLimitedPremiumAccess(modelId, isSubscribed)
                }
                
                UsageCategory.UNLIMITED_PREMIUM -> {
                    if (!isSubscribed) {
                        // Free users need credits for premium models
                        return@withContext validateCreditBasedAccess(modelId, isSubscribed)
                    }
                    // Premium users have unlimited access
                    return@withContext UsageValidationResult.allowed()
                }
                
                UsageCategory.IMAGE_GENERATION -> {
                    return@withContext validateImageGenerationAccess(isSubscribed)
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error validating request")
            return@withContext UsageValidationResult.denied(
                "Validation error: ${e.message}",
                LimitType.NONE
            )
        }
    }
    
    /**
     * Validate token limits based on user type and model
     */
    private fun validateTokenLimits(
        modelId: String, 
        inputTokens: Int, 
        isSubscribed: Boolean
    ): UsageValidationResult {
        
        val tokenLimits = getTokenLimitsForUser(modelId, isSubscribed)
        
        // Check input token limit
        if (inputTokens > tokenLimits.maxInputTokens) {
            return UsageValidationResult.denied(
                "Input too long. Limit: ${TokenValidator.formatTokenCount(tokenLimits.maxInputTokens)}, " +
                "Your input: ${TokenValidator.formatTokenCount(inputTokens)}",
                LimitType.INPUT_TOKENS,
                remainingCount = tokenLimits.maxInputTokens,
                upgradeRequired = !isSubscribed
            )
        }
        
        return UsageValidationResult.allowed()
    }
    
    /**
     * Validate credit-based access for free users
     */
    private fun validateCreditBasedAccess(modelId: String, isSubscribed: Boolean): UsageValidationResult {
        if (isSubscribed) {
            // Premium users don't need credits
            return UsageValidationResult.allowed()
        }
        
        // Free users need credits
        val creditsNeeded = getCreditCostForModel(modelId)
        val availableCredits = creditManager.getChatCredits()
        
        if (availableCredits < creditsNeeded) {
            return UsageValidationResult.needsCredits(creditsNeeded)
        }
        
        return UsageValidationResult.allowed()
    }
    
    /**
     * Validate limited premium access (30 requests per day)
     */
    private fun validateLimitedPremiumAccess(modelId: String, isSubscribed: Boolean): UsageValidationResult {
        if (!isSubscribed) {
            // Free users can use credits for premium models
            return validateCreditBasedAccess(modelId, isSubscribed)
        }
        
        // Premium users have daily limits
        val usageToday = getDailyUsage(modelId)
        val remaining = DAILY_REQUEST_LIMIT_LIMITED_PREMIUM - usageToday
        
        if (remaining <= 0) {
            return UsageValidationResult.denied(
                "Daily limit reached. Limit: $DAILY_REQUEST_LIMIT_LIMITED_PREMIUM requests per day",
                LimitType.DAILY_REQUESTS,
                remainingCount = 0
            )
        }
        
        return UsageValidationResult.allowed()
    }
    
    /**
     * Validate image generation access
     */
    private fun validateImageGenerationAccess(isSubscribed: Boolean): UsageValidationResult {
        if (!isSubscribed) {
            // Free users use credit system
            val imageCredits = creditManager.getImageCredits()
            val creditsNeeded = 1 // 1 credit per image
            
            if (imageCredits < creditsNeeded) {
                return UsageValidationResult.needsCredits(creditsNeeded)
            }
        } else {
            // Premium users have daily limits
            val imagesGenerated = getDailyImageGenerations()
            val remaining = DAILY_IMAGE_GENERATION_LIMIT_PREMIUM - imagesGenerated
            
            if (remaining <= 0) {
                return UsageValidationResult.denied(
                    "Daily image generation limit reached. Limit: $DAILY_IMAGE_GENERATION_LIMIT_PREMIUM per day",
                    LimitType.IMAGE_GENERATION,
                    remainingCount = 0
                )
            }
        }
        
        return UsageValidationResult.allowed()
    }
    
    /**
     * Record successful request for tracking
     */
    suspend fun recordRequest(
        modelId: String, 
        inputTokens: Int, 
        outputTokens: Int, 
        isSubscribed: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val usageCategory = getModelUsageCategory(modelId)
            
            // Update daily usage counter
            if (usageCategory == UsageCategory.LIMITED_PREMIUM && isSubscribed) {
                incrementDailyUsage(modelId)
            }
            
            // Consume credits if needed
            if (!isSubscribed && (usageCategory == UsageCategory.CREDIT_BASED || 
                                 usageCategory == UsageCategory.UNLIMITED_PREMIUM)) {
                val creditsNeeded = getCreditCostForModel(modelId)
                creditManager.consumeChatCredits(creditsNeeded)
            }
            
            Timber.d("📊 Recorded request - Model: $modelId, Input: $inputTokens, Output: $outputTokens")
        } catch (e: Exception) {
            Timber.e(e, "Error recording request usage")
        }
    }
    
    /**
     * Record image generation
     */
    suspend fun recordImageGeneration(isSubscribed: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (!isSubscribed) {
                creditManager.consumeImageCredits(1)
            } else {
                incrementDailyImageGenerations()
            }
            
            Timber.d("📊 Recorded image generation - Subscribed: $isSubscribed")
        } catch (e: Exception) {
            Timber.e(e, "Error recording image generation")
        }
    }
    
    /**
     * Get token limits based on user type
     */
    fun getTokenLimitsForUser(modelId: String, isSubscribed: Boolean): TokenLimits {
        val modelConfig = ModelConfigManager.getConfig(modelId)
            ?: return TokenLimits.FREE_USER_LIMITS
        
        return if (isSubscribed) {
            // Premium users get full model capacity
            TokenLimits(
                maxInputTokens = modelConfig.maxInputTokens,
                maxOutputTokens = modelConfig.maxOutputTokens
            )
        } else {
            // Free users get limited tokens as requested: 1000 input, 1500 output
            TokenLimits.FREE_USER_LIMITS
        }
    }
    
    /**
     * Get usage category for a model
     */
    fun getModelUsageCategory(modelId: String): UsageCategory {
        return when (modelId) {
            // Free unlimited models
            "cohere/command-r-plus" -> UsageCategory.FREE_UNLIMITED
            
            // Limited premium models (30 requests/day for premium users)
            "gpt-4o",
            "claude-3-7-sonnet-20250219",
            "x-ai/grok-3-beta",
            "Qwen/Qwen3-235B-A22B-fp8-tput" -> UsageCategory.LIMITED_PREMIUM
            
            // Unlimited premium models  
            "gpt-4-turbo",
            "deepseek/deepseek-r1",
            "google/gemma-3-27b-it",
            "mistral/mistral-ocr-latest",
            "zhipu/glm-4.5" -> UsageCategory.UNLIMITED_PREMIUM
            
            // Image generation models
            "dall-e-3",
            "stable-diffusion-v35-large",
            "flux/schnell",
            "flux-realism",
            "recraft-v3" -> UsageCategory.IMAGE_GENERATION
            
            // Default: credit-based for unknown models
            else -> UsageCategory.CREDIT_BASED
        }
    }
    
    /**
     * Get credit cost for a model (for free users)
     */
    private fun getCreditCostForModel(modelId: String): Int {
        return when (getModelUsageCategory(modelId)) {
            UsageCategory.LIMITED_PREMIUM -> 3 // Expensive models cost more
            UsageCategory.UNLIMITED_PREMIUM -> 2 // Standard premium cost
            UsageCategory.CREDIT_BASED -> 1 // Default cost
            else -> 0 // Free models
        }
    }
    
    /**
     * Get daily usage count for a model
     */
    private fun getDailyUsage(modelId: String): Int {
        val key = "daily_usage_${modelId}_${getCurrentDateString()}"
        return encryptedPrefs.getInt(key, 0)
    }
    
    /**
     * Increment daily usage for a model
     */
    private fun incrementDailyUsage(modelId: String) {
        val key = "daily_usage_${modelId}_${getCurrentDateString()}"
        val current = encryptedPrefs.getInt(key, 0)
        encryptedPrefs.edit().putInt(key, current + 1).apply()
    }
    
    /**
     * Get daily image generations count
     */
    private fun getDailyImageGenerations(): Int {
        val key = "daily_images_${getCurrentDateString()}"
        return encryptedPrefs.getInt(key, 0)
    }
    
    /**
     * Increment daily image generations
     */
    private fun incrementDailyImageGenerations() {
        val key = "daily_images_${getCurrentDateString()}"
        val current = encryptedPrefs.getInt(key, 0)
        encryptedPrefs.edit().putInt(key, current + 1).apply()
    }
    
    /**
     * Get usage summary for UI display
     */
    fun getUsageSummary(modelId: String, isSubscribed: Boolean): UsageSummary {
        val usageCategory = getModelUsageCategory(modelId)
        val tokenLimits = getTokenLimitsForUser(modelId, isSubscribed)
        
        val (dailyRequestLimit, remainingRequests) = when {
            usageCategory == UsageCategory.LIMITED_PREMIUM && isSubscribed -> {
                val used = getDailyUsage(modelId)
                Pair(DAILY_REQUEST_LIMIT_LIMITED_PREMIUM, DAILY_REQUEST_LIMIT_LIMITED_PREMIUM - used)
            }
            usageCategory == UsageCategory.IMAGE_GENERATION && isSubscribed -> {
                val used = getDailyImageGenerations()
                Pair(DAILY_IMAGE_GENERATION_LIMIT_PREMIUM, DAILY_IMAGE_GENERATION_LIMIT_PREMIUM - used)
            }
            else -> Pair(-1, -1) // Unlimited
        }
        
        val creditsRequired = if (!isSubscribed) getCreditCostForModel(modelId) else 0
        val availableCredits = if (!isSubscribed) creditManager.getChatCredits() else 0
        val isSubscriptionRequired = (usageCategory == UsageCategory.LIMITED_PREMIUM || 
                                    usageCategory == UsageCategory.UNLIMITED_PREMIUM) && !isSubscribed
        
        return UsageSummary(
            modelId = modelId,
            usageCategory = usageCategory,
            tokenLimits = tokenLimits,
            dailyRequestLimit = dailyRequestLimit,
            remainingRequests = remainingRequests,
            creditsRequired = creditsRequired,
            availableCredits = availableCredits,
            isSubscriptionRequired = isSubscriptionRequired
        )
    }
    
    /**
     * Reset daily usage (called by background worker)
     */
    fun resetDailyUsage() {
        val editor = encryptedPrefs.edit()
        val currentDate = getCurrentDateString()
        
        // Clean up old usage data (keep only current day)
        val allKeys = encryptedPrefs.all.keys
        allKeys.forEach { key ->
            if (key.startsWith("daily_usage_") || key.startsWith("daily_images_")) {
                if (!key.endsWith(currentDate)) {
                    editor.remove(key)
                }
            }
        }
        editor.apply()
        
        Timber.d("🔄 Daily usage data cleaned up for date: $currentDate")
    }
    
    /**
     * Get current date string for daily tracking
     */
    private fun getCurrentDateString(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }
    
    /**
     * Debug function - get all usage data
     */
    fun getDebugInfo(): String {
        val isSubscribed = prefsManager.isSubscribed(context)
        return """
            🔒 UNIFIED USAGE MANAGER DEBUG
            Subscription: $isSubscribed
            Chat Credits: ${creditManager.getChatCredits()}
            Image Credits: ${creditManager.getImageCredits()}
            Daily Requests (GPT-4o): ${getDailyUsage("gpt-4o")}/${DAILY_REQUEST_LIMIT_LIMITED_PREMIUM}
            Daily Images: ${getDailyImageGenerations()}/${DAILY_IMAGE_GENERATION_LIMIT_PREMIUM}
            Date: ${getCurrentDateString()}
        """.trimIndent()
    }
}