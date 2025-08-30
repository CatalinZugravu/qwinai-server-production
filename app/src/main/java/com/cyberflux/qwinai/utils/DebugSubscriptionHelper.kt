package com.cyberflux.qwinai.utils

import android.content.Context
import android.widget.Toast
import com.cyberflux.qwinai.BuildConfig
import timber.log.Timber

/**
 * Debug-only helper for testing subscription functionality
 * 
 * Features:
 * - Mock subscription toggle for testing
 * - Easy testing of usage limits
 * - Debug subscription status display
 * 
 * Only active in debug builds!
 */
object DebugSubscriptionHelper {
    
    private const val DEBUG_SUBSCRIPTION_KEY = "debug_subscription_enabled"
    
    /**
     * Toggle debug subscription status (DEBUG builds only)
     */
    fun toggleDebugSubscription(context: Context): Boolean {
        if (!BuildConfig.DEBUG) {
            Timber.w("Debug subscription helper only works in debug builds")
            return false
        }
        
        val currentStatus = isDebugSubscriptionActive(context)
        val newStatus = !currentStatus
        
        // Store the debug subscription status
        context.getSharedPreferences("debug_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DEBUG_SUBSCRIPTION_KEY, newStatus)
            .apply()
        
        // Update the main subscription status
        PrefsManager.setSubscribed(context, newStatus, if (newStatus) 365 else 0) // 365 days for debug subscription
        
        val statusText = if (newStatus) "ENABLED" else "DISABLED"
        Toast.makeText(context, "🔧 Debug Subscription $statusText", Toast.LENGTH_LONG).show()
        
        Timber.d("🔧 Debug subscription toggled: $newStatus")
        
        return newStatus
    }
    
    /**
     * Check if debug subscription is active
     */
    fun isDebugSubscriptionActive(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        
        return context.getSharedPreferences("debug_prefs", Context.MODE_PRIVATE)
            .getBoolean(DEBUG_SUBSCRIPTION_KEY, false)
    }
    
    /**
     * Get debug subscription end time (1 year from now)
     */
    private fun getDebugSubscriptionEndTime(): Long {
        return System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000) // 1 year
    }
    
    /**
     * Show debug subscription status
     */
    fun showDebugSubscriptionStatus(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        val isDebugSubscribed = isDebugSubscriptionActive(context)
        val isRealSubscribed = PrefsManager.isSubscribed(context)
        
        val message = buildString {
            appendLine("🔧 DEBUG SUBSCRIPTION STATUS")
            appendLine("Debug Override: ${if (isDebugSubscribed) "ENABLED ✅" else "DISABLED ❌"}")
            appendLine("Real Status: ${if (isRealSubscribed) "SUBSCRIBED ✅" else "FREE ❌"}")
            appendLine("Effective Status: ${if (isRealSubscribed) "PREMIUM" else "FREE"}")
            
            if (isDebugSubscribed) {
                appendLine()
                appendLine("Debug Features Unlocked:")
                appendLine("• Unlimited token limits")
                appendLine("• 30 requests/day for limited models")
                appendLine("• 25 image generations/day")
                appendLine("• No credit consumption")
            }
        }
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Timber.d(message)
    }
    
    /**
     * Reset all debug settings
     */
    fun resetDebugSettings(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        context.getSharedPreferences("debug_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        
        // Reset real subscription to false
        PrefsManager.setSubscribed(context, false, 0)
        
        Toast.makeText(context, "🔧 Debug settings reset", Toast.LENGTH_SHORT).show()
        Timber.d("🔧 Debug subscription settings reset")
    }
    
    /**
     * Get debug info for subscription testing
     */
    fun getDebugInfo(context: Context): String {
        if (!BuildConfig.DEBUG) return "Debug mode not available"
        
        val isDebugSubscribed = isDebugSubscriptionActive(context)
        val isRealSubscribed = PrefsManager.isSubscribed(context)
        val unifiedUsageManager = UnifiedUsageManager.getInstance(context)
        
        return buildString {
            appendLine("🔧 DEBUG SUBSCRIPTION INFO")
            appendLine("========================")
            appendLine("Build Type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
            appendLine("Debug Override: $isDebugSubscribed")
            appendLine("Real Subscription: $isRealSubscribed")
            appendLine("Effective Status: ${if (isRealSubscribed) "PREMIUM" else "FREE"}")
            appendLine()
            appendLine("Usage Manager Debug:")
            append(unifiedUsageManager.getDebugInfo())
        }
    }
    
    /**
     * Quick test of usage limits with current subscription status
     */
    suspend fun testUsageLimits(context: Context): String {
        if (!BuildConfig.DEBUG) return "Debug mode not available"
        
        val unifiedUsageManager = UnifiedUsageManager.getInstance(context)
        val isSubscribed = PrefsManager.isSubscribed(context)
        
        val results = buildString {
            appendLine("🧪 USAGE LIMITS TEST")
            appendLine("Subscription Status: ${if (isSubscribed) "PREMIUM" else "FREE"}")
            appendLine()
            
            // Test different models
            val testModels = listOf(
                "gpt-4o" to "Limited Premium Model",
                "gpt-4-turbo" to "Unlimited Premium Model", 
                "cohere/command-r-plus" to "Free Model",
                "dall-e-3" to "Image Generation"
            )
            
            for ((modelId, description) in testModels) {
                appendLine("Testing: $description ($modelId)")
                
                val validation = unifiedUsageManager.validateRequest(modelId, "Test prompt", isSubscribed)
                val summary = unifiedUsageManager.getUsageSummary(modelId, isSubscribed)
                
                appendLine("  ✓ Allowed: ${validation.isAllowed}")
                if (!validation.isAllowed) {
                    appendLine("  ⚠ Reason: ${validation.reason}")
                }
                appendLine("  📊 Category: ${summary.usageCategory}")
                appendLine("  🔢 Tokens: ${summary.tokenLimits.maxInputTokens}in/${summary.tokenLimits.maxOutputTokens}out")
                
                if (summary.dailyRequestLimit > 0) {
                    appendLine("  📅 Daily: ${summary.remainingRequests}/${summary.dailyRequestLimit}")
                }
                
                if (summary.creditsRequired > 0) {
                    appendLine("  💳 Credits: ${summary.creditsRequired} required, ${summary.availableCredits} available")
                }
                appendLine()
            }
        }
        
        Timber.d(results)
        return results
    }
}