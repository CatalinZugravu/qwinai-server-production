package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.cyberflux.qwinai.dao.ChatMessageDao
import com.cyberflux.qwinai.model.AttachmentType
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.ConversationAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import android.util.Log

/**
 * Manager for handling conversation attachments
 */
class ConversationAttachmentsManager(
    private val context: Context,
    private val chatMessageDao: ChatMessageDao
) {
    
    companion object {
        private const val TAG = "AttachmentsManager"
    }
    
    /**
     * Get all attachments for a conversation, sorted with images first, then documents
     */
    suspend fun getConversationAttachments(conversationId: String): List<ConversationAttachment> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all messages from the conversation that have files or images
                val messages = chatMessageDao.getMessagesByConversation(conversationId)
                val attachments = mutableListOf<ConversationAttachment>()
                
                for (message in messages) {
                    // Extract attachments from each message
                    val messageAttachments = extractAttachmentsFromMessage(message)
                    attachments.addAll(messageAttachments)
                }
                
                // Sort: images first, then documents, then by timestamp
                attachments.sortedWith(compareBy<ConversationAttachment> { 
                    when (it.type) {
                        AttachmentType.IMAGE -> 0
                        AttachmentType.DOCUMENT -> 1
                    }
                }.thenBy { it.timestamp })
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting conversation attachments", e)
                emptyList()
            }
        }
    }
    
    /**
     * Extract attachments from a chat message
     * Uses the attachments field if available, otherwise falls back to legacy methods
     */
    private suspend fun extractAttachmentsFromMessage(message: ChatMessage): List<ConversationAttachment> {
        return withContext(Dispatchers.IO) {
            val attachments = mutableListOf<ConversationAttachment>()
            
            try {
                // Method 1: Use the new attachments field (preferred)
                if (!message.attachments.isNullOrEmpty()) {
                    try {
                        val attachmentsData = com.squareup.moshi.Moshi.Builder().build()
                            .adapter<List<Map<String, Any>>>(
                                com.squareup.moshi.Types.newParameterizedType(
                                    List::class.java,
                                    Map::class.java
                                )
                            ).fromJson(message.attachments) ?: emptyList()
                        
                        for (attachmentData in attachmentsData) {
                            try {
                                val uriString = attachmentData["uri"] as? String ?: continue
                                val name = attachmentData["name"] as? String ?: "Unknown File"
                                val size = (attachmentData["size"] as? Number)?.toLong() ?: 0L
                                val mimeType = attachmentData["mimeType"] as? String ?: "application/octet-stream"
                                
                                val uri = Uri.parse(uriString)
                                val attachmentType = if (mimeType.startsWith("image/")) {
                                    AttachmentType.IMAGE
                                } else {
                                    AttachmentType.DOCUMENT
                                }
                                
                                val attachment = ConversationAttachment(
                                    id = UUID.randomUUID().toString(),
                                    uri = uri,
                                    name = name,
                                    type = attachmentType,
                                    mimeType = mimeType,
                                    size = size,
                                    timestamp = message.timestamp,
                                    conversationId = message.conversationId,
                                    messageId = message.id,
                                    fullPath = getRealPathFromURI(uri)
                                )
                                attachments.add(attachment)
                                
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse attachment data: $attachmentData", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse attachments JSON: ${message.attachments}", e)
                    }
                }
                
                // Method 2: Legacy fallback - Check if message has image/document flags
                if (attachments.isEmpty() && (message.isImage || message.isDocument)) {
                    // Try to extract URI from message content
                    val uris = extractUrisFromText(message.message)
                    for (uri in uris) {
                        val attachment = createAttachmentFromUri(
                            uri = uri,
                            conversationId = message.conversationId,
                            messageId = message.id,
                            timestamp = message.timestamp,
                            isImage = message.isImage
                        )
                        attachment?.let { attachments.add(it) }
                    }
                }
                
                // Method 3: Legacy fallback - Look for content:// or file:// URIs in message text
                if (attachments.isEmpty()) {
                    val contentUris = extractContentUrisFromText(message.message)
                    for (uriString in contentUris) {
                        try {
                            val uri = Uri.parse(uriString)
                            val attachment = createAttachmentFromUri(
                                uri = uri,
                                conversationId = message.conversationId,
                                messageId = message.id,
                                timestamp = message.timestamp
                            )
                            attachment?.let { attachments.add(it) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse URI: $uriString", e)
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting attachments from message ${message.id}", e)
            }
            
            attachments
        }
    }
    
    /**
     * Create a ConversationAttachment from a URI
     */
    private fun createAttachmentFromUri(
        uri: Uri,
        conversationId: String,
        messageId: String,
        timestamp: Long,
        isImage: Boolean? = null
    ): ConversationAttachment? {
        return try {
            val (name, size, fullPath) = getFileInfo(uri)
            val mimeType = getMimeType(uri, name)
            val attachmentType = if (isImage == true || mimeType.startsWith("image/")) {
                AttachmentType.IMAGE
            } else {
                AttachmentType.DOCUMENT
            }
            
            ConversationAttachment(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                type = attachmentType,
                mimeType = mimeType,
                size = size,
                timestamp = timestamp,
                conversationId = conversationId,
                messageId = messageId,
                fullPath = fullPath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating attachment from URI: $uri", e)
            null
        }
    }
    
    /**
     * Get file name, size, and full path from URI
     */
    private fun getFileInfo(uri: Uri): Triple<String, Long, String?> {
        var name = "Unknown File"
        var size = 0L
        
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                    
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting file info for URI: $uri", e)
            // Fallback: try to get name from URI path
            uri.lastPathSegment?.let { name = it }
        }
        
        // Try to get the full path
        val fullPath = getRealPathFromURI(uri)
        
        return Triple(name, size, fullPath)
    }
    
    /**
     * Get MIME type from URI
     */
    private fun getMimeType(uri: Uri, fileName: String): String {
        return try {
            // Try to get MIME type from content resolver
            context.contentResolver.getType(uri)
                ?: // Fallback: get MIME type from file extension
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    fileName.substringAfterLast('.', "").lowercase()
                )
                ?: "application/octet-stream"
        } catch (e: Exception) {
            Log.w(TAG, "Error getting MIME type for URI: $uri", e)
            "application/octet-stream"
        }
    }
    
    /**
     * Extract URIs from text (basic implementation)
     */
    private fun extractUrisFromText(text: String): List<Uri> {
        val uris = mutableListOf<Uri>()
        // This is a basic implementation - you might need to enhance this
        // based on how your app stores URI references in messages
        return uris
    }
    
    /**
     * Get real file path from content URI
     */
    fun getRealPathFromURI(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    uri.path
                }
                "content" -> {
                    when {
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> {
                            // Android 10+ - try to get path from DocumentsContract
                            if (DocumentsContract.isDocumentUri(context, uri)) {
                                when {
                                    uri.authority == "com.android.externalstorage.documents" -> {
                                        val docId = DocumentsContract.getDocumentId(uri)
                                        val split = docId.split(":")
                                        if (split.size >= 2) {
                                            val type = split[0]
                                            val id = split[1]
                                            when (type) {
                                                "primary" -> "/storage/emulated/0/$id"
                                                else -> "/storage/$type/$id"
                                            }
                                        } else null
                                    }
                                    uri.authority == "com.android.providers.downloads.documents" -> {
                                        val docId = DocumentsContract.getDocumentId(uri)
                                        if (docId.startsWith("raw:")) {
                                            docId.substring(4)
                                        } else {
                                            "/storage/emulated/0/Download/${getFileName(uri)}"
                                        }
                                    }
                                    uri.authority == "com.android.providers.media.documents" -> {
                                        val docId = DocumentsContract.getDocumentId(uri)
                                        val split = docId.split(":")
                                        if (split.size >= 2) {
                                            val type = split[0]
                                            when (type) {
                                                "image" -> "/storage/emulated/0/DCIM/Camera/${getFileName(uri)}"
                                                "video" -> "/storage/emulated/0/DCIM/Camera/${getFileName(uri)}"
                                                "audio" -> "/storage/emulated/0/Music/${getFileName(uri)}"
                                                else -> null
                                            }
                                        } else null
                                    }
                                    else -> {
                                        // Try to extract path from other authorities
                                        uri.path
                                    }
                                }
                            } else {
                                // Non-document URI - try to resolve through content resolver
                                getPathFromContentResolver(uri)
                            }
                        }
                        else -> {
                            // Pre-Android 10 - try legacy methods
                            getPathFromContentResolver(uri)
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get real path from URI: $uri", e)
            null
        }
    }
    
    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String {
        var name = "unknown_file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get file name for URI: $uri", e)
        }
        return name
    }
    
    /**
     * Get path from content resolver using MediaStore
     */
    private fun getPathFromContentResolver(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (columnIndex != -1) {
                        cursor.getString(columnIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get path from content resolver: $uri", e)
            null
        }
    }

    /**
     * Extract content:// URIs from text
     */
    private fun extractContentUrisFromText(text: String): List<String> {
        val uriRegex = Regex("(content://[^\\s]+)")
        return uriRegex.findAll(text).map { it.value }.toList()
    }
    
    /**
     * Count attachments in a conversation
     */
    suspend fun getAttachmentCount(conversationId: String): Int {
        return getConversationAttachments(conversationId).size
    }
}