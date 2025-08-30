package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import androidx.core.content.edit

/**
 * UNIFIED STREAMING MANAGER
 * 
 * Consolidates all streaming-related management:
 * - State management across activity lifecycle
 * - Performance monitoring and analytics
 * - Configuration management
 * - Session tracking
 * - Memory and cache management
 */
object UnifiedStreamingManager {
    
    private const val PREFS_NAME = "unified_streaming"
    private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    private const val MAX_CONTENT_LENGTH = 100_000 // Optimized content limit for better UX
    private const val MAX_ACTIVE_SESSIONS = 10 // Limit concurrent sessions
    
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false
    
    // ===== STATE MANAGEMENT =====
    
    // In-memory active streaming sessions
    private val activeSessions = ConcurrentHashMap<String, StreamingSession>()
    
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
        var isBackgroundActive: Boolean = false,
        // Analytics data
        val events: MutableList<AnalyticsEvent> = mutableListOf(),
        val metrics: MutableMap<String, Any> = mutableMapOf(),
        var wasSuccessful: Boolean = false,
        var errorMessage: String? = null
    ) {
        fun appendContent(content: String) {
            // CRITICAL: Enhanced memory protection with progressive trimming
            val newLength = partialContent.length + content.length
            
            when {
                // ULTRA-LARGE: Aggressive trimming for very large content
                newLength > 150_000 -> {
                    val keepLength = 80_000 // Keep last 80K chars
                    val current = partialContent.toString()
                    partialContent.clear()
                    partialContent.append("...[content trimmed for memory safety]...\n\n")
                    partialContent.append(current.takeLast(keepLength))
                    partialContent.append(content)
                    Timber.w("⚠️ ULTRA-LARGE content trimmed: ${newLength} -> ${partialContent.length}")
                }
                
                // LARGE: Progressive trimming for large content  
                newLength > MAX_CONTENT_LENGTH -> {
                    val keepLength = (MAX_CONTENT_LENGTH * 0.75).toInt() // Keep 75%
                    val current = partialContent.toString()
                    
                    // Smart trimming - try to preserve markdown structure
                    val trimmedContent = UnifiedStreamingManager.smartTrimContent(current, keepLength)
                    partialContent.clear()
                    partialContent.append("...[earlier content trimmed]...\n\n")
                    partialContent.append(trimmedContent)
                    partialContent.append(content)
                    Timber.w("⚠️ Large content trimmed: ${newLength} -> ${partialContent.length}")
                }
                
                // NORMAL: Just append content
                else -> {
                    partialContent.append(content)
                }
            }
            
            lastUpdateTime = System.currentTimeMillis()
        }
        
        
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - lastUpdateTime > SESSION_TIMEOUT_MS
        }
        
        fun getPartialContent(): String {
            return partialContent.toString()
        }
    }
    
    /**
     * Smart content trimming that preserves markdown structure
     * Shared utility method for both session and cleanup operations
     */
    private fun smartTrimContent(content: String, keepLength: Int): String {
        if (content.length <= keepLength) return content
        
        val targetStart = content.length - keepLength
        
        // Try to find a good break point (paragraph, heading, or list item)
        val breakPoints = listOf("\n\n", "\n# ", "\n## ", "\n### ", "\n- ", "\n1. ", "\n* ")
        
        for (breakPoint in breakPoints) {
            val breakIndex = content.indexOf(breakPoint, targetStart)
            if (breakIndex != -1 && breakIndex < content.length - (keepLength * 0.1)) {
                return content.substring(breakIndex + 1) // Skip the break character
            }
        }
        
        // Fallback to simple substring if no good break point found
        return content.substring(targetStart)
    }
    
    // ===== PERFORMANCE MONITORING =====
    
    // Performance metrics
    private val processingTimes = ArrayDeque<Long>(1000)
    private val uiUpdateTimes = ArrayDeque<Long>(1000)
    private val memorySnapshots = ArrayDeque<Long>(100)
    
    // Counters
    private val totalUpdates = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val frameDrops = AtomicLong(0)
    
    // Real-time tracking
    private val currentFPS = AtomicReference(60.0)
    private val isMonitoring = AtomicReference(false)
    
    // Enhanced streaming metrics
    private val timeToFirstTokenMap = ConcurrentHashMap<String, Long>()
    private val charactersPerSecondMap = ConcurrentHashMap<String, Long>()
    private val markdownComplexityScores = ArrayDeque<Int>(100)
    
    data class PerformanceStats(
        val averageProcessingTime: Long,
        val maxProcessingTime: Long,
        val minProcessingTime: Long,
        val currentFPS: Double,
        val cacheHitRate: Double,
        val memoryUsage: Long,
        val totalUpdates: Long,
        val frameDrops: Long
    )
    
    // ===== ANALYTICS =====
    
    // Event tracking
    private val eventCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val eventTimings = ConcurrentHashMap<String, AtomicLong>()
    private val customMetrics = ConcurrentHashMap<String, Any>()
    
    data class AnalyticsEvent(
        val eventName: String,
        val timestamp: Long = System.currentTimeMillis(),
        val properties: Map<String, Any> = emptyMap(),
        val category: EventCategory = EventCategory.GENERAL
    )
    
    enum class EventCategory {
        GENERAL, PERFORMANCE, ERROR,
    }
    
    // ===== INITIALIZATION =====
    
    fun initialize(context: Context) {
        if (isInitialized) return
        
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        
        // Load configuration
        updateRuntimeFlags()
        
        // Start cleanup job
        startPeriodicCleanup()
        
        // Start performance monitoring
        startPerformanceMonitoring()
        
        logEvent("unified_streaming_manager_initialized", mapOf(
            "featureEnabled" to _featureEnabled,
            "backgroundStreamingEnabled" to _backgroundStreamingEnabled,
            "performanceMode" to _performanceMode.name,
            "debugMode" to _debugModeEnabled
        ))
        
        Timber.d("UnifiedStreamingManager initialized with config: feature=$_featureEnabled, background=$_backgroundStreamingEnabled, performance=$_performanceMode")
    }
    
    // ===== STATE MANAGEMENT METHODS =====
    
    fun createSession(
        messageId: String,
        conversationId: String,
        sessionId: String,
        modelId: String
    ): StreamingSession {
        // Clean up expired sessions first
        cleanupExpiredSessions()
        
        // Enforce session limit
        if (activeSessions.size >= MAX_ACTIVE_SESSIONS) {
            val oldestSession = activeSessions.values.minByOrNull { it.startTime }
            oldestSession?.let { 
                activeSessions.remove(it.sessionId)
                logEvent("session_evicted", mapOf("reason" to "max_sessions_reached"))
            }
        }
        
        val session = StreamingSession(messageId, conversationId, sessionId, modelId)
        activeSessions[sessionId] = session
        
        logEvent("session_created", mapOf(
            "messageId" to messageId,
            "conversationId" to conversationId,
            "modelId" to modelId
        ))
        
        return session
    }
    
    fun getSession(sessionId: String): StreamingSession? {
        return activeSessions[sessionId]?.takeIf { !it.isExpired() }
    }
    
    fun updateSessionContent(sessionId: String, content: String): Boolean {
        val session = getSession(sessionId) ?: return false
        session.appendContent(content)
        return true
    }
    
    fun completeSession(sessionId: String, success: Boolean = true, error: String? = null) {
        val session = activeSessions[sessionId] ?: return
        
        session.isActive = false
        session.wasSuccessful = success
        session.errorMessage = error
        
        // Log completion analytics
        val duration = System.currentTimeMillis() - session.startTime
        logEvent("session_completed", mapOf(
            "sessionId" to sessionId,
            "duration" to duration,
            "success" to success,
            "contentLength" to session.partialContent.length,
            "eventCount" to session.events.size
        ))
        
        // Archive or cleanup session after delay
        scope.launch {
            delay(5000) // Keep for 5 seconds for potential recovery
            activeSessions.remove(sessionId)
        }
    }
    
    /**
     * Get average time to first token
     */
    fun getAverageTimeToFirstToken(): Long {
        return if (timeToFirstTokenMap.isEmpty()) 0L else {
            timeToFirstTokenMap.values.average().toLong()
        }
    }
    
    /**
     * Get average characters per second
     */
    fun getAverageCharactersPerSecond(): Long {
        return if (charactersPerSecondMap.isEmpty()) 0L else {
            charactersPerSecondMap.values.average().toLong()
        }
    }
    
    /**
     * Get average markdown complexity
     */
    fun getAverageMarkdownComplexity(): Int {
        return synchronized(markdownComplexityScores) {
            if (markdownComplexityScores.isEmpty()) 0 else markdownComplexityScores.average().toInt()
        }
    }
    
    /**
     * ADAPTIVE BUFFER MANAGEMENT: Get optimal buffer size based on streaming state
     */
    fun getOptimalBufferSize(contentLength: Int, isFirstToken: Boolean, streamingPhase: StreamingPhase = StreamingPhase.SUSTAINED): Int {
        return when {
            // FIRST TOKEN OPTIMIZATION: Ultra-small buffer for immediate response
            isFirstToken -> FIRST_TOKEN_BUFFER
            
            // ADAPTIVE SIZING: Based on current content length and streaming phase
            contentLength == 0 -> INITIAL_BUFFER
            contentLength < SMALL_CONTENT_THRESHOLD -> when (streamingPhase) {
                StreamingPhase.INITIAL -> INITIAL_BUFFER
                else -> SUSTAINED_BUFFER
            }
            contentLength < MEDIUM_CONTENT_THRESHOLD -> SUSTAINED_BUFFER
            contentLength < LARGE_CONTENT_THRESHOLD -> HEAVY_CONTENT_BUFFER
            else -> MAX_STREAMING_BUFFER
        }
    }
    
    /**
     * STREAMING PACE OPTIMIZATION: Get recommended update interval for consistent pace
     */
    fun getRecommendedUpdateInterval(contentLength: Int, streamingPhase: StreamingPhase): Long {
        return when (streamingPhase) {
            StreamingPhase.FIRST_TOKEN -> 8L    // Ultra-fast for first token
            StreamingPhase.INITIAL -> 16L       // Fast initial streaming (60 FPS)
            StreamingPhase.SUSTAINED -> when {
                contentLength < SMALL_CONTENT_THRESHOLD -> 16L   // Maintain 60 FPS for small content
                contentLength < MEDIUM_CONTENT_THRESHOLD -> 20L  // Slightly slower for medium content
                contentLength < LARGE_CONTENT_THRESHOLD -> 25L   // Conservative for large content
                else -> 33L  // 30 FPS for very large content to prevent UI blocking
            }
            StreamingPhase.HEAVY_CONTENT -> 33L  // Conservative for heavy processing
            StreamingPhase.FINALIZATION -> 50L   // Slower during finalization
        }
    }
    
    /**
     * STREAMING PHASES: Different phases of streaming for optimal buffer management
     */
    enum class StreamingPhase {
        FIRST_TOKEN,      // First content received - ultra-fast response needed
        INITIAL,          // First few chunks - prioritize speed
        SUSTAINED,        // Normal streaming - balanced performance
        HEAVY_CONTENT,    // Large content - prioritize stability
        FINALIZATION      // Completing stream - prioritize completeness
    }
    
    /**
     * PERFORMANCE ANALYSIS: Analyze streaming performance and suggest optimizations
     */
    fun analyzeStreamingPerformance(sessionId: String): Map<String, Any> {
        val session = activeSessions[sessionId]
        return if (session != null) {
            val duration = System.currentTimeMillis() - session.startTime
            val contentLength = session.partialContent.length
            val avgCharsPerSecond = if (duration > 0) (contentLength * 1000L) / duration else 0
            
            mapOf(
                "sessionId" to sessionId,
                "duration" to duration,
                "contentLength" to contentLength,
                "avgCharsPerSecond" to avgCharsPerSecond,
                "isActive" to session.isActive,
                "updateCount" to session.events.count { it.eventName.contains("update") },
                "recommendedBufferSize" to getOptimalBufferSize(contentLength, false, StreamingPhase.SUSTAINED),
                "recommendedUpdateInterval" to getRecommendedUpdateInterval(contentLength, StreamingPhase.SUSTAINED),
                "performanceGrade" to when {
                    avgCharsPerSecond > 100 -> "EXCELLENT"
                    avgCharsPerSecond > 50 -> "GOOD"
                    avgCharsPerSecond > 20 -> "FAIR"
                    else -> "NEEDS_OPTIMIZATION"
                }
            )
        } else {
            mapOf("error" to "Session not found")
        }
    }
    
    /**
     * CRITICAL: Enhanced memory cleanup with aggressive optimization
     */
    fun performMemoryCleanup() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Phase 1: Clean expired sessions
            val expiredSessions = activeSessions.values.filter { it.isExpired() }
            expiredSessions.forEach { session ->
                activeSessions.remove(session.sessionId)
                Timber.d("Cleaned expired session: ${session.sessionId}")
            }
            
            // Phase 2: Aggressive content trimming based on memory pressure
            val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val maxMemory = Runtime.getRuntime().maxMemory()
            val memoryPressure = memoryUsage.toFloat() / maxMemory.toFloat()
            
            val trimThreshold = when {
                memoryPressure > 0.9 -> 20_000  // High pressure: very aggressive
                memoryPressure > 0.7 -> 40_000  // Medium pressure: moderate
                memoryPressure > 0.5 -> 60_000  // Low pressure: gentle
                else -> MAX_CONTENT_LENGTH      // No pressure: use normal limit
            }
            
            var trimmedSessions = 0
            activeSessions.values.forEach { session ->
                if (session.partialContent.length > trimThreshold) {
                    val keepLength = (trimThreshold * 0.6).toInt()
                    val currentContent = session.partialContent.toString()
                    
                    // Use smart trimming to preserve structure
                    val trimmedContent = smartTrimContent(currentContent, keepLength)
                    session.partialContent.clear()
                    session.partialContent.append("...[content trimmed due to memory pressure]...\n\n")
                    session.partialContent.append(trimmedContent)
                    
                    trimmedSessions++
                    Timber.d("Trimmed content for session: ${session.sessionId} (${currentContent.length} -> ${session.partialContent.length})")
                }
            }
            
            // Phase 3: Clean performance metrics with adaptive retention
            val metricsRetentionTime = when {
                memoryPressure > 0.8 -> 5 * 60 * 1000   // 5 minutes
                memoryPressure > 0.6 -> 15 * 60 * 1000  // 15 minutes  
                else -> 30 * 60 * 1000                  // 30 minutes
            }
            
            val metricsCutoffTime = currentTime - metricsRetentionTime
            
            synchronized(processingTimes) {
                val sizeBefore = processingTimes.size
                processingTimes.removeAll { it < metricsCutoffTime }
                if (processingTimes.size != sizeBefore) {
                    Timber.d("Cleaned ${sizeBefore - processingTimes.size} old processing times")
                }
            }
            
            synchronized(uiUpdateTimes) {
                val sizeBefore = uiUpdateTimes.size
                uiUpdateTimes.removeAll { it < metricsCutoffTime }
                if (uiUpdateTimes.size != sizeBefore) {
                    Timber.d("Cleaned ${sizeBefore - uiUpdateTimes.size} old UI update times")
                }
            }
            
            synchronized(memorySnapshots) {
                val sizeBefore = memorySnapshots.size
                memorySnapshots.removeAll { it < metricsCutoffTime }
                if (memorySnapshots.size != sizeBefore) {
                    Timber.d("Cleaned ${sizeBefore - memorySnapshots.size} old memory snapshots")
                }
            }
            
            // Phase 4: Clean analytics events with session limits
            val maxEventsPerSession = when {
                memoryPressure > 0.8 -> 10   // High pressure: minimal events
                memoryPressure > 0.6 -> 25   // Medium pressure: fewer events
                else -> 50                   // Normal: standard limit
            }
            
            var cleanedEvents = 0
            activeSessions.values.forEach { session ->
                if (session.events.size > maxEventsPerSession) {
                    val eventsToRemove = session.events.size - maxEventsPerSession
                    // Keep the most recent events
                    session.events.sortByDescending { it.timestamp }
                    repeat(eventsToRemove) {
                        if (session.events.isNotEmpty()) {
                            session.events.removeAt(session.events.lastIndex)
                            cleanedEvents++
                        }
                    }
                }
            }
            
            // Phase 5: Force garbage collection if memory pressure is high
            if (memoryPressure > 0.85) {
                System.gc()
                Timber.w("⚠️ Forced garbage collection due to high memory pressure: ${(memoryPressure * 100).toInt()}%")
            }
            
            Timber.d("Enhanced memory cleanup completed:")
            Timber.d("- Removed ${expiredSessions.size} expired sessions")
            Timber.d("- Trimmed $trimmedSessions sessions")
            Timber.d("- Cleaned $cleanedEvents analytics events")
            Timber.d("- Memory pressure: ${(memoryPressure * 100).toInt()}%")
            Timber.d("- Active sessions: ${activeSessions.size}")
            
        } catch (e: Exception) {
            Timber.e(e, "Error during enhanced memory cleanup: ${e.message}")
        }
    }

    
    fun getPerformanceStats(): PerformanceStats {
        val processingTimesSnapshot = synchronized(processingTimes) { processingTimes.toList() }
        
        return PerformanceStats(
            averageProcessingTime = if (processingTimesSnapshot.isNotEmpty()) {
                processingTimesSnapshot.average().toLong()
            } else 0L,
            maxProcessingTime = processingTimesSnapshot.maxOrNull() ?: 0L,
            minProcessingTime = processingTimesSnapshot.minOrNull() ?: 0L,
            currentFPS = currentFPS.get(),
            cacheHitRate = run {
                val hits = cacheHits.get()
                val misses = cacheMisses.get()
                if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
            },
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            totalUpdates = totalUpdates.get(),
            frameDrops = frameDrops.get()
        )
    }
    
    // ===== ANALYTICS METHODS =====
    
    fun logEvent(eventName: String, properties: Map<String, Any> = emptyMap(), category: EventCategory = EventCategory.GENERAL) {
        val event = AnalyticsEvent(eventName, properties = properties, category = category)
        
        // Increment event counter
        eventCounts.computeIfAbsent(eventName) { AtomicInteger(0) }.incrementAndGet()
        
        // Record timing if performance-related
        if (category == EventCategory.PERFORMANCE) {
            eventTimings[eventName] = AtomicLong(System.currentTimeMillis())
        }
        
        // Log to active sessions
        activeSessions.values.forEach { session ->
            if (session.isActive) {
                session.events.add(event)
                
                // Limit events per session to prevent memory issues
                if (session.events.size > 100) {
                    session.events.removeAt(0)
                }
            }
        }
        
        Timber.d("Analytics Event: $eventName - $properties")
    }

    
    fun getAnalyticsSummary(): Map<String, Any> {
        return mapOf(
            "totalEvents" to eventCounts.values.sumOf { it.get() },
            "activeSessions" to activeSessions.size,
            "totalSessions" to (eventCounts["session_created"]?.get() ?: 0),
            "averageSessionDuration" to calculateAverageSessionDuration(),
            "errorRate" to calculateErrorRate(),
            "customMetrics" to HashMap(customMetrics),
            "performanceStats" to getPerformanceStats(),
            "topEvents" to getTopEvents(),
            // Enhanced streaming metrics
            "averageTimeToFirstToken" to getAverageTimeToFirstToken(),
            "averageCharactersPerSecond" to getAverageCharactersPerSecond(),
            "averageMarkdownComplexity" to getAverageMarkdownComplexity(),
            "streamingSessionsCount" to timeToFirstTokenMap.size
        )
    }
    
    // ===== CONFIGURATION MANAGEMENT =====
    
    // ULTRA-FAST STREAMING: Dynamic buffer configuration for consistent streaming pace
    const val STREAM_BUFFER_SIZE = 16384  // Default buffer size (backward compatibility)
    
    // REAL-TIME STREAMING: Adaptive buffer sizes for different streaming phases
    const val FIRST_TOKEN_BUFFER = 1024    // Ultra-small for immediate first token
    const val INITIAL_BUFFER = 4096        // Small for quick initial response
    const val SUSTAINED_BUFFER = 8192      // Balanced for sustained streaming
    const val HEAVY_CONTENT_BUFFER = 16384 // Standard for heavy markdown content
    const val MAX_STREAMING_BUFFER = 32768 // Maximum for very large responses
    
    // ADAPTIVE THRESHOLDS: Content size thresholds for buffer management
    const val SMALL_CONTENT_THRESHOLD = 1000      // Switch to sustained buffer
    const val MEDIUM_CONTENT_THRESHOLD = 5000     // Switch to heavy buffer
    const val LARGE_CONTENT_THRESHOLD = 20000     // Switch to max buffer
    
    // Configuration keys
    private const val KEY_FEATURE_ENABLED = "streaming_continuation_enabled"
    private const val KEY_BACKGROUND_STREAMING_ENABLED = "background_streaming_enabled"
    private const val KEY_PERFORMANCE_MODE = "performance_mode"
    private const val KEY_DEBUG_MODE = "debug_mode_enabled"
    private const val KEY_CIRCUIT_BREAKER_ENABLED = "circuit_breaker_enabled"
    
    // Runtime feature flags
    @Volatile
    private var _featureEnabled = true
    @Volatile
    private var _backgroundStreamingEnabled = true
    @Volatile
    private var _debugModeEnabled = false
    @Volatile
    private var _performanceMode = PerformanceMode.BALANCED
    @Volatile
    private var _circuitBreakerEnabled = true
    
    enum class PerformanceMode {
        HIGH_PERFORMANCE,  // Maximum responsiveness
        BALANCED,         // Balanced performance and battery
        BATTERY_SAVER     // Battery optimized
    }
    
    // Circuit breaker for streaming
    private val circuitBreakerFailures = AtomicInteger(0)
    private val circuitBreakerLastFailure = AtomicLong(0)
    private const val CIRCUIT_BREAKER_THRESHOLD = 5
    private const val CIRCUIT_BREAKER_TIMEOUT = 30_000L // 30 seconds
    
    // Configuration getters
    val isStreamingContinuationEnabled: Boolean get() = _featureEnabled
    val isDebugModeEnabled: Boolean get() = _debugModeEnabled

    fun getSetting(key: String, defaultValue: Any): Any {
        if (!isInitialized) return defaultValue
        
        return when (defaultValue) {
            is Boolean -> prefs.getBoolean(key, defaultValue)
            is Int -> prefs.getInt(key, defaultValue)
            is Long -> prefs.getLong(key, defaultValue)
            is Float -> prefs.getFloat(key, defaultValue)
            is String -> prefs.getString(key, defaultValue) ?: defaultValue
            else -> defaultValue
        }
    }
    
    fun setSetting(key: String, value: Any) {
        if (!isInitialized) return
        
        prefs.edit {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
            }
        }
        
        // Update runtime flags
        updateRuntimeFlags()
    }
    
    fun isCircuitBreakerOpen(): Boolean {
        if (!_circuitBreakerEnabled) return false
        
        val failures = circuitBreakerFailures.get()
        val lastFailure = circuitBreakerLastFailure.get()
        val now = System.currentTimeMillis()
        
        return failures >= CIRCUIT_BREAKER_THRESHOLD && 
               (now - lastFailure) < CIRCUIT_BREAKER_TIMEOUT
    }

    
    private fun updateRuntimeFlags() {
        _featureEnabled = getSetting(KEY_FEATURE_ENABLED, true) as Boolean
        _backgroundStreamingEnabled = getSetting(KEY_BACKGROUND_STREAMING_ENABLED, true) as Boolean
        _debugModeEnabled = getSetting(KEY_DEBUG_MODE, false) as Boolean
        _circuitBreakerEnabled = getSetting(KEY_CIRCUIT_BREAKER_ENABLED, true) as Boolean
        
        val performanceModeOrdinal = getSetting(KEY_PERFORMANCE_MODE, PerformanceMode.BALANCED.ordinal) as Int
        _performanceMode = PerformanceMode.entries.toTypedArray().getOrElse(performanceModeOrdinal) { PerformanceMode.BALANCED }
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(60_000) // Clean up every minute
                cleanupExpiredSessions()
                cleanupOldMetrics()
            }
        }
    }
    
    private fun cleanupExpiredSessions() {
        val expiredSessions = activeSessions.values.filter { it.isExpired() }
        expiredSessions.forEach { session ->
            activeSessions.remove(session.sessionId)
            logEvent("session_expired", mapOf(
                "sessionId" to session.sessionId,
                "duration" to (System.currentTimeMillis() - session.startTime)
            ))
        }
    }
    
    private fun cleanupOldMetrics() {
        synchronized(processingTimes) {
            while (processingTimes.size > 500) {
                processingTimes.removeFirst()
            }
        }
        
        synchronized(uiUpdateTimes) {
            while (uiUpdateTimes.size > 500) {
                uiUpdateTimes.removeFirst()
            }
        }
        
        synchronized(memorySnapshots) {
            while (memorySnapshots.size > 50) {
                memorySnapshots.removeFirst()
            }
        }
    }
    
    private fun startPerformanceMonitoring() {
        if (isMonitoring.get()) return
        isMonitoring.set(true)
        
        scope.launch {
            while (isMonitoring.get()) {
                delay(5000) // Monitor every 5 seconds
                
                // Record memory snapshot
                val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                synchronized(memorySnapshots) {
                    memorySnapshots.addLast(memoryUsage)
                }
                
                // Log performance metrics
                val stats = getPerformanceStats()
                if (stats.averageProcessingTime > 100) { // Alert if processing is slow
                    logEvent("performance_warning", mapOf(
                        "averageProcessingTime" to stats.averageProcessingTime,
                        "currentFPS" to stats.currentFPS,
                        "cacheHitRate" to stats.cacheHitRate
                    ), EventCategory.PERFORMANCE)
                }
            }
        }
    }
    
    private fun calculateAverageSessionDuration(): Long {
        val completedSessions = activeSessions.values.filter { !it.isActive }
        if (completedSessions.isEmpty()) return 0L
        
        return completedSessions.map { System.currentTimeMillis() - it.startTime }.average().toLong()
    }
    
    private fun calculateErrorRate(): Double {
        val totalSessions = eventCounts["session_created"]?.get() ?: 0
        val errorSessions = eventCounts["session_error"]?.get() ?: 0
        
        return if (totalSessions > 0) errorSessions.toDouble() / totalSessions else 0.0
    }
    
    private fun getTopEvents(): List<Map<String, Any>> {
        return eventCounts.entries
            .sortedByDescending { it.value.get() }
            .take(10)
            .map { mapOf("event" to it.key, "count" to it.value.get()) }
    }
    
    // ===== SESSION QUERY METHODS =====
    
    fun getActiveSessionsForConversation(conversationId: String? = null): List<StreamingSession> {
        return activeSessions.values.filter { session ->
            session.isActive && (conversationId == null || session.conversationId == conversationId)
        }.toList()
    }
    
    fun hasActiveStreamingInConversation(conversationId: String? = null): Boolean {
        return activeSessions.values.any { session ->
            session.isActive && (conversationId == null || session.conversationId == conversationId)
        }
    }
    
    fun hasBackgroundActiveSessions(): Boolean {
        return activeSessions.values.any { it.isBackgroundActive && it.isActive }
    }
    
    fun markAsBackgroundActive(messageId: String) {
        getSession(messageId)?.isBackgroundActive = true
    }
    
    // ===== CLEANUP =====
    
    fun cleanup() {
        isMonitoring.set(false)
        scope.cancel("Manager cleanup")
        activeSessions.clear()
        processingTimes.clear()
        uiUpdateTimes.clear()
        memorySnapshots.clear()
        eventCounts.clear()
        eventTimings.clear()
        customMetrics.clear()
        
        // Clear enhanced streaming metrics
        timeToFirstTokenMap.clear()
        charactersPerSecondMap.clear()
        synchronized(markdownComplexityScores) { markdownComplexityScores.clear() }
    }
}