package com.cyberflux.qwinai

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cyberflux.qwinai.data.ImageGalleryRepository
import com.cyberflux.qwinai.databinding.ActivityImageDetailBinding
import com.cyberflux.qwinai.model.GeneratedImage
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.HapticManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ImageDetailActivity : BaseThemedActivity() {
    
    private lateinit var binding: ActivityImageDetailBinding
    private lateinit var repository: ImageGalleryRepository
    private lateinit var image: GeneratedImage
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = ImageGalleryRepository(this)
        
        image = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("image", GeneratedImage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("image")
        } ?: run {
            finish()
            return
        }
        
        setupUI()
        loadImage()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = image.fileName
        }
        
        // Enable zoom and pan
        binding.photoView.apply {
            maximumScale = 10.0f
            mediumScale = 3.0f
            minimumScale = 0.5f
        }
        
        // Setup action buttons
        binding.btnDownload.setOnClickListener {
            downloadImage()
        }
        
        binding.btnShare.setOnClickListener {
            shareImage()
        }
        
        binding.btnDelete.setOnClickListener {
            deleteImage()
        }
        
        binding.btnSetWallpaper.setOnClickListener {
            setAsWallpaper()
        }
        
        // Setup image info
        setupImageInfo()
    }
    
    private fun loadImage() {
        val file = File(image.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        Glide.with(this)
            .load(file)
            .into(binding.photoView)
    }
    
    private fun setupImageInfo() {
        binding.apply {
            textFileName.text = image.fileName
            textModel.text = image.aiModel
            textPrompt.text = image.prompt
            textResolution.text = "${image.width} × ${image.height}"
            textFileSize.text = Formatter.formatFileSize(this@ImageDetailActivity, image.fileSize)
            textTimestamp.text = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                .format(Date(image.timestamp))
            
            // Generation settings
            image.generationSettings?.let { settings ->
                textStyle.text = settings.style ?: "Default"
                textQuality.text = settings.quality ?: "Standard"
                textAspectRatio.text = settings.aspectRatio ?: "Auto"
                textSeed.text = settings.seed ?: "Random"
            } ?: run {
                cardGenerationSettings.visibility = android.view.View.GONE
            }
            
            iconDownloaded.visibility = if (image.isDownloaded) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    
    private fun downloadImage() {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    saveImageToGallery()
                }
                
                if (success) {
                    repository.markAsDownloaded(image.id)
                    binding.iconDownloaded.visibility = android.view.View.VISIBLE
                    
                    Snackbar.make(binding.root, "Image saved to gallery", Snackbar.LENGTH_SHORT)
                        .setAction("View") {
                            viewInGallery()
                        }
                        .show()
                    
                    HapticManager.customVibration(this@ImageDetailActivity, 200)
                } else {
                    Toast.makeText(this@ImageDetailActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ImageDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveImageToGallery(): Boolean {
        val file = File(image.filePath)
        if (!file.exists()) return false
        
        val bitmap = BitmapFactory.decodeFile(image.filePath) ?: return false
        
        return try {
            val filename = "AI_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI_Generated")
                }
                
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { imageUri ->
                    contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    true
                } ?: false
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/AI_Generated"
                val dir = File(imagesDir)
                if (!dir.exists()) dir.mkdirs()
                
                val imageFile = File(dir, filename)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun shareImage() {
        val file = File(image.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Generated with ${image.aiModel}: ${image.prompt}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(intent, "Share Image"))
    }
    
    private fun deleteImage() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Image")
            .setMessage("Are you sure you want to delete this image? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        repository.deleteImage(image.id)
                    }
                    
                    if (success) {
                        setResult(RESULT_OK)
                        finish()
                        HapticManager.customVibration(this@ImageDetailActivity, 200)
                    } else {
                        Toast.makeText(this@ImageDetailActivity, "Failed to delete image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setAsWallpaper() {
        val file = File(image.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(uri, "image/*")
            putExtra("mimeType", "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Set as Wallpaper"))
        } catch (e: Exception) {
            Toast.makeText(this, "No wallpaper app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun viewInGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_image_detail, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_info -> {
                showImageInfo()
                true
            }
            R.id.action_copy_prompt -> {
                copyPromptToClipboard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showImageInfo() {
        val info = buildString {
            appendLine("File: ${image.fileName}")
            appendLine("Model: ${image.aiModel}")
            appendLine("Resolution: ${image.width} × ${image.height}")
            appendLine("Size: ${Formatter.formatFileSize(this@ImageDetailActivity, image.fileSize)}")
            appendLine("Created: ${SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(image.timestamp))}")
            appendLine("Path: ${image.filePath}")
            appendLine()
            appendLine("Prompt:")
            appendLine(image.prompt)
            
            image.generationSettings?.let { settings ->
                appendLine()
                appendLine("Generation Settings:")
                settings.style?.let { appendLine("Style: $it") }
                settings.quality?.let { appendLine("Quality: $it") }
                settings.aspectRatio?.let { appendLine("Aspect Ratio: $it") }
                settings.seed?.let { appendLine("Seed: $it") }
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Image Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun copyPromptToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI Prompt", image.prompt)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
        HapticManager.customVibration(this, 50)
    }
}