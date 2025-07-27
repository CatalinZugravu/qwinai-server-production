package com.cyberflux.qwinai

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cyberflux.qwinai.ads.AdConfig
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.utils.FileCacheManager
import com.cyberflux.qwinai.utils.GoogleServicesHelper
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PDFBoxResourceHelper
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.StartupOptimizer
import com.cyberflux.qwinai.utils.StreamingConfig
import com.cyberflux.qwinai.utils.StreamingPerformanceMonitor
import com.cyberflux.qwinai.utils.StreamingStateManager
import com.cyberflux.qwinai.utils.ThemeManager
import com.cyberflux.qwinai.utils.UiUtils
import com.cyberflux.qwinai.utils.UnifiedUpdateManager
import com.cyberflux.qwinai.workers.CheckSubscriptionWorker
import com.cyberflux.qwinai.workers.ConversationCleanupWorker
import com.cyberflux.qwinai.workers.CreditResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enhanced MyApp with improved Huawei device detection
 * Handles global app initialization that needs to happen once per app lifecycle
 */
class MyApp : Application() {

    private lateinit var updateManager: UnifiedUpdateManager
    private val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private var googlePlayServicesInitialized = false

    // Billing manager reference at application level
    private var billingManager: BillingManager? = null

    companion object {
        // Use lazy delegation for thread-safe initialization
        val database: AppDatabase by lazy {
            AppDatabase.getDatabase(instance)
        }
        lateinit var instance: MyApp
            private set

        // Cache for device type detection - now with volatile for thread safety
        @Volatile
        var isHuaweiDeviceCache: Boolean? = null
        private const val PREF_DEVICE_TYPE = "device_type_huawei"
        private const val PREF_DEVICE_DETECTION_TIMESTAMP = "device_detection_timestamp"

        // Flag to indicate if detection is running to prevent duplicate checks
        private val isRunningDeviceDetection = AtomicBoolean(false)

        // Force device type for testing - set to null for production
        private val FORCE_DEVICE_TYPE: Boolean? = null // Set to true/false to override detection

        /**
         * Quick non-blocking check for Huawei device
         * 1. First tries persisted preference (fastest)
         * 2. Then tries runtime cache (very fast)
         * 3. Then does a brand check (quick)
         */
        fun isHuaweiDeviceNonBlocking(): Boolean {
            // Override detection if testing flag is set
            FORCE_DEVICE_TYPE?.let {
                Timber.d("Using forced device type: $it")
                return it
            }

            // First check the runtime cache
            isHuaweiDeviceCache?.let {
                return it
            }

            // Then try to read from persistent preferences
            try {
                val prefs = instance.prefs
                if (prefs.contains(PREF_DEVICE_TYPE)) {
                    val savedValue = prefs.getBoolean(PREF_DEVICE_TYPE, false)
                    // Update the runtime cache
                    isHuaweiDeviceCache = savedValue
                    return savedValue
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reading device type from preferences")
            }

            // Last resort: quick brand check
            val isByBrand = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
                    Build.BRAND.equals("HUAWEI", ignoreCase = true) ||
                    Build.MANUFACTURER.equals("HONOR", ignoreCase = true) ||
                    Build.BRAND.equals("HONOR", ignoreCase = true)

            return isByBrand
        }

        /**
         * Full device detection which may block
         * Only use this method when you need guaranteed accurate results
         * ENHANCED: More robust HMS Core detection
         */
        fun isHuaweiDevice(): Boolean {
            // Check for testing override
            FORCE_DEVICE_TYPE?.let {
                Timber.d("Using forced device type: $it")
                isHuaweiDeviceCache = it
                saveDeviceType(it)
                return it
            }

            // First check cache
            isHuaweiDeviceCache?.let {
                Timber.d("Using cached device detection result: $it")
                return it
            }

            // If detection is already running, use non-blocking method as temp result
            if (isRunningDeviceDetection.get()) {
                Timber.d("Device detection in progress, using quick check")
                return isHuaweiDeviceNonBlocking()
            }

            // Try to set the running flag - if we can't, another thread is already doing detection
            if (!isRunningDeviceDetection.compareAndSet(false, true)) {
                Timber.d("Another thread is doing detection, using quick check")
                return isHuaweiDeviceNonBlocking()
            }

            try {
                // First check by brand/manufacturer (fast method)
                val isByBrand = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
                        Build.BRAND.equals("HUAWEI", ignoreCase = true) ||
                        Build.MANUFACTURER.equals("HONOR", ignoreCase = true) ||
                        Build.BRAND.equals("HONOR", ignoreCase = true)

                // Log brand detection
                Timber.d("Brand check: manufacturer=${Build.MANUFACTURER}, brand=${Build.BRAND}, isByBrand=$isByBrand")

                // For AppGallery builds, we prioritize brand detection to ensure Huawei handling
                if (isByBrand) {
                    Timber.d("Huawei/Honor device detected by brand")

                    // Do an immediate check for HMS Core
                    val hmsAvailable = isHMSCoreAvailable()
                    if (!hmsAvailable) {
                        Timber.d("HMS Core not available, will use Google billing instead")
                        saveDeviceType(false)
                        isHuaweiDeviceCache = false
                        return false
                    }

                    // Brand check and HMS Core check both confirm Huawei
                    saveDeviceType(true)
                    isHuaweiDeviceCache = true
                    return true
                }

                // ENHANCED: More robust HMS Core detection
                val hmsAvailable = isHMSCoreAvailable() && isHMSCoreClassesAvailable()
                if (hmsAvailable) {
                    Timber.d("HMS Core available, using Huawei billing")
                    saveDeviceType(true)
                    isHuaweiDeviceCache = true
                    return true
                }

                // Neither brand nor HMS Core detected
                saveDeviceType(false)
                isHuaweiDeviceCache = false
                return false
            } finally {
                // Reset running flag
                isRunningDeviceDetection.set(false)
            }
        }

        // ENHANCED: More robust HMS Core class detection
        private fun isHMSCoreClassesAvailable(): Boolean {
            return try {
                // Check if multiple HMS Core classes are available
                Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
                Class.forName("com.huawei.hms.iap.Iap")

                Timber.d("HMS Core classes detected")
                true
            } catch (e: Exception) {
                Timber.d("HMS Core classes not available: ${e.message}")
                false
            }
        }

        // ENHANCED: Check if HMS Core is installed and available
        private fun isHMSCoreAvailable(): Boolean {
            try {
                val context = instance.applicationContext

                // Check for HMS Core service availability - most reliable method
                val intent = Intent("com.huawei.hms.core.aidlservice")
                intent.setPackage("com.huawei.hwid")
                val resolveInfo = context.packageManager.resolveService(intent, 0)
                val serviceAvailable = resolveInfo != null

                // Also check for HMS Core app presence
                val appPresent = try {
                    context.packageManager.getPackageInfo("com.huawei.hwid", 0)
                    true
                } catch (e: Exception) {
                    false
                }

                Timber.d("HMS Core availability: serviceAvailable=$serviceAvailable, appPresent=$appPresent")
                return serviceAvailable && appPresent
            } catch (e: Exception) {
                Timber.e(e, "Error checking HMS Core availability: ${e.message}")
                return false
            }
        }

        // Get update manager
        fun getUpdateManager(): UnifiedUpdateManager? {
            return if (::instance.isInitialized && instance::updateManager.isInitialized) {
                instance.updateManager
            } else {
                null
            }
        }

        // Save device type to preferences
        private fun saveDeviceType(isHuawei: Boolean) {
            try {
                instance.prefs.edit {
                    putBoolean(PREF_DEVICE_TYPE, isHuawei)
                    putLong(PREF_DEVICE_DETECTION_TIMESTAMP, System.currentTimeMillis())
                }
                Timber.d("Saved device type to preferences: isHuawei=$isHuawei")
            } catch (e: Exception) {
                Timber.e(e, "Error saving device type to preferences: ${e.message}")
            }
        }

        // Get BillingManager instance that was pre-initialized during app startup
        fun getBillingManager(): BillingManager {
            return instance.billingManager ?: kotlin.run {
                val bm = BillingManager.getInstance(instance)
                instance.billingManager = bm
                bm
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize only critical components synchronously
        initializeCriticalComponents()

        // Initialize non-critical components asynchronously
        initializeNonCriticalComponentsAsync()

        Timber.d("Application onCreate completed quickly")
    }

    private fun initializeCriticalComponents() {
        // Initialize shared preferences first - needed immediately
        prefs = getSharedPreferences("app_device_prefs", Context.MODE_PRIVATE)

        // Initialize Timber for logging - needed for debugging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize theme manager FIRST - needed by activities
        ThemeManager.initialize(this)
        
        // PERFORMANCE: Quick device detection cache load for immediate billing decisions
        loadDeviceDetectionCache()

        Timber.d("Critical components initialized")
    }

    private fun initializeNonCriticalComponentsAsync() {
        // PERFORMANCE: Use lower priority background dispatcher to not block UI
        applicationScope.launch(Dispatchers.Default) {
            try {
                Timber.d("Starting background initialization")

                // PERFORMANCE: Initialize most critical services first
                initializeGlobalServices()
                
                // PERFORMANCE: Do device detection with lower priority
                launch(Dispatchers.IO) {
                    initializeDeviceDetection()
                    preInitializeBillingManager()
                }
                
                // PERFORMANCE: Initialize Google Play Services with lowest priority
                launch(Dispatchers.IO) {
                    initializeGooglePlayServices()
                }

                // PERFORMANCE: Initialize remaining services with delays to spread load
                launch {
                    initializeCoreManagers()
                    kotlinx.coroutines.delay(100) // Small delay to prevent blocking
                    initializeHuaweiServices()
                    kotlinx.coroutines.delay(100)
                    initializeUpdateManager()
                }

                // PERFORMANCE: Schedule workers last with lowest priority
                launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(500) // Wait for other services
                    scheduleGlobalBackgroundWorkers()
                }

                Timber.d("Background initialization completed")
            } catch (e: Exception) {
                Timber.e(e, "Error in background initialization: ${e.message}")
            }
        }
    }

    private fun initializeGlobalServices() {
        try {
            // PDFBox initialization removed due to compatibility issues
            // This prevents NumberFormatException: "For input string: 'space' under radix 16"
            Timber.d("Skipping PDFBox initialization due to device compatibility issues")

            // Initialize ad configuration early
            AdConfig.initialize()

            // Initialize global preferences manager
            PrefsManager.initialize(this)

            // Initialize file cache manager
            FileCacheManager.initialize(this)
            
            // PERFORMANCE: Start pre-warming services in background
            StartupOptimizer.preWarmServices(this)

            Timber.d("Global services initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing global services: ${e.message}")
        }
    }

    private fun initializeCoreManagers() {
        try {
            // Initialize production-ready streaming configuration
            StreamingConfig.initialize(this@MyApp)
            
            // Initialize streaming state manager with memory protection
            StreamingStateManager.initialize(this@MyApp)
            
            // Initialize performance monitoring
            StreamingPerformanceMonitor.startMonitoring()
            
            // CRITICAL: Schedule periodic cleanup to prevent memory leaks
            applicationScope.launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(5 * 60 * 1000L) // Every 5 minutes
                    try {
                        System.gc() // Suggest garbage collection
                        
                        // Log performance summary in debug mode
                        if (StreamingConfig.isDebugModeEnabled) {
                            StreamingPerformanceMonitor.logPerformanceSummary()
                        }
                        
                        Timber.v("Performed periodic memory cleanup")
                    } catch (e: Exception) {
                        Timber.e(e, "Error in periodic cleanup: ${e.message}")
                    }
                }
            }
            
            // PRODUCTION: Schedule health checks
            applicationScope.launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(30 * 60 * 1000L) // Every 30 minutes
                    try {
                        // Check streaming health
                        if (!StreamingPerformanceMonitor.isHealthy()) {
                            val alerts = StreamingPerformanceMonitor.getActiveAlerts()
                            Timber.w("⚠️ Streaming health check failed: ${alerts.joinToString(", ")}")
                        }
                        
                        // Auto-recovery from circuit breaker
                        if (StreamingConfig.isCircuitBreakerOpen()) {
                            Timber.i("🔄 Attempting streaming circuit breaker recovery")
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error in health check: ${e.message}")
                    }
                }
            }
            
            // Initialize and clear model validator cache
            ModelValidator.clearCache()
            ModelValidator.init()

            Timber.d("Core managers initialized with production monitoring")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing core managers: ${e.message}")
            
            // Emergency fallback
            try {
                StreamingConfig.enableEmergencyMode()
                Timber.w("🚨 Enabled emergency mode due to initialization failure")
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "Failed to enable emergency mode: ${fallbackError.message}")
            }
        }
    }

    private fun scheduleGlobalBackgroundWorkers() {
        try {
            // Schedule conversation cleanup
            val cleanupRequest = PeriodicWorkRequestBuilder<ConversationCleanupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(cleanupRequest)

            // Schedule subscription checker
            val checkSubscriptionRequest = PeriodicWorkRequestBuilder<CheckSubscriptionWorker>(
                12, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "subscription_checker",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                checkSubscriptionRequest
            )

            // Schedule credit reset worker
            val resetWorkRequest = PeriodicWorkRequestBuilder<CreditResetWorker>(1, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "daily_credit_reset",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                resetWorkRequest
            )

            Timber.d("Global background workers scheduled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule background workers: ${e.message}")
        }
    }

    private fun initializeGooglePlayServices() {
        try {
            Timber.d("Initializing Google Play Services...")

            // First check if we're on a Huawei device
            val isHuawei = isHuaweiDeviceNonBlocking()

            if (isHuawei) {
                Timber.d("Huawei device detected, skipping Google Play Services initialization")
                return
            }

            // Use the safe helper to check availability
            val available = GoogleServicesHelper.checkGooglePlayServicesAvailability(this)

            if (available) {
                Timber.d("Google Play Services available, proceeding with initialization")
                googlePlayServicesInitialized = true
            } else {
                Timber.w("Google Play Services not available, using alternative services")
                googlePlayServicesInitialized = false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing Google Play Services: ${e.message}")
            googlePlayServicesInitialized = false
        }
    }

    // PERFORMANCE: Optimized method to load cached device detection immediately
    private fun loadDeviceDetectionCache() {
        try {
            if (prefs.contains(PREF_DEVICE_TYPE)) {
                val savedIsHuawei = prefs.getBoolean(PREF_DEVICE_TYPE, false)
                isHuaweiDeviceCache = savedIsHuawei
                Timber.d("Loaded cached device detection: isHuawei=$savedIsHuawei")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading device detection cache: ${e.message}")
        }
    }
    
    // PERFORMANCE: Optimized device detection initialization
    private fun initializeDeviceDetection() {
        Timber.d("Starting device detection at app startup")

        // If we already have a cached result, just verify it in background
        isHuaweiDeviceCache?.let { cached ->
            Timber.d("Using cached device detection result: isHuawei=$cached")
            // Do a background refresh but don't wait for it
            refreshDeviceDetectionAsync()
            return
        }

        // First, try to load from preferences for instant results
        if (prefs.contains(PREF_DEVICE_TYPE)) {
            val savedIsHuawei = prefs.getBoolean(PREF_DEVICE_TYPE, false)
            val timestamp = prefs.getLong(PREF_DEVICE_DETECTION_TIMESTAMP, 0)
            val ageHours = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60)

            // If we have a recently saved result (under 24 hours), use it immediately
            if (ageHours < 24) {
                isHuaweiDeviceCache = savedIsHuawei
                Timber.d("Using saved device detection result: isHuawei=$savedIsHuawei (age: ${ageHours}h)")
                return
            }

            // Otherwise still use saved value as initial guess but refresh immediately
            isHuaweiDeviceCache = savedIsHuawei
            Timber.d("Using outdated saved detection result temporarily: isHuawei=$savedIsHuawei (age: ${ageHours}h)")
        }

        // Do an immediate synchronous basic check to get a very fast initial result
        val quickResult = isHuaweiDeviceNonBlocking()
        isHuaweiDeviceCache = quickResult
        Timber.d("Quick device detection result: isHuawei=$quickResult")

        // Then do a full detection asynchronously to get accurate result
        refreshDeviceDetectionAsync()
    }

    // Refresh device detection asynchronously
    private fun refreshDeviceDetectionAsync() {
        applicationScope.launch {
            try {
                Timber.d("Starting async full device detection")
                val result = withContext(Dispatchers.IO) {
                    isHuaweiDevice() // This will update the cache and prefs
                }
                Timber.d("Async device detection completed: isHuawei=$result")

                // Pre-initialize billing with the correct provider based on detection
                preInitializeBillingManager()
            } catch (e: Exception) {
                Timber.e(e, "Error in async device detection: ${e.message}")
            }
        }
    }

    // Pre-initialize billing manager to speed up first use
    private fun preInitializeBillingManager() {
        applicationScope.launch {
            try {
                // Create the BillingManager instance early
                if (billingManager == null) {
                    Timber.d("Pre-initializing BillingManager")
                    billingManager = BillingManager.getInstance(applicationContext)

                    // Connect to billing service
                    billingManager?.connectToPlayBilling { success ->
                        Timber.d("BillingManager pre-connected result: $success")

                        // If connected successfully, query products
                        if (success) {
                            billingManager?.queryProducts()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error pre-initializing BillingManager: ${e.message}")
            }
        }
    }

    // Initialize update manager
    private fun initializeUpdateManager() {
        try {
            updateManager = UnifiedUpdateManager(this)
            updateManager.initialize()
            Timber.d("Update manager initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize update manager: ${e.message}")
        }
    }

    // Initialize Huawei services with improved detection
    private fun initializeHuaweiServices() {
        try {
            Timber.d("Checking for Huawei device...")

            // Use cached detection result
            val isHuaweiDevice = isHuaweiDeviceCache ?: isHuaweiDeviceNonBlocking()

            // ENHANCED: More robust initialization
            // Always try to initialize Huawei AGConnect regardless of device detection
            try {
                Timber.d("Initializing Huawei AGConnect...")
                try {
                    // Check if AGConnect classes are available
                    Class.forName("com.huawei.agconnect.AGConnectInstance")
                    com.huawei.agconnect.AGConnectInstance.initialize(this)
                    Timber.d("AGConnect initialized successfully")
                } catch (e: ClassNotFoundException) {
                    Timber.e("AGConnect classes not found: ${e.message}")
                } catch (e: Exception) {
                    Timber.e(e, "AGConnect initialization error: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize AGConnect: ${e.message}")
            }

            // Now check if this is a Huawei device using our improved method
            if (isHuaweiDevice) {
                Timber.d("Confirmed Huawei device, HMS services should be ready")

                // ENHANCED: Additional HMS Core verification
                val hmsAvailable = isHMSCoreAvailable()
                val hmsClassesAvailable = isHMSCoreClassesAvailable()

                Timber.d("HMS Core status: available=$hmsAvailable, classes=$hmsClassesAvailable")

                if (!hmsAvailable || !hmsClassesAvailable) {
                    Timber.w("Huawei device detected but HMS Core may not be fully functional")
                }
            } else {
                // For non-Huawei devices, log but don't prevent initialization
                Timber.d("Non-Huawei device detected or HMS Core not available")
            }
        } catch (e: Exception) {
            // Don't crash if HMS services aren't available
            Timber.e(e, "Failed during Huawei services check: ${e.message}")
        }
    }
}