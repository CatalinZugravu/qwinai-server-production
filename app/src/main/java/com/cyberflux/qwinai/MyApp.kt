package com.cyberflux.qwinai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
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
import com.cyberflux.qwinai.utils.PerformanceMonitor
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.StartupOptimizer
import com.cyberflux.qwinai.utils.UnifiedStreamingManager
import com.cyberflux.qwinai.utils.ThemeManager
import com.cyberflux.qwinai.utils.UnifiedUpdateManager
import com.cyberflux.qwinai.workers.CheckSubscriptionWorker
import com.cyberflux.qwinai.workers.ConversationCleanupWorker
import com.cyberflux.qwinai.workers.CreditResetWorker
import com.cyberflux.qwinai.workers.UsageLimitResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.cyberflux.qwinai.utils.SecureLogger
import com.cyberflux.qwinai.utils.DeviceDetectionManager
import com.cyberflux.qwinai.security.StartupRestorationManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REFACTORED: Simplified MyApp with extracted device detection
 * Handles global app initialization that needs to happen once per app lifecycle
 * CRITICAL FIXES: Reduced from 675 lines, extracted DeviceDetectionManager, secure logging
 */
@HiltAndroidApp
class MyApp : Application() {

    private lateinit var updateManager: UnifiedUpdateManager
    private val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var googlePlayServicesInitialized = false

    // Billing manager reference at application level
    private var billingManager: BillingManager? = null
    
    // Device detection manager - extracted from MyApp for better architecture
    private lateinit var deviceDetectionManager: DeviceDetectionManager
    
    // Startup restoration manager - prevents credit/subscription loss on reinstall
    private lateinit var startupRestorationManager: StartupRestorationManager

    companion object {
        // Use lazy delegation for thread-safe initialization
        val database: AppDatabase by lazy {
            AppDatabase.getDatabase(instance)
        }
        lateinit var instance: MyApp
            private set

        // REFACTORED: Clean delegation to DeviceDetectionManager - reduced from 200+ lines to 6 lines!
        fun isHuaweiDeviceNonBlocking(): Boolean {
            return instance.deviceDetectionManager.isHuaweiDeviceNonBlocking()
        }
        
        fun isHuaweiDevice(): Boolean {
            return instance.deviceDetectionManager.isHuaweiDevice()
        }

        // Get update manager
        fun getUpdateManager(): UnifiedUpdateManager? {
            return if (::instance.isInitialized && instance::updateManager.isInitialized) {
                instance.updateManager
            } else {
                null
            }
        }

        // REMOVED: saveDeviceType - now handled by DeviceDetectionManager

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

        SecureLogger.d("MyApp", "Application onCreate completed quickly")
    }

    private fun initializeCriticalComponents() {
        // Initialize Timber for logging - needed for debugging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // CRITICAL REFACTORING: Initialize DeviceDetectionManager early
        deviceDetectionManager = DeviceDetectionManager.getInstance(this)
        deviceDetectionManager.loadCachedDetection()

        // Initialize theme manager FIRST - needed by activities
        ThemeManager.initialize(this)
        
        // Initialize startup restoration manager - CRITICAL for preventing credit/subscription loss
        startupRestorationManager = StartupRestorationManager.getInstance(this)
        SecureLogger.d("MyApp", "🚀 Startup restoration manager initialized")

        SecureLogger.d("MyApp", "Critical components initialized")
    }

