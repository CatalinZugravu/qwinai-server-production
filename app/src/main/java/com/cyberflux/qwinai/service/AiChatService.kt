package com.cyberflux.qwinai.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.cyberflux.qwinai.ConversationsViewModel
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.branch.MessageManager
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.ResponseLength
import com.cyberflux.qwinai.model.ResponseTone
import com.cyberflux.qwinai.network.AimlApiRequest
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.network.AimlApiService
import com.cyberflux.qwinai.network.ModelApiHandler
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.network.StreamingHandler
import com.cyberflux.qwinai.tools.ToolServiceHelper
import com.cyberflux.qwinai.utils.SimplifiedTokenManager
import com.cyberflux.qwinai.utils.SimplifiedDocumentExtractor
import com.cyberflux.qwinai.utils.SimpleSafeDocumentExtractor
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.JsonUtils
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.TokenValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

/**
 * Enhanced service class that handles all AI API calls and message processing
 * with smart web search detection and enhanced tool calls support
 */
class AiChatService(
    private val context: Context,
    private val conversationsViewModel: ConversationsViewModel,
    private val chatAdapter: ChatAdapter,
    private val coroutineScope: CoroutineScope,
    private val callbacks: Callbacks,
    private val messageManager: MessageManager,
    private val tokenManager: SimplifiedTokenManager,
    var isStreamingActive: Boolean = false,
    private var userName: String = "user",

) {
    // Define the updated callback interface for state updates and UI interactions
    interface Callbacks {
        // State getters
        fun isGenerating(): Boolean
        fun isSubscribed(): Boolean
        fun getFreeMessagesLeft(): Int

        // Updated button state getters
        fun getWebSearchEnabled(): Boolean

        // Response preferences getters
        fun getResponseLength(): ResponseLength
        fun getResponseTone(): ResponseTone

        // State setters
        fun setGeneratingState(generating: Boolean)
        fun decrementFreeMessages()
        fun incrementFreeMessages()

        // Updated button state setters
        fun setWebSearchEnabled(enabled: Boolean)

        // UI update handlers
        fun updateDeepSearchSetting(enabled: Boolean)

        // UI updates
        fun updateTypingIndicator(visible: Boolean)
        fun updateMessageInAdapter(message: ChatMessage)
        fun forceUpdateMessageInAdapter(message: ChatMessage)
        fun updateMessageInGroups(message: ChatMessage)
        fun showError(message: String)
        fun scrollToBottom()
        fun markFilesWithError(fileUris: List<Uri>)

        // Message group management
        fun getCurrentAiGroupId(): String?
        fun setCurrentAiGroupId(groupId: String?)
        fun getAiResponseGroups(): MutableMap<String, MutableList<ChatMessage>>
        fun getCurrentMessageIndex(): MutableMap<String, Int>
        fun runOnUiThread(action: () -> Unit)
        fun suggestAlternativeModel(message: String)

        // Additional needed callbacks
        fun onMessageCompleted(messageId: String, content: String)
        fun getCurrentMessages(): List<ChatMessage>
        fun saveMessage(message: ChatMessage)

        // Button visibility callbacks
        fun shouldShowWebSearchButton(): Boolean
        fun updateButtonVisibility()
    }

    // Service-specific properties
    private var currentApiJob: Job? = null
    private var streamingJob: Job? = null

    // Create a private service scope for internal operations with proper cancellation handling
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + CoroutineName("AiChatService"))
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("AiChatService-BG"))
    private val toolManager by lazy { ToolServiceHelper.getToolManager(context) }

    /**
     * Main method to send a message with smart web search auto-detection
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun sendMessage(
        message: String,
        hasFiles: Boolean,
        fileUris: List<Uri>,
        userMessageId: String,
        createOrGetConversation: (String) -> String,
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low",
        selectedFiles: List<FileUtil.FileUtil.SelectedFile>? = null
    ) {
        val currentModel = ModelManager.selectedModel

        // Check if we have enough credits (for non-subscribers) - need 2 credits per message
        if (!callbacks.isSubscribed() && !currentModel.isFree && callbacks.getFreeMessagesLeft() < 2) {
            callbacks.showError("You need at least 2 credits to send a message. Get Pro or watch ads for more credits.")
            return
        }
        // Check if current model is allowed to generate images
        ModelValidator.isImageGenerator(currentModel.id)

        // ALWAYS ENABLE TOOLS FOR AI MODELS - Let AI decide when to use them
        val currentWebSearchEnabled = callbacks.getWebSearchEnabled()
        // Always enable web search tools if model supports function calling
        val effectiveWebSearchEnabled = ModelValidator.supportsFunctionCalling(currentModel.id)
        
        if (effectiveWebSearchEnabled && !currentWebSearchEnabled) {
            Timber.d("🔧 AUTO-ENABLED tools for AI model: ${currentModel.displayName}")
        }

        // Handle file validation and message sending
        if (validateFiles(fileUris, message)) {
            val conversationIdString = createOrGetConversation(message)

            if (hasFiles) {
                serviceScope.launch {
                    processAndSendFilesWithFileSupportedModelAsync(
                        conversationIdString,
                        message,
                        fileUris,
                        userMessageId,
                        effectiveWebSearchEnabled,
                        isReasoningEnabled,
                        reasoningLevel,
                        selectedFiles
                    )
                }
            } else if (message.isNotBlank()) {
                serviceScope.launch {
                    handleTextOnlyMessageAsync(
                        conversationIdString,
                        message,
                        userMessageId,
                        isReasoningEnabled,
                        reasoningLevel
                    )
                }
            }
        } else {
            // File validation failed - reset UI state
            Timber.w("File validation failed, resetting UI state")
            callbacks.setGeneratingState(false)
            callbacks.updateTypingIndicator(false)
            // Mark failed files with error state
            markFilesWithError(fileUris)
        }
    }

    /**
     * Handle text-only messages with smart web search analysis
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun handleTextOnlyMessageAsync(
        conversationId: String,
        message: String,
        userMessageId: String,
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low"
    ) {
        try {
            // Validate token limit before proceeding
            val isSubscribed = callbacks.isSubscribed()
            val modelId = ModelManager.selectedModel.id

            if (!validateTokenLimit(message, modelId, isSubscribed)) {
                withContext(Dispatchers.Main) {
                    callbacks.setGeneratingState(false)
                }
                return
            }

            // Preload conversation context
            messageManager.preloadMessagesForContext(conversationId)
            // Remove all web search detection - let AI model decide when to use tools
            val currentModel = ModelManager.selectedModel
            val webSearchEnabled = ModelValidator.supportsFunctionCalling(currentModel.id)
            
            Timber.d("🔧 Tools enabled for ${currentModel.displayName}: $webSearchEnabled")

            // Simply enable tools if model supports function calling
            val useWebSearch = webSearchEnabled
            
            Timber.d("🔧 Using tools for this request: $useWebSearch")

            Timber.d("🧠 Analysis - Tools enabled: $useWebSearch")
            Timber.d("   - Message: ${message.take(50)}...")
            Timber.d("   - Model: ${currentModel.displayName}")

            // Create user message
            val userMessage = ChatMessage(
                id = userMessageId,
                conversationId = conversationId,
                message = message,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                isForceSearch = useWebSearch
            )

            withContext(Dispatchers.Main) {
                messageManager.addMessage(userMessage)
                // Remove delay for instant indicator display

                // Verify message was added
                val adapterMessages = chatAdapter.currentList
                if (!adapterMessages.any { it.id == userMessageId }) {
                    Timber.e("User message not found in adapter after adding")
                    callbacks.showError("Error adding message to chat. Please try again.")
                    return@withContext
                }
            }

            // Handle credits deduction
            if (!callbacks.isSubscribed() && !ModelManager.selectedModel.isFree) {
                callbacks.decrementFreeMessages()
            }

            withContext(Dispatchers.Main) {
                callbacks.setGeneratingState(true)
                callbacks.updateTypingIndicator(true)
            }

            // SMART INDICATOR TEXT based on what will happen
            // SMART INDICATOR TEXT based on what will happen
            val (indicatorText, indicatorColor, isWebSearchActive) = when {
                useWebSearch -> {
                    Triple("Web search available", "#2563EB".toColorInt(), true)
                }
                else -> {
                    // Determine if a specific tool is being used
                    val toolId = toolManager.findToolForMessage(message)?.id
                    if (toolId != null) {
                        val (text, color) = getToolStatusText(toolId)
                        Triple(text, color, false)
                    } else {
                        Triple("Generating response", "#757575".toColorInt(), false)
                    }
                }
            }

            // Create AI message with proper state flags
            val aiMessageId = UUID.randomUUID().toString()
            val aiMessage = ChatMessage(
                id = aiMessageId,
                conversationId = conversationId,
                message = "",
                isUser = false, // CRITICAL: AI messages must NEVER be user messages
                timestamp = System.currentTimeMillis(),
                isGenerating = !isWebSearchActive,
                showButtons = false,
                modelId = modelId,
                aiModel = ModelManager.selectedModel.displayName,
                parentMessageId = userMessageId,
                isWebSearchActive = isWebSearchActive,
                isForceSearch = useWebSearch,
                initialIndicatorText = indicatorText,
                initialIndicatorColor = indicatorColor,
                isImage = false, // AI responses are never images
                isDocument = false // AI responses are never documents
            )

            withContext(Dispatchers.Main) {
                messageManager.addMessage(aiMessage)
                // Remove delay for instant indicator display

                // Verify AI message was added
                val adapterMessages = chatAdapter.currentList
                if (!adapterMessages.any { it.id == aiMessage.id }) {
                    Timber.e("AI message not found in adapter after adding")
                    callbacks.showError("Error creating AI response. Please try again.")
                    callbacks.setGeneratingState(false)
                    return@withContext
                }

                // Start enhanced API call - tools enabled automatically
                currentApiJob = startEnhancedApiCall(
                    conversationId = conversationId,
                    messageId = aiMessage.id,
                    isSubscribed = callbacks.isSubscribed(),
                    isModelFree = ModelManager.selectedModel.isFree,
                    enableWebSearch = useWebSearch,
                    originalMessageText = message,
                    webSearchParams = emptyMap(), // Let AI decide when to use tools
                    isReasoningEnabled = isReasoningEnabled,
                    reasoningLevel = reasoningLevel
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in enhanced handleTextOnlyMessageAsync: ${e.message}")
            withContext(Dispatchers.Main) {
                // Show user-friendly error message based on exception type
                val errorMessage = when (e) {
                    is java.net.SocketTimeoutException -> "Request timed out. Please try again."
                    is java.net.UnknownHostException -> "No internet connection. Please check your connection."
                    is java.net.ConnectException -> "Connection failed. Please try again later."
                    is java.io.IOException -> "Network error. Please check your connection."
                    else -> "Generation error: Please try again"
                }
                callbacks.showError(errorMessage)
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)
                
                // CRITICAL: Ensure full UI reset on any error
                stopStreamingMode()
            }
        }
    }    /**
     * Process files with smart web search support
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processAndSendFilesWithFileSupportedModelAsync(
        conversationId: String,
        userText: String,
        fileUris: List<Uri>,
        userMessageId: String,
        forceWebSearch: Boolean = false,
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low",
        selectedFiles: List<FileUtil.FileUtil.SelectedFile>? = null
    ) {
        try {
            withContext(Dispatchers.Main) {
                callbacks.setGeneratingState(true)
                callbacks.updateTypingIndicator(true)
            }

            // Track credits for non-subscribers
            if (!callbacks.isSubscribed() && !ModelManager.selectedModel.isFree) {
                callbacks.decrementFreeMessages()
            }

            val fileInfos = mutableListOf<ChatAdapter.FileInfo>()
            val processedFiles = mutableListOf<Uri>()
            val processedImages = mutableListOf<ProcessedImageInfo>()
            val extractedDocuments = mutableListOf<ExtractedDocumentInfo>()

            // Process files using pre-extracted metadata when available
            Timber.d("Processing ${fileUris.size} files for AI model")
            for ((index, fileUri) in fileUris.withIndex()) {
                try {
                    // Use metadata from selectedFiles if available, otherwise fallback to extraction
                    val selectedFile = selectedFiles?.getOrNull(index)
                    val fileName: String
                    val fileSize: Long
                    val mimeType: String
                    
                    if (selectedFile != null) {
                        // Use pre-extracted metadata
                        fileName = selectedFile.name
                        fileSize = selectedFile.size
                        mimeType = context.contentResolver.getType(fileUri) ?: 
                                  FileUtil.getMimeType(context, fileUri)
                        Timber.d("Using pre-extracted metadata for: $fileName")
                    } else {
                        // Fallback to extraction (legacy path)
                        mimeType = context.contentResolver.getType(fileUri) ?: ""
                        fileName = getFileName(fileUri)
                        fileSize = getFileSize(fileUri)
                        Timber.w("No pre-extracted metadata available, extracting for: $fileName")
                    }
                    
                    val isImage = mimeType.startsWith("image/")
                    
                    Timber.d("📋 File analysis: $fileName")
                    Timber.d("  📁 MIME type: $mimeType")
                    Timber.d("  🖼️ Is image: $isImage")
                    Timber.d("  📏 File size: ${FileUtil.formatFileSize(fileSize)} (raw: $fileSize bytes)")
                    
                    // Log exactly what's going into FileInfo
                    Timber.d("🔄 Creating FileInfo with:")
                    Timber.d("  📍 URI: ${fileUri.toString()}")
                    Timber.d("  📝 Name: '$fileName'")
                    Timber.d("  📊 Size: $fileSize")
                    Timber.d("  🏷️ MIME Type: '$mimeType' (${if (isImage) "image" else "document"})")

                    fileInfos.add(ChatAdapter.FileInfo(
                        uri = fileUri.toString(),
                        name = fileName,
                        size = fileSize,
                        type = mimeType // Store the actual MIME type, not just "image"/"document"
                    ))
                    processedFiles.add(fileUri)

                    if (isImage) {
                        processedImages.add(ProcessedImageInfo(fileUri.toString(), "", fileName))
                        Timber.d("Added image to processedImages: $fileName")
                    } else {
                        // Document processing logic
                        val currentModel = ModelManager.selectedModel
                        val hasNativeSupport = ModelValidator.hasNativeDocumentSupport(currentModel.id)
                        val isOcrModel = currentModel.isOcrModel
                        
                        Timber.d("Processing document file: $fileName (URI: $fileUri)")
                        Timber.d("Current model: ${currentModel.displayName} (${currentModel.id})")
                        Timber.d("Native document support: $hasNativeSupport")
                        Timber.d("Is OCR model: $isOcrModel")
                        
                        // Skip extraction for OCR models - they need raw files
                        if (isOcrModel) {
                            Timber.d("Skipping content extraction for OCR model - will send raw file to OCR endpoint")
                            // For OCR models, just add the file info without extraction
                            extractedDocuments.add(ExtractedDocumentInfo(
                                name = fileName,
                                mimeType = mimeType,
                                size = fileSize,
                                extractedContent = "" // Empty content - raw file will be used
                            ))
                            continue
                        }
                        
                        // For non-OCR models: extract content for AI to read
                        Timber.d("Extracting content for non-OCR model to ensure AI can read document text")

                        val fileUriString = fileUri.toString()
                        var extractedContent = SimpleSafeDocumentExtractor.getCachedContent(fileUriString)

                        if (extractedContent == null && context is MainActivity) {
                            Timber.d("Direct cache lookup failed, trying via selected files")
                            val selectedFile = context.selectedFiles.find {
                                it.uri.toString() == fileUriString
                            }

                            if (selectedFile != null) {
                                Timber.d("Found file in selected files: ${selectedFile.name}, extracted=${selectedFile.isExtracted}")

                                if (selectedFile.isExtracted && selectedFile.extractedContentId.isNotEmpty()) {
                                    Timber.d("Looking up content with ID: ${selectedFile.extractedContentId}")
                                    extractedContent = SimpleSafeDocumentExtractor.getCachedContent(selectedFile.extractedContentId)
                                }
                            }
                        }

                        if (extractedContent == null) {
                            Timber.d("Cache lookup failed, extracting content on the fly for file: $fileName")
                            val tempExtractor = SimpleSafeDocumentExtractor(context)
                            val result = withContext(Dispatchers.IO) {
                                tempExtractor.extractContent(fileUri)
                            }

                            when (result) {
                                is SimpleSafeDocumentExtractor.ExtractionResult.Success -> {
                                    extractedContent = result.content
                                    Timber.d("On-the-fly extraction successful: ${extractedContent.length} chars extracted from $fileName")
                                    // Cache the content for potential reuse
                                    SimpleSafeDocumentExtractor.cacheContent(fileUriString, extractedContent)
                                }
                                is SimpleSafeDocumentExtractor.ExtractionResult.Error -> {
                                    Timber.e("On-the-fly extraction failed for $fileName: ${result.message}")
                                    callbacks.showError("Failed to extract content from $fileName: ${result.message}")
                                }
                            }
                        }

                        if (extractedContent != null) {
                            val contentLength = extractedContent.length
                            val tokenCount = TokenValidator.estimateTokenCount(extractedContent)
                            Timber.d("Adding document with extracted content: $fileName ($contentLength chars, $tokenCount tokens)")
                            
                            // Use the extracted content directly (it's already formatted)
                            extractedDocuments.add(
                                ExtractedDocumentInfo(
                                    name = fileName,
                                    mimeType = mimeType,
                                    size = fileSize,
                                    extractedContent = extractedContent
                                )
                            )
                        } else {
                            Timber.w("No extracted content available for $fileName - this may cause AI model to report missing content")
                            val failureMessage = """
                                FILE INFORMATION:
                                Filename: $fileName
                                Type: $mimeType
                                Size: ${FileUtil.formatFileSize(fileSize)}
                                URI: $fileUri
                                
                                ⚠️ EXTRACTION FAILED: Unable to extract text content from this document.
                                This could be due to:
                                - Encrypted or password-protected file
                                - Corrupted file format
                                - Unsupported document structure
                                - File access permissions
                                
                                Please try re-uploading the file or use a different format.
                            """.trimIndent()
                            
                            extractedDocuments.add(
                                ExtractedDocumentInfo(
                                    name = fileName,
                                    mimeType = mimeType,
                                    size = fileSize,
                                    extractedContent = failureMessage
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing file: $fileUri - ${e.message}")
                }
            }

            Timber.d("Processed files summary:")
            Timber.d("- ${processedImages.size} images")
            Timber.d("- ${extractedDocuments.size} documents with extracted content")

            if (processedFiles.isEmpty() && userText.isBlank()) {
                withContext(Dispatchers.Main) {
                    callbacks.setGeneratingState(false)
                    callbacks.updateTypingIndicator(false)
                    callbacks.showError("Failed to process any files")
                }
                return
            }

            // Create user message with files
            val userVisibleText = userText
            val aiOnlyText = buildAiModelPrompt(userText, processedImages, extractedDocuments)
            Timber.d("AI-only text length: ${aiOnlyText.length} chars")
            
            // Debug: Log a preview of the content being sent to AI
            if (extractedDocuments.isNotEmpty()) {
                Timber.d("AI will receive ${extractedDocuments.size} extracted documents:")
                extractedDocuments.forEachIndexed { index, doc ->
                    val contentPreview = doc.extractedContent.take(100) + if (doc.extractedContent.length > 100) "..." else ""
                    Timber.d("  Document ${index+1}: ${doc.name} - ${doc.extractedContent.length} chars")
                    Timber.d("  Content preview: $contentPreview")
                }
            }

            // CRITICAL FIX: For documents with extracted content, don't send original file to AI
            // Only send images and documents that failed extraction
            val documentsForDisplay = fileInfos.filter { !it.type.startsWith("image/") }
            val imagesForProcessing = fileInfos.filter { it.type.startsWith("image/") }
            
            // Log file processing results
            Timber.d("🎯 Creating GroupedFileMessage:")
            Timber.d("  🖼️ Images (${imagesForProcessing.size}):")
            imagesForProcessing.forEach { img ->
                Timber.d("    - ${img.name} (${FileUtil.formatFileSize(img.size)}) [${img.uri}]")
            }
            Timber.d("  📄 Documents (${documentsForDisplay.size}):")
            documentsForDisplay.forEach { doc ->
                Timber.d("    - ${doc.name} (${FileUtil.formatFileSize(doc.size)}) [${doc.uri}]")
            }
            Timber.d("  📝 Text: '${userVisibleText?.take(50)}${if ((userVisibleText?.length ?: 0) > 50) "..." else ""}'")
            
            val groupedMessage = ChatAdapter.GroupedFileMessage(
                images = imagesForProcessing,
                documents = documentsForDisplay, // Keep for display purposes
                text = userVisibleText
            )

            val messageJson = JsonUtils.toJson(groupedMessage)
            Timber.d("💾 GroupedFileMessage JSON: ${messageJson?.take(200)}${if ((messageJson?.length ?: 0) > 200) "..." else ""}")

            // Create human-readable message for display
            val userDisplayMessage = buildString {
                if (userVisibleText?.isNotBlank() == true) {
                    append(userVisibleText)
                    append("\n\n")
                }
                append("📎 ")
                val totalFiles = imagesForProcessing.size + documentsForDisplay.size
                append("$totalFiles file(s) attached:")
                
                imagesForProcessing.forEach { image ->
                    append("\n🖼️ ${image.name}")
                }
                documentsForDisplay.forEach { doc ->
                    append("\n📄 ${doc.name}")
                }
            }

            // Create serialized attachments data for ConversationAttachmentsManager
            val attachmentsJson = try {
                val attachmentsList = fileInfos.map { fileInfo ->
                    mapOf(
                        "uri" to fileInfo.uri.toString(),
                        "name" to fileInfo.name,
                        "size" to fileInfo.size,
                        "type" to fileInfo.type,
                        "mimeType" to fileInfo.type
                    )
                }
                JsonUtils.toJson(attachmentsList)
            } catch (e: Exception) {
                Timber.e(e, "Error serializing attachments")
                null
            }

            val userFileMessage = ChatMessage(
                id = userMessageId,
                message = userDisplayMessage,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                conversationId = conversationId,
                isImage = fileInfos.any { it.type.startsWith("image/") },
                isDocument = fileInfos.any { !it.type.startsWith("image/") },
                prompt = messageJson, // Store JSON in prompt field for AI processing
                isForceSearch = forceWebSearch,
                attachments = attachmentsJson // Store file attachments for ConversationAttachmentsManager
            )

            withContext(Dispatchers.Main) {
                messageManager.addMessage(userFileMessage)
                // Remove delay for instant indicator display
                
                // Note: No need to check chatAdapter.currentList immediately since submitList is async
                // The message will appear in the adapter after the async operation completes
            }

            if (!conversationId.startsWith("private_")) {
                callbacks.saveMessage(userFileMessage)
            }

            // Get current button states and analyze web search need
            callbacks.getWebSearchEnabled() || forceWebSearch

            // CRITICAL FIX: Disable automatic web search when files are present
            // Files contain the information the user is asking about, no need to search the web
            val smartWebSearchEnabled = false
            val needsWebSearch = false
            val webSearchParams = emptyMap<String, String>()
            
            Timber.d("🚫 Auto web search DISABLED for file processing - files contain the needed information")

            // Determine indicator text and states
            val (indicatorText, indicatorColor, isWebSearchActive) = when {
                smartWebSearchEnabled -> {
                    // Web search takes priority
                    val searchType = if (needsWebSearch) "Smart web search" else "Web search"
                    Triple("$searchType with files", "#2563EB".toColorInt(), true)
                }
                else -> {
                    // Determine specific file type for better indicator text
                    val hasImages = imagesForProcessing.isNotEmpty()
                    val hasDocuments = documentsForDisplay.isNotEmpty()
                    
                    val indicatorText = when {
                        hasImages && !hasDocuments -> "Processing image"
                        !hasImages && hasDocuments -> "Processing pdf file"
                        hasImages && hasDocuments -> "Processing files"
                        else -> "Processing files"
                    }
                    
                    Triple(indicatorText, "#757575".toColorInt(), false)
                }
            }

            Timber.d("🎯 Creating AI message for file processing with smart analysis - WebSearch: $smartWebSearchEnabled")

            // Create AI response with proper state flags
            val aiMessageId = UUID.randomUUID().toString()
            val aiMessage = ChatMessage(
                id = aiMessageId,
                conversationId = conversationId,
                message = "",
                isUser = false, // CRITICAL: AI messages must NEVER be user messages
                timestamp = System.currentTimeMillis(),
                isGenerating = true,
                showButtons = false,
                modelId = ModelManager.selectedModel.id,
                aiModel = ModelManager.selectedModel.displayName,
                parentMessageId = userMessageId,
                isWebSearchActive = isWebSearchActive,
                isForceSearch = smartWebSearchEnabled,
                initialIndicatorText = indicatorText,
                initialIndicatorColor = indicatorColor,
                isImage = false, // AI responses are never images
                isDocument = false // AI responses are never documents
            )

            withContext(Dispatchers.Main) {
                messageManager.addMessage(aiMessage)
                // Remove delay for instant indicator display
                
                // Note: No need to check chatAdapter.currentList immediately since submitList is async
                // The message will appear in the adapter after the async operation completes
                
                callbacks.scrollToBottom()
            }

            if (!conversationId.startsWith("private_")) {
                callbacks.saveMessage(aiMessage)
            }

            // Check if this is an OCR model and handle differently
            if (ModelManager.selectedModel.isOcrModel && fileUris.isNotEmpty()) {
                Timber.d("🔍 OCR model detected with files - routing to OCR endpoint")
                
                // Find the AI message position for updates
                val aiMessagePosition = withContext(Dispatchers.Main) {
                    val messages = chatAdapter.currentList
                    messages.indexOfFirst { it.id == aiMessageId }
                }
                
                processOcrRequest(
                    messagePosition = aiMessagePosition,
                    originalMessageText = aiOnlyText,
                    messageId = aiMessageId,
                    fileUris = fileUris
                )
            } else {
                // Start enhanced API call with AI-only text and web search params
                withContext(Dispatchers.Main) {
                    currentApiJob = startEnhancedApiCall(
                        conversationId = conversationId,
                        messageId = aiMessageId,
                        isSubscribed = callbacks.isSubscribed(),
                        isModelFree = ModelManager.selectedModel.isFree,
                        enableWebSearch = smartWebSearchEnabled,
                        originalMessageText = aiOnlyText,
                        webSearchParams = webSearchParams,
                        isReasoningEnabled = isReasoningEnabled,
                        reasoningLevel = reasoningLevel
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing and sending files: ${e.message}")
            withContext(Dispatchers.Main) {
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)
                callbacks.showError("Generation error: Please try again")
                
                // CRITICAL: Ensure full UI reset on error
                stopStreamingMode()
            }
        }
    }

    /**
     * Start enhanced API call with web search parameters
     */
