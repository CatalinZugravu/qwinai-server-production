package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Enhanced utility class for handling image processing for AIMLAPI image-to-image generation
 * AIMLAPI accepts base64 data URLs for the image_url parameter
 */
object ImageUploadUtils {

    // Constants for image processing
    private const val MAX_IMAGE_SIZE = 4_500_000 // ~6MB base64 = ~4.5MB binary
    private const val OPTIMAL_DIMENSION = 1024 // Optimal for most AI models
    private const val MAX_DIMENSION = 2048 // Maximum allowed dimension
    private const val MIN_DIMENSION = 64 // Minimum allowed dimension

    /**
     * Process image URI to base64 data URL for AIMLAPI's image_url parameter
     * @param context Android context
     * @param imageUri URI of the image to process
     * @return Base64 data URL (e.g., "data:image/jpeg;base64,<data>") or null if processing failed
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Processing image for AIMLAPI image_url parameter: $imageUri")

            // Validate the image first
            if (!validateImageUri(context, imageUri)) {
                Timber.e("Invalid image URI: $imageUri")
                return@withContext null
            }

            // Convert URI to optimized bitmap
            val bitmap = createOptimizedBitmapFromUri(context, imageUri) ?: return@withContext null

            // Convert bitmap to base64 data URL
            val dataUrl = bitmapToBase64DataUrl(bitmap)

            // Recycle bitmap to free memory
            bitmap.recycle()

            if (dataUrl != null) {
                Timber.d("Successfully converted image to base64 data URL (${dataUrl.length} chars)")
                return@withContext dataUrl
            } else {
                Timber.e("Failed to convert bitmap to base64 data URL")
                return@withContext null
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing image for AIMLAPI: ${e.message}")
            null
        }
    }

    /**
     * Process multiple images for multi-image models
     * @param context Android context
     * @param imageUris List of image URIs to process
     * @return List of base64 data URLs
     */
    suspend fun uploadMultipleImages(context: Context, imageUris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val processedImages = mutableListOf<String>()
        var successCount = 0
        var failureCount = 0

        for ((index, uri) in imageUris.withIndex()) {
            try {
                Timber.d("Processing image ${index + 1} of ${imageUris.size}: $uri")
                val dataUrl = uploadImage(context, uri)
                if (dataUrl != null) {
                    processedImages.add(dataUrl)
                    successCount++
                } else {
                    failureCount++
                    Timber.w("Failed to process image ${index + 1}: $uri")
                }
            } catch (e: Exception) {
                failureCount++
                Timber.e(e, "Error processing image ${index + 1}: ${e.message}")
            }
        }

        Timber.d("Multi-image processing complete: $successCount successful, $failureCount failed")
        processedImages
    }

