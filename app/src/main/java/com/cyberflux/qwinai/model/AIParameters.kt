package com.cyberflux.qwinai.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.squareup.moshi.JsonClass

/**
 * Comprehensive AI Parameters that affect model responses
 * Different models support different subsets of these parameters
 */
@Parcelize
@JsonClass(generateAdapter = true)
data class AIParameters(
    // Core Generation Parameters
    val temperature: Float = 0.7f,                    // Creativity/randomness (0.0-2.0)
    val topP: Float = 0.9f,                          // Nucleus sampling (0.0-1.0) 
    val topK: Int = 50,                              // Top-K sampling (1-100)
    val maxTokens: Int = 4000,                       // Maximum output length
    
    // Repetition Control
    val repetitionPenalty: Float = 1.1f,             // Prevent repetition (0.1-2.0)
    val frequencyPenalty: Float = 0.0f,              // OpenAI frequency penalty (-2.0-2.0)
    val presencePenalty: Float = 0.0f,               // OpenAI presence penalty (-2.0-2.0)
    
    // Advanced Sampling
    val minP: Float = 0.001f,                        // Minimum probability threshold (API requires >= 0.001)
    val typicalP: Float = 1.0f,                      // Typical sampling parameter
    val tfs: Float = 1.0f,                           // Tail free sampling
    val eta: Float = 1.0f,                           // Learning rate for some models
    val epsilon: Float = 1e-4f,                      // Epsilon cutoff for dynamic sampling
    
    // Response Control
    val stopSequences: List<String> = emptyList(),   // Stop generation at these tokens
    val seed: Long? = null,                          // Random seed for reproducibility
    val bestOf: Int = 1,                             // Generate N and return best (expensive)
    val n: Int = 1,                                  // Number of completions to generate
    
    // Model-Specific Parameters
    val doSample: Boolean = true,                    // Enable sampling vs greedy
    val earlyStopping: Boolean = false,              // Stop when EOS token generated
    val lengthPenalty: Float = 1.0f,                 // Penalty for length (beam search)
    val noRepeatNgramSize: Int = 0,                  // Prevent repeating n-grams
    val numBeams: Int = 1,                           // Beam search width
    val padTokenId: Int? = null,                     // Padding token ID
    val eosTokenId: Int? = null,                     // End of sequence token ID
    
    // Mirostat Sampling (for some models)
    val mirostat: Int = 0,                           // Mirostat sampling mode (0,1,2)
    val mirostatTau: Float = 5.0f,                   // Mirostat target entropy
    val mirostatEta: Float = 0.1f,                   // Mirostat learning rate
    
    // System Control
    val streamResponse: Boolean = true,               // Stream response tokens
    val logProbs: Boolean = false,                   // Return token probabilities
    val echo: Boolean = false,                       // Echo input in response
    val useCaching: Boolean = true,                  // Enable response caching
    
    // Custom Instructions
    val systemPrompt: String? = null,                // Custom system prompt override
    val responseFormat: String = "text",             // Response format (text, json, etc.)
    val jsonSchema: String? = null                   // JSON schema for structured output
) : Parcelable {
    
    /**
     * Create parameters suitable for the given model
     */
    fun adaptForModel(modelId: String): AIParameters {
        return when {
            // OpenAI Models (GPT-4, GPT-3.5, etc.)
            modelId.contains("gpt", ignoreCase = true) || 
            modelId.contains("o1", ignoreCase = true) ||
            modelId.contains("chatgpt", ignoreCase = true) -> {
                copy(
                    // OpenAI uses frequency/presence penalty instead of repetition penalty
                    repetitionPenalty = 1.0f,
                    // OpenAI specific ranges
                    temperature = temperature.coerceIn(0.0f, 2.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    frequencyPenalty = frequencyPenalty.coerceIn(-2.0f, 2.0f),
                    presencePenalty = presencePenalty.coerceIn(-2.0f, 2.0f),
                    // Disable unsupported parameters
                    topK = 0, // OpenAI doesn't use top-k
                    mirostat = 0
                )
            }
            
            // Anthropic Claude Models
            modelId.contains("claude", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 1.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    topK = topK.coerceIn(1, 40), // Claude supports lower top-k
                    // Disable OpenAI-specific parameters
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f,
                    mirostat = 0
                )
            }
            
            // Google Models (Gemini, PaLM, etc.)
            modelId.contains("gemini", ignoreCase = true) ||
            modelId.contains("palm", ignoreCase = true) ||
            modelId.contains("gemma", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 1.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    topK = topK.coerceIn(1, 40),
                    // Google specific settings
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f,
                    mirostat = 0
                )
            }
            
            // Meta LLaMA Models
            modelId.contains("llama", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.1f, 1.0f),
                    topP = topP.coerceIn(0.1f, 1.0f),
                    topK = topK.coerceIn(10, 100),
                    repetitionPenalty = repetitionPenalty.coerceIn(1.0f, 1.3f),
                    // LLaMA supports mirostat
                    mirostat = mirostat.coerceIn(0, 2),
                    // Disable OpenAI-specific
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f
                )
            }
            
            // Mistral Models
            modelId.contains("mistral", ignoreCase = true) ||
            modelId.contains("mixtral", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 1.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    topK = topK.coerceIn(1, 200),
                    repetitionPenalty = repetitionPenalty.coerceIn(1.0f, 2.0f),
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f,
                    mirostat = 0
                )
            }
            
            // DeepSeek Models
            modelId.contains("deepseek", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 2.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    topK = topK.coerceIn(1, 100),
                    repetitionPenalty = repetitionPenalty.coerceIn(1.0f, 1.5f),
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f
                )
            }
            
            // Qwen Models
            modelId.contains("qwen", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.1f, 2.0f),
                    topP = topP.coerceIn(0.1f, 1.0f),
                    topK = topK.coerceIn(1, 50),
                    repetitionPenalty = repetitionPenalty.coerceIn(1.0f, 1.2f),
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f
                )
            }
            
            // Cohere Models
            modelId.contains("cohere", ignoreCase = true) ||
            modelId.contains("command", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 5.0f), // Cohere has wider range
                    topP = topP.coerceIn(0.01f, 0.99f),
                    topK = topK.coerceIn(1, 500), // Cohere supports high top-k
                    frequencyPenalty = frequencyPenalty.coerceIn(0.0f, 1.0f),
                    presencePenalty = presencePenalty.coerceIn(0.0f, 1.0f),
                    repetitionPenalty = 1.0f, // Cohere uses frequency/presence instead
                    mirostat = 0
                )
            }
            
            // Perplexity Models
            modelId.contains("perplexity", ignoreCase = true) ||
            modelId.contains("sonar", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 2.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    topK = 0, // Perplexity doesn't use top-k
                    frequencyPenalty = frequencyPenalty.coerceIn(-2.0f, 2.0f),
                    presencePenalty = presencePenalty.coerceIn(-2.0f, 2.0f),
                    repetitionPenalty = 1.0f,
                    mirostat = 0
                )
            }
            
            // xAI Grok Models
            modelId.contains("grok", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 2.0f),
                    topP = topP.coerceIn(0.0f, 1.0f),
                    topK = topK.coerceIn(1, 100),
                    repetitionPenalty = repetitionPenalty.coerceIn(1.0f, 1.5f),
                    frequencyPenalty = 0.0f,
                    presencePenalty = 0.0f
                )
            }
            
            // ZhiPu GLM Models
            modelId.contains("zhipu", ignoreCase = true) ||
            modelId.contains("glm", ignoreCase = true) -> {
                copy(
                    temperature = temperature.coerceIn(0.0f, 2.0f),
                    topP = topP.coerceIn(0.01f, 1.0f),
                    topK = 0, // ZhiPu doesn't use top-k
                    repetitionPenalty = 1.0f, // ZhiPu uses frequency/presence penalty
                    frequencyPenalty = frequencyPenalty.coerceIn(-2.0f, 2.0f),
                    presencePenalty = presencePenalty.coerceIn(-2.0f, 2.0f),
                    mirostat = 0
                )
            }
            
            // Default for unknown models
            else -> this
        }
    }
    
    /**
     * Get user-friendly parameter descriptions
     */
    fun getParameterInfo(param: String): ParameterInfo? {
        return when (param) {
            "temperature" -> ParameterInfo(
                "Creativity", 
                "Controls randomness. Lower = more focused, Higher = more creative",
                0.0f, 2.0f, 0.1f
            )
            "topP" -> ParameterInfo(
                "Focus", 
                "Nucleus sampling. Lower = more focused vocabulary",
                0.0f, 1.0f, 0.05f
            )
            "topK" -> ParameterInfo(
                "Vocabulary", 
                "Limits vocabulary choice. Lower = more predictable",
                1f, 100f, 1f
            )
            "repetitionPenalty" -> ParameterInfo(
                "Avoid Repetition", 
                "Prevents repetitive text. Higher = less repetition",
                0.1f, 2.0f, 0.1f
            )
            "frequencyPenalty" -> ParameterInfo(
                "Word Frequency", 
                "Reduces frequent words. Higher = more diverse vocabulary",
                -2.0f, 2.0f, 0.1f
            )
            "presencePenalty" -> ParameterInfo(
                "Topic Novelty", 
                "Encourages new topics. Higher = more topic diversity",
                -2.0f, 2.0f, 0.1f
            )
            "maxTokens" -> ParameterInfo(
                "Response Length", 
                "Maximum response length in tokens",
                50f, 8000f, 50f
            )
            else -> null
        }
    }
    
    companion object {
        /**
         * Get default parameters for a specific model
         */
        fun getDefaultForModel(modelId: String): AIParameters {
            return AIParameters().adaptForModel(modelId)
        }
        
        /**
         * Create parameters from JSON for API requests
         */
        fun fromApiDefaults(): AIParameters {
            return AIParameters(
                temperature = 0.7f,
                topP = 0.9f,
                topK = 50,
                maxTokens = 4000,
                repetitionPenalty = 1.1f,
                streamResponse = true
            )
        }
    }
}

