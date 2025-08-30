package com.cyberflux.qwinai.utils

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages streaming cancellation tokens and session cleanup
 * Minimal implementation for streaming cancellation management
 */
object StreamingCancellationManager {
    
    private val activeSessions = ConcurrentHashMap<String, StreamingSession>()
    
    data class StreamingSession(
        val streamId: String,
        val startTime: Long = System.currentTimeMillis(),
        val isCancelled: AtomicBoolean = AtomicBoolean(false)
    ) {
        fun cancel() {
            isCancelled.set(true)
        }
    }
    
    fun createStreamingSession(sessionId: String): StreamingSession {
        val session = StreamingSession(sessionId)
        activeSessions[sessionId] = session
        Timber.d("🔄 Created streaming session: $sessionId")
        return session
    }
    
    fun cancelStream(streamId: String, reason: String) {
        activeSessions[streamId]?.let { session ->
            session.cancel()
            Timber.d("❌ Cancelled stream $streamId: $reason")
            
            // Give a brief moment for cancellation to propagate
            Thread.sleep(50)
        }
        activeSessions.remove(streamId)
    }
    
    fun cancelAllStreams(reason: String) {
        val sessionCount = activeSessions.size
        activeSessions.values.forEach { it.cancel() }
        activeSessions.clear()
        Timber.d("❌ Cancelled all $sessionCount streams: $reason")
    }
    
    fun isStreamCancelled(streamId: String): Boolean {
        return activeSessions[streamId]?.isCancelled?.get() ?: true
    }
    
    fun getActiveSessionCount(): Int {
        return activeSessions.size
    }
}