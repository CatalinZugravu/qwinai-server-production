package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context

/**
 * Primary interface for ad mediation managers
 * Defines methods for initializing, loading, and showing ads
 */
interface AdMediationManager {
    /**
     * Initialize the ad mediation system
     * @param context Application context for initialization
     */
    fun initialize(context: Context)

    /**
     * Load an interstitial ad
     * @param activity Activity context for loading ad
     * @param listener Callback for ad events
     */
    fun loadInterstitialAd(activity: Activity, listener: InterstitialAdListener)

    /**
     * Show a previously loaded interstitial ad
     * @param activity Activity context for showing ad
     */
    fun showInterstitialAd(activity: Activity)

    /**
     * Load a rewarded ad
     * @param activity Activity context for loading ad
     * @param listener Callback for ad events
     */
    fun loadRewardedAd(activity: Activity, listener: RewardedAdListener)

    /**
     * Show a previously loaded rewarded ad
     * @param activity Activity context for showing ad
     * @param onRewarded Callback for when user earns reward
     */
    fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit)

    /**
     * Check if a rewarded ad is loaded and ready to show
     * @return true if ad is loaded, false otherwise
     */
    fun isRewardedAdLoaded(): Boolean

    /**
     * Check if an interstitial ad is loaded and ready to show
     * @return true if ad is loaded, false otherwise
     */
    fun isInterstitialAdLoaded(): Boolean

    /**
     * Release resources to prevent memory leaks
     * Call this from activity onDestroy or when completely finished with ads
     */
    fun release()
}

/**
 * Callbacks for interstitial ads in mediation system
 */
interface InterstitialAdListener {
    /**
     * Called when ad is successfully loaded
     * @param networkName Name of ad network that loaded the ad
     */
    fun onAdLoaded(networkName: String)

    /**
     * Called when ad fails to load
     * @param errorCode Network-specific error code
     * @param errorMessage Human-readable error message
     */
    fun onAdFailedToLoad(errorCode: Int, errorMessage: String)

    /**
     * Called when ad is closed by user
     */
    fun onAdClosed()
}

/**
 * Callbacks for rewarded ads in mediation system
 */
interface RewardedAdListener {
    /**
     * Called when ad is successfully loaded
     * @param networkName Name of ad network that loaded the ad
     */
    fun onAdLoaded(networkName: String)

    /**
     * Called when ad fails to load
     * @param errorCode Network-specific error code
     * @param errorMessage Human-readable error message
     */
    fun onAdFailedToLoad(errorCode: Int, errorMessage: String)

    /**
     * Called when ad is closed by user
     */
    fun onAdClosed()
}