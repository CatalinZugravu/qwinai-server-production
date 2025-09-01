package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.cyberflux.qwinai.R
import kotlinx.coroutines.*
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * UI manager for message draft functionality
 * Handles draft visual indicators, restoration prompts, and user interactions
 */
class DraftUIManager(
    private val context: Context,
    private val draftManager: MessageDraftManager,
    private val coroutineScope: CoroutineScope
) : MessageDraftManager.DraftCallback {
    
    private var currentConversationId: Long? = null
    private var draftIndicator: View? = null
    private var draftText: TextView? = null
    private var draftTimestamp: TextView? = null
    private var restoreButton: Button? = null
    private var dismissButton: ImageView? = null
    private var messageInput: EditText? = null
    private var draftTextWatcher: TextWatcher? = null
    private var callback: DraftUICallback? = null
    
    companion object {
        private const val DRAFT_PREVIEW_LENGTH = 50
    }
    
    /**
     * Callback interface for UI events
     */
    interface DraftUICallback {
        fun onDraftRestored(text: String, files: List<MessageDraftManager.DraftFile>)
        fun onDraftDismissed()
        fun onDraftSaved()
        fun onDraftError(error: String)
    }
    
    /**
     * Initialize draft UI manager
     */
    fun initialize() {
        draftManager.registerDraftCallback(this)
        Timber.d("Draft UI manager initialized")
    }
    
    /**
     * Set draft UI callback
     */
    fun setDraftUICallback(callback: DraftUICallback) {
        this.callback = callback
    }
    
    /**
     * Set up draft UI components
     */
    fun setupDraftUI(
        conversationId: Long,
        draftIndicatorView: View,
        messageInputView: EditText,
        draftTextView: TextView? = null,
        draftTimestampView: TextView? = null,
        restoreButtonView: Button? = null,
        dismissButtonView: ImageView? = null
    ) {
        currentConversationId = conversationId
        draftIndicator = draftIndicatorView
        messageInput = messageInputView
        draftText = draftTextView
        draftTimestamp = draftTimestampView
        restoreButton = restoreButtonView
        dismissButton = dismissButtonView
        
        // Remove existing text watcher
        draftTextWatcher?.let { messageInput?.removeTextChangedListener(it) }
        
        // Setup text watcher for auto-save
        draftTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    draftManager.updateDraftText(conversationId, text)
                }
            }
        }
        
        messageInput?.addTextChangedListener(draftTextWatcher)
        
        // Setup button listeners
        restoreButton?.setOnClickListener {
            restoreDraft()
        }
        
        dismissButton?.setOnClickListener {
            dismissDraft()
        }
        
        // Check for existing draft
        checkForExistingDraft(conversationId)
    }
    
    /**
     * Check for existing draft and show indicator if found
     */
    private fun checkForExistingDraft(conversationId: Long) {
        coroutineScope.launch {
            try {
                val draft = draftManager.restoreDraft(conversationId)
                if (draft != null && (draft.text.isNotEmpty() || draft.attachedFiles.isNotEmpty())) {
                    withContext(Dispatchers.Main) {
                        showDraftIndicator(draft)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        hideDraftIndicator()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for existing draft")
            }
        }
    }
    
    /**
     * Show draft indicator with preview
     */
    private fun showDraftIndicator(draft: MessageDraftManager.DraftData) {
        draftIndicator?.isVisible = true
        
        // Update draft preview text
        val previewText = if (draft.text.length > DRAFT_PREVIEW_LENGTH) {
            "${draft.text.take(DRAFT_PREVIEW_LENGTH)}..."
        } else {
            draft.text
        }
        
        draftText?.text = if (previewText.isNotEmpty()) {
            previewText
        } else if (draft.attachedFiles.isNotEmpty()) {
            "${draft.attachedFiles.size} file(s) attached"
        } else {
            "Draft available"
        }
        
        // Update timestamp
        val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        draftTimestamp?.text = "Saved ${timeFormat.format(Date(draft.lastModified))}"
        
        // Add visual styling
        draftIndicator?.background = ContextCompat.getDrawable(context, R.drawable.draft_indicator_background)
        
        Timber.d("Draft indicator shown for conversation ${draft.conversationId}")
    }
    
    /**
     * Hide draft indicator
     */
    private fun hideDraftIndicator() {
        draftIndicator?.isVisible = false
    }
    
    /**
     * Restore draft content
     */
    private fun restoreDraft() {
        val conversationId = currentConversationId ?: return
        
        coroutineScope.launch {
            try {
                val draft = draftManager.getDraft(conversationId)
                if (draft != null) {
                    withContext(Dispatchers.Main) {
                        // Remove text watcher temporarily to avoid triggering save
                        draftTextWatcher?.let { messageInput?.removeTextChangedListener(it) }
                        
                        // Restore text
                        messageInput?.setText(draft.text)
                        messageInput?.setSelection(draft.text.length)
                        
                        // Re-add text watcher
                        draftTextWatcher?.let { messageInput?.addTextChangedListener(it) }
                        
                        // Hide indicator
                        hideDraftIndicator()
                        
                        // Notify callback
                        callback?.onDraftRestored(draft.text, draft.attachedFiles)
                        
                        Timber.d("Draft restored for conversation $conversationId")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error restoring draft")
                withContext(Dispatchers.Main) {
                    callback?.onDraftError("Failed to restore draft")
                }
            }
        }
    }
    
    /**
     * Dismiss draft (delete it)
     */
    private fun dismissDraft() {
        val conversationId = currentConversationId ?: return
        
        coroutineScope.launch {
            try {
                draftManager.deleteDraft(conversationId)
                withContext(Dispatchers.Main) {
                    hideDraftIndicator()
                    callback?.onDraftDismissed()
                }
                Timber.d("Draft dismissed for conversation $conversationId")
            } catch (e: Exception) {
                Timber.e(e, "Error dismissing draft")
                withContext(Dispatchers.Main) {
                    callback?.onDraftError("Failed to dismiss draft")
                }
            }
        }
    }
    
    /**
     * Add file to current draft
     */
    fun addFileToDraft(file: MessageDraftManager.DraftFile) {
        val conversationId = currentConversationId ?: return
        draftManager.addDraftFile(conversationId, file)
    }
    
    /**
     * Remove file from current draft
     */
    fun removeFileFromDraft(fileUri: String) {
        val conversationId = currentConversationId ?: return
        draftManager.removeDraftFile(conversationId, fileUri)
    }
    
    /**
     * Clear current draft (when message is sent)
     */
    fun clearCurrentDraft() {
        val conversationId = currentConversationId ?: return
        draftManager.clearDraft(conversationId)
        hideDraftIndicator()
    }
    
    /**
     * Save current draft immediately
     */
    fun saveCurrentDraft() {
        val conversationId = currentConversationId ?: return
        val text = messageInput?.text?.toString()?.trim() ?: ""
        
        if (text.isNotEmpty()) {
            draftManager.updateDraftText(conversationId, text)
        }
        
        coroutineScope.launch {
            draftManager.saveDraft(conversationId)
        }
    }
    
    /**
     * Show draft saving indicator
     */
    private fun showSavingIndicator() {
        // Could add a subtle saving indicator here
        Timber.v("Draft saving...")
    }
    
    /**
     * Hide draft saving indicator
     */
    private fun hideSavingIndicator() {
        // Hide saving indicator
        Timber.v("Draft saved")
    }
    
    /**
     * Create draft indicator view programmatically
     */
    fun createDraftIndicatorView(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            background = ContextCompat.getDrawable(context, R.drawable.draft_indicator_background)
        }
        
        // Draft icon
        val draftIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_draft)
            setColorFilter(ContextCompat.getColor(context, R.color.colorPrimary))
            layoutParams = LinearLayout.LayoutParams(
                24.dpToPx(),
                24.dpToPx()
            ).apply {
                marginEnd = 12.dpToPx()
            }
        }
        
        // Content container
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        // Draft text
        val draftTextView = TextView(context).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            maxLines = 1
        }
        
        // Draft timestamp
        val timestampView = TextView(context).apply {
            id = View.generateViewId()
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        
        contentLayout.addView(draftTextView)
        contentLayout.addView(timestampView)
        
        // Action buttons container
        val actionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        // Restore button
        val restoreBtn = Button(context).apply {
            id = View.generateViewId()
            text = "Restore"
            textSize = 12f
            background = ContextCompat.getDrawable(context, R.drawable.button_small_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dpToPx()
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }
        
        // Dismiss button
        val dismissBtn = ImageView(context).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_close)
            setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
            background = ContextCompat.getDrawable(context, R.drawable.circular_touch_background)
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            )
        }
        
        actionsLayout.addView(restoreBtn)
        actionsLayout.addView(dismissBtn)
        
        layout.addView(draftIcon)
        layout.addView(contentLayout)
        layout.addView(actionsLayout)
        
        return layout
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        draftTextWatcher?.let { messageInput?.removeTextChangedListener(it) }
        draftManager.unregisterDraftCallback(this)
        
        currentConversationId = null
        draftIndicator = null
        draftText = null
        draftTimestamp = null
        restoreButton = null
        dismissButton = null
        messageInput = null
        draftTextWatcher = null
        callback = null
        
        Timber.d("Draft UI manager cleaned up")
    }
    
    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    
    // MessageDraftManager.DraftCallback implementation
    override fun onDraftSaved(conversationId: Long, draft: MessageDraftManager.DraftData) {
        if (conversationId == currentConversationId) {
            hideSavingIndicator()
            callback?.onDraftSaved()
        }
    }
    
    override fun onDraftRestored(conversationId: Long, draft: MessageDraftManager.DraftData) {
        if (conversationId == currentConversationId) {
            showDraftIndicator(draft)
        }
    }
    
    override fun onDraftDeleted(conversationId: Long) {
        if (conversationId == currentConversationId) {
            hideDraftIndicator()
        }
    }
    
    override fun onDraftError(conversationId: Long, error: String) {
        if (conversationId == currentConversationId) {
            hideSavingIndicator()
            callback?.onDraftError(error)
        }
    }
}