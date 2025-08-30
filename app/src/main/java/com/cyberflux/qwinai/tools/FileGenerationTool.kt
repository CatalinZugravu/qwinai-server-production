package com.cyberflux.qwinai.tools

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.utils.JsonUtils
import org.json.JSONObject
import com.opencsv.CSVWriter
// PDFBox imports removed due to compatibility issues
// import com.tom_roush.pdfbox.pdmodel.PDDocument
// import com.tom_roush.pdfbox.pdmodel.PDPage
// import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
// import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
// import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// Apache POI imports removed - using simplified alternatives for compatibility
// Word and Excel files will be created using Android-native or CSV alternatives
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

data class DocumentRequest(
    val type: String, // pdf, docx, xlsx, csv, txt, json, xml
    val title: String = "",
    val content: String = "",
    val data: List<Map<String, Any>> = emptyList(),
    val headers: List<String> = emptyList(),
    val formatting: DocumentFormatting = DocumentFormatting(),
    val metadata: DocumentMetadata = DocumentMetadata()
)

data class DocumentFormatting(
    val fontSize: Float = 12f,
    val fontFamily: String = "Arial",
    val textColor: String = "#000000",
    val backgroundColor: String = "#FFFFFF",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: String = "LEFT", // LEFT, CENTER, RIGHT, JUSTIFY
    val margins: DocumentMargins = DocumentMargins(),
    val pageSize: String = "A4", // A4, LETTER, LEGAL
    val orientation: String = "PORTRAIT" // PORTRAIT, LANDSCAPE
)

data class DocumentMargins(
    val top: Float = 72f,
    val bottom: Float = 72f,
    val left: Float = 72f,
    val right: Float = 72f
)

data class DocumentMetadata(
    val author: String = "Qwin AI",
    val subject: String = "",
    val keywords: String = "",
    val description: String = "",
    val creator: String = "Qwin AI Document Generator"
)

data class FileGenerationResult(
    val success: Boolean,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val mimeType: String? = null,
    val error: String? = null,
    val uri: Uri? = null
)

class FileGenerationTool(private val context: Context) : Tool {

    override val id: String = "file_generation"
    override val name: String = "File Generation"
    override val description: String = "Generate various file formats including PDF, DOCX, XLSX, CSV, TXT, JSON, XML with customizable formatting and content"

    companion object {
        private const val TAG = "FileGenerationTool"

        const val MIME_PDF = "application/pdf"
        const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val MIME_CSV = "text/csv"
        const val MIME_TXT = "text/plain"
        const val MIME_JSON = "application/json"
        const val MIME_XML = "application/xml"
    }

