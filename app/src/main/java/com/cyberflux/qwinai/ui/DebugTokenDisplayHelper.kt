package com.cyberflux.qwinai.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.utils.TokenValidator
import timber.log.Timber

/**
 * Debug-only helper for displaying detailed token usage information
 * 
 * Shows comprehensive token consumption data for debug APK:
 * - Input/Output tokens from API
 * - Total tokens consumed
 * - Cost calculations
 * - Usage limits and remaining quotas
 * 
 * Only active in debug builds!
 */
class DebugTokenDisplayHelper(private val context: Context) {
    
    private var debugOverlay: CardView? = null
    private var debugTextView: TextView? = null
    private var isVisible = false
    
    companion object {
        private const val DEBUG_PREFS = "debug_token_display"
        private const val SHOW_DEBUG_OVERLAY_KEY = "show_debug_overlay"
        
        // Token cost estimates (for educational purposes)
        private const val COST_PER_1K_INPUT_TOKENS = 0.01 // $0.01 per 1K input tokens (example)
        private const val COST_PER_1K_OUTPUT_TOKENS = 0.03 // $0.03 per 1K output tokens (example)
    }
    
    /**
     * Create and show debug token overlay (DEBUG builds only)
     */
    fun createDebugOverlay(parentView: LinearLayout) {
        if (!BuildConfig.DEBUG) return
        
        try {
            // Remove existing overlay
            removeDebugOverlay(parentView)
            
            // Create debug card
            debugOverlay = CardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 8, 16, 8)
                }
                
                cardElevation = 4f
                radius = 8f
                setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
                alpha = 0.9f
                
