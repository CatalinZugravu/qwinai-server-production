package com.cyberflux.qwinai.ads

import timber.log.Timber

/**
 * Centralized configuration for ad unit IDs.
 * Contains ad units for AdMob, AppLovin, IronSource, and Huawei Ads Kit
 */
object AdConfig {
    // Set to true to use test ads for debugging
    private const val USE_TEST_ADS = true
    
    /**
     * Initialize ad configuration
     */
    fun initialize() {
        val adsType = if (USE_TEST_ADS) "TEST" else "PRODUCTION"
        Timber.d("Ad configuration initialized with $adsType ads")

        // Log IDs that will be used
        Timber.d("AdMob Interstitial: ${getAdMobInterstitialId()}")
        Timber.d("AdMob Rewarded: ${getAdMobRewardedId()}")
        Timber.d("AppLovin Interstitial: ${getAppLovinInterstitialId()}")
        Timber.d("AppLovin Rewarded: ${getAppLovinRewardedId()}")
        Timber.d("IronSource App Key: ${getIronSourceAppKey()}")
        Timber.d("IronSource Interstitial: ${getIronSourceInterstitialId()}")
        Timber.d("IronSource Rewarded: ${getIronSourceRewardedId()}")
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

    /**
     * AppLovin ad unit IDs
     */
    object AppLovin {
        // Production SDK Key - replace with your actual AppLovin SDK key
        const val PROD_SDK_KEY = "YOUR_APPLOVIN_SDK_KEY"

        // Production ad unit IDs - replace with your actual AppLovin ad unit IDs
        const val PROD_INTERSTITIAL_AD_UNIT_ID = "YOUR_APPLOVIN_INTERSTITIAL_ID"
        const val PROD_REWARDED_AD_UNIT_ID = "YOUR_APPLOVIN_REWARDED_ID"
    }

    /**
     * IronSource ad unit IDs
     */
    object IronSource {
        // Production App Key - replace with your actual IronSource app key
        const val PROD_APP_KEY = "YOUR_IRONSOURCE_APP_KEY"

        // Production placement IDs - replace with your actual IronSource placement IDs
        const val PROD_INTERSTITIAL_PLACEMENT_ID = "DefaultInterstitial"
        const val PROD_REWARDED_PLACEMENT_ID = "DefaultRewardedVideo"
    }

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

    // Getter methods for AppLovin
    fun getAppLovinSdkKey(): String = AppLovin.PROD_SDK_KEY
    fun getAppLovinInterstitialId(): String = AppLovin.PROD_INTERSTITIAL_AD_UNIT_ID
    fun getAppLovinRewardedId(): String = AppLovin.PROD_REWARDED_AD_UNIT_ID

    // Getter methods for IronSource
    fun getIronSourceAppKey(): String = IronSource.PROD_APP_KEY
    fun getIronSourceInterstitialId(): String = IronSource.PROD_INTERSTITIAL_PLACEMENT_ID
    fun getIronSourceRewardedId(): String = IronSource.PROD_REWARDED_PLACEMENT_ID

    // Getter methods for Huawei
    fun getHuaweiInterstitialId(): String = Huawei.PROD_INTERSTITIAL_AD_UNIT_ID
    fun getHuaweiRewardedId(): String = Huawei.PROD_REWARDED_AD_UNIT_ID
}