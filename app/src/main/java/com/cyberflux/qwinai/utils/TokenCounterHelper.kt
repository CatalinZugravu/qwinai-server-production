package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import timber.log.Timber

/**
 * Enhanced TokenCounterHelper with conversation awareness and persistence support
 */
object TokenCounterHelper {

    /**
     * Update token counter UI with current token usage including secure token management
     * Enhanced to work with SecureTokenManager when available
     */
    fun updateTokenCounter(
        textView: TextView,
        text: String,
        modelId: String,
        isSubscribed: Boolean,
        context: Context,
        conversationTokenManager: ConversationTokenManager? = null
    ) {
        try {
            val inputTokens = TokenValidator.estimateTokenCount(text)
            
            if (conversationTokenManager != null) {
                // Try to get secure token usage info first
                val secureTokenUsage = conversationTokenManager.getSecureTokenUsageInfo()
                
                if (secureTokenUsage != null) {
                    // Use secure token manager data
                    updateWithSecureTokenInfo(textView, text, modelId, isSubscribed, context, secureTokenUsage, conversationTokenManager)
                } else {
                    // Fall back to legacy token manager
                    updateWithLegacyTokenInfo(textView, text, modelId, isSubscribed, context, conversationTokenManager)
                }
            } else {
                // Fallback to simple display without conversation context
                updateWithSimpleTokenInfo(textView, text, modelId, isSubscribed, context)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error updating token counter: ${e.message}")
            textView.text = "Error"
            textView.setTextColor(ContextCompat.getColor(context, R.color.error_color))
        }
    }

    /**
     * Update UI with secure token manager information
     */
    private fun updateWithSecureTokenInfo(
        textView: TextView,
        text: String,
        modelId: String,
        isSubscribed: Boolean,
        context: Context,
        secureTokenUsage: com.cyberflux.qwinai.tokens.SecureTokenManager.TokenUsageInfo,
        conversationTokenManager: ConversationTokenManager
    ) {
        val inputTokens = TokenValidator.estimateTokenCount(text)
        
        // Use secure token manager data
        val conversationTokens = secureTokenUsage.conversationTokens
        val systemTokens = secureTokenUsage.systemTokens
        val maxInputTokens = secureTokenUsage.maxInputTokens
        val usagePercentage = secureTokenUsage.usagePercentage
        val continuedPastWarning = secureTokenUsage.continuedPastWarning
        
        // Calculate available tokens (reserve space for AI response)
        val reservedPercentage = if (isComplexQuery(text)) 0.35 else 0.25
        val reservedTokens = (maxInputTokens * reservedPercentage).toInt()
        val currentUsage = conversationTokens + systemTokens
        val availableTokens = maxInputTokens - currentUsage - reservedTokens
        
        // Calculate total with input
        val totalWithInput = currentUsage + inputTokens
        
        // Enhanced display format with secure token data
        val displayText = "${inputTokens}↓ | ${conversationTokens}∑ | ${maxOf(0, availableTokens)}↑ | ${(usagePercentage * 100).toInt()}%"
        val spannableText = SpannableString(displayText)
        
        // Calculate percentages for color coding
        val totalPercentage = totalWithInput.toFloat() / maxInputTokens.toFloat()
        val conversationPercentage = currentUsage.toFloat() / maxInputTokens.toFloat()
        
        // Enhanced color logic using secure token thresholds
        val color = when {
            // Critical: Total with input over 90%
            totalPercentage > 0.90f ->
                ContextCompat.getColor(context, R.color.error_color)

            // Critical: Conversation already over 90%
            conversationPercentage > 0.90f ->
                ContextCompat.getColor(context, R.color.error_color)

            // Warning: Total with input over 80%
            totalPercentage > 0.80f ->
                ContextCompat.getColor(context, R.color.warning_color)

            // Warning: Conversation over 80%
            conversationPercentage > 0.80f ->
                ContextCompat.getColor(context, R.color.warning_color)

            // Input would exceed available space
            availableTokens <= 0 ->
                ContextCompat.getColor(context, R.color.error_color)

            // User has chosen to continue past warning
            continuedPastWarning ->
                ContextCompat.getColor(context, R.color.warning_color)

            else ->
                ContextCompat.getColor(context, R.color.text_secondary)
        }

        spannableText.setSpan(
            ForegroundColorSpan(color),
            0,
            displayText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textView.text = spannableText

        // Show warning dialog if needed
        if (context is MainActivity && totalPercentage > 0.80f && !continuedPastWarning) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                context.triggerTokenLimitDialog(totalPercentage, modelId, isSubscribed)
            }, 1000)
        }

