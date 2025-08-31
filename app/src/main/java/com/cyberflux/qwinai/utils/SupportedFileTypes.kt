package com.cyberflux.qwinai.utils

/**
 * Simplified supported file types management
 * Only includes formats that can be processed reliably without crashes
 */
object SupportedFileTypes {

    // Document MIME types that can be processed safely with server-side processing
    val SUPPORTED_DOCUMENT_TYPES = arrayOf(
        // PDF - processed by server or sent directly to AI models
        "application/pdf",
        
        // Microsoft Office formats - processed by server
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",      // XLSX  
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", // PPTX
        
        // Text formats - content extracted and sent as text
        "text/plain",
        "text/csv",
        "application/rtf" // RTF now supported by server
        
        // Note: Server-side processing enables reliable Office document support
    )

    // Office formats that are NOT supported (legacy formats only)
    val UNSUPPORTED_OFFICE_TYPES = arrayOf(
        "application/msword", // Legacy .doc format
        "application/vnd.ms-excel", // Legacy .xls format
        "application/vnd.ms-powerpoint", // Legacy .ppt format
        "application/vnd.oasis.opendocument.text", // OpenDocument text
        "application/vnd.oasis.opendocument.spreadsheet", // OpenDocument spreadsheet
        "application/vnd.oasis.opendocument.presentation" // OpenDocument presentation
        // Note: Modern Office formats (DOCX, XLSX, PPTX, RTF) are now supported via server processing
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
     * With server-side processing, all models support all file types
     */
    fun isPdfSupportedForModel(modelId: String?): Boolean {
        return true // Server-side processing supports all file types for all models
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
            📄 SUPPORTED FORMATS (with server processing):
            • Microsoft Word (.docx) - processed by server
            • Microsoft Excel (.xlsx) - processed by server
            • Microsoft PowerPoint (.pptx) - processed by server
            • PDF files (.pdf) - processed by server or sent directly to AI
            • Text files (.txt) - content extracted and processed
            • CSV files (.csv) - content extracted and processed
            • RTF files (.rtf) - processed by server

            ⚠️ UNSUPPORTED FORMATS:
            • Legacy Office formats (.doc, .xls, .ppt) - please convert to newer formats
            • OpenDocument (.odt, .ods, .odp) - please convert to Office or PDF formats
            • All other document formats

            💡 FOR UNSUPPORTED FILES:
            • Convert legacy Office files to .docx, .xlsx, .pptx formats
            • Convert OpenDocument files to Office or PDF formats
            • Copy/paste content directly into the chat
        """.trimIndent()
    }

    /**
     * Get alternative suggestions for unsupported file types
     */
    fun getAlternativesForUnsupportedType(mimeType: String): String {
        return when {
            mimeType.contains("msword") && !mimeType.contains("openxml") -> 
                "💡 Legacy Word Document (.doc): Please convert to .docx format"
            mimeType.contains("ms-excel") -> 
                "💡 Legacy Excel Document (.xls): Please convert to .xlsx format"
            mimeType.contains("ms-powerpoint") ->
                "💡 Legacy PowerPoint (.ppt): Please convert to .pptx format"
            mimeType.contains("oasis") ->
                "💡 OpenDocument: Convert to Office format (.docx, .xlsx, .pptx) or PDF"
            else ->
                "💡 Try converting to supported formats: .docx, .xlsx, .pptx, .pdf, .txt, .csv, .rtf"
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