package com.cyberflux.qwinai.security

import android.content.Context
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.billing.BillingProvider
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.utils.PrefsManager
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonClass
import retrofit2.http.*
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages user state persistence across app installations.
 * This prevents users from getting fresh credits when they uninstall/reinstall the app.
 * 
 * Features:
 * - Server-side credit consumption tracking
 * - Subscription status restoration  
 * - Device fingerprint-based user identification
 * - Automatic state synchronization on app startup
 */
class UserStateManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: UserStateManager? = null
        
        // Local cache keys
        private const val PREFS_NAME = "user_state_cache"
        private const val LAST_SYNC_KEY = "last_sync_timestamp"
        private const val CREDITS_CONSUMED_TODAY_CHAT_KEY = "credits_consumed_today_chat"
        private const val CREDITS_CONSUMED_TODAY_IMAGE_KEY = "credits_consumed_today_image"
        private const val SERVER_USER_ID_KEY = "server_user_id"
        private const val SYNC_FAILED_COUNT_KEY = "sync_failed_count"
        
        // Server configuration
        internal const val SERVER_BASE_URL = "https://qwinai-server-production.up.railway.app/api/" // Replace with your actual server URL
        private const val SYNC_INTERVAL_MS = 60 * 60 * 1000L // Sync every hour
        private const val MAX_RETRY_ATTEMPTS = 3
        
        fun getInstance(context: Context): UserStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserStateManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    private val deviceId: String by lazy {
        DeviceFingerprinter.getDeviceId(context)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val userStateApi: UserStateApi by lazy {
        createApiService()
    }
    
    init {
        Timber.d("🔄 UserStateManager initialized for device: ${deviceId.take(8)}...")
    }
    
    /**
     * Restore user state on app startup
     * This is the main function that prevents credit reset on reinstall
     */
    suspend fun restoreUserState(): RestoreResult {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("🔄 Starting user state restoration for device: ${deviceId.take(8)}...")
                
                // First, restore subscription status
                val subscriptionRestored = restoreSubscriptionStatus()
                
                // Then, restore credit consumption data
                val creditsRestored = restoreCreditState()
                
                // Update local cache
                updateLastSyncTime()
                
                val result = RestoreResult(
                    success = true,
                    subscriptionRestored = subscriptionRestored,
                    creditsRestored = creditsRestored,
                    message = "User state restored successfully"
                )
                
                Timber.d("✅ User state restoration completed: $result")
                result
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to restore user state")
                RestoreResult(
                    success = false,
                    subscriptionRestored = false,
                    creditsRestored = false,
                    message = "Restoration failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Sync current user state to server (called when credits are consumed)
     */
    fun syncUserState(immediate: Boolean = false) {
        scope.launch {
            try {
                if (!shouldSync() && !immediate) {
                    return@launch
                }
                
                Timber.d("🔄 Syncing user state to server...")
                
                val userState = getCurrentUserState()
                val response = userStateApi.updateUserState(deviceId, userState).execute()
                
                if (response.isSuccessful) {
                    Timber.d("✅ User state synced successfully")
                    updateLastSyncTime()
                    resetFailedSyncCount()
                } else {
                    Timber.w("⚠️ Server sync failed: ${response.code()} ${response.message()}")
                    incrementFailedSyncCount()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Error syncing user state")
                incrementFailedSyncCount()
            }
        }
    }
    
    /**
     * Record credit consumption (called from CreditManager)
     */
    fun recordCreditConsumption(type: CreditManager.CreditType, amount: Int) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentDate = getCurrentDateString()
                val key = when (type) {
                    CreditManager.CreditType.CHAT -> CREDITS_CONSUMED_TODAY_CHAT_KEY
                    CreditManager.CreditType.IMAGE_GENERATION -> CREDITS_CONSUMED_TODAY_IMAGE_KEY
                }
                
                // Get current consumption for today
                val currentConsumption = prefs.getInt("${key}_$currentDate", 0)
                val newConsumption = currentConsumption + amount
                
                // Store locally
                prefs.edit()
                    .putInt("${key}_$currentDate", newConsumption)
                    .apply()
                
                Timber.d("📊 Recorded credit consumption: $type = $newConsumption (added $amount)")
                
                // Sync to server (with rate limiting)
                syncUserState(immediate = false)
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to record credit consumption")
            }
        }
    }
    
    /**
     * Check if user has exceeded daily limits (based on server data)
     */
    suspend fun checkDailyLimits(type: CreditManager.CreditType): DailyLimitResult {
        return withContext(Dispatchers.IO) {
            try {
                // Get server data
                val fingerprint = DeviceFingerprinter.getDeviceFingerprint(context)
                val response = userStateApi.getUserState(deviceId, fingerprint).execute()
                
                if (response.isSuccessful) {
                    val serverState = response.body()
                    if (serverState != null) {
                        val consumedToday = when (type) {
                            CreditManager.CreditType.CHAT -> serverState.creditsConsumedTodayChat
                            CreditManager.CreditType.IMAGE_GENERATION -> serverState.creditsConsumedTodayImage
                        }
                        
                        val dailyLimit = when (type) {
                            CreditManager.CreditType.CHAT -> 25 // 15 base + 10 from ads
                            CreditManager.CreditType.IMAGE_GENERATION -> 30 // 20 base + 10 from ads
                        }
                        
                        return@withContext DailyLimitResult(
                            withinLimits = consumedToday < dailyLimit,
                            consumed = consumedToday,
                            limit = dailyLimit,
                            remaining = maxOf(0, dailyLimit - consumedToday)
                        )
                    }
                }
                
                // Fallback to local data if server unavailable
                getLocalDailyLimits(type)
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to check daily limits from server")
                getLocalDailyLimits(type)
            }
        }
    }
    
    private suspend fun restoreSubscriptionStatus(): Boolean {
        return try {
            // First check server for subscription data
            val response = userStateApi.getUserState(deviceId).execute()
            
            if (response.isSuccessful) {
                val serverState = response.body()
                if (serverState != null && serverState.hasActiveSubscription) {
                    Timber.d("🔄 Found active subscription on server, restoring...")
                    
                    // Restore subscription locally
                    PrefsManager.setSubscribed(context, true, serverState.subscriptionEndTime)
                    if (!serverState.subscriptionType.isNullOrEmpty()) {
                        PrefsManager.setSubscriptionType(context, serverState.subscriptionType)
                    }
                    
                    return true
                }
            }
            
            // Fallback: Check with billing provider for active subscriptions
            // This will work even if our server is down
            restoreSubscriptionFromBilling()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error restoring subscription status")
            false
        }
    }
    
    private suspend fun restoreSubscriptionFromBilling(): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                // This should be called from the billing manager
                // For now, we'll just check if the user has any active subscriptions
                val billingManager = com.cyberflux.qwinai.billing.BillingManager.getInstance(context)
                
                // Restore subscriptions through billing manager
                billingManager.restoreSubscriptions()
                billingManager.validateSubscriptionStatus()
                
                // Check if restoration was successful
                val isSubscribed = PrefsManager.isSubscribed(context)
                if (isSubscribed) {
                    Timber.d("✅ Subscription restored from billing provider")
                    
                    // Sync this back to our server
                    syncSubscriptionToServer()
                }
                
                isSubscribed
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Error restoring subscription from billing")
                false
            }
        }
    }
    
    private suspend fun restoreCreditState(): Boolean {
        return try {
            val response = userStateApi.getUserState(deviceId).execute()
            
            if (response.isSuccessful) {
                val serverState = response.body()
                if (serverState != null) {
                    Timber.d("🔄 Restoring credit state from server...")
                    
                    // Store server data locally for offline access
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val currentDate = getCurrentDateString()
                    
                    prefs.edit()
                        .putInt("${CREDITS_CONSUMED_TODAY_CHAT_KEY}_$currentDate", 
                               serverState.creditsConsumedTodayChat)
                        .putInt("${CREDITS_CONSUMED_TODAY_IMAGE_KEY}_$currentDate", 
                               serverState.creditsConsumedTodayImage)
                        .putString(SERVER_USER_ID_KEY, serverState.userId)
                        .apply()
                    
                    Timber.d("✅ Credit state restored - Chat: ${serverState.creditsConsumedTodayChat}, Image: ${serverState.creditsConsumedTodayImage}")
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            Timber.e(e, "❌ Error restoring credit state")
            false
        }
    }
    
    private fun getCurrentUserState(): UserState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDate = getCurrentDateString()
        
        return UserState(
            deviceId = deviceId,
            deviceFingerprint = DeviceFingerprinter.getDeviceFingerprint(context),
            creditsConsumedTodayChat = prefs.getInt("${CREDITS_CONSUMED_TODAY_CHAT_KEY}_$currentDate", 0),
            creditsConsumedTodayImage = prefs.getInt("${CREDITS_CONSUMED_TODAY_IMAGE_KEY}_$currentDate", 0),
            hasActiveSubscription = PrefsManager.isSubscribed(context),
            subscriptionType = PrefsManager.getSubscriptionType(context),
            subscriptionEndTime = PrefsManager.getSubscriptionEndTime(context),
            lastActive = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            date = currentDate
        )
    }
    
    private fun syncSubscriptionToServer() {
        scope.launch {
            try {
                val userState = getCurrentUserState()
                userStateApi.updateUserState(deviceId, userState).execute()
                Timber.d("✅ Subscription synced to server")
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to sync subscription to server")
            }
        }
    }
    
    private fun getLocalDailyLimits(type: CreditManager.CreditType): DailyLimitResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDate = getCurrentDateString()
        val key = when (type) {
            CreditManager.CreditType.CHAT -> CREDITS_CONSUMED_TODAY_CHAT_KEY
            CreditManager.CreditType.IMAGE_GENERATION -> CREDITS_CONSUMED_TODAY_IMAGE_KEY
        }
        
        val consumed = prefs.getInt("${key}_$currentDate", 0)
        val limit = when (type) {
            CreditManager.CreditType.CHAT -> 25
            CreditManager.CreditType.IMAGE_GENERATION -> 30
        }
        
        return DailyLimitResult(
            withinLimits = consumed < limit,
            consumed = consumed,
            limit = limit,
            remaining = maxOf(0, limit - consumed)
        )
    }
    
    private fun shouldSync(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getLong(LAST_SYNC_KEY, 0L)
        val timeDiff = System.currentTimeMillis() - lastSync
        val failedCount = prefs.getInt(SYNC_FAILED_COUNT_KEY, 0)
        
        // Don't sync too frequently if there are failures
        val minInterval = if (failedCount > 0) SYNC_INTERVAL_MS * (1 + failedCount) else SYNC_INTERVAL_MS
        
        return timeDiff >= minInterval
    }
    
    private fun updateLastSyncTime() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_SYNC_KEY, System.currentTimeMillis())
            .apply()
    }
    
    private fun incrementFailedSyncCount() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(SYNC_FAILED_COUNT_KEY, 0)
        prefs.edit().putInt(SYNC_FAILED_COUNT_KEY, count + 1).apply()
    }
    
    private fun resetFailedSyncCount() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(SYNC_FAILED_COUNT_KEY, 0)
            .apply()
    }
    
    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    
    private fun createApiService(): UserStateApi {
        // Use existing RetrofitInstance if available, otherwise create new one
        return try {
            val moshi = Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            Retrofit.Builder()
                .baseUrl(SERVER_BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(UserStateApi::class.java)
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create UserStateApi")
            throw e
        }
    }
    
    /**
     * Force sync for testing/debugging
     */
    fun forceSyncNow() {
        syncUserState(immediate = true)
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDate = getCurrentDateString()
        
        return """
            🔒 USER STATE DEBUG INFO
            Device ID: ${deviceId.take(8)}...
            Last Sync: ${Date(prefs.getLong(LAST_SYNC_KEY, 0L))}
            Credits Consumed Today:
              Chat: ${prefs.getInt("${CREDITS_CONSUMED_TODAY_CHAT_KEY}_$currentDate", 0)}
              Image: ${prefs.getInt("${CREDITS_CONSUMED_TODAY_IMAGE_KEY}_$currentDate", 0)}
            Server User ID: ${prefs.getString(SERVER_USER_ID_KEY, "none")}
            Failed Sync Count: ${prefs.getInt(SYNC_FAILED_COUNT_KEY, 0)}
            Subscription Status: ${PrefsManager.isSubscribed(context)}
        """.trimIndent()
    }
    
    fun cleanup() {
        scope.cancel()
        INSTANCE = null
    }
}

