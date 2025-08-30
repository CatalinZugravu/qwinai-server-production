package com.cyberflux.qwinai.utils

import android.content.Context
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * CRITICAL FIX: Comprehensive Error Handling and Recovery System
 * 
 * Provides robust error handling and automatic recovery for streaming operations:
 * - Automatic retry mechanisms
 * - State corruption detection and repair
 * - Graceful degradation
 * - Recovery from various failure scenarios
 */
object StreamingRecoveryManager {
    
    private val recoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    private lateinit var context: Context
    
    // Recovery tracking
    private val recoveryAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastRecoveryAttempts = ConcurrentHashMap<String, AtomicLong>()
    private val corruptedStates = ConcurrentHashMap<String, CorruptionInfo>()
    
    // Configuration
    private const val MAX_RECOVERY_ATTEMPTS = 3
    private const val RECOVERY_COOLDOWN_MS = 30_000L // 30 seconds
    private const val STATE_CORRUPTION_THRESHOLD = 60_000L // 1 minute
    private const val MAX_CONTENT_INCONSISTENCY = 1000 // characters
    
    data class CorruptionInfo(
        val messageId: String,
        val detectedAt: Long,
        val corruptionType: CorruptionType,
        val description: String,
        val severity: Severity
    )
    
    enum class CorruptionType {
        STATE_MISMATCH,         // Different states across sources
        CONTENT_INCONSISTENCY,   // Content length/content mismatch
        TIMEOUT_CORRUPTION,     // Stuck in generating state too long
        SERVICE_DESYNC,         // Service and app state out of sync
        DATABASE_CORRUPTION,    // Database inconsistency
        MEMORY_CORRUPTION       // Memory issues affecting state
    }
    
    enum class Severity {
        LOW,        // Minor issue, can continue
        MEDIUM,     // Notable issue, needs attention
        HIGH,       // Major issue, needs immediate recovery
        CRITICAL    // System integrity at risk
    }
    
    data class RecoveryResult(
        val success: Boolean,
        val recoveryType: RecoveryType,
        val message: String,
        val restoredContent: String? = null
    )
    
    enum class RecoveryType {
        STATE_REPAIR,           // Fixed state inconsistency
        CONTENT_RESTORATION,    // Restored content from backup source
        SERVICE_RESTART,        // Restarted background service
        GRACEFUL_COMPLETION,    // Marked as completed gracefully
        FORCE_STOP,            // Stopped corrupted generation
        NO_RECOVERY_NEEDED      // State was actually fine
    }
    
    /**
     * Initialize the recovery manager
     */
    fun initialize(context: Context) {
        this.context = context
        database = AppDatabase.getDatabase(context)
        StreamingStateCoordinator.initialize(context)
        EnhancedProgressTracker.initialize(context)
        
        Timber.d("🛡️ StreamingRecoveryManager initialized")
        startCorruptionDetection()
    }
    
