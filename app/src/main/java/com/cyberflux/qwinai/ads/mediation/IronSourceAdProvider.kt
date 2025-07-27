package com.cyberflux.qwinai.ads.mediation

import android.app.Activity
import android.content.Context
import com.cyberflux.qwinai.ads.AdConfig
import com.cyberflux.qwinai.BuildConfig
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.integration.IntegrationHelper
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.model.Placement
import com.ironsource.mediationsdk.sdk.InterstitialListener
import com.ironsource.mediationsdk.sdk.RewardedVideoListener
import timber.log.Timber

class IronSourceAdProvider : BaseAdProvider() {
    override val networkName: String = "IronSource"

    private var interstitialListener: InterstitialAdCallbacks? = null
    private var rewardedListener: RewardedAdCallbacks? = null
    private var isInitialized = false
    private var isInterstitialLoaded = false
    private var isRewardedLoaded = false

    override fun initialize(context: Context) {
        try {
            val appKey = AdConfig.getIronSourceAppKey()

            if (appKey == "YOUR_IRONSOURCE_APP_KEY") {
                Timber.e("IronSource app key not configured properly")
                return
            }

            // Set user ID (optional)
            IronSource.setUserId("user_id_${System.currentTimeMillis()}")

            // Set metadata for compliance (if needed)
            IronSource.setMetaData("is_child_directed", "false")

            // Set listeners
            setListeners()

            // Initialize the SDK
            IronSource.init(context as Activity, appKey, IronSource.AD_UNIT.INTERSTITIAL, IronSource.AD_UNIT.REWARDED_VIDEO)

            // Validate integration (optional, for debugging)
            if (BuildConfig.DEBUG) {
                IntegrationHelper.validateIntegration(context)
            }

            isInitialized = true
            Timber.d("IronSource SDK initialization completed")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing IronSource SDK")
            isInitialized = false
        }
    }

    private fun setListeners() {
        // Set Interstitial listener
        IronSource.setInterstitialListener(object : InterstitialListener {
            override fun onInterstitialAdReady() {
                isInterstitialLoaded = true
                Timber.d("IronSource interstitial ad ready")
                interstitialListener?.onAdLoaded()
            }

            override fun onInterstitialAdLoadFailed(error: IronSourceError) {
                isInterstitialLoaded = false
                Timber.e("IronSource interstitial ad load failed: ${error.errorMessage}")
                interstitialListener?.onAdFailedToLoad(error.errorCode, error.errorMessage)
            }

            override fun onInterstitialAdOpened() {
                Timber.d("IronSource interstitial ad opened")
            }

            override fun onInterstitialAdClosed() {
                isInterstitialLoaded = false
                Timber.d("IronSource interstitial ad closed")
                interstitialListener?.onAdClosed()
            }

            override fun onInterstitialAdShowSucceeded() {
                Timber.d("IronSource interstitial ad show succeeded")
            }

            override fun onInterstitialAdShowFailed(error: IronSourceError) {
                isInterstitialLoaded = false
                Timber.e("IronSource interstitial ad show failed: ${error.errorMessage}")
                interstitialListener?.onAdFailedToLoad(error.errorCode, error.errorMessage)
            }

            override fun onInterstitialAdClicked() {
                Timber.d("IronSource interstitial ad clicked")
            }
        })

        // Set Rewarded Video listener
        IronSource.setRewardedVideoListener(object : RewardedVideoListener {
            override fun onRewardedVideoAdOpened() {
                Timber.d("IronSource rewarded video opened")
            }

            override fun onRewardedVideoAdClosed() {
                isRewardedLoaded = false
                Timber.d("IronSource rewarded video closed")
                rewardedListener?.onAdClosed()
            }

            override fun onRewardedVideoAvailabilityChanged(available: Boolean) {
                isRewardedLoaded = available
                if (available) {
                    Timber.d("IronSource rewarded video available")
                    rewardedListener?.onAdLoaded()
                } else {
                    Timber.d("IronSource rewarded video unavailable")
                    rewardedListener?.onAdFailedToLoad(-1, "Rewarded video unavailable")
                }
            }

            override fun onRewardedVideoAdStarted() {
                Timber.d("IronSource rewarded video started")
            }

            override fun onRewardedVideoAdEnded() {
                Timber.d("IronSource rewarded video ended")
            }

            override fun onRewardedVideoAdRewarded(placement: Placement) {
                val rewardAmount = placement.rewardAmount
                Timber.d("IronSource rewarded video rewarded: amount=$rewardAmount")
                rewardedListener?.onUserRewarded(rewardAmount)
            }

            override fun onRewardedVideoAdShowFailed(error: IronSourceError) {
                isRewardedLoaded = false
                Timber.e("IronSource rewarded video show failed: ${error.errorMessage}")
                rewardedListener?.onAdFailedToLoad(error.errorCode, error.errorMessage)
            }

            override fun onRewardedVideoAdClicked(placement: Placement) {
                Timber.d("IronSource rewarded video clicked")
            }
        })
    }

