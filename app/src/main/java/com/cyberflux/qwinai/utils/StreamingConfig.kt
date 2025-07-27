package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import timber.log.Timber

/**
 * PRODUCTION-READY STREAMING CONFIGURATION
 * 
 * Centralized configuration for all streaming optimizations:
 * - Performance tuning parameters
 * - Anti-flicker settings
 * - Table-specific configurations
 * - Caching strategies
 * - Feature flags and runtime configuration
 * - Production monitoring and health checks
 */
object StreamingConfig {
    
    // Production configuration management
    private const val PREF_NAME = "streaming_config"
    private const val KEY_FEATURE_ENABLED = "streaming_continuation_enabled"
    private const val KEY_BACKGROUND_STREAMING_ENABLED = "background_streaming_enabled"
    private const val KEY_PERFORMANCE_MODE = "performance_mode"
    private const val KEY_DEBUG_MODE = "debug_mode_enabled"
    
    private lateinit var prefs: SharedPreferences
    private var isInitialized = false
    
    // Runtime feature flags
    @Volatile
    private var _featureEnabled = true
    @Volatile
    private var _backgroundStreamingEnabled = true
    @Volatile
    private var _debugModeEnabled = false
    @Volatile
    private var _performanceMode = PerformanceMode.BALANCED
    
    enum class PerformanceMode {
        HIGH_PERFORMANCE,  // Maximum responsiveness
        BALANCED,         // Balanced performance and battery
        BATTERY_SAVER     // Battery optimized
    }
    
    // === PERFORMANCE SETTINGS ===
    
    // Processing thresholds
    const val IMMEDIATE_PROCESSING_THRESHOLD = 50      // Process immediately (< 50 chars)
    const val CHUNK_SIZE = 200                         // Chunk size for long content
    const val MIN_UPDATE_INTERVAL = 8L                 // 120fps capability
    const val CONTENT_CHANGE_THRESHOLD = 5             // Minimum change to trigger update
    
    // Buffer sizes
    const val STREAM_BUFFER_SIZE = 2048                // Stream reading buffer
    const val MARKDOWN_CACHE_SIZE = 32                 // Markdown cache entries
    const val TABLE_CACHE_SIZE = 16                    // Table-specific cache
    
    // === ANTI-FLICKER SETTINGS ===
    
    // Content diffing
    const val ANTI_FLICKER_MIN_CHANGE = 2             // Minimum chars to prevent flickering
    const val CONTENT_SIMILARITY_THRESHOLD = 0.9f     // Content similarity threshold
    const val UI_UPDATE_DEBOUNCE = 8L                 // UI update debouncing
    
    // Change detection
    const val MIN_CONTENT_DIFF = 3                    // Minimum content difference
    const val PROGRESSIVE_UPDATE_THRESHOLD = 100      // Switch to progressive updates
    
    // === TABLE-SPECIFIC SETTINGS ===
    
    // Table detection
    const val MIN_TABLE_ROWS = 2                      // Minimum rows to consider table
    const val MIN_TABLE_COLUMNS = 2                   // Minimum columns for table
    const val TABLE_CONFIDENCE_THRESHOLD = 0.7f       // Confidence threshold for table detection
    
    // Table processing
    const val TABLE_BUFFER_TIMEOUT = 150L             // Wait for complete table (ms)
    const val PARTIAL_TABLE_UPDATE_DELAY = 50L        // Delay for partial table updates
    const val TABLE_COMPLETION_CHECK_INTERVAL = 25L   // Check interval for table completion
    
    // === CACHING STRATEGIES ===
    
    // Cache management
    const val CACHE_EVICTION_THRESHOLD = 75           // Start eviction at 75% capacity
    const val CACHE_ACCESS_COUNT_THRESHOLD = 3        // Keep frequently accessed items
    const val CACHE_TTL_MS = 300_000L                 // 5 minutes cache TTL
    
    // Cache priorities
    const val HIGH_PRIORITY_CACHE_SIZE = 8            // High priority cache slots
    const val MEDIUM_PRIORITY_CACHE_SIZE = 16         // Medium priority cache slots
    const val LOW_PRIORITY_CACHE_SIZE = 8             // Low priority cache slots
    
