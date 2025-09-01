package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.mozilla.universalchardet.UniversalDetector
import timber.log.Timber
import java.io.*
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * Simplified Document Content Extractor
 *
 * Supports only reliable, crash-free formats:
 * - Text files (.txt) with advanced encoding detection
 * - CSV files (.csv) with smart delimiter detection  
 * - PDF files are handled by AI models that support them directly
 * 
 * Removed PDFBox and Apache POI dependencies to prevent Android crashes
 */
class SimplifiedDocumentExtractor(private val context: Context) {

    companion object {
        private val extractedContentCache = ConcurrentHashMap<String, ExtractedContent>()
        
        // Default limits
        private const val DEFAULT_MAX_TOKENS = 8000
        private const val MAX_CONTENT_LENGTH = 500_000 // 500K chars max
        private const val MAX_CSV_ROWS = 1000
        
        // MIME type constants
        private const val MIME_PDF = "application/pdf"
        private const val MIME_TEXT = "text/plain"
        private const val MIME_CSV = "text/csv"

        fun getCachedContent(uriString: String): ExtractedContent? {
            return extractedContentCache[uriString]
        }

        fun cacheContent(uriString: String, content: ExtractedContent) {
            extractedContentCache[uriString] = content
            Timber.d("Cached content for $uriString (${content.textContent.length} chars)")
        }

        fun clearCache(uriString: String) {
            extractedContentCache.remove(uriString)
        }

        fun clearAllCache() {
            extractedContentCache.clear()
        }
    }

    /**
     * Main entry point for document extraction with timeout protection
     */
    suspend fun extractContent(
        uri: Uri,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        aiModelId: String? = null
    ): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        try {
            // Add timeout protection to prevent infinite processing
            Timber.d("🔄 Starting simplified extraction with 30s timeout...")
            withTimeout(30_000) {
                // Get file metadata
                Timber.d("📋 Getting file metadata...")
                val fileName = FileUtil.getFileNameFromUri(context, uri) ?: "unknown_file"
                Timber.d("📋 Got fileName: $fileName")
                val mimeType = FileUtil.getMimeType(context, uri) ?: detectMimeTypeFromFileName(fileName)
                Timber.d("📋 Got mimeType: $mimeType")
                val fileSize = FileUtil.getFileSizeFromUri(context, uri)
                Timber.d("📋 Got fileSize: ${FileUtil.formatFileSize(fileSize)}")

                Timber.d("🔄 Extracting content from $fileName (MIME: $mimeType, Size: ${FileUtil.formatFileSize(fileSize)})")

                // Extract content based on file type
                Timber.d("🔄 Determining extraction method...")
                val (textContent, pageCount) = when {
                    // Text files
                    mimeType == MIME_TEXT || fileName.endsWith(".txt", true) -> {
                        Timber.d("📄 Processing TXT file...")
                        extractTextContent(uri) to 1
                    }

                    // CSV files
                    mimeType == MIME_CSV || fileName.endsWith(".csv", true) -> {
                        Timber.d("📊 Processing CSV file...")
                        extractCsvContent(uri) to 1
                    }

                    // PDF files - check if AI model supports them
                    mimeType == MIME_PDF || fileName.endsWith(".pdf", true) -> {
                        Timber.d("📄 Processing PDF file...")
                        handlePdfFile(uri, fileName, aiModelId) to 1
                    }

                    // Other text-based files
                    mimeType.startsWith("text/") -> {
                        extractTextContent(uri) to 1
                    }

                    // Unsupported formats
                    else -> {
                        Timber.w("⚠️ Unsupported file type: $mimeType")
                        createUnsupportedFileMessage(fileName, mimeType) to 0
                    }
                }

                // Truncate if needed
                val truncatedContent = if (textContent.length > MAX_CONTENT_LENGTH) {
                    textContent.take(MAX_CONTENT_LENGTH) +
                            "\n\n[Content truncated - file too large for complete extraction]"
                } else {
                    textContent
                }

                // Estimate tokens
                val tokenCount = TokenValidator.estimateTokenCount(truncatedContent)

                // Create result
                val extractedContent = ExtractedContent(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    textContent = truncatedContent,
                    containsImages = detectIfContainsImages(mimeType),
                    tokenCount = tokenCount,
                    pageCount = pageCount
                )

                // Cache the result
                cacheContent(uri.toString(), extractedContent)

                Result.success(extractedContent)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.e("Document extraction timed out after 30 seconds")
            Result.failure(Exception("Document processing timed out. File may be too large or complex. Please try a smaller file."))
        } catch (e: Exception) {
            Timber.e(e, "Error extracting content: ${e.message}")
            Result.failure(Exception("Failed to extract content: ${e.message}"))
        }
    }