    /**
     * CRITICAL: Detect and recover from streaming corruption
     */
    suspend fun detectAndRecover(messageId: String): RecoveryResult {
        return try {
            Timber.d("🛡️ Starting corruption detection and recovery for: $messageId")
            
            // Step 1: Gather state from all sources
            val stateAnalysis = analyzeMessageState(messageId)
            
            // Step 2: Detect corruption
            val corruption = detectCorruption(stateAnalysis)
            
            if (corruption != null) {
                Timber.w("⚠️ Detected ${corruption.severity} corruption: ${corruption.description}")
                corruptedStates[messageId] = corruption
                
                // Step 3: Attempt recovery based on corruption type and severity
                performRecovery(messageId, corruption, stateAnalysis)
            } else {
                RecoveryResult(
                    success = true,
                    recoveryType = RecoveryType.NO_RECOVERY_NEEDED,
                    message = "No corruption detected"
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error during corruption detection and recovery: ${e.message}")
            RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Recovery failed: ${e.message}"
            )
        }
    }
    
    /**
     * Analyze message state across all sources
     */
    private suspend fun analyzeMessageState(messageId: String): StateAnalysis {
        return withContext(Dispatchers.IO) {
            val dbMessage = database.chatMessageDao().getMessageById(messageId)
            val streamingSession = UnifiedStreamingManager.getSession(messageId)
            val coordinatorState = StreamingStateCoordinator.getStreamingState(messageId)
            val progressState = EnhancedProgressTracker.getProgressState(messageId)
            
            StateAnalysis(
                messageId = messageId,
                dbMessage = dbMessage,
                streamingSession = streamingSession,
                coordinatorState = coordinatorState,
                progressState = progressState,
                analysisTime = System.currentTimeMillis()
            )
        }
    }
    
    data class StateAnalysis(
        val messageId: String,
        val dbMessage: ChatMessage?,
        val streamingSession: UnifiedStreamingManager.StreamingSession?,
        val coordinatorState: StreamingStateCoordinator.ConsolidatedStreamingState?,
        val progressState: EnhancedProgressTracker.ProgressState?,
        val analysisTime: Long
    )
    
    /**
     * Detect corruption based on state analysis
     */
    private fun detectCorruption(analysis: StateAnalysis): CorruptionInfo? {
        val messageId = analysis.messageId
        val now = System.currentTimeMillis()
        
        // Check for timeout corruption
        analysis.dbMessage?.let { dbMsg ->
            if (dbMsg.isGenerating) {
                val timeSinceLastUpdate = now - dbMsg.lastModified
                if (timeSinceLastUpdate > STATE_CORRUPTION_THRESHOLD) {
                    return CorruptionInfo(
                        messageId = messageId,
                        detectedAt = now,
                        corruptionType = CorruptionType.TIMEOUT_CORRUPTION,
                        description = "Message stuck in generating state for ${timeSinceLastUpdate}ms",
                        severity = if (timeSinceLastUpdate > 5 * 60 * 1000) Severity.HIGH else Severity.MEDIUM
                    )
                }
            }
        }
        
        // Check for content inconsistency
        if (analysis.dbMessage != null && analysis.coordinatorState != null) {
            val dbContentLength = analysis.dbMessage.message.length
            val coordinatorContentLength = analysis.coordinatorState.currentContent.length
            val contentDiff = kotlin.math.abs(dbContentLength - coordinatorContentLength)
            
            if (contentDiff > MAX_CONTENT_INCONSISTENCY) {
                return CorruptionInfo(
                    messageId = messageId,
                    detectedAt = now,
                    corruptionType = CorruptionType.CONTENT_INCONSISTENCY,
                    description = "Content length mismatch: DB=${dbContentLength}, Coordinator=${coordinatorContentLength}",
                    severity = if (contentDiff > 5000) Severity.HIGH else Severity.MEDIUM
                )
            }
        }
        
        // Check for state mismatch
        if (analysis.dbMessage != null && analysis.coordinatorState != null) {
            val dbGenerating = analysis.dbMessage.isGenerating
            val coordinatorGenerating = analysis.coordinatorState.isGenerating
            
            if (dbGenerating != coordinatorGenerating) {
                // Allow some tolerance for race conditions
                val stateStaleness = now - analysis.coordinatorState.lastUpdateTime
                if (stateStaleness > 10_000) { // 10 seconds
                    return CorruptionInfo(
                        messageId = messageId,
                        detectedAt = now,
                        corruptionType = CorruptionType.STATE_MISMATCH,
                        description = "Generating state mismatch: DB=${dbGenerating}, Coordinator=${coordinatorGenerating}",
                        severity = Severity.MEDIUM
                    )
                }
            }
        }
        
        // Check for service desync
        if (analysis.streamingSession != null && analysis.coordinatorState != null) {
            val sessionActive = analysis.streamingSession.isActive
            val coordinatorActive = analysis.coordinatorState.isGenerating
            
            if (sessionActive && !coordinatorActive) {
                return CorruptionInfo(
                    messageId = messageId,
                    detectedAt = now,
                    corruptionType = CorruptionType.SERVICE_DESYNC,
                    description = "Active session but coordinator shows inactive",
                    severity = Severity.MEDIUM
                )
            }
        }
        
        return null // No corruption detected
    }
    
    /**
     * Perform recovery based on corruption type
     */
    private suspend fun performRecovery(
        messageId: String,
        corruption: CorruptionInfo,
        analysis: StateAnalysis
    ): RecoveryResult {
        
        // Check recovery attempts limit
        val attempts = recoveryAttempts.computeIfAbsent(messageId) { AtomicInteger(0) }
        if (attempts.get() >= MAX_RECOVERY_ATTEMPTS) {
            return RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Max recovery attempts reached"
            )
        }
        
        // Check recovery cooldown
        val lastAttempt = lastRecoveryAttempts[messageId]?.get() ?: 0
        if (System.currentTimeMillis() - lastAttempt < RECOVERY_COOLDOWN_MS) {
            return RecoveryResult(
                success = false,
                recoveryType = RecoveryType.NO_RECOVERY_NEEDED,
                message = "Recovery cooldown active"
            )
        }
        
        attempts.incrementAndGet()
        lastRecoveryAttempts[messageId] = AtomicLong(System.currentTimeMillis())
        
        return when (corruption.corruptionType) {
            CorruptionType.TIMEOUT_CORRUPTION -> recoverTimeoutCorruption(messageId, analysis)
            CorruptionType.CONTENT_INCONSISTENCY -> recoverContentInconsistency(messageId, analysis)
            CorruptionType.STATE_MISMATCH -> recoverStateMismatch(messageId, analysis)
            CorruptionType.SERVICE_DESYNC -> recoverServiceDesync(messageId, analysis)
            CorruptionType.DATABASE_CORRUPTION -> recoverDatabaseCorruption(messageId, analysis)
            CorruptionType.MEMORY_CORRUPTION -> recoverMemoryCorruption(messageId, analysis)
        }
    }
    
