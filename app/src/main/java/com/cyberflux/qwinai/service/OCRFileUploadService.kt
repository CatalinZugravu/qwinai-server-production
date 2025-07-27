package com.cyberflux.qwinai.service

import android.content.Context
import android.net.Uri
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.DocumentInput
import com.cyberflux.qwinai.network.OCRRequest
import com.cyberflux.qwinai.network.OCRResponse
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.utils.FileProgressTracker
import com.cyberflux.qwinai.utils.ImageUploadUtils
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.PdfFileHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.http.Body
import retrofit2.http.POST
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * OCR options for processing documents
 */
data class OCROptions(
    val pages: String? = null,
    val includeImageBase64: Boolean? = null,
    val imageLimit: Int? = null,
    val imageMinSize: Int? = null
)

/**
 * Additional API service interface for OCR via /v1/ocr endpoint
 * Mistral OCR uses the dedicated /v1/ocr endpoint, not chat completions
 */
interface OCRFileUploadService {
    @POST("v1/ocr")
    suspend fun processDocument(@Body request: OCRRequest): retrofit2.Response<OCRResponse>
}

/**
 * Service for handling OCR processing of PDF documents
 */
class OCRService(private val context: Context) {

    private val pdfFileHandler = PdfFileHandler(context)

    /**
     * Process a PDF file for OCR and generate AI message with extracted text
     *
     * @param uri URI of the PDF file
     * @param conversationId Conversation ID
     * @param userMessageId Parent user message ID
     * @param ocrOptions OCR processing options
     * @param progressCallback Callback for progress updates
     * @param completionCallback Callback for completion
     */
    suspend fun processPdfForOcr(
        uri: Uri,
        conversationId: String,
        userMessageId: String,
        ocrOptions: OCROptions = OCROptions(),
        progressCallback: (ChatMessage) -> Unit,
        completionCallback: (ChatMessage) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Create AI message for OCR processing
                val aiMessageId = UUID.randomUUID().toString()
                val groupId = UUID.randomUUID().toString()

                // Initial processing message using standard AI layout
                val processingMessage = ChatMessage(
                    id = aiMessageId,
                    conversationId = conversationId,
                    message = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isGenerating = true,
                    showButtons = false,
                    modelId = "mistral/mistral-ocr-latest",
                    aiModel = "Mistral OCR",
                    aiGroupId = groupId,
                    parentMessageId = userMessageId,
                    isOcrDocument = false, // Use standard AI layout
                    initialIndicatorText = "Processing pdf file",
                    initialIndicatorColor = android.graphics.Color.parseColor("#757575")
                )

                // Send initial progress
                withContext(Dispatchers.Main) {
                    progressCallback(processingMessage)
                }

                // Step 2: Process the PDF file
                val tracker = FileProgressTracker()

                // Create collector for progress updates using standard AI format
                MainScope().launch {
                    tracker.progressFlow.collect { (progress, message, stage) ->
                        // Keep the same indicator text throughout processing
                        val updatedMessage = processingMessage.copy(
                            message = "",
                            initialIndicatorText = "Processing pdf file",
                            initialIndicatorColor = android.graphics.Color.parseColor("#757575")
                        )
                        progressCallback(updatedMessage)
                    }
                }

                val pdfResult = pdfFileHandler.processPdfForOcr(uri, ModelManager.MISTRAL_OCR_ID, tracker)

                if (pdfResult.isFailure) {
                    val errorMessage = pdfResult.exceptionOrNull()?.message ?: "Unknown error processing PDF"
                    throw Exception(errorMessage)
                }

                val processedPdf = pdfResult.getOrNull() ?: throw Exception("PDF processing failed")

                // Step 3: Update progress
                tracker.updateProgress(
                    60,
                    "Uploading PDF and extracting text...",
                    FileProgressTracker.ProcessingStage.ENCODING
                )

                // Step 4: Use Mistral OCR API for text extraction
                val ocrResult = processWithMistralOCR(processedPdf.file, tracker, ocrOptions)

                // Step 5: Process OCR results
                val extractedText = ocrResult.pages.joinToString("\n\n") { page ->
                    "**Page ${page.index + 1}:**\n\n${page.markdown}"
                }
                val extractedImages = ocrResult.pages.flatMap { it.images }

                // Create the AI response content (only extracted text, no additional info)
                val responseContent = buildString {
                    if (!extractedText.isNullOrBlank()) {
                        append(extractedText)
                    } else {
                        append("No text content was extracted from this document.")
                    }
                    
                    // Include extracted images as base64 if available
                    if (!extractedImages.isNullOrEmpty()) {
                        append("\n\n")
                        extractedImages.forEachIndexed { index, image ->
                            if (image.image_base64 != null) {
                                append("![${image.id}](${image.image_base64})\n\n")
                            }
                        }
                    }
                }

                // Final success message using standard AI message format
                val successMessage = processingMessage.copy(
                    message = responseContent,
                    isGenerating = false,
                    showButtons = true,
                    isOcrDocument = false // Change to use standard AI layout
                )

                withContext(Dispatchers.Main) {
                    completionCallback(successMessage)
                }

                // Clean up temp file
                processedPdf.file.delete()

            } catch (e: Exception) {
                Timber.e(e, "OCR processing error: ${e.message}")

                // Create error message using standard AI format
                val errorMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    message = "❌ **OCR Processing Failed**\n\n" +
                            "I encountered an error while processing your document:\n\n" +
                            "**Error:** ${e.message}\n\n" +
                            "Please try uploading your document again or check if the file is valid.",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isGenerating = false,
                    showButtons = true,
                    modelId = "mistral/mistral-ocr-latest",
                    aiModel = "Mistral OCR",
                    parentMessageId = userMessageId,
                    isOcrDocument = false, // Use standard AI layout
                    error = true
                )

                withContext(Dispatchers.Main) {
                    completionCallback(errorMessage)
                }
            }
        }
    }

    /**
     * Process document using actual Mistral OCR API
     */
    private suspend fun processWithMistralOCR(file: File, tracker: FileProgressTracker, ocrOptions: OCROptions): OCRResponse = withContext(Dispatchers.IO) {
        try {
            tracker.updateProgress(
                70,
                "Converting file for OCR processing...",
                FileProgressTracker.ProcessingStage.ENCODING
            )

            // Convert file to base64 data URL
            val fileUri = Uri.fromFile(file)
            val dataUrl = when {
                file.name.endsWith(".pdf", ignoreCase = true) -> {
                    // For PDF files, keep as PDF for proper OCR
                    convertPdfToBase64DataUrl(context, fileUri, convertToImage = false)
                }
                file.name.matches(Regex(".*\\.(jpg|jpeg|png|gif|bmp|webp)$", RegexOption.IGNORE_CASE)) -> {
                    // For images, use existing ImageUploadUtils
                    ImageUploadUtils.uploadImage(context, fileUri)
                }
                else -> {
                    throw Exception("Unsupported file format. Please use PDF or image files.")
                }
            }

            if (dataUrl == null) {
                throw Exception("Failed to convert file to base64")
            }

            tracker.updateProgress(
                80,
                "Processing with Mistral OCR...",
                FileProgressTracker.ProcessingStage.FINALIZING
            )

            // Get OCR API service
            val ocrService = RetrofitInstance.getApiService(
                RetrofitInstance.ApiServiceType.AIMLAPI,
                OCRFileUploadService::class.java
            )

            // Determine document type based on file extension
            val documentInput = if (file.name.endsWith(".pdf", ignoreCase = true)) {
                DocumentInput.DocumentUrl(
                    type = "document_url",
                    document_url = dataUrl
                )
            } else {
                DocumentInput.ImageUrl(
                    type = "image_url", 
                    image_url = dataUrl
                )
            }

            // Create OCR request
            val request = OCRRequest(
                model = "mistral/mistral-ocr-latest",
                document = documentInput,
                pages = ocrOptions.pages,
                include_image_base64 = ocrOptions.includeImageBase64,
                image_limit = ocrOptions.imageLimit,
                image_min_size = ocrOptions.imageMinSize
            )

            Timber.d("Mistral OCR Request: model=${request.model}")
            Timber.d("Mistral OCR Request dataUrl length: ${dataUrl.length}")

            // Make API call
            val response = ocrService.processDocument(request)

            tracker.updateProgress(
                90,
                "Processing OCR response...",
                FileProgressTracker.ProcessingStage.FINALIZING
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Timber.e("OCR API Error: ${response.code()} - $errorBody")
                throw Exception("OCR processing failed: ${response.message()} - $errorBody")
            }

            val ocrResponse = response.body()
                ?: throw Exception("Empty OCR response")

            Timber.d("OCR Success: ${ocrResponse.pages.size} pages processed")

            tracker.updateProgress(
                100,
                "OCR processing completed",
                FileProgressTracker.ProcessingStage.COMPLETE
            )

            return@withContext ocrResponse

        } catch (e: Exception) {
            Timber.e(e, "Error in processWithMistralOCR")
            tracker.updateProgress(
                100,
                "OCR processing failed: ${e.message}",
                FileProgressTracker.ProcessingStage.ERROR
            )
            throw e
        }
    }

    /**
     * Process PDF/Image for OCR using vision model (GPT-4o, Claude, etc.)
     * More reliable than Google Document AI and doesn't require special permissions
     */
    private suspend fun processWithVisionModel(file: File, tracker: FileProgressTracker, ocrOptions: OCROptions): OCRResponse = withContext(Dispatchers.IO) {
        try {
            tracker.updateProgress(
                70,
                "Converting file to base64...",
                FileProgressTracker.ProcessingStage.ENCODING
            )

            // Convert file to base64 data URL for vision model
            val fileUri = Uri.fromFile(file)
            val dataUrl = when {
                file.name.endsWith(".pdf", ignoreCase = true) -> {
                    // For PDF files, convert to image for vision model
                    convertPdfToBase64DataUrl(context, fileUri, convertToImage = true)
                }
                file.name.matches(Regex(".*\\.(jpg|jpeg|png|gif|bmp|webp)$", RegexOption.IGNORE_CASE)) -> {
                    // For images, use existing ImageUploadUtils
                    ImageUploadUtils.uploadImage(context, fileUri)
                }
                else -> {
                    throw Exception("Unsupported file format. Please use PDF or image files.")
                }
            }

            if (dataUrl == null) {
                throw Exception("Failed to convert file to base64")
            }

            tracker.updateProgress(
                80,
                "Analyzing document with GPT-4o...",
                FileProgressTracker.ProcessingStage.FINALIZING
            )

            // Get chat API service for vision model
            val chatService = RetrofitInstance.getApiService(
                RetrofitInstance.ApiServiceType.AIMLAPI,
                com.cyberflux.qwinai.network.AimlApiService::class.java
            )

            // Create OCR prompt with options
            val prompt = buildString {
                append("Please extract all text from this document image. ")
                append("Provide the complete text content exactly as it appears, preserving formatting and structure. ")
                append("If there are multiple columns or sections, maintain their organization. ")
                
                if (!ocrOptions.pages.isNullOrBlank()) {
                    append("Focus on pages: ${ocrOptions.pages}. ")
                }
                
                append("Do not add any commentary or summary - just return the extracted text.")
            }

            // Create vision request using standard chat API
            val request = com.cyberflux.qwinai.network.AimlApiRequest(
                model = "gpt-4o", // Best vision model for OCR
                messages = listOf(
                    com.cyberflux.qwinai.network.AimlApiRequest.Message(
                        role = "user",
                        content = listOf(
                            com.cyberflux.qwinai.network.AimlApiRequest.ContentPart(
                                type = "text",
                                text = prompt
                            ),
                            com.cyberflux.qwinai.network.AimlApiRequest.ContentPart(
                                type = "image_url",
                                imageUrl = com.cyberflux.qwinai.network.AimlApiRequest.ImageUrl(url = dataUrl)
                            )
                        )
                    )
                ),
                maxTokens = 4000,
                temperature = 0.0, // Low temperature for accurate extraction
                topA = null,
                parallelToolCalls = null,
                minP = null,
                useWebSearch = false
            )

            Timber.d("Vision OCR Request: model=${request.model}")
            Timber.d("Vision OCR Request dataUrl length: ${dataUrl.length}")

            // Make the API call
            val response = chatService.sendMessage(request).execute()

            if (response.isSuccessful) {
                tracker.updateProgress(
                    95,
                    "Processing OCR results...",
                    FileProgressTracker.ProcessingStage.FINALIZING
                )

                val chatResponse = response.body() ?: throw Exception("Empty response from vision API")
                
                // Extract text from chat response
                val extractedText = chatResponse.choices?.firstOrNull()?.message?.content ?: ""
                
                Timber.d("Vision OCR Response: text length=${extractedText.length}")
                Timber.d("Vision OCR Response text preview: ${extractedText.take(100)}")

                // Convert to OCR response format
                return@withContext OCRResponse(
                    pages = listOf(
                        com.cyberflux.qwinai.network.OCRPage(
                            index = 0,
                            markdown = extractedText,
                            images = emptyList(),
                            dimensions = com.cyberflux.qwinai.network.OCRDimensions(
                                dpi = 200,
                                height = 0,
                                width = 0
                            )
                        )
                    ),
                    model = "gpt-4o-vision",
                    usage_info = com.cyberflux.qwinai.network.OCRUsageInfo(
                        pages_processed = 1,
                        doc_size_bytes = 0
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Timber.e("Vision OCR API error (${response.code()}): $errorBody")
                throw Exception("Vision OCR API error (${response.code()}): $errorBody")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing file with vision model: ${e.message}")
            throw e
        }
    }

    /**
     * Convert PDF to base64 data URL - either as raw PDF or as image
     */
    private suspend fun convertPdfToBase64DataUrl(context: Context, pdfUri: Uri, convertToImage: Boolean = true): String? = withContext(Dispatchers.IO) {
        try {
            if (convertToImage) {
                // Use Android's PdfRenderer to convert first page to bitmap
                val fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                    ?: return@withContext null
                
                val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                val page = pdfRenderer.openPage(0) // First page
                
                // Create bitmap from PDF page
                val bitmap = android.graphics.Bitmap.createBitmap(
                    page.width, page.height, 
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Convert bitmap to base64 data URL (reuse image upload logic)
                val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
                val byteArray = byteArrayOutputStream.toByteArray()
                
                val base64String = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64String"
                
                // Clean up
                page.close()
                pdfRenderer.close()
                fileDescriptor.close()
                bitmap.recycle()
                byteArrayOutputStream.close()
                
                return@withContext dataUrl
            } else {
                // Convert entire PDF to base64 (preserve text content for OCR)
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: return@withContext null
                
                val bytes = inputStream.readBytes()
                inputStream.close()
                
                val base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val dataUrl = "data:application/pdf;base64,$base64String"
                
                return@withContext dataUrl
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error converting PDF to base64: ${e.message}")
            return@withContext null
        }
    }



}