    /**
     * Handle PDF files - either direct AI processing or user guidance
     */
    private fun handlePdfFile(uri: Uri, fileName: String, aiModelId: String?): String {
        val fileSize = try {
            FileUtil.getFileSizeFromUri(context, uri)
        } catch (e: Exception) {
            0L
        }
        
        // Check if the AI model supports PDF
        val supportsPdf = aiModelId?.let { ModelValidator.supportsPdfDirectly(it) } ?: false
        
        return if (supportsPdf) {
            """
                📄 PDF Document: $fileName
                Size: ${FileUtil.formatFileSize(fileSize)}
                
                ✅ This PDF will be processed directly by the AI model.
                The selected AI model (${aiModelId}) has native PDF support.
                
                📋 PDF DETAILS:
                • File: $fileName
                • Size: ${FileUtil.formatFileSize(fileSize)}
                • Processing: Native AI model support
                
                Note: The AI will analyze the PDF content, including text, tables, and images.
            """.trimIndent()
        } else {
            """
                📄 PDF Document: $fileName
                Size: ${FileUtil.formatFileSize(fileSize)}
                
                ⚠️ PDF processing not available for this AI model.
                
                🔧 SOLUTIONS:
                
                1. 📱 USE AI MODEL WITH PDF SUPPORT:
                   • GPT-4o, Claude-3, or Gemini Pro
                   • These models can read PDFs directly
                
                2. 📝 CONVERT TO SUPPORTED FORMAT:
                   • Use Google Drive: PDF → Text
                   • Online converters: PDF → .txt
                   • Copy text manually from PDF viewer
                
                3. 📋 MANUAL TEXT EXTRACTION:
                   • Open PDF in any viewer
                   • Select and copy the text
                   • Paste directly in chat
                
                The AI can analyze any text you provide through these methods!
            """.trimIndent()
        }
    }

    /**
     * Create message for unsupported file types
     */
    private fun createUnsupportedFileMessage(fileName: String, mimeType: String): String {
        return """
            📄 File: $fileName
            Type: $mimeType
            
            ❌ This file type is not supported for text extraction.
            
            🔧 SUPPORTED FORMATS:
            • ✅ Text files (.txt)
            • ✅ CSV files (.csv)  
            • ✅ PDF files (with compatible AI models)
            
            📋 ALTERNATIVES:
            • Convert to .txt format
            • Export as CSV (for spreadsheets)
            • Use copy/paste for small amounts of text
            • Choose an AI model that supports this file type
            
            For the best experience, use text files or CSV files which are fully supported.
        """.trimIndent()
    }

