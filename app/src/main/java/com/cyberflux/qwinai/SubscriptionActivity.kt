package com.cyberflux.qwinai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.billing.MockBillingProvider
import com.cyberflux.qwinai.billing.ProductInfo
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.HapticManager
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date

class SubscriptionActivity : BaseThemedActivity() {

    private lateinit var billingManager: BillingManager
    
    // UI Components
    private lateinit var btnClose: ImageView
    private lateinit var currentStatusCard: CardView
    private lateinit var statusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var btnManageSubscription: TextView
    private lateinit var featuresContainer: LinearLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var plansContainer: LinearLayout
    private lateinit var errorCard: CardView
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var btnRetry: AppCompatButton
    private lateinit var successCard: CardView
    private lateinit var btnContinue: AppCompatButton
    private lateinit var btnTerms: TextView
    private lateinit var btnPrivacy: TextView
    private lateinit var btnRestorePurchases: TextView
    
    // Debug UI (only in debug builds)
    private var debugContainer: LinearLayout? = null
    
    // State
    private var isInitialized = false
    private var availableProducts: List<ProductInfo> = emptyList()
    private var isProcessingPurchase = false
    
    companion object {
        private const val TERMS_URL = "https://your-app-terms.com"
        private const val PRIVACY_URL = "https://your-app-privacy.com"
        
        fun start(context: Context) {
            val intent = Intent(context, SubscriptionActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)
        
        initializeViews()
        setupBillingManager()
        setupClickListeners()
        setupFeaturesList()
        setupDebugMenu()
        
        // Haptic feedback is handled per-interaction
    }

    private fun initializeViews() {
        btnClose = findViewById(R.id.btnClose)
        currentStatusCard = findViewById(R.id.currentStatusCard)
        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusSubtitle = findViewById(R.id.statusSubtitle)
        btnManageSubscription = findViewById(R.id.btnManageSubscription)
        featuresContainer = findViewById(R.id.featuresContainer)
        loadingProgress = findViewById(R.id.loadingProgress)
        plansContainer = findViewById(R.id.plansContainer)
        errorCard = findViewById(R.id.errorCard)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        btnRetry = findViewById(R.id.btnRetry)
        successCard = findViewById(R.id.successCard)
        btnContinue = findViewById(R.id.btnContinue)
        btnTerms = findViewById(R.id.btnTerms)
        btnPrivacy = findViewById(R.id.btnPrivacy)
        btnRestorePurchases = findViewById(R.id.btnRestorePurchases)
    }

    private fun setupBillingManager() {
        billingManager = BillingManager.getInstance(this)
        
        // Observe subscription status
        lifecycleScope.launch {
            billingManager.subscriptionStatus.collectLatest { isSubscribed ->
                updateSubscriptionStatus(isSubscribed)
            }
        }
        
        // Observe product details
        lifecycleScope.launch {
            billingManager.productDetails.collectLatest { products ->
                availableProducts = products
                updateProductsUI(products)
            }
        }
        
        // Observe error messages
        lifecycleScope.launch {
            billingManager.errorMessage.collectLatest { error ->
                if (error.isNotEmpty()) {
                    handleBillingError(error)
                }
            }
        }
        
        // Connect to billing and query products
        connectToBilling()
    }

    private fun connectToBilling() {
        showLoading()
        
        billingManager.connectToPlayBilling { success ->
            if (success) {
                billingManager.queryProducts()
                billingManager.restoreSubscriptions()
                isInitialized = true
            } else {
                showError(
                    "Connection Failed",
                    "Unable to connect to the billing service. Please check your internet connection and try again."
                )
            }
        }
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            HapticManager.lightVibration(this)
            finish()
        }
        
        btnManageSubscription.setOnClickListener {
            HapticManager.lightVibration(this)
            openSubscriptionManagement()
        }
        
        btnRetry.setOnClickListener {
            HapticManager.lightVibration(this)
            connectToBilling()
        }
        
        btnContinue.setOnClickListener {
            HapticManager.lightVibration(this)
            finishWithSuccess()
        }
        
        btnTerms.setOnClickListener {
            HapticManager.lightVibration(this)
            openUrl(TERMS_URL)
        }
        
        btnPrivacy.setOnClickListener {
            HapticManager.lightVibration(this)
            openUrl(PRIVACY_URL)
        }
        
        btnRestorePurchases.setOnClickListener {
            HapticManager.lightVibration(this)
            restorePurchases()
        }
    }

