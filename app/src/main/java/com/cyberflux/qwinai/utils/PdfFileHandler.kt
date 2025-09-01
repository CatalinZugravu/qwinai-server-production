package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Handler for PDF file processing for OCR capabilities
 * Using Android's built-in PdfRenderer
 */
class PdfFileHandler(private val context: Context) {

    /**
     * Process a PDF file for OCR
     * @param uri URI of the PDF file
     * @param modelId ID of the OCR model
     * @param tracker Optional progress tracker
     * @return Result with either the processed file or an error
     */
    suspend fun processPdfForOcr(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker? = null
    ): Result<ProcessedPdfResult> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Processing PDF for OCR: $uri")
            tracker?.updateProgress(
                10,
                "Validating PDF file...",
                FileProgressTracker.ProcessingStage.INITIALIZING
            )

            // Get file metadata
            val mimeType = FileUtil.getMimeType(context, uri) ?: "application/octet-stream"
            val fileName = FileUtil.getFileName(context, uri)
            val fileSize = FileUtil.getFileSize(context, uri)

            // Log file details
            Timber.d("PDF details: name=$fileName, type=$mimeType, size=${fileSize/1024}KB")

            // Step 1: Validate file type
            if (mimeType != "application/pdf") {
                return@withContext Result.failure(
                    UnsupportedOperationException("Only PDF files are supported for OCR. Found: $mimeType")
                )
            }

            // Step 2: Check file size limit
            val maxFileSizeBytes = ModelValidator.getMaxFileSizeBytes(modelId)
            if (maxFileSizeBytes > 0 && fileSize > maxFileSizeBytes) {
                return@withContext Result.failure(
                    IOException("PDF size (${FileUtil.formatFileSize(fileSize)}) exceeds maximum allowed (${FileUtil.formatFileSize(maxFileSizeBytes)})")
                )
            }

            tracker?.updateProgress(
                30,
                "Reading PDF file...",
                FileProgressTracker.ProcessingStage.READING_FILE
            )

            // Step 3: Create a temporary file for the PDF
            val tempFile = createTempFile(uri)

            tracker?.updateProgress(
                40,
                "Analyzing PDF structure...",
                FileProgressTracker.ProcessingStage.ANALYZING_FORMAT
            )

            // Step 4: Check page count using Android PdfRenderer
            Timber.d("Starting PDF page count analysis...")
            val pageCount = countPdfPages(tempFile)
            Timber.d("PDF page count: $pageCount")

            val maxPages = ModelValidator.getMaxDocumentPages(modelId)
            if (pageCount > maxPages) {
                tempFile.delete()
                return@withContext Result.failure(
                    IOException("PDF has $pageCount pages, which exceeds the maximum of $maxPages pages")
                )
            }

            tracker?.updateProgress(
                70,
                "Optimizing PDF for OCR...",
                FileProgressTracker.ProcessingStage.OPTIMIZING
            )

            // Additional processing steps if needed

            tracker?.updateProgress(
                90,
                "PDF ready for OCR processing",
                FileProgressTracker.ProcessingStage.FINALIZING
            )

            // Return success with the file details
            return@withContext Result.success(
                ProcessedPdfResult(
                    file = tempFile,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    pageCount = pageCount
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Error processing PDF file: ${e.message}")
            tracker?.updateProgress(
                100,
                "Error: ${e.message}",
                FileProgressTracker.ProcessingStage.ERROR
            )
            return@withContext Result.failure(e)
        }
    }

    /**
     * Count the number of pages in a PDF document using Android's PdfRenderer
     */
    private suspend fun countPdfPages(file: File): Int = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            withTimeout(30000) { // 30 second timeout
                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(fileDescriptor)
                return@withTimeout renderer.pageCount
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("PDF analysis timed out - file may be too large or corrupted")
        } catch (e: Exception) {
            throw IOException("Failed to analyze PDF structure: ${e.message}")
        } finally {
            renderer?.close()
            fileDescriptor?.close()
        }
    }

    /**
     * Create a temporary file from the URI
     */
    private suspend fun createTempFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open input stream")

        val tempFile = File.createTempFile("ocr_", ".pdf", context.cacheDir)

        FileOutputStream(tempFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        return@withContext tempFile
    }

    /**
     * Result class for processed PDF files
     */
    data class ProcessedPdfResult(
        val file: File,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val pageCount: Int
    )
}