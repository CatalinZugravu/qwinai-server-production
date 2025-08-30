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
import android.widget.EditText
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
        
        binding.btnRename.setOnClickListener {
            showRenameDialog()
        }
        
        binding.btnCopyPrompt.setOnClickListener {
            copyPromptToClipboard()
        }
        
        binding.btnFullscreen.setOnClickListener {
            openFullscreen()
        }
        
        // Setup image info
        setupImageInfo()
    }
    
    private fun loadImage() {
        val file = File(image.filePath)
        android.util.Log.d("ImageDetail", "Loading image from: ${image.filePath}")
        android.util.Log.d("ImageDetail", "File exists: ${file.exists()}")
        
        if (!file.exists()) {
            android.util.Log.e("ImageDetail", "Image file not found at: ${image.filePath}")
            Toast.makeText(this, "Image file not found: ${file.name}", Toast.LENGTH_LONG).show()
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
                } == true
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
    
    private fun showRenameDialog() {
        val currentName = File(image.fileName).nameWithoutExtension
        val extension = File(image.fileName).extension
        
        val editText = EditText(this).apply {
            setText(currentName)
            setSelectAllOnFocus(true)
            setSingleLine(true)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(48, 16, 48, 16)
            }
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Rename Image")
            .setMessage("Enter new name:")
            .setView(editText)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    // Clear focus before proceeding to prevent IME callback issues
                    editText.clearFocus()
                    renameImageFile(newName, extension)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                editText.clearFocus()
                dialog.dismiss()
            }
            .create()
        
        dialog.show()
        
        // Request focus and show keyboard after dialog is shown
        editText.post {
            editText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun renameImageFile(newName: String, extension: String) {
        val currentFile = File(image.filePath)
        val newFileName = "$newName.$extension"
        val newFile = File(currentFile.parent, newFileName)
        
        // Debug logging
        android.util.Log.d("ImageDetail", "Renaming from: ${currentFile.absolutePath}")
        android.util.Log.d("ImageDetail", "Renaming to: ${newFile.absolutePath}")
        android.util.Log.d("ImageDetail", "Current file exists: ${currentFile.exists()}")
        android.util.Log.d("ImageDetail", "Parent directory exists: ${currentFile.parent != null && File(currentFile.parent!!).exists()}")
        
        if (!currentFile.exists()) {
            Toast.makeText(this, "Original file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (newFile.exists()) {
            Toast.makeText(this, "A file with this name already exists", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val renameSuccess = currentFile.renameTo(newFile)
            android.util.Log.d("ImageDetail", "Rename success: $renameSuccess")
            
            if (renameSuccess && newFile.exists()) {
                // Update the database
                lifecycleScope.launch {
                    try {
                        val success = withContext(Dispatchers.IO) {
                            repository.updateImageInfo(image.id, newFileName, newFile.absolutePath)
                        }
                        
                        android.util.Log.d("ImageDetail", "Database update success: $success")
                        
                        if (success) {
                            // Update the UI immediately
                            supportActionBar?.title = newFileName
                            
                            // Create updated image object
                            val updatedImage = image.copy(
                                fileName = newFileName, 
                                filePath = newFile.absolutePath
                            )
                            image = updatedImage
                            
                            // Refresh the info display
                            setupImageInfo()
                            
                            // Reload the image with new path
                            loadImage()
                            
                            Toast.makeText(this@ImageDetailActivity, "Image renamed successfully", Toast.LENGTH_SHORT).show()
                            HapticManager.customVibration(this@ImageDetailActivity, 50)
                        } else {
                            Toast.makeText(this@ImageDetailActivity, "Failed to update database", Toast.LENGTH_SHORT).show()
                            // Try to rename back if database update failed
                            newFile.renameTo(currentFile)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ImageDetail", "Database update error", e)
                        Toast.makeText(this@ImageDetailActivity, "Error updating database: ${e.message}", Toast.LENGTH_LONG).show()
                        // Try to rename back if database update failed
                        newFile.renameTo(currentFile)
                    }
                }
            } else {
                Toast.makeText(this, "Failed to rename file", Toast.LENGTH_SHORT).show()
                android.util.Log.e("ImageDetail", "File rename failed or new file doesn't exist after rename")
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageDetail", "Rename operation error", e)
            Toast.makeText(this, "Error renaming file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun openFullscreen() {
        // Toggle system UI visibility for fullscreen experience
        if (isInFullscreenMode) {
            showSystemUI()
            // Show all UI elements
            supportActionBar?.show()
            binding.toolbar.visibility = android.view.View.VISIBLE
            binding.btnFullscreen.show()
            // Show the scrollable content with image info
            binding.root.getChildAt(1).visibility = android.view.View.VISIBLE // NestedScrollView
            
            // Remove fullscreen overlay if it exists
            val childCount = binding.root.childCount
            if (childCount > 2) { // AppBarLayout + NestedScrollView + potential overlay
                binding.root.removeViewAt(childCount - 1) // Remove the last added fullscreen overlay
            }
            
            isInFullscreenMode = false
        } else {
            hideSystemUI()
            // Hide all UI elements except the image
            supportActionBar?.hide()
            binding.toolbar.visibility = android.view.View.GONE
            binding.btnFullscreen.hide()
            // Hide the scrollable content, show only image
            binding.root.getChildAt(1).visibility = android.view.View.GONE // NestedScrollView
            isInFullscreenMode = true
            
            // Show only the PhotoView in fullscreen
            showImageOnlyFullscreen()
        }
    }
    
    private fun showImageOnlyFullscreen() {
        // Create a temporary fullscreen image view
        val fullscreenLayout = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        // Create a new PhotoView for fullscreen
        val fullscreenPhotoView = com.github.chrisbanes.photoview.PhotoView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            maximumScale = 10.0f
            mediumScale = 3.0f
            minimumScale = 0.5f
            
            // Exit fullscreen on tap
            setOnPhotoTapListener { _, _, _ ->
                openFullscreen() // Toggle back to normal mode
            }
        }
        
        // Load the same image
        val file = File(image.filePath)
        if (file.exists()) {
            Glide.with(this@ImageDetailActivity)
                .load(file)
                .into(fullscreenPhotoView)
        }
        
        fullscreenLayout.addView(fullscreenPhotoView)
        binding.root.addView(fullscreenLayout)
    }
    
    private var isInFullscreenMode = false
    
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
    
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}