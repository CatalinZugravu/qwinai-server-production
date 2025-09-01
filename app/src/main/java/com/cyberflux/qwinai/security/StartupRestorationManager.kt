package com.cyberflux.qwinai.security

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.utils.PrefsManager
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Comprehensive startup restoration manager that ensures user state consistency
 * across app installations and prevents credit/subscription loss.
 * 
 * This manager:
 * 1. Runs automatically on app startup
 * 2. Restores subscription status from app stores
 * 3. Restores user credit consumption state from server
 * 4. Prevents users from getting fresh credits after reinstall
 * 5. Provides debug information for troubleshooting
 */
class StartupRestorationManager private constructor(private val context: Context) : DefaultLifecycleObserver {
    
    companion object {
        @Volatile
        private var INSTANCE: StartupRestorationManager? = null
        
        private const val PREFS_NAME = "startup_restoration"
        private const val RESTORATION_COMPLETED_KEY = "restoration_completed_v2"
        private const val LAST_RESTORATION_TIME_KEY = "last_restoration_time"
        private const val RESTORATION_ATTEMPTS_KEY = "restoration_attempts"
        private const val MAX_RESTORATION_ATTEMPTS = 3
        private const val RESTORATION_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        
        fun getInstance(context: Context): StartupRestorationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StartupRestorationManager(context.applicationContext).also { 
                    INSTANCE = it
                    // Register lifecycle observer
                    ProcessLifecycleOwner.get().lifecycle.addObserver(it)
                }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRestorationInProgress = AtomicBoolean(false)
    
    private val userStateManager: UserStateManager by lazy {
        UserStateManager.getInstance(context)
    }
    
    private val billingManager: BillingManager by lazy {
        BillingManager.getInstance(context)
    }
    
    private val creditManager: CreditManager by lazy {
        CreditManager.getInstance(context)
    }
    
    init {
        Timber.d("🚀 StartupRestorationManager initialized")
    }
    
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        // Trigger restoration when app starts
        triggerStartupRestoration()
    }
    
