package com.cyberflux.qwinai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.AimlApiService
import com.cyberflux.qwinai.network.ModelApiHandler
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.network.StreamingHandler
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.UnifiedStreamingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Background service that continues AI response generation when the app is backgrounded
 */
class BackgroundAiService : Service() {
    
    private val binder = BackgroundAiBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val database by lazy { AppDatabase.getDatabase(this) }
    
    // Track active generation jobs
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val generationStates = ConcurrentHashMap<String, GenerationState>()
    
    // Notification constants
    private val CHANNEL_ID = "AI_GENERATION_CHANNEL"
    private val NOTIFICATION_ID = 1001
    
    data class GenerationState(
        val messageId: String,
        val conversationId: String,
        val isActive: Boolean = true,
        val progress: String = "",
        val startTime: Long = System.currentTimeMillis()
    )
    
    inner class BackgroundAiBinder : Binder() {
        fun getService(): BackgroundAiService = this@BackgroundAiService
    }
    
    /**
     * CRITICAL FIX: Request current progress for a specific message
     * This is called when MainActivity enters a conversation to get the latest state
     */
    fun requestCurrentProgress(messageId: String) {
        Timber.d("📡📡📡 RECEIVED REQUEST FOR CURRENT PROGRESS: $messageId")
        Timber.d("📡 Thread: ${Thread.currentThread().name}")
        Timber.d("📡 Active jobs: ${activeJobs.keys}")
        Timber.d("📡 Generation states: ${generationStates.keys}")
        
        try {
            // Check if we have an active generation for this message
            val generationState = generationStates[messageId]
            val activeJob = activeJobs[messageId]
            
            if (generationState != null && activeJob != null && activeJob.isActive) {
                Timber.d("🔄 Found active generation, broadcasting current progress")
                
                // Get current content from StreamingStateManager
                val session = UnifiedStreamingManager.getSession(messageId)
                if (session != null) {
                    val currentContent = session.getPartialContent()
                    if (currentContent.isNotEmpty()) {
                        // CRITICAL FIX: Use a special broadcast to indicate this is EXISTING content
                        broadcastExistingProgress(messageId, currentContent)
                        Timber.d("📤 Sent existing progress: ${currentContent.length} chars")
                    }
                }
            } else {
                Timber.d("ℹ️ No active generation found for messageId: $messageId")
                Timber.d("🔍 Debug: generationState=$generationState, activeJob=$activeJob, activeJobActive=${activeJob?.isActive}")
                
                // CRITICAL FIX: First check database for message state
                serviceScope.launch {
                    try {
                        val message = database.chatMessageDao().getMessageById(messageId)
                        if (message != null) {
                            val messageAge = System.currentTimeMillis() - message.lastModified
                            val hasContent = message.message.isNotEmpty()
                            val isStuckGenerating = message.isGenerating && hasContent && messageAge > 30_000 // 30 seconds old
                            
                            if (isStuckGenerating) {
                                Timber.d("🔧 Detected stuck generating message, fixing state")
                                
                                // Fix stuck state in database
                                val fixedMessage = message.copy(
                                    isGenerating = false,
                                    isLoading = false,
                                    showButtons = true,
                                    canContinueStreaming = false,
                                    lastModified = System.currentTimeMillis()
                                )
                                database.chatMessageDao().update(fixedMessage)
                                
                                // Send as completion
                                broadcastCompletion(messageId, message.message)
                                Timber.d("📤 Fixed stuck state and sent completion: ${message.message.length} chars")
                                
                            } else if (hasContent && !message.isGenerating) {
                                // Message is properly completed
                                broadcastCompletion(messageId, message.message)
                                Timber.d("📤 Sent completed content from database: ${message.message.length} chars")
                                
                            } else if (hasContent && message.isGenerating) {
                                // Message is still generating but has content
                                broadcastExistingProgress(messageId, message.message)
                                Timber.d("📤 Sent existing progress from database: ${message.message.length} chars")
                                
                            } else {
                                Timber.d("ℹ️ No meaningful content found for messageId: $messageId")
                            }
                        } else {
                            Timber.d("⚠️ Message not found in database: $messageId")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error checking message state: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error requesting current progress: ${e.message}")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("🚀 BackgroundAiService created")
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("🎯 BackgroundAiService started")
        
        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification("AI response generating..."))
        
        // Handle different actions
        when (intent?.action) {
            ACTION_START_GENERATION -> {
                val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
                val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
                
                if (messageId != null && conversationId != null) {
                    startBackgroundGeneration(messageId, conversationId)
                }
            }
            ACTION_STOP_GENERATION -> {
                val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
                if (messageId != null) {
                    stopGeneration(messageId)
                }
            }
            ACTION_STOP_ALL -> {
                stopAllGenerations()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("🛑 BackgroundAiService destroyed")
        stopAllGenerations()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for ongoing AI response generation"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QwinAI")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startBackgroundGeneration(messageId: String, conversationId: String) {
        Timber.d("🔄 Starting background generation for message: $messageId")
        
        // CRITICAL FIX: Check if we can continue existing streaming instead of restarting
        val existingSession = UnifiedStreamingManager.getSession(messageId)
        if (existingSession != null && UnifiedStreamingManager.getSession(messageId) != null) {
            Timber.d("✅ Found existing streaming session, continuing instead of restarting: $messageId")
            continueExistingStream(messageId, conversationId, existingSession)
            return
        }
        
        // Cancel existing job if any
        activeJobs[messageId]?.cancel()
        
        // Create generation state
        generationStates[messageId] = GenerationState(messageId, conversationId)
        
        // Start generation job
        val job = serviceScope.launch {
            try {
                // FIXED: Start new streaming session in StreamingStateManager
                UnifiedStreamingManager.createSession(
                    messageId = messageId,
                    conversationId = conversationId,
                    sessionId = "bg_stream_${messageId}_${System.currentTimeMillis()}",
                    modelId = "" // Will be set during generation
                )
                UnifiedStreamingManager.markAsBackgroundActive(messageId)
                
                continueAiGeneration(messageId, conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Error in background generation: ${e.message}")
                handleGenerationError(messageId, e.message ?: "Unknown error")
            } finally {
                // Clean up
                activeJobs.remove(messageId)
                generationStates.remove(messageId)
                
                // CRITICAL FIX: Enhanced service lifecycle management
                // Use improved shouldStopService logic
                if (shouldStopService()) {
                    Timber.d("🛑 Service should stop based on comprehensive checks, stopping service")
                    stopSelf()
                } else {
                    Timber.d("📍 Keeping service alive: activeJobs=${activeJobs.size}, backgroundSessions=${UnifiedStreamingManager.hasBackgroundActiveSessions()}, recentActivity=${hasRecentActivity()}")
                }
            }
        }
        
        activeJobs[messageId] = job
        updateNotification("Generating AI response...")
    }
    
    /**
     * CRITICAL FIX: Continue existing streaming session instead of restarting
     * This prevents token waste and provides seamless continuation
     */
    private fun continueExistingStream(messageId: String, conversationId: String, session: UnifiedStreamingManager.StreamingSession) {
        Timber.d("✅ Continuing existing streaming session: $messageId with ${session.getPartialContent().length} existing chars")
        
        // Mark as background active
        UnifiedStreamingManager.markAsBackgroundActive(messageId)
        
        // Update generation state
        generationStates[messageId] = GenerationState(messageId, conversationId)
        
        // CRITICAL FIX: Only broadcast existing content if it's substantial and not already shown
        val existingContent = session.getPartialContent()
        if (existingContent.isNotEmpty() && existingContent.length > 50) {
            // Add a small delay to ensure the UI is ready to receive the content
            serviceScope.launch {
                delay(100) // Small delay to avoid race conditions
                broadcastExistingProgress(messageId, existingContent)
                Timber.d("📤 Sent existing content to UI: ${existingContent.length} chars")
            }
        }
        
        // Check if this session is actually still generating or if it was completed
        val job = serviceScope.launch {
            try {
                // Load message from database to check current state
                val message = database.chatMessageDao().getMessageById(messageId)
                
                if (message != null && message.isGenerating) {
                    Timber.d("⚙️ Message is still generating, checking if generation is actually active")
                    
                    // CRITICAL FIX: Check if there's actually active generation happening
                    // If not, the message might be stuck in generating state
                    val messageAge = System.currentTimeMillis() - message.lastModified
                    val contentLength = existingContent.length
                    
                    // If message hasn't been updated recently and has substantial content, 
                    // it's likely completed but stuck in generating state
                    if (messageAge > 30_000 && contentLength > 100) { // 30 seconds old with content
                        Timber.d("🔍 Message appears stuck in generating state, marking as completed")
                        
                        // Update database to mark as completed
                        val completedMessage = message.copy(
                            message = existingContent,
                            partialContent = existingContent,
                            isGenerating = false,
                            isLoading = false,
                            showButtons = true,
                            canContinueStreaming = false,
                            lastModified = System.currentTimeMillis()
                        )
                        database.chatMessageDao().update(completedMessage)
                        
                        // Mark session as complete and broadcast final content
                        UnifiedStreamingManager.completeSession(messageId, true)
                        broadcastCompletion(messageId, existingContent)
                        
                        Timber.d("✅ Fixed stuck generating state and marked as completed")
                    } else {
                        Timber.d("⚙️ Message appears to be actively generating, just showing existing progress")
                        // Just show current progress - the original generation may still be running
                        updateNotification("Continuing AI response...")
                    }
                } else {
                    Timber.d("✓ Message generation appears to be completed")
                    // Mark session as complete and broadcast final content
                    UnifiedStreamingManager.completeSession(messageId, true)
                    broadcastCompletion(messageId, existingContent)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error continuing existing stream: ${e.message}")
                handleGenerationError(messageId, e.message ?: "Unknown error")
            } finally {
                // Clean up
                activeJobs.remove(messageId)
                generationStates.remove(messageId)
                
                // CRITICAL FIX: Enhanced service lifecycle management
                // Use improved shouldStopService logic
                if (shouldStopService()) {
                    Timber.d("🛑 Service should stop based on comprehensive checks, stopping service")
                    stopSelf()
                } else {
                    Timber.d("📍 Keeping service alive: activeJobs=${activeJobs.size}, backgroundSessions=${UnifiedStreamingManager.hasBackgroundActiveSessions()}, recentActivity=${hasRecentActivity()}")
                }
            }
        }
        
        activeJobs[messageId] = job
        updateNotification("Resuming AI response...")
    }
    
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun continueAiGeneration(messageId: String, conversationId: String) {
        // Load the incomplete message from database
        val message = database.chatMessageDao().getMessageById(messageId)
        if (message == null) {
            Timber.e("Message not found: $messageId")
            return
        }
        
        // Load conversation context
        val conversationMessages = database.chatMessageDao().getMessagesByConversation(conversationId)
        
        // Build API request
        val apiRequest = buildApiRequestFromContext(conversationMessages, message)
        
        // Get API service
        val apiService = RetrofitInstance.getApiService(
            RetrofitInstance.ApiServiceType.AIMLAPI,
            AimlApiService::class.java
        )
        
        val modelId = message.modelId ?: ModelManager.selectedModel.id
        
        // Check if this is the Mistral OCR model and handle it specially
        val response = if (modelId == ModelManager.MISTRAL_OCR_ID) {
            Timber.d("🔍 Detected Mistral OCR model, using OCR endpoint")
            
            // For OCR models, we need to use a different approach
            // Since OCR is not streaming, we should delegate to OCRFileUploadService
            throw Exception("OCR models should be handled by OCRFileUploadService, not BackgroundAiService")
            
        } else {
            // Create request body for regular models
            val requestBody = ModelApiHandler.createRequestBody(
                apiRequest = apiRequest,
                modelId = modelId,
                context = this@BackgroundAiService,
                audioEnabled = false
            )
            
            Timber.d("🌐 Making background API call for message: $messageId")
            
            withContext(Dispatchers.IO) {
                apiService.sendRawMessageStreaming(requestBody)
            }
        }
        
        if (response.isSuccessful) {
            val responseBody = response.body()
            if (responseBody != null) {
                Timber.d("✅ Processing background streaming response")
                
                // Process streaming response with database updates
                StreamingHandler.processBackgroundStreamingResponse(
                    responseBody = responseBody,
                    messageId = messageId,
                    database = database,
                    onProgress = { partialContent ->
                        // CRITICAL FIX: Save IMMEDIATELY to database every time, no matter what
                        serviceScope.launch {
                            try {
                                val message = database.chatMessageDao().getMessageById(messageId)
                                if (message != null) {
                                    // CRITICAL: Limit content size to prevent OOM
                                    val safePartialContent = if (partialContent.length > 40_000) {
                                        Timber.w("Large content detected, trimming to prevent OOM")
                                        partialContent.takeLast(40_000)
                                    } else {
                                        partialContent
                                    }
                                    
                                    val updatedMessage = message.copy(
                                        message = safePartialContent,
                                        partialContent = safePartialContent,
                                        lastModified = System.currentTimeMillis(),
                                        isGenerating = true,
                                        isLoading = false,  // CRITICAL: Never show loading when we have content!
                                        canContinueStreaming = true,
                                        showButtons = false  // Hide buttons during generation
                                    )
                                    
                                    // 🐛 DEBUG: Log every database save
                                    Timber.d("🐛🐛🐛 BACKGROUND SERVICE IMMEDIATE SAVE:")
                                    Timber.d("🐛 Message ID: $messageId")
                                    Timber.d("🐛 Content length: ${safePartialContent.length}")
                                    Timber.d("🐛 Content preview: '${safePartialContent.take(100)}...'")
                                    Timber.d("🐛 isGenerating: ${updatedMessage.isGenerating}")
                                    Timber.d("🐛 isLoading: ${updatedMessage.isLoading}")
                                    
                                    // CRITICAL: Immediate database save within coroutine
                                    database.chatMessageDao().update(updatedMessage)
                                    
                                    // Verify the save worked immediately
                                    val verifyMessage = database.chatMessageDao().getMessageById(messageId)
                                    Timber.d("🐛 ✅ VERIFIED - Content in DB: ${verifyMessage?.message?.length} chars")
                                    
                                    // CRITICAL: Update streaming state with safe content
                                    UnifiedStreamingManager.updateSessionContent(messageId, safePartialContent)
                                } else {
                                    Timber.e("🐛 ERROR: Message not found in database: $messageId")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "🐛 CRITICAL ERROR saving progress to database: ${e.message}")
                            }
                        }
                        
                        updateNotification("AI writing: ${partialContent.take(50)}...")
                        
                        // Broadcast progress with throttling for UI responsiveness
                        broadcastProgress(messageId, partialContent)
                    },
                    onComplete = { finalContent ->
                        Timber.d("🎉 Background generation completed for message: $messageId")
                        updateNotification("AI response completed")
                        
                        // CRITICAL: Mark message as completed in database
                        serviceScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val message = database.chatMessageDao().getMessageById(messageId)
                                    if (message != null) {
                                        val completedMessage = message.copy(
                                            message = finalContent,
                                            partialContent = finalContent,
                                            isGenerating = false,
                                            isLoading = false,
                                            showButtons = true,
                                            canContinueStreaming = false,
                                            lastModified = System.currentTimeMillis(),
                                            completionTime = System.currentTimeMillis(),
                                            error = false // Ensure no error state
                                        )
                                        database.chatMessageDao().update(completedMessage)
                                        Timber.d("✅ Marked message as completed in database: ${finalContent.length} chars")
                                    } else {
                                        Timber.w("⚠️ Message not found in database during completion: $messageId")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error marking message as completed: ${e.message}")
                            }
                        }
                        
                        // CRITICAL: Complete streaming session
                        UnifiedStreamingManager.completeSession(messageId, true)
                        
                        broadcastCompletion(messageId, finalContent)
                    },
                    onError = { error ->
                        Timber.e("❌ Background generation failed: $error")
                        
                        // CRITICAL: Remove failed streaming session
                        UnifiedStreamingManager.completeSession(messageId, false)
                        
                        handleGenerationError(messageId, error)
                    }
                )
            }
        } else {
            val errorMessage = response.errorBody()?.string() ?: "API request failed"
            throw Exception("API error: ${response.code()} - $errorMessage")
        }
    }
    
    private fun buildApiRequestFromContext(messages: List<ChatMessage>, currentMessage: ChatMessage): com.cyberflux.qwinai.network.AimlApiRequest {
        // Build messages for API (similar to AiChatService logic)
        val apiMessages = mutableListOf<com.cyberflux.qwinai.network.AimlApiRequest.Message>()
        
        // Add system message if needed
        val modelId = currentMessage.modelId ?: ModelManager.selectedModel.id
        if (!isClaudeModel(modelId)) {
            apiMessages.add(com.cyberflux.qwinai.network.AimlApiRequest.Message(
                role = "system",
                content = "You are a helpful AI assistant. Continue providing accurate and detailed responses."
            ))
        }
        
        // Add conversation history (excluding the current generating message)
        messages.filter { it.id != currentMessage.id && !it.isGenerating }
            .sortedBy { it.timestamp }
            .forEach { message ->
                if (message.isUser) {
                    val content = if (!message.prompt.isNullOrBlank()) {
                        message.prompt // Use extracted content for files
                    } else {
                        message.message
                    }
                    apiMessages.add(com.cyberflux.qwinai.network.AimlApiRequest.Message("user", content))
                } else if (message.message.isNotBlank()) {
                    apiMessages.add(com.cyberflux.qwinai.network.AimlApiRequest.Message("assistant", message.message))
                }
            }
        
        return com.cyberflux.qwinai.network.AimlApiRequest(
            model = modelId,
            messages = apiMessages,
            temperature = 0.7,
            stream = true,
            topA = null,
            parallelToolCalls = null,
            minP = null,
            useWebSearch = currentMessage.isForceSearch
        )
    }
    
    private fun getOriginalUserMessage(messages: List<ChatMessage>, aiMessageId: String): String? {
        // Find the user message that triggered this AI response
        val aiMessage = messages.find { it.id == aiMessageId }
        val parentMessageId = aiMessage?.parentMessageId
        
        return if (parentMessageId != null) {
            messages.find { it.id == parentMessageId }?.message
        } else {
            // Fallback: get the last user message before this AI message
            messages.filter { it.isUser && it.timestamp < (aiMessage?.timestamp ?: 0) }
                .maxByOrNull { it.timestamp }?.message
        }
    }
    
    private fun isClaudeModel(modelId: String): Boolean {
        return modelId.contains("claude", ignoreCase = true)
    }
    
    private fun stopGeneration(messageId: String) {
        Timber.d("⏹️ Stopping generation for message: $messageId")
        activeJobs[messageId]?.cancel()
        activeJobs.remove(messageId)
        generationStates.remove(messageId)
        
        // ADDED: Clean up streaming state
        UnifiedStreamingManager.completeSession(messageId, false)
        
        // CRITICAL FIX: Enhanced service lifecycle management for individual generation stop
        // Use improved shouldStopService logic
        if (shouldStopService()) {
            Timber.d("🛑 Service should stop based on comprehensive checks, stopping service")
            stopSelf()
        } else {
            Timber.d("📍 Keeping service alive: activeJobs=${activeJobs.size}, backgroundSessions=${UnifiedStreamingManager.hasBackgroundActiveSessions()}, recentActivity=${hasRecentActivity()}")
        }
    }
    
    private fun stopAllGenerations() {
        Timber.d("🛑 Stopping all background generations")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        generationStates.clear()
        
        // ADDED: Clean up all streaming states
        UnifiedStreamingManager.getActiveSessionsForConversation().forEach { session ->
            UnifiedStreamingManager.completeSession(session.messageId, false)
        }
    }
    
    private fun handleGenerationError(messageId: String, error: String) {
        serviceScope.launch {
            try {
                val message = database.chatMessageDao().getMessageById(messageId)
                if (message != null) {
                    // CRITICAL FIX: If message has substantial content, just mark as complete instead of adding error
                    val currentContent = message.message.trim()
                    if (currentContent.length > 50) {
                        Timber.d("✅ Message has substantial content (${currentContent.length} chars), marking as complete instead of adding error")
                        val completedMessage = message.copy(
                            isGenerating = false,
                            isLoading = false,
                            showButtons = true,
                            canContinueStreaming = false,
                            error = false,
                            lastModified = System.currentTimeMillis()
                        )
                        database.chatMessageDao().update(completedMessage)
                        broadcastCompletion(messageId, message.message)
                    } else {
                        // Only add error message if there's no substantial content
                        val errorMessage = message.copy(
                            message = message.message + "\n\n⚠️ Generation interrupted: $error\n\nTap 'Regenerate' to try again.",
                            isGenerating = false,
                            showButtons = true,
                            error = true
                        )
                        database.chatMessageDao().update(errorMessage)
                        broadcastError(messageId, error)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating message with error state: ${e.message}")
            }
        }
        updateNotification("Generation failed")
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    // Broadcast methods to communicate with MainActivity
    private fun broadcastProgress(messageId: String, content: String) {
        val intent = Intent(ACTION_GENERATION_PROGRESS).apply {
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_CONTENT, content)
            // Add explicit package to ensure delivery
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Timber.d("📡 Broadcasting progress for message: $messageId, content length: ${content.length}")
    }
    
    /**
     * CRITICAL FIX: Broadcast existing content without overwriting UI
     * This is used when reconnecting to ongoing generation to show current state
     */
    private fun broadcastExistingProgress(messageId: String, content: String) {
        Timber.d("📡📡📡 BROADCASTING EXISTING PROGRESS for message: $messageId")
        Timber.d("📡 Content length: ${content.length}")
        Timber.d("📡 Content preview: '${content.take(100)}...'")
        Timber.d("📡 Thread: ${Thread.currentThread().name}")
        
        val intent = Intent(ACTION_EXISTING_PROGRESS).apply {
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_CONTENT, content)
            // Add explicit package to ensure delivery
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Timber.d("📡 ✅ Broadcast sent successfully")
    }
    
    private fun broadcastCompletion(messageId: String, content: String) {
        Timber.d("📡📡📡 BROADCASTING COMPLETION for message: $messageId")
        Timber.d("📡 Content length: ${content.length}")
        Timber.d("📡 Content preview: '${content.take(100)}...'")
        Timber.d("📡 Thread: ${Thread.currentThread().name}")
        
        val intent = Intent(ACTION_GENERATION_COMPLETE).apply {
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_CONTENT, content)
            // Add explicit package to ensure delivery
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Timber.d("📡 ✅ Completion broadcast sent successfully")
    }
    
    private fun broadcastError(messageId: String, error: String) {
        val intent = Intent(ACTION_GENERATION_ERROR).apply {
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_ERROR, error)
            // Add explicit package to ensure delivery
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Timber.d("📡 Broadcasting error for message: $messageId, error: $error")
    }
    
    // Public methods for service control
    fun isGenerating(messageId: String): Boolean = activeJobs.containsKey(messageId)
    
    /**
     * CRITICAL FIX: Enhanced service lifecycle management
     * Determines if service should stop based on multiple factors
     */
    private fun shouldStopService(): Boolean {
        val hasActiveJobs = activeJobs.isNotEmpty()
        val hasActiveSessions = UnifiedStreamingManager.hasBackgroundActiveSessions()
        val hasRecent = hasRecentActivity()
        val hasPendingWork = generationStates.isNotEmpty()
        
        // Don't stop if there's any active work
        if (hasActiveJobs || hasActiveSessions || hasPendingWork) {
            return false
        }
        
        // Don't stop if there's been recent activity
        if (hasRecent) {
            return false
        }
        
        // Safe to stop
        return true
    }
    
    /**
     * Check if there's been recent activity that suggests we should keep service alive
     */
    private fun hasRecentActivity(): Boolean {
        val now = System.currentTimeMillis()
        val recentThreshold = 60_000L // 1 minute
        
        // Check for recent generation state activity
        val hasRecentGenerationActivity = generationStates.values.any { state ->
            (now - state.startTime) < recentThreshold
        }
        
        // Check for recent streaming sessions
        val hasRecentStreamingActivity = UnifiedStreamingManager.getActiveSessionsForConversation().any { session ->
            session.isActive && (now - session.startTime) < recentThreshold
        }
        
        return hasRecentGenerationActivity || hasRecentStreamingActivity
    }

    companion object {
        const val ACTION_START_GENERATION = "com.cyberflux.qwinai.START_GENERATION"
        const val ACTION_STOP_GENERATION = "com.cyberflux.qwinai.STOP_GENERATION"
        const val ACTION_STOP_ALL = "com.cyberflux.qwinai.STOP_ALL"
        
        const val ACTION_GENERATION_PROGRESS = "com.cyberflux.qwinai.GENERATION_PROGRESS"
        const val ACTION_EXISTING_PROGRESS = "com.cyberflux.qwinai.EXISTING_PROGRESS"
        const val ACTION_GENERATION_COMPLETE = "com.cyberflux.qwinai.GENERATION_COMPLETE"
        const val ACTION_GENERATION_ERROR = "com.cyberflux.qwinai.GENERATION_ERROR"
        
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_ERROR = "error"
        
        // Helper method to start the service
        fun startGeneration(context: Context, messageId: String, conversationId: String) {
            val intent = Intent(context, BackgroundAiService::class.java).apply {
                action = ACTION_START_GENERATION
                putExtra(EXTRA_MESSAGE_ID, messageId)
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
            context.startForegroundService(intent)
        }
        
        // Helper method to stop generation
        fun stopGeneration(context: Context, messageId: String) {
            val intent = Intent(context, BackgroundAiService::class.java).apply {
                action = ACTION_STOP_GENERATION
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
            context.startService(intent)
        }
    }
}