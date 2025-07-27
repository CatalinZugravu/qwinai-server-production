package com.cyberflux.qwinai

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cyberflux.qwinai.adapter.ImageGalleryAdapter
import com.cyberflux.qwinai.data.ImageGalleryRepository
import com.cyberflux.qwinai.databinding.ActivityImageGalleryBinding
import com.cyberflux.qwinai.model.GeneratedImage
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.HapticManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ImageGalleryActivity : BaseThemedActivity(), ImageGalleryAdapter.OnImageClickListener {
    
    private lateinit var binding: ActivityImageGalleryBinding
    private lateinit var repository: ImageGalleryRepository
    private lateinit var adapter: ImageGalleryAdapter
    private var allImages = listOf<GeneratedImage>()
    private var filteredImages = listOf<GeneratedImage>()
    private var selectedImages = mutableSetOf<String>()
    private var isSelectionMode = false
    private var currentFilter = "All Models"
    
    private val shareImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Handle result if needed */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = ImageGalleryRepository(this)
        setupUI()
        loadImages()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Image Gallery"
        }
        
        // Setup RecyclerView
        adapter = ImageGalleryAdapter(this)
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@ImageGalleryActivity, 2)
            adapter = this@ImageGalleryActivity.adapter
        }
        
        // Setup FAB
        binding.fabToggleView.setOnClickListener {
            toggleViewMode()
        }
        
        // Setup filter chips
        setupFilterChips()
        
        // Setup search
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterImages(query)
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                filterImages(newText)
                return true
            }
        })
        
        // Setup selection mode toggle
        binding.btnSelectMode.setOnClickListener {
            toggleSelectionMode()
        }
        
        // Setup batch actions
        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedImages()
        }
        
        binding.btnShareSelected.setOnClickListener {
            shareSelectedImages()
        }
    }
    
    private fun setupFilterChips() {
        val models = repository.getUniqueModels()
        
        // Add "All Models" chip
        val allChip = Chip(this).apply {
            text = "All Models"
            isCheckable = true
            isChecked = true
            setOnClickListener {
                filterByModel("All Models")
            }
        }
        binding.chipGroupFilters.addView(allChip)
        
        // Add model-specific chips
        models.forEach { model ->
            val chip = Chip(this).apply {
                text = model
                isCheckable = true
                setOnClickListener {
                    filterByModel(model)
                }
            }
            binding.chipGroupFilters.addView(chip)
        }
    }
    
    private fun loadImages() {
        lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) {
                repository.getAllImages()
            }
            
            allImages = images
            filteredImages = images
            adapter.submitList(filteredImages)
            
            updateUI()
        }
    }
    
    private fun updateUI() {
        val count = filteredImages.size
        val totalSize = filteredImages.sumOf { it.fileSize }
        
        binding.textImageCount.text = "$count images"
        binding.textTotalSize.text = Formatter.formatFileSize(this, totalSize)
        
        // Update selection UI
        if (isSelectionMode) {
            binding.selectionToolbar.visibility = android.view.View.VISIBLE
            binding.textSelectedCount.text = "${selectedImages.size} selected"
            binding.btnDeleteSelected.isEnabled = selectedImages.isNotEmpty()
            binding.btnShareSelected.isEnabled = selectedImages.isNotEmpty()
        } else {
            binding.selectionToolbar.visibility = android.view.View.GONE
        }
        
        // Show empty state if no images
        if (filteredImages.isEmpty()) {
            binding.emptyState.visibility = android.view.View.VISIBLE
            binding.recyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyState.visibility = android.view.View.GONE
            binding.recyclerView.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun filterByModel(model: String) {
        currentFilter = model
        
        // Update chip selection
        for (i in 0 until binding.chipGroupFilters.childCount) {
            val chip = binding.chipGroupFilters.getChildAt(i) as Chip
            chip.isChecked = chip.text == model
        }
        
        filteredImages = if (model == "All Models") {
            allImages
        } else {
            allImages.filter { it.aiModel.equals(model, ignoreCase = true) }
        }
        
        adapter.submitList(filteredImages)
        updateUI()
    }
    
    private fun filterImages(query: String?) {
        filteredImages = if (query.isNullOrBlank()) {
            if (currentFilter == "All Models") allImages else allImages.filter { it.aiModel.equals(currentFilter, ignoreCase = true) }
        } else {
            val baseList = if (currentFilter == "All Models") allImages else allImages.filter { it.aiModel.equals(currentFilter, ignoreCase = true) }
            baseList.filter { 
                it.prompt.contains(query, ignoreCase = true) || 
                it.fileName.contains(query, ignoreCase = true)
            }
        }
        
        adapter.submitList(filteredImages)
        updateUI()
    }
    
    private fun toggleViewMode() {
        val layoutManager = binding.recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.fabToggleView.setImageResource(R.drawable.ic_grid_view)
            adapter.setViewType(ImageGalleryAdapter.VIEW_TYPE_LIST)
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
            binding.fabToggleView.setImageResource(R.drawable.ic_list_view)
            adapter.setViewType(ImageGalleryAdapter.VIEW_TYPE_GRID)
        }
        
        HapticManager.customVibration(this, 50)
    }
    
    private fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        selectedImages.clear()
        adapter.setSelectionMode(isSelectionMode)
        updateUI()
        
        HapticManager.customVibration(this, 100)
    }
    
    private fun deleteSelectedImages() {
        if (selectedImages.isEmpty()) return
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Images")
            .setMessage("Are you sure you want to delete ${selectedImages.size} images? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val deletedCount = withContext(Dispatchers.IO) {
                        repository.deleteMultipleImages(selectedImages.toList())
                    }
                    
                    selectedImages.clear()
                    loadImages()
                    
                    Snackbar.make(
                        binding.root,
                        "$deletedCount images deleted",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    
                    HapticManager.customVibration(this@ImageGalleryActivity, 200)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun shareSelectedImages() {
        if (selectedImages.isEmpty()) return
        
        val selectedImageObjects = filteredImages.filter { it.id in selectedImages }
        
        if (selectedImageObjects.size == 1) {
            shareImage(selectedImageObjects.first())
        } else {
            shareMultipleImages(selectedImageObjects)
        }
    }
    
    private fun shareImage(image: GeneratedImage) {
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
    
    private fun shareMultipleImages(images: List<GeneratedImage>) {
        val uris = ArrayList<Uri>()
        
        images.forEach { image ->
            val file = File(image.filePath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                uris.add(uri)
            }
        }
        
        if (uris.isEmpty()) {
            Toast.makeText(this, "No valid images to share", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, "AI Generated Images from DeepSeek Chat")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(intent, "Share Images"))
    }
    
    override fun onImageClick(image: GeneratedImage) {
        if (isSelectionMode) {
            if (selectedImages.contains(image.id)) {
                selectedImages.remove(image.id)
            } else {
                selectedImages.add(image.id)
            }
            adapter.setSelectedImages(selectedImages)
            updateUI()
        } else {
            openImageDetail(image)
        }
    }
    
    override fun onImageLongClick(image: GeneratedImage) {
        if (!isSelectionMode) {
            toggleSelectionMode()
            selectedImages.add(image.id)
            adapter.setSelectedImages(selectedImages)
            updateUI()
        }
    }
    
    private fun openImageDetail(image: GeneratedImage) {
        val intent = Intent(this, ImageDetailActivity::class.java).apply {
            putExtra("image", image)
        }
        startActivity(intent)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_image_gallery, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_clear_all -> {
                showClearAllDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSortDialog() {
        val options = arrayOf("Newest First", "Oldest First", "Model Name", "File Size")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Sort Images")
            .setItems(options) { _, which ->
                sortImages(which)
            }
            .show()
    }
    
    private fun sortImages(sortType: Int) {
        filteredImages = when (sortType) {
            0 -> filteredImages.sortedByDescending { it.timestamp }
            1 -> filteredImages.sortedBy { it.timestamp }
            2 -> filteredImages.sortedBy { it.aiModel }
            3 -> filteredImages.sortedByDescending { it.fileSize }
            else -> filteredImages
        }
        
        adapter.submitList(filteredImages)
    }
    
    private fun showClearAllDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear All Images")
            .setMessage("Are you sure you want to delete all images? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                lifecycleScope.launch {
                    val allImageIds = allImages.map { it.id }
                    val deletedCount = withContext(Dispatchers.IO) {
                        repository.deleteMultipleImages(allImageIds)
                    }
                    
                    loadImages()
                    
                    Snackbar.make(
                        binding.root,
                        "All images cleared ($deletedCount deleted)",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onBackPressed() {
        if (isSelectionMode) {
            toggleSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}