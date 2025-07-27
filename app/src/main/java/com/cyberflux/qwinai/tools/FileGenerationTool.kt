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
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.opencsv.CSVWriter
// PDFBox imports removed due to compatibility issues
// import com.tom_roush.pdfbox.pdmodel.PDDocument
// import com.tom_roush.pdfbox.pdmodel.PDPage
// import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
// import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
// import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
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
            bold = formatting["bold"] as? Boolean ?: false,
            italic = formatting["italic"] as? Boolean ?: false,
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

    fun getToolDefinition(): JsonObject {
        return JsonParser.parseString("""
        {
            "type": "function",
            "function": {
                "name": "$id",
                "description": "$description",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "enum": ["pdf", "docx", "xlsx", "csv", "txt", "json", "xml"],
                            "description": "Type of file to generate"
                        },
                        "title": {
                            "type": "string",
                            "description": "Document title"
                        },
                        "content": {
                            "type": "string",
                            "description": "Main content for text-based documents"
                        },
                        "data": {
                            "type": "array",
                            "items": {
                                "type": "object"
                            },
                            "description": "Data rows for spreadsheets and structured documents"
                        },
                        "headers": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            },
                            "description": "Column headers for spreadsheets"
                        },
                        "formatting": {
                            "type": "object",
                            "properties": {
                                "fontSize": {"type": "number", "default": 12},
                                "fontFamily": {"type": "string", "default": "Arial"},
                                "textColor": {"type": "string", "default": "#000000"},
                                "backgroundColor": {"type": "string", "default": "#FFFFFF"},
                                "bold": {"type": "boolean", "default": false},
                                "italic": {"type": "boolean", "default": false},
                                "alignment": {"type": "string", "enum": ["LEFT", "CENTER", "RIGHT", "JUSTIFY"], "default": "LEFT"},
                                "pageSize": {"type": "string", "enum": ["A4", "LETTER", "LEGAL"], "default": "A4"},
                                "orientation": {"type": "string", "enum": ["PORTRAIT", "LANDSCAPE"], "default": "PORTRAIT"}
                            }
                        },
                        "metadata": {
                            "type": "object",
                            "properties": {
                                "author": {"type": "string", "default": "Qwin AI"},
                                "subject": {"type": "string"},
                                "keywords": {"type": "string"},
                                "description": {"type": "string"}
                            }
                        }
                    },
                    "required": ["type"]
                }
            }
        }
        """).asJsonObject
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
            val fileName = "${request.title.ifEmpty { "document" }}_${getTimestamp()}.docx"
            val file = createFile(fileName)
            
            val document = XWPFDocument()
            
            // Set metadata
            val properties = document.properties
            val coreProps = properties.coreProperties
            coreProps.setCreator(request.metadata.author)
            coreProps.setTitle(request.title)
            coreProps.setSubjectProperty(request.metadata.subject)
            coreProps.setKeywords(request.metadata.keywords)
            coreProps.setDescription(request.metadata.description)
            
            // Add title
            if (request.title.isNotEmpty()) {
                val titleParagraph = document.createParagraph()
                titleParagraph.alignment = getAlignment(request.formatting.alignment)
                val titleRun = titleParagraph.createRun()
                titleRun.setText(request.title)
                titleRun.fontSize = (request.formatting.fontSize + 4).toInt()
                titleRun.isBold = true
                titleRun.fontFamily = request.formatting.fontFamily
            }
            
            // Add content
            if (request.content.isNotEmpty()) {
                val contentParagraph = document.createParagraph()
                contentParagraph.alignment = getAlignment(request.formatting.alignment)
                val contentRun = contentParagraph.createRun()
                contentRun.setText(request.content)
                contentRun.fontSize = request.formatting.fontSize.toInt()
                contentRun.isBold = request.formatting.bold
                contentRun.isItalic = request.formatting.italic
                contentRun.fontFamily = request.formatting.fontFamily
            }
            
            // Add data table if provided
            if (request.data.isNotEmpty() && request.headers.isNotEmpty()) {
                addTableToDOCX(document, request.data, request.headers, request.formatting)
            }
            
            val outputStream = FileOutputStream(file)
            document.write(outputStream)
            outputStream.close()
            document.close()
            
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            
            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_DOCX,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating DOCX", e)
            FileGenerationResult(success = false, error = "DOCX generation failed: ${e.message}")
        }
    }

    private fun generateXLSX(request: DocumentRequest): FileGenerationResult {
        return try {
            val fileName = "${request.title.ifEmpty { "spreadsheet" }}_${getTimestamp()}.xlsx"
            val file = createFile(fileName)
            
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet(request.title.ifEmpty { "Sheet1" })
            
            // Create header style
            val headerStyle = workbook.createCellStyle()
            val headerFont = workbook.createFont()
            headerFont.bold = request.formatting.bold
            headerFont.fontHeightInPoints = request.formatting.fontSize.toInt().toShort()
            headerStyle.setFont(headerFont)
            headerStyle.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
            
            // Create data style
            val dataStyle = workbook.createCellStyle()
            val dataFont = workbook.createFont()
            dataFont.fontHeightInPoints = request.formatting.fontSize.toInt().toShort()
            dataFont.italic = request.formatting.italic
            dataStyle.setFont(dataFont)
            
            var rowIndex = 0
            
            // Add title if provided
            if (request.title.isNotEmpty()) {
                val titleRow = sheet.createRow(rowIndex++)
                val titleCell = titleRow.createCell(0)
                titleCell.setCellValue(request.title)
                val titleStyle = workbook.createCellStyle()
                val titleFont = workbook.createFont()
                titleFont.bold = true
                titleFont.fontHeightInPoints = (request.formatting.fontSize + 4).toInt().toShort()
                titleStyle.setFont(titleFont)
                titleCell.cellStyle = titleStyle
                rowIndex++ // Empty row after title
            }
            
            // Add headers if provided
            if (request.headers.isNotEmpty()) {
                val headerRow = sheet.createRow(rowIndex++)
                request.headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }
            }
            
            // Add data
            request.data.forEach { dataRow ->
                val row = sheet.createRow(rowIndex++)
                if (request.headers.isNotEmpty()) {
                    request.headers.forEachIndexed { index, header ->
                        val cell = row.createCell(index)
                        val value = dataRow[header]
                        when (value) {
                            is Number -> cell.setCellValue(value.toDouble())
                            is Boolean -> cell.setCellValue(value)
                            else -> cell.setCellValue(value?.toString() ?: "")
                        }
                        cell.cellStyle = dataStyle
                    }
                } else {
                    dataRow.values.forEachIndexed { index, value ->
                        val cell = row.createCell(index)
                        when (value) {
                            is Number -> cell.setCellValue(value.toDouble())
                            is Boolean -> cell.setCellValue(value)
                            else -> cell.setCellValue(value?.toString() ?: "")
                        }
                        cell.cellStyle = dataStyle
                    }
                }
            }
            
            // Auto-size columns
            repeat(if (request.headers.isNotEmpty()) request.headers.size else (request.data.firstOrNull()?.size ?: 0)) { index ->
                sheet.autoSizeColumn(index)
            }
            
            val outputStream = FileOutputStream(file)
            workbook.write(outputStream)
            outputStream.close()
            workbook.close()
            
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            
            FileGenerationResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = file.length(),
                mimeType = MIME_XLSX,
                uri = uri
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating XLSX", e)
            FileGenerationResult(success = false, error = "XLSX generation failed: ${e.message}")
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
            
            val jsonObject = JsonObject().apply {
                if (request.title.isNotEmpty()) {
                    addProperty("title", request.title)
                }
                if (request.content.isNotEmpty()) {
                    addProperty("content", request.content)
                }
                addProperty("generated_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()))
                addProperty("generated_by", request.metadata.creator)
                
                if (request.data.isNotEmpty()) {
                    add("data", Gson().toJsonTree(request.data))
                }
                if (request.headers.isNotEmpty()) {
                    add("headers", Gson().toJsonTree(request.headers))
                }
                
                val metadataObj = JsonObject().apply {
                    addProperty("author", request.metadata.author)
                    addProperty("subject", request.metadata.subject)
                    addProperty("keywords", request.metadata.keywords)
                    addProperty("description", request.metadata.description)
                }
                add("metadata", metadataObj)
            }
            
            file.writeText(Gson().toJson(jsonObject))
            
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

    private fun getAlignment(alignment: String): org.apache.poi.xwpf.usermodel.ParagraphAlignment {
        return when (alignment.uppercase()) {
            "CENTER" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
            "RIGHT" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT
            "JUSTIFY" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH
            else -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT
        }
    }

    // PDFBox wrapText method removed due to compatibility issues

    // PDFBox addTableToPDF method removed due to compatibility issues

    private fun addTableToDOCX(
        document: XWPFDocument,
        data: List<Map<String, Any>>,
        headers: List<String>,
        formatting: DocumentFormatting
    ) {
        val table = document.createTable()
        
        // Add header row
        val headerRow = table.getRow(0)
        headers.forEachIndexed { index, header ->
            if (index == 0) {
                headerRow.getCell(0).text = header
            } else {
                headerRow.addNewTableCell().text = header
            }
        }
        
        // Add data rows
        data.forEach { dataRow ->
            val row = table.createRow()
            headers.forEachIndexed { index, header ->
                row.getCell(index).text = dataRow[header]?.toString() ?: ""
            }
        }
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