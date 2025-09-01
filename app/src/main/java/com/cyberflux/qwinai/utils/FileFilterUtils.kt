package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.model.ModelConfig

object FileFilterUtils {
    
    /**
     * Get MIME types supported by the given model
     */
    fun getSupportedMimeTypes(modelConfig: ModelConfig): Array<String> {
        if (!modelConfig.supportsFileUpload) {
            return emptyArray()
        }
        
        val mimeTypes = mutableListOf<String>()
        
        // Add image formats
        modelConfig.supportedImageFormats.forEach { format ->
            when (format.lowercase()) {
                "jpg", "jpeg" -> mimeTypes.add("image/jpeg")
                "png" -> mimeTypes.add("image/png")
                "gif" -> mimeTypes.add("image/gif")
                "webp" -> mimeTypes.add("image/webp")
            }
        }
        
        // Add document formats
        modelConfig.supportedDocumentFormats.forEach { format ->
            when (format.lowercase()) {
                "pdf" -> mimeTypes.add("application/pdf")
                "txt" -> mimeTypes.add("text/plain")
                "csv" -> mimeTypes.add("text/csv")
                "xlsx" -> mimeTypes.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                "xls" -> mimeTypes.add("application/vnd.ms-excel")
                "doc" -> mimeTypes.add("application/msword")
                "docx" -> mimeTypes.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                "ppt" -> mimeTypes.add("application/vnd.ms-powerpoint")
                "pptx" -> mimeTypes.add("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            }
        }
        
        // Add audio formats if supported
        if (modelConfig.supportsAudio) {
            modelConfig.supportedAudioFormats.forEach { format ->
                when (format.lowercase()) {
                    "mp3" -> mimeTypes.add("audio/mpeg")
                    "wav" -> mimeTypes.add("audio/wav")
                    "flac" -> mimeTypes.add("audio/flac")
                    "aac" -> mimeTypes.add("audio/aac")
                    "opus" -> mimeTypes.add("audio/opus")
                    "pcm" -> mimeTypes.add("audio/pcm")
                }
            }
        }
        
        return mimeTypes.toTypedArray()
    }
    
    /**
     * Get a human-readable description of supported file types
     */
    fun getSupportedFileTypesDescription(modelConfig: ModelConfig): String {
        if (!modelConfig.supportsFileUpload) {
            return "No file uploads supported"
        }
        
        val types = mutableListOf<String>()
        
        if (modelConfig.supportedImageFormats.isNotEmpty()) {
            types.add("Images (${modelConfig.supportedImageFormats.joinToString(", ").uppercase()})")
        }
        
        if (modelConfig.supportedDocumentFormats.isNotEmpty()) {
            types.add("Documents (${modelConfig.supportedDocumentFormats.joinToString(", ").uppercase()})")
        }
        
        if (modelConfig.supportsAudio && modelConfig.supportedAudioFormats.isNotEmpty()) {
            types.add("Audio (${modelConfig.supportedAudioFormats.joinToString(", ").uppercase()})")
        }
        
        return if (types.isNotEmpty()) {
            types.joinToString(", ")
        } else {
            "Files supported"
        }
    }
    
    /**
     * Check if a file extension is supported by the model
     */
    fun isFileExtensionSupported(modelConfig: ModelConfig, extension: String): Boolean {
        if (!modelConfig.supportsFileUpload) return false
        
        val ext = extension.lowercase().removePrefix(".")
        return modelConfig.supportedFileTypes.any { it.lowercase() == ext }
    }
    
    /**
     * Validate file size against model limits
     */
    fun isFileSizeValid(modelConfig: ModelConfig, fileSizeBytes: Long): Boolean {
        if (!modelConfig.supportsFileUpload) return false
        return fileSizeBytes <= modelConfig.maxFileSizeBytes
    }
    
    /**
     * Get maximum file count for the model
     */
    fun getMaxFileCount(modelConfig: ModelConfig): Int {
        return if (modelConfig.supportsFileUpload) modelConfig.maxFiles else 0
    }
    
    /**
     * Get file size limit in MB for display
     */
    fun getFileSizeLimitMB(modelConfig: ModelConfig): Int {
        return (modelConfig.maxFileSizeBytes / (1024 * 1024)).toInt()
    }
    
    /**
     * Create file picker intent with model-specific filtering
     */
    fun createFilePickerIntent(modelConfig: ModelConfig, allowMultiple: Boolean = true): android.content.Intent? {
        if (!modelConfig.supportsFileUpload) return null
        
        val mimeTypes = getSupportedMimeTypes(modelConfig)
        if (mimeTypes.isEmpty()) return null
        
        return android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple && modelConfig.supportsMultipleFileSelection)
        }
    }
}