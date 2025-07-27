package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.billing.BillingManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.ResponseLength
import com.cyberflux.qwinai.model.ResponseTone
import com.cyberflux.qwinai.model.ResponsePreferences
import com.cyberflux.qwinai.service.OCRService
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PERFORMANCE: Startup optimization utility to move heavy operations off the main thread
 * This class handles background initialization of services and data loading
 */
object StartupOptimizer {
    
    private val isInitialized = AtomicBoolean(false)
    private val initializationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Cached data to avoid repeated operations
    private var cachedResponsePreferences: ResponsePreferences? = null
    private var cachedAiMessageHistory: MutableMap<String, MutableList<ChatMessage>>? = null
    
    /**
     * PERFORMANCE: Load response preferences in background
     */
    suspend fun loadResponsePreferencesAsync(context: Context): ResponsePreferences = withContext(Dispatchers.IO) {
        cachedResponsePreferences?.let { return@withContext it }
        
        try {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val lengthStr = prefs.getString("response_length", ResponseLength.DEFAULT.name)
            val toneStr = prefs.getString("response_tone", ResponseTone.DEFAULT.name)
            
            val length = try {
                ResponseLength.valueOf(lengthStr ?: ResponseLength.DEFAULT.name)
            } catch (e: IllegalArgumentException) {
                ResponseLength.DEFAULT
            }
            
            val tone = try {
                ResponseTone.valueOf(toneStr ?: ResponseTone.DEFAULT.name)
            } catch (e: IllegalArgumentException) {
                ResponseTone.DEFAULT
            }
            
            val preferences = ResponsePreferences(length = length, tone = tone)
            cachedResponsePreferences = preferences
            preferences
        } catch (e: Exception) {
            Timber.e(e, "Error loading response preferences")
            ResponsePreferences(length = ResponseLength.DEFAULT, tone = ResponseTone.DEFAULT)
        }
    }
    
    /**
     * PERFORMANCE: Load AI message history in background with JSON parsing
     */
    suspend fun loadAiMessageHistoryAsync(context: Context): MutableMap<String, MutableList<ChatMessage>> = withContext(Dispatchers.IO) {
        cachedAiMessageHistory?.let { return@withContext it }
        
        try {
            val sharedPrefs = context.getSharedPreferences("ai_messages", Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString("ai_message_history", null)
            
            val history = if (!historyJson.isNullOrEmpty()) {
                try {
                    val gson = Gson()
                    val type = object : TypeToken<MutableMap<String, MutableList<ChatMessage>>>() {}.type
                    val parsed: MutableMap<String, MutableList<ChatMessage>> = gson.fromJson(historyJson, type)
                    parsed
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing AI message history JSON")
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
            }
            
            cachedAiMessageHistory = history
            history
        } catch (e: Exception) {
            Timber.e(e, "Error loading AI message history")
            mutableMapOf()
        }
    }
    
    /**
     * PERFORMANCE: Initialize billing manager in background
     */
    suspend fun initializeBillingManagerAsync(context: Context): BillingManager = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing billing manager in background")
            val billingManager = BillingManager.getInstance(context)
            
            // Connect and restore subscriptions in background
            billingManager.restoreSubscriptions()
            billingManager.validateSubscriptionStatus()
            
            billingManager
        } catch (e: Exception) {
            Timber.e(e, "Error initializing billing manager")
            BillingManager.getInstance(context)
        }
    }
    
    /**
     * PERFORMANCE: Initialize OCR service in background
     */
    suspend fun initializeOCRServiceAsync(context: Context): OCRService = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing OCR service in background")
            OCRService(context)
        } catch (e: Exception) {
            Timber.e(e, "Error initializing OCR service")
            OCRService(context)
        }
    }
    
    /**
     * PERFORMANCE: Pre-warm all services in background during app startup
     * Call this from Application.onCreate() or Activity.onCreate() to start background initialization
     */
    fun preWarmServices(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            initializationScope.launch {
                try {
                    Timber.d("Starting service pre-warming")
                    
                    // Start all background operations in parallel
                    val preferencesDeferred = async { loadResponsePreferencesAsync(context) }
                    val historyDeferred = async { loadAiMessageHistoryAsync(context) }
                    val billingDeferred = async { initializeBillingManagerAsync(context) }
                    val ocrDeferred = async { initializeOCRServiceAsync(context) }
                    
                    // Wait for all to complete
                    preferencesDeferred.await()
                    historyDeferred.await()
                    billingDeferred.await()
                    ocrDeferred.await()
                    
                    Timber.d("Service pre-warming completed")
                } catch (e: Exception) {
                    Timber.e(e, "Error during service pre-warming")
                }
            }
        }
    }
    
    /**
     * PERFORMANCE: Get cached response preferences or load them if not available
     */
    suspend fun getCachedResponsePreferences(context: Context): ResponsePreferences {
        return cachedResponsePreferences ?: loadResponsePreferencesAsync(context)
    }
    
    /**
     * PERFORMANCE: Get cached AI message history or load it if not available
     */
    suspend fun getCachedAiMessageHistory(context: Context): MutableMap<String, MutableList<ChatMessage>> {
        return cachedAiMessageHistory ?: loadAiMessageHistoryAsync(context)
    }
    
    /**
     * Clear cached data when needed (e.g., when data is updated)
     */
    fun clearCache() {
        cachedResponsePreferences = null
        cachedAiMessageHistory = null
        Timber.d("Startup optimizer cache cleared")
    }
    
    /**
     * PERFORMANCE: Cleanup when no longer needed
     */
    fun cleanup() {
        initializationScope.cancel()
        clearCache()
        isInitialized.set(false)
    }
}