    /**
     * Recover from timeout corruption
     */
    private suspend fun recoverTimeoutCorruption(
        messageId: String,
        analysis: StateAnalysis
    ): RecoveryResult = withContext(Dispatchers.IO) {
        
        val dbMessage = analysis.dbMessage ?: return@withContext RecoveryResult(
            success = false,
            recoveryType = RecoveryType.FORCE_STOP,
            message = "No database message found"
        )
        
        // If we have substantial content, mark as completed
        if (dbMessage.message.length > 100) {
            val completedMessage = dbMessage.copy(
                isGenerating = false,
                isLoading = false,
                showButtons = true,
                canContinueStreaming = false,
                lastModified = System.currentTimeMillis(),
                error = false
            )
            
            database.chatMessageDao().update(completedMessage)
            UnifiedStreamingManager.completeSession(messageId, true)
            EnhancedProgressTracker.markCompleted(messageId)
            
            return@withContext RecoveryResult(
                success = true,
                recoveryType = RecoveryType.GRACEFUL_COMPLETION,
                message = "Marked timeout message as completed with existing content",
                restoredContent = dbMessage.message
            )
        } else {
            // No substantial content, mark as failed
            val failedMessage = dbMessage.copy(
                isGenerating = false,
                isLoading = false,
                showButtons = true,
                canContinueStreaming = true,
                error = true,
                lastModified = System.currentTimeMillis()
            )
            
            database.chatMessageDao().update(failedMessage)
            UnifiedStreamingManager.completeSession(messageId, false, "Timeout recovery")
            EnhancedProgressTracker.markAsError(messageId, "Generation timeout")
            
            return@withContext RecoveryResult(
                success = true,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Stopped timeout message without substantial content"
            )
        }
    }
    
    /**
     * Recover from content inconsistency
     */
    private suspend fun recoverContentInconsistency(
        messageId: String,
        analysis: StateAnalysis
    ): RecoveryResult = withContext(Dispatchers.IO) {
        
        val dbMessage = analysis.dbMessage
        val coordinatorState = analysis.coordinatorState
        
        if (dbMessage == null || coordinatorState == null) {
            return@withContext RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Missing state for content recovery"
            )
        }
        
        // Use the source with more content as the authoritative source
        val (authoritativeContent, source) = if (coordinatorState.currentContent.length > dbMessage.message.length) {
            Pair(coordinatorState.currentContent, "coordinator")
        } else {
            Pair(dbMessage.message, "database")
        }
        
        // Update both sources to match
        val updatedMessage = dbMessage.copy(
            message = authoritativeContent,
            partialContent = authoritativeContent,
            lastModified = System.currentTimeMillis()
        )
        
        database.chatMessageDao().update(updatedMessage)
        UnifiedStreamingManager.updateSessionContent(messageId, authoritativeContent)
        
