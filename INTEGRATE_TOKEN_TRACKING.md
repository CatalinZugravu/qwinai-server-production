# How to Integrate Token Tracking in Your App

## Step-by-Step Integration Guide

Based on your current codebase, here's exactly how to add token tracking for PDF files:

## **1. Modify StreamingHandler.kt**

Add token usage processing to your streaming handler:

```kotlin
// Add this to StreamingHandler.kt
private fun processTokenUsageFromStream(chunk: String) {
    try {
        // Look for the final chunk with usage information
        if (chunk.contains("\"usage\"") && chunk.contains("\"prompt_tokens\"")) {
            val jsonData = chunk.removePrefix("data: ").trim()
            val jsonObject = JSONObject(jsonData)
            
            if (jsonObject.has("usage")) {
                val usageObject = jsonObject.getJSONObject("usage")
                
                val inputTokens = usageObject.optInt("prompt_tokens", 0)
                val outputTokens = usageObject.optInt("completion_tokens", 0) 
                val totalTokens = usageObject.optInt("total_tokens", 0)
                
                Timber.i("📊 STREAMING TOKEN USAGE: Input=$inputTokens, Output=$outputTokens, Total=$totalTokens")
                
                // Update UI on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    updateTokenDisplayInUI(inputTokens, outputTokens, totalTokens)
                }
                
                // Store token usage
                storeTokenUsageData(inputTokens, outputTokens, totalTokens)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse token usage from streaming chunk")
    }
}

// Add this helper function
private fun updateTokenDisplayInUI(inputTokens: Int, outputTokens: Int, totalTokens: Int) {
    // Find your token display TextView (you'll need to pass context/activity reference)
    // Example: 
    // MainActivity.instance?.updateTokenCounter(inputTokens, outputTokens, totalTokens)
}

// Add this helper function  
private fun storeTokenUsageData(inputTokens: Int, outputTokens: Int, totalTokens: Int) {
    // Store in SharedPreferences or Database for tracking
    // Example implementation in next section
}
```

## **2. Add to MainActivity.kt**

Add token display and tracking to your main activity:

```kotlin
// Add these properties to MainActivity
private var currentInputTokens = 0
private var currentOutputTokens = 0  
private var currentTotalTokens = 0

// Add this method to MainActivity
fun updateTokenCounter(inputTokens: Int, outputTokens: Int, totalTokens: Int) {
    currentInputTokens = inputTokens
    currentOutputTokens = outputTokens  
    currentTotalTokens = totalTokens
    
    // Update your token counter TextView
    val tokenText = when {
        totalTokens > 0 -> "📊 ${formatTokenCount(inputTokens)} in • ${formatTokenCount(outputTokens)} out • ${formatTokenCount(totalTokens)} total"
        else -> "📊 Ready"
    }
    
    // Find your token display TextView (adjust selector to match your layout)
    findViewById<TextView>(R.id.token_counter)?.apply {
        text = tokenText
        
        // Color code based on usage
        val color = when {
            totalTokens > 100_000 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
            totalTokens > 50_000 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)
        }
        setTextColor(color)
    }
}

// Helper to format large token counts  
private fun formatTokenCount(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
        tokens >= 1_000 -> "${tokens / 1_000}k"
        else -> tokens.toString()
    }
}

// Add this when sending messages with files
private fun analyzeFileTokenImpact(fileCount: Int, messageText: String) {
    if (fileCount > 0 && currentInputTokens > 0) {
        val estimatedTextTokens = messageText.length / 4  // Rough estimate
        val estimatedFileTokens = maxOf(0, currentInputTokens - estimatedTextTokens)
        
        Timber.i("""
            📄 FILE TOKEN BREAKDOWN:
            ├── Total Input Tokens: ${formatTokenCount(currentInputTokens)}
            ├── Message Text: ~${formatTokenCount(estimatedTextTokens)} tokens
            ├── File Content: ~${formatTokenCount(estimatedFileTokens)} tokens  
            └── Files Uploaded: $fileCount
            
            💡 Your PDF consumed ~${formatTokenCount(estimatedFileTokens)} tokens!
        """.trimIndent())
    }
}
```

## **3. Create Token Storage System**

Create a simple token usage tracker:

