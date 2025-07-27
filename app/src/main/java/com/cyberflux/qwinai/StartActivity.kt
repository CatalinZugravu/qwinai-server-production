package com.cyberflux.qwinai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.provider.MediaStore
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.airbnb.lottie.LottieAnimationView
import com.cyberflux.qwinai.adapter.ViewPagerAdapter
import com.cyberflux.qwinai.databinding.ActivityStartBinding
import com.cyberflux.qwinai.model.AIFeature
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.DirectThemeApplier
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.workers.CreditResetWorker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class StartActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityStartBinding
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private lateinit var conversationsViewModel: ConversationsViewModel

    // Camera-related properties
    private var currentPhotoPath: String = ""
    private val CAMERA_PERMISSION_REQUEST = 101
    private var lastClickTime = 0L
    private val CLICK_DEBOUNCE_TIME = 1000L // 1 second
    
    // Dialog reference to prevent window leaks
    private var rateAppDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Timber.d("StartActivity onCreate started")

            // Initialize binding first for immediate UI
            binding = ActivityStartBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Apply theme gradient immediately for visual feedback
            DirectThemeApplier.applyGradientToActivity(this)

            // Initialize critical UI components first
            initializeCriticalUIComponents()

            // Setup basic UI immediately for perceived performance
            setupBasicUI()

            // Initialize remaining components asynchronously
            initializeComponentsAsync()

            Timber.d("StartActivity initialization completed")
        } catch (e: Exception) {
            Timber.e(e, "Fatal error in StartActivity onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing app. Please restart.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Initialize critical UI components first for immediate responsiveness
     */
    private fun initializeCriticalUIComponents() {
        try {
            // Initialize ViewModel first - needed for HistoryFragment
            conversationsViewModel = ViewModelProvider(this)[ConversationsViewModel::class.java]

            // Setup status bar immediately
            setupStatusBar()

            Timber.d("Critical UI components initialized in StartActivity")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing critical UI components: ${e.message}")
        }
    }

    /**
     * Setup basic UI immediately for perceived performance
     */
    private fun setupBasicUI() {
        try {
            // Setup ViewPager immediately - users see content faster
            setupViewPager()

            // Setup input area - primary interaction point
            setupInputArea()

            // Setup buttons that are immediately visible
            setupProButton()
            setupSettingsButton()

            // Update UI based on subscription status
            updateUIBasedOnSubscriptionStatus()

            Timber.d("Basic UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up basic UI: ${e.message}")
        }
    }

    /**
     * Initialize remaining components asynchronously
     */
    private fun initializeComponentsAsync() {
        lifecycleScope.launch {
            try {
                // Initialize startup services in background
                withContext(Dispatchers.IO) {
                    initializeStartupServices()
                }

                // Background animation removed for cleaner design
                delay(100)

                // Apply entrance animation for the bottom input
                delay(200)
                withContext(Dispatchers.Main) {
                    applyInputCardAnimation()
                }

                // Handle post-startup tasks with delay
                delay(500)
                withContext(Dispatchers.Main) {
                    handlePostStartupTasks()
                }

                Timber.d("Async components initialization completed")
            } catch (e: Exception) {
                Timber.e(e, "Error in async initialization: ${e.message}")
            }
        }
    }

    private fun initializeStartupServices() {
        try {
            Timber.d("Initializing startup services in StartActivity")

            // Initialize model validators - needed for feature availability checks
            ModelValidator.clearCache()
            ModelValidator.init()

            // Reset free conversations and credits if needed
            PrefsManager.resetFreeConversationsIfNeeded()
            resetFreeMessagesIfNeeded()

            // Schedule credit reset worker if not already scheduled
            scheduleDailyResetWorker()

            Timber.d("Startup services initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing startup services: ${e.message}")
        }
    }

    private fun handlePostStartupTasks() {
        // Check for any notification dot
        checkForNotifications()

        // Check for app updates with delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkForAppUpdates()
        }, 3000) // Check after 3 seconds

        // Show rate app dialog after much longer delay
        Handler(Looper.getMainLooper()).postDelayed({
            showRateAppMessage()
        }, 60000L) // 60 seconds delay
    }

    private fun updateUIBasedOnSubscriptionStatus() {
        val isSubscribed = PrefsManager.isSubscribed(this)

        // Update PRO button
        if (isSubscribed) {
            binding.btnPro.text = "PRO ✓"
            binding.proStatusIndicator?.visibility = View.VISIBLE
        } else {
            binding.btnPro.text = "GET PRO"
            binding.proStatusIndicator?.visibility = View.GONE
        }
    }

    private fun resetFreeMessagesIfNeeded() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastResetDate = prefs.getLong("last_reset_date", 0L)
        val currentDate = System.currentTimeMillis()

        if (!isSameDay(lastResetDate, currentDate)) {
            // Reset counters for new day
            prefs.edit().apply {
                putLong("last_reset_date", currentDate)
                putInt("ads_watched_today", 0)
                putInt("free_messages_left", 10)
                apply()
            }
            Timber.d("Free messages reset for new day")
        }
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date(time1)) == sdf.format(Date(time2))
    }

    private fun scheduleDailyResetWorker() {
        try {
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            // Set execution time to midnight (00:00:00)
            dueDate.set(Calendar.HOUR_OF_DAY, 0)
            dueDate.set(Calendar.MINUTE, 0)
            dueDate.set(Calendar.SECOND, 0)

            // If it's already past midnight, schedule for the next day
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.DATE, 1)
            }

            // Calculate delay until execution
            val timeDiff = dueDate.timeInMillis - System.currentTimeMillis()

            // Schedule one-time work with delay
            val resetWorkRequest = OneTimeWorkRequestBuilder<CreditResetWorker>()
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .build()

            // Then schedule daily repeating work for subsequent days
            val dailyResetWorkRequest = PeriodicWorkRequestBuilder<CreditResetWorker>(
                24, TimeUnit.HOURS
            ).build()

            // Enqueue the work requests
            WorkManager.getInstance(applicationContext).apply {
                enqueue(resetWorkRequest)
                enqueueUniquePeriodicWork(
                    "daily_credit_reset",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    dailyResetWorkRequest
                )
            }

            Timber.d("Daily credit reset worker scheduled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule daily reset worker: ${e.message}")
        }
    }

    private fun checkForAppUpdates() {
        try {
            // Get the update manager from MyApp
            MyApp.getUpdateManager()?.checkForUpdates(this)
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates: ${e.message}")
        }
    }

    private fun showRateAppMessage() {
        if (!isFinishing && !isDestroyed) {
            try {
                rateAppDialog = AlertDialog.Builder(this)
                    .setTitle("Rate Our App")
                    .setMessage("If you enjoy using our app, please take a moment to rate it. Your feedback is valuable to us!")
                    .setPositiveButton("Rate Now") { _, _ ->
                        val uri = "market://details?id=$packageName".let { Uri.parse(it) }
                        Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            try {
                                startActivity(this)
                            } catch (e: Exception) {
                                Timber.e(e, "Error opening app store: ${e.message}")
                            }
                        }
                    }
                    .setNegativeButton("Maybe Later", null)
                    .setNeutralButton("No, Thanks", null)
                    .create()
                
                // Additional safety check before showing
                if (!isFinishing && !isDestroyed) {
                    rateAppDialog?.show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error showing rate app dialog: ${e.message}")
            }
        }
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            // Animate the button when clicked
            binding.btnSettings.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    binding.btnSettings.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()

                    // Launch SettingsActivity
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                .start()

            provideHapticFeedback()
        }

        // Make sure the button is visible
        binding.btnSettings.visibility = View.VISIBLE
    }

    private fun setupStatusBar() {
        window.statusBarColor = getColor(R.color.colorPrimary)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    private fun setupViewPager() {
        try {
            // Initialize ViewPager adapter
            viewPagerAdapter = ViewPagerAdapter(this)
            binding.viewPager.adapter = viewPagerAdapter

            // DISABLE SWIPING
            binding.viewPager.isUserInputEnabled = false

            // Initial state setup - ensure home content is visible at start
            binding.inputCardLayout.visibility = View.VISIBLE
            binding.headerLayout.visibility = View.VISIBLE

            // Add page change callback
            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)

                    // Show/hide elements based on selected tab
                    binding.inputCardLayout.visibility = if (position == 0) View.VISIBLE else View.GONE

                    // Animate header visibility change
                    if (position == 0) {
                        // Show header when switching to Home tab
                        binding.headerLayout.alpha = 0f
                        binding.headerLayout.visibility = View.VISIBLE
                        binding.headerLayout.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()

                        // Animate ViewPager margin change
                        val params = binding.viewPager.layoutParams as CoordinatorLayout.LayoutParams
                        val targetMargin = resources.getDimensionPixelSize(R.dimen.header_height)

                        // Only animate if current margin is different
                        if (params.topMargin != targetMargin) {
                            val currentMargin = params.topMargin
                            val animator = android.animation.ValueAnimator.ofInt(currentMargin, targetMargin)
                            animator.addUpdateListener { valueAnimator ->
                                params.topMargin = valueAnimator.animatedValue as Int
                                binding.viewPager.layoutParams = params
                            }
                            animator.duration = 200
                            animator.start()
                        }
                    } else {
                        // Hide header when switching to History tab
                        binding.headerLayout.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                binding.headerLayout.visibility = View.GONE
                            }
                            .start()

                        // Animate ViewPager margin change
                        val params = binding.viewPager.layoutParams as CoordinatorLayout.LayoutParams
                        val currentMargin = params.topMargin
                        val animator = android.animation.ValueAnimator.ofInt(currentMargin, 0)
                        animator.addUpdateListener { valueAnimator ->
                            params.topMargin = valueAnimator.animatedValue as Int
                            binding.viewPager.layoutParams = params
                        }
                        animator.duration = 200
                        animator.start()
                    }
                }
            })

            // Connect TabLayout with ViewPager
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = when(position) {
                    0 -> "Home"
                    1 -> "History"
                    else -> "Home"
                }

                // Add icons to tabs
                tab.icon = when(position) {
                    0 -> getDrawable(R.drawable.ic_home)
                    1 -> getDrawable(R.drawable.history_menu)
                    else -> null
                }
            }.attach()

            // Set up tab selection listener to handle navigation
            binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    binding.viewPager.currentItem = tab.position
                    animateTabSelection(tab)
                    provideHapticFeedback()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {
                    animateTabSelection(tab)
                    provideHapticFeedback()
                }
            })

        } catch (e: Exception) {
            Timber.e(e, "Error setting up ViewPager: ${e.message}")
            Snackbar.make(binding.root, "Error initializing app interface", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun animateTabSelection(tab: TabLayout.Tab) {
        val view = tab.view
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        provideHapticFeedback()
    }

    private fun setupProButton() {
        binding.btnPro.apply {
            setOnClickListener {
                // Animate the PRO button when clicked
                animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()

                        // Navigate to welcome activity (subscription screen)
                        navigateToWelcomeActivity()
                    }
                    .start()

                provideHapticFeedback()
            }
        }

        // Update based on subscription status
        updateUIBasedOnSubscriptionStatus()
    }

    private fun setupInputArea() {
        // Apply beautiful shadow and elevation to the card
        binding.inputCardLayout.cardElevation = 10f

        // Make the entire input card clickable
        binding.inputCardLayout.setOnClickListener {
            // Haptic feedback for better interaction
            provideHapticFeedback()

            // Scale effect on click
            binding.inputCardLayout.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.inputCardLayout.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            // Get text from input field (if any)
            val text = binding.etInputText.text.toString().trim()

            // Open MainActivity with or without text
            val intent = Intent(this, MainActivity::class.java)
            if (text.isNotEmpty()) {
                intent.putExtra("INITIAL_PROMPT", text)
                binding.etInputText.text?.clear()
            }
            startActivity(intent)
        }

        // Keep existing submit button functionality
        binding.btnSubmitText.setOnClickListener {
            val text = binding.etInputText.text.toString().trim()
            if (text.isNotEmpty()) {
                startChatWithPrompt(text)
                binding.etInputText.text?.clear()
            }
        }

        // Add voice input button
        binding.btnVoiceInput.setOnClickListener {
            // Show voice input dialog or start voice recognition
            showVoiceInputDialog()
        }
    }

    // Background animation removed for cleaner design
    // private fun setupAnimatedBackground() {
    //     val animator = binding.backgroundAnimator
    //     animator.setAnimation(R.raw.gradient_animation)
    //     animator.playAnimation()
    // }

    private fun applyInputCardAnimation() {
        // Bounce animation for input card
        binding.inputCardLayout.alpha = 0f
        binding.inputCardLayout.scaleX = 0.8f
        binding.inputCardLayout.scaleY = 0.8f
        binding.inputCardLayout.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setStartDelay(700)
            .start()

        // Fade in tabs
        binding.tabLayout.alpha = 0f
        binding.tabLayout.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(800)
            .start()
    }

    private fun checkForNotifications() {
        // Check for draft conversations
        lifecycleScope.launch {
            try {
                val draftCount = conversationsViewModel.countConversationsWithDrafts()
                if (draftCount > 0) {
                    // Show notification badge or update UI
                    Timber.d("Found $draftCount draft conversations")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for drafts: ${e.message}")
            }
        }
    }

    fun handleFeatureClick(feature: AIFeature) {
        // Prevent multiple rapid clicks
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_TIME) {
            Timber.d("Ignoring rapid click for feature: ${feature.id}")
            return
        }
        lastClickTime = currentTime

        provideHapticFeedback()

        when (feature.id) {
            "web_search" -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
                    putExtra("FORCE_MODEL", ModelManager.GROK_3_BETA_ID)
                    putExtra("FEATURE_MODE", "web_search")
                    putExtra("INITIAL_PROMPT", "What would you like to search for?")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }

            "image_upload" -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
                    putExtra("FORCE_MODEL", ModelManager.GPT_4O_ID)
                    putExtra("FEATURE_MODE", "image_upload")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }

            "ask_by_link" -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("FEATURE_MODE", "ask_by_link")
                    putExtra("SHOW_LINK_PROMPT", true)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }

            "prompt_of_day" -> {
                val dailyPrompt = getDailyPrompt()
                startChatWithPrompt(dailyPrompt)
            }

            "image_generation" -> {
                startImageGeneration()
            }

            "ocr_reader" -> {
                openCameraForOCR()
            }

            "translator" -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
                    putExtra("FORCE_MODEL", ModelManager.GPT_4_TURBO_ID)
                    putExtra("FEATURE_MODE", "translator")
                    putExtra("SHOW_TRANSLATOR_UI", true)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    // Add this method to StartActivity class
    fun getDailyPrompt(): String {
        // In a real app, this could be fetched from a server or rotated on a schedule
        val prompts = listOf(
            "If you could design a perfect AI assistant, what features would it have?",
            "What emerging technology do you think will have the biggest impact in the next decade?",
            "Describe your dream vacation destination and help me plan an itinerary.",
            "What's a scientific concept you've always wanted to understand better?",
            "If you could have dinner with any three historical figures, who would they be and why?",
            "What's the most interesting book you've read recently, and what made it special?",
            "If you could solve one global problem with unlimited resources, what would it be?",
            "What skill would you most like to learn and why?",
            "Describe a perfect day from start to finish.",
            "What invention do you think has had the most positive impact on humanity?"
        )

        // Use the day of year to select a prompt
        val calendar = java.util.Calendar.getInstance()
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        return prompts[dayOfYear % prompts.size]
    }

    fun startChatWithPrompt(prompt: String) {
        // Animate the input card before navigating
        binding.inputCardLayout.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                binding.inputCardLayout.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("INITIAL_PROMPT", prompt)
                }
                startActivity(intent)
            }
            .start()
    }

    fun startChatWithModel(modelId: String) {
        // Prevent multiple rapid clicks
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_TIME) {
            Timber.d("Ignoring rapid click for model: $modelId")
            return
        }
        lastClickTime = currentTime

        try {
            // Record usage before starting activity
            ModelUsageTracker.recordModelUsage(this, modelId)

            // Find model details for better logging
            val modelName = ModelManager.models.find { it.id == modelId }?.displayName ?: modelId
            Timber.d("Starting chat with model: $modelName ($modelId)")

            // Create intent with action to make it clearer
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
                putExtra("FORCE_MODEL", modelId)
                // Remove CLEAR_TOP flag to prevent multiple launches
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            // Start activity
            startActivity(intent)

            // Provide feedback
            Toast.makeText(this, "Starting chat with $modelName", Toast.LENGTH_SHORT).show()

            // Optional animation
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } catch (e: Exception) {
            Timber.e(e, "Error starting chat with model: ${e.message}")
            Toast.makeText(this, "Error starting chat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startImageGeneration() {
        try {
            // Always open ImageGenerationActivity, let it handle subscription checks internally
            val intent = Intent(this, ImageGenerationActivity::class.java)
            startActivity(intent)

            // Add transition animation
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

            // Provide haptic feedback
            provideHapticFeedback()

            Timber.d("Launched ImageGenerationActivity successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error launching ImageGenerationActivity: ${e.message}")
            Toast.makeText(this, "Error opening image generation: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    // New method for OCR with phone camera using DeepSeek model
    private fun openCameraForOCR() {
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }

        // Create file for photo
        val photoFile = try {
            createImageFile()
        } catch (e: Exception) {
            Timber.e(e, "Error creating image file: ${e.message}")
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
            return
        }

        val photoURI = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )

        // Create camera intent
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        // Start camera activity
        try {
            cameraLauncher.launch(takePictureIntent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching camera: ${e.message}")
            Toast.makeText(this, "Error launching camera", Toast.LENGTH_SHORT).show()
        }
    }

    // Create image file for camera
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    // ActivityResultLauncher for camera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Process the captured image
            val photoUri = Uri.fromFile(File(currentPhotoPath))

            // Start MainActivity with DeepSeek model and the captured image
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
                putExtra("FORCE_MODEL", ModelManager.MISTRAL_OCR_ID)
                putExtra("FEATURE_MODE", "ocr_reader")
                putExtra("CAMERA_IMAGE_URI", photoUri.toString())
                putExtra("INITIAL_PROMPT", "Please extract all text from this image and provide a summary.")
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    fun navigateToWelcomeActivity() {
        Intent(this, WelcomeActivity::class.java).apply {
            startActivity(this)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun showVoiceInputDialog() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_voice_input)

        // Setup animation
        val animationView = dialog.findViewById<LottieAnimationView>(R.id.voiceAnimationView)
        animationView?.playAnimation()

        // Cancel button
        dialog.findViewById<android.widget.Button>(R.id.btnCancelVoice)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // In a real app, this would use SpeechRecognizer to capture voice
        // For demonstration, show an example result after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()

            // Show "captured" text
            binding.etInputText.setText("Tell me about the latest advancements in AI")

            // Auto-send after slight delay
            Handler(Looper.getMainLooper()).postDelayed({
                startChatWithPrompt(binding.etInputText.text.toString())
                binding.etInputText.text?.clear()
            }, 500)
        }, 3000)
    }

    fun showWhatsNewDialog() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_whats_new)

        // Setup dismiss button
        dialog.findViewById<android.widget.Button>(R.id.btnDismissWhatsNew)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showLinkInputDialog() {
        val dialog = LinkInputDialog(this) { link ->
            // Force set model to bagoodex
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("FORCE_MODEL", ModelManager.GROK_3_BETA_ID)
                putExtra("FEATURE_MODE", "ask_by_link")
                putExtra("LINK_URL", link)
            }
            startActivity(intent)

            // Add transition animation
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        dialog.show()
    }

    private fun showTranslationDialog() {
        val dialog = TranslationDialog(this) { sourceText, sourceLang, targetLang ->
            val prompt = "Translate the following text from $sourceLang to $targetLang: $sourceText"
            startChatWithPrompt(prompt)
        }
        dialog.show()
    }

    fun provideHapticFeedback() {
        HapticManager.lightVibration(this)
    }

    private fun getCurrentFragment(): Fragment? {
        return supportFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem)
    }

    // Handle permission results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, open camera
                    openCameraForOCR()
                } else {
                    // Permission denied
                    Toast.makeText(this, "Camera permission is required for OCR", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up dialog to prevent window leaks
        try {
            rateAppDialog?.dismiss()
            rateAppDialog = null
        } catch (e: Exception) {
            Timber.e(e, "Error dismissing rate app dialog: ${e.message}")
        }
    }

    // Add necessary dialog classes for link input and translation
    inner class LinkInputDialog(
        context: Context,
        private val onLinkSubmitted: (String) -> Unit
    ) : AlertDialog(context) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.dialog_link_input)

            val btnSubmit = findViewById<android.widget.Button>(R.id.btnSubmitLink)
            val etLink = findViewById<EditText>(R.id.etLinkInput)

            btnSubmit?.setOnClickListener {
                val link = etLink?.text.toString().trim()
                if (link.isNotEmpty()) {
                    onLinkSubmitted(link)
                    dismiss()
                } else {
                    etLink?.error = "Please enter a valid URL"
                }
            }
        }
    }

    inner class TranslationDialog(
        context: Context,
        private val onTranslationRequested: (text: String, fromLang: String, toLang: String) -> Unit
    ) : AlertDialog(context) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.dialog_translation)

            val btnTranslate = findViewById<android.widget.Button>(R.id.btnTranslate)
            val etText = findViewById<EditText>(R.id.etTextToTranslate)
            val spinnerFrom = findViewById<android.widget.Spinner>(R.id.spinnerSourceLang)
            val spinnerTo = findViewById<android.widget.Spinner>(R.id.spinnerTargetLang)

            btnTranslate?.setOnClickListener {
                val text = etText?.text.toString().trim()
                val fromLang = spinnerFrom?.selectedItem.toString()
                val toLang = spinnerTo?.selectedItem.toString()

                if (text.isNotEmpty()) {
                    onTranslationRequested(text, fromLang, toLang)
                    dismiss()
                } else {
                    etText?.error = "Please enter text to translate"
                }
            }
        }
    }
}