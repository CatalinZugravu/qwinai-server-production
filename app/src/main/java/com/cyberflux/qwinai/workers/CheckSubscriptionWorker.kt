package com.cyberflux.qwinai.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Enhanced worker to periodically check subscription status and handle:
 * - Expired subscriptions
 * - Network issues during purchase acknowledgment
 * - Feature access management
 * - Premium/free mode transitions
 */
class CheckSubscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔄 Starting enhanced subscription check worker")

            // Initialize subscription manager if needed
            SubscriptionManager.initialize(applicationContext)

            // Check local subscription status first
            val wasSubscribed = PrefsManager.isSubscribed(applicationContext)
            val subscriptionEndTime = PrefsManager.getSubscriptionEndTime(applicationContext)

            Timber.d("Current subscription status: $wasSubscribed, end time: $subscriptionEndTime")

            // Check if subscription has expired
            if (wasSubscribed && subscriptionEndTime > 0 && subscriptionEndTime < System.currentTimeMillis()) {
                Timber.w("⚠️ Subscription has expired, transitioning to free mode")
                
                // Reset subscription status
                PrefsManager.setSubscribed(applicationContext, false, 0)
                
                // Update subscription manager
                SubscriptionManager.updateSubscriptionStatus(applicationContext)
                
                // Log the transition
                Timber.d("✅ Successfully transitioned expired subscription to free mode")
            }

            // Get billing manager for remote verification
            val billingManager = BillingManager.getInstance(applicationContext)

            // Attempt to connect and verify with remote billing service
            var connectionSuccessful = false
            
            try {
                withContext(Dispatchers.Main) {
                    billingManager.connectToPlayBilling { success ->
                        connectionSuccessful = success
                    }
                }

                // Wait for connection with retry logic
                var connectionAttempts = 0
                val maxAttempts = 3
                
                while (!connectionSuccessful && connectionAttempts < maxAttempts) {
                    kotlinx.coroutines.delay(1500) // Slightly longer delay
                    connectionAttempts++
                    Timber.d("Connection attempt $connectionAttempts of $maxAttempts")
                }

                if (connectionSuccessful) {
                    Timber.d("✅ Connected to billing service, performing remote verification")

                    // Validate with remote billing service
                    billingManager.validateSubscriptionStatus()
                    
                    // Check for active subscriptions
                    billingManager.restoreSubscriptions()

                    // Wait for remote verification to complete
                    kotlinx.coroutines.delay(3000) // Longer wait for remote calls

                    // Refresh local status based on remote verification
                    billingManager.refreshSubscriptionStatus()

                    // Update subscription manager with latest status
                    SubscriptionManager.updateSubscriptionStatus(applicationContext)
                    
                    val finalStatus = PrefsManager.isSubscribed(applicationContext)
                    Timber.d("✅ Remote verification completed. Final status: $finalStatus")
                    
                    // Log status change if any
                    if (wasSubscribed != finalStatus) {
                        Timber.i("📱 Subscription status changed during verification: $wasSubscribed -> $finalStatus")
                    }
                    
                } else {
                    Timber.w("❌ Failed to connect to billing service after $maxAttempts attempts")
                    
                    // Fallback: rely on local validation only
                    SubscriptionManager.updateSubscriptionStatus(applicationContext)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Error during remote subscription verification: ${e.message}")
                
                // Even if remote check fails, ensure local status is consistent
                SubscriptionManager.updateSubscriptionStatus(applicationContext)
            }

            // Perform additional cleanup and validation
            performSubscriptionMaintenance()

            Timber.d("✅ Subscription check worker completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Subscription check worker failed: ${e.message}")
            
            // Even on error, try to maintain consistent local state
            try {
                SubscriptionManager.updateSubscriptionStatus(applicationContext)
            } catch (cleanupError: Exception) {
                Timber.e(cleanupError, "Failed to maintain subscription state during error recovery")
            }
            
            // Retry on failure with exponential backoff handled by WorkManager
            Result.retry()
        }
    }

    /**
     * Perform additional subscription maintenance tasks
     */
    private suspend fun performSubscriptionMaintenance() {
        try {
            Timber.d("🔧 Performing subscription maintenance")
            
            // Clear any stale subscription data
            val currentTime = System.currentTimeMillis()
            val endTime = PrefsManager.getSubscriptionEndTime(applicationContext)
            
            // If subscription ended more than 7 days ago, clean up completely
            if (endTime > 0 && (currentTime - endTime) > 7 * 24 * 60 * 60 * 1000) {
                Timber.d("🧹 Cleaning up old subscription data (expired >7 days ago)")
                PrefsManager.setSubscribed(applicationContext, false, 0)
            }
            
            // Ensure ad preferences are consistent with subscription status
            val isSubscribed = PrefsManager.isSubscribed(applicationContext)
            val shouldShowAds = PrefsManager.shouldShowAds(applicationContext)
            
            if (isSubscribed && shouldShowAds) {
                Timber.d("🔧 Fixing ad preferences for subscribed user")
                PrefsManager.setShouldShowAds(applicationContext, false)
            } else if (!isSubscribed && !shouldShowAds) {
                Timber.d("🔧 Fixing ad preferences for free user")
                PrefsManager.setShouldShowAds(applicationContext, true)
            }
            
            Timber.d("✅ Subscription maintenance completed")
            
        } catch (e: Exception) {
            Timber.e(e, "Error during subscription maintenance: ${e.message}")
        }
    }
}