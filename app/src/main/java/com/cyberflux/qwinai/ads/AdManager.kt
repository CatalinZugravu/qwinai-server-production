package com.cyberflux.qwinai.ads

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.Toast
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.MyApp
import com.cyberflux.qwinai.ads.mediation.MediationManagerFactory
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.utils.PrefsManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * UNIFIED AD MANAGER - Consolidates all ad management functionality
 * Features:
 * - Frequency control and rate limiting
 * - Integration with credit system
 * - Automatic ad loading with retry logic
 * - Loading dialogs and UI feedback
 * - Activity lifecycle management
 * - Smart ad triggers based on user behavior
 * - Context-aware ad display
 */

@Suppress("DEPRECATION")
class AdManager private constructor(private val context: Context) {

    companion object {
        private const val MIN_INTERSTITIAL_INTERVAL_MS = 60000L // 1 minute
        private const val MIN_REWARDED_INTERVAL_MS = 30000L // 30 seconds
        private const val MAX_INTERSTITIAL_PER_HOUR = 6
        private const val MAX_REWARDED_PER_HOUR = 10
        
        // UI constants from mediation AdManager
        private const val MAX_RETRY_COUNT = 3
        private const val AD_LOAD_TIMEOUT = 20000L // 20 seconds
        
        @Volatile
        private var INSTANCE: AdManager? = null
        
        fun getInstance(context: Context): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // CRITICAL FIX: Make mediation manager lazy to prevent startup deadlocks
    private val mediationManager by lazy { MediationManagerFactory.getMediationManager() }
    private val creditManager = CreditManager.getInstance(context)
    
    // Store current activity for ad loading (use WeakReference to prevent memory leaks)
    @Volatile
    private var currentActivity: java.lang.ref.WeakReference<Activity>? = null
    
    // Frequency control
    private var lastInterstitialTime = AtomicLong(0)
    private var lastRewardedTime = AtomicLong(0)
    private var interstitialCountThisHour = AtomicBoolean(false)
    private var rewardedCountThisHour = AtomicBoolean(false)
    private var lastHourReset = AtomicLong(System.currentTimeMillis())
    
    private val interstitialCooldowns = mutableMapOf<String, Long>()
    private val rewardedCooldowns = mutableMapOf<String, Long>()
    
    // Loading state management (from mediation AdManager)
    private val isLoadingRewardedAd = AtomicBoolean(false)
    private val isLoadingInterstitialAd = AtomicBoolean(false)
    private var adLoadRetryCount = 0
    private var pendingRewardCredits = 0
    private var loadingDialog: AlertDialog? = null
    
    // UI handlers
    private val mainHandler = Handler(Looper.getMainLooper())
    private var adLoadTimeoutRunnable: Runnable? = null
    private var adLoadCheckRunnable: Runnable? = null

    enum class AdTrigger {
        CHAT_RESPONSE_COMPLETE,
        IMAGE_GENERATION_COMPLETE,
        IMAGE_DOWNLOAD,
        APP_RESUME,
        FEATURE_ACCESS,
        MANUAL_REWARD,
        INSUFFICIENT_CREDITS
    }

    interface AdCallback {
        fun onAdShown(trigger: AdTrigger)
        fun onAdFailed(trigger: AdTrigger, error: String)
        fun onAdClosed(trigger: AdTrigger)
        fun onRewardEarned(trigger: AdTrigger, amount: Int)
    }

    private val adCallbacks = mutableListOf<AdCallback>()

    init {
        mediationManager.initialize(context)
        preloadAds()
    }
    
    /**
     * Initialize ads for MainActivity (replaces the mediation AdManager)
     */
    fun initializeForActivity(activity: Activity) {
        try {
            // Store the activity reference for ad loading
            currentActivity = java.lang.ref.WeakReference(activity)
            
            if (!PrefsManager.isSubscribed(activity)) {
                mainHandler.postDelayed({
                    loadInterstitialAd()
                    loadRewardedAd()
                    Timber.d("Initial ad loading started for activity")
                }, 2000)
            }
            Timber.d("Ad system initialized for activity using ${if (MyApp.isHuaweiDeviceNonBlocking()) "Huawei" else "Google"} mediation")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing ad system for activity")
        }
    }

    fun addAdCallback(callback: AdCallback) {
        adCallbacks.add(callback)
    }

    fun removeAdCallback(callback: AdCallback) {
        adCallbacks.remove(callback)
    }

    private fun preloadAds() {
        // Pre-load interstitial ads - requires activity context, will be loaded on-demand
    }
    
    /**
     * Load interstitial ad with retry logic (from mediation AdManager)
     */
    fun loadInterstitialAd() {
        if (!isLoadingInterstitialAd.compareAndSet(false, true)) {
            Timber.d("Interstitial ad load already in progress")
            return
        }
        
        // Get the current activity from weak reference
        val activity = currentActivity?.get()
        if (activity == null) {
            Timber.w("⚠️ No activity available for ad loading, skipping")
            isLoadingInterstitialAd.set(false)
            return
        }
        
        adLoadRetryCount = 0
        adLoadTimeoutRunnable = Runnable {
            if (isLoadingInterstitialAd.get()) {
                isLoadingInterstitialAd.set(false)
                Timber.d("Interstitial ad load timed out")
            }
        }
        mainHandler.postDelayed(adLoadTimeoutRunnable!!, AD_LOAD_TIMEOUT)
        
        // Use the stored activity reference for loading
        mediationManager.loadInterstitialAd(
            activity,
            object : com.cyberflux.qwinai.ads.mediation.InterstitialAdListener {
                override fun onAdLoaded(networkName: String) {
                    isLoadingInterstitialAd.set(false)
                    mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                    adLoadRetryCount = 0
                    Timber.d("Interstitial ad loaded successfully from $networkName")
                }
                
                override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                    isLoadingInterstitialAd.set(false)
                    mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                    Timber.d("Failed to load interstitial ad (code: $errorCode): $errorMessage")
                    
                    if (adLoadRetryCount < MAX_RETRY_COUNT) {
                        adLoadRetryCount++
                        Timber.d("Retrying interstitial ad load (attempt $adLoadRetryCount)")
                        mainHandler.postDelayed({ loadInterstitialAd() }, 3000)
                    }
                }
                
                override fun onAdClosed() {
                    Timber.d("Interstitial ad closed, reloading")
                    mainHandler.postDelayed({ loadInterstitialAd() }, 1000)
                }
            }
        )
    }
    