    override fun canHandle(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val fileGenerationKeywords = listOf(
            "generate", "create", "make", "export", "produce", "build",
            "pdf", "docx", "xlsx", "csv", "txt", "json", "xml",
            "document", "file", "spreadsheet", "report", "table"
        )

        return fileGenerationKeywords.any { keyword ->
            lowerMessage.contains(keyword)
        } && (lowerMessage.contains("file") ||
              lowerMessage.contains("document") ||
              lowerMessage.contains("pdf") ||
              lowerMessage.contains("docx") ||
              lowerMessage.contains("xlsx") ||
              lowerMessage.contains("csv") ||
              lowerMessage.contains("export"))
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult {
        return try {
            // Parse parameters or use defaults
            val fileType = parameters["type"]?.toString()?.lowercase() ?: "pdf"
            val title = parameters["title"]?.toString() ?: "Generated Document"
            val content = parameters["content"]?.toString() ?: message
            val data = parameters["data"] as? List<Map<String, Any>> ?: emptyList()
            val headers = parameters["headers"] as? List<String> ?: emptyList()

            val request = DocumentRequest(
                type = fileType,
                title = title,
                content = content,
                data = data,
                headers = headers,
                formatting = parseFormatting(parameters),
                metadata = parseMetadata(parameters)
            )

            val result = generateFile(request)

            if (result.success) {
                ToolResult.success(
                    content = formatSuccessMessage(result),
                    data = result,
                    metadata = buildMap {
                        result.filePath?.let { put("file_path", it) }
                        result.fileName?.let { put("file_name", it) }
                        put("file_size", result.fileSize)
                        result.mimeType?.let { put("mime_type", it) }
                        result.uri?.toString()?.let { put("uri", it) }
                        put("show_download_card", true)
                        put("file_generation_result", result)
                    }
                )
            } else {
                ToolResult.error(
                    errorMessage = result.error ?: "Unknown error occurred",
                    content = "Failed to generate file: ${result.error}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in execute", e)
            ToolResult.error(
                errorMessage = "Tool execution failed: ${e.message}",
                content = "Error generating file: ${e.message}"
            )
        }
    }

    private fun parseFormatting(parameters: Map<String, Any>): DocumentFormatting {
        val formatting = parameters["formatting"] as? Map<String, Any> ?: emptyMap()
        return DocumentFormatting(
            fontSize = (formatting["fontSize"] as? Number)?.toFloat() ?: 12f,
            fontFamily = formatting["fontFamily"]?.toString() ?: "Arial",
            textColor = formatting["textColor"]?.toString() ?: "#000000",
            backgroundColor = formatting["backgroundColor"]?.toString() ?: "#FFFFFF",
            bold = formatting["bold"] as? Boolean == true,
            italic = formatting["italic"] as? Boolean == true,
            alignment = formatting["alignment"]?.toString() ?: "LEFT",
            pageSize = formatting["pageSize"]?.toString() ?: "A4",
            orientation = formatting["orientation"]?.toString() ?: "PORTRAIT"
        )
    }

    private fun parseMetadata(parameters: Map<String, Any>): DocumentMetadata {
        val metadata = parameters["metadata"] as? Map<String, Any> ?: emptyMap()
        return DocumentMetadata(
            author = metadata["author"]?.toString() ?: "Qwin AI",
            subject = metadata["subject"]?.toString() ?: "",
            keywords = metadata["keywords"]?.toString() ?: "",
            description = metadata["description"]?.toString() ?: "",
            creator = metadata["creator"]?.toString() ?: "Qwin AI Document Generator"
        )
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }

    private fun formatSuccessMessage(result: FileGenerationResult): String {
        val fileExtension = result.fileName?.substringAfterLast(".", "")?.uppercase() ?: "FILE"
        return buildString {
            append("✅ **File Generated Successfully!**\n\n")
            append("📄 **${result.fileName}**\n")
            append("📊 Size: ${formatFileSize(result.fileSize)}\n")
            append("🗂️ Type: $fileExtension\n\n")
            append("Click the download card below to open or share your file.")
        }
    }

    fun getToolDefinition(): Map<String, Any> {
        return mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to id,
                "description" to description,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "type" to mapOf(
                            "type" to "string",
                            "enum" to listOf("pdf", "docx", "xlsx", "csv", "txt", "json", "xml"),
                            "description" to "Type of file to generate"
                        ),
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Document title"
                        ),
                        "content" to mapOf(
                            "type" to "string",
                            "description" to "Main content for text-based documents"
                        ),
                        "data" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object"
                            ),
                            "description" to "Data rows for spreadsheets and structured documents"
                        ),
                        "headers" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "string"
                            ),
                            "description" to "Column headers for spreadsheets"
                        ),
                        "formatting" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "fontSize" to mapOf("type" to "number", "default" to 12),
                                "fontFamily" to mapOf("type" to "string", "default" to "Arial"),
                                "textColor" to mapOf("type" to "string", "default" to "#000000"),
                                "backgroundColor" to mapOf("type" to "string", "default" to "#FFFFFF"),
                                "bold" to mapOf("type" to "boolean", "default" to false),
                                "italic" to mapOf("type" to "boolean", "default" to false),
                                "alignment" to mapOf("type" to "string", "enum" to listOf("LEFT", "CENTER", "RIGHT", "JUSTIFY"), "default" to "LEFT"),
                                "pageSize" to mapOf("type" to "string", "enum" to listOf("A4", "LETTER", "LEGAL"), "default" to "A4"),
                                "orientation" to mapOf("type" to "string", "enum" to listOf("PORTRAIT", "LANDSCAPE"), "default" to "PORTRAIT")
                            )
                        ),
                        "metadata" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "author" to mapOf("type" to "string", "default" to "Qwin AI"),
                                "subject" to mapOf("type" to "string"),
                                "keywords" to mapOf("type" to "string"),
                                "description" to mapOf("type" to "string")
                            )
                        )
                    ),
                    "required" to listOf("type")
                )
            )
        )
    }

    suspend fun generateFile(request: DocumentRequest): FileGenerationResult {
        return withContext(Dispatchers.IO) {
            try {
                when (request.type.lowercase()) {
                    "pdf" -> generatePDF(request)
                    "docx" -> generateDOCX(request)
                    "xlsx" -> generateXLSX(request)
                    "csv" -> generateCSV(request)
                    "txt" -> generateTXT(request)
                    "json" -> generateJSON(request)
                    "xml" -> generateXML(request)
                    else -> FileGenerationResult(
                        success = false,
                        error = "Unsupported file type: ${request.type}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating file", e)
                FileGenerationResult(
                    success = false,
                    error = "Failed to generate file: ${e.message}"
                )
            }
        }
    }

    private fun generatePDF(request: DocumentRequest): FileGenerationResult {
        // PDF generation is disabled due to PDFBox compatibility issues
        // The PDFBox library causes NumberFormatException on some devices
        Log.w(TAG, "PDF generation disabled due to PDFBox compatibility issues")
        return FileGenerationResult(
            success = false,
            error = "PDF generation is temporarily disabled due to device compatibility issues. Please use DOCX or TXT format instead."
        )
    }

    private fun generateDOCX(request: DocumentRequest): FileGenerationResult {
        return try {
            // Convert DOCX request to structured TXT format for compatibility
            val fileName = "${request.title.ifEmpty { "document" }}_${getTimestamp()}.txt"
            val file = createFile(fileName)

            val content = buildString {
                // Add metadata as header
                appendLine("=".repeat(50))
                appendLine("DOCUMENT: ${request.title}")
                appendLine("Author: ${request.metadata.author}")
                appendLine("Subject: ${request.metadata.subject}")
                appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("=".repeat(50))
                appendLine()

                // Add title
                if (request.title.isNotEmpty()) {
                    appendLine(request.title.uppercase())
                    appendLine("=".repeat(request.title.length))
                    appendLine()
                }

                // Add content
                if (request.content.isNotEmpty()) {
                    appendLine(request.content)
                    appendLine()
                }

                // Add table if data is present
                if (request.data.isNotEmpty() && request.headers.isNotEmpty()) {
                    appendLine("DATA TABLE:")
                    appendLine("-".repeat(50))
                    
                    // Headers
                    append(request.headers.joinToString(" | "))
                    appendLine()
                    appendLine("-".repeat(request.headers.joinToString(" | ").length))
                    
                    // Data rows
                    request.data.forEach { row ->
                        val rowValues = request.headers.map { header -> 
                            row[header]?.toString() ?: "" 
                        }
                        appendLine(rowValues.joinToString(" | "))
                    }
                    appendLine()
                }

                // Add footer
                appendLine("-".repeat(50))
                appendLine("Generated by QwinAI File Generator")
                appendLine("Note: This file was created in TXT format for maximum compatibility")
                appendLine("Original request was for DOCX format")
            }

            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = "text/plain",
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating document", e)
            FileGenerationResult(success = false, error = "Document generation failed: ${e.message}")
        }
    }

    private fun generateXLSX(request: DocumentRequest): FileGenerationResult {
        return try {
            // Convert XLSX request to CSV format for compatibility
            val fileName = "${request.title.ifEmpty { "spreadsheet" }}_${getTimestamp()}.csv"
            val file = createFile(fileName)

            val fileWriter = FileWriter(file)
            val csvWriter = CSVWriter(fileWriter)

            // Add title as comment if provided
            if (request.title.isNotEmpty()) {
                csvWriter.writeNext(arrayOf("# ${request.title} (Converted from XLSX format for compatibility)"))
                csvWriter.writeNext(arrayOf("")) // Empty row
            }

            // Add headers if provided
            if (request.headers.isNotEmpty()) {
                csvWriter.writeNext(request.headers.toTypedArray())
            }

            // Add data
            request.data.forEach { dataRow ->
                if (request.headers.isNotEmpty()) {
                    val rowData = request.headers.map { header ->
                        dataRow[header]?.toString() ?: ""
                    }.toTypedArray()
                    csvWriter.writeNext(rowData)
                } else {
                    val rowData = dataRow.values.map { it?.toString() ?: "" }.toTypedArray()
                    csvWriter.writeNext(rowData)
                }
            }

            csvWriter.close()
            fileWriter.close()

            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_CSV,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating spreadsheet (CSV format)", e)
            FileGenerationResult(success = false, error = "Spreadsheet generation failed: ${e.message}")
        }
    }

    private fun generateCSV(request: DocumentRequest): FileGenerationResult {
        return try {
            val fileName = "${request.title.ifEmpty { "data" }}_${getTimestamp()}.csv"
            val file = createFile(fileName)

            val fileWriter = FileWriter(file)
            val csvWriter = CSVWriter(fileWriter)

            // Add title as comment if provided
            if (request.title.isNotEmpty()) {
                csvWriter.writeNext(arrayOf("# ${request.title}"))
            }

            // Add headers if provided
            if (request.headers.isNotEmpty()) {
                csvWriter.writeNext(request.headers.toTypedArray())
            }

            // Add data
            request.data.forEach { dataRow ->
                if (request.headers.isNotEmpty()) {
                    val rowData = request.headers.map { header ->
                        dataRow[header]?.toString() ?: ""
                    }.toTypedArray()
                    csvWriter.writeNext(rowData)
                } else {
                    val rowData = dataRow.values.map { it?.toString() ?: "" }.toTypedArray()
                    csvWriter.writeNext(rowData)
                }
            }

            csvWriter.close()
            fileWriter.close()

            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_CSV,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating CSV", e)
            FileGenerationResult(success = false, error = "CSV generation failed: ${e.message}")
        }
    }

    private fun generateTXT(request: DocumentRequest): FileGenerationResult {
        return try {
            val fileName = "${request.title.ifEmpty { "text" }}_${getTimestamp()}.txt"
            val file = createFile(fileName)

            val content = buildString {
                if (request.title.isNotEmpty()) {
                    append(request.title)
                    append("\n")
                    append("=".repeat(request.title.length))
                    append("\n\n")
                }

                if (request.content.isNotEmpty()) {
                    append(request.content)
                    append("\n\n")
                }

                if (request.data.isNotEmpty()) {
                    if (request.headers.isNotEmpty()) {
                        append(request.headers.joinToString("\t"))
                        append("\n")
                        append("-".repeat(request.headers.joinToString("\t").length))
                        append("\n")

                        request.data.forEach { dataRow ->
                            val rowData = request.headers.map { header ->
                                dataRow[header]?.toString() ?: ""
                            }
                            append(rowData.joinToString("\t"))
                            append("\n")
                        }
                    } else {
                        request.data.forEach { dataRow ->
                            append(dataRow.values.joinToString("\t") { it?.toString() ?: "" })
                            append("\n")
                        }
                    }
                }

                append("\n")
                append("Generated by ${request.metadata.creator} on ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            }

            file.writeText(content)

            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_TXT,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating TXT", e)
            FileGenerationResult(success = false, error = "TXT generation failed: ${e.message}")
        }
    }

    private fun generateJSON(request: DocumentRequest): FileGenerationResult {
        return try {
            val fileName = "${request.title.ifEmpty { "data" }}_${getTimestamp()}.json"
            val file = createFile(fileName)

            val jsonMap = mutableMapOf<String, Any>()
            
            if (request.title.isNotEmpty()) {
                jsonMap["title"] = request.title
            }
            if (request.content.isNotEmpty()) {
                jsonMap["content"] = request.content
            }
            jsonMap["generated_at"] = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
            jsonMap["generated_by"] = request.metadata.creator

            if (request.data.isNotEmpty()) {
                jsonMap["data"] = request.data
            }
            if (request.headers.isNotEmpty()) {
                jsonMap["headers"] = request.headers
            }

            jsonMap["metadata"] = mapOf(
                "author" to request.metadata.author,
                "subject" to request.metadata.subject,
                "keywords" to request.metadata.keywords,
                "description" to request.metadata.description
            )

            file.writeText(JsonUtils.toJson(jsonMap))

            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_JSON,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating JSON", e)
            FileGenerationResult(success = false, error = "JSON generation failed: ${e.message}")
        }
    }

    private fun generateXML(request: DocumentRequest): FileGenerationResult {
        return try {
            val fileName = "${request.title.ifEmpty { "data" }}_${getTimestamp()}.xml"
            val file = createFile(fileName)

            val xml = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                append("<document>\n")

                if (request.title.isNotEmpty()) {
                    append("  <title>${escapeXml(request.title)}</title>\n")
                }

                if (request.content.isNotEmpty()) {
                    append("  <content>${escapeXml(request.content)}</content>\n")
                }

                append("  <metadata>\n")
                append("    <author>${escapeXml(request.metadata.author)}</author>\n")
                append("    <subject>${escapeXml(request.metadata.subject)}</subject>\n")
                append("    <keywords>${escapeXml(request.metadata.keywords)}</keywords>\n")
                append("    <description>${escapeXml(request.metadata.description)}</description>\n")
                append("    <creator>${escapeXml(request.metadata.creator)}</creator>\n")
                append("    <generated_at>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())}</generated_at>\n")
                append("  </metadata>\n")

                if (request.data.isNotEmpty()) {
                    append("  <data>\n")
                    request.data.forEach { dataRow ->
                        append("    <row>\n")
                        dataRow.forEach { (key, value) ->
                            append("      <${escapeXml(key)}>${escapeXml(value?.toString() ?: "")}</${escapeXml(key)}>\n")
                        }
                        append("    </row>\n")
                    }
                    append("  </data>\n")
                }

                append("</document>\n")
            }

            file.writeText(xml)

            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_XML,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating XML", e)
            FileGenerationResult(success = false, error = "XML generation failed: ${e.message}")
        }
    }

    // Helper functions
    private fun createFile(fileName: String): File {
        val documentsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Generated")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
        return File(documentsDir, fileName)
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    // PDFBox getPageSize method removed due to compatibility issues

    // Apache POI alignment method removed - now using text-based formatting
    private fun getTextAlignment(alignment: String): String {
        return when (alignment.uppercase()) {
            "CENTER" -> "CENTER"
            "RIGHT" -> "RIGHT"
            "JUSTIFY" -> "JUSTIFY"
            else -> "LEFT"
        }
    }

    // PDFBox wrapText method removed due to compatibility issues

    // PDFBox addTableToPDF method removed due to compatibility issues

    // Apache POI table method removed - tables now rendered as formatted text
    private fun addTableToText(
        data: List<Map<String, Any>>,
        headers: List<String>
    ): String {
        val stringBuilder = StringBuilder()
        
        if (headers.isNotEmpty()) {
            // Add headers
            stringBuilder.append(headers.joinToString(" | "))
            stringBuilder.append("\n")
            stringBuilder.append("-".repeat(headers.joinToString(" | ").length))
            stringBuilder.append("\n")
            
            // Add data rows
            data.forEach { dataRow ->
                val rowValues = headers.map { header -> 
                    dataRow[header]?.toString() ?: "" 
                }
                stringBuilder.append(rowValues.joinToString(" | "))
                stringBuilder.append("\n")
            }
        }
        
        return stringBuilder.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun openFile(result: FileGenerationResult) {
        result.uri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, result.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "No app found to open file type: ${result.mimeType}")
                // Fallback to share intent
                shareFile(result)
            }
        }
    }

    fun shareFile(result: FileGenerationResult) {
        result.uri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = result.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share ${result.fileName}"))
        }
    }
}