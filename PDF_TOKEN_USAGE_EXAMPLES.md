# PDF Token Usage Examples

## Real API Response Examples

### **Example 1: Small PDF (5 pages)**
```json
{
  "id": "chatcmpl-ABC123",
  "object": "chat.completion",
  "created": 1709567890,
  "model": "gpt-4o",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "I've analyzed the PDF document. It contains information about..."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 3247,     // ← Your text (47) + PDF content (3200)
    "completion_tokens": 156,  // ← AI response 
    "total_tokens": 3403      // ← Total: 3247 + 156
  }
}
```

### **Example 2: Large PDF (50 pages)**
```json
{
  "id": "chatcmpl-DEF456",
  "object": "chat.completion",
  "created": 1709567895,
  "model": "gpt-4o",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant", 
      "content": "This comprehensive document covers multiple topics..."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 28567,    // ← Your text (67) + PDF content (28500)
    "completion_tokens": 423,  // ← AI response
    "total_tokens": 28990     // ← Total: 28567 + 423
  }
}
```

### **Example 3: Multiple Files (2 PDFs + 1 Image)**
```json
{
  "id": "chatcmpl-GHI789",
  "object": "chat.completion", 
  "created": 1709567900,
  "model": "gpt-4o",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "Comparing the two PDF documents and the chart image..."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 45234,    // ← Text + PDF1 + PDF2 + Image tokens
    "completion_tokens": 678,  // ← AI response
    "total_tokens": 45912     // ← Total
  }
}
```

### **Example 4: Streaming Response (Final Chunk with Usage)**
```
data: {"id":"chatcmpl-JKL012","object":"chat.completion.chunk","created":1709567905,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":15420,"completion_tokens":892,"total_tokens":16312}}
```

## **Token Breakdown Analysis**

### **Small PDF (1-10 pages)**
- **Typical Range**: 500 - 5,000 tokens
- **Example**: 5-page report = ~2,500 tokens  
- **Context**: Uses ~2% of GPT-4o's 128k context window

### **Medium PDF (10-50 pages)**
- **Typical Range**: 5,000 - 25,000 tokens
- **Example**: 25-page manual = ~12,500 tokens
- **Context**: Uses ~10% of GPT-4o's 128k context window

### **Large PDF (50+ pages)**  
- **Typical Range**: 25,000 - 100,000+ tokens
- **Example**: 100-page book = ~50,000 tokens
- **Context**: Uses ~40% of GPT-4o's 128k context window
- **Warning**: May approach model limits!

## **Implementation in Your App**

### **1. In Non-Streaming Responses**
```kotlin
// In your API response handler
fun handleNonStreamingResponse(response: AimlApiResponse) {
    // Extract token usage
    response.usage?.let { usage ->
        val inputTokens = usage.promptTokens ?: 0
        val outputTokens = usage.completionTokens ?: 0
        val totalTokens = usage.totalTokens ?: 0
        
        Timber.i("📊 Token Usage - Input: $inputTokens, Output: $outputTokens, Total: $totalTokens")
        
        // Update UI with token information
        updateTokenCounterUI(inputTokens, outputTokens, totalTokens)
        
        // Store for billing/tracking
        storeTokenUsageInDatabase(inputTokens, outputTokens, totalTokens)
    }
}
```

### **2. In Streaming Responses** 
```kotlin
// In StreamingHandler.kt - look for final chunk
fun processStreamingChunk(chunk: String) {
    if (chunk.contains("\"usage\"")) {
        // This is the final chunk with token usage
        try {
            val jsonObject = JSONObject(chunk.removePrefix("data: "))
            val usageObject = jsonObject.getJSONObject("usage")
            
            val inputTokens = usageObject.getInt("prompt_tokens")
            val outputTokens = usageObject.getInt("completion_tokens") 
            val totalTokens = usageObject.getInt("total_tokens")
            
            Timber.i("📊 Final Token Count - Input: $inputTokens, Output: $outputTokens, Total: $totalTokens")
            
            // Update UI on main thread
            runOnUiThread {
                updateTokenCounterUI(inputTokens, outputTokens, totalTokens)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse token usage from streaming response")
        }
    }
}
```

### **3. Token Usage Display in UI**
```kotlin
// Update your token counter TextView
fun updateTokenCounterUI(inputTokens: Int, outputTokens: Int, totalTokens: Int) {
    val tokenText = "📊 $inputTokens in • $outputTokens out • $totalTokens total"
    
    runOnUiThread {
        tokenCounterTextView?.text = tokenText
        
        // Color coding based on usage
        val color = when {
            totalTokens > 100_000 -> ContextCompat.getColor(this, R.color.token_high)
            totalTokens > 50_000 -> ContextCompat.getColor(this, R.color.token_medium) 
            else -> ContextCompat.getColor(this, R.color.token_normal)
        }
        
        tokenCounterTextView?.setTextColor(color)
    }
}
```

### **4. File-Specific Token Analysis**
```kotlin
fun analyzeFileTokenUsage(
    usage: AimlApiResponse.Usage,
    userMessage: String,
    fileCount: Int
) {
    val inputTokens = usage.promptTokens ?: 0
    val estimatedTextTokens = userMessage.length / 4  // Rough estimate
    val estimatedFileTokens = inputTokens - estimatedTextTokens
    
    Timber.i("""
        🔍 TOKEN ANALYSIS:
        ├── Total Input: $inputTokens tokens
        ├── User Text: ~$estimatedTextTokens tokens  
        ├── File Content: ~$estimatedFileTokens tokens
        └── Files Uploaded: $fileCount
        
        💰 Estimated Cost: ~$${calculateCost(inputTokens, usage.completionTokens ?: 0)}
    """.trimIndent())
}

fun calculateCost(inputTokens: Int, outputTokens: Int): String {
    // GPT-4o pricing (example - check current rates)
    val inputCostPer1k = 0.0025   // $0.0025 per 1K input tokens
    val outputCostPer1k = 0.01    // $0.01 per 1K output tokens
    
    val inputCost = (inputTokens / 1000.0) * inputCostPer1k
    val outputCost = (outputTokens / 1000.0) * outputCostPer1k
    val totalCost = inputCost + outputCost
    
    return String.format("%.4f", totalCost)
}
```

## **Key Points** 🔑

### **How PDF Tokens Are Counted**
1. **Server-Side Processing**: OpenAI extracts text from your PDF
2. **Tokenization**: Extracted text is tokenized using their tokenizer
3. **Inclusion**: PDF tokens are added to `prompt_tokens` / `input_tokens`
4. **Reporting**: Total usage returned in API response

### **Token Limits to Watch**
- **GPT-4o**: 128k input + 128k output = 256k total context
- **Per File**: Up to 2 million tokens per PDF
- **Per Conversation**: Up to 20 files total

### **Cost Impact**
- **Large PDFs**: Can significantly increase input token costs
- **Multiple Files**: Costs multiply with each additional file
- **Monitor Usage**: Track tokens to avoid unexpected bills

### **Best Practices**
1. **Display Token Counts**: Show users token usage in real-time
2. **Set Warnings**: Alert when approaching model limits  
3. **Track Costs**: Monitor token usage for billing
4. **File Size Limits**: Validate PDF size before upload
5. **Usage Analytics**: Track which files consume most tokens

This gives you complete visibility into how PDF files affect your token usage! 📊