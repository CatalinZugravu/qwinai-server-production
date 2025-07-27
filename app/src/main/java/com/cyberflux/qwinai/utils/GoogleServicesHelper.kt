package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper class to safely work with Google Play Services
 */
object GoogleServicesHelper {

    private var isGooglePlayServicesAvailable = false
    private val isCheckingAvailability = AtomicBoolean(false)

    /**
     * Safely check if Google Play Services are available
     * Handles security exceptions and package validation
     */
    @Synchronized
    fun checkGooglePlayServicesAvailability(context: Context): Boolean {
        // If we're already checking, return the last known state
        if (isCheckingAvailability.get()) {
            return isGooglePlayServicesAvailable
        }

        try {
            isCheckingAvailability.set(true)

            // First check if Google Play Services package exists
            val gmsPackageName = "com.google.android.gms"
            val isPackageInstalled = try {
                context.packageManager.getPackageInfo(gmsPackageName, 0)
                Timber.d("Google Play Services package is installed")
                true
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.d("Google Play Services package is not installed")
                false
            } catch (e: Exception) {
                Timber.e(e, "Error checking Google Play Services package")
                false
            }

            // If package isn't installed, we know services aren't available
            if (!isPackageInstalled) {
                isGooglePlayServicesAvailable = false
                return false
            }

            // Try to find the GMS version
            val gmsVersion = try {
                val packageInfo = context.packageManager.getPackageInfo(gmsPackageName, 0)
                packageInfo.versionName
            } catch (e: Exception) {
                "unknown"
            }

            Timber.d("Google Play Services version: $gmsVersion")

            // Check if required classes are available
            val classesAvailable = areGooglePlayServicesClassesAvailable()
            if (!classesAvailable) {
                Timber.d("Google Play Services classes not available")
                isGooglePlayServicesAvailable = false
                return false
            }

            // Set flag and return success
            isGooglePlayServicesAvailable = true
            return true

        } catch (e: Exception) {
            Timber.e(e, "Error checking Google Play Services availability")
            isGooglePlayServicesAvailable = false
            return false
        } finally {
            isCheckingAvailability.set(false)
        }
    }

    /**
     * Check if key Google Play Services classes are available
     */
    private fun areGooglePlayServicesClassesAvailable(): Boolean {
        return try {
            // Try to load a few key classes
            Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil")
            true
        } catch (e: Exception) {
            Timber.d("Google Play Services classes not available: ${e.message}")
            false
        }
    }

    /**
     * Get the current availability state (without checking again)
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        return isGooglePlayServicesAvailable
    }

    /**
     * Safely execute Google Play Services code with fallback
     */
    fun <T> withGooglePlayServices(
        context: Context,
        action: () -> T,
        fallback: () -> T
    ): T {
        return if (isGooglePlayServicesAvailable()) {
            try {
                action()
            } catch (e: Exception) {
                Timber.e(e, "Error using Google Play Services, using fallback")
                fallback()
            }
        } else {
            fallback()
        }
    }

    /**
     * Reset for testing
     */
    fun reset() {
        isGooglePlayServicesAvailable = false
    }
}