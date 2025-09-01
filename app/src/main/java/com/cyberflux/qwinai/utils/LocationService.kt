package com.cyberflux.qwinai.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.app.ActivityCompat
import com.cyberflux.qwinai.network.AimlApiRequest
import com.cyberflux.qwinai.network.IpGeolocationHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Enhanced LocationService with improved international location detection
 */
object LocationService {
    private const val LOCATION_CACHE_DURATION = 15 * 60 * 1000 // 15 minutes
    private var cachedLocation: AimlApiRequest.UserLocation? = null
    private var locationCacheTime: Long = 0
    private var locationSource: String = "NONE"

    /**
     * Gets the approximate location information with improved international support
     */
    suspend fun getApproximateLocation(context: Context): AimlApiRequest.UserLocation = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val now = System.currentTimeMillis()
            if (cachedLocation != null && (now - locationCacheTime < LOCATION_CACHE_DURATION)
                && locationSource != "SYSTEM_DEFAULT") {
                Timber.d("Using cached location: ${cachedLocation?.approximate?.city}, ${cachedLocation?.approximate?.region} (source: $locationSource)")
                return@withContext cachedLocation!!
            }

            // FIRST PRIORITY: User preference (check first to respect user choice)
            val userPreferenceLocation = getUserDefaultLocation(context)
            if (userPreferenceLocation != null) {
                cachedLocation = userPreferenceLocation
                locationCacheTime = now
                locationSource = "USER_PREFERENCE"
                Timber.d("Using user preference location: ${userPreferenceLocation.approximate.city}, ${userPreferenceLocation.approximate.country}")
                return@withContext userPreferenceLocation
            }

            // SECOND PRIORITY: IP-based location - most reliable for international users
            val ipLocation = try {
                withTimeoutOrNull(5000) { // Add timeout to prevent hanging
                    IpGeolocationHelper.getLocation()
                }
            } catch (e: Exception) {
                Timber.e(e, "IP geolocation error: ${e.message}")
                null
            }

            if (ipLocation != null) {
                cachedLocation = ipLocation
                locationCacheTime = now
                locationSource = "IP"
                Timber.d("Using IP-based location: ${ipLocation.approximate.city}, ${ipLocation.approximate.country}")
                return@withContext ipLocation
            }

            // THIRD PRIORITY: Device location if permissions granted
            val deviceLocation = getLocationFromDevice(context)
            if (deviceLocation != null) {
                cachedLocation = deviceLocation
                locationCacheTime = now
                locationSource = "DEVICE"
                Timber.d("Using device location: ${deviceLocation.approximate.city}, ${deviceLocation.approximate.country}")
                return@withContext deviceLocation
            }

            // FOURTH PRIORITY: Try locale-based location
            val localeLocation = getLocationFromLocale()
            if (localeLocation != null) {
                cachedLocation = localeLocation
                locationCacheTime = now
                locationSource = "LOCALE"
                Timber.d("Using locale-based location: ${localeLocation.approximate.city}, ${localeLocation.approximate.country}")
                return@withContext localeLocation
            }

