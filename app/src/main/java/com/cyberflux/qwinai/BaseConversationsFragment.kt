package com.cyberflux.qwinai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.GroupedConversationAdapter
import com.cyberflux.qwinai.model.Conversation

/**
 * Base fragment for displaying conversations (either all or saved)
 */
abstract class BaseConversationsFragment : Fragment() {

    protected val viewModel: ConversationsViewModel by activityViewModels()
    protected lateinit var groupedConversationAdapter: GroupedConversationAdapter
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var emptyStateView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_conversation_list, container, false)
        recyclerView = view.findViewById(R.id.conversationsRecyclerView)
        emptyStateView = view.findViewById(R.id.emptyStateLayout)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        val database = com.cyberflux.qwinai.database.AppDatabase.getDatabase(requireContext())
        val attachmentsManager = com.cyberflux.qwinai.utils.ConversationAttachmentsManager(
            requireContext(),
            database.chatMessageDao()
        )
        
        groupedConversationAdapter = GroupedConversationAdapter(
            onConversationClick = { conversation -> openConversation(conversation) },
            onConversationLongClick = { view, conversation ->
                showConversationMenu(view, conversation)
            },
            attachmentsManager = attachmentsManager
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupedConversationAdapter
        }
    }

    // Abstract method to be implemented by subclasses to observe the correct LiveData
    abstract fun observeViewModel()

    protected fun openConversation(conversation: Conversation) {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            putExtra("CONVERSATION_ID", conversation.id)
            putExtra("AI_MODEL", conversation.aiModel)
            putExtra("CONVERSATION_NAME", conversation.name)
            putExtra("LOAD_ALL_VERSIONS", true)
            // CRITICAL: Flag to indicate this is a conversation continuation
            putExtra("CHECK_ACTIVE_STREAMING", true)
        }
        startActivity(intent)
    }

    protected fun showConversationMenu(view: View, conversation: Conversation) {
        PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(R.menu.conversation_menu, menu)

            // Configure menu based on saved status
            menu.findItem(R.id.action_save)?.isVisible = !conversation.saved
            menu.findItem(R.id.action_unsave)?.isVisible = conversation.saved

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save, R.id.action_save -> {
                        viewModel.toggleSavedStatus(conversation)
                        Toast.makeText(requireContext(), "Conversation saved", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.action_unsave, R.id.action_unsave -> {
                        viewModel.toggleSavedStatus(conversation)
                        Toast.makeText(requireContext(), "Conversation removed from saved", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_rename, R.id.action_rename -> {
                        renameConversation(conversation)
                        true
                    }
                    R.id.menu_delete, R.id.action_delete -> {
                        deleteConversation(conversation)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun renameConversation(conversation: Conversation) {
        val input = EditText(requireContext()).apply {
            setText(conversation.name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Rename Conversation")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    conversation.name = newName
                    conversation.title = newName
                    viewModel.updateConversation(conversation)
                    Toast.makeText(context, "Conversation renamed.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteConversation(conversation: Conversation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteConversation(conversation)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadConversations()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::groupedConversationAdapter.isInitialized) {
            groupedConversationAdapter.cleanup()
        }
    }
}