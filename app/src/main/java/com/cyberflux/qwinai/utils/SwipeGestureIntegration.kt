package com.cyberflux.qwinai.utils

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.model.ChatMessage
import timber.log.Timber

/**
 * Integration helper for swipe gestures in chat interfaces
 * Provides complete integration patterns and examples
 */
class SwipeGestureIntegration {
    
    companion object {
        
        /**
         * Setup complete swipe gesture system for MainActivity
         */
        fun setupForMainActivity(
            context: Context,
            recyclerView: RecyclerView,
            chatAdapter: ChatAdapter,
            onMessageAction: MessageActionHandler
        ): SwipeGestureHelper {
            
            val swipeHelper = SwipeGestureHelper(context, chatAdapter)
            
            // Setup swipe action handler
            swipeHelper.setSwipeActionHandler(object : SwipeGestureHelper.SwipeActionHandler {
                override fun onCopyMessage(message: ChatMessage) {
                    onMessageAction.copyMessage(message)
                }
                
                override fun onDeleteMessage(message: ChatMessage, position: Int) {
                    onMessageAction.deleteMessage(message, position)
                }
                
                override fun onEditMessage(message: ChatMessage, position: Int) {
                    onMessageAction.editMessage(message, position)
                }
                
                override fun onRegenerateMessage(message: ChatMessage, position: Int) {
                    onMessageAction.regenerateMessage(message, position)
                }
                
                override fun onReplyToMessage(message: ChatMessage) {
                    onMessageAction.replyToMessage(message)
                }
                
                override fun onSaveMessage(message: ChatMessage) {
                    onMessageAction.saveMessage(message)
                }
                
                override fun onContinueMessage(message: ChatMessage) {
                    onMessageAction.continueMessage(message)
                }
                
                override fun onArchiveMessage(message: ChatMessage, position: Int) {
                    onMessageAction.archiveMessage(message, position)
                }
                
                override fun onRateMessage(message: ChatMessage, rating: Int) {
                    onMessageAction.rateMessage(message, rating)
                }
            })
            
            // Setup swipe gestures with default configuration
            swipeHelper.setupSwipeGestures(recyclerView)
            
            return swipeHelper
        }
        
        /**
         * Setup swipe gestures with custom configuration
         */
        fun setupWithCustomConfig(
            context: Context,
            recyclerView: RecyclerView,
            chatAdapter: ChatAdapter,
            configuration: SwipeGestureHelper.SwipeConfiguration,
            onMessageAction: MessageActionHandler
        ): SwipeGestureHelper {
            
            val swipeHelper = SwipeGestureHelper(context, chatAdapter)
            
            swipeHelper.setSwipeActionHandler(object : SwipeGestureHelper.SwipeActionHandler {
                override fun onCopyMessage(message: ChatMessage) {
                    onMessageAction.copyMessage(message)
                }
                
                override fun onDeleteMessage(message: ChatMessage, position: Int) {
                    onMessageAction.deleteMessage(message, position)
                }
                
                override fun onEditMessage(message: ChatMessage, position: Int) {
                    onMessageAction.editMessage(message, position)
                }
                
                override fun onRegenerateMessage(message: ChatMessage, position: Int) {
                    onMessageAction.regenerateMessage(message, position)
                }
                
                override fun onReplyToMessage(message: ChatMessage) {
                    onMessageAction.replyToMessage(message)
                }
                
                override fun onSaveMessage(message: ChatMessage) {
                    onMessageAction.saveMessage(message)
                }
                
                override fun onContinueMessage(message: ChatMessage) {
                    onMessageAction.continueMessage(message)
                }
                
                override fun onArchiveMessage(message: ChatMessage, position: Int) {
                    onMessageAction.archiveMessage(message, position)
                }
                
                override fun onRateMessage(message: ChatMessage, rating: Int) {
                    onMessageAction.rateMessage(message, rating)
                }
            })
            
            swipeHelper.setupSwipeGestures(recyclerView, customConfiguration = configuration)
            
            return swipeHelper
        }
        
        /**
         * Setup minimal swipe gestures (copy only)
         */
        fun setupMinimal(
            context: Context,
            recyclerView: RecyclerView,
            chatAdapter: ChatAdapter,
            onCopyMessage: (ChatMessage) -> Unit
        ): SwipeGestureHelper {
            
            val swipeHelper = SwipeGestureHelper(context, chatAdapter)
            
            swipeHelper.setSwipeActionHandler(object : SwipeGestureHelper.SwipeActionHandler {
                override fun onCopyMessage(message: ChatMessage) {
                    onCopyMessage(message)
                }
                
                // Other actions not implemented in minimal setup
                override fun onDeleteMessage(message: ChatMessage, position: Int) {}
                override fun onEditMessage(message: ChatMessage, position: Int) {}
                override fun onRegenerateMessage(message: ChatMessage, position: Int) {}
                override fun onReplyToMessage(message: ChatMessage) {}
                override fun onSaveMessage(message: ChatMessage) {}
                override fun onContinueMessage(message: ChatMessage) {}
                override fun onArchiveMessage(message: ChatMessage, position: Int) {}
                override fun onRateMessage(message: ChatMessage, rating: Int) {}
            })
            
            val config = SwipeGestureHelper.Configurations.copyOnly(context)
            swipeHelper.setupSwipeGestures(recyclerView, customConfiguration = config)
            
            return swipeHelper
        }
    }
    