    // === PERFORMANCE MONITORING ===
    
    // Monitoring thresholds
    const val TARGET_FPS = 60.0                       // Target frame rate
    const val WARNING_FPS_THRESHOLD = 45.0            // Warning threshold
    const val CRITICAL_FPS_THRESHOLD = 30.0           // Critical threshold
    
    // Processing time targets
    const val TARGET_PROCESSING_TIME_MS = 16L         // Target processing time
    const val WARNING_PROCESSING_TIME_MS = 25L        // Warning threshold
    const val CRITICAL_PROCESSING_TIME_MS = 50L       // Critical threshold
    
    // Memory thresholds
    const val MEMORY_WARNING_THRESHOLD_MB = 100       // Memory usage warning
    const val MEMORY_CRITICAL_THRESHOLD_MB = 200      // Memory usage critical
    
    // === CONTENT TYPE SPECIFIC ===
    
    // Short content (immediate processing)
    const val SHORT_CONTENT_MAX_LENGTH = 50
    const val SHORT_CONTENT_PROCESSING_TIME_MS = 5L
    
    // Medium content (standard processing)  
    const val MEDIUM_CONTENT_MAX_LENGTH = 1000
    const val MEDIUM_CONTENT_PROCESSING_TIME_MS = 15L
    
    // Long content (chunked processing)
    const val LONG_CONTENT_CHUNK_SIZE = 500
    const val LONG_CONTENT_YIELD_INTERVAL = 5         // Yield every 5 chunks
    
    // === ADAPTIVE SETTINGS ===
    
    /**
     * Get adaptive buffer size based on content length
     */
    fun getAdaptiveBufferSize(contentLength: Int): Int {
        return when {
            contentLength < 100 -> 512
            contentLength < 1000 -> 1024
            contentLength < 5000 -> 2048
            else -> 4096
        }
    }
    
    /**
     * Get adaptive update interval based on content complexity
     */
    fun getAdaptiveUpdateInterval(hasTable: Boolean, contentLength: Int): Long {
        return when {
            hasTable -> TABLE_COMPLETION_CHECK_INTERVAL
            contentLength < SHORT_CONTENT_MAX_LENGTH -> 4L  // Ultra-fast for short content
            contentLength < MEDIUM_CONTENT_MAX_LENGTH -> MIN_UPDATE_INTERVAL
            else -> MIN_UPDATE_INTERVAL * 2  // Slower for very long content
        }
    }
    
    /**
     * Get adaptive cache size based on content type
     */
    fun getAdaptiveCacheSize(contentType: ContentType): Int {
        return when (contentType) {
            ContentType.SHORT -> HIGH_PRIORITY_CACHE_SIZE
            ContentType.MEDIUM -> MEDIUM_PRIORITY_CACHE_SIZE
            ContentType.LONG -> LOW_PRIORITY_CACHE_SIZE
            ContentType.TABLE -> TABLE_CACHE_SIZE
        }
    }
    
    /**
     * Check if content should be processed immediately
     */
    fun shouldProcessImmediately(content: String): Boolean {
        return content.length <= IMMEDIATE_PROCESSING_THRESHOLD && 
               !AdvancedTableDetector.hasTableContent(content)
    }
    
    /**
     * Check if content requires special table handling
     */
    fun requiresTableHandling(content: String): Boolean {
        return AdvancedTableDetector.hasTableContent(content)
    }
    
    /**
     * Get processing priority based on content
     */
    fun getProcessingPriority(content: String): ProcessingPriority {
        return when {
            AdvancedTableDetector.hasTableContent(content) -> ProcessingPriority.HIGH
            content.length <= SHORT_CONTENT_MAX_LENGTH -> ProcessingPriority.HIGH
            content.length <= MEDIUM_CONTENT_MAX_LENGTH -> ProcessingPriority.MEDIUM
            else -> ProcessingPriority.LOW
        }
    }
    
    enum class ContentType {
        SHORT,
        MEDIUM, 
        LONG,
        TABLE
    }
    
