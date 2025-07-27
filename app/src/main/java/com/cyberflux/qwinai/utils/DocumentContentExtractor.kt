package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
// PDFBox imports removed to prevent NumberFormatException during class loading
// import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
// import com.tom_roush.pdfbox.pdmodel.PDDocument
// import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Document Content Extractor
 *
 * A utility class that extracts text content from various document types
 * for AI models that don't natively support file processing.
 *
 * Supports:
 * - PDF files
 * - Text files
 * - Word documents (.docx, .doc)
 * - CSV files
 * - Excel files (.xlsx, .xls)
 * - PowerPoint files (.pptx, .ppt)
 */
class DocumentContentExtractor(private val context: Context) {

    companion object {
        private val extractedContentCache = ConcurrentHashMap<String, ExtractedContent>()

        // MIME type constants
        private const val MIME_PDF = "application/pdf"
        private const val MIME_TEXT = "text/plain"
        private const val MIME_CSV = "text/csv"

        // Default token limits
        private const val DEFAULT_MAX_TOKENS = 8000

        // CSV Parser constants
        private const val CSV_SEPARATOR = ','
        private const val CSV_QUOTE_CHAR = '"'
        private const val CSV_ESCAPE_CHAR = '\\'

        // Timeout for PDF processing (30 seconds)
        private const val PDF_PROCESSING_TIMEOUT = 30_000L
        fun getCachedContent(uriString: String): ExtractedContent? {
            val content = extractedContentCache[uriString]
            Timber.d("Getting cached content for $uriString: ${content != null}")
            if (content == null) {
                Timber.w("Cache miss for $uriString! Cache size: ${extractedContentCache.size}")
                // Debug all cache keys
                extractedContentCache.keys.forEach {
                    Timber.d("Cache contains key: $it")
                }
            }
            return content
        }

        // Make sure caching is working correctly
        fun cacheContent(uriString: String, content: ExtractedContent) {
            Timber.d("Caching content for $uriString (${content.textContent.length} chars)")
            extractedContentCache[uriString] = content
        }

        // Clear cache for specific URI
        fun clearCache(uriString: String) {
            extractedContentCache.remove(uriString)
        }

        // Clear all cache
        fun clearAllCache() {
            extractedContentCache.clear()
        }
    }

    // PDFBox variables removed - using alternative extraction only

