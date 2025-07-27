package com.cyberflux.qwinai.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import com.cyberflux.qwinai.model.GeneratedImage
import com.cyberflux.qwinai.model.GenerationSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

class ImageGalleryRepository(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("image_gallery", Context.MODE_PRIVATE)
    private val gson = Gson()
    
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
        val json = prefs.getString(PREF_IMAGES, "[]") ?: "[]"
        val type = object : TypeToken<List<GeneratedImage>>() {}.type
        val images: List<GeneratedImage> = gson.fromJson(json, type) ?: emptyList()
        
        // Filter out images whose files no longer exist
        return images.filter { File(it.filePath).exists() }
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
    
    fun getUniqueModels(): List<String> {
        return getAllImages().map { it.aiModel }.distinct().sorted()
    }
    
    fun getImageCount(): Int = getAllImages().size
    
    fun getTotalFileSize(): Long = getAllImages().sumOf { it.fileSize }
    
    private fun saveImages(images: List<GeneratedImage>) {
        val json = gson.toJson(images)
        prefs.edit().putString(PREF_IMAGES, json).apply()
    }
}