        // Enhanced animation for critical state
        if (totalPercentage > 0.90f || availableTokens <= 0) {
            val animation = android.view.animation.AlphaAnimation(1.0f, 0.3f).apply {
                duration = 750
                repeatCount = 5
                repeatMode = android.view.animation.Animation.REVERSE
            }
            textView.startAnimation(animation)
        }

        // Debug logging for critical states
        if (totalPercentage > 0.7f || availableTokens <= 0) {
            Timber.d("Secure token counter: ${(totalPercentage * 100).toInt()}% usage, " +
                    "conversation: $conversationTokens, input: $inputTokens, " +
                    "available: $availableTokens, max: $maxInputTokens")
        }
    }

    /**
     * Update UI with legacy token manager information
     */
    private fun updateWithLegacyTokenInfo(
        textView: TextView,
        text: String,
        modelId: String,
        isSubscribed: Boolean,
        context: Context,
        conversationTokenManager: ConversationTokenManager
    ) {
        val inputTokens = TokenValidator.estimateTokenCount(text)
        val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
        
        // Enhanced display with conversation context
        val systemTokens = 500 // Consistent with ConversationTokenManager
        val conversationTokens = conversationTokenManager.getConversationTokens()
        val totalUsedTokens = conversationTokens + systemTokens + inputTokens

        // Calculate available tokens using the same logic as token manager
        val reservedPercentage = conversationTokenManager.getReservedPercentage(text)
        val reservedTokens = (maxTokens * reservedPercentage).toInt()
        val availableTokens = maxTokens - systemTokens - conversationTokens - reservedTokens

        // Display non-negative numbers only
        val displayAvailable = maxOf(0, availableTokens)
        val displayTotal = maxOf(0, totalUsedTokens)

        // Enhanced display format with conversation awareness
        val displayText = "${inputTokens}↓ | ${conversationTokens}∑ | ${displayAvailable}↑"
        val spannableText = SpannableString(displayText)

        // Calculate usage percentages for color coding
        val totalPercentage = totalUsedTokens.toFloat() / maxTokens.toFloat()
        val conversationPercentage = (conversationTokens + systemTokens).toFloat() / maxTokens.toFloat()

        // Enhanced color logic based on conversation state
        val color = when {
            // Critical: Conversation already over 95%
            conversationPercentage > ConversationTokenManager.CRITICAL_THRESHOLD ->
                ContextCompat.getColor(context, R.color.error_color)

            // Critical: Total with input over 95%
            totalPercentage > ConversationTokenManager.CRITICAL_THRESHOLD ->
                ContextCompat.getColor(context, R.color.error_color)

            // Warning: Conversation over warning threshold (85%)
            conversationPercentage > ConversationTokenManager.WARNING_THRESHOLD ->
                ContextCompat.getColor(context, R.color.warning_color)

            // Warning: Total with input over warning threshold
            totalPercentage > ConversationTokenManager.WARNING_THRESHOLD ->
                ContextCompat.getColor(context, R.color.warning_color)

            // Input would exceed available space
            availableTokens <= 0 ->
                ContextCompat.getColor(context, R.color.error_color)

            // User has chosen to continue past warning
            conversationTokenManager.hasContinuedPastWarning() ->
                ContextCompat.getColor(context, R.color.warning_color)

            else ->
                ContextCompat.getColor(context, R.color.text_secondary)
        }

        spannableText.setSpan(
            ForegroundColorSpan(color),
            0,
            displayText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textView.text = spannableText

        // Trigger token limit dialog when appropriate
        if (context is MainActivity) {
            // Check if we should show warning dialog
            val (shouldShowWarning, percentage) = conversationTokenManager.checkTokenLimits(modelId, isSubscribed)

            if (shouldShowWarning) {
                // Delay slightly to avoid showing dialog during text input
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    context.triggerTokenLimitDialog(percentage, modelId, isSubscribed)
                }, 1000)
            }
        }

        // Enhanced animation for critical state
        if (conversationPercentage > ConversationTokenManager.CRITICAL_THRESHOLD || availableTokens <= 0) {
            val animation = android.view.animation.AlphaAnimation(1.0f, 0.3f).apply {
                duration = 750
                repeatCount = 5
                repeatMode = android.view.animation.Animation.REVERSE
            }
            textView.startAnimation(animation)
        }

        // Debug logging for critical states
        if (totalPercentage > 0.7f || availableTokens <= 0) {
            Timber.d("Legacy token counter: ${(totalPercentage * 100).toInt()}% usage, " +
                    "conversation: $conversationTokens, input: $inputTokens, " +
                    "available: $availableTokens (display: $displayAvailable)")
        }
    }

    /**
     * Update UI with simple token information (no conversation context)
     */
    private fun updateWithSimpleTokenInfo(
        textView: TextView,
        text: String,
        modelId: String,
        isSubscribed: Boolean,
        context: Context
    ) {
        val inputTokens = TokenValidator.estimateTokenCount(text)
        val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
        
        val displayText = "$inputTokens/$maxTokens"
        val spannableText = SpannableString(displayText)

        val percentage = if (maxTokens > 0) inputTokens.toFloat() / maxTokens.toFloat() else 0f
        val color = when {
            percentage > 0.9f -> ContextCompat.getColor(context, R.color.error_color)
            percentage > 0.7f -> ContextCompat.getColor(context, R.color.warning_color)
            else -> ContextCompat.getColor(context, R.color.text_secondary)
        }

        spannableText.setSpan(
            ForegroundColorSpan(color),
            0,
            displayText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textView.text = spannableText
    }

    /**
     * Check if query is complex and needs more response space
     */
    private fun isComplexQuery(text: String): Boolean {
        val lowerText = text.lowercase()
        val complexKeywords = listOf(
            "explain", "generate", "code", "write", "create", "analyze",
            "compare", "summarize", "detailed", "step by step", "list", "describe"
        )
        return complexKeywords.any { keyword ->
            lowerText.contains(keyword)
        }
    }

    /**
     * Get a user-friendly message about token limits with conversation context
     * Enhanced to work with secure token manager
     */
    fun getTokenLimitExplanation(
        modelId: String,
        isSubscribed: Boolean,
        conversationTokenManager: ConversationTokenManager? = null
    ): String {
        val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
        val subscriberTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, true)

        // Get conversation context if available
        val conversationText = if (conversationTokenManager != null) {
            val secureTokenUsage = conversationTokenManager.getSecureTokenUsageInfo()
            
            if (secureTokenUsage != null) {
                // Use secure token manager data
                val usedTokens = secureTokenUsage.totalTokens
                val usagePercentage = secureTokenUsage.usagePercentage
                val formattedPercentage = (usagePercentage * 100).toInt()
                val maxInputTokens = secureTokenUsage.maxInputTokens
                val maxOutputTokens = secureTokenUsage.maxOutputTokens
                
                "\n\nCurrent conversation is using $usedTokens tokens (${formattedPercentage}% of limit)." +
                "\nInput limit: ${TokenValidator.formatTokenCount(maxInputTokens)}" +
                "\nOutput limit: ${TokenValidator.formatTokenCount(maxOutputTokens)}" +
                if (secureTokenUsage.continuedPastWarning) "\n⚠️ Continued past token warning" else ""
            } else {
                // Use legacy token manager data
                val usedTokens = conversationTokenManager.getTotalTokens()
                val usedPercentage = conversationTokenManager.getTokenUsagePercentage(modelId, isSubscribed)
                val formattedPercentage = (usedPercentage * 100).toInt()

                "\n\nCurrent conversation is using $usedTokens tokens (${formattedPercentage}% of limit)."
            }
        } else {
            ""
        }

        return if (isSubscribed) {
            "You can send up to ${TokenValidator.formatTokenCount(maxTokens)} tokens in a single conversation with this model.$conversationText"
        } else {
            "Free users can send up to ${TokenValidator.formatTokenCount(maxTokens)} tokens with this model. Pro subscribers get ${TokenValidator.formatTokenCount(subscriberTokens)} tokens.$conversationText"
        }
    }


}