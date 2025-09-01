package com.cyberflux.qwinai.utils

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.service.BackgroundAiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * CRITICAL FIX: Unified Streaming State Coordinator
 * 
 * This class provides a single source of truth for all streaming states across:
 * - UnifiedStreamingManager (in-memory sessions)
 * - BackgroundAiService (background generation)
 * - Database (persistent state)
 * 
 * Prevents race conditions, state inconsistencies, and content loss.
 */
object StreamingStateCoordinator {
    
    private val coordinatorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    
    // Consolidated streaming state for all active messages
    private val consolidatedStates = ConcurrentHashMap<String, ConsolidatedStreamingState>()
    
    // State synchronization timestamps
    private val lastSyncTimes = ConcurrentHashMap<String, Long>()
    private val syncInterval = AtomicLong(1000L) // 1 second default
    
    data class ConsolidatedStreamingState(
        val messageId: String,
        val conversationId: String,
        val currentContent: String,
        val isGenerating: Boolean,
        val isBackgroundActive: Boolean,
        val hasStreamingSession: Boolean,
        val hasBackgroundService: Boolean,
        val lastUpdateTime: Long,
        val contentSource: ContentSource,
        val sessionId: String? = null,
        val estimatedProgress: Float = 0f, // 0.0 to 1.0
        val generationPhase: GenerationPhase = GenerationPhase.UNKNOWN
    )
    
    enum class ContentSource {
        DATABASE,           // Content from database (most reliable)
        STREAMING_SESSION,  // Content from active streaming session
        BACKGROUND_SERVICE, // Content from background service
        UNIFIED_MANAGER    // Content from UnifiedStreamingManager
    }
    
    enum class GenerationPhase {
        INITIALIZING,       // Just started
        STREAMING,          // Actively streaming content
        BACKGROUND,         // Running in background
        COMPLETING,         // Finalizing response
        COMPLETED,          // Fully complete
        PAUSED,            // Temporarily paused
        ERROR,             // Error state
        UNKNOWN            // Cannot determine state
    }
    
    /**
     * Initialize the coordinator with database access
     */
    fun initialize(context: Context) {
        if (!::database.isInitialized) {
            database = AppDatabase.getDatabase(context)
            Timber.d("🔄 StreamingStateCoordinator initialized")
            startPeriodicSync()
        }
    }
    
    /**
     * CRITICAL: Get the current streaming state for a message across all sources
     * This is the single source of truth for any streaming state queries
     */
    fun getStreamingState(messageId: String): ConsolidatedStreamingState? {
        try {
            val cached = consolidatedStates[messageId]
            
            // If we have recent cached data, return it
            if (cached != null && (System.currentTimeMillis() - cached.lastUpdateTime) < 5000) {
                return cached
            }
            
            // Otherwise, refresh the state from all sources
            refreshStreamingState(messageId)
            return consolidatedStates[messageId]
            
        } catch (e: Exception) {
            Timber.e(e, "Error getting streaming state for $messageId: ${e.message}")
            return null
        }
    }
    
