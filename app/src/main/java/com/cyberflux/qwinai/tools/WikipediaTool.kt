package com.cyberflux.qwinai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tool for retrieving information from Wikipedia
 */
class WikipediaTool(private val context: Context) : Tool {
    override val id: String = "wikipedia"
    override val name: String = "Wikipedia Knowledge"
    override val description: String = "Retrieves information from Wikipedia for topics, people, places, and concepts."

    // Wikipedia API configuration
    private val BASE_URL = "https://en.wikipedia.org/api/rest_v1"
    private val SEARCH_URL = "https://en.wikipedia.org/w/api.php"

    // HTTP Client for API requests
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Patterns to recognize Wikipedia knowledge queries
    private val wikipediaPatterns = listOf(
        Pattern.compile("\\bwiki\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwikipedia\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwho\\s+(?:is|was)\\s+([\\w\\s]+)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwhat\\s+(?:is|was)\\s+([\\w\\s]+)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwhen\\s+(?:was|is|did)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwhere\\s+(?:is|was)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\btell\\s+me\\s+about\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\binformation\\s+about\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bdefinition\\s+of\\b", Pattern.CASE_INSENSITIVE)
    )

    override fun canHandle(message: String): Boolean {
        // Check if the message contains knowledge query patterns
        // but avoid triggering on general questions
        if (message.contains("weather") ||
            message.contains("calculate") ||
            message.contains("translate")) {
            return false
        }

        return wikipediaPatterns.any { it.matcher(message).find() }
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("WikipediaTool: Executing with message: ${message.take(50)}...")

            // Extract the search query
            val query = parameters["query"] as? String ?: extractQuery(message)

            if (query.isBlank()) {
                return@withContext ToolResult.error(
                    "No search query",
                    "I couldn't determine what topic you want information about. Please specify the topic more clearly."
                )
            }

            Timber.d("WikipediaTool: Searching for: $query")

            // Search for the topic
            val searchResults = searchWikipedia(query)

            if (searchResults.isEmpty()) {
                return@withContext ToolResult.error(
                    "No Wikipedia results found",
                    "I couldn't find any Wikipedia articles about '$query'. Please try a different search term."
                )
            }

            // Get the first search result
            val topResult = searchResults.first()

            // Get the summary for the top result
            val summary = fetchWikipediaSummary(topResult.pageId)

            if (summary == null) {
                return@withContext ToolResult.error(
                    "Failed to fetch Wikipedia summary",
                    "I found Wikipedia results for '$query', but couldn't retrieve the summary information."
                )
            }

            // Format the result
            val content = buildString {
                append("Wikipedia Information: ${summary.title}\n\n")
                append(summary.extract)
                append("\n\nSource: Wikipedia")
            }

            return@withContext ToolResult.success(
                content = content,
                data = mapOf(
                    "title" to summary.title,
                    "extract" to summary.extract,
                    "pageId" to summary.pageId,
                    "url" to summary.url
                ),
                metadata = mapOf(
                    "originalQuery" to query,
                    "source" to "Wikipedia"
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "WikipediaTool: Error fetching information: ${e.message}")
            return@withContext ToolResult.error(
                "Wikipedia information error: ${e.message}",
                "There was an error retrieving information from Wikipedia: ${e.message}"
            )
        }
    }

    /**
     * Extract the search query from the message
     */
    private fun extractQuery(message: String): String {
        // Try various patterns to extract the query
        val patterns = listOf(
            "who\\s+(?:is|was)\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "what\\s+(?:is|was)\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "when\\s+(?:was|is|did)\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "where\\s+(?:is|was)\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "tell\\s+me\\s+about\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "information\\s+about\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "definition\\s+of\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)",
            "wiki(?:pedia)?\\s+([\\w\\s,\\-]+?)(?:\\?|\\.|$)"
        )

        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(message)
            if (matcher.find()) {
                return matcher.group(1).trim()
            }
        }

        // If no specific pattern matches, try to extract the most likely topic
        // This is a simplistic approach and could be improved
        val words = message.split(" ")
        if (words.size > 3) {
            return words.subList(2, words.size).joinToString(" ").trim()
                .replace("?", "")
                .replace(".", "")
        }

        return message.trim()
    }

    /**
     * Search Wikipedia for articles matching the query
     */
    private suspend fun searchWikipedia(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?action=query&list=search&srsearch=$encodedQuery&format=json&srlimit=5"

            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList<SearchResult>()
                }

                val responseBody = response.body?.string() ?: return@withContext emptyList<SearchResult>()

                val searchResponse = JSONObject(responseBody)
                val searchResults = searchResponse.getJSONObject("query").getJSONArray("search")

                val results = mutableListOf<SearchResult>()

                for (i in 0 until searchResults.length()) {
                    val result = searchResults.getJSONObject(i)
                    val title = result.getString("title")
                    val pageId = result.getInt("pageid")
                    val snippet = result.getString("snippet")
                        .replace("<span class=\"searchmatch\">", "")
                        .replace("</span>", "")

                    results.add(SearchResult(title, pageId, snippet))
                }

                return@withContext results
            }
        } catch (e: Exception) {
            Timber.e(e, "Error searching Wikipedia: ${e.message}")
            return@withContext emptyList<SearchResult>()
        }
    }

    /**
     * Fetch a summary of a Wikipedia article by page ID
     */
    private suspend fun fetchWikipediaSummary(pageId: Int): WikipediaSummary? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/page/summary/$pageId"

            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }

                val responseBody = response.body?.string() ?: return@withContext null

                val summaryJson = JSONObject(responseBody)

                val title = summaryJson.getString("title")
                val extract = summaryJson.getString("extract")
                val pageUrl = summaryJson.getJSONObject("content_urls").getJSONObject("desktop").getString("page")

                return@withContext WikipediaSummary(pageId, title, extract, pageUrl)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Wikipedia summary: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Data class for Wikipedia search results
     */
    data class SearchResult(
        val title: String,
        val pageId: Int,
        val snippet: String
    )

    /**
     * Data class for Wikipedia article summaries
     */
    data class WikipediaSummary(
        val pageId: Int,
        val title: String,
        val extract: String,
        val url: String
    )
}