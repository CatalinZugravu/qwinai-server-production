package com.cyberflux.qwinai.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.Conversation
import com.cyberflux.qwinai.utils.ConversationAttachmentsManager
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.JsonUtils
import com.cyberflux.qwinai.views.ConversationAttachmentsView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit,
    private val onConversationLongClick: (View, Conversation) -> Unit,
    private val attachmentsManager: ConversationAttachmentsManager? = null
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conversationText: TextView = itemView.findViewById(R.id.conversationText)
        val aiModelInfo: TextView = itemView.findViewById(R.id.aiModelInfo)
        val conversationDateTime: TextView = itemView.findViewById(R.id.conversationDateTime)
        val conversationStatus: TextView = itemView.findViewById(R.id.conversationStatus)
        val btnDropdown: ImageButton = itemView.findViewById(R.id.btnDropdown)
        val savedIndicator: ImageView = itemView.findViewById(R.id.savedIndicator)
        val draftIndicator: TextView = itemView.findViewById(R.id.draftIndicator)
        val attachmentsView: ConversationAttachmentsView = itemView.findViewById(R.id.attachmentsView)
        
        private var attachmentJob: Job? = null
        
        fun cancelAttachmentJob() {
            attachmentJob?.cancel()
        }
        
        fun loadAttachments(conversationId: String, attachmentsManager: ConversationAttachmentsManager, scope: CoroutineScope) {
            cancelAttachmentJob()
            attachmentJob = scope.launch {
                try {
                    val attachments = withContext(Dispatchers.IO) {
                        attachmentsManager.getConversationAttachments(conversationId)
                    }
                    withContext(Dispatchers.Main) {
                        attachmentsView.setAttachments(attachments)
                    }
                } catch (e: Exception) {
                    // Handle error silently or log it
                    withContext(Dispatchers.Main) {
                        attachmentsView.setAttachments(emptyList())
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.conversation_item, parent, false)
        return ConversationViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = getItem(position)

        // Basic conversation data - this is the conversation title
        holder.conversationText.text = conversation.title
        holder.aiModelInfo.text = conversation.aiModel

        // Format date for display
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateString = dateFormat.format(Date(conversation.timestamp))
        val timeString = timeFormat.format(Date(conversation.timestamp))

        // Always show date and time
        holder.conversationDateTime.text = "$dateString $timeString"

        // Handle draft status and message status
        if (conversation.hasDraft) {
            // Show draft indicator
            holder.draftIndicator.visibility = View.VISIBLE

            // Build draft status text for the status field
            val draftStatus = buildString {
                append("Draft")
                
                // Add attachment count if any
                if (conversation.draftFiles.isNotEmpty()) {
                    try {
                        val files = JsonUtils.fromJsonList(conversation.draftFiles, FileUtil.SelectedFile::class.java)
                        val fileCount = files?.size ?: 0
                        if (fileCount > 0) {
                            append(" with $fileCount ")
                            append(if (fileCount == 1) "attachment" else "attachments")
                        }
                    } catch (e: Exception) {
                        append(" with attachments")
                    }
                }
                
                // Add draft text preview if there's text and no files, or show both
                if (conversation.draftText.isNotEmpty()) {
                    if (conversation.draftFiles.isNotEmpty()) {
                        // Show draft text after attachment info
                        append(": ${conversation.draftText.take(50)}")
                        if (conversation.draftText.length > 50) append("...")
                    } else {
                        // Just draft text, show more of it
                        append(": ${conversation.draftText.take(80)}")
                        if (conversation.draftText.length > 80) append("...")
                    }
                }
            }

            holder.conversationStatus.text = draftStatus
        } else {
            // Hide draft indicator for normal conversations
            holder.draftIndicator.visibility = View.GONE

            // Show last user message if available
            if (conversation.lastMessage.isNotEmpty()) {
                holder.conversationStatus.text = conversation.lastMessage
            } else {
                holder.conversationStatus.text = "No messages yet"
            }
        }

        // Update saved indicator visibility
        holder.savedIndicator.visibility = if (conversation.saved) View.VISIBLE else View.GONE

        // Click listeners
        holder.itemView.setOnClickListener {
            onConversationClick(conversation)
        }

        holder.itemView.setOnLongClickListener {
            onConversationLongClick(holder.itemView, conversation)
            true
        }

        holder.btnDropdown.setOnClickListener { view ->
            onConversationLongClick(view, conversation)
        }
        
        // Load attachments if manager is available
        attachmentsManager?.let { manager ->
            holder.loadAttachments(conversation.id.toString(), manager, scope)
        } ?: run {
            // Hide attachments view if no manager
            holder.attachmentsView.setAttachments(emptyList())
        }
    }
    
    override fun onViewRecycled(holder: ConversationViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelAttachmentJob()
    }
    
    fun cleanup() {
        scope.cancel()
    }

    object ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.title == newItem.title &&
                    oldItem.lastModified == newItem.lastModified &&
                    oldItem.saved == newItem.saved
        }
    }
}