package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mediation manager for Google devices
 * Uses AdMob (Google Ads) only - other networks removed for APK size optimization
 */
class GoogleDeviceMediationManager : AdMediationManager {
    private val adProviders = mutableListOf<BaseAdProvider>()
    private var currentInterstitialProvider: BaseAdProvider? = null
    private var currentRewardedProvider: BaseAdProvider? = null
    private var interstitialWaterfallIndex = 0
    private var rewardedWaterfallIndex = 0
    private var rewardAmount = 0

    // Track initialization status for each provider
    private val initializedProviders = ConcurrentHashMap<String, Boolean>()
    private val pendingInitCount = AtomicInteger(0)

    override fun initialize(context: Context) {
        try {
            // FIXED: Use AdMob ONLY for Google devices as requested
            adProviders.clear() // Clear any existing providers to avoid duplicates

            adProviders.add(GoogleAdProvider())        // AdMob ONLY for Google devices

            // Initialize all providers with proper tracking
            pendingInitCount.set(adProviders.size)

            // Initialize each provider
            adProviders.forEach { provider ->
                // Track which provider is being initialized
                initializedProviders[provider.networkName] = false

                // Initialize on a background thread to avoid blocking UI
                Thread {
                    try {
                        provider.initialize(context)
                        initializedProviders[provider.networkName] = true
                        Timber.d("Provider ${provider.networkName} initialized successfully")
                    } catch (e: Exception) {
                        Timber.e(e, "Error initializing provider ${provider.networkName}")
                    } finally {
                        // Count down regardless of success/failure
                        pendingInitCount.decrementAndGet()
                    }
                }.start()
            }

            Timber.d("Initialized Google device mediation with ${adProviders.size} provider: AdMob ONLY")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up Google device mediation: ${e.message}")
        }
    }

    override fun loadInterstitialAd(activity: Activity, listener: InterstitialAdListener) {
        // Wait for initialization if needed
        if (pendingInitCount.get() > 0) {
            Timber.d("Waiting for providers to initialize before loading interstitial")
            Thread {
                // Wait up to 5 seconds for initialization
                var waitCount = 0
                while (pendingInitCount.get() > 0 && waitCount < 50) {
                    Thread.sleep(100)
                    waitCount++
                }

                // Now load the ad on the main thread
                activity.runOnUiThread {
                    startInterstitialWaterfall(activity, listener)
                }
            }.start()
        } else {
            // Already initialized, start waterfall
            startInterstitialWaterfall(activity, listener)
        }
    }

    private fun startInterstitialWaterfall(activity: Activity, listener: InterstitialAdListener) {
        // Reset waterfall index
        interstitialWaterfallIndex = 0
        loadNextInterstitialInWaterfall(activity, listener)
    }

