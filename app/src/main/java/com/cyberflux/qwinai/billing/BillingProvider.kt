package com.cyberflux.qwinai.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for billing provider operations (Google Play or Huawei IAP)
 */
interface BillingProvider {
    // Product IDs - standardized across Google and Huawei
    companion object {
        // Make sure these match EXACTLY with your store console product IDs
        const val SUBSCRIPTION_WEEKLY = "qwinai_weekly_subscription"
        const val SUBSCRIPTION_MONTHLY = "qwinai_monthly_subscription"
    }

    // Subscription status state flow
    val subscriptionStatus: StateFlow<Boolean>

    // Product information state flow
    val productDetails: StateFlow<List<ProductInfo>>

    // Error messages state flow
    val errorMessage: StateFlow<String>

    // Initialize billing connection
    fun initialize()

    // Query available products
    fun queryProducts()

    // Start subscription purchase flow
    fun launchBillingFlow(activity: Activity, productId: String)

    // Check existing subscription status
    fun checkSubscriptionStatus()

    // Clean up resources
    fun release()
}

/**
 * Common product information model for both Google and Huawei
 */
data class ProductInfo(
    val productId: String,
    val title: String,
    val description: String,
    val price: String,
    val rawPriceInMicros: Long,
    val currencyCode: String
)