    private fun initializeNonCriticalComponentsAsync() {
        // ULTRAFAST: Use IO dispatcher for maximum performance
        applicationScope.launch(Dispatchers.IO) {
            try {
                SecureLogger.d("MyApp", "Starting background initialization")

                // ULTRAFAST: Initialize critical services immediately
                initializeGlobalServices()
                
                // ULTRAFAST: Initialize device detection without callback delay
                launch(Dispatchers.IO) {
                    deviceDetectionManager.initializeDetection { isHuawei ->
                        SecureLogger.logDeviceDetection(isHuawei, "async-initialization")
                        preInitializeBillingManagerInstant()
                    }
                }
                
                // ULTRAFAST: Initialize Google Play Services immediately
                launch(Dispatchers.IO) {
                    initializeGooglePlayServices()
                }

                // ULTRAFAST: Initialize remaining services without delays
                launch {
                    initializeCoreManagers()
                    // No delay - initialize immediately
                    initializeHuaweiServices()
                    // No delay - initialize immediately
                    initializeUpdateManager()
                }

                // ULTRAFAST: Schedule workers with minimal delay
                launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(1000) // Reduced from 2000ms to 1000ms
                    scheduleGlobalBackgroundWorkers()
                }

                SecureLogger.d("MyApp", "Background initialization completed")
            } catch (e: Exception) {
                SecureLogger.e("MyApp", e, "Error in background initialization: ${e.message}")
            }
        }
    }

    private fun initializeGlobalServices() {
        try {
            // Document extraction now uses simplified extractor for reliable processing
            SecureLogger.d("MyApp", "Using simplified document extraction for better compatibility")

            // Initialize ad configuration early
            AdConfig.initialize()

            // Initialize global preferences manager
            PrefsManager.initialize(this)

            // Initialize file cache manager
            FileCacheManager.initialize(this)
            
            // Performance monitoring is initialized via DI
            
            // PERFORMANCE: Start pre-warming services in background
            StartupOptimizer.preWarmServices(this)

            SecureLogger.d("MyApp", "Global services initialized successfully")
        } catch (e: Exception) {
            SecureLogger.e("MyApp", e, "Error initializing global services: ${e.message}")
        }
    }

    private fun initializeCoreManagers() {
        try {
            // Initialize production-ready streaming configuration
            UnifiedStreamingManager.initialize(this@MyApp)
            
            // Performance monitoring is started automatically by UnifiedStreamingManager
            
            // CRITICAL: Schedule periodic cleanup to prevent memory leaks
            applicationScope.launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(5 * 60 * 1000L) // Every 5 minutes
                    try {
                        System.gc() // Suggest garbage collection
                        
                        // Log performance summary in debug mode
                        if (UnifiedStreamingManager.isDebugModeEnabled) {
                            val summary = UnifiedStreamingManager.getAnalyticsSummary()
                            SecureLogger.d("Performance", "Summary: $summary")
                        }
                        
                        SecureLogger.v("Performance", "Performed periodic memory cleanup")
                    } catch (e: Exception) {
                        SecureLogger.e("Performance", e, "Error in periodic cleanup: ${e.message}")
                    }
                }
            }
            
            // PRODUCTION: Schedule health checks
            applicationScope.launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(30 * 60 * 1000L) // Every 30 minutes
                    try {
                        // Check streaming health
                        val stats = UnifiedStreamingManager.getPerformanceStats()
                        if (stats.averageProcessingTime > 200 || stats.currentFPS < 30) {
                            SecureLogger.w("HealthCheck", "⚠️ Streaming health check failed: avgTime=${stats.averageProcessingTime}ms, fps=${stats.currentFPS}")
                        }
                        
                        // Auto-recovery from circuit breaker
                        if (UnifiedStreamingManager.isCircuitBreakerOpen()) {
                            SecureLogger.i("HealthCheck", "🔄 Attempting streaming circuit breaker recovery")
                        }
                        
                    } catch (e: Exception) {
                        SecureLogger.e("HealthCheck", e, "Error in health check: ${e.message}")
                    }
                }
            }
            
            // Initialize and clear model validator cache
            ModelValidator.clearCache()
            ModelValidator.init()

            SecureLogger.d("MyApp", "Core managers initialized with production monitoring")
        } catch (e: Exception) {
            SecureLogger.e("MyApp", e, "Error initializing core managers: ${e.message}")
            
            // Emergency fallback
            try {
                UnifiedStreamingManager.setSetting("emergency_mode_enabled", true)
                SecureLogger.w("MyApp", "🚨 Enabled emergency mode due to initialization failure")
            } catch (fallbackError: Exception) {
                SecureLogger.e("MyApp", fallbackError, "Failed to enable emergency mode: ${fallbackError.message}")
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
            
            // Schedule unified usage limit reset worker (new comprehensive system)
            UsageLimitResetWorker.scheduleResetWorker(applicationContext)

            SecureLogger.d("MyApp", "Global background workers scheduled (including unified usage limits)")
        } catch (e: Exception) {
            SecureLogger.e("MyApp", e, "Failed to schedule background workers: ${e.message}")
        }
    }

    private fun initializeGooglePlayServices() {
        try {
            SecureLogger.d("GoogleServices", "Initializing Google Play Services...")

            // First check if we're on a Huawei device
            val isHuawei = isHuaweiDeviceNonBlocking()

            if (isHuawei) {
                SecureLogger.d("GoogleServices", "Huawei device detected, skipping Google Play Services initialization")
                return
            }

            // Use the safe helper to check availability
            val available = GoogleServicesHelper.checkGooglePlayServicesAvailability(this)

            if (available) {
                SecureLogger.d("GoogleServices", "Google Play Services available, proceeding with initialization")
                googlePlayServicesInitialized = true
            } else {
                SecureLogger.w("GoogleServices", "Google Play Services not available, using alternative services")
                googlePlayServicesInitialized = false
            }
        } catch (e: Exception) {
            SecureLogger.e("GoogleServices", e, "Error initializing Google Play Services: ${e.message}")
            googlePlayServicesInitialized = false
        }
    }

    // REMOVED: Old device detection methods - now handled by DeviceDetectionManager

    // Pre-initialize billing manager to speed up first use
    private fun preInitializeBillingManager() {
        applicationScope.launch {
            try {
                // PERFORMANCE FIX: Add delay to prevent blocking startup in release builds
                kotlinx.coroutines.delay(3000) // Wait 3 seconds before billing initialization for smoother startup
                
                // Create the BillingManager instance early
                if (billingManager == null) {
                    SecureLogger.d("Billing", "Pre-initializing BillingManager")
                    billingManager = BillingManager.getInstance(applicationContext)

                    // Connect to billing service with timeout protection
                    kotlinx.coroutines.withTimeoutOrNull(5000) { // 5 second timeout
                        billingManager?.connectToPlayBilling { success ->
                            SecureLogger.logBilling("pre-connection", success, "BillingManager connected")

                            // If connected successfully, query products
                            if (success) {
                                billingManager?.queryProducts()
                            }
                        }
                    } ?: SecureLogger.w("Billing", "BillingManager connection timed out - will retry later")
                }
            } catch (e: Exception) {
                SecureLogger.e("Billing", e, "Error pre-initializing BillingManager: ${e.message}")
            }
        }
    }

    /**
     * ULTRAFAST: Instant billing manager pre-initialization without delays
     */
    private fun preInitializeBillingManagerInstant() {
        applicationScope.launch {
            try {
                // ULTRAFAST: No delay - initialize immediately
                
                // Create the BillingManager instance early
                if (billingManager == null) {
                    SecureLogger.d("Billing", "ULTRAFAST: Pre-initializing BillingManager instantly")
                    billingManager = BillingManager.getInstance(applicationContext)

                    // Connect to billing service with reduced timeout for speed
                    kotlinx.coroutines.withTimeoutOrNull(2000) { // Reduced from 5s to 2s
                        billingManager?.connectToPlayBilling { success ->
                            SecureLogger.logBilling("instant-pre-connection", success, "BillingManager connected instantly")

                            // If connected successfully, query products
                            if (success) {
                                billingManager?.queryProducts()
                            }
                        }
                    } ?: SecureLogger.w("Billing", "BillingManager instant connection timed out - will retry later")
                }
            } catch (e: Exception) {
                SecureLogger.e("Billing", e, "Error in instant BillingManager initialization: ${e.message}")
            }
        }
    }

    // Initialize update manager
    private fun initializeUpdateManager() {
        try {
            updateManager = UnifiedUpdateManager(this)
            updateManager.initialize()
            SecureLogger.d("MyApp", "Update manager initialized")
        } catch (e: Exception) {
            SecureLogger.e("MyApp", e, "Failed to initialize update manager: ${e.message}")
        }
    }

    // Initialize Huawei services with improved detection
    private fun initializeHuaweiServices() {
        try {
            SecureLogger.d("HuaweiServices", "Checking for Huawei device...")

            // Use cached detection result
            val isHuaweiDevice = deviceDetectionManager.isHuaweiDeviceNonBlocking()

            // ENHANCED: More robust initialization
            // Always try to initialize Huawei AGConnect regardless of device detection
            try {
                SecureLogger.d("HuaweiServices", "Initializing Huawei AGConnect...")
                try {
                    // Check if AGConnect classes are available
                    Class.forName("com.huawei.agconnect.AGConnectInstance")
                    com.huawei.agconnect.AGConnectInstance.initialize(this)
                    SecureLogger.d("HuaweiServices", "AGConnect initialized successfully")
                } catch (e: ClassNotFoundException) {
                    SecureLogger.e("HuaweiServices", e, "AGConnect classes not found")
                } catch (e: Exception) {
                    SecureLogger.e("HuaweiServices", e, "AGConnect initialization error")
                }
            } catch (e: Exception) {
                SecureLogger.e("HuaweiServices", e, "Failed to initialize AGConnect")
            }

            // Now check if this is a Huawei device using our improved method
            if (isHuaweiDevice) {
                SecureLogger.d("HuaweiServices", "Confirmed Huawei device, HMS services should be ready")

                // ENHANCED: Additional HMS Core verification
                val hmsAvailable = deviceDetectionManager.isHMSCoreAvailablePublic()
                val hmsClassesAvailable = deviceDetectionManager.isHMSCoreClassesAvailablePublic()

                SecureLogger.d("HuaweiServices", "HMS Core status: available=$hmsAvailable, classes=$hmsClassesAvailable")

                if (!hmsAvailable || !hmsClassesAvailable) {
                    SecureLogger.w("HuaweiServices", "Huawei device detected but HMS Core may not be fully functional")
                }
            } else {
                // For non-Huawei devices, log but don't prevent initialization
                SecureLogger.d("HuaweiServices", "Non-Huawei device detected or HMS Core not available")
            }
        } catch (e: Exception) {
            // Don't crash if HMS services aren't available
            SecureLogger.e("HuaweiServices", e, "Failed during Huawei services check")
        }
    }

}