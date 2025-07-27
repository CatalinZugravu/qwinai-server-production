package com.cyberflux.qwinai.tools

import android.content.Context
import android.location.Geocoder
import com.cyberflux.qwinai.utils.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tool for retrieving weather information based on location queries
 */
class WeatherTool(private val context: Context) : Tool {
    override val id: String = "weather"
    override val name: String = "Weather Information"
    override val description: String = "Retrieves current weather conditions and forecasts for specified locations."

    // Weather API configuration (using OpenWeatherMap)
    private val API_KEY = com.cyberflux.qwinai.BuildConfig.WEATHER_API_KEY
    private val BASE_URL = "https://api.openweathermap.org/data/2.5"

    // HTTP Client for API requests
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Patterns to recognize weather queries
    private val weatherPatterns = listOf(
        Pattern.compile("\\bweather\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bforecast\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\btemperature\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bhow\\s+(?:is|are)\\s+the\\s+weather\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:is|will)\\s+it\\s+(?:rain|snow|sunny|cloudy|windy)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bhumidity\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwind\\s+speed\\b", Pattern.CASE_INSENSITIVE)
    )

    override fun canHandle(message: String): Boolean {
        return weatherPatterns.any { it.matcher(message).find() }
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("WeatherTool: Executing with message: ${message.take(50)}...")

            // Extract location from message or parameters
            val location = parameters["location"] as? String
                ?: extractLocation(message)
                ?: getUserLocation()

            if (location.isBlank()) {
                return@withContext ToolResult.error(
                    "No location specified",
                    "I couldn't determine which location you want weather information for. Please specify a city or location."
                )
            }

            Timber.d("WeatherTool: Fetching weather for location: $location")

            // Determine if we need current weather or forecast
            val needsForecast = message.contains("forecast") ||
                    message.contains("tomorrow") ||
                    message.contains("next day") ||
                    message.contains("this week") ||
                    message.contains("upcoming")

            val result = if (needsForecast) {
                fetchWeatherForecast(location)
            } else {
                fetchCurrentWeather(location)
            }

            if (!result.success) {
                return@withContext result
            }

            // Format and return the result
            return@withContext result

        } catch (e: Exception) {
            Timber.e(e, "WeatherTool: Error fetching weather: ${e.message}")
            return@withContext ToolResult.error(
                "Weather service error: ${e.message}",
                "There was an error retrieving weather information: ${e.message}"
            )
        }
    }

    /**
     * Extract location from the message
     */
    private fun extractLocation(message: String): String? {
        // Try specific location patterns
        val locationPatterns = listOf(
            Pattern.compile("\\bweather\\s+(?:in|for|at)\\s+([\\w\\s,]+?)(?:\\s+(?:today|now|tomorrow|this week))?(?:\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bforecast\\s+(?:in|for|at)\\s+([\\w\\s,]+?)(?:\\s+(?:today|now|tomorrow|this week))?(?:\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:in|at)\\s+([\\w\\s,]+?)\\s+(?:weather|forecast|temperature)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bhow\\s+(?:is|are)\\s+the\\s+weather\\s+(?:in|at)\\s+([\\w\\s,]+?)(?:\\.|$)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in locationPatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val location = matcher.group(1).trim()
                if (location.isNotBlank() && !isCommonStopWord(location)) {
                    return location
                }
            }
        }

        return null
    }

    /**
     * Check if a word is a common stop word that shouldn't be considered a location
     */
    private fun isCommonStopWord(word: String): Boolean {
        val stopWords = listOf("the", "a", "an", "this", "that", "these", "those", "now", "today", "tomorrow", "yesterday")
        return stopWords.contains(word.lowercase())
    }

    /**
     * Get user's current location
     */
    private suspend fun getUserLocation(): String {
        return try {
            val location = LocationService.getApproximateLocation(context)
            "${location.approximate.city}, ${location.approximate.country}"
        } catch (e: Exception) {
            Timber.e(e, "Error getting user location: ${e.message}")
            "" // Empty string indicates failure
        }
    }

    /**
     * Fetch current weather for a location
     */
    private suspend fun fetchCurrentWeather(location: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Get coordinates for the location
            val (lat, lon) = getCoordinates(location) ?: return@withContext ToolResult.error(
                "Location not found",
                "I couldn't find the coordinates for '$location'. Please check the spelling or try a different location."
            )

            // Build the API URL
            val url = "$BASE_URL/weather?lat=$lat&lon=$lon&units=metric&appid=$API_KEY"

            // Make the API request
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ToolResult.error(
                        "Weather API error: ${response.code}",
                        "There was an error retrieving weather data. Please try again later."
                    )
                }

                val responseBody = response.body?.string() ?: return@withContext ToolResult.error(
                    "Empty response from weather API",
                    "The weather service returned an empty response. Please try again later."
                )

                // Parse the JSON response
                val weatherJson = JSONObject(responseBody)

                // Extract weather information
                val cityName = weatherJson.getString("name")
                val country = weatherJson.getJSONObject("sys").getString("country")
                val temperature = weatherJson.getJSONObject("main").getDouble("temp")
                val feelsLike = weatherJson.getJSONObject("main").getDouble("feels_like")
                val humidity = weatherJson.getJSONObject("main").getInt("humidity")
                val pressure = weatherJson.getJSONObject("main").getInt("pressure")

                val weatherArray = weatherJson.getJSONArray("weather")
                val weatherMain = weatherArray.getJSONObject(0).getString("main")
                val weatherDescription = weatherArray.getJSONObject(0).getString("description")
                val weatherIcon = weatherArray.getJSONObject(0).getString("icon")

                val windSpeed = weatherJson.getJSONObject("wind").getDouble("speed")
                val windDirection = weatherJson.getJSONObject("wind").getInt("deg")

                val timestamp = weatherJson.getLong("dt")
                val date = Date(timestamp * 1000)
                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)
                val formattedDate = dateFormat.format(date)

                // Format the weather information
                val content = buildString {
                    append("Current Weather for $cityName, $country\n")
                    append("As of $formattedDate\n\n")
                    append("Temperature: ${temperature.toInt()}°C (${celsiusToFahrenheit(temperature).toInt()}°F)\n")
                    append("Feels like: ${feelsLike.toInt()}°C (${celsiusToFahrenheit(feelsLike).toInt()}°F)\n")
                    append("Condition: $weatherMain - $weatherDescription\n")
                    append("Humidity: $humidity%\n")
                    append("Pressure: $pressure hPa\n")
                    append("Wind: $windSpeed m/s, ${getWindDirection(windDirection)}\n")
                }

                return@withContext ToolResult.success(
                    content = content,
                    data = mapOf(
                        "cityName" to cityName,
                        "country" to country,
                        "temperature" to temperature,
                        "condition" to weatherMain,
                        "description" to weatherDescription,
                        "humidity" to humidity,
                        "windSpeed" to windSpeed
                    ),
                    metadata = mapOf(
                        "type" to "current_weather",
                        "location" to "$cityName, $country",
                        "icon" to weatherIcon,
                        "timestamp" to timestamp
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching current weather: ${e.message}")
            return@withContext ToolResult.error(
                "Weather service error: ${e.message}",
                "There was an error retrieving current weather information: ${e.message}"
            )
        }
    }

    /**
     * Fetch weather forecast for a location
     */
    private suspend fun fetchWeatherForecast(location: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Get coordinates for the location
            val (lat, lon) = getCoordinates(location) ?: return@withContext ToolResult.error(
                "Location not found",
                "I couldn't find the coordinates for '$location'. Please check the spelling or try a different location."
            )

            // Build the API URL for 5-day forecast
            val url = "$BASE_URL/forecast?lat=$lat&lon=$lon&units=metric&appid=$API_KEY"

            // Make the API request
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ToolResult.error(
                        "Weather API error: ${response.code}",
                        "There was an error retrieving forecast data. Please try again later."
                    )
                }

                val responseBody = response.body?.string() ?: return@withContext ToolResult.error(
                    "Empty response from weather API",
                    "The weather service returned an empty response. Please try again later."
                )

                // Parse the JSON response
                val forecastJson = JSONObject(responseBody)

                // Extract city information
                val cityJson = forecastJson.getJSONObject("city")
                val cityName = cityJson.getString("name")
                val country = cityJson.getString("country")

                // Extract forecast list
                val forecastList = forecastJson.getJSONArray("list")

                // Process forecast for the next 5 days
                val dailyForecasts = mutableMapOf<String, MutableList<JSONObject>>()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                for (i in 0 until forecastList.length()) {
                    val forecast = forecastList.getJSONObject(i)
                    val timestamp = forecast.getLong("dt")
                    val date = Date(timestamp * 1000)
                    val dateKey = dateFormat.format(date)

                    if (!dailyForecasts.containsKey(dateKey)) {
                        dailyForecasts[dateKey] = mutableListOf()
                    }

                    dailyForecasts[dateKey]?.add(forecast)
                }

                // Format the forecast information
                val content = buildString {
                    append("5-Day Weather Forecast for $cityName, $country\n\n")

                    val displayFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)

                    dailyForecasts.entries.sortedBy { it.key }.take(5).forEach { (dateKey, forecasts) ->
                        val date = dateFormat.parse(dateKey) ?: Date()
                        val displayDate = displayFormat.format(date)

                        // Calculate min/max temperatures and predominant weather condition
                        val temperatures = forecasts.map { it.getJSONObject("main").getDouble("temp") }
                        val minTemp = temperatures.minOrNull() ?: 0.0
                        val maxTemp = temperatures.maxOrNull() ?: 0.0

                        // Get mid-day forecast if available, or first forecast of the day
                        val midDayForecast = forecasts.find {
                            val time = SimpleDateFormat("HH", Locale.US).format(Date(it.getLong("dt") * 1000))
                            time == "12" || time == "13" || time == "14"
                        } ?: forecasts.first()

                        val weatherMain = midDayForecast.getJSONArray("weather").getJSONObject(0).getString("main")
                        val weatherDescription = midDayForecast.getJSONArray("weather").getJSONObject(0).getString("description")

                        append("$displayDate: ")
                        append("${minTemp.toInt()}-${maxTemp.toInt()}°C ")
                        append("(${celsiusToFahrenheit(minTemp).toInt()}-${celsiusToFahrenheit(maxTemp).toInt()}°F), ")
                        append("$weatherMain - $weatherDescription\n")
                    }
                }

                return@withContext ToolResult.success(
                    content = content,
                    data = mapOf(
                        "cityName" to cityName,
                        "country" to country,
                        "forecastDays" to dailyForecasts.size
                    ),
                    metadata = mapOf(
                        "type" to "forecast",
                        "location" to "$cityName, $country",
                        "days" to dailyForecasts.size
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching weather forecast: ${e.message}")
            return@withContext ToolResult.error(
                "Weather service error: ${e.message}",
                "There was an error retrieving forecast information: ${e.message}"
            )
        }
    }

    /**
     * Get coordinates (latitude, longitude) for a location
     * @return Pair<latitude, longitude> or null if not found
     */
    private suspend fun getCoordinates(location: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            // Try to use Geocoder first
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(location, 1)

                if (!addresses.isNullOrEmpty() && addresses.size > 0) {
                    val address = addresses[0]
                    return@withContext Pair(address.latitude, address.longitude)
                }
            }

            // Fallback to direct API call for geocoding
            val encodedLocation = java.net.URLEncoder.encode(location, "UTF-8")
            val url = "https://api.openweathermap.org/geo/1.0/direct?q=$encodedLocation&limit=1&appid=$API_KEY"

            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }

                val responseBody = response.body?.string() ?: return@withContext null
                val geoArray = org.json.JSONArray(responseBody)

                if (geoArray.length() > 0) {
                    val geoObject = geoArray.getJSONObject(0)
                    val lat = geoObject.getDouble("lat")
                    val lon = geoObject.getDouble("lon")
                    return@withContext Pair(lat, lon)
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "Error getting coordinates: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Convert Celsius to Fahrenheit
     */
    private fun celsiusToFahrenheit(celsius: Double): Double {
        return celsius * 9 / 5 + 32
    }

    /**
     * Get wind direction as a compass point
     */
    private fun getWindDirection(degrees: Int): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }
}