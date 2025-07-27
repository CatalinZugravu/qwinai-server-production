package com.cyberflux.qwinai.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
    fun getAllConversationsLive(): LiveData<List<Conversation>>

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
}