                // Click to toggle visibility
                setOnClickListener { toggleDebugOverlay() }
                setOnLongClickListener { 
                    clearDebugData()
                    true
                }
            }
            
            // Create debug text view
            debugTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(12, 12, 12, 12)
                }
                
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.GREEN)
                text = "🔧 DEBUG TOKEN TRACKER\nTap to toggle | Long press to clear"
                gravity = Gravity.START
                maxLines = 20
                
                // Make text selectable for copying
                setTextIsSelectable(true)
            }
            
            debugOverlay?.addView(debugTextView)
            parentView.addView(debugOverlay)
            
            // Restore visibility state
            val showOverlay = context.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE)
                .getBoolean(SHOW_DEBUG_OVERLAY_KEY, true)
            setDebugOverlayVisibility(showOverlay)
            
            Timber.d("🔧 Debug token overlay created")
            
        } catch (e: Exception) {
            Timber.e(e, "Error creating debug token overlay")
        }
    }
    
    /**
     * Update debug overlay with token information
     */
    fun updateDebugTokenInfo(
        modelId: String,
        apiResponse: AimlApiResponse?,
        inputText: String = "",
        outputText: String = "",
        isSubscribed: Boolean = false
    ) {
        if (!BuildConfig.DEBUG || debugTextView == null) return
        
        try {
            val usage = apiResponse?.usage
            val inputTokens = usage?.promptTokens ?: TokenValidator.estimateTokenCount(inputText, modelId)
            val outputTokens = usage?.completionTokens ?: TokenValidator.estimateTokenCount(outputText, modelId)
            val totalTokens = usage?.totalTokens ?: (inputTokens + outputTokens)
            
            // Calculate costs (educational)
            val inputCost = (inputTokens / 1000.0) * COST_PER_1K_INPUT_TOKENS
            val outputCost = (outputTokens / 1000.0) * COST_PER_1K_OUTPUT_TOKENS
            val totalCost = inputCost + outputCost
            
            val debugText = createDebugTokenText(
                modelId = modelId,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = totalTokens,
                inputCost = inputCost,
                outputCost = outputCost,
                totalCost = totalCost,
                isSubscribed = isSubscribed,
                hasRealUsage = usage != null
            )
            
            debugTextView?.text = debugText
            
            // Log for debugging
            Timber.d("🔧 Updated debug token info: $inputTokens in, $outputTokens out, $totalTokens total")
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating debug token info")
        }
    }
    
    /**
     * Create formatted debug text with token information
     */
    private fun createDebugTokenText(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        inputCost: Double,
        outputCost: Double,
        totalCost: Double,
        isSubscribed: Boolean,
        hasRealUsage: Boolean
    ): SpannableString {
        val text = buildString {
            appendLine("🔧 DEBUG TOKEN TRACKER")
            appendLine("═" .repeat(25))
            
            // Model info
            appendLine("Model: $modelId")
            appendLine("Status: ${if (isSubscribed) "PREMIUM ⭐" else "FREE 🆓"}")
            appendLine("Source: ${if (hasRealUsage) "API Response ✓" else "Estimated ~"}")
            appendLine()
            
            // Token breakdown
            appendLine("TOKEN USAGE:")
            appendLine("Input:  ${formatTokens(inputTokens)}")
            appendLine("Output: ${formatTokens(outputTokens)}")
            appendLine("Total:  ${formatTokens(totalTokens)}")
            appendLine()
            
            // Cost breakdown (educational)
            appendLine("ESTIMATED COST:")
            appendLine("Input:  $${String.format("%.4f", inputCost)}")
            appendLine("Output: $${String.format("%.4f", outputCost)}")
            appendLine("Total:  $${String.format("%.4f", totalCost)}")
            appendLine()
            
            // Usage limits
            val inputLimit = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
            val outputLimit = TokenValidator.getEffectiveMaxOutputTokens(modelId, isSubscribed)
            
            appendLine("TOKEN LIMITS:")
            appendLine("Input:  ${formatTokens(inputTokens)}/${formatTokens(inputLimit)} (${getUsagePercentage(inputTokens, inputLimit)}%)")
            appendLine("Output: ${formatTokens(outputTokens)}/${formatTokens(outputLimit)} (${getUsagePercentage(outputTokens, outputLimit)}%)")
            appendLine()
            
            // Performance metrics
            val timestamp = System.currentTimeMillis()
            appendLine("Updated: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(timestamp)}")
            appendLine("Tap to toggle | Long press to clear")
        }
        
        return SpannableString(text).apply {
            // Color coding
            colorText(this, "🔧 DEBUG TOKEN TRACKER", Color.CYAN)
            colorText(this, "PREMIUM ⭐", Color.rgb(255, 215, 0)) // Gold
            colorText(this, "FREE 🆓", Color.YELLOW)
            colorText(this, "API Response ✓", Color.GREEN)
            colorText(this, "Estimated ~", Color.rgb(255, 165, 0)) // Orange
            
            // Highlight high usage
            val inputLimitValue = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
            val outputLimitValue = TokenValidator.getEffectiveMaxOutputTokens(modelId, isSubscribed)
            
            if (getUsagePercentage(inputTokens, inputLimitValue) > 80) {
                colorText(this, "${getUsagePercentage(inputTokens, inputLimitValue)}%", Color.RED)
            }
            if (getUsagePercentage(outputTokens, outputLimitValue) > 80) {
                colorText(this, "${getUsagePercentage(outputTokens, outputLimitValue)}%", Color.RED)
            }
        }
    }
    
    /**
     * Format token count for display
     */
    private fun formatTokens(tokens: Int): String {
        return when {
            tokens >= 1000000 -> String.format("%.1fM", tokens / 1000000.0)
            tokens >= 1000 -> String.format("%.1fK", tokens / 1000.0)
            else -> tokens.toString()
        }
    }
    
    /**
     * Calculate usage percentage
     */
    private fun getUsagePercentage(used: Int, limit: Int): Int {
        return if (limit > 0) ((used.toDouble() / limit) * 100).toInt() else 0
    }
    
    /**
     * Color specific text in SpannableString
     */
    private fun colorText(spannable: SpannableString, text: String, color: Int) {
        val fullText = spannable.toString()
        var startIndex = 0
        while (true) {
            val index = fullText.indexOf(text, startIndex)
            if (index == -1) break
            
            spannable.setSpan(
                ForegroundColorSpan(color),
                index, index + text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Make important text bold
            if (text.contains("DEBUG") || text.contains("PREMIUM") || text.contains("FREE")) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    index, index + text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            startIndex = index + 1
        }
    }
    
    /**
     * Toggle debug overlay visibility
     */
    private fun toggleDebugOverlay() {
        isVisible = !isVisible
        setDebugOverlayVisibility(isVisible)
        
        // Save state
        context.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SHOW_DEBUG_OVERLAY_KEY, isVisible)
            .apply()
    }
    
    /**
     * Set debug overlay visibility
     */
    private fun setDebugOverlayVisibility(visible: Boolean) {
        debugOverlay?.visibility = if (visible) View.VISIBLE else View.GONE
        isVisible = visible
    }
    
    /**
     * Clear debug data
     */
    private fun clearDebugData() {
        debugTextView?.text = "🔧 DEBUG TOKEN TRACKER\nData cleared. Make a request to see token usage."
        Timber.d("🔧 Debug token data cleared")
    }
    
    /**
     * Remove debug overlay
     */
    fun removeDebugOverlay(parentView: LinearLayout) {
        if (!BuildConfig.DEBUG) return
        
        try {
            debugOverlay?.let { overlay ->
                (overlay.parent as? LinearLayout)?.removeView(overlay)
            }
            debugOverlay = null
            debugTextView = null
            Timber.d("🔧 Debug token overlay removed")
        } catch (e: Exception) {
            Timber.e(e, "Error removing debug token overlay")
        }
    }
    
    /**
     * Check if debug overlay is available
     */
    fun isDebugOverlayAvailable(): Boolean {
        return BuildConfig.DEBUG && debugOverlay != null
    }
    
    /**
     * Get debug overlay visibility state
     */
    fun isDebugOverlayVisible(): Boolean {
        return isVisible
    }
}