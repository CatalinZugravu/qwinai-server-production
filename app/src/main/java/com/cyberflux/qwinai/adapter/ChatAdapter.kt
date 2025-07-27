package com.cyberflux.qwinai.adapter

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.BulletSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.service.AiChatService
import com.cyberflux.qwinai.ui.SourcesBottomSheet
import com.cyberflux.qwinai.ui.spans.RoundedBackgroundSpan
import com.cyberflux.qwinai.utils.FileHandler
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.FileUtil.formatFileSize
import com.cyberflux.qwinai.utils.FileUtil.formatTimestamp
import com.cyberflux.qwinai.utils.UltraFastStreamingProcessor
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.ModelValidator
import com.google.android.material.card.MaterialCardView
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ChatAdapter(
    private val onCopy: (String) -> Unit,
    private val onReload: (ChatMessage) -> Unit,
    private val onNavigate: (ChatMessage, Int) -> Unit,
    private val onLoadMore: () -> Unit,
    private var currentModelId: String = "",
    private var callbacks: Callbacks? = null,
    private val onAudioPlay: (ChatMessage) -> Unit = {}
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {
    
    // Optimized markdown processor for smooth streaming
    private var optimizedProcessor: UltraFastStreamingProcessor? = null
    // Ultra-fast processor for maximum performance streaming
    private var ultraFastProcessor: UltraFastStreamingProcessor? = null

    // Code block handling is now done directly in UltraFastStreamingProcessor.handleCodeBlocks()
    // No need for a separate handleExtractedCodeBlocks method

    companion object {
        // View type constants for recycler view optimization
        const val VIEW_TYPE_WELCOME_MESSAGE = 0
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_AI = 2
        const val VIEW_TYPE_USER_MESSAGE = 1
        const val VIEW_TYPE_AI_MESSAGE = 2
        const val VIEW_TYPE_USER_IMAGE = 3
        const val VIEW_TYPE_AI_IMAGE = 4
        const val VIEW_TYPE_USER_DOCUMENT = 5
        const val VIEW_TYPE_AI_DOCUMENT = 6
        const val VIEW_TYPE_USER_CAMERA = 7
        const val VIEW_TYPE_AI_CAMERA = 8
        const val VIEW_TYPE_USER_CAMERA_PHOTO = 7
        const val VIEW_TYPE_AI_CAMERA_PHOTO = 8
        const val VIEW_TYPE_USER_GROUPED_FILES = 9
        const val VIEW_TYPE_OCR_DOCUMENT = 10
        const val VIEW_TYPE_AI_OCR_DOCUMENT = 10
        const val VIEW_TYPE_AI_GENERATED_IMAGE = 11
    }

    @Volatile
    var isGenerating = false
        private set

    private var isLoading = false
    var recyclerViewRef: RecyclerView? = null
    private var imageRegenerationCallback: ImageRegenerationCallback? = null
    private var currentlyPlayingMessageId: String? = null

    // Add these properties for streaming support
    var isStreamingActive = false

    interface MessageCompletionCallback {
        fun onMessageCompleted(message: ChatMessage)
    }

    interface Callbacks : AiChatService.Callbacks {
        override fun getWebSearchEnabled(): Boolean

        fun isLatestAiMessage(messageId: String): Boolean
    }

    private fun Float.dp(context: Context): Float {
        return this * context.resources.displayMetrics.density
    }
    private var messageCompletionCallback: MessageCompletionCallback? = null

    fun setMessageCompletionCallback(callback: MessageCompletionCallback) {
        this.messageCompletionCallback = callback
    }

    interface ImageRegenerationCallback {
        fun onRegenerateImage(message: ChatMessage)
    }


    data class GroupedFileMessage(
        val images: List<FileInfo> = emptyList(),
        val documents: List<FileInfo> = emptyList(),
        val text: String? = null
    )

    data class FileInfo(
        val uri: String,
        val name: String,
        val size: Long,
        val type: String
    )

    data class WebSearchSource(
        val title: String,
        val url: String,
        val snippet: String,
        val displayLink: String? = null,
        val favicon: String? = null
    ) {
        val shortDisplayName: String
            get() = displayLink?.let { link ->
                when {
                    link.contains("nytimes.com") -> "NY Times"
                    link.contains("bbc.com") || link.contains("bbc.co.uk") -> "BBC"
                    link.contains("reuters.com") -> "Reuters"
                    link.contains("cnn.com") -> "CNN"
                    link.contains("theguardian.com") -> "Guardian"
                    link.contains("wsj.com") -> "WSJ"
                    link.contains("wikipedia.org") -> "Wikipedia"
                    link.contains("yahoo.com") -> "Yahoo"
                    link.contains("nbcnews.com") -> "NBC"
                    link.contains("cbsnews.com") -> "CBS"
                    link.contains("abcnews.go.com") -> "ABC"
                    link.contains("npr.org") -> "NPR"
                    link.contains("forbes.com") -> "Forbes"
                    link.contains("bloomberg.com") -> "Bloomberg"
                    link.contains("techcrunch.com") -> "TechCrunch"
                    link.contains("reddit.com") -> "Reddit"
                    link.contains("twitter.com") || link.contains("x.com") -> "X"
                    link.contains("facebook.com") -> "Facebook"
                    link.contains("instagram.com") -> "Instagram"
                    link.contains("youtube.com") -> "YouTube"
                    link.contains("linkedin.com") -> "LinkedIn"
                    link.contains("medium.com") -> "Medium"
                    link.contains("github.com") -> "GitHub"
                    link.contains("stackoverflow.com") -> "Stack Overflow"
                    else -> {
                        // Extract domain name from URL or displayLink
                        try {
                            // First try to get a clean domain name
                            val cleanLink = link.replace("www.", "").replace("m.", "")
                            val parts = cleanLink.split('.')

                            // Handle cases like "example.com" or "news.example.com"
                            when {
                                parts.size >= 2 -> {
                                    // Get the main domain part (e.g., "example" from "example.com")
                                    val domain = parts[parts.size - 2]
                                    // Capitalize first letter
                                    domain.replaceFirstChar { it.uppercase() }
                                }
                                else -> {
                                    // Fallback to first part
                                    parts.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Source"
                                }
                            }
                        } catch (e: Exception) {
                            // If all else fails, try to extract from the title or URL
                            extractDomainFromUrl(url) ?: "Source"
                        }
                    }
                }
            } ?: extractDomainFromUrl(url) ?: "Source"

        // Helper function to extract domain from URL
        private fun extractDomainFromUrl(url: String): String? {
            return try {
                val uri = url.toUri()
                val host = uri.host ?: return null

                // Remove common prefixes
                val cleanHost = host.replace("www.", "").replace("m.", "")

                // Extract main domain
                val parts = cleanHost.split('.')
                when {
                    parts.size >= 2 -> {
                        val domain = parts[parts.size - 2]
                        domain.replaceFirstChar { it.uppercase() }
                    }
                    else -> cleanHost.replaceFirstChar { it.uppercase() }
                }
            } catch (e: Exception) {
                null
            }
        }
    }    @SuppressLint("NotifyDataSetChanged")
    fun setGenerating(generating: Boolean) {
        Timber.d("Setting generation state to: $generating (was: $isGenerating)")
        if (isGenerating != generating) {
            isGenerating = generating

            // Immediate UI update on main thread
            Handler(Looper.getMainLooper()).post {
                try {
                    notifyDataSetChanged()
                    Timber.d("Adapter updated after generation state change to: $generating")
                } catch (e: Exception) {
                    Timber.e(e, "notifyDataSetChanged failed: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateCurrentModel(modelId: String) {
        this.currentModelId = modelId
        notifyDataSetChanged()
    }
    
    /**
     * Get performance statistics from the optimized processor
     */
    fun getPerformanceStats(): String {
        return optimizedProcessor?.getPerformanceStats() ?: "Processor not initialized"
    }
    
    /**
     * Clear cache and reset processor state
     */
    fun clearMarkdownCache() {
        optimizedProcessor?.clearCache()
        Timber.d("Markdown cache cleared")
    }

    // FIXED: Method to update content during streaming without flickering
    @Deprecated("Use updateStreamingContentDirect instead for better performance", ReplaceWith("updateStreamingContentDirect(messageId, content, false)"))
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun updateStreamingContent(messageId: String, content: String) {
        val position = currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            val currentMessage = currentList[position]

            // During streaming, ONLY update ViewHolder directly to prevent flickering
            recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                if (holder is AiMessageViewHolder) {
                    holder.updateContentOnly(content)
                    return
                }
            }

            // Only use DiffUtil as fallback when ViewHolder not available
            val updatedMessage = currentMessage.copy(message = content)
            val newList = currentList.toMutableList()
            newList[position] = updatedMessage
            submitList(newList)
        }
    }

    // În ChatAdapter.kt
    @Deprecated("Use updateLoadingStateDirect instead for better performance", ReplaceWith("updateLoadingStateDirect(messageId, isGenerating, isWebSearching, customStatusText, customStatusColor)"))
    fun updateLoadingState(
        messageId: String,
        isGenerating: Boolean,
        isWebSearching: Boolean,
        customStatusText: String? = null,  // Parametru nou
        customStatusColor: Int? = null     // Parametru nou
    ) {
        val position = currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            // Update the message in the list
            val currentMessage = currentList[position]
            val updatedMessage = currentMessage.copy(
                isGenerating = isGenerating,
                isWebSearchActive = isWebSearching,
                isLoading = isGenerating || isWebSearching,
                initialIndicatorText = customStatusText ?: currentMessage.initialIndicatorText,  // Modificare aici
                initialIndicatorColor = customStatusColor ?: currentMessage.initialIndicatorColor  // Modificare aici
            )

            val newList = currentList.toMutableList()
            newList[position] = updatedMessage

            submitList(newList) {
                // Update ViewHolder directly
                recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                    if (holder is AiMessageViewHolder) {
                        holder.updateLoadingState(updatedMessage)
                    }
                }
            }
        }
    }
    // Add these methods to your ChatAdapter class
    fun startStreamingMode() {
        isStreamingActive = true
        recyclerViewRef?.itemAnimator?.changeDuration = 0
        recyclerViewRef?.itemAnimator?.moveDuration = 0
        recyclerViewRef?.itemAnimator?.addDuration = 0
        recyclerViewRef?.itemAnimator?.removeDuration = 0

        // Disable animations and prefetch during streaming for better performance
        recyclerViewRef?.layoutManager?.let {
            if (it is androidx.recyclerview.widget.LinearLayoutManager) {
                it.setItemPrefetchEnabled(false)
            }
        }

        // Disable item change animations completely
        recyclerViewRef?.itemAnimator = null

        Timber.d("Started streaming mode - disabled animations")
    }

    @Deprecated("Use stopStreamingModeGradually instead to prevent layout refresh", ReplaceWith("stopStreamingModeGradually()"))
    fun stopStreamingMode() {
        isStreamingActive = false

        // Restore default animator
        recyclerViewRef?.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            changeDuration = 250
            moveDuration = 250
            addDuration = 250
            removeDuration = 250
        }

        // Re-enable animations and prefetch
        recyclerViewRef?.layoutManager?.let {
            if (it is androidx.recyclerview.widget.LinearLayoutManager) {
                it.setItemPrefetchEnabled(true)
            }
        }

        Timber.d("Stopped streaming mode - restored animations")
    }

    // OPTIMIZED: Direct ViewHolder content updates for streaming
    fun updateStreamingContentDirect(messageId: String, content: String, processMarkdown: Boolean = false, isStreaming: Boolean = true) {
        val position = currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            // Direct ViewHolder update for maximum speed
            recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                if (holder is AiMessageViewHolder) {
                    if (processMarkdown) {
                        if (isStreaming) {
                            // Use optimized markdown processor for real-time rendering
                            Timber.d("CODE_BLOCK_DEBUG: Processing STREAMING markdown for position $position")
                            Timber.d("CODE_BLOCK_DEBUG: ChatAdapter - About to call processMarkdownStreaming")
                            Timber.d("CODE_BLOCK_DEBUG: ChatAdapter - Container: ${holder.codeBlocksContainer}")
                            Timber.d("CODE_BLOCK_DEBUG: ChatAdapter - Container null? ${holder.codeBlocksContainer == null}")
                            optimizedProcessor?.processMarkdownStreaming(
                                content = content,
                                textView = holder.messageText,
                                codeBlockContainer = holder.codeBlocksContainer
                            )
                            // Code blocks are now handled inside processMarkdownStreaming
                        } else {
                            // Use complete markdown processing for final content
                            Timber.d("CODE_BLOCK_DEBUG: Processing COMPLETE markdown for position $position")
                            optimizedProcessor?.processMarkdownComplete(content, holder.messageText, holder.codeBlocksContainer)
                            // Code blocks are now handled inside processMarkdownComplete
                        }
                    } else {
                        // Direct text update for maximum speed
                        holder.messageText.text = content
                    }
                    return
                }
            }
        }
    }

    // ULTRA-FAST: Enhanced direct ViewHolder updates with intelligent processing
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun updateStreamingContentUltraFast(
        messageId: String, 
        content: String, 
        processMarkdown: Boolean = false, 
        isStreaming: Boolean = true,
        contentLength: Int = content.length
    ) {
        val position = currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            // ULTRA-FAST: Direct ViewHolder access with minimal overhead
            recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                if (holder is AiMessageViewHolder) {
                    when {
                        processMarkdown -> {
                            // INTELLIGENT: Use ultra-fast processor for optimal performance
                            Timber.d("CODE_BLOCK_DEBUG: ChatAdapter - Using ultraFastProcessor")
                            Timber.d("CODE_BLOCK_DEBUG: ChatAdapter - Ultra container: ${holder.codeBlocksContainer}")
                            ultraFastProcessor?.processStreamingMarkdown(
                                content = content,
                                textView = holder.messageText,
                                isStreaming = isStreaming,
                                codeBlockContainer = holder.codeBlocksContainer
                            ) ?: run {
                                // Fallback to optimized processor
                                optimizedProcessor?.processMarkdownStreaming(
                                    content = content,
                                    textView = holder.messageText,
                                    isStreaming = isStreaming,
                                    codeBlockContainer = holder.codeBlocksContainer

                                )
                            }
                        }
                        else -> {
                            // ULTRA-FAST: Direct text update with zero overhead
                            holder.messageText.text = content
                        }
                    }
                    return
                }
            }
        }
    }

    // ULTRA-OPTIMIZED: Gradual animation restoration to prevent layout refresh
    fun stopStreamingModeGradually() {
        isStreamingActive = false

        // Gradually restore animations to prevent jarring layout changes
        recyclerViewRef?.post {
            recyclerViewRef?.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                // Start with shorter durations and gradually increase
                changeDuration = 100
                moveDuration = 100
                addDuration = 100
                removeDuration = 100
            }

            // After a delay, restore full animation durations
            recyclerViewRef?.postDelayed({
                recyclerViewRef?.itemAnimator?.let { animator ->
                    if (animator is androidx.recyclerview.widget.DefaultItemAnimator) {
                        animator.changeDuration = 250
                        animator.moveDuration = 250
                        animator.addDuration = 250
                        animator.removeDuration = 250
                    }
                }
            }, 200)

            // Re-enable prefetch gradually
            recyclerViewRef?.postDelayed({
                recyclerViewRef?.layoutManager?.let {
                    if (it is androidx.recyclerview.widget.LinearLayoutManager) {
                        it.setItemPrefetchEnabled(true)
                    }
                }
            }, 300)
        }

        Timber.d("Stopped streaming mode gradually - smooth animation restoration")
    }

    // ULTRA-OPTIMIZED: Direct loading state updates bypassing DiffUtil
    fun updateLoadingStateDirect(
        messageId: String,
        isGenerating: Boolean,
        isWebSearching: Boolean = false,
        customStatusText: String? = null,
        customStatusColor: Int? = null
    ) {
        val position = currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            // Direct ViewHolder update for maximum speed
            recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                if (holder is AiMessageViewHolder) {
                    val message = currentList[position]
                    val updatedMessage = message.copy(
                        isGenerating = isGenerating,
                        isWebSearchActive = isWebSearching
                    )
                    
                    // Direct state update without triggering adapter changes
                    holder.updateLoadingStateDirect(updatedMessage, customStatusText, customStatusColor)
                    return
                }
            }
        }
    }

    // ULTRA-OPTIMIZED: Direct message updates without full list submission
    fun updateMessageDirectly(position: Int, message: ChatMessage) {
        if (position >= 0 && position < currentList.size) {
            // Update the ViewHolder directly if visible
            recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                when (holder) {
                    is AiMessageViewHolder -> {
                        if (message.hasWebSearchResults) {
                            holder.updateWebSearchResults(message)
                        }
                    }
                }
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return if (currentList.size > 0 || position >= currentList.size) {
            getItem(position).id.hashCode().toLong()
        } else {
            -1L
        }
    }

    override fun getItemCount(): Int {
        val normalCount = currentList.size
        return if (normalCount == 0 && !isGenerating) 1 else normalCount
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerViewRef = recyclerView
        
        // Initialize processors for ultra-fast streaming
        if (optimizedProcessor == null) {
            optimizedProcessor = UltraFastStreamingProcessor(recyclerView.context)
            optimizedProcessor?.setDebugMode(true) // Enable for debugging
        }
        if (ultraFastProcessor == null) {
            ultraFastProcessor = UltraFastStreamingProcessor(recyclerView.context)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerViewRef = null
        
        // Cleanup processor resources
        optimizedProcessor?.cleanup()
        optimizedProcessor = null
        ultraFastProcessor?.cleanup()
        ultraFastProcessor = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_WELCOME_MESSAGE -> WelcomeMessageViewHolder(inflater.inflate(R.layout.welcome_message, parent, false))
            VIEW_TYPE_USER -> UserMessageViewHolder(inflater.inflate(R.layout.item_chat_message_user, parent, false))
            VIEW_TYPE_AI -> AiMessageViewHolder(inflater.inflate(R.layout.item_chat_message_ai, parent, false))
            VIEW_TYPE_USER_IMAGE -> UserImageMessageViewHolder(inflater.inflate(R.layout.item_chat_image_message_user, parent, false))
            VIEW_TYPE_AI_IMAGE -> AiImageMessageViewHolder(inflater.inflate(R.layout.item_chat_image_message_ai, parent, false))
            VIEW_TYPE_USER_DOCUMENT -> UserDocumentMessageViewHolder(inflater.inflate(R.layout.item_chat_document_message_user, parent, false))
            VIEW_TYPE_AI_DOCUMENT -> AiDocumentMessageViewHolder(inflater.inflate(R.layout.item_chat_document_message_ai, parent, false))
            VIEW_TYPE_USER_CAMERA -> UserCameraPhotoViewHolder(inflater.inflate(R.layout.item_chat_camera_message_user, parent, false))
            VIEW_TYPE_AI_CAMERA -> AiCameraPhotoViewHolder(inflater.inflate(R.layout.item_chat_camera_message_ai, parent, false))
            VIEW_TYPE_USER_GROUPED_FILES -> UserGroupedFilesViewHolder(inflater.inflate(R.layout.item_chat_grouped_files_user, parent, false))
            VIEW_TYPE_AI_GENERATED_IMAGE -> AiGeneratedImageViewHolder(inflater.inflate(R.layout.item_ai_generated_image, parent, false))
            VIEW_TYPE_AI_OCR_DOCUMENT -> OCRDocumentViewHolder(inflater.inflate(R.layout.item_ai_ocr_document, parent, false), onCopy, onReload)
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (currentList.isEmpty() && !isGenerating && position == 0) return VIEW_TYPE_WELCOME_MESSAGE
        if (position >= currentList.size) {
            Timber.e("Attempted to get view type for position $position, but currentList size is ${currentList.size}")
            return VIEW_TYPE_WELCOME_MESSAGE
        }

        val message = getItem(position)
        if (message.isUser && (message.message.startsWith("{\"images\":") || message.message.startsWith("{\"documents\":") || message.message.contains("\"FileInfo\""))) {
            Timber.d("Detected grouped files message at position $position")
            return VIEW_TYPE_USER_GROUPED_FILES
        }

        Timber.d("Message type at position $position: isUser=${message.isUser}, isImage=${message.isImage}, isDocument=${message.isDocument}, isCamera=${message.isCamera}, isOcrDocument=${message.isOcrDocument}, URI=${message.message.take(30)}")

        val isDocumentByExtension = if (message.message.contains("content://") || message.message.contains("file://")) {
            val path = message.message.lowercase()
            path.endsWith(".pdf") || path.endsWith(".doc") || path.endsWith(".docx") || path.endsWith(".xls") || path.endsWith(".xlsx") || path.endsWith(".txt") || path.endsWith(".ppt") || path.endsWith(".pptx") || path.endsWith(".csv")
        } else false

        val shouldTreatAsDocument = message.isDocument || isDocumentByExtension

        return if (message.isUser) {
            when {
                shouldTreatAsDocument -> VIEW_TYPE_USER_DOCUMENT
                message.isImage || message.isCamera -> VIEW_TYPE_USER_IMAGE
                else -> VIEW_TYPE_USER
            }
        } else {
            when {
                message.isOcrDocument -> VIEW_TYPE_AI_OCR_DOCUMENT
                shouldTreatAsDocument -> VIEW_TYPE_AI_DOCUMENT
                message.isImage || message.isCamera -> VIEW_TYPE_AI_IMAGE
                message.isGeneratedImage -> VIEW_TYPE_AI_GENERATED_IMAGE
                else -> VIEW_TYPE_AI
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        val message = if (position < currentList.size) getItem(position) else return

        payloads.forEach { payload ->
            when (payload) {
                "STREAMING_UPDATE", "CONTENT_UPDATE" -> {
                    // Fast-track streaming content updates
                    when (holder) {
                        is AiMessageViewHolder -> {
                            // Bypass other checks during streaming for better performance
                            holder.updateContentOnly(message.message)
                            // Skip further processing if streaming update
                            if (isStreamingActive) return
                        }
                    }
                }
                "LOADING_UPDATE" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            holder.updateLoadingState(message)
                        }
                    }
                }
                "FINAL_UPDATE" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            holder.handleFinalUpdate()
                        }
                    }
                }
                "FINAL_CODE_BLOCK_UPDATE" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            // Force complete reprocessing with emphasis on code blocks
                            holder.forceFinalCodeBlockProcessing()
                        }
                    }
                }
                "ENSURE_CODE_BLOCKS_PERSIST" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            // Final pass to ensure code blocks remain visible and properly formatted
                            val currentMessage = getItem(position)
                            holder.updateContent(currentMessage.message, currentMessage)
                        }
                    }
                }
                "AUDIO_STATE_CHANGE" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            holder.updateAudioButtonState(message.id, message.isAudioPlaying)
                        }
                    }
                }
            }
        }

        // Fall back to full binding if no specific payload handled
        if (isStreamingActive) return // Skip full binding during streaming
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (currentList.isEmpty() && !isGenerating && position == 0) {
            if (holder is WelcomeMessageViewHolder) {
                holder.bind()
            }
            return
        }

        if (position >= currentList.size) {
            Timber.e("Attempted to bind position $position, but currentList size is ${currentList.size}")
            return
        }

        val message = getItem(position)

        when (holder) {
            is WelcomeMessageViewHolder -> holder.bind()
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message)
            is UserImageMessageViewHolder -> holder.bind(message)
            is AiImageMessageViewHolder -> holder.bind(message)
            is UserDocumentMessageViewHolder -> holder.bind(message)
            is AiDocumentMessageViewHolder -> holder.bind(message)
            is OCRDocumentViewHolder -> holder.bind(message)
            is UserCameraPhotoViewHolder -> holder.bind(message)
            is AiCameraPhotoViewHolder -> holder.bind(message)
            is UserGroupedFilesViewHolder -> holder.bind(message)
            is AiGeneratedImageViewHolder -> holder.bind(message)
        }

        // Load more messages if near the beginning of the list
        if (position == 0 && !isLoading && currentList.size > 0) {
            isLoading = true
            onLoadMore()
        }
    }

    // WelcomeMessageViewHolder - unchanged
    inner class WelcomeMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modelIcon: ImageView = itemView.findViewById(R.id.ivModelIcon)
        private val welcomeHeader: TextView = itemView.findViewById(R.id.tvWelcomeHeader)

        @SuppressLint("SetTextI18n")
        fun bind() {
            val modelName = ModelIconUtils.getModelNameForDisplay(currentModelId)
            modelIcon.setImageResource(ModelIconUtils.getIconResourceForModel(currentModelId))
            
            // Get dynamic welcome message based on current model and user name
            val welcomeMessage = getWelcomeMessageForModel(currentModelId, modelName)
            welcomeHeader.text = welcomeMessage
            
            val modelColor = ModelIconUtils.getColorForModel(currentModelId)
            welcomeHeader.setTextColor(modelColor)
            val parent = itemView.parent as? RecyclerView
            parent?.let { itemView.minimumHeight = (parent.height * 0.7).toInt() }
        }
        
        private fun getWelcomeMessageForModel(modelId: String, modelName: String): String {
            val context = itemView.context
            val userName = com.cyberflux.qwinai.utils.PrefsManager.getUserName(context)
            val displayName = if (!userName.isNullOrBlank()) userName else "there"
            
            return when {
                // OCR-specific models - fixed message
                modelId.contains("ocr", ignoreCase = true) || modelId == "mistral/mistral-ocr-latest" -> {
                    "Hi $displayName! What image or PDF would you like me to perform OCR?"
                }
                
                // All other models - rotating nice messages
                else -> {
                    val niceMessages = listOf(
                        "Welcome back, $displayName!",
                        "Let's get creative, $displayName!",
                        "Happy ${getDayOfWeek()}, $displayName!",
                        "Ready to chat, $displayName?",
                        "Hey $displayName! What's on your mind?",
                        "Good to see you again, $displayName!",
                        "Let's make today amazing, $displayName!",
                        "Hope you're having a great day, $displayName!",
                        "What adventure shall we go on, $displayName?",
                        "Feeling curious today, $displayName?",
                        "Let's explore something new, $displayName!",
                        "Ready for some fun, $displayName?",
                        "What's inspiring you today, $displayName?",
                        "Let's dive into something interesting, $displayName!",
                        "Great to have you here, $displayName!",
                        "What story shall we create, $displayName?",
                        "Let's make magic happen, $displayName!",
                        "Time for some brainstorming, $displayName?",
                        "What discovery awaits us, $displayName?",
                        "Let's turn ideas into reality, $displayName!",
                        "Hello there, $displayName!",
                        "What's new today, $displayName?",
                        "Ready to learn something, $displayName?",
                        "Let's start fresh, $displayName!",
                        "What questions do you have, $displayName?",
                        "Time to get things done, $displayName!",
                        "Let's solve problems together, $displayName!",
                        "What's exciting you, $displayName?",
                        "Ready for a challenge, $displayName?",
                        "Let's build something cool, $displayName!",
                        "What's your next move, $displayName?",
                        "Time to innovate, $displayName!",
                        "Let's think outside the box, $displayName!",
                        "What's your vision, $displayName?",
                        "Ready to make progress, $displayName?",
                        "Let's tackle today together, $displayName!",
                        "What's sparking your interest, $displayName?",
                        "Time for fresh ideas, $displayName!",
                        "Let's create something special, $displayName!",
                        "What's your goal today, $displayName?"
                    )
                    
                    // Use current time to ensure messages change each time user enters
                    val messageIndex = (System.currentTimeMillis() / 1000).toInt() % niceMessages.size
                    niceMessages[messageIndex]
                }
            }
        }
        
        private fun getDayOfWeek(): String {
            val calendar = java.util.Calendar.getInstance()
            return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "Monday"
                java.util.Calendar.TUESDAY -> "Tuesday"
                java.util.Calendar.WEDNESDAY -> "Wednesday"
                java.util.Calendar.THURSDAY -> "Thursday"
                java.util.Calendar.FRIDAY -> "Friday"
                java.util.Calendar.SATURDAY -> "Saturday"
                java.util.Calendar.SUNDAY -> "Sunday"
                else -> "day"
            }
        }
    }

    // UserMessageViewHolder - enhanced with navigation support
    inner class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val voiceIndicator: ImageView? = itemView.findViewById(R.id.ivVoiceIndicator)
        private val navigationContainer: LinearLayout? = itemView.findViewById(R.id.userNavigationControls)
        private val btnPrevious: ImageButton? = itemView.findViewById(R.id.btnPrevious)
        private val btnNext: ImageButton? = itemView.findViewById(R.id.btnNext)
        private val tvNavigationIndicator: TextView? = itemView.findViewById(R.id.tvNavigationIndicator)

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun bind(message: ChatMessage) {
            messageText.text = message.getCurrentVersionText()
            messageText.setTextIsSelectable(false)
            timestampText.text = formatTimestamp(message.timestamp)

            if (voiceIndicator != null) {
                voiceIndicator.visibility = if (message.isVoiceMessage) View.VISIBLE else View.GONE
            }

            // Handle navigation controls for user messages
            handleUserNavigationControls(message)

            messageText.setOnLongClickListener { view ->
                handleUserMessageLongPress(view, message)
            }

            itemView.setOnLongClickListener { view ->
                handleUserMessageLongPress(view, message)
            }

            Timber.d("UserMsgBind: ID=${message.id}, Content='${message.message.take(20)}'")
        }

        private fun handleUserNavigationControls(message: ChatMessage) {
            // Check if any message is generating
            val isAnyMessageGenerating = currentList.any { it.isGenerating }
            
            // Hide navigation controls during generation
            if (isAnyMessageGenerating) {
                navigationContainer?.visibility = View.GONE
                return
            }
            
            if (message.totalVersions > 1) {
                navigationContainer?.visibility = View.VISIBLE
                tvNavigationIndicator?.text = "<${message.versionIndex + 1}/${message.totalVersions}>"

                btnPrevious?.isEnabled = message.versionIndex > 0
                btnNext?.isEnabled = message.versionIndex < message.totalVersions - 1

                btnPrevious?.alpha = if (btnPrevious?.isEnabled == true) 1.0f else 0.3f
                btnNext?.alpha = if (btnNext?.isEnabled == true) 1.0f else 0.3f

                // Setup navigation click listeners
                btnPrevious?.setOnClickListener { 
                    if (btnPrevious.isEnabled) onNavigate(message, -1) 
                }
                btnNext?.setOnClickListener { 
                    if (btnNext.isEnabled) onNavigate(message, 1) 
                }
            } else {
                navigationContainer?.visibility = View.GONE
            }
        }

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun handleUserMessageLongPress(view: View, message: ChatMessage): Boolean {
            return try {
                val context = itemView.context
                if (context is MainActivity) {
                    context.selectedUserPosition = adapterPosition
                    context.selectedUserMessage = message
                    context.selectedMessage = null

                    HapticManager.mediumVibration(context)

                    showMessagePopupMenu(context, view, message, true)
                    Timber.d("Long press detected on user message text at position $adapterPosition")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error showing user message context menu: ${e.message}")
                false
            }
        }

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun showMessagePopupMenu(context: MainActivity, anchorView: View, message: ChatMessage, isUser: Boolean) {
            try {
                val popupMenu = PopupMenu(context, anchorView)

                if (isUser) {
                    popupMenu.menuInflater.inflate(R.menu.popup_user_message, popupMenu.menu)
                }

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_edit_message -> {
                            context.editMessage(message)
                            true
                        }
                        R.id.menu_select_text_user -> {
                            context.openTextSelectionScreen(message.message, false)
                            true
                        }
                        R.id.menu_copy_message_user -> {
                            context.copyToClipboard(message.message)
                            true
                        }
                        else -> false
                    }
                }

                popupMenu.show()

            } catch (e: Exception) {
                Timber.e(e, "Error showing popup menu: ${e.message}")
                context.selectedUserMessage = message
                anchorView.showContextMenu()
            }
        }

        fun cleanup() {
            // No cleanup needed for popup menu
        }
    }

    fun updateAudioPlayingState(messageId: String, isPlaying: Boolean) {
        currentlyPlayingMessageId = if (isPlaying) messageId else null

        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }

        if (index != -1) {
            val message = currentList[index]
            val updatedMessage = message.copy(isAudioPlaying = isPlaying)

            currentList[index] = updatedMessage
            submitList(currentList)

            notifyItemChanged(index, "AUDIO_STATE_CHANGE")
        }
    }
    private data class CodeBlockMatch(
        val start: Int,
        val end: Int,
        val prefixLength: Int,
        val language: String,
        val content: String,
        val isComplete: Boolean
    )
    // SIMPLIFIED AiMessageViewHolder without thinking components
    inner class AiMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Standard UI components
        val messageText: TextView = itemView.findViewById(R.id.tvMessage)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        val btnReload: ImageButton = itemView.findViewById(R.id.btnReload)
        val btnPrevious: ImageButton? = itemView.findViewById(R.id.btnPrevious)
        val btnNext: ImageButton? = itemView.findViewById(R.id.btnNext)
        private val tvNavigationIndicator: TextView? = itemView.findViewById(R.id.tvNavigationIndicator)
        val btnAudio: ImageButton? = itemView.findViewById(R.id.btnAudio)

        private val modelIconView: ImageView = itemView.findViewById(R.id.ivModelIcon)
        private val aiNavigationControls: LinearLayout? = itemView.findViewById(R.id.aiNavigationControls)
        val buttonContainer: LinearLayout = itemView.findViewById(R.id.buttonContainer)

        // Persistent indicator container (always visible)
        private val persistentIndicatorContainer: LinearLayout? = itemView.findViewById(R.id.persistentIndicatorContainer)
        private val persistentLoadingIndicator: com.cyberflux.qwinai.components.PersistentLoadingIndicator? = itemView.findViewById(R.id.persistentLoadingIndicator)
        
        // Status components (can hide/show independently)
        private val simpleStatusContainer: LinearLayout? = itemView.findViewById(R.id.simpleStatusContainer)
        private val tvSimpleStatus: TextView? = itemView.findViewById(R.id.tvSimpleStatus)
        // Legacy statusProgressBar reference (no longer used in layout)
        private val statusProgressBar: ProgressBar? = null
        
        // Shimmer status wrapper
        private val shimmerStatusWrapper: com.cyberflux.qwinai.components.ShimmerStatusWrapper? = 
            itemView.findViewById(R.id.shimmerStatusWrapper)

        // Web search sources UI
        private val webSearchSourcesContainer: LinearLayout? = itemView.findViewById(R.id.webSearchSourcesContainer)
        
        // File download container
        private val fileDownloadContainer: LinearLayout? = itemView.findViewById(R.id.fileDownloadContainer)
        
        // Code blocks container
        val codeBlocksContainer: LinearLayout = itemView.findViewById(R.id.codeBlocksContainer)
        
        init {
            Timber.d("CODE_BLOCK_DEBUG: ViewHolder - codeBlocksContainer found: ${codeBlocksContainer != null}")
            if (codeBlocksContainer != null) {
                Timber.d("CODE_BLOCK_DEBUG: ViewHolder - Container ID: ${codeBlocksContainer.id}")
                Timber.d("CODE_BLOCK_DEBUG: ViewHolder - Container visibility: ${codeBlocksContainer.visibility}")
            }
        }

        // Thinking components
        private val thinkingContainer: LinearLayout? = itemView.findViewById(R.id.thinkingContainer)
        private val thinkingHeader: LinearLayout? = itemView.findViewById(R.id.thinkingHeader)
        private val thinkingTitle: TextView? = itemView.findViewById(R.id.thinkingTitle)
        private val thinkingTimer: TextView? = itemView.findViewById(R.id.tvThinkingTimer)
        private val thinkingToggle: ImageButton? = itemView.findViewById(R.id.thinkingToggle)
        private val thinkingContentContainer: LinearLayout? = itemView.findViewById(R.id.thinkingContentContainer)
        private val thinkingContent: TextView? = itemView.findViewById(R.id.thinkingContent)
        private val thinkingIndicatorLine: View? = itemView.findViewById(R.id.thinkingIndicatorLine)

        // State tracking for streaming
        private var message: ChatMessage? = null
        private var currentStatusText: String = ""
        private var isUserInteracting = false

        // Add these for streaming support
        private var lastDisplayedContent: String = ""
        private var markdownDisabledDuringStreaming = false

        // Cache for processed Spannables
        private val spannableCache = LruCache<String, Spannable>(20)

        private val sourcesContainer: MaterialCardView? = itemView.findViewById(R.id.sourcesContainer)
        private val sourcesButton: LinearLayout? = itemView.findViewById(R.id.sourcesButton)
        private val sourcesIconContainer: LinearLayout? = itemView.findViewById(R.id.sourcesIconContainer)
        private var cachedWebSearchSources: List<WebSearchSource>? = null
        private val sourcesCountText: TextView? = itemView.findViewById(R.id.sourcesCountText)
        init {
            messageText.setTextIsSelectable(false)

            messageText.setOnLongClickListener { view ->
                handleLongPress(view)
            }

            itemView.setOnLongClickListener { view ->
                handleLongPress(view)
            }
        }

        // Main method to update content with message context
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun updateContent(content: String, msg: ChatMessage) {
            this.message = msg
            updateContentOnly(content)
        }

        // OPTIMIZED: Update content with smooth streaming, reduced processing
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun updateContentOnly(content: String) {
            // Don't update if user is interacting with the text
            if (isUserInteracting) {
                Timber.d("Skipping content update - user is interacting")
                return
            }
            
            // Simple redundancy check to prevent unnecessary updates
            if (lastDisplayedContent == content) {
                return
            }
            
            Timber.d("UpdateContentOnly - Content: ${content.take(100)}... (${content.length} chars), isGenerating: ${message?.isGenerating}")
            
            lastDisplayedContent = content
            
            try {
                if (content.isNotEmpty()) {
                    // During active streaming, use lightweight markdown processing for better performance
                    // but still support real-time code blocks
                    // Parse sources first
                    val sources = cachedWebSearchSources ?: message?.let { msg ->
                        if (msg.hasWebSearchResults && !msg.webSearchResults.isNullOrBlank()) {
                            parseWebSearchSources(msg.webSearchResults)
                        } else null
                    }
                    
                    // Use optimized Markwon processor for both streaming and complete content
                    if (message?.isGenerating == true) {
                        Timber.d("CODE_BLOCK_DEBUG: AiMessageViewHolder - Processing STREAMING content")
                        // Process with streaming optimization - full markdown but smooth
                        optimizedProcessor?.processMarkdownStreaming(
                            content = content,
                            textView = messageText,
                            onComplete = { processed ->
                                // Handle citations after processing if not streaming
                                messageText.movementMethod = LinkMovementMethod.getInstance()
                            },
                            isStreaming = true,
                            codeBlockContainer = codeBlocksContainer

                        )
                        // Code blocks are now handled inside processMarkdownStreaming
                    } else {
                        Timber.d("CODE_BLOCK_DEBUG: AiMessageViewHolder - Processing COMPLETE content")
                        // Process complete content with full quality
                        optimizedProcessor?.processMarkdownComplete(content, messageText, codeBlocksContainer)
                        // Code blocks are now handled inside processMarkdownComplete
                        
                        // Handle sources and citations for complete content
                        if (!sources.isNullOrEmpty()) {
                            // Apply citations immediately without delay to prevent text disappearing
                            try {
                                val currentText = messageText.text
                                if (currentText is android.text.Spanned && currentText.isNotEmpty()) {
                                    val finalMessage = processCitationsWithChips(android.text.SpannableStringBuilder(currentText), sources)
                                    // Only update if finalMessage is not empty to prevent text disappearing
                                    if (finalMessage.isNotEmpty()) {
                                        messageText.text = finalMessage
                                    }
                                    setupSourcesButton(sources)
                                } else {
                                    // If currentText is empty, just setup sources button without modifying text
                                    setupSourcesButton(sources)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error applying citations: ${e.message}")
                                // Ensure sources button is still shown on error
                                setupSourcesButton(sources)
                            }
                        }
                        
                        messageText.movementMethod = LinkMovementMethod.getInstance()
                    }
                    
                    // Reset markdown disabled flag if it was set
                    if (markdownDisabledDuringStreaming) {
                        markdownDisabledDuringStreaming = false
                    }
                    
                    Timber.d("Content update complete - final text length: ${messageText.text.length}")
                } else {
                    Timber.w("Content is empty - not updating")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating content: ${e.message}")
                messageText.text = content // Fallback to raw content
            }
        }
        
        // Lightweight markdown processing for streaming with real-time code blocks
        private fun processLightMarkdown(content: String): SpannableStringBuilder {
            val spannable = SpannableStringBuilder(content)
            
            // Only apply basic formatting during streaming to reduce processing
            try {
                // STREAMING CODE BLOCKS - Process immediately, even if incomplete
                // REAL-TIME CODE BLOCKS: Process immediately when detected
                if (content.contains("```")) {
                    Timber.d("Processing real-time code blocks for content with ${content.length} chars")
                    processRealTimeCodeBlocks(spannable)
                }
                
                // Basic bold formatting **text**
                var pattern = Pattern.compile("\\*\\*([^*]+)\\*\\*")
                var matcher = pattern.matcher(spannable)
                val boldRanges = mutableListOf<Pair<Int, Int>>()
                while (matcher.find()) {
                    boldRanges.add(Pair(matcher.start(), matcher.end()))
                }
                
                // Apply bold spans (reverse order to preserve positions)
                for (range in boldRanges.reversed()) {
                    val text = spannable.substring(range.first + 2, range.second - 2)
                    spannable.replace(range.first, range.second, text)
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        range.first,
                        range.first + text.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                
                // Basic italic formatting *text*
                pattern = Pattern.compile("\\*([^*]+)\\*")
                matcher = pattern.matcher(spannable)
                val italicRanges = mutableListOf<Pair<Int, Int>>()
                while (matcher.find()) {
                    italicRanges.add(Pair(matcher.start(), matcher.end()))
                }
                
                // Apply italic spans (reverse order to preserve positions)
                for (range in italicRanges.reversed()) {
                    val text = spannable.substring(range.first + 1, range.second - 1)
                    spannable.replace(range.first, range.second, text)
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        range.first,
                        range.first + text.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                
                // Inline code formatting `code`
                pattern = Pattern.compile("`([^`\n]+)`")
                matcher = pattern.matcher(spannable)
                val codeRanges = mutableListOf<Pair<Int, Int>>()
                while (matcher.find()) {
                    codeRanges.add(Pair(matcher.start(), matcher.end()))
                }
                
                // Apply inline code spans (reverse order to preserve positions)
                for (range in codeRanges.reversed()) {
                    val text = spannable.substring(range.first + 1, range.second - 1)
                    spannable.replace(range.first, range.second, text)
                    spannable.setSpan(
                        android.text.style.TypefaceSpan("monospace"),
                        range.first,
                        range.first + text.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Add background for inline code
                    spannable.setSpan(
                        android.text.style.BackgroundColorSpan(Color.parseColor("#F5F5F5")),
                        range.first,
                        range.first + text.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error in light markdown processing")
            }
            
            return spannable
        }
        
        // NEW: Real-time code block processing - shows blocks immediately and fills content as it streams
        private fun processRealTimeCodeBlocks(spannable: SpannableStringBuilder) {
            try {
                // Aggressive pattern that catches both complete and incomplete code blocks
                val codeBlockPattern = Pattern.compile(
                    "(^|\\n)```(\\w*)(?:\\n|\\r\\n)?([\\s\\S]*?)(?:```|$)",
                    Pattern.MULTILINE or Pattern.DOTALL
                )
                val matcher = codeBlockPattern.matcher(spannable)
                val codeBlockRanges = mutableListOf<CodeBlockMatch>()
                
                while (matcher.find()) {
                    val fullMatch = matcher.group(0) ?: ""
                    val prefix = matcher.group(1) ?: ""
                    val language = matcher.group(2) ?: ""
                    val codeContent = matcher.group(3) ?: ""
                    val isComplete = fullMatch.endsWith("```")
                    
                    Timber.d("Real-time code block - lang: '$language', content: '${codeContent.take(30)}...', complete: $isComplete")
                    
                    // Process ANY code block detection - even incomplete ones for real-time display
                    if (fullMatch.contains("\n") || codeContent.isNotEmpty()) {
                        codeBlockRanges.add(
                            CodeBlockMatch(
                                start = matcher.start(),
                                end = matcher.end(),
                                prefixLength = prefix.length,
                                language = language,
                                content = codeContent,
                                isComplete = isComplete
                            )
                        )
                        Timber.d("Added real-time code block range: ${matcher.start()}-${matcher.end()}")
                    }
                }
                
                // Process code blocks in reverse order to preserve positions
                for (range in codeBlockRanges.reversed()) {
                    val start = range.start + range.prefixLength
                    val end = range.end
                    val content = range.content.trim()
                    
                    // Skip code block processing here - Markwon plugin handles it directly
                    // No styling needed as Markwon will process the original markdown
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing real-time code blocks")
            }
        }
        
        // Fallback method for basic styling when CodeBlockSpan fails
        private fun applyBasicCodeBlockStyling(
            spannable: SpannableStringBuilder,
            start: Int,
            end: Int,
            isComplete: Boolean
        ) {
            try {
                if (start >= 0 && end <= spannable.length && end > start) {
                    // Apply monospace font
                    spannable.setSpan(
                        android.text.style.TypefaceSpan("monospace"),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    // Different background for streaming vs complete
                    val backgroundColor = if (isComplete) "#F5F5F5" else "#F8F8FF"
                    spannable.setSpan(
                        android.text.style.BackgroundColorSpan(Color.parseColor(backgroundColor)),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    // Apply size and color
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(0.9f),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    val textColor = if (isComplete) "#333333" else "#555555"
                    spannable.setSpan(
                        android.text.style.ForegroundColorSpan(Color.parseColor(textColor)),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error applying basic code block styling: ${e.message}")
            }
        }
        
        // IMPROVED: Process code blocks with conservative pattern to prevent flickering during streaming
        private fun processStreamingCodeBlocks(spannable: SpannableStringBuilder) {
            try {
                // Conservative pattern that only catches well-formed code blocks to prevent flickering
                val codeBlockPattern = Pattern.compile(
                    "(^|\\n)```(\\w*)\\n([\\s\\S]*?)```",
                    Pattern.MULTILINE or Pattern.DOTALL
                )
                val matcher = codeBlockPattern.matcher(spannable)
                val codeBlockRanges = mutableListOf<CodeBlockMatch>()
                
                while (matcher.find()) {
                    val fullMatch = matcher.group(0) ?: ""
                    val prefix = matcher.group(1) ?: ""
                    val language = matcher.group(2) ?: ""
                    val codeContent = matcher.group(3) ?: ""
                    val isComplete = fullMatch.endsWith("```")
                    
                    Timber.d("Found code block - lang: '$language', content: '${codeContent.take(50)}...', complete: $isComplete")
                    
                    // Only process complete code blocks to prevent flickering during streaming
                    if (isComplete && codeContent.trim().isNotEmpty()) {
                        codeBlockRanges.add(
                            CodeBlockMatch(
                                start = matcher.start(),
                                end = matcher.end(),
                                prefixLength = prefix.length,
                                language = language,
                                content = codeContent,
                                isComplete = isComplete
                            )
                        )
                        Timber.d("Added code block range: ${matcher.start()}-${matcher.end()}")
                    }
                }
                
                // Process code blocks in reverse order to preserve positions
                for (range in codeBlockRanges.reversed()) {
                    val start = range.start + range.prefixLength
                    val end = range.end
                    
                    // Create replacement content
                    val lineBreak = if (range.prefixLength > 0) "\n" else ""
                    val formattedContent = lineBreak + range.content
                    
                    spannable.replace(start, end, formattedContent)
                    
                    // Apply styling to the code content immediately
                    val contentStart = start + lineBreak.length
                    val contentEnd = contentStart + range.content.length
                    
                    // FIXED: Validate range before applying spans
                    if (contentEnd > contentStart && contentStart >= 0 && contentEnd <= spannable.length) {
                        Timber.d("Applying code block styling to range $contentStart-$contentEnd")
                        
                        try {
                            // IMMEDIATE STYLING: Apply all spans right away for instant visual feedback
                            spannable.setSpan(
                                android.text.style.TypefaceSpan("monospace"),
                                contentStart,
                                contentEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            // Apply consistent styling for complete code blocks
                            val backgroundColor = "#F5F5F5"
                            spannable.setSpan(
                                android.text.style.BackgroundColorSpan(Color.parseColor(backgroundColor)),
                                contentStart,
                                contentEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            // Apply monospace font
                            spannable.setSpan(
                                android.text.style.RelativeSizeSpan(0.9f),
                                contentStart,
                                contentEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            // Apply consistent text color
                            spannable.setSpan(
                                android.text.style.ForegroundColorSpan(Color.parseColor("#333333")),
                                contentStart,
                                contentEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            Timber.d("Applied complete code block styling with background $backgroundColor")
                        } catch (e: Exception) {
                            Timber.e(e, "Error applying code block styling to range $contentStart-$contentEnd")
                        }
                    } else {
                        Timber.d("Skipping code block styling for invalid range $contentStart-$contentEnd (spannable length: ${spannable.length})")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing streaming code blocks")
            }
        }
        
        // Data class for code block matching
               // In ChatAdapter.kt - Update processCitationsWithChips
        private fun processCitationsWithChips(
            spannable: SpannableStringBuilder,
            sources: List<WebSearchSource>
        ): SpannableStringBuilder {
            val text = spannable.toString()
            val newSpannable = SpannableStringBuilder()

            // Pattern to find [1], [2], etc.
            val citationPattern = Pattern.compile("\\[(\\d+)\\]")
            val matcher = citationPattern.matcher(text)

            var lastEnd = 0

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val citationNumber = matcher.group(1)?.toIntOrNull() ?: continue
                val sourceIndex = citationNumber - 1

                // Add text before citation
                if (start > lastEnd) {
                    // Preserve existing spans from the original spannable
                    newSpannable.append(spannable.subSequence(lastEnd, start))
                }

                if (sourceIndex in sources.indices) {
                    val source = sources[sourceIndex]

                    // Add a space before the chip
                    newSpannable.append(" ")

                    // Create citation chip
                    val chipStart = newSpannable.length
                    val chipText = source.shortDisplayName.takeIf { it.isNotEmpty() } ?: "Source"
                    newSpannable.append(chipText)
                    val chipEnd = newSpannable.length

                    // Add clickable span that opens the SPECIFIC URL, not just the domain
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            try {
                                // Set interaction flag to prevent text updates
                                isUserInteracting = true
                                
                                // Open the specific URL from the search result
                                openUrl(source.url)
                                
                                // Reset interaction flag after a delay
                                Handler(Looper.getMainLooper()).postDelayed({
                                    isUserInteracting = false
                                }, 1000)
                            } catch (e: Exception) {
                                Timber.e(e, "Error opening citation URL: ${source.url}")
                                isUserInteracting = false
                            }
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                            ds.color = ContextCompat.getColor(itemView.context, R.color.link_color)
                        }
                    }

                    newSpannable.setSpan(
                        clickableSpan,
                        chipStart,
                        chipEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    // Add rounded background chip style
                    val backgroundSpan = RoundedBackgroundSpan(
                        backgroundColor = ContextCompat.getColor(itemView.context, R.color.selected_item_background),
                        textColor = ContextCompat.getColor(itemView.context, R.color.source_url_color),
                        cornerRadius = 12f.dp(itemView.context),
                        paddingHorizontal = 12f.dp(itemView.context),
                        paddingVertical = 4f.dp(itemView.context)
                    )

                    newSpannable.setSpan(
                        backgroundSpan,
                        chipStart,
                        chipEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    // Add a space after the chip
                    newSpannable.append(" ")
                } else {
                    // Keep original citation if source not found
                    newSpannable.append(text.substring(start, end))
                }

                lastEnd = end
            }

            // Add remaining text
            if (lastEnd < text.length) {
                newSpannable.append(spannable.subSequence(lastEnd, text.length))
            }

            // Ensure we never return empty text - if something went wrong, return original
            return if (newSpannable.isEmpty() && spannable.isNotEmpty()) {
                Timber.w("processCitationsWithChips resulted in empty text, returning original")
                spannable
            } else {
                newSpannable
            }
        }
        // In ChatAdapter.kt - Update the updateLoadingState method in AiMessageViewHolder
        fun updateLoadingState(message: ChatMessage) {
            // Determine new state with improved text indicators
            val (newText, newColor, shouldShow) = when {
                message.isWebSearchActive -> {
                    Triple("Searching web", ContextCompat.getColor(itemView.context, R.color.web_search_indicator), true)
                }
                message.isGenerating && !message.isWebSearchActive -> {
                    Triple("Generating response", ContextCompat.getColor(itemView.context, R.color.text_secondary), true)
                }
                message.isLoading -> {
                    Triple("Processing", ContextCompat.getColor(itemView.context, R.color.text_secondary), true)
                }
                else -> {
                    Triple("", Color.TRANSPARENT, false)
                }
            }

            // Custom status overrides with fallback
            val finalText = message.initialIndicatorText.takeIf { !it.isNullOrBlank() } ?: newText
            val finalColor = message.initialIndicatorColor ?: newColor

            // Always keep the persistent indicator container visible for smooth transitions
            persistentIndicatorContainer?.visibility = View.VISIBLE
            
            if (shouldShow && finalText.isNotEmpty()) {
                // Show persistent loading indicator with smooth color transition
                persistentLoadingIndicator?.show(finalColor)
                
                // Show status text container
                simpleStatusContainer?.visibility = View.VISIBLE
                
                // Update shimmer status wrapper if available
                if (shimmerStatusWrapper != null) {
                    shimmerStatusWrapper.setStatus(finalText, finalColor, true)
                    tvSimpleStatus?.visibility = View.GONE
                } else {
                    // Fallback to traditional status display
                    tvSimpleStatus?.apply {
                        text = finalText
                        setTextColor(finalColor)
                        alpha = 1f
                        visibility = View.VISIBLE
                    }
                }
                
            } else {
                // Hide loading indicator smoothly but keep container visible
                persistentLoadingIndicator?.hide()
                
                // Hide status text but keep the container structure
                if (shimmerStatusWrapper != null) {
                    shimmerStatusWrapper.hideStatus()
                }
                simpleStatusContainer?.visibility = View.GONE
            }

        }

        // ULTRA-OPTIMIZED: Direct loading state updates for maximum streaming performance
        fun updateLoadingStateDirect(
            message: ChatMessage, 
            customStatusText: String? = null, 
            customStatusColor: Int? = null
        ) {
            try {
                // Direct UI updates without triggering adapter changes
                when {
                    message.isWebSearchActive -> {
                        val finalColor = customStatusColor ?: Color.parseColor("#2563EB")
                        val finalText = customStatusText ?: "Searching web"
                        
                        persistentLoadingIndicator?.show(finalColor)
                        
                        shimmerStatusWrapper?.setStatus(finalText, finalColor, true)
                        
                        simpleStatusContainer?.visibility = View.VISIBLE
                    }
                    
                    message.isGenerating -> {
                        val finalColor = customStatusColor ?: Color.parseColor("#757575")
                        val finalText = customStatusText ?: "Generating response"
                        
                        persistentLoadingIndicator?.show(finalColor)
                        
                        shimmerStatusWrapper?.setStatus(finalText, finalColor, true)
                        
                        simpleStatusContainer?.visibility = View.VISIBLE
                    }
                    
                    else -> {
                        // Hide all loading indicators directly
                        persistentLoadingIndicator?.hide()
                        shimmerStatusWrapper?.hideStatus()
                        simpleStatusContainer?.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in direct loading state update: ${e.message}")
            }
        }

        // ULTRA-OPTIMIZED: Direct web search results update
        fun updateWebSearchResults(message: ChatMessage) {
            try {
                if (message.hasWebSearchResults && !message.webSearchResults.isNullOrEmpty()) {
                    // Parse and display search results directly
                    try {
                        val searchResults = Gson().fromJson<List<WebSearchSource>>(
                            message.webSearchResults,
                            object : TypeToken<List<WebSearchSource>>() {}.type
                        )
                        
                        if (searchResults?.isNotEmpty() == true) {
                            // Update sources UI directly
                            sourcesButton?.visibility = View.VISIBLE
                            sourcesContainer?.visibility = View.VISIBLE
                            sourcesCountText?.text = "${searchResults.size}"
                            
                            sourcesButton?.setOnClickListener {
                                try {
                                    val bottomSheet = SourcesBottomSheet(itemView.context, searchResults)
                                    bottomSheet.show()
                                } catch (e: Exception) {
                                    Timber.e(e, "Error showing sources: ${e.message}")
                                    // Fallback - just log the sources
                                    Timber.d("Web search sources: ${searchResults.size} results")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing web search results: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in web search results update: ${e.message}")
            }
        }
        
        private fun handleLongPress(view: View): Boolean {
            val currentMessage = message
            if (currentMessage != null) {
                val context = view.context
                if (context is MainActivity) {
                    context.selectedMessage = currentMessage
                    context.selectedUserMessage = null

                    HapticManager.quickFeedback(context)
                    showAiMessagePopupMenu(context, view, currentMessage)
                    Timber.d("Long press detected on AI message: ${currentMessage.id}")
                    return true
                }
            }
            return false
        }

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun bind(message: ChatMessage) {
            Timber.d("🔴 BIND - Message: ${message.id}, model: ${message.modelId}, " +
                    "isGenerating: ${message.isGenerating}")

            resetAllViews()
            this.message = message
            
            // Initialize loading state immediately after reset to ensure persistent indicators are shown
            updateLoadingState(message)
            if (message.hasWebSearchResults && !message.webSearchResults.isNullOrBlank()) {
                cachedWebSearchSources = parseWebSearchSources(message.webSearchResults)
                Timber.d("Cached ${cachedWebSearchSources?.size} web search sources")
            }
            messageText.setTextIsSelectable(false)

            val modelId = message.modelId ?: message.aiModel ?: currentModelId
            val currentPosition = bindingAdapterPosition
            val latestPosition = getLatestAiMessagePosition(message.conversationId)
            
            // Fallback if bindingAdapterPosition is not available
            val actualPosition = if (currentPosition == RecyclerView.NO_POSITION) {
                currentList.indexOfFirst { it.id == message.id }
            } else {
                currentPosition
            }
            
            val isLatestAiMessage = (actualPosition == latestPosition && actualPosition != -1)
            
            Timber.d("🔴 POSITION: bindingPos=$currentPosition, actualPos=$actualPosition, latestPos=$latestPosition, isLatest=$isLatestAiMessage")
            
            // Enhanced debugging for AI messages only
            if (!message.isUser) {
                Timber.d("🔴 BIND AI: messageId=${message.id.take(8)}, actualPos=$actualPosition, latestPos=$latestPosition, isLatest=$isLatestAiMessage")
                Timber.d("🔴 BIND AI: isGenerating=${message.isGenerating}, totalVersions=${message.totalVersions}")
                
                // Show all AI messages in this conversation with detailed info
                val aiMessages = currentList.mapIndexed { index, msg -> 
                    if (!msg.isUser && msg.conversationId == message.conversationId) {
                        "pos=$index, id=${msg.id.take(8)}, isGenerating=${msg.isGenerating}, isLatest=${index == latestPosition}"
                    } else null
                }.filterNotNull()
                
                Timber.d("🔴 BIND AI: All AI messages: $aiMessages")
            }
            val isGenerating = message.isGenerating

            setModelIcon(modelIconView, modelId)

            if (message.hasWebSearchResults && !message.webSearchResults.isNullOrBlank() && !message.isGenerating) {
                showWebSearchSources(message.webSearchResults)
            } else {
                hideWebSearchSources()
            }
            
            // Handle file download cards
            if (message.hasGeneratedFile && !message.isGenerating) {
                showFileDownloadCard(message)
            } else {
                hideFileDownloadCard()
            }

            when {
                isGenerating -> {
                    // Hide all controls during generation
                    buttonContainer.visibility = View.GONE
                    aiNavigationControls?.visibility = View.GONE

                    // Loading state already handled by updateLoadingState above
                    // Custom indicator override if specified
                    if (message.initialIndicatorText != null && message.initialIndicatorColor != null) {
                        showIndicatorImmediately(message.initialIndicatorText, message.initialIndicatorColor)
                    }

                    updateMessageContentDirectly(message)
                }
                else -> {
                    // Loading state already handled by updateLoadingState above
                    // Only update the content
                    updateMessageContentDirectly(message)
                    
                    // Show buttons based on message state and position
                    handleButtonVisibility(message, isLatestAiMessage)
                    
                    // Setup navigation controls with proper visibility
                    handleNavigationControls(message, isGenerating)
                    
                    // Setup button listeners
                    setupButtonListeners(message)
                    setupAudioButton(message)
                }
            }

            messageText.visibility = View.VISIBLE
            messageText.alpha = 1f

            timestampText.text = formatTimestamp(message.timestamp)

            // Handle thinking functionality
            setupThinkingComponents(message)

            // Button handling is now done in the when statement above
        }

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun updateMessageContentDirectly(message: ChatMessage) {
            this.message = message

            try {
                val contentToDisplay = message.getCurrentVersionText()

                if (contentToDisplay.isNotEmpty()) {
                    val processedMessage = processMarkdown(contentToDisplay, messageText)
                    
                    // Process web search sources if available
                    if (message.hasWebSearchResults && !message.webSearchResults.isNullOrBlank()) {
                        cachedWebSearchSources = parseWebSearchSources(message.webSearchResults)
                        cachedWebSearchSources?.let { sources ->
                            try {
                                val finalMessage = processCitationsWithChips(processedMessage, sources)
                                // Only update if finalMessage is not empty to prevent text disappearing
                                if (finalMessage.isNotEmpty()) {
                                    messageText.text = finalMessage
                                } else {
                                    messageText.text = processedMessage
                                }
                                setupSourcesButton(sources)
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing citations in updateMessageContentDirectly: ${e.message}")
                                messageText.text = processedMessage
                                setupSourcesButton(sources)
                            }
                        } ?: run {
                            messageText.text = processedMessage
                        }
                    } else {
                        messageText.text = processedMessage
                    }
                    
                    messageText.movementMethod = LinkMovementMethod.getInstance()
                    messageText.visibility = View.VISIBLE
                    messageText.alpha = 1f

                    Timber.d("📝 Direct content update: ${contentToDisplay.length} chars")
                }

            } catch (e: Exception) {
                Timber.e(e, "Error in direct content update: ${e.message}")
                messageText.text = message.message
                messageText.visibility = View.VISIBLE
            }
        }

        // In ChatAdapter.kt - Update handleFinalUpdate in AiMessageViewHolder
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun handleFinalUpdate() {
            message?.let { msg ->
                // Ensure we have the latest content and web search results
                updateMessageContentDirectly(msg)

                // Re-parse and cache web search sources IMMEDIATELY
                if (msg.hasWebSearchResults && !msg.webSearchResults.isNullOrBlank()) {
                    cachedWebSearchSources = parseWebSearchSources(msg.webSearchResults)
                    // Show sources immediately without delay
                    setupSourcesButton(cachedWebSearchSources!!)
                    showWebSearchSources(msg.webSearchResults)
                }

                // IMPORTANT: Ensure citations are processed and persisted
                if (!cachedWebSearchSources.isNullOrEmpty() && msg.message.isNotEmpty()) {
                    try {
                        val processedMessage = processMarkdown(msg.message, messageText)
                        val finalMessage = processCitationsWithChips(processedMessage, cachedWebSearchSources!!)
                        // Only update if finalMessage is not empty to prevent text disappearing
                        if (finalMessage.isNotEmpty()) {
                            messageText.text = finalMessage
                            messageText.movementMethod = LinkMovementMethod.getInstance()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing citations in handleFinalUpdate: ${e.message}")
                        // Fallback to original message text without citations
                        messageText.text = processMarkdown(msg.message, messageText)
                        messageText.movementMethod = LinkMovementMethod.getInstance()
                    }
                }

                // COMMENTED OUT: This was interfering with handleButtonVisibility
                // val isLatestMessage = callbacks?.isLatestAiMessage(msg.id) ?: false
                // buttonContainer.visibility = if (isLatestMessage) View.VISIBLE else View.GONE
                
                // Clean up loading indicators when generation is truly complete
                if (!msg.isGenerating && !msg.isLoading && !msg.isWebSearchActive) {
                    persistentIndicatorContainer?.visibility = View.GONE
                    persistentLoadingIndicator?.hide()
                }
                
                Timber.d("🔴 handleFinalUpdate: NOT setting buttonContainer visibility for ${msg.id.take(8)}")
            }
        }
        
        /**
         * Enhanced final processing that preserves existing formatting while completing code blocks
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun forceFinalCodeBlockProcessing() {
            try {
                // Get content from the current TextView which may already have spans
                val currentContent = messageText.text
                val contentString = currentContent.toString()
                
                Timber.d("ForceFinalCodeBlockProcessing - Content: ${contentString.take(100)}... (${contentString.length} chars)")
                
                if (contentString.contains("```")) {
                    Timber.d("Enhanced final processing for content with code blocks")
                    
                    // Always process with enhanced markdown to ensure CodeBlockSpans are properly applied
                    val processedMessage = if (currentContent is SpannableStringBuilder) {
                        // Check if we already have CodeBlockSpans
                        val hasCodeBlockSpans = currentContent.getSpans(0, currentContent.length, CodeBlockSpan::class.java).isNotEmpty()
                        
                        if (hasCodeBlockSpans) {
                            Timber.d("Content already has CodeBlockSpans - keeping existing")
                            currentContent
                        } else {
                            Timber.d("Content has spans but no CodeBlockSpans - enhancing")
                            enhanceExistingCodeBlocks(currentContent)
                            currentContent
                        }
                    } else {
                        Timber.d("Content is plain text - processing with enhanced markdown")
                        processEnhancedMarkdown(contentString)
                    }
                    
                    // Apply the processed message
                    if (processedMessage.toString().isNotEmpty()) {
                        messageText.text = processedMessage
                        Timber.d("Applied processed message with ${processedMessage.length} chars")
                    } else {
                        Timber.w("Processed message is empty - keeping original content")
                        return
                    }
                    
                    // Apply citations if available
                    cachedWebSearchSources?.let { sources ->
                        if (sources.isNotEmpty()) {
                            val finalMessage = processCitationsWithChips(processedMessage, sources)
                            messageText.text = finalMessage
                            messageText.movementMethod = LinkMovementMethod.getInstance()
                        }
                    }
                    
                    Timber.d("Completed enhanced final processing - final content: ${messageText.text.length} chars")
                } else {
                    Timber.d("No code blocks detected - applying standard processing")
                    // Still process other markdown elements that might have been missed
                    val processedMessage = if (currentContent is SpannableStringBuilder) {
                        currentContent
                    } else {
                        processEnhancedMarkdown(contentString)
                    }
                    
                    if (processedMessage.toString().isNotEmpty()) {
                        messageText.text = processedMessage
                    }
                }
                
                // Ensure movement method is set for final content
                if (message?.isGenerating != true) {
                    messageText.movementMethod = LinkMovementMethod.getInstance()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error in enhanced final processing: ${e.message}")
                // Keep existing content on error
            }
        }
        
        /**
         * Enhanced markdown processing that preserves streaming formatting and adds missing elements
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun processEnhancedMarkdown(content: String): SpannableStringBuilder {
            return try {
                val spannable = SpannableStringBuilder(content)
                
                // Check if content already has spans applied (from streaming)
                val hasExistingSpans = spannable.getSpans(0, spannable.length, Any::class.java).isNotEmpty()
                
                // Process in order of priority to maintain formatting
                
                // 1. Process code blocks - use different approach if spans already exist
                if (content.contains("```")) {
                    if (hasExistingSpans) {
                        // Content already processed during streaming, just enhance it
                        enhanceExistingCodeBlocks(spannable)
                    } else {
                        // Fresh processing needed
                        processRealTimeCodeBlocks(spannable)
                    }
                }
                
                // 2. Process inline code (backticks) - only if not already processed
                if (!hasExistingSpans || !hasInlineCodeSpans(spannable)) {
                    processInlineCode(spannable)
                }
                
                // 3. Process text formatting (bold/italic) - only if not already processed
                if (!hasExistingSpans || !hasTextFormattingSpans(spannable)) {
                    processTextFormattingEnhanced(spannable)
                }
                
                // 4. Process headers
                processHeaders(spannable)
                
                // 5. Process lists
                processLists(spannable)
                
                // 6. Process links
                processLinks(spannable)
                
                spannable
            } catch (e: Exception) {
                Timber.e(e, "Error in enhanced markdown processing: ${e.message}")
                SpannableStringBuilder(content) // Fallback to original content
            }
        }
        
        // Helper method to enhance existing code blocks without replacing content
        private fun enhanceExistingCodeBlocks(spannable: SpannableStringBuilder) {
            try {
                // Check for existing CodeBlockSpan instances first
                val existingCodeBlockSpans = spannable.getSpans(0, spannable.length, CodeBlockSpan::class.java)
                
                if (existingCodeBlockSpans.isNotEmpty()) {
                    Timber.d("Found ${existingCodeBlockSpans.size} existing CodeBlockSpan instances - no enhancement needed")
                    return // CodeBlockSpans are already properly rendered
                }
                
                // Find existing TypefaceSpan("monospace") spans that indicate basic code block styling
                val monospaceSpans = spannable.getSpans(0, spannable.length, android.text.style.TypefaceSpan::class.java)
                    .filter { it.family == "monospace" }
                
                if (monospaceSpans.isEmpty()) {
                    return // No code blocks to enhance
                }
                
                Timber.d("Found ${monospaceSpans.size} basic styled code blocks - enhancing to CodeBlockSpan")
                
                // Convert basic styling to proper CodeBlockSpan
                for (span in monospaceSpans) {
                    val start = spannable.getSpanStart(span)
                    val end = spannable.getSpanEnd(span)
                    
                    if (start >= 0 && end <= spannable.length && end > start) {
                        try {
                            // Extract the code content
                            val codeContent = spannable.substring(start, end)
                            
                            // Try to determine language from context (look for ```lang pattern before this span)
                            var language = ""
                            val beforeSpan = if (start > 10) spannable.substring(start - 10, start) else spannable.substring(0, start)
                            val langMatch = Pattern.compile("```(\\w+)").matcher(beforeSpan)
                            if (langMatch.find()) {
                                language = langMatch.group(1) ?: ""
                            }
                            
                            // Remove old spans
                            spannable.removeSpan(span)
                            spannable.getSpans(start, end, android.text.style.BackgroundColorSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            spannable.getSpans(start, end, android.text.style.ForegroundColorSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            spannable.getSpans(start, end, android.text.style.RelativeSizeSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            
                            // Skip code block processing - Markwon plugin handles it directly
                            Timber.d("Skipping code block processing - handled by Markwon plugin (lang: '$language')")
                        } catch (e: Exception) {
                            Timber.e(e, "Error enhancing code block span: ${e.message}")
                            // Keep the basic styling if CodeBlockSpan enhancement fails
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error enhancing existing code blocks: ${e.message}")
            }
        }
        
        // Helper to check if inline code spans exist
        private fun hasInlineCodeSpans(spannable: SpannableStringBuilder): Boolean {
            val monospaceSpans = spannable.getSpans(0, spannable.length, android.text.style.TypefaceSpan::class.java)
                .filter { it.family == "monospace" }
            val backgroundSpans = spannable.getSpans(0, spannable.length, android.text.style.BackgroundColorSpan::class.java)
            return monospaceSpans.isNotEmpty() && backgroundSpans.isNotEmpty()
        }
        
        // Helper to check if text formatting spans exist
        private fun hasTextFormattingSpans(spannable: SpannableStringBuilder): Boolean {
            val styleSpans = spannable.getSpans(0, spannable.length, android.text.style.StyleSpan::class.java)
            return styleSpans.isNotEmpty()
        }
        
        // Enhanced text formatting that preserves existing spans
        private fun processTextFormattingEnhanced(spannable: SpannableStringBuilder) {
            try {
                // Bold text - **text** or __text__
                var pattern = Pattern.compile("\\*\\*([^*\n]+?)\\*\\*")
                var matcher = pattern.matcher(spannable)
                val boldRanges = mutableListOf<Pair<Int, Int>>()
                
                while (matcher.find()) {
                    val start = matcher.start(1) // Start of content inside **
                    val end = matcher.end(1)     // End of content inside **
                    boldRanges.add(Pair(start, end))
                }
                
                // Apply bold spans in reverse order with validation
                for ((start, end) in boldRanges.reversed()) {
                    if (start >= 0 && end > start && start < spannable.length && end <= spannable.length) {
                        try {
                            spannable.setSpan(
                                StyleSpan(Typeface.BOLD),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Error applying bold span to range $start-$end")
                        }
                    }
                }
                
                // Replace **text** with text (remove markers but keep formatting)
                val boldPattern = Pattern.compile("\\*\\*([^*\n]+?)\\*\\*")
                val boldMatcher = boldPattern.matcher(spannable)
                val replacements = mutableListOf<Pair<Int, String>>()
                
                while (boldMatcher.find()) {
                    replacements.add(Pair(boldMatcher.start(), boldMatcher.group(1) ?: ""))
                }
                
                // Apply replacements in reverse order
                for ((start, replacement) in replacements.reversed()) {
                    val end = spannable.toString().indexOf("**", start + 2) + 2
                    if (end > start && end <= spannable.length) {
                        spannable.replace(start, end, replacement)
                    }
                }
                
                // Similar process for italic text
                processItalicText(spannable)
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing enhanced text formatting: ${e.message}")
            }
        }
        
        private fun processItalicText(spannable: SpannableStringBuilder) {
            try {
                // Italic text - *text* (but not **text**)
                val pattern = Pattern.compile("(?<!\\*)\\*([^*\n]+?)\\*(?!\\*)")
                val matcher = pattern.matcher(spannable)
                val italicRanges = mutableListOf<Pair<Int, Int>>()
                
                while (matcher.find()) {
                    val start = matcher.start(1)
                    val end = matcher.end(1)
                    italicRanges.add(Pair(start, end))
                }
                
                // Apply italic spans with validation
                for ((start, end) in italicRanges.reversed()) {
                    if (start >= 0 && end > start && start < spannable.length && end <= spannable.length) {
                        try {
                            spannable.setSpan(
                                StyleSpan(Typeface.ITALIC),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Error applying italic span to range $start-$end")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing italic text: ${e.message}")
            }
        }
        
        private fun processInlineCode(spannable: SpannableStringBuilder) {
            try {
                val pattern = Pattern.compile("`([^`\n]+?)`")
                val matcher = pattern.matcher(spannable)
                val codeRanges = mutableListOf<Pair<Int, Int>>()
                
                while (matcher.find()) {
                    val start = matcher.start(1)
                    val end = matcher.end(1)
                    codeRanges.add(Pair(start, end))
                }
                
                // Apply inline code spans with validation
                for ((start, end) in codeRanges.reversed()) {
                    if (start >= 0 && end > start && start < spannable.length && end <= spannable.length) {
                        try {
                            spannable.setSpan(
                                android.text.style.TypefaceSpan("monospace"),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            spannable.setSpan(
                                android.text.style.BackgroundColorSpan(Color.parseColor("#F0F0F0")),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Error applying inline code span to range $start-$end")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing inline code: ${e.message}")
            }
        }
        
        private fun processHeaders(spannable: SpannableStringBuilder) {
            try {
                // Process headers # ## ### etc.
                val pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE)
                val matcher = pattern.matcher(spannable)
                val headerRanges = mutableListOf<Triple<Int, Int, Int>>()
                
                while (matcher.find()) {
                    val level = matcher.group(1)?.length ?: 1
                    val start = matcher.start(2)
                    val end = matcher.end(2)
                    headerRanges.add(Triple(start, end, level))
                }
                
                // Apply header spans
                for ((start, end, level) in headerRanges.reversed()) {
                    if (start < spannable.length && end <= spannable.length && start < end) {
                        val size = when (level) {
                            1 -> 1.5f
                            2 -> 1.3f
                            3 -> 1.2f
                            else -> 1.1f
                        }
                        spannable.setSpan(
                            RelativeSizeSpan(size),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing headers: ${e.message}")
            }
        }
        
        private fun processLists(spannable: SpannableStringBuilder) {
            try {
                // Process bullet lists
                val bulletPattern = Pattern.compile("^[\\s]*[\\-\\*\\+]\\s+(.+)$", Pattern.MULTILINE)
                val bulletMatcher = bulletPattern.matcher(spannable)
                val listRanges = mutableListOf<Pair<Int, Int>>()
                
                while (bulletMatcher.find()) {
                    listRanges.add(Pair(bulletMatcher.start(), bulletMatcher.end()))
                }
                
                // Apply bullet formatting
                for ((start, end) in listRanges.reversed()) {
                    if (start < spannable.length && end <= spannable.length && start < end) {
                        spannable.setSpan(
                            BulletSpan(20, Color.parseColor("#666666")),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing lists: ${e.message}")
            }
        }
        
        private fun processLinks(spannable: SpannableStringBuilder) {
            try {
                // Process markdown links [text](url)
                val linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
                val matcher = linkPattern.matcher(spannable)
                val linkRanges = mutableListOf<Triple<Int, Int, String>>()
                
                while (matcher.find()) {
                    val start = matcher.start(1)
                    val end = matcher.end(1)
                    val url = matcher.group(2) ?: ""
                    linkRanges.add(Triple(start, end, url))
                }
                
                // Apply link spans
                for ((start, end, url) in linkRanges.reversed()) {
                    if (start < spannable.length && end <= spannable.length && start < end) {
                        val clickableSpan = object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    itemView.context.startActivity(intent)
                                } catch (e: Exception) {
                                    Timber.e(e, "Error opening link: $url")
                                }
                            }
                            
                            override fun updateDrawState(ds: TextPaint) {
                                super.updateDrawState(ds)
                                ds.color = ContextCompat.getColor(itemView.context, R.color.link_color)
                                ds.isUnderlineText = true
                            }
                        }
                        
                        spannable.setSpan(
                            clickableSpan,
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing links: ${e.message}")
            }
        }
        
        private fun setupThinkingComponents(message: ChatMessage) {
            // Check if this message should show thinking content
            val shouldShowThinking = message.hasThinkingProcess && 
                                   !message.thinkingProcess.isNullOrBlank() && 
                                   message.isThinkingActive
            
            if (shouldShowThinking) {
                // Show thinking container
                thinkingContainer?.visibility = View.VISIBLE
                thinkingContainer?.alpha = 1f
                
                // Set thinking content
                thinkingContent?.text = message.thinkingProcess
                
                // Setup thinking toggle functionality
                setupThinkingToggle(message)
                
                // Set thinking timer if available
                if (!message.thinkingDuration.isNullOrBlank()) {
                    thinkingTimer?.text = message.thinkingDuration
                    thinkingTimer?.visibility = View.VISIBLE
                } else {
                    thinkingTimer?.visibility = View.GONE
                }
                
                // Set thinking indicator color based on model
                val modelId = message.modelId ?: message.aiModel ?: currentModelId
                val thinkingColor = when {
                    modelId.contains("claude", ignoreCase = true) -> 
                        ContextCompat.getColor(itemView.context, R.color.thinking_indicator_claude)
                    modelId.contains("gpt", ignoreCase = true) -> 
                        ContextCompat.getColor(itemView.context, R.color.thinking_indicator_gpt)
                    else -> 
                        ContextCompat.getColor(itemView.context, R.color.thinking_indicator_default)
                }
                thinkingIndicatorLine?.setBackgroundColor(thinkingColor)
                
                Timber.d("Showing thinking content for message: ${message.id}")
            } else {
                // Hide thinking container
                thinkingContainer?.visibility = View.GONE
                thinkingContainer?.alpha = 0f
                
                Timber.d("Hiding thinking content for message: ${message.id}")
            }
        }
        
        private fun setupThinkingToggle(message: ChatMessage) {
            thinkingToggle?.setOnClickListener {
                val isExpanded = thinkingContentContainer?.visibility == View.VISIBLE
                
                if (isExpanded) {
                    // Collapse thinking content
                    thinkingContentContainer?.visibility = View.GONE
                    thinkingToggle?.rotation = 0f
                } else {
                    // Expand thinking content
                    thinkingContentContainer?.visibility = View.VISIBLE
                    thinkingToggle?.rotation = 180f
                }
                
                // Animate the toggle
                thinkingToggle?.animate()
                    ?.rotation(if (isExpanded) 0f else 180f)
                    ?.setDuration(200)
                    ?.start()
            }
            
            // Set initial state (collapsed by default)
            thinkingContentContainer?.visibility = View.GONE
            thinkingToggle?.rotation = 0f
        }
        
        fun updateAudioButtonState(messageId: String, isPlaying: Boolean) {
            btnAudio?.let { audioButton ->
                if (message?.id == messageId) {
                    audioButton.setImageResource(
                        if (isPlaying) R.drawable.ic_stop
                        else R.drawable.ic_play_arrow
                    )

                    audioButton.contentDescription = if (isPlaying) "Stop audio" else "Play audio"

                    if (isPlaying) {
                        audioButton.animate()
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(200)
                            .start()
                    } else {
                        audioButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .start()
                    }
                }
            }
        }

        private fun showIndicatorImmediately(indicatorText: String, indicatorColor: Int) {
            // Use shimmer status wrapper if available
            if (shimmerStatusWrapper != null) {
                shimmerStatusWrapper.setStatus(indicatorText, indicatorColor, true)
                // Show the container with progress bar but hide the TextView
                simpleStatusContainer?.apply {
                    visibility = View.VISIBLE
                    alpha = 1f
                    clearAnimation()
                }
                tvSimpleStatus?.visibility = View.GONE
                statusProgressBar?.apply {
                    visibility = View.VISIBLE
                    indeterminateTintList = ColorStateList.valueOf(indicatorColor)
                    alpha = 1f
                    clearAnimation()
                }
                return
            }

            // Fallback to traditional status indicator
            simpleStatusContainer?.apply {
                visibility = View.VISIBLE
                alpha = 1f
                clearAnimation()
            }

            tvSimpleStatus?.apply {
                visibility = View.VISIBLE
                text = indicatorText
                setTextColor(indicatorColor)
                alpha = 1f
                clearAnimation()
            }

            // Always keep the persistent indicator container visible
            persistentIndicatorContainer?.visibility = View.VISIBLE
            
            // Use persistent loading indicator instead of statusProgressBar
            persistentLoadingIndicator?.show(indicatorColor)
        }

        fun resetAllViews() {
            Timber.d("🔴 RESET: resetAllViews() called for message: ${message?.id?.take(8)}")
            
            // Reset shimmer status wrapper
            shimmerStatusWrapper?.hideStatus()
            
            simpleStatusContainer?.apply {
                visibility = View.GONE
                alpha = 0f
                clearAnimation()
            }

            tvSimpleStatus?.apply {
                text = ""
                clearAnimation()
            }

            // Keep persistent indicator container available - let updateLoadingState manage visibility
            persistentLoadingIndicator?.hide()

            webSearchSourcesContainer?.apply {
                visibility = View.GONE
                alpha = 0f
                clearAnimation()
            }

            messageText.apply {
                text = ""
                alpha = 1f
                visibility = View.VISIBLE
            }

            // Don't hide button container here - let handleButtonVisibility control it
            // buttonContainer.visibility = View.GONE
            
            // Instead, hide individual buttons
            btnCopy.visibility = View.GONE
            btnReload.visibility = View.GONE
            aiNavigationControls?.visibility = View.GONE
            
            // Reset thinking components
            thinkingContainer?.apply {
                visibility = View.GONE
                alpha = 0f
                clearAnimation()
            }
            thinkingContent?.text = ""
            thinkingTimer?.text = ""
            thinkingContentContainer?.visibility = View.GONE
            thinkingToggle?.rotation = 0f
            
            Timber.d("🔴 RESET: Hidden individual buttons and thinking components for message: ${message?.id?.take(8)}")
        }

        private fun hideSimpleStatusWithAnimation() {
            // Hide shimmer status wrapper immediately for instant transitions
            shimmerStatusWrapper?.hideStatus()
            
            if (simpleStatusContainer?.visibility != View.VISIBLE) return

            // Hide only the text status container
            simpleStatusContainer?.apply {
                visibility = View.GONE
                alpha = 1f // Reset alpha for next use
            }
            
            // Hide loading indicator but keep container available for next use
            persistentLoadingIndicator?.hide()
            
            // Don't hide the persistent container here - let updateLoadingState manage it
            // This allows smooth transitions between different loading states
            
            currentStatusText = ""
        }

        // Web search methods
        private fun showWebSearchSources(webSearchResults: String) {
            try {
                val sources = parseWebSearchSources(webSearchResults)
                if (sources.isEmpty()) {
                    hideWebSearchSources()
                    return
                }

                // Don't show the inline source cards anymore
                webSearchSourcesContainer?.visibility = View.GONE

                // Instead, show the sources button at the bottom
                setupSourcesButton(sources)

                Timber.d("Set up sources button with ${sources.size} sources")

            } catch (e: Exception) {
                Timber.e(e, "Error showing web search sources: ${e.message}")
                hideWebSearchSources()
            }
        }
        
        private fun parseWebSearchSources(webSearchResults: String): List<WebSearchSource> {
            val sources = mutableListOf<WebSearchSource>()

            try {
                if (webSearchResults.trim().startsWith("{") || webSearchResults.trim().startsWith("[")) {
                    try {
                        if (webSearchResults.trim().startsWith("[")) {
                            val jsonArray = JSONArray(webSearchResults)
                            for (i in 0 until jsonArray.length()) {
                                val result = jsonArray.getJSONObject(i)
                                val title = result.optString("title", "")
                                val url = result.optString("url", "")
                                val snippet = result.optString("snippet", result.optString("description", ""))
                                val displayLink = result.optString("displayLink", "").ifEmpty {
                                    // Extract domain from URL if displayLink is missing
                                    try {
                                        Uri.parse(url).host ?: url
                                    } catch (e: Exception) {
                                        url
                                    }
                                }

                                if (title.isNotEmpty() || url.isNotEmpty()) {
                                    sources.add(WebSearchSource(
                                        title = title,
                                        url = url,
                                        snippet = snippet,
                                        displayLink = displayLink,
                                        favicon = result.optString("favicon", null)
                                    ))
                                }
                            }
                        } else {
                            val jsonObject = JSONObject(webSearchResults)
                            if (jsonObject.has("results")) {
                                val resultsArray = jsonObject.getJSONArray("results")
                                for (i in 0 until resultsArray.length()) {
                                    val result = resultsArray.getJSONObject(i)
                                    val title = result.optString("title", "")
                                    val url = result.optString("url", "")
                                    val snippet = result.optString("snippet", result.optString("description", ""))
                                    val displayLink = result.optString("displayLink", "").ifEmpty {
                                        try {
                                            Uri.parse(url).host ?: url
                                        } catch (e: Exception) {
                                            url
                                        }
                                    }

                                    if (title.isNotEmpty() || url.isNotEmpty()) {
                                        sources.add(WebSearchSource(
                                            title = title,
                                            url = url,
                                            snippet = snippet,
                                            displayLink = displayLink,
                                            favicon = result.optString("favicon", null)
                                        ))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing JSON web search results: ${e.message}")
                    }
                }

                if (sources.isEmpty()) {
                    val lines = webSearchResults.split("\n")
                    for (line in lines) {
                        if (line.trim().isNotEmpty()) {
                            val parts = line.split("|")
                            if (parts.size >= 2) {
                                sources.add(WebSearchSource(
                                    title = parts[0].trim(),
                                    url = parts[1].trim(),
                                    snippet = if (parts.size >= 3) parts[2].trim() else ""
                                ))
                            } else {
                                val urlPattern = "(https?://[^\\s]+)".toRegex()
                                val urlMatches = urlPattern.findAll(line)

                                if (urlMatches.count() > 0) {
                                    val url = urlMatches.first().value
                                    val title = line.replace(url, "").trim()
                                    sources.add(WebSearchSource(title, url, ""))
                                } else {
                                    sources.add(WebSearchSource(line.trim(), "", ""))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing web search sources: ${e.message}")
            }

            return sources.take(10)
        }

        private fun hideWebSearchSources() {
            webSearchSourcesContainer?.animate()
                ?.alpha(0f)
                ?.setDuration(200)
                ?.withEndAction {
                    webSearchSourcesContainer?.visibility = View.GONE
                }
                ?.start()
        }


        private fun setupSourcesButton(sources: List<WebSearchSource>) {
            if (sources.isEmpty()) {
                sourcesContainer?.visibility = View.GONE
                return
            }
            
            sourcesContainer?.visibility = View.VISIBLE

            sourcesButton?.setOnClickListener {
                showExpandedSources(sources)
            }

            // Clear existing icons
            sourcesIconContainer?.removeAllViews()

            // Create overlapping favicon layout
            val iconSize = 24.dp(itemView.context)
            val overlap = 8.dp(itemView.context)

            sources.take(3).forEachIndexed { index, source ->
                val faviconContainer = FrameLayout(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        if (index > 0) {
                            marginStart = -overlap // Negative margin for overlap
                        }
                    }
                }

                // Background circle for each favicon
                val backgroundView = View(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
                    background = ContextCompat.getDrawable(itemView.context, R.drawable.favicon_circle_background)
                }
                faviconContainer.addView(backgroundView)

                // Favicon image
                val favicon = ImageView(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(iconSize - 4.dp(itemView.context), iconSize - 4.dp(itemView.context)).apply {
                        gravity = Gravity.CENTER
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                // Load favicon with proper URL
                val faviconUrl = source.favicon ?: try {
                    val domain = Uri.parse(source.url).host
                    "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                } catch (e: Exception) {
                    null
                }

                if (!faviconUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(faviconUrl)
                        .placeholder(R.drawable.ic_web)
                        .error(R.drawable.ic_web)
                        .circleCrop() // Make it circular
                        .into(favicon)
                } else {
                    favicon.setImageResource(R.drawable.ic_web)
                }

                faviconContainer.addView(favicon)

                // Add elevation for stacking effect
                faviconContainer.elevation = (3 - index).toFloat() * 2.dp(itemView.context)

                sourcesIconContainer?.addView(faviconContainer)
            }

            // Update text
            sourcesCountText?.text = if (sources.size > 3) {
                "+${sources.size - 3} more"
            } else {
                "${sources.size} sources"
            }
            
            // Force visibility and layout update
            sourcesContainer?.apply {
                visibility = View.VISIBLE
                alpha = 1f
                requestLayout()
            }
        }
        
        private fun showExpandedSources(sources: List<WebSearchSource>) {
            val context = itemView.context
            val bottomSheet = SourcesBottomSheet(context, sources)
            bottomSheet.show()
        }
        private fun openUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                itemView.context.startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "Error opening URL: $url")
                Toast.makeText(itemView.context, "Could not open link", Toast.LENGTH_SHORT).show()
            }
        }



        private fun handleButtonVisibility(message: ChatMessage, isLatestAiMessage: Boolean) {
            // Check if any message is generating (both global and individual message state)
            val isAnyMessageGenerating = currentList.any { it.isGenerating } || isGenerating
            val isThisMessageGenerating = message.isGenerating
            
            Timber.d("🔴 handleButtonVisibility: messageId=${message.id.take(8)}, isLatest=$isLatestAiMessage, anyGenerating=$isAnyMessageGenerating, thisGenerating=$isThisMessageGenerating, globalGenerating=$isGenerating")
            Timber.d("🔴 totalVersions=${message.totalVersions}")
            
            // Hide all buttons if any message is generating OR if this specific message is generating
            if (isAnyMessageGenerating || isThisMessageGenerating) {
                buttonContainer.visibility = View.GONE
                aiNavigationControls?.visibility = View.GONE
                Timber.d("🔴 Hidden all buttons due to generation - messageId=${message.id.take(8)}")
                return
            }
            
            // No generation happening - show appropriate buttons
            if (isLatestAiMessage) {
                // Latest AI message shows copy + reload buttons
                buttonContainer.visibility = View.VISIBLE
                btnCopy.visibility = View.VISIBLE
                btnReload.visibility = View.VISIBLE
                
                Timber.d("🔴 Showing copy + reload buttons for latest AI message")
                
                // Handle navigation controls for latest message
                handleNavigationControls(message, isThisMessageGenerating)
                
                // Force layout update to ensure buttons appear
                buttonContainer.requestLayout()
                
            } else {
                // Previous AI messages only show navigation if they have versions
                if (message.totalVersions > 1) {
                    buttonContainer.visibility = View.VISIBLE
                    btnCopy.visibility = View.GONE
                    btnReload.visibility = View.GONE
                    
                    // Handle navigation controls for previous message
                    handleNavigationControls(message, isThisMessageGenerating)
                    
                    Timber.d("🔴 Showing only navigation controls for previous AI message")
                } else {
                    // No versions and not latest = hide all buttons
                    buttonContainer.visibility = View.GONE
                    aiNavigationControls?.visibility = View.GONE
                    Timber.d("🔴 Hidden all buttons for previous AI message (no versions)")
                }
            }
        }

        private fun handleNavigationControls(message: ChatMessage, isGenerating: Boolean) {
            // Check if any message is generating (both global and individual message state)
            val isAnyMessageGenerating = currentList.any { it.isGenerating } || this@ChatAdapter.isGenerating
            
            // Find the latest AI message position for this conversation
            val latestAiMessagePosition = getLatestAiMessagePosition(message.conversationId)
            val currentPosition = bindingAdapterPosition
            val actualPosition = if (currentPosition == RecyclerView.NO_POSITION) {
                currentList.indexOfFirst { it.id == message.id }
            } else {
                currentPosition
            }
            val isLatestAiMessage = (actualPosition == latestAiMessagePosition)
            
            Timber.d("AI handleNavigationControls: totalVersions=${message.totalVersions}, isGenerating=$isGenerating, isAnyMessageGenerating=$isAnyMessageGenerating, isLatest=$isLatestAiMessage")
            
            // Show navigation controls if:
            // 1. No generation is happening (neither this message nor any other)
            // 2. AND either:
            //    - Message has multiple versions (for any AI message)
            //    - OR it's the latest AI message (even with single version)
            val shouldShowControls = !isGenerating && !isAnyMessageGenerating && 
                                   (message.totalVersions > 1 || isLatestAiMessage)
            
            if (shouldShowControls) {
                aiNavigationControls?.visibility = View.VISIBLE
                tvNavigationIndicator?.text = "<${message.versionIndex + 1}/${message.totalVersions}>"

                btnPrevious?.isEnabled = message.versionIndex > 0
                btnNext?.isEnabled = message.versionIndex < message.totalVersions - 1

                btnPrevious?.alpha = if (btnPrevious?.isEnabled == true) 1.0f else 0.3f
                btnNext?.alpha = if (btnNext?.isEnabled == true) 1.0f else 0.3f
                
                // Setup navigation listeners
                btnPrevious?.setOnClickListener { if (btnPrevious.isEnabled) onNavigate(message, -1) }
                btnNext?.setOnClickListener { if (btnNext.isEnabled) onNavigate(message, 1) }
                
                // Force layout update to ensure controls appear
                aiNavigationControls?.requestLayout()
                
                Timber.d("AI navigation controls shown: <${message.versionIndex + 1}/${message.totalVersions}> (isLatest=$isLatestAiMessage)")
            } else {
                aiNavigationControls?.visibility = View.GONE
                Timber.d("AI navigation controls hidden - totalVersions=${message.totalVersions}, isGenerating=$isGenerating, isAnyMessageGenerating=$isAnyMessageGenerating, isLatest=$isLatestAiMessage")
            }
        }

        private fun setupButtonListeners(message: ChatMessage) {
            btnCopy.setOnClickListener { onCopy(message.getCurrentVersionText()) }
            btnReload.setOnClickListener { onReload(message) }
            // Navigation listeners are set up in handleNavigationControls to avoid duplicates
        }

        private fun setupAudioButton(message: ChatMessage) {
            btnAudio?.let { audioButton ->
                val modelSupportsAudio = ModelValidator.supportsAudio(message.modelId ?: currentModelId)

                audioButton.visibility = if (modelSupportsAudio && !message.isGenerating)
                    View.VISIBLE else View.GONE

                if (modelSupportsAudio && !message.isGenerating) {
                    updateAudioButtonState(message.id, currentlyPlayingMessageId == message.id)

                    audioButton.setOnClickListener {
                        onAudioPlay(message)
                        HapticManager.quickFeedback(itemView.context)
                    }
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun processMarkdown(text: String, codeContainer: TextView): SpannableStringBuilder {
            // Try to use optimized processor first, fallback to old processor
            optimizedProcessor?.let { processor ->
                processor.processMarkdownComplete(text, codeContainer, null)
                return SpannableStringBuilder(codeContainer.text)
            }
            
            // Fallback to old processor if optimized one is not available
            val markdownProcessor = UltraFastStreamingProcessor(itemView.context)
            return markdownProcessor.processMarkdown(text)
        }

        private fun setModelIcon(imageView: ImageView, modelId: String) {
            val iconResource = ModelIconUtils.getIconResourceForModel(modelId)
            imageView.setImageResource(iconResource)
        }

        private fun getLatestAiMessagePosition(conversationId: String): Int {
            var latestPosition = -1
            
            // Find the last AI message in the list (not by timestamp, but by list order)
            for (i in currentList.size - 1 downTo 0) {
                val message = getItem(i)
                if (!message.isUser && message.conversationId == conversationId) {
                    latestPosition = i
                    break // First AI message we find from the end is the latest
                }
            }
            
            // Debug: Show AI messages in order
            val aiMessages = currentList.mapIndexed { index, message -> 
                if (!message.isUser && message.conversationId == conversationId) {
                    "pos=$index, id=${message.id.take(8)}, isGenerating=${message.isGenerating}"
                } else null
            }.filterNotNull()
            
            Timber.d("🔍 getLatestAiMessagePosition: conversationId=$conversationId, latestPosition=$latestPosition")
            Timber.d("🔍 AI messages: $aiMessages")
            return latestPosition
        }


        private fun showAiMessagePopupMenu(context: MainActivity, anchorView: View, message: ChatMessage) {
            try {
                val popupMenu = PopupMenu(context, anchorView)
                val isLatestMessage = callbacks?.isLatestAiMessage(message.id) ?: false
                val isGenerating = this@ChatAdapter.isGenerating

                if (isLatestMessage) {
                    popupMenu.menuInflater.inflate(R.menu.popup_ai_message, popupMenu.menu)
                } else {
                    popupMenu.menuInflater.inflate(R.menu.popup_ai_message_no_reload, popupMenu.menu)
                }

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_copy_message -> {
                            onCopy(message.message)
                            true
                        }
                        R.id.menu_reload_message -> {
                            if (!isGenerating) {
                                onReload(message)
                            } else {
                                Toast.makeText(context,
                                    "Please wait for current generation to complete",
                                    Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        R.id.menu_select_text -> {
                            context.openTextSelectionScreen(message.message, false)
                            true
                        }
                        else -> false
                    }
                }

                popupMenu.show()

            } catch (e: Exception) {
                Timber.e(e, "Error showing AI popup menu: ${e.message}")
                context.selectedMessage = message
                anchorView.showContextMenu()
            }
        }

        private fun showFileDownloadCard(message: ChatMessage) {
            fileDownloadContainer?.let { container ->
                try {
                    // Clear any existing cards
                    container.removeAllViews()
                    
                    // Create file generation result from message data
                    val fileResult = com.cyberflux.qwinai.tools.FileGenerationResult(
                        success = true,
                        filePath = message.generatedFileUri,
                        fileName = message.generatedFileName,
                        fileSize = message.generatedFileSize,
                        mimeType = getMimeTypeFromExtension(message.generatedFileType),
                        uri = message.generatedFileUri?.let { android.net.Uri.parse(it) }
                    )
                    
                    // Create and add the download card
                    val downloadCard = com.cyberflux.qwinai.ui.FileDownloadCard.createWithClickListener(
                        itemView.context,
                        fileResult
                    ) { result ->
                        // Handle download click
                        val downloadManager = com.cyberflux.qwinai.utils.FileDownloadManager(itemView.context)
                        downloadManager.openFile(result)
                    }
                    
                    container.addView(downloadCard)
                    container.visibility = View.VISIBLE
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error showing file download card: ${e.message}")
                    container.visibility = View.GONE
                }
            }
        }
        
        private fun hideFileDownloadCard() {
            fileDownloadContainer?.let { container ->
                container.removeAllViews()
                container.visibility = View.GONE
            }
        }
        
        private fun getMimeTypeFromExtension(fileType: String?): String {
            return when (fileType?.lowercase()) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "csv" -> "text/csv"
                "txt" -> "text/plain"
                "json" -> "application/json"
                "xml" -> "application/xml"
                else -> "application/octet-stream"
            }
        }
        
        // STREAMING CONTINUATION METHODS - Fix for background streaming issues
        
        /**
         * Update content directly for smooth streaming continuation
         * Prevents content jumping when re-entering active conversations
         */
        fun updateContentDirectly(content: String, processMarkdown: Boolean = true, isStreaming: Boolean = true) {
            try {
                // Prevent update if user is currently interacting
                if (isUserInteracting) return
                
                // Anti-flicker: Only update if content has meaningfully changed
                if (lastDisplayedContent == content) return
                
                lastDisplayedContent = content
                
                if (content.isNotEmpty()) {
                    if (processMarkdown) {
                        // Use streaming processor for smooth updates
                        if (isStreaming) {
                            optimizedProcessor?.processMarkdownStreaming(
                                content = content,
                                textView = messageText,
                                onComplete = { 
                                    messageText.movementMethod = LinkMovementMethod.getInstance()
                                },
                                isStreaming = true,
                                codeBlockContainer = codeBlocksContainer
                            )
                        } else {
                            optimizedProcessor?.processMarkdownComplete(content, messageText, codeBlocksContainer)
                        }
                    } else {
                        // Direct text update for maximum speed
                        messageText.text = content
                    }
                }
                
                Timber.v("✅ Direct content update: ${content.length} chars, streaming=$isStreaming")
                
            } catch (e: Exception) {
                Timber.e(e, "Error in direct content update: ${e.message}")
                // Fallback to simple text update
                messageText.text = content
            }
        }
        
        /**
         * Update loading state directly without adapter notifications
         */
        fun updateLoadingState(
            isGenerating: Boolean,
            isWebSearching: Boolean = false,
            customStatusText: String? = null,
            customStatusColor: Int? = null
        ) {
            try {
                // Update persistent indicator
                persistentLoadingIndicator?.let { indicator ->
                    when {
                        isWebSearching -> {
                            indicator.show(customStatusColor ?: Color.parseColor("#2563EB"))
                            indicator.visibility = View.VISIBLE
                        }
                        isGenerating -> {
                            indicator.show(customStatusColor ?: Color.parseColor("#757575"))
                            indicator.visibility = View.VISIBLE
                        }
                        else -> {
                            indicator.visibility = View.GONE
                        }
                    }
                }
                
                // Update simple status if available
                tvSimpleStatus?.let { statusText ->
                    if (isGenerating || isWebSearching) {
                        statusText.text = customStatusText ?: if (isWebSearching) "Searching web" else "Generating"
                        customStatusColor?.let { color ->
                            statusText.setTextColor(color)
                        }
                        simpleStatusContainer?.visibility = View.VISIBLE
                    } else {
                        simpleStatusContainer?.visibility = View.GONE
                    }
                }
                
                // Update button container visibility
                buttonContainer.visibility = if (isGenerating || isWebSearching) View.GONE else View.VISIBLE
                
                Timber.v("✅ Direct loading state update: generating=$isGenerating, webSearch=$isWebSearching")
                
            } catch (e: Exception) {
                Timber.e(e, "Error updating loading state: ${e.message}")
            }
        }

        fun cleanup() {
            message = null
            spannableCache.evictAll()
        }

        private fun Int.dp(context: Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }
    }

    // All other ViewHolders remain unchanged...
    inner class UserImageMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivImage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(message: ChatMessage) {
            Glide.with(itemView.context).load(message.message.toUri()).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(imageView)
            timestampText.text = formatTimestamp(message.timestamp)
            imageView.setOnClickListener { (itemView.context as? FileHandler)?.openImage(message.message.toUri()) }
        }
    }

    inner class AiImageMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivImage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        val btnReload: ImageButton = itemView.findViewById(R.id.btnReload)
        val btnPrevious: ImageButton = itemView.findViewById(R.id.btnPrevious)
        val btnNext: ImageButton = itemView.findViewById(R.id.btnNext)
        private val tvNavigationIndicator: TextView = itemView.findViewById(R.id.tvNavigationIndicator)
        private val controlsLayout: LinearLayout = itemView.findViewById(R.id.controlsLayout)

        @SuppressLint("SetTextI18n")
        fun bind(message: ChatMessage) {
            timestampText.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            try {
                timestampText.setTextAppearance(android.R.style.TextAppearance_Small)
            } catch (_: Exception) {
                timestampText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            Glide.with(itemView.context)
                .load(message.message.toUri())
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(imageView)

            timestampText.text = formatTimestamp(message.timestamp)

            imageView.setOnClickListener {
                (itemView.context as? FileHandler)?.openImage(message.message.toUri())
            }

            val myPosition = adapterPosition
            if (myPosition == RecyclerView.NO_POSITION) return

            val latestAiMessagePosition = getLatestAiMessagePosition(message.conversationId)
            val isLatestAiMessage = (myPosition == latestAiMessagePosition)

            // Check if any message is generating
            val isAnyMessageGenerating = currentList.any { it.isGenerating }
            
            if (isAnyMessageGenerating) {
                controlsLayout.visibility = View.GONE
                return
            }

            if (message.totalVersions > 1) {
                controlsLayout.visibility = View.VISIBLE
                btnPrevious.visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
                tvNavigationIndicator.visibility = View.VISIBLE
                btnCopy.visibility = if (isLatestAiMessage) View.VISIBLE else View.GONE
                btnReload.visibility = if (isLatestAiMessage) View.VISIBLE else View.GONE
                btnPrevious.isEnabled = message.versionIndex > 0
                btnNext.isEnabled = message.versionIndex < message.totalVersions - 1
                btnPrevious.alpha = if (btnPrevious.isEnabled) 1.0f else 0.3f
                btnNext.alpha = if (btnNext.isEnabled) 1.0f else 0.3f
                tvNavigationIndicator.text = "${message.versionIndex + 1}/${message.totalVersions}"

                tvNavigationIndicator.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            } else if (isLatestAiMessage) {
                controlsLayout.visibility = View.VISIBLE
                btnCopy.visibility = View.VISIBLE
                btnReload.visibility = View.VISIBLE
                btnPrevious.visibility = View.GONE
                btnNext.visibility = View.GONE
                tvNavigationIndicator.visibility = View.GONE
            } else {
                controlsLayout.visibility = View.GONE
            }

            btnCopy.setOnClickListener { onCopy(message.message) }
            btnReload.setOnClickListener { onReload(message) }
            btnPrevious.setOnClickListener { if (btnPrevious.isEnabled) onNavigate(message, -1) }
            btnNext.setOnClickListener { if (btnNext.isEnabled) onNavigate(message, 1) }
        }

        private fun getLatestAiMessagePosition(conversationId: String): Int {
            var latestPosition = -1
            
            // Find the last AI message in the list (not by timestamp, but by list order)
            for (i in currentList.size - 1 downTo 0) {
                val message = getItem(i)
                if (!message.isUser && message.conversationId == conversationId) {
                    latestPosition = i
                    break // First AI message we find from the end is the latest
                }
            }
            
            // Debug: Show AI messages in order
            val aiMessages = currentList.mapIndexed { index, message -> 
                if (!message.isUser && message.conversationId == conversationId) {
                    "pos=$index, id=${message.id.take(8)}, isGenerating=${message.isGenerating}"
                } else null
            }.filterNotNull()
            
            Timber.d("🔍 getLatestAiMessagePosition: conversationId=$conversationId, latestPosition=$latestPosition")
            Timber.d("🔍 AI messages: $aiMessages")
            return latestPosition
        }
    }

    inner class UserDocumentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val docNameTextView: TextView = itemView.findViewById(R.id.tvDocName)
        private val docTypeTextView: TextView = itemView.findViewById(R.id.tvDocType)
        private val docSizeTextView: TextView = itemView.findViewById(R.id.tvDocSize)
        private val documentCardView: CardView = itemView.findViewById(R.id.documentCardView)
        private val documentIcon: ImageView? = itemView.findViewById(R.id.ivDocumentIcon)

        fun bind(message: ChatMessage) {
            try {
                val uri = message.message.toUri()
                val context = itemView.context
                var fileName = FileUtil.getFileNameFromUri(context, uri) ?: (uri.lastPathSegment ?: "Document")
                val fileSize = FileUtil.getFileSizeFromUri(context, uri)
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val fileType = FileUtil.getFileTypeFromName(fileName)

                docNameTextView.text = fileName
                docTypeTextView.text = fileType
                docSizeTextView.text = formatFileSize(fileSize)

                documentIcon?.let { iconView ->
                    Timber.d("Setting icon for extension: $extension")
                    val iconResId = when (extension) {
                        "pdf" -> R.drawable.ic_pdf
                        "doc", "docx" -> R.drawable.ic_word
                        "xls", "xlsx" -> R.drawable.ic_excel
                        "ppt", "pptx" -> R.drawable.ic_powerpoint
                        "txt" -> R.drawable.ic_text
                        else -> R.drawable.ic_document
                    }
                    try {
                        iconView.setImageResource(iconResId)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) iconView.imageTintList = null
                    } catch (e: Exception) {
                        Timber.e(e, "Error setting document icon resource")
                        iconView.setImageResource(R.drawable.ic_document)
                    }
                }

                documentCardView.setOnClickListener { (itemView.context as? FileHandler)?.openFile(uri, fileName) }
            } catch (e: Exception) {
                Timber.e(e, "Error binding document view: ${e.message}")
                docNameTextView.text = "Document"
                docTypeTextView.text = "Unknown Type"
                docSizeTextView.text = "Unknown Size"
                documentIcon?.setImageResource(R.drawable.ic_document)
            }
        }
    }

    inner class AiDocumentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val docNameTextView: TextView = itemView.findViewById(R.id.tvDocName)
        private val docTypeTextView: TextView = itemView.findViewById(R.id.tvDocType)
        private val docSizeTextView: TextView = itemView.findViewById(R.id.tvDocSize)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val documentCardView: CardView = itemView.findViewById(R.id.documentCardView)
        private val documentIcon: ImageView? = itemView.findViewById(R.id.ivDocumentIcon)

        fun bind(message: ChatMessage) {
            try {
                timestampText.text = formatTimestamp(message.timestamp)
                val uri = message.message.toUri()
                val context = itemView.context
                val fileName = FileUtil.getFileNameFromUri(context, uri) ?: "Unknown Document"
                val fileSize = FileUtil.getFileSizeFromUri(context, uri)
                val fileType = FileUtil.getFileTypeFromName(fileName)

                docNameTextView.text = fileName
                docTypeTextView.text = fileType
                docSizeTextView.text = formatFileSize(fileSize)

                documentIcon?.let { iconView ->
                    val extension = fileName.substringAfterLast('.', "").lowercase()
                    val iconResId = when (extension) {
                        "pdf" -> R.drawable.ic_pdf
                        "doc", "docx" -> R.drawable.ic_word
                        "xls", "xlsx" -> R.drawable.ic_excel
                        "ppt", "pptx" -> R.drawable.ic_powerpoint
                        "txt" -> R.drawable.ic_text
                        else -> R.drawable.ic_document
                    }
                    try {
                        iconView.setImageResource(iconResId)
                    } catch (e: Exception) {
                        Timber.e(e, "Error setting AI document icon")
                        iconView.setImageResource(R.drawable.ic_document)
                    }
                }

                documentCardView.setOnClickListener { (itemView.context as? FileHandler)?.openFile(uri, fileName) }
            } catch (e: Exception) {
                Timber.e(e, "Error binding AI document view: ${e.message}")
                docNameTextView.text = "Document"
                docTypeTextView.text = "Unknown Type"
                docSizeTextView.text = "Unknown Size"
            }
        }
    }

    inner class UserCameraPhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivCameraImage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val cameraCardView: CardView = itemView.findViewById(R.id.cameraCardView)

        fun bind(message: ChatMessage) {
            Glide.with(itemView.context).load(message.message.toUri()).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(imageView)
            timestampText.text = formatTimestamp(message.timestamp)
            cameraCardView.setOnClickListener { (itemView.context as? FileHandler)?.openImage(message.message.toUri()) }
        }
    }

    inner class AiCameraPhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivCameraImage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val cameraCardView: CardView = itemView.findViewById(R.id.cameraCardView)

        fun bind(message: ChatMessage) {
            Glide.with(itemView.context).load(message.message.toUri()).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(imageView)
            timestampText.text = formatTimestamp(message.timestamp)
            cameraCardView.setOnClickListener { (itemView.context as? FileHandler)?.openImage(message.message.toUri()) }
        }
    }

    inner class UserGroupedFilesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagesScrollView: HorizontalScrollView = itemView.findViewById(R.id.imagesScrollView)
        private val imagesContainer: LinearLayout = itemView.findViewById(R.id.imagesContainer)
        private val documentsScrollView: HorizontalScrollView = itemView.findViewById(R.id.documentsScrollView)
        private val documentsContainer: LinearLayout = itemView.findViewById(R.id.documentsContainer)
        private val messageCardView: CardView = itemView.findViewById(R.id.messageCardView)
        private val messageText: TextView = itemView.findViewById(R.id.tvMessage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(message: ChatMessage) {
            timestampText.text = formatTimestamp(message.timestamp)
            try {
                Timber.d("Binding grouped files message: ${message.message.take(50)}...")
                val gson = Gson()
                val type = object : TypeToken<GroupedFileMessage>() {}.type
                val groupedMessage = gson.fromJson<GroupedFileMessage>(message.message, type)

                if (groupedMessage.images.isNotEmpty()) {
                    imagesScrollView.visibility = View.VISIBLE
                    imagesContainer.removeAllViews()

                    for (imageInfo in groupedMessage.images) {
                        Glide.with(itemView.context)
                            .load(Uri.parse(imageInfo.uri))
                            .preload()
                    }

                    for (imageInfo in groupedMessage.images) {
                        Timber.d("Creating image view for: ${imageInfo.name}, URI: ${imageInfo.uri}")
                        val imageView = createImageView(itemView.context, Uri.parse(imageInfo.uri))
                        imagesContainer.addView(imageView)
                    }
                    Timber.d("Added ${groupedMessage.images.size} images to container")
                } else {
                    imagesScrollView.visibility = View.GONE
                }

                if (groupedMessage.documents.isNotEmpty()) {
                    documentsScrollView.visibility = View.VISIBLE
                    documentsContainer.removeAllViews()

                    for (docInfo in groupedMessage.documents) {
                        Timber.d("Creating document view for: ${docInfo.name}, URI: ${docInfo.uri}")
                        val documentView = createDocumentView(
                            itemView.context,
                            Uri.parse(docInfo.uri),
                            docInfo.name,
                            docInfo.size,
                            docInfo.type
                        )
                        documentsContainer.addView(documentView)
                    }
                    Timber.d("Added ${groupedMessage.documents.size} documents to container")
                } else {
                    documentsScrollView.visibility = View.GONE
                }

                if (groupedMessage.text?.isNotEmpty() == true) {
                    messageCardView.visibility = View.VISIBLE
                    messageText.text = groupedMessage.text
                } else {
                    messageCardView.visibility = View.GONE
                }

                itemView.requestLayout()

            } catch (e: Exception) {
                Timber.e(e, "Error parsing grouped file message: ${e.message}")
                messageCardView.visibility = View.VISIBLE
                messageText.text = "Error displaying content: ${e.message}"
            }
        }

        private fun createImageView(context: Context, uri: Uri): View {
            val cardView = CardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.chat_image_width),
                    context.resources.getDimensionPixelSize(R.dimen.chat_image_height)
                ).apply {
                    marginEnd = context.resources.getDimensionPixelSize(R.dimen.chat_image_margin)
                }
                radius = context.resources.getDimension(R.dimen.chat_image_corner_radius)
                cardElevation = context.resources.getDimension(R.dimen.chat_image_elevation)
            }

            val imageView = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            cardView.addView(imageView)

            Glide.with(context)
                .load(uri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .priority(com.bumptech.glide.Priority.HIGH)
                .into(imageView)

            cardView.setOnClickListener { (context as? FileHandler)?.openImage(uri) }
            return cardView
        }

        private fun createDocumentView(context: Context, uri: Uri, fileName: String, fileSize: Long, fileType: String): View {
            val cardView = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(context.resources.getDimensionPixelSize(R.dimen.document_thumbnail_width), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = context.resources.getDimensionPixelSize(R.dimen.document_thumbnail_margin)
                }
                radius = context.resources.getDimension(R.dimen.document_corner_radius)
                cardElevation = context.resources.getDimension(R.dimen.document_elevation)
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.document_background))
                strokeWidth = 1
                strokeColor = ContextCompat.getColor(context, R.color.document_stroke)
            }
            val documentContentView = LayoutInflater.from(context).inflate(R.layout.item_document_thumbnail, null)
            val extension = fileName.substringAfterLast('.', "").lowercase()
            documentContentView.findViewById<TextView>(R.id.tvDocName).text = fileName
            documentContentView.findViewById<TextView>(R.id.tvDocType).text = fileType
            documentContentView.findViewById<TextView>(R.id.tvDocSize).text = formatFileSize(fileSize)
            val iconView = documentContentView.findViewById<ImageView>(R.id.ivDocumentIcon)
            val iconResId = when (extension) {
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx" -> R.drawable.ic_word
                "xls", "xlsx" -> R.drawable.ic_excel
                "ppt", "pptx" -> R.drawable.ic_powerpoint
                "txt" -> R.drawable.ic_text
                else -> R.drawable.ic_document
            }
            try {
                iconView.setImageResource(iconResId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) iconView.imageTintList = null
            } catch (e: Exception) {
                Timber.e(e, "Error setting document icon")
                iconView.setImageResource(R.drawable.ic_document)
            }
            cardView.addView(documentContentView)
            cardView.setOnClickListener { (context as? FileHandler)?.openFile(uri, fileName) }
            return cardView
        }
    }

    inner class OCRDocumentViewHolder(
        itemView: View,
        private val onCopy: (String) -> Unit,
        private val onReload: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val loadingIndicator: ProgressBar = itemView.findViewById(R.id.loadingIndicator)
        private val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        private val btnReload: ImageButton = itemView.findViewById(R.id.btnReload)
        private val ocrInfoContainer: LinearLayout = itemView.findViewById(R.id.ocrInfoContainer)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvPageCount: TextView = itemView.findViewById(R.id.tvPageCount)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val modelIconView: ImageView = itemView.findViewById(R.id.ivAvatar)

        fun bind(message: ChatMessage) {
            try {
                val messageJson = JSONObject(message.message)

                tvTimestamp.text = formatTimestamp(message.timestamp)

                val modelId = message.modelId ?: message.aiModel ?: currentModelId
                modelIconView.setImageResource(ModelIconUtils.getIconResourceForModel(modelId))

                when (messageJson.optString("status")) {
                    "processing" -> {
                        loadingIndicator.visibility = View.VISIBLE
                        btnCopy.visibility = View.GONE
                        btnReload.visibility = View.GONE
                        ocrInfoContainer.visibility = View.GONE

                        val stage = messageJson.optString("stage", "Processing PDF...")
                        val progress = messageJson.optInt("progress", 0)
                        tvMessage.text = "$stage ($progress%)"
                    }
                    "error" -> {
                        loadingIndicator.visibility = View.GONE
                        btnCopy.visibility = View.GONE
                        btnReload.visibility = View.VISIBLE
                        ocrInfoContainer.visibility = View.GONE

                        val errorMessage = messageJson.optString("errorMessage", "OCR processing failed")
                        tvMessage.text = errorMessage
                        tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_color))
                    }
                    "complete" -> {
                        loadingIndicator.visibility = View.GONE
                        btnCopy.visibility = View.VISIBLE
                        btnReload.visibility = View.VISIBLE
                        ocrInfoContainer.visibility = View.VISIBLE

                        val ocrText = messageJson.optString("ocrText", "")
                        tvMessage.text = ocrText
                        tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))

                        val fileName = messageJson.optString("fileName", "")
                        val pageCount = messageJson.optInt("pageCount", 0)
                        val fileSize = messageJson.optLong("fileSize", 0)

                        tvFileName.text = "File: $fileName"
                        tvPageCount.text = "Pages: $pageCount"
                        tvFileSize.text = "Size: ${formatFileSize(fileSize)}"

                        btnCopy.setOnClickListener { onCopy(ocrText) }
                        btnReload.setOnClickListener { onReload(message) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error binding OCR document: ${e.message}")
                tvMessage.text = "Error displaying OCR result: ${e.message}"
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    inner class AiGeneratedImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modelIcon: ImageView = itemView.findViewById(R.id.ivModelIcon)
        private val imageCaption: TextView = itemView.findViewById(R.id.tvImageCaption)
        private val generatedImage: ImageView = itemView.findViewById(R.id.ivGeneratedImage)
        private val loadingIndicator: ProgressBar = itemView.findViewById(R.id.imageLoadingIndicator)
        private val downloadButton: ImageButton = itemView.findViewById(R.id.btnDownload)
        private val shareButton: ImageButton = itemView.findViewById(R.id.btnShare)
        private val regenerateButton: ImageButton = itemView.findViewById(R.id.btnRegenerateImage)
        private val timestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val generationStatus: TextView = itemView.findViewById(R.id.tvGenerationStatus)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBarGeneration)
        private val buttonContainer: LinearLayout = itemView.findViewById(R.id.buttonContainer)

        @OptIn(ExperimentalEncodingApi::class)
        fun bind(message: ChatMessage) {
            val modelId = message.modelId ?: message.aiModel ?: currentModelId
            modelIcon.setImageResource(ModelIconUtils.getIconResourceForModel(modelId))

            timestamp.text = formatTimestamp(message.timestamp)

            try {
                val jsonObject = JSONObject(message.message)

                val status = jsonObject.optString("status", "complete")

                if (status == "generating") {
                    imageCaption.text = jsonObject.optString("caption", "Generating image...")

                    loadingIndicator.visibility = View.VISIBLE
                    generationStatus.visibility = View.VISIBLE

                    val stage = jsonObject.optString("stage", "")
                    val progress = jsonObject.optInt("progress", -1)

                    generationStatus.text = stage

                    if (progress >= 0) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = false
                        progressBar.progress = progress
                    } else {
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = true
                    }

                    generatedImage.visibility = View.GONE
                    buttonContainer.visibility = View.GONE

                } else if (status == "error") {
                    imageCaption.text = jsonObject.optString("caption", "Error generating image")

                    loadingIndicator.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    generationStatus.visibility = View.GONE

                    generatedImage.visibility = View.VISIBLE
                    generatedImage.setImageResource(R.drawable.ic_image_error)

                    buttonContainer.visibility = View.GONE

                } else {
                    val caption = jsonObject.optString("caption", "")
                    if (caption.isNotEmpty()) {
                        imageCaption.text = caption
                        imageCaption.visibility = View.VISIBLE
                    } else {
                        imageCaption.visibility = View.GONE
                    }

                    loadingIndicator.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    generationStatus.visibility = View.GONE

                    generatedImage.visibility = View.VISIBLE
                    buttonContainer.visibility = View.VISIBLE

                    val imageUrl = jsonObject.optString("imageUrl", "")
                    val imageBase64 = jsonObject.optString("imageBase64", "")

                    if (imageUrl.isNotEmpty()) {
                        Glide.with(itemView.context)
                            .load(imageUrl)
                            .centerInside()
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    loadingIndicator.visibility = View.GONE
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    val animation = AnimationUtils.loadAnimation(
                                        itemView.context, R.anim.image_reveal_animation
                                    )
                                    generatedImage.startAnimation(animation)
                                    loadingIndicator.visibility = View.GONE
                                    return false
                                }
                            })
                            .into(generatedImage)
                    } else if (imageBase64.isNotEmpty()) {
                        try {
                            val imageBytes = Base64.decode(imageBase64)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                            generatedImage.setImageBitmap(bitmap)
                            val animation = AnimationUtils.loadAnimation(
                                itemView.context, R.anim.image_reveal_animation
                            )
                            generatedImage.startAnimation(animation)

                        } catch (e: Exception) {
                            Timber.e(e, "Error decoding base64 image")
                            generatedImage.setImageResource(R.drawable.ic_image_error)
                        }
                    } else {
                        generatedImage.setImageResource(R.drawable.ic_image_placeholder)
                    }
                }

                setupActionButtons(message)

            } catch (e: Exception) {
                Timber.e(e, "Error parsing AI generated image message")
                imageCaption.text = "Error loading generated image"
                imageCaption.visibility = View.VISIBLE
                generatedImage.setImageResource(R.drawable.ic_image_error)
                loadingIndicator.visibility = View.GONE
                progressBar.visibility = View.GONE
                generationStatus.visibility = View.GONE
                buttonContainer.visibility = View.GONE
            }
        }

        private fun setupActionButtons(message: ChatMessage) {
            if (message.isGenerating) {
                buttonContainer.visibility = View.GONE
                return
            }

            buttonContainer.visibility = View.VISIBLE

            downloadButton.setOnClickListener {
                try {
                    val drawable = generatedImage.drawable
                    if (drawable != null) {
                        val bitmap = getBitmapFromDrawable(drawable)
                        if (bitmap != null) {
                            saveImageToGallery(bitmap)
                        } else {
                            Toast.makeText(itemView.context, "Could not process image", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(itemView.context, "No image to download", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error downloading image: ${e.message}")
                    Toast.makeText(itemView.context, "Error downloading image", Toast.LENGTH_SHORT).show()
                }
            }

            shareButton.setOnClickListener {
                try {
                    val drawable = generatedImage.drawable
                    if (drawable != null) {
                        val bitmap = getBitmapFromDrawable(drawable)
                        if (bitmap != null) {
                            shareImage(bitmap)
                        } else {
                            Toast.makeText(itemView.context, "Could not process image for sharing", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(itemView.context, "No image to share", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error sharing image: ${e.message}")
                    Toast.makeText(itemView.context, "Error sharing image", Toast.LENGTH_SHORT).show()
                }
            }

            regenerateButton.setOnClickListener {
                try {
                    val jsonObject = JSONObject(message.message)
                    val prompt = jsonObject.optString("prompt", "")

                    if (prompt.isNotEmpty()) {
                        if (imageRegenerationCallback != null) {
                            HapticManager.mediumVibration(itemView.context)

                            imageRegenerationCallback?.onRegenerateImage(message)
                        } else {
                            Toast.makeText(itemView.context,
                                "Regeneration callback not set",
                                Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(itemView.context,
                            "Cannot regenerate image without original prompt",
                            Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error regenerating image: ${e.message}")
                    Toast.makeText(itemView.context,
                        "Error regenerating image: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
            try {
                return when (drawable) {
                    is BitmapDrawable -> {
                        drawable.bitmap
                    }
                    else -> {
                        val bitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error converting drawable to bitmap")
                return null
            }
        }

        private fun saveImageToGallery(bitmap: Bitmap) {
            try {
                val context = itemView.context
                val filename = "AI_Image_${System.currentTimeMillis()}.jpg"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/AI_Generated")
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )

                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        }

                        Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()

                        HapticManager.successFeedback(context)

                        downloadButton.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .setDuration(200)
                            .withEndAction {
                                downloadButton.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(200)
                                    .start()
                            }
                            .start()
                    }
                } else {
                    val directory = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "AI_Generated"
                    )

                    if (!directory.exists()) {
                        directory.mkdirs()
                    }

                    val file = File(directory, filename)
                    FileOutputStream(file).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }

                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))

                    Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()

                    HapticManager.successFeedback(context)

                    downloadButton.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(200)
                        .withEndAction {
                            downloadButton.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving image to gallery: ${e.message}")
                Toast.makeText(itemView.context, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun shareImage(bitmap: Bitmap) {
            try {
                val context = itemView.context
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()

                val file = File(cachePath, "AI_Image_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                shareButton.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .withEndAction {
                        shareButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .start()
                    }
                    .start()

                context.startActivity(Intent.createChooser(shareIntent, "Share AI Generated Image"))
            } catch (e: Exception) {
                Timber.e(e, "Error sharing image: ${e.message}")
                Toast.makeText(itemView.context, "Error sharing image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // CRITICAL: Handle resource cleanup in onViewRecycled and onViewDetachedFromWindow
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        when (holder) {
            is AiMessageViewHolder -> {
                holder.resetAllViews()
                holder.cleanup()
            }
            is UserMessageViewHolder -> holder.cleanup()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is AiMessageViewHolder) holder.cleanup()
    }
    
    /**
     * CRITICAL FIX: Override submitList to clean up interruption messages before displaying
     * This ensures that any messages with interruption text but substantial content are fixed
     */
    override fun submitList(list: List<ChatMessage>?) {
        if (list == null) {
            super.submitList(null)
            return
        }
        
        // Clean up any messages with interruption text that have substantial content
        val cleanedList = list.map { message ->
            if (!message.isUser && 
                message.message.contains("⚠️ Generation was interrupted. Tap 'Continue' to resume.")) {
                
                // Remove the interruption text
                val cleanedMessage = message.message
                    .replace("\n\n⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                    .replace("⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                    .trim()
                
                // If there's substantial content after cleaning, mark as complete
                if (cleanedMessage.length > 50) {
                    Timber.d("🧹 ChatAdapter: Cleaning message ${message.id} with ${cleanedMessage.length} chars")
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
        
        super.submitList(cleanedList)
    }

}