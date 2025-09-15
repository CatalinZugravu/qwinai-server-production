package com.cyberflux.qwinai.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import com.cyberflux.qwinai.MyApp
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Enhanced PrefsManager with improved security and validation
 */
/**
 * Enhanced PrefsManager with improved security and validation
 */
object PrefsManager {
    private const val PREFS_NAME = "qwinai_prefs"
    private const val SETTINGS_PREFS = "settings"
    private const val SUBSCRIPTION_STATUS_KEY = "subscription_status"
    private const val DAYS_LEFT_KEY = "days_left"
    private const val FREE_CONVERSATIONS_KEY = "free_conversations"
    private const val LAST_FREE_RESET_KEY = "last_free_reset"
    private const val SUBSCRIPTION_END_TIME_KEY = "subscription_end_time"
    private const val SUBSCRIPTION_VALIDATION_HASH = "subscription_validation_hash"
    private const val SHOULD_SHOW_ADS_KEY = "should_show_ads"
    private const val FREE_CONVERSATIONS_RESET_DATE = "free_conversations_reset_date"
    private const val FREE_CONVERSATIONS_COUNT = "free_conversations_count"
    private const val USER_NAME_KEY = "user_name"
    private const val SUBSCRIPTION_TYPE_KEY = "subscription_type"

    // Settings keys
    private const val DEEP_SEARCH_ENABLED_KEY = "deep_search"
    private const val REASONING_ENABLED_KEY = "reasoning_enabled"
    private const val REASONING_LEVEL_KEY = "reasoning_level"

    private lateinit var sharedPrefs: SharedPreferences
    private var isInitialized = false
    private const val HAPTIC_FEEDBACK_KEY = "haptic_feedback_enabled"

    /**
     * Initialize PrefsManager
     */
    fun initialize(context: Context) {
        if (!isInitialized) {
            sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Initialize trial start date if not set
            if (!sharedPrefs.contains(LAST_FREE_RESET_KEY)) {
                sharedPrefs.edit { putLong(LAST_FREE_RESET_KEY, System.currentTimeMillis()) }
            }

            isInitialized = true
            Timber.d("PrefsManager initialized")
        }
    }

    // Free Conversations
    private var freeConversations: Int
        get() = sharedPrefs.getInt(FREE_CONVERSATIONS_KEY, 5)
        set(value) = sharedPrefs.edit { putInt(FREE_CONVERSATIONS_KEY, value) }

    // Last Free Reset
    private var lastFreeReset: Long
        get() = sharedPrefs.getLong(LAST_FREE_RESET_KEY, 0)
        set(value) = sharedPrefs.edit { putLong(LAST_FREE_RESET_KEY, value) }

