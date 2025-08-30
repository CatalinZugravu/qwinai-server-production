package com.cyberflux.qwinai.ui

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.UsageCategory
import com.cyberflux.qwinai.model.UsageSummary
import com.cyberflux.qwinai.utils.TokenValidator
import com.cyberflux.qwinai.utils.UnifiedUsageManager
import com.cyberflux.qwinai.credits.CreditManager

/**
 * Helper class for displaying usage limits and quotas in the UI
 * 
 * Provides formatted text and styling for:
 * - Token limits (1000 input / 1500 output for free users)
 * - Daily request limits (30/day for premium limited models)
 * - Credit information (for free users)
 * - Image generation limits (25/day for premium users)
 */
class UsageLimitDisplayHelper(private val context: Context) {
    
    private val unifiedUsageManager by lazy { UnifiedUsageManager.getInstance(context) }
    private val creditManager by lazy { CreditManager.getInstance(context) }
    
    // Colors for different states
    private val colorNormal = ContextCompat.getColor(context, R.color.primary_text)
    private val colorWarning = ContextCompat.getColor(context, android.R.color.holo_orange_dark)
    private val colorDanger = ContextCompat.getColor(context, android.R.color.holo_red_dark)
    private val colorGood = ContextCompat.getColor(context, android.R.color.holo_green_dark)
    
    /**
     * Get comprehensive usage display text for a model
     */
    fun getUsageDisplayText(
        modelId: String,
        isSubscribed: Boolean,
        includeDetails: Boolean = false
    ): SpannableString {
        val usageSummary = unifiedUsageManager.getUsageSummary(modelId, isSubscribed)
        
        return if (includeDetails) {
            getDetailedUsageText(usageSummary, isSubscribed)
        } else {
            getCompactUsageText(usageSummary, isSubscribed)
        }
    }
    
    /**
     * Get compact usage text for status bars and small displays
     */
    private fun getCompactUsageText(usageSummary: UsageSummary, isSubscribed: Boolean): SpannableString {
        val text = buildString {
            // Category icon
            val categoryIcon = when (usageSummary.usageCategory) {
                UsageCategory.FREE_UNLIMITED -> "🆓"
                UsageCategory.CREDIT_BASED -> "💳"
                UsageCategory.LIMITED_PREMIUM -> "⭐"
                UsageCategory.UNLIMITED_PREMIUM -> "🔓"
                UsageCategory.IMAGE_GENERATION -> "🎨"
            }
            append("$categoryIcon ")
            
            // Token limits
            append("${TokenValidator.formatTokenCount(usageSummary.tokenLimits.maxInputTokens)}in")
            append("/")
            append("${TokenValidator.formatTokenCount(usageSummary.tokenLimits.maxOutputTokens)}out")
            
            // Additional limits
            when (usageSummary.usageCategory) {
                UsageCategory.LIMITED_PREMIUM -> {
                    if (usageSummary.dailyRequestLimit > 0) {
                        append(" | ${usageSummary.remainingRequests}/${usageSummary.dailyRequestLimit} req")
                    }
                }
                UsageCategory.CREDIT_BASED -> {
                    if (!isSubscribed && usageSummary.creditsRequired > 0) {
                        append(" | ${usageSummary.availableCredits} credits")
                    }
                }
                UsageCategory.IMAGE_GENERATION -> {
                    if (isSubscribed && usageSummary.dailyRequestLimit > 0) {
                        append(" | ${usageSummary.remainingRequests}/${usageSummary.dailyRequestLimit} imgs")
                    } else if (!isSubscribed) {
                        append(" | ${usageSummary.availableCredits} img credits")
                    }
                }
                else -> { /* No additional info */ }
            }
        }
        
        return SpannableString(text).apply {
            applyUsageColors(this, usageSummary, isSubscribed)
        }
    }
    
