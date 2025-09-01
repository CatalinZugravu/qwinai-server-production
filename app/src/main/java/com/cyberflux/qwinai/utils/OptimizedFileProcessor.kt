package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mozilla.universalchardet.UniversalDetector
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * OPTIMIZED File Processor for PDF, CSV, and TXT files
 * 
 * Purpose: Handle only supported file types with proper token counting
 * - PDF files: Pass through directly to AI models (no content extraction)
 * - CSV files: Extract content for text processing
 * - TXT files: Extract content for text processing
 * 
 * Integration with SimplifiedTokenManager for accurate token counting
 */
class OptimizedFileProcessor(private val context: Context) {

    companion object {
        private const val MAX_CONTENT_LENGTH = 500_000 // 500K chars max
        private const val MAX_CSV_ROWS = 1000
        private const val EXTRACTION_TIMEOUT = 30_000L // 30 seconds
    }

    /**
     * Main processing method for supported file types
     */
    suspend fun processFile(
        uri: Uri,
        modelId: String,
        tracker: FileProgressTracker? = null
    ): Result<ProcessedFileResult> = withContext(Dispatchers.IO) {
        try {
            tracker?.updateProgress(
                10,
                "Analyzing file...",
                FileProgressTracker.ProcessingStage.INITIALIZING
            )

            // Get file metadata
            val fileName = FileUtil.getFileName(context, uri) ?: "unknown_file"
            val mimeType = FileUtil.getMimeType(context, uri) ?: detectMimeTypeFromFileName(fileName)
            val fileSize = FileUtil.getFileSize(context, uri)

            Timber.d("🔄 Processing file: $fileName (MIME: $mimeType, Size: ${FileUtil.formatFileSize(fileSize)})")

            // Validate file type is supported
            val fileType = when {
                mimeType == "application/pdf" || fileName.endsWith(".pdf", true) -> FileType.PDF
                mimeType == "text/plain" || fileName.endsWith(".txt", true) -> FileType.TXT
                mimeType == "text/csv" || fileName.endsWith(".csv", true) -> FileType.CSV
                else -> return@withContext Result.failure(
                    UnsupportedOperationException("Unsupported file type: $mimeType. Only PDF, TXT, and CSV files are supported.")
                )
            }

            tracker?.updateProgress(
                30,
                "Processing ${fileType.name.lowercase()} file...",
                FileProgressTracker.ProcessingStage.PROCESSING_PAGES
            )

            // Process based on file type
            val result = when (fileType) {
                FileType.PDF -> processPdfFile(uri, fileName, modelId, tracker)
                FileType.TXT -> processTextFile(uri, fileName, tracker)
                FileType.CSV -> processCsvFile(uri, fileName, tracker)
            }

            tracker?.updateProgress(
                100,
                "File processing complete",
                FileProgressTracker.ProcessingStage.COMPLETE
            )

            return@withContext result

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
     * Process PDF file - pass through with metadata
     */
    private suspend fun processPdfFile(
        uri: Uri,
        fileName: String,
        modelId: String,
        tracker: FileProgressTracker?
    ): Result<ProcessedFileResult> = withContext(Dispatchers.IO) {
        try {
            // Check if the AI model supports PDF
            val supportsPdf = ModelValidator.supportsPdfDirectly(modelId)
            
            if (!supportsPdf) {
                return@withContext Result.failure(
                    UnsupportedOperationException("The selected AI model ($modelId) does not support PDF files directly. Please use a model like GPT-4o, Claude-3, or Gemini Pro.")
                )
            }

            tracker?.updateProgress(
                60,
                "Preparing PDF for AI model...",
                FileProgressTracker.ProcessingStage.ENCODING
            )

            val fileSize = FileUtil.getFileSize(context, uri)
            
            // Estimate tokens for PDF (conservative estimate based on file size)
            val estimatedTokens = estimatePdfTokens(fileSize)
            
            val result = ProcessedFileResult(
                fileType = FileType.PDF,
                fileName = fileName,
                fileSize = fileSize,
                extractedContent = null, // PDF content not extracted
                tokenCount = estimatedTokens,
                uri = uri,
                processingMessage = "PDF will be processed directly by the AI model"
            )

            tracker?.updateProgress(
                90,
                "PDF ready for AI processing",
                FileProgressTracker.ProcessingStage.FINALIZING
            )

            return@withContext Result.success(result)

        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Process text file - extract content
     */
    private suspend fun processTextFile(
        uri: Uri,
        fileName: String,
        tracker: FileProgressTracker?
    ): Result<ProcessedFileResult> = withContext(Dispatchers.IO) {
        try {
            tracker?.updateProgress(
                40,
                "Reading text file...",
                FileProgressTracker.ProcessingStage.READING_FILE
            )

            val result = withTimeout(EXTRACTION_TIMEOUT) {
                // Detect encoding
                val encoding = detectTextEncoding(uri)
                
                tracker?.updateProgress(
                    60,
                    "Extracting text content...",
                    FileProgressTracker.ProcessingStage.PROCESSING_PAGES
                )

                // Read content with encoding detection
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input, encoding).use { reader ->
                        reader.readText().trim()
                    }
                } ?: throw Exception("Cannot read text file")

                if (content.isBlank()) {
                    throw Exception("Text file is empty or contains only whitespace")
                }

                // Truncate if necessary
                val truncatedContent = if (content.length > MAX_CONTENT_LENGTH) {
                    content.take(MAX_CONTENT_LENGTH) + "\n\n[Content truncated - file too large]"
                } else {
                    content
                }

                tracker?.updateProgress(
                    80,
                    "Counting tokens...",
                    FileProgressTracker.ProcessingStage.FINALIZING
                )

                // Count tokens
                val tokenCount = TokenValidator.estimateTokenCount(truncatedContent)
                val fileSize = FileUtil.getFileSize(context, uri)

                ProcessedFileResult(
                    fileType = FileType.TXT,
                    fileName = fileName,
                    fileSize = fileSize,
                    extractedContent = truncatedContent,
                    tokenCount = tokenCount,
                    uri = uri,
                    processingMessage = "Text content extracted successfully"
                )
            }

            return@withContext Result.success(result)

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return@withContext Result.failure(Exception("Text file processing timed out. File may be too large."))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Process CSV file - extract content with structure
     */
    private suspend fun processCsvFile(
        uri: Uri,
        fileName: String,
        tracker: FileProgressTracker?
    ): Result<ProcessedFileResult> = withContext(Dispatchers.IO) {
        try {
            tracker?.updateProgress(
                40,
                "Reading CSV file...",
                FileProgressTracker.ProcessingStage.READING_FILE
            )

            val result = withTimeout(EXTRACTION_TIMEOUT) {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        val csvText = reader.readText()
                        formatCsvContent(csvText, fileName)
                    }
                } ?: throw Exception("Cannot read CSV file")

                tracker?.updateProgress(
                    80,
                    "Counting tokens...",
                    FileProgressTracker.ProcessingStage.FINALIZING
                )

                // Count tokens
                val tokenCount = TokenValidator.estimateTokenCount(content)
                val fileSize = FileUtil.getFileSize(context, uri)

                ProcessedFileResult(
                    fileType = FileType.CSV,
                    fileName = fileName,
                    fileSize = fileSize,
                    extractedContent = content,
                    tokenCount = tokenCount,
                    uri = uri,
                    processingMessage = "CSV content extracted successfully"
                )
            }

            return@withContext Result.success(result)

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return@withContext Result.failure(Exception("CSV file processing timed out. File may be too large."))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Format CSV content for AI processing
     */
    private fun formatCsvContent(csvText: String, fileName: String): String {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw Exception("CSV file is empty")
        }

        val content = StringBuilder()
        content.append("📊 CSV File: $fileName\n\n")

        // Auto-detect delimiter
        val delimiter = detectCsvDelimiter(lines.first())
        content.append("📋 CSV Details:\n")
        content.append("• Delimiter: '$delimiter'\n")
        content.append("• Total rows: ${lines.size}\n\n")

        // Add header if available
        if (lines.isNotEmpty()) {
            val header = lines.first().split(delimiter)
            content.append("📊 COLUMNS (${header.size}):\n")
            header.forEachIndexed { index, columnName ->
                content.append("${index + 1}. ${columnName.trim()}\n")
            }
            content.append("\n" + "=".repeat(50) + "\n\n")
        }

        // Add data preview (limited rows)
        content.append("📋 DATA:\n")
        val rowsToShow = minOf(lines.size, MAX_CSV_ROWS)
        
        for (i in 0 until rowsToShow) {
            val row = lines[i].split(delimiter)
            content.append("Row ${i + 1}: ${row.joinToString(" | ") { it.trim() }}\n")
        }

        if (lines.size > MAX_CSV_ROWS) {
            content.append("\n[Showing first $MAX_CSV_ROWS rows out of ${lines.size} total rows]\n")
        }

        return content.toString()
    }

    /**
     * Detect CSV delimiter
     */
    private fun detectCsvDelimiter(firstLine: String): Char {
        val delimiters = listOf(',', ';', '\t', '|')
        val counts = delimiters.associateWith { delimiter ->
            firstLine.count { it == delimiter }
        }
        return counts.maxByOrNull { it.value }?.key ?: ','
    }

    /**
     * Detect text file encoding
     */
    private fun detectTextEncoding(uri: Uri): Charset {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val detector = UniversalDetector(null)
                val buffer = ByteArray(4096)
                var nread: Int

                while (input.read(buffer).also { nread = it } > 0 && !detector.isDone) {
                    detector.handleData(buffer, 0, nread)
                }

                detector.dataEnd()
                val encoding = detector.detectedCharset
                detector.reset()

                when (encoding) {
                    null -> Charsets.UTF_8
                    else -> Charset.forName(encoding)
                }
            } ?: Charsets.UTF_8
        } catch (e: Exception) {
            Timber.w("Encoding detection failed, using UTF-8: ${e.message}")
            Charsets.UTF_8
        }
    }

    /**
     * Estimate PDF tokens based on file size (conservative)
     */
    private fun estimatePdfTokens(fileSize: Long): Int {
        // Conservative estimation: ~250 tokens per KB for PDF files
        val tokensPerKb = 250
        val fileSizeKb = (fileSize / 1024).toInt()
        return maxOf(fileSizeKb * tokensPerKb, 1000) // Minimum 1000 tokens
    }

    /**
     * Detect MIME type from file name
     */
    private fun detectMimeTypeFromFileName(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".txt", true) -> "text/plain"
            fileName.endsWith(".csv", true) -> "text/csv"
            else -> "application/octet-stream"
        }
    }

    /**
     * File type enumeration
     */
    enum class FileType {
        PDF, TXT, CSV
    }

    /**
     * Result of file processing
     */
    data class ProcessedFileResult(
        val fileType: FileType,
        val fileName: String,
        val fileSize: Long,
        val extractedContent: String?, // null for PDF files (sent directly)
        val tokenCount: Int,
        val uri: Uri,
        val processingMessage: String
    ) {
        /**
         * Get content for AI model
         */
        fun getContentForAI(): String? {
            return extractedContent
        }

        /**
         * Check if file should be sent directly to AI
         */
        fun shouldSendDirectly(): Boolean {
            return fileType == FileType.PDF
        }

        /**
         * Get summary for UI display
         */
        fun getSummary(): String {
            return "📄 $fileName • ${FileUtil.formatFileSize(fileSize)} • $tokenCount tokens"
        }
    }
}