package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.network.AimlApiResponse
import timber.log.Timber

/**
 * Universal token counter that works across all AI providers in the app
 * Handles the differences in token reporting between OpenAI, Anthropic, Google, etc.
 */
object UniversalTokenCounter {
    
    /**
     * Extract token usage from any AI provider response
     * @param response The API response
     * @param modelId The model ID to determine provider-specific handling
     * @return TokenUsage with standardized token counts
     */
    fun extractTokenUsage(response: AimlApiResponse, modelId: String): TokenUsage {
        val usage = response.usage
        if (usage == null) {
            Timber.w("⚠️ No token usage provided by model: $modelId")
            return TokenUsage.empty()
        }
        
        val provider = getProviderFromModelId(modelId)
        
        return when (provider) {
            "openai" -> extractOpenAITokens(usage, modelId)
            "anthropic" -> extractAnthropicTokens(usage, modelId) 
            "google" -> extractGoogleTokens(usage, modelId)
            "meta" -> extractMetaTokens(usage, modelId)
            "cohere" -> extractCohereTokens(usage, modelId)
            "deepseek" -> extractDeepSeekTokens(usage, modelId)
            "qwen" -> extractQwenTokens(usage, modelId)
            "xai" -> extractXAITokens(usage, modelId)
            "mistral" -> extractMistralTokens(usage, modelId)
            "perplexity" -> extractPerplexityTokens(usage, modelId)
            "zhipu" -> extractZhiPuTokens(usage, modelId)
            else -> extractGenericTokens(usage, modelId) // Fallback
        }
    }
    
    /**
     * OpenAI models (GPT-4o, GPT-4-turbo, O1, etc.)
     * Full automatic token counting including files
     */
    private fun extractOpenAITokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: 0
        val outputTokens = usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        val reasoningTokens = usage.reasoningTokens ?: 0
        
