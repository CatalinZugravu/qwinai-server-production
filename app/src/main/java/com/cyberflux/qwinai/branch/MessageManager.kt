package com.cyberflux.qwinai.branch

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.ConversationsViewModel
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.utils.ConversationTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

class MessageManager(
    private val viewModel: ConversationsViewModel,
    private val adapter: ChatAdapter,
    private val lifecycleScope: CoroutineScope,
    private val chatAdapter: ChatAdapter,
    private val tokenManager: ConversationTokenManager,
    private var recyclerViewRef: RecyclerView? = null,
    var isStreamingActive: Boolean = false,
    private val internalMessageList: MutableList<ChatMessage> = mutableListOf<ChatMessage>()
) {

    fun initialize(messages: List<ChatMessage>, conversationId: String) {
        val conversationMessages = messages
            .filter { it.conversationId == conversationId }
            .sortedBy { it.timestamp }

        // CRITICAL FIX: Clean up any messages with interruption text before initializing
        val cleanedMessages = cleanupInterruptionMessages(conversationMessages)
        
        // CRITICAL FIX: Fix loading states for messages with content
        val fixedMessages = cleanedMessages.map { message ->
            if (!message.isUser && message.message.isNotEmpty()) {
                // CRITICAL: Any message with content should show content, not loading - even if marked as generating
                message.copy(isLoading = false)
            } else {
                message
            }
        }

        internalMessageList.clear()
        internalMessageList.addAll(fixedMessages)
        synchronizeWithAdapter(fixedMessages)

        Timber.d("Initialized message manager with ${fixedMessages.size} messages for conversation $conversationId")
    }
    
    /**
     * CRITICAL FIX: Clean up interruption messages from loaded messages
     */
    private fun cleanupInterruptionMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.map { message ->
            if (!message.isUser && 
                message.message.contains("⚠️ Generation was interrupted. Tap 'Continue' to resume.")) {
                
                // Remove the interruption text
                val cleanedMessage = message.message
                    .replace("\n\n⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                    .replace("⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                    .trim()
                
                // If there's substantial content after cleaning, mark as complete
                if (cleanedMessage.length > 50) {
                    Timber.d("🧹 MessageManager: Cleaning message ${message.id} with ${cleanedMessage.length} chars")
                    
                    // Save the cleaned message to database
                    lifecycleScope.launch {
                        try {
                            val fixedMessage = message.copy(
                                message = cleanedMessage,
                                isGenerating = false,
                                showButtons = true,
                                canContinueStreaming = false,
                                error = false,
                                lastModified = System.currentTimeMillis()
                            )
                            viewModel.saveMessage(fixedMessage)
                            Timber.d("🧹 Saved cleaned message to database: ${message.id}")
                        } catch (e: Exception) {
                            Timber.e(e, "Error saving cleaned message: ${e.message}")
                        }
                    }
                    
                    message.copy(
                        message = cleanedMessage,
                        isGenerating = false,
                        showButtons = true,
                        canContinueStreaming = false,
                        error = false
                    )
                } else {
                    message // Keep original if no substantial content
                }
            } else {
                message // No interruption text, keep original
            }
        }
    }

    private fun synchronizeWithAdapter(messages: List<ChatMessage>) {
        val adapterMessageIds = adapter.currentList.map { it.id }.toSet()
        val internalMessageIds = messages.map { it.id }.toSet()

        val newAdapterList = adapter.currentList.toMutableList()
        newAdapterList.removeAll { it.id !in internalMessageIds }

        val messagesToAdd = messages.filter { it.id !in adapterMessageIds }
        newAdapterList.addAll(messagesToAdd)
        newAdapterList.sortBy { it.timestamp }

        adapter.submitList(newAdapterList)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun updateStreamingContent(messageId: String, content: String) {
        try {
            val internalIndex = internalMessageList.indexOfFirst { it.id == messageId }
            if (internalIndex != -1) {
                val oldMessage = internalMessageList[internalIndex]

                val updatedMessage = oldMessage.copy(
                    message = content
                )
                internalMessageList[internalIndex] = updatedMessage

                lifecycleScope.launch(Dispatchers.Main) {
                    // Pass content to adapter
                    chatAdapter.updateStreamingContent(messageId, content)

                    if (content.length - oldMessage.message.length > 30) {
                        recyclerViewRef?.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            } else {
                Timber.w("⚠️ Cannot find message $messageId for streaming update")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in streaming content update: ${e.message}")
        }
    }

    // În MessageManager.kt, linia aproximativ 106
    fun updateLoadingState(
        messageId: String,
        isGenerating: Boolean,
        isWebSearching: Boolean,
        customStatusText: String? = null,  // Parametru nou
        customStatusColor: Int? = null     // Parametru nou
    ) {
        try {
            val internalIndex = internalMessageList.indexOfFirst { it.id == messageId }
            if (internalIndex != -1) {
                val oldMessage = internalMessageList[internalIndex]

                val updatedMessage = oldMessage.copy(
                    isGenerating = isGenerating,
                    isWebSearchActive = isWebSearching,
                    isLoading = isGenerating || isWebSearching,
                    initialIndicatorText = customStatusText ?: oldMessage.initialIndicatorText,  // Modificare aici
                    initialIndicatorColor = customStatusColor ?: oldMessage.initialIndicatorColor  // Modificare aici
                )
                internalMessageList[internalIndex] = updatedMessage

                lifecycleScope.launch(Dispatchers.Main) {
                    chatAdapter.updateLoadingState(
                        messageId,
                        isGenerating,
                        isWebSearching,
                        customStatusText,  // Parametru nou
                        customStatusColor  // Parametru nou
                    )
                }
            } else {
                Timber.w("⚠️ Cannot find message $messageId for loading state update")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in loading state update: ${e.message}")
        }
    }


    suspend fun ensureConversationContextLoaded(conversationId: String) {
        val currentMessages = internalMessageList.filter { it.conversationId == conversationId }
        Timber.d("Current messages in memory for conversation $conversationId: ${currentMessages.size}")

        try {
            val dbMessages = withContext(Dispatchers.IO) {
                viewModel.getAllConversationMessages(conversationId)
            }

            if (dbMessages.isNotEmpty()) {
                Timber.d("Loading ${dbMessages.size} messages from database for conversation context")

                val existingIds = internalMessageList.map { it.id }.toSet()
                val newMessages = dbMessages.filter { it.id !in existingIds }

                if (newMessages.isNotEmpty()) {
                    internalMessageList.addAll(newMessages)
                    internalMessageList.sortBy { it.timestamp }
                    Timber.d("Added ${newMessages.size} messages to context")
                    synchronizeWithAdapter(internalMessageList)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading conversation context from database: ${e.message}")
        }
    }

    suspend fun ensureFullSynchronizationForApiWithFallback(conversationId: String): List<ChatMessage> {
        ensureConversationContextLoaded(conversationId)
        synchronizeWithAdapter(internalMessageList)

        val conversationMessages = internalMessageList.filter { it.conversationId == conversationId }
        Timber.d("Providing ${conversationMessages.size} messages for API context")

        if (conversationMessages.isEmpty()) {
            val dbMessages = withContext(Dispatchers.IO) {
                viewModel.getAllConversationMessages(conversationId)
            }

            if (dbMessages.isNotEmpty()) {
                Timber.d("Using ${dbMessages.size} messages from emergency database reload")
                return dbMessages
            }

            Timber.e("CRITICAL: No messages found for conversation $conversationId even after emergency reload")
        }

        return conversationMessages
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun addMessageWithValidation(message: ChatMessage, addToAdapter: Boolean = true): Any {
        if (message.conversationId.isBlank()) {
            Timber.e("Cannot add message with blank conversation ID: ${message.id}")
            return message
        }

        val existingMessage = internalMessageList.find { it.id == message.id }
        if (existingMessage != null) {
            Timber.d("Message ${message.id} already exists, updating instead of adding")
            return updateMessageContent(message.id, message.message)
        }

        internalMessageList.add(message)
        internalMessageList.sortBy { it.timestamp }

        if (addToAdapter) {
            val currentList = adapter.currentList.toMutableList()

            if (!currentList.any { it.id == message.id }) {
                currentList.add(message)
                currentList.sortBy { it.timestamp }
                adapter.submitList(currentList)

                val newPosition = currentList.indexOfFirst { it.id == message.id }
                try {
                    adapter.notifyItemInserted(newPosition)
                } catch (e: Exception) {
                    Timber.e(e, "Error notifying item inserted: ${e.message}")
                }

                Timber.d("✅ Added message ${message.id} to adapter at position $newPosition")
            } else {
                Timber.w("⚠️ Message ${message.id} already exists in adapter, skipping duplicate")
            }
        }

        if (!message.conversationId.startsWith("private_")) {
            lifecycleScope.launch {
                viewModel.saveMessage(message)
            }
        }

        tokenManager.addMessage(message)
        return message
    }

    fun getConversationStatsWithValidation(conversationId: String): Triple<Int, Int, Boolean> {
        val messages = internalMessageList.filter { it.conversationId == conversationId }
        val userMessages = messages.count { it.isUser }
        val aiMessages = messages.count { !it.isUser && !it.isGenerating && it.message.isNotBlank() }
        val hasContext = messages.size >= 2

        Timber.d("Conversation $conversationId stats: ${messages.size} total, $userMessages user, $aiMessages AI, hasContext=$hasContext")
        return Triple(userMessages, aiMessages, hasContext)
    }

    fun addMessage(message: ChatMessage, addToAdapter: Boolean = true): ChatMessage {
        internalMessageList.add(message)

        if (addToAdapter) {
            val currentList = adapter.currentList.toMutableList()

            if (!currentList.any { it.id == message.id }) {
                currentList.add(message)

                adapter.submitList(currentList) {
                    Timber.d("✅ Message ${message.id} added to adapter, list size: ${adapter.currentList.size}")
                    recyclerViewRef?.post {
                        recyclerViewRef?.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            } else {
                Timber.w("⚠️ Message ${message.id} already exists in adapter, skipping duplicate")
            }
        }

        if (!message.conversationId.startsWith("private_")) {
            lifecycleScope.launch {
                viewModel.saveMessage(message)
            }
        }

        tokenManager.addMessage(message)
        return message
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun updateMessageContent(messageId: String, content: String) {
        val internalIndex = internalMessageList.indexOfFirst { it.id == messageId }
        if (internalIndex != -1) {
            val oldMessage = internalMessageList[internalIndex]

            // Allow empty content during streaming
            if (oldMessage.isGenerating || content.isNotEmpty()) {
                tokenManager.removeMessage(oldMessage.id)

                val updatedMessage = oldMessage.copy(
                    message = content,
                    isGenerating = oldMessage.isGenerating, // Keep generating state during streaming
                    showButtons = !oldMessage.isGenerating  // Only show buttons when not generating
                )

                internalMessageList[internalIndex] = updatedMessage
                tokenManager.addMessage(updatedMessage)

                lifecycleScope.launch(Dispatchers.Main) {
                    val adapterIndex = chatAdapter.currentList.indexOfFirst { it.id == messageId }

                    if (adapterIndex != -1) {
                        chatAdapter.updateStreamingContent(messageId, content)
                    } else {
                        val currentList = chatAdapter.currentList.toMutableList()
                        currentList.add(updatedMessage)
                        chatAdapter.submitList(currentList)
                    }

                    if (!updatedMessage.conversationId.startsWith("private_")) {
                        viewModel.saveMessage(updatedMessage)
                    }
                }
            } else {
                Timber.w("⚠️ Attempted to clear content for non-generating message: $messageId")
            }
        } else {
            Timber.e("❌ Could not find message with ID $messageId to update")
        }
    }

    fun getMessageById(messageId: String): ChatMessage? {
        val internalMessage = internalMessageList.find { it.id == messageId }
        if (internalMessage != null) {
            return internalMessage
        }
        return adapter.currentList.find { it.id == messageId }
    }

    fun isLatestAiMessage(messageId: String): Boolean {
        val messages = adapter.currentList
        val aiMessages = messages.filter { !it.isUser }
        val latestAiMessage = aiMessages.maxByOrNull { it.timestamp }
        return latestAiMessage?.id == messageId
    }

    fun regenerateAiResponse(message: ChatMessage): ChatMessage {
        val conversationId = message.conversationId
        tokenManager.removeMessage(message.id)

        val regeneratedMessage = message.copy(
            id = UUID.randomUUID().toString(),
            message = "",
            timestamp = System.currentTimeMillis(),
            parentMessageId = message.parentMessageId,
            isGenerating = true,
            isRegenerated = true,
        )

        val currentList = adapter.currentList.toMutableList()

        val updatedList = currentList.map {
            if (it.id == message.id) {
                it.copy(isGenerating = false, showButtons = true)
            } else {
                it
            }
        }.toMutableList()

        updatedList.add(regeneratedMessage)
        adapter.submitList(updatedList)
        internalMessageList.add(regeneratedMessage)

        try {
            adapter.notifyItemInserted(updatedList.size - 1)
        } catch (e: Exception) {
            Timber.e(e, "Error notifying regenerated message insertion: ${e.message}")
        }

        if (!conversationId.startsWith("private_")) {
            lifecycleScope.launch {
                viewModel.saveMessage(regeneratedMessage)
                val originalMessage = currentList.find { it.id == message.id }
                if (originalMessage != null) {
                    viewModel.saveMessage(originalMessage)
                }
            }
        }

        Timber.d("✅ Created regenerated message ${regeneratedMessage.id} alongside ${message.id}")
        return regeneratedMessage
    }

    fun editUserMessage(originalMessage: ChatMessage, newContent: String): ChatMessage {
        if (!originalMessage.isUser) {
            Timber.e("❌ Cannot edit AI message")
            return originalMessage
        }

        val conversationId = originalMessage.conversationId
        tokenManager.removeMessage(originalMessage.id)

        val editedMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            message = newContent,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            isEdited = true,
        )

        val currentList = chatAdapter.currentList.toMutableList()
        currentList.add(editedMessage)
        adapter.submitList(currentList)
        internalMessageList.add(editedMessage)

        try {
            adapter.notifyItemInserted(currentList.size - 1)
        } catch (e: Exception) {
            Timber.e(e, "Error notifying edited message insertion: ${e.message}")
        }

        tokenManager.addMessage(editedMessage)

        if (!conversationId.startsWith("private_")) {
            lifecycleScope.launch {
                viewModel.saveMessage(editedMessage)
            }
        }

        Timber.d("✅ Created edited message ${editedMessage.id} to replace ${originalMessage.id}")
        return editedMessage
    }

    fun preloadMessagesForContext(conversationId: String) {
        val existingMessages = internalMessageList.filter { it.conversationId == conversationId }
        Timber.d("Preloading context: currently have ${existingMessages.size} messages for conversation $conversationId")

        if (existingMessages.size < 2) {
            Timber.d("⚠️ Very few messages in context, attempting to load from database...")

            lifecycleScope.launch {
                try {
                    val dbMessages = viewModel.getAllConversationMessages(conversationId)
                    val existingIds = internalMessageList.map { it.id }.toSet()
                    val newMessages = dbMessages.filter { it.id !in existingIds }

                    if (newMessages.isNotEmpty()) {
                        internalMessageList.addAll(newMessages)
                        Timber.d("✅ Added ${newMessages.size} messages from database to context")
                        synchronizeWithAdapter(internalMessageList)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error preloading messages from database: ${e.message}")
                }
            }
        }
    }

    fun getCurrentMessages(): List<ChatMessage> {
        Timber.d("getCurrentMessages() returning ${internalMessageList.size} messages from internal list")
        return internalMessageList.toList()
    }

    fun ensureFullSynchronizationForApi(): List<ChatMessage> {
        synchronizeWithAdapter(internalMessageList)
        Timber.d("Providing ${internalMessageList.size} messages for API context")
        return internalMessageList.toList()
    }

    fun fixLoadingStates() {
        val currentList = adapter.currentList.toMutableList()
        var hasChanged = false

        val latestAiMessage = currentList.filter { !it.isUser }.maxByOrNull { it.timestamp }

        for (i in currentList.indices) {
            val message = currentList[i]
            if (!message.isUser && message.id != latestAiMessage?.id && message.isGenerating) {
                currentList[i] = message.copy(
                    isGenerating = false,
                    showButtons = true
                )
                hasChanged = true
            }
        }

        if (hasChanged) {
            adapter.submitList(currentList)

            for (i in internalMessageList.indices) {
                val message = internalMessageList[i]
                if (!message.isUser && message.id != latestAiMessage?.id && message.isGenerating) {
                    internalMessageList[i] = message.copy(
                        isGenerating = false,
                        showButtons = true
                    )
                }
            }

            Timber.d("✅ Fixed loading states for messages")
        }
    }

    fun saveAllMessages() {
        val messages = adapter.currentList
        for (message in messages) {
            if (!message.conversationId.startsWith("private_")) {
                lifecycleScope.launch {
                    viewModel.saveMessage(message)
                }
            }
        }
    }

    fun addMessages(messages: List<ChatMessage>) {
        val currentList = adapter.currentList.toMutableList()
        val existingIds = currentList.map { it.id }.toSet()
        val newMessages = messages.filter { it.id !in existingIds }

        if (newMessages.isNotEmpty()) {
            newMessages.forEach { message ->
                tokenManager.addMessage(message)
            }

            internalMessageList.addAll(0, newMessages)
            currentList.addAll(0, newMessages)
            adapter.submitList(currentList)

            try {
                adapter.notifyItemRangeInserted(0, newMessages.size)
            } catch (e: Exception) {
                Timber.e(e, "Error notifying range inserted: ${e.message}")
            }

            Timber.d("✅ Added ${newMessages.size} more messages")
        }
    }

    fun startStreamingMode() {
        isStreamingActive = true
        lifecycleScope.launch(Dispatchers.Main) {
            chatAdapter.startStreamingMode()
        }
    }

    fun stopStreamingMode() {
        isStreamingActive = false
        lifecycleScope.launch(Dispatchers.Main) {
            chatAdapter.stopStreamingMode()
        }
    }
}