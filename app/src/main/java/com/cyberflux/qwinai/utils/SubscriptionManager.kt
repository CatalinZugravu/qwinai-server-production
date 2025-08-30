package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.SubscriptionActivity
import com.cyberflux.qwinai.ads.AdManager
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.workers.CheckSubscriptionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Comprehensive subscription management system that handles:
 * - Feature unlocking/locking based on subscription status
 * - Automatic subscription monitoring and renewal detection
 * - Credit system management for free users
 * - Ad visibility control
 * - Premium feature access control
 */
object SubscriptionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Subscription status flow
    private val _subscriptionStatus = MutableStateFlow(false)
    val subscriptionStatus: StateFlow<Boolean> = _subscriptionStatus.asStateFlow()
    
    // Premium features availability
    private val _premiumFeaturesEnabled = MutableStateFlow(false)
    val premiumFeaturesEnabled: StateFlow<Boolean> = _premiumFeaturesEnabled.asStateFlow()
    
    // Subscription expiry information
    private val _subscriptionInfo = MutableStateFlow<SubscriptionInfo?>(null)
    val subscriptionInfo: StateFlow<SubscriptionInfo?> = _subscriptionInfo.asStateFlow()

    private var isInitialized = false
    private var context: Context? = null

    /**
     * Initialize the subscription manager
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        this.context = context.applicationContext
        isInitialized = true
        
        Timber.d("Initializing SubscriptionManager")
        
        // Load initial subscription status
        updateSubscriptionStatus(context)
        
        // Setup periodic subscription checking
        setupPeriodicSubscriptionCheck(context)
        
        // Start monitoring billing manager updates
        startBillingMonitoring(context)
        
        Timber.d("SubscriptionManager initialized")
    }

    /**
     * Setup periodic subscription status checking
     */
    private fun setupPeriodicSubscriptionCheck(context: Context) {
        val subscriptionCheckWork = PeriodicWorkRequestBuilder<CheckSubscriptionWorker>(
            6, TimeUnit.HOURS // Check every 6 hours
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "subscription_check",
            ExistingPeriodicWorkPolicy.KEEP,
            subscriptionCheckWork
        )
        
        Timber.d("Periodic subscription check scheduled")
    }

    /**
     * Start monitoring billing manager for status changes
     */
    private fun startBillingMonitoring(context: Context) {
        scope.launch {
            try {
                val billingManager = BillingManager.getInstance(context)
                
                billingManager.subscriptionStatus.collect { isSubscribed ->
                    handleSubscriptionStatusChange(context, isSubscribed)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error monitoring billing manager")
            }
        }
    }

    /**
     * Handle subscription status changes
     */
    private fun handleSubscriptionStatusChange(context: Context, isSubscribed: Boolean) {
        val previousStatus = _subscriptionStatus.value
        
        if (previousStatus != isSubscribed) {
            Timber.d("Subscription status changed: $previousStatus -> $isSubscribed")
            
            updateSubscriptionStatus(context)
            
            if (isSubscribed) {
                onSubscriptionActivated(context)
            } else {
                onSubscriptionDeactivated(context)
            }
        }
    }

    /**
     * Update subscription status and related features
     */
    fun updateSubscriptionStatus(context: Context) {
        val isSubscribed = PrefsManager.isSubscribed(context)
        val endTime = PrefsManager.getSubscriptionEndTime(context)
        
        _subscriptionStatus.value = isSubscribed
        _premiumFeaturesEnabled.value = isSubscribed
        
        // Update subscription info
        if (isSubscribed && endTime > 0) {
            val remainingTime = endTime - System.currentTimeMillis()
            val daysRemaining = (remainingTime / (24 * 60 * 60 * 1000)).toInt()
            
            _subscriptionInfo.value = SubscriptionInfo(
                isActive = true,
                expiryTime = endTime,
                daysRemaining = maxOf(0, daysRemaining),
                isExpiringSoon = daysRemaining <= 3
            )
        } else {
            _subscriptionInfo.value = SubscriptionInfo(
                isActive = false,
                expiryTime = 0,
                daysRemaining = 0,
                isExpiringSoon = false
            )
        }
        
        // Update feature access
        updateFeatureAccess(context, isSubscribed)
        
        Timber.d("Subscription status updated: $isSubscribed")
    }

    /**
     * Called when subscription is activated
     */
    private fun onSubscriptionActivated(context: Context) {
        Timber.d("Subscription activated - unlocking premium features")
        
        // Disable ads
        PrefsManager.setShouldShowAds(context, false)
        
        // Credit system handles premium mode automatically based on subscription status
        
        // Notify UI components
        notifySubscriptionActivated(context)
    }

    /**
     * Called when subscription is deactivated/expired
     */
    private fun onSubscriptionDeactivated(context: Context) {
        Timber.d("Subscription deactivated - reverting to free mode")
        
        // Enable ads for free users
        PrefsManager.setShouldShowAds(context, true)
        
        // Credit system handles free mode automatically based on subscription status
        
        // Notify UI components
        notifySubscriptionDeactivated(context)
    }

    /**
     * Update feature access based on subscription status
     */
    private fun updateFeatureAccess(context: Context, isSubscribed: Boolean) {
        if (isSubscribed) {
            // Unlock all premium features
            unlockPremiumFeatures(context)
        } else {
            // Lock premium features and enable free limitations
            lockPremiumFeatures(context)
        }
    }

    /**
     * Unlock premium features
     */
    private fun unlockPremiumFeatures(context: Context) {
        Timber.d("Unlocking premium features")
        
        // Store premium status in preferences for quick access
        val prefs = context.getSharedPreferences("premium_features", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("unlimited_conversations", true)
            putBoolean("advanced_models", true)
            putBoolean("no_ads", true)
            putBoolean("priority_support", true)
            putBoolean("file_processing", true)
            putBoolean("voice_chat", true)
            putBoolean("image_generation", true)
            putBoolean("early_access", true)
            apply()
        }
    }

    /**
     * Lock premium features
     */
    private fun lockPremiumFeatures(context: Context) {
        Timber.d("Locking premium features - reverting to free mode")
        
        // Remove premium features
        val prefs = context.getSharedPreferences("premium_features", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Reset any premium-only settings to defaults
        resetToFreeDefaults(context)
    }

    /**
     * Reset app settings to free user defaults
     */
    private fun resetToFreeDefaults(context: Context) {
        val settingsPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        settingsPrefs.edit().apply {
            // Disable premium-only features
            putBoolean("advanced_ai_models", false)
            putBoolean("unlimited_file_uploads", false)
            putBoolean("priority_processing", false)
            
            // Reset to free limits
            putInt("daily_conversation_limit", 5)
            putInt("file_upload_size_limit", 5) // 5MB for free users
            
            apply()
        }
        
        Timber.d("Reset to free user defaults")
    }

    /**
     * Check if a specific premium feature is available
     */
    fun isPremiumFeatureAvailable(context: Context, featureName: String): Boolean {
        if (!isInitialized) {
            initialize(context)
        }
        
        // Quick check - if subscribed, all features are available
        if (_subscriptionStatus.value) {
            return true
        }
        
        // For free users, check specific feature availability
        val prefs = context.getSharedPreferences("premium_features", Context.MODE_PRIVATE)
        return prefs.getBoolean(featureName, false)
    }

    /**
     * Check if user can access a premium feature, show upgrade dialog if not
     */
    fun checkPremiumAccess(
        activity: Activity,
        featureName: String,
        onAccessGranted: () -> Unit
    ) {
        if (isPremiumFeatureAvailable(activity, featureName)) {
            onAccessGranted()
        } else {
            showUpgradeDialog(activity, getFeatureDescription(featureName))
        }
    }

    /**
     * Show upgrade dialog for premium features
     */
    private fun showUpgradeDialog(activity: Activity, featureDescription: String) {
        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle("Premium Feature")
            .setMessage("$featureDescription requires a premium subscription. Upgrade now to unlock this and all other premium features.")
            .setPositiveButton("Upgrade Now") { _, _ ->
                SubscriptionActivity.start(activity)
            }
            .setNegativeButton("Maybe Later", null)
            .create()
            
        dialog.show()
        
        // Apply haptic feedback
        HapticManager.mediumVibration(activity)
    }

    /**
     * Get feature description for upgrade dialog
     */
    private fun getFeatureDescription(featureName: String): String {
        return when (featureName) {
            "unlimited_conversations" -> "Unlimited AI conversations"
            "advanced_models" -> "Advanced AI models"
            "no_ads" -> "Ad-free experience"
            "file_processing" -> "File upload and processing"
            "voice_chat" -> "Voice chat with AI"
            "image_generation" -> "AI image generation"
            "priority_support" -> "Priority customer support"
            "early_access" -> "Early access to new features"
            else -> "This premium feature"
        }
    }

    /**
     * Force refresh subscription status from billing service
     */
    fun forceRefreshSubscriptionStatus(context: Context) {
        scope.launch {
            try {
                val billingManager = BillingManager.getInstance(context)
                billingManager.validateSubscriptionStatus()
                billingManager.refreshSubscriptionStatus()
                
                // Wait for update and refresh local status
                kotlinx.coroutines.delay(2000)
                updateSubscriptionStatus(context)
                
            } catch (e: Exception) {
                Timber.e(e, "Error force refreshing subscription status")
            }
        }
    }

    /**
     * Check if subscription is expiring soon (within 3 days)
     */
    fun isSubscriptionExpiringSoon(context: Context): Boolean {
        val info = _subscriptionInfo.value
        return info?.isExpiringSoon ?: false
    }

    /**
     * Get days remaining in subscription
     */
    fun getDaysRemaining(context: Context): Int {
        val info = _subscriptionInfo.value
        return info?.daysRemaining ?: 0
    }

    /**
     * Notify activities about subscription activation
     */
    private fun notifySubscriptionActivated(context: Context) {
        val intent = Intent("com.cyberflux.qwinai.SUBSCRIPTION_ACTIVATED")
        context.sendBroadcast(intent)
    }

    /**
     * Notify activities about subscription deactivation
     */
    private fun notifySubscriptionDeactivated(context: Context) {
        val intent = Intent("com.cyberflux.qwinai.SUBSCRIPTION_DEACTIVATED")
        context.sendBroadcast(intent)
    }

    /**
     * Show subscription expiry warning
     */
    fun showExpiryWarningIfNeeded(activity: Activity) {
        if (isSubscriptionExpiringSoon(activity)) {
            val daysRemaining = getDaysRemaining(activity)
            
            val message = if (daysRemaining == 0) {
                "Your premium subscription expires today. Renew now to continue enjoying all premium features."
            } else {
                "Your premium subscription expires in $daysRemaining day${if (daysRemaining != 1) "s" else ""}. Renew now to avoid interruption."
            }
            
            val dialog = android.app.AlertDialog.Builder(activity)
                .setTitle("Subscription Expiring")
                .setMessage(message)
                .setPositiveButton("Renew Now") { _, _ ->
                    SubscriptionActivity.start(activity)
                }
                .setNegativeButton("Remind Later", null)
                .create()
                
            dialog.show()
        }
    }

    /**
     * Data class for subscription information
     */
    data class SubscriptionInfo(
        val isActive: Boolean,
        val expiryTime: Long,
        val daysRemaining: Int,
        val isExpiringSoon: Boolean
    )
}