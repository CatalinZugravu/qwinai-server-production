package com.cyberflux.qwinai

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextMenu
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.adapter.ConversationAdapter
import com.cyberflux.qwinai.adapter.CustomSpinnerDialog
import com.cyberflux.qwinai.adapter.ModelSpinnerAdapter
import com.cyberflux.qwinai.ui.FileUploadBottomSheet
import com.cyberflux.qwinai.ads.ConsentManager
import com.cyberflux.qwinai.ads.AdManager
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.billing.HuaweiIapProvider
import com.cyberflux.qwinai.branch.MessageManager
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.databinding.ActivityMainBinding
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.Conversation
import com.cyberflux.qwinai.model.ResponseLength
import com.cyberflux.qwinai.model.ResponsePreferences
import com.cyberflux.qwinai.model.ResponseTone
import com.cyberflux.qwinai.network.AimlApiRequest
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.network.AudioResponseHandler
import com.cyberflux.qwinai.network.ModelApiHandler
import com.cyberflux.qwinai.service.AiChatService
import com.cyberflux.qwinai.service.BackgroundAiService
import com.cyberflux.qwinai.service.OCRService
import com.cyberflux.qwinai.service.OCROptions
import com.cyberflux.qwinai.utils.AppSettings
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.ConversationSummarizer
import com.cyberflux.qwinai.utils.ConversationTokenManager
import com.cyberflux.qwinai.utils.FileFilterUtils
import com.cyberflux.qwinai.utils.FileHandler
import com.cyberflux.qwinai.utils.FileProgressTracker
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.LocationService
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PersistentFileStorage
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.ScrollArrowsHelper
import com.cyberflux.qwinai.utils.SupportedFileTypes
import com.cyberflux.qwinai.utils.StreamingStateManager
import com.cyberflux.qwinai.utils.TokenCounterHelper
import com.cyberflux.qwinai.utils.TokenLimitDialogHandler
import com.cyberflux.qwinai.utils.TokenValidator
import com.cyberflux.qwinai.utils.TranslationUtils
import com.cyberflux.qwinai.utils.UnifiedFileHandler
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sin

object NotificationConstants {
    const val CHANNEL_ID = "credits_channel"
    const val NOTIFICATION_ID = 1001
}

class MainActivity : BaseThemedActivity() {

    // -------------------------------------------------------------------------
    // PROPERTIES AND FIELDS
    // -------------------------------------------------------------------------

    // View Binding
    lateinit var binding: ActivityMainBinding
    // ViewModels and Adapters
    lateinit var conversationsViewModel: ConversationsViewModel
    lateinit var chatAdapter: ChatAdapter
    private lateinit var conversationAdapter: ConversationAdapter
    lateinit var fileHandler: FileHandler
    var selectedMessage: ChatMessage? = null
    // Chat and Conversation State
    private val aiResponseGroups = mutableMapOf<String, MutableList<ChatMessage>>()
    private var currentAiGroupId: String? = null
    var currentConversationId: String? = null
    private val conversations = mutableListOf<Conversation>()
    private val aiMessageHistory = mutableMapOf<String, MutableList<ChatMessage>>()
    private val currentMessageIndex = mutableMapOf<String, Int>()
    private var isMessageSent: Boolean = false
    private var isFirstModelSelection = true
    private var currentPage = 1
    private var isLoadingMore = false
    private var isReloadingMessage = false
    private lateinit var unifiedFileHandler: UnifiedFileHandler
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    // UI Components
    private lateinit var chatTitleView: TextView
    private val SETTINGS_REQUEST_CODE = 100
    lateinit var messageManager: MessageManager
    private var deferredIntent: Intent? = null
    // Voice recording properties
    private lateinit var btnMicrophone: ImageButton
    private lateinit var recordingOverlay: CardView
    private lateinit var tvRecordingTime: TextView
    private lateinit var btnCancelRecording: ImageButton
    private lateinit var btnSendRecording: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isRecording = false
    private var recordingStartTime = 0L
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val RECORD_AUDIO_PERMISSION_CODE = 200
    private var recordingAnimators = arrayListOf<ValueAnimator>()
    private var waveBars = ArrayList<View>()
    private var lastRmsValue = 0f  // Variable to store the last RMS value
    private var realTimeSearchIndicator: CardView? = null
    private var isRealTimeSearchActive = false
    var isPrivateModeEnabled = false
    private lateinit var btnPrivateChat: ImageButton
    private lateinit var privateModeIndicator: CardView
    private lateinit var ghostFloatAnimation: Animation
    private lateinit var ghostActivateAnimation: Animation
    private lateinit var fadeInAnimation: Animation
    private lateinit var fadeOutAnimation: Animation
    private lateinit var responsePreferences: ResponsePreferences
    private var isIntentProcessed = false
    private var processingIntentAction: String? = null
    private lateinit var scrollArrowsHelper: ScrollArrowsHelper
    private var buttonMonitoringJob: Job? = null
    private var editDialogOriginalMessage: ChatMessage? = null
    private var progressDialog: AlertDialog? = null

    private var isTranslationModeActive = false
    private var translatorLayout: View? = null
    private var sourceLanguageSpinner: Spinner? = null
    private var targetLanguageSpinner: Spinner? = null
    private var originalSubmitListener: View.OnClickListener? = null
    private var preTranslationModelId: String? = null
    lateinit var ocrService: OCRService
    // Token Management
    private lateinit var conversationTokenManager: ConversationTokenManager
    private var tokenLimitWarningBadge: View? = null
    private var loadingDialog: AlertDialog? = null

    private var recordingAnimationTimer: Handler? = null
    private var lastPartialResults: String = "" // Store partial results as they come in
    private var isSpeechProcessing: Boolean = false // Track if we're processing speech
    // Credits System - using new CreditManager
    private lateinit var creditManager: CreditManager
    private var lastWarningTime = 0L

    // For tracking user message versions
    private val userMessageGroups = mutableMapOf<String, MutableList<ChatMessage>>()
    // Add this as a class property in MainActivity
    private val conversationMetadata = mutableMapOf<String, String>()

    private lateinit var reasoningControlsLayout: LinearLayout
    private lateinit var btnReasoning: ImageButton
    private lateinit var btnDeepSearch: ImageButton// Keep the same variable name for compatibility
    private var isReturningFromTextSelection = false
    var isReturningFromFileSelection = false
    private var isDraftContentLoaded = false

    // File Handling
    private var currentPhotoPath: String = ""
    val selectedFiles = mutableListOf<FileUtil.FileUtil.SelectedFile>()
    // At the top with other properties
    private var consentHandled = false
    // Device Services
    lateinit var vibrator: Vibrator
    private lateinit var sharedPrefs: SharedPreferences

    var reasoningLevel: String = "low" // Initial reasoning level
    var isReasoningEnabled = false
    var isDeepSearchEnabled = false

    var selectedUserPosition: Int = -1
    var selectedUserMessage: ChatMessage? = null

    private lateinit var consentManager: ConsentManager
    private lateinit var adManager: AdManager
    private lateinit var btnExpandText: ImageButton
    private val TEXT_EXPANSION_REQUEST_CODE = 1001
    private var currentEditDialog: Dialog? = null
    private val EDIT_TEXT_EXPANSION_REQUEST_CODE = 1002
    private val TEXT_VIEW_REQUEST_CODE = 1003 // NEW: For view-only mode
    
    // Modern Activity Result API launchers
    private val textExpansionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isReturningFromTextSelection = true
        handleMainTextExpansionResult(result.resultCode, result.data)
    }
    
    private val editTextExpansionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isReturningFromTextSelection = true
        handleEditTextExpansionResult(result.resultCode, result.data)
    }
    
    private val textViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isReturningFromTextSelection = true
        // View-only mode - no changes expected, just log
        Timber.d("Returned from view-only text selection")
    }

    private var consentDialogShown = false
    private lateinit var btnAudioConversation: ImageButton

    var isGenerating = false
    private var currentApiJob: Job? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var pulseAnimator: ValueAnimator? = null

    // At the top with other properties
    private lateinit var aiChatService: AiChatService
    private lateinit var startActivity: StartActivity
    
    // Background service for continuing AI generation
    private var backgroundAiService: BackgroundAiService? = null
    private var isServiceBound = false
    
    // CRITICAL FIX: Prevent duplicate background generation checks
    private var isBackgroundCheckInProgress = false
    private var lastBackgroundCheckTime = 0L
    private val backgroundServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BackgroundAiService.BackgroundAiBinder
            backgroundAiService = binder.getService()
            isServiceBound = true
            Timber.d("🔗 Connected to BackgroundAiService")
            
            // CRITICAL FIX: When service connects, request progress for any generating messages
            currentConversationId?.let { conversationId ->
                lifecycleScope.launch {
                    try {
                        val messages = conversationsViewModel.getAllConversationMessages(conversationId)
                        val generatingMessages = messages.filter { !it.isUser && it.isGenerating }
                        
                        generatingMessages.forEach { message ->
                            backgroundAiService?.requestCurrentProgress(message.id, conversationId)
                            Timber.d("📡 Requested current progress for message: ${message.id}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error requesting progress on service connect: ${e.message}")
                    }
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            backgroundAiService = null
            isServiceBound = false
            Timber.d("💔 Disconnected from BackgroundAiService")
        }
    }
    
    // Broadcast receiver for background generation updates
    private val backgroundGenerationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("📨 Broadcast received with action: ${intent?.action}")
            when (intent?.action) {
                BackgroundAiService.ACTION_GENERATION_PROGRESS -> {
                    val messageId = intent.getStringExtra(BackgroundAiService.EXTRA_MESSAGE_ID)
                    val content = intent.getStringExtra(BackgroundAiService.EXTRA_CONTENT)
                    Timber.d("📨 Progress broadcast - messageId: $messageId, content length: ${content?.length}")
                    
                    // Check if this message exists in current conversation (only if adapter is initialized)
                    val messageExists = if (::chatAdapter.isInitialized) {
                        chatAdapter.currentList.any { it.id == messageId }
                    } else {
                        false
                    }
                    Timber.d("🔍 Message exists in current conversation: $messageExists")
                    
                    if (messageId != null && content != null) {
                        handleBackgroundProgress(messageId, content)
                    } else {
                        Timber.w("⚠️ Invalid progress broadcast: messageId=$messageId, content=${if (content == null) "null" else "present"}")
                    }
                }
                BackgroundAiService.ACTION_EXISTING_PROGRESS -> {
                    val messageId = intent.getStringExtra(BackgroundAiService.EXTRA_MESSAGE_ID)
                    val content = intent.getStringExtra(BackgroundAiService.EXTRA_CONTENT)
                    Timber.d("📨 Existing progress broadcast - messageId: $messageId, content length: ${content?.length}")
                    
                    if (messageId != null && content != null) {
                        handleExistingProgress(messageId, content)
                    } else {
                        Timber.w("⚠️ Invalid existing progress broadcast: messageId=$messageId, content=${if (content == null) "null" else "present"}")
                    }
                }
                BackgroundAiService.ACTION_GENERATION_COMPLETE -> {
                    val messageId = intent.getStringExtra(BackgroundAiService.EXTRA_MESSAGE_ID)
                    val content = intent.getStringExtra(BackgroundAiService.EXTRA_CONTENT)
                    Timber.d("📨 Completion broadcast - messageId: $messageId, content length: ${content?.length}")
                    if (messageId != null && content != null) {
                        handleBackgroundCompletion(messageId, content)
                    }
                }
                BackgroundAiService.ACTION_GENERATION_ERROR -> {
                    val messageId = intent.getStringExtra(BackgroundAiService.EXTRA_MESSAGE_ID)
                    val error = intent.getStringExtra(BackgroundAiService.EXTRA_ERROR)
                    Timber.d("📨 Error broadcast - messageId: $messageId, error: $error")
                    if (messageId != null && error != null) {
                        handleBackgroundError(messageId, error)
                    }
                }
            }
        }
    }

    // Add to the PROPERTIES AND FIELDS section
    private lateinit var audioResponseHandler: AudioResponseHandler
    private var audioEnabled = false
    private var currentAudioFormat = "mp3"
    private var currentVoiceType = "alloy"
    // -------------------------------------------------------------------------
    // ACTIVITY RESULT LAUNCHERS
    // -------------------------------------------------------------------------

    /**
     * Activity result launcher for picking files
     * Improved to handle file count limits properly
     */
    val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val currentModel = ModelManager.selectedModel
            val supportsImages = ModelValidator.supportsImageUpload(currentModel.id)

            if (!supportsImages) {
                Toast.makeText(
                    this,
                    "The selected model doesn't support image uploads",
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

            // Handle multiple files
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                Timber.d("Multiple files selected: $count files")

                // Check against TOTAL files for direct upload mode
                val totalFileCount = selectedFiles.size + count
                if (totalFileCount > 5) {
                    // Show warning about file limit
                    Toast.makeText(
                        this,
                        "Maximum 5 files allowed. You're trying to add $count more when you already have ${selectedFiles.size}.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Process only up to the max files allowed (considering already selected files)
                val remainingSlots = 5 - selectedFiles.size
                val filesToProcess = if (remainingSlots > 0) minOf(count, remainingSlots) else 0

                if (filesToProcess <= 0) {
                    Toast.makeText(
                        this,
                        "Cannot add more files. Please remove some first.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@registerForActivityResult
                }

                Timber.d("Processing $filesToProcess out of $count selected files")
                var processedCount = 0
                for (i in 0 until filesToProcess) {
                    val uri = data.clipData!!.getItemAt(i).uri

                    // Verify it's an image file
                    val mimeType = FileUtil.getMimeType(this, uri)
                    if (mimeType.startsWith("image/")) {
                        fileHandler.handleSelectedFile(uri, false)
                        processedCount++
                    } else {
                        Toast.makeText(
                            this,
                            "Skipped non-image file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
                // Show success message for multiple files
                if (processedCount > 1) {
                    Toast.makeText(
                        this,
                        "Added $processedCount files successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (data?.data != null) {
                // Handle single file
                Timber.d("Single file selected")

                // Check if adding one more file would exceed the limit
                if (selectedFiles.size >= 5) {
                    Toast.makeText(
                        this,
                        "Maximum 5 files allowed. Please remove some first.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }

                val uri = data.data!!
                val mimeType = FileUtil.getMimeType(this, uri)

                // Only process if it's an image file
                if (mimeType.startsWith("image/")) {
                    fileHandler.handleSelectedFile(uri, false)
                } else {
                    Toast.makeText(
                        this,
                        "Only image files are supported",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Timber.d("File picker cancelled or failed with result code: ${result.resultCode}")
        }
    }
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Timber.d("Camera result received: resultCode=${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            // Use the enhanced method for processing the photo
            processPhoto()
        } else {
            Timber.e("Camera returned with error code: ${result.resultCode}")
            if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Camera operation was cancelled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera operation failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun processPhoto() {
        Timber.d("Processing photo from path: $currentPhotoPath")

        if (currentPhotoPath.isEmpty()) {
            Timber.e("Current photo path is empty!")
            Toast.makeText(this, "Error: Photo path is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val photoFile = File(currentPhotoPath)
        Timber.d("Photo file exists: ${photoFile.exists()}, size: ${photoFile.length()} bytes")
        
        if (!photoFile.exists() || photoFile.length() == 0L) {
            Timber.e("Photo file doesn't exist or is empty: ${photoFile.absolutePath}")
            Toast.makeText(this, "Error: Photo file not found or is empty", Toast.LENGTH_SHORT).show()
            
            // Try to recover from shared preferences
            val savedPath = getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
                .getString("last_photo_path", "")
            if (!savedPath.isNullOrEmpty() && savedPath != currentPhotoPath) {
                Timber.d("Trying to recover photo from saved path: $savedPath")
                currentPhotoPath = savedPath
                val recoveredFile = File(currentPhotoPath)
                if (recoveredFile.exists() && recoveredFile.length() > 0L) {
                    Timber.d("Successfully recovered photo file")
                    // Continue with processing
                } else {
                    return
                }
            } else {
                return
            }
        }

        try {
            // Create FileProvider URI for the photo
            val photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                photoFile
            )

            // Process the photo using the enhanced handler
            handleCameraFile(photoUri, photoFile.name, photoFile.length())
            
            // Give user feedback that photo was captured successfully
            Toast.makeText(this, "Photo captured successfully!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Timber.e(e, "Error processing camera photo: ${e.message}")
            Toast.makeText(this, "Error processing photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun handleCameraFile(photoUri: Uri, fileName: String, fileSize: Long) {
        try {
            // Check if OCR model is selected - handle OCR differently
            val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
            
            if (isOcrModel) {
                // For OCR models: add file and show options, don't auto-process
                val selectedFile = FileUtil.FileUtil.SelectedFile(
                    uri = photoUri,
                    name = fileName,
                    size = fileSize,
                    isDocument = false
                )
                
                selectedFiles.add(selectedFile)
                fileHandler.updateSelectedFilesView()
                
                // OCR options are now always visible in the main UI
                
                // Provide feedback that photo is ready for OCR processing
                Toast.makeText(
                    this,
                    "Photo captured. Configure OCR options and click Send to process.",
                    Toast.LENGTH_LONG
                ).show()
                
                return
            }
            
            // For non-OCR models: continue with existing auto-processing
            lifecycleScope.launch {
                // 1. Create a temporary SelectedFile object for displaying
                val temporaryFile = FileUtil.FileUtil.SelectedFile(
                    uri = photoUri,
                    name = fileName,
                    size = fileSize,
                    isDocument = false
                )

                // 2. Add to selected files IMMEDIATELY with TEMP status
                selectedFiles.add(temporaryFile)

                // 3. Update UI to show the file with progress
                withContext(Dispatchers.Main) {
                    fileHandler.updateSelectedFilesView()

                    // 4. Get the view we just created and set up the progress tracker
                    val fileView = FileUtil.findFileViewForUri(photoUri, this@MainActivity)
                    if (fileView == null) {
                        Timber.e("Could not find view for camera photo: $photoUri")
                        Toast.makeText(this@MainActivity, "Error displaying camera photo", Toast.LENGTH_SHORT).show()
                        selectedFiles.remove(temporaryFile)
                        fileHandler.updateSelectedFilesView()
                        return@withContext
                    }

                    // 5. Create a progress tracker and initialize it with the view
                    val progressTracker = FileProgressTracker()
                    progressTracker.initWithImageFileItem(fileView)

                    // 6. Start showing progress
                    progressTracker.showProgress()
                    progressTracker.observeProgress(this@MainActivity)
                }

                // 7. Create a progress tracker for handling the file processing
                val progressTracker = FileProgressTracker()

                try {
                    progressTracker.updateProgress(
                        0,
                        "Analyzing photo",
                        FileProgressTracker.ProcessingStage.INITIALIZING
                    )

                    // 8. Process the file using the com.cyberflux.qwinai.utils.UnifiedFileHandler - updated for Grok 3 Beta
                    val fileResult = unifiedFileHandler.processFileForModel(
                        photoUri,
                        ModelManager.selectedModel.id,
                        progressTracker
                    )

                    if (fileResult.isSuccess) {
                        progressTracker.updateProgress(
                            80,
                            "Photo processed successfully",
                            FileProgressTracker.ProcessingStage.COMPLETE
                        )

                        withContext(Dispatchers.Main) {
                            // Hide progress
                            val fileView = FileUtil.findFileViewForUri(photoUri, this@MainActivity)
                            if (fileView != null) {
                                val finalProgressTracker = FileProgressTracker()
                                finalProgressTracker.initWithImageFileItem(fileView)
                                finalProgressTracker.hideProgress()
                            }

                            // Provide feedback
                            Toast.makeText(
                                this@MainActivity,
                                "Photo added successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Haptic feedback
                            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            provideHapticFeedback(50)
                        }

                        progressTracker.updateProgress(
                            100,
                            "Photo processing complete",
                            FileProgressTracker.ProcessingStage.COMPLETE
                        )
                        delay(500) // Show completion briefly
                    } else {
                        val error = fileResult.exceptionOrNull()
                        progressTracker.updateProgress(
                            100,
                            "Error: ${error?.message}",
                            FileProgressTracker.ProcessingStage.ERROR
                        )

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Error processing photo: ${error?.message}",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Remove file from selected files on error
                            selectedFiles.remove(temporaryFile)
                            fileHandler.updateSelectedFilesView()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing camera photo: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error processing photo: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Remove file from selected files on error
                        selectedFiles.remove(temporaryFile)
                        fileHandler.updateSelectedFilesView()
                    }
                }
            } // Close lifecycleScope.launch
        } catch (e: Exception) {
            Timber.e(e, "Fatal error in handleCameraFile: ${e.message}")
            Toast.makeText(
                this@MainActivity,
                "Error handling camera photo: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun debugChatAdapterState() {
        val itemCount = chatAdapter.itemCount
        val actualListSize = chatAdapter.currentList.size

        Timber.d("Chat adapter contains $itemCount items (actual list size: $actualListSize)")

        // If the current list is empty, we might be showing just the welcome message
        if (chatAdapter.currentList.isEmpty()) {
            Timber.d("Current list is empty - likely showing welcome message")
            return
        }

        // Only proceed with item debugging if we have actual messages
        for (i in 0 until chatAdapter.currentList.size) {
            val message = chatAdapter.currentList[i]
            val viewTypeName = when(val viewType = chatAdapter.getItemViewType(i)) {
                ChatAdapter.VIEW_TYPE_USER -> "USER"
                ChatAdapter.VIEW_TYPE_AI -> "AI"
                ChatAdapter.VIEW_TYPE_USER_IMAGE -> "USER_IMAGE"
                ChatAdapter.VIEW_TYPE_AI_IMAGE -> "AI_IMAGE"
                ChatAdapter.VIEW_TYPE_USER_DOCUMENT -> "USER_DOCUMENT"
                ChatAdapter.VIEW_TYPE_AI_DOCUMENT -> "AI_DOCUMENT"
                ChatAdapter.VIEW_TYPE_USER_CAMERA -> "USER_CAMERA"
                ChatAdapter.VIEW_TYPE_AI_CAMERA -> "AI_CAMERA"
                ChatAdapter.VIEW_TYPE_WELCOME_MESSAGE -> "WELCOME_MESSAGE"
                else -> "UNKNOWN($viewType)"
            }

            Timber.d("Item $i: id=${message.id}, isUser=${message.isUser}, " +
                    "isImage=${message.isImage}, isCamera=${message.isCamera}, " +
                    "viewType=$viewTypeName, content=${message.message.take(30)}")
        }
    }
    private fun restoreCurrentPhotoPath() {
        if (currentPhotoPath.isEmpty()) {
            currentPhotoPath = getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
                .getString("last_photo_path", "") ?: ""

            if (currentPhotoPath.isNotEmpty()) {
                Timber.d("Restored photo path: $currentPhotoPath")
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Camera permission granted, launching camera")
            launchCameraDirectly()
        } else {
            Timber.e("Camera permission denied")
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Timber.d("MainActivity onCreate called")
            
            // Check if we're in a problematic state (e.g., after a crash)
            if (isFinishing || isDestroyed) {
                Timber.w("Activity is finishing or destroyed, skipping onCreate")
                return
            }
            
            // Initialize intent processing tracking
            isIntentProcessed = false
            processingIntentAction = null

            // CRITICAL: Load response preferences early
            responsePreferences = loadResponsePreferences()
            
            // CRITICAL FIX: Immediate cleanup of corrupted messages on app start
            performImmediateMessageCleanup()

            // Set up view binding with null check
            try {
                binding = ActivityMainBinding.inflate(layoutInflater)
                binding.chatRecyclerView.itemAnimator = null
                setContentView(binding.root)
            } catch (e: Exception) {
                Timber.e(e, "Failed to inflate binding: ${e.message}")
                finish()
                return
            }

            // Initialize critical UI components first for immediate responsiveness
            initializeCriticalUIComponents()

            // Setup basic UI immediately for perceived performance
            setupBasicUI()

            // Initialize chat components asynchronously for better performance
            initializeChatComponentsAsync()

            // Initialize remaining non-critical components asynchronously
            initializeNonCriticalComponentsAsync()

            // Handle intent processing
            val currentIntent = intent
            var shouldShowKeyboard = true // Default to showing keyboard

            // Check if we're opening an existing conversation
            if (currentIntent != null) {
                val conversationId = currentIntent.getLongExtra("CONVERSATION_ID", -1L)
                val aiModel = currentIntent.getStringExtra("AI_MODEL")

                // Don't show keyboard if opening existing conversation
                if (conversationId != -1L && aiModel != null) {
                    shouldShowKeyboard = false
                    Timber.d("Not showing keyboard - opening existing conversation from intent")
                }

                // Process the intent
                if (!isIntentProcessed) {
                    Timber.d("Processing initial intent in onCreate: ${currentIntent.action}")
                    processingIntentAction = currentIntent.action
                    val handled = handleIntent(currentIntent)
                    if (handled) {
                        isIntentProcessed = true
                        // If intent was handled, we don't want to show keyboard for most cases
                        val featureMode = currentIntent.getStringExtra("FEATURE_MODE")
                        val hasInitialPrompt = !currentIntent.getStringExtra("INITIAL_PROMPT").isNullOrEmpty()

                        // Only show keyboard for features that need immediate input
                        shouldShowKeyboard = shouldShowKeyboard && (
                                featureMode in listOf("ask_by_link", "translator") ||
                                        (hasInitialPrompt && !currentIntent.getBooleanExtra("AUTO_SEND", false))
                                )
                    } else {
                        deferredIntent = currentIntent
                        isIntentProcessed = false
                    }
                }
            }

            // Only show keyboard and vibrate for new chats or specific features
            if (shouldShowKeyboard && !isIntentProcessed) {
                // Vibrate to indicate new chat
                provideHapticFeedback(50)

                // Wait for UI to be fully ready, then show keyboard
                binding.etInputText.post {
                    binding.etInputText.requestFocus()
                    showKeyboard()
                    forceShowKeyboard()
                }

                Timber.d("Showing keyboard for new chat in onCreate")
            } else {
                Timber.d("Not showing keyboard in onCreate - existing conversation or intent processed")
            }

            // Enable new conversation button when appropriate
            updateNewConversationButtonState()

            // Setup activity completion handlers with deferred intent processing
            Handler(Looper.getMainLooper()).postDelayed({
                processDeferredIntent()
            }, 1000) // Give time for full initialization
            
            // Register broadcast receiver for background generation updates
            registerBackgroundGenerationReceiver()
            
            // Bind to background service if it exists
            bindToBackgroundService()
            
            // Check for ongoing background generation and connect seamlessly
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndConnectToBackgroundGeneration()
            }, 2000) // Wait for app to be fully initialized

        } catch (e: Exception) {
            Timber.e(e, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing chat. Please restart.", Toast.LENGTH_LONG).show()

            // Try to recover by initializing essential components if possible
            try {
                if (!::conversationTokenManager.isInitialized) {
                    conversationTokenManager = ConversationTokenManager()
                }

                if (!::messageManager.isInitialized && ::chatAdapter.isInitialized && ::conversationsViewModel.isInitialized) {
                    messageManager = MessageManager(
                        viewModel = conversationsViewModel,
                        adapter = chatAdapter,
                        lifecycleScope = lifecycleScope,
                        chatAdapter = chatAdapter,
                        tokenManager = conversationTokenManager
                    )
                }
            } catch (innerEx: Exception) {
                Timber.e(innerEx, "Error in recovery attempt: ${innerEx.message}")
            }
        }
    }
    private fun setupChatAdapterCallbacks() {
        chatAdapter.setMessageCompletionCallback(object : ChatAdapter.MessageCompletionCallback {
            @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
            override fun onMessageCompleted(message: ChatMessage) {
                // Always use Handler for thread safety
                Handler(Looper.getMainLooper()).post {
                    try {
                        Timber.d("Message completed: ${message.id}")

                        // If this was a regenerated message, update content in branch system
                        if (isReloadingMessage && message.message.isNotEmpty()) {
                            Timber.d("🔄 COMPLETION: isReloadingMessage=$isReloadingMessage, messageId=${message.id}, messageContent=${message.message.take(50)}")
                            
                            // Add the new response as a version
                            message.addVersion(message.message)
                            
                            Timber.d("🔄 After addVersion: totalVersions=${message.totalVersions}, versionIndex=${message.versionIndex}, messageVersions.size=${message.messageVersions.size}")
                            Timber.d("🔄 AI message versions after adding: ${message.totalVersions}, current index: ${message.versionIndex}")
                            
                            messageManager.updateMessageContent(message.id, message.message)

                            // Important: Save all messages to ensure database consistency
                            messageManager.saveAllMessages()

                            // Update the message in the adapter to show navigation controls
                            val messageIndex = chatAdapter.currentList.indexOfFirst { it.id == message.id }
                            if (messageIndex != -1) {
                                chatAdapter.notifyItemChanged(messageIndex)
                            }
                            
                            // Also refresh all messages to update button visibility
                            chatAdapter.notifyDataSetChanged()

                            // Save the updated message to database if not private
                            if (!message.conversationId.startsWith("private_")) {
                                lifecycleScope.launch {
                                    conversationsViewModel.saveMessage(message)
                                }
                            }
                            
                            // CRITICAL: Reset generating state on the message itself
                            message.isGenerating = false
                        }
                        
                        // Reset flag after all version logic has completed
                        isReloadingMessage = false

                        // CRITICAL: Always reset generating state on the message itself
                        message.isGenerating = false
                        
                        // Safe way to update generating state
                        setGeneratingState(false)
                        
                        // Post to ensure UI state is fully updated before refreshing adapter
                        Handler(Looper.getMainLooper()).post {
                            // Force refresh to show buttons for latest AI message
                            chatAdapter.notifyDataSetChanged()
                            
                            Timber.d("🔴 Adapter refreshed after message completion: ${message.id}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error in message completion callback: ${e.message}")
                    }
                }
            }
        })

    }


    private fun createAiChatCallbacks(): AiChatService.Callbacks {
        return object : AiChatService.Callbacks {
            override fun isGenerating(): Boolean = this@MainActivity.isGenerating

            override fun getWebSearchEnabled(): Boolean {
                return isDeepSearchEnabled
            }

            override fun isSubscribed(): Boolean = PrefsManager.isSubscribed(this@MainActivity)

            override fun getFreeMessagesLeft(): Int = this@MainActivity.creditManager.getChatCredits()

            override fun setGeneratingState(generating: Boolean) {
                // Update the local state variable first
                isGenerating = generating

                // Remove any pending UI updates to prevent conflicts
                uiHandler.removeCallbacksAndMessages(null)

                // Use immediate execution on UI thread
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    updateButtonStateInternal(generating)
                } else {
                    uiHandler.post { updateButtonStateInternal(generating) }
                }
            }

            override fun decrementFreeMessages() {
                this@MainActivity.creditManager.consumeChatCredits(2)
                updateFreeMessagesText()
                
                // Show interstitial ad after AI response completion
                this@MainActivity.adManager.smartAdTrigger(
                    this@MainActivity, 
                    AdManager.AdTrigger.CHAT_RESPONSE_COMPLETE
                )
            }

            @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
            override fun onMessageCompleted(messageId: String, content: String) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        Timber.d("🔄 onMessageCompleted: ID=$messageId, contentLength=${content.length}, isReloadingMessage=$isReloadingMessage")
                        
                        // Get the completed message 
                        val completedMessage = messageManager.getMessageById(messageId)
                        if (completedMessage != null) {
                            Timber.d("🔄 Found completed message: isUser=${completedMessage.isUser}, isRegenerated=${completedMessage.isRegenerated}")
                            
                            // Version logic is handled in the first callback (ChatAdapter.MessageCompletionCallback)
                            // This second callback just handles final cleanup and database updates
                            
                            // Update the message in MessageManager to reflect the versioning
                            messageManager.updateMessageContent(completedMessage.id, completedMessage.message)
                            
                            // Update token count and save to database
                            conversationTokenManager.removeMessage(messageId)
                            conversationTokenManager.addMessage(completedMessage)
                            updateTokenCounterUI()

                            if (!completedMessage.conversationId.startsWith("private_")) {
                                conversationsViewModel.saveMessage(completedMessage)
                            }
                        }

                        // Use the existing method
                        messageManager.updateMessageContent(messageId, content)

                        // Note: isReloadingMessage flag is reset in the first callback after version logic completes
                        
                        // CRITICAL: Reset generating state on completed message
                        completedMessage?.let { it.isGenerating = false }
                        
                        // Update generating state
                        setGeneratingState(false)
                        
                        // Post to ensure UI state is fully updated before refreshing adapter
                        Handler(Looper.getMainLooper()).post {
                            // Force refresh to show buttons for latest AI message
                            chatAdapter.notifyDataSetChanged()
                            
                            Timber.d("🔴 Adapter refreshed after message completion: $messageId")
                        }
                        binding.typingIndicator.visibility = View.GONE

                    } catch (e: Exception) {
                        Timber.e(e, "Error handling message completion: ${e.message}")
                        setGeneratingState(false)
                        binding.typingIndicator.visibility = View.GONE
                    }
                }
            }

            override fun incrementFreeMessages() {
                this@MainActivity.creditManager.addCreditsFromAd(CreditManager.CreditType.CHAT, 2)
                updateFreeMessagesText()
            }

            override fun setWebSearchEnabled(enabled: Boolean) {
                this@MainActivity.isDeepSearchEnabled = enabled
                this@MainActivity.updateDeepSearchButtonState(enabled)

                // Save the setting
                PrefsManager.saveAiSettings(this@MainActivity, AppSettings(
                    isDeepSearchEnabled = enabled,
                    isReasoningEnabled = this@MainActivity.isReasoningEnabled,
                    reasoningLevel = this@MainActivity.reasoningLevel
                ))
            }


            override fun updateDeepSearchSetting(enabled: Boolean) {
                this@MainActivity.isDeepSearchEnabled = enabled
                this@MainActivity.updateDeepSearchButtonState(enabled)

                // Save the setting
                PrefsManager.saveAiSettings(this@MainActivity, AppSettings(
                    isDeepSearchEnabled = enabled,
                    isReasoningEnabled = this@MainActivity.isReasoningEnabled,
                    reasoningLevel = this@MainActivity.reasoningLevel
                ))
            }


            override fun getResponseLength(): ResponseLength {
                return responsePreferences.length
            }

            override fun getResponseTone(): ResponseTone {
                return responsePreferences.tone
            }

            override fun updateTypingIndicator(visible: Boolean) {
                binding.typingIndicator.visibility = if (visible) View.VISIBLE else View.GONE
            }

            override fun updateMessageInAdapter(message: ChatMessage) {
                this@MainActivity.updateMessageInAdapter(message)
            }

            override fun forceUpdateMessageInAdapter(message: ChatMessage) {
                this@MainActivity.forceUpdateMessageInAdapter(message)
            }

            override fun updateMessageInGroups(message: ChatMessage) {
                this@MainActivity.updateMessageInGroups(message)
            }

            override fun showError(message: String) {
                this@MainActivity.showError(message)
            }

            override fun scrollToBottom() {
                this@MainActivity.scrollToBottom()
            }

            override fun getCurrentAiGroupId(): String? = this@MainActivity.currentAiGroupId

            override fun setCurrentAiGroupId(groupId: String?) {
                this@MainActivity.currentAiGroupId = groupId
            }

            override fun getAiResponseGroups(): MutableMap<String, MutableList<ChatMessage>> =
                this@MainActivity.aiResponseGroups

            override fun getCurrentMessageIndex(): MutableMap<String, Int> =
                this@MainActivity.currentMessageIndex

            override fun runOnUiThread(action: () -> Unit) {
                this@MainActivity.runOnUiThread(action)
            }

            override fun saveMessage(message: ChatMessage) {
                if (!message.conversationId.startsWith("private_")) {
                    conversationsViewModel.saveMessage(message)
                }
            }

            override fun shouldShowWebSearchButton(): Boolean {
                val currentModel = ModelManager.selectedModel.id
                return ModelValidator.supportsWebSearch(currentModel)
            }



            override fun updateButtonVisibility() {
                this@MainActivity.runOnUiThread {
                    // Update web search button visibility
                    this@MainActivity.btnDeepSearch.visibility = if (shouldShowWebSearchButton()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                }
            }

            override fun suggestAlternativeModel(message: String) {
                this@MainActivity.runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Model Suggestion")
                        .setMessage(message)
                        .setPositiveButton("Switch Model") { _, _ ->
                            binding.spinnerModels.performClick()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            override fun getCurrentMessages(): List<ChatMessage> {
                return chatAdapter.currentList
            }

            override fun markFilesWithError(fileUris: List<Uri>) {
                this@MainActivity.runOnUiThread {
                    // Mark files with error state in the UI
                    for (uri in fileUris) {
                        val selectedFile = selectedFiles.find { it.uri == uri }
                        selectedFile?.let {
                            it.hasError = true
                            Timber.d("Marked file ${it.name} with error state")
                        }
                    }
                    // Update the selected files view to show error indicators
                    fileHandler.updateSelectedFilesView()
                }
            }
        }
    }
    private fun initializeChatServices() {
        try {
            Timber.d("Initializing chat-specific services")

            // Initialize consent manager for ads
            consentManager = ConsentManager(this)

            // Initialize ad manager - IMPORTANT: This should be in MainActivity, not StartActivity
            adManager = AdManager.getInstance(this)
            creditManager = CreditManager.getInstance(this)

            // Delay ad initialization to improve startup time, but only for non-subscribers
            if (!PrefsManager.isSubscribed(this)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    // AdManager initializes automatically
                    Timber.d("AdManager initialized for free user")
                }, 2000) // 2-second delay
            } else {
                Timber.d("User subscribed, skipping ad initialization")
            }

            // Initialize billing manager reference
            val billingManager = BillingManager.getInstance(this)

            // Restore subscriptions when chat starts
            billingManager.restoreSubscriptions()
            billingManager.validateSubscriptionStatus()

            Timber.d("Chat services initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing chat services: ${e.message}")
        }
    }

    /**
     * Initialize core chat components
     */
    private fun initializeChatComponents() {
        try {
            // Initialize chat-specific UI elements
            chatTitleView = binding.tvChatTitle

            // Initialize OCR service for document processing
            ocrService = OCRService(this)

            // Initialize ViewModel - needed for chat functionality
            conversationsViewModel = ViewModelProvider(this)[ConversationsViewModel::class.java]

            // Initialize system services
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            sharedPrefs = getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

            // Load AI settings but don't update UI yet
            loadAiSettingsFromPrefs()
            setupPrivateChatFeature()

            // Initialize credits and subscriptions
            updateUIBasedOnSubscriptionStatus()

            // Initialize file handlers
            unifiedFileHandler = UnifiedFileHandler(this)
            fileHandler = FileHandler(this)

            // Set up file handler submission
            binding.btnSubmitText.setOnClickListener {
                if (isGenerating) {
                    stopGeneration()
                } else {
                    fileHandler.handleSubmitTextClick()
                }
            }

            Timber.d("Core chat components initialized")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing chat components: ${e.message}")
        }
    }
    private fun updateButtonStateInternal(generating: Boolean) {
        try {
            if (generating) {
                // CRITICAL: Set stop button state immediately
                binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                binding.btnSubmitText.contentDescription = "Stop generation"
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnMicrophone.visibility = View.GONE

                // Start pulsing animation
                startPulsingAnimation()

                // Start aggressive monitoring to ensure button stays visible
                startAggressiveButtonMonitoring()

                Timber.d("Button set to STOP state with animation")
            } else {
                // Stop monitoring first
                stopAggressiveButtonMonitoring()

                // Stop animation
                stopPulsingAnimation()

                // Reset button state based on input text, file processing, and credits
                val inputText = binding.etInputText.text.toString().trim()
                val hasContent = inputText.isNotEmpty() || selectedFiles.isNotEmpty()
                val isSubscribed = PrefsManager.isSubscribed(this)
                val hasCredits = isSubscribed || (::creditManager.isInitialized && creditManager.hasSufficientChatCredits())
                
                if (!hasContent) {
                    binding.btnSubmitText.visibility = View.GONE
                    binding.btnMicrophone.visibility = View.VISIBLE
                } else {
                    binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                    binding.btnSubmitText.visibility = View.VISIBLE
                    binding.btnMicrophone.visibility = View.GONE
                    
                    if (hasCredits) {
                        binding.btnSubmitText.contentDescription = "Send message"
                        binding.btnSubmitText.isEnabled = true
                        binding.btnSubmitText.alpha = 1.0f
                    } else {
                        binding.btnSubmitText.contentDescription = "Need more credits to send"
                        binding.btnSubmitText.isEnabled = false
                        binding.btnSubmitText.alpha = 0.5f
                    }
                }
                
                // Update submit button state based on file processing (only if initialized)
                if (::fileHandler.isInitialized) {
                    fileHandler.updateSubmitButtonState()
                }

                Timber.d("Button reset to normal state")
            }

            // Update adapter state but don't let it interfere with button state
            if (::chatAdapter.isInitialized) {
                chatAdapter.setGenerating(generating)
            } else {
                Timber.w("chatAdapter not initialized yet, skipping setGenerating call")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in updateButtonStateInternal: ${e.message}")
            // Recovery: ensure button is in a valid state
            recoverButtonState()
        }
    }

    private fun initializeEnhancedButtonHandling() {
        // Set up enhanced button handlers
        setupEnhancedButtonHandlers()

        // Add debugging for button state changes
        binding.btnSubmitText.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            Timber.d("Submit button layout changed - visibility: ${binding.btnSubmitText.visibility}, " +
                    "generating: $isGenerating, " +
                    "contentDescription: ${binding.btnSubmitText.contentDescription}")
        }
    }    private fun startAggressiveButtonMonitoring() {
        stopAggressiveButtonMonitoring() // Stop any existing monitoring

        buttonMonitoringJob = lifecycleScope.launch {
            while (isGenerating && isActive) {
                // REDUCED frequency - check every 1 second instead of 200ms
                delay(1000)

                withContext(Dispatchers.Main) {
                    if (isGenerating) {
                        val isButtonCorrect = binding.btnSubmitText.visibility == View.VISIBLE &&
                                binding.btnSubmitText.contentDescription == "Stop generation"

                        if (!isButtonCorrect) {
                            Timber.d("Button state incorrect - fixing")
                            binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                            binding.btnSubmitText.contentDescription = "Stop generation"
                            binding.btnSubmitText.visibility = View.VISIBLE
                            binding.btnMicrophone.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun stopAggressiveButtonMonitoring() {
        buttonMonitoringJob?.cancel()
        buttonMonitoringJob = null
    }
    /**
     * Handle OCR document processing
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @SuppressLint("UseKtx")
    fun triggerTokenLimitDialog(conversationPercentage: Float, modelId: String, isSubscribed: Boolean) {
        // Nu afișa dialog-ul prea des
        val currentTime = System.currentTimeMillis()
        val lastDialogTime = getSharedPreferences("token_dialogs", Context.MODE_PRIVATE)
            .getLong("last_dialog_time", 0)

        if (currentTime - lastDialogTime < 30000) { // 30 secunde între dialog-uri
            return
        }

        // Salvează timpul dialog-ului
        getSharedPreferences("token_dialogs", Context.MODE_PRIVATE).edit {
            putLong("last_dialog_time", currentTime)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                if (conversationPercentage >= 0.95f) {
                    // Limită critică - forțează summarize sau new chat
                    TokenLimitDialogHandler.showTokenLimitReachedDialog(
                        context = this,
                        modelId = modelId,
                        isSubscribed = isSubscribed,
                        onSummarize = { summarizeAndStartNewChat() },
                        onNewChat = { startNewConversation() },
                        onUpgrade = { navigateToWelcomeActivity() }
                    )
                } else {
                    // Aproape de limită - oferă opțiuni
                    TokenLimitDialogHandler.showTokenLimitApproachingDialog(
                        context = this,
                        tokenPercentage = conversationPercentage,
                        onContinue = { handleContinueAnyway() },
                        onSummarize = { summarizeAndStartNewChat() },
                        onNewChat = { startNewConversation() }
                    )
                }
            }
        }, 500)
    }

    private fun initializeLocationServices() {
        // This method will be called when we have location permissions
        Timber.d("Initializing location services for web search")

        try {
            // Preload location to ensure it's available when needed
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val location = LocationService.getApproximateLocation(this@MainActivity)
                    Timber.d("Location preloaded: ${location.approximate.city}, ${location.approximate.country}")
                } catch (e: Exception) {
                    Timber.e(e, "Error preloading location: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing location services: ${e.message}")
        }
    }


    /**
     * Ensure proper branch assignment for AI responses
     */
    /**
     * Update conversation with latest user message
     */
    /**
     * Update conversation with latest user message
     */
    private fun updateConversationWithLatestMessage(userMessage: String) {
        // Only update if we have a valid conversation ID
        if (currentConversationId == null || isPrivateModeEnabled) {
            return
        }

        lifecycleScope.launch {
            try {
                // Get current conversation
                val conversation = conversationsViewModel.getConversationById(currentConversationId!!)

                if (conversation != null) {
                    // Update the conversation with this new user message
                    val updatedConversation = conversation.copy(
                        lastMessage = userMessage,  // Set the latest user message
                        lastModified = System.currentTimeMillis()
                    )

                    // Save the updated conversation
                    conversationsViewModel.updateConversation(updatedConversation)

                    Timber.d("Updated conversation lastMessage to: ${userMessage.take(30)}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating conversation with latest message: ${e.message}")
            }
        }
    }

    private fun ensureFilesPersistent() {
        try {
            val persistentStorage = PersistentFileStorage(this)
            val updatedFiles = mutableListOf<FileUtil.FileUtil.SelectedFile>()
            
            for (file in selectedFiles) {
                if (!file.isPersistent) {
                    // Make the file persistent
                    val persistentFile = file.toPersistent(persistentStorage)
                    if (persistentFile != null) {
                        updatedFiles.add(persistentFile)
                        Timber.d("Made file persistent: ${file.name}")
                    } else {
                        // Keep original if persistence failed
                        updatedFiles.add(file)
                        Timber.w("Failed to make file persistent: ${file.name}")
                    }
                } else {
                    // Already persistent
                    updatedFiles.add(file)
                }
            }
            
            // Replace the files list with persistent versions
            selectedFiles.clear()
            selectedFiles.addAll(updatedFiles)
            
        } catch (e: Exception) {
            Timber.e(e, "Error ensuring files are persistent: ${e.message}")
        }
    }

    fun saveDraftIfNeeded() {
        val draftText = binding.etInputText.text.toString().trim()

        // Only save draft if there's content to save or files are attached
        if ((draftText.isEmpty() && selectedFiles.isEmpty()) || isPrivateModeEnabled) {
            return
        }

        lifecycleScope.launch {
            try {
                Timber.d("Saving draft - text: ${draftText.take(20)}, files: ${selectedFiles.size}")

                if (currentConversationId != null && !currentConversationId!!.startsWith("private_")) {
                    // Existing conversation - Get current conversation
                    val conversation = withContext(Dispatchers.IO) {
                        conversationsViewModel.getConversationById(currentConversationId!!)
                    }

                    if (conversation != null) {
                        // Update with draft
                        val hasDraftChanged = !conversation.hasDraft ||
                                conversation.draftText != draftText ||
                                selectedFiles.isNotEmpty()

                        // Only update if something changed
                        if (hasDraftChanged) {
                            // Ensure all files are persistent before saving
                            ensureFilesPersistent()
                            
                            // Serialize files using the helper method
                            val serializedFiles = fileHandler.serializeSelectedFiles(selectedFiles)

                            // Copy conversation to avoid modification issues
                            val updatedConversation = conversation.copy(
                                hasDraft = true,
                                draftText = draftText,
                                draftFiles = serializedFiles,
                                draftTimestamp = System.currentTimeMillis()
                            )

                            // Save to database - use update, not addConversation
                            withContext(Dispatchers.IO) {
                                conversationsViewModel.updateConversation(updatedConversation)
                            }

                            Timber.d("Updated draft for existing conversation: ${conversation.id}, hasDraft=true")
                        }
                    } else {
                        // Handle case where conversation ID exists but conversation not found
                        createNewConversationWithDraft(draftText)
                    }
                } else if (!isPrivateModeEnabled && (draftText.isNotEmpty() || selectedFiles.isNotEmpty())) {
                    // No existing conversation - create new one with draft
                    createNewConversationWithDraft(draftText)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving draft: ${e.message}")
            }
        }
    }    private fun createNewConversationWithDraft(draftText: String) {
        try {
            // Create title for draft - use first 30 chars of text or indicate attachments
            val draftTitle = when {
                draftText.isNotEmpty() -> {
                    if (draftText.length > 30) "${draftText.take(30)}..." else draftText
                }
                selectedFiles.isNotEmpty() -> {
                    "Draft with ${selectedFiles.size} attachment(s)"
                }
                else -> "Draft"
            }

            // Ensure all files are persistent before saving
            ensureFilesPersistent()
            
            // Serialize files using the helper method
            val serializedFiles = fileHandler.serializeSelectedFiles(selectedFiles)

            // Generate a unique timestamp-based ID
            val conversationId = System.currentTimeMillis()

            // Create new conversation with draft flag and content
            val conversation = Conversation(
                id = conversationId,
                title = draftTitle,
                preview = draftText.take(100),
                modelId = ModelManager.selectedModel.id,
                timestamp = System.currentTimeMillis(),
                lastMessage = "",
                lastModified = System.currentTimeMillis(),
                name = draftTitle,
                aiModel = ModelManager.selectedModel.displayName,
                hasDraft = true,  // Explicitly set hasDraft flag
                draftText = draftText,
                draftFiles = serializedFiles,
                draftTimestamp = System.currentTimeMillis()
            )

            // Save to database using IO dispatcher
            lifecycleScope.launch(Dispatchers.IO) {
                conversationsViewModel.addConversation(conversation)

                // Update current conversation ID on main thread
                withContext(Dispatchers.Main) {
                    currentConversationId = conversation.id.toString()
                }

                Timber.d("Created new draft conversation: ${conversation.id} with title: $draftTitle, hasDraft=true")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating draft conversation: ${e.message}")
        }
    }

    // Method to clear draft when sending a message
    /**
     * Enhanced method to clear draft when sending a message or explicitly deleting a draft
     * @param shouldDeleteIfEmpty If true, will delete the conversation if it has no messages
     */

    /**
     * Set up text change listener to detect draft changes
     * This should be called in onCreate or when initializing the UI
     */
    private fun setupDraftTextChangeListener() {
        // Add a text change listener to detect when draft is deleted
        binding.etInputText.addTextChangedListener(object : TextWatcher {
            private var lastText: String = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Save the current text to compare after change
                lastText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // No-op, handled in afterTextChanged
            }

            override fun afterTextChanged(s: Editable?) {
                val newText = s?.toString() ?: ""

                // Check if text was cleared completely (was non-empty, now empty)
                if (lastText.isNotEmpty() && newText.isEmpty()) {
                    // Text was cleared - check if we need to handle draft deletion
                    // Only check for deletion if no files are present
                    if (selectedFiles.isEmpty() && currentConversationId != null) {
                        lifecycleScope.launch {
                            val conversation = withContext(Dispatchers.IO) {
                                conversationsViewModel.getConversationById(currentConversationId!!)
                            }

                            // Only trigger draft deletion logic if this was a draft
                            if (conversation?.hasDraft == true) {
                                // Delay slightly to avoid triggering on normal editing
                                Handler(Looper.getMainLooper()).postDelayed({
                                    // Check again in case text was added back
                                    if (binding.etInputText.text.toString().isEmpty() && selectedFiles.isEmpty()) {
                                        handleDraftDeletion()
                                    }
                                }, 500)
                            }
                        }
                    }
                }

                // Update microphone/send button visibility based on text content
                toggleInputButtons(newText.isNotEmpty())
                
                // Save draft when text changes (with slight delay to avoid excessive calls)
                Handler(Looper.getMainLooper()).postDelayed({
                    saveDraftIfNeeded()
                }, 1000) // 1 second delay
            }
        })
    }


    private fun handleDraftDeletion() {
        if (currentConversationId == null || isPrivateModeEnabled) return

        // Check if input is empty and no files
        val isEmpty = binding.etInputText.text.toString().trim().isEmpty() && selectedFiles.isEmpty()

        if (isEmpty) {
            lifecycleScope.launch {
                try {
                    val conversation = withContext(Dispatchers.IO) {
                        conversationsViewModel.getConversationById(currentConversationId!!)
                    }

                    if (conversation != null && conversation.hasDraft) {
                        // Check if there are any messages
                        val messageCount = withContext(Dispatchers.IO) {
                            val messages = conversationsViewModel.getAllConversationMessages(currentConversationId!!)
                            messages.size
                        }

                        if (messageCount == 0) {
                            // No messages and draft cleared - offer to delete the conversation
                            withContext(Dispatchers.Main) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Empty Conversation")
                                    .setMessage("This conversation has no messages and the draft is empty. Would you like to delete it?")
                                    .setPositiveButton("Delete") { _, _ ->
                                        clearDraft(shouldDeleteIfEmpty = true)
                                    }
                                    .setNegativeButton("Keep") { _, _ ->
                                        clearDraft(shouldDeleteIfEmpty = false)
                                    }
                                    .show()
                            }
                        } else {
                            // Has messages, just clear the draft
                            clearDraft(shouldDeleteIfEmpty = false)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error handling draft deletion: ${e.message}")
                }
            }
        }
    }


    /**
     * Clear draft from the current conversation
     */
    fun clearDraft(shouldDeleteIfEmpty: Boolean = false) {
        lifecycleScope.launch {
            try {
                if (currentConversationId != null) {
                    val conversation = conversationsViewModel.getConversationById(currentConversationId!!)
                    if (conversation != null) {
                        if (shouldDeleteIfEmpty) {
                            // Check if conversation has any messages
                            val messages = withContext(Dispatchers.IO) {
                                conversationsViewModel.getAllConversationMessages(currentConversationId!!)
                            }
                            if (messages.isEmpty()) {
                                // Delete entire conversation
                                conversationsViewModel.deleteConversation(conversation)
                                currentConversationId = null
                                Timber.d("Empty conversation deleted")
                                return@launch
                            }
                        }
                        
                        // Just clear the draft but keep the conversation
                        val updatedConversation = conversation.copy(
                            hasDraft = false,
                            draftText = "",
                            draftFiles = "",
                            draftTimestamp = 0
                        )
                        
                        conversationsViewModel.updateConversation(updatedConversation)
                        Timber.d("Draft cleared from conversation ${currentConversationId}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error clearing draft: ${e.message}")
            }
        }
    }

    /**
     * Handle new intents that are received when the activity is already running
     * This method safely handles various intent types and ensures properties are initialized
     * before accessing them
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Timber.d("onNewIntent received: ${intent.action}")
        setIntent(intent)

        // Prevent duplicate processing of the same intent
        if (isIntentProcessed && intent.action == processingIntentAction) {
            Timber.d("Intent already processed, ignoring duplicate")
            return
        }

        try {
            if (::binding.isInitialized && ::vibrator.isInitialized && ::chatAdapter.isInitialized
                && ::messageManager.isInitialized && ::conversationsViewModel.isInitialized) {

                handleIntent(intent)
                isIntentProcessed = true
                processingIntentAction = intent.action
            } else {
                Timber.d("Activity not fully initialized, deferring intent processing")
                deferredIntent = intent
                isIntentProcessed = false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling new intent: ${e.message}")
            deferredIntent = intent
            isIntentProcessed = false
        }
    }

    /**
     * CRITICAL FIX: Restore ongoing streaming sessions without disconnecting from them
     * This ensures real-time streaming continues seamlessly in the same activity.
     */
    private fun restoreOngoingStreamingSessions(conversationId: String, messages: List<ChatMessage>) {
        lifecycleScope.launch {
            try {
                // Find any messages that are still generating
                val generatingMessages = messages.filter { !it.isUser && it.isGenerating }
                
                if (generatingMessages.isNotEmpty()) {
                    Timber.d("🔄 Found ${generatingMessages.size} generating messages, reconnecting to streaming")
                    
                    withContext(Dispatchers.Main) {
                        // CRITICAL: Don't cancel ongoing API job, reconnect to it instead
                        if (currentApiJob?.isActive == true) {
                            Timber.d("✅ Ongoing API job found, keeping connection alive")
                        } else {
                            Timber.d("🔌 No active API job, connecting to background service")
                            bindToBackgroundService()
                            
                            // CRITICAL FIX: Don't schedule another check - we'll handle reconnection here
                            // checkAndConnectToBackgroundGeneration will be called only from onCreate
                        }
                        
                        // Start streaming mode in adapter
                        if (!chatAdapter.isStreamingActive) {
                            chatAdapter.startStreamingMode()
                            Timber.d("🎬 Started streaming mode for generating messages")
                        }
                        
                        // CRITICAL FIX: Set generating state and show proper UI
                        setGeneratingState(true)
                        
                        // CRITICAL FIX: Restore full UI state for generating messages
                        restoreGeneratingUIState(generatingMessages)
                        
                        // For each generating message, ensure real-time updates
                        generatingMessages.forEach { message ->
                            Timber.d("🔍 Processing generating message: ${message.id}, content length: ${message.message.length}")
                            
                            // Register broadcast receiver for this message if not already registered
                            registerBackgroundGenerationReceiver()
                            
                            // CRITICAL FIX: Request current progress from background service
                            // This will be handled by the service connection callback
                            Timber.d("📡 Will request progress for message: ${message.id} when service connects")
                        }
                        
                        Timber.d("✅ Reconnected to ${generatingMessages.size} streaming sessions")
                    }
                } else {
                    Timber.d("ℹ️ No active streaming sessions found in conversation: $conversationId")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error restoring streaming sessions: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::audioResponseHandler.isInitialized) {
            audioResponseHandler.stopAudio()
        }
        
        // CRITICAL: Unregister broadcast receivers
        unregisterBackgroundGenerationReceiver()
        
        // Transfer any ongoing AI generation to background service
        transferGenerationToBackground()
        
        // Save all messages
        messageManager.saveAllMessages()

        getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
            putLong("last_session_time", System.currentTimeMillis())
        }

        saveAiMessageHistory()

        // Save draft if there's text or files
        saveDraftIfNeeded()

        // CRITICAL FIX: Don't reset generation state during pause - background generation should continue
        // The UI will reconnect to background generation when resumed
        // chatAdapter.setGenerating(false) - REMOVED to prevent response refresh
        
        if (isRecording) {
            cancelVoiceRecording()
        }
    }

    /**
     * Initialize critical UI components first for faster perceived performance
     */
    private fun initializeCriticalUIComponents() {
        try {
            // Initialize ViewModel first - needed for chat functionality
            conversationsViewModel = ViewModelProvider(this)[ConversationsViewModel::class.java]
            
            // Initialize system services
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            sharedPrefs = getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            
            // Initialize conversation token manager EARLY - before components that depend on it
            conversationTokenManager = ConversationTokenManager()
            
            // Initialize chat title view
            chatTitleView = binding.tvChatTitle
            
            Timber.d("Critical UI components initialized")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing critical UI components: ${e.message}")
        }
    }

    /**
     * Initialize core chat components asynchronously
     */
    private fun initializeChatComponentsAsync() {
        lifecycleScope.launch {
            try {
                // Initialize CHAT-SPECIFIC services first
                initializeChatServices()
                
                // Initialize core chat components
                initializeChatComponents()
                
                // CRITICAL: Setup chat adapter BEFORE UI components that depend on it
                setupChatAdapter()
                
                // Initialize the message manager with token manager
                messageManager = MessageManager(
                    viewModel = conversationsViewModel,
                    adapter = chatAdapter,
                    lifecycleScope = lifecycleScope,
                    chatAdapter = chatAdapter,
                    tokenManager = conversationTokenManager
                )
                
                // Setup chat adapter callbacks
                setupChatAdapterCallbacks()
                
                // Initialize the AI chat service AFTER the adapter is ready
                aiChatService = AiChatService(
                    context = this@MainActivity,
                    conversationsViewModel = conversationsViewModel,
                    chatAdapter = chatAdapter,
                    coroutineScope = lifecycleScope,
                    callbacks = createAiChatCallbacks(),
                    messageManager = messageManager,
                    conversationTokenManager = conversationTokenManager
                )
                
                Timber.d("Core chat components initialized")
                
                // Update UI components that depend on initialized managers
                withContext(Dispatchers.Main) {
                    updateFreeMessagesText() // Update credits UI now that creditManager is ready
                }
            } catch (e: Exception) {
                Timber.e(e, "Error initializing chat components: ${e.message}")
            }
        }
    }

    /**
     * Setup basic UI immediately for perceived performance
     */
    private fun setupBasicUI() {
        try {
            // Setup basic UI components
            setupUIComponents()
            
            // Initialize basic button handling
            initializeButtons()
            
            // Setup basic listeners
            setupListeners()
            
            Timber.d("Basic UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up basic UI: ${e.message}")
        }
    }

    /**
     * Initialize non-critical components in background
     */
    private fun initializeNonCriticalComponentsAsync() {
        lifecycleScope.launch {
            try {
                // Setup UI components after core components are initialized
                setupUIComponents()
                setupScrollArrows()
                
                // Initialize remaining services
                initializeButtons()
                initializeEnhancedButtonHandling()
                initializeTextExpansion()
                initializeAudio()
                
                // Setup listeners and input handling
                setupListeners()
                setupDraftTextChangeListener()
                
                // Load conversation data
                loadInitialData()
                
                // Initialize token counter for tracking token usage
                setupTokenMonitoring()
                
                // Handle ads and consent - ONLY FOR NON-SUBSCRIBERS
                if (!PrefsManager.isSubscribed(this@MainActivity)) {
                    delay(2000) // Delay to improve chat startup time
                    withContext(Dispatchers.Main) {
                        handleConsentAndAds()
                    }
                }
                
                Timber.d("Non-critical components initialized")
            } catch (e: Exception) {
                Timber.e(e, "Error initializing non-critical components: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        try {
            Timber.d("MainActivity onResume called")
            
            // CRITICAL: Register broadcast receivers for background generation updates
            registerBackgroundGenerationReceiver()
            
            // Check if activity is in a valid state
            if (isFinishing || isDestroyed) {
                Timber.w("Activity is finishing or destroyed, skipping onResume")
                return
            }
            
            // Only proceed if binding is initialized
            if (!::binding.isInitialized) {
                Timber.w("Binding not initialized in onResume, this might indicate a problem")
                return
            }
            
            if (currentConversationId != null && ::conversationTokenManager.isInitialized) {
                // Force reload token state for current conversation
                conversationTokenManager.setConversationId(currentConversationId!!)

                // Force update token counter UI
                updateTokenCounterUI()

                // Load draft if necessary but ONLY if we have no existing conversation content
                // AND we're not returning from text selection activity
                val hasExistingMessages = if (::chatAdapter.isInitialized) {
                    chatAdapter.currentList.isNotEmpty()
                } else {
                    false
                }

            // Check if we have a force model intent that should prevent draft loading
            val hasForcedModelIntent = intent?.action == "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
            
            if (!hasExistingMessages && !isReturningFromTextSelection && !isReturningFromFileSelection && !isDraftContentLoaded && !hasForcedModelIntent) {
                Timber.d("Attempting to load draft in onResume for empty conversation")
                loadDraft(currentConversationId!!)
                isDraftContentLoaded = true
            } else {
                Timber.d("Skipping draft load in onResume - conversation has content or returning from selection or draft already loaded or has forced model intent")
            }

            // Reset the flags after handling them
            isReturningFromTextSelection = false
            isReturningFromFileSelection = false

            Timber.d("Restored token state in onResume for conversation: $currentConversationId")
        } else {
            // Only check for drafts if we're NOT already in a conversation
            // AND we're not returning from any selection
            // AND we don't have a forced model intent
            val hasForcedModelIntent = intent?.action == "com.cyberflux.qwinai.ACTION_FORCE_MODEL"
            
            if (!isReturningFromTextSelection && !isReturningFromFileSelection && !hasForcedModelIntent) {
                checkForDraftsOnStartup()
            } else {
                isReturningFromTextSelection = false
                isReturningFromFileSelection = false
            }
        }

        // IMPORTANT: Reset intent processing for genuinely new intents
        val currentIntent = intent
        if (currentIntent?.action != processingIntentAction) {
            Timber.d("New intent detected in onResume, resetting processing flags")
            isIntentProcessed = false
            processingIntentAction = null

            // If we have a new intent, process it
            if (currentIntent != null) {
                val handled = handleIntent(currentIntent)
                if (handled) {
                    isIntentProcessed = true
                }
            }
        }


        // ADDED: Check if we need to reset model selection after returning from image generation
        val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
        val shouldResetModel = appSettings.getBoolean("reset_to_default_model", false)

        if (shouldResetModel) {
            // Reset flag
            appSettings.edit {
                putBoolean("reset_to_default_model", false)
                apply()
            }

            // Reset to default model
            loadDefaultModel()

            // CRITICAL FIX: Clear ModelValidator cache and update controls
            ModelValidator.clearCache()
            updateControlsVisibility(ModelManager.selectedModel.id)

            Timber.d("Model selection reset to default after returning from image generation")
        }

        messageManager.fixLoadingStates()

        // Always verify subscription status on resume
        updateUIBasedOnSubscriptionStatus()

        // Save any pending messages to database
        if (currentConversationId != null) {
            messageManager.saveAllMessages()
        }

        // Check if settings have changed while we were away
        checkForSettingsChanges()
        checkAndResetModelSelection()

        // IMPORTANT: Reset generation state to ensure UI consistency
        // This should match what the adapter thinks
        isGenerating = chatAdapter.isGenerating

        // CRITICAL FIX: If generation state is confused, force reset it
        if (binding.typingIndicator.isGone && isGenerating) {
            isGenerating = false
            chatAdapter.setGenerating(false)
        }

        // Force update button states based on generation state and text input
        val inputText = binding.etInputText.text.toString().trim()

        // Use setGeneratingState instead of directly manipulating visibility
        // This ensures proper button state and animation
        if (isGenerating) {
            // Make sure we're in generating state with proper UI
            setGeneratingState(true)
        } else {
            // Reset generation state and update button UI
            setGeneratingState(false)

            // Additional check to ensure button states match input text
            if (inputText.isEmpty()) {
                binding.btnMicrophone.visibility = View.VISIBLE
                binding.btnSubmitText.visibility = View.GONE
            } else {
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                binding.btnSubmitText.contentDescription = "Send message"
            }
        }

        // If more than 10 minutes have passed, consider it a new session
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastSessionTime = prefs.getLong("last_session_time", 0)
        val currentTime = System.currentTimeMillis()
        val isNewSession = (currentTime - lastSessionTime) > 10 * 60 * 1000

        if (isNewSession) {
            // Reset private mode
            isPrivateModeEnabled = false
            updatePrivateModeUI(false)

            // Clear any private messages from previous sessions
            conversationsViewModel.clearPrivateMessages()

            // Reset intent processing for new session
            isIntentProcessed = false
            processingIntentAction = null

            Timber.d("New session detected, private mode reset to OFF, intent processing reset")
        }

        // Update the last session time
        prefs.edit {
            putLong("last_session_time", currentTime)
        }

        // Check for ongoing app updates
        try {
            val updateManager = MyApp.getUpdateManager()
            updateManager?.checkOngoingUpdates(this)
        } catch (e: Exception) {
            Timber.e(e, "Error checking ongoing updates: ${e.message}")
        }

        // Refresh the conversation title
        if (currentConversationId != null) {
            lifecycleScope.launch {
                try {
                    val conversation = conversationsViewModel.getConversationById(currentConversationId.toString())
                    if (conversation != null) {
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            chatTitleView.text = conversation.title
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error refreshing conversation title")
                }
            }
        }

        // Load message history, reset typing effect, etc.
        loadAiMessageHistory()
        isReloadingMessage = false
        
        // CRITICAL FIX: Clean up any existing messages with interruption text after loading
        Handler(Looper.getMainLooper()).postDelayed({
            cleanupInterruptionMessages()
        }, 1000)

        // IMPORTANT: Only reset adapter generating state if we're sure we're not generating
        // This prevents the adapter and UI from getting out of sync
        if (!isGenerating) {
            chatAdapter.setGenerating(false)
        }

        // Debug state - Updated for simplified branching system
        Handler(Looper.getMainLooper()).postDelayed({
            // Log system state instead of branches
            Timber.d("Simplified branching system ready, current conversation: $currentConversationId")
        }, 500)

        // Restore buttons state with a slight delay to ensure UI is ready
        Handler(Looper.getMainLooper()).postDelayed({
            restoreButtonsOnError()

            // IMPORTANT: Force check generation button state after restoring buttons
            // This ensures the stop button appears if we're in a generating state
            forceUpdateButtonState()

            // If we're generating, start monitoring button state to ensure it stays visible
            if (isGenerating) {
                monitorGenerationButtonState()
            }
        }, 300)

        // Debug adapter state
        Handler(Looper.getMainLooper()).postDelayed({
            debugChatAdapterState()
        }, 500)

        // Restore photo path if needed
        restoreCurrentPhotoPath()

        // Preload ads if needed - after checking subscription status
        if (!PrefsManager.isSubscribed(this)) {
            preloadAdsIfNeeded()
        }

        if (::scrollArrowsHelper.isInitialized) {
            Handler(Looper.getMainLooper()).postDelayed({
                scrollArrowsHelper.updateButtonsState()
            }, 300)
        }

        getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
            putLong("last_session_time", System.currentTimeMillis())
        }
        
        } catch (e: Exception) {
            Timber.e(e, "Error in MainActivity onResume: ${e.message}")
            // Don't crash - just log and continue
        }
    }
    override fun onDestroy() {
        try {
            Timber.d("MainActivity onDestroy called")
            
            // Clean up scroll arrows
            if (::scrollArrowsHelper.isInitialized) {
                scrollArrowsHelper.cleanup()
            }
            
            // Cancel animations to prevent leaks
            stopPulsingAnimation()
            
            // Clean up handlers
            uiHandler.removeCallbacksAndMessages(null)
            recordingHandler.removeCallbacksAndMessages(null)
            
            // Clean up dialogs
            currentEditDialog?.dismiss()
            currentEditDialog = null
            loadingDialog?.dismiss()
            loadingDialog = null
            progressDialog?.dismiss()
            progressDialog = null
            
            // Cancel API jobs
            currentApiJob?.cancel()
            currentApiJob = null
            buttonMonitoringJob?.cancel()
            buttonMonitoringJob = null
            
            // Clean up speech recognizer
            if (::speechRecognizer.isInitialized) {
                try {
                    speechRecognizer.destroy()
                } catch (e: Exception) {
                    Timber.e(e, "Error destroying speech recognizer: ${e.message}")
                }
            }
            
            // Stop animations
            stopWaveformAnimation()
            
            // Clean up audio handler
            if (::audioResponseHandler.isInitialized) {
                try {
                    audioResponseHandler.stopAudio()
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up audio handler: ${e.message}")
                }
            }
            
            // Clean up adapters
            try {
                if (::chatAdapter.isInitialized) {
                    // Clear adapter data if method exists
                    chatAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up adapters: ${e.message}")
            }
            
            // Clean up managers
            try {
                if (::messageManager.isInitialized) {
                    messageManager.saveAllMessages()
                }
                if (::conversationTokenManager.isInitialized) {
                    // conversationTokenManager doesn't have a cleanup method
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up managers: ${e.message}")
            }
            
            // Clean up update manager
            try {
                val updateManager = MyApp.getUpdateManager()
                updateManager?.cleanup()
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up update manager: ${e.message}")
            }
            
            // Clean up services
            try {
                // Note: Services may not have cleanup methods, so we just nullify references
                if (::ocrService.isInitialized) {
                    // ocrService doesn't have a cleanup method
                }
                if (::aiChatService.isInitialized) {
                    // aiChatService doesn't have a cleanup method
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up services: ${e.message}")
            }
            
            // Clean up credit manager
            try {
                if (::creditManager.isInitialized) {
                    // creditManager doesn't have a cleanup method
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up credit manager: ${e.message}")
            }
            
            // Clean up consent and ad managers
            try {
                if (::consentManager.isInitialized) {
                    // consentManager doesn't have a cleanup method
                }
                if (::adManager.isInitialized) {
                    // adManager doesn't have a cleanup method
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up ad managers: ${e.message}")
            }
            
            // Clear collections to prevent memory leaks
            aiResponseGroups.clear()
            conversations.clear()
            aiMessageHistory.clear()
            currentMessageIndex.clear()
            conversationMetadata.clear()
            
            // Clear wave bars
            waveBars.clear()
            recordingAnimators.clear()
            
            // Reset state variables to prevent stale references
            currentConversationId = null
            selectedMessage = null
            selectedUserMessage = null
            currentAiGroupId = null
            deferredIntent = null
            
            Timber.d("MainActivity onDestroy completed successfully")
            
            // CRITICAL FIX: Only clean up background service if activity is actually finishing
            // Don't stop background generation if just changing conversations or rotating screen
            if (isFinishing || isDestroyed) {
                Timber.d("🛑 Activity is finishing/destroyed, cleaning up background service")
                
                // Clean up UI-related streaming state but keep background generation running
                cleanupUIStreamingState()
                
                unregisterBackgroundGenerationReceiver()
                unbindFromBackgroundService()
                
                // IMPORTANT: Don't stop background service - let it continue generating
                // The service will stop itself when generation completes or times out
                Timber.d("📍 Allowing background generation to continue after activity exit")
            } else {
                Timber.d("📍 Activity destroyed but not finishing, keeping background service alive")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error in MainActivity onDestroy: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Update scroll arrows after orientation change
        if (::scrollArrowsHelper.isInitialized) {
            Handler(Looper.getMainLooper()).postDelayed({
                scrollArrowsHelper.updateButtonsState()
            }, 300)
        }
    }

    private fun checkAndResetModelSelection() {
        try {
            val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val shouldReset = prefs.getBoolean("reset_to_default_model", false)

            if (shouldReset) {
                // Clear the flag
                prefs.edit().putBoolean("reset_to_default_model", false).apply()

                // Get default model ID
                val defaultModelId = PrefsManager.getDefaultModelId(this)

                // Reset spinner to default model using ModelManager
                val modelIndex = ModelManager.models.indexOfFirst { it.id == defaultModelId }
                if (modelIndex != -1) {
                    binding.spinnerModels.setSelection(modelIndex)
                    ModelManager.selectedModel = ModelManager.models[modelIndex]
                }

                Timber.d("Reset AI model spinner to default model")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error resetting model spinner: ${e.message}")
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            TEXT_EXPANSION_REQUEST_CODE -> {
                isReturningFromTextSelection = true
                handleMainTextExpansionResult(resultCode, data)
            }
            EDIT_TEXT_EXPANSION_REQUEST_CODE -> {
                isReturningFromTextSelection = true
                handleEditTextExpansionResult(resultCode, data)
            }
            TEXT_VIEW_REQUEST_CODE -> {
                isReturningFromTextSelection = true
                // View-only mode - no changes expected, just log
                Timber.d("Returned from view-only text selection")
            }
            // Other existing cases
            HuaweiIapProvider.REQ_CODE_BUY -> {
                if (HuaweiIapProvider.processPurchaseResult(requestCode, data, this)) {
                    updateUIBasedOnSubscriptionStatus()
                }
            }
            SETTINGS_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    checkForSettingsChanges()
                }
            }
            500 -> { // UPDATE_REQUEST_CODE
                try {
                    val updateManager = MyApp.getUpdateManager()
                    updateManager?.handleUpdateResult(requestCode, resultCode)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling update result: ${e.message}")
                }
            }
        }
    }
    private fun handleMainTextExpansionResult(resultCode: Int, data: Intent?) {
        try {
            Timber.d("Handling main text expansion result: resultCode=$resultCode")

            if (resultCode == Activity.RESULT_OK && data != null) {
                val editedText = data.getStringExtra("EDITED_TEXT")
                val textChanged = data.getBooleanExtra("TEXT_CHANGED", false)
                val originalText = data.getStringExtra("ORIGINAL_TEXT")
                val source = data.getStringExtra("SOURCE") ?: "input"

                Timber.d("Text expansion result: changed=$textChanged, " +
                        "original=${originalText?.length ?: 0} chars, " +
                        "edited=${editedText?.length ?: 0} chars, " +
                        "source=$source")

                // Only proceed if this was from the input source
                if (source == "input" && textChanged) {
                    if (!editedText.isNullOrEmpty()) {
                        // Update the input text
                        binding.etInputText.setText(editedText)
                        binding.etInputText.setSelection(editedText.length)

                        // Update button visibility
                        updateExpandButtonVisibility()

                        // Show feedback
                        Toast.makeText(this, "Text updated", Toast.LENGTH_SHORT).show()
                        provideHapticFeedback(30)

                        Timber.d("Successfully updated main input text")
                    } else {
                        // User cleared the text
                        binding.etInputText.setText("")
                        updateExpandButtonVisibility()
                        Toast.makeText(this, "Text cleared", Toast.LENGTH_SHORT).show()

                        Timber.d("Main input text cleared")
                    }
                } else {
                    Timber.d("No changes made to main input text or wrong source")
                }
            } else {
                Timber.d("Main text expansion cancelled or failed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling main text expansion result: ${e.message}")
            Toast.makeText(this, "Error updating text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Handle result from edit dialog text expansion
     */
    private fun handleEditTextExpansionResult(resultCode: Int, data: Intent?) {
        try {
            val originalMessage = editDialogOriginalMessage
            if (originalMessage == null) {
                Timber.e("No original message stored for edit dialog")
                return
            }

            Timber.d("Handling edit text expansion result: resultCode=$resultCode")

            if (resultCode == Activity.RESULT_OK && data != null) {
                val editedText = data.getStringExtra("EDITED_TEXT") ?: ""
                val textChanged = data.getBooleanExtra("TEXT_CHANGED", false)
                val originalText = data.getStringExtra("ORIGINAL_TEXT")
                val source = data.getStringExtra("SOURCE") ?: "dialog"

                Timber.d("Edit text expansion result: changed=$textChanged, " +
                        "original=${originalText?.length ?: 0} chars, " +
                        "edited=${editedText.length} chars, " +
                        "source=$source")

                // Only proceed if this was from the dialog source
                if (source == "dialog" && textChanged) {
                    // Re-show the edit dialog with the updated text
                    showEditMessageDialogWithText(originalMessage, editedText)

                    if (editedText.isNotEmpty()) {
                        Toast.makeText(this, "Text updated in editor", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Text cleared", Toast.LENGTH_SHORT).show()
                    }

                    Timber.d("Re-showing edit dialog with updated text")
                } else if (!textChanged) {
                    // No changes - just re-show the original dialog
                    showEditMessageDialog(originalMessage)
                    Timber.d("No changes made, re-showing original edit dialog")
                } else {
                    // Wrong source - re-show original dialog
                    showEditMessageDialog(originalMessage)
                    Timber.d("Wrong source, re-showing original edit dialog")
                }
            } else {
                // User cancelled - re-show the original dialog
                showEditMessageDialog(originalMessage)
                Timber.d("Edit text expansion cancelled, re-showing original dialog")
            }

            // Clear the stored message reference after handling the result
            editDialogOriginalMessage = null
        } catch (e: Exception) {
            Timber.e(e, "Error handling edit text expansion result: ${e.message}")
            Toast.makeText(this, "Error updating edited text: ${e.message}", Toast.LENGTH_SHORT).show()

            // Try to show original dialog as fallback
            editDialogOriginalMessage?.let { showEditMessageDialog(it) }
            editDialogOriginalMessage = null
        }
    }    private fun showEditMessageDialogWithText(message: ChatMessage, predefinedText: String) {
        try {
            // Create a temporary message with the new text for the dialog
            val tempMessage = message.copy(message = predefinedText)
            showEditMessageDialog(tempMessage)

            // But keep the original message reference for actual editing
            editDialogOriginalMessage = message
        } catch (e: Exception) {
            Timber.e(e, "Error showing edit dialog with text: ${e.message}")
            showEditMessageDialog(message)
        }
    }    private fun updateEditExpandButtonVisibility(editText: TextInputEditText, expandButton: ImageButton) {
        try {
            val text = editText.text.toString()
            val lineCount = editText.lineCount
            val maxLines = 12

            val hasMaxLines = lineCount >= maxLines
            val isVeryLong = text.length > 800
            val hasManyLineBreaks = text.count { it == '\n' } >= 10

            val shouldShow = hasMaxLines || isVeryLong || hasManyLineBreaks

            expandButton.visibility = if (shouldShow) View.VISIBLE else View.GONE

            if (shouldShow) {
                expandButton.background = ContextCompat.getDrawable(this, R.drawable.circle_button_active)
                expandButton.setColorFilter(ContextCompat.getColor(this, R.color.white))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating edit expand button visibility: ${e.message}")
        }
    }

    /**
     * Setup button listeners for edit dialog
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun handleEditMessageTokenCheck(
        dialog: Dialog,
        editText: TextInputEditText,
        message: ChatMessage,
        newText: String,
        reasoningEnabled: Boolean,
        deepSearchEnabled: Boolean
    ) {
        try {
            val modelId = ModelManager.selectedModel.id
            val isSubscribed = PrefsManager.isSubscribed(this)

            // Temporarily remove original message tokens
            conversationTokenManager.removeMessage(message.id)

            // Check if new message would exceed limits
            val (wouldExceed, available, _) = conversationTokenManager.wouldExceedLimit(newText, modelId, isSubscribed)

            // Add original message back
            conversationTokenManager.addMessage(message)

            if (wouldExceed) {
                val inputTokens = TokenValidator.estimateTokenCount(newText)
                AlertDialog.Builder(this)
                    .setTitle("Edited Message Too Long")
                    .setMessage("The edited message has $inputTokens tokens, but only $available tokens are available.")
                    .setPositiveButton("Truncate & Send") { _, _ ->
                        val truncatedText = TokenValidator.truncateToTokenCount(newText, available)
                        proceedWithEditedMessage(message, truncatedText, reasoningEnabled, deepSearchEnabled)

                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(editText.windowToken, 0)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Edit Again", null)
                    .setNeutralButton("Cancel") { _, _ -> dialog.dismiss() }
                    .show()
            } else {
                proceedWithEditedMessage(message, newText, reasoningEnabled, deepSearchEnabled)

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking tokens for edited message: ${e.message}")

            // Proceed anyway
            proceedWithEditedMessage(message, newText, reasoningEnabled, deepSearchEnabled)

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
            dialog.dismiss()
        }
    }

    private fun setupUIComponents() {
        // Original components
        setupStatusBar()

        // Initialize UI components BEFORE setting up model selector
        reasoningControlsLayout = findViewById(R.id.reasoningControlsLayout)

        // Change these from LinearLayout to ImageButton
        btnReasoning = findViewById(R.id.btnReasoning)
        btnDeepSearch = findViewById(R.id.btnDeepSearch)
        // Hide manual web search button - AI models handle tools automatically
        btnDeepSearch.visibility = View.GONE

        // Initialize voice recording
        initializeVoiceRecording()

        // Now set up model selector (which calls updateControlsVisibility)
        setupModelSelector()
        updateFreeMessagesText()

        // Make reasoning controls visible by default
        reasoningControlsLayout.visibility = View.VISIBLE

        // Update button states based on loaded settings
        updateReasoningButtonState(isReasoningEnabled)
        updateDeepSearchButtonState(isDeepSearchEnabled)
    }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setupListeners() {
        // Existing reasoning and deep search listeners
        btnReasoning.setOnClickListener {
            provideHapticFeedback(50)
            toggleReasoningLevel()
        }

        btnDeepSearch.setOnClickListener {
            provideHapticFeedback(50)
            toggleDeepSearch()
        }

        // CHANGED: Update btnConversationsList to be a back button to StartActivity
        binding.btnConversationsList.setOnClickListener {
            provideHapticFeedback(50)
            // Navigate back to StartActivity
            finish()
        }


        binding.btnNewConversation.setOnClickListener {
            startNewConversation()
        }

        binding.btnAttach.setOnClickListener {
            showAttachmentMenu()
        }

        binding.btnSubmitText.setOnClickListener {
            if (isGenerating) {
                stopGeneration()
            } else {
                sendMessage()
            }
        }

        binding.creditsButton.setOnClickListener {
            showGetCreditsMenu()
        }
    }
    /**
     * Initialize ads safely
     */
    private fun initializeAds() {
        // Skip if already handled or user is subscribed
        if (PrefsManager.isSubscribed(this)) {
            Timber.d("Ads already initialized or user subscribed, skipping")
            return
        }

        try {
            // AdManager initializes automatically
            Timber.d("Ads initialized for free user")

            // Log the platform we're using for ads
            val platformType = if (MyApp.isHuaweiDevice()) "Huawei" else "Google"
            Timber.d("Using $platformType ad mediation")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ads: ${e.message}")
        }
    }
    /**
     * Preload ads if needed
     */
    private fun preloadAdsIfNeeded() {
        // AdManager handles ad loading automatically
    }
    /**
     * Setup status bar appearance
     */
    private fun setupStatusBar() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setupChatAdapter() {
        chatAdapter = ChatAdapter(
            onCopy = { text -> copyToClipboard(text) },
            onReload = { message ->
                // Only allow reload if we're not already generating
                if (!isGenerating) {
                    reloadAiMessage(message)
                } else {
                    Toast.makeText(
                        this,
                        "Please wait for current generation to complete or stop it",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onNavigate = { message, direction ->
                navigateMessageVersion(message, direction)
            },
            onLoadMore = { loadMoreMessages() },
            onAudioPlay = { message -> handleAudioPlayRequest(message) }, // Add this line
            currentModelId = ModelManager.selectedModel.id
        )

        conversationAdapter = ConversationAdapter(
            onConversationClick = { conversation ->
                openConversation(conversation.id, conversation.aiModel)
            },
            onConversationLongClick = { view, conversation ->
                showConversationMenu(view, conversation)
            },
        )

        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                        loadMoreMessages()
                    }
                }
            })
        }

        // Note: MessageCompletionCallback is set up in setupChatAdapterCallbacks()

        Timber.d("Chat adapters initialized with all required dependencies")
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setupModelSelector() {
        // Clear ModelValidator cache when setting up models
        ModelValidator.clearCache()
        ModelValidator.init() // Initialize validators explicitly

        // Apply styling directly here - no styles from XML
        binding.spinnerModels.apply {
            // Apply a clean, simple background with proper styling
            setBackgroundResource(R.drawable.spinner_background)

            // Set dimensions
            layoutParams = layoutParams.apply {
                width = (resources.displayMetrics.widthPixels * 0.65).toInt() // 65% of screen width
            }

            // Configure dropdown behavior
            dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
            val offsetInDp = 2
            val offsetInPixels = (offsetInDp * resources.displayMetrics.density).toInt()
            dropDownVerticalOffset = offsetInPixels
            setPopupBackgroundResource(R.drawable.spinner_dropdown_background)
        }

        // Use simple adapter instead of complicated one
        val adapter = ModelSpinnerAdapter(
            context = this,
            models = ModelManager.models,
            isTranslationMode = { isTranslationModeActive }
        )

        binding.spinnerModels.adapter = adapter

        // Load default model as before
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val defaultModelId = sharedPrefs.getString("default_ai_model_id", ModelManager.DEFAULT_MODEL_ID)
            ?: ModelManager.DEFAULT_MODEL_ID

        // Find the index of the default model and set it
        val currentModelIndex = ModelManager.models.indexOfFirst { it.id == defaultModelId }
        if (currentModelIndex != -1) {
            // Update ModelManager selection first
            ModelManager.selectedModel = ModelManager.models[currentModelIndex]
            
            // Update token manager with new model ID
            if (::conversationTokenManager.isInitialized) {
                conversationTokenManager.setModelId(ModelManager.selectedModel.id)
            }

            // Update adapter selection
            adapter.setSelectedPosition(currentModelIndex)

            // Set spinner selection
            binding.spinnerModels.setSelection(currentModelIndex)

            // Update the model position in response preferences
            responsePreferences.modelPosition = currentModelIndex

            // Record usage for the default model on app start
            ModelUsageTracker.recordModelUsage(this, defaultModelId)
        }

        // Variable to track current selection
        var currentSelection = currentModelIndex

        // Disable the default spinner dropdown behavior and implement custom dialog
        binding.spinnerModels.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                // Get the current selection before showing dialog
                val currentPosition = binding.spinnerModels.selectedItemPosition

                // Load the latest preferences to ensure they're current
                responsePreferences = loadResponsePreferences()

                // Always update the model position to match the spinner
                responsePreferences.modelPosition = currentPosition

                // Show custom dialog with loaded preferences and translation mode support
                val customDialog = CustomSpinnerDialog(
                    this,
                    ModelManager.models,
                    currentPosition,
                    isTranslationModeFunc = { isTranslationModeActive } // Updated parameter name
                ) { preferences ->
                    val selectedModel = ModelManager.models[preferences.modelPosition]

                    // Check if model is disabled in translation mode
                    if (isTranslationModeActive && !TranslationUtils.supportsTranslation(selectedModel.id)) {
                        // Simply ignore the selection - model is already visually disabled
                        return@CustomSpinnerDialog
                    }

                    // Update our tracked preferences with the returned ones
                    responsePreferences = preferences

                    // Save the preferences to SharedPreferences
                    saveResponsePreferences(preferences)

                    Timber.d("Model selected: ${selectedModel.displayName} (${selectedModel.id})")
                    Timber.d("Response preferences: length=${preferences.length.displayName}, tone=${preferences.tone.displayName}")

                    // Update our tracked selection for the spinner
                    currentSelection = preferences.modelPosition

                    // Update the adapter's selected position
                    adapter.setSelectedPosition(preferences.modelPosition)

                    // Force the spinner to update its display
                    binding.spinnerModels.setSelection(preferences.modelPosition)

                    // Record this model usage for tracking recent models
                    ModelUsageTracker.recordModelUsage(this, selectedModel.id)

                    // Check if this is a dedicated image generator model
                    if (ModelValidator.isImageGenerator(selectedModel.id) &&
                        !ModelValidator.supportsImageUpload(selectedModel.id)) {
                        // This is an image-only model like DALL-E 2
                        ModelManager.selectedModel = selectedModel
                        
                        // Update token manager with new model ID
                        if (::conversationTokenManager.isInitialized) {
                            conversationTokenManager.setModelId(selectedModel.id)
                        }

                        // Apply color for visual feedback before launching
                        applyModelColorToUI(selectedModel.id)

                        // Only launch if this isn't the first selection during app initialization
                        if (!isFirstModelSelection) {
                            // Set flag to reset model selection when returning
                            val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
                            appSettings.edit {
                                putBoolean("reset_to_default_model", true)
                                apply()
                            }

                            // Launch the image generation activity
                            val intent = Intent(this@MainActivity, ImageGenerationActivity::class.java)
                            startActivity(intent)
                        } else {
                            isFirstModelSelection = false
                        }
                        return@CustomSpinnerDialog
                    }

                    if (isFirstModelSelection) {
                        isFirstModelSelection = false
                        // Apply the initial color even on first selection
                        applyModelColorToUI(selectedModel.id)

                        // Update the ChatAdapter with the new model for welcome message
                        chatAdapter.updateCurrentModel(selectedModel.id)

                        // IMPORTANT: Update controls synchronously during initialization
                        updateControlsVisibility(selectedModel.id)
                        return@CustomSpinnerDialog
                    }

                    // CRITICAL FIX: Only cancel generation if model is actually changing AND we're not restoring a conversation
                    val isActualModelChange = ModelManager.selectedModel.id != selectedModel.id
                    val hasGeneratingMessages = chatAdapter.currentList.any { !it.isUser && it.isGenerating }
                    
                    if (isActualModelChange && !hasGeneratingMessages) {
                        // Only cancel if it's a real model change and no ongoing generation
                        isGenerating = false
                        chatAdapter.setGenerating(false)
                        currentApiJob?.cancel()
                        currentApiJob = null
                        Timber.d("🔄 Canceled generation due to model change: ${ModelManager.selectedModel.id} -> ${selectedModel.id}")
                    } else if (hasGeneratingMessages) {
                        Timber.d("🔄 Keeping ongoing generation alive during conversation restore")
                    }

                    // Reset UI state
                    binding.typingIndicator.visibility = View.GONE
                    stopPulsingAnimation()

                    // Model change - no dialog about exiting translation mode
                    proceedWithModelChange(selectedModel, preferences)
                }

                customDialog.show(binding.spinnerModels)
                return@setOnTouchListener true
            }
            false
        }

        // Standard item selection listener as backup for programmatic selection
        binding.spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedModel = ModelManager.models[position]

                // Only process if this is a programmatic selection (not from touch)
                if (position != currentSelection) {
                    // Check translation mode compatibility for programmatic selections too
                    if (isTranslationModeActive && !TranslationUtils.supportsTranslation(selectedModel.id)) {
                        // Reset to previous selection
                        binding.spinnerModels.setSelection(currentSelection)
                        return
                    }

                    // Track selection
                    currentSelection = position

                    // Update ModelManager
                    ModelManager.selectedModel = selectedModel
                    
                    // Update token manager with new model ID
                    if (::conversationTokenManager.isInitialized) {
                        conversationTokenManager.setModelId(selectedModel.id)
                    }

                    // Record model usage for tracking
                    ModelUsageTracker.recordModelUsage(this@MainActivity, selectedModel.id)

                    // Update UI
                    applyModelColorToUI(selectedModel.id)
                    updateControlsVisibility(selectedModel.id)

                    Timber.d("Model programmatically selected: ${selectedModel.displayName}")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // Apply color for the initial model on startup
        applyModelColorToUI(ModelManager.selectedModel.id)

        // Initialize welcome message with current model (only if adapter is initialized)
        if (::chatAdapter.isInitialized) {
            chatAdapter.updateCurrentModel(ModelManager.selectedModel.id)
        }

        // CRITICAL FIX: Update controls synchronously, not in a handler
        updateControlsVisibility(ModelManager.selectedModel.id)

        // For debugging: Log model data
        Timber.d("Initial model set to: ${ModelManager.selectedModel.displayName} (${ModelManager.selectedModel.id})")
    }
    private fun initializeAudio() {
        // Initialize audio response handler
        audioResponseHandler = AudioResponseHandler(this)

        // Set up default audio preferences (or load from settings)
        audioEnabled = PrefsManager.getAudioEnabled(this, false)
        currentAudioFormat = PrefsManager.getAudioFormat(this, "mp3")
        currentVoiceType = PrefsManager.getAudioVoice(this, "alloy")

        Timber.d("Audio initialized: enabled=$audioEnabled, format=$currentAudioFormat, voice=$currentVoiceType")
    }
    private fun hideRealTimeSearchIndicator() {
        runOnUiThread {
            realTimeSearchIndicator?.let { indicator ->
                if (indicator.visibility == View.VISIBLE) {
                    indicator.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            indicator.visibility = View.GONE
                        }
                        .start()
                }
            }
        }
    }

    /**
     * Update UI based on real-time search state
     */
    private fun updateRealTimeSearchUI(isActive: Boolean) {
        isRealTimeSearchActive = isActive

        if (isActive) {
            showRealTimeSearchIndicator()
        } else {
            hideRealTimeSearchIndicator()
        }
    }

    private fun saveResponsePreferences(preferences: ResponsePreferences) {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        // Log what we're saving for debugging
        Timber.d("Saving response preferences: length=${preferences.length.name}, tone=${preferences.tone.name}")

        prefs.edit {
            putString("response_length", preferences.length.name)
            putString("response_tone", preferences.tone.name)
            // Don't save model position here - that's handled separately as the default model
            apply()
        }

        // Update the local reference to keep in sync
        responsePreferences = preferences
    }
    private fun loadResponsePreferences(): ResponsePreferences {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        val lengthStr = prefs.getString("response_length", ResponseLength.DEFAULT.name)
        val length = try {
            ResponseLength.valueOf(lengthStr ?: ResponseLength.DEFAULT.name)
        } catch (e: Exception) {
            ResponseLength.DEFAULT
        }

        val toneStr = prefs.getString("response_tone", ResponseTone.DEFAULT.name)
        val tone = try {
            ResponseTone.valueOf(toneStr ?: ResponseTone.DEFAULT.name)
        } catch (e: Exception) {
            ResponseTone.DEFAULT
        }

        return ResponsePreferences(
            modelPosition = 0, // This will be set later in setupModelSelector
            length = length,
            tone = tone
        )
    }
    /**
     * Opens an existing conversation and loads its messages
     */
    /**
     * Opens an existing conversation and loads its messages
     */
    /**
     * Opens an existing conversation and loads its messages
     * Enhanced to properly handle drafts with files
     */
    private fun openConversation(conversationId: Long, aiModel: String?) {
        val conversationIdString = conversationId.toString()
        currentConversationId = conversationIdString
        // Reset draft loaded flag for new conversation
        isDraftContentLoaded = false
        hideKeyboard()
        
        // CRITICAL FIX: Don't immediately check for background generation to avoid race conditions
        // This will be called after conversation is loaded in restoreOngoingStreamingSessions

        // Show loading indicator while we load conversation
        val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
        loadingIndicator?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // CRITICAL FIX: Reset token manager before loading new conversation
                conversationTokenManager.reset()
                conversationTokenManager.setConversationId(conversationIdString)

                // Load token state from database
                val tokenStateLoaded = withContext(Dispatchers.IO) {
                    conversationTokenManager.loadTokenStateFromDatabase(conversationIdString, conversationsViewModel.getConversationDao())
                }

                // CRITICAL FIX: Load ALL messages from conversation
                val messages = withContext(Dispatchers.IO) {
                    conversationsViewModel.getAllConversationMessages(conversationIdString)
                }

                Timber.d("🔄 OPENING CONVERSATION $conversationIdString with ${messages.size} messages")

                // Verify we have messages
                if (messages.isEmpty()) {
                    Timber.w("No messages found for conversation $conversationIdString")
                } else {
                    Timber.d("Messages loaded for conversation:")
                    messages.forEachIndexed { index, msg ->
                        val role = if (msg.isUser) "USER" else "AI"
                        val preview = msg.message.take(30).replace("\n", " ")
                        Timber.d("  $index. [$role] $preview...")
                    }
                }

                // Get conversation object for draft handling
                val conversation = withContext(Dispatchers.IO) {
                    conversationsViewModel.getConversationById(conversationIdString)
                }

                withContext(Dispatchers.Main) {
                    // Set model first if provided
                    if (!aiModel.isNullOrEmpty()) {
                        setSelectedModelWithCallback(aiModel) {
                            continueOpeningConversationEnhanced(conversationIdString, tokenStateLoaded, messages, loadingIndicator, conversation)
                        }
                    } else {
                        continueOpeningConversationEnhanced(conversationIdString, tokenStateLoaded, messages, loadingIndicator, conversation)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading conversation: ${e.message}")
                withContext(Dispatchers.Main) {
                    loadingIndicator?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error loading conversation: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun continueOpeningConversationEnhanced(
        conversationIdString: String,
        tokenStateLoaded: Boolean,
        messages: List<ChatMessage>,
        loadingIndicator: ProgressBar?,
        conversation: Conversation?
    ) {
        // CRITICAL FIX: Check for ACTIVELY generating messages BEFORE initializing
        // Only consider messages as actively generating if they have active streaming sessions
        val actuallyGeneratingMessages = messages.filter { message ->
            !message.isUser && 
            message.isGenerating && 
            (StreamingStateManager.canContinueStreaming(message.id) || 
             backgroundAiService?.isGenerating(message.id) == true)
        }
        
        // CRITICAL FIX: If we're opening the same conversation that's already active, 
        // be extra careful about preserving streaming content
        val isSameConversation = currentConversationId == conversationIdString
        val hasCurrentStreamingContent = chatAdapter.currentList.any { it.isGenerating && it.message.isNotEmpty() }
        
        if (actuallyGeneratingMessages.isNotEmpty() || (isSameConversation && hasCurrentStreamingContent)) {
            Timber.d("🔄 Found ${actuallyGeneratingMessages.size} actually generating messages or existing streaming content - preserving streaming state")
            Timber.d("🔍 Same conversation: $isSameConversation, has streaming content: $hasCurrentStreamingContent")
            
            // CRITICAL FIX: Don't reinitialize MessageManager - it would overwrite streaming content
            // Instead, merge new messages with existing adapter content while preserving streaming
            mergeMessagesPreservingStreaming(messages, conversationIdString)
            
            // Restore ongoing streaming sessions without canceling them
            restoreOngoingStreamingSessions(conversationIdString, messages)
        } else {
            // No generating messages - safe to initialize normally
            messageManager.initialize(messages, conversationIdString)
            Timber.d("✅ Initialized MessageManager normally - no active streaming")
        }

        // Rebuild token manager if needed
        if (!tokenStateLoaded && messages.isNotEmpty()) {
            Timber.d("Rebuilding token state from ${messages.size} messages")
            conversationTokenManager.rebuildFromMessages(messages, conversationIdString)

            // Save the rebuilt state
            lifecycleScope.launch(Dispatchers.IO) {
                conversationTokenManager.saveTokenStateToDatabase(
                    conversationIdString,
                    conversationsViewModel.getConversationDao()
                )
            }
        }

        // Force update token counter UI
        updateTokenCounterUI()

        // Update conversation title and UI
        lifecycleScope.launch {
            conversation?.let { convo ->
                chatTitleView.text = convo.title
                isMessageSent = messages.isNotEmpty()
                updateNewConversationButtonState()

                // Handle draft loading
                if (convo.hasDraft) {
                    withContext(Dispatchers.Main) {
                        loadDraft(conversationIdString)
                    }
                }
            }

            // Hide loading indicator
            loadingIndicator?.visibility = View.GONE
            scrollToBottom()

            // Update scroll arrows
            if (::scrollArrowsHelper.isInitialized) {
                scrollArrowsHelper.updateButtonsState()
            }
        }
    }

    private fun loadDraft(conversationId: String) {
        lifecycleScope.launch {
            try {
                Timber.d("Attempting to load draft for conversation: $conversationId")

                // CRITICAL: Use withContext to ensure database operation completes
                val conversation = withContext(Dispatchers.IO) {
                    conversationsViewModel.getConversationById(conversationId)
                }

                if (conversation != null && conversation.hasDraft) {
                    Timber.d("Found draft for conversation: ${conversation.id}, hasDraft=${conversation.hasDraft}")
                    Timber.d("Draft content: text=${conversation.draftText.take(50)}")
                    Timber.d("Draft files: ${if (conversation.draftFiles.isNotEmpty()) "present" else "none"}")

                    // Run UI updates on main thread
                    withContext(Dispatchers.Main) {
                        // Load draft text
                        binding.etInputText.setText(conversation.draftText)

                        // Position cursor at the end of the text
                        binding.etInputText.setSelection(conversation.draftText.length)

                        // Load draft files with our enhanced method
                        if (conversation.draftFiles.isNotEmpty()) {
                            loadDraftFiles(conversation.draftFiles)
                        } else {
                            // No files, make sure file container is hidden
                            selectedFiles.clear()
                            binding.selectedFilesScrollView.visibility = View.GONE
                            fileHandler.updateSelectedFilesView()
                        }

                        // Update input field UI based on content
                        if (conversation.draftText.isNotEmpty() || selectedFiles.isNotEmpty()) {
                            binding.btnMicrophone.visibility = View.GONE
                            binding.btnSubmitText.visibility = View.VISIBLE

                            // Provide subtle feedback
                            if (conversation.draftText.isNotEmpty()) {
                                Toast.makeText(this@MainActivity, "Text draft loaded", Toast.LENGTH_SHORT).show()
                            }

                            if (selectedFiles.isNotEmpty()) {
                                Toast.makeText(this@MainActivity,
                                    "${selectedFiles.size} draft file(s) loaded", Toast.LENGTH_SHORT).show()
                            }

                            // Add subtle vibration feedback
                            if (::vibrator.isInitialized) {
                                provideHapticFeedback(50)
                            }
                        }
                    }
                } else {
                    Timber.d("No draft found for conversation: $conversationId")
                    if (conversation != null) {
                        Timber.d("Conversation exists but hasDraft=${conversation.hasDraft}")
                    } else {
                        Timber.d("Conversation object is null")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading draft: ${e.message}")
            }
        }
    }
    /**
     * Enhanced method for loading draft files with better URI handling
     */
    private fun loadDraftFiles(draftFilesJson: String) {
        if (draftFilesJson.isEmpty()) return

        try {
            Timber.d("Loading draft files from JSON: ${draftFilesJson.take(200)}...")
            
            // Use the helper method to deserialize
            val draftFiles = fileHandler.deserializeSelectedFiles(draftFilesJson)

            if (draftFiles.isEmpty()) {
                Timber.d("No draft files found or deserialization returned empty list")
                return
            }
            
            Timber.d("Found ${draftFiles.size} draft files to restore")
            draftFiles.forEachIndexed { index, file ->
                Timber.d("Draft file $index: ${file.name} (persistent: ${file.isPersistent}, persistentFileName: ${file.persistentFileName})")
            }

            // Create persistent storage helper
            val persistentStorage = PersistentFileStorage(this)

            // Clear current files
            selectedFiles.clear()

            // Track whether we had valid files
            var validFilesCount = 0

            // Process each file
            for (file in draftFiles) {
                try {
                    // First check if this is already a persistent file
                    if (file.isPersistent && file.persistentFileName.isNotEmpty()) {
                        // Try to restore from persistent storage
                        val restoredFile = FileUtil.FileUtil.SelectedFile.fromPersistentFileName(
                            fileName = file.persistentFileName,
                            persistentStorage = persistentStorage,
                            size = file.size,
                            isDocument = file.isDocument,
                            isExtracted = file.isExtracted,
                            extractedContentId = file.extractedContentId
                        )

                        if (restoredFile != null) {
                            selectedFiles.add(restoredFile)
                            validFilesCount++
                            Timber.d("Restored persistent file: ${file.persistentFileName}")
                            continue
                        } else {
                            Timber.w("Failed to restore persistent file: ${file.persistentFileName}")
                        }
                    }

                    // Fall back to checking original URI
                    val isAccessible = try {
                        contentResolver.openInputStream(file.uri)?.use { true } ?: false
                    } catch (e: Exception) {
                        Timber.e(e, "Can't access file URI: ${file.uri}")
                        false
                    }

                    if (isAccessible) {
                        // URI is still valid - make a persistent copy now
                        val persistentFile = file.toPersistent(persistentStorage)
                        if (persistentFile != null) {
                            selectedFiles.add(persistentFile)
                            validFilesCount++
                            Timber.d("Created persistent copy of: ${file.name}")
                        } else {
                            // Just add the original as fallback
                            selectedFiles.add(file)
                            validFilesCount++
                            Timber.d("Added original file (failed to persist): ${file.name}")
                        }
                    } else {
                        Timber.d("File not accessible and not persistent: ${file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing draft file: ${file.name} - ${e.message}")
                }
            }

            // Update UI if we found valid files
            if (validFilesCount > 0) {
                binding.selectedFilesScrollView.visibility = View.VISIBLE

                // Update the files view
                fileHandler.updateSelectedFilesView()

                Timber.d("Loaded $validFilesCount draft files out of ${draftFiles.size} saved")
            } else {
                binding.selectedFilesScrollView.visibility = View.GONE
                Timber.d("No valid files found from draft")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing draft files JSON: ${e.message}")
            // Fallback: Hide file container and clear any partial files
            selectedFiles.clear()
            binding.selectedFilesScrollView.visibility = View.GONE
            fileHandler.updateSelectedFilesView()
            
            // Show user feedback about the issue
            runOnUiThread {
                Toast.makeText(this, "Some draft files could not be restored", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /**
     * Apply color theme based on the selected model
     */
    fun applyModelColorToUI(modelId: String) {
        // Apply color only to the input field
        ModelIconUtils.applyModelColorToInput(binding.etInputText, modelId)
    }

    // -------------------------------------------------------------------------
    // CLICK LISTENERS AND EVENT HANDLERS
    // -------------------------------------------------------------------------

    fun stopGeneration() {
        Timber.d("Stop generation requested")

        // Call the AiChatService method to properly cancel the API call
        aiChatService.cancelCurrentGeneration()

        // Set flag immediately to prevent new operations
        isGenerating = false

        // Stop monitoring
        stopAggressiveButtonMonitoring()

        // Stop animation and reset UI immediately
        stopPulsingAnimation()

        // Reset generating flag in adapter
        chatAdapter.setGenerating(false)

        // Hide indicators
        binding.typingIndicator.visibility = View.GONE
        hideRealTimeSearchIndicator()

        // Reset button state
        val inputText = binding.etInputText.text.toString().trim()
        if (inputText.isEmpty()) {
            binding.btnMicrophone.visibility = View.VISIBLE
            binding.btnSubmitText.visibility = View.GONE
        } else {
            binding.btnMicrophone.visibility = View.GONE
            binding.btnSubmitText.visibility = View.VISIBLE
            binding.btnSubmitText.setImageResource(R.drawable.send_icon)
            binding.btnSubmitText.contentDescription = "Send message"
        }

        // Force update any generating messages to completed state
        forceCompleteGeneratingMessages()

        // Clear any reloading flags
        isReloadingMessage = false

        Toast.makeText(this, "Response generation stopped", Toast.LENGTH_SHORT).show()
        Timber.d("Generation stopped and UI reset")
    }

    private fun forceCompleteGeneratingMessages() {
        try {
            val currentList = chatAdapter.currentList.toMutableList()
            var hasChanges = false

            for (i in currentList.indices) {
                val message = currentList[i]
                if (message.isGenerating || message.isLoading) {
                    currentList[i] = message.copy(
                        isGenerating = false,
                        isLoading = false,
                        showButtons = true,
                        isThinkingActive = false,
                        isWebSearchActive = false,
                        message = message.message.ifEmpty { "Generation stopped" }
                    )
                    hasChanges = true
                }
            }

            if (hasChanges) {
                chatAdapter.submitList(currentList)
                Timber.d("Forced completion of ${currentList.count { it.isGenerating }} generating messages")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error forcing completion of generating messages: ${e.message}")
        }
    }


    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false

        val intentAction = intent.action
        val intentExtras = intent.extras?.keySet()?.joinToString()

        Timber.d("Processing intent: $intentAction, extras: $intentExtras")

        // Check if we're already processing this exact intent
        if (isIntentProcessed && intentAction == processingIntentAction) {
            Timber.d("Duplicate intent detected, skipping: $intentAction")
            return true
        }

        // Extract all intent variables at the top of the method
        val conversationId = intent.getLongExtra("CONVERSATION_ID", -1L)
        val aiModel = intent.getStringExtra("AI_MODEL")
        val conversationName = intent.getStringExtra("CONVERSATION_NAME")
        val featureMode = intent.getStringExtra("FEATURE_MODE")
        val initialPrompt = intent.getStringExtra("INITIAL_PROMPT")
        val cameraImageUri = intent.getStringExtra("CAMERA_IMAGE_URI")
        val linkUrl = intent.getStringExtra("LINK_URL")
        val showTranslatorUI = intent.getBooleanExtra("SHOW_TRANSLATOR_UI", false)
        val showLinkPrompt = intent.getBooleanExtra("SHOW_LINK_PROMPT", false)
        val autoSend = intent.getBooleanExtra("AUTO_SEND", false)

        // Add a flag to track if these special UI requests have been handled
        var specialFlagsHandled = false
        var handledIntent = false

        try {
            // Mark as being processed
            processingIntentAction = intentAction

            // Check if all required properties are initialized
            val needsDeferredProcessing = !::binding.isInitialized || !::vibrator.isInitialized ||
                    !::chatAdapter.isInitialized || !::messageManager.isInitialized ||
                    !::conversationsViewModel.isInitialized

            if (needsDeferredProcessing) {
                Timber.d("Activity not fully initialized, deferring intent processing")
                deferredIntent = intent
                isIntentProcessed = false
                return false
            }

            // Check for conversation opening (highest priority)
            if (conversationId != -1L && aiModel != null) {
                // Handle opening existing conversation
                Timber.d("Opening conversation: $conversationId with model: $aiModel")

                // Update the chat title if we have a conversation name
                if (!conversationName.isNullOrEmpty()) {
                    chatTitleView.text = conversationName
                }

                val conversationIdString = conversationId.toString()
                currentConversationId = conversationIdString

                // IMPORTANT: Hide keyboard and reset generation state for existing conversations
                hideKeyboard()
                setGeneratingState(false)
                isReloadingMessage = false
                
                // CRITICAL FIX: Don't immediately check for background generation to avoid race conditions
                // This will be called after conversation is loaded in restoreOngoingStreamingSessions

                lifecycleScope.launch {
                    try {
                        // Get ALL messages for this conversation
                        val messages = withContext(Dispatchers.IO) {
                            conversationsViewModel.getAllConversationMessages(conversationIdString)
                        }

                        Timber.d("Loaded ${messages.size} messages for conversation $conversationIdString")

                        withContext(Dispatchers.Main) {
                            // Set the selected AI model first
                            setSelectedModel(aiModel)

                            // Initialize with messages
                            messageManager.initialize(messages, conversationIdString)

                            // Update title and UI based on conversation data
                            val conversation = withContext(Dispatchers.IO) {
                                conversationsViewModel.getConversationById(conversationIdString)
                            }

                            conversation?.let { convo ->
                                chatTitleView.text = convo.title
                                isMessageSent = messages.isNotEmpty()
                                updateNewConversationButtonState()
                                binding.btnNewConversation.isEnabled = true
                            }

                            // Scroll chat to bottom
                            scrollToBottom()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading conversation: ${e.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Error loading conversation: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                handledIntent = true
            }
            // Check for force model action (second priority)
            else if (intent.action == "com.cyberflux.qwinai.ACTION_FORCE_MODEL") {
                val modelId = intent.getStringExtra("FORCE_MODEL")
                if (!modelId.isNullOrEmpty()) {
                    Timber.d("Force model selection: $modelId")

                    // CRITICAL FIX: Reset chat state to start completely fresh
                    resetChat()
                    
                    // Mark that we're handling a forced model to prevent draft loading
                    isDraftContentLoaded = true
                    
                    // Use a delayed approach to ensure UI is ready - but only ONCE
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Set selected model in spinner
                        setSelectedModelInSpinner(modelId)

                        // Check if there's also a feature mode to handle
                        if (!featureMode.isNullOrEmpty()) {
                            Timber.d("Handling feature mode after model selection: $featureMode")

                            // Pass the flags back to the feature mode handler
                            // Pass true to indicate we already reset the chat
                            specialFlagsHandled = handleFeatureMode(featureMode, intent, skipReset = true)
                        }
                        
                        // Force focus and show keyboard for new conversation
                        binding.etInputText.post {
                            binding.etInputText.requestFocus()
                            showKeyboard()
                            forceShowKeyboard()
                        }
                    }, 500)

                    handledIntent = true
                }
            }
            // Check for feature mode (third priority)
            else if (featureMode != null) {
                Timber.d("Handling feature mode: $featureMode")

                // The handleFeatureMode method now returns a boolean indicating if it handled special flags
                specialFlagsHandled = handleFeatureMode(featureMode, intent)
                handledIntent = true
            }
            // Check for initial prompt (fourth priority)
            else if (!initialPrompt.isNullOrEmpty()) {
                Timber.d("Setting initial prompt: ${initialPrompt.take(30)}...")

                // Set the prompt in the input field
                binding.etInputText.setText(initialPrompt)

                // Auto-send if requested
                if (autoSend) {
                    Timber.d("Auto-sending initial prompt")
                    Handler(Looper.getMainLooper()).postDelayed({
                        sendMessage()
                    }, 500)
                } else {
                    // Just focus the input field and show keyboard
                    binding.etInputText.requestFocus()
                    showKeyboard()
                }

                handledIntent = true
            }

            // Handle special UI requests if they weren't already handled by feature mode
            if (!specialFlagsHandled) {
                // Handle translator UI
                if (showTranslatorUI) {
                    Timber.d("Showing translator UI (via special flag)")

                    // Delay to ensure MainActivity is fully initialized
                    Handler(Looper.getMainLooper()).postDelayed({
                        showTranslatorUI()
                    }, 800)

                    handledIntent = true
                }

                // Handle link prompt
                if (showLinkPrompt) {
                    Timber.d("Showing link prompt AI message (via special flag)")

                    // Delay to ensure MainActivity is fully initialized
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Create a simulated AI message
                        val aiMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            conversationId = createOrGetConversation(""),
                            message = "Type the website link that you would like to be checked. I'll analyze the content for you.",
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )

                        // Add the message to the chat
                        val currentList = chatAdapter.currentList.toMutableList()
                        currentList.add(aiMessage)
                        chatAdapter.submitList(currentList)

                        // Save the message if not in private mode
                        if (!isPrivateModeEnabled) {
                            conversationsViewModel.saveMessage(aiMessage)
                        }

                        // Scroll to show the message
                        scrollToBottom()

                        // Focus the input field and show keyboard for this feature
                        binding.etInputText.requestFocus()
                        showKeyboard()
                    }, 800)

                    handledIntent = true
                }
            }

            // Handle any additional intent data that might be present regardless of whether
            // we've already handled something else

            // Check for camera image URI for OCR
            if (!cameraImageUri.isNullOrEmpty()) {
                Timber.d("Processing camera image URI: $cameraImageUri")

                try {
                    // Parse the URI
                    val uri = Uri.parse(cameraImageUri)

                    // Process the image with a delay to ensure MainActivity is fully initialized
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Add the image to selected files
                        fileHandler.handleSelectedFile(uri, false)

                        // If there's no prompt already set, add a default one
                        if (binding.etInputText.text.isNullOrEmpty()) {
                            binding.etInputText.setText("Please extract all text from this image and provide a concise summary.")
                        }

                        // Auto send with delay to ensure image is processed
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendMessage()
                        }, 1500)
                    }, 1000)

                    handledIntent = true
                } catch (e: Exception) {
                    Timber.e(e, "Error processing camera image URI: ${e.message}")
                    Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Handle link URL
            if (!linkUrl.isNullOrEmpty()) {
                Timber.d("Processing link URL: $linkUrl")

                Handler(Looper.getMainLooper()).postDelayed({
                    // Create prompt with link
                    val prompt = "Please analyze the content of this webpage: $linkUrl"
                    binding.etInputText.setText(prompt)

                    // Auto send
                    Handler(Looper.getMainLooper()).postDelayed({
                        sendMessage()
                    }, 500)
                }, 500)

                handledIntent = true
            }

            // Mark as successfully processed if we handled something
            if (handledIntent) {
                isIntentProcessed = true
            }

        } catch (e: Exception) {
            Timber.e(e, "Error handling intent: ${e.message}")
            // Store the intent for deferred processing since we hit an error
            deferredIntent = intent
            isIntentProcessed = false
            return false
        }

        // UPDATED: More specific conditions for when to show keyboard
        if (handledIntent && ::vibrator.isInitialized && ::binding.isInitialized) {
            try {
                val isOpeningExistingConversation = conversationId != -1L && aiModel != null
                val isFeatureNeedingInput = featureMode in listOf("ask_by_link", "translator") || showLinkPrompt
                val hasNonAutoSendPrompt = !initialPrompt.isNullOrEmpty() && !autoSend

                // Only show keyboard and vibrate for specific cases
                val shouldShowKeyboard = !isOpeningExistingConversation && (
                        isFeatureNeedingInput || hasNonAutoSendPrompt
                        )

                if (shouldShowKeyboard) {
                    // Vibrate to indicate new interaction
                    provideHapticFeedback(50)

                    // Wait for UI to be fully ready, then show keyboard
                    binding.etInputText.post {
                        binding.etInputText.requestFocus()
                        showKeyboard()
                        forceShowKeyboard()
                    }

                    Timber.d("Showing keyboard for new chat interaction or input-needed feature")
                } else {
                    Timber.d("Not showing keyboard - opening existing conversation: $conversationId or non-input feature")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error providing feedback after intent handling: ${e.message}")
            }
        }

        return handledIntent
    }        /**
     * Show options menu for getting more credits
     */
    private fun showGetCreditsMenu() {
        PopupMenu(this, binding.creditsButton).apply {
            menuInflater.inflate(R.menu.get_credits_menu, menu)

            // Check if ads are available using new credit system
            val canEarnCredits = creditManager.canEarnMoreCredits(CreditManager.CreditType.CHAT)
            val creditsToMax = creditManager.getCreditsToMax(CreditManager.CreditType.CHAT)
            
            if (canEarnCredits && creditsToMax > 0) {
                menu.findItem(R.id.menu_watch_ad_1).title = "Watch ad for 5 credits"
                menu.findItem(R.id.menu_watch_ad_1).isEnabled = true
            } else {
                menu.findItem(R.id.menu_watch_ad_1).isEnabled = false
                menu.findItem(R.id.menu_watch_ad_1).title = "Watch ad for 5 credits (limit reached)"
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_get_unlimited_credits -> navigateToWelcomeActivity()
                    R.id.menu_watch_ad_1 -> adManager.showRewardedAd(
                        this@MainActivity,
                        CreditManager.CreditType.CHAT,
                        AdManager.AdTrigger.MANUAL_REWARD
                    )
                }
                true
            }
            show()
        }
    }
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Delayed processing of any deferred intent
        Handler(Looper.getMainLooper()).postDelayed({
            processDeferredIntent()
        }, 300)
    }


    /**
     * Handle different feature modes passed from StartActivity
     * @return true if this method handled any special UI flags (translator or ask by link)
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun handleFeatureMode(featureMode: String, intent: Intent, skipReset: Boolean = false): Boolean {
        var handledSpecialFlags = false

        try {
            Timber.d("Handling feature mode: $featureMode, skipReset: $skipReset")
            
            // CRITICAL FIX: Reset chat state before starting any feature to ensure clean state
            // Only reset if not already done by force model handler
            if (!skipReset) {
                resetChat()
                
                // Mark that we're handling a feature mode to prevent draft loading
                isDraftContentLoaded = true
            }

            // Extract UI flags directly from intent
            val showTranslatorUI = intent.getBooleanExtra("SHOW_TRANSLATOR_UI", false)
            val showLinkPrompt = intent.getBooleanExtra("SHOW_LINK_PROMPT", false)

            when (featureMode) {
                "web_search" -> {
                    // Force enable deep search for web search feature
                    isDeepSearchEnabled = true
                    updateDeepSearchButtonState(true)

                    // Save the setting
                    PrefsManager.saveAiSettings(this, AppSettings(
                        isDeepSearchEnabled = true,
                        isReasoningEnabled = isReasoningEnabled,
                        reasoningLevel = reasoningLevel
                    ))

                    // Show web search welcome message
                    Handler(Looper.getMainLooper()).postDelayed({
                        val aiMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            conversationId = createOrGetConversation(""),
                            message = "Web search is now enabled! I can search the internet for real-time information. What would you like to search for?",
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )

                        val currentList = chatAdapter.currentList.toMutableList()
                        currentList.add(aiMessage)
                        chatAdapter.submitList(currentList)

                        if (!isPrivateModeEnabled) {
                            conversationsViewModel.saveMessage(aiMessage)
                        }

                        scrollToBottom()
                    }, 500)
                }

                "image_upload" -> {
                    // Open file picker for images
                    Handler(Looper.getMainLooper()).postDelayed({
                        fileHandler.openFilePicker(filePickerLauncher)
                        Toast.makeText(this, "Select an image to analyze", Toast.LENGTH_SHORT).show()
                    }, 500)
                }

                "ask_by_link" -> {
                    // If showLinkPrompt is true, then we should handle it here and mark as handled
                    if (showLinkPrompt) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val aiMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = createOrGetConversation(""),
                                message = "Type the website link that you would like to be checked. I'll analyze the content for you.",
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )

                            val currentList = chatAdapter.currentList.toMutableList()
                            currentList.add(aiMessage)
                            chatAdapter.submitList(currentList)

                            if (!isPrivateModeEnabled) {
                                conversationsViewModel.saveMessage(aiMessage)
                            }

                            scrollToBottom()

                            // Focus input field
                            binding.etInputText.requestFocus()
                            showKeyboard()
                        }, 500)

                        // Mark that we've handled this special flag
                        handledSpecialFlags = true
                    } else {
                        // Standard message for "ask by link" mode
                        Handler(Looper.getMainLooper()).postDelayed({
                            val aiMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = createOrGetConversation(""),
                                message = "I can analyze content from web links using advanced search capabilities. Please paste the URL you'd like me to analyze.",
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )

                            val currentList = chatAdapter.currentList.toMutableList()
                            currentList.add(aiMessage)
                            chatAdapter.submitList(currentList)

                            if (!isPrivateModeEnabled) {
                                conversationsViewModel.saveMessage(aiMessage)
                            }

                            scrollToBottom()

                            // Focus input field
                            binding.etInputText.requestFocus()
                            showKeyboard()
                        }, 500)
                    }
                }

                "prompt_of_day" -> {
                    // Show daily prompt
                    Handler(Looper.getMainLooper()).postDelayed({
                        val dailyPrompt = startActivity.getDailyPrompt()
                        binding.etInputText.setText(dailyPrompt)

                        // Auto-send after a short delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendMessage()
                        }, 1000)
                    }, 500)
                }

                "image_generation" -> {
                    // Check subscription first
                    if (!PrefsManager.isSubscribed(this)) {
                        Toast.makeText(this, "Image generation requires a subscription", Toast.LENGTH_LONG).show()
                        navigateToWelcomeActivity()
                        return false
                    }

                    // Show image generation prompt
                    Handler(Looper.getMainLooper()).postDelayed({
                        val aiMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            conversationId = createOrGetConversation(""),
                            message = "I'm ready to generate images for you! Describe what you'd like me to create in detail - the more specific you are, the better the result will be.",
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )

                        val currentList = chatAdapter.currentList.toMutableList()
                        currentList.add(aiMessage)
                        chatAdapter.submitList(currentList)

                        if (!isPrivateModeEnabled) {
                            conversationsViewModel.saveMessage(aiMessage)
                        }

                        scrollToBottom()

                        // Focus input field
                        binding.etInputText.requestFocus()
                        showKeyboard()
                    }, 500)
                }

                "ocr_reader" -> {
                    // Open camera for OCR
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Check if we have a camera image URI from intent
                        val cameraImageUri = intent.getStringExtra("CAMERA_IMAGE_URI")
                        if (!cameraImageUri.isNullOrEmpty()) {
                            // Process the provided image
                            try {
                                val uri = Uri.parse(cameraImageUri)
                                fileHandler.handleSelectedFile(uri, false)
                                Toast.makeText(this, "Processing captured image...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing camera image: ${e.message}")
                                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Show OCR welcome message and open camera
                            val aiMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = createOrGetConversation(""),
                                message = "I can extract and analyze text from images. Take a photo or upload an image with text, and I'll read it for you.",
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )

                            val currentList = chatAdapter.currentList.toMutableList()
                            currentList.add(aiMessage)
                            chatAdapter.submitList(currentList)

                            if (!isPrivateModeEnabled) {
                                conversationsViewModel.saveMessage(aiMessage)
                            }

                            scrollToBottom()

                            // Open camera
                            takePhoto()
                        }
                    }, 500)
                }

                "translator" -> {
                    // If showTranslatorUI is true, then we should handle it here and mark as handled
                    if (showTranslatorUI) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            showTranslatorUI()
                        }, 800)

                        // Mark that we've handled this special flag
                        handledSpecialFlags = true
                    } else {
                        // Standard initialization for translator mode
                        Handler(Looper.getMainLooper()).postDelayed({
                            showTranslatorUI()
                        }, 800)
                    }
                }

                else -> {
                    Timber.w("Unknown feature mode: $featureMode")
                    Toast.makeText(this, "Feature mode not implemented: $featureMode", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error handling feature mode $featureMode: ${e.message}")
            Toast.makeText(this, "Error activating feature: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return handledSpecialFlags
    }
    /**
     * Show options menu for conversation actions
     */
    @SuppressLint("SimpleDateFormat")
    private fun showConversationMenu(view: View, conversation: Conversation) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.conversation_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_rename -> renameConversation(conversation)
                    R.id.menu_delete -> deleteConversation(conversation)
                    R.id.menu_share -> shareConversation()
                }
                true
            }
            show()
        }
    }

    private fun showAttachmentMenu() {
        val currentModel = ModelManager.selectedModel
        val modelConfig = ModelConfigManager.getConfig(currentModel.id)
        val supportsImage = ModelValidator.supportsImageUpload(currentModel.id)

        // Check file limit
        if (selectedFiles.size >= 5) {
            Toast.makeText(
                this,
                "Maximum 5 files allowed. Please remove some first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Show the new file upload bottom sheet
        val fileUploadBottomSheet = FileUploadBottomSheet.newInstance(
            onFileSelected = { uri ->
                // Handle the selected file using the existing file handler
                fileHandler.handleSelectedFile(uri, false)
            },
            onCameraClick = {
                if (supportsImage) {
                    takePhoto()
                } else {
                    Toast.makeText(
                        this,
                        "This model doesn't support image uploads",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        
        fileUploadBottomSheet.show(supportFragmentManager, "FileUploadBottomSheet")
    }
    private fun openImagePicker() {
        val currentModel = ModelManager.selectedModel
        val modelConfig = ModelConfigManager.getConfig(currentModel.id)
        
        if (modelConfig == null || !modelConfig.supportsImages) {
            Toast.makeText(this, "This model doesn't support image uploads", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create image-specific intent with model's supported formats
        val supportedMimeTypes = mutableListOf<String>()
        modelConfig.supportedImageFormats.forEach { format ->
            when (format.lowercase()) {
                "jpg", "jpeg" -> supportedMimeTypes.add("image/jpeg")
                "png" -> supportedMimeTypes.add("image/png")
                "gif" -> supportedMimeTypes.add("image/gif")
                "webp" -> supportedMimeTypes.add("image/webp")
            }
        }
        
        if (supportedMimeTypes.isEmpty()) {
            // Fallback to common image types
            supportedMimeTypes.addAll(listOf("image/jpeg", "image/png", "image/gif", "image/webp"))
        }
        
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, modelConfig.supportsMultipleFileSelection)
            putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes.toTypedArray())
        }

        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching image picker: ${e.message}")
            Toast.makeText(this, "Error opening image picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDocumentPicker() {
        val currentModel = ModelManager.selectedModel
        val modelConfig = ModelConfigManager.getConfig(currentModel.id)
        
        if (modelConfig == null || !modelConfig.supportsFileUpload) {
            // Fallback to text extraction for models without native file support
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain",
                    "text/csv"
                ))
            }
            
            try {
                documentPickerLauncher.launch(intent)
            } catch (e: Exception) {
                Timber.e(e, "Error launching document picker: ${e.message}")
                Toast.makeText(this, "Error opening document picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        // Use model-specific file filtering
        val intent = FileFilterUtils.createFilePickerIntent(modelConfig, allowMultiple = true)
        
        if (intent == null) {
            Toast.makeText(this, "This model doesn't support file uploads", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            documentPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching document picker: ${e.message}")
            Toast.makeText(this, "Error opening document picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    // Add document picker launcher
    val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val currentModel = ModelManager.selectedModel
            val hasNativeSupport = ModelValidator.hasNativeDocumentSupport(currentModel.id)
            val isOcrModel = ModelValidator.isOcrModel(currentModel.id)

            // Handle multiple documents
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                Timber.d("Multiple documents selected: $count files")

                // Check against TOTAL files for document processing
                val totalFileCount = selectedFiles.size + count
                if (totalFileCount > 5) {
                    Toast.makeText(
                        this,
                        "Maximum 5 files allowed. You're trying to add $count more when you already have ${selectedFiles.size}.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Process only up to the max files allowed
                val remainingSlots = 5 - selectedFiles.size
                val filesToProcess = if (remainingSlots > 0) minOf(count, remainingSlots) else 0

                if (filesToProcess <= 0) {
                    Toast.makeText(
                        this,
                        "Cannot add more documents. Please remove some first.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@registerForActivityResult
                }

                Timber.d("Processing $filesToProcess out of $count selected documents")

                // Process each document
                for (i in 0 until filesToProcess) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    processSelectedDocument(uri, currentModel, hasNativeSupport, isOcrModel)
                }
                
                // Show success message for multiple documents
                if (filesToProcess > 1) {
                    Toast.makeText(
                        this,
                        "Added $filesToProcess documents successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (data?.data != null) {
                // Single document
                val uri = data.data!!
                processSelectedDocument(uri, currentModel, hasNativeSupport, isOcrModel)
            }
        }
    }

    private fun processSelectedDocument(uri: Uri, currentModel: Any, hasNativeSupport: Boolean, isOcrModel: Boolean) {
        // Validate file type using centralized validation
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        
        if (!SupportedFileTypes.isDocumentTypeSupported(mimeType)) {
            val fileName = FileUtil.getFileNameFromUri(this, uri) ?: "unknown file"
            val fileExtension = SupportedFileTypes.getFileExtensionFromMimeType(mimeType)
            
            Toast.makeText(
                this,
                "Unsupported file type: $fileName ($mimeType$fileExtension)\n\n${SupportedFileTypes.getSupportedDocumentFormatsDescription()}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Check if this is a PDF file that should be extracted
        val fileName = FileUtil.getFileNameFromUri(this, uri) ?: ""
        val isPdfFile = fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"
        
        val modelConfig = ModelConfigManager.getConfig((currentModel as? AIModel)?.id ?: "")
        val supportsFileUpload = modelConfig?.supportsFileUpload ?: false
        
        if (isOcrModel) {
            // Handle OCR specifically
            handlePdfForOcr(uri)
        } else if (supportsFileUpload && hasNativeSupport) {
            // Model supports native file uploads - send the actual file
            Timber.d("Using native file upload for: $fileName (model supports file uploads)")
            fileHandler.handleSelectedFile(uri, true)
        } else {
            // Model doesn't support native files - extract text content
            Timber.d("Using text extraction for: $fileName (model needs text extraction)")
            fileHandler.handleSelectedDocument(uri)
        }
    }



    // Add method to handle selected PDF for OCR
    private fun handlePdfForOcr(uri: Uri) {
        // Verify that we're using OCR model
        if (!ModelValidator.isOcrModel(ModelManager.selectedModel.id)) {
            // Switch to OCR model
            setSelectedModelInSpinner(ModelManager.MISTRAL_OCR_ID)
            Toast.makeText(this, "Switched to OCR model for PDF processing", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            try {
                // Get file details
                val fileName = FileUtil.getFileName(this@MainActivity, uri)
                val fileSize = FileUtil.getFileSize(this@MainActivity, uri)

                // Add to selectedFiles for preview
                val selectedFile = FileUtil.FileUtil.SelectedFile(
                    uri = uri,
                    name = fileName,
                    size = fileSize,
                    isDocument = true
                )

                selectedFiles.add(selectedFile)
                fileHandler.updateSelectedFilesView()

                // Show OCR options dialog instead of immediately processing
                showOcrOptionsDialog(uri, fileName)

            } catch (e: Exception) {
                Timber.e(e, "Error preparing PDF for OCR")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error preparing PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOcrOptionsDialog(uri: Uri, fileName: String) {
        // Since the user already selected a file, we need to show them they can configure
        // OCR options and then send. We'll show a simple dialog explaining this.
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("File Selected for OCR")
            .setMessage("$fileName has been selected for OCR processing.\n\nYou can now configure OCR options below and click Send when ready.")
            .setPositiveButton("OK") { _, _ ->
                // File remains in selectedFiles list, user can configure options and send
                Toast.makeText(this, "Configure OCR options below and click Send", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Remove from selected files
                selectedFiles.removeAll { it.uri == uri }
                fileHandler.updateSelectedFilesView()
            }
            .create()

        dialog.show()
    }




    // Helper method to set model with callback
    private fun setSelectedModelWithCallback(modelId: String, onComplete: () -> Unit) {
        try {
            val modelIndex = ModelManager.models.indexOfFirst { it.id == modelId }
            if (modelIndex != -1) {
                // Update ModelManager first
                ModelManager.selectedModel = ModelManager.models[modelIndex]

                // Set UI synchronously
                binding.spinnerModels.setSelection(modelIndex)

                // Update adapter if it's our custom adapter
                val adapter = binding.spinnerModels.adapter
                if (adapter is ModelSpinnerAdapter) {
                    adapter.setSelectedPosition(modelIndex)
                }

                // Update UI synchronously
                ModelValidator.clearCache()
                updateControlsVisibility(modelId)
                applyModelColorToUI(modelId)
                chatAdapter.updateCurrentModel(modelId)

                // Execute callback after a short delay to ensure UI updates
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 300)
            } else {
                Timber.e("Model not found: $modelId")
                onComplete() // Still call completion even if model not found
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting model: ${e.message}")
            onComplete() // Still call completion on error
        }
    }

    private fun saveTokenStateAfterMessageSent() {
        if (currentConversationId == null || currentConversationId!!.startsWith("private_")) return

        lifecycleScope.launch(Dispatchers.IO) {
            conversationTokenManager.saveTokenStateToDatabase(
                currentConversationId!!,
                conversationsViewModel.getConversationDao()
            )
        }
    }
    /**
     * Update token counter UI method
     * Enhanced to use secure token management system
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun updateTokenCounterUI() {
        val tvTokenCount = findViewById<TextView>(R.id.tvTokenCount) ?: return
        val currentText = binding.etInputText.text?.toString() ?: ""
        val isSubscribed = PrefsManager.isSubscribed(this)
        val modelId = ModelManager.selectedModel.id

        // Use TokenCounterHelper for consistent UI updates
        TokenCounterHelper.updateTokenCounter(
            tvTokenCount,
            currentText,
            modelId,
            isSubscribed,
            this,
            conversationTokenManager
        )
    }

    /**
     * Handle the post-edit flow to generate an AI response for the edited message
     */
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        // Clear the menu first to prevent any existing items from showing up
        menu.clear()

        // Determine which message to use based on stored references
        if (selectedUserMessage != null) {
            // User message menu
            menuInflater.inflate(R.menu.popup_user_message, menu)
            Timber.d("Showing context menu for user message: ${selectedUserMessage?.id}")
        } else if (selectedMessage != null) {
            // AI message menu
            val isLatestMessage = messageManager.isLatestAiMessage(selectedMessage!!.id)

            if (isLatestMessage) {
                // Show full menu with reload option for latest AI message
                menuInflater.inflate(R.menu.popup_ai_message, menu)
            } else {
                // Show limited menu without reload for older AI messages
                menuInflater.inflate(R.menu.popup_ai_message_no_reload, menu)
            }
            Timber.d("Showing context menu for AI message: ${selectedMessage?.id}")
        } else {
            Timber.w("No message selected for context menu")
        }
    }

    /**
     * Handle context menu item selection - handles both AI and user menus
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // User message actions
            R.id.menu_edit_message -> {
                selectedUserMessage?.let { editMessage(it) }
                return true
            }
            R.id.menu_select_text_user -> {
                selectedUserMessage?.let {
                    // Pass false to indicate this is not editable
                    openTextSelectionScreen(it.message, false)
                }
                return true
            }
            R.id.menu_copy_message_user -> {
                selectedUserMessage?.let { copyToClipboard(it.message) }
                return true
            }

            // AI message actions
            R.id.menu_copy_message -> {
                selectedMessage?.let { copyToClipboard(it.message) }
                return true
            }
            R.id.menu_reload_message -> {
                selectedMessage?.let {
                    if (!isGenerating) {
                        reloadAiMessage(it)
                    } else {
                        Toast.makeText(this, "Please wait for current generation to complete", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            R.id.menu_select_text -> {
                selectedMessage?.let {
                    // Pass false to indicate this is not editable
                    openTextSelectionScreen(it.message, false)
                }
                return true
            }
        }

        return super.onContextItemSelected(item)
    }    /**
     * Show dialog to edit a message with null safety improvements
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun showEditMessageDialog(message: ChatMessage) {
        try {
            val dialog = Dialog(this, R.style.EditMessageDialogTheme)
            dialog.setContentView(R.layout.edit_message_dialog)

            // Configure dialog window
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }

            // Get UI components with null safety
            val editText = dialog.findViewById<TextInputEditText>(R.id.etEditMessage)
            val btnSend = dialog.findViewById<ImageButton>(R.id.btnSendEdit)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseEdit)
            val btnEditReasoning = dialog.findViewById<ImageButton>(R.id.btnEditReasoning)
            val btnEditDeepSearch = dialog.findViewById<ImageButton>(R.id.btnEditDeepSearch)
            val btnEditExpandText = dialog.findViewById<ImageButton>(R.id.btnEditExpandText)
            val tokenCounterView = dialog.findViewById<TextView>(R.id.editTokenCounter)

            if (editText == null || btnSend == null || btnClose == null) {
                Timber.e("Required UI components not found in edit dialog")
                return
            }

            // Set initial text and state
            editText.setText(message.message)
            editText.requestFocus()
            editText.selectAll()

            // Track reasoning and web search state
            var dialogReasoningEnabled = isReasoningEnabled
            var dialogDeepSearchEnabled = isDeepSearchEnabled

            // Set initial button states (with null checks)
            btnEditReasoning?.let { updateButtonState(it, dialogReasoningEnabled) }
            btnEditDeepSearch?.let { updateButtonState(it, dialogDeepSearchEnabled) }

            // Update token counter if available
            tokenCounterView?.let {
                updateTokenCountForEdit(it, message, message.message, btnSend)
            }

            // Update expand button visibility if available
            btnEditExpandText?.let {
                updateEditExpandButtonVisibility(editText, it)
            }

            // Add text watcher
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?.toString() ?: ""

                    // Update token counter if available
                    tokenCounterView?.let {
                        updateTokenCountForEdit(it, message, text, btnSend)
                    }

                    // Update expand button if available
                    btnEditExpandText?.let {
                        updateEditExpandButtonVisibility(editText, it)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            // Set up close button
            btnClose.setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
                Handler(Looper.getMainLooper()).postDelayed({
                    dialog.dismiss()
                }, 100)
            }

            // Set up reasoning button if available
            btnEditReasoning?.setOnClickListener {
                dialogReasoningEnabled = !dialogReasoningEnabled
                updateButtonState(btnEditReasoning, dialogReasoningEnabled)
                provideHapticFeedback(50)
            }

            // Set up deep search button if available
            btnEditDeepSearch?.setOnClickListener {
                dialogDeepSearchEnabled = !dialogDeepSearchEnabled
                updateButtonState(btnEditDeepSearch, dialogDeepSearchEnabled)
                provideHapticFeedback(50)
            }

            // Set up expand button if available
            btnEditExpandText?.setOnClickListener {
                val currentText = editText.text.toString()
                dialog.dismiss() // Close dialog before opening expansion
                openEditTextExpansionActivity(currentText, message)
                provideHapticFeedback(50)
            }

            // Handle sending edited message
            btnSend.setOnClickListener {
                val newText = editText.text.toString().trim()

                if (newText.isEmpty()) {
                    Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (newText == message.message) {
                    // No changes made, just dismiss
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(editText.windowToken, 0)
                    dialog.dismiss()
                    return@setOnClickListener
                }

                // Check token limits and proceed
                handleEditMessageTokenCheck(dialog, editText, message, newText, dialogReasoningEnabled, dialogDeepSearchEnabled)
            }

            // Show the dialog
            dialog.show()

        } catch (e: Exception) {
            Timber.e(e, "Error showing edit message dialog: ${e.message}")
            Toast.makeText(this, "Error opening edit dialog", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Simplified method to update token count for edit dialog
     * Safely handles null values
     */
    private fun updateTokenCountForEdit(tokenCounterView: TextView, originalMessage: ChatMessage,
                                        newText: String, btnSend: ImageButton) {
        try {
            val modelId = ModelManager.selectedModel.id
            val isSubscribed = PrefsManager.isSubscribed(this)

            // Calculate tokens for the new text
            val newTokens = TokenValidator.estimateTokenCount(newText)

            // Calculate tokens for the original message
            val originalTokens = TokenValidator.estimateTokenCount(originalMessage.message)

            // Calculate the difference
            val tokenDifference = newTokens - originalTokens

            // Try to get conversation tokens excluding original message
            val currentTokens = try {
                conversationTokenManager.getRawConversationTokens() - originalTokens
            } catch (e: Exception) {
                Timber.e(e, "Error getting raw conversation tokens")
                0 // Default to 0 if there's an error
            }

            // Calculate new total
            val newTotal = currentTokens + newTokens

            // Get max tokens
            val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)

            // Use constants for system tokens and reservation
            val systemTokens = 500 // Default system tokens
            val reservedPercentage = 0.2 // 20% reserved for response
            val reservedResponseTokens = (maxTokens * reservedPercentage).toInt()
            val availableTokens = maxTokens - systemTokens - reservedResponseTokens - newTotal

            // Create display text
            val displayText = if (tokenDifference > 0) {
                "$newTokens tokens (+$tokenDifference) | $availableTokens available"
            } else if (tokenDifference < 0) {
                "$newTokens tokens (${tokenDifference}) | $availableTokens available"
            } else {
                "$newTokens tokens (unchanged) | $availableTokens available"
            }

            // Apply color based on availability
            val color = when {
                availableTokens < 0 -> ContextCompat.getColor(this, R.color.error_color)
                availableTokens < maxTokens * 0.15 -> ContextCompat.getColor(this, R.color.warning_color)
                else -> ContextCompat.getColor(this, R.color.text_secondary)
            }

            // Update the view
            tokenCounterView.text = displayText
            tokenCounterView.setTextColor(color)

            // Update send button state
            btnSend.isEnabled = availableTokens >= 0 && newText.isNotEmpty()
            btnSend.alpha = if (availableTokens >= 0 && newText.isNotEmpty()) 1.0f else 0.5f
        } catch (e: Exception) {
            // Log error but don't crash
            Timber.e(e, "Error updating token count for edit: ${e.message}")

            // Set safe default state
            tokenCounterView.text = "Token count unavailable"
            tokenCounterView.setTextColor(ContextCompat.getColor(this, R.color.text_disabled))

            // Enable button if text isn't empty
            btnSend.isEnabled = newText.isNotEmpty()
            btnSend.alpha = if (newText.isNotEmpty()) 1.0f else 0.5f
        }
    }    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun proceedWithEditedMessage(
        originalMessage: ChatMessage,
        newText: String,
        reasoningEnabled: Boolean,
        deepSearchEnabled: Boolean
    ) {
        try {
            // Update global app settings
            isReasoningEnabled = reasoningEnabled
            isDeepSearchEnabled = deepSearchEnabled
            reasoningLevel = if(reasoningEnabled) "high" else "low"

            // Save the updated settings
            PrefsManager.saveAiSettings(this, AppSettings(
                isDeepSearchEnabled = isDeepSearchEnabled,
                isReasoningEnabled = isReasoningEnabled,
                reasoningLevel = reasoningLevel
            ))

            // Update UI button states
            updateReasoningButtonState(isReasoningEnabled)
            updateDeepSearchButtonState(isDeepSearchEnabled)

            // Remove the original message from token count
            conversationTokenManager.removeMessage(originalMessage.id)

            // Store the original message content as the first version if not already stored
            if (originalMessage.messageVersions.isEmpty()) {
                originalMessage.messageVersions.add(originalMessage.message)
                originalMessage.totalVersions = 1
                originalMessage.versionIndex = 0
            }

            // Add the new text as a new version
            originalMessage.addVersion(newText)
            originalMessage.isEdited = true

            // Update the message in the adapter
            val messageIndex = chatAdapter.currentList.indexOfFirst { it.id == originalMessage.id }
            if (messageIndex != -1) {
                chatAdapter.notifyItemChanged(messageIndex)
            }

            // Add the updated message to token count
            conversationTokenManager.addMessage(originalMessage)

            // Save the updated message to database if not private
            if (!originalMessage.conversationId.startsWith("private_")) {
                lifecycleScope.launch {
                    conversationsViewModel.saveMessage(originalMessage)
                }
            }

            // Update token counter UI
            updateTokenCounterUI()

            // Save token state to database if not private conversation
            if (currentConversationId != null && !currentConversationId!!.startsWith("private_")) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        conversationTokenManager.saveTokenStateToDatabase(
                            currentConversationId!!,
                            conversationsViewModel.getConversationDao()
                        )
                        Timber.d("Token state saved to database after message edit")
                    } catch (e: Exception) {
                        Timber.e(e, "Error saving token state to database: ${e.message}")
                    }
                }
            }

            // Show loading indicator
            binding.typingIndicator.visibility = View.VISIBLE
            setGeneratingState(true)

            // Wait for UI to update with the edited message
            lifecycleScope.launch {
                delay(100)

                // Generate a response for the edited message
                aiChatService.generateResponseForEditedMessage(
                    conversationId = originalMessage.conversationId,
                    message = newText,
                    forceWebSearch = isDeepSearchEnabled,
                    userMessageId = originalMessage.id
                )

                // Scroll to show the message
                scrollToBottom()
            }

            // Provide feedback
            provideHapticFeedback(50)

        } catch (e: Exception) {
            Timber.e(e, "Error in proceedWithEditedMessage: ${e.message}")
            Toast.makeText(this, "Error processing edited message: ${e.message}", Toast.LENGTH_SHORT).show()

            // Reset state on error
            setGeneratingState(false)
            binding.typingIndicator.visibility = View.GONE
        }
    }

    // Helper method to update button state within dialog
    private fun updateButtonState(button: ImageButton, isEnabled: Boolean) {
        if (isEnabled) {
            // Active state
            button.background = ContextCompat.getDrawable(this, R.drawable.circle_button_active)
            button.setColorFilter(ContextCompat.getColor(this, R.color.white))
        } else {
            // Inactive state
            button.background = ContextCompat.getDrawable(this, R.drawable.circle_button_inactive)
            button.setColorFilter("#757575".toColorInt())
        }
    }


    /**
     * Open text selection screen for any message
     */
    fun openTextSelectionScreen(messageText: String, isEditable: Boolean) {
        openTextSelectionActivity(messageText, isEditable, if (isEditable) "Edit Text" else "View Text", "view")
    }    /**
     * Rename a conversation
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun renameConversation(conversation: Conversation) {
        val input = EditText(this)
        input.setText(conversation.title)
        AlertDialog.Builder(this)
            .setTitle("Rename Chat")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    // Update model
                    conversation.title = newName
                    conversation.name = newName
                    conversation.timestamp = System.currentTimeMillis()

                    // Save to database
                    conversationsViewModel.updateConversation(conversation)

                    // Update UI IMMEDIATELY
                    chatTitleView.text = newName

                    // Notify adapter
                    conversationAdapter.notifyDataSetChanged()
                }
            }
            .show()
    }

    private fun showProgressDialog(message: String) {
        progressDialog?.dismiss()

        progressDialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()

        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    // In MainActivity, ensure you're calling this method name consistently
    private fun handleAudioPlayRequest(message: ChatMessage) {
        // Check if the message has audio data
        if (message.audioData != null) {
            lifecycleScope.launch {
                // If we have cached audio data, play it
                val isPlaying = audioResponseHandler.getCurrentlyPlayingId() == message.id

                if (isPlaying) {
                    // If already playing this message, stop it
                    audioResponseHandler.stopAudio()
                    chatAdapter.updateAudioPlayingState(message.id, false)
                } else {
                    // Stop any currently playing audio
                    audioResponseHandler.stopAudio()

                    // Play this message's audio
                    val success = audioResponseHandler.playAudioFromBase64(
                        message.audioData.content,
                        message.id,
                        message.audioData.format
                    )

                    if (success) {
                        // Update UI to show playing state
                        chatAdapter.updateAudioPlayingState(message.id, true)

                        // Provide haptic feedback
                        provideHapticFeedback(50)

                        // Show toast - Fix: Get voice from preferences instead
                        val currentVoice = PrefsManager.getAudioVoice(this@MainActivity, "alloy")
                        Toast.makeText(
                            this@MainActivity,
                            "Playing audio with $currentVoice voice",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Show error
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to play audio",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            // If we don't have audio data, request it from the API
            requestAudioForMessage(message)
        }
    }


    /**
     * Method to request audio for a specific message from the API
     * @param message The chat message for which to generate audio
     */
    // Add this method to MainActivity
    private fun requestAudioForMessage(message: ChatMessage) {
        if (!ModelValidator.supportsAudio(message.modelId ?: ModelManager.selectedModel.id)) {
            showError("This model doesn't support audio output")
            return
        }

        // Check if audio is enabled
        val audioEnabled = PrefsManager.getAudioEnabled(this, false)
        if (!audioEnabled) {
            showError("Audio output is disabled. Enable it in settings.")
            return
        }

        lifecycleScope.launch {
            try {
                // Show loading state
                runOnUiThread {
                    showProgressDialog("Generating audio...")
                }

                val audioFormat = PrefsManager.getAudioFormat(this@MainActivity, "mp3")
                val voiceType = PrefsManager.getAudioVoice(this@MainActivity, "alloy")

                // Create audio request
                val audioRequest = createAudioRequest(
                    message = message.message,
                    modelId = message.modelId ?: ModelManager.selectedModel.id,
                    audioFormat = audioFormat,
                    voiceType = voiceType
                )

                // Create request body manually for TTS to ensure stream = false
                val requestJson = createTtsRequestJson(
                    audioRequest = audioRequest,
                    modelId = message.modelId ?: ModelManager.selectedModel.id,
                    audioFormat = audioFormat,
                    voiceType = voiceType
                )
                
                val requestBody = RequestBody.create(
                    "application/json; charset=utf-8".toMediaType(),
                    requestJson
                )

                // Make API request
                val response = aiChatService.makeAudioRequest(
                    modelId = message.modelId ?: ModelManager.selectedModel.id,
                    requestBody = requestBody
                )

                withContext(Dispatchers.Main) {
                    hideProgressDialog()

                    if (response != null) {
                        handleAudioResponse(response, message)
                    } else {
                        showError("Failed to generate audio. Please try again.")
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Error requesting audio: ${e.message}")
                withContext(Dispatchers.Main) {
                    hideProgressDialog()
                    showError("Error generating audio: ${e.message}")
                }
            }
        }
    }

    /**
     * Create TTS request JSON manually to avoid streaming override
     */
    private fun createTtsRequestJson(
        audioRequest: AimlApiRequest,
        modelId: String,
        audioFormat: String,
        voiceType: String
    ): String {
        // Log the TTS request details for debugging
        Timber.d("Creating TTS request for model: $modelId, format: $audioFormat, voice: $voiceType")
        
        val json = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                audioRequest.messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
            put("max_tokens", 10)  // Minimal tokens for TTS
            put("temperature", 0.1)  // Low temperature for consistent TTS
            put("stream", false)  // CRITICAL: Ensure no streaming for TTS
            
            // Check if AIML API supports modalities for this model
            if (modelId.contains("gpt-4") || modelId.contains("openai")) {
                put("modalities", JSONArray().apply {
                    put("text")
                    put("audio")
                })
                put("audio", JSONObject().apply {
                    put("voice", voiceType)
                    put("format", audioFormat)
                })
            }
        }
        
        val requestString = json.toString()
        Timber.d("TTS request JSON: $requestString")
        return requestString
    }

    /**
     * Create audio request for TTS
     */
    private fun createAudioRequest(
        message: String,
        modelId: String,
        audioFormat: String = "mp3",
        voiceType: String = "alloy"
    ): AimlApiRequest {
        // Get the model configuration
        val config = ModelConfigManager.getConfig(modelId)
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        // For TTS (Text-to-Speech), we need a specific request format
        // This should NOT be a streaming request and should not use chat completion format
        return AimlApiRequest(
            model = modelId, // Use the actual model ID (e.g., "openai/gpt-4.1-mini-2025-04-14")
            messages = listOf(
                AimlApiRequest.Message(
                    role = "user",
                    content = message  // Direct text input for TTS
                )
            ),
            maxTokens = 10,  // Minimal tokens since we want audio output
            temperature = 0.1,  // Low temperature for consistent TTS
            stream = false,  // CRITICAL: Must be false for TTS
            audio = AimlApiRequest.AudioOptions(
                format = audioFormat,
                voice = voiceType
            ),
            modalities = listOf("text", "audio"),  // Include both text and audio modalities
            // Required parameters
            topA = null,
            parallelToolCalls = false,
            minP = null,
            // TTS-specific settings
            useWebSearch = false,
            streamOptions = null  // Explicitly disable streaming options
        )
    }

    /**
     * Handle the audio response
     */
    private fun handleAudioResponse(response: AimlApiResponse, message: ChatMessage) {
        try {
            // Try multiple ways to extract audio data from response
            val audioData = response.choices?.firstOrNull()?.message?.audioData
                ?: response.audioData
                ?: response.audio

            if (audioData != null) {
                Timber.d("Received audio data: format=${audioData.format}, content length=${audioData.content?.length ?: 0}")
                
                // Check if we have audio content
                val audioContent = audioData.content ?: audioData.data
                if (audioContent != null && audioContent.isNotEmpty()) {
                    
                    // Validate if content is actually base64
                    val isValidBase64 = try {
                        android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                        true
                    } catch (e: IllegalArgumentException) {
                        false
                    }
                    
                    if (isValidBase64) {
                        // Play the audio using AudioResponseHandler
                        lifecycleScope.launch {
                            val success = audioResponseHandler.playAudioFromBase64(
                                audioBase64 = audioContent,
                                messageId = message.id,
                                format = audioData.format ?: "mp3"
                            )

                            if (success) {
                                Timber.d("Audio playback started for message: ${message.id}")
                                chatAdapter.updateAudioPlayingState(message.id, true)
                            } else {
                                showError("Failed to play audio")
                            }
                        }
                    } else {
                        Timber.e("Invalid base64 audio data received. Content starts with: ${audioContent.take(50)}")
                        showError("Invalid audio data format received from API")
                    }
                } else if (audioData.url != null || audioData.audioUrl != null) {
                    // Handle URL-based audio
                    val audioUrl = audioData.url ?: audioData.audioUrl
                    Timber.d("Received audio URL: $audioUrl")
                    showError("URL-based audio not yet supported. Please try again.")
                } else {
                    Timber.e("No audio content or URL in response")
                    showError("No audio content received from the API")
                }
            } else {
                Timber.e("No audio data in response structure")
                Timber.d("Response structure: ${response}")
                showError("No audio data received from the API")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling audio response: ${e.message}")
            showError("Error processing audio: ${e.message}")
        }
    }
    /**
     * Delete a conversation
     */
    private fun deleteConversation(conversation: Conversation) {
        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation?")
            .setPositiveButton("Delete") { _, _ ->
                conversations.remove(conversation)
                conversationAdapter.submitList(conversations.toList())
                Toast.makeText(this, "Conversation deleted.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Share a conversation
     */
    private fun shareConversation() {
        val shareText = chatAdapter.currentList.joinToString("\n") {
            "${it.timestamp}: ${it.message}"
        }
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            startActivity(Intent.createChooser(this, "Share Conversation"))
        }
    }
    private fun initializeTextExpansion() {
        try {
            btnExpandText = findViewById(R.id.btnExpandText)

            // Set up text change listener to show/hide expand button
            binding.etInputText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Update expand button visibility
                    updateExpandButtonVisibility()

                    // Your existing toggle button logic
                    toggleInputButtons(s?.toString()?.trim()?.isNotEmpty() == true)
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            // Set up expand button click listener
            btnExpandText.setOnClickListener {
                openMainTextExpansionActivity()
                provideHapticFeedback(50)
            }

            // Initially hide the expand button
            btnExpandText.visibility = View.GONE

            Timber.d("Text expansion functionality initialized")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing text expansion: ${e.message}")
        }
    }

    private fun updateExpandButtonVisibility() {
        try {
            if (!::btnExpandText.isInitialized) return

            val text = binding.etInputText.text.toString()
            val lineCount = binding.etInputText.lineCount
            val maxLines = 10

            val hasMaxLines = lineCount >= maxLines
            val isVeryLong = text.length > 500
            val hasManyLineBreaks = text.count { it == '\n' } >= 8

            val shouldShow = hasMaxLines || isVeryLong || hasManyLineBreaks

            btnExpandText.visibility = if (shouldShow) View.VISIBLE else View.GONE

            if (shouldShow) {
                btnExpandText.background = ContextCompat.getDrawable(this, R.drawable.circle_button_active)
                btnExpandText.setColorFilter(ContextCompat.getColor(this, R.color.white))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating expand button visibility: ${e.message}")
        }
    }
    private fun openMainTextExpansionActivity() {
        try {
            val currentText = binding.etInputText.text.toString()
            openTextSelectionActivity(currentText, true, "Edit Message", "input")
            provideHapticFeedback(50)
        } catch (e: Exception) {
            Timber.e(e, "Error opening text expansion activity: ${e.message}")
            Toast.makeText(this, "Error opening text editor", Toast.LENGTH_SHORT).show()
        }
    }
    fun openTextSelectionActivity(messageText: String, isEditable: Boolean, title: String, source: String = "input") {
        try {
            val intent = Intent(this, TextSelectionActivity::class.java).apply {
                putExtra("MESSAGE_TEXT", messageText)
                putExtra("IS_EDITABLE", isEditable)
                putExtra("TITLE", title)
                putExtra("SOURCE", source) // Add source parameter to track origin

                // Add a hint that is appropriate for the mode
                putExtra("HINT", if (isEditable) "Edit your message here..." else "Long press to select text")
            }

            // Set the flag before starting activity
            isReturningFromTextSelection = true

            // Use modern Activity Result API instead of startActivityForResult
            when {
                // If from edit dialog, use edit launcher
                source == "dialog" -> editTextExpansionLauncher.launch(intent)
                // If editable, use main expansion launcher
                isEditable -> textExpansionLauncher.launch(intent)
                // Otherwise use view-only launcher
                else -> textViewLauncher.launch(intent)
            }
            Timber.d("Opening TextSelectionActivity: editable=$isEditable, source=$source")
        } catch (e: Exception) {
            Timber.e(e, "Error opening text selection activity: ${e.message}")
            Toast.makeText(this, "Error opening text viewer", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Open text expansion activity for edit dialog
     */
    private fun openEditTextExpansionActivity(currentText: String, originalMessage: ChatMessage) {
        try {
            editDialogOriginalMessage = originalMessage
            openTextSelectionActivity(currentText, true, "Edit Message", "dialog")
        } catch (e: Exception) {
            Timber.e(e, "Error opening edit text expansion activity: ${e.message}")
            Toast.makeText(this, "Error opening text editor", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Reset chat to new conversation state
     */
    private fun resetChat() {
        currentConversationId = null
        // Reset draft loaded flag for new conversation
        isDraftContentLoaded = false

        // Clear all message state by submitting empty list
        chatAdapter.submitList(emptyList())
        
        // CRITICAL FIX: Clear all draft-related UI state
        binding.etInputText.text?.clear()
        selectedFiles.clear()
        binding.selectedFilesScrollView.visibility = View.GONE
        fileHandler.updateSelectedFilesView()
        
        // Reset input UI state
        binding.btnMicrophone.visibility = View.VISIBLE
        binding.btnSubmitText.visibility = View.GONE

        // Set appropriate title based on private mode
        if (isPrivateModeEnabled) {
            chatTitleView.text = "Private Chat"
        } else {
            chatTitleView.text = "New Chat"
        }

        binding.btnNewConversation.isEnabled = true

        // Also reset generation state
        isGenerating = false
        chatAdapter.setGenerating(false)
    }   /**
     * Update the new conversation button state
     */
    private fun updateNewConversationButtonState() {
        // Enable the button if:
        // 1. We have a current conversation (regardless of messages), or
        // 2. We're in a new chat but no conversation ID
        binding.btnNewConversation.isEnabled = (currentConversationId != null) ||
                (currentConversationId == null)
    }
    /**
     * Set the selected model in the spinner
     */
    private fun setSelectedModel(modelIdentifier: String) {
        // Încearcă mai întâi după ID (recomandat)
        var modelIndex = ModelManager.models.indexOfFirst { it.id == modelIdentifier }

        // Dacă nu găsim după ID, încearcă după nume (pentru compatibilitate)
        if (modelIndex == -1) {
            modelIndex = ModelManager.models.indexOfFirst { it.displayName == modelIdentifier }
        }

        if (modelIndex != -1) {
            binding.spinnerModels.setSelection(modelIndex)
            ModelManager.selectedModel = ModelManager.models[modelIndex]
            updateCreditsVisibility()

            // Update UI based on model
            ModelValidator.clearCache()
            updateControlsVisibility(ModelManager.selectedModel.id)
            applyModelColorToUI(ModelManager.selectedModel.id)
            chatAdapter.updateCurrentModel(ModelManager.selectedModel.id)

            Timber.d("Model set to: ${ModelManager.selectedModel.displayName} (${ModelManager.selectedModel.id})")
        } else {
            Timber.e("Model not found: $modelIdentifier, using default")
            // Use default model as fallback
            loadDefaultModel()
        }
    }

    fun updateControlsVisibility(modelId: String) {
        try {
            Timber.d("=== UPDATING CONTROLS VISIBILITY FOR $modelId ===")

            // Check if all UI components are initialized
            if (!::btnReasoning.isInitialized ||
                !::btnDeepSearch.isInitialized ||
                !::btnAudioConversation.isInitialized ||
                !::reasoningControlsLayout.isInitialized) {

                Timber.w("UI components not yet initialized, deferring controls update")
                Handler(Looper.getMainLooper()).postDelayed({
                    updateControlsVisibility(modelId)
                }, 100)
                return
            }

            // Clear cache to ensure fresh checks
            ModelValidator.clearCache()

            // ✅ FIXED: Check capabilities with debug logging
            val supportsWebSearch = ModelValidator.supportsWebSearch(modelId)
            val supportsReasoning = ModelConfigManager.supportsReasoning(modelId)
            val isOcrModel = ModelValidator.isOcrModel(modelId)

            Timber.d("Model capabilities:")
            Timber.d("  - Web Search: $supportsWebSearch")
            Timber.d("  - Reasoning: $supportsReasoning")
            Timber.d("  - OCR Model: $isOcrModel")

            // Update web search button (disabled for OCR models)
            btnDeepSearch.visibility = if (supportsWebSearch && !isOcrModel) View.VISIBLE else View.GONE
            btnDeepSearch.isEnabled = supportsWebSearch && !isOcrModel

            // Update reasoning button based on model capabilities (disabled for OCR models)
            btnReasoning.visibility = if (supportsReasoning && !isOcrModel) View.VISIBLE else View.GONE
            btnReasoning.isEnabled = supportsReasoning && !isOcrModel

            // Update reasoning controls layout visibility (hidden for OCR models)
            reasoningControlsLayout.visibility = if (supportsReasoning && !isOcrModel) View.VISIBLE else View.GONE

            // Update UI for OCR models - disable text input and show appropriate hint
            if (isOcrModel) {
                binding.etInputText.hint = "📄 Upload a PDF or image file for OCR processing"
                binding.etInputText.isEnabled = false
                binding.btnSubmitText.isEnabled = false
                binding.ocrOptionsPanel.visibility = View.VISIBLE
                
                // Clear any existing text for OCR models
                if (binding.etInputText.text.toString().isNotEmpty()) {
                    binding.etInputText.setText("")
                }
            } else {
                binding.etInputText.hint = "Type a message..."
                binding.etInputText.isEnabled = true
                binding.ocrOptionsPanel.visibility = View.GONE
                // Send button enablement is controlled by the text watcher
            }


        } catch (e: Exception) {
            Timber.e(e, "Error updating controls visibility: ${e.message}")
            // Set safe defaults
            if (::reasoningControlsLayout.isInitialized) {
                reasoningControlsLayout.visibility = View.GONE
            }
        }
    }


    fun showTranslatorUI() {
        try {
            // Check if required properties are initialized
            if (!::binding.isInitialized) {
                Timber.e("Cannot show translator UI: binding not initialized")
                Toast.makeText(this, "Cannot show translator UI at this time", Toast.LENGTH_SHORT).show()
                return
            }

            // Save current model to restore later
            preTranslationModelId = ModelManager.selectedModel.id

            // Set translation mode active
            isTranslationModeActive = true
            (binding.spinnerModels.adapter as? ModelSpinnerAdapter)?.notifyDataSetChanged()

            // Switch to optimal translation model
            setModelForTranslation()

            // Create translator UI overlay
            translatorLayout = layoutInflater.inflate(R.layout.translator_overlay, null)
            translatorLayout!!.findViewById<LinearLayout>(R.id.translatorOverlay)

            // Get reference to parent layout
            val parentLayout = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? ViewGroup
                ?: return

            findViewById<View>(R.id.inputCardLayout) ?: return

            // Add translator layout above input card layout
            if (parentLayout is androidx.constraintlayout.widget.ConstraintLayout) {
                val params = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                params.bottomToTop = R.id.inputCardLayout
                params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomMargin = resources.getDimensionPixelSize(R.dimen.small_margin)

                translatorLayout!!.layoutParams = params
                parentLayout.addView(translatorLayout)
                translatorLayout!!.visibility = View.VISIBLE

                Timber.d("Translator UI added above credits layout")
            } else {
                parentLayout.addView(translatorLayout)
                Timber.d("Translator UI added to parent (fallback mode)")
            }

            // Get references to UI elements
            sourceLanguageSpinner = translatorLayout!!.findViewById(R.id.spinnerFromLanguage)
            targetLanguageSpinner = translatorLayout!!.findViewById(R.id.spinnerToLanguage)
            val btnSwap = translatorLayout!!.findViewById<ImageButton>(R.id.btnSwapLanguages)
            val btnClose = translatorLayout!!.findViewById<ImageButton>(R.id.btnCloseTranslator)

            // Set up language spinners with auto-detect option
            val languages = mutableListOf("Auto-detect").apply {
                addAll(resources.getStringArray(R.array.languages))
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            sourceLanguageSpinner!!.adapter = adapter
            targetLanguageSpinner!!.adapter = adapter

            // Set default selections (Auto-detect -> English)
            sourceLanguageSpinner!!.setSelection(0) // Auto-detect
            targetLanguageSpinner!!.setSelection(adapter.getPosition("English"))

            // Set up swap button
            btnSwap.setOnClickListener {
                val fromPos = sourceLanguageSpinner!!.selectedItemPosition
                val toPos = targetLanguageSpinner!!.selectedItemPosition

                // Don't swap if source is auto-detect
                if (fromPos == 0) {
                    Toast.makeText(this, "Cannot swap when auto-detect is selected", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                sourceLanguageSpinner!!.setSelection(toPos)
                targetLanguageSpinner!!.setSelection(fromPos)

                if (::vibrator.isInitialized) {
                    provideHapticFeedback(50)
                }
            }

            // Set up close button
            btnClose.setOnClickListener {
                // Disable the button immediately to prevent double-clicks
                btnClose.isEnabled = false

                // Set a visual indicator that we're processing
                btnClose.alpha = 0.5f

                // Execute synchronously without any delay
                translatorLayout?.visibility = View.GONE

                // Execute the close immediately
                closeTranslatorMode()
            }

            // Show initial AI message about translator
            showTranslatorWelcomeMessage()

            // Set up translation submit listener (override the normal one)
            setupTranslationSubmitListener()

            // Update input hint
            binding.etInputText.hint = "Type text to translate..."

            // Update language change listener to update welcome message
            val languageChangeListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // Update welcome message when languages change
                    Handler(Looper.getMainLooper()).postDelayed({
                        updateTranslatorWelcomeMessage()
                    }, 100)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            sourceLanguageSpinner!!.onItemSelectedListener = languageChangeListener
            targetLanguageSpinner!!.onItemSelectedListener = languageChangeListener

        } catch (e: Exception) {
            Timber.e(e, "Error showing translator UI: ${e.message}")
            Toast.makeText(this, "Error showing translator: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setModelForTranslation() {
        // Check if current model is good for translation
        if (!TranslationUtils.supportsTranslation(ModelManager.selectedModel.id)) {
            // Switch to default translation model
            setSelectedModelInSpinner(TranslationUtils.DEFAULT_TRANSLATION_MODEL)
            Toast.makeText(
                this,
                "Switched to ${ModelManager.selectedModel.displayName} for better translation",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showTranslatorWelcomeMessage() {
        val sourceLanguage = sourceLanguageSpinner!!.selectedItem.toString()
        val targetLanguage = targetLanguageSpinner!!.selectedItem.toString()

        val welcomeText = if (sourceLanguage == "Auto-detect") {
            "I'm ready to translate your text to $targetLanguage. I'll automatically detect the source language."
        } else {
            "I'm ready to translate your text from $sourceLanguage to $targetLanguage."
        }

        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = createOrGetConversation(""),
            message = welcomeText,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )

        // Add the message to the chat
        val currentList = chatAdapter.currentList.toMutableList()
        currentList.add(aiMessage)
        chatAdapter.submitList(currentList)

        // Save the message if not in private mode
        if (!isPrivateModeEnabled) {
            conversationsViewModel.saveMessage(aiMessage)
        }

        scrollToBottom()
    }

    private fun updateTranslatorWelcomeMessage() {
        if (!isTranslationModeActive) return

        val sourceLanguage = sourceLanguageSpinner?.selectedItem?.toString() ?: return
        val targetLanguage = targetLanguageSpinner?.selectedItem?.toString() ?: return

        val updatedText = if (sourceLanguage == "Auto-detect") {
            "I'm ready to translate your text to $targetLanguage. I'll automatically detect the source language."
        } else {
            "I'm ready to translate your text from $sourceLanguage to $targetLanguage."
        }

        // Find the last AI message and update it if it's a welcome message
        val currentList = chatAdapter.currentList.toMutableList()
        val lastAiMessage = currentList.findLast { !it.isUser }

        if (lastAiMessage != null && (lastAiMessage.message.contains("I'm ready to translate") ||
                    lastAiMessage.message.contains("ready to translate"))) {

            val updatedMessage = lastAiMessage.copy(message = updatedText)
            val index = currentList.indexOfLast { it.id == lastAiMessage.id }

            if (index != -1) {
                currentList[index] = updatedMessage
                chatAdapter.submitList(currentList)

                // Save updated message if not in private mode
                if (!isPrivateModeEnabled) {
                    conversationsViewModel.saveMessage(updatedMessage)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setupTranslationSubmitListener() {
        // Store original click listener if not already saved
        if (originalSubmitListener == null) {
            // Save the current click listener by creating a new one that captures the behavior
            val currentListener = binding.btnSubmitText.onClickListener
            originalSubmitListener = if (currentListener != null) {
                currentListener
            } else {
                // If no listener yet, create a default one
                View.OnClickListener {
                    if (isGenerating) {
                        stopGeneration()
                    } else {
                        sendMessage()
                    }
                }
            }
        }

        // Set up the translation-specific submit behavior
        binding.btnSubmitText.setOnClickListener {
            handleTranslationSubmit()
        }
    }

    // Helper extension property to get the current click listener
    private val View.onClickListener: View.OnClickListener?
        get() = try {
            val field = View::class.java.getDeclaredField("mListenerInfo")
            field.isAccessible = true
            val listenerInfo = field.get(this)
            val listenerInfoClass = Class.forName("android.view.View\$ListenerInfo")
            val onClickListenerField = listenerInfoClass.getDeclaredField("mOnClickListener")
            onClickListenerField.isAccessible = true
            onClickListenerField.get(listenerInfo) as? View.OnClickListener
        } catch (e: Exception) {
            Timber.e(e, "Error getting click listener: ${e.message}")
            null
        }


    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun handleTranslationSubmit() {
        val text = binding.etInputText.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val sourceLanguage = sourceLanguageSpinner!!.selectedItem.toString()
        val targetLanguage = targetLanguageSpinner!!.selectedItem.toString()

        // Clear the input first
        binding.etInputText.text?.clear()

        // Send the raw text as user message
        sendTranslationMessage(text, sourceLanguage, targetLanguage)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun sendTranslationMessage(text: String, sourceLanguage: String, targetLanguage: String) {
        // Generate a unique ID for this user message
        val userMessageId = UUID.randomUUID().toString()

        // Create user message with the raw text
        val userMessage = ChatMessage(
            id = userMessageId,
            conversationId = createOrGetConversation(text),
            message = text, // Just the raw text, no translation instruction
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        // Add user message to chat
        val currentList = chatAdapter.currentList.toMutableList()
        currentList.add(userMessage)
        chatAdapter.submitList(currentList)

        // Save user message
        if (!isPrivateModeEnabled) {
            conversationsViewModel.saveMessage(userMessage)
        }

        // Update conversation with the latest user message
        updateConversationWithLatestMessage(text)

        // Scroll to bottom after sending
        scrollToBottom()

        // Provide haptic feedback
        provideHapticFeedback(50)

        // Hide keyboard
        hideKeyboard()

        // Set UI to generating state
        setGeneratingState(true)

        // Create translation context for the AI
        val translationContext = createTranslationContext(sourceLanguage, targetLanguage)

        // Generate AI response with translation context
        aiChatService.sendTranslationMessage(
            userText = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            translationContext = translationContext,
            userMessageId = userMessageId,
            conversationId = userMessage.conversationId
        )

        // Update message sent state
        isMessageSent = true
        binding.btnNewConversation.isEnabled = true
    }

    private fun createTranslationContext(sourceLanguage: String, targetLanguage: String): String {
        return if (sourceLanguage == "Auto-detect") {
            "You are currently in translation mode. The user wants you to translate their text to $targetLanguage. " +
                    "Auto-detect the source language. Provide the translation, then a brief explanation if the text contains " +
                    "any idiomatic expressions or cultural context. If the user asks questions about the translation or the text, " +
                    "answer them while maintaining awareness of the translation context."
        } else {
            "You are currently in translation mode. The user wants you to translate their text from $sourceLanguage to $targetLanguage. " +
                    "Provide the translation, then a brief explanation if the text contains any idiomatic expressions or cultural context. " +
                    "If the user asks questions about the translation or the text, answer them while maintaining awareness of the translation context."
        }
    }

    private fun closeTranslatorMode() {
        try {
            // First set flag to inactive
            isTranslationModeActive = false

            // Get parent container - this should be the root ConstraintLayout or whatever ViewGroup holds the translator
            val parentLayout = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? ViewGroup
            if (parentLayout == null) {
                Timber.e("Cannot find parent layout to remove translator")
                return
            }

            // Hard-coded direct removal by ID if we have it
            val translatorById = findViewById<View>(R.id.translatorOverlay)
            if (translatorById != null) {
                parentLayout.removeView(translatorById)
                Timber.d("Removed translator layout by ID")
            }

            // Also try to remove our stored layout reference
            if (translatorLayout != null) {
                parentLayout.removeView(translatorLayout)
                Timber.d("Removed translator layout from stored reference")
            }

            // Last resort: find ALL LinearLayouts with ID translatorOverlay and remove them
            for (i in 0 until parentLayout.childCount) {
                val child = parentLayout.getChildAt(i)
                if (child.id == R.id.translatorOverlay ||
                    (child is LinearLayout && child.findViewById<View>(R.id.spinnerFromLanguage) != null)) {
                    parentLayout.removeView(child)
                    Timber.d("Removed translator layout by search")
                    break
                }
            }

            // Absolutely ensure it's gone by doing a brute-force search for any translator components
            for (i in 0 until parentLayout.childCount) {
                val child = parentLayout.getChildAt(i)
                // Look for any view that might be part of our translator
                if (child is ViewGroup &&
                    (child.findViewById<View>(R.id.spinnerFromLanguage) != null ||
                            child.findViewById<View>(R.id.spinnerToLanguage) != null ||
                            child.findViewById<View>(R.id.btnSwapLanguages) != null)) {
                    parentLayout.removeView(child)
                    Timber.d("Removed translator with component search")
                    break
                }
            }

            // Reset all references
            translatorLayout = null
            sourceLanguageSpinner = null
            targetLanguageSpinner = null

            // Restore original submit listener
            originalSubmitListener?.let {
                binding.btnSubmitText.setOnClickListener(it)
            }
            originalSubmitListener = null

            // Reset input hint
            binding.etInputText.hint = "Type a message..."

            // Restore previous model if it was changed
            preTranslationModelId?.let { modelId ->
                if (modelId != ModelManager.selectedModel.id) {
                    setSelectedModelInSpinner(modelId)
                }
            }
            preTranslationModelId = null

            // Force complete UI refresh
            binding.root.invalidate()
            binding.root.requestLayout()

            // Send AI message about exiting translation mode
            val exitMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = createOrGetConversation(""),
                message = "Translation mode disabled. I'm now ready for regular conversation.",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )

            val currentList = chatAdapter.currentList.toMutableList()
            currentList.add(exitMessage)
            chatAdapter.submitList(currentList)

            if (!isPrivateModeEnabled) {
                conversationsViewModel.saveMessage(exitMessage)
            }

            scrollToBottom()

            // Haptic feedback
            if (::vibrator.isInitialized) {
                provideHapticFeedback(50)
            }

            Toast.makeText(this, "Translation mode closed", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Timber.e(e, "Error closing translator mode: ${e.message}")

            // Force cleanup even if there's an error
            isTranslationModeActive = false
            translatorLayout = null
            sourceLanguageSpinner = null
            targetLanguageSpinner = null
        }
    }    // Modified to NOT exit translation mode when starting a new conversation
    // Modified to NOT exit translation mode when starting a new conversation
    /**
     * Starts a new conversation, resetting the chat state
     */
    private fun startNewConversation() {

        // Stop any ongoing generation
        if (isGenerating) {
            stopGeneration()
            // Wait a moment for cleanup
            uiHandler.postDelayed({
                proceedWithNewConversation()
            }, 300)
        } else {
            proceedWithNewConversation()
        }
        // Check if we're already in a new conversation with no messages
        if (currentConversationId == null) {
            // CRITICAL FIX: Still reset UI state even if no conversation
            resetChat()
            provideHapticFeedback(50)
            binding.etInputText.post {
                binding.etInputText.requestFocus()
                forceShowKeyboard()
            }
            Toast.makeText(this@MainActivity, "Type your message", Toast.LENGTH_SHORT).show()
            return
        }



        // CRITICAL FIX: Save token state for current conversation before resetting
        if (currentConversationId != null) {
            conversationTokenManager.setConversationId(currentConversationId!!)
        }

        // Reset conversation ID first, then reset token manager
        currentConversationId = null
        conversationTokenManager.reset()

        // Clear message state
        chatAdapter.submitList(emptyList())

        // Remove warning badge if present
        hideTokenLimitWarningBadge()

        // Retrieve user name to be used in conversation context
        val userName = PrefsManager.getUserName(this) ?: "user"

        // Store the user name for the new conversation
        // We'll use this when creating the first message
        conversationMetadata["user_name"] = userName

        // Set appropriate title based on mode
        if (isPrivateModeEnabled) {
            chatTitleView.text = "Private Chat"
        } else if (isTranslationModeActive) {
            chatTitleView.text = "Translation Mode"
        } else {
            // Include the user's name in the title if available
            if (userName != "user") {
                chatTitleView.text = "$userName's Chat"
            } else {
                chatTitleView.text = "New Chat"
            }
        }

        // Reset UI state
        binding.btnNewConversation.isEnabled = true
        isGenerating = false
        chatAdapter.setGenerating(false)
        isMessageSent = false

        // Update token counter for new conversation
        updateTokenCounterUI()

        // Show keyboard
        binding.etInputText.post {
            binding.etInputText.requestFocus()
            forceShowKeyboard()
        }

        // Provide feedback
        provideHapticFeedback(50)
        Toast.makeText(this, "New conversation started - type your message", Toast.LENGTH_SHORT).show()

        if (::scrollArrowsHelper.isInitialized) {
            scrollArrowsHelper.updateButtonsState()
        }
    }

    private fun proceedWithNewConversation() {
        // Ensure we're not generating
        isGenerating = false
        chatAdapter.setGenerating(false)

        // Stop any monitoring
        stopAggressiveButtonMonitoring()
        
        // CRITICAL FIX: Use resetChat to properly clear all state including drafts
        resetChat()
        
        // Reset conversation state (redundant but keeping for safety)
        currentConversationId = null
        conversationTokenManager.reset()

        // Remove warning badge if present
        hideTokenLimitWarningBadge()

        // Set appropriate title based on mode
        if (isPrivateModeEnabled) {
            chatTitleView.text = "Private Chat"
        } else if (isTranslationModeActive) {
            chatTitleView.text = "Translation Mode"
        } else {
            val userName = PrefsManager.getUserName(this) ?: "user"
            chatTitleView.text = if (userName != "user") "$userName's Chat" else "New Chat"
        }

        // Reset UI state completely (redundant but keeping for safety)
        binding.btnNewConversation.isEnabled = true
        isMessageSent = false

        // Reset button to proper state
        val inputText = binding.etInputText.text.toString().trim()
        if (inputText.isEmpty()) {
            binding.btnMicrophone.visibility = View.VISIBLE
            binding.btnSubmitText.visibility = View.GONE
        } else {
            binding.btnMicrophone.visibility = View.GONE
            binding.btnSubmitText.visibility = View.VISIBLE
            binding.btnSubmitText.setImageResource(R.drawable.send_icon)
            binding.btnSubmitText.contentDescription = "Send message"
        }

        // Ensure no animation is running
        stopPulsingAnimation()

        // Update token counter
        updateTokenCounterUI()

        // Show keyboard and provide feedback
        binding.etInputText.post {
            binding.etInputText.requestFocus()
            forceShowKeyboard()
        }

        provideHapticFeedback(50)
        Toast.makeText(this, "New conversation started - type your message", Toast.LENGTH_SHORT).show()

        if (::scrollArrowsHelper.isInitialized) {
            scrollArrowsHelper.updateButtonsState()
        }
    }

    // 6. Recovery method for button state
    private fun recoverButtonState() {
        try {
            if (isGenerating) {
                // Should be in stop mode
                binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                binding.btnSubmitText.contentDescription = "Stop generation"
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnMicrophone.visibility = View.GONE
                startPulsingAnimation()
            } else {
                // Should be in normal mode
                val inputText = binding.etInputText.text.toString().trim()
                if (inputText.isEmpty()) {
                    binding.btnMicrophone.visibility = View.VISIBLE
                    binding.btnSubmitText.visibility = View.GONE
                } else {
                    binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                    binding.btnSubmitText.contentDescription = "Send message"
                    binding.btnSubmitText.visibility = View.VISIBLE
                    binding.btnMicrophone.visibility = View.GONE
                }
                stopPulsingAnimation()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in recoverButtonState: ${e.message}")
        }
    }

    private fun proceedWithModelChange(selectedModel: AIModel, preferences: ResponsePreferences) {
        // Reset chat and update model
        resetChat()
        ModelManager.selectedModel = selectedModel

        // CRITICAL FIX: Clear cache BEFORE checking capabilities
        ModelValidator.clearCache()

        // Update the ChatAdapter with the new model ID
        chatAdapter.updateCurrentModel(selectedModel.id)

        // Apply color changes to UI elements
        applyModelColorToUI(selectedModel.id)

        // CRITICAL FIX: Update controls visibility synchronously (NOT in a handler)
        updateControlsVisibility(selectedModel.id)

        // Update token limits for the new model
        updateTokenLimitForModel(selectedModel.id)

        // Update credits visibility
        updateCreditsVisibility()

        // Reset title to "New Chat" when model changes, but preserve Translation Mode if active
        if (isTranslationModeActive) {
            chatTitleView.text = "Translation Mode"
        } else if (isPrivateModeEnabled) {
            chatTitleView.text = "Private Chat"
        } else {
            chatTitleView.text = "New Chat"
        }

        // Show toast if response preferences were changed
        if (preferences.length != ResponseLength.DEFAULT || preferences.tone != ResponseTone.DEFAULT) {
            val message = StringBuilder("Response preferences updated: ")
            if (preferences.length != ResponseLength.DEFAULT) {
                message.append("${preferences.length.displayName} length")
            }
            if (preferences.tone != ResponseTone.DEFAULT) {
                if (preferences.length != ResponseLength.DEFAULT) message.append(", ")
                message.append("${preferences.tone.displayName} tone")
            }
            Toast.makeText(this@MainActivity, message.toString(), Toast.LENGTH_SHORT).show()
        }

        // Set the selected model as default
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        sharedPrefs.edit {
            putString("default_ai_model_id", selectedModel.id)
            apply()
        }
    }

    private fun setupScrollArrows() {
        try {
            // Initialize scroll arrows helper
            scrollArrowsHelper = ScrollArrowsHelper.setup(
                context = this,
                recyclerView = binding.chatRecyclerView,
                fabScrollToTop = binding.fabScrollToTop,
                fabScrollToBottom = binding.fabScrollToBottom
            )

            Timber.d("Scroll arrows setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up scroll arrows: ${e.message}")
        }
    }

    /**
     * Set up token monitoring for the conversation
     */
    private fun setupTokenMonitoring() {
        try {
            val tvTokenCount = findViewById<TextView>(R.id.tvTokenCount)
            if (tvTokenCount == null) {
                Timber.e("Token counter TextView not found in layout")
                return
            }

            // Update initial state
            updateTokenCounterUI()

            // Improved text watcher that always uses current model
            binding.etInputText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        // Always use current model ID, not cached one
                        val currentModelId = ModelManager.selectedModel.id
                        val isSubscribed = PrefsManager.isSubscribed(this@MainActivity)

                        TokenCounterHelper.updateTokenCounter(
                            tvTokenCount,
                            s?.toString() ?: "",
                            currentModelId,  // Use current model
                            isSubscribed,
                            this@MainActivity,
                            conversationTokenManager
                        )

                        // Check token limits with current message
                        val text = s?.toString() ?: ""
                        if (text.isNotEmpty() && text.length > 100) { // Only check for longer messages
                            val result = conversationTokenManager.wouldExceedLimit(text, currentModelId, isSubscribed)
                            result.first
                            val almostFull = result.third

                            if (almostFull && !conversationTokenManager.hasContinuedPastWarning()) {
                                val percentage = conversationTokenManager.getTokenUsagePercentage(currentModelId, isSubscribed)
                                if (percentage > 0.8f) { // 80% threshold
                                    showTokenWarningIfNeeded(percentage)
                                }
                            }
                        }

                        // Toggle buttons based on text content
                        toggleInputButtons(s?.toString()?.trim()?.isNotEmpty() == true)

                    } catch (e: Exception) {
                        Timber.e(e, "Error in text watcher: ${e.message}")
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            // Long press tooltip
            tvTokenCount.setOnLongClickListener {
                try {
                    val currentModelId = ModelManager.selectedModel.id
                    val isSubscribed = PrefsManager.isSubscribed(this@MainActivity)
                    val message = TokenCounterHelper.getTokenLimitExplanation(
                        currentModelId,
                        isSubscribed,
                        conversationTokenManager
                    )
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Timber.e(e, "Error showing token explanation: ${e.message}")
                }
                true
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in setupTokenMonitoring: ${e.message}")
        }

    }

    private fun toggleInputButtons(hasText: Boolean) {
        try {
            // First, check if we're in generating state - if so, don't change the button
            if (isGenerating) {
                // We're generating, so maintain the stop button state
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                binding.btnSubmitText.contentDescription = "Stop generation"
                return
            }

            // Check if current model is OCR model
            val currentModelId = ModelManager.selectedModel.id
            val isOcrModel = ModelValidator.isOcrModel(currentModelId)
            
            // For OCR models, always show send button but enable only when files are selected
            if (isOcrModel) {
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                binding.btnSubmitText.contentDescription = "Process OCR"
                binding.btnSubmitText.isEnabled = selectedFiles.isNotEmpty()
                return
            }

            // Not generating, proceed with normal button state based on text content
            if (hasText) {
                // Has text - show send button
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                // IMPORTANT: Explicitly set the send icon
                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                binding.btnSubmitText.contentDescription = "Send message"
            } else {
                // No text - show microphone button
                binding.btnSubmitText.visibility = View.GONE
                binding.btnMicrophone.visibility = View.VISIBLE
            }

            // Log the button state change for debugging
            Timber.d("toggleInputButtons: hasText=$hasText, isGenerating=$isGenerating, " +
                    "btnSubmitText.visibility=${binding.btnSubmitText.visibility}, " +
                    "contentDescription=${binding.btnSubmitText.contentDescription}")
        } catch (e: Exception) {
            Timber.e(e, "Error in toggleInputButtons: ${e.message}")
        }
    }

    /**
     * Get OCR options from the UI controls
     */
    private fun getOcrOptionsFromUI(): OCROptions {
        return try {
            val pages = binding.etOcrPages.text?.toString()?.trim()?.ifEmpty { null }
            val includeImages = if (binding.cbIncludeImages.isChecked) true else null
            val imageLimit = binding.etImageLimit.text?.toString()?.trim()?.toIntOrNull()
            val imageMinSize = binding.etImageMinSize.text?.toString()?.trim()?.toIntOrNull()
            
            OCROptions(
                pages = pages,
                includeImageBase64 = includeImages,
                imageLimit = imageLimit,
                imageMinSize = imageMinSize
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting OCR options from UI: ${e.message}")
            OCROptions() // Return default options on error
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun showTokenWarningIfNeeded(percentage: Float) {
        val currentTime = System.currentTimeMillis()
        // Only show warning every 10 seconds to avoid spam
        if (currentTime - lastWarningTime > 10000 && !isFinishing && !isDestroyed) {
            lastWarningTime = currentTime

            TokenLimitDialogHandler.showTokenLimitApproachingDialog(
                this,
                percentage,
                onContinue = { handleContinueAnyway() },
                onSummarize = { summarizeAndStartNewChat() },
                onNewChat = { startNewConversation() }
            )
        }
    }



    /**
     * Handle when user selects Continue Anyway
     */
    private fun handleContinueAnyway() {
        // Set the flag for reduced token reservation
        conversationTokenManager.setContinuedPastWarning(true)

        // Show persistent warning badge
        showTokenLimitWarningBadge()

        // Update token counter to show warning color
        val currentText = binding.etInputText.text?.toString() ?: ""
        val isSubscribed = PrefsManager.isSubscribed(this)
        val modelId = ModelManager.selectedModel.id

        TokenCounterHelper.updateTokenCounter(
            findViewById(R.id.tvTokenCount),
            currentText,
            modelId,
            isSubscribed,
            this,
            conversationTokenManager
        )

        // Show toast with brief explanation
        Toast.makeText(
            this,
            "AI responses may be shorter due to limited token space",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Show a persistent warning badge
     */
    private fun showTokenLimitWarningBadge() {
        // Only create if not already showing
        if (tokenLimitWarningBadge == null) {
            tokenLimitWarningBadge = layoutInflater.inflate(
                R.layout.token_limit_warning_badge,
                binding.root,
                false
            )

            // Add to the root layout
            binding.root.addView(tokenLimitWarningBadge)

            // Animate in
            tokenLimitWarningBadge?.alpha = 0f
            tokenLimitWarningBadge?.animate()
                ?.alpha(1f)
                ?.setDuration(300)
                ?.start()
        }
    }

    /**
     * Hide token limit warning badge
     */
    private fun hideTokenLimitWarningBadge() {
        tokenLimitWarningBadge?.let { badge ->
            badge.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.root.removeView(badge)
                    tokenLimitWarningBadge = null
                }
                .start()
        }
    }

    /**
     * Handles summarizing the conversation and starting a new chat
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun summarizeAndStartNewChat() {
        // Show loading indicator
        showLoadingDialog("Summarizing conversation...")

        lifecycleScope.launch {
            try {
                // Get all messages for current conversation
                val messages = messageManager.getCurrentMessages()

                // Create summarization prompt
                val summarizationPrompt = ConversationSummarizer.createSummarizationPrompt(messages)

                // Find best model for summarization
                val promptTokens = TokenValidator.estimateTokenCount(summarizationPrompt)
                val bestModelId = ConversationSummarizer.getBestModelForSummarization(
                    promptTokens,
                    PrefsManager.isSubscribed(this@MainActivity)
                )

                // Temporarily switch to that model if needed
                val originalModelId = ModelManager.selectedModel.id
                if (bestModelId != originalModelId) {
                    // Switch model
                    setSelectedModelInSpinner(bestModelId)
                }

                // Generate summary using AI service
                val userMessageId = UUID.randomUUID().toString()
                val conversationId = createOrGetConversation(summarizationPrompt)

                // Create user message with summarization prompt
                val userMessage = ChatMessage(
                    id = userMessageId,
                    conversationId = conversationId,
                    message = summarizationPrompt,
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                )

                // Add to chat - but don't display
                messageManager.addMessage(userMessage, addToAdapter = false)

                // Create AI message for summary
                val aiMessageId = UUID.randomUUID().toString()
                val aiMessage = ChatMessage(
                    id = aiMessageId,
                    conversationId = conversationId,
                    message = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isGenerating = true,
                    showButtons = false,
                    modelId = ModelManager.selectedModel.id,
                    aiModel = ModelManager.selectedModel.displayName,
                    parentMessageId = userMessageId,
                )

                // Add to chat - but don't display
                messageManager.addMessage(aiMessage, addToAdapter = false)

                // Generate summary (this is a synchronous wait)
                val summaryJob = aiChatService.startApiCall(
                    conversationId = conversationId,
                    messageId = aiMessageId,
                    isSubscribed = PrefsManager.isSubscribed(this@MainActivity),
                    isModelFree = ModelManager.selectedModel.isFree,
                )

                // Wait for summary to be generated
                summaryJob.join()

                // Get the summary from the message
                val summary = messageManager.getMessageById(aiMessageId)?.message ?: ""

                // Reset to original model if we changed it
                if (bestModelId != originalModelId) {
                    setSelectedModelInSpinner(originalModelId)
                }

                // Create a new conversation with the summary as first message
                val newConversationId = createOrGetConversation(summary)

                // Create continuation message
                val continuationPrompt = ConversationSummarizer.createContinuationMessage(summary)
                val continuationMessageId = UUID.randomUUID().toString()

                // Add system-generated user message with summary
                val continuationMessage = ChatMessage(
                    id = continuationMessageId,
                    conversationId = newConversationId,
                    message = continuationPrompt,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )

                // Add to chat and display
                messageManager.addMessage(continuationMessage)

                // Reset token counter for new conversation
                conversationTokenManager.reset()

                // Add the first message to token counter
                conversationTokenManager.addMessage(continuationMessage)

                // Update UI for new conversation
                withContext(Dispatchers.Main) {
                    // Hide loading dialog
                    hideLoadingDialog()

                    // Remove warning badge if present
                    hideTokenLimitWarningBadge()

                    // Update chat title
                    chatTitleView.text = "Continued Conversation"

                    // Scroll to bottom
                    scrollToBottom()

                    // Set current conversation ID
                    currentConversationId = newConversationId

                    // Show toast about continuation
                    Toast.makeText(
                        this@MainActivity,
                        "Conversation summarized and continued in new chat",
                        Toast.LENGTH_LONG
                    ).show()

                    // Automatically generate AI response to the continuation message
                    sendMessage()
                }

            } catch (e: Exception) {
                Timber.e(e, "Error in summarizeAndStartNewChat: ${e.message}")

                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    Toast.makeText(
                        this@MainActivity,
                        "Error summarizing conversation: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Shows a loading dialog
     */
    private fun showLoadingDialog(message: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.loadingMessage)
        messageTextView.text = message

        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        loadingDialog?.show()
    }

    /**
     * Hides the loading dialog
     */
    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
    /**
     * Send a message with text and optional file attachments
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun sendMessage() {
        val inputEditText = binding.etInputText
        val message = inputEditText.text.toString().trim()
        val modelId = ModelManager.selectedModel.id
        val isSubscribed = PrefsManager.isSubscribed(this)
        val hasFiles = selectedFiles.isNotEmpty()

        // CRITICAL FIX: Add debug logging for file tracking
        if (hasFiles) {
            Timber.d("Sending message with ${selectedFiles.size} files")
            selectedFiles.forEach { file ->
                Timber.d("File: ${file.name}, URI: ${file.uri}")
            }
        }

        // Log current state before validation
        conversationTokenManager.logCurrentState("before sendMessage")

        // Token validation with proper error handling
        val (wouldExceed, availableTokens) =
            conversationTokenManager.wouldExceedLimit(message, modelId, isSubscribed)

        if (wouldExceed) {
            val inputTokens = TokenValidator.estimateTokenCount(message)
            Timber.d("Message would exceed limit: input=$inputTokens, available=$availableTokens")

            showMessageTooLongDialog(message, inputTokens, availableTokens, isSubscribed)
            return
        }

        // Check conversation limit with better thresholds
        val currentUsage = conversationTokenManager.getTokenUsagePercentage(modelId, isSubscribed)

        Timber.d("Current conversation usage: ${(currentUsage * 100).toInt()}%")
        val currentMessages = chatAdapter.currentList
        Timber.d("Current messages in adapter before sending: ${currentMessages.size}")
        currentMessages.forEach { message ->
            Timber.d("  Message: ${if (message.isUser) "USER" else "AI"} - ${message.message.take(30)}...")
        }

        // Show dialog at WARNING_THRESHOLD (85%), not just CRITICAL (95%)
        if (currentUsage >= ConversationTokenManager.WARNING_THRESHOLD) {
            if (!conversationTokenManager.hasContinuedPastWarning()) {
                // First time hitting warning threshold
                TokenLimitDialogHandler.showTokenLimitApproachingDialog(
                    context = this,
                    tokenPercentage = currentUsage,
                    onContinue = {
                        conversationTokenManager.setContinuedPastWarning(true)
                        // Clear input and proceed with sending
                        clearInputAndProceed(inputEditText, message, hasFiles)
                    },
                    onSummarize = { summarizeAndStartNewChat() },
                    onNewChat = { startNewConversation() }
                )
                return
            } else if (currentUsage >= ConversationTokenManager.CRITICAL_THRESHOLD) {
                // Already continued past warning, but now at critical threshold
                TokenLimitDialogHandler.showTokenLimitReachedDialog(
                    context = this,
                    modelId = modelId,
                    isSubscribed = isSubscribed,
                    onSummarize = { summarizeAndStartNewChat() },
                    onNewChat = { startNewConversation() },
                    onUpgrade = { navigateToWelcomeActivity() }
                )
                return
            }
        }

        // Check credit availability before sending (for non-subscribers)
        if (!isSubscribed) {
            if (!creditManager.hasSufficientChatCredits()) {
                Timber.w("Insufficient chat credits to send message")
                showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                return
            }
        }

        // Clear any existing draft when sending a message
        clearDraft()
        setGeneratingState(true)
        Timber.d("Starting message send - generating state set to true")

        // Clear input and proceed with sending if all checks pass
        clearInputAndProceed(inputEditText, message, hasFiles)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun clearInputAndProceed(inputEditText: EditText, message: String, hasFiles: Boolean) {
        // Clear the input field
        inputEditText.setText("")

        // Hide keyboard
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(inputEditText.windowToken, 0)

        // CRITICAL: Ensure generating state is maintained
        if (!isGenerating) {
            setGeneratingState(true)
            Timber.d("Re-setting generating state to true in clearInputAndProceed")
        }

        // Proceed with sending the message
        proceedWithSendingMessageAndFiles(message, hasFiles)
        saveTokenStateAfterMessageSent()

        // Update scroll arrows after sending
        Handler(Looper.getMainLooper()).postDelayed({
            if (::scrollArrowsHelper.isInitialized) {
                scrollArrowsHelper.updateButtonsState()
            }
        }, 500)
    }        // New helper method to handle both text-only and file messages
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun proceedWithSendingMessageAndFiles(message: String, hasFiles: Boolean) {
        if (hasFiles) {
            // Verify file accessibility before sending
            val accessibleFiles = selectedFiles.filter { file ->
                try {
                    contentResolver.openInputStream(file.uri)?.use { true } ?: false
                } catch (e: Exception) {
                    Timber.e(e, "File not accessible: ${file.uri}")
                    false
                }
            }

            if (accessibleFiles.size < selectedFiles.size) {
                Timber.w("Some files are not accessible! ${selectedFiles.size - accessibleFiles.size} files will be skipped")
                Toast.makeText(this, "Some files couldn't be accessed and will be skipped", Toast.LENGTH_SHORT).show()
            }

            if (accessibleFiles.isEmpty()) {
                // If no files are accessible, send as text-only
                Toast.makeText(this, "Files couldn't be accessed, sending text only", Toast.LENGTH_SHORT).show()
                proceedWithSendingMessage(message)
            } else {
                // Consume credits for non-subscribers before sending files
                if (!PrefsManager.isSubscribed(this)) {
                    if (!creditManager.consumeChatCredits()) {
                        Timber.e("Failed to consume chat credits - file message aborted")
                        setGeneratingState(false)
                        showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                        return
                    }
                }
                
                // Send with accessible files
                val userMessageId = UUID.randomUUID().toString()
                aiChatService.sendMessage(
                    message = message,
                    hasFiles = true,
                    fileUris = accessibleFiles.map { it.uri },
                    userMessageId = userMessageId,
                    createOrGetConversation = ::createOrGetConversation,
                    isReasoningEnabled = isReasoningEnabled,
                    reasoningLevel = reasoningLevel
                )

                // Clear files after sending
                selectedFiles.clear()
                binding.selectedFilesScrollView.visibility = View.GONE
                fileHandler.updateSelectedFilesView()
            }
        } else {
            // Text-only message
            proceedWithSendingMessage(message)
        }
    }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun showMessageTooLongDialog(
        originalMessage: String,
        inputTokens: Int,
        availableTokens: Int,
        isSubscribed: Boolean
    ) {
        // Create truncated preview
        val truncatedMessage = TokenValidator.truncateToTokenCount(originalMessage, availableTokens)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Message Too Long")
            .setMessage("Your message is $inputTokens tokens, but only $availableTokens tokens are available.\n\n" +
                    "Truncated version:\n\"${truncatedMessage.take(200)}${if (truncatedMessage.length > 200) "..." else ""}\"")
            .setPositiveButton("Send Truncated") { _, _ ->
                Timber.d("User chose to send truncated message")
                proceedWithSendingMessage(truncatedMessage)
            }
            .setNeutralButton("Edit") { _, _ ->
                Timber.d("User chose to edit message")
                binding.etInputText.setText(truncatedMessage)
                binding.etInputText.setSelection(truncatedMessage.length)
            }
            .setNegativeButton("Cancel", null)

        if (!isSubscribed) {
            dialog.setNeutralButton("Upgrade") { _, _ ->
                navigateToWelcomeActivity()
            }
        }

        dialog.show()
    }
    private fun checkForDraftsOnStartup() {
        lifecycleScope.launch {
            try {
                // Only check for draft conflicts if we're not already in a conversation
                if (currentConversationId == null) {
                    checkForDraftConflicts()
                }

                // Then show notification about total drafts
                val draftCount = conversationsViewModel.countConversationsWithDrafts()
                if (draftCount > 0) {
                    withContext(Dispatchers.Main) {
                        // Show a toast or notification
                        Toast.makeText(
                            this@MainActivity,
                            "You have $draftCount saved draft${if (draftCount > 1) "s" else ""}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for drafts on startup: ${e.message}")
            }
        }
    }    private fun checkForDraftConflicts() {
        lifecycleScope.launch {
            try {
                // If we already have a specific conversation open, don't show draft selection
                if (currentConversationId != null) {
                    Timber.d("Skipping draft conflict check - already in conversation $currentConversationId")
                    return@launch
                }

                // Get all conversations with drafts using the ViewModel
                val drafts = conversationsViewModel.getConversationsWithDrafts()

                // If there are multiple drafts AND we're not already in a conversation,
                // show a dialog to let the user choose
                if (drafts.size > 1 && currentConversationId == null) {
                    withContext(Dispatchers.Main) {
                        showDraftSelectionDialog(drafts)
                    }
                }
                // If there's exactly one draft and we're not in a conversation, just open it directly
                else if (drafts.size == 1 && currentConversationId == null) {
                    withContext(Dispatchers.Main) {
                        val draft = drafts[0]
                        Toast.makeText(this@MainActivity, "Opening saved draft", Toast.LENGTH_SHORT).show()
                        openConversation(draft.id, draft.aiModel)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for draft conflicts: ${e.message}")
            }
        }
    }    private fun showDraftSelectionDialog(drafts: List<Conversation>) {
        // Create a dialog to let the user choose which draft to open
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose a Saved Draft")

        // Create items for each draft
        val items = drafts.map { conversation ->
            val preview = if (conversation.draftText.isNotEmpty()) {
                conversation.draftText.take(30) + if (conversation.draftText.length > 30) "..." else ""
            } else {
                "Draft with attachments"
            }

            val date = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                .format(Date(conversation.draftTimestamp))

            "$date - $preview"
        }.toTypedArray()

        builder.setItems(items) { _, which ->
            // Open the selected draft conversation
            Timber.d("Selected draft conversation: ${drafts[which].id}")
            openConversation(drafts[which].id, drafts[which].aiModel)
        }

        builder.setNegativeButton("Cancel", null)
        builder.setNeutralButton("Create New Conversation") { _, _ ->
            // Reset for a new conversation
            resetChat()
            currentConversationId = null
            binding.etInputText.text?.clear()
            selectedFiles.clear()
            binding.selectedFilesScrollView.visibility = View.GONE
            fileHandler.updateSelectedFilesView()
        }

        builder.show()
    }    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun proceedWithSendingMessage(message: String) {
        val hasFiles = selectedFiles.isNotEmpty()
        val fileUris = selectedFiles.map { it.uri }
        val userMessageId = UUID.randomUUID().toString()
        
        // Consume credits for non-subscribers
        if (!PrefsManager.isSubscribed(this)) {
            if (!creditManager.consumeChatCredits()) {
                Timber.e("Failed to consume chat credits - message aborted")
                setGeneratingState(false)
                showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                return
            }
        }

        // Get or create conversation ID
        val conversationId = createOrGetConversation(message)

        // Set conversation ID in token manager
        conversationTokenManager.setConversationId(conversationId)

        // Create and add user message
        val userMessage = ChatMessage(
            id = userMessageId,
            conversationId = conversationId,
            message = message,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        // Add user message to MessageManager and TokenManager
        messageManager.addMessage(userMessage)
        conversationTokenManager.addMessage(userMessage)

        // Clear any existing draft when sending a message
        clearDraft(shouldDeleteIfEmpty = false)

        // CRITICAL: Ensure generating state is still true before proceeding
        if (!isGenerating) {
            setGeneratingState(true)
            Timber.d("Re-setting generating state to true before AI response")
        }

        // Start AI response immediately for seamless indicator display
        Handler(Looper.getMainLooper()).post {
            try {
                if (hasFiles) {
                    aiChatService.sendMessage(
                        message = message,
                        hasFiles = true,
                        fileUris = fileUris,
                        userMessageId = userMessageId,
                        createOrGetConversation = ::createOrGetConversation,
                        isReasoningEnabled = isReasoningEnabled,
                        reasoningLevel = reasoningLevel
                    )

                    selectedFiles.clear()
                    binding.selectedFilesScrollView.visibility = View.GONE
                    fileHandler.updateSelectedFilesView()

                    // Add file tokens to token manager
                    fileUris.forEach { uri ->
                        val fileTokens = estimateFileTokens(uri)
                        conversationTokenManager.addFileTokens(fileTokens)
                    }
                } else {
                    // Generate AI response with full context
                    generateAIResponseWithFullContext(conversationId, userMessageId)
                }

                isMessageSent = true
                binding.btnNewConversation.isEnabled = true

            } catch (e: Exception) {
                Timber.e(e, "Error sending message: ${e.message}")
                Toast.makeText(this, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
                setGeneratingState(false)
            }
        } // Immediate execution for seamless indicator transitions

        // Update UI immediately
        updateConversationWithLatestMessage(message)
        updateTokenCounterUI()
        scrollToBottom()
        provideHapticFeedback(50)
        hideKeyboard()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun generateAIResponseWithFullContext(conversationId: String, userMessageId: String) {
        lifecycleScope.launch {
            try {
                // CRITICAL: Ensure generating state before starting
                withContext(Dispatchers.Main) {
                    if (!isGenerating) {
                        setGeneratingState(true)
                        Timber.d("Setting generating state in generateAIResponseWithFullContext")
                    }
                }

                // Ensure full conversation context is loaded
                messageManager.ensureConversationContextLoaded(conversationId)

                // Verify we have context
                val (userCount, aiCount, hasContext) = messageManager.getConversationStatsWithValidation(conversationId)

                if (!hasContext) {
                    Timber.w("WARNING: Limited conversation context - user: $userCount, AI: $aiCount")

                    // Try to reload from database one more time
                    try {
                        val dbMessages = withContext(Dispatchers.IO) {
                            conversationsViewModel.getAllConversationMessages(conversationId)
                        }

                        if (dbMessages.isNotEmpty()) {
                            Timber.d("Reloading ${dbMessages.size} messages from database for context")
                            messageManager.initialize(dbMessages, conversationId)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error reloading messages from database: ${e.message}")
                    }
                }

                val (indicatorText, indicatorColor) = when {
                    isDeepSearchEnabled -> {
                        Pair("Searching the web...", ContextCompat.getColor(this@MainActivity, R.color.web_search_button_color))
                    }
                    isReasoningEnabled -> {
                        Pair("Thinking...", ContextCompat.getColor(this@MainActivity, R.color.reasoning_button_color))
                    }
                    else -> {
                        Pair("Generating response...", ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    }
                }

                // Create AI message WITH initial indicator
                val aiMessageId = UUID.randomUUID().toString()
                val aiMessage = ChatMessage(
                    id = aiMessageId,
                    conversationId = conversationId,
                    message = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isGenerating = true,
                    showButtons = false,
                    modelId = ModelManager.selectedModel.id,
                    aiModel = ModelManager.selectedModel.displayName,
                    parentMessageId = userMessageId,
                    // CRITICAL: These must be set based on model type
                    isThinkingActive = isReasoningEnabled && ModelConfigManager.supportsReasoning(ModelManager.selectedModel.id) && !isDeepSearchEnabled,
                    isWebSearchActive = isDeepSearchEnabled,
                    isForceSearch = false, // Add this field
                    hasThinkingProcess = false, // Will be updated during streaming
                    thinkingProcess = null, // Will be filled during streaming
                    initialIndicatorText = indicatorText,
                    initialIndicatorColor = indicatorColor
                )
                // Add message to manager with validation
                messageManager.addMessageWithValidation(aiMessage)

                // Log context before API call
                val contextMessages = messageManager.getCurrentMessages()
                    .filter { it.conversationId == conversationId }
                    .sortedBy { it.timestamp }

                Timber.d("=== FINAL CONTEXT VERIFICATION BEFORE API CALL ===")
                Timber.d("Conversation ID: $conversationId")
                Timber.d("Total context messages: ${contextMessages.size}")

                if (contextMessages.size < 2) {
                    Timber.e("CRITICAL: INSUFFICIENT CONTEXT! Only ${contextMessages.size} messages found")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity,
                            "Error: Conversation context not found. Please try again.",
                            Toast.LENGTH_LONG).show()
                        setGeneratingState(false)
                    }
                    return@launch
                }

                // Log each message for verification
                contextMessages.forEachIndexed { index, msg ->
                    val role = if (msg.isUser) "USER" else "AI"
                    val preview = msg.message.take(50).replace("\n", " ")
                    Timber.d("  Context[$index]: [$role] $preview...")
                }

                // CRITICAL: Ensure generating state before API call
                withContext(Dispatchers.Main) {
                    if (!isGenerating) {
                        setGeneratingState(true)
                        Timber.d("Final check - setting generating state before API call")
                    }
                }

                // Launch API request with verified context
                currentApiJob = aiChatService.startApiCall(
                    conversationId = conversationId,
                    messageId = aiMessageId,
                    isSubscribed = PrefsManager.isSubscribed(this@MainActivity),
                    isModelFree = ModelManager.selectedModel.isFree,
                    originalMessageText = contextMessages.lastOrNull { it.isUser }?.message
                )

            } catch (e: Exception) {
                Timber.e(e, "Error in generateAIResponseWithFullContext: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Error generating response: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                    setGeneratingState(false)
                }
            }
        }
    }    /**
     * Estimate tokens for a file
     */
    private fun estimateFileTokens(uri: Uri): Int {
        // Get MIME type
        val mimeType = contentResolver.getType(uri) ?: return 1000 // Default estimate

        return when {
            // Images typically extract to around 200-300 tokens of description
            mimeType.startsWith("image/") -> 300

            // Text files estimate based on content
            mimeType == "text/plain" -> {
                try {
                    val text = FileUtil.readTextFromUri(this, uri)
                    TokenValidator.estimateTokenCount(text)
                } catch (e: Exception) {
                    Timber.e(e, "Error reading text file: ${e.message}")
                    500 // Default estimate for text files
                }
            }

            // PDF and other documents are typically 1000-3000 tokens
            mimeType.contains("pdf") -> 2000

            // Default for other file types
            else -> 1000
        }
    }



    private fun updateRecordingTimer() {
        if (isRecording) {
            val elapsedMillis = System.currentTimeMillis() - recordingStartTime
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60

            tvRecordingTime.text = String.format("%02d:%02d", minutes, seconds)

            // Update every 100ms for smoother animation
            recordingHandler.postDelayed({ updateRecordingTimer() }, 100)
        }
    }


    private fun stopWaveformAnimation() {
        recordingAnimators.forEach { it.cancel() }
        recordingAnimators.clear()
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    /**
     * Send a voice message for AI processing
     *
     * @param text The transcribed text from voice recognition
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun sendVoiceMessage(text: String) {
        // Create conversation ID first if needed
        val conversationIdString = createOrGetConversation(text)

        // Update conversation with the latest user message
        updateConversationWithLatestMessage(text)

        // Create user message with voice indicator
        val userMessageId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(
            id = userMessageId,
            conversationId = conversationIdString,
            message = text,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            isVoiceMessage = true  // Add this field to your ChatMessage class
        )

        // Add to chat
        val currentList = chatAdapter.currentList.toMutableList()
        currentList.add(userMessage)
        chatAdapter.submitList(currentList)

        // Save to database
        conversationsViewModel.addChatMessage(userMessage)

        // Clear input field
        binding.etInputText.text?.clear()

        // Handle credits deduction for voice messages
        if (!PrefsManager.isSubscribed(this) && !ModelManager.selectedModel.isFree) {
            creditManager.consumeChatCredits(2)
            updateFreeMessagesText()
        }

        // CRITICAL FIX: Use the same pattern for voice messages with indicator logic
        lifecycleScope.launch {
            try {
                // Set generating state
                withContext(Dispatchers.Main) {
                    setGeneratingState(true)
                    binding.typingIndicator.visibility = View.VISIBLE
                }

                // Determine initial indicator before creating AI message
                val (indicatorText, indicatorColor) = when {
                    isDeepSearchEnabled -> {
                        Pair("Searching the web...", ContextCompat.getColor(this@MainActivity, R.color.web_search_button_color))
                    }
                    isReasoningEnabled -> {
                        Pair("Thinking...", ContextCompat.getColor(this@MainActivity, R.color.reasoning_button_color))
                    }
                    else -> {
                        Pair("Generating response...", ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    }
                }

                // Create AI message with initial indicator
                val aiMessageId = UUID.randomUUID().toString()
                val aiMessage = ChatMessage(
                    id = aiMessageId,
                    conversationId = conversationIdString,
                    message = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isGenerating = true,
                    showButtons = false,
                    modelId = ModelManager.selectedModel.id,
                    aiModel = ModelManager.selectedModel.displayName,
                    parentMessageId = userMessageId,
                    // CRITICAL: These must be set based on model type
                    isThinkingActive = isReasoningEnabled && ModelConfigManager.supportsReasoning(ModelManager.selectedModel.id) && !isDeepSearchEnabled,
                    isWebSearchActive = isDeepSearchEnabled,
                    isForceSearch = false, // Add this field
                    hasThinkingProcess = false, // Will be updated during streaming
                    thinkingProcess = null, // Will be filled during streaming
                    initialIndicatorText = indicatorText,
                    initialIndicatorColor = indicatorColor
                )

                // Use message manager to add AI message
                messageManager.addMessage(aiMessage)

                // Generate AI response
                currentApiJob = aiChatService.startApiCall(
                    conversationId = conversationIdString,
                    messageId = aiMessage.id,
                    isSubscribed = PrefsManager.isSubscribed(this@MainActivity),
                    isModelFree = ModelManager.selectedModel.isFree,
                    originalMessageText = text
                )

            } catch (e: Exception) {
                Timber.e(e, "Error in sendVoiceMessage: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Error generating response: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                    setGeneratingState(false)
                }
            }
        }
    }    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, start recording
                    startVoiceRecording()
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Microphone permission is needed for voice input",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            // Handle your existing permission requests...
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) &&
                    (grantResults.size > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    // Permissions granted, proceed with location services
                    initializeLocationServices()
                    Timber.d("Location permissions granted")
                } else {
                    // Permissions denied, but we can still function with default location
                    Timber.d("Location permissions denied, will use default location")
                    showPermissionDeniedMessage()
                }
                return
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this,
            "Location permissions denied. Web search will use default location information.",
            Toast.LENGTH_SHORT).show()
    }


    private fun processDeferredIntent() {
        if (deferredIntent != null && !isIntentProcessed) {
            try {
                Timber.d("Processing deferred intent: ${deferredIntent?.action}")

                if (::binding.isInitialized && ::vibrator.isInitialized && ::chatAdapter.isInitialized) {
                    handleIntent(deferredIntent)
                    isIntentProcessed = true
                    processingIntentAction = deferredIntent?.action
                    deferredIntent = null
                } else {
                    // Try again after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        processDeferredIntent()
                    }, 200)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing deferred intent: ${e.message}")
                deferredIntent = null
                isIntentProcessed = false
            }
        }
    }
    fun createOrGetConversation(userText: String): String {
        // Get user name from preferences
        val userName = PrefsManager.getUserName(this) ?: "user"

        // If private mode is enabled, use a temporary conversation ID
        if (isPrivateModeEnabled) {
            // Generate a random ID for this session only - won't be persisted
            val privateId = "private_" + UUID.randomUUID().toString()
            Timber.d("Created private conversation: $privateId for user: $userName")

            // Only switch if this is a new private conversation
            if (currentConversationId == null || !currentConversationId!!.startsWith("private_")) {
                // Reset token manager for new private conversation
                conversationTokenManager.reset()
                conversationTokenManager.setConversationId(privateId)
            }

            currentConversationId = privateId

            // Set chat title to "Private Chat"
            chatTitleView.text = "Private Chat"

            // Store user name in private conversation metadata
            conversationMetadata[privateId] = userName

            return privateId
        }

        // Otherwise, use the normal logic for persistent conversations
        return if (currentConversationId == null) {
            // Create conversation with user text if available
            val conversationTitle = if (userText.isNotBlank()) {
                userText.take(30)
            } else {
                "File attachment"
            }

            // Create the conversation
            val conversation = Conversation(
                id = System.currentTimeMillis(),
                title = conversationTitle,
                preview = userText.ifBlank { "File attachment" },
                modelId = ModelManager.selectedModel.id,
                timestamp = System.currentTimeMillis(),
                lastMessage = userText.ifBlank { "File attachment" },
                lastModified = System.currentTimeMillis(),
                name = conversationTitle,
                aiModel = ModelManager.selectedModel.displayName,
                userName = userName  // Add userName to the conversation object
            )

            // Try to get location for this conversation
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val location = LocationService.getApproximateLocation(this@MainActivity)
                    // Store location with conversation
                    conversation.locationCity = location.approximate.city
                    conversation.locationRegion = location.approximate.region
                    conversation.locationCountry = location.approximate.country

                    // Update in database
                    conversationsViewModel.updateConversation(conversation)

                    Timber.d("Added location ${location.approximate.city} to conversation ${conversation.id}")
                } catch (e: Exception) {
                    Timber.e(e, "Error adding location to conversation: ${e.message}")
                }
            }

            // Set the conversation ID and save
            val newId = conversation.id.toString()

            // Reset token manager for brand new conversation
            conversationTokenManager.reset()
            currentConversationId = newId
            // Reset draft loaded flag for new conversation
            isDraftContentLoaded = false
            conversationTokenManager.setConversationId(newId)

            // Save synchronously, not in a coroutine
            conversationsViewModel.addConversation(conversation)

            // Personalize chat title with user name if available
            if (userName != "user") {
                chatTitleView.text = "$userName's Chat: ${conversationTitle.take(20)}"
            } else {
                chatTitleView.text = conversationTitle
            }

            // Store user name in conversation metadata map for reference during the session
            conversationMetadata[newId] = userName

            newId
        } else {
            // If we already have a conversation ID, make sure token manager is using it
            conversationTokenManager.setConversationId(currentConversationId!!)

            // Ensure user name is in metadata for existing conversation
            if (!conversationMetadata.containsKey(currentConversationId!!)) {
                conversationMetadata[currentConversationId!!] = userName

                // Optionally update the existing conversation in the database with the user name
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val existingConversation = conversationsViewModel.getConversationById(currentConversationId!!)
                        existingConversation?.let {
                            // Update the userName field if it's empty or different
                            if (it.userName.isEmpty() || it.userName != userName) {
                                it.userName = userName
                                conversationsViewModel.updateConversation(it)
                                Timber.d("Updated user name for existing conversation: ${currentConversationId}")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating user name for existing conversation: ${e.message}")
                    }
                }
            }

            currentConversationId.toString()
        }
    }    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                simulateWaveMovement()
                recordingAnimationTimer?.postDelayed(this, 250) // Slightly faster updates for smoother animation
            }
        }
    }
    /**
     * Update token limit when model changes
     * Add this to model change handlers
     */
    private fun updateTokenLimitForModel(modelId: String) {
        // Find token counter TextView
        val tvTokenCount = findViewById<TextView>(R.id.tvTokenCount) ?: return

        // Update with current text
        val currentText = binding.etInputText.text?.toString() ?: ""
        val isSubscribed = PrefsManager.isSubscribed(this)

        // Update token counter
        TokenCounterHelper.updateTokenCounter(
            tvTokenCount,
            currentText,
            modelId,
            isSubscribed,
            this
        )

        // Show toast with new limits
        val model = ModelManager.models.find { it.id == modelId }
        val modelName = model?.displayName ?: modelId
        val tokenLimitMessage = TokenValidator.getTokenLimitMessage(modelId, isSubscribed)

        Toast.makeText(this, "$modelName: $tokenLimitMessage", Toast.LENGTH_SHORT).show()
    }

    /**
     * Initialize voice recording with proper state reset
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun initializeVoiceRecording() {
        try {
            // Initialize voice recording views
            btnMicrophone = findViewById(R.id.btnMicrophone)
            recordingOverlay = findViewById(R.id.recordingOverlay)
            tvRecordingTime = findViewById(R.id.tvRecordingTime)
            btnCancelRecording = findViewById(R.id.btnCancelRecording)
            btnSendRecording = findViewById(R.id.btnSendRecording)

            // Find loading indicator
            val loadingIndicator = findViewById<ProgressBar>(R.id.sendLoadingIndicator)

            // Reset UI state - ensure send button is visible and loading is hidden
            btnSendRecording.visibility = View.VISIBLE
            loadingIndicator?.visibility = View.GONE

            // Safely initialize wave bars
            initializeWaveBars()

            // Initialize speech recognizer if available
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer.setRecognitionListener(createRecognitionListener())

                // Log success
                Timber.d("Speech recognizer initialized successfully")
            } else {
                // Fall back to send button if speech recognition isn't available
                btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
                Timber.w("Speech recognition not available on this device")
            }

            // Setup click listeners
            btnMicrophone.setOnClickListener { startVoiceRecording() }
            btnCancelRecording.setOnClickListener { cancelVoiceRecording() }
            btnSendRecording.setOnClickListener { stopVoiceRecordingAndSend() }

            // Add text change listener to input field
            binding.etInputText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Toggle between mic and send button based on text content
                    if (s.toString().trim().isNotEmpty()) {
                        btnMicrophone.visibility = View.GONE
                        binding.btnSubmitText.visibility = View.VISIBLE
                    } else {
                        binding.btnSubmitText.visibility = View.GONE
                        btnMicrophone.visibility = View.VISIBLE
                    }
                    
                    // Update submit button state based on text input and file processing
                    fileHandler.updateSubmitButtonState()
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            Timber.d("Voice recording initialization complete")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing voice recording")

            // Fallback to text input only
            btnMicrophone.visibility = View.GONE
            binding.btnSubmitText.visibility = View.VISIBLE
        }
    }
    private fun initializeWaveBars() {
        try {
            // Find the wave container by its ID - this is the only ID lookup we need
            val waveformView = findViewById<LinearLayout>(R.id.waveformView)
            if (waveformView == null) {
                Timber.e("Waveform view container not found")
                return
            }

            // Clear existing collection and views
            waveBars.clear()
            waveformView.removeAllViews()

            Timber.d("Creating wave bars programmatically")

            // Define colors for the bars
            val colors = arrayOf(
                "#FF4081", "#F48FB1", "#FF9800", "#FFB74D", "#FFEB3B",
                "#8BC34A", "#4CAF50", "#03A9F4", "#2196F3", "#3F51B5",
                "#673AB7", "#9C27B0", "#E91E63", "#D81B60", "#00BCD4",
                "#7C4DFF", "#FF5722", "#FF4081", "#F48FB1", "#FF9800",
                "#FFB74D", "#FFEB3B", "#8BC34A", "#4CAF50", "#03A9F4",
                "#2196F3", "#3F51B5", "#673AB7", "#9C27B0", "#E91E63"
            )

            // ALWAYS create 30 bars programmatically
            val numBars = 30

            for (i in 0 until numBars) {
                // Get color for this bar
                val colorString = colors[i % colors.size]

                // Calculate a wave-like height pattern
                val angle = (i.toFloat() / numBars.toFloat()) * 2f * Math.PI.toFloat()
                val heightPercent = 0.4f + 0.4f * sin(angle.toDouble()).toFloat()
                val heightDp = (10 + (heightPercent * 40)).toInt()

                try {
                    // Parse the color
                    val color = colorString.toColorInt()

                    // Create the bar view
                    val bar = View(this).apply {
                        // Set dimensions - small width, variable height
                        layoutParams = LinearLayout.LayoutParams(
                            3.dp(this@MainActivity),
                            heightDp.dp(this@MainActivity)
                        ).apply {
                            marginStart = 1.dp(this@MainActivity)
                            marginEnd = 1.dp(this@MainActivity)
                        }

                        // Set the color
                        setBackgroundColor(color)

                        // Set initial scale for animation
                        pivotY = heightDp.dp(this@MainActivity).toFloat()
                        scaleY = 0.3f

                        // Set an ID and tag for identification
                        id = View.generateViewId()
                        tag = "WaveBar_${i+1}"
                    }

                    // Add to view
                    waveformView.addView(bar)

                    // Add to our collection for animation
                    waveBars.add(bar)

                    Timber.d("Created wave bar ${i+1} with height ${heightDp}dp and color $colorString")
                } catch (e: Exception) {
                    Timber.e(e, "Error creating wave bar ${i+1}")
                }
            }

            Timber.d("Successfully created ${waveBars.size} wave bars")
        } catch (e: Exception) {
            Timber.e(e, "Error in wave bar initialization: ${e.message}")
            // Create empty list to avoid null pointer exceptions
            waveBars = ArrayList()
        }
    }


    fun setSelectedModelInSpinner(modelId: String) {
        try {
            Timber.d("Setting selected model in spinner: $modelId")
            
            // Find model index
            val modelIndex = ModelManager.models.indexOfFirst { it.id == modelId }

            if (modelIndex != -1) {
                // Update ModelManager first
                ModelManager.selectedModel = ModelManager.models[modelIndex]
                Timber.d("Updated ModelManager.selectedModel to: ${ModelManager.selectedModel.displayName}")

                // Update UI with multiple retry attempts to handle race conditions
                var attempts = 0
                val maxAttempts = 5

                val setSpinnerRunnable = object : Runnable {
                    override fun run() {
                        try {
                            // Try to set the spinner
                            if (::binding.isInitialized) {
                                // Check current selection first
                                val currentSelection = binding.spinnerModels.selectedItemPosition
                                Timber.d("Current spinner selection: $currentSelection, Target: $modelIndex")
                                
                                // Only update if different
                                if (currentSelection != modelIndex) {
                                    // Set spinner selection
                                    binding.spinnerModels.setSelection(modelIndex, true)
                                    Timber.d("Updated spinner selection to position: $modelIndex")
                                }

                                // Get adapter and update its selected position if it's our custom adapter
                                val adapter = binding.spinnerModels.adapter
                                if (adapter is ModelSpinnerAdapter) {
                                    adapter.setSelectedPosition(modelIndex)
                                    adapter.notifyDataSetChanged()
                                }

                                // Update UI
                                ModelValidator.clearCache()
                                updateControlsVisibility(modelId)
                                applyModelColorToUI(modelId)
                                chatAdapter.updateCurrentModel(modelId)

                                Timber.d("Successfully set spinner to model: ${ModelManager.selectedModel.displayName}")
                            } else if (attempts < maxAttempts) {
                                // Binding not initialized yet, retry with delay
                                attempts++
                                Timber.d("Binding not initialized, retrying attempt $attempts/$maxAttempts")
                                Handler(Looper.getMainLooper()).postDelayed(this, 300)
                            } else {
                                Timber.e("Failed to set spinner after $maxAttempts attempts - binding not initialized")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error setting spinner in attempt $attempts")

                            // Retry if we haven't exceeded max attempts
                            if (attempts < maxAttempts) {
                                attempts++
                                Handler(Looper.getMainLooper()).postDelayed(this, 300)
                            }
                        }
                    }
                }

                // Start the retry process immediately
                Handler(Looper.getMainLooper()).post(setSpinnerRunnable)

                // Also update the response preferences model position
                if (::responsePreferences.isInitialized) {
                    responsePreferences.modelPosition = modelIndex
                }
            } else {
                Timber.e("Could not find model with ID: $modelId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in setSelectedModelInSpinner: ${e.message}")
        }
    }

    private fun startRecordingAnimationTimer() {
        // Cancel any existing timer
        recordingAnimationTimer?.removeCallbacksAndMessages(null)

        // Create new timer
        recordingAnimationTimer = Handler(Looper.getMainLooper())
        recordingAnimationTimer?.post(animationRunnable)
        Timber.d("Recording animation timer started")
    }

    private fun updateWaveAnimation(rmsdB: Float) {
        // Store the last RMS value for later use
        lastRmsValue = rmsdB

        // Check recording state
        if (!isRecording) return

        // Check if waveBars is initialized and not empty
        if (waveBars.isEmpty()) {
            Timber.d("Wave bars collection is empty, can't animate")
            return
        }

        // Map RMS dB value to a range usable for animation
        // RMS values are typically negative with 0 being loudest and -60 being silent
        val normalizedVolume = if (rmsdB < -60) {
            0.3f // Minimum height for very quiet
        } else if (rmsdB > 0) {
            1.0f // Maximum for very loud
        } else {
            // Map from -60..0 to 0.3..1.0
            0.3f + (1.0f - 0.3f) * (rmsdB + 60) / 60
        }

        // Update wave bars in a natural pattern using try-catch for safety
        try {
            runOnUiThread {
                // Validate that we're still recording and activity is active
                if (!isRecording || isFinishing || isDestroyed) return@runOnUiThread

                // Clear any existing animators
                recordingAnimators.forEach { it.cancel() }
                recordingAnimators.clear()

                // Update each bar individually with null check
                for (i in waveBars.indices) {
                    val bar = waveBars.getOrNull(i) ?: continue

                    try {
                        // Create amplitude pattern based on position (sine wave)
                        val angleOffset = (i.toFloat() / waveBars.size.toFloat()) * 2f * Math.PI.toFloat()
                        val positionFactor = 0.7f + 0.3f * sin(angleOffset.toDouble()).toFloat()

                        // Vary the heights with randomness but scaled by the volume
                        val randomFactor = 0.9f + (Math.random() * 0.3f).toFloat()
                        val targetScale = normalizedVolume * positionFactor * randomFactor

                        // Create smooth animation to the new height
                        val scaleAnim = ValueAnimator.ofFloat(bar.scaleY, targetScale.coerceAtMost(1.0f))
                        scaleAnim.duration = 150
                        scaleAnim.interpolator = AccelerateDecelerateInterpolator()
                        scaleAnim.addUpdateListener { animator ->
                            try {
                                bar.scaleY = animator.animatedValue as Float
                            } catch (e: Exception) {
                                // Ignore animation errors
                            }
                        }
                        scaleAnim.start()
                        recordingAnimators.add(scaleAnim)
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating wave bar $i")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in wave animation")
        }
    }
    private fun showRealTimeSearchIndicator() {
        runOnUiThread {
            try {
                // Check if indicator already exists
                if (realTimeSearchIndicator == null) {
                    // Create indicator
                    realTimeSearchIndicator = CardView(this).apply {
                        id = View.generateViewId()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(8.dp(this@MainActivity), 4.dp(this@MainActivity),
                                8.dp(this@MainActivity), 4.dp(this@MainActivity))
                        }
                        radius = 12f
                        cardElevation = 4f
                        setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent))

                        // Add text view inside
                        val textView = TextView(context).apply {
                            text = "Real-time search active"
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                            setPadding(12.dp(context), 6.dp(context), 12.dp(context), 6.dp(context))
                            setTypeface(null, Typeface.BOLD)
                        }
                        addView(textView)
                    }

                    // Add to reasoning controls layout
                    reasoningControlsLayout.addView(realTimeSearchIndicator)
                }

                // Show with animation
                realTimeSearchIndicator?.let { indicator ->
                    if (indicator.visibility != View.VISIBLE) {
                        indicator.alpha = 0f
                        indicator.visibility = View.VISIBLE
                        indicator.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error showing real-time search indicator: ${e.message}")
            }
        }
    }

    private fun startVoiceRecording() {
        // Reset recording state
        lastPartialResults = ""
        isSpeechProcessing = false

        // Reset UI components
        val loadingIndicator = findViewById<ProgressBar>(R.id.sendLoadingIndicator)
        btnSendRecording.visibility = View.VISIBLE
        loadingIndicator?.visibility = View.GONE

        // Log permission status
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        Timber.d("RECORD_AUDIO permission status: $hasPermission")

        // If permission is missing, show a more informative message
        if (!hasPermission) {
            Toast.makeText(this, "Microphone permission required for voice recording", Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
            return
        }

        try {
            // Hide the reasoning controls, deep search, input layout AND credits button
            binding.reasoningControlsLayout.visibility = View.GONE
            binding.inputCardLayout.visibility = View.GONE
            binding.creditsButton.visibility = View.GONE  // Hide credits button during recording

            // Start speech recognition
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            // Make sure animation starts even if RMS updates are delayed or missing
            runOnUiThread {
                // Reset all wave bars to default height
                for (bar in waveBars) {
                    bar.pivotY = bar.height.toFloat()
                    bar.scaleY = 0.3f
                }

                // Start initial animation regardless of audio input
                simulateWaveMovement()

                // Ensure visual feedback immediately
            }

            // Start speech recognizer with detailed logging
            speechRecognizer.startListening(intent)
            Timber.d("SpeechRecognizer.startListening called with intent: ${intent.extras}")

            // Start fallback animation timer
            startRecordingAnimationTimer()

            // Update UI
            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            // Show recording overlay with animation
            recordingOverlay.visibility = View.VISIBLE
            recordingOverlay.alpha = 0f
            recordingOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            // Start timer
            updateRecordingTimer()

            // Hide keyboard
            hideKeyboard()

            // Vibrate to indicate recording started
            provideHapticFeedback(50)

            Timber.d("Voice recording started")
        } catch (e: Exception) {
            Timber.e(e, "Error starting voice recording: ${e.message}")
            Toast.makeText(this, "Error starting voice recording: ${e.message}", Toast.LENGTH_SHORT).show()

            // Restore UI in case of error
            binding.reasoningControlsLayout.visibility = View.VISIBLE
            binding.inputCardLayout.visibility = View.VISIBLE

            // Only show credits button if user is not subscribed and model isn't free
            if (!PrefsManager.isSubscribed(this) && !ModelManager.selectedModel.isFree) {
                binding.creditsButton.visibility = View.VISIBLE
            }

            if (::scrollArrowsHelper.isInitialized) {
                scrollArrowsHelper.hideButtonsTemporarily(10000) // Hide for 10 seconds
            }
        }
    }
    private fun simulateWaveMovement() {
        if (!isRecording || waveBars.isEmpty()) return

        runOnUiThread {
            try {
                // Clear any existing animators
                recordingAnimators.forEach { it.cancel() }
                recordingAnimators.clear()

                // Create a sine wave pattern across all bars
                val timeOffset = System.currentTimeMillis() % 2000 / 2000f

                for (i in waveBars.indices) {
                    val bar = waveBars.getOrNull(i) ?: continue

                    // Calculate position in the wave (0 to 2π)
                    val position = (i.toFloat() / waveBars.size.toFloat()) * 2f * Math.PI.toFloat()

                    // Add time-based movement
                    val angle = position + (timeOffset * 2f * Math.PI.toFloat())

                    // Base scale using sine wave pattern
                    val baseScale = 0.3f + 0.4f * sin(angle.toDouble()).toFloat()

                    // Add small random variation
                    val randomFactor = 1f + (Math.random() * 0.2f - 0.1f).toFloat()
                    val targetScale = (baseScale * randomFactor).coerceIn(0.2f, 1.0f)

                    // Create animation to the target scale
                    val scaleAnim = ValueAnimator.ofFloat(bar.scaleY, targetScale)
                    scaleAnim.duration = 250
                    scaleAnim.interpolator = AccelerateDecelerateInterpolator()
                    scaleAnim.addUpdateListener { animator ->
                        try {
                            bar.scaleY = animator.animatedValue as Float
                        } catch (e: Exception) {
                            // Ignore animation errors
                        }
                    }
                    scaleAnim.start()
                    recordingAnimators.add(scaleAnim)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in wave simulation")
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun stopVoiceRecordingAndSend() {
        if (isRecording) {
            // First set the processing flag to show loading indicator
            isSpeechProcessing = true

            // Show loading indicator, hide send button
            val loadingIndicator = findViewById<ProgressBar>(R.id.sendLoadingIndicator)
            loadingIndicator?.visibility = View.VISIBLE
            btnSendRecording.visibility = View.GONE

            try {
                // Stop the speech recognizer - will trigger onResults or onError
                speechRecognizer.stopListening()

                // Wait briefly to see if we get results
                Handler(Looper.getMainLooper()).postDelayed({
                    // If we still haven't received a result after 1.5 seconds
                    if (isSpeechProcessing) {
                        // If we have partial results, use them
                        if (lastPartialResults.isNotEmpty()) {
                            Timber.d("Using partial results as final: $lastPartialResults")
                            finishRecordingWithText(lastPartialResults)
                        } else {
                            // No results available
                            Timber.d("No speech detected after waiting")
                            Toast.makeText(this@MainActivity, "No speech detected. Please try again.", Toast.LENGTH_SHORT).show()

                            // Finish recording UI
                            finishRecordingUI()
                        }
                    }
                }, 1500) // Wait 1.5 seconds for results

            } catch (e: Exception) {
                Timber.e(e, "Error stopping speech recognizer")
                Toast.makeText(this, "Error processing speech: ${e.message}", Toast.LENGTH_SHORT).show()

                // Reset UI
                finishRecordingUI()
            }
        }
    }

    /**
     * Finish recording with the given text
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun finishRecordingWithText(text: String) {
        if (text.isNotEmpty()) {
            Timber.d("Speech recognition final result: $text")

            // Reset processing flag
            isSpeechProcessing = false

            // Send the message
            sendVoiceMessage(text)
        } else {
            Timber.d("Empty text, not sending")
            Toast.makeText(this, "No speech detected", Toast.LENGTH_SHORT).show()
        }

        // Finish recording UI
        finishRecordingUI()
    }

    /**
     * Clean up recording UI
     */
    private fun finishRecordingUI() {
        isRecording = false

        // Reset the send button and loading indicator
        val loadingIndicator = findViewById<ProgressBar>(R.id.sendLoadingIndicator)
        btnSendRecording.visibility = View.VISIBLE
        loadingIndicator?.visibility = View.GONE

        // Stop timer and animations
        recordingHandler.removeCallbacksAndMessages(null)
        recordingAnimationTimer?.removeCallbacksAndMessages(null)
        recordingAnimationTimer = null
        stopWaveformAnimation()

        // Hide overlay with animation
        recordingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                recordingOverlay.visibility = View.GONE

                // Show the input layout and controls again
                binding.reasoningControlsLayout.visibility = View.VISIBLE
                binding.inputCardLayout.visibility = View.VISIBLE

                // Only show credits button if user is not subscribed and model isn't free
                if (!PrefsManager.isSubscribed(this) && !ModelManager.selectedModel.isFree) {
                    binding.creditsButton.visibility = View.VISIBLE
                }
            }
            .start()
        if (::scrollArrowsHelper.isInitialized) {
            scrollArrowsHelper.updateButtonsState()
        }
        // Reset processing flags
        isSpeechProcessing = false
    }

    private fun cancelVoiceRecording() {
        if (isRecording) {
            // Stop recording
            try {
                speechRecognizer.stopListening()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping speech recognizer")
            }

            isRecording = false

            // Reset send button and loading indicator
            val loadingIndicator = findViewById<ProgressBar>(R.id.sendLoadingIndicator)
            btnSendRecording.visibility = View.VISIBLE
            loadingIndicator?.visibility = View.GONE

            // Stop timer and animations
            recordingHandler.removeCallbacksAndMessages(null)
            recordingAnimationTimer?.removeCallbacksAndMessages(null)
            recordingAnimationTimer = null
            stopWaveformAnimation()

            // Hide overlay with animation
            recordingOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    recordingOverlay.visibility = View.GONE

                    // Show the input layout and controls again
                    binding.reasoningControlsLayout.visibility = View.VISIBLE
                    binding.inputCardLayout.visibility = View.VISIBLE

                    // Only show credits button if user is not subscribed and model isn't free
                    if (!PrefsManager.isSubscribed(this) && !ModelManager.selectedModel.isFree) {
                        binding.creditsButton.visibility = View.VISIBLE
                    }
                }
                .start()

            // Vibrate to indicate cancellation
            provideHapticFeedback(50)

            Timber.d("Voice recording cancelled")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Timber.d("Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Timber.d("Beginning of speech")

                // Additional feedback that microphone is active
                runOnUiThread {
                    // Start a small pulse animation on the recording overlay
                    recordingOverlay.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(200)
                        .withEndAction {
                            recordingOverlay.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Log RMS values for debugging
                if (rmsdB > -10) { // Only log significant sound
                    Timber.d("RMS changed: $rmsdB dB")
                }

                // Update the wave animation based on audio level
                updateWaveAnimation(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }

            override fun onEndOfSpeech() {
                Timber.d("End of speech")
            }

            @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }

                Timber.e("Speech recognition error: $errorMessage (code: $error)")

                // If actively processing a send action, handle appropriately
                if (isSpeechProcessing) {
                    // Check if we have partial results to use
                    if (lastPartialResults.isNotEmpty()) {
                        Timber.d("Using partial results after error: $lastPartialResults")
                        finishRecordingWithText(lastPartialResults)
                    } else {
                        Timber.d("No results available after error")
                        isSpeechProcessing = false

                        // Only show error if it's not just "no speech input" or no match
                        if (error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                            error != SpeechRecognizer.ERROR_NO_MATCH) {
                            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "No speech detected", Toast.LENGTH_SHORT).show()
                        }

                        // Finish recording UI
                        finishRecordingUI()
                    }
                } else {
                    // Regular error during recording (not during send)
                    // Only show error if it's not just "no speech input" or no match
                    if (error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }

                    // End recording UI
                    cancelVoiceRecording()
                }
            }

            @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Timber.d("Speech recognition result: $text")

                    // If we're actively processing a send action
                    if (isSpeechProcessing) {
                        finishRecordingWithText(text)
                    } else {
                        // Normal end of speech recognition
                        sendVoiceMessage(text)
                    }
                } else {
                    Timber.d("Speech recognition returned no results")

                    // If we're actively processing a send action
                    if (isSpeechProcessing) {
                        // Try to use partial results if available
                        if (lastPartialResults.isNotEmpty()) {
                            finishRecordingWithText(lastPartialResults)
                        } else {
                            Toast.makeText(this@MainActivity, "Sorry, I didn't catch that", Toast.LENGTH_SHORT).show()
                            finishRecordingUI()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Sorry, I didn't catch that", Toast.LENGTH_SHORT).show()
                        cancelVoiceRecording()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Get partial results to show in real-time
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    if (partialText.isNotEmpty()) {
                        // Store the partial result for potential use
                        lastPartialResults = partialText
                        Timber.d("Partial speech result: $partialText")
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not used
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun reloadAiMessage(message: ChatMessage) {
        if (isGenerating) {
            Toast.makeText(this, "Please wait for current generation to complete", Toast.LENGTH_SHORT).show()
            return
        }

        // IMPROVED: Check token limits before reload
        val modelId = ModelManager.selectedModel.id
        val isSubscribed = PrefsManager.isSubscribed(this)

        // Find the parent user message
        val parentUserMessage = findParentUserMessage(message)
        val parentText = parentUserMessage?.message ?: ""

        if (parentText.isNotEmpty()) {
            // Check if regeneration would exceed limits
            val (wouldExceed, available, _) = conversationTokenManager.wouldExceedLimit(parentText, modelId, isSubscribed)

            if (wouldExceed) {
                AlertDialog.Builder(this)
                    .setTitle("Cannot Reload Message")
                    .setMessage("Reloading this message would exceed token limits. Available tokens: $available")
                    .setPositiveButton("Start New Chat") { _, _ ->
                        startNewConversation()
                    }
                    .setNegativeButton("Summarize & Continue") { _, _ ->
                        summarizeAndStartNewChat()
                    }
                    .setNeutralButton("Cancel", null)
                    .show()
                return
            }
        }

        setGeneratingState(true)
        isReloadingMessage = true
        
        Timber.d("🔄 RELOAD: Setting isReloadingMessage = true for message ${message.id}")

        try {
            // CRITICAL FIX: Remove tokens for the original message that will be replaced
            conversationTokenManager.removeMessage(message.id)

            // Store the original message content as the first version if not already stored
            val originalMessageContent = message.message
            if (message.messageVersions.isEmpty()) {
                // Use the addVersion method to properly initialize versioning
                message.messageVersions.add(originalMessageContent)
                message.totalVersions = 1
                message.versionIndex = 0
                Timber.d("🔄 RELOAD: Added original message as first version. totalVersions=${message.totalVersions}")
            }
            
            Timber.d("🔄 RELOAD: Before regeneration - totalVersions=${message.totalVersions}, versionIndex=${message.versionIndex}, messageVersions.size=${message.messageVersions.size}")
            
            // Update the existing message for regeneration instead of creating new one
            message.message = ""
            message.isGenerating = true
            message.isRegenerated = true
            
            Timber.d("🔄 RELOAD: Prepared message for regeneration. Original saved: ${originalMessageContent.take(50)}")

            // Update the message in the adapter
            val messageIndex = chatAdapter.currentList.indexOfFirst { it.id == message.id }
            if (messageIndex != -1) {
                chatAdapter.notifyItemChanged(messageIndex)
            }

            // Log regeneration
            Timber.d("Regenerating message ${message.id} (updating existing message)")

            // Start API call with the existing message ID
            currentApiJob = aiChatService.startApiCall(
                conversationId = message.conversationId,
                messageId = message.id,
                isSubscribed = PrefsManager.isSubscribed(this),
                isModelFree = ModelManager.selectedModel.isFree,
                originalMessageText = parentText
            )
        } catch (e: Exception) {
            Timber.e(e, "Error in reloadAiMessage: ${e.message}")
            Toast.makeText(this, "Error preparing regeneration: ${e.message}", Toast.LENGTH_SHORT).show()
            setGeneratingState(false)
            isReloadingMessage = false
        }
    }

    /**
     * Navigate between message versions
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun navigateMessageVersion(message: ChatMessage, direction: Int) {
        val newIndex = message.versionIndex + direction
        
        if (newIndex >= 0 && newIndex < message.totalVersions) {
            message.navigateToVersion(newIndex)
            
            // Update the message in the adapter
            val messageIndex = chatAdapter.currentList.indexOfFirst { it.id == message.id }
            if (messageIndex != -1) {
                chatAdapter.notifyItemChanged(messageIndex)
            }
            
            // Save the updated message
            lifecycleScope.launch {
                conversationsViewModel.saveMessage(message)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun editMessage(message: ChatMessage) {
        showEditMessageDialog(message)
    }
    /**
     * Find the parent user message for an AI message
     */
    private fun findParentUserMessage(aiMessage: ChatMessage): ChatMessage? {
        // If parent message ID is not set, we can't find parent
        val parentId = aiMessage.parentMessageId ?: return null

        // Find the parent message in the current messages
        return chatAdapter.currentList.find { it.id == parentId }
    }

    private fun setupPrivateChatFeature() {
        // Initialize views
        btnPrivateChat = findViewById(R.id.btnPrivateChat)
        privateModeIndicator = findViewById(R.id.privateModeIndicator)

        // Load animations
        ghostFloatAnimation = AnimationUtils.loadAnimation(this, R.anim.ghost_float)
        ghostActivateAnimation = AnimationUtils.loadAnimation(this, R.anim.ghost_activate)
        fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        // Configure click listener
        btnPrivateChat.setOnClickListener {
            togglePrivateMode()
        }

        // Set long press listener for help tooltip
        btnPrivateChat.setOnLongClickListener {
            Toast.makeText(
                this,
                "Private Mode: When enabled, conversations won't be saved",
                Toast.LENGTH_LONG
            ).show()
            true
        }

        // IMPORTANT: Always start with private mode disabled when app opens
        isPrivateModeEnabled = false

        // Update UI based on initial state (off)
        updatePrivateModeUI(false)

        // Clear any private messages from previous sessions
        conversationsViewModel.clearPrivateMessages()

        Timber.d("Private chat feature initialized with private mode OFF")
    }

    // Add this method to toggle private mode
    private fun togglePrivateMode() {
        // Toggle the state
        isPrivateModeEnabled = !isPrivateModeEnabled

        // Play animation
        btnPrivateChat.startAnimation(ghostActivateAnimation)

        // Haptic feedback
        provideHapticFeedback(50)

        // Update UI with animation
        updatePrivateModeUI(true)

        // If turning on private mode and we have an active conversation
        if (isPrivateModeEnabled && currentConversationId != null && !currentConversationId!!.startsWith("private_")) {
            // Ask user if they want to start a new private chat
            AlertDialog.Builder(this)
                .setTitle("Enable Private Mode?")
                .setMessage("For best privacy, we recommend starting a new chat in private mode.")
                .setPositiveButton("Start New Chat") { _, _ ->
                    // Reset chat and start fresh private conversation
                    resetChat()
                }
                .setNegativeButton("Continue") { _, _ ->
                    // Just continue, next message will be private
                }
                .show()
        } else if (isPrivateModeEnabled) {
            // If turning on private mode with no active conversation, reset to ensure clean state
            resetChat()
        }

        // Show toast notification
        Toast.makeText(
            this,
            if (isPrivateModeEnabled) "Private mode enabled" else "Private mode disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updatePrivateModeUI(animate: Boolean) {
        // Update ghost icon tint
        if (isPrivateModeEnabled) {
            // Update icon appearance
            btnPrivateChat.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.colorAccent)))

            // Set chat title to "Private Chat"
            chatTitleView.text = "Private Chat"

            // Show indicator with animation if requested
            privateModeIndicator.visibility = View.VISIBLE
            if (animate) {
                privateModeIndicator.startAnimation(fadeInAnimation)
            }

            // Start periodic floating animation
            Handler(Looper.getMainLooper()).postDelayed({
                if (isPrivateModeEnabled) {
                    btnPrivateChat.startAnimation(ghostFloatAnimation)
                }
            }, 2000) // Delay before floating animation starts

            // ==== CUSTOMIZE INPUT LAYOUT FOR PRIVATE MODE ====

            // 1. Change input card background to private mode drawable
            binding.inputCardLayout.setBackgroundResource(R.drawable.input_card_background_private)

            // 2. Add a border effect by using elevation
            binding.inputCardLayout.elevation = resources.getDimension(R.dimen.private_mode_card_elevation)

            // 3. Change hint text
            binding.etInputText.hint = "Type a private message..."

            // 4. Change hint text color
            binding.etInputText.setHintTextColor(
                ContextCompat.getColor(this, R.color.private_mode_hint))

            // 5. Change the attach button tint
            binding.btnAttach.setColorFilter(
                ContextCompat.getColor(this, R.color.private_mode_active))

            // 6. Change the send/mic button background
            val sendButtonBackground = binding.btnSubmitText.background
            val micButtonBackground = binding.btnMicrophone.background

            if (sendButtonBackground is Drawable) {
                sendButtonBackground.setTint(ContextCompat.getColor(this, R.color.private_mode_active))
            }

            if (micButtonBackground is Drawable) {
                micButtonBackground.setTint(ContextCompat.getColor(this, R.color.private_mode_active))
            }

            // 7. Add ghost icon to input field
            addGhostIconToInputField()

        } else {
            // Reset icon appearance
            btnPrivateChat.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_primary)))

            // Reset title to "New Chat" only if we're not in an existing conversation
            if (currentConversationId == null || currentConversationId!!.startsWith("private_")) {
                chatTitleView.text = "New Chat"
            }

            // Hide indicator with animation if requested
            if (animate) {
                privateModeIndicator.startAnimation(fadeOutAnimation)
                fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        privateModeIndicator.visibility = View.GONE
                    }
                })
            } else {
                privateModeIndicator.visibility = View.GONE
            }

            // ==== RESET INPUT LAYOUT TO NORMAL ====

            // 1. Reset input card background to drawable
            binding.inputCardLayout.setBackgroundResource(R.drawable.input_card_background)

            // 2. Reset elevation to normal
            binding.inputCardLayout.elevation = resources.getDimension(R.dimen.normal_card_elevation)

            // 3. Reset hint text
            binding.etInputText.hint = "Type a message..."

            // 4. Reset hint text color
            binding.etInputText.setHintTextColor(
                ContextCompat.getColor(this, R.color.hint_color))

            // 5. Reset the attach button tint
            binding.btnAttach.setColorFilter(
                ContextCompat.getColor(this, R.color.accent_color))

            // 6. Reset the send/mic button background
            val sendButtonBackground = binding.btnSubmitText.background
            val micButtonBackground = binding.btnMicrophone.background

            if (sendButtonBackground is Drawable) {
                sendButtonBackground.setTint(ContextCompat.getColor(this, R.color.accent_color))
            }

            if (micButtonBackground is Drawable) {
                micButtonBackground.setTint(ContextCompat.getColor(this, R.color.accent_color))
            }

            // 7. Remove ghost icon from input field
            removeGhostIconFromInputField()
        }
    }
    private fun loadDefaultModel() {
        try {
            val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val defaultModelId = sharedPrefs.getString("default_ai_model_id", null)

            if (defaultModelId != null) {
                // Find the model by ID
                val modelIndex = ModelManager.models.indexOfFirst { it.id == defaultModelId }

                if (modelIndex != -1) {
                    // Set the spinner to this model
                    binding.spinnerModels.setSelection(modelIndex)

                    // Also update the ModelManager selection
                    ModelManager.selectedModel = ModelManager.models[modelIndex]

                    // CRITICAL FIX: Clear cache BEFORE updating UI
                    ModelValidator.clearCache()

                    // Apply color changes to UI elements
                    applyModelColorToUI(defaultModelId)

                    // Update controls visibility SYNCHRONOUSLY
                    updateControlsVisibility(defaultModelId)

                    // IMPORTANT: Explicitly update adapter
                    chatAdapter.updateCurrentModel(defaultModelId)

                    // Update credits visibility
                    updateCreditsVisibility()

                    Timber.d("Default model loaded: ${ModelManager.selectedModel.displayName}")
                } else {
                    // If model ID not found, use the first model
                    Timber.w("Default model ID $defaultModelId not found, using first model")
                    binding.spinnerModels.setSelection(0)
                    ModelManager.selectedModel = ModelManager.models[0]
                    chatAdapter.updateCurrentModel(ModelManager.models[0].id)
                }
            } else {
                // No default model set, use the first one
                Timber.d("No default model set, using first model")
                binding.spinnerModels.setSelection(0)
                ModelManager.selectedModel = ModelManager.models[0]
                chatAdapter.updateCurrentModel(ModelManager.models[0].id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading default model: ${e.message}")

            // Fallback to first model on error
            try {
                binding.spinnerModels.setSelection(0)
                ModelManager.selectedModel = ModelManager.models[0]
                chatAdapter.updateCurrentModel(ModelManager.models[0].id)
            } catch (e2: Exception) {
                Timber.e(e2, "Critical error setting fallback model")
            }
        }
    }    private fun addGhostIconToInputField() {
        try {
            // Create the ghost icon drawable
            val ghostDrawable = ContextCompat.getDrawable(this, R.drawable.ic_ghost_small)

            // Set the bounds for the drawable
            ghostDrawable?.setBounds(0, 0,
                ghostDrawable.intrinsicWidth,
                ghostDrawable.intrinsicHeight)

            // Set the drawable as the start compound drawable
            binding.etInputText.setCompoundDrawablesRelative(
                ghostDrawable, null, null, null)

            // Add padding between the drawable and text
            binding.etInputText.compoundDrawablePadding =
                resources.getDimensionPixelSize(R.dimen.ghost_padding)
        } catch (e: Exception) {
            Timber.e(e, "Error adding ghost icon to input field")
        }
    }

    /**
     * Helper method to remove the ghost icon from input field
     */
    private fun removeGhostIconFromInputField() {
        binding.etInputText.setCompoundDrawablesRelative(null, null, null, null)
    }

    private fun checkForSettingsChanges() {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val modelChanged = sharedPrefs.getBoolean("model_changed", false)

        if (modelChanged) {
            // Reset the flag
            sharedPrefs.edit { putBoolean("model_changed", false) }

            // Apply the new model settings
            loadDefaultModel()

            // Show confirmation to user
            Toast.makeText(this, "AI model updated to ${ModelManager.selectedModel.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPulsingAnimation() {
        // Stop any existing animation first
        stopPulsingAnimation()

        try {
            // Only start if we're actually generating
            if (!isGenerating) {
                Timber.d("Not starting pulse animation - not generating")
                return
            }

            pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f, 1.0f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()

                addUpdateListener { animator ->
                    try {
                        // Only update if we're still generating
                        if (isGenerating) {
                            val value = animator.animatedValue as Float
                            binding.btnSubmitText.scaleX = value
                            binding.btnSubmitText.scaleY = value
                            binding.btnSubmitText.alpha = if (value > 1.07f) 0.8f else 1.0f
                        }
                    } catch (e: Exception) {
                        // Ignore animation errors during cleanup
                    }
                }

                start()
            }

            Timber.d("Pulse animation started")
        } catch (e: Exception) {
            Timber.e(e, "Error starting pulse animation: ${e.message}")
        }
    }

    private fun stopPulsingAnimation() {
        try {
            pulseAnimator?.cancel()
            pulseAnimator = null

            // Reset button appearance only if binding is initialized
            if (::binding.isInitialized) {
                binding.btnSubmitText.scaleX = 1.0f
                binding.btnSubmitText.scaleY = 1.0f
                binding.btnSubmitText.alpha = 1.0f
            }

            Timber.d("Pulse animation stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping pulse animation: ${e.message}")
        }
    }

    private fun forceUpdateMessageInAdapter(message: ChatMessage) {
        Handler(Looper.getMainLooper()).post {
            try {
                val newList = chatAdapter.currentList.toMutableList()
                val index = newList.indexOfFirst { it.id == message.id }

                if (index != -1) {
                    // Set the new message in the list
                    newList[index] = message

                    // Update the adapter list using submitList with a callback
                    chatAdapter.submitList(newList) {
                        try {
                            // Only notify after the list update is complete
                            chatAdapter.notifyItemChanged(index)
                            Timber.tag("BUTTONS").d("Force updated message ${message.id}")
                        } catch (e: Exception) {
                            Timber.tag("BUTTONS").e(e, "Error notifying item changed: ${e.message}")
                        }
                    }
                } else {
                    Timber.tag("BUTTONS").e("Failed to force update: message ${message.id} not found in adapter")
                }
            } catch (e: Exception) {
                Timber.tag("BUTTONS").e(e, "Error in forceUpdateMessageInAdapter: ${e.message}")
            }
        }
    }
    /**
     * Update message in adapter
     */
    private fun updateMessageInAdapter(message: ChatMessage) {
        val newList = chatAdapter.currentList.toMutableList()
        val index = newList.indexOfFirst { it.id == message.id }

        if (index != -1) {
            // CRITICAL FIX: During streaming, use direct update to prevent UI resets
            if (message.isGenerating || chatAdapter.isStreamingActive) {
                chatAdapter.updateMessageDirectly(index, message)
                Timber.d("🔄 Direct update for streaming message: ${message.id}")
            } else {
                // Update existing message normally
                newList[index] = message
                chatAdapter.submitList(newList)
                chatAdapter.notifyItemChanged(index)
                Timber.d("📝 Normal update for message: ${message.id}")
            }
        } else {
            // Add new message if not found
            newList.add(message)
            chatAdapter.submitList(newList)
            Timber.d("➕ Added new message: ${message.id}")
        }
    }    /**
     * Update a message in the AI response groups
     */
    private fun updateMessageInGroups(message: ChatMessage) {
        // For user messages
        if (message.isUser && message.userGroupId != null) {
            val group = userMessageGroups[message.userGroupId] ?: return
            val index = group.indexOfFirst { it.id == message.id }
            if (index != -1) {
                group[index] = message
            }
        }
        // For AI messages
        else if (!message.isUser && message.aiGroupId != null) {
            val group = aiResponseGroups[message.aiGroupId] ?: return
            val index = group.indexOfFirst { it.id == message.id }
            if (index != -1) {
                group[index] = message
            }
        }
    }
    private fun loadMoreMessages() {
        if (isLoadingMore || currentConversationId == null) return

        isLoadingMore = true
        currentPage++

        conversationsViewModel.getMessagesByConversationId(currentConversationId.toString(), currentPage).observe(this) { messages ->
            if (messages.isNotEmpty()) {
                // Add loaded messages using the message manager
                messageManager.addMessages(messages)
            } else {
                currentPage--
            }
            isLoadingMore = false
        }
    }    /**
     * Restore buttons on error (recovery mechanism)
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun restoreButtonsOnError() {
        try {
            Timber.tag("RECOVERY").d("Running button recovery routine")

            // First reset the generation state flag
            chatAdapter.setGenerating(false)

            val currentList = chatAdapter.currentList.toMutableList()
            var updatedCount = 0

            // Find ANY AI messages and force buttons visible
            for (i in currentList.indices) {
                val message = currentList[i]
                if (!message.isUser) {
                    // Always force buttons visible for all AI messages during recovery
                    currentList[i] = message.copy(
                        isGenerating = false,
                        showButtons = true
                    )
                    updatedCount++

                    // Also update in the groups
                    val groupId = message.aiGroupId ?: message.id
                    aiResponseGroups[groupId]?.let { group ->
                        val groupIndex = group.indexOfLast { it.id == message.id }
                        if (groupIndex != -1) {
                            group[groupIndex] = currentList[i]
                        }
                    }
                }
            }

            // If we made changes, update the adapter
            if (updatedCount > 0) {
                Timber.tag("RECOVERY").d("Restored buttons for $updatedCount messages")
                chatAdapter.submitList(currentList)
                chatAdapter.notifyDataSetChanged() // Force full refresh
            }
        } catch (e: Exception) {
            Timber.tag("RECOVERY").e(e, "Error in restoreButtonsOnError: ${e.message}")
        }
    }

    /**
     * Save all message versions to database
     */

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun initializeButtons() {
        // Change the icon for btnConversationsList to a back button
        binding.btnConversationsList.setImageResource(R.drawable.back_button)
        binding.btnConversationsList.contentDescription = "Back to home"
        btnAudioConversation = findViewById(R.id.btnAudioConversation)

        val btnReasoning = findViewById<ImageButton>(R.id.btnReasoning)
        val btnDeepSearch = findViewById<ImageButton>(R.id.btnDeepSearch)

        // Set initial states based on saved preferences AND model capabilities
        val currentModel = ModelManager.selectedModel.id

        // Initialize button states
        updateReasoningButtonState(isReasoningEnabled)
        updateDeepSearchButtonState(isDeepSearchEnabled)

        // Set click listeners for reasoning and deep search
        btnReasoning.setOnClickListener {
            val currentModel = ModelManager.selectedModel.id

            when {

                else -> {
                    // Traditional reasoning toggle
                    isReasoningEnabled = !isReasoningEnabled
                    reasoningLevel = if (isReasoningEnabled) "high" else "low"

                    updateReasoningButtonState(isReasoningEnabled)

                    PrefsManager.saveAiSettings(this, AppSettings(
                        isDeepSearchEnabled = isDeepSearchEnabled,
                        isReasoningEnabled = isReasoningEnabled,
                        reasoningLevel = reasoningLevel
                    ))

                    Toast.makeText(this,
                        if (isReasoningEnabled) "Reasoning enabled (high)" else "Reasoning disabled (low)",
                        Toast.LENGTH_SHORT).show()

                    Timber.d("Traditional reasoning toggled: $isReasoningEnabled, level: $reasoningLevel")
                }
            }

            // Provide haptic feedback
            provideHapticFeedback(50)
        }
        btnDeepSearch.setOnClickListener {
            // Toggle state
            isDeepSearchEnabled = !isDeepSearchEnabled

            // Update button appearance
            updateDeepSearchButtonState(isDeepSearchEnabled)

            // Update real-time search UI
            updateRealTimeSearchUI(isDeepSearchEnabled)

            // Save settings
            PrefsManager.saveAiSettings(this, AppSettings(
                isDeepSearchEnabled = isDeepSearchEnabled,
                isReasoningEnabled = isReasoningEnabled,
                reasoningLevel = reasoningLevel
            ))

            // Show feedback
            Toast.makeText(this,
                if (isDeepSearchEnabled) "Web Search enabled" else "Web Search disabled",
                Toast.LENGTH_SHORT).show()

            // Provide haptic feedback
            provideHapticFeedback(50)
        }

        // Rest of button listeners
        binding.btnConversationsList.setOnClickListener {
            provideHapticFeedback(50)
            navigateToConversationsActivity()
        }


        binding.btnNewConversation.setOnClickListener {
            startNewConversation()
        }

        binding.btnAttach.setOnClickListener {
            showAttachmentMenu()
        }

        binding.btnSubmitText.setOnClickListener {
            if (isGenerating) {
                stopGeneration()
            } else {
                sendMessage()
            }
        }

        binding.creditsButton.setOnClickListener {
            showGetCreditsMenu()
        }

        btnAudioConversation.setOnClickListener {
            openRealTimeAudioConversation()
            provideHapticFeedback(50)
        }
    }
    private fun loadAiSettingsFromPrefs() {
        val settings = PrefsManager.getAiSettings(this)
        isReasoningEnabled = settings.isReasoningEnabled
        isDeepSearchEnabled = settings.isDeepSearchEnabled
        reasoningLevel = settings.reasoningLevel
        Timber.d("Loaded AI settings via PrefsManager")
    }

    fun updateReasoningButtonState(isEnabled: Boolean) {
        val currentModel = ModelManager.selectedModel.id

        btnReasoning.isSelected = isEnabled

        if (isEnabled) {
            btnReasoning.background = ContextCompat.getDrawable(this, R.drawable.circle_button_active)

        } else {
            btnReasoning.background = ContextCompat.getDrawable(this, R.drawable.circle_button_inactive)
            btnReasoning.setColorFilter("#757575".toColorInt())


        }
    }
    fun updateDeepSearchButtonState(isEnabled: Boolean) {
        btnDeepSearch.isSelected = isEnabled

        if (isEnabled) {
            btnDeepSearch.background = ContextCompat.getDrawable(this, R.drawable.circle_button_active)
            btnDeepSearch.setColorFilter(ContextCompat.getColor(this, R.color.white))
            btnDeepSearch.contentDescription = "Web Search ON"
        } else {
            btnDeepSearch.background = ContextCompat.getDrawable(this, R.drawable.circle_button_inactive)
            btnDeepSearch.setColorFilter("#757575".toColorInt())
            btnDeepSearch.contentDescription = "Web Search"
        }
    }

    private fun toggleReasoningLevel() {
        provideHapticFeedback(50)

        val currentModel = ModelManager.selectedModel.id

        when {

            else -> {
                // Traditional reasoning
                isReasoningEnabled = !isReasoningEnabled
                reasoningLevel = if (isReasoningEnabled) "high" else "low"

                updateReasoningButtonState(isReasoningEnabled)

                Toast.makeText(this,
                    if (isReasoningEnabled) "Reasoning enabled (high)" else "Reasoning disabled (low)",
                    Toast.LENGTH_SHORT).show()
            }
        }

        // Save settings
        PrefsManager.saveAiSettings(this, AppSettings(
            isDeepSearchEnabled = isDeepSearchEnabled,
            isReasoningEnabled = isReasoningEnabled,
            reasoningLevel = reasoningLevel
        ))
    }

    private fun toggleDeepSearch() {
        provideHapticFeedback(50)

        // Toggle the state
        isDeepSearchEnabled = !isDeepSearchEnabled

        // Update UI
        updateDeepSearchButtonState(isDeepSearchEnabled)

        // Save settings
        PrefsManager.saveAiSettings(this, AppSettings(
            isDeepSearchEnabled = isDeepSearchEnabled,
            isReasoningEnabled = isReasoningEnabled,
            reasoningLevel = reasoningLevel
        ))

        // Show feedback
        Toast.makeText(this,
            if (isDeepSearchEnabled) "Web Search enabled" else "Web Search disabled",
            Toast.LENGTH_SHORT).show()
    }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setupEnhancedButtonHandlers() {
        // Enhanced submit button click handler
        binding.btnSubmitText.setOnClickListener { view ->
            // Disable button temporarily to prevent double-clicks
            view.isEnabled = false

            // Re-enable after a short delay
            uiHandler.postDelayed({ view.isEnabled = true }, 500)

            if (isGenerating) {
                Timber.d("Stop button clicked - stopping generation")
                stopGeneration()
            } else {
                Timber.d("Send button clicked - sending message")
                sendMessage()
            }
        }

        // Microphone button handler remains the same
        binding.btnMicrophone.setOnClickListener {
            startVoiceRecording()
        }
    }
    /**
     * Take a photo using the camera
     */
    fun takePhoto() {
        Timber.d("takePhoto() called")

        // Check camera permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Timber.d("Requesting camera permission")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        try {
            // Make sure the model supports images
            if (!ModelValidator.supportsPhotoCapture(ModelManager.selectedModel.id)) {
                Toast.makeText(this, "The current AI model doesn't support photos", Toast.LENGTH_LONG).show()
                return
            }

            // Check if adding a new file would exceed the model's limit
            val maxFiles = ModelValidator.getMaxFilesForModel(ModelManager.selectedModel.id)
            if (maxFiles > 0 && selectedFiles.size >= maxFiles) {
                Toast.makeText(
                    this,
                    "You've reached the maximum number of files for this model ($maxFiles)",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Create file for photo
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath

            // Save path to shared preferences to restore if needed
            getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
                .edit {
                    putString("last_photo_path", currentPhotoPath)
                }

            Timber.d("Created photo file at: $currentPhotoPath")

            // Create URI via FileProvider
            val photoURI = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                photoFile
            )
            Timber.d("Created FileProvider URI: $photoURI")

            // Create camera intent with necessary flags
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Grant permissions to all potential camera apps
            packageManager.queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY).forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    photoURI,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            // Try to launch camera directly first - more reliable approach
            try {
                // Set flag to indicate we're going to camera
                isReturningFromFileSelection = true
                cameraLauncher.launch(takePictureIntent)
                Timber.d("Camera launcher executed successfully")
            } catch (e: ActivityNotFoundException) {
                // Fallback: Check if there are camera apps available
                val cameraActivities = packageManager.queryIntentActivities(
                    takePictureIntent, 
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                
                if (cameraActivities.isNotEmpty()) {
                    // There are camera apps, try again with explicit component
                    try {
                        val cameraActivity = cameraActivities[0]
                        takePictureIntent.component = android.content.ComponentName(
                            cameraActivity.activityInfo.packageName,
                            cameraActivity.activityInfo.name
                        )
                        cameraLauncher.launch(takePictureIntent)
                        Timber.d("Camera launched with explicit component")
                    } catch (e2: Exception) {
                        Timber.e(e2, "Failed to launch camera with explicit component")
                        // Final fallback - try simple camera intent
                        trySimpleCameraIntent()
                    }
                } else {
                    Timber.e(e, "No camera app found that can handle the intent")
                    Toast.makeText(this, "No camera apps found on your device", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error launching camera")
                Toast.makeText(this, "Error launching camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in takePhoto: ${e.message}")
            Toast.makeText(this, "Error launching camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun trySimpleCameraIntent() {
        try {
            val simpleCameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            startActivity(simpleCameraIntent)
            Timber.d("Launched simple camera app (no file capture)")
            Toast.makeText(this, "Camera opened. Please use gallery to select your photo after taking it.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch any camera app: ${e.message}")
            Toast.makeText(this, "No camera app available on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun openRealTimeAudioConversation() {
        try {
            // Double-check that the current model supports real-time audio conversations
            if (!ModelValidator.supportsRealTimeAudioConversation(ModelManager.selectedModel.id)) {
                Toast.makeText(
                    this,
                    "Real-time audio conversations are only available with GPT-4o Audio Preview",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Show a brief explanation of what this feature does
            AlertDialog.Builder(this)
                .setTitle("Start Audio Conversation")
                .setMessage("You're about to start a real-time audio conversation with AI. This works like a phone call - speak naturally and the AI will respond with voice.")
                .setPositiveButton("Start Call") { _, _ ->
                    launchAudioConversation()
                }
                .setNegativeButton("Cancel", null)
                .show()

        } catch (e: Exception) {
            Timber.e(e, "Error opening audio conversation: ${e.message}")
            Toast.makeText(this, "Error starting audio conversation: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchAudioConversation() {
        try {
            // Launch AudioAiActivity
            val intent = Intent(this, AudioAiActivity::class.java).apply {
                // Pass the current model info
                putExtra("MODEL_ID", ModelManager.selectedModel.id)
                putExtra("MODEL_NAME", ModelManager.selectedModel.displayName)
                putExtra("IS_AUDIO_CONVERSATION", true)

                // Pass any current conversation context if needed
                currentConversationId?.let {
                    putExtra("CONVERSATION_ID", it)
                }
            }

            startActivity(intent)

            // Add a smooth transition with phone call feel
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

            // Show toast about the feature
            Toast.makeText(
                this,
                "Starting audio conversation with ${ModelManager.selectedModel.displayName}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Timber.e(e, "Error launching audio conversation: ${e.message}")
            Toast.makeText(this, "Error launching audio conversation: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Launch camera after permission check
     */
    private fun launchCameraDirectly() {
        try {
            // Explicitly log what we're doing
            Timber.d("Launching camera directly")

            // Create file for photo
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath

            Timber.d("Created photo file at: $currentPhotoPath")

            // Get content URI via FileProvider
            val photoURI = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                photoFile
            )

            Timber.d("Created FileProvider URI: $photoURI")

            // Create camera intent
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

            // Grant URI permissions
            val resInfoList = packageManager.queryIntentActivities(
                takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY)

            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    photoURI,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            // Check if intent can be resolved
            if (resInfoList.isEmpty()) {
                Timber.e("No camera apps found that can handle the intent")
                Toast.makeText(this, "No camera apps found on your device", Toast.LENGTH_LONG).show()

                // Debug camera apps
                debugCameraAvailability()
                return
            }

            // Launch camera
            isReturningFromFileSelection = true
            cameraLauncher.launch(takePictureIntent)

        } catch (e: Exception) {
            Timber.e(e, "Error launching camera: ${e.message}")
            Toast.makeText(this, "Error launching camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun logDeviceInfo() {
        Timber.d("Device Info:")
        Timber.d("Manufacturer: ${Build.MANUFACTURER}")
        Timber.d("Model: ${Build.MODEL}")
        Timber.d("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")

        // Log camera features
        Timber.d("Camera features:")
        Timber.d("Has camera: ${packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)}")
        Timber.d("Has front camera: ${packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)}")

        // Log camera apps
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val cameraApps = packageManager.queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
        Timber.d("Camera apps found: ${cameraApps.size}")
        cameraApps.forEach { resolveInfo ->
            Timber.d("Camera app: ${resolveInfo.activityInfo.packageName}")
        }
    }
    /**
     * Create image file for camera
     */
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        // Try multiple potential storage directories in case some fail
        val potentialStorageDirs = listOf(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            filesDir.resolve("Pictures"),
            cacheDir.resolve("Pictures")
        )

        // Find first directory that exists or can be created
        val storageDir = potentialStorageDirs.firstOrNull { dir ->
            if (dir?.exists() == true) return@firstOrNull true

            try {
                if (dir?.mkdirs() == true) return@firstOrNull true
            } catch (e: Exception) {
                Timber.e(e, "Failed to create directory: ${dir?.absolutePath}")
            }
            false
        } ?: cacheDir  // Fallback to cache directory if all else fails

        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            Timber.d("Created image file: $absolutePath")
        }
    }

    private fun debugCameraAvailability() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val cameraApps = packageManager.queryIntentActivities(
            cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)

        Timber.d("Camera apps found: ${cameraApps.size}")
        for (app in cameraApps) {
            Timber.d("Camera app available: ${app.activityInfo.packageName}")
        }

        // Check other camera-related intents
        val stillImageIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val stillImageApps = packageManager.queryIntentActivities(
            stillImageIntent, PackageManager.MATCH_DEFAULT_ONLY)
        Timber.d("Still image camera apps found: ${stillImageApps.size}")

        // Check if camera hardware is detected
        val hasCameraFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        Timber.d("Device has camera hardware: $hasCameraFeature")

        // If we found no apps but have camera hardware, this suggests a permission or system issue
        if (cameraApps.isEmpty() && hasCameraFeature) {
            Timber.w("Device has camera hardware but no apps can handle camera intent")

            // Check if we have camera permission
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            Timber.d("Camera permission granted: $hasCameraPermission")

            // If permission is missing, request it - this might fix the second-click issue
            if (!hasCameraPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // -------------------------------------------------------------------------
    // CREDITS AND SUBSCRIPTION MANAGEMENT
    // -------------------------------------------------------------------------

    /**
     * Update free messages counter text
     */
    @SuppressLint("SetTextI18n")
    fun updateFreeMessagesText() {
        if (PrefsManager.isSubscribed(this)) {
            // Don't update text for subscribers as it's hidden anyway
            return
        }
        
        // Check if creditManager is initialized before using it
        if (!::creditManager.isInitialized) {
            Timber.w("creditManager not initialized yet, skipping updateFreeMessagesText")
            return
        }
        
        val currentCredits = creditManager.getChatCredits()
        binding.tvCreditsCount.text = currentCredits.toString()
        
        // Update send button state when credits change
        updateButtonStateInternal(isGenerating)
    }

    /**
     * Update UI based on subscription status
     */
    private fun updateUIBasedOnSubscriptionStatus() {
        val isSubscribed = PrefsManager.isSubscribed(this)

        // Update credits visibility
        if (isSubscribed) {
            // Hide credit-related UI when subscribed
            binding.creditsButton.visibility = View.GONE

            // Also release ad resources if user is subscribed
            // AdManager handles cleanup automatically
        } else {
            // Show credit-related UI when not subscribed
            binding.creditsButton.visibility = if (!ModelManager.selectedModel.isFree) View.VISIBLE else View.GONE

            // Update the free messages text
            updateFreeMessagesText()
        }

        // Update token counter to reflect subscription status changes
        val tvTokenCount = findViewById<TextView>(R.id.tvTokenCount)
        if (tvTokenCount != null) {
            val currentText = binding.etInputText.text?.toString() ?: ""
            val modelId = ModelManager.selectedModel.id

            // Update token counter with new subscription status
            TokenCounterHelper.updateTokenCounter(
                tvTokenCount,
                currentText,
                modelId,
                isSubscribed,
                this
            )
        }

        // Update ad settings based on subscription
        if (isSubscribed) {
            PrefsManager.setShouldShowAds(this, false)
        } else {
            // Restore ad settings to default
            PrefsManager.setShouldShowAds(this, true)
        }
    }    private fun handleConsentAndAds() {
        // Skip if already handled or user is subscribed
        if (consentHandled || PrefsManager.isSubscribed(this)) {
            Timber.d("Skipping consent handling - already handled or user subscribed")
            return
        }

        try {
            if (consentManager.shouldShowConsentDialog() && !consentDialogShown) {
                // Set flag to prevent showing consent multiple times
                consentDialogShown = true
                consentHandled = true

                // Only show consent if user is actively using the app
                if (!isFinishing && !isDestroyed) {
                    Timber.d("Showing consent dialog")
                    consentManager.showConsentDialog(this) {
                        // Initialize ads after consent
                        Handler(Looper.getMainLooper()).postDelayed({
                            Timber.d("Initializing ads after consent")
                            initializeAds()
                        }, 1000)
                    }
                }
            } else {
                // Consent already obtained
                Timber.d("Consent already obtained, initializing ads")
                consentHandled = true

                // Initialize ads with slight delay
                Handler(Looper.getMainLooper()).postDelayed({
                    initializeAds()
                }, 1000)
            }

            // Always update UI based on subscription status
            updateUIBasedOnSubscriptionStatus()
        } catch (e: Exception) {
            Timber.e(e, "Error handling consent and ads: ${e.message}")
            consentHandled = true
        }
    }
    /**
     * Update visibility of credits UI based on selected model
     */
    private fun updateCreditsVisibility() {
        // First check if user is subscribed - subscribers don't see credit UI
        if (PrefsManager.isSubscribed(this)) {
            binding.creditsButton.visibility = View.GONE
            return
        }

        // For non-subscribers, show credits for paid models
        binding.creditsButton.visibility = if (ModelManager.selectedModel.isFree) View.GONE else View.VISIBLE
        updateFreeMessagesText()
    }



    fun navigateToWelcomeActivity() {
        Intent(this@MainActivity, WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
    }

    /**
     * Navigate to Conversations Activity
     */
    private fun navigateToConversationsActivity() {
        startActivity(Intent(this@MainActivity, HistoryFragment::class.java))
    }

    /**
     * Copy text to clipboard
     */
    fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Scroll to bottom of chat
     */
    fun scrollToBottom() {
        try {
            binding.chatRecyclerView.post {
                if (chatAdapter.itemCount > 0) {
                    binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)

                    // CRITICAL FIX: Remove haptic feedback to prevent constant vibration during streaming
                    // HapticManager.lightVibration(this)

                    Timber.v("Scrolling to bottom") // Changed to verbose to reduce log spam
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scrolling to bottom: ${e.message}")
        }
    }

    /**
     * CRITICAL FIX: Check if user is at bottom of chat to prevent auto-scroll during manual scrolling
     */
    private fun isUserAtBottom(): Boolean {
        return try {
            val layoutManager = binding.chatRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            if (layoutManager != null && chatAdapter.itemCount > 0) {
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItems = chatAdapter.itemCount
                // Consider user at bottom if they're within 2 items of the bottom
                lastVisiblePosition >= totalItems - 3
            } else {
                true // Default to true if we can't determine position
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking if user is at bottom: ${e.message}")
            true // Default to true on error
        }
    }

    /**
     * Show keyboard
     */
    fun showKeyboard() {
        binding.etInputText.requestFocus()

        // ADD THIS BLOCK
        // Hide scroll arrows temporarily when showing keyboard
        if (::scrollArrowsHelper.isInitialized) {
            scrollArrowsHelper.hideButtonsTemporarily(3000)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etInputText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }
    /**
     * Force show keyboard
     */
    private fun forceShowKeyboard() {
        binding.etInputText.requestFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    /**
     * Hide keyboard
     */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        // ADD THIS LINE
        // Temporarily hide scroll arrows when keyboard is shown
        if (::scrollArrowsHelper.isInitialized) {
            scrollArrowsHelper.hideButtonsTemporarily(2000)
        }
    }
    /**
     * Show error message
     */
    private fun showError(message: String) {
        // Check if the error message is actually meaningful
        val isBenignError = message.contains("Unknown error") ||
                message.contains("System error") ||
                message.isEmpty() ||
                message.contains("null")

        // Only log benign errors, don't show to user
        if (isBenignError) {
            Timber.tag(TAG).w("Suppressing benign error: $message")
            return
        }

        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Timber.tag(TAG).e("Error shown to user: $message")
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun setGeneratingState(generating: Boolean) {
        // First update the local state variable
        isGenerating = generating

        // Always use runOnUiThread to ensure thread safety
        runOnUiThread {
            try {
                if (generating) {
                    // Immediately change to stop button - no delay
                    binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                    binding.btnSubmitText.contentDescription = "Stop generation"
                    binding.btnSubmitText.visibility = View.VISIBLE
                    binding.btnMicrophone.visibility = View.GONE

                    // Start animation immediately
                    startPulsingAnimation()

                    // Start monitoring to ensure button stays visible
                    monitorGenerationButtonState()
                } else {
                    // Stop animation first
                    stopPulsingAnimation()

                    // Reset button state based on input text
                    val inputText = binding.etInputText.text.toString().trim()
                    if (inputText.isEmpty()) {
                        binding.btnSubmitText.visibility = View.GONE
                        binding.btnMicrophone.visibility = View.VISIBLE
                    } else {
                        binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                        binding.btnSubmitText.contentDescription = "Send message"
                        binding.btnSubmitText.visibility = View.VISIBLE
                        binding.btnMicrophone.visibility = View.GONE
                    }
                }

                // Update adapter state immediately, not in a separate post
                chatAdapter.setGenerating(generating)
            } catch (e: Exception) {
                Timber.e(e, "Error in setGeneratingState: ${e.message}")
                // Try to restore to a good state
                forceUpdateButtonState()
            }
        }
    }    private fun forceUpdateButtonState() {
        uiHandler.removeCallbacksAndMessages(null)  // Remove any pending updates
        uiHandler.post {
            try {
                // Get current state of text input
                val inputText = binding.etInputText.text.toString().trim()

                if (isGenerating) {
                    // Should be in stop mode
                    binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                    binding.btnSubmitText.contentDescription = "Stop generation"
                    binding.btnSubmitText.visibility = View.VISIBLE
                    binding.btnMicrophone.visibility = View.GONE

                    // Try restarting animation if needed
                    if (pulseAnimator == null || pulseAnimator?.isRunning == false) {
                        startPulsingAnimation()
                    }
                } else {
                    if (inputText.isEmpty()) {
                        // Should be in microphone mode
                        binding.btnMicrophone.visibility = View.VISIBLE
                        binding.btnSubmitText.visibility = View.GONE

                        // Ensure any pulsing is stopped
                        stopPulsingAnimation()
                    } else {
                        // Should be in send mode
                        binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                        binding.btnSubmitText.contentDescription = "Send message"
                        binding.btnSubmitText.visibility = View.VISIBLE
                        binding.btnMicrophone.visibility = View.GONE

                        // Ensure any pulsing is stopped
                        stopPulsingAnimation()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in forceUpdateButtonState: ${e.message}")

                // Set safe defaults in case of error
                binding.btnMicrophone.visibility = View.VISIBLE
                binding.btnSubmitText.visibility = View.GONE
                stopPulsingAnimation()
            }
        }
    }
    // Add this method to periodically check button state during generation
    private fun monitorGenerationButtonState() {
        if (!isGenerating) return

        // Check that the button is properly showing
        if (binding.btnSubmitText.visibility != View.VISIBLE ||
            binding.btnSubmitText.drawable == null ||
            pulseAnimator == null || !pulseAnimator!!.isRunning) {

            Timber.d("Button state incorrect during generation - fixing")

            // Force immediate update to correct state
            runOnUiThread {
                binding.btnSubmitText.setImageResource(R.drawable.stop_icon)
                binding.btnSubmitText.contentDescription = "Stop generation"
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnMicrophone.visibility = View.GONE

                // Restart animation if needed
                if (pulseAnimator == null || !pulseAnimator!!.isRunning) {
                    startPulsingAnimation()
                }
            }
        }

        // Schedule next check - shorter interval for faster recovery
        uiHandler.removeCallbacksAndMessages(null)  // Clear any pending updates
        uiHandler.postDelayed({ monitorGenerationButtonState() }, 500)
    }
    private fun saveAiMessageHistory() {
        sharedPrefs.edit {
            val gson = Gson()
            putString("ai_message_history", gson.toJson(aiMessageHistory))
            putString("current_message_index", gson.toJson(currentMessageIndex))
            // Also save the aiResponseGroups
            putString("ai_response_groups", gson.toJson(aiResponseGroups))
        }
    }

    /**
     * Load AI message history from preferences
     */
    private fun loadAiMessageHistory() {
        val gson = Gson()
        sharedPrefs.getString("ai_message_history", null)?.let {
            val type = object : TypeToken<MutableMap<String, MutableList<ChatMessage>>>() {}.type
            aiMessageHistory.putAll(gson.fromJson(it, type))
        }
        sharedPrefs.getString("ai_response_groups", null)?.let {
            val type = object : TypeToken<MutableMap<String, MutableList<ChatMessage>>>() {}.type
            aiResponseGroups.putAll(gson.fromJson(it, type))
        }
        
        // CRITICAL FIX: Clean up any existing messages with interruption text that shouldn't have it
        cleanupInterruptionMessages()
    }
    
    /**
     * CRITICAL FIX: Remove interruption messages from database messages that have substantial content
     * This fixes messages that were previously marked with interruption text but actually have content
     */
    private fun cleanupInterruptionMessages() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clean up messages in current conversation if available
                    if (currentConversationId != null) {
                        val messages = conversationsViewModel.getAllConversationMessages(currentConversationId!!)
                        cleanupMessagesInList(messages)
                    }
                    
                    // Also clean up any visible messages in the adapter
                    val currentMessages = chatAdapter.currentList
                    if (currentMessages.isNotEmpty()) {
                        cleanupMessagesInList(currentMessages)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up interruption messages: ${e.message}")
            }
        }
    }
    
    /**
     * CRITICAL FIX: Immediate cleanup that runs on app start
     * This catches and fixes corrupted messages before they can be displayed
     */
    private fun performImmediateMessageCleanup() {
        try {
            Timber.d("🧹 Starting immediate message cleanup on app start")
            
            // Use coroutine to run cleanup immediately but asynchronously
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val database = AppDatabase.getDatabase(this@MainActivity)
                        
                        // Find all messages with interruption text
                        val allMessages = database.chatMessageDao().getAllMessages()
                        val corruptedMessages = allMessages.filter { message ->
                            !message.isUser && 
                            message.message.contains("⚠️ Generation was interrupted. Tap 'Continue' to resume.")
                        }
                        
                        Timber.d("🧹 Found ${corruptedMessages.size} corrupted messages to fix immediately")
                        
                        corruptedMessages.forEach { message ->
                            val cleanedMessage = message.message
                                .replace("\n\n⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                                .replace("⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                                .trim()
                            
                            if (cleanedMessage.length > 50) {
                                val fixedMessage = message.copy(
                                    message = cleanedMessage,
                                    isGenerating = false,
                                    showButtons = true,
                                    canContinueStreaming = false,
                                    error = false,
                                    lastModified = System.currentTimeMillis()
                                )
                                
                                database.chatMessageDao().update(fixedMessage)
                                Timber.d("🧹 IMMEDIATE: Fixed message ${message.id} with ${cleanedMessage.length} chars")
                            }
                        }
                        
                        if (corruptedMessages.isNotEmpty()) {
                            Timber.d("✅ IMMEDIATE: Fixed ${corruptedMessages.size} corrupted messages")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in immediate message cleanup: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting immediate cleanup: ${e.message}")
        }
    }
    
    /**
     * Helper function to clean up interruption messages from a list of messages
     */
    private suspend fun cleanupMessagesInList(messages: List<ChatMessage>) {
        val messagesToFix = messages.filter { message ->
            !message.isUser && 
            message.message.contains("⚠️ Generation was interrupted. Tap 'Continue' to resume.") &&
            message.message.replace("⚠️ Generation was interrupted. Tap 'Continue' to resume.", "").trim().length > 50
        }
        
        Timber.d("🧹 Found ${messagesToFix.size} messages to clean up in this batch")
        
        messagesToFix.forEach { message ->
            // Remove the interruption text
            val cleanedMessage = message.message
                .replace("\n\n⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                .replace("⚠️ Generation was interrupted. Tap 'Continue' to resume.", "")
                .trim()
            
            if (cleanedMessage.isNotEmpty() && cleanedMessage != message.message) {
                val fixedMessage = message.copy(
                    message = cleanedMessage,
                    isGenerating = false,
                    showButtons = true,
                    canContinueStreaming = false,
                    error = false,
                    lastModified = System.currentTimeMillis()
                )
                
                // Update in database
                conversationsViewModel.saveMessage(fixedMessage)
                
                // Update in adapter if it's currently visible
                runOnUiThread {
                    updateMessageInAdapter(fixedMessage)
                }
                
                Timber.d("🧹 Cleaned message ${message.id}: removed interruption text, content length: ${cleanedMessage.length}")
            }
        }
        
        if (messagesToFix.isNotEmpty()) {
            Timber.d("✅ Cleaned up ${messagesToFix.size} messages with interruption text")
        }
    }

    /**
     * Load initial data
     */
    private fun loadInitialData() {
        try {
            // Load AI message history
            loadAiMessageHistory()

            // Load conversation data if needed
            // This is now lighter since most initialization is in StartActivity

            Timber.d("Initial chat data loaded")
        } catch (e: Exception) {
            Timber.e(e, "Error loading initial data: ${e.message}")
        }
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
                navigateToWelcomeActivity()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    // ================================================================================
    // BACKGROUND SERVICE METHODS
    // ================================================================================
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBackgroundGenerationReceiver() {
        try {
            val intentFilter = IntentFilter().apply {
                addAction(BackgroundAiService.ACTION_GENERATION_PROGRESS)
                addAction(BackgroundAiService.ACTION_EXISTING_PROGRESS)
                addAction(BackgroundAiService.ACTION_GENERATION_COMPLETE)
                addAction(BackgroundAiService.ACTION_GENERATION_ERROR)
            }
            
            // CRITICAL FIX: Add RECEIVER_NOT_EXPORTED flag for Android 34+ security requirement
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(backgroundGenerationReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(backgroundGenerationReceiver, intentFilter)
            }
            
            Timber.d("📻 Registered background generation receiver")
        } catch (e: Exception) {
            Timber.e(e, "Error registering background generation receiver: ${e.message}")
        }
    }
    
    private fun unregisterBackgroundGenerationReceiver() {
        try {
            unregisterReceiver(backgroundGenerationReceiver)
            Timber.d("📻 Unregistered background generation receiver")
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering background generation receiver: ${e.message}")
        }
    }
    
    private fun bindToBackgroundService() {
        try {
            val intent = Intent(this, BackgroundAiService::class.java)
            bindService(intent, backgroundServiceConnection, Context.BIND_AUTO_CREATE)
            Timber.d("🔗 Binding to background service")
        } catch (e: Exception) {
            Timber.e(e, "Error binding to background service: ${e.message}")
        }
    }
    
    private fun unbindFromBackgroundService() {
        try {
            if (isServiceBound) {
                unbindService(backgroundServiceConnection)
                isServiceBound = false
                backgroundAiService = null
                Timber.d("💔 Unbinded from background service")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error unbinding from background service: ${e.message}")
        }
    }
    
    /**
     * Clean up UI-related streaming state when activity exits
     * This prevents memory leaks while keeping background generation running
     */
    private fun cleanupUIStreamingState() {
        try {
            Timber.d("🧹 Cleaning up UI streaming state")
            
            // Stop any UI streaming animations
            if (::chatAdapter.isInitialized) {
                chatAdapter.stopStreamingModeGradually()
                Timber.d("🎬 Stopped UI streaming mode")
            }
            
            // Clear any pending UI update jobs but don't cancel background generation
            if (::messageManager.isInitialized) {
                // Note: messageManager doesn't have clearPendingUIUpdates method
                // Just log that we're cleaning up
                Timber.d("🗑️ MessageManager cleanup completed")
            }
            
            // Mark all active streaming sessions as background active
            // This ensures they continue in the background but UI updates stop
            currentConversationId?.let { conversationId ->
                val activeSessions = StreamingStateManager.getActiveSessionsForConversation(conversationId)
                activeSessions.forEach { session ->
                    // CRITICAL: Update the session with latest content from adapter before marking as background
                    val currentMessage = chatAdapter.currentList.find { it.id == session.messageId }
                    if (currentMessage != null && currentMessage.message.isNotEmpty()) {
                        StreamingStateManager.setPartialContent(session.messageId, currentMessage.message)
                        Timber.d("💾 Saved current UI content to session: ${session.messageId} (${currentMessage.message.length} chars)")
                    }
                    
                    StreamingStateManager.markAsBackgroundActive(session.messageId)
                    Timber.d("📍 Marked session as background active: ${session.messageId}")
                }
                
                if (activeSessions.isNotEmpty()) {
                    Timber.d("✅ Preserved ${activeSessions.size} streaming sessions for background continuation")
                }
            }
            
            Timber.d("✅ UI streaming state cleanup completed")
            
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up UI streaming state: ${e.message}")
        }
    }
    
    private fun transferGenerationToBackground() {
        try {
            // CRITICAL FIX: Save current content from UI adapter to database before transferring
            if (::chatAdapter.isInitialized) {
                val currentMessages = chatAdapter.currentList
                val generatingMessages = currentMessages.filter { it.isGenerating }
                
                for (message in generatingMessages) {
                    // Save current content to database immediately
                    if (message.message.isNotEmpty()) {
                        lifecycleScope.launch {
                            try {
                                conversationsViewModel.saveMessage(message)
                                Timber.d("💾 Saved current content to database before background transfer: ${message.id} (${message.message.length} chars)")
                            } catch (e: Exception) {
                                Timber.e(e, "Error saving message before background transfer: ${e.message}")
                            }
                        }
                    }
                    
                    // Save to streaming state manager as well
                    StreamingStateManager.setPartialContent(message.id, message.message)
                }
            }
            
            if (::aiChatService.isInitialized) {
                val generatingMessages = aiChatService.getGeneratingMessages()
                
                for (message in generatingMessages) {
                    val conversationId = message.conversationId
                    
                    Timber.d("🔄 Transferring message ${message.id} to background service")
                    aiChatService.transferToBackgroundService(message.id, conversationId)
                }
                
                if (generatingMessages.isNotEmpty()) {
                    Timber.d("✅ Transferred ${generatingMessages.size} messages to background service")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error transferring generation to background: ${e.message}")
        }
    }
    
    /**
     * CRITICAL FIX: Handle existing progress without overwriting current content
     * This is called when reconnecting to ongoing generation
     */
    private fun handleExistingProgress(messageId: String, content: String) {
        runOnUiThread {
            try {
                Timber.d("📨 Received existing progress for message: $messageId, content length: ${content.length}")
                
                // CRITICAL FIX: Only proceed if chatAdapter is initialized
                if (!::chatAdapter.isInitialized) {
                    Timber.w("⚠️ ChatAdapter not initialized, skipping existing progress update")
                    return@runOnUiThread
                }
                
                // Find the message in the adapter
                val currentList = chatAdapter.currentList
                val index = currentList.indexOfFirst { it.id == messageId }
                
                if (index != -1) {
                    val currentMessage = currentList[index]
                    val currentContent = currentMessage.message
                    
                    // CRITICAL FIX: Always show existing content if message is generating/loading
                    // Only skip if current content is significantly longer (to prevent overwriting newer with older)
                    val isMessageStillActive = currentMessage.isLoading || currentMessage.isGenerating
                    val currentContentIsSignificantlyLonger = currentContent.length > content.length + 100 // Current has 100+ more chars
                    
                    val shouldUpdate = isMessageStillActive && !currentContentIsSignificantlyLonger
                    
                    if (shouldUpdate) {
                        Timber.d("📝 Updating with existing content: current=${currentContent.length}, existing=${content.length}")
                        
                        // Start streaming mode if not already active
                        if (!chatAdapter.isStreamingActive) {
                            chatAdapter.startStreamingMode()
                            Timber.d("🎬 Started streaming mode for existing progress")
                        }
                        
                        // CRITICAL FIX: Mark this as existing content to prevent overwriting by subsequent progress updates
                        StreamingStateManager.setPartialContent(messageId, content)
                        
                        // Update message state for streaming
                        val streamingMessage = currentMessage.copy(
                            message = content,
                            isGenerating = true,
                            isLoading = false,
                            showButtons = false,
                            lastModified = System.currentTimeMillis(),
                            partialContent = content // Store as partial content to preserve it
                        )
                        
                        // Use direct update to avoid UI flicker
                        chatAdapter.updateMessageDirectly(index, streamingMessage)
                        chatAdapter.updateStreamingContentDirect(messageId, content, processMarkdown = true, isStreaming = true)
                        
                        Timber.d("✅ Updated with existing content without reset")
                    } else {
                        Timber.d("ℹ️ Skipping existing progress update - current content is significantly longer: currentLength=${currentContent.length}, existingLength=${content.length}, isActive=${isMessageStillActive}")
                        
                        // CRITICAL FIX: Still ensure the message is in proper streaming state if actively generating
                        if (currentMessage.isGenerating && !chatAdapter.isStreamingActive) {
                            chatAdapter.startStreamingMode()
                            Timber.d("🎬 Started streaming mode for active generation")
                        }
                    }
                }
                
                // Don't auto-scroll when restoring existing content
                // User should see exactly where they left off
                
            } catch (e: Exception) {
                Timber.e(e, "Error handling existing progress: ${e.message}")
            }
        }
    }
    
    private fun handleBackgroundProgress(messageId: String, content: String) {
        runOnUiThread {
            try {
                Timber.d("📨 Received background progress for message: $messageId, content length: ${content.length}")
                
                // CRITICAL FIX: Only proceed if chatAdapter is initialized
                if (!::chatAdapter.isInitialized) {
                    Timber.w("⚠️ ChatAdapter not initialized, skipping background progress update")
                    return@runOnUiThread
                }
                
                // CRITICAL FIX: Start streaming mode if not already active
                if (!chatAdapter.isStreamingActive) {
                    chatAdapter.startStreamingMode()
                    Timber.d("🎬 Started streaming mode for background progress")
                }
                
                // Use MessageManager to update streaming content with animation
                if (::messageManager.isInitialized) {
                    // CRITICAL FIX: Don't use submitList during streaming - it causes flicker!
                    // Just update the message state once and use direct updates for content
                    val currentList = chatAdapter.currentList
                    val index = currentList.indexOfFirst { it.id == messageId }
                    
                    if (index != -1) {
                        val currentMessage = currentList[index]
                        val currentContent = currentMessage.message
                        
                        // CRITICAL FIX: Only update if this is genuine new progress (longer content)
                        // This prevents short content from overwriting longer existing content
                        if (content.length >= currentContent.length || currentMessage.isLoading) {
                            if (!currentMessage.isGenerating) {
                                // Fix the message state ONLY ONCE using direct update
                                val correctedMessage = currentMessage.copy(
                                    isGenerating = true,
                                    isLoading = false,
                                    showButtons = false
                                )
                                chatAdapter.updateMessageDirectly(index, correctedMessage)
                                Timber.d("🔧 Fixed message state: isGenerating was false, now true")
                            }
                            
                            // CRITICAL FIX: Save streaming chunks to database with throttling
                            val updatedMessage = currentMessage.copy(
                                message = content,
                                partialContent = content,
                                isGenerating = true,
                                isLoading = false,
                                showButtons = false,
                                lastModified = System.currentTimeMillis()
                            )
                            
                            // Save to database with intelligent throttling (every 50+ chars or 2 seconds)
                            val lastSaveTime = updatedMessage.lastModified ?: 0
                            val timeSinceLastSave = System.currentTimeMillis() - lastSaveTime
                            val contentDifference = content.length - currentContent.length
                            
                            if (contentDifference >= 50 || timeSinceLastSave >= 2000) {
                                lifecycleScope.launch {
                                    try {
                                        conversationsViewModel.saveMessage(updatedMessage)
                                        Timber.d("💾 Saved streaming chunk to database: ${content.length} chars (diff: +${contentDifference})")
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error saving streaming chunk: ${e.message}")
                                    }
                                }
                            } else {
                                Timber.v("⏭️ Skipping database save (throttled): ${content.length} chars (diff: +${contentDifference})")
                            }
                            
                            // Use direct streaming method to bypass submitList entirely
                            chatAdapter.updateStreamingContentDirect(messageId, content, processMarkdown = true, isStreaming = true)
                            Timber.d("✅ Updated streaming content directly, content length: ${content.length}")
                        } else {
                            Timber.d("⚠️ Skipping shorter progress update: current=${currentContent.length}, new=${content.length}")
                        }
                    }
                } else {
                    // Fallback: Direct adapter update without streaming animation
                    val currentList = chatAdapter.currentList.toMutableList()
                    val index = currentList.indexOfFirst { it.id == messageId }
                    
                    if (index != -1) {
                        val updatedMessage = currentList[index].copy(
                            message = content,
                            isGenerating = true,
                            isLoading = false,
                            showButtons = false,
                            lastModified = System.currentTimeMillis()
                        )
                        
                        currentList[index] = updatedMessage
                        chatAdapter.submitList(currentList)
                        updateMessageInAdapter(updatedMessage)
                        Timber.d("⚠️ Used fallback update method")
                    }
                }
                
                // CRITICAL FIX: Don't auto-scroll during streaming to prevent flicker
                // Only scroll if user is already at bottom
                if (isUserAtBottom()) {
                    scrollToBottom()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error handling background progress: ${e.message}")
            }
        }
    }
    
    private fun handleBackgroundCompletion(messageId: String, content: String) {
        runOnUiThread {
            try {
                Timber.d("🎉 Background generation completed for message: $messageId, content length: ${content.length}")
                
                // Use MessageManager to properly finalize streaming
                if (::messageManager.isInitialized) {
                    // Final streaming update
                    // Use direct streaming method to bypass the ViewHolder's message state check
                    chatAdapter.updateStreamingContentDirect(messageId, content, processMarkdown = true, isStreaming = true)
                    
                    // Stop streaming mode and finalize
                    lifecycleScope.launch {
                        delay(500) // Small delay to show final content
                        chatAdapter.stopStreamingMode()
                        messageManager.stopStreamingMode()
                        Timber.d("✅ Finalized streaming via ChatAdapter and MessageManager")
                    }
                } else {
                    // Fallback: Direct adapter update
                    val currentList = chatAdapter.currentList.toMutableList()
                    val index = currentList.indexOfFirst { it.id == messageId }
                    
                    if (index != -1) {
                        val completedMessage = currentList[index].copy(
                            message = content,
                            isGenerating = false,
                            showButtons = true,
                            lastModified = System.currentTimeMillis(),
                            completionTime = System.currentTimeMillis()
                        )
                        
                        currentList[index] = completedMessage
                        chatAdapter.submitList(currentList)
                        updateMessageInAdapter(completedMessage)
                        
                        // Save to database
                        lifecycleScope.launch {
                            conversationsViewModel.saveMessage(completedMessage)
                        }
                        Timber.d("⚠️ Used fallback completion method")
                    }
                }
                
                // Show completion notification
                Toast.makeText(this@MainActivity, "AI response completed!", Toast.LENGTH_SHORT).show()
                
                // CRITICAL FIX: Don't auto-scroll on completion to prevent flicker
                // Only scroll if user is at bottom
                if (isUserAtBottom()) {
                    scrollToBottom()
                }
                
                Timber.d("✅ Successfully handled background completion")
            } catch (e: Exception) {
                Timber.e(e, "Error handling background completion: ${e.message}")
            }
        }
    }
    
    private fun handleBackgroundError(messageId: String, error: String) {
        runOnUiThread {
            try {
                Timber.e("❌ Background generation error for message $messageId: $error")
                
                // Update the message with error state
                val currentList = chatAdapter.currentList.toMutableList()
                val index = currentList.indexOfFirst { it.id == messageId }
                
                if (index != -1) {
                    val currentContent = currentList[index].message.trim()
                    
                    // CRITICAL FIX: If message has substantial content, just mark as complete instead of adding error
                    if (currentContent.length > 50) {
                        Timber.d("✅ Message has substantial content (${currentContent.length} chars), marking as complete instead of adding error")
                        val completedMessage = currentList[index].copy(
                            isGenerating = false,
                            isLoading = false,
                            showButtons = true,
                            canContinueStreaming = false,
                            error = false,
                            lastModified = System.currentTimeMillis()
                        )
                        currentList[index] = completedMessage
                        chatAdapter.submitList(currentList)
                        updateMessageInAdapter(completedMessage)
                        
                        // Update in database
                        lifecycleScope.launch {
                            conversationsViewModel.saveMessage(completedMessage)
                        }
                        return@runOnUiThread
                    }
                    
                    // Only add error message if there's no substantial content
                    val errorMessage = currentList[index].copy(
                        message = currentList[index].message + "\n\n⚠️ Generation interrupted: $error\n\nTap 'Regenerate' to try again.",
                        isGenerating = false,
                        showButtons = true,
                        error = true,
                        lastModified = System.currentTimeMillis()
                    )
                    
                    currentList[index] = errorMessage
                    chatAdapter.submitList(currentList)
                    
                    // Update in message manager
                    updateMessageInAdapter(errorMessage)
                    
                    // Save to database
                    lifecycleScope.launch {
                        conversationsViewModel.saveMessage(errorMessage)
                    }
                    
                    // Show error notification
                    Toast.makeText(this@MainActivity, "AI generation encountered an error: $error", Toast.LENGTH_SHORT).show()
                    
                    Timber.d("⚠️ Successfully handled background error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling background error: ${e.message}")
            }
        }
    }
    
    /**
     * Check for incomplete AI responses and offer recovery options
     */
    private fun checkAndConnectToBackgroundGeneration() {
        if (currentConversationId == null) return
        
        // CRITICAL FIX: Prevent duplicate checks within 3 seconds
        val currentTime = System.currentTimeMillis()
        if (isBackgroundCheckInProgress || (currentTime - lastBackgroundCheckTime) < 3000) {
            Timber.d("⏸️ Skipping background check - already in progress or too recent (${currentTime - lastBackgroundCheckTime}ms ago)")
            return
        }
        
        isBackgroundCheckInProgress = true
        lastBackgroundCheckTime = currentTime
        Timber.d("🔍 Starting background generation check for conversation: $currentConversationId")
        
        lifecycleScope.launch {
            try {
                val messages = conversationsViewModel.getAllConversationMessages(currentConversationId.toString())
                val generatingMessages = messages.filter { !it.isUser && it.isGenerating }
                
                if (generatingMessages.isNotEmpty()) {
                    Timber.d("🔍 Found ${generatingMessages.size} messages marked as generating:")
                    generatingMessages.forEach { msg ->
                        Timber.d("  - ${msg.id}: ${msg.message.length} chars, lastModified: ${msg.lastModified}, age: ${(System.currentTimeMillis() - (msg.lastModified ?: 0)) / 1000}s")
                    }
                }
                
                // CRITICAL FIX: Don't auto-complete messages with content - they might still be streaming!
                // Check if messages with content are actually finished or still generating
                val messagesWithContent = generatingMessages.filter { message ->
                    message.message.trim().isNotEmpty()
                }
                
                val messagesWithoutContent = generatingMessages.filter { message ->
                    message.message.trim().isEmpty()
                }
                
                // Check messages with content to see if they're actually finished or still generating
                val actuallyGeneratingMessages = mutableListOf<ChatMessage>()
                
                for (messageWithContent in messagesWithContent) {
                    val messageAge = System.currentTimeMillis() - (messageWithContent.lastModified ?: 0)
                    val isRecent = messageAge < 2 * 60 * 1000 // 2 minutes
                    
                    // Check if there's active streaming for this message
                    val hasActiveSession = StreamingStateManager.canContinueStreaming(messageWithContent.id) ||
                                         backgroundAiService?.isGenerating(messageWithContent.id) == true
                    
                    if (isRecent && hasActiveSession) {
                        // Message has content but is still actively generating - continue streaming
                        Timber.d("🔄 Message has content but still generating: ${messageWithContent.id} (${messageWithContent.message.length} chars)")
                        actuallyGeneratingMessages.add(messageWithContent)
                    } else {
                        // Message has content and is old/inactive - mark as complete
                        Timber.d("🔧 Auto-completing inactive message with content: ${messageWithContent.id} (${messageWithContent.message.length} chars)")
                        markMessageAsComplete(messageWithContent)
                    }
                }
                
                // Add messages without content that are recent
                actuallyGeneratingMessages.addAll(messagesWithoutContent.filter { message ->
                    val messageAge = System.currentTimeMillis() - (message.lastModified ?: 0)
                    val isRecent = messageAge < 5 * 60 * 1000 // 5 minutes
                    
                    // Only process if recent and empty
                    isRecent
                })
                
                if (actuallyGeneratingMessages.isNotEmpty()) {
                    Timber.d("🔄 Found ${actuallyGeneratingMessages.size} messages actually being generated in background (${generatingMessages.size} total marked as generating)")
                    
                    for (message in actuallyGeneratingMessages) {
                        // CRITICAL FIX: Comprehensive check to avoid duplicate generations
                        val canContinue = StreamingStateManager.canContinueStreaming(message.id)
                        val existingSession = StreamingStateManager.getStreamingSession(message.id)
                        val isBackgroundActive = backgroundAiService?.isGenerating(message.id) == true
                        val hasBackgroundSession = StreamingStateManager.hasActiveStreamingInConversation(message.conversationId)
                        
                        Timber.d("🔍 Generation check for ${message.id}: canContinue=$canContinue, existingSession=${existingSession != null}, isBackgroundActive=$isBackgroundActive, hasBackgroundSession=$hasBackgroundSession")
                        
                        if (canContinue && existingSession != null) {
                            Timber.d("✅ Found continuable streaming session for message: ${message.id} with ${existingSession.getPartialContent().length} chars")
                            
                            // CRITICAL: Set global generating state
                            setGeneratingState(true)
                            continueStreamingInUI(message, existingSession)
                        } else if (isBackgroundActive) {
                            Timber.d("✅ Connecting to active background generation for message: ${message.id}")
                            
                            // CRITICAL: Set global generating state
                            setGeneratingState(true)
                            connectToActiveGeneration(message)
                        } else if (hasBackgroundSession) {
                            Timber.d("✅ Found background session in conversation, requesting current progress for message: ${message.id}")
                            
                            // CRITICAL: Set global generating state
                            setGeneratingState(true)
                            // CRITICAL FIX: Don't restart, just request current progress from background service
                            requestCurrentProgressFromBackground(message)
                        } else {
                            // CRITICAL FIX: Don't mark as incomplete immediately
                            // If message has no content and no active generation, just leave it as is
                            // The user can manually regenerate if needed
                            Timber.d("ℹ️ Message has no content and no active generation, leaving as-is for potential manual action: ${message.id}")
                            // Remove the automatic marking as incomplete to prevent interruption messages
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for background generation: ${e.message}")
            } finally {
                // CRITICAL FIX: Reset flag when check is complete
                isBackgroundCheckInProgress = false
                Timber.d("✅ Background generation check completed")
            }
        }
    }
    
    /**
     * Connect to active background generation seamlessly
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun connectToActiveGeneration(message: ChatMessage) {
        try {
            Timber.d("🔗 Connecting to active generation for message: ${message.id}")
            
            // Enable streaming mode in both ChatAdapter and MessageManager for live updates
            if (::messageManager.isInitialized) {
                chatAdapter.startStreamingMode()
                messageManager.startStreamingMode()
                Timber.d("🎬 Started streaming mode for background generation")
            }
            
            // Update UI to show the message is generating - CRITICAL: Set isGenerating BEFORE starting streaming
            val currentList = chatAdapter.currentList.toMutableList()
            val index = currentList.indexOfFirst { it.id == message.id }
            
            if (index != -1) {
                val updatedMessage = currentList[index].copy(
                    isGenerating = true,
                    isLoading = false,  // CRITICAL: Show content, not loading spinner
                    showButtons = false,
                    error = false
                )
                
                // CRITICAL FIX: Don't use submitList during streaming - it causes reloading effect!
                // Use direct update to prevent UI flicker and "reloading" appearance
                chatAdapter.updateMessageDirectly(index, updatedMessage)
                updateMessageInAdapter(updatedMessage)
                Timber.d("🔄 Updated message state for streaming: isGenerating=${updatedMessage.isGenerating}")
                
                // CRITICAL FIX: Don't auto-scroll to prevent flicker during streaming
                // Only scroll if user is at bottom
                if (isUserAtBottom()) {
                    binding.chatRecyclerView.smoothScrollToPosition(index)
                }
            }
            
            Timber.d("✅ Connected to active background generation - UI will update via broadcasts")
            
        } catch (e: Exception) {
            Timber.e(e, "Error connecting to active generation: ${e.message}")
        }
    }
    
    // CRITICAL FIX: Removed restartBackgroundGeneration function
    // We should NEVER restart generation - only connect to existing ones
    
    /**
     * CRITICAL FIX: Continue streaming in UI with existing content instead of restarting
     * This prevents token waste and provides seamless continuation
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun continueStreamingInUI(message: ChatMessage, session: StreamingStateManager.StreamingSession) {
        try {
            Timber.d("✅ Continuing streaming in UI for message: ${message.id} with ${session.getPartialContent().length} existing chars")
            
            // Enable streaming mode in both ChatAdapter and MessageManager for live updates
            if (::messageManager.isInitialized) {
                chatAdapter.startStreamingMode()
                messageManager.startStreamingMode()
                Timber.d("🎬 Started streaming mode for continuation")
            }
            
            // Update message with existing content from session
            val currentList = chatAdapter.currentList.toMutableList()
            val index = currentList.indexOfFirst { it.id == message.id }
            
            if (index != -1) {
                val existingContent = session.getPartialContent()
                
                val updatedMessage = currentList[index].copy(
                    message = existingContent, // Show existing progress
                    partialContent = existingContent,
                    isGenerating = true, // Still generating
                    showButtons = false,
                    error = false,
                    canContinueStreaming = true,
                    streamingSessionId = session.sessionId
                )
                
                // CRITICAL FIX: Don't use submitList during streaming continuation - it causes reloading effect!
                // Use direct updates to prevent UI flicker and "reloading" appearance
                chatAdapter.updateMessageDirectly(index, updatedMessage)
                
                // Update the specific ViewHolder with existing content directly
                chatAdapter.updateStreamingContentDirect(
                    messageId = message.id,
                    content = existingContent,
                    processMarkdown = true,
                    isStreaming = true
                )
                
                Timber.d("📝 Displayed existing content directly: ${existingContent.length} chars")
                
                updateMessageInAdapter(updatedMessage)
                
                // CRITICAL FIX: Don't auto-scroll to prevent flicker during streaming
                // Only scroll if user is at bottom
                if (isUserAtBottom()) {
                    binding.chatRecyclerView.smoothScrollToPosition(index)
                }
                
                // Start or connect to background service for any remaining generation
                if (session.isBackgroundActive) {
                    Timber.d("🔗 Background service is active, requesting current progress")
                    
                    // CRITICAL FIX: Request current progress from background service
                    if (isServiceBound && backgroundAiService != null) {
                        backgroundAiService?.requestCurrentProgress(message.id, message.conversationId)
                        Timber.d("📡 Requested current progress from background service")
                    } else {
                        // Bind to service and then request progress
                        bindToBackgroundService()
                        
                        // Request progress after binding
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(500) // Wait for binding
                            backgroundAiService?.requestCurrentProgress(message.id, message.conversationId)
                            Timber.d("📡 Requested current progress after binding")
                        }
                    }
                } else {
                    // CRITICAL FIX: Don't restart generation if session already exists
                    // Just show the existing content and wait for manual continuation if needed
                    Timber.d("✓ Session exists but not actively generating - showing existing content")
                    
                    // Check if content looks complete
                    val looksComplete = existingContent.isNotEmpty() && 
                        (existingContent.trim().endsWith(".") || 
                         existingContent.trim().endsWith("!") ||
                         existingContent.trim().endsWith("?") ||
                         existingContent.contains("[DONE]") ||
                         existingContent.length > 1000) // Long responses are likely complete
                    
                    if (looksComplete) {
                        Timber.d("✓ Content appears complete, marking as finished")
                        // Mark as completed
                        val completedMessage = updatedMessage.copy(
                            isGenerating = false,
                            showButtons = true,
                            canContinueStreaming = false
                        )
                        updateMessageInAdapter(completedMessage)
                        StreamingStateManager.completeStreamingSession(message.id, existingContent)
                    } else {
                        Timber.d("ℹ️ Content may be incomplete but not auto-restarting generation")
                        // Keep as generating but don't start new generation
                        // User can manually continue if needed
                    }
                }
            }
            
            Timber.d("✅ Successfully continued streaming in UI")
            
        } catch (e: Exception) {
            Timber.e(e, "Error continuing streaming in UI: ${e.message}")
            // CRITICAL FIX: If message has content, mark as complete instead of incomplete
            if (message.message.trim().isNotEmpty()) {
                Timber.d("✅ Message has content despite error, marking as complete instead of incomplete")
                markMessageAsComplete(message)
            } else {
                // Only mark as incomplete if there's no content
                markMessageAsIncomplete(message)
            }
        }
    }
    
    /**
     * CRITICAL FIX: Request current progress from background service without starting new generation
     */
    private fun requestCurrentProgressFromBackground(message: ChatMessage) {
        try {
            Timber.d("📡 Requesting current progress from background service for message: ${message.id}")
            
            // Bind to service if not already bound
            if (backgroundAiService == null) {
                bindToBackgroundService()
                
                // Wait briefly for binding, then request progress
                Handler(Looper.getMainLooper()).postDelayed({
                    backgroundAiService?.requestCurrentProgress(message.id, message.conversationId)
                }, 1000)
            } else {
                // Service is already bound, request immediately
                backgroundAiService?.requestCurrentProgress(message.id, message.conversationId)
            }
            
            // Enable streaming mode to receive updates
            if (::messageManager.isInitialized) {
                chatAdapter.startStreamingMode()
                messageManager.startStreamingMode()
                Timber.d("🎬 Started streaming mode for progress request")
            }
            
            // Update UI to show it's generating - CRITICAL: Use direct update to prevent UI resets
            val currentList = chatAdapter.currentList.toMutableList()
            val index = currentList.indexOfFirst { it.id == message.id }
            
            if (index != -1) {
                val updatedMessage = currentList[index].copy(
                    isGenerating = true,
                    isLoading = false, // CRITICAL: Show content, not loading spinner
                    showButtons = false
                )
                
                // CRITICAL FIX: Use direct update to prevent UI refresh
                chatAdapter.updateMessageDirectly(index, updatedMessage)
                Timber.d("✅ Updated UI for progress request with direct update")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error requesting current progress: ${e.message}")
        }
    }
    
    /**
     * CRITICAL FIX: Mark old generating messages as incomplete instead of restarting
     */
    /**
     * Mark message as completed when it has substantial content but is still marked as generating
     */
    private fun markMessageAsComplete(message: ChatMessage) {
        try {
            Timber.d("✅ Marking message as completed: ${message.id} with ${message.message.length} chars")
            
            val currentList = chatAdapter.currentList.toMutableList()
            val index = currentList.indexOfFirst { it.id == message.id }
            
            if (index != -1) {
                val completedMessage = currentList[index].copy(
                    isGenerating = false,
                    isLoading = false,
                    showButtons = true,
                    canContinueStreaming = false,
                    lastModified = System.currentTimeMillis()
                )
                
                currentList[index] = completedMessage
                chatAdapter.submitList(currentList)
                updateMessageInAdapter(completedMessage)
                
                // Update in database
                lifecycleScope.launch {
                    try {
                        conversationsViewModel.saveMessage(completedMessage)
                        Timber.d("✅ Updated message as completed in database: ${message.id}")
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating completed message in database: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error marking message as complete: ${e.message}")
        }
    }
    
    private fun markMessageAsIncomplete(message: ChatMessage) {
        try {
            Timber.w("⚠️ WARNING: markMessageAsIncomplete called for message: ${message.id} with content length: ${message.message.length}")
            Timber.d("⏸️ Marking old message as incomplete: ${message.id}")
            
            val currentList = chatAdapter.currentList.toMutableList()
            val index = currentList.indexOfFirst { it.id == message.id }
            
            if (index != -1) {
                val currentMessage = currentList[index].message.trim()
                
                // CRITICAL FIX: If message already has substantial content (>50 chars), 
                // just mark as complete instead of adding interruption message
                if (currentMessage.length > 50) {
                    Timber.d("✅ Message has substantial content (${currentMessage.length} chars), marking as complete instead")
                    markMessageAsComplete(message)
                    return
                }
                
                val incompleteMessage = currentList[index].copy(
                    isGenerating = false,
                    showButtons = true,
                    canContinueStreaming = false,
                    message = if (currentMessage.isEmpty()) {
                        "⚠️ Generation was interrupted. Tap 'Continue' to resume."
                    } else {
                        currentList[index].message + "\n\n⚠️ Generation was interrupted. Tap 'Continue' to resume."
                    },
                    lastModified = System.currentTimeMillis()
                )
                
                currentList[index] = incompleteMessage
                chatAdapter.submitList(currentList)
                updateMessageInAdapter(incompleteMessage)
                
                // Update in database
                lifecycleScope.launch {
                    conversationsViewModel.saveMessage(incompleteMessage)
                }
                
                // Clean up streaming state
                StreamingStateManager.removeStreamingSession(message.id)
                
                Timber.d("✅ Marked message as incomplete")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error marking message as incomplete: ${e.message}")
        }
    }
    
    /**
     * CRITICAL FIX: Restore full UI state for generating messages
     * Shows stop button, pulsing animation, text indicator, etc.
     */
    private fun restoreGeneratingUIState(generatingMessages: List<ChatMessage>) {
        try {
            Timber.d("🎨 Restoring UI state for ${generatingMessages.size} generating messages")
            
            // Update each generating message to ensure proper UI state
            val currentList = chatAdapter.currentList.toMutableList()
            var updated = false
            
            generatingMessages.forEach { generatingMessage ->
                val index = currentList.indexOfFirst { it.id == generatingMessage.id }
                if (index != -1) {
                    val currentMessage = currentList[index]
                    val updatedMessage = currentMessage.copy(
                        isGenerating = true,
                        isLoading = false, // Show content, not loading spinner
                        showButtons = false, // Hide regenerate/copy buttons
                        canContinueStreaming = true
                    )
                    
                    currentList[index] = updatedMessage
                    updated = true
                    Timber.d("🎨 Updated UI state for generating message: ${generatingMessage.id}")
                }
            }
            
            if (updated) {
                chatAdapter.submitList(currentList)
                Timber.d("✅ UI state restored for generating messages")
            }
            
            // CRITICAL FIX: Show stop button with pulsing animation
            runOnUiThread {
                try {
                    // Enable stop button and start pulsing using binding
                    binding.btnSubmitText.let { btn ->
                        btn.visibility = View.VISIBLE
                        btn.isEnabled = true
                        
                        // Start pulsing animation
                        try {
                            val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                            btn.startAnimation(pulseAnimation)
                            Timber.d("🟡 Started stop button pulsing animation")
                        } catch (e: Exception) {
                            Timber.w("Could not load pulse animation, using default: ${e.message}")
                        }
                        
                        // Update button to show stop state
                        btn.setImageResource(android.R.drawable.ic_media_pause)
                        Timber.d("🛑 Updated button to stop state")
                    }
                    
                    // Show generating text indicator
                    showGeneratingIndicator()
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error updating stop button UI: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error restoring generating UI state: ${e.message}")
        }
    }
    
    /**
     * Show text indicator for generating state
     */
    private fun showGeneratingIndicator() {
        try {
            // This would show "AI is thinking..." or similar text
            // Implementation depends on your UI structure
            Timber.d("📝 Should show generating text indicator here")
            
            // You might want to show a status text like:
            // statusTextView?.text = "AI is generating response..."
            // statusTextView?.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Timber.e(e, "Error showing generating indicator: ${e.message}")
        }
    }
    
    /**
     * CRITICAL FIX: Merge messages while preserving active streaming content
     * This prevents overwriting streaming content with database content
     */
    private fun mergeMessagesPreservingStreaming(messages: List<ChatMessage>, conversationId: String) {
        try {
            Timber.d("🔄 Merging ${messages.size} messages while preserving streaming content")
            
            val currentList = chatAdapter.currentList.toMutableList()
            val currentMessageIds = currentList.map { it.id }.toSet()
            
            // CRITICAL FIX: Instead of rebuilding the list, only add missing messages
            // Find messages from database that aren't in current adapter
            val newMessages = messages.filter { it.id !in currentMessageIds }
            
            // Add new messages that aren't already in adapter
            newMessages.forEach { newMessage ->
                if (!newMessage.isGenerating) {
                    // Safe to add non-generating messages
                    currentList.add(newMessage)
                    Timber.d("➕ Added new message: ${newMessage.id}")
                } else {
                    // For generating messages from DB, only add if we don't have them at all
                    Timber.d("⚠️ Found generating message in DB that's not in current list: ${newMessage.id}")
                    // Don't add - let the background service handle this through broadcasts
                }
            }
            
            // CRITICAL FIX: For generating messages, compare content and preserve the actively streaming version
            currentList.toList().forEach { currentMessage ->
                if (currentMessage.isGenerating) {
                    val dbMessage = messages.find { it.id == currentMessage.id }
                    if (dbMessage != null) {
                        // CRITICAL: Check if there's an active streaming session
                        val hasActiveSession = StreamingStateManager.canContinueStreaming(currentMessage.id)
                        
                        if (hasActiveSession) {
                            // Use streaming state manager content as the source of truth
                            val streamingSession = StreamingStateManager.getStreamingSession(currentMessage.id)
                            val streamingContent = streamingSession?.getPartialContent() ?: ""
                            
                            if (streamingContent.length > currentMessage.message.length) {
                                // Update current message with streaming content (most recent)
                                val updatedMessage = currentMessage.copy(
                                    message = streamingContent,
                                    partialContent = streamingContent,
                                    lastModified = System.currentTimeMillis()
                                )
                                val index = currentList.indexOf(currentMessage)
                                if (index != -1) {
                                    currentList[index] = updatedMessage
                                    Timber.d("📊 Updated with streaming content for ${currentMessage.id}: streaming=${streamingContent.length} chars")
                                }
                            }
                        } else if (dbMessage.message.length > currentMessage.message.length) {
                            val index = currentList.indexOfFirst { it.id == currentMessage.id }
                            if (index != -1) {
                                // DB has more content - update but preserve generating state
                                val updatedMessage = dbMessage.copy(
                                    isGenerating = true, // Keep generating state
                                    isLoading = false,
                                    showButtons = false
                                )
                                currentList[index] = updatedMessage
                                Timber.d("📈 Updated generating message ${currentMessage.id} with longer DB content: ${dbMessage.message.length} chars")
                            }
                        } else {
                            Timber.d("📊 Kept current streaming content for ${currentMessage.id}: current=${currentMessage.message.length} > db=${dbMessage.message.length}")
                        }
                    }
                }
            }
            
            // Sort the list by timestamp while preserving current content
            currentList.sortBy { it.timestamp }
            
            // CRITICAL FIX: Only submit list if it's actually different to avoid unnecessary updates
            val currentContentHashes = chatAdapter.currentList.map { "${it.id}:${it.message.length}:${it.isGenerating}" }
            val newContentHashes = currentList.map { "${it.id}:${it.message.length}:${it.isGenerating}" }
            
            if (currentContentHashes != newContentHashes) {
                chatAdapter.submitList(currentList)
                Timber.d("✅ Updated adapter with preserved streaming content")
            } else {
                Timber.d("ℹ️ No changes needed - content is identical")
            }
            
            // Update MessageManager internal list to match
            if (::messageManager.isInitialized) {
                // Update MessageManager's internal list to match what we have in adapter
                Timber.d("📝 Synchronizing MessageManager internal list")
                // TODO: Add method to update MessageManager internal list without triggering adapter updates
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error merging messages while preserving streaming: ${e.message}")
            // Fallback to normal initialization only if current list is empty
            if (chatAdapter.currentList.isEmpty()) {
                messageManager.initialize(messages, conversationId)
                Timber.d("⚠️ Used fallback initialization due to error")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}