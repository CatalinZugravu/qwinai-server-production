package com.cyberflux.qwinai.ads

import timber.log.Timber

/**
 * Centralized configuration for ad unit IDs.
 * Contains ad units for AdMob, AppLovin, IronSource, and Huawei Ads Kit
 */
object AdConfig {
    // Set to true to use test ads for debugging
    private const val USE_TEST_ADS = false
    
    /**
     * Initialize ad configuration
     */
    fun initialize() {
        val adsType = if (USE_TEST_ADS) "TEST" else "PRODUCTION"
        Timber.d("Ad configuration initialized with $adsType ads")

        // Log IDs that will be used
        Timber.d("AdMob Interstitial: ${getAdMobInterstitialId()}")
        Timber.d("AdMob Rewarded: ${getAdMobRewardedId()}")
        Timber.d("Huawei Interstitial: ${getHuaweiInterstitialId()}")
        Timber.d("Huawei Rewarded: ${getHuaweiRewardedId()}")
    }

    /**
     * AdMob (Google) ad unit IDs
     */
    object AdMob {
        // Test IDs for debugging
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        
        // Production IDs
        const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-6013627845589729/3687643738"
        const val PROD_REWARDED_AD_UNIT_ID = "ca-app-pub-6013627845589729/1791635370"
    }

    // AppLovin and IronSource ad networks - REMOVED (SDKs no longer included)

    /**
     * Huawei Ads Kit ad unit IDs
     */
    object Huawei {
        // Production IDs - replace with your actual Huawei ad unit IDs
        const val PROD_INTERSTITIAL_AD_UNIT_ID = "testx9dtjwj8hp"
        const val PROD_REWARDED_AD_UNIT_ID = "testx9dtjwj8hp"
    }

    // Getter methods for AdMob
    fun getAdMobInterstitialId(): String = if (USE_TEST_ADS) AdMob.TEST_INTERSTITIAL_AD_UNIT_ID else AdMob.PROD_INTERSTITIAL_AD_UNIT_ID
    fun getAdMobRewardedId(): String = if (USE_TEST_ADS) AdMob.TEST_REWARDED_AD_UNIT_ID else AdMob.PROD_REWARDED_AD_UNIT_ID
    
    // Legacy compatibility for BuildConfig references
    val ADMOB_REWARDED_AD_ID: String get() = getAdMobRewardedId()

    // AppLovin and IronSource getter methods - REMOVED (SDKs no longer included)

    // Getter methods for Huawei
    fun getHuaweiInterstitialId(): String = Huawei.PROD_INTERSTITIAL_AD_UNIT_ID
    fun getHuaweiRewardedId(): String = Huawei.PROD_REWARDED_AD_UNIT_ID
}