    /**
     * Load rewarded ad with retry logic (from mediation AdManager)
     */
    fun loadRewardedAd() {
        if (!isLoadingRewardedAd.compareAndSet(false, true)) {
            Timber.d("Rewarded ad load already in progress")
            return
        }
        
        // Get the current activity from weak reference
        val activity = currentActivity?.get()
        if (activity == null) {
            Timber.w("⚠️ No activity available for rewarded ad loading, skipping")
            isLoadingRewardedAd.set(false)
            return
        }
        
        adLoadRetryCount = 0
        adLoadTimeoutRunnable = Runnable {
            if (isLoadingRewardedAd.get()) {
                isLoadingRewardedAd.set(false)
                pendingRewardCredits = 0
                Timber.d("Rewarded ad load timed out")
                dismissLoadingDialog()
                if (pendingRewardCredits > 0) {
                    // Show toast if we have a context reference
                    Timber.w("Ad loading timed out with pending credits")
                }
            }
        }
        mainHandler.postDelayed(adLoadTimeoutRunnable!!, AD_LOAD_TIMEOUT)
        
        mediationManager.loadRewardedAd(
            activity,
            object : com.cyberflux.qwinai.ads.mediation.RewardedAdListener {
                override fun onAdLoaded(networkName: String) {
                    isLoadingRewardedAd.set(false)
                    mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                    adLoadRetryCount = 0
                    Timber.d("Rewarded ad loaded successfully from $networkName")
                    dismissLoadingDialog()
                    if (pendingRewardCredits > 0) {
                        // Show pending rewarded ad
                        pendingRewardCredits = 0
                    }
                }
                
                override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                    isLoadingRewardedAd.set(false)
                    mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                    Timber.d("Failed to load rewarded ad (code: $errorCode): $errorMessage")
                    
                    if (adLoadRetryCount < MAX_RETRY_COUNT) {
                        adLoadRetryCount++
                        Timber.d("Retrying rewarded ad load (attempt $adLoadRetryCount)")
                        mainHandler.postDelayed({ loadRewardedAd() }, 3000)
                    } else {
                        dismissLoadingDialog()
                        if (pendingRewardCredits > 0) {
                            Timber.w("Couldn't load ad after retries")
                            pendingRewardCredits = 0
                        }
                    }
                }
                
                override fun onAdClosed() {
                    Timber.d("Rewarded ad closed, reloading")
                    mainHandler.postDelayed({ loadRewardedAd() }, 1000)
                }
            }
        )
    }

    private fun resetHourlyCounters() {
        val now = System.currentTimeMillis()
        if (now - lastHourReset.get() >= 3600000) { // 1 hour
            interstitialCountThisHour.set(false)
            rewardedCountThisHour.set(false)
            lastHourReset.set(now)
        }
    }

    private fun canShowInterstitial(trigger: AdTrigger): Boolean {
        resetHourlyCounters()
        
        val now = System.currentTimeMillis()
        val timeSinceLastInterstitial = now - lastInterstitialTime.get()
        
        // Check global cooldown
        if (timeSinceLastInterstitial < MIN_INTERSTITIAL_INTERVAL_MS) {
            Timber.d("Interstitial blocked by global cooldown")
            return false
        }
        
        // Check trigger-specific cooldown
        val triggerKey = trigger.name
        val lastTriggerTime = interstitialCooldowns[triggerKey] ?: 0
        if (now - lastTriggerTime < getTriggerCooldown(trigger)) {
            Timber.d("Interstitial blocked by trigger cooldown for $trigger")
            return false
        }
        
        // Check hourly limits
        if (interstitialCountThisHour.get()) {
            Timber.d("Interstitial blocked by hourly limit")
            return false
        }
        
        return true
    }

    private fun getTriggerCooldown(trigger: AdTrigger): Long {
        return when (trigger) {
            AdTrigger.CHAT_RESPONSE_COMPLETE -> 180000L // 3 minutes
            AdTrigger.IMAGE_GENERATION_COMPLETE -> 180000L // 3 minutes
            AdTrigger.IMAGE_DOWNLOAD -> 300000L // 5 minutes
            AdTrigger.APP_RESUME -> 600000L // 10 minutes
            AdTrigger.FEATURE_ACCESS -> 240000L // 4 minutes
            AdTrigger.MANUAL_REWARD -> 120000L // 2 minutes
            AdTrigger.INSUFFICIENT_CREDITS -> 60000L // 1 minute
        }
    }

    /**
     * Show interstitial ad with frequency control
     */
    fun showInterstitialAd(activity: Activity, trigger: AdTrigger, callback: AdCallback? = null) {
        if (!canShowInterstitial(trigger)) {
            Timber.d("Interstitial ad blocked for trigger: $trigger")
            return
        }

        val now = System.currentTimeMillis()
        
        mediationManager.loadInterstitialAd(activity, object : com.cyberflux.qwinai.ads.mediation.InterstitialAdListener {
            override fun onAdLoaded(networkName: String) {
                mediationManager.showInterstitialAd(activity)
            }

            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                Timber.w("Interstitial ad failed to load: $errorMessage")
                callback?.onAdFailed(trigger, errorMessage)
                adCallbacks.forEach { it.onAdFailed(trigger, errorMessage) }
            }

            override fun onAdClosed() {
                lastInterstitialTime.set(now)
                interstitialCooldowns[trigger.name] = now
                interstitialCountThisHour.set(true)
                
                Timber.d("Interstitial ad closed for trigger: $trigger")
                callback?.onAdClosed(trigger)
                adCallbacks.forEach { it.onAdClosed(trigger) }
            }
        })
        
        callback?.onAdShown(trigger)
        adCallbacks.forEach { it.onAdShown(trigger) }
    }

    /**
     * Show rewarded ad for earning credits (enhanced version)
     */
    fun showRewardedAd(
        activity: Activity, 
        creditType: CreditManager.CreditType,
        trigger: AdTrigger = AdTrigger.MANUAL_REWARD,
        callback: AdCallback? = null
    ) {
        if (!creditManager.canEarnMoreCredits(creditType)) {
            Timber.d("User already at maximum credits for $creditType")
            callback?.onAdFailed(trigger, "Already at maximum credits")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastRewardedTime.get() < MIN_REWARDED_INTERVAL_MS) {
            Timber.d("Rewarded ad blocked by cooldown")
            callback?.onAdFailed(trigger, "Please wait before watching another ad")
            return
        }

        // Use the enhanced loading mechanism
        if (mediationManager.isRewardedAdLoaded()) {
            mediationManager.showRewardedAd(activity) { rewardAmount ->
                // Each ad gives exactly 1 credit regardless of type
                val creditsEarned = 1
                
                if (creditManager.addCreditsFromAd(creditType, creditsEarned)) {
                    lastRewardedTime.set(now)
                    rewardedCooldowns[trigger.name] = now
                    
                    Timber.d("User earned $creditsEarned credits from rewarded ad")
                    callback?.onRewardEarned(trigger, creditsEarned)
                    adCallbacks.forEach { it.onRewardEarned(trigger, creditsEarned) }
                } else {
                    Timber.w("Failed to add credits from rewarded ad")
                    callback?.onAdFailed(trigger, "Failed to add credits")
                }
                
                // Reload for next time
                loadRewardedAd()
            }
            
            callback?.onAdShown(trigger)
            adCallbacks.forEach { it.onAdShown(trigger) }
        } else {
            // Load ad first
            mediationManager.loadRewardedAd(activity, object : com.cyberflux.qwinai.ads.mediation.RewardedAdListener {
                override fun onAdLoaded(networkName: String) {
                    // Retry showing the ad
                    showRewardedAd(activity, creditType, trigger, callback)
                }

                override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                    Timber.w("Rewarded ad failed to load: $errorMessage")
                    callback?.onAdFailed(trigger, errorMessage)
                    adCallbacks.forEach { it.onAdFailed(trigger, errorMessage) }
                }

                override fun onAdClosed() {
                    callback?.onAdClosed(trigger)
                    adCallbacks.forEach { it.onAdClosed(trigger) }
                }
            })
        }
    }

    /**
     * Check if interstitial ad is ready
     */
    fun isInterstitialReady(): Boolean {
        return mediationManager.isInterstitialAdLoaded()
    }

    /**
     * Check if rewarded ad is ready
     */
    fun isRewardedReady(): Boolean {
        return mediationManager.isRewardedAdLoaded()
    }

    /**
     * Get time until next interstitial ad can be shown
     */
    fun getTimeUntilNextInterstitial(): Long {
        val now = System.currentTimeMillis()
        val timeSinceLastInterstitial = now - lastInterstitialTime.get()
        return maxOf(0, MIN_INTERSTITIAL_INTERVAL_MS - timeSinceLastInterstitial)
    }

    /**
     * Get time until next rewarded ad can be shown
     */
    fun getTimeUntilNextRewarded(): Long {
        val now = System.currentTimeMillis()
        val timeSinceLastRewarded = now - lastRewardedTime.get()
        return maxOf(0, MIN_REWARDED_INTERVAL_MS - timeSinceLastRewarded)
    }

    /**
     * Smart ad trigger - decides whether to show ad based on context
     */
    fun smartAdTrigger(activity: Activity, trigger: AdTrigger) {
        when (trigger) {
            AdTrigger.CHAT_RESPONSE_COMPLETE -> {
                // Show interstitial after some chat responses
                if (shouldShowAdForChatResponse()) {
                    showInterstitialAd(activity, trigger)
                }
            }
            AdTrigger.IMAGE_GENERATION_COMPLETE -> {
                // Show interstitial after image generation
                if (shouldShowAdForImageGeneration()) {
                    showInterstitialAd(activity, trigger)
                }
            }
            AdTrigger.IMAGE_DOWNLOAD -> {
                // Show interstitial after image download
                showInterstitialAd(activity, trigger)
            }
            else -> {
                // Other triggers
                showInterstitialAd(activity, trigger)
            }
        }
    }

    private fun shouldShowAdForChatResponse(): Boolean {
        // Show ad roughly every 3-5 chat responses
        return Math.random() < 0.3
    }

    private fun shouldShowAdForImageGeneration(): Boolean {
        // Show ad roughly every 2-3 image generations
        return Math.random() < 0.4
    }

    /**
     * Watch ad for credits (from mediation AdManager)
     */
    fun watchAdForCredits(activity: Activity, credits: Int) {
        if (credits != 1) {
            Timber.e("Invalid credit amount: $credits - each ad should give exactly 1 credit")
            if (activity is MainActivity) {
                Toast.makeText(activity, "Invalid credit request", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (PrefsManager.isSubscribed(activity)) {
            if (activity is MainActivity) {
                Toast.makeText(activity, "You're a premium user! No need to watch ads.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Check if user can earn more credits
        if (!creditManager.canEarnMoreCredits(CreditManager.CreditType.CHAT)) {
            if (activity is MainActivity) {
                Toast.makeText(activity, "You've reached your maximum credits for today. Try again tomorrow.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (mediationManager.isRewardedAdLoaded()) {
            showRewardedAdForCredits(activity, credits)
        } else {
            Timber.d("Rewarded ad not ready, starting load")
            pendingRewardCredits = credits
            if (!isLoadingRewardedAd.get()) {
                showAdLoadingDialog(activity)
                loadRewardedAd()
            } else {
                if (activity is MainActivity) {
                    Toast.makeText(activity, "Still loading ad, please wait...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Show rewarded ad specifically for credits
     */
    private fun showRewardedAdForCredits(activity: Activity, credits: Int) {
        if (mediationManager.isRewardedAdLoaded()) {
            Timber.d("Showing rewarded ad for $credits credits")
            mediationManager.showRewardedAd(activity) { rewardAmount ->
                // Each ad gives exactly 1 credit
                val actualReward = 1
                creditManager.addCreditsFromAd(CreditManager.CreditType.CHAT, actualReward)
                if (activity is MainActivity) {
                    activity.updateFreeMessagesText()
                    Toast.makeText(activity, "+$actualReward credits added!", Toast.LENGTH_SHORT).show()
                }
                loadRewardedAd()
            }
        } else {
            Timber.d("Rewarded ad not ready when attempting to show")
            if (activity is MainActivity) {
                Toast.makeText(activity, "Ad not ready, please try again later.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Show ad loading dialog (from mediation AdManager)
     */
    private fun showAdLoadingDialog(activity: Activity) {
        try {
            dismissLoadingDialog()
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Loading Rewarded Ad")
            builder.setMessage("Please wait while we prepare your rewarded ad...")
            builder.setCancelable(true)
            builder.setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                pendingRewardCredits = 0
            }
            val progressBar = ProgressBar(activity)
            builder.setView(progressBar)
            loadingDialog = builder.create()
            loadingDialog?.show()
            mainHandler.postDelayed({
                dismissLoadingDialog()
                if (isLoadingRewardedAd.get()) {
                    if (activity is MainActivity) {
                        Toast.makeText(activity, "Couldn't load ad. Please check your internet connection and try again.", Toast.LENGTH_LONG).show()
                    }
                    pendingRewardCredits = 0
                }
            }, 15000)
            checkAdLoaded(activity)
        } catch (e: Exception) {
            Timber.e(e, "Error showing loading dialog")
            pendingRewardCredits = 0
        }
    }
    
    /**
     * Check if ad is loaded and show if ready
     */
    private fun checkAdLoaded(activity: Activity) {
        adLoadCheckRunnable = Runnable {
            if (mediationManager.isRewardedAdLoaded()) {
                dismissLoadingDialog()
                if (pendingRewardCredits > 0) {
                    showRewardedAdForCredits(activity, pendingRewardCredits)
                    pendingRewardCredits = 0
                }
            } else if (isLoadingRewardedAd.get() && loadingDialog?.isShowing == true) {
                mainHandler.postDelayed(adLoadCheckRunnable!!, 1000)
            }
        }
        mainHandler.post(adLoadCheckRunnable!!)
    }
    
    /**
     * Dismiss loading dialog
     */
    private fun dismissLoadingDialog() {
        try {
            if (loadingDialog?.isShowing == true) {
                loadingDialog?.dismiss()
            }
            loadingDialog = null
        } catch (e: Exception) {
            Timber.e(e, "Error dismissing dialog")
        }
    }
    
    /**
     * Check if ads are loaded (from mediation AdManager)
     */
    fun areAdsLoaded(): Boolean {
        return mediationManager.isRewardedAdLoaded() || mediationManager.isInterstitialAdLoaded()
    }
    
    /**
     * Cleanup resources (from mediation AdManager)
     */
    fun cleanup() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
            adLoadTimeoutRunnable = null
            adLoadCheckRunnable = null
            dismissLoadingDialog()
            pendingRewardCredits = 0
            isLoadingRewardedAd.set(false)
            isLoadingInterstitialAd.set(false)
            
            try {
                mediationManager.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing mediation manager: ${e.message}")
            }
            
            Timber.d("AdManager cleanup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error during AdManager cleanup")
        }
    }

    fun getDebugInfo(): String {
        return """
            Interstitial Ready: ${isInterstitialReady()}
            Rewarded Ready: ${isRewardedReady()}
            Time Until Next Interstitial: ${getTimeUntilNextInterstitial()}ms
            Time Until Next Rewarded: ${getTimeUntilNextRewarded()}ms
            Last Interstitial: ${lastInterstitialTime.get()}
            Last Rewarded: ${lastRewardedTime.get()}
            Is Loading Interstitial: ${isLoadingInterstitialAd.get()}
            Is Loading Rewarded: ${isLoadingRewardedAd.get()}
            Pending Credits: $pendingRewardCredits
        """.trimIndent()
    }
}