package com.cyberflux.qwinai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.billing.BillingProvider
import com.cyberflux.qwinai.billing.ProductInfo
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.HapticManager
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.SubscriptionAnalyticsManager
import com.cyberflux.qwinai.utils.PurchaseStep
import com.cyberflux.qwinai.utils.SubscriptionEvent
import com.cyberflux.qwinai.utils.SubscriptionComplianceManager
import kotlinx.coroutines.launch
import timber.log.Timber

class SubscriptionActivity : BaseThemedActivity() {

    private lateinit var billingManager: BillingManager
    
    // UI Components
    private lateinit var btnClose: ImageView
    private lateinit var currentStatusCard: CardView
    private lateinit var statusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var btnManageSubscription: TextView
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
    
    // State
    private var availableProducts: List<ProductInfo> = emptyList()
    private var isProcessingPurchase = false
    private var connectionRetryCount = 0
    private val maxRetries = 3
    
    companion object {
        private const val TERMS_URL = "https://docs.google.com/document/d/1example-terms"
        private const val PRIVACY_URL = "https://docs.google.com/document/d/1example-privacy"
        
        fun start(context: Context) {
            val intent = Intent(context, SubscriptionActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)
        
        Timber.d("🚀 SubscriptionActivity started")
        
        // Track subscription screen view for analytics
        val source = intent.getStringExtra("source") ?: "unknown"
        SubscriptionAnalyticsManager.trackSubscriptionScreenView(this)
        SubscriptionAnalyticsManager.trackPurchaseFunnel(
            this,
            PurchaseStep.SCREEN_VIEWED,
            "all_plans"
        )
        
        initializeViews()
        setupClickListeners()
        initializeBillingManager()
        updateUIBasedOnCurrentStatus()
    }

    private fun initializeViews() {
        btnClose = findViewById(R.id.btnClose)
        currentStatusCard = findViewById(R.id.currentStatusCard)
        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusSubtitle = findViewById(R.id.statusSubtitle)
        btnManageSubscription = findViewById(R.id.btnManageSubscription)
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
        
        Timber.d("✅ Views initialized successfully")
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            HapticManager.lightVibration(this)
            finish()
        }

        btnRetry.setOnClickListener {
            HapticManager.lightVibration(this)
            retryConnection()
        }

        btnContinue.setOnClickListener {
            HapticManager.lightVibration(this)
            finish()
        }

        btnRestorePurchases.setOnClickListener {
            HapticManager.lightVibration(this)
            restorePurchases()
        }

        btnTerms.setOnClickListener {
            HapticManager.lightVibration(this)
            SubscriptionComplianceManager.openTermsOfService(this)
        }

        btnPrivacy.setOnClickListener {
            HapticManager.lightVibration(this)
            SubscriptionComplianceManager.openPrivacyPolicy(this)
        }

        btnManageSubscription.setOnClickListener {
            HapticManager.lightVibration(this)
            SubscriptionComplianceManager.showSubscriptionManagement(this)
        }
    }

