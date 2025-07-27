package com.cyberflux.qwinai.tools

import android.content.Context
import com.cyberflux.qwinai.service.WebSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Tool implementation for web search functionality
 * Simplified to rely on AI model for detection instead of manual detection logic
 */
class WebSearchTool(private val context: Context) : Tool {
    override val id: String = "web_search"
    override val name: String = "Web Search"
    override val description: String = "Searches the web for current information"

    companion object {
        private const val SEARCH_TIMEOUT_MS = 30000L // 30 seconds timeout

        // Additional parameters that can be passed to execute()
        const val PARAM_QUERY = "query"
        const val PARAM_FRESHNESS = "freshness"
        const val PARAM_COUNT = "count"
        const val PARAM_SAFE_SEARCH = "safe_search"
        const val PARAM_QUALITY_FILTER = "quality_filter"
    }

    override fun canHandle(message: String): Boolean {
        // Simplified: Let the AI model decide when to use this tool
        // We only do basic validation here
        return message.isNotBlank() && message.length >= 3
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("WebSearchTool executing with parameters: $parameters")

            // Extract parameters with fallbacks
            val query = parameters[PARAM_QUERY] as? String ?: message.trim()

            // Clean the query using WebSearchService
            val cleanedQuery = WebSearchService.cleanSearchQuery(query)

            val freshness = parameters[PARAM_FRESHNESS] as? String
            val count = parameters[PARAM_COUNT] as? Int ?: 5
            val safeSearch = parameters[PARAM_SAFE_SEARCH] as? Boolean ?: true
            val qualityFilter = parameters[PARAM_QUALITY_FILTER] as? String ?: "high"

            Timber.d("Executing web search: query='$cleanedQuery', freshness=$freshness, count=$count")

            // Apply timeout to prevent hanging
            return@withContext withTimeout(SEARCH_TIMEOUT_MS) {
                performSearch(cleanedQuery, freshness, count, safeSearch, qualityFilter, message)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Web search timed out after ${SEARCH_TIMEOUT_MS}ms")
            ToolResult.error(
                errorMessage = "Search timed out",
                content = "The web search took too long to complete. Please try a more specific query."
            )
        } catch (e: Exception) {
            Timber.e(e, "Error executing web search: ${e.message}")
            ToolResult.error(
                errorMessage = "Search error: ${e.message}",
                content = "Error searching the web: ${e.message}"
            )
        }
    }

    /**
     * Perform the actual search using ONLY WebSearchService to prevent duplicate API calls
     */
    private suspend fun performSearch(
        query: String,
        freshness: String?,
        count: Int,
        safeSearch: Boolean,
        qualityFilter: String,
        originalMessage: String
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 WebSearchTool using single API call via WebSearchService")
            
            // CRITICAL: Use only WebSearchService to prevent duplicate API calls
            // The WebSearchService already handles caching and deduplication
            val webSearchResponse = WebSearchService.performSearch(query, freshness, count)

            if (!webSearchResponse.success || webSearchResponse.results.isEmpty()) {
                return@withContext ToolResult.error(
                    errorMessage = webSearchResponse.error ?: "No search results found",
                    content = "No search results found for: $query",
                    metadata = mapOf(
                        "query" to query,
                        "freshness" to (freshness ?: "unknown")
                    )
                )
            }

            // Format the results from WebSearchService
            val formattedResults = WebSearchService.formatSearchResultsForAI(
                webSearchResponse.results,
                webSearchResponse.query
            )

            Timber.d("✓ WebSearchService returned ${webSearchResponse.results.size} results")

            ToolResult.success(
                content = formattedResults,
                data = webSearchResponse.results,
                metadata = mapOf(
                    "query" to webSearchResponse.query,
                    "freshness" to (webSearchResponse.freshness ?: "unknown"),
                    "result_count" to webSearchResponse.results.size,
                    "source" to "WebSearchService"
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error in performSearch: ${e.message}")
            ToolResult.error(
                errorMessage = "Search error: ${e.message}",
                content = "Error searching the web: ${e.message}"
            )
        }
    }

}