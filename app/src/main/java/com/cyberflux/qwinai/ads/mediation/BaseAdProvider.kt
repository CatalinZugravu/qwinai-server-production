package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context

/**
 * Abstract base class for ad providers
 * Defines common interface and callback methods
 */
abstract class BaseAdProvider {
    /**
     * The name of this ad network (for logging and debugging)
     */
    abstract val networkName: String

    /**
     * Initialize the ad provider
     */
    abstract fun initialize(context: Context)

    /**
     * Load an interstitial ad
     */
    abstract fun loadInterstitialAd(activity: Activity, listener: InterstitialAdCallbacks)

    /**
     * Show an interstitial ad
     */
    abstract fun showInterstitialAd(activity: Activity)

    /**
     * Load a rewarded ad
     */
    abstract fun loadRewardedAd(activity: Activity, listener: RewardedAdCallbacks)

    /**
     * Show a rewarded ad with callback for reward
     */
    abstract fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit)

    /**
     * Check if rewarded ad is loaded and ready to show
     */
    abstract fun isRewardedAdLoaded(): Boolean

    /**
     * Check if interstitial ad is loaded and ready to show
     */
    abstract fun isInterstitialAdLoaded(): Boolean

    /**
     * Release resources to prevent memory leaks
     * Each provider should implement appropriate cleanup
     */
    abstract fun release()

    /**
     * Callback interface for interstitial ads
     */
    interface InterstitialAdCallbacks {
        /**
         * Called when an ad is successfully loaded and ready to show
         */
        fun onAdLoaded()

        /**
         * Called when ad loading fails with error information
         */
        fun onAdFailedToLoad(errorCode: Int, errorMessage: String)

        /**
         * Called when an ad is closed by the user
         */
        fun onAdClosed()
    }

    /**
     * Callback interface for rewarded ads
     */
    interface RewardedAdCallbacks {
        /**
         * Called when an ad is successfully loaded and ready to show
         */
        fun onAdLoaded()

        /**
         * Called when ad loading fails with error information
         */
        fun onAdFailedToLoad(errorCode: Int, errorMessage: String)

        /**
         * Called when an ad is closed by the user
         */
        fun onAdClosed()

        /**
         * Called when user completes watching ad and earns reward
         * @param amount The reward amount (may vary by network)
         */
        fun onUserRewarded(amount: Int)
    }
}