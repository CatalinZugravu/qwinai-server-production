package com.cyberflux.qwinai

/**
 * Data class for API configuration
 *
 * @property baseUrl The base URL of the API
 * @property apiKey The API key for authentication
 */
data class ApiConfig(
    val baseUrl: String,
    val apiKey: String
)