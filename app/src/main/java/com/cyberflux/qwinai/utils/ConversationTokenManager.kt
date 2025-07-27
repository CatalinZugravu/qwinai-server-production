package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import androidx.core.content.edit
import com.cyberflux.qwinai.dao.ConversationDao
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.tokens.SecureTokenManager
import kotlinx.coroutines.delay
import org.json.JSONObject
import timber.log.Timber

/**
 * Enhanced ConversationTokenManager with persistence and proper synchronization
 * Now uses SecureTokenManager for secure token management
 */
class ConversationTokenManager(private val context: Context? = null) {
    // Legacy compatibility fields
    private var conversationTokenCount = 0
    private var systemInstructionTokens = 500
    private val messageTokenCounts = mutableMapOf<String, Int>()
    private var continuedPastWarning = false
    private val tokenChangeListeners = mutableListOf<TokenChangeListener>()
    private var currentConversationId: String? = null
    private val prefs: SharedPreferences? = context?.getSharedPreferences("token_manager", Context.MODE_PRIVATE)

    // New secure token manager
    private val secureTokenManager: SecureTokenManager? = context?.let { 
        SecureTokenManager.getInstance(it)
    }
    
    // Current model ID for secure token management
    private var currentModelId: String = "gpt-4o-mini" // Default model

    // Enhanced token change listener that includes secure token info
    private val secureTokenChangeListener = object : SecureTokenManager.TokenChangeListener {
        override fun onTokensChanged(
            conversationTokens: Int,
            systemTokens: Int,
            totalTokens: Int,
            modelId: String,
            isSubscribed: Boolean,
            maxTokens: Int,
            usagePercentage: Float
        ) {
            // Update legacy fields for compatibility
            conversationTokenCount = conversationTokens
            systemInstructionTokens = systemTokens
            
            // Notify legacy listeners
            notifyTokenChangeListeners()
        }

        override fun onMaxTokensReached(conversationId: String, modelId: String) {
            Timber.w("Max tokens reached for conversation $conversationId with model $modelId")
        }

        override fun onConversationMustReset(conversationId: String, reason: String) {
            Timber.w("Conversation $conversationId must be reset: $reason")
        }
    }

    init {
        // Register secure token change listener
        secureTokenManager?.addTokenChangeListener(secureTokenChangeListener)
    }

    // Constants for token reservation
    companion object {
        const val RESERVED_RESPONSE_PERCENTAGE = 0.20 // Reserve 20% for responses
        const val REDUCED_RESPONSE_PERCENTAGE = 0.10 // Reduced reservation after "Continue Anyway"
        const val COMPLEX_QUERY_RESERVED_PERCENTAGE = 0.35 // Reserve 35% for complex responses
        const val WARNING_THRESHOLD = 0.85 // Show warning at 85% usage
        const val CRITICAL_THRESHOLD = 0.95 // Hard limit at 95% usage (lowered from 98%)

        // Keywords that suggest complex queries needing more response space
        private val COMPLEX_QUERY_KEYWORDS = listOf(
            "explain", "generate", "code", "write", "create", "analyze",
            "compare", "summarize", "detailed", "step by step"
        )

        /**
         * Detect if a query is likely to need a complex response
         */
        fun isComplexQuery(text: String): Boolean {
            val lowerText = text.lowercase()
            return COMPLEX_QUERY_KEYWORDS.any { keyword ->
                lowerText.contains(keyword)
            }
        }
    }

    /**
     * Set the current conversation ID for persistence
     */
    fun setConversationId(conversationId: String) {
        Timber.d("Setting conversation ID: $conversationId (current: $currentConversationId)")

        // Use secure token manager if available
        if (secureTokenManager != null) {
            secureTokenManager.setConversationId(conversationId, currentModelId)
        } else {
            // Legacy implementation
            if (this.currentConversationId == conversationId) {
                loadTokenState()
                Timber.d("Reloaded same conversation state: $conversationTokenCount tokens")
                notifyTokenChangeListeners()
                return
            }

            if (this.currentConversationId != null) {
                saveTokenState()
                Timber.d("Saved token state for previous conversation: ${this.currentConversationId}")
            }

            this.currentConversationId = conversationId
            loadTokenState()
            Timber.d("Loaded token state for new conversation: $conversationId, tokens: $conversationTokenCount")
            notifyTokenChangeListeners()
        }
        
        // Update current conversation ID for legacy compatibility
        this.currentConversationId = conversationId
    }

