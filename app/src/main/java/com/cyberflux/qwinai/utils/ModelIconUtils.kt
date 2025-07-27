package com.cyberflux.qwinai.utils

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import androidx.core.graphics.toColorInt
import com.cyberflux.qwinai.R
import timber.log.Timber

object ModelIconUtils {
    // Get icon resource for a model
    internal fun getIconResourceForModel(modelId: String): Int {
        return when (modelId) {
            ModelManager.DALLE_3_ID -> R.drawable.ic_dalle
            "stable-diffusion-v35-large" -> R.drawable.ic_stable_diffusion
            ModelManager.SEEDREAM_3_ID -> R.drawable.ic_image_model_default
            ModelManager.FLUX_SCHNELL_ID -> R.drawable.ic_flux
            ModelManager.FLUX_REALISM_ID -> R.drawable.ic_flux
            ModelManager.RECRAFT_V3_ID -> R.drawable.ic_recraft
            // Image-to-image models
            ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> R.drawable.ic_image_to_image
            ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID -> R.drawable.ic_image_to_image
            ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID -> R.drawable.ic_image_to_image
            else -> R.drawable.ic_image_model_default
        }
    }

    // Get color for a model
    fun getColorForModel(modelId: String): Int {
        return when {
            modelId.contains("claude", ignoreCase = true) -> "#5436DA".toColorInt() // Purple for Claude
            modelId.contains("llama", ignoreCase = true) -> "#0077B5".toColorInt() // Blue for Llama
            modelId.contains("gemma", ignoreCase = true) -> "#FF5722".toColorInt() // Orange for Gemma
            modelId.contains("deepseek", ignoreCase = true) -> "#00BCD4".toColorInt() // Cyan for DeepSeek
            modelId.contains("mistral", ignoreCase = true) -> "#4CAF50".toColorInt() // Green for Mistral
            modelId.contains("gpt", ignoreCase = true) -> "#10A37F".toColorInt() // Teal for
            modelId.contains("qwen", ignoreCase = true) -> "#10A37F".toColorInt() // Teal for GPT
            else -> "#757575".toColorInt() // Gray for default
        }
    }

    // Get display name for a model
    fun getModelNameForDisplay(modelId: String): String {
        return when {
            modelId.contains("claude", ignoreCase = true) -> "Claude"
            modelId.contains("llama", ignoreCase = true) -> "Llama"
            modelId.contains("gemma", ignoreCase = true) -> "Gemma"
            modelId.contains("deepseek", ignoreCase = true) -> "DeepSeek"
            modelId.contains("mistral", ignoreCase = true) -> "Mistral"
            modelId.contains("gpt", ignoreCase = true) -> "GPT"
            modelId.contains("qwen", ignoreCase = true) -> "Qwen"
            else -> "Assistant"
        }
    }

    // Method to apply model color to EditText
    fun applyModelColorToInput(editText: EditText, modelId: String) {
        val color = getColorForModel(modelId)

        // Set the underline color
        editText.backgroundTintList = ColorStateList.valueOf(color)

        // Set cursor color directly with TextCursorDrawable
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Create a cursor drawable with the model color
            val cursorDrawable = GradientDrawable().apply {
                setColor(color) // Set the cursor color
                setSize(2, -1) // Width of 2dp, height will match the text height
            }

            // Apply the cursor drawable
            editText.textCursorDrawable = cursorDrawable
        }

        // Set hint text color with transparency
        val hintColor = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))
        editText.setHintTextColor(hintColor)

        // Log the color being applied
        Timber.d("Applied color ${String.format("#%06X", 0xFFFFFF and color)} to input field for model $modelId")
    }
}