    /**
     * Interface for handling message actions
     */
    interface MessageActionHandler {
        fun copyMessage(message: ChatMessage)
        fun deleteMessage(message: ChatMessage, position: Int)
        fun editMessage(message: ChatMessage, position: Int)
        fun regenerateMessage(message: ChatMessage, position: Int)
        fun replyToMessage(message: ChatMessage)
        fun saveMessage(message: ChatMessage)
        fun continueMessage(message: ChatMessage)
        fun archiveMessage(message: ChatMessage, position: Int)
        fun rateMessage(message: ChatMessage, rating: Int)
    }
    
    /**
     * Usage examples and integration patterns
     */
    object Examples {
        
        /**
         * Example: MainActivity integration
         */
        fun exampleMainActivityIntegration() {
            /*
            class MainActivity : AppCompatActivity() {
                
                private lateinit var chatAdapter: ChatAdapter
                private lateinit var recyclerView: RecyclerView
                private lateinit var swipeGestureHelper: SwipeGestureHelper
                
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                    
                    setupRecyclerView()
                    setupSwipeGestures()
                }
                
                private fun setupRecyclerView() {
                    recyclerView = findViewById(R.id.messagesRecyclerView)
                    chatAdapter = ChatAdapter(this) { message ->
                        // Handle message click
                    }
                    recyclerView.adapter = chatAdapter
                }
                
                private fun setupSwipeGestures() {
                    swipeGestureHelper = SwipeGestureIntegration.setupForMainActivity(
                        context = this,
                        recyclerView = recyclerView,
                        chatAdapter = chatAdapter,
                        onMessageAction = object : SwipeGestureIntegration.MessageActionHandler {
                            override fun copyMessage(message: ChatMessage) {
                                // Copy message to clipboard
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Message", message.message)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@MainActivity, "Message copied", Toast.LENGTH_SHORT).show()
                            }
                            
                            override fun deleteMessage(message: ChatMessage, position: Int) {
                                // Show confirmation dialog and delete
                                showDeleteConfirmation(message, position)
                            }
                            
                            override fun editMessage(message: ChatMessage, position: Int) {
                                // Show edit dialog or navigate to edit mode
                                showEditDialog(message)
                            }
                            
                            override fun regenerateMessage(message: ChatMessage, position: Int) {
                                // Regenerate AI response
                                regenerateAiResponse(message)
                            }
                            
                            override fun replyToMessage(message: ChatMessage) {
                                // Set up reply context
                                setupReplyMode(message)
                            }
                            
                            override fun saveMessage(message: ChatMessage) {
                                // Save message to favorites or bookmarks
                                saveToFavorites(message)
                            }
                            
                            override fun continueMessage(message: ChatMessage) {
                                // Continue AI response
                                continueAiResponse(message)
                            }
                            
                            override fun archiveMessage(message: ChatMessage, position: Int) {
                                // Archive message
                                archiveMessage(message)
                            }
                            
                            override fun rateMessage(message: ChatMessage, rating: Int) {
                                // Rate AI response
                                rateAiResponse(message, rating)
                            }
                        }
                    )
                }
                
                private fun showDeleteConfirmation(message: ChatMessage, position: Int) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Message")
                        .setMessage("Are you sure you want to delete this message?")
                        .setPositiveButton("Delete") { _, _ ->
                            deleteMessageFromAdapter(position)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                
                private fun deleteMessageFromAdapter(position: Int) {
                    val currentList = chatAdapter.currentList.toMutableList()
                    if (position < currentList.size) {
                        currentList.removeAt(position)
                        chatAdapter.submitList(currentList)
                    }
                }
                
                override fun onDestroy() {
                    super.onDestroy()
                    swipeGestureHelper.cleanup()
                }
            }
            */
        }
        
