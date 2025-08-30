package com.cyberflux.qwinai.model

import android.net.Uri

/**
 * Represents a file item for AI processing
 * Used in token calculation and message validation
 */
data class FileItem(
    val uri: Uri,
    val name: String,
    val mimeType: String? = null
)