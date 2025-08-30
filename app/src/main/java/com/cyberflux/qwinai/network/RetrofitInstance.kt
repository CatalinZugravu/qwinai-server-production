package com.cyberflux.qwinai.network

import com.cyberflux.qwinai.ApiConfig
import com.cyberflux.qwinai.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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
    
    // Track active HTTP clients for cancellation
    private val activeClients = ConcurrentHashMap<String, OkHttpClient>()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    
    /**
     * Cancel all active requests using streaming cancellation manager and legacy methods
     */
    fun cancelAllRequests() {
        Timber.d("🛑🛑🛑 ENHANCED CANCELLATION: Starting comprehensive request cancellation")
        
        // Method 1: Cancel active streaming sessions
        Timber.d("🛑 Cancelling active streams via HTTP client cancellation")
        
        // Method 2: Cancel individually tracked calls (legacy)
        var cancelledCount = 0
        activeCalls.values.forEach { call ->
            if (!call.isCanceled()) {
                try {
                    call.cancel()
                    cancelledCount++
                    Timber.d("🛑 ✅ Cancelled tracked call: ${call.request().url}")
                } catch (e: Exception) {
                    Timber.e(e, "🛑 ❌ Error cancelling tracked call: ${e.message}")
                }
            }
        }
        activeCalls.clear()
        
        // Method 3: Cancel all calls on all active clients via dispatcher
        var totalDispatcherCalls = 0
        activeClients.values.forEachIndexed { index, client ->
            val dispatcher = client.dispatcher
            val runningCalls = dispatcher.runningCallsCount()
            val queuedCalls = dispatcher.queuedCallsCount()
            totalDispatcherCalls += runningCalls + queuedCalls
            
            if (runningCalls > 0 || queuedCalls > 0) {
                try {
                    dispatcher.cancelAll()
                    Timber.d("🛑 ✅ Client $index - Dispatcher cancellation successful")
                } catch (e: Exception) {
                    Timber.e(e, "🛑 ❌ Client $index - Error in dispatcher cancellation: ${e.message}")
                }
            }
        }
        
        Timber.d("🛑🛑🛑 ENHANCED CANCELLATION COMPLETE:")
        Timber.d("🛑   - Streaming sessions cancelled: handled by UnifiedStreamingManager")
        Timber.d("🛑   - Tracked calls cancelled: $cancelledCount")
        Timber.d("🛑   - Total dispatcher calls cancelled: $totalDispatcherCalls")
        Timber.d("🛑   - Active clients processed: ${activeClients.size}")
        
        // Clean up completed sessions - handled by UnifiedStreamingManager
    }

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

    /**
     * Create a specialized service for OCR operations with extended timeouts
     */
    fun createCustomOcrService(): AimlApiService {
        val config = apiConfigs[ApiServiceType.AIMLAPI] ?: throw IllegalArgumentException("AIMLAPI config not found")
        return createOcrService(config)
    }

    /**
     * Create OCR API service for DI
     */
    fun createOCRApiService(): OCRApiService {
        val config = apiConfigs[ApiServiceType.AIMLAPI] ?: throw IllegalArgumentException("AIMLAPI config not found")
        return createService(config, OCRApiService::class.java)
    }

    private fun <T> createService(
        config: ApiConfig,
        serviceClass: Class<T>,
        isThirdParty: Boolean = false,
        forceHttp1: Boolean = false
    ): T {
        val moshi = Moshi.Builder()
            .add(AimlApiResponse::class.java, ImprovedStreamingAdapter())
            .build()

        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(createHttpClient(config.apiKey, isThirdParty))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(serviceClass)
    }

    /**
     * Create service specifically optimized for OCR operations
     */
    private fun createOcrService(config: ApiConfig): AimlApiService {
        val moshi = Moshi.Builder()
            .add(AimlApiResponse::class.java, ImprovedStreamingAdapter())
            .build()

        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(createOcrHttpClient(config.apiKey))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AimlApiService::class.java)
    }

    // In RetrofitInstance.kt, modify the createHttpClient method:
    fun createHttpClient(apiKey: String, isThirdParty: Boolean = false): OkHttpClient {
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
            private val delegate = getDefault()

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

        // Optimized timeouts for fastest response time
        builder.connectTimeout(10, TimeUnit.SECONDS)   // Fast connect for immediate response start
        builder.readTimeout(300, TimeUnit.SECONDS)     // Long read for streaming
        builder.writeTimeout(30, TimeUnit.SECONDS)     // Fast write for quick message sending
        builder.callTimeout(600, TimeUnit.SECONDS)     // Total timeout unchanged

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

                    // Only retry on network-related issues, but NOT on explicit cancellations
                    val shouldRetry = when (e) {
                        is SocketTimeoutException,
                        is java.net.SocketException,
                        is okhttp3.internal.http2.StreamResetException -> true
                        is java.io.IOException -> {
                            // Don't retry if it's a cancellation
                            !e.message.orEmpty().contains("Canceled", ignoreCase = true)
                        }
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

        // Add enhanced call tracking interceptor for cancellation support
        val callTrackingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val call = chain.call()
            
            // Track the call for potential cancellation (legacy system)
            val callId = "chat_${System.currentTimeMillis()}_${hashCode()}"
            activeCalls[callId] = call
            
            // Register streaming request (simplified)
            if (request.url.toString().contains("/chat/completions") || 
                request.header("Accept")?.contains("text/event-stream") == true) {
                val messageId = request.header("X-Message-ID") ?: "http_${System.currentTimeMillis()}"
                Timber.d("📡 Registered streaming HTTP call for message: $messageId")
            }
            
            try {
                val response = chain.proceed(request)
                // Remove from tracking when complete
                activeCalls.remove(callId)
                // Stream lifecycle managed by UnifiedStreamingManager
                response
            } catch (e: Exception) {
                // Remove from tracking on error
                activeCalls.remove(callId)
                // Stream cleanup handled by UnifiedStreamingManager
                throw e
            }
        }

        // Add the call tracking interceptor first
        builder.addInterceptor(callTrackingInterceptor)
        
        // Add the logging interceptor
        builder.addInterceptor(loggingInterceptor)

        // Build the client
        val client = builder.build()
        
        // Track the client for cancellation
        val clientId = "client_${System.currentTimeMillis()}_${hashCode()}"
        activeClients[clientId] = client
        
        Timber.d("🔧 Created and tracked HTTP client: $clientId")
        return client
    }

    /**
     * Create HTTP client specifically optimized for OCR file uploads with extended timeouts
     */
    private fun createOcrHttpClient(apiKey: String): OkHttpClient {
        // Create logging interceptor
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Timber.tag("OCR_API").d(message)
        }.apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BASIC
            else
                HttpLoggingInterceptor.Level.NONE
        }

        // Create dispatcher with higher limits for file uploads
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 4
        }

        val builder = OkHttpClient.Builder()

        // Use HTTP/1.1 for better file upload compatibility
        builder.protocols(listOf(Protocol.HTTP_1_1))
        builder.dispatcher(dispatcher)

        // Connection pool optimized for file uploads
        builder.connectionPool(ConnectionPool(
            10,  // Fewer idle connections for file uploads
            10,  // Longer keep-alive for large uploads
            TimeUnit.MINUTES
        ))

        builder.retryOnConnectionFailure(true)

        // Socket optimizations for file uploads
        builder.socketFactory(object : javax.net.SocketFactory() {
            private val delegate = getDefault()

            override fun createSocket(): java.net.Socket {
                return delegate.createSocket().apply {
                    receiveBufferSize = 65536  // Larger buffer for file uploads
                    sendBufferSize = 65536     // Larger buffer for file uploads
                    tcpNoDelay = false         // Enable Nagle's algorithm for file uploads
                }
            }

            override fun createSocket(host: String, port: Int) =
                delegate.createSocket(host, port).apply {
                    receiveBufferSize = 65536
                    sendBufferSize = 65536
                    tcpNoDelay = false
                }

            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) =
                delegate.createSocket(host, port, localHost, localPort).apply {
                    receiveBufferSize = 65536
                    sendBufferSize = 65536
                    tcpNoDelay = false
                }

            override fun createSocket(host: java.net.InetAddress, port: Int) =
                delegate.createSocket(host, port).apply {
                    receiveBufferSize = 65536
                    sendBufferSize = 65536
                    tcpNoDelay = false
                }

            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) =
                delegate.createSocket(address, port, localAddress, localPort).apply {
                    receiveBufferSize = 65536
                    sendBufferSize = 65536
                    tcpNoDelay = false
                }
        })

        // Extended timeouts specifically for OCR operations
        builder.connectTimeout(60, TimeUnit.SECONDS)   // 1 minute connect timeout
        builder.readTimeout(600, TimeUnit.SECONDS)     // 10 minutes read timeout for OCR processing
        builder.writeTimeout(300, TimeUnit.SECONDS)    // 5 minutes write timeout for large file uploads
        builder.callTimeout(900, TimeUnit.SECONDS)     // 15 minutes total timeout for complete OCR operation

        // Add auth interceptor
        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
            
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            // OCR-specific headers
            requestBuilder.addHeader("Accept", "application/json")
            requestBuilder.addHeader("Connection", "keep-alive")
            
            chain.proceed(requestBuilder.build())
        }

        // Add logging interceptor
        builder.addInterceptor(loggingInterceptor)

        // Build the client
        val client = builder.build()
        
        // Track the client for cancellation
        val clientId = "ocr_client_${System.currentTimeMillis()}_${hashCode()}"
        activeClients[clientId] = client
        
        Timber.d("🔧 Created and tracked OCR HTTP client: $clientId")
        return client
    }
    /**
     * Improved adapter for streaming responses
     * Better handles incomplete JSON and large payloads
     */
    private class ImprovedStreamingAdapter : JsonAdapter<AimlApiResponse>() {

        override fun toJson(writer: JsonWriter, value: AimlApiResponse?) {
            try {
                if (value == null) {
                    writer.nullValue()
                    return
                }

                // Custom serialization for better control
                writer.beginObject()

                // Write choices array
                writer.name("choices")
                writer.beginArray()
                for (choice in value.choices!!) {
                    writer.beginObject()

                    // Write message
                    writer.name("message")
                    writer.beginObject()
                    writer.name("role")
                    writer.value(choice.message?.role ?: "assistant")
                    writer.name("content")
                    writer.value(choice.message?.content ?: "")
                    writer.endObject()

                    // Write finish reason
                    writer.name("finish_reason")
                    writer.value(choice.finishReason ?: "stop")

                    writer.endObject()
                }
                writer.endArray()

                // Write usage info if available
                if (value.usage != null) {
                    writer.name("usage")
                    writer.beginObject()
                    writer.name("prompt_tokens")
                    writer.value(value.usage.promptTokens)
                    writer.name("completion_tokens")
                    writer.value(value.usage.completionTokens)
                    writer.name("total_tokens")
                    writer.value(value.usage.totalTokens)
                    writer.endObject()
                }

                writer.endObject()
            } catch (e: Exception) {
                Timber.e(e, "Error writing AimlApiResponse")
                writer.nullValue()
            }
        }

        override fun fromJson(reader: JsonReader): AimlApiResponse? {
            return try {
                // Properly handle empty or malformed responses
                if (reader.peek() == JsonReader.Token.NULL) {
                    reader.nextNull<AimlApiResponse?>()
                    return createEmptyResponse()
                }

                // Manual parsing without Kotlin reflection
                parseAimlApiResponse(reader)
            } catch (e: Exception) {
                Timber.d("Error parsing streaming response: ${e.message}")
                // Return a valid but empty response instead of null
                createEmptyResponse()
            }
        }

        private fun parseAimlApiResponse(reader: JsonReader): AimlApiResponse {
            val choices = mutableListOf<AimlApiResponse.Choice>()
            var usage: AimlApiResponse.Usage? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "choices" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            choices.add(parseChoice(reader))
                        }
                        reader.endArray()
                    }
                    "usage" -> {
                        usage = parseUsage(reader)
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AimlApiResponse(
                choices = choices.ifEmpty { 
                    listOf(AimlApiResponse.Choice(
                        message = AimlApiResponse.Message("assistant", ""),
                        finishReason = "stop"
                    ))
                },
                usage = usage ?: AimlApiResponse.Usage(0, 0, 0)
            )
        }

        private fun parseChoice(reader: JsonReader): AimlApiResponse.Choice {
            var message: AimlApiResponse.Message? = null
            var finishReason: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "message" -> message = parseMessage(reader)
                    "finish_reason" -> finishReason = reader.nextString()
                    "delta" -> message = parseMessage(reader) // Handle streaming format
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AimlApiResponse.Choice(
                message = message ?: AimlApiResponse.Message("assistant", ""),
                finishReason = finishReason ?: "stop"
            )
        }

        private fun parseMessage(reader: JsonReader): AimlApiResponse.Message {
            var role = "assistant"
            var content = ""

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "role" -> role = reader.nextString()
                    "content" -> content = reader.nextString() ?: ""
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AimlApiResponse.Message(role, content)
        }

        private fun parseUsage(reader: JsonReader): AimlApiResponse.Usage {
            var promptTokens = 0
            var completionTokens = 0
            var totalTokens = 0

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "prompt_tokens" -> promptTokens = reader.nextInt()
                    "completion_tokens" -> completionTokens = reader.nextInt()
                    "total_tokens" -> totalTokens = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AimlApiResponse.Usage(promptTokens, completionTokens, totalTokens)
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
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            } ?: ""
            
            return "${request.method}:${request.url}:${body.hashCode()}"
        }
    }

    // Lazy initialization of API services
    val aimlApi: AimlApiService by lazy {
        getApiService(ApiServiceType.AIMLAPI, AimlApiService::class.java)
    }

    val togetherAi: AimlApiService by lazy {
        getApiService(ApiServiceType.TOGETHER_AI, AimlApiService::class.java)
    }
    
    /**
     * Create version service for forced update checks
     */
    fun createVersionService(): AppVersionService {
        // Use a simple HTTP client for version checks - no API key required
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
        
        val moshi = Moshi.Builder().build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.your-server.com/") // Replace with your server URL
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        return retrofit.create(AppVersionService::class.java)
    }
}