package com.cyberflux.qwinai.network

import com.cyberflux.qwinai.ApiConfig
import com.cyberflux.qwinai.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Improved RetrofitInstance with better streaming support
 * Handles large responses more efficiently
 */
object RetrofitInstance {

    val apiConfigs = mapOf(
        ApiServiceType.AIMLAPI to ApiConfig(
            baseUrl = "https://api.aimlapi.com/",
            apiKey = BuildConfig.AIMLAPI_KEY
        ),
        ApiServiceType.TOGETHER_AI to ApiConfig(
            baseUrl = "https://api.together.xyz/v1/",
            apiKey = BuildConfig.TOGETHER_AI_KEY
        )
    )

    fun getApiKey(apiType: ApiServiceType): String? {
        return apiConfigs[apiType]?.apiKey
    }

    fun getBaseUrl(apiType: ApiServiceType): String? {
        return apiConfigs[apiType]?.baseUrl
    }

    enum class ApiServiceType {
        AIMLAPI,
        TOGETHER_AI
    }

    fun <T> getApiService(apiType: ApiServiceType, serviceClass: Class<T>): T {
        val config = apiConfigs[apiType] ?: throw IllegalArgumentException("Invalid API name")
        return createService(config, serviceClass)
    }

    fun <T> createCustomService(
        baseUrl: String,
        serviceClass: Class<T>,
        forceHttp1: Boolean = false
    ): T {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val config = ApiConfig(normalizedUrl, "")
        return createService(config, serviceClass, isThirdParty = true, forceHttp1 = forceHttp1)
    }

    private fun <T> createService(
        config: ApiConfig,
        serviceClass: Class<T>,
        isThirdParty: Boolean = false,
        forceHttp1: Boolean = false
    ): T {
        val gson = GsonBuilder()
            .registerTypeAdapter(Any::class.java, FlexibleJsonAdapter())
            .registerTypeAdapter(AimlApiResponse::class.java, ImprovedStreamingAdapter())
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(createHttpClient(config.apiKey, isThirdParty, forceHttp1))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(serviceClass)
    }

    // In RetrofitInstance.kt, modify the createHttpClient method:
    fun createHttpClient(apiKey: String, isThirdParty: Boolean = false, forceHttp1: Boolean = false): OkHttpClient {
        // Create an improved logging interceptor with no truncation
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // Log the entire message with no truncation
            Timber.tag("API").d(message)
        }.apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BASIC  // CHANGED from BODY to BASIC for streaming
            else
                HttpLoggingInterceptor.Level.NONE
        }

        // Create an optimized dispatcher with reasonable limits
        val dispatcher = Dispatcher().apply {
            maxRequests = 64                 // Reasonable concurrent requests
            maxRequestsPerHost = 8          // Per-host concurrent limit
        }

        // Create a client builder with improved configuration
        val builder = OkHttpClient.Builder()

        // Use HTTP/1.1 protocol for all requests for maximum compatibility
        builder.protocols(listOf(Protocol.HTTP_1_1))

        // Configure the dispatcher
        builder.dispatcher(dispatcher)

        // Optimized connection pool settings
        builder.connectionPool(ConnectionPool(
            20,  // Maximum idle connections - reasonable limit
            5,   // Keep-alive duration - balanced
            TimeUnit.MINUTES // Time unit for keep-alive
        ))

        // Enable retries for improved reliability
        builder.retryOnConnectionFailure(true)

        // IMPROVED: Use smaller buffer size for faster streaming
        builder.socketFactory(object : javax.net.SocketFactory() {
            private val delegate = javax.net.SocketFactory.getDefault()

            override fun createSocket(): java.net.Socket {
                return delegate.createSocket().apply {
                    receiveBufferSize = 32768  // Optimized buffer size for better throughput
                    sendBufferSize = 32768     // Optimized buffer size for better throughput
                    tcpNoDelay = true         // Disable Nagle's algorithm for faster packets
                }
            }

            override fun createSocket(host: String, port: Int) =
                delegate.createSocket(host, port).apply {
                    receiveBufferSize = 32768
                    sendBufferSize = 32768
                    tcpNoDelay = true
                }

            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) =
                delegate.createSocket(host, port, localHost, localPort).apply {
                    receiveBufferSize = 32768
                    sendBufferSize = 32768
                    tcpNoDelay = true
                }

            override fun createSocket(host: java.net.InetAddress, port: Int) =
                delegate.createSocket(host, port).apply {
                    receiveBufferSize = 32768
                    sendBufferSize = 32768
                    tcpNoDelay = true
                }

            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) =
                delegate.createSocket(address, port, localAddress, localPort).apply {
                    receiveBufferSize = 32768
                    sendBufferSize = 32768
                    tcpNoDelay = true
                }
        })

        // Set reasonable timeouts
        builder.connectTimeout(15, TimeUnit.SECONDS)   // 15 seconds connect
        builder.readTimeout(60, TimeUnit.SECONDS)      // 1 minute read timeout
        builder.writeTimeout(30, TimeUnit.SECONDS)     // 30 seconds write timeout
        builder.callTimeout(120, TimeUnit.SECONDS)     // 2 minutes total timeout

        // Add HTTP cache for better performance
        val cacheSize = 50L * 1024L * 1024L // 50MB cache
        val cacheDir = File("cache") // This should be passed from context
        if (!isThirdParty) {
            try {
                val cache = Cache(cacheDir, cacheSize)
                builder.cache(cache)
                builder.addNetworkInterceptor(CacheInterceptor())
            } catch (e: Exception) {
                Timber.w("Failed to setup cache: ${e.message}")
            }
        }

        // Add request deduplication interceptor
        builder.addInterceptor(RequestDeduplicationInterceptor())

        // Add streaming-aware request interceptor
        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBody = originalRequest.body
            var isStreamingRequest = false

            // Detect if this is a streaming request
            if (requestBody != null && originalRequest.url.toString().contains("/chat/completions")) {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val bodyString = buffer.readUtf8()
                isStreamingRequest = bodyString.contains("\"stream\":true") ||
                        bodyString.contains("\"stream\":\"full\"")
            }

            val requestBuilder = originalRequest.newBuilder()
            if (!isThirdParty && apiKey.isNotBlank()) {
                requestBuilder.addHeader("Content-Type", "application/json")
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            // IMPROVED: Set proper headers for streaming responses
            if (isStreamingRequest) {
                requestBuilder.addHeader("Accept", "text/event-stream")
                requestBuilder.addHeader("Cache-Control", "no-cache")
                requestBuilder.addHeader("Transfer-Encoding", "chunked") // Important for chunked transfers
                // ADDED: More streaming-specific headers
                requestBuilder.addHeader("X-Accel-Buffering", "no") // Disable proxy buffering
            } else {
                requestBuilder.addHeader("Accept", "application/json")
            }

            // Always set Connection: keep-alive for better performance
            requestBuilder.addHeader("Connection", "keep-alive")

            val request = requestBuilder.build()

            // Retry logic for network errors
            var retryCount = 0
            val maxRetries = 3
            var response: okhttp3.Response? = null
            var lastException: Exception? = null

            while (retryCount < maxRetries) {
                try {
                    // Don't retry if we have a valid response
                    if (response != null) break

                    // Execute the request
                    response = chain.proceed(request)

                    // Check for server errors that may benefit from a retry
                    if (response.code in 500..599 && retryCount < maxRetries - 1) {
                        Timber.w("Server error ${response.code}, retrying (attempt ${retryCount + 1})")
                        response.close() // Close this response
                        response = null // Clear for retry
                        retryCount++
                        Thread.sleep(1000L * (retryCount)) // Exponential backoff
                        continue
                    }

                    break // Success or non-retryable response

                } catch (e: Exception) {
                    lastException = e

                    // Only retry on network-related issues
                    val shouldRetry = when (e) {
                        is SocketTimeoutException,
                        is java.net.SocketException,
                        is okhttp3.internal.http2.StreamResetException -> true
                        else -> false
                    }

                    if (shouldRetry && retryCount < maxRetries - 1) {
                        Timber.w("Request failed with ${e.javaClass.simpleName}: ${e.message}, retrying (attempt ${retryCount + 1})")
                        retryCount++
                        Thread.sleep(1000L * (retryCount)) // Exponential backoff
                    } else {
                        Timber.e(e, "Request failed, not retrying: ${e.message}")
                        throw e // Rethrow if we can't retry
                    }
                }
            }

            // If we have a response, return it, otherwise throw the last exception
            response ?: throw lastException ?: IOException("Request failed after $maxRetries retries")
        }

        // Add the logging interceptor
        builder.addInterceptor(loggingInterceptor)

        // Build and return the client
        return builder.build()
    }
    /**
     * Improved adapter for streaming responses
     * Better handles incomplete JSON and large payloads
     */
    private class ImprovedStreamingAdapter : TypeAdapter<AimlApiResponse>() {
        private val gson = GsonBuilder()
            .setLenient() // Important for malformed JSON
            .create()

        override fun write(out: JsonWriter, value: AimlApiResponse?) {
            try {
                if (value == null) {
                    out.nullValue()
                    return
                }

                // Custom serialization for better control
                out.beginObject()

                // Write choices array
                out.name("choices")
                out.beginArray()
                for (choice in value.choices!!) {
                    out.beginObject()

                    // Write message
                    out.name("message")
                    out.beginObject()
                    out.name("role")
                    out.value(choice.message?.role ?: "assistant")
                    out.name("content")
                    out.value(choice.message?.content ?: "")
                    out.endObject()

                    // Write finish reason
                    out.name("finish_reason")
                    out.value(choice.finishReason ?: "stop")

                    out.endObject()
                }
                out.endArray()

                // Write usage info if available
                if (value.usage != null) {
                    out.name("usage")
                    out.beginObject()
                    out.name("prompt_tokens")
                    out.value(value.usage.promptTokens)
                    out.name("completion_tokens")
                    out.value(value.usage.completionTokens)
                    out.name("total_tokens")
                    out.value(value.usage.totalTokens)
                    out.endObject()
                }

                out.endObject()
            } catch (e: Exception) {
                Timber.e(e, "Error writing AimlApiResponse")
                out.nullValue()
            }
        }

        override fun read(reader: JsonReader): AimlApiResponse? {
            reader.isLenient = true
            return try {
                // Properly handle empty or malformed responses
                if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
                    reader.nextNull()
                    return createEmptyResponse()
                }

                // Use Gson for normal parsing
                val adapter = gson.getAdapter(AimlApiResponse::class.java)
                val result = adapter.read(reader)

                // Handle null content in messages
                result?.choices?.forEach { choice ->
                    if (choice.message?.content == null) {
                        choice.message?.content = ""
                    }
                }

                result
            } catch (e: Exception) {
                Timber.d("Error parsing streaming response: ${e.message}")
                // Return a valid but empty response instead of null
                createEmptyResponse()
            }
        }

        private fun createEmptyResponse(): AimlApiResponse {
            return AimlApiResponse(
                choices = listOf(
                    AimlApiResponse.Choice(
                        message = AimlApiResponse.Message(
                            role = "assistant",
                            content = ""
                        ),
                        finishReason = "stop"
                    )
                ),
                usage = AimlApiResponse.Usage(0, 0, 0)
            )
        }
    }

    /**
     * Flexible JSON adapter for handling arbitrary JSON structures
     * Improved to better handle streaming data
     */
    private class FlexibleJsonAdapter : TypeAdapter<Any>() {
        private val gson = GsonBuilder()
            .setLenient()
            .create()

        override fun write(out: JsonWriter, value: Any?) {
            try {
                when (value) {
                    null -> out.nullValue()
                    is Number -> out.value(value)
                    is Boolean -> out.value(value)
                    is String -> out.value(value)
                    is List<*> -> {
                        out.beginArray()
                        value.forEach { item -> write(out, item) }
                        out.endArray()
                    }
                    is Map<*, *> -> {
                        out.beginObject()
                        value.forEach { (k, v) ->
                            out.name(k.toString())
                            write(out, v)
                        }
                        out.endObject()
                    }
                    else -> gson.toJson(gson.toJsonTree(value), out)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing value: $value")
                out.nullValue()
            }
        }

        override fun read(reader: JsonReader): Any {
            reader.isLenient = true
            try {
                return when (reader.peek()) {
                    com.google.gson.stream.JsonToken.NULL -> {
                        reader.nextNull()
                        null as Any
                    }
                    com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean()
                    com.google.gson.stream.JsonToken.NUMBER -> {
                        val numStr = reader.nextString()
                        if (numStr.contains('.')) numStr.toDouble() else numStr.toLong()
                    }
                    com.google.gson.stream.JsonToken.STRING -> reader.nextString()
                    com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                        val list = mutableListOf<Any?>()
                        reader.beginArray()
                        while (reader.hasNext()) list.add(read(reader))
                        reader.endArray()
                        list
                    }
                    com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                        val map = mutableMapOf<String, Any?>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            map[reader.nextName()] = read(reader)
                        }
                        reader.endObject()
                        map
                    }
                    else -> {
                        reader.skipValue()
                        null as Any
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reading JSON value: ${e.message}")
                return null as Any
            }
        }
    }

    /**
     * HTTP Cache Interceptor for better performance
     */
    private class CacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val response = chain.proceed(chain.request())
            
            // Don't cache streaming responses
            val isStreaming = response.header("Content-Type")?.contains("text/event-stream") == true
            
            return if (!isStreaming) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=300") // 5 minutes cache
                    .build()
            } else {
                response
            }
        }
    }

    /**
     * Request Deduplication Interceptor to prevent duplicate API calls
     */
    private class RequestDeduplicationInterceptor : Interceptor {
        private val ongoingRequests = ConcurrentHashMap<String, okhttp3.Call>()
        
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val requestKey = generateRequestKey(request)
            
            // Check for ongoing identical requests (excluding streaming)
            val isStreaming = request.header("Accept")?.contains("text/event-stream") == true
            
            if (!isStreaming) {
                ongoingRequests[requestKey]?.let { ongoingCall ->
                    if (!ongoingCall.isCanceled() && !ongoingCall.isExecuted()) {
                        Timber.d("Deduplicating request: $requestKey")
                        // Wait for the ongoing request to complete
                        try {
                            return ongoingCall.execute()
                        } catch (e: Exception) {
                            ongoingRequests.remove(requestKey)
                        }
                    }
                }
            }
            
            val call = chain.call()
            if (!isStreaming) {
                ongoingRequests[requestKey] = call
            }
            
            return try {
                val response = chain.proceed(request)
                ongoingRequests.remove(requestKey)
                response
            } catch (e: Exception) {
                ongoingRequests.remove(requestKey)
                throw e
            }
        }
        
        private fun generateRequestKey(request: okhttp3.Request): String {
            val body = request.body?.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            } ?: ""
            
            return "${request.method}:${request.url}:${body.hashCode()}"
        }
    }
}