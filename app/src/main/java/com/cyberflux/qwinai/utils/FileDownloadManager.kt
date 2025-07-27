package com.cyberflux.qwinai.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.tools.FileGenerationResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileDownloadManager"
    }

    fun downloadFile(result: FileGenerationResult) {
        try {
            // Copy file to Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val sourceFile = File(result.filePath ?: return)
            val destFile = File(downloadsDir, result.fileName ?: "download")

            // Copy file
            copyFile(sourceFile, destFile)

            // Notify user
            Toast.makeText(
                context,
                "File downloaded to Downloads/${result.fileName}",
                Toast.LENGTH_LONG
            ).show()

            // Open file
            openDownloadedFile(destFile, result.mimeType)

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(result: FileGenerationResult) {
        try {
            result.uri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, result.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to share
                    shareFile(result)
                }
            } ?: run {
                Toast.makeText(context, "File not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file", e)
            Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(result: FileGenerationResult) {
        try {
            result.uri?.let { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = result.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, result.fileName)
                    putExtra(Intent.EXTRA_TEXT, "Generated with Qwin AI")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(intent, "Share ${result.fileName}")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file", e)
            Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(result: FileGenerationResult) {
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData.newUri(
                context.contentResolver,
                "Generated File",
                result.uri
            )
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(context, "File path copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
        }
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun openDownloadedFile(file: File, mimeType: String?) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening downloaded file", e)
            // Show file location instead
            Toast.makeText(
                context,
                "File saved to Downloads/${file.name}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun getFileActions(result: FileGenerationResult): List<FileAction> {
        return listOf(
            FileAction("Open", "ic_open_in_new") { openFile(result) },
            FileAction("Download", "ic_download") { downloadFile(result) },
            FileAction("Share", "ic_share") { shareFile(result) },
            FileAction("Copy Link", "ic_copy") { copyToClipboard(result) }
        )
    }

    data class FileAction(
        val title: String,
        val icon: String,
        val action: () -> Unit
    )
}