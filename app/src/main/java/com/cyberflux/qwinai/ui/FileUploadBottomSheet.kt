package com.cyberflux.qwinai.ui

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.adapter.MediaThumbnailAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.*

class FileUploadBottomSheet : BottomSheetDialogFragment() {

    private lateinit var btnPhotos: CardView
    private lateinit var btnFiles: CardView
    private lateinit var rvMediaGrid: RecyclerView
    
    private lateinit var mediaThumbnailAdapter: MediaThumbnailAdapter
    private var onFileSelectedListener: ((Uri) -> Unit)? = null
    private var onCameraClickListener: (() -> Unit)? = null
    
    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, now launch camera
            onCameraClickListener?.invoke()
            dismiss()
        } else {
            // Permission denied
            // Could show a message or handle gracefully
        }
    }

    // Activity result launchers
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Handle multiple images
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        onFileSelectedListener?.invoke(uri)
                    }
                } else {
                    // Handle single image
                    data.data?.let { uri ->
                        onFileSelectedListener?.invoke(uri)
                    }
                }
            }
            dismiss()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Handle multiple files
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        onFileSelectedListener?.invoke(uri)
                    }
                } else {
                    // Handle single file
                    data.data?.let { uri ->
                        onFileSelectedListener?.invoke(uri)
                    }
                }
            }
            dismiss()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true

        if (readPermissionGranted) {
            loadRecentImages()
        }
    }

    companion object {
        fun newInstance(
            onFileSelected: (Uri) -> Unit,
            onCameraClick: () -> Unit
        ): FileUploadBottomSheet {
            return FileUploadBottomSheet().apply {
                this.onFileSelectedListener = onFileSelected
                this.onCameraClickListener = onCameraClick
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_file_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        checkPermissionsAndLoadImages()
    }

    private fun initViews(view: View) {
        btnPhotos = view.findViewById(R.id.btn_photos)
        btnFiles = view.findViewById(R.id.btn_files)
        rvMediaGrid = view.findViewById(R.id.rv_media_grid)
    }

    private fun setupRecyclerView() {
        mediaThumbnailAdapter = MediaThumbnailAdapter(
            context = requireContext(),
            onCameraClick = {
                // Check camera permission first
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission already granted
                    onCameraClickListener?.invoke()
                    dismiss()
                } else {
                    // Request permission
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onImageClick = { uri ->
                onFileSelectedListener?.invoke(uri)
                dismiss()
            },
            onUploadClick = {
                openImagePicker()
            }
        )

        rvMediaGrid.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = mediaThumbnailAdapter
        }
    }

    private fun setupClickListeners() {
        btnPhotos.setOnClickListener {
            openImagePicker()
        }

        btnFiles.setOnClickListener {
            openFilePicker()
        }
    }

    private fun checkPermissionsAndLoadImages() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            loadRecentImages()
        }
    }

    private fun loadRecentImages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recentImages = getRecentImages()
                withContext(Dispatchers.Main) {
                    mediaThumbnailAdapter.updateRecentImages(recentImages)
                }
            } catch (e: Exception) {
                // Handle exception silently - adapter will just show camera and upload buttons
            }
        }
    }

    private suspend fun getRecentImages(): List<Uri> = withContext(Dispatchers.IO) {
        val images = mutableListOf<Uri>()
        
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            val cursor: Cursor? = requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                
                var count = 0
                while (it.moveToNext() && count < 10) {
                    val id = it.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    images.add(contentUri)
                    count++
                }
            }
        } catch (e: Exception) {
            // Handle exception silently
        }
        
        return@withContext images
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        
        try {
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Images"))
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
            
            // Add MIME types for common file formats
            val mimeTypes = arrayOf(
                "image/*",
                "application/pdf",
                "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Select Files"))
        } catch (e: Exception) {
            // Handle exception
        }
    }
}