package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.cyberflux.qwinai.network.AimlApiRequest
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
     * Process a file for a specific AI model
     * Complete pipeline from URI to formatted ContentItem
     */
    suspend fun processFileForModel(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker? = null
    ): Result<ProcessedFileResult> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Processing file for model: $modelId")

            // Get file metadata
            val mimeType = FileUtil.getMimeType(context, uri) ?: "application/octet-stream"
            val fileName = FileUtil.getFileName(context, uri)
            val fileSize = FileUtil.getFileSize(context, uri)

            // Log file details
            Timber.d("File details: name=$fileName, type=$mimeType, size=${fileSize/1024}KB")

            tracker?.updateProgress(
                30,
                "Processing file format...",
                FileProgressTracker.ProcessingStage.PROCESSING_PAGES
            )

            // Check file size limit
            val maxFileSizeBytes = ModelValidator.getMaxFileSizeBytes(modelId)
            if (maxFileSizeBytes > 0 && fileSize > maxFileSizeBytes) {
                return@withContext Result.failure(
                    IOException("File size (${FileUtil.formatFileSize(fileSize)}) exceeds maximum allowed (${FileUtil.formatFileSize(maxFileSizeBytes)})")
                )
            }

            // Check if file is image
            val isImage = mimeType.startsWith("image/")

            if (isImage) {
                // Process image with retry mechanism
                tracker?.updateProgress(
                    50,
                    "Processing image",
                    FileProgressTracker.ProcessingStage.PROCESSING_PAGES
                )

                val contentItem = processImageWithRetry(uri, modelId, tracker)
                return@withContext Result.success(
                    ProcessedFileResult(
                        contentItem = contentItem.getOrThrow(),
                        fileType = FileType.IMAGE
                    )
                )
            } else {
                // Not an image - reject the file
                return@withContext Result.failure(
                    UnsupportedOperationException("Only image files are supported")
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing file: ${e.message}")
            tracker?.updateProgress(
                100,
                "Error: ${e.message}",
                FileProgressTracker.ProcessingStage.ERROR
            )
            return@withContext Result.failure(e)
        }
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
        IMAGE
    }

    /**
     * Data class to hold the result of file processing
     */
    data class ProcessedFileResult(
        val contentItem: AimlApiRequest.ContentPart,
        val fileType: FileType
    )
}