package com.cyberflux.qwinai.ads

import android.app.Activity
import android.content.Context
import com.cyberflux.qwinai.ads.mediation.MediationManagerFactory
import com.cyberflux.qwinai.credits.CreditManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive Ad Manager that handles all ad operations
 * Features:
 * - Frequency control to prevent too many ads
 * - Integration with credit system
 * - Automatic ad loading and showing
 * - Rate limiting and cooldown periods
 */
class AdManager private constructor(private val context: Context) {

    companion object {
        private const val MIN_INTERSTITIAL_INTERVAL_MS = 60000L // 1 minute
        private const val MIN_REWARDED_INTERVAL_MS = 30000L // 30 seconds
        private const val MAX_INTERSTITIAL_PER_HOUR = 6
        private const val MAX_REWARDED_PER_HOUR = 10
        
        @Volatile
        private var INSTANCE: AdManager? = null
        
        fun getInstance(context: Context): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val mediationManager = MediationManagerFactory.getMediationManager()
    private val creditManager = CreditManager.getInstance(context)
    
    private var lastInterstitialTime = AtomicLong(0)
    private var lastRewardedTime = AtomicLong(0)
    private var interstitialCountThisHour = AtomicBoolean(false)
    private var rewardedCountThisHour = AtomicBoolean(false)
    private var lastHourReset = AtomicLong(System.currentTimeMillis())
    
    private val interstitialCooldowns = mutableMapOf<String, Long>()
    private val rewardedCooldowns = mutableMapOf<String, Long>()

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

    fun addAdCallback(callback: AdCallback) {
        adCallbacks.add(callback)
    }

    fun removeAdCallback(callback: AdCallback) {
        adCallbacks.remove(callback)
    }

    private fun preloadAds() {
        // Pre-load interstitial ads - requires activity context, will be loaded on-demand
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
     * Show rewarded ad for earning credits
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

        mediationManager.loadRewardedAd(activity, object : com.cyberflux.qwinai.ads.mediation.RewardedAdListener {
            override fun onAdLoaded(networkName: String) {
                mediationManager.showRewardedAd(activity) { rewardAmount ->
                    val creditsEarned = if (rewardAmount > 0) rewardAmount else {
                        // Different credit amounts based on credit type
                        when (creditType) {
                            CreditManager.CreditType.IMAGE_GENERATION -> 2
                            CreditManager.CreditType.CHAT -> 5
                        }
                    }
                    
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
                }
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
        
        callback?.onAdShown(trigger)
        adCallbacks.forEach { it.onAdShown(trigger) }
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

    fun getDebugInfo(): String {
        return """
            Interstitial Ready: ${isInterstitialReady()}
            Rewarded Ready: ${isRewardedReady()}
            Time Until Next Interstitial: ${getTimeUntilNextInterstitial()}ms
            Time Until Next Rewarded: ${getTimeUntilNextRewarded()}ms
            Last Interstitial: ${lastInterstitialTime.get()}
            Last Rewarded: ${lastRewardedTime.get()}
        """.trimIndent()
    }
}