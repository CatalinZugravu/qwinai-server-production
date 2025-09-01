package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log

/**
 * Utility class for resolving content URIs to file system paths
 */
object UriPathResolver {
    
    private const val TAG = "UriPathResolver"
    
    /**
     * Get real file path from content URI
     */
    fun getRealPathFromURI(context: Context, uri: Uri): String? {
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
                                            "/storage/emulated/0/Download/${getFileName(context, uri)}"
                                        }
                                    }
                                    uri.authority == "com.android.providers.media.documents" -> {
                                        val docId = DocumentsContract.getDocumentId(uri)
                                        val split = docId.split(":")
                                        if (split.size >= 2) {
                                            val type = split[0]
                                            when (type) {
                                                "image" -> "/storage/emulated/0/DCIM/Camera/${getFileName(context, uri)}"
                                                "video" -> "/storage/emulated/0/DCIM/Camera/${getFileName(context, uri)}"
                                                "audio" -> "/storage/emulated/0/Music/${getFileName(context, uri)}"
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
                                getPathFromContentResolver(context, uri)
                            }
                        }
                        else -> {
                            // Pre-Android 10 - try legacy methods
                            getPathFromContentResolver(context, uri)
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
    private fun getFileName(context: Context, uri: Uri): String {
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
    private fun getPathFromContentResolver(context: Context, uri: Uri): String? {
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
}