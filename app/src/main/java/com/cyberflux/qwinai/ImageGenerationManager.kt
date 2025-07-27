package com.cyberflux.qwinai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.widget.Toast
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.ImageGenerationHttpClient
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.utils.ImageGenerationUtils
import com.cyberflux.qwinai.utils.ModelManager
import kotlinx.coroutines.*
import org.json.JSONObject
import timber.log.Timber
import java.util.*
import kotlin.io.encoding.ExperimentalEncodingApi
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

class ImageGenerationManager(private val context: Context) {

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Handle a conversation image generation request, switching from GPT-4 to DALL-E
     */
    fun handleConversationalImageGeneration(
        conversationId: String,
        prompt: String,
        userMessageId: String,
        onUpdateMessage: (ChatMessage) -> Unit,
        onSaveMessage: (ChatMessage) -> Unit,
        onGenerationComplete: () -> Unit,
        onGenerationError: (String) -> Unit,
        isSubscribed: Boolean,
        onDecrementCredits: () -> Unit
    ) {
        // Create AI message for the image generation
        val aiMessageId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()

        val loadingMessage = ChatMessage(
            id = aiMessageId,
            conversationId = conversationId,
            message = JSONObject().apply {
                put("caption", "Generating image with DALL-E 3: $prompt")
                put("status", "generating")
                put("stage", "Starting image generation...")
                put("progress", 0)
                put("prompt", prompt) // Store prompt for regeneration
            }.toString(),
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isGenerating = true,
            showButtons = false,
            modelId = ModelManager.DALLE_3_ID,
            aiModel = "DALL-E 3",
            aiGroupId = groupId,
            parentMessageId = userMessageId,
            isGeneratedImage = true
        )

        // Send to UI for display
        onUpdateMessage(loadingMessage)

        // Save to database
        onSaveMessage(loadingMessage)

        // Deduct credits if not a subscriber
        if (!isSubscribed) {
            // Image generation costs multiple credits
            repeat(5) { onDecrementCredits() }
        }

        // Generate the image
        generateImage(loadingMessage, prompt, onUpdateMessage, onSaveMessage, onGenerationComplete, onGenerationError, isSubscribed)
    }

