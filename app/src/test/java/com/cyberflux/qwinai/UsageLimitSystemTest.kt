package com.cyberflux.qwinai

import android.content.Context
import com.cyberflux.qwinai.model.UsageCategory
import com.cyberflux.qwinai.model.TokenLimits
import com.cyberflux.qwinai.model.LimitType
import com.cyberflux.qwinai.utils.UnifiedUsageManager
import com.cyberflux.qwinai.utils.TokenValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Comprehensive test suite for the new Usage Limit System
 * 
 * Tests:
 * 1. Token limits (1000 input / 1500 output for free users)
 * 2. Daily request limits (30/day for premium limited models)
 * 3. Image generation limits (25/day for premium users)
 * 4. Model categorization
 * 5. Credit system integration
 * 6. UI display helpers
 */
@RunWith(RobolectricTestRunner::class)
class UsageLimitSystemTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var context: Context
    private lateinit var unifiedUsageManager: UnifiedUsageManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
        unifiedUsageManager = UnifiedUsageManager.getInstance(context)
    }
    
    @Test
    fun testTokenLimitsForFreeUsers() {
        // Test that free users get exactly 1000 input / 1500 output tokens
        val freeInputLimit = TokenValidator.getEffectiveMaxInputTokens("gpt-4o", false)
        val freeOutputLimit = TokenValidator.getEffectiveMaxOutputTokens("gpt-4o", false)
        
        assertEquals("Free users should get 1000 input tokens", 1000, freeInputLimit)
        assertEquals("Free users should get 1500 output tokens", 1500, freeOutputLimit)
    }
    
    @Test
    fun testTokenLimitsForPremiumUsers() {
        // Test that premium users get full model capacity
        val premiumInputLimit = TokenValidator.getEffectiveMaxInputTokens("gpt-4o", true)
        val premiumOutputLimit = TokenValidator.getEffectiveMaxOutputTokens("gpt-4o", true)
        
        // Should be much higher than free user limits
        assertTrue("Premium users should get more than 1000 input tokens", premiumInputLimit > 1000)
        assertTrue("Premium users should get more than 1500 output tokens", premiumOutputLimit > 1500)
    }
    
    @Test
    fun testModelCategorization() {
        // Test that models are correctly categorized
        
        // Free unlimited model
        val freeModel = unifiedUsageManager.getModelUsageCategory("cohere/command-r-plus")
        assertEquals("Cohere model should be free unlimited", UsageCategory.FREE_UNLIMITED, freeModel)
        
        // Limited premium models (30 requests/day for premium users)
        val limitedPremiumModel = unifiedUsageManager.getModelUsageCategory("gpt-4o")
        assertEquals("GPT-4o should be limited premium", UsageCategory.LIMITED_PREMIUM, limitedPremiumModel)
        
        val claudeModel = unifiedUsageManager.getModelUsageCategory("claude-3-7-sonnet-20250219")
        assertEquals("Claude should be limited premium", UsageCategory.LIMITED_PREMIUM, claudeModel)
        
        // Unlimited premium models
        val unlimitedPremiumModel = unifiedUsageManager.getModelUsageCategory("gpt-4-turbo")
        assertEquals("GPT-4 Turbo should be unlimited premium", UsageCategory.UNLIMITED_PREMIUM, unlimitedPremiumModel)
        
        val deepseekModel = unifiedUsageManager.getModelUsageCategory("deepseek/deepseek-r1")
        assertEquals("DeepSeek should be unlimited premium", UsageCategory.UNLIMITED_PREMIUM, deepseekModel)
        
        // Image generation models
        val imageModel = unifiedUsageManager.getModelUsageCategory("dall-e-3")
        assertEquals("DALL-E should be image generation", UsageCategory.IMAGE_GENERATION, imageModel)
    }
    
    @Test
    fun testFreeUserValidation() = runBlocking {
        // Test validation for free users with different scenarios
        
        // 1. Test short input (should pass)
        val shortInput = "Hello world"
        val shortValidation = unifiedUsageManager.validateRequest("gpt-4o", shortInput, false)
        assertFalse("Short input should be blocked for premium model without credits", shortValidation.isAllowed)
        assertTrue("Should require credits", shortValidation.creditsRequired > 0)
        
        // 2. Test very long input (should fail token limit)
        val longInput = "Hello world. ".repeat(200) // ~2400 characters, likely > 1000 tokens
        val longValidation = unifiedUsageManager.validateRequest("gpt-4o", longInput, false)
        assertFalse("Long input should be blocked", longValidation.isAllowed)
        assertEquals("Should fail on input tokens", LimitType.INPUT_TOKENS, longValidation.limitType)
        
        // 3. Test free unlimited model (should pass)
        val freeModelValidation = unifiedUsageManager.validateRequest("cohere/command-r-plus", shortInput, false)
        assertTrue("Free model should be allowed", freeModelValidation.isAllowed)
    }
    
    @Test
    fun testPremiumUserValidation() = runBlocking {
        // Test validation for premium users
        
        // 1. Test limited premium model (should pass initially)
        val limitedValidation = unifiedUsageManager.validateRequest("gpt-4o", "Hello world", true)
        assertTrue("Limited premium should be allowed for premium users", limitedValidation.isAllowed)
        
        // 2. Test unlimited premium model (should pass)
        val unlimitedValidation = unifiedUsageManager.validateRequest("gpt-4-turbo", "Hello world", true)
        assertTrue("Unlimited premium should be allowed", unlimitedValidation.isAllowed)
    }
    
    @Test
    fun testImageGenerationValidation() = runBlocking {
        // Test image generation validation
        
        // 1. Free user (should require credits)
        val freeImageValidation = unifiedUsageManager.validateRequest("dall-e-3", "A beautiful sunset", false)
        assertFalse("Free user should need credits for images", freeImageValidation.isAllowed)
        assertTrue("Should require credits", freeImageValidation.creditsRequired > 0)
        
        // 2. Premium user (should pass initially)
        val premiumImageValidation = unifiedUsageManager.validateRequest("dall-e-3", "A beautiful sunset", true)
        assertTrue("Premium user should be allowed initially", premiumImageValidation.isAllowed)
    }
    
    @Test
    fun testUsageRecording() = runBlocking {
        // Test that usage is recorded correctly
        
        // Record a request for a limited premium model
        unifiedUsageManager.recordRequest("gpt-4o", 500, 800, true)
        
        // Check that usage summary reflects the change
        val summary = unifiedUsageManager.getUsageSummary("gpt-4o", true)
        assertEquals("Should be limited premium category", UsageCategory.LIMITED_PREMIUM, summary.usageCategory)
        assertTrue("Should have daily request limit", summary.dailyRequestLimit > 0)
        assertTrue("Should have remaining requests", summary.remainingRequests >= 0)
    }
    
    @Test
    fun testImageGenerationRecording() = runBlocking {
        // Test image generation recording
        
        // Record image generation for premium user
        unifiedUsageManager.recordImageGeneration(true)
        
        // Check that it affects the daily limit
        val summary = unifiedUsageManager.getUsageSummary("dall-e-3", true)
        assertEquals("Should track daily image limit", 25, summary.dailyRequestLimit)
        assertTrue("Should have fewer remaining images", summary.remainingRequests < 25)
    }
    
    @Test
    fun testTokenLimitsDataClass() {
        // Test the TokenLimits data class
        val freeUserLimits = TokenLimits.FREE_USER_LIMITS
        assertEquals("Free input limit should be 1000", 1000, freeUserLimits.maxInputTokens)
        assertEquals("Free output limit should be 1500", 1500, freeUserLimits.maxOutputTokens)
    }
    
    @Test
    fun testUsageSummaryGeneration() {
        // Test usage summary generation for different user types and models
        
        // Free user with credit-based model
        val freeCreditSummary = unifiedUsageManager.getUsageSummary("gpt-4o", false)
        assertEquals("Should be credit-based for free user", UsageCategory.LIMITED_PREMIUM, freeCreditSummary.usageCategory)
        assertEquals("Should have free user token limits", 1000, freeCreditSummary.tokenLimits.maxInputTokens)
        assertEquals("Should have free user token limits", 1500, freeCreditSummary.tokenLimits.maxOutputTokens)
        assertTrue("Should require credits", freeCreditSummary.creditsRequired > 0)
        
        // Premium user with limited model
        val premiumLimitedSummary = unifiedUsageManager.getUsageSummary("gpt-4o", true)
        assertEquals("Should be limited premium", UsageCategory.LIMITED_PREMIUM, premiumLimitedSummary.usageCategory)
        assertTrue("Should have higher token limits", premiumLimitedSummary.tokenLimits.maxInputTokens > 1000)
        assertEquals("Should have no credit requirement", 0, premiumLimitedSummary.creditsRequired)
        assertEquals("Should have daily request limit", 30, premiumLimitedSummary.dailyRequestLimit)
    }
    
    @Test
    fun testDailyUsageConstants() {
        // Test that the daily usage constants match requirements
        assertEquals("Limited premium should be 30 requests per day", 
            30, UnifiedUsageManager.DAILY_REQUEST_LIMIT_LIMITED_PREMIUM)
        assertEquals("Image generation should be 25 per day for premium", 
            25, UnifiedUsageManager.DAILY_IMAGE_GENERATION_LIMIT_PREMIUM)
    }
    
    @Test
    fun testValidationResultTypes() {
        // Test different validation result types
        val allowed = com.cyberflux.qwinai.model.UsageValidationResult.allowed()
        assertTrue("Allowed result should be allowed", allowed.isAllowed)
        assertEquals("Should have no limit type", LimitType.NONE, allowed.limitType)
        
        val needsCredits = com.cyberflux.qwinai.model.UsageValidationResult.needsCredits(5)
        assertFalse("Needs credits should not be allowed", needsCredits.isAllowed)
        assertEquals("Should be credit limit type", LimitType.CREDITS, needsCredits.limitType)
        assertEquals("Should require 5 credits", 5, needsCredits.creditsRequired)
        
        val needsUpgrade = com.cyberflux.qwinai.model.UsageValidationResult.needsUpgrade("Premium required")
        assertFalse("Needs upgrade should not be allowed", needsUpgrade.isAllowed)
        assertTrue("Should require upgrade", needsUpgrade.upgradeRequired)
        assertEquals("Should be subscription limit", LimitType.SUBSCRIPTION, needsUpgrade.limitType)
    }
    
    @Test
    fun testTokenEstimation() {
        // Test token estimation works correctly
        val shortText = "Hello world"
        val shortTokens = TokenValidator.estimateTokenCount(shortText, "gpt-4o")
        assertTrue("Short text should have few tokens", shortTokens < 10)
        
        val mediumText = "This is a longer text that should have more tokens than the short text above."
        val mediumTokens = TokenValidator.estimateTokenCount(mediumText, "gpt-4o")
        assertTrue("Medium text should have more tokens than short", mediumTokens > shortTokens)
        
        val longText = "Lorem ipsum ".repeat(100) // ~1300 characters
        val longTokens = TokenValidator.estimateTokenCount(longText, "gpt-4o")
        assertTrue("Long text should exceed free user limit", longTokens > 1000)
    }
    
    @Test
    fun testDebugInfo() {
        // Test that debug info can be generated without errors
        val debugInfo = unifiedUsageManager.getDebugInfo()
        assertNotNull("Debug info should not be null", debugInfo)
        assertTrue("Debug info should contain useful information", debugInfo.contains("UNIFIED USAGE MANAGER"))
        assertTrue("Should contain credit information", debugInfo.contains("Credits"))
        assertTrue("Should contain date information", debugInfo.contains("Date"))
    }
}