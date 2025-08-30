package com.cyberflux.qwinai.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cyberflux.qwinai.network.AimlApiResponse
import com.cyberflux.qwinai.utils.FileProgressTracker
import com.cyberflux.qwinai.utils.ModelValidator
import java.util.*

/**
 * Simplified ChatMessage class with branch-related fields removed
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        androidx.room.Index(value = ["conversationId"], name = "index_chat_messages_conversation_id"),
        androidx.room.Index(value = ["timestamp"], name = "index_chat_messages_timestamp"),
        androidx.room.Index(value = ["isUser"], name = "index_chat_messages_is_user"),
        androidx.room.Index(value = ["modelId"], name = "index_chat_messages_model_id"),
        androidx.room.Index(value = ["conversationId", "timestamp"], name = "index_chat_messages_conversation_timestamp"),
        androidx.room.Index(value = ["userGroupId"], name = "index_chat_messages_user_group_id"),
        androidx.room.Index(value = ["aiGroupId"], name = "index_chat_messages_ai_group_id"),
        androidx.room.Index(value = ["parentMessageId"], name = "index_chat_messages_parent_message_id")
    ]
)
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    var message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),

    // Simplified versioning fields
    var isReloaded: Boolean = false,
    var isRegenerated: Boolean = false,
    var userGroupId: String? = null,
    var aiGroupId: String? = null,

    // Parent-child relationship
    var parentMessageId: String? = null,

    // UI state fields
    var showButtons: Boolean = false,
    var isGenerating: Boolean = false,
    var isLoading: Boolean = false,
    var isHidden: Boolean = false,
    var isEdited: Boolean = false,
    var isActive: Boolean = true,
    var displayMessage: String? = null,

    // Content type fields
    val isImage: Boolean = false,
    val isDocument: Boolean = false,
    val isCamera: Boolean = false,
    val isVoiceMessage: Boolean = false,
    val prompt: String? = null,
    
    // ADDED: Streaming continuation fields
    var streamingSessionId: String? = null,
    var partialContent: String = "",
    var streamingStartTime: Long? = null,
    var canContinueStreaming: Boolean = false,

    // Model and feature fields
    val modelId: String? = null,
    var aiModel: String? = null,
    val useDeepThink: Boolean = false,
    val useSearch: Boolean = false,

    // Web search fields
    var isWebSearchUsed: Boolean = false,
    val isReasoningUsed: Boolean = false,
    val reasoningLevel: String = "off",
    var isForceSearch: Boolean = false,

    // Process tracking fields
    var hasThinkingProcess: Boolean = false,
    var hasWebSearchProcess: Boolean = false,
    var thinkingProcess: String? = null,
    var webSearchProcess: String? = null,
    var isThinkingActive: Boolean = false,
    var isWebSearchActive: Boolean = false,
    var processCompleted: Boolean = false,
    var keepProcessVisible: Boolean = false,

    // Location awareness
    var locationContext: String? = null,
    var locationInfo: LocationInfo? = null,

    // Error handling
    var hasInconsistency: Boolean = false,
    var errorMessage: String? = null,
    var error: Boolean = false,

    // Versioning (enhanced)
    var versionIndex: Int = 0,
    var totalVersions: Int = 1,
    var messageVersions: MutableList<String> = mutableListOf(),
    val isGeneratedImage: Boolean = false,
    val imagePrompt: String? = null,

    var isOcrDocument: Boolean = false,  // Added this property for OCR documents

    // Metadata
    var lastModified: Long = System.currentTimeMillis(),
    val indicatorText: String? = null,  // ADD THIS
    val initialIndicatorText: String? = null,  // ADD THIS
    val initialIndicatorColor: Int? = null,    // ADD THIS
    val audioData: AimlApiResponse.AudioData? = null,
    val isAudioPlaying: Boolean = false,

    // Enhanced Web Search Properties
    val webSearchParams: Map<String, String> = emptyMap(),
    val webSearchResults: String = "",
    val hasWebSearchResults: Boolean = false,
    val webSearchQuery: String = "",
    val webSearchTimestamp: Long = 0L,
    val webSearchResultCount: Int = 0,
    val webSearchFreshness: String? = null,

    // Tool Calls Properties
    val hasToolCalls: Boolean = false,
    val toolCallsInProgress: Boolean = false,
    val toolCallsCompleted: Boolean = false,
    val toolCallResults: List<ToolCallResult> = emptyList(),
    val toolCallErrors: List<String> = emptyList(),
    
    // File Generation Properties
    val hasGeneratedFile: Boolean = false,
    val generatedFileName: String? = null,
    val generatedFileSize: Long = 0L,
    val generatedFileType: String? = null,
    val generatedFileUri: String? = null,

    // Enhanced Thinking Properties
    val thinkingStage: String = "",
    val thinkingProgress: Float = 0f,
    val thinkingStartTime: Long = 0L,
    val thinkingEndTime: Long = 0L,

    // Smart Detection Properties
    val autoWebSearchDetected: Boolean = false,
    val autoWebSearchReason: String = "",
    val smartAnalysisResults: Map<String, Any> = emptyMap(),

    // Enhanced Status Properties
    val stageProgress: Float = 0f,
    val stageMessage: String = "",
    val estimatedCompletionTime: Long = 0L,

    // Enhanced Error Handling
    val hasRecoverableError: Boolean = false,
    val recoveryOptions: List<String> = emptyList(),
    val errorContext: Map<String, String> = emptyMap(),

    // Performance Metrics
    val responseStartTime: Long = 0L,
    val firstTokenTime: Long = 0L,
    val completionTime: Long = 0L,
    val tokenGenerationRate: Float = 0f,
    val totalTokensUsed: Int = 0,

    // Enhanced Metadata
    val confidenceScore: Float = 0f,
    val sourceReliability: Float = 0f,
    val informationFreshness: String = "",
    val citedSources: List<String> = emptyList(),
    val thinkingDuration: String? = 0L.toString(), // sau altă valoare default
    
    // Related questions for Perplexity
    val relatedQuestions: String? = null,
    
    // Search images for Perplexity (JSON array of image objects)
    val searchImages: String? = null,
    
    // File attachments (JSON serialized list of attachment info)
    val attachments: String? = null,

) {
    /**
     * Location information for a message
     */
    data class LocationInfo(
        val city: String,
        val region: String,
        val country: String,
        val timezone: String
    )
    enum class ProcessingStage {
        NONE,
        ANALYZING_QUERY,
        DETECTING_INTENT,
        PREPARING_SEARCH,
        EXECUTING_WEB_SEARCH,
        PROCESSING_SEARCH_RESULTS,
        THINKING,
        REASONING,
        GENERATING_RESPONSE,
        FINALIZING,
        COMPLETED,
        ERROR
    }

    /**
     * ToolCallResult data class
     */
    data class ToolCallResult(
        val toolName: String,
        val callId: String,
        val startTime: Long,
        val endTime: Long,
        val success: Boolean,
        val result: String,
        val error: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )



    /**
     * SourceType enum pentru categorisirea surselor
     */
    enum class SourceType {
        OFFICIAL,           // .gov, .edu, organizații oficiale
        NEWS,              // surse de știri recunoscute
        ACADEMIC,          // publicații academice
        COMMERCIAL,        // site-uri comerciale
        SOCIAL_MEDIA,      // platforme sociale
        FORUM,             // forumuri și comunități
        WIKI,              // Wikipedia și wiki-uri
        GENERAL            // alte surse
    }

    /**
     * Calculează durata de procesare
     */
    fun ChatMessage.getProcessingDuration(): Long {
        return if (responseStartTime > 0 && completionTime > 0) {
            completionTime - responseStartTime
        } else {
            0L
        }
    }

    /**
     * Adds a new version to the message
     */
    fun addVersion(newMessage: String) {
        if (messageVersions.isEmpty()) {
            messageVersions.add(message) // Add current message as first version
        }
        messageVersions.add(newMessage)
        totalVersions = messageVersions.size
        versionIndex = totalVersions - 1 // Set to latest version
        message = newMessage // Update current message
        
        // Debug logging
        android.util.Log.d("ChatMessage", "🔄 addVersion: Added version. totalVersions=$totalVersions, versionIndex=$versionIndex, messageId=$id")
    }

    /**
     * Navigates to a specific version
     */
    fun navigateToVersion(index: Int) {
        if (index >= 0 && index < messageVersions.size) {
            versionIndex = index
            message = messageVersions[index]
        }
    }

    /**
     * Gets the current version text
     */
    fun getCurrentVersionText(): String {
        return if (messageVersions.isNotEmpty() && versionIndex < messageVersions.size) {
            messageVersions[versionIndex]
        } else {
            message
        }
    }

}