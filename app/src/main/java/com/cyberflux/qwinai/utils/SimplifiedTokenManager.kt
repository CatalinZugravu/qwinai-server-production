package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.cyberflux.qwinai.network.AimlApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * SIMPLIFIED Token Manager - One-Stop Shop
 * 
 * Purpose: Single entry point for all token management operations
 * - Pre-request validation  
 * - Post-response tracking
 * - UI display helpers
 * - Context management
 * 
 * This is the ONLY class MainActivity needs to interact with!
 */
class SimplifiedTokenManager(private val context: Context) {
    
    private val contextWindowManager by lazy { ContextWindowManager(context) }
    private val apiUsageTracker by lazy { ApiUsageTracker.getInstance() }
    private val fileTokenEstimator by lazy { FileTokenEstimator(context) }
    private val unifiedUsageManager by lazy { UnifiedUsageManager.getInstance(context) }
    
    /**
     * STEP 1: Validate message before sending - UPDATED to use UnifiedUsageManager
     * Call this in your sendMessage() method
     */
    suspend fun validateMessage(
        conversationId: String,
        modelId: String,
        userPrompt: String,
        attachedFiles: List<Uri> = emptyList(),
        isSubscribed: Boolean,
        onAllowed: () -> Unit,
        onNewConversationRequired: () -> Unit,
        onCancel: () -> Unit,
        onUpgrade: () -> Unit = {}
    ) {
        // Use the new unified validation system
        val validationResult = unifiedUsageManager.validateRequest(modelId, userPrompt, isSubscribed)
        
        if (validationResult.isAllowed) {
            onAllowed()
        } else {
            // Handle different types of failures
            when {
                validationResult.upgradeRequired -> onUpgrade()
                validationResult.creditsRequired > 0 -> {
                    // Show credit insufficiency dialog
                    TokenLimitDialogHandler.showInsufficientCreditsDialog(
                        context = context,
                        creditsNeeded = validationResult.creditsRequired,
                        onUpgrade = onUpgrade,
                        onCancel = onCancel
                    )
                }
                else -> {
                    // Show generic limit dialog
                    TokenLimitDialogHandler.showLimitDialog(
                        context = context,
                        message = validationResult.reason,
                        limitType = validationResult.limitType,
                        onUpgrade = if (validationResult.upgradeRequired) onUpgrade else null,
                        onCancel = onCancel
                    )
                }
            }
        }
    }
    
    /**
     * STEP 2: Update with API response after receiving - UPDATED to use UnifiedUsageManager
     * Call this when you get an API response
     */
    suspend fun recordApiResponse(
        conversationId: String,
        modelId: String,
        apiResponse: AimlApiResponse,
        isSubscribed: Boolean
    ) {
        // Extract token usage from API response
        val inputTokens = apiResponse.usage?.promptTokens ?: 0
        val outputTokens = apiResponse.usage?.completionTokens ?: 0
        
        // Record with unified system
        unifiedUsageManager.recordRequest(
            modelId = modelId,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            isSubscribed = isSubscribed
        )
        
        // Also update the old system for compatibility
        TokenLimitDialogHandler.updateContextWithApiResponse(
            context = context,
            conversationId = conversationId,
            modelId = modelId,
            apiResponse = apiResponse
        )
        
        Timber.d("📊 Recorded API response for $conversationId - Input: $inputTokens, Output: $outputTokens")
    }
    
    /**
     * Get usage summary for UI display - UPDATED to use UnifiedUsageManager
     * Use this for your token counter
     */
    fun getUsageSummary(
        conversationId: String,
        modelId: String,
        isSubscribed: Boolean
    ): String {
        val usageSummary = unifiedUsageManager.getUsageSummary(modelId, isSubscribed)
        
        return buildString {
            // Token limits
            append("Tokens: ${TokenValidator.formatTokenCount(usageSummary.tokenLimits.maxInputTokens)} in")
            append(" / ${TokenValidator.formatTokenCount(usageSummary.tokenLimits.maxOutputTokens)} out")
            
            // Daily request limits for premium users
            if (usageSummary.dailyRequestLimit > 0) {
                append(" | Requests: ${usageSummary.remainingRequests}/${usageSummary.dailyRequestLimit}")
            }
            
            // Credits for free users
            if (usageSummary.creditsRequired > 0) {
                append(" | Credits: ${usageSummary.availableCredits}")
                if (usageSummary.creditsRequired > 1) {
                    append(" (${usageSummary.creditsRequired} needed)")
                }
            }
            
            // Category indicator
            val categoryIndicator = when (usageSummary.usageCategory) {
                com.cyberflux.qwinai.model.UsageCategory.FREE_UNLIMITED -> "🆓"
                com.cyberflux.qwinai.model.UsageCategory.CREDIT_BASED -> "💳"
                com.cyberflux.qwinai.model.UsageCategory.LIMITED_PREMIUM -> "⭐"
                com.cyberflux.qwinai.model.UsageCategory.UNLIMITED_PREMIUM -> "🔓"
                com.cyberflux.qwinai.model.UsageCategory.IMAGE_GENERATION -> "🎨"
            }
            append(" $categoryIndicator")
        }
    }
    