    private fun setupFeaturesList() {
        val features = listOf(
            FeatureItem("Unlimited AI Conversations", "No daily limits or restrictions", R.drawable.ic_auto_awesome),
            FeatureItem("Remove All Ads", "Enjoy a clean, distraction-free experience", R.drawable.ic_clear),
            FeatureItem("Advanced AI Models", "Access to latest and most powerful AI models", R.drawable.ic_lightbulb),
            FeatureItem("Priority Support", "Get faster response times and dedicated help", R.drawable.ic_person),
            FeatureItem("Early Access", "Be the first to try new features and updates", R.drawable.ic_refresh),
            FeatureItem("File Processing", "Upload and analyze documents, images, and more", R.drawable.ic_attachment),
            FeatureItem("Voice Chat", "Natural voice conversations with AI", R.drawable.ic_reply),
            FeatureItem("Image Generation", "Create stunning AI-generated images", R.drawable.ic_image_generation)
        )
        
        features.forEach { feature ->
            addFeatureToList(feature)
        }
    }

    private fun addFeatureToList(feature: FeatureItem) {
        val featureView = layoutInflater.inflate(R.layout.item_premium_feature, featuresContainer, false)
        
        val icon = featureView.findViewById<ImageView>(R.id.featureIcon)
        val title = featureView.findViewById<TextView>(R.id.featureTitle)
        val description = featureView.findViewById<TextView>(R.id.featureDescription)
        val checkmark = featureView.findViewById<ImageView>(R.id.featureCheckmark)
        
        icon.setImageResource(feature.iconRes)
        title.text = feature.title
        description.text = feature.description
        
        // Apply theme colors
        val primaryColor = ContextCompat.getColor(this, ThemeManager.getAccentColorResource(this))
        checkmark.setColorFilter(primaryColor)
        icon.setColorFilter(primaryColor)
        
        featuresContainer.addView(featureView)
    }

    private fun updateSubscriptionStatus(isSubscribed: Boolean) {
        if (isSubscribed) {
            showCurrentSubscriptionStatus()
        } else {
            hideCurrentSubscriptionStatus()
        }
    }

