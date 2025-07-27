package com.cyberflux.qwinai.ads.mediation

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.Toast
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.MyApp
import com.cyberflux.qwinai.utils.PrefsManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class AdManager(private val activity: MainActivity) {
    private lateinit var mediationManager: AdMediationManager
    private val isLoadingRewardedAd = AtomicBoolean(false)
    private val isLoadingInterstitialAd = AtomicBoolean(false)
    private var adLoadRetryCount = 0
    private val MAX_RETRY_COUNT = 3
    private val AD_LOAD_TIMEOUT = 20000L // 20 seconds
    private var pendingRewardCredits = 0
    private var loadingDialog: AlertDialog? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var adLoadTimeoutRunnable: Runnable? = null
    private var adLoadCheckRunnable: Runnable? = null

    fun initialize(context: Context) {
        try {
            mediationManager = MediationManagerFactory.getMediationManager()
            mediationManager.initialize(context)

            if (!PrefsManager.isSubscribed(activity)) {
                mainHandler.postDelayed({
                    loadInterstitialAd()
                    loadRewardedAd()
                    Timber.d("Initial ad loading started")
                }, 2000)
            }

            Timber.d("Ad system successfully initialized using ${if (MyApp.isHuaweiDevice()) "Huawei" else "Google"} mediation")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing ad system")
        }
    }

    fun loadInterstitialAd() {
        if (!::mediationManager.isInitialized) {
            Timber.d("Ad system not initialized yet")
            return
        }

        if (!isLoadingInterstitialAd.compareAndSet(false, true)) {
            Timber.d("Interstitial ad load already in progress")
            return
        }

        if (PrefsManager.isSubscribed(activity)) {
            isLoadingInterstitialAd.set(false)
            return
        }

        if (PrefsManager.shouldShowAds(activity)) {
            adLoadRetryCount = 0
            adLoadTimeoutRunnable = Runnable {
                if (isLoadingInterstitialAd.get()) {
                    isLoadingInterstitialAd.set(false)
                    Timber.d("Interstitial ad load timed out")
                }
            }
            mainHandler.postDelayed(adLoadTimeoutRunnable!!, AD_LOAD_TIMEOUT)

            mediationManager.loadInterstitialAd(
                activity,
                object : InterstitialAdListener {
                    override fun onAdLoaded(networkName: String) {
                        isLoadingInterstitialAd.set(false)
                        mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                        adLoadRetryCount = 0
                        Timber.d("Interstitial ad loaded successfully from $networkName")
                    }

                    override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                        isLoadingInterstitialAd.set(false)
                        mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                        Timber.d("Failed to load interstitial ad (code: $errorCode): $errorMessage")

                        if (adLoadRetryCount < MAX_RETRY_COUNT) {
                            adLoadRetryCount++
                            Timber.d("Retrying interstitial ad load (attempt $adLoadRetryCount)")
                            mainHandler.postDelayed({ loadInterstitialAd() }, 3000)
                        }
                    }

                    override fun onAdClosed() {
                        Timber.d("Interstitial ad closed, reloading")
                        mainHandler.postDelayed({ loadInterstitialAd() }, 1000)
                    }
                }
            )
        } else {
            isLoadingInterstitialAd.set(false)
        }
    }

    fun loadRewardedAd() {
        if (!::mediationManager.isInitialized) {
            Timber.d("Ad system not initialized yet")
            return
        }

        if (!isLoadingRewardedAd.compareAndSet(false, true)) {
            Timber.d("Rewarded ad load already in progress")
            return
        }

        if (PrefsManager.isSubscribed(activity)) {
            isLoadingRewardedAd.set(false)
            return
        }

        adLoadRetryCount = 0
        adLoadTimeoutRunnable = Runnable {
            if (isLoadingRewardedAd.get()) {
                isLoadingRewardedAd.set(false)
                pendingRewardCredits = 0
                Timber.d("Rewarded ad load timed out")
                dismissLoadingDialog()
                if (pendingRewardCredits > 0) {
                    Toast.makeText(activity, "Ad loading timed out. Please try again later.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        mainHandler.postDelayed(adLoadTimeoutRunnable!!, AD_LOAD_TIMEOUT)

        mediationManager.loadRewardedAd(
            activity,
            object : RewardedAdListener {
                override fun onAdLoaded(networkName: String) {
                    isLoadingRewardedAd.set(false)
                    mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                    adLoadRetryCount = 0
                    Timber.d("Rewarded ad loaded successfully from $networkName")
                    dismissLoadingDialog()
                    if (pendingRewardCredits > 0) {
                        showRewardedAd(pendingRewardCredits)
                        pendingRewardCredits = 0
                    }
                }

                override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                    isLoadingRewardedAd.set(false)
                    mainHandler.removeCallbacks(adLoadTimeoutRunnable!!)
                    Timber.d("Failed to load rewarded ad (code: $errorCode): $errorMessage")

                    if (adLoadRetryCount < MAX_RETRY_COUNT) {
                        adLoadRetryCount++
                        Timber.d("Retrying rewarded ad load (attempt $adLoadRetryCount)")
                        mainHandler.postDelayed({ loadRewardedAd() }, 3000)
                    } else {
                        dismissLoadingDialog()
                        if (pendingRewardCredits > 0) {
                            Toast.makeText(activity, "Couldn't load ad. Please try again later.", Toast.LENGTH_SHORT).show()
                            pendingRewardCredits = 0
                        }
                    }
                }

                override fun onAdClosed() {
                    Timber.d("Rewarded ad closed, reloading")
                    mainHandler.postDelayed({ loadRewardedAd() }, 1000)
                }
            }
        )
    }

    private fun showRewardedAd(credits: Int) {
        if (!::mediationManager.isInitialized) {
            Timber.d("Ad system not initialized yet")
            return
        }

        if (mediationManager.isRewardedAdLoaded()) {
            Timber.d("Showing rewarded ad for $credits credits")
            mediationManager.showRewardedAd(activity) { rewardAmount ->
                val actualReward = if (rewardAmount in 1..5) rewardAmount else 1
                // Use CreditManager instead of direct freeMessagesLeft manipulation
                val creditManager = com.cyberflux.qwinai.credits.CreditManager.getInstance(activity)
                creditManager.addCreditsFromAd(com.cyberflux.qwinai.credits.CreditManager.CreditType.CHAT, actualReward)
                activity.updateFreeMessagesText()
                loadRewardedAd()
                Toast.makeText(activity, "+$actualReward credits added!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Timber.d("Rewarded ad not ready when attempting to show")
            Toast.makeText(activity, "Ad not ready, please try again later.", Toast.LENGTH_SHORT).show()
        }
    }

    fun watchAdForCredits(credits: Int) {
        if (credits <= 0 || credits > 5) {
            Timber.e("Invalid credit amount: $credits")
            Toast.makeText(activity, "Invalid credit request", Toast.LENGTH_SHORT).show()
            return
        }

        if (PrefsManager.isSubscribed(activity)) {
            Toast.makeText(activity, "You're a premium user! No need to watch ads.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if user can earn more credits
        val creditManager = com.cyberflux.qwinai.credits.CreditManager.getInstance(activity)
        if (!creditManager.canEarnMoreCredits(com.cyberflux.qwinai.credits.CreditManager.CreditType.CHAT)) {
            Toast.makeText(activity, "You've reached your maximum credits for today. Try again tomorrow.", Toast.LENGTH_SHORT).show()
            return
        }

        if (mediationManager.isRewardedAdLoaded()) {
            showRewardedAd(credits)
        } else {
            Timber.d("Rewarded ad not ready, starting load")
            pendingRewardCredits = credits
            if (!isLoadingRewardedAd.get()) {
                showAdLoadingDialog()
                loadRewardedAd()
            } else {
                Toast.makeText(activity, "Still loading ad, please wait...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAdLoadingDialog() {
        try {
            dismissLoadingDialog()
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Loading Rewarded Ad")
            builder.setMessage("Please wait while we prepare your rewarded ad...")
            builder.setCancelable(true)
            builder.setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                pendingRewardCredits = 0
            }
            val progressBar = ProgressBar(activity)
            builder.setView(progressBar)
            loadingDialog = builder.create()
            loadingDialog?.show()
            mainHandler.postDelayed({
                dismissLoadingDialog()
                if (isLoadingRewardedAd.get()) {
                    Toast.makeText(activity, "Couldn't load ad. Please check your internet connection and try again.", Toast.LENGTH_LONG).show()
                    pendingRewardCredits = 0
                }
            }, 15000)
            checkAdLoaded()
        } catch (e: Exception) {
            Timber.e(e, "Error showing loading dialog")
            pendingRewardCredits = 0
        }
    }

    private fun checkAdLoaded() {
        if (!::mediationManager.isInitialized) return

        adLoadCheckRunnable = Runnable {
            if (mediationManager.isRewardedAdLoaded()) {
                dismissLoadingDialog()
                if (pendingRewardCredits > 0) {
                    showRewardedAd(pendingRewardCredits)
                    pendingRewardCredits = 0
                }
            } else if (isLoadingRewardedAd.get() && loadingDialog?.isShowing == true) {
                mainHandler.postDelayed(adLoadCheckRunnable!!, 1000)
            }
        }
        mainHandler.post(adLoadCheckRunnable!!)
    }

    private fun dismissLoadingDialog() {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                loadingDialog = null
                return
            }

            if (loadingDialog?.isShowing == true) {
                loadingDialog?.dismiss()
            }
            loadingDialog = null
        } catch (e: Exception) {
            Timber.e(e, "Error dismissing dialog")
        }
    }

    fun areAdsLoaded(): Boolean {
        if (!::mediationManager.isInitialized) return false
        return mediationManager.isRewardedAdLoaded() || mediationManager.isInterstitialAdLoaded()
    }

    fun cleanup() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
            adLoadTimeoutRunnable = null
            adLoadCheckRunnable = null
            dismissLoadingDialog()
            pendingRewardCredits = 0
            isLoadingRewardedAd.set(false)
            isLoadingInterstitialAd.set(false)

            if (::mediationManager.isInitialized) {
                try {
                    mediationManager.release()
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing mediation manager: ${e.message}")
                }
            }

            Timber.d("AdManager cleanup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error during AdManager cleanup")
        }
    }
}
