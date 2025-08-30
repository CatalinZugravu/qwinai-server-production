package com.cyberflux.qwinai.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.ImageDetailActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.adapter.ConversationAttachmentsAdapter
import com.cyberflux.qwinai.model.AttachmentType
import com.cyberflux.qwinai.model.ConversationAttachment
import kotlinx.coroutines.*

/**
 * Custom view for displaying conversation attachments
 */
class ConversationAttachmentsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tvAttachmentsCount: TextView
    private val rvAttachments: RecyclerView
    private val attachmentsAdapter: ConversationAttachmentsAdapter
    
    private var attachments: List<ConversationAttachment> = emptyList()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_conversation_attachments, this, true)
        
        tvAttachmentsCount = findViewById(R.id.tvAttachmentsCount)
        rvAttachments = findViewById(R.id.rvAttachments)
        
        // Setup RecyclerView
        attachmentsAdapter = ConversationAttachmentsAdapter(context) { attachment ->
            handleAttachmentClick(attachment)
        }
        
        rvAttachments.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = attachmentsAdapter
        }
    }

    /**
     * Set attachments and update the view
     */
    fun setAttachments(attachments: List<ConversationAttachment>) {
        this.attachments = attachments
        
        if (attachments.isEmpty()) {
            visibility = GONE
        } else {
            visibility = VISIBLE
            
            // Sort attachments: images first, then files
            val sortedAttachments = attachments.sortedBy { 
                when (it.type) {
                    AttachmentType.IMAGE -> 0
                    AttachmentType.DOCUMENT -> 1
                }
            }
            
            // Count images and files
            val imageCount = attachments.count { it.type == AttachmentType.IMAGE }
            val fileCount = attachments.count { it.type == AttachmentType.DOCUMENT }
            
            // Update count text with specific counts
            val countText = buildString {
                if (imageCount > 0) {
                    append("$imageCount ")
                    append(if (imageCount == 1) "image" else "images")
                }
                if (fileCount > 0) {
                    if (imageCount > 0) append(" and ")
                    append("$fileCount ")
                    append(if (fileCount == 1) "file" else "files")
                }
            }
            tvAttachmentsCount.text = countText
            
            // Update adapter with sorted attachments
            attachmentsAdapter.submitList(sortedAttachments)
        }
    }

    /**
     * Handle attachment click
     */
    private fun handleAttachmentClick(attachment: ConversationAttachment) {
        when (attachment.type) {
            AttachmentType.IMAGE -> {
                // Open image in ImageDetailActivity
                val intent = Intent(context, ImageDetailActivity::class.java).apply {
                    putExtra("image_uri", attachment.uri.toString())
                    putExtra("image_name", attachment.name)
                }
                context.startActivity(intent)
            }
            
            AttachmentType.DOCUMENT -> {
                // Open document with appropriate app
                openDocument(attachment.uri, attachment.mimeType)
            }
        }
    }

    /**
     * Open document with system app
     */
    private fun openDocument(uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback: try with generic intent
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Open file"))
            }
        } catch (e: Exception) {
            // Handle error - could show a toast or log
            android.util.Log.e("ConversationAttachmentsView", "Error opening document", e)
        }
    }

    /**
     * Get current attachments count
     */
    fun getAttachmentsCount(): Int = attachments.size

    /**
     * Check if there are any attachments
     */
    fun hasAttachments(): Boolean = attachments.isNotEmpty()
}