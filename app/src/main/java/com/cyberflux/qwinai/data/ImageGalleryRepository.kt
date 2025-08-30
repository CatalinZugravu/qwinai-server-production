package com.cyberflux.qwinai.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import com.cyberflux.qwinai.model.GeneratedImage
import com.cyberflux.qwinai.model.GenerationSettings
import com.cyberflux.qwinai.utils.JsonUtils
import java.io.File
import java.util.*

class ImageGalleryRepository(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("image_gallery", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_IMAGES = "generated_images"
    }
    
    fun saveImage(
        filePath: String,
        aiModel: String,
        prompt: String,
        generationSettings: GenerationSettings? = null
    ): GeneratedImage {
        val file = File(filePath)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)
        
        val image = GeneratedImage(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            fileName = file.name,
            aiModel = aiModel,
            prompt = prompt,
            timestamp = System.currentTimeMillis(),
            fileSize = file.length(),
            width = options.outWidth,
            height = options.outHeight,
            generationSettings = generationSettings
        )
        
        val images = getAllImages().toMutableList()
        images.add(0, image)
        saveImages(images)
        
        return image
    }
    
    fun getAllImages(): List<GeneratedImage> {
        return try {
            val json = prefs.getString(PREF_IMAGES, "[]") ?: "[]"
            android.util.Log.d("ImageRepository", "Loading images from SharedPrefs, JSON length: ${json.length}")
            
            val images: List<GeneratedImage> = JsonUtils.jsonToGeneratedImages(json)
            android.util.Log.d("ImageRepository", "Successfully loaded ${images.size} images from JSON")
            
            // Filter out images whose files no longer exist and log any removals
            val existingImages = images.filter { image ->
                val exists = File(image.filePath).exists()
                if (!exists) {
                    android.util.Log.d("ImageRepository", "Removing non-existent image: ${image.filePath}")
                }
                exists
            }
            
            // If we filtered out any images, save the cleaned list
            if (existingImages.size != images.size) {
                android.util.Log.d("ImageRepository", "Cleaned database: ${images.size} -> ${existingImages.size} images")
                saveImages(existingImages)
            }
            
            existingImages
        } catch (e: Exception) {
            android.util.Log.e("ImageRepository", "Error reading images from database", e)
            emptyList()
        }
    }
    
    fun getImagesByModel(model: String): List<GeneratedImage> {
        return getAllImages().filter { it.aiModel.equals(model, ignoreCase = true) }
    }
    
    fun searchImages(query: String): List<GeneratedImage> {
        return getAllImages().filter { 
            it.prompt.contains(query, ignoreCase = true) || 
            it.aiModel.contains(query, ignoreCase = true) ||
            it.fileName.contains(query, ignoreCase = true)
        }
    }
    
    fun deleteImage(imageId: String): Boolean {
        val images = getAllImages().toMutableList()
        val imageToDelete = images.find { it.id == imageId } ?: return false
        
        // Delete the physical file
        val file = File(imageToDelete.filePath)
        if (file.exists()) {
            file.delete()
        }
        
        // Remove from list
        images.removeAll { it.id == imageId }
        saveImages(images)
        
        return true
    }
    
    fun deleteMultipleImages(imageIds: List<String>): Int {
        var deletedCount = 0
        imageIds.forEach { imageId ->
            if (deleteImage(imageId)) {
                deletedCount++
            }
        }
        return deletedCount
    }
    
    fun markAsDownloaded(imageId: String) {
        val images = getAllImages().toMutableList()
        val index = images.indexOfFirst { it.id == imageId }
        if (index != -1) {
            images[index] = images[index].copy(isDownloaded = true)
            saveImages(images)
        }
    }
    
    fun updateImageInfo(imageId: String, newFileName: String, newFilePath: String): Boolean {
        try {
            val images = getAllImages().toMutableList()
            android.util.Log.d("ImageRepository", "Found ${images.size} images in database")
            
            val index = images.indexOfFirst { it.id == imageId }
            android.util.Log.d("ImageRepository", "Looking for image ID: $imageId, found at index: $index")
            
            if (index != -1) {
                val oldImage = images[index]
                android.util.Log.d("ImageRepository", "Old path: ${oldImage.filePath}")
                android.util.Log.d("ImageRepository", "New path: $newFilePath")
                
                images[index] = images[index].copy(fileName = newFileName, filePath = newFilePath)
                saveImages(images)
                android.util.Log.d("ImageRepository", "Successfully updated image info")
                return true
            } else {
                android.util.Log.e("ImageRepository", "Image not found in database")
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageRepository", "Error updating image info", e)
        }
        return false
    }
    
    fun getUniqueModels(): List<String> {
        return getAllImages().map { it.aiModel }.distinct().sorted()
    }
    
    fun getImageCount(): Int = getAllImages().size
    
    fun getTotalFileSize(): Long = getAllImages().sumOf { it.fileSize }
    
    private fun saveImages(images: List<GeneratedImage>) {
        android.util.Log.d("ImageRepository", "Saving ${images.size} images to SharedPrefs")
        val json = JsonUtils.generatedImagesToJson(images)
        android.util.Log.d("ImageRepository", "Serialized JSON length: ${json.length}")
        prefs.edit().putString(PREF_IMAGES, json).apply()
        android.util.Log.d("ImageRepository", "Successfully saved images to SharedPrefs")
    }
}