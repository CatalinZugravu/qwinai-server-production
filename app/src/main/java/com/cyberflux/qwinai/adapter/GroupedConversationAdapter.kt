package com.cyberflux.qwinai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.Conversation
import com.cyberflux.qwinai.model.ConversationGroup
import com.cyberflux.qwinai.utils.ConversationAttachmentsManager
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.JsonUtils
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupedConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit,
    private val onConversationLongClick: (View, Conversation) -> Unit,
    private val attachmentsManager: ConversationAttachmentsManager? = null
) : ListAdapter<GroupedConversationAdapter.Item, RecyclerView.ViewHolder>(ItemDiffCallback()) {

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    // Define view types
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONVERSATION = 1
    }

    // Sealed class hierarchy for our items
    sealed class Item {
        data class Header(val title: String) : Item()
        data class ConversationItem(val conversation: Conversation) : Item()
    }

    private var items: List<Item> = emptyList()

    fun submitGroups(groups: List<ConversationGroup>) {
        Timber.d("Submitting ${groups.size} groups to adapter")
        val flattenedItems = mutableListOf<Item>()
        groups.forEach { group ->
            flattenedItems.add(Item.Header(group.title))
            group.conversations.forEach { conversation ->
                flattenedItems.add(Item.ConversationItem(conversation))
            }
        }

        items = flattenedItems
        submitList(flattenedItems)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Item.Header -> VIEW_TYPE_HEADER
            is Item.ConversationItem -> VIEW_TYPE_CONVERSATION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conversation_date_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_CONVERSATION -> {
                // Use your existing item_conversation layout
                try {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.conversation_item, parent, false)
                    ConversationViewHolder(view)
                } catch (e: Exception) {
                    Timber.e(e, "Error creating ConversationViewHolder")
                    // Fallback to a simple layout if there's an error
                    val view = TextView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(16, 16, 16, 16)
                    }
                    EmptyViewHolder(view)
                }
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            when (val item = getItem(position)) {
                is Item.Header -> (holder as? HeaderViewHolder)?.bind(item)
                is Item.ConversationItem -> {
                    when (holder) {
                        is ConversationViewHolder -> holder.bind(
                            item.conversation,
                            onConversationClick,
                            onConversationLongClick,
                            attachmentsManager,
                            scope
                        )
                        is EmptyViewHolder -> holder.bind(item.conversation.name)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error binding view holder at position $position")
        }
    }
    
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ConversationViewHolder) {
            holder.cancelAttachmentJob()
        }
    }
    
    fun cleanup() {
        scope.cancel()
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView? = itemView.findViewById(R.id.tvDateHeader)

        fun bind(header: Item.Header) {
            titleTextView?.text = header.title
        }
    }

    // Add this inside the ConversationViewHolder class in GroupedConversationAdapter
    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Make ALL view references nullable and use safe findViewById
        private val titleTextView: TextView? = itemView.findViewById(R.id.conversationText)
        private val modelTextView: TextView? = itemView.findViewById(R.id.aiModelInfo)
        private val dateTimeTextView: TextView? = itemView.findViewById(R.id.conversationDateTime)
        private val statusTextView: TextView? = itemView.findViewById(R.id.conversationStatus)
        private val savedIndicator: View? = itemView.findViewById(R.id.savedIndicator)
        private val draftIndicator: TextView? = itemView.findViewById(R.id.draftIndicator)
        private val attachmentsView: com.cyberflux.qwinai.views.ConversationAttachmentsView? = itemView.findViewById(R.id.attachmentsView)
        
        private var attachmentJob: kotlinx.coroutines.Job? = null
        
        fun cancelAttachmentJob() {
            attachmentJob?.cancel()
        }
        
        fun loadAttachments(conversationId: String, attachmentsManager: ConversationAttachmentsManager, scope: kotlinx.coroutines.CoroutineScope) {
            cancelAttachmentJob()
            attachmentJob = scope.launch {
                try {
                    val attachments = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        attachmentsManager.getConversationAttachments(conversationId)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        attachmentsView?.setAttachments(attachments)
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        attachmentsView?.setAttachments(emptyList())
                    }
                }
            }
        }

        fun bind(
            conversation: Conversation,
            onConversationClick: (Conversation) -> Unit,
            onConversationLongClick: (View, Conversation) -> Unit,
            attachmentsManager: ConversationAttachmentsManager?,
            scope: kotlinx.coroutines.CoroutineScope
        ) {
            // Set conversation title - always use the actual title, never modify it
            titleTextView?.text = conversation.title

            // Set model info
            modelTextView?.text = conversation.aiModel

            // Format date time - this should ONLY show date and time, no draft info
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateString = dateFormat.format(Date(conversation.timestamp))
            val timeString = timeFormat.format(Date(conversation.timestamp))
            dateTimeTextView?.text = "$dateString $timeString"

            // Handle draft status and message status in the separate status field
            if (conversation.hasDraft) {
                // Show draft indicator
                draftIndicator?.visibility = View.VISIBLE

                // Build draft status text for the status field
                val draftStatus = buildString {
                    append("Draft")
                    
                    // Add attachment count if any
                    if (conversation.draftFiles.isNotEmpty()) {
                        try {
                            val files = JsonUtils.fromJsonList(conversation.draftFiles, FileUtil.FileUtil.SelectedFile::class.java)
                            val fileCount = files?.size ?: 0
                            if (fileCount > 0) {
                                append(" with $fileCount ")
                                append(if (fileCount == 1) "attachment" else "attachments")
                            }
                        } catch (e: Exception) {
                            append(" with attachments")
                        }
                    }
                    
                    // Add draft text preview if there's text
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

                statusTextView?.text = draftStatus
            } else {
                // Hide draft indicator for normal conversations
                draftIndicator?.visibility = View.GONE

                // Show last user message if available
                if (conversation.lastMessage.isNotEmpty()) {
                    statusTextView?.text = conversation.lastMessage
                } else {
                    statusTextView?.text = "No messages yet"
                }
            }

            // Set saved indicator visibility
            savedIndicator?.visibility = if (conversation.saved) View.VISIBLE else View.GONE

            // Set click listeners
            itemView.setOnClickListener { onConversationClick(conversation) }

            // Use the dropdown button for long press actions if it exists
            itemView.findViewById<View>(R.id.btnDropdown)?.setOnClickListener {
                onConversationLongClick(it, conversation)
            }

            // Also set long click listener on the whole item as fallback
            itemView.setOnLongClickListener {
                onConversationLongClick(it, conversation)
                true
            }
            
            // Load attachments if manager is available
            attachmentsManager?.let { manager ->
                loadAttachments(conversation.id.toString(), manager, scope)
            } ?: run {
                // Hide attachments view if no manager
                attachmentsView?.setAttachments(emptyList())
            }
        }
    }
    // Fallback ViewHolder in case of errors
    class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(title: String) {
            (itemView as? TextView)?.text = title
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when {
                oldItem is Item.Header && newItem is Item.Header -> oldItem.title == newItem.title
                oldItem is Item.ConversationItem && newItem is Item.ConversationItem ->
                    oldItem.conversation.id == newItem.conversation.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when {
                oldItem is Item.Header && newItem is Item.Header -> oldItem.title == newItem.title
                oldItem is Item.ConversationItem && newItem is Item.ConversationItem ->
                    oldItem.conversation == newItem.conversation
                else -> false
            }
        }
    }
}