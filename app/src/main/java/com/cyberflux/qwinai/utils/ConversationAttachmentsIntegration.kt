package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.adapter.ConversationAdapter
import com.cyberflux.qwinai.dao.ChatMessageDao
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.model.Conversation

/**
 * Integration helper for adding conversation attachments to fragments
 * 
 * Usage example in your Fragment:
 * 
 * 1. In your Fragment setup:
 * ```kotlin
 * class YourConversationsFragment : Fragment() {
 *     private lateinit var attachmentsManager: ConversationAttachmentsManager
 *     private lateinit var conversationAdapter: ConversationAdapter
 *     
 *     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *         super.onViewCreated(view, savedInstanceState)
 *         
 *         // Initialize attachments manager
 *         val database = AppDatabase.getDatabase(requireContext())
 *         attachmentsManager = ConversationAttachmentsManager(
 *             context = requireContext(),
 *             chatMessageDao = database.chatMessageDao()
 *         )
 *         
 *         // Setup adapter with attachments manager
 *         conversationAdapter = ConversationAdapter(
 *             onConversationClick = { conversation -> 
 *                 // Handle conversation click
 *             },
 *             onConversationLongClick = { view, conversation ->
 *                 // Handle long click
 *             },
 *             attachmentsManager = attachmentsManager
 *         )
 *         
 *         // Set adapter to RecyclerView
 *         recyclerView.adapter = conversationAdapter
 *     }
 *     
 *     override fun onDestroyView() {
 *         super.onDestroyView()
 *         conversationAdapter.cleanup()
 *     }
 * }
 * ```
 * 
 * 2. The attachments view will automatically:
 *    - Show images and files from each conversation
 *    - Display images first, then documents
 *    - Allow clicking to open files/images
 *    - Handle file type icons and formatting
 */
object ConversationAttachmentsIntegration {
    
    /**
     * Create a ConversationAttachmentsManager instance
     */
    fun createAttachmentsManager(context: Context): ConversationAttachmentsManager {
        val database = AppDatabase.getDatabase(context)
        return ConversationAttachmentsManager(
            context = context,
            chatMessageDao = database.chatMessageDao()
        )
    }
    
    /**
     * Create a ConversationAdapter with attachments support
     */
    fun createConversationAdapter(
        context: Context,
        onConversationClick: (Conversation) -> Unit,
        onConversationLongClick: (android.view.View, Conversation) -> Unit
    ): ConversationAdapter {
        val attachmentsManager = createAttachmentsManager(context)
        
        return ConversationAdapter(
            onConversationClick = onConversationClick,
            onConversationLongClick = onConversationLongClick,
            attachmentsManager = attachmentsManager
        )
    }
}