        return@withContext RecoveryResult(
            success = true,
            recoveryType = RecoveryType.CONTENT_RESTORATION,
            message = "Restored content consistency using $source as authoritative source",
            restoredContent = authoritativeContent
        )
    }
    
    /**
     * Recover from state mismatch
     */
    private suspend fun recoverStateMismatch(
        messageId: String,
        analysis: StateAnalysis
    ): RecoveryResult = withContext(Dispatchers.IO) {
        
        val dbMessage = analysis.dbMessage
        val coordinatorState = analysis.coordinatorState
        
        if (dbMessage == null || coordinatorState == null) {
            return@withContext RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Missing state for state recovery"
            )
        }
        
        // Use coordinator state as authoritative for generating status
        val updatedMessage = dbMessage.copy(
            isGenerating = coordinatorState.isGenerating,
            isLoading = coordinatorState.isGenerating && coordinatorState.currentContent.isEmpty(),
            showButtons = !coordinatorState.isGenerating,
            lastModified = System.currentTimeMillis()
        )
        
        database.chatMessageDao().update(updatedMessage)
        StreamingStateCoordinator.forceSync(messageId)
        
        return@withContext RecoveryResult(
            success = true,
            recoveryType = RecoveryType.STATE_REPAIR,
            message = "Repaired state mismatch using coordinator as authoritative source"
        )
    }
    
    /**
     * Recover from service desync
     */
    private suspend fun recoverServiceDesync(
        messageId: String,
        analysis: StateAnalysis
    ): RecoveryResult {
        
        // Try to restart background service connection
        return try {
            BackgroundAiService.startGeneration(context, messageId, analysis.dbMessage?.conversationId ?: "unknown")
            delay(2000) // Wait for service to start
            
            RecoveryResult(
                success = true,
                recoveryType = RecoveryType.SERVICE_RESTART,
                message = "Restarted background service to fix desync"
            )
        } catch (e: Exception) {
            RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Failed to restart service: ${e.message}"
            )
        }
    }
    
    /**
     * Recover from database corruption
     */
    private suspend fun recoverDatabaseCorruption(
        messageId: String,
        analysis: StateAnalysis
    ): RecoveryResult = withContext(Dispatchers.IO) {
        
        // Try to restore from streaming session or coordinator
        val restorationContent = analysis.streamingSession?.getPartialContent() 
            ?: analysis.coordinatorState?.currentContent
            ?: ""
        
        if (restorationContent.isNotEmpty()) {
            // Try to recreate database entry
            val restoredMessage = analysis.dbMessage?.copy(
                message = restorationContent,
                partialContent = restorationContent,
                lastModified = System.currentTimeMillis(),
                error = false
            ) ?: return@withContext RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "Cannot restore without original message"
            )
            
            database.chatMessageDao().update(restoredMessage)
            
            return@withContext RecoveryResult(
                success = true,
                recoveryType = RecoveryType.CONTENT_RESTORATION,
                message = "Restored database from streaming sources",
                restoredContent = restorationContent
            )
        } else {
            return@withContext RecoveryResult(
                success = false,
                recoveryType = RecoveryType.FORCE_STOP,
                message = "No content available for database restoration"
            )
        }
    }
    
    /**
     * Recover from memory corruption
     */
    private suspend fun recoverMemoryCorruption(
        messageId: String,
        analysis: StateAnalysis
    ): RecoveryResult {
        
        // Force memory cleanup and state refresh
        UnifiedStreamingManager.performMemoryCleanup()
        StreamingStateCoordinator.forceSync(messageId)
        
        // Force garbage collection
        System.gc()
        
        return RecoveryResult(
            success = true,
            recoveryType = RecoveryType.STATE_REPAIR,
            message = "Performed memory cleanup and state refresh"
        )
    }
    
    /**
     * Start periodic corruption detection
     */
    private fun startCorruptionDetection() {
        recoveryScope.launch {
            while (true) {
                delay(30_000) // Check every 30 seconds
                
                try {
                    performPeriodicCorruptionCheck()
                } catch (e: Exception) {
                    Timber.e(e, "Error in periodic corruption check: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Perform periodic corruption check
     */
    private suspend fun performPeriodicCorruptionCheck() {
        // Get all potentially active messages
        val activeStates = StreamingStateCoordinator.getAllStreamingStates()
        val generatingMessages = withContext(Dispatchers.IO) {
            database.chatMessageDao().getGeneratingMessages()
        }
        
        val allMessageIds = (activeStates.map { it.messageId } + generatingMessages.map { it.id }).distinct()
        
        allMessageIds.forEach { messageId ->
            recoveryScope.launch {
                try {
                    val result = detectAndRecover(messageId)
                    if (!result.success && result.recoveryType != RecoveryType.NO_RECOVERY_NEEDED) {
                        Timber.w("⚠️ Recovery failed for $messageId: ${result.message}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error checking message $messageId: ${e.message}")
                }
            }
        }
        
        // Clean up old recovery attempts
        cleanupOldRecoveryAttempts()
    }
    
    /**
     * Clean up old recovery attempts
     */
    private fun cleanupOldRecoveryAttempts() {
        val now = System.currentTimeMillis()
        val oldThreshold = now - (24 * 60 * 60 * 1000L) // 24 hours
        
        lastRecoveryAttempts.entries.removeAll { (_, lastAttempt) ->
            lastAttempt.get() < oldThreshold
        }
        
        recoveryAttempts.entries.removeAll { (messageId, _) ->
            !lastRecoveryAttempts.containsKey(messageId)
        }
        
        corruptedStates.entries.removeAll { (_, corruption) ->
            corruption.detectedAt < oldThreshold
        }
    }
    
    /**
     * Get recovery metrics
     */
    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "activeRecoveryAttempts" to recoveryAttempts.size,
            "corruptedStates" to corruptedStates.size,
            "totalRecoveryAttempts" to recoveryAttempts.values.sumOf { it.get() },
            "corruptionTypes" to corruptedStates.values.groupBy { it.corruptionType }.mapValues { it.value.size },
            "severityDistribution" to corruptedStates.values.groupBy { it.severity }.mapValues { it.value.size }
        )
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        recoveryAttempts.clear()
        lastRecoveryAttempts.clear()
        corruptedStates.clear()
        Timber.d("🧹 StreamingRecoveryManager cleaned up")
    }
}