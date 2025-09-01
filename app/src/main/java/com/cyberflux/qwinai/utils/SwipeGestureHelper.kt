package com.cyberflux.qwinai.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.model.ChatMessage
import timber.log.Timber

/**
 * Helper class for integrating swipe gestures with chat adapters
 * Provides easy setup and common action implementations
 */
class SwipeGestureHelper(
    private val context: Context,
    private val chatAdapter: ChatAdapter
) {
    
    private var itemTouchHelper: ItemTouchHelper? = null
    private var swipeActionHandler: SwipeActionHandler? = null
    
    /**
     * Callback interface for swipe actions
     */
    interface SwipeActionHandler {
        fun onCopyMessage(message: ChatMessage)
        fun onDeleteMessage(message: ChatMessage, position: Int)
        fun onEditMessage(message: ChatMessage, position: Int)
        fun onRegenerateMessage(message: ChatMessage, position: Int)
        fun onReplyToMessage(message: ChatMessage)
        fun onSaveMessage(message: ChatMessage)
        fun onContinueMessage(message: ChatMessage)
        fun onArchiveMessage(message: ChatMessage, position: Int)
        fun onRateMessage(message: ChatMessage, rating: Int)
    }
    
    /**
     * Set swipe action handler
     */
    fun setSwipeActionHandler(handler: SwipeActionHandler) {
        this.swipeActionHandler = handler
    }
    
    /**
     * Setup swipe gestures for chat RecyclerView
     */
    fun setupSwipeGestures(
        recyclerView: RecyclerView,
        enableUserMessageSwipes: Boolean = true,
        enableAiMessageSwipes: Boolean = true,
        customConfiguration: SwipeConfiguration? = null
    ) {
        
        val swipeManager = if (customConfiguration != null) {
            createCustomSwipeManager(customConfiguration)
        } else {
            createDefaultSwipeManager(enableUserMessageSwipes, enableAiMessageSwipes)
        }
        
        itemTouchHelper = ItemTouchHelper(swipeManager)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
        
        Timber.d("Swipe gestures setup for RecyclerView")
    }
    
    /**
     * Create default swipe manager based on message types
     */
    private fun createDefaultSwipeManager(
        enableUserSwipes: Boolean,
        enableAiSwipes: Boolean
    ): MessageSwipeGestureManager {
        
        val callback = object : MessageSwipeGestureManager.SwipeActionCallback {
            override fun onSwipeAction(
                action: MessageSwipeGestureManager.SwipeAction,
                message: ChatMessage,
                position: Int
            ) {
                handleSwipeAction(action, message, position)
            }
            
            override fun canSwipeMessage(message: ChatMessage, direction: Int): Boolean {
                return when {
                    message.isUser && enableUserSwipes -> true
                    !message.isUser && enableAiSwipes -> true
                    else -> false
                }
            }
            
            override fun getMessageAtPosition(position: Int): ChatMessage? {
                return try {
                    if (position >= 0 && position < chatAdapter.currentList.size) {
                        chatAdapter.currentList[position]
                    } else null
                } catch (e: Exception) {
                    Timber.e(e, "Error getting message at position $position")
                    null
                }
            }
        }
        
        // Create appropriate swipe manager based on configuration
        return MessageSwipeGestureManager(context, callback)
    }
    
    /**
     * Create custom swipe manager with specific configuration
     */
    private fun createCustomSwipeManager(config: SwipeConfiguration): MessageSwipeGestureManager {
        val callback = object : MessageSwipeGestureManager.SwipeActionCallback {
            override fun onSwipeAction(
                action: MessageSwipeGestureManager.SwipeAction,
                message: ChatMessage,
                position: Int
            ) {
                handleSwipeAction(action, message, position)
            }
            
            override fun canSwipeMessage(message: ChatMessage, direction: Int): Boolean {
                return config.canSwipe(message, direction)
            }
            
            override fun getMessageAtPosition(position: Int): ChatMessage? {
                return try {
                    if (position >= 0 && position < chatAdapter.currentList.size) {
                        chatAdapter.currentList[position]
                    } else null
                } catch (e: Exception) {
                    Timber.e(e, "Error getting message at position $position")
                    null
                }
            }
        }
        
        val manager = MessageSwipeGestureManager(context, callback)
        
        // Clear default actions and add custom ones
        manager.clearActions()
        config.leftActions.forEach { manager.addLeftAction(it) }
        config.rightActions.forEach { manager.addRightAction(it) }
        
        return manager
    }
    
    /**
     * Handle swipe action execution
     */
    private fun handleSwipeAction(
        action: MessageSwipeGestureManager.SwipeAction,
        message: ChatMessage,
        position: Int
    ) {
        Timber.d("Executing swipe action: ${action.id} for message ${message.id}")
        
        when (action.id) {
            "copy" -> handleCopyAction(message)
            "delete" -> handleDeleteAction(message, position)
            "edit" -> handleEditAction(message, position)
            "regenerate" -> handleRegenerateAction(message, position)
            "reply" -> handleReplyAction(message)
            "save" -> handleSaveAction(message)
            "continue" -> handleContinueAction(message)
            "archive" -> handleArchiveAction(message, position)
            "rate_up" -> handleRateAction(message, 1)
            "rate_down" -> handleRateAction(message, -1)
            else -> {
                Timber.w("Unknown swipe action: ${action.id}")
                Toast.makeText(context, "Action not implemented: ${action.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Handle copy action with fallback implementation
     */
    private fun handleCopyAction(message: ChatMessage) {
        try {
            swipeActionHandler?.onCopyMessage(message) ?: run {
                // Fallback implementation
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Message", message.message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error copying message")
            Toast.makeText(context, "Failed to copy message", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle delete action
     */
    private fun handleDeleteAction(message: ChatMessage, position: Int) {
        swipeActionHandler?.onDeleteMessage(message, position) ?: run {
            Toast.makeText(context, "Delete not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle edit action
     */
    private fun handleEditAction(message: ChatMessage, position: Int) {
        swipeActionHandler?.onEditMessage(message, position) ?: run {
            Toast.makeText(context, "Edit not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle regenerate action
     */
    private fun handleRegenerateAction(message: ChatMessage, position: Int) {
        swipeActionHandler?.onRegenerateMessage(message, position) ?: run {
            Toast.makeText(context, "Regenerate not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle reply action
     */
    private fun handleReplyAction(message: ChatMessage) {
        swipeActionHandler?.onReplyToMessage(message) ?: run {
            Toast.makeText(context, "Reply not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle save action
     */
    private fun handleSaveAction(message: ChatMessage) {
        swipeActionHandler?.onSaveMessage(message) ?: run {
            Toast.makeText(context, "Save not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle continue action
     */
    private fun handleContinueAction(message: ChatMessage) {
        swipeActionHandler?.onContinueMessage(message) ?: run {
            Toast.makeText(context, "Continue not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle archive action
     */
    private fun handleArchiveAction(message: ChatMessage, position: Int) {
        swipeActionHandler?.onArchiveMessage(message, position) ?: run {
            Toast.makeText(context, "Archive not implemented", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Handle rate action
     */
    private fun handleRateAction(message: ChatMessage, rating: Int) {
        swipeActionHandler?.onRateMessage(message, rating) ?: run {
            val ratingText = if (rating > 0) "liked" else "disliked"
            Toast.makeText(context, "Message $ratingText", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Enable or disable swipe gestures
     */
    fun setSwipeEnabled(enabled: Boolean) {
        if (enabled) {
            // Re-attach if previously detached
            itemTouchHelper?.attachToRecyclerView(null)
        } else {
            // Detach to disable
            itemTouchHelper?.attachToRecyclerView(null)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        swipeActionHandler = null
    }
    
    /**
     * Configuration class for custom swipe setups
     */
    data class SwipeConfiguration(
        val leftActions: List<MessageSwipeGestureManager.SwipeAction>,
        val rightActions: List<MessageSwipeGestureManager.SwipeAction>,
        val canSwipe: (ChatMessage, Int) -> Boolean = { _, _ -> true }
    )
    
    /**
     * Builder for swipe configuration
     */
    class SwipeConfigurationBuilder(private val context: Context) {
        private val leftActions = mutableListOf<MessageSwipeGestureManager.SwipeAction>()
        private val rightActions = mutableListOf<MessageSwipeGestureManager.SwipeAction>()
        private var canSwipeFunction: (ChatMessage, Int) -> Boolean = { _, _ -> true }
        
        fun addLeftAction(action: MessageSwipeGestureManager.SwipeAction): SwipeConfigurationBuilder {
            leftActions.add(action)
            return this
        }
        
        fun addRightAction(action: MessageSwipeGestureManager.SwipeAction): SwipeConfigurationBuilder {
            rightActions.add(action)
            return this
        }
        
        fun setCanSwipeFunction(canSwipe: (ChatMessage, Int) -> Boolean): SwipeConfigurationBuilder {
            canSwipeFunction = canSwipe
            return this
        }
        
        fun build(): SwipeConfiguration {
            return SwipeConfiguration(leftActions, rightActions, canSwipeFunction)
        }
    }
    
    /**
     * Pre-built configurations for common use cases
     */
    object Configurations {
        
        /**
         * Copy-only configuration
         */
        fun copyOnly(context: Context): SwipeConfiguration {
            return SwipeConfigurationBuilder(context)
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "copy",
                        label = "Copy",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_copy)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.info_color)
                    )
                )
                .build()
        }
        
        /**
         * Basic configuration with copy and delete
         */
        fun basic(context: Context): SwipeConfiguration {
            return SwipeConfigurationBuilder(context)
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "copy",
                        label = "Copy",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_copy)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.info_color)
                    )
                )
                .addLeftAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "delete",
                        label = "Delete",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_delete)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.error_color),
                        isDestructive = true,
                        threshold = 0.6f
                    )
                )
                .build()
        }
        
        /**
         * Advanced configuration with multiple actions
         */
        fun advanced(context: Context): SwipeConfiguration {
            return SwipeConfigurationBuilder(context)
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "copy",
                        label = "Copy",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_copy)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.info_color)
                    )
                )
                .addRightAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "reply",
                        label = "Reply",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_reply)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.success_color)
                    )
                )
                .addLeftAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "save",
                        label = "Save",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_bookmark)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.accent_gold)
                    )
                )
                .addLeftAction(
                    MessageSwipeGestureManager.SwipeAction(
                        id = "delete",
                        label = "Delete",
                        icon = androidx.core.content.ContextCompat.getDrawable(context, com.cyberflux.qwinai.R.drawable.ic_delete)!!,
                        backgroundColor = androidx.core.content.ContextCompat.getColor(context, com.cyberflux.qwinai.R.color.error_color),
                        isDestructive = true,
                        threshold = 0.6f
                    )
                )
                .setCanSwipeFunction { message, direction ->
                    // Allow all swipes except for generating messages
                    !message.isGenerating && !message.isLoading
                }
                .build()
        }
    }
}