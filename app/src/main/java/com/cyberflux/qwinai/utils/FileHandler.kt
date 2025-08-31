package com.cyberflux.qwinai.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import com.cyberflux.qwinai.utils.HapticManager
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.widget.CheckBox
import android.widget.EditText
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.service.OCROptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.cyberflux.qwinai.utils.JsonUtils
import com.cyberflux.qwinai.utils.SupportedFileTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class FileHandler(private val activity: MainActivity) {
    private val animator = FileSendAnimator(activity)
    private val unifiedFileHandler = UnifiedFileHandler(activity)
    private val tokenManager = SimplifiedTokenManager(activity)

    fun openFilePicker(filePickerLauncher: ActivityResultLauncher<Intent>) {
        // Check if current model is an OCR model (specifically Mistral OCR)
        val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
        val isMistralOcr = ModelManager.selectedModel.id == ModelManager.MISTRAL_OCR_ID

        // For Mistral OCR, enforce single file only and check if any files already selected
        if (isMistralOcr && activity.selectedFiles.isNotEmpty()) {
            Toast.makeText(
                activity,
                "Mistral OCR allows only one file at a time. Please remove the current file first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Check if adding files would exceed the 5-file limit (for non-OCR models)
        if (!isOcrModel && activity.selectedFiles.size >= 5) {
            Toast.makeText(
                activity,
                "Maximum 5 files allowed. Please remove some first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Calculate how many more files can be added
        val remainingSlots = if (isMistralOcr) 1 else (5 - activity.selectedFiles.size)

        Intent(Intent.ACTION_GET_CONTENT).apply {
            if (isOcrModel) {
                // For OCR models, allow PDFs and images for OCR processing
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
            } else {
                // For non-OCR models, only allow simplified supported document types
                val currentModelId = ModelManager.selectedModel.id
                val supportedTypes = mutableListOf<String>()
                
                // Always support text and CSV
                supportedTypes.addAll(arrayOf("text/plain", "text/csv", "text/tab-separated-values"))
                
                // Add PDF only if the current AI model supports it
                if (SupportedFileTypes.isPdfSupportedForModel(currentModelId)) {
                    supportedTypes.add("application/pdf")
                    Timber.d("PDF support enabled for model: $currentModelId")
                } else {
                    Timber.d("PDF support disabled for model: $currentModelId")
                }
                
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, supportedTypes.toTypedArray())
                
                // Show user what's supported
                val supportedFormats = if (supportedTypes.contains("application/pdf")) {
                    "text files, CSV files, and PDF files"
                } else {
                    "text files and CSV files only"
                }
                
                if (activity.selectedFiles.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "📄 Supported formats: $supportedFormats",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // For OCR models, restrict to single file selection
            // For non-OCR models, allow multiple documents if slots available
            val allowMultiple = !isOcrModel && remainingSlots > 1
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            
            // Show user feedback about selection capabilities
            if (allowMultiple) {
                val fileType = if (isOcrModel) "image(s)" else "document(s)"
                Toast.makeText(
                    activity,
                    "You can select up to $remainingSlots more $fileType",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Set flag to indicate we're going to file selection
            if (activity is MainActivity) {
                (activity as MainActivity).isReturningFromFileSelection = true
            }

            filePickerLauncher.launch(this)
        }
    }

    fun openDocumentPicker(filePickerLauncher: ActivityResultLauncher<Intent>) {
        // Check if current model is Mistral OCR
        val isMistralOcr = ModelManager.selectedModel.id == ModelManager.MISTRAL_OCR_ID

        // For Mistral OCR, enforce single file only and check if any files already selected
        if (isMistralOcr && activity.selectedFiles.isNotEmpty()) {
            Toast.makeText(
                activity,
                "Mistral OCR allows only one file at a time. Please remove the current file first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Check if adding files would exceed the limit (for non-Mistral OCR models)
        if (!isMistralOcr && activity.selectedFiles.size >= 5) {
            Toast.makeText(
                activity,
                "Maximum 5 files allowed. Please remove some first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Calculate how many more files can be added
        val remainingSlots = if (isMistralOcr) 1 else (5 - activity.selectedFiles.size)

        // Check if current model supports documents
        val currentModel = ModelManager.selectedModel
        !ModelValidator.hasNativeDocumentSupport(currentModel.id)

        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, SupportedFileTypes.SUPPORTED_DOCUMENT_TYPES)
            // For Mistral OCR, always restrict to single file selection
            // For other models, allow multiple if slots available
            val allowMultiple = !isMistralOcr && remainingSlots > 1
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            
            // Show user feedback about selection capabilities
            if (allowMultiple) {
                Toast.makeText(
                    activity,
                    "You can select up to $remainingSlots more document(s)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            // Set flag to indicate we're going to file selection
            if (activity is MainActivity) {
                (activity as MainActivity).isReturningFromFileSelection = true
            }
            
            filePickerLauncher.launch(this)
        }
    }
    suspend fun processDocumentWithExtractor(uri: Uri): Result<SimplifiedDocumentExtractor.ExtractedContent> {
        val currentModelId = ModelManager.selectedModel.id
        val extractor = SimplifiedDocumentExtractor(activity)
        return extractor.extractContent(uri, aiModelId = currentModelId)
    }

    /**
     * NEW: Process file using UnifiedFileHandler with server-side processing
     */
    suspend fun processFileOptimized(
        uri: Uri,
        tracker: FileProgressTracker? = null
    ): Result<UnifiedFileHandler.ProcessedFileResult> {
        val currentModelId = ModelManager.selectedModel.id
        return unifiedFileHandler.processFileForModel(uri, currentModelId, tracker)
    }

    /**
     * Check if file type is supported by the new optimized system
     */
    fun isFileTypeSupported(uri: Uri, fileName: String): Boolean {
        val mimeType = activity.contentResolver.getType(uri) ?: ""
        return when {
            mimeType == "application/pdf" || fileName.endsWith(".pdf", true) -> true
            mimeType == "text/plain" || fileName.endsWith(".txt", true) -> true
            mimeType == "text/csv" || fileName.endsWith(".csv", true) -> true
            else -> false
        }
    }

    /**
     * Calculate total tokens for current message including files and prompt
     */
    suspend fun calculateTotalTokens(
        conversationId: String,
        modelId: String,
        userPrompt: String,
        isSubscribed: Boolean
    ): TokenCalculationResult = withContext(Dispatchers.IO) {
        try {
            // Get files that are ready (extracted and cached)
            val readyFiles = activity.selectedFiles.filter { 
                it.isExtracted && !it.isExtracting 
            }
            
            // Create URI list for SimplifiedTokenManager
            val fileUris = readyFiles.map { it.uri }
            
            Timber.d("🧮 Calculating tokens: Prompt=${userPrompt.length} chars, Files=${readyFiles.size}")
            
            // Use SimplifiedTokenManager to validate
            val validationResult = SimplifiedTokenManager.quickValidate(
                context = activity,
                conversationId = conversationId,
                modelId = modelId,
                userPrompt = userPrompt,
                attachedFiles = fileUris,
                isSubscribed = isSubscribed
            )
            
            // Calculate individual components for display
            val promptTokens = TokenValidator.estimateTokenCount(userPrompt)
            val fileTokens = readyFiles.sumOf { file -> 
                // Try to get token count from processing info or calculate
                extractTokenCountFromProcessingInfo(file) ?: 0
            }
            val totalTokens = promptTokens + fileTokens
            
            when (validationResult) {
                is ContextWindowManager.ValidationResult.Allowed -> {
                    TokenCalculationResult.Allowed(
                        promptTokens = promptTokens,
                        fileTokens = fileTokens,
                        totalTokens = totalTokens,
                        message = "Ready to send • $totalTokens total tokens"
                    )
                }
                
                is ContextWindowManager.ValidationResult.Warning -> {
                    TokenCalculationResult.Warning(
                        promptTokens = promptTokens,
                        fileTokens = fileTokens,
                        totalTokens = totalTokens,
                        usagePercentage = (validationResult.usagePercentage * 100).toInt(),
                        message = "Conversation is ${(validationResult.usagePercentage * 100).toInt()}% of limit. Consider starting a new chat."
                    )
                }
                
                is ContextWindowManager.ValidationResult.Blocked -> {
                    TokenCalculationResult.Blocked(
                        promptTokens = promptTokens,
                        fileTokens = fileTokens,
                        totalTokens = totalTokens,
                        usagePercentage = (validationResult.usagePercentage * 100).toInt(),
                        message = "Conversation is ${(validationResult.usagePercentage * 100).toInt()}% over the length limit. Try replacing the attached files with smaller excerpts."
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating tokens: ${e.message}")
            TokenCalculationResult.Error("Failed to calculate tokens: ${e.message}")
        }
    }
    
    /**
     * Extract token count from file processing info string
     */
    fun extractTokenCountFromProcessingInfo(file: FileUtil.FileUtil.SelectedFile): Int? {
        return try {
            val processingInfo = file.processingInfo ?: return null
            val regex = """(\d+)\s+tokens""".toRegex()
            val match = regex.find(processingInfo)
            match?.groupValues?.get(1)?.toInt()
        } catch (e: Exception) {
            Timber.w("Failed to extract token count from processing info: ${e.message}")
            null
        }
    }
    
    /**
     * Show token limit warning dialog if needed before sending message
     */
    suspend fun validateTokensBeforeSend(
        conversationId: String,
        modelId: String,
        userPrompt: String,
        isSubscribed: Boolean,
        onAllowed: () -> Unit,
        onNewConversationRequired: () -> Unit,
        onCancel: () -> Unit,
        onUpgrade: () -> Unit = {}
    ) {
        val fileUris = activity.selectedFiles.filter { 
            it.isExtracted && !it.isExtracting 
        }.map { it.uri }
        
        tokenManager.validateMessage(
            conversationId = conversationId,
            modelId = modelId,
            userPrompt = userPrompt,
            attachedFiles = fileUris,
            isSubscribed = isSubscribed,
            onAllowed = onAllowed,
            onNewConversationRequired = onNewConversationRequired,
            onCancel = onCancel,
            onUpgrade = onUpgrade
        )
    }

    /**
     * Token calculation result
     */
    sealed class TokenCalculationResult {
        data class Allowed(
            val promptTokens: Int,
            val fileTokens: Int,
            val totalTokens: Int,
            val message: String
        ) : TokenCalculationResult()
        
        data class Warning(
            val promptTokens: Int,
            val fileTokens: Int,
            val totalTokens: Int,
            val usagePercentage: Int,
            val message: String
        ) : TokenCalculationResult()
        
        data class Blocked(
            val promptTokens: Int,
            val fileTokens: Int,
            val totalTokens: Int,
            val usagePercentage: Int,
            val message: String
        ) : TokenCalculationResult()
        
        data class Error(val message: String) : TokenCalculationResult()
    }


    fun handleSelectedDocument(uri: Uri) {
        try {
            // Get file metadata
            val fileName = FileUtil.getFileName(activity, uri)
            val fileSize = FileUtil.getFileSize(activity, uri)
            val mimeType = activity.contentResolver.getType(uri) ?: "application/octet-stream"

            Timber.d("🔍 FileHandler.handleSelectedDocument called")
            Timber.d("  File: $fileName")
            Timber.d("  Size: ${FileUtil.formatFileSize(fileSize)}")
            Timber.d("  MIME: $mimeType")
            Timber.d("  URI: $uri")

            // Check if current model needs extraction
            val currentModel = ModelManager.selectedModel
            val hasNativeSupport = ModelValidator.hasNativeDocumentSupport(currentModel.id)
            val isOcrModel = ModelValidator.isOcrModel(currentModel.id)

            Timber.d("  Model: ${currentModel.displayName} (${currentModel.id})")
            Timber.d("  Native support: $hasNativeSupport")
            Timber.d("  OCR model: $isOcrModel")

            // First, check if this is an unsupported Office file type
            if (SupportedFileTypes.isUnsupportedOfficeType(mimeType)) {
                Timber.w("  -> Unsupported Office file type detected: $mimeType")
                showUnsupportedFileDialog(fileName, mimeType)
                return
            }

            // Check if document is supported for extraction
            if (!SupportedFileTypes.isDocumentTypeSupported(mimeType) && !isOcrModel) {
                // Special case: PDF with non-supporting model
                if (mimeType == "application/pdf" && !SupportedFileTypes.isPdfSupportedForModel(currentModel.id)) {
                    Timber.w("  -> PDF not supported for current model: ${currentModel.id}")
                    showPdfNotSupportedDialog(fileName, currentModel.displayName)
                    return
                } else {
                    Timber.w("  -> Unsupported document type: $mimeType")
                    showUnsupportedFileDialog(fileName, mimeType)
                    return
                }
            }

            if (isOcrModel) {
                // Handle OCR specifically - show OCR options dialog
                Timber.d("  -> Routing to OCR options display")
                showOcrOptionsDialog(uri, fileName, fileSize)
            } else {
                // Route to simplified extraction process
                Timber.d("  -> Routing to simplified extraction process")
                processDocumentWithExtractionAndUI(uri, fileName, mimeType, fileSize)
            }

            // Vibrate to provide feedback
            HapticManager.mediumVibration(activity)

        } catch (e: Exception) {
            Timber.e(e, "Error handling selected document: ${e.message}")
            Toast.makeText(activity, "Error adding document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processDocumentWithExtractionAndUI(uri: Uri, fileName: String, mimeType: String, fileSize: Long) {
        Timber.d("🔧 Starting document extraction process for: $fileName")
        
        // Create a temporary SelectedFile object for displaying
        val tempFile = FileUtil.FileUtil.SelectedFile(
            uri = uri,
            name = fileName,
            size = fileSize,
            isDocument = true,
            isExtracting = true  // Mark as extracting initially
        )

        // Add file to selected files immediately to show in UI
        activity.selectedFiles.add(tempFile)
        activity.fileHandler.updateSelectedFilesView()
        
        // Update button state to disable during extraction
        activity.updateButtonVisibilityAndState()
        
        Timber.d("  Added file to UI, starting extraction...")

        // Find file view to update progress
        val fileView = FileUtil.findFileViewForUri(uri, activity)
        if (fileView == null) {
            Timber.e("Could not find view for document: $uri")
            Toast.makeText(activity, "Error displaying document", Toast.LENGTH_SHORT).show()
            // Clean up on error
            activity.selectedFiles.remove(tempFile)
            activity.updateButtonVisibilityAndState()
            return
        }

        // Create progress tracker and initialize
        val progressTracker = FileProgressTracker()
        progressTracker.initWithImageFileItem(fileView)

        // Show progress
        progressTracker.showProgress()
        progressTracker.observeProgress(activity)

        // Process document in background
        activity.lifecycleScope.launch {
            try {
                // Update progress
                progressTracker.updateProgress(
                    10,
                    "Reading document...",
                    FileProgressTracker.ProcessingStage.READING_FILE
                )

                // Check if file type is supported
                if (!isFileTypeSupported(uri, fileName)) {
                    withContext(Dispatchers.Main) {
                        activity.selectedFiles.remove(tempFile)
                        updateSelectedFilesView()
                        showUnsupportedFileDialog(fileName ?: "Unknown file", mimeType)
                        progressTracker.hideProgress()
                    }
                    return@launch
                }

                // Process document using optimized processor
                val result = processFileOptimized(uri, progressTracker)

                if (result.isSuccess) {
                    val processedFile = result.getOrNull()!!
                    val extractedContent = when (processedFile.contentItem.type) {
                        "text" -> processedFile.contentItem.text ?: "Document processed by server"
                        "file" -> "Document processed directly by AI model"
                        else -> "Document processed by server"
                    }
                        
                        Timber.d("✅ Document processing SUCCESS: ${processedFile.fileType} file processed")

                        withContext(Dispatchers.Main) {
                            // Progress is handled by processor
                            
                            // Use the URI string consistently for the extractedContentId
                            val uriString = uri.toString()

                            // Cache the processed content if it's text content
                            if (processedFile.contentItem.type == "text" && processedFile.contentItem.text != null) {
                                val fileName = FileUtil.getFileName(activity, uri) ?: "document"
                                val fileSize = FileUtil.getFileSize(activity, uri)
                                val contentForCache = SimplifiedDocumentExtractor.ExtractedContent(
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    fileSize = fileSize,
                                    textContent = processedFile.contentItem.text!!,
                                    containsImages = false,
                                    tokenCount = extractedContent.length / 4, // Rough token estimate
                                    pageCount = 1
                                )
                                SimplifiedDocumentExtractor.cacheContent(uriString, contentForCache)
                            }
                            
                            // Update file status with processing information
                            val updatedFile = tempFile.copy(
                                isExtracting = false,
                                isExtracted = true,
                                extractedContentId = uriString,
                                processingInfo = "✅ Document processed by server"
                            )

                            // Replace in selected files list
                            val index = activity.selectedFiles.indexOf(tempFile)
                            if (index != -1) {
                                activity.selectedFiles[index] = updatedFile
                            }

                            // Update UI
                            updateSelectedFilesView()
                            progressTracker.hideProgress()
                            
                            // Update button state to re-enable after extraction
                            activity.updateButtonVisibilityAndState()

                            // Provide feedback with token count
                            Toast.makeText(
                                activity,
                                "📄 Document ready • processed by server",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Unknown error"
                    Timber.e("❌ Document processing FAILED for: $fileName")
                    Timber.e("  Error: $errorMessage")
                    
                    withContext(Dispatchers.Main) {
                        progressTracker.updateProgress(
                            100,
                            "Error: $errorMessage",
                            FileProgressTracker.ProcessingStage.ERROR
                        )

                        // Show specific error messages
                        val userMessage = when {
                            errorMessage.contains("not supported", true) -> 
                                "File type not supported. Only PDF, TXT, and CSV files are allowed."
                            errorMessage.contains("PDF", true) ->
                                "PDF processing failed. This AI model may not support PDF files."
                            else -> "Error processing document: $errorMessage"
                        }

                        Toast.makeText(activity, userMessage, Toast.LENGTH_LONG).show()

                        // Remove file from selected files on error
                        activity.selectedFiles.remove(tempFile)
                        updateSelectedFilesView()
                        
                        // Update button state after error
                        activity.updateButtonVisibilityAndState()

                        // Hide progress
                        progressTracker.hideProgress()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Critical error in document processing: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressTracker.updateProgress(
                        100,
                        "Critical error",
                        FileProgressTracker.ProcessingStage.ERROR
                    )

                    Toast.makeText(
                        activity,
                        "Critical error processing document: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Remove file from selected files on error
                    activity.selectedFiles.remove(tempFile)
                    updateSelectedFilesView()
                    
                    // Update button state after error
                    activity.updateButtonVisibilityAndState()

                    // Hide progress
                    progressTracker.hideProgress()
                }
            }
        }
    }
    /**
     * Handle image selection for OCR models - show options instead of auto-sending
     */
    private fun handleImageWithOcrOptions(uri: Uri, fileName: String, fileSize: Long) {
        try {
            // Check if this is Mistral OCR model
            val isMistralOcr = ModelManager.selectedModel.id == ModelManager.MISTRAL_OCR_ID
            
            // Add image to selected files for display (but don't auto-send)
            val selectedFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = false
            )
            
            activity.selectedFiles.add(selectedFile)
            updateSelectedFilesView()
            
            // Update UI state
            activity.updateButtonVisibilityAndState()
            activity.saveDraftIfNeeded()
            
            // Provide haptic feedback
            HapticManager.mediumVibration(activity)
            
            if (isMistralOcr) {
                // For Mistral OCR with images: hide OCR options panel
                activity.binding.ocrOptionsPanel.visibility = View.GONE
                
                Toast.makeText(
                    activity,
                    "Image selected for Mistral OCR processing. Click Send when ready.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // For other OCR models: show OCR options panel
                activity.binding.ocrOptionsPanel.visibility = View.VISIBLE
                
                Toast.makeText(
                    activity,
                    "Image selected. Configure OCR options and click Send to process.",
                    Toast.LENGTH_LONG
                ).show()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling image with OCR options: ${e.message}")
            Toast.makeText(activity, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle image auto-send for OCR models (DEPRECATED - now using handleImageWithOcrOptions)
     */
    private fun handleImageAutoSend(uri: Uri, fileName: String, fileSize: Long) {
        try {
            // Add image to selected files for display
            val selectedFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = false
            )
            
            activity.selectedFiles.add(selectedFile)
            updateSelectedFilesView()
            
            // Auto-send immediately
            activity.lifecycleScope.launch {
                try {
                    // Create conversation if needed
                    if (activity is MainActivity) {
                        (activity as MainActivity).createOrGetConversation("Image Analysis: $fileName")
                    } else {
                        UUID.randomUUID().toString()
                    }

                    // Send the image with AI model automatically
                    activity.sendMessage()
                    
                    // Clear input and files after sending
                    activity.binding.etInputText.setText("")
                    activity.selectedFiles.clear()
                    // OCR options remain visible in main UI
                    updateSelectedFilesView()
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error auto-sending image: ${e.message}")
                    Toast.makeText(activity, "Error sending image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling image auto-send: ${e.message}")
            Toast.makeText(activity, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle PDF with OCR options - show file but don't auto-send
     */
    private fun handlePdfWithOcrOptions(uri: Uri, fileName: String, fileSize: Long) {
        try {
            // Check if this is Mistral OCR model
            val isMistralOcr = ModelManager.selectedModel.id == ModelManager.MISTRAL_OCR_ID
            
            // For Mistral OCR, check if any files already selected (only one allowed)
            if (isMistralOcr && activity.selectedFiles.isNotEmpty()) {
                Toast.makeText(
                    activity,
                    "Mistral OCR allows only one file at a time. Please remove the current file first.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            // For other OCR models, check if we already have a PDF selected (only one PDF allowed)
            if (!isMistralOcr) {
                val existingPdf = activity.selectedFiles.find { it.name.endsWith(".pdf", ignoreCase = true) }
                if (existingPdf != null) {
                    Toast.makeText(
                        activity,
                        "Only one PDF file can be selected at a time. Please remove the existing PDF first.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
            
            // Add PDF to selected files for display (but don't auto-send)
            val selectedFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = true
            )
            
            activity.selectedFiles.add(selectedFile)
            updateSelectedFilesView()
            
            // Update UI state
            activity.updateButtonVisibilityAndState()
            activity.saveDraftIfNeeded()
            
            // Provide haptic feedback
            HapticManager.mediumVibration(activity)
            
            // Always show OCR options panel for PDFs (both Mistral and other OCR models)
            activity.binding.ocrOptionsPanel.visibility = View.VISIBLE
            
            // Provide feedback that PDF is ready for OCR processing
            Toast.makeText(
                activity,
                "PDF selected. Configure OCR options and click Send to process.",
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling PDF with OCR options: ${e.message}")
            Toast.makeText(activity, "Error processing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Get OCR options from the UI
     */
    fun getOcrOptionsFromUI(): OCROptions {
        return try {
            // Use the main activity UI elements (from activity_main.xml)
            val pagesInput = activity.findViewById<EditText>(R.id.etOcrPages)
            val includeImagesCheckbox = activity.findViewById<CheckBox>(R.id.cbIncludeImages)
            val imageLimitInput = activity.findViewById<EditText>(R.id.etImageLimit)
            val imageMinSizeInput = activity.findViewById<EditText>(R.id.etImageMinSize)
            
            val pages = pagesInput?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val includeImages = includeImagesCheckbox?.isChecked
            val imageLimit = imageLimitInput?.text?.toString()?.trim()?.toIntOrNull()
            val imageMinSize = imageMinSizeInput?.text?.toString()?.trim()?.toIntOrNull()
            
            OCROptions(
                pages = pages,
                includeImageBase64 = includeImages,
                imageLimit = imageLimit,
                imageMinSize = imageMinSize
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting OCR options from UI: ${e.message}")
            OCROptions() // Return default options on error
        }
    }

    /**
     * Show OCR options dialog for PDF processing
     */
    private fun showOcrOptionsDialog(uri: Uri, fileName: String, fileSize: Long) {
        try {
            // Show simple notification that file was selected for OCR
            val dialogBuilder = AlertDialog.Builder(activity)
            dialogBuilder.setTitle("File Selected for OCR")
            dialogBuilder.setMessage("$fileName has been selected for OCR processing.\n\nYou can now configure OCR options in the main UI and click Send when ready.")
            
            dialogBuilder.setPositiveButton("OK") { _, _ ->
                // File remains in selectedFiles list, user can configure options and send
                Toast.makeText(activity, "Configure OCR options below and click Send", Toast.LENGTH_LONG).show()
            }
            
            dialogBuilder.setNegativeButton("Cancel") { _, _ ->
                // Remove from selected files
                activity.selectedFiles.removeAll { it.uri == uri }
                updateSelectedFilesView()
            }
            
            dialogBuilder.show()
            
        } catch (e: Exception) {
            Timber.e(e, "Error showing OCR options dialog: ${e.message}")
            Toast.makeText(activity, "Error showing OCR options", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Start OCR processing with user-selected options
     */
    private fun startOcrProcessing(uri: Uri, fileName: String, ocrOptions: OCROptions) {
        activity.lifecycleScope.launch {
            try {
                // Create conversation if needed
                val conversationId = if (activity is MainActivity) {
                    (activity as MainActivity).createOrGetConversation("OCR: $fileName")
                } else {
                    UUID.randomUUID().toString()
                }

                // Create user message
                val userMessageId = UUID.randomUUID().toString()
                val userMessage = ChatMessage(
                    id = userMessageId,
                    conversationId = conversationId,
                    message = "Extract text from PDF: $fileName",
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                    isDocument = true
                )

                // Add user message to chat
                if (activity is MainActivity) {
                    val currentList = activity.chatAdapter.currentList.toMutableList()
                    currentList.add(userMessage)
                    activity.chatAdapter.submitList(currentList)

                    // Save to database if not in private mode
                    if (!activity.isPrivateModeEnabled) {
                        activity.conversationsViewModel.addChatMessage(userMessage)
                    }

                    // Start OCR processing
                    activity.setGeneratingState(true)
                    activity.binding.typingIndicator.visibility = View.VISIBLE

                    // Process OCR with user options
                    activity.ocrService.processPdfForOcr(
                        uri = uri,
                        conversationId = conversationId,
                        userMessageId = userMessageId,
                        ocrOptions = ocrOptions,
                        progressCallback = { updatedMessage ->
                            activity.runOnUiThread {
                                // Update message in adapter
                                val newList = activity.chatAdapter.currentList.toMutableList()
                                val index = newList.indexOfFirst { it.id == updatedMessage.id }

                                if (index != -1) {
                                    newList[index] = updatedMessage
                                } else {
                                    newList.add(updatedMessage)
                                }

                                activity.chatAdapter.submitList(newList)

                                // Save message if not in private mode
                                if (!activity.isPrivateModeEnabled) {
                                    activity.conversationsViewModel.addChatMessage(updatedMessage)
                                }
                            }
                        },
                        completionCallback = { finalMessage ->
                            activity.runOnUiThread {
                                // Update final message
                                val newList = activity.chatAdapter.currentList.toMutableList()
                                val index = newList.indexOfFirst { it.id == finalMessage.id }

                                if (index != -1) {
                                    newList[index] = finalMessage
                                } else {
                                    newList.add(finalMessage)
                                }

                                activity.chatAdapter.submitList(newList)

                                // Save message if not in private mode
                                if (!activity.isPrivateModeEnabled) {
                                    activity.conversationsViewModel.addChatMessage(finalMessage)
                                }

                                // Reset UI state
                                activity.setGeneratingState(false)
                                activity.binding.typingIndicator.visibility = View.GONE

                                // Clear selected files
                                activity.selectedFiles.clear()
                                // OCR options remain visible in main UI
                                updateSelectedFilesView()

                                // Scroll to bottom
                                activity.scrollToBottom()
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting OCR processing: ${e.message}")
                Toast.makeText(activity, "Error starting OCR: ${e.message}", Toast.LENGTH_SHORT).show()
                
                if (activity is MainActivity) {
                    activity.setGeneratingState(false)
                    activity.binding.typingIndicator.visibility = View.GONE
                }
            }
        }
    }

    fun handlePdfForOcr(uri: Uri) {
        try {
            // Verify that we're using OCR model
            val currentModel = ModelManager.selectedModel
            val isOcrModel = ModelValidator.isOcrModel(currentModel.id)

            if (!isOcrModel) {
                // Switch to OCR model
                if (activity is MainActivity) {
                    (activity as MainActivity).setSelectedModelInSpinner(ModelManager.MISTRAL_OCR_ID)
                }
                Toast.makeText(activity, "Switched to OCR model for PDF processing", Toast.LENGTH_SHORT).show()
            }

            activity.lifecycleScope.launch {
                try {
                    // Get file details
                    val fileName = FileUtil.getFileName(activity, uri)
                    val fileSize = FileUtil.getFileSize(activity, uri)

                    // Add to selectedFiles
                    val selectedFile = FileUtil.FileUtil.SelectedFile(
                        uri = uri,
                        name = fileName,
                        size = fileSize,
                        isDocument = true
                    )

                    activity.selectedFiles.add(selectedFile)
                    updateSelectedFilesView()

                    // Create conversation if needed
                    val conversationId = if (activity is MainActivity) {
                        (activity as MainActivity).createOrGetConversation("OCR: $fileName")
                    } else {
                        UUID.randomUUID().toString()
                    }

                    // Create user message
                    val userMessageId = UUID.randomUUID().toString()
                    val userMessage = ChatMessage(
                        id = userMessageId,
                        conversationId = conversationId,
                        message = "Extract text from PDF: $fileName",
                        isUser = true,
                        timestamp = System.currentTimeMillis(),
                        isDocument = true
                    )

                    // Add user message to chat
                    if (activity is MainActivity) {
                        val currentList = activity.chatAdapter.currentList.toMutableList()
                        currentList.add(userMessage)
                        activity.chatAdapter.submitList(currentList)

                        // Save to database if not in private mode
                        if (!activity.isPrivateModeEnabled) {
                            activity.conversationsViewModel.addChatMessage(userMessage)
                        }

                        // Start OCR processing
                        activity.setGeneratingState(true)
                        activity.binding.typingIndicator.visibility = View.VISIBLE

                        // Process OCR in the background using the OCR service
                        activity.ocrService.processPdfForOcr(
                            uri = uri,
                            conversationId = conversationId,
                            userMessageId = userMessageId,
                            progressCallback = { updatedMessage ->
                                activity.runOnUiThread {
                                    // Update message in adapter
                                    val newList = activity.chatAdapter.currentList.toMutableList()
                                    val index = newList.indexOfFirst { it.id == updatedMessage.id }

                                    if (index != -1) {
                                        newList[index] = updatedMessage
                                    } else {
                                        newList.add(updatedMessage)
                                    }

                                    activity.chatAdapter.submitList(newList)

                                    // Save message if not in private mode
                                    if (!activity.isPrivateModeEnabled) {
                                        activity.conversationsViewModel.addChatMessage(updatedMessage)
                                    }
                                }
                            },
                            completionCallback = { finalMessage ->
                                activity.runOnUiThread {
                                    // Update final message
                                    val newList = activity.chatAdapter.currentList.toMutableList()
                                    val index = newList.indexOfFirst { it.id == finalMessage.id }

                                    if (index != -1) {
                                        newList[index] = finalMessage
                                    } else {
                                        newList.add(finalMessage)
                                    }

                                    activity.chatAdapter.submitList(newList)

                                    // Save message if not in private mode
                                    if (!activity.isPrivateModeEnabled) {
                                        activity.conversationsViewModel.addChatMessage(finalMessage)
                                    }

                                    // Reset UI state
                                    activity.setGeneratingState(false)
                                    activity.binding.typingIndicator.visibility = View.GONE

                                    // Clear selected files
                                    activity.selectedFiles.clear()
                                    // OCR options remain visible in main UI
                                    updateSelectedFilesView()

                                    // Scroll to bottom
                                    activity.scrollToBottom()
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error handling PDF for OCR: ${e.message}")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            "Error processing PDF: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        if (activity is MainActivity) {
                            activity.setGeneratingState(false)
                            activity.binding.typingIndicator.visibility = View.GONE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in handlePdfForOcr: ${e.message}")
            Toast.makeText(activity, "Error processing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
private fun processDocumentWithExtractor(uri: Uri, fileName: String, mimeType: String, fileSize: Long) {
        // Show loading indicator
        val loadingDialog = AlertDialog.Builder(activity)
            .setTitle("Processing Document")
            .setMessage("Extracting text from $fileName...")
            .setCancelable(false)
            .setView(activity.layoutInflater.inflate(R.layout.dialog_loading, null))
            .create()

        loadingDialog.show()

        // Process in background
        activity.lifecycleScope.launch {
            try {
                val extractor = SimplifiedDocumentExtractor(activity)
                val result = extractor.extractContent(uri)

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()

                    if (result.isSuccess) {
                        val extractedContent: SimplifiedDocumentExtractor.ExtractedContent? = result.getOrNull()
                        if (extractedContent != null) {
                            // Add the extracted text to the input field instead of as a file
                            val currentText = activity.binding.etInputText.text.toString()
                            val newText = if (currentText.isBlank()) {
                                "Document: $fileName\n\n${extractedContent.textContent}"
                            } else {
                                "$currentText\n\nDocument: $fileName\n\n${extractedContent.textContent}"
                            }

                            activity.binding.etInputText.setText(newText)

                            // Show success message
                            Toast.makeText(
                                activity,
                                "Document text extracted (${extractedContent.tokenCount} tokens)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        val error = result.exceptionOrNull()
                        Toast.makeText(
                            activity,
                            "Error extracting text: ${error?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(
                        activity,
                        "Error processing document: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    /**
     * Handle a file selected by the user, with improved document content extraction
     * @param uri The URI of the selected file
     * @param isDocument Whether this is a document file (vs an image)
     */
    fun handleSelectedFile(uri: Uri, isDocument: Boolean) {
        try {
            // Get file metadata
            val fileName = FileUtil.getFileName(activity, uri)
            val fileSize = FileUtil.getFileSize(activity, uri)
            val mimeType = activity.contentResolver.getType(uri) ?: "application/octet-stream"

            Timber.d("Handling selected file: $fileName, size: ${FileUtil.formatFileSize(fileSize)}, type: $mimeType, isDocument: $isDocument")

            // Check if this is an image file
            val isImageFile = isImageFile(fileName)
            val isPdfFile = fileName.endsWith(".pdf", ignoreCase = true)
            val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
            
            // Auto-detect if this is actually a document file based on extension
            val actuallyIsDocument = isDocument || isDocumentFile(fileName)
            Timber.d("File classification - isImageFile: $isImageFile, actuallyIsDocument: $actuallyIsDocument, passed isDocument: $isDocument")

            // Special handling for Mistral OCR model
            val isMistralOcr = ModelManager.selectedModel.id == ModelManager.MISTRAL_OCR_ID
            
            if (isMistralOcr) {
                if (isImageFile) {
                    // For Mistral OCR with images: add file without showing OCR options
                    // OCR options should not appear for images
                    addFileToSelectionWithoutOcrOptions(uri, fileName, fileSize, isDocument = false)
                    return
                } else if (isPdfFile) {
                    // For Mistral OCR with PDFs: show OCR options
                    handlePdfWithOcrOptions(uri, fileName, fileSize)
                    return
                }
            }
            
            // For other OCR models with images: show OCR options
            if (isOcrModel && !isMistralOcr && isImageFile) {
                handleImageWithOcrOptions(uri, fileName, fileSize)
                return
            }

            // For other OCR models with PDFs: show file and OCR options
            if (isOcrModel && !isMistralOcr && isPdfFile) {
                handlePdfWithOcrOptions(uri, fileName, fileSize)
                return
            }

            // Create a temporary SelectedFile with processing state
            val tempFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = actuallyIsDocument,
                isExtracting = actuallyIsDocument, // Set to true for documents that need processing
                isExtracted = false
            )

            // IMPORTANT: Show file in UI immediately before processing
            activity.selectedFiles.add(tempFile)
            updateSelectedFilesView()
            
            // Update submit button state immediately
            activity.updateButtonVisibilityAndState()
            
            // For images, make persistent immediately to prevent URI invalidation
            if (isImageFile && !actuallyIsDocument) {
                Timber.d("Making image file persistent immediately: ${fileName}")
                activity.lifecycleScope.launch {
                    try {
                        val persistentStorage = PersistentFileStorage(activity)
                        val persistentFile = tempFile.toPersistent(persistentStorage)
                        if (persistentFile != null) {
                            // Replace the temp file with persistent version
                            val index = activity.selectedFiles.indexOf(tempFile)
                            if (index != -1) {
                                activity.selectedFiles[index] = persistentFile
                                withContext(Dispatchers.Main) {
                                    updateSelectedFilesView()
                                }
                                Timber.d("Image made persistent successfully: ${fileName}")
                            }
                        } else {
                            Timber.w("Failed to make image persistent: ${fileName}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error making image persistent: ${e.message}")
                    }
                }
            }
            
            // Save draft when files are added
            activity.saveDraftIfNeeded()

            // Vibrate to provide feedback that the file was added
            HapticManager.mediumVibration(activity)

            // For documents, check if supported and process accordingly
            if (actuallyIsDocument) {
                // Check if this file type is supported by the optimized system
                if (!isFileTypeSupported(uri, fileName)) {
                    // Remove from selected files and show unsupported dialog
                    activity.selectedFiles.remove(tempFile)
                    updateSelectedFilesView()
                    showUnsupportedFileDialog(fileName, mimeType)
                    return
                }

                Timber.d("📄 Supported document detected, processing with OptimizedFileProcessor: $fileName")

                // Find file view to show extraction progress
                val fileView = FileUtil.findFileViewForUri(uri, activity)
                if (fileView != null) {
                    // Create progress tracker and initialize
                    val progressTracker = FileProgressTracker()
                    progressTracker.initWithImageFileItem(fileView)

                    // Show progress
                    progressTracker.showProgress()
                    progressTracker.observeProgress(activity)

                    // Process file using new optimized processor
                    activity.lifecycleScope.launch {
                        try {
                            // Use optimized file processor
                            val result = processFileOptimized(uri, progressTracker)

                            if (result.isSuccess) {
                                val processedFile = result.getOrNull()!!

                                withContext(Dispatchers.Main) {
                                    // Update progress is handled by the processor
                                    
                                    // Use the URI string consistently as the extracted content ID
                                    val uriString = uri.toString()

                                    // Update file object with processing results
                                    val updatedFile = tempFile.copy(
                                        isExtracting = false,
                                        isExtracted = true,
                                        extractedContentId = uriString,
                                        // Store token count and processing info
                                        processingInfo = "✅ Document processed by server"
                                    )

                                    // Cache the processed content if it's extracted content (not PDF)
                                    if (processedFile.contentItem.type == "text" && processedFile.contentItem.text != null) {
                                        // For compatibility with existing system, create a simplified content object
                                        val contentForCache = SimplifiedDocumentExtractor.ExtractedContent(
                                            fileName = FileUtil.getFileName(activity, uri) ?: "document",
                                            mimeType = activity.contentResolver.getType(uri) ?: "",
                                            fileSize = FileUtil.getFileSize(activity, uri),
                                            textContent = processedFile.contentItem.text ?: "Document processed by server",
                                            containsImages = false,
                                            tokenCount = (processedFile.contentItem.text?.length ?: 0) / 4,
                                            pageCount = 1
                                        )
                                        SimplifiedDocumentExtractor.cacheContent(uriString, contentForCache)
                                    }

                                    // Replace in selected files list
                                    val index = activity.selectedFiles.indexOf(tempFile)
                                    if (index != -1) {
                                        activity.selectedFiles[index] = updatedFile
                                    }

                                    // Update UI to hide loading overlay and show completion
                                    updateSelectedFilesView()
                                    
                                    // Update submit button state now that processing is complete
                                    activity.updateButtonVisibilityAndState()
                                    
                                    Timber.d("✅ Document processing completed: ${processedFile.fileType} file")

                                    // Hide progress after a short delay
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        progressTracker.hideProgress()
                                    }, 500)

                                    // Show success message with token count
                                    Toast.makeText(
                                        activity, 
                                        "📄 Document ready • processed by server",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                val error = result.exceptionOrNull()
                                Timber.e(error, "❌ File processing failed: ${error?.message}")

                                withContext(Dispatchers.Main) {
                                    progressTracker.updateProgress(
                                        100,
                                        "Error: ${error?.message}",
                                        FileProgressTracker.ProcessingStage.ERROR
                                    )

                                    // Remove file from selection as it cannot be processed
                                    activity.selectedFiles.remove(tempFile)
                                    updateSelectedFilesView()

                                    // Show error message with details
                                    val errorMsg = when {
                                        error?.message?.contains("not supported", true) == true -> 
                                            "File type not supported. Only PDF, TXT, and CSV files are allowed."
                                        error?.message?.contains("PDF", true) == true ->
                                            "PDF processing failed. This AI model may not support PDF files."
                                        else -> "File processing failed: ${error?.message}"
                                    }

                                    Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
                                    
                                    // Update submit button state after removal
                                    activity.updateButtonVisibilityAndState()

                                    // Hide progress after a short delay
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        progressTracker.hideProgress()
                                    }, 500)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Critical error in document processing: ${e.message}")

                            withContext(Dispatchers.Main) {
                                progressTracker.updateProgress(
                                    100,
                                    "Critical error",
                                    FileProgressTracker.ProcessingStage.ERROR
                                )

                                // Remove file due to processing failure
                                activity.selectedFiles.remove(tempFile)
                                updateSelectedFilesView()
                                activity.updateButtonVisibilityAndState()

                                Toast.makeText(
                                    activity,
                                    "File processing failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Hide progress after a short delay
                                Handler(Looper.getMainLooper()).postDelayed({
                                    progressTracker.hideProgress()
                                }, 500)
                            }
                        }
                    }
                } else {
                    // No file view found, process in background with optimized processor
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val result = processFileOptimized(uri, null)

                            if (result.isSuccess) {
                                val processedFile = result.getOrNull()!!
                                val uriString = uri.toString()

                                // Cache the processed content if it's extracted content (not PDF)
                                if (processedFile.contentItem.type == "text" && processedFile.contentItem.text != null) {
                                    val contentForCache = SimplifiedDocumentExtractor.ExtractedContent(
                                        fileName = FileUtil.getFileName(activity, uri) ?: "document",
                                        mimeType = activity.contentResolver.getType(uri) ?: "",
                                        fileSize = FileUtil.getFileSize(activity, uri),
                                        textContent = processedFile.contentItem.text ?: "Document processed by server",
                                        containsImages = false,
                                        tokenCount = (processedFile.contentItem.text?.length ?: 0) / 4,
                                        pageCount = 1
                                    )
                                    SimplifiedDocumentExtractor.cacheContent(uriString, contentForCache)
                                }

                                // Update file status on main thread
                                withContext(Dispatchers.Main) {
                                    // Update file as extracted with token info
                                    val updatedFile = tempFile.copy(
                                        isExtracting = false,
                                        isExtracted = true,
                                        extractedContentId = uriString,
                                        processingInfo = "✅ Document processed by server"
                                    )

                                    // Replace in selected files list
                                    val index = activity.selectedFiles.indexOf(tempFile)
                                    if (index != -1) {
                                        activity.selectedFiles[index] = updatedFile
                                    }

                                    // Update UI
                                    updateSelectedFilesView()
                                    activity.updateButtonVisibilityAndState()
                                }

                                // Log success
                                Timber.d("✅ Background processing complete: ${processedFile.fileType} file")
                            } else {
                                val error = result.exceptionOrNull()
                                Timber.e("❌ Background processing failed: ${error?.message}")
                                
                                withContext(Dispatchers.Main) {
                                    // Remove file due to processing failure
                                    activity.selectedFiles.remove(tempFile)
                                    updateSelectedFilesView()
                                    activity.updateButtonVisibilityAndState()
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Error in background document processing: ${e.message}")
                            
                            // Remove file from selection on critical error
                            withContext(Dispatchers.Main) {
                                activity.selectedFiles.remove(tempFile)
                                updateSelectedFilesView()
                                activity.updateButtonVisibilityAndState()
                            }
                        }
                    }
                }
            }

            // Process persistence in the background (for all files) - delayed to allow extraction to complete
            activity.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // For documents, wait a bit to let extraction complete first
                    if (isDocument) {
                        kotlinx.coroutines.delay(2000) // Wait 2 seconds for extraction
                    }
                    
                    // Create PersistentFileStorage instance
                    val persistentStorage = PersistentFileStorage(activity)

                    // Get the current file state (which may have been updated by extraction)
                    var currentFile = tempFile
                    withContext(Dispatchers.Main) {
                        val index = activity.selectedFiles.indexOf(tempFile)
                        if (index != -1) {
                            currentFile = activity.selectedFiles[index]
                        }
                    }

                    // Create a persistent copy with current state
                    val persistentFile = currentFile.toPersistent(persistentStorage)

                    if (persistentFile != null && persistentFile != currentFile) {
                        // Replace the temporary file with the persistent version
                        withContext(Dispatchers.Main) {
                            val index = activity.selectedFiles.indexOf(currentFile)
                            if (index != -1) {
                                activity.selectedFiles[index] = persistentFile
                                // Update UI to reflect the persistent file
                                updateSelectedFilesView()
                            }
                        }
                        Timber.d("File persistence completed for: ${persistentFile.name} (isExtracted: ${persistentFile.isExtracted})")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error creating persistent file: ${e.message}")
                    // File is already displayed, so no UI update needed on failure
                }
            }

            // Scroll to show the selected files
            activity.binding.selectedFilesScrollView.post {
                activity.binding.selectedFilesScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error handling selected file: ${e.message}")
            Toast.makeText(activity, "Error adding file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }    fun updateSelectedFilesView() {
        try {
            activity.binding.selectedFilesContainer.removeAllViews()

            // Handle empty files case
            if (activity.selectedFiles.isEmpty()) {
                if (activity.binding.selectedFilesScrollView.isVisible) {
                    val fadeOut = AlphaAnimation(1.0f, 0.0f)
                    fadeOut.duration = 300
                    fadeOut.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation?) {}
                        override fun onAnimationEnd(animation: Animation?) {
                            activity.binding.selectedFilesScrollView.visibility = View.GONE
                        }
                        override fun onAnimationRepeat(animation: Animation?) {}
                    })
                    activity.binding.selectedFilesScrollView.startAnimation(fadeOut)
                } else {
                    activity.binding.selectedFilesScrollView.visibility = View.GONE
                }
                return
            }

            Timber.d("Updating selected files view with ${activity.selectedFiles.size} files")

            // Make sure files view is visible with animation
            if (activity.binding.selectedFilesScrollView.visibility != View.VISIBLE) {
                activity.binding.selectedFilesScrollView.visibility = View.VISIBLE
                val fadeIn = AlphaAnimation(0.0f, 1.0f)
                fadeIn.duration = 300
                activity.binding.selectedFilesScrollView.startAnimation(fadeIn)
            }

            // Create a list to track files that will be removed due to accessibility issues
            val filesToRemove = mutableListOf<FileUtil.FileUtil.SelectedFile>()
            val filesToUpdate = mutableListOf<Pair<Int, FileUtil.FileUtil.SelectedFile>>()

            // Process each file
            for (i in activity.selectedFiles.indices) {
                val file = activity.selectedFiles[i]

                // Check if file is accessible
                val isAccessible = try {
                    activity.contentResolver.openInputStream(file.uri)?.use { true } == true
                } catch (e: Exception) {
                    Timber.e(e, "File not accessible: ${file.uri}")
                    false
                }

                if (!isAccessible) {
                    // Try to recover the file without triggering MediaProvider errors
                    val recoveredUri = findFileByName(file.name)

                    if (recoveredUri != null) {
                        // Create a new file with updated URI to replace in the list
                        Timber.d("Recovered file URI for ${file.name}")
                        val updatedFile = file.copy(uri = recoveredUri)
                        filesToUpdate.add(Pair(i, updatedFile))

                        // Create and add the file view
                        createAndAddFileView(updatedFile, i)
                    } else {
                        // Couldn't recover, mark for removal
                        Timber.d("Could not recover file ${file.name}, marking for removal")
                        filesToRemove.add(file)

                        // Still show the file but with warning
                        val fileView = createFileView(file, i)
                        fileView.tag = file.uri

                        // Find the file name view and update it
                        val fileNameView = fileView.findViewById<TextView>(R.id.fileName)
                        fileNameView?.text = "${file.name} (inaccessible)"
                        fileNameView?.setTextColor(Color.RED)

                        // Find the icon view and update it
                        val fileIconView = fileView.findViewById<ImageView>(R.id.fileIcon)
                        fileIconView?.setImageResource(R.drawable.ic_warning)
                        fileIconView?.setColorFilter(Color.RED)

                        addFileViewWithAnimation(fileView, i)
                    }
                } else {
                    // File is accessible, create and add view normally
                    createAndAddFileView(file, i)
                }
            }

            // Update files with recovered URIs
            for ((index, updatedFile) in filesToUpdate) {
                if (index < activity.selectedFiles.size) {
                    activity.selectedFiles[index] = updatedFile
                }
            }

            // Remove inaccessible files that couldn't be recovered
            if (filesToRemove.isNotEmpty()) {
                activity.selectedFiles.removeAll(filesToRemove)

                // If we removed all files, hide the container
                if (activity.selectedFiles.isEmpty()) {
                    val fadeOut = AlphaAnimation(1.0f, 0.0f)
                    fadeOut.duration = 300
                    fadeOut.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation?) {}
                        override fun onAnimationEnd(animation: Animation?) {
                            activity.binding.selectedFilesScrollView.visibility = View.GONE
                        }
                        override fun onAnimationRepeat(animation: Animation?) {}
                    })
                    activity.binding.selectedFilesScrollView.startAnimation(fadeOut)
                }

                // Show toast about removed files
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "${filesToRemove.size} inaccessible files removed",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Make sure to update any draft
                if (activity is MainActivity) {
                    activity.saveDraftIfNeeded()
                }
            }

            // Scroll to the right to show the newest file
            activity.binding.selectedFilesScrollView.post {
                activity.binding.selectedFilesScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating selected files view: ${e.message}")
            Toast.makeText(activity, "Error displaying files", Toast.LENGTH_SHORT).show()

            // Last resort: ensure files container is visible if we have files
            if (activity.selectedFiles.isNotEmpty()) {
                activity.binding.selectedFilesScrollView.visibility = View.VISIBLE
            } else {
                activity.binding.selectedFilesScrollView.visibility = View.GONE
            }
        }
    }
    fun findFileByName(fileName: String): Uri? {
        try {
            // STRATEGY 1: Check app's private storage first (most reliable)
            val privateStorageDirs = listOf(
                activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                activity.getExternalFilesDir(null),
                activity.filesDir,
                activity.cacheDir
            )

            for (dir in privateStorageDirs) {
                if (dir != null && dir.exists()) {
                    val potentialFile = dir.listFiles()?.firstOrNull {
                        it.name == fileName || it.name.contains(fileName)
                    }

                    if (potentialFile != null && potentialFile.exists()) {
                        // Found in app's private storage
                        return FileProvider.getUriForFile(
                            activity,
                            "${activity.applicationContext.packageName}.provider",
                            potentialFile
                        )
                    }
                }
            }

            // STRATEGY 2: Try direct access through content resolver
            try {
                // Just find the most recent image as a fallback
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                activity.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,  // No selection, just get most recent
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val id = cursor.getLong(idColumn)

                        Timber.d("Using most recent photo as fallback")
                        return ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore MediaStore errors
                Timber.e(e, "MediaStore query failed: ${e.message}")
            }

            // Not found
            return null
        } catch (e: Exception) {
            Timber.e(e, "Error in findFileByName: ${e.message}")
            return null
        }
    }

    /**
     * Helper method to serialize selected files safely
     */
    fun serializeSelectedFiles(files: List<FileUtil.FileUtil.SelectedFile>): String {
        return try {
            JsonUtils.listToJson(files)
        } catch (e: Exception) {
            Timber.e(e, "Error serializing files: ${e.message}")
            "[]" // Return empty array as fallback
        }
    }

    /**
     * Helper method to deserialize selected files safely
     */
    fun deserializeSelectedFiles(json: String): List<FileUtil.FileUtil.SelectedFile> {
        return try {
            JsonUtils.fromJsonList(json, FileUtil.FileUtil.SelectedFile::class.java) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error deserializing files: ${e.message}")
            emptyList()
        }
    }
    // Helper method to create and add a file view with animation
    private fun createAndAddFileView(file: FileUtil.FileUtil.SelectedFile, index: Int) {
        try {
            val fileView = createFileView(file, index)
            fileView.tag = file.uri
            addFileViewWithAnimation(fileView, index)
        } catch (e: Exception) {
            Timber.e(e, "Error creating file view: ${e.message}")
        }
    }

    // Helper method to add a file view with animation
    private fun addFileViewWithAnimation(fileView: View, index: Int) {
        try {
            val fadeIn = AlphaAnimation(0.0f, 1.0f)
            fadeIn.duration = 300
            fadeIn.startOffset = (index * 50).toLong()
            fileView.startAnimation(fadeIn)

            val layoutParams = LinearLayout.LayoutParams(
                activity.resources.getDimensionPixelSize(R.dimen.file_item_width),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(
                activity.resources.getDimensionPixelSize(R.dimen.file_item_margin_horizontal),
                0,
                activity.resources.getDimensionPixelSize(R.dimen.file_item_margin_horizontal),
                0
            )
            fileView.layoutParams = layoutParams
            activity.binding.selectedFilesContainer.addView(fileView)
        } catch (e: Exception) {
            Timber.e(e, "Error adding file view with animation: ${e.message}")
        }
    }
    private fun createFileView(file: FileUtil.FileUtil.SelectedFile, index: Int = -1): View {
        val fileView = activity.layoutInflater.inflate(R.layout.selected_file_item, null) as MaterialCardView
        fileView.layoutParams = LinearLayout.LayoutParams(
            activity.resources.getDimensionPixelSize(R.dimen.file_item_width),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val fileName = fileView.findViewById<TextView>(R.id.file_name)
        val fileType = fileView.findViewById<TextView>(R.id.file_type)
        val fileSize = fileView.findViewById<TextView>(R.id.file_size)
        val removeButton = fileView.findViewById<MaterialButton>(R.id.remove_button)
        fileView.findViewById<FrameLayout>(R.id.previewContainer)
        val imagePreview = fileView.findViewById<ShapeableImageView>(R.id.image_preview)
        val documentIcon = fileView.findViewById<ImageView>(R.id.document_icon)
        val progressLayout = fileView.findViewById<View>(R.id.progressLayout)
        val loadingOverlay = fileView.findViewById<FrameLayout>(R.id.loadingOverlay)

        fileName.text = file.name
        fileType.text = FileUtil.getFileType(file.name)
        fileSize.text = FileUtil.formatFileSize(file.size)

        val isImage = isImageFile(file.name)
        val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
        
        Timber.d("Creating file view for: ${file.name}, isImage: $isImage, isDocument: ${file.isDocument}, isPdf: $isPdf")

        if (isImage) {
            imagePreview.visibility = View.VISIBLE
            documentIcon.visibility = View.GONE
            progressLayout.visibility = View.GONE
            
            // Show loading overlay if file is being processed
            if (file.isExtracting) {
                loadingOverlay.visibility = View.VISIBLE
                Timber.d("Showing loading overlay for image: ${file.name}")
            } else {
                loadingOverlay.visibility = View.GONE
            }

            Timber.d("Loading image with Glide: ${file.uri}")
            try {
                Glide.with(activity)
                    .load(file.uri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .centerCrop()
                    .into(imagePreview)
                Timber.d("Glide load request initiated for: ${file.uri}")
            } catch (e: Exception) {
                Timber.e(e, "Error setting up Glide load for image: ${file.uri}")
            }

            imagePreview.setOnClickListener { openImage(file.uri) }
        } else if (file.isDocument) {
            // Show document icon
            Timber.d("Setting up document view for: ${file.name}")
            imagePreview.visibility = View.GONE
            documentIcon.visibility = View.VISIBLE

            // Get file extension and set appropriate icon
            val extension = file.name.substringAfterLast('.', "").lowercase()
            val iconDrawable = when (extension) {
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx" -> R.drawable.ic_word
                "xls", "xlsx" -> R.drawable.ic_excel
                "ppt", "pptx" -> R.drawable.ic_powerpoint
                "txt" -> R.drawable.ic_text
                else -> R.drawable.ic_document
            }
            Timber.d("Setting document icon for extension '$extension': $iconDrawable")
            documentIcon.setImageResource(iconDrawable)

            // Show extraction status if applicable
            val fileInfoLayout = fileView.findViewById<LinearLayout>(R.id.file_info_layout)
            if (file.isExtracting) {
                fileInfoLayout.visibility = View.GONE
                progressLayout.visibility = View.VISIBLE
                loadingOverlay.visibility = View.VISIBLE
                Timber.d("Showing loading overlay for document: ${file.name} (extracting)")
                // The progress is handled by FileProgressTracker
            } else {
                progressLayout.visibility = View.GONE
                fileInfoLayout.visibility = View.VISIBLE
                loadingOverlay.visibility = View.GONE
                Timber.d("Document processing complete, hiding loading overlay: ${file.name}")
                // Document is processed - show normal file info
            }

            documentIcon.setOnClickListener {
                if (!file.isExtracting && !file.isExtracted) {
                    openFile(file.uri, file.name)
                }
            }
        } else {
            // Handle other document types
            Timber.d("Setting up generic file view for: ${file.name} (not marked as image or document)")
            imagePreview.visibility = View.GONE
            documentIcon.visibility = View.VISIBLE
            progressLayout.visibility = View.GONE
            
            // Show loading overlay if file is being processed
            if (file.isExtracting) {
                loadingOverlay.visibility = View.VISIBLE
                Timber.d("Showing loading overlay for generic file: ${file.name}")
            } else {
                loadingOverlay.visibility = View.GONE
            }

            val extension = file.name.substringAfterLast('.', "").lowercase()
            val iconDrawable = when (extension) {
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx" -> R.drawable.ic_word
                "xls", "xlsx" -> R.drawable.ic_excel
                "ppt", "pptx" -> R.drawable.ic_powerpoint
                "txt" -> R.drawable.ic_text
                else -> R.drawable.ic_document
            }
            Timber.d("Setting generic file icon for extension '$extension': $iconDrawable")
            documentIcon.setImageResource(iconDrawable)
            documentIcon.setOnClickListener { openFile(file.uri, file.name) }
        }

        removeButton.setOnClickListener {
            // Provide haptic feedback using HapticManager
            HapticManager.mediumVibration(activity)
            
            // Use index if available, otherwise find by URI
            if (index >= 0 && index < activity.selectedFiles.size) {
                val removedFile = activity.selectedFiles.removeAt(index)
                Timber.d("Removed file by index $index: ${removedFile.name}")
            } else {
                // Fallback: Find the file by URI to ensure we remove the correct one
                val fileToRemove = activity.selectedFiles.find { it.uri == file.uri }
                if (fileToRemove != null) {
                    activity.selectedFiles.remove(fileToRemove)
                    Timber.d("Removed file by URI: ${file.name}")
                } else {
                    // Second fallback: try to find by name and size
                    val fallbackFile = activity.selectedFiles.find { it.name == file.name && it.size == file.size }
                    if (fallbackFile != null) {
                        activity.selectedFiles.remove(fallbackFile)
                        Timber.d("Removed file by name/size: ${file.name}")
                    } else {
                        Timber.w("Could not find file to remove: ${file.name}")
                    }
                }
            }
            
            // Check if we removed a PDF file and hide OCR options if no PDFs left
            val hasAnyPdf = activity.selectedFiles.any { it.name.endsWith(".pdf", ignoreCase = true) }
            if (!hasAnyPdf) {
                // OCR options remain visible in main UI
            }
            
            updateSelectedFilesView()
            // Update submit button state after file removal
            activity.updateButtonVisibilityAndState()
            // Save draft when files are removed
            activity.saveDraftIfNeeded()
        }

        // Handle processing and processed states using proper layout visibility
        val fileInfoLayout = fileView.findViewById<LinearLayout>(R.id.file_info_layout)
        
        if (file.isExtracting) {
            // Show processing state using progressLayout
            fileInfoLayout.visibility = View.GONE
            progressLayout.visibility = View.VISIBLE
        } else {
            // Show normal state - show file type and size, hide processing
            progressLayout.visibility = View.GONE
            fileInfoLayout.visibility = View.VISIBLE
            
            // Set proper file type (not "Processed")
            fileType.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary))
        }
        
        // Handle error state
        if (file.hasError) {
            // Add red stroke around the card
            fileView.strokeColor = Color.RED
            fileView.strokeWidth = 4 // 4dp error stroke width
            
            // Update file name color to red
            fileName.setTextColor(Color.RED)
            
            // Add error indicator to remove button
            removeButton.setTextColor(Color.RED)
            removeButton.iconTint = ColorStateList.valueOf(Color.RED)
            
            // Add error overlay if needed
            fileView.alpha = 0.8f
        } else {
            // Reset to normal state
            fileView.strokeColor = Color.TRANSPARENT
            fileView.strokeWidth = 0
            fileView.alpha = 1.0f
        }
        
        // Final debug log
        Timber.d("File view created for ${file.name}: imagePreview=${imagePreview.visibility}, documentIcon=${documentIcon.visibility}")
        return fileView
    }

    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }
    
    private fun isDocumentFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods", "odp")
    }


    fun openFile(uri: Uri, fileName: String) {
        Timber.d("Attempting to open file: $uri")
        try {
            val mimeType = FileUtil.getMimeType(activity, uri) ?: "application/octet-stream"
            val cacheDir = activity.cacheDir
            val tempFile = File(cacheDir, "shared_${System.currentTimeMillis()}_$fileName")
            activity.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Failed to open input stream")
            val tempUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                tempFile
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(tempUri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolveInfo = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo.isNotEmpty()) {
                for (info in resolveInfo) {
                    activity.grantUriPermission(
                        info.activityInfo.packageName,
                        tempUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                activity.startActivity(intent)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error deleting temp file: ${e.message}")
                    }
                }, 10 * 60 * 1000)
                return
            } else {
                Toast.makeText(activity, "No app found to open this file type", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Timber.e(e, "Error opening file: ${e.message}")
            Toast.makeText(activity, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openImage(uri: Uri) {
        Timber.d("Opening image: $uri")
        try {
            val fileName = "temp_image_${System.currentTimeMillis()}.jpg"
            val tempFile = File(activity.cacheDir, fileName)
            activity.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Failed to open input stream")
            val tempUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                tempFile
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(tempUri, "image/*")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val resolveInfo = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo.isNotEmpty()) {
                for (info in resolveInfo) {
                    activity.grantUriPermission(
                        info.activityInfo.packageName,
                        tempUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                activity.startActivity(intent)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error deleting temp image file: ${e.message}")
                    }
                }, 10 * 60 * 1000)
                return
            } else {
                showImageDialog(tempUri)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error opening image: ${e.message}")
            Toast.makeText(activity, "Error opening image: ${e.message}", Toast.LENGTH_SHORT).show()
            try {
                showImageDialog(uri)
            } catch (e2: Exception) {
                Timber.e(e2, "Error showing image dialog: ${e2.message}")
            }
        }
    }

    private fun showImageDialog(uri: Uri) {
        try {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(true)
            val view = activity.layoutInflater.inflate(R.layout.dialog_image_viewer, null)
            val imageView = view.findViewById<ImageView>(R.id.fullScreenImageView)
            val closeButton = view.findViewById<ImageButton>(R.id.btnCloseImageViewer)
            Glide.with(activity)
                .load(uri)
                .into(imageView)
            closeButton.setOnClickListener {
                dialog.dismiss()
            }
            dialog.setContentView(view)
            dialog.show()
        } catch (e: Exception) {
            Timber.e(e, "Error showing image dialog: ${e.message}")
            Toast.makeText(activity, "Could not display image", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Check if any files are still being processed
     */
    fun areFilesStillProcessing(): Boolean {
        return activity.selectedFiles.any { it.isExtracting && !it.isExtracted }
    }

    /**
     * Get the count of files still being processed
     */
    fun getProcessingFilesCount(): Int {
        return activity.selectedFiles.count { it.isExtracting && !it.isExtracted }
    }

    /**
     * Update the submit button state based on file processing status
     */
    fun updateSubmitButtonState() {
        val isProcessing = areFilesStillProcessing()
        val hasText = activity.binding.etInputText.text.toString().trim().isNotEmpty()
        val hasFiles = activity.selectedFiles.isNotEmpty()
        val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
        
        // Button should be enabled only if:
        // 1. There's text or files to send
        // 2. No files are currently being processed
        // 3. Not currently generating a response
        // For non-OCR models: also enable if there's text (even without files)
        val shouldEnable = if (isOcrModel) {
            (hasText || hasFiles) && !isProcessing && !activity.isGenerating
        } else {
            // For non-OCR models: enable if there's text, regardless of files
            hasText && !isProcessing && !activity.isGenerating
        }
        
        activity.binding.btnSubmitText.isEnabled = shouldEnable
        
        // Update visual state
        if (isProcessing) {
            activity.binding.btnSubmitText.alpha = 0.5f
            activity.binding.btnSubmitText.contentDescription = "Processing files..."
            // Add a subtle color tint to indicate processing state
            activity.binding.btnSubmitText.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.disabled_button_tint)
            )
        } else {
            activity.binding.btnSubmitText.alpha = 1.0f
            activity.binding.btnSubmitText.contentDescription = "Send message"
            // Reset to normal color
            activity.binding.btnSubmitText.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, android.R.color.white)
            )
        }
        
        Timber.d("Submit button state updated: enabled=$shouldEnable, processing=$isProcessing, hasText=$hasText, hasFiles=$hasFiles")
    }

    // Replace the entire handleSubmitTextClick method in FileHandler class
    fun handleSubmitTextClick() {
        (activity as? BaseThemedActivity)?.provideHapticFeedback(50)

        if (activity.isGenerating) {
            activity.stopGeneration()
        } else {
            // Check if files are still being processed
            if (areFilesStillProcessing()) {
                val processingCount = getProcessingFilesCount()
                Toast.makeText(
                    activity,
                    "Please wait... $processingCount file(s) still being processed",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            // Check if we have a PDF file for OCR processing
            val pdfFile = activity.selectedFiles.find { it.name.endsWith(".pdf", ignoreCase = true) }
            val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
            
            if (pdfFile != null && isOcrModel) {
                // Handle PDF OCR processing
                handlePdfOcrSubmit(pdfFile)
                return
            }
            
            // If there are files, use very simple animation
            if (activity.selectedFiles.isNotEmpty()) {
                Timber.d("Starting simplified file animation for ${activity.selectedFiles.size} files")

                // Disable button immediately to prevent double-clicks
                activity.binding.btnSubmitText.isEnabled = false

                // Capture file views for animation
                val fileViews = ArrayList<View>()
                for (i in 0 until activity.binding.selectedFilesContainer.childCount) {
                    fileViews.add(activity.binding.selectedFilesContainer.getChildAt(i))
                }

                // Create very simple, guaranteed-to-work animation
                simpleFadeAndShrinkAnimation(fileViews) {
                    Timber.d("Simple animation completed, sending message")

                    // Re-enable button
                    activity.binding.btnSubmitText.isEnabled = true

                    // Send message
                    activity.sendMessage()

                    // Clear input
                    activity.binding.etInputText.setText("")

                    // Clear files with short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        activity.selectedFiles.clear()
                        // OCR options remain visible in main UI
                        updateSelectedFilesView()
                        // Clear draft after sending message
                        activity.clearDraft()
                    }, 100)
                }
            } else {
                // No files, just send normally
                activity.sendMessage()
                activity.binding.etInputText.setText("")
                // Clear draft after sending message
                activity.clearDraft()
            }
        }
    }

    /**
     * Handle PDF OCR submission with user options
     */
    private fun handlePdfOcrSubmit(pdfFile: FileUtil.FileUtil.SelectedFile) {
        try {
            // Get OCR options from the UI
            val ocrOptions = getOcrOptionsFromUI()
            
            // Hide OCR options UI
            // OCR options remain visible in main UI
            
            // Start OCR processing
            startOcrProcessing(pdfFile.uri, pdfFile.name, ocrOptions)
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling PDF OCR submit: ${e.message}")
            Toast.makeText(activity, "Error processing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Add this new simple animation method to FileHandler class
    private fun simpleFadeAndShrinkAnimation(fileViews: List<View>, onComplete: () -> Unit) {
        try {
            Timber.d("Starting simplified animation with ${fileViews.size} views")

            // If no views, complete immediately
            if (fileViews.isEmpty()) {
                Timber.d("No file views to animate")
                onComplete()
                return
            }

            // Add vibration feedback
            HapticManager.mediumVibration(activity)

            // Track completion
            var animatedCount = 0

            // Safety timeout - make sure we complete even if animations fail
            Handler(Looper.getMainLooper()).postDelayed({
                if (animatedCount < fileViews.size) {
                    Timber.w("Animation timeout - forcing completion")
                    onComplete()
                }
            }, 2000)

            // Animate each file view with staggered timing
            fileViews.forEachIndexed { index, view ->
                // Delay each animation slightly
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Create very simple animations that are almost guaranteed to work
                        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0f)
                        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0f)
                        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)

                        val animSet = AnimatorSet()
                        animSet.playTogether(scaleX, scaleY, alpha)
                        animSet.duration = 500

                        // Check if this is the last view
                        val isLastView = index == fileViews.size - 1

                        // Add listener only to last view
                        if (isLastView) {
                            animSet.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    Timber.d("Last view animation completed")
                                    animatedCount = fileViews.size
                                    onComplete()
                                }
                            })
                        }

                        // Log and start animation
                        Timber.d("Starting animation for file view $index")
                        animSet.start()

                    } catch (e: Exception) {
                        Timber.e(e, "Error animating file view $index")

                        // If this was the last view, ensure we complete
                        if (index == fileViews.size - 1) {
                            Timber.d("Error on last view - ensuring completion")
                            Handler(Looper.getMainLooper()).post {
                                animatedCount = fileViews.size
                                onComplete()
                            }
                        }
                    }
                }, index * 100L) // 100ms delay between each view
            }

        } catch (e: Exception) {
            Timber.e(e, "Fatal error in simplified animation: ${e.message}")
            onComplete() // Ensure completion on error
        }
    }    private fun animateFilesWithFallback(
        sourceContainer: ViewGroup,
        targetRecyclerView: RecyclerView,
        onComplete: () -> Unit
    ) {
        try {
            // Log the animation attempt
            Timber.d("Starting file animation with ${sourceContainer.childCount} files")

            // Validate views - with more relaxed conditions
            if (sourceContainer.childCount == 0) {
                Timber.d("No files to animate, calling onComplete")
                onComplete()
                return
            }



            // Force layout pass to ensure views are measured
            sourceContainer.post {
                try {
                    // Create overlay for animation
                    val rootView = (activity as Activity).window.decorView as ViewGroup
                    val overlayView = FrameLayout(activity)
                    val layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    overlayView.setBackgroundColor(Color.TRANSPARENT)
                    rootView.addView(overlayView, layoutParams)

                    // Get coordinates of target
                    val targetLocation = IntArray(2)
                    targetRecyclerView.getLocationOnScreen(targetLocation)
                    val targetX = targetLocation[0] + targetRecyclerView.width / 2
                    val targetY = targetLocation[1] + targetRecyclerView.height - 200

                    // Track animation completion
                    var completedCount = 0
                    val totalCount = sourceContainer.childCount

                    // Function to check if all animations are complete
                    fun checkCompletion() {
                        completedCount++
                        if (completedCount >= totalCount) {
                            // Remove overlay and complete
                            rootView.removeView(overlayView)
                            onComplete()
                        }
                    }

                    // Animate each file with staggered timing
                    for (i in 0 until sourceContainer.childCount) {
                        val fileView = sourceContainer.getChildAt(i)

                        // Delay each animation slightly
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                // Create snapshot of the view
                                val bitmap = createViewBitmap(fileView)
                                val imageView = ImageView(activity)
                                imageView.setImageBitmap(bitmap)
                                imageView.layoutParams = FrameLayout.LayoutParams(
                                    fileView.width,
                                    fileView.height
                                )

                                // Get source view location
                                val sourceLocation = IntArray(2)
                                fileView.getLocationOnScreen(sourceLocation)

                                // Position the view copy
                                imageView.x = sourceLocation[0].toFloat()
                                imageView.y = sourceLocation[1].toFloat()

                                // Add to overlay
                                overlayView.addView(imageView)

                                // Create simple animation path
                                val path = Path()
                                path.moveTo(sourceLocation[0].toFloat(), sourceLocation[1].toFloat())

                                // Add curve to path
                                val midX = (sourceLocation[0] + targetX) / 2
                                val controlY = sourceLocation[1] - 300f
                                path.quadTo(midX.toFloat(), controlY, targetX.toFloat(), targetY.toFloat())

                                // Create animator that follows the path
                                val pathAnimator = ObjectAnimator.ofFloat(imageView, View.X, View.Y, path)

                                // Add scale and fade animations
                                val scaleX = ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1f, 0.3f)
                                val scaleY = ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1f, 0.3f)
                                val alpha = ObjectAnimator.ofFloat(imageView, View.ALPHA, 1f, 0f)

                                // Create animator set
                                val animatorSet = AnimatorSet()
                                animatorSet.playTogether(pathAnimator, scaleX, scaleY, alpha)
                                animatorSet.duration = (800 + (i * 50)).toLong()

                                // Add completion listener
                                animatorSet.addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        overlayView.removeView(imageView)
                                        checkCompletion()
                                    }
                                })

                                // Start animation
                                animatorSet.start()

                                // Add haptic feedback for first item
                                if (i == 0) {
                                    HapticManager.mediumVibration(activity)
                                }

                            } catch (e: Exception) {
                                Timber.e(e, "Error animating file view $i: ${e.message}")
                                checkCompletion() // Ensure we complete even on error
                            }
                        }, i * 100L) // Stagger animations
                    }

                    // Safety timeout - complete after 3 seconds regardless
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (completedCount < totalCount) {
                            Timber.w("Animation timeout - forcing completion")
                            rootView.removeView(overlayView)
                            onComplete()
                        }
                    }, 3000)

                } catch (e: Exception) {
                    Timber.e(e, "Error in main animation block: ${e.message}")
                    onComplete() // Ensure completion on error
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Fatal error in animation setup: ${e.message}")
            onComplete() // Ensure completion on error
        }
    }

    private fun createViewBitmap(view: View): Bitmap {
        // Create a bitmap of the source view
        val bitmap = Bitmap.createBitmap(
            Math.max(view.width, 1),
            Math.max(view.height, 1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    // Add this class to your project (no dependencies on missing variables)
    class FileSendAnimator(private val context: Context) {

        // Enhanced animation for files being sent with proper image/file separation
        fun animateSelectedFilesToChat(
            selectedFilesContainer: LinearLayout,
            targetRecyclerView: RecyclerView,
            onAnimationEnd: () -> Unit = {}
        ) {
            // Debug logging
            Timber.d("Starting file animation with ${selectedFilesContainer.childCount} files")

            // Check if there are any files to animate
            if (selectedFilesContainer.childCount == 0) {
                Timber.d("No files to animate, calling onAnimationEnd")
                onAnimationEnd()
                return
            }

            // Validate that views are properly initialized
            if (!validateAnimationPreconditions(selectedFilesContainer, targetRecyclerView)) {
                Timber.w("Animation preconditions not met, fallback to immediate completion")
                onAnimationEnd()
                return
            }

            try {
                // Get all file views and categorize them
                val allFileViews = mutableListOf<View>()
                val imageViews = mutableListOf<View>()
                val documentViews = mutableListOf<View>()

                for (i in 0 until selectedFilesContainer.childCount) {
                    val fileView = selectedFilesContainer.getChildAt(i)
                    allFileViews.add(fileView)

                    // Check if this is an image or document based on the view content
                    val isImage = isImageFileView(fileView)
                    Timber.d("File $i is ${if (isImage) "image" else "document"}")

                    if (isImage) {
                        imageViews.add(fileView)
                    } else {
                        documentViews.add(fileView)
                    }
                }

                Timber.d("Categorized files: ${imageViews.size} images, ${documentViews.size} documents")

                // Get coordinates of target (the bottom of RecyclerView where new message will appear)
                val targetLocation = IntArray(2)
                targetRecyclerView.getLocationOnScreen(targetLocation)
                val targetX = targetLocation[0] + targetRecyclerView.width / 2
                val targetY = targetLocation[1] + targetRecyclerView.height - 200

                Timber.d("Target coordinates: ($targetX, $targetY)")

                // Create overlay for animation
                val rootView = (context as Activity).window.decorView as ViewGroup
                val overlayView = FrameLayout(context)
                val layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                overlayView.setBackgroundColor(Color.TRANSPARENT)
                rootView.addView(overlayView, layoutParams)

                // Start animation sequence: Images first, then documents
                animateFileSequence(
                    imageViews,
                    documentViews,
                    overlayView,
                    targetX.toFloat(),
                    targetY.toFloat(),
                    rootView
                ) {
                    // All animations completed
                    Timber.d("All animations completed, cleaning up")
                    try {
                        rootView.removeView(overlayView)
                    } catch (e: Exception) {
                        Timber.e(e, "Error removing overlay: ${e.message}")
                    }
                    onAnimationEnd()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in animateSelectedFilesToChat: ${e.message}")
                onAnimationEnd()
            }
        }

        private fun validateAnimationPreconditions(
            selectedFilesContainer: LinearLayout,
            targetRecyclerView: RecyclerView
        ): Boolean {
            return try {
                // Check if views are attached and have valid dimensions
                val containerAttached = selectedFilesContainer.isAttachedToWindow
                val recyclerAttached = targetRecyclerView.isAttachedToWindow
                val hasChildren = selectedFilesContainer.childCount > 0
                val hasValidDimensions = selectedFilesContainer.width > 0 &&
                        selectedFilesContainer.height > 0 &&
                        targetRecyclerView.width > 0 &&
                        targetRecyclerView.height > 0

                Timber.d("Animation validation - Container attached: $containerAttached, " +
                        "Recycler attached: $recyclerAttached, Has children: $hasChildren, " +
                        "Valid dimensions: $hasValidDimensions")

                containerAttached && recyclerAttached && hasChildren && hasValidDimensions
            } catch (e: Exception) {
                Timber.e(e, "Error in validation: ${e.message}")
                false
            }
        }

        private fun animateFileSequence(
            imageViews: List<View>,
            documentViews: List<View>,
            overlayView: FrameLayout,
            targetX: Float,
            targetY: Float,
            rootView: ViewGroup,
            onComplete: () -> Unit
        ) {
            var completedAnimations = 0
            val totalAnimations = imageViews.size + documentViews.size

            fun checkCompletion() {
                completedAnimations++
                if (completedAnimations >= totalAnimations) {
                    onComplete()
                }
            }

            // First animate images with staggered timing
            imageViews.forEachIndexed { index, imageView ->
                Handler(Looper.getMainLooper()).postDelayed({
                    animateSingleFileWithBeautifulEffect(
                        sourceView = imageView,
                        overlayView = overlayView,
                        targetX = targetX,
                        targetY = targetY,
                        animationIndex = index,
                        isImage = true,
                        onComplete = { checkCompletion() }
                    )
                }, index * 150L) // 150ms delay between each image
            }

            // Then animate documents after images (with additional delay)
            val imageAnimationDuration = imageViews.size * 150L + 800L // Wait for images + extra buffer
            documentViews.forEachIndexed { index, documentView ->
                Handler(Looper.getMainLooper()).postDelayed({
                    animateSingleFileWithBeautifulEffect(
                        sourceView = documentView,
                        overlayView = overlayView,
                        targetX = targetX,
                        targetY = targetY,
                        animationIndex = index,
                        isImage = false,
                        onComplete = { checkCompletion() }
                    )
                }, imageAnimationDuration + (index * 120L)) // 120ms delay between documents
            }

            // If no files to animate, call completion immediately
            if (totalAnimations == 0) {
                onComplete()
            }
        }

        private fun animateSingleFileWithBeautifulEffect(
            sourceView: View,
            overlayView: FrameLayout,
            targetX: Float,
            targetY: Float,
            animationIndex: Int,
            isImage: Boolean,
            onComplete: () -> Unit
        ) {
            try {
                // Create enhanced copy of the view
                val viewCopy = createEnhancedViewCopy(sourceView, isImage)
                overlayView.addView(viewCopy)

                // Get source view location
                val sourceLocation = IntArray(2)
                sourceView.getLocationOnScreen(sourceLocation)

                // Position the copy at the source location
                viewCopy.x = sourceLocation[0].toFloat()
                viewCopy.y = sourceLocation[1].toFloat()

                // Create beautiful curved path animation
                val pathAnimator = createBeautifulPathAnimation(
                    viewCopy,
                    sourceLocation[0].toFloat(),
                    sourceLocation[1].toFloat(),
                    targetX,
                    targetY,
                    animationIndex,
                    isImage
                )

                // Create enhanced scale and rotation animations
                val scaleXAnimator = ObjectAnimator.ofFloat(viewCopy, View.SCALE_X, 1f, 1.2f, 0.3f)
                val scaleYAnimator = ObjectAnimator.ofFloat(viewCopy, View.SCALE_Y, 1f, 1.2f, 0.3f)
                val rotationAnimator = ObjectAnimator.ofFloat(viewCopy, View.ROTATION, 0f, 15f, -10f, 0f)
                val alphaAnimator = ObjectAnimator.ofFloat(viewCopy, View.ALPHA, 1f, 1f, 0.8f, 0f)

                // Create sparkle effect for images
                val sparkleAnimator = if (isImage) {
                    createSparkleEffect(viewCopy)
                } else {
                    null
                }

                // Combine all animations
                val animatorSet = AnimatorSet()
                val animators = mutableListOf(pathAnimator, scaleXAnimator, scaleYAnimator, rotationAnimator, alphaAnimator)
                sparkleAnimator?.let { animators.add(it) }

                animatorSet.playTogether(*animators.toTypedArray())

                // Set duration based on file type and add easing
                val baseDuration = if (isImage) 1000L else 800L
                animatorSet.duration = baseDuration + (animationIndex * 50L)
                animatorSet.interpolator = AccelerateDecelerateInterpolator()

                // Add bounce effect at the start
                viewCopy.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        viewCopy.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .withEndAction {
                                // Start main animation after bounce
                                animatorSet.start()
                            }
                    }

                // Handle animation completion
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            overlayView.removeView(viewCopy)
                            onComplete()
                        } catch (e: Exception) {
                            // Ignore cleanup errors
                        }
                    }
                })

                // Add haptic feedback for images
                if (isImage && animationIndex == 0) {
                    HapticManager.strongVibration(context)
                }

            } catch (e: Exception) {
                // Fallback if animation fails
                onComplete()
            }
        }

        private fun createEnhancedViewCopy(sourceView: View, isImage: Boolean): View {
            try {
                // Measure the source view
                val width = sourceView.width
                val height = sourceView.height

                // Create a bitmap of the source view
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                sourceView.draw(canvas)

                // Create an ImageView with the bitmap
                val imageView = ImageView(context)
                imageView.setImageBitmap(bitmap)
                imageView.layoutParams = FrameLayout.LayoutParams(width, height)

                // Add special effects for images
                if (isImage) {
                    // Add subtle shadow and glow effect
                    imageView.elevation = 8f
                    imageView.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                } else {
                    // Add document styling
                    imageView.elevation = 4f
                }

                return imageView
            } catch (e: Exception) {
                // Fallback to simple view copy
                val fallbackView = View(context)
                fallbackView.layoutParams = FrameLayout.LayoutParams(
                    sourceView.width,
                    sourceView.height
                )
                fallbackView.setBackgroundColor(Color.GRAY)
                return fallbackView
            }
        }

        private fun createBeautifulPathAnimation(
            view: View,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            index: Int,
            isImage: Boolean
        ): ObjectAnimator {
            // Create a more complex curved path
            val path = Path()
            path.moveTo(startX, startY)

            // Calculate multiple control points for a beautiful S-curve
            val midX = (startX + endX) / 2
            val controlOffset = if (isImage) 400f else 300f
            val indexOffset = index * 50f

            // First curve control point (higher arc for images)
            val control1X = midX - 200f + indexOffset
            val control1Y = startY - controlOffset - indexOffset

            // Second curve control point
            val control2X = midX + 100f - indexOffset
            val control2Y = endY - 150f + indexOffset

            // Create smooth S-curve path
            path.cubicTo(control1X, control1Y, control2X, control2Y, endX, endY)

            // Create the animator that moves along the path
            return ObjectAnimator.ofFloat(view, View.X, View.Y, path)
        }

        private fun createSparkleEffect(view: View): ObjectAnimator? {
            return try {
                // Create a subtle sparkle effect by modifying the view's properties
                val sparkleAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f, 1f, 0.8f, 1f)
                sparkleAnimator.duration = 500
                sparkleAnimator.repeatCount = 2
                sparkleAnimator
            } catch (e: Exception) {
                null
            }
        }

        private fun isImageFileView(fileView: View): Boolean {
            return try {
                // Check if the view contains an image preview (ShapeableImageView visible)
                val imagePreview = fileView.findViewById<View>(R.id.image_preview)
                val documentIcon = fileView.findViewById<View>(R.id.document_icon)

                val isImage = imagePreview?.visibility == View.VISIBLE &&
                        (documentIcon?.visibility == View.GONE || documentIcon?.visibility == View.INVISIBLE)

                // Also check for camera type files
                val tag = fileView.tag
                val isCameraFile = tag?.toString()?.contains("camera") == true

                Timber.d("File view analysis - Image preview visible: ${imagePreview?.visibility == View.VISIBLE}, " +
                        "Document icon visible: ${documentIcon?.visibility == View.VISIBLE}, " +
                        "Is camera file: $isCameraFile, Final decision: ${isImage || isCameraFile}")

                isImage || isCameraFile
            } catch (e: Exception) {
                Timber.e(e, "Error determining file type: ${e.message}")
                false
            }
        }

        // Debug method to help troubleshoot animation issues
        fun debugFileViewStructure(selectedFilesContainer: LinearLayout) {
            Timber.d("=== FILE VIEW STRUCTURE DEBUG ===")
            Timber.d("Container child count: ${selectedFilesContainer.childCount}")
            Timber.d("Container visibility: ${selectedFilesContainer.visibility}")
            Timber.d("Container dimensions: ${selectedFilesContainer.width}x${selectedFilesContainer.height}")

            for (i in 0 until selectedFilesContainer.childCount) {
                val child = selectedFilesContainer.getChildAt(i)
                Timber.d("Child $i: ${child.javaClass.simpleName}")
                Timber.d("  - Dimensions: ${child.width}x${child.height}")
                Timber.d("  - Visibility: ${child.visibility}")
                Timber.d("  - Tag: ${child.tag}")

                // Try to find key child views
                try {
                    val imagePreview = child.findViewById<View>(R.id.image_preview)
                    val documentIcon = child.findViewById<View>(R.id.document_icon)
                    val fileName = child.findViewById<TextView>(R.id.file_name)

                    Timber.d("  - Image preview: ${imagePreview?.visibility}")
                    Timber.d("  - Document icon: ${documentIcon?.visibility}")
                    Timber.d("  - File name: ${fileName?.text}")
                } catch (e: Exception) {
                    Timber.d("  - Error analyzing child views: ${e.message}")
                }
            }
            Timber.d("=== END DEBUG ===")
        }
    }

    /**
     * Add file to selection without showing OCR options - used for Mistral OCR with images
     */
    private fun addFileToSelectionWithoutOcrOptions(uri: Uri, fileName: String, fileSize: Long, isDocument: Boolean) {
        try {
            // Check if Mistral OCR already has files selected (only one file allowed)
            val isMistralOcr = ModelManager.selectedModel.id == ModelManager.MISTRAL_OCR_ID
            if (isMistralOcr && activity.selectedFiles.isNotEmpty()) {
                Toast.makeText(
                    activity,
                    "Mistral OCR allows only one file at a time. Please remove the current file first.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            // Create selected file
            val selectedFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = isDocument
            )

            // Add to selected files
            activity.selectedFiles.add(selectedFile)
            updateSelectedFilesView()

            // For images, make persistent immediately to prevent URI invalidation
            if (!isDocument && isImageFile(fileName)) {
                Timber.d("Making image file persistent immediately (OCR path): ${fileName}")
                activity.lifecycleScope.launch {
                    try {
                        val persistentStorage = PersistentFileStorage(activity)
                        val persistentFile = selectedFile.toPersistent(persistentStorage)
                        if (persistentFile != null) {
                            // Replace the temp file with persistent version
                            val index = activity.selectedFiles.indexOf(selectedFile)
                            if (index != -1) {
                                activity.selectedFiles[index] = persistentFile
                                withContext(Dispatchers.Main) {
                                    updateSelectedFilesView()
                                }
                                Timber.d("Image made persistent successfully (OCR path): ${fileName}")
                            }
                        } else {
                            Timber.w("Failed to make image persistent (OCR path): ${fileName}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error making image persistent (OCR path): ${e.message}")
                    }
                }
            }

            // Update UI state
            activity.updateButtonVisibilityAndState()
            activity.saveDraftIfNeeded()

            // Provide haptic feedback
            HapticManager.mediumVibration(activity)

            // Hide OCR options panel since this is an image for Mistral OCR
            activity.binding.ocrOptionsPanel.visibility = View.GONE

            Toast.makeText(
                activity,
                "Image selected for Mistral OCR processing",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Timber.e(e, "Error adding file without OCR options")
            Toast.makeText(
                activity,
                "Error selecting file: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Show dialog for unsupported Office file types
     */
    private fun showUnsupportedFileDialog(fileName: String, mimeType: String) {
        val alternatives = SupportedFileTypes.getAlternativesForUnsupportedType(mimeType)
        
        AlertDialog.Builder(activity)
            .setTitle("⚠️ File Type Not Supported")
            .setMessage("""
                📄 File: $fileName
                
                This file type cannot be processed with server-side processing.
                
                $alternatives
                
                📋 FULLY SUPPORTED FORMATS (with server processing):
                • Microsoft Word (.docx)
                • Microsoft Excel (.xlsx) 
                • Microsoft PowerPoint (.pptx)
                • PDF files (.pdf)
                • Text files (.txt)
                • CSV files (.csv)
                • RTF files (.rtf)
                • All image formats
                
                For the best experience, please convert your file to one of these formats.
            """.trimIndent())
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Show Supported Formats") { _, _ ->
                showSupportedFormatsDialog()
            }
            .show()
    }

    /**
     * Show dialog for PDF files with non-supporting AI models
     */
    private fun showPdfNotSupportedDialog(fileName: String, modelName: String) {
        AlertDialog.Builder(activity)
            .setTitle("📄 PDF Not Supported by Current AI Model")
            .setMessage("""
                File: $fileName
                Current Model: $modelName
                
                This AI model cannot process PDF files directly.
                
                💡 SOLUTIONS:
                
                1. 🤖 SWITCH TO PDF-COMPATIBLE MODEL:
                   • GPT-4o, GPT-4 Turbo
                   • Claude 3/3.5 (Opus, Sonnet)
                   • Gemini Pro/1.5/2.0
                   • Perplexity models
                
                2. 📝 CONVERT PDF:
                   • Use Google Drive: PDF → Text
                   • Online tools: PDF to TXT converter
                   • Copy text manually from PDF
                
                3. 📋 PASTE CONTENT:
                   • Open PDF in any viewer
                   • Select and copy text
                   • Paste directly in chat
            """.trimIndent())
            .setPositiveButton("Switch AI Model") { dialog, _ ->
                dialog.dismiss()
                // Guide user to model selection in main UI
                Toast.makeText(activity, "Tap the model name at the top to switch models", Toast.LENGTH_LONG).show()
                // Note: Model selector highlighting could be added here if needed
            }
            .setNeutralButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show supported formats information dialog
     */
    private fun showSupportedFormatsDialog() {
        AlertDialog.Builder(activity)
            .setTitle("📄 Supported File Formats")
            .setMessage(SupportedFileTypes.getSupportedDocumentFormatsDescription())
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Update file upload limits based on subscription status
     */
    fun updateLimits(maxFiles: Int, maxSizeMB: Int) {
        this.maxFiles = maxFiles
        this.maxFileSizeMB = maxSizeMB
        
        Timber.d("File limits updated: maxFiles=$maxFiles, maxSize=${maxSizeMB}MB")
    }
    
    // File limits (default values for free users)
    private var maxFiles = 3
    private var maxFileSizeMB = 5

}
