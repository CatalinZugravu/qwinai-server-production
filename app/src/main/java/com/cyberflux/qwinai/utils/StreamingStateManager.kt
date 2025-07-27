package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages streaming states across activity lifecycle changes
 * Enables continuation of streaming without re-requesting content
 */
object StreamingStateManager {
    
    private const val PREFS_NAME = "streaming_states"
    private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    private const val MAX_CONTENT_LENGTH = 50_000 // Limit content to prevent OOM
    private const val MAX_ACTIVE_SESSIONS = 10 // Limit concurrent sessions
    
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // In-memory active streaming sessions
    private val activeStreams = ConcurrentHashMap<String, StreamingSession>()
    
    data class StreamingSession(
        val messageId: String,
        val conversationId: String,
        val sessionId: String,
        val modelId: String,
        val partialContent: StringBuilder = StringBuilder(),
        val startTime: Long = System.currentTimeMillis(),
        var isActive: Boolean = true,
        var lastUpdateTime: Long = System.currentTimeMillis(),
        var hasWebSearchResults: Boolean = false,
        var webSearchContent: String = "",
        var isBackgroundActive: Boolean = false
    ) {
        fun appendContent(content: String) {
            // CRITICAL: Prevent OOM by limiting content size
            val newLength = partialContent.length + content.length
            if (newLength > MAX_CONTENT_LENGTH) {
                // Keep only the last 80% to maintain context
                val keepLength = (MAX_CONTENT_LENGTH * 0.8).toInt()
                val currentContent = partialContent.toString()
                val startIndex = currentContent.length - keepLength
                partialContent.clear()
                partialContent.append(currentContent.substring(maxOf(0, startIndex)))
                Timber.w("Trimmed streaming content to prevent OOM: ${partialContent.length} chars")
            }
            partialContent.append(content)
            lastUpdateTime = System.currentTimeMillis()
        }
        
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - lastUpdateTime > SESSION_TIMEOUT_MS
        }
        
