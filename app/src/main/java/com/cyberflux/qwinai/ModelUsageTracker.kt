package com.cyberflux.qwinai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.cyberflux.qwinai.model.RecentModel
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.JsonUtils
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Utility class to track and manage AI model usage statistics
 * Maintains usage count for models and provides recent models based on frequency
 */
object ModelUsageTracker {
    // SharedPreferences constants
    private const val PREFS_NAME = "model_usage_prefs"
    private const val KEY_MODEL_USAGE = "model_usage"
    private const val KEY_LAST_RESET = "last_reset_time"
    private const val KEY_FIRST_LAUNCH = "first_launch_time"

    // Default model IDs if no usage history exists
    private val DEFAULT_MODEL_IDS = listOf(
        ModelManager.CLAUDE_3_7_SONNET_ID,
        ModelManager.GPT_4O_ID,
        ModelManager.LLAMA_4_MAVERICK_ID
    )

    /**
     * Record model usage when a user selects a model
     * @param context Application context
     * @param modelId ID of the model being used
     */
    fun recordModelUsage(context: Context, modelId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Clean and normalize the model ID to prevent duplicates
            val cleanModelId = cleanModelId(modelId)

            // Validate that the model ID is not empty after cleaning
            if (cleanModelId.isEmpty()) {
                Timber.w("Empty model ID after cleaning, skipping usage recording")
                return
            }

            // Check if we need to initialize first launch time
            initializeFirstLaunchIfNeeded(prefs)

            // Check if we need a weekly reset
            checkAndPerformWeeklyReset(prefs)

            // Load current usage data
            val usageMap = getUsageMap(prefs)

            // Increment usage count for this model
            val currentCount = usageMap[cleanModelId] ?: 0
            usageMap[cleanModelId] = currentCount + 1

            // Save updated data
            saveUsageMap(prefs, usageMap)

            Timber.d("Recorded usage for model $cleanModelId, count: ${usageMap[cleanModelId]}")
        } catch (e: Exception) {
            Timber.e(e, "Error recording model usage for $modelId: ${e.message}")
        }
    }

    /**
     * Get the most recently/frequently used models
     * @param context Application context
     * @param limit Maximum number of models to return
     * @return List of recent models sorted by usage frequency
     */
    fun getRecentModels(context: Context, limit: Int = 3): List<RecentModel> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Initialize first launch if needed
            initializeFirstLaunchIfNeeded(prefs)

            // Check if we need a weekly reset
            checkAndPerformWeeklyReset(prefs)

            // Get usage data
            val usageMap = getUsageMap(prefs)

            // If no usage data, return default models
            if (usageMap.isEmpty()) {
                return getDefaultModels(limit)
            }

            // Sort by usage count (descending) and convert to RecentModel objects
            val recentModels = usageMap.entries
                .sortedByDescending { it.value }
                .mapNotNull { entry ->
                    // Find model details from ModelManager
                    val model = ModelManager.models.find { it.id == entry.key }

                    if (model != null) {
                        RecentModel(
                            displayName = model.displayName,
                            id = model.id,
                            iconResId = getIconForModelId(model.id)
                        )
                    } else {
                        // Skip if model not found in ModelManager (might have been removed)
                        Timber.d("Model ${entry.key} not found in ModelManager, skipping")
                        null
                    }
                }
                .distinctBy { it.id } // Remove any potential duplicates by model ID
                .take(limit)

            // If we don't have enough recent models, supplement with defaults
            if (recentModels.size < limit) {
                val defaultModels = getDefaultModels(limit - recentModels.size)
                val combinedModels = (recentModels + defaultModels)
                    .distinctBy { it.id } // Ensure no duplicates between recent and default
                    .take(limit)

                Timber.d("Returning ${combinedModels.size} models (${recentModels.size} recent + ${defaultModels.size} default)")
                return combinedModels
            }

            Timber.d("Returning ${recentModels.size} recent models")
            return recentModels

        } catch (e: Exception) {
            Timber.e(e, "Error getting recent models: ${e.message}")
            // Fallback to defaults on error
            return getDefaultModels(limit)
        }
    }

    /**
     * Reset usage statistics, keeping the top 5 models
     * This preserves some history while giving newer models a chance
     */
    fun resetUsageStatistics(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Get current usage data
            val usageMap = getUsageMap(prefs)

            // If no data, nothing to reset
            if (usageMap.isEmpty()) {
                return
            }

            // Keep top 5 models but reset their counts to 1
            // This preserves history but gives new models a chance
            val newUsageMap = usageMap.entries
                .sortedByDescending { it.value }
                .take(5)
                .associate { it.key to 1 }
                .toMutableMap()

            // Save updated data
            saveUsageMap(prefs, newUsageMap)

            // Update reset time
            prefs.edit {
                putLong(KEY_LAST_RESET, System.currentTimeMillis())
                apply()
            }

            Timber.d("Reset usage statistics, kept ${newUsageMap.size} models")
        } catch (e: Exception) {
            Timber.e(e, "Error resetting usage statistics: ${e.message}")
        }
    }

    /**
     * Get usage statistics for analytics or debugging
     */
    fun getUsageStatistics(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getUsageMap(prefs).toMap()
    }

    /**
     * Get days until next weekly reset
     */
    fun getDaysUntilReset(context: Context): Int {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastReset = prefs.getLong(KEY_LAST_RESET, 0)

            // If no last reset, return 7 (full week)
            if (lastReset == 0L) {
                return 7
            }

            val now = System.currentTimeMillis()
            val daysSinceReset = TimeUnit.MILLISECONDS.toDays(now - lastReset)

            // Weekly reset (7 days)
            return (7 - daysSinceReset).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating days until reset: ${e.message}")
            return 7
        }
    }

    /**
     * Clean and normalize model ID to prevent duplicates
     * @param modelId Raw model ID
     * @return Cleaned model ID
     */
    private fun cleanModelId(modelId: String): String {
        return modelId.trim().lowercase()
    }

    /**
     * Initialize first launch timestamp if needed
     */
    private fun initializeFirstLaunchIfNeeded(prefs: SharedPreferences) {
        if (!prefs.contains(KEY_FIRST_LAUNCH)) {
            prefs.edit {
                putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
                apply()
            }
        }

        // Also initialize last reset time if needed
        if (!prefs.contains(KEY_LAST_RESET)) {
            prefs.edit {
                putLong(KEY_LAST_RESET, System.currentTimeMillis())
                apply()
            }
        }
    }

    /**
     * Check if it's time for a weekly reset
     */
    private fun checkAndPerformWeeklyReset(prefs: SharedPreferences) {
        val lastResetTime = prefs.getLong(KEY_LAST_RESET, 0)
        val currentTime = System.currentTimeMillis()

        // No previous reset, initialize
        if (lastResetTime == 0L) {
            prefs.edit {
                putLong(KEY_LAST_RESET, currentTime)
                apply()
            }
            return
        }

        // Get calendar for last reset
        val lastResetCal = Calendar.getInstance().apply {
            timeInMillis = lastResetTime
        }

        // Get current calendar
        val currentCal = Calendar.getInstance().apply {
            timeInMillis = currentTime
        }

        // Check if a week has passed (by comparing week numbers)
        // We reset on Sunday at midnight
        if (getWeekOfYear(lastResetCal) != getWeekOfYear(currentCal)) {
            val usageMap = getUsageMap(prefs)

            // Only perform reset if we actually have usage data
            if (usageMap.isNotEmpty()) {
                // Keep top 5 models but reduce their counts
                val newUsageMap = usageMap.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .associate {
                        // Reduce counts to 1 to preserve history but give new models a chance
                        it.key to 1
                    }
                    .toMutableMap()

                // Save updated data
                saveUsageMap(prefs, newUsageMap)

                Timber.d("Weekly reset performed, kept ${newUsageMap.size} models with reduced counts")
            }

            // Update reset time
            prefs.edit {
                putLong(KEY_LAST_RESET, currentTime)
                apply()
            }
        }
    }

    /**
     * Get the week number of a calendar for weekly reset calculations
     */
    private fun getWeekOfYear(calendar: Calendar): Int {
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    /**
     * Load usage map from SharedPreferences
     */
    private fun getUsageMap(prefs: SharedPreferences): MutableMap<String, Int> {
        val usageJson = prefs.getString(KEY_MODEL_USAGE, "{}")
        return try {
            val rawMap = JsonUtils.fromJsonMap(usageJson, String::class.java, Int::class.java) ?: mutableMapOf()

            // Clean existing keys to prevent duplicates from old data
            val cleanedMap = mutableMapOf<String, Int>()
            rawMap.forEach { (key, value) ->
                val cleanKey = cleanModelId(key)
                if (cleanKey.isNotEmpty()) {
                    // If the clean key already exists, sum the values
                    cleanedMap[cleanKey] = (cleanedMap[cleanKey] ?: 0) + value
                }
            }

            cleanedMap.toMutableMap()
        } catch (e: Exception) {
            Timber.e(e, "Error parsing usage data, resetting: ${e.message}")
            mutableMapOf()
        }
    }

    /**
     * Save usage map to SharedPreferences
     */
    private fun saveUsageMap(prefs: SharedPreferences, usageMap: MutableMap<String, Int>) {
        try {
            val usageJson = JsonUtils.mapToJson(usageMap)
            prefs.edit {
                putString(KEY_MODEL_USAGE, usageJson)
                apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving usage map: ${e.message}")
        }
    }

    /**
     * Get default models when no usage history exists
     */
    private fun getDefaultModels(limit: Int): List<RecentModel> {
        return DEFAULT_MODEL_IDS
            .take(limit)
            .mapNotNull { modelId ->
                // Find model in ModelManager
                val model = ModelManager.models.find { it.id.equals(modelId, ignoreCase = true) }

                if (model != null) {
                    RecentModel(
                        displayName = model.displayName,
                        id = model.id,
                        iconResId = getIconForModelId(model.id)
                    )
                } else {
                    Timber.d("Default model $modelId not found in ModelManager")
                    null
                }
            }
            .distinctBy { it.id } // Ensure no duplicates in default models
    }

    /**
     * Get icon resource for a model ID
     */
    private fun getIconForModelId(modelId: String): Int {
        return when {
            modelId.contains("claude", ignoreCase = true) -> R.drawable.ic_claude
            modelId.contains("gpt", ignoreCase = true) -> R.drawable.ic_gpt
            modelId.contains("llama", ignoreCase = true) -> R.drawable.ic_llama
            modelId.contains("mistral", ignoreCase = true) -> R.drawable.ic_mistral
            modelId.contains("gemini", ignoreCase = true) -> R.drawable.ic_gemini
            modelId.contains("bagoodex", ignoreCase = true) -> R.drawable.ic_bagoodex
            else -> R.drawable.ic_robot // Default icon
        }
    }
}