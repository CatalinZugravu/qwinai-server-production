package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.database.AppDatabase
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
 * CRITICAL FIX: Enhanced Progress Tracking System
 * 
 * Provides unified progress tracking across all streaming sources:
 * - Real-time progress estimates
 * - Cross-system state synchronization
 * - Progress event broadcasting
 * - Recovery from interrupted states
 */
object EnhancedProgressTracker {
    
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    
    // Progress tracking state
    private val progressStates = ConcurrentHashMap<String, ProgressState>()
    private val progressListeners = mutableMapOf<String, MutableList<ProgressListener>>()
    
    // Performance metrics
    private val trackingStartTimes = ConcurrentHashMap<String, Long>()
    private val lastProgressUpdates = ConcurrentHashMap<String, Long>()
    
    data class ProgressState(
        val messageId: String,
        val conversationId: String,
        val currentProgress: Float, // 0.0 to 1.0
        val estimatedTimeRemaining: Long, // milliseconds
        val currentPhase: ProgressPhase,
        val contentLength: Int,
        val isStalled: Boolean = false,
        val errorState: String? = null,
        val lastUpdate: Long = System.currentTimeMillis(),
        val throughputCharPerSec: Float = 0f,
        val qualityScore: Float = 1.0f // 0.0 to 1.0, measures progress reliability
    )
    
    enum class ProgressPhase {
        INITIALIZING,
        FIRST_TOKEN_RECEIVED,
        ACTIVE_STREAMING,
        BACKGROUND_PROCESSING,
        FINALIZING,
        COMPLETED,
        FAILED,
        STALLED,
        RECOVERING
    }
    
    interface ProgressListener {
        fun onProgressUpdate(messageId: String, progressState: ProgressState)
        fun onPhaseChange(messageId: String, oldPhase: ProgressPhase, newPhase: ProgressPhase)
        fun onError(messageId: String, error: String)
        fun onCompleted(messageId: String, finalState: ProgressState)
    }
    
    /**
     * Initialize the progress tracker
     */
    fun initialize(context: Context) {
        if (!::database.isInitialized) {
            database = AppDatabase.getDatabase(context)
            StreamingStateCoordinator.initialize(context)
            Timber.d("🎯 EnhancedProgressTracker initialized")
            startProgressMonitoring()
        }
    }
    
    /**
     * Start tracking progress for a message
     */
    fun startTracking(messageId: String, conversationId: String) {
        val initialState = ProgressState(
            messageId = messageId,
            conversationId = conversationId,
            currentProgress = 0.0f,
            estimatedTimeRemaining = -1L, // Unknown
            currentPhase = ProgressPhase.INITIALIZING,
            contentLength = 0
        )
        
        progressStates[messageId] = initialState
        trackingStartTimes[messageId] = System.currentTimeMillis()
        lastProgressUpdates[messageId] = System.currentTimeMillis()
        
        Timber.d("🎯 Started tracking progress for message: $messageId")
        notifyProgressUpdate(messageId, initialState)
    }
    