    override fun loadInterstitialAd(activity: Activity, listener: InterstitialAdCallbacks) {
        if (!isInitialized) {
            listener.onAdFailedToLoad(-1, "IronSource SDK not initialized")
            return
        }

        try {
            // Store listener for callbacks
            this.interstitialListener = listener

            // Load interstitial ad
            IronSource.loadInterstitial()
            Timber.d("IronSource interstitial ad load requested")
        } catch (e: Exception) {
            Timber.e(e, "Error loading IronSource interstitial ad")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    override fun showInterstitialAd(activity: Activity) {
        try {
            if (IronSource.isInterstitialReady()) {
                Timber.d("Showing IronSource interstitial ad")
                IronSource.showInterstitial()
            } else {
                Timber.d("IronSource interstitial ad not ready to show")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing IronSource interstitial ad")
        }
    }

    override fun loadRewardedAd(activity: Activity, listener: RewardedAdCallbacks) {
        if (!isInitialized) {
            listener.onAdFailedToLoad(-1, "IronSource SDK not initialized")
            return
        }

        try {
            // Store listener for callbacks
            this.rewardedListener = listener

            // Check if rewarded video is available
            if (IronSource.isRewardedVideoAvailable()) {
                isRewardedLoaded = true
                listener.onAdLoaded()
                Timber.d("IronSource rewarded video already available")
            } else {
                // Rewarded videos are automatically loaded by IronSource
                // The availability change will trigger the callback
                Timber.d("IronSource rewarded video not available, waiting for load")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking IronSource rewarded video availability")
            listener.onAdFailedToLoad(-1, e.message ?: "Unknown error")
        }
    }

    override fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit) {
        try {
            if (IronSource.isRewardedVideoAvailable()) {
                Timber.d("Showing IronSource rewarded video ad")

                // Create a temporary listener to handle the reward callback
                val originalListener = rewardedListener

                IronSource.setRewardedVideoListener(object : RewardedVideoListener {
                    override fun onRewardedVideoAdOpened() {
                        Timber.d("IronSource rewarded video opened")
                    }

                    override fun onRewardedVideoAdClosed() {
                        isRewardedLoaded = false
                        Timber.d("IronSource rewarded video closed")
                        originalListener?.onAdClosed()
                        // Restore original listener
                        setListeners()
                    }

                    override fun onRewardedVideoAvailabilityChanged(available: Boolean) {
                        originalListener?.let { listener ->
                            if (available) {
                                listener.onAdLoaded()
                            } else {
                                listener.onAdFailedToLoad(-1, "Rewarded video unavailable")
                            }
                        }
                    }

                    override fun onRewardedVideoAdStarted() {
                        Timber.d("IronSource rewarded video started")
                    }

                    override fun onRewardedVideoAdEnded() {
                        Timber.d("IronSource rewarded video ended")
                    }

                    override fun onRewardedVideoAdRewarded(placement: Placement) {
                        val rewardAmount = placement.rewardAmount
                        Timber.d("IronSource rewarded video rewarded: amount=$rewardAmount")
                        originalListener?.onUserRewarded(rewardAmount)
                        onRewarded(rewardAmount)
                    }

                    override fun onRewardedVideoAdShowFailed(error: IronSourceError) {
                        isRewardedLoaded = false
                        Timber.e("IronSource rewarded video show failed: ${error.errorMessage}")
                        originalListener?.onAdFailedToLoad(error.errorCode, error.errorMessage)
                    }

                    override fun onRewardedVideoAdClicked(placement: Placement) {
                        Timber.d("IronSource rewarded video clicked")
                    }
                })

                IronSource.showRewardedVideo()
            } else {
                Timber.d("IronSource rewarded video ad not available to show")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing IronSource rewarded video ad")
        }
    }

    override fun isRewardedAdLoaded(): Boolean = isRewardedLoaded && IronSource.isRewardedVideoAvailable()

    override fun isInterstitialAdLoaded(): Boolean = isInterstitialLoaded && IronSource.isInterstitialReady()

    override fun release() {
        try {
            // Clean up references
            interstitialListener = null
            rewardedListener = null
            isInitialized = false
            isInterstitialLoaded = false
            isRewardedLoaded = false

            Timber.d("IronSource ad provider released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing IronSource ad provider")
        }
    }
}