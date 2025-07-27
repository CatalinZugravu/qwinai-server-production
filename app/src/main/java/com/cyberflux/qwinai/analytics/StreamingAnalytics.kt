package com.cyberflux.qwinai.analytics

import android.content.Context
import com.cyberflux.qwinai.utils.StreamingConfig
import com.cyberflux.qwinai.utils.StreamingPerformanceMonitor
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Production-ready analytics and logging for streaming operations
 * Provides detailed insights for monitoring, debugging, and optimization
 */
object StreamingAnalytics {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isInitialized = false
    
    // Event tracking
    private val eventCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val eventTimings = ConcurrentHashMap<String, AtomicLong>()
    private val customMetrics = ConcurrentHashMap<String, Any>()
    
    // Session analytics
    private val sessionData = ConcurrentHashMap<String, SessionAnalytics>()
    
    data class SessionAnalytics(
        val sessionId: String,
        val startTime: Long = System.currentTimeMillis(),
        var endTime: Long? = null,
        val events: MutableList<AnalyticsEvent> = mutableListOf(),
        val metrics: MutableMap<String, Any> = mutableMapOf(),
        var wasSuccessful: Boolean = false,
        var errorMessage: String? = null
    )
    
    data class AnalyticsEvent(
        val eventName: String,
        val timestamp: Long = System.currentTimeMillis(),
        val properties: Map<String, Any> = emptyMap(),
        val category: EventCategory = EventCategory.GENERAL
    )
    
    enum class EventCategory {
        GENERAL,
        PERFORMANCE,
        ERROR,
        USER_INTERACTION,
        SYSTEM,
        STREAMING,
        BACKGROUND
    }
    
    // Event names - production standardized
    object Events {
        // Session events
        const val STREAMING_SESSION_STARTED = "streaming_session_started"
        const val STREAMING_SESSION_ENDED = "streaming_session_ended"
        const val BACKGROUND_SESSION_STARTED = "background_session_started"
        const val BACKGROUND_SESSION_CONTINUED = "background_session_continued"
        
        // Content events
        const val CONTENT_CHUNK_PROCESSED = "content_chunk_processed"
        const val MARKDOWN_PROCESSED = "markdown_processed"
        const val CODE_BLOCK_PROCESSED = "code_block_processed"
        
        // Performance events
        const val PERFORMANCE_WARNING = "performance_warning"
        const val PERFORMANCE_CRITICAL = "performance_critical"
        const val MEMORY_WARNING = "memory_warning"
        const val CIRCUIT_BREAKER_OPENED = "circuit_breaker_opened"
        const val CIRCUIT_BREAKER_CLOSED = "circuit_breaker_closed"
        
        // Error events
        const val STREAMING_ERROR = "streaming_error"
        const val CONTINUATION_FAILED = "continuation_failed"
        const val UI_UPDATE_FAILED = "ui_update_failed"
        
        // User events
        const val CONVERSATION_OPENED = "conversation_opened"
        const val STREAMING_INTERRUPTED = "streaming_interrupted"
        const val FEATURE_DISABLED = "feature_disabled"
    }
    
