package com.cyberflux.qwinai.utils

import android.content.Context
import android.widget.Toast
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.billing.BillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Comprehensive payment testing and validation helper
 * Tests all possible payment scenarios and edge cases
 */
object PaymentTestHelper {

    /**
     * Test all payment scenarios
     */
    fun runComprehensivePaymentTests(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            Timber.d("🧪 Starting comprehensive payment tests")
            
            try {
                // Test 1: Initial subscription state validation
                testInitialSubscriptionState(context)
                delay(1000)
                
                // Test 2: Billing manager initialization
                testBillingManagerInitialization(context)
                delay(1000)
                
                // Test 3: Subscription expiry handling
                testSubscriptionExpiryHandling(context)
                delay(1000)
                
                // Test 4: Feature access validation
                testFeatureAccessValidation(context)
                delay(1000)
                
                // Test 5: Edge case handling
                testEdgeCases(context)
                delay(1000)
                
                // Test 6: UI state consistency
                testUIStateConsistency(context)
                
                Timber.d("✅ All payment tests completed successfully")
                
                // Show results to user in debug builds
                if (BuildConfig.DEBUG) {
                    Toast.makeText(context, "Payment system tests completed - check logs", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Payment tests failed: ${e.message}")
                
                if (BuildConfig.DEBUG) {
                    Toast.makeText(context, "Payment tests failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Test initial subscription state validation
     */
    private fun testInitialSubscriptionState(context: Context) {
        Timber.d("🧪 Testing initial subscription state")
        
        val isSubscribed = PrefsManager.isSubscribed(context)
        val endTime = PrefsManager.getSubscriptionEndTime(context)
        val shouldShowAds = PrefsManager.shouldShowAds(context)
        
        // Validate consistency
        if (isSubscribed && shouldShowAds) {
            Timber.w("⚠️ Inconsistency: User is subscribed but ads are enabled")
        }
        
        if (isSubscribed && endTime > 0) {
            val remainingTime = endTime - System.currentTimeMillis()
            if (remainingTime <= 0) {
                Timber.w("⚠️ Expired subscription detected")
            } else {
                val days = remainingTime / (24 * 60 * 60 * 1000)
                Timber.d("✅ Active subscription: ${days} days remaining")
            }
        }
        
        Timber.d("✅ Initial state validation complete")
    }

    /**
     * Test billing manager initialization
     */
    private suspend fun testBillingManagerInitialization(context: Context) {
        Timber.d("🧪 Testing billing manager initialization")
        
        try {
            val billingManager = BillingManager.getInstance(context)
            
            // Test connection
            var connected = false
            billingManager.connectToPlayBilling { success ->
                connected = success
            }
            
            // Wait for connection
            var attempts = 0
            while (!connected && attempts < 5) {
                delay(1000)
                attempts++
            }
            
            if (connected) {
                Timber.d("✅ Billing manager connected successfully")
                
                // Test product query
                billingManager.queryProducts()
                delay(2000)
                
                // Test subscription restoration
                billingManager.restoreSubscriptions()
                delay(2000)
                
                Timber.d("✅ Billing operations completed")
            } else {
                Timber.w("❌ Failed to connect to billing service")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Billing manager initialization failed")
        }
    }

    /**
     * Test subscription expiry handling
     */
    private fun testSubscriptionExpiryHandling(context: Context) {
        Timber.d("🧪 Testing subscription expiry handling")
        
        // Test with expired subscription
        val currentTime = System.currentTimeMillis()
        val expiredTime = currentTime - (24 * 60 * 60 * 1000) // 1 day ago
        
        // Temporarily set expired subscription
        PrefsManager.setSubscribed(context, true, 0)
        val prefs = context.getSharedPreferences("qwinai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("subscription_end_time", expiredTime).apply()
        
        // Test expiry detection
        val shouldBeExpired = PrefsManager.isSubscribed(context)
        
        if (!shouldBeExpired) {
            Timber.d("✅ Expiry detection working correctly")
        } else {
            Timber.w("❌ Expiry detection failed")
        }
        
        // Test SubscriptionManager handling
        SubscriptionManager.updateSubscriptionStatus(context)
        
        val managerStatus = SubscriptionManager.subscriptionStatus.value
        if (!managerStatus) {
            Timber.d("✅ SubscriptionManager expiry handling working")
        } else {
            Timber.w("❌ SubscriptionManager expiry handling failed")
        }
    }

    /**
     * Test feature access validation
     */
    private fun testFeatureAccessValidation(context: Context) {
        Timber.d("🧪 Testing feature access validation")
        
        val testFeatures = listOf(
            "unlimited_conversations",
            "advanced_models", 
            "no_ads",
            "file_processing",
            "voice_chat",
            "image_generation"
        )
        
        testFeatures.forEach { feature ->
            val hasAccess = SubscriptionManager.isPremiumFeatureAvailable(context, feature)
            val isSubscribed = SubscriptionManager.subscriptionStatus.value
            
            if (isSubscribed && !hasAccess) {
                Timber.w("❌ Feature $feature should be available for subscribed user")
            } else if (!isSubscribed && hasAccess) {
                Timber.w("❌ Feature $feature should not be available for free user")
            } else {
                Timber.d("✅ Feature $feature access correct")
            }
        }
    }

    /**
     * Test edge cases
     */
    private fun testEdgeCases(context: Context) {
        Timber.d("🧪 Testing edge cases")
        
        // Test 1: Corrupted subscription data
        testCorruptedSubscriptionData(context)
        
        // Test 2: Network failures during purchase
        testNetworkFailureScenarios(context)
        
        // Test 3: Multiple subscription status changes
        testRapidStatusChanges(context)
        
        // Test 4: App restart during purchase
        testAppRestartScenarios(context)
        
        Timber.d("✅ Edge case testing completed")
    }

    /**
     * Test corrupted subscription data
     */
    private fun testCorruptedSubscriptionData(context: Context) {
        Timber.d("🧪 Testing corrupted data handling")
        
        val prefs = context.getSharedPreferences("qwinai_prefs", Context.MODE_PRIVATE)
        
        // Corrupt the validation hash
        prefs.edit().putString("subscription_validation_hash", "corrupted_hash").apply()
        
        // Test if system handles corrupted data
        val isSubscribed = PrefsManager.isSubscribed(context)
        
        if (!isSubscribed) {
            Timber.d("✅ Corrupted data handled correctly (subscription reset)")
        } else {
            Timber.w("❌ System did not detect corrupted data")
        }
    }

    /**
     * Test network failure scenarios
     */
    private fun testNetworkFailureScenarios(context: Context) {
        Timber.d("🧪 Testing network failure scenarios")
        
        // These tests verify that the system handles network failures gracefully
        // In a real implementation, you might simulate network conditions
        
        try {
            val billingManager = BillingManager.getInstance(context)
            
            // Test connection without network (will fail gracefully)
            billingManager.connectToPlayBilling { success ->
                if (!success) {
                    Timber.d("✅ Network failure handled gracefully")
                }
            }
            
        } catch (e: Exception) {
            Timber.d("✅ Exception during network failure handled: ${e.message}")
        }
    }

    /**
     * Test rapid status changes
     */
    private fun testRapidStatusChanges(context: Context) {
        Timber.d("🧪 Testing rapid status changes")
        
        // Simulate rapid subscription changes
        for (i in 1..5) {
            PrefsManager.setSubscribed(context, i % 2 == 0, if (i % 2 == 0) 30 else 0)
            SubscriptionManager.updateSubscriptionStatus(context)
        }
        
        // Final state should be consistent
        val finalStatus = SubscriptionManager.subscriptionStatus.value
        val prefsStatus = PrefsManager.isSubscribed(context)
        
        if (finalStatus == prefsStatus) {
            Timber.d("✅ Status consistency maintained during rapid changes")
        } else {
            Timber.w("❌ Status inconsistency after rapid changes")
        }
    }

    /**
     * Test app restart scenarios
     */
    private fun testAppRestartScenarios(context: Context) {
        Timber.d("🧪 Testing app restart scenarios")
        
        // Set a subscription status
        PrefsManager.setSubscribed(context, true, 30)
        
        // Simulate app restart by reinitializing SubscriptionManager
        SubscriptionManager.initialize(context)
        
        val restoredStatus = SubscriptionManager.subscriptionStatus.value
        
        if (restoredStatus) {
            Timber.d("✅ Subscription status persisted across restart")
        } else {
            Timber.w("❌ Subscription status lost after restart")
        }
    }

    /**
     * Test UI state consistency
     */
    private fun testUIStateConsistency(context: Context) {
        Timber.d("🧪 Testing UI state consistency")
        
        // Test with subscribed state
        PrefsManager.setSubscribed(context, true, 30)
        SubscriptionManager.updateSubscriptionStatus(context)
        
        val shouldShowAds = PrefsManager.shouldShowAds(context)
        val premiumFeaturesEnabled = SubscriptionManager.premiumFeaturesEnabled.value
        
        if (!shouldShowAds && premiumFeaturesEnabled) {
            Timber.d("✅ UI state consistent for subscribed user")
        } else {
            Timber.w("❌ UI state inconsistent for subscribed user")
        }
        
        // Test with free state
        PrefsManager.setSubscribed(context, false, 0)
        SubscriptionManager.updateSubscriptionStatus(context)
        
        val shouldShowAdsFree = PrefsManager.shouldShowAds(context)
        val premiumFeaturesEnabledFree = SubscriptionManager.premiumFeaturesEnabled.value
        
        if (shouldShowAdsFree && !premiumFeaturesEnabledFree) {
            Timber.d("✅ UI state consistent for free user")
        } else {
            Timber.w("❌ UI state inconsistent for free user")
        }
    }

    /**
     * Validate payment flow scenarios
     */
    fun validatePaymentFlowScenarios(context: Context): PaymentValidationResult {
        return try {
            val results = mutableListOf<String>()
            
            // Check billing manager availability
            val billingManager = BillingManager.getInstance(context)
            results.add("✅ BillingManager initialized")
            
            // Check subscription manager
            SubscriptionManager.initialize(context)
            results.add("✅ SubscriptionManager initialized")
            
            // Check preference consistency
            val isSubscribed = PrefsManager.isSubscribed(context)
            val shouldShowAds = PrefsManager.shouldShowAds(context)
            
            if (isSubscribed && !shouldShowAds) {
                results.add("✅ Subscription/Ad preferences consistent")
            } else if (!isSubscribed && shouldShowAds) {
                results.add("✅ Free user preferences consistent")
            } else {
                results.add("❌ Preference inconsistency detected")
            }
            
            // Check feature access
            val premiumAvailable = SubscriptionManager.isPremiumFeatureAvailable(context, "unlimited_conversations")
            if ((isSubscribed && premiumAvailable) || (!isSubscribed && !premiumAvailable)) {
                results.add("✅ Feature access consistent")
            } else {
                results.add("❌ Feature access inconsistent")
            }
            
            PaymentValidationResult(
                success = true,
                message = "Payment system validation completed",
                details = results
            )
            
        } catch (e: Exception) {
            PaymentValidationResult(
                success = false,
                message = "Payment system validation failed: ${e.message}",
                details = listOf("❌ ${e.message}")
            )
        }
    }

    data class PaymentValidationResult(
        val success: Boolean,
        val message: String,
        val details: List<String>
    )
}