    private fun showCurrentSubscriptionStatus() {
        currentStatusCard.visibility = View.VISIBLE
        
        statusIcon.setImageResource(R.drawable.ic_checkmark_green)
        statusTitle.text = getString(R.string.premium_active)
        
        val endTime = PrefsManager.getSubscriptionEndTime(this)
        statusSubtitle.text = if (endTime > 0) {
            val remainingDays = (endTime - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
            if (remainingDays > 0) {
                getString(R.string.expires_in_days, remainingDays.toInt())
            } else {
                getString(R.string.expires_soon)
            }
        } else {
            getString(R.string.expires_never)
        }
    }

    private fun hideCurrentSubscriptionStatus() {
        currentStatusCard.visibility = View.GONE
    }

    private fun updateProductsUI(products: List<ProductInfo>) {
        runOnUiThread {
            hideLoading()
            hideError()
            
            if (products.isEmpty()) {
                showError(
                    "No Plans Available",
                    "Unable to load subscription plans. Please try again later."
                )
                return@runOnUiThread
            }
            
            showPlans(products)
        }
    }

    private fun showPlans(products: List<ProductInfo>) {
        plansContainer.removeAllViews()
        plansContainer.visibility = View.VISIBLE
        
        // Sort products by price (weekly first, then monthly)
        val sortedProducts = products.sortedBy { product ->
            when (product.productId) {
                "qwinai_weekly_subscription" -> 0
                "qwinai_monthly_subscription" -> 1
                else -> 2
            }
        }
        
        sortedProducts.forEach { product ->
            addPlanCard(product)
        }
    }

    private fun addPlanCard(product: ProductInfo) {
        val planCard = createPlanCard(product)
        plansContainer.addView(planCard)
    }

    private fun createPlanCard(product: ProductInfo): CardView {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            radius = 16.dpToPx().toFloat()
            cardElevation = 6.dpToPx().toFloat()
            useCompatPadding = true
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
            setBackgroundResource(
                if (product.productId.contains("monthly")) R.drawable.premium_platinum_card
                else R.drawable.premium_gold_accent_card
            )
        }
        
        // Popular badge for monthly
        if (product.productId.contains("monthly")) {
            val badge = TextView(this).apply {
                text = "MOST POPULAR"
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.badge_background)
                setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            val badgeContainer = LinearLayout(this).apply {
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 16.dpToPx()
                }
            }
            badgeContainer.addView(badge)
            container.addView(badgeContainer)
        }
        
