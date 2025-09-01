package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.AIParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber

/**
 * Manages AI parameters for different models
 * Stores and retrieves model-specific parameters from SharedPreferences
 */
class AIParametersManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREF_NAME = "ai_parameters"
        private const val KEY_MODEL_PARAMETERS = "model_parameters_"
        private const val KEY_CURRENT_MODEL_ID = "current_model_id"
        
        @Volatile
        private var INSTANCE: AIParametersManager? = null
        
        fun getInstance(context: Context): AIParametersManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIParametersManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val parametersAdapter = moshi.adapter(AIParameters::class.java)
    
    // Cache for current session
    private val parameterCache = mutableMapOf<String, AIParameters>()
    private var currentModelId: String? = null
    
    init {
        currentModelId = prefs.getString(KEY_CURRENT_MODEL_ID, null)
        loadCachedParameters()
    }
    
    /**
     * Get parameters for a specific model
     */
    fun getParametersForModel(model: AIModel): AIParameters {
        val modelId = model.id
        
        // Check cache first
        parameterCache[modelId]?.let { return it }
        
        // Load from preferences
        val key = KEY_MODEL_PARAMETERS + modelId
        val jsonString = prefs.getString(key, null)
        
        return if (jsonString != null) {
            try {
                val parameters = parametersAdapter.fromJson(jsonString)
                if (parameters != null) {
                    // Adapt parameters for the model to ensure compatibility
                    val adaptedParameters = parameters.adaptForModel(modelId)
                    parameterCache[modelId] = adaptedParameters
                    adaptedParameters
                } else {
                    // Fall back to defaults if parsing returns null
                    val defaultParams = AIParameters.getDefaultForModel(modelId)
                    parameterCache[modelId] = defaultParams
                    defaultParams
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading parameters for model $modelId")
                // Fall back to defaults
                val defaultParams = AIParameters.getDefaultForModel(modelId)
                parameterCache[modelId] = defaultParams
                defaultParams
            }
        } else {
            // No saved parameters, use defaults
            val defaultParams = AIParameters.getDefaultForModel(modelId)
            parameterCache[modelId] = defaultParams
            defaultParams
        }
    }
    
    /**
     * Save parameters for a specific model
     */
    fun saveParametersForModel(model: AIModel, parameters: AIParameters) {
        val modelId = model.id
        val adaptedParameters = parameters.adaptForModel(modelId)
        
        try {
            val jsonString = parametersAdapter.toJson(adaptedParameters)
            val key = KEY_MODEL_PARAMETERS + modelId
            
            prefs.edit()
                .putString(key, jsonString)
                .apply()
            
            // Update cache
            parameterCache[modelId] = adaptedParameters
            
            Timber.d("Saved parameters for model $modelId: $adaptedParameters")
            
        } catch (e: Exception) {
            Timber.e(e, "Error saving parameters for model $modelId")
        }
    }
    
    /**
     * Set the current model and return its parameters
     */
    fun setCurrentModel(model: AIModel): AIParameters {
        currentModelId = model.id
        prefs.edit()
            .putString(KEY_CURRENT_MODEL_ID, model.id)
            .apply()
            
        return getParametersForModel(model)
    }
    
    /**
     * Get parameters for the current model
     */
    fun getCurrentModelParameters(currentModel: AIModel): AIParameters {
        return getParametersForModel(currentModel)
    }
    
    /**
     * Check if a model has custom parameters (non-default)
     */
    fun hasCustomParameters(model: AIModel): Boolean {
        val key = KEY_MODEL_PARAMETERS + model.id
        return prefs.contains(key)
    }
    
    /**
     * Reset parameters for a model to defaults
     */
    fun resetToDefaults(model: AIModel) {
        val key = KEY_MODEL_PARAMETERS + model.id
        prefs.edit().remove(key).apply()
        parameterCache.remove(model.id)
        
        Timber.d("Reset parameters for model ${model.id} to defaults")
    }
    
    /**
     * Clear all saved parameters
     */
    fun clearAllParameters() {
        prefs.edit().clear().apply()
        parameterCache.clear()
        
        Timber.d("Cleared all saved AI parameters")
    }
    
    /**
     * Export parameters for all models
     */
    fun exportAllParameters(): Map<String, AIParameters> {
        val allParams = mutableMapOf<String, AIParameters>()
        
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_MODEL_PARAMETERS) && value is String) {
                val modelId = key.removePrefix(KEY_MODEL_PARAMETERS)
                try {
                    val parameters = parametersAdapter.fromJson(value)
                    if (parameters != null) {
                        allParams[modelId] = parameters
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing parameters for model $modelId")
                }
            }
        }
        
        return allParams
    }
    
    /**
     * Import parameters for multiple models
     */
    fun importParameters(parametersMap: Map<String, AIParameters>) {
        val editor = prefs.edit()
        
        parametersMap.forEach { (modelId, parameters) ->
            try {
                val jsonString = parametersAdapter.toJson(parameters)
                val key = KEY_MODEL_PARAMETERS + modelId
                editor.putString(key, jsonString)
                parameterCache[modelId] = parameters
            } catch (e: Exception) {
                Timber.e(e, "Error importing parameters for model $modelId")
            }
        }
        
        editor.apply()
        Timber.d("Imported parameters for ${parametersMap.size} models")
    }
    
    /**
     * Get a list of all models with custom parameters
     */
    fun getModelsWithCustomParameters(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_MODEL_PARAMETERS) }
            .map { it.removePrefix(KEY_MODEL_PARAMETERS) }
    }
    
    /**
     * Load all cached parameters at startup
     */
    private fun loadCachedParameters() {
        try {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_MODEL_PARAMETERS) && value is String) {
                    val modelId = key.removePrefix(KEY_MODEL_PARAMETERS)
                    try {
                        val parameters = parametersAdapter.fromJson(value)
                        if (parameters != null) {
                            parameterCache[modelId] = parameters
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading cached parameters for model $modelId")
                    }
                }
            }
            Timber.d("Loaded ${parameterCache.size} cached parameter sets")
        } catch (e: Exception) {
            Timber.e(e, "Error loading cached parameters")
        }
    }
    
    /**
     * Create API request parameters from AIParameters
     */
    fun createApiParameters(parameters: AIParameters, modelId: String): Map<String, Any> {
        val apiParams = mutableMapOf<String, Any>()
        
        // Core parameters
        apiParams["temperature"] = parameters.temperature
        apiParams["max_tokens"] = parameters.maxTokens
        
        // Model-specific parameter mapping
        when {
            modelId.contains("gpt", ignoreCase = true) || 
            modelId.contains("o1", ignoreCase = true) ||
            modelId.contains("chatgpt", ignoreCase = true) -> {
                // OpenAI parameters
                apiParams["top_p"] = parameters.topP
                if (parameters.frequencyPenalty != 0.0f) {
                    apiParams["frequency_penalty"] = parameters.frequencyPenalty
                }
                if (parameters.presencePenalty != 0.0f) {
                    apiParams["presence_penalty"] = parameters.presencePenalty
                }
                if (parameters.seed != null) {
                    apiParams["seed"] = parameters.seed
                }
                apiParams["stream"] = parameters.streamResponse
            }
            
            modelId.contains("claude", ignoreCase = true) -> {
                // Anthropic parameters
                apiParams["top_p"] = parameters.topP
                if (parameters.topK > 0) {
                    apiParams["top_k"] = parameters.topK
                }
            }
            
            modelId.contains("llama", ignoreCase = true) ||
            modelId.contains("mistral", ignoreCase = true) ||
            modelId.contains("deepseek", ignoreCase = true) ||
            modelId.contains("qwen", ignoreCase = true) -> {
                // Open source model parameters
                apiParams["top_p"] = parameters.topP
                apiParams["top_k"] = parameters.topK
                apiParams["repetition_penalty"] = parameters.repetitionPenalty
                
                if (parameters.mirostat > 0) {
                    apiParams["mirostat"] = parameters.mirostat
                    apiParams["mirostat_tau"] = parameters.mirostatTau
                    apiParams["mirostat_eta"] = parameters.mirostatEta
                }
                
                if (parameters.seed != null) {
                    apiParams["seed"] = parameters.seed
                }
            }
            
            modelId.contains("cohere", ignoreCase = true) -> {
                // Cohere parameters
                apiParams["p"] = parameters.topP
                apiParams["k"] = parameters.topK
                if (parameters.frequencyPenalty != 0.0f) {
                    apiParams["frequency_penalty"] = parameters.frequencyPenalty
                }
                if (parameters.presencePenalty != 0.0f) {
                    apiParams["presence_penalty"] = parameters.presencePenalty
                }
            }
            
            else -> {
                // Default parameter mapping
                apiParams["top_p"] = parameters.topP
                if (parameters.topK > 0) {
                    apiParams["top_k"] = parameters.topK
                }
                if (parameters.repetitionPenalty != 1.0f) {
                    apiParams["repetition_penalty"] = parameters.repetitionPenalty
                }
            }
        }
        
        // Add stop sequences if specified
        if (parameters.stopSequences.isNotEmpty()) {
            apiParams["stop"] = parameters.stopSequences
        }
        
        return apiParams
    }
}