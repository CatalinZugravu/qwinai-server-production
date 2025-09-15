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
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import timber.log.Timber
import android.content.res.ColorStateList
import androidx.core.view.ViewCompat

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
    
    override fun onResume() {
        super.onResume()
        // Apply dynamic colors when activity resumes
        applyDynamicTheming()
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
    
    /**
     * Apply dynamic theming based on user's accent color selection
     */
    private fun applyDynamicTheming() {
        try {
            // Apply accent-based status bar theming FIRST
            ThemeManager.applyAccentStatusBarTheming(this)
            
            // Apply system UI appearance
            ThemeManager.applySystemUIAppearance(this)
            
            // Apply dynamic colors to UI elements
            applyDynamicAccentColor()
            
            Log.d(TAG, "Dynamic theming with accent status bar applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dynamic theming", e)
        }
    }
    
    /**
     * Apply dynamic accent color to UI components safely
     */
    protected open fun applyDynamicAccentColor() {
        try {
            // Use the safe method to avoid interfering with button functionality
            DynamicColorManager.applySafeAccentColorsToActivity(this)
            
            Log.d(TAG, "Scheduled safe dynamic accent colors for ${this.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying safe dynamic accent colors", e)
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
                    Log.d(TAG, "Theme change broadcast received, applying dynamic theming and recreating activity")
                    
                    // Apply dynamic theming before recreating
                    applyDynamicTheming()
                    
                    // Small delay to ensure theming is applied
                    window.decorView.postDelayed({
                        recreate()
                    }, 50)
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