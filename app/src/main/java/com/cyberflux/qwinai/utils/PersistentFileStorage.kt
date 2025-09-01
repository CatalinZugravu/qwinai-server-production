package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility class for managing persistent file storage
 * Handles copying external files to app-private storage to retain access
 */
class PersistentFileStorage(private val context: Context) {

    /**
     * Copies a file from a content URI to app-private storage
     * Returns a FileProvider URI that will remain accessible across app restarts
     */
    fun copyToPrivateStorage(sourceUri: Uri): Pair<Uri, String>? {
        try {
            // Get original file name or generate one
            val fileName = getFileName(sourceUri) ?: "file_${UUID.randomUUID()}"

            // Create directory for persistent files if it doesn't exist
            val storageDir = File(context.filesDir, "persistent_files")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            // Create a new file in our private directory
            val targetFile = File(storageDir, fileName)

            // Copy the content
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // Create a persistent URI using FileProvider
            val persistentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            Timber.d("File copied to private storage: $fileName")
            return Pair(persistentUri, fileName)
        } catch (e: Exception) {
            Timber.e(e, "Error copying file to private storage: ${e.message}")
            return null
        }
    }

    /**
     * Get original file name from URI
     */
    private fun getFileName(uri: Uri): String? {
        // Try to get the file name from content resolver
        var result: String? = null
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

        // If we couldn't get it from content resolver, try from the URI path
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) {
                    path.substring(cut + 1)
                } else {
                    path
                }
            }
        }

        // Make sure the file name is safe for filesystem
        result = result?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")

        return result
    }

    /**
     * Check if a file exists in private storage
     */
    fun fileExistsInPrivateStorage(fileName: String): Boolean {
        val storageDir = File(context.filesDir, "persistent_files")
        val file = File(storageDir, fileName)
        return file.exists()
    }

    /**
     * Get a URI for a file in private storage
     */
    fun getUriForPrivateFile(fileName: String): Uri? {
        val storageDir = File(context.filesDir, "persistent_files")
        val file = File(storageDir, fileName)

        return if (file.exists()) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } else {
            null
        }
    }
}