```kotlin
// Create TokenUsageStorage.kt
package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

object TokenUsageStorage {
    
    private const val PREFS_NAME = "token_usage_prefs"
    private const val KEY_TOTAL_INPUT_TOKENS = "total_input_tokens"
    private const val KEY_TOTAL_OUTPUT_TOKENS = "total_output_tokens" 
    private const val KEY_TOTAL_TOKENS = "total_tokens"
    private const val KEY_FILE_TOKENS = "file_tokens"
    private const val KEY_LAST_RESET = "last_reset"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Store token usage from latest API response
     */
    fun storeTokenUsage(
        context: Context,
        inputTokens: Int,
        outputTokens: Int, 
        totalTokens: Int,
        hasFiles: Boolean = false
    ) {
        val prefs = getPrefs(context)
        val currentTotal = prefs.getLong(KEY_TOTAL_TOKENS, 0L)
        val currentFileTokens = prefs.getLong(KEY_FILE_TOKENS, 0L)
        
        prefs.edit().apply {
            // Store latest usage
            putInt("last_input_tokens", inputTokens)
            putInt("last_output_tokens", outputTokens)
            putInt("last_total_tokens", totalTokens)
            
            // Accumulate total usage
            putLong(KEY_TOTAL_INPUT_TOKENS, prefs.getLong(KEY_TOTAL_INPUT_TOKENS, 0L) + inputTokens)
            putLong(KEY_TOTAL_OUTPUT_TOKENS, prefs.getLong(KEY_TOTAL_OUTPUT_TOKENS, 0L) + outputTokens)
            putLong(KEY_TOTAL_TOKENS, currentTotal + totalTokens)
            
            // Track file tokens separately
            if (hasFiles) {
                putLong(KEY_FILE_TOKENS, currentFileTokens + inputTokens)
            }
            
            putLong("last_usage_timestamp", System.currentTimeMillis())
            apply()
        }
        
        Timber.d("💾 Stored token usage: $inputTokens in, $outputTokens out, $totalTokens total")
    }
    
    /**
     * Get cumulative token usage statistics  
     */
    fun getUsageStats(context: Context): TokenUsageStats {
        val prefs = getPrefs(context)
        
        return TokenUsageStats(
            totalInputTokens = prefs.getLong(KEY_TOTAL_INPUT_TOKENS, 0L),
            totalOutputTokens = prefs.getLong(KEY_TOTAL_OUTPUT_TOKENS, 0L),
            totalTokens = prefs.getLong(KEY_TOTAL_TOKENS, 0L),
            fileTokens = prefs.getLong(KEY_FILE_TOKENS, 0L),
            lastUsageTimestamp = prefs.getLong("last_usage_timestamp", 0L)
        )
    }
    
    /**
     * Get latest single request token usage
     */
    fun getLatestUsage(context: Context): TokenUsageStats {
        val prefs = getPrefs(context)
        
        return TokenUsageStats(
            totalInputTokens = prefs.getInt("last_input_tokens", 0).toLong(),
            totalOutputTokens = prefs.getInt("last_output_tokens", 0).toLong(), 
            totalTokens = prefs.getInt("last_total_tokens", 0).toLong(),
            fileTokens = 0L, // Not tracked per request
            lastUsageTimestamp = prefs.getLong("last_usage_timestamp", 0L)
        )
    }
    
    data class TokenUsageStats(
        val totalInputTokens: Long,
        val totalOutputTokens: Long,
        val totalTokens: Long,
        val fileTokens: Long,
        val lastUsageTimestamp: Long
    ) {
        fun getUsageSummary(): String {
            return "Total: ${formatTokens(totalTokens)} • Files: ${formatTokens(fileTokens)}"
        }
        
        private fun formatTokens(tokens: Long): String {
            return when {
                tokens >= 1_000_000L -> "${tokens / 1_000_000L}M"
                tokens >= 1_000L -> "${tokens / 1_000L}k"
                else -> tokens.toString()
            }
        }
    }
}
```

## **4. Update Your Layout**

Add a token counter to your activity layout:

```xml
<!-- Add this to your activity_main.xml or wherever appropriate -->
<TextView
    android:id="@+id/token_counter"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="📊 Ready"
    android:textSize="12sp"
    android:textColor="@color/secondary_text_color"
    android:layout_marginTop="4dp"
    android:layout_marginEnd="8dp"
    android:fontFamily="monospace"
    app:layout_constraintTop_toBottomOf="@id/your_input_field"
    app:layout_constraintEnd_toEndOf="parent" />
```

## **5. Integration Points**

Modify these key points in your existing code:

### **A. In your API response handler:**
```kotlin
// When you receive API response
fun handleApiResponse(response: AimlApiResponse, hasFiles: Boolean) {
    // Your existing response processing...
    
    // Add token usage processing
    response.usage?.let { usage ->
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        // Update UI
        updateTokenCounter(inputTokens, outputTokens, totalTokens)
        
        // Store for tracking
        TokenUsageStorage.storeTokenUsage(this, inputTokens, outputTokens, totalTokens, hasFiles)
        
        // Analyze file impact
        if (hasFiles) {
            analyzeFileTokenImpact(fileCount, lastMessageText)
        }
    }
}
```

### **B. In your streaming handler:**
```kotlin
// Add this call in your existing streaming processing
fun processStreamingChunk(chunk: String) {
    // Your existing streaming logic...
    
    // Add token processing
    processTokenUsageFromStream(chunk)  // Add this line
}
```

## **6. Usage Analytics Dashboard**

Create a simple analytics view:

```kotlin
// Add this method to show usage stats
fun showTokenUsageStats() {
    val stats = TokenUsageStorage.getUsageStats(this)
    
    val message = """
        📊 TOKEN USAGE STATISTICS
        
        Total Tokens Used: ${stats.totalTokens.formatNumber()}
        ├── Input: ${stats.totalInputTokens.formatNumber()}
        └── Output: ${stats.totalOutputTokens.formatNumber()}
        
        File Tokens Used: ${stats.fileTokens.formatNumber()}
        
        🔍 File tokens represent content from your PDFs!
    """.trimIndent()
    
    AlertDialog.Builder(this)
        .setTitle("Token Usage")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

private fun Long.formatNumber(): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(this)
}
```

This integration will give you complete visibility into token usage when PDFs are involved! 🚀

The key insight is that **PDF tokens show up in the `prompt_tokens` field** of the API response, combined with your message text tokens. The examples above show you exactly how to access and track this information.