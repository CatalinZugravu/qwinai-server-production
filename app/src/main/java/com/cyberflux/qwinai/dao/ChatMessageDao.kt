package com.cyberflux.qwinai.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cyberflux.qwinai.model.ChatMessage

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    @Update
    suspend fun update(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversation(conversationId: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    fun getMessagesByConversationId(
        conversationId: String,
        offset: Int,
        limit: Int = 100
    ): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE aiGroupId = :groupId ORDER BY timestamp ASC")
    suspend fun getVersionsForGroup(groupId: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND isUser = 0")
    suspend fun getAiMessagesForConversation(conversationId: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForConversation(conversationId: String): List<ChatMessage>

    @Delete
    suspend fun delete(message: ChatMessage)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<ChatMessage>
    
    @Query("SELECT * FROM chat_messages WHERE isGenerating = 1 AND isUser = 0 ORDER BY timestamp DESC")
    suspend fun getGeneratingMessages(): List<ChatMessage>
    
    // Enhanced search methods for messages
    @Query("""
        SELECT * FROM chat_messages 
        WHERE message LIKE '%' || :query || '%'
        AND (:onlyUserMessages = 0 OR isUser = 1)
        AND (:onlyAiMessages = 0 OR isUser = 0)
        AND (:startDate IS NULL OR timestamp >= :startDate)
        AND (:endDate IS NULL OR timestamp <= :endDate)
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessages(
        query: String,
        onlyUserMessages: Boolean = false,
        onlyAiMessages: Boolean = false,
        startDate: Long? = null,
        endDate: Long? = null,
        limit: Int = 100
    ): List<ChatMessage>
    
    @Query("""
        SELECT * FROM chat_messages 
        WHERE conversationId = :conversationId 
        AND message LIKE '%' || :query || '%'
        ORDER BY timestamp ASC
    """)
    suspend fun searchMessagesInConversation(conversationId: String, query: String): List<ChatMessage>
    
    @Query("""
        SELECT COUNT(*) FROM chat_messages 
        WHERE message LIKE '%' || :query || '%'
    """)
    suspend fun countMessageSearchResults(query: String): Int
    
    @Query("""
        SELECT * FROM chat_messages 
        WHERE timestamp BETWEEN :startDate AND :endDate
        ORDER BY timestamp DESC
    """)
    suspend fun getMessagesByDateRange(startDate: Long, endDate: Long): List<ChatMessage>
    
    @Query("""
        SELECT * FROM chat_messages 
        WHERE conversationId = :conversationId
        AND timestamp >= (SELECT timestamp FROM chat_messages WHERE id = :messageId) - :contextWindow
        AND timestamp <= (SELECT timestamp FROM chat_messages WHERE id = :messageId) + :contextWindow
        ORDER BY timestamp ASC
    """)
    suspend fun getMessageContext(
        conversationId: String, 
        messageId: String, 
        contextWindow: Long = 300000 // 5 minutes
    ): List<ChatMessage>
    
    @Query("SELECT DISTINCT conversationId FROM chat_messages")
    suspend fun getAllConversationIds(): List<String>
    
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}