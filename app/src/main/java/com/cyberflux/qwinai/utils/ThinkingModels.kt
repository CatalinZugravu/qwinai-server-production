package com.cyberflux.qwinai.utils

import androidx.core.graphics.toColorInt

object ThinkingModels {

    /**
     * All thinking/reasoning functionality has been removed.
     * This object is kept for compatibility but returns default values.
     */

    /**
     * Always returns false - no models support thinking in this version
     */
    fun supportsThinking(modelId: String): Boolean {
        return false
    }

    /**
     * Always returns false - no Claude thinking support
     */
    fun supportsClaudeThinking(modelId: String): Boolean {
        return false
    }

    /**
     * Always returns null - no Claude thinking config
     */
    fun getClaudeThinkingConfig(reasoningLevel: String, enabled: Boolean): Any? {
        return null
    }

    /**
     * Always returns false - no Claude thinking models
     */
    fun isClaudeThinkingModel(modelId: String): Boolean {
        return false
    }

    /**
     * Always returns false - no O1 thinking models
     */
    fun isO1ThinkingModel(modelId: String): Boolean {
        return false
    }

    /**
     * Always returns false - no built-in thinking
     */
    fun hasBuiltInThinking(modelId: String): Boolean {
        return false
    }

    /**
     * Always returns default tokens
     */
    fun getClaudeThinkingBudgetTokens(reasoningLevel: String): Int {
        return 4096
    }

    /**
     * Always returns false - no Claude thinking usage
     */
    fun shouldUseClaudeThinking(modelId: String, thinkingEnabled: Boolean): Boolean {
        return false
    }

    /**
     * Always returns null - no forced thinking levels
     */
    fun getForcedThinkingLevel(modelId: String): String? {
        return null
    }

    /**
     * Get the color for web search indicators (kept for web search functionality)
     */
    fun getWebSearchColor(): Int {
        return "#2563EB".toColorInt() // Blue for web search
    }

    /**
     * Default color for any remaining thinking references
     */
    fun getThinkingColor(modelId: String): Int {
        return "#6366F1".toColorInt() // Default purple
    }

    /**
     * Default thinking indicator text (should not be used)
     */
    fun getThinkingIndicatorText(modelId: String): String {
        return "Processing..."
    }
}