package com.cyberflux.qwinai.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.tools.FileGenerationResult
import java.io.File
import java.text.DecimalFormat

class FileDownloadCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var fileIcon: ImageView
    private lateinit var fileName: TextView
    private lateinit var fileType: TextView
    private lateinit var fileSize: TextView
    private lateinit var downloadButton: CardView
    private lateinit var downloadProgress: ProgressBar
    private var fileGenerationResult: FileGenerationResult? = null
    private var onDownloadListener: ((FileGenerationResult) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.item_file_download_card, this, true)
        initViews()
    }

    private fun initViews() {
        fileIcon = findViewById(R.id.fileIcon)
        fileName = findViewById(R.id.fileName)
        fileType = findViewById(R.id.fileType)
        fileSize = findViewById(R.id.fileSize)
        downloadButton = findViewById(R.id.downloadButton)
        downloadProgress = findViewById(R.id.downloadProgress)

        downloadButton.setOnClickListener {
            handleDownload()
        }

        // Make the entire card clickable for download
        setOnClickListener {
            handleDownload()
        }
    }

    fun setFileData(result: FileGenerationResult) {
        this.fileGenerationResult = result
        
        // Set file name
        fileName.text = result.fileName ?: "Unknown File"
        
        // Set file type and icon
        val extension = getFileExtension(result.fileName ?: "")
        fileType.text = extension.uppercase()
        setFileTypeIcon(extension)
        setFileTypeBadgeColor(extension)
        
        // Set file size
        fileSize.text = formatFileSize(result.fileSize)
    }

    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast(".", "")
    }

    private fun setFileTypeIcon(extension: String) {
        val iconRes = when (extension.lowercase()) {
            "pdf" -> R.drawable.ic_pdf
            "docx", "doc" -> R.drawable.ic_word
            "xlsx", "xls" -> R.drawable.ic_excel
            "pptx", "ppt" -> R.drawable.ic_powerpoint
            "csv" -> R.drawable.ic_document
            "txt" -> R.drawable.ic_text
            "json", "xml" -> R.drawable.ic_code
            else -> R.drawable.ic_document
        }
        fileIcon.setImageResource(iconRes)
    }

    private fun setFileTypeBadgeColor(extension: String) {
        val colorRes = when (extension.lowercase()) {
            "pdf" -> R.color.error_color
            "docx", "doc" -> R.color.blue
            "xlsx", "xls" -> R.color.status_active
            "pptx", "ppt" -> R.color.accent_orange
            "csv" -> R.color.colorSecondary
            "txt" -> R.color.text_secondary
            "json", "xml" -> R.color.code_function
            else -> R.color.colorPrimary
        }
        
        val color = ContextCompat.getColor(context, colorRes)
        fileType.setBackgroundColor(color)
        
        // Update the icon background color to match
        val iconCard = findViewById<CardView>(R.id.fileIcon).parent as CardView
        iconCard.setCardBackgroundColor(color)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun handleDownload() {
        val result = fileGenerationResult ?: return
        
        // Show download animation
        showDownloadAnimation()
        
        // Trigger download
        onDownloadListener?.invoke(result) ?: defaultDownloadBehavior(result)
    }

    private fun defaultDownloadBehavior(result: FileGenerationResult) {
        try {
            // Try to open the file directly
            result.uri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, result.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    context.startActivity(intent)
                    showSuccessAnimation()
                } catch (e: Exception) {
                    // Fallback to share intent
                    shareFile(result)
                }
            } ?: run {
                Toast.makeText(context, "File not available for download", Toast.LENGTH_SHORT).show()
                resetDownloadButton()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            resetDownloadButton()
        }
    }

    private fun shareFile(result: FileGenerationResult) {
        result.uri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = result.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, result.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(Intent.createChooser(intent, "Share ${result.fileName}"))
            showSuccessAnimation()
        }
    }

    private fun showDownloadAnimation() {
        // Show progress bar
        downloadProgress.visibility = VISIBLE
        downloadProgress.isIndeterminate = true
        
        // Animate download button
        val scaleDown = ObjectAnimator.ofFloat(downloadButton, "scaleX", 1f, 0.9f)
        scaleDown.duration = 100
        scaleDown.start()
        
        ObjectAnimator.ofFloat(downloadButton, "scaleY", 1f, 0.9f).apply {
            duration = 100
            start()
        }
    }

    private fun showSuccessAnimation() {
        // Hide progress bar
        downloadProgress.visibility = GONE
        
        // Change download button to checkmark briefly
        val downloadIcon = downloadButton.findViewById<ImageView>(R.id.downloadIcon)
        downloadIcon?.setImageResource(R.drawable.ic_check)
        downloadButton.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_active))
        
        // Reset after delay
        postDelayed({
            resetDownloadButton()
        }, 1500)
        
        // Scale back up
        ObjectAnimator.ofFloat(downloadButton, "scaleX", 0.9f, 1f).apply {
            duration = 150
            start()
        }
        ObjectAnimator.ofFloat(downloadButton, "scaleY", 0.9f, 1f).apply {
            duration = 150
            start()
        }
        
        Toast.makeText(context, "File opened successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun resetDownloadButton() {
        downloadProgress.visibility = GONE
        
        val downloadIcon = downloadButton.findViewById<ImageView>(R.id.downloadIcon)
        downloadIcon?.setImageResource(R.drawable.ic_download)
        downloadButton.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_active))
        
        ObjectAnimator.ofFloat(downloadButton, "scaleX", 1f).apply {
            duration = 150
            start()
        }
        ObjectAnimator.ofFloat(downloadButton, "scaleY", 1f).apply {
            duration = 150
            start()
        }
    }

    fun setOnDownloadListener(listener: (FileGenerationResult) -> Unit) {
        this.onDownloadListener = listener
    }

    // Static method to create and configure the card
    companion object {
        fun create(context: Context, result: FileGenerationResult): FileDownloadCard {
            return FileDownloadCard(context).apply {
                setFileData(result)
            }
        }

        fun createWithClickListener(
            context: Context, 
            result: FileGenerationResult,
            onDownload: (FileGenerationResult) -> Unit
        ): FileDownloadCard {
            return FileDownloadCard(context).apply {
                setFileData(result)
                setOnDownloadListener(onDownload)
            }
        }
    }
}