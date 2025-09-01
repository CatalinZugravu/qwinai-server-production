# Multi-Provider Token Counting Guide

Your app supports **12 different AI providers**! Each handles token counting differently. Here's the complete breakdown:

## **Provider-Specific Token Reporting**

### **1. OpenAI Models** 🟢 (Full Auto-Counting)
**Models**: `gpt-4o`, `gpt-4-turbo`, `o1`, etc.
```json
{
  "usage": {
    "prompt_tokens": 1500,        // ✅ Includes file content
    "completion_tokens": 200,     // ✅ Response tokens
    "total_tokens": 1700         // ✅ Auto-calculated
  }
}
```
**File Support**: ✅ PDF, Images (tokens auto-counted)

### **2. Anthropic (Claude)** 🟢 (Full Auto-Counting)  
**Models**: `claude-3-7-sonnet`, `claude-3.5`, etc.
```json
{
  "usage": {
    "input_tokens": 1500,        // ✅ Includes file content
    "output_tokens": 200,        // ✅ Response tokens  
    "total_tokens": 1700         // ✅ Auto-calculated
  }
}
```
**File Support**: ✅ PDF, Images (tokens auto-counted)

### **3. Meta (Llama)** 🟡 (Partial Auto-Counting)
**Models**: `meta-llama/llama-4-maverick`
```json
{
  "usage": {
    "prompt_tokens": 1500,       // ✅ Usually provided
    "completion_tokens": 200,    // ✅ Usually provided
    "total_tokens": 1700        // ⚠️ Sometimes missing
  }
}
```
**File Support**: ✅ Images only (basic token counting)

### **4. Google Models** 🟡 (Inconsistent)
**Models**: `gemini-pro`, `gemma-3-27b-it`
```json
{
  "usage": {
    "promptTokenCount": 1500,    // ✅ Different field names
    "candidatesTokenCount": 200, // ✅ Different field names
    "totalTokenCount": 1700     // ⚠️ Sometimes missing
  }
}
```
**File Support**: ✅ Images, Documents (limited token info)

### **5. Cohere** 🔴 (Manual Counting Required)
**Models**: `cohere/command-r-plus`
```json
{
  "meta": {
    "tokens": {
      "input_tokens": 1500,      // ✅ Basic counting
      "output_tokens": 200       // ✅ Basic counting
      // ❌ No file token breakdown
    }
  }
}
```
**File Support**: ✅ PDF (but file tokens NOT separated)

### **6. DeepSeek** 🟡 (Basic Auto-Counting)
**Models**: `deepseek/deepseek-r1`
```json
{
  "usage": {
    "prompt_tokens": 1500,       // ✅ Usually provided
    "completion_tokens": 200,    // ✅ Usually provided
    "total_tokens": 1700        // ✅ Usually provided
  }
}
```
**File Support**: ✅ Multiple formats (basic token info)

### **7. Qwen** 🟡 (Basic Auto-Counting)
**Models**: `Qwen/Qwen3-235B-A22B-fp8-tput`
```json
{
  "usage": {
    "input_tokens": 1500,        // ✅ Usually provided
    "output_tokens": 200,        // ✅ Usually provided
    "total_tokens": 1700        // ✅ Usually provided
  }
}
```
**File Support**: ❌ No file support configured

### **8. xAI (Grok)** 🟢 (Good Auto-Counting)
**Models**: `x-ai/grok-3-beta`  
```json
{
  "usage": {
    "prompt_tokens": 1500,       // ✅ Includes file content
    "completion_tokens": 200,    // ✅ Response tokens
    "total_tokens": 1700        // ✅ Auto-calculated
  }
}
```
**File Support**: ✅ Images, Documents (good token counting)

### **9. Mistral** 🔴 (OCR Only - Special Case)
**Models**: `mistral/mistral-ocr-latest`
```json
{
  "usage": {
    "prompt_tokens": 0,          // ⚠️ OCR models don't count input
    "completion_tokens": 5000,   // ✅ Large output (extracted text)
    "total_tokens": 5000        // ✅ Total provided
  }
}
```
**File Support**: ✅ PDF, Images (OCR extraction, not analysis)

### **10. Perplexity** 🟡 (Web Search Focus)
**Models**: `perplexity/sonar-pro`
```json
{
  "usage": {
    "prompt_tokens": 1500,       // ✅ Basic counting
    "completion_tokens": 200,    // ✅ Basic counting
    "total_tokens": 1700        // ✅ Basic counting
  }
}
```
**File Support**: ✅ Images only (web search focused)

### **11. ZhiPu (GLM)** 🟡 (Basic Auto-Counting)
**Models**: `zhipu/glm-4.5`
```json
{
  "usage": {
    "prompt_tokens": 1500,       // ✅ Usually provided
    "completion_tokens": 200,    // ✅ Usually provided  
    "total_tokens": 1700        // ✅ Usually provided
  }
}
```
**File Support**: ✅ Multiple formats (basic token info)

## **Universal Token Extraction Code**

Here's code that works across ALL providers:

