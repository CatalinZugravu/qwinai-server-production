package com.cyberflux.qwinai.utils

import java.util.regex.Pattern

/**
 * ADVANCED TABLE DETECTOR
 * 
 * Provides sophisticated table detection with:
 * - Multiple table format support
 * - Partial table detection during streaming
 * - Table boundary identification
 * - Anti-flicker table state tracking
 */
object AdvancedTableDetector {
    
    // Comprehensive table patterns
    private val PIPE_TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*\$")
    private val PIPE_TABLE_SEPARATOR = Pattern.compile("^\\s*\\|\\s*[-:]+\\s*(\\|\\s*[-:]+\\s*)*\\|\\s*\$")
    private val SIMPLE_TABLE_ROW = Pattern.compile("^\\s*[^|]*\\|[^|]*\\|.*\$")
    
    // Table characteristics
    private const val MIN_TABLE_COLUMNS = 2
    private const val MIN_TABLE_ROWS = 2
    
    data class TableDetectionResult(
        val hasTable: Boolean,
        val tableType: TableType,
        val startLine: Int,
        val endLine: Int,
        val isComplete: Boolean,
        val confidence: Float,
        val columnCount: Int,
        val rowCount: Int
    )
    
    enum class TableType {
        NONE,
        PIPE_TABLE,        // Standard markdown table with |
        SIMPLE_TABLE,      // Basic delimited table
        PARTIAL_TABLE      // Incomplete table during streaming
    }
    
    /**
     * MAIN: Detect table content with detailed analysis
     */
    fun detectTable(content: String): TableDetectionResult {
        if (content.isBlank() || !content.contains('|')) {
            return createNoTableResult()
        }
        
        val lines = content.split('\n')
        return analyzeLines(lines)
    }
    
    /**
     * QUICK: Fast table detection for streaming
     */
    fun hasTableContent(content: String): Boolean {
        if (!content.contains('|')) return false
        
        val lines = content.split('\n')
        var pipeRowCount = 0
        var hasSeparator = false
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                PIPE_TABLE_ROW.matcher(trimmed).matches() -> {
                    pipeRowCount++
                }
                PIPE_TABLE_SEPARATOR.matcher(trimmed).matches() -> {
                    hasSeparator = true
                }
            }
            
            // Quick positive detection
            if (hasSeparator && pipeRowCount >= 1) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * ANALYZE: Detailed line-by-line table analysis
     */
    private fun analyzeLines(lines: List<String>): TableDetectionResult {
        var tableStart = -1
        var tableEnd = -1
        var tableType = TableType.NONE
        var maxColumns = 0
        var rowCount = 0
        var hasSeparator = false
        var confidence = 0f
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            when {
                // Check for pipe table separator
                PIPE_TABLE_SEPARATOR.matcher(line).matches() -> {
                    hasSeparator = true
                    if (tableStart == -1) {
                        tableStart = maxOf(0, i - 1) // Include header if exists
                    }
                    confidence += 0.4f
                }
                
                // Check for pipe table row
                PIPE_TABLE_ROW.matcher(line).matches() -> {
                    val columns = countTableColumns(line)
                    maxColumns = maxOf(maxColumns, columns)
                    
                    if (tableStart == -1) {
                        tableStart = i
                        tableType = TableType.PIPE_TABLE
                    }
                    tableEnd = i
                    rowCount++
                    confidence += 0.2f
                }
                
                // Check for simple table row
                SIMPLE_TABLE_ROW.matcher(line).matches() && tableType == TableType.NONE -> {
                    val columns = countTableColumns(line)
                    if (columns >= MIN_TABLE_COLUMNS) {
                        maxColumns = maxOf(maxColumns, columns)
                        
                        if (tableStart == -1) {
                            tableStart = i
                            tableType = TableType.SIMPLE_TABLE
                        }
                        tableEnd = i
                        rowCount++
                        confidence += 0.1f
                    }
                }
                
                // Empty line might end table
                line.isEmpty() && tableStart != -1 -> {
                    break
                }
                
                // Non-table content after table starts
                tableStart != -1 && !isTableLikeLine(line) -> {
                    break
                }
            }
        }
        
