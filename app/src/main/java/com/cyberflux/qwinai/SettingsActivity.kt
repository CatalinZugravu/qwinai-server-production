package com.cyberflux.qwinai

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.cyberflux.qwinai.databinding.ActivitySettingsBinding
import com.cyberflux.qwinai.model.ResponseTone
import com.cyberflux.qwinai.utils.ThemeManager
import android.content.Intent
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.DynamicColorManager
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.PrefsManager.isHapticFeedbackEnabled
import com.cyberflux.qwinai.utils.HapticManager
import com.cyberflux.qwinai.utils.ThemeSettingsDialog
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : BaseThemedActivity() {
    private val TAG = "SettingsActivity"
    private lateinit var binding: ActivitySettingsBinding
    private val conversationsViewModel: ConversationsViewModel by viewModels()
    // Audio settings variables
    private lateinit var switchAudioEnabled: SwitchMaterial
    private lateinit var audioFormatContainer: LinearLayout
    private lateinit var voiceTypeContainer: LinearLayout
    private lateinit var audioFormatValue: TextView
    private lateinit var voiceTypeValue: TextView
    // Material Design 3 uses simplified theming

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "SettingsActivity created, setting up click listeners")
        setupClickListeners()
        loadSavedPreferences()
    }

    /**
     * Override from BaseThemedActivity to apply dynamic accent colors
     */
    override fun applyDynamicAccentColor() {
        try {
            if (::binding.isInitialized) {
                // Apply accent colors to the settings UI
                DynamicColorManager.applyAccentColorToViewGroup(this, binding.root)
                
                // Apply to common elements
                DynamicColorManager.applyAccentColorToCommonElements(this, binding.root)
                
                Log.d(TAG, "Applied dynamic accent colors to SettingsActivity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dynamic accent colors: ${e.message}")
        }
    }

    private fun loadSavedPreferences() {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        // User Name
        val userName = PrefsManager.getUserName(this)
        binding.userNameValue.text = if (!userName.isNullOrBlank()) {
            userName
        } else {
            "Set your name so AI can address you personally"
        }

        // Default AI Model
        val defaultModel = sharedPrefs.getString("default_ai_model", "DeepSeek Chat v1.0")
        binding.defaultModelValue.text = defaultModel

        // Theme setting - show both theme mode and accent color
        binding.themeValue.text = "${ThemeManager.getThemeModeDisplayName(this)} - ${ThemeManager.getAccentColorDisplayName(this)}"


        // Haptic feedback
        binding.hapticFeedbackToggle.isChecked = sharedPrefs.getBoolean("haptic_feedback_enabled", true)

        // Auto-clean messages
        binding.autoCleanToggle.isChecked = sharedPrefs.getBoolean("auto_clean_enabled", true)

        // Cleanup interval
        val cleanupInterval = sharedPrefs.getInt("cleanup_interval_days", 30)
        binding.cleanupIntervalValue.text = getCleanupIntervalDisplayText(cleanupInterval)

        // Response tone
        val toneString = sharedPrefs.getString("response_tone", ResponseTone.DEFAULT.name)
        val tone = try {
            ResponseTone.valueOf(toneString ?: ResponseTone.DEFAULT.name)
        } catch (e: Exception) {
            ResponseTone.DEFAULT
        }
        binding.responseToneValue.text = tone.displayName

        // Initialize audio settings
        initializeAudioSettings()

        // Security & Performance settings
        binding.biometricAuthToggle.isChecked = sharedPrefs.getBoolean("biometric_auth_enabled", false)
        binding.performanceModeToggle.isChecked = sharedPrefs.getBoolean("performance_mode_enabled", true)
        binding.privacyModeToggle.isChecked = sharedPrefs.getBoolean("privacy_mode_enabled", false)
        
        // Update cleanup interval visibility based on auto-clean state
        updateCleanupIntervalVisibility()
    }
    private fun setupClickListeners() {
        // Back button in header
        binding.btnBack.setOnClickListener {
            finish()
        }

        // User Name
        binding.userNameContainer.setOnClickListener {
            showUserNameDialog()
        }

        // Default AI Model
        binding.defaultModelContainer.setOnClickListener {
            showDefaultModelDialog()
        }

        // Response Tone
        binding.responseToneContainer.setOnClickListener {
            showResponseToneDialog()
        }

        // Theme Selection
        binding.themeContainer.setOnClickListener {
            showThemeDialog()
        }


        // Haptic Feedback
        binding.hapticFeedbackToggle.setOnCheckedChangeListener { _, isChecked ->
            savePreference("haptic_feedback_enabled", isChecked)

            // Provide feedback only if we're enabling it
            if (isChecked) {
                HapticManager.mediumVibration(this)
            }

            Toast.makeText(
                this,
                if (isChecked) "Haptic feedback enabled" else "Haptic feedback disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Auto-clean Messages
        binding.autoCleanToggle.setOnCheckedChangeListener { _, isChecked ->
            savePreference("auto_clean_enabled", isChecked)

            // If auto-clean is enabled but interval is set to "Never", set it to default 30 days
            if (isChecked) {
                val currentInterval = getSharedPreferences("app_settings", MODE_PRIVATE)
                    .getInt("cleanup_interval_days", 30)
                if (currentInterval == -1) {
                    savePreference("cleanup_interval_days", 30)
                    binding.cleanupIntervalValue.text = "30 days"
                }
            } else {
                // If auto-clean is disabled, set interval to "Never"
                savePreference("cleanup_interval_days", -1)
                binding.cleanupIntervalValue.text = "Never"
            }

            // Provide feedback
            if (isHapticFeedbackEnabled(this)) {
                HapticManager.mediumVibration(this)
            }

            Toast.makeText(
                this,
                if (isChecked) "Auto-clean enabled" else "Auto-clean disabled",
                Toast.LENGTH_SHORT
            ).show()
            
            // Update cleanup interval visibility
            updateCleanupIntervalVisibility()
        }

        binding.btnAutoCleanInfo.setOnClickListener {
            showAutoCleanInfoDialog()
        }

        // Cleanup Interval
        binding.cleanupIntervalContainer.setOnClickListener {
            if (binding.autoCleanToggle.isChecked) {
                showCleanupIntervalDialog()
            } else {
                Toast.makeText(this, "Enable auto-clean first to set cleanup interval", Toast.LENGTH_SHORT).show()
            }
        }

        // Chat settings
        binding.btnDeleteAllChats.setOnClickListener {
            deleteAllChats()
        }

        binding.btnDeleteUnsavedChats.setOnClickListener {
            deleteUnsavedChats()
        }

        binding.btnDeleteSavedChats.setOnClickListener {
            deleteSavedChats()
        }

        // Support options
        binding.btnShareApp.setOnClickListener {
            shareApp()
        }

        binding.btnRateApp.setOnClickListener {
            rateApp()
        }

        binding.btnContactUs.setOnClickListener {
            contactUs()
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        binding.btnAbout.setOnClickListener {
            showAbout()
        }

        // Tools Section
        binding.fileGenerationContainer.setOnClickListener {
            openFileGenerationActivity()
        }

        binding.textSelectionContainer.setOnClickListener {
            openTextSelectionTool()
        }

        binding.videoPlayerContainer.setOnClickListener {
            openVideoPlayerTool()
        }

        // Security & Performance
        binding.biometricAuthToggle.setOnCheckedChangeListener { _, isChecked ->
            savePreference("biometric_auth_enabled", isChecked)
            
            if (isChecked) {
                // Check if biometric auth is available
                if (isBiometricAuthAvailable()) {
                    Toast.makeText(this, "Biometric authentication enabled", Toast.LENGTH_SHORT).show()
                } else {
                    binding.biometricAuthToggle.isChecked = false
                    Toast.makeText(this, "Biometric authentication not available on this device", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Biometric authentication disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.performanceModeToggle.setOnCheckedChangeListener { _, isChecked ->
            savePreference("performance_mode_enabled", isChecked)
            Toast.makeText(
                this,
                if (isChecked) "Performance mode enabled" else "Performance mode disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.privacyModeToggle.setOnCheckedChangeListener { _, isChecked ->
            savePreference("privacy_mode_enabled", isChecked)
            Toast.makeText(
                this,
                if (isChecked) "Enhanced privacy enabled" else "Enhanced privacy disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.securityDashboardContainer.setOnClickListener {
            showSecurityDashboard()
        }
    }

    private fun showThemeDialog() {
        // Show the new Material Design 3 theme settings dialog
        val themeDialog = ThemeSettingsDialog(this)
        themeDialog.show()
        
        // Update the displayed theme name
        binding.themeValue.text = "${ThemeManager.getThemeModeDisplayName(this)} - ${ThemeManager.getAccentColorDisplayName(this)}"
    }


    private fun showResponseToneDialog() {
        val tones = ResponseTone.values().map { it.displayName }.toTypedArray()
        val currentToneString = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getString("response_tone", ResponseTone.DEFAULT.name)

        val currentTone = try {
            ResponseTone.valueOf(currentToneString ?: ResponseTone.DEFAULT.name)
        } catch (e: Exception) {
            ResponseTone.DEFAULT
        }

        val selectedIndex = ResponseTone.values().indexOf(currentTone)

        AlertDialog.Builder(this)
            .setTitle("Response Tone")
            .setSingleChoiceItems(tones, selectedIndex) { dialog, which ->
                val selectedTone = ResponseTone.values()[which]

                // Save preference
                getSharedPreferences("app_settings", MODE_PRIVATE).edit {
                    putString("response_tone", selectedTone.name)
                }

                // Update UI
                binding.responseToneValue.text = selectedTone.displayName

                // Set result to refresh main activity if needed
                setResult(RESULT_OK)

                dialog.dismiss()

                // Provide feedback
                Toast.makeText(this, "${selectedTone.displayName} tone selected", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUserNameDialog() {
        val currentName = PrefsManager.getUserName(this) ?: ""
        
        val editText = EditText(this).apply {
            setText(currentName)
            hint = "Enter your name"
            setSingleLine(true)
            requestFocus()
            selectAll()
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Your Name")
            .setMessage("Set your name so the AI can address you personally in conversations.")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotBlank()) {
                    PrefsManager.setUserName(this, newName)
                    binding.userNameValue.text = newName
                    Toast.makeText(this, "Name saved! AI will now address you as $newName", Toast.LENGTH_SHORT).show()
                } else {
                    PrefsManager.setUserName(this, "")
                    binding.userNameValue.text = "Set your name so AI can address you personally"
                    Toast.makeText(this, "Name cleared", Toast.LENGTH_SHORT).show()
                }
                if (isHapticFeedbackEnabled(this@SettingsActivity)) {
                    HapticManager.lightVibration(this@SettingsActivity)
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                PrefsManager.setUserName(this, "")
                binding.userNameValue.text = "Set your name so AI can address you personally"
                Toast.makeText(this, "Name cleared", Toast.LENGTH_SHORT).show()
                if (isHapticFeedbackEnabled(this@SettingsActivity)) {
                    HapticManager.lightVibration(this@SettingsActivity)
                }
            }
            .create()
        
        dialog.show()
        
        // Show keyboard automatically
        editText.postDelayed({
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun savePreference(key: String, value: Boolean) {
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit {
                putBoolean(key, value)
            }
    }

    private fun savePreference(key: String, value: String) {
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit {
                putString(key, value)
            }
    }
    private fun initializeAudioSettings() {
        // Get references to UI elements
        switchAudioEnabled = findViewById(R.id.switchAudioEnabled)
        audioFormatContainer = findViewById(R.id.audioFormatContainer)
        voiceTypeContainer = findViewById(R.id.voiceTypeContainer)
        audioFormatValue = findViewById(R.id.audioFormatValue)
        voiceTypeValue = findViewById(R.id.voiceTypeValue)

        // Load current settings
        val audioEnabled = PrefsManager.getAudioEnabled(this, false)
        val audioFormat = PrefsManager.getAudioFormat(this, "mp3")
        val voiceType = PrefsManager.getAudioVoice(this, "alloy")

        // Set up the audio enabled switch
        switchAudioEnabled.isChecked = audioEnabled
        switchAudioEnabled.setOnCheckedChangeListener { _, isChecked ->
            PrefsManager.setAudioEnabled(this, isChecked)

            // Enable/disable other audio options based on switch state
            audioFormatContainer.isEnabled = isChecked
            voiceTypeContainer.isEnabled = isChecked

            // Update visual state
            updateAudioSettingsVisualState(isChecked)

            // Provide feedback
            HapticManager.mediumVibration(this)

            Toast.makeText(
                this,
                if (isChecked) "Audio output enabled" else "Audio output disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Display current values
        audioFormatValue.text = audioFormat
        voiceTypeValue.text = voiceType

        // Set up click listeners for format and voice type
        audioFormatContainer.setOnClickListener {
            if (switchAudioEnabled.isChecked) {
                showAudioFormatDialog()
            } else {
                Toast.makeText(this, "Enable audio output first", Toast.LENGTH_SHORT).show()
            }
        }

        voiceTypeContainer.setOnClickListener {
            if (switchAudioEnabled.isChecked) {
                showVoiceTypeDialog()
            } else {
                Toast.makeText(this, "Enable audio output first", Toast.LENGTH_SHORT).show()
            }
        }

        // Set initial visual state
        updateAudioSettingsVisualState(audioEnabled)
    }

    private fun updateAudioSettingsVisualState(enabled: Boolean) {
        // Update text and icon colors based on enabled state
        val alpha = if (enabled) 1.0f else 0.5f
        audioFormatContainer.alpha = alpha
        voiceTypeContainer.alpha = alpha
    }

    private fun showAudioFormatDialog() {
        val audioFormats = arrayOf("mp3", "wav", "opus", "flac")
        val currentFormat = PrefsManager.getAudioFormat(this, "mp3")
        val currentIndex = audioFormats.indexOf(currentFormat).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Select Audio Format")
            .setSingleChoiceItems(audioFormats, currentIndex) { dialog, which ->
                val selectedFormat = audioFormats[which]

                // Save the selection
                PrefsManager.setAudioFormat(this, selectedFormat)

                // Update UI
                audioFormatValue.text = selectedFormat

                // Provide feedback
                HapticManager.selectionFeedback(this)

                Toast.makeText(this, "$selectedFormat format selected", Toast.LENGTH_SHORT).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVoiceTypeDialog() {
        val voiceTypes = arrayOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
        val currentVoice = PrefsManager.getAudioVoice(this, "alloy")
        val currentIndex = voiceTypes.indexOf(currentVoice).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Select Voice Type")
            .setSingleChoiceItems(voiceTypes, currentIndex) { dialog, which ->
                val selectedVoice = voiceTypes[which]

                // Save the selection
                PrefsManager.setAudioVoice(this, selectedVoice)

                // Update UI
                voiceTypeValue.text = selectedVoice

                // Provide feedback
                HapticManager.selectionFeedback(this)

                Toast.makeText(this, "$selectedVoice voice selected", Toast.LENGTH_SHORT).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun showDefaultModelDialog() {
        // Get models from ModelManager
        val models = ModelManager.models.map { it.displayName }.toTypedArray()
        val currentModel = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getString("default_ai_model", "DeepSeek Chat v1.0")
        val selectedIndex = models.indexOf(currentModel).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Choose Default AI Model")
            .setSingleChoiceItems(models, selectedIndex) { dialog, which ->
                // Save the selection
                val selectedModel = models[which]
                savePreference("default_ai_model", selectedModel)

                // Also save the model ID for easier lookup in MainActivity
                val modelId = ModelManager.models.find { it.displayName == selectedModel }?.id ?: ""
                savePreference("default_ai_model_id", modelId)

                // Update the UI
                binding.defaultModelValue.text = selectedModel

                // Set a flag to indicate model has changed (for immediate apply)
                savePreference("model_changed", true)

                // Set the result to let MainActivity know settings changed
                setResult(RESULT_OK)

                Toast.makeText(this, "$selectedModel set as default and applied", Toast.LENGTH_SHORT).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAllChats() {
        AlertDialog.Builder(this)
            .setTitle("Delete All Chats")
            .setMessage("Are you sure you want to delete all conversations? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                conversationsViewModel.deleteAllConversations()
                Toast.makeText(this, "All conversations deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUnsavedChats() {
        AlertDialog.Builder(this)
            .setTitle("Delete Unsaved Chats")
            .setMessage("Are you sure you want to delete all unsaved conversations? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                conversationsViewModel.deleteAllUnsavedConversations()
                Toast.makeText(this, "Unsaved conversations deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSavedChats() {
        AlertDialog.Builder(this)
            .setTitle("Delete Saved Chats")
            .setMessage("Are you sure you want to delete all saved conversations? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                conversationsViewModel.deleteAllSavedConversations()
                Toast.makeText(this, "Saved conversations deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareApp() {
        val shareText = "Check out this amazing AI chat app: https://play.google.com/store/apps/details?id=$packageName"
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            startActivity(Intent.createChooser(this, "Share App"))
        }
    }

    private fun rateApp() {
        val uri = "market://details?id=$packageName".toUri()
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            startActivity(goToMarket)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
            startActivity(webIntent)
        }
    }

    private fun contactUs() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@deepseekchat.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Support Request - DeepSeek Chat")
        }
        try {
            startActivity(Intent.createChooser(emailIntent, "Contact Us"))
        } catch (e: Exception) {
            Toast.makeText(this, "No email client found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, "https://www.deepseekchat.com/privacy".toUri())
        startActivity(intent)
    }

    private fun showAutoCleanInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Auto-clean Messages")
            .setMessage("When enabled, unsaved conversations older than the selected interval will be automatically deleted to free up storage space. Saved conversations are never automatically deleted.\n\nYou can disable this feature or change the cleanup interval at any time in Settings.")
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun showCleanupIntervalDialog() {
        val intervals = arrayOf("7 days", "15 days", "30 days", "60 days", "90 days", "Never")
        val intervalValues = arrayOf(7, 15, 30, 60, 90, -1) // -1 for never
        
        val currentInterval = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getInt("cleanup_interval_days", 30)
        
        val selectedIndex = intervalValues.indexOf(currentInterval).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Select Cleanup Interval")
            .setSingleChoiceItems(intervals, selectedIndex) { dialog, which ->
                val selectedInterval = intervalValues[which]
                
                // Save preference
                savePreference("cleanup_interval_days", selectedInterval)

                // Update UI
                binding.cleanupIntervalValue.text = intervals[which]

                // If user selected "Never", disable auto-clean
                if (selectedInterval == -1) {
                    binding.autoCleanToggle.isChecked = false
                    savePreference("auto_clean_enabled", false)
                    Toast.makeText(this, "Auto-clean disabled - conversations will never be automatically deleted", Toast.LENGTH_LONG).show()
                } else {
                    // If auto-clean was disabled, enable it when user selects an interval
                    if (!binding.autoCleanToggle.isChecked) {
                        binding.autoCleanToggle.isChecked = true
                        savePreference("auto_clean_enabled", true)
                    }
                    Toast.makeText(this, "Cleanup interval set to ${intervals[which]}", Toast.LENGTH_SHORT).show()
                }

                // Provide haptic feedback
                if (isHapticFeedbackEnabled(this)) {
                    HapticManager.mediumVibration(this)
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCleanupIntervalDisplayText(days: Int): String {
        return when (days) {
            7 -> "7 days"
            15 -> "15 days"
            30 -> "30 days"
            60 -> "60 days"
            90 -> "90 days"
            -1 -> "Never"
            else -> "$days days"
        }
    }

    private fun savePreference(key: String, value: Int) {
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit {
                putInt(key, value)
            }
    }

    private fun updateCleanupIntervalVisibility() {
        val isAutoCleanEnabled = binding.autoCleanToggle.isChecked
        val alpha = if (isAutoCleanEnabled) 1.0f else 0.5f
        
        binding.cleanupIntervalContainer.isEnabled = isAutoCleanEnabled
        binding.cleanupIntervalContainer.alpha = alpha
    }

    private fun showAbout() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName

        AlertDialog.Builder(this)
            .setTitle("About Qwin AI")
            .setMessage("Qwin AI is an advanced AI-powered chat application that provides intelligent conversations and assistance.\n\nVersion: $versionName")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openFileGenerationActivity() {
        try {
            val intent = Intent(this, FileGenerationActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file generation tool", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error opening FileGenerationActivity", e)
        }
    }

    private fun openTextSelectionTool() {
        try {
            val intent = Intent(this, TextSelectionActivity::class.java).apply {
                putExtra("MESSAGE_TEXT", "Welcome to the advanced text editor!\n\nThis tool provides powerful text selection and editing capabilities.")
                putExtra("IS_EDITABLE", true)
                putExtra("TITLE", "Text Editor")
                putExtra("HINT", "Enter or paste your text here...")
                putExtra("SOURCE", "settings")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening text editor", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error opening TextSelectionActivity", e)
        }
    }



    private fun openVideoPlayerTool() {
        val input = EditText(this).apply {
            hint = "Enter video URL (e.g., https://example.com/video.mp4)"
            setText("") // Default empty
        }

        AlertDialog.Builder(this)
            .setTitle("Video Player")
            .setMessage("Enter a video URL to play:")
            .setView(input)
            .setPositiveButton("Play") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (isValidVideoUrl(url)) {
                        playVideo(url)
                    } else {
                        Toast.makeText(this, "Please enter a valid video URL", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter a video URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Sample Videos") { _, _ ->
                showSampleVideosDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSampleVideosDialog() {
        val sampleVideos = arrayOf(
            "Big Buck Bunny (Sample)",
            "Elephant's Dream (Sample)", 
            "Sintel (Sample)"
        )
        
        val sampleUrls = arrayOf(
            "https://sample-videos.com/zip/10/mp4/720/big_buck_bunny_720p_1mb.mp4",
            "https://sample-videos.com/zip/10/mp4/720/elephants_dream_1080p_24fps_1mb.mp4",
            "https://sample-videos.com/zip/10/mp4/720/sintel_trailer-720p.mp4"
        )

        AlertDialog.Builder(this)
            .setTitle("Sample Videos")
            .setItems(sampleVideos) { _, which ->
                playVideo(sampleUrls[which], sampleVideos[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playVideo(url: String, title: String = "Video") {
        try {
            // Use external video player or browser to play video
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                setDataAndType(android.net.Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Try to open with video player first, fallback to browser
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback to browser
                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening video: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error opening video", e)
        }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.scheme in listOf("http", "https") && 
            (url.contains(".mp4", ignoreCase = true) || 
             url.contains(".avi", ignoreCase = true) ||
             url.contains(".mov", ignoreCase = true) ||
             url.contains(".mkv", ignoreCase = true) ||
             url.contains("youtube.com") ||
             url.contains("vimeo.com") ||
             url.contains("twitch.tv"))
        } catch (e: Exception) {
            false
        }
    }

    private fun isBiometricAuthAvailable(): Boolean {
        return try {
            val biometricManager = androidx.biometric.BiometricManager.from(this)
            when (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric availability", e)
            false
        }
    }

    private fun showSecurityDashboard() {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val biometricEnabled = sharedPrefs.getBoolean("biometric_auth_enabled", false)
        val performanceEnabled = sharedPrefs.getBoolean("performance_mode_enabled", true)
        val privacyEnabled = sharedPrefs.getBoolean("privacy_mode_enabled", false)
        
        val statusBuilder = StringBuilder()
        statusBuilder.append("🔒 Security & Performance Status\n\n")
        
        statusBuilder.append("Biometric Authentication: ")
        statusBuilder.append(if (biometricEnabled) "✅ Enabled" else "❌ Disabled")
        statusBuilder.append("\n")
        
        statusBuilder.append("Performance Mode: ")
        statusBuilder.append(if (performanceEnabled) "✅ Enabled" else "❌ Disabled")
        statusBuilder.append("\n")
        
        statusBuilder.append("Enhanced Privacy: ")
        statusBuilder.append(if (privacyEnabled) "✅ Enabled" else "❌ Disabled")
        statusBuilder.append("\n\n")
        
        statusBuilder.append("🛡️ Security Features Available:\n")
        statusBuilder.append("• Biometric authentication support\n")
        statusBuilder.append("• Encrypted local storage\n")
        statusBuilder.append("• Privacy-enhanced communications\n")
        statusBuilder.append("• Advanced security monitoring\n\n")
        
        statusBuilder.append("⚡ Performance Features:\n")
        statusBuilder.append("• Memory optimization\n")
        statusBuilder.append("• Network request optimization\n")
        statusBuilder.append("• UI rendering improvements\n")
        statusBuilder.append("• Cold start optimization")

        AlertDialog.Builder(this)
            .setTitle("Security & Performance Dashboard")
            .setMessage(statusBuilder.toString())
            .setPositiveButton("OK", null)
            .setNeutralButton("Advanced Settings") { _, _ ->
                Toast.makeText(this, "Advanced security settings coming soon", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}