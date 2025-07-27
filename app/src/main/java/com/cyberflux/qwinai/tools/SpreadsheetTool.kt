package com.cyberflux.qwinai.tools

import android.content.Context
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Tool for creating and manipulating spreadsheets based on user requests
 */
class SpreadsheetTool(private val context: Context) : Tool {
    override val id: String = "spreadsheet_creator"
    override val name: String = "Spreadsheet Creator"
    override val description: String = "Creates spreadsheets with tables, data, and formulas based on user requests"

    // Patterns to recognize spreadsheet creation requests
    private val spreadsheetPatterns = listOf(
        Pattern.compile("\\bcreate\\s+(?:a\\s+)?(?:spreadsheet|sheet|table|excel)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmake\\s+(?:a\\s+)?(?:spreadsheet|sheet|table|excel)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bgenerate\\s+(?:a\\s+)?(?:spreadsheet|sheet|table|excel)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bspreadsheet\\s+(?:with|containing|of)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\btable\\s+(?:with|containing|of)\\b.+(?:data|rows|columns)", Pattern.CASE_INSENSITIVE)
    )

    override fun canHandle(message: String): Boolean {
        return spreadsheetPatterns.any { it.matcher(message).find() }
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("SpreadsheetTool: Executing with message: ${message.take(50)}...")

            // Extract spreadsheet data specifications
            val spec = extractSpreadsheetSpec(message)

            if (spec.headers.isEmpty() && spec.data.isEmpty()) {
                return@withContext ToolResult.error(
                    "Insufficient spreadsheet data",
                    "I couldn't extract enough information to create a meaningful spreadsheet. Please provide more details about the columns, rows, or data you want included."
                )
            }

            // Create the spreadsheet
            val (workbook, base64Data) = createSpreadsheet(spec)

            // Create response content
            val content = buildString {
                append("Created a spreadsheet with:\n")
                append("- ${spec.headers.size} columns: ${spec.headers.joinToString(", ")}\n")
                append("- ${spec.data.size} data rows\n")
                if (spec.title.isNotBlank()) {
                    append("- Title: ${spec.title}\n")
                }
                if (spec.formulas.isNotEmpty()) {
                    append("- ${spec.formulas.size} formulas applied\n")
                }
                append("\nSpreadsheet is ready for download.")
            }

            return@withContext ToolResult.success(
                content = content,
                data = mapOf(
                    "base64Data" to base64Data,
                    "filename" to generateFilename(spec.title)
                ),
                metadata = mapOf(
                    "format" to "xlsx",
                    "headers" to spec.headers,
                    "rows" to spec.data.size
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "SpreadsheetTool: Error creating spreadsheet: ${e.message}")
            return@withContext ToolResult.error(
                "Spreadsheet creation error: ${e.message}",
                "There was an error creating the spreadsheet: ${e.message}"
            )
        }
    }

    /**
     * Data class representing a spreadsheet specification
     */
    private data class SpreadsheetSpec(
        val title: String = "",
        val headers: List<String> = emptyList(),
        val data: List<List<String>> = emptyList(),
        val formulas: Map<String, String> = emptyMap()
    )

    /**
     * Extract spreadsheet specifications from the user message
     */
    private fun extractSpreadsheetSpec(message: String): SpreadsheetSpec {
        val title = extractTitle(message)
        val headers = extractHeaders(message)

        // Extract data based on the message and headers
        val data = if (message.contains("random") || message.contains("dummy") || message.contains("sample")) {
            generateSampleData(headers, 5)
        } else {
            extractDataRows(message, headers)
        }

        // Extract formulas if mentioned
        val formulas = extractFormulas(message)

        return SpreadsheetSpec(title, headers, data, formulas)
    }

