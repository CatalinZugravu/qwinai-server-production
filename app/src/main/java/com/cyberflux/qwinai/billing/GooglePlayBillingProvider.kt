package com.cyberflux.qwinai.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
// import com.android.billingclient.api.PendingPurchasesParams // Not available in 6.2.1
import com.cyberflux.qwinai.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Google Play implementation of the BillingProvider interface
 */
class GooglePlayBillingProvider(private val context: Context) : BillingProvider {

    // LiveData fields
    private val _subscriptionStatus = MutableLiveData<Boolean>()
    override val subscriptionStatus: LiveData<Boolean>
        get() = _subscriptionStatus

    private val _productDetails = MutableLiveData<List<ProductInfo>>()
    override val productDetails: LiveData<List<ProductInfo>>
        get() = _productDetails

    private val _errorMessage = MutableLiveData<String>()
    override val errorMessage: LiveData<String>
        get() = _errorMessage

    // Google Play Billing Client
    private lateinit var billingClient: BillingClient

    // Connection state
    private var isConnected = false
    private var connectionRetryCount = 0
    private val maxConnectionRetries = 5

    // Cache for product details
    private val googleProductDetails = mutableMapOf<String, ProductDetails>()

    // PurchasesUpdatedListener to handle purchase updates
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Handle successful purchases
            CoroutineScope(Dispatchers.IO).launch {
                purchases.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User canceled the purchase flow
            Timber.d("User canceled the purchase")
            _errorMessage.postValue("Purchase canceled")
        } else {
            // Handle other error cases
            Timber.e("Purchase failed: ${billingResult.responseCode}, ${billingResult.debugMessage}")
            _errorMessage.postValue("Purchase failed: ${billingResult.debugMessage}")
        }
    }

    /**
     * Initialize the BillingClient
     */
    override fun initialize() {
        Timber.d("Initializing Google Play Billing Client")
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        connectToPlayBilling()
    }

    /**
     * Connect to Google Play Billing
     */
    private fun connectToPlayBilling() {
        if (isConnected) return

        Timber.d("Connecting to Google Play Billing")
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready
                    Timber.d("Google Play Billing client connected")
                    isConnected = true
                    connectionRetryCount = 0

                    // Query available products and existing purchases
                    queryProducts()
                    checkSubscriptionStatus()
                } else {
                    // Connection problem
                    Timber.e("Google Play Billing setup failed: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                    isConnected = false
                    _errorMessage.postValue("Google Play Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request
                Timber.d("Google Play Billing service disconnected")
                isConnected = false

                // Auto-retry connection with backoff
                if (connectionRetryCount < maxConnectionRetries) {
                    connectionRetryCount++
                    val delayMillis = 1000L * connectionRetryCount
                    Handler(Looper.getMainLooper()).postDelayed({
                        connectToPlayBilling()
                    }, delayMillis)
                }
            }
        })
    }

    /**
     * Query available subscription products
     */
    override fun queryProducts() {
        Timber.d("Querying available Google Play subscription products")
        if (!isConnected) {
            connectToPlayBilling()
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProvider.SUBSCRIPTION_WEEKLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProvider.SUBSCRIPTION_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            // Yearly option removed
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            Timber.d("Google Play product details query result: ${billingResult.responseCode}, ${billingResult.debugMessage}")
            
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                if (productDetailsList.isNotEmpty()) {
                    Timber.d("Found ${productDetailsList.size} Google products")
                    
                    // Cache Google product details for purchase flow
                    for (product in productDetailsList) {
                        googleProductDetails[product.productId] = product
                    }

                    // Convert to common ProductInfo format
                    val commonProducts = mutableListOf<ProductInfo>()
                    for (product in productDetailsList) {
                        val productInfo = convertToProductInfo(product)
                        if (productInfo != null) {
                            commonProducts.add(productInfo)
                        }
                    }

                    // Update the available products
                    _productDetails.postValue(commonProducts)

                    // Log details for debugging
                    for (product in productDetailsList) {
                        Timber.d("Google product: ${product.productId}, ${product.title}")
                    }
                } else {
                    // No products found
                    Timber.e("No Google Play subscription products found")
                    _errorMessage.postValue("No subscription products found. Please check your Google Play Console configuration.")
                }
            } else {
                // Query failed
                Timber.e("Failed to query Google Play product details: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                _errorMessage.postValue("Failed to retrieve subscription details: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Convert Google ProductDetails to common ProductInfo
     */
    private fun convertToProductInfo(productDetails: ProductDetails): ProductInfo? {
        val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull() ?: return null
        val pricingPhase = offerDetails.pricingPhases.pricingPhaseList.firstOrNull() ?: return null

        return ProductInfo(
            productId = productDetails.productId,
            title = productDetails.title,
            description = productDetails.description,
            price = pricingPhase.formattedPrice,
            rawPriceInMicros = pricingPhase.priceAmountMicros,
            currencyCode = pricingPhase.priceCurrencyCode
        )
    }

    /**
     * Query existing purchases
     */
    override fun checkSubscriptionStatus() {
        Timber.d("Querying existing Google Play purchases")
        if (!isConnected) {
            connectToPlayBilling()
            return
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Found ${purchases.size} active Google Play subscriptions")

                // Process any existing subscriptions
                CoroutineScope(Dispatchers.IO).launch {
                    if (purchases.size > 0) {
                        // User has active subscriptions
                        purchases.forEach { purchase ->
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                // Verify and acknowledge purchase if needed
                                handlePurchase(purchase)
                            }
                        }
                    } else {
                        // No active subscriptions in Google Play
                        // Check Huawei status before concluding user has no active subscriptions
                        _subscriptionStatus.postValue(false)
                    }
                }
            } else {
                Timber.e("Failed to query Google Play purchases: ${billingResult.responseCode}, ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Launch Google Play subscription purchase flow
     */
    override fun launchBillingFlow(activity: Activity, productId: String) {
        Timber.d("Launching Google Play subscription flow for product: $productId")

        if (!isConnected) {
            connectToPlayBilling()
            _errorMessage.postValue("Google Play Billing service not connected. Please try again.")
            return
        }

        // Check if we need to query products first
        if (googleProductDetails.isEmpty()) {
            _errorMessage.postValue("Loading product details...")
            queryProducts()
            Handler(Looper.getMainLooper()).postDelayed({
                launchBillingFlow(activity, productId)
            }, 1500)
            return
        }

        // Get the cached ProductDetails
        val productDetails = googleProductDetails[productId]
        if (productDetails == null) {
            Timber.e("No product details found for: $productId")
            _errorMessage.postValue("Subscription details not available. Please try again later.")

            // Refresh products and try again
            queryProducts()
            return
        }

        // Get the offer token for the base plan
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Timber.e("No offer token found for product: $productId")
            _errorMessage.postValue("Subscription offer details not available. Please try again later.")
            return
        }

        // Build the billing flow params
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        // Launch the billing flow
        try {
            val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.e("Failed to launch Google Play billing flow: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                _errorMessage.postValue("Failed to start subscription process: ${billingResult.debugMessage}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception launching billing flow")
            _errorMessage.postValue("Error launching subscription: ${e.message}")
        }
    }

    /**
     * Handle a Google Play purchase
     */
    private fun handlePurchase(purchase: Purchase) {
        Timber.d("Handling purchase: ${purchase.products.joinToString()}, state: ${purchase.purchaseState}")

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verify purchase signature first
            if (!verifyPurchase(purchase)) {
                Timber.e("Purchase signature verification failed")
                _errorMessage.postValue("Purchase verification failed. Please contact support.")
                return
            }

            // Grant entitlement to the user
            if (!purchase.isAcknowledged) {
                // Acknowledge the purchase if it hasn't been acknowledged yet
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Timber.d("Purchase acknowledged")
                        processPurchaseSuccess(purchase)
                    } else {
                        Timber.e("Failed to acknowledge purchase: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                        _errorMessage.postValue("Failed to complete purchase. Please try again.")
                    }
                }
            } else {
                // Purchase already acknowledged, just process it
                processPurchaseSuccess(purchase)
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            // Handle pending purchases (e.g., waiting for payment)
            Timber.d("Purchase is pending. Payment may be processing.")
            _errorMessage.postValue("Your subscription is pending. This may be due to a pending payment.")
        }
    }

    private fun verifyPurchase(purchase: Purchase): Boolean {
        // Basic verification - check if purchase data is valid
        return try {
            purchase.purchaseToken.isNotEmpty() && 
            purchase.products.isNotEmpty() && 
            purchase.purchaseTime > 0
        } catch (e: Exception) {
            Timber.e(e, "Error verifying purchase")
            false
        }
    }

    private fun processPurchaseSuccess(purchase: Purchase) {
        // Calculate subscription end date (for monitoring purposes)
        val subscriptionDays = when {
            purchase.products.contains(BillingProvider.SUBSCRIPTION_WEEKLY) -> 7
            purchase.products.contains(BillingProvider.SUBSCRIPTION_MONTHLY) -> 30
            else -> 0
        }

        // Update subscription status in PrefsManager
        PrefsManager.setSubscribed(context, true, subscriptionDays)
        _subscriptionStatus.postValue(true)
        
        Timber.d("Purchase processed successfully: ${purchase.products.joinToString()}, days: $subscriptionDays")
    }

    /**
     * Release resources
     */
    override fun release() {
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}