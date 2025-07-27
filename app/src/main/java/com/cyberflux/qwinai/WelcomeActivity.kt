package com.cyberflux.qwinai

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.billing.BillingProvider
import com.cyberflux.qwinai.billing.HuaweiIapProvider
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.UiUtils
import timber.log.Timber
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class WelcomeActivity : BaseThemedActivity() {

    // UI elements
    private lateinit var btnClose: ImageButton
    private lateinit var btnWeekly: Button
    private lateinit var btnMonthly: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var tvWeeklyPrice: TextView
    private lateinit var tvMonthlyPrice: TextView
    private lateinit var weeklyCard: CardView
    private lateinit var monthlyCard: CardView
    private lateinit var tvMonthlyDesc: TextView

    // Feature Icons
    private lateinit var unlimitedIcon: ImageView
    private lateinit var noAdsIcon: ImageView
    private lateinit var fileUploadIcon: ImageView

    // Billing
    private var billingManager: BillingManager? = null
    private var isQueryingProducts = false
    private var billingInitialized = false
    private var billingRetryCount = 0
    private val maxRetryCount = 3

    // Navigation flags
    private var isNavigatingAway = false

    // Default prices in RON (Romanian currency)
    private val defaultWeeklyPriceRON = 36.58
    private val defaultMonthlyPriceRON = 88.76

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_welcome)

        Timber.d("WelcomeActivity created")

        // Initialize views
        initializeViews()

        // Set up initial UI state
        setupInitialState()

        // Set up click listeners
        setupClickListeners()

        // Add debug info overlay (only in debug builds)
        addDebugOverlay()

        // Check for existing subscription
        if (checkExistingSubscription()) {
            // If subscription is active, don't initialize billing
            return
        }

        // IMPORTANT: Get the pre-initialized billing manager from MyApp
        // This is faster because it's already been created during app startup
        billingManager = MyApp.getBillingManager()

        // Process billing setup right away - no delay needed anymore
        initializeBilling()
    }

    private fun initializeViews() {
        // Main UI elements
        btnClose = findViewById(R.id.btnClose)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        statusText = findViewById(R.id.statusText)

        // Subscription cards and buttons
        weeklyCard = findViewById(R.id.weeklyCard)
        monthlyCard = findViewById(R.id.monthlyCard)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnMonthly = findViewById(R.id.btnMonthly)
        tvWeeklyPrice = findViewById(R.id.tvWeeklyPrice)
        tvMonthlyPrice = findViewById(R.id.tvMonthlyPrice)
        tvMonthlyDesc = findViewById(R.id.tvMonthlyDesc)

        // Initialize feature icons
        unlimitedIcon = findViewById(R.id.unlimitedIcon)
        noAdsIcon = findViewById(R.id.noAdsIcon)
        fileUploadIcon = findViewById(R.id.fileUploadIcon)

        // CRITICAL: Make sure buttons are enabled
        btnWeekly.isEnabled = true
        btnMonthly.isEnabled = true
    }

    @SuppressLint("SetTextI18n")
    private fun setupInitialState() {
        // Hide loading indicator initially
        loadingIndicator.visibility = View.GONE

        // Set default prices with local currency format
        updatePriceDisplay(defaultWeeklyPriceRON, defaultMonthlyPriceRON)

        // CRITICAL: Enable subscription buttons right away
        enableSubscriptionButtons()

        // Set initial status text
        statusText.text = "Choose your subscription plan"
    }

    /**
     * Add a debug info overlay to help troubleshoot billing issues
     */
    @SuppressLint("SetTextI18s")
    private fun addDebugOverlay() {
        if (BuildConfig.DEBUG) {
            // Create a TextView for debugging information
            val debugText = TextView(this)

            // Add more detailed information about the device and billing
            val isHuaweiDevice = MyApp.isHuaweiDeviceNonBlocking()

            debugText.text = "Device: ${if (isHuaweiDevice) "Huawei" else "Google"}\n" +
                    "Manufacturer: ${Build.MANUFACTURER}\n" +
                    "Model: ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n" +
                    "Billing: ${if (billingManager != null) "Pre-initialized" else "Initializing..."}\n" +
                    "HMS Core Available: ${isHuaweiDevice}"

            debugText.setBackgroundColor(Color.parseColor("#88000000"))
            debugText.setTextColor(Color.WHITE)
            debugText.setPadding(20, 20, 20, 20)
            debugText.textSize = 12f

            // Position at top-right
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            (window.decorView as ViewGroup).addView(debugText, params)

            // Update billing provider info after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                val bm = billingManager ?: MyApp.getBillingManager()
                val providerType = when (bm.activeProvider) {
                    is HuaweiIapProvider -> "HMS IAP"
                    null -> "None"
                    else -> "Google Play"
                }

                debugText.text = "Device: ${if (isHuaweiDevice) "Huawei" else "Google"}\n" +
                        "Manufacturer: ${Build.MANUFACTURER}\n" +
                        "Model: ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n" +
                        "Billing Provider: $providerType\n" +
                        "HMS Core Available: ${isHuaweiDevice}\n" +
                        "Weekly ID: ${BillingProvider.SUBSCRIPTION_WEEKLY}\n" +
                        "Monthly ID: ${BillingProvider.SUBSCRIPTION_MONTHLY}\n" +
                        "Products Loaded: ${bm.productDetails.value?.size ?: 0}"
            }, 1000) // Just 1 second is enough now
        }
    }

    /**
     * Checks if user has an active subscription and handles UI accordingly
     * @return true if user has a subscription, false otherwise
     */
    @SuppressLint("SetTextI18s")
    private fun checkExistingSubscription(): Boolean {
        if (PrefsManager.isSubscribed(this)) {
            statusText.text = "You already have a premium subscription!"
            disableSubscriptionButtons()

            // Only navigate if not already navigating
            if (!isNavigatingAway) {
                isNavigatingAway = true
                // Navigate back to main after short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    navigateToMainActivity()
                }, 300) // Even shorter delay for better UX
            }
            return true
        }
        return false
    }

    private fun setupClickListeners() {
        // Close button - immediately return to main activity
        btnClose.setOnClickListener {
            Timber.d("Close button clicked")
            if (!isNavigatingAway) {
                isNavigatingAway = true
                navigateToMainActivity()
            }
        }

        // CRITICAL: Replace with direct implementation
        btnWeekly.setOnClickListener {
            // Directly get billing manager and launch flow
            Timber.d("Weekly button clicked directly")
            btnWeekly.isEnabled = false  // Prevent double clicks

            try {
                Toast.makeText(this, "Processing weekly subscription...", Toast.LENGTH_SHORT).show()
                showLoading("Processing purchase...")

                // Visual feedback
                weeklyCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
                Handler(Looper.getMainLooper()).postDelayed({
                    weeklyCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                }, 300)

                // Get billing manager (already initialized) and launch flow directly
                val bm = billingManager ?: MyApp.getBillingManager()
                Timber.d("Direct weekly purchase - BM: $bm, Provider: ${bm.activeProvider?.javaClass?.simpleName}")
                bm.launchBillingFlow(this, BillingProvider.SUBSCRIPTION_WEEKLY)

                // Check subscription status after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    // Refresh subscription status
                    bm.refreshSubscriptionStatus()

                    // Check if we're subscribed now
                    if (PrefsManager.isSubscribed(this)) {
                        hideLoading()
                        statusText.text = "Premium subscription activated!"
                        navigateToMainActivity()
                    } else {
                        hideLoading()
                        statusText.text = "Choose your subscription plan"
                        btnWeekly.isEnabled = true
                    }
                }, 5000)
            } catch (e: Exception) {
                Timber.e(e, "Error in weekly purchase: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                hideLoading()
                btnWeekly.isEnabled = true
            }
        }

        btnMonthly.setOnClickListener {
            // Directly get billing manager and launch flow
            Timber.d("Monthly button clicked directly")
            btnMonthly.isEnabled = false  // Prevent double clicks

            try {
                Toast.makeText(this, "Processing monthly subscription...", Toast.LENGTH_SHORT).show()
                showLoading("Processing purchase...")

                // Visual feedback
                monthlyCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
                Handler(Looper.getMainLooper()).postDelayed({
                    monthlyCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                }, 300)

                // Get billing manager (already initialized) and launch flow directly
                val bm = billingManager ?: MyApp.getBillingManager()
                Timber.d("Direct monthly purchase - BM: $bm, Provider: ${bm.activeProvider?.javaClass?.simpleName}")
                bm.launchBillingFlow(this, BillingProvider.SUBSCRIPTION_MONTHLY)

                // Check subscription status after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    // Refresh subscription status
                    bm.refreshSubscriptionStatus()

                    // Check if we're subscribed now
                    if (PrefsManager.isSubscribed(this)) {
                        hideLoading()
                        statusText.text = "Premium subscription activated!"
                        navigateToMainActivity()
                    } else {
                        hideLoading()
                        statusText.text = "Choose your subscription plan"
                        btnMonthly.isEnabled = true
                    }
                }, 5000)
            } catch (e: Exception) {
                Timber.e(e, "Error in monthly purchase: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                hideLoading()
                btnMonthly.isEnabled = true
            }
        }
    }

    private fun disableSubscriptionButtons() {
        btnWeekly.isEnabled = false
        btnMonthly.isEnabled = false
    }

    private fun enableSubscriptionButtons() {
        btnWeekly.isEnabled = true
        btnMonthly.isEnabled = true
    }

    /**
     * Initialize billing with improved logging and error handling
     * This is now much faster because it uses the pre-initialized billing manager
     */
    private fun initializeBilling() {
        try {
            // Show loading indicator
            showLoading("Connecting to billing service...")

            // Get the billing manager - it should already be initialized during app startup
            if (billingManager == null) {
                billingManager = MyApp.getBillingManager()
            }

            val bm = billingManager ?: return

            Timber.d("Using pre-initialized billing manager, active provider: ${bm.activeProvider?.javaClass?.simpleName}")

            // Always enable buttons regardless of billing status
            enableSubscriptionButtons()

            // We should already be connected, but connect just to be sure
            bm.connectToPlayBilling { success ->
                if (success) {
                    billingInitialized = true
                    Timber.d("Billing service connected successfully")

                    // Enable subscription buttons once billing is initialized
                    runOnUiThread {
                        enableSubscriptionButtons()
                    }

                    // Set up observers for billing events
                    setupBillingObservers()

                    // Query available products - might already be loaded
                    if (bm.productDetails.value.isNullOrEmpty() && !isQueryingProducts) {
                        isQueryingProducts = true
                        Timber.d("Querying subscription products")
                        bm.queryProducts()
                    } else if (!bm.productDetails.value.isNullOrEmpty()) {
                        // Products already loaded - update UI immediately
                        Timber.d("Products already loaded, updating UI")
                        bm.productDetails.value?.let { products ->
                            processProductDetails(products)
                        }
                    }
                } else {
                    // Retry billing connection if failed
                    handleBillingConnectionFailure()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing billing")
            handleBillingError("Billing initialization error: ${e.message}")
        }
    }

    private fun processProductDetails(products: List<com.cyberflux.qwinai.billing.ProductInfo>) {
        if (products.isNotEmpty()) {
            Timber.d("Processing ${products.size} products")

            // Find weekly and monthly products
            val weeklyProduct = products.find { it.productId == BillingProvider.SUBSCRIPTION_WEEKLY }
            val monthlyProduct = products.find { it.productId == BillingProvider.SUBSCRIPTION_MONTHLY }

            if (weeklyProduct != null && monthlyProduct != null) {
                // Update UI with product details
                updatePriceDisplay(
                    weeklyPrice = weeklyProduct.rawPriceInMicros / 1_000_000.0,
                    monthlyPrice = monthlyProduct.rawPriceInMicros / 1_000_000.0,
                    weeklyCurrencyCode = weeklyProduct.currencyCode,
                    monthlyCurrencyCode = monthlyProduct.currencyCode
                )

                // Update status and hide loading
                statusText.text = "Choose your subscription plan"
                hideLoading()
            } else {
                Timber.w("Product details incomplete - Weekly: $weeklyProduct, Monthly: $monthlyProduct")
                statusText.text = "Choose your subscription plan"
                hideLoading()
            }

            // Always enable buttons
            enableSubscriptionButtons()
        }
    }

    @SuppressLint("SetTextI18s")
    private fun handleBillingConnectionFailure() {
        if (billingRetryCount < maxRetryCount) {
            billingRetryCount++
            Timber.d("Billing connection failed, retrying ($billingRetryCount/$maxRetryCount)")

            // Update status
            runOnUiThread {
                statusText.text = "Retrying connection ($billingRetryCount/$maxRetryCount)..."
            }

            // Retry with delay
            Handler(Looper.getMainLooper()).postDelayed({
                initializeBilling()
            }, 1000) // Reduced from 2000ms to 1000ms
        } else {
            // Max retries reached, show error
            handleBillingError("Couldn't connect to billing service. Please try again later.")
        }
    }

    @SuppressLint("SetTextI18s")
    private fun handleBillingError(message: String) {
        Timber.e("Billing error: $message")

        runOnUiThread {
            hideLoading()

            // Show a user-friendly message
            statusText.text = "Payment service unavailable. Please try again later."

            // Only show toast for generic errors, not specific error codes
            if (!message.contains("907135003") && !message.contains("Connection Failed")) {
                Toast.makeText(this, "Payment service temporarily unavailable", Toast.LENGTH_SHORT).show()
            }

            // Still enable buttons so users can try anyway
            enableSubscriptionButtons()
        }
    }

    private fun setupBillingObservers() {
        val bm = billingManager ?: MyApp.getBillingManager()

        // Using mediator observers from BillingManager class

        // Product details observer
        bm.productDetails.observe(this) { products ->
            if (products.isNotEmpty() && isQueryingProducts) {
                isQueryingProducts = false
                Timber.d("Received product details: ${products.size} products")
                processProductDetails(products)
            }
        }

        // Error message observer
        bm.errorMessage.observe(this) { errorMsg ->
            hideLoading()

            Timber.e("Billing error received: $errorMsg")

            // Show appropriate error message
            if (errorMsg.contains("Purchase canceled")) {
                statusText.text = "Purchase canceled"
            } else if (errorMsg.contains("Failed to retrieve") ||
                errorMsg.contains("No subscription products found") ||
                errorMsg.contains("907135003") ||
                errorMsg.contains("Connection Failed")) {
                statusText.text = "Payment service unavailable"
            } else {
                statusText.text = "Choose your subscription plan"
            }

            // Re-enable buttons after error
            enableSubscriptionButtons()
        }

        // Check for active subscription
        Handler(Looper.getMainLooper()).postDelayed({
            if (PrefsManager.isSubscribed(this)) {
                hideLoading()
                statusText.text = "Premium subscription activated!"

                // Return to main screen
                if (!isNavigatingAway) {
                    isNavigatingAway = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        navigateToMainActivity()
                    }, 300) // Reduced from 1500ms to 300ms for faster response
                }
            }
        }, 500) // Reduced from 2000ms to 500ms for faster response
    }

    @SuppressLint("SetTextI18s")
    private fun updatePriceDisplay(weeklyPrice: Double, monthlyPrice: Double,
                                   weeklyCurrencyCode: String = "RON",
                                   monthlyCurrencyCode: String = "RON") {
        try {
            // Format weekly price with currency
            val weeklyFormatter = NumberFormat.getCurrencyInstance(getCurrentLocale())
            weeklyFormatter.currency = Currency.getInstance(weeklyCurrencyCode)
            val formattedWeeklyPrice = weeklyFormatter.format(weeklyPrice)
            tvWeeklyPrice.text = "$formattedWeeklyPrice/week"

            // Format monthly price with currency
            val monthlyFormatter = NumberFormat.getCurrencyInstance(getCurrentLocale())
            monthlyFormatter.currency = Currency.getInstance(monthlyCurrencyCode)
            val formattedMonthlyPrice = monthlyFormatter.format(monthlyPrice)
            tvMonthlyPrice.text = "$formattedMonthlyPrice/month"

            // Calculate savings compared to weekly
            val weeklyMonthCost = weeklyPrice * 4.3 // Approximate weeks in a month
            val savingsPercent = ((weeklyMonthCost - monthlyPrice) / weeklyMonthCost * 100).toInt()

            // Update description text to include the calculated savings
            tvMonthlyDesc.text = "30-day access with ${savingsPercent}% savings"

            // Enable buttons now that prices are loaded
            enableSubscriptionButtons()

            // Update status text
            statusText.text = "Choose your subscription plan"
        } catch (e: Exception) {
            // Fallback if formatting fails
            Timber.e(e, "Error formatting prices")
            tvWeeklyPrice.text = "$weeklyPrice $weeklyCurrencyCode/week"
            tvMonthlyPrice.text = "$monthlyPrice $monthlyCurrencyCode/month"
        }
    }

    private fun getCurrentLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
    }

    // Show loading indicator with status message
    private fun showLoading(message: String) {
        loadingIndicator.visibility = View.VISIBLE
        statusText.text = message
    }

    // Hide loading indicator
    private fun hideLoading() {
        loadingIndicator.visibility = View.GONE
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        // Use FLAG_ACTIVITY_CLEAR_TOP to prevent multiple instances
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        Timber.d("WelcomeActivity resumed")

        // Reset navigation flag when coming back to this screen
        isNavigatingAway = false

        // Check if subscription status changed while away
        if (checkExistingSubscription()) {
            // Don't continue if we're navigating away due to active subscription
            return
        }

        // Always ensure buttons are enabled
        runOnUiThread {
            enableSubscriptionButtons()

            // If loading has been showing too long, hide it
            if (loadingIndicator.visibility == View.VISIBLE) {
                hideLoading()
                statusText.text = "Choose your subscription plan"
            }
        }
    }

    @Deprecated("This method has been deprecated in favor of using Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Timber.d("Activity result received: requestCode=$requestCode, resultCode=$resultCode")

        // First, check if this is a Huawei IAP result
        if (requestCode == HuaweiIapProvider.REQ_CODE_BUY) {
            // Process Huawei IAP result
            Timber.d("Processing Huawei IAP result")

            val bm = billingManager ?: MyApp.getBillingManager()
            val processed = bm.processPurchaseResult(requestCode, resultCode, data)

            if (processed) {
                Timber.d("Huawei purchase processed successfully")
                // Force refresh subscription status
                bm.refreshSubscriptionStatus()

                // Update UI based on subscription status
                updateUIBasedOnSubscriptionStatus()
                return
            } else {
                Timber.d("Huawei purchase processing returned false")
            }
        }

        // Handle update flow result
        try {
            val updateManager = MyApp.getUpdateManager()
            if (updateManager != null && requestCode == 500) { // 500 is UPDATE_REQUEST_CODE
                updateManager.handleUpdateResult(requestCode, resultCode)
                return
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling update result: ${e.message}")
        }

        // If not handled above, pass to super
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateUIBasedOnSubscriptionStatus() {
        if (PrefsManager.isSubscribed(this)) {
            hideLoading()
            statusText.text = "Premium subscription activated!"

            // Return to main screen
            if (!isNavigatingAway) {
                isNavigatingAway = true
                Handler(Looper.getMainLooper()).postDelayed({
                    navigateToMainActivity()
                }, 300) // Reduced for faster response
            }
        } else {
            hideLoading()
            statusText.text = "Choose your subscription plan"
            enableSubscriptionButtons()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Don't release billing manager since it's now managed at app level
        // Just clear our reference to it
        billingManager = null
    }
}