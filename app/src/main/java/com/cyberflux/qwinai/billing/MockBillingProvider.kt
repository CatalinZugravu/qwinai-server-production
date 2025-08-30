package com.cyberflux.qwinai.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.utils.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Mock billing provider for debug builds to test subscription flows
 * This allows developers to test the full subscription experience without real purchases
 */
class MockBillingProvider(private val context: Context) : BillingProvider {

    // StateFlow for subscription status
    private val _subscriptionStatus = MutableStateFlow(false)
    override val subscriptionStatus: StateFlow<Boolean> = _subscriptionStatus.asStateFlow()

    // StateFlow for product details
    private val _productDetails = MutableStateFlow<List<ProductInfo>>(emptyList())
    override val productDetails: StateFlow<List<ProductInfo>> = _productDetails.asStateFlow()

    // StateFlow for error messages
    private val _errorMessage = MutableStateFlow("")
    override val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var isInitialized = false

    init {
        // Only enable in debug builds
        if (BuildConfig.DEBUG) {
            Timber.d("🧪 MockBillingProvider initialized for debug build")
        }
    }

    override fun initialize() {
        if (!BuildConfig.DEBUG) {
            Timber.w("MockBillingProvider should only be used in debug builds")
            return
        }

        if (isInitialized) {
            Timber.d("MockBillingProvider already initialized")
            return
        }

        Timber.d("🧪 Initializing MockBillingProvider...")
        
        // Initialize subscription status from preferences
        _subscriptionStatus.value = PrefsManager.isSubscribed(context)
        
        isInitialized = true
        Timber.d("🧪 MockBillingProvider initialization completed")
    }

    override fun queryProducts() {
        if (!BuildConfig.DEBUG) return

        Timber.d("🧪 Querying mock products...")

        // Create mock product data that matches the real subscription products
        val mockProducts = listOf(
            ProductInfo(
                productId = BillingProvider.SUBSCRIPTION_WEEKLY,
                title = "Weekly Premium",
                description = "7 days of premium features with unlimited AI conversations",
                price = "$4.99",
                rawPriceInMicros = 4990000,
                currencyCode = "USD"
            ),
            ProductInfo(
                productId = BillingProvider.SUBSCRIPTION_MONTHLY,
                title = "Monthly Premium",
                description = "30 days of premium features with unlimited AI conversations",
                price = "$9.99",
                rawPriceInMicros = 9990000,
                currencyCode = "USD"
            )
        )

        // Simulate network delay for realistic testing
        Handler(Looper.getMainLooper()).postDelayed({
            _productDetails.value = mockProducts
            Timber.d("🧪 Mock products loaded: ${mockProducts.size} products")
        }, 1000) // 1 second delay to simulate network request
    }

    override fun launchBillingFlow(activity: Activity, productId: String) {
        if (!BuildConfig.DEBUG) {
            _errorMessage.value = "Mock billing only available in debug builds"
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            _errorMessage.value = "Activity is no longer valid"
            return
        }

        Timber.d("🧪 Launching mock billing flow for product: $productId")

        // Find the product info
        val product = _productDetails.value.find { it.productId == productId }
        if (product == null) {
            _errorMessage.value = "Product not found: $productId"
            return
        }

        // Show debug purchase dialog
        showMockPurchaseDialog(activity, product)
    }

    private fun showMockPurchaseDialog(activity: Activity, product: ProductInfo) {
        val productName = when (product.productId) {
            BillingProvider.SUBSCRIPTION_WEEKLY -> "Weekly Premium"
            BillingProvider.SUBSCRIPTION_MONTHLY -> "Monthly Premium"
            else -> product.title
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("🧪 Mock Purchase")
            .setMessage("This is a mock purchase dialog for testing.\n\n" +
                       "Product: $productName\n" +
                       "Price: ${product.price}\n\n" +
                       "Choose an outcome to test different scenarios:")
            .setPositiveButton("✅ Success") { _, _ ->
                simulatePurchaseSuccess(product)
            }
            .setNegativeButton("❌ Failed") { _, _ ->
                simulatePurchaseFailure("Mock purchase failed for testing")
            }
            .setNeutralButton("🚫 Cancelled") { _, _ ->
                simulatePurchaseCancellation()
            }
            .setCancelable(true)
            .create()

        dialog.show()
    }

    private fun simulatePurchaseSuccess(product: ProductInfo) {
        Timber.d("🧪 Simulating successful purchase for: ${product.productId}")

        // Calculate subscription end time
        val currentTime = System.currentTimeMillis()
        val subscriptionDuration = when (product.productId) {
            BillingProvider.SUBSCRIPTION_WEEKLY -> 7 * 24 * 60 * 60 * 1000L // 7 days
            BillingProvider.SUBSCRIPTION_MONTHLY -> 30 * 24 * 60 * 60 * 1000L // 30 days
            else -> 30 * 24 * 60 * 60 * 1000L // Default to 30 days
        }
        val endTime = currentTime + subscriptionDuration

        // Update subscription status
        PrefsManager.setSubscribed(context, true, endTime)
        PrefsManager.setSubscriptionType(context, product.productId)

        // Update StateFlow
        _subscriptionStatus.value = true

        // Simulate delay for realistic experience
        Handler(Looper.getMainLooper()).postDelayed({
            Timber.d("🧪 Mock purchase completed successfully")
        }, 500)
    }

    private fun simulatePurchaseFailure(errorMessage: String) {
        Timber.d("🧪 Simulating purchase failure: $errorMessage")
        
        Handler(Looper.getMainLooper()).postDelayed({
            _errorMessage.value = errorMessage
        }, 500)
    }

    private fun simulatePurchaseCancellation() {
        Timber.d("🧪 Simulating purchase cancellation")
        
        Handler(Looper.getMainLooper()).postDelayed({
            _errorMessage.value = "Purchase cancelled by user"
        }, 300)
    }

    override fun checkSubscriptionStatus() {
        if (!BuildConfig.DEBUG) return

        Timber.d("🧪 Checking mock subscription status...")

        // Check if subscription has expired
        val currentTime = System.currentTimeMillis()
        val endTime = PrefsManager.getSubscriptionEndTime(context)
        
        val isSubscribed = PrefsManager.isSubscribed(context) && 
                          (endTime == 0L || endTime > currentTime)

        if (PrefsManager.isSubscribed(context) && endTime > 0 && endTime <= currentTime) {
            // Subscription expired
            PrefsManager.setSubscribed(context, false, 0)
            Timber.d("🧪 Mock subscription expired")
        }

        _subscriptionStatus.value = isSubscribed
        Timber.d("🧪 Mock subscription status: $isSubscribed")
    }

    override fun release() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("🧪 Releasing MockBillingProvider resources")
        isInitialized = false
    }

    /**
     * Debug helper to simulate subscription expiry
     */
    fun simulateSubscriptionExpiry() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("🧪 Simulating subscription expiry")
        PrefsManager.setSubscribed(context, false, 0)
        _subscriptionStatus.value = false
    }

    /**
     * Debug helper to grant free premium for testing
     */
    fun grantFreePremium(durationDays: Int = 30) {
        if (!BuildConfig.DEBUG) return
        
        val endTime = System.currentTimeMillis() + (durationDays * 24 * 60 * 60 * 1000L)
        PrefsManager.setSubscribed(context, true, endTime)
        PrefsManager.setSubscriptionType(context, "debug_free_premium")
        _subscriptionStatus.value = true
        
        Timber.d("🧪 Granted free premium for $durationDays days")
    }
}