    /**
     * Extract content from a document file
     * @param uri URI of the document
     * @param maxTokens Maximum number of tokens to extract (for truncation)
     * @return Extracted content with metadata
     */
    suspend fun extractContent(
        uri: Uri,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        var tempFile: File? = null

        try {
            // Get basic file information
            val fileName = FileUtil.getFileNameFromUri(context, uri) ?: "unknown_file"
            val mimeType = FileUtil.getMimeType(context, uri) ?: "application/octet-stream"
            val fileSize = FileUtil.getFileSizeFromUri(context, uri)

            Timber.d("Extracting content from $fileName (MIME: $mimeType, Size: ${FileUtil.formatFileSize(fileSize)})")
            
            // Additional validation for PDF files
            if (fileName.endsWith(".pdf", ignoreCase = true) && mimeType != MIME_PDF) {
                Timber.w("PDF file detected by extension but MIME type is: $mimeType")
                // Force PDF processing for .pdf files
                if (mimeType != MIME_PDF) {
                    Timber.d("Forcing PDF processing for file with .pdf extension")
                }
            }

            // Extract content based on file type
            val textContent = when {
                // Plain text files
                mimeType == MIME_TEXT -> extractTextFileContent(uri)

                // PDF files (check both MIME type and file extension)
                mimeType == MIME_PDF || fileName.endsWith(".pdf", ignoreCase = true) -> {
                    Timber.d("Processing PDF file: $fileName")
                    tempFile = File.createTempFile("temp_pdf_", ".pdf", context.cacheDir)
                    copyToTempFile(uri, tempFile)
                    
                    // Skip PDFBox entirely and use alternative extraction directly
                    // This avoids the NumberFormatException: "For input string: 'space' under radix 16"
                    // that occurs during PDFBox's GlyphList initialization on some devices
                    val pdfContent = try {
                        Timber.d("Using alternative PDF extraction to avoid PDFBox compatibility issues")
                        extractPdfContentAlternative(tempFile)
                    } catch (e: Exception) {
                        Timber.w("Alternative PDF extraction failed: ${e.message}")
                        extractPdfContentFallback(tempFile)
                    }
                    
                    Timber.d("PDF extraction completed. Content length: ${pdfContent.length} chars")
                    pdfContent
                }

                // Word documents
                mimeType.contains("word") || mimeType.contains("docx") || mimeType.contains("doc") -> {
                    tempFile = File.createTempFile("temp_word_",
                        if (mimeType.contains("docx")) ".docx" else ".doc",
                        context.cacheDir)
                    copyToTempFile(uri, tempFile)
                    extractWordContent(tempFile, mimeType)
                }

                // CSV files
                mimeType == MIME_CSV || mimeType.contains("csv") -> extractCsvContent(uri)

                // Excel files
                mimeType.contains("excel") || mimeType.contains("spreadsheet") ||
                        mimeType.contains("xlsx") || mimeType.contains("xls") -> {
                    tempFile = File.createTempFile("temp_excel_",
                        if (mimeType.contains("xlsx")) ".xlsx" else ".xls",
                        context.cacheDir)
                    copyToTempFile(uri, tempFile)
                    extractExcelContent(tempFile, mimeType)
                }

                // PowerPoint files
                mimeType.contains("powerpoint") || mimeType.contains("presentation") -> {
                    tempFile = File.createTempFile("temp_ppt_",
                        if (mimeType.contains("pptx")) ".pptx" else ".ppt",
                        context.cacheDir)
                    copyToTempFile(uri, tempFile)
                    extractPowerPointContent(tempFile, mimeType)
                }

                // Other text-based files
                mimeType.contains("text/") -> extractTextFileContent(uri)

                // Unsupported file types
                else -> "This file type ($mimeType) cannot be processed for content extraction."
            }

            // Detect if file contains images
            val containsImages = detectImages(uri, mimeType)

            // Truncate content if needed
            val truncatedContent = truncateContent(textContent, maxTokens)

            // Calculate token estimate for the truncated content
            val tokenCount = TokenValidator.estimateTokenCount(truncatedContent)

            // Estimate page count
            val pageCount = estimatePageCount(uri, mimeType, truncatedContent)

            Timber.d("Successfully extracted content from $fileName, token count: $tokenCount")

            // Return success with extracted content
            Result.success(
                ExtractedContent(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    textContent = truncatedContent,
                    containsImages = containsImages,
                    tokenCount = tokenCount,
                    pageCount = pageCount
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting content from document: ${e.message}")
            Result.failure(e)
        } finally {
            // Clean up temporary file if it exists
            tempFile?.delete()
        }
    }

    suspend fun processDocumentForModel(uri: Uri): Result<ExtractedContent> {
        return extractContent(uri).also { result ->
            if (result.isSuccess) {
                // CRITICAL FIX: Cache the extracted content using URI string as key
                result.getOrNull()?.let { content ->
                    val uriString = uri.toString()
                    cacheContent(uriString, content)
                    Timber.d("Cached extracted content for URI: $uriString")
                    // Verify cache worked
                    val cached = getCachedContent(uriString)
                    Timber.d("Verified cache: ${cached != null}, content length: ${cached?.textContent?.length}")
                }
            }
        }
    }

    /**
     * Helper method to copy content to temporary file
     */
    private suspend fun copyToTempFile(uri: Uri, tempFile: File) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to open input stream")
    }

    /**
     * Truncate content to limit token count
     */
    private fun truncateContent(content: String, maxTokens: Int): String {
        // If content is already under the limit, return it as is
        val estimatedTokens = TokenValidator.estimateTokenCount(content)
        if (estimatedTokens <= maxTokens) {
            return content
        }

        // Simple truncation strategy - preserve approximately the right amount of text
        // This is a rough approximation; actual token counting is more complex
        val ratio = maxTokens.toDouble() / estimatedTokens.toDouble()
        val keepChars = (content.length * ratio * 0.9).toInt() // 0.9 factor for safety margin

        val truncated = content.take(keepChars)
        return truncated + "\n\n[Content truncated due to length. Approximately ${estimatedTokens - maxTokens} tokens omitted.]"
    }

    /**
     * Extract content from a plain text file with better encoding handling
     */
    private suspend fun extractTextFileContent(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Try to detect encoding and read content
                val content = try {
                    // First try UTF-8
                    inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }
                } catch (e: Exception) {
                    // If UTF-8 fails, try other encodings
                    inputStream.reset()
                    try {
                        inputStream.bufferedReader(Charsets.ISO_8859_1).use { reader ->
                            reader.readText()
                        }
                    } catch (e2: Exception) {
                        // Final fallback - read as system default
                        inputStream.bufferedReader().use { reader ->
                            reader.readText()
                        }
                    }
                }
                
                // Clean up the content
                val cleanedContent = content
                    .replace(Regex("\\r\\n|\\r"), "\n")  // Normalize line endings
                    .replace(Regex("\\t"), "    ")       // Convert tabs to spaces
                    .trim()
                
                return@withContext cleanedContent
            } ?: throw IOException("Failed to open input stream for text file")
        } catch (e: Exception) {
            Timber.e(e, "Error extracting text file content: ${e.message}")
            return@withContext "Error extracting text content: ${e.message}"
        }
    }

    // PDFBox-related methods removed to prevent class loading issues

    // PDFBox extraction methods removed to prevent class loading issues

    // PDFBox extraction method removed to prevent class loading issues

    // PDFBox availability and initialization methods removed to prevent class loading issues

    /**
     * Alternative PDF text extraction method using basic text pattern matching
     */
    private suspend fun extractPdfContentAlternative(tempFile: File): String = withContext(Dispatchers.IO) {
        try {
            Timber.d("Using alternative PDF text extraction method")
            
            // Try to extract text using basic patterns in PDF structure
            val extractedContent = mutableListOf<String>()
            
            // Read PDF file as bytes to look for text streams
            val bytes = tempFile.readBytes()
            val content = String(bytes, Charsets.ISO_8859_1)
            
            // Look for text between common PDF text operators
            val textPatterns = listOf(
                Regex("\\(([^)]{3,})\\)"),  // Text in parentheses
                Regex("\\[([^]]{3,})\\]"),  // Text in brackets
                Regex("Tj\\s*\\(([^)]+)\\)"), // Text show operators
                Regex("TJ\\s*\\[([^]]+)\\]"), // Text show array operators
                Regex("Td\\s*\\(([^)]+)\\)"), // Text positioning operators
                Regex("TD\\s*\\(([^)]+)\\)") // Text positioning operators
            )
            
            textPatterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val text = match.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\\\", "\\")
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .trim()
                    
                    if (text.length > 2 && isReadableText(text)) {
                        extractedContent.add(text)
                    }
                }
            }
            
            // Remove duplicates and combine
            val uniqueContent = extractedContent.distinct()
            
            return@withContext if (uniqueContent.isNotEmpty()) {
                val result = uniqueContent.joinToString("\n").trim()
                Timber.d("Alternative PDF extraction found text: ${result.length} characters")
                result
            } else {
                throw Exception("No readable text found using alternative method")
            }
        } catch (e: Exception) {
            Timber.e(e, "Alternative PDF extraction failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * Check if text is readable (not garbled binary data)
     */
    private fun isReadableText(text: String): Boolean {
        if (text.length < 3) return false
        
        // Check for reasonable ratio of letters to other characters
        val letterCount = text.count { it.isLetter() }
        val totalCount = text.length
        val letterRatio = letterCount.toFloat() / totalCount.toFloat()
        
        // At least 60% letters, no control characters, reasonable length
        return letterRatio >= 0.6 && 
               text.none { it.isISOControl() } && 
               text.length <= 500 && // Avoid very long strings that might be binary
               !text.contains(Regex("[\\x00-\\x08\\x0E-\\x1F\\x7F-\\x9F]")) // No control chars
    }

    /**
     * Simple fallback PDF extraction method
     */
    private suspend fun extractPdfContentFallback(tempFile: File): String = withContext(Dispatchers.IO) {
        Timber.d("Using fallback PDF extraction method")
        
        return@withContext """
            ⚠️ PDF TEXT EXTRACTION FAILED
            
            Unable to extract readable text from this PDF file using available methods.
            
            This could indicate:
            • Scanned document (image-only PDF requiring OCR)
            • Password-protected or encrypted PDF
            • Corrupted PDF file
            • Complex PDF format with compressed/encoded text
            • PDF with non-standard text encoding
            
            💡 RECOMMENDATIONS:
            1. 🔍 Use OCR Model: For scanned documents, try an OCR-capable AI model
            2. 📄 Convert Format: Save as Word (.docx) or plain text (.txt) first
            3. 🔓 Check Protection: Ensure PDF is not password-protected
            4. 📱 Re-upload: Try uploading the file again
            5. 📖 Manual Check: Verify the PDF contains readable text in a PDF viewer
            6. 🔧 PDF Repair: Try opening and re-saving the PDF in Adobe Reader
            
            If this is a text-based PDF that displays normally in PDF viewers, 
            the issue may be with complex text encoding or formatting.
        """.trimIndent()
    }

    /**
     * Extract content from a Word document with improved error handling
     */
    private suspend fun extractWordContent(tempFile: File, mimeType: String): String = withContext(Dispatchers.IO) {
        try {
            val wordContent = StringBuilder()

            if (mimeType.contains("docx")) {
                // DOCX format (XML-based)
                var document: XWPFDocument? = null
                try {
                    document = XWPFDocument(tempFile.inputStream())

                    // Extract headers
                    document.headerList.forEach { header ->
                        header.paragraphs.forEach { paragraph ->
                            val text = paragraph.text.trim()
                            if (text.isNotEmpty()) {
                                wordContent.append(text).append("\n")
                            }
                        }
                    }

                    // Extract paragraphs (main content)
                    document.paragraphs.forEach { paragraph ->
                        val text = paragraph.text.trim()
                        if (text.isNotEmpty()) {
                            wordContent.append(text).append("\n")
                        }
                    }

                    // Extract tables
                    document.tables.forEach { table ->
                        wordContent.append("\n--- Table ---\n")
                        table.rows.forEach { row ->
                            val rowContent = row.tableCells
                                .map { it.text.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(" | ")
                            if (rowContent.isNotEmpty()) {
                                wordContent.append(rowContent).append("\n")
                            }
                        }
                        wordContent.append("--- End Table ---\n\n")
                    }

                    // Extract footers
                    document.footerList.forEach { footer ->
                        footer.paragraphs.forEach { paragraph ->
                            val text = paragraph.text.trim()
                            if (text.isNotEmpty()) {
                                wordContent.append(text).append("\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting DOCX content: ${e.message}")
                    wordContent.append("⚠️ Error extracting DOCX content: ${e.message}\n")
                } finally {
                    document?.close()
                }
            } else {
                // DOC format (older binary format)
                var document: HWPFDocument? = null
                var extractor: WordExtractor? = null
                try {
                    document = HWPFDocument(tempFile.inputStream())
                    extractor = WordExtractor(document)

                    // Extract text using the WordExtractor
                    val paragraphText = extractor.paragraphText
                    for (i in paragraphText.indices) {
                        val text = paragraphText[i].trim()
                        if (text.isNotEmpty()) {
                            wordContent.append(text).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting DOC content: ${e.message}")
                    wordContent.append("⚠️ Error extracting DOC content: ${e.message}\n")
                } finally {
                    extractor?.close()
                    document?.close()
                }
            }

            val result = wordContent.toString().trim()
            return@withContext if (result.isNotEmpty()) {
                result
            } else {
                "⚠️ No readable text content found in Word document.\n\nPossible reasons:\n- Document is corrupted\n- Document contains only images\n- Document uses unsupported formatting\n- File is password-protected"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting Word document content: ${e.message}")
            return@withContext "⚠️ Error extracting Word document content: ${e.message}\n\nPlease try:\n1. Re-uploading the file\n2. Converting to PDF or plain text\n3. Ensuring the file is not corrupted or password-protected"
        }
    }

    /**
     * Extract content from a CSV file with improved parsing and error handling
     */
    private suspend fun extractCsvContent(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val content = StringBuilder()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val csvText = inputStream.bufferedReader().use { it.readText() }

                // Try different CSV parsing approaches
                val result = try {
                    // Try with OpenCSV library
                    extractCsvWithOpenCSV(csvText)
                } catch (e: Exception) {
                    Timber.w("OpenCSV failed, trying manual parsing: ${e.message}")
                    // Fallback to manual parsing
                    extractCsvManually(csvText)
                }

                return@withContext result
            } ?: throw IOException("Failed to open input stream for CSV file")
        } catch (e: Exception) {
            Timber.e(e, "Error extracting CSV content: ${e.message}")
            return@withContext "⚠️ Error extracting CSV content: ${e.message}\n\nPlease try:\n1. Ensuring the file is a valid CSV\n2. Converting to plain text format\n3. Checking for special characters or encoding issues"
        }
    }

    /**
     * Extract CSV content using OpenCSV library
     */
    private fun extractCsvWithOpenCSV(csvText: String): String {
        val content = StringBuilder()
        
        // Create CSV parser with custom configuration
        val parser = CSVParserBuilder()
            .withSeparator(CSV_SEPARATOR)
            .withQuoteChar(CSV_QUOTE_CHAR)
            .withEscapeChar(CSV_ESCAPE_CHAR)
            .build()

        // Use CSVReaderBuilder instead of direct constructor
        val reader = CSVReaderBuilder(StringReader(csvText))
            .withCSVParser(parser)
            .build()

        try {
            // Read header
            val header = reader.readNext()
            if (header != null) {
                val cleanedHeader = header.map { it.trim() }
                content.append("Columns: ${cleanedHeader.joinToString(", ")}\n\n")
            }

            // Read data (limit to first 100 rows for large files)
            var rowCount = 0
            var rowData: Array<String>? = reader.readNext()

            while (rowData != null && rowCount < 100) {
                val cleanedRow = rowData.map { it.trim() }
                content.append("Row ${rowCount + 1}: ${cleanedRow.joinToString(" | ")}\n")
                rowCount++
                rowData = reader.readNext()
            }

            // Check for more data
            if (reader.readNext() != null) {
                content.append("\n[CSV file contains ${rowCount} more rows not shown here]")
            }
            
            content.append("\n\nTotal rows processed: $rowCount")
        } finally {
            reader.close()
        }

        return content.toString()
    }

    /**
     * Manual CSV parsing as fallback
     */
    private fun extractCsvManually(csvText: String): String {
        val content = StringBuilder()
        val lines = csvText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (lines.isEmpty()) {
            return "⚠️ CSV file appears to be empty or contains no readable data."
        }

        // Process header
        val header = lines.first().split(",").map { it.trim().replace("\"", "") }
        content.append("Columns: ${header.joinToString(", ")}\n\n")

        // Process data rows (limit to first 100)
        val dataLines = lines.drop(1).take(100)
        dataLines.forEachIndexed { index, line ->
            val cells = line.split(",").map { it.trim().replace("\"", "") }
            content.append("Row ${index + 1}: ${cells.joinToString(" | ")}\n")
        }

        if (lines.size > 101) {
            content.append("\n[CSV file contains ${lines.size - 101} more rows not shown here]")
        }

        content.append("\n\nTotal rows processed: ${dataLines.size}")
        return content.toString()
    }

    /**
     * Extract content from an Excel file with improved formatting and error handling
     */
    private suspend fun extractExcelContent(tempFile: File, mimeType: String): String = withContext(Dispatchers.IO) {
        try {
            val excelContent = StringBuilder()

            if (mimeType.contains("xlsx")) {
                // XLSX format (XML-based)
                var workbook: XSSFWorkbook? = null
                try {
                    workbook = XSSFWorkbook(tempFile.inputStream())

                    // Limit to 5 sheets for large workbooks
                    val sheetCount = minOf(workbook.numberOfSheets, 5)
                    excelContent.append("Excel Workbook (${workbook.numberOfSheets} sheets total)\n\n")

                    for (sheetIndex in 0 until sheetCount) {
                        val sheet = workbook.getSheetAt(sheetIndex)
                        excelContent.append("=== Sheet: ${sheet.sheetName} ===\n")

                        // Process up to 50 rows per sheet
                        val maxRows = minOf(sheet.lastRowNum + 1, 50)
                        var nonEmptyRows = 0
                        
                        for (rowIndex in 0 until maxRows) {
                            val row = sheet.getRow(rowIndex) ?: continue
                            val rowContent = mutableListOf<String>()

                            // Process cells in this row
                            for (cellIndex in 0 until row.lastCellNum) {
                                val cell = row.getCell(cellIndex)
                                val cellValue = when {
                                    cell == null -> ""
                                    cell.cellType == null -> cell.toString().trim()
                                    cell.cellType.toString() == "FORMULA" -> {
                                        try {
                                            cell.toString().trim()
                                        } catch (e: Exception) {
                                            "[Formula Error]"
                                        }
                                    }
                                    else -> cell.toString().trim()
                                }
                                rowContent.add(cellValue)
                            }

                            // Only add non-empty rows
                            val cleanedRow = rowContent.filter { it.isNotEmpty() }
                            if (cleanedRow.isNotEmpty()) {
                                excelContent.append("Row ${rowIndex + 1}: ${cleanedRow.joinToString(" | ")}\n")
                                nonEmptyRows++
                            }
                        }

                        // Indicate if there are more rows
                        if (sheet.lastRowNum + 1 > 50) {
                            excelContent.append("[Sheet contains ${sheet.lastRowNum + 1 - 50} more rows not shown]\n")
                        }

                        excelContent.append("Total non-empty rows: $nonEmptyRows\n\n")
                    }

                    // Indicate if there are more sheets
                    if (workbook.numberOfSheets > 5) {
                        excelContent.append("[Workbook contains ${workbook.numberOfSheets - 5} more sheets not shown]\n")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting XLSX content: ${e.message}")
                    excelContent.append("⚠️ Error extracting XLSX content: ${e.message}\n")
                } finally {
                    workbook?.close()
                }
            } else {
                // XLS format (older binary format)
                var workbook: HSSFWorkbook? = null
                try {
                    workbook = HSSFWorkbook(tempFile.inputStream())

                    val sheetCount = minOf(workbook.numberOfSheets, 5)
                    excelContent.append("Excel Workbook (${workbook.numberOfSheets} sheets total)\n\n")

                    for (sheetIndex in 0 until sheetCount) {
                        val sheet = workbook.getSheetAt(sheetIndex)
                        excelContent.append("=== Sheet: ${sheet.sheetName} ===\n")

                        // Process rows (limited to 50)
                        val maxRows = minOf(sheet.lastRowNum + 1, 50)
                        var nonEmptyRows = 0
                        
                        for (rowIndex in 0 until maxRows) {
                            val row = sheet.getRow(rowIndex) ?: continue
                            val rowContent = mutableListOf<String>()

                            for (cellIndex in 0 until row.lastCellNum) {
                                val cell = row.getCell(cellIndex)
                                val cellValue = cell?.toString()?.trim() ?: ""
                                if (cellValue.isNotEmpty()) {
                                    rowContent.add(cellValue)
                                }
                            }

                            if (rowContent.isNotEmpty()) {
                                excelContent.append("Row ${rowIndex + 1}: ${rowContent.joinToString(" | ")}\n")
                                nonEmptyRows++
                            }
                        }

                        if (sheet.lastRowNum + 1 > 50) {
                            excelContent.append("[Sheet contains ${sheet.lastRowNum + 1 - 50} more rows not shown]\n")
                        }

                        excelContent.append("Total non-empty rows: $nonEmptyRows\n\n")
                    }

                    if (workbook.numberOfSheets > 5) {
                        excelContent.append("[Workbook contains ${workbook.numberOfSheets - 5} more sheets not shown]\n")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting XLS content: ${e.message}")
                    excelContent.append("⚠️ Error extracting XLS content: ${e.message}\n")
                } finally {
                    workbook?.close()
                }
            }

            val result = excelContent.toString().trim()
            return@withContext if (result.isNotEmpty()) {
                result
            } else {
                "⚠️ No readable content found in Excel file.\n\nPossible reasons:\n- File is corrupted\n- File is password-protected\n- File contains only formulas with no values\n- File is empty"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting Excel content: ${e.message}")
            return@withContext "⚠️ Error extracting Excel content: ${e.message}\n\nPlease try:\n1. Re-uploading the file\n2. Converting to CSV format\n3. Ensuring the file is not corrupted or password-protected"
        }
    }

    /**
     * Extract content from a PowerPoint presentation with improved formatting and error handling
     */
    private suspend fun extractPowerPointContent(tempFile: File, mimeType: String): String = withContext(Dispatchers.IO) {
        try {
            val pptContent = StringBuilder()

            if (mimeType.contains("pptx")) {
                // PPTX format (XML-based)
                var presentation: XMLSlideShow? = null
                try {
                    presentation = XMLSlideShow(tempFile.inputStream())

                    val slideCount = presentation.slides.size
                    pptContent.append("PowerPoint Presentation ($slideCount slides)\n\n")

                    // Process each slide (limit to 20 slides for large presentations)
                    val maxSlides = minOf(slideCount, 20)
                    presentation.slides.take(maxSlides).forEachIndexed { index, slide ->
                        pptContent.append("=== Slide ${index + 1} ===\n")

                        // Extract slide title if available
                        val title = slide.title
                        if (!title.isNullOrBlank()) {
                            pptContent.append("Title: $title\n")
                        }

                        // Extract text from shapes
                        val slideTexts = mutableListOf<String>()
                        for (shape in slide.shapes) {
                            if (shape is XSLFTextShape) {
                                for (paragraph in shape.textParagraphs) {
                                    val text = paragraph.text.trim()
                                    if (text.isNotEmpty() && !slideTexts.contains(text)) {
                                        slideTexts.add(text)
                                    }
                                }
                            }
                        }

                        // Add slide content
                        if (slideTexts.isNotEmpty()) {
                            pptContent.append("Content:\n")
                            slideTexts.forEach { text ->
                                pptContent.append("• $text\n")
                            }
                        }

                        // Extract notes if available
                        val notes = slide.notes
                        if (notes != null) {
                            val noteTexts = mutableListOf<String>()
                            for (shape in notes.shapes) {
                                if (shape is XSLFTextShape) {
                                    for (paragraph in shape.textParagraphs) {
                                        val text = paragraph.text.trim()
                                        if (text.isNotEmpty()) {
                                            noteTexts.add(text)
                                        }
                                    }
                                }
                            }
                            if (noteTexts.isNotEmpty()) {
                                pptContent.append("Notes: ${noteTexts.joinToString(" ")}\n")
                            }
                        }

                        pptContent.append("\n")
                    }

                    // Indicate if there are more slides
                    if (slideCount > 20) {
                        pptContent.append("[Presentation contains ${slideCount - 20} more slides not shown]\n")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting PPTX content: ${e.message}")
                    pptContent.append("⚠️ Error extracting PPTX content: ${e.message}\n")
                } finally {
                    presentation?.close()
                }
            } else {
                // PPT format (older binary format)
                var presentation: HSLFSlideShow? = null
                try {
                    presentation = HSLFSlideShow(tempFile.inputStream())

                    val slideCount = presentation.slides.size
                    pptContent.append("PowerPoint Presentation ($slideCount slides)\n\n")

                    // Process each slide (limit to 20 slides)
                    val maxSlides = minOf(slideCount, 20)
                    presentation.slides.take(maxSlides).forEachIndexed { index, slide ->
                        pptContent.append("=== Slide ${index + 1} ===\n")

                        // Extract text from shapes directly
                        val slideTexts = mutableListOf<String>()
                        for (shape in slide.shapes) {
                            if (shape is HSLFTextShape) {
                                val text = shape.text.trim()
                                if (text.isNotEmpty() && !slideTexts.contains(text)) {
                                    slideTexts.add(text)
                                }
                            }
                        }

                        if (slideTexts.isNotEmpty()) {
                            pptContent.append("Content:\n")
                            slideTexts.forEach { text ->
                                pptContent.append("• $text\n")
                            }
                        }

                        pptContent.append("\n")
                    }

                    if (slideCount > 20) {
                        pptContent.append("[Presentation contains ${slideCount - 20} more slides not shown]\n")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting PPT content: ${e.message}")
                    pptContent.append("⚠️ Error extracting PPT content: ${e.message}\n")
                } finally {
                    presentation?.close()
                }
            }

            val result = pptContent.toString().trim()
            return@withContext if (result.isNotEmpty()) {
                result
            } else {
                "⚠️ No readable content found in PowerPoint presentation.\n\nPossible reasons:\n- Presentation contains only images\n- File is corrupted\n- File is password-protected\n- Presentation is empty"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting PowerPoint content: ${e.message}")
            return@withContext "⚠️ Error extracting PowerPoint content: ${e.message}\n\nPlease try:\n1. Re-uploading the file\n2. Converting to PDF format\n3. Ensuring the file is not corrupted or password-protected"
        }
    }

    /**
     * Detect if a file contains images - simplified version that doesn't rely on PDImageXObject
     */
    private suspend fun detectImages(uri: Uri, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        // Simplified detection that doesn't rely on problematic classes
        return@withContext when {
            // PDFs typically contain images
            mimeType == MIME_PDF -> true

            // Word documents may contain images
            mimeType.contains("word") || mimeType.contains("docx") || mimeType.contains("doc") -> true

            // PowerPoint presentations typically contain images
            mimeType.contains("powerpoint") || mimeType.contains("presentation") -> true

            // Excel files may contain charts/images
            mimeType.contains("excel") || mimeType.contains("spreadsheet") -> true

            // Plain text files don't contain images
            mimeType == MIME_TEXT || mimeType.contains("csv") || mimeType.contains("text/") -> false

            // Default to false for unknown types
            else -> false
        }
    }

    /**
     * Estimate page count for a document - simplified version
     */
    private suspend fun estimatePageCount(uri: Uri, mimeType: String, content: String): Int =
        withContext(Dispatchers.IO) {
            try {
                when {
                    // For text files, estimate based on line count
                    mimeType == MIME_TEXT -> {
                        val lineCount = content.count { it == '\n' } + 1
                        return@withContext Math.max(1, lineCount / 40)  // Assume ~40 lines per page
                    }

                    // For CSV, count rows
                    mimeType.contains("csv") -> {
                        val lineCount = content.count { it == '\n' } + 1
                        return@withContext Math.max(1, lineCount / 40)  // Assume ~40 rows per page
                    }

                    // Default to content-based estimation
                    else -> Math.max(1, content.length / 3000)  // Rough character count estimate
                }
            } catch (e: Exception) {
                Timber.e(e, "Error estimating page count: ${e.message}")
                // Default fallback
                return@withContext 1
            }
        }

    /**
     * Data class for extracted content and metadata
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
        /**
         * Format the content for the AI model with comprehensive metadata
         */
        fun formatForAiModel(): String {
            val sb = StringBuilder()

            // Add comprehensive file metadata
            sb.append("=== DOCUMENT METADATA ===\n")
            sb.append("Filename: $fileName\n")
            sb.append("MIME Type: $mimeType\n")
            sb.append("File Size: ${FileUtil.formatFileSize(fileSize)}\n")
            sb.append("Estimated Pages: $pageCount\n")
            sb.append("Contains Images: $containsImages\n")
            sb.append("Content Length: ${textContent.length} characters\n")
            sb.append("Estimated Tokens: $tokenCount\n")
            sb.append("Extraction Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            sb.append("=== END METADATA ===\n\n")

            // Add content header
            sb.append("--- EXTRACTED DOCUMENT CONTENT BEGINS ---\n\n")

            // Add the actual content
            sb.append(textContent)

            // Add content footer
            sb.append("\n\n--- EXTRACTED DOCUMENT CONTENT ENDS ---\n")
            
            // Add processing notes
            if (textContent.contains("[Content truncated")) {
                sb.append("\nNOTE: This document content was truncated due to size limitations. The full document contains more information than shown above.\n")
            }

            return sb.toString()
        }

        /**
         * Convert to JSON for API usage
         */
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("fileName", fileName)
            json.put("mimeType", mimeType)
            json.put("fileSize", fileSize)
            json.put("containsImages", containsImages)
            json.put("tokenCount", tokenCount)
            json.put("pageCount", pageCount)
            json.put("content", textContent)
            return json
        }
    }
}