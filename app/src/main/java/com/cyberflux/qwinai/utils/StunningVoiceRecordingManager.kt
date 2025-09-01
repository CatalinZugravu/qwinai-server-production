package com.cyberflux.qwinai.utils

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.cyberflux.qwinai.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/**
 * Stunning voice recording manager using bottom sheet dialog approach
 * Inspired by StartActivity but more beautiful and functional
 */
class StunningVoiceRecordingManager(
    private val activity: Activity,
    private val callback: VoiceRecordingCallback
) {

    private var dialog: BottomSheetDialog? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    
    // UI Components
    private var statusText: TextView? = null
    private var subtitleText: TextView? = null
    private var voiceAnimation: LottieAnimationView? = null
    private var transcriptCard: MaterialCardView? = null
    private var realtimeTranscript: TextView? = null
    private var timerText: TextView? = null
    private var recordingDot: View? = null
    private var cancelButton: MaterialButton? = null
    private var sendButton: MaterialButton? = null
    private var waveBars: LinearLayout? = null
    private var breathingGlow: View? = null
    
    // Animation and timer
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var breathingAnimator: ValueAnimator? = null
    private var waveBarAnimators = mutableListOf<ValueAnimator>()
    
    interface VoiceRecordingCallback {
        fun onRecordingStarted()
        fun onRecordingFinished(transcription: String?)
        fun onRecordingCancelled()
        fun onRecordingError(error: String)
        fun onTranscriptionUpdate(partialText: String)
    }

    fun startRecording() {
        if (isRecording) {
            Timber.w("Voice recording already in progress")
            return
        }

        // Check permissions
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
            return
        }

        try {
            createAndShowDialog()
            initializeSpeechRecognizer()
            startSpeechRecognition()
            startAnimations()
            startTimer()
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            callback.onRecordingStarted()
            
            // Haptic feedback
            provideHapticFeedback()
            
            Timber.d("🎙️ Stunning voice recording started")
        } catch (e: Exception) {
            Timber.e(e, "Error starting stunning voice recording: ${e.message}")
            callback.onRecordingError("Failed to start recording: ${e.message}")
        }
    }

    private fun createAndShowDialog() {
        dialog = BottomSheetDialog(activity, R.style.BottomSheetDialogTheme)
        val view = activity.layoutInflater.inflate(R.layout.stunning_voice_input_dialog, null)
        dialog?.setContentView(view)
        
        // Initialize UI components
        statusText = view.findViewById(R.id.stunningStatusText)
        subtitleText = view.findViewById(R.id.stunningSubtitleText)
        voiceAnimation = view.findViewById(R.id.stunningVoiceAnimation)
        transcriptCard = view.findViewById(R.id.stunningTranscriptCard)
        realtimeTranscript = view.findViewById(R.id.stunningRealtimeTranscript)
        timerText = view.findViewById(R.id.stunningRecordingTimer)
        recordingDot = view.findViewById(R.id.stunningRecordingDot)
        cancelButton = view.findViewById(R.id.stunningCancelButton)
        sendButton = view.findViewById(R.id.stunningSendButton)
        waveBars = view.findViewById(R.id.stunningWaveBars)
        breathingGlow = view.findViewById(R.id.breathingGlow)
        
        // Setup button listeners
        cancelButton?.setOnClickListener {
            cancelRecording()
        }
        
        sendButton?.setOnClickListener {
            finishRecording()
        }
        
        // Prevent dialog dismissal by outside touch during recording
        dialog?.setCanceledOnTouchOutside(false)
        
        dialog?.show()
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
        }
        
        speechRecognizer?.startListening(intent)
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Timber.d("🎙️ Ready for speech")
                updateStatus("Listening...", "Speak naturally, I'm listening attentively")
            }

            override fun onBeginningOfSpeech() {
                Timber.d("🎙️ Speech detection started")
                updateStatus("🗣️ Speaking...", "I can hear you clearly")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Update wave bars based on audio level
                updateWaveVisualization(rmsdB)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    showTranscript(partialText)
                    callback.onTranscriptionUpdate(partialText)
                    Timber.d("🎙️ Partial result: $partialText")
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val finalText = matches[0]
                    Timber.d("🎙️ Final result: $finalText")
                    finishWithText(finalText)
                } else {
                    Timber.w("🎙️ No speech results received")
                    callback.onRecordingError("No speech detected")
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                
                Timber.e("🎙️ Speech recognition error: $errorMessage")
                
                if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    updateStatus("🤔 No speech detected", "Try speaking more clearly")
                } else {
                    callback.onRecordingError(errorMessage)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }

            override fun onEndOfSpeech() {
                Timber.d("🎙️ End of speech detected")
                updateStatus("✨ Processing...", "Analyzing your speech")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not used
            }
        }
    }

    private fun startAnimations() {
        // Start Lottie animation
        voiceAnimation?.playAnimation()
        
        // Start breathing glow animation
        breathingAnimator = ValueAnimator.ofFloat(0.3f, 1.0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                breathingGlow?.alpha = animator.animatedValue as Float
            }
            start()
        }
        
        // Create elegant wave bars
        createElegantWaveBars()
    }

    private fun createElegantWaveBars() {
        waveBars?.removeAllViews()
        waveBarAnimators.clear()
        
        val barCount = 12
        val barWidth = 4
        val barSpacing = 6
        
        repeat(barCount) { index ->
            val bar = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    barWidth.dpToPx(activity),
                    20.dpToPx(activity)
                ).apply {
                    marginStart = if (index > 0) barSpacing.dpToPx(activity) else 0
                }
                setBackgroundColor(ContextCompat.getColor(activity, R.color.white))
                alpha = 0.7f
                scaleY = 0.3f
                pivotY = layoutParams.height.toFloat()
            }
            
            waveBars?.addView(bar)
        }
    }

    private fun updateWaveVisualization(rmsdB: Float) {
        val barCount = waveBars?.childCount ?: return
        
        // Normalize RMS value (-60 to 0 dB range)
        val normalizedVolume = ((rmsdB + 60f) / 60f).coerceIn(0f, 1f)
        
        for (i in 0 until barCount) {
            val bar = waveBars?.getChildAt(i) ?: continue
            
            // Create wave pattern with position-based variation
            val position = i.toFloat() / barCount.toFloat()
            val wave = sin((position * Math.PI * 2) + (System.currentTimeMillis() % 2000) / 1000.0 * Math.PI).toFloat()
            val amplitude = 0.3f + (normalizedVolume * 0.7f) + (wave * 0.2f)
            
            // Animate to new scale
            ValueAnimator.ofFloat(bar.scaleY, amplitude).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    bar.scaleY = animator.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun startTimer() {
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
                    
                    timerText?.text = String.format("%02d:%02d", minutes, seconds)
                    timerHandler?.postDelayed(this, 1000)
                }
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    private fun updateStatus(status: String, subtitle: String) {
        statusText?.text = status
        subtitleText?.text = subtitle
    }

    private fun showTranscript(text: String) {
        if (text.isNotEmpty()) {
            transcriptCard?.visibility = View.VISIBLE
            realtimeTranscript?.text = text
        }
    }

    private fun finishRecording() {
        speechRecognizer?.stopListening()
    }

    private fun finishWithText(text: String) {
        cleanup()
        callback.onRecordingFinished(text)
    }

    fun cancelRecording() {
        cleanup()
        callback.onRecordingCancelled()
    }

    private fun cleanup() {
        isRecording = false
        
        // Stop animations
        breathingAnimator?.cancel()
        waveBarAnimators.forEach { it.cancel() }
        waveBarAnimators.clear()
        voiceAnimation?.cancelAnimation()
        
        // Stop timer
        timerHandler?.removeCallbacks(timerRunnable!!)
        timerHandler = null
        timerRunnable = null
        
        // Cleanup speech recognizer
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        // Dismiss dialog
        dialog?.dismiss()
        dialog = null
        
        Timber.d("🎙️ Stunning voice recording cleaned up")
    }

    private fun provideHapticFeedback() {
        try {
            val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing haptic feedback")
        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 123
    }
}