    /**
     * Get detailed usage text for dialogs and detailed views
     */
    private fun getDetailedUsageText(usageSummary: UsageSummary, isSubscribed: Boolean): SpannableString {
        val text = buildString {
            val categoryName = when (usageSummary.usageCategory) {
                UsageCategory.FREE_UNLIMITED -> "Free Unlimited"
                UsageCategory.CREDIT_BASED -> "Credit Based"
                UsageCategory.LIMITED_PREMIUM -> "Limited Premium"
                UsageCategory.UNLIMITED_PREMIUM -> "Unlimited Premium"
                UsageCategory.IMAGE_GENERATION -> "Image Generation"
            }
            
            appendLine("Category: $categoryName")
            
            // Token limits section
            appendLine("Token Limits:")
            append("  • Input: ${TokenValidator.formatTokenCount(usageSummary.tokenLimits.maxInputTokens)} tokens")
            if (!isSubscribed) {
                append(" (Free tier)")
            }
            appendLine()
            append("  • Output: ${TokenValidator.formatTokenCount(usageSummary.tokenLimits.maxOutputTokens)} tokens")
            if (!isSubscribed) {
                append(" (Free tier)")
            }
            appendLine()
            
            // Category-specific information
            when (usageSummary.usageCategory) {
                UsageCategory.FREE_UNLIMITED -> {
                    appendLine("✅ No usage restrictions")
                }
                
                UsageCategory.CREDIT_BASED -> {
                    if (!isSubscribed) {
                        appendLine("Credit Usage:")
                        appendLine("  • Cost: ${usageSummary.creditsRequired} credits per request")
                        append("  • Available: ${usageSummary.availableCredits} credits")
                        
                        if (usageSummary.availableCredits < usageSummary.creditsRequired) {
                            append(" ⚠️ Insufficient")
                        }
                        appendLine()
                    } else {
                        appendLine("✅ Premium: No credit cost")
                    }
                }
                
                UsageCategory.LIMITED_PREMIUM -> {
                    if (isSubscribed) {
                        appendLine("Daily Request Limit:")
                        appendLine("  • Limit: ${usageSummary.dailyRequestLimit} requests per day")
                        append("  • Remaining: ${usageSummary.remainingRequests} requests")
                        
                        if (usageSummary.remainingRequests <= 5) {
                            append(" ⚠️ Low")
                        }
                        appendLine()
                    } else {
                        appendLine("🔐 Premium model - requires credits for free users")
                        appendLine("  • Cost: ${usageSummary.creditsRequired} credits per request")
                        appendLine("  • Available: ${usageSummary.availableCredits} credits")
                    }
                }
                
                UsageCategory.UNLIMITED_PREMIUM -> {
                    if (isSubscribed) {
                        appendLine("✅ Premium: Unlimited usage")
                    } else {
                        appendLine("💳 Credit usage for free users:")
                        appendLine("  • Cost: ${usageSummary.creditsRequired} credits per request")
                        appendLine("  • Available: ${usageSummary.availableCredits} credits")
                    }
                }
                
                UsageCategory.IMAGE_GENERATION -> {
                    if (isSubscribed) {
                        appendLine("Image Generation Limit:")
                        appendLine("  • Limit: ${usageSummary.dailyRequestLimit} images per day")
                        append("  • Remaining: ${usageSummary.remainingRequests} images")
                        
                        if (usageSummary.remainingRequests <= 3) {
                            append(" ⚠️ Low")
                        }
                        appendLine()
                    } else {
                        appendLine("💳 Image generation uses credits:")
                        appendLine("  • Cost: 1 credit per image")
                        appendLine("  • Available: ${usageSummary.availableCredits} image credits")
                    }
                }
            }
            
            if (usageSummary.isSubscriptionRequired) {
                appendLine("💎 Upgrade to Premium for better limits")
            }
        }
        
        return SpannableString(text.trim()).apply {
            applyDetailedColors(this, usageSummary, isSubscribed)
        }
    }
    
