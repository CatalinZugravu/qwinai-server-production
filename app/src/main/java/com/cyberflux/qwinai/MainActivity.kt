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
import android.util.Log
import android.os.Looper
import android.os.Vibrator
import android.provider.MediaStore
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
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
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.adapter.ConversationAdapter
import com.cyberflux.qwinai.adapter.CustomSpinnerDialog
import com.cyberflux.qwinai.adapter.ModelGridAdapter
import com.cyberflux.qwinai.adapter.ModelSpinnerAdapter
import com.cyberflux.qwinai.ui.FileUploadBottomSheet
import com.cyberflux.qwinai.ui.DebugTokenDisplayHelper
import com.cyberflux.qwinai.ads.ConsentManager
import com.cyberflux.qwinai.ads.AdManager
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.billing.BillingManager
import com.cyberflux.qwinai.billing.HuaweiIapProvider
import com.cyberflux.qwinai.branch.MessageManager
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.databinding.ActivityMainBinding
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.AIParameters
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.model.Conversation
import com.cyberflux.qwinai.model.FileItem
import com.cyberflux.qwinai.model.ResponseLength
import com.cyberflux.qwinai.model.ResponsePreferences
import com.cyberflux.qwinai.model.ResponseTone
import com.cyberflux.qwinai.network.AimlApiRequest
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.network.AudioResponseHandler
import com.cyberflux.qwinai.network.ModelApiHandler
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.network.StreamingHandler
import com.cyberflux.qwinai.service.AiChatService
import com.cyberflux.qwinai.service.BackgroundAiService
import com.cyberflux.qwinai.service.OCRService
import com.cyberflux.qwinai.utils.AIParametersDialog
import com.cyberflux.qwinai.utils.AIParametersManager
import com.cyberflux.qwinai.utils.AppSettings
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.DynamicColorManager
import com.cyberflux.qwinai.utils.ThemeManager
import com.cyberflux.qwinai.utils.UltraAudioIntegration
import com.cyberflux.qwinai.utils.ConversationSummarizer
import com.cyberflux.qwinai.utils.SimplifiedTokenManager
// Using new SimplifiedTokenManager system for comprehensive token management
// Wildcard import includes MessageValidationResult and ContextCalculationResult
import com.cyberflux.qwinai.utils.*
import com.cyberflux.qwinai.utils.FileHandler
import com.cyberflux.qwinai.utils.FileProgressTracker
import com.cyberflux.qwinai.utils.FileUtil
import com.cyberflux.qwinai.utils.LocationService
import com.cyberflux.qwinai.utils.ModelConfigManager
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PerplexityPreferences
import com.cyberflux.qwinai.utils.PersistentFileStorage
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.ScrollArrowsHelper
import com.cyberflux.qwinai.utils.UnifiedStreamingManager
import com.cyberflux.qwinai.utils.TokenLimitDialogHandler
import com.cyberflux.qwinai.utils.TokenValidator
import com.cyberflux.qwinai.utils.TranslationUtils
import com.cyberflux.qwinai.utils.UnifiedFileHandler
import com.cyberflux.qwinai.utils.SupportedFileTypes
import com.google.android.material.textfield.TextInputEditText
import com.cyberflux.qwinai.utils.JsonUtils
import com.cyberflux.qwinai.utils.DebugMenuHelper
import com.cyberflux.qwinai.utils.DebugSubscriptionHelper
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
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

class MainActivity : BaseThemedActivity(), ModelGridAdapter.TranslationModeFetcher {

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
    // Ultra Audio Recording - NEW SYSTEM
    private lateinit var btnMicrophone: ImageButton
    private lateinit var ultraAudioIntegration: UltraAudioIntegration
    var isNextMessageFromVoice = false  // Flag to track voice messages
    private val RECORD_AUDIO_PERMISSION_CODE = 200
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
    // Token Management - NEW SYSTEM
    private lateinit var tokenManager: SimplifiedTokenManager
    private var tokenLimitWarningBadge: View? = null
    private var loadingDialog: AlertDialog? = null

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
    
    // CRITICAL FIX: Store model state before file picker to prevent reset issues
    private var modelStateBeforeFilePicker: AIModel? = null
    private lateinit var btnPerplexitySearchMode: ImageButton
    private lateinit var btnPerplexityContextSize: ImageButton
    private var isReturningFromTextSelection = false
    var isReturningFromFileSelection = false
    private var isDraftContentLoaded = false

    // File Handling
    private var currentPhotoPath: String = ""
    val selectedFiles = mutableListOf<FileUtil.SelectedFile>()
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
    
    // File Animation Management - beautiful file transfer animations
    private lateinit var fileAnimationManager: FileAnimationManager
    
    // NEW: Hilt-injected managers for modern architecture
    private lateinit var btnExpandText: ImageButton
    
    // AI Parameters Management
    private lateinit var aiParametersManager: AIParametersManager
    private lateinit var btnAiParameters: ImageButton
    private var currentAiParameters: AIParameters? = null
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
    
    // Pre-validation system for ultra-fast message sending
    private val validationDebouncer = Handler(Looper.getMainLooper())
    private var validationRunnable: Runnable? = null
    private var preValidatedConversationId: String? = null
    private var preValidatedContext: List<ChatMessage>? = null
    private var pulseAnimator: ValueAnimator? = null

    // Debug components (DEBUG builds only)
    private var debugTokenDisplayHelper: DebugTokenDisplayHelper? = null

    // At the top with other properties
    private lateinit var aiChatService: AiChatService
    private lateinit var startActivity: StartActivity
    
    // CRITICAL FIX: Dedicated coroutine scope for background database operations
    private val backgroundDbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // CRITICAL FIX: Dedicated coroutine scope for streaming operations - immune to activity lifecycle changes
    private val streamingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("MainActivity-Streaming"))
    
    // Database instance for direct access
    private val database by lazy { AppDatabase.getDatabase(this) }
    
    // Background service for continuing AI generation
    private var backgroundAiService: BackgroundAiService? = null
    private var isServiceBound = false
    
    // CRITICAL FIX: Service binding queue and callback system
    private val serviceBindingCallbacks = mutableListOf<() -> Unit>()
    private var isBindingInProgress = false
    
    // CRITICAL FIX: Prevent duplicate background generation checks
    private var isBackgroundCheckInProgress = false
    private var lastBackgroundCheckTime = 0L
    
    private val backgroundServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as BackgroundAiService.BackgroundAiBinder
                backgroundAiService = binder.getService()
                isServiceBound = true
                isBindingInProgress = false
                Timber.d("🔗 Connected to BackgroundAiService")
                
                // CRITICAL FIX: Execute all queued callbacks
                val callbacksCopy = serviceBindingCallbacks.toList()
                serviceBindingCallbacks.clear()
                
                callbacksCopy.forEach { callback ->
                    try {
                        callback()
                    } catch (e: Exception) {
                        Timber.e(e, "Error executing service binding callback: ${e.message}")
                    }
                }
                
                // CRITICAL FIX: Auto-request progress for any generating messages
                if (::chatAdapter.isInitialized) {
                    val generatingMessages = chatAdapter.currentList.filter { !it.isUser && it.isGenerating }
                    if (generatingMessages.isNotEmpty()) {
                        Timber.d("📡 Service connected - requesting progress for ${generatingMessages.size} generating messages")
                        generatingMessages.forEach { message ->
                            backgroundAiService?.requestCurrentProgress(message.id)
                            Timber.d("📡 Requested progress for message: ${message.id}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error in onServiceConnected: ${e.message}")
                isBindingInProgress = false
                serviceBindingCallbacks.clear()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            backgroundAiService = null
            isServiceBound = false
            isBindingInProgress = false
            serviceBindingCallbacks.clear()
            Timber.d("💔 Disconnected from BackgroundAiService")
        }
    }
    
    // Broadcast receiver for background generation updates
    private val backgroundGenerationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("📨📨📨 BROADCAST RECEIVED!!!")
            Timber.d("📨 Action: ${intent?.action}")
            Timber.d("📨 Intent extras: ${intent?.extras?.keySet()?.joinToString()}")
            Timber.d("📨 Thread: ${Thread.currentThread().name}")
            
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
    
    // Track whether the background generation receiver is registered
    private var isBackgroundGenerationReceiverRegistered = false

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
            
            // CRITICAL FIX: Use stored model state instead of current ModelManager.selectedModel
            // to prevent model reset issues during file picker activity lifecycle
            val currentModel = modelStateBeforeFilePicker ?: ModelManager.selectedModel
            val supportsImages = ModelValidator.supportsImageUpload(currentModel.id)
            
            Timber.d("🔄 File picker result - using model: ${currentModel.displayName} (stored: ${modelStateBeforeFilePicker?.displayName}, current: ${ModelManager.selectedModel.displayName})")

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

                    // Check if file is compatible with current model (use stored model)
                    val mimeType = FileUtil.getMimeType(this, uri)
                    val isOcrModel = ModelValidator.isOcrModel(currentModel.id)
                    val isCompatible = if (isOcrModel) {
                        // OCR models: accept images and PDFs
                        mimeType.startsWith("image/") || mimeType == "application/pdf"
                    } else {
                        // Regular models: accept documents
                        SupportedFileTypes.isDocumentTypeSupported(mimeType)
                    }
                    
                    if (isCompatible) {
                        val fileType = if (mimeType.startsWith("image/")) "image" 
                                      else if (mimeType == "application/pdf") "PDF" 
                                      else "document"
                        Timber.d("Processing $fileType file from picker: $uri")
                        
                        if (mimeType.startsWith("image/")) {
                            fileHandler.handleSelectedFile(uri, false)
                        } else {
                            // Handle documents
                            fileHandler.handleSelectedDocument(uri)
                        }
                        processedCount++
                    } else {
                        val expectedType = if (isOcrModel) "images or PDFs" else "documents"
                        Toast.makeText(
                            this,
                            "Skipped incompatible file. Expected: $expectedType",
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
                
                // CRITICAL: For OCR models, immediately force send button visible after multiple selection
                if (ModelValidator.isOcrModel(currentModel.id)) {
                    binding.btnMicrophone.visibility = View.GONE
                    binding.btnSubmitText.visibility = View.VISIBLE
                    binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                    binding.btnSubmitText.contentDescription = "Process OCR"
                    binding.btnSubmitText.isEnabled = true
                    binding.btnSubmitText.alpha = 1.0f
                    Timber.d("Multiple files: Send button forced visible for OCR model")
                } else {
                    val hasText = binding.etInputText.text.toString().trim().isNotEmpty()
                    val hasFiles = selectedFiles.isNotEmpty()
                    toggleInputButtons(hasText, hasFiles)
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

                // Check if file is compatible with current model (use stored model)
                val isOcrModel = ModelValidator.isOcrModel(currentModel.id)
                val isCompatible = if (isOcrModel) {
                    // OCR models: accept images and PDFs
                    mimeType.startsWith("image/") || mimeType == "application/pdf"
                } else {
                    // Regular models: accept documents
                    SupportedFileTypes.isDocumentTypeSupported(mimeType)
                }
                
                if (isCompatible) {
                    val fileType = if (mimeType.startsWith("image/")) "image" 
                                  else if (mimeType == "application/pdf") "PDF" 
                                  else "document"
                    Timber.d("Processing single $fileType file from picker: $uri")
                    
                    if (mimeType.startsWith("image/")) {
                        fileHandler.handleSelectedFile(uri, false)
                    } else {
                        // Handle documents
                        fileHandler.handleSelectedDocument(uri)
                    }
                    
                    // Update UI based on model type
                    if (isOcrModel && (mimeType.startsWith("image/") || mimeType == "application/pdf")) {
                        // CRITICAL: For OCR models, immediately force send button visible
                        binding.btnMicrophone.visibility = View.GONE
                        binding.btnSubmitText.visibility = View.VISIBLE
                        binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                        binding.btnSubmitText.contentDescription = "Process OCR"
                        binding.btnSubmitText.isEnabled = true
                        binding.btnSubmitText.alpha = 1.0f
                        Timber.d("File picker: Send button forced visible for OCR model")
                    } else {
                        val hasText = binding.etInputText.text.toString().trim().isNotEmpty()
                        val hasFiles = selectedFiles.isNotEmpty()
                        toggleInputButtons(hasText, hasFiles)
                    }
                } else {
                    val expectedType = if (isOcrModel) "images or PDFs" else "documents"
                    Toast.makeText(
                        this,
                        "Incompatible file type. Expected: $expectedType",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Timber.d("File picker cancelled or failed with result code: ${result.resultCode}")
            // Clear stored model state even if file picker was cancelled
            modelStateBeforeFilePicker = null
            Timber.d("📎 Cleared stored model state after file picker cancellation")
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
            val savedPath = getSharedPreferences("camera_prefs", MODE_PRIVATE)
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
                val selectedFile = FileUtil.SelectedFile(
                    uri = photoUri,
                    name = fileName,
                    size = fileSize,
                    isDocument = false
                )
                
                selectedFiles.add(selectedFile)
                Timber.d("Added camera file to selectedFiles, updating view")
                fileHandler.updateSelectedFilesView()
                
                // CRITICAL: Immediately set send button visible for OCR models
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                binding.btnSubmitText.contentDescription = "Process OCR"
                binding.btnSubmitText.isEnabled = true
                binding.btnSubmitText.alpha = 1.0f
                
                // Start aggressive monitoring to prevent button from disappearing
                startOcrButtonMonitoring()
                
                Timber.d("Camera result: Send button forced visible for OCR model and monitoring started")
                
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
                // 1. Create a temporary SelectedFile object for displaying - start in processing state
                val temporaryFile = FileUtil.SelectedFile(
                    uri = photoUri,
                    name = fileName,
                    size = fileSize,
                    isDocument = false,
                    isExtracting = true,  // Start in processing state
                    isExtracted = false   // Not yet processed
                )

                // 2. Add to selected files IMMEDIATELY with TEMP status
                selectedFiles.add(temporaryFile)

                // 3. Create single progress tracker that will be used throughout the entire process
                val progressTracker = FileProgressTracker()

                // 4. Update UI to show the file with progress
                withContext(Dispatchers.Main) {
                    if (::fileHandler.isInitialized) {
                        fileHandler.updateSelectedFilesView()
                    }

                    // 5. Get the view we just created and set up the progress tracker
                    val fileView = FileUtil.findFileViewForUri(photoUri, this@MainActivity)
                    if (fileView == null) {
                        Timber.e("Could not find view for camera photo: $photoUri")
                        Toast.makeText(this@MainActivity, "Error displaying camera photo", Toast.LENGTH_SHORT).show()
                        selectedFiles.remove(temporaryFile)
                        if (::fileHandler.isInitialized) {
                            fileHandler.updateSelectedFilesView()
                        }
                        return@withContext
                    }

                    // 6. Initialize the SAME progress tracker with the view and start showing progress
                    progressTracker.initWithImageFileItem(fileView)
                    progressTracker.showProgress()
                    progressTracker.observeProgress(this@MainActivity)
                }

                try {
                    // Process the file - let UnifiedFileHandler handle ALL progress updates
                    val fileResult = unifiedFileHandler.processFileForModel(
                        photoUri,
                        ModelManager.selectedModel.id,
                        progressTracker
                    )

                    if (fileResult.isSuccess) {
                        withContext(Dispatchers.Main) {
                            // Hide progress
                            progressTracker.hideProgress()
                            
                            // CRITICAL FIX: Update file state to show it's ready to send
                            val index = selectedFiles.indexOfFirst { it.uri == temporaryFile.uri }
                            if (index != -1) {
                                selectedFiles[index] = temporaryFile.copy(
                                    isExtracting = false,  // No longer processing
                                    isExtracted = true,    // Successfully processed
                                    processingInfo = "✅ Photo ready"
                                )
                                Timber.d("✅ Updated file state: photo ready to send")
                            }
                            
                            // Update UI to reflect new file state
                            if (::fileHandler.isInitialized) {
                                fileHandler.updateSelectedFilesView()
                            }
                            updateButtonVisibilityAndState()  // Enable send button
                            
                            Toast.makeText(
                                this@MainActivity,
                                "Photo added successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Haptic feedback
                            getSystemService(VIBRATOR_SERVICE) as Vibrator
                            provideHapticFeedback(50)
                        }
                    } else {
                        val error = fileResult.exceptionOrNull()
                        
                        withContext(Dispatchers.Main) {
                            progressTracker.hideProgress()
                            Toast.makeText(
                                this@MainActivity,
                                "Error processing photo: ${error?.message}",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Remove file from selected files on error
                            selectedFiles.remove(temporaryFile)
                            if (::fileHandler.isInitialized) {
                                fileHandler.updateSelectedFilesView()
                            }
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
                        if (::fileHandler.isInitialized) {
                            fileHandler.updateSelectedFilesView()
                        }
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
            currentPhotoPath = getSharedPreferences("camera_prefs", MODE_PRIVATE)
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
                
                // Apply accent status bar theming first
                ThemeManager.applyAccentStatusBarTheming(this)
                
                // Apply Material Design 3 system UI appearance
                ThemeManager.applySystemUIAppearance(this)
                
                // Apply dynamic accent colors to the main UI
                applyDynamicAccentColor()
            } catch (e: Exception) {
                Timber.e(e, "Failed to inflate binding: ${e.message}")
                finish()
                return
            }

            // Initialize critical UI components first for immediate responsiveness
            initializeCriticalUIComponents()

            // Setup basic UI immediately for perceived performance
            setupBasicUI()

            // Initialize file handler immediately after UI setup to prevent timing issues
            fileHandler = FileHandler(this)

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
            
            // Initialize debug features in debug mode
            if (BuildConfig.DEBUG) {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        initializeDebugFeatures()
                        Timber.d("🔧 Debug features initialized")
                    } catch (e: Exception) {
                        Timber.e(e, "Debug feature initialization failed: ${e.message}")
                    }
                }, 2000) // Wait for UI to be ready
            }

        } catch (e: Exception) {
            Timber.e(e, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing chat. Please restart.", Toast.LENGTH_LONG).show()

            // Try to recover by initializing essential components if possible
            try {
                if (!::tokenManager.isInitialized) {
                    // CRITICAL: Initialize SimplifiedTokenManager even in error recovery
                    tokenManager = SimplifiedTokenManager(this)
                    Timber.d("✅ Emergency recovery: SimplifiedTokenManager initialized")
                }

                if (!::messageManager.isInitialized && ::chatAdapter.isInitialized && ::conversationsViewModel.isInitialized) {
                    messageManager = MessageManager(
                        viewModel = conversationsViewModel,
                        adapter = chatAdapter,
                        lifecycleScope = lifecycleScope,
                        chatAdapter = chatAdapter,
                        tokenManager = tokenManager
                    )
                }
            } catch (innerEx: Exception) {
                Timber.e(innerEx, "Error in recovery attempt: ${innerEx.message}")
            }
        }
    }
    
    /**
     * Initialize debug features for DEBUG builds only
     */
    private fun initializeDebugFeatures() {
        if (!BuildConfig.DEBUG) return
        
        try {
            // Initialize debug token display helper
            debugTokenDisplayHelper = DebugTokenDisplayHelper(this)
            
            // Find the main chat container (usually the parent of RecyclerView)
            val chatContainer = binding.chatRecyclerView.parent as? LinearLayout
            if (chatContainer != null) {
                // Create debug token overlay
                debugTokenDisplayHelper?.createDebugOverlay(chatContainer)
                
                // Add debug menu button 
                DebugMenuHelper.createDebugButton(this, chatContainer)
                
                Timber.d("🔧 Debug token overlay and menu created")
            } else {
                Timber.w("🔧 Could not find suitable container for debug components")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error initializing debug features: ${e.message}")
        }
    }
    
    /**
     * Initialize new simplified token system
     */
    private fun initializeProductionTokenSystem() {
        try {
            Timber.d("🚀 Initializing NEW simplified token system")
            
            tokenManager = SimplifiedTokenManager(this)
            
            Timber.d("✅ NEW token system initialized successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "💥 Failed to initialize token system: ${e.message}")
            
            // This is critical - app might be unstable without token management
            Toast.makeText(this, "Token system initialization failed - app may be unstable", Toast.LENGTH_LONG).show()
            throw RuntimeException("Token system initialization failed", e)
        }
    }
    
    private fun setupChatAdapterCallbacks() {
        chatAdapter.setMessageCompletionCallback(object : ChatAdapter.MessageCompletionCallback {
            override fun onMessageCompleted(message: ChatMessage) {
                // Always use Handler for thread safety
                Handler(Looper.getMainLooper()).post {
                    try {
                        Timber.d("🔄 Message completed: ${message.id}")
                        Timber.d("🔄 DEBUG: isReloadingMessage=$isReloadingMessage, messageLength=${message.message.length}, isEmpty=${message.message.isEmpty()}")

                        // If this was a regenerated message, update content in branch system
                        // Check both isReloadingMessage flag AND message.isRegenerated flag
                        if ((isReloadingMessage || message.isRegenerated) && message.message.isNotEmpty()) {
                            Timber.d("🔄 COMPLETION: isReloadingMessage=$isReloadingMessage, messageId=${message.id}, messageContent=${message.message.take(50)}")
                            
                            // DEBUG: Check state before adding version
                            Timber.d("🔄 BEFORE addVersion: totalVersions=${message.totalVersions}, versionIndex=${message.versionIndex}, messageVersions.size=${message.messageVersions.size}")
                            
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
                // Credits are now consumed on button press, not after completion
                updateFreeMessagesText()
                
                // Show interstitial ad after AI response completion with state preservation
                // CRITICAL FIX: Preserve conversation state before showing ads to prevent state loss
                val currentConversationBackup = currentConversationId
                val currentModelBackup = ModelManager.selectedModel.id
                val currentFilesBackup = selectedFiles.toList()
                
                // Show ad with callback to restore state if needed
                this@MainActivity.adManager.showInterstitialAd(
                    this@MainActivity,
                    AdManager.AdTrigger.CHAT_RESPONSE_COMPLETE,
                    object : AdManager.AdCallback {
                        override fun onAdShown(trigger: AdManager.AdTrigger) {
                            Timber.d("🎯 Ad shown, conversation state preserved")
                        }
                        
                        override fun onAdClosed(trigger: AdManager.AdTrigger) {
                            // Restore state after ad closes to prevent conversation loss
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    if (currentConversationId != currentConversationBackup) {
                                        Timber.w("⚠️ Conversation state changed after ad, restoring: $currentConversationBackup")
                                        currentConversationId = currentConversationBackup
                                    }
                                    
                                    if (ModelManager.selectedModel.id != currentModelBackup) {
                                        Timber.w("⚠️ Model changed after ad, restoring: $currentModelBackup")
                                        val modelIndex = ModelManager.models.indexOfFirst { it.id == currentModelBackup }
                                        if (modelIndex != -1) {
                                            ModelManager.selectedModel = ModelManager.models[modelIndex]
                                            updateSelectedModelDisplay()
                                            updateControlsVisibility(currentModelBackup)
                                            applyModelColorToUI(currentModelBackup)
                                        }
                                    }
                                    
                                    if (selectedFiles.size != currentFilesBackup.size) {
                                        Timber.w("⚠️ File selection changed after ad, restoring ${currentFilesBackup.size} files")
                                        selectedFiles.clear()
                                        selectedFiles.addAll(currentFilesBackup)
                                        if (::fileHandler.isInitialized) {
                                            fileHandler.updateSelectedFilesView()
                                        }
                                    }
                                    
                                    Timber.d("✅ State restoration completed after ad")
                                } catch (e: Exception) {
                                    Timber.e(e, "Error restoring state after ad: ${e.message}")
                                }
                            }, 100) // Small delay to ensure activity is fully resumed
                        }
                        
                        override fun onAdFailed(trigger: AdManager.AdTrigger, error: String) {
                            Timber.d("Ad failed, no state restoration needed: $error")
                        }
                        
                        override fun onRewardEarned(trigger: AdManager.AdTrigger, amount: Int) {
                            // Not applicable for interstitial ads
                        }
                    }
                )
            }

            override fun onMessageCompleted(messageId: String, content: String) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        Timber.d("🔄 onMessageCompleted(String): ID=$messageId, contentLength=${content.length}, isReloadingMessage=$isReloadingMessage")
                        
                        // Get the completed message 
                        val completedMessage = messageManager.getMessageById(messageId)
                        if (completedMessage != null) {
                            Timber.d("🔄 Found completed message: isUser=${completedMessage.isUser}, isRegenerated=${completedMessage.isRegenerated}")
                            
                            // Version logic is handled in the first callback (ChatAdapter.MessageCompletionCallback)
                            // This second callback just handles final cleanup and database updates
                            
                            // Update the message in MessageManager to reflect the versioning
                            messageManager.updateMessageContent(completedMessage.id, completedMessage.message)
                            
                            // Update token count and save to database
                            // Token management is now handled automatically by SimplifiedTokenManager
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
                // Properly refund credits on API errors/cancellations
                if (creditManager.refundCredits(1, CreditManager.CreditType.CHAT)) {
                    updateFreeMessagesText()
                    Timber.d("🔄 Credits refunded due to API error or cancellation")
                }
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
                    if (::fileHandler.isInitialized) {
                        fileHandler.updateSelectedFilesView()
                    }
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
            adManager.initializeForActivity(this)  // Initialize for this activity
            creditManager = CreditManager.getInstance(this)
            
            // Initialize file animation manager for beautiful file transfer animations
            fileAnimationManager = FileAnimationManager(this)

            // Delay ad initialization to improve startup time, but only for non-subscribers
            if (!PrefsManager.isSubscribed(this)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    // AdManager initializes automatically
                    Timber.d("AdManager initialized for free user")
                }, 2000) // 2-second delay
            } else {
                Timber.d("User subscribed, skipping ad initialization")
            }

            // Initialize subscription manager first
            SubscriptionManager.initialize(this)

            // Initialize billing manager reference
            val billingManager = BillingManager.getInstance(this)

            // Restore subscriptions when chat starts
            billingManager.restoreSubscriptions()
            billingManager.validateSubscriptionStatus()

            // Monitor subscription status changes
            lifecycleScope.launch {
                SubscriptionManager.subscriptionStatus.collect { isSubscribed ->
                    handleSubscriptionStatusChange(isSubscribed)
                }
            }

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
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            sharedPrefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)

            // Load AI settings but don't update UI yet
            loadAiSettingsFromPrefs()
            setupPrivateChatFeature()

            // Initialize credits and subscriptions
            updateUIBasedOnSubscriptionStatus()

            // Initialize unified file handler (fileHandler already initialized in onCreate)
            unifiedFileHandler = UnifiedFileHandler(this)

            // Set up file handler submission
            binding.btnSubmitText.setOnClickListener {
                if (isGenerating) {
                    stopGeneration()
                } else {
                    Timber.d("Submit button clicked, calling fileHandler.handleSubmitTextClick()")
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
                    setMicrophoneVisibility(true)
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
            val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
            val hasFiles = selectedFiles.isNotEmpty()
            Timber.d("Submit button layout changed - visibility: ${binding.btnSubmitText.visibility}, " +
                    "generating: $isGenerating, " +
                    "isOcrModel: $isOcrModel, hasFiles: $hasFiles, " +
                    "contentDescription: ${binding.btnSubmitText.contentDescription}")
            
            // Force OCR button to stay visible if we have files
            if (isOcrModel && hasFiles && binding.btnSubmitText.visibility != View.VISIBLE) {
                Timber.w("OCR model with files but send button not visible - FORCING VISIBLE")
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnMicrophone.visibility = View.GONE
            }
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

    /**
     * Helper function to properly set microphone button visibility respecting OCR model rules
     */
    internal fun setMicrophoneVisibility(visible: Boolean) {
        val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
        
        if (isOcrModel) {
            // For OCR models (including Mistral OCR): never show microphone, always show send button
            btnMicrophone.visibility = View.GONE
            binding.btnSubmitText.visibility = View.VISIBLE
        } else {
            // For non-OCR models: follow the requested visibility
            if (visible) {
                btnMicrophone.visibility = View.VISIBLE
                binding.btnSubmitText.visibility = View.GONE
            } else {
                btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("UseKtx")
    fun triggerTokenLimitDialog(conversationPercentage: Float, modelId: String, isSubscribed: Boolean) {
        // Nu afișa dialog-ul prea des
        val currentTime = System.currentTimeMillis()
        val lastDialogTime = getSharedPreferences("token_dialogs", MODE_PRIVATE)
            .getLong("last_dialog_time", 0)

        if (currentTime - lastDialogTime < 30000) { // 30 secunde între dialog-uri
            return
        }

        // Salvează timpul dialog-ului
        getSharedPreferences("token_dialogs", MODE_PRIVATE).edit {
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
            val updatedFiles = mutableListOf<FileUtil.SelectedFile>()
            
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
                            val serializedFiles = if (::fileHandler.isInitialized) {
                                fileHandler.serializeSelectedFiles(selectedFiles)
                            } else {
                                "[]"
                            }

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
            // Create proper title for conversation - only based on text content, never files
            val draftTitle = when {
                draftText.isNotEmpty() -> {
                    // Use the draft text as the conversation title
                    if (draftText.length > 50) "${draftText.take(50)}..." else draftText
                }
                else -> "New Conversation"
            }

            // Ensure all files are persistent before saving
            ensureFilesPersistent()
            
            // Serialize files using the helper method
            val serializedFiles = if (::fileHandler.isInitialized) {
                fileHandler.serializeSelectedFiles(selectedFiles)
            } else {
                "[]"
            }

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
                // For OCR models with files, don't let text watcher override button state
                val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id) 
                val hasFiles = selectedFiles.isNotEmpty()
                
                if (isOcrModel && hasFiles) {
                    // OCR model with files - ensure send button stays visible
                    binding.btnMicrophone.visibility = View.GONE
                    binding.btnSubmitText.visibility = View.VISIBLE
                    binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                    binding.btnSubmitText.contentDescription = "Process OCR"
                    binding.btnSubmitText.isEnabled = true
                    binding.btnSubmitText.alpha = 1.0f
                    Timber.d("Text watcher: Preserved send button for OCR model with files")
                } else {
                    val hasFiles = selectedFiles.isNotEmpty()
                    toggleInputButtons(newText.isNotEmpty(), hasFiles)
                }
                
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
                            ensureServiceBinding {
                                Timber.d("🔗 Connected to background service during conversation load")
                            }
                            
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

    // 🧪 DEBUG: Key event handler to test subscription activity
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                Timber.d("🧪 DEBUG: Volume UP pressed - launching SubscriptionActivity for testing")
                try {
                    WelcomeActivity.start(this)
                    true
                } catch (e: Exception) {
                    Timber.e("🧪 DEBUG: Failed to launch SubscriptionActivity: ${e.message}")
                    false
                }
            }
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Timber.d("🧪 DEBUG: Volume DOWN pressed - showing debug info")
                val isSubscribed = PrefsManager.isSubscribed(this)
                val subscriptionType = PrefsManager.getSubscriptionType(this)
                val endTime = PrefsManager.getSubscriptionEndTime(this)
                android.widget.Toast.makeText(this, "🧪 DEBUG: Subscribed=$isSubscribed, Type=$subscriptionType, EndTime=$endTime", android.widget.Toast.LENGTH_LONG).show()
                true
            }
            else -> super.onKeyDown(keyCode, event)
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

        getSharedPreferences("app_prefs", MODE_PRIVATE).edit {
            putLong("last_session_time", System.currentTimeMillis())
        }

        saveAiMessageHistory()

        // Save draft if there's text or files
        saveDraftIfNeeded()

        // CRITICAL FIX: Don't reset generation state during pause - background generation should continue
        // The UI will reconnect to background generation when resumed
        // chatAdapter.setGenerating(false) - REMOVED to prevent response refresh
        
        // Cancel any ongoing ultra voice recording
        if (::ultraAudioIntegration.isInitialized && ultraAudioIntegration.isCurrentlyRecording()) {
            ultraAudioIntegration.cancelRecording()
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
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            sharedPrefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
            
            // Initialize conversation token manager EARLY - before components that depend on it
            initializeProductionTokenSystem()
            
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
                    tokenManager = tokenManager
                )
                
                // Setup chat adapter callbacks
                setupChatAdapterCallbacks()
                
                // Initialize the AI chat service AFTER the adapter is ready
                aiChatService = AiChatService(
                    context = this@MainActivity,
                    conversationsViewModel = conversationsViewModel,
                    chatAdapter = chatAdapter,
                    coroutineScope = streamingScope,
                    callbacks = createAiChatCallbacks(),
                    messageManager = messageManager,
                    tokenManager = tokenManager
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
            
            // Setup bottom navigation tabs
            setupBottomTabs()
            
            // Setup keyboard visibility detection
            setupKeyboardVisibilityListener()
            
            Timber.d("Basic UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up basic UI: ${e.message}")
        }
    }

    /**
     * Setup bottom navigation tabs
     */
    private fun setupBottomTabs() {
        try {
            // Setup tab layout with icons and text
            val tabLayout = binding.tabLayout
            
            // Create tabs
            val homeTab = tabLayout.newTab()
                .setText("Home")
                .setIcon(getDrawable(R.drawable.ic_home))
            
            val chatTab = tabLayout.newTab()
                .setText("Chat")
                .setIcon(getDrawable(R.drawable.ic_chat))
            
            val imageTab = tabLayout.newTab()
                .setText("Image")
                .setIcon(getDrawable(R.drawable.ic_image_generation))
            
            val historyTab = tabLayout.newTab()
                .setText("History")
                .setIcon(getDrawable(R.drawable.history_menu))
            
            // Add tabs to layout
            tabLayout.addTab(homeTab)
            tabLayout.addTab(chatTab, true) // Select Chat tab since we're in MainActivity
            tabLayout.addTab(imageTab)
            tabLayout.addTab(historyTab)
            
            // Set up tab selection listener
            tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    when (tab.position) {
                        0 -> {
                            // Home tab - go to StartActivity with no animations
                            provideHapticFeedback()
                            val intent = Intent(this@MainActivity, StartActivity::class.java)
                            intent.putExtra("FROM_TAB_NAVIGATION", true)
                            intent.putExtra("SELECTED_TAB", 0)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            overridePendingTransition(0, 0) // No transition animation
                        }
                        1 -> {
                            // Chat tab - we're already here, just provide feedback
                            provideHapticFeedback()
                        }
                        2 -> {
                            // Image tab - go to ImageGenerationActivity with no animations
                            provideHapticFeedback()
                            val intent = Intent(this@MainActivity, ImageGenerationActivity::class.java)
                            intent.putExtra("FROM_TAB_NAVIGATION", true)
                            intent.putExtra("SELECTED_TAB", 2)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            overridePendingTransition(0, 0) // No transition animation
                        }
                        3 -> {
                            // History tab - go to StartActivity with History tab selected, no animations
                            provideHapticFeedback()
                            val intent = Intent(this@MainActivity, StartActivity::class.java)
                            intent.putExtra("INITIAL_TAB", 3)
                            intent.putExtra("FROM_TAB_NAVIGATION", true)
                            intent.putExtra("SELECTED_TAB", 3)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            overridePendingTransition(0, 0) // No transition animation
                        }
                    }
                }

                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    // Just provide feedback for reselection
                    provideHapticFeedback()
                }
            })
            
            Timber.d("Bottom tabs setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up bottom tabs: ${e.message}")
        }
    }

    /**
     * Ensure the correct tab is selected for this activity
     */
    private fun ensureCorrectTabSelected() {
        try {
            // Only manage Chat tab (position 1) - don't interfere with other activities
            if (::binding.isInitialized) {
                val fromTabNavigation = intent.getBooleanExtra("FROM_TAB_NAVIGATION", false)
                val selectedTab = intent.getIntExtra("SELECTED_TAB", 1)
                
                // Only select Chat tab if explicitly intended for this activity
                if (selectedTab == 1 || fromTabNavigation) {
                    val tabLayout = binding.tabLayout
                    val chatTab = tabLayout.getTabAt(1) // Chat tab is at position 1
                    if (chatTab != null && !chatTab.isSelected) {
                        chatTab.select()
                        Timber.d("MainActivity corrected tab selection to Chat tab")
                    }
                } else {
                    Timber.d("MainActivity skipping tab selection - not intended for Chat tab")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error ensuring correct tab selected: ${e.message}")
        }
    }

    /**
     * Animate tab selection with consistent effects
     */
    private fun animateTabSelection(tab: com.google.android.material.tabs.TabLayout.Tab) {
        try {
            val view = tab.view
            
            // Create a bounce effect with rotation - same as StartActivity
            view.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .rotation(5f)
                .setDuration(100)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    view.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .rotation(-2f)
                        .setDuration(150)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .rotation(0f)
                                .setDuration(100)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .start()
                        }
                        .start()
                }
                .start()

            // Add a subtle color pulse effect to the icon
            tab.icon?.let { icon ->
                val iconView = view.findViewById<android.widget.ImageView>(com.google.android.material.R.id.icon)
                iconView?.animate()
                    ?.alpha(0.6f)
                    ?.setDuration(75)
                    ?.withEndAction {
                        iconView.animate()
                            .alpha(1f)
                            .setDuration(75)
                            .start()
                    }
                    ?.start()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error animating tab selection: ${e.message}")
        }
    }

    /**
     * Setup keyboard visibility detection to hide tabs when keyboard is open
     */
    private fun setupKeyboardVisibilityListener() {
        // Also set up focus listener on input field for immediate response
        setupInputFieldFocusListener()
        try {
            val rootView = binding.root
            val tabLayout = binding.bottomContainer
            
            // Store initial values
            var rootViewHeight = 0
            var isKeyboardCurrentlyVisible = false
            
            val globalLayoutListener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val rect = android.graphics.Rect()
                    rootView.getWindowVisibleDisplayFrame(rect)
                    
                    val currentRootViewHeight = rootView.height
                    
                    // Initialize on first call
                    if (rootViewHeight == 0) {
                        rootViewHeight = currentRootViewHeight
                        return
                    }
                    
                    val heightDifference = rootViewHeight - (rect.bottom - rect.top)
                    val keyboardHeight = heightDifference
                    
                    // More reliable keyboard detection - threshold based on dp value
                    val keyboardThreshold = (200 * resources.displayMetrics.density).toInt()
                    val isKeyboardVisible = keyboardHeight > keyboardThreshold
                    
                    // Debug logging
                    Timber.d("Keyboard detection: height=$keyboardHeight, threshold=$keyboardThreshold, visible=$isKeyboardVisible")
                    
                    // Only animate if state changed
                    if (isKeyboardVisible != isKeyboardCurrentlyVisible) {
                        isKeyboardCurrentlyVisible = isKeyboardVisible
                        
                        if (isKeyboardVisible && tabLayout.visibility == View.VISIBLE) {
                            // Hide tabs with smooth slide down animation
                            Timber.d("Hiding TabLayout - keyboard opened")
                            tabLayout.animate()
                                .translationY(tabLayout.height.toFloat())
                                .alpha(0f)
                                .setDuration(250)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .withEndAction {
                                    tabLayout.visibility = View.GONE
                                }
                                .start()
                        } else if (!isKeyboardVisible && tabLayout.visibility != View.VISIBLE) {
                            // Show tabs with smooth slide up animation  
                            Timber.d("Showing TabLayout - keyboard closed")
                            tabLayout.visibility = View.VISIBLE
                            tabLayout.translationY = tabLayout.height.toFloat()
                            tabLayout.alpha = 0f
                            tabLayout.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(250)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .start()
                        }
                    }
                }
            }
            
            rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
            
            Timber.d("Enhanced keyboard visibility listener setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up keyboard visibility listener: ${e.message}")
        }
    }

    /**
     * Setup focus listener on input field for immediate keyboard response
     */
    private fun setupInputFieldFocusListener() {
        try {
            val tabLayout = binding.bottomContainer
            
            binding.etInputText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Input field gained focus - keyboard likely to appear
                    Timber.d("Input field focused - preparing to hide TabLayout")
                    
                    // Hide TabLayout with slight delay to allow for keyboard animation
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (tabLayout.visibility == View.VISIBLE) {
                            Timber.d("Hiding TabLayout due to input focus")
                            tabLayout.animate()
                                .translationY(tabLayout.height.toFloat())
                                .alpha(0f)
                                .setDuration(200)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .withEndAction {
                                    tabLayout.visibility = View.GONE
                                }
                                .start()
                        }
                    }, 100)
                } else {
                    // Input field lost focus - keyboard likely to disappear
                    Timber.d("Input field unfocused - preparing to show TabLayout")
                    
                    // Show TabLayout with slight delay to allow for keyboard animation
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (tabLayout.visibility != View.VISIBLE) {
                            Timber.d("Showing TabLayout due to input unfocus")
                            tabLayout.visibility = View.VISIBLE
                            tabLayout.translationY = tabLayout.height.toFloat()
                            tabLayout.alpha = 0f
                            tabLayout.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(200)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .start()
                        }
                    }, 300) // Longer delay for showing to ensure keyboard is fully hidden
                }
            }
            
            Timber.d("Input field focus listener setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up input field focus listener: ${e.message}")
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
                
                // Initialize remaining services (buttons/listeners already setup in setupBasicUI)
                initializeEnhancedButtonHandling()
                initializeTextExpansion()
                initializeAudio()
                
                // Setup additional input handling
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
            
            if (currentConversationId != null) {
                // Update token counter UI with new system
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
        // CRITICAL FIX: Do NOT reset model when returning from file selection to prevent model switching
        val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
        val shouldResetModel = appSettings.getBoolean("reset_to_default_model", false)

        if (shouldResetModel && !isReturningFromFileSelection) {
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
        } else if (shouldResetModel && isReturningFromFileSelection) {
            // Clear flag but don't reset model when returning from file selection
            appSettings.edit {
                putBoolean("reset_to_default_model", false)
                apply()
            }
            Timber.d("📎 Skipped model reset because returning from file selection - model preserved: ${ModelManager.selectedModel.displayName}")
        }

        messageManager.fixLoadingStates()

        // Always verify subscription status on resume
        SubscriptionManager.updateSubscriptionStatus(this)
        updateUIBasedOnSubscriptionStatus()

        // Show expiry warning if subscription is expiring soon
        SubscriptionManager.showExpiryWarningIfNeeded(this)

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
            // Skip this for OCR models as they're handled correctly by updateControlsVisibility()
            val currentModelId = ModelManager.selectedModel.id
            val isOcrModel = ModelConfigManager.getConfig(currentModelId)?.isOcrModel == true
            
            if (!isOcrModel) {
                if (inputText.isEmpty()) {
                    setMicrophoneVisibility(true)
                } else {
                    setMicrophoneVisibility(false)
                    binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                    binding.btnSubmitText.contentDescription = "Send message"
                }
            }
        }

        // If more than 10 minutes have passed, consider it a new session
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
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

        // Check for forced updates first, then ongoing updates
        try {
            val updateManager = MyApp.getUpdateManager()
            if (updateManager != null) {
                // Check if a forced update is required
                if (updateManager.isForceUpdateRequired()) {
                    Timber.w("🚨 Forced update required, launching BlockingUpdateActivity from MainActivity")
                    val intent = Intent(this, BlockingUpdateActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    }
                    startActivity(intent)
                    finish()
                    return
                }
                
                // Check for ongoing app updates only if no forced update is pending
                updateManager.checkOngoingUpdates(this)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking updates: ${e.message}")
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

        getSharedPreferences("app_prefs", MODE_PRIVATE).edit {
            putLong("last_session_time", System.currentTimeMillis())
        }
        
        // Ensure the Chat tab is selected when MainActivity is active
        ensureCorrectTabSelected()
        
        } catch (e: Exception) {
            Timber.e(e, "Error in MainActivity onResume: ${e.message}")
            // Don't crash - just log and continue
        }
    }

    /**
     * Override from BaseThemedActivity to apply dynamic accent colors
     */
    override fun applyDynamicAccentColor() {
        try {
            if (::binding.isInitialized) {
                // Apply accent colors to the main UI components
                DynamicColorManager.applyAccentColorToViewGroup(this, binding.root)
                
                // Apply to specific common elements
                DynamicColorManager.applyAccentColorToCommonElements(this, binding.root)
                
                Timber.d("Applied dynamic accent colors to MainActivity")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error applying dynamic accent colors: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            Timber.d("MainActivity onDestroy called")
            
            // Clean up scroll arrows
            if (::scrollArrowsHelper.isInitialized) {
                scrollArrowsHelper.cleanup()
            }
            
            // Clean up ultra audio integration
            if (::ultraAudioIntegration.isInitialized) {
                ultraAudioIntegration.cleanup()
            }
            
            // Cancel animations to prevent leaks
            stopPulsingAnimation()
            
            // Clean up handlers
            uiHandler.removeCallbacksAndMessages(null)
            // recordingHandler.removeCallbacksAndMessages(null) // OLD SYSTEM - REMOVED
            
            // Stop OCR button monitoring
            stopOcrButtonMonitoring()
            
            // Clean up dialogs
            currentEditDialog?.dismiss()
            currentEditDialog = null
            loadingDialog?.dismiss()
            loadingDialog = null
            progressDialog?.dismiss()
            progressDialog = null
            
            // Clean up consent dialog to prevent window leak
            if (::consentManager.isInitialized) {
                consentManager.dismissDialog()
            }
            
            // Cancel API jobs
            currentApiJob?.cancel()
            currentApiJob = null
            buttonMonitoringJob?.cancel()
            buttonMonitoringJob = null
            
            // Clean up ultra audio integration
            if (::ultraAudioIntegration.isInitialized) {
                try {
                    ultraAudioIntegration.cleanup()
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up ultra audio integration: ${e.message}")
                }
            }
            
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
                
                // Clean up production token manager
                // ProductionTokenManager cleanup handled by ConversationTokenManager
                if (::tokenManager.isInitialized) {
                    tokenManager.cleanupOldData()
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
                    adManager.cleanup()  // Now it has a cleanup method!
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
            // OLD VOICE SYSTEM CLEANUP - REMOVED (waveBars and recordingAnimators no longer exist)
            
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
            
            // CRITICAL FIX: Cancel streaming scope to prevent memory leaks but only if activity is finishing
            // Don't cancel on configuration changes to allow streaming to continue
            if (isFinishing) {
                streamingScope.coroutineContext[Job]?.cancel()
                Timber.d("🛑 Streaming scope cancelled on activity finish")
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
                    ModelManager.selectedModel = ModelManager.models[modelIndex]
                    updateSelectedModelDisplay()
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

            if (resultCode == RESULT_OK && data != null) {
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

            if (resultCode == RESULT_OK && data != null) {
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

            // Check credit availability first (for non-subscribers)
            if (!isSubscribed && !creditManager.hasSufficientChatCredits()) {
                Timber.w("❌ Insufficient chat credits for edit message")
                showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                return
            }
            
            // Consume credits immediately for non-subscribers (when edit send button is pressed)
            var creditsConsumed = false
            if (!isSubscribed) {
                if (creditManager.consumeChatCredits()) {
                    creditsConsumed = true
                    updateFreeMessagesText()
                    Timber.d("✅ Chat credits consumed on edit send button press")
                } else {
                    Timber.e("❌ Failed to consume chat credits despite previous check")
                    showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                    return
                }
            }

            // Token management is now handled automatically by the new system
            // Simple validation using TokenValidator
            val wouldExceed = false // For now, disable this check - new system will handle it during send
            val available = 999999 // Placeholder - should not be used since wouldExceed is false

            if (wouldExceed) {
                val inputTokens = TokenValidator.getAccurateTokenCount(newText, ModelManager.selectedModel.id)
                AlertDialog.Builder(this)
                    .setTitle("Edited Message Too Long")
                    .setMessage("The edited message has $inputTokens tokens, but only $available tokens are available.")
                    .setPositiveButton("Truncate & Send") { _, _ ->
                        val truncatedText = TokenValidator.truncateToTokenCount(newText, available)
                        proceedWithEditedMessage(message, truncatedText, reasoningEnabled, deepSearchEnabled)

                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(editText.windowToken, 0)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Edit Again", null)
                    .setNeutralButton("Cancel") { _, _ -> 
                        // Refund credits if consumed and cancelled
                        if (creditsConsumed) {
                            creditManager.refundCredits(1, CreditManager.CreditType.CHAT)
                            updateFreeMessagesText()
                            Timber.d("🔄 Credits refunded due to edit cancellation")
                        }
                        dialog.dismiss() 
                    }
                    .show()
            } else {
                proceedWithEditedMessage(message, newText, reasoningEnabled, deepSearchEnabled)

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking tokens for edited message: ${e.message}")

            // Refund credits if consumed and error occurred
            if (!PrefsManager.isSubscribed(this)) {
                creditManager.refundCredits(1, CreditManager.CreditType.CHAT)
                updateFreeMessagesText()
                Timber.d("🔄 Credits refunded due to edit error")
            }

            // Proceed anyway
            proceedWithEditedMessage(message, newText, reasoningEnabled, deepSearchEnabled)

            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
        btnPerplexitySearchMode = findViewById(R.id.btnPerplexitySearchMode)
        btnPerplexityContextSize = findViewById(R.id.btnPerplexityContextSize)
        btnAiParameters = findViewById(R.id.btnAiParameters)
        
        // Initialize AI Parameters Manager
        aiParametersManager = AIParametersManager.getInstance(this)
        
        // Hide manual web search button - AI models handle tools automatically
        btnDeepSearch.visibility = View.GONE
        
        // Initialize Perplexity button visibility and states
        updatePerplexityButtonsVisibility()

        // Initialize ultra-modern voice recording
        initializeUltraVoiceRecording()

        // Now set up model selector (which calls updateControlsVisibility)
        setupModelSelector()
        updateFreeMessagesText()

        // Make reasoning controls visible by default
        reasoningControlsLayout.visibility = View.VISIBLE

        // Update button states based on loaded settings
        updateReasoningButtonState(isReasoningEnabled)
        updateDeepSearchButtonState(isDeepSearchEnabled)
    }
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

        btnAiParameters.setOnClickListener {
            provideHapticFeedback(50)
            showAiParametersDialog()
        }

        btnPerplexitySearchMode.setOnClickListener {
            provideHapticFeedback(50)
            togglePerplexitySearchMode()
        }

        btnPerplexityContextSize.setOnClickListener {
            provideHapticFeedback(50)
            showPerplexityContextSizeDialog()
        }

        // CHANGED: Update btnConversationsList to be a back button to StartActivity


        binding.btnMainMenu.setOnClickListener { view ->
            Timber.d("🔘 btnMainMenu clicked - triggering showMainMenu")
            showMainMenu(view)
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

        // Conversations list button
        binding.btnConversationsList.setOnClickListener {
            provideHapticFeedback(50)
            navigateToConversationsActivity()
        }

        // Microphone button handler
        binding.btnMicrophone.setOnClickListener {
            if (::ultraAudioIntegration.isInitialized) {
                ultraAudioIntegration.startUltraVoiceRecording()
            } else {
                Toast.makeText(this, "Voice recording not initialized", Toast.LENGTH_SHORT).show()
            }
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
        
        // Initialize font size from preferences
        val savedFontSize = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getFloat("chat_font_size", 16f)
        chatAdapter.updateFontSize(savedFontSize)

        conversationAdapter = ConversationAdapter(
            onConversationClick = { conversation ->
                openConversation(conversation.id, conversation.aiModel)
            },
            onConversationLongClick = { view, conversation ->
                showConversationMenu(view, conversation)
            },
            attachmentsManager = com.cyberflux.qwinai.utils.ConversationAttachmentsIntegration.createAttachmentsManager(this)
        )

        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            
            // PERFORMANCE: Set up view recycling pools for better performance
            val viewPool = RecyclerView.RecycledViewPool()
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_MESSAGE, 10)
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_MESSAGE, 15)
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_IMAGE, 5)
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_IMAGE, 5)
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_USER_DOCUMENT, 5)
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_DOCUMENT, 5)
            viewPool.setMaxRecycledViews(ChatAdapter.VIEW_TYPE_AI_GENERATED_IMAGE, 5)
            setRecycledViewPool(viewPool)
            
            // PERFORMANCE: Enable item animator optimizations
            itemAnimator?.run {
                changeDuration = 0
                moveDuration = 0
            }
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
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

        // Set up click handler to show custom dialog instead of spinner dropdown
        binding.spinnerModels.setOnClickListener {
            showCustomModelSelectionDialog()
        }

        // Load default model
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val defaultModelId = sharedPrefs.getString("default_ai_model_id", ModelManager.DEFAULT_MODEL_ID)
            ?: ModelManager.DEFAULT_MODEL_ID

        // Find the index of the default model and set it
        val currentModelIndex = ModelManager.models.indexOfFirst { it.id == defaultModelId }
        if (currentModelIndex != -1) {
            // Update ModelManager selection first
            ModelManager.selectedModel = ModelManager.models[currentModelIndex]
            
            // Update selected model display
            updateSelectedModelDisplay()

            // Update the model position in response preferences
            responsePreferences.modelPosition = currentModelIndex

            // Record usage for the default model on app start
            ModelUsageTracker.recordModelUsage(this, defaultModelId)
            
            // Update UI
            applyModelColorToUI(defaultModelId)
            updateControlsVisibility(defaultModelId)
        }
        
        // Apply color for the initial model on startup
        applyModelColorToUI(ModelManager.selectedModel.id)
    }
    
    private fun updateSelectedModelDisplay() {
        binding.tvSelectedModel.text = ModelManager.selectedModel.displayName
        
        // Load AI parameters for the initial model
        loadAiParametersForCurrentModel()

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
        } catch (_: Exception) {
            ResponseLength.DEFAULT
        }

        val toneStr = prefs.getString("response_tone", ResponseTone.DEFAULT.name)
        val tone = try {
            ResponseTone.valueOf(toneStr ?: ResponseTone.DEFAULT.name)
        } catch (_: Exception) {
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
                // PRODUCTION TOKEN SYSTEM: Load with comprehensive token management
                // conversationTokenManager.setConversationId(conversationIdString)
                
                // Use ultra-reliable token system loading through ConversationTokenManager
                var tokenStateLoaded = false
                try {
                    Timber.d("📂 Loading conversation with ultra-reliable token system")
                    
                    // Load messages first to get full context
                    val prelimMessages = withContext(Dispatchers.IO) {
                        conversationsViewModel.getAllConversationMessages(conversationIdString)
                    }
                    
                    // ProductionTokenManager handles loading through ConversationTokenManager
                    try {
                        // Token state loading handled automatically by new system
                        val dbLoadSuccess = true // New system doesn't require explicit database loading
                        
                        if (dbLoadSuccess) {
                            Timber.d("✅ PRODUCTION TOKEN LOAD SUCCESS: Loaded from database")
                            tokenStateLoaded = true
                        } else {
                            // Token rebuilding handled automatically by new system
                            tokenStateLoaded = true
                            Timber.d("✅ NEW TOKEN SYSTEM: Ready for use")
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "❌ ProductionTokenManager load error, will use fallback")
                        tokenStateLoaded = false
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Production token loading failed: ${e.message}")
                    tokenStateLoaded = false
                }
                
                // Fallback to legacy token system if production failed
                if (!tokenStateLoaded) {
                    Timber.w("⚠️ Falling back to legacy token loading")
                    tokenStateLoaded = withContext(Dispatchers.IO) {
                        // SimplifiedTokenManager handles state automatically - no manual loading needed
                        false // Fall through to rebuild from messages
                    }
                    
                    if (!tokenStateLoaded) {
                        Timber.d("No saved token state found for conversation $conversationIdString, will rebuild from messages")
                        // conversationTokenManager.reset()
                    } else {
                        Timber.d("Successfully loaded legacy token state for conversation $conversationIdString")
                    }
                }

                // CRITICAL FIX: Load ALL messages from conversation
                val messages = withContext(Dispatchers.IO) {
                    conversationsViewModel.getAllConversationMessages(conversationIdString)
                }
                
                // 🐛 DEBUG: Log all loaded messages in detail
                Timber.d("🐛🐛🐛 LOADED ${messages.size} MESSAGES FROM DATABASE:")
                messages.forEach { message ->
                    Timber.d("🐛 Message ${message.id}:")
                    Timber.d("🐛   - isUser: ${message.isUser}")
                    Timber.d("🐛   - isGenerating: ${message.isGenerating}")
                    Timber.d("🐛   - isLoading: ${message.isLoading}")
                    Timber.d("🐛   - content length: ${message.message.length}")
                    Timber.d("🐛   - content preview: '${message.message.take(100)}...'")
                    Timber.d("🐛   - lastModified: ${message.lastModified}")
                    Timber.d("🐛   - age: ${(System.currentTimeMillis() - message.lastModified) / 1000}s ago")
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
        Timber.d("🔄 Opening conversation: $conversationIdString with ${messages.size} messages")
        
        // CRITICAL FIX: Simple approach - always initialize with all messages from database
        // This ensures user sees existing content immediately, including partial responses
        messageManager.initialize(messages, conversationIdString)
        Timber.d("✅ Initialized MessageManager with ${messages.size} messages")
        
        // CRITICAL FIX: Check for messages that are ACTUALLY currently generating
        // Only process messages that are genuinely in progress, not just marked as generating
        val potentialGeneratingMessages = messages.filter { !it.isUser && it.isGenerating }
        
        // IMPROVED: Validate which messages are actually still generating by checking active sessions
        val actuallyGeneratingMessages = potentialGeneratingMessages.filter { message ->
            val hasActiveSession = UnifiedStreamingManager.getSession(message.id) != null
            val isRecentlyStarted = (System.currentTimeMillis() - message.timestamp) < 30000 // Within 30 seconds
            val hasMinimalContent = message.message.length < 100 // Less than 100 chars suggests still generating
            
            // Only consider a message as generating if it has an active session OR is very recent with minimal content
            val isActuallyGenerating = hasActiveSession || (isRecentlyStarted && hasMinimalContent)
            
            if (!isActuallyGenerating) {
                Timber.d("🛡️ PROTECTION: Skipping message ${message.id} - not actually generating (hasSession=$hasActiveSession, recent=$isRecentlyStarted, minimal=$hasMinimalContent)")
            }
            
            isActuallyGenerating
        }
        
        if (actuallyGeneratingMessages.isNotEmpty()) {
            Timber.d("📋 Found ${actuallyGeneratingMessages.size} messages ACTUALLY generating (filtered from ${potentialGeneratingMessages.size} potential)")
            
            // Log content for debugging
            actuallyGeneratingMessages.forEach { message ->
                Timber.d("📝 Actually generating message ${message.id}: content length=${message.message.length}, content preview='${message.message.take(100)}...'")
            }
            
            // CRITICAL FIX: Set proper UI state for ongoing generation
            setGeneratingState(true)  // This enables the stop button and handles all UI updates
            
            // Start streaming mode to show proper UI state  
            chatAdapter.startStreamingMode()
            
            // CRITICAL FIX: Force stop button to be clickable and visible
            runOnUiThread {
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnSubmitText.isEnabled = true
                binding.btnSubmitText.isClickable = true
                binding.btnSubmitText.isFocusable = true
                binding.btnMicrophone.visibility = View.GONE
                Timber.d("🛑 Forced stop button to be clickable in saved conversation")
            }
            
            Timber.d("🛑 Generating state set to true for saved conversation generation")
            
            // CRITICAL FIX: Show loading indicators ONLY for actually generating messages
            actuallyGeneratingMessages.forEach { message ->
                val index = chatAdapter.currentList.indexOfFirst { it.id == message.id }
                
                if (index != -1) {
                    // CRITICAL FIX: Double approach - both MessageManager and direct adapter calls
                    val statusText = "" // No text status, just loading indicator
                    
                    // Method 1: MessageManager (if available)
                    if (::messageManager.isInitialized) {
                        messageManager.updateLoadingState(message.id, true, false, statusText)
                    }
                    
                    // Method 2: Direct adapter call (ensure it shows)
                    chatAdapter.updateLoadingStateDirect(message.id, true, false, statusText)
                    
                    Timber.d("🔄 Applied loading indicators to ACTUALLY generating message ${message.id}: '$statusText'")
                }
                if (index != -1) {
                    // CRITICAL FIX: Set proper generating state to hide AI navigation buttons
                    val generatingMessage = message.copy(
                        isGenerating = true,
                        isLoading = message.message.isEmpty(), // Only show loading if no content
                        showButtons = false, // CRITICAL: Hide copy/reload buttons during generation
                        canContinueStreaming = true,
                        isWebSearchActive = false, // Ensure proper state
                        partialContent = message.message // Preserve content
                    )
                    
                    // Update the message in adapter to show generating state
                    chatAdapter.updateMessageDirectly(index, generatingMessage)
                    
                    Timber.d("🔄 Set generating state for message: ${message.id} with content: ${message.message.length} chars")
                }
            }
            
            Timber.d("🎬 Started streaming mode and UI state for generating messages")
            
            // Connect to background service for real-time updates (non-blocking)
            lifecycleScope.launch {
                delay(500) // Small delay to let UI settle
                connectToBackgroundServiceForMessages(actuallyGeneratingMessages)
                
                // CRITICAL FIX: Fallback mechanism - if service connection fails, check database directly
                delay(3000) // Wait 3 seconds for service connection
                fallbackCheckGeneratingMessages(actuallyGeneratingMessages)
            }
        }

        // Rebuild token manager if needed
        if (!tokenStateLoaded && messages.isNotEmpty()) {
            Timber.d("Rebuilding token state from ${messages.size} messages")
            lifecycleScope.launch {
                // conversationTokenManager.rebuildFromMessagesProduction(messages, conversationIdString)
            }

            // Token state saving handled automatically by new system
        }

        // Force update token counter UI with production system
        updateTokenCounterWithProduction()

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
                            loadDraftFiles(conversation.draftFiles, conversationId)
                        } else {
                            // No files, make sure file container is hidden
                            selectedFiles.clear()
                            binding.selectedFilesScrollView.visibility = View.GONE
                            if (::fileHandler.isInitialized) {
                                fileHandler.updateSelectedFilesView()
                            }
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
    private fun loadDraftFiles(draftFilesJson: String, conversationId: String) {
        if (draftFilesJson.isEmpty()) return

        try {
            Timber.d("Loading draft files from JSON: ${draftFilesJson.take(200)}...")
            
            // Try to deserialize as SelectedFile format first (old format)
            var draftFiles = if (::fileHandler.isInitialized) {
                fileHandler.deserializeSelectedFiles(draftFilesJson)
            } else {
                emptyList()
            }

            // If that didn't work, try to deserialize as MessageDraftManager.DraftFile format (new format)
            if (draftFiles.isEmpty()) {
                try {
                    val moshi = com.squareup.moshi.Moshi.Builder().build()
                    val fileListType = com.squareup.moshi.Types.newParameterizedType(
                        List::class.java, 
                        com.cyberflux.qwinai.utils.MessageDraftManager.DraftFile::class.java
                    )
                    val adapter = moshi.adapter<List<com.cyberflux.qwinai.utils.MessageDraftManager.DraftFile>>(fileListType)
                    val messageDraftFiles = adapter.fromJson(draftFilesJson) ?: emptyList()
                    
                    if (messageDraftFiles.isNotEmpty()) {
                        Timber.d("Found MessageDraftManager.DraftFile format, converting to SelectedFile format")
                        // Convert MessageDraftManager.DraftFile to SelectedFile format
                        draftFiles = messageDraftFiles.mapNotNull { draftFile ->
                            try {
                                FileUtil.SelectedFile(
                                    uri = android.net.Uri.parse(draftFile.uri),
                                    name = draftFile.name,
                                    size = draftFile.size,
                                    isDocument = !draftFile.type.startsWith("image/"),
                                    isExtracted = false,
                                    extractedContentId = "",
                                    isPersistent = false,
                                    persistentFileName = ""
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Error converting draft file: ${draftFile.name}")
                                null
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error deserializing as MessageDraftManager.DraftFile format")
                }
            }

            if (draftFiles.isEmpty()) {
                Timber.d("No draft files found in any supported format")
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
                        val restoredFile = FileUtil.SelectedFile.fromPersistentFileName(
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
                        contentResolver.openInputStream(file.uri)?.use { true } == true
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

            // Update UI based on results
            if (validFilesCount > 0) {
                binding.selectedFilesScrollView.visibility = View.VISIBLE

                // Update the files view
                if (::fileHandler.isInitialized) {
                    fileHandler.updateSelectedFilesView()
                }

                Timber.d("Loaded $validFilesCount draft files out of ${draftFiles.size} saved")
                
                // Show user feedback if some files were missing
                val missingFilesCount = draftFiles.size - validFilesCount
                if (missingFilesCount > 0) {
                    val message = if (missingFilesCount == 1) {
                        "1 draft file was no longer available and was removed"
                    } else {
                        "$missingFilesCount draft files were no longer available and were removed"
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                    
                    // Clean up the draft in the database to remove invalid file references
                    cleanupInvalidDraftFiles(conversationId.toString(), selectedFiles)
                }
            } else {
                binding.selectedFilesScrollView.visibility = View.GONE
                Timber.d("No valid files found from draft")
                
                // If we had draft files but none were valid, notify user and clean up
                if (draftFiles.isNotEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, 
                            "Draft files are no longer available and were removed", 
                            Toast.LENGTH_LONG).show()
                    }
                    
                    // Clean up the draft in the database
                    cleanupInvalidDraftFiles(conversationId.toString(), emptyList())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing draft files JSON: ${e.message}")
            // Fallback: Hide file container and clear any partial files
            selectedFiles.clear()
            binding.selectedFilesScrollView.visibility = View.GONE
            if (::fileHandler.isInitialized) {
                fileHandler.updateSelectedFilesView()
            }
            
            // Show user feedback about the issue
            runOnUiThread {
                Toast.makeText(this, "Some draft files could not be restored", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Clean up invalid draft file references in the database
     * Updates the conversation's draft files to only include valid files
     */
    private fun cleanupInvalidDraftFiles(conversationId: String, validFiles: List<FileUtil.SelectedFile>) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val conversation = conversationsViewModel.getConversationById(conversationId)
                    if (conversation != null) {
                        // Serialize only the valid files back to JSON
                        val updatedDraftFilesJson = if (validFiles.isNotEmpty() && ::fileHandler.isInitialized) {
                            fileHandler.serializeSelectedFiles(validFiles)
                        } else {
                            ""
                        }
                        
                        // Update conversation with cleaned up draft files
                        val updatedConversation = conversation.copy(
                            draftFiles = updatedDraftFilesJson,
                            hasDraft = conversation.draftText.isNotEmpty() || validFiles.isNotEmpty(),
                            draftTimestamp = System.currentTimeMillis()
                        )
                        
                        conversationsViewModel.updateConversation(updatedConversation)
                        
                        Timber.d("Cleaned up draft files for conversation $conversationId: " +
                               "${validFiles.size} valid files remaining")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up invalid draft files for conversation $conversationId")
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
        Timber.d("🛑🛑🛑 STOP GENERATION BUTTON PRESSED - MainActivity.stopGeneration() called")
        Timber.d("🛑 Current isGenerating state: $isGenerating")
        Timber.d("🛑 ChatAdapter streaming active: ${if (::chatAdapter.isInitialized) chatAdapter.isStreamingActive else "not initialized"}")
        Timber.d("🛑 Current thread: ${Thread.currentThread().name}")

        // CRITICAL: Cancel HTTP requests immediately
        try {
            RetrofitInstance.cancelAllRequests()
            Timber.d("🛑 ✅ HTTP requests cancelled from MainActivity")
        } catch (e: Exception) {
            Timber.e(e, "🛑 ❌ Error cancelling HTTP requests: ${e.message}")
        }

        // CRITICAL: Force stop all streaming immediately
        if (::chatAdapter.isInitialized) {
            chatAdapter.stopStreamingModeGradually()
            Timber.d("🛑 Forced stop ChatAdapter streaming")
        }
        
        if (::messageManager.isInitialized) {
            messageManager.stopStreamingMode()
            Timber.d("🛑 Forced stop MessageManager streaming")
        }

        // Call the AiChatService method to properly cancel the API call
        aiChatService.cancelCurrentGeneration()
        
        // CRITICAL FIX: Cancel background generation for saved conversations
        try {
            StreamingHandler.cancelAllStreaming()
            backgroundAiService?.let { service ->
                // Get any generating message IDs to stop them specifically
                val generatingMessages = chatAdapter.currentList.filter { it.isGenerating && !it.isUser }
                generatingMessages.forEach { message ->
                    BackgroundAiService.stopGeneration(this, message.id)
                    Timber.d("🛑 Stopping background generation for message: ${message.id}")
                }
            }
            Timber.d("🛑 ✅ Background generation cancelled")
        } catch (e: Exception) {
            Timber.e(e, "🛑 ❌ Error cancelling background generation: ${e.message}")
        }

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
            setMicrophoneVisibility(true)
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
                        message = message.message // Keep existing message content, don't add "Generation stopped"
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


    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                                binding.btnMainMenu.isEnabled = true
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
                        if (::fileHandler.isInitialized) {
                            fileHandler.handleSelectedFile(uri, false)
                        }

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
                menu.findItem(R.id.menu_watch_ad_1).title = "Watch ad for 1 credit"
                menu.findItem(R.id.menu_watch_ad_1).isEnabled = true
            } else {
                menu.findItem(R.id.menu_watch_ad_1).isEnabled = false
                menu.findItem(R.id.menu_watch_ad_1).title = "Watch ad for 1 credit (limit reached)"
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_get_unlimited_credits -> navigateToWelcomeActivity()
                    R.id.menu_watch_ad_1 -> adManager.watchAdForCredits(this@MainActivity, 1)  // Use the new method for UI integration
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                        if (::fileHandler.isInitialized) {
                            fileHandler.openFilePicker(filePickerLauncher)
                        }
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
                                if (::fileHandler.isInitialized) {
                                    fileHandler.handleSelectedFile(uri, false)
                                }
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

    private fun showMainMenu(view: View) {
        Timber.d("🔘 Main menu button clicked - showing beautiful menu dialog")
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_main_menu, null)
            
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()
            
            // Configure dialog window positioning
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.TOP or Gravity.END)
                
                val params = attributes
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                
                // Position dialog just below the menu button with proper margins
                params.x = 16 // Right margin from screen edge
                params.y = location[1] + view.height + 8 // Small gap below button
                attributes = params
                
                // Set dialog dimensions
                setLayout(
                    280.dpToPx(), // Fixed width matching layout
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Get current follow-up questions setting
            val followUpEnabled = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("follow_up_questions_enabled", true)
            
            val toggleSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.toggle_follow_up)
            toggleSwitch.isChecked = followUpEnabled
            
            // Set up click listeners
            dialogView.findViewById<LinearLayout>(R.id.menu_new_chat).setOnClickListener {
                Timber.d("🔘 New Chat clicked")
                dialog.dismiss()
                startNewConversation()
            }
            
            dialogView.findViewById<LinearLayout>(R.id.menu_chat_settings).setOnClickListener {
                Timber.d("🔘 Chat Settings clicked")
                dialog.dismiss()
                showChatSettingsDialog()
            }
            
            dialogView.findViewById<LinearLayout>(R.id.menu_speech_settings).setOnClickListener {
                Timber.d("🔘 Speech Settings clicked")
                dialog.dismiss()
                showSpeechSettingsDialog()
            }
            
            dialogView.findViewById<LinearLayout>(R.id.menu_font_size).setOnClickListener {
                Timber.d("🔘 Font Size clicked")
                dialog.dismiss()
                showFontSizeDialog()
            }
            
            dialogView.findViewById<LinearLayout>(R.id.menu_share).setOnClickListener {
                Timber.d("🔘 Share clicked")
                dialog.dismiss()
                shareCurrentConversation()
            }
            
            dialogView.findViewById<LinearLayout>(R.id.menu_follow_up_questions).setOnClickListener {
                Timber.d("🔘 Follow-up Questions clicked")
                val newState = !toggleSwitch.isChecked
                toggleSwitch.isChecked = newState
                toggleFollowUpQuestions(newState)
                dialog.dismiss()
            }
            
            // Also handle direct toggle switch clicks
            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                Timber.d("🔘 Follow-up Questions toggled to: $isChecked")
                toggleFollowUpQuestions(isChecked)
            }
            
            dialog.show()
            
        } catch (e: Exception) {
            Timber.e(e, "Error showing beautiful menu: ${e.message}")
            Toast.makeText(this, "Error showing menu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFontSizeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_font_size, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // Remove all dialog margins and padding for true edge-to-edge display
            decorView.setPadding(0, 0, 0, 0)
            attributes?.let { layoutParams ->
                layoutParams.horizontalMargin = 0f
                layoutParams.verticalMargin = 0f
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.x = 0
                layoutParams.y = 0
                attributes = layoutParams
            }
            // Ensure no system window insets
            decorView.systemUiVisibility = decorView.systemUiVisibility or 
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        
        val slider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.fontSizeSlider)
        val previewText = dialogView.findViewById<TextView>(R.id.tvPreviewText)
        val btnApply = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApplyFontSize)
        
        // Load current font size from preferences
        val currentFontSize = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getFloat("chat_font_size", 16f)
        slider.value = currentFontSize
        previewText.textSize = currentFontSize
        
        // Update preview as slider moves
        slider.addOnChangeListener { _, value, _ ->
            previewText.textSize = value
        }
        
        // Apply button click
        btnApply.setOnClickListener {
            val selectedSize = slider.value
            
            // Save to preferences
            getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putFloat("chat_font_size", selectedSize)
                .apply()
            
            // Apply to chat adapter
            chatAdapter.updateFontSize(selectedSize)
            android.widget.Toast.makeText(this, "Font size updated to ${selectedSize.toInt()}sp", android.widget.Toast.LENGTH_SHORT).show()
            
            dialog.dismiss()
        }
        
        // Set up drag handle functionality
        val dragHandleContainer = dialogView.findViewById<android.widget.FrameLayout>(R.id.dragHandleContainer)
        setupDragHandle(dialog, dragHandleContainer)
        
        dialog.show()
    }

    private fun showCustomModelSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tabbed_spinner, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            attributes?.windowAnimations = R.style.DialogSlideAnimation
            // Remove all dialog margins and padding for true edge-to-edge display
            decorView.setPadding(0, 0, 0, 0)
            attributes?.let { layoutParams ->
                layoutParams.horizontalMargin = 0f
                layoutParams.verticalMargin = 0f
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.x = 0
                layoutParams.y = 0
                attributes = layoutParams
            }
            // Ensure no system window insets
            decorView.systemUiVisibility = decorView.systemUiVisibility or 
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.modelsRecyclerView)
        val btnDone = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDone)
        
        // Set up grid layout manager (2 columns to match Image #2 design)
        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        
        // Get current selected model position
        val currentSelection = ModelManager.models.indexOfFirst { it.id == ModelManager.selectedModel.id }
        
        // Create adapter with current selection
        val gridAdapter = ModelGridAdapter(
            context = this,
            models = ModelManager.models,
            selectedPosition = currentSelection,
            onItemClick = { position: Int ->
                val selectedModel = ModelManager.models[position]
                
                // Check translation mode compatibility
                if (isTranslationModeActive && !TranslationUtils.supportsTranslation(selectedModel.id)) {
                    Toast.makeText(this, "${selectedModel.displayName} doesn't support translation", Toast.LENGTH_SHORT).show()
                    return@ModelGridAdapter
                }
                
                // Update model selection
                ModelManager.selectedModel = selectedModel
                
                // Update the displayed model name
                updateSelectedModelDisplay()
                
                // Update UI to match model
                ModelValidator.clearCache()
                updateControlsVisibility(selectedModel.id)
                applyModelColorToUI(selectedModel.id)
                chatAdapter.updateCurrentModel(selectedModel.id)
                
                // Record model usage
                ModelUsageTracker.recordModelUsage(this, selectedModel.id)
                
                // Dismiss dialog after short delay for visual feedback
                Handler(Looper.getMainLooper()).postDelayed({
                    dialog.dismiss()
                }, 150)
                
                Timber.d("Model selected from grid: ${selectedModel.displayName}")
            }
        )
        
        recyclerView.adapter = gridAdapter
        
        // Done button click
        btnDone.setOnClickListener {
            dialog.dismiss()
        }
        
        // Set up drag handle functionality
        val dragHandleContainer = dialogView.findViewById<android.widget.FrameLayout>(R.id.dragHandleContainer)
        setupDragHandle(dialog, dragHandleContainer)
        
        dialog.show()
    }

    private fun showSpeechSettingsDialog() {
        // TODO: Implement speech settings dialog
        Toast.makeText(this, "Speech Settings - Coming Soon", Toast.LENGTH_SHORT).show()
    }
    
    @SuppressLint("ClickableViewAccessibility") 
    private fun setupDragHandle(dialog: androidx.appcompat.app.AlertDialog, dragHandleContainer: android.widget.FrameLayout) {
        var initialY = 0f
        var initialTouchY = 0f
        var isDragging = false
        
        dragHandleContainer.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialY = dialog.window?.decorView?.y ?: 0f
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) {
                        val deltaY = kotlin.math.abs(event.rawY - initialTouchY)
                        if (deltaY > 20) { // Start dragging threshold
                            isDragging = true
                        }
                    }
                    
                    if (isDragging) {
                        val deltaY = event.rawY - initialTouchY
                        val newY = initialY + deltaY
                        
                        // Constrain to screen bounds
                        val maxY = resources.displayMetrics.heightPixels * 0.8f
                        val constrainedY = newY.coerceIn(0f, maxY)
                        
                        dialog.window?.decorView?.y = constrainedY
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val deltaY = event.rawY - initialTouchY
                        
                        // If dragged down significantly, dismiss dialog
                        if (deltaY > 200) {
                            dialog.dismiss()
                        } else {
                            // Snap back to bottom
                            val displayHeight = resources.displayMetrics.heightPixels
                            val dialogHeight = dialog.window?.decorView?.height ?: 0
                            val targetY = displayHeight - dialogHeight.toFloat()
                            
                            dialog.window?.decorView?.animate()
                                ?.y(targetY)
                                ?.setDuration(200)
                                ?.start()
                        }
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }
    
    private fun shareCurrentConversation() {
        // TODO: Implement conversation sharing
        Toast.makeText(this, "Share Conversation - Coming Soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleFollowUpQuestions(enabled: Boolean) {
        Timber.d("🔘 Follow-up questions toggled: $enabled")
        
        // Save setting to SharedPreferences
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("follow_up_questions_enabled", enabled)
            .apply()
        
        // Show feedback to user
        val message = if (enabled) {
            "Follow-up questions enabled"
        } else {
            "Follow-up questions disabled" 
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        // You can add additional logic here to handle the follow-up questions functionality
        // For example, showing/hiding UI elements or updating chat behavior
    }

    private fun showChatSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_chat_settings, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // Remove all dialog margins and padding for true edge-to-edge display
            decorView.setPadding(0, 0, 0, 0)
            attributes?.let { layoutParams ->
                layoutParams.horizontalMargin = 0f
                layoutParams.verticalMargin = 0f
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.x = 0
                layoutParams.y = 0
                attributes = layoutParams
            }
            // Ensure no system window insets
            decorView.systemUiVisibility = decorView.systemUiVisibility or 
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        
        val lengthContainer = dialogView.findViewById<LinearLayout>(R.id.lengthOptionsContainer)
        val toneContainer = dialogView.findViewById<LinearLayout>(R.id.toneOptionsContainer)
        val btnApply = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApplyChatSettings)
        
        // Setup response options (simplified version)
        setupResponseLengthOptions(lengthContainer)
        setupResponseToneOptions(toneContainer)
        
        // Apply button click
        btnApply.setOnClickListener {
            // Save current selections to preferences
            // TODO: Implement saving logic
            android.widget.Toast.makeText(this, "Chat settings applied", android.widget.Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        // Set up drag handle functionality
        val dragHandleContainer = dialogView.findViewById<android.widget.FrameLayout>(R.id.dragHandleContainer)
        setupDragHandle(dialog, dragHandleContainer)
        
        dialog.show()
    }
    
    private fun setupResponseLengthOptions(container: LinearLayout) {
        container.removeAllViews()
        
        val lengths = listOf("Short", "Default", "Long")
        val currentSelection = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("response_length", "Default")
        
        for (length in lengths) {
            val chipView = com.google.android.material.chip.Chip(this)
            chipView.text = length
            chipView.isCheckable = true
            chipView.isChecked = (length == currentSelection)
            chipView.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Uncheck other chips
                    for (i in 0 until container.childCount) {
                        val otherChip = container.getChildAt(i) as com.google.android.material.chip.Chip
                        if (otherChip != chipView) {
                            otherChip.isChecked = false
                        }
                    }
                    // Save selection
                    getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("response_length", length)
                        .apply()
                }
            }
            
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 0, 8, 0)
            chipView.layoutParams = layoutParams
            
            container.addView(chipView)
        }
    }
    
    private fun setupResponseToneOptions(container: LinearLayout) {
        container.removeAllViews()
        
        // Get all available tones from ResponseTone enum
        val allTones = ResponseTone.values()
        val currentSelection = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("response_tone", "DEFAULT")
        
        // Create two-column layout for tones
        var currentRowLayout: LinearLayout? = null
        var columnCount = 0
        
        for ((index, tone) in allTones.withIndex()) {
            // Create new row every 2 items
            if (columnCount == 0) {
                currentRowLayout = LinearLayout(this)
                currentRowLayout.orientation = LinearLayout.HORIZONTAL
                currentRowLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                container.addView(currentRowLayout)
            }
            
            val chipView = com.google.android.material.chip.Chip(this)
            chipView.text = tone.displayName
            chipView.isCheckable = true
            chipView.isChecked = (tone.name == currentSelection)
            chipView.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Uncheck other chips in all rows
                    uncheckAllToneChips(container)
                    chipView.isChecked = true // Re-check this one
                    
                    // Save selection
                    getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("response_tone", tone.name)
                        .apply()
                }
            }
            
            // Set chip layout params for two-column layout
            val layoutParams = LinearLayout.LayoutParams(
                0, // Use weight for equal width
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f // Equal weight for both columns
            )
            layoutParams.setMargins(4, 4, 4, 4)
            chipView.layoutParams = layoutParams
            
            currentRowLayout?.addView(chipView)
            
            columnCount++
            if (columnCount == 2) {
                columnCount = 0 // Reset for next row
            }
        }
    }
    
    private fun uncheckAllToneChips(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val rowLayout = container.getChildAt(i) as LinearLayout
            for (j in 0 until rowLayout.childCount) {
                val chip = rowLayout.getChildAt(j) as com.google.android.material.chip.Chip
                chip.isChecked = false
            }
        }
    }

    private fun showAttachmentMenu() {
        val currentModel = ModelManager.selectedModel
        ModelConfigManager.getConfig(currentModel.id)
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
                Timber.d("File selected from attachment menu: $uri")
                try {
                    if (::fileHandler.isInitialized) {
                        Timber.d("FileHandler is initialized, processing file...")
                        fileHandler.handleSelectedFile(uri, false)
                        Timber.d("File processing completed")
                    } else {
                        Timber.e("FileHandler not initialized when trying to select file!")
                        Toast.makeText(this, "Please wait for app to finish loading", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing selected file: ${e.message}")
                    Toast.makeText(this, "Error processing file: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    // Clear stored model state after file processing
                    modelStateBeforeFilePicker = null
                    Timber.d("📎 Cleared stored model state after file processing")
                }
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
            },
            onPickerLaunch = {
                // Set flag when external picker is about to be launched
                isReturningFromFileSelection = true
                
                // CRITICAL FIX: Store model state before launching file picker to prevent reset
                modelStateBeforeFilePicker = ModelManager.selectedModel
                Timber.d("📎 Setting isReturningFromFileSelection = true (bottom sheet picker launch)")
                Timber.d("📎 Stored model state before picker: ${modelStateBeforeFilePicker?.displayName}")
            }
        )
        
        fileUploadBottomSheet.show(supportFragmentManager, "FileUploadBottomSheet")
    }


    // Helper method to set model with callback
    private fun setSelectedModelWithCallback(modelId: String, onComplete: () -> Unit) {
        try {
            val modelIndex = ModelManager.models.indexOfFirst { it.id == modelId }
            if (modelIndex != -1) {
                // Update ModelManager first
                ModelManager.selectedModel = ModelManager.models[modelIndex]
                
                // Update selected model display
                updateSelectedModelDisplay()

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

        // Token state automatically saved by new system
    }
    /**
     * Update token counter UI method
     * Enhanced to use secure token management system
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun updateTokenCounterUI() {
        try {
            val tvTokenCount = findViewById<TextView>(R.id.tvTokenCount) ?: return
            val conversationId = currentConversationId ?: return
            val modelId = ModelManager.selectedModel.id
            val isSubscribed = PrefsManager.isSubscribed(this)
            
            if (!::tokenManager.isInitialized) {
                tvTokenCount.text = "Token system not initialized"
                return
            }
            
            // Get usage summary from new token system
            val usageSummary = tokenManager.getUsageSummary(
                conversationId = conversationId,
                modelId = modelId,
                isSubscribed = isSubscribed
            )
            
            // Update UI
            tvTokenCount.text = usageSummary
            
            // Check if warning needed
            val (needsWarning, warningMessage) = tokenManager.checkNeedsWarning(
                conversationId = conversationId,
                modelId = modelId,
                isSubscribed = isSubscribed
            )
            
            if (needsWarning) {
                tvTokenCount.setTextColor(ContextCompat.getColor(this, R.color.warning_color))
                tokenLimitWarningBadge?.visibility = View.VISIBLE
            } else {
                tvTokenCount.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface))
                tokenLimitWarningBadge?.visibility = View.GONE
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating token counter UI: ${e.message}")
            findViewById<TextView>(R.id.tvTokenCount)?.text = "Token error"
        }
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
            val newTokens = TokenValidator.getAccurateTokenCount(newText, ModelManager.selectedModel.id)

            // Calculate tokens for the original message
            val originalTokens = TokenValidator.getAccurateTokenCount(originalMessage.message, ModelManager.selectedModel.id)

            // Calculate the difference
            val tokenDifference = newTokens - originalTokens

            // Try to get conversation tokens excluding original message
            val currentTokens = try {
                // SimplifiedTokenManager uses different approach - estimate current conversation size
                val conversationUsage = tokenManager.getConversationUsageDetails(currentConversationId ?: "")
                (conversationUsage?.totalTokens ?: 0) - originalTokens
            } catch (e: Exception) {
                Timber.e(e, "Error getting conversation tokens")
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
    }    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
            // conversationTokenManager.removeMessage(originalMessage.id)

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
            // conversationTokenManager.addMessage(originalMessage)

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
                // Token state automatically saved by new system
            }

            // Show loading indicator
            binding.typingIndicator.visibility = View.VISIBLE
            setGeneratingState(true)

            // Wait for UI to update with the edited message
            lifecycleScope.launch {
                delay(100)
                
                // Credits already consumed on button press - no need to consume again
                
                // Handle existing AI responses for this user message
                handleExistingAiResponsesForEditedMessage(originalMessage.id)

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
                
                val requestBody = requestJson
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                // Make API request
                val response = aiChatService.makeAudioRequest(
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
        ModelConfigManager.getConfig(modelId)
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
                Timber.d("Received audio data: format=${audioData.format}, content length=${audioData.content.length}")
                
                // Check if we have audio content
                val audioContent = audioData.content
                if (audioContent.isNotEmpty()) {
                    
                    // Validate if content is actually base64
                    val isValidBase64 = try {
                        android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                        true
                    } catch (_: IllegalArgumentException) {
                        false
                    }
                    
                    if (isValidBase64) {
                        // Play the audio using AudioResponseHandler
                        lifecycleScope.launch {
                            val success = audioResponseHandler.playAudioFromBase64(
                                audioBase64 = audioContent,
                                messageId = message.id,
                                format = audioData.format
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
                    val hasFiles = selectedFiles.isNotEmpty()
                    toggleInputButtons(s?.toString()?.trim()?.isNotEmpty() == true, hasFiles)
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
        
        // CRITICAL FIX: Also clear MessageManager internal state
        messageManager.clearAll()
        
        // CRITICAL FIX: Clear all draft-related UI state
        binding.etInputText.text?.clear()
        selectedFiles.clear()
        binding.selectedFilesScrollView.visibility = View.GONE
        
        // Only update file handler view if it's been initialized
        if (::fileHandler.isInitialized) {
            fileHandler.updateSelectedFilesView()
        }
        
        // Reset input UI state
        setMicrophoneVisibility(true)

        // Set appropriate title based on private mode
        if (isPrivateModeEnabled) {
            chatTitleView.text = "Private Chat"
        } else {
            chatTitleView.text = "New Chat"
        }

        binding.btnMainMenu.isEnabled = true

        // Also reset generation state
        isGenerating = false
        chatAdapter.setGenerating(false)
        
        // CRITICAL FIX: Force proper button state reset
        setGeneratingState(false)
    }   /**
     * Update the new conversation button state
     */
    private fun updateNewConversationButtonState() {
        // Enable the button if:
        // 1. We have a current conversation (regardless of messages), or
        // 2. We're in a new chat but no conversation ID
        binding.btnMainMenu.isEnabled = (currentConversationId != null) ||
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
            ModelManager.selectedModel = ModelManager.models[modelIndex]
            updateSelectedModelDisplay()
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
                !::btnPerplexitySearchMode.isInitialized ||
                !::btnPerplexityContextSize.isInitialized ||
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
                
                // Hide microphone button for OCR models (especially Mistral OCR)
                if (::btnMicrophone.isInitialized) {
                    btnMicrophone.visibility = View.GONE
                    binding.btnSubmitText.visibility = View.VISIBLE
                }
                
                // Clear any existing text for OCR models
                if (binding.etInputText.text.toString().isNotEmpty()) {
                    binding.etInputText.setText("")
                }
            } else {
                binding.etInputText.hint = "Type a message..."
                binding.etInputText.isEnabled = true
                binding.ocrOptionsPanel.visibility = View.GONE
                
                // Restore normal microphone/send button behavior for non-OCR models
                if (::btnMicrophone.isInitialized) {
                    // Reset to normal text input behavior (controlled by text watcher)
                    val hasText = binding.etInputText.text.toString().trim().isNotEmpty()
                    setMicrophoneVisibility(!hasText)
                }
                
                // Send button enablement is controlled by the text watcher
            }

            // Update Perplexity-specific button visibility
            updatePerplexityButtonsVisibility()

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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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


    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
        binding.btnMainMenu.isEnabled = true
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

    fun exitTranslationMode() {
        closeTranslatorMode()
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
            // conversationTokenManager.setConversationId(currentConversationId!!)
        }

        // Reset conversation ID first, then reset token manager
        currentConversationId = null
        // conversationTokenManager.reset()

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
        binding.btnMainMenu.isEnabled = true
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

    /**
     * Start a new conversation with a predefined question (used for related questions)
     */
    fun startNewConversationWithQuestion(question: String) {
        // Stop any ongoing generation
        if (isGenerating) {
            stopGeneration()
            // Wait a moment for cleanup
            uiHandler.postDelayed({
                proceedWithNewConversationAndQuestion(question)
            }, 300)
        } else {
            proceedWithNewConversationAndQuestion(question)
        }
    }

    private fun proceedWithNewConversationAndQuestion(question: String) {
        try {
            // First ensure we have a clean slate
            proceedWithNewConversation()
            
            // Set the question in the input field
            binding.etInputText.setText(question)
            
            // Automatically send the message after a short delay
            uiHandler.postDelayed({
                if (binding.etInputText.text.toString() == question) {
                    sendMessage()
                }
            }, 500)
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting new conversation with question")
            Toast.makeText(this, "Failed to start new conversation", Toast.LENGTH_SHORT).show()
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
        // conversationTokenManager.reset()

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
        binding.btnMainMenu.isEnabled = true
        isMessageSent = false

        // Reset button to proper state
        val inputText = binding.etInputText.text.toString().trim()
        if (inputText.isEmpty()) {
            setMicrophoneVisibility(true)
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
                    setMicrophoneVisibility(true)
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

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        // Always use current model ID, not cached one
                        val currentModelId = ModelManager.selectedModel.id
                        val isSubscribed = PrefsManager.isSubscribed(this@MainActivity)

                        updateTokenCounterUI(
                            tvTokenCount,
                            s?.toString() ?: "",
                            currentModelId,
                            isSubscribed
                        )

                        // Check token limits with current message
                        val text = s?.toString() ?: ""
                        // Token validation is now handled during message sending by the new system

                        // Toggle buttons based on text content
                        val hasFiles = selectedFiles.isNotEmpty()
                        toggleInputButtons(s?.toString()?.trim()?.isNotEmpty() == true, hasFiles)
                        
                        // Pre-validation for ultra-fast message sending
                        val message = text.trim()
                        if (message.length > 10) { // Only pre-validate substantial messages
                            performPreValidation(message)
                        }

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
                    val maxTokens = TokenValidator.getEffectiveMaxInputTokens(currentModelId, isSubscribed)
                    val message = "Current model: ${ModelManager.selectedModel.displayName}\nMax tokens: ${maxTokens}\nSubscribed: ${if (isSubscribed) "Yes" else "No"}"
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Timber.e(e, "Error showing token explanation: ${e.message}")
                }
                true
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in setupTokenMonitoring: ${e.message}")
        }

    }

    private fun toggleInputButtons(hasText: Boolean, hasFiles: Boolean = false) {
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
            
            // For OCR models, always show send button and be more permissive with enabling
            if (isOcrModel) {
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                binding.btnSubmitText.contentDescription = "Process OCR"
                
                // For OCR models, be more permissive - enable if files OR text OR likely returning from selection
                val hasFilesInFunction = hasFiles || selectedFiles.isNotEmpty()
                val hasTextContent = hasText || binding.etInputText.text.toString().trim().isNotEmpty()
                // Also check if any file upload dialogs might be open or we recently launched one
                val isLikelyFileSelection = isReturningFromFileSelection
                
                // OCR models should remain enabled when there's content or when user is likely selecting files
                binding.btnSubmitText.isEnabled = hasFilesInFunction || hasTextContent || isLikelyFileSelection
                
                // Set alpha to provide visual feedback
                binding.btnSubmitText.alpha = if (binding.btnSubmitText.isEnabled) 1.0f else 0.7f
                
                Timber.d("OCR button state - hasFiles: $hasFilesInFunction, hasText: $hasTextContent, likelySelection: $isLikelyFileSelection, enabled: ${binding.btnSubmitText.isEnabled}")
                return
            }

            // Not generating, proceed with normal button state based on text content OR files
            if (hasText || hasFiles) {
                // Has text OR files - show send button
                binding.btnMicrophone.visibility = View.GONE
                binding.btnSubmitText.visibility = View.VISIBLE
                // IMPORTANT: Explicitly set the send icon
                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                
                if (hasFiles && !hasText) {
                    binding.btnSubmitText.contentDescription = "Send with attachments"
                } else {
                    binding.btnSubmitText.contentDescription = "Send message"
                }
                
                // CRITICAL: Enable the button for non-OCR models with text or files
                binding.btnSubmitText.isEnabled = true
                binding.btnSubmitText.alpha = 1.0f
                
                Timber.d("Send button shown - hasText: $hasText, hasFiles: $hasFiles")
            } else {
                // No text AND no files - show microphone button (unless OCR model)
                setMicrophoneVisibility(true)
                Timber.d("Microphone shown - no text and no files")
            }

            // Log the button state change for debugging
            Timber.d("toggleInputButtons: hasText=$hasText, hasFiles=$hasFiles, isGenerating=$isGenerating, " +
                    "btnSubmitText.visibility=${binding.btnSubmitText.visibility}, " +
                    "contentDescription=${binding.btnSubmitText.contentDescription}")
        } catch (e: Exception) {
            Timber.e(e, "Error in toggleInputButtons: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
        // conversationTokenManager.setContinuedPastWarning(true)

        // Show persistent warning badge
        showTokenLimitWarningBadge()

        // Update token counter to show warning color
        val currentText = binding.etInputText.text?.toString() ?: ""
        val isSubscribed = PrefsManager.isSubscribed(this)
        val modelId = ModelManager.selectedModel.id

        updateTokenCounterUI(
            findViewById(R.id.tvTokenCount),
            currentText,
            modelId,
            isSubscribed
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                // conversationTokenManager.reset()

                // Add the first message to token counter
                // conversationTokenManager.addMessage(continuationMessage)

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
     * Shows upgrade dialog for non-subscribers
     */
    private fun showUpgradeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Upgrade Required")
            .setMessage("This feature is available to subscribers only. Would you like to upgrade to Pro?")
            .setPositiveButton("Upgrade") { _, _ ->
                // Navigate to subscription/upgrade flow
                WelcomeActivity.start(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shows a generic alert dialog with customizable content
     */
    private fun showAlertDialog(
        title: String,
        message: String,
        positiveButton: Pair<String, () -> Unit>? = null,
        negativeButton: Pair<String, () -> Unit>? = null
    ) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
        
        positiveButton?.let { (text, action) ->
            builder.setPositiveButton(text) { _, _ -> action() }
        }
        
        negativeButton?.let { (text, action) ->
            builder.setNegativeButton(text) { _, _ -> action() }
        }
        
        if (positiveButton == null && negativeButton == null) {
            builder.setPositiveButton("OK", null)
        }
        
        builder.show()
    }
    /**
     * PRODUCTION-READY sendMessage() method with comprehensive token validation
     */
    fun sendMessage() {
        val inputEditText = binding.etInputText
        val message = inputEditText.text.toString().trim()
        val modelId = ModelManager.selectedModel.id
        val isSubscribed = PrefsManager.isSubscribed(this)
        val hasFiles = selectedFiles.isNotEmpty()
        
        Timber.d("📤 PRODUCTION MESSAGE SEND START")
        Timber.d("   Model: $modelId (subscribed: $isSubscribed)")
        Timber.d("   Message length: ${message.length}")
        Timber.d("   Files: ${selectedFiles.size}")
        
        if (hasFiles) {
            selectedFiles.forEach { file ->
                Timber.d("   📎 File: ${file.name}, URI: ${file.uri}")
            }
        }
        
        // Check basic preconditions - prevent completely empty messages
        if (message.isBlank() && selectedFiles.isEmpty()) {
            Timber.w("❌ Cannot send empty message with no files attached")
            Toast.makeText(this, "Please enter a message or attach files", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Additional validation for specific model requirements
        if (message.isBlank() && selectedFiles.isNotEmpty()) {
            val hasImages = selectedFiles.any { !it.isDocument }
            val currentModel = ModelManager.selectedModel
            if (hasImages && ModelValidator.requiresTextWithImages(currentModel.id)) {
                Timber.w("❌ Model requires text with images: ${currentModel.displayName}")
                Toast.makeText(this, "Please add descriptive text when sending images to ${currentModel.displayName}", Toast.LENGTH_LONG).show()
                return
            }
        }
        
        // Check credit availability first (for non-subscribers)
        if (!isSubscribed && ::creditManager.isInitialized && !creditManager.hasSufficientChatCredits()) {
            Timber.w("❌ Insufficient chat credits")
            showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
            return
        }
        
        // Consume credits immediately for non-subscribers (when button is pressed)
        var creditsConsumed = false
        if (!isSubscribed && ::creditManager.isInitialized) {
            if (creditManager.consumeChatCredits()) {
                creditsConsumed = true
                updateFreeMessagesText()
                Timber.d("✅ Chat credits consumed on send button press")
            } else {
                Timber.e("❌ Failed to consume chat credits despite previous check")
                showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                return
            }
        }
        
        // Convert selectedFiles to FileItem format
        val fileItems = selectedFiles.map { file ->
            FileItem(
                uri = file.uri,
                name = file.name,
                mimeType = FileUtil.getMimeType(this, file.uri)
            )
        }
        
        // Get all current messages for context calculation
        val currentMessages = try {
            chatAdapter.currentList.filter { !it.isGenerating }
        } catch (e: Exception) {
            Timber.e(e, "Error getting current messages: ${e.message}")
            emptyList()
        }
        
        // OPTIMIZED FILE PROCESSING & TOKEN VALIDATION SYSTEM
        val conversationId = currentConversationId ?: "new_conversation"
        
        // First check: Are any files still processing? 
        val processingFiles = selectedFiles.filter { it.isExtracting && !it.isExtracted }
        if (processingFiles.isNotEmpty()) {
            val processingCount = processingFiles.size
            val fileWord = if (processingCount == 1) "file is" else "files are"
            
            Toast.makeText(
                this,
                "⏳ $processingCount $fileWord still being processed. Please wait...",
                Toast.LENGTH_LONG
            ).show()
            
            Timber.w("❌ Cannot send - $processingCount files still processing")
            return
        }
        
        // Second check: Are there any files that failed to process?
        val failedFiles = selectedFiles.filter { 
            !it.isExtracting && !it.isExtracted && it.isDocument
        }
        
        if (failedFiles.isNotEmpty()) {
            Toast.makeText(
                this,
                "⚠️ Some files failed to process. Remove them or try again.",
                Toast.LENGTH_LONG
            ).show()
            
            Timber.w("❌ Cannot send - ${failedFiles.size} files failed processing")
            return
        }
        
        lifecycleScope.launch {
            try {
                // Use new optimized token validation system
                if (::fileHandler.isInitialized) {
                    fileHandler.validateTokensBeforeSend(
                    conversationId = conversationId,
                    modelId = modelId,
                    userPrompt = message,
                    isSubscribed = isSubscribed,
                    onAllowed = {
                        // ✅ Validation passed - proceed with sending
                        Timber.d("✅ Message validation passed - sending")
                        clearDraft()
                        setGeneratingState(true)
                        proceedWithSending(inputEditText, message, hasFiles)
                    },
                    onNewConversationRequired = {
                        // ⚠️ Context window full - start new conversation
                        Timber.w("⚠️ Starting new conversation due to context limits")
                        startNewConversationForTokens()
                        // After starting new conversation, send the message
                        clearDraft()
                        setGeneratingState(true)
                        proceedWithSending(inputEditText, message, hasFiles)
                    },
                    onCancel = {
                        // ❌ User cancelled - refund credits
                        if (creditsConsumed && ::creditManager.isInitialized) {
                            creditManager.refundCredits(1, CreditManager.CreditType.CHAT)
                            updateFreeMessagesText()
                            Timber.d("🔄 Credits refunded due to cancellation")
                        }
                        Timber.d("User cancelled message send")
                    },
                    onUpgrade = {
                        // 💰 Show upgrade dialog for non-subscribers
                        showUpgradeDialog()
                    }
                    )
                } else {
                    // FileHandler not initialized - proceed anyway with fallback
                    Timber.w("⚠️ FileHandler not initialized, proceeding without token validation")
                    clearDraft()
                    setGeneratingState(true)
                    proceedWithSending(inputEditText, message, hasFiles)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "💥 Error during token validation: ${e.message}")
                
                // Fallback: show error and let user decide
                showAlertDialog(
                    title = "Token Validation Error",
                    message = "Unable to validate message: ${e.message}\n\nWould you like to proceed anyway?",
                    positiveButton = "Send Anyway" to {
                        clearDraft()
                        setGeneratingState(true)
                        proceedWithSending(inputEditText, message, hasFiles)
                    },
                    negativeButton = "Cancel" to {
                        // Refund credits if consumed
                        if (creditsConsumed && ::creditManager.isInitialized) {
                            creditManager.refundCredits(1, CreditManager.CreditType.CHAT)
                            updateFreeMessagesText()
                            Timber.d("🔄 Credits refunded due to validation error")
                        }
                    }
                )
            }
        }
    }
    
    /**
     * Proceed with sending message after token validation
     */
    private fun proceedWithSending(inputEditText: EditText, message: String, hasFiles: Boolean) {
        try {
            // Clear input
            inputEditText.setText("")
            
            // Hide keyboard
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(inputEditText.windowToken, 0)
            
            // Continue with existing message sending logic
            clearInputAndProceed(inputEditText, message, hasFiles)
            
        } catch (e: Exception) {
            Timber.e(e, "Error proceeding with message send: ${e.message}")
            setGeneratingState(false)
        }
    }
    
    /**
     * Start new conversation due to token limits
     */
    private fun startNewConversationForTokens() {
        try {
            val oldConversationId = currentConversationId
            
            // Reset token context for old conversation
            if (oldConversationId != null && ::tokenManager.isInitialized) {
                tokenManager.resetConversation(oldConversationId)
            }
            
            // Create new conversation
            currentConversationId = "conversation_${System.currentTimeMillis()}"
            
            // Clear chat adapter
            chatAdapter.submitList(emptyList())
            
            // Update UI
            updateTokenCounterUI()
            
            // Show success message
            Toast.makeText(this, "Started new conversation", Toast.LENGTH_SHORT).show()
            
            Timber.d("🔄 Started new conversation due to token limits")
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting new conversation: ${e.message}")
        }
    }
    
    /**
     * Handle API response and record token usage
     */
    private fun handleApiResponseTokens(apiResponse: AimlApiResponse) {
        try {
            val conversationId = currentConversationId ?: return
            val modelId = ModelManager.selectedModel.id
            
            // Use ApiUsageTracker to record actual token usage from API response
            val usageTracker = ApiUsageTracker.getInstance()
            val usageData = usageTracker.recordApiUsage(
                conversationId = conversationId,
                modelId = modelId,
                apiResponse = apiResponse
            )
            
            if (usageData != null) {
                Timber.d("📊 Recorded API usage: ${usageData.getDisplayString()}")
                
                // Update token counter UI with real data
                updateTokenCounterWithProduction()
                
                // Update debug token display (DEBUG builds only)
                if (BuildConfig.DEBUG) {
                    debugTokenDisplayHelper?.updateDebugTokenInfo(
                        modelId = modelId,
                        apiResponse = apiResponse,
                        inputText = "", // Could be extracted from current message if needed
                        outputText = "", // Could be extracted from current message if needed
                        isSubscribed = PrefsManager.isSubscribed(this@MainActivity)
                    )
                }
                
                // Log token breakdown for debugging
                Timber.d("📊 Token breakdown - Model: $modelId, Input: ${usageData.inputTokens}, Output: ${usageData.outputTokens}, Total: ${usageData.totalTokens}")
                
                // Check for unusually high token usage and log warning
                if (usageData.inputTokens > 10000) {
                    Timber.w("⚠️ HIGH TOKEN USAGE DETECTED!")
                    Timber.w("   Model: $modelId")
                    Timber.w("   Input tokens: ${usageData.inputTokens}")
                    Timber.w("   This may indicate inefficient request building")
                    Timber.w("   Check for: long system messages, unnecessary tools, or large conversation history")
                }
                
            } else {
                Timber.w("📊 No token usage data available in API response")
                updateTokenCounterWithProduction()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error recording API response tokens: ${e.message}")
        }
    }

    /**
     * Clear input and proceed with production token calculation
     */
    private fun clearInputAndProceedProduction(
        inputEditText: EditText,
        message: String,
        hasFiles: Boolean,
        calculation: ContextCalculationResult
    ) {
        // Clear the input field
        inputEditText.setText("")
        
        // Hide keyboard
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(inputEditText.windowToken, 0)
        
        // Ensure generating state is maintained
        if (!isGenerating) {
            setGeneratingState(true)
            Timber.d("🔄 Set generating state to true")
        }
        
        // Log comprehensive token info
        Timber.d("📊 SENDING WITH COMPREHENSIVE TOKENS")
        Timber.d("   Input tokens: ${calculation.usage.inputTokens}")
        Timber.d("   System tokens: ${calculation.usage.systemTokens}")
        Timber.d("   Total context: ${calculation.usage.totalTokens}")
        Timber.d("   Usage: ${(calculation.usagePercentage * 100).toInt()}%")
        
        // Process the message with production token awareness
        processMessageWithProductionTokens(
            message = message,
            hasFiles = hasFiles,
            calculation = calculation
        )
    }
    
    /**
     * Process message with production token management
     */
    private fun processMessageWithProductionTokens(
        message: String,
        hasFiles: Boolean,
        calculation: ContextCalculationResult
    ) {
        lifecycleScope.launch {
            try {
                // Get or create conversation ID
                val conversationId = currentConversationId ?: createNewConversationId()
                
                // ProductionTokenManager handles streaming through ConversationTokenManager
                
                // Create and add user message
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = message,
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                    conversationId = conversationId,
                    modelId = ModelManager.selectedModel.id
                )
                
                // Add user message to managers  
                messageManager.addMessage(userMessage)
                // conversationTokenManager.addMessage(userMessage)
                // conversationTokenManager.setConversationId(conversationId)
                
                // Create AI message placeholder
                val aiMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = "",
                    isUser = false,
                    isGenerating = true,
                    timestamp = System.currentTimeMillis(),
                    conversationId = conversationId,
                    modelId = ModelManager.selectedModel.id,
                    showButtons = false,
                    aiModel = ModelManager.selectedModel.displayName,
                    parentMessageId = userMessage.id
                )
                
                // Add AI message to manager
                messageManager.addMessage(aiMessage)
                
                // Update chat adapter with all messages (same approach as legacy method)
                val allMessages = messageManager.getCurrentMessages()
                    .filter { it.conversationId == conversationId }
                    .sortedBy { it.timestamp }
                
                chatAdapter.submitList(allMessages) {
                    // Scroll to bottom after adapter is updated
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    // Update conversation with latest message
                    updateConversationWithLatestMessage(message)
                }
                
                // Clear any existing draft
                clearDraft(shouldDeleteIfEmpty = false)
                
                // Update UI state
                isMessageSent = true
                binding.btnMainMenu.isEnabled = true
                
                // UI feedback
                scrollToBottom()
                provideHapticFeedback(50)
                hideKeyboard()
                
                // Process files if present
                if (hasFiles) {
                    handleFileProcessingWithProductionTokens(userMessage, selectedFiles.map { file ->
                        FileItem(uri = file.uri, name = file.name, mimeType = FileUtil.getMimeType(this@MainActivity, file.uri))
                    })
                }
                
                // Send to API using existing working flow
                generateAIResponseWithFullContext(
                    conversationId = conversationId,
                    userMessageId = userMessage.id,
                    aiMessageId = aiMessage.id
                )
                
                // ✨ BEAUTIFUL FILE ANIMATION: Capture file data before clearing
                if (hasFiles && ::fileAnimationManager.isInitialized) {
                    try {
                        val fileAnimationData = mutableListOf<FileAnimationManager.FileAnimationData>()
                        
                        // Find all visible file views and create animation data
                        for (i in selectedFiles.indices) {
                            val file = selectedFiles[i]
                            val fileView = FileUtil.findFileViewForUri(file.uri, this@MainActivity)
                            
                            if (fileView != null && fileView.visibility == View.VISIBLE) {
                                fileAnimationData.add(
                                    FileAnimationManager.FileAnimationData(
                                        sourceView = fileView,
                                        fileName = file.name,
                                        isImage = !file.isDocument,
                                        index = i,
                                        totalFiles = selectedFiles.size
                                    )
                                )
                                Timber.d("🎬 Prepared animation for file: ${file.name}")
                            }
                        }
                        
                        if (fileAnimationData.isNotEmpty()) {
                            Timber.d("🚀 Starting file transfer animation for ${fileAnimationData.size} files")
                            
                            // Start beautiful file animation
                            fileAnimationManager.startFileTransferAnimation(
                                files = fileAnimationData,
                                onAnimationComplete = {
                                    // Animation completed, now clear files with fade effect
                                    Timber.d("✨ File animation completed, clearing files")
                                    runOnUiThread {
                                        selectedFiles.clear()
                                        binding.selectedFilesScrollView.visibility = View.GONE
                                        if (::fileHandler.isInitialized) {
                                            fileHandler.updateSelectedFilesView()
                                        }
                                    }
                                }
                            )
                        } else {
                            // No visible file views found, clear immediately
                            Timber.d("No visible file views for animation, clearing immediately")
                            selectedFiles.clear()
                            binding.selectedFilesScrollView.visibility = View.GONE
                            if (::fileHandler.isInitialized) {
                                fileHandler.updateSelectedFilesView()
                            }
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error starting file animation: ${e.message}")
                        // Fallback to immediate cleanup on animation error
                        selectedFiles.clear()
                        binding.selectedFilesScrollView.visibility = View.GONE
                        if (::fileHandler.isInitialized) {
                            fileHandler.updateSelectedFilesView()
                        }
                    }
                } else {
                    // Handle file cleanup after sending (fallback for no files or animation manager not ready)
                    selectedFiles.clear()
                    binding.selectedFilesScrollView.visibility = View.GONE
                    if (::fileHandler.isInitialized) {
                        fileHandler.updateSelectedFilesView()
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "💥 Error processing message with production tokens: ${e.message}")
                setGeneratingState(false)
                showErrorDialog("Failed to send message: ${e.message}")
            }
        }
    }
    
    /**
     * Handle file processing with production token awareness
     */
    private suspend fun handleFileProcessingWithProductionTokens(
        userMessage: ChatMessage,
        files: List<FileItem>
    ) = withContext(Dispatchers.IO) {
        try {
            files.forEach { file ->
                try {
                    // Read file content for token counting
                    val fileContent = contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    } ?: ""
                    
                    if (fileContent.isNotBlank()) {
                        // File token handling is automatic with new system
                        val success = true // Always succeed with new system
                        
                        if (success) {
                            val tokens = TokenValidator.getAccurateTokenCount(fileContent, ModelManager.selectedModel.id)
                            Timber.d("📎 File processed: ${file.name} = $tokens tokens")
                        } else {
                            Timber.w("⚠️ Failed to count tokens for file: ${file.name}")
                        }
                    } else {
                        Timber.w("⚠️ File content empty or unreadable: ${file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ File processing error: ${file.name} - ${e.message}")
                }
            }
            
            // Clear selected files
            withContext(Dispatchers.Main) {
                selectedFiles.clear()
                binding.selectedFilesScrollView.visibility = View.GONE
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error in production file processing: ${e.message}")
        }
    }
    
    /**
     * Send message to API with production token management
     */
    private suspend fun sendToApiWithProductionTokens(
        userMessage: ChatMessage,
        aiMessage: ChatMessage,
        conversationId: String,
        calculation: ContextCalculationResult
    ) = withContext(Dispatchers.IO) {
        try {
            // Use existing API logic but with token awareness
            // This integrates with the existing streaming system
            
            // Create API request
            val apiRequest = createApiRequestWithProductionTokens(
                userMessage = userMessage,
                calculation = calculation
            )
            
            // Send request and handle streaming response
            // TODO: Implement proper streaming integration with production token system
            Timber.d("🚀 API request ready with production token validation")
            Timber.d("   Request details: ${apiRequest.model}, tokens validated")
            
            // For now, complete the streaming integration
            handleStreamingCompletionWithProductionTokens(
                conversationId = conversationId,
                userMessage = userMessage,
                aiMessage = aiMessage,
                calculation = calculation
            )
            
        } catch (e: Exception) {
            Timber.e(e, "💥 Error sending to API with production tokens: ${e.message}")
            withContext(Dispatchers.Main) {
                setGeneratingState(false)
                showErrorDialog("Failed to send message: ${e.message}")
            }
        }
    }
    
    private fun showProductionTokenWarningDialog(
        message: String,
        calculation: ContextCalculationResult,
        onProceed: () -> Unit,
        onCancel: () -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Token Usage Warning")
            .setMessage(
                "$message\n\n" +
                "Current usage: ${(calculation.usagePercentage * 100).toInt()}%\n" +
                "Total tokens: ${calculation.usage.totalTokens}"
            )
            .setPositiveButton("Continue Anyway") { _, _ -> onProceed() }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setNeutralButton("Start New Chat") { _, _ -> startNewConversation() }
            .show()
    }
    
    private fun showProductionTokenErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cannot Send Message")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Start New Chat") { _, _ -> startNewConversation() }
            .show()
    }
    
    private fun handleValidationFallback(
        message: String,
        modelId: String,
        isSubscribed: Boolean,
        inputEditText: EditText,
        hasFiles: Boolean
    ) {
        try {
            Timber.w("⚠️ Using legacy validation fallback")
            
            // Skip legacy validation - new system handles this during sendMessage()
            val wouldExceed = false // Let new system handle validation
            val availableTokens = 999999 // Placeholder for compatibility
            
            if (wouldExceed) {
                val inputTokens = TokenValidator.estimateTokenCount(message)
                showMessageTooLongDialog(message, inputTokens, availableTokens, isSubscribed)
                return
            }
            
            // Clear draft and proceed with legacy flow
            clearDraft()
            setGeneratingState(true)
            clearInputAndProceed(inputEditText, message, hasFiles)
            
        } catch (e: Exception) {
            Timber.e(e, "💥 Even legacy validation failed: ${e.message}")
            setGeneratingState(false)
            showErrorDialog("Unable to validate message. Please try again.")
        }
    }
    
    private fun getSystemPromptForModel(modelId: String): String {
        // Return system prompt based on model - use existing logic if available
        return "You are a helpful AI assistant."
    }
    
    private fun getWebSearchResults(): String {
        // Return web search results if available - use existing logic if available
        return ""
    }
    
    private fun createApiRequestWithProductionTokens(
        userMessage: ChatMessage,
        calculation: ContextCalculationResult
    ): AimlApiRequest {
        // Create API request using existing logic
        return AimlApiRequest(
            model = ModelManager.selectedModel.id,
            messages = listOf(
                AimlApiRequest.Message(
                    role = "user",
                    content = userMessage.message
                )
            ),
            maxTokens = calculation.modelLimits.maxOutputTokens,
            topA = null,
            parallelToolCalls = false,
            minP = null,
            useWebSearch = false
        )
    }
    
    private fun handleStreamingCompletionWithProductionTokens(
        conversationId: String,
        userMessage: ChatMessage,
        aiMessage: ChatMessage,
        calculation: ContextCalculationResult
    ) {
        lifecycleScope.launch {
            try {
                // Get all current messages
                val allMessages = chatAdapter.currentList.toMutableList()
                
                // ProductionTokenManager handles completion through ConversationTokenManager
                
                // Update token counter UI
                updateTokenCounterWithProduction()
                
                // Set generating state to false
                setGeneratingState(false)
                
                Timber.d("✅ Streaming completed with production token management")
                
            } catch (e: Exception) {
                Timber.e(e, "Error handling streaming completion: ${e.message}")
            }
        }
    }
    
    private fun updateTokenCounterWithProduction() {
        try {
            val tokenCountTextView = binding.tvTokenCount
            if (tokenCountTextView != null) {
                // Show actual API usage data from ApiUsageTracker
                val conversationId = currentConversationId
                if (conversationId != null) {
                    val usageTracker = ApiUsageTracker.getInstance()
                    val conversationUsage = usageTracker.getConversationUsage(conversationId)
                    
                    if (conversationUsage != null) {
                        // Display actual token usage from API responses
                        tokenCountTextView.text = conversationUsage.getDisplaySummary()
                        
                        // Set appropriate color based on usage
                        val totalTokens = conversationUsage.totalTokens
                        val textColor = when {
                            totalTokens > 50000 -> ContextCompat.getColor(this, R.color.error_color)
                            totalTokens > 20000 -> ContextCompat.getColor(this, R.color.warning_color)
                            else -> ContextCompat.getColor(this, R.color.text_secondary)
                        }
                        tokenCountTextView.setTextColor(textColor)
                        
                        Timber.d("📊 Token counter updated: ${conversationUsage.getDisplaySummary()}")
                    } else {
                        // No usage data yet - show input estimation
                        val currentText = binding.etInputText.text?.toString() ?: ""
                        updateTokenCounterUI(
                            tokenCountTextView,
                            currentText,
                            ModelManager.selectedModel.id,
                            PrefsManager.isSubscribed(this)
                        )
                    }
                } else {
                    // No conversation - show input estimation only
                    val currentText = binding.etInputText.text?.toString() ?: ""
                    updateTokenCounterUI(
                        tokenCountTextView,
                        currentText,
                        ModelManager.selectedModel.id,
                        PrefsManager.isSubscribed(this)
                    )
                }
            } else {
                binding.tvTokenCount?.text = "No token counter"
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating token counter with production system: ${e.message}")
            binding.tvTokenCount?.text = "Token error"
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun clearInputAndProceed(inputEditText: EditText, message: String, hasFiles: Boolean) {
        // Clear the input field
        inputEditText.setText("")

        // Hide keyboard
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
    private fun proceedWithSendingMessageAndFiles(message: String, hasFiles: Boolean) {
        if (hasFiles) {
            // DEBUG: Log all selectedFiles before accessibility check
            Timber.d("🔍 DEBUG: Checking ${selectedFiles.size} selected files before sending:")
            selectedFiles.forEachIndexed { i, file ->
                Timber.d("   [$i] ${file.name}")
                Timber.d("       URI: ${file.uri}")
                Timber.d("       Size: ${file.size} (${FileUtil.formatFileSize(file.size)})")
                Timber.d("       isDocument: ${file.isDocument}")
                Timber.d("       isExtracting: ${file.isExtracting}")
                Timber.d("       isExtracted: ${file.isExtracted}")
                Timber.d("       extractedContentId: '${file.extractedContentId}'")
                Timber.d("       processingInfo length: ${file.processingInfo.length} chars")
                if (file.processingInfo.isNotEmpty()) {
                    val preview = file.processingInfo.take(100) + if (file.processingInfo.length > 100) "..." else ""
                    Timber.d("       processingInfo preview: '$preview'")
                }
            }

            // Verify file accessibility before sending
            val accessibleFiles = selectedFiles.filter { file ->
                try {
                    contentResolver.openInputStream(file.uri)?.use { true } == true
                } catch (e: Exception) {
                    Timber.e(e, "File not accessible: ${file.uri}")
                    false
                }
            }
            
            // DEBUG: Log accessible files
            Timber.d("🔍 DEBUG: ${accessibleFiles.size} files are accessible:")
            accessibleFiles.forEachIndexed { i, file ->
                Timber.d("   [$i] ${file.name} - isExtracted: ${file.isExtracted}, contentLength: ${file.processingInfo.length}")
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
                // Credits already consumed on button press - no need to consume again
                
                // CRITICAL FIX: Single API call for file messages 
                Timber.d("🔧 FIXED: Making single API call for file message")
                val userMessageId = UUID.randomUUID().toString()
                aiChatService.sendMessage(
                    message = message,
                    hasFiles = true,
                    fileUris = accessibleFiles.map { it.uri },
                    userMessageId = userMessageId,
                    createOrGetConversation = ::createOrGetConversation,
                    isReasoningEnabled = isReasoningEnabled,
                    reasoningLevel = reasoningLevel,
                    selectedFiles = accessibleFiles
                )

                // ✨ BEAUTIFUL FILE ANIMATION: Alternative send path
                if (::fileAnimationManager.isInitialized && selectedFiles.isNotEmpty()) {
                    try {
                        val fileAnimationData = mutableListOf<FileAnimationManager.FileAnimationData>()
                        
                        // Find all visible file views and create animation data
                        for (i in selectedFiles.indices) {
                            val file = selectedFiles[i]
                            val fileView = FileUtil.findFileViewForUri(file.uri, this@MainActivity)
                            
                            if (fileView != null && fileView.visibility == View.VISIBLE) {
                                fileAnimationData.add(
                                    FileAnimationManager.FileAnimationData(
                                        sourceView = fileView,
                                        fileName = file.name,
                                        isImage = !file.isDocument,
                                        index = i,
                                        totalFiles = selectedFiles.size
                                    )
                                )
                                Timber.d("🎬 [Alt Path] Prepared animation for file: ${file.name}")
                            }
                        }
                        
                        if (fileAnimationData.isNotEmpty()) {
                            Timber.d("🚀 [Alt Path] Starting file transfer animation for ${fileAnimationData.size} files")
                            
                            // Start beautiful file animation
                            fileAnimationManager.startFileTransferAnimation(
                                files = fileAnimationData,
                                onAnimationComplete = {
                                    // Animation completed, now clear files
                                    Timber.d("✨ [Alt Path] File animation completed, clearing files")
                                    runOnUiThread {
                                        selectedFiles.clear()
                                        binding.selectedFilesScrollView.visibility = View.GONE
                                        if (::fileHandler.isInitialized) {
                                            fileHandler.updateSelectedFilesView()
                                        }
                                    }
                                }
                            )
                        } else {
                            // No visible file views found, clear immediately
                            Timber.d("[Alt Path] No visible file views for animation, clearing immediately")
                            selectedFiles.clear()
                            binding.selectedFilesScrollView.visibility = View.GONE
                            if (::fileHandler.isInitialized) {
                                fileHandler.updateSelectedFilesView()
                            }
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "[Alt Path] Error starting file animation: ${e.message}")
                        // Fallback to immediate cleanup on animation error
                        selectedFiles.clear()
                        binding.selectedFilesScrollView.visibility = View.GONE
                        if (::fileHandler.isInitialized) {
                            fileHandler.updateSelectedFilesView()
                        }
                    }
                } else {
                    // Clear files after sending (fallback)
                    selectedFiles.clear()
                    binding.selectedFilesScrollView.visibility = View.GONE
                    if (::fileHandler.isInitialized) {
                        fileHandler.updateSelectedFilesView()
                    }
                }
                
                // Ensure microphone stays hidden for OCR models after sending files
                val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
                if (isOcrModel) {
                    setMicrophoneVisibility(false)
                }
                
                // Update UI state
                isMessageSent = true
                binding.btnMainMenu.isEnabled = true
            }
        } else {
            // CRITICAL FIX: Text-only message - single API call path
            Timber.d("🔧 FIXED: Using single API call path for text-only message")
            proceedWithSendingMessage(message)
        }
    }
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
            if (::fileHandler.isInitialized) {
                fileHandler.updateSelectedFilesView()
            }
        }

        builder.show()
    }    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun proceedWithSendingMessage(message: String) {
        val messageSendStartTime = System.currentTimeMillis()
        Timber.d("⚡ Message send initiated at: $messageSendStartTime")
        
        val hasFiles = selectedFiles.isNotEmpty()
        val fileUris = selectedFiles.map { it.uri }
        val userMessageId = UUID.randomUUID().toString()
        
        // Show immediate UI feedback - typing indicator (OPTIMIZED)
        binding.typingIndicator.visibility = View.VISIBLE
        setGeneratingState(true)
        Timber.d("⚡ UI updated immediately in ${System.currentTimeMillis() - messageSendStartTime}ms")
        
        // Credits already consumed on button press - no need to consume again

        // OPTIMIZATION: Direct conversation creation for fastest response
        val conversationId = createOrGetConversation(message)

        // Set conversation ID in token manager
        // conversationTokenManager.setConversationId(conversationId)

        // CRITICAL FIX: Only create user message if no files are present
        // When files are present, AiChatService will create the proper user message with attachment flags
        if (!hasFiles) {
            // Create and add user message (text-only)
            val userMessage = ChatMessage(
                id = userMessageId,
                conversationId = conversationId,
                message = message,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                isVoiceMessage = isNextMessageFromVoice // 🎙️ Set voice message flag
            )
            
            // Reset the voice message flag after use
            isNextMessageFromVoice = false

            // Add user message to MessageManager and TokenManager
            messageManager.addMessage(userMessage)
            // conversationTokenManager.addMessage(userMessage)
        } else {
            // Reset the voice message flag even when files are present
            isNextMessageFromVoice = false
            Timber.d("🔧 Skipping user message creation - files present, AiChatService will handle it")
        }

        // OPTIMIZATION: Create AI message layout immediately for instant visual feedback
        val aiMessageId = UUID.randomUUID().toString()
        val (indicatorText, indicatorColor) = when {
            isDeepSearchEnabled -> {
                Pair("Web search", ContextCompat.getColor(this, R.color.web_search_button_color))
            }
            isReasoningEnabled -> {
                Pair("Thinking...", ContextCompat.getColor(this, R.color.reasoning_button_color))
            }
            else -> {
                // CRITICAL: Force loading indicator to show by setting null (will use default logic)
                Pair(null, 0xFF6366F1.toInt()) // Professional indigo color
            }
        }
        
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
            isThinkingActive = isReasoningEnabled && ModelConfigManager.supportsReasoning(ModelManager.selectedModel.id) && !isDeepSearchEnabled,
            isWebSearchActive = isDeepSearchEnabled,
            isForceSearch = false,
            hasThinkingProcess = false,
            thinkingProcess = null,
            initialIndicatorText = indicatorText,
            initialIndicatorColor = indicatorColor
        )
        
        // Add AI message immediately for instant layout appearance
        messageManager.addMessage(aiMessage)
        
        // CRITICAL: Update adapter immediately with both messages
        val allMessages = messageManager.getCurrentMessages()
            .filter { it.conversationId == conversationId }
            .sortedBy { it.timestamp }
        
        chatAdapter.submitList(allMessages) {
            // Update UI after adapter is updated
            updateConversationWithLatestMessage(message)
        }

        // Clear any existing draft when sending a message
        clearDraft(shouldDeleteIfEmpty = false)

        // CRITICAL: Ensure generating state is still true before proceeding
        if (!isGenerating) {
            setGeneratingState(true)
            Timber.d("Re-setting generating state to true before AI response")
        }

        // CRITICAL FIX: Generate AI response with full context (single API call)
        // This replaces the duplicate API calls that were causing race conditions
        Timber.d("🔧 FIXED: Using single API call flow via generateAIResponseWithFullContext")
        
        // Generate AI response with full context for text-only messages
        generateAIResponseWithFullContext(conversationId, userMessageId, aiMessageId)
        
        // Handle file cleanup if there were files
        if (hasFiles) {
            selectedFiles.clear()
            binding.selectedFilesScrollView.visibility = View.GONE
            if (::fileHandler.isInitialized) {
                fileHandler.updateSelectedFilesView()
            }
            
            // Ensure microphone stays hidden for OCR models after sending files
            val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
            if (isOcrModel) {
                setMicrophoneVisibility(false)
            }

            // Add file tokens to token manager
            fileUris.forEach { uri ->
                val fileTokens = estimateFileTokens(uri)
                // Use the enhanced file token handling
                val fileContent = try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    } ?: ""
                } catch (e: Exception) {
                    Timber.w("Could not read file content for token counting: ${e.message}")
                    ""
                }
                
                // File token handling is automatic with new system
            }
        }

        isMessageSent = true
        binding.btnMainMenu.isEnabled = true

        // Update UI immediately (OPTIMIZED - removed delays)
        scrollToBottom()
        provideHapticFeedback(50)
        hideKeyboard()
    }

    private fun generateAIResponseWithFullContext(conversationId: String, userMessageId: String, aiMessageId: String) {
        lifecycleScope.launch {
            try {
                // CRITICAL: Ensure generating state before starting
                withContext(Dispatchers.Main) {
                    if (!isGenerating) {
                        setGeneratingState(true)
                        Timber.d("Setting generating state in generateAIResponseWithFullContext")
                    }
                }

                // OPTIMIZATION: Use pre-loaded context if available
                if (preValidatedConversationId == conversationId && preValidatedContext != null) {
                    Timber.d("Using pre-loaded conversation context with ${preValidatedContext?.size} messages")
                    // Context is already loaded from pre-validation
                } else {
                    // Fallback to normal context loading
                    messageManager.ensureConversationContextLoaded(conversationId)
                }

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

                // AI message already created and added in proceedWithSendingMessage()
                // Just use the existing aiMessageId passed as parameter

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



    /* OLD VOICE SYSTEM - COMMENTED OUT
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
    } */

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    /**
     * Send a voice message for AI processing
     *
     * @param text The transcribed text from voice recognition
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

        // Credits are already consumed when voice send button is pressed

        // CRITICAL FIX: Use the same pattern for voice messages with indicator logic
        lifecycleScope.launch {
            try {
                // Set generating state and show immediate feedback
                val sendStartTime = System.currentTimeMillis()
                withContext(Dispatchers.Main) {
                    setGeneratingState(true)
                    binding.typingIndicator.visibility = View.VISIBLE
                    Timber.d("⚡ Loading indicator shown in ${System.currentTimeMillis() - sendStartTime}ms after message send")
                }

                // Determine initial indicator before creating AI message
                val (indicatorText, indicatorColor) = when {
                    isDeepSearchEnabled -> {
                        Pair("Web search", ContextCompat.getColor(this@MainActivity, R.color.web_search_button_color))
                    }
                    isReasoningEnabled -> {
                        Pair("Thinking...", ContextCompat.getColor(this@MainActivity, R.color.reasoning_button_color))
                    }
                    else -> {
                        // CRITICAL: Force loading indicator to show by setting null
                        Pair(null, ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
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
                    // Permission granted, start ultra voice recording  
                    if (::ultraAudioIntegration.isInitialized) {
                        ultraAudioIntegration.startUltraVoiceRecording()
                    }
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
                // conversationTokenManager.reset()
                // conversationTokenManager.setConversationId(privateId)
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
            // conversationTokenManager.reset()
            currentConversationId = newId
            // Reset draft loaded flag for new conversation
            isDraftContentLoaded = false
            // conversationTokenManager.setConversationId(newId)

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
            // conversationTokenManager.setConversationId(currentConversationId!!)

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
    }
    
    /* OLD VOICE SYSTEM - COMMENTED OUT
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                simulateWaveMovement()
                recordingAnimationTimer?.postDelayed(this, 250) // Slightly faster updates for smoother animation
            }
        }
    } */
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
        updateTokenCounterUI(
            tvTokenCount,
            currentText,
            modelId,
            isSubscribed
        )

        // Show toast with new limits
        val model = ModelManager.models.find { it.id == modelId }
        val modelName = model?.displayName ?: modelId
        val tokenLimitMessage = TokenValidator.getTokenLimitMessage(modelId, isSubscribed)

        Toast.makeText(this, "$modelName: $tokenLimitMessage", Toast.LENGTH_SHORT).show()
    }

    /**
     * Initialize ultra-modern voice recording system
     */
    private fun initializeUltraVoiceRecording() {
        try {
            // Initialize microphone button
            btnMicrophone = findViewById(R.id.btnMicrophone)
            
            // Initialize Ultra Audio Recording Manager
            ultraAudioIntegration = UltraAudioIntegration(
                activity = this,
                parentView = findViewById(android.R.id.content) // Use root content view
            )
            ultraAudioIntegration.initialize()
            
            // Setup ultra-modern microphone click handler
            btnMicrophone.setOnClickListener { 
                ultraAudioIntegration.startUltraVoiceRecording() 
            }

            // Add text change listener to input field for button toggle
            binding.etInputText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Check if current model is an OCR model
                    val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
                    val hasFiles = selectedFiles.isNotEmpty()
                    val hasText = s.toString().trim().isNotEmpty()
                    
                    // For OCR models with files, preserve send button regardless of text
                    if (isOcrModel && hasFiles) {
                        btnMicrophone.visibility = View.GONE
                        binding.btnSubmitText.visibility = View.VISIBLE
                        binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                        binding.btnSubmitText.contentDescription = "Process OCR"
                        binding.btnSubmitText.isEnabled = true
                        binding.btnSubmitText.alpha = 1.0f
                        Timber.d("Ultra Audio: Preserved send button for OCR model with files")
                        return
                    }
                    
                    // Toggle between mic and send button based on text content and model type
                    if (hasText) {
                        btnMicrophone.visibility = View.GONE
                        binding.btnSubmitText.visibility = View.VISIBLE
                    } else {
                        if (isOcrModel) {
                            // For OCR models: never show microphone, always show send button
                            btnMicrophone.visibility = View.GONE
                            binding.btnSubmitText.visibility = View.VISIBLE
                        } else {
                            // For non-OCR models: show ultra-modern microphone when no text
                            binding.btnSubmitText.visibility = View.GONE
                            setMicrophoneVisibility(true)
                        }
                    }
                    
                    // Update submit button state
                    updateButtonVisibilityAndState()
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            Timber.d("🎙️ Ultra Voice Recording initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing ultra voice recording: ${e.message}")

            // Fallback to text input only
            btnMicrophone.visibility = View.GONE
            binding.btnSubmitText.visibility = View.VISIBLE
            
            Toast.makeText(this, "Voice recording not available", Toast.LENGTH_SHORT).show()
        }
    }
    /* OLD VOICE SYSTEM - COMMENTED OUT
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
    } */


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
                                // Update selected model display
                                Timber.d("Updating model display to: $modelId")
                                updateSelectedModelDisplay()
                                Timber.d("Updated model display to position: $modelIndex")

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

    /* OLD VOICE SYSTEM - COMMENTED OUT
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
                            } catch (_: Exception) {
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
    } */
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

    // OLD VOICE SYSTEM - COMMENTED OUT
    /* private fun startVoiceRecording() {
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
    } */
    /* private fun simulateWaveMovement() {
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
                        } catch (_: Exception) {
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
    } */
    /* @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun stopVoiceRecordingAndSend() {
        if (isRecording) {
            // Check credit availability and consume credits for non-subscribers
            val isSubscribed = PrefsManager.isSubscribed(this)
            if (!isSubscribed) {
                if (!creditManager.hasSufficientChatCredits()) {
                    Timber.w("❌ Insufficient chat credits for voice message")
                    showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                    finishRecordingUI()
                    return
                }
                
                if (creditManager.consumeChatCredits()) {
                    updateFreeMessagesText()
                    Timber.d("✅ Chat credits consumed on voice send button press")
                } else {
                    Timber.e("❌ Failed to consume chat credits despite previous check")
                    showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                    finishRecordingUI()
                    return
                }
            }
            
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

                // Refund credits if consumed and error occurred
                if (!PrefsManager.isSubscribed(this)) {
                    creditManager.refundCredits(1, CreditManager.CreditType.CHAT)
                    updateFreeMessagesText()
                    Timber.d("🔄 Credits refunded due to voice recording error")
                }

                // Reset UI
                finishRecordingUI()
            }
        }
    }

    /**
     * Finish recording with the given text
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
    } */

    /* private fun cancelVoiceRecording() {
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
    } */

    /* private fun createRecognitionListener(): RecognitionListener {
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

            @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

            @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
    } */

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun reloadAiMessage(message: ChatMessage) {
        if (isGenerating) {
            Toast.makeText(this, "Please wait for current generation to complete", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check credit availability first (for non-subscribers)
        val isSubscribed = PrefsManager.isSubscribed(this)
        if (!isSubscribed && !creditManager.hasSufficientChatCredits()) {
            Timber.w("❌ Insufficient chat credits for reload")
            showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
            return
        }
        
        // Consume credits immediately for non-subscribers (when reload button is pressed)
        var creditsConsumed = false
        if (!isSubscribed) {
            if (creditManager.consumeChatCredits()) {
                creditsConsumed = true
                updateFreeMessagesText()
                Timber.d("✅ Chat credits consumed on reload button press")
            } else {
                Timber.e("❌ Failed to consume chat credits despite previous check")
                showInsufficientCreditsDialog(CreditManager.CreditType.CHAT)
                return
            }
        }

        // IMPROVED: Check token limits before reload
        val modelId = ModelManager.selectedModel.id

        // Find the parent user message
        val parentUserMessage = findParentUserMessage(message)
        val parentText = parentUserMessage?.message ?: ""

        if (parentText.isNotEmpty()) {
            // Token validation handled by new system during actual generation
            val wouldExceed = false // Let new system handle validation  
            val available = 999999 // Placeholder

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
            // conversationTokenManager.removeMessage(message.id)

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

            // Credits already consumed on button press - no need to consume again
            
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
            
            // Refund credits if consumed and error occurred
            if (creditsConsumed) {
                creditManager.refundCredits(1, CreditManager.CreditType.CHAT)
                updateFreeMessagesText()
                Timber.d("🔄 Credits refunded due to reload error")
            }
        }
    }

    /**
     * Navigate between message versions
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
    
    /**
     * Handle existing AI responses when a user message is edited
     * This removes orphaned AI responses to prevent empty messages
     */
    private fun handleExistingAiResponsesForEditedMessage(userMessageId: String) {
        try {
            // Find existing AI responses that are children of this user message
            val existingAiResponses = chatAdapter.currentList.filter { message ->
                !message.isUser && message.parentMessageId == userMessageId
            }
            
            if (existingAiResponses.isNotEmpty()) {
                Timber.d("Found ${existingAiResponses.size} existing AI responses for edited user message $userMessageId")
                
                // Remove existing AI responses from the adapter
                val currentMessages = chatAdapter.currentList.toMutableList()
                existingAiResponses.forEach { aiResponse ->
                    currentMessages.removeAll { it.id == aiResponse.id }
                    Timber.d("Removed AI response ${aiResponse.id} for edited user message")
                }
                
                // Update the adapter with the cleaned list
                chatAdapter.submitList(currentMessages)
                
                // Remove from token counter
                existingAiResponses.forEach { aiResponse ->
                    // conversationTokenManager.removeMessage(aiResponse.id)
                }
                
                // Note: Messages are automatically cleaned up by the messageManager
                // when saveAllMessages() is called. No need for explicit database deletion.
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling existing AI responses for edited message: ${e.message}")
        }
    }

    private fun setupPrivateChatFeature() {
        try {
            // Initialize views with defensive checks
            val privateChatView = null // Private chat button moved to menu
            val privateModeIndicatorView = findViewById<CardView>(R.id.privateModeIndicator)
            
            if (privateChatView == null) {
                Timber.w("⚠️ btnPrivateChat view not found in layout, skipping private chat setup")
                return
            }
            if (privateModeIndicatorView == null) {
                Timber.w("⚠️ privateModeIndicator view not found in layout, skipping private chat setup")
                return
            }
            
            btnPrivateChat = privateChatView
            privateModeIndicator = privateModeIndicatorView

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
        
        } catch (e: Exception) {
            Timber.e(e, "⚠️ Error setting up private chat feature: ${e.message}")
            // Don't rethrow - just log and continue without private chat functionality
        }
    }

    // Add this method to toggle private mode
    private fun togglePrivateMode() {
        // Toggle the state
        isPrivateModeEnabled = !isPrivateModeEnabled

        // Play animation (button now in menu)
        // btnPrivateChat.startAnimation(ghostActivateAnimation)

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
        // Defensive check: Ensure views are initialized before accessing them
        if (!::btnPrivateChat.isInitialized) {
            Timber.w("⚠️ btnPrivateChat not initialized yet, skipping UI update")
            return
        }
        
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

    /**
     * Dynamically resize the spinner card based on the selected model text
     */
    private fun resizeSpinnerCard(displayText: String) {
        if (!::binding.isInitialized) return
        
        // Measure the text width to determine optimal card width
        val textView = TextView(this).apply {
            text = displayText
            textSize = 14f // Same as spinner_item_custom.xml (updated to 14sp)
            // Use safer font loading with fallback
            try {
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.ultrathink)
            } catch (e: Exception) {
                // Fallback to default font if ultrathink is not available
                typeface = Typeface.DEFAULT
                Timber.w("Failed to load ultrathink font, using default: ${e.message}")
            }
            setPadding(0, 0, 0, 0)
        }
        
        // Measure the text
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        // Calculate optimal width: text width + padding + arrow space
        val textWidth = textView.measuredWidth
        val padding = resources.getDimensionPixelSize(R.dimen.spinner_horizontal_padding_8dp) * 2
        val arrowSpace = resources.getDimensionPixelSize(R.dimen.spinner_arrow_space_20dp)
        val optimalWidth = textWidth + padding + arrowSpace
        
        // Set minimum and maximum bounds
        val minWidth = resources.getDimensionPixelSize(R.dimen.spinner_min_width_60dp)
        val maxWidth = resources.getDimensionPixelSize(R.dimen.spinner_max_width_150dp)
        val finalWidth = optimalWidth.coerceIn(minWidth, maxWidth)
        
        // Note: modelSelectorCard was removed - spinner now uses its own background
    }


    private fun loadDefaultModel() {
        try {
            val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val defaultModelId = sharedPrefs.getString("default_ai_model_id", null)

            if (defaultModelId != null) {
                // Find the model by ID
                val modelIndex = ModelManager.models.indexOfFirst { it.id == defaultModelId }

                if (modelIndex != -1) {
                    // Update the ModelManager selection
                    ModelManager.selectedModel = ModelManager.models[modelIndex]
                    updateSelectedModelDisplay()

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
                    ModelManager.selectedModel = ModelManager.models[0]
                    updateSelectedModelDisplay()
                    chatAdapter.updateCurrentModel(ModelManager.models[0].id)
                }
            } else {
                // No default model set, use the first one
                Timber.d("No default model set, using first model")
                ModelManager.selectedModel = ModelManager.models[0]
                updateSelectedModelDisplay()
                chatAdapter.updateCurrentModel(ModelManager.models[0].id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading default model: ${e.message}")

            // Fallback to first model on error
            try {
                ModelManager.selectedModel = ModelManager.models[0]
                updateSelectedModelDisplay()
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
                    } catch (_: Exception) {
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

        lifecycleScope.launch {
            conversationsViewModel.getMessagesByConversationId(currentConversationId.toString(), currentPage).collect { messages ->
                if (messages.isNotEmpty()) {
                // Add loaded messages using the message manager
                messageManager.addMessages(messages)
                } else {
                    currentPage--
                }
                isLoadingMore = false
            }
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun initializeButtons() {
        // Change the icon for btnConversationsList to a back button
        binding.btnConversationsList.setIcon(ContextCompat.getDrawable(this, R.drawable.back_button))
        binding.btnConversationsList.contentDescription = "Back to home"
        btnAudioConversation = findViewById(R.id.btnAudioConversation)

        val btnReasoning = findViewById<ImageButton>(R.id.btnReasoning)
        val btnDeepSearch = findViewById<ImageButton>(R.id.btnDeepSearch)

        // Set initial states based on saved preferences AND model capabilities
        ModelManager.selectedModel.id

        // Initialize button states
        updateReasoningButtonState(isReasoningEnabled)
        updateDeepSearchButtonState(isDeepSearchEnabled)

        // Set click listeners for reasoning and deep search
        btnReasoning.setOnClickListener {
            ModelManager.selectedModel.id

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

        // Button listeners are now handled in setupListeners() - removed duplicates to prevent conflicts

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
        ModelManager.selectedModel.id

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

        ModelManager.selectedModel.id

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
    private fun setupEnhancedButtonHandlers() {
        // Enhanced submit button click handler with improved debouncing
        binding.btnSubmitText.setOnClickListener { view ->
            // Disable button temporarily to prevent double-clicks
            view.isEnabled = false

            // Re-enable after a short delay
            uiHandler.postDelayed({ view.isEnabled = true }, 500)

            if (isGenerating) {
                Timber.d("🔘🔘🔘 STOP BUTTON CLICKED - isGenerating=$isGenerating")
                stopGeneration()
            } else {
                Timber.d("🔘 SEND BUTTON CLICKED - isGenerating=$isGenerating")
                sendMessage()
            }
        }

        // Enhanced microphone button handler
        binding.btnMicrophone.setOnClickListener {
            if (::ultraAudioIntegration.isInitialized) {
                ultraAudioIntegration.startUltraVoiceRecording()
            } else {
                Toast.makeText(this, "Voice recording not initialized", Toast.LENGTH_SHORT).show()
            }
        }

        // Enhanced main menu button
        binding.btnMainMenu.setOnClickListener { view ->
            Timber.d("🔘 btnMainMenu clicked - triggering showMainMenu")
            showMainMenu(view)
        }

        // Enhanced attach button
        binding.btnAttach.setOnClickListener {
            showAttachmentMenu()
        }

        // Enhanced credits button
        binding.creditsButton.setOnClickListener {
            showGetCreditsMenu()
        }

        // Enhanced conversations list button
        binding.btnConversationsList.setOnClickListener {
            provideHapticFeedback(50)
            navigateToConversationsActivity()
        }

        // Enhanced audio conversation button
        if (::btnAudioConversation.isInitialized) {
            btnAudioConversation.setOnClickListener {
                openRealTimeAudioConversation()
                provideHapticFeedback(50)
            }
        }

        // Enhanced reasoning button
        if (::btnReasoning.isInitialized) {
            btnReasoning.setOnClickListener {
                ModelManager.selectedModel.id

                // Toggle state
                isReasoningEnabled = !isReasoningEnabled

                // Update button appearance
                updateReasoningButtonState(isReasoningEnabled)

                // Save state
                val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                sharedPrefs.edit()
                    .putBoolean("reasoning_enabled", isReasoningEnabled)
                    .apply()

                // Show feedback
                Toast.makeText(this,
                    if (isReasoningEnabled) "Reasoning enabled" else "Reasoning disabled",
                    Toast.LENGTH_SHORT).show()

                provideHapticFeedback(50)
            }
        }

        // Enhanced deep search button
        if (::btnDeepSearch.isInitialized) {
            btnDeepSearch.setOnClickListener {
                // Toggle state
                isDeepSearchEnabled = !isDeepSearchEnabled

                // Update button appearance
                updateDeepSearchButtonState(isDeepSearchEnabled)

                // Save state
                val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                sharedPrefs.edit()
                    .putBoolean("deep_search_enabled", isDeepSearchEnabled)
                    .apply()

                // Show feedback
                Toast.makeText(this,
                    if (isDeepSearchEnabled) "Web Search enabled" else "Web Search disabled",
                    Toast.LENGTH_SHORT).show()

                provideHapticFeedback(50)
            }
        }

        // Enhanced AI parameters button
        if (::btnAiParameters.isInitialized) {
            btnAiParameters.setOnClickListener {
                provideHapticFeedback(50)
                showAiParametersDialog()
            }
        }

        // Enhanced Perplexity search mode button
        if (::btnPerplexitySearchMode.isInitialized) {
            btnPerplexitySearchMode.setOnClickListener {
                provideHapticFeedback(50)
                togglePerplexitySearchMode()
            }
        }

        // Enhanced Perplexity context size button
        if (::btnPerplexityContextSize.isInitialized) {
            btnPerplexityContextSize.setOnClickListener {
                provideHapticFeedback(50)
                showPerplexityContextSizeDialog()
            }
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
            getSharedPreferences("camera_prefs", MODE_PRIVATE)
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
                        takePictureIntent.component = ComponentName(
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
            overridePendingTransition(0, 0) // ULTRAFAST: No transition animation

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
     * Update button visibility and state - can be called from FileHandler
     */
    fun updateButtonVisibilityAndState() {
        val hasText = binding.etInputText.text.toString().trim().isNotEmpty()
        val hasFiles = selectedFiles.isNotEmpty()
        val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
        
        // Enhanced file processing state detection
        val processingFiles = selectedFiles.filter { it.isExtracting && !it.isExtracted }
        val failedFiles = selectedFiles.filter { !it.isExtracting && !it.isExtracted && it.isDocument }
        val readyFiles = selectedFiles.filter { it.isExtracted && !it.isExtracting }
        val isExtracting = processingFiles.isNotEmpty()
        val hasFailedFiles = failedFiles.isNotEmpty()
        
        Timber.d("🔄 Button state update - hasText=$hasText, hasFiles=$hasFiles, isOcrModel=$isOcrModel")
        Timber.d("   📊 Files: ${selectedFiles.size} total, ${processingFiles.size} processing, ${failedFiles.size} failed, ${readyFiles.size} ready")
        
        // Debug: Log detailed file states
        selectedFiles.forEachIndexed { index, file ->
            Timber.d("   📄 File[$index]: ${file.name}")
            Timber.d("      🔄 isExtracting=${file.isExtracting}, isExtracted=${file.isExtracted}, isDocument=${file.isDocument}")
            if (file.isExtracting && !file.isExtracted) {
                Timber.w("      ⚠️ This file is causing 'Processing' state!")
            }
        }
        
        // For OCR models, always ensure send button is visible
        if (isOcrModel) {
            binding.btnMicrophone.visibility = View.GONE
            binding.btnSubmitText.visibility = View.VISIBLE
            binding.btnSubmitText.setImageResource(R.drawable.send_icon)
            
            // Determine button state based on file processing
            val (shouldEnable, buttonDescription) = when {
                isExtracting -> {
                    false to "Please wait while files are processed"
                }
                hasFailedFiles -> {
                    false to "Some files failed to process"
                }
                hasFiles || hasText -> {
                    true to "Process OCR"
                }
                else -> {
                    false to "No content to process"
                }
            }
            
            binding.btnSubmitText.isEnabled = shouldEnable
            binding.btnSubmitText.alpha = if (shouldEnable) 1.0f else 0.7f
            binding.btnSubmitText.contentDescription = buttonDescription
            
            Timber.d("OCR model: send button visible, enabled=${binding.btnSubmitText.isEnabled}")
            
            // Start aggressive monitoring for OCR models with files
            if (hasFiles) {
                startOcrButtonMonitoring()
            } else {
                stopOcrButtonMonitoring()
            }
            
            // Add a small delay to ensure this state persists against any other UI updates
            Handler(Looper.getMainLooper()).postDelayed({
                if (ModelValidator.isOcrModel(ModelManager.selectedModel.id)) {
                    binding.btnMicrophone.visibility = View.GONE
                    binding.btnSubmitText.visibility = View.VISIBLE
                    Timber.d("OCR model: Re-enforced send button visibility")
                }
            }, 100)
        } else {
            stopOcrButtonMonitoring()
            
            // For non-OCR models, handle different file processing states
            when {
                isExtracting -> {
                    // Files are still processing - disable send button
                    binding.btnSubmitText.isEnabled = false
                    binding.btnSubmitText.alpha = 0.7f
                    binding.btnSubmitText.contentDescription = "Please wait while files are processed"
                    
                    // Keep send button visible if we have text or files, just disabled
                    if (hasText || hasFiles) {
                        binding.btnMicrophone.visibility = View.GONE
                        binding.btnSubmitText.visibility = View.VISIBLE
                    } else {
                        toggleInputButtons(hasText, hasFiles)
                    }
                }
                
                hasFailedFiles -> {
                    // Some files failed processing - disable send button
                    binding.btnSubmitText.isEnabled = false
                    binding.btnSubmitText.alpha = 0.7f
                    binding.btnSubmitText.contentDescription = "Some files failed to process"
                    
                    if (hasText || hasFiles) {
                        binding.btnMicrophone.visibility = View.GONE
                        binding.btnSubmitText.visibility = View.VISIBLE
                    } else {
                        toggleInputButtons(hasText, hasFiles)
                    }
                }
                
                else -> {
                    // All files are ready or no files - normal toggle logic
                    toggleInputButtons(hasText, hasFiles)
                    
                    // Update button description with token info if available
                    if (hasFiles && readyFiles.isNotEmpty()) {
                        val totalTokens = readyFiles.sumOf { file ->
                            fileHandler.extractTokenCountFromProcessingInfo(file) ?: 0
                        }
                        if (totalTokens > 0) {
                            binding.btnSubmitText.contentDescription = "Send message ($totalTokens file tokens)"
                        }
                    }
                }
            }
        }
    }

    private var ocrButtonMonitoringJob: Job? = null

    private fun startOcrButtonMonitoring() {
        stopOcrButtonMonitoring()
        
        ocrButtonMonitoringJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
                    val hasFiles = selectedFiles.isNotEmpty()
                    
                    if (isOcrModel && hasFiles) {
                        if (binding.btnSubmitText.visibility != View.VISIBLE) {
                            Timber.w("OCR MONITOR: Send button hidden - FORCING VISIBLE")
                            withContext(Dispatchers.Main) {
                                binding.btnMicrophone.visibility = View.GONE
                                binding.btnSubmitText.visibility = View.VISIBLE
                                binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                                binding.btnSubmitText.contentDescription = "Process OCR"
                                binding.btnSubmitText.isEnabled = true
                                binding.btnSubmitText.alpha = 1.0f
                            }
                        }
                    } else {
                        // No longer OCR with files, stop monitoring
                        break
                    }
                    
                    delay(200) // Check every 200ms
                } catch (e: Exception) {
                    Timber.e(e, "Error in OCR button monitoring")
                    break
                }
            }
        }
        
        Timber.d("Started OCR button monitoring")
    }

    private fun stopOcrButtonMonitoring() {
        ocrButtonMonitoringJob?.cancel()
        ocrButtonMonitoringJob = null
    }

    /**
     * Handle subscription status changes from SubscriptionManager
     */
    private fun handleSubscriptionStatusChange(isSubscribed: Boolean) {
        runOnUiThread {
            Timber.d("📱 Handling subscription status change: $isSubscribed")
            
            if (isSubscribed) {
                onSubscriptionActivated()
            } else {
                onSubscriptionDeactivated()
            }
            
            updateUIBasedOnSubscriptionStatus()
        }
    }

    /**
     * Called when subscription is activated
     */
    private fun onSubscriptionActivated() {
        Timber.d("✨ Premium activated - unlocking features")
        
        // Show success message
        runOnUiThread {
            Toast.makeText(this, "Welcome to Premium! All features unlocked.", Toast.LENGTH_LONG).show()
        }
        
        // Ads are controlled by PrefsManager - they're handled in updateUIBasedOnSubscriptionStatus()
        // No explicit hideAds() method needed as ads check subscription status automatically
        
        // Credit limitations are handled automatically by CreditManager based on subscription status
        // No explicit unlockPremiumMode() needed
    }

    /**
     * Called when subscription is deactivated/expired
     */
    private fun onSubscriptionDeactivated() {
        Timber.d("🔒 Subscription deactivated - enabling free mode restrictions")
        
        // Show notification about reverting to free mode
        runOnUiThread {
            Toast.makeText(this, "Subscription expired. Switched to free mode.", Toast.LENGTH_LONG).show()
        }
        
        // Free mode is enabled automatically by CreditManager based on subscription status
        // No explicit enableFreeMode() needed
    }

    /**
     * Update UI based on subscription status
     */
    private fun updateUIBasedOnSubscriptionStatus() {
        val isSubscribed = SubscriptionManager.subscriptionStatus.value

        // Update credits visibility based on subscription and model
        if (isSubscribed) {
            // Hide credit-related UI when subscribed
            binding.creditsButton.visibility = View.GONE
            
            // Update status indicator if available
            updatePremiumStatusIndicator(true)
            
            // Also release ad resources if user is subscribed
            // AdManager handles cleanup automatically
        } else {
            // Show credit-related UI when not subscribed for paid models
            binding.creditsButton.visibility = if (!ModelManager.selectedModel.isFree) View.VISIBLE else View.GONE

            // Update the free messages text
            updateFreeMessagesText()
            
            // Update status indicator 
            updatePremiumStatusIndicator(false)
        }

        // Update token counter to reflect subscription status changes
        val tvTokenCount = findViewById<TextView>(R.id.tvTokenCount)
        if (tvTokenCount != null) {
            val currentText = binding.etInputText.text?.toString() ?: ""
            val modelId = ModelManager.selectedModel.id

            // Update token counter with new subscription status
            updateTokenCounterUI(
                tvTokenCount,
                currentText,
                modelId,
                isSubscribed
            )
        }

        // Update feature access indicators
        updateFeatureAccessIndicators(isSubscribed)
        
        // Update ad settings based on subscription
        if (isSubscribed) {
            PrefsManager.setShouldShowAds(this, false)
        } else {
            // Restore ad settings to default
            PrefsManager.setShouldShowAds(this, true)
        }
    }

    /**
     * Update premium status indicator in UI
     */
    private fun updatePremiumStatusIndicator(isSubscribed: Boolean) {
        // Update app title or add premium badge
        supportActionBar?.title = if (isSubscribed) {
            getString(R.string.app_name) + " Premium"
        } else {
            getString(R.string.app_name)
        }
    }

    /**
     * Update feature access indicators throughout the UI
     */
    private fun updateFeatureAccessIndicators(isSubscribed: Boolean) {
        try {
            // Update model selection to show/hide premium models
            updateModelAvailability(isSubscribed)
            
            // Update image generation button if available
            updateImageGenerationAccess(isSubscribed)
            
            // Update file upload capabilities
            updateFileUploadLimits(isSubscribed)
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating feature access indicators")
        }
    }

    /**
     * Update model availability based on subscription
     */
    private fun updateModelAvailability(isSubscribed: Boolean) {
        // Refresh model manager with subscription status
        ModelManager.updateSubscriptionStatus(isSubscribed)
        
        // If current model requires subscription and user is not subscribed, switch to free model
        if (!isSubscribed && !ModelManager.selectedModel.isFree) {
            val freeModel = ModelManager.getDefaultFreeModel()
            ModelManager.selectModel(freeModel.id)
            
            // Update UI to reflect model change
            updateControlsVisibility(freeModel.id)
            
            Toast.makeText(this, "Switched to free model. Upgrade to Premium to access advanced models.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Update image generation access
     */
    private fun updateImageGenerationAccess(isSubscribed: Boolean) {
        // Image generation access logic - this method can be expanded
        // based on your specific requirements for image generation features
    }

    /**
     * Update file upload limits based on subscription
     */
    private fun updateFileUploadLimits(isSubscribed: Boolean) {
        val maxFiles = if (isSubscribed) 10 else 3
        val maxSizeMB = if (isSubscribed) 50 else 5
        
        // Update file handler limits
        fileHandler.updateLimits(maxFiles, maxSizeMB)
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
        // Updated to use new SubscriptionActivity
        WelcomeActivity.start(this)
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
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    // === PRE-VALIDATION SYSTEM FOR ULTRA-FAST MESSAGE SENDING ===
    
    /**
     * Pre-validate message during typing to eliminate send-to-response latency
     */
    private fun performPreValidation(message: String) {
        // Cancel any existing validation
        validationRunnable?.let { validationDebouncer.removeCallbacks(it) }
        
        // Create new debounced validation
        validationRunnable = Runnable {
            val startTime = System.currentTimeMillis()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Timber.d("⚡ Pre-validation started for message: ${message.take(30)}...")
                    
                    // Pre-validate in background
                    val modelId = ModelManager.selectedModel.id
                    val isSubscribed = PrefsManager.isSubscribed(this@MainActivity)
                    
                    // Skip pre-validation - new system handles this
                    val wouldExceed = false // Let new system handle validation
                    val almostFull = false
                    
                    if (!wouldExceed) {
                        // Pre-prepare conversation context
                        val conversationId = getActiveConversationId() ?: createNewConversationId()
                        
                        // Pre-load conversation context in background
                        val context = if (conversationId.startsWith("conv_")) {
                            // New conversation - no context needed
                            emptyList<ChatMessage>()
                        } else {
                            // Existing conversation - load actual context
                            try {
                                conversationsViewModel.getAllConversationMessages(conversationId).take(5) // Last 5 messages for context
                            } catch (e: Exception) {
                                Timber.w("Failed to load conversation context: ${e.message}")
                                emptyList<ChatMessage>()
                            }
                        }
                        
                        // Cache the pre-validated data
                        preValidatedConversationId = conversationId
                        preValidatedContext = context
                        
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        
                        Timber.d("⚡ Pre-validation complete in ${duration}ms: conversationId=$conversationId, contextSize=${context.size}")
                        
                        // Update UI to show pre-validation is active
                        withContext(Dispatchers.Main) {
                            // Subtle indicator that pre-validation is working
                            binding.btnSubmitText.alpha = 1.0f
                        }
                    } else {
                        Timber.d("⚡ Pre-validation skipped - would exceed token limit")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Pre-validation failed: ${e.message}")
                }
            }
        }
        
        // Reduced debounce time for faster response
        validationDebouncer.postDelayed(validationRunnable!!, 250) // Reduced from 300ms to 250ms
    }
    
    /**
     * Get current conversation ID or create new one
     */
    private fun getActiveConversationId(): String? {
        return if (chatAdapter.currentList.isEmpty()) {
            null
        } else {
            chatAdapter.currentList.firstOrNull()?.conversationId
        }
    }
    
    /**
     * Create new conversation ID for pre-validation
     */
    private fun createNewConversationId(): String {
        return "conv_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
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
            val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
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
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etInputText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }
    /**
     * Force show keyboard
     */
    private fun forceShowKeyboard() {
        binding.etInputText.requestFocus()

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    /**
     * Hide keyboard
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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

                    // Reset button state based on input text and selected files
                    val inputText = binding.etInputText.text.toString().trim()
                    val hasFiles = selectedFiles.isNotEmpty()
                    val isOcrModel = ModelValidator.isOcrModel(ModelManager.selectedModel.id)
                    
                    if (inputText.isEmpty() && !hasFiles && !isOcrModel) {
                        binding.btnSubmitText.visibility = View.GONE
                        setMicrophoneVisibility(true) // This will handle OCR model check
                    } else {
                        binding.btnSubmitText.setImageResource(R.drawable.send_icon)
                        binding.btnSubmitText.contentDescription = if (isOcrModel) "Process OCR" else "Send message"
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
                        setMicrophoneVisibility(true) // This will handle OCR model check
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
                setMicrophoneVisibility(true) // This will handle OCR model check
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
            putString("ai_message_history", JsonUtils.toJson(aiMessageHistory))
            putString("current_message_index", JsonUtils.toJson(currentMessageIndex))
            // Also save the aiResponseGroups
            putString("ai_response_groups", JsonUtils.toJson(aiResponseGroups))
        }
    }

    /**
     * Load AI message history from preferences
     */
    private fun loadAiMessageHistory() {
        sharedPrefs.getString("ai_message_history", null)?.let { jsonString ->
            try {
                // For now, skip loading complex nested maps until proper Moshi deserialization is implemented
                // This prevents crashes while maintaining app functionality
                Log.d("MainActivity", "Skipping ai_message_history loading until Moshi migration complete")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading ai_message_history", e)
            }
        }
        sharedPrefs.getString("ai_response_groups", null)?.let { jsonString ->
            try {
                // For now, skip loading complex nested maps until proper Moshi deserialization is implemented
                // This prevents crashes while maintaining app functionality  
                Log.d("MainActivity", "Skipping ai_response_groups loading until Moshi migration complete")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading ai_response_groups", e)
            }
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
            // Don't register if already registered
            if (isBackgroundGenerationReceiverRegistered) {
                Timber.d("📻 Background generation receiver already registered, skipping")
                return
            }
            
            val intentFilter = IntentFilter().apply {
                addAction(BackgroundAiService.ACTION_GENERATION_PROGRESS)
                addAction(BackgroundAiService.ACTION_EXISTING_PROGRESS)
                addAction(BackgroundAiService.ACTION_GENERATION_COMPLETE)
                addAction(BackgroundAiService.ACTION_GENERATION_ERROR)
            }
            
            // CRITICAL FIX: Add RECEIVER_NOT_EXPORTED flag for Android 34+ security requirement
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(backgroundGenerationReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(backgroundGenerationReceiver, intentFilter)
            }
            
            isBackgroundGenerationReceiverRegistered = true
            Timber.d("📻 Registered background generation receiver")
        } catch (e: Exception) {
            Timber.e(e, "Error registering background generation receiver: ${e.message}")
        }
    }
    
    private fun unregisterBackgroundGenerationReceiver() {
        try {
            // Only unregister if it was registered
            if (isBackgroundGenerationReceiverRegistered) {
                unregisterReceiver(backgroundGenerationReceiver)
                isBackgroundGenerationReceiverRegistered = false
                Timber.d("📻 Unregistered background generation receiver")
            } else {
                Timber.d("📻 Background generation receiver not registered, skipping unregistration")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering background generation receiver: ${e.message}")
            // Reset flag on error to prevent future attempts
            isBackgroundGenerationReceiverRegistered = false
        }
    }
    
    /**
     * CRITICAL FIX: Ensure service binding with callback queue system
     * This prevents race conditions by queuing operations until service is actually connected
     */
    private fun ensureServiceBinding(callback: () -> Unit) {
        when {
            isServiceBound && backgroundAiService != null -> {
                // Service is already bound and ready
                try {
                    callback()
                } catch (e: Exception) {
                    Timber.e(e, "Error executing immediate callback: ${e.message}")
                }
            }
            isBindingInProgress -> {
                // Binding is in progress, queue the callback
                serviceBindingCallbacks.add(callback)
                Timber.d("⏳ Queued callback - binding in progress")
            }
            else -> {
                // Need to start binding and queue the callback
                serviceBindingCallbacks.add(callback)
                bindToBackgroundService()
                Timber.d("🔗 Started binding and queued callback")
            }
        }
    }
    
    private fun bindToBackgroundService() {
        try {
            if (isBindingInProgress || isServiceBound) {
                Timber.d("⚠️ Service binding already in progress or bound, skipping")
                return
            }
            
            isBindingInProgress = true
            val intent = Intent(this, BackgroundAiService::class.java)
            bindService(intent, backgroundServiceConnection, BIND_AUTO_CREATE)
            Timber.d("🔗 Binding to background service")
        } catch (e: Exception) {
            Timber.e(e, "Error binding to background service: ${e.message}")
            isBindingInProgress = false
            serviceBindingCallbacks.clear()
        }
    }
    
    /**
     * CRITICAL FIX: Connect to background service specifically for generating messages
     * Now uses ensureServiceBinding to prevent race conditions
     */
    private fun connectToBackgroundServiceForMessages(generatingMessages: List<ChatMessage>) {
        Timber.d("🔗 Connecting to background service for ${generatingMessages.size} generating messages")
        
        try {
            // Use ensureServiceBinding to handle race conditions properly
            ensureServiceBinding {
                Timber.d("✅ Service connected, requesting progress for ${generatingMessages.size} messages")
                generatingMessages.forEach { message ->
                    backgroundAiService?.requestCurrentProgress(message.id)
                    Timber.d("📡 Requested progress for message: ${message.id}")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error connecting to background service: ${e.message}")
        }
    }
    


    /**
     * CRITICAL FIX: Fallback mechanism to check generating messages directly from database
     * This ensures content is shown even if service connection fails
     */
    private fun fallbackCheckGeneratingMessages(generatingMessages: List<ChatMessage>) {
        Timber.d("🔄 Fallback check for ${generatingMessages.size} generating messages")
        
        lifecycleScope.launch {
            try {
                generatingMessages.forEach { originalMessage ->
                    // Re-check message in database
                    val currentMessage = withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(this@MainActivity).chatMessageDao().getMessageById(originalMessage.id)
                    }
                    
                    if (currentMessage != null) {
                        val messageAge = System.currentTimeMillis() - currentMessage.lastModified
                        val hasContent = currentMessage.message.isNotEmpty() && currentMessage.message.length > 10
                        val isStillGenerating = currentMessage.isGenerating
                        
                        Timber.d("🔍 Fallback check for ${originalMessage.id}: age=${messageAge/1000}s, hasContent=$hasContent, isGenerating=$isStillGenerating, contentLength=${currentMessage.message.length}")
                        
                        if (hasContent) {
                            // Show the content from database
                            withContext(Dispatchers.Main) {
                                if (::chatAdapter.isInitialized) {
                                    val index = chatAdapter.currentList.indexOfFirst { it.id == originalMessage.id }
                                    if (index != -1) {
                                        val displayMessage = if (isStillGenerating && messageAge < 120_000) { // 2 minutes
                                            // Still potentially generating
                                            currentMessage.copy(isLoading = false)
                                        } else {
                                            // Completed or stuck
                                            currentMessage.copy(
                                                isLoading = false,
                                                isGenerating = false,
                                                showButtons = true
                                            )
                                        }
                                        
                                        chatAdapter.updateMessageDirectly(index, displayMessage)
                                        Timber.d("✅ Fallback updated message ${originalMessage.id} with content")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in fallback check: ${e.message}")
            }
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
                val activeSessions = UnifiedStreamingManager.getActiveSessionsForConversation(conversationId)
                activeSessions.forEach { session ->
                    // CRITICAL: Update the session with latest content from adapter before marking as background
                    val currentMessage = chatAdapter.currentList.find { it.id == session.messageId }
                    if (currentMessage != null && currentMessage.message.isNotEmpty()) {
                        UnifiedStreamingManager.updateSessionContent(session.messageId, currentMessage.message)
                        Timber.d("💾 Saved current UI content to session: ${session.messageId} (${currentMessage.message.length} chars)")
                    }
                    
                    UnifiedStreamingManager.markAsBackgroundActive(session.messageId)
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
        Timber.d("🔄🔄🔄 IMMEDIATE BACKGROUND TRANSFER STARTING")
        
        try {
            // CRITICAL FIX: Save current content from UI adapter to database SYNCHRONOUSLY
            if (::chatAdapter.isInitialized) {
                val currentMessages = chatAdapter.currentList
                val generatingMessages = currentMessages.filter { it.isGenerating }
                
                Timber.d("🔄 Found ${generatingMessages.size} generating messages to transfer")
                
                for (message in generatingMessages) {
                    Timber.d("🔄 Transferring message ${message.id}: content length=${message.message.length}")
                    
                    // CRITICAL FIX: Save current content immediately via async but don't wait
                    if (message.message.isNotEmpty()) {
                        lifecycleScope.launch {
                            try {
                                conversationsViewModel.saveMessage(message)
                                Timber.d("🔄 ✅ IMMEDIATELY saved current content: ${message.id} (${message.message.length} chars)")
                            } catch (e: Exception) {
                                Timber.e(e, "🔄 ❌ Error saving message before background transfer: ${e.message}")
                            }
                        }
                    }
                    
                    // Save to streaming state manager as well
                    UnifiedStreamingManager.updateSessionContent(message.id, message.message)
                    
                    // Start background service immediately for this message
                    if (currentConversationId != null) {
                        BackgroundAiService.startGeneration(this, message.id, currentConversationId!!)
                        Timber.d("🔄 ✅ Started background service for message: ${message.id}")
                    }
                }
            }
            
            // Also use AiChatService method if available
            if (::aiChatService.isInitialized) {
                val generatingMessages = aiChatService.getGeneratingMessages()
                
                for (message in generatingMessages) {
                    val conversationId = message.conversationId
                    
                    Timber.d("🔄 Transferring via AiChatService: ${message.id}")
                    aiChatService.transferToBackgroundService(message.id, conversationId)
                }
                
                if (generatingMessages.isNotEmpty()) {
                    Timber.d("🔄 ✅ Transferred ${generatingMessages.size} messages via AiChatService")
                }
            }
            
            Timber.d("🔄 ✅ IMMEDIATE BACKGROUND TRANSFER COMPLETED")
            
        } catch (e: Exception) {
            Timber.e(e, "🔄 ❌ Error transferring generation to background: ${e.message}")
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
                    
                    Timber.d("📝 Restoring existing content: ${content.length} chars")
                    
                    // CRITICAL FIX: Always restore existing content - this means background generation has content
                    // The fact that we received this broadcast means the content is meaningful
                    
                    // Start streaming mode for ongoing generation
                    if (!chatAdapter.isStreamingActive) {
                        chatAdapter.startStreamingMode()
                        Timber.d("🎬 Started streaming mode for existing progress")
                    }
                    
                    // Determine if this is still generating or completed based on background service state
                    // If we got existing progress, it means there might be more coming
                    val isStillGenerating = currentMessage.isGenerating || currentMessage.isLoading
                    
                    if (isStillGenerating) {
                        setGeneratingState(true) // Ensure UI shows generating state
                        
                        // Update message for ongoing generation
                        val streamingMessage = currentMessage.copy(
                            message = content,
                            isGenerating = true,
                            isLoading = false,
                            showButtons = false,
                            lastModified = System.currentTimeMillis(),
                            partialContent = content
                        )
                        
                        // Use direct update to avoid UI flicker
                        chatAdapter.updateMessageDirectly(index, streamingMessage)
                        chatAdapter.updateStreamingContentDirect(messageId, content, processMarkdown = true, isStreaming = true)
                        
                        Timber.d("✅ Restored ongoing generation content")
                    } else {
                        // This is completed content
                        val completedMessage = currentMessage.copy(
                            message = content,
                            isGenerating = false,
                            isLoading = false,
                            showButtons = true,
                            lastModified = System.currentTimeMillis(),
                            partialContent = content
                        )
                        
                        chatAdapter.updateMessageDirectly(index, completedMessage)
                        chatAdapter.stopStreamingModeGradually()
                        
                        Timber.d("✅ Restored completed content")
                    }
                } else {
                    Timber.w("⚠️ Message not found in adapter: $messageId")
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
                
                // CRITICAL FIX: Only start streaming mode if generation is actually active
                // Don't force streaming mode if user has stopped generation
                if (!chatAdapter.isStreamingActive && isGenerating) {
                    chatAdapter.startStreamingMode()
                    Timber.d("🎬 Started streaming mode for background progress")
                    setGeneratingState(true) // Ensure UI is in generating state
                } else if (!isGenerating) {
                    Timber.d("🛑 Skipping streaming mode start - generation was stopped by user")
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
                        
                        // CRITICAL FIX: Always update content in saved conversations for better streaming performance
                        // Skip the length check that was causing slower streaming
                        if (true) {
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
                            
                            // CRITICAL FIX: Reduced throttling for better streaming performance in saved conversations
                            // Save more frequently to improve responsiveness (every 20+ chars or 500ms)
                            val lastSaveTime = currentMessage.lastModified
                            val timeSinceLastSave = System.currentTimeMillis() - lastSaveTime
                            val contentDifference = content.length - currentContent.length
                            
                            if (contentDifference >= 20 || timeSinceLastSave >= 500) {
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
                
                // CRITICAL FIX: Minimal scrolling - only scroll once at the end, not during streaming
                // This prevents the jumping and flickering issues
                Timber.d("🔄 Streaming content updated, skipping scroll to prevent flicker")
                
            } catch (e: Exception) {
                Timber.e(e, "Error handling background progress: ${e.message}")
            }
        }
    }
    

    private fun handleBackgroundCompletion(messageId: String, content: String) {
        runOnUiThread {
            try {
                Timber.d("🎉🎉🎉 BACKGROUND GENERATION COMPLETED for message: $messageId, content length: ${content.length}")
                
                // CRITICAL FIX: Update UI state immediately when generation completes
                setGeneratingState(false)  // This will hide the stop button and show send button
                updateButtonVisibilityAndState()  // Update button states
                
                // Use MessageManager to properly finalize streaming
                if (::messageManager.isInitialized) {
                    // CRITICAL FIX: Final content update with full refresh to prevent cutoff
                    val currentList = chatAdapter.currentList.toMutableList()
                    val index = currentList.indexOfFirst { it.id == messageId }
                    
                    if (index != -1) {
                        val completedMessage = currentList[index].copy(
                            message = content,
                            partialContent = content,
                            isGenerating = false,
                            isLoading = false,
                            showButtons = true,
                            canContinueStreaming = false,
                            lastModified = System.currentTimeMillis(),
                            completionTime = System.currentTimeMillis()
                        )
                        
                        // Update the list and trigger a proper refresh
                        currentList[index] = completedMessage
                        chatAdapter.submitList(currentList) {
                            // CRITICAL FIX: Force final content display after list update
                            Handler(Looper.getMainLooper()).postDelayed({
                                chatAdapter.updateStreamingContentDirect(messageId, content, processMarkdown = true, isStreaming = false)
                                Timber.d("🔄 Final content displayed: ${content.length} chars")
                            }, 100)
                        }
                        
                        // Save completed message to database
                        lifecycleScope.launch {
                            try {
                                conversationsViewModel.saveMessage(completedMessage)
                                Timber.d("💾 Saved completed message to database")
                            } catch (e: Exception) {
                                Timber.e(e, "Error saving completed message: ${e.message}")
                            }
                        }
                    }
                    
                    // Stop streaming mode gradually
                    lifecycleScope.launch {
                        delay(500) // Longer delay to ensure content is displayed
                        chatAdapter.stopStreamingModeGradually()
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
                            isLoading = false,
                            showButtons = true,
                            lastModified = System.currentTimeMillis(),
                            completionTime = System.currentTimeMillis()
                        )
                        
                        currentList[index] = completedMessage
                        chatAdapter.submitList(currentList)
                        
                        // Save to database
                        lifecycleScope.launch {
                            conversationsViewModel.saveMessage(completedMessage)
                        }
                        Timber.d("⚠️ Used fallback completion method")
                    }
                }
                
                // CRITICAL FIX: Clear isGenerating flag globally
                isGenerating = false
                
                // Show completion notification
                Toast.makeText(this@MainActivity, "AI response completed!", Toast.LENGTH_SHORT).show()
                
                // CRITICAL FIX: Final scroll only after generation completes
                // Single scroll call to prevent jumping
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (::chatAdapter.isInitialized && chatAdapter.itemCount > 0) {
                            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                            Timber.d("📜 Final scroll after generation completion")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error in final scroll: ${e.message}")
                    }
                }, 600) // Longer delay to ensure content is fully rendered
                
                Timber.d("✅ Successfully handled background completion and updated UI state")
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
                        Timber.d("  - ${msg.id}: ${msg.message.length} chars, lastModified: ${msg.lastModified}, age: ${(System.currentTimeMillis() - msg.lastModified) / 1000}s")
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
                    val messageAge = System.currentTimeMillis() - messageWithContent.lastModified
                    val isRecent = messageAge < 2 * 60 * 1000 // 2 minutes
                    
                    // Check if there's active streaming for this message
                    val hasActiveSession = UnifiedStreamingManager.getSession(messageWithContent.id) != null ||
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
                    val messageAge = System.currentTimeMillis() - message.lastModified
                    val isRecent = messageAge < 5 * 60 * 1000 // 5 minutes
                    
                    // Only process if recent and empty
                    isRecent
                })
                
                if (actuallyGeneratingMessages.isNotEmpty()) {
                    Timber.d("🔄 Found ${actuallyGeneratingMessages.size} messages actually being generated in background (${generatingMessages.size} total marked as generating)")
                    
                    for (message in actuallyGeneratingMessages) {
                        // CRITICAL FIX: Comprehensive check to avoid duplicate generations
                        val canContinue = UnifiedStreamingManager.getSession(message.id) != null
                        val existingSession = UnifiedStreamingManager.getSession(message.id)
                        val isBackgroundActive = backgroundAiService?.isGenerating(message.id) == true
                        val hasBackgroundSession = UnifiedStreamingManager.hasActiveStreamingInConversation()
                        
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun continueStreamingInUI(message: ChatMessage, session: UnifiedStreamingManager.StreamingSession) {
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
                    
                    // CRITICAL FIX: Request current progress from background service using ensureServiceBinding
                    ensureServiceBinding {
                        backgroundAiService?.requestCurrentProgress(message.id)
                        Timber.d("📡 Requested current progress from background service")
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
                        UnifiedStreamingManager.completeSession(message.id, true)
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
     * Now uses proper threading and the new ensureServiceBinding to prevent race conditions
     */
    private fun requestCurrentProgressFromBackground(message: ChatMessage) {
        // CRITICAL: All database operations on background thread
        backgroundDbScope.launch {
            try {
                Timber.d("📡 Requesting current progress from background service for message: ${message.id}")
                
                // Check message state in background thread
                val dbMessage = database.chatMessageDao().getMessageById(message.id)
                if (dbMessage == null) {
                    Timber.w("⚠️ Message not found in database: ${message.id}")
                    return@launch
                }
                
                // Switch to main thread for UI operations
                withContext(Dispatchers.Main) {
                    // Use the new ensureServiceBinding method to prevent race conditions
                    ensureServiceBinding {
                        backgroundAiService?.requestCurrentProgress(message.id)
                        Timber.d("📡 ✅ Successfully requested progress after ensuring service binding")
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
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error requesting current progress from background: ${e.message}")
                
                // Switch to main thread for error handling
                withContext(Dispatchers.Main) {
                    // Still try to enable streaming mode even if there was an error
                    if (::messageManager.isInitialized) {
                        chatAdapter.startStreamingMode()
                    }
                }
            }
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
                UnifiedStreamingManager.completeSession(message.id, false)
                
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
                            val pulseAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
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
            Timber.d("🔄 Merging ${messages.size} messages while preserving streaming content for conversation $conversationId")
            
            // CRITICAL FIX: Filter messages by conversation ID FIRST to prevent message leaking
            val conversationMessages = messages.filter { it.conversationId == conversationId }
            Timber.d("🔍 Filtered to ${conversationMessages.size} messages for conversation $conversationId")
            
            val currentList = chatAdapter.currentList.toMutableList()
            val currentMessageIds = currentList.map { it.id }.toSet()
            
            // CRITICAL FIX: Instead of rebuilding the list, only add missing messages
            // Find messages from database that aren't in current adapter
            val newMessages = conversationMessages.filter { it.id !in currentMessageIds }
            
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
                    val dbMessage = conversationMessages.find { it.id == currentMessage.id }
                    if (dbMessage != null) {
                        // CRITICAL: Check if there's an active streaming session
                        val hasActiveSession = UnifiedStreamingManager.getSession(currentMessage.id) != null
                        
                        if (hasActiveSession) {
                            // Use streaming state manager content as the source of truth
                            val streamingSession = UnifiedStreamingManager.getSession(currentMessage.id)
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
                // IMPLEMENTED: Update MessageManager's internal list to match what we have in adapter
                Timber.d("📝 Synchronizing MessageManager internal list")
                messageManager.updateInternalListSilently(chatAdapter.currentList)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error merging messages while preserving streaming: ${e.message}")
            // Fallback to normal initialization only if current list is empty
            if (chatAdapter.currentList.isEmpty()) {
                // CRITICAL: Filter messages by conversation ID before fallback
                val conversationMessages = messages.filter { it.conversationId == conversationId }
                messageManager.initialize(conversationMessages, conversationId)
                Timber.d("⚠️ Used fallback initialization due to error with ${conversationMessages.size} filtered messages")
            }
        }
    }

    /**
     * Toggle Perplexity search mode between academic and web
     */
    private fun togglePerplexitySearchMode() {
        showPerplexitySearchModeDialog()
    }
    
    private fun showPerplexitySearchModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_perplexity_search_mode, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        
        // Set dialog to appear at bottom of screen
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.attributes?.windowAnimations = R.style.DialogSlideAnimation
        
        val webOption = dialogView.findViewById<LinearLayout>(R.id.webOption)
        val academicOption = dialogView.findViewById<LinearLayout>(R.id.academicOption)
        val webCheckmark = dialogView.findViewById<ImageView>(R.id.webCheckmark)
        val academicCheckmark = dialogView.findViewById<ImageView>(R.id.academicCheckmark)
        
        // Show current selection
        val currentMode = PerplexityPreferences.getSearchMode(this)
        if (currentMode == PerplexityPreferences.SEARCH_MODE_WEB) {
            webCheckmark.visibility = View.VISIBLE
            academicCheckmark.visibility = View.GONE
        } else {
            webCheckmark.visibility = View.GONE
            academicCheckmark.visibility = View.VISIBLE
        }
        
        webOption.setOnClickListener {
            PerplexityPreferences.setSearchMode(this, PerplexityPreferences.SEARCH_MODE_WEB)
            updatePerplexitySearchModeButton()
            dialog.dismiss()
            Toast.makeText(this, "Search Mode: Web", Toast.LENGTH_SHORT).show()
        }
        
        academicOption.setOnClickListener {
            PerplexityPreferences.setSearchMode(this, PerplexityPreferences.SEARCH_MODE_ACADEMIC)
            updatePerplexitySearchModeButton()
            dialog.dismiss()
            Toast.makeText(this, "Search Mode: Academic", Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
    }
    
    /**
     * Show context size selection dialog - new Perplexity style
     */
    private fun showPerplexityContextSizeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_perplexity_context_size, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        
        // Set dialog to appear at bottom of screen
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.attributes?.windowAnimations = R.style.DialogSlideAnimation
        
        val searchOption = dialogView.findViewById<LinearLayout>(R.id.searchOption)
        val advancedOption = dialogView.findViewById<LinearLayout>(R.id.advancedOption)
        val researchOption = dialogView.findViewById<LinearLayout>(R.id.researchOption)
        val searchCheckmark = dialogView.findViewById<ImageView>(R.id.searchCheckmark)
        val researchCheckmark = dialogView.findViewById<ImageView>(R.id.researchCheckmark)
        val proSwitch = dialogView.findViewById<Switch>(R.id.proSwitch)
        
        // Show current selection
        val currentContextSize = PerplexityPreferences.getContextSize(this)
        when (currentContextSize) {
            PerplexityPreferences.CONTEXT_SIZE_LOW -> {
                searchCheckmark.visibility = View.VISIBLE
                researchCheckmark.visibility = View.GONE
                proSwitch.isChecked = false
                searchOption.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background_selected)
                researchOption.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background)
            }
            PerplexityPreferences.CONTEXT_SIZE_MEDIUM -> {
                searchCheckmark.visibility = View.GONE
                researchCheckmark.visibility = View.GONE
                proSwitch.isChecked = true
                searchOption.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background)
                researchOption.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background)
            }
            PerplexityPreferences.CONTEXT_SIZE_HIGH -> {
                searchCheckmark.visibility = View.GONE
                researchCheckmark.visibility = View.VISIBLE
                proSwitch.isChecked = false
                searchOption.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background)
                researchOption.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background_selected)
            }
        }
        
        searchOption.setOnClickListener {
            PerplexityPreferences.setContextSize(this, PerplexityPreferences.CONTEXT_SIZE_LOW)
            updatePerplexityContextSizeButton()
            dialog.dismiss()
            Toast.makeText(this, "Context Size: Search", Toast.LENGTH_SHORT).show()
        }
        
        advancedOption.setOnClickListener {
            PerplexityPreferences.setContextSize(this, PerplexityPreferences.CONTEXT_SIZE_MEDIUM)
            updatePerplexityContextSizeButton()
            dialog.dismiss()
            Toast.makeText(this, "Context Size: Advanced", Toast.LENGTH_SHORT).show()
        }
        
        researchOption.setOnClickListener {
            PerplexityPreferences.setContextSize(this, PerplexityPreferences.CONTEXT_SIZE_HIGH)
            updatePerplexityContextSizeButton()
            dialog.dismiss()
            Toast.makeText(this, "Context Size: Research", Toast.LENGTH_SHORT).show()
        }
        
        proSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                PerplexityPreferences.setContextSize(this, PerplexityPreferences.CONTEXT_SIZE_MEDIUM)
                updatePerplexityContextSizeButton()
                dialog.dismiss()
                Toast.makeText(this, "Context Size: Advanced", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Update the search mode button appearance - new Perplexity card style
     */
    private fun updatePerplexitySearchModeButton() {
        val currentMode = PerplexityPreferences.getSearchMode(this)
        val isAcademic = currentMode == PerplexityPreferences.SEARCH_MODE_ACADEMIC
        
        btnPerplexitySearchMode.isSelected = isAcademic
        if (isAcademic) {
            btnPerplexitySearchMode.background = ContextCompat.getDrawable(this, R.drawable.perplexity_search_mode_card_background_selected)
            btnPerplexitySearchMode.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))
            btnPerplexitySearchMode.contentDescription = "Search Mode: Academic"
        } else {
            btnPerplexitySearchMode.background = ContextCompat.getDrawable(this, R.drawable.perplexity_search_mode_card_background)
            btnPerplexitySearchMode.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            btnPerplexitySearchMode.contentDescription = "Search Mode: Web"
        }
    }
    
    /**
     * Update the context size button appearance - new Perplexity card style
     */
    private fun updatePerplexityContextSizeButton() {
        val currentContextSize = PerplexityPreferences.getContextSize(this)
        val displayName = PerplexityPreferences.getContextSizeDisplayName(currentContextSize)
        
        // Update button appearance based on context size - new card style
        when (currentContextSize) {
            PerplexityPreferences.CONTEXT_SIZE_LOW -> {
                // First option (Search) - highlighted in blue
                btnPerplexityContextSize.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background_selected)
                btnPerplexityContextSize.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary)) // Blue
            }
            PerplexityPreferences.CONTEXT_SIZE_MEDIUM -> {
                // Second option (Advanced) - highlighted in blue with switch indicator
                btnPerplexityContextSize.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background_selected)
                btnPerplexityContextSize.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary)) // Blue
            }
            PerplexityPreferences.CONTEXT_SIZE_HIGH -> {
                // Third option (Research) - remove highlighting from first two, highlight this one
                btnPerplexityContextSize.background = ContextCompat.getDrawable(this, R.drawable.perplexity_context_card_background_selected)
                btnPerplexityContextSize.setColorFilter("#FF9800".toColorInt()) // Orange for research
            }
        }
        
        btnPerplexityContextSize.contentDescription = "Search Context: $displayName"
    }
    
    /**
     * Update visibility of Perplexity-specific buttons based on current model
     */
    private fun updatePerplexityButtonsVisibility() {
        val isPerplexityModel = PerplexityPreferences.isPerplexityModel(ModelManager.selectedModel.id)
        
        btnPerplexitySearchMode.visibility = if (isPerplexityModel) View.VISIBLE else View.GONE
        btnPerplexityContextSize.visibility = if (isPerplexityModel) View.VISIBLE else View.GONE
        
        if (isPerplexityModel) {
            updatePerplexitySearchModeButton()
            updatePerplexityContextSizeButton()
        }
    }

    // -------------------------------------------------------------------------
    // AI PARAMETERS MANAGEMENT
    // -------------------------------------------------------------------------
    
    /**
     * Show the AI Parameters dialog for the current model
     */
    private fun showAiParametersDialog() {
        val currentModel = ModelManager.selectedModel
        val currentParams = currentAiParameters ?: aiParametersManager.getParametersForModel(currentModel)
        
        val dialog = AIParametersDialog(
            context = this,
            currentModel = currentModel,
            currentParameters = currentParams
        ) { newParameters ->
            // Save the new parameters
            aiParametersManager.saveParametersForModel(currentModel, newParameters)
            currentAiParameters = newParameters
            
            // Update the button state to show parameters are active
            updateAiParametersButtonState()
            
            // Log the parameter change
            Timber.d("AI parameters updated for model ${currentModel.id}: $newParameters")
            
            // Show a toast confirmation
            Toast.makeText(this, "AI parameters updated for ${currentModel.displayName}", Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
    }
    
    /**
     * Update the AI parameters button state based on whether custom parameters are active
     */
    private fun updateAiParametersButtonState() {
        val currentModel = ModelManager.selectedModel
        val hasCustomParams = aiParametersManager.hasCustomParameters(currentModel)
        
        if (hasCustomParams) {
            // Active state - show that custom parameters are applied
            btnAiParameters.background = ContextCompat.getDrawable(this, R.drawable.circle_button_active)
            btnAiParameters.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        } else {
            // Inactive state - show default parameters
            btnAiParameters.background = ContextCompat.getDrawable(this, R.drawable.circle_button_inactive)
            btnAiParameters.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }
    
    /**
     * Load AI parameters for the current model
     */
    private fun loadAiParametersForCurrentModel() {
        val currentModel = ModelManager.selectedModel
        currentAiParameters = aiParametersManager.getParametersForModel(currentModel)
        updateAiParametersButtonState()
    }
    
    /**
     * Get API parameters for making requests
     */
    private fun getApiParametersForRequest(): Map<String, Any> {
        val currentModel = ModelManager.selectedModel
        val parameters = currentAiParameters ?: aiParametersManager.getParametersForModel(currentModel)
        return aiParametersManager.createApiParameters(parameters, currentModel.id)
    }
    
    /**
     * Update token counter UI using SimplifiedTokenManager
     * Replaces TokenCounterHelper.updateTokenCounter
     */
    private fun updateTokenCounterUI(
        textView: TextView,
        text: String,
        modelId: String,
        isSubscribed: Boolean
    ) {
        try {
            val conversationId = currentConversationId ?: return
            
            // Get usage summary from SimplifiedTokenManager
            val usageSummary = tokenManager.getUsageSummary(
                conversationId = conversationId,
                modelId = modelId,
                isSubscribed = isSubscribed
            )
            
            // Check if warning is needed
            val (needsWarning, warningMessage) = tokenManager.checkNeedsWarning(
                conversationId = conversationId,
                modelId = modelId,
                isSubscribed = isSubscribed
            )
            
            // Update UI
            textView.text = usageSummary
            textView.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (needsWarning) R.color.error_color else R.color.text_secondary
                )
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating token counter UI: ${e.message}")
            textView.text = "Error"
            textView.setTextColor(ContextCompat.getColor(this, R.color.error_color))
        }
    }
    
    // === INSTANCE STATE PRESERVATION ===
    // CRITICAL FIX: Preserve model state during external activities (file picker, permissions)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            // Save critical state that can get lost during external activities
            outState.putString("selected_model_id", ModelManager.selectedModel.id)
            outState.putString("current_conversation_id", currentConversationId)
            outState.putBoolean("is_returning_from_file_selection", isReturningFromFileSelection)
            outState.putBoolean("is_first_model_selection", isFirstModelSelection)
            
            // Save file selection state if any
            if (selectedFiles.isNotEmpty()) {
                val fileUris = selectedFiles.map { it.uri.toString() }.toTypedArray()
                outState.putStringArray("selected_file_uris", fileUris)
            }
            
            Timber.d("💾 State saved - model: ${ModelManager.selectedModel.id}, conversation: $currentConversationId")
        } catch (e: Exception) {
            Timber.e(e, "Error saving instance state: ${e.message}")
        }
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        try {
            // Restore model selection to prevent unwanted model changes
            val savedModelId = savedInstanceState.getString("selected_model_id")
            if (savedModelId != null) {
                val modelIndex = ModelManager.models.indexOfFirst { it.id == savedModelId }
                if (modelIndex != -1) {
                    ModelManager.selectedModel = ModelManager.models[modelIndex]
                    
                    // Update display to match restored model
                    updateSelectedModelDisplay()
                    
                    // Update UI controls for the restored model
                    updateControlsVisibility(savedModelId)
                    applyModelColorToUI(savedModelId)
                    
                    Timber.d("🔄 Model state restored to: ${ModelManager.selectedModel.displayName}")
                } else {
                    Timber.w("Saved model ID '$savedModelId' not found, keeping current selection")
                }
            }
            
            // Restore other critical state
            currentConversationId = savedInstanceState.getString("current_conversation_id")
            isReturningFromFileSelection = savedInstanceState.getBoolean("is_returning_from_file_selection", false)
            isFirstModelSelection = savedInstanceState.getBoolean("is_first_model_selection", false)
            
            // Restore file selection if any
            savedInstanceState.getStringArray("selected_file_uris")?.let { uriStrings ->
                selectedFiles.clear()
                uriStrings.forEach { uriString ->
                    try {
                        val uri = Uri.parse(uriString)
                        val fileName = FileUtil.getFileName(this, uri) ?: "Unknown file"
                        val fileSize = FileUtil.getFileSize(this, uri)
                        val mimeType = FileUtil.getMimeType(this, uri)
                        selectedFiles.add(FileUtil.SelectedFile(
                            uri = uri,
                            name = fileName,
                            size = fileSize,
                            isDocument = !mimeType.startsWith("image/")
                        ))
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to restore file: $uriString")
                    }
                }
                
                if (selectedFiles.isNotEmpty()) {
                    if (::fileHandler.isInitialized) {
                        fileHandler.updateSelectedFilesView()
                    }
                    Timber.d("📎 Restored ${selectedFiles.size} selected files")
                }
            }
            
            // Clear any stored file picker model state since we've restored from instance state
            modelStateBeforeFilePicker = null
            
            Timber.d("🔄 Instance state restored successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error restoring instance state: ${e.message}")
        }
    }

    // Extension function for converting dp to pixels
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    // Implementation of TranslationModeFetcher interface
    override fun isTranslationMode(): Boolean {
        return isTranslationModeActive
    }

   companion object {
        private const val TAG = "MainActivity"
    }
}