// Update the startEnhancedApiCall method in AiChatService.kt
// to properly pass the web search flag to StreamingHandler

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun startEnhancedApiCall(
        conversationId: String,
        messageId: String,
        isSubscribed: Boolean,
        isModelFree: Boolean,
        enableWebSearch: Boolean = false,  // This is the parameter we need to use correctly
        originalMessageText: String? = null,
        webSearchParams: Map<String, String> = emptyMap(),
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low"
    ): Job {
        return serviceScope.launch {
            var needToRefundCredits = false

            try {
                Timber.tag("ENHANCED-API").d("Starting enhanced AI message process for messageId: $messageId")

                // IMPORTANT: Log the web search status at the beginning
                Timber.d("Web search enabled for this request: $enableWebSearch")

                // CRITICAL: Start streaming mode immediately before any API processing
                withContext(Dispatchers.Main) {
                    chatAdapter.startStreamingMode()
                    messageManager.startStreamingMode()
                    callbacks.setGeneratingState(true)
                }

                // Handle credits deduction for reloaded messages
                if (!isSubscribed && !isModelFree) {
                    callbacks.decrementFreeMessages()
                }

                messageManager.ensureConversationContextLoaded(conversationId)
                verifyMessageContextEnhanced(conversationId)

                val adapterMessages = withContext(Dispatchers.Main) {
                    chatAdapter.currentList
                }

                var messagePosition = adapterMessages.indexOfFirst { it.id == messageId }

                if (messagePosition == -1) {
                    // Recovery logic for missing message
                    val managerMessages = messageManager.getCurrentMessages()
                    val messageInManager = managerMessages.find { it.id == messageId }

                    if (messageInManager != null) {
                        withContext(Dispatchers.Main) {
                            val currentList = chatAdapter.currentList.toMutableList()
                            if (!currentList.any { it.id == messageId }) {
                                currentList.add(messageInManager)
                                currentList.sortBy { it.timestamp }
                                chatAdapter.submitList(currentList)
                                delay(100)

                                messagePosition = chatAdapter.currentList.indexOfFirst { it.id == messageId }
                                if (messagePosition == -1) {
                                    callbacks.showError("Could not add message to chat. Please try again.")
                                    needToRefundCredits = true
                                    return@withContext
                                }
                            } else {
                                messagePosition = currentList.indexOfFirst { it.id == messageId }
                            }
                        }
                    } else {
                        Timber.e("Message $messageId not found anywhere")
                        withContext(Dispatchers.Main) {
                            callbacks.showError("Message not found. Please try again.")
                            needToRefundCredits = true
                            callbacks.setGeneratingState(false)
                        }
                        return@launch
                    }
                }

                // Continue with enhanced API call - pass enableWebSearch correctly
                continueWithEnhancedApiCall(
                    messagePosition = messagePosition,
                    conversationId = conversationId,
                    originalMessageText = originalMessageText,
                    messageId = messageId,
                    modelId = ModelManager.selectedModel.id,
                    enableWebSearch = enableWebSearch,  // Pass the correct flag value
                    webSearchParams = webSearchParams,
                    isReasoningEnabled = isReasoningEnabled,
                    reasoningLevel = reasoningLevel
                )

            } catch (_: CancellationException) {
                Timber.d("Enhanced API request cancelled by user")
                needToRefundCredits = true
                withContext(Dispatchers.Main) {
                    callbacks.setGeneratingState(false)
                    // CRITICAL: Ensure streaming mode is stopped on cancellation
                    chatAdapter.stopStreamingModeGradually()
                    messageManager.stopStreamingMode()
                }
            } catch (e: Exception) {
                Timber.tag("ENHANCED-API-ERROR").e(e, "Error in enhanced API call: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Show user-friendly error message based on exception type
                    val errorMessage = when (e) {
                        is java.net.SocketTimeoutException -> "Request timed out. Please try again."
                        is java.net.UnknownHostException -> "No internet connection. Please check your connection."
                        is java.net.ConnectException -> "Connection failed. Please try again later."
                        is java.io.IOException -> "Network error. Please check your connection."
                        else -> "Generation error: Please try again"
                    }
                    callbacks.showError(errorMessage)
                    callbacks.setGeneratingState(false)
                    callbacks.updateTypingIndicator(false)
                    
                    // CRITICAL: Ensure streaming mode is stopped on error
                    chatAdapter.stopStreamingModeGradually()
                    messageManager.stopStreamingMode()
                    stopStreamingMode()
                }
                needToRefundCredits = true
            } finally {
                withContext(Dispatchers.Main) {
                    if (needToRefundCredits) {
                        callbacks.setGeneratingState(false)
                        callbacks.updateTypingIndicator(false)

                        if (!isSubscribed && !isModelFree) {
                            callbacks.incrementFreeMessages()
                            Timber.d("Credit refunded due to API error or cancellation")
                        }
                    }

                    callbacks.scrollToBottom()
                    currentApiJob = null
                    streamingJob = null

                    // CRITICAL: Final check to ensure streaming mode is properly stopped
                    if (chatAdapter.isStreamingActive) {
                        chatAdapter.stopStreamingModeGradually()
                    }
                    messageManager.stopStreamingMode()
                }
            }
        }
    }
    /**
     * Continue with API call using enhanced approach with tool calls support
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun continueWithEnhancedApiCall(
        messagePosition: Int,
        conversationId: String,
        originalMessageText: String?,
        messageId: String,
        modelId: String,
        enableWebSearch: Boolean,
        webSearchParams: Map<String, String> = emptyMap(),
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low"
    ) {
        try {
            // CRITICAL: Ensure streaming mode is active for best real-time performance
            withContext(Dispatchers.Main) {
                if (!chatAdapter.isStreamingActive) {
                    chatAdapter.startStreamingMode()
                }
                if (!messageManager.isStreamingActive) {
                    messageManager.startStreamingMode()
                }
            }

            val allMessages = messageManager.ensureFullSynchronizationForApiWithFallback(conversationId)
            val conversationMessages = allMessages
                .filter { it.conversationId == conversationId }
                .sortedBy { it.timestamp }

            if (conversationMessages.isEmpty()) {
                // ENHANCED RECOVERY: Try to reload from database if no messages found
                try {
                    val dbMessages = runBlocking {
                        conversationsViewModel.getAllConversationMessages(conversationId)
                    }

                    if (dbMessages.isNotEmpty()) {
                        Timber.d("Reloaded ${dbMessages.size} messages from database for conversation $conversationId")
                        messageManager.initialize(dbMessages, conversationId)
                    } else {
                        Timber.e("No messages found for conversation $conversationId and none in database")
                        withContext(Dispatchers.Main) {
                            callbacks.showError("No conversation messages found. Please try again.")
                            callbacks.setGeneratingState(false)
                            chatAdapter.stopStreamingModeGradually()
                            messageManager.stopStreamingMode()
                        }
                        return
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error reloading messages from database: ${e.message}")
                    withContext(Dispatchers.Main) {
                        callbacks.showError("Error loading conversation context. Please try again.")
                        callbacks.setGeneratingState(false)
                        chatAdapter.stopStreamingModeGradually()
                        messageManager.stopStreamingMode()
                    }
                    return
                }
            }

            // Build messages using enhanced approach
            val userMessages = buildMessagesForEnhancedApproach(conversationId, modelId)

            // Skip pre-processing web search - let AI model handle it through streaming tool calls
            // This eliminates delays and reduces token usage
            val enhancedMessages = userMessages
            
            if (enableWebSearch && originalMessageText != null) {
                Timber.d("Web search enabled - AI model will handle tool calls during streaming")
            }

            // Create API request using enhanced method
            val request = ModelApiHandler.createRequest(
                modelId = modelId,
                messages = enhancedMessages,
                isWebSearch = enableWebSearch,
                context = context,
                messageText = originalMessageText,
                isReasoningEnabled = isReasoningEnabled,
                reasoningLevel = reasoningLevel
            )

            // CRITICAL: Ensure stream=true for real-time streaming
            if (request.stream != true) {
                // Force enable streaming for real-time updates
                request.stream = true
                Timber.d("Forcing stream=true for real-time updates")
            }

            // Create request body with enhanced parameters
            val requestBody = ModelApiHandler.createRequestBody(
                apiRequest = request,
                modelId = modelId,
                context = context,
                audioEnabled = false
            )
            Timber.d("Enhanced API request - model: $modelId, context messages: ${conversationMessages.size}, " +
                    "webSearch: $enableWebSearch, streaming: ${request.stream}")

            val apiService = RetrofitInstance.getApiService(
                RetrofitInstance.ApiServiceType.AIMLAPI,
                AimlApiService::class.java
            )

            // Check if this is an OCR model and handle specially
            // CRITICAL FIX: Capture model information at start of request to prevent state issues
            val currentModelInfo = ModelManager.selectedModel
            val currentModelDisplayName = currentModelInfo.displayName
            val isCurrentOcrModel = currentModelInfo.isOcrModel
            
            val response = if (isCurrentOcrModel) {
                Timber.d("🔍 Detected OCR model without files, showing user-friendly message")
                
                // For OCR models without files, show a user-friendly message - use captured model name
                throw Exception("The $currentModelDisplayName is designed for document and image processing. Please attach a file (PDF or image) to use this model. Text-only messages are not supported with OCR models.")
                
            } else {
                // We're now enforcing streaming for real-time updates
                withContext(Dispatchers.IO) {
                    try {
                        // Note: sendRawMessageStreaming returns Response, not Call
                        // So we track the individual calls through the client interceptor
                        apiService.sendRawMessageStreaming(requestBody)
                    } catch (e: Exception) {
                        Timber.e(e, "Enhanced API streaming request failed: ${e.message}")
                        throw e
                    }
                }
            }

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    // CRITICAL: Track the streaming job for cancellation - use background scope to avoid premature cancellation
                    streamingJob = backgroundScope.launch {
                        // Use enhanced streaming processing with tool calls support
                        StreamingHandler.processEnhancedStreamingResponse(
                        responseBody = responseBody,
                        adapter = chatAdapter,
                        messagePosition = messagePosition,
                        isDeepSearchEnabled = enableWebSearch,
                        context = context,
                        apiService = apiService,
                        modelId = modelId,
                        onComplete = {
                            serviceScope.launch(Dispatchers.Main) {
                                callbacks.setGeneratingState(false)
                                callbacks.updateTypingIndicator(false)

                                val completedMessage = chatAdapter.currentList.find { it.id == messageId }
                                if (completedMessage != null) {
                                    updateCompletedMessageInVersionManager(completedMessage)
                                    updateCompletedMessageInGroups(completedMessage)
                                }

                                // CRITICAL: Stop streaming mode when complete
                                chatAdapter.stopStreamingModeGradually()
                                messageManager.stopStreamingMode()
                                
                                // CRITICAL: Reset streaming job reference
                                streamingJob = null
                            }
                        },
                        onError = { errorMessage ->
                            serviceScope.launch(Dispatchers.Main) {
                                callbacks.showError("Generation error: Please try again")
                                callbacks.setGeneratingState(false)
                                callbacks.updateTypingIndicator(false)

                                // CRITICAL: Stop streaming mode on error
                                chatAdapter.stopStreamingModeGradually()
                                messageManager.stopStreamingMode()
                                
                                // CRITICAL: Ensure full UI reset on error
                                stopStreamingMode()
                                
                                // CRITICAL: Reset streaming job reference
                                streamingJob = null
                            }
                        }
                    )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callbacks.showError("Empty response from the enhanced API")
                        callbacks.setGeneratingState(false)

                        // CRITICAL: Stop streaming mode on empty response
                        chatAdapter.stopStreamingModeGradually()
                        messageManager.stopStreamingMode()
                    }
                }
            } else {
                val errorCode = response.code()
                val errorMessage = response.errorBody()?.string() ?: "Unknown error"

                withContext(Dispatchers.Main) {
                    handleApiError(errorCode, errorMessage, modelId, enableWebSearch)
                    callbacks.setGeneratingState(false)

                    // CRITICAL: Stop streaming mode on API error
                    chatAdapter.stopStreamingModeGradually()
                    messageManager.stopStreamingMode()
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in enhanced API call execution: ${e.message}")
            withContext(Dispatchers.Main) {
                callbacks.showError("Enhanced API call error: ${e.message}")
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)

                // CRITICAL: Stop streaming mode on exception
                chatAdapter.stopStreamingModeGradually()
                messageManager.stopStreamingMode()
            }
        }
    }

    // Add this helper method
// In AiChatService.kt - Update performFallbackWebSearch
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun performFallbackWebSearch(
        originalMessageText: String,
        webSearchParams: Map<String, String>,
        messageId: String,
        userMessages: List<AimlApiRequest.Message>
    ): List<AimlApiRequest.Message> {
        try {
            val cleanedQuery = WebSearchService.cleanSearchQuery(originalMessageText)
            val enhancedParams = webSearchParams

            // Perform the actual search using WebSearchService
            val searchResponse = WebSearchService.performSearch(
                query = cleanedQuery,
                freshness = enhancedParams["freshness"],
                count = enhancedParams["result_count"]?.toIntOrNull() ?: 5
            )

            if (searchResponse.success && searchResponse.results.isNotEmpty()) {
                // Format results as system message with enhanced context
                val formattedResults = WebSearchService.formatSearchResultsForAI(
                    searchResponse.results,
                    cleanedQuery
                )

                // Create enhanced search results message
                val searchMessage = AimlApiRequest.Message(
                    role = "system",
                    content = formattedResults
                )

                // Insert after system message
                val enhancedList = userMessages.toMutableList()
                val systemIndex = enhancedList.indexOfFirst { it.role == "system" }
                if (systemIndex != -1) {
                    enhancedList.add(systemIndex + 1, searchMessage)
                } else {
                    enhancedList.add(0, searchMessage)
                }

                Timber.d("Enhanced messages with ${searchResponse.results.size} web search results")

                // Update the message state to show search completed
                withContext(Dispatchers.Main) {
                    messageManager.updateLoadingState(
                        messageId = messageId,
                        isGenerating = true,
                        isWebSearching = false
                    )
                }

                return enhancedList
            } else {
                Timber.w("No web search results found")

                // Update the message state
                withContext(Dispatchers.Main) {
                    messageManager.updateLoadingState(
                        messageId = messageId,
                        isGenerating = true,
                        isWebSearching = false
                    )
                }

                return userMessages
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in fallback web search: ${e.message}")

            // Update the message state
            withContext(Dispatchers.Main) {
                messageManager.updateLoadingState(
                    messageId = messageId,
                    isGenerating = true,
                    isWebSearching = false
                )
            }

            return userMessages
        }
    }

    /**
     * Build messages for enhanced approach with smart context
     */
    private fun buildMessagesForEnhancedApproach(conversationId: String, modelId: String): List<AimlApiRequest.Message> {
        val allMessages = messageManager.ensureFullSynchronizationForApi()
        val messages = allMessages
            .filter { it.conversationId == conversationId }
            .sortedBy { it.timestamp }

        Timber.d("=== BUILDING MESSAGES FOR ENHANCED API APPROACH ===")
        Timber.d("Conversation ID: $conversationId")
        Timber.d("Model ID: $modelId")
        Timber.d("Raw messages for this conversation: ${messages.size}")

        // If no messages found, try to recover from database
        if (messages.isEmpty()) {
            try {
                val dbMessages = runBlocking {
                    conversationsViewModel.getAllConversationMessages(conversationId)
                }

                if (dbMessages.isNotEmpty()) {
                    Timber.d("Reloaded ${dbMessages.size} messages from database for conversation $conversationId")
                    messageManager.initialize(dbMessages, conversationId)

                    // Recursive call with reloaded messages
                    return buildMessagesForEnhancedApproach(conversationId, modelId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reloading messages from database: ${e.message}")
            }
        }

        val formattedMessages = mutableListOf<AimlApiRequest.Message>()
        val isClaudeModel = ModelValidator.isClaudeModel(modelId)

        // FIXED: For Claude models, don't add any system message
        if (!isClaudeModel) {
            // Create enhanced system message for non-Claude models
            val systemPrompt = when {
                ModelValidator.supportsFunctionCalling(modelId) -> {
                    "You are a helpful AI assistant with enhanced capabilities. You have access to web search when needed."
                }
                else -> {
                    "You are a helpful AI assistant."
                }
            }

            val supportsSystemMessages = ModelValidator.supportsSystemMessages(modelId)

            if (supportsSystemMessages) {
                formattedMessages.add(AimlApiRequest.Message("system", systemPrompt))

                for (message in messages) {
                    if (message.isUser) {
                        // CRITICAL FIX: Handle image messages with structured content
                        if (message.isImage) {
                            Timber.d("🖼️ Processing image message: ${message.id}")
                            val structuredContent = createStructuredContentForImageMessage(message)
                            if (structuredContent != null) {
                                formattedMessages.add(AimlApiRequest.Message("user", structuredContent))
                                Timber.d("✅ Added structured image message to API request")
                            } else {
                                // Fallback to text content if structured content creation fails
                                val messageContent = if (!message.prompt.isNullOrBlank()) {
                                    message.prompt
                                } else {
                                    message.message
                                }
                                formattedMessages.add(AimlApiRequest.Message("user", messageContent))
                                Timber.w("⚠️ Fallback to text content for image message")
                            }
                        } else {
                            // Regular text message
                            val messageContent = if (!message.prompt.isNullOrBlank()) {
                                message.prompt // Use extracted content
                            } else {
                                message.message // Use regular message
                            }
                            formattedMessages.add(AimlApiRequest.Message("user", messageContent))
                        }
                    } else if (!message.isGenerating && message.message.isNotBlank()) {
                        formattedMessages.add(AimlApiRequest.Message("assistant", message.message))
                    }
                }
            } else {
                // For models that don't support system messages
                var hasAddedSystemPrompt = false

                for (message in messages) {
                    if (message.isUser) {
                        // CRITICAL FIX: Use prompt field for file messages with extracted content
                        val messageContent = if (!message.prompt.isNullOrBlank()) {
                            message.prompt // Use extracted content
                        } else {
                            message.message // Use regular message
                        }
                        
                        if (!hasAddedSystemPrompt) {
                            formattedMessages.add(AimlApiRequest.Message(
                                "user",
                                "SYSTEM INSTRUCTIONS: $systemPrompt\n\nUSER QUERY: $messageContent"
                            ))
                            hasAddedSystemPrompt = true
                        } else {
                            formattedMessages.add(AimlApiRequest.Message("user", messageContent))
                        }
                    } else if (!message.isGenerating && message.message.isNotBlank()) {
                        formattedMessages.add(AimlApiRequest.Message("assistant", message.message))
                    }
                }

                if (!hasAddedSystemPrompt) {
                    formattedMessages.add(AimlApiRequest.Message("user", "SYSTEM INSTRUCTIONS: $systemPrompt"))
                }
            }
        } else {
            // FIXED: For Claude models, just use the conversation messages as-is
            // Claude has its own built-in instructions and doesn't need custom system messages
            for (message in messages) {
                if (message.isUser) {
                    // CRITICAL FIX: Use prompt field for file messages with extracted content
                    val messageContent = if (!message.prompt.isNullOrBlank()) {
                        message.prompt // Use extracted content
                    } else {
                        message.message // Use regular message
                    }
                    formattedMessages.add(AimlApiRequest.Message("user", messageContent))
                } else if (!message.isGenerating && message.message.isNotBlank()) {
                    formattedMessages.add(AimlApiRequest.Message("assistant", message.message))
                }
            }
        }

        // Ensure we have at least one message
        if (formattedMessages.isEmpty()) {
            Timber.w("No messages found for conversation $conversationId! Adding fallback message.")
            if (isClaudeModel) {
                formattedMessages.add(AimlApiRequest.Message(
                    "user",
                    "Hello! I'm starting a new conversation."
                ))
            } else {
                formattedMessages.add(AimlApiRequest.Message(
                    "system",
                    "You are an AI assistant with enhanced capabilities. The user is starting a new conversation."
                ))
            }
        }

        val userMessages = formattedMessages.count { it.role == "user" }
        val assistantMessages = formattedMessages.count { it.role == "assistant" }
        val systemMessages = formattedMessages.count { it.role == "system" }

        Timber.d("Final enhanced API payload: ${formattedMessages.size} total messages")
        Timber.d("  - User messages: $userMessages")
        Timber.d("  - Assistant messages: $assistantMessages")
        Timber.d("  - System messages: $systemMessages")
        Timber.d("  - Is Claude model: $isClaudeModel")

        Timber.d("=== END BUILDING ENHANCED MESSAGES ===")

        return formattedMessages
    }

    // Keep original startApiCall for backward compatibility
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun startApiCall(
        conversationId: String,
        messageId: String,
        isSubscribed: Boolean,
        isModelFree: Boolean,
        enableWebSearch: Boolean = false,
        originalMessageText: String? = null,
    ): Job {
        messageManager.startStreamingMode()

        // Get current reasoning settings from PrefsManager
        val isReasoningEnabled = PrefsManager.getAiSettings(context).isReasoningEnabled
        val reasoningLevel = PrefsManager.getAiSettings(context).reasoningLevel

        return startEnhancedApiCall(
            conversationId = conversationId,
            messageId = messageId,
            isSubscribed = isSubscribed,
            isModelFree = isModelFree,
            enableWebSearch = enableWebSearch,
            originalMessageText = originalMessageText,
            webSearchParams = emptyMap(),
            isReasoningEnabled = isReasoningEnabled,
            reasoningLevel = reasoningLevel
        )
    }

    /**
     * Make audio request with proper error handling
     */
    suspend fun makeAudioRequest(
        requestBody: RequestBody
    ): AimlApiResponse? {
        return try {
            // Get the appropriate API service
            val apiService = RetrofitInstance.getApiService(
                RetrofitInstance.ApiServiceType.AIMLAPI,
                AimlApiService::class.java
            )

            // AIML API doesn't support /audio/speech endpoint, use chat completions with audio modality
            val call = withContext(Dispatchers.IO) {
                apiService.sendRawMessage(requestBody)
            }
            
            // Note: This call will be tracked via OkHttpClient interceptor for cancellation

            val response = withContext(Dispatchers.IO) {
                call.execute()
            }

            if (response.isSuccessful) {
                val responseBody = response.body()
                val contentType = response.headers()["Content-Type"] ?: ""
                
                Timber.d("Audio API response received, content-type: $contentType")

                when {
                    // If response is binary audio data (mp3, wav, etc.)
                    contentType.startsWith("audio/") -> {
                        val audioBytes = responseBody?.bytes() ?: byteArrayOf()
                        Timber.d("Received binary audio data: ${audioBytes.size} bytes")
                        
                        if (audioBytes.isNotEmpty()) {
                            // Convert binary audio to base64 for processing
                            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.DEFAULT)
                            createAudioResponseWrapper(base64Audio, "mp3") // Assume mp3 format
                        } else {
                            Timber.e("Empty binary audio response received")
                            null
                        }
                    }
                    
                    // If response is JSON (some APIs return JSON with base64 audio)
                    contentType.contains("json") -> {
                        val responseString = responseBody?.string() ?: ""
                        Timber.d("Audio API JSON response: ${responseString.take(100)}...")
                        
                        try {
                            JsonUtils.fromJson(responseString, AimlApiResponse::class.java)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to parse JSON response: $responseString")
                            null
                        }
                    }

                    else -> {
                        // Fallback: try to read as string (for base64 responses)
                        val responseString = responseBody?.string() ?: ""
                        Timber.d("Audio API fallback response: ${responseString.take(100)}...")
                        
                        if (responseString.trim().isNotEmpty()) {
                            createAudioResponseWrapper(responseString)
                        } else {
                            Timber.e("Empty audio response received")
                            null
                        }
                    }
                }
            } else {
                val errorCode = response.code()
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Timber.e("Audio API request failed: $errorCode - $errorBody")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in audio API request: ${e.message}")
            null
        }
    }

    /**
     * Create a wrapper response for audio data
     */
    private fun createAudioResponseWrapper(audioData: String, format: String = "mp3"): AimlApiResponse {
        return AimlApiResponse(
            choices = listOf(
                AimlApiResponse.Choice(
                    message = AimlApiResponse.Message(
                        role = "assistant",
                        content = "",
                        audioData = AimlApiResponse.AudioData(
                            format = format,
                            content = audioData
                        )
                    ),
                    finishReason = "stop"
                )
            ),
            usage = AimlApiResponse.Usage(0, 0, 0)
        )
    }

    fun cancelCurrentGeneration() {
        Timber.d("🛑🛑🛑 CANCELLING current generation from AiChatService")
        Timber.d("🛑 Current API Job active: ${currentApiJob?.isActive}")
        Timber.d("🛑 Current Streaming Job active: ${streamingJob?.isActive}")
        Timber.d("🛑 Service thread: ${Thread.currentThread().name}")

        // CRITICAL FIX: Cancel streaming handler IMMEDIATELY for instant response
        try {
            StreamingHandler.cancelAllStreaming()
            Timber.d("🛑 ✅ Streaming handler cancelled immediately")
        } catch (e: Exception) {
            Timber.e(e, "🛑 ❌ Error cancelling streaming handler: ${e.message}")
        }

        // CRITICAL: Cancel HTTP requests FIRST to stop network activity
        try {
            RetrofitInstance.cancelAllRequests()
            Timber.d("🛑 ✅ HTTP requests cancelled from AiChatService")
        } catch (e: Exception) {
            Timber.e(e, "🛑 ❌ Error cancelling HTTP requests from AiChatService: ${e.message}")
        }

        // CRITICAL: Set generating state to false FIRST to stop monitoring
        callbacks.setGeneratingState(false)
        callbacks.updateTypingIndicator(false)

        currentApiJob?.cancel()
        Timber.d("🛑 API Job cancelled")
        currentApiJob = null
        
        // CRITICAL: Cancel streaming job separately
        streamingJob?.cancel()
        Timber.d("🛑 Streaming Job cancelled")
        streamingJob = null

        // CRITICAL: Ensure streaming mode is stopped when cancelling
        stopStreamingMode()
        
        // CRITICAL: Force stop any adapter or manager streaming
        chatAdapter.stopStreamingModeGradually()
        messageManager.stopStreamingMode()
        
        // CRITICAL: Stop any background generation service
        try {
            // Stop the background service entirely
            val serviceIntent = Intent(context, BackgroundAiService::class.java)
            context.stopService(serviceIntent)
            Timber.d("🛑 Background service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping background service: ${e.message}")
        }
        
        Timber.d("🛑 All generation jobs cancelled successfully")
    }
    
    /**
     * Transfer ongoing generation to background service when user exits
     */
    fun transferToBackgroundService(messageId: String, conversationId: String) {
        if (currentApiJob?.isActive == true && callbacks.isGenerating()) {
            Timber.d("🔄 Transferring generation to background service for message: $messageId")
            
            // Cancel current foreground job
            currentApiJob?.cancel()
            currentApiJob = null
            
            // Cancel streaming job
            streamingJob?.cancel()
            streamingJob = null
            
            // Start background service to continue generation
            BackgroundAiService.startGeneration(context, messageId, conversationId)
            
            Timber.d("✅ Successfully transferred generation to background service")
        }
    }
    
    /**
     * Check if we have any generating messages that need background transfer
     */
    fun getGeneratingMessages(): List<ChatMessage> {
        return callbacks.getCurrentMessages().filter { !it.isUser && it.isGenerating }
    }

    private fun updateCompletedMessageInVersionManager(message: ChatMessage) {
        callbacks.onMessageCompleted(message.id, message.message)
    }

    private fun updateCompletedMessageInGroups(message: ChatMessage) {
        val groupId = message.aiGroupId ?: return
        val groups = callbacks.getAiResponseGroups()
        val group = groups[groupId] ?: return

        val index = group.indexOfFirst { it.id == message.id }
        if (index != -1) {
            val updatedMessage = message.copy(
                isGenerating = false,
                isLoading = false,
                showButtons = true
            )

            group[index] = updatedMessage
            callbacks.saveMessage(updatedMessage)

            Timber.d("Updated message in groups: id=${message.id}, groupId=$groupId, contentLength=${message.message.length}")
        }
    }

    private fun verifyMessageContextEnhanced(conversationId: String) {
        val messages = messageManager.getCurrentMessages()
            .filter { it.conversationId == conversationId }
            .sortedBy { it.timestamp }

        Timber.d("=== ENHANCED CONTEXT VERIFICATION FOR CONVERSATION $conversationId ===")
        Timber.d("Total context messages: ${messages.size}")

        if (messages.isEmpty()) {
            Timber.e("❌ NO MESSAGES FOUND FOR CONVERSATION $conversationId!")
            return
        }

        val userMessages = messages.filter { it.isUser }
        val aiMessages = messages.filter { !it.isUser && !it.isGenerating && it.message.isNotBlank() }
        val generatingMessages = messages.filter { it.isGenerating }

        Timber.d("Message breakdown:")
        Timber.d("  - User messages: ${userMessages.size}")
        Timber.d("  - Completed AI messages: ${aiMessages.size}")
        Timber.d("  - Generating AI messages: ${generatingMessages.size}")

        if (messages.size <= 6) {
            messages.forEachIndexed { index, msg ->
                val role = if (msg.isUser) "USER" else "AI"
                val status = if (msg.isGenerating) " (generating)" else ""
                val preview = msg.message.take(50).replace("\n", " ")
                Timber.d("  $index. [$role$status] $preview...")
            }
        } else {
            messages.take(3).forEachIndexed { index, msg ->
                val role = if (msg.isUser) "USER" else "AI"
                val preview = msg.message.take(50).replace("\n", " ")
                Timber.d("  $index. [$role] $preview...")
            }
            Timber.d("  ... ${messages.size - 6} more messages ...")
            messages.takeLast(3).forEachIndexed { index, msg ->
                val actualIndex = messages.size - 3 + index
                val role = if (msg.isUser) "USER" else "AI"
                val preview = msg.message.take(50).replace("\n", " ")
                Timber.d("  $actualIndex. [$role] $preview...")
            }
        }

        if (userMessages.isEmpty()) {
            Timber.w("⚠️ WARNING: No user messages found in conversation!")
        }

        if (messages.size < 2) {
            Timber.w("⚠️ WARNING: Very few messages in context (${messages.size})")
        }

        Timber.d("=== END ENHANCED CONTEXT VERIFICATION ===")
    }

    private fun validateFiles(fileUris: List<Uri>, message: String): Boolean {
        if (fileUris.isEmpty()) return true

        val currentModel = ModelManager.selectedModel

        if (fileUris.size > ModelValidator.getMaxFilesForModel(currentModel.id)) {
            callbacks.showError("Maximum ${ModelValidator.getMaxFilesForModel(currentModel.id)} files allowed for ${currentModel.displayName}.")
            return false
        }

        val hasImages = fileUris.any { uri ->
            val mimeType = context.contentResolver.getType(uri) ?: ""
            mimeType.startsWith("image/")
        }

        if (hasImages && !ModelValidator.supportsImageUpload(currentModel.id)) {
            callbacks.showError("The selected AI model (${currentModel.displayName}) doesn't support image uploads.")
            return false
        }

        if (message.isBlank() && ModelValidator.requiresTextWithImages(currentModel.id) && hasImages) {
            callbacks.showError("Please add descriptive text when sending images to ${currentModel.displayName}.")
            return false
        }

        val maxFileSizeBytes = ModelValidator.getMaxFileSizeBytes(currentModel.id)
        Timber.d("Checking file sizes against max: ${FileUtil.formatFileSize(maxFileSizeBytes)} for model: ${currentModel.id}")
        for (uri in fileUris) {
            val fileSize = FileUtil.getFileSizeFromUri(context, uri)
            val fileName = FileUtil.getFileNameFromUri(context, uri) ?: "file"
            
            Timber.d("Validating file: $fileName, size: ${FileUtil.formatFileSize(fileSize)}, max allowed: ${FileUtil.formatFileSize(maxFileSizeBytes)}")
            
            // Additional safety check: ensure maxFileSizeBytes is reasonable (not 0 or negative)
            if (maxFileSizeBytes <= 0) {
                Timber.w("Invalid max file size ($maxFileSizeBytes), using 100MB fallback")
                val fallbackMaxSize = 100L * 1024 * 1024 // 100MB
                if (fileSize > fallbackMaxSize) {
                    val formattedMaxSize = FileUtil.formatFileSize(fallbackMaxSize)
                    val formattedActualSize = FileUtil.formatFileSize(fileSize)
                    callbacks.showError("File '$fileName' exceeds the maximum allowed size ($formattedMaxSize). Current size: $formattedActualSize")
                    return false
                }
            } else if (fileSize > maxFileSizeBytes) {
                val formattedMaxSize = FileUtil.formatFileSize(maxFileSizeBytes)
                val formattedActualSize = FileUtil.formatFileSize(fileSize)
                callbacks.showError("File '$fileName' exceeds the maximum allowed size ($formattedMaxSize). Current size: $formattedActualSize")
                return false
            }
        }

        return true
    }

    private fun buildAiModelPrompt(
        userText: String,
        processedImages: List<ProcessedImageInfo>,
        extractedDocuments: List<ExtractedDocumentInfo> = emptyList()
    ): String {
        val prompt = StringBuilder()

        if (userText.isNotBlank()) {
            prompt.append(userText).append("\n\n")
        }

        // Concise image attachment notice (reduced from ~50 tokens to ~10 tokens)
        if (processedImages.isNotEmpty()) {
            prompt.append("📎 ${processedImages.size} image(s) attached\n\n")
        }

        // Concise document content inclusion (reduced from ~200+ tokens to ~20 tokens)
        if (extractedDocuments.isNotEmpty()) {
            Timber.d("Adding ${extractedDocuments.size} extracted documents to prompt")
            prompt.append("📄 ${extractedDocuments.size} document(s):\n")
            
            extractedDocuments.forEachIndexed { index, doc ->
                if (doc.extractedContent.isNotEmpty()) {
                    val contentTokens = TokenValidator.estimateTokenCount(doc.extractedContent)
                    prompt.append("\n${doc.name}:\n")
                    prompt.append(doc.extractedContent)
                    prompt.append("\n")
                    Timber.d("Added ${doc.extractedContent.length} chars ($contentTokens tokens) of extracted content for ${doc.name}")
                } else {
                    Timber.w("Document ${doc.name} has empty extracted content!")
                    prompt.append("⚠️ ${doc.name}: extraction failed\n")
                }
            }
        } else {
            Timber.d("No extracted documents to add to prompt")
        }

        return prompt.toString()
    }

    private fun validateTokenLimit(message: String, modelId: String, isSubscribed: Boolean): Boolean {
        try {
            // Use TokenValidator for simple token limit validation
            val messageTokens = TokenValidator.getAccurateTokenCount(message, modelId)
            val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
            
            if (messageTokens > maxTokens) {
                val errorMessage = if (isSubscribed) {
                    "Your message ($messageTokens tokens) exceeds the model's token limit ($maxTokens tokens). Please shorten your message."
                } else {
                    "Your message exceeds the free tier token limit. Upgrade to Pro for higher limits, or shorten your message."
                }

                callbacks.showError(errorMessage)
                return false
            }

            return true
        } catch (e: Exception) {
            Timber.e(e, "Error validating token limit")
            return true // Allow the message to proceed if validation fails
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun generateResponseForEditedMessage(
        conversationId: String,
        message: String,
        forceWebSearch: Boolean = false,
        userMessageId: String
    ) {
        val aiMessageId = UUID.randomUUID().toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            conversationId = conversationId,
            message = "",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isGenerating = true,
            showButtons = false,
            modelId = ModelManager.selectedModel.id,
            aiModel = ModelManager.selectedModel.displayName,
            parentMessageId = userMessageId
        )

        Timber.d("Adding AI message ${aiMessage.id} for edited user message $userMessageId")
        messageManager.addMessage(aiMessage)

        callbacks.setGeneratingState(true)
        callbacks.updateTypingIndicator(true)

        // Use enhanced API call for edited messages
        currentApiJob = startEnhancedApiCall(
            conversationId = conversationId,
            messageId = aiMessageId,
            isSubscribed = callbacks.isSubscribed(),
            isModelFree = ModelManager.selectedModel.isFree,
            enableWebSearch = forceWebSearch,
            originalMessageText = message,
            webSearchParams = emptyMap(),
            isReasoningEnabled = false, // Edit message function uses defaults
            reasoningLevel = "low"
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun sendTranslationMessage(
        userText: String,
        sourceLanguage: String,
        targetLanguage: String,
        translationContext: String,
        userMessageId: String,
        conversationId: String
    ) {
        serviceScope.launch {
            try {
                val aiMessageId = UUID.randomUUID().toString()
                val groupId = UUID.randomUUID().toString()

                val aiMessage = ChatMessage(
                    id = aiMessageId,
                    conversationId = conversationId,
                    message = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isGenerating = true,
                    showButtons = false,
                    modelId = ModelManager.selectedModel.id,
                    aiModel = ModelManager.selectedModel.displayName,
                    aiGroupId = groupId,
                    parentMessageId = userMessageId
                )

                callbacks.updateMessageInAdapter(aiMessage)
                callbacks.saveMessage(aiMessage)

                if (!callbacks.isSubscribed() && !ModelManager.selectedModel.isFree) {
                    callbacks.decrementFreeMessages()
                }

                currentApiJob = startTranslationApiCall(
                    messageId = aiMessageId,
                    translationContext = translationContext,
                    userText = userText,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    isSubscribed = callbacks.isSubscribed(),
                    isModelFree = ModelManager.selectedModel.isFree
                )

            } catch (e: Exception) {
                Timber.e(e, "Error in sendTranslationMessage: ${e.message}")
                callbacks.showError("Translation error: ${e.message}")
                callbacks.setGeneratingState(false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startTranslationApiCall(
        messageId: String,
        translationContext: String,
        userText: String,
        sourceLanguage: String,
        targetLanguage: String,
        isSubscribed: Boolean,
        isModelFree: Boolean
    ): Job {
        return serviceScope.launch {
            try {
                Timber.d("Starting translation API call for: $sourceLanguage -> $targetLanguage")

                val messages = mutableListOf<AimlApiRequest.Message>()

                val modelId = ModelManager.selectedModel.id
                val supportsSystemMessages = ModelValidator.supportsSystemMessages(modelId)

                // Handle system messages based on model capability
                if (supportsSystemMessages) {
                    messages.add(AimlApiRequest.Message(
                        role = "system",
                        content = translationContext
                    ))

                    messages.add(AimlApiRequest.Message(
                        role = "user",
                        content = userText
                    ))
                } else {
                    messages.add(AimlApiRequest.Message(
                        role = "user",
                        content = "$translationContext\n\nText to translate: $userText"
                    ))
                }

                val config = ModelConfigManager.getConfig(modelId)
                    ?: throw IllegalArgumentException("Unknown model: $modelId")

                // Define useWebSearch variable - set to false for translation
                val useWebSearch = false

                val request = AimlApiRequest(
                    model = modelId,
                    messages = messages,
                    temperature = 0.3,
                    stream = true,
                    topA = if (config.supportsTopA) null else null,
                    parallelToolCalls = if (config.supportsParallelToolCalls && useWebSearch) true else null,
                    minP = if (config.supportsMinP) null else null,
                    useWebSearch = false,
                )

                val apiService = RetrofitInstance.getApiService(
                    RetrofitInstance.ApiServiceType.AIMLAPI,
                    AimlApiService::class.java
                )

                val requestBody = ModelApiHandler.createRequestBody(
                    apiRequest = request,
                    modelId = modelId,
                    context = context,
                    audioEnabled = false
                )

                val response = withContext(Dispatchers.IO) {
                    // Note: sendRawMessageStreaming returns Response, not Call
                    // So we track the individual calls through the client interceptor
                    apiService.sendRawMessageStreaming(requestBody)
                }

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val messagePosition = chatAdapter.currentList.indexOfFirst { it.id == messageId }

                        if (messagePosition != -1) {
                            handleStreamingResponse(
                                responseBody = responseBody,
                                messagePosition = messagePosition,
                                messageId = messageId
                            )
                        } else {
                            throw Exception("Translation message not found in adapter")
                        }
                    } else {
                        throw Exception("Empty response from translation API")
                    }
                } else {
                    val errorCode = response.code()
                    val errorMessage = response.errorBody()?.string() ?: "Unknown translation error"
                    throw Exception("Translation API error ($errorCode): $errorMessage")
                }

            } catch (e: Exception) {
                Timber.e(e, "Error in translation API call: ${e.message}")

                withContext(Dispatchers.Main) {
                    val currentList = chatAdapter.currentList.toMutableList()
                    val index = currentList.indexOfFirst { it.id == messageId }

                    if (index != -1) {
                        val errorMessage = currentList[index].copy(
                            message = "Translation failed: ${e.message}\n\nPlease try again or check your internet connection.",
                            isGenerating = false,
                            showButtons = true,
                            error = true
                        )

                        currentList[index] = errorMessage
                        chatAdapter.submitList(currentList)
                        callbacks.saveMessage(errorMessage)
                    }

                    callbacks.setGeneratingState(false)
                    callbacks.updateTypingIndicator(false)

                    if (!isSubscribed && !isModelFree) {
                        callbacks.incrementFreeMessages()
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun handleStreamingResponse(
        responseBody: ResponseBody,
        messagePosition: Int,
        messageId: String
    ) {
        // CRITICAL: Ensure streaming mode is active before processing begins
        startStreamingMode()

        streamingJob = coroutineScope.launch {
            try {
                StreamingHandler.processStreamingResponse(
                    responseBody = responseBody,
                    adapter = chatAdapter,
                    messagePosition = messagePosition,
                    isWebSearchEnabled = callbacks.getWebSearchEnabled(),
                    onComplete = {
                        coroutineScope.launch(Dispatchers.Main) {
                            val completedMessage = chatAdapter.currentList.find { it.id == messageId }

                            if (completedMessage != null) {
                                updateCompletedMessageInVersionManager(completedMessage)
                                updateCompletedMessageInGroups(completedMessage)
                            }

                            callbacks.setGeneratingState(false)

                            // CRITICAL: Stop streaming mode when complete
                            stopStreamingMode()
                            
                            // CRITICAL: Reset streaming job reference
                            streamingJob = null
                        }
                    },
                    onError = { errorMessage ->
                        coroutineScope.launch(Dispatchers.Main) {
                            callbacks.showError("Generation error: Please try again")
                            callbacks.setGeneratingState(false)
                            callbacks.updateTypingIndicator(false)

                            // CRITICAL: Stop streaming mode on error
                            stopStreamingMode()
                            
                            // CRITICAL: Reset streaming job reference
                            streamingJob = null
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in streaming response: ${e.message}")
                coroutineScope.launch(Dispatchers.Main) {
                    callbacks.showError("Generation error: Please try again")
                    callbacks.setGeneratingState(false)
                    callbacks.updateTypingIndicator(false)

                    // CRITICAL: Stop streaming mode on exception
                    stopStreamingMode()
                    
                    // CRITICAL: Reset streaming job reference
                    streamingJob = null
                }
            }
        }
    }

    fun startStreamingMode() {
        if (isStreamingActive) return

        isStreamingActive = true
        serviceScope.launch(Dispatchers.Main) {
            // Start streaming mode in ChatAdapter
            chatAdapter.startStreamingMode()

            // Start streaming mode in MessageManager
            messageManager.startStreamingMode()

            // Update UI state
            callbacks.setGeneratingState(true)
            callbacks.updateTypingIndicator(true)

            Timber.d("🚀 Started streaming mode across all components")
        }
    }

    /**
     * Stop streaming mode across all components
     */
    fun stopStreamingMode() {
        if (!isStreamingActive) return

        isStreamingActive = false
        serviceScope.launch(Dispatchers.Main) {
            // Stop streaming mode in ChatAdapter
            chatAdapter.stopStreamingModeGradually()

            // Stop streaming mode in MessageManager
            messageManager.stopStreamingMode()

            // Update UI state if we're actually done generating
            if (!chatAdapter.isGenerating) {
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)
            }

            Timber.d("✅ Stopped streaming mode across all components")
        }
    }



    /**
     * Extract specific validation errors from error response
     */
    private fun extractValidationError(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)

            if (json.has("meta") && json.get("meta") is JSONArray) {
                val metaArray = json.getJSONArray("meta")
                if (metaArray.length() > 0) {
                    val firstError = metaArray.getJSONObject(0)
                    return firstError.optString("message", "Invalid format")
                }
            } else if (json.has("meta") && json.get("meta") is String) {
                return json.getString("meta")
            }

            json.optString("message", "Invalid request format")
        } catch (e: Exception) {
            Timber.e(e, "Error extracting validation error: ${e.message}")
            "Invalid request format"
        }
    }
    private fun handleApiError(errorCode: Int, errorBody: String, modelId: String? = null, isEnhanced: Boolean = false) {
        Timber.tag("API-ERROR").e("${if (isEnhanced) "Enhanced " else ""}API Error: $errorCode - $errorBody")

        try {
            var shouldAutoFixWebSearch = false
            var shouldAutoFixMessageFormat = false
            var shouldSuggestModelChange = false
            var suggestedModel: String? = null
            var errorMessage: String

            val parsedError = try {
                JSONObject(errorBody)
            } catch (_: Exception) {
                null
            }

            val innerErrorMessage = parsedError?.let {
                try {
                    val messageStr = it.optString("message", "")
                    if (messageStr.startsWith("{")) {
                        val innerJson = JSONObject(messageStr)
                        innerJson.optString("message", messageStr)
                    } else {
                        messageStr
                    }
                } catch (_: Exception) {
                    it.optString("message", "")
                }
            } ?: errorBody

            when {
                errorBody.contains("invalid_enum_value") &&
                        (errorBody.contains("Expected 'user' | 'assistant'") ||
                                errorBody.contains("system")) -> {
                    errorMessage = "This model doesn't support system messages. Automatically fixing message format."
                    shouldAutoFixMessageFormat = true
                }

                errorBody.contains("web_search") ||
                        errorBody.contains("Web search options") ||
                        innerErrorMessage.contains("web_search") ||
                        errorBody.contains("tool_calls") ||
                        errorBody.contains("function_call") ||
                        errorBody.contains("tools") ||
                        errorBody.contains("function calling") ||
                        errorBody.contains("tool use") ||
                        errorBody.contains("No endpoints") ||
                        errorBody.contains("invalid tools") ||
                        innerErrorMessage.contains("invalid tools") -> {
                    errorMessage = if (isEnhanced) {
                        "Web search temporarily disabled - this model doesn't support function calling. Please try again."
                    } else {
                        "Web search is not supported with this model. Disabling web search."
                    }
                    shouldAutoFixWebSearch = true
                }

                errorBody.contains("invalid base64") ||
                        errorBody.contains("Invalid url") ||
                        errorBody.contains("image_url") ||
                        errorBody.contains("validation") -> {
                    val validationError = extractValidationError(errorBody)
                    errorMessage = "File encoding error: $validationError. Please try re-uploading the file."
                }

                errorBody.contains("does not support files") -> {
                    errorMessage = "This model doesn't support file uploads. Please try a text-only request."
                }

                errorCode == 500 && (
                        errorBody.contains("Internal server error") ||
                                modelId?.contains("mistral", ignoreCase = true) == true
                        ) -> {
                    errorMessage = "The AI service is experiencing issues. Switching to a more reliable model."
                    shouldSuggestModelChange = true
                    suggestedModel = "claude-3-sonnet-20240229"
                }

                errorCode == 400 -> errorMessage = "Bad request (400): The request format was incorrect."
                errorCode == 401 -> errorMessage = "Authentication error (401): Invalid API key or permissions."
                errorCode == 403 -> errorMessage = "Forbidden (403): You don't have permission for this operation."
                errorCode == 404 -> errorMessage = "Not found (404): The requested resource wasn't found."
                errorCode == 422 -> errorMessage = "Validation error (422): The request format doesn't match what the API requires."
                errorCode == 429 -> errorMessage = "Rate limit exceeded (429): Too many requests. Please try again later."
                errorCode in 500..599 -> errorMessage = "Server error ($errorCode): The AI service is experiencing issues."

                else -> errorMessage = "API error ($errorCode): ${errorBody.take(100)}"
            }

            callbacks.showError(errorMessage)
            handleErrorMessageUpdate(errorMessage)

            if (shouldAutoFixWebSearch) {
                callbacks.setWebSearchEnabled(false)
                callbacks.updateDeepSearchSetting(false)
                Timber.d("Auto-disabled web search due to API error")
            }

            if (shouldAutoFixMessageFormat && modelId != null) {
                updateModelCapabilities(modelId)
            }

            if (shouldSuggestModelChange && suggestedModel != null) {
                val currentModel = ModelManager.selectedModel.id
                if (currentModel != suggestedModel) {
                    callbacks.suggestAlternativeModel(
                        "The current model ($currentModel) is experiencing issues. Would you like to switch to ${suggestedModel}?"
                    )
                }
            }

            callbacks.setGeneratingState(false)

        } catch (e: Exception) {
            Timber.e(e, "Error handling API error: ${e.message}")
            callbacks.showError("API error ($errorCode): Unknown error")
            callbacks.setGeneratingState(false)
        }
    }

    private fun handleErrorMessageUpdate(errorMessage: String) {
        val currentMessages = callbacks.getCurrentMessages()
        val generatingMessage = currentMessages.findLast { !it.isUser && it.isGenerating }

        if (generatingMessage != null) {
            val updatedMessage = generatingMessage.copy(
                message = "Error: $errorMessage\n\nPlease try again or select a different model if this error persists.",
                isGenerating = false,
                isLoading = false,
                showButtons = true,
                error = true
            )

            callbacks.forceUpdateMessageInAdapter(updatedMessage)
            callbacks.updateMessageInGroups(updatedMessage)
            callbacks.saveMessage(updatedMessage)
        }
    }

    private fun updateModelCapabilities(modelId: String) {
        serviceScope.launch {
            try {
                val config = ModelConfigManager.getConfig(modelId)
                if (config != null) {
                    // Create an updated config
                    val updatedConfig = config.copy(
                        supportsSystemMessages = false
                    )
                    // Register the updated config
                    ModelConfigManager.register(updatedConfig)
                    Timber.d("Updated capabilities for model: $modelId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating model capabilities")
            }
        }
    }

    /**
     * Mark files with error state for UI indication
     */
    private fun markFilesWithError(fileUris: List<Uri>) {
        // Delegate to callback to handle UI updates
        callbacks.markFilesWithError(fileUris)
    }

    /**
     * Process OCR request using OCR endpoint with multipart file upload
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processOcrRequest(
        messagePosition: Int,
        originalMessageText: String?,
        messageId: String,
        fileUris: List<Uri>
    ) {
        try {
            Timber.d("🔍 Processing OCR request with ${fileUris.size} files")

            RetrofitInstance.getApiService(
                RetrofitInstance.ApiServiceType.AIMLAPI,
                AimlApiService::class.java
            )

            // Process each file for OCR
            for (fileUri in fileUris) {
                // CRITICAL: Check if generation was cancelled
                if (!callbacks.isGenerating()) {
                    Timber.d("🛑 OCR processing cancelled by user")
                    return
                }
                try {
                    val mimeType = context.contentResolver.getType(fileUri) ?: ""
                    val fileName = getFileName(fileUri)
                    
                    Timber.d("🔍 Processing OCR file: $fileName, MIME: $mimeType")
                    
                    // Create multipart file for OCR
                    val fileInputStream = context.contentResolver.openInputStream(fileUri)
                    if (fileInputStream == null) {
                        Timber.e("Cannot read file: $fileName")
                        continue
                    }
                    
                    val fileBytes = fileInputStream.use { it.readBytes() }
                    val requestFile = RequestBody.create(
                        mimeType.toMediaTypeOrNull(),
                        fileBytes
                    )
                    
                    val filePart = okhttp3.MultipartBody.Part.createFormData(
                        "document", 
                        fileName, 
                        requestFile
                    )
                    
                    // Add prompt if provided
                    val promptPart = originalMessageText?.let { text ->
                        text.toRequestBody("text/plain".toMediaTypeOrNull())
                    }
                    
                    Timber.d("🔍 Calling OCR API for file: $fileName")
                    
                    // CRITICAL: Final check before API call
                    if (!callbacks.isGenerating()) {
                        Timber.d("🛑 OCR API call cancelled by user")
                        return
                    }
                    
                    // Call OCR API with extended timeout service
                    val ocrApiService = getOcrApiService()
                    val call = ocrApiService.performOCR(
                        document = filePart,
                        prompt = promptPart
                    )
                    
                    val response = withContext(Dispatchers.IO) {
                        executeWithRetry(call, fileName)
                    }
                    
                    // CRITICAL: Check cancellation after API response
                    if (!callbacks.isGenerating()) {
                        Timber.d("🛑 OCR processing cancelled after API response")
                        return
                    }
                    
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody != null) {
                            // Extract OCR result from response
                            val ocrResult = responseBody.choices?.firstOrNull()?.message?.content
                                ?: responseBody.content
                                ?: "OCR processing completed but no content returned"
                            
                            Timber.d("🔍 OCR successful for $fileName: ${ocrResult.length} chars")
                            
                            // Update the AI message with OCR result
                            withContext(Dispatchers.Main) {
                                val messages = chatAdapter.currentList.toMutableList()
                                val messageToUpdate = messages.find { it.id == messageId }
                                
                                if (messageToUpdate != null) {
                                    // Update message properties
                                    messageToUpdate.message = ocrResult
                                    messageToUpdate.isGenerating = false
                                    messageToUpdate.isOcrDocument = true
                                    messageToUpdate.processCompleted = true
                                    
                                    // Update through message manager
                                    messageManager.updateMessageContent(messageId, ocrResult)
                                    
                                    // Update adapter
                                    chatAdapter.notifyItemChanged(messagePosition)
                                    
                                    callbacks.setGeneratingState(false)
                                    callbacks.updateTypingIndicator(false)
                                    callbacks.scrollToBottom()
                                }
                            }
                            return // Successfully processed
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Timber.e("🔍 OCR API error for $fileName: ${response.code()} - $errorBody")
                        
                        // Handle specific HTTP error codes with user-friendly messages
                        val errorMessage = when (response.code()) {
                            502 -> "OCR service is temporarily unavailable (502 Bad Gateway). Please try again in a few moments."
                            503 -> "OCR service is temporarily overloaded (Service Unavailable). Please try again later."
                            500 -> "OCR service encountered an internal error. Please try again or contact support."
                            429 -> "Too many requests. Please wait before trying again."
                            413 -> "File is too large for OCR processing. Please try with a smaller file."
                            415 -> "Unsupported file format. Please try with a different image format (PNG, JPG, PDF)."
                            401, 403 -> "Authentication error with OCR service. Please check your API configuration."
                            404 -> "OCR service endpoint not found. Please contact support."
                            408 -> "Request timeout. The file may be too large or connection is slow."
                            else -> "OCR service error (${response.code()}). Please try again later."
                        }
                        
                        withContext(Dispatchers.Main) {
                            callbacks.showError(errorMessage)
                            callbacks.setGeneratingState(false)
                            callbacks.updateTypingIndicator(false)
                        }
                        continue // Try next file if available
                    }
                } catch (e: Exception) {
                    val fileName = getFileName(fileUri)
                    Timber.e(e, "🔍 Error processing OCR for file: $fileName")
                    
                    // Provide specific error messages based on exception type
                    when (e) {
                        is java.net.ProtocolException -> {
                            if (e.message?.contains("unexpected end of stream") == true) {
                                "File upload interrupted. This may be due to large file size or network issues. Please try again with a smaller file or check your connection."
                            } else {
                                "Network protocol error: ${e.message}"
                            }
                        }
                        is java.net.SocketTimeoutException -> "Request timed out. The file may be too large or the server is busy. Please try again."
                        is java.net.UnknownHostException -> "Unable to connect to OCR service. Please check your internet connection."
                        is java.net.ConnectException -> "Failed to connect to OCR service. Please try again later."
                        is java.io.IOException -> "File upload error: ${e.message}"
                        else -> "OCR processing failed: ${e.localizedMessage ?: e.message}"
                    }
                    
                    withContext(Dispatchers.Main) {
                        callbacks.showError("Processing error: Please try again")
                        callbacks.setGeneratingState(false)
                        callbacks.updateTypingIndicator(false)
                        
                        // CRITICAL: Ensure full UI reset on OCR error
                        stopStreamingMode()
                    }
                    continue // Try next file if available
                }
            }
            
            // If we get here, all files failed
            withContext(Dispatchers.Main) {
                callbacks.showError("Processing error: Please try again")
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)
                
                // CRITICAL: Ensure full UI reset when all files fail
                stopStreamingMode()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "🔍 Fatal error in OCR processing: ${e.message}")
            withContext(Dispatchers.Main) {
                callbacks.showError("Processing error: Please try again")
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)
                
                // CRITICAL: Ensure full UI reset on fatal OCR error
                stopStreamingMode()
            }
        }
    }

    // Utility data classes and methods
    data class ProcessedImageInfo(
        val uri: String,
        val description: String,
        val name: String
    )

    data class ExtractedDocumentInfo(
        val name: String,
        val mimeType: String,
        val size: Long,
        val extractedContent: String
    )

    private fun getFileName(uri: Uri): String {
        return FileUtil.getFileNameFromUri(context, uri) ?: uri.lastPathSegment ?: "file"
    }

    /**
     * Execute OCR request with retry logic for improved reliability
     */
    private fun executeWithRetry(call: retrofit2.Call<AimlApiResponse>, fileName: String): retrofit2.Response<AimlApiResponse> {
        var lastException: Exception? = null
        val maxRetries = 3
        
        repeat(maxRetries) { attempt ->
            try {
                val response = call.clone().execute()
                
                // Check for server errors that should be retried
                val shouldRetryHttpError = when (response.code()) {
                    502, 503, 504 -> true // Bad Gateway, Service Unavailable, Gateway Timeout
                    500 -> attempt < 1 // Only retry 500 once
                    else -> false
                }
                
                if (shouldRetryHttpError && attempt < maxRetries - 1) {
                    val waitTime = when (response.code()) {
                        502 -> (attempt + 1) * 3000L // 3s, 6s, 9s for Bad Gateway
                        503 -> (attempt + 1) * 5000L // 5s, 10s, 15s for Service Unavailable
                        else -> (attempt + 1) * 2000L // Default backoff
                    }
                    Timber.w("🔍 OCR server error ${response.code()} for $fileName, retrying in ${waitTime}ms (attempt ${attempt + 1})")
                    response.errorBody()?.close()
                    
                    try {
                        Thread.sleep(waitTime)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ie
                    }
                    return@repeat // Continue to next attempt
                }
                
                // Log successful attempts after retries
                if (attempt > 0) {
                    Timber.d("🔍 OCR request succeeded on attempt ${attempt + 1} for file: $fileName")
                }
                
                return response
                
            } catch (e: Exception) {
                lastException = e
                val shouldRetry = when (e) {
                    is java.net.ProtocolException -> {
                        // Retry on "unexpected end of stream" which often resolves on retry
                        e.message?.contains("unexpected end of stream") == true
                    }
                    is java.net.SocketTimeoutException,
                    is java.net.SocketException,
                    is java.io.IOException -> {
                        // Retry on network-related issues
                        !e.message?.contains("Canceled")!! // Don't retry if explicitly canceled
                    }
                    else -> false
                }
                
                if (shouldRetry && attempt < maxRetries - 1) {
                    val waitTime = (attempt + 1) * 2000L // Progressive backoff: 2s, 4s, 6s
                    Timber.w("🔍 OCR attempt ${attempt + 1} failed for $fileName: ${e.message}. Retrying in ${waitTime}ms...")
                    
                    try {
                        Thread.sleep(waitTime)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ie
                    }
                } else {
                    Timber.e(e, "🔍 OCR request failed after ${attempt + 1} attempts for file: $fileName")
                    throw e
                }
            }
        }
        
        // This should never be reached due to the throw in the catch block
        throw lastException ?: Exception("OCR request failed after $maxRetries attempts")
    }

    /**
     * Create a specialized API service for OCR operations with extended timeouts
     */
    private fun getOcrApiService(): AimlApiService {
        return RetrofitInstance.createCustomOcrService()
    }
    private fun getToolStatusText(toolId: String): Pair<String, Int> {
        return when (toolId) {
            "web_search" -> Pair("Searching the web", "#2563EB".toColorInt())
            "calculator", "spreadsheet_creator", "weather", "translator", "wikipedia" ->
                Pair("Analyzing", "#757575".toColorInt())
            else -> Pair("Processing", "#757575".toColorInt())
        }
    }
    /**
     * Creates structured content for image messages to be sent to multimodal AI models
     * Converts images to base64 and creates ContentPart arrays for API requests
     */
    private fun createStructuredContentForImageMessage(message: ChatMessage): List<AimlApiRequest.ContentPart>? {
        return try {
            Timber.d("🖼️ Creating structured content for image message: ${message.id}")
            
            // Parse GroupedFileMessage JSON from message content
            val groupedMessage = JsonUtils.fromJson(message.message, ChatAdapter.GroupedFileMessage::class.java)
            if (groupedMessage == null) {
                Timber.e("Failed to parse GroupedFileMessage JSON: ${message.message}")
                return null
            }
            
            val contentParts = mutableListOf<AimlApiRequest.ContentPart>()
            
            // Add text content if present
            if (!groupedMessage.text.isNullOrBlank()) {
                contentParts.add(AimlApiRequest.ContentPart(
                    type = "text",
                    text = groupedMessage.text
                ))
                Timber.d("✅ Added text content: ${groupedMessage.text.take(50)}...")
            }
            
            // Process images and convert to base64
            for (imageInfo in groupedMessage.images) {
                try {
                    val imageUri = Uri.parse(imageInfo.uri)
                    Timber.d("🖼️ Processing image: ${imageInfo.name} (${imageInfo.type})")
                    
                    // Read image data and encode to base64
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    if (inputStream != null) {
                        val imageBytes = inputStream.readBytes()
                        val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                        
                        // Create image content part with base64 data
                        val imageContentPart = AimlApiRequest.ContentPart(
                            type = "image_url",
                            imageUrl = AimlApiRequest.ImageUrl(
                                url = "data:${imageInfo.type};base64,$base64String",
                                detail = "auto"
                            )
                        )
                        contentParts.add(imageContentPart)
                        
                        inputStream.close()
                        Timber.d("✅ Successfully converted ${imageInfo.name} to base64 (${imageBytes.size} bytes)")
                    } else {
                        Timber.e("Failed to open input stream for image: ${imageInfo.uri}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing image ${imageInfo.name}: ${e.message}")
                    // Continue processing other images even if one fails
                }
            }
            
            if (contentParts.isEmpty()) {
                Timber.w("No content parts created for image message")
                return null
            }
            
            Timber.d("✅ Created ${contentParts.size} content parts for image message")
            return contentParts
            
        } catch (e: Exception) {
            Timber.e(e, "Error creating structured content for image message: ${e.message}")
            return null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Error getting file size")
            0L
        }
    }
}