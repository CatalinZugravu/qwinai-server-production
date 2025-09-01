package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive service for handling file uploads to AI models.
 * Enhanced with fixes for disappearing images and improved reliability.
 */
class AIFileService(val context: Context) {

    // Cache for base64 encoded files to improve performance
    private val fileCache = ConcurrentHashMap<String, String>()

    /**
     * Get optimized image as base64 with improved error handling and memory management
     */
    suspend fun getOptimizedImageBase64(uri: Uri, maxDimension: Int = 2048, modelId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            // Adjust maxDimension based on the model ID to prevent oversized payloads
            val adjustedMaxDimension = when {
                modelId?.contains("grok-3", ignoreCase = true) == true -> 1024  // Smaller max size for Grok-3
                else -> maxDimension                                            // Use provided value for other models
            }

            val cacheKey = "img_${uri}_${adjustedMaxDimension}"

            // Check cache first
            fileCache[cacheKey]?.let {
                Timber.d("Using cached image encoding for $uri")
                return@withContext it
            }

            // Use improved memory-efficient scaling
            val bitmap = decodeSampledBitmapFromUri(uri, adjustedMaxDimension)

            if (bitmap != null) {
                Timber.d("Successfully decoded bitmap with dimensions ${bitmap.width}x${bitmap.height}")

                // Convert to base64 - determine format based on URI
                val outputStream = ByteArrayOutputStream()
                val format = when {
                    uri.toString().endsWith(".png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
                    uri.toString().endsWith(".webp", ignoreCase = true) -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }

                // Adjust quality based on model
                val quality = when {
                    modelId?.contains("grok", ignoreCase = true) == true -> 80  // Lower quality
                    else -> getImageQuality(modelId)                            // Use standard quality calculation
                }

                bitmap.compress(format, quality, outputStream)

                // Use standardized encoding
                val base64 = encodeToBase64(outputStream.toByteArray(), modelId ?: "")
                Timber.d("Compressed image to ${outputStream.size()/1024}KB")

                // Free bitmap memory immediately
                bitmap.recycle()

                // Add data URI prefix if not already present
                val dataUri = if (!base64.startsWith("data:")) {
                    val mimeType = when (format) {
                        Bitmap.CompressFormat.PNG -> "image/png"
                        Bitmap.CompressFormat.WEBP -> "image/webp"
                        else -> "image/jpeg"
                    }
                    "data:$mimeType;base64,$base64"
                } else {
                    base64
                }

                // Cache the result
                fileCache[cacheKey] = dataUri

                return@withContext dataUri
            } else {
                Timber.w("Failed to decode bitmap, falling back to direct conversion")

                // Direct conversion fallback
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    Timber.d("Direct conversion image size: ${bytes.size/1024}KB")

                    // Get MIME type for data URI
                    val mimeType = when {
                        uri.toString().endsWith(".png", ignoreCase = true) -> "image/png"
                        uri.toString().endsWith(".webp", ignoreCase = true) -> "image/webp"
                        uri.toString().endsWith(".gif", ignoreCase = true) -> "image/gif"
                        else -> "image/jpeg"
                    }

                    val base64 = encodeToBase64(bytes, modelId ?: "")
                    val dataUri = "data:$mimeType;base64,$base64"

                    // Cache the result
                    fileCache[cacheKey] = dataUri

                    return@withContext dataUri
                }

                Timber.w("Failed to open input stream for image")
                return@withContext null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error optimizing image: ${e.message}")

            // Try a more direct approach as fallback
            try {
                Timber.d("Trying fallback direct conversion for image")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val mimeType = FileUtil.getMimeType(context, uri) ?: "image/jpeg"
                    return@withContext "data:$mimeType;base64,${encodeToBase64(bytes, modelId ?: "")}"
                }
            } catch (e2: Exception) {
                Timber.e(e2, "Critical error in image encoding fallback")
                null
            }
        }
    }

    /**
     * Standardized Base64 encoding that works consistently across all models
     * Uses Android's Base64 implementation with appropriate flags
     */
    private fun encodeToBase64(bytes: ByteArray, modelId: String): String {
        // Select appropriate flags based on model requirements
        val flags = when {
            // Claude models require NO_WRAP for proper encoding
            modelId.contains("claude", ignoreCase = true) ||
                    modelId.contains("anthropic", ignoreCase = true) ->
                android.util.Base64.NO_WRAP

            // GPT models work best with DEFAULT
            modelId.contains("gpt", ignoreCase = true) ||
                    modelId.contains("openai", ignoreCase = true) ->
                android.util.Base64.DEFAULT

            // Llama models need NO_PADDING and NO_WRAP
            modelId.contains("llama", ignoreCase = true) ->
                android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING

            // Default encoding for other models
            else -> android.util.Base64.DEFAULT
        }

        // Use Android's Base64 encoder with selected flags
        return android.util.Base64.encodeToString(bytes, flags)
    }

    /**
     * Improved image scaling that's memory efficient and handles large images properly
     */
    private fun decodeSampledBitmapFromUri(uri: Uri, maxDimension: Int): Bitmap? {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxDimension)

            // Set to false as we're now actually decoding the bitmap
            options.inJustDecodeBounds = false

            // Add memory optimizations
            options.inPreferredConfig = Bitmap.Config.RGB_565  // Use less memory per pixel
            options.inMutable = true                           // Allow bitmap to be modified

            // Decode bitmap with inSampleSize set
            var bitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                try {
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                } catch (outOfMemory: OutOfMemoryError) {
                    // If we still hit OOM, try with more aggressive sampling
                    options.inSampleSize *= 2
                    context.contentResolver.openInputStream(uri)?.use { retryStream ->
                        bitmap = BitmapFactory.decodeStream(retryStream, null, options)
                    }
                }
            }

            // If we still couldn't decode, log the error
            if (bitmap == null) {
                Timber.e("Failed to decode bitmap even with sampling")
                return null
            }

            // Apply scaling if needed to get exact dimensions
            val width = bitmap!!.width
            val height = bitmap!!.height

            if (width > maxDimension || height > maxDimension) {
                val scaleFactor = maxDimension.toFloat() / Math.max(width, height)
                val scaledWidth = (width * scaleFactor).toInt()
                val scaledHeight = (height * scaleFactor).toInt()

                // Create scaled bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, scaledWidth, scaledHeight, true)

                // If a new bitmap was created, recycle the old one to free memory
                if (scaledBitmap != bitmap) {
                    bitmap!!.recycle()
                }

                return scaledBitmap
            }

            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "Error decoding sampled bitmap: ${e.message}")
            return null
        }
    }

    /**
     * Calculate the optimal sample size for loading a bitmap
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > maxDimension || width > maxDimension) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested maxDimension
            while ((halfHeight / inSampleSize) >= maxDimension || (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }

            // For safety, ensure we don't go below 1
            inSampleSize = Math.max(1, inSampleSize)
        }

        Timber.d("Calculated sample size: $inSampleSize for dimensions ${width}x${height}")
        return inSampleSize
    }

    /**
     * Get image compression quality for a model (0-100)
     */
    private fun getImageQuality(modelId: String?): Int {
        return when {
            modelId?.contains("gpt-4o", ignoreCase = true) == true ||
                    modelId?.contains("o1", ignoreCase = true) == true -> 95
            modelId?.contains("claude-3", ignoreCase = true) == true -> 95
            modelId?.contains("gemini", ignoreCase = true) == true -> 90
            else -> 85 // Default
        }
    }

    /**
     * Clear image cache to free memory
     */
    fun clearImageCache() {
        fileCache.clear()
        Timber.d("Image cache cleared")
    }
}