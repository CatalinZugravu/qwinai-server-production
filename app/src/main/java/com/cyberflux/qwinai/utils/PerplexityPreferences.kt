package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages Perplexity-specific preferences for search mode and context size
 */
object PerplexityPreferences {
    
    private const val PREFS_NAME = "perplexity_preferences"
    private const val KEY_SEARCH_MODE = "search_mode"
    private const val KEY_CONTEXT_SIZE = "context_size"
    
    // Search mode constants
    const val SEARCH_MODE_ACADEMIC = "academic"
    const val SEARCH_MODE_WEB = "web"
    
    // Context size constants  
    const val CONTEXT_SIZE_LOW = "low"     // Search: Fast answers to everyday questions
    const val CONTEXT_SIZE_MEDIUM = "medium"  // Advanced search
    const val CONTEXT_SIZE_HIGH = "high"   // Research: Advanced analysis on any topic
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get the current search mode (academic or web)
     */
    fun getSearchMode(context: Context): String {
        return getPrefs(context).getString(KEY_SEARCH_MODE, SEARCH_MODE_WEB) ?: SEARCH_MODE_WEB
    }
    
    /**
     * Set the search mode (academic or web)
     */
    fun setSearchMode(context: Context, searchMode: String) {
        getPrefs(context).edit().putString(KEY_SEARCH_MODE, searchMode).apply()
    }
    
    /**
     * Get the current context size (low, medium, high)
     */
    fun getContextSize(context: Context): String {
        return getPrefs(context).getString(KEY_CONTEXT_SIZE, CONTEXT_SIZE_MEDIUM) ?: CONTEXT_SIZE_MEDIUM
    }
    
    /**
     * Set the context size (low, medium, high)
     */
    fun setContextSize(context: Context, contextSize: String) {
        getPrefs(context).edit().putString(KEY_CONTEXT_SIZE, contextSize).apply()
    }
    
    /**
     * Get display name for context size
     */
    fun getContextSizeDisplayName(contextSize: String): String {
        return when (contextSize) {
            CONTEXT_SIZE_LOW -> "Search"
            CONTEXT_SIZE_MEDIUM -> "Advanced"
            CONTEXT_SIZE_HIGH -> "Research"
            else -> "Advanced"
        }
    }
    
    /**
     * Get description for context size
     */
    fun getContextSizeDescription(contextSize: String): String {
        return when (contextSize) {
            CONTEXT_SIZE_LOW -> "Fast answers to everyday questions"
            CONTEXT_SIZE_MEDIUM -> "Balanced search with good detail"
            CONTEXT_SIZE_HIGH -> "Advanced analysis on any topic"
            else -> "Balanced search with good detail"
        }
    }
    
    /**
     * Check if current model is Perplexity Sonar Pro
     */
    fun isPerplexityModel(modelId: String): Boolean {
        return modelId == "perplexity/sonar-pro"
    }
}