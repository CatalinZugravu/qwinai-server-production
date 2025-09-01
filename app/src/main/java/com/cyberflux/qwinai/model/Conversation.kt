package com.cyberflux.qwinai.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [
        androidx.room.Index(value = ["timestamp"], name = "index_conversations_timestamp"),
        androidx.room.Index(value = ["modelId"], name = "index_conversations_model_id"),
        androidx.room.Index(value = ["saved"], name = "index_conversations_saved"),
        androidx.room.Index(value = ["lastModified"], name = "index_conversations_last_modified"),
        androidx.room.Index(value = ["has_draft"], name = "index_conversations_has_draft"),
        androidx.room.Index(value = ["timestamp", "saved"], name = "index_conversations_timestamp_saved")
    ]
)
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
    var draftTimestamp: Long = 0,
    
    // Enhanced comprehensive token tracking
    @ColumnInfo(name = "detailed_token_info")
    var detailedTokenInfo: String = "", // JSON with comprehensive token breakdown
    
    @ColumnInfo(name = "input_tokens")
    var inputTokens: Int = 0,
    
    @ColumnInfo(name = "output_tokens")
    var outputTokens: Int = 0,
    
    @ColumnInfo(name = "file_tokens")
    var fileTokens: Int = 0,
    
    @ColumnInfo(name = "system_tokens")
    var systemTokens: Int = 0,
    
    @ColumnInfo(name = "usage_percentage")
    var usagePercentage: Float = 0f,
    
    @ColumnInfo(name = "context_action")
    var contextAction: String = "PROCEED_NORMAL", // ContextAction enum name
    
    @ColumnInfo(name = "token_calculation_timestamp")
    var tokenCalculationTimestamp: Long = 0
)