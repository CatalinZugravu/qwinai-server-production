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
import com.cyberflux.qwinai.utils.FileUtil
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupedConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit,
    private val onConversationLongClick: (View, Conversation) -> Unit
) : ListAdapter<GroupedConversationAdapter.Item, RecyclerView.ViewHolder>(ItemDiffCallback()) {

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
                            onConversationLongClick
                        )
                        is EmptyViewHolder -> holder.bind(item.conversation.name)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error binding view holder at position $position")
        }
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
        private val savedIndicator: View? = itemView.findViewById(R.id.savedIndicator)
        private val draftIndicator: TextView? = itemView.findViewById(R.id.draftIndicator) // Add this!

        fun bind(
            conversation: Conversation,
            onConversationClick: (Conversation) -> Unit,
            onConversationLongClick: (View, Conversation) -> Unit
        ) {
            // Set conversation title (this is the conversation name/title)
            titleTextView?.text = conversation.name

            // Set model info
            modelTextView?.text = conversation.aiModel

            // Format date time
            val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(conversation.lastModified))

            // IMPORTANT: Handle draft information
            if (conversation.hasDraft) {
                // Show draft indicator
                draftIndicator?.visibility = View.VISIBLE

                // Show draft preview in the date/time field
                val draftPreview = when {
                    conversation.draftText.isNotEmpty() -> {
                        val previewText = conversation.draftText.take(20)
                        "Draft: $previewText${if (conversation.draftText.length > 20) "..." else ""}"
                    }
                    conversation.draftFiles.isNotEmpty() -> {
                        try {
                            val type = object : TypeToken<List<FileUtil.FileUtil.SelectedFile>>() {}.type
                            val files = Gson().fromJson<List<FileUtil.FileUtil.SelectedFile>>(conversation.draftFiles, type)
                            "Draft with ${files.size} attachment(s)"
                        } catch (e: Exception) {
                            "Draft with attachments"
                        }
                    }
                    else -> "Draft"
                }

                dateTimeTextView?.text = "$dateStr - $draftPreview"
            } else {
                // Hide draft indicator for normal conversations
                draftIndicator?.visibility = View.GONE

                // Show normal last message
                if (conversation.lastMessage.isNotEmpty()) {
                    dateTimeTextView?.text = "$dateStr - ${conversation.lastMessage}"
                } else {
                    dateTimeTextView?.text = dateStr
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