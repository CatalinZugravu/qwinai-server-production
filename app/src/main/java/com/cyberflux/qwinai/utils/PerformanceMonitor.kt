package com.cyberflux.qwinai.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive performance monitoring system
 * Tracks operation timing, memory usage, and system performance metrics
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val PERFORMANCE_LOG_THRESHOLD_MS = 100L
        private const val SLOW_OPERATION_THRESHOLD_MS = 1000L
        private const val VERY_SLOW_OPERATION_THRESHOLD_MS = 5000L
        private const val MAX_OPERATION_HISTORY = 1000
        private const val CLEANUP_INTERVAL_MS = 60000L // 1 minute
    }
    
    // Performance tracking
    private val operationMetrics = ConcurrentHashMap<String, OperationMetrics>()
    private val operationHistory = mutableListOf<OperationRecord>()
    private val activeOperations = ConcurrentHashMap<String, OperationContext>()
    
    // System metrics
    private val systemMetrics = SystemMetrics()
    private val performanceCallbacks = mutableListOf<PerformanceCallback>()
    
    // Counters
    private val totalOperations = AtomicLong(0)
    private val slowOperations = AtomicLong(0)
    private val verySlowOperations = AtomicLong(0)
    private val failedOperations = AtomicLong(0)
    
    // Monitoring scope
    private val monitoringScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("PerformanceMonitoring")
    )
    
    /**
     * Performance callback interface
     */
    interface PerformanceCallback {
        fun onSlowOperation(operationName: String, duration: Long, context: Map<String, Any>)
        fun onOperationCompleted(operationName: String, duration: Long, success: Boolean)
        fun onPerformanceThresholdExceeded(metric: String, value: Double, threshold: Double)
    }
    
    /**
     * Operation context for tracking
     */
    data class OperationContext(
        val operationId: String,
        val operationName: String,
        val startTime: Long,
        val startMemory: Long,
        val threadName: String,
        val additionalContext: Map<String, Any> = emptyMap()
    )
    
    /**
     * Operation metrics tracking
     */
    data class OperationMetrics(
        var totalCount: Long = 0,
        var totalTime: Long = 0,
        var minTime: Long = Long.MAX_VALUE,
        var maxTime: Long = 0,
        var slowCount: Long = 0,
        var verySlowCount: Long = 0,
        var failureCount: Long = 0,
        var lastExecutionTime: Long = 0
    ) {
        val averageTime: Double get() = if (totalCount > 0) totalTime.toDouble() / totalCount else 0.0
        val successRate: Double get() = if (totalCount > 0) ((totalCount - failureCount).toDouble() / totalCount) * 100 else 100.0
        val slowPercentage: Double get() = if (totalCount > 0) (slowCount.toDouble() / totalCount) * 100 else 0.0
    }
    
    /**
     * Operation record for history
     */
    data class OperationRecord(
        val operationName: String,
        val duration: Long,
        val timestamp: Long,
        val success: Boolean,
        val memoryUsedMB: Long,
        val threadName: String,
        val additionalContext: Map<String, Any> = emptyMap()
    )
    
    /**
     * System metrics tracking
     */
    private class SystemMetrics {
        private val cpuUsageHistory = mutableListOf<Double>()
        private val memoryUsageHistory = mutableListOf<Long>()
        private val maxHistorySize = 100
        
        fun recordCpuUsage(usage: Double) {
            synchronized(this) {
                cpuUsageHistory.add(usage)
                if (cpuUsageHistory.size > maxHistorySize) {
                    cpuUsageHistory.removeFirst()
                }
            }
        }
        
        fun recordMemoryUsage(usage: Long) {
            synchronized(this) {
                memoryUsageHistory.add(usage)
                if (memoryUsageHistory.size > maxHistorySize) {
                    memoryUsageHistory.removeFirst()
                }
            }
        }
        
        fun getAverageCpuUsage(): Double {
            return synchronized(this) {
                if (cpuUsageHistory.isEmpty()) 0.0 else cpuUsageHistory.average()
            }
        }
        
        fun getAverageMemoryUsage(): Long {
            return synchronized(this) {
                if (memoryUsageHistory.isEmpty()) 0L else memoryUsageHistory.average().toLong()
            }
        }
        
        fun getMetricsSnapshot(): SystemMetricsSnapshot {
            return synchronized(this) {
                SystemMetricsSnapshot(
                    averageCpuUsage = getAverageCpuUsage(),
                    averageMemoryUsage = getAverageMemoryUsage(),
                    cpuSamples = cpuUsageHistory.size,
                    memorySamples = memoryUsageHistory.size
                )
            }
        }
    }
    
    data class SystemMetricsSnapshot(
        val averageCpuUsage: Double,
        val averageMemoryUsage: Long,
        val cpuSamples: Int,
        val memorySamples: Int
    )
    
    init {
        startPeriodicCleanup()
        Timber.d("📊 PerformanceMonitor initialized")
    }
    
    /**
     * Start periodic cleanup of old data
     */
    private fun startPeriodicCleanup() {
        monitoringScope.launch {
            while (isActive) {
                try {
                    cleanupOldData()
                    delay(CLEANUP_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in performance monitor cleanup")
                }
            }
        }
    }
    
    /**
     * Clean up old performance data
     */
    private fun cleanupOldData() {
        // Clean up operation history
        synchronized(operationHistory) {
            if (operationHistory.size > MAX_OPERATION_HISTORY) {
                val toRemove = operationHistory.size - MAX_OPERATION_HISTORY
                repeat(toRemove) {
                    operationHistory.removeFirst()
                }
                Timber.d("🧹 Cleaned up $toRemove old operation records")
            }
        }
        
        // Clean up stale active operations (older than 5 minutes)
        val staleThreshold = System.currentTimeMillis() - 300000L
        val staleOperations = activeOperations.values.filter { it.startTime < staleThreshold }
        staleOperations.forEach { operation ->
            activeOperations.remove(operation.operationId)
            Timber.w("🧹 Removed stale active operation: ${operation.operationName}")
        }
    }
    
    /**
     * Measure operation performance with automatic timing
     */
    suspend fun <T> measureOperation(
        operationName: String,
        context: Map<String, Any> = emptyMap(),
        operation: suspend () -> T
    ): T {
        val operationId = "${operationName}_${System.nanoTime()}"
        val startTime = System.currentTimeMillis()
        val startMemory = getCurrentMemoryUsage()
        val threadName = Thread.currentThread().name
        
        // Track active operation
        val operationContext = OperationContext(
            operationId = operationId,
            operationName = operationName,
            startTime = startTime,
            startMemory = startMemory,
            threadName = threadName,
            additionalContext = context
        )
        activeOperations[operationId] = operationContext
        
        return try {
            val result = operation()
            val duration = System.currentTimeMillis() - startTime
            recordOperationSuccess(operationName, duration, startMemory, threadName, context)
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            recordOperationFailure(operationName, duration, startMemory, threadName, context, e)
            throw e
        } finally {
            activeOperations.remove(operationId)
        }
    }
    
    /**
     * Start manual operation timing
     */
    fun startOperation(operationName: String, context: Map<String, Any> = emptyMap()): String {
        val operationId = "${operationName}_${System.nanoTime()}"
        val operationContext = OperationContext(
            operationId = operationId,
            operationName = operationName,
            startTime = System.currentTimeMillis(),
            startMemory = getCurrentMemoryUsage(),
            threadName = Thread.currentThread().name,
            additionalContext = context
        )
        activeOperations[operationId] = operationContext
        return operationId
    }
    
    /**
     * End manual operation timing
     */
    fun endOperation(operationId: String, success: Boolean = true, error: Throwable? = null) {
        val operationContext = activeOperations.remove(operationId) ?: return
        val duration = System.currentTimeMillis() - operationContext.startTime
        
        if (success) {
            recordOperationSuccess(
                operationContext.operationName,
                duration,
                operationContext.startMemory,
                operationContext.threadName,
                operationContext.additionalContext
            )
        } else {
            recordOperationFailure(
                operationContext.operationName,
                duration,
                operationContext.startMemory,
                operationContext.threadName,
                operationContext.additionalContext,
                error
            )
        }
    }
    
    /**
     * Record successful operation
     */
    private fun recordOperationSuccess(
        operationName: String,
        duration: Long,
        startMemory: Long,
        threadName: String,
        context: Map<String, Any>
    ) {
        totalOperations.incrementAndGet()
        
        // Update operation metrics
        val metrics = operationMetrics.getOrPut(operationName) { OperationMetrics() }
        synchronized(metrics) {
            metrics.totalCount++
            metrics.totalTime += duration
            metrics.minTime = minOf(metrics.minTime, duration)
            metrics.maxTime = maxOf(metrics.maxTime, duration)
            metrics.lastExecutionTime = duration
            
            when {
                duration >= VERY_SLOW_OPERATION_THRESHOLD_MS -> {
                    metrics.verySlowCount++
                    verySlowOperations.incrementAndGet()
                }
                duration >= SLOW_OPERATION_THRESHOLD_MS -> {
                    metrics.slowCount++
                    slowOperations.incrementAndGet()
                }
            }
        }
        
        // Record in history
        val memoryUsed = getCurrentMemoryUsage() - startMemory
        val record = OperationRecord(
            operationName = operationName,
            duration = duration,
            timestamp = System.currentTimeMillis(),
            success = true,
            memoryUsedMB = memoryUsed / (1024 * 1024),
            threadName = threadName,
            additionalContext = context
        )
        
        synchronized(operationHistory) {
            operationHistory.add(record)
        }
        
        // Log slow operations
        if (duration >= PERFORMANCE_LOG_THRESHOLD_MS) {
            val level = when {
                duration >= VERY_SLOW_OPERATION_THRESHOLD_MS -> "🐌🐌"
                duration >= SLOW_OPERATION_THRESHOLD_MS -> "🐌"
                else -> "⏱️"
            }
            Timber.d("$level $operationName: ${duration}ms")
        }
        
        // Notify callbacks for slow operations
        if (duration >= SLOW_OPERATION_THRESHOLD_MS) {
            notifySlowOperation(operationName, duration, context)
        }
        
        // Always notify completion
        notifyOperationCompleted(operationName, duration, true)
    }
    
    /**
     * Record failed operation
     */
    private fun recordOperationFailure(
        operationName: String,
        duration: Long,
        startMemory: Long,
        threadName: String,
        context: Map<String, Any>,
        error: Throwable?
    ) {
        totalOperations.incrementAndGet()
        failedOperations.incrementAndGet()
        
        // Update operation metrics
        val metrics = operationMetrics.getOrPut(operationName) { OperationMetrics() }
        synchronized(metrics) {
            metrics.totalCount++
            metrics.failureCount++
            metrics.totalTime += duration
            metrics.lastExecutionTime = duration
        }
        
        // Record in history
        val memoryUsed = getCurrentMemoryUsage() - startMemory
        val record = OperationRecord(
            operationName = operationName,
            duration = duration,
            timestamp = System.currentTimeMillis(),
            success = false,
            memoryUsedMB = memoryUsed / (1024 * 1024),
            threadName = threadName,
            additionalContext = context + mapOf("error" to (error?.message ?: "Unknown error"))
        )
        
        synchronized(operationHistory) {
            operationHistory.add(record)
        }
        
        Timber.w("❌ $operationName failed after ${duration}ms: ${error?.message}")
        
        // Notify completion with failure
        notifyOperationCompleted(operationName, duration, false)
    }
    
    /**
     * Get current memory usage
     */
    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    /**
     * Notify callbacks about slow operation
     */
    private fun notifySlowOperation(operationName: String, duration: Long, context: Map<String, Any>) {
        performanceCallbacks.forEach { callback ->
            try {
                callback.onSlowOperation(operationName, duration, context)
            } catch (e: Exception) {
                Timber.e(e, "Error in performance callback")
            }
        }
    }
    
    /**
     * Notify callbacks about operation completion
     */
    private fun notifyOperationCompleted(operationName: String, duration: Long, success: Boolean) {
        performanceCallbacks.forEach { callback ->
            try {
                callback.onOperationCompleted(operationName, duration, success)
            } catch (e: Exception) {
                Timber.e(e, "Error in performance callback")
            }
        }
    }
    
    /**
     * Register performance callback
     */
    fun registerCallback(callback: PerformanceCallback) {
        performanceCallbacks.add(callback)
        Timber.d("📝 Performance callback registered (total: ${performanceCallbacks.size})")
    }
    
    /**
     * Unregister performance callback
     */
    fun unregisterCallback(callback: PerformanceCallback) {
        performanceCallbacks.remove(callback)
        Timber.d("📝 Performance callback unregistered (total: ${performanceCallbacks.size})")
    }
    
    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val now = System.currentTimeMillis()
        val recentOperations = synchronized(operationHistory) {
            operationHistory.filter { now - it.timestamp < 300000L } // Last 5 minutes
        }
        
        return PerformanceSummary(
            totalOperations = totalOperations.get(),
            slowOperations = slowOperations.get(),
            verySlowOperations = verySlowOperations.get(),
            failedOperations = failedOperations.get(),
            successRate = if (totalOperations.get() > 0) {
                ((totalOperations.get() - failedOperations.get()).toDouble() / totalOperations.get()) * 100
            } else 100.0,
            averageOperationTime = recentOperations.map { it.duration }.average().takeIf { !it.isNaN() } ?: 0.0,
            activeOperationsCount = activeOperations.size,
            recentOperationsCount = recentOperations.size,
            systemMetrics = systemMetrics.getMetricsSnapshot(),
            topSlowOperations = getTopSlowOperations(10),
            operationMetrics = operationMetrics.toMap()
        )
    }
    
    /**
     * Get detailed operation metrics
     */
    fun getOperationMetrics(operationName: String): OperationMetrics? {
        return operationMetrics[operationName]
    }
    
    /**
     * Get all operation metrics
     */
    fun getAllOperationMetrics(): Map<String, OperationMetrics> {
        return operationMetrics.toMap()
    }
    
    /**
     * Get top slow operations
     */
    fun getTopSlowOperations(limit: Int = 10): List<Pair<String, OperationMetrics>> {
        return operationMetrics.entries
            .sortedByDescending { it.value.averageTime }
            .take(limit)
            .map { it.key to it.value }
    }
    
    /**
     * Get recent operation history
     */
    fun getRecentOperations(limit: Int = 100): List<OperationRecord> {
        return synchronized(operationHistory) {
            operationHistory.takeLast(limit)
        }
    }
    
    /**
     * Get active operations
     */
    fun getActiveOperations(): List<OperationContext> {
        return activeOperations.values.toList()
    }
    
    /**
     * Reset all performance metrics
     */
    fun resetMetrics() {
        operationMetrics.clear()
        synchronized(operationHistory) {
            operationHistory.clear()
        }
        activeOperations.clear()
        
        totalOperations.set(0)
        slowOperations.set(0)
        verySlowOperations.set(0)
        failedOperations.set(0)
        
        Timber.i("📊 Performance metrics reset")
    }
    
    /**
     * Export performance data
     */
    fun exportPerformanceData(): PerformanceExport {
        val summary = getPerformanceSummary()
        val recentHistory = getRecentOperations(1000)
        val activeOps = getActiveOperations()
        
        return PerformanceExport(
            timestamp = System.currentTimeMillis(),
            summary = summary,
            operationHistory = recentHistory,
            activeOperations = activeOps,
            systemInfo = getSystemInfo()
        )
    }
    
    /**
     * Get system information
     */
    private fun getSystemInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "maxMemory" to runtime.maxMemory(),
            "totalMemory" to runtime.totalMemory(),
            "freeMemory" to runtime.freeMemory(),
            "availableProcessors" to runtime.availableProcessors(),
            "androidSdk" to android.os.Build.VERSION.SDK_INT,
            "deviceModel" to android.os.Build.MODEL,
            "deviceManufacturer" to android.os.Build.MANUFACTURER
        )
    }
    
    // Data classes
    data class PerformanceSummary(
        val totalOperations: Long,
        val slowOperations: Long,
        val verySlowOperations: Long,
        val failedOperations: Long,
        val successRate: Double,
        val averageOperationTime: Double,
        val activeOperationsCount: Int,
        val recentOperationsCount: Int,
        val systemMetrics: SystemMetricsSnapshot,
        val topSlowOperations: List<Pair<String, OperationMetrics>>,
        val operationMetrics: Map<String, OperationMetrics>
    )
    
    data class PerformanceExport(
        val timestamp: Long,
        val summary: PerformanceSummary,
        val operationHistory: List<OperationRecord>,
        val activeOperations: List<OperationContext>,
        val systemInfo: Map<String, Any>
    )
}

/**
 * Extension function for easy performance measurement
 */
suspend inline fun <T> PerformanceMonitor.measure(
    operationName: String,
    noinline block: suspend () -> T
): T = measureOperation(operationName, operation = block)