    private fun loadNextInterstitialInWaterfall(activity: Activity, listener: InterstitialAdListener) {
        if (interstitialWaterfallIndex >= adProviders.size) {
            // We've tried all providers and none worked
            listener.onAdFailedToLoad(-1, "All ad networks failed to load interstitial ad")
            return
        }

        val provider = adProviders[interstitialWaterfallIndex]

        // Skip providers that failed to initialize
        if (initializedProviders[provider.networkName] != true) {
            Timber.d("Skipping uninitialized provider: ${provider.networkName}")
            interstitialWaterfallIndex++
            loadNextInterstitialInWaterfall(activity, listener)
            return
        }

        Timber.d("Trying to load interstitial from: ${provider.networkName}")

        provider.loadInterstitialAd(activity, object : BaseAdProvider.InterstitialAdCallbacks {
            override fun onAdLoaded() {
                currentInterstitialProvider = provider
                listener.onAdLoaded(provider.networkName)
                Timber.d("Successfully loaded interstitial from: ${provider.networkName}")
            }

            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                Timber.d("Failed to load interstitial from: ${provider.networkName}, error: $errorMessage")

                // Try next provider in waterfall
                interstitialWaterfallIndex++
                loadNextInterstitialInWaterfall(activity, listener)
            }

            override fun onAdClosed() {
                // Clear reference and notify listener
                currentInterstitialProvider = null
                listener.onAdClosed()
            }
        })
    }

    override fun showInterstitialAd(activity: Activity) {
        currentInterstitialProvider?.let { provider ->
            if (provider.isInterstitialAdLoaded()) {
                Timber.d("Showing interstitial ad from: ${provider.networkName}")
                provider.showInterstitialAd(activity)
            } else {
                Timber.d("Interstitial ad from ${provider.networkName} was marked as loaded but is not actually ready")
                // Clear provider reference to allow waterfall to restart on next load
                currentInterstitialProvider = null
            }
        } ?: Timber.d("No interstitial ad ready to show")
    }

    override fun loadRewardedAd(activity: Activity, listener: RewardedAdListener) {
        // Wait for initialization if needed
        if (pendingInitCount.get() > 0) {
            Timber.d("Waiting for providers to initialize before loading rewarded")
            Thread {
                // Wait up to 5 seconds for initialization
                var waitCount = 0
                while (pendingInitCount.get() > 0 && waitCount < 50) {
                    Thread.sleep(100)
                    waitCount++
                }

                // Now load the ad on the main thread
                activity.runOnUiThread {
                    startRewardedWaterfall(activity, listener)
                }
            }.start()
        } else {
            // Already initialized, start waterfall
            startRewardedWaterfall(activity, listener)
        }
    }

    private fun startRewardedWaterfall(activity: Activity, listener: RewardedAdListener) {
        // Reset waterfall index
        rewardedWaterfallIndex = 0
        loadNextRewardedInWaterfall(activity, listener)
    }

    private fun loadNextRewardedInWaterfall(activity: Activity, listener: RewardedAdListener) {
        if (rewardedWaterfallIndex >= adProviders.size) {
            // We've tried all providers and none worked
            listener.onAdFailedToLoad(-1, "All ad networks failed to load rewarded ad")
            return
        }

        val provider = adProviders[rewardedWaterfallIndex]

        // Skip providers that failed to initialize
        if (initializedProviders[provider.networkName] != true) {
            Timber.d("Skipping uninitialized provider: ${provider.networkName}")
            rewardedWaterfallIndex++
            loadNextRewardedInWaterfall(activity, listener)
            return
        }

        Timber.d("Trying to load rewarded from: ${provider.networkName}")

        provider.loadRewardedAd(activity, object : BaseAdProvider.RewardedAdCallbacks {
            override fun onAdLoaded() {
                currentRewardedProvider = provider
                listener.onAdLoaded(provider.networkName)
                Timber.d("Successfully loaded rewarded from: ${provider.networkName}")
            }

            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                Timber.d("Failed to load rewarded from: ${provider.networkName}, error: $errorMessage")

                // Try next provider in waterfall
                rewardedWaterfallIndex++
                loadNextRewardedInWaterfall(activity, listener)
            }

            override fun onAdClosed() {
                // Clear reference and notify listener
                currentRewardedProvider = null
                listener.onAdClosed()
            }

            override fun onUserRewarded(amount: Int) {
                rewardAmount = amount
            }
        })
    }

    override fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit) {
        currentRewardedProvider?.let { provider ->
            if (provider.isRewardedAdLoaded()) {
                Timber.d("Showing rewarded ad from: ${provider.networkName}")
                rewardAmount = 0 // Reset reward amount
                provider.showRewardedAd(activity) { amount ->
                    rewardAmount = amount
                    onRewarded(amount)
                }
            } else {
                Timber.d("Rewarded ad from ${provider.networkName} was marked as loaded but is not actually ready")
                // Clear provider reference to allow waterfall to restart on next load
                currentRewardedProvider = null
            }
        } ?: Timber.d("No rewarded ad ready to show")
    }

    override fun isRewardedAdLoaded(): Boolean = currentRewardedProvider?.isRewardedAdLoaded() == true

    override fun isInterstitialAdLoaded(): Boolean = currentInterstitialProvider?.isInterstitialAdLoaded() == true

    override fun release() {
        // Release resources for all providers
        adProviders.forEach { provider ->
            try {
                provider.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing provider ${provider.networkName}")
            }
        }

        // Clear provider references
        currentInterstitialProvider = null
        currentRewardedProvider = null

        // Reset waterfall indexes
        interstitialWaterfallIndex = 0
        rewardedWaterfallIndex = 0

        Timber.d("Google device mediation released")
    }
}