    /**
     * Main restoration flow - called on app startup
     */
    private fun triggerStartupRestoration() {
        scope.launch {
            try {
                if (!shouldPerformRestoration()) {
                    Timber.d("🚀 Skipping restoration - not needed or in cooldown")
                    return@launch
                }
                
                if (!isRestorationInProgress.compareAndSet(false, true)) {
                    Timber.d("🚀 Restoration already in progress")
                    return@launch
                }
                
                Timber.d("🚀 Starting comprehensive app restoration...")
                
                val result = performCompleteRestoration()
                
                if (result.success) {
                    markRestorationCompleted()
                    Timber.d("✅ Startup restoration completed successfully")
                } else {
                    incrementRestorationAttempts()
                    Timber.w("⚠️ Startup restoration failed: ${result.message}")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Startup restoration crashed")
                incrementRestorationAttempts()
            } finally {
                isRestorationInProgress.set(false)
            }
        }
    }
    
    /**
     * Comprehensive restoration flow
     */
    private suspend fun performCompleteRestoration(): RestorationResult {
        return withContext(Dispatchers.IO) {
            val steps = mutableListOf<RestorationStep>()
            
            try {
                // Step 1: Initialize device fingerprinting
                steps.add(performDeviceFingerprinting())
                
                // Step 2: Restore subscription status
                steps.add(performSubscriptionRestoration())
                
                // Step 3: Restore credit state
                steps.add(performCreditStateRestoration())
                
                // Step 4: Validate restoration
                steps.add(performRestorationValidation())
                
                // Step 5: Sync state to server
                steps.add(performStateSynchronization())
                
                val allSucceeded = steps.all { it.success }
                val failedSteps = steps.filter { !it.success }
                
                RestorationResult(
                    success = allSucceeded,
                    steps = steps,
                    message = if (allSucceeded) {
                        "All restoration steps completed successfully"
                    } else {
                        "Failed steps: ${failedSteps.joinToString(", ") { it.name }}"
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Error in restoration flow")
                RestorationResult(
                    success = false,
                    steps = steps,
                    message = "Restoration crashed: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun performDeviceFingerprinting(): RestorationStep {
        return try {
            val deviceInfo = DeviceFingerprinter.getDeviceInfo(context)
            Timber.d("🔒 Device fingerprint: ${deviceInfo.fingerprint.take(8)}...")
            Timber.d("🔒 Device: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            
            RestorationStep(
                name = "Device Fingerprinting",
                success = true,
                message = "Device identified: ${deviceInfo.fingerprint.take(8)}...",
                data = mapOf(
                    "deviceId" to deviceInfo.fingerprint.take(16),
                    "manufacturer" to deviceInfo.manufacturer,
                    "model" to deviceInfo.model
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ Device fingerprinting failed")
            RestorationStep(
                name = "Device Fingerprinting",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun performSubscriptionRestoration(): RestorationStep {
        return try {
            // First check if user already has active subscription locally
            val wasAlreadySubscribed = PrefsManager.isSubscribed(context)
            
            // Restore from billing provider
            withContext(Dispatchers.Main) {
                billingManager.connectToPlayBilling { connected ->
                    if (connected) {
                        billingManager.restoreSubscriptions()
                        billingManager.validateSubscriptionStatus()
                    }
                }
            }
            
            // Wait a moment for billing restoration
            delay(2000)
            
            // Check if subscription was restored
            val isNowSubscribed = PrefsManager.isSubscribed(context)
            val subscriptionType = PrefsManager.getSubscriptionType(context)
            
            when {
                !wasAlreadySubscribed && isNowSubscribed -> {
                    Timber.d("✅ Subscription restored from billing provider")
                    RestorationStep(
                        name = "Subscription Restoration",
                        success = true,
                        message = "Subscription restored: $subscriptionType",
                        data = mapOf(
                            "wasSubscribed" to false,
                            "isSubscribed" to true,
                            "subscriptionType" to (subscriptionType ?: "unknown")
                        )
                    )
                }
                wasAlreadySubscribed && isNowSubscribed -> {
                    Timber.d("✅ Subscription was already active")
                    RestorationStep(
                        name = "Subscription Restoration",
                        success = true,
                        message = "Subscription already active: $subscriptionType"
                    )
                }
                else -> {
                    Timber.d("ℹ️ No active subscription found")
                    RestorationStep(
                        name = "Subscription Restoration",
                        success = true,
                        message = "No active subscription to restore"
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Subscription restoration failed")
            RestorationStep(
                name = "Subscription Restoration",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun performCreditStateRestoration(): RestorationStep {
        return try {
            val restoreResult = userStateManager.restoreUserState()
            
            if (restoreResult.success) {
                Timber.d("✅ Credit state restoration successful")
                RestorationStep(
                    name = "Credit State Restoration",
                    success = true,
                    message = restoreResult.message,
                    data = mapOf(
                        "creditsRestored" to restoreResult.creditsRestored,
                        "subscriptionRestored" to restoreResult.subscriptionRestored
                    )
                )
            } else {
                Timber.w("⚠️ Credit state restoration failed")
                RestorationStep(
                    name = "Credit State Restoration",
                    success = false,
                    message = restoreResult.message
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Credit state restoration crashed")
            RestorationStep(
                name = "Credit State Restoration",
                success = false,
                message = "Crashed: ${e.message}"
            )
        }
    }
    
    private suspend fun performRestorationValidation(): RestorationStep {
        return try {
            val chatCredits = creditManager.getChatCredits()
            val imageCredits = creditManager.getImageCredits()
            val isSubscribed = PrefsManager.isSubscribed(context)
            val deviceFingerprint = DeviceFingerprinter.getDeviceId(context)
            
            // Basic validation
            val isValid = chatCredits >= 0 && 
                         imageCredits >= 0 && 
                         deviceFingerprint.isNotEmpty()
            
            if (isValid) {
                Timber.d("✅ Restoration validation passed")
                RestorationStep(
                    name = "Restoration Validation",
                    success = true,
                    message = "State validation passed",
                    data = mapOf(
                        "chatCredits" to chatCredits,
                        "imageCredits" to imageCredits,
                        "isSubscribed" to isSubscribed,
                        "hasDeviceId" to deviceFingerprint.isNotEmpty()
                    )
                )
            } else {
                Timber.w("⚠️ Restoration validation failed")
                RestorationStep(
                    name = "Restoration Validation",
                    success = false,
                    message = "Invalid state detected"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Restoration validation crashed")
            RestorationStep(
                name = "Restoration Validation",
                success = false,
                message = "Validation crashed: ${e.message}"
            )
        }
    }
    
    private suspend fun performStateSynchronization(): RestorationStep {
        return try {
            // Sync current state to server
            userStateManager.forceSyncNow()
            
            // Wait for sync to complete
            delay(1000)
            
            Timber.d("✅ State synchronization completed")
            RestorationStep(
                name = "State Synchronization",
                success = true,
                message = "State synced to server"
            )
        } catch (e: Exception) {
            Timber.w("⚠️ State synchronization failed (non-critical): ${e.message}")
            // Non-critical failure - app can still function
            RestorationStep(
                name = "State Synchronization",
                success = true,
                message = "Sync failed but non-critical: ${e.message}"
            )
        }
    }
    
    private fun shouldPerformRestoration(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if restoration was already completed recently
        val lastRestoration = prefs.getLong(LAST_RESTORATION_TIME_KEY, 0L)
        val timeSinceLastRestoration = System.currentTimeMillis() - lastRestoration
        
        if (timeSinceLastRestoration < RESTORATION_COOLDOWN_MS) {
            return false
        }
        
        // Check if we've exceeded max attempts
        val attempts = prefs.getInt(RESTORATION_ATTEMPTS_KEY, 0)
        if (attempts >= MAX_RESTORATION_ATTEMPTS) {
            return false
        }
        
        // Always perform restoration on fresh installs or after updates
        val lastVersion = prefs.getString("last_app_version", "")
        if (lastVersion != BuildConfig.VERSION_NAME) {
            return true
        }
        
        // Check if restoration was completed for this version
        return !prefs.getBoolean(RESTORATION_COMPLETED_KEY, false)
    }
    
    private fun markRestorationCompleted() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(RESTORATION_COMPLETED_KEY, true)
            .putLong(LAST_RESTORATION_TIME_KEY, System.currentTimeMillis())
            .putInt(RESTORATION_ATTEMPTS_KEY, 0) // Reset attempts on success
            .putString("last_app_version", BuildConfig.VERSION_NAME)
            .apply()
    }
    
    private fun incrementRestorationAttempts() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(RESTORATION_ATTEMPTS_KEY, 0)
        prefs.edit()
            .putInt(RESTORATION_ATTEMPTS_KEY, attempts + 1)
            .putLong(LAST_RESTORATION_TIME_KEY, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Force restoration (for testing or manual recovery)
     */
    fun forceRestoration() {
        scope.launch {
            Timber.d("🔄 Force restoration requested")
            
            // Reset restoration state
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(RESTORATION_COMPLETED_KEY)
                .remove(RESTORATION_ATTEMPTS_KEY)
                .apply()
            
            triggerStartupRestoration()
        }
    }
    
    /**
     * Get restoration debug info
     */
    fun getDebugInfo(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceInfo = DeviceFingerprinter.getDeviceInfo(context)
        
        return """
            🚀 STARTUP RESTORATION DEBUG INFO
            
            Restoration Status:
            - Completed: ${prefs.getBoolean(RESTORATION_COMPLETED_KEY, false)}
            - Last Attempt: ${java.util.Date(prefs.getLong(LAST_RESTORATION_TIME_KEY, 0L))}
            - Attempts: ${prefs.getInt(RESTORATION_ATTEMPTS_KEY, 0)}/$MAX_RESTORATION_ATTEMPTS
            - In Progress: ${isRestorationInProgress.get()}
            
            Device Info:
            - Fingerprint: ${deviceInfo.fingerprint.take(12)}...
            - Device: ${deviceInfo.manufacturer} ${deviceInfo.model}
            - First Seen: ${java.util.Date(deviceInfo.firstSeen)}
            
            Current State:
            - Chat Credits: ${creditManager.getChatCredits()}
            - Image Credits: ${creditManager.getImageCredits()}
            - Subscribed: ${PrefsManager.isSubscribed(context)}
            - Subscription Type: ${PrefsManager.getSubscriptionType(context) ?: "none"}
            
            App Info:
            - Version: ${BuildConfig.VERSION_NAME}
            - Last Version: ${prefs.getString("last_app_version", "unknown")}
            - Build Type: ${BuildConfig.BUILD_TYPE}
        """.trimIndent()
    }
    
    fun cleanup() {
        scope.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        INSTANCE = null
    }
}

/**
 * Data classes for restoration results
 */
data class RestorationResult(
    val success: Boolean,
    val steps: List<RestorationStep>,
    val message: String
)

data class RestorationStep(
    val name: String,
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
)