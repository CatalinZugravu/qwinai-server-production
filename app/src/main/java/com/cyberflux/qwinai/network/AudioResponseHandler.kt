package com.cyberflux.qwinai.network

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Handles audio responses from AI models
 * Manages playback, caching, and conversion
 */
class AudioResponseHandler(private val context: Context) {

    private val audioCache = mutableMapOf<String, File>()
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingId: String? = null

    /**
     * Play audio from a base64 encoded string
     * @param audioBase64 The base64 encoded audio data
     * @param messageId The message ID to associate with this audio
     * @param format The audio format (mp3, wav, etc.)
     * @return Success or failure
     */
    suspend fun playAudioFromBase64(audioBase64: String, messageId: String, format: String): Boolean {
        return try {
            // Validate base64 input
            if (audioBase64.isBlank()) {
                Timber.e("Empty base64 audio data")
                return false
            }

            // Log first 100 characters for debugging
            Timber.d("Base64 audio data preview: ${audioBase64.take(100)}...")
            Timber.d("Base64 audio data length: ${audioBase64.length}")

            // Decode the base64 string to binary data
            val audioData = try {
                // Clean the base64 string first
                val cleanedBase64 = audioBase64.trim()
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace(" ", "")
                
                Base64.decode(cleanedBase64, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Invalid base64 audio data. First 100 chars: ${audioBase64.take(100)}")
                return false
            }

            // Validate audio data size
            if (audioData.isEmpty()) {
                Timber.e("Decoded audio data is empty")
                return false
            }

            // Create a temporary file with the correct extension
            val fileExtension = when (format.lowercase()) {
                "mp3" -> ".mp3"
                "wav" -> ".wav"
                "m4a" -> ".m4a"
                "aac" -> ".aac"
                "3gp" -> ".3gp"
                else -> {
                    Timber.w("Unsupported format '$format', defaulting to mp3")
                    ".mp3"
                }
            }

            val audioFile = createAudioFile(messageId, fileExtension)

            // Write the audio data to the file with proper error handling
            val writeSuccess = withContext(Dispatchers.IO) {
                try {
                    FileOutputStream(audioFile).use { output ->
                        output.write(audioData)
                        output.flush()
                    }
                    true
                } catch (e: IOException) {
                    Timber.e(e, "Failed to write audio file")
                    false
                }
            }

            if (!writeSuccess) {
                return false
            }

            // Verify the file was written correctly
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Timber.e("Audio file was not created properly")
                return false
            }

            // Store in cache
            audioCache[messageId] = audioFile

            // Play the audio
            val playSuccess = playAudio(audioFile, messageId)

            if (playSuccess) {
                Timber.d("Audio playback started for message: $messageId")
            }

            playSuccess
        } catch (e: Exception) {
            Timber.e(e, "Failed to play audio: ${e.message}")
            false
        }
    }

    /**
     * Play audio from a file with enhanced error handling
     * @param audioFile The audio file to play
     * @param messageId The message ID to associate with this audio
     * @return Success or failure
     */
    private fun playAudio(audioFile: File, messageId: String): Boolean {
        try {
            // Validate file before attempting to play
            if (!audioFile.exists()) {
                Timber.e("Audio file does not exist: ${audioFile.absolutePath}")
                return false
            }

            if (audioFile.length() == 0L) {
                Timber.e("Audio file is empty: ${audioFile.absolutePath}")
                return false
            }

            Timber.d("Attempting to play audio file: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")

            // Stop any currently playing audio
            stopAudio()

            // Create and configure new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setOnCompletionListener {
                    Timber.d("Audio playback completed for message: $messageId")
                    stopAudio()
                }

                setOnErrorListener { _, what, extra ->
                    Timber.e("MediaPlayer error: what=$what, extra=$extra, file=${audioFile.absolutePath}")
                    stopAudio()
                    false // Return false to trigger onCompletion
                }

                setOnPreparedListener {
                    Timber.d("MediaPlayer prepared, starting playback")
                    start()
                    currentlyPlayingId = messageId
                }

                try {
                    setDataSource(audioFile.absolutePath)
                    prepareAsync() // Use async preparation to avoid blocking
                } catch (e: IOException) {
                    Timber.e(e, "Failed to set data source: ${audioFile.absolutePath}")
                    release()
                    throw e
                }
            }

            return true
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio file: ${e.message}")
            cleanupFailedPlayback()
            return false
        }
    }

    /**
     * Clean up after failed playback attempt
     */
    private fun cleanupFailedPlayback() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing MediaPlayer after failure")
        }
        mediaPlayer = null
        currentlyPlayingId = null
    }

    /**
     * Stop any currently playing audio
     */
    fun stopAudio() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping audio playback")
            }
        }
        mediaPlayer = null
        currentlyPlayingId = null
    }

    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    /**
     * Get the ID of the currently playing message
     * @return The message ID or null if nothing is playing
     */
    fun getCurrentlyPlayingId(): String? {
        return currentlyPlayingId
    }

    /**
     * Create a temporary file for storing audio
     * @param messageId The message ID to associate with this audio
     * @param extension The file extension (including dot)
     * @return The created file
     */
    private fun createAudioFile(messageId: String, extension: String): File {
        val cacheDir = context.cacheDir
        val audioDir = File(cacheDir, "audio_cache").apply {
            if (!exists()) mkdirs()
        }

        // Use a combination of message ID and random UUID to ensure uniqueness
        val fileName = "audio_${messageId.take(8)}_${UUID.randomUUID().toString().take(8)}$extension"
        return File(audioDir, fileName)
    }

    /**
     * Clear the audio cache
     */
    fun clearCache() {
        stopAudio()

        // Delete all cached files
        audioCache.values.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting cached audio file: ${e.message}")
            }
        }

        audioCache.clear()
    }

    /**
     * Release resources when no longer needed
     */
    fun release() {
        stopAudio()
        clearCache()
    }
}