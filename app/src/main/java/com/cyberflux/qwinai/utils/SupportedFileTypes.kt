package com.cyberflux.qwinai.utils

/**
 * Centralized management of supported file types for the file extractor system
 */
object SupportedFileTypes {

    // Document MIME types that can be processed by the text extractor
    val SUPPORTED_DOCUMENT_TYPES = arrayOf(
        // PDF
        "application/pdf",
        
        // Microsoft Word
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        
        // Microsoft Excel
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        
        // Microsoft PowerPoint
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        
        // OpenDocument formats
        "application/vnd.oasis.opendocument.text",           // ODT
        "application/vnd.oasis.opendocument.spreadsheet",    // ODS
        "application/vnd.oasis.opendocument.presentation",   // ODP
        
        // Text formats
        "text/plain",
        "text/csv",
        "text/tab-separated-values",
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
            Supported document formats:
            • PDF files (.pdf)
            • Microsoft Word (.doc, .docx)
            • Microsoft Excel (.xls, .xlsx)
            • Microsoft PowerPoint (.ppt, .pptx)
            • OpenDocument (.odt, .ods, .odp)
            • Text files (.txt, .csv, .tsv, .rtf)
        """.trimIndent()
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