    /**
     * Convert URI to an optimized bitmap with proper compression for AIMLAPI
     */
    private fun createOptimizedBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // First, decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Validate image dimensions
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Timber.e("Invalid image dimensions: ${options.outWidth}x${options.outHeight}")
                return null
            }

            // Check if image is too small
            if (options.outWidth < MIN_DIMENSION || options.outHeight < MIN_DIMENSION) {
                Timber.e("Image too small: ${options.outWidth}x${options.outHeight} (min: ${MIN_DIMENSION}x${MIN_DIMENSION})")
                return null
            }

            // Calculate optimal sample size
            options.inSampleSize = calculateInSampleSize(options, OPTIMAL_DIMENSION, OPTIMAL_DIMENSION)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory

            // Decode the actual bitmap
            val newInputStream = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
            newInputStream.close()

            if (bitmap == null) {
                Timber.e("Failed to decode bitmap from URI")
                return null
            }

            // Handle EXIF orientation
            bitmap = handleExifOrientation(context, uri, bitmap)

            // Further resize if still too large for optimal API performance
            val resizedBitmap = if (bitmap.width > OPTIMAL_DIMENSION || bitmap.height > OPTIMAL_DIMENSION) {
                val ratio = kotlin.math.min(OPTIMAL_DIMENSION.toDouble() / bitmap.width, OPTIMAL_DIMENSION.toDouble() / bitmap.height)
                val width = (bitmap.width * ratio).toInt().coerceAtLeast(MIN_DIMENSION)
                val height = (bitmap.height * ratio).toInt().coerceAtLeast(MIN_DIMENSION)

                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (scaled != bitmap) {
                    bitmap.recycle() // Recycle original if we created a new one
                }
                scaled
            } else {
                bitmap
            }

            // Final dimension check
            if (resizedBitmap.width > MAX_DIMENSION || resizedBitmap.height > MAX_DIMENSION) {
                Timber.w("Image still too large after resizing: ${resizedBitmap.width}x${resizedBitmap.height}")
                // One more resize to ensure we're within limits
                val ratio = kotlin.math.min(MAX_DIMENSION.toDouble() / resizedBitmap.width, MAX_DIMENSION.toDouble() / resizedBitmap.height)
                val width = (resizedBitmap.width * ratio).toInt()
                val height = (resizedBitmap.height * ratio).toInt()

                val finalBitmap = Bitmap.createScaledBitmap(resizedBitmap, width, height, true)
                if (finalBitmap != resizedBitmap) {
                    resizedBitmap.recycle()
                }

                Timber.d("Created final optimized bitmap: ${finalBitmap.width}x${finalBitmap.height}")
                return finalBitmap
            }

            Timber.d("Created optimized bitmap: ${resizedBitmap.width}x${resizedBitmap.height}")
            return resizedBitmap

        } catch (e: IOException) {
            Timber.e(e, "Error creating optimized bitmap: ${e.message}")
            return null
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory while processing image")
            return null
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception accessing image: ${e.message}")
            return null
        }
    }

    /**
     * Handle EXIF orientation to ensure images are displayed correctly
     */
    private fun handleExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

                return when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true, vertical = false)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false, vertical = true)
                    else -> bitmap
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not read EXIF data, using original orientation")
        }
        return bitmap
    }

    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    /**
     * Flip bitmap horizontally or vertically
     */
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postScale(
                if (horizontal) -1f else 1f,
                if (vertical) -1f else 1f,
                bitmap.width / 2f,
                bitmap.height / 2f
            )
        }
        val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (flippedBitmap != bitmap) {
            bitmap.recycle()
        }
        return flippedBitmap
    }

    /**
     * Convert bitmap to base64 data URL format expected by AIMLAPI
     */
    private fun bitmapToBase64DataUrl(bitmap: Bitmap): String? {
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()

            // Use progressive JPEG compression based on image characteristics
            val pixelCount = bitmap.width * bitmap.height
            val quality = when {
                pixelCount > 1_000_000 -> 70  // Very large images: lower quality
                pixelCount > 500_000 -> 75    // Large images: medium-low quality
                pixelCount > 250_000 -> 80    // Medium images: medium quality
                else -> 85                    // Small images: higher quality
            }

            // Try JPEG first (usually smaller)
            val jpegCompressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            var byteArray = byteArrayOutputStream.toByteArray()
            var format = "jpeg"

            // If JPEG compression failed or file is still too large, try PNG with lower quality
            if (!jpegCompressed || byteArray.size > MAX_IMAGE_SIZE) {
                byteArrayOutputStream.reset()

                // For PNG, we need to reduce dimensions further if still too large
                var workingBitmap = bitmap
                var attempt = 0
                val maxAttempts = 3

                while (byteArray.size > MAX_IMAGE_SIZE && attempt < maxAttempts) {
                    val scale = 0.8f // Reduce by 20% each iteration
                    val newWidth = (workingBitmap.width * scale).toInt().coerceAtLeast(MIN_DIMENSION)
                    val newHeight = (workingBitmap.height * scale).toInt().coerceAtLeast(MIN_DIMENSION)

                    val smallerBitmap = Bitmap.createScaledBitmap(workingBitmap, newWidth, newHeight, true)
                    if (smallerBitmap != workingBitmap && workingBitmap != bitmap) {
                        workingBitmap.recycle()
                    }
                    workingBitmap = smallerBitmap

                    byteArrayOutputStream.reset()
                    workingBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                    byteArray = byteArrayOutputStream.toByteArray()
                    format = "png"
                    attempt++

                    Timber.d("Attempt $attempt: Reduced to ${workingBitmap.width}x${workingBitmap.height}, size: ${byteArray.size} bytes")
                }

                // Clean up if we created a working bitmap
                if (workingBitmap != bitmap) {
                    workingBitmap.recycle()
                }
            }

            byteArrayOutputStream.close()

            // Final size check
            if (byteArray.size > MAX_IMAGE_SIZE) {
                Timber.e("Compressed image still too large: ${byteArray.size} bytes (max: $MAX_IMAGE_SIZE)")
                return null
            }

            // Encode to base64 and create data URL
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            val dataUrl = "data:image/$format;base64,$base64String"

            Timber.d("Created base64 data URL - Format: $format, Length: ${dataUrl.length} chars, Binary size: ${byteArray.size} bytes")
            return dataUrl

        } catch (e: Exception) {
            Timber.e(e, "Error converting bitmap to base64 data URL: ${e.message}")
            return null
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory while converting to base64")
            return null
        }
    }

    /**
     * Calculate appropriate inSampleSize for bitmap loading
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Enhanced validation for image URIs with detailed error reporting
     */
    fun validateImageUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                val isValid = options.outWidth > 0 && options.outHeight > 0
                val width = options.outWidth
                val height = options.outHeight

                when {
                    !isValid -> {
                        Timber.e("Image validation failed: invalid dimensions ($width x $height)")
                        false
                    }
                    width < MIN_DIMENSION || height < MIN_DIMENSION -> {
                        Timber.e("Image too small: ${width}x${height} (minimum: ${MIN_DIMENSION}x${MIN_DIMENSION})")
                        false
                    }
                    width > MAX_DIMENSION * 4 || height > MAX_DIMENSION * 4 -> {
                        Timber.e("Image too large: ${width}x${height} (maximum recommended: ${MAX_DIMENSION * 4}x${MAX_DIMENSION * 4})")
                        false
                    }
                    else -> {
                        Timber.d("Image validation passed: ${width}x${height}")
                        true
                    }
                }
            } ?: false
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception validating image URI: ${e.message}")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error validating image URI: ${e.message}")
            false
        }
    }

    /**
     * Get image dimensions without loading the full bitmap
     */
    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                if (options.outWidth > 0 && options.outHeight > 0) {
                    Pair(options.outWidth, options.outHeight)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting image dimensions: ${e.message}")
            null
        }
    }

    /**
     * Estimate the file size after compression
     */
    fun estimateCompressedSize(context: Context, uri: Uri): Long? {
        return try {
            val dimensions = getImageDimensions(context, uri) ?: return null
            val (width, height) = dimensions

            // Rough estimation based on typical compression ratios
            val pixelCount = width * height
            val estimatedBytes = when {
                pixelCount > 2_000_000 -> pixelCount / 15 // High compression for large images
                pixelCount > 1_000_000 -> pixelCount / 12 // Medium compression
                pixelCount > 500_000 -> pixelCount / 10   // Lower compression
                else -> pixelCount / 8                    // Minimal compression for small images
            }

            estimatedBytes.toLong()
        } catch (e: Exception) {
            Timber.e(e, "Error estimating compressed size: ${e.message}")
            null
        }
    }

    /**
     * Helper method to validate base64 data URL format
     */
    fun isValidBase64DataUrl(dataUrl: String): Boolean {
        return try {
            val pattern = Regex("^data:image/(jpeg|jpg|png|gif|webp);base64,[A-Za-z0-9+/]+=*$")
            val isValid = pattern.matches(dataUrl)

            if (!isValid) {
                Timber.w("Invalid base64 data URL format")
            }

            isValid
        } catch (e: Exception) {
            Timber.e(e, "Error validating base64 data URL: ${e.message}")
            false
        }
    }

    /**
     * Extract base64 content from data URL (for debugging/validation)
     */
    fun extractBase64FromDataUrl(dataUrl: String): String? {
        return try {
            if (dataUrl.startsWith("data:image/") && dataUrl.contains(";base64,")) {
                dataUrl.substringAfter(";base64,")
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting base64 from data URL: ${e.message}")
            null
        }
    }

    /**
     * Get the MIME type from a base64 data URL
     */
    fun getMimeTypeFromDataUrl(dataUrl: String): String? {
        return try {
            if (dataUrl.startsWith("data:image/") && dataUrl.contains(";base64,")) {
                val mimeType = dataUrl.substringAfter("data:").substringBefore(";base64,")
                mimeType
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting MIME type from data URL: ${e.message}")
            null
        }
    }

    /**
     * Clean up bitmap resources safely
     */
    fun recycleBitmapSafely(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error recycling bitmap")
        }
    }

    /**
     * Check if device has enough memory for image processing
     */
    fun hasEnoughMemoryForProcessing(width: Int, height: Int): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            // Estimate memory needed (4 bytes per pixel for ARGB_8888, plus overhead)
            val estimatedMemoryNeeded = (width * height * 4 * 2.5).toLong() // 2.5x overhead factor

            val hasEnough = availableMemory > estimatedMemoryNeeded

            if (!hasEnough) {
                Timber.w("Insufficient memory for image processing. Available: $availableMemory, Needed: $estimatedMemoryNeeded")
            }

            hasEnough
        } catch (e: Exception) {
            Timber.e(e, "Error checking available memory")
            true // Assume we have enough if we can't check
        }
    }
}