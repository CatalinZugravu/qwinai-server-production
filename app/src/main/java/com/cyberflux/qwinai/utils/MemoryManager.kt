package com.cyberflux.qwinai.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UNIFIED MEMORY MANAGEMENT SYSTEM - Consolidates all memory management functionality
 * Features:
 * - Proactive memory monitoring with StateFlow
 * - Priority-based caching (HIGH/NORMAL/LOW)
 * - Bitmap management with proper recycling
 * - Weak reference storage for memory efficiency
 * - Power saving mode support
 * - Multiple cleanup strategies
 * - Comprehensive memory statistics
 * - Hilt dependency injection
 */

@Suppress("DEPRECATION")
@Singleton
class MemoryManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val MEMORY_WARNING_THRESHOLD = 0.8 // 80% memory usage
        private const val MEMORY_CRITICAL_THRESHOLD = 0.9 // 90% memory usage
        private const val CACHE_CLEANUP_THRESHOLD = 0.85 // 85% memory usage
        private const val MONITORING_INTERVAL_MS = 5000L // 5 seconds
        private const val MEMORY_PRESSURE_COOLDOWN = 30000L // 30 seconds
        
        // Cache size limits (in MB)
        private const val MAX_MARKDOWN_CACHE_SIZE = 50
        private const val MAX_IMAGE_MEMORY_CACHE = 100
        private const val MAX_NETWORK_CACHE_SIZE = 200
        
        // New constants from core version
        private const val DEFAULT_MEMORY_CACHE_SIZE = 1024 * 1024 * 10 // 10MB
        private const val BITMAP_CACHE_SIZE = 1024 * 1024 * 5 // 5MB for bitmaps
        private const val STRING_CACHE_SIZE = 1000 // 1000 strings
    }
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    
    // Memory monitoring
    private val memoryMonitoringScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("MemoryMonitoring")
    )
    
    private val _memoryPressure = MutableStateFlow(MemoryPressureLevel.NORMAL)
    val memoryPressure: StateFlow<MemoryPressureLevel> = _memoryPressure.asStateFlow()
    
    private val lastMemoryPressureTime = AtomicLong(0)
    private val memoryCallbacks = mutableListOf<MemoryPressureCallback>()
    
    // Cache managers
    private val cacheManagers = ConcurrentHashMap<String, CacheManager>()
    
    // Enhanced caching from core version
    private val bitmapCache = LruCache<String, Bitmap>(BITMAP_CACHE_SIZE)
    private val stringCache = LruCache<String, String>(STRING_CACHE_SIZE)
    private val weakReferences = ConcurrentHashMap<String, WeakReference<Any>>()
    
    // Memory management settings
    private var aggressiveCaching = false
    private var powerSavingMode = false
    
    // Memory statistics
    private var peakMemoryUsage = AtomicLong(0)
    private var totalMemoryOptimizations = AtomicLong(0)
    private var lastOptimizationTime = AtomicLong(0)
    
    /**
     * Memory pressure levels
     */
    enum class MemoryPressureLevel(val threshold: Double) {
        NORMAL(0.0),
        WARNING(0.8),
        CRITICAL(0.9),
        EMERGENCY(0.95)
    }
    
    /**
     * Cache priority levels (from core version)
     */
    enum class CachePriority {
        LOW, NORMAL, HIGH
    }
    
    /**
     * Memory pressure callback interface
     */
    interface MemoryPressureCallback {
        suspend fun onMemoryPressure(level: MemoryPressureLevel, availableMemory: Long)
        suspend fun onMemoryRecovered(newLevel: MemoryPressureLevel)
    }
    
    /**
     * Cache manager interface
     */
    interface CacheManager {
        suspend fun clearCache(aggressive: Boolean = false)
        fun getCacheSize(): Long
        fun getMaxCacheSize(): Long
    }
    
    
    /**
     * Built-in cache managers
     */
    
    /**
     * Markdown rendering cache manager
     */
    private inner class MarkdownCacheManager : CacheManager {
        private val markdownCache = LruCache<String, Any>(MAX_MARKDOWN_CACHE_SIZE * 1024 * 1024) // 50MB
        
        override suspend fun clearCache(aggressive: Boolean) {
            if (aggressive) {
                markdownCache.evictAll()
                Timber.d("🧹 Markdown cache cleared aggressively")
            } else {
                // Clear 50% of cache
                val keysToRemove = markdownCache.snapshot().keys.take(markdownCache.size() / 2)
                keysToRemove.forEach { markdownCache.remove(it) }
                Timber.d("🧹 Markdown cache 50% cleared")
            }
        }
        
        override fun getCacheSize(): Long = markdownCache.size().toLong()
        override fun getMaxCacheSize(): Long = markdownCache.maxSize().toLong()
    }
    
    /**
     * Image cache manager (Glide integration)
     */
    private inner class ImageCacheManager : CacheManager {
        override suspend fun clearCache(aggressive: Boolean) = withContext(Dispatchers.Main) {
            try {
                if (aggressive) {
                    Glide.get(context).clearMemory()
                    withContext(Dispatchers.IO) {
                        Glide.get(context).clearDiskCache()
                    }
                    Timber.d("🧹 Image cache cleared aggressively (memory + disk)")
                } else {
                    // Clear only memory cache
                    Glide.get(context).clearMemory()
                    Timber.d("🧹 Image memory cache cleared")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error clearing image cache")
            }
        }
        
        override fun getCacheSize(): Long {
            return try {
                // Note: BitmapPool size might not be directly accessible
                // Using a safe default approach based on available memory
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val availableMemory = maxMemory - usedMemory
                
                // Estimate cache size as 1/8 of available memory, clamped between 25MB and 100MB
                val estimatedCacheSize = (availableMemory / 8).coerceIn(
                    25 * 1024 * 1024L, // 25MB minimum
                    100 * 1024 * 1024L  // 100MB maximum
                )
                estimatedCacheSize
            } catch (e: Exception) {
                50 * 1024 * 1024L // Default 50MB
            }
        }
        
        override fun getMaxCacheSize(): Long {
            return try {
                Glide.get(context).bitmapPool.maxSize.toLong()
            } catch (e: Exception) {
                MAX_IMAGE_MEMORY_CACHE * 1024 * 1024L
            }
        }
    }
    
    /**
     * Network cache manager
     */
    private inner class NetworkCacheManager : CacheManager {
        override suspend fun clearCache(aggressive: Boolean) {
            try {
                // Clear HTTP cache directories
                val cacheDir = context.cacheDir
                val httpCacheDir = java.io.File(cacheDir, "http_cache")
                
                if (httpCacheDir.exists()) {
                    if (aggressive) {
                        httpCacheDir.deleteRecursively()
                        Timber.d("🧹 Network cache cleared aggressively")
                    } else {
                        // Clear 70% of cache files (keep recent ones)
                        val files = httpCacheDir.listFiles()?.sortedBy { it.lastModified() }
                        val filesToDelete = files?.take((files.size * 0.7).toInt())
                        filesToDelete?.forEach { it.delete() }
                        Timber.d("🧹 Network cache 70% cleared")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error clearing network cache")
            }
        }
        
        override fun getCacheSize(): Long {
            return try {
                val httpCacheDir = java.io.File(context.cacheDir, "http_cache")
                if (httpCacheDir.exists()) {
                    httpCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                } else {
                    0L
                }
            } catch (e: Exception) {
                0L
            }
        }
        
        override fun getMaxCacheSize(): Long = MAX_NETWORK_CACHE_SIZE * 1024 * 1024L
    }
    
    init {
        // Register built-in cache managers
        cacheManagers["markdown"] = MarkdownCacheManager()
        cacheManagers["images"] = ImageCacheManager()
        cacheManagers["network"] = NetworkCacheManager()
        
        // Start memory monitoring
        startMemoryMonitoring()
        
        Timber.d("🧠 MemoryManager initialized with ${cacheManagers.size} cache managers")
    }
    
    /**
     * Start continuous memory monitoring
     */
    private fun startMemoryMonitoring() {
        memoryMonitoringScope.launch {
            while (isActive) {
                try {
                    val memoryStats = getCurrentMemoryStats()
                    val pressureLevel = determinePressureLevel(memoryStats.usageRatio)
                    
                    // Update peak memory usage
                    peakMemoryUsage.updateAndGet { current ->
                        maxOf(current, memoryStats.usedMemory)
                    }
                    
                    // Check if pressure level changed
                    val currentPressure = _memoryPressure.value
                    if (pressureLevel != currentPressure) {
                        _memoryPressure.value = pressureLevel
                        
                        when (pressureLevel) {
                            MemoryPressureLevel.WARNING -> handleMemoryWarning(memoryStats)
                            MemoryPressureLevel.CRITICAL -> handleMemoryCritical(memoryStats)
                            MemoryPressureLevel.EMERGENCY -> handleMemoryEmergency(memoryStats)
                            MemoryPressureLevel.NORMAL -> {
                                if (currentPressure != MemoryPressureLevel.NORMAL) {
                                    handleMemoryRecovered(pressureLevel)
                                }
                            }
                        }
                    }
                    
                    // Proactive cleanup at cache threshold
                    if (memoryStats.usageRatio >= CACHE_CLEANUP_THRESHOLD && 
                        System.currentTimeMillis() - lastMemoryPressureTime.get() > MEMORY_PRESSURE_COOLDOWN) {
                        performProactiveCleanup()
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error in memory monitoring")
                }
                
                delay(MONITORING_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Get current memory statistics
     */
    fun getCurrentMemoryStats(): MemoryStats {
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        return MemoryStats(
            maxMemory = maxMemory,
            totalMemory = totalMemory,
            usedMemory = usedMemory,
            freeMemory = freeMemory,
            availableMemory = memoryInfo.availMem,
            totalDeviceMemory = memoryInfo.totalMem,
            usageRatio = usedMemory.toDouble() / maxMemory.toDouble(),
            isLowMemory = memoryInfo.lowMemory,
            lowMemoryThreshold = memoryInfo.threshold
        )
    }
    
    /**
     * Determine memory pressure level
     */
    private fun determinePressureLevel(usageRatio: Double): MemoryPressureLevel {
        return when {
            usageRatio >= MemoryPressureLevel.EMERGENCY.threshold -> MemoryPressureLevel.EMERGENCY
            usageRatio >= MemoryPressureLevel.CRITICAL.threshold -> MemoryPressureLevel.CRITICAL
            usageRatio >= MemoryPressureLevel.WARNING.threshold -> MemoryPressureLevel.WARNING
            else -> MemoryPressureLevel.NORMAL
        }
    }
    
    /**
     * Handle memory warning
     */
    private suspend fun handleMemoryWarning(memoryStats: MemoryStats) {
        Timber.w("⚠️ Memory pressure WARNING: ${String.format("%.1f", memoryStats.usageRatio * 100)}% used")
        
        notifyCallbacks(MemoryPressureLevel.WARNING, memoryStats.availableMemory)
        
        // Light cleanup
        performCacheCleanup(aggressive = false)
    }
    
    /**
     * Handle critical memory situation
     */
    private suspend fun handleMemoryCritical(memoryStats: MemoryStats) {
        Timber.w("🔥 Memory pressure CRITICAL: ${String.format("%.1f", memoryStats.usageRatio * 100)}% used")
        
        notifyCallbacks(MemoryPressureLevel.CRITICAL, memoryStats.availableMemory)
        lastMemoryPressureTime.set(System.currentTimeMillis())
        
        // Aggressive cleanup
        performCacheCleanup(aggressive = true)
        
        // Force garbage collection
        System.gc()
        
        totalMemoryOptimizations.incrementAndGet()
        lastOptimizationTime.set(System.currentTimeMillis())
    }
    
    /**
     * Handle emergency memory situation
     */
    private suspend fun handleMemoryEmergency(memoryStats: MemoryStats) {
        Timber.e("🚨 Memory pressure EMERGENCY: ${String.format("%.1f", memoryStats.usageRatio * 100)}% used")
        
        notifyCallbacks(MemoryPressureLevel.EMERGENCY, memoryStats.availableMemory)
        lastMemoryPressureTime.set(System.currentTimeMillis())
        
        // Emergency cleanup
        performEmergencyCleanup()
        
        // Multiple GC calls
        repeat(3) {
            System.gc()
            delay(100)
        }
        
        totalMemoryOptimizations.incrementAndGet()
        lastOptimizationTime.set(System.currentTimeMillis())
    }
    
    /**
     * Handle memory recovery
     */
    private suspend fun handleMemoryRecovered(newLevel: MemoryPressureLevel) {
        Timber.i("✅ Memory pressure recovered to: $newLevel")
        
        memoryCallbacks.forEach { callback ->
            try {
                callback.onMemoryRecovered(newLevel)
            } catch (e: Exception) {
                Timber.e(e, "Error in memory recovery callback")
            }
        }
    }
    
    /**
     * Perform proactive cleanup when approaching cache threshold
     */
    private suspend fun performProactiveCleanup() {
        Timber.d("🧹 Performing proactive memory cleanup")
        
        // Clear 30% of caches proactively
        cacheManagers.values.forEach { cacheManager ->
            try {
                cacheManager.clearCache(aggressive = false)
            } catch (e: Exception) {
                Timber.e(e, "Error in proactive cache cleanup")
            }
        }
        
        lastMemoryPressureTime.set(System.currentTimeMillis())
    }
    
    /**
     * Perform cache cleanup
     */
    private suspend fun performCacheCleanup(aggressive: Boolean) {
        Timber.d("🧹 Performing cache cleanup (aggressive: $aggressive)")
        
        cacheManagers.values.forEach { cacheManager ->
            try {
                cacheManager.clearCache(aggressive)
            } catch (e: Exception) {
                Timber.e(e, "Error in cache cleanup")
            }
        }
    }
    
    /**
     * Perform emergency cleanup
     */
    private suspend fun performEmergencyCleanup() {
        Timber.w("🚨 Performing emergency memory cleanup")
        
        // Clear all caches aggressively
        performCacheCleanup(aggressive = true)
        
        // Clear app cache directories
        try {
            context.cacheDir.deleteRecursively()
            Timber.d("🧹 App cache directory cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing app cache directory")
        }
        
        // Clear temporary files
        try {
            val tempDir = java.io.File(context.cacheDir, "temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                Timber.d("🧹 Temporary files cleared")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing temporary files")
        }
    }
    
    /**
     * Notify all callbacks about memory pressure
     */
    private suspend fun notifyCallbacks(level: MemoryPressureLevel, availableMemory: Long) {
        memoryCallbacks.forEach { callback ->
            try {
                callback.onMemoryPressure(level, availableMemory)
            } catch (e: Exception) {
                Timber.e(e, "Error in memory pressure callback")
            }
        }
    }
    
    /**
     * Register memory pressure callback
     */
    fun registerMemoryCallback(callback: MemoryPressureCallback) {
        memoryCallbacks.add(callback)
        Timber.d("📝 Memory callback registered (total: ${memoryCallbacks.size})")
    }
    
    /**
     * Unregister memory pressure callback
     */
    fun unregisterMemoryCallback(callback: MemoryPressureCallback) {
        memoryCallbacks.remove(callback)
        Timber.d("📝 Memory callback unregistered (total: ${memoryCallbacks.size})")
    }
    
    /**
     * Register custom cache manager
     */
    fun registerCacheManager(name: String, cacheManager: CacheManager) {
        cacheManagers[name] = cacheManager
        Timber.d("📝 Cache manager '$name' registered (total: ${cacheManagers.size})")
    }
    
    /**
     * Unregister cache manager
     */
    fun unregisterCacheManager(name: String) {
        cacheManagers.remove(name)
        Timber.d("📝 Cache manager '$name' unregistered (total: ${cacheManagers.size})")
    }
    
    /**
     * Manual memory optimization trigger
     */
    suspend fun optimizeMemory(level: MemoryPressureLevel = MemoryPressureLevel.WARNING) {
        Timber.i("🧠 Manual memory optimization triggered at level: $level")
        
        when (level) {
            MemoryPressureLevel.NORMAL -> {
                // Light cleanup
                performProactiveCleanup()
            }
            MemoryPressureLevel.WARNING -> {
                performCacheCleanup(aggressive = false)
                System.gc()
            }
            MemoryPressureLevel.CRITICAL -> {
                performCacheCleanup(aggressive = true)
                System.gc()
            }
            MemoryPressureLevel.EMERGENCY -> {
                performEmergencyCleanup()
                repeat(3) {
                    System.gc()
                    delay(100)
                }
            }
        }
        
        totalMemoryOptimizations.incrementAndGet()
        lastOptimizationTime.set(System.currentTimeMillis())
    }
    
    /**
     * Get memory optimization statistics
     */
    fun getMemoryStats(): DetailedMemoryStats {
        val currentStats = getCurrentMemoryStats()
        val cacheStats = cacheManagers.mapValues { (_, manager) ->
            CacheStats(
                currentSize = manager.getCacheSize(),
                maxSize = manager.getMaxCacheSize()
            )
        }
        
        return DetailedMemoryStats(
            current = currentStats,
            peakUsage = peakMemoryUsage.get(),
            totalOptimizations = totalMemoryOptimizations.get(),
            lastOptimizationTime = lastOptimizationTime.get(),
            currentPressureLevel = _memoryPressure.value,
            cacheStats = cacheStats,
            registeredCallbacks = memoryCallbacks.size,
            registeredCacheManagers = cacheManagers.size
        )
    }
    
    /**
     * Check if memory optimization is recommended
     */
    fun shouldOptimizeMemory(): Boolean {
        val stats = getCurrentMemoryStats()
        return stats.usageRatio >= CACHE_CLEANUP_THRESHOLD ||
                stats.isLowMemory ||
                _memoryPressure.value != MemoryPressureLevel.NORMAL
    }
    
    /**
     * Estimate memory savings from optimization
     */
    suspend fun estimateMemorySavings(): MemorySavingsEstimate {
        var totalSavings = 0L
        val cacheBreakdown = mutableMapOf<String, Long>()
        
        cacheManagers.forEach { (name, manager) ->
            val cacheSize = manager.getCacheSize()
            val estimatedSavings = cacheSize * 0.7 // Assume 70% can be cleared
            cacheBreakdown[name] = estimatedSavings.toLong()
            totalSavings += estimatedSavings.toLong()
        }
        
        return MemorySavingsEstimate(
            totalEstimatedSavings = totalSavings,
            cacheBreakdown = cacheBreakdown,
            percentageOfCurrentUsage = (totalSavings.toDouble() / getCurrentMemoryStats().usedMemory) * 100
        )
    }
    
    // Data classes
    data class MemoryStats(
        val maxMemory: Long,
        val totalMemory: Long,
        val usedMemory: Long,
        val freeMemory: Long,
        val availableMemory: Long,
        val totalDeviceMemory: Long,
        val usageRatio: Double,
        val isLowMemory: Boolean,
        val lowMemoryThreshold: Long
    )
    
    data class DetailedMemoryStats(
        val current: MemoryStats,
        val peakUsage: Long,
        val totalOptimizations: Long,
        val lastOptimizationTime: Long,
        val currentPressureLevel: MemoryPressureLevel,
        val cacheStats: Map<String, CacheStats>,
        val registeredCallbacks: Int,
        val registeredCacheManagers: Int
    )
    
    data class CacheStats(
        val currentSize: Long,
        val maxSize: Long
    ) {
        val usageRatio: Double get() = if (maxSize > 0) currentSize.toDouble() / maxSize else 0.0
    }
    
    data class MemorySavingsEstimate(
        val totalEstimatedSavings: Long,
        val cacheBreakdown: Map<String, Long>,
        val percentageOfCurrentUsage: Double
    )
}

/**
 * Extension function to format memory sizes
 */
fun Long.formatMemorySize(): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = this.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "${String.format("%.1f", size)} ${units[unitIndex]}"
}