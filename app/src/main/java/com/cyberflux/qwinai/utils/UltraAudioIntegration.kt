package com.cyberflux.qwinai.utils

import android.app.Activity
import android.view.ViewGroup
import android.widget.Toast
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.model.ChatMessage
import timber.log.Timber

/**
 * Stunning voice recording integration for MainActivity
 * Uses elegant bottom sheet dialog approach like StartActivity but more beautiful
 */
class UltraAudioIntegration(
    private val activity: MainActivity,
    private val parentView: ViewGroup
) {
    
    private var stunningVoiceRecordingManager: StunningVoiceRecordingManager? = null
    private var isRecording = false
    
    fun initialize() {
        stunningVoiceRecordingManager = StunningVoiceRecordingManager(
            activity = activity,
            callback = createVoiceCallback()
        )
        Timber.d("✨ Stunning Voice Integration initialized")
    }
    
    fun startUltraVoiceRecording() {
        if (isRecording) {
            Timber.w("Stunning voice recording already in progress")
            return
        }
        
        try {
            stunningVoiceRecordingManager?.startRecording()
            Timber.d("✨ Stunning voice recording started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start stunning voice recording: ${e.message}")
            Toast.makeText(activity, "Failed to start voice recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun stopRecording() {
        if (isRecording) {
            stunningVoiceRecordingManager?.cancelRecording()
            isRecording = false
        }
    }
    
    fun cancelRecording() {
        if (isRecording) {
            stunningVoiceRecordingManager?.cancelRecording()
            isRecording = false
        }
    }
    
    private fun createVoiceCallback(): StunningVoiceRecordingManager.VoiceRecordingCallback {
        return object : StunningVoiceRecordingManager.VoiceRecordingCallback {
            override fun onRecordingStarted() {
                isRecording = true
                Timber.d("✨ Stunning recording started callback")
            }
            
            override fun onRecordingFinished(transcription: String?) {
                isRecording = false
                
                if (!transcription.isNullOrEmpty()) {
                    // We have text transcription - send as voice message
                    Timber.d("✨ Sending voice message: $transcription")
                    sendVoiceMessage(transcription)
                } else {
                    // No usable data
                    Timber.w("✨ No speech detected")
                    Toast.makeText(activity, "No speech detected. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onRecordingCancelled() {
                isRecording = false
                Timber.d("✨ Stunning recording cancelled")
            }
            
            override fun onRecordingError(error: String) {
                isRecording = false
                Timber.e("✨ Stunning recording error: $error")
                Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            }
            
            override fun onTranscriptionUpdate(partialText: String) {
                // Real-time transcription updates
                Timber.v("✨ Partial transcription: $partialText")
            }
        }
    }
    
    private fun sendVoiceMessage(text: String) {
        if (text.trim().isEmpty()) {
            Toast.makeText(activity, "No speech detected", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            activity.runOnUiThread {
                // Set the voice message flag so the next message will be marked as voice message
                activity.isNextMessageFromVoice = true
                
                // Set the text in the input field
                activity.binding.etInputText.setText(text.trim())
                
                // Trigger the send button programmatically
                // This ensures we use MainActivity's existing message sending logic
                activity.binding.btnSubmitText.performClick()
                
                Timber.d("🎙️ Voice message sent successfully: $text")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error sending voice message: ${e.message}")
            Toast.makeText(activity, "Error sending message: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    fun cleanup() {
        try {
            stunningVoiceRecordingManager = null
            isRecording = false
            Timber.d("✨ Stunning Voice Integration cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up stunning voice integration: ${e.message}")
        }
    }
    
    fun isCurrentlyRecording(): Boolean = isRecording
}

// Note: Integration works by setting text in input field and clicking send button
// This ensures we use MainActivity's existing message processing pipeline