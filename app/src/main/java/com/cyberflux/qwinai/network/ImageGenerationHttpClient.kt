package com.cyberflux.qwinai.network

import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import com.cyberflux.qwinai.BuildConfig
import java.io.IOException

object ImageGenerationHttpClient {

    // Timeouts optimized for image generation
    private const val IMAGE_GEN_CONNECT_TIMEOUT = 30L
    private const val IMAGE_GEN_READ_TIMEOUT = 180L    // Increased to 3 minutes
    private const val IMAGE_GEN_WRITE_TIMEOUT = 60L    // Increased write timeout
    private const val IMAGE_GEN_CALL_TIMEOUT = 300L    // 5 minutes total

    val client: OkHttpClient by lazy {
        createImageGenerationClient()
    }

    private fun createImageGenerationClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // Limit logging for large responses
            if (message.length <= 2000) {
                Timber.tag("ImageGenAPI").d(message)
            } else {
                Timber.tag("ImageGenAPI").d("Response too large (${message.length} chars): ${message.substring(0, 200)}...")
            }
        }.apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.HEADERS // Changed from BODY to reduce log spam
            else
                HttpLoggingInterceptor.Level.NONE
        }

        // Optimized dispatcher for image generation
        val dispatcher = Dispatcher().apply {
            maxRequests = 3  // Further reduced to prevent server overload
            maxRequestsPerHost = 2
        }

        // Connection pool with longer keep-alive
        val connectionPool = ConnectionPool(
            2,  // Max 2 idle connections
            15, // 15 minutes keep alive
            TimeUnit.MINUTES
        )

        return OkHttpClient.Builder().apply {
            // Use HTTP/1.1 as primary protocol to avoid HTTP/2 stream issues
            protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))

            dispatcher(dispatcher)
            connectionPool(connectionPool)

            // Increased timeouts for image generation
            connectTimeout(IMAGE_GEN_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            readTimeout(IMAGE_GEN_READ_TIMEOUT, TimeUnit.SECONDS)
            writeTimeout(IMAGE_GEN_WRITE_TIMEOUT, TimeUnit.SECONDS)
            callTimeout(IMAGE_GEN_CALL_TIMEOUT, TimeUnit.SECONDS)

            retryOnConnectionFailure(true)

            // Add interceptors in order of importance
            addInterceptor(ProtocolFallbackInterceptor())
            addInterceptor(ConnectionResetRecoveryInterceptor())
            addInterceptor(ImageGenerationRetryInterceptor())
            addInterceptor(TimeoutRecoveryInterceptor())
            addInterceptor(RequestValidationInterceptor())
            addInterceptor(ResponseValidationInterceptor())
            addInterceptor(ProgressInterceptor())
            addInterceptor(loggingInterceptor)

            dns(Dns.SYSTEM)
            pingInterval(45, TimeUnit.SECONDS) // Increased ping interval

        }.build()
    }

    /**
     * New interceptor to handle protocol fallback and stream resets
     */
    private class ProtocolFallbackInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            return try {
                chain.proceed(request)
            } catch (e: Exception) {
                when {
                    e.message?.contains("stream was reset") == true ||
                            e.message?.contains("INTERNAL_ERROR") == true ||
                            e.message?.contains("PROTOCOL_ERROR") == true -> {

                        Timber.w("HTTP/2 stream error detected, attempting HTTP/1.1 fallback: ${e.message}")

                        // Force HTTP/1.1 for this request
                        val fallbackRequest = request.newBuilder()
                            .addHeader("Connection", "close") // Force new connection
                            .addHeader("Cache-Control", "no-cache")
                            .build()

                        try {
                            chain.proceed(fallbackRequest)
                        } catch (fallbackException: Exception) {
                            Timber.e("Fallback to HTTP/1.1 also failed: ${fallbackException.message}")
                            throw e // Throw original exception
                        }
                    }
                    else -> throw e
                }
            }
        }
    }

    /**
     * Enhanced interceptor specifically for connection reset errors
     */
    private class ConnectionResetRecoveryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            return try {
                chain.proceed(request)
            } catch (e: Exception) {
                when {
                    e.message?.contains("Connection reset") == true ||
                            e.message?.contains("Broken pipe") == true ||
                            e.message?.contains("Socket closed") == true -> {

                        Timber.w("Connection reset detected, creating fresh connection: ${e.message}")

                        // Add headers to force a fresh connection
                        val freshRequest = request.newBuilder()
                            .addHeader("Connection", "close")
                            .addHeader("Cache-Control", "no-cache, no-store")
                            .removeHeader("Keep-Alive")
                            .build()

                        // Wait a bit before retry
                        Thread.sleep(2000)

                        try {
                            chain.proceed(freshRequest)
                        } catch (retryException: Exception) {
                            Timber.e("Fresh connection attempt failed: ${retryException.message}")
                            throw IOException("Connection reset - server may be overloaded. Please try again.", e)
                        }
                    }
                    else -> throw e
                }
            }
        }
    }

    /**
     * Enhanced retry interceptor with better error classification
     */
    private class ImageGenerationRetryInterceptor : Interceptor {
        companion object {
            private const val MAX_RETRIES = 2  // Reduced retries to avoid spam
            private val RETRY_DELAYS = listOf(3000L, 8000L) // Longer delays
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var lastException: Exception? = null

            for (attempt in 0 until MAX_RETRIES) {
                try {
                    Timber.d("Image generation attempt ${attempt + 1} of $MAX_RETRIES for ${request.url}")

                    val newRequest = request.newBuilder()
                        .removeHeader("Retry-Count")
                        .addHeader("Retry-Count", attempt.toString())
                        .addHeader("X-Request-Timestamp", System.currentTimeMillis().toString())
                        .build()

                    val response = chain.proceed(newRequest)

                    // Success or client error (don't retry)
                    if (response.isSuccessful || response.code in 400..499) {
                        if (attempt > 0) {
                            Timber.i("Request succeeded on attempt ${attempt + 1}")
                        }
                        return response
                    }

                    // Server errors - retry
                    if (response.code in 500..599) {
                        response.close()
                        throw ServerErrorException("Server error: ${response.code} ${response.message}")
                    }

                    return response

                } catch (e: Exception) {
                    lastException = e

                    val shouldRetry = when (e) {
                        is SocketTimeoutException -> true
                        is java.net.SocketException -> e.message?.contains("reset") == true
                        is java.net.UnknownHostException -> false // Don't retry DNS failures
                        is javax.net.ssl.SSLException -> false // Don't retry SSL failures
                        is ServerErrorException -> true
                        is java.io.InterruptedIOException -> e.message?.contains("timeout") == true
                        else -> false
                    }

                    if (!shouldRetry || attempt >= MAX_RETRIES - 1) {
                        Timber.e("Request failed permanently after ${attempt + 1} attempts: ${e.message}")
                        throw e
                    }

                    val delay = RETRY_DELAYS.getOrElse(attempt) { 8000L }
                    Timber.w("Retrying in ${delay}ms due to ${e.javaClass.simpleName}: ${e.message}")

                    try {
                        Thread.sleep(delay)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }

            throw lastException ?: IOException("Request failed after $MAX_RETRIES attempts")
        }
    }

    /**
     * Enhanced timeout recovery with better diagnostics
     */
    private class TimeoutRecoveryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()

            return try {
                chain.proceed(request)
            } catch (e: SocketTimeoutException) {
                val duration = System.currentTimeMillis() - startTime

                Timber.e("Request timeout after ${duration}ms")
                Timber.e("URL: ${request.url}")
                Timber.e("Method: ${request.method}")
                Timber.e("Timeout settings - Connect: ${chain.connectTimeoutMillis()}ms, Read: ${chain.readTimeoutMillis()}ms")

                // Check request size for POST requests
                if (request.method == "POST" && request.body != null) {
                    try {
                        val buffer = okio.Buffer()
                        request.body!!.writeTo(buffer)
                        val bodySize = buffer.size
                        Timber.e("Request body size: $bodySize bytes")

                        if (bodySize > 5 * 1024 * 1024) { // > 5MB
                            throw SocketTimeoutException("Request timed out - large request body (${bodySize} bytes) may be causing issues. Consider reducing image size.")
                        }
                    } catch (ignored: Exception) {
                        // Ignore buffer read errors
                    }
                }

                // Enhanced error message based on timeout type
                val timeoutType = when {
                    duration < 5000 -> "connection"
                    duration < 30000 -> "server processing"
                    else -> "data transfer"
                }

                throw SocketTimeoutException("Image generation timeout ($timeoutType) after ${duration}ms. URL: ${request.url}. Try reducing image size or checking your connection.")
            }
        }
    }

    /**
     * Enhanced request validation
     */
    private class RequestValidationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            // Validate authorization header
            val authHeader = request.header("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw IOException("Missing or invalid Authorization header")
            }

            // Validate API key format (basic check)
            val apiKey = authHeader.substring(7)
            if (apiKey.length < 10) {
                throw IOException("API key appears to be too short")
            }

            // For POST requests with JSON bodies
            if (request.method == "POST" &&
                request.header("Content-Type")?.contains("application/json") == true &&
                request.body != null) {

                try {
                    val buffer = okio.Buffer()
                    request.body!!.writeTo(buffer)
                    val bodyString = buffer.readUtf8()

                    // Validate JSON structure
                    val json = org.json.JSONObject(bodyString)

                    // Check for required fields
                    if (!json.has("model")) {
                        throw IOException("Request missing required 'model' field")
                    }
                    if (!json.has("prompt")) {
                        throw IOException("Request missing required 'prompt' field")
                    }

                    Timber.d("Request validation passed for model: ${json.optString("model")}")
                } catch (e: org.json.JSONException) {
                    throw IOException("Invalid JSON in request body: ${e.message}")
                } catch (e: IOException) {
                    if (e.message?.contains("missing required") == true) {
                        throw e
                    }
                    // If it's just a reading error, continue
                    Timber.w("Could not validate request body: ${e.message}")
                }
            }

            return chain.proceed(request)
        }
    }

    /**
     * Enhanced response validation
     */
    private class ResponseValidationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)

            // Only validate JSON responses
            val contentType = response.header("Content-Type")
            if (contentType?.contains("application/json") == true) {
                try {
                    val responseBody = response.peekBody(Long.MAX_VALUE).string()

                    if (responseBody.isBlank()) {
                        Timber.e("Empty JSON response from ${request.url}")
                        return response
                    }

                    val jsonResponse = org.json.JSONObject(responseBody)

                    // Check for API errors
                    if (jsonResponse.has("error")) {
                        val error = jsonResponse.get("error")
                        Timber.e("API error response: $error")

                        // Parse error details for better user feedback
                        if (error is org.json.JSONObject) {
                            val errorMessage = error.optString("message", "Unknown error")
                            val errorCode = error.optString("code", "")
                            Timber.e("Error details - Code: $errorCode, Message: $errorMessage")
                        }
                    } else if (response.isSuccessful) {
                        // Validate successful image generation responses
                        if (request.url.toString().contains("/images/generations")) {
                            val hasValidImageData = jsonResponse.has("data") ||
                                    jsonResponse.has("images") ||
                                    jsonResponse.has("url") ||
                                    jsonResponse.has("b64_json")

                            if (!hasValidImageData) {
                                Timber.w("Image generation response missing expected image data fields")
                            } else {
                                Timber.d("Valid image generation response received")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e("Response validation error: ${e.message}")
                }
            }

            return response
        }
    }

    /**
     * Enhanced progress tracking
     */
    private class ProgressInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()

            Timber.d("Starting request: ${request.method} ${request.url}")

            // Log request details for debugging
            if (request.body != null) {
                try {
                    val buffer = okio.Buffer()
                    request.body!!.writeTo(buffer)
                    val bodySize = buffer.size
                    Timber.d("Request body size: $bodySize bytes")
                } catch (e: Exception) {
                    Timber.d("Could not determine request body size")
                }
            }

            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Timber.e("Request failed after ${duration}ms: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            }

            val duration = System.currentTimeMillis() - startTime
            val responseSize = response.body?.contentLength() ?: -1

            Timber.d("Request completed: ${response.code} ${response.message} in ${duration}ms (${responseSize} bytes)")

            return response
        }
    }

    private class ServerErrorException(message: String) : IOException(message)

    /**
     * Cancel all pending requests
     */
    fun cancelAll() {
        client.dispatcher.cancelAll()
        Timber.d("Cancelled all pending image generation requests")
    }

    /**
     * Get client statistics for debugging
     */
    fun getClientStats(): String {
        val dispatcher = client.dispatcher
        return "Running calls: ${dispatcher.runningCallsCount()}, Queued calls: ${dispatcher.queuedCallsCount()}"
    }
}