    enum class ProcessingPriority {
        HIGH,
        MEDIUM,
        LOW
    }
    
    /**
     * Get content type classification
     */
    fun classifyContent(content: String): ContentType {
        return when {
            AdvancedTableDetector.hasTableContent(content) -> ContentType.TABLE
            content.length <= SHORT_CONTENT_MAX_LENGTH -> ContentType.SHORT
            content.length <= MEDIUM_CONTENT_MAX_LENGTH -> ContentType.MEDIUM
            else -> ContentType.LONG
        }
    }
    
    /**
     * Performance-optimized settings for different scenarios
     */
    object Performance {
        // Ultra-fast mode (prioritize speed over quality)
        const val ULTRA_FAST_MODE = true
        const val ULTRA_FAST_UPDATE_INTERVAL = 4L
        const val ULTRA_FAST_CACHE_SIZE = 64
        
        // Balanced mode (balance speed and quality)
        const val BALANCED_MODE = false
        const val BALANCED_UPDATE_INTERVAL = 8L
        const val BALANCED_CACHE_SIZE = 32
        
        // Quality mode (prioritize quality over speed)
        const val QUALITY_MODE = false
        const val QUALITY_UPDATE_INTERVAL = 16L
        const val QUALITY_CACHE_SIZE = 16
    }
    
    // ===== PRODUCTION CONFIGURATION MANAGEMENT =====
    
    /**
     * Initialize production configuration system
     */
    fun initialize(context: Context) {
        try {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            loadRuntimeConfiguration()
            isInitialized = true
            Timber.d("✅ StreamingConfig production mode initialized")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize StreamingConfig: ${e.message}")
            isInitialized = true // Use defaults
        }
    }
    
    /**
     * Load runtime configuration from preferences
     */
    private fun loadRuntimeConfiguration() {
        _featureEnabled = prefs.getBoolean(KEY_FEATURE_ENABLED, true)
        _backgroundStreamingEnabled = prefs.getBoolean(KEY_BACKGROUND_STREAMING_ENABLED, true)
        _debugModeEnabled = prefs.getBoolean(KEY_DEBUG_MODE, false)
        _performanceMode = PerformanceMode.valueOf(
            prefs.getString(KEY_PERFORMANCE_MODE, PerformanceMode.BALANCED.name) ?: PerformanceMode.BALANCED.name
        )
        
        Timber.d("📋 Runtime config loaded: feature=$_featureEnabled, background=$_backgroundStreamingEnabled, mode=$_performanceMode")
    }
    
    // Public getters for feature flags
    val isStreamingContinuationEnabled: Boolean
        get() = if (isInitialized) _featureEnabled else true
    
    val isBackgroundStreamingEnabled: Boolean
        get() = if (isInitialized) _backgroundStreamingEnabled else true
    
    val isDebugModeEnabled: Boolean
        get() = if (isInitialized) _debugModeEnabled else false
    
    val currentPerformanceMode: PerformanceMode
        get() = if (isInitialized) _performanceMode else PerformanceMode.BALANCED
    
