package com.cyberflux.qwinai.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Debug menu helper for easy access to debug features
 * 
 * Provides:
 * - Subscription toggle
 * - Usage limits testing
 * - Token tracking
 * - System status
 * 
 * Only visible in debug builds!
 */
object DebugMenuHelper {
    
    /**
     * Create debug button that appears only in debug builds
     */
    fun createDebugButton(context: Context, parentLayout: LinearLayout): Button? {
        if (!BuildConfig.DEBUG) return null
        
        return try {
            Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 4, 8, 4)
                }
                
                text = "🔧 DEBUG"
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                alpha = 0.8f
                
                setOnClickListener { showDebugMenu(context) }
                
                // Add to parent layout
                parentLayout.addView(this)
                
                Timber.d("🔧 Debug button created")
                this
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating debug button")
            null
        }
    }
    
    /**
     * Show comprehensive debug menu
     */
    private fun showDebugMenu(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        val items = arrayOf(
            "🔐 Toggle Subscription",
            "📊 Show Usage Status", 
            "🧪 Test Usage Limits",
            "💳 Show Credit Status",
            "🎯 Reset Debug Settings",
            "📋 Export Debug Info",
            "🔍 System Diagnostics"
        )
        
        AlertDialog.Builder(context)
            .setTitle("🔧 Debug Menu")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleSubscription(context)
                    1 -> showUsageStatus(context)
                    2 -> testUsageLimits(context)
                    3 -> showCreditStatus(context)
                    4 -> resetDebugSettings(context)
                    5 -> exportDebugInfo(context)
                    6 -> showSystemDiagnostics(context)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Toggle subscription status
     */
    private fun toggleSubscription(context: Context) {
        val newStatus = DebugSubscriptionHelper.toggleDebugSubscription(context)
        
        // Show confirmation with details
        val message = if (newStatus) {
            """
            ✅ DEBUG SUBSCRIPTION ENABLED
            
            You now have access to:
            • Unlimited token limits
            • 30 requests/day for limited models
            • 25 image generations/day
            • No credit consumption
            
            This is a debug override only!
            """.trimIndent()
        } else {
            """
            ❌ DEBUG SUBSCRIPTION DISABLED
            
            You are now using free tier limits:
            • 1000 input / 1500 output tokens
            • Credit-based model access
            • Limited features
            
            Real subscription unaffected.
            """.trimIndent()
        }
        
        AlertDialog.Builder(context)
            .setTitle("Debug Subscription Changed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Show usage status for all model categories
     */
    private fun showUsageStatus(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            val usageStatus = DebugSubscriptionHelper.testUsageLimits(context)
            showScrollableDialog(context, "📊 Usage Status", usageStatus)
        }
    }
    
    /**
     * Test usage limits with sample requests
     */
    private fun testUsageLimits(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            val unifiedUsageManager = UnifiedUsageManager.getInstance(context)
            val isSubscribed = PrefsManager.isSubscribed(context)
            
            val results = buildString {
                appendLine("🧪 TESTING USAGE LIMITS")
                appendLine("Subscription: ${if (isSubscribed) "PREMIUM ⭐" else "FREE 🆓"}")
                appendLine("=" .repeat(30))
                appendLine()
                
                val testCases = listOf(
                    "gpt-4o" to "Short test prompt",
                    "gpt-4o" to "This is a much longer test prompt that should consume significantly more tokens and potentially trigger different validation behaviors depending on the user's subscription status and current usage limits.",
                    "gpt-4-turbo" to "Unlimited premium model test",
                    "cohere/command-r-plus" to "Free model test",
                    "dall-e-3" to "Beautiful sunset landscape"
                )
                
                for ((modelId, prompt) in testCases) {
                    appendLine("Testing: $modelId")
                    appendLine("Prompt: ${prompt.take(50)}${if (prompt.length > 50) "..." else ""}")
                    
                    val validation = unifiedUsageManager.validateRequest(modelId, prompt, isSubscribed)
                    val summary = unifiedUsageManager.getUsageSummary(modelId, isSubscribed)
                    
                    appendLine("Result: ${if (validation.isAllowed) "✅ ALLOWED" else "❌ BLOCKED"}")
                    if (!validation.isAllowed) {
                        appendLine("Reason: ${validation.reason}")
                    }
                    appendLine("Category: ${summary.usageCategory}")
                    appendLine("Tokens: ${summary.tokenLimits.maxInputTokens}in/${summary.tokenLimits.maxOutputTokens}out")
                    appendLine()
                }
            }
            
            showScrollableDialog(context, "🧪 Usage Limits Test", results)
        }
    }
    
    /**
     * Show credit status
     */
    private fun showCreditStatus(context: Context) {
        val creditManager = com.cyberflux.qwinai.credits.CreditManager.getInstance(context)
        val debugInfo = creditManager.getDebugInfo()
        
        showScrollableDialog(context, "💳 Credit Status", debugInfo)
    }
    
    /**
     * Reset all debug settings
     */
    private fun resetDebugSettings(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Reset Debug Settings")
            .setMessage("This will reset all debug overrides and return to normal app behavior. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                DebugSubscriptionHelper.resetDebugSettings(context)
                
                // Also reset other debug settings
                context.getSharedPreferences("debug_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                context.getSharedPreferences("debug_token_display", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                
                AlertDialog.Builder(context)
                    .setTitle("Settings Reset")
                    .setMessage("All debug settings have been reset to defaults.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Export debug information
     */
    private fun exportDebugInfo(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            val debugInfo = buildString {
                appendLine("🔧 COMPLETE DEBUG EXPORT")
                appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(System.currentTimeMillis())}")
                appendLine("=" .repeat(40))
                appendLine()
                
                // Subscription info
                appendLine(DebugSubscriptionHelper.getDebugInfo(context))
                appendLine()
                
                // Usage manager info
                val unifiedUsageManager = UnifiedUsageManager.getInstance(context)
                appendLine(unifiedUsageManager.getDebugInfo())
                appendLine()
                
                // Credit system info
                val creditManager = com.cyberflux.qwinai.credits.CreditManager.getInstance(context)
                appendLine(creditManager.getDebugInfo())
                appendLine()
                
                // Test results
                appendLine(DebugSubscriptionHelper.testUsageLimits(context))
            }
            
            showScrollableDialog(context, "📋 Complete Debug Export", debugInfo, true)
        }
    }
    
    /**
     * Show system diagnostics
     */
    private fun showSystemDiagnostics(context: Context) {
        val diagnostics = buildString {
            appendLine("🔍 SYSTEM DIAGNOSTICS")
            appendLine("=" .repeat(25))
            appendLine()
            
            appendLine("Build Info:")
            appendLine("Debug Mode: ${BuildConfig.DEBUG}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            
            appendLine("Memory Info:")
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024
            val usedMemory = totalMemory - freeMemory
            
            appendLine("Max Memory: ${maxMemory}MB")
            appendLine("Used Memory: ${usedMemory}MB")
            appendLine("Free Memory: ${freeMemory}MB")
            appendLine()
            
            appendLine("Storage Info:")
            try {
                val internalDir = context.filesDir
                val totalSpace = internalDir.totalSpace / 1024 / 1024
                val freeSpace = internalDir.freeSpace / 1024 / 1024
                val usedSpace = totalSpace - freeSpace
                
                appendLine("Total Internal: ${totalSpace}MB")
                appendLine("Used Internal: ${usedSpace}MB")
                appendLine("Free Internal: ${freeSpace}MB")
            } catch (e: Exception) {
                appendLine("Storage info unavailable: ${e.message}")
            }
            
            appendLine()
            appendLine("Workers Status:")
            appendLine("Usage Limit Reset: Scheduled")
            appendLine("Credit Reset: Scheduled")
            appendLine("Conversation Cleanup: Scheduled")
        }
        
        showScrollableDialog(context, "🔍 System Diagnostics", diagnostics)
    }
    
    /**
     * Show scrollable dialog with large text content
     */
    private fun showScrollableDialog(
        context: Context, 
        title: String, 
        content: String, 
        selectable: Boolean = false
    ) {
        val scrollView = ScrollView(context)
        val textView = TextView(context).apply {
            text = content
            textSize = 12f
            setPadding(16, 16, 16, 16)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(selectable)
        }
        
        scrollView.addView(textView)
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(title, content)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "Error copying to clipboard")
                }
            }
            .show()
    }
}