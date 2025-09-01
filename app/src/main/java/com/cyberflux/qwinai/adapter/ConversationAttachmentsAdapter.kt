package com.cyberflux.qwinai.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.AttachmentType
import com.cyberflux.qwinai.model.ConversationAttachment

/**
 * Adapter for displaying conversation attachments in horizontal scrollable view
 */
class ConversationAttachmentsAdapter(
    private val context: Context,
    private val onAttachmentClick: (ConversationAttachment) -> Unit
) : ListAdapter<ConversationAttachment, ConversationAttachmentsAdapter.AttachmentViewHolder>(AttachmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attachment_thumbnail, parent, false)
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        private val layoutFile: LinearLayout = itemView.findViewById(R.id.layoutFile)
        private val ivFileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)

        fun bind(attachment: ConversationAttachment) {
            // Set different widths for images vs files (same height)
            val layoutParams = itemView.layoutParams
            layoutParams.height = context.resources.getDimensionPixelSize(R.dimen.attachment_item_height)
            
            when (attachment.type) {
                AttachmentType.IMAGE -> {
                    // Show image - square format
                    ivImage.visibility = View.VISIBLE
                    layoutFile.visibility = View.GONE
                    layoutParams.width = context.resources.getDimensionPixelSize(R.dimen.attachment_item_height) // Square
                    
                    Glide.with(context)
                        .load(attachment.uri)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(ivImage)
                }
                
                AttachmentType.DOCUMENT -> {
                    // Show file details - wider format to show more text
                    ivImage.visibility = View.GONE
                    layoutFile.visibility = View.VISIBLE
                    layoutParams.width = context.resources.getDimensionPixelSize(R.dimen.attachment_file_width)
                    
                    // Set file icon based on type
                    ivFileIcon.setImageResource(getFileIcon(attachment.mimeType, attachment.getFileExtension()))
                    
                    // Set file name/path (show full path if available, otherwise filename)
                    tvFileName.text = attachment.getTruncatedDisplayPath(25) // Show more characters for full paths
                    
                    // Set file size
                    tvFileSize.text = attachment.getFormattedSize()
                }
            }
            
            itemView.layoutParams = layoutParams
            
            // Set click listener
            itemView.setOnClickListener {
                onAttachmentClick(attachment)
            }
        }
        
        private fun getFileIcon(mimeType: String, extension: String): Int {
            return when {
                mimeType.startsWith("application/pdf") || extension == "PDF" -> R.drawable.ic_file_pdf
                mimeType.startsWith("application/msword") || 
                mimeType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") ||
                extension in listOf("DOC", "DOCX") -> R.drawable.ic_file_word
                mimeType.startsWith("application/vnd.ms-excel") ||
                mimeType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") ||
                extension in listOf("XLS", "XLSX") -> R.drawable.ic_file_excel
                mimeType.startsWith("application/vnd.ms-powerpoint") ||
                mimeType.startsWith("application/vnd.openxmlformats-officedocument.presentationml") ||
                extension in listOf("PPT", "PPTX") -> R.drawable.ic_file_powerpoint
                mimeType.startsWith("text/") || extension in listOf("TXT", "MD") -> R.drawable.ic_file_text
                mimeType.startsWith("application/zip") || 
                extension in listOf("ZIP", "RAR", "7Z") -> R.drawable.ic_file_archive
                else -> R.drawable.ic_file_generic
            }
        }
    }

    class AttachmentDiffCallback : DiffUtil.ItemCallback<ConversationAttachment>() {
        override fun areItemsTheSame(oldItem: ConversationAttachment, newItem: ConversationAttachment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ConversationAttachment, newItem: ConversationAttachment): Boolean {
            return oldItem == newItem
        }
    }
}