        fun getPartialContent(): String = partialContent.toString()
    }
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // CRITICAL FIX: Restore active sessions from SharedPreferences
        restoreActiveSessionsFromPrefs()
        
        cleanupExpiredSessions()
        Timber.d("🔄 StreamingStateManager initialized with ${activeStreams.size} restored sessions")
    }
    
    /**
     * Start a new streaming session
     */
    fun startStreamingSession(
        messageId: String,
        conversationId: String,
        modelId: String,
        sessionId: String = generateSessionId()
    ): StreamingSession {
        // CRITICAL: Prevent memory leaks by limiting active sessions
        if (activeStreams.size >= MAX_ACTIVE_SESSIONS) {
            // Remove oldest expired sessions first
            val expiredSessions = activeStreams.values.filter { it.isExpired() }
            expiredSessions.forEach { removeStreamingSession(it.messageId) }
            
            // If still too many, remove oldest active session
            if (activeStreams.size >= MAX_ACTIVE_SESSIONS) {
                val oldestSession = activeStreams.values.minByOrNull { it.startTime }
                oldestSession?.let { removeStreamingSession(it.messageId) }
                Timber.w("Removed oldest session to prevent memory issues")
            }
        }
        val session = StreamingSession(
            messageId = messageId,
            conversationId = conversationId,
            sessionId = sessionId,
            modelId = modelId
        )
        
        activeStreams[messageId] = session
        persistSessionToPrefs(session)
        
        Timber.d("🚀 Started streaming session: $sessionId for message: $messageId")
        return session
    }
    
    /**
     * Get existing streaming session or null if not found/expired
     */
    fun getStreamingSession(messageId: String): StreamingSession? {
        val session = activeStreams[messageId] ?: loadSessionFromPrefs(messageId)
        
        if (session != null && session.isExpired()) {
            removeStreamingSession(messageId)
            return null
        }
        
        return session
    }
    
    /**
     * Update streaming content - saves progress for continuation
     */
    fun updateStreamingContent(messageId: String, content: String) {
        val session = activeStreams[messageId]
        if (session != null) {
            // CRITICAL: Limit individual update size to prevent OOM
            val safeContent = if (content.length > 10_000) {
                Timber.w("Large content update truncated to prevent OOM")
                content.takeLast(10_000) // Keep the end which is most recent
            } else {
                content
            }
            
            session.appendContent(safeContent)
            // Persist major updates to prefs (but limit frequency)
            if (safeContent.length > 50 && System.currentTimeMillis() - session.lastUpdateTime > 1000) {
                persistSessionToPrefs(session)
            }
            Timber.v("📝 Updated streaming content for $messageId: ${safeContent.length} chars")
        }
    }
    
    /**
     * Set partial content (for initial load from database)
     */
    fun setPartialContent(messageId: String, content: String) {
        val session = activeStreams[messageId]
        if (session != null) {
            session.partialContent.clear()
            session.partialContent.append(content)
            session.lastUpdateTime = System.currentTimeMillis()
            persistSessionToPrefs(session)
            Timber.d("📝 Set initial partial content for $messageId: ${content.length} chars")
        }
    }
    
    /**
     * Mark streaming session as background active
     */
    fun markAsBackgroundActive(messageId: String) {
        val session = activeStreams[messageId]
        if (session != null) {
            session.isBackgroundActive = true
            persistSessionToPrefs(session)
            Timber.d("📍 Marked streaming session as background active: $messageId")
        }
    }
    
    /**
     * Complete streaming session
     */
    fun completeStreamingSession(messageId: String, finalContent: String) {
        val session = activeStreams[messageId]
        if (session != null) {
            session.isActive = false
            removeStreamingSession(messageId)
            Timber.d("✅ Completed streaming session for $messageId: ${finalContent.length} final chars")
        }
    }
    
    /**
     * Remove streaming session (expired or completed)
     */
    fun removeStreamingSession(messageId: String) {
        activeStreams.remove(messageId)
        removeSessionFromPrefs(messageId)
        Timber.d("🗑️ Removed streaming session: $messageId")
    }
    
    /**
     * Check if message can continue streaming
     */
    fun canContinueStreaming(messageId: String): Boolean {
        val session = getStreamingSession(messageId)
        return session != null && session.isActive && !session.isExpired()
    }
    
    /**
     * Get all active streaming sessions
     */
    fun getActiveStreamingSessions(): List<StreamingSession> {
        return activeStreams.values.filter { it.isActive && !it.isExpired() }
    }
    
    /**
     * Check if there are any background active sessions
     */
    fun hasBackgroundActiveSessions(): Boolean {
        return activeStreams.values.any { it.isActive && it.isBackgroundActive && !it.isExpired() }
    }
    
    /**
     * Get active streaming sessions for a specific conversation
     */
    fun getActiveSessionsForConversation(conversationId: String): List<StreamingSession> {
        return activeStreams.values.filter { 
            it.conversationId == conversationId && it.isActive && !it.isExpired() 
        }
    }
    
    /**
     * Get the latest active streaming message for a conversation
     */
    fun getLatestActiveMessageForConversation(conversationId: String): StreamingSession? {
        return getActiveSessionsForConversation(conversationId)
            .maxByOrNull { it.lastUpdateTime }
    }
    
    /**
     * Check if a conversation has any active streaming sessions
     */
    fun hasActiveStreamingInConversation(conversationId: String): Boolean {
        return getActiveSessionsForConversation(conversationId).isNotEmpty()
    }
    
    private fun generateSessionId(): String {
        return "stream_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun persistSessionToPrefs(session: StreamingSession) {
        scope.launch {
            try {
                // CRITICAL: Limit content size when persisting to prevent SharedPrefs bloat
                val contentToSave = session.getPartialContent().let { content ->
                    if (content.length > MAX_CONTENT_LENGTH) {
                        content.takeLast((MAX_CONTENT_LENGTH * 0.8).toInt())
                    } else {
                        content
                    }
                }
                
                prefs.edit()
                    .putString("${session.messageId}_sessionId", session.sessionId)
                    .putString("${session.messageId}_conversationId", session.conversationId)
                    .putString("${session.messageId}_modelId", session.modelId)
                    .putString("${session.messageId}_content", contentToSave)
                    .putLong("${session.messageId}_startTime", session.startTime)
                    .putLong("${session.messageId}_lastUpdate", session.lastUpdateTime)
                    .putBoolean("${session.messageId}_isActive", session.isActive)
                    .putBoolean("${session.messageId}_backgroundActive", session.isBackgroundActive)
                    .putString("${session.messageId}_webSearchContent", session.webSearchContent.take(5000)) // Limit web search content
                    .putBoolean("${session.messageId}_hasWebSearchResults", session.hasWebSearchResults)
                    .apply()
                
                Timber.v("💾 Persisted streaming session: ${session.messageId}")
            } catch (e: Exception) {
                Timber.e(e, "Error persisting streaming session: ${e.message}")
            }
        }
    }
    
    private fun loadSessionFromPrefs(messageId: String): StreamingSession? {
        return try {
            val sessionId = prefs.getString("${messageId}_sessionId", null) ?: return null
            val conversationId = prefs.getString("${messageId}_conversationId", null) ?: return null
            val modelId = prefs.getString("${messageId}_modelId", null) ?: return null
            val content = prefs.getString("${messageId}_content", "") ?: ""
            val startTime = prefs.getLong("${messageId}_startTime", 0L)
            val lastUpdate = prefs.getLong("${messageId}_lastUpdate", 0L)
            val isActive = prefs.getBoolean("${messageId}_isActive", false)
            val backgroundActive = prefs.getBoolean("${messageId}_backgroundActive", false)
            val webSearchContent = prefs.getString("${messageId}_webSearchContent", "") ?: ""
            val hasWebSearchResults = prefs.getBoolean("${messageId}_hasWebSearchResults", false)
            
            val session = StreamingSession(
                messageId = messageId,
                conversationId = conversationId,
                sessionId = sessionId,
                modelId = modelId,
                startTime = startTime,
                isActive = isActive,
                lastUpdateTime = lastUpdate,
                webSearchContent = webSearchContent,
                hasWebSearchResults = hasWebSearchResults,
                isBackgroundActive = backgroundActive
            )
            
            session.partialContent.append(content)
            activeStreams[messageId] = session
            
            Timber.d("📂 Loaded streaming session from prefs: $messageId")
            session
        } catch (e: Exception) {
            Timber.e(e, "Error loading streaming session from prefs: ${e.message}")
            null
        }
    }
    
    private fun removeSessionFromPrefs(messageId: String) {
        scope.launch {
            try {
                prefs.edit()
                    .remove("${messageId}_sessionId")
                    .remove("${messageId}_conversationId")
                    .remove("${messageId}_modelId")
                    .remove("${messageId}_content")
                    .remove("${messageId}_startTime")
                    .remove("${messageId}_lastUpdate")
                    .remove("${messageId}_isActive")
                    .remove("${messageId}_backgroundActive")
                    .remove("${messageId}_webSearchContent")
                    .remove("${messageId}_hasWebSearchResults")
                    .apply()
                
                Timber.v("🗑️ Removed streaming session from prefs: $messageId")
            } catch (e: Exception) {
                Timber.e(e, "Error removing streaming session from prefs: ${e.message}")
            }
        }
    }
    
    private fun cleanupExpiredSessions() {
        scope.launch {
            try {
                val expiredSessions = activeStreams.values.filter { it.isExpired() }
                expiredSessions.forEach { session ->
                    removeStreamingSession(session.messageId)
                }
                
                // CRITICAL: Additional memory cleanup
                if (activeStreams.size > MAX_ACTIVE_SESSIONS / 2) {
                    val oldSessions = activeStreams.values
                        .sortedBy { it.lastUpdateTime }
                        .take(activeStreams.size - MAX_ACTIVE_SESSIONS / 2)
                    oldSessions.forEach { removeStreamingSession(it.messageId) }
                    Timber.d("🧹 Additional cleanup: removed ${oldSessions.size} old sessions")
                }
                
                if (expiredSessions.isNotEmpty()) {
                    Timber.d("🧹 Cleaned up ${expiredSessions.size} expired streaming sessions")
                }
                
                // Force garbage collection after cleanup
                System.gc()
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up expired sessions: ${e.message}")
            }
        }
    }
    
    /**
     * CRITICAL FIX: Restore active sessions from SharedPreferences
     * This is essential for reconnecting to background generations after app restart
     */
    private fun restoreActiveSessionsFromPrefs() {
        try {
            val allKeys = prefs.all.keys
            val messageIds = allKeys.filter { it.endsWith("_sessionId") }
                .map { it.substring(0, it.lastIndexOf("_sessionId")) }
                .distinct()
            
            Timber.d("🔄 Found ${messageIds.size} potential sessions in SharedPreferences")
            
            messageIds.forEach { messageId ->
                try {
                    val session = loadSessionFromPrefs(messageId)
                    if (session != null && session.isActive && !session.isExpired()) {
                        activeStreams[messageId] = session
                        Timber.d("✅ Restored active session: $messageId with ${session.getPartialContent().length} chars")
                    } else if (session != null && session.isExpired()) {
                        Timber.d("🗑️ Removing expired session during restore: $messageId")
                        removeSessionFromPrefs(messageId)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error restoring session $messageId: ${e.message}")
                    // Clean up corrupted session data
                    removeSessionFromPrefs(messageId)
                }
            }
            
            Timber.d("🔄 Successfully restored ${activeStreams.size} active sessions")
            
        } catch (e: Exception) {
            Timber.e(e, "Error restoring sessions from SharedPreferences: ${e.message}")
        }
    }
}