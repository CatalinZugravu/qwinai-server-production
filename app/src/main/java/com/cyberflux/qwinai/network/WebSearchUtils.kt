package com.cyberflux.qwinai.network

import timber.log.Timber
import java.util.Calendar

/**
 * Utility class for web search detection and analysis
 * Designed to work seamlessly with AI models that have auto web search capability
 */
object WebSearchUtils {

    /**
     * Analyzes a message to determine if web search is needed and extract time-based parameters
     * This is a lightweight heuristic that complements the AI model's auto search capability
     * 
     * @param message The user's message to analyze
     * @return Pair<Boolean, Map<String, String>> where Boolean indicates if search is needed
     *         and Map contains time-related parameters like freshness
     */
    fun analyzeMessage(message: String): Pair<Boolean, Map<String, String>> {
        if (message.isBlank()) {
            return Pair(false, emptyMap())
        }

        val lowerMessage = message.lowercase().trim()
        val timeParams = mutableMapOf<String, String>()

        // Check for current/recent information indicators
        val needsWebSearch = containsCurrentEventIndicator(lowerMessage) ||
                containsSearchKeywords(lowerMessage) ||
                containsTimeIndicators(lowerMessage)

        // Extract time-based parameters for freshness
        when {
            containsRecentTimeIndicators(lowerMessage) -> {
                timeParams["freshness"] = "day"
            }
            containsTodayIndicators(lowerMessage) -> {
                timeParams["freshness"] = "hour" 
            }
            containsThisWeekIndicators(lowerMessage) -> {
                timeParams["freshness"] = "week"
            }
            containsThisMonthIndicators(lowerMessage) -> {
                timeParams["freshness"] = "month"
            }
            else -> {
                timeParams["freshness"] = "recent"
            }
        }

        Timber.d("WebSearchUtils analysis: needsSearch=$needsWebSearch, params=$timeParams")
        return Pair(needsWebSearch, timeParams)
    }

    /**
     * Check if message contains indicators for current events or recent information
     */
    fun containsCurrentEventIndicator(message: String): Boolean {
        val currentIndicators = listOf(
            "latest", "recent", "current", "now", "today", "this week", "this month",
            "what's happening", "news", "breaking", "update", "currently", "right now",
            "as of", "up to date", "newest", "fresh", "live", "real-time"
        )
        
        return currentIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
    }

    private fun containsSearchKeywords(message: String): Boolean {
        val searchKeywords = listOf(
            "search", "find", "look up", "google", "check", "what is", "who is", "where is",
            "when is", "how much", "price of", "cost of", "information about", "tell me about"
        )
        
        return searchKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }

    private fun containsTimeIndicators(message: String): Boolean {
        val timeIndicators = listOf(
            "2025", "2024", getCurrentYear().toString(), "january", "february", "march", 
            "april", "may", "june", "july", "august", "september", "october", 
            "november", "december", "monday", "tuesday", "wednesday", "thursday", 
            "friday", "saturday", "sunday", "yesterday", "tomorrow"
        )
        
        return timeIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
    }

    private fun containsRecentTimeIndicators(message: String): Boolean {
        val recentIndicators = listOf(
            "today", "right now", "currently", "at the moment", "this morning", 
            "this afternoon", "this evening", "tonight"
        )
        
        return recentIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
    }

    private fun containsTodayIndicators(message: String): Boolean {
        val todayIndicators = listOf(
            "right now", "live", "real-time", "immediate", "instant", "current status"
        )
        
        return todayIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
    }

    private fun containsThisWeekIndicators(message: String): Boolean {
        val weekIndicators = listOf(
            "this week", "recent", "lately", "past few days", "last few days"
        )
        
        return weekIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
    }

    private fun containsThisMonthIndicators(message: String): Boolean {
        val monthIndicators = listOf(
            "this month", "past month", "last month", "recent weeks"
        )
        
        return monthIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
    }

    private fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }
}