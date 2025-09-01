package com.cyberflux.qwinai.model

/**
 * Data class to represent an image generation option item
 * used in the spinner adapters
 */
data class ImageGenerationOption(
    val id: String,
    val displayName: String,
    val iconResourceId: Int? = null,
    val isPremium: Boolean = false,
    val description: String = ""  // Optional description for tooltips or info popups

)