    /**
     * Set the current model ID for secure token management
     */
    fun setModelId(modelId: String) {
        currentModelId = modelId
        Timber.d("Set model ID for token management: $modelId")
    }

    /**
     * Set system instruction tokens
     */
    fun setSystemInstructionTokens(tokens: Int) {
        systemInstructionTokens = tokens
        
        // Use secure token manager if available
        if (secureTokenManager != null) {
            secureTokenManager.setSystemInstructionTokens(tokens, currentModelId)
        } else {
            // Force UI update for legacy
            notifyTokenChangeListeners()
        }
    }

    fun getRawConversationTokens(): Int {
        return conversationTokenCount
    }

    interface TokenChangeListener {
        fun onTokensChanged(conversationTokens: Int, systemTokens: Int, totalTokens: Int)
    }

    fun addTokenChangeListener(listener: TokenChangeListener) {
        tokenChangeListeners.add(listener)
    }

    // NEW: Remove listener
    fun removeTokenChangeListener(listener: TokenChangeListener) {
        tokenChangeListeners.remove(listener)
    }

    // NEW: Notify listeners of token changes
    private fun notifyTokenChangeListeners() {
        tokenChangeListeners.forEach { listener ->
            listener.onTokensChanged(
                conversationTokenCount,
                systemInstructionTokens,
                conversationTokenCount + systemInstructionTokens
            )
        }
    }
    /**
     * Add a message to the token count with persistence
     */
    fun addMessage(message: ChatMessage): Int {
        // Use secure token manager if available
        if (secureTokenManager != null) {
            val success = secureTokenManager.addMessage(message, currentModelId)
            if (!success) {
                Timber.w("Secure token manager rejected message ${message.id}")
                return conversationTokenCount
            }
            
            // Update legacy fields for compatibility
            val tokenInfo = secureTokenManager.getTokenUsageInfo(currentModelId)
            conversationTokenCount = tokenInfo.conversationTokens
            systemInstructionTokens = tokenInfo.systemTokens
            
            return conversationTokenCount
        }

        // Legacy implementation
        if (messageTokenCounts.containsKey(message.id)) {
            Timber.w("Message ${message.id} already counted, skipping")
            return conversationTokenCount
        }

        val messageTokens = TokenValidator.estimateTokenCount(message.message)
        messageTokenCounts[message.id] = messageTokens
        conversationTokenCount += messageTokens

        Timber.d("Added message ${message.id}: $messageTokens tokens, total: $conversationTokenCount")

        validateConsistency()
        saveTokenState()
        notifyTokenChangeListeners()

        return conversationTokenCount
    }
    /**
     * Remove a message from the token count with persistence
     */
    fun removeMessage(messageId: String): Int {
        // Use secure token manager if available
        if (secureTokenManager != null) {
            val success = secureTokenManager.removeMessage(messageId, currentModelId)
            if (!success) {
                Timber.w("Secure token manager failed to remove message $messageId")
            }
            
            // Update legacy fields for compatibility
            val tokenInfo = secureTokenManager.getTokenUsageInfo(currentModelId)
            conversationTokenCount = tokenInfo.conversationTokens
            systemInstructionTokens = tokenInfo.systemTokens
            
            return conversationTokenCount
        }

        // Legacy implementation
        val tokens = messageTokenCounts.remove(messageId) ?: 0
        conversationTokenCount -= tokens
        conversationTokenCount = maxOf(0, conversationTokenCount)

        Timber.d("Removed message $messageId with $tokens tokens, total now: $conversationTokenCount")

        saveTokenState()
        notifyTokenChangeListeners()

        return conversationTokenCount
    }

    /**
     * Update tokens for a message (used for edits and reloads)
     */
    fun updateMessage(messageId: String, newContent: String): Int {
        // Use secure token manager if available
        if (secureTokenManager != null) {
            val success = secureTokenManager.updateMessage(messageId, newContent, currentModelId)
            if (!success) {
                Timber.w("Secure token manager failed to update message $messageId")
                return conversationTokenCount
            }
            
            // Update legacy fields for compatibility
            val tokenInfo = secureTokenManager.getTokenUsageInfo(currentModelId)
            conversationTokenCount = tokenInfo.conversationTokens
            systemInstructionTokens = tokenInfo.systemTokens
            
            return conversationTokenCount
        }

        // Legacy implementation
        val oldTokens = messageTokenCounts.remove(messageId) ?: 0
        conversationTokenCount -= oldTokens

        val newTokens = TokenValidator.estimateTokenCount(newContent)
        messageTokenCounts[messageId] = newTokens
        conversationTokenCount += newTokens
        conversationTokenCount = maxOf(0, conversationTokenCount)

        Timber.d("Updated message $messageId: $oldTokens -> $newTokens tokens, total: $conversationTokenCount")

        saveTokenState()
        notifyTokenChangeListeners()

        return conversationTokenCount
    }