    private fun initializeBillingManager() {
        Timber.d("🔧 Initializing billing manager")
        
        try {
            billingManager = BillingManager.getInstance(this)
            
            // Show loading state
            showLoadingState()
            
            // Observe billing manager states
            observeBillingStates()
            
            // Connect to billing service
            billingManager.connectToPlayBilling { success ->
                if (success) {
                    Timber.d("✅ Billing connected successfully")
                    billingManager.queryProducts()
                } else {
                    Timber.e("❌ Billing connection failed")
                    showErrorState("Failed to connect to billing service")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error initializing billing manager")
            showErrorState("Error initializing billing: ${e.message}")
        }
    }

    private fun observeBillingStates() {
        // Observe product details
        lifecycleScope.launch {
            billingManager.productDetails.collect { products ->
                Timber.d("📦 Received ${products.size} products from billing manager")
                availableProducts = products
                
                if (products.isNotEmpty()) {
                    showSubscriptionPlans(products)
                } else if (loadingProgress.visibility == View.VISIBLE) {
                    // Only show error if we're currently loading
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (availableProducts.isEmpty()) {
                            showErrorState("No subscription plans available")
                        }
                    }, 3000) // Give it 3 seconds to load
                }
            }
        }

        // Observe subscription status
        lifecycleScope.launch {
            billingManager.subscriptionStatus.collect { isSubscribed ->
                Timber.d("📱 Subscription status: $isSubscribed")
                updateUIBasedOnCurrentStatus()
                
                if (isSubscribed) {
                    showSuccessState()
                }
            }
        }

        // Observe error messages
        lifecycleScope.launch {
            billingManager.errorMessage.collect { error ->
                if (error.isNotBlank()) {
                    Timber.w("⚠️ Billing error: $error")
                    if (!isProcessingPurchase) {
                        Toast.makeText(this@SubscriptionActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showLoadingState() {
        loadingProgress.visibility = View.VISIBLE
        plansContainer.visibility = View.GONE
        errorCard.visibility = View.GONE
        successCard.visibility = View.GONE
    }

    private fun showSubscriptionPlans(products: List<ProductInfo>) {
        Timber.d("🎯 Displaying ${products.size} subscription plans")
        
        loadingProgress.visibility = View.GONE
        plansContainer.visibility = View.VISIBLE
        errorCard.visibility = View.GONE
        successCard.visibility = View.GONE
        
        // Clear existing plans
        plansContainer.removeAllViews()
        
        // Sort products: weekly first, then monthly
        val sortedProducts = products.sortedBy { product ->
            when (product.productId) {
                BillingProvider.SUBSCRIPTION_WEEKLY -> 0
                BillingProvider.SUBSCRIPTION_MONTHLY -> 1
                else -> 2
            }
        }
        
        sortedProducts.forEachIndexed { index, product ->
            val planCard = createPlanCard(product, index == 1) // Make monthly plan popular
            plansContainer.addView(planCard)
        }
    }

    private fun createPlanCard(product: ProductInfo, isPopular: Boolean): CardView {
        // Get theme-aware colors using TypedValue
        val typedValue = android.util.TypedValue()
        val theme = this.theme
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val surfaceColor = ContextCompat.getColor(this, typedValue.resourceId)
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
        val primaryContainerColor = ContextCompat.getColor(this, typedValue.resourceId)
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val onSurfaceColor = ContextCompat.getColor(this, typedValue.resourceId)
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val primaryColor = ContextCompat.getColor(this, typedValue.resourceId)
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val onPrimaryColor = ContextCompat.getColor(this, typedValue.resourceId)
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        val onSurfaceVariantColor = ContextCompat.getColor(this, typedValue.resourceId)

        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                if (plansContainer.childCount > 0) {
                    marginStart = resources.getDimensionPixelSize(R.dimen.subscription_plan_spacing)
                }
            }
            radius = resources.getDimensionPixelSize(R.dimen.card_corner_radius).toFloat()
            cardElevation = if (isPopular) 12f else 6f
            setCardBackgroundColor(if (isPopular) primaryContainerColor else surfaceColor)
            
            // Add subtle stroke for better definition
            if (isPopular) {
                foreground = ContextCompat.getDrawable(context, R.drawable.premium_card_stroke)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.card_corner_radius) + 8
            setPadding(padding, padding, padding, padding)
        }

        // Popular badge with professional styling
        if (isPopular) {
            val badgeContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 16)
            }
            
            val crown = TextView(this).apply {
                text = "👑"
                textSize = 16f
                setPadding(0, 0, 8, 0)
            }
            
            val badge = TextView(this).apply {
                text = "MOST POPULAR"
                textSize = 11f
                setTextColor(onPrimaryColor)
                background = ContextCompat.getDrawable(context, R.drawable.popular_badge_background)
                setPadding(12, 6, 12, 6)
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            badgeContainer.addView(crown)
            badgeContainer.addView(badge)
            container.addView(badgeContainer)
        } else {
            // Add spacer for alignment
            val spacer = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.small_margin) * 4
                )
            }
            container.addView(spacer)
        }

        // Plan title with better typography
        val title = TextView(this).apply {
            text = when (product.productId) {
                BillingProvider.SUBSCRIPTION_WEEKLY -> "Weekly Premium"
                BillingProvider.SUBSCRIPTION_MONTHLY -> "Monthly Premium"
                else -> product.title
            }
            textSize = 20f
            setTextColor(onSurfaceColor)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }
        container.addView(title)

        // Price with enhanced styling
        val priceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.CENTER_VERTICAL
        }
        
        val currencySymbol = TextView(this).apply {
            text = product.price.take(1) // Get currency symbol
            textSize = 18f
            setTextColor(primaryColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 4, 0)
        }
        