/**
 * Information about a parameter for UI display
 */
@JsonClass(generateAdapter = true)
data class ParameterInfo(
    val displayName: String,
    val description: String,
    val minValue: Float,
    val maxValue: Float,
    val stepSize: Float
)

/**
 * Parameter presets for quick selection
 */
enum class ParameterPreset(
    val displayName: String,
    val description: String,
    val parameters: AIParameters
) {
    BALANCED(
        "Balanced", 
        "Good for most conversations",
        AIParameters(
            temperature = 0.7f,
            topP = 0.9f,
            topK = 50,
            repetitionPenalty = 1.1f
        )
    ),
    
    CREATIVE(
        "Creative", 
        "More imaginative and varied responses",
        AIParameters(
            temperature = 0.9f,
            topP = 0.95f,
            topK = 80,
            repetitionPenalty = 1.2f
        )
    ),
    
    PRECISE(
        "Precise", 
        "Focused and consistent responses",
        AIParameters(
            temperature = 0.3f,
            topP = 0.8f,
            topK = 20,
            repetitionPenalty = 1.0f
        )
    ),
    
    DETERMINISTIC(
        "Deterministic", 
        "Most predictable responses",
        AIParameters(
            temperature = 0.1f,
            topP = 0.7f,
            topK = 10,
            repetitionPenalty = 1.0f,
            doSample = false
        )
    ),
    
    EXPERIMENTAL(
        "Experimental", 
        "Maximum creativity and exploration",
        AIParameters(
            temperature = 1.2f,
            topP = 0.98f,
            topK = 100,
            repetitionPenalty = 1.3f,
            mirostat = 2,
            mirostatTau = 5.0f
        )
    )
}