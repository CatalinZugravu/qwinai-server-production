package com.cyberflux.qwinai.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber

/**
 * Centralized haptic feedback manager that respects user settings
 */
object HapticManager {

    /**
     * Check if haptic feedback is enabled in settings
     */
    private fun isHapticEnabled(context: Context): Boolean {
        // Check both preference stores to handle existing inconsistencies
        val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val qwinaiPrefs = context.getSharedPreferences("qwinai_prefs", Context.MODE_PRIVATE)
        
        // First check app_settings (used by SettingsActivity)
        if (appSettings.contains("haptic_feedback_enabled")) {
            return appSettings.getBoolean("haptic_feedback_enabled", true)
        }
        
        // Fallback to qwinai_prefs (used by PrefsManager)
        return qwinaiPrefs.getBoolean("haptic_feedback_enabled", true)
    }

    /**
     * Get vibrator instance using modern API on Android 12+ (API 31)
     */
    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting vibrator service")
            null
        }
    }

    /**
     * Provide light haptic feedback (15-30ms)
     */
    fun lightVibration(context: Context) {
        if (!isHapticEnabled(context)) return
        
        try {
            val vibrator = getVibrator(context)
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(15)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing light haptic feedback")
        }
    }

    /**
     * Provide medium haptic feedback (40-50ms)
     */
    fun mediumVibration(context: Context) {
        if (!isHapticEnabled(context)) return
        
        try {
            val vibrator = getVibrator(context)
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing medium haptic feedback")
        }
    }

    /**
     * Provide strong haptic feedback (80ms)
     */
    fun strongVibration(context: Context) {
        if (!isHapticEnabled(context)) return
        
        try {
            val vibrator = getVibrator(context)
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(80)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing strong haptic feedback")
        }
    }

    /**
     * Provide custom duration haptic feedback
     */
    fun customVibration(context: Context, durationMs: Long) {
        if (!isHapticEnabled(context)) return
        
        try {
            val vibrator = getVibrator(context)
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing custom haptic feedback")
        }
    }

    /**
     * Provide pattern haptic feedback
     */
    fun patternVibration(context: Context, pattern: LongArray) {
        if (!isHapticEnabled(context)) return
        
        try {
            val vibrator = getVibrator(context)
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing pattern haptic feedback")
        }
    }

    /**
     * Quick feedback for button presses and UI interactions
     */
    fun quickFeedback(context: Context) = lightVibration(context)

    /**
     * Selection feedback for item selection and toggles
     */
    fun selectionFeedback(context: Context) = mediumVibration(context)

    /**
     * Success feedback for completed actions
     */
    fun successFeedback(context: Context) = strongVibration(context)

    /**
     * Error feedback - use medium vibration for errors
     */
    fun errorFeedback(context: Context) = mediumVibration(context)
}