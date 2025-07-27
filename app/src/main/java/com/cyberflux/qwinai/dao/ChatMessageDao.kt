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
}