        /**
         * Example: Custom swipe configuration
         */
        fun exampleCustomConfiguration() {
            /*
            // Create custom swipe configuration
            val customConfig = SwipeGestureHelper.SwipeConfigurationBuilder(context)
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "copy",
                        label = "Copy",
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)!!,
                        backgroundColor = ContextCompat.getColor(context, R.color.info_color)
                    )
                )
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "share",
                        label = "Share",
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_share)!!,
                        backgroundColor = ContextCompat.getColor(context, R.color.success_color)
                    )
                )
                .addLeftAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "star",
                        label = "Star",
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_star)!!,
                        backgroundColor = ContextCompat.getColor(context, R.color.accent_gold)
                    )
                )
                .setCanSwipeFunction { message, direction ->
                    // Only allow swipes on non-system messages
                    !message.isSystem && !message.isGenerating
                }
                .build()
                
            // Setup with custom configuration
            val swipeHelper = SwipeGestureIntegration.setupWithCustomConfig(
                context = context,
                recyclerView = recyclerView,
                chatAdapter = chatAdapter,
                configuration = customConfig,
                onMessageAction = messageActionHandler
            )
            */
        }
        
        /**
         * Example: Fragment integration
         */
        fun exampleFragmentIntegration() {
            /*
            class ChatFragment : Fragment() {
                
                private var swipeGestureHelper: SwipeGestureHelper? = null
                
                override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                    super.onViewCreated(view, savedInstanceState)
                    
                    val recyclerView = view.findViewById<RecyclerView>(R.id.messagesRecyclerView)
                    val chatAdapter = ChatAdapter(requireContext()) { message ->
                        // Handle message click
                    }
                    
                    recyclerView.adapter = chatAdapter
                    
                    // Setup minimal swipe gestures (copy only)
                    swipeGestureHelper = SwipeGestureIntegration.setupMinimal(
                        context = requireContext(),
                        recyclerView = recyclerView,
                        chatAdapter = chatAdapter,
                        onCopyMessage = { message ->
                            copyMessageToClipboard(message)
                        }
                    )
                }
                
                private fun copyMessageToClipboard(message: ChatMessage) {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Message", message.message)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Message copied", Toast.LENGTH_SHORT).show()
                }
                
                override fun onDestroyView() {
                    super.onDestroyView()
                    swipeGestureHelper?.cleanup()
                }
            }
            */
        }
        
        /**
         * Example: Conditional swipe actions based on message type
         */
        fun exampleConditionalSwipes() {
            /*
            val swipeConfig = SwipeGestureHelper.SwipeConfigurationBuilder(context)
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "copy",
                        label = "Copy",
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)!!,
                        backgroundColor = ContextCompat.getColor(context, R.color.info_color)
                    )
                )
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "regenerate",
                        label = "Retry",
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_refresh)!!,
                        backgroundColor = ContextCompat.getColor(context, R.color.colorPrimary)
                    )
                )
                .addLeftAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "delete",
                        label = "Delete",
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_delete)!!,
                        backgroundColor = ContextCompat.getColor(context, R.color.error_color),
                        isDestructive = true
                    )
                )
                .setCanSwipeFunction { message, direction ->
                    when {
                        // No swipes on loading/generating messages
                        message.isGenerating || message.isLoading -> false
                        
                        // Right swipes (copy, regenerate) - allow on all messages
                        direction == ItemTouchHelper.RIGHT -> true
                        
                        // Left swipes (delete) - only allow on user messages or completed AI messages
                        direction == ItemTouchHelper.LEFT -> {
                            message.isUser || (!message.isGenerating && !message.isLoading)
                        }
                        
                        else -> false
                    }
                }
                .build()
            */
        }
    }
}