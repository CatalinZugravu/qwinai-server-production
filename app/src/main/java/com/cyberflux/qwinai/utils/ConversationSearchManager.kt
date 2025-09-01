package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.dao.ChatMessageDao
import com.cyberflux.qwinai.dao.ConversationDao
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive conversation search manager
 * Provides advanced search functionality for conversations and messages
 */
class ConversationSearchManager(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao
) {
    
    private val searchHistory = mutableListOf<String>()
    private val searchSuggestions = ConcurrentHashMap<String, Int>()
    private val recentSearches = mutableListOf<SearchQuery>()
    
    companion object {
        private const val MAX_SEARCH_HISTORY = 50
        private const val MAX_SEARCH_SUGGESTIONS = 20
        private const val MIN_QUERY_LENGTH = 2
        private const val SEARCH_RESULTS_LIMIT = 100
    }
    
    /**
     * Search query with metadata
     */
    data class SearchQuery(
        val query: String,
        val timestamp: Long,
        val filters: SearchFilters = SearchFilters()
    )
    
    /**
     * Search filters for advanced search
     */
    data class SearchFilters(
        val includeConversations: Boolean = true,
        val includeMessages: Boolean = true,
        val onlyUserMessages: Boolean = false,
        val onlyAiMessages: Boolean = false,
        val onlySavedConversations: Boolean = false,
        val modelId: String? = null,
        val dateRange: DateRange? = null,
        val minTokenCount: Int? = null,
        val maxTokenCount: Int? = null
    )
    
    /**
     * Date range for search filtering
     */
    data class DateRange(
        val startDate: Long,
        val endDate: Long
    )
    
    /**
     * Search result types
     */
    sealed class SearchResult {
        abstract val relevanceScore: Float
        abstract val snippet: String
        abstract val timestamp: Long
        
        data class ConversationResult(
            val conversation: Conversation,
            override val relevanceScore: Float,
            override val snippet: String,
            override val timestamp: Long,
            val matchedFields: List<String>
        ) : SearchResult()
        
        data class MessageResult(
            val message: ChatMessage,
            val conversation: Conversation,
            override val relevanceScore: Float,
            override val snippet: String,
            override val timestamp: Long,
            val contextMessages: List<ChatMessage> = emptyList()
        ) : SearchResult()
    }
    
    /**
     * Combined search results with metadata
     */
    data class SearchResults(
        val query: String,
        val results: List<SearchResult>,
        val totalCount: Int,
        val searchTime: Long,
        val suggestions: List<String> = emptyList(),
        val filters: SearchFilters
    )
    
    /**
     * Perform comprehensive search across conversations and messages
     */
    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters(),
        limit: Int = SEARCH_RESULTS_LIMIT
    ): SearchResults = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        
        try {
            // Validate query
            if (query.length < MIN_QUERY_LENGTH) {
                return@withContext SearchResults(
                    query = query,
                    results = emptyList(),
                    totalCount = 0,
                    searchTime = 0,
                    suggestions = getSearchSuggestions(query),
                    filters = filters
                )
            }
            
            // Store search query
            storeSearchQuery(query, filters)
            
            val allResults = mutableListOf<SearchResult>()
            
            // Search conversations if enabled
            if (filters.includeConversations) {
                val conversationResults = searchConversations(query, filters)
                allResults.addAll(conversationResults)
            }
            
            // Search messages if enabled
            if (filters.includeMessages) {
                val messageResults = searchMessages(query, filters)
                allResults.addAll(messageResults)
            }
            
            // Sort by relevance score
            val sortedResults = allResults.sortedByDescending { it.relevanceScore }
                .take(limit)
            
            val searchTime = System.currentTimeMillis() - startTime
            
            Timber.d("Search completed: query='$query', results=${sortedResults.size}, time=${searchTime}ms")
            
            SearchResults(
                query = query,
                results = sortedResults,
                totalCount = allResults.size,
                searchTime = searchTime,
                suggestions = getSearchSuggestions(query),
                filters = filters
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error performing search: $query")
            SearchResults(
                query = query,
                results = emptyList(),
                totalCount = 0,
                searchTime = System.currentTimeMillis() - startTime,
                suggestions = getSearchSuggestions(query),
                filters = filters
            )
        }
    }
    
    /**
     * Search conversations with advanced filtering
     */
    private suspend fun searchConversations(
        query: String,
        filters: SearchFilters
    ): List<SearchResult.ConversationResult> {
        
        // Get all conversations (we'll filter in memory for now)
        val allConversations = if (filters.onlySavedConversations) {
            conversationDao.getSavedConversations()
        } else {
            conversationDao.getAllConversations()
        }
        
        return allConversations.mapNotNull { conversation ->
            val matchResult = matchConversationAgainstQuery(conversation, query, filters)
            if (matchResult != null) {
                SearchResult.ConversationResult(
                    conversation = conversation,
                    relevanceScore = matchResult.score,
                    snippet = matchResult.snippet,
                    timestamp = conversation.lastModified,
                    matchedFields = matchResult.matchedFields
                )
            } else null
        }
    }
    
    /**
     * Search messages with context
     */
    private suspend fun searchMessages(
        query: String,
        filters: SearchFilters
    ): List<SearchResult.MessageResult> {
        
        // Get all messages (this could be optimized with better database queries)
        val allMessages = chatMessageDao.getAllMessages()
        
        val results = mutableListOf<SearchResult.MessageResult>()
        val conversationCache = mutableMapOf<String, Conversation>()
        
        for (message in allMessages) {
            val matchResult = matchMessageAgainstQuery(message, query, filters)
            if (matchResult != null) {
                
                // Get conversation for this message
                val conversation = try {
                    conversationCache.getOrPut(message.conversationId) {
                        conversationDao.getConversationById(message.conversationId.toLongOrNull() ?: 0L)
                    }
                } catch (e: Exception) {
                    Timber.w("Could not find conversation for message: ${message.conversationId}")
                    continue
                }
                
                // Get context messages (previous and next messages)
                val contextMessages = getContextMessages(message, 2)
                
                results.add(
                    SearchResult.MessageResult(
                        message = message,
                        conversation = conversation,
                        relevanceScore = matchResult.score,
                        snippet = matchResult.snippet,
                        timestamp = message.timestamp,
                        contextMessages = contextMessages
                    )
                )
            }
        }
        
        return results
    }
    
    /**
     * Match conversation against search query
     */
    private fun matchConversationAgainstQuery(
        conversation: Conversation,
        query: String,
        filters: SearchFilters
    ): MatchResult? {
        
        // Apply filters
        if (!applyConversationFilters(conversation, filters)) {
            return null
        }
        
        val queryLower = query.lowercase()
        val matchedFields = mutableListOf<String>()
        var totalScore = 0f
        
        // Search in title (highest weight)
        if (conversation.title.lowercase().contains(queryLower)) {
            matchedFields.add("title")
            totalScore += calculateMatchScore(conversation.title, query, 3.0f)
        }
        
        // Search in preview
        if (conversation.preview.lowercase().contains(queryLower)) {
            matchedFields.add("preview")
            totalScore += calculateMatchScore(conversation.preview, query, 2.0f)
        }
        
        // Search in last message
        if (conversation.lastMessage.lowercase().contains(queryLower)) {
            matchedFields.add("lastMessage")
            totalScore += calculateMatchScore(conversation.lastMessage, query, 2.0f)
        }
        
        // Search in name
        if (conversation.name.lowercase().contains(queryLower)) {
            matchedFields.add("name")
            totalScore += calculateMatchScore(conversation.name, query, 1.5f)
        }
        
        // Search in model ID
        if (conversation.modelId.lowercase().contains(queryLower)) {
            matchedFields.add("modelId")
            totalScore += calculateMatchScore(conversation.modelId, query, 1.0f)
        }
        
        return if (matchedFields.isNotEmpty()) {
            MatchResult(
                score = totalScore,
                snippet = generateConversationSnippet(conversation, query, matchedFields),
                matchedFields = matchedFields
            )
        } else null
    }
    
    /**
     * Match message against search query
     */
    private fun matchMessageAgainstQuery(
        message: ChatMessage,
        query: String,
        filters: SearchFilters
    ): MatchResult? {
        
        // Apply filters
        if (!applyMessageFilters(message, filters)) {
            return null
        }
        
        val queryLower = query.lowercase()
        
        // Search in message content
        if (message.message.lowercase().contains(queryLower)) {
            val score = calculateMatchScore(message.message, query, 1.0f)
            val snippet = generateMessageSnippet(message.message, query)
            
            return MatchResult(
                score = score,
                snippet = snippet,
                matchedFields = listOf("message")
            )
        }
        
        return null
    }
    
    /**
     * Apply conversation filters
     */
    private fun applyConversationFilters(conversation: Conversation, filters: SearchFilters): Boolean {
        // Date range filter
        if (filters.dateRange != null) {
            if (conversation.timestamp < filters.dateRange.startDate || 
                conversation.timestamp > filters.dateRange.endDate) {
                return false
            }
        }
        
        // Model filter
        if (filters.modelId != null && conversation.modelId != filters.modelId) {
            return false
        }
        
        // Token count filter
        if (filters.minTokenCount != null && conversation.tokenCount < filters.minTokenCount) {
            return false
        }
        
        if (filters.maxTokenCount != null && conversation.tokenCount > filters.maxTokenCount) {
            return false
        }
        
        return true
    }
    
    /**
     * Apply message filters
     */
    private fun applyMessageFilters(message: ChatMessage, filters: SearchFilters): Boolean {
        // User/AI message filter
        if (filters.onlyUserMessages && !message.isUser) {
            return false
        }
        
        if (filters.onlyAiMessages && message.isUser) {
            return false
        }
        
        // Date range filter
        if (filters.dateRange != null) {
            if (message.timestamp < filters.dateRange.startDate || 
                message.timestamp > filters.dateRange.endDate) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Calculate match score based on various factors
     */
    private fun calculateMatchScore(text: String, query: String, baseWeight: Float): Float {
        val textLower = text.lowercase()
        val queryLower = query.lowercase()
        
        var score = 0f
        
        // Exact match (highest score)
        if (textLower == queryLower) {
            score += baseWeight * 10f
        }
        
        // Starts with query
        else if (textLower.startsWith(queryLower)) {
            score += baseWeight * 5f
        }
        
        // Contains query as whole word
        else if (textLower.contains("\\b$queryLower\\b".toRegex())) {
            score += baseWeight * 3f
        }
        
        // Contains query
        else if (textLower.contains(queryLower)) {
            score += baseWeight * 1f
        }
        
        // Boost score for shorter text (more relevant)
        val lengthBonus = maxOf(0f, (100f - text.length) / 100f)
        score += lengthBonus * baseWeight * 0.5f
        
        return score
    }
    
    /**
     * Generate conversation snippet for search results
     */
    private fun generateConversationSnippet(
        conversation: Conversation,
        query: String,
        matchedFields: List<String>
    ): String {
        val queryLower = query.lowercase()
        
        // Prioritize fields by importance
        val fieldPriority = listOf("title", "preview", "lastMessage", "name")
        
        for (field in fieldPriority) {
            if (matchedFields.contains(field)) {
                val text = when (field) {
                    "title" -> conversation.title
                    "preview" -> conversation.preview
                    "lastMessage" -> conversation.lastMessage
                    "name" -> conversation.name
                    else -> ""
                }
                
                if (text.isNotEmpty()) {
                    return highlightQuery(text, query, maxLength = 150)
                }
            }
        }
        
        return conversation.title.take(100)
    }
    
    /**
     * Generate message snippet for search results
     */
    private fun generateMessageSnippet(message: String, query: String): String {
        return highlightQuery(message, query, maxLength = 200)
    }
    
    /**
     * Highlight query in text and create snippet
     */
    private fun highlightQuery(text: String, query: String, maxLength: Int): String {
        val queryLower = query.lowercase()
        val textLower = text.lowercase()
        
        val index = textLower.indexOf(queryLower)
        
        return if (index != -1) {
            // Create snippet around the match
            val start = maxOf(0, index - maxLength / 2)
            val end = minOf(text.length, start + maxLength)
            
            val snippet = text.substring(start, end)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < text.length) "..." else ""
            
            "$prefix$snippet$suffix"
        } else {
            text.take(maxLength)
        }
    }
    
    /**
     * Get context messages around a specific message
     */
    private suspend fun getContextMessages(message: ChatMessage, contextSize: Int): List<ChatMessage> {
        return try {
            val allMessages = chatMessageDao.getMessagesByConversation(message.conversationId)
            val messageIndex = allMessages.indexOfFirst { it.id == message.id }
            
            if (messageIndex == -1) return emptyList()
            
            val start = maxOf(0, messageIndex - contextSize)
            val end = minOf(allMessages.size, messageIndex + contextSize + 1)
            
            allMessages.subList(start, end).filter { it.id != message.id }
        } catch (e: Exception) {
            Timber.e(e, "Error getting context messages")
            emptyList()
        }
    }
    
    /**
     * Store search query for history and suggestions
     */
    private fun storeSearchQuery(query: String, filters: SearchFilters) {
        // Add to search history
        searchHistory.add(0, query)
        if (searchHistory.size > MAX_SEARCH_HISTORY) {
            searchHistory.removeAt(searchHistory.size - 1)
        }
        
        // Update suggestions count
        val count = searchSuggestions.getOrDefault(query, 0)
        searchSuggestions[query] = count + 1
        
        // Limit suggestions size
        if (searchSuggestions.size > MAX_SEARCH_SUGGESTIONS) {
            val sortedEntries = searchSuggestions.toList().sortedByDescending { it.second }
            searchSuggestions.clear()
            searchSuggestions.putAll(sortedEntries.take(MAX_SEARCH_SUGGESTIONS))
        }
        
        // Add to recent searches
        recentSearches.add(0, SearchQuery(query, System.currentTimeMillis(), filters))
        if (recentSearches.size > 20) {
            recentSearches.removeAt(recentSearches.size - 1)
        }
    }
    
    /**
     * Get search suggestions based on history
     */
    private fun getSearchSuggestions(partialQuery: String): List<String> {
        if (partialQuery.length < 2) return emptyList()
        
        val queryLower = partialQuery.lowercase()
        
        return searchSuggestions.keys
            .filter { it.lowercase().startsWith(queryLower) && it != partialQuery }
            .sortedByDescending { searchSuggestions[it] ?: 0 }
            .take(5)
    }
    
    /**
     * Get recent search queries
     */
    fun getRecentSearches(): List<SearchQuery> {
        return recentSearches.toList()
    }
    
    /**
     * Clear search history
     */
    fun clearSearchHistory() {
        searchHistory.clear()
        searchSuggestions.clear()
        recentSearches.clear()
        Timber.d("Search history cleared")
    }
    
    /**
     * Get search statistics
     */
    fun getSearchStatistics(): Map<String, Any> {
        return mapOf(
            "totalSearches" to searchSuggestions.values.sum(),
            "uniqueQueries" to searchSuggestions.size,
            "recentSearches" to recentSearches.size,
            "topQueries" to searchSuggestions.toList()
                .sortedByDescending { it.second }
                .take(5)
                .toMap()
        )
    }
    
    /**
     * Internal match result
     */
    private data class MatchResult(
        val score: Float,
        val snippet: String,
        val matchedFields: List<String>
    )
}