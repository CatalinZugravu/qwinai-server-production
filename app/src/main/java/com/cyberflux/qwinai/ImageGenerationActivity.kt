package com.cyberflux.qwinai

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.cyberflux.qwinai.adapter.ImageOptionSpinnerAdapter
import com.cyberflux.qwinai.databinding.ActivityImageGenerationBinding
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.ImageGenerationOption
import com.cyberflux.qwinai.model.GeneratedImage
import com.cyberflux.qwinai.model.GenerationSettings
import com.cyberflux.qwinai.data.ImageGalleryRepository
import com.cyberflux.qwinai.network.ImageGenerationHttpClient
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.ImageGenerationUtils
import com.cyberflux.qwinai.utils.ImageUploadUtils
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.ads.AdConfig
import com.cyberflux.qwinai.ads.AdManager
import com.cyberflux.qwinai.credits.CreditManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class ImageGenerationActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityImageGenerationBinding
    private lateinit var imageGalleryRepository: ImageGalleryRepository

    // Generation state
    private var generationJob: Job? = null
    private var currentImageBitmap: Bitmap? = null
    private var isGenerating = false
    private var statusUpdateJob: Job? = null
    private var particleAnimator: ValueAnimator? = null
    private var errorTimer: Handler? = null

    // Selected options for text-to-image
    private var selectedModel: String = ModelManager.DALLE_3_ID
    private var selectedSize: String = "1024x1024"
    private var selectedStyle: String = "vivid"
    private var selectedQuality: String = "standard"
    private var selectedOutputFormat: String = "url"
    private var selectedAspectRatio: String = "landscape_4_3"
    private var negativePrompt: String = ""
    private var guidanceScale: Float = 7.5f
    private var numInferenceSteps: Int = 25
    private var numImages: Int = 1
    private var seed: Int? = null
    private var enableSafetyChecker: Boolean = true
    private var enableWatermark: Boolean = false
    private var selectedColors = ArrayList<Triple<Int, Int, Int>>()

    // Image-to-image specific parameters
    private var selectedSourceImageUri: Uri? = null
    private val additionalImageUris = mutableListOf<Uri>()
    private var strength: Float = 0.95f
    private var selectedAspectRatioFormat: String = "16:9"
    private var safetyTolerance: String = "2"
    private var isImageToImageMode: Boolean = false

    // Activity result launchers for image selection
    private lateinit var selectImageLauncher: ActivityResultLauncher<String>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Uri>
    private lateinit var selectAdditionalImageLauncher: ActivityResultLauncher<String>

    // UI elements for text-to-image
    private lateinit var advancedOptionsLayout: ConstraintLayout
    private lateinit var negativePromptLayout: ConstraintLayout
    private lateinit var guidanceScaleLayout: ConstraintLayout
    private lateinit var inferenceStepsLayout: ConstraintLayout
    private lateinit var numImagesLayout: ConstraintLayout
    private lateinit var seedLayout: ConstraintLayout
    private lateinit var safetyCheckerLayout: ConstraintLayout
    private lateinit var watermarkLayout: ConstraintLayout
    private lateinit var colorsLayout: ConstraintLayout
    private lateinit var outputFormatLayout: LinearLayout

    // Controls
    private lateinit var tvNegativePrompt: TextView
    private lateinit var guidanceScaleSeekBar: SeekBar
    private lateinit var tvGuidanceScaleValue: TextView
    private lateinit var inferenceStepsSeekBar: SeekBar
    private lateinit var tvInferenceStepsValue: TextView
    private lateinit var numImagesSeekBar: SeekBar
    private lateinit var tvNumImagesValue: TextView
    private lateinit var etSeed: EditText
    private lateinit var btnRandomSeed: Button
    private lateinit var switchSafetyChecker: SwitchMaterial
    private lateinit var switchWatermark: SwitchMaterial
    private lateinit var colorChipGroup: ChipGroup
    private lateinit var btnAdvancedOptions: ImageButton
    private lateinit var etNegativePrompt: EditText
    private lateinit var btnAddColor: Button
    private lateinit var tvImageCaption: TextView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var tvCharacterCount: TextView
    private lateinit var spinnerOutputFormat: Spinner

    // UI elements for image-to-image
    private lateinit var imageUploadCard: CardView
    private lateinit var strengthLayout: ConstraintLayout
    private lateinit var aspectRatioLayout: ConstraintLayout
    private lateinit var safetyToleranceLayout: ConstraintLayout
    private lateinit var multiImageLayout: ConstraintLayout
    private lateinit var sourceImageContainer: FrameLayout
    private lateinit var ivSourceImage: ImageView
    private lateinit var sourceImagePlaceholder: LinearLayout
    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var seekBarStrength: SeekBar
    private lateinit var tvStrengthValue: TextView
    private lateinit var spinnerAspectRatio: Spinner
    private lateinit var seekBarSafetyTolerance: SeekBar
    private lateinit var tvSafetyToleranceValue: TextView
    private lateinit var additionalImagesChipGroup: ChipGroup
    private lateinit var btnAddMoreImages: Button
    private lateinit var tvMultiImageCount: TextView

    // Enhanced Credit and Ad Management
    private lateinit var creditManager: CreditManager
    private lateinit var adManager: AdManager
    private var rewardedAd: RewardedAd? = null
    private var isLoadingAd = false

    // For storage permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            downloadGeneratedImage()
        } else {
            Toast.makeText(
                this,
                "Storage permission needed to save images",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Notification constants
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "image_download_channel"
        private const val DOWNLOAD_NOTIFICATION_ID = 1001
        private const val SHARE_NOTIFICATION_ID = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityImageGenerationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        creditManager = CreditManager.getInstance(this)
        adManager = AdManager.getInstance(this)
        imageGalleryRepository = ImageGalleryRepository(this)

        // Initialize notification channel
        createNotificationChannel()

        // Initialize views
        findAllViews()
        findImageToImageViews()

        // Initialize activity result launchers
        initializeActivityResultLaunchers()

        // Set max length for prompt input with character counter
        setupPromptInputWithCounter()

        // CRITICAL: Initialize default values FIRST before anything else
        selectedModel = ModelManager.DALLE_3_ID
        selectedQuality = "standard"
        selectedSize = "1024x1024"
        selectedStyle = "vivid"
        selectedOutputFormat = "url"
        selectedAspectRatio = "landscape_4_3"
        isImageToImageMode = false

        Timber.d("Initial setup: model=$selectedModel, quality=$selectedQuality, mode=${if (isImageToImageMode) "I2I" else "T2I"}")

        // Initialize advanced options
        if (::guidanceScaleSeekBar.isInitialized &&
            ::inferenceStepsSeekBar.isInitialized) {
            initializeAdvancedOptions()
        } else {
            Timber.e("Some required views were not initialized")
            hideAllAdvancedOptions()
        }

        setupModelSpinner()
        setupOptionSpinners()
        setupButtons()
        setupInfoButtons()

        updateCreditsInfo()

        // Initialize ad system
        loadRewardedAd()

        // Start with empty state
        showEmptyState()

        // Final button state update after everything is initialized
        updateGenerateButtonState()
    }



    private fun loadRewardedAd() {
        if (isLoadingAd || rewardedAd != null) return

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(this, AdConfig.ADMOB_REWARDED_AD_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Timber.e("Ad failed to load: ${adError.message}")
                rewardedAd = null
                isLoadingAd = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Timber.d("Rewarded ad loaded successfully")
                rewardedAd = ad
                isLoadingAd = false
            }
        })
    }

    private fun showRewardedAd() {
        adManager.showRewardedAd(
            this,
            CreditManager.CreditType.IMAGE_GENERATION,
            AdManager.AdTrigger.MANUAL_REWARD,
            object : AdManager.AdCallback {
                override fun onAdShown(trigger: AdManager.AdTrigger) {
                    Timber.d("Rewarded ad shown")
                }

                override fun onAdFailed(trigger: AdManager.AdTrigger, error: String) {
                    Toast.makeText(this@ImageGenerationActivity, "Ad not ready yet. Please try again in a moment.", Toast.LENGTH_SHORT).show()
                    loadRewardedAd()
                }

                override fun onAdClosed(trigger: AdManager.AdTrigger) {
                    updateCreditsInfo()
                    updateGenerateButtonState()
                }

                override fun onRewardEarned(trigger: AdManager.AdTrigger, amount: Int) {
                    val totalCredits = creditManager.getImageCredits()
                    Toast.makeText(
                        this@ImageGenerationActivity,
                        "You earned $amount credits! Total: $totalCredits",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    updateCreditsInfo()
                    updateGenerateButtonState()
                }
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Image Downloads"
            val descriptionText = "Interactive notifications for image download completion with view and share actions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupPromptInputWithCounter() {
        binding.etImagePrompt.filters = arrayOf(InputFilter.LengthFilter(4000))

        binding.etImagePrompt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val currentLength = s?.length ?: 0
                tvCharacterCount.text = "$currentLength/4000"

                // Change color when approaching limit
                val color = when {
                    currentLength >= 3800 -> ContextCompat.getColor(this@ImageGenerationActivity, android.R.color.holo_red_dark)
                    currentLength >= 3500 -> ContextCompat.getColor(this@ImageGenerationActivity, android.R.color.holo_orange_dark)
                    else -> ContextCompat.getColor(this@ImageGenerationActivity, android.R.color.darker_gray)
                }
                tvCharacterCount.setTextColor(color)

                // Update button state whenever prompt text changes
                updateGenerateButtonState()
            }
        })
    }

    private fun setupInfoButtons() {
        // Negative Prompt Info
        findViewById<ImageButton>(R.id.btnInfoNegativePrompt)?.setOnClickListener {
            showInfoDialog(
                "Negative Prompt",
                "Describe what you DON'T want in your image. The AI will try to avoid generating these elements. Examples: 'blurry, low quality, distorted faces, extra limbs'"
            )
        }

        // Guidance Scale Info
        findViewById<ImageButton>(R.id.btnInfoGuidanceScale)?.setOnClickListener {
            showInfoDialog(
                "Guidance Scale",
                "Controls how closely the AI follows your prompt. Lower values (1-7) allow more creativity and variation. Higher values (8-20) stick more strictly to your description but may reduce creativity."
            )
        }

        // Inference Steps Info
        findViewById<ImageButton>(R.id.btnInfoInferenceSteps)?.setOnClickListener {
            showInfoDialog(
                "Inference Steps",
                "Number of denoising steps the AI takes to generate the image. More steps generally mean higher quality but take longer. 20-30 steps are usually sufficient for most images."
            )
        }

        // Number of Images Info
        findViewById<ImageButton>(R.id.btnInfoNumImages)?.setOnClickListener {
            showInfoDialog(
                "Number of Images",
                "Generate multiple variations of your prompt in one request. Each image multiplies the credit cost. Maximum varies by model."
            )
        }

        // Seed Info
        findViewById<ImageButton>(R.id.btnInfoSeed)?.setOnClickListener {
            showInfoDialog(
                "Seed",
                "A number that controls randomness. Using the same seed with the same prompt and settings will generate identical images. Leave empty for random results."
            )
        }

        // Safety Checker Info
        findViewById<ImageButton>(R.id.btnInfoSafetyChecker)?.setOnClickListener {
            showInfoDialog(
                "Safety Checker",
                "Automatically filters out potentially inappropriate content. Recommended to keep enabled. Disabling may allow more creative freedom but could generate unsuitable content."
            )
        }

        // Watermark Info (SeedDream)
        findViewById<ImageButton>(R.id.btnInfoWatermark)?.setOnClickListener {
            showInfoDialog(
                "Watermark",
                "Adds an invisible watermark to the generated image for identification purposes. Does not affect image quality or appearance."
            )
        }

        // Custom Colors Info
        findViewById<ImageButton>(R.id.btnInfoColors)?.setOnClickListener {
            showInfoDialog(
                "Custom Colors",
                "Specify preferred colors for your image. The AI will try to incorporate these colors into the generated artwork. Works best with Recraft v3 model."
            )
        }

        // Strength Info (Image-to-Image)
        findViewById<ImageButton>(R.id.btnInfoStrength)?.setOnClickListener {
            showInfoDialog(
                "Strength",
                "Controls how much the output differs from the input image. Lower values (0.1-0.5) make small changes, keeping the original structure. Higher values (0.6-1.0) allow more dramatic transformations."
            )
        }

        // Aspect Ratio Info
        findViewById<ImageButton>(R.id.btnInfoAspectRatio)?.setOnClickListener {
            showInfoDialog(
                "Aspect Ratio",
                "The width-to-height ratio of the generated image. Choose based on your intended use: 16:9 for landscapes/videos, 9:16 for portraits/social media, 1:1 for square images."
            )
        }

        // Safety Tolerance Info
        findViewById<ImageButton>(R.id.btnInfoSafetyTolerance)?.setOnClickListener {
            showInfoDialog(
                "Safety Tolerance",
                "Adjusts the strictness of content filtering. Level 1 is most restrictive, Level 6 is most permissive. Higher levels allow more creative content but may generate unsuitable images."
            )
        }

        // Multi-Image Info
        findViewById<ImageButton>(R.id.btnInfoMultiImage)?.setOnClickListener {
            showInfoDialog(
                "Additional Images",
                "Combine multiple source images for more complex transformations. The AI will blend elements from all provided images according to your prompt. Maximum 4 images total."
            )
        }

        // Source Image Info
        findViewById<ImageButton>(R.id.btnInfoSourceImage)?.setOnClickListener {
            showInfoDialog(
                "Source Image",
                "Upload an image to transform or use as reference. The AI will modify this image according to your prompt while preserving its basic structure and composition."
            )
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun initializeActivityResultLaunchers() {
        // Select image from gallery
        selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleSelectedImage(it) }
        }

        // Take photo with camera
        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                selectedSourceImageUri?.let { handleSelectedImage(it) }
            }
        }

        // Select additional images for multi-image models
        selectAdditionalImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleAdditionalImage(it) }
        }
    }

    private fun findAllViews() {
        try {
            advancedOptionsLayout = findViewById(R.id.advancedOptionsLayout)
            negativePromptLayout = findViewById(R.id.negativePromptLayout)
            guidanceScaleLayout = findViewById(R.id.guidanceScaleLayout)
            inferenceStepsLayout = findViewById(R.id.inferenceStepsLayout)
            numImagesLayout = findViewById(R.id.numImagesLayout)
            seedLayout = findViewById(R.id.seedLayout)
            safetyCheckerLayout = findViewById(R.id.safetyCheckerLayout)
            watermarkLayout = findViewById(R.id.watermarkLayout)
            colorsLayout = findViewById(R.id.colorsLayout)
            outputFormatLayout = findViewById(R.id.outputFormatLayout)

            // Controls
            tvNegativePrompt = findViewById(R.id.tvNegativePromptLabel)
            guidanceScaleSeekBar = findViewById(R.id.seekBarGuidanceScale)
            tvGuidanceScaleValue = findViewById(R.id.tvGuidanceScaleValue)
            inferenceStepsSeekBar = findViewById(R.id.seekBarInferenceSteps)
            tvInferenceStepsValue = findViewById(R.id.tvInferenceStepsValue)
            numImagesSeekBar = findViewById(R.id.seekBarNumImages)
            tvNumImagesValue = findViewById(R.id.tvNumImagesValue)
            etSeed = findViewById(R.id.etSeed)
            btnRandomSeed = findViewById(R.id.btnRandomSeed)
            switchSafetyChecker = findViewById(R.id.switchSafetyChecker)
            switchWatermark = findViewById(R.id.switchWatermark)
            colorChipGroup = findViewById(R.id.colorChipGroup)
            btnAdvancedOptions = findViewById(R.id.btnAdvancedOptions)
            etNegativePrompt = findViewById(R.id.etNegativePrompt)
            btnAddColor = findViewById(R.id.btnAddColor)
            tvImageCaption = findViewById(R.id.tvImageCaption)
            lottieAnimationView = findViewById(R.id.lottieAnimationView)
            tvCharacterCount = findViewById(R.id.tvCharacterCount)
            spinnerOutputFormat = findViewById(R.id.spinnerOutputFormat)

            Timber.d("Views initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error finding views")
        }
    }

    private fun findImageToImageViews() {
        try {
            imageUploadCard = findViewById(R.id.imageUploadCard)
            sourceImageContainer = findViewById(R.id.sourceImageContainer)
            ivSourceImage = findViewById(R.id.ivSourceImage)
            sourceImagePlaceholder = findViewById(R.id.sourceImagePlaceholder)
            btnSelectImage = findViewById(R.id.btnSelectImage)
            btnTakePhoto = findViewById(R.id.btnTakePhoto)

            // Parameter layouts
            strengthLayout = findViewById(R.id.strengthLayout)
            aspectRatioLayout = findViewById(R.id.aspectRatioLayout)
            safetyToleranceLayout = findViewById(R.id.safetyToleranceLayout)
            multiImageLayout = findViewById(R.id.multiImageLayout)

            // Parameter controls
            seekBarStrength = findViewById(R.id.seekBarStrength)
            tvStrengthValue = findViewById(R.id.tvStrengthValue)
            spinnerAspectRatio = findViewById(R.id.spinnerAspectRatio)
            seekBarSafetyTolerance = findViewById(R.id.seekBarSafetyTolerance)
            tvSafetyToleranceValue = findViewById(R.id.tvSafetyToleranceValue)
            additionalImagesChipGroup = findViewById(R.id.additionalImagesChipGroup)
            btnAddMoreImages = findViewById(R.id.btnAddMoreImages)
            tvMultiImageCount = findViewById(R.id.tvMultiImageCount)

            // Set click listeners
            sourceImageContainer.setOnClickListener {
                selectImageLauncher.launch("image/*")
            }

            btnSelectImage.setOnClickListener {
                selectImageLauncher.launch("image/*")
            }

            btnTakePhoto.setOnClickListener {
                takePhoto()
            }

            btnAddMoreImages.setOnClickListener {
                if (additionalImageUris.size < ModelValidator.getMaxSourceImages(selectedModel) - 1) {
                    selectAdditionalImageLauncher.launch("image/*")
                } else {
                    Toast.makeText(this, "Maximum number of images reached", Toast.LENGTH_SHORT).show()
                }
            }

            // Setup seekbars
            setupImageToImageSeekBars()

        } catch (e: Exception) {
            Timber.e(e, "Error finding image-to-image views")
        }
    }

    private fun setupImageToImageSeekBars() {
        try {
            // Strength seekbar
            seekBarStrength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    strength = (progress / 100f) + 0.1f // 0.1 to 1.0
                    tvStrengthValue.text = String.format("%.2f", strength)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Safety tolerance seekbar
            seekBarSafetyTolerance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val level = progress + 1 // 1 to 6
                    safetyTolerance = level.toString()
                    tvSafetyToleranceValue.text = safetyTolerance
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (e: Exception) {
            Timber.e(e, "Error setting up image-to-image seekbars")
        }
    }

    private fun initializeAdvancedOptions() {
        try {
            // Setup event listeners
            guidanceScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    guidanceScale = when (selectedModel) {
                        ModelManager.SEEDREAM_3_ID -> (progress / 10f) + 1f // 1.0 to 10.0
                        else -> (progress / 10f) + 1f // 1.0 to 20.0
                    }
                    tvGuidanceScaleValue.text = String.format("%.1f", guidanceScale)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            inferenceStepsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    numInferenceSteps = when (selectedModel) {
                        ModelManager.FLUX_SCHNELL_ID, ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> progress + 1 // 1 to 12
                        else -> progress + 1 // 1 to 50 for others
                    }
                    tvInferenceStepsValue.text = numInferenceSteps.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            numImagesSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val maxImages = ModelValidator.getMaxOutputImages(selectedModel)
                    numImages = (progress + 1).coerceAtMost(maxImages)
                    tvNumImagesValue.text = numImages.toString()
                    updateCreditsInfo() // Update credits when number of images changes
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Random seed button
            btnRandomSeed.setOnClickListener {
                val randomSeed = Random.nextInt(1, Int.MAX_VALUE)
                etSeed.setText(randomSeed.toString())
            }

            // Safety checker switch
            switchSafetyChecker.setOnCheckedChangeListener { _, isChecked ->
                enableSafetyChecker = isChecked
            }

            // Watermark switch
            switchWatermark.setOnCheckedChangeListener { _, isChecked ->
                enableWatermark = isChecked
            }

            // Setup color chooser button
            btnAddColor.setOnClickListener {
                showColorPicker()
            }

            // Initially hide all advanced options
            hideAllAdvancedOptions()
        } catch (e: Exception) {
            Timber.e(e, "Error initializing advanced options")
            hideAllAdvancedOptions()
        }
    }

    private fun hideAllAdvancedOptions() {
        try {
            advancedOptionsLayout.visibility = View.GONE
            negativePromptLayout.visibility = View.GONE
            guidanceScaleLayout.visibility = View.GONE
            inferenceStepsLayout.visibility = View.GONE
            numImagesLayout.visibility = View.GONE
            seedLayout.visibility = View.GONE
            safetyCheckerLayout.visibility = View.GONE
            watermarkLayout.visibility = View.GONE
            colorsLayout.visibility = View.GONE

            // Hide image-to-image specific layouts
            strengthLayout.visibility = View.GONE
            aspectRatioLayout.visibility = View.GONE
            safetyToleranceLayout.visibility = View.GONE
            multiImageLayout.visibility = View.GONE
        } catch (e: Exception) {
            Timber.e(e, "Error hiding advanced options")
        }
    }

    private fun updateAdvancedOptions() {
        try {
            // Reset all to hidden first
            negativePromptLayout.visibility = View.GONE
            guidanceScaleLayout.visibility = View.GONE
            inferenceStepsLayout.visibility = View.GONE
            numImagesLayout.visibility = View.GONE
            seedLayout.visibility = View.GONE
            safetyCheckerLayout.visibility = View.GONE
            watermarkLayout.visibility = View.GONE
            colorsLayout.visibility = View.GONE

            // Hide image-to-image specific options
            strengthLayout.visibility = View.GONE
            aspectRatioLayout.visibility = View.GONE
            safetyToleranceLayout.visibility = View.GONE
            multiImageLayout.visibility = View.GONE

            // For DALL-E 3, there are no advanced options, hide everything
            if (selectedModel == ModelManager.DALLE_3_ID) {
                advancedOptionsLayout.visibility = View.GONE
                findViewById<CardView>(R.id.advancedOptionsCard)?.visibility = View.GONE
                return
            }

            // For other models, show appropriate options
            advancedOptionsLayout.visibility = View.VISIBLE

            when (selectedModel) {
                "stable-diffusion-v35-large" -> {
                    negativePromptLayout.visibility = View.VISIBLE
                    guidanceScaleLayout.visibility = View.VISIBLE
                    inferenceStepsLayout.visibility = View.VISIBLE
                    numImagesLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    safetyCheckerLayout.visibility = View.VISIBLE

                    setupNumImagesSeekBar(4) // Max 4 images
                    guidanceScaleSeekBar.max = 190
                    inferenceStepsSeekBar.max = 49
                    findViewById<TextView>(R.id.tvGuidanceScaleLabel)?.text = "Guidance Scale (1.0 - 20.0)"
                    findViewById<TextView>(R.id.tvInferenceStepsLabel)?.text = "Inference Steps (1 - 50)"
                }

                ModelManager.SEEDREAM_3_ID -> {
                    guidanceScaleLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    watermarkLayout.visibility = View.VISIBLE

                    guidanceScaleSeekBar.max = 90
                    findViewById<TextView>(R.id.tvGuidanceScaleLabel)?.text = "Guidance Scale (1.0 - 10.0)"
                    guidanceScale = 7.5f
                    guidanceScaleSeekBar.progress = 65
                    tvGuidanceScaleValue.text = guidanceScale.toString()
                }

                ModelManager.FLUX_SCHNELL_ID -> {
                    inferenceStepsLayout.visibility = View.VISIBLE
                    numImagesLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    safetyCheckerLayout.visibility = View.VISIBLE

                    setupNumImagesSeekBar(4) // Max 4 images
                    inferenceStepsSeekBar.max = 11
                    findViewById<TextView>(R.id.tvInferenceStepsLabel)?.text = "Inference Steps (1 - 12)"
                }

                ModelManager.FLUX_REALISM_ID -> {
                    guidanceScaleLayout.visibility = View.VISIBLE
                    inferenceStepsLayout.visibility = View.VISIBLE
                    numImagesLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    safetyCheckerLayout.visibility = View.VISIBLE

                    setupNumImagesSeekBar(4) // Max 4 images
                    guidanceScaleSeekBar.max = 190
                    inferenceStepsSeekBar.max = 49
                    findViewById<TextView>(R.id.tvGuidanceScaleLabel)?.text = "Guidance Scale (1.0 - 20.0)"
                    findViewById<TextView>(R.id.tvInferenceStepsLabel)?.text = "Inference Steps (1 - 50)"
                }

                ModelManager.RECRAFT_V3_ID -> {
                    colorsLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    // Recraft v3 only supports 1 image, so no numImagesLayout
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating advanced options")
            advancedOptionsLayout.visibility = View.GONE
        }
    }

    private fun setupNumImagesSeekBar(maxImages: Int) {
        numImagesSeekBar.max = maxImages - 1 // 0-based index
        val currentMax = numImages.coerceAtMost(maxImages)
        numImagesSeekBar.progress = currentMax - 1
        numImages = currentMax
        tvNumImagesValue.text = numImages.toString()
        findViewById<TextView>(R.id.tvNumImagesLabel)?.text = "Number of Images (1 - $maxImages)"
    }

    private fun setupModelSpinner() {
        // Text-to-image models
        val imageModels = listOf(
            AIModel(
                id = ModelManager.DALLE_3_ID,
                displayName = "DALL-E 3",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "openai",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.SEEDREAM_3_ID,
                displayName = "SeedDream 3.0",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "bytedance",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.STABLE_DIFFUSION_V35_LARGE_ID,
                displayName = "Stable Diffusion v3.5",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "stability",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.FLUX_SCHNELL_ID,
                displayName = "Flux Schnell",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "flux",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.FLUX_REALISM_ID,
                displayName = "Flux Realism",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "flux",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.RECRAFT_V3_ID,
                displayName = "Recraft v3",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "recraft",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.QWEN_IMAGE_ID,
                displayName = "Qwen Image",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "aimlapi",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.IMAGEN_4_ULTRA_ID,
                displayName = "Imagen 4.0 Ultra",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "aimlapi",
                isFree = false,
                isImageGenerator = true,
                maxInputTokens = 0
            )
        )

        // Image-to-image models
        val imageToImageModels = listOf(
            AIModel(
                id = ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID,
                displayName = "Flux Dev I2I",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "flux",
                isFree = false,
                isImageGenerator = true,
                isImageToImage = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID,
                displayName = "Flux Kontext Max I2I",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "flux",
                isFree = false,
                isImageGenerator = true,
                isImageToImage = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID,
                displayName = "Flux Kontext Pro I2I",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "flux",
                isFree = false,
                isImageGenerator = true,
                isImageToImage = true,
                maxInputTokens = 0
            ),
            AIModel(
                id = ModelManager.SEEDEDIT_3_I2I_ID,
                displayName = "SeedEdit 3.0 I2I",
                maxTokens = 0,
                temperature = 0.0,
                apiName = "aimlapi",
                isFree = false,
                isImageGenerator = true,
                isImageToImage = true,
                maxInputTokens = 0
            )
        )

        // Create complete model list
        val allModels = imageModels + imageToImageModels

        val optionsList = allModels.map { model ->
            val baseCost = ModelValidator.getBaseCreditCost(model.id)
            ImageGenerationOption(
                id = model.id,
                displayName = "${model.displayName} ($baseCost credits)",
                iconResourceId = getIconResourceForModel(model.id),
                isPremium = !model.isFree
            )
        }

        val adapter = ImageOptionSpinnerAdapter(
            this,
            optionsList,
            showProBadges = true
        )
        binding.spinnerImageModels.adapter = adapter

        binding.spinnerImageModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                try {
                    if (position >= 0 && position < optionsList.size) {
                        val previousModel = selectedModel
                        selectedModel = allModels[position].id
                        (binding.spinnerImageModels.adapter as? ImageOptionSpinnerAdapter)?.setSelectedPosition(position)

                        Timber.d("Model changed from '$previousModel' to '$selectedModel'")

                        // Initialize quality FIRST before any other operations
                        initializeQualityForModel(selectedModel)

                        // Update UI components in the correct order
                        updateOptionSpinners()
                        updateUIForSelectedModel()
                        updateCreditsInfo()

                        // ALWAYS update button state last
                        updateGenerateButtonState()

                    } else {
                        Timber.e("Model spinner position out of bounds: $position, size: ${optionsList.size}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in model selection: ${e.message}")
                    initializeQualityForModel(selectedModel)
                    updateGenerateButtonState()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (selectedQuality.isEmpty()) {
                    initializeQualityForModel(selectedModel)
                }
            }
        }

        // Set initial selection
        binding.spinnerImageModels.setSelection(0)
        (binding.spinnerImageModels.adapter as? ImageOptionSpinnerAdapter)?.setSelectedPosition(0)
        selectedModel = ModelManager.DALLE_3_ID

        // Initialize quality for the default model
        initializeQualityForModel(selectedModel)
    }

    private fun initializeQualityForModel(modelId: String) {
        val previousQuality = selectedQuality
        selectedQuality = when (modelId) {
            ModelManager.DALLE_3_ID -> "standard"
            else -> "standard"
        }

        if (previousQuality != selectedQuality) {
            Timber.d("Quality changed from '$previousQuality' to '$selectedQuality' for model: $modelId")
        }
    }

    private fun getIconResourceForModel(modelId: String): Int {
        return when (modelId) {
            ModelManager.DALLE_3_ID -> R.drawable.ic_dalle
            "stable-diffusion-v35-large" -> R.drawable.ic_stable_diffusion
            ModelManager.FLUX_SCHNELL_ID -> R.drawable.ic_flux
            ModelManager.FLUX_REALISM_ID -> R.drawable.ic_flux
            ModelManager.RECRAFT_V3_ID -> R.drawable.ic_recraft
            ModelManager.SEEDREAM_3_ID -> R.drawable.ic_seedream
            ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> R.drawable.ic_image_to_image
            ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID -> R.drawable.ic_image_to_image
            ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID -> R.drawable.ic_image_to_image
            ModelManager.QWEN_IMAGE_ID -> R.drawable.ic_qwen
            ModelManager.SEEDEDIT_3_I2I_ID -> R.drawable.ic_image_to_image
            ModelManager.IMAGEN_4_ULTRA_ID -> R.drawable.ic_image_model_default
            else -> R.drawable.ic_image_model_default
        }
    }

    private fun updateOptionSpinners() {
        // Hide all option spinners for image-to-image models
        if (ModelValidator.isImageToImageModel(selectedModel)) {
            binding.spinnerSize.visibility = View.GONE
            binding.tvSizeLabel.visibility = View.GONE
            binding.spinnerStyle.visibility = View.GONE
            binding.tvStyleLabel.visibility = View.GONE
            binding.spinnerQuality.visibility = View.GONE
            binding.tvQualityLabel.visibility = View.GONE
            outputFormatLayout.visibility = View.GONE

            selectedQuality = "standard"
            return
        }

        // Update size/aspect ratio spinner based on model
        when (selectedModel) {
            ModelManager.DALLE_3_ID -> {
                val sizes = listOf("1024x1024", "1024x1792", "1792x1024")
                val sizeOptions = sizes.map {
                    ImageGenerationOption(id = it, displayName = it)
                }
                binding.spinnerSize.adapter = ImageOptionSpinnerAdapter(this, sizeOptions)
                binding.spinnerSize.setSelection(0)
                selectedSize = sizes[0]
                binding.tvSizeLabel.text = "Size"
                binding.spinnerSize.visibility = View.VISIBLE
                binding.tvSizeLabel.visibility = View.VISIBLE
            }

            ModelManager.SEEDREAM_3_ID, ModelManager.QWEN_IMAGE_ID -> {
                val sizes = listOf("1024x1024", "1024x1792", "1792x1024", "512x512", "768x768")
                val sizeOptions = sizes.map {
                    ImageGenerationOption(id = it, displayName = it)
                }
                binding.spinnerSize.adapter = ImageOptionSpinnerAdapter(this, sizeOptions)
                binding.spinnerSize.setSelection(0)
                selectedSize = sizes[0]
                binding.tvSizeLabel.text = "Size"
                binding.spinnerSize.visibility = View.VISIBLE
                binding.tvSizeLabel.visibility = View.VISIBLE
            }

            "stable-diffusion-v35-large", ModelManager.FLUX_SCHNELL_ID,
            ModelManager.FLUX_REALISM_ID, ModelManager.RECRAFT_V3_ID -> {
                val aspectRatios = listOf("square_hd", "square", "portrait_4_3", "portrait_16_9", "landscape_4_3", "landscape_16_9")
                val aspectOptions = aspectRatios.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = it.replace("_", " ").replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                    )
                }
                binding.spinnerSize.adapter = ImageOptionSpinnerAdapter(this, aspectOptions)
                binding.spinnerSize.setSelection(4) // Default to landscape_4_3
                selectedAspectRatio = aspectRatios[4]
                binding.tvSizeLabel.text = "Aspect Ratio"
                binding.spinnerSize.visibility = View.VISIBLE
                binding.tvSizeLabel.visibility = View.VISIBLE
            }

            ModelManager.IMAGEN_4_ULTRA_ID -> {
                val aspectRatios = listOf("1:1", "9:16", "16:9", "3:4", "4:3")
                val aspectOptions = aspectRatios.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = ImageGenerationUtils.getAspectRatioDisplayName(it)
                    )
                }
                binding.spinnerSize.adapter = ImageOptionSpinnerAdapter(this, aspectOptions)
                binding.spinnerSize.setSelection(0) // Default to 1:1
                selectedAspectRatio = aspectRatios[0]
                binding.tvSizeLabel.text = "Aspect Ratio"
                binding.spinnerSize.visibility = View.VISIBLE
                binding.tvSizeLabel.visibility = View.VISIBLE
            }
        }

        // Update style spinner with complete Recraft v3 styles
        when (selectedModel) {
            ModelManager.DALLE_3_ID -> {
                val styles = listOf("vivid", "natural")
                val styleOptions = styles.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = it.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                    )
                }
                binding.spinnerStyle.adapter = ImageOptionSpinnerAdapter(this, styleOptions)
                binding.spinnerStyle.setSelection(0)
                selectedStyle = styles[0]
                binding.spinnerStyle.visibility = View.VISIBLE
                binding.tvStyleLabel.visibility = View.VISIBLE
            }

            ModelManager.RECRAFT_V3_ID -> {
                val styles = listOf(
                    "realistic_image",
                    "digital_illustration",
                    "vector_illustration",
                    "realistic_image/b_and_w",
                    "realistic_image/hard_flash",
                    "realistic_image/hdr",
                    "realistic_image/natural_light",
                    "realistic_image/studio_portrait",
                    "realistic_image/enterprise",
                    "realistic_image/motion_blur",
                    "digital_illustration/pixel_art",
                    "digital_illustration/hand_drawn",
                    "digital_illustration/grain",
                    "digital_illustration/infantile_sketch",
                    "digital_illustration/2d_art_poster",
                    "digital_illustration/handmade_3d",
                    "digital_illustration/hand_drawn_outline",
                    "digital_illustration/engraving_color",
                    "digital_illustration/2d_art_poster_2",
                    "vector_illustration/engraving",
                    "vector_illustration/line_art",
                    "vector_illustration/line_circuit",
                    "vector_illustration/linocut",
                    "any"
                )
                val styleOptions = styles.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = it.replace("_", " ").replace("/", " - ").replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                    )
                }
                binding.spinnerStyle.adapter = ImageOptionSpinnerAdapter(this, styleOptions)
                binding.spinnerStyle.setSelection(0)
                selectedStyle = styles[0]
                binding.spinnerStyle.visibility = View.VISIBLE
                binding.tvStyleLabel.visibility = View.VISIBLE
            }

            else -> {
                binding.spinnerStyle.visibility = View.GONE
                binding.tvStyleLabel.visibility = View.GONE
                selectedStyle = "default"
            }
        }

        // Update quality spinner
        if (selectedModel == ModelManager.DALLE_3_ID) {
            val qualities = listOf("standard", "hd")
            val qualityOptions = qualities.map {
                ImageGenerationOption(
                    id = it,
                    displayName = it.replaceFirstChar { char -> char.titlecase(Locale.ROOT) },
                    isPremium = it == "hd"
                )
            }
            binding.spinnerQuality.adapter = ImageOptionSpinnerAdapter(
                this,
                qualityOptions,
                showProBadges = true
            )
            binding.spinnerQuality.setSelection(0)
            selectedQuality = qualities[0]
            binding.spinnerQuality.visibility = View.VISIBLE
            binding.tvQualityLabel.visibility = View.VISIBLE
        } else {
            binding.spinnerQuality.visibility = View.GONE
            binding.tvQualityLabel.visibility = View.GONE
            selectedQuality = "standard"
        }

        // Update output format spinner
        when (selectedModel) {
            ModelManager.DALLE_3_ID -> {
                val formats = listOf("url", "b64_json")
                val formatOptions = formats.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = when(it) {
                            "url" -> "URL"
                            "b64_json" -> "Base64 JSON"
                            else -> it
                        }
                    )
                }
                spinnerOutputFormat.adapter = ImageOptionSpinnerAdapter(this, formatOptions)
                spinnerOutputFormat.setSelection(0)
                selectedOutputFormat = formats[0]
                outputFormatLayout.visibility = View.VISIBLE
            }

            ModelManager.SEEDREAM_3_ID -> {
                val formats = listOf("url", "b64_json")
                val formatOptions = formats.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = when(it) {
                            "url" -> "URL"
                            "b64_json" -> "Base64 JSON"
                            else -> it
                        }
                    )
                }
                spinnerOutputFormat.adapter = ImageOptionSpinnerAdapter(this, formatOptions)
                spinnerOutputFormat.setSelection(0)
                selectedOutputFormat = formats[0]
                outputFormatLayout.visibility = View.VISIBLE
            }

            "stable-diffusion-v35-large", ModelManager.FLUX_REALISM_ID -> {
                val formats = listOf("jpeg", "png")
                val formatOptions = formats.map {
                    ImageGenerationOption(
                        id = it,
                        displayName = it.uppercase()
                    )
                }
                spinnerOutputFormat.adapter = ImageOptionSpinnerAdapter(this, formatOptions)
                spinnerOutputFormat.setSelection(0)
                selectedOutputFormat = formats[0]
                outputFormatLayout.visibility = View.VISIBLE
            }

            else -> {
                outputFormatLayout.visibility = View.GONE
                selectedOutputFormat = "url"
            }
        }

        updateCreditsInfo()
    }

    private fun setupOptionSpinners() {
        try {
            // Size spinner
            binding.spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val adapter = binding.spinnerSize.adapter as? ImageOptionSpinnerAdapter
                    if (adapter != null && position >= 0 && position < adapter.count) {
                        val option = adapter.getItem(position) as ImageGenerationOption
                        when (selectedModel) {
                            ModelManager.DALLE_3_ID, ModelManager.SEEDREAM_3_ID, ModelManager.QWEN_IMAGE_ID -> selectedSize = option.id
                            else -> selectedAspectRatio = option.id
                        }
                        adapter.setSelectedPosition(position)
                        updateGenerateButtonState()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Style spinner
            binding.spinnerStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val adapter = binding.spinnerStyle.adapter as? ImageOptionSpinnerAdapter
                    if (adapter != null && position >= 0 && position < adapter.count) {
                        val option = adapter.getItem(position) as ImageGenerationOption
                        selectedStyle = option.id
                        adapter.setSelectedPosition(position)
                        updateGenerateButtonState()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Quality spinner
            binding.spinnerQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val adapter = binding.spinnerQuality.adapter as? ImageOptionSpinnerAdapter
                    if (adapter != null && position >= 0 && position < adapter.count) {
                        val option = adapter.getItem(position) as ImageGenerationOption

                        if (option.isPremium && !PrefsManager.isSubscribed(this@ImageGenerationActivity)) {
                            binding.spinnerQuality.setSelection(0)
                            Toast.makeText(
                                this@ImageGenerationActivity,
                                "HD quality requires a Pro subscription",
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }

                        selectedQuality = option.id
                        adapter.setSelectedPosition(position)
                        updateCreditsInfo()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Output format spinner
            spinnerOutputFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val adapter = spinnerOutputFormat.adapter as? ImageOptionSpinnerAdapter
                    if (adapter != null && position >= 0 && position < adapter.count) {
                        val option = adapter.getItem(position) as ImageGenerationOption
                        selectedOutputFormat = option.id
                        adapter.setSelectedPosition(position)
                        updateGenerateButtonState()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up option spinners: ${e.message}")
            updateGenerateButtonState()
        }
    }

    private fun updateUIForSelectedModel() {
        try {
            // Determine if this is an image-to-image model
            val previousMode = isImageToImageMode
            isImageToImageMode = when (selectedModel) {
                ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID,
                ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID,
                ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID -> true
                else -> false
            }

            if (previousMode != isImageToImageMode) {
                Timber.d("Mode changed from ${if (previousMode) "I2I" else "T2I"} to ${if (isImageToImageMode) "I2I" else "T2I"} for model: $selectedModel")
            }

            val optionsCard = findViewById<CardView>(R.id.optionsCard)
            val advancedOptionsCard = findViewById<CardView>(R.id.advancedOptionsCard)

            if (isImageToImageMode) {
                optionsCard?.visibility = View.GONE
                imageUploadCard.visibility = View.VISIBLE

                if (!previousMode) {
                    selectedSourceImageUri = null
                    ivSourceImage.setImageURI(null)
                    ivSourceImage.visibility = View.GONE
                    sourceImagePlaceholder.visibility = View.VISIBLE

                    additionalImageUris.clear()
                    additionalImagesChipGroup.removeAllViews()
                }

                updateImageToImageAdvancedOptions()
                advancedOptionsCard?.visibility = View.VISIBLE

            } else {
                optionsCard?.visibility = View.VISIBLE

                val sizeLayout = findViewById<LinearLayout>(R.id.sizeLayout)
                val styleLayout = findViewById<LinearLayout>(R.id.styleLayout)
                val qualityLayout = findViewById<LinearLayout>(R.id.qualityLayout)

                sizeLayout?.visibility = View.VISIBLE

                styleLayout?.visibility = if (selectedModel == ModelManager.DALLE_3_ID ||
                    selectedModel == ModelManager.RECRAFT_V3_ID) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                qualityLayout?.visibility = if (selectedModel == ModelManager.DALLE_3_ID) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                imageUploadCard.visibility = View.GONE

                if (selectedModel == ModelManager.DALLE_3_ID) {
                    advancedOptionsCard?.visibility = View.GONE
                } else {
                    advancedOptionsCard?.visibility = View.VISIBLE
                    updateAdvancedOptions()
                }
            }

            updateGenerateButtonState()

        } catch (e: Exception) {
            Timber.e(e, "Error updating UI for selected model: ${e.message}")

            try {
                findViewById<CardView>(R.id.optionsCard)?.visibility =
                    if (isImageToImageMode) View.GONE else View.VISIBLE

                imageUploadCard.visibility = if (isImageToImageMode) View.VISIBLE else View.GONE

                findViewById<CardView>(R.id.advancedOptionsCard)?.visibility =
                    if (selectedModel == ModelManager.DALLE_3_ID) View.GONE else View.VISIBLE

                updateGenerateButtonState()
            } catch (e2: Exception) {
                Timber.e(e2, "Error in fallback UI update")
            }
        }
    }

    private fun updateImageToImageAdvancedOptions() {
        try {
            advancedOptionsLayout.visibility = View.VISIBLE
            findViewById<CardView>(R.id.advancedOptionsCard)?.visibility = View.VISIBLE

            // Hide all options first
            negativePromptLayout.visibility = View.GONE
            guidanceScaleLayout.visibility = View.GONE
            inferenceStepsLayout.visibility = View.GONE
            numImagesLayout.visibility = View.GONE
            seedLayout.visibility = View.GONE
            safetyCheckerLayout.visibility = View.GONE
            watermarkLayout.visibility = View.GONE
            colorsLayout.visibility = View.GONE
            strengthLayout.visibility = View.GONE
            aspectRatioLayout.visibility = View.GONE
            safetyToleranceLayout.visibility = View.GONE
            multiImageLayout.visibility = View.GONE

            when (selectedModel) {
                ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> {
                    guidanceScaleLayout.visibility = View.VISIBLE
                    inferenceStepsLayout.visibility = View.VISIBLE
                    numImagesLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    safetyCheckerLayout.visibility = View.VISIBLE
                    strengthLayout.visibility = View.VISIBLE

                    setupNumImagesSeekBar(4) // Max 4 images
                    guidanceScale = 7.5f
                    guidanceScaleSeekBar.progress = 65
                    numInferenceSteps = 25
                    inferenceStepsSeekBar.progress = 24
                    strength = 0.95f
                    seekBarStrength.progress = 85

                    inferenceStepsSeekBar.max = 49
                    findViewById<TextView>(R.id.tvInferenceStepsLabel)?.text = "Inference Steps (1 - 50)"

                    btnAdvancedOptions.setImageResource(R.drawable.ic_expand_less)
                }

                ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID, ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID -> {
                    guidanceScaleLayout.visibility = View.VISIBLE
                    numImagesLayout.visibility = View.VISIBLE
                    seedLayout.visibility = View.VISIBLE
                    aspectRatioLayout.visibility = View.VISIBLE
                    safetyToleranceLayout.visibility = View.VISIBLE
                    multiImageLayout.visibility = View.VISIBLE

                    setupNumImagesSeekBar(4) // Max 4 images
                    setupAspectRatioSpinner()

                    guidanceScale = 7.5f
                    guidanceScaleSeekBar.progress = 65
                    safetyTolerance = "2"
                    seekBarSafetyTolerance.progress = 1

                    updateImageCount()

                    btnAdvancedOptions.setImageResource(R.drawable.ic_expand_less)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating image-to-image advanced options")
            advancedOptionsLayout.visibility = View.GONE
        }
    }

    private fun setupAspectRatioSpinner() {
        try {
            val aspectRatios = ImageGenerationUtils.getSupportedAspectRatios(selectedModel)
            if (aspectRatios.isEmpty()) return

            val options = aspectRatios.map {
                ImageGenerationOption(
                    id = it,
                    displayName = it.replace(":", "×")
                )
            }

            val adapter = ImageOptionSpinnerAdapter(this, options)
            spinnerAspectRatio.adapter = adapter

            spinnerAspectRatio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position >= 0 && position < aspectRatios.size) {
                        selectedAspectRatioFormat = aspectRatios[position]
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            val defaultIndex = aspectRatios.indexOf("16:9").coerceAtLeast(0)
            spinnerAspectRatio.setSelection(defaultIndex)
        } catch (e: Exception) {
            Timber.e(e, "Error setting up aspect ratio spinner: ${e.message}")
        }
    }

    private fun showColorPicker() {
        val colors = arrayOf(
            "Red", "Blue", "Green", "Yellow", "Purple", "Orange", "Pink", "Cyan", "Brown", "Gray",
            "Black", "White", "Navy", "Maroon", "Teal", "Silver", "Gold", "Magenta", "Lime", "Indigo"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Choose a Color")
            .setItems(colors) { _, which ->
                val color = when (which) {
                    0 -> Triple(255, 0, 0)      // Red
                    1 -> Triple(0, 0, 255)      // Blue
                    2 -> Triple(0, 255, 0)      // Green
                    3 -> Triple(255, 255, 0)    // Yellow
                    4 -> Triple(128, 0, 128)    // Purple
                    5 -> Triple(255, 165, 0)    // Orange
                    6 -> Triple(255, 192, 203)  // Pink
                    7 -> Triple(0, 255, 255)    // Cyan
                    8 -> Triple(165, 42, 42)    // Brown
                    9 -> Triple(128, 128, 128)  // Gray
                    10 -> Triple(0, 0, 0)       // Black
                    11 -> Triple(255, 255, 255) // White
                    12 -> Triple(0, 0, 128)     // Navy
                    13 -> Triple(128, 0, 0)     // Maroon
                    14 -> Triple(0, 128, 128)   // Teal
                    15 -> Triple(192, 192, 192) // Silver
                    16 -> Triple(255, 215, 0)   // Gold
                    17 -> Triple(255, 0, 255)   // Magenta
                    18 -> Triple(0, 255, 0)     // Lime
                    19 -> Triple(75, 0, 130)    // Indigo
                    else -> Triple(0, 0, 0)
                }

                addColorChip(colors[which], color)
                selectedColors.add(color)
            }
            .show()
    }

    private fun addColorChip(colorName: String, colorValues: Triple<Int, Int, Int>) {
        try {
            val chip = Chip(this)
            chip.text = colorName
            chip.isCloseIconVisible = true
            chip.setChipBackgroundColorResource(R.color.white)

            try {
                val color = android.graphics.Color.rgb(colorValues.first, colorValues.second, colorValues.third)
                chip.chipIconTint = android.content.res.ColorStateList.valueOf(color)
                chip.chipIcon = ContextCompat.getDrawable(this, R.drawable.ic_color_circle)
                chip.chipIconSize = resources.getDimension(R.dimen.chip_icon_size)
            } catch (e: Exception) {
                Timber.e(e, "Error setting chip color")
            }

            chip.setOnCloseIconClickListener {
                colorChipGroup.removeView(chip)
                selectedColors.remove(colorValues)
            }

            colorChipGroup.addView(chip)
        } catch (e: Exception) {
            Timber.e(e, "Error adding color chip")
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                if (!ImageUploadUtils.validateImageUri(this@ImageGenerationActivity, uri)) {
                    Toast.makeText(
                        this@ImageGenerationActivity,
                        "Invalid or corrupted image file",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                selectedSourceImageUri = uri

                ivSourceImage.setImageURI(uri)
                ivSourceImage.visibility = View.VISIBLE
                sourceImagePlaceholder.visibility = View.GONE

                updateGenerateButtonState()

                Toast.makeText(
                    this@ImageGenerationActivity,
                    "Image selected successfully",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Timber.e(e, "Error handling selected image: ${e.message}")
                Toast.makeText(
                    this@ImageGenerationActivity,
                    "Error selecting image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleAdditionalImage(uri: Uri) {
        try {
            if (additionalImageUris.size >= ModelValidator.getMaxSourceImages(selectedModel) - 1) {
                Toast.makeText(this, "Maximum number of images reached", Toast.LENGTH_SHORT).show()
                return
            }

            additionalImageUris.add(uri)
            addImageChip(uri)
            updateImageCount()
            updateGenerateButtonState()

        } catch (e: Exception) {
            Timber.e(e, "Error handling additional image: ${e.message}")
            Toast.makeText(this, "Error adding image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addImageChip(uri: Uri) {
        val chip = Chip(this)
        chip.text = "Image ${additionalImageUris.size}"
        chip.isCloseIconVisible = true
        chip.setChipIconResource(R.drawable.ic_image)

        chip.setOnCloseIconClickListener {
            val index = additionalImagesChipGroup.indexOfChild(chip)
            if (index >= 0 && index < additionalImageUris.size) {
                additionalImageUris.removeAt(index)
                additionalImagesChipGroup.removeView(chip)
                updateImageCount()
            }
        }

        chip.setOnClickListener {
            val previewDialog = MaterialAlertDialogBuilder(this)
                .setTitle("Image Preview")
                .setPositiveButton("Close", null)
                .create()

            val imageView = ImageView(this)
            imageView.setImageURI(uri)
            imageView.adjustViewBounds = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER

            previewDialog.setView(imageView)
            previewDialog.show()
        }

        additionalImagesChipGroup.addView(chip)
    }

    private fun updateImageCount() {
        val max = ModelValidator.getMaxSourceImages(selectedModel)
        val current = additionalImageUris.size + 1 // +1 for main image
        tvMultiImageCount.text = "$current/$max"

        btnAddMoreImages.isEnabled = current < max
    }

    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            photoFile.also {
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    it
                )
                selectedSourceImageUri = photoURI
                takePhotoLauncher.launch(photoURI)
            }
        } catch (e: IOException) {
            Timber.e(e, "Error creating image file")
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }

    private fun setupButtons() {
        try {
            binding.btnBack.setOnClickListener {
                provideHapticFeedback(50)
                finish()
            }

            binding.btnGalleryTop.setOnClickListener {
                provideHapticFeedback(50)
                openImageGallery()
            }

            binding.btnGenerate.setOnClickListener {
                try {
                    val prompt = binding.etImagePrompt.text.toString().trim()
                    if (prompt.isEmpty()) {
                        Toast.makeText(this, "Please enter an image description", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (isImageToImageMode && selectedSourceImageUri == null) {
                        Toast.makeText(this, "Please select a source image", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // Check credits and show options if insufficient
                    if (!checkCreditsWithOptions()) {
                        return@setOnClickListener
                    }

                    // Get seed value
                    seed = etSeed.text.toString().toIntOrNull()

                    provideHapticFeedback(50)
                    generateImage(prompt)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling generate button click")
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnGallery.setOnClickListener {
                provideHapticFeedback(50)
                openImageGallery()
            }

            binding.btnDownload.setOnClickListener {
                provideHapticFeedback(50)
                checkStoragePermissionAndDownload()
            }

            binding.btnShare.setOnClickListener {
                provideHapticFeedback(50)
                shareGeneratedImage()
            }

            binding.btnRegenerateImage.setOnClickListener {
                provideHapticFeedback(50)
                showEmptyState()
            }

            binding.btnCancel.setOnClickListener {
                provideHapticFeedback(50)
                cancelGeneration()
            }
            
            // Watch ad button for credits
            binding.btnWatchAdForCredits.setOnClickListener {
                provideHapticFeedback(50)
                showRewardedAd()
            }

            btnAdvancedOptions.setOnClickListener {
                provideHapticFeedback(50)
                if (advancedOptionsLayout.visibility == View.VISIBLE) {
                    advancedOptionsLayout.visibility = View.GONE
                    btnAdvancedOptions.setImageResource(R.drawable.ic_expand_more)
                } else {
                    if (isImageToImageMode) {
                        updateImageToImageAdvancedOptions()
                    } else {
                        updateAdvancedOptions()
                    }
                    btnAdvancedOptions.setImageResource(R.drawable.ic_expand_less)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up buttons")
        }
    }

    private fun updateGenerateButtonState() {
        try {
            val prompt = binding.etImagePrompt.text.toString().trim()
            val isSubscribed = PrefsManager.isSubscribed(this)

            if (selectedQuality.isEmpty()) {
                Timber.w("Quality not set, initializing for model: $selectedModel")
                initializeQualityForModel(selectedModel)
            }

            val creditCost = try {
                ModelValidator.getImageGenerationCost(selectedModel, selectedQuality,
                    numImages // Remove .toString()
                )
            } catch (e: Exception) {
                Timber.e(e, "Error getting credit cost, using fallback")
                5 * numImages
            }

            val currentCredits = creditManager.getImageCredits()
            val hasEnoughCredits = isSubscribed || currentCredits >= creditCost

            val shouldBeEnabled = when {
                prompt.isEmpty() -> {
                    Timber.d("Button disabled: empty prompt")
                    false
                }
                isImageToImageMode && selectedSourceImageUri == null -> {
                    Timber.d("Button disabled: I2I mode but no source image")
                    false
                }
                isGenerating -> {
                    Timber.d("Button disabled: generation in progress")
                    false
                }
                !isSubscribed && currentCredits <= 0 -> {
                    Timber.d("Button disabled: no credits available ($currentCredits)")
                    false
                }
                !hasEnoughCredits -> {
                    Timber.d("Button disabled: insufficient credits ($currentCredits < $creditCost)")
                    false
                }
                else -> {
                    Timber.d("Button enabled: all conditions met")
                    true
                }
            }

            binding.btnGenerate.isEnabled = shouldBeEnabled

            // Update button text based on state
            binding.btnGenerate.text = when {
                isGenerating -> "Generating..."
                !isSubscribed && currentCredits <= 0 -> "No Credits - Watch Ad"
                !hasEnoughCredits -> {
                    val creditsNeeded = ModelValidator.getCreditsNeeded(currentCredits, selectedModel, selectedQuality, numImages)
                    "Need $creditsNeeded more credits"
                }
                prompt.isEmpty() -> "Enter a prompt to generate"
                isImageToImageMode && selectedSourceImageUri == null -> "Select source image"
                else -> "Generate Image"
            }

            // Debug logging...
            Timber.d("=== GENERATE BUTTON STATE DEBUG ===")
            Timber.d("Model: $selectedModel")
            Timber.d("Quality: '$selectedQuality'")
            Timber.d("Number of Images: $numImages")
            Timber.d("Is I2I Mode: $isImageToImageMode")
            Timber.d("Prompt Length: ${prompt.length}")
            Timber.d("Has Enough Credits: $hasEnoughCredits ($currentCredits >= $creditCost)")
            Timber.d("Source Image Selected: ${selectedSourceImageUri != null}")
            Timber.d("Button Enabled: $shouldBeEnabled")
            Timber.d("=====================================")

        } catch (e: Exception) {
            Timber.e(e, "Error updating generate button state: ${e.message}")
            val prompt = binding.etImagePrompt.text.toString().trim()
            val fallbackEnabled = prompt.isNotEmpty() &&
                    (!isImageToImageMode || selectedSourceImageUri != null)
            binding.btnGenerate.isEnabled = fallbackEnabled
            binding.btnGenerate.text = "Generate Image"
        }
    }
    // Fix 1: In updateCreditsInfo() method around line 800
    private fun updateCreditsInfo() {
        try {
            val isSubscribed = PrefsManager.isSubscribed(this)

            if (selectedQuality.isEmpty()) {
                initializeQualityForModel(selectedModel)
            }

            try {
                ModelValidator.getImageGenerationCost(selectedModel, selectedQuality,
                    numImages // Remove .toString()
                )
            } catch (e: Exception) {
                Timber.e(e, "Error getting credit cost, using fallback")
                5 * numImages
            }

            if (isSubscribed) {
                binding.tvCreditsInfo.text = "Premium: Unlimited image generation"
                binding.btnWatchAdForCredits.visibility = View.GONE
            } else {
                val breakdown = ModelValidator.getCostBreakdown(selectedModel, selectedQuality,
                    numImages // Remove .toString()
                )
                val currentCredits = creditManager.getImageCredits()
                if (currentCredits <= 0) {
                    binding.tvCreditsInfo.text = "⚡ No credits left! Watch ads to earn more"
                    shakeCreditsIndicator()
                } else {
                    binding.tvCreditsInfo.text = "Uses ${breakdown.getBreakdownText()} ($currentCredits left)"
                }
                
                // Show/hide ad button based on credit availability
                val canWatchAds = creditManager.canEarnMoreCredits(CreditManager.CreditType.IMAGE_GENERATION)
                adManager.isRewardedReady()
                
                // Always show the watch ad button if user can earn more credits
                if (canWatchAds) {
                    binding.btnWatchAdForCredits.visibility = View.VISIBLE
                    binding.btnWatchAdForCredits.isEnabled = true
                    
                    // Make button more prominent when user has no credits
                    if (currentCredits <= 0) {
                        binding.btnWatchAdForCredits.text = "🎥 Watch Ad - Get 1 Credit!"
                        binding.btnWatchAdForCredits.setBackgroundResource(R.drawable.premium_gradient_background)
                        // Add a subtle animation for attention
                        val animator = ValueAnimator.ofFloat(0.8f, 1.0f)
                        animator.duration = 1000
                        animator.repeatMode = ValueAnimator.REVERSE
                        animator.repeatCount = ValueAnimator.INFINITE
                        animator.addUpdateListener { animation ->
                            binding.btnWatchAdForCredits.alpha = animation.animatedValue as Float
                        }
                        animator.start()
                    } else {
                        binding.btnWatchAdForCredits.text = "Watch Ad +2 Credits"
                        binding.btnWatchAdForCredits.setBackgroundResource(R.drawable.rounded_button_background)
                        binding.btnWatchAdForCredits.alpha = 1.0f
                        binding.btnWatchAdForCredits.clearAnimation()
                    }
                } else {
                    binding.btnWatchAdForCredits.visibility = View.GONE
                }
            }

            updateGenerateButtonState()
            
            // Update options menu to reflect credit changes
            invalidateOptionsMenu()

        } catch (e: Exception) {
            Timber.e(e, "Error updating credits info: ${e.message}")
            val currentCredits = creditManager.getImageCredits()
            binding.tvCreditsInfo.text = "Image generation ($currentCredits credits left)"
            updateGenerateButtonState()
            
            // Update options menu even on error
            invalidateOptionsMenu()
        }
    }
    private fun shakeCreditsIndicator() {
        val shakeAnimation = TranslateAnimation(0f, 10f, 0f, 0f).apply {
            duration = 100
            repeatCount = 5
            repeatMode = Animation.REVERSE
            interpolator = CycleInterpolator(1f)
        }
        binding.tvCreditsInfo.startAnimation(shakeAnimation)
        provideHapticFeedback(100)
    }

    private fun checkCreditsWithOptions(): Boolean {
        if (PrefsManager.isSubscribed(this)) {
            return true
        }

        val creditCost = ModelValidator.getImageGenerationCost(selectedModel, selectedQuality,
            numImages // Remove .toString()
        )

        if (creditManager.getImageCredits() >= creditCost) {
            return true
        }

        // Not enough credits - show options
        showInsufficientCreditsDialog(creditCost)
        return false
    }
    private fun showInsufficientCreditsDialog(creditCost: Int) {
        val currentCredits = creditManager.getImageCredits()
        creditCost - currentCredits
        val canWatchAds = creditManager.canEarnMoreCredits(CreditManager.CreditType.IMAGE_GENERATION)

        val message = buildString {
            append("You need $creditCost credits but only have $currentCredits.\n\n")
            append("Options:\n")
            append("• Upgrade to Pro for unlimited generations\n")
            if (canWatchAds) {
                val creditsToMax = creditManager.getCreditsToMax(CreditManager.CreditType.IMAGE_GENERATION)
                append("• Watch ads to earn up to $creditsToMax more credits (1 credit per ad)")
            } else {
                append("• You've reached today's maximum credits (${creditManager.getMaxCredits(CreditManager.CreditType.IMAGE_GENERATION)} total)")
            }
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Insufficient Credits")
            .setMessage(message)
            .setPositiveButton("Upgrade to Pro") { _, _ ->
                SubscriptionActivity.start(this)
            }
            .setNegativeButton("Cancel", null)

        if (canWatchAds) {
            builder.setNeutralButton("Watch Ad") { _, _ ->
                showRewardedAd()
            }
        }

        builder.show()
    }

    private fun deductCredits(amount: Int) {
        try {
            if (creditManager.consumeImageCredits(amount)) {
                Timber.d("Successfully consumed $amount image credits")
                updateCreditsInfo()
            } else {
                Timber.w("Failed to consume $amount image credits - insufficient credits")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deducting credits: ${e.message}")
        }
    }

    private fun refundCredits(amount: Int) {
        try {
            if (creditManager.refundCredits(amount, CreditManager.CreditType.IMAGE_GENERATION)) {
                updateCreditsInfo()
                Timber.d("Refunded $amount image credits")
            } else {
                Timber.d("Credits refund failed - possibly already at maximum")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error refunding credits: ${e.message}")
        }
    }

    private fun refundCreditsIfNeeded() {
        if (!PrefsManager.isSubscribed(this@ImageGenerationActivity)) {
            val creditCost = ModelValidator.getImageGenerationCost(selectedModel, selectedQuality,
                numImages // Remove .toString()
            )
            refundCredits(creditCost)
            Toast.makeText(
                this@ImageGenerationActivity,
                "Credits refunded due to generation error",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun generateImage(prompt: String) {
        // Validate state before generation
        if (selectedQuality.isEmpty()) {
            Timber.e("Quality not set, initializing...")
            initializeQualityForModel(selectedModel)
        }

        Timber.d("Starting image generation with prompt: $prompt")
        Timber.d("Model: $selectedModel, Quality: $selectedQuality, IsI2I: $isImageToImageMode, NumImages: $numImages")

        errorTimer = Handler(Looper.getMainLooper())
        errorTimer?.postDelayed({
            if (isGenerating) {
                Timber.e("Safety timeout triggered - UI appears to be stuck")
                stopGenerationAnimation()
                showEmptyState()
                refundCreditsIfNeeded()
                Toast.makeText(
                    this@ImageGenerationActivity,
                    "Image generation timed out. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, 120000) // 2 minute safety timeout

        if (!PrefsManager.isSubscribed(this)) {
            val creditCost = ModelValidator.getImageGenerationCost(selectedModel, selectedQuality,
                numImages // Remove .toString()
            )
            
            // Check if user has sufficient credits before proceeding
            if (!creditManager.hasSufficientImageCredits(creditCost)) {
                Timber.w("Insufficient image credits to generate image")
                showInsufficientCreditsDialog(creditCost)
                return
            }
            
            // Consume credits
            if (!creditManager.consumeImageCredits(creditCost)) {
                Timber.e("Failed to consume image credits - generation aborted")
                showInsufficientCreditsDialog(creditCost)
                return
            }
            
            // Update UI immediately after consuming credits
            updateCreditsInfo()
            updateGenerateButtonState()
            Timber.d("Consumed $creditCost image credits - UI updated")
        }

        showLoadingState()
        Timber.d("Showing loading state")

        negativePrompt = etNegativePrompt.text.toString().trim()

        Timber.d("Selected model: $selectedModel")

        generationJob = lifecycleScope.launch {
            try {
                val result = withTimeoutOrNull(360_000L) {
                    generateImageWithRetry(prompt)
                }

                if (result == null) {
                    withContext(Dispatchers.Main) {
                        handleGenerationTimeout()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in generateImage coroutine")
                withContext(Dispatchers.Main) {
                    handleGenerationError(e)
                }
            }
        }
    }
    // Add all the remaining methods from the previous implementation
    // (generateImageWithRetry, makeApiRequest, handleSuccessfulResponse, etc.)
    // These remain largely the same as in the previous version...

    private fun showEmptyState() {
        try {
            binding.imageResultCard.visibility = View.GONE
            binding.loadingCard.visibility = View.GONE
            binding.emptyStateContainer.visibility = View.VISIBLE

            currentImageBitmap = null
            binding.ivGeneratedImage.setImageBitmap(null)

            binding.etImagePrompt.text = null
            binding.etImagePrompt.requestFocus()

            // Reset all parameters
            negativePrompt = ""
            etNegativePrompt.text = null
            guidanceScale = 7.5f
            guidanceScaleSeekBar.progress = 65
            numInferenceSteps = when (selectedModel) {
                ModelManager.FLUX_SCHNELL_ID, ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> 4
                else -> 25
            }
            inferenceStepsSeekBar.progress = numInferenceSteps - 1
            numImages = 1
            numImagesSeekBar.progress = 0
            seed = null
            etSeed.setText("")
            enableSafetyChecker = true
            switchSafetyChecker.isChecked = true
            enableWatermark = false
            switchWatermark.isChecked = false

            selectedColors.clear()
            colorChipGroup.removeAllViews()

            if (advancedOptionsLayout.visibility == View.VISIBLE) {
                advancedOptionsLayout.visibility = View.GONE
                btnAdvancedOptions.setImageResource(R.drawable.ic_expand_more)
            }

            // Reset image-to-image state
            selectedSourceImageUri = null
            ivSourceImage.setImageURI(null)
            ivSourceImage.visibility = View.GONE
            sourceImagePlaceholder.visibility = View.VISIBLE

            additionalImageUris.clear()
            additionalImagesChipGroup.removeAllViews()

            strength = 0.95f
            seekBarStrength.progress = 85

            safetyTolerance = "2"
            seekBarSafetyTolerance.progress = 1

            updateUIForSelectedModel()

        } catch (e: Exception) {
            Timber.e(e, "Error showing empty state")
        }
    }

    // [Rest of implementation continues with generateImageWithRetry, makeApiRequest, etc.]
    // These methods remain largely the same as in the previous version...

    private fun preloadAds() {
        // Pre-load rewarded ads for better user experience
        lifecycleScope.launch {
            try {
                // Give a small delay to ensure managers are ready
                delay(1000)
                
                // Force load a rewarded ad if user can earn more credits
                if (!PrefsManager.isSubscribed(this@ImageGenerationActivity) && 
                    creditManager.canEarnMoreCredits(CreditManager.CreditType.IMAGE_GENERATION)) {
                    Timber.d("Pre-loading rewarded ad for image generation")
                    // The ad will be loaded automatically by the AdManager
                }
            } catch (e: Exception) {
                Timber.e(e, "Error preloading ads")
            }
        }
    }

    private fun provideHapticFeedback(duration: Long) {
        try {
            HapticManager.customVibration(this, duration)
        } catch (e: Exception) {
            Timber.e(e, "Error providing haptic feedback")
        }
    }

    override fun onDestroy() {
        generationJob?.cancel()
        generationJob = null

        ImageGenerationHttpClient.cancelAll()
        stopGenerationAnimation()

        super.onDestroy()
    }

    private suspend fun generateImageWithRetry(prompt: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!validateApiConfiguration()) {
                    withContext(Dispatchers.Main) {
                        stopGenerationAnimation()
                        refundCreditsIfNeeded()
                    }
                    return@withContext false
                }

                // For image-to-image models, convert images to base64
                if (isImageToImageMode) {
                    if (selectedSourceImageUri == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ImageGenerationActivity,
                                "Please select a source image",
                                Toast.LENGTH_SHORT
                            ).show()
                            stopGenerationAnimation()
                        }
                        return@withContext false
                    }

                    withContext(Dispatchers.Main) {
                        binding.tvGeneratingStatus.text = "Processing image..."
                        binding.tvGeneratingSubStatus.text = "Converting image to optimal format..."
                    }

                    val sourceImageBase64 = ImageUploadUtils.uploadImage(
                        this@ImageGenerationActivity,
                        selectedSourceImageUri!!
                    )

                    if (sourceImageBase64 == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ImageGenerationActivity,
                                "Failed to process source image",
                                Toast.LENGTH_SHORT
                            ).show()
                            stopGenerationAnimation()
                            refundCreditsIfNeeded()
                        }
                        return@withContext false
                    }

                    val allImageBase64s = mutableListOf(sourceImageBase64)

                    if (ModelValidator.supportsMultipleSourceImages(selectedModel) && additionalImageUris.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            binding.tvGeneratingStatus.text = "Processing additional images..."
                            binding.tvGeneratingSubStatus.text = "Converting ${additionalImageUris.size} more images..."
                        }

                        val additionalBase64s = ImageUploadUtils.uploadMultipleImages(
                            this@ImageGenerationActivity,
                            additionalImageUris
                        )

                        allImageBase64s.addAll(additionalBase64s)
                    }

                    withContext(Dispatchers.Main) {
                        binding.tvGeneratingStatus.text = "Generating image..."
                        binding.tvGeneratingSubStatus.text = "Creating your masterpiece..."
                    }

                    val requestJson = when (selectedModel) {
                        ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> {
                            ImageGenerationUtils.createFluxDevImageToImageRequest(
                                prompt = prompt,
                                imageBase64DataUrl = sourceImageBase64,
                                guidanceScale = guidanceScale,
                                numInferenceSteps = numInferenceSteps,
                                enableSafetyChecker = enableSafetyChecker,
                                strength = strength,
                                numImages = numImages,
                                seed = seed,
                                enableProgressiveLoading = true
                            )
                        }
                        ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID,
                        ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID -> {
                            ImageGenerationUtils.createFluxKontextImageToImageRequest(
                                modelId = selectedModel,
                                prompt = prompt,
                                imageBase64DataUrls = allImageBase64s,
                                guidanceScale = guidanceScale,
                                safetyTolerance = safetyTolerance,
                                outputFormat = "jpeg",
                                aspectRatio = selectedAspectRatioFormat,
                                numImages = numImages,
                                seed = seed,
                                enableProgressiveLoading = true
                            )
                        }
                        ModelManager.SEEDEDIT_3_I2I_ID -> {
                            ImageGenerationUtils.createSeedEdit3ImageToImageRequest(
                                imageBase64 = sourceImageBase64,
                                prompt = prompt,
                                size = "adaptive",
                                responseFormat = "url",
                                seed = seed,
                                guidanceScale = guidanceScale,
                                watermark = enableWatermark,
                                enableProgressiveLoading = true
                            )
                        }
                        else -> {
                            Timber.e("Unknown image-to-image model: $selectedModel")
                            JSONObject().apply {
                                put("model", selectedModel)
                                put("prompt", prompt)
                                put("image_url", sourceImageBase64)
                            }
                        }
                    }

                    if (!validateRequestJson(requestJson)) {
                        withContext(Dispatchers.Main) {
                            stopGenerationAnimation()
                            refundCreditsIfNeeded()
                        }
                        return@withContext false
                    }

                    return@withContext makeApiRequest(requestJson)
                }

                // Standard text-to-image generation
                val requestJson = when (selectedModel) {
                    ModelManager.DALLE_3_ID -> {
                        ImageGenerationUtils.createDalle3Request(
                            prompt = prompt,
                            size = selectedSize,
                            style = selectedStyle,
                            quality = selectedQuality,
                            responseFormat = selectedOutputFormat,
                            enableProgressiveLoading = true
                        )
                    }
                    ModelManager.SEEDREAM_3_ID -> {
                        ImageGenerationUtils.createSeedDream3Request(
                            prompt = prompt,
                            size = selectedSize,
                            guidanceScale = guidanceScale,
                            responseFormat = selectedOutputFormat,
                            watermark = enableWatermark,
                            seed = seed,
                            enableProgressiveLoading = true
                        )
                    }
                    "stable-diffusion-v35-large" -> {
                        ImageGenerationUtils.createStableDiffusionRequest(
                            prompt = prompt,
                            negativePrompt = negativePrompt,
                            imageSize = selectedAspectRatio,
                            guidanceScale = guidanceScale,
                            numInferenceSteps = numInferenceSteps,
                            enableSafetyChecker = enableSafetyChecker,
                            outputFormat = selectedOutputFormat,
                            numImages = numImages,
                            seed = seed,
                            enableProgressiveLoading = true
                        )
                    }
                    ModelManager.FLUX_SCHNELL_ID -> {
                        ImageGenerationUtils.createFluxSchnellRequest(
                            prompt = prompt,
                            imageSize = selectedAspectRatio,
                            numInferenceSteps = numInferenceSteps,
                            enableSafetyChecker = enableSafetyChecker,
                            numImages = numImages,
                            seed = seed,
                            enableProgressiveLoading = true
                        )
                    }
                    ModelManager.FLUX_REALISM_ID -> {
                        ImageGenerationUtils.createFluxRealismRequest(
                            prompt = prompt,
                            imageSize = selectedAspectRatio,
                            guidanceScale = guidanceScale,
                            numInferenceSteps = numInferenceSteps,
                            enableSafetyChecker = enableSafetyChecker,
                            outputFormat = selectedOutputFormat,
                            numImages = numImages,
                            seed = seed,
                            enableProgressiveLoading = true
                        )
                    }
                    ModelManager.RECRAFT_V3_ID -> {
                        ImageGenerationUtils.createRecraftRequest(
                            prompt = prompt,
                            imageSize = selectedAspectRatio,
                            style = selectedStyle,
                            colors = selectedColors,
                            numImages = numImages,
                            enableProgressiveLoading = true
                        )
                    }
                    ModelManager.QWEN_IMAGE_ID -> {
                        ImageGenerationUtils.createQwenImageRequest(
                            prompt = prompt,
                            outputFormat = selectedOutputFormat,
                            numImages = numImages,
                            seed = seed,
                            enableSafetyChecker = enableSafetyChecker,
                            guidanceScale = guidanceScale,
                            syncMode = false,
                            negativePrompt = negativePrompt,
                            enableProgressiveLoading = true
                        )
                    }
                    ModelManager.IMAGEN_4_ULTRA_ID -> {
                        ImageGenerationUtils.createImagen4UltraRequest(
                            prompt = prompt,
                            convertBase64ToUrl = true,
                            numImages = numImages,
                            seed = seed,
                            enhancePrompt = true,
                            aspectRatio = selectedAspectRatio,
                            personGeneration = "allow_adult",
                            safetySetting = "block_medium_and_above",
                            enableProgressiveLoading = true
                        )
                    }
                    else -> {
                        ImageGenerationUtils.createDalle3Request(
                            prompt = prompt,
                            size = "1024x1024",
                            style = "vivid",
                            quality = "standard",
                            responseFormat = "url",
                            enableProgressiveLoading = true
                        )
                    }
                }

                if (!validateRequestJson(requestJson)) {
                    withContext(Dispatchers.Main) {
                        stopGenerationAnimation()
                        refundCreditsIfNeeded()
                    }
                    return@withContext false
                }

                return@withContext makeApiRequest(requestJson)

            } catch (e: Exception) {
                Timber.e(e, "Error in generateImageWithRetry: ${e.message}")
                withContext(Dispatchers.Main) {
                    showError("Error preparing image generation: ${e.message}")
                    stopGenerationAnimation()
                    refundCreditsIfNeeded()
                }
                false
            }
        }
    }

    private suspend fun makeApiRequest(requestJson: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            val apiKey = getApiKey()
            val baseUrl = getBaseUrl()

            val request = Request.Builder()
                .url("${baseUrl}v1/images/generations")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .addHeader("X-Request-ID", UUID.randomUUID().toString())
                .tag("image_generation_${System.currentTimeMillis()}")
                .build()

            Timber.d("Making API call to: ${baseUrl}v1/images/generations")

            val deferred = CompletableDeferred<Boolean>()

            ImageGenerationHttpClient.client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.e(e, "API call failed: ${e.message}")

                    val errorMessage = when (e) {
                        is SocketTimeoutException -> "Connection timed out. Please verify your internet connection and API key."
                        is java.net.UnknownHostException -> "Cannot connect to image generation server. Please check your internet connection."
                        is javax.net.ssl.SSLException -> "Secure connection to the server failed."
                        else -> "Network error: ${e.message}"
                    }

                    runOnUiThread {
                        showError(errorMessage)
                        stopGenerationAnimation()
                        refundCreditsIfNeeded()
                    }

                    if (!deferred.isCompleted) deferred.complete(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        Timber.d("API response received, status: ${response.code}")

                        if (responseBody == null) {
                            Timber.e("Response body is null")
                            runOnUiThread {
                                showError("Empty response from server")
                                stopGenerationAnimation()
                                refundCreditsIfNeeded()
                            }
                            if (!deferred.isCompleted) deferred.complete(false)
                            return
                        }

                        if (!response.isSuccessful) {
                            val errorDetail = parseErrorResponse(responseBody, response.code)
                            Timber.e("API error: ${response.code}, body: $responseBody")

                            runOnUiThread {
                                showError(errorDetail)
                                stopGenerationAnimation()
                                refundCreditsIfNeeded()
                            }
                            if (!deferred.isCompleted) deferred.complete(false)
                            return
                        }

                        handleSuccessfulResponse(responseBody, deferred)
                    } catch (e: Exception) {
                        Timber.e(e, "Error handling API response: ${e.message}")
                        runOnUiThread {
                            showError("Error processing response: ${e.message}")
                            stopGenerationAnimation()
                            refundCreditsIfNeeded()
                        }
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
            })

            return@withContext deferred.await()
        } catch (e: Exception) {
            Timber.e(e, "Error in makeApiRequest: ${e.message}")
            withContext(Dispatchers.Main) {
                showError("API request error: ${e.message}")
                stopGenerationAnimation()
                refundCreditsIfNeeded()
            }
            return@withContext false
        }
    }

    private fun validateApiConfiguration(): Boolean {
        val apiKey = getApiKey()
        val baseUrl = getBaseUrl()

        if (apiKey.isBlank()) {
            Timber.e("API key is blank or null")
            return false
        }

        if (baseUrl.isBlank() || !baseUrl.startsWith("http")) {
            Timber.e("Base URL is invalid: $baseUrl")
            return false
        }

        Timber.d("Will attempt to connect to: ${baseUrl}v1/images/generations")
        return true
    }

    private fun validateRequestJson(requestJson: JSONObject): Boolean {
        try {
            if (!requestJson.has("model")) {
                Timber.e("Missing model in request")
                return false
            }

            if (!requestJson.has("prompt") || requestJson.getString("prompt").isBlank()) {
                Timber.e("Missing or empty prompt")
                return false
            }

            val model = requestJson.getString("model")
            val prompt = requestJson.getString("prompt")
            Timber.d("Request validation - Model: $model, Prompt length: ${prompt.length}")

            val promptPreview = if (prompt.length > 50) prompt.substring(0, 50) + "..." else prompt
            Timber.d("Prompt preview: $promptPreview")

            return true
        } catch (e: Exception) {
            Timber.e(e, "Error validating request JSON: ${e.message}")
            return false
        }
    }

    private fun handleSuccessfulResponse(responseBody: String, deferred: CompletableDeferred<Boolean>) {
        try {
            Timber.d("Raw API response: $responseBody")

            if (responseBody.isBlank() || !responseBody.trim().startsWith("{")) {
                Timber.e("Invalid JSON response: $responseBody")
                runOnUiThread {
                    showError("Server returned an invalid response. Please try again.")
                    stopGenerationAnimation()
                    refundCreditsIfNeeded()
                }
                if (!deferred.isCompleted) deferred.complete(false)
                return
            }

            val jsonResponse = JSONObject(responseBody)

            when {
                jsonResponse.has("data") -> {
                    val dataArray = jsonResponse.getJSONArray("data")
                    if (dataArray.length() > 0) {
                        val imageObject = dataArray.getJSONObject(0)
                        processImageObject(imageObject, deferred)
                    } else {
                        runOnUiThread {
                            showError("No image data in response")
                            stopGenerationAnimation()
                            refundCreditsIfNeeded()
                        }
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
                jsonResponse.has("images") -> {
                    val imagesArray = jsonResponse.getJSONArray("images")
                    if (imagesArray.length() > 0) {
                        val imageUrl = imagesArray.getJSONObject(0).getString("url")
                        runOnUiThread {
                            loadGeneratedImage(imageUrl, deferred)
                        }
                    } else {
                        runOnUiThread {
                            showError("No images in response")
                            stopGenerationAnimation()
                            refundCreditsIfNeeded()
                        }
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
                else -> {
                    runOnUiThread {
                        showError("Unexpected response format")
                        stopGenerationAnimation()
                        refundCreditsIfNeeded()
                    }
                    if (!deferred.isCompleted) deferred.complete(false)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing response: ${e.message}")
            runOnUiThread {
                showError("Error processing server response: ${e.message}")
                stopGenerationAnimation()
                refundCreditsIfNeeded()
            }
            if (!deferred.isCompleted) deferred.complete(false)
        }
    }

    private fun processImageObject(imageObject: JSONObject, deferred: CompletableDeferred<Boolean>) {
        Timber.d("Processing image object with keys: ${imageObject.keys().asSequence().toList().joinToString()}")

        when {
            imageObject.has("url") -> {
                Timber.d("Image object contains URL")
                val imageUrl = imageObject.getString("url")
                runOnUiThread {
                    loadGeneratedImage(imageUrl, deferred)
                }
            }
            imageObject.has("b64_json") -> {
                Timber.d("Image object contains base64 image")
                val base64Image = imageObject.getString("b64_json")
                processBase64Image(base64Image, deferred)
            }
            else -> {
                Timber.e("Image object has no recognized image data")
                runOnUiThread {
                    showError("No image data in response")
                    stopGenerationAnimation()
                    refundCreditsIfNeeded()
                }
                if (!deferred.isCompleted) deferred.complete(false)
            }
        }
    }

    private fun processBase64Image(base64Image: String, deferred: CompletableDeferred<Boolean>) {
        runOnUiThread {
            try {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                if (bitmap != null) {
                    Timber.d("Successfully decoded bitmap from base64, size: ${bitmap.width}x${bitmap.height}")
                    currentImageBitmap = bitmap
                    binding.ivGeneratedImage.setImageBitmap(bitmap)
                    
                    // Save to gallery repository
                    saveImageToGallery(bitmap)

                    Handler(Looper.getMainLooper()).postDelayed({
                        showResultWithAnimation()
                        if (!deferred.isCompleted) deferred.complete(true)
                    }, 100)
                } else {
                    Timber.e("Failed to decode bitmap from base64")
                    showError("Failed to decode image data")
                    showEmptyState()
                    if (!deferred.isCompleted) deferred.complete(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing base64 image")
                showError("Error processing image: ${e.message}")
                showEmptyState()
                if (!deferred.isCompleted) deferred.complete(false)
            }
        }
    }

    private fun loadGeneratedImage(imageUrl: String, deferred: CompletableDeferred<Boolean>) {
        Timber.d("Loading image from URL: $imageUrl")

        runOnUiThread {
            binding.tvGeneratingStatus.text = "Image generated! Loading now..."
            binding.tvGeneratingSubStatus.text = "Please wait while we download your image"

            Toast.makeText(
                this@ImageGenerationActivity,
                "Image generated! Loading now...",
                Toast.LENGTH_SHORT
            ).show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(imageUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doInput = true
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Timber.e("Server returned HTTP ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        showError("Failed to download image: HTTP ${connection.responseCode}")
                        stopGenerationAnimation()
                        refundCreditsIfNeeded()
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                    return@launch
                }

                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                if (bitmap != null) {
                    Timber.d("Successfully downloaded bitmap: ${bitmap.width}x${bitmap.height}")

                    withContext(Dispatchers.Main) {
                        currentImageBitmap = bitmap
                        binding.ivGeneratedImage.setImageBitmap(bitmap)
                        binding.ivGeneratedImage.visibility = View.VISIBLE
                        
                        // Save to gallery repository
                        saveImageToGallery(bitmap)
                        
                        showResultWithAnimation()
                        if (!deferred.isCompleted) deferred.complete(true)
                    }
                } else {
                    Timber.e("Failed to decode bitmap from network")
                    withContext(Dispatchers.Main) {
                        showError("Failed to decode downloaded image")
                        stopGenerationAnimation()
                        refundCreditsIfNeeded()
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading/displaying image: ${e.message}")
                withContext(Dispatchers.Main) {
                    showError("Error loading image: ${e.message}")
                    stopGenerationAnimation()
                    refundCreditsIfNeeded()
                    if (!deferred.isCompleted) deferred.complete(false)
                }
            }
        }
    }

    private fun parseErrorResponse(responseBody: String, statusCode: Int): String {
        Timber.e("Error response: $responseBody")
        return try {
            val json = JSONObject(responseBody)
            when {
                json.has("error") -> {
                    val error = if (json.get("error") is JSONObject) {
                        json.getJSONObject("error")
                    } else {
                        JSONObject().put("message", json.getString("error"))
                    }
                    error.optString("message", "Unknown error")
                }
                json.has("message") -> json.getString("message")
                json.has("meta") && json.getJSONArray("meta").length() > 0 -> {
                    "Validation error: Please check your model and parameters"
                }
                else -> "API Error: $statusCode"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing error response")
            "API Error: $statusCode"
        }
    }

    private fun handleGenerationTimeout() {
        Timber.e("Image generation timed out after 6 minutes")
        showError("Image generation timed out. This can happen with complex prompts or during high server load. Please try again with a simpler prompt or different model.")
        stopGenerationAnimation()
        ImageGenerationHttpClient.cancelAll()
        refundCreditsIfNeeded()
    }

    private fun handleGenerationError(e: Exception) {
        val errorMessage = when (e) {
            is SocketTimeoutException -> "Connection timed out. Please try again."
            is java.net.ConnectException -> "Cannot connect to server. Please check your internet connection."
            is kotlinx.coroutines.TimeoutCancellationException -> "Image generation took too long. Please try with a simpler prompt."
            else -> "Error: ${e.message}"
        }

        showError(errorMessage)
        stopGenerationAnimation()
        showEmptyState()
        refundCreditsIfNeeded()
    }

    private fun cancelGeneration() {
        generationJob?.cancel()
        stopGenerationAnimation()
        ImageGenerationHttpClient.cancelAll()
        showEmptyState()

        if (!PrefsManager.isSubscribed(this)) {
            val creditCost = ModelValidator.getImageGenerationCost(selectedModel, selectedQuality,
                numImages
            )
            refundCredits(creditCost)
            Toast.makeText(this, "Generation cancelled and credits refunded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Image generation cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoadingState() {
        binding.emptyStateContainer.visibility = View.GONE
        binding.imageResultCard.visibility = View.GONE
        binding.loadingCard.visibility = View.VISIBLE
        isGenerating = true

        startParticleAnimation()
        startStatusUpdates()
    }

    private fun startParticleAnimation() {
        lottieAnimationView.setAnimation("image_generation_animation.json")
        lottieAnimationView.playAnimation()

        particleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                binding.progressBar.scaleX = 0.85f + value * 0.15f
                binding.progressBar.scaleY = 0.85f + value * 0.15f
            }

            start()
        }
    }

    private fun startStatusUpdates() {
        val generationSteps = listOf(
            "Interpreting your description...",
            "Gathering visual elements...",
            "Applying artistic style...",
            "Adding fine details...",
            "Finalizing your image...",
            "Almost there...",
            "Creating your masterpiece..."
        )

        val subStatus = listOf(
            "This may take a few moments",
            "AI is working its magic",
            "Building pixel by pixel",
            "Adding the finishing touches",
            "Refining the details",
            "Almost ready to show you"
        )

        statusUpdateJob = lifecycleScope.launch {
            var stepIndex = 0
            var subIndex = 0

            while (isGenerating) {
                binding.tvGeneratingStatus.text = generationSteps[stepIndex % generationSteps.size]
                binding.tvGeneratingSubStatus.text = subStatus[subIndex % subStatus.size]

                binding.tvGeneratingStatus.alpha = 0f
                binding.tvGeneratingStatus.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start()

                delay(400)

                binding.tvGeneratingSubStatus.alpha = 0f
                binding.tvGeneratingSubStatus.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start()

                stepIndex++
                subIndex++
                delay(3000)
            }
        }
    }

    private fun showResultWithAnimation() {
        Timber.d("Showing result with animation")
        errorTimer?.removeCallbacksAndMessages(null)
        errorTimer = null

        isGenerating = false
        statusUpdateJob?.cancel()
        statusUpdateJob = null
        particleAnimator?.cancel()
        particleAnimator = null

        lottieAnimationView.cancelAnimation()

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { showResultWithAnimation() }
            return
        }

        try {
            Timber.d("UI Debug - currentImageBitmap: ${currentImageBitmap != null}")

            binding.loadingCard.visibility = View.GONE
            binding.emptyStateContainer.visibility = View.GONE
            binding.imageResultCard.visibility = View.VISIBLE
            binding.ivGeneratedImage.visibility = View.VISIBLE

            if (currentImageBitmap != null) {
                binding.ivGeneratedImage.setImageBitmap(currentImageBitmap)
            }

            val modelName = ImageGenerationUtils.getModelDisplayName(selectedModel)
            tvImageCaption.text = "Generated with $modelName"

            provideHapticFeedback(50)
            Toast.makeText(
                this@ImageGenerationActivity,
                "Image generated successfully!",
                Toast.LENGTH_SHORT
            ).show()

            binding.ivGeneratedImage.scaleX = 0.8f
            binding.ivGeneratedImage.scaleY = 0.8f
            binding.ivGeneratedImage.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            Timber.d("Result animation completed successfully")
            
            // Show interstitial ad after successful image generation
            adManager.smartAdTrigger(this, AdManager.AdTrigger.IMAGE_GENERATION_COMPLETE)
        } catch (e: Exception) {
            Timber.e(e, "Error showing result animation")
            binding.loadingCard.visibility = View.GONE
            binding.imageResultCard.visibility = View.VISIBLE

            if (currentImageBitmap != null) {
                binding.ivGeneratedImage.setImageBitmap(currentImageBitmap)
            }

            Toast.makeText(
                this@ImageGenerationActivity,
                "Image generated successfully!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopGenerationAnimation() {
        isGenerating = false
        errorTimer?.removeCallbacksAndMessages(null)
        errorTimer = null
        try {
            statusUpdateJob?.cancel()
            statusUpdateJob = null

            particleAnimator?.cancel()
            particleAnimator = null

            lottieAnimationView.cancelAnimation()

            runOnUiThread {
                binding.loadingCard.visibility = View.GONE
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping generation animation")
        }
    }

    private fun checkStoragePermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadGeneratedImage()
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    downloadGeneratedImage()
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("To save images to your device, we need permission to access your storage.")
                        .setPositiveButton("Grant Permission") { _, _ ->
                            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun downloadGeneratedImage() {
        val bitmap = currentImageBitmap ?: run {
            Toast.makeText(this, "No image to download", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "AI_Image_${timestamp}.png"
                var savedImageUri: Uri? = null

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
                        savedImageUri = imageUri
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/AI_Generated"
                    val dir = File(imagesDir)
                    if (!dir.exists()) dir.mkdirs()

                    val image = File(dir, filename)
                    FileOutputStream(image).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }

                    savedImageUri = try {
                        FileProvider.getUriForFile(
                            this@ImageGenerationActivity,
                            "${applicationContext.packageName}.provider",
                            image
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error creating FileProvider URI")
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    if (savedImageUri != null) {
                        showDownloadSuccessAnimation()
                        showDownloadNotification(filename, savedImageUri)
                        Toast.makeText(
                            this@ImageGenerationActivity,
                            "Image saved! Tap notification to view or share.",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Show interstitial ad after successful image download
                        adManager.smartAdTrigger(this@ImageGenerationActivity, AdManager.AdTrigger.IMAGE_DOWNLOAD)
                    } else {
                        Toast.makeText(
                            this@ImageGenerationActivity,
                            "Image saved but notification may not be clickable",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving image: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ImageGenerationActivity,
                        "Error saving image: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showDownloadSuccessAnimation() {
        ValueAnimator.ofArgb(
            ContextCompat.getColor(this, R.color.colorAccent),
            ContextCompat.getColor(this, R.color.colorPrimary)
        ).apply {
            duration = 1000
            addUpdateListener { animator ->
                binding.btnDownload.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    animator.animatedValue as Int
                )
            }
            start()
        }

        provideHapticFeedback(50)
    }

    private fun showDownloadNotification(filename: String, imageUri: Uri? = null) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Image Downloaded")
            .setContentText("$filename saved to Pictures/AI_Generated. Tap to view.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))

        imageUri?.let { uri ->
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val canResolveIntent = try {
                packageManager.queryIntentActivities(viewIntent, 0).isNotEmpty()
            } catch (e: Exception) {
                Timber.e(e, "Error querying intent activities")
                false
            }

            if (canResolveIntent) {
                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val viewPendingIntent = PendingIntent.getActivity(
                    this,
                    DOWNLOAD_NOTIFICATION_ID,
                    viewIntent,
                    pendingIntentFlags
                )

                notificationBuilder.setContentIntent(viewPendingIntent)

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Check out this AI-generated image!")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val shareChooserIntent = Intent.createChooser(shareIntent, "Share Image")
                shareChooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                val sharePendingIntent = PendingIntent.getActivity(
                    this,
                    SHARE_NOTIFICATION_ID,
                    shareChooserIntent,
                    pendingIntentFlags
                )

                notificationBuilder.addAction(
                    R.drawable.ic_share,
                    "Share",
                    sharePendingIntent
                )
            }
        }

        val notification = notificationBuilder.build()
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    private fun shareGeneratedImage() {
        val bitmap = currentImageBitmap ?: run {
            Toast.makeText(this, "No image to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(cachePath, "AI_Image_${timestamp}.png")

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }

            val contentUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val modelName = when (selectedModel) {
                "stable-diffusion-v35-large" -> "Stable Diffusion v3.5"
                ModelManager.FLUX_DEV_IMAGE_TO_IMAGE_ID -> "Flux Dev I2I"
                ModelManager.FLUX_KONTEXT_MAX_IMAGE_TO_IMAGE_ID -> "Flux Kontext Max I2I"
                ModelManager.FLUX_KONTEXT_PRO_IMAGE_TO_IMAGE_ID -> "Flux Kontext Pro I2I"
                else -> ImageGenerationUtils.getModelDisplayName(selectedModel)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Check out this AI-generated image using $modelName!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            binding.btnShare.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .withEndAction {
                    binding.btnShare.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()

            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } catch (e: Exception) {
            Timber.e(e, "Error sharing image: ${e.message}")
            Toast.makeText(
                this,
                "Error sharing image: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openImageGallery() {
        val intent = Intent(this, ImageGalleryActivity::class.java)
        startActivity(intent)
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create a unique filename
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "AI_Image_${timestamp}.png"
                
                // Save to app's private directory
                val imagesDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AI_Generated")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                
                val imageFile = File(imagesDir, filename)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                // Get current prompt from the UI
                val prompt = withContext(Dispatchers.Main) {
                    binding.etImagePrompt.text.toString().trim()
                }
                
                // Create generation settings
                val generationSettings = GenerationSettings(
                    style = if (selectedModel == ModelManager.DALLE_3_ID) selectedStyle else null,
                    quality = if (selectedModel == ModelManager.DALLE_3_ID) selectedQuality else null,
                    aspectRatio = if (selectedModel != ModelManager.DALLE_3_ID) selectedAspectRatio else selectedSize,
                    seed = null // Add seed if available in the future
                )
                
                // Save to repository
                val modelDisplayName = ImageGenerationUtils.getModelDisplayName(selectedModel)
                Timber.d("Saving image to gallery - Model: $modelDisplayName, Prompt: $prompt")
                Timber.d("File path: ${imageFile.absolutePath}, File exists: ${imageFile.exists()}, File size: ${imageFile.length()}")
                
                val savedImage = imageGalleryRepository.saveImage(
                    filePath = imageFile.absolutePath,
                    aiModel = modelDisplayName,
                    prompt = prompt,
                    generationSettings = generationSettings
                )
                
                Timber.d("Image saved to gallery with ID: ${savedImage.id}, File: ${imageFile.absolutePath}")
                Timber.d("Repository now has ${imageGalleryRepository.getImageCount()} total images")
                
            } catch (e: Exception) {
                Timber.e(e, "Error saving image to gallery: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Timber.e("Error: $message")
        stopGenerationAnimation()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        try {
            MaterialAlertDialogBuilder(this)
                .setTitle("Image Generation Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    showEmptyState()
                }
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Error showing error dialog")
            showEmptyState()
        }
    }

    private fun getApiKey(): String {
        return apiConfigs[RetrofitInstance.ApiServiceType.AIMLAPI]?.apiKey ?: ""
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.image_generation_menu, menu)
        
        // Update menu items based on subscription and credit status
        val canEarnCredits = creditManager.canEarnMoreCredits(CreditManager.CreditType.IMAGE_GENERATION)
        val creditsToMax = creditManager.getCreditsToMax(CreditManager.CreditType.IMAGE_GENERATION)
        
        if (!PrefsManager.isSubscribed(this) && canEarnCredits && creditsToMax > 0) {
            menu.findItem(R.id.menu_watch_ad_image).title = "Watch ad for 1 credit"
            menu.findItem(R.id.menu_watch_ad_image).isEnabled = true
        } else {
            menu.findItem(R.id.menu_watch_ad_image).isEnabled = false
            menu.findItem(R.id.menu_watch_ad_image).title = "Watch ad for 1 credit (limit reached)"
        }
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_watch_ad_image -> {
                if (creditManager.canEarnMoreCredits(CreditManager.CreditType.IMAGE_GENERATION)) {
                    showRewardedAd()
                } else {
                    Toast.makeText(this, "You've reached the daily maximum credits for image generation", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getBaseUrl(): String {
        var baseUrl = apiConfigs[RetrofitInstance.ApiServiceType.AIMLAPI]?.baseUrl ?: ""
        if (baseUrl.isNotEmpty() && !baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
        return baseUrl
    }

    /**
     * Show dialog when user has insufficient credits
     */
    private fun showInsufficientCreditsDialog(creditType: CreditManager.CreditType) {
        val typeName = when (creditType) {
            CreditManager.CreditType.CHAT -> "chat"
            CreditManager.CreditType.IMAGE_GENERATION -> "image generation"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Insufficient Credits")
            .setMessage("You don't have enough credits for $typeName. Watch ads to earn more credits or upgrade to premium for unlimited usage.")
            .setPositiveButton("Watch Ad") { dialog, which ->
                adManager.showRewardedAd(this, creditType, AdManager.AdTrigger.INSUFFICIENT_CREDITS)
            }
            .setNegativeButton("Upgrade to Premium") { dialog, which ->
                startActivity(Intent(this, StartActivity::class.java).apply {
                    putExtra("showPremiumFlow", true)
                })
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private val apiConfigs = mapOf(
        RetrofitInstance.ApiServiceType.AIMLAPI to ApiConfig(
            baseUrl = "https://api.aimlapi.com/",
            apiKey = BuildConfig.AIMLAPI_KEY
        )
    )

    data class ApiConfig(
        val baseUrl: String,
        val apiKey: String
    )
}