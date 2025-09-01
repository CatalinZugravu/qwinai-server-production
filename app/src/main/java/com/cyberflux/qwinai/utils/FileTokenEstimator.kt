package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.util.zip.ZipInputStream
// External dependencies temporarily commented out for compilation
// import org.apache.poi.xwpf.usermodel.XWPFDocument
// import org.apache.poi.xssf.usermodel.XSSFWorkbook
// import org.apache.poi.hslf.usermodel.HSLFSlideShow
// import com.itextpdf.text.pdf.PdfReader
// import com.itextpdf.text.pdf.parser.PdfTextExtractor

/**
 * SMART File Token Estimator
 * 
 * Purpose: Accurately estimate tokens for different file types BEFORE upload
 * - Handles text, PDF, images, Office documents
 * - Uses file-specific logic for better accuracy  
 * - Conservative estimates to prevent context overflow
 * - Fast estimation without full processing
 */
class FileTokenEstimator(private val context: Context) {
    
    companion object {
        // Token estimation constants (conservative)
        private const val CHARS_PER_TOKEN_PLAIN_TEXT = 3.5
        private const val CHARS_PER_TOKEN_PDF = 4.0  // PDFs have formatting overhead
        private const val CHARS_PER_TOKEN_OFFICE = 4.2  // Office docs have more metadata
        
        // Image token costs (vision models)
        private const val TOKENS_PER_IMAGE_BASE = 85  // Base cost for image processing
        private const val TOKENS_PER_TILE = 170      // Additional cost per 512x512 tile
        private const val MAX_IMAGE_DIMENSION = 2048 // Max dimension for processing
        
        // File size limits (conservative estimates)
        private const val MAX_REASONABLE_FILE_TOKENS = 100000
        private const val FALLBACK_TOKENS_PER_KB = 250
        
        // Office document estimation factors
        private const val WORD_CHARS_PER_PAGE = 2500
        private const val EXCEL_CHARS_PER_CELL = 20
        private const val POWERPOINT_CHARS_PER_SLIDE = 500
    }
    
    /**
     * File token estimation result
     */
    sealed class FileTokenResult {
        data class Success(
            val estimatedTokens: Int,
            val fileType: FileType,
            val details: String,
            val confidence: Confidence = Confidence.MEDIUM
        ) : FileTokenResult()
        
        data class Error(
            val message: String,
            val fallbackTokens: Int = 1000
        ) : FileTokenResult()
    }
    
    enum class FileType {
        PLAIN_TEXT,
        PDF,
        IMAGE,
        WORD_DOC,
        EXCEL_SHEET,
        POWERPOINT,
        CSV,
        JSON,
        XML,
        CODE,
        ARCHIVE,
        UNKNOWN
    }
    
    enum class Confidence {
        HIGH,    // Very accurate estimate (text files, simple formats)
        MEDIUM,  // Good estimate (PDFs, Office docs)  
        LOW,     // Rough estimate (images, binary files)
        FALLBACK // Emergency fallback
    }
    
    /**
     * MAIN METHOD: Estimate tokens for a file
     */
    suspend fun estimateFileTokens(uri: Uri, fileName: String? = null): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val mimeType = FileUtil.getMimeType(context, uri)
            val actualFileName = fileName ?: FileUtil.getFileName(context, uri) ?: "unknown"
            val fileType = determineFileType(mimeType, actualFileName)
            
            Timber.d("📊 Estimating tokens for: $actualFileName (type: $fileType, mime: $mimeType)")
            
            val result = when (fileType) {
                FileType.PLAIN_TEXT -> estimateTextFileTokens(uri)
                FileType.PDF -> estimatePdfTokens(uri)
                FileType.IMAGE -> estimateImageTokens(uri)
                FileType.WORD_DOC -> estimateWordDocTokens(uri)
                FileType.EXCEL_SHEET -> estimateExcelTokens(uri)
                FileType.POWERPOINT -> estimatePowerPointTokens(uri)
                FileType.CSV -> estimateCsvTokens(uri)
                FileType.JSON -> estimateJsonTokens(uri)
                FileType.XML -> estimateXmlTokens(uri)
                FileType.CODE -> estimateCodeFileTokens(uri)
                FileType.ARCHIVE -> estimateArchiveTokens(uri)
                FileType.UNKNOWN -> estimateUnknownFileTokens(uri)
            }
            
