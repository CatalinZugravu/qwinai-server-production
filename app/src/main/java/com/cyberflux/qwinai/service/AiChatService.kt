package com.cyberflux.qwinai.service

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
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
import com.cyberflux.qwinai.utils.ConversationTokenManager
import com.cyberflux.qwinai.utils.DocumentContentExtractor
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.TokenValidator
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
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
    private val conversationTokenManager: ConversationTokenManager,
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

    // Create a private service scope for internal operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val toolManager by lazy { ToolServiceHelper.getToolManager(context) }

    /**
     * Main method to send a message with smart web search auto-detection
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun sendMessage(
        message: String,
        hasFiles: Boolean,
        fileUris: List<Uri>,
        userMessageId: String,
        createOrGetConversation: (String) -> String,
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low"
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
                        reasoningLevel
                    )
                }
            } else if (message.isNotBlank()) {
                serviceScope.launch {
                    handleTextOnlyMessageAsync(
                        conversationIdString,
                        message,
                        effectiveWebSearchEnabled,
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
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun handleTextOnlyMessageAsync(
        conversationId: String,
        message: String,
        forceWebSearch: Boolean = false,
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
                    Triple("Web search available", Color.parseColor("#2563EB"), true)
                }
                else -> {
                    // Determine if a specific tool is being used
                    val toolId = toolManager.findToolForMessage(message)?.id
                    if (toolId != null) {
                        val (text, color) = getToolStatusText(toolId)
                        Triple(text, color, false)
                    } else {
                        Triple("Generating response", Color.parseColor("#757575"), false)
                    }
                }
            }

            // Create AI message with proper state flags
            val aiMessageId = UUID.randomUUID().toString()
            val aiMessage = ChatMessage(
                id = aiMessageId,
                conversationId = conversationId,
                message = "",
                isUser = false,
                timestamp = System.currentTimeMillis(),
                isGenerating = !isWebSearchActive,
                showButtons = false,
                modelId = modelId,
                aiModel = ModelManager.selectedModel.displayName,
                parentMessageId = userMessageId,
                isWebSearchActive = isWebSearchActive,
                isForceSearch = useWebSearch,
                initialIndicatorText = indicatorText,
                initialIndicatorColor = indicatorColor
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
                callbacks.showError("Error processing message: ${e.message}")
                callbacks.setGeneratingState(false)
            }
        }
    }    /**
     * Process files with smart web search support
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun processAndSendFilesWithFileSupportedModelAsync(
        conversationId: String,
        userText: String,
        fileUris: List<Uri>,
        userMessageId: String,
        forceWebSearch: Boolean = false,
        isReasoningEnabled: Boolean = false,
        reasoningLevel: String = "low"
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

            // Process files
            Timber.d("Processing ${fileUris.size} files for AI model")
            for (fileUri in fileUris) {
                try {
                    val mimeType = context.contentResolver.getType(fileUri) ?: ""
                    val isImage = mimeType.startsWith("image/")
                    val fileName = getFileName(fileUri)
                    val fileSize = getFileSize(fileUri)
                    
                    Timber.d("File analysis: $fileName")
                    Timber.d("  MIME type: $mimeType")
                    Timber.d("  Is image: $isImage")
                    Timber.d("  File size: ${FileUtil.formatFileSize(fileSize)}")

                    fileInfos.add(ChatAdapter.FileInfo(
                        uri = fileUri.toString(),
                        name = fileName,
                        size = fileSize,
                        type = if (isImage) "image" else "document"
                    ))
                    processedFiles.add(fileUri)

                    if (isImage) {
                        processedImages.add(ProcessedImageInfo(fileUri.toString(), "", fileName))
                        Timber.d("Added image to processedImages: $fileName")
                    } else {
                        // Document processing logic - FORCE EXTRACTION FOR ALL MODELS
                        val currentModel = ModelManager.selectedModel
                        val hasNativeSupport = ModelValidator.hasNativeDocumentSupport(currentModel.id)
                        Timber.d("Processing document file: $fileName (URI: $fileUri)")
                        Timber.d("Current model: ${currentModel.displayName} (${currentModel.id})")
                        Timber.d("Native document support: $hasNativeSupport")
                        
                        // CRITICAL FIX: Always extract content for all models to ensure AI can see the text
                        Timber.d("Extracting content for all models to ensure AI can read document text")

                        val fileUriString = fileUri.toString()
                        var extractedContent = DocumentContentExtractor.getCachedContent(fileUriString)

                        if (extractedContent == null && context is MainActivity) {
                            Timber.d("Direct cache lookup failed, trying via selected files")
                            val selectedFile = (context as MainActivity).selectedFiles.find {
                                it.uri.toString() == fileUriString
                            }

                            if (selectedFile != null) {
                                Timber.d("Found file in selected files: ${selectedFile.name}, extracted=${selectedFile.isExtracted}")

                                if (selectedFile.isExtracted && selectedFile.extractedContentId.isNotEmpty()) {
                                    Timber.d("Looking up content with ID: ${selectedFile.extractedContentId}")
                                    extractedContent = DocumentContentExtractor.getCachedContent(selectedFile.extractedContentId)
                                }
                            }
                        }

                        if (extractedContent == null) {
                            Timber.d("Cache lookup failed, extracting content on the fly for file: $fileName")
                            val tempExtractor = DocumentContentExtractor(context)
                            val result = withContext(Dispatchers.IO) {
                                tempExtractor.processDocumentForModel(fileUri)
                            }

                            if (result.isSuccess) {
                                extractedContent = result.getOrNull()
                                if (extractedContent != null) {
                                    Timber.d("On-the-fly extraction successful: ${extractedContent.textContent.length} chars extracted from $fileName")
                                    // Cache the content for potential reuse
                                    DocumentContentExtractor.cacheContent(fileUriString, extractedContent)
                                } else {
                                    Timber.w("Extraction result was successful but content is null for $fileName")
                                }
                            } else {
                                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                                Timber.e("On-the-fly extraction failed for $fileName: $errorMsg")
                                callbacks.showError("Failed to extract content from $fileName: $errorMsg")
                            }
                        }

                        if (extractedContent != null) {
                            val contentLength = extractedContent.textContent.length
                            val tokenCount = extractedContent.tokenCount
                            Timber.d("Adding document with extracted content: $fileName ($contentLength chars, $tokenCount tokens)")
                            
                            // Use the formatForAiModel method to get properly formatted content with metadata
                            val formattedContent = extractedContent.formatForAiModel()
                            
                            extractedDocuments.add(
                                ExtractedDocumentInfo(
                                    name = fileName,
                                    mimeType = mimeType,
                                    size = fileSize,
                                    extractedContent = formattedContent
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
            val documentsForDisplay = fileInfos.filter { it.type == "document" }
            val imagesForProcessing = fileInfos.filter { it.type == "image" }
            
            val groupedMessage = ChatAdapter.GroupedFileMessage(
                images = imagesForProcessing,
                documents = documentsForDisplay, // Keep for display purposes
                text = userVisibleText
            )

            val messageJson = Gson().toJson(groupedMessage)

            val userFileMessage = ChatMessage(
                id = userMessageId,
                message = messageJson,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                conversationId = conversationId,
                isImage = fileInfos.any { it.type == "image" },
                isDocument = fileInfos.any { it.type == "document" },
                prompt = aiOnlyText,
                isForceSearch = forceWebSearch
            )

            withContext(Dispatchers.Main) {
                messageManager.addMessage(userFileMessage)
                // Remove delay for instant indicator display

                if (!chatAdapter.currentList.any { it.id == userMessageId }) {
                    callbacks.showError("Error adding file message to chat. Please try again.")
                    callbacks.setGeneratingState(false)
                    return@withContext
                }
            }

            if (!conversationId.startsWith("private_")) {
                callbacks.saveMessage(userFileMessage)
            }

            // Get current button states and analyze web search need
            val webSearchEnabled = callbacks.getWebSearchEnabled() || forceWebSearch

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
                    Triple("$searchType with files", Color.parseColor("#2563EB"), true)
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
                    
                    Triple(indicatorText, Color.parseColor("#757575"), false)
                }
            }

            Timber.d("🎯 Creating AI message for file processing with smart analysis - WebSearch: $smartWebSearchEnabled")

            // Create AI response with proper state flags
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
                parentMessageId = userMessageId,
                isWebSearchActive = isWebSearchActive,
                isForceSearch = smartWebSearchEnabled,
                initialIndicatorText = indicatorText,
                initialIndicatorColor = indicatorColor
            )

            withContext(Dispatchers.Main) {
                messageManager.addMessage(aiMessage)
                // Remove delay for instant indicator display

                if (!chatAdapter.currentList.any { it.id == aiMessageId }) {
                    callbacks.showError("Error creating AI response. Please try again.")
                    callbacks.setGeneratingState(false)
                    return@withContext
                }

                callbacks.scrollToBottom()
            }

            if (!conversationId.startsWith("private_")) {
                callbacks.saveMessage(aiMessage)
            }

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
        } catch (e: Exception) {
            Timber.e(e, "Error processing and sending files: ${e.message}")
            withContext(Dispatchers.Main) {
                callbacks.setGeneratingState(false)
                callbacks.updateTypingIndicator(false)
                callbacks.showError("Error processing files: ${e.message}")
            }
        }
    }

    /**
     * Start enhanced API call with web search parameters
     */
