package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.network.AimlApiResponse
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * SIMPLIFIED API Usage Tracker
 * 
 * Purpose: Track actual token usage from API responses for display
 * - Records actual usage returned by API (not estimates)
 * - Provides UI display data
 * - Tracks usage statistics
 */
class ApiUsageTracker {
    
    companion object {
        @Volatile
        private var INSTANCE: ApiUsageTracker? = null
        
        fun getInstance(): ApiUsageTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiUsageTracker().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Usage data for a single API call
     */
    data class ApiUsageData(
        val conversationId: String,
        val modelId: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        val reasoningTokens: Int = 0,
        val timestamp: Long = System.currentTimeMillis(),
        val provider: String = "unknown"
    ) {
        fun getDisplayString(): String {
            return when {
                reasoningTokens > 0 -> "$inputTokens→ $outputTokens← ($reasoningTokens💭) = ${totalTokens}📊"
                else -> "$inputTokens→ $outputTokens← = ${totalTokens}📊"
            }
        }
        
        fun getCostEstimate(): String {
            // Rough cost estimates (can be refined based on actual model pricing)
            val inputCost = inputTokens * 0.01 / 1000 // $0.01 per 1K input tokens
            val outputCost = outputTokens * 0.03 / 1000 // $0.03 per 1K output tokens
            val total = inputCost + outputCost
            return if (total < 0.01) "<$0.01" else "$%.3f".format(total)
        }
    }
    
    /**
     * Conversation usage summary
     */
    data class ConversationUsage(
        val conversationId: String,
        val modelId: String,
        var totalInputTokens: AtomicInteger = AtomicInteger(0),
        var totalOutputTokens: AtomicInteger = AtomicInteger(0),
        var totalReasoningTokens: AtomicInteger = AtomicInteger(0),
        var messageCount: AtomicInteger = AtomicInteger(0),
        var lastUpdated: AtomicLong = AtomicLong(System.currentTimeMillis())
    ) {
        val totalTokens: Int
            get() = totalInputTokens.get() + totalOutputTokens.get() + totalReasoningTokens.get()
            
        fun getDisplaySummary(): String {
            val input = totalInputTokens.get()
            val output = totalOutputTokens.get()
            val reasoning = totalReasoningTokens.get()
            val total = totalTokens
            val messages = messageCount.get()
            
            return when {
                reasoning > 0 -> "$total tokens ($input→ $output← $reasoning💭) • $messages msg"
                else -> "$total tokens ($input→ $output←) • $messages msg"
            }
        }
    }
    
    // Thread-safe storage
    private val conversationUsages = ConcurrentHashMap<String, ConversationUsage>()
    private val recentApiCalls = mutableListOf<ApiUsageData>()
    private val maxRecentCalls = 100
    
    /**
     * Record API usage from response
     * Call this whenever you receive an API response
     */
    fun recordApiUsage(
        conversationId: String,
        modelId: String,
        apiResponse: AimlApiResponse
    ): ApiUsageData? {
        
        try {
            val usage = apiResponse.usage
            if (usage == null) {
                Timber.d("No usage data in API response")
                return null
            }
            
            // Extract token usage using UniversalTokenCounter logic
            val tokenUsage = UniversalTokenCounter.extractTokenUsage(apiResponse, modelId)
            
            if (!tokenUsage.hasValidData()) {
                Timber.w("Invalid token data from API response")
                return null
            }
            
            // Create usage record
            val usageData = ApiUsageData(
                conversationId = conversationId,
                modelId = modelId,
                inputTokens = tokenUsage.inputTokens,
                outputTokens = tokenUsage.outputTokens,
                totalTokens = tokenUsage.totalTokens,
                reasoningTokens = tokenUsage.reasoningTokens,
                provider = tokenUsage.provider
            )
            
            // Update conversation totals
            updateConversationUsage(conversationId, modelId, usageData)
            
            // Store recent call
            synchronized(recentApiCalls) {
                recentApiCalls.add(0, usageData) // Add to beginning
                if (recentApiCalls.size > maxRecentCalls) {
                    recentApiCalls.removeAt(recentApiCalls.size - 1) // Remove oldest
                }
            }
            
            Timber.d("📊 API Usage Recorded: ${usageData.getDisplayString()}")
            return usageData
            
        } catch (e: Exception) {
            Timber.e(e, "Error recording API usage: ${e.message}")
            return null
        }
    }
    
    /**
     * Update conversation usage totals
     */
    private fun updateConversationUsage(conversationId: String, modelId: String, usageData: ApiUsageData) {
        val conversationUsage = conversationUsages.getOrPut(conversationId) {
            ConversationUsage(conversationId, modelId)
        }
        
        conversationUsage.totalInputTokens.addAndGet(usageData.inputTokens)
        conversationUsage.totalOutputTokens.addAndGet(usageData.outputTokens)
        conversationUsage.totalReasoningTokens.addAndGet(usageData.reasoningTokens)
        conversationUsage.messageCount.incrementAndGet()
        conversationUsage.lastUpdated.set(System.currentTimeMillis())
    }
    
    /**
     * Get usage summary for a conversation
     */
    fun getConversationUsage(conversationId: String): ConversationUsage? {
        return conversationUsages[conversationId]
    }
    
    /**
     * Get display string for conversation usage
     */
    fun getConversationUsageDisplay(conversationId: String): String {
        val usage = conversationUsages[conversationId]
        return usage?.getDisplaySummary() ?: "No usage data"
    }
    
    /**
     * Get recent API calls for debugging
     */
    fun getRecentApiCalls(count: Int = 10): List<ApiUsageData> {
        return synchronized(recentApiCalls) {
            recentApiCalls.take(count)
        }
    }
    
    /**
     * Get total tokens for all conversations
     */
    fun getTotalTokensUsed(): Int {
        return conversationUsages.values.sumOf { it.totalTokens }
    }
    
    /**
     * Get usage by provider
     */
    fun getUsageByProvider(): Map<String, Int> {
        val providerUsage = mutableMapOf<String, Int>()
        
        synchronized(recentApiCalls) {
            recentApiCalls.forEach { usage ->
                providerUsage[usage.provider] = (providerUsage[usage.provider] ?: 0) + usage.totalTokens
            }
        }
        
        return providerUsage
    }
    
    /**
     * Reset usage for a conversation
     */
    fun resetConversationUsage(conversationId: String) {
        conversationUsages.remove(conversationId)
        Timber.d("🔄 Reset usage tracking for conversation: $conversationId")
    }
    
    /**
     * Get estimated costs for conversation
     */
    fun getConversationCostEstimate(conversationId: String): String {
        val usage = conversationUsages[conversationId] ?: return "No data"
        
        // Rough cost calculation (adjust based on actual model pricing)
        val inputCost = usage.totalInputTokens.get() * 0.01 / 1000
        val outputCost = usage.totalOutputTokens.get() * 0.03 / 1000
        val total = inputCost + outputCost
        
        return if (total < 0.01) "<$0.01" else "$%.3f".format(total)
    }
    
    /**
     * Export usage data for analysis
     */
    fun exportUsageData(): String {
        val sb = StringBuilder()
        sb.append("=== API USAGE REPORT ===\n")
        sb.append("Generated: ${java.util.Date()}\n\n")
        
        sb.append("CONVERSATION SUMMARIES:\n")
        conversationUsages.values.sortedByDescending { it.lastUpdated.get() }.forEach { usage ->
            sb.append("${usage.conversationId}: ${usage.getDisplaySummary()}\n")
        }
        
        sb.append("\nPROVIDER USAGE:\n")
        getUsageByProvider().forEach { (provider, tokens) ->
            sb.append("$provider: $tokens tokens\n")
        }
        
        sb.append("\nRECENT API CALLS:\n")
        getRecentApiCalls(20).forEach { usage ->
            sb.append("${usage.modelId}: ${usage.getDisplayString()} (${java.util.Date(usage.timestamp)})\n")
        }
        
        return sb.toString()
    }
    
    /**
     * Clean up old data (memory management)
     */
    fun cleanup(maxAge: Long = 24 * 60 * 60 * 1000) { // 24 hours
        val currentTime = System.currentTimeMillis()
        
        // Clean old conversations
        val oldConversations = conversationUsages.filter { (_, usage) ->
            currentTime - usage.lastUpdated.get() > maxAge
        }.keys
        
        oldConversations.forEach { conversationId ->
            conversationUsages.remove(conversationId)
        }
        
        // Clean old API calls
        synchronized(recentApiCalls) {
            recentApiCalls.removeAll { usage ->
                currentTime - usage.timestamp > maxAge
            }
        }
        
        if (oldConversations.isNotEmpty()) {
            Timber.d("🧹 Cleaned ${oldConversations.size} old conversation usage records")
        }
    }
}