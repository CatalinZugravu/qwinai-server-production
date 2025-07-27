package com.cyberflux.qwinai.utils

/**
 * Data class to encapsulate all AI settings in one object
 */
data class AppSettings(
    val isDeepSearchEnabled: Boolean,
    val isReasoningEnabled: Boolean,
    val reasoningLevel: String
)