package com.cyberflux.qwinai.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cyberflux.qwinai.dao.ChatMessageDao
import com.cyberflux.qwinai.dao.ConversationDao
import com.cyberflux.qwinai.model.Branch
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.Conversation

/**
 * Main database for the application
 * Includes tables for conversations, messages, and branches
 */
@Database(
    entities = [ChatMessage::class, Conversation::class, Branch::class],
    version = 4, // Increased for message versioning support
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration(false) // Use this with caution in production
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}