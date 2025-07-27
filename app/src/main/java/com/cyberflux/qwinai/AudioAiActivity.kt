package com.cyberflux.qwinai

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.PrefsManager
import com.cyberflux.qwinai.views.LiquidMorphingView
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

    // Audio Components - KEEPING ALL EXISTING LOGIC
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false

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

    // Playback audio configuration - UNCHANGED
    private val playbackSampleRate = 44100
    private val playbackChannelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val playbackAudioFormat = AudioFormat.ENCODING_PCM_16BIT

    // API Settings - UNCHANGED
    private val audioApiFormat = "wav"
    private val voiceType = "coral"

    enum class CallState {
        IDLE, RECORDING, PROCESSING, SPEAKING, LISTENING
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_ai)

        initializeViews()
        setupClickListeners()
        setupInitialAnimations()
        checkAudioPermission()
        createVolumeVisualizer()

        // Set initial state
        setState(CallState.IDLE)
    }

    private fun initializeViews() {
        // Main UI components
        ambientGlow = findViewById(R.id.ambientGlow)
        liquidMorphingView = findViewById(R.id.liquidMorphingView)
        statusText = findViewById(R.id.statusText)
        subtitleText = findViewById(R.id.subtitleText)
        volumeVisualizer = findViewById(R.id.volumeVisualizer)
        statusDot = findViewById(R.id.statusDot)

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

        // Add beautiful touch animations
        setupTouchAnimations()
    }

    private fun setupTouchAnimations() {
        listOf(muteButton, recordButton, exitButton).forEach { button ->
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

            // Process the recorded audio
            recordedAudioData?.let { audioData ->
                processAudioWithAI(audioData)
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

    // KEEPING ALL EXISTING API PROCESSING LOGIC UNCHANGED
    fun processAudioWithAI(audioData: ByteArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    setState(CallState.PROCESSING)
                }

                val base64Audio = java.util.Base64.getEncoder().encodeToString(audioData)
                val requestJson = createAudioApiRequest(base64Audio)
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
                        val minBufferSize = AudioTrack.getMinBufferSize(
                            playbackSampleRate, playbackChannelConfig, playbackAudioFormat
                        )

                        withContext(Dispatchers.Main) {
                            audioTrack = AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                playbackSampleRate,
                                playbackChannelConfig,
                                playbackAudioFormat,
                                minBufferSize,
                                AudioTrack.MODE_STREAM
                            )
                            audioTrack?.play()
                            isPlaying = true
                            setState(CallState.SPEAKING)
                        }

                        // Buffer to accumulate audio bytes
                        val audioBuffer = ByteArrayOutputStream()

                        while (true) {
                            val line = reader.readLine() ?: break
                            Timber.d("Received line: $line")

                            try {
                                // Skip empty lines
                                if (line.isBlank()) continue

                                // Handle SSE format (lines starting with "data: ")
                                val jsonStr = if (line.startsWith("data: ")) {
                                    line.substring(6) // Remove "data: " prefix
                                } else {
                                    line
                                }

                                val json = JSONObject(jsonStr)
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
                                                audioTrack?.write(audioBytes, 0, audioBytes.size)
                                                audioBuffer.write(audioBytes)
                                            }

                                            // For debugging - log transcript if available
                                            val transcript = audio.optString("transcript")
                                            if (!transcript.isNullOrEmpty()) {
                                                Timber.d("Transcript: $transcript")
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
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing streaming response: ${e.message}")
                            }
                        }

                        // Log total audio data received
                        val totalAudioBytes = audioBuffer.toByteArray()
                        Timber.d("Total audio data received: ${totalAudioBytes.size} bytes")

                        // If we didn't receive any audio data, but the request was successful,
                        // the API might be returning only transcripts without audio
                        if (totalAudioBytes.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                showError("No audio data received from API. Check if your API key has TTS capabilities.")
                            }
                        }

                        withContext(Dispatchers.Main) {
                            audioTrack?.stop()
                            audioTrack?.release()
                            audioTrack = null
                            isPlaying = false
                            setState(CallState.IDLE)
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

    private fun createAudioApiRequest(base64Audio: String): String {
        val requestJson = JSONObject().apply {
            put("model", "gpt-4o-audio-preview")

            // Include both text and audio modalities (required by API)
            put("modalities", JSONArray().apply {
                put("text")
                put("audio")
            })

            // Audio config at root level - Use a valid voice type
            put("audio", JSONObject().apply {
                put("format", "pcm16")
                put("voice", voiceType)
            })

            // Audio-only message format
            put("messages", JSONArray().apply {
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
            })

            // Standard parameters
            put("max_tokens", 512)
            put("temperature", 0.7)
            put("stream", true) // Enable streaming
        }

        val requestString = requestJson.toString()
        Timber.d("Audio API request created:")
        Timber.d("- Model: gpt-4o-audio-preview")
        Timber.d("- Modalities: [text, audio]")
        Timber.d("- Audio format: pcm16")
        Timber.d("- Audio voice: $voiceType")
        Timber.d("- Audio data length: ${base64Audio.length}")
        Timber.d("- Stream: true")

        return requestString
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
            CallState.SPEAKING -> LiquidMorphingView.State.SPEAKING
            CallState.LISTENING -> LiquidMorphingView.State.LISTENING
        }
        liquidMorphingView.animateToState(morphState)

        when (state) {
            CallState.IDLE -> {
                statusText.text = "Tap to speak"
                subtitleText.text = "Neural Voice Assistant"
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
                subtitleText.text = "AI is thinking"
                volumeVisualizer.visibility = View.GONE
                recordingRing.visibility = View.GONE
                // Processing animation is now handled by the liquid morphing view
            }

            CallState.SPEAKING -> {
                statusText.text = "AI Speaking"
                subtitleText.text = "Neural response"
                volumeVisualizer.visibility = View.VISIBLE
                startSpeakingAnimation()
            }

            CallState.LISTENING -> {
                statusText.text = "Listening"
                // Listening animation is now handled by the liquid morphing view
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
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
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
        audioTrack?.release()
        mediaPlayer?.release()

        stopAllAnimations()
    }

    override fun onPause() {
        super.onPause()

        if (isRecording) {
            stopRecording()
        }

        if (isPlaying) {
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