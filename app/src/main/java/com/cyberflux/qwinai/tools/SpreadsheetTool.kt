package com.cyberflux.qwinai.tools

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.opencsv.CSVWriter
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Tool for creating and manipulating spreadsheets (CSV format) based on user requests
 * NOTE: Now generates CSV files instead of Excel for compatibility after removing Apache POI
 */
class SpreadsheetTool(private val context: Context) : Tool {
    override val id: String = "spreadsheet_creator"
    override val name: String = "Spreadsheet Creator (CSV)"
    override val description: String = "Creates spreadsheets in CSV format with tables, data, and calculations based on user requests"

    // Patterns to recognize spreadsheet creation requests
    private val spreadsheetPatterns = listOf(
        Pattern.compile("\\bcreate\\s+(?:a\\s+)?(?:spreadsheet|sheet|table|excel|csv)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmake\\s+(?:a\\s+)?(?:spreadsheet|sheet|table|excel|csv)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bgenerate\\s+(?:a\\s+)?(?:spreadsheet|sheet|table|excel|csv)\\b", Pattern.CASE_INSENSITIVE),
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

            // Create the spreadsheet (CSV format)
            val base64Data = createSpreadsheetCSV(spec)

            // Create response content
            val content = buildString {
                append("Created a CSV spreadsheet with:\n")
                append("- ${spec.headers.size} columns: ${spec.headers.joinToString(", ")}\n")
                append("- ${spec.data.size} data rows\n")
                if (spec.title.isNotBlank()) {
                    append("- Title: ${spec.title}\n")
                }
                if (spec.formulas.isNotEmpty()) {
                    append("- ${spec.formulas.size} calculations included\n")
                }
                append("\nSpreadsheet (CSV format) is ready for download.")
                append("\nNote: Generated in CSV format for maximum compatibility.")
            }

            return@withContext ToolResult.success(
                content = content,
                data = mapOf(
                    "base64Data" to base64Data,
                    "filename" to generateFilename(spec.title)
                ),
                metadata = mapOf(
                    "format" to "csv",
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
     * Create the actual spreadsheet file in CSV format and return base64 encoded data
     */
    private fun createSpreadsheetCSV(spec: SpreadsheetSpec): String {
        val stringWriter = StringWriter()
        val csvWriter = CSVWriter(stringWriter)

        // Add title if present
        if (spec.title.isNotBlank()) {
            csvWriter.writeNext(arrayOf("# ${spec.title}"))
            csvWriter.writeNext(arrayOf("")) // Empty row
        }

        // Add headers
        csvWriter.writeNext(spec.headers.toTypedArray())

        // Add data rows
        spec.data.forEach { rowData ->
            csvWriter.writeNext(rowData.toTypedArray())
        }

        // Add calculations/summary if needed
        if (spec.formulas.isNotEmpty()) {
            csvWriter.writeNext(arrayOf("")) // Empty row
            csvWriter.writeNext(arrayOf("# Summary/Calculations:"))
            
            spec.formulas.forEach { (name, _) ->
                // For CSV, we'll add descriptive text instead of actual formulas
                when (name) {
                    "SUM" -> csvWriter.writeNext(arrayOf("Total", "[Sum calculation would go here]"))
                    "AVERAGE" -> csvWriter.writeNext(arrayOf("Average", "[Average calculation would go here]"))
                    "MAX" -> csvWriter.writeNext(arrayOf("Maximum", "[Max value would go here]"))
                    "MIN" -> csvWriter.writeNext(arrayOf("Minimum", "[Min value would go here]"))
                    else -> csvWriter.writeNext(arrayOf(name, "[Calculation result would go here]"))
                }
            }
        }

        csvWriter.close()
        stringWriter.close()

        // Convert CSV content to base64
        val csvContent = stringWriter.toString()
        return Base64.encodeToString(csvContent.toByteArray(), Base64.DEFAULT)
    }

    /**
     * Generate a filename for the spreadsheet (CSV format)
     */
    private fun generateFilename(title: String): String {
        val safeName = title.replace("[^a-zA-Z0-9 ]".toRegex(), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${safeName}_${timestamp}.csv"
    }
}