    /**
     * Update progress based on streaming state coordinator data
     */
    fun updateProgress(messageId: String) {
        trackerScope.launch {
            try {
                val streamingState = StreamingStateCoordinator.getStreamingState(messageId)
                if (streamingState != null) {
                    val currentState = progressStates[messageId]
                    if (currentState != null) {
                        val updatedState = calculateProgressState(currentState, streamingState)
                        progressStates[messageId] = updatedState
                        lastProgressUpdates[messageId] = System.currentTimeMillis()
                        
                        withContext(Dispatchers.Main) {
                            notifyProgressUpdate(messageId, updatedState)
                            
                            // Check for phase changes
                            if (currentState.currentPhase != updatedState.currentPhase) {
                                notifyPhaseChange(messageId, currentState.currentPhase, updatedState.currentPhase)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating progress for $messageId: ${e.message}")
                markAsError(messageId, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Calculate comprehensive progress state from streaming coordinator data
     */
    private fun calculateProgressState(
        currentState: ProgressState,
        streamingState: StreamingStateCoordinator.ConsolidatedStreamingState
    ): ProgressState {
        
        val now = System.currentTimeMillis()
        val trackingStartTime = trackingStartTimes[streamingState.messageId] ?: now
        val elapsedTime = now - trackingStartTime
        
        // Calculate throughput (characters per second)
        val throughput = if (elapsedTime > 0) {
            (streamingState.currentContent.length * 1000f) / elapsedTime
        } else {
            currentState.throughputCharPerSec
        }
        
        // Estimate time remaining based on current progress and throughput
        val estimatedTimeRemaining = if (streamingState.estimatedProgress > 0 && throughput > 0) {
            val remainingProgress = 1.0f - streamingState.estimatedProgress
            val estimatedRemainingChars = (streamingState.currentContent.length / streamingState.estimatedProgress) * remainingProgress
            (estimatedRemainingChars / throughput * 1000).toLong()
        } else {
            -1L // Unknown
        }
        
        // Convert streaming coordinator phase to progress phase
        val progressPhase = when (streamingState.generationPhase) {
            StreamingStateCoordinator.GenerationPhase.INITIALIZING -> ProgressPhase.INITIALIZING
            StreamingStateCoordinator.GenerationPhase.STREAMING -> {
                if (streamingState.currentContent.isEmpty()) {
                    ProgressPhase.INITIALIZING
                } else if (streamingState.currentContent.length < 50) {
                    ProgressPhase.FIRST_TOKEN_RECEIVED
                } else {
                    ProgressPhase.ACTIVE_STREAMING
                }
            }
            StreamingStateCoordinator.GenerationPhase.BACKGROUND -> ProgressPhase.BACKGROUND_PROCESSING
            StreamingStateCoordinator.GenerationPhase.COMPLETING -> ProgressPhase.FINALIZING
            StreamingStateCoordinator.GenerationPhase.COMPLETED -> ProgressPhase.COMPLETED
            StreamingStateCoordinator.GenerationPhase.PAUSED -> ProgressPhase.STALLED
            StreamingStateCoordinator.GenerationPhase.ERROR -> ProgressPhase.FAILED
            StreamingStateCoordinator.GenerationPhase.UNKNOWN -> currentState.currentPhase
        }
        
        // Detect stalled state
        val isStalled = !streamingState.isGenerating && 
                       streamingState.generationPhase != StreamingStateCoordinator.GenerationPhase.COMPLETED &&
                       (now - streamingState.lastUpdateTime) > 30_000 // 30 seconds
        
        // Calculate quality score based on various factors
        val qualityScore = calculateQualityScore(streamingState, throughput, elapsedTime)
        
        return currentState.copy(
            currentProgress = streamingState.estimatedProgress,
            estimatedTimeRemaining = estimatedTimeRemaining,
            currentPhase = if (isStalled) ProgressPhase.STALLED else progressPhase,
            contentLength = streamingState.currentContent.length,
            isStalled = isStalled,
            throughputCharPerSec = throughput,
            qualityScore = qualityScore,
            lastUpdate = now
        )
    }
    
    /**
     * Calculate quality score for progress tracking reliability
     */
    private fun calculateQualityScore(
        streamingState: StreamingStateCoordinator.ConsolidatedStreamingState,
        throughput: Float,
        elapsedTime: Long
    ): Float {
        var score = 1.0f
        
        // Reduce score if content source is unreliable
        when (streamingState.contentSource) {
            StreamingStateCoordinator.ContentSource.DATABASE -> score *= 0.9f // Good but not real-time
            StreamingStateCoordinator.ContentSource.STREAMING_SESSION -> score *= 1.0f // Best
            StreamingStateCoordinator.ContentSource.BACKGROUND_SERVICE -> score *= 0.8f // Decent
            StreamingStateCoordinator.ContentSource.UNIFIED_MANAGER -> score *= 0.7f // Potentially stale
        }
        
        // Reduce score for very low throughput
        if (throughput < 10f && elapsedTime > 10_000) {
            score *= 0.6f
        }
        
        // Reduce score for very old updates
        val updateAge = System.currentTimeMillis() - streamingState.lastUpdateTime
        if (updateAge > 60_000) { // 1 minute
            score *= 0.4f
        } else if (updateAge > 30_000) { // 30 seconds
            score *= 0.7f
        }
        
        return score.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Add progress listener
     */
    fun addProgressListener(messageId: String, listener: ProgressListener) {
        progressListeners.computeIfAbsent(messageId) { mutableListOf() }.add(listener)
        
        // Immediately notify with current state if available
        progressStates[messageId]?.let { state ->
            listener.onProgressUpdate(messageId, state)
        }
    }
    
    /**
     * Remove progress listener
     */
    fun removeProgressListener(messageId: String, listener: ProgressListener) {
        progressListeners[messageId]?.remove(listener)
        if (progressListeners[messageId]?.isEmpty() == true) {
            progressListeners.remove(messageId)
        }
    }
    
    /**
     * Get current progress state
     */
    fun getProgressState(messageId: String): ProgressState? {
        return progressStates[messageId]
    }
    
    /**
     * Mark message as completed
     */
    fun markCompleted(messageId: String) {
        val currentState = progressStates[messageId]
        if (currentState != null) {
            val completedState = currentState.copy(
                currentProgress = 1.0f,
                currentPhase = ProgressPhase.COMPLETED,
                estimatedTimeRemaining = 0L,
                isStalled = false,
                lastUpdate = System.currentTimeMillis()
            )
            progressStates[messageId] = completedState
            
            notifyCompleted(messageId, completedState)
        }
    }
    
    /**
     * Mark message as failed
     */
    fun markAsError(messageId: String, error: String) {
        val currentState = progressStates[messageId]
        if (currentState != null) {
            val errorState = currentState.copy(
                currentPhase = ProgressPhase.FAILED,
                errorState = error,
                isStalled = false,
                lastUpdate = System.currentTimeMillis()
            )
            progressStates[messageId] = errorState
            
            notifyError(messageId, error)
        }
    }
    
    /**
     * Stop tracking progress for a message
     */
    fun stopTracking(messageId: String) {
        progressStates.remove(messageId)
        trackingStartTimes.remove(messageId)
        lastProgressUpdates.remove(messageId)
        progressListeners.remove(messageId)
        Timber.d("🎯 Stopped tracking progress for message: $messageId")
    }
    
    /**
     * Start periodic progress monitoring
     */
    private fun startProgressMonitoring() {
        trackerScope.launch {
            while (true) {
                delay(2000) // Update every 2 seconds
                
                try {
                    val activeMessages = progressStates.keys.toList()
                    activeMessages.forEach { messageId ->
                        updateProgress(messageId)
                    }
                    
                    // Clean up completed or stale tracking
                    cleanupStaleTracking()
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error in progress monitoring loop: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Clean up stale progress tracking
     */
    private fun cleanupStaleTracking() {
        val now = System.currentTimeMillis()
        val staleThreshold = 10 * 60 * 1000L // 10 minutes
        
        val staleMessages = progressStates.filter { (_, state) ->
            state.currentPhase == ProgressPhase.COMPLETED ||
            (now - state.lastUpdate) > staleThreshold
        }
        
        staleMessages.keys.forEach { messageId ->
            stopTracking(messageId)
        }
        
        if (staleMessages.isNotEmpty()) {
            Timber.d("🧹 Cleaned up ${staleMessages.size} stale progress tracking entries")
        }
    }
    
    /**
     * Notification methods
     */
    private fun notifyProgressUpdate(messageId: String, state: ProgressState) {
        progressListeners[messageId]?.forEach { listener ->
            try {
                listener.onProgressUpdate(messageId, state)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying progress listener: ${e.message}")
            }
        }
    }
    
    private fun notifyPhaseChange(messageId: String, oldPhase: ProgressPhase, newPhase: ProgressPhase) {
        Timber.d("🎯 Phase change for $messageId: $oldPhase -> $newPhase")
        progressListeners[messageId]?.forEach { listener ->
            try {
                listener.onPhaseChange(messageId, oldPhase, newPhase)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying phase change listener: ${e.message}")
            }
        }
    }
    
    private fun notifyError(messageId: String, error: String) {
        progressListeners[messageId]?.forEach { listener ->
            try {
                listener.onError(messageId, error)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying error listener: ${e.message}")
            }
        }
    }
    
    private fun notifyCompleted(messageId: String, finalState: ProgressState) {
        progressListeners[messageId]?.forEach { listener ->
            try {
                listener.onCompleted(messageId, finalState)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying completion listener: ${e.message}")
            }
        }
    }
    
    /**
     * Get performance metrics
     */
    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "activeTracking" to progressStates.size,
            "totalListeners" to progressListeners.values.sumOf { it.size },
            "avgThroughput" to (progressStates.values.map { it.throughputCharPerSec }.average().takeIf { !it.isNaN() } ?: 0.0),
            "avgQualityScore" to (progressStates.values.map { it.qualityScore }.average().takeIf { !it.isNaN() } ?: 0.0),
            "phaseDistribution" to progressStates.values.groupBy { it.currentPhase }.mapValues { it.value.size },
            "stalledCount" to progressStates.values.count { it.isStalled }
        )
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        progressStates.clear()
        trackingStartTimes.clear()
        lastProgressUpdates.clear()
        progressListeners.clear()
        Timber.d("🧹 EnhancedProgressTracker cleaned up")
    }
}