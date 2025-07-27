package com.cyberflux.qwinai.utils

import android.util.LongSparseArray
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * PERFORMANCE MONITOR: Real-time streaming performance tracking
 * 
 * Monitors:
 * - Markdown processing times
 * - UI update frequency  
 * - Memory allocations
 * - Frame drops
 * - Cache hit rates
 */
object StreamingPerformanceMonitor {
    
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
    private val lastUpdateTime = AtomicLong(0)
    private val currentFPS = AtomicReference(60.0)
    private val isMonitoring = AtomicReference(false)
    
    // Monitoring scope
    private val monitoringScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("PerformanceMonitor")
    )
    
    data class PerformanceStats(
        val averageProcessingTime: Long,
        val maxProcessingTime: Long,
        val minProcessingTime: Long,
        val currentFPS: Double,
        val cacheHitRate: Double,
        val memoryUsage: Long,
        val totalUpdates: Long,
        val frameDrops: Long,
        val isOptimal: Boolean
    )
    
    /**
     * Start performance monitoring
     */
    fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            monitoringScope.launch {
                while (isMonitoring.get()) {
                    updatePerformanceMetrics()
                    delay(100) // Update every 100ms
                }
            }
            Timber.d("🚀 Performance monitoring started")
        }
    }
    
    /**
     * Stop performance monitoring
     */
    fun stopMonitoring() {
        isMonitoring.set(false)
        Timber.d("⏹️ Performance monitoring stopped")
    }
    
    /**
     * Record markdown processing time
     */
    fun recordProcessingTime(timeMs: Long) {
        synchronized(processingTimes) {
            if (processingTimes.size >= 1000) {
                processingTimes.removeFirst()
            }
            processingTimes.addLast(timeMs)
        }
    }
    
    /**
     * Record UI update
     */
    fun recordUIUpdate() {
        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTime.getAndSet(now)
        
        totalUpdates.incrementAndGet()
        
        if (lastUpdate > 0) {
            val updateInterval = now - lastUpdate
            synchronized(uiUpdateTimes) {
                if (uiUpdateTimes.size >= 1000) {
                    uiUpdateTimes.removeFirst()
                }
                uiUpdateTimes.addLast(updateInterval)
            }
            
            // Calculate FPS
            if (updateInterval > 0) {
                val fps = 1000.0 / updateInterval
                currentFPS.set(fps)
                
                // Detect frame drops (< 45 FPS)
                if (fps < 45.0) {
                    frameDrops.incrementAndGet()
                }
            }
        }
    }
    
    /**
     * Record cache hit
     */
    fun recordCacheHit() {
        cacheHits.incrementAndGet()
    }
    
    /**
     * Record cache miss
     */
    fun recordCacheMiss() {
        cacheMisses.incrementAndGet()
    }
    
    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        // Memory monitoring
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        
        synchronized(memorySnapshots) {
            if (memorySnapshots.size >= 100) {
                memorySnapshots.removeFirst()
            }
            memorySnapshots.addLast(usedMemory)
        }
    }
    
    /**
     * Get current performance statistics
     */
    fun getPerformanceStats(): PerformanceStats {
        val avgProcessingTime = synchronized(processingTimes) {
            if (processingTimes.isEmpty()) 0L else processingTimes.average().toLong()
        }
        
        val maxProcessingTime = synchronized(processingTimes) {
            processingTimes.maxOrNull() ?: 0L
        }
        
        val minProcessingTime = synchronized(processingTimes) {
            processingTimes.minOrNull() ?: 0L
        }
        
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val hitRate = if (hits + misses > 0) (hits.toDouble() / (hits + misses)) * 100 else 0.0
        
        val memoryUsage = synchronized(memorySnapshots) {
            memorySnapshots.lastOrNull() ?: 0L
        }
        
        val fps = currentFPS.get()
        val isOptimal = fps >= 55.0 && avgProcessingTime <= 20L && hitRate >= 70.0
        
        return PerformanceStats(
            averageProcessingTime = avgProcessingTime,
            maxProcessingTime = maxProcessingTime,
            minProcessingTime = minProcessingTime,
            currentFPS = fps,
            cacheHitRate = hitRate,
            memoryUsage = memoryUsage,
            totalUpdates = totalUpdates.get(),
            frameDrops = frameDrops.get(),
            isOptimal = isOptimal
        )
    }
    
    /**
     * Get formatted performance report
     */
    fun getPerformanceReport(): String {
        val stats = getPerformanceStats()
        
        return buildString {
            appendLine("🚀 ULTRA-FAST STREAMING PERFORMANCE")
            appendLine("════════════════════════════════════")
            appendLine("📊 Processing: Avg ${stats.averageProcessingTime}ms, Max ${stats.maxProcessingTime}ms")
            appendLine("🎯 FPS: ${String.format("%.1f", stats.currentFPS)} (Target: 60+)")
            appendLine("💾 Cache Hit Rate: ${String.format("%.1f", stats.cacheHitRate)}%")
            appendLine("📱 Memory: ${stats.memoryUsage / 1024 / 1024}MB")
            appendLine("📈 Total Updates: ${stats.totalUpdates}")
            appendLine("⚠️ Frame Drops: ${stats.frameDrops}")
            appendLine("✅ Status: ${if (stats.isOptimal) "OPTIMAL" else "NEEDS OPTIMIZATION"}")
            
            if (!stats.isOptimal) {
                appendLine()
                appendLine("🔧 OPTIMIZATION SUGGESTIONS:")
                if (stats.currentFPS < 55.0) {
                    appendLine("• Reduce UI update frequency")
                }
                if (stats.averageProcessingTime > 20L) {
                    appendLine("• Optimize markdown processing")
                }
                if (stats.cacheHitRate < 70.0) {
                    appendLine("• Improve cache efficiency")
                }
            }
        }
    }
    
    /**
     * Log performance summary periodically
     */
    fun logPerformanceSummary() {
        if (isMonitoring.get()) {
            val report = getPerformanceReport()
            Timber.d(report)
        }
    }
    
    /**
     * Reset all performance metrics
     */
    fun resetMetrics() {
        synchronized(processingTimes) { processingTimes.clear() }
        synchronized(uiUpdateTimes) { uiUpdateTimes.clear() }
        synchronized(memorySnapshots) { memorySnapshots.clear() }
        
        totalUpdates.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        frameDrops.set(0)
        lastUpdateTime.set(0)
        currentFPS.set(60.0)
        
        Timber.d("📊 Performance metrics reset")
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopMonitoring()
        monitoringScope.cancel("Performance monitor cleanup")
        resetMetrics()
    }
    
    // ===== PRODUCTION-READY ENHANCEMENTS =====
    
    // Session tracking for streaming continuation
    private val sessionMetrics = mutableMapOf<String, SessionMetrics>()
    private val errorCount = AtomicLong(0)
    private val warningCount = AtomicLong(0)
    
    data class SessionMetrics(
        val sessionId: String,
        val startTime: Long = System.currentTimeMillis(),
        var lastUpdateTime: Long = System.currentTimeMillis(),
        var updateCount: Int = 0,
        var totalContentLength: Int = 0,
        var avgProcessingTime: Double = 0.0
    )
    
    /**
     * Start tracking a streaming session
     */
    fun startStreamingSession(sessionId: String) {
        sessionMetrics[sessionId] = SessionMetrics(sessionId)
        Timber.d("📊 Started performance tracking for session: $sessionId")
    }
    
    /**
     * Update session metrics
     */
    fun updateSessionMetrics(sessionId: String, processingTimeMs: Long, contentLength: Int = 0) {
        sessionMetrics[sessionId]?.let { session ->
            session.lastUpdateTime = System.currentTimeMillis()
            session.updateCount++
            session.totalContentLength += contentLength
            
            // Update rolling average
            val totalTime = session.avgProcessingTime * (session.updateCount - 1) + processingTimeMs
            session.avgProcessingTime = totalTime / session.updateCount
            
            // Performance warnings
            if (processingTimeMs > StreamingConfig.WARNING_PROCESSING_TIME_MS) {
                warningCount.incrementAndGet()
                if (processingTimeMs > StreamingConfig.CRITICAL_PROCESSING_TIME_MS) {
                    handleCriticalPerformance(sessionId, processingTimeMs)
                }
            }
        }
        
        recordProcessingTime(processingTimeMs)
    }
    
    /**
     * End streaming session tracking
     */
    fun endStreamingSession(sessionId: String) {
        val session = sessionMetrics.remove(sessionId)
        session?.let {
            val duration = System.currentTimeMillis() - it.startTime
            Timber.d("📊 Session $sessionId completed: ${duration}ms, ${it.updateCount} updates, avg ${String.format("%.1f", it.avgProcessingTime)}ms")
        }
    }
    
    /**
     * Record streaming error
     */
    fun recordStreamingError(sessionId: String? = null, error: Throwable) {
        errorCount.incrementAndGet()
        StreamingConfig.reportStreamingFailure()
        
        if (StreamingConfig.isDebugModeEnabled) {
            Timber.w("⚠️ Streaming error in session $sessionId: ${error.message}")
        }
    }
    
    /**
     * Handle critical performance issues
     */
    private fun handleCriticalPerformance(sessionId: String, processingTimeMs: Long) {
        Timber.w("🚨 CRITICAL performance: session=$sessionId, time=${processingTimeMs}ms")
        
        // Auto-adjust performance mode if enabled
        if (StreamingConfig.currentPerformanceMode == StreamingConfig.PerformanceMode.HIGH_PERFORMANCE) {
            StreamingConfig.updatePerformanceMode(StreamingConfig.PerformanceMode.BALANCED)
            Timber.i("🔧 Auto-adjusted to BALANCED mode due to performance issues")
        }
    }
    
    /**
     * Get comprehensive performance report for production monitoring
     */
    fun getProductionReport(): Map<String, Any> {
        val stats = getPerformanceStats()
        val runtime = Runtime.getRuntime()
        
        return mapOf(
            "timestamp" to System.currentTimeMillis(),
            "performance_grade" to calculatePerformanceGrade(stats),
            "metrics" to mapOf(
                "avg_processing_time_ms" to stats.averageProcessingTime,
                "max_processing_time_ms" to stats.maxProcessingTime,
                "current_fps" to stats.currentFPS,
                "cache_hit_rate_percent" to stats.cacheHitRate,
                "memory_usage_mb" to (stats.memoryUsage / 1024 / 1024),
                "total_updates" to stats.totalUpdates,
                "frame_drops" to stats.frameDrops,
                "error_count" to errorCount.get(),
                "warning_count" to warningCount.get()
            ),
            "runtime" to mapOf(
                "total_memory_mb" to (runtime.totalMemory() / 1024 / 1024),
                "free_memory_mb" to (runtime.freeMemory() / 1024 / 1024),
                "max_memory_mb" to (runtime.maxMemory() / 1024 / 1024)
            ),
            "active_sessions" to sessionMetrics.size,
            "session_details" to sessionMetrics.values.map { session ->
                mapOf(
                    "session_id" to session.sessionId,
                    "duration_ms" to (System.currentTimeMillis() - session.startTime),
                    "update_count" to session.updateCount,
                    "avg_processing_time_ms" to session.avgProcessingTime,
                    "content_length" to session.totalContentLength
                )
            },
            "config" to StreamingConfig.getHealthStatus(),
            "is_optimal" to stats.isOptimal
        )
    }
    
    /**
     * Calculate performance grade A-F
     */
    private fun calculatePerformanceGrade(stats: PerformanceStats): String {
        var score = 100.0
        
        // FPS penalty
        if (stats.currentFPS < 30.0) score -= 30
        else if (stats.currentFPS < 45.0) score -= 15
        else if (stats.currentFPS < 55.0) score -= 5
        
        // Processing time penalty
        if (stats.averageProcessingTime > 50L) score -= 25
        else if (stats.averageProcessingTime > 30L) score -= 15
        else if (stats.averageProcessingTime > 20L) score -= 5
        
        // Cache hit rate penalty
        if (stats.cacheHitRate < 50.0) score -= 20
        else if (stats.cacheHitRate < 70.0) score -= 10
        
        // Error rate penalty
        val totalOperations = stats.totalUpdates
        if (totalOperations > 0) {
            val errorRate = errorCount.get().toDouble() / totalOperations
            if (errorRate > 0.1) score -= 20  // 10% error rate
            else if (errorRate > 0.05) score -= 10  // 5% error rate
        }
        
        return when {
            score >= 90 -> "A"
            score >= 80 -> "B"
            score >= 70 -> "C"
            score >= 60 -> "D"
            else -> "F"
        }
    }
    
    /**
     * Health check for production monitoring
     */
    fun isHealthy(): Boolean {
        val stats = getPerformanceStats()
        return stats.isOptimal && 
               errorCount.get() < 10 && 
               !StreamingConfig.isCircuitBreakerOpen()
    }
    
    /**
     * Get alerts for production monitoring
     */
    fun getActiveAlerts(): List<String> {
        val alerts = mutableListOf<String>()
        val stats = getPerformanceStats()
        
        if (stats.currentFPS < 30.0) {
            alerts.add("CRITICAL: FPS below 30 (${String.format("%.1f", stats.currentFPS)})")
        } else if (stats.currentFPS < 45.0) {
            alerts.add("WARNING: FPS below 45 (${String.format("%.1f", stats.currentFPS)})")
        }
        
        if (stats.averageProcessingTime > 50L) {
            alerts.add("CRITICAL: Processing time above 50ms (${stats.averageProcessingTime}ms)")
        } else if (stats.averageProcessingTime > 30L) {
            alerts.add("WARNING: Processing time above 30ms (${stats.averageProcessingTime}ms)")
        }
        
        if (stats.cacheHitRate < 50.0) {
            alerts.add("WARNING: Cache hit rate below 50% (${String.format("%.1f", stats.cacheHitRate)}%)")
        }
        
        if (StreamingConfig.isCircuitBreakerOpen()) {
            alerts.add("CRITICAL: Streaming circuit breaker is OPEN")
        }
        
        val memoryMB = stats.memoryUsage / 1024 / 1024
        if (memoryMB > 200) {
            alerts.add("CRITICAL: Memory usage above 200MB (${memoryMB}MB)")
        } else if (memoryMB > 100) {
            alerts.add("WARNING: Memory usage above 100MB (${memoryMB}MB)")
        }
        
        return alerts
    }
}