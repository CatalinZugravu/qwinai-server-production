package com.cyberflux.qwinai.model

import androidx.annotation.DrawableRes

/**
 * Data class for AI features displayed on the start screen
 */
data class AIFeature(
    val id: String,
    val title: String,
    val description: String,
    val iconResId: Int,
    val colorResId: Int,
)

/**
 * Data class for quick prompt chips displayed on the start screen
 */
data class PromptChip(
    val id: String,
    val text: String,
    val category: String
)


data class PromptCategory(
    val title: String,
    val prompts: List<PromptChip>
)

data class RecentModel(
    val displayName: String,
    val id: String,
    @DrawableRes val iconResId: Int
)

/**
 * Data class representing a trending prompt suggestion
 */
data class TrendingPrompt(
    val title: String,
    val description: String,
    @DrawableRes val iconResId: Int,
    val colorHex: String  // Hex color code e.g. "#ff9500"
)


