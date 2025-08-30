# Comprehensive Usage Limits System Design

## Overview

This document outlines the new comprehensive usage limits system that combines token limits, daily request limits, and credit-based systems for both free and premium users.

## Current System Analysis

### Existing Components:
1. **CreditManager**: Separate chat/image credits with daily reset
2. **TokenValidator**: Model-aware token estimation and limits
3. **BillingManager**: Premium/free user management
4. **ModelConfigManager**: 15+ AI models with capabilities

## New Usage Limits Requirements

### Free Users:
- **Input Tokens**: 1,000 tokens per request
- **Output Tokens**: 1,500 tokens per request
- **Credit System**: Keep existing chat/image credits
- **Free Models**: Unlimited access to free models
- **Premium Models**: Credit-based access

### Premium Users:
- **Token Limits**: Higher limits based on model configuration
- **Premium Models**: Two categories:
  - **Limited Premium**: 30 requests per day
  - **Unlimited Premium**: No daily limits
- **Image Generation**: 25 generations per day
- **Free Models**: Unlimited access

## Proposed Architecture

### 1. Unified Usage Manager

```kotlin
class UnifiedUsageManager {
    // Handles all usage limits: tokens, credits, daily requests
    // Single entry point for validation
}
```

### 2. Usage Limit Types

#### A. Token Limits (per request)
- **Free Users**: 1,000 input / 1,500 output
- **Premium Users**: Model-specific limits (existing config)

#### B. Daily Request Limits (for premium models)
- **Limited Premium Models**: 30 requests per day
- **Unlimited Premium Models**: No limits
- **Tracking**: Reset at midnight user timezone

#### C. Credit Limits (existing system)
- **Free Users**: 25 chat credits, 30 image credits daily
- **Premium Users**: No credit consumption

### 3. Model Categorization

#### A. Free Models
- No token/credit restrictions for basic functionality
- Examples: Some Cohere models, basic Gemma models

#### B. Credit-Based Models (Free Users)
- Consume credits per request
- Examples: GPT-4, Claude, premium models

#### C. Limited Premium Models (Premium Users)
- 30 requests per day
- Examples: GPT-4o, Claude 3.7 Sonnet, Grok 3 Beta

#### D. Unlimited Premium Models (Premium Users)  
- No daily limits
- Examples: GPT-4 Turbo, DeepSeek R1, Qwen 3

#### E. Image Generation Models
- **Free Users**: Credit-based (existing system)
- **Premium Users**: 25 generations per day

## Implementation Plan

### Phase 1: Core Components

1. **Enhanced Model Configuration**
```kotlin
data class ModelConfig(
    // ... existing fields ...
    val usageCategory: UsageCategory,
    val dailyRequestLimit: Int = -1, // -1 = unlimited
    val freeTokenLimits: TokenLimits = TokenLimits(1000, 1500),
    val premiumTokenLimits: TokenLimits = TokenLimits(maxInputTokens, maxOutputTokens)
)

enum class UsageCategory {
    FREE_UNLIMITED,      // Free access, no limits
    CREDIT_BASED,        // Uses credit system
    LIMITED_PREMIUM,     // 30 requests/day for premium
    UNLIMITED_PREMIUM    // Unlimited for premium
}
```

2. **Unified Usage Tracker**
```kotlin
class UsageTracker {
    fun trackRequest(modelId: String, inputTokens: Int, outputTokens: Int)
    fun getDailyRequestCount(modelId: String): Int
    fun getRemainingRequests(modelId: String, isSubscribed: Boolean): Int
    fun resetDailyUsage()
}
```

### Phase 2: Validation System

3. **Pre-Request Validation**
```kotlin
suspend fun validateRequest(
    modelId: String,
    inputPrompt: String,
    isSubscribed: Boolean
): ValidationResult {
    // Check token limits
    // Check daily request limits  
    // Check credit availability
    // Return comprehensive validation result
}
```

### Phase 3: UI Integration