    /**
     * Get all AI settings in one object
     */
    fun getAiSettings(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            isDeepSearchEnabled = prefs.getBoolean(DEEP_SEARCH_ENABLED_KEY, false),
            isReasoningEnabled = prefs.getBoolean(REASONING_ENABLED_KEY, false),
            reasoningLevel = prefs.getString(REASONING_LEVEL_KEY, "low") ?: "low"
        )
    }
    fun getDefaultModelId(context: Context): String {
        val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        return prefs.getString("default_model_id", ModelManager.DEFAULT_MODEL_ID)
            ?: ModelManager.DEFAULT_MODEL_ID
    }
    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(USER_NAME_KEY, null)
    }
    fun getAudioEnabled(context: Context, defaultValue: Boolean = false): Boolean {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("audio_enabled", defaultValue)
    }

    fun setAudioEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit {
                putBoolean("audio_enabled", enabled)
            }
    }

    fun getAudioFormat(context: Context, defaultValue: String = "mp3"): String {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("audio_format", defaultValue) ?: defaultValue
    }

    fun setAudioFormat(context: Context, format: String) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit {
                putString("audio_format", format)
            }
    }

    fun getAudioVoice(context: Context, defaultValue: String = "alloy"): String {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("audio_voice", defaultValue) ?: defaultValue
    }

    fun setAudioVoice(context: Context, voice: String) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit {
                putString("audio_voice", voice)
            }
    }
    // Set user name
    fun setUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(USER_NAME_KEY, name)
        }
        Timber.d("User name set: $name")
    }
    fun isHapticFeedbackEnabled(context: Context): Boolean {
        // Use the same preference store as SettingsActivity for consistency
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("haptic_feedback_enabled", true) // Default to true
    }

    /**
     * Check if accent color should be applied to status bar
     */
    fun isAccentStatusBarEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("accent_status_bar_enabled", true) // Default to enabled
    }

    /**
     * Set whether accent color should be applied to status bar
     */
    fun setAccentStatusBarEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit {
                putBoolean("accent_status_bar_enabled", enabled)
            }
        Timber.d("Accent status bar setting updated: $enabled")
    }

    /**
     * Save all AI settings from one object
     */
    fun saveAiSettings(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(DEEP_SEARCH_ENABLED_KEY, settings.isDeepSearchEnabled)
            putBoolean(REASONING_ENABLED_KEY, settings.isReasoningEnabled)
            putString(REASONING_LEVEL_KEY, settings.reasoningLevel)
            apply()
        }

        Timber.d("Saved AI settings: $settings")
    }


    /**
     * Set subscription status with enhanced security
     */
    @SuppressLint("HardwareIds")
    fun setSubscribed(context: Context, isSubscribed: Boolean, daysLeft: Int = 0) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Calculate end time
        val endTime = if (isSubscribed && daysLeft > 0) {
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysLeft.toLong())
        } else 0L

        // Create a validation hash using device ID, status, and end time
        // This simple hash adds basic tamper detection
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val validationData = "$deviceId:$isSubscribed:$endTime"
        val validationHash = validationData.hashCode().toString()

        prefs.edit {
            putBoolean(SUBSCRIPTION_STATUS_KEY, isSubscribed)
            putInt(DAYS_LEFT_KEY, daysLeft)
            putLong(SUBSCRIPTION_END_TIME_KEY, endTime)
            putString(SUBSCRIPTION_VALIDATION_HASH, validationHash)

            // Auto-disable ads for subscribers
            if (isSubscribed) {
                putBoolean(SHOULD_SHOW_ADS_KEY, false)
            } else {
                putBoolean(SHOULD_SHOW_ADS_KEY, true)
            }
        }

        Timber.d("Subscription status set: subscribed=$isSubscribed, days=$daysLeft, expiry=${Date(endTime)}")
    }

    /**
     * Set subscription status with end time directly (for mock billing)
     */
    @SuppressLint("HardwareIds")
    fun setSubscribed(context: Context, isSubscribed: Boolean, endTime: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Calculate days left from end time
        val daysLeft = if (isSubscribed && endTime > System.currentTimeMillis()) {
            ((endTime - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1)).toInt()
        } else 0

        // Create a validation hash using device ID, status, and end time
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val validationData = "$deviceId:$isSubscribed:$endTime"
        val validationHash = validationData.hashCode().toString()

        prefs.edit {
            putBoolean(SUBSCRIPTION_STATUS_KEY, isSubscribed)
            putInt(DAYS_LEFT_KEY, daysLeft)
            putLong(SUBSCRIPTION_END_TIME_KEY, endTime)
            putString(SUBSCRIPTION_VALIDATION_HASH, validationHash)

            // Auto-disable ads for subscribers
            if (isSubscribed) {
                putBoolean(SHOULD_SHOW_ADS_KEY, false)
            } else {
                putBoolean(SHOULD_SHOW_ADS_KEY, true)
            }
        }

        Timber.d("Subscription status set: subscribed=$isSubscribed, endTime=${Date(endTime)}, days=$daysLeft")
    }

    /**
     * Set subscription type (weekly, monthly, etc.)
     */
    fun setSubscriptionType(context: Context, subscriptionType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(SUBSCRIPTION_TYPE_KEY, subscriptionType)
        }
        Timber.d("Subscription type set: $subscriptionType")
    }

    /**
     * Get subscription type
     */
    fun getSubscriptionType(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SUBSCRIPTION_TYPE_KEY, null)
    }

    /**
     * Check if user is subscribed with validation
     */
    @SuppressLint("HardwareIds")
    fun isSubscribed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean(SUBSCRIPTION_STATUS_KEY, false)

        // Quick return if not subscribed
        if (!isSubscribed) return false

        val endTime = prefs.getLong(SUBSCRIPTION_END_TIME_KEY, 0L)
        val storedHash = prefs.getString(SUBSCRIPTION_VALIDATION_HASH, "") ?: ""

        // Check if subscription has expired
        if (endTime > 0 && endTime < System.currentTimeMillis()) {
            // Expired subscription, reset status
            setSubscribed(context, false, 0)
            return false
        }

        // Verify hash for tamper detection
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val validationData = "$deviceId:$isSubscribed:$endTime"
        val expectedHash = validationData.hashCode().toString()

        // If hash doesn't match, possible tampering attempt
        if (storedHash != expectedHash) {
            Timber.w("Subscription validation failed: hash mismatch")
            setSubscribed(context, false, 0)
            return false
        }

        return true
    }

    /**
     * Get subscription end time (for checking expiration)
     */
    fun getSubscriptionEndTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(SUBSCRIPTION_END_TIME_KEY, 0L)
    }

    /**
     * Set whether ads should be shown
     */
    fun setShouldShowAds(context: Context, shouldShow: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(SHOULD_SHOW_ADS_KEY, shouldShow)
        }
    }

    /**
     * Check if ads should be shown
     */
    fun shouldShowAds(context: Context): Boolean {
        // Always check subscription status first
        if (isSubscribed(context)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(SHOULD_SHOW_ADS_KEY, true)
    }

    /**
     * Reset free conversations if needed
     */
    fun resetFreeConversationsIfNeeded(context: Context = MyApp.instance) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResetDate = prefs.getLong(FREE_CONVERSATIONS_RESET_DATE, 0)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Also support legacy method
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = TimeUnit.DAYS.toMillis(1)
        if (currentTime - lastFreeReset >= oneDayInMillis || lastResetDate < today) {
            freeConversations = 5
            lastFreeReset = currentTime

            // Update new format too
            prefs.edit {
                putLong(FREE_CONVERSATIONS_RESET_DATE, today)
                putInt(FREE_CONVERSATIONS_COUNT, 0)
            }
            Timber.d("Free conversations reset for new day")
        }
    }
}