/**
 * API interface for server communication
 */
interface UserStateApi {
    @GET("user-state/{deviceId}")
    fun getUserState(@Path("deviceId") deviceId: String, @Query("fingerprint") fingerprint: String? = null): Call<UserState>
    
    @PUT("user-state/{deviceId}")
    fun updateUserState(@Path("deviceId") deviceId: String, @Body userState: UserState): Call<ApiResponse>
    
    @POST("user-state/{deviceId}/reset")
    fun resetUserState(@Path("deviceId") deviceId: String): Call<ApiResponse>
}

/**
 * Data classes for API communication
 */
@JsonClass(generateAdapter = true)
data class UserState(
    val deviceId: String,
    val deviceFingerprint: String,
    val creditsConsumedTodayChat: Int,
    val creditsConsumedTodayImage: Int,
    val hasActiveSubscription: Boolean,
    val subscriptionType: String? = null,
    val subscriptionEndTime: Long = 0L,
    val lastActive: Long,
    val appVersion: String,
    val date: String,
    val userId: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

data class RestoreResult(
    val success: Boolean,
    val subscriptionRestored: Boolean,
    val creditsRestored: Boolean,
    val message: String
)

data class DailyLimitResult(
    val withinLimits: Boolean,
    val consumed: Int,
    val limit: Int,
    val remaining: Int
)