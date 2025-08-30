package com.cyberflux.qwinai

import android.app.Application
import kotlinx.coroutines.cancel
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.lifecycle.viewModelScope
import com.cyberflux.qwinai.MyApp.Companion.database
import com.cyberflux.qwinai.dao.ConversationDao
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.Conversation
import com.cyberflux.qwinai.model.ConversationGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {
    private val conversationDao = AppDatabase.getDatabase(application).conversationDao()
    private val chatMessageDao = AppDatabase.getDatabase(application).chatMessageDao()

    private val _allConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val allConversations: StateFlow<List<Conversation>> = _allConversations.asStateFlow()

    private val _filteredConversations = MutableStateFlow<List<Conversation>>(emptyList())

    // New LiveData for grouped conversations
    private val _groupedConversations = MutableStateFlow<List<ConversationGroup>>(emptyList())
    val groupedConversations: StateFlow<List<ConversationGroup>> = _groupedConversations.asStateFlow()

    private val inMemoryPrivateMessages = mutableListOf<ChatMessage>()

    private var currentSearchQuery: String = ""

    // Add this property for saved conversations  
    private val _savedConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val savedConversations: StateFlow<List<Conversation>> = _savedConversations.asStateFlow()
    
    private val _savedGroupedConversations = MutableStateFlow<List<ConversationGroup>>(emptyList())
    val savedGroupedConversations: StateFlow<List<ConversationGroup>> = _savedGroupedConversations.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    init {
        loadConversations()
    }
    fun getConversationDao(): ConversationDao {
        return database.conversationDao()
    }
    fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val conversations = withContext(Dispatchers.IO) {
                    conversationDao.getAllConversations()
                }
                _allConversations.value = conversations
                
                // Update saved conversations
                val savedConversations = conversations.filter { it.saved }
                _savedConversations.value = savedConversations

                // Apply current search filter if exists
                if (currentSearchQuery.isNotEmpty()) {
                    searchConversations(currentSearchQuery)
                } else {
                    _filteredConversations.value = conversations
                    // Group conversations by date and update the groupedConversations LiveData
                    groupConversationsByDate(conversations)
                }

                Timber.tag("ConversationsViewModel").d("Loaded ${conversations.size} conversations, ${savedConversations.size} saved")
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e, "Error loading conversations")
            } finally {
                _isLoading.value = false
            }
        }
    }
    private fun groupConversationsByDate(conversations: List<Conversation>) {
        Calendar.getInstance()

        // Set to beginning of today
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Set to beginning of yesterday
        val yesterdayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }

        // Get timestamps for date ranges
        val todayTimestamp = todayStart.timeInMillis
        val yesterdayTimestamp = yesterdayStart.timeInMillis
        val sevenDaysAgoTimestamp = todayTimestamp - TimeUnit.DAYS.toMillis(7)
        val fourteenDaysAgoTimestamp = todayTimestamp - TimeUnit.DAYS.toMillis(14)
        val twentyOneDaysAgoTimestamp = todayTimestamp - TimeUnit.DAYS.toMillis(21)

        // Group conversations
        val todayConversations = conversations.filter { it.lastModified >= todayTimestamp }
        val yesterdayConversations = conversations.filter {
            it.lastModified < todayTimestamp && it.lastModified >= yesterdayTimestamp
        }
        val last7DaysConversations = conversations.filter {
            it.lastModified < yesterdayTimestamp && it.lastModified >= sevenDaysAgoTimestamp
        }
        val last14DaysConversations = conversations.filter {
            it.lastModified < sevenDaysAgoTimestamp && it.lastModified >= fourteenDaysAgoTimestamp
        }
        val last21DaysConversations = conversations.filter {
            it.lastModified < fourteenDaysAgoTimestamp && it.lastModified >= twentyOneDaysAgoTimestamp
        }
        val olderConversations = conversations.filter { it.lastModified < twentyOneDaysAgoTimestamp }

        // Create groups only for non-empty lists
        val groups = mutableListOf<ConversationGroup>()

        if (todayConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Today", todayConversations))
        }

        if (yesterdayConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Yesterday", yesterdayConversations))
        }

        if (last7DaysConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Last 7 Days", last7DaysConversations))
        }

        if (last14DaysConversations.isNotEmpty()) {
            groups.add(ConversationGroup("8-14 Days Ago", last14DaysConversations))
        }

        if (last21DaysConversations.isNotEmpty()) {
            groups.add(ConversationGroup("15-21 Days Ago", last21DaysConversations))
        }

        if (olderConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Older", olderConversations))
        }

        _groupedConversations.value = groups

        // Also filter saved conversations and group them separately
        val savedConversations = conversations.filter { it.saved }
        groupSavedConversationsByDate(savedConversations)
    }

    private fun groupSavedConversationsByDate(savedConversations: List<Conversation>) {
        Calendar.getInstance()

        // Set to beginning of today
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Set to beginning of yesterday
        val yesterdayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }

        // Get timestamps for date ranges
        val todayTimestamp = todayStart.timeInMillis
        val yesterdayTimestamp = yesterdayStart.timeInMillis
        val sevenDaysAgoTimestamp = todayTimestamp - TimeUnit.DAYS.toMillis(7)
        val fourteenDaysAgoTimestamp = todayTimestamp - TimeUnit.DAYS.toMillis(14)
        val twentyOneDaysAgoTimestamp = todayTimestamp - TimeUnit.DAYS.toMillis(21)

        // Group conversations
        val todayConversations = savedConversations.filter { it.lastModified >= todayTimestamp }
        val yesterdayConversations = savedConversations.filter {
            it.lastModified < todayTimestamp && it.lastModified >= yesterdayTimestamp
        }
        val last7DaysConversations = savedConversations.filter {
            it.lastModified < yesterdayTimestamp && it.lastModified >= sevenDaysAgoTimestamp
        }
        val last14DaysConversations = savedConversations.filter {
            it.lastModified < sevenDaysAgoTimestamp && it.lastModified >= fourteenDaysAgoTimestamp
        }
        val last21DaysConversations = savedConversations.filter {
            it.lastModified < fourteenDaysAgoTimestamp && it.lastModified >= twentyOneDaysAgoTimestamp
        }
        val olderConversations = savedConversations.filter { it.lastModified < twentyOneDaysAgoTimestamp }

        // Create groups only for non-empty lists
        val groups = mutableListOf<ConversationGroup>()

        if (todayConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Today", todayConversations))
        }

        if (yesterdayConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Yesterday", yesterdayConversations))
        }

        if (last7DaysConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Last 7 Days", last7DaysConversations))
        }

        if (last14DaysConversations.isNotEmpty()) {
            groups.add(ConversationGroup("8-14 Days Ago", last14DaysConversations))
        }

        if (last21DaysConversations.isNotEmpty()) {
            groups.add(ConversationGroup("15-21 Days Ago", last21DaysConversations))
        }

        if (olderConversations.isNotEmpty()) {
            groups.add(ConversationGroup("Older", olderConversations))
        }

        _savedGroupedConversations.value = groups
    }

    fun searchConversations(query: String) {
        currentSearchQuery = query.trim().lowercase()

        viewModelScope.launch {
            try {
                val allConvos = _allConversations.value ?: emptyList()

                if (currentSearchQuery.isEmpty()) {
                    // If search is empty, show all conversations
                    _filteredConversations.value = allConvos
                    // Also update grouped conversations
                    groupConversationsByDate(allConvos)
                    Timber.tag("ConversationsViewModel").d("Search cleared, showing all ${allConvos.size} conversations")
                } else {
                    // Filter conversations that contain the search query in their name, title or lastMessage
                    val filtered = allConvos.filter { conversation ->
                        conversation.name.lowercase().contains(currentSearchQuery) ||
                                conversation.title.lowercase().contains(currentSearchQuery) ||
                                conversation.lastMessage.lowercase().contains(currentSearchQuery)
                    }

                    _filteredConversations.value = filtered
                    // Update grouped conversations with the filtered results
                    groupConversationsByDate(filtered)
                    Timber.tag("ConversationsViewModel").d("Search for '$currentSearchQuery' found ${filtered.size} conversations")
                }
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e, "Error searching conversations")
                // In case of error, just show all conversations
                _filteredConversations.value = _allConversations.value
            }
        }
    }   fun toggleSavedStatus(conversation: Conversation) {
        viewModelScope.launch {
            try {
                val updated = conversation.copy(saved = !conversation.saved)
                conversationDao.update(updated)
                loadConversations() // This will also refresh the grouped conversations
                Timber.tag("ConversationsViewModel").d("Toggled saved status for conversation: ${conversation.id} to ${updated.saved}")
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e, "Error toggling saved status: ${e.message}")
            }
        }
    }
    suspend fun getConversationsWithDrafts(): List<Conversation> {
        return withContext(Dispatchers.IO) {
            try {
                database.conversationDao().getConversationsWithDrafts()
            } catch (e: Exception) {
                Timber.e(e, "Error getting conversations with drafts: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun countConversationsWithDrafts(): Int {
        return withContext(Dispatchers.IO) {
            try {
                database.conversationDao().countConversationsWithDrafts()
            } catch (e: Exception) {
                Timber.e(e, "Error counting conversations with drafts: ${e.message}")
                0
            }
        }
    }

    fun deleteAllUnsavedConversations() {
        viewModelScope.launch {
            try {
                val count = conversationDao.deleteAllUnsavedConversations()
                loadConversations()
                Timber.tag("ConversationsViewModel").d("Deleted all unsaved conversations: $count")
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e, "Error deleting unsaved conversations: ${e.message}")
            }
        }
    }

    fun deleteAllSavedConversations() {
        viewModelScope.launch {
            try {
                val count = conversationDao.deleteAllSavedConversations()
                loadConversations()
                Timber.tag("ConversationsViewModel").d("Deleted all saved conversations: $count")
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e, "Error deleting saved conversations: ${e.message}")
            }
        }
    }

    fun addConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationDao.insert(conversation)
            loadConversations()
        }
    }

    fun updateConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationDao.update(conversation)
            loadConversations()
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationDao.delete(conversation)
            loadConversations()
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            try {
                conversationDao.deleteAllConversations()
                loadConversations()
                Timber.tag("ConversationsViewModel").d("All conversations deleted successfully.")
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e("Error deleting conversations: ${e.message}")
            }
        }
    }

    fun getMessagesByConversationId(conversationId: String, page: Int): StateFlow<List<ChatMessage>> {
        // For private conversations, use in-memory storage
        if (conversationId.startsWith("private_")) {
            Timber.tag("VIEWMODEL").d("Getting messages for private conversation: $conversationId")

            // Create a MutableStateFlow with our filtered private messages
            val stateFlow = MutableStateFlow<List<ChatMessage>>(emptyList())

            // Filter private messages by conversation ID
            val filteredMessages = inMemoryPrivateMessages.filter {
                it.conversationId == conversationId
            }

            // Apply pagination if needed (simple version)
            val pageSize = 20
            val startIndex = (page - 1) * pageSize
            val endIndex = minOf(startIndex + pageSize, filteredMessages.size)

            // Get the page of messages if in range
            val pageMessages = if (startIndex < filteredMessages.size) {
                filteredMessages.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            stateFlow.value = pageMessages
            return stateFlow.asStateFlow()
        }

        // For regular conversations, use the database
        val offset = (page - 1) * 20
        val stateFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messages = chatMessageDao.getMessagesByConversationId(conversationId, offset)
                stateFlow.value = messages
            } catch (e: Exception) {
                Timber.tag("VIEWMODEL").e(e, "Error getting messages for conversation: $conversationId")
                stateFlow.value = emptyList()
            }
        }
        
        return stateFlow.asStateFlow()
    }

    fun addChatMessage(message: ChatMessage) {
        // For private conversations, store in memory only
        if (message.conversationId.startsWith("private_")) {
            Timber.tag("VIEWMODEL").d("Storing private message in memory: ${message.id}")

            // Add to in-memory list
            synchronized(inMemoryPrivateMessages) {
                // Remove any previous message with same ID to avoid duplicates
                inMemoryPrivateMessages.removeIf { it.id == message.id }
                inMemoryPrivateMessages.add(message)
            }
            return
        }

        // For regular messages, save to database
        viewModelScope.launch {
            try {
                chatMessageDao.insert(message)
            } catch (e: Exception) {
                Timber.tag("VIEWMODEL").e(e, "Error adding chat message: ${e.message}")
            }
        }
    }

    suspend fun getConversationById(conversationId: String): Conversation? {
        // Skip database lookup for private conversations
        if (conversationId.startsWith("private_")) {
            Timber.tag("VIEWMODEL").d("Private conversation ID detected, skipping database lookup")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // Convert string ID to Long only for regular conversations
                val id = conversationId.toLong()
                val conversation = conversationDao.getConversationById(id)

                // Log retrieved conversation details for debugging
                if (conversation != null) {
                    Timber.tag("VIEWMODEL").d("Found conversation: id=${conversation.id}, " +
                            "title=${conversation.title}, hasDraft=${conversation.hasDraft}, " +
                            "draftText=${conversation.draftText.take(20)}, " +
                            "draftFiles=${if (conversation.draftFiles.isNotEmpty()) "present" else "empty"}")
                } else {
                    Timber.tag("VIEWMODEL").d("No conversation found with id: $conversationId")
                }

                return@withContext conversation
            } catch (e: NumberFormatException) {
                Timber.tag("VIEWMODEL").e(e, "Invalid conversation ID format: $conversationId")
                return@withContext null
            } catch (e: Exception) {
                Timber.tag("VIEWMODEL").e(e, "Error fetching conversation: $conversationId")
                return@withContext null
            }
        }
    }
    fun saveMessage(message: ChatMessage) {
        // For private messages, store in memory only
        if (message.conversationId.startsWith("private_")) {
            Timber.tag("VIEWMODEL").d("Saving private message in memory: ${message.id}")
            synchronized(inMemoryPrivateMessages) {
                // Replace if exists, otherwise add
                val index = inMemoryPrivateMessages.indexOfFirst { it.id == message.id }
                if (index != -1) {
                    inMemoryPrivateMessages[index] = message
                } else {
                    inMemoryPrivateMessages.add(message)
                }
            }
            return
        }

        // For regular messages, use the database
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if message already exists in chatMessageDao
                val existing = chatMessageDao.getMessageById(message.id)
                if (existing != null) {
                    chatMessageDao.update(message)
                    Timber.tag("VIEWMODEL").d("Updated message in database: ${message.id}")
                } else {
                    chatMessageDao.insert(message)
                    Timber.tag("VIEWMODEL").d("Inserted new message in database: ${message.id}")
                }
            } catch (e: Exception) {
                Timber.tag("VIEWMODEL").e(e, "Error saving message: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel all background operations
        viewModelScope.cancel()
    }

    suspend fun getAllConversationMessages(conversationId: String): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                chatMessageDao.getAllMessagesForConversation(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Error loading all messages for conversation $conversationId")
                emptyList()
            }
        }
    }
    
    /**
     * Clear all conversations (alias for deleteAllConversations)
     */
    suspend fun clearAllConversations() {
        deleteAllConversations()
    }
    
    /**
     * Clear all saved conversations (removes saved status, doesn't delete)
     */
    suspend fun clearAllSavedConversations() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val savedConversations = conversationDao.getAllConversations().filter { it.saved }
                    savedConversations.forEach { conversation ->
                        val updatedConversation = conversation.copy(saved = false)
                        conversationDao.update(updatedConversation)
                    }
                }
                // Reload conversations to update UI
                loadConversations()
                Timber.d("Cleared saved status from all conversations")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing saved conversations")
            }
        }
    }
    
    /**
     * Clear search and show all conversations
     */
    fun clearSearch() {
        currentSearchQuery = ""
        _filteredConversations.value = _allConversations.value
        groupConversationsByDate(_allConversations.value)
    }
    
    /**
     * Delete conversation by ID
     */
    suspend fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Delete all messages for this conversation first
                    chatMessageDao.deleteMessagesForConversation(conversationId.toString())
                    // Then delete the conversation
                    conversationDao.deleteConversationById(conversationId)
                }
                // Reload conversations
                loadConversations()
                Timber.d("Deleted conversation with ID: $conversationId")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting conversation $conversationId")
            }
        }
    }


    fun clearPrivateMessages() {
        synchronized(inMemoryPrivateMessages) {
            inMemoryPrivateMessages.clear()
        }
        Timber.tag("VIEWMODEL").d("Cleared all private messages from memory")
    }
}