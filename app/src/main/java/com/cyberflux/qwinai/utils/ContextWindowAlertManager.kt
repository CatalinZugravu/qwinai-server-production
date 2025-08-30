package com.cyberflux.qwinai.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import timber.log.Timber

/**
 * Context Window Alert Manager
 * 
 * Purpose: Show user-friendly alerts for context window limits
 * - 70% Warning: Suggest starting new conversation
 * - 80% Blocking: Force new conversation 
 * - Clear explanations of why limits exist
 * - Smooth user experience
 */
class ContextWindowAlertManager(private val context: Context) {
    
    /**
     * Alert action callbacks
     */
    interface AlertCallbacks {
        fun onContinueAnyway()
        fun onStartNewConversation()
        fun onCancel()
        fun onUpgradeAccount() // For non-subscribers
    }
    
    /**
     * Show 70% warning dialog (user can still continue)
     */
    fun showContextWarningDialog(
        usagePercentage: Float,
        estimatedTokens: Int,
        modelName: String,
        isSubscribed: Boolean,
        callbacks: AlertCallbacks,
        hasAttachedFiles: Boolean = false
    ) {
        if (context !is Activity || context.isFinishing) {
            Timber.w("Cannot show context warning - activity not available")
            return
        }
        
        try {
            // Inflate custom dialog layout
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_context_warning, null)
            
            // Setup views
            val titleText = dialogView.findViewById<TextView>(R.id.tvTitle)
            val messageText = dialogView.findViewById<TextView>(R.id.tvMessage)
            val usageProgress = dialogView.findViewById<ProgressBar>(R.id.progressUsage)
            val usageText = dialogView.findViewById<TextView>(R.id.tvUsagePercentage)
            val btnContinue = dialogView.findViewById<Button>(R.id.btnContinueAnyway)
            val btnNewChat = dialogView.findViewById<Button>(R.id.btnStartNewChat)
            val btnUpgrade = dialogView.findViewById<Button>(R.id.btnUpgrade)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
            
            // Set content
            titleText.text = "Context Window Approaching Limit"
            messageText.text = buildWarningMessage(usagePercentage, modelName, isSubscribed, hasAttachedFiles)
            
            // Setup progress bar
            usageProgress.progress = (usagePercentage * 100).toInt()
            usageProgress.progressTintList = ContextCompat.getColorStateList(context, 
                if (usagePercentage > 0.75f) R.color.token_warning_color else R.color.token_info_color)
                
            usageText.text = "${(usagePercentage * 100).toInt()}% used"
            
            // Show/hide upgrade button
            btnUpgrade.visibility = if (isSubscribed) android.view.View.GONE else android.view.View.VISIBLE
            
            // Create dialog
            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create()
            
            // Setup button callbacks
            btnContinue.setOnClickListener {
                dialog.dismiss()
                callbacks.onContinueAnyway()
                Timber.d("User chose to continue despite warning")
            }
            
            btnNewChat.setOnClickListener {
                dialog.dismiss()
                callbacks.onStartNewConversation()
                Timber.d("User chose to start new conversation")
            }
            
            btnUpgrade.setOnClickListener {
                dialog.dismiss()
                callbacks.onUpgradeAccount()
                Timber.d("User chose to upgrade account")
            }
            
            btnCancel.setOnClickListener {
                dialog.dismiss()
                callbacks.onCancel()
                Timber.d("User cancelled context warning")
            }
            
            dialog.show()
            
        } catch (e: Exception) {
            Timber.e(e, "Error showing context warning dialog: ${e.message}")
            
            // Fallback to simple dialog
            showSimpleWarningDialog(usagePercentage, modelName, callbacks)
        }
    }
    
    /**
     * Show 80% blocking dialog (must start new conversation)
     */
    fun showContextBlockedDialog(
        usagePercentage: Float,
        estimatedTokens: Int,
        modelName: String,
        isSubscribed: Boolean,
        callbacks: AlertCallbacks,
        hasAttachedFiles: Boolean = false
    ) {
        if (context !is Activity || context.isFinishing) {
            Timber.w("Cannot show context blocked dialog - activity not available")
            return
        }
        
        try {
            // Inflate custom dialog layout
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_context_blocked, null)
            
            // Setup views
            val titleText = dialogView.findViewById<TextView>(R.id.tvTitle)
            val messageText = dialogView.findViewById<TextView>(R.id.tvMessage)
            val usageProgress = dialogView.findViewById<ProgressBar>(R.id.progressUsage)
            val usageText = dialogView.findViewById<TextView>(R.id.tvUsagePercentage)
            val btnNewChat = dialogView.findViewById<Button>(R.id.btnStartNewChat)
            val btnUpgrade = dialogView.findViewById<Button>(R.id.btnUpgrade)
            
            // Set content
            titleText.text = "Context Window Limit Reached"
            messageText.text = buildBlockedMessage(usagePercentage, modelName, isSubscribed, hasAttachedFiles)
            
            // Setup progress bar
            usageProgress.progress = (usagePercentage * 100).toInt()
            usageProgress.progressTintList = ContextCompat.getColorStateList(context, R.color.error_color)
            usageText.text = "${(usagePercentage * 100).toInt()}% used"
            
            // Show/hide upgrade button
            btnUpgrade.visibility = if (isSubscribed) android.view.View.GONE else android.view.View.VISIBLE
            
            // Create dialog
            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false) // User must take action
                .create()
            
            // Setup button callbacks
            btnNewChat.setOnClickListener {
                dialog.dismiss()
                callbacks.onStartNewConversation()
                Timber.d("User forced to start new conversation")
            }
            
            btnUpgrade.setOnClickListener {
                dialog.dismiss()
                callbacks.onUpgradeAccount()
                Timber.d("User chose to upgrade from blocked dialog")
            }
            
            dialog.show()
            
        } catch (e: Exception) {
            Timber.e(e, "Error showing context blocked dialog: ${e.message}")
            
            // Fallback to simple dialog
            showSimpleBlockedDialog(usagePercentage, modelName, callbacks)
        }
    }
    
    /**
     * Show file too large warning
     */
    fun showFileTooLargeDialog(
        fileName: String,
        estimatedTokens: Int,
        maxAllowedTokens: Int,
        callbacks: AlertCallbacks
    ) {
        if (context !is Activity || context.isFinishing) {
            Timber.w("Cannot show file too large dialog - activity not available")
            return
        }
        
        val message = """
            The file "$fileName" is too large for the current context.
            
            File size: ~${TokenValidator.formatTokenCount(estimatedTokens)} tokens
            Available space: ${TokenValidator.formatTokenCount(maxAllowedTokens)} tokens
            
            Please start a new conversation or choose a smaller file.
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("File Too Large")
            .setMessage(message)
            .setIcon(R.drawable.ic_error)
            .setPositiveButton("Start New Chat") { _, _ ->
                callbacks.onStartNewConversation()
            }
            .setNegativeButton("Cancel") { _, _ ->
                callbacks.onCancel()
            }
            .show()
    }
    
    /**
     * Show message too long warning
     */
    fun showMessageTooLongDialog(
        messageTokens: Int,
        maxAllowedTokens: Int,
        callbacks: AlertCallbacks
    ) {
        if (context !is Activity || context.isFinishing) {
            Timber.w("Cannot show message too long dialog - activity not available")
            return
        }
        
        val message = """
            Your message is too long for the current context.
            
            Message size: ${TokenValidator.formatTokenCount(messageTokens)} tokens
            Available space: ${TokenValidator.formatTokenCount(maxAllowedTokens)} tokens
            
            Please shorten your message or start a new conversation.
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("Message Too Long")
            .setMessage(message)
            .setIcon(R.drawable.ic_error)
            .setPositiveButton("Start New Chat") { _, _ ->
                callbacks.onStartNewConversation()
            }
            .setNegativeButton("Edit Message") { _, _ ->
                callbacks.onCancel()
            }
            .show()
    }
    
    /**
     * Build warning message text
     */
    private fun buildWarningMessage(
        usagePercentage: Float,
        modelName: String,
        isSubscribed: Boolean,
        hasAttachedFiles: Boolean = false
    ): String {
        val percentage = (usagePercentage * 100).toInt()
        
        val baseMessage = if (hasAttachedFiles && percentage > 100) {
            // Show the specific message format requested by user for over-limit with files
            """
                Conversation is $percentage% over the length limit for $modelName.
                
                Try replacing the attached files with smaller excerpts or remove some files to reduce the token count.
                
                Alternatively, you can start a new conversation.
            """.trimIndent()
        } else if (hasAttachedFiles) {
            """
                This conversation is using $percentage% of the available context window for $modelName.
                
                The attached files are contributing significantly to token usage. Consider:
                • Using smaller file excerpts
                • Removing unnecessary files
                • Starting a new conversation
            """.trimIndent()
        } else {
            """
                This conversation is using $percentage% of the available context window for $modelName.
                
                When the context fills up, the AI may lose track of earlier parts of the conversation.
                
                What would you like to do?
            """.trimIndent()
        }
        
        return if (!isSubscribed) {
            "$baseMessage\n\n💡 Pro users get larger context windows with more models!"
        } else {
            baseMessage
        }
    }
    
    /**
     * Build blocked message text
     */
    private fun buildBlockedMessage(
        usagePercentage: Float,
        modelName: String,
        isSubscribed: Boolean,
        hasAttachedFiles: Boolean = false
    ): String {
        val percentage = (usagePercentage * 100).toInt()
        
        val baseMessage = if (hasAttachedFiles && percentage > 100) {
            // Show specific message for over-limit with files (user's requested format)
            """
                Conversation is $percentage% over the length limit for $modelName.
                
                Try replacing the attached files with smaller excerpts to reduce token usage, or start a new conversation.
                
                📄 File attachments often contain large amounts of text that count toward token limits.
            """.trimIndent()
        } else if (hasAttachedFiles) {
            """
                The context window for $modelName is at capacity ($percentage% used).
                
                Your attached files are using significant tokens. To continue:
                • Start a new conversation (recommended)
                • Or remove/replace files with smaller excerpts
                
                All conversations are saved and accessible anytime.
            """.trimIndent()
        } else {
            """
                The context window for $modelName is full ($percentage% used).
                
                To continue chatting, you need to start a new conversation. Don't worry - all your previous conversations are saved and you can return to them anytime.
                
                Why context limits exist:
                • AI models have fixed memory limits
                • Staying within limits ensures better responses
                • New conversations keep things focused
            """.trimIndent()
        }
        
        return if (!isSubscribed) {
            "$baseMessage\n\n💡 Pro users get access to models with larger context windows!"
        } else {
            baseMessage
        }
    }
    
    /**
     * Fallback simple warning dialog
     */
    private fun showSimpleWarningDialog(
        usagePercentage: Float,
        modelName: String,
        callbacks: AlertCallbacks
    ) {
        val percentage = (usagePercentage * 100).toInt()
        
        AlertDialog.Builder(context)
            .setTitle("Context Window Warning")
            .setMessage("This conversation is $percentage% full. Consider starting a new conversation soon.")
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton("Continue") { _, _ ->
                callbacks.onContinueAnyway()
            }
            .setNeutralButton("New Chat") { _, _ ->
                callbacks.onStartNewConversation()
            }
            .setNegativeButton("Cancel") { _, _ ->
                callbacks.onCancel()
            }
            .show()
    }
    
    /**
     * Fallback simple blocked dialog
     */
    private fun showSimpleBlockedDialog(
        usagePercentage: Float,
        modelName: String,
        callbacks: AlertCallbacks
    ) {
        val percentage = (usagePercentage * 100).toInt()
        
        AlertDialog.Builder(context)
            .setTitle("Context Limit Reached")
            .setMessage("The context window is full ($percentage%). You need to start a new conversation to continue.")
            .setIcon(R.drawable.ic_error)
            .setPositiveButton("New Chat") { _, _ ->
                callbacks.onStartNewConversation()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show info dialog about context windows
     */
    fun showContextExplanationDialog() {
        if (context !is Activity || context.isFinishing) return
        
        val message = """
            What are Context Windows?
            
            Context windows are the "memory" limits of AI models. They determine how much text (measured in tokens) the AI can remember at once.
            
            Why do they matter?
            • Larger contexts = AI remembers more of your conversation
            • When full, AI may forget earlier messages
            • Different models have different limits
            
            Token Usage:
            • Your messages use tokens
            • AI responses use tokens  
            • Uploaded files use tokens
            • Everything adds up in the context
            
            Best Practices:
            ✅ Start new chats for different topics
            ✅ Keep conversations focused
            ✅ Use appropriate models for your needs
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("Understanding Context Windows")
            .setMessage(message)
            .setIcon(R.drawable.ic_info)
            .setPositiveButton("Got it") { _, _ -> }
            .show()
    }
    
    /**
     * Show success message when starting new conversation
     */
    fun showNewConversationStartedMessage() {
        if (context !is Activity || context.isFinishing) return
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("New Conversation Started")
            .setMessage("You now have a fresh context window. Your previous conversation has been saved and you can return to it anytime from the history.")
            .setIcon(R.drawable.ic_checkmark_green)
            .setPositiveButton("Continue") { _, _ -> }
            .show()
    }
}

/**
 * Extension function to easily show context alerts from Activities
 */
fun Activity.showContextWindowAlert(
    type: ContextAlertType,
    usagePercentage: Float,
    estimatedTokens: Int,
    modelName: String,
    isSubscribed: Boolean,
    callbacks: ContextWindowAlertManager.AlertCallbacks
) {
    val alertManager = ContextWindowAlertManager(this)
    
    when (type) {
        ContextAlertType.WARNING -> alertManager.showContextWarningDialog(
            usagePercentage, estimatedTokens, modelName, isSubscribed, callbacks
        )
        ContextAlertType.BLOCKED -> alertManager.showContextBlockedDialog(
            usagePercentage, estimatedTokens, modelName, isSubscribed, callbacks
        )
    }
}

enum class ContextAlertType {
    WARNING,  // 70% - can continue
    BLOCKED   // 80% - must start new
}