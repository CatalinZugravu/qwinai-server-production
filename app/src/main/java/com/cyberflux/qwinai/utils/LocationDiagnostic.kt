package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.network.AimlApiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Helper to diagnose location issues
 */
object LocationDiagnostic {

    /**
     * Run diagnostics to check location detection
     * Call this when setting up the app to verify location detection
     */
    suspend fun runLocationDiagnostic(context: Context) = withContext(Dispatchers.IO) {
        try {
            Timber.d("🌍 LOCATION DIAGNOSTIC STARTED 🌍")

            // Clear any cached location
            LocationService.clearLocationCache()

            // Test IP geolocation directly
            Timber.d("Testing IP geolocation directly...")
            val ipLocation = com.cyberflux.qwinai.network.IpGeolocationHelper.getLocation()
            if (ipLocation != null) {
                Timber.d("✅ IP GEOLOCATION SUCCESS: ${ipLocation.approximate.city}, ${ipLocation.approximate.region}, ${ipLocation.approximate.country}")
            } else {
                Timber.e("❌ IP GEOLOCATION FAILED")
            }

            // Test full location service
            Timber.d("Testing full location service...")
            val fullLocation = LocationService.getApproximateLocation(context)
            Timber.d("📍 LOCATION SERVICE RESULT: ${fullLocation.approximate.city}, ${fullLocation.approximate.region}, ${fullLocation.approximate.country}")
            Timber.d("📍 LOCATION SOURCE: ${LocationService.getLocationSource()}")

            // Done
            Timber.d("🌍 LOCATION DIAGNOSTIC COMPLETED 🌍")
        } catch (e: Exception) {
            Timber.e(e, "Error in location diagnostic")
        }
    }

    /**
     * Check if current location appears to be the default fallback
     */
    fun isDefaultLocation(location: AimlApiRequest.UserLocation): Boolean {
        return (location.approximate.city == "San Francisco" && location.approximate.country == "US") ||
                (location.approximate.city == "London" && location.approximate.country == "United Kingdom") ||
                (location.approximate.city == "Unknown City")
    }
}