package com.cyberflux.qwinai.tokens

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.TokenValidator
import timber.log.Timber
import java.security.MessageDigest
import java.util.*

/**
 * Secure Token Manager with encrypted storage and proper validation
 * Features:
 * - Encrypted storage of token data
 * - Model-specific token limits from ModelConfigManager
 * - Non-subscriber token reduction (50% penalty)
 * - Token counting for all scenarios (send, edit, reload)
 * - Conversation-level persistence and resumption
 * - Max token enforcement and new conversation forcing
 * - Edge case handling
 * - Instruction token calculations
 */
class SecureTokenManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "secure_token_system"
        private const val MIN_RESPONSE_TOKENS = 500 // Minimum tokens reserved for AI response
        private const val INSTRUCTION_TOKEN_BUFFER = 100 // Buffer for instruction variations
        private const val MAX_CONVERSATION_AGE_DAYS = 30 // Auto-cleanup old conversations
        
        // Constants for token reservation
        private const val RESERVED_RESPONSE_PERCENTAGE = 0.25 // Reserve 25% for responses
        private const val COMPLEX_QUERY_RESERVED_PERCENTAGE = 0.35 // Reserve 35% for complex responses
        private const val WARNING_THRESHOLD = 0.80 // Show warning at 80% usage
        private const val CRITICAL_THRESHOLD = 0.90 // Hard limit at 90% usage
        
        // Security constants
        private const val HASH_VALIDATION_ENABLED = true
        private const val TOKEN_STATE_TIMEOUT_MS = 5000L // 5 seconds timeout for operations
        
        // Keywords that suggest complex queries needing more response space
        private val COMPLEX_QUERY_KEYWORDS = listOf(
            "explain", "generate", "code", "write", "create", "analyze",
            "compare", "summarize", "detailed", "step by step", "list", "describe"
        )
        
        @Volatile
        private var INSTANCE: SecureTokenManager? = null
        
        fun getInstance(context: Context): SecureTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureTokenManager(context.applicationContext).also { INSTANCE = it }
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

    private val installId: String by lazy {
        encryptedPrefs.getString("install_id", null) ?: generateInstallId()
    }

    // Current conversation state
    private var currentConversationId: String? = null
    private var conversationTokenCount = 0
    private var systemInstructionTokens = 500
    private var messageTokenCounts = mutableMapOf<String, Int>()
    private var continuedPastWarning = false
    private var lastTokenUpdate = 0L
    
    // Listeners for token changes
    private val tokenChangeListeners = mutableListOf<TokenChangeListener>()
    
    interface TokenChangeListener {
        fun onTokensChanged(
            conversationTokens: Int, 
            systemTokens: Int, 
            totalTokens: Int,
            modelId: String,
            isSubscribed: Boolean,
            maxTokens: Int,
            usagePercentage: Float
        )
        fun onMaxTokensReached(conversationId: String, modelId: String)
        fun onConversationMustReset(conversationId: String, reason: String)
    }
    
    enum class TokenOperation {
        SEND_MESSAGE, EDIT_MESSAGE, RELOAD_RESPONSE, ADD_FILE, SYSTEM_INSTRUCTION
    }
    
    data class TokenValidationResult(
        val isValid: Boolean,
        val exceedsLimit: Boolean,
        val availableTokens: Int,
        val usagePercentage: Float,
        val shouldShowWarning: Boolean,
        val mustStartNewConversation: Boolean,
        val reason: String
    )
    
    init {
        // Clean up old conversation data on initialization
        cleanupOldConversations()
    }

    private fun generateInstallId(): String {
        val uuid = UUID.randomUUID().toString()
        encryptedPrefs.edit().putString("install_id", uuid).apply()
        return uuid
    }

    private fun generateTokenHash(
        conversationId: String,
        tokenCount: Int,
        systemTokens: Int,
        messageCount: Int
    ): String {
        val data = "$conversationId:$tokenCount:$systemTokens:$messageCount:$installId:${getCurrentDate()}:${System.currentTimeMillis() / 10000}"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentDate(): String {
        return Calendar.getInstance().let {
            "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)}-${it.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    /**
     * Get model-specific token limits from ModelConfigManager
     */
    private fun getModelTokenLimits(modelId: String, isSubscribed: Boolean): Pair<Int, Int> {
        val config = ModelConfigManager.getConfig(modelId)
        
        if (config == null) {
            Timber.w("Model configuration not found for $modelId, using defaults")
            return Pair(16000, 4000) // Default fallback
        }
        
        val maxInputTokens = config.maxInputTokens
        val maxOutputTokens = config.maxOutputTokens
        
        // Apply non-subscriber penalty (50% reduction)
        val effectiveInputTokens = if (isSubscribed) maxInputTokens else maxInputTokens / 2
        val effectiveOutputTokens = if (isSubscribed) maxOutputTokens else maxOutputTokens / 2
        
        return Pair(effectiveInputTokens, effectiveOutputTokens)
    }

    /**
     * Set the current conversation ID and load its state
     */
    fun setConversationId(conversationId: String, modelId: String) {
        Timber.d("Setting conversation ID: $conversationId (model: $modelId)")
        
        if (this.currentConversationId == conversationId) {
            // Same conversation, just reload state
            loadTokenState(conversationId, modelId)
            return
        }
        
        // Save current state before switching
        if (this.currentConversationId != null) {
            saveTokenState(this.currentConversationId!!, modelId)
        }
        
        // Switch to new conversation
        this.currentConversationId = conversationId
        loadTokenState(conversationId, modelId)
        
        // Notify listeners
        notifyTokenChangeListeners(modelId)
    }

    /**
     * Validate token operation before execution
     */
    fun validateTokenOperation(
        operation: TokenOperation,
        inputText: String,
        modelId: String,
        isSubscribed: Boolean,
        conversationId: String? = null
    ): TokenValidationResult {
        val inputTokens = TokenValidator.estimateTokenCount(inputText)
        val (maxInputTokens, maxOutputTokens) = getModelTokenLimits(modelId, isSubscribed)
        
        // Calculate effective system tokens
        val effectiveSystemTokens = minOf(systemInstructionTokens, maxInputTokens / 4)
        
        // Determine if this is a complex query requiring more response space
        val isComplexQuery = isComplexQuery(inputText)
        val reservedPercentage = if (isComplexQuery) {
            COMPLEX_QUERY_RESERVED_PERCENTAGE
        } else {
            RESERVED_RESPONSE_PERCENTAGE
        }
        
        // Calculate reserved tokens for response
        val reservedResponseTokens = maxOf(
            MIN_RESPONSE_TOKENS,
            (maxInputTokens * reservedPercentage).toInt()
        )
        
        // Calculate current usage
        val currentUsage = conversationTokenCount + effectiveSystemTokens
        val projectedUsage = currentUsage + inputTokens
        val availableTokens = maxInputTokens - currentUsage - reservedResponseTokens
        val usagePercentage = projectedUsage.toFloat() / maxInputTokens.toFloat()
        
        // Check various conditions
        val exceedsHardLimit = projectedUsage > (maxInputTokens * CRITICAL_THRESHOLD)
        val shouldShowWarning = projectedUsage > (maxInputTokens * WARNING_THRESHOLD) && !continuedPastWarning
        val insufficientSpace = inputTokens > availableTokens
        val mustStartNew = exceedsHardLimit || (projectedUsage + MIN_RESPONSE_TOKENS) > maxInputTokens
        
        val reason = when {
            exceedsHardLimit -> "Token usage exceeds critical threshold (${(CRITICAL_THRESHOLD * 100).toInt()}%)"
            insufficientSpace -> "Insufficient space for input text ($inputTokens tokens needed, $availableTokens available)"
            mustStartNew -> "Not enough space for AI response (need $MIN_RESPONSE_TOKENS minimum)"
            else -> "Validation passed"
        }
        
        Timber.d("Token validation for $operation: " +
                "input=$inputTokens, current=$currentUsage, projected=$projectedUsage, " +
                "available=$availableTokens, usage=${(usagePercentage * 100).toInt()}%, " +
                "exceeds=$exceedsHardLimit, warning=$shouldShowWarning, mustStart=$mustStartNew")
        
        return TokenValidationResult(
            isValid = !exceedsHardLimit && !insufficientSpace,
            exceedsLimit = exceedsHardLimit,
            availableTokens = availableTokens,
            usagePercentage = usagePercentage,
            shouldShowWarning = shouldShowWarning,
            mustStartNewConversation = mustStartNew,
            reason = reason
        )
    }

    /**
     * Add a message to the token count with security validation
     */
    fun addMessage(message: ChatMessage, modelId: String, operation: TokenOperation = TokenOperation.SEND_MESSAGE): Boolean {
        if (messageTokenCounts.containsKey(message.id)) {
            Timber.w("Message ${message.id} already counted, skipping")
            return true
        }
        
        val messageTokens = TokenValidator.estimateTokenCount(message.message)
        val isSubscribed = PrefsManager.isSubscribed(context)
        
        // Validate the operation
        val validation = validateTokenOperation(operation, message.message, modelId, isSubscribed, message.conversationId)
        
        if (!validation.isValid) {
            Timber.w("Token validation failed for message ${message.id}: ${validation.reason}")
            
            if (validation.mustStartNewConversation) {
                notifyMustStartNewConversation(message.conversationId, validation.reason)
            }
            
            return false
        }
        
        // Add the message
        messageTokenCounts[message.id] = messageTokens
        conversationTokenCount += messageTokens
        lastTokenUpdate = System.currentTimeMillis()
        
        // Validate consistency
        validateTokenConsistency()
        
        // Save state
        saveTokenState(message.conversationId, modelId)
        
        // Notify listeners
        notifyTokenChangeListeners(modelId)
        
        Timber.d("Added message ${message.id}: $messageTokens tokens, total: $conversationTokenCount")
        
        return true
    }

    /**
     * Update message tokens (for edits and reloads)
     */
    fun updateMessage(messageId: String, newContent: String, modelId: String): Boolean {
        val oldTokens = messageTokenCounts[messageId] ?: 0
        val newTokens = TokenValidator.estimateTokenCount(newContent)
        val isSubscribed = PrefsManager.isSubscribed(context)
        
        // For updates, we need to check if the change is acceptable
        val tokenDifference = newTokens - oldTokens
        if (tokenDifference > 0) {
            // Validate the additional tokens
            val validation = validateTokenOperation(
                TokenOperation.EDIT_MESSAGE, 
                newContent, 
                modelId, 
                isSubscribed, 
                currentConversationId
            )
            
            if (!validation.isValid) {
                Timber.w("Token validation failed for message update $messageId: ${validation.reason}")
                return false
            }
        }
        
        // Update tokens
        conversationTokenCount = conversationTokenCount - oldTokens + newTokens
        messageTokenCounts[messageId] = newTokens
        lastTokenUpdate = System.currentTimeMillis()
        
        // Validate consistency
        validateTokenConsistency()
        
        // Save state
        currentConversationId?.let { saveTokenState(it, modelId) }
        
        // Notify listeners
        notifyTokenChangeListeners(modelId)
        
        Timber.d("Updated message $messageId: $oldTokens -> $newTokens tokens, total: $conversationTokenCount")
        
        return true
    }

    /**
     * Remove a message from token count
     */
    fun removeMessage(messageId: String, modelId: String): Boolean {
        val tokens = messageTokenCounts.remove(messageId) ?: 0
        conversationTokenCount -= tokens
        conversationTokenCount = maxOf(0, conversationTokenCount)
        lastTokenUpdate = System.currentTimeMillis()
        
        // Validate consistency
        validateTokenConsistency()
        
        // Save state
        currentConversationId?.let { saveTokenState(it, modelId) }
        
        // Notify listeners
        notifyTokenChangeListeners(modelId)
        
        Timber.d("Removed message $messageId: $tokens tokens, total: $conversationTokenCount")
        
        return true
    }

    /**
     * Set system instruction tokens
     */
    fun setSystemInstructionTokens(tokens: Int, modelId: String) {
        systemInstructionTokens = tokens + INSTRUCTION_TOKEN_BUFFER
        lastTokenUpdate = System.currentTimeMillis()
        
        // Save state
        currentConversationId?.let { saveTokenState(it, modelId) }
        
        // Notify listeners
        notifyTokenChangeListeners(modelId)
        
        Timber.d("Set system instruction tokens: $systemInstructionTokens (including buffer)")
    }

    /**
     * Add file tokens
     */
    fun addFileTokens(tokens: Int, modelId: String): Boolean {
        val isSubscribed = PrefsManager.isSubscribed(context)
        
        // Create dummy text for validation
        val dummyText = "a".repeat(tokens * 4) // Approximate text for token count
        
        val validation = validateTokenOperation(
            TokenOperation.ADD_FILE, 
            dummyText, 
            modelId, 
            isSubscribed, 
            currentConversationId
        )
        
        if (!validation.isValid) {
            Timber.w("Cannot add file tokens: ${validation.reason}")
            return false
        }
        
        conversationTokenCount += tokens
        lastTokenUpdate = System.currentTimeMillis()
        
        // Save state
        currentConversationId?.let { saveTokenState(it, modelId) }
        
        // Notify listeners
        notifyTokenChangeListeners(modelId)
        
        Timber.d("Added file tokens: $tokens, total: $conversationTokenCount")
        
        return true
    }

    /**
     * Check if query is complex and needs more response space
     */
    private fun isComplexQuery(text: String): Boolean {
        val lowerText = text.lowercase()
        return COMPLEX_QUERY_KEYWORDS.any { keyword ->
            lowerText.contains(keyword)
        }
    }

    /**
     * Validate token consistency and fix if needed
     */
    private fun validateTokenConsistency() {
        val calculatedTotal = messageTokenCounts.values.sum()
        if (calculatedTotal != conversationTokenCount) {
            Timber.e("Token count inconsistency! Stored: $conversationTokenCount, Calculated: $calculatedTotal")
            conversationTokenCount = calculatedTotal // Fix the inconsistency
        }
    }

    /**
     * Save token state with encryption and hash validation
     */
    private fun saveTokenState(conversationId: String, modelId: String) {
        if (conversationId.startsWith("private_")) return // Don't save private conversations
        
        try {
            val messageCount = messageTokenCounts.size
            val hash = generateTokenHash(conversationId, conversationTokenCount, systemInstructionTokens, messageCount)
            
            val key = "conv_$conversationId"
            encryptedPrefs.edit()
                .putInt("${key}_tokens", conversationTokenCount)
                .putInt("${key}_system_tokens", systemInstructionTokens)
                .putBoolean("${key}_continued_warning", continuedPastWarning)
                .putString("${key}_hash", hash)
                .putString("${key}_model_id", modelId)
                .putLong("${key}_last_update", lastTokenUpdate)
                .putInt("${key}_message_count", messageCount)
                .apply()
            
            Timber.d("Saved secure token state for $conversationId: $conversationTokenCount tokens")
        } catch (e: Exception) {
            Timber.e(e, "Error saving token state: ${e.message}")
        }
    }

    /**
     * Load token state with hash validation
     */
    private fun loadTokenState(conversationId: String, modelId: String) {
        if (conversationId.startsWith("private_")) return // Don't load private conversations
        
        try {
            val key = "conv_$conversationId"
            val tokens = encryptedPrefs.getInt("${key}_tokens", 0)
            val systemTokens = encryptedPrefs.getInt("${key}_system_tokens", 500)
            val continuedWarning = encryptedPrefs.getBoolean("${key}_continued_warning", false)
            val storedHash = encryptedPrefs.getString("${key}_hash", "")
            val storedModelId = encryptedPrefs.getString("${key}_model_id", "")
            val lastUpdate = encryptedPrefs.getLong("${key}_last_update", 0)
            val messageCount = encryptedPrefs.getInt("${key}_message_count", 0)
            
            // Validate hash if enabled
            if (HASH_VALIDATION_ENABLED && storedHash?.isNotEmpty() == true) {
                val expectedHash = generateTokenHash(conversationId, tokens, systemTokens, messageCount)
                if (storedHash != expectedHash) {
                    Timber.w("Token hash validation failed for $conversationId, resetting to defaults")
                    resetConversationTokens(conversationId, modelId)
                    return
                }
            }
            
            // Load state
            conversationTokenCount = tokens
            systemInstructionTokens = systemTokens
            continuedPastWarning = continuedWarning
            lastTokenUpdate = lastUpdate
            
            // Clear message token counts (will be rebuilt from messages if needed)
            messageTokenCounts.clear()
            
            Timber.d("Loaded secure token state for $conversationId: $conversationTokenCount tokens")
            
        } catch (e: Exception) {
            Timber.e(e, "Error loading token state: ${e.message}")
            resetConversationTokens(conversationId, modelId)
        }
    }

    /**
     * Reset conversation tokens to defaults
     */
    private fun resetConversationTokens(conversationId: String, modelId: String) {
        conversationTokenCount = 0
        systemInstructionTokens = 500
        continuedPastWarning = false
        messageTokenCounts.clear()
        lastTokenUpdate = System.currentTimeMillis()
        
        // Save clean state
        saveTokenState(conversationId, modelId)
        
        Timber.d("Reset token state for conversation $conversationId")
    }

    /**
     * Clean up old conversation data
     */
    private fun cleanupOldConversations() {
        try {
            val cutoffTime = System.currentTimeMillis() - (MAX_CONVERSATION_AGE_DAYS * 24 * 60 * 60 * 1000L)
            val allKeys = encryptedPrefs.all.keys
            val editor = encryptedPrefs.edit()
            
            var cleanedCount = 0
            for (key in allKeys) {
                if (key.startsWith("conv_") && key.endsWith("_last_update")) {
                    val lastUpdate = encryptedPrefs.getLong(key, 0)
                    if (lastUpdate < cutoffTime) {
                        val conversationKey = key.substringBefore("_last_update")
                        // Remove all related keys
                        editor.remove("${conversationKey}_tokens")
                        editor.remove("${conversationKey}_system_tokens")
                        editor.remove("${conversationKey}_continued_warning")
                        editor.remove("${conversationKey}_hash")
                        editor.remove("${conversationKey}_model_id")
                        editor.remove("${conversationKey}_last_update")
                        editor.remove("${conversationKey}_message_count")
                        cleanedCount++
                    }
                }
            }
            
            if (cleanedCount > 0) {
                editor.apply()
                Timber.d("Cleaned up $cleanedCount old conversation token states")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old conversations: ${e.message}")
        }
    }

    /**
     * Add token change listener
     */
    fun addTokenChangeListener(listener: TokenChangeListener) {
        tokenChangeListeners.add(listener)
    }

    /**
     * Remove token change listener
     */
    fun removeTokenChangeListener(listener: TokenChangeListener) {
        tokenChangeListeners.remove(listener)
    }

    /**
     * Notify listeners of token changes
     */
    private fun notifyTokenChangeListeners(modelId: String) {
        val isSubscribed = PrefsManager.isSubscribed(context)
        val (maxInputTokens, _) = getModelTokenLimits(modelId, isSubscribed)
        val totalTokens = conversationTokenCount + systemInstructionTokens
        val usagePercentage = totalTokens.toFloat() / maxInputTokens.toFloat()
        
        tokenChangeListeners.forEach { listener ->
            try {
                listener.onTokensChanged(
                    conversationTokenCount,
                    systemInstructionTokens,
                    totalTokens,
                    modelId,
                    isSubscribed,
                    maxInputTokens,
                    usagePercentage
                )
            } catch (e: Exception) {
                Timber.e(e, "Error notifying token change listener: ${e.message}")
            }
        }
    }

    /**
     * Notify listeners that max tokens have been reached
     */
    private fun notifyMaxTokensReached(conversationId: String, modelId: String) {
        tokenChangeListeners.forEach { listener ->
            try {
                listener.onMaxTokensReached(conversationId, modelId)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying max tokens reached listener: ${e.message}")
            }
        }
    }

    /**
     * Notify listeners that conversation must be reset
     */
    private fun notifyMustStartNewConversation(conversationId: String, reason: String) {
        tokenChangeListeners.forEach { listener ->
            try {
                listener.onConversationMustReset(conversationId, reason)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying conversation reset listener: ${e.message}")
            }
        }
    }

    /**
     * Set continued past warning flag
     */
    fun setContinuedPastWarning(continued: Boolean, modelId: String) {
        continuedPastWarning = continued
        currentConversationId?.let { saveTokenState(it, modelId) }
        Timber.d("Set continued past warning: $continued")
    }

    /**
     * Get current token usage information
     */
    fun getTokenUsageInfo(modelId: String): TokenUsageInfo {
        val isSubscribed = PrefsManager.isSubscribed(context)
        val (maxInputTokens, maxOutputTokens) = getModelTokenLimits(modelId, isSubscribed)
        val totalTokens = conversationTokenCount + systemInstructionTokens
        val usagePercentage = totalTokens.toFloat() / maxInputTokens.toFloat()
        
        return TokenUsageInfo(
            conversationTokens = conversationTokenCount,
            systemTokens = systemInstructionTokens,
            totalTokens = totalTokens,
            maxInputTokens = maxInputTokens,
            maxOutputTokens = maxOutputTokens,
            usagePercentage = usagePercentage,
            isSubscribed = isSubscribed,
            continuedPastWarning = continuedPastWarning,
            messageCount = messageTokenCounts.size
        )
    }

    data class TokenUsageInfo(
        val conversationTokens: Int,
        val systemTokens: Int,
        val totalTokens: Int,
        val maxInputTokens: Int,
        val maxOutputTokens: Int,
        val usagePercentage: Float,
        val isSubscribed: Boolean,
        val continuedPastWarning: Boolean,
        val messageCount: Int
    )

    /**
     * Force start new conversation
     */
    fun forceStartNewConversation(reason: String) {
        currentConversationId?.let { conversationId ->
            notifyMustStartNewConversation(conversationId, reason)
        }
    }

    /**
     * Reset current conversation
     */
    fun resetConversation(modelId: String) {
        currentConversationId?.let { conversationId ->
            resetConversationTokens(conversationId, modelId)
            notifyTokenChangeListeners(modelId)
        }
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        return """
            Conversation ID: $currentConversationId
            Conversation tokens: $conversationTokenCount
            System tokens: $systemInstructionTokens
            Total messages: ${messageTokenCounts.size}
            Continued past warning: $continuedPastWarning
            Last update: $lastTokenUpdate
            Install ID: $installId
        """.trimIndent()
    }
}