package com.cyberflux.qwinai.model

import android.net.Uri

/**
 * Represents an attachment in a conversation (image or file)
 */
data class ConversationAttachment(
    val id: String,
    val uri: Uri,
    val name: String,
    val type: AttachmentType,
    val mimeType: String,
    val size: Long,
    val timestamp: Long,
    val conversationId: String,
    val messageId: String? = null,
    val fullPath: String? = null // Full file path when available
) {
    
    /**
     * Get formatted file size
     */
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * Get file extension from name
     */
    fun getFileExtension(): String {
        return name.substringAfterLast('.', "").uppercase()
    }
    
    /**
     * Get truncated file name for display
     */
    fun getTruncatedName(maxLength: Int = 12): String {
        return if (name.length <= maxLength) {
            name
        } else {
            val extension = getFileExtension()
            val nameWithoutExtension = name.substringBeforeLast('.')
            val truncatedName = nameWithoutExtension.take(maxLength - extension.length - 1)
            "$truncatedName...$extension"
        }
    }
    
    /**
     * Get display path - prefers full path over filename
     */
    fun getDisplayPath(): String {
        return fullPath ?: name
    }
    
    /**
     * Get truncated display path for UI
     */
    fun getTruncatedDisplayPath(maxLength: Int = 30): String {
        val displayPath = getDisplayPath()
        return if (displayPath.length <= maxLength) {
            displayPath
        } else {
            val extension = getFileExtension()
            // Show beginning and end of path
            val halfLength = (maxLength - 3 - extension.length) / 2
            val start = displayPath.take(halfLength)
            val end = displayPath.takeLast(halfLength + extension.length)
            "$start...$end"
        }
    }
}

/**
 * Attachment type enum
 */
enum class AttachmentType {
    IMAGE,
    DOCUMENT
}