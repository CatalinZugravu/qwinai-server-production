package com.cyberflux.qwinai.service

import com.cyberflux.qwinai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

/**
 * Unified WebSearchService that handles web search detection and execution
 * through Google Custom Search API
 * CRITICAL: This service ensures only ONE API call per search request
 */
object WebSearchService {
    // Google API configuration
    private val GOOGLE_API_KEY = BuildConfig.GOOGLE_API_KEY
    private val GOOGLE_SEARCH_ENGINE_ID = BuildConfig.GOOGLE_SEARCH_ENGINE_ID
    private const val GOOGLE_SEARCH_URL = "https://www.googleapis.com/customsearch/v1"

    // OkHttpClient with appropriate timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    // CRITICAL: Track ongoing searches to prevent duplicates
    private val ongoingSearches = mutableMapOf<String, SearchResponse>()
    private val searchLock = kotlinx.coroutines.sync.Mutex()

    // Search result data class
    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
        val displayLink: String,
        val datePublished: String? = null,
        val imageUrl: String? = null
    )

    data class SearchResponse(
        val success: Boolean,
        val results: List<SearchResult> = emptyList(),
        val error: String? = null,
        val query: String,
        val freshness: String? = null
    )

    fun cleanSearchQuery(query: String): String {
        // Basic query cleaning without complex detection
        return query
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .trim()
            .take(200) // Limit query length
    }

    suspend fun performSearch(
        query: String,
        freshness: String? = null,
        count: Int = 5
    ): SearchResponse = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 Starting web search for: $query (freshness: $freshness)")

            // Clean and encode the query
            val cleanedQuery = cleanSearchQuery(query)
            val searchKey = "${cleanedQuery}_${freshness}_${count}"
            
            // CRITICAL: Check if search is already in progress or completed
            searchLock.withLock {
                ongoingSearches[searchKey]?.let { cachedResult ->
                    Timber.d("✓ Returning cached search result for: $cleanedQuery")
                    return@withContext cachedResult
                }
            }
            
            val encodedQuery = URLEncoder.encode(cleanedQuery, "UTF-8")
            
            Timber.d("🔍 Making single Google API call for: $cleanedQuery")

            // Build base URL
            var url = "${GOOGLE_SEARCH_URL}?q=$encodedQuery&key=${GOOGLE_API_KEY}&cx=${GOOGLE_SEARCH_ENGINE_ID}&num=$count"

            // Add dateRestrict parameter if freshness is specified
            if (!freshness.isNullOrEmpty()) {
                val dateRestrict = convertFreshnessToDateRestrict(freshness)
                if (dateRestrict != null) {
                    url += "&dateRestrict=$dateRestrict"
                }
            }

            // Create and execute request
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "QwinAI/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("Google Search error: ${response.code} - ${response.message}")
                return@withContext SearchResponse(
                    success = false,
                    error = "Search error: ${response.code}",
                    query = cleanedQuery
                )
            }

            // Parse response body
            val responseBody = response.body?.string() ?: return@withContext SearchResponse(
                success = false,
                error = "Empty response",
                query = cleanedQuery
            )

            val results = parseSearchResults(responseBody)

            // Ensure all results have proper displayLink
            val enhancedResults = results.map { result ->
                result.copy(
                    displayLink = result.displayLink.ifEmpty {
                        try {
                            result.url.toUri().host ?: result.url
                        } catch (_: Exception) {
                            result.url
                        }
                    }
                )
            }

            Timber.d("✓ Google API returned ${enhancedResults.size} results for query: $cleanedQuery")

            val searchResponse = SearchResponse(
                success = true,
                results = enhancedResults,
                query = cleanedQuery,
                freshness = freshness
            )
            
            // CRITICAL: Cache the result to prevent duplicate API calls
            searchLock.withLock {
                ongoingSearches[searchKey] = searchResponse
            }
            
            return@withContext searchResponse
        } catch (e: Exception) {
            Timber.e(e, "Error during web search: ${e.message}")
            val errorResponse = SearchResponse(
                success = false,
                error = e.message,
                query = query
            )
            
            // Cache error response too to prevent retries
            val searchKey = "${cleanSearchQuery(query)}_${freshness}_${count}"
            searchLock.withLock {
                ongoingSearches[searchKey] = errorResponse
            }
            
            return@withContext errorResponse
        }
    }

    /**
     * Parse search results from API response
     */
    private fun parseSearchResults(responseBody: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val jsonResponse = JSONObject(responseBody)

        val items = jsonResponse.optJSONArray("items") ?: return results

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)

            // Extract metadata
            val pagemap = item.optJSONObject("pagemap")
            var publishedDate: String? = null
            var imageUrl: String? = null

            // Try to get published date
            pagemap?.optJSONArray("metatags")?.let { metatags ->
                if (metatags.length() > 0) {
                    val meta = metatags.getJSONObject(0)
                    publishedDate = meta.optString("article:published_time", null)
                    if (publishedDate.isNullOrEmpty()) {
                        publishedDate = meta.optString("datePublished", null)
                    }
                }
            }

            // Try to get image
            pagemap?.optJSONArray("cse_image")?.let { images ->
                if (images.length() > 0) {
                    imageUrl = images.getJSONObject(0).optString("src")
                }
            }

            results.add(
                SearchResult(
                    title = item.getString("title"),
                    url = item.getString("link"),
                    snippet = item.getString("snippet"),
                    displayLink = item.getString("displayLink"),
                    datePublished = publishedDate,
                    imageUrl = imageUrl
                )
            )
        }

        return results
    }

    /**
     * Format search results for AI consumption
     */
// In WebSearchService.kt - Update formatSearchResultsForAI
    fun formatSearchResultsForAI(results: List<SearchResult>, query: String): String {
        val sb = StringBuilder()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        sb.append("Current search results for: \"$query\" (${currentDate})\n\n")

        results.forEachIndexed { index, result ->
            sb.append("${index + 1}. ${result.title}\n")
            sb.append("${result.snippet}\n")
            sb.append("Source: ${result.displayLink}\n\n")
        }
        
        sb.append("IMPORTANT: Do not add a 'References' section at the end of your response. Sources are already handled by the UI.")

        return sb.toString()
    }

    private fun convertFreshnessToDateRestrict(freshness: String?): String? {
        return when (freshness?.lowercase()) {
            "hour" -> "h1"    // Last 1 hour
            "day" -> "d1"     // Last 1 day
            "week" -> "w1"    // Last 1 week
            "month" -> "m1"   // Last 1 month
            "year" -> "y1"    // Last 1 year
            else -> null
        }
    }

}