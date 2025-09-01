package com.cyberflux.qwinai.dao

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.ColumnInfo
import com.cyberflux.qwinai.model.Conversation

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(conversation: Conversation)

    @Update
    suspend fun update(conversation: Conversation)

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("SELECT * FROM conversations ORDER BY lastModified DESC")
    suspend fun getAllConversations(): List<Conversation>

    @Query("SELECT * FROM conversations ORDER BY lastModified DESC")
    fun getAllConversationsFlow(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: Long): Conversation

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM conversations WHERE saved = 0")
    suspend fun deleteAllUnsavedConversations()

    @Query("DELETE FROM conversations WHERE saved = 1")
    suspend fun deleteAllSavedConversations()

    @Query("DELETE FROM conversations WHERE timestamp < :timestamp AND saved = 0")
    suspend fun deleteOldConversations(timestamp: Long)

    @Query("SELECT * FROM conversations WHERE saved = 1 ORDER BY lastModified DESC")
    suspend fun getSavedConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE saved = 0 ORDER BY lastModified DESC")
    suspend fun getUnsavedConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    suspend fun searchConversations(query: String): List<Conversation>

    @Query("UPDATE conversations SET token_count = :tokenCount, continued_past_warning = :continuedPastWarning, last_token_update = :timestamp WHERE id = :conversationId")
    suspend fun updateTokenInfo(conversationId: String, tokenCount: Int, continuedPastWarning: Boolean, timestamp: Long)

    @Query("SELECT token_count FROM conversations WHERE id = :conversationId")
    suspend fun getTokenCount(conversationId: String): Int?

    @Query("SELECT continued_past_warning FROM conversations WHERE id = :conversationId")
    suspend fun getContinuedPastWarning(conversationId: String): Boolean?

    @Query("SELECT * FROM conversations WHERE has_draft = 1 ORDER BY draft_timestamp DESC")
    suspend fun getConversationsWithDrafts(): List<Conversation>

    @Query("SELECT COUNT(*) FROM conversations WHERE has_draft = 1")
    suspend fun countConversationsWithDrafts(): Int
    
    // Enhanced search methods
    @Query("""
        SELECT * FROM conversations 
        WHERE (title LIKE '%' || :query || '%' 
               OR preview LIKE '%' || :query || '%' 
               OR lastMessage LIKE '%' || :query || '%'
               OR name LIKE '%' || :query || '%')
        AND (:modelId IS NULL OR modelId = :modelId)
        AND (:onlySaved = 0 OR saved = 1)
        AND (:startDate IS NULL OR timestamp >= :startDate)
        AND (:endDate IS NULL OR timestamp <= :endDate)
        ORDER BY lastModified DESC
        LIMIT :limit
    """)
    suspend fun searchConversationsAdvanced(
        query: String,
        modelId: String? = null,
        onlySaved: Boolean = false,
        startDate: Long? = null,
        endDate: Long? = null,
        limit: Int = 100
    ): List<Conversation>
    
    @Query("""
        SELECT * FROM conversations 
        WHERE title LIKE :query || '%'
        ORDER BY 
            CASE WHEN title = :query THEN 1 ELSE 2 END,
            lastModified DESC
        LIMIT 10
    """)
    suspend fun searchConversationsByTitlePrefix(query: String): List<Conversation>
    
    @Query("SELECT DISTINCT modelId FROM conversations ORDER BY modelId")
    suspend fun getAllModelIds(): List<String>
    
    @Query("""
        SELECT * FROM conversations 
        WHERE timestamp BETWEEN :startDate AND :endDate
        ORDER BY timestamp DESC
    """)
    suspend fun getConversationsByDateRange(startDate: Long, endDate: Long): List<Conversation>
    
    @Query("""
        SELECT COUNT(*) FROM conversations 
        WHERE title LIKE '%' || :query || '%' 
           OR preview LIKE '%' || :query || '%' 
           OR lastMessage LIKE '%' || :query || '%'
           OR name LIKE '%' || :query || '%'
    """)
    suspend fun countSearchResults(query: String): Int
    
    // ===============================
    // COMPREHENSIVE TOKEN MANAGEMENT 
    // ===============================
    
    /**
     * Update comprehensive token information for a conversation
     */
    @Query("""
        UPDATE conversations SET 
            detailed_token_info = :detailedTokenInfo,
            input_tokens = :inputTokens,
            output_tokens = :outputTokens,
            file_tokens = :fileTokens,
            system_tokens = :systemTokens,
            usage_percentage = :usagePercentage,
            context_action = :contextAction,
            token_calculation_timestamp = :timestamp,
            token_count = :totalTokens,
            continued_past_warning = :continuedPastWarning,
            last_token_update = :timestamp
        WHERE id = :conversationId
    """)
    suspend fun updateComprehensiveTokenInfo(
        conversationId: String,
        detailedTokenInfo: String,
        inputTokens: Int,
        outputTokens: Int,
        fileTokens: Int,
        systemTokens: Int,
        usagePercentage: Float,
        contextAction: String,
        totalTokens: Int,
        continuedPastWarning: Boolean,
        timestamp: Long
    )
    
    /**
     * Get detailed token information for a conversation
     */
    @Query("SELECT detailed_token_info FROM conversations WHERE id = :conversationId")
    suspend fun getDetailedTokenInfo(conversationId: String): String?
    
    /**
     * Get input tokens for a conversation
     */
    @Query("SELECT input_tokens FROM conversations WHERE id = :conversationId")
    suspend fun getInputTokens(conversationId: String): Int?
    
    /**
     * Get output tokens for a conversation
     */
    @Query("SELECT output_tokens FROM conversations WHERE id = :conversationId")
    suspend fun getOutputTokens(conversationId: String): Int?
    
    /**
     * Get file tokens for a conversation
     */
    @Query("SELECT file_tokens FROM conversations WHERE id = :conversationId")
    suspend fun getFileTokens(conversationId: String): Int?
    
    /**
     * Get system tokens for a conversation
     */
    @Query("SELECT system_tokens FROM conversations WHERE id = :conversationId")
    suspend fun getSystemTokens(conversationId: String): Int?
    
    /**
     * Get usage percentage for a conversation
     */
    @Query("SELECT usage_percentage FROM conversations WHERE id = :conversationId")
    suspend fun getUsagePercentage(conversationId: String): Float?
    
    /**
     * Get context action for a conversation
     */
    @Query("SELECT context_action FROM conversations WHERE id = :conversationId")
    suspend fun getContextAction(conversationId: String): String?
    
    /**
     * Get model ID for a conversation
     */
    @Query("SELECT modelId FROM conversations WHERE id = :conversationId")
    suspend fun getModelId(conversationId: String): String?
    
    /**
     * Get conversations with high token usage (for management/cleanup)
     */
    @Query("""
        SELECT * FROM conversations 
        WHERE usage_percentage > :threshold 
        ORDER BY usage_percentage DESC
    """)
    suspend fun getHighUsageConversations(threshold: Float = 0.8f): List<Conversation>
    
    /**
     * Get token usage statistics for analytics
     */
    @Query("""
        SELECT 
            AVG(usage_percentage) as avg_usage,
            MAX(usage_percentage) as max_usage,
            COUNT(*) as total_conversations,
            SUM(CASE WHEN usage_percentage > 0.8 THEN 1 ELSE 0 END) as high_usage_count
        FROM conversations 
        WHERE token_calculation_timestamp > :sinceTimestamp
    """)
    suspend fun getTokenUsageStats(sinceTimestamp: Long): TokenUsageStats?
    
    /**
     * Clear detailed token info for conversations older than specified days
     */
    @Query("""
        UPDATE conversations SET 
            detailed_token_info = '',
            token_calculation_timestamp = 0
        WHERE token_calculation_timestamp > 0 
        AND token_calculation_timestamp < :cutoffTimestamp
    """)
    suspend fun clearOldDetailedTokenInfo(cutoffTimestamp: Long): Int

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversationById(conversationId: Long)

}

/**
 * Data class for token usage statistics
 */
data class TokenUsageStats(
    @ColumnInfo(name = "avg_usage") val avgUsage: Float,
    @ColumnInfo(name = "max_usage") val maxUsage: Float,
    @ColumnInfo(name = "total_conversations") val totalConversations: Int,
    @ColumnInfo(name = "high_usage_count") val highUsageCount: Int
)