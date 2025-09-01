package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.AimlApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream

/**
 * SIMPLIFIED Context Window Manager
 * 
 * Purpose: Pre-request validation to prevent exceeding context limits
 * - Estimates tokens before sending (user prompt + files + conversation history)
 * - Enforces 80% context window limit
 * - Forces new conversation when approaching limits
 * - Uses actual API-returned usage for tracking (not estimation)
 */
class ContextWindowManager(private val context: Context) {
    
    companion object {
        // Context window limits (80% rule)
        private const val MAX_USAGE_PERCENTAGE = 0.80f
        private const val WARNING_THRESHOLD = 0.70f
        
        // File size to token approximations (conservative estimates)
        private const val CHARS_PER_TOKEN_TEXT = 4.0
        private const val CHARS_PER_TOKEN_PDF = 5.0 // PDFs have more overhead
        private const val MAX_FILE_TOKENS = 50000 // Reasonable per-file limit
    }
    
    /**
     * Pre-request validation result
     */
    sealed class ValidationResult {
        data class Allowed(
            val estimatedTokens: Int,
            val usagePercentage: Float,
            val message: String = ""
        ) : ValidationResult()
        
        data class Warning(
            val estimatedTokens: Int,
            val usagePercentage: Float,
            val message: String
        ) : ValidationResult()
        
        data class Blocked(
            val estimatedTokens: Int,
            val usagePercentage: Float,
            val message: String
        ) : ValidationResult()
    }
    
    /**
     * Conversation context state (tracks actual API usage)
     */
    data class ConversationContext(
        val conversationId: String,
        val modelId: String,
        var actualInputTokens: Int = 0,
        var actualOutputTokens: Int = 0,
        var lastUpdated: Long = System.currentTimeMillis()
    ) {
        val totalTokens: Int get() = actualInputTokens + actualOutputTokens
        
        fun getUsagePercentage(maxContextTokens: Int): Float {
            return if (maxContextTokens > 0) totalTokens.toFloat() / maxContextTokens else 0f
        }
    }
    
    // Track conversation contexts (use actual API usage)
    private val conversationContexts = mutableMapOf<String, ConversationContext>()
    
