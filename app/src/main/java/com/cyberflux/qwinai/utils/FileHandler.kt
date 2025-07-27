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
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
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
    private val documentExtractor = DocumentContentExtractor(activity)

    fun openFilePicker(filePickerLauncher: ActivityResultLauncher<Intent>) {
        // Check if adding files would exceed the 5-file limit
        if (activity.selectedFiles.size >= 5) {
            Toast.makeText(
                activity,
                "Maximum 5 files allowed. Please remove some first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Calculate how many more files can be added
        val remainingSlots = 5 - activity.selectedFiles.size

        // Check if current model is an OCR model
        val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)

        Intent(Intent.ACTION_GET_CONTENT).apply {
            if (isOcrModel) {
                // For OCR models, allow both PDF and image files, but PDFs require OCR options
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
            } else {
                // For non-OCR models, only allow images
                type = "image/*"
                putExtra(Intent.EXTRA_MIME_TYPES, SupportedFileTypes.SUPPORTED_IMAGE_TYPES)
            }

            // Only allow multiple selection if we have more than 1 slot left
            // For OCR models with PDFs, always limit to single file
            val allowMultiple = remainingSlots > 1 && !isOcrModel
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            
            // Show user feedback about selection capabilities
            if (allowMultiple) {
                Toast.makeText(
                    activity,
                    "You can select up to $remainingSlots more file(s)",
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
        // Check if adding files would exceed the limit
        if (activity.selectedFiles.size >= 5) {
            Toast.makeText(
                activity,
                "Maximum 5 files allowed. Please remove some first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Calculate how many more files can be added
        val remainingSlots = 5 - activity.selectedFiles.size

        // Check if current model supports documents
        val currentModel = ModelManager.selectedModel
        val needsExtraction = !ModelValidator.hasNativeDocumentSupport(currentModel.id)

        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, SupportedFileTypes.SUPPORTED_DOCUMENT_TYPES)
            // Allow multiple selection if we have more than 1 slot left
            val allowMultiple = remainingSlots > 1
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
    suspend fun processDocumentWithExtractor(uri: Uri): Result<DocumentContentExtractor.ExtractedContent> {
        return documentExtractor.extractContent(uri)
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

            if (isOcrModel) {
                // Handle OCR specifically - show OCR options dialog
                Timber.d("  -> Routing to OCR options display")
                showOcrOptionsDialog(uri, fileName, fileSize)
            } else {
                // CRITICAL FIX: Always route to extraction for all models
                // This ensures content is extracted and can be read by AI models
                Timber.d("  -> Routing to extraction process for all models (native support: $hasNativeSupport)")
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
        
        Timber.d("  Added file to UI, starting extraction...")

        // Find file view to update progress
        val fileView = FileUtil.findFileViewForUri(uri, activity)
        if (fileView == null) {
            Timber.e("Could not find view for document: $uri")
            Toast.makeText(activity, "Error displaying document", Toast.LENGTH_SHORT).show()
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

                // Process document using extractor
                val documentExtractor = DocumentContentExtractor(activity)
                val result = documentExtractor.processDocumentForModel(uri)

                if (result.isSuccess) {
                    val extractedContent = result.getOrNull()
                    
                    Timber.d("✅ Document extraction SUCCESS for: $fileName")
                    if (extractedContent != null) {
                        Timber.d("  Content length: ${extractedContent.textContent.length} chars")
                        Timber.d("  Token count: ${extractedContent.tokenCount}")
                        Timber.d("  Content preview: ${extractedContent.textContent.take(100)}...")
                    } else {
                        Timber.w("  ⚠️ Extracted content is NULL despite success!")
                    }

                    withContext(Dispatchers.Main) {
                        // Update progress
                        progressTracker.updateProgress(
                            90,
                            "Document ready",
                            FileProgressTracker.ProcessingStage.COMPLETE
                        )

                        // CRITICAL FIX: Use the URI string consistently for the extractedContentId
                        val uriString = uri.toString()

                        // Update file status
                        val updatedFile = tempFile.copy(
                            isExtracting = false,
                            isExtracted = true,
                            extractedContentId = uriString  // Store URI string as the content ID
                        )

                        // Debug what we're storing
                        Timber.d("📝 Document processed - storing with ID: $uriString")
                        val isCached = DocumentContentExtractor.getCachedContent(uriString) != null
                        Timber.d("  Content cached: $isCached")
                        if (!isCached) {
                            Timber.e("  ❌ CRITICAL: Content not found in cache after processing!")
                        }

                        // Replace in selected files list
                        val index = activity.selectedFiles.indexOf(tempFile)
                        if (index != -1) {
                            activity.selectedFiles[index] = updatedFile
                        }

                        // Update UI
                        activity.fileHandler.updateSelectedFilesView()
                        progressTracker.hideProgress()

                        // Provide feedback
                        Toast.makeText(
                            activity,
                            "Document processed successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e("❌ Document extraction FAILED for: $fileName")
                    Timber.e("  Error: ${error?.message}")
                    Timber.e("  Exception: ${error?.toString()}")
                    
                    withContext(Dispatchers.Main) {
                        progressTracker.updateProgress(
                            100,
                            "Error: ${error?.message}",
                            FileProgressTracker.ProcessingStage.ERROR
                        )

                        Toast.makeText(
                            activity,
                            "Error processing document: ${error?.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Remove file from selected files on error
                        activity.selectedFiles.remove(tempFile)
                        activity.fileHandler.updateSelectedFilesView()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error extracting document content: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressTracker.updateProgress(
                        100,
                        "Error processing document",
                        FileProgressTracker.ProcessingStage.ERROR
                    )

                    Toast.makeText(
                        activity,
                        "Error processing document: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Remove file from selected files on error
                    activity.selectedFiles.remove(tempFile)
                    activity.fileHandler.updateSelectedFilesView()
                }
            }
        }
    }
    /**
     * Handle image selection for OCR models - show options instead of auto-sending
     */
    private fun handleImageWithOcrOptions(uri: Uri, fileName: String, fileSize: Long) {
        try {
            // Add image to selected files for display (but don't auto-send)
            val selectedFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = false
            )
            
            activity.selectedFiles.add(selectedFile)
            updateSelectedFilesView()
            
            // OCR options are now always visible in the main UI
            
            // Provide feedback that image is ready for OCR processing
            Toast.makeText(
                activity,
                "Image selected. Configure OCR options and click Send to process.",
                Toast.LENGTH_LONG
            ).show()
            
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
                    val conversationId = if (activity is MainActivity) {
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
            // Check if we already have a PDF selected (only one allowed)
            val existingPdf = activity.selectedFiles.find { it.name.endsWith(".pdf", ignoreCase = true) }
            if (existingPdf != null) {
                Toast.makeText(
                    activity,
                    "Only one PDF file can be selected at a time. Please remove the existing PDF first.",
                    Toast.LENGTH_LONG
                ).show()
                return
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
            
            // OCR options are now always visible in the main UI
            
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
                val result = documentExtractor.extractContent(uri)

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()

                    if (result.isSuccess) {
                        val extractedContent = result.getOrNull()
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

            // For OCR models with images: show OCR options instead of auto-sending
            if (isOcrModel && isImageFile) {
                handleImageWithOcrOptions(uri, fileName, fileSize)
                return
            }

            // For OCR models with PDFs: show file and OCR options, don't auto-send
            if (isOcrModel && isPdfFile) {
                handlePdfWithOcrOptions(uri, fileName, fileSize)
                return
            }

            // Create a temporary SelectedFile with processing state
            val tempFile = FileUtil.FileUtil.SelectedFile(
                uri = uri,
                name = fileName,
                size = fileSize,
                isDocument = isDocument,
                isExtracting = isDocument, // Set to true for documents that need processing
                isExtracted = false
            )

            // IMPORTANT: Show file in UI immediately before processing
            activity.selectedFiles.add(tempFile)
            updateSelectedFilesView()
            
            // Update submit button state immediately
            updateSubmitButtonState()
            
            // Save draft when files are added
            activity.saveDraftIfNeeded()

            // Vibrate to provide feedback that the file was added
            HapticManager.mediumVibration(activity)

            // For documents, extract content immediately to ensure it's cached
            if (isDocument) {
                Timber.d("Document detected, starting immediate content extraction")

                // Find file view to show extraction progress
                val fileView = FileUtil.findFileViewForUri(uri, activity)
                if (fileView != null) {
                    // Create progress tracker and initialize
                    val progressTracker = FileProgressTracker()
                    progressTracker.initWithImageFileItem(fileView)

                    // Show progress
                    progressTracker.showProgress()
                    progressTracker.observeProgress(activity)

                    // Process extraction in background
                    activity.lifecycleScope.launch {
                        try {
                            // Update progress
                            progressTracker.updateProgress(
                                10,
                                "Reading document...",
                                FileProgressTracker.ProcessingStage.READING_FILE
                            )

                            // Extract content
                            val documentExtractor = DocumentContentExtractor(activity)
                            val result = documentExtractor.processDocumentForModel(uri)

                            if (result.isSuccess) {
                                val extractedContent = result.getOrNull()

                                withContext(Dispatchers.Main) {
                                    // Update progress to complete
                                    progressTracker.updateProgress(
                                        100,
                                        "Document ready",
                                        FileProgressTracker.ProcessingStage.COMPLETE
                                    )

                                    // Use the URI string consistently as the extracted content ID
                                    val uriString = uri.toString()

                                    // Update file object
                                    val updatedFile = tempFile.copy(
                                        isExtracting = false,
                                        isExtracted = true,
                                        extractedContentId = uriString  // Store URI string as the content ID
                                    )

                                    // Replace in selected files list
                                    val index = activity.selectedFiles.indexOf(tempFile)
                                    if (index != -1) {
                                        activity.selectedFiles[index] = updatedFile
                                    }

                                    // Update UI
                                    updateSelectedFilesView()
                                    
                                    // Update submit button state now that processing is complete
                                    updateSubmitButtonState()

                                    // Hide progress after a short delay
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        progressTracker.hideProgress()
                                    }, 500)

                                    // Log successful extraction
                                    val contentSize = extractedContent?.textContent?.length ?: 0
                                    Timber.d("Document extraction complete: $fileName, content size: $contentSize chars")

                                    // Verify content was cached properly
                                    val cached = DocumentContentExtractor.getCachedContent(uriString)
                                    Timber.d("Cache verification: ${cached != null}, content size: ${cached?.textContent?.length}")
                                }
                            } else {
                                val error = result.exceptionOrNull()
                                Timber.e(error, "Error extracting document content: ${error?.message}")

                                withContext(Dispatchers.Main) {
                                    progressTracker.updateProgress(
                                        100,
                                        "Error: ${error?.message}",
                                        FileProgressTracker.ProcessingStage.ERROR
                                    )

                                    // Show error but don't remove file - we'll still send metadata
                                    Toast.makeText(
                                        activity,
                                        "Warning: Could not extract text from document.",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    // Update file as not extracted
                                    val updatedFile = tempFile.copy(
                                        isExtracting = false,
                                        isExtracted = false
                                    )

                                    // Replace in selected files list
                                    val index = activity.selectedFiles.indexOf(tempFile)
                                    if (index != -1) {
                                        activity.selectedFiles[index] = updatedFile
                                    }

                                    // Update UI
                                    updateSelectedFilesView()
                                    
                                    // Update submit button state after error
                                    updateSubmitButtonState()

                                    // Hide progress after a short delay
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        progressTracker.hideProgress()
                                    }, 500)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error in document extraction: ${e.message}")

                            withContext(Dispatchers.Main) {
                                progressTracker.updateProgress(
                                    100,
                                    "Error processing",
                                    FileProgressTracker.ProcessingStage.ERROR
                                )

                                // Hide progress after a short delay
                                Handler(Looper.getMainLooper()).postDelayed({
                                    progressTracker.hideProgress()
                                }, 500)
                            }
                        }
                    }
                } else {
                    // No file view found, just extract in background without UI updates
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val documentExtractor = DocumentContentExtractor(activity)
                            val result = documentExtractor.processDocumentForModel(uri)

                            if (result.isSuccess) {
                                // Get cache key (URI string)
                                val uriString = uri.toString()

                                // Update file status on main thread
                                withContext(Dispatchers.Main) {
                                    // Update file as extracted
                                    val updatedFile = tempFile.copy(
                                        isExtracting = false,
                                        isExtracted = true,
                                        extractedContentId = uriString
                                    )

                                    // Replace in selected files list
                                    val index = activity.selectedFiles.indexOf(tempFile)
                                    if (index != -1) {
                                        activity.selectedFiles[index] = updatedFile
                                    }

                                    // Update UI
                                    updateSelectedFilesView()
                                }

                                // Log success
                                Timber.d("Background document extraction complete: $fileName")
                            } else {
                                Timber.e("Background extraction failed: ${result.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error in background document extraction: ${e.message}")
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
                    activity.contentResolver.openInputStream(file.uri)?.use { true } ?: false
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
            // Use activity as the context
            val gson = GsonFactory.createGson(activity)
            gson.toJson(files)
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
            // Use activity as the context
            val gson = GsonFactory.createGson(activity)
            val type = object : TypeToken<List<FileUtil.FileUtil.SelectedFile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
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
        val previewContainer = fileView.findViewById<FrameLayout>(R.id.previewContainer)
        val imagePreview = fileView.findViewById<ShapeableImageView>(R.id.image_preview)
        val documentIcon = fileView.findViewById<ImageView>(R.id.document_icon)
        val progressLayout = fileView.findViewById<View>(R.id.progressLayout)

        fileName.text = file.name
        fileType.text = FileUtil.getFileType(file.name)
        fileSize.text = FileUtil.formatFileSize(file.size)

        val isImage = isImageFile(file.name)
        val isPdf = file.name.endsWith(".pdf", ignoreCase = true)

        if (isImage) {
            imagePreview.visibility = View.VISIBLE
            documentIcon.visibility = View.GONE
            progressLayout.visibility = View.GONE

            Glide.with(activity)
                .load(file.uri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .into(imagePreview)

            imagePreview.setOnClickListener { openImage(file.uri) }
        } else if (file.isDocument) {
            // Show document icon
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
            documentIcon.setImageResource(iconDrawable)

            // Show extraction status if applicable
            val fileInfoLayout = fileView.findViewById<LinearLayout>(R.id.file_info_layout)
            if (file.isExtracting) {
                fileInfoLayout.visibility = View.GONE
                progressLayout.visibility = View.VISIBLE
                // The progress is handled by FileProgressTracker
            } else {
                progressLayout.visibility = View.GONE
                fileInfoLayout.visibility = View.VISIBLE
                // Document is processed - show normal file info
            }

            documentIcon.setOnClickListener {
                if (!file.isExtracting && !file.isExtracted) {
                    openFile(file.uri, file.name)
                }
            }
        } else {
            // Handle other document types
            imagePreview.visibility = View.GONE
            documentIcon.visibility = View.VISIBLE
            progressLayout.visibility = View.GONE

            val extension = file.name.substringAfterLast('.', "").lowercase()
            val iconDrawable = when (extension) {
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx" -> R.drawable.ic_word
                "xls", "xlsx" -> R.drawable.ic_excel
                "ppt", "pptx" -> R.drawable.ic_powerpoint
                "txt" -> R.drawable.ic_text
                else -> R.drawable.ic_document
            }
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
            updateSubmitButtonState()
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

        return fileView
    }

    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
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
        
        // Button should be enabled only if:
        // 1. There's text or files to send
        // 2. No files are currently being processed
        // 3. Not currently generating a response
        val shouldEnable = (hasText || hasFiles) && !isProcessing && !activity.isGenerating
        
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

}