            // LAST RESORT: Use regionally appropriate default
            val defaultLocation = getRegionalDefaultLocation()
            cachedLocation = defaultLocation
            locationCacheTime = now
            locationSource = "SYSTEM_DEFAULT"
            Timber.d("Using regional default location: ${defaultLocation.approximate.city}, ${defaultLocation.approximate.country}")
            return@withContext defaultLocation

        } catch (e: Exception) {
            Timber.e(e, "Error in location service: ${e.message}")
            locationSource = "SYSTEM_DEFAULT"
            return@withContext getRegionalDefaultLocation()
        }
    }

    private fun getRegionalDefaultLocation(): AimlApiRequest.UserLocation {
        try {
            val locale = Locale.getDefault()
            val country = locale.displayCountry
            val language = locale.language

            // Set default based on language or country
            return when {
                // European languages/countries
                language == "en" && country.contains("UK") -> createDefaultLocation("London", "United Kingdom", "England", "Europe/London")
                language == "de" || country.contains("Germany") -> createDefaultLocation("Berlin", "Germany", "Berlin", "Europe/Berlin")
                language == "fr" || country.contains("France") -> createDefaultLocation("Paris", "France", "Île-de-France", "Europe/Paris")
                language == "es" -> createDefaultLocation("Madrid", "Spain", "Madrid", "Europe/Madrid")
                language == "it" -> createDefaultLocation("Rome", "Italy", "Lazio", "Europe/Rome")
                language.startsWith("ro") -> createDefaultLocation("Bucharest", "Romania", "Bucharest", "Europe/Bucharest")

                // Asian languages/countries
                language == "zh" -> createDefaultLocation("Beijing", "China", "Beijing", "Asia/Shanghai")
                language == "ja" -> createDefaultLocation("Tokyo", "Japan", "Tokyo", "Asia/Tokyo")
                language == "ko" -> createDefaultLocation("Seoul", "South Korea", "Seoul", "Asia/Seoul")
                language == "hi" || country.contains("India") -> createDefaultLocation("New Delhi", "India", "Delhi", "Asia/Kolkata")

                // Americas
                language == "en" && (country.contains("US") || country.contains("States")) ->
                    createDefaultLocation("New York", "United States", "New York", "America/New_York")
                language == "en" && country.contains("Canada") -> createDefaultLocation("Toronto", "Canada", "Ontario", "America/Toronto")
                language == "pt" || country.contains("Brazil") -> createDefaultLocation("São Paulo", "Brazil", "São Paulo", "America/Sao_Paulo")
                language == "es" && country.contains("Mexico") -> createDefaultLocation("Mexico City", "Mexico", "Mexico City", "America/Mexico_City")

                // Default to London as a globally recognized city
                else -> createDefaultLocation("London", "United Kingdom", "England", "Europe/London")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error determining regional default, using London: ${e.message}")
            return createDefaultLocation("London", "United Kingdom", "England", "Europe/London")
        }
    }

    /**
     * Get location information from device GPS if permissions are granted
     */
    private suspend fun getLocationFromDevice(context: Context): AimlApiRequest.UserLocation? = withContext(Dispatchers.IO) {
        try {
            // Check for location permissions
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return@withContext null
            }

            // Try to get device location with timeout
            val deviceLocation = withTimeoutOrNull(3000) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                suspendCancellableCoroutine { continuation ->
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                continuation.resume(location)
                            } else {
                                continuation.resumeWithException(Exception("Location not available"))
                            }
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                }
            }

            if (deviceLocation != null) {
                return@withContext getLocationFromGeocoder(context, deviceLocation)
            }

            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "Error getting device location: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Get location information based on device locale settings
     */
    private fun getLocationFromLocale(): AimlApiRequest.UserLocation? {
        try {
            val locale = Locale.getDefault()
            val country = locale.country
            val language = locale.language

            // Basic location mapping based on locale
            val locationInfo = when {
                // English-speaking countries
                language == "en" && country == "US" -> Triple("New York", "United States", "New York")
                language == "en" && country == "GB" -> Triple("London", "United Kingdom", "England")
                language == "en" && country == "CA" -> Triple("Toronto", "Canada", "Ontario")
                language == "en" && country == "AU" -> Triple("Sydney", "Australia", "New South Wales")

                // European countries
                language == "de" -> Triple("Berlin", "Germany", "Berlin")
                language == "fr" -> Triple("Paris", "France", "Île-de-France")
                language == "es" && country == "ES" -> Triple("Madrid", "Spain", "Madrid")
                language == "it" -> Triple("Rome", "Italy", "Lazio")
                language == "pt" && country == "PT" -> Triple("Lisbon", "Portugal", "Lisbon")
                language == "nl" -> Triple("Amsterdam", "Netherlands", "North Holland")
                language == "pl" -> Triple("Warsaw", "Poland", "Mazovia")
                language == "ro" -> Triple("Bucharest", "Romania", "Bucharest")
                language == "ru" -> Triple("Moscow", "Russia", "Moscow")

                // Asian countries
                language == "zh" && (country == "CN" || country == "SG") -> Triple("Beijing", "China", "Beijing")
                language == "ja" -> Triple("Tokyo", "Japan", "Tokyo")
                language == "ko" -> Triple("Seoul", "South Korea", "Seoul")
                language == "hi" -> Triple("New Delhi", "India", "Delhi")
                language == "ar" -> Triple("Dubai", "United Arab Emirates", "Dubai")

                // Latin American countries
                language == "es" && country == "MX" -> Triple("Mexico City", "Mexico", "Mexico City")
                language == "es" && country == "AR" -> Triple("Buenos Aires", "Argentina", "Buenos Aires")
                language == "es" && country == "CO" -> Triple("Bogotá", "Colombia", "Bogotá")
                language == "pt" && country == "BR" -> Triple("São Paulo", "Brazil", "São Paulo")

                // If no specific mapping, return null to try other methods
                else -> return null
            }

            // Get timezone based on country
            val timezone = getTimezoneForCountry(country) ?: TimeZone.getDefault().id

            return createDefaultLocation(locationInfo.first, locationInfo.second, locationInfo.third, timezone)
        } catch (e: Exception) {
            Timber.e(e, "Error getting location from locale: ${e.message}")
            return null
        }
    }

    /**
     * Helper to create a location object with the given parameters
     */
    private fun createDefaultLocation(
        city: String,
        country: String,
        region: String,
        timezone: String
    ): AimlApiRequest.UserLocation {
        return AimlApiRequest.UserLocation(
            approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                city = city,
                country = country,
                region = region,
                timezone = timezone
            ),
            timezone = timezone
        )
    }

    /**
     * Helper to get a timezone ID for a country code
     */
    private fun getTimezoneForCountry(countryCode: String): String? {
        return when (countryCode) {
            "US" -> "America/New_York"
            "GB" -> "Europe/London"
            "DE" -> "Europe/Berlin"
            "FR" -> "Europe/Paris"
            "ES" -> "Europe/Madrid"
            "IT" -> "Europe/Rome"
            "RU" -> "Europe/Moscow"
            "CN" -> "Asia/Shanghai"
            "JP" -> "Asia/Tokyo"
            "KR" -> "Asia/Seoul"
            "IN" -> "Asia/Kolkata"
            "BR" -> "America/Sao_Paulo"
            "AU" -> "Australia/Sydney"
            "CA" -> "America/Toronto"
            "MX" -> "America/Mexico_City"
            "AR" -> "America/Buenos_Aires"
            "ZA" -> "Africa/Johannesburg"
            "RO" -> "Europe/Bucharest"
            else -> null
        }
    }

    /**
     * Get location information using Geocoder
     */
    private suspend fun getLocationFromGeocoder(context: Context, location: Location): AimlApiRequest.UserLocation = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            // Use the appropriate Geocoder method based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use the new callback-based method for Android 13+
                var geocoderResult: AimlApiRequest.UserLocation.ApproximateLocation? = null

                suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                val address = addresses[0]
                                geocoderResult = AimlApiRequest.UserLocation.ApproximateLocation(
                                    city = address.locality ?: address.subAdminArea ?: "Unknown City",
                                    country = address.countryName ?: "Unknown Country",
                                    region = address.adminArea ?: "Unknown Region",
                                    timezone = TimeZone.getDefault().id
                                )
                                continuation.resume(Unit)
                            } else {
                                continuation.resume(Unit)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error with Geocoder callback: ${e.message}")
                        continuation.resume(Unit)
                    }
                }

                if (geocoderResult != null) {
                    return@withContext AimlApiRequest.UserLocation(
                        approximate = geocoderResult!!,
                        timezone = geocoderResult!!.timezone
                    )
                }
            } else {
                // Use the older synchronous method for Android 12 and below
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val timezone = TimeZone.getDefault().id
                        val approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                            city = address.locality ?: address.subAdminArea ?: "Unknown City",
                            country = address.countryName ?: "Unknown Country",
                            region = address.adminArea ?: "Unknown Region",
                            timezone = timezone
                        )

                        return@withContext AimlApiRequest.UserLocation(
                            approximate = approximate,
                            timezone = timezone
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error with deprecated Geocoder: ${e.message}")
                }
            }

            // If Geocoder failed, fallback to a simple location based on coordinates
            val timezone = TimeZone.getDefault().id
            val approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                city = "Unknown City",
                country = Locale.getDefault().displayCountry,
                region = "Unknown Region",
                timezone = timezone
            )

            return@withContext AimlApiRequest.UserLocation(
                approximate = approximate,
                timezone = timezone
            )

        } catch (e: Exception) {
            Timber.e(e, "Error getting location from Geocoder: ${e.message}")
            return@withContext getEuropeanDefaultLocation()
        }
    }

    /**
     * Get user-defined default location from preferences if available
     */
    private fun getUserDefaultLocation(context: Context): AimlApiRequest.UserLocation? {
        try {
            val prefs = context.getSharedPreferences("qwin_ai_location_prefs", Context.MODE_PRIVATE)
            val isLocationSet = prefs.getBoolean("location_set", false)

            if (!isLocationSet) {
                return null
            }

            // Read location data from preferences
            val city = prefs.getString("default_city", null) ?: return null
            val region = prefs.getString("default_region", null) ?: return null
            val country = prefs.getString("default_country", null) ?: return null
            val timezone = prefs.getString("default_timezone", TimeZone.getDefault().id) ?: TimeZone.getDefault().id

            return AimlApiRequest.UserLocation(
                approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                    city = city,
                    region = region,
                    country = country,
                    timezone = timezone
                ),
                timezone = timezone
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting user default location: ${e.message}")
            return null
        }
    }

    /**
     * Returns the source of the most recently retrieved location
     */
    fun getLocationSource(): String {
        return locationSource
    }

    /**
     * Clear location cache to force refresh
     */
    fun clearLocationCache() {
        cachedLocation = null
        locationCacheTime = 0
        locationSource = "NONE"
        Timber.d("Location cache cleared")
    }

    /**
     * Provides a more international default location when actual location cannot be determined
     */
    private fun getEuropeanDefaultLocation(): AimlApiRequest.UserLocation {
        return AimlApiRequest.UserLocation(
            approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                city = "London",
                country = "United Kingdom",
                region = "England",
                timezone = "Europe/London"
            ),
            timezone = "Europe/London"
        )
    }

    /**
     * Original default location - kept for backward compatibility
     */
    fun getSystemDefaultLocation(): AimlApiRequest.UserLocation {
        return AimlApiRequest.UserLocation(
            approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                city = "London", // Changed from San Francisco to be more international
                country = "United Kingdom", // Changed from US
                region = "England", // Changed from California
                timezone = "Europe/London" // Changed from America/Los_Angeles
            ),
            timezone = "Europe/London"
        )
    }
}