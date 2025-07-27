package com.cyberflux.qwinai.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.cyberflux.qwinai.database.Converters

/**
 * Represents a conversation branch in the chat system
 * A branch is a specific path in the conversation tree
 */
@Entity(tableName = "branches")
@TypeConverters(Converters::class)
data class Branch(
    @PrimaryKey val id: String,
    val displayName: String,
    var parentBranchId: String? = null,
    var isActive: Boolean = false,
    // Store as a string that will be converted via type converter
    val messageIdsJson: String = "[]",  // We'll store as JSON string
    val createdFromMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val createdFromMessage: String,  // Original message that spawned this branch
    val branchDepth: Int = 0,        // How many branches deep this is
    val branchType: String = "USER_EDIT" // or "AI_REGENERATE"
) {


    companion object
}

