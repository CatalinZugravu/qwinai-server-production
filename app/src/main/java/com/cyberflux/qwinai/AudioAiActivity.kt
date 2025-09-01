package com.cyberflux.qwinai

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.tools.WebSearchTool
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.utils.SimplifiedTokenManager
import com.cyberflux.qwinai.views.LiquidMorphingView
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.Base64
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

class AudioAiActivity : BaseThemedActivity() {

    // UI Components - Beautiful Warm Amber Design
    private lateinit var ambientGlow: View
    private lateinit var liquidMorphingView: LiquidMorphingView
    private lateinit var statusText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var muteButton: CardView
    private lateinit var recordButton: CardView
    private lateinit var exitButton: CardView
    private lateinit var btnSettings: android.widget.ImageButton
    private lateinit var muteIconContainer: FrameLayout
    private lateinit var micHead: View
    private lateinit var micStand: View
    private lateinit var micBase: View
    private lateinit var muteStrike: View
    private lateinit var recordIndicator: View
    private lateinit var recordingRing: View
    private lateinit var volumeVisualizer: LinearLayout
    private lateinit var statusDot: View
    private lateinit var muteLabel: TextView

    // Audio Components - Enhanced with AI tools
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    
    // AI Tools
    private var webSearchTool: WebSearchTool? = null
    private var isSearching = false
    
    // Token Management
    private lateinit var tokenManager: SimplifiedTokenManager
    private val audioConversationId = "audio_session_${System.currentTimeMillis()}"
    private val audioModelId = "gpt-4o-audio-preview"
    private var totalTokensUsed = 0

    // Audio Settings - UNCHANGED
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Animation and State
    private var currentState = CallState.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var ambientGlowAnimator: ObjectAnimator? = null
    private var recordingAnimator: ObjectAnimator? = null
    private var volumeAnimators = mutableListOf<ValueAnimator>()
    private var pulseAnimators = mutableListOf<ObjectAnimator>()

    // Audio Data - UNCHANGED
    private var recordedAudioData: ByteArray? = null
    private var volumeLevelValue = 0f

    // Playback audio configuration - Enhanced for proper speed
    private val playbackSampleRate = 24000 // Match API output sample rate
    private val playbackChannelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val playbackAudioFormat = AudioFormat.ENCODING_PCM_16BIT

    // API Settings - Enhanced with voice selection
    private val audioApiFormat = "wav"
    private var voiceType = "coral"

    enum class CallState {
        IDLE, RECORDING, PROCESSING, SPEAKING, LISTENING, SEARCHING
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 101
        private const val PREF_VOICE_TYPE = "audio_voice_type"
        private const val PREF_USE_WEB_SEARCH = "audio_use_web_search"
        
        // Available voices for GPT-4 Audio
        private val AVAILABLE_VOICES = arrayOf(
            "alloy", "ash", "ballad", "coral", "echo", 
            "fable", "nova", "onyx", "sage", "shimmer"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_ai)

        initializeViews()
        setupClickListeners()
        setupInitialAnimations()
        checkAudioPermission()
        createVolumeVisualizer()

        // Initialize token manager first (needed by loadUserPreferences)
        initializeTokenManager()
        
        // Load user preferences
        loadUserPreferences()
        
        // Initialize AI tools
        initializeAITools()
        
        // Set initial state
        setState(CallState.IDLE)
    }
    
    private fun initializeAITools() {
        webSearchTool = WebSearchTool(this)
    }
    
    private fun initializeTokenManager() {
        tokenManager = SimplifiedTokenManager(this)
        // Reset conversation on new session
        tokenManager.resetConversation(audioConversationId)
        Timber.d("📊 Token manager initialized for audio session: $audioConversationId")
    }
    
    private fun loadUserPreferences() {
        voiceType = PrefsManager.getAudioVoice(this, "coral")
        
        // Update UI to show current settings with token usage
        updateTokenDisplay()
    }

    private fun initializeViews() {
        // Main UI components
        ambientGlow = findViewById(R.id.ambientGlow)
        liquidMorphingView = findViewById(R.id.liquidMorphingView)
        statusText = findViewById(R.id.statusText)
        subtitleText = findViewById(R.id.subtitleText)
        volumeVisualizer = findViewById(R.id.volumeVisualizer)
        statusDot = findViewById(R.id.statusDot)
        btnSettings = findViewById(R.id.btnSettings)

        // Control buttons
        muteButton = findViewById(R.id.muteButton)
        recordButton = findViewById(R.id.recordButton)
        exitButton = findViewById(R.id.exitButton)
        muteLabel = findViewById(R.id.muteLabel)

        // Icon components
        muteIconContainer = findViewById(R.id.muteIconContainer)
        micHead = findViewById(R.id.micHead)
        micStand = findViewById(R.id.micStand)
        micBase = findViewById(R.id.micBase)
        muteStrike = findViewById(R.id.muteStrike)
        recordIndicator = findViewById(R.id.recordIndicator)
        recordingRing = findViewById(R.id.recordingRing)
    }

    private fun setupClickListeners() {
        recordButton.setOnClickListener {
            handleMicButtonClick()
        }

        muteButton.setOnClickListener {
            toggleMute()
        }

        exitButton.setOnClickListener {
            endCall()
        }
        
        // Add voice settings access via settings button
        btnSettings.setOnClickListener {
            showAdvancedSettingsMenu()
        }

        // Add beautiful touch animations
        setupTouchAnimations()
    }

    private fun setupTouchAnimations() {
        listOf(muteButton, recordButton, exitButton, btnSettings).forEach { button ->
            button.setOnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate()
                            .scaleX(0.92f)
                            .scaleY(0.92f)
                            .setDuration(150)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                        provideTactileFeedback()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                    }
                }
                false
            }
        }
    }

    private fun setupInitialAnimations() {
        // Setup gentle ambient glow pulse
        setupAmbientGlowAnimation()
        // The liquid morphing is now handled automatically by the custom view
    }

    private fun setupAmbientGlowAnimation() {
        ambientGlowAnimator = ObjectAnimator.ofFloat(ambientGlow, "alpha", 0.4f, 0.8f)
        ambientGlowAnimator?.apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun createVolumeVisualizer() {
        volumeVisualizer.removeAllViews()

        val barCount = 15
        for (i in 0 until barCount) {
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(8),
                    dpToPx(6),
                    0f
                ).apply {
                    marginStart = dpToPx(2)
                    marginEnd = dpToPx(2)
                }
                background = ContextCompat.getDrawable(this@AudioAiActivity, R.drawable.volume_bar_inactive)
            }
            volumeVisualizer.addView(bar)
        }
    }

    // KEEPING ALL EXISTING AUDIO PERMISSION LOGIC UNCHANGED
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Audio permission required for voice calls", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // KEEPING ALL EXISTING AUDIO LOGIC UNCHANGED
    private fun handleMicButtonClick() {
        provideTactileFeedback()

        when (currentState) {
            CallState.IDLE -> {
                startRecording()
            }
            CallState.RECORDING -> {
                stopRecording()
            }
            else -> {
                // Ignore clicks during processing or speaking
                Timber.d("Ignoring mic button click in state: $currentState")
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            checkAudioPermission()
            return
        }

        try {
            runOnUiThread {
                setState(CallState.RECORDING)
            }
            isRecording = true

            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord initialization failed")
                stopRecording()
                return
            }

            audioRecord?.startRecording()

            // Start recording in background thread
            lifecycleScope.launch(Dispatchers.IO) {
                recordAudio()
            }

            Timber.d("Recording started")

        } catch (e: Exception) {
            Timber.e(e, "Error starting recording: ${e.message}")
            runOnUiThread {
                setState(CallState.IDLE)
                showError("Failed to start recording: ${e.message}")
            }
        }
    }

    private fun recordAudio() {
        val audioData = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)

        try {
            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    audioData.write(buffer, 0, bytesRead)

                    // Calculate volume level for visualization
                    val volume = calculateVolumeLevel(buffer, bytesRead)

                    // Update UI on main thread
                    runOnUiThread {
                        updateVolumeVisualization(volume)
                        volumeLevelValue = volume
                    }
                }
            }

            // Create WAV file with proper headers
            val rawAudioData = audioData.toByteArray()
            recordedAudioData = createWavFile(rawAudioData)

            Timber.d("Recording completed:")
            Timber.d("- Raw PCM size: ${rawAudioData.size} bytes")
            Timber.d("- WAV file size: ${recordedAudioData?.size} bytes")
            Timber.d("- Sample rate: $sampleRate Hz")

        } catch (e: Exception) {
            Timber.e(e, "Error during recording: ${e.message}")
            runOnUiThread {
                showError("Recording error: ${e.message}")
                setState(CallState.IDLE)
            }
        }
    }

    // KEEPING ALL EXISTING AUDIO PROCESSING METHODS UNCHANGED
    private fun createWavFile(audioData: ByteArray): ByteArray {
        val wavHeader = createWavHeader(audioData.size)
        val wavFile = ByteArray(wavHeader.size + audioData.size)

        // Copy header
        System.arraycopy(wavHeader, 0, wavFile, 0, wavHeader.size)
        // Copy audio data
        System.arraycopy(audioData, 0, wavFile, wavHeader.size, audioData.size)

        return wavFile
    }

    private fun createWavHeader(audioDataSize: Int): ByteArray {
        val headerSize = 44
        val totalSize = headerSize + audioDataSize - 8
        val channels = 1 // Mono
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8

        return ByteArray(headerSize).apply {
            // RIFF header
            "RIFF".toByteArray().copyInto(this, 0)
            writeInt32(this, 4, totalSize)
            "WAVE".toByteArray().copyInto(this, 8)

            // fmt chunk
            "fmt ".toByteArray().copyInto(this, 12)
            writeInt32(this, 16, 16) // fmt chunk size
            writeInt16(this, 20, 1)  // PCM format
            writeInt16(this, 22, channels.toShort())
            writeInt32(this, 24, sampleRate)
            writeInt32(this, 28, byteRate)
            writeInt16(this, 32, (channels * bitsPerSample / 8).toShort())
            writeInt16(this, 34, bitsPerSample.toShort())

            // data chunk
            "data".toByteArray().copyInto(this, 36)
            writeInt32(this, 40, audioDataSize)
        }
    }

    private fun writeInt32(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeInt16(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset] = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun calculateVolumeLevel(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += abs(sample.toDouble())
        }
        val average = sum / (bytesRead / 2)
        val volumeLevel = (20 * log10(average + 1)).toFloat().coerceIn(0f, 100f)

        // Update the liquid morphing view with the volume level
        runOnUiThread {
            liquidMorphingView.setVolumeLevel(volumeLevel)
        }

        return volumeLevel
    }

    private fun updateVolumeVisualization(volume: Float) {
        if (currentState != CallState.RECORDING && currentState != CallState.SPEAKING) return

        // Update the liquid morphing view
        liquidMorphingView.setVolumeLevel(volume)

        val barCount = volumeVisualizer.childCount
        val activeBarCount = ((volume / 100f) * barCount).toInt()

        for (i in 0 until barCount) {
            val bar = volumeVisualizer.getChildAt(i)
            val isActive = i < activeBarCount

            val targetHeight = if (isActive) {
                dpToPx((20 + (volume / 100f) * 20).toInt())
            } else {
                dpToPx(6)
            }

            val animator = ValueAnimator.ofInt(bar.layoutParams.height, targetHeight)
            animator.duration = 100
            animator.addUpdateListener { animation ->
                val params = bar.layoutParams as LinearLayout.LayoutParams
                params.height = animation.animatedValue as Int
                bar.layoutParams = params
            }
            animator.start()

            bar.background = ContextCompat.getDrawable(
                this,
                if (isActive) R.drawable.volume_bar else R.drawable.volume_bar_inactive
            )
        }
    }

    private fun stopRecording() {
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            runOnUiThread {
                setState(CallState.PROCESSING)
            }

            // Process the recorded audio with validation
            recordedAudioData?.let { audioData ->
                if (isAudioMeaningful(audioData)) {
                    processAudioWithAI(audioData)
                } else {
                    runOnUiThread {
                        showError("No speech detected. Please speak clearly into the microphone.")
                        setState(CallState.IDLE)
                        Timber.d("Audio recording was too quiet or empty - not sending to API")
                    }
                }
            } ?: run {
                runOnUiThread {
                    showError("No audio data recorded")
                    setState(CallState.IDLE)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error stopping recording: ${e.message}")
            runOnUiThread {
                setState(CallState.IDLE)
            }
        }
    }
    
    /**
     * Check if the recorded audio contains meaningful speech
     * Analyzes volume levels and duration to prevent sending empty/silent audio
     */
    private fun isAudioMeaningful(audioData: ByteArray): Boolean {
        if (audioData.size < 44) { // Minimum WAV header size
            Timber.d("Audio data too small: ${audioData.size} bytes")
            return false
        }
        
        // Skip WAV header (44 bytes) and analyze actual audio data
        val audioSamples = audioData.sliceArray(44 until audioData.size)
        
        if (audioSamples.size < sampleRate / 2) { // Less than 0.5 seconds of audio
            Timber.d("Audio too short: ${audioSamples.size} samples (< 0.5 seconds)")
            return false
        }
        
        var totalVolume = 0.0
        var peakVolume = 0.0
        var significantSamples = 0
        val volumeThreshold = 1000.0 // Threshold for meaningful audio
        
        // Analyze audio samples (16-bit PCM)
        for (i in audioSamples.indices step 2) {
            if (i + 1 < audioSamples.size) {
                val sample = (audioSamples[i + 1].toInt() shl 8) or (audioSamples[i].toInt() and 0xFF)
                val volume = kotlin.math.abs(sample.toDouble())
                
                totalVolume += volume
                peakVolume = kotlin.math.max(peakVolume, volume)
                
                if (volume > volumeThreshold) {
                    significantSamples++
                }
            }
        }
        
        val sampleCount = audioSamples.size / 2
        val averageVolume = totalVolume / sampleCount
        val significantRatio = significantSamples.toDouble() / sampleCount
        
        val isMeaningful = averageVolume > volumeThreshold/2 && 
                          peakVolume > volumeThreshold*2 && 
                          significantRatio > 0.01 // At least 1% of samples above threshold
        
        Timber.d("Audio analysis:")
        Timber.d("- Duration: ${sampleCount / sampleRate.toDouble()} seconds")
        Timber.d("- Average volume: $averageVolume")
        Timber.d("- Peak volume: $peakVolume")
        Timber.d("- Significant samples ratio: ${significantRatio * 100}%")
        Timber.d("- Is meaningful: $isMeaningful")
        
        return isMeaningful
    }

    // KEEPING ALL EXISTING API PROCESSING LOGIC UNCHANGED
    fun processAudioWithAI(audioData: ByteArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    setState(CallState.PROCESSING)
                }

                val base64Audio = java.util.Base64.getEncoder().encodeToString(audioData)
                
                // Create enhanced request with AI capabilities
                val requestJson = createEnhancedAudioApiRequest(base64Audio)
                val requestBody = requestJson.toRequestBody("application/json".toMediaTypeOrNull())

                val apiConfig = RetrofitInstance.apiConfigs[RetrofitInstance.ApiServiceType.AIMLAPI]
                val client = RetrofitInstance.createHttpClient(apiKey = apiConfig?.apiKey ?: "")

                val request = Request.Builder()
                    .url("https://api.aimlapi.com/chat/completions")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody != null) {
                        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                        
                        withContext(Dispatchers.Main) {
                            // Initialize audio with proper configuration for speech
                            initializeAudioTrackForSpeech()
                            
                            // Show web search indicator proactively if enabled
                            val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
                            val useWebSearch = prefs.getBoolean(PREF_USE_WEB_SEARCH, false)
                            if (useWebSearch && !isSearching) {
                                showWebSearchIndicator()
                            }
                        }

                        // Buffer to accumulate audio bytes
                        val audioBuffer = ByteArrayOutputStream()

                        var audioChunksReceived = 0
                        var isStreamComplete = false
                        
                        while (!isStreamComplete) {
                            val line = reader.readLine()
                            if (line == null) {
                                Timber.d("Stream ended naturally")
                                break
                            }
                            
                            Timber.d("Received line: $line")

                            try {
                                // Skip empty lines
                                if (line.isBlank()) continue

                                // Handle different SSE formats and completion markers
                                val jsonStr = when {
                                    line.startsWith("data: ") -> line.substring(6)
                                    line.startsWith("event: ") -> continue // Skip event lines
                                    line == "data: [DONE]" -> {
                                        Timber.d("Stream completion marker received")
                                        isStreamComplete = true
                                        break
                                    }
                                    else -> line
                                }
                                
                                if (jsonStr.isBlank() || jsonStr == "[DONE]") {
                                    isStreamComplete = true
                                    break
                                }

                                val json = JSONObject(jsonStr)
                                
                                // Track token usage if present
                                trackTokenUsage(json)
                                
                                val choices = json.optJSONArray("choices")

                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)

                                    // Check for delta format (streaming API)
                                    val delta = choice.optJSONObject("delta")
                                    if (delta != null) {
                                        val audio = delta.optJSONObject("audio")
                                        if (audio != null) {
                                            // Check for binary audio data
                                            val audioDataStr = audio.optString("data")
                                            if (!audioDataStr.isNullOrEmpty()) {
                                                val audioBytes = Base64.getDecoder().decode(audioDataStr)
                                                
                                                // Write audio data with enhanced error handling
                                                audioTrack?.let { track ->
                                                    try {
                                                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                                            val writeResult = track.write(audioBytes, 0, audioBytes.size)
                                                            if (writeResult < 0) {
                                                                Timber.w("AudioTrack write error: $writeResult")
                                                                when (writeResult) {
                                                                    AudioTrack.ERROR_INVALID_OPERATION -> {
                                                                        Timber.e("AudioTrack invalid operation - recreating")
                                                                        recreateAudioTrack()
                                                                    }
                                                                    AudioTrack.ERROR_BAD_VALUE -> {
                                                                        Timber.e("AudioTrack bad value error")
                                                                    }
                                                                    else -> {
                                                                        Timber.e("AudioTrack unknown error: $writeResult")
                                                                    }
                                                                }
                                                            } else {
                                                                Timber.v("Audio chunk written: ${audioBytes.size} bytes")
                                                            }
                                                        } else {
                                                            Timber.w("AudioTrack not in playing state: ${track.playState}")
                                                        }
                                                    } catch (e: Exception) {
                                                        Timber.e(e, "Exception writing audio data: ${e.message}")
                                                        recreateAudioTrack()
                                                    }
                                                }
                                                
                                                audioBuffer.write(audioBytes)
                                                audioChunksReceived++
                                            }

                                            // Process transcript for AI actions and debugging
                                            val transcript = audio.optString("transcript")
                                            if (!transcript.isNullOrEmpty()) {
                                                Timber.d("Transcript: $transcript")
                                                processTranscriptForAIActions(transcript)
                                            }
                                        }
                                    } else {
                                        // Check for message format (non-streaming API)
                                        val message = choice.optJSONObject("message")
                                        val audio = message?.optJSONObject("audio")
                                        val audioDataStr = audio?.optString("data")
                                        if (!audioDataStr.isNullOrEmpty()) {
                                            val audioBytes = Base64.getDecoder().decode(audioDataStr)
                                            audioTrack?.write(audioBytes, 0, audioBytes.size)
                                            audioBuffer.write(audioBytes)
                                            audioChunksReceived++
                                        }
                                    }
                                    
                                    // Check for completion
                                    val finishReason = choice.optString("finish_reason")
                                    if (finishReason == "stop" || finishReason == "length") {
                                        Timber.d("Stream finished with reason: $finishReason")
                                        isStreamComplete = true
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing streaming response: ${e.message}")
                            }
                        }

                        // Log total audio data received
                        val totalAudioBytes = audioBuffer.toByteArray()
                        Timber.d("Enhanced audio streaming complete:")
                        Timber.d("- Total audio chunks: $audioChunksReceived")
                        Timber.d("- Total audio bytes: ${totalAudioBytes.size}")

                        // Handle audio completion
                        withContext(Dispatchers.Main) {
                            if (totalAudioBytes.isEmpty()) {
                                showError("No audio data received from API. Check if your API key has TTS capabilities.")
                                cleanupAudioTrack()
                                setState(CallState.IDLE)
                            } else {
                                // Calculate audio duration and wait for playback to complete
                                val audioLengthMs = calculateAudioDuration(totalAudioBytes.size)
                                Timber.d("Waiting for audio playback to complete: ${audioLengthMs}ms")
                                
                                finalizeAudioPlayback(audioLengthMs)
                            }
                        }
                    }
                } else {
                    val errorCode = response.code
                    val errorBody = response.body?.string()
                    Timber.e("API Error: $errorCode - $errorBody")

                    withContext(Dispatchers.Main) {
                        showError("API error ($errorCode). Please try again.")
                        setState(CallState.IDLE)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error streaming audio: ${e.message}")

                withContext(Dispatchers.Main) {
                    showError("Streaming error: ${e.message}")
                    setState(CallState.IDLE)
                }
            }
        }
    }

    private suspend fun createEnhancedAudioApiRequest(base64Audio: String): String = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
        val useWebSearch = prefs.getBoolean(PREF_USE_WEB_SEARCH, false)
        
        // Build enhanced system message
        val systemMessage = buildEnhancedSystemMessage(useWebSearch)
        
        val messagesArray = JSONArray().apply {
            // Add system message
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemMessage)
            })
            
            // Add current audio input
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_audio")
                        put("input_audio", JSONObject().apply {
                            put("data", base64Audio)
                            put("format", "wav")
                        })
                    })
                })
            })
        }

        val requestJson = JSONObject().apply {
            put("model", "gpt-4o-audio-preview")

            // Include both text and audio modalities
            put("modalities", JSONArray().apply {
                put("text")
                put("audio")
            })

            // Audio config with selected voice
            put("audio", JSONObject().apply {
                put("format", "pcm16")
                put("voice", voiceType)
            })

            put("messages", messagesArray)

            // Optimized parameters for audio responses  
            put("max_tokens", 75) // Lower limit for shorter audio responses
            put("temperature", 0.7)
            put("top_p", 0.9)
            put("frequency_penalty", 0.0)
            put("presence_penalty", 0.0)
            put("stream", true)
        }

        val requestString = requestJson.toString()
        Timber.d("Optimized Audio API request created:")
        Timber.d("- Model: gpt-4o-audio-preview")
        Timber.d("- Voice: $voiceType")
        Timber.d("- Max tokens: 75")
        Timber.d("- Web search: $useWebSearch")
        Timber.d("- Audio data length: ${base64Audio.length}")
        Timber.d("- System message length: ${systemMessage.length}")

        requestString
    }
    
    private fun buildEnhancedSystemMessage(useWebSearch: Boolean): String {
        val systemPrompt = StringBuilder()
        
        systemPrompt.append("You are a voice-only AI assistant. You can only receive audio input and respond with audio. ")
        systemPrompt.append("You cannot see images, process visual content, or analyze any visual media. ")
        systemPrompt.append("Give brief, natural spoken responses suitable for voice conversation. ")
        systemPrompt.append("If asked about visual content, politely explain that you can only process audio. ")
        
        if (useWebSearch) {
            systemPrompt.append("Search the web when asked about current information. ")
        }
        
        return systemPrompt.toString()
    }
    
    private fun showWebSearchIndicator() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (!isSearching) {
                Timber.d("Showing proactive web search indicator")
                isSearching = true
                setState(CallState.SEARCHING)
                
                // Show indicator briefly at the start of AI response
                handler.postDelayed({
                    if (isSearching && currentState == CallState.SEARCHING) {
                        isSearching = false
                        setState(CallState.SPEAKING)
                        Timber.d("Web search indicator completed, returning to speaking")
                    }
                }, 2000) // Show search indicator for 2 seconds initially
            }
        }
    }
    
    private fun initializeAudioTrackForSpeech() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                playbackSampleRate, playbackChannelConfig, playbackAudioFormat
            )
            
            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Timber.e("Invalid AudioTrack buffer size: $minBufferSize")
                showError("Audio system error. Please restart the app.")
                return
            }
            
            // Use optimal buffer size for speech (4x minimum for smoother playback)
            val optimalBufferSize = minBufferSize * 4
            
            Timber.d("Audio track configuration:")
            Timber.d("- Sample rate: $playbackSampleRate Hz")
            Timber.d("- Channel config: $playbackChannelConfig")
            Timber.d("- Audio format: $playbackAudioFormat")
            Timber.d("- Min buffer size: $minBufferSize bytes")
            Timber.d("- Optimal buffer size: $optimalBufferSize bytes")
            
            // Create AudioTrack with improved configuration
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(playbackAudioFormat)
                        .setSampleRate(playbackSampleRate)
                        .setChannelMask(playbackChannelConfig)
                        .build()
                )
                .setBufferSizeInBytes(optimalBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Timber.e("AudioTrack failed to initialize. State: ${audioTrack?.state}")
                showError("Failed to initialize audio playback")
                return
            }
                
            audioTrack?.play()
            isPlaying = true
            setState(CallState.SPEAKING)
            
            Timber.d("AudioTrack initialized and started successfully with improved config")
            
        } catch (e: Exception) {
            Timber.e(e, "Error initializing AudioTrack: ${e.message}")
            showError("Audio initialization failed: ${e.message}")
        }
    }
    
    private fun recreateAudioTrack() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                Timber.d("Recreating AudioTrack due to I/O errors")
                cleanupAudioTrack()
                
                // Small delay before recreating
                handler.postDelayed({
                    initializeAudioTrackForSpeech()
                }, 100)
            } catch (e: Exception) {
                Timber.e(e, "Error recreating AudioTrack: ${e.message}")
            }
        }
    }
    
    private fun finalizeAudioPlayback(durationMs: Long) {
        try {
            audioTrack?.let { track ->
                Timber.d("Finalizing audio playback, waiting ${durationMs}ms for completion")
                
                // Wait for the calculated duration plus a small buffer
                val waitTimeMs = durationMs + 500 // Add 500ms buffer
                
                handler.postDelayed({
                    try {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            Timber.d("Audio still playing, waiting additional 1000ms...")
                            // Wait a bit more if still playing
                            handler.postDelayed({
                                cleanupAudioTrack()
                                setState(CallState.IDLE)
                            }, 1000)
                        } else {
                            Timber.d("Audio playback completed")
                            cleanupAudioTrack()
                            setState(CallState.IDLE)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error in audio completion check: ${e.message}")
                        cleanupAudioTrack()
                        setState(CallState.IDLE)
                    }
                }, waitTimeMs)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error finalizing audio playback: ${e.message}")
            cleanupAudioTrack()
            setState(CallState.IDLE)
        }
    }
    
    private fun calculateAudioDuration(audioSizeBytes: Int): Long {
        // PCM 16-bit, mono, 24kHz
        val bytesPerSample = 2 // 16-bit = 2 bytes
        val samplesPerSecond = playbackSampleRate // 24000
        val bytesPerSecond = samplesPerSecond * bytesPerSample
        
        val durationSeconds = audioSizeBytes.toDouble() / bytesPerSecond
        val durationMs = (durationSeconds * 1000).toLong()
        
        Timber.d("Audio duration calculation:")
        Timber.d("- Audio size: $audioSizeBytes bytes")
        Timber.d("- Bytes per second: $bytesPerSecond")
        Timber.d("- Duration: ${durationSeconds}s (${durationMs}ms)")
        
        return durationMs
    }
    
    private fun cleanupAudioTrack() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
                Timber.d("AudioTrack cleaned up successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
            isPlaying = false
        }
    }
    
    private fun showAdvancedSettingsMenu() {
        val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
        val useWebSearch = prefs.getBoolean(PREF_USE_WEB_SEARCH, false)
        
        val options = arrayOf(
            "Change Voice",
            "Web Search: ${if (useWebSearch) "ON" else "OFF"}",
            "Token Usage Details"
        )
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Voice Assistant Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showVoiceSelectionMenu()
                    1 -> toggleWebSearch()
                    2 -> showTokenUsageDetails()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showVoiceSelectionMenu() {
        val voiceOptions = AVAILABLE_VOICES
        val currentIndex = voiceOptions.indexOf(voiceType)
        
        val items = voiceOptions.map { voice ->
            if (voice == voiceType) "$voice (current)" else voice
        }.toTypedArray()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Voice")
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                voiceType = voiceOptions[which]
                PrefsManager.setAudioVoice(this, voiceType)
                updateTokenDisplay() // Update UI
                
                Toast.makeText(this, "Voice changed to: ${voiceType.capitalize()}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleWebSearch() {
        val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
        val current = prefs.getBoolean(PREF_USE_WEB_SEARCH, false)
        val newValue = !current
        
        prefs.edit().putBoolean(PREF_USE_WEB_SEARCH, newValue).apply()
        updateTokenDisplay() // Update UI
        
        Toast.makeText(this, "Web Search: ${if (newValue) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
    }
    
    
    private fun processTranscriptForAIActions(transcript: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
            val useWebSearch = prefs.getBoolean(PREF_USE_WEB_SEARCH, false)
            
            // Enhanced web search detection with more indicators
            if (useWebSearch && detectWebSearchActivity(transcript) && !isSearching) {
                Timber.d("Web search activity detected in transcript: $transcript")
                isSearching = true
                setState(CallState.SEARCHING)
                
                // Return to speaking after indication
                handler.postDelayed({
                    if (isSearching) {
                        isSearching = false
                        if (currentState == CallState.SEARCHING) {
                            setState(CallState.SPEAKING)
                        }
                    }
                }, 2500) // Show search for 2.5 seconds
            }
        }
    }
    
    private fun detectWebSearchActivity(transcript: String): Boolean {
        val searchIndicators = listOf(
            // Direct search terms
            "searching", "search", "looking up", "let me find", "checking", "finding",
            "let me check", "I'll search", "I need to search", "searching for",
            
            // Current information indicators
            "current", "latest", "recent", "today", "now", "this year", "2025",
            "up to date", "most recent", "as of",
            
            // Specific search topics
            "news", "weather", "price", "stock", "rate", "temperature",
            "forecast", "market", "exchange rate",
            
            // Search result indicators
            "according to", "based on my search", "I found", "search results",
            "from my search", "web search shows", "online sources",
            
            // Research indicators
            "let me research", "I'll look that up", "checking online",
            "from the web", "internet search"
        )
        
        val lowerTranscript = transcript.lowercase()
        val hasSearchIndicator = searchIndicators.any { indicator -> lowerTranscript.contains(indicator) }
        
        if (hasSearchIndicator) {
            Timber.d("Web search indicator detected: ${searchIndicators.find { lowerTranscript.contains(it) }}")
        }
        
        return hasSearchIndicator
    }
    
    private fun trackTokenUsage(json: JSONObject) {
        try {
            val usageJson = json.optJSONObject("usage")
            if (usageJson != null) {
                val promptTokens = usageJson.optInt("prompt_tokens", 0)
                val completionTokens = usageJson.optInt("completion_tokens", 0)
                val totalTokens = usageJson.optInt("total_tokens", 0)
                val audioTokens = usageJson.optJSONObject("completion_tokens_details")?.optInt("audio_tokens", 0) ?: 0
                
                if (totalTokens > 0) {
                    totalTokensUsed = totalTokens
                    
                    // Create AimlApiResponse for token manager
                    val usage = AimlApiResponse.Usage(
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        audioTokens = audioTokens
                    )
                    
                    val apiResponse = AimlApiResponse(
                        id = json.optString("id"),
                        model = json.optString("model", audioModelId),
                        created = json.optLong("created"),
                        usage = usage
                    )
                    
                    // Record with token manager
                    val isSubscribed = PrefsManager.isSubscribed(this@AudioAiActivity)
                    lifecycleScope.launch {
                        tokenManager.recordApiResponse(
                            conversationId = audioConversationId,
                            modelId = audioModelId,
                            apiResponse = apiResponse,
                            isSubscribed = isSubscribed
                        )
                    }
                    
                    // Update UI on main thread
                    runOnUiThread {
                        updateTokenDisplay()
                    }
                    
                    Timber.d("📊 Token usage tracked: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens, audio=$audioTokens")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error tracking token usage: ${e.message}")
        }
    }
    
    private fun updateTokenDisplay() {
        try {
            // Check if tokenManager is initialized
            if (!::tokenManager.isInitialized) {
                Timber.w("⚠️ TokenManager not initialized yet, skipping token display update")
                return
            }
            
            val isSubscribed = PrefsManager.isSubscribed(this)
            val usageSummary = tokenManager.getUsageSummary(
                conversationId = audioConversationId,
                modelId = audioModelId,
                isSubscribed = isSubscribed
            )
            
            val (needsWarning, warningMessage) = tokenManager.checkNeedsWarning(
                conversationId = audioConversationId,
                modelId = audioModelId,
                isSubscribed = isSubscribed
            )
            
            // Update subtitle to show token usage
            val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
            val useWebSearch = prefs.getBoolean(PREF_USE_WEB_SEARCH, false)
            
            val features = mutableListOf<String>()
            if (useWebSearch) features.add("Web Search")
            
            val baseText = if (features.isEmpty()) {
                "Voice: ${voiceType.capitalize()}"
            } else {
                "${features.joinToString(" • ")} • Voice: ${voiceType.capitalize()}"
            }
            
            // Add token usage if we have data
            val fullText = if (totalTokensUsed > 0) {
                "$baseText • $usageSummary"
            } else {
                baseText
            }
            
            subtitleText.text = fullText
            
            // Change color if warning needed
            if (needsWarning) {
                subtitleText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            } else {
                subtitleText.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating token display: ${e.message}")
        }
    }
    
    private fun showTokenUsageDetails() {
        try {
            val isSubscribed = PrefsManager.isSubscribed(this)
            val conversationUsage = tokenManager.getConversationUsageDetails(audioConversationId)
            val recentCalls = tokenManager.getRecentApiCalls(5)
            
            val details = StringBuilder()
            details.append("🎙️ Audio Session Token Usage\n\n")
            
            if (conversationUsage != null) {
                details.append("📊 Current Session:\n")
                details.append("Total Tokens: ${conversationUsage.totalTokens}\n")
                details.append("Input Tokens: ${conversationUsage.totalInputTokens.get()}\n")
                details.append("Output Tokens: ${conversationUsage.totalOutputTokens.get()}\n")
                details.append("Reasoning Tokens: ${conversationUsage.totalReasoningTokens.get()}\n")
                details.append("Messages: ${conversationUsage.messageCount.get()}\n\n")
            } else {
                details.append("No token usage data yet\n\n")
            }
            
            if (recentCalls.isNotEmpty()) {
                details.append("📈 Recent API Calls:\n")
                recentCalls.take(3).forEach { call ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(call.timestamp))
                    details.append("• $time: ${call.totalTokens} tokens\n")
                }
            }
            
            details.append("\n💡 Tip: Audio tokens count as both input (speech) and output (generated voice) tokens.")
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Token Usage Details")
                .setMessage(details.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Export Report") { _, _ ->
                    exportTokenUsageReport()
                }
                .show()
                
        } catch (e: Exception) {
            Timber.e(e, "Error showing token usage details: ${e.message}")
            Toast.makeText(this, "Error loading token usage details", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun exportTokenUsageReport() {
        try {
            val report = tokenManager.exportUsageReport()
            
            // Simple way to share the report
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, report)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Audio AI Token Usage Report")
            }
            
            startActivity(android.content.Intent.createChooser(intent, "Share Token Report"))
            
        } catch (e: Exception) {
            Timber.e(e, "Error exporting token usage report: ${e.message}")
            Toast.makeText(this, "Error exporting report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVolumeVisualizationForPlayback(mediaPlayer: MediaPlayer) {
        val volumeUpdateRunnable = object : Runnable {
            override fun run() {
                if (isPlaying && mediaPlayer.isPlaying) {
                    // Simulate volume levels for visualization
                    val volume = Random.nextFloat() * 60f + 20f
                    updateVolumeVisualization(volume)
                    volumeLevelValue = volume

                    handler.postDelayed(this, 100)
                } else {
                    // Reset visualization when playback stops
                    updateVolumeVisualization(0f)
                    volumeLevelValue = 0f
                }
            }
        }

        handler.post(volumeUpdateRunnable)
    }

    // NEW UI STATE MANAGEMENT
    private fun setState(newState: CallState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { setState(newState) }
            return
        }

        currentState = newState
        updateUIForState(newState)
    }

    private fun updateUIForState(state: CallState) {
        // Update the liquid morphing view state
        val morphState = when (state) {
            CallState.IDLE -> LiquidMorphingView.State.IDLE
            CallState.RECORDING -> LiquidMorphingView.State.LISTENING
            CallState.PROCESSING -> LiquidMorphingView.State.PROCESSING
            CallState.SEARCHING -> LiquidMorphingView.State.PROCESSING
            CallState.SPEAKING -> LiquidMorphingView.State.SPEAKING
            CallState.LISTENING -> LiquidMorphingView.State.LISTENING
        }
        liquidMorphingView.animateToState(morphState)

        when (state) {
            CallState.IDLE -> {
                statusText.text = "Tap to speak"
                loadUserPreferences() // Restore subtitle with settings
                volumeVisualizer.visibility = View.GONE
                recordingRing.visibility = View.GONE
                stopAllAnimations()

                // Reset record button
                recordIndicator.background = ContextCompat.getDrawable(this, R.drawable.record_circle)
            }

            CallState.RECORDING -> {
                statusText.text = "Listening..."
                subtitleText.text = "Speak now"
                volumeVisualizer.visibility = View.VISIBLE
                recordingRing.visibility = View.VISIBLE
                startRecordingAnimation()
            }

            CallState.PROCESSING -> {
                statusText.text = "Processing"
                subtitleText.text = "AI is thinking..."
                volumeVisualizer.visibility = View.GONE
                recordingRing.visibility = View.GONE
            }
            
            CallState.SEARCHING -> {
                statusText.text = "Searching the web..."
                subtitleText.text = "Finding current information"
                volumeVisualizer.visibility = View.GONE
                recordingRing.visibility = View.GONE
                startSearchingAnimation()
            }

            CallState.SPEAKING -> {
                statusText.text = "AI Speaking"
                subtitleText.text = "Neural response"
                volumeVisualizer.visibility = View.VISIBLE
                startSpeakingAnimation()
            }

            CallState.LISTENING -> {
                statusText.text = "Listening"
                subtitleText.text = "Waiting for input"
            }
        }
    }

    private fun startRecordingAnimation() {
        recordingAnimator = ObjectAnimator.ofFloat(recordingRing, "alpha", 0.3f, 1f)
        recordingAnimator?.apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun startSpeakingAnimation() {
        // Simulate speaking volume levels
        val speakingRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    val volume = Random.nextFloat() * 80f + 10f
                    updateVolumeVisualization(volume)
                    volumeLevelValue = volume
                    handler.postDelayed(this, 100)
                } else {
                    // Reset volume when done
                    liquidMorphingView.setVolumeLevel(0f)
                }
            }
        }
        handler.post(speakingRunnable)
    }
    
    private fun startSearchingAnimation() {
        // Animate status text with search indication
        val searchingRunnable = object : Runnable {
            var dotCount = 0
            override fun run() {
                if (currentState == CallState.SEARCHING) {
                    dotCount = (dotCount + 1) % 4
                    val dots = ".".repeat(dotCount)
                    statusText.text = "Searching the web$dots"
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(searchingRunnable)
    }
    

    private fun stopAllAnimations() {
        recordingAnimator?.cancel()
        ambientGlowAnimator?.cancel()
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        volumeAnimators.forEach { it.cancel() }
        volumeAnimators.clear()

        recordingRing.alpha = 0f
        volumeLevelValue = 0f

        // Reset the liquid morphing view
        liquidMorphingView.setVolumeLevel(0f)
        liquidMorphingView.setState(LiquidMorphingView.State.IDLE)
    }

    private fun toggleMute() {
        isMuted = !isMuted
        provideTactileFeedback()

        if (isMuted) {
            muteStrike.visibility = View.VISIBLE
            muteLabel.text = "Unmute"
            micHead.alpha = 0.5f
            micStand.alpha = 0.5f
            micBase.alpha = 0.5f
        } else {
            muteStrike.visibility = View.GONE
            muteLabel.text = "Mute"
            micHead.alpha = 1f
            micStand.alpha = 1f
            micBase.alpha = 1f
        }

        Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun endCall() {
        provideTactileFeedback()

        if (isRecording) {
            stopRecording()
        }

        if (isPlaying) {
            cleanupAudioTrack()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        stopAllAnimations()
        finish()
    }

    private fun provideTactileFeedback() {
        HapticManager.mediumVibration(this)
    }

    private fun showError(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } else {
            handler.post {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        Timber.e("AudioAiActivity Error: $message")
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()

        audioRecord?.release()
        cleanupAudioTrack()
        mediaPlayer?.release()

        stopAllAnimations()
    }

    override fun onPause() {
        super.onPause()

        if (isRecording) {
            stopRecording()
        }

        if (isPlaying) {
            audioTrack?.pause()
            mediaPlayer?.pause()
        }
    }

    override fun onResume() {
        super.onResume()

        if (isPlaying && mediaPlayer != null) {
            mediaPlayer?.start()
        }
    }
}