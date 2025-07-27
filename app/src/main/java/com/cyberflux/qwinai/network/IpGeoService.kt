package com.cyberflux.qwinai.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.TimeZone

/**
 * Service interface for IP-based geolocation
 */
interface IpGeoService {
    @GET(".")
    @Headers("Accept: application/json")
    suspend fun getLocation(): Response<IpLocationResponse>
}

/**
 * Response data class for IP geolocation
 * Supporting multiple service response formats
 */
data class IpLocationResponse(
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val loc: String? = null,
    val timezone: String? = null,
    val postal: String? = null,
    val org: String? = null,
    // Additional fields that some services might return
    val region_name: String? = null,
    val country_name: String? = null,
    val country_code: String? = null,
    val state: String? = null
)

/**
 * Helper object for IP geolocation that tries multiple services
 */
object IpGeolocationHelper {
    // Multiple geolocation services for better international coverage
    private val GEOLOCATION_SERVICES = listOf(
        "https://ipinfo.io/json",
        "https://ipapi.co/json",
        "https://ip-api.com/json"
    )

    // Timeout per service to prevent long delays
    private const val SERVICE_TIMEOUT_MS = 3000L

    /**
     * Attempt to get location from multiple IP services concurrently
     */
    suspend fun getLocation(): AimlApiRequest.UserLocation? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Attempting IP geolocation with multiple services")

            // Create request jobs for all services
            coroutineScope {
                val locationJobs = GEOLOCATION_SERVICES.map { serviceUrl ->
                    async {
                        withTimeoutOrNull(SERVICE_TIMEOUT_MS) {
                            try {
                                val service = RetrofitInstance.createCustomService(serviceUrl, IpGeoService::class.java)
                                val response = service.getLocation()

                                if (response.isSuccessful && response.body() != null) {
                                    val ipData = response.body()!!

                                    // Check for essential data - city or country must be present
                                    if ((ipData.city != null && ipData.city.isNotBlank()) ||
                                        (ipData.country != null && ipData.country.isNotBlank()) ||
                                        (ipData.country_name != null && ipData.country_name.isNotBlank()) ||
                                        !ipData.country_code.isNullOrBlank()
                                    ) {

                                        val cityName = ipData.city ?: ipData.state ?: "Unknown City"
                                        val countryName = ipData.country ?: ipData.country_name ?: ipData.country_code ?: "Unknown Country"

                                        Timber.d("Got location via IP: $cityName, $countryName")

                                        AimlApiRequest.UserLocation(
                                            approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                                                city = cityName,
                                                country = countryName,
                                                region = ipData.region ?: ipData.region_name ?: ipData.state ?: "Unknown Region",
                                                timezone = ipData.timezone ?: TimeZone.getDefault().id
                                            ),
                                            timezone = ipData.timezone ?: TimeZone.getDefault().id
                                        )
                                    } else {
                                        null
                                    }
                                } else {
                                    Timber.w("Service $serviceUrl responded with code ${response.code()}")
                                    null
                                }
                            } catch (e: java.net.UnknownHostException) {
                                Timber.w("Network unavailable for service: $serviceUrl - ${e.message}")
                                null
                            } catch (e: java.net.SocketTimeoutException) {
                                Timber.w("Timeout for service: $serviceUrl")
                                null
                            } catch (e: Exception) {
                                Timber.w("IP geolocation service error: $serviceUrl - ${e.message}")
                                null
                            }
                        }
                    }
                }

                // Wait for all jobs and return the first successful one
                val results = locationJobs.awaitAll().filterNotNull()
                if (results.isEmpty()) {
                    Timber.w("All IP geolocation services failed, using fallback location")
                    // Return a fallback location based on system timezone
                    AimlApiRequest.UserLocation(
                        approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                            city = "Unknown City",
                            country = "Unknown Country", 
                            region = "Unknown Region",
                            timezone = TimeZone.getDefault().id
                        ),
                        timezone = TimeZone.getDefault().id
                    )
                } else {
                    results.first()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error in IP geolocation, using fallback")
            // Return fallback location
            AimlApiRequest.UserLocation(
                approximate = AimlApiRequest.UserLocation.ApproximateLocation(
                    city = "Unknown City",
                    country = "Unknown Country",
                    region = "Unknown Region", 
                    timezone = TimeZone.getDefault().id
                ),
                timezone = TimeZone.getDefault().id
            )
        }
    }
}