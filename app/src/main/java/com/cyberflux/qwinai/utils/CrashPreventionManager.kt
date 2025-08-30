package com.cyberflux.qwinai.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * Comprehensive crash prevention and recovery system
 * Handles unhandled exceptions, provides recovery mechanisms, and prevents app crashes
 */
class CrashPreventionManager private constructor(
    private val context: Context,
    private val errorManager: ErrorManager
) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "crash_prevention", Context.MODE_PRIVATE
    )
    
    private val crashCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastCrashTimes = ConcurrentHashMap<String, Long>()
    private val recoveryAttempts = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Recovery scope for handling crashes
    private val recoveryScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Error in recovery scope")
        }
    )
    
    companion object {
        @Volatile
        private var INSTANCE: CrashPreventionManager? = null
        
        fun getInstance(context: Context, errorManager: ErrorManager): CrashPreventionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CrashPreventionManager(context.applicationContext, errorManager)
                    .also { INSTANCE = it }
            }
        }
        
        private const val MAX_CRASH_COUNT = 3
        private const val CRASH_COOLDOWN_PERIOD = 300000L // 5 minutes
        private const val MAX_RECOVERY_ATTEMPTS = 5
        private const val PREF_KEY_CRASH_COUNT = "crash_count"
        private const val PREF_KEY_LAST_CRASH_TIME = "last_crash_time"
        private const val PREF_KEY_RECOVERY_MODE = "recovery_mode"
    }
    
    /**
     * Crash severity levels
     */
    enum class CrashSeverity {
        LOW,        // Recoverable errors that don't affect core functionality
        MEDIUM,     // Errors that might affect some features
        HIGH,       // Serious errors that significantly impact user experience
        CRITICAL    // Fatal errors that require app restart
    }
    
    /**
     * Recovery strategies for different crash types
     */
    enum class RecoveryStrategy {
        IGNORE,           // Log and continue
        RESTART_ACTIVITY, // Restart current activity
        CLEAR_CACHE,      // Clear app cache and continue
        SAFE_MODE,        // Enter safe mode with limited functionality
        RESTART_APP       // Force app restart
    }
    
    /**
     * Initialize crash prevention system
     */
    fun initialize() {
        setupUncaughtExceptionHandler()
        setupCoroutineExceptionHandler()
        checkForPreviousCrashes()
        
        Timber.d("Crash prevention system initialized")
    }
    
    /**
     * Setup global uncaught exception handler
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            handleUncaughtException(thread, exception, defaultHandler)
        }
    }
    
    /**
     * Setup coroutine exception handler
     */
    private fun setupCoroutineExceptionHandler() {
        // This is handled in individual coroutine scopes
        // The pattern is to use CoroutineExceptionHandler in CoroutineScope creation
    }
    
    /**
     * Handle uncaught exceptions
     */
    private fun handleUncaughtException(
        thread: Thread,
        exception: Throwable,
        defaultHandler: Thread.UncaughtExceptionHandler?
    ) {
        try {
            Timber.e(exception, "Uncaught exception in thread: ${thread.name}")
            
            // Log crash details
            logCrashDetails(exception, thread)
            
            // Determine crash severity
            val severity = determineCrashSeverity(exception)
            
            // Get recovery strategy
            val strategy = getRecoveryStrategy(exception, severity)
            
            // Track crash
            trackCrash(exception.javaClass.simpleName)
            
            // Attempt recovery
            val recovered = attemptRecovery(strategy, exception)
            
            if (!recovered) {
                // If recovery failed, use default handler or exit gracefully
                Timber.e("Recovery failed, using default crash handler")
                defaultHandler?.uncaughtException(thread, exception) ?: run {
                    exitProcess(1)
                }
            }
            
        } catch (e: Exception) {
            // If our crash handler crashes, fall back to default
            Timber.e(e, "Error in crash handler")
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * Log detailed crash information
     */
    private fun logCrashDetails(exception: Throwable, thread: Thread) {
        val stackTrace = getStackTraceString(exception)
        val crashData = mapOf(
            "exception" to exception.javaClass.simpleName,
            "message" to (exception.message ?: "No message"),
            "thread" to thread.name,
            "timestamp" to System.currentTimeMillis(),
            "stackTrace" to stackTrace,
            "memoryInfo" to getMemoryInfo(),
            "deviceInfo" to getDeviceInfo()
        )
        
        Timber.tag("CrashDetails").e("Crash occurred: $crashData")
        
        // Store crash details for later analysis
        storeCrashDetails(crashData)
    }
    
    /**
     * Determine crash severity based on exception type and context
     */
    private fun determineCrashSeverity(exception: Throwable): CrashSeverity {
        return when (exception) {
            is OutOfMemoryError,
            is StackOverflowError -> CrashSeverity.CRITICAL
            
            is SecurityException,
            is IllegalStateException -> CrashSeverity.HIGH
            
            is NullPointerException,
            is IllegalArgumentException,
            is ClassCastException -> CrashSeverity.MEDIUM
            
            is RuntimeException -> CrashSeverity.MEDIUM
            
            else -> CrashSeverity.LOW
        }
    }
    
    /**
     * Get recovery strategy based on crash severity and type
     */
    private fun getRecoveryStrategy(exception: Throwable, severity: CrashSeverity): RecoveryStrategy {
        // Check if we've had too many recent crashes
        val recentCrashes = getRecentCrashCount()
        if (recentCrashes >= MAX_CRASH_COUNT) {
            return RecoveryStrategy.RESTART_APP
        }
        
        return when (severity) {
            CrashSeverity.CRITICAL -> RecoveryStrategy.RESTART_APP
            CrashSeverity.HIGH -> when (exception) {
                is SecurityException -> RecoveryStrategy.SAFE_MODE
                else -> RecoveryStrategy.RESTART_ACTIVITY
            }
            CrashSeverity.MEDIUM -> RecoveryStrategy.CLEAR_CACHE
            CrashSeverity.LOW -> RecoveryStrategy.IGNORE
        }
    }
    
    /**
     * Attempt to recover from crash
     */
    private fun attemptRecovery(strategy: RecoveryStrategy, exception: Throwable): Boolean {
        return try {
            when (strategy) {
                RecoveryStrategy.IGNORE -> {
                    Timber.d("Ignoring low-severity crash")
                    true
                }
                
                RecoveryStrategy.RESTART_ACTIVITY -> {
                    Timber.i("Attempting to restart current activity")
                    restartCurrentActivity()
                    true
                }
                
                RecoveryStrategy.CLEAR_CACHE -> {
                    Timber.i("Clearing app cache for recovery")
                    clearAppCache()
                    true
                }
                
                RecoveryStrategy.SAFE_MODE -> {
                    Timber.i("Entering safe mode")
                    enterSafeMode()
                    true
                }
                
                RecoveryStrategy.RESTART_APP -> {
                    Timber.i("Restarting application")
                    restartApplication()
                    false // This will exit the app
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Recovery attempt failed")
            false
        }
    }
    
    /**
     * Restart current activity
     */
    private fun restartCurrentActivity() {
        recoveryScope.launch {
            try {
                mainHandler.post {
                    // This would need to be implemented with activity reference
                    // For now, we'll just log the intent
                    Timber.d("Activity restart requested - implementation depends on current activity reference")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restart activity")
            }
        }
    }
    
    /**
     * Clear app cache
     */
    private fun clearAppCache() {
        recoveryScope.launch {
            try {
                // Clear various caches
                context.cacheDir.deleteRecursively()
                
                // Clear shared preferences if needed
                preferences.edit().clear().apply()
                
                Timber.d("App cache cleared for recovery")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear app cache")
            }
        }
    }
    
    /**
     * Enter safe mode with limited functionality
     */
    private fun enterSafeMode() {
        preferences.edit().putBoolean(PREF_KEY_RECOVERY_MODE, true).apply()
        Timber.i("Safe mode enabled")
    }
    
    /**
     * Restart application
     */
    private fun restartApplication() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            
            if (intent != null) {
                context.startActivity(intent)
            }
            
            // Exit current process
            exitProcess(0)
        } catch (e: Exception) {
            Timber.e(e, "Failed to restart application")
            exitProcess(1)
        }
    }
    
    /**
     * Track crash occurrence
     */
    private fun trackCrash(crashType: String) {
        val count = crashCounts.getOrPut(crashType) { AtomicInteger(0) }
        count.incrementAndGet()
        lastCrashTimes[crashType] = System.currentTimeMillis()
        
        // Store in preferences for persistence
        preferences.edit()
            .putInt(PREF_KEY_CRASH_COUNT, getRecentCrashCount())
            .putLong(PREF_KEY_LAST_CRASH_TIME, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get recent crash count
     */
    private fun getRecentCrashCount(): Int {
        val currentTime = System.currentTimeMillis()
        return lastCrashTimes.values.count { lastCrashTime ->
            (currentTime - lastCrashTime) < CRASH_COOLDOWN_PERIOD
        }
    }
    
    /**
     * Check for previous crashes on app start
     */
    private fun checkForPreviousCrashes() {
        val lastCrashTime = preferences.getLong(PREF_KEY_LAST_CRASH_TIME, 0)
        val crashCount = preferences.getInt(PREF_KEY_CRASH_COUNT, 0)
        val inRecoveryMode = preferences.getBoolean(PREF_KEY_RECOVERY_MODE, false)
        
        if (lastCrashTime > 0) {
            val timeSinceLastCrash = System.currentTimeMillis() - lastCrashTime
            Timber.i("Previous crash detected: count=$crashCount, timeSince=${timeSinceLastCrash}ms, recoveryMode=$inRecoveryMode")
            
            // If it's been a while since last crash, reset counters
            if (timeSinceLastCrash > CRASH_COOLDOWN_PERIOD) {
                preferences.edit()
                    .remove(PREF_KEY_CRASH_COUNT)
                    .remove(PREF_KEY_LAST_CRASH_TIME)
                    .remove(PREF_KEY_RECOVERY_MODE)
                    .apply()
                
                Timber.d("Crash counters reset after cooldown period")
            }
        }
    }
    
    /**
     * Get stack trace as string
     */
    private fun getStackTraceString(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
    
    /**
     * Get memory information
     */
    private fun getMemoryInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "maxMemory" to runtime.maxMemory(),
            "totalMemory" to runtime.totalMemory(),
            "freeMemory" to runtime.freeMemory(),
            "usedMemory" to (runtime.totalMemory() - runtime.freeMemory())
        )
    }
    
    /**
     * Get device information
     */
    private fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "model" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "version" to android.os.Build.VERSION.RELEASE,
            "sdk" to android.os.Build.VERSION.SDK_INT
        )
    }
    
    /**
     * Store crash details for analysis
     */
    private fun storeCrashDetails(crashData: Map<String, Any>) {
        recoveryScope.launch {
            try {
                // Here you could store to local database or send to analytics
                Timber.tag("CrashStorage").d("Crash details stored: ${crashData.keys}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to store crash details")
            }
        }
    }
    
    /**
     * Check if app is in safe mode
     */
    fun isInSafeMode(): Boolean {
        return preferences.getBoolean(PREF_KEY_RECOVERY_MODE, false)
    }
    
    /**
     * Exit safe mode
     */
    fun exitSafeMode() {
        preferences.edit().remove(PREF_KEY_RECOVERY_MODE).apply()
        Timber.i("Safe mode disabled")
    }
    
    /**
     * Get crash statistics
     */
    fun getCrashStatistics(): Map<String, Any> {
        return mapOf(
            "crashCounts" to crashCounts.mapValues { it.value.get() },
            "lastCrashTimes" to lastCrashTimes.toMap(),
            "recentCrashCount" to getRecentCrashCount(),
            "recoveryAttempts" to recoveryAttempts.get(),
            "isInSafeMode" to isInSafeMode()
        )
    }
    
    /**
     * Create safe coroutine exception handler
     */
    fun createSafeCoroutineExceptionHandler(
        operation: String = "unknown"
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            recoveryScope.launch {
                try {
                    Timber.e(exception, "Coroutine exception in operation: $operation")
                    
                    // Handle the error through ErrorManager
                    errorManager.handleError(exception, operation, showToUser = false)
                    
                    // Track as a handled crash
                    trackCrash("coroutine_${exception.javaClass.simpleName}")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error handling coroutine exception")
                }
            }
        }
    }
    
    /**
     * Wrap potentially dangerous operations
     */
    suspend fun <T> safeExecute(
        operation: String,
        fallbackValue: T? = null,
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            Timber.e(e, "Safe execution failed for operation: $operation")
            
            // Handle through error manager
            errorManager.handleError(e, operation, showToUser = false)
            
            fallbackValue
        }
    }
    
    /**
     * Reset crash prevention state
     */
    fun reset() {
        crashCounts.clear()
        lastCrashTimes.clear()
        recoveryAttempts.set(0)
        preferences.edit().clear().apply()
        
        Timber.d("Crash prevention state reset")
    }
}