        // Plan title and period
        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val planTitle = TextView(this).apply {
            text = when {
                product.productId.contains("weekly") -> "Weekly Premium"
                product.productId.contains("monthly") -> "Monthly Premium"
                else -> product.title
            }
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@SubscriptionActivity, R.color.premium_text))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val priceText = TextView(this).apply {
            text = product.price
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@SubscriptionActivity, R.color.premium_gold))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.END
        }
        
        titleContainer.addView(planTitle)
        titleContainer.addView(priceText)
        container.addView(titleContainer)
        
        // Billing period
        val billingPeriod = TextView(this).apply {
            text = when {
                product.productId.contains("weekly") -> "Billed weekly, cancel anytime"
                product.productId.contains("monthly") -> "Billed monthly, cancel anytime"
                else -> "Subscription plan"
            }
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SubscriptionActivity, R.color.premium_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx()
                bottomMargin = 20.dpToPx()
            }
        }
        container.addView(billingPeriod)
        
        // Subscribe button
        val subscribeButton = AppCompatButton(this).apply {
            text = "Choose ${if (product.productId.contains("weekly")) "Weekly" else "Monthly"}"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.modern_button_background)
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                HapticManager.mediumVibration(this@SubscriptionActivity)
                purchaseSubscription(product)
            }
        }
        container.addView(subscribeButton)
        
        cardView.addView(container)
        return cardView
    }

    private fun purchaseSubscription(product: ProductInfo) {
        if (isProcessingPurchase) {
            Toast.makeText(this, "Purchase in progress...", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isInitialized) {
            Toast.makeText(this, "Billing service not ready. Please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        
        isProcessingPurchase = true
        showPurchaseProgress()
        
        try {
            billingManager.launchBillingFlow(this, product.productId)
            
            // Reset processing flag after a delay to prevent stuck state
            Handler(Looper.getMainLooper()).postDelayed({
                isProcessingPurchase = false
                hidePurchaseProgress()
            }, 30000) // 30 seconds timeout
            
        } catch (e: Exception) {
            Timber.e(e, "Error launching billing flow")
            isProcessingPurchase = false
            hidePurchaseProgress()
            Toast.makeText(this, "Unable to start purchase: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPurchaseProgress() {
        runOnUiThread {
            plansContainer.alpha = 0.5f
            loadingProgress.visibility = View.VISIBLE
        }
    }

    private fun hidePurchaseProgress() {
        runOnUiThread {
            plansContainer.alpha = 1.0f
            loadingProgress.visibility = View.GONE
        }
    }

    private fun restorePurchases() {
        showLoading()
        billingManager.restoreSubscriptions()
        
        Handler(Looper.getMainLooper()).postDelayed({
            hideLoading()
            
            if (PrefsManager.isSubscribed(this)) {
                showSuccess()
            } else {
                Toast.makeText(this, "No active subscriptions found to restore", Toast.LENGTH_LONG).show()
            }
        }, 3000)
    }

    private fun handleBillingError(error: String) {
        runOnUiThread {
            isProcessingPurchase = false
            hidePurchaseProgress()
            
            when {
                error.contains("canceled", ignoreCase = true) -> {
                    Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show()
                }
                error.contains("unavailable", ignoreCase = true) -> {
                    showError(
                        "Service Unavailable",
                        "The billing service is temporarily unavailable. Please try again later."
                    )
                }
                error.contains("network", ignoreCase = true) -> {
                    showError(
                        "Network Error",
                        "Please check your internet connection and try again."
                    )
                }
                else -> {
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoading() {
        loadingProgress.visibility = View.VISIBLE
        plansContainer.visibility = View.GONE
        errorCard.visibility = View.GONE
        successCard.visibility = View.GONE
    }

    private fun hideLoading() {
        loadingProgress.visibility = View.GONE
    }

    private fun showError(title: String, message: String) {
        errorTitle.text = title
        errorMessage.text = message
        errorCard.visibility = View.VISIBLE
        plansContainer.visibility = View.GONE
        successCard.visibility = View.GONE
    }

    private fun hideError() {
        errorCard.visibility = View.GONE
    }

    private fun showSuccess() {
        successCard.visibility = View.VISIBLE
        plansContainer.visibility = View.GONE
        errorCard.visibility = View.GONE
        loadingProgress.visibility = View.GONE
    }

    private fun openSubscriptionManagement() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/account/subscriptions")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to web
            openUrl("https://play.google.com/store/account/subscriptions")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK)
        finish()
    }

    override fun onResume() {
        super.onResume()
        
        // Check if subscription status changed while away
        val wasSubscribed = PrefsManager.isSubscribed(this)
        billingManager.validateSubscriptionStatus()
        billingManager.refreshSubscriptionStatus()
        
        val isNowSubscribed = PrefsManager.isSubscribed(this)
        
        // If user became subscribed while in another app, show success
        if (!wasSubscribed && isNowSubscribed) {
            showSuccess()
        }
        
        // Reset processing flag in case user returned from billing
        isProcessingPurchase = false
        hidePurchaseProgress()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle billing result
        val handled = billingManager.processPurchaseResult(requestCode, resultCode, data)
        
        if (handled) {
            // Wait a moment for the purchase to be processed
            Handler(Looper.getMainLooper()).postDelayed({
                billingManager.refreshSubscriptionStatus()
                
                if (PrefsManager.isSubscribed(this)) {
                    showSuccess()
                }
                
                isProcessingPurchase = false
                hidePurchaseProgress()
            }, 1000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up is handled by BillingManager singleton
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density + 0.5f).toInt()
    }

    private data class FeatureItem(
        val title: String,
        val description: String,
        val iconRes: Int
    )

    /**
     * Setup debug menu for testing subscription flows (debug builds only)
     */
    private fun setupDebugMenu() {
        // Only show debug menu in debug builds
        if (!BuildConfig.DEBUG) return

        try {
            // Find the main container (the LinearLayout inside the ScrollView)
            val scrollView = findViewById<ScrollView>(android.R.id.content)?.getChildAt(0) as? ScrollView
                ?: window.decorView.findViewById<ScrollView>(android.R.id.content)
                ?: return
                
            val mainContainer = scrollView.getChildAt(0) as? LinearLayout ?: return

            // Create debug container
            debugContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                setBackgroundColor(ContextCompat.getColor(this@SubscriptionActivity, R.color.debug_background))
            }

            // Debug title
            val debugTitle = TextView(this).apply {
                text = "🧪 DEBUG MENU"
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@SubscriptionActivity, android.R.color.holo_orange_dark))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 16.dpToPx())
            }
            debugContainer?.addView(debugTitle)

            // Grant Free Premium Button
            val btnGrantPremium = AppCompatButton(this).apply {
                text = "Grant Free Premium (30 days)"
                setBackgroundColor(ContextCompat.getColor(this@SubscriptionActivity, android.R.color.holo_green_dark))
                setTextColor(Color.WHITE)
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                
                setOnClickListener {
                    val mockProvider = billingManager.activeProvider as? MockBillingProvider
                    mockProvider?.grantFreePremium(30)
                    
                    Toast.makeText(this@SubscriptionActivity, "🧪 Granted 30 days free premium!", Toast.LENGTH_SHORT).show()
                    billingManager.refreshSubscriptionStatus()
                }
            }
            debugContainer?.addView(btnGrantPremium)

            // Expire Subscription Button
            val btnExpireSubscription = AppCompatButton(this).apply {
                text = "Expire Subscription"
                setBackgroundColor(ContextCompat.getColor(this@SubscriptionActivity, android.R.color.holo_red_dark))
                setTextColor(Color.WHITE)
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8.dpToPx()
                }
                
                setOnClickListener {
                    val mockProvider = billingManager.activeProvider as? MockBillingProvider
                    mockProvider?.simulateSubscriptionExpiry()
                    
                    Toast.makeText(this@SubscriptionActivity, "🧪 Subscription expired!", Toast.LENGTH_SHORT).show()
                    billingManager.refreshSubscriptionStatus()
                }
            }
            debugContainer?.addView(btnExpireSubscription)

            // Reset Billing State Button
            val btnResetBilling = AppCompatButton(this).apply {
                text = "Reset Billing State"
                setBackgroundColor(ContextCompat.getColor(this@SubscriptionActivity, android.R.color.holo_blue_dark))
                setTextColor(Color.WHITE)
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8.dpToPx()
                }
                
                setOnClickListener {
                    PrefsManager.setSubscribed(this@SubscriptionActivity, false, 0)
                    PrefsManager.setSubscriptionType(this@SubscriptionActivity, "")
                    
                    Toast.makeText(this@SubscriptionActivity, "🧪 Billing state reset!", Toast.LENGTH_SHORT).show()
                    billingManager.refreshSubscriptionStatus()
                }
            }
            debugContainer?.addView(btnResetBilling)

            // Show Current Status Button
            val btnShowStatus = AppCompatButton(this).apply {
                text = "Show Debug Info"
                setBackgroundColor(ContextCompat.getColor(this@SubscriptionActivity, android.R.color.darker_gray))
                setTextColor(Color.WHITE)
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8.dpToPx()
                }
                
                setOnClickListener {
                    val isSubscribed = PrefsManager.isSubscribed(this@SubscriptionActivity)
                    val endTime = PrefsManager.getSubscriptionEndTime(this@SubscriptionActivity)
                    val subscriptionType = PrefsManager.getSubscriptionType(this@SubscriptionActivity) ?: "none"
                    val productsCount = availableProducts.size
                    
                    val info = """
                        🧪 Debug Info:
                        
                        Subscribed: $isSubscribed
                        End Time: ${if (endTime > 0) Date(endTime) else "Never"}
                        Subscription Type: $subscriptionType
                        Available Products: $productsCount
                        Provider: ${billingManager.activeProvider?.javaClass?.simpleName}
                    """.trimIndent()
                    
                    androidx.appcompat.app.AlertDialog.Builder(this@SubscriptionActivity)
                        .setTitle("🧪 Debug Information")
                        .setMessage(info)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            debugContainer?.addView(btnShowStatus)

            // Add debug container to main layout
            mainContainer.addView(debugContainer)

            Timber.d("🧪 Debug menu setup completed")

        } catch (e: Exception) {
            Timber.e(e, "Error setting up debug menu")
        }
    }
}