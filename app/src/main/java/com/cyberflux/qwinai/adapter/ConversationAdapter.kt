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
import com.cyberflux.qwinai.utils.FileUtil
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit,
    private val onConversationLongClick: (View, Conversation) -> Unit,
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback) {

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conversationText: TextView = itemView.findViewById(R.id.conversationText)
        val aiModelInfo: TextView = itemView.findViewById(R.id.aiModelInfo)
        val conversationDateTime: TextView = itemView.findViewById(R.id.conversationDateTime)
        val btnDropdown: ImageButton = itemView.findViewById(R.id.btnDropdown)
        val savedIndicator: ImageView = itemView.findViewById(R.id.savedIndicator)
        val draftIndicator: TextView = itemView.findViewById(R.id.draftIndicator) // Add this

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
        holder.aiModelInfo.text = "Model: ${conversation.aiModel}"

        // Format date for display
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateString = dateFormat.format(Date(conversation.timestamp))
        val timeString = timeFormat.format(Date(conversation.timestamp))

        // Update to show draft status if present
        if (conversation.hasDraft) {
            // Show draft indicator
            holder.draftIndicator.visibility = View.VISIBLE

            // Show draft preview in the last message area
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

            // Format with date/time and draft preview
            holder.conversationDateTime.text = "$dateString $timeString - $draftPreview"
        } else {
            // Hide draft indicator for normal conversations
            holder.draftIndicator.visibility = View.GONE

            // Show normal last message
            if (conversation.lastMessage.isNotEmpty()) {
                holder.conversationDateTime.text = "$dateString $timeString - ${conversation.lastMessage}"
            } else {
                holder.conversationDateTime.text = "$dateString $timeString"
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