    /**
     * Check if conversation needs a warning indicator
     * Use this to color your token counter
     */
    fun checkNeedsWarning(
        conversationId: String,
        modelId: String,
        isSubscribed: Boolean
    ): Pair<Boolean, String?> {
        return TokenLimitDialogHandler.checkConversationLimits(
            context = context,
            conversationId = conversationId,
            modelId = modelId,
            isSubscribed = isSubscribed
        )
    }
    
    /**
     * Reset conversation context
     * Call this when starting a new conversation
     */
    fun resetConversation(conversationId: String) {
        TokenLimitDialogHandler.resetConversation(context, conversationId)
    }
    
    /**
     * Estimate tokens for files (optional - used internally by validation)
     * Use this if you want to show file token estimates in UI
     */
    suspend fun estimateFileTokens(files: List<Pair<Uri, String?>>): List<FileTokenEstimator.FileTokenResult> = withContext(Dispatchers.IO) {
        return@withContext fileTokenEstimator.estimateMultipleFiles(files)
    }
    
    /**
     * Get total estimated tokens for files
     */
    suspend fun getTotalFileTokens(files: List<Pair<Uri, String?>>): Int = withContext(Dispatchers.IO) {
        return@withContext fileTokenEstimator.getTotalEstimatedTokens(files)
    }
    
    /**
     * Get conversation usage details (for advanced UI)
     */
    fun getConversationUsageDetails(conversationId: String): ApiUsageTracker.ConversationUsage? {
        return apiUsageTracker.getConversationUsage(conversationId)
    }
    
    /**
     * Get recent API calls for debugging
     */
    fun getRecentApiCalls(count: Int = 10): List<ApiUsageTracker.ApiUsageData> {
        return apiUsageTracker.getRecentApiCalls(count)
    }
    
    /**
     * Get usage by provider (for analytics)
     */
    fun getUsageByProvider(): Map<String, Int> {
        return apiUsageTracker.getUsageByProvider()
    }
    
    /**
     * Export usage report for debugging
     */
    fun exportUsageReport(): String {
        return apiUsageTracker.exportUsageData()
    }
    
    /**
     * Show context explanation dialog
     * Use this for help/info buttons
     */
    fun showContextExplanation() {
        val alertManager = ContextWindowAlertManager(context)
        alertManager.showContextExplanationDialog()
    }
    
    /**
     * Cleanup old data (call periodically)
     */
    fun cleanupOldData() {
        contextWindowManager.cleanupOldContexts()
        apiUsageTracker.cleanup()
        Timber.d("🧹 Cleaned up old token data")
    }
    
    companion object {
        
        /**
         * Quick validation without showing dialogs
         * Use this for lightweight checks
         */
        suspend fun quickValidate(
            context: Context,
            conversationId: String,
            modelId: String,
            userPrompt: String,
            attachedFiles: List<Uri> = emptyList(),
            isSubscribed: Boolean
        ): ContextWindowManager.ValidationResult {
            val contextManager = ContextWindowManager(context)
            return contextManager.validateMessage(
                conversationId = conversationId,
                modelId = modelId,
                userPrompt = userPrompt,
                attachedFiles = attachedFiles,
                isSubscribed = isSubscribed
            )
        }
        
        /**
         * Get formatted token display string
         */
        fun formatTokenCount(tokens: Int): String {
            return TokenValidator.formatTokenCount(tokens)
        }
        
        /**
         * Get model token limits
         */
        fun getModelLimits(modelId: String, isSubscribed: Boolean): Pair<Int, Int> {
            return TokenValidator.getModelTokenLimits(modelId, isSubscribed)
        }
    }
}