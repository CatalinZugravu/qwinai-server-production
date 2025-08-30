package com.cyberflux.qwinai.utils

/**
 * Simplified supported file types management
 * Only includes formats that can be processed reliably without crashes
 */
object SupportedFileTypes {

    // Document MIME types that can be processed safely
    val SUPPORTED_DOCUMENT_TYPES = arrayOf(
        // PDF - sent directly to AI models that support them (no extraction)
        "application/pdf",
        
        // Text formats - content extracted and sent as text
        "text/plain",
        "text/csv"
        
        // Note: Only PDF, TXT, and CSV are supported for optimal stability
        // Office formats removed to prevent crashes and processing issues
    )

    // Office formats that are NOT supported (for user guidance)
    val UNSUPPORTED_OFFICE_TYPES = arrayOf(
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", 
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet", 
        "application/vnd.oasis.opendocument.presentation",
        "text/rtf"
    )

    // Image MIME types
    val SUPPORTED_IMAGE_TYPES = arrayOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/bmp",
        "image/tiff"
    )

    /**
     * Check if a MIME type is supported for document extraction
     */
    fun isDocumentTypeSupported(mimeType: String?): Boolean {
        if (mimeType == null) return false
        
        return SUPPORTED_DOCUMENT_TYPES.contains(mimeType) || 
               mimeType.startsWith("text/") ||
               mimeType.startsWith("application/text")
    }

    /**
     * Check if a MIME type is an unsupported Office format
     */
    fun isUnsupportedOfficeType(mimeType: String?): Boolean {
        if (mimeType == null) return false
        return UNSUPPORTED_OFFICE_TYPES.contains(mimeType)
    }

    /**
     * Check if PDF is supported for the current AI model
     */
    fun isPdfSupportedForModel(modelId: String?): Boolean {
        return modelId?.let { ModelValidator.supportsPdfDirectly(it) } ?: false
    }

    /**
     * Check if a MIME type is a supported image
     */
    fun isImageTypeSupported(mimeType: String?): Boolean {
        if (mimeType == null) return false
        return SUPPORTED_IMAGE_TYPES.contains(mimeType)
    }

    /**
     * Get file category based on MIME type
     */
    fun getFileCategory(mimeType: String?): FileCategory {
        return when {
            isDocumentTypeSupported(mimeType) -> FileCategory.DOCUMENT
            isImageTypeSupported(mimeType) -> FileCategory.IMAGE
            else -> FileCategory.UNSUPPORTED
        }
    }

    /**
     * Get human-readable description of supported document formats
     */
    fun getSupportedDocumentFormatsDescription(): String {
        return """
            📄 SUPPORTED FORMATS:
            • Text files (.txt) - content extracted and processed
            • CSV files (.csv) - content extracted and processed  
            • PDF files (.pdf) - sent directly to AI models (no extraction needed)

            ⚠️ UNSUPPORTED FORMATS:
            • Microsoft Office (.doc, .docx, .xls, .xlsx, .ppt, .pptx)
            • OpenDocument (.odt, .ods, .odp)  
            • RTF files (.rtf)
            • All other document formats

            💡 ALTERNATIVES FOR UNSUPPORTED FILES:
            • Convert Office files to PDF or TXT format
            • Use AI models with native Office document support
            • Copy/paste content directly into the chat
        """.trimIndent()
    }

    /**
     * Get alternative suggestions for unsupported file types
     */
    fun getAlternativesForUnsupportedType(mimeType: String): String {
        return when {
            mimeType.contains("word") -> 
                "💡 Word Document: Convert to PDF or export as plain text (.txt)"
            mimeType.contains("excel") || mimeType.contains("spreadsheet") ->
                "💡 Excel/Spreadsheet: Export as CSV format or convert to PDF"
            mimeType.contains("powerpoint") || mimeType.contains("presentation") ->
                "💡 PowerPoint: Export as PDF or save slides as images"
            mimeType.contains("oasis") ->
                "💡 OpenDocument: Convert to PDF or export as plain text"
            mimeType.contains("rtf") ->
                "💡 RTF Document: Save as plain text (.txt) format"
            else ->
                "💡 Try converting to PDF, TXT, or CSV format for best compatibility"
        }
    }

    /**
     * Get file extension from MIME type
     */
    fun getFileExtensionFromMimeType(mimeType: String): String {
        return when (mimeType) {
            "application/pdf" -> ".pdf"
            "application/msword" -> ".doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
            "application/vnd.ms-excel" -> ".xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
            "application/vnd.ms-powerpoint" -> ".ppt"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx"
            "application/vnd.oasis.opendocument.text" -> ".odt"
            "application/vnd.oasis.opendocument.spreadsheet" -> ".ods"
            "application/vnd.oasis.opendocument.presentation" -> ".odp"
            "text/plain" -> ".txt"
            "text/csv" -> ".csv"
            "text/tab-separated-values" -> ".tsv"
            "text/rtf" -> ".rtf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "image/bmp" -> ".bmp"
            "image/tiff" -> ".tiff"
            else -> ""
        }
    }

    enum class FileCategory {
        DOCUMENT,
        IMAGE,
        UNSUPPORTED
    }
}