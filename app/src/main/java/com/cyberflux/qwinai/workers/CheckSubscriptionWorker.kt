package com.cyberflux.qwinai.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Worker to periodically check subscription status to handle edge cases
 * like network issues during purchase acknowledgment
 */
class CheckSubscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting subscription check worker")

            // Check local subscription status
            val isSubscribed = PrefsManager.isSubscribed(applicationContext)
            val subscriptionEndTime = PrefsManager.getSubscriptionEndTime(applicationContext)

            // Simple validation - if the subscription is marked as active but has expired, reset it
            if (isSubscribed && subscriptionEndTime > 0 && subscriptionEndTime < System.currentTimeMillis()) {
                Timber.d("Subscription has expired, resetting status")
                PrefsManager.setSubscribed(applicationContext, false, 0)
            }

            // Get billing manager
            val billingManager = BillingManager.getInstance(applicationContext)

            // Connect to billing service
            var connected = false
            try {
                withContext(Dispatchers.Main) {
                    billingManager.connectToPlayBilling { success ->
                        connected = success
                    }
                }

                // Wait for connection
                var attempts = 0
                while (!connected && attempts < 3) {
                    kotlinx.coroutines.delay(1000)
                    attempts++
                }

                if (connected) {
                    Timber.d("Connected to billing service, checking subscription")

                    // Check subscription status - this will query existing subscriptions
                    billingManager.restoreSubscriptions()

                    // Wait for check to complete
                    kotlinx.coroutines.delay(2000)

                    // Refresh subscription status
                    billingManager.refreshSubscriptionStatus()

                    Timber.d("Subscription check completed, status: ${PrefsManager.isSubscribed(applicationContext)}")
                } else {
                    Timber.w("Failed to connect to billing service")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking subscription status")
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Subscription check worker failed")
            Result.retry()
        }
    }
}