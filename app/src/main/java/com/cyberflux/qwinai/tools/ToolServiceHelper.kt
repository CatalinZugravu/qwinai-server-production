package com.cyberflux.qwinai.tools

import android.content.Context
import com.cyberflux.qwinai.network.AimlApiRequest
import com.cyberflux.qwinai.service.WebSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * Helper class for integrating tools with existing services
 * Acts as a bridge between the new tool system and existing code
 */
object ToolServiceHelper {
    // Singleton instance of ToolManager
    private val toolManagerRef = AtomicReference<ToolManager?>(null)

    /**
     * Get or create the ToolManager instance
     * @param context Context to use for creating ToolManager
     * @return ToolManager instance
     */
    fun getToolManager(context: Context): ToolManager {
        return toolManagerRef.get() ?: synchronized(this) {
            toolManagerRef.get() ?: ToolManager(context).also {
                toolManagerRef.set(it)
                Timber.d("Created new ToolManager instance")
            }
        }
    }

    suspend fun executeWebSearch(
        context: Context,
        message: String,
        parameters: Map<String, Any> = emptyMap()
    ): ToolResult? = withContext(Dispatchers.IO) {
        try {
            val toolManager = getToolManager(context)

            // Extract or use the provided query
            val query = parameters["query"] as? String
                ?: parameters["extracted_query"] as? String
                ?: WebSearchService.cleanSearchQuery(message)

            Timber.d("Executing web search via tool system:")
            Timber.d("  - Input message: ${message.take(50)}...")
            Timber.d("  - Final query: $query")
            Timber.d("  - Parameters: $parameters")

            // Prepare final parameters with the correct query
            val finalParams = parameters.toMutableMap()
            finalParams["query"] = query

            val result = toolManager.executeToolById("web_search", query, finalParams)

            if (result.success) {
                Timber.d("Web search executed successfully via tool system for query: '$query'")
                Timber.d("Result content length: ${result.content.length}")
            } else {
                Timber.w("Web search tool execution failed for query '$query': ${result.error}")
            }

            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "Error executing web search via tool system: ${e.message}")
            return@withContext null
        }
    }
    /**
     * Create an AimlApiRequest.Message containing web search results
     * @param context Context to use for getting ToolManager
     * @param message User message or search query
     * @param parameters Additional parameters for the search
     * @return Message with search results or null if search failed
     */
    suspend fun createWebSearchMessage(
        context: Context,
        message: String,
        parameters: Map<String, Any> = emptyMap()
    ): AimlApiRequest.Message? = withContext(Dispatchers.IO) {
        try {
            // IMPORTANT: Extract the actual search query from the message
            val extractedQuery = parameters["extracted_query"] as? String
                ?: WebSearchService.cleanSearchQuery(message)

            Timber.d("Creating web search message:")
            Timber.d("  - Original message: ${message.take(50)}...")
            Timber.d("  - Extracted query: $extractedQuery")
            Timber.d("  - Parameters: $parameters")

            // Prepare search parameters with the extracted query
            val searchParams = parameters.toMutableMap()
            searchParams["query"] = extractedQuery

            // Execute the search with the proper query
            val result = executeWebSearch(context, extractedQuery, searchParams)

            if (result == null || !result.success || result.content.isBlank()) {
                Timber.w("Failed to create web search message: ${result?.error ?: "No result"}")
                return@withContext null
            }

            // Get the actual query that was used from the result metadata
            val actualQuery = result.metadata["query"] as? String ?: extractedQuery

            // Create message with the search results
            val searchMessage = AimlApiRequest.Message(
                role = "system",
                content = """Web search results for "${actualQuery}":

${result.content}

Please use these current web search results to provide an accurate, up-to-date response. 
Cite sources when referencing specific information. Prioritize recent and authoritative sources."""
            )

            Timber.d("Created web search message with ${result.content.length} chars of content for query: '$actualQuery'")
            return@withContext searchMessage
        } catch (e: Exception) {
            Timber.e(e, "Error creating web search message: ${e.message}")
            return@withContext null
        }
    }
    /**
     * Determine if web search should be enabled for a message
     * Uses both old and new detection systems for maximum coverage
     * @param context Context to use for getting ToolManager
     * @param message User message to analyze
     * @return true if web search should be enabled
     */
// In ToolServiceHelper.kt - Add this method
    fun shouldEnableWebSearch(context: Context, message: String): Boolean {
        try {
            val toolManager = getToolManager(context)
            val webSearchTool = toolManager.getToolById("web_search")

            if (webSearchTool != null && webSearchTool.canHandle(message)) {
                return true
            }

            // Additional multilingual checks
            val lowerMessage = message.lowercase()

            // Romanian specific patterns
            val romanianWebSearchPatterns = listOf(
                Regex("(caută|găsește|află)\\s+(despre|informații|date|știri)", RegexOption.IGNORE_CASE),
                Regex("(vreau să știu|spune-mi|arată-mi)\\s+(despre|ce|cine|unde)", RegexOption.IGNORE_CASE),
                Regex("(ultimele|noi|recente)\\s+(știri|evenimente|informații)", RegexOption.IGNORE_CASE)
            )

            for (pattern in romanianWebSearchPatterns) {
                if (pattern.containsMatchIn(lowerMessage)) {
                    Timber.d("Romanian web search pattern detected")
                    return true
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in shouldEnableWebSearch: ${e.message}")
        }

        return false
    }}