    /**
     * Runtime configuration updates
     */
    fun updateFeatureEnabled(enabled: Boolean) {
        if (!isInitialized) return
        _featureEnabled = enabled
        prefs.edit { putBoolean(KEY_FEATURE_ENABLED, enabled) }
        Timber.i("🔧 Streaming continuation feature: ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    fun updateBackgroundStreamingEnabled(enabled: Boolean) {
        if (!isInitialized) return
        _backgroundStreamingEnabled = enabled
        prefs.edit { putBoolean(KEY_BACKGROUND_STREAMING_ENABLED, enabled) }
        Timber.i("🔧 Background streaming: ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    fun updatePerformanceMode(mode: PerformanceMode) {
        if (!isInitialized) return
        _performanceMode = mode
        prefs.edit { putString(KEY_PERFORMANCE_MODE, mode.name) }
        Timber.i("🔧 Performance mode updated: $mode")
    }
    
    fun updateDebugMode(enabled: Boolean) {
        if (!isInitialized) return
        _debugModeEnabled = enabled
        prefs.edit { putBoolean(KEY_DEBUG_MODE, enabled) }
        Timber.i("🔧 Debug mode: ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    /**
     * Get performance-adjusted values based on current mode
     */
    fun getPerformanceAdjustedUpdateInterval(): Long {
        return when (currentPerformanceMode) {
            PerformanceMode.HIGH_PERFORMANCE -> 4L
            PerformanceMode.BALANCED -> MIN_UPDATE_INTERVAL
            PerformanceMode.BATTERY_SAVER -> 33L // 30 FPS
        }
    }
    
    fun getPerformanceAdjustedBufferSize(): Int {
        return when (currentPerformanceMode) {
            PerformanceMode.HIGH_PERFORMANCE -> STREAM_BUFFER_SIZE * 2
            PerformanceMode.BALANCED -> STREAM_BUFFER_SIZE
            PerformanceMode.BATTERY_SAVER -> STREAM_BUFFER_SIZE / 2
        }
    }
    
    fun getPerformanceAdjustedAntiFlicker(): Int {
        return when (currentPerformanceMode) {
            PerformanceMode.HIGH_PERFORMANCE -> 1    // Every character
            PerformanceMode.BALANCED -> ANTI_FLICKER_MIN_CHANGE
            PerformanceMode.BATTERY_SAVER -> ANTI_FLICKER_MIN_CHANGE * 2
        }
    }
    
    /**
     * Production health check and monitoring
     */
    fun getHealthStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "feature_enabled" to isStreamingContinuationEnabled,
            "background_enabled" to isBackgroundStreamingEnabled,
            "debug_enabled" to isDebugModeEnabled,
            "performance_mode" to currentPerformanceMode.name,
            "version" to "1.0.0",
            "config_timestamp" to System.currentTimeMillis()
        )
    }
    
    /**
     * Performance metrics and thresholds
     */
    fun isPerformanceCritical(processingTimeMs: Long): Boolean {
        return processingTimeMs > CRITICAL_PROCESSING_TIME_MS
    }
    
    fun isMemoryUsageCritical(memoryMB: Int): Boolean {
        return memoryMB > MEMORY_CRITICAL_THRESHOLD_MB
    }
    
    /**
     * Circuit breaker for production issues
     */
    @Volatile
    private var circuitBreakerOpen = false
    private var lastFailureTime = 0L
    private var failureCount = 0
    private val maxFailures = 5
    private val resetTimeoutMs = 30_000L // 30 seconds
    
    fun reportStreamingFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        if (failureCount >= maxFailures) {
            circuitBreakerOpen = true
            Timber.w("⚠️ Streaming circuit breaker OPENED after $failureCount failures")
        }
    }
    
    fun isCircuitBreakerOpen(): Boolean {
        if (circuitBreakerOpen && (System.currentTimeMillis() - lastFailureTime) > resetTimeoutMs) {
            circuitBreakerOpen = false
            failureCount = 0
            Timber.i("✅ Streaming circuit breaker RESET")
        }
        return circuitBreakerOpen
    }
    
    /**
     * Emergency fallback configuration
     */
    fun enableEmergencyMode() {
        Timber.w("🚨 EMERGENCY MODE activated - using minimal streaming features")
        _featureEnabled = false
        _backgroundStreamingEnabled = false
        _performanceMode = PerformanceMode.BATTERY_SAVER
    }
    
    fun disableEmergencyMode() {
        Timber.i("✅ Emergency mode deactivated - restoring normal operation")
        loadRuntimeConfiguration()
    }
    
    /**
     * Reset all configuration to safe defaults
     */
    fun resetToSafeDefaults() {
        if (!isInitialized) return
        
        prefs.edit {
            putBoolean(KEY_FEATURE_ENABLED, true)
            putBoolean(KEY_BACKGROUND_STREAMING_ENABLED, true)
            putBoolean(KEY_DEBUG_MODE, false)
            putString(KEY_PERFORMANCE_MODE, PerformanceMode.BALANCED.name)
        }
        
        loadRuntimeConfiguration()
        circuitBreakerOpen = false
        failureCount = 0
        
        Timber.i("🔄 StreamingConfig reset to safe defaults")
    }
}