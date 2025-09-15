package com.cyberflux.qwinai.network

import android.content.Context
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.security.UserStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service for processing files through the server
 * Features:
 * - Multi-format file extraction (PDF, DOCX, XLSX, PPTX, TXT)
 * - Accurate token counting for different AI models
 * - Intelligent document chunking for large files
 * - Cost estimation and context window management
 */
class FileProcessingService private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: FileProcessingService? = null
        
        fun getInstance(context: Context): FileProcessingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileProcessingService(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // Long timeout for large files
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    // Let's check what the correct endpoint should be
    private val serverUrl = UserStateManager.SERVER_BASE_URL
    
    /**
     * Process a file and return extracted content with token analysis
     */
    suspend fun processFile(
        file: File,
        aiModel: String = "gpt-4",
        maxTokensPerChunk: Int = 6000
    ): FileProcessingResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔄 Processing file: ${file.name} for model: $aiModel")
            
            // Create multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody())
                .addFormDataPart("model", aiModel)
                .addFormDataPart("maxTokensPerChunk", maxTokensPerChunk.toString())
                .build()
            
            val request = Request.Builder()
                .url(serverUrl + "file-processor/process")
                .addHeader("Authorization", "Bearer ${BuildConfig.AIMLAPI_KEY}")
                .post(requestBody)
                .build()
            
            Timber.d("🔄 Making request to: ${serverUrl}file-processor/process")
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonResponse = response.body?.string()
                    if (jsonResponse != null) {
                        val result = parseProcessingResult(jsonResponse)
                        Timber.d("✅ File processed successfully: ${result.chunks.size} chunks, ${result.tokenAnalysis.totalTokens} tokens")
                        return@withContext result
                    } else {
                        throw Exception("Server returned empty response body")
                    }
                } else {
                    val error = response.body?.string() ?: "Unknown error"
                    Timber.e("❌ Server error: ${response.code} - $error")
                    
                    when (response.code) {
                        404 -> throw Exception("Server endpoint not found. Make sure the server is deployed and running at ${serverUrl}file-processor/process")
                        401, 403 -> throw Exception("Authentication failed. Check API key configuration.")
                        413 -> throw Exception("File too large. Maximum size is 50MB.")
                        415 -> throw Exception("Unsupported file type. Supported: PDF, DOCX, XLSX, PPTX, TXT")
                        429 -> throw Exception("Rate limit exceeded. Please try again later.")
                        500, 502, 503 -> throw Exception("Server error: $error. The server may be temporarily unavailable.")
                        else -> throw Exception("File processing failed (${response.code}): $error")
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error processing file: ${file.name}")
            throw e
        }
    }
    
    /**
     * Get a specific chunk for sending to AI model
     */
    suspend fun getOptimalChunksForModel(
        processedFile: FileProcessingResult,
        targetModel: String,
        maxContextTokens: Int? = null
    ): List<DocumentChunk> = withContext(Dispatchers.Default) {
        
        val contextLimit = maxContextTokens ?: when (targetModel.lowercase()) {
            "gpt-4" -> 8192
            "gpt-4-32k" -> 32768
            "gpt-3.5-turbo" -> 4096
            "claude-3" -> 100000
            "gemini-pro" -> 30720
            else -> 8192
        }
        
        // Filter chunks that fit in context window
        val suitableChunks = processedFile.chunks.filter { chunk ->
            chunk.tokenCount <= contextLimit * 0.8 // Leave 20% buffer for prompt
        }
        
        if (suitableChunks.isEmpty()) {
            Timber.w("⚠️ No chunks suitable for model $targetModel (context limit: $contextLimit)")
        }
        
        return@withContext suitableChunks
    }
    
    /**
     * Create optimized prompt with file content
     */
    fun createPromptWithFileContent(
        userPrompt: String,
        documentChunks: List<DocumentChunk>,
        fileName: String
    ): String {
        val promptBuilder = StringBuilder()
        
        promptBuilder.append("Please analyze the following document content and answer the user's question.\n\n")
        promptBuilder.append("📄 File: $fileName\n")
        promptBuilder.append("📊 Content: ${documentChunks.size} chunks, ")
        promptBuilder.append("${documentChunks.sumOf { it.tokenCount }} tokens total\n\n")
        
        promptBuilder.append("=== DOCUMENT CONTENT ===\n")
        documentChunks.forEachIndexed { index, chunk ->
            if (documentChunks.size > 1) {
                promptBuilder.append("--- Chunk ${index + 1}/${documentChunks.size} ---\n")
            }
            promptBuilder.append(chunk.text)
            promptBuilder.append("\n\n")
        }
        
        promptBuilder.append("=== USER QUESTION ===\n")
        promptBuilder.append(userPrompt)
        
        return promptBuilder.toString()
    }
    
    private fun parseProcessingResult(jsonString: String): FileProcessingResult {
        val json = JSONObject(jsonString)
        
        val tokenAnalysis = json.getJSONObject("tokenAnalysis")
        val chunks = json.getJSONArray("chunks")
        
        val documentChunks = mutableListOf<DocumentChunk>()
        for (i in 0 until chunks.length()) {
            val chunkJson = chunks.getJSONObject(i)
            documentChunks.add(
                DocumentChunk(
                    index = chunkJson.getInt("index"),
                    totalChunks = chunkJson.getInt("totalChunks"),
                    text = chunkJson.getString("text"),
                    tokenCount = chunkJson.getInt("tokenCount"),
                    characterCount = chunkJson.getInt("characterCount"),
                    preview = chunkJson.getString("preview")
                )
            )
        }
        
        return FileProcessingResult(
            success = json.getBoolean("success"),
            originalFileName = json.getString("originalFileName"),
            fileSize = json.getLong("fileSize"),
            mimeType = json.getString("mimeType"),
            extractedText = json.getJSONObject("extractedContent").getString("text"),
            tokenAnalysis = TokenAnalysis(
                totalTokens = tokenAnalysis.getInt("totalTokens"),
                contextLimit = tokenAnalysis.getInt("contextLimit"),
                estimatedCost = tokenAnalysis.getString("estimatedCost"),
                model = tokenAnalysis.getString("model"),
                exceedsContext = tokenAnalysis.getBoolean("exceedsContext")
            ),
            chunks = documentChunks
        )
    }
}

/**
 * Result of file processing
 */
data class FileProcessingResult(
    val success: Boolean,
    val originalFileName: String,
    val fileSize: Long,
    val mimeType: String,
    val extractedText: String,
    val tokenAnalysis: TokenAnalysis,
    val chunks: List<DocumentChunk>
)

/**
 * Token analysis for AI models
 */
data class TokenAnalysis(
    val totalTokens: Int,
    val contextLimit: Int,
    val estimatedCost: String,
    val model: String,
    val exceedsContext: Boolean
)

/**
 * Document chunk with metadata
 */
data class DocumentChunk(
    val index: Int,
    val totalChunks: Int,
    val text: String,
    val tokenCount: Int,
    val characterCount: Int,
    val preview: String
)