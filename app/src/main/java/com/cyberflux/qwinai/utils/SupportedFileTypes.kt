package com.cyberflux.qwinai.utils

import timber.log.Timber

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
     * Enhanced to handle alternative MIME types that Android may report
     */
    fun isDocumentTypeSupported(mimeType: String?, fileName: String? = null): Boolean {
        if (mimeType == null) {
            Timber.d("🔍 SupportedFileTypes: isDocumentTypeSupported() - mimeType is null")
            return false
        }
        
        Timber.d("🔍 SupportedFileTypes: isDocumentTypeSupported() checking mimeType='$mimeType', fileName='$fileName'")
        
        // First check direct MIME type match
        if (SUPPORTED_DOCUMENT_TYPES.contains(mimeType) || 
            mimeType.startsWith("text/") ||
            mimeType.startsWith("application/text")) {
            Timber.d("✅ SupportedFileTypes: Direct MIME type match - SUPPORTED")
            return true
        }
        
        // Handle alternative MIME types that Android reports for Office files
        if (mimeType == "application/zip" || mimeType == "application/octet-stream") {
            Timber.d("🔍 SupportedFileTypes: Alternative MIME type detected ($mimeType), checking extension...")
            val result = isDocumentByExtension(fileName)
            Timber.d("${if (result) "✅" else "❌"} SupportedFileTypes: Extension check result = $result")
            return result
        }
        
        Timber.d("❌ SupportedFileTypes: No match found - NOT SUPPORTED")
        return false
    }

    /**
     * Check if file is a supported document based on file extension
     * Fallback when MIME type detection fails
     */
    private fun isDocumentByExtension(fileName: String?): Boolean {
        if (fileName == null) {
            Timber.d("🔍 SupportedFileTypes: isDocumentByExtension() - fileName is null")
            return false
        }
        
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val supportedExtensions = setOf(
            "pdf", "docx", "xlsx", "pptx", "txt", "csv", "rtf"
        )
        
        val result = supportedExtensions.contains(extension)
        Timber.d("🔍 SupportedFileTypes: isDocumentByExtension() fileName='$fileName', extension='$extension', result=$result")
        
        return result
    }

    /**
     * Check if a MIME type is an unsupported Office format
     * Enhanced to avoid blocking ZIP-based Office files
     */
    fun isUnsupportedOfficeType(mimeType: String?, fileName: String? = null): Boolean {
        if (mimeType == null) {
            Timber.d("🔍 SupportedFileTypes: isUnsupportedOfficeType() - mimeType is null")
            return false
        }
        
        Timber.d("🔍 SupportedFileTypes: isUnsupportedOfficeType() checking mimeType='$mimeType', fileName='$fileName'")
        
        // Don't block ZIP or octet-stream as these might be valid Office files
        if (mimeType == "application/zip" || mimeType == "application/octet-stream") {
            Timber.d("🔍 SupportedFileTypes: ZIP/octet-stream detected, checking extension...")
            // Only consider it unsupported if the extension is actually unsupported
            val result = if (fileName != null) {
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val unsupportedExtensions = setOf("doc", "xls", "ppt", "odt", "ods", "odp")
                val isUnsupported = unsupportedExtensions.contains(extension)
                Timber.d("🔍 SupportedFileTypes: Extension '$extension' ${if (isUnsupported) "IS" else "IS NOT"} in unsupported list")
                isUnsupported
            } else {
                Timber.d("🔍 SupportedFileTypes: No fileName provided, defaulting to false")
                false // Don't block if we can't determine the file type
            }
            Timber.d("${if (result) "❌" else "✅"} SupportedFileTypes: isUnsupportedOfficeType result = $result")
            return result
        }
        
        val result = UNSUPPORTED_OFFICE_TYPES.contains(mimeType)
        Timber.d("${if (result) "❌" else "✅"} SupportedFileTypes: Standard unsupported type check result = $result")
        return result
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