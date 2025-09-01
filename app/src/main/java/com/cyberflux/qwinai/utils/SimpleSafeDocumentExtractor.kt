package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * ULTRA-SIMPLE SAFE Document Extractor
 * 
 * No complex libraries, no crashes, no hangs.
 * Just simple, reliable text extraction that ALWAYS works.
 */
class SimpleSafeDocumentExtractor(private val context: Context) {

    companion object {
        // Simple cache for extracted content
        private val contentCache = ConcurrentHashMap<String, String>()
        
        fun cacheContent(uriString: String, content: String) {
            contentCache[uriString] = content
        }
        
        fun getCachedContent(uriString: String): String? {
            return contentCache[uriString]
        }
        
        fun clearCache() {
            contentCache.clear()
        }
    }

    /**
     * Extract content - guaranteed to complete within 30 seconds
     */
    suspend fun extractContent(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            Timber.i("🚀 SimpleSafeExtractor starting extraction...")
            
            withTimeout(30_000L) {
                val fileName = getFileName(uri)
                val fileSize = getFileSize(uri)
                val mimeType = getMimeType(uri)
                
                Timber.i("📋 File: $fileName, MIME: $mimeType, Size: ${formatBytes(fileSize)}")
                
                val content = when {
                    // Text files - always work
                    fileName.endsWith(".txt", true) || mimeType?.contains("text/plain") == true -> {
                        extractSimpleText(uri, fileName)
                    }
                    
                    // CSV files - simple line-by-line reading
                    fileName.endsWith(".csv", true) || mimeType?.contains("csv") == true -> {
                        extractSimpleCsv(uri, fileName)
                    }
                    
                    // DOCX - simple XML extraction from ZIP
                    fileName.endsWith(".docx", true) -> {
                        extractSimpleDocx(uri, fileName)
                    }
                    
                    // XLSX - simple XML extraction from ZIP  
                    fileName.endsWith(".xlsx", true) -> {
                        extractSimpleXlsx(uri, fileName)
                    }
                    
                    // PDF - provide alternatives
                    fileName.endsWith(".pdf", true) || mimeType?.contains("pdf") == true -> {
                        createPdfAlternatives(fileName, fileSize)
                    }
                    
                    // Everything else
                    else -> {
                        createUnsupportedMessage(fileName, mimeType)
                    }
                }
                
                Timber.i("✅ Extraction completed: ${content.length} chars")
                ExtractionResult.Success(content)
            }
            
        } catch (e: TimeoutCancellationException) {
            Timber.w("⏰ Extraction timed out")
            ExtractionResult.Error("Document processing timed out. Please try a smaller file.")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Extraction failed")
            ExtractionResult.Error("Failed to process document: ${e.message}")
        }
    }

    private suspend fun extractSimpleText(uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        Timber.d("📄 Extracting text file: $fileName")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val content = StringBuilder()
                content.append("📄 Text File: $fileName\n\n")
                
                var lineCount = 0
                reader.forEachLine { line ->
                    if (lineCount < 1000) { // Limit lines to prevent memory issues
                        content.append(line).append("\n")
                        lineCount++
                    }
                }
                
                if (lineCount >= 1000) {
                    content.append("\n[File truncated - showing first 1000 lines]")
                }
                
                content.toString()
            }
        } ?: "⚠️ Could not read text file"
    }

    private suspend fun extractSimpleCsv(uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        Timber.d("📊 Extracting CSV file: $fileName")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val content = StringBuilder()
                content.append("📊 CSV File: $fileName\n\n")
                
                var rowCount = 0
                reader.forEachLine { line ->
                    if (rowCount < 100) { // Limit rows
                        content.append("Row ${rowCount + 1}: $line\n")
                        rowCount++
                    }
                }
                
                if (rowCount >= 100) {
                    content.append("\n[File truncated - showing first 100 rows]")
                }
                
                content.toString()
            }
        } ?: "⚠️ Could not read CSV file"
    }

    private suspend fun extractSimpleDocx(uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        Timber.d("📝 Extracting DOCX file: $fileName")
        
        try {
            // Copy to temp file
            val tempFile = File(context.cacheDir, "temp_docx_${System.currentTimeMillis()}.docx")
            copyUriToFile(uri, tempFile)
            
            val content = StringBuilder()
            content.append("📝 Word Document: $fileName\n\n")
            
            // Extract from ZIP
            ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlContent = String(zipIn.readBytes(), Charsets.UTF_8)
                        
                        // Simple regex to extract text between w:t tags
                        val pattern = "<w:t[^>]*>([^<]+)</w:t>".toRegex()
                        val matches = pattern.findAll(xmlContent)
                        
                        matches.forEach { match ->
                            val text = match.groupValues[1].trim()
                            if (text.isNotBlank()) {
                                content.append(text).append(" ")
                            }
                        }
                        break
                    }
                    entry = zipIn.nextEntry
                }
            }
            
            // Clean up
            tempFile.delete()
            
            val result = content.toString().trim()
            if (result.endsWith(fileName)) {
                // No content extracted
                "$result\n\n⚠️ No readable text found. Document may contain only images or complex formatting."
            } else {
                result
            }
            
        } catch (e: Exception) {
            Timber.w(e, "DOCX extraction failed")
            "📝 Word Document: $fileName\n\n⚠️ Could not extract text. Please try saving as plain text (.txt) format."
        }
    }

    private suspend fun extractSimpleXlsx(uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        Timber.d("📊 Extracting XLSX file: $fileName")
        
        try {
            // Copy to temp file
            val tempFile = File(context.cacheDir, "temp_xlsx_${System.currentTimeMillis()}.xlsx")
            copyUriToFile(uri, tempFile)
            
            val content = StringBuilder()
            content.append("📊 Excel Spreadsheet: $fileName\n\n")
            
            var sheetCount = 0
            
            // Extract from ZIP
            ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                
                while (entry != null && sheetCount < 3) { // Limit to 3 sheets
                    if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                        sheetCount++
                        val xmlContent = String(zipIn.readBytes(), Charsets.UTF_8)
                        
                        content.append("=== Sheet $sheetCount ===\n")
                        
                        // Simple regex to extract cell values
                        val valuePattern = "<v>([^<]+)</v>".toRegex()
                        val values = mutableListOf<String>()
                        
                        valuePattern.findAll(xmlContent).forEach { match ->
                            if (values.size < 500) { // Limit values
                                values.add(match.groupValues[1])
                            }
                        }
                        
                        // Display as rows (10 values per row)
                        values.chunked(10).take(20).forEachIndexed { rowIndex, rowValues ->
                            content.append("Row ${rowIndex + 1}: ${rowValues.joinToString(" | ")}\n")
                        }
                        
                        if (values.size > 200) {
                            content.append("[Additional values truncated]\n")
                        }
                        content.append("\n")
                    }
                    entry = zipIn.nextEntry
                }
            }
            
            // Clean up
            tempFile.delete()
            
            content.toString()
            
        } catch (e: Exception) {
            Timber.w(e, "XLSX extraction failed")
            "📊 Excel Spreadsheet: $fileName\n\n⚠️ Could not extract data. Please try saving as CSV format."
        }
    }

    private fun createPdfAlternatives(fileName: String, fileSize: Long): String {
        return """
            📄 PDF Document: $fileName
            Size: ${formatBytes(fileSize)}
            
            ❌ PDF text extraction is not supported on this device.
            
            🔧 ALTERNATIVES:
            
            • Convert to Word (.docx) using Google Drive or Office apps
            • Copy text manually from PDF viewer and paste in chat
            • Use online PDF-to-text converters
            • Save as plain text (.txt) format
            
            The AI can analyze any text you provide through these methods!
        """.trimIndent()
    }

    private fun createUnsupportedMessage(fileName: String, mimeType: String?): String {
        return """
            ⚠️ Unsupported File: $fileName
            Type: ${mimeType ?: "Unknown"}
            
            Supported formats:
            • Text files (.txt)
            • CSV files (.csv)  
            • Word documents (.docx)
            • Excel spreadsheets (.xlsx)
            
            Please convert your file to one of these supported formats.
        """.trimIndent()
    }

    private fun copyUriToFile(uri: Uri, file: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot copy file")
    }

    private fun getFileName(uri: Uri): String {
        return FileUtil.getFileNameFromUri(context, uri) ?: "unknown_file"
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            FileUtil.getFileSizeFromUri(context, uri)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    private fun formatBytes(bytes: Long): String {
        return FileUtil.formatFileSize(bytes)
    }

    sealed class ExtractionResult {
        data class Success(val content: String) : ExtractionResult()
        data class Error(val message: String) : ExtractionResult()
    }
}