        // Determine if table is complete
        val isComplete = determineTableCompleteness(lines, tableStart, tableEnd)
        
        // Adjust confidence based on table characteristics
        confidence = adjustConfidence(confidence, rowCount, maxColumns, hasSeparator, isComplete)
        
        // Determine final table type
        val finalTableType = when {
            tableType == TableType.NONE -> TableType.NONE
            !isComplete -> TableType.PARTIAL_TABLE
            else -> tableType
        }
        
        return TableDetectionResult(
            hasTable = tableStart != -1 && rowCount >= MIN_TABLE_ROWS && maxColumns >= MIN_TABLE_COLUMNS,
            tableType = finalTableType,
            startLine = tableStart,
            endLine = tableEnd,
            isComplete = isComplete,
            confidence = minOf(1.0f, confidence),
            columnCount = maxColumns,
            rowCount = rowCount
        )
    }
    
    /**
     * COUNT: Count table columns in a line
     */
    private fun countTableColumns(line: String): Int {
        // Remove leading/trailing pipes and count remaining pipes + 1
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return if (trimmed.isEmpty()) 0 else trimmed.count { it == '|' } + 1
    }
    
    /**
     * CHECK: Determine if line could be part of a table
     */
    private fun isTableLikeLine(line: String): Boolean {
        return line.contains('|') || line.trim().isEmpty()
    }
    
    /**
     * COMPLETENESS: Check if table appears complete
     */
    private fun determineTableCompleteness(
        lines: List<String>, 
        tableStart: Int, 
        tableEnd: Int
    ): Boolean {
        if (tableStart == -1 || tableEnd == -1) return false
        
        // If table goes to end of content, it might be incomplete during streaming
        if (tableEnd == lines.size - 1) {
            val lastLine = lines[tableEnd].trim()
            // If last line is empty or doesn't end with |, might be incomplete
            return lastLine.isEmpty() || !lastLine.endsWith("|")
        }
        
        // Check if next line after table is clearly non-table content
        val nextLineIndex = tableEnd + 1
        if (nextLineIndex < lines.size) {
            val nextLine = lines[nextLineIndex].trim()
            return nextLine.isEmpty() || !nextLine.contains('|')
        }
        
        return true
    }
    
    /**
     * CONFIDENCE: Adjust confidence score based on table characteristics
     */
    private fun adjustConfidence(
        baseConfidence: Float,
        rowCount: Int,
        columnCount: Int,
        hasSeparator: Boolean,
        isComplete: Boolean
    ): Float {
        var confidence = baseConfidence
        
        // Boost confidence for well-formed tables
        if (hasSeparator) confidence += 0.3f
        if (rowCount >= 3) confidence += 0.2f
        if (columnCount >= 3) confidence += 0.1f
        if (isComplete) confidence += 0.2f
        
        // Penalty for very short tables
        if (rowCount < MIN_TABLE_ROWS) confidence *= 0.5f
        if (columnCount < MIN_TABLE_COLUMNS) confidence *= 0.5f
        
        return confidence
    }
    
    /**
     * HELPER: Create no-table result
     */
    private fun createNoTableResult(): TableDetectionResult {
        return TableDetectionResult(
            hasTable = false,
            tableType = TableType.NONE,
            startLine = -1,
            endLine = -1,
            isComplete = true,
            confidence = 0f,
            columnCount = 0,
            rowCount = 0
        )
    }
    
    /**
     * STREAMING: Check if content contains partial table during streaming
     */
    fun containsPartialTable(content: String): Boolean {
        val result = detectTable(content)
        return result.hasTable && (result.tableType == TableType.PARTIAL_TABLE || !result.isComplete)
    }
    
    /**
     * STATISTICS: Get detection statistics for monitoring
     */
    fun getDetectionStats(content: String): String {
        val result = detectTable(content)
        return "Table Detection - Type: ${result.tableType}, Rows: ${result.rowCount}, " +
                "Cols: ${result.columnCount}, Complete: ${result.isComplete}, " +
                "Confidence: ${String.format("%.2f", result.confidence)}"
    }
}