    /**
     * Initialize analytics system
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            isInitialized = true
            
            // Start periodic analytics reporting
            scope.launch {
                while (isInitialized) {
                    try {
                        // Log analytics summary every 10 minutes in debug mode
                        if (StreamingConfig.isDebugModeEnabled) {
                            logAnalyticsSummary()
                        }
                        
                        // Clean up old sessions
                        cleanupOldSessions()
                        
                        kotlinx.coroutines.delay(10 * 60 * 1000L) // 10 minutes
                    } catch (e: Exception) {
                        Timber.e(e, "Error in analytics reporting: ${e.message}")
                        kotlinx.coroutines.delay(30 * 60 * 1000L) // Wait longer on error
                    }
                }
            }
            
            Timber.d("✅ StreamingAnalytics initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize StreamingAnalytics: ${e.message}")
        }
    }
    
    /**
     * Track a streaming event
     */
    fun trackEvent(
        eventName: String,
        properties: Map<String, Any> = emptyMap(),
        category: EventCategory = EventCategory.GENERAL,
        sessionId: String? = null
    ) {
        if (!isInitialized) return
        
        try {
            // Update event counts
            eventCounts.computeIfAbsent(eventName) { AtomicInteger(0) }.incrementAndGet()
            
            val event = AnalyticsEvent(eventName, properties = properties, category = category)
            
            // Add to session if specified
            sessionId?.let { id ->
                sessionData[id]?.events?.add(event)
            }
            
            // Log in debug mode
            if (StreamingConfig.isDebugModeEnabled) {
                val propsStr = if (properties.isNotEmpty()) " props=$properties" else ""
                Timber.d("📊 Analytics: $eventName [$category]$propsStr")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error tracking event: ${e.message}")
        }
    }
    
    /**
     * Start analytics session
     */
    fun startSession(sessionId: String, sessionType: String = "streaming") {
        if (!isInitialized) return
        
        try {
            val session = SessionAnalytics(sessionId)
            sessionData[sessionId] = session
            
            trackEvent(
                Events.STREAMING_SESSION_STARTED,
                mapOf(
                    "session_id" to sessionId,
                    "session_type" to sessionType,
                    "timestamp" to System.currentTimeMillis()
                ),
                EventCategory.STREAMING,
                sessionId
            )
            
            Timber.d("📊 Analytics session started: $sessionId")
        } catch (e: Exception) {
            Timber.e(e, "Error starting analytics session: ${e.message}")
        }
    }
    
    /**
     * End analytics session
     */
    fun endSession(sessionId: String, wasSuccessful: Boolean = true, errorMessage: String? = null) {
        if (!isInitialized) return
        
        try {
            sessionData[sessionId]?.let { session ->
                session.endTime = System.currentTimeMillis()
                session.wasSuccessful = wasSuccessful
                session.errorMessage = errorMessage
                
                val duration = session.endTime!! - session.startTime
                
                trackEvent(
                    Events.STREAMING_SESSION_ENDED,
                    mapOf(
                        "session_id" to sessionId,
                        "duration_ms" to duration,
                        "was_successful" to wasSuccessful,
                        "event_count" to session.events.size,
                        "error_message" to (errorMessage ?: "none")
                    ),
                    EventCategory.STREAMING,
                    sessionId
                )
                
                Timber.d("📊 Analytics session ended: $sessionId (${duration}ms, success=$wasSuccessful)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error ending analytics session: ${e.message}")
        }
    }
    
    /**
     * Track timing metrics
     */
    fun trackTiming(metricName: String, durationMs: Long, sessionId: String? = null) {
        if (!isInitialized) return
        
        try {
            eventTimings.computeIfAbsent(metricName) { AtomicLong(0) }.addAndGet(durationMs)
            
            sessionId?.let { id ->
                sessionData[id]?.metrics?.put("${metricName}_ms", durationMs)
            }
            
            if (StreamingConfig.isDebugModeEnabled) {
                Timber.v("⏱️ Timing: $metricName = ${durationMs}ms")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error tracking timing: ${e.message}")
        }
    }
    
    /**
     * Track performance metrics
     */
    fun trackPerformanceMetrics(sessionId: String? = null) {
        if (!isInitialized) return
        
        try {
            val performanceReport = StreamingPerformanceMonitor.getProductionReport()
            val metrics = performanceReport["metrics"] as? Map<String, Any> ?: return
            
            trackEvent(
                "performance_snapshot",
                metrics,
                EventCategory.PERFORMANCE,
                sessionId
            )
            
            // Track specific performance alerts
            val alerts = StreamingPerformanceMonitor.getActiveAlerts()
            if (alerts.isNotEmpty()) {
                trackEvent(
                    Events.PERFORMANCE_WARNING,
                    mapOf("alerts" to alerts),
                    EventCategory.PERFORMANCE,
                    sessionId
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error tracking performance metrics: ${e.message}")
        }
    }
    
    /**
     * Track error with context
     */
    fun trackError(
        error: Throwable,
        context: String = "unknown",
        sessionId: String? = null,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        if (!isInitialized) return
        
        try {
            val errorData = mutableMapOf<String, Any>(
                "error_class" to error.javaClass.simpleName,
                "error_message" to (error.message ?: "no message"),
                "context" to context,
                "stack_trace" to error.stackTraceToString().take(1000) // Limit size
            )
            errorData.putAll(additionalData)
            
            trackEvent(
                Events.STREAMING_ERROR,
                errorData,
                EventCategory.ERROR,
                sessionId
            )
            
            // End session with error if specified
            sessionId?.let { id ->
                endSession(id, wasSuccessful = false, errorMessage = error.message)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error tracking error: ${e.message}")
        }
    }
    
    /**
     * Track user interaction
     */
    fun trackUserInteraction(
        action: String,
        element: String,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        if (!isInitialized) return
        
        val data = mutableMapOf<String, Any>(
            "action" to action,
            "element" to element
        )
        data.putAll(additionalData)
        
        trackEvent(
            "user_interaction",
            data,
            EventCategory.USER_INTERACTION
        )
    }
    
    /**
     * Get analytics summary
     */
    fun getAnalyticsSummary(): Map<String, Any> {
        try {
            val totalEvents = eventCounts.values.sumOf { it.get() }
            val activeSessions = sessionData.values.count { it.endTime == null }
            val completedSessions = sessionData.values.count { it.endTime != null }
            val successfulSessions = sessionData.values.count { it.wasSuccessful }
            
            return mapOf(
                "total_events" to totalEvents,
                "active_sessions" to activeSessions,
                "completed_sessions" to completedSessions,
                "successful_sessions" to successfulSessions,
                "success_rate" to if (completedSessions > 0) {
                    (successfulSessions.toDouble() / completedSessions * 100).toInt()
                } else 0,
                "top_events" to eventCounts.entries
                    .sortedByDescending { it.value.get() }
                    .take(10)
                    .associate { it.key to it.value.get() },
                "performance" to StreamingPerformanceMonitor.getProductionReport(),
                "config" to StreamingConfig.getHealthStatus()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting analytics summary: ${e.message}")
            return mapOf("error" to (e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Export analytics data (for debugging/support)
     */
    fun exportAnalyticsData(): String {
        try {
            val summary = getAnalyticsSummary()
            val json = JSONObject(summary)
            return json.toString(2) // Pretty print
        } catch (e: Exception) {
            Timber.e(e, "Error exporting analytics data: ${e.message}")
            return "{\"error\": \"${e.message}\"}"
        }
    }
    
    // Private helper methods
    
    private fun logAnalyticsSummary() {
        try {
            val summary = getAnalyticsSummary()
            val totalEvents = summary["total_events"]
            val activeSessions = summary["active_sessions"]
            val successRate = summary["success_rate"]
            
            Timber.d("📊 Analytics Summary: $totalEvents events, $activeSessions active sessions, $successRate% success rate")
        } catch (e: Exception) {
            Timber.e(e, "Error logging analytics summary: ${e.message}")
        }
    }
    
    private fun cleanupOldSessions() {
        try {
            val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000L) // 24 hours
            val expiredSessions = sessionData.values.filter { session ->
                val endTime = session.endTime ?: session.startTime
                endTime < cutoffTime
            }
            
            expiredSessions.forEach { session ->
                sessionData.remove(session.sessionId)
            }
            
            if (expiredSessions.isNotEmpty() && StreamingConfig.isDebugModeEnabled) {
                Timber.d("🧹 Cleaned up ${expiredSessions.size} expired analytics sessions")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old sessions: ${e.message}")
        }
    }
    
    /**
     * Shutdown analytics system
     */
    fun shutdown() {
        try {
            isInitialized = false
            
            // End all active sessions
            sessionData.values.filter { it.endTime == null }.forEach { session ->
                endSession(session.sessionId, wasSuccessful = false, errorMessage = "System shutdown")
            }
            
            scope.launch {
                // Final analytics summary
                if (StreamingConfig.isDebugModeEnabled) {
                    Timber.d("📊 Final Analytics Summary:")
                    Timber.d(exportAnalyticsData())
                }
            }
            
            Timber.d("📊 StreamingAnalytics shutdown completed")
        } catch (e: Exception) {
            Timber.e(e, "Error during analytics shutdown: ${e.message}")
        }
    }
}