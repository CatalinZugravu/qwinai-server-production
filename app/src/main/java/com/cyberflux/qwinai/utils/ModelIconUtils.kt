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

    // Get color for a model - now uses dynamic accent color
    fun getColorForModel(modelId: String, context: android.content.Context): Int {
        return com.cyberflux.qwinai.utils.ThemeManager.getCurrentAccentColor(context)
    }

    // Get icon resource for a chat model (different from image generation models)
    fun getIconResourceForChatModel(modelId: String): Int {
        return when {
            modelId.contains("claude", ignoreCase = true) -> R.drawable.ic_claude
            modelId.contains("gpt", ignoreCase = true) -> R.drawable.ic_gpt
            modelId.contains("llama", ignoreCase = true) -> R.drawable.ic_llama
            modelId.contains("gemma", ignoreCase = true) -> R.drawable.ic_gemma
            modelId.contains("deepseek", ignoreCase = true) -> R.drawable.ic_ai_brain
            modelId.contains("mistral", ignoreCase = true) -> R.drawable.ic_mistral
            modelId.contains("qwen", ignoreCase = true) -> R.drawable.ic_qwen
            modelId.contains("cohere", ignoreCase = true) -> R.drawable.ic_ai_chip
            else -> R.drawable.ic_default_model
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
        val color = getColorForModel(modelId, editText.context)

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