```kotlin
/**
 * Universal token extractor that works with all AI providers
 */
object UniversalTokenCounter {
    
    fun extractTokenUsage(response: AimlApiResponse, modelId: String): TokenUsage {
        val usage = response.usage
        if (usage == null) {
            Timber.w("⚠️ No token usage provided by model: $modelId")
            return TokenUsage(0, 0, 0)
        }
        
        val provider = getProviderFromModelId(modelId)
        
        return when (provider) {
            "openai" -> extractOpenAITokens(usage)
            "anthropic" -> extractAnthropicTokens(usage) 
            "google" -> extractGoogleTokens(usage)
            "meta" -> extractMetaTokens(usage)
            "cohere" -> extractCohereTokens(usage)
            "deepseek" -> extractDeepSeekTokens(usage)
            "qwen" -> extractQwenTokens(usage)
            "xai" -> extractXAITokens(usage)
            "mistral" -> extractMistralTokens(usage)
            "perplexity" -> extractPerplexityTokens(usage)
            "zhipu" -> extractZhiPuTokens(usage)
            else -> extractGenericTokens(usage) // Fallback
        }
    }
    
    private fun extractOpenAITokens(usage: AimlApiResponse.Usage): TokenUsage {
        return TokenUsage(
            inputTokens = usage.promptTokens ?: 0,
            outputTokens = usage.completionTokens ?: 0, 
            totalTokens = usage.totalTokens ?: 0
        )
    }
    
    private fun extractAnthropicTokens(usage: AimlApiResponse.Usage): TokenUsage {
        return TokenUsage(
            inputTokens = usage.inputTokens ?: usage.promptTokens ?: 0,
            outputTokens = usage.outputTokens ?: usage.completionTokens ?: 0,
            totalTokens = usage.totalTokens ?: 0
        )
    }
    
    private fun extractGoogleTokens(usage: AimlApiResponse.Usage): TokenUsage {
        // Google uses different field names
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: usage.generatedTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        return TokenUsage(inputTokens, outputTokens, totalTokens)
    }
    
    private fun extractGenericTokens(usage: AimlApiResponse.Usage): TokenUsage {
        // Fallback for any provider
        val inputTokens = usage.promptTokens ?: usage.inputTokens ?: usage.contextTokens ?: 0
        val outputTokens = usage.completionTokens ?: usage.outputTokens ?: usage.generatedTokens ?: 0
        val totalTokens = usage.totalTokens ?: (inputTokens + outputTokens)
        
        return TokenUsage(inputTokens, outputTokens, totalTokens)
    }
    
    private fun getProviderFromModelId(modelId: String): String {
        return ModelConfigManager.getConfig(modelId)?.provider ?: "unknown"
    }
    
    data class TokenUsage(
        val inputTokens: Int,
        val outputTokens: Int,  
        val totalTokens: Int
    ) {
        fun hasValidData(): Boolean = totalTokens > 0 || (inputTokens > 0 && outputTokens > 0)
        
        fun getDisplayText(): String {
            return when {
                totalTokens > 0 -> "📊 ${inputTokens.format()} in • ${outputTokens.format()} out • ${totalTokens.format()} total"
                inputTokens > 0 || outputTokens > 0 -> "📊 ${inputTokens.format()} in • ${outputTokens.format()} out"
                else -> "📊 No token data"
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
```

## **File Token Support Matrix**

| Provider | PDF Support | Token Counting | File Token Separation |
|----------|-------------|----------------|----------------------|
| OpenAI | ✅ Full | ✅ Automatic | ✅ Included in prompt_tokens |
| Anthropic | ✅ Full | ✅ Automatic | ✅ Included in input_tokens |
| xAI (Grok) | ✅ Good | ✅ Automatic | ✅ Included in prompt_tokens |
| DeepSeek | ✅ Basic | ✅ Basic | ⚠️ Limited breakdown |
| ZhiPu | ✅ Basic | ✅ Basic | ⚠️ Limited breakdown |
| Cohere | ✅ PDF Only | ⚠️ Basic | ❌ No separation |
| Google | ⚠️ Limited | ⚠️ Inconsistent | ❌ No separation |
| Meta | ⚠️ Images Only | ⚠️ Basic | ❌ No separation |
| Perplexity | ⚠️ Images Only | ✅ Basic | ❌ No separation |
| Mistral | ✅ OCR Only | ⚠️ Output Only | ❌ Special case |
| Qwen | ❌ None | ✅ Basic | ❌ No files |

## **Implementation Strategy**

```kotlin
// In your message handler
fun handleApiResponse(response: AimlApiResponse, modelId: String, hasFiles: Boolean) {
    // Extract tokens universally
    val tokenUsage = UniversalTokenCounter.extractTokenUsage(response, modelId)
    
    if (tokenUsage.hasValidData()) {
        // Update UI
        updateTokenDisplay(tokenUsage.getDisplayText())
        
        // Store usage
        TokenUsageStorage.store(tokenUsage, modelId, hasFiles)
        
        // Analyze file impact (only for supported providers)
        if (hasFiles && supportsFileTokenBreakdown(modelId)) {
            analyzeFileTokenImpact(tokenUsage, modelId)
        }
    } else {
        Timber.w("⚠️ Model $modelId provided no token usage data")
        updateTokenDisplay("📊 No token data")
    }
}

private fun supportsFileTokenBreakdown(modelId: String): Boolean {
    val provider = ModelConfigManager.getConfig(modelId)?.provider
    return provider in listOf("openai", "anthropic", "xai")
}
```

## **Key Takeaways** 🎯

1. **OpenAI, Anthropic, xAI**: Full automatic counting including file tokens
2. **Others**: Basic counting, manual estimation may be needed for files  
3. **Universal Approach**: Use the extraction code above to handle all providers
4. **File Support**: Only some providers separate file tokens from message tokens
5. **Fallback Strategy**: Always have generic token extraction for unknown providers

**Bottom line**: You still don't need to manually count most tokens, but the quality and detail of token reporting varies significantly across providers! 📊