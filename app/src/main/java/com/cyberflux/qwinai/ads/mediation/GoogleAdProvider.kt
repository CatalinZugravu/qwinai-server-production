package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context
import com.cyberflux.qwinai.ads.AdConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import timber.log.Timber

class GoogleAdProvider : BaseAdProvider() {
    override val networkName: String = "AdMob"
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedListener: RewardedAdCallbacks? = null
    private var isInitialized = false

    override fun initialize(context: Context) {
        try {
            // Initialize Google Mobile Ads SDK
            MobileAds.initialize(context) { initializationStatus ->
                val statusMap = initializationStatus.adapterStatusMap
                var allInitialized = true

                // Check if all adapters initialized successfully without comparing to specific int values
                for ((adapter, status) in statusMap) {
                    // Check if initialization was not successful
                    if (status.initializationState != com.google.android.gms.ads.initialization.AdapterStatus.State.READY) {
                        allInitialized = false
                        Timber.w("Adapter $adapter failed to initialize: ${status.description}")
                    }
                }

                isInitialized = true
                Timber.d("Google AdMob initialized, all adapters ready: $allInitialized")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing Google AdMob")
            isInitialized = false
        }
    }

    override fun loadInterstitialAd(activity: Activity, listener: InterstitialAdCallbacks) {
        if (!isInitialized) {
            listener.onAdFailedToLoad(-1, "Google AdMob not initialized")
            return
        }

        // Use AdConfig to get the correct ad unit ID
        val adUnitId = AdConfig.getAdMobInterstitialId()

        // Null out any existing reference to ensure no memory leaks
        interstitialAd = null

        Timber.d("Loading Google interstitial ad with ID: $adUnitId")

        InterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Timber.d("Google interstitial ad loaded successfully")

                    // Set full screen callback
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Timber.d("Google interstitial ad dismissed")
                            interstitialAd = null
                            listener.onAdClosed()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Timber.e("Google interstitial ad failed to show: ${adError.message}")
                            interstitialAd = null
                            listener.onAdFailedToLoad(adError.code, adError.message)
                        }

                        override fun onAdShowedFullScreenContent() {
                            Timber.d("Google interstitial ad shown full screen")
                        }

                        override fun onAdClicked() {
                            Timber.d("Google interstitial ad clicked")
                        }

                        override fun onAdImpression() {
                            Timber.d("Google interstitial ad recorded an impression")
                        }
                    }

                    listener.onAdLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    Timber.e("Failed to load Google interstitial ad: ${loadAdError.message}")
                    listener.onAdFailedToLoad(loadAdError.code, loadAdError.message)
                }
            }
        )
    }

    override fun showInterstitialAd(activity: Activity) {
        val ad = interstitialAd
        if (ad != null) {
            Timber.d("Showing Google interstitial ad")
            ad.show(activity)
        } else {
            Timber.d("Google interstitial ad not ready to show")
        }
    }

    override fun loadRewardedAd(activity: Activity, listener: RewardedAdCallbacks) {
        if (!isInitialized) {
            listener.onAdFailedToLoad(-1, "Google AdMob not initialized")
            return
        }

        // Store listener for reward callback
        this.rewardedListener = listener

        // Use AdConfig to get the correct ad unit ID
        val adUnitId = AdConfig.getAdMobRewardedId()

        // Null out any existing reference to ensure no memory leaks
        rewardedAd = null

        Timber.d("Loading Google rewarded ad with ID: $adUnitId")

        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Timber.d("Google rewarded ad loaded successfully")

                    // Set full screen callback
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Timber.d("Google rewarded ad dismissed")
                            rewardedAd = null
                            listener.onAdClosed()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Timber.e("Google rewarded ad failed to show: ${adError.message}")
                            rewardedAd = null
                            listener.onAdFailedToLoad(adError.code, adError.message)
                        }

                        override fun onAdShowedFullScreenContent() {
                            Timber.d("Google rewarded ad shown full screen")
                        }

                        override fun onAdClicked() {
                            Timber.d("Google rewarded ad clicked")
                        }

                        override fun onAdImpression() {
                            Timber.d("Google rewarded ad recorded an impression")
                        }
                    }

                    listener.onAdLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    Timber.e("Failed to load Google rewarded ad: ${loadAdError.message}")
                    listener.onAdFailedToLoad(loadAdError.code, loadAdError.message)
                }
            }
        )
    }

    override fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            Timber.d("Showing Google rewarded ad")
            ad.show(activity) { rewardItem ->
                val amount = rewardItem.amount
                Timber.d("Google rewarded ad: User earned reward of $amount")
                onRewarded(amount)
                rewardedListener?.onUserRewarded(amount)
            }
        } else {
            Timber.d("Google rewarded ad not ready to show")
        }
    }

    override fun isRewardedAdLoaded(): Boolean = rewardedAd != null

    override fun isInterstitialAdLoaded(): Boolean = interstitialAd != null

    override fun release() {
        // Clear references to prevent memory leaks
        interstitialAd = null
        rewardedAd = null
        rewardedListener = null
        isInitialized = false
        Timber.d("Google AdMob provider released")
    }
}