        val priceAmount = TextView(this).apply {
            text = product.price.drop(1) // Remove currency symbol
            textSize = 32f
            setTextColor(primaryColor)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        
        priceContainer.addView(currencySymbol)
        priceContainer.addView(priceAmount)
        container.addView(priceContainer)

        // Duration with professional styling
        val duration = TextView(this).apply {
            text = when (product.productId) {
                BillingProvider.SUBSCRIPTION_WEEKLY -> "per week"
                BillingProvider.SUBSCRIPTION_MONTHLY -> "per month" 
                else -> "subscription"
            }
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 4, 0, 20)
            alpha = 0.8f
        }
        container.addView(duration)

        // Features list for popular plan
        if (isPopular) {
            val featuresContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16)
            }
            
            val features = listOf(
                "🚀 Unlimited AI conversations",
                "⭐ Premium AI models",
                "🎨 AI image generation",
                "📱 Priority support"
            )
            
            features.forEach { feature ->
                val featureText = TextView(this).apply {
                    text = feature
                    textSize = 12f
                    setTextColor(onSurfaceColor)
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 2, 0, 2)
                    alpha = 0.9f
                }
                featuresContainer.addView(featureText)
            }
            
            container.addView(featuresContainer)
        }

        // Subscribe button with Material 3 styling
        val button = AppCompatButton(this).apply {
            text = if (isPopular) "Start Premium" else "Subscribe"
            textSize = 16f
            setTextColor(onPrimaryColor)
            background = ContextCompat.getDrawable(context, R.drawable.modern_button_background)
            isAllCaps = false
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            
            val buttonPadding = resources.getDimensionPixelSize(R.dimen.standard_corner_radius) * 2
            setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
            
            // Add ripple effect
            foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            
            setOnClickListener {
                HapticManager.mediumVibration(this@SubscriptionActivity)
                
                // Track plan selection for analytics
                SubscriptionAnalyticsManager.trackPlanInteraction(
                    this@SubscriptionActivity, 
                    product.productId, 
                    "tapped"
                )
                SubscriptionAnalyticsManager.trackPurchaseFunnel(
                    this@SubscriptionActivity,
                    PurchaseStep.PLAN_SELECTED,
                    product.productId
                )
                
                purchaseSubscription(product)
            }
        }
        container.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        cardView.addView(container)
        return cardView
    }

    private fun purchaseSubscription(product: ProductInfo) {
        if (isProcessingPurchase) {
            Timber.w("⏳ Purchase already in progress")
            return
        }

        // CRITICAL: Show legal compliance disclosure before purchase
        val billingPeriod = when (product.productId) {
            BillingProvider.SUBSCRIPTION_WEEKLY -> "weekly"
            BillingProvider.SUBSCRIPTION_MONTHLY -> "monthly"
            else -> "subscription"
        }
        
        // Track disclosure step
        SubscriptionAnalyticsManager.trackPurchaseFunnel(
            this,
            PurchaseStep.DISCLOSURE_SHOWN,
            product.productId
        )
        
        SubscriptionComplianceManager.showSubscriptionDisclosure(this) { disclosureAccepted ->
            if (disclosureAccepted) {
                SubscriptionAnalyticsManager.trackPurchaseFunnel(
                    this,
                    PurchaseStep.DISCLOSURE_ACCEPTED,
                    product.productId
                )
                SubscriptionAnalyticsManager.trackPurchaseFunnel(
                    this,
                    PurchaseStep.PURCHASE_INITIATED,
                    product.productId
                )
                proceedWithPurchase(product)
            } else {
                SubscriptionAnalyticsManager.trackPurchaseFunnel(
                    this,
                    PurchaseStep.PURCHASE_CANCELLED,
                    product.productId,
                    mapOf("reason" to "disclosure_rejected")
                )
            }
        }
    }
    
    private fun proceedWithPurchase(product: ProductInfo) {
        Timber.d("🛒 Starting purchase for ${product.productId}")
        isProcessingPurchase = true
        
        try {
            // Track billing flow start
            SubscriptionAnalyticsManager.trackPurchaseFunnel(
                this,
                PurchaseStep.BILLING_FLOW_STARTED,
                product.productId
            )
            
            billingManager.launchBillingFlow(this, product.productId)
            
            // Reset processing flag after timeout
            Handler(Looper.getMainLooper()).postDelayed({
                if (isProcessingPurchase) {
                    isProcessingPurchase = false
                    SubscriptionAnalyticsManager.trackPurchaseFunnel(
                        this,
                        PurchaseStep.PURCHASE_FAILED,
                        product.productId,
                        mapOf("reason" to "timeout")
                    )
                }
            }, 30000) // 30 seconds timeout
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error launching billing flow")
            isProcessingPurchase = false
            
            SubscriptionAnalyticsManager.trackPurchaseFunnel(
                this,
                PurchaseStep.PURCHASE_FAILED,
                product.productId,
                mapOf("reason" to "billing_flow_error", "error" to e.message.orEmpty())
            )
            
            Toast.makeText(this, getString(R.string.subscription_error_unknown), Toast.LENGTH_LONG).show()
        }
    }

    private fun showErrorState(message: String) {
        loadingProgress.visibility = View.GONE
        plansContainer.visibility = View.GONE
        errorCard.visibility = View.VISIBLE
        successCard.visibility = View.GONE
        
        errorMessage.text = message
    }

    private fun showSuccessState() {
        loadingProgress.visibility = View.GONE
        plansContainer.visibility = View.GONE
        errorCard.visibility = View.GONE
        successCard.visibility = View.VISIBLE
        
        // Auto-close after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
    }

    private fun retryConnection() {
        if (connectionRetryCount >= maxRetries) {
            Toast.makeText(this, "Max retries reached. Please check your connection.", Toast.LENGTH_LONG).show()
            return
        }
        
        connectionRetryCount++
        Timber.d("🔄 Retrying connection (attempt $connectionRetryCount)")
        
        showLoadingState()
        initializeBillingManager()
    }

    private fun restorePurchases() {
        Timber.d("🔄 Restoring purchases")
        Toast.makeText(this, "Restoring purchases...", Toast.LENGTH_SHORT).show()
        
        billingManager.restoreSubscriptions()
        
        // Refresh UI after restoration
        Handler(Looper.getMainLooper()).postDelayed({
            updateUIBasedOnCurrentStatus()
        }, 2000)
    }

    private fun updateUIBasedOnCurrentStatus() {
        val isSubscribed = PrefsManager.isSubscribed(this)
        val subscriptionType = PrefsManager.getSubscriptionType(this)
        val endTime = PrefsManager.getSubscriptionEndTime(this)
        
        if (isSubscribed) {
            currentStatusCard.visibility = View.VISIBLE
            statusIcon.setImageResource(R.drawable.ic_checkmark_green)
            statusTitle.text = "Premium Active"
            
            if (endTime > 0) {
                val date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(endTime))
                statusSubtitle.text = "Expires on $date"
            } else {
                statusSubtitle.text = "Active subscription"
            }
            
            btnManageSubscription.visibility = View.VISIBLE
        } else {
            currentStatusCard.visibility = View.GONE
        }
    }

    private fun openSubscriptionManagement() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open subscription management", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Timber.d("🔄 Activity result: requestCode=$requestCode, resultCode=$resultCode")
        
        val handled = billingManager.processPurchaseResult(requestCode, resultCode, data)
        if (handled) {
            isProcessingPurchase = false
            
            // Track purchase completion analytics
            if (resultCode == RESULT_OK) {
                val subscriptionType = PrefsManager.getSubscriptionType(this) ?: "unknown"
                SubscriptionAnalyticsManager.trackPurchaseFunnel(
                    this,
                    PurchaseStep.PURCHASE_COMPLETED,
                    subscriptionType
                )
                SubscriptionAnalyticsManager.trackSubscriptionEvent(
                    this,
                    SubscriptionEvent.SUBSCRIPTION_STARTED,
                    mapOf("plan" to subscriptionType)
                )
            } else {
                SubscriptionAnalyticsManager.trackPurchaseFunnel(
                    this,
                    PurchaseStep.PURCHASE_FAILED,
                    "unknown",
                    mapOf("reason" to "user_cancelled_or_failed")
                )
            }
            
            // Refresh subscription status
            Handler(Looper.getMainLooper()).postDelayed({
                billingManager.refreshSubscriptionStatus()
                updateUIBasedOnCurrentStatus()
            }, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Refresh subscription status when returning to activity
        billingManager.refreshSubscriptionStatus()
        updateUIBasedOnCurrentStatus()
        
        // Reset processing flag in case user returned from billing flow
        if (isProcessingPurchase) {
            Handler(Looper.getMainLooper()).postDelayed({
                isProcessingPurchase = false
            }, 2000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("🔚 SubscriptionActivity destroyed")
    }
}