            when (result) {
                is FileTokenResult.Success -> {
                    Timber.d("📊 Token estimate for $actualFileName: ${result.estimatedTokens} tokens (${result.confidence} confidence)")
                }
                is FileTokenResult.Error -> {
                    Timber.w("⚠️ Token estimation failed for $actualFileName: ${result.message}")
                }
            }
            return@withContext result
            
        } catch (e: Exception) {
            Timber.e(e, "Error estimating tokens for file: $uri")
            return@withContext FileTokenResult.Error(
                message = "Failed to estimate tokens: ${e.message}",
                fallbackTokens = 2000
            )
        }
    }
    
    /**
     * Determine file type from MIME type and extension
     */
    private fun determineFileType(mimeType: String?, fileName: String): FileType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        return when {
            // Text files
            mimeType?.startsWith("text/") == true -> FileType.PLAIN_TEXT
            extension in listOf("txt", "md", "readme") -> FileType.PLAIN_TEXT
            
            // PDF
            mimeType == "application/pdf" -> FileType.PDF
            extension == "pdf" -> FileType.PDF
            
            // Images  
            mimeType?.startsWith("image/") == true -> FileType.IMAGE
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> FileType.IMAGE
            
            // Office documents
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FileType.WORD_DOC
            extension in listOf("docx", "doc") -> FileType.WORD_DOC
            
            mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> FileType.EXCEL_SHEET
            extension in listOf("xlsx", "xls") -> FileType.EXCEL_SHEET
            
            mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> FileType.POWERPOINT
            extension in listOf("pptx", "ppt") -> FileType.POWERPOINT
            
            // Data files
            mimeType == "text/csv" -> FileType.CSV
            extension == "csv" -> FileType.CSV
            
            mimeType == "application/json" -> FileType.JSON
            extension == "json" -> FileType.JSON
            
            mimeType?.contains("xml") == true -> FileType.XML
            extension == "xml" -> FileType.XML
            
            // Code files
            extension in listOf("js", "ts", "py", "java", "kt", "cpp", "c", "h", "cs", "php", "rb", "go", "rs", "swift") -> FileType.CODE
            
            // Archives
            mimeType?.contains("zip") == true -> FileType.ARCHIVE
            extension in listOf("zip", "rar", "7z", "tar", "gz") -> FileType.ARCHIVE
            
            else -> FileType.UNKNOWN
        }
    }
    
    /**
     * Estimate tokens for plain text files
     */
    private suspend fun estimateTextFileTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open file stream")
                
            val content = inputStream.bufferedReader().use { it.readText() }
            val charCount = content.length
            val estimatedTokens = (charCount / CHARS_PER_TOKEN_PLAIN_TEXT).toInt()
            
            // Cap at reasonable limit
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = FileType.PLAIN_TEXT,
                details = "$charCount characters → $cappedTokens tokens",
                confidence = Confidence.HIGH
            )
            
        } catch (e: Exception) {
            return@withContext FileTokenResult.Error("Error reading text file: ${e.message}")
        }
    }
    
    /**
     * Estimate tokens for PDF files (using hybrid approach)
     */
    private suspend fun estimatePdfTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Estimating PDF tokens using hybrid approach")
            
            // Try hybrid estimation first, fallback to size-based
            return@withContext hybridPdfEstimation(uri)
            
            /* TODO: Enable when PDF libraries are available
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open PDF stream")
                
            // Try to extract text and estimate
            val reader = PdfReader(inputStream)
            val pageCount = reader.numberOfPages
            var totalChars = 0
            
            // Extract text from first few pages for estimation
            val pagesToSample = minOf(pageCount, 5)
            for (i in 1..pagesToSample) {
                val pageText = PdfTextExtractor.getTextFromPage(reader, i)
                totalChars += pageText.length
            }
            
            reader.close()
            
            // Extrapolate to full document
            val avgCharsPerPage = if (pagesToSample > 0) totalChars / pagesToSample else 2000
            val estimatedTotalChars = avgCharsPerPage * pageCount
            val estimatedTokens = (estimatedTotalChars / CHARS_PER_TOKEN_PDF).toInt()
            
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = FileType.PDF,
                details = "$pageCount pages, ~$estimatedTotalChars chars → $cappedTokens tokens",
                confidence = Confidence.MEDIUM
            )
            */
            
        } catch (e: Exception) {
            Timber.w("PDF estimation failed, using fallback: ${e.message}")
            return@withContext estimateByFileSize(uri, FileType.PDF, CHARS_PER_TOKEN_PDF)
        }
    }
    
    /**
     * Hybrid PDF token estimation using structure analysis + file size
     */
    private suspend fun hybridPdfEstimation(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open PDF")
                
            // Read first 8KB for PDF structure analysis
            val buffer = ByteArray(8192)
            val bytesRead = inputStream.read(buffer)
            
            if (bytesRead <= 0) {
                inputStream.close()
                return@withContext estimateByFileSize(uri, FileType.PDF, CHARS_PER_TOKEN_PDF)
            }
            
            // Quick PDF structure analysis
            val headerContent = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)
            
            // Check if PDF is readable (not encrypted/corrupted)
            if (!headerContent.startsWith("%PDF-")) {
                Timber.w("File doesn't appear to be a valid PDF")
                return@withContext estimateByFileSize(uri, FileType.PDF, CHARS_PER_TOKEN_PDF)
            }
            
            // Check for encryption
            val isEncrypted = headerContent.contains("/Encrypt")
            if (isEncrypted) {
                Timber.d("PDF is encrypted, using size-based estimation")
                return@withContext estimateByFileSize(uri, FileType.PDF, CHARS_PER_TOKEN_PDF * 1.2)
            }
            
            val pageCount = estimatePageCountFromPdfHeader(headerContent)
            val hasImages = headerContent.contains("/Image") || headerContent.contains("/XObject")
            val hasText = headerContent.contains("/Font") || headerContent.contains("/Text")
            val isFormDocument = headerContent.contains("/AcroForm") || headerContent.contains("/Field")
            
            // Validate page count (sanity check)
            val validatedPageCount = when {
                pageCount <= 0 -> 1  // Default to at least 1 page
                pageCount > 10000 -> {  // Suspicious page count
                    Timber.w("Suspicious page count: $pageCount, capping at 1000")
                    1000
                }
                else -> pageCount
            }
            
            inputStream.close()
            
            // Get file size for cross-validation
            val fileSize = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { 
                    it.statSize 
                } ?: 0L
            } catch (e: Exception) {
                0L
            }
            
            // Estimate tokens per page based on document characteristics
            val tokensPerPage = when {
                isFormDocument -> 400  // Form documents tend to be structured
                hasImages && hasText -> 600  // Mixed content documents
                hasImages -> 300  // Image-heavy documents
                hasText -> 800  // Text-heavy documents
                else -> 500  // Generic fallback
            }
            
            // Calculate estimates using different methods
            val structureBasedTokens = validatedPageCount * tokensPerPage
            val sizeBasedTokens = if (fileSize > 0) (fileSize / 8).toInt() else Int.MAX_VALUE
            
            // Use the more conservative estimate
            val estimatedTokens = minOf(
                structureBasedTokens,
                sizeBasedTokens,
                MAX_REASONABLE_FILE_TOKENS
            )
            
            // Determine confidence based on data quality
            val confidence = when {
                validatedPageCount > 1 && fileSize > 0 -> Confidence.MEDIUM
                validatedPageCount > 0 || fileSize > 0 -> Confidence.LOW
                else -> Confidence.FALLBACK
            }
            
            val details = buildString {
                append("$validatedPageCount pages")
                if (hasImages) append(", images")
                if (hasText) append(", text")
                if (isFormDocument) append(", forms")
                append(" → $estimatedTokens tokens")
            }
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = estimatedTokens,
                fileType = FileType.PDF,
                details = details,
                confidence = confidence
            )
            
        } catch (e: Exception) {
            Timber.w("Hybrid PDF estimation failed, using size fallback: ${e.message}")
            return@withContext estimateByFileSize(uri, FileType.PDF, CHARS_PER_TOKEN_PDF)
        }
    }
    
    /**
     * Estimate page count from PDF header content
     */
    private fun estimatePageCountFromPdfHeader(content: String): Int {
        return try {
            // Multiple patterns for better page count detection
            val patterns = listOf(
                Regex("""/Count\s+(\d+)"""),           // Standard page tree count
                Regex("""/N\s+(\d+)"""),               // Alternative page count
                Regex("""/PageCount\s+(\d+)"""),       // Some PDF generators use this
                Regex("""<<\s*/Count\s+(\d+)"""),      // Count in dictionary
                Regex("""/Kids\s*\[[^\]]*\]""")        // Count kids array (approximate)
            )
            
            // Try each pattern
            for (pattern in patterns) {
                val match = pattern.find(content)
                if (match != null) {
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null && value > 0) {
                        return value
                    }
                }
            }
            
            // Fallback: estimate from /Kids arrays
            val kidsMatches = Regex("""/Kids\s*\[([^\]]*)\]""").findAll(content)
            var maxKidsCount = 0
            for (kidsMatch in kidsMatches) {
                val kidsContent = kidsMatch.groupValues[1]
                val objectCount = kidsContent.split("R").size - 1  // Count object references
                if (objectCount > maxKidsCount) {
                    maxKidsCount = objectCount
                }
            }
            
            return maxOf(maxKidsCount, 0)
            
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Estimate tokens for images (vision models)
     */
    private suspend fun estimateImageTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open image stream")
                
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            val width = options.outWidth
            val height = options.outHeight
            
            if (width <= 0 || height <= 0) {
                return@withContext FileTokenResult.Error("Invalid image dimensions")
            }
            
            // Calculate tiles needed for processing
            val tilesWide = (width + 511) / 512  // Round up division
            val tilesHigh = (height + 511) / 512
            val totalTiles = tilesWide * tilesHigh
            
            // Vision model token calculation
            val estimatedTokens = TOKENS_PER_IMAGE_BASE + (totalTiles * TOKENS_PER_TILE)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = estimatedTokens,
                fileType = FileType.IMAGE,
                details = "${width}x${height} → $totalTiles tiles → $estimatedTokens tokens",
                confidence = Confidence.HIGH
            )
            
        } catch (e: Exception) {
            return@withContext FileTokenResult.Error("Error processing image: ${e.message}", 1000)
        }
    }
    
    /**
     * Estimate tokens for Word documents (using fallback size-based estimation)
     */
    private suspend fun estimateWordDocTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Estimating Word document tokens using size-based approach")
            
            // Use size-based estimation as primary method for now
            return@withContext estimateByFileSize(uri, FileType.WORD_DOC, CHARS_PER_TOKEN_OFFICE)
            
            /* TODO: Enable when Apache POI libraries are available
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open Word document")
                
            val document = XWPFDocument(inputStream)
            val paragraphs = document.paragraphs
            var totalChars = 0
            
            // Extract text from paragraphs
            paragraphs.forEach { paragraph ->
                totalChars += paragraph.text.length
            }
            
            // Add table content
            document.tables.forEach { table ->
                table.rows.forEach { row ->
                    row.tableCells.forEach { cell ->
                        totalChars += cell.text.length
                    }
                }
            }
            
            document.close()
            inputStream.close()
            
            val estimatedTokens = (totalChars / CHARS_PER_TOKEN_OFFICE).toInt()
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = FileType.WORD_DOC,
                details = "$totalChars characters → $cappedTokens tokens",
                confidence = Confidence.MEDIUM
            )
            */
            
        } catch (e: Exception) {
            Timber.w("Word doc estimation failed, using fallback: ${e.message}")
            return@withContext estimateByFileSize(uri, FileType.WORD_DOC, CHARS_PER_TOKEN_OFFICE)
        }
    }
    
    /**
     * Estimate tokens for Excel files (using fallback size-based estimation)
     */
    private suspend fun estimateExcelTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Estimating Excel tokens using size-based approach")
            
            // Use size-based estimation as primary method for now
            return@withContext estimateByFileSize(uri, FileType.EXCEL_SHEET, CHARS_PER_TOKEN_OFFICE)
            
            /* TODO: Enable when Apache POI libraries are available
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open Excel file")
                
            val workbook = XSSFWorkbook(inputStream)
            var totalChars = 0
            var totalCells = 0
            
            // Process all sheets
            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(i)
                sheet.forEach { row ->
                    row.forEach { cell ->
                        val cellText = cell.toString()
                        totalChars += cellText.length
                        totalCells++
                    }
                }
            }
            
            workbook.close()
            inputStream.close()
            
            val estimatedTokens = (totalChars / CHARS_PER_TOKEN_OFFICE).toInt()
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = FileType.EXCEL_SHEET,
                details = "$totalCells cells, $totalChars chars → $cappedTokens tokens",
                confidence = Confidence.MEDIUM
            )
            */
            
        } catch (e: Exception) {
            Timber.w("Excel estimation failed, using fallback: ${e.message}")
            return@withContext estimateByFileSize(uri, FileType.EXCEL_SHEET, CHARS_PER_TOKEN_OFFICE)
        }
    }
    
    /**
     * Estimate tokens for PowerPoint files (using fallback size-based estimation)
     */
    private suspend fun estimatePowerPointTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Estimating PowerPoint tokens using size-based approach")
            
            // Use size-based estimation as primary method for now
            return@withContext estimateByFileSize(uri, FileType.POWERPOINT, CHARS_PER_TOKEN_OFFICE)
            
            /* TODO: Enable when Apache POI libraries are available
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open PowerPoint file")
                
            val slideShow = HSLFSlideShow(inputStream)
            val slides = slideShow.slides
            var totalChars = 0
            
            slides.forEach { slide ->
                slide.shapes.forEach { shape ->
                    totalChars += shape.shapeName?.length ?: 0
                    // Add more text extraction logic here if needed
                }
            }
            
            slideShow.close()
            inputStream.close()
            
            // Fallback estimation based on slide count
            val slideCount = slides.size
            val estimatedChars = maxOf(totalChars, slideCount * POWERPOINT_CHARS_PER_SLIDE)
            val estimatedTokens = (estimatedChars / CHARS_PER_TOKEN_OFFICE).toInt()
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = estimatedTokens,
                fileType = FileType.POWERPOINT,
                details = "$slideCount slides → $estimatedTokens tokens",
                confidence = Confidence.LOW
            )
            */
            
        } catch (e: Exception) {
            Timber.w("PowerPoint estimation failed, using fallback: ${e.message}")
            return@withContext estimateByFileSize(uri, FileType.POWERPOINT, CHARS_PER_TOKEN_OFFICE)
        }
    }
    
    /**
     * Estimate tokens for CSV files
     */
    private suspend fun estimateCsvTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open CSV file")
                
            val content = inputStream.bufferedReader().use { it.readText() }
            val lines = content.lines().filter { it.isNotBlank() }
            val totalChars = content.length
            
            // CSV files are generally more token-efficient due to structure
            val estimatedTokens = (totalChars / (CHARS_PER_TOKEN_PLAIN_TEXT * 0.8)).toInt()
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = FileType.CSV,
                details = "${lines.size} rows, $totalChars chars → $cappedTokens tokens",
                confidence = Confidence.HIGH
            )
            
        } catch (e: Exception) {
            return@withContext FileTokenResult.Error("Error reading CSV: ${e.message}")
        }
    }
    
    /**
     * Estimate tokens for JSON files
     */
    private suspend fun estimateJsonTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open JSON file")
                
            val content = inputStream.bufferedReader().use { it.readText() }
            val charCount = content.length
            
            // JSON has overhead due to structure, but is generally predictable
            val estimatedTokens = (charCount / (CHARS_PER_TOKEN_PLAIN_TEXT * 1.1)).toInt()
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = FileType.JSON,
                details = "$charCount characters → $cappedTokens tokens",
                confidence = Confidence.MEDIUM
            )
            
        } catch (e: Exception) {
            return@withContext FileTokenResult.Error("Error reading JSON: ${e.message}")
        }
    }
    
    /**
     * Estimate tokens for XML files
     */
    private suspend fun estimateXmlTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        return@withContext estimateTextFileTokens(uri).let { result ->
            when (result) {
                is FileTokenResult.Success -> result.copy(
                    fileType = FileType.XML,
                    confidence = Confidence.MEDIUM
                )
                is FileTokenResult.Error -> result
            }
        }
    }
    
    /**
     * Estimate tokens for code files
     */
    private suspend fun estimateCodeFileTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        return@withContext estimateTextFileTokens(uri).let { result ->
            when (result) {
                is FileTokenResult.Success -> result.copy(
                    fileType = FileType.CODE,
                    estimatedTokens = (result.estimatedTokens * 1.2).toInt(), // Code has more tokens due to symbols
                    confidence = Confidence.MEDIUM
                )
                is FileTokenResult.Error -> result
            }
        }
    }
    
    /**
     * Estimate tokens for archive files
     */
    private suspend fun estimateArchiveTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot open archive")
                
            val zipStream = ZipInputStream(inputStream)
            var fileCount = 0
            var entry = zipStream.nextEntry
            
            while (entry != null && fileCount < 100) { // Limit scanning to prevent long processing
                fileCount++
                entry = zipStream.nextEntry
            }
            
            zipStream.close()
            
            // Very rough estimate based on file count
            val estimatedTokens = fileCount * 500 // Assume average 500 tokens per file
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = estimatedTokens,
                fileType = FileType.ARCHIVE,
                details = "$fileCount files → $estimatedTokens tokens (rough estimate)",
                confidence = Confidence.LOW
            )
            
        } catch (e: Exception) {
            return@withContext FileTokenResult.Error("Error processing archive: ${e.message}", 5000)
        }
    }
    
    /**
     * Estimate tokens for unknown file types
     */
    private suspend fun estimateUnknownFileTokens(uri: Uri): FileTokenResult = withContext(Dispatchers.IO) {
        return@withContext estimateByFileSize(uri, FileType.UNKNOWN, CHARS_PER_TOKEN_PLAIN_TEXT * 2.0)
    }
    
    /**
     * Fallback estimation based on file size
     */
    private suspend fun estimateByFileSize(uri: Uri, fileType: FileType, charsPerToken: Double): FileTokenResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: 
                return@withContext FileTokenResult.Error("Cannot determine file size")
                
            val fileSize = inputStream.available()
            inputStream.close()
            
            // Very conservative estimate: assume file size in bytes ≈ character count
            val estimatedTokens = (fileSize / charsPerToken).toInt()
            val cappedTokens = minOf(estimatedTokens, MAX_REASONABLE_FILE_TOKENS)
            
            return@withContext FileTokenResult.Success(
                estimatedTokens = cappedTokens,
                fileType = fileType,
                details = "${fileSize} bytes → $cappedTokens tokens (size-based estimate)",
                confidence = Confidence.LOW
            )
            
        } catch (e: Exception) {
            return@withContext FileTokenResult.Error(
                message = "Size-based estimation failed: ${e.message}",
                fallbackTokens = 2000
            )
        }
    }
    
    /**
     * Estimate tokens for multiple files
     */
    suspend fun estimateMultipleFiles(files: List<Pair<Uri, String?>>): List<FileTokenResult> = withContext(Dispatchers.IO) {
        return@withContext files.map { (uri, fileName) ->
            estimateFileTokens(uri, fileName)
        }
    }
    
    /**
     * Get total estimated tokens for multiple files
     */
    suspend fun getTotalEstimatedTokens(files: List<Pair<Uri, String?>>): Int = withContext(Dispatchers.IO) {
        val results = estimateMultipleFiles(files)
        return@withContext results.sumOf { result ->
            when (result) {
                is FileTokenResult.Success -> result.estimatedTokens
                is FileTokenResult.Error -> result.fallbackTokens
            }
        }
    }
}