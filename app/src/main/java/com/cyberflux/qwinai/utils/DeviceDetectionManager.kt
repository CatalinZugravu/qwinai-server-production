package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CRITICAL REFACTORING: Dedicated manager for device type detection and caching
 * Extracted from MyApp to improve maintainability and testability
 * This fixes the God Class antipattern in MyApp.kt
 */
class DeviceDetectionManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREF_DEVICE_TYPE = "device_type_huawei"
        private const val PREF_DEVICE_DETECTION_TIMESTAMP = "device_detection_timestamp"
        private const val CACHE_VALIDITY_HOURS = 24L
        
        // Force device type for testing - set to null for production
        private val FORCE_DEVICE_TYPE: Boolean? = null
        
        @Volatile
        private var instance: DeviceDetectionManager? = null
        
        fun getInstance(context: Context): DeviceDetectionManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceDetectionManager(
                    context.applicationContext,
                    context.getSharedPreferences("app_device_prefs", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
    
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Thread-safe cache for device type detection
    @Volatile
    private var isHuaweiDeviceCache: Boolean? = null
    
    // Flag to prevent duplicate detection operations
    private val isRunningDeviceDetection = AtomicBoolean(false)
    
    /**
     * Quick non-blocking check for Huawei device
     * Uses cached results and fallback to brand detection
     */
    fun isHuaweiDeviceNonBlocking(): Boolean {
        // Override detection if testing flag is set
        FORCE_DEVICE_TYPE?.let {
            SecureLogger.d("DeviceDetection", "Using forced device type: $it")
            return it
        }
        
        // First check the runtime cache
        isHuaweiDeviceCache?.let {
            return it
        }
        
        // Then try to read from persistent preferences
        try {
            if (prefs.contains(PREF_DEVICE_TYPE)) {
                val savedValue = prefs.getBoolean(PREF_DEVICE_TYPE, false)
                val timestamp = prefs.getLong(PREF_DEVICE_DETECTION_TIMESTAMP, 0)
                val ageHours = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60)
                
                if (ageHours < CACHE_VALIDITY_HOURS) {
                    // Update the runtime cache with valid saved value
                    isHuaweiDeviceCache = savedValue
                    SecureLogger.d("DeviceDetection", "Using cached preference (age: ${ageHours}h)")
                    return savedValue
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("DeviceDetection", e, "Error reading device type from preferences")
        }
        
        // Last resort: quick brand check
        val brandResult = performBrandCheck()
        SecureLogger.logDeviceDetection(brandResult, "brand-check-fallback")
        return brandResult
    }
    
    /**
     * Full device detection which may block
     * Only use when you need guaranteed accurate results
     */
    fun isHuaweiDevice(): Boolean {
        // Check for testing override
        FORCE_DEVICE_TYPE?.let {
            SecureLogger.d("DeviceDetection", "Using forced device type: $it")
            isHuaweiDeviceCache = it
            saveDeviceType(it)
            return it
        }
        
        // First check cache
        isHuaweiDeviceCache?.let {
            SecureLogger.d("DeviceDetection", "Using cached device detection result: $it")
            return it
        }
        
        // If detection is already running, use non-blocking method as temp result
        if (isRunningDeviceDetection.get()) {
            SecureLogger.d("DeviceDetection", "Detection in progress, using quick check")
            return isHuaweiDeviceNonBlocking()
        }
        
        // Try to set the running flag - if we can't, another thread is already doing detection
        if (!isRunningDeviceDetection.compareAndSet(false, true)) {
            SecureLogger.d("DeviceDetection", "Another thread doing detection, using quick check")
            return isHuaweiDeviceNonBlocking()
        }
        
        return try {
            performFullDetection()
        } finally {
            // Reset running flag
            isRunningDeviceDetection.set(false)
        }
    }
    
    /**
     * Asynchronously refresh device detection in background
     */
    fun refreshDetectionAsync(callback: ((Boolean) -> Unit)? = null) {
        detectionScope.launch {
            try {
                SecureLogger.d("DeviceDetection", "Starting async device detection refresh")
                val result = isHuaweiDevice()
                SecureLogger.logDeviceDetection(result, "async-refresh")
                callback?.invoke(result)
            } catch (e: Exception) {
                SecureLogger.e("DeviceDetection", e, "Error in async device detection")
                callback?.invoke(isHuaweiDeviceNonBlocking()) // Fallback
            }
        }
    }
    
    /**
     * Load cached device detection immediately during app startup
     */
    fun loadCachedDetection() {
        try {
            if (prefs.contains(PREF_DEVICE_TYPE)) {
                val savedIsHuawei = prefs.getBoolean(PREF_DEVICE_TYPE, false)
                isHuaweiDeviceCache = savedIsHuawei
                SecureLogger.d("DeviceDetection", "Loaded cached device detection: isHuawei=$savedIsHuawei")
            }
        } catch (e: Exception) {
            SecureLogger.e("DeviceDetection", e, "Error loading device detection cache")
        }
    }
    
    /**
     * Initialize device detection during app startup
     * Combines cached loading with background verification
     */
    fun initializeDetection(callback: ((Boolean) -> Unit)? = null) {
        SecureLogger.d("DeviceDetection", "Starting device detection initialization")
        
        // Load cached result immediately
        loadCachedDetection()
        
        // If we have a cached result, use it but verify in background
        isHuaweiDeviceCache?.let { cached ->
            SecureLogger.d("DeviceDetection", "Using cached result for immediate initialization")
            callback?.invoke(cached)
            
            // Verify in background
            refreshDetectionAsync()
            return
        }
        
        // No cached result, do immediate basic check then full detection
        val quickResult = isHuaweiDeviceNonBlocking()
        isHuaweiDeviceCache = quickResult
        callback?.invoke(quickResult)
        
        // Then do full detection asynchronously
        refreshDetectionAsync()
    }
    
    /**
     * Get current device type with explanation of detection method
     */
    fun getDeviceTypeInfo(): DeviceTypeInfo {
        val isHuawei = isHuaweiDeviceNonBlocking()
        val method = when {
            FORCE_DEVICE_TYPE != null -> "forced-for-testing"
            isHuaweiDeviceCache != null -> "cached-result"
            prefs.contains(PREF_DEVICE_TYPE) -> "persisted-preference"
            else -> "brand-detection"
        }
        
        return DeviceTypeInfo(
            isHuaweiDevice = isHuawei,
            detectionMethod = method,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            cacheTimestamp = prefs.getLong(PREF_DEVICE_DETECTION_TIMESTAMP, 0)
        )
    }
    
    private fun performBrandCheck(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
                Build.BRAND.equals("HUAWEI", ignoreCase = true) ||
                Build.MANUFACTURER.equals("HONOR", ignoreCase = true) ||
                Build.BRAND.equals("HONOR", ignoreCase = true)
    }
    
    private fun performFullDetection(): Boolean {
        val startTime = System.currentTimeMillis()
        
        // First check by brand/manufacturer (fast method)
        val isByBrand = performBrandCheck()
        
        SecureLogger.d("DeviceDetection", 
            "Full detection - Brand check: manufacturer=${Build.MANUFACTURER}, brand=${Build.BRAND}, isByBrand=$isByBrand")
        
        // For AppGallery builds, prioritize brand detection
        if (isByBrand) {
            SecureLogger.d("DeviceDetection", "Huawei/Honor device detected by brand")
            
            // Verify HMS Core availability
            val hmsAvailable = isHMSCoreAvailable()
            if (!hmsAvailable) {
                SecureLogger.w("DeviceDetection", "HMS Core not available on Huawei device, using Google billing")
                saveDeviceType(false)
                isHuaweiDeviceCache = false
                logDetectionTime(startTime, false, "brand-positive-but-no-hms")
                return false
            }
            
            // Both brand and HMS Core confirm Huawei
            saveDeviceType(true)
            isHuaweiDeviceCache = true
            logDetectionTime(startTime, true, "brand-and-hms-core")
            return true
        }
        
        // Check for HMS Core without brand match
        val hmsAvailable = isHMSCoreAvailable() && isHMSCoreClassesAvailable()
        if (hmsAvailable) {
            SecureLogger.d("DeviceDetection", "HMS Core available without brand match, using Huawei billing")
            saveDeviceType(true)
            isHuaweiDeviceCache = true
            logDetectionTime(startTime, true, "hms-core-only")
            return true
        }
        
        // Neither brand nor HMS Core detected
        saveDeviceType(false)
        isHuaweiDeviceCache = false
        logDetectionTime(startTime, false, "no-huawei-indicators")
        return false
    }
    
    private fun isHMSCoreClassesAvailable(): Boolean {
        return try {
            Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
            Class.forName("com.huawei.hms.iap.Iap")
            SecureLogger.d("DeviceDetection", "HMS Core classes detected")
            true
        } catch (e: Exception) {
            SecureLogger.d("DeviceDetection", "HMS Core classes not available: ${e.message}")
            false
        }
    }
    
    private fun isHMSCoreAvailable(): Boolean {
        return try {
            // Check for HMS Core service availability
            val intent = Intent("com.huawei.hms.core.aidlservice")
            intent.setPackage("com.huawei.hwid")
            val resolveInfo = context.packageManager.resolveService(intent, 0)
            val serviceAvailable = resolveInfo != null
            
            // Check for HMS Core app presence
            val appPresent = try {
                context.packageManager.getPackageInfo("com.huawei.hwid", 0)
                true
            } catch (_: Exception) {
                false
            }
            
            SecureLogger.d("DeviceDetection", 
                "HMS Core availability: serviceAvailable=$serviceAvailable, appPresent=$appPresent")
            serviceAvailable && appPresent
        } catch (e: Exception) {
            SecureLogger.e("DeviceDetection", e, "Error checking HMS Core availability")
            false
        }
    }
    
    private fun saveDeviceType(isHuawei: Boolean) {
        try {
            prefs.edit {
                putBoolean(PREF_DEVICE_TYPE, isHuawei)
                putLong(PREF_DEVICE_DETECTION_TIMESTAMP, System.currentTimeMillis())
            }
            SecureLogger.d("DeviceDetection", "Saved device type to preferences: isHuawei=$isHuawei")
        } catch (e: Exception) {
            SecureLogger.e("DeviceDetection", e, "Error saving device type to preferences")
        }
    }
    
    private fun logDetectionTime(startTime: Long, result: Boolean, method: String) {
        val duration = System.currentTimeMillis() - startTime
        SecureLogger.logPerformance("DeviceDetection-$method", duration, "result=$result")
    }
    
    /**
     * Public accessor for HMS Core availability check
     */
    fun isHMSCoreAvailablePublic(): Boolean {
        return isHMSCoreAvailable()
    }
    
    /**
     * Public accessor for HMS Core classes availability check
     */
    fun isHMSCoreClassesAvailablePublic(): Boolean {
        return isHMSCoreClassesAvailable()
    }
    
    /**
     * Data class for device type information
     */
    data class DeviceTypeInfo(
        val isHuaweiDevice: Boolean,
        val detectionMethod: String,
        val manufacturer: String,
        val brand: String,
        val cacheTimestamp: Long
    )
}