    /**
     * Apply color styling to compact usage text
     */
    private fun applyUsageColors(spannable: SpannableString, usageSummary: UsageSummary, isSubscribed: Boolean) {
        val text = spannable.toString()
        
        // Color the token limits based on user type
        val tokenLimitColor = if (isSubscribed) colorGood else colorWarning
        
        // Find and color input/output token info
        val inputTokenStart = text.indexOf("in")
        if (inputTokenStart > 0) {
            spannable.setSpan(
                ForegroundColorSpan(tokenLimitColor),
                0, inputTokenStart + 2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        val outputTokenStart = text.indexOf("out")
        if (outputTokenStart > 0) {
            val outputTokenEnd = outputTokenStart + 3
            spannable.setSpan(
                ForegroundColorSpan(tokenLimitColor),
                inputTokenStart + 3, outputTokenEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Color remaining counts based on availability
        when (usageSummary.usageCategory) {
            UsageCategory.LIMITED_PREMIUM -> {
                if (usageSummary.remainingRequests <= 5) {
                    colorRemainingCount(spannable, "req", colorDanger)
                } else if (usageSummary.remainingRequests <= 10) {
                    colorRemainingCount(spannable, "req", colorWarning)
                }
            }
            UsageCategory.CREDIT_BASED -> {
                if (usageSummary.availableCredits < usageSummary.creditsRequired) {
                    colorRemainingCount(spannable, "credits", colorDanger)
                } else if (usageSummary.availableCredits < usageSummary.creditsRequired * 3) {
                    colorRemainingCount(spannable, "credits", colorWarning)
                }
            }
            UsageCategory.IMAGE_GENERATION -> {
                if (isSubscribed && usageSummary.remainingRequests <= 3) {
                    colorRemainingCount(spannable, "imgs", colorDanger)
                } else if (!isSubscribed && usageSummary.availableCredits < 3) {
                    colorRemainingCount(spannable, "credits", colorWarning)
                }
            }
            else -> { /* No special coloring */ }
        }
    }
    
    /**
     * Apply color styling to detailed usage text
     */
    private fun applyDetailedColors(spannable: SpannableString, usageSummary: UsageSummary, isSubscribed: Boolean) {
        val text = spannable.toString()
        
        // Color category name
        val categoryEnd = text.indexOf('\n')
        if (categoryEnd > 0) {
            spannable.setSpan(
                ForegroundColorSpan(colorNormal),
                0, categoryEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Color warning indicators
        colorWarningText(spannable, "⚠️ Insufficient", colorDanger)
        colorWarningText(spannable, "⚠️ Low", colorWarning)
        colorWarningText(spannable, "✅", colorGood)
        colorWarningText(spannable, "🔐 Premium", colorWarning)
        colorWarningText(spannable, "💎 Upgrade", colorWarning)
    }
    
    /**
     * Helper to color specific remaining count text
     */
    private fun colorRemainingCount(spannable: SpannableString, keyword: String, color: Int) {
        val text = spannable.toString()
        val keywordIndex = text.indexOf(keyword)
        if (keywordIndex > 0) {
            // Find the number before the keyword
            val beforeKeyword = text.substring(0, keywordIndex).trim()
            val lastSpace = beforeKeyword.lastIndexOf(' ')
            if (lastSpace >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    lastSpace + 1, keywordIndex + keyword.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
    
    /**
     * Helper to color warning text
     */
    private fun colorWarningText(spannable: SpannableString, text: String, color: Int) {
        val fullText = spannable.toString()
        var startIndex = 0
        while (true) {
            val index = fullText.indexOf(text, startIndex)
            if (index == -1) break
            
            spannable.setSpan(
                ForegroundColorSpan(color),
                index, index + text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + 1
        }
    }
    
    /**
     * Set usage display on a TextView with appropriate styling
     */
    fun updateUsageDisplay(
        textView: TextView,
        modelId: String,
        isSubscribed: Boolean,
        isDetailed: Boolean = false
    ) {
        val usageText = getUsageDisplayText(modelId, isSubscribed, isDetailed)
        textView.text = usageText
        
        // Apply additional styling
        textView.textSize = if (isDetailed) 14f else 12f
        textView.maxLines = if (isDetailed) Int.MAX_VALUE else 2
    }
    
    /**
     * Get usage status color for theming UI elements
     */
    fun getUsageStatusColor(modelId: String, isSubscribed: Boolean): Int {
        val usageSummary = unifiedUsageManager.getUsageSummary(modelId, isSubscribed)
        
        return when {
            // Check for critical states first
            !isSubscribed && usageSummary.creditsRequired > 0 && 
                usageSummary.availableCredits < usageSummary.creditsRequired -> colorDanger
            
            usageSummary.dailyRequestLimit > 0 && usageSummary.remainingRequests <= 0 -> colorDanger
            
            // Warning states
            usageSummary.dailyRequestLimit > 0 && usageSummary.remainingRequests <= 5 -> colorWarning
            !isSubscribed && usageSummary.creditsRequired > 0 && 
                usageSummary.availableCredits < usageSummary.creditsRequired * 3 -> colorWarning
            
            // Good states
            usageSummary.usageCategory == UsageCategory.FREE_UNLIMITED -> colorGood
            isSubscribed && usageSummary.usageCategory == UsageCategory.UNLIMITED_PREMIUM -> colorGood
            
            // Default
            else -> colorNormal
        }
    }
    
    /**
     * Check if user should see upgrade prompt for this model
     */
    fun shouldShowUpgradePrompt(modelId: String, isSubscribed: Boolean): Boolean {
        if (isSubscribed) return false
        
        val usageSummary = unifiedUsageManager.getUsageSummary(modelId, isSubscribed)
        
        return when (usageSummary.usageCategory) {
            UsageCategory.LIMITED_PREMIUM, UsageCategory.UNLIMITED_PREMIUM -> true
            UsageCategory.CREDIT_BASED -> usageSummary.availableCredits < usageSummary.creditsRequired
            UsageCategory.IMAGE_GENERATION -> usageSummary.availableCredits < 1
            else -> false
        }
    }
}