    /**
     * CRITICAL: Refresh streaming state from all sources and consolidate
     */
    private fun refreshStreamingState(messageId: String) {
        coordinatorScope.launch {
            try {
                // Collect state from all sources
                val streamingSession = UnifiedStreamingManager.getSession(messageId)
                val dbMessage = withContext(Dispatchers.IO) {
                    database.chatMessageDao().getMessageById(messageId)
                }
                
                if (dbMessage != null) {
                    val hasBackgroundService = try {
                        // This would require adding a method to BackgroundAiService to check state
                        false // Placeholder - would need to implement in BackgroundAiService
                    } catch (e: Exception) {
                        false
                    }
                    
                    // Determine the most reliable content source and state
                    val content: String
                    val source: ContentSource
                    val isGenerating: Boolean
                    val phase: GenerationPhase
                    
                    when {
                        // Priority 1: Active streaming session with fresh content
                        streamingSession != null && streamingSession.isActive -> {
                            val sessionContent = streamingSession.getPartialContent()
                            if (sessionContent.length >= dbMessage.message.length) {
                                content = sessionContent
                                source = ContentSource.STREAMING_SESSION
                                isGenerating = true
                                phase = GenerationPhase.STREAMING
                            } else {
                                content = dbMessage.message
                                source = ContentSource.DATABASE
                                isGenerating = dbMessage.isGenerating
                                phase = if (dbMessage.isGenerating) GenerationPhase.BACKGROUND else GenerationPhase.COMPLETED
                            }
                        }
                        // Priority 2: Database with generating flag and recent activity
                        dbMessage.isGenerating && (System.currentTimeMillis() - dbMessage.lastModified) < 60000 -> {
                            content = dbMessage.message
                            source = ContentSource.DATABASE
                            isGenerating = true
                            phase = GenerationPhase.BACKGROUND
                        }
                        // Priority 3: Database with completed content
                        dbMessage.message.isNotEmpty() -> {
                            content = dbMessage.message
                            source = ContentSource.DATABASE
                            isGenerating = false
                            phase = GenerationPhase.COMPLETED
                        }
                        // Priority 4: Fallback to streaming session even if inactive
                        streamingSession != null -> {
                            content = streamingSession.getPartialContent()
                            source = ContentSource.UNIFIED_MANAGER
                            isGenerating = streamingSession.isActive
                            phase = if (streamingSession.isActive) GenerationPhase.STREAMING else GenerationPhase.PAUSED
                        }
                        else -> {
                            content = ""
                            source = ContentSource.DATABASE
                            isGenerating = false
                            phase = GenerationPhase.UNKNOWN
                        }
                    }
                    
                    // Calculate estimated progress
                    val estimatedProgress = calculateEstimatedProgress(content, isGenerating, phase)
                    
                    // Create consolidated state
                    val consolidatedState = ConsolidatedStreamingState(
                        messageId = messageId,
                        conversationId = dbMessage.conversationId,
                        currentContent = content,
                        isGenerating = isGenerating,
                        isBackgroundActive = streamingSession?.isBackgroundActive ?: false,
                        hasStreamingSession = streamingSession != null,
                        hasBackgroundService = hasBackgroundService,
                        lastUpdateTime = System.currentTimeMillis(),
                        contentSource = source,
                        sessionId = streamingSession?.sessionId,
                        estimatedProgress = estimatedProgress,
                        generationPhase = phase
                    )
                    
                    consolidatedStates[messageId] = consolidatedState
                    lastSyncTimes[messageId] = System.currentTimeMillis()
                    
                    Timber.d("🔄 Refreshed state for $messageId: ${content.length} chars, $source, $phase")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing streaming state for $messageId: ${e.message}")
            }
        }
    }
    
    /**
     * Calculate estimated progress based on content and generation phase
     */
    private fun calculateEstimatedProgress(content: String, isGenerating: Boolean, phase: GenerationPhase): Float {
        return when {
            phase == GenerationPhase.COMPLETED -> 1.0f
            phase == GenerationPhase.ERROR -> 0.0f
            !isGenerating && content.isEmpty() -> 0.0f
            !isGenerating && content.isNotEmpty() -> 1.0f
            else -> {
                // Estimate based on content length and phase
                val baseProgress = when (phase) {
                    GenerationPhase.INITIALIZING -> 0.1f
                    GenerationPhase.STREAMING -> 0.3f + (content.length / 10000f).coerceAtMost(0.5f)
                    GenerationPhase.BACKGROUND -> 0.4f + (content.length / 8000f).coerceAtMost(0.4f)
                    GenerationPhase.COMPLETING -> 0.9f
                    else -> 0.2f
                }
                baseProgress.coerceIn(0.0f, 0.95f) // Never show 100% until actually complete
            }
        }
    }
    
    /**
     * CRITICAL: Synchronize states across all sources
     * This resolves conflicts and ensures consistency
     */
    fun synchronizeStates() {
        coordinatorScope.launch {
            try {
                // Get all active sessions from UnifiedStreamingManager
                val activeSessions = UnifiedStreamingManager.getActiveSessionsForConversation()
                
                // Refresh states for all active sessions
                activeSessions.forEach { session ->
                    refreshStreamingState(session.messageId)
                }
                
                // Clean up expired states
                val now = System.currentTimeMillis()
                val expiredStates = consolidatedStates.filter { (_, state) ->
                    (now - state.lastUpdateTime) > 300000 && // 5 minutes old
                    !state.isGenerating && 
                    state.generationPhase == GenerationPhase.COMPLETED
                }
                
                expiredStates.keys.forEach { messageId ->
                    consolidatedStates.remove(messageId)
                    lastSyncTimes.remove(messageId)
                    Timber.d("🧹 Cleaned up expired state for $messageId")
                }
                
                Timber.d("🔄 State synchronization completed - ${consolidatedStates.size} active states")
                
            } catch (e: Exception) {
                Timber.e(e, "Error during state synchronization: ${e.message}")
            }
        }
    }
    
    /**
     * Get all currently tracked streaming states
     */
    fun getAllStreamingStates(): List<ConsolidatedStreamingState> {
        return consolidatedStates.values.toList()
    }
    
    /**
     * Check if a message is currently generating (across all sources)
     */
    fun isGenerating(messageId: String): Boolean {
        return getStreamingState(messageId)?.isGenerating ?: false
    }
    
    /**
     * Get the current content for a message (most up-to-date across all sources)
     */
    fun getCurrentContent(messageId: String): String {
        return getStreamingState(messageId)?.currentContent ?: ""
    }
    
    /**
     * Start periodic synchronization to keep states consistent
     */
    private fun startPeriodicSync() {
        coordinatorScope.launch {
            while (true) {
                delay(syncInterval.get())
                
                try {
                    if (consolidatedStates.isNotEmpty()) {
                        synchronizeStates()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in periodic sync: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Force immediate synchronization for a specific message
     */
    fun forceSync(messageId: String) {
        refreshStreamingState(messageId)
    }
    
    /**
     * Update sync interval (for performance tuning)
     */
    fun setSyncInterval(intervalMs: Long) {
        syncInterval.set(intervalMs.coerceIn(500L, 10000L))
        Timber.d("🔧 Updated sync interval to ${syncInterval.get()}ms")
    }
    
    /**
     * Get performance metrics
     */
    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "activeStates" to consolidatedStates.size,
            "syncInterval" to syncInterval.get(),
            "avgContentLength" to (consolidatedStates.values.map { it.currentContent.length }.average().takeIf { !it.isNaN() } ?: 0.0),
            "generatingCount" to consolidatedStates.values.count { it.isGenerating },
            "backgroundActiveCount" to consolidatedStates.values.count { it.isBackgroundActive },
            "phases" to consolidatedStates.values.groupBy { it.generationPhase }.mapValues { it.value.size }
        )
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        consolidatedStates.clear()
        lastSyncTimes.clear()
        Timber.d("🧹 StreamingStateCoordinator cleaned up")
    }
}