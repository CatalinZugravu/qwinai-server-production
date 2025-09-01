package com.cyberflux.qwinai.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cyberflux.qwinai.R
import timber.log.Timber

/**
 * Base activity that handles theme application for all activities in the app
 */
open class BaseThemedActivity : AppCompatActivity() {
    private val TAG = "BaseThemedActivity"
    private var themeChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // CRITICAL: Apply theme BEFORE calling super.onCreate()
        applyThemeBeforeCreate()

        super.onCreate(savedInstanceState)

        // Register for theme change broadcasts
        registerThemeChangeReceiver()
    }

    /**
     * Apply the current theme based on saved preferences
     * This MUST be called before super.onCreate()
     */
    private fun applyThemeBeforeCreate() {
        try {
            // Material Design 3 uses a single AppTheme that adapts to light/dark mode
            setTheme(R.style.AppTheme)
            Log.d(TAG, "Successfully applied Material Design 3 AppTheme")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme, falling back to default", e)
            setTheme(R.style.AppTheme)
        }
    }
    fun provideHapticFeedback(intensity: Int = 30) {
        try {
            if (PrefsManager.isHapticFeedbackEnabled(this)) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(intensity.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(intensity.toLong())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error providing haptic feedback: ${e.message}")
        }
    }
    /**
     * Register for theme change broadcasts
     */
    private fun registerThemeChangeReceiver() {
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ThemeManager.ACTION_THEME_CHANGED) {
                    Log.d(TAG, "Theme change broadcast received, recreating activity")
                    recreate()
                }
            }
        }

        val filter = IntentFilter(ThemeManager.ACTION_THEME_CHANGED)
        registerReceiver(themeChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister the receiver
        if (themeChangeReceiver != null) {
            try {
                unregisterReceiver(themeChangeReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            themeChangeReceiver = null
        }
    }
}