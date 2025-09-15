package com.cyberflux.qwinai.adapter

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.cyberflux.qwinai.utils.HapticManager
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.cyberflux.qwinai.CodeViewerActivity
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.service.AiChatService
import com.cyberflux.qwinai.ui.SourcesBottomSheet
import com.cyberflux.qwinai.ui.spans.RoundedBackgroundSpan
import com.cyberflux.qwinai.utils.UriPathResolver
import com.cyberflux.qwinai.utils.FileHandler
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.FileUtil.formatFileSize
import com.cyberflux.qwinai.utils.FileUtil.formatTimestamp
// UNIFIED: Using only the new span-free UnifiedMarkdownProcessor
import com.cyberflux.qwinai.utils.UnifiedMarkdownProcessor
import com.cyberflux.qwinai.model.MessageContent
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.ModelValidator
import com.google.android.material.card.MaterialCardView
import com.cyberflux.qwinai.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// REMOVED: @PrismBundle annotation - syntax highlighting now handled by unified MarkdownProcessor
class ChatAdapter(
    private val onCopy: (String) -> Unit,
    private val onReload: (ChatMessage) -> Unit,
    private val onNavigate: (ChatMessage, Int) -> Unit,
    private val onLoadMore: () -> Unit,
    private var currentModelId: String = "",
    private var callbacks: Callbacks? = null,
    private val onAudioPlay: (ChatMessage) -> Unit = {}
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {
    
    init {
        // Enable stable IDs for better recycling performance
        setHasStableIds(true)
    }
    
    // PRODUCTION-READY BLOCK-BASED RENDERER: Uses proper View layouts for interactive elements
    private var unifiedMarkdownProcessor: UnifiedMarkdownProcessor? = null
    
    // Initialize processor when context is available
    private fun initializeProcessor(context: Context) {
        if (unifiedMarkdownProcessor == null) {
            try {
                unifiedMarkdownProcessor = UnifiedMarkdownProcessor.create(context)
                Timber.d("✅ ChatAdapter: Production-ready block-based processor initialized")
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize UnifiedMarkdownProcessor")
                // Continue without markdown processor - fallback rendering will be used
            }
        }
    }

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
    
    // View binding cache for performance optimization
    private val viewBindingCache = mutableMapOf<Int, Any>()
    
    /**
     * Provide stable IDs for better recycling performance
     */
    override fun getItemId(position: Int): Long {
        return try {
            getItem(position).id.hashCode().toLong()
        } catch (e: Exception) {
            position.toLong()
        }
    }

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
            } catch (_: Exception) {
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

    // Font size management
    private var currentFontSize: Float = 16f
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateFontSize(fontSize: Float) {
        this.currentFontSize = fontSize
        notifyDataSetChanged()
    }
    
    private fun applyFontSize(textView: TextView) {
        textView.textSize = currentFontSize
        // Apply Ultrathink font with fallback protection
        try {
            val customFont = androidx.core.content.res.ResourcesCompat.getFont(textView.context, R.font.ultrathink)
            if (customFont != null) {
                textView.typeface = customFont
            } else {
                // Fallback to default typeface if font loading returns null
                textView.typeface = Typeface.DEFAULT
            }
        } catch (e: Exception) {
            // Fallback to default typeface if font loading fails
            textView.typeface = Typeface.DEFAULT
            Timber.w("Failed to load ultrathink font, using default: ${e.message}")
        }
    }

    @Deprecated("Use updateStreamingContentDirect instead for better performance", ReplaceWith("updateStreamingContentDirect(messageId, content, false)"))
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
            // Use thread-safe approach to prevent RecyclerView crashes
            try {
                val updatedMessage = currentMessage.copy(message = content)
                val currentSnapshot = currentList.toList() // Create immutable snapshot
                val newList = currentSnapshot.toMutableList()
                if (position < newList.size) {
                    newList[position] = updatedMessage
                    // Use post to ensure UI thread execution and prevent race conditions
                    recyclerViewRef?.post {
                        if (position < currentList.size) {
                            submitList(newList)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating streaming content fallback: ${e.message}")
            }
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

            val currentSnapshot = currentList.toList() // Create immutable snapshot
            val newList = currentSnapshot.toMutableList()
            if (position < newList.size) {
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
                it.isItemPrefetchEnabled = false
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
                it.isItemPrefetchEnabled = true
            }
        }

        Timber.d("Stopped streaming mode - restored animations")
    }

    // UNIFIED: Same processing for streaming and complete content to preserve code blocks - THREAD SAFE
    fun updateStreamingContentDirect(
        messageId: String,
        content: String,
        processMarkdown: Boolean = true,
        isStreaming: Boolean = true
    ) {
        // Ensure this runs on the main thread to avoid UI thread violations
        Handler(Looper.getMainLooper()).post {
            val position = currentList.indexOfFirst { it.id == messageId }
            if (position != -1) {
                val currentMessage = currentList[position]
                recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                    if (holder is AiMessageViewHolder) {
                        try {
                            // CRITICAL: Hide ALL indicators when AI starts writing text
                            if (content.isNotEmpty() && isStreaming) {
                                Timber.d("🔴 updateStreamingContentDirect: Hiding indicators because content appeared (${content.length} chars)")
                                holder.updateLoadingStateDirect(
                                    currentMessage.copy(
                                        isGenerating = true,
                                        message = content // Mark message as having content
                                    )
                                )
                            }
                            
                            // FIXED APPROACH: Use mixed content rendering with inline code blocks
                            Timber.d("updateStreamingContentDirect - Using mixed content rendering")
                            
                            // Start fade effect if streaming
                            if (isStreaming && content.isNotEmpty()) {
                                holder.startGeneratingFadeEffect()
                            }
                            
                            // Use the new updateContentOnly method which handles mixed content properly
                            holder.updateContentOnly(content)
                            
                        } catch (e: Exception) {
                            Timber.e(e, "updateStreamingContentDirect: Error with mixed content rendering")
                            // This will be handled by the fallback in updateContentOnly
                            holder.updateContentOnly(content)
                        }
                    }
                }
            }
        }
    }

    // ULTRA-OPTIMIZED: Gradual animation restoration to prevent layout refresh
    fun stopStreamingModeGradually() {
        isStreamingActive = false

        // Stop fade effects for all visible AI message holders
        recyclerViewRef?.let { recyclerView ->
            for (i in 0 until recyclerView.childCount) {
                val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
                if (holder is AiMessageViewHolder) {
                    holder.stopGeneratingFadeEffect()
                }
            }
        }

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
                        it.isItemPrefetchEnabled = true
                    }
                }
            }, 300)
        }

        Timber.d("Stopped streaming mode gradually - smooth animation restoration")
    }

    /**
     * Update thinking content for streaming messages - THREAD SAFE
     */
    fun updateThinkingContent(messageId: String, thinkingContent: String, isThinkingActive: Boolean = true) {
        // Ensure this runs on the main thread to avoid UI thread violations
        Handler(Looper.getMainLooper()).post {
            val messagePosition = currentList.indexOfFirst { it.id == messageId }
            if (messagePosition == -1) {
                Timber.w("🧠 Message not found for thinking update: $messageId")
                return@post
            }

            val holder = recyclerViewRef?.findViewHolderForAdapterPosition(messagePosition) as? AiMessageViewHolder
            holder?.let { aiHolder ->
                val thinkingView = aiHolder.itemView.findViewById<com.cyberflux.qwinai.components.ThinkingContentView>(R.id.thinkingContentView)
                
                if (thinkingView != null) {
                    // Show the thinking component if not visible
                    if (thinkingView.visibility != View.VISIBLE) {
                        thinkingView.visibility = View.VISIBLE
                    }
                    
                    // Start thinking if not already started and we're in active thinking mode
                    if (isThinkingActive && !thinkingView.isCurrentlyThinking()) {
                        thinkingView.startThinking()
                    }
                    
                    // Append the new thinking content
                    if (thinkingContent.isNotEmpty()) {
                        thinkingView.appendThinkingContent(thinkingContent)
                    }
                    
                    // If thinking is no longer active, stop the thinking mode
                    if (!isThinkingActive && thinkingView.isCurrentlyThinking()) {
                        thinkingView.stopThinking()
                    }
                    
                    Timber.d("🧠 Updated thinking content for message $messageId: ${thinkingContent.length} chars, active: $isThinkingActive")
                } else {
                    Timber.w("🧠 ThinkingContentView not found for message: $messageId")
                }
            } ?: run {
                Timber.w("🧠 ViewHolder not found for thinking update: $messageId at position $messagePosition")
            }
        }
    }

    /**
     * Complete thinking for a message (called when thinking phase is done) - THREAD SAFE
     */
    fun completeThinkingForMessage(messageId: String) {
        // Ensure this runs on the main thread to avoid UI thread violations
        Handler(Looper.getMainLooper()).post {
            Timber.w("🧠 ATTEMPTING to complete thinking for message: $messageId")
            
            val messagePosition = currentList.indexOfFirst { it.id == messageId }
            if (messagePosition == -1) {
                Timber.e("🧠 ERROR: Message not found in list: $messageId")
                return@post
            }

            Timber.w("🧠 Found message at position: $messagePosition")
            
            val holder = recyclerViewRef?.findViewHolderForAdapterPosition(messagePosition) as? AiMessageViewHolder
            if (holder != null) {
                Timber.w("🧠 Found ViewHolder, looking for ThinkingContentView...")
                
                val thinkingView = holder.itemView.findViewById<com.cyberflux.qwinai.components.ThinkingContentView>(R.id.thinkingContentView)
                if (thinkingView != null) {
                    Timber.w("🧠 Found ThinkingContentView, calling stopThinking()...")
                    thinkingView.stopThinking()
                } else {
                    Timber.e("🧠 ERROR: ThinkingContentView not found in ViewHolder")
                }
            } else {
                Timber.w("🧠 WARNING: ViewHolder not found - using fallback methods")
                // Fallback 1: Update message state and trigger a rebind
                notifyItemChanged(messagePosition, "COMPLETE_THINKING")
                
                // Fallback 2: Retry after a short delay (ViewHolder might become available)
                Handler(Looper.getMainLooper()).postDelayed({
                    val retryHolder = recyclerViewRef?.findViewHolderForAdapterPosition(messagePosition) as? AiMessageViewHolder
                    retryHolder?.let { rh ->
                        val thinkingView = rh.itemView.findViewById<com.cyberflux.qwinai.components.ThinkingContentView>(R.id.thinkingContentView)
                        thinkingView?.let { tv ->
                            Timber.w("🧠 RETRY SUCCESS: Found ThinkingContentView on second attempt")
                            tv.stopThinking()
                        }
                    } ?: Timber.w("🧠 RETRY FAILED: ViewHolder still not found after delay")
                }, 100) // Small delay to allow RecyclerView to settle
            }
            
            // CRITICAL FIX: Don't use submitList during thinking completion - it clears the main content!
            // Instead, just update the message internally without any list operations
            val currentMessage = currentList[messagePosition]
            if (currentMessage.isThinkingActive || currentMessage.hasThinkingProcess) {
                // Update the message object in-place in the current list
                // This preserves the streamed content in messageContentContainer
                val updatedMessage = currentMessage.copy(
                    isThinkingActive = false,
                    hasThinkingProcess = true // Keep this true to show the completed thinking content
                )
                
                // IMPORTANT: Only update the internal list, don't call submitList at all!
                // This completely avoids any rebinding that would clear the streamed content
                try {
                    // Update currentList in place without triggering any adapter notifications
                    val mutableList = currentList as? MutableList<ChatMessage>
                    if (mutableList != null) {
                        mutableList[messagePosition] = updatedMessage
                        Timber.w("🧠 Updated message state in-place (no rebind, preserving streamed content)")
                    } else {
                        Timber.w("🧠 Cannot update - currentList is not mutable")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🧠 Error updating message state in-place: ${e.message}")
                }
            }
            
            Timber.w("🧠 ✅ SUCCESSFULLY completed thinking for message: $messageId")
        }
    }

    // ULTRA-OPTIMIZED: Direct loading state updates bypassing DiffUtil for maximum streaming fluidity
    fun updateLoadingStateDirect(
        messageId: String,
        isGenerating: Boolean,
        isWebSearching: Boolean = false,
        customStatusText: String? = null,
        customStatusColor: Int? = null
    ) {
        // ULTRA-FAST: Use cached position if available to avoid indexOf lookup
        val position = currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            // REAL-TIME UPDATE: Direct ViewHolder update for instant response
            recyclerViewRef?.findViewHolderForAdapterPosition(position)?.let { holder ->
                if (holder is AiMessageViewHolder) {
                    val message = currentList[position]
                    val updatedMessage = message.copy(
                        isGenerating = isGenerating,
                        isWebSearchActive = isWebSearching,
                        initialIndicatorText = customStatusText,
                        initialIndicatorColor = customStatusColor
                    )
                    
                    // INSTANT STATE UPDATE: No adapter notifications, pure ViewHolder update
                    try {
                        holder.updateLoadingStateDirect(updatedMessage, customStatusText, customStatusColor)
                        Timber.v("⚡ Ultra-fast loading state update: $messageId")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed ultra-fast loading state update, using fallback")
                        // Fallback to slower but safer method
                        holder.updateLoadingState(updatedMessage)
                    }
                    return
                }
            }
        }
    }
    
    /**
     * ULTRA-STREAMING: Enhanced streaming content updates with performance optimization
     * Includes first-token acceleration and real-time markdown processing
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 
    fun updateStreamingContentUltraFast(
        messageId: String,
        content: String,
        isFirstToken: Boolean = false,
        processMarkdown: Boolean = true,
        isStreaming: Boolean = true
    ) {
        // FIRST TOKEN OPTIMIZATION: Skip all checks and update immediately for first content
        if (isFirstToken && content.isNotEmpty()) {
            updateStreamingContentDirect(messageId, content, processMarkdown, isStreaming)
            Timber.d("🚀 FIRST TOKEN ULTRA-FAST: Immediate delivery for $messageId")
            return
        }
        
        // PERFORMANCE OPTIMIZATION: Batch smaller updates to reduce UI pressure
        if (content.length < 50 && !isFirstToken) {
            // Wait for more content before updating to reduce flicker
            return
        }
        
        // REAL-TIME UPDATE: Standard ultra-fast update for sustained streaming
        updateStreamingContentDirect(messageId, content, processMarkdown, isStreaming)
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


    override fun getItemCount(): Int {
        val normalCount = currentList.size
        return if (normalCount == 0 && !isGenerating) 1 else normalCount
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerViewRef = recyclerView
        
        // Initialize the production-ready block-based processor
        initializeProcessor(recyclerView.context)
        Timber.d("✅ ChatAdapter: Production-ready block-based processor ready")
    }
    
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        
        // Clean up markdown processor resources
        unifiedMarkdownProcessor?.cleanup()
        unifiedMarkdownProcessor = null
        
        this.recyclerViewRef = null
        Timber.d("✅ ChatAdapter: Resources cleaned up")
    }
    
    /**
     * Create professional syntax highlighting plugin using Prism4j
     * Replaces the manual regex-based highlighting with industry-standard solution
     */
    // REMOVED: Old syntax highlighting plugin - now handled by unified MarkdownProcessor

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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                            // SIMPLIFIED: Only handle UI updates, no reprocessing
                            holder.handleFinalUpdate()
                        }
                    }
                }
                // REMOVED: FINAL_CODE_BLOCK_UPDATE - code blocks should work during streaming  
                "ENSURE_CODE_BLOCKS_PERSIST" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            Timber.d("ENSURE_CODE_BLOCKS_PERSIST: Mixed content approach handles persistence automatically")
                            // No manual handling needed - mixed content views handle their own state
                        }
                    }
                }
                "COMPLETE_THINKING" -> {
                    when (holder) {
                        is AiMessageViewHolder -> {
                            Timber.w("🧠 COMPLETE_THINKING payload received for position: $position")
                            val thinkingView = holder.itemView.findViewById<com.cyberflux.qwinai.components.ThinkingContentView>(R.id.thinkingContentView)
                            if (thinkingView != null) {
                                Timber.w("🧠 Found ThinkingContentView via payload, calling stopThinking()...")
                                thinkingView.stopThinking()
                            } else {
                                Timber.e("🧠 ThinkingContentView not found via payload")
                            }
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
            
            val modelColor = ModelIconUtils.getColorForModel(currentModelId, itemView.context)
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

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun bind(message: ChatMessage) {
            messageText.text = message.getCurrentVersionText()
            messageText.setTextIsSelectable(false)
            applyFontSize(messageText)
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
                Timber.d("🔍 USER: Showing version controls for message ${message.id.take(8)} - totalVersions=${message.totalVersions}, versionIndex=${message.versionIndex}")
                navigationContainer?.visibility = View.VISIBLE
                tvNavigationIndicator?.visibility = View.VISIBLE
                btnPrevious?.visibility = View.VISIBLE
                btnNext?.visibility = View.VISIBLE
                
                tvNavigationIndicator?.text = "${message.versionIndex + 1}/${message.totalVersions}"

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
                Timber.d("🔍 USER: Hiding version controls for message ${message.id.take(8)} - totalVersions=${message.totalVersions}, isGenerating=${isAnyMessageGenerating}")
                navigationContainer?.visibility = View.GONE
                tvNavigationIndicator?.visibility = View.GONE
                btnPrevious?.visibility = View.GONE
                btnNext?.visibility = View.GONE
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

    // SIMPLIFIED AiMessageViewHolder without thinking components
    inner class AiMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // FIXED: Use messageContentContainer for mixed content rendering
        val messageContentContainer: LinearLayout = itemView.findViewById(R.id.messageContentContainer)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        val btnReload: ImageButton = itemView.findViewById(R.id.btnReload)
        
        // Fade effect components
        private val messageContentFrame: FrameLayout? = itemView.findViewById(R.id.messageContentFrame)
        private val generatingTextFade: View? = itemView.findViewById(R.id.generatingTextFade)
        private var fadeAnimator: android.animation.ValueAnimator? = null
        val btnPrevious: ImageButton? = itemView.findViewById(R.id.btnAiPrevious)
        val btnNext: ImageButton? = itemView.findViewById(R.id.btnAiNext)
        private val tvNavigationIndicator: TextView? = itemView.findViewById(R.id.tvAiNavigationIndicator)
        val btnAudio: ImageButton? = itemView.findViewById(R.id.btnAudio)
        private var lastMarkdownProcessTime = 0L
        private var lastMarkdownContentLength = 0
        private val modelIconView: ImageView = itemView.findViewById(R.id.ivModelIcon)
        private val aiNavigationControls: LinearLayout? = itemView.findViewById(R.id.aiNavigationControls)
        val buttonContainer: LinearLayout = itemView.findViewById(R.id.buttonContainer)
        
        // Related questions for Perplexity
        private val relatedQuestionsContainer: LinearLayout? = itemView.findViewById(R.id.relatedQuestionsContainer)
        private val relatedQuestionsLayout: LinearLayout? = itemView.findViewById(R.id.relatedQuestionsLayout)
        
        // Search images for Perplexity
        private val searchImagesContainer: LinearLayout? = itemView.findViewById(R.id.searchImagesContainer)
        private val searchImagesRecyclerView: RecyclerView? = itemView.findViewById(R.id.searchImagesRecyclerView)

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
        
        // UNIFIED: Using only MarkdownProcessor - no circular dependencies
        private var currentDisplayedContent: String? = null

        // New Thinking Content Component
        private val thinkingContentView: com.cyberflux.qwinai.components.ThinkingContentView? = 
            itemView.findViewById(R.id.thinkingContentView)

        // State tracking for streaming
        private var message: ChatMessage? = null
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
            itemView.setOnLongClickListener { view ->
                handleLongPress(view)
            }
        }

        // Main method to update content with message context
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun updateContent(content: String, msg: ChatMessage) {
            this.message = msg
            updateContentOnly(content)
        }

        // SMOOTH-OPTIMIZED: Update content with intelligent batching
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun updateContentOnly(content: String) {
            if (isUserInteracting) {
                return
            }

            // CRITICAL: Skip redundant updates to prevent unnecessary processing
            if (content == lastDisplayedContent && messageContentContainer.childCount > 0) {
                return
            }
            
            lastDisplayedContent = content

            try {
                Timber.d("updateContentOnly - PROCESSING ${content.length} chars with unified markdown")
                setMessageTextSafely(content)
            } catch (e: Exception) {
                Timber.e(e, "Error updating content: ${e.message}")
                setMessageTextSafely(content, fallback = true)
            }
        }
        
        /**
         * FIXED: Render message with mixed inline content (text + code blocks)
         */
        private fun setMessageTextSafely(content: String, fallback: Boolean = false) {
            try {
                // FIXED: Don't clear content if the new content is empty and we already have content
                if (content.isBlank() && messageContentContainer.childCount > 0) {
                    Timber.d("STREAMING: Skipping empty content update, preserving existing ${messageContentContainer.childCount} views")
                    return
                }
                
                if (!fallback && message != null && content.isNotBlank()) {
                    // FIXED: Proper markdown processing for both streaming and final content
                    Timber.d("MARKDOWN: Processing ${content.length} chars, streaming=${isStreamingActive}")
                    
                    // CRITICAL: Initialize processor before use
                    initializeProcessor(messageContentContainer.context)
                    
                    // Parse web search sources for citations
                    val cachedWebSearchSources = (message as? ChatMessage)?.webSearchResults?.let { jsonString ->
                        parseWebSearchSources(jsonString)
                    }
                    
                    val webSearchSources = cachedWebSearchSources?.map { source ->
                        UnifiedMarkdownProcessor.WebSearchSource(
                            title = source.title,
                            url = source.url,
                            shortDisplayName = source.shortDisplayName
                        )
                    }
                    
                    // CRITICAL FIX: DO NOT clear views - use incremental rendering to prevent flickering
                    // messageContentContainer.removeAllViews() // REMOVED - causes massive flickering
                    
                    if (isStreamingActive) {
                        // Use block-based streaming renderer for real-time updates
                        val result = unifiedMarkdownProcessor?.renderToContainer(
                            content = content,
                            container = messageContentContainer,
                            webSearchSources = webSearchSources,
                            isStreaming = true
                        )
                        Timber.d("STREAMING: Rendered ${messageContentContainer.childCount} views from ${result} blocks")
                    } else {
                        // Use block-based final renderer for complete content
                        val result = unifiedMarkdownProcessor?.renderToContainer(
                            content = content,
                            container = messageContentContainer,
                            webSearchSources = webSearchSources,
                            isStreaming = false
                        )
                        Timber.d("FINAL: Rendered ${messageContentContainer.childCount} views from ${result} blocks")
                    }
                } else if (content.isNotBlank()) {
                    // FALLBACK RENDERING: Use block-based processor for consistent behavior
                    initializeProcessor(messageContentContainer.context)
                    val result = unifiedMarkdownProcessor?.renderToContainer(
                        content = content,
                        container = messageContentContainer,
                        webSearchSources = null,
                        isStreaming = false
                    )
                    Timber.d("FALLBACK: Rendered ${messageContentContainer.childCount} views from ${result} blocks")
                } else {
                    Timber.d("VIEW-BASED: Empty content provided, keeping existing display")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "VIEW-BASED: Error rendering content, using simple fallback")
                // Critical fallback - simple TextView
                if (content.isNotBlank()) {
                    messageContentContainer.removeAllViews()
                    val fallbackTextView = TextView(ContextThemeWrapper(messageContentContainer.context, R.style.AiMessageTextStyle)).apply {
                        text = content
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setTextIsSelectable(true)
                        movementMethod = LinkMovementMethod.getInstance()
                        setPadding(0, 8, 0, 8)
                        setTextColor(ContextCompat.getColor(context, android.R.color.black))
                        textSize = currentFontSize
                        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ultrathink)
                    }
                    messageContentContainer.addView(fallbackTextView)
                }
            }
        }
        
        /**
         * DEPRECATED: Add a text content view with Markwon processing and citation support
         * REPLACED BY: ViewBasedMarkdownRenderer which eliminates span conflicts
         */
        @Deprecated("Use ViewBasedMarkdownRenderer instead", ReplaceWith("viewBasedRenderer.renderToViews()"))
        private fun addTextContentView(textContent: MessageContent.TextContent, index: Int) {
            val inflater = LayoutInflater.from(messageContentContainer.context)
            val textView = inflater.inflate(R.layout.item_message_text_content, messageContentContainer, false) 
                as TextView
            
            try {
                // DEPRECATED: Old method - now using view-based rendering
                textView.text = textContent.text // Simple fallback for deprecated method
                applyFontSize(textView) // Apply font size and typeface
                
                Timber.d("TEXT_CONTENT: Added text view with simple text (deprecated method)")
                
                messageContentContainer.addView(textView)
                
                Timber.d("MIXED_CONTENT: Added text view $index: ${textContent.text.take(50)}...")
                
            } catch (e: Exception) {
                Timber.e(e, "MIXED_CONTENT: Error adding text content view")
                
                // Fallback: simple text display
                try {
                    textView.text = textContent.text
                    applyFontSize(textView) // Apply font size and typeface
                    messageContentContainer.addView(textView)
                    Timber.d("MIXED_CONTENT: Added fallback text view $index")
                } catch (fallbackError: Exception) {
                    Timber.e(fallbackError, "MIXED_CONTENT: Error in fallback text display")
                }
            }
        }
        
        /**
         * Open code in full-screen viewer
         */
        private fun openCodeInViewer(code: String, language: String) {
            try {
                Timber.d("Opening ${language} code in full screen viewer - code length: ${code.length}")
                
                val intent = CodeViewerActivity.createIntent(
                    context = messageContentContainer.context,
                    codeContent = code,
                    language = language.takeIf { it.isNotBlank() } ?: "Code"
                )
                
                // Add FLAG_ACTIVITY_NEW_TASK to ensure it can launch from adapter context
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                messageContentContainer.context.startActivity(intent)
                Timber.d("Successfully launched CodeViewerActivity")
            } catch (e: Exception) {
                Timber.e(e, "Failed to open code viewer: ${e.message}")
                Toast.makeText(messageContentContainer.context, "Failed to open code viewer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        /**
         * Add a code block content view with custom layout
         */
        private fun addCodeBlockContentView(codeContent: MessageContent.CodeBlockContent, index: Int) {
            try {
                val inflater = LayoutInflater.from(messageContentContainer.context)
                val codeBlockView = inflater.inflate(R.layout.item_code_block, messageContentContainer, false)
                
                // Set up the code block view
                val tvLanguage = codeBlockView.findViewById<TextView>(R.id.tvLanguage)
                val tvCodeContent = codeBlockView.findViewById<TextView>(R.id.tvCodeContent)
                val btnCopyCode = codeBlockView.findViewById<LinearLayout>(R.id.btnCopyCode)
                val btnOpenCode = codeBlockView.findViewById<LinearLayout>(R.id.btnOpenCode)
                val copyIcon = codeBlockView.findViewById<android.widget.ImageView>(R.id.copyIcon)
                val copyText = codeBlockView.findViewById<TextView>(R.id.copyText)
                
                // Set content
                tvLanguage?.text = if (codeContent.language.isNotEmpty()) codeContent.language else "text"
                
                // Apply syntax highlighting
                val highlightedCode = applySyntaxHighlighting(codeContent.code, codeContent.language)
                tvCodeContent?.text = highlightedCode
                
                // Set up copy button
                btnCopyCode?.setOnClickListener {
                    copyCodeToClipboard(codeContent.code, copyIcon, copyText)
                }
                
                // Set up open code in viewer button
                btnOpenCode?.setOnClickListener {
                    openCodeInViewer(codeContent.code, codeContent.language)
                }
                
                messageContentContainer.addView(codeBlockView)
                
                Timber.d("MIXED_CONTENT: Added code block $index: ${codeContent.language}, ${codeContent.code.length} chars")
                
            } catch (e: Exception) {
                Timber.e(e, "MIXED_CONTENT: Error adding code block view")
            }
        }
        
        /**
         * Add an image content view
         */
        private fun addImageContentView(imageContent: MessageContent.ImageContent, index: Int) {
            try {
                // For now, add as simple text with link - could be enhanced with actual ImageView
                val textView = TextView(messageContentContainer.context)
                textView.text = "🖼️ [Image: ${imageContent.altText ?: "Image"}](${imageContent.imageUrl})"
                applyFontSize(textView)
                textView.setPadding(0, 8, 0, 8)
                
                messageContentContainer.addView(textView)
                
                Timber.d("MIXED_CONTENT: Added image view $index: ${imageContent.imageUrl}")
                
            } catch (e: Exception) {
                Timber.e(e, "MIXED_CONTENT: Error adding image content view")
            }
        }
        
        /**
         * Add simple fallback text view
         */
        private fun addSimpleTextView(content: String) {
            try {
                val textView = TextView(messageContentContainer.context)
                textView.text = content
                applyFontSize(textView)
                textView.setTextColor(ContextCompat.getColor(messageContentContainer.context, R.color.text_primary))
                textView.setPadding(0, 8, 0, 8)
                
                messageContentContainer.addView(textView)
                
                Timber.d("MIXED_CONTENT: Added simple text view fallback")
                
            } catch (e: Exception) {
                Timber.e(e, "MIXED_CONTENT: Error adding simple text view")
            }
        }
        
        /**
         * Copy code to clipboard with visual feedback
         */
        private fun copyCodeToClipboard(code: String, copyIcon: android.widget.ImageView?, copyText: TextView?) {
            try {
                val clipboard = messageContentContainer.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Code", code)
                clipboard.setPrimaryClip(clip)
                
                // FIXED: Show success feedback with text only  
                copyText?.apply {
                    text = "Copied"
                    setTextColor(Color.parseColor("#4CAF50"))
                }
                
                // Animate the text only
                copyText?.animate()
                    ?.scaleX(1.1f)
                    ?.scaleY(1.1f)
                    ?.setDuration(200)
                    ?.withEndAction {
                        copyText.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .start()
                    }
                    ?.start()
                
                // Removed toast notification - using button feedback only
                
                // Reset after delay with consistent colors
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    copyText?.apply {
                        text = "Copy"
                        setTextColor(Color.parseColor("#171717")) // Consistent with layout
                    }
                }, 2000)
                
            } catch (e: Exception) {
                Timber.e(e, "MIXED_CONTENT: Failed to copy code to clipboard")
                // Show error feedback on button only
                copyIcon?.setImageResource(R.drawable.ic_error)
                copyText?.apply {
                    text = "Error"
                    setTextColor(Color.parseColor("#FF5722"))
                }
            }
        }

        // Initialize for inline code block rendering
        init {
            // FIXED: No special initialization needed - real views handle their own interactions
        }
        // IMPLEMENTED: Citations system for mixed content approach
        // NOTE: This function now works with TextViews that already have properly processed citations
        // since citation processing now happens BEFORE markdown processing in addTextContentView
        private fun applySourceCitations(sources: List<ChatAdapter.WebSearchSource>) {
            try {
                if (sources.isEmpty()) return
                
                Timber.d("MIXED_CONTENT: Applying ${sources.size} citations to mixed content (already processed)")
                
                // Get all TextViews in the message content container
                val textViews = getAllTextViewsFromContainer(messageContentContainer)
                
                if (textViews.isNotEmpty()) {
                    // Apply citations to the last TextView (main content)
                    val mainTextView = textViews.last()
                    applyCitationsToTextView(mainTextView, sources)
                }
                
                // Add citation indicators at the bottom
                addCitationIndicators(sources)
                
            } catch (e: Exception) {
                Timber.e(e, "Error applying citations: ${e.message}")
            }
        }
        
        private fun getAllTextViewsFromContainer(container: ViewGroup): List<TextView> {
            val textViews = mutableListOf<TextView>()
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                when (child) {
                    is TextView -> textViews.add(child)
                    is ViewGroup -> textViews.addAll(getAllTextViewsFromContainer(child))
                }
            }
            return textViews
        }

        // Apply font size and typeface to all TextViews in the AI message
        private fun applyFontToAllTextViews() {
            try {
                val allTextViews = getAllTextViewsFromContainer(messageContentContainer)
                allTextViews.forEach { textView ->
                    applyFontSize(textView)
                }
                Timber.d("Applied font to ${allTextViews.size} TextViews in AI message")
            } catch (e: Exception) {
                Timber.e(e, "Error applying fonts to AI message TextViews")
            }
        }
        
        private fun applyCitationsToTextView(textView: TextView, sources: List<ChatAdapter.WebSearchSource>) {
            val content = textView.text.toString()
            val spannableString = SpannableStringBuilder(content)
            
            // Add citation numbers to the end of sentences that reference sources
            sources.forEachIndexed { index, source ->
                val citationNumber = index + 1
                val pattern = "\\b${Regex.escape(source.title.take(10))}\\b".toRegex(RegexOption.IGNORE_CASE)
                
                pattern.findAll(content).forEach { match ->
                    val citationSpan = object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: View) {
                            showCitationDialog(source)
                        }
                    }
                    val superscriptSpan = android.text.style.SuperscriptSpan()
                    val colorSpan = android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(itemView.context, R.color.colorPrimary)
                    )
                    
                    val citationText = "[$citationNumber]"
                    val insertPosition = match.range.last + 1
                    
                    spannableString.insert(insertPosition, citationText)
                    val citationStart = insertPosition
                    val citationEnd = insertPosition + citationText.length
                    
                    spannableString.setSpan(citationSpan, citationStart, citationEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(superscriptSpan, citationStart, citationEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(colorSpan, citationStart, citationEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            
            textView.text = spannableString
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
        
        private fun addCitationIndicators(sources: List<ChatAdapter.WebSearchSource>) {
            val citationContainer = LinearLayout(itemView.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 8, 16, 0)
            }
            
            sources.forEachIndexed { index, source ->
                val citationView = createCitationIndicatorView(index + 1, source)
                citationContainer.addView(citationView)
            }
            
            // Add to the parent container
            if (messageContentContainer.parent is ViewGroup) {
                val parentContainer = messageContentContainer.parent as ViewGroup
                parentContainer.addView(citationContainer)
            }
        }
        
        private fun createCitationIndicatorView(number: Int, source: ChatAdapter.WebSearchSource): View {
            val citationView = LinearLayout(itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 4, 8, 4)
                background = ContextCompat.getDrawable(itemView.context, R.drawable.citation_background)
            }
            
            val numberView = TextView(itemView.context).apply {
                text = "[$number]"
                setTextColor(ContextCompat.getColor(itemView.context, R.color.colorPrimary))
                setTypeface(null, Typeface.BOLD)
                textSize = 12f
            }
            
            val titleView = TextView(itemView.context).apply {
                text = source.title
                setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            citationView.addView(numberView)
            citationView.addView(titleView)
            
            citationView.setOnClickListener {
                showCitationDialog(source)
            }
            
            return citationView
        }
        
        private fun showCitationDialog(source: ChatAdapter.WebSearchSource) {
            // Implementation for showing citation details dialog
            try {
                val context = itemView.context
                MaterialAlertDialogBuilder(context)
                    .setTitle(source.title)
                    .setMessage("URL: ${source.url}\n\n${source.snippet}")
                    .setPositiveButton("Open Link") { _, _ ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } catch (e: Exception) {
                Timber.e(e, "Error showing citation dialog")
            }
        }        // Add this helper method to AiMessageViewHolder
        private fun shouldProcessMarkdownNow(content: String): Boolean {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastMarkdown = currentTime - lastMarkdownProcessTime
            val contentGrowth = content.length - lastMarkdownContentLength

            // Process markdown only when:
            // 1. Enough time has passed (100ms) to prevent flickering
            // 2. Content has grown significantly (50+ chars)
            // 3. Content contains complete markdown structures (not partial)
            val shouldProcess = when {
                // Always process if enough time passed and significant growth
                timeSinceLastMarkdown > 100 && contentGrowth > 50 -> true
                // Process if we detect a complete code block
                content.count { it == '`' } % 2 == 0 && content.contains("```") -> true
                // Skip if content ends with incomplete markdown
                content.endsWith("**") || content.endsWith("*") || content.endsWith("`") -> false
                // Default: only if enough time passed
                else -> timeSinceLastMarkdown > 200
            }

            if (shouldProcess) {
                lastMarkdownProcessTime = currentTime
                lastMarkdownContentLength = content.length
            }

            return shouldProcess
        }
        
        // REMOVED: createMarkwonWithCitations() - replaced with MarkdownProcessor
        // which already has comprehensive citation support and all markdown features
        
        // DEPRECATED: Manual citation processing - replaced by WebSearchCitationPlugin
        // Keeping for fallback compatibility
        private fun processCitationsWithChips(
            spannable: SpannableStringBuilder,
            sources: List<WebSearchSource>
        ): SpannableStringBuilder {
            val text = spannable.toString()
            val newSpannable = SpannableStringBuilder()

            // Pattern to find [1], [2], etc.
            val citationPattern = Pattern.compile("\\[(\\d+)]")
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
        
        /**
         * Start fade animation for generating text
         */
        fun startGeneratingFadeEffect() {
            generatingTextFade?.let { fadeView ->
                fadeView.visibility = View.VISIBLE
                
                // Create gentle pulsing animation
                fadeAnimator = android.animation.ValueAnimator.ofFloat(0.0f, 0.3f).apply {
                    duration = 1500
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    
                    addUpdateListener { animator ->
                        val alpha = animator.animatedValue as Float
                        fadeView.alpha = alpha
                    }
                    
                    start()
                }
                
                Timber.d("Started generating text fade effect")
            }
        }
        
        /**
         * Stop fade animation for generating text
         */
        fun stopGeneratingFadeEffect() {
            fadeAnimator?.cancel()
            fadeAnimator = null
            
            generatingTextFade?.let { fadeView ->
                fadeView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        fadeView.visibility = View.GONE
                    }
                    .start()
                    
                Timber.d("Stopped generating text fade effect")
            }
        }
        
        // In ChatAdapter.kt - Update the updateLoadingState method in AiMessageViewHolder  
        fun updateLoadingState(message: ChatMessage) {
            Timber.d("🔴 updateLoadingState: isGenerating:${message.isGenerating}, message.length:${message.message.length}, isWebSearchActive:${message.isWebSearchActive}")
            
            // FIXED: Simpler, more predictable logic
            val (newText, newColor, shouldShow) = when {
                message.isWebSearchActive -> {
                    // Web search: show indicator + text
                    Triple("Web search", ContextCompat.getColor(itemView.context, R.color.web_search_indicator), true)
                }
                message.isGenerating && message.message.isNotEmpty() -> {
                    // AI is writing text - hide ALL indicators completely
                    Triple("", Color.TRANSPARENT, false)
                }
                message.isGenerating && message.message.isEmpty() -> {
                    // AI is preparing to generate but no text yet - show loading indicator
                    Triple("", ContextCompat.getColor(itemView.context, R.color.text_secondary), true)
                }
                !message.isGenerating && message.message.isNotEmpty() -> {
                    // Complete state with content - hide ALL indicators completely
                    Triple("", Color.TRANSPARENT, false)
                }
                !message.isGenerating && message.message.isEmpty() -> {
                    // Idle state with no content - show loading indicator
                    Triple("", ContextCompat.getColor(itemView.context, R.color.text_secondary), true)
                }
                else -> {
                    // Default fallback - hide indicators
                    Triple("", Color.TRANSPARENT, false)
                }
            }

            // Custom status overrides with fallback - handle null as "use default"
            val finalText = when {
                message.initialIndicatorText != null && message.initialIndicatorText.isNotBlank() -> message.initialIndicatorText
                message.initialIndicatorText == null -> newText // null means use default logic
                else -> newText // empty string also means use default
            }
            val finalColor = message.initialIndicatorColor ?: newColor

            // Always keep the persistent indicator container visible for smooth transitions
            persistentIndicatorContainer?.visibility = View.VISIBLE
            
            Timber.d("🔴 updateLoadingState RESULT: shouldShow:$shouldShow, finalText:'$finalText', finalColor:$finalColor")
            
            if (shouldShow) {
                // Show persistent loading indicator
                persistentLoadingIndicator?.show(finalColor)
                Timber.d("🔴 SHOWING loading indicator")
                
                if (finalText.isNotEmpty()) {
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
                    // Hide text but keep loading indicator
                    if (shimmerStatusWrapper != null) {
                        shimmerStatusWrapper.hideStatus()
                    }
                    simpleStatusContainer?.visibility = View.GONE
                }
                
            } else {
                // Hide loading indicator completely
                persistentLoadingIndicator?.hide()
                Timber.d("🔴 HIDING loading indicator")
                
                // Hide status text
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
                // Direct UI updates without triggering adapter changes - FIXED LOGIC
                when {
                    message.isWebSearchActive -> {
                        // Web search: show indicator + text
                        val finalColor = customStatusColor ?: Color.parseColor("#2563EB")
                        val finalText = customStatusText ?: "Web search"
                        
                        persistentLoadingIndicator?.show(finalColor)
                        shimmerStatusWrapper?.setStatus(finalText, finalColor, true)
                        simpleStatusContainer?.visibility = View.VISIBLE
                    }
                    
                    message.isGenerating && message.message.isNotEmpty() -> {
                        // AI is writing text - hide ALL indicators completely
                        persistentLoadingIndicator?.hide()
                        shimmerStatusWrapper?.hideStatus()
                        simpleStatusContainer?.visibility = View.GONE
                    }
                    
                    message.isGenerating && message.message.isEmpty() -> {
                        // AI is preparing to generate but no text yet - show loading indicator
                        val finalColor = customStatusColor ?: Color.parseColor("#757575")
                        
                        persistentLoadingIndicator?.show(finalColor)
                        shimmerStatusWrapper?.hideStatus() // No text during preparation
                        simpleStatusContainer?.visibility = View.GONE
                    }
                    
                    !message.isGenerating && message.message.isNotEmpty() -> {
                        // Complete state with content - hide ALL indicators completely
                        persistentLoadingIndicator?.hide()
                        shimmerStatusWrapper?.hideStatus()
                        simpleStatusContainer?.visibility = View.GONE
                    }
                    
                    !message.isGenerating && message.message.isEmpty() -> {
                        // Idle state with no content - show loading indicator
                        val finalColor = customStatusColor ?: Color.parseColor("#757575")
                        
                        persistentLoadingIndicator?.show(finalColor)
                        shimmerStatusWrapper?.hideStatus() // No text in idle state
                        simpleStatusContainer?.visibility = View.GONE
                    }
                    
                    else -> {
                        // Default fallback - hide indicators
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
                        val searchResults = JsonUtils.fromJsonList(
                            message.webSearchResults,
                            WebSearchSource::class.java
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

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun bind(message: ChatMessage) {
            Timber.d("🔴 BIND - Message: ${message.id}, model: ${message.modelId}, " +
                    "isGenerating: ${message.isGenerating}")

            resetAllViews()
            this.message = message
            
            // Initialize loading state immediately after reset to ensure persistent indicators are shown
            Timber.d("🔴 BIND: Message state - isGenerating:${message.isGenerating}, message.length:${message.message.length}, isWebSearchActive:${message.isWebSearchActive}")
            updateLoadingState(message)
            if (message.hasWebSearchResults && !message.webSearchResults.isNullOrBlank()) {
                cachedWebSearchSources = parseWebSearchSources(message.webSearchResults)
                Timber.d("Cached ${cachedWebSearchSources?.size} web search sources")
            }
            // Text selection is handled by individual TextViews in messageContentContainer

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

            messageContentContainer.visibility = View.VISIBLE
            messageContentContainer.alpha = 1f

            timestampText.text = formatTimestamp(message.timestamp)

            // Apply font size and typeface to all TextViews in the AI message
            applyFontToAllTextViews()

            // Handle thinking functionality
            setupThinkingComponents(message)
            
            // Handle related questions for Perplexity
            handleRelatedQuestions(message)
            
            // Handle search images for Perplexity
            handleSearchImages(message)

            // Button handling is now done in the when statement above
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun updateMessageContentDirectly(message: ChatMessage) {
            this.message = message

            try {
                val contentToDisplay = message.getCurrentVersionText()
                
                // FIXED: Don't overwrite streamed content with potentially incomplete stored content
                val hasStreamedContent = messageContentContainer.childCount > 0
                
                if (hasStreamedContent && contentToDisplay.length <= lastDisplayedContent.length) {
                    Timber.d("📝 Direct update skipped - preserving streamed content (${messageContentContainer.childCount} views, streamed: ${lastDisplayedContent.length}, stored: ${contentToDisplay.length})")
                    // Only handle sources, don't update content
                    if (message.hasWebSearchResults && !message.webSearchResults.isNullOrBlank()) {
                        cachedWebSearchSources = parseWebSearchSources(message.webSearchResults)
                        cachedWebSearchSources?.let { sources ->
                            setupSourcesButton(sources)
                            applySourceCitations(sources)
                        }
                    }
                    return
                }

                if (contentToDisplay.isNotEmpty()) {
                    val processedMessage = SpannableStringBuilder(contentToDisplay) // Use unified processor elsewhere
                    
                    // Process web search sources if available
                    if (message.hasWebSearchResults && !message.webSearchResults.isNullOrBlank()) {
                        cachedWebSearchSources = parseWebSearchSources(message.webSearchResults)
                        cachedWebSearchSources?.let { sources ->
                            try {
                                // FIXED: Use MarkdownProcessor instead of competing Markwon instance
                                // The MarkdownProcessor already has citation processing built-in
                                setMessageTextSafely(processedMessage.toString())
                                setupSourcesButton(sources)
                                Timber.d("CITATION_FIX: Using MarkdownProcessor for ${sources.size} sources")
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing citations in updateMessageContentDirectly: ${e.message}")
                                setMessageTextSafely(processedMessage.toString())
                                setupSourcesButton(sources)
                            }
                        } ?: run {
                            setMessageTextSafely(processedMessage.toString())
                        }
                    } else {
                        setMessageTextSafely(processedMessage.toString())
                    }

                    Timber.d("📝 Direct content update: ${contentToDisplay.length} chars")
                }

            } catch (e: Exception) {
                Timber.e(e, "Error in direct content update: ${e.message}")
                // Don't fallback to message.message if we have streamed content
                if (messageContentContainer.childCount == 0) {
                    setMessageTextSafely(message.message, fallback = true)
                }
            }
        }

        // SIMPLIFIED: handleFinalUpdate - only handle UI state, no content reprocessing
// In ChatAdapter.kt - Update handleFinalUpdate in AiMessageViewHolder
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun handleFinalUpdate() {
            message?.let { msg ->
                Timber.d("handleFinalUpdate: Message has ${msg.message.length} chars, container has ${messageContentContainer.childCount} views")
                
                // FIXED: Don't overwrite streamed content if it's already properly rendered
                val hasStreamedContent = messageContentContainer.childCount > 0
                val storedContentLength = msg.message.length
                
                if (hasStreamedContent) {
                    Timber.d("handleFinalUpdate: Preserving streamed content (${messageContentContainer.childCount} views)")
                    // Only handle non-content updates - don't call setMessageTextSafely
                } else if (storedContentLength > 0) {
                    Timber.d("handleFinalUpdate: No streamed content, using stored content (${storedContentLength} chars)")
                    // Fallback: use stored content if no streamed content exists
                    setMessageTextSafely(msg.message)
                } else {
                    Timber.d("handleFinalUpdate: No content available, keeping existing display")
                    // Don't clear existing content
                }
                
                // Always handle web search sources if present
                if (msg.hasWebSearchResults && !msg.webSearchResults.isNullOrBlank()) {
                    cachedWebSearchSources = parseWebSearchSources(msg.webSearchResults)
                    setupSourcesButton(cachedWebSearchSources!!)
                    applySourceCitations(cachedWebSearchSources!!)
                }

                // Hide loading indicators
                persistentIndicatorContainer?.visibility = View.GONE
                persistentLoadingIndicator?.hide()
                simpleStatusContainer?.visibility = View.GONE
                shimmerStatusWrapper?.hideStatus()
            }
        }

        
        
        // REMOVED: forceFinalCodeBlockProcessing - code blocks should work during streaming
        // No final processing needed - all processing now happens during streaming
        
        private fun setupThinkingComponents(message: ChatMessage) {
            // FIXED: Show thinking view if message has thinking process, even if content is empty initially
            // Content will be populated during streaming via updateThinkingContent()
            val shouldShowThinking = message.hasThinkingProcess
            
            if (shouldShowThinking) {
                thinkingContentView?.visibility = View.VISIBLE
                
                // Set thinking content if available (might be empty during initial streaming)
                val thinkingContent = message.thinkingProcess ?: ""
                if (thinkingContent.isNotEmpty()) {
                    thinkingContentView?.setThinkingContent(thinkingContent)
                }
                
                // Handle thinking state
                if (message.isThinkingActive) {
                    // Still thinking - start thinking mode if not already started
                    if (!thinkingContentView?.isCurrentlyThinking()!!) {
                        thinkingContentView.startThinking()
                    }
                } else {
                    // Thinking completed - stop thinking mode if active
                    if (thinkingContentView?.isCurrentlyThinking()!!) {
                        thinkingContentView.stopThinking()
                    }
                }
                
                // Setup callback for expansion toggle
                thinkingContentView?.onToggleExpansion = { isExpanded ->
                    Timber.d("🧠 Thinking content expanded: $isExpanded for message: ${message.id}")
                }
                
                Timber.d("🧠 Showing thinking view for message: ${message.id}, active: ${message.isThinkingActive}, content: ${thinkingContent.length} chars")
            } else {
                // Hide thinking container
                thinkingContentView?.visibility = View.GONE
                Timber.d("🧠 Hiding thinking content for message: ${message.id}")
            }
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

            // Always show loading indicator by default (idle state) - updateLoadingState will manage specific states
            persistentLoadingIndicator?.show(Color.parseColor("#757575"))

            webSearchSourcesContainer?.apply {
                visibility = View.GONE
                alpha = 0f
                clearAnimation()
            }

            messageContentContainer.apply {
                removeAllViews()
                alpha = 1f
                visibility = View.VISIBLE
            }

            // Don't hide button container here - let handleButtonVisibility control it
            // buttonContainer.visibility = View.GONE
            
            // Instead, hide individual buttons
            btnCopy.visibility = View.GONE
            btnReload.visibility = View.GONE
            aiNavigationControls?.visibility = View.GONE
            
            // Reset thinking content view
            thinkingContentView?.visibility = View.GONE
            
            Timber.d("🔴 RESET: Hidden individual buttons and thinking components for message: ${message?.id?.take(8)}")
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
            //    - OR it's the latest AI message (for reload functionality)
            val shouldShowControls = !isGenerating && !isAnyMessageGenerating && 
                                   (message.totalVersions > 1 || isLatestAiMessage)
            
            if (shouldShowControls) {
                aiNavigationControls?.visibility = View.VISIBLE
                
                // Only show version indicator when there are multiple versions
                if (message.totalVersions > 1) {
                    Timber.d("🔍 AI: Showing version controls for message ${message.id.take(8)} - totalVersions=${message.totalVersions}, versionIndex=${message.versionIndex}")
                    tvNavigationIndicator?.visibility = View.VISIBLE
                    tvNavigationIndicator?.text = "${message.versionIndex + 1}/${message.totalVersions}"
                    btnPrevious?.visibility = View.VISIBLE
                    btnNext?.visibility = View.VISIBLE
                    
                    btnPrevious?.isEnabled = message.versionIndex > 0
                    btnNext?.isEnabled = message.versionIndex < message.totalVersions - 1
                    
                    btnPrevious?.alpha = if (btnPrevious?.isEnabled == true) 1.0f else 0.3f
                    btnNext?.alpha = if (btnNext?.isEnabled == true) 1.0f else 0.3f
                } else {
                    // Hide version controls for single version messages
                    Timber.d("🔍 AI: Hiding version indicators (single version) for message ${message.id.take(8)} - totalVersions=${message.totalVersions}")
                    tvNavigationIndicator?.visibility = View.GONE
                    btnPrevious?.visibility = View.GONE
                    btnNext?.visibility = View.GONE
                }
                
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
        
        private fun handleRelatedQuestions(message: ChatMessage) {
            relatedQuestionsContainer?.let { container ->
                relatedQuestionsLayout?.let { layout ->
                    // Check if this is a Perplexity model and the message is complete (not generating)
                    val isPerplexityModel = com.cyberflux.qwinai.utils.PerplexityPreferences.isPerplexityModel(
                        message.modelId ?: currentModelId
                    )
                    
                    if (isPerplexityModel && !message.isGenerating && message.relatedQuestions != null) {
                        // Show related questions
                        container.visibility = View.VISIBLE
                        layout.removeAllViews()
                        
                        // Parse related questions from message
                        val questions = parseRelatedQuestions(message.relatedQuestions)
                        questions.forEach { question ->
                            val questionView = createRelatedQuestionView(question)
                            layout.addView(questionView)
                        }
                    } else {
                        // Hide related questions
                        container.visibility = View.GONE
                    }
                }
            }
        }
        
        private fun parseRelatedQuestions(relatedQuestionsJson: String?): List<String> {
            if (relatedQuestionsJson.isNullOrBlank()) return emptyList()
            
            return try {
                val jsonArray = org.json.JSONArray(relatedQuestionsJson)
                val questions = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    questions.add(jsonArray.getString(i))
                }
                questions.take(5) // Limit to 5 questions max
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse related questions: $relatedQuestionsJson")
                emptyList()
            }
        }
        
        private fun createRelatedQuestionView(question: String): View {
            val context = itemView.context
            val textView = TextView(context).apply {
                text = question
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                background = ContextCompat.getDrawable(context, R.drawable.related_question_background)
                setPadding(16.dp.toInt(), 12.dp.toInt(), 16.dp.toInt(), 12.dp.toInt())
                
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8.dp.toInt())
                }
                setLayoutParams(layoutParams)
                
                // Add click listener to start new conversation with the question
                setOnClickListener {
                    onRelatedQuestionClicked(question)
                }
                
                // Add ripple effect
                isClickable = true
                isFocusable = true
            }
            
            return textView
        }
        
        private fun onRelatedQuestionClicked(question: String) {
            // Trigger callback to MainActivity to start a new conversation
            if (itemView.context is MainActivity) {
                val mainActivity = itemView.context as MainActivity
                mainActivity.startNewConversationWithQuestion(question)
            }
        }
        
        private fun handleSearchImages(message: ChatMessage) {
            searchImagesContainer?.let { container ->
                searchImagesRecyclerView?.let { recyclerView ->
                    // Check if this is a Perplexity model and the message is complete (not generating)
                    val isPerplexityModel = com.cyberflux.qwinai.utils.PerplexityPreferences.isPerplexityModel(
                        message.modelId ?: currentModelId
                    )
                    
                    if (isPerplexityModel && !message.isGenerating && message.searchImages != null) {
                        // Show search images
                        container.visibility = View.VISIBLE
                        
                        // Parse search images from message
                        val images = parseSearchImages(message.searchImages)
                        if (images.isNotEmpty()) {
                            // Set up horizontal RecyclerView with LinearLayoutManager
                            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                                itemView.context,
                                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                                false
                            )
                            
                            // Create and set adapter
                            recyclerView.adapter = SearchImagesAdapter(images) { imageUrl ->
                                onSearchImageClicked(imageUrl)
                            }
                        } else {
                            container.visibility = View.GONE
                        }
                    } else {
                        // Hide search images
                        container.visibility = View.GONE
                    }
                }
            }
        }
        
        private fun parseSearchImages(searchImagesJson: String?): List<SearchImageData> {
            if (searchImagesJson.isNullOrBlank()) return emptyList()
            
            return try {
                val jsonArray = org.json.JSONArray(searchImagesJson)
                val images = mutableListOf<SearchImageData>()
                for (i in 0 until jsonArray.length()) {
                    val imageObj = jsonArray.getJSONObject(i)
                    images.add(SearchImageData(
                        url = imageObj.getString("url"),
                        alt = imageObj.optString("alt").takeIf { it.isNotBlank() },
                        width = imageObj.optInt("width").takeIf { it > 0 },
                        height = imageObj.optInt("height").takeIf { it > 0 },
                        thumbnail = imageObj.optString("thumbnail").takeIf { it.isNotBlank() },
                        title = imageObj.optString("title").takeIf { it.isNotBlank() },
                        source = imageObj.optString("source").takeIf { it.isNotBlank() }
                    ))
                }
                images.take(10) // Limit to 10 images max
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse search images: $searchImagesJson")
                emptyList()
            }
        }
        
        private fun onSearchImageClicked(imageUrl: String) {
            // Open image in full screen or external app
            try {
                val intent = Intent(Intent.ACTION_VIEW).also {
                    it.setDataAndType(Uri.parse(imageUrl), "image/*")
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                itemView.context.startActivity(intent)
            } catch (e: Exception) {
                // If no app can handle the image, try opening in browser
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
                    itemView.context.startActivity(browserIntent)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to open image: $imageUrl")
                }
            }
        }
        
        private val Int.dp: Float
            get() = this * itemView.context.resources.displayMetrics.density

        // REMOVED: Old processMarkdown method - now using unified MarkdownProcessor

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
                val isLatestMessage = callbacks?.isLatestAiMessage(message.id) == true
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
                        uri = message.generatedFileUri?.let { Uri.parse(it) }
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
        
        
        /**
         * ANTI-FLICKER: Detect if content structure changed significantly
         * Prevents unnecessary view rebuilds during streaming
         */
        private fun hasSignificantStructureChange(newContent: String, oldContent: String): Boolean {
            // Check for new code blocks
            val oldCodeBlocks = oldContent.split("```").size - 1
            val newCodeBlocks = newContent.split("```").size - 1
            if (newCodeBlocks > oldCodeBlocks) return true
            
            // Check for new headers
            val oldHeaders = oldContent.lines().count { it.trimStart().startsWith("#") }
            val newHeaders = newContent.lines().count { it.trimStart().startsWith("#") }
            if (newHeaders > oldHeaders) return true
            
            // Check for new citations
            val oldCitations = oldContent.split("[").size - 1
            val newCitations = newContent.split("[").size - 1
            if (newCitations > oldCitations) return true
            
            // Check for significant length increase (new paragraph likely)
            val lengthIncrease = newContent.length - oldContent.length
            if (lengthIncrease > 200) return true
            
            return false
        }

        fun cleanup() {
            message = null
            spannableCache.evictAll()
        }

        private fun Int.dp(context: Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }
        
        /**
         * Apply enhanced syntax highlighting to code based on language
         * Uses the enhanced MarkdownProcessor for beautiful colors
         */
        private fun applySyntaxHighlighting(code: String, language: String): SpannableStringBuilder {
            // The MarkdownProcessor handles syntax highlighting internally
            // This method is kept for compatibility but uses simple fallback highlighting
            return try {
                val spannable = SpannableStringBuilder(code)
                when (language.lowercase()) {
                    "kotlin", "java" -> highlightJavaKotlin(spannable, code)
                    "javascript", "js" -> highlightJavaScript(spannable, code)
                    "python", "py" -> highlightPython(spannable, code)
                }
                spannable
            } catch (e: Exception) {
                Timber.w(e, "Syntax highlighting failed, using plain text")
                SpannableStringBuilder(code)
            }
        }

        private fun highlightJavaKotlin(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "abstract", "class", "fun", "function", "var", "val", "let", "const",
                "if", "else", "when", "for", "while", "do", "break", "continue",
                "return", "package", "import", "private", "public", "protected",
                "override", "interface", "enum", "data", "object", "companion",
                "inline", "suspend", "lateinit", "by", "in", "is", "as", "try",
                "catch", "finally", "throw", "throws", "extends", "implements",
                "new", "this", "super", "static", "final", "void", "int", "long",
                "float", "double", "boolean", "char", "byte", "short", "true", "false", "null"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightComments(spannable, code)
            highlightNumbers(spannable, code)
            highlightAnnotations(spannable, code)
        }

        private fun highlightJavaScript(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "var", "let", "const", "function", "if", "else", "for", "while",
                "do", "break", "continue", "return", "class", "extends", "new",
                "this", "super", "import", "export", "default", "from", "async",
                "await", "try", "catch", "finally", "throw", "typeof", "instanceof",
                "in", "of", "true", "false", "null", "undefined"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightComments(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightPython(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "def", "class", "if", "elif", "else", "for", "while", "break",
                "continue", "return", "import", "from", "as", "try", "except",
                "finally", "raise", "with", "lambda", "yield", "global", "nonlocal",
                "True", "False", "None", "and", "or", "not", "in", "is"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightPythonComments(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightXml(spannable: SpannableStringBuilder, code: String) {
            // XML tags
            val tagPattern = Pattern.compile("</?\\w+[^>]*>")
            val tagMatcher = tagPattern.matcher(code)
            while (tagMatcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#22863A")),
                    tagMatcher.start(), tagMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Attributes
            val attrPattern = Pattern.compile("\\w+=\"[^\"]*\"")
            val attrMatcher = attrPattern.matcher(code)
            while (attrMatcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#6F42C1")),
                    attrMatcher.start(), attrMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun highlightJson(spannable: SpannableStringBuilder, code: String) {
            // JSON keys
            val keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:")
            val keyMatcher = keyPattern.matcher(code)
            while (keyMatcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#005CC5")),
                    keyMatcher.start(), keyMatcher.end(1) + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            highlightStrings(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightCpp(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "auto", "break", "case", "char", "const", "continue", "default", "do",
                "double", "else", "enum", "extern", "float", "for", "goto", "if",
                "int", "long", "register", "return", "short", "signed", "sizeof", "static",
                "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
                "class", "private", "protected", "public", "virtual", "inline", "template",
                "namespace", "using", "try", "catch", "throw", "new", "delete", "true", "false"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightComments(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightSwift(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "var", "let", "func", "class", "struct", "enum", "protocol", "extension",
                "if", "else", "guard", "switch", "case", "default", "for", "while", "repeat",
                "break", "continue", "return", "throw", "throws", "rethrows", "try", "catch",
                "do", "defer", "import", "public", "private", "internal", "fileprivate",
                "open", "final", "static", "lazy", "weak", "unowned", "override", "required",
                "convenience", "init", "deinit", "subscript", "willSet", "didSet", "get", "set",
                "true", "false", "nil", "self", "super", "Any", "AnyObject"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightComments(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightRust(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "as", "break", "const", "continue", "crate", "else", "enum", "extern", "false", "fn",
                "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub",
                "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true",
                "type", "unsafe", "use", "where", "while", "async", "await", "dyn"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightComments(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightGo(spannable: SpannableStringBuilder, code: String) {
            val keywords = setOf(
                "break", "default", "func", "interface", "select", "case", "defer", "go", "map",
                "struct", "chan", "else", "goto", "package", "switch", "const", "fallthrough",
                "if", "range", "type", "continue", "for", "import", "return", "var", "true", "false",
                "nil", "make", "new", "len", "cap", "append", "copy", "close", "delete", "complex",
                "real", "imag", "panic", "recover"
            )

            highlightKeywords(spannable, code, keywords)
            highlightStrings(spannable, code)
            highlightComments(spannable, code)
            highlightNumbers(spannable, code)
        }

        private fun highlightGeneric(spannable: SpannableStringBuilder, code: String) {
            highlightStrings(spannable, code)
            highlightNumbers(spannable, code)
            highlightComments(spannable, code)
        }

        private fun highlightKeywords(spannable: SpannableStringBuilder, code: String, keywords: Set<String>) {
            for (keyword in keywords) {
                val pattern = Pattern.compile("\\b$keyword\\b")
                val matcher = pattern.matcher(code)
                while (matcher.find()) {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#0969DA")),
                        matcher.start(), matcher.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        matcher.start(), matcher.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        private fun highlightStrings(spannable: SpannableStringBuilder, code: String) {
            val pattern = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`")
            val matcher = pattern.matcher(code)
            while (matcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#0A3069")),
                    matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun highlightComments(spannable: SpannableStringBuilder, code: String) {
            // Single line //
            val singleLinePattern = Pattern.compile("//.*$", Pattern.MULTILINE)
            val singleLineMatcher = singleLinePattern.matcher(code)
            while (singleLineMatcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#6F7781")),
                    singleLineMatcher.start(), singleLineMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Multi line /* */
            val multiLinePattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
            val multiLineMatcher = multiLinePattern.matcher(code)
            while (multiLineMatcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#6F7781")),
                    multiLineMatcher.start(), multiLineMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun highlightPythonComments(spannable: SpannableStringBuilder, code: String) {
            val pattern = Pattern.compile("#.*$", Pattern.MULTILINE)
            val matcher = pattern.matcher(code)
            while (matcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#6F7781")),
                    matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun highlightNumbers(spannable: SpannableStringBuilder, code: String) {
            val pattern = Pattern.compile("\\b\\d+(\\.\\d+)?[fFdDlL]?\\b")
            val matcher = pattern.matcher(code)
            while (matcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#0550AE")),
                    matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun highlightAnnotations(spannable: SpannableStringBuilder, code: String) {
            val pattern = Pattern.compile("@\\w+")
            val matcher = pattern.matcher(code)
            while (matcher.find()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#6F42C1")),
                    matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    // All other ViewHolders remain unchanged...
    inner class UserImageMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivImage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(message: ChatMessage) {
            // Extract image info from JSON stored in prompt field
            val imageInfo = extractFirstImageFromPrompt(message.prompt)
            if (imageInfo != null) {
                val uri = Uri.parse(imageInfo.uri)
                Glide.with(itemView.context)
                    .load(uri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(imageView)
                timestampText.text = formatTimestamp(message.timestamp)
                imageView.setOnClickListener { (itemView.context as? FileHandler)?.openImage(uri) }
            } else {
                // Fallback to parsing message as URI (legacy support)
                try {
                    val uri = message.message.toUri()
                    Glide.with(itemView.context)
                        .load(uri)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .into(imageView)
                    timestampText.text = formatTimestamp(message.timestamp)
                    imageView.setOnClickListener { (itemView.context as? FileHandler)?.openImage(uri) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load image: ${e.message}")
                    imageView.setImageResource(R.drawable.ic_image_error)
                    timestampText.text = formatTimestamp(message.timestamp)
                }
            }
        }
        
        private fun extractFirstImageFromPrompt(promptJson: String?): FileInfo? {
            if (promptJson.isNullOrBlank()) return null
            
            return try {
                val json = JsonUtils.fromJson(promptJson, GroupedFileMessage::class.java)
                json?.images?.firstOrNull()
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse prompt JSON for image info")
                null
            }
        }
    }

    inner class AiImageMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivImage)
        private val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        val btnReload: ImageButton = itemView.findViewById(R.id.btnReload)
        val btnPrevious: ImageButton = itemView.findViewById(R.id.btnAiPrevious)
        val btnNext: ImageButton = itemView.findViewById(R.id.btnAiNext)
        private val tvNavigationIndicator: TextView = itemView.findViewById(R.id.tvAiNavigationIndicator)
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

            // Show controls for messages with multiple versions OR latest AI messages
            val shouldShowControls = message.totalVersions > 1 || isLatestAiMessage
            
            if (shouldShowControls) {
                controlsLayout.visibility = View.VISIBLE
                btnCopy.visibility = if (isLatestAiMessage) View.VISIBLE else View.GONE
                btnReload.visibility = if (isLatestAiMessage) View.VISIBLE else View.GONE
                
                // Only show version navigation when there are multiple versions
                if (message.totalVersions > 1) {
                    btnPrevious.visibility = View.VISIBLE
                    btnNext.visibility = View.VISIBLE
                    tvNavigationIndicator.visibility = View.VISIBLE
                    tvNavigationIndicator.text = "${message.versionIndex + 1}/${message.totalVersions}"
                    
                    btnPrevious.isEnabled = message.versionIndex > 0
                    btnNext.isEnabled = message.versionIndex < message.totalVersions - 1
                    btnPrevious.alpha = if (btnPrevious.isEnabled) 1.0f else 0.3f
                    btnNext.alpha = if (btnNext.isEnabled) 1.0f else 0.3f
                    
                    tvNavigationIndicator.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
                } else {
                    // Hide version navigation for single version messages
                    btnPrevious.visibility = View.GONE
                    btnNext.visibility = View.GONE
                    tvNavigationIndicator.visibility = View.GONE
                }
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
                // Declare variables at function scope
                var fileName: String
                var fileSize: Long
                var fileType: String
                var extension: String
                var uri: Uri
                
                // Extract file info from JSON stored in prompt field
                val fileInfo = extractFirstDocumentFromPrompt(message.prompt)
                if (fileInfo != null) {
                    val context = itemView.context
                    uri = Uri.parse(fileInfo.uri)
                    fileName = fileInfo.name
                    fileSize = fileInfo.size
                    val mimeType = context.contentResolver.getType(uri) ?: fileInfo.type
                    
                    // Get file type from name or mime type
                    extension = fileName.substringAfterLast('.', "").lowercase()
                    fileType = when {
                        extension.isNotEmpty() -> FileUtil.getFileTypeFromName(fileName)
                        mimeType.isNotEmpty() -> getFileTypeFromMimeType(mimeType)
                        else -> "Document"
                    }

                    // Try to get full path using UriPathResolver
                    val fullPath = UriPathResolver.getRealPathFromURI(context, uri)
                    val displayText = fullPath ?: fileName
                    
                    // Truncate long paths for better display
                    val maxDisplayLength = 35
                    val finalDisplayText = if (displayText.length > maxDisplayLength) {
                        "...${displayText.takeLast(maxDisplayLength - 3)}"
                    } else {
                        displayText
                    }

                    docNameTextView.text = finalDisplayText
                    docTypeTextView.text = fileType
                    docSizeTextView.text = formatFileSize(fileSize)
                } else {
                    // Fallback to parsing message as URI (legacy support)
                    uri = message.message.toUri()
                    val context = itemView.context
                    fileName = FileUtil.getFileNameFromUri(context, uri) ?: (uri.lastPathSegment ?: "Document")
                    fileSize = FileUtil.getFileSizeFromUri(context, uri)
                    extension = fileName.substringAfterLast('.', "").lowercase()
                    fileType = FileUtil.getFileTypeFromName(fileName)

                    // Try to get full path using UriPathResolver
                    val fullPath = UriPathResolver.getRealPathFromURI(context, uri)
                    val displayText = fullPath ?: fileName
                    
                    // Truncate long paths for better display
                    val maxDisplayLength = 35
                    val finalDisplayText = if (displayText.length > maxDisplayLength) {
                        "...${displayText.takeLast(maxDisplayLength - 3)}"
                    } else {
                        displayText
                    }

                    docNameTextView.text = finalDisplayText
                    docTypeTextView.text = fileType
                    docSizeTextView.text = formatFileSize(fileSize)
                }

                // Set click listener and icon
                documentCardView.setOnClickListener { (itemView.context as? FileHandler)?.openFile(uri, fileName) }

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
        
        private fun extractFirstDocumentFromPrompt(promptJson: String?): FileInfo? {
            if (promptJson.isNullOrBlank()) return null
            
            return try {
                val json = JsonUtils.fromJson(promptJson, GroupedFileMessage::class.java)
                json?.documents?.firstOrNull()
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse prompt JSON for document info")
                null
            }
        }
        
        private fun getFileTypeFromMimeType(mimeType: String): String {
            return when {
                mimeType.startsWith("application/pdf") -> "PDF Document"
                mimeType.startsWith("application/msword") || mimeType.contains("wordprocessingml") -> "Word Document"
                mimeType.startsWith("application/vnd.ms-excel") || mimeType.contains("spreadsheetml") -> "Excel Spreadsheet"
                mimeType.startsWith("application/vnd.ms-powerpoint") || mimeType.contains("presentationml") -> "PowerPoint"
                mimeType.startsWith("text/") -> "Text File"
                mimeType.startsWith("image/") -> "Image"
                else -> "Document"
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
                Timber.d("🔧 BINDING grouped files message: ${message.message.take(50)}...")
                Timber.d("🔧 FULL message JSON: ${message.message}")
                
                // First validate that we have a non-empty message
                if (message.message.isNullOrBlank()) {
                    Timber.e("❌ BIND ERROR: Empty or null message content for grouped files")
                    showFallbackMessage("No file content available")
                    return
                }
                
                // CRITICAL FIX: Detect if message field contains AI response text instead of JSON
                // This was the root cause of "URI=The content you've shared appe..." issue
                if (!message.message.startsWith("{") && !message.message.startsWith("[")) {
                    Timber.e("🔧 DETECTED UI BUG: Message field contains AI response text instead of GroupedFileMessage JSON!")
                    Timber.e("🔧 PROBLEMATIC CONTENT: ${message.message.take(100)}...")
                    
                    // Try to fallback to prompt field which might have the correct JSON
                    if (!message.prompt.isNullOrBlank() && (message.prompt.startsWith("{") || message.prompt.startsWith("["))) {
                        Timber.w("🔧 ATTEMPTING FALLBACK: Using prompt field as JSON source")
                        val groupedMessageFromPrompt = JsonUtils.fromJson(message.prompt, GroupedFileMessage::class.java)
                        if (groupedMessageFromPrompt != null) {
                            Timber.w("✅ FALLBACK SUCCESS: Parsed GroupedFileMessage from prompt field")
                            renderGroupedFiles(groupedMessageFromPrompt)
                            return
                        } else {
                            Timber.e("❌ FALLBACK FAILED: Prompt field also not valid JSON")
                        }
                    }
                    
                    // Final fallback: show error with the AI response text issue
                    showFallbackMessage("Files display error - contact support if this persists")
                    return
                }
                
                val groupedMessage = JsonUtils.fromJson(message.message, GroupedFileMessage::class.java)

                if (groupedMessage == null) {
                    Timber.e("❌ BIND ERROR: Failed to parse GroupedFileMessage JSON: ${message.message}")
                    showFallbackMessage("Failed to load file information")
                    return
                }

                Timber.d("✅ BIND SUCCESS: Parsed GroupedFileMessage: images=${groupedMessage.images.size}, documents=${groupedMessage.documents.size}")
                
                // Render the files using the helper method
                renderGroupedFiles(groupedMessage)
                
            } catch (e: Exception) {
                Timber.e(e, "❌ BIND ERROR: Exception in UserGroupedFilesViewHolder.bind: ${e.message}")
                showFallbackMessage("Error displaying files")
            }
        }
        
        /**
         * Helper method to render grouped files from a valid GroupedFileMessage object
         */
        private fun renderGroupedFiles(groupedMessage: GroupedFileMessage) {
            try {
                // Log detailed info about what we're about to display
                Timber.d("🖼️ Images to display:")
                groupedMessage.images.forEach { img ->
                    Timber.d("  - Name: '${img.name}', Size: ${img.size} (${FileUtil.formatFileSize(img.size)}), URI: ${img.uri}")
                }
                Timber.d("📄 Documents to display:")
                groupedMessage.documents.forEach { doc ->
                    Timber.d("  - Name: '${doc.name}', Size: ${doc.size} (${FileUtil.formatFileSize(doc.size)}), MIME: ${doc.type}, URI: ${doc.uri}")
                }
                
                // Handle images
                if (groupedMessage.images.isNotEmpty()) {
                    imagesScrollView.visibility = View.VISIBLE
                    imagesContainer.removeAllViews()

                    for (imageInfo in groupedMessage.images) {
                        try {
                            Timber.d("Processing image: name='${imageInfo.name}', uri='${imageInfo.uri}', size=${imageInfo.size}")
                            
                            // Validate image info
                            if (imageInfo.uri.isBlank()) {
                                Timber.w("Skipping image with empty URI: ${imageInfo.name}")
                                continue
                            }
                            
                            // Preload image
                            Glide.with(itemView.context)
                                .load(Uri.parse(imageInfo.uri))
                                .preload()
                            
                            val imageView = createImageView(itemView.context, Uri.parse(imageInfo.uri), imageInfo.name)
                            imagesContainer.addView(imageView)
                        } catch (e: Exception) {
                            Timber.e(e, "Error creating image view for: ${imageInfo.name}")
                            // Continue with other images instead of failing completely
                        }
                    }
                    Timber.d("Added ${imagesContainer.childCount} image views")
                } else {
                    imagesScrollView.visibility = View.GONE
                }

                // Handle documents
                if (groupedMessage.documents.isNotEmpty()) {
                    documentsScrollView.visibility = View.VISIBLE
                    documentsContainer.removeAllViews()

                    for (docInfo in groupedMessage.documents) {
                        try {
                            Timber.d("Processing document: name='${docInfo.name}', uri='${docInfo.uri}', size=${docInfo.size}, mimeType='${docInfo.type}'")
                            
                            // Validate document info
                            if (docInfo.uri.isBlank()) {
                                Timber.w("⚠️ Skipping document with empty URI: name='${docInfo.name}'")
                                continue
                            }
                            if (docInfo.name.isBlank()) {
                                Timber.w("⚠️ Document has empty name, using fallback: uri='${docInfo.uri}'")
                            }
                            
                            val documentView = createDocumentView(
                                itemView.context,
                                Uri.parse(docInfo.uri),
                                docInfo.name,
                                docInfo.size,
                                docInfo.type // This is now the actual MIME type (e.g., "application/pdf")
                            )
                            documentsContainer.addView(documentView)
                        } catch (e: Exception) {
                            Timber.e(e, "Error creating document view for: ${docInfo.name}")
                            // Continue with other documents instead of failing completely
                        }
                    }
                    Timber.d("Added ${documentsContainer.childCount} document views")
                } else {
                    documentsScrollView.visibility = View.GONE
                }

                // Handle text message
                if (groupedMessage.text?.isNotEmpty() == true) {
                    messageCardView.visibility = View.VISIBLE
                    messageText.text = groupedMessage.text
                } else {
                    messageCardView.visibility = View.GONE
                }

            } catch (e: Exception) {
                Timber.e(e, "❌ RENDER ERROR: Exception in renderGroupedFiles: ${e.message}")
                showFallbackMessage("Error rendering files")
            }
        }
        
        /**
         * Show a fallback message when file display fails
         */
        private fun showFallbackMessage(errorMessage: String) {
            try {
                // Hide file containers
                imagesScrollView.visibility = View.GONE
                documentsScrollView.visibility = View.GONE
                
                // Show error message in text card
                messageCardView.visibility = View.VISIBLE
                messageText.text = "⚠️ $errorMessage"
                
                Timber.w("🔧 SHOWING FALLBACK: $errorMessage")
            } catch (e: Exception) {
                Timber.e(e, "❌ FALLBACK ERROR: Failed to show fallback message: ${e.message}")
            }
        }

        private fun createImageView(context: Context, uri: Uri, imageName: String = "Image"): View {
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

            cardView.setOnClickListener { 
                try {
                    when (context) {
                        is com.cyberflux.qwinai.MainActivity -> {
                            try {
                                context.fileHandler.openImage(uri)
                            } catch (e: UninitializedPropertyAccessException) {
                                Timber.w("FileHandler not initialized, opening image directly")
                                openImageDirectly(context, uri, imageName)
                            }
                        }
                        else -> {
                            Timber.w("Context is not MainActivity, attempting to open image directly")
                            openImageDirectly(context, uri, imageName)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error opening image: $imageName")
                    android.widget.Toast.makeText(context, "Unable to open image: $imageName", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return cardView
        }

        private fun createDocumentView(context: Context, uri: Uri, fileName: String, fileSize: Long, fileType: String): View {
            Timber.d("Creating document view: name='$fileName', size=$fileSize, type='$fileType', uri='$uri'")
            
            val cardView = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.document_thumbnail_width), 
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = context.resources.getDimensionPixelSize(R.dimen.document_thumbnail_margin)
                }
                radius = context.resources.getDimension(R.dimen.document_corner_radius)
                cardElevation = context.resources.getDimension(R.dimen.document_elevation)
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.document_background))
                strokeWidth = 1
                strokeColor = ContextCompat.getColor(context, R.color.document_stroke)
            }
            
            val documentContentView = LayoutInflater.from(context).inflate(R.layout.item_document_thumbnail, null)
            
            // Get file extension for icon determination
            val extension = if (fileName.isNotBlank()) {
                fileName.substringAfterLast('.', "").lowercase()
            } else {
                ""
            }
            
            // Set file name with fallback
            val displayName = if (fileName.isNotBlank()) fileName else "Unknown File"
            documentContentView.findViewById<TextView>(R.id.tvDocName)?.text = displayName
            
            // Set file type with fallback
            val displayType = when {
                fileType.isNotBlank() -> fileType
                extension.isNotBlank() -> extension.uppercase() + " Document"
                else -> "Document"
            }
            documentContentView.findViewById<TextView>(R.id.tvDocType)?.text = displayType
            
            // Set file size with proper formatting
            val displaySize = if (fileSize > 0) {
                FileUtil.formatFileSize(fileSize)
            } else {
                "Unknown size"
            }
            documentContentView.findViewById<TextView>(R.id.tvDocSize)?.text = displaySize
            
            // Set appropriate icon
            val iconView = documentContentView.findViewById<ImageView>(R.id.ivDocumentIcon)
            val iconResId = when (extension) {
                "pdf" -> getDrawableResourceSafely(context, "ic_file_pdf", R.drawable.ic_document)
                "doc", "docx" -> getDrawableResourceSafely(context, "ic_file_word", R.drawable.ic_document)
                "xls", "xlsx" -> getDrawableResourceSafely(context, "ic_file_excel", R.drawable.ic_document)
                "ppt", "pptx" -> getDrawableResourceSafely(context, "ic_file_powerpoint", R.drawable.ic_document)
                "txt" -> getDrawableResourceSafely(context, "ic_file_text", R.drawable.ic_document)
                "zip", "rar", "7z" -> getDrawableResourceSafely(context, "ic_file_archive", R.drawable.ic_document)
                else -> R.drawable.ic_document
            }
            
            iconView?.let { icon ->
                try {
                    icon.setImageResource(iconResId)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        icon.imageTintList = null
                    }
                    Timber.d("Set document icon for extension '$extension': $iconResId")
                } catch (e: Exception) {
                    Timber.e(e, "Error setting document icon for extension '$extension'")
                    try {
                        icon.setImageResource(R.drawable.ic_document)
                    } catch (fallbackError: Exception) {
                        Timber.e(fallbackError, "Error setting fallback document icon")
                    }
                }
            }
            
            cardView.addView(documentContentView)
            cardView.setOnClickListener { 
                try {
                    when (context) {
                        is com.cyberflux.qwinai.MainActivity -> {
                            try {
                                context.fileHandler.openFile(uri, fileName)
                            } catch (e: UninitializedPropertyAccessException) {
                                Timber.w("FileHandler not initialized, opening file directly")
                                openFileDirectly(context, uri, fileName)
                            }
                        }
                        else -> {
                            Timber.w("Context is not MainActivity, attempting to open file directly")
                            openFileDirectly(context, uri, fileName)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error opening file: $fileName")
                    android.widget.Toast.makeText(context, "Unable to open file: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return cardView
        }
        
        private fun getDrawableResourceSafely(context: Context, resourceName: String, fallback: Int): Int {
            return try {
                val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
                if (resourceId != 0) {
                    resourceId
                } else {
                    Timber.w("Drawable resource not found: $resourceName, using fallback")
                    fallback
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting drawable resource: $resourceName")
                fallback
            }
        }
        
        private fun openFileDirectly(context: Context, uri: Uri, fileName: String) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // Check if there's an app that can handle this file
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    android.widget.Toast.makeText(context, "No app found to open this file type", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error opening file directly: $fileName")
                android.widget.Toast.makeText(context, "Cannot open file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun openImageDirectly(context: Context, uri: Uri, imageName: String) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // Check if there's an app that can handle this image
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    android.widget.Toast.makeText(context, "No app found to open images", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error opening image directly: $imageName")
                android.widget.Toast.makeText(context, "Cannot open image: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
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
        
        // 🐛 DEBUG: Log what ChatAdapter receives
        Timber.d("🐛🐛🐛 CHATADAPTER RECEIVING ${list.size} MESSAGES:")
        list.forEach { message ->
            if (!message.isUser) { // Only log AI messages for debugging
                Timber.d("🐛 ChatAdapter got message ${message.id}:")
                Timber.d("🐛   - isGenerating: ${message.isGenerating}")
                Timber.d("🐛   - isLoading: ${message.isLoading}")
                Timber.d("🐛   - showButtons: ${message.showButtons}")
                Timber.d("🐛   - content length: ${message.message.length}")
                Timber.d("🐛   - content preview: '${message.message.take(100)}...'")
            }
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

    /**
     * Data class for search image information
     */
    data class SearchImageData(
        val url: String,
        val alt: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val thumbnail: String? = null,
        val title: String? = null,
        val source: String? = null
    )

    /**
     * Adapter for displaying search images in horizontal RecyclerView
     */
    class SearchImagesAdapter(
        private val images: List<SearchImageData>,
        private val onImageClick: (String) -> Unit
    ) : RecyclerView.Adapter<SearchImagesAdapter.ImageViewHolder>() {

        class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.searchImageView)
            val titleView: TextView = itemView.findViewById(R.id.searchImageTitle)
            val sourceView: TextView = itemView.findViewById(R.id.searchImageSource)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val image = images[position]

            // Load image using Glide
            Glide.with(holder.itemView.context)
                .load(image.thumbnail ?: image.url)
                .placeholder(R.drawable.image_placeholder_background)
                .error(R.drawable.image_placeholder_background)
                .centerCrop()
                .into(holder.imageView)

            // Set title
            holder.titleView.text = image.title ?: image.alt ?: "Image"

            // Set source
            holder.sourceView.text = image.source ?: "Unknown source"

            // Set click listener
            holder.itemView.setOnClickListener {
                onImageClick(image.url)
            }
        }

        override fun getItemCount(): Int = images.size
    }



}