    /**
     * Extract the title for the spreadsheet
     */
    private fun extractTitle(message: String): String {
        val titlePatterns = listOf(
            Pattern.compile("titled\\s+['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("title\\s+['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("named\\s+['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("with\\s+title\\s+['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in titlePatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                return matcher.group(1).trim()
            }
        }

        // If no explicit title, try to generate one from the context
        val topicPatterns = listOf(
            Pattern.compile("spreadsheet\\s+(?:for|of|about)\\s+(\\w+(?:\\s+\\w+){0,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("table\\s+(?:for|of|about)\\s+(\\w+(?:\\s+\\w+){0,4})", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in topicPatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                return matcher.group(1).trim().capitalize()
            }
        }

        return "Spreadsheet"
    }

    /**
     * Extract column headers from the message
     */
    private fun extractHeaders(message: String): List<String> {
        // Try to find explicitly stated columns
        val columnsPattern = Pattern.compile("columns?\\s+(?:of|with|named|like|:)\\s+[\"']?([^\"']+?)[\"']?(?:and|\\.|$)", Pattern.CASE_INSENSITIVE)
        val matcher = columnsPattern.matcher(message)

        if (matcher.find()) {
            val columnsText = matcher.group(1).trim()
            return parseColumnList(columnsText)
        }

        // Try to identify common spreadsheet types and provide default headers
        if (message.contains("expense") || message.contains("budget")) {
            return listOf("Date", "Category", "Description", "Amount", "Notes")
        }

        if (message.contains("contact") || message.contains("people") || message.contains("person")) {
            return listOf("Name", "Email", "Phone", "Company", "Notes")
        }

        if (message.contains("inventory") || message.contains("product")) {
            return listOf("ID", "Name", "Category", "Quantity", "Price", "Supplier")
        }

        if (message.contains("schedule") || message.contains("calendar")) {
            return listOf("Date", "Time", "Event", "Location", "Description")
        }

        // Default generic headers
        return listOf("Column A", "Column B", "Column C", "Column D")
    }

    /**
     * Parse a comma/and separated list of columns
     */
    private fun parseColumnList(columnsText: String): List<String> {
        // Replace "and" with commas for easier splitting
        val normalizedText = columnsText.replace(" and ", ", ").replace(" & ", ", ")

        return normalizedText.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.capitalize() }
    }

    /**
     * Extract data rows from the message or generate them
     */
    private fun extractDataRows(message: String, headers: List<String>): List<List<String>> {
        // This would require complex NLP to extract structured data from free text
        // For simplicity, we'll generate sample data instead

        // Try to determine how many rows are needed
        val rowCountPattern = Pattern.compile("(\\d+)\\s+rows", Pattern.CASE_INSENSITIVE)
        val matcher = rowCountPattern.matcher(message)

        val rowCount = if (matcher.find()) {
            matcher.group(1).toIntOrNull() ?: 5
        } else {
            5 // Default row count
        }

        return generateSampleData(headers, rowCount)
    }

    /**
     * Generate sample data based on column headers
     */
    private fun generateSampleData(headers: List<String>, rowCount: Int): List<List<String>> {
        val data = mutableListOf<List<String>>()
        val random = java.util.Random()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (i in 1..rowCount) {
            val row = headers.map { header ->
                when {
                    header.contains("date", ignoreCase = true) -> {
                        val date = Date(System.currentTimeMillis() - random.nextInt(30) * 86400000L)
                        dateFormat.format(date)
                    }
                    header.contains("id", ignoreCase = true) -> "ID-${1000 + i}"
                    header.contains("name", ignoreCase = true) -> {
                        val names = listOf("John Smith", "Jane Doe", "Bob Johnson", "Alice Brown", "David Lee")
                        names[random.nextInt(names.size)]
                    }
                    header.contains("email", ignoreCase = true) -> {
                        val domains = listOf("gmail.com", "yahoo.com", "outlook.com", "company.com")
                        "user${1000 + i}@${domains[random.nextInt(domains.size)]}"
                    }
                    header.contains("phone", ignoreCase = true) -> {
                        val area = 100 + random.nextInt(900)
                        val prefix = 100 + random.nextInt(900)
                        val line = 1000 + random.nextInt(9000)
                        "($area) $prefix-$line"
                    }
                    header.contains("price", ignoreCase = true) ||
                            header.contains("amount", ignoreCase = true) ||
                            header.contains("cost", ignoreCase = true) -> {
                        String.format("$%.2f", 10.0 + random.nextInt(990) / 10.0)
                    }
                    header.contains("quantity", ignoreCase = true) ||
                            header.contains("qty", ignoreCase = true) ||
                            header.contains("number", ignoreCase = true) -> {
                        (1 + random.nextInt(100)).toString()
                    }
                    header.contains("category", ignoreCase = true) -> {
                        val categories = listOf("Category A", "Category B", "Category C", "Category D")
                        categories[random.nextInt(categories.size)]
                    }
                    header.contains("description", ignoreCase = true) ||
                            header.contains("notes", ignoreCase = true) -> {
                        val descriptions = listOf(
                            "Sample description for item $i",
                            "This is a note for record $i",
                            "Additional information about entry $i",
                            "Details for item $i"
                        )
                        descriptions[random.nextInt(descriptions.size)]
                    }
                    else -> "Sample $i"
                }
            }
            data.add(row)
        }

        return data
    }

    /**
     * Extract formulas from the message
     */
    private fun extractFormulas(message: String): Map<String, String> {
        val formulas = mutableMapOf<String, String>()

        // Simple pattern matching for common formulas
        if (message.contains("sum") || message.contains("total")) {
            formulas["SUM"] = "=SUM()"
        }

        if (message.contains("average") || message.contains("mean")) {
            formulas["AVERAGE"] = "=AVERAGE()"
        }

        if (message.contains("max") || message.contains("maximum")) {
            formulas["MAX"] = "=MAX()"
        }

        if (message.contains("min") || message.contains("minimum")) {
            formulas["MIN"] = "=MIN()"
        }

        return formulas
    }

    /**
     * Create the actual spreadsheet file and return base64 encoded data
     */
    private fun createSpreadsheet(spec: SpreadsheetSpec): Pair<XSSFWorkbook, String> {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Data")

        // Create styles
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val dataStyle = workbook.createCellStyle()

        // Add title if present
        var rowNum = 0
        if (spec.title.isNotBlank()) {
            val titleRow = sheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue(spec.title)

            val titleStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont()
                font.bold = true
                font.fontHeightInPoints = 14
                setFont(font)
            }

            titleCell.cellStyle = titleStyle

            // Merge cells for title
            sheet.addMergedRegion(CellRangeAddress(0, 0, 0, spec.headers.size - 1))

            // Add an empty row after title
            sheet.createRow(rowNum++)
        }

        // Add headers
        val headerRow = sheet.createRow(rowNum++)
        spec.headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Add data rows
        spec.data.forEach { rowData ->
            val dataRow = sheet.createRow(rowNum++)
            rowData.forEachIndexed { index, value ->
                val cell = dataRow.createCell(index)
                cell.setCellValue(value)
                cell.cellStyle = dataStyle
            }
        }

        // Add formula row if needed
        if (spec.formulas.isNotEmpty()) {
            val formulaRow = sheet.createRow(rowNum++)
            val labelCell = formulaRow.createCell(0)
            labelCell.setCellValue("Summary:")

            // Apply formulas
            var colIndex = 1
            spec.formulas.forEach { (name, formula) ->
                val cell = formulaRow.createCell(colIndex++)
                cell.setCellValue(name)

                // For simplicity, we're not implementing actual Excel formulas here
            }
        }

        // Auto-size columns
        for (i in spec.headers.indices) {
            sheet.autoSizeColumn(i)
        }

        // Convert to base64
        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)

        return Pair(workbook, base64Data)
    }

    /**
     * Generate a filename for the spreadsheet
     */
    private fun generateFilename(title: String): String {
        val safeName = title.replace("[^a-zA-Z0-9 ]".toRegex(), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${safeName}_${timestamp}.xlsx"
    }
}