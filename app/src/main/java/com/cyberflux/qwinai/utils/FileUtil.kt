package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import androidx.core.net.toFile
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Utility class for file operations
 */
object FileUtil {
    private const val TAG = "FileUtil"

    /**
     * Get the file name from a content URI
     */
    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null

        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                }
            }

            if (result == null) {
                // If the query didn't work, try getting the last path segment
                result = uri.lastPathSegment

                // For file:// URIs, get the actual file name
                if (uri.scheme == "file") {
                    try {
                        result = uri.toFile().name
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error getting file name from file URI")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting file name")
        }

        return result ?: "unknown_file"
    }
    fun getFileType(fileName: String): String {
        return when (val extension = fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "PDF Document"
            "doc", "docx" -> "Word Document"
            "xls", "xlsx" -> "Excel Spreadsheet"
            "ppt", "pptx" -> "PowerPoint Presentation"
            "txt" -> "Text File"
            else -> extension.uppercase() + " File"
        }
    }

    /**
     * Read text content from a URI
     */
    fun readTextFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (e: Exception) {
            Timber.e(e, "Error reading text from URI: ${e.message}")
            ""
        }
    }
    
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) +
                " " + units[digitGroups]
    }

    fun findFileViewForUri(uri: Uri, activity: Activity): View? {
        val container = activity.findViewById<LinearLayout>(R.id.selectedFilesContainer)
        if (container != null) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.tag == uri) {
                    return child
                }
            }
        }
        return null
    }

    /**
     * Get the file size from a content URI
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0

        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }

            if (size == 0L) {
                // If the query didn't work, try getting the file size directly
                if (uri.scheme == "file") {
                    try {
                        size = uri.toFile().length()
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error getting file size from file URI")
                    }
                } else {
                    // For content URIs, try opening an input stream and getting available bytes
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            size = inputStream.available().toLong()
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error getting file size from content URI")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting file size")
        }

        return size
    }
    suspend fun createPersistentFileCopy(context: Context, uri: Uri): Result<File> = withContext(
        Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri)
            val cacheFile = File(context.cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IOException("Failed to open input stream"))

            Timber.d("Created persistent file copy at ${cacheFile.absolutePath}")
            return@withContext Result.success(cacheFile)
        } catch (e: Exception) {
            Timber.e(e, "Error creating persistent file copy: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get the MIME type of a file
     */
    fun getMimeType(context: Context, uri: Uri): String {
        var mimeType: String? = null
        val fileName = getFileName(context, uri)

        Timber.d("🔍 FileUtil: getMimeType() for file: $fileName")
        Timber.d("  URI: $uri")

        try {
            if (uri.scheme == "content") {
                mimeType = context.contentResolver.getType(uri)
                Timber.d("  ContentResolver MIME: $mimeType")
            }

            if (mimeType == null) {
                // If content resolver couldn't determine type, try from file extension
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                Timber.d("  URL Extension: $extension")
                
                if (extension != null) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        extension.lowercase(
                            Locale.ROOT
                        )
                    )
                    Timber.d("  MimeTypeMap result: $mimeType")
                }

                // Special case for common file types that might not be properly detected
                if (mimeType == null) {
                    val fileNameLower = fileName.lowercase(Locale.ROOT)
                    Timber.d("  Fallback filename check: $fileNameLower")
                    
                    when {
                        fileNameLower.endsWith(".pdf") -> mimeType = "application/pdf"
                        fileNameLower.endsWith(".doc") -> mimeType = "application/msword"
                        fileNameLower.endsWith(".docx") -> mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        fileNameLower.endsWith(".xls") -> mimeType = "application/vnd.ms-excel"
                        fileNameLower.endsWith(".xlsx") -> mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        fileNameLower.endsWith(".ppt") -> mimeType = "application/vnd.ms-powerpoint"
                        fileNameLower.endsWith(".pptx") -> mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        fileNameLower.endsWith(".txt") -> mimeType = "text/plain"
                        fileNameLower.endsWith(".csv") -> mimeType = "text/csv"
                        fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg") -> mimeType = "image/jpeg"
                        fileNameLower.endsWith(".png") -> mimeType = "image/png"
                        fileNameLower.endsWith(".gif") -> mimeType = "image/gif"
                        fileNameLower.endsWith(".webp") -> mimeType = "image/webp"
                    }
                    Timber.d("  Fallback MIME result: $mimeType")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting MIME type for $fileName")
        }

        // Default to octet-stream if we couldn't determine type
        val finalMimeType = mimeType ?: "application/octet-stream"
        Timber.d("🎯 FileUtil: Final MIME type for '$fileName': $finalMimeType")
        return finalMimeType
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return try {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting file name from URI")
            uri.lastPathSegment
        }
    }

    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) cursor.getLong(sizeIndex) else 0
            } ?: 0
        } catch (e: Exception) {
            Timber.e(e, "Error getting file size from URI")
            0
        }
    }

    fun getFileTypeFromName(fileName: String): String {
        val extension = try { fileName.substringAfterLast('.', "").lowercase() } catch (e: Exception) { "" }
        return when (extension) {
            "pdf" -> "PDF Document"
            "doc", "docx" -> "Word Document"
            "xls", "xlsx" -> "Excel Spreadsheet"
            "ppt", "pptx" -> "PowerPoint Presentation"
            "txt" -> "Text File"
            "jpg", "jpeg", "png", "gif" -> "Image File"
            "csv" -> "CSV File"
            "rtf" -> "Rich Text Format"
            "zip" -> "Zip Archive"
            "" -> "Unknown File Type"
            else -> "${extension.uppercase()} File"
        }
    }


    /**
     * Data class representing a selected file
     */
    data class SelectedFile(
        val uri: Uri,
        val name: String,
        val size: Long,
        val isDocument: Boolean = false,
        // Add new fields for persistent storage
        val isPersistent: Boolean = false,
        val persistentFileName: String = "",
        val isExtracting: Boolean = false,
        val isExtracted: Boolean = false,
        val extractedContentId: String = "",  // Reference to cached extracted content
        var hasError: Boolean = false,  // Error state for UI indication
        val processingInfo: String = ""  // Store processing status and token info
    ) {
        /**
         * Create a persistent copy of this file
         */
        fun toPersistent(persistentStorage: PersistentFileStorage): SelectedFile? {
            // Only create persistent copy if not already persistent
            if (isPersistent) return this

            // Copy to persistent storage
            val (persistentUri, fileName) = persistentStorage.copyToPrivateStorage(uri) ?: return null

            // Return new file with persistent flag and filename, preserving all metadata
            return copy(
                uri = persistentUri,
                isPersistent = true,
                persistentFileName = fileName,
                isExtracted = isExtracted,
                extractedContentId = extractedContentId
            )
        }

        /**
         * Restore a persistent file from storage
         */
        companion object {
            fun fromPersistentFileName(
                fileName: String,
                persistentStorage: PersistentFileStorage,
                size: Long = 0,
                isDocument: Boolean = false,
                isExtracted: Boolean = false,
                extractedContentId: String = ""
            ): SelectedFile? {
                val uri = persistentStorage.getUriForPrivateFile(fileName) ?: return null

                return SelectedFile(
                    uri = uri,
                    name = fileName,
                    size = size,
                    isDocument = isDocument,
                    isPersistent = true,
                    persistentFileName = fileName,
                    isExtracted = isExtracted,
                    extractedContentId = extractedContentId
                )
            }
        }
    }
}