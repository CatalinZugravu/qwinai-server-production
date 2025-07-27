package com.cyberflux.qwinai.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    var title: String,
    var preview: String = "",
    var modelId: String,
    var timestamp: Long = System.currentTimeMillis(),
    var lastMessage: String = "",
    var lastModified: Long = System.currentTimeMillis(),
    var name: String = "",
    var userName: String = "user",  // Add this field
    var aiModel: String = "",
    var saved: Boolean = false,
    var locationCity: String? = null,
    var locationRegion: String? = null,
    var locationCountry: String? = null,
    @ColumnInfo(name = "token_count")
    var tokenCount: Int = 0,

    @ColumnInfo(name = "continued_past_warning")
    var continuedPastWarning: Boolean = false,

    @ColumnInfo(name = "last_token_update")
    var lastTokenUpdate: Long = 0,

    @ColumnInfo(name = "has_draft")
    var hasDraft: Boolean = false,
    @ColumnInfo(name = "draft_text")
    var draftText: String = "",
    @ColumnInfo(name = "draft_files")
    var draftFiles: String = "", // JSON serialized list of files
    @ColumnInfo(name = "draft_timestamp")
    var draftTimestamp: Long = 0
)