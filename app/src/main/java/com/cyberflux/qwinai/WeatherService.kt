package com.cyberflux.qwinai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

object WeatherService {
    data class WeatherData(
        val temperature: Int,
        val condition: String,
        val humidity: Int,
        val windSpeed: Float
    )

    private const val API_KEY = BuildConfig.WEATHER_API_KEY

    suspend fun getWeatherForLocation(city: String, country: String): WeatherData {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                val url = "https://api.weatherapi.com/v1/current.json?key=$API_KEY&q=$city,$country"

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    throw IOException("Unsuccessful response or empty body: ${response.code}")
                }

                val json = JSONObject(responseBody)
                val current = json.getJSONObject("current")
                val condition = current.getJSONObject("condition")

                WeatherData(
                    temperature = current.getDouble("temp_c").toInt(),
                    condition = condition.getString("text"),
                    humidity = current.getInt("humidity"),
                    windSpeed = current.getDouble("wind_kph").toFloat()
                )
            } catch (e: Exception) {
                Timber.e(e, "Weather API error: ${e.message}")
                WeatherData(
                    temperature = 20,
                    condition = "Clear",
                    humidity = 60,
                    windSpeed = 5.0f
                )
            }
        }
    }
}