    /**
     * Extract text content with encoding detection and timeout protection
     */
    private suspend fun extractTextContent(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            Timber.d("📄 Starting text extraction with 10s timeout...")
            withTimeout(10_000) {
                // First, detect encoding
                val encoding = detectTextEncoding(uri)

                // Read with detected encoding
                context.contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input, encoding).use { reader ->
                        val content = reader.readText().trim()
                        
                        if (content.isBlank()) {
                            "⚠️ Text file appears to be empty or contains only whitespace."
                        } else {
                            // Add file info header
                            "📄 Text File Content:\n\n$content"
                        }
                    }
                } ?: throw IOException("Cannot open text file")
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            "⚠️ Text file processing timed out. File may be too large."
        } catch (e: Exception) {
            Timber.e(e, "Error extracting text content: ${e.message}")
            "⚠️ Error reading text file: ${e.message}"
        }
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
     * Extract CSV content with timeout protection
     */
    private suspend fun extractCsvContent(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000) {
                val content = StringBuilder()
                content.append("📊 CSV File Content\n\n")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Try to detect delimiter
                    val csvText = input.bufferedReader().use { it.readText() }
                    val delimiter = detectCsvDelimiter(csvText)

                    content.append("📋 CSV Info:\n")
                    content.append("• Delimiter: '$delimiter'\n")
                    content.append("• Encoding: UTF-8\n\n")

                    // Parse CSV
                    val parser = CSVParserBuilder()
                        .withSeparator(delimiter)
                        .withQuoteChar('"')
                        .withEscapeChar('\\')
                        .build()

                    val reader = CSVReaderBuilder(StringReader(csvText))
                        .withCSVParser(parser)
                        .build()

                    // Read header
                    val header = reader.readNext()
                    if (header != null) {
                        content.append("📊 COLUMNS (${header.size}):\n")
                        header.forEachIndexed { index, columnName ->
                            content.append("${index + 1}. $columnName\n")
                        }
                        content.append("\n" + "=".repeat(50) + "\n\n")
                        
                        content.append("📋 DATA PREVIEW:\n")
                        content.append("${header.joinToString(" | ")}\n")
                        content.append("-".repeat(header.joinToString(" | ").length) + "\n")
                    }

                    // Read data rows
                    var rowCount = 0
                    var row: Array<String>?

                    while (reader.readNext().also { row = it } != null && rowCount < MAX_CSV_ROWS) {
                        row?.let {
                            content.append("${it.joinToString(" | ")}\n")
                            rowCount++
                        }
                    }

                    reader.close()

                    // Count total rows
                    val totalRows = csvText.lines().size - 1
                    if (totalRows > rowCount) {
                        content.append("\n📊 SUMMARY:\n")
                        content.append("• Showing: $rowCount rows\n")
                        content.append("• Total: $totalRows rows\n")
                        content.append("• Hidden: ${totalRows - rowCount} rows (file too large)\n")
                    } else {
                        content.append("\n📊 SUMMARY:\n")
                        content.append("• Total rows: $totalRows\n")
                        content.append("• All data displayed\n")
                    }
                }

                content.toString()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            "⚠️ CSV file processing timed out. File may be too large or have complex formatting."
        } catch (e: Exception) {
            Timber.e(e, "Error extracting CSV content: ${e.message}")
            "⚠️ Error reading CSV file: ${e.message}"
        }
    }

    /**
     * Detect CSV delimiter
     */
    private fun detectCsvDelimiter(csvText: String): Char {
        val firstLine = csvText.lines().firstOrNull() ?: return ','

        val delimiters = listOf(',', ';', '\t', '|')
        val counts = delimiters.associateWith { delimiter ->
            firstLine.count { it == delimiter }
        }

        return counts.maxByOrNull { it.value }?.key ?: ','
    }

    /**
     * Helper functions
     */
    private fun detectMimeTypeFromFileName(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", true) -> MIME_PDF
            fileName.endsWith(".txt", true) -> MIME_TEXT
            fileName.endsWith(".csv", true) -> MIME_CSV
            else -> "application/octet-stream"
        }
    }

    private fun detectIfContainsImages(mimeType: String): Boolean {
        return when {
            mimeType == MIME_PDF -> true
            else -> false
        }
    }

    /**
     * Process document for AI model
     */
    suspend fun processDocumentForModel(uri: Uri, aiModelId: String? = null): Result<ExtractedContent> {
        return extractContent(uri, aiModelId = aiModelId)
    }

    /**
     * Data class for extracted content
     */
    data class ExtractedContent(
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val textContent: String,
        val containsImages: Boolean,
        val tokenCount: Int,
        val pageCount: Int
    ) {
        fun formatForAiModel(): String {
            val sb = StringBuilder()

            // Metadata header
            sb.append("=== DOCUMENT METADATA ===\n")
            sb.append("File: $fileName\n")
            sb.append("Type: ${getReadableFileType()}\n")
            sb.append("Size: ${FileUtil.formatFileSize(fileSize)}\n")
            sb.append("Estimated tokens: $tokenCount\n")
            sb.append("=========================\n\n")

            // Content
            sb.append(textContent)

            return sb.toString()
        }

        private fun getReadableFileType(): String {
            return when {
                mimeType.contains("pdf") -> "PDF Document"
                mimeType.contains("csv") -> "CSV File"
                mimeType.contains("text") -> "Text File"
                else -> "Document"
            }
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("fileName", fileName)
                put("mimeType", mimeType)
                put("fileSize", fileSize)
                put("containsImages", containsImages)
                put("tokenCount", tokenCount)
                put("pageCount", pageCount)
                put("content", textContent)
            }
        }
    }
}