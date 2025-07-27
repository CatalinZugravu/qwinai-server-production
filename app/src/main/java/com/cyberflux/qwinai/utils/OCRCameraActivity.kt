package com.cyberflux.qwinai.utils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.databinding.ActivityOcrCameraBinding
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Activity for handling OCR camera capture and text extraction
 */
class OCRCameraActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityOcrCameraBinding
    private var currentPhotoPath: String = ""
    private val CAMERA_PERMISSION_CODE = 101
    private val CAMERA_REQUEST_CODE = 102
    
    // Modern Activity Result API launcher for camera
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Image captured successfully
            binding.previewImage.setImageURI(Uri.parse("file://$currentPhotoPath"))

            // Show preview UI
            binding.previewGroup.visibility = android.view.View.VISIBLE
            binding.cameraInstructionsGroup.visibility = android.view.View.GONE
        } else {
            // Failed to capture image
            Toast.makeText(
                this,
                "Failed to capture image. Please try again.",
                Toast.LENGTH_SHORT
            ).show()

            // Show retry UI
            binding.btnRetry.visibility = android.view.View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOcrCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissionAndOpenCamera()
    }

    private fun setupUI() {
        // Set up back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Set up retry button
        binding.btnRetry.setOnClickListener {
            checkPermissionAndOpenCamera()
        }

        // Set up continue button
        binding.btnContinue.setOnClickListener {
            processCapturedImage()
        }
    }

    private fun checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error creating image file")
                    null
                }

                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    cameraLauncher.launch(takePictureIntent)
                }
            }
        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            // Save a file path for use with ACTION_VIEW intent
            currentPhotoPath = absolutePath
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission denied. Cannot proceed with OCR.",
                    Toast.LENGTH_LONG
                ).show()

                // Show retry UI
                binding.cameraInstructionsGroup.alpha = 0.5f
                binding.btnRetry.visibility = android.view.View.VISIBLE
            }
        }
    }

    // onActivityResult is no longer needed as we use the modern Activity Result API

    private fun processCapturedImage() {
        if (currentPhotoPath.isBlank()) {
            Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a prompt for OCR and summary
        val prompt = "I've taken a picture containing text. Please extract all the text from this image and then provide a concise summary of the content."

        // Create an intent to MainActivity with the image and prompt
        val intent = Intent(this, MainActivity::class.java).apply {
            // Pass the currentPhotoPath as a file URI
            val photoUri = Uri.parse("file://$currentPhotoPath")

            putExtra("CAMERA_IMAGE_URI", photoUri.toString())
            putExtra("INITIAL_PROMPT", prompt)
            putExtra("FEATURE_MODE", "ocr_reader")

            // Generate a unique ID for this conversation
            putExtra("CONVERSATION_ID", UUID.randomUUID().toString())

            // Set OCR-compatible model - using GPT-4o as default for OCR
            putExtra("FORCE_MODEL", ModelManager.GPT_4O_ID)
        }

        startActivity(intent)
        finish() // Close this activity
    }
}