    /**
     * MAIN VALIDATION METHOD
     * Call this BEFORE sending any message to validate context limits
     */
    suspend fun validateMessage(
        conversationId: String,
        modelId: String,
        userPrompt: String,
        attachedFiles: List<Uri> = emptyList(),
        isSubscribed: Boolean = true
    ): ValidationResult = withContext(Dispatchers.IO) {
        
        try {
            // Get model context limits
            val maxInputTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
            val maxOutputTokens = TokenValidator.getEffectiveMaxOutputTokens(modelId, isSubscribed)
            val totalContextTokens = maxInputTokens + maxOutputTokens
            
            // Get current conversation context (actual API usage)
            val currentContext = conversationContexts[conversationId] 
                ?: ConversationContext(conversationId, modelId)
            
            // Estimate tokens for new request
            val promptTokens = TokenValidator.getAccurateTokenCount(userPrompt, modelId)
            val fileTokens = estimateFileTokens(attachedFiles)
            val estimatedNewInputTokens = promptTokens + fileTokens
            
            // Calculate projected total usage
            val projectedTotalTokens = currentContext.totalTokens + estimatedNewInputTokens
            val projectedUsagePercentage = projectedTotalTokens.toFloat() / totalContextTokens.toFloat()
            
            Timber.d("=== CONTEXT WINDOW VALIDATION ===")
            Timber.d("Model: $modelId (max: ${totalContextTokens} tokens)")
            Timber.d("Current context: ${currentContext.totalTokens} tokens")
            Timber.d("New prompt: $promptTokens tokens")
            Timber.d("Attached files: $fileTokens tokens")
            Timber.d("Projected total: $projectedTotalTokens tokens")
            Timber.d("Usage percentage: ${(projectedUsagePercentage * 100).toInt()}%")
            Timber.d("===============================")
            
            // Make decision based on usage
            return@withContext when {
                projectedUsagePercentage > MAX_USAGE_PERCENTAGE -> {
                    ValidationResult.Blocked(
                        estimatedTokens = projectedTotalTokens,
                        usagePercentage = projectedUsagePercentage,
                        message = "Context window limit exceeded (${(projectedUsagePercentage * 100).toInt()}%). Please start a new conversation to continue."
                    )
                }
                
                projectedUsagePercentage > WARNING_THRESHOLD -> {
                    ValidationResult.Warning(
                        estimatedTokens = projectedTotalTokens,
                        usagePercentage = projectedUsagePercentage,
                        message = "Approaching context limit (${(projectedUsagePercentage * 100).toInt()}%). Consider starting a new conversation soon."
                    )
                }
                
                else -> {
                    ValidationResult.Allowed(
                        estimatedTokens = projectedTotalTokens,
                        usagePercentage = projectedUsagePercentage,
                        message = "Context usage: ${(projectedUsagePercentage * 100).toInt()}%"
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error validating context window: ${e.message}")
            return@withContext ValidationResult.Blocked(
                estimatedTokens = 0,
                usagePercentage = 1.0f,
                message = "Validation error: ${e.message}"
            )
        }
    }
    
    /**
     * Update conversation context with actual API usage
     * Call this AFTER receiving API response
     */
    fun updateWithApiUsage(
        conversationId: String,
        modelId: String,
        apiResponse: AimlApiResponse
    ) {
        try {
            val usage = apiResponse.usage
            if (usage == null) {
                Timber.w("No usage data in API response for conversation $conversationId")
                return
            }
            
            // Extract actual token usage from API
            val inputTokens = usage.promptTokens ?: usage.inputTokens ?: 0
            val outputTokens = usage.completionTokens ?: usage.outputTokens ?: 0
            
            if (inputTokens == 0 && outputTokens == 0) {
                Timber.w("API returned zero tokens for conversation $conversationId")
                return
            }
            
            // Get or create conversation context
            val context = conversationContexts.getOrPut(conversationId) {
                ConversationContext(conversationId, modelId)
            }
            
            // Update with actual API usage (these are the real numbers)
            context.actualInputTokens += inputTokens
            context.actualOutputTokens += outputTokens
            context.lastUpdated = System.currentTimeMillis()
            
            Timber.d("📊 Updated context for $conversationId:")
            Timber.d("   API Input: $inputTokens tokens")
            Timber.d("   API Output: $outputTokens tokens")
            Timber.d("   Total Context: ${context.totalTokens} tokens")
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating context with API usage: ${e.message}")
        }
    }
    
    /**
     * Get current conversation context
     */
    fun getConversationContext(conversationId: String): ConversationContext? {
        return conversationContexts[conversationId]
    }
    
    /**
     * Reset conversation context (when starting new conversation)
     */
    fun resetConversation(conversationId: String) {
        conversationContexts.remove(conversationId)
        Timber.d("🔄 Reset context for conversation: $conversationId")
    }
    
    /**
     * Get usage summary for UI display
     */
    fun getUsageSummary(conversationId: String, modelId: String, isSubscribed: Boolean): String {
        val context = conversationContexts[conversationId] ?: return "No usage data"
        val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed) + 
                       TokenValidator.getEffectiveMaxOutputTokens(modelId, isSubscribed)
        val percentage = (context.getUsagePercentage(maxTokens) * 100).toInt()
        
        return "${context.totalTokens}/${maxTokens} tokens ($percentage%)"
    }
    
    /**
     * Estimate tokens for attached files (before sending)
     */
    private suspend fun estimateFileTokens(files: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext 0
        
        var totalEstimatedTokens = 0
        
        for (file in files) {
            try {
                val estimatedTokens = estimateTokensForFile(file)
                totalEstimatedTokens += estimatedTokens
                Timber.d("📎 File token estimate: $estimatedTokens tokens")
            } catch (e: Exception) {
                Timber.e(e, "Error estimating tokens for file: $file")
                // Add conservative fallback estimate
                totalEstimatedTokens += 1000
            }
        }
        
        return@withContext minOf(totalEstimatedTokens, MAX_FILE_TOKENS * files.size)
    }
    
    /**
     * Estimate tokens for a single file
     */
    private suspend fun estimateTokensForFile(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val mimeType = FileUtil.getMimeType(context, uri)
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@withContext 0
            
            return@withContext when {
                mimeType?.startsWith("text/") == true -> {
                    // Text files: read and estimate
                    val content = inputStream.bufferedReader().use { it.readText() }
                    (content.length / CHARS_PER_TOKEN_TEXT).toInt()
                }
                
                mimeType == "application/pdf" -> {
                    // PDF files: estimate based on file size
                    val fileSize = inputStream.available()
                    val estimatedChars = fileSize * 0.5 // PDFs have ~50% text content
                    (estimatedChars / CHARS_PER_TOKEN_PDF).toInt()
                }
                
                mimeType?.startsWith("image/") == true -> {
                    // Images: fixed token cost (vision models)
                    1000
                }
                
                else -> {
                    // Unknown file types: conservative estimate
                    val fileSize = inputStream.available()
                    minOf(fileSize / 10, 5000) // Conservative estimate
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading file for token estimation: $uri")
            return@withContext 1000 // Fallback estimate
        }
    }
    
    /**
     * Check if conversation needs warning/reset based on actual usage
     */
    fun checkContextLimits(conversationId: String, modelId: String, isSubscribed: Boolean): Pair<Boolean, String?> {
        val context = conversationContexts[conversationId] ?: return Pair(false, null)
        val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed) + 
                       TokenValidator.getEffectiveMaxOutputTokens(modelId, isSubscribed)
        
        val usagePercentage = context.getUsagePercentage(maxTokens)
        
        return when {
            usagePercentage > MAX_USAGE_PERCENTAGE -> {
                Pair(true, "Context limit exceeded (${(usagePercentage * 100).toInt()}%). New conversation required.")
            }
            usagePercentage > WARNING_THRESHOLD -> {
                Pair(false, "Approaching context limit (${(usagePercentage * 100).toInt()}%). Consider starting new conversation.")
            }
            else -> {
                Pair(false, null)
            }
        }
    }
    
    /**
     * Clean up old conversation contexts (memory management)
     */
    fun cleanupOldContexts(maxAge: Long = 24 * 60 * 60 * 1000) { // 24 hours default
        val currentTime = System.currentTimeMillis()
        val toRemove = conversationContexts.filter { (_, context) ->
            currentTime - context.lastUpdated > maxAge
        }.keys
        
        toRemove.forEach { conversationId ->
            conversationContexts.remove(conversationId)
        }
        
        if (toRemove.isNotEmpty()) {
            Timber.d("🧹 Cleaned up ${toRemove.size} old conversation contexts")
        }
    }
}