// Update the startEnhancedApiCall method in AiChatService.kt
// to properly pass the web search flag to StreamingHandler

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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

            } catch (e: CancellationException) {
                Timber.d("Enhanced API request cancelled by user")
                needToRefundCredits = true
                withContext(Dispatchers.Main) {
                    callbacks.setGeneratingState(false)
                    // CRITICAL: Ensure streaming mode is stopped on cancellation
                    chatAdapter.stopStreamingMode()
                    messageManager.stopStreamingMode()
                }
            } catch (e: Exception) {
                Timber.tag("ENHANCED-API-ERROR").e(e, "Error in enhanced API call: ${e.message}")
                withContext(Dispatchers.Main) {
                    callbacks.showError("Error: ${e.localizedMessage ?: e.message ?: "Communication error"}")
                    callbacks.setGeneratingState(false)
                    // CRITICAL: Ensure streaming mode is stopped on error
                    chatAdapter.stopStreamingMode()
                    messageManager.stopStreamingMode()
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

                    // CRITICAL: Final check to ensure streaming mode is properly stopped
                    if (chatAdapter.isStreamingActive) {
                        chatAdapter.stopStreamingMode()
                    }
                    messageManager.stopStreamingMode()
                }
            }
        }
    }
    /**
     * Continue with API call using enhanced approach with tool calls support
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
                            chatAdapter.stopStreamingMode()
                            messageManager.stopStreamingMode()
                        }
                        return
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error reloading messages from database: ${e.message}")
                    withContext(Dispatchers.Main) {
                        callbacks.showError("Error loading conversation context. Please try again.")
                        callbacks.setGeneratingState(false)
                        chatAdapter.stopStreamingMode()
                        messageManager.stopStreamingMode()
                    }
                    return
                }
            }

            // Build messages using enhanced approach
            val userMessages = buildMessagesForEnhancedApproach(conversationId, modelId)

            // Perform smart pre-processing web search if needed using our tool system
            val enhancedMessages = if (enableWebSearch && originalMessageText != null) {
                try {
                    // Get search parameters from analysis
                    val cleanedQuery = WebSearchService.cleanSearchQuery(originalMessageText)
                    val enhancedParams = webSearchParams

                    Timber.d("Performing enhanced web search for: $cleanedQuery with params: $enhancedParams")

                    // Update the message to show web search is in progress
                    withContext(Dispatchers.Main) {
                        messageManager.updateLoadingState(
                            messageId = messageId,
                            isGenerating = true,
                            isWebSearching = true
                        )
                    }

                    // Convert enhanced params to toolParams
                    val toolParams = mutableMapOf<String, Any>(
                        "query" to cleanedQuery
                    )

                    // Add the enhancedParams to toolParams
                    enhancedParams.forEach { (key, value) ->
                        toolParams[key] = value
                    }

                    // Use ToolServiceHelper to execute web search
                    val searchMessage = ToolServiceHelper.createWebSearchMessage(
                        context = context,
                        message = originalMessageText,
                        parameters = toolParams
                    )

                    if (searchMessage != null) {
                        // Insert search message after system message
                        val enhancedList = userMessages.toMutableList()
                        val systemIndex = enhancedList.indexOfFirst { it.role == "system" }
                        if (systemIndex != -1) {
                            enhancedList.add(systemIndex + 1, searchMessage)
                        } else {
                            enhancedList.add(0, searchMessage)
                        }

                        Timber.d("Enhanced messages with web search results from tool system")

                        // Update the message state to show search completed
                        withContext(Dispatchers.Main) {
                            messageManager.updateLoadingState(
                                messageId = messageId,
                                isGenerating = true,
                                isWebSearching = false
                            )
                        }

                        enhancedList
                    } else {
                        // Fall back to existing WebSearchService if our tool fails
                        performFallbackWebSearch(originalMessageText, modelId, webSearchParams, messageId, userMessages)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error performing web search with tool: ${e.message}")

                    // Fall back to existing WebSearchService
                    performFallbackWebSearch(originalMessageText, modelId, webSearchParams, messageId, userMessages)
                }
            } else {
                userMessages
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
                useWebSearch = enableWebSearch,
                messageText = originalMessageText,
                context = context,
                audioEnabled = false,
                audioFormat = "mp3",
                voiceType = "alloy"
            )
            Timber.d("Enhanced API request - model: $modelId, context messages: ${conversationMessages.size}, " +
                    "webSearch: $enableWebSearch, streaming: ${request.stream}")

            val apiService = RetrofitInstance.getApiService(
                RetrofitInstance.ApiServiceType.AIMLAPI,
                AimlApiService::class.java
            )

            // We're now enforcing streaming for real-time updates
            val response = withContext(Dispatchers.IO) {
                try {
                    apiService.sendRawMessageStreaming(requestBody)
                } catch (e: Exception) {
                    Timber.e(e, "Enhanced API streaming request failed: ${e.message}")
                    throw e
                }
            }

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
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
                                chatAdapter.stopStreamingMode()
                                messageManager.stopStreamingMode()
                            }
                        },
                        onError = { errorMessage ->
                            serviceScope.launch(Dispatchers.Main) {
                                callbacks.showError("Enhanced streaming error: $errorMessage")
                                callbacks.setGeneratingState(false)
                                callbacks.updateTypingIndicator(false)

                                // CRITICAL: Stop streaming mode on error
                                chatAdapter.stopStreamingMode()
                                messageManager.stopStreamingMode()
                            }
                        }
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        callbacks.showError("Empty response from the enhanced API")
                        callbacks.setGeneratingState(false)

                        // CRITICAL: Stop streaming mode on empty response
                        chatAdapter.stopStreamingMode()
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
                    chatAdapter.stopStreamingMode()
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
                chatAdapter.stopStreamingMode()
                messageManager.stopStreamingMode()
            }
        }
    }

    // Add this helper method
// In AiChatService.kt - Update performFallbackWebSearch
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun performFallbackWebSearch(
        originalMessageText: String,
        modelId: String,
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
    }    private fun isExplicitWebSearchRequest(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val explicitPatterns = listOf(
            "search the internet",
            "search the web",
            "search online",
            "google",
            "look up online",
            "search for",
            "find information about",
            "web search",
            "internet search",
            "look this up",
            "find out about",
            "get current information",
            "latest information",
            "recent news",
            "what's happening",
            "current events"
        )

        val hasExplicitPattern = explicitPatterns.any { lowerMessage.contains(it) }

        // Also check for question patterns that typically need current info
        val questionPatterns = listOf(
            "what's the latest",
            "what are the current",
            "what is happening",
            "tell me about recent",
            "any news about",
            "latest updates on"
        )

        val hasQuestionPattern = questionPatterns.any { lowerMessage.contains(it) }

        return hasExplicitPattern || hasQuestionPattern
    }    /**
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
                    """You are a helpful AI assistant with enhanced capabilities chatting with ${userName}.
Address them by name when appropriate to personalize the conversation.
Current conversation has ${messages.size} messages total.

You can access current information through web search when needed.
Always cite sources when using web search results and prioritize recent, authoritative information."""
                }
                else -> {
                    """You are a helpful AI assistant chatting with ${userName}.
Address them by name when appropriate to personalize the conversation.
Current conversation has ${messages.size} messages total."""
                }
            }

            val supportsSystemMessages = ModelValidator.supportsSystemMessages(modelId)

            if (supportsSystemMessages) {
                formattedMessages.add(AimlApiRequest.Message("system", systemPrompt))

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
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
        modelId: String,
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
                            Gson().fromJson(responseString, AimlApiResponse::class.java)
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
        Timber.d("Cancelling current generation from AiChatService")

        currentApiJob?.cancel()
        currentApiJob = null

        // CRITICAL: Ensure streaming mode is stopped when cancelling
        stopStreamingMode()

        callbacks.setGeneratingState(false)
        callbacks.updateTypingIndicator(false)
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

        if (processedImages.isNotEmpty()) {
            prompt.append("User has attached ${processedImages.size} image(s):\n")
            processedImages.forEachIndexed { index, image ->
                prompt.append("Image ${index+1}: ${image.name}\n")
            }
            prompt.append("\n")
        }

        if (extractedDocuments.isNotEmpty()) {
            Timber.d("Adding ${extractedDocuments.size} extracted documents to prompt")
            prompt.append("\n=== DOCUMENT CONTENT PROVIDED BELOW ===\n")
            prompt.append("The following documents have been uploaded and their content extracted for you to analyze:\n")
            prompt.append("Total Documents: ${extractedDocuments.size}\n")
            prompt.append("IMPORTANT: All document content is included in this message - you do not need to request file access.\n\n")
            
            extractedDocuments.forEachIndexed { index, doc ->
                prompt.append("DOCUMENT ${index+1}:\n")
                prompt.append("Name: ${doc.name}\n")
                prompt.append("Type: ${doc.mimeType}\n")
                prompt.append("Size: ${FileUtil.formatFileSize(doc.size)}\n")

                if (doc.extractedContent.isNotEmpty()) {
                    // Count tokens in the extracted content
                    val contentTokens = TokenValidator.estimateTokenCount(doc.extractedContent)
                    prompt.append("Content Tokens: $contentTokens\n")
                    prompt.append("\n--- COMPLETE EXTRACTED CONTENT FROM ${doc.name} ---\n")
                    prompt.append(doc.extractedContent)
                    prompt.append("\n--- END OF EXTRACTED CONTENT FROM ${doc.name} ---\n\n")

                    Timber.d("Added ${doc.extractedContent.length} chars ($contentTokens tokens) of extracted content for ${doc.name}")
                } else {
                    Timber.w("Document ${doc.name} has empty extracted content!")
                    prompt.append("⚠️ Content extraction failed for this document\n\n")
                }
            }
            prompt.append("=== END OF DOCUMENT CONTENT ===\n\n")
            prompt.append("Please analyze the provided document content above to answer the user's question.\n")
        } else {
            Timber.d("No extracted documents to add to prompt")
        }

        return prompt.toString()
    }

    private fun validateTokenLimit(message: String, modelId: String, isSubscribed: Boolean): Boolean {
        val (wouldExceed, remainingTokens, _) =
            conversationTokenManager.wouldExceedLimit(message, modelId, isSubscribed)

        if (wouldExceed) {
            val errorMessage = if (isSubscribed) {
                "Your message would exceed the model's token limit. You have $remainingTokens tokens available for this message."
            } else {
                "Your message would exceed the free tier token limit. Upgrade to Pro for higher limits, or shorten your message."
            }

            callbacks.showError(errorMessage)
            return false
        }

        return true
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
                    useWebSearch = false,
                    messageText = userText,
                    context = context,
                    audioEnabled = false,
                    audioFormat = "mp3",
                    voiceType = "alloy"
                )

                val response = withContext(Dispatchers.IO) {
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

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun handleStreamingResponse(
        responseBody: ResponseBody,
        messagePosition: Int,
        messageId: String
    ) {
        // CRITICAL: Ensure streaming mode is active before processing begins
        startStreamingMode()

        coroutineScope.launch {
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
                        }
                    },
                    onError = { errorMessage ->
                        coroutineScope.launch(Dispatchers.Main) {
                            callbacks.showError("Streaming error: $errorMessage")
                            callbacks.setGeneratingState(false)

                            // CRITICAL: Stop streaming mode on error
                            stopStreamingMode()
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in streaming response: ${e.message}")
                coroutineScope.launch(Dispatchers.Main) {
                    callbacks.showError("Error processing response: ${e.message}")
                    callbacks.setGeneratingState(false)

                    // CRITICAL: Stop streaming mode on exception
                    stopStreamingMode()
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
            chatAdapter.stopStreamingMode()

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
            } catch (e: Exception) {
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
                } catch (e: Exception) {
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
        scope.launch {
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
    private fun getToolStatusText(toolId: String): Pair<String, Int> {
        return when (toolId) {
            "web_search" -> Pair("Searching the web", Color.parseColor("#2563EB"))
            "calculator", "spreadsheet_creator", "weather", "translator", "wikipedia" ->
                Pair("Analyzing", Color.parseColor("#757575"))
            else -> Pair("Processing", Color.parseColor("#757575"))
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