        Timber.d("🟢 OpenAI token extraction: in=$inputTokens, out=$outputTokens, reasoning=$reasoningTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            reasoningTokens = reasoningTokens,
            provider = "openai",
            supportsFileTokens = true
        )
    }
    
    /**
     * Anthropic Claude models
     * Uses input_tokens/output_tokens instead of prompt_tokens/completion_tokens
     */
    private fun extractAnthropicTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.inputTokens ?: usage.promptTokens ?: 0
        val outputTokens = usage.outputTokens ?: usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟢 Anthropic token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "anthropic",
            supportsFileTokens = true
        )
    }
    
    /**
     * Google models (Gemini, Gemma)
     * Uses different field names and sometimes inconsistent reporting
     */
    private fun extractGoogleTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        // Google may use different field names
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: usage.contextTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: usage.generatedTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟡 Google token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "google",
            supportsFileTokens = false
        )
    }
    
    /**
     * Meta Llama models
     * Basic token counting, limited file support
     */
    private fun extractMetaTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟡 Meta token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "meta",
            supportsFileTokens = false
        )
    }
    
    /**
     * Cohere models
     * May use different response structure
     */
    private fun extractCohereTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.inputTokens ?: usage.promptTokens ?: 0
        val outputTokens = usage.outputTokens ?: usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🔴 Cohere token extraction: in=$inputTokens, out=$outputTokens (limited file token breakdown)")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "cohere",
            supportsFileTokens = false
        )
    }
    
    /**
     * DeepSeek models
     * Basic automatic counting
     */
    private fun extractDeepSeekTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟡 DeepSeek token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "deepseek",
            supportsFileTokens = false
        )
    }
    
    /**
     * Qwen models
     * Basic automatic counting
     */
    private fun extractQwenTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.inputTokens ?: usage.promptTokens ?: 0
        val outputTokens = usage.outputTokens ?: usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟡 Qwen token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "qwen",
            supportsFileTokens = false
        )
    }
    
    /**
     * xAI Grok models
     * Good automatic token counting
     */
    private fun extractXAITokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: 0
        val outputTokens = usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟢 xAI token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "xai",
            supportsFileTokens = true
        )
    }
    
    /**
     * Mistral models (mainly OCR)
     * Special case - mainly output tokens for extracted content
     */
    private fun extractMistralTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: 0  // Often 0 for OCR
        val outputTokens = usage.completionTokens ?: 0  // Large for extracted content
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🔴 Mistral OCR token extraction: in=$inputTokens, out=$outputTokens (OCR special case)")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "mistral",
            supportsFileTokens = false,
            isOcrModel = true
        )
    }
    
    /**
     * Perplexity models
     * Web search focused, basic token counting
     */
    private fun extractPerplexityTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: 0
        val outputTokens = usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟡 Perplexity token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "perplexity",
            supportsFileTokens = false
        )
    }
    
    /**
     * ZhiPu GLM models
     * Basic automatic counting
     */
    private fun extractZhiPuTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("🟡 ZhiPu token extraction: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "zhipu",
            supportsFileTokens = false
        )
    }
    
    /**
     * Generic fallback for unknown providers
     */
    private fun extractGenericTokens(usage: AimlApiResponse.Usage, modelId: String): TokenUsage {
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: usage.contextTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: usage.generatedTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        Timber.d("⚪ Generic token extraction for $modelId: in=$inputTokens, out=$outputTokens")
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            provider = "unknown",
            supportsFileTokens = false
        )
    }
    
    /**
     * Get provider name from model ID
     */
    private fun getProviderFromModelId(modelId: String): String {
        return ModelConfigManager.getConfig(modelId)?.provider ?: "unknown"
    }
    
    /**
     * Check if a provider supports detailed file token breakdown
     */
    fun supportsFileTokenBreakdown(modelId: String): Boolean {
        val provider = getProviderFromModelId(modelId)
        return provider in listOf("openai", "anthropic", "xai")
    }
    
    /**
     * Standardized token usage data class
     */
    data class TokenUsage(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        val reasoningTokens: Int = 0,
        val provider: String = "unknown",
        val supportsFileTokens: Boolean = false,
        val isOcrModel: Boolean = false
    ) {
        
        /**
         * Check if token data is valid/available
         */
        fun hasValidData(): Boolean = totalTokens > 0 || (inputTokens > 0 && outputTokens > 0)
        
        /**
         * Get formatted display text for UI
         */
        fun getDisplayText(): String {
            return when {
                !hasValidData() -> "📊 No token data"
                reasoningTokens > 0 -> "📊 ${inputTokens.format()} in • ${outputTokens.format()} out • ${reasoningTokens.format()} thinking • ${totalTokens.format()} total"
                totalTokens > 0 -> "📊 ${inputTokens.format()} in • ${outputTokens.format()} out • ${totalTokens.format()} total"
                else -> "📊 ${inputTokens.format()} in • ${outputTokens.format()} out"
            }
        }
        
        /**
         * Get simple summary for logging
         */
        fun getSummary(): String {
            return "Tokens: $inputTokens in + $outputTokens out = $totalTokens total [$provider]"
        }
        
        /**
         * Format large numbers with k/M suffixes
         */
        private fun Int.format(): String {
            return when {
                this >= 1_000_000 -> "${this / 1_000_000}M"
                this >= 1_000 -> "${this / 1_000}k"
                else -> toString()
            }
        }
        
        /**
         * Get provider-specific note for UI
         */
        fun getProviderNote(): String {
            return when {
                isOcrModel -> "OCR model - tokens represent extracted content"
                supportsFileTokens -> "File content included in input tokens"
                provider in listOf("cohere", "google", "meta") -> "File token breakdown not available"
                else -> "Basic token counting"
            }
        }
        
        companion object {
            fun empty() = TokenUsage(0, 0, 0, provider = "none")
        }
    }
    
    /**
     * Analyze file token impact (works only with supported providers)
     */
    fun analyzeFileTokenImpact(
        tokenUsage: TokenUsage,
        messageText: String,
        fileCount: Int
    ): FileTokenAnalysis {
        if (!tokenUsage.supportsFileTokens || fileCount == 0) {
            return FileTokenAnalysis(
                hasFileTokens = false,
                estimatedFileTokens = 0,
                estimatedTextTokens = 0,
                totalInputTokens = tokenUsage.inputTokens,
                note = "Provider doesn't support file token breakdown"
            )
        }
        
        // Estimate message text tokens (rough calculation)
        val estimatedTextTokens = TokenValidator.getAccurateTokenCount(messageText, "gpt-4o")
        
        // Calculate estimated file tokens
        val estimatedFileTokens = maxOf(0, tokenUsage.inputTokens - estimatedTextTokens)
        
        return FileTokenAnalysis(
            hasFileTokens = true,
            estimatedFileTokens = estimatedFileTokens,
            estimatedTextTokens = estimatedTextTokens,
            totalInputTokens = tokenUsage.inputTokens,
            note = "File tokens estimated from input total"
        )
    }
    
    data class FileTokenAnalysis(
        val hasFileTokens: Boolean,
        val estimatedFileTokens: Int,
        val estimatedTextTokens: Int,
        val totalInputTokens: Int,
        val note: String
    ) {
        fun getAnalysisText(): String {
            return if (hasFileTokens) {
                """
                📄 FILE TOKEN ANALYSIS:
                ├── Total Input: ${totalInputTokens.format()}
                ├── Text Est.: ${estimatedTextTokens.format()}
                ├── File Est.: ${estimatedFileTokens.format()}
                └── Note: $note
                """.trimIndent()
            } else {
                "📄 File token analysis not available: $note"
            }
        }
        
        private fun Int.format(): String {
            return when {
                this >= 1_000_000 -> "${this / 1_000_000}M"
                this >= 1_000 -> "${this / 1_000}k"
                else -> toString()
            }
        }
    }
}