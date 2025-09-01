package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.model.LimitType
import timber.log.Timber

/**
 * UPDATED Token Limit Dialog Handler
 * 
 * Purpose: Simplified interface for token limit dialogs using new context management system
 * - Uses ContextWindowManager for validation  
 * - Uses ContextWindowAlertManager for UI
 * - Uses ApiUsageTracker for display data
 * - Integrates with new 80% limit system
 */
object TokenLimitDialogHandler {

    /**
     * Validate and show appropriate dialog for a message
     * This is the main entry point for token validation
     */
    suspend fun validateAndShowDialog(
        context: Context,
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
        if (context !is Activity || context.isFinishing) {
            Timber.w("Cannot validate - activity not available")
            return
        }
        
        try {
            // Use ContextWindowManager for validation
            val contextManager = ContextWindowManager(context)
            val validationResult = contextManager.validateMessage(
                conversationId = conversationId,
                modelId = modelId,
                userPrompt = userPrompt,
                attachedFiles = attachedFiles,
                isSubscribed = isSubscribed
            )
            
            when (validationResult) {
                is ContextWindowManager.ValidationResult.Allowed -> {
                    Timber.d("✅ Message validated - proceeding")
                    onAllowed()
                }
                
                is ContextWindowManager.ValidationResult.Warning -> {
                    Timber.w("⚠️ Context window warning: ${validationResult.message}")
                    showContextWarning(
                        context = context,
                        usagePercentage = validationResult.usagePercentage,
                        estimatedTokens = validationResult.estimatedTokens,
                        modelId = modelId,
                        isSubscribed = isSubscribed,
                        attachedFiles = attachedFiles,
                        onContinue = onAllowed,
                        onNewChat = onNewConversationRequired,
                        onCancel = onCancel,
                        onUpgrade = onUpgrade
                    )
                }
                
                is ContextWindowManager.ValidationResult.Blocked -> {
                    Timber.e("❌ Context window blocked: ${validationResult.message}")
                    showContextBlocked(
                        context = context,
                        usagePercentage = validationResult.usagePercentage,
                        estimatedTokens = validationResult.estimatedTokens,
                        modelId = modelId,
                        isSubscribed = isSubscribed,
                        attachedFiles = attachedFiles,
                        onNewChat = onNewConversationRequired,
                        onUpgrade = onUpgrade
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error during token validation: ${e.message}")
            
            // Fallback: show generic error and allow user to decide
            showValidationError(context, e.message ?: "Unknown error", onAllowed, onCancel)
        }
    }

    /**
     * Update context with API response (call after receiving response)
     */
    fun updateContextWithApiResponse(
        context: Context,
        conversationId: String,
        modelId: String,
        apiResponse: AimlApiResponse
    ) {
        try {
            // Update context window manager
            val contextManager = ContextWindowManager(context)
            contextManager.updateWithApiUsage(conversationId, modelId, apiResponse)
            
            // Update usage tracker for display
            val usageTracker = ApiUsageTracker.getInstance()
            usageTracker.recordApiUsage(conversationId, modelId, apiResponse)
            
            Timber.d("📊 Context updated with API response")
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating context with API response: ${e.message}")
        }
    }
    
    /**
     * Get usage summary for UI display
     */
    fun getUsageSummary(context: Context, conversationId: String, modelId: String, isSubscribed: Boolean): String {
        return try {
            val contextManager = ContextWindowManager(context)
            contextManager.getUsageSummary(conversationId, modelId, isSubscribed)
        } catch (e: Exception) {
            Timber.e(e, "Error getting usage summary: ${e.message}")
            "Unable to get usage"
        }
    }
    
    /**
     * Check if conversation needs warning
     */
    fun checkConversationLimits(
        context: Context,
        conversationId: String,
        modelId: String,
        isSubscribed: Boolean
    ): Pair<Boolean, String?> {
        return try {
            val contextManager = ContextWindowManager(context)
            contextManager.checkContextLimits(conversationId, modelId, isSubscribed)
        } catch (e: Exception) {
            Timber.e(e, "Error checking conversation limits: ${e.message}")
            Pair(false, null)
        }
    }
    
    /**
     * Reset conversation context
     */
    fun resetConversation(context: Context, conversationId: String) {
        try {
            val contextManager = ContextWindowManager(context)
            contextManager.resetConversation(conversationId)
            
            val usageTracker = ApiUsageTracker.getInstance()
            usageTracker.resetConversationUsage(conversationId)
            
            Timber.d("🔄 Conversation context reset: $conversationId")
            
        } catch (e: Exception) {
            Timber.e(e, "Error resetting conversation: ${e.message}")
        }
    }
    
    // PRIVATE HELPER METHODS
    
    private fun showContextWarning(
        context: Context,
        usagePercentage: Float,
        estimatedTokens: Int,
        modelId: String,
        isSubscribed: Boolean,
        attachedFiles: List<Uri>,
        onContinue: () -> Unit,
        onNewChat: () -> Unit,
        onCancel: () -> Unit,
        onUpgrade: () -> Unit
    ) {
        val alertManager = ContextWindowAlertManager(context)
        val modelName = ModelManager.getModelDisplayName(modelId)
        
        val callbacks = object : ContextWindowAlertManager.AlertCallbacks {
            override fun onContinueAnyway() = onContinue()
            override fun onStartNewConversation() = onNewChat()
            override fun onCancel() = onCancel()
            override fun onUpgradeAccount() = onUpgrade()
        }
        
        alertManager.showContextWarningDialog(
            usagePercentage = usagePercentage,
            estimatedTokens = estimatedTokens,
            modelName = modelName,
            isSubscribed = isSubscribed,
            callbacks = callbacks,
            hasAttachedFiles = attachedFiles.isNotEmpty()
        )
    }
    
    private fun showContextBlocked(
        context: Context,
        usagePercentage: Float,
        estimatedTokens: Int,
        modelId: String,
        isSubscribed: Boolean,
        attachedFiles: List<Uri>,
        onNewChat: () -> Unit,
        onUpgrade: () -> Unit
    ) {
        val alertManager = ContextWindowAlertManager(context)
        val modelName = ModelManager.getModelDisplayName(modelId)
        
        val callbacks = object : ContextWindowAlertManager.AlertCallbacks {
            override fun onContinueAnyway() { /* Not allowed in blocked state */ }
            override fun onStartNewConversation() = onNewChat()
            override fun onCancel() { /* Not allowed in blocked state */ }
            override fun onUpgradeAccount() = onUpgrade()
        }
        
        alertManager.showContextBlockedDialog(
            usagePercentage = usagePercentage,
            estimatedTokens = estimatedTokens,
            modelName = modelName,
            isSubscribed = isSubscribed,
            callbacks = callbacks,
            hasAttachedFiles = attachedFiles.isNotEmpty()
        )
    }
    
    private fun showValidationError(
        context: Context,
        errorMessage: String,
        onProceed: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (context !is Activity || context.isFinishing) return
        
        android.app.AlertDialog.Builder(context)
            .setTitle("Validation Error")
            .setMessage("Unable to validate message: $errorMessage\n\nWould you like to proceed anyway?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Proceed") { _, _ -> onProceed() }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .show()
    }
    
    // NEW UNIFIED SYSTEM METHODS
    
    /**
     * Show insufficient credits dialog
     */
    fun showInsufficientCreditsDialog(
        context: Context,
        creditsNeeded: Int,
        onUpgrade: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (context !is Activity || context.isFinishing) return
        
        AlertDialog.Builder(context)
            .setTitle("Insufficient Credits")
            .setMessage("You need $creditsNeeded credits to use this model.\n\nOptions:\n• Watch ads to earn more credits\n• Upgrade to Premium for unlimited access")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Upgrade to Premium") { _, _ -> onUpgrade() }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show generic limit dialog
     */
    fun showLimitDialog(
        context: Context,
        message: String,
        limitType: LimitType,
        onUpgrade: (() -> Unit)? = null,
        onCancel: () -> Unit
    ) {
        if (context !is Activity || context.isFinishing) return
        
        val title = when (limitType) {
            LimitType.INPUT_TOKENS -> "Input Too Long"
            LimitType.OUTPUT_TOKENS -> "Output Limit"
            LimitType.DAILY_REQUESTS -> "Daily Limit Reached"
            LimitType.CREDITS -> "Insufficient Credits"
            LimitType.SUBSCRIPTION -> "Premium Required"
            LimitType.IMAGE_GENERATION -> "Image Limit Reached"
            else -> "Usage Limit"
        }
        
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setCancelable(false)
        
        if (onUpgrade != null) {
            builder.setPositiveButton("Upgrade") { _, _ -> onUpgrade() }
        }
        
        builder.show()
    }
    
    // LEGACY SUPPORT METHODS (for existing code compatibility)
    
    /**
     * Legacy method - now uses new system
     */
    @Deprecated("Use validateAndShowDialog instead")
    fun showTokenLimitApproachingDialog(
        context: Context,
        tokenPercentage: Float,
        onContinue: () -> Unit,
        onSummarize: () -> Unit,
        onNewChat: () -> Unit
    ) {
        showContextWarning(
            context = context,
            usagePercentage = tokenPercentage,
            estimatedTokens = 0,
            modelId = "gpt-4o-mini", // Default model
            isSubscribed = true,
            attachedFiles = emptyList(), // No files for legacy method
            onContinue = onContinue,
            onNewChat = onNewChat,
            onCancel = {},
            onUpgrade = {}
        )
    }
    
    /**
     * Legacy method - now uses new system
     */
    @Deprecated("Use validateAndShowDialog instead")
    fun showTokenLimitReachedDialog(
        context: Context,
        modelId: String,
        isSubscribed: Boolean,
        onSummarize: () -> Unit,
        onNewChat: () -> Unit,
        onUpgrade: () -> Unit
    ) {
        showContextBlocked(
            context = context,
            usagePercentage = 0.85f,
            estimatedTokens = 0,
            modelId = modelId,
            isSubscribed = isSubscribed,
            attachedFiles = emptyList(), // No files for legacy method
            onNewChat = onNewChat,
            onUpgrade = onUpgrade
        )
    }
}