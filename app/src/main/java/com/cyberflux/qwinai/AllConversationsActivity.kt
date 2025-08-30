package com.cyberflux.qwinai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.ConversationAdapter
import com.cyberflux.qwinai.databinding.ActivityAllConversationsBinding
import com.cyberflux.qwinai.model.Conversation
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.ConversationAttachmentsManager
import com.cyberflux.qwinai.utils.HapticManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Full-screen activity for viewing all conversations with search and filter capabilities
 */
class AllConversationsActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityAllConversationsBinding
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var attachmentsManager: ConversationAttachmentsManager
    private val viewModel: ConversationsViewModel by viewModels()
    
    private var showSavedOnly = false
    private var currentFilter = FilterType.ALL
    private var currentSortOrder = SortOrder.DATE_DESC
    
    companion object {
        private const val EXTRA_SHOW_SAVED_ONLY = "show_saved_only"
        
        fun createIntent(context: Context, showSavedOnly: Boolean = false): Intent {
            return Intent(context, AllConversationsActivity::class.java).apply {
                putExtra(EXTRA_SHOW_SAVED_ONLY, showSavedOnly)
            }
        }
    }
    
    enum class FilterType {
        ALL, WITH_DRAFTS, WITH_ATTACHMENTS
    }
    
    enum class SortOrder {
        DATE_DESC, DATE_ASC, TITLE_ASC, TITLE_DESC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        showSavedOnly = intent.getBooleanExtra(EXTRA_SHOW_SAVED_ONLY, false)
        
        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupFilters()
        setupFab()
        observeConversations()
        
        // Apply initial filter
        applyCurrentFilters()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Set title based on mode
        binding.tvTitle.text = if (showSavedOnly) "Saved Conversations" else "All Conversations"
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupRecyclerView() {
        val database = com.cyberflux.qwinai.database.AppDatabase.getDatabase(this)
        attachmentsManager = ConversationAttachmentsManager(this, database.chatMessageDao())
        
        conversationAdapter = ConversationAdapter(
            onConversationClick = { conversation -> openConversation(conversation) },
            onConversationLongClick = { view, conversation -> showConversationOptions(view, conversation) },
            attachmentsManager = attachmentsManager
        )
        
        binding.rvConversations.apply {
            layoutManager = LinearLayoutManager(this@AllConversationsActivity)
            adapter = conversationAdapter
            
            // Add item decoration for spacing
            val spacing = resources.getDimensionPixelSize(R.dimen.small_margin)
            addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(
                this@AllConversationsActivity,
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            ))
        }
        
        // Add swipe to delete/save functionality
        setupSwipeActions()
    }
    
    private fun setupSwipeActions() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val conversation = conversationAdapter.currentList[position]
                
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // Delete conversation
                        showDeleteConfirmation(conversation, position)
                    }
                    ItemTouchHelper.RIGHT -> {
                        // Toggle save state
                        toggleSaveConversation(conversation)
                    }
                }
            }
            
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Disable swipe for saved-only view on delete
                return if (showSavedOnly) ItemTouchHelper.RIGHT else super.getSwipeDirs(recyclerView, viewHolder)
            }
        })
        
        itemTouchHelper.attachToRecyclerView(binding.rvConversations)
    }
    
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                performSearch(newText)
                return true
            }
        })
        
        // Clear focus initially
        binding.searchView.clearFocus()
    }
    
    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipAll -> FilterType.ALL
                R.id.chipWithDrafts -> FilterType.WITH_DRAFTS
                R.id.chipWithAttachments -> FilterType.WITH_ATTACHMENTS
                else -> FilterType.ALL
            }
            applyCurrentFilters()
        }
        
        binding.btnSort.setOnClickListener {
            showSortOptions()
        }
    }
    
    private fun setupFab() {
        binding.fabNewConversation.setOnClickListener {
            // Create new conversation
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("create_new_conversation", true)
            }
            startActivity(intent)
            finish()
        }
    }
    
    private fun observeConversations() {
        lifecycleScope.launch {
            if (showSavedOnly) {
                viewModel.savedConversations.collectLatest { conversations ->
                    handleConversationsUpdate(conversations)
                }
            } else {
                viewModel.allConversations.collectLatest { conversations ->
                    handleConversationsUpdate(conversations)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun handleConversationsUpdate(conversations: List<Conversation>) {
        val filteredConversations = applyFilters(conversations)
        val sortedConversations = applySorting(filteredConversations)
        
        conversationAdapter.submitList(sortedConversations)
        
        updateUI(sortedConversations)
    }
    
    private fun updateUI(conversations: List<Conversation>) {
        val count = conversations.size
        binding.tvConversationCount.text = when (count) {
            0 -> "No conversations"
            1 -> "1 conversation"
            else -> "$count conversations"
        }
        
        // Show/hide empty state
        val isEmpty = conversations.isEmpty()
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvConversations.visibility = if (isEmpty) View.GONE else View.VISIBLE
        
        if (isEmpty) {
            updateEmptyStateMessage()
        }
    }
    
    private fun updateEmptyStateMessage() {
        when {
            showSavedOnly -> {
                binding.tvEmptyMessage.text = "No saved conversations"
                binding.tvEmptySubtitle.text = "Conversations you save will appear here"
            }
            currentFilter == FilterType.WITH_DRAFTS -> {
                binding.tvEmptyMessage.text = "No conversations with drafts"
                binding.tvEmptySubtitle.text = "Start typing a message to create a draft"
            }
            currentFilter == FilterType.WITH_ATTACHMENTS -> {
                binding.tvEmptyMessage.text = "No conversations with attachments"
                binding.tvEmptySubtitle.text = "Add files to conversations to see them here"
            }
            binding.searchView.query.isNotEmpty() -> {
                binding.tvEmptyMessage.text = "No conversations found"
                binding.tvEmptySubtitle.text = "Try adjusting your search terms"
            }
            else -> {
                binding.tvEmptyMessage.text = "No conversations yet"
                binding.tvEmptySubtitle.text = "Start a new conversation to get started"
            }
        }
    }
    
    private fun applyCurrentFilters() {
        // Trigger a refresh with current filters
        val searchQuery = binding.searchView.query?.toString()
        if (!searchQuery.isNullOrEmpty()) {
            performSearch(searchQuery)
        } else {
            // Just reapply filters to current data
            lifecycleScope.launch {
                val currentList = conversationAdapter.currentList
                if (currentList.isNotEmpty()) {
                    handleConversationsUpdate(currentList)
                }
            }
        }
    }
    
    private fun applyFilters(conversations: List<Conversation>): List<Conversation> {
        return when (currentFilter) {
            FilterType.ALL -> conversations
            FilterType.WITH_DRAFTS -> conversations.filter { it.hasDraft }
            FilterType.WITH_ATTACHMENTS -> conversations.filter { hasAttachments(it) }
        }
    }
    
    private fun applySorting(conversations: List<Conversation>): List<Conversation> {
        return when (currentSortOrder) {
            SortOrder.DATE_DESC -> conversations.sortedByDescending { it.lastModified }
            SortOrder.DATE_ASC -> conversations.sortedBy { it.lastModified }
            SortOrder.TITLE_ASC -> conversations.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> conversations.sortedByDescending { it.title.lowercase() }
        }
    }
    
    private fun hasAttachments(conversation: Conversation): Boolean {
        // Check if conversation has files in drafts
        // For now, we'll only check draft files to avoid suspend function issue
        // TODO: Consider caching message attachments or using a different approach
        return conversation.draftFiles.isNotEmpty()
    }
    
    private fun performSearch(query: String?) {
        if (query.isNullOrEmpty()) {
            viewModel.clearSearch()
        } else {
            viewModel.searchConversations(query)
        }
    }
    
    private fun showSortOptions() {
        val options = arrayOf(
            "Newest first",
            "Oldest first", 
            "Title A-Z",
            "Title Z-A"
        )
        
        val currentSelection = when (currentSortOrder) {
            SortOrder.DATE_DESC -> 0
            SortOrder.DATE_ASC -> 1
            SortOrder.TITLE_ASC -> 2
            SortOrder.TITLE_DESC -> 3
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Sort conversations")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                currentSortOrder = when (which) {
                    0 -> SortOrder.DATE_DESC
                    1 -> SortOrder.DATE_ASC
                    2 -> SortOrder.TITLE_ASC
                    3 -> SortOrder.TITLE_DESC
                    else -> SortOrder.DATE_DESC
                }
                applyCurrentFilters()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openConversation(conversation: Conversation) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("conversation_id", conversation.id.toString())
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    
    private fun showConversationOptions(view: View, conversation: Conversation) {
        // Provide haptic feedback
        HapticManager.lightVibration(this)
        
        // Show options dialog
        val options = mutableListOf<String>()
        if (conversation.saved) {
            options.add("Remove from saved")
        } else {
            options.add("Save conversation")
        }
        options.addAll(listOf("Rename", "Delete", "Share"))
        
        MaterialAlertDialogBuilder(this)
            .setTitle(conversation.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> toggleSaveConversation(conversation)
                    1 -> renameConversation(conversation)
                    2 -> deleteConversation(conversation)
                    3 -> shareConversation(conversation)
                }
            }
            .show()
    }
    
    private fun toggleSaveConversation(conversation: Conversation) {
        lifecycleScope.launch {
            try {
                val updatedConversation = conversation.copy(saved = !conversation.saved)
                viewModel.updateConversation(updatedConversation)
                
                val message = if (updatedConversation.saved) "Conversation saved" else "Removed from saved"
                showSnackbar(message)
                
            } catch (e: Exception) {
                Timber.e(e, "Error toggling save state for conversation ${conversation.id}")
                showSnackbar("Error updating conversation")
            }
        }
    }
    
    private fun renameConversation(conversation: Conversation) {
        // Implementation for renaming conversation
        // This could open a dialog with EditText
        showSnackbar("Rename feature coming soon")
    }
    
    private fun deleteConversation(conversation: Conversation) {
        showDeleteConfirmation(conversation)
    }
    
    private fun showDeleteConfirmation(conversation: Conversation, adapterPosition: Int = -1) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete conversation")
            .setMessage("Are you sure you want to delete \"${conversation.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete(conversation)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Restore item position if needed
                if (adapterPosition >= 0) {
                    conversationAdapter.notifyItemChanged(adapterPosition)
                }
            }
            .show()
    }
    
    private fun performDelete(conversation: Conversation) {
        lifecycleScope.launch {
            try {
                viewModel.deleteConversation(conversation.id)
                showSnackbar("Conversation deleted")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting conversation ${conversation.id}")
                showSnackbar("Error deleting conversation")
            }
        }
    }
    
    private fun shareConversation(conversation: Conversation) {
        // Implementation for sharing conversation
        showSnackbar("Share feature coming soon")
    }
    
    private fun showSnackbar(message: String) {
        com.google.android.material.snackbar.Snackbar.make(binding.root, message, 
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_clear_all -> {
                showClearAllConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showClearAllConfirmation() {
        val title = if (showSavedOnly) "Clear all saved conversations" else "Clear all conversations"
        val message = if (showSavedOnly) 
            "Are you sure you want to remove all saved conversations? This will only remove them from your saved list." 
        else 
            "Are you sure you want to delete all conversations? This action cannot be undone."
            
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Clear") { _, _ ->
                if (showSavedOnly) {
                    clearAllSaved()
                } else {
                    clearAllConversations()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearAllSaved() {
        lifecycleScope.launch {
            try {
                viewModel.clearAllSavedConversations()
                showSnackbar("All conversations removed from saved")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing saved conversations")
                showSnackbar("Error clearing saved conversations")
            }
        }
    }
    
    private fun clearAllConversations() {
        lifecycleScope.launch {
            try {
                viewModel.clearAllConversations()
                showSnackbar("All conversations deleted")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing all conversations")
                showSnackbar("Error clearing conversations")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        conversationAdapter.cleanup()
    }
}