package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cyberflux.qwinai.model.ModelConfig

object FileValidationUtils {
    
    data class FileValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val fileName: String? = null,
        val fileSize: Long = 0L,
        val mimeType: String? = null
    )
    
    /**
     * Validate a file against model capabilities
     */
    fun validateFile(context: Context, uri: Uri, modelConfig: ModelConfig): FileValidationResult {
        try {
            // Get file information
            val fileName = getFileName(context, uri)
            val fileSize = getFileSize(context, uri)
            val mimeType = context.contentResolver.getType(uri)
            
            // Check if model supports file uploads
            if (!modelConfig.supportsFileUpload) {
                return FileValidationResult(
                    isValid = false,
                    errorMessage = "This model doesn't support file uploads",
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType
                )
            }
            
            // Check file size
            if (fileSize > modelConfig.maxFileSizeBytes) {
                val limitMB = FileFilterUtils.getFileSizeLimitMB(modelConfig)
                val actualMB = (fileSize / (1024 * 1024)).toInt()
                return FileValidationResult(
                    isValid = false,
                    errorMessage = "File too large: ${actualMB}MB (limit: ${limitMB}MB)",
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType
                )
            }
            
            // Check file extension
            val extension = fileName?.substringAfterLast('.', "") ?: ""
            if (extension.isNotEmpty() && !FileFilterUtils.isFileExtensionSupported(modelConfig, extension)) {
                val supportedTypes = modelConfig.supportedFileTypes.joinToString(", ").uppercase()
                return FileValidationResult(
                    isValid = false,
                    errorMessage = "Unsupported file type: ${extension.uppercase()}. Supported: $supportedTypes",
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType
                )
            }
            
            // Check MIME type if available
            if (mimeType != null) {
                val supportedMimeTypes = FileFilterUtils.getSupportedMimeTypes(modelConfig)
                if (supportedMimeTypes.isNotEmpty() && !supportedMimeTypes.contains(mimeType)) {
                    return FileValidationResult(
                        isValid = false,
                        errorMessage = "Unsupported file format: ${mimeType}",
                        fileName = fileName,
                        fileSize = fileSize,
                        mimeType = mimeType
                    )
                }
            }
            
            return FileValidationResult(
                isValid = true,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType
            )
            
        } catch (e: Exception) {
            return FileValidationResult(
                isValid = false,
                errorMessage = "Error validating file: ${e.message}"
            )
        }
    }
    
    /**
     * Validate multiple files against model capabilities
     */
    fun validateFiles(context: Context, uris: List<Uri>, modelConfig: ModelConfig): List<FileValidationResult> {
        // Check file count limit
        val maxFiles = FileFilterUtils.getMaxFileCount(modelConfig)
        if (uris.size > maxFiles) {
            return uris.map { uri ->
                val fileName = getFileName(context, uri)
                FileValidationResult(
                    isValid = false,
                    errorMessage = "Too many files: ${uris.size} (limit: $maxFiles)",
                    fileName = fileName
                )
            }
        }
        
        return uris.map { uri -> validateFile(context, uri, modelConfig) }
    }
    
    /**
     * Get file name from URI
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        uri.lastPathSegment
                    }
                } else {
                    uri.lastPathSegment
                }
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
    
    /**
     * Get file size from URI
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        cursor.getLong(sizeIndex)
                    } else {
                        0L
                    }
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Check if current file selection is within limits
     */
    fun canAddMoreFiles(currentFileCount: Int, modelConfig: ModelConfig): Boolean {
        return currentFileCount < FileFilterUtils.getMaxFileCount(modelConfig)
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> "%.1f GB".format(gb)
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes bytes"
        }
    }
}