4. **Usage Display Components**
- Token counter with limits
- Daily request counter for premium models
- Credit display for free users
- Clear upgrade prompts

## Suggested Model Categories

### Free Unlimited (No Restrictions)
- `cohere/command-r-plus` (already marked as free)
- Basic educational/demo models

### Credit-Based (Free Users Pay Credits)
- `gpt-4o`
- `claude-3-7-sonnet-20250219`  
- `meta-llama/llama-4-maverick`
- `x-ai/grok-3-beta`
- `perplexity/sonar-pro`

### Limited Premium (30 requests/day)
- `gpt-4o` (most expensive model)
- `claude-3-7-sonnet-20250219` (high capability)
- `x-ai/grok-3-beta` (beta/experimental)
- `Qwen/Qwen3-235B-A22B-fp8-tput` (large model)

### Unlimited Premium
- `gpt-4-turbo` (stable, widely used)
- `deepseek/deepseek-r1` (good balance)
- `google/gemma-3-27b-it` (efficient)
- `mistral/mistral-ocr-latest` (specialized)
- `zhipu/glm-4.5` (good performance)

### Image Generation (25/day Premium)
- All existing image models: DALL-E 3, Flux, Stable Diffusion, etc.
- Free users continue using credit system

## Additional Suggestions & Improvements

### A. Smart Limit Management
1. **Token Pooling**: Allow users to "save up" unused tokens within limits
2. **Burst Allowance**: Occasional higher token usage for special cases
3. **Progressive Limits**: Increase limits based on usage patterns/loyalty

### B. Enhanced User Experience
1. **Usage Dashboard**: Detailed breakdown of token/request usage
2. **Smart Warnings**: Proactive notifications before hitting limits
3. **Upgrade Guidance**: Contextual upgrade suggestions based on usage

### C. Business Logic Improvements
1. **Time-based Limits**: Different limits for peak/off-peak hours
2. **Model Rotation**: Suggest alternative models when limits reached
3. **Fair Usage Policy**: Prevent abuse while allowing legitimate usage

### D. Technical Enhancements
1. **Async Tracking**: Non-blocking usage tracking
2. **Cached Limits**: Fast limit checking with Redis/memory cache
3. **Analytics Integration**: Usage pattern analysis for optimization

### E. Monetization Opportunities
1. **Token Packs**: Additional token purchases for free users
2. **Model-Specific Subscriptions**: Premium access to specific models
3. **Usage-Based Pricing**: Pay-per-token option for high-volume users

## Implementation Priority

### High Priority (Core Functionality):
1. Free user token limits (1000 input / 1500 output)
2. Premium daily request limits (30/day for specific models)
3. Image generation limits (25/day premium)
4. Basic usage tracking and reset

### Medium Priority (Enhanced UX):
1. Usage dashboard and counters
2. Smart warnings and notifications
3. Model categorization display

### Low Priority (Advanced Features):
1. Token pooling and burst allowance
2. Usage analytics and insights
3. Advanced monetization features

## Technical Considerations

### Performance:
- Minimize database operations during request validation
- Use memory caching for frequently accessed limits
- Async logging of usage data

### Security:
- Encrypted storage of usage data
- Anti-tampering measures similar to CreditManager
- Rate limiting to prevent abuse

### Scalability:
- Horizontal scaling support
- Database sharding for usage data
- Efficient cleanup of old usage records

### Error Handling:
- Graceful degradation when limits service unavailable
- Clear error messages for different limit types
- Recovery mechanisms for failed limit updates

## Success Metrics

### User Engagement:
- Increase in premium subscriptions
- Reduced churn due to limit frustration
- Higher user satisfaction scores

### Technical Performance:
- < 100ms limit validation response time
- 99.9% uptime for limits service
- Zero limit bypass security incidents

### Business Impact:
- Improved revenue per user
- Better cost control for free tier
- Reduced support tickets about limits

This comprehensive system will provide clear, fair usage limits while encouraging premium upgrades and maintaining a great user experience for all user types.