    /**
     * Rebuild token count from scratch for a conversation
     */
    fun rebuildFromMessages(messages: List<ChatMessage>, conversationId: String) {
        Timber.d("Rebuilding token count for conversation $conversationId with ${messages.size} messages")

        // Set conversation ID
        val previousConversationId = currentConversationId
        this.currentConversationId = conversationId

        // Clear current state
        conversationTokenCount = 0
        messageTokenCounts.clear()

        // Add all messages
        val sortedMessages = messages.sortedBy { it.timestamp }
        for (message in sortedMessages) {
            if (!message.isGenerating && message.message.isNotBlank()) {
                val tokens = TokenValidator.estimateTokenCount(message.message)
                messageTokenCounts[message.id] = tokens
                conversationTokenCount += tokens
                Timber.d("Added to rebuild: message ${message.id} with $tokens tokens")
            }
        }

        Timber.d("Rebuilt token count: $conversationTokenCount tokens from ${messageTokenCounts.size} messages")

        // Validate and save
        validateConsistency()
        saveTokenState()

        // Notify listeners
        notifyTokenChangeListeners()

        // If we had a different conversation ID before, restore it after saving
        if (previousConversationId != null && previousConversationId != conversationId) {
            this.currentConversationId = previousConversationId
        }
    }
    /**
     * Update tokens for file content extraction
     */
    fun addFileTokens(tokens: Int) {
        conversationTokenCount += tokens
        Timber.d("Added $tokens file tokens, total now: $conversationTokenCount")
        saveTokenState()
    }

    /**
     * Check if adding this text would exceed token limits
     */
    fun wouldExceedLimit(inputText: String, modelId: String, isSubscribed: Boolean): Triple<Boolean, Int, Boolean> {
        // Use secure token manager if available
        if (secureTokenManager != null) {
            val validation = secureTokenManager.validateTokenOperation(
                SecureTokenManager.TokenOperation.SEND_MESSAGE,
                inputText,
                modelId,
                isSubscribed,
                currentConversationId
            )
            
            Timber.d("=== SECURE TOKEN VALIDATION ===")
            Timber.d("Model: $modelId (subscribed: $isSubscribed)")
            Timber.d("Input text: ${inputText.take(50)}...")
            Timber.d("Validation result: ${validation.reason}")
            Timber.d("Available tokens: ${validation.availableTokens}")
            Timber.d("Usage percentage: ${(validation.usagePercentage * 100).toInt()}%")
            Timber.d("Should show warning: ${validation.shouldShowWarning}")
            Timber.d("Must start new conversation: ${validation.mustStartNewConversation}")
            Timber.d("===============================")
            
            return Triple(
                validation.exceedsLimit || validation.mustStartNewConversation,
                validation.availableTokens,
                validation.shouldShowWarning
            )
        }

        // Legacy implementation
        val inputTokens = TokenValidator.estimateTokenCount(inputText)
        val totalModelTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)

        val effectiveSystemTokens = minOf(systemInstructionTokens, totalModelTokens / 3)

        val isComplex = isComplexQuery(inputText)
        val reservedPercentage = when {
            continuedPastWarning -> REDUCED_RESPONSE_PERCENTAGE
            isComplex -> COMPLEX_QUERY_RESERVED_PERCENTAGE
            else -> RESERVED_RESPONSE_PERCENTAGE
        }

        val reservedResponseTokens = (totalModelTokens * reservedPercentage).toInt()
        val usedTokens = conversationTokenCount + effectiveSystemTokens
        val availableTokens = totalModelTokens - usedTokens - reservedResponseTokens

        val almostFull = (usedTokens + inputTokens) > (totalModelTokens * WARNING_THRESHOLD)
        val exceedsHardLimit = (usedTokens + inputTokens) > (totalModelTokens * CRITICAL_THRESHOLD)

        Timber.d("=== LEGACY TOKEN LIMIT CHECK ===")
        Timber.d("Model: $modelId (subscribed: $isSubscribed)")
        Timber.d("Input tokens: $inputTokens")
        Timber.d("Conversation tokens: $conversationTokenCount")
        Timber.d("System tokens: $effectiveSystemTokens")
        Timber.d("Used tokens: $usedTokens")
        Timber.d("Reserved tokens: $reservedResponseTokens (${(reservedPercentage * 100).toInt()}%)")
        Timber.d("Total model tokens: $totalModelTokens")
        Timber.d("Available tokens: $availableTokens")
        Timber.d("Almost full: $almostFull (threshold: ${(WARNING_THRESHOLD * 100).toInt()}%)")
        Timber.d("Exceeds limit: $exceedsHardLimit (threshold: ${(CRITICAL_THRESHOLD * 100).toInt()}%)")
        Timber.d("===============================")

        return Triple(exceedsHardLimit || inputTokens > availableTokens, maxOf(0, availableTokens), almostFull)
    }

    /**
     * Get percentage of token limit used
     */
    fun getTokenUsagePercentage(modelId: String, isSubscribed: Boolean): Float {
        val totalModelTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
        return (conversationTokenCount + systemInstructionTokens).toFloat() / totalModelTokens.toFloat()
    }

    /**
     * Reset token counter for new conversation
     */
    fun reset() {
        conversationTokenCount = 0
        messageTokenCounts.clear()
        continuedPastWarning = false

        // Clear persisted state for current conversation
        clearPersistedState()

        // Notify listeners
        notifyTokenChangeListeners()

        Timber.d("Reset conversation token counter")
    }

    /**
     * Get total tokens including system instructions
     */
    fun getTotalTokens(): Int {
        return conversationTokenCount + systemInstructionTokens
    }

    /**
     * Get just conversation tokens (without system instructions)
     */
    fun getConversationTokens(): Int {
        return conversationTokenCount
    }

    /**
     * Set continued past warning flag
     */
    fun setContinuedPastWarning(continued: Boolean) {
        continuedPastWarning = continued
        
        // Use secure token manager if available
        if (secureTokenManager != null) {
            secureTokenManager.setContinuedPastWarning(continued, currentModelId)
        } else {
            saveTokenState()
        }
        
        Timber.d("Set continuedPastWarning = $continued")
    }

    /**
     * Check if user has continued past warning
     */
    fun hasContinuedPastWarning(): Boolean {
        return continuedPastWarning
    }

    fun ensureTokenStateLoaded(conversationId: String?) {
        if (conversationId != null && conversationId == currentConversationId) {
            // Simply reload the state for the current conversation
            loadTokenState()
            Timber.d("Restored token state for current conversation: $conversationId, tokens: $conversationTokenCount")
        }
    }
    /**
     * Log current state for debugging
     */
    fun logCurrentState(context: String = "") {
        Timber.d("=== TOKEN MANAGER STATE ($context) ===")
        Timber.d("Conversation ID: $currentConversationId")
        Timber.d("Conversation tokens: $conversationTokenCount")
        Timber.d("System tokens: $systemInstructionTokens")
        Timber.d("Total tracked messages: ${messageTokenCounts.size}")
        Timber.d("Continued past warning: $continuedPastWarning")

        var totalCalculated = 0
        messageTokenCounts.forEach { (id, tokens) ->
            totalCalculated += tokens
            Timber.d("Message $id: $tokens tokens")
        }
        Timber.d("Calculated total: $totalCalculated (should match $conversationTokenCount)")
        Timber.d("=====================================")
    }

    /**
     * Get the appropriate reserved percentage based on conversation state
     */
    fun getReservedPercentage(inputText: String): Double {
        val isComplex = isComplexQuery(inputText)
        return if (continuedPastWarning) {
            REDUCED_RESPONSE_PERCENTAGE
        } else if (isComplex) {
            COMPLEX_QUERY_RESERVED_PERCENTAGE
        } else {
            RESERVED_RESPONSE_PERCENTAGE
        }
    }

    /**
     * Validate internal consistency
     */
    private fun validateConsistency() {
        val calculatedTotal = messageTokenCounts.values.sum()
        if (calculatedTotal != conversationTokenCount) {
            Timber.e("Token count inconsistency! Stored: $conversationTokenCount, Calculated: $calculatedTotal")
            conversationTokenCount = calculatedTotal // Fix the inconsistency
        }
    }
    fun getMessageCount(): Int {
        return messageTokenCounts.size
    }

    /**
     * Get system tokens value
     */
    fun getSystemTokens(): Int {
        return systemInstructionTokens
    }
    /**
     * Clear persisted state for current conversation
     */
    private fun clearPersistedState() {
        if (prefs == null || currentConversationId == null) return

        try {
            val key = "tokens_$currentConversationId"
            prefs.edit {
                remove("${key}_count")
                remove("${key}_warning")
                remove("${key}_messages")
                remove("${key}_timestamp")
            }

            Timber.d("Cleared persisted token state for conversation $currentConversationId")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing persisted state: ${e.message}")
        }
    }
    fun forceLoadTokenStateForConversation(conversationId: String) {
        val oldConversationId = this.currentConversationId
        this.currentConversationId = conversationId
        loadTokenState()
        Timber.d("Forced loading token state for $conversationId: $conversationTokenCount tokens")

        // Restore previous conversation ID if different
        if (oldConversationId != null && oldConversationId != conversationId) {
            this.currentConversationId = oldConversationId
        }

        // Notify listeners
        notifyTokenChangeListeners()
    }

    fun clearTokenStateForConversation(conversationId: String) {
        if (prefs == null) return

        try {
            val key = "tokens_$conversationId"
            prefs.edit {
                remove("${key}_count")
                remove("${key}_warning")
                remove("${key}_messages")
                remove("${key}_timestamp")
                apply()
            }

            // If this is the current conversation, also reset in-memory state
            if (conversationId == currentConversationId) {
                conversationTokenCount = 0
                messageTokenCounts.clear()
                continuedPastWarning = false
                notifyTokenChangeListeners()
            }

            Timber.d("Cleared token state for conversation $conversationId")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing token state for conversation $conversationId")
        }
    }
    /**
     * Save token state to database with improved error handling and retry mechanism
     */
    /**
     * Save token state to database with improved error handling and retry mechanism
     */
    suspend fun saveTokenStateToDatabase(
        conversationId: String,
        conversationDao: ConversationDao,
        retryCount: Int = 3
    ): Boolean {
        if (conversationId.startsWith("private_")) return false // Don't save private conversations

        for (attempt in 1..retryCount) {
            try {
                // Validate token count for consistency before saving
                validateConsistency()

                // Room handles transactions internally for individual operations
                // Update database with token information
                conversationDao.updateTokenInfo(
                    conversationId = conversationId,
                    tokenCount = conversationTokenCount,
                    continuedPastWarning = continuedPastWarning,
                    timestamp = System.currentTimeMillis()
                )

                Timber.d("Saved token state to database for conversation $conversationId: " +
                        "$conversationTokenCount tokens, ${messageTokenCounts.size} messages")

                return true

            } catch (e: Exception) {
                Timber.e(e, "Error saving token state to database (attempt $attempt): ${e.message}")

                // Only retry on specific exceptions that might be transient
                if (e is SQLiteException || e is SQLiteDatabaseLockedException) {
                    if (attempt < retryCount) {
                        // Exponential backoff delay
                        val delayMs = 100L * (1 shl (attempt - 1))
                        Timber.d("Retrying database save after $delayMs ms")
                        delay(delayMs)
                        continue
                    }
                }

                return false
            }
        }

        return false
    }    /**
     * Load token state from database
     */
    suspend fun loadTokenStateFromDatabase(conversationId: String, conversationDao: ConversationDao): Boolean {
        if (conversationId.startsWith("private_")) return false // Don't load private conversations

        try {
            val tokenCount = conversationDao.getTokenCount(conversationId)
            val continuedWarning = conversationDao.getContinuedPastWarning(conversationId)

            if (tokenCount != null) {
                this.conversationTokenCount = tokenCount
                this.continuedPastWarning = continuedWarning ?: false

                // Also update current conversation ID
                this.currentConversationId = conversationId

                Timber.d("Loaded token state from database: $conversationId has $tokenCount tokens")
                notifyTokenChangeListeners()
                return true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading token state from database: ${e.message}")
        }

        return false
    }
    private fun saveTokenState() {
        if (prefs == null || currentConversationId == null) return

        try {
            val key = "tokens_$currentConversationId"
            prefs.edit {
                putInt("${key}_count", conversationTokenCount)
                putBoolean("${key}_warning", continuedPastWarning)

                // Save message token counts as a simple string
                val messageTokensString = messageTokenCounts.entries.joinToString("|") { "${it.key}:${it.value}" }
                putString("${key}_messages", messageTokensString)

                putLong("${key}_timestamp", System.currentTimeMillis())
                apply()  // Using apply() instead of commit() for performance
            }

            Timber.d("Saved token state for conversation $currentConversationId: $conversationTokenCount tokens")
        } catch (e: Exception) {
            Timber.e(e, "Error saving token state: ${e.message}")
        }
    }

    // IMPROVED: Load token state with better error handling
    private fun loadTokenState() {
        if (prefs == null || currentConversationId == null) return

        try {
            val key = "tokens_$currentConversationId"
            conversationTokenCount = prefs.getInt("${key}_count", 0)
            continuedPastWarning = prefs.getBoolean("${key}_warning", false)

            // Load message token counts
            messageTokenCounts.clear()
            val messageTokensString = prefs.getString("${key}_messages", "") ?: ""
            if (messageTokensString.isNotEmpty()) {
                messageTokensString.split("|").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        try {
                            messageTokenCounts[parts[0]] = parts[1].toInt()
                        } catch (e: NumberFormatException) {
                            Timber.e("Error parsing token count for message ${parts[0]}")
                        }
                    }
                }
            }

            val timestamp = prefs.getLong("${key}_timestamp", 0)
            val age = System.currentTimeMillis() - timestamp

            Timber.d("Loaded token state for conversation $currentConversationId: $conversationTokenCount tokens (age: ${age / 1000}s)")

            // If data is older than 24 hours, consider it stale but still use it
            if (age > 24 * 60 * 60 * 1000) {
                Timber.w("Token state is older than 24 hours, may need refresh")
            }

            // Validate consistency after loading
            validateConsistency()

        } catch (e: Exception) {
            Timber.e(e, "Error loading token state: ${e.message}")
            // Reset to clean state on error
            conversationTokenCount = 0
            messageTokenCounts.clear()
            continuedPastWarning = false
        }
    }

    /**
     * Check if current conversation is approaching token limits
     */
    fun checkTokenLimits(modelId: String, isSubscribed: Boolean): Pair<Boolean, Float> {
        val percentage = getTokenUsagePercentage(modelId, isSubscribed)
        val shouldShowWarning = percentage >= WARNING_THRESHOLD && !continuedPastWarning

        return Pair(shouldShowWarning, percentage)
    }

    /**
     * Get secure token usage information for advanced UI
     */
    fun getSecureTokenUsageInfo(): SecureTokenManager.TokenUsageInfo? {
        return secureTokenManager?.getTokenUsageInfo(currentModelId)
    }

    /**
     * Force start new conversation when tokens are exceeded
     */
    fun forceStartNewConversation(reason: String) {
        if (secureTokenManager != null) {
            secureTokenManager.forceStartNewConversation(reason)
        } else {
            // Legacy: just reset
            reset()
        }
    }

    /**
     * Reset current conversation
     */
    fun resetConversation() {
        if (secureTokenManager != null) {
            secureTokenManager.resetConversation(currentModelId)
        } else {
            reset()
        }
    }

    /**
     * Cleanup method to prevent memory leaks
     */
    fun cleanup() {
        // Remove secure token change listener
        secureTokenManager?.removeTokenChangeListener(secureTokenChangeListener)
        
        // Clear legacy listeners
        tokenChangeListeners.clear()
        
        // Clear legacy state
        messageTokenCounts.clear()
        
        Timber.d("ConversationTokenManager cleanup completed")
    }

    /**
     * Get debug information including secure token manager info
     */
    fun getEnhancedDebugInfo(): String {
        val legacyInfo = """
            === Legacy Token Manager ===
            Conversation ID: $currentConversationId
            Model ID: $currentModelId
            Conversation tokens: $conversationTokenCount
            System tokens: $systemInstructionTokens
            Total messages: ${messageTokenCounts.size}
            Continued past warning: $continuedPastWarning
        """.trimIndent()
        
        val secureInfo = secureTokenManager?.getDebugInfo() ?: "SecureTokenManager not available"
        
        return "$legacyInfo\n\n=== Secure Token Manager ===\n$secureInfo"
    }
}