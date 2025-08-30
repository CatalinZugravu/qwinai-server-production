package com.cyberflux.qwinai.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

/**
 * Complete API service interface for all AI model providers
 * Supports chat, audio, images, files, streaming, and more
 */
interface AimlApiService {

    // ==================== CHAT COMPLETIONS ====================

    /**
     * Standard chat completions
     */
    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    fun sendMessage(@Body request: AimlApiRequest): Call<AimlApiResponse>

    /**
     * Raw request body for flexible API calls
     */
    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    fun sendRawMessage(@Body requestBody: RequestBody): Call<ResponseBody>

    /**
     * Raw streaming request
     */
    @Streaming
    @POST("chat/completions")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream",
        "Cache-Control: no-cache"
    )
    suspend fun sendRawMessageStreaming(@Body requestBody: RequestBody): Response<ResponseBody>

    /**
     * Multipart chat with files
     */
    @Multipart
    @POST("chat/completions")
    fun sendMessageWithFiles(
        @Part("request") request: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): Call<AimlApiResponse>

    /**
     * Streaming multipart chat with files
     */
    @Streaming
    @Multipart
    @POST("chat/completions")
    @Headers("Accept: text/event-stream")
    suspend fun sendMessageWithFilesStreaming(
        @Part("request") request: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): Response<ResponseBody>

    // ==================== LEGACY COMPLETIONS ====================

    /**
     * Legacy completions endpoint (for older models)
     */
    @POST("completions")
    @Headers("Content-Type: application/json")
    fun sendCompletion(@Body request: RequestBody): Call<AimlApiResponse>

    /**
     * Streaming legacy completions
     */
    @Streaming
    @POST("completions")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream"
    )
    suspend fun sendCompletionStreaming(@Body request: RequestBody): Response<ResponseBody>

    // ==================== AUDIO ====================

    /**
     * Text-to-Speech (TTS)
     */
    @POST("audio/speech")
    @Headers("Content-Type: application/json")
    fun generateSpeech(@Body request: RequestBody): Call<ResponseBody>

    /**
     * Speech-to-Text (STT) - Transcription
     */
    @Multipart
    @POST("audio/transcriptions")
    fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language") language: RequestBody? = null,
        @Part("prompt") prompt: RequestBody? = null,
        @Part("response_format") responseFormat: RequestBody? = null,
        @Part("temperature") temperature: RequestBody? = null
    ): Call<AimlApiResponse>

    /**
     * Speech-to-Text (STT) - Translation
     */
    @Multipart
    @POST("audio/translations")
    fun translateAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("prompt") prompt: RequestBody? = null,
        @Part("response_format") responseFormat: RequestBody? = null,
        @Part("temperature") temperature: RequestBody? = null
    ): Call<AimlApiResponse>

    /**
     * Audio generation with streaming
     */
    @Streaming
    @POST("audio/speech")
    @Headers("Content-Type: application/json")
    suspend fun generateSpeechStreaming(@Body request: RequestBody): Response<ResponseBody>

    // ==================== IMAGES ====================

    /**
     * Image generation
     */
    @POST("images/generations")
    @Headers("Content-Type: application/json")
    fun generateImage(@Body request: RequestBody): Call<AimlApiResponse>

    /**
     * Image editing
     */
    @Multipart
    @POST("images/edits")
    fun editImage(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody,
        @Part mask: MultipartBody.Part? = null,
        @Part("model") model: RequestBody? = null,
        @Part("n") n: RequestBody? = null,
        @Part("size") size: RequestBody? = null,
        @Part("response_format") responseFormat: RequestBody? = null,
        @Part("user") user: RequestBody? = null
    ): Call<AimlApiResponse>

    /**
     * Image variations
     */
    @Multipart
    @POST("images/variations")
    fun createImageVariation(
        @Part image: MultipartBody.Part,
        @Part("model") model: RequestBody? = null,
        @Part("n") n: RequestBody? = null,
        @Part("response_format") responseFormat: RequestBody? = null,
        @Part("size") size: RequestBody? = null,
        @Part("user") user: RequestBody? = null
    ): Call<AimlApiResponse>

    // ==================== FILES ====================

    /**
     * Upload file
     */
    @Multipart
    @POST("files")
    fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: RequestBody
    ): Call<AimlApiResponse>

    /**
     * List files
     */
    @GET("files")
    fun listFiles(): Call<AimlApiResponse>

    /**
     * Get file info
     */
    @GET("files/{file_id}")
    fun getFile(@Path("file_id") fileId: String): Call<AimlApiResponse>

    /**
     * Delete file
     */
    @DELETE("files/{file_id}")
    fun deleteFile(@Path("file_id") fileId: String): Call<AimlApiResponse>

    /**
     * Download file content
     */
    @GET("files/{file_id}/content")
    fun downloadFile(@Path("file_id") fileId: String): Call<ResponseBody>

    // ==================== MODELS ====================

    /**
     * List available models
     */
    @GET("models")
    fun listModels(): Call<AimlApiResponse>

    /**
     * Get specific model information
     */
    @GET("models/{model}")
    fun getModel(@Path("model") model: String): Call<AimlApiResponse>

    // ==================== EMBEDDINGS ====================

    /**
     * Create embeddings
     */
    @POST("embeddings")
    @Headers("Content-Type: application/json")
    fun createEmbeddings(@Body request: RequestBody): Call<AimlApiResponse>

    // ==================== FINE-TUNING ====================

    /**
     * Create fine-tuning job
     */
    @POST("fine_tuning/jobs")
    @Headers("Content-Type: application/json")
    fun createFineTuningJob(@Body request: RequestBody): Call<AimlApiResponse>

    /**
     * List fine-tuning jobs
     */
    @GET("fine_tuning/jobs")
    fun listFineTuningJobs(
        @Query("after") after: String? = null,
        @Query("limit") limit: Int? = null
    ): Call<AimlApiResponse>

    /**
     * Get fine-tuning job
     */
    @GET("fine_tuning/jobs/{fine_tuning_job_id}")
    fun getFineTuningJob(@Path("fine_tuning_job_id") jobId: String): Call<AimlApiResponse>

    /**
     * Cancel fine-tuning job
     */
    @POST("fine_tuning/jobs/{fine_tuning_job_id}/cancel")
    fun cancelFineTuningJob(@Path("fine_tuning_job_id") jobId: String): Call<AimlApiResponse>

    // ==================== MODERATION ====================

    /**
     * Content moderation
     */
    @POST("moderations")
    @Headers("Content-Type: application/json")
    fun moderateContent(@Body request: RequestBody): Call<AimlApiResponse>

    // ==================== ASSISTANTS ====================

    /**
     * Create assistant
     */
    @POST("assistants")
    @Headers("Content-Type: application/json")
    fun createAssistant(@Body request: RequestBody): Call<AimlApiResponse>

    /**
     * List assistants
     */
    @GET("assistants")
    fun listAssistants(
        @Query("limit") limit: Int? = null,
        @Query("order") order: String? = null,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null
    ): Call<AimlApiResponse>

    /**
     * Get assistant
     */
    @GET("assistants/{assistant_id}")
    fun getAssistant(@Path("assistant_id") assistantId: String): Call<AimlApiResponse>

    /**
     * Modify assistant
     */
    @POST("assistants/{assistant_id}")
    @Headers("Content-Type: application/json")
    fun modifyAssistant(
        @Path("assistant_id") assistantId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * Delete assistant
     */
    @DELETE("assistants/{assistant_id}")
    fun deleteAssistant(@Path("assistant_id") assistantId: String): Call<AimlApiResponse>

    // ==================== THREADS ====================

    /**
     * Create thread
     */
    @POST("threads")
    @Headers("Content-Type: application/json")
    fun createThread(@Body request: RequestBody? = null): Call<AimlApiResponse>

    /**
     * Get thread
     */
    @GET("threads/{thread_id}")
    fun getThread(@Path("thread_id") threadId: String): Call<AimlApiResponse>

    /**
     * Modify thread
     */
    @POST("threads/{thread_id}")
    @Headers("Content-Type: application/json")
    fun modifyThread(
        @Path("thread_id") threadId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * Delete thread
     */
    @DELETE("threads/{thread_id}")
    fun deleteThread(@Path("thread_id") threadId: String): Call<AimlApiResponse>

    // ==================== MESSAGES ====================

    /**
     * Create message in thread
     */
    @POST("threads/{thread_id}/messages")
    @Headers("Content-Type: application/json")
    fun createMessage(
        @Path("thread_id") threadId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * List messages in thread
     */
    @GET("threads/{thread_id}/messages")
    fun listMessages(
        @Path("thread_id") threadId: String,
        @Query("limit") limit: Int? = null,
        @Query("order") order: String? = null,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null
    ): Call<AimlApiResponse>

    /**
     * Get specific message
     */
    @GET("threads/{thread_id}/messages/{message_id}")
    fun getMessage(
        @Path("thread_id") threadId: String,
        @Path("message_id") messageId: String
    ): Call<AimlApiResponse>

    /**
     * Modify message
     */
    @POST("threads/{thread_id}/messages/{message_id}")
    @Headers("Content-Type: application/json")
    fun modifyMessage(
        @Path("thread_id") threadId: String,
        @Path("message_id") messageId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    // ==================== RUNS ====================

    /**
     * Create run
     */
    @POST("threads/{thread_id}/runs")
    @Headers("Content-Type: application/json")
    fun createRun(
        @Path("thread_id") threadId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * Streaming run
     */
    @Streaming
    @POST("threads/{thread_id}/runs")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream"
    )
    suspend fun createRunStreaming(
        @Path("thread_id") threadId: String,
        @Body request: RequestBody
    ): Response<ResponseBody>

    /**
     * List runs
     */
    @GET("threads/{thread_id}/runs")
    fun listRuns(
        @Path("thread_id") threadId: String,
        @Query("limit") limit: Int? = null,
        @Query("order") order: String? = null,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null
    ): Call<AimlApiResponse>

    /**
     * Get run
     */
    @GET("threads/{thread_id}/runs/{run_id}")
    fun getRun(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String
    ): Call<AimlApiResponse>

    /**
     * Modify run
     */
    @POST("threads/{thread_id}/runs/{run_id}")
    @Headers("Content-Type: application/json")
    fun modifyRun(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * Cancel run
     */
    @POST("threads/{thread_id}/runs/{run_id}/cancel")
    fun cancelRun(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String
    ): Call<AimlApiResponse>

    /**
     * Submit tool outputs to run
     */
    @POST("threads/{thread_id}/runs/{run_id}/submit_tool_outputs")
    @Headers("Content-Type: application/json")
    fun submitToolOutputs(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * Submit tool outputs with streaming
     */
    @Streaming
    @POST("threads/{thread_id}/runs/{run_id}/submit_tool_outputs")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream"
    )
    suspend fun submitToolOutputsStreaming(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Body request: RequestBody
    ): Response<ResponseBody>

    // ==================== VECTOR STORES ====================

    /**
     * Create vector store
     */
    @POST("vector_stores")
    @Headers("Content-Type: application/json")
    fun createVectorStore(@Body request: RequestBody): Call<AimlApiResponse>

    /**
     * List vector stores
     */
    @GET("vector_stores")
    fun listVectorStores(
        @Query("limit") limit: Int? = null,
        @Query("order") order: String? = null,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null
    ): Call<AimlApiResponse>

    /**
     * Get vector store
     */
    @GET("vector_stores/{vector_store_id}")
    fun getVectorStore(@Path("vector_store_id") vectorStoreId: String): Call<AimlApiResponse>

    /**
     * Modify vector store
     */
    @POST("vector_stores/{vector_store_id}")
    @Headers("Content-Type: application/json")
    fun modifyVectorStore(
        @Path("vector_store_id") vectorStoreId: String,
        @Body request: RequestBody
    ): Call<AimlApiResponse>

    /**
     * Delete vector store
     */
    @DELETE("vector_stores/{vector_store_id}")
    fun deleteVectorStore(@Path("vector_store_id") vectorStoreId: String): Call<AimlApiResponse>

    // ==================== BATCH ====================

    /**
     * Create batch
     */
    @POST("batches")
    @Headers("Content-Type: application/json")
    fun createBatch(@Body request: RequestBody): Call<AimlApiResponse>

    /**
     * Get batch
     */
    @GET("batches/{batch_id}")
    fun getBatch(@Path("batch_id") batchId: String): Call<AimlApiResponse>

    /**
     * Cancel batch
     */
    @POST("batches/{batch_id}/cancel")
    fun cancelBatch(@Path("batch_id") batchId: String): Call<AimlApiResponse>

    /**
     * List batches
     */
    @GET("batches")
    fun listBatches(
        @Query("after") after: String? = null,
        @Query("limit") limit: Int? = null
    ): Call<AimlApiResponse>

    // ==================== CUSTOM ENDPOINTS ====================

    /**
     * Generic GET request for custom endpoints
     */
    @GET
    fun genericGet(@Url url: String): Call<ResponseBody>

    /**
     * Generic POST request for custom endpoints
     */
    @POST
    @Headers("Content-Type: application/json")
    fun genericPost(@Url url: String, @Body request: RequestBody): Call<ResponseBody>

    /**
     * Generic PUT request
     */
    @PUT
    @Headers("Content-Type: application/json")
    fun genericPut(@Url url: String, @Body request: RequestBody): Call<ResponseBody>

    /**
     * Generic DELETE request
     */
    @DELETE
    fun genericDelete(@Url url: String): Call<ResponseBody>

    /**
     * Generic multipart POST
     */
    @Multipart
    @POST
    fun genericMultipartPost(
        @Url url: String,
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody>
    ): Call<ResponseBody>

    /**
     * Health check endpoint
     */
    @GET("health")
    fun healthCheck(): Call<ResponseBody>

    /**
     * API status
     */
    @GET("status")
    fun getStatus(): Call<AimlApiResponse>

    /**
     * Rate limit information
     */
    @GET("rate_limits")
    fun getRateLimits(): Call<AimlApiResponse>

    // ==================== OCR ====================

    /**
     * OCR endpoint for document and image processing
     */
    @Multipart
    @POST("v1/ocr")
    fun performOCR(
        @Part document: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody? = null,
        @Part("pages") pages: RequestBody? = null,
        @Part("include_images") includeImages: RequestBody? = null,
        @Part("image_limit") imageLimit: RequestBody? = null,
        @Part("image_min_size") imageMinSize: RequestBody? = null,
        @Part("max_tokens") maxTokens: RequestBody? = null
    ): Call<AimlApiResponse>

    // ==================== PROVIDER SPECIFIC ====================

    /**
     * Claude-specific endpoint
     */
    @POST("messages")
    @Headers("Content-Type: application/json")
    fun sendClaudeMessage(@Body request: RequestBody): Call<ResponseBody>

    /**
     * Claude streaming
     */
    @Streaming
    @POST("messages")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream"
    )
    suspend fun sendClaudeMessageStreaming(@Body request: RequestBody): Response<ResponseBody>

    /**
     * Gemini-specific endpoint
     */
    @POST("generateContent")
    @Headers("Content-Type: application/json")
    fun sendGeminiMessage(@Body request: RequestBody): Call<ResponseBody>

    /**
     * Gemini streaming
     */
    @Streaming
    @POST("streamGenerateContent")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream"
    )
    suspend fun sendGeminiMessageStreaming(@Body request: RequestBody): Response<ResponseBody>

    /**
     * Together.ai specific endpoint
     */
    @POST("inference")
    @Headers("Content-Type: application/json")
    fun sendTogetherMessage(@Body request: RequestBody): Call<ResponseBody>

    /**
     * Together.ai streaming
     */
    @Streaming
    @POST("inference")
    @Headers(
        "Content-Type: application/json",
        "Accept: text/event-stream"
    )
    suspend fun sendTogetherMessageStreaming(@Body request: RequestBody): Response<ResponseBody>

    // ==================== EXPERIMENTAL ====================

}