package com.cyberflux.qwinai.utils

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

class UltraAudioRecordingManager(
    private val context: Context,
    private val parentView: ViewGroup,
    private val callback: AudioRecordingCallback
) {

    interface AudioRecordingCallback {
        fun onRecordingStarted()
        fun onRecordingFinished(audioData: ByteArray?, transcription: String?)
        fun onRecordingCancelled()
        fun onRecordingError(error: String)
        fun onTranscriptionUpdate(partialText: String)
    }

    enum class RecordingMode {
        SPEECH_TO_TEXT,    // Uses SpeechRecognizer (original approach)
        REAL_AUDIO,        // Uses AudioRecord for real audio (new approach)
        HYBRID             // Uses both for maximum compatibility
    }

    // UI Components
    private var overlayView: ConstraintLayout? = null
    private var ultraRecordingCard: CardView? = null
    private var glassBackground: View? = null
    private var shimmerOverlay: View? = null
    private var outerPulseRing: View? = null
    private var middlePulseRing: View? = null
    private var innerCore: View? = null
    private var micBreathingGlow: View? = null
    private var dynamicAudioBars: LinearLayout? = null
    private var ultraStatusText: TextView? = null
    private var ultraSubtitleText: TextView? = null
    private var transcriptCard: CardView? = null
    private var realtimeTranscript: TextView? = null
    private var ultraRecordingTimer: TextView? = null
    private var recordingDot: View? = null
    private var ultraCancelButton: CardView? = null
    private var ultraSendButton: CardView? = null
    private var ultraSendProgress: ProgressBar? = null
    private var floatingParticlesContainer: FrameLayout? = null

    // Audio Recording Components
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private var recordingMode = RecordingMode.HYBRID
    private var recordedAudioData: ByteArray? = null
    private var lastTranscription = ""

    // Animation Components
    private val animationScope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var pulseAnimators = mutableListOf<ObjectAnimator>()
    private var particleAnimators = mutableListOf<ObjectAnimator>()
    private var audioBarAnimators = mutableListOf<ValueAnimator>()
    private var shimmerAnimator: ObjectAnimator? = null
    private var breathingAnimator: ObjectAnimator? = null

    // Audio Settings
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 201
        private const val MAX_RECORDING_DURATION_MS = 60000L // 1 minute max
        private const val AUDIO_BAR_COUNT = 12
    }

    fun startRecording(mode: RecordingMode = RecordingMode.HYBRID) {
        if (isRecording) {
            Timber.w("Recording already in progress")
            return
        }

        recordingMode = mode
        
        // Check permissions
        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }

        try {
            createUltraModernOverlay()
            showRecordingOverlay()
            initializeAudioComponents()
            startRecordingProcess()
            startUltraAnimations()
            callback.onRecordingStarted()
            
            Timber.d("Ultra audio recording started with mode: $mode")
        } catch (e: Exception) {
            Timber.e(e, "Error starting ultra audio recording: ${e.message}")
            callback.onRecordingError("Failed to start recording: ${e.message}")
        }
    }

    private fun createUltraModernOverlay() {
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.ultra_audio_recording_overlay, parentView, false) as ConstraintLayout
        
        // Initialize UI components
        ultraRecordingCard = overlayView?.findViewById(R.id.ultraRecordingCard)
        glassBackground = overlayView?.findViewById(R.id.glassBackground)
        shimmerOverlay = overlayView?.findViewById(R.id.shimmerOverlay)
        outerPulseRing = overlayView?.findViewById(R.id.outerPulseRing)
        middlePulseRing = overlayView?.findViewById(R.id.middlePulseRing)
        innerCore = overlayView?.findViewById(R.id.innerCore)
        micBreathingGlow = overlayView?.findViewById(R.id.micBreathingGlow)
        dynamicAudioBars = overlayView?.findViewById(R.id.dynamicAudioBars)
        ultraStatusText = overlayView?.findViewById(R.id.ultraStatusText)
        ultraSubtitleText = overlayView?.findViewById(R.id.ultraSubtitleText)
        transcriptCard = overlayView?.findViewById(R.id.transcriptCard)
        realtimeTranscript = overlayView?.findViewById(R.id.realtimeTranscript)
        ultraRecordingTimer = overlayView?.findViewById(R.id.ultraRecordingTimer)
        recordingDot = overlayView?.findViewById(R.id.recordingDot)
        ultraCancelButton = overlayView?.findViewById(R.id.ultraCancelButton)
        ultraSendButton = overlayView?.findViewById(R.id.ultraSendButton)
        ultraSendProgress = overlayView?.findViewById(R.id.ultraSendProgress)
        floatingParticlesContainer = overlayView?.findViewById(R.id.floatingParticlesContainer)

        setupClickListeners()
        createDynamicAudioBars()
        createFloatingParticles()
    }

    private fun setupClickListeners() {
        ultraCancelButton?.setOnClickListener {
            provideTactileFeedback()
            cancelRecording()
        }

        ultraSendButton?.setOnClickListener {
            provideTactileFeedback()
            finishRecording()
        }

        // Add beautiful touch animations
        listOf(ultraCancelButton, ultraSendButton).forEach { button ->
            button?.setOnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate()
                            .scaleX(0.9f)
                            .scaleY(0.9f)
                            .setDuration(150)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
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

    private fun createDynamicAudioBars() {
        dynamicAudioBars?.removeAllViews()
        
        for (i in 0 until AUDIO_BAR_COUNT) {
            val bar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(context, 4),
                    dpToPx(context, 8)
                ).apply {
                    marginStart = dpToPx(context, 1)
                    marginEnd = dpToPx(context, 1)
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.white))
                alpha = 0.3f
            }
            dynamicAudioBars?.addView(bar)
        }
    }

    private fun createFloatingParticles() {
        floatingParticlesContainer?.removeAllViews()
        
        for (i in 0 until 8) {
            val particle = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(context, 4),
                    dpToPx(context, 4)
                )
                background = ContextCompat.getDrawable(context, R.drawable.breathing_glow_circle)
                alpha = 0.6f
            }
            
            // Position particles randomly
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            
            particle.x = Random.nextFloat() * screenWidth
            particle.y = Random.nextFloat() * screenHeight * 0.6f
            
            floatingParticlesContainer?.addView(particle)
        }
    }

    private fun showRecordingOverlay() {
        overlayView?.let { overlay ->
            parentView.addView(overlay)
            
            // Entrance animation
            overlay.alpha = 0f
            overlay.scaleX = 0.8f
            overlay.scaleY = 0.8f
            
            overlay.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun initializeAudioComponents() {
        when (recordingMode) {
            RecordingMode.SPEECH_TO_TEXT, RecordingMode.HYBRID -> {
                initializeSpeechRecognizer()
            }
            RecordingMode.REAL_AUDIO -> {
                initializeAudioRecord()
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Timber.d("SpeechRecognizer initialized successfully")
        } else {
            Timber.w("SpeechRecognizer not available, falling back to AudioRecord")
            if (recordingMode == RecordingMode.HYBRID) {
                recordingMode = RecordingMode.REAL_AUDIO
                initializeAudioRecord()
            }
        }
    }

    private fun initializeAudioRecord() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }
            
            Timber.d("AudioRecord initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AudioRecord: ${e.message}")
            throw e
        }
    }

    private fun startRecordingProcess() {
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        
        when (recordingMode) {
            RecordingMode.SPEECH_TO_TEXT -> {
                startSpeechRecognition()
            }
            RecordingMode.REAL_AUDIO -> {
                startAudioRecording()
            }
            RecordingMode.HYBRID -> {
                startSpeechRecognition()
                startAudioRecording()
            }
        }
        
        startTimerUpdate()
    }

    private fun startSpeechRecognition() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        speechRecognizer?.startListening(intent)
        Timber.d("Speech recognition started")
    }

    private fun startAudioRecording() {
        animationScope.launch(Dispatchers.IO) {
            try {
                audioRecord?.startRecording()
                recordAudio()
            } catch (e: Exception) {
                Timber.e(e, "Error during audio recording: ${e.message}")
                launch(Dispatchers.Main) {
                    callback.onRecordingError("Audio recording failed: ${e.message}")
                }
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
                    handler.post {
                        updateAudioVisualization(volume)
                    }
                }
            }
            
            // Create WAV file with proper headers
            val rawAudioData = audioData.toByteArray()
            recordedAudioData = createWavFile(rawAudioData)
            
            Timber.d("Audio recording completed: ${recordedAudioData?.size} bytes")
            
        } catch (e: Exception) {
            Timber.e(e, "Error during audio recording: ${e.message}")
            handler.post {
                callback.onRecordingError("Recording error: ${e.message}")
            }
        }
    }

    private fun calculateVolumeLevel(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                sum += abs(sample.toDouble())
            }
        }
        val average = sum / (bytesRead / 2)
        return (20 * log10(average + 1)).toFloat().coerceIn(0f, 100f)
    }

    private fun createWavFile(audioData: ByteArray): ByteArray {
        val wavHeader = createWavHeader(audioData.size)
        val wavFile = ByteArray(wavHeader.size + audioData.size)
        
        System.arraycopy(wavHeader, 0, wavFile, 0, wavHeader.size)
        System.arraycopy(audioData, 0, wavFile, wavHeader.size, audioData.size)
        
        return wavFile
    }

    private fun createWavHeader(audioDataSize: Int): ByteArray {
        val headerSize = 44
        val totalSize = headerSize + audioDataSize - 8
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        
        return ByteArray(headerSize).apply {
            "RIFF".toByteArray().copyInto(this, 0)
            writeInt32(this, 4, totalSize)
            "WAVE".toByteArray().copyInto(this, 8)
            "fmt ".toByteArray().copyInto(this, 12)
            writeInt32(this, 16, 16)
            writeInt16(this, 20, 1)
            writeInt16(this, 22, channels.toShort())
            writeInt32(this, 24, sampleRate)
            writeInt32(this, 28, byteRate)
            writeInt16(this, 32, (channels * bitsPerSample / 8).toShort())
            writeInt16(this, 34, bitsPerSample.toShort())
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

    private fun startUltraAnimations() {
        startPulseAnimations()
        startShimmerAnimation()
        startBreathingAnimation()
        startParticleAnimations()
    }

    private fun startPulseAnimations() {
        // Outer pulse ring
        outerPulseRing?.let { ring ->
            val animator = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.2f, 1f)
            animator.duration = 2000
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.start()
            pulseAnimators.add(animator)
            
            val animatorY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.2f, 1f)
            animatorY.duration = 2000
            animatorY.repeatCount = ValueAnimator.INFINITE
            animatorY.interpolator = AccelerateDecelerateInterpolator()
            animatorY.start()
            pulseAnimators.add(animatorY)
        }
        
        // Middle pulse ring
        middlePulseRing?.let { ring ->
            val animator = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.15f, 1f)
            animator.duration = 1500
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.startDelay = 300
            animator.start()
            pulseAnimators.add(animator)
            
            val animatorY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.15f, 1f)
            animatorY.duration = 1500
            animatorY.repeatCount = ValueAnimator.INFINITE
            animatorY.interpolator = AccelerateDecelerateInterpolator()
            animatorY.startDelay = 300
            animatorY.start()
            pulseAnimators.add(animatorY)
        }
        
        // Inner core
        innerCore?.let { core ->
            val animator = ObjectAnimator.ofFloat(core, "scaleX", 1f, 1.1f, 1f)
            animator.duration = 1000
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.startDelay = 600
            animator.start()
            pulseAnimators.add(animator)
            
            val animatorY = ObjectAnimator.ofFloat(core, "scaleY", 1f, 1.1f, 1f)
            animatorY.duration = 1000
            animatorY.repeatCount = ValueAnimator.INFINITE
            animatorY.interpolator = AccelerateDecelerateInterpolator()
            animatorY.startDelay = 600
            animatorY.start()
            pulseAnimators.add(animatorY)
        }
    }

    private fun startShimmerAnimation() {
        shimmerOverlay?.let { shimmer ->
            shimmerAnimator = ObjectAnimator.ofFloat(shimmer, "translationX", -200f, 200f)
            shimmerAnimator?.duration = 2500
            shimmerAnimator?.repeatCount = ValueAnimator.INFINITE
            shimmerAnimator?.interpolator = LinearInterpolator()
            shimmerAnimator?.start()
        }
    }

    private fun startBreathingAnimation() {
        micBreathingGlow?.let { glow ->
            breathingAnimator = ObjectAnimator.ofFloat(glow, "alpha", 0.3f, 0.8f, 0.3f)
            breathingAnimator?.duration = 1800
            breathingAnimator?.repeatCount = ValueAnimator.INFINITE
            breathingAnimator?.interpolator = AccelerateDecelerateInterpolator()
            breathingAnimator?.start()
        }
    }

    private fun startParticleAnimations() {
        floatingParticlesContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val particle = container.getChildAt(i)
                
                // Floating animation
                val floatAnimator = ObjectAnimator.ofFloat(particle, "translationY", 0f, -50f, 0f)
                floatAnimator.duration = (2000 + Random.nextLong(1000))
                floatAnimator.repeatCount = ValueAnimator.INFINITE
                floatAnimator.startDelay = Random.nextLong(1000)
                floatAnimator.interpolator = AccelerateDecelerateInterpolator()
                floatAnimator.start()
                particleAnimators.add(floatAnimator)
                
                // Fade animation
                val fadeAnimator = ObjectAnimator.ofFloat(particle, "alpha", 0.2f, 0.8f, 0.2f)
                fadeAnimator.duration = (1500 + Random.nextLong(1000))
                fadeAnimator.repeatCount = ValueAnimator.INFINITE
                fadeAnimator.startDelay = Random.nextLong(500)
                fadeAnimator.start()
                particleAnimators.add(fadeAnimator)
            }
        }
    }

    private fun startTimerUpdate() {
        val timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    val minutes = elapsed / 60000
                    val seconds = (elapsed % 60000) / 1000
                    
                    ultraRecordingTimer?.text = String.format("%02d:%02d", minutes, seconds)
                    
                    // Auto-stop after max duration
                    if (elapsed >= MAX_RECORDING_DURATION_MS) {
                        finishRecording()
                        return
                    }
                    
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(timerRunnable)
    }

    private fun updateAudioVisualization(volume: Float) {
        // Update audio bars based on volume
        dynamicAudioBars?.let { container ->
            val barCount = container.childCount
            val activeBarCount = ((volume / 100f) * barCount).toInt()
            
            for (i in 0 until barCount) {
                val bar = container.getChildAt(i)
                val targetAlpha = if (i < activeBarCount) {
                    0.8f + (volume / 100f) * 0.2f
                } else {
                    0.2f
                }
                
                val targetHeight = if (i < activeBarCount) {
                    dpToPx(context, (12 + (volume / 100f) * 20).toInt())
                } else {
                    dpToPx(context, 8)
                }
                
                // Smooth animation
                bar.animate()
                    .alpha(targetAlpha)
                    .setDuration(100)
                    .start()
                
                val params = bar.layoutParams as LinearLayout.LayoutParams
                val animator = ValueAnimator.ofInt(params.height, targetHeight)
                animator.duration = 100
                animator.addUpdateListener { animation ->
                    params.height = animation.animatedValue as Int
                    bar.layoutParams = params
                }
                animator.start()
            }
        }
        
        // Pulse the recording dot
        recordingDot?.animate()
            ?.alpha(0.3f + (volume / 100f) * 0.7f)
            ?.setDuration(50)
            ?.start()
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Timber.d("Ready for speech")
                ultraStatusText?.text = "Listening..."
                ultraSubtitleText?.text = "Speak now and I'll understand you"
            }
            
            override fun onBeginningOfSpeech() {
                Timber.d("Beginning of speech")
                ultraStatusText?.text = "Processing..."
                ultraSubtitleText?.text = "I can hear you speaking"
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Use RMS value for audio visualization if not using AudioRecord
                if (recordingMode != RecordingMode.REAL_AUDIO) {
                    val volume = (rmsdB + 20f).coerceIn(0f, 100f)
                    updateAudioVisualization(volume)
                }
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                Timber.d("End of speech")
                ultraStatusText?.text = "Processing..."
                ultraSubtitleText?.text = "Understanding your request..."
            }
            
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
                
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Use audio data if available in hybrid mode
                    if (recordingMode == RecordingMode.HYBRID && recordedAudioData != null) {
                        callback.onRecordingFinished(recordedAudioData, null)
                    } else {
                        callback.onRecordingError("No speech detected. Please try again.")
                    }
                } else {
                    callback.onRecordingError(errorMessage)
                }
                
                hideRecordingOverlay()
            }
            
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    lastTranscription = matches[0]
                    Timber.d("Speech recognition result: $lastTranscription")
                    
                    // Show final transcription
                    realtimeTranscript?.text = lastTranscription
                    transcriptCard?.visibility = View.VISIBLE
                    
                    callback.onRecordingFinished(recordedAudioData, lastTranscription)
                } else {
                    callback.onRecordingError("No speech recognized")
                }
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    if (partialText.isNotEmpty()) {
                        // Show real-time transcription
                        realtimeTranscript?.text = partialText
                        transcriptCard?.visibility = View.VISIBLE
                        
                        callback.onTranscriptionUpdate(partialText)
                        Timber.d("Partial speech result: $partialText")
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
    }

    fun finishRecording() {
        if (!isRecording) return
        
        isRecording = false
        
        // Show processing state
        ultraSendProgress?.visibility = View.VISIBLE
        ultraSendButton?.findViewById<android.widget.ImageView>(R.id.sendIcon)?.visibility = View.GONE
        ultraStatusText?.text = "Processing..."
        ultraSubtitleText?.text = "Finalizing your recording..."
        
        try {
            // Stop audio components
            speechRecognizer?.stopListening()
            audioRecord?.stop()
            
            // Wait briefly for final results
            handler.postDelayed({
                // Use transcription if available, otherwise use audio data
                val transcription = if (lastTranscription.isNotEmpty()) lastTranscription else null
                callback.onRecordingFinished(recordedAudioData, transcription)
                hideRecordingOverlay()
            }, 1000)
            
        } catch (e: Exception) {
            Timber.e(e, "Error finishing recording: ${e.message}")
            callback.onRecordingError("Error finishing recording: ${e.message}")
            hideRecordingOverlay()
        }
    }

    fun cancelRecording() {
        if (!isRecording) return
        
        isRecording = false
        
        try {
            speechRecognizer?.cancel()
            audioRecord?.stop()
            callback.onRecordingCancelled()
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling recording: ${e.message}")
        }
        
        hideRecordingOverlay()
    }

    private fun hideRecordingOverlay() {
        overlayView?.let { overlay ->
            overlay.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(300)
                .withEndAction {
                    parentView.removeView(overlay)
                    cleanup()
                }
                .start()
        }
    }

    private fun cleanup() {
        // Stop all animations
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        
        particleAnimators.forEach { it.cancel() }
        particleAnimators.clear()
        
        audioBarAnimators.forEach { it.cancel() }
        audioBarAnimators.clear()
        
        shimmerAnimator?.cancel()
        breathingAnimator?.cancel()
        
        // Release audio resources
        try {
            speechRecognizer?.destroy()
            audioRecord?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up audio resources: ${e.message}")
        }
        
        // Clear references
        speechRecognizer = null
        audioRecord = null
        overlayView = null
        
        Timber.d("Ultra audio recording manager cleaned up")
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        if (context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }

    private fun provideTactileFeedback() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing tactile feedback: ${e.message}")
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}