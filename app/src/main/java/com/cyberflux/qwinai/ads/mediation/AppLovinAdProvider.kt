package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.cyberflux.qwinai.ads.AdConfig
import timber.log.Timber

class AppLovinAdProvider : BaseAdProvider() {
    override val networkName: String = "AppLovin"

    private var interstitialAd: MaxInterstitialAd? = null
    private var rewardedAd: MaxRewardedAd? = null
    private var rewardedListener: RewardedAdCallbacks? = null
    private var isInitialized = false

    override fun initialize(context: Context) {
        try {
            val sdkKey = AdConfig.getAppLovinSdkKey()

            if (sdkKey == "YOUR_APPLOVIN_SDK_KEY") {
                Timber.e("AppLovin SDK key not configured properly")
                return
            }

            // Initialize AppLovin MAX SDK
            isInitialized = true
            Timber.d("AppLovin SDK initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing AppLovin SDK")
            isInitialized = false
        }
    }

    override fun loadInterstitialAd(activity: Activity, listener: InterstitialAdCallbacks) {
        if (!isInitialized) {
            listener.onAdFailedToLoad(-1, "AppLovin SDK not initialized")
            return
        }

        try {
            val adUnitId = AdConfig.getAppLovinInterstitialId()

            if (adUnitId == "YOUR_APPLOVIN_INTERSTITIAL_ID") {
                listener.onAdFailedToLoad(-1, "AppLovin interstitial ad unit ID not configured")
                return
            }

            // Clean up previous ad
            interstitialAd?.destroy()
            interstitialAd = null

            // Create new interstitial ad
            interstitialAd = MaxInterstitialAd(adUnitId, activity)

            interstitialAd?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Timber.d("AppLovin interstitial ad loaded successfully")
                    listener.onAdLoaded()
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    Timber.d("AppLovin interstitial ad displayed")
                }

                override fun onAdHidden(ad: MaxAd) {
                    Timber.d("AppLovin interstitial ad hidden")
                    listener.onAdClosed()
                }

                override fun onAdClicked(ad: MaxAd) {
                    Timber.d("AppLovin interstitial ad clicked")
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Timber.e("Failed to load AppLovin interstitial ad: ${error.message}")
                    listener.onAdFailedToLoad(error.code, error.message)
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Timber.e("AppLovin interstitial ad failed to display: ${error.message}")
                    listener.onAdFailedToLoad(error.code, error.message)
                }
            })

            // Load the ad
            interstitialAd?.loadAd()
            Timber.d("AppLovin interstitial ad load requested")
        } catch (e: Exception) {
            Timber.e(e, "Error loading AppLovin interstitial ad")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    override fun showInterstitialAd(activity: Activity) {
        try {
            val ad = interstitialAd
            if (ad != null && ad.isReady) {
                Timber.d("Showing AppLovin interstitial ad")
                ad.showAd()
            } else {
                Timber.d("AppLovin interstitial ad not ready to show")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing AppLovin interstitial ad")
        }
    }

    override fun loadRewardedAd(activity: Activity, listener: RewardedAdCallbacks) {
        if (!isInitialized) {
            listener.onAdFailedToLoad(-1, "AppLovin SDK not initialized")
            return
        }

        try {
            val adUnitId = AdConfig.getAppLovinRewardedId()

            if (adUnitId == "YOUR_APPLOVIN_REWARDED_ID") {
                listener.onAdFailedToLoad(-1, "AppLovin rewarded ad unit ID not configured")
                return
            }

            // Store listener for reward callback
            this.rewardedListener = listener

            // Clean up previous ad
            rewardedAd?.destroy()
            rewardedAd = null

            // Create new rewarded ad
            rewardedAd = MaxRewardedAd.getInstance(adUnitId, activity)

            rewardedAd?.setListener(object : MaxRewardedAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Timber.d("AppLovin rewarded ad loaded successfully")
                    listener.onAdLoaded()
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    Timber.d("AppLovin rewarded ad displayed")
                }

                override fun onAdHidden(ad: MaxAd) {
                    Timber.d("AppLovin rewarded ad hidden")
                    listener.onAdClosed()
                }

                override fun onAdClicked(ad: MaxAd) {
                    Timber.d("AppLovin rewarded ad clicked")
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Timber.e("Failed to load AppLovin rewarded ad: ${error.message}")
                    listener.onAdFailedToLoad(error.code, error.message)
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Timber.e("AppLovin rewarded ad failed to display: ${error.message}")
                    listener.onAdFailedToLoad(error.code, error.message)
                }

                override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                    val amount = reward.amount
                    Timber.d("AppLovin rewarded ad: User earned reward of $amount")
                    listener.onUserRewarded(amount)
                }
            })

            // Load the ad
            rewardedAd?.loadAd()
            Timber.d("AppLovin rewarded ad load requested")
        } catch (e: Exception) {
            Timber.e(e, "Error loading AppLovin rewarded ad")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    override fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit) {
        try {
            val ad = rewardedAd
            if (ad != null && ad.isReady) {
                Timber.d("Showing AppLovin rewarded ad")

                // Create a temporary listener that handles both original callbacks and the lambda
                val originalListener = rewardedListener
                rewardedAd?.setListener(object : MaxRewardedAdListener {
                    override fun onAdLoaded(ad: MaxAd) {
                        originalListener?.onAdLoaded()
                    }

                    override fun onAdDisplayed(ad: MaxAd) {
                        Timber.d("AppLovin rewarded ad displayed")
                    }

                    override fun onAdHidden(ad: MaxAd) {
                        Timber.d("AppLovin rewarded ad hidden")
                        originalListener?.onAdClosed()
                    }

                    override fun onAdClicked(ad: MaxAd) {
                        Timber.d("AppLovin rewarded ad clicked")
                    }

                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                        originalListener?.onAdFailedToLoad(error.code, error.message)
                    }

                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                        originalListener?.onAdFailedToLoad(error.code, error.message)
                    }

                    override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                        val amount = reward.amount
                        Timber.d("AppLovin rewarded ad: User earned reward of $amount")
                        originalListener?.onUserRewarded(amount)
                        onRewarded(amount)
                    }
                })

                ad.showAd()
            } else {
                Timber.d("AppLovin rewarded ad not ready to show")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing AppLovin rewarded ad")
        }
    }

    override fun isRewardedAdLoaded(): Boolean = rewardedAd?.isReady ?: false

    override fun isInterstitialAdLoaded(): Boolean = interstitialAd?.isReady ?: false

    override fun release() {
        try {
            // Destroy ads properly
            interstitialAd?.destroy()
            rewardedAd?.destroy()

            // Clean up references
            interstitialAd = null
            rewardedAd = null
            rewardedListener = null
            isInitialized = false

            Timber.d("AppLovin ad provider released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing AppLovin ad provider")
        }
    }
}