    /**
     * Generate image with beautiful staged animations
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun generateImage(
        message: ChatMessage,
        prompt: String,
        onUpdateMessage: (ChatMessage) -> Unit,
        onSaveMessage: (ChatMessage) -> Unit,
        onGenerationComplete: () -> Unit,
        onGenerationError: (String) -> Unit,
        isSubscribed: Boolean
    ) {
        mainScope.launch {
            try {
                // Define generation stages for visual progress
                val stages = listOf(
                    "Creating visual concepts...",
                    "Building composition...",
                    "Adding details and texture...",
                    "Refining lighting and colors...",
                    "Finalizing image..."
                )

                // Run through animation stages
                val stageJob = launch {
                    for (i in stages.indices) {
                        if (!isActive) break

                        val stage = stages[i]
                        val progress = ((i + 1) * 20).coerceAtMost(95) // 0 to 95%

                        val updatedJson = JSONObject(message.message)
                        updatedJson.put("stage", stage)
                        updatedJson.put("progress", progress)

                        // Update message in UI
                        val updatedMessage = message.copy(
                            message = updatedJson.toString()
                        )
                        onUpdateMessage(updatedMessage)

                        // Simulate processing time for the animation
                        delay(800)
                    }
                }

                // Make the actual API call with timeout
                val result = withTimeoutOrNull(360_000L) { // 6 minutes timeout
                    makeImageGenerationRequest(message, prompt, onUpdateMessage, onSaveMessage, onGenerationComplete, onGenerationError)
                }

                // Cancel stage animation
                stageJob.cancel()

                if (result == null) {
                    // Timeout occurred
                    val errorJson = JSONObject(message.message)
                    errorJson.put("status", "error")
                    errorJson.put("caption", "Image generation timed out. Please try with a simpler prompt.")

                    val errorMessage = message.copy(
                        message = errorJson.toString(),
                        isGenerating = false,
                        error = true
                    )

                    onUpdateMessage(errorMessage)
                    onSaveMessage(errorMessage)
                    onGenerationError("Generation timed out")

                    // Refund credits on timeout
                    if (!isSubscribed) {
                        refundCredits()
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Error generating image: ${e.message}")

                // Update message with error
                val errorJson = JSONObject(message.message)
                errorJson.put("status", "error")
                errorJson.put("caption", "Error: ${e.message}")

                val errorMessage = message.copy(
                    message = errorJson.toString(),
                    isGenerating = false,
                    error = true
                )

                onUpdateMessage(errorMessage)
                onSaveMessage(errorMessage)
                onGenerationError(e.message ?: "Unknown error")

                // Refund credits on error
                if (!isSubscribed) {
                    refundCredits()
                }
            }
        }
    }

    private suspend fun makeImageGenerationRequest(
        message: ChatMessage,
        prompt: String,
        onUpdateMessage: (ChatMessage) -> Unit,
        onSaveMessage: (ChatMessage) -> Unit,
        onGenerationComplete: () -> Unit,
        onGenerationError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create DALL-E request using the utility function from ImageGenerationUtils
            val requestJson = ImageGenerationUtils.createDalle3Request(
                prompt = prompt,
                size = "1024x1024", // Default size for conversation image generation
                style = "vivid",     // Default style
                quality = "standard", // Default quality
                responseFormat = "b64_json", // Use base64 for easier handling in chat
                enableProgressiveLoading = true
            )

            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            // Get API key and base URL from configuration
            val apiKey = getApiKey()
            val baseUrl = getBaseUrl()

            if (apiKey.isBlank()) {
                throw Exception("API key is missing")
            }

            // Create request with unique tag
            val request = Request.Builder()
                .url("${baseUrl}v1/images/generations")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .addHeader("X-Request-ID", UUID.randomUUID().toString())
                .tag("conversational_image_gen_${System.currentTimeMillis()}")
                .build()

            // Use CompletableDeferred for proper async handling
            val deferred = CompletableDeferred<Boolean>()

            // Execute request with dedicated image generation client
            ImageGenerationHttpClient.client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mainScope.launch {
                        val errorMessage = when (e) {
                            is SocketTimeoutException -> "Request timed out"
                            is java.net.UnknownHostException -> "Network connection error"
                            else -> e.message ?: "Network error"
                        }

                        // Update message with error
                        val errorJson = JSONObject(message.message)
                        errorJson.put("status", "error")
                        errorJson.put("caption", "Image generation failed: $errorMessage")

                        val errorMsg = message.copy(
                            message = errorJson.toString(),
                            isGenerating = false,
                            error = true
                        )

                        onUpdateMessage(errorMsg)
                        onSaveMessage(errorMsg)
                        onGenerationError(errorMessage)
                    }

                    if (!deferred.isCompleted) deferred.complete(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            mainScope.launch {
                                val errorDetail = parseErrorResponse(errorBody, response.code)

                                // Update message with error
                                val errorJson = JSONObject(message.message)
                                errorJson.put("status", "error")
                                errorJson.put("caption", "API error: $errorDetail")

                                val errorMessage = message.copy(
                                    message = errorJson.toString(),
                                    isGenerating = false,
                                    error = true
                                )

                                onUpdateMessage(errorMessage)
                                onSaveMessage(errorMessage)
                                onGenerationError(errorDetail)
                            }
                            if (!deferred.isCompleted) deferred.complete(false)
                            return
                        }

                        val responseBody = response.body?.string()
                        if (responseBody == null) {
                            mainScope.launch {
                                onGenerationError("Empty response from API")
                            }
                            if (!deferred.isCompleted) deferred.complete(false)
                            return
                        }

                        // Parse the response
                        val jsonResponse = JSONObject(responseBody)
                        val data = jsonResponse.getJSONArray("data")

                        if (data.length() > 0) {
                            val imageObject = data.getJSONObject(0)

                            // Extract base64 image
                            if (imageObject.has("b64_json")) {
                                val base64Image = imageObject.getString("b64_json")
                                val revisedPrompt = imageObject.optString("revised_prompt", prompt)

                                // Create the success response JSON
                                val successJson = JSONObject().apply {
                                    put("imageBase64", base64Image)
                                    put("prompt", prompt)
                                    put("revisedPrompt", revisedPrompt)
                                    put("status", "complete")
                                    put("caption", "Here's your generated image:")
                                    put("timestamp", System.currentTimeMillis())
                                }

                                // Update message with completed image
                                val completedMessage = message.copy(
                                    message = successJson.toString(),
                                    isGenerating = false,
                                    showButtons = true
                                )

                                mainScope.launch {
                                    onUpdateMessage(completedMessage)
                                    onSaveMessage(completedMessage)
                                    onGenerationComplete()

                                    // Provide haptic feedback
                                    provideHapticFeedback()

                                    // Show success toast
                                    Toast.makeText(context,
                                        "Image generated successfully!",
                                        Toast.LENGTH_SHORT).show()

                                    // Add a GPT-4 followup message
                                    addGPT4FollowupMessage(
                                        conversationId = message.conversationId,
                                        parentMessageId = message.id,
                                        prompt = prompt,
                                        onUpdateMessage = onUpdateMessage,
                                        onSaveMessage = onSaveMessage
                                    )
                                }

                                if (!deferred.isCompleted) deferred.complete(true)
                            } else {
                                mainScope.launch {
                                    onGenerationError("Response missing base64 image data")
                                }
                                if (!deferred.isCompleted) deferred.complete(false)
                            }
                        } else {
                            mainScope.launch {
                                onGenerationError("No images were generated")
                            }
                            if (!deferred.isCompleted) deferred.complete(false)
                        }
                    } catch (e: Exception) {
                        mainScope.launch {
                            onGenerationError("Error processing response: ${e.message}")
                        }
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
            })

            // Wait for completion
            return@withContext deferred.await()

        } catch (e: Exception) {
            Timber.e(e, "Error in makeImageGenerationRequest")
            throw e
        }
    }

    private fun parseErrorResponse(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            when {
                json.has("error") -> {
                    val error = json.getJSONObject("error")
                    error.optString("message", "Unknown error")
                }
                json.has("message") -> json.getString("message")
                else -> "API Error: $statusCode"
            }
        } catch (e: Exception) {
            "API Error: $statusCode"
        }
    }

    /**
     * Refund credits to the user
     */
    private fun refundCredits() {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val currentCredits = prefs.getInt("free_messages_left", 10)
            val newCredits = currentCredits + 5 // Refund the 5 credits

            prefs.edit().putInt("free_messages_left", newCredits).apply()

            Toast.makeText(context,
                "Credits refunded due to generation error",
                Toast.LENGTH_SHORT).show()

            Timber.d("Refunded 5 credits, new total: $newCredits")
        } catch (e: Exception) {
            Timber.e(e, "Error refunding credits")
        }
    }
    private fun createTimeoutInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val isImageGeneration = request.url.toString().contains("/images/generations")

            try {
                // For image generation, use a higher timeout
                val timeout = if (isImageGeneration) 180L else 60L

                // Update socket timeout if connection exists (correct way without assignment)
                chain.connection()?.socket()?.let { socket ->
                    socket.soTimeout = (timeout * 1000).toInt()
                    Timber.d("Set socket timeout to ${timeout}s for ${request.url}")
                }

                // Proceed with the request
                chain.proceed(request)
            } catch (e: SocketTimeoutException) {
                Timber.e("Socket timeout: ${e.message}")
                if (isImageGeneration) {
                    // Log image generation timeouts
                    Timber.e("Image generation timeout - might need longer timeouts or server-side optimization")
                }
                throw e
            }
        }
    }    /**
     * Add a GPT-4 followup message after image generation
     */
    private fun addGPT4FollowupMessage(
        conversationId: String,
        parentMessageId: String,
        prompt: String,
        onUpdateMessage: (ChatMessage) -> Unit,
        onSaveMessage: (ChatMessage) -> Unit
    ) {
        // Create a followup message
        val followupMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            message = "I've created this image using DALL-E 3 based on your prompt: \"$prompt\". " +
                    "What do you think? If you'd like any changes or a different style, just let me know.",
            isUser = false,
            timestamp = System.currentTimeMillis() + 1000, // Slightly later timestamp
            modelId = ModelManager.GPT_4_TURBO_ID, // Return to GPT-4
            aiModel = "GPT-4 Turbo", // Label as GPT-4
            aiGroupId = UUID.randomUUID().toString(),
            parentMessageId = parentMessageId
        )

        // Add a slight delay for natural conversation flow
        Handler(Looper.getMainLooper()).postDelayed({
            onUpdateMessage(followupMessage)
            onSaveMessage(followupMessage)
        }, 1500)
    }

    /**
     * Provide haptic feedback when image generation completes
     */
    private fun provideHapticFeedback() {
        try {
            HapticManager.strongVibration(context)
        } catch (e: Exception) {
            Timber.e(e, "Error providing haptic feedback")
        }
    }

    /**
     * Get API key from configuration
     */

    private fun getApiKey(): String {
        return RetrofitInstance.getApiKey(RetrofitInstance.ApiServiceType.AIMLAPI) ?: ""
    }

    /**
     * Get base URL from configuration
     */
    private fun getBaseUrl(): String {
        return RetrofitInstance.getBaseUrl(RetrofitInstance.ApiServiceType.AIMLAPI) ?: "https://api.aimlapi.com/"
    }

    /**
     * Helper enum for API service types
     */
    enum class ApiServiceType {
        AIMLAPI,
    }

    /**
     * Cancel any running generation jobs
     */
    fun cancelGeneration() {
        mainScope.coroutineContext.cancelChildren()
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        mainScope.cancel()
    }
}