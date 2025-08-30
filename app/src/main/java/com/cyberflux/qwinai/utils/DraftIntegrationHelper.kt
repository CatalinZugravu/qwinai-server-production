package com.cyberflux.qwinai.utils

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.dao.ConversationDao
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/**
 * Integration helper for message draft functionality
 * Provides easy integration patterns for activities and fragments
 */
class DraftIntegrationHelper {
    
    companion object {
        
        /**
         * Initialize complete draft system for an activity or fragment
         */
        fun initializeDraftSystem(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            conversationDao: ConversationDao,
            conversationId: Long,
            messageInputContainer: LinearLayout,
            messageInput: EditText,
            onDraftRestored: ((String, List<MessageDraftManager.DraftFile>) -> Unit)? = null,
            onDraftError: ((String) -> Unit)? = null
        ): DraftSystemComponents {
            
            val coroutineScope = lifecycleOwner.lifecycleScope
            
            // Initialize draft manager
            val draftManager = MessageDraftManager(context, conversationDao, coroutineScope)
            draftManager.initialize()
            
            // Initialize draft UI manager
            val draftUIManager = DraftUIManager(context, draftManager, coroutineScope)
            draftUIManager.initialize()
            
            // Create and add draft indicator to message input container
            val draftIndicator = draftUIManager.createDraftIndicatorView()
            messageInputContainer.addView(draftIndicator, 0) // Add at top
            
            // Setup UI callback
            draftUIManager.setDraftUICallback(object : DraftUIManager.DraftUICallback {
                override fun onDraftRestored(text: String, files: List<MessageDraftManager.DraftFile>) {
                    onDraftRestored?.invoke(text, files)
                }
                
                override fun onDraftDismissed() {
                    Timber.d("Draft dismissed for conversation $conversationId")
                }
                
                override fun onDraftSaved() {
                    Timber.v("Draft saved for conversation $conversationId")
                }
                
                override fun onDraftError(error: String) {
                    Timber.e("Draft error for conversation $conversationId: $error")
                    onDraftError?.invoke(error)
                }
            })
            
            // Setup draft UI components
            draftUIManager.setupDraftUI(
                conversationId = conversationId,
                draftIndicatorView = draftIndicator,
                messageInputView = messageInput,
                draftTextView = null,
                draftTimestampView = null,
                restoreButtonView = null,
                dismissButtonView = null
            )
            
            return DraftSystemComponents(
                draftManager = draftManager,
                draftUIManager = draftUIManager,
                draftIndicator = draftIndicator
            )
        }
        
        /**
         * Simple draft system for basic auto-save functionality
         */
        fun initializeBasicDraftSystem(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            conversationDao: ConversationDao,
            conversationId: Long,
            messageInput: EditText
        ): MessageDraftManager {
            
            val coroutineScope = lifecycleOwner.lifecycleScope
            
            val draftManager = MessageDraftManager(context, conversationDao, coroutineScope)
            draftManager.initialize()
            
            // Setup simple text watcher for auto-save
            messageInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString()?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        draftManager.updateDraftText(conversationId, text)
                    }
                }
            })
            
            return draftManager
        }
        
        /**
         * MainActivity integration example
         */
        fun integrateWithMainActivity(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            conversationDao: ConversationDao
        ): MainActivityDraftIntegration {
            
            return MainActivityDraftIntegration(context, lifecycleOwner, conversationDao)
        }
    }
    
    /**
     * Container for draft system components
     */
    data class DraftSystemComponents(
        val draftManager: MessageDraftManager,
        val draftUIManager: DraftUIManager,
        val draftIndicator: android.view.View
    ) {
        fun cleanup() {
            draftUIManager.cleanup()
            draftManager.cleanup()
        }
    }
    
    /**
     * MainActivity integration wrapper
     */
    class MainActivityDraftIntegration(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner,
        private val conversationDao: ConversationDao
    ) {
        
        private var draftSystemComponents: DraftSystemComponents? = null
        private val coroutineScope = lifecycleOwner.lifecycleScope
        
        /**
         * Setup draft system for current conversation
         */
        fun setupForConversation(
            conversationId: Long,
            messageInputContainer: LinearLayout,
            messageInput: EditText,
            onDraftRestored: ((String, List<MessageDraftManager.DraftFile>) -> Unit)? = null
        ) {
            // Cleanup previous draft system if exists
            draftSystemComponents?.cleanup()
            
            // Initialize new draft system
            draftSystemComponents = initializeDraftSystem(
                context = context,
                lifecycleOwner = lifecycleOwner,
                conversationDao = conversationDao,
                conversationId = conversationId,
                messageInputContainer = messageInputContainer,
                messageInput = messageInput,
                onDraftRestored = onDraftRestored,
                onDraftError = { error ->
                    // Handle draft errors - could show toast or snackbar
                    Timber.e("Draft error: $error")
                }
            )
        }
        
        /**
         * Handle message sent - clear draft
         */
        fun onMessageSent() {
            draftSystemComponents?.draftUIManager?.clearCurrentDraft()
        }
        
        /**
         * Handle file attachment added
         */
        fun onFileAttached(uri: String, name: String, type: String, size: Long) {
            val file = MessageDraftManager.DraftFile(
                uri = uri,
                name = name,
                type = type,
                size = size
            )
            draftSystemComponents?.draftUIManager?.addFileToDraft(file)
        }
        
        /**
         * Handle file attachment removed
         */
        fun onFileRemoved(uri: String) {
            draftSystemComponents?.draftUIManager?.removeFileFromDraft(uri)
        }
        
        /**
         * Save current draft manually
         */
        fun saveCurrentDraft() {
            draftSystemComponents?.draftUIManager?.saveCurrentDraft()
        }
        
        /**
         * Get draft statistics for debugging
         */
        fun getDraftStatistics(): Map<String, Any>? {
            return draftSystemComponents?.draftManager?.getDraftStatistics()
        }
        
        /**
         * Cleanup resources
         */
        fun cleanup() {
            draftSystemComponents?.cleanup()
            draftSystemComponents = null
        }
    }
    
    /**
     * Extension functions for easier integration
     */
    object Extensions {
        
        /**
         * Extension function for EditText to enable draft auto-save
         */
        fun EditText.enableDraftAutoSave(
            draftManager: MessageDraftManager,
            conversationId: Long
        ) {
            this.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString()?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        draftManager.updateDraftText(conversationId, text)
                    }
                }
            })
        }
        
        /**
         * Extension function to check if conversation has draft
         */
        suspend fun ConversationDao.hasActiveDraft(conversationId: Long): Boolean {
            return try {
                val conversation = getConversationById(conversationId)
                conversation.hasDraft && 
                (conversation.draftText.isNotEmpty() || conversation.draftFiles.isNotEmpty())
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Extension function to get draft preview
         */
        suspend fun ConversationDao.getDraftPreview(conversationId: Long, maxLength: Int = 50): String? {
            return try {
                val conversation = getConversationById(conversationId)
                if (conversation.hasDraft && conversation.draftText.isNotEmpty()) {
                    if (conversation.draftText.length > maxLength) {
                        "${conversation.draftText.take(maxLength)}..."
                    } else {
                        conversation.draftText
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Usage examples and patterns
     */
    object UsageExamples {
        
        /**
         * Example: MainActivity onCreate
         */
        fun exampleMainActivitySetup() {
            /*
            class MainActivity : AppCompatActivity() {
                
                private lateinit var draftIntegration: DraftIntegrationHelper.MainActivityDraftIntegration
                private lateinit var messageInput: EditText
                private lateinit var messageInputContainer: LinearLayout
                
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                    
                    // Initialize views
                    messageInput = findViewById(R.id.messageInput)
                    messageInputContainer = findViewById(R.id.messageInputContainer)
                    
                    // Initialize draft integration
                    draftIntegration = DraftIntegrationHelper.integrateWithMainActivity(
                        context = this,
                        lifecycleOwner = this,
                        conversationDao = database.conversationDao()
                    )
                }
                
                private fun switchToConversation(conversationId: Long) {
                    // Setup draft system for new conversation
                    draftIntegration.setupForConversation(
                        conversationId = conversationId,
                        messageInputContainer = messageInputContainer,
                        messageInput = messageInput,
                        onDraftRestored = { text, files ->
                            // Handle restored draft
                            messageInput.setText(text)
                            // Handle attached files...
                        }
                    )
                }
                
                private fun sendMessage() {
                    // Send message logic...
                    
                    // Clear draft after sending
                    draftIntegration.onMessageSent()
                }
                
                override fun onDestroy() {
                    super.onDestroy()
                    draftIntegration.cleanup()
                }
            }
            */
        }
        
        /**
         * Example: Fragment setup
         */
        fun exampleFragmentSetup() {
            /*
            class ChatFragment : Fragment() {
                
                private var draftSystemComponents: DraftIntegrationHelper.DraftSystemComponents? = null
                
                override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                    super.onViewCreated(view, savedInstanceState)
                    
                    val messageInput = view.findViewById<EditText>(R.id.messageInput)
                    val messageContainer = view.findViewById<LinearLayout>(R.id.messageContainer)
                    val conversationId = arguments?.getLong("conversationId") ?: 0L
                    
                    // Setup draft system
                    draftSystemComponents = DraftIntegrationHelper.initializeDraftSystem(
                        context = requireContext(),
                        lifecycleOwner = viewLifecycleOwner,
                        conversationDao = database.conversationDao(),
                        conversationId = conversationId,
                        messageInputContainer = messageContainer,
                        messageInput = messageInput
                    )
                }
                
                override fun onDestroyView() {
                    super.onDestroyView()
                    draftSystemComponents?.cleanup()
                }
            }
            */
        }
    }
}