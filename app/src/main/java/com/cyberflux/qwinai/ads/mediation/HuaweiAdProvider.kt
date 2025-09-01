package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context
import com.cyberflux.qwinai.ads.AdConfig
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdProvider implementation for Huawei Ads Kit.
 * Enhanced with better error handling and detection
 */
class HuaweiAdProvider : BaseAdProvider() {
    override val networkName: String = "Huawei Ads"
    private var isHmsAvailable = false
    private var isInitialized = AtomicBoolean(false)
    private var interstitialAd: Any? = null
    private var rewardedAd: Any? = null
    private var interstitialListener: InterstitialAdCallbacks? = null
    private var rewardedListener: RewardedAdCallbacks? = null

    // Reflected classes
    private var hwAdsClass: Class<*>? = null
    private var interstitialAdClass: Class<*>? = null
    private var rewardAdClass: Class<*>? = null
    private var adParamClass: Class<*>? = null
    private var rewardVerifyConfigClass: Class<*>? = null

    override fun initialize(context: Context) {
        try {
            // Check if we can load HMS classes - with better error reporting
            try {
                hwAdsClass = Class.forName("com.huawei.hms.ads.HwAds")
                Timber.d("Huawei HwAds class found")
            } catch (e: ClassNotFoundException) {
                Timber.e("Huawei HwAds class not found: ${e.message}")
                isHmsAvailable = false
                isInitialized.set(false)
                return
            }

            // Get additional classes with better error handling
            try {
                interstitialAdClass = Class.forName("com.huawei.hms.ads.InterstitialAd")
                rewardAdClass = Class.forName("com.huawei.hms.ads.reward.RewardAd")
                adParamClass = Class.forName("com.huawei.hms.ads.AdParam")
                rewardVerifyConfigClass = Class.forName("com.huawei.hms.ads.reward.RewardVerifyConfig")
                Timber.d("All Huawei ad classes loaded successfully")
            } catch (e: ClassNotFoundException) {
                Timber.e("Huawei ad class not found: ${e.message}")
                isHmsAvailable = false
                isInitialized.set(false)
                return
            }

            // Initialize HwAds
            try {
                val initMethod = hwAdsClass?.getMethod("init", Context::class.java)
                initMethod?.invoke(null, context)
                Timber.d("Huawei HwAds init method called successfully")
            } catch (e: Exception) {
                Timber.e("Failed to initialize Huawei HwAds: ${e.message}")
                isHmsAvailable = false
                isInitialized.set(false)
                return
            }

            isHmsAvailable = true
            isInitialized.set(true)
            Timber.d("Huawei Ads Kit initialized successfully")
        } catch (e: Exception) {
            isHmsAvailable = false
            isInitialized.set(false)
            Timber.e(e, "Failed to initialize Huawei Ads Kit")
        }
    }

    override fun loadInterstitialAd(activity: Activity, listener: InterstitialAdCallbacks) {
        if (!isHmsAvailable || !isInitialized.get()) {
            listener.onAdFailedToLoad(-1, "Huawei Ads Kit not available")
            return
        }

        // Store listener for callbacks
        this.interstitialListener = listener

        try {
            // Get ad unit ID
            val adUnitId = AdConfig.getHuaweiInterstitialId()

            // Clean up previous ad
            interstitialAd?.let { ad ->
                try {
                    val destroyMethod = interstitialAdClass?.getMethod("destroy")
                    destroyMethod?.invoke(ad)
                } catch (e: Exception) {
                    Timber.e(e, "Error destroying previous Huawei interstitial ad")
                }
            }
            interstitialAd = null

            // Create new interstitial ad
            val constructor = interstitialAdClass?.getConstructor(Context::class.java, String::class.java)
            val ad = constructor?.newInstance(activity, adUnitId)
            interstitialAd = ad

            // Create ad listener
            createInterstitialAdListener(ad, listener)

            // Create ad param
            val adParam = createAdParam()

            // Load ad
            val loadMethod = interstitialAdClass?.getMethod("loadAd", adParamClass)
            loadMethod?.invoke(ad, adParam)

            Timber.d("Huawei interstitial ad load requested for ID: $adUnitId")
        } catch (e: Exception) {
            Timber.e(e, "Error loading Huawei interstitial ad")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * Create interstitial ad listener via reflection
     */
    private fun createInterstitialAdListener(ad: Any?, listener: InterstitialAdCallbacks) {
        try {
            // Reflect listener interface
            val adListenerClass = Class.forName("com.huawei.hms.ads.AdListener")

            // Create proxy instance
            val adListener = java.lang.reflect.Proxy.newProxyInstance(
                adListenerClass.classLoader,
                arrayOf(adListenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onAdLoaded" -> {
                        Timber.d("Huawei interstitial ad loaded")
                        listener.onAdLoaded()
                        null
                    }
                    "onAdFailed" -> {
                        val errorCode = args?.getOrNull(0)?.toString()?.toIntOrNull() ?: -1
                        val errorMsg = "Huawei ad load failed: error code $errorCode"
                        Timber.e(errorMsg)
                        listener.onAdFailedToLoad(errorCode, errorMsg)
                        null
                    }
                    "onAdClosed" -> {
                        Timber.d("Huawei interstitial ad closed")
                        interstitialAd = null
                        listener.onAdClosed()
                        null
                    }
                    "onAdClicked" -> {
                        Timber.d("Huawei interstitial ad clicked")
                        null
                    }
                    "onAdLeave" -> {
                        Timber.d("Huawei interstitial ad left")
                        null
                    }
                    "onAdOpened" -> {
                        Timber.d("Huawei interstitial ad opened")
                        null
                    }
                    "onAdImpression" -> {
                        Timber.d("Huawei interstitial ad impression")
                        null
                    }
                    else -> null
                }
            }

            // Set the listener
            val setAdListenerMethod = interstitialAdClass?.getMethod("setAdListener", adListenerClass)
            setAdListenerMethod?.invoke(ad, adListener)

        } catch (e: Exception) {
            Timber.e(e, "Error creating Huawei interstitial ad listener")
        }
    }

    override fun showInterstitialAd(activity: Activity) {
        if (!isHmsAvailable || !isInitialized.get()) {
            Timber.d("Huawei Ads Kit not available")
            return
        }

        try {
            val ad = interstitialAd

            if (ad != null) {
                // Check if ad is loaded
                val isLoadedMethod = interstitialAdClass?.getMethod("isLoaded")
                val isLoaded = isLoadedMethod?.invoke(ad) as? Boolean == true

                if (isLoaded) {
                    // Show the ad
                    val showMethod = interstitialAdClass?.getMethod("show")
                    showMethod?.invoke(ad)
                    Timber.d("Huawei interstitial ad shown")
                } else {
                    Timber.d("Huawei interstitial ad not loaded")
                }
            } else {
                Timber.d("Huawei interstitial ad not initialized")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing Huawei interstitial ad")
        }
    }

    override fun loadRewardedAd(activity: Activity, listener: RewardedAdCallbacks) {
        if (!isHmsAvailable || !isInitialized.get()) {
            listener.onAdFailedToLoad(-1, "Huawei Ads Kit not available")
            return
        }

        // Store listener for callbacks
        this.rewardedListener = listener

        try {
            // Get ad unit ID
            val adUnitId = AdConfig.getHuaweiRewardedId()

            // Clean up previous ad
            rewardedAd = null

            // Load rewarded ad
            loadHuaweiRewardedAd(activity, adUnitId, listener)

            Timber.d("Huawei rewarded ad load requested for ID: $adUnitId")
        } catch (e: Exception) {
            Timber.e(e, "Error loading Huawei rewarded ad")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * Load Huawei rewarded ad with proper listener setup
     */
    private fun loadHuaweiRewardedAd(activity: Activity, adUnitId: String, listener: RewardedAdCallbacks) {
        try {
            // Create ad params
            val adParam = createAdParam()

            // Create load listener
            val loadListenerClass = Class.forName("com.huawei.hms.ads.reward.RewardAdLoadListener")

            val loadListener = java.lang.reflect.Proxy.newProxyInstance(
                loadListenerClass.classLoader,
                arrayOf(loadListenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onRewardAdFailedToLoad" -> {
                        val errorCode = args?.getOrNull(0)?.toString()?.toIntOrNull() ?: -1
                        val errorMsg = "Huawei rewarded ad load failed: error code $errorCode"
                        Timber.e(errorMsg)
                        listener.onAdFailedToLoad(errorCode, errorMsg)
                        null
                    }
                    "onRewardedLoaded" -> {
                        Timber.d("Huawei rewarded ad loaded")

                        // Get the loaded ad
                        rewardedAd = args?.getOrNull(0)

                        // Set up reward listener
                        createRewardedAdListener(rewardedAd, listener)

                        listener.onAdLoaded()
                        null
                    }
                    else -> null
                }
            }

            // Static load method
            val loadMethod = rewardAdClass?.getMethod(
                "loadAd",
                Context::class.java,
                String::class.java,
                adParamClass,
                loadListenerClass
            )

            loadMethod?.invoke(null, activity, adUnitId, adParam, loadListener)

        } catch (e: Exception) {
            Timber.e(e, "Error in loadHuaweiRewardedAd: ${e.message}")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * Create rewarded ad listener via reflection
     */
    private fun createRewardedAdListener(ad: Any?, listener: RewardedAdCallbacks) {
        try {
            if (ad == null) return

            // Reflect listener interface
            val rewardAdListenerClass = Class.forName("com.huawei.hms.ads.reward.RewardAdListener")

            // Create proxy instance
            val rewardListener = java.lang.reflect.Proxy.newProxyInstance(
                rewardAdListenerClass.classLoader,
                arrayOf(rewardAdListenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onRewarded" -> {
                        // Parse reward amount
                        val rewardObj = args?.getOrNull(0)
                        val amount = try {
                            val amountMethod = rewardObj?.javaClass?.getMethod("getAmount")
                            (amountMethod?.invoke(rewardObj) as? Int) ?: 1
                        } catch (e: Exception) {
                            1 // Default to 1 on error
                        }

                        Timber.d("Huawei rewarded ad completed with reward amount: $amount")
                        listener.onUserRewarded(amount)
                        null
                    }
                    "onRewardAdClosed" -> {
                        Timber.d("Huawei rewarded ad closed")
                        rewardedAd = null
                        listener.onAdClosed()
                        null
                    }
                    "onRewardAdFailedToShow" -> {
                        val errorCode = args?.getOrNull(0)?.toString()?.toIntOrNull() ?: -1
                        Timber.e("Huawei rewarded ad failed to show: error code $errorCode")
                        rewardedAd = null
                        null
                    }
                    "onRewardAdOpened" -> {
                        Timber.d("Huawei rewarded ad opened")
                        null
                    }
                    "onRewardAdLeftApp" -> {
                        Timber.d("Huawei rewarded ad left app")
                        null
                    }
                    "onRewardAdClicked" -> {
                        Timber.d("Huawei rewarded ad clicked")
                        null
                    }
                    "onRewardAdStarted" -> {
                        Timber.d("Huawei rewarded ad started")
                        null
                    }
                    "onRewardAdCompleted" -> {
                        Timber.d("Huawei rewarded ad playback completed")
                        null
                    }
                    else -> null
                }
            }

            // Set the listener
            val setRewardAdListenerMethod = rewardAdClass?.getMethod("setRewardAdListener", rewardAdListenerClass)
            setRewardAdListenerMethod?.invoke(ad, rewardListener)

        } catch (e: Exception) {
            Timber.e(e, "Error creating Huawei rewarded ad listener")
        }
    }

    /**
     * Create ad parameters for Huawei Ads
     */
    private fun createAdParam(): Any? {
        try {
            // Create builder
            val builderClass = Class.forName("com.huawei.hms.ads.AdParam\$Builder")
            val builder = builderClass.getConstructor().newInstance()

            // Build ad params
            val buildMethod = builderClass.getMethod("build")
            return buildMethod.invoke(builder)
        } catch (e: Exception) {
            Timber.e(e, "Error creating Huawei ad params")
            return null
        }
    }

    override fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit) {
        if (!isHmsAvailable || !isInitialized.get()) {
            Timber.d("Huawei Ads Kit not available")
            return
        }

        try {
            val ad = rewardedAd

            if (ad != null) {
                // Create reward verification config (optional)
                val verifyConfig = createRewardVerifyConfig()

                // Get show method
                val showMethod = if (verifyConfig != null) {
                    rewardAdClass?.getMethod("show", Activity::class.java, rewardVerifyConfigClass)
                } else {
                    rewardAdClass?.getMethod("show", Activity::class.java)
                }

                // Set up lambda to receive reward
                val currentListener = rewardedListener
                if (currentListener != null) {
                    // Replace the original listener with one that also calls onRewarded
                    createRewardedAdListener(ad, object : RewardedAdCallbacks {
                        override fun onAdLoaded() {
                            currentListener.onAdLoaded()
                        }

                        override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                            currentListener.onAdFailedToLoad(errorCode, errorMessage)
                        }

                        override fun onAdClosed() {
                            currentListener.onAdClosed()
                        }

                        override fun onUserRewarded(amount: Int) {
                            // Call both the stored listener and the passed lambda
                            currentListener.onUserRewarded(amount)
                            onRewarded(amount)
                        }
                    })
                }

                // Show the ad
                if (verifyConfig != null) {
                    showMethod?.invoke(ad, activity, verifyConfig)
                } else {
                    showMethod?.invoke(ad, activity)
                }

                Timber.d("Huawei rewarded ad shown")
            } else {
                Timber.d("Huawei rewarded ad not ready")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing Huawei rewarded ad")
        }
    }

    /**
     * Create reward verification config
     */
    private fun createRewardVerifyConfig(): Any? {
        try {
            // Create builder
            val builderClass = Class.forName("com.huawei.hms.ads.reward.RewardVerifyConfig\$Builder")
            val builder = builderClass.getConstructor().newInstance()

            // Set user ID (optional)
            val setUserIdMethod = builderClass.getMethod("setUserId", String::class.java)
            setUserIdMethod.invoke(builder, "user_" + System.currentTimeMillis())

            // Set custom data (optional)
            val setCustomDataMethod = builderClass.getMethod("setCustomData", String::class.java)
            setCustomDataMethod.invoke(builder, "app_custom_data")

            // Build config
            val buildMethod = builderClass.getMethod("build")
            return buildMethod.invoke(builder)
        } catch (e: Exception) {
            Timber.e(e, "Error creating Huawei reward verify config")
            return null
        }
    }

    override fun isRewardedAdLoaded(): Boolean {
        if (!isHmsAvailable || !isInitialized.get() || rewardedAd == null) {
            return false
        }

        try {
            val ad = rewardedAd
            val isLoadedMethod = rewardAdClass?.getMethod("isLoaded")
            return isLoadedMethod?.invoke(ad) as? Boolean == true
        } catch (e: Exception) {
            Timber.e(e, "Error checking if Huawei rewarded ad is loaded")
            return false
        }
    }

    override fun isInterstitialAdLoaded(): Boolean {
        if (!isHmsAvailable || !isInitialized.get() || interstitialAd == null) {
            return false
        }

        try {
            val ad = interstitialAd
            val isLoadedMethod = interstitialAdClass?.getMethod("isLoaded")
            return isLoadedMethod?.invoke(ad) as? Boolean == true
        } catch (e: Exception) {
            Timber.e(e, "Error checking if Huawei interstitial ad is loaded")
            return false
        }
    }

    override fun release() {
        try {
            // Destroy interstitial ad
            interstitialAd?.let { ad ->
                try {
                    val destroyMethod = interstitialAdClass?.getMethod("destroy")
                    destroyMethod?.invoke(ad)
                } catch (e: Exception) {
                    Timber.e(e, "Error destroying Huawei interstitial ad")
                }
            }

            // Reset all references
            interstitialAd = null
            rewardedAd = null
            interstitialListener = null
            rewardedListener = null

            Timber.d("Huawei ad provider released")
        } catch (e: Exception) {
            Timber.e(e, "Error in Huawei ad provider release")
        }
    }
}