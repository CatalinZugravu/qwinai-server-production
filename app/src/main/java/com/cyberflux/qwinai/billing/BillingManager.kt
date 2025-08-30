package com.cyberflux.qwinai.billing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.cyberflux.qwinai.MyApp
import com.cyberflux.qwinai.utils.PrefsManager
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.cyberflux.qwinai.BuildConfig

/**
 * BillingManager - Detects platform and delegates to appropriate billing provider
 * Optimized version with faster initialization and improved caching
 */
class BillingManager private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: BillingManager? = null

        // Flag to track if initialization has started
        private var initializationStarted = false

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also {
                    instance = it
                    // Start pre-initialization immediately when instance is created
                    it.preInitialize()
                }
            }
        }
    }

    // Billing providers
    private var googleProvider: GooglePlayBillingProvider? = null
    private var huaweiProvider: HuaweiIapProvider? = null
    private var mockProvider: MockBillingProvider? = null

    // Active provider based on device
    var activeProvider: BillingProvider? = null
        private set

    // Connection state
    private var isConnected = false
    private var isInitialized = false

    // StateFlow
    private val _productDetails = MutableStateFlow<List<ProductInfo>>(emptyList())
    val productDetails: StateFlow<List<ProductInfo>> = _productDetails.asStateFlow()

    private val _subscriptionStatus = MutableStateFlow(false)
    val subscriptionStatus: StateFlow<Boolean> = _subscriptionStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    init {
        // Initialize billing providers 
        initializeBillingProviders()
    }

    /**
     * Pre-initialize the billing manager without blocking
     * This helps speed up the eventual user experience
     */
    private fun preInitialize() {
        if (initializationStarted) return
        initializationStarted = true

        // Run this on a background thread to avoid blocking the UI
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Timber.d("Pre-initializing billing providers")

                // First, determine if we're on a Huawei device - use non-blocking method
                val isHuaweiDevice = MyApp.isHuaweiDeviceNonBlocking()

                Timber.d("Pre-init detected device as ${if (isHuaweiDevice) "Huawei" else "Google"}")

                // Initialize the appropriate provider based on quick device detection
                if (isHuaweiDevice) {
                    // Don't do HMS Core check here - that'll happen in full initialization
                    Timber.d("Pre-initializing Huawei IAP Provider")
                    if (huaweiProvider == null) {
                        huaweiProvider = HuaweiIapProvider(context)
                    }
                } else {
                    Timber.d("Pre-initializing Google Play Billing Provider")
                    if (googleProvider == null) {
                        googleProvider = GooglePlayBillingProvider(context)
                    }
                }

                // We don't set activeProvider yet - that happens in full initialization

            } catch (e: Exception) {
                Timber.e(e, "Error in billing pre-initialization: ${e.message}")
                // Errors here are not critical since this is just optimization
            }
        }
    }

    /**
     * Process the purchase result from Activity
     */
    fun processPurchaseResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        Timber.d("Processing purchase result: requestCode=$requestCode, resultCode=$resultCode")

        // Handle HMS IAP result for Huawei
        if (activeProvider is HuaweiIapProvider && requestCode == HuaweiIapProvider.REQ_CODE_BUY) {
            Timber.d("Delegating to HuaweiIapProvider")
            val result = HuaweiIapProvider.processPurchaseResult(requestCode, data, context)
            Timber.d("HuaweiIapProvider.processPurchaseResult returned: $result")
            return result
        }

        // For Google billing, no processing needed here as it's handled through the BillingClient listener
        Timber.d("Google Play doesn't need explicit result processing")
        return false
    }

    /**
     * Refresh UI based on subscription status
     * Call this after processing a purchase result
     */
    fun refreshSubscriptionStatus() {
        // First validate the current status
        validateSubscriptionStatus()

        // Then update the StateFlow
        _subscriptionStatus.value = PrefsManager.isSubscribed(context)

        Timber.d("Subscription status refreshed: ${PrefsManager.isSubscribed(context)}")
    }

    /**
     * Initialize the appropriate billing provider based on device with improved detection
     */
    private fun initializeBillingProviders() {
        if (isInitialized) {
            Timber.d("Billing providers already initialized")
            return
        }

        try {
            // In debug builds, use mock billing provider for testing
            if (BuildConfig.DEBUG) {
                Timber.d("🧪 Debug build detected - initializing MockBillingProvider")
                mockProvider = MockBillingProvider(context)
                activeProvider = mockProvider
                setupMockLiveData()
                isInitialized = true
                return
            }

            // Use cached device detection result if available for fastest path
            val isHuaweiDevice: Boolean
            val hmsAvailable: Boolean

            // Use device detection
            isHuaweiDevice = MyApp.isHuaweiDeviceNonBlocking()
            Timber.d("Device detection: isHuaweiDevice=$isHuaweiDevice")

            // For Huawei devices, verify HMS Core availability separately
            if (isHuaweiDevice) {
                hmsAvailable = verifyHMSCoreAvailability()
                Timber.d("HMS Core availability verified: $hmsAvailable")
            } else {
                hmsAvailable = false
            }

            Timber.d("Final billing provider selection: Device=${if (isHuaweiDevice) "Huawei" else "Google"}, HMS=$hmsAvailable")

            if (isHuaweiDevice && hmsAvailable) {
                // Initialize Huawei IAP only if device is Huawei AND HMS Core is available
                Timber.d("Initializing Huawei IAP Provider")

                // Reuse pre-initialized provider if available
                if (huaweiProvider == null) {
                    huaweiProvider = HuaweiIapProvider(context)
                }
                activeProvider = huaweiProvider

                // Set up LiveData sources for Huawei
                setupHuaweiLiveData()
            } else {
                // Initialize Google Play Billing for non-Huawei devices or when HMS Core is missing
                val reason = if (!isHuaweiDevice) "non-Huawei device" else "HMS Core not available"
                Timber.d("Initializing Google Play Billing Provider ($reason)")

                // Reuse pre-initialized provider if available
                if (googleProvider == null) {
                    googleProvider = GooglePlayBillingProvider(context)
                }
                activeProvider = googleProvider

                // Set up LiveData sources for Google
                setupGoogleLiveData()
            }

            isInitialized = true

        } catch (e: Exception) {
            Timber.e(e, "Error initializing billing providers: ${e.message}")

            // If there's an error with the detected provider, use Google as fallback
            if (activeProvider == null) {
                Timber.d("Error detected, using Google billing as fallback")
                try {
                    googleProvider = GooglePlayBillingProvider(context)
                    activeProvider = googleProvider
                    setupGoogleLiveData()
                    isInitialized = true
                } catch (fallbackException: Exception) {
                    Timber.e(fallbackException, "Google fallback also failed")

                    // If Google also fails, try to create mockup data
                    Timber.d("Both providers failed, using mockup data")
                    provideMockupProducts()
                }
            }
        }
    }

    /**
     * Verify HMS Core availability for Huawei devices
     */
    private fun verifyHMSCoreAvailability(): Boolean {
        return try {
            // Check if HMS Core classes are available
            Class.forName("com.huawei.hms.iap.Iap")
            Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
            
            // Check if HMS Core service is available
            val intent = Intent("com.huawei.hms.core.aidlservice")
            intent.setPackage("com.huawei.hwid")
            val resolveInfo = context.packageManager.resolveService(intent, 0)
            
            val available = resolveInfo != null
            Timber.d("HMS Core verification: classes available, service available=$available")
            available
        } catch (e: Exception) {
            Timber.w(e, "HMS Core verification failed: ${e.message}")
            false
        }
    }

    /**
     * Set up StateFlow observers for Huawei provider
     */
    private fun setupHuaweiLiveData() {
        huaweiProvider?.let { provider ->
            CoroutineScope(Dispatchers.Main).launch {
                provider.productDetails.collect { products ->
                    _productDetails.value = products
                    Timber.d("Updated product details from Huawei: ${products.size} products")
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                provider.subscriptionStatus.collect { status ->
                    _subscriptionStatus.value = status
                    Timber.d("Updated subscription status from Huawei: $status")
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                provider.errorMessage.collect { message ->
                    _errorMessage.value = message
                    Timber.d("Received error message from Huawei: $message")
                }
            }
        }
    }

    /**
     * Set up StateFlow observers for Google provider
     */
    private fun setupGoogleLiveData() {
        googleProvider?.let { provider ->
            CoroutineScope(Dispatchers.Main).launch {
                provider.productDetails.collect { products ->
                    _productDetails.value = products
                    Timber.d("Updated product details from Google: ${products.size} products")
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                provider.subscriptionStatus.collect { status ->
                    _subscriptionStatus.value = status
                    Timber.d("Updated subscription status from Google: $status")
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                provider.errorMessage.collect { message ->
                    _errorMessage.value = message
                    Timber.d("Received error message from Google: $message")
                }
            }
        }
    }

    /**
     * Set up StateFlow observers for Mock provider
     */
    private fun setupMockLiveData() {
        mockProvider?.let { provider ->
            CoroutineScope(Dispatchers.Main).launch {
                provider.productDetails.collect { products ->
                    _productDetails.value = products
                    Timber.d("🧪 Updated product details from Mock: ${products.size} products")
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                provider.subscriptionStatus.collect { status ->
                    _subscriptionStatus.value = status
                    Timber.d("🧪 Updated subscription status from Mock: $status")
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                provider.errorMessage.collect { message ->
                    _errorMessage.value = message
                    Timber.d("🧪 Received error message from Mock: $message")
                }
            }
        }
    }

    /**
     * Connect to billing service with callback support and improved retry
     */
    fun connectToPlayBilling(callback: (Boolean) -> Unit = {}) {
        if (isConnected) {
            Timber.d("Already connected to billing service")
            callback(true)
            return
        }

        Timber.d("Connecting to billing service")

        // Ensure billing providers are initialized
        if (!isInitialized) {
            initializeBillingProviders()
        }

        // If no valid provider available, use mockup for development
        if (activeProvider == null) {
            Timber.w("No valid billing provider found, using mockup data")
            isConnected = true
            // Use mockup product details for testing
            provideMockupProducts()
            callback(true)
            return
        }

        // Initialize the provider
        activeProvider?.initialize()

        // Assume connection successful for now, provider will handle actual connection
        isConnected = true
        callback(true)

        // Double-check connection status after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (activeProvider is HuaweiIapProvider) {
                // For Huawei, query products again to ensure connection is working
                activeProvider?.queryProducts()
            }
        }, 1000) // Reduced from 2000ms to 1000ms for faster response
    }

    /**
     * Provide mockup products for testing
     */
    private fun provideMockupProducts() {
        val mockProducts = listOf(
            ProductInfo(
                productId = BillingProvider.SUBSCRIPTION_WEEKLY,
                title = "Weekly Premium",
                description = "7 days of premium features",
                price = "4.99 USD",
                rawPriceInMicros = 4990000,
                currencyCode = "USD"
            ),
            ProductInfo(
                productId = BillingProvider.SUBSCRIPTION_MONTHLY,
                title = "Monthly Premium",
                description = "30 days of premium features",
                price = "9.99 USD",
                rawPriceInMicros = 9990000,
                currencyCode = "USD"
            )
        )

        // Update product details
        Handler(Looper.getMainLooper()).postDelayed({
            _productDetails.value = mockProducts
        }, 500) // Reduced delay for faster response
    }

    /**
     * Query available subscription products
     */
    fun queryProducts() {
        if (!isConnected) {
            connectToPlayBilling { success ->
                if (success) {
                    activeProvider?.queryProducts()
                }
            }
            return
        }

        activeProvider?.queryProducts()
    }

    /**
     * Launch subscription purchase flow with improved error handling and connectivity
     */
    fun launchBillingFlow(activity: Activity, productId: String) {
        Timber.d("Attempting to launch billing flow for $productId")

        // Check activity state
        if (activity.isFinishing || activity.isDestroyed) {
            _errorMessage.value = "Cannot start purchase - activity is no longer valid"
            return
        }

        // Check if we need to reconnect first
        if (!isConnected) {
            _errorMessage.value = "Connecting to billing service..."
            connectToPlayBilling { success ->
                if (success) {
                    // If we just connected successfully, delay slightly before continuing
                    Handler(Looper.getMainLooper()).postDelayed({
                        launchBillingFlow(activity, productId)
                    }, 500) // Reduced from 1000ms to 500ms
                } else {
                    _errorMessage.value = "Could not connect to billing service. Please try again later."
                }
            }
            return
        }

        // Extra logging before launching billing flow
        Timber.d("Preparing to launch billing flow")
        Timber.d("Active provider: ${activeProvider?.javaClass?.simpleName}")
        Timber.d("Product ID: $productId")

        try {
            activeProvider?.launchBillingFlow(activity, productId)
            Timber.d("Billing flow launched successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error launching billing flow: ${e.message}")
            _errorMessage.value = "Error launching purchase: ${e.message}"

            // Try Google as fallback if Huawei fails
            if (activeProvider is HuaweiIapProvider) {
                Timber.d("Huawei billing failed, trying Google as fallback")
                try {
                    // Initialize Google if needed
                    if (googleProvider == null) {
                        googleProvider = GooglePlayBillingProvider(context)
                    }
                    // Use Google directly this time
                    googleProvider?.launchBillingFlow(activity, productId)
                } catch (fallbackError: Exception) {
                    Timber.e(fallbackError, "Google fallback also failed")
                    _errorMessage.value = "Could not process purchase. Please try again later."
                }
            }
        }
    }

    /**
     * Check and restore existing subscriptions on app startup
     * Enhanced version that properly handles app reinstalls
     */
    fun restoreSubscriptions() {
        Timber.d("🔄 Attempting to restore subscriptions (enhanced version)")

        // Ensure providers are initialized
        if (!isInitialized) {
            initializeBillingProviders()
        }

        if (!isConnected) {
            connectToPlayBilling { success ->
                if (success) {
                    performEnhancedSubscriptionRestore()
                } else {
                    Timber.e("❌ Failed to connect for subscription restoration")
                }
            }
            return
        }

        performEnhancedSubscriptionRestore()
    }
    
    /**
     * Enhanced subscription restoration that checks multiple sources
     */
    private fun performEnhancedSubscriptionRestore() {
        Timber.d("🔄 Performing enhanced subscription restoration")
        
        try {
            // Step 1: Check current local status
            val wasLocallySubscribed = PrefsManager.isSubscribed(context)
            Timber.d("📱 Local subscription status before restore: $wasLocallySubscribed")
            
            // Step 2: Force check with billing provider
            activeProvider?.checkSubscriptionStatus()
            
            // Step 3: Wait a moment for provider to update status
            Handler(Looper.getMainLooper()).postDelayed({
                val isNowSubscribed = PrefsManager.isSubscribed(context)
                val subscriptionType = PrefsManager.getSubscriptionType(context)
                
                when {
                    !wasLocallySubscribed && isNowSubscribed -> {
                        Timber.d("✅ Subscription restored from ${activeProvider?.javaClass?.simpleName}")
                        Timber.d("🎉 Restored subscription type: $subscriptionType")
                        
                        // Notify user state manager about restored subscription
                        notifySubscriptionRestored(subscriptionType)
                    }
                    wasLocallySubscribed && isNowSubscribed -> {
                        Timber.d("✅ Subscription was already active locally")
                    }
                    wasLocallySubscribed && !isNowSubscribed -> {
                        Timber.w("⚠️ Local subscription was revoked by billing provider")
                    }
                    else -> {
                        Timber.d("ℹ️ No active subscription found")
                    }
                }
                
                // Final validation
                validateRestoredSubscription()
                
            }, 2000) // Give billing provider time to process
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in enhanced subscription restoration")
        }
    }
    
    /**
     * Validate that restored subscription is legitimate
     */
    private fun validateRestoredSubscription() {
        val isSubscribed = PrefsManager.isSubscribed(context)
        val endTime = PrefsManager.getSubscriptionEndTime(context)
        val subscriptionType = PrefsManager.getSubscriptionType(context)
        
        if (isSubscribed) {
            val isValidEndTime = endTime == 0L || endTime > System.currentTimeMillis()
            val hasValidType = !subscriptionType.isNullOrEmpty()
            
            if (!isValidEndTime || !hasValidType) {
                Timber.w("⚠️ Restored subscription appears invalid, rechecking...")
                // Force another check
                Handler(Looper.getMainLooper()).postDelayed({
                    activeProvider?.checkSubscriptionStatus()
                }, 1000)
            } else {
                Timber.d("✅ Restored subscription validated successfully")
                Timber.d("📊 Subscription expires: ${if (endTime > 0) java.util.Date(endTime) else "Never"}")
            }
        }
    }
    
    /**
     * Notify about subscription restoration (for server sync)
     */
    private fun notifySubscriptionRestored(subscriptionType: String?) {
        try {
            // This would typically notify a server about the subscription restoration
            // For now, we'll just log it
            Timber.d("🔔 Subscription restoration notification sent")
            Timber.d("📋 Restored subscription details:")
            Timber.d("   - Type: $subscriptionType")
            Timber.d("   - End Time: ${PrefsManager.getSubscriptionEndTime(context)}")
            Timber.d("   - Provider: ${activeProvider?.javaClass?.simpleName}")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error sending subscription restoration notification")
        }
    }

    /**
     * Validate subscription status against local data
     */
    fun validateSubscriptionStatus() {
        val isCurrentlySubscribed = PrefsManager.isSubscribed(context)
        val subscriptionEndTime = PrefsManager.getSubscriptionEndTime(context)

        // Check if subscription has expired
        if (isCurrentlySubscribed && subscriptionEndTime > 0 &&
            subscriptionEndTime < System.currentTimeMillis()) {

            // Subscription has expired, update status
            PrefsManager.setSubscribed(context, false, 0)
            Timber.d("Expired subscription detected and reset")
        }
    }

    /**
     * Release resources
     */
    fun release() {
        googleProvider?.release()
        huaweiProvider?.release()
        mockProvider?.release()

        // Clear instance
        instance = null
        isInitialized = false
        isConnected = false
        initializationStarted = false
    }
}