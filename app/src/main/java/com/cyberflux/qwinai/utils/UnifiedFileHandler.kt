package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.cyberflux.qwinai.network.AimlApiRequest
import com.cyberflux.qwinai.network.FileProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Handler for file processing for AI models.
 * Enhanced with fixes for disappearing images.
 */
class UnifiedFileHandler(private val context: Context) {
    private val aiFileService = AIFileService(context)

    /**
     * Process a file for a specific AI model - ENHANCED WITH SERVER-SIDE PROCESSING
     * Complete pipeline from URI to formatted ContentItem with advanced file support
     */
    suspend fun processFileForModel(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker? = null
    ): Result<ProcessedFileResult> = withContext(Dispatchers.IO) {
        try {
            Timber.d("🚀 Processing file for model: $modelId with server-side processing")

            // Get file metadata
            val mimeType = FileUtil.getMimeType(context, uri)
            val fileName = FileUtil.getFileName(context, uri)
            val fileSize = FileUtil.getFileSize(context, uri)

            // Log file details
            Timber.d("📄 File details: name=$fileName, type=$mimeType, size=${fileSize/1024}KB")

            tracker?.updateProgress(
                10,
                "Analyzing file type...",
                FileProgressTracker.ProcessingStage.INITIALIZING
            )

            // Check file size limit (50MB server limit)
            val maxFileSizeBytes = 50 * 1024 * 1024 // 50MB server limit
            if (fileSize > maxFileSizeBytes) {
                return@withContext Result.failure(
                    IOException("File size (${FileUtil.formatFileSize(fileSize)}) exceeds 50MB server limit")
                )
            }

            // Determine file type for processing - expanded with server-side support
            val isImage = mimeType.startsWith("image/")
            
            // Enhanced document detection with alternative MIME types and file extensions
            val isDocument = isDocumentFile(mimeType, fileName)
            
            // Log detected MIME type for debugging
            Timber.d("📋 File analysis: name=$fileName, mimeType=$mimeType, isImage=$isImage, isDocument=$isDocument")

            when {
                isImage -> {
                    // Process images locally (as before) - no server needed for images
                    tracker?.updateProgress(
                        50,
                        "Processing image...",
                        FileProgressTracker.ProcessingStage.PROCESSING_PAGES
                    )

                    val contentItem = processImageWithRetry(uri, modelId, tracker)
                    return@withContext Result.success(
                        ProcessedFileResult(
                            contentItem = contentItem.getOrThrow(),
                            fileType = FileType.IMAGE
                        )
                    )
                }
                
                isDocument -> {
                    // Try server-side document processing first, fallback to local processing
                    tracker?.updateProgress(
                        30,
                        "Trying server processing...",
                        FileProgressTracker.ProcessingStage.PROCESSING_PAGES
                    )

                    // Convert URI to File
                    val tempFile = uriToTempFile(uri, fileName ?: "document")
                    
                    try {
                        // Try server processing first
                        try {
                            val fileProcessingService = FileProcessingService.getInstance(context)
                            val processingResult = fileProcessingService.processFile(
                                file = tempFile,
                                aiModel = modelId,
                                maxTokensPerChunk = 6000
                            )

                            tracker?.updateProgress(
                                70,
                                "Server processing complete, analyzing content...",
                                FileProgressTracker.ProcessingStage.PROCESSING_PAGES
                            )

                            // Get optimal chunks for this model
                            val optimalChunks = fileProcessingService.getOptimalChunksForModel(
                                processingResult, modelId
                            )

                            if (optimalChunks.isEmpty()) {
                                Timber.w("⚠️ No suitable chunks found for model $modelId")
                                return@withContext Result.failure(
                                    IOException("Document is too large for model $modelId context window")
                                )
                            }

                            tracker?.updateProgress(
                                90,
                                "Creating AI-ready content...",
                                FileProgressTracker.ProcessingStage.FINALIZING
                            )

                            // Create content for AI model
                            val documentText = if (optimalChunks.size == 1) {
                                // Single chunk - use directly
                                optimalChunks[0].text
                            } else {
                                // Multiple chunks - create structured content
                                buildString {
                                    append("📄 Document: ${processingResult.originalFileName}\n")
                                    append("📊 ${optimalChunks.size} sections, ${processingResult.tokenAnalysis.totalTokens} tokens total\n")
                                    append("💰 Estimated cost: ${processingResult.tokenAnalysis.estimatedCost}\n\n")
                                    
                                    optimalChunks.forEachIndexed { index, chunk ->
                                        if (optimalChunks.size > 1) {
                                            append("=== Section ${index + 1}/${optimalChunks.size} ===\n")
                                        }
                                        append(chunk.text)
                                        append("\n\n")
                                    }
                                }
                            }

                            // Create content item
                            val contentItem = AimlApiRequest.ContentPart(
                                type = "text",
                                text = documentText
                            )

                            tracker?.updateProgress(
                                100,
                                "Document processed successfully! ${processingResult.tokenAnalysis.totalTokens} tokens",
                                FileProgressTracker.ProcessingStage.COMPLETE
                            )

                            // Log processing summary
                            Timber.d("✅ Server document processing successful: ${processingResult.originalFileName}")
                            Timber.d("   📊 Total tokens: ${processingResult.tokenAnalysis.totalTokens}")
                            Timber.d("   💰 Estimated cost: ${processingResult.tokenAnalysis.estimatedCost}")
                            Timber.d("   📑 Chunks used: ${optimalChunks.size}/${processingResult.chunks.size}")

                            return@withContext Result.success(
                                ProcessedFileResult(
                                    contentItem = contentItem,
                                    fileType = FileType.DOCUMENT
                                )
                            )

                        } catch (serverException: Exception) {
                            // Server processing failed, try local fallback
                            Timber.w("⚠️ Server processing failed: ${serverException.message}")
                            Timber.d("🔄 Attempting local document processing fallback...")
                            
                            tracker?.updateProgress(
                                50,
                                "Server unavailable, trying local processing...",
                                FileProgressTracker.ProcessingStage.PROCESSING_PAGES
                            )

                            // Check if this is a PDF and we can process it locally
                            if (mimeType == "application/pdf") {
                                Timber.d("📄 Attempting local PDF processing")
                                
                                val localResult = processPdfWithRetry(uri, modelId, tracker, fileName)
                                if (localResult.isSuccess) {
                                    Timber.d("✅ Local PDF processing successful")
                                    return@withContext Result.success(
                                        ProcessedFileResult(
                                            contentItem = localResult.getOrThrow(),
                                            fileType = FileType.PDF
                                        )
                                    )
                                } else {
                                    Timber.w("❌ Local PDF processing also failed: ${localResult.exceptionOrNull()?.message}")
                                }
                            }

                            // If we get here, both server and local processing failed
                            val errorMessage = when {
                                mimeType == "application/pdf" -> "Both server and local PDF processing failed. Server error: ${serverException.message}"
                                else -> "Server processing failed and no local fallback available for $mimeType files. Please ensure the server is deployed and running. Server error: ${serverException.message}"
                            }
                            
                            return@withContext Result.failure(IOException(errorMessage))
                        }

                    } finally {
                        // Clean up temp file
                        try {
                            tempFile.delete()
                        } catch (e: Exception) {
                            Timber.w("Failed to delete temp file: ${e.message}")
                        }
                    }
                }
                
                else -> {
                    // Unsupported file type
                    val fileExtension = fileName?.substringAfterLast('.', "")?.lowercase() ?: "unknown"
                    return@withContext Result.failure(
                        UnsupportedOperationException(
                            "File type not supported.\n" +
                            "• Detected: $mimeType (.$fileExtension)\n" +
                            "• Supported Images: JPG, PNG, WebP, GIF\n" +
                            "• Supported Documents: PDF, DOCX, DOC, XLSX, XLS, PPTX, PPT, TXT, CSV, RTF\n" +
                            "• Note: Server processing required for most document types"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error processing file: ${e.message}")
            tracker?.updateProgress(
                100,
                "Error: ${e.message}",
                FileProgressTracker.ProcessingStage.ERROR
            )
            return@withContext Result.failure(e)
        }
    }

    /**
     * Enhanced document detection using both MIME type and file extension
     */
    private fun isDocumentFile(mimeType: String, fileName: String?): Boolean {
        // Primary MIME type detection
        val supportedMimeTypes = listOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",      // XLSX
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // PPTX
            "text/plain",  // TXT
            "text/csv",    // CSV
            "application/rtf", // RTF
            // Alternative MIME types that sometimes get detected
            "application/msword", // Old DOC format
            "application/vnd.ms-excel", // Old XLS format
            "application/vnd.ms-powerpoint", // Old PPT format
            "application/zip", // Sometimes Office files are detected as ZIP
            "application/octet-stream" // Generic binary, check by extension
        )
        
        // Check by MIME type first
        if (supportedMimeTypes.contains(mimeType)) {
            // If it's ZIP or octet-stream, we need to verify by extension
            if (mimeType == "application/zip" || mimeType == "application/octet-stream") {
                return isDocumentByExtension(fileName)
            }
            return true
        }
        
        // Fallback: check by file extension if MIME type detection failed
        return isDocumentByExtension(fileName)
    }
    
    /**
     * Check if file is a document based on file extension
     */
    private fun isDocumentByExtension(fileName: String?): Boolean {
        if (fileName == null) return false
        
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val supportedExtensions = setOf(
            "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", 
            "txt", "csv", "rtf", "odt", "ods", "odp"
        )
        
        return supportedExtensions.contains(extension)
    }

    /**
     * Convert URI to temporary file for server processing
     */
    private suspend fun uriToTempFile(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}_$fileName")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to read file from URI")
        
        tempFile
    }

    /**
     * Process PDF with retry mechanism for better reliability
     */
    private suspend fun processPdfWithRetry(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker?,
        fileName: String?,
        maxRetries: Int = 2
    ): Result<AimlApiRequest.ContentPart> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                Timber.d("Retry attempt $attempt for PDF processing")
                tracker?.updateProgress(
                    30 + (attempt * 10),
                    "Retrying PDF processing (attempt $attempt)...",
                    FileProgressTracker.ProcessingStage.ENCODING
                )
                delay(1000)  // Wait before retrying
            }

            try {
                // Call the process method
                return@withContext processPdf(uri, modelId, tracker, fileName)
            } catch (e: Exception) {
                lastException = e
                Timber.e(e, "Error in PDF processing attempt $attempt: ${e.message}")
            }
        }

        // If we get here, all attempts failed
        return@withContext Result.failure(
            lastException ?: IOException("Failed to process PDF after $maxRetries retries")
        )
    }

    /**
     * Process image with retry mechanism for better reliability
     */
    private suspend fun processImageWithRetry(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker?,
        maxRetries: Int = 2
    ): Result<AimlApiRequest.ContentPart> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                Timber.d("Retry attempt $attempt for image processing")
                tracker?.updateProgress(
                    30 + (attempt * 10),
                    "Retrying image processing (attempt $attempt)...",
                    FileProgressTracker.ProcessingStage.ENCODING
                )
                delay(1000)  // Wait before retrying
            }

            try {
                // Call the process method with persistent copy
                return@withContext processImage(uri, modelId, tracker)
            } catch (e: Exception) {
                lastException = e
                Timber.e(e, "Error in image processing attempt $attempt: ${e.message}")
            }
        }

        // If we get here, all attempts failed
        return@withContext Result.failure(
            lastException ?: IOException("Failed to process image after $maxRetries retries")
        )
    }

    /**
     * Process an image file - Fixed with persistent copy
     */
    private suspend fun processImage(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker?
    ): Result<AimlApiRequest.ContentPart> = withContext(Dispatchers.IO) {        try {
            // Create persistent copy first
            tracker?.updateProgress(
                20,
                "Creating stable image copy...",
                FileProgressTracker.ProcessingStage.INITIALIZING
            )

            val persistentImageResult = createPersistentImageCopy(uri)
            if (persistentImageResult.isFailure) {
                return@withContext Result.failure(
                    persistentImageResult.exceptionOrNull() ?: IOException("Failed to create persistent image")
                )
            }

            val persistentFile = persistentImageResult.getOrNull()!!
            val persistentUri = Uri.fromFile(persistentFile)

            tracker?.updateProgress(
                60,
                "Optimizing image...",
                FileProgressTracker.ProcessingStage.ENCODING
            )

            // Get the image as a base64 string
            val fullImageBase64 = aiFileService.getOptimizedImageBase64(persistentUri, 2048, modelId)

            if (fullImageBase64 != null) {
                tracker?.updateProgress(
                    90,
                    "Image optimization complete",
                    FileProgressTracker.ProcessingStage.FINALIZING
                )

                // Create ImageContent with PROPER data URI
                val imageUrl = AimlApiRequest.ImageUrl(
                    url = fullImageBase64,  // Use complete data URI
                    detail = "auto"
                )

                val contentItem = AimlApiRequest.ContentPart(
                    type = "image_url",
                    imageUrl = AimlApiRequest.ImageUrl(url = imageUrl.toString())
                )
                tracker?.updateProgress(
                    100,
                    "Image processing complete",
                    FileProgressTracker.ProcessingStage.COMPLETE
                )

                // Clean up the persistent file after successful processing
                withContext(Dispatchers.IO) {
                    try {
                        if (persistentFile.exists()) {
                            persistentFile.delete()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to delete temporary file: ${e.message}")
                        // Non-fatal error, continue with processing
                    }
                }

                return@withContext Result.success(contentItem)
            } else {
                return@withContext Result.failure(IOException("Failed to encode image as base64"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing image: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Process a PDF file for GPT-4o and compatible models
     */
    private suspend fun processPdf(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker?,
        fileName: String?
    ): Result<AimlApiRequest.ContentPart> = withContext(Dispatchers.IO) {
        try {
            // Update progress
            tracker?.updateProgress(
                20,
                "Reading PDF file...",
                FileProgressTracker.ProcessingStage.INITIALIZING
            )

            // Read the PDF file as bytes
            val pdfBytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: return@withContext Result.failure(IOException("Failed to read PDF file"))

            // Validate PDF file size against model limits
            val maxSize = ModelValidator.getMaxFileSizeBytes(modelId)
            if (pdfBytes.size > maxSize) {
                return@withContext Result.failure(
                    IOException("PDF file size (${FileUtil.formatFileSize(pdfBytes.size.toLong())}) exceeds model limit (${FileUtil.formatFileSize(maxSize)})")
                )
            }

            tracker?.updateProgress(
                60,
                "Encoding PDF to base64...",
                FileProgressTracker.ProcessingStage.ENCODING
            )

            // Convert to base64
            val base64Pdf = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.NO_WRAP)
            
            tracker?.updateProgress(
                80,
                "Creating file content...",
                FileProgressTracker.ProcessingStage.FINALIZING
            )

            // Create the file content object
            val fileContent = AimlApiRequest.FileContent(
                fileData = base64Pdf,
                filename = fileName ?: "document.pdf"
            )

            // Create the content part
            val contentPart = AimlApiRequest.ContentPart(
                type = "file",
                file = fileContent
            )

            tracker?.updateProgress(
                100,
                "PDF processing complete",
                FileProgressTracker.ProcessingStage.COMPLETE
            )

            Timber.d("PDF processing completed successfully - size: ${FileUtil.formatFileSize(pdfBytes.size.toLong())}, filename: $fileName")
            
            return@withContext Result.success(contentPart)
            
        } catch (e: Exception) {
            Timber.e(e, "Error processing PDF: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Create a persistent copy of an image file
     * This prevents URIs from becoming invalid during processing
     */
    private suspend fun createPersistentImageCopy(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val cacheFile = File(context.cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IOException("Failed to open input stream"))

            Timber.d("Created persistent image copy at ${cacheFile.absolutePath}")
            return@withContext Result.success(cacheFile)
        } catch (e: Exception) {
            Timber.e(e, "Error creating persistent image copy: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Result class with file type information
     */
    enum class FileType {
        IMAGE,
        PDF,
        DOCUMENT  // Added for server-processed documents (DOCX, XLSX, PPTX, TXT)
    }

    /**
     * Data class to hold the result of file processing
     */
    data class ProcessedFileResult(
        val contentItem: AimlApiRequest.ContentPart,
        val fileType: FileType
    )
}