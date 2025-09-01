package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.dao.ConversationDao
import com.cyberflux.qwinai.model.Conversation
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Comprehensive message draft management system
 * Provides auto-save functionality, draft restoration, and cleanup
 */
class MessageDraftManager(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val coroutineScope: CoroutineScope
) {
    
    private val moshi = Moshi.Builder().build()
    private val activeDrafts = ConcurrentHashMap<Long, DraftData>()
    private val autoSaveJobs = ConcurrentHashMap<Long, Job>()
    private val draftCallbacks = mutableListOf<DraftCallback>()
    
    companion object {
        private const val AUTO_SAVE_DELAY = 2000L // 2 seconds
        private const val DRAFT_CLEANUP_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_DRAFT_AGE = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val MAX_DRAFT_SIZE = 10000 // 10KB limit for draft text
    }
    
    /**
     * Draft data container
     */
    data class DraftData(
        val conversationId: Long,
        var text: String = "",
        var attachedFiles: List<DraftFile> = emptyList(),
        var lastModified: Long = System.currentTimeMillis(),
        var isDirty: Boolean = false
    )
    
    /**
     * Draft file attachment
     */
    data class DraftFile(
        val uri: String,
        val name: String,
        val type: String,
        val size: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Callback interface for draft events
     */
    interface DraftCallback {
        fun onDraftSaved(conversationId: Long, draft: DraftData)
        fun onDraftRestored(conversationId: Long, draft: DraftData)
        fun onDraftDeleted(conversationId: Long)
        fun onDraftError(conversationId: Long, error: String)
    }
    
    /**
     * Initialize draft manager
     */
    fun initialize() {
        // Start periodic cleanup
        startPeriodicCleanup()
        
        // Load existing drafts from database
        loadExistingDrafts()
        
        Timber.d("Message draft manager initialized")
    }
    
    /**
     * Register draft callback
     */
    fun registerDraftCallback(callback: DraftCallback) {
        draftCallbacks.add(callback)
    }
    
    /**
     * Unregister draft callback
     */
    fun unregisterDraftCallback(callback: DraftCallback) {
        draftCallbacks.remove(callback)
    }
    
    /**
     * Update draft text and trigger auto-save
     */
    fun updateDraftText(conversationId: Long, text: String) {
        if (text.length > MAX_DRAFT_SIZE) {
            Timber.w("Draft text too large for conversation $conversationId: ${text.length} chars")
            notifyDraftError(conversationId, "Draft text too large")
            return
        }
        
        val draft = activeDrafts.getOrPut(conversationId) {
            DraftData(conversationId = conversationId)
        }
        
        draft.text = text
        draft.lastModified = System.currentTimeMillis()
        draft.isDirty = true
        
        // Cancel existing auto-save job and start new one
        scheduleAutoSave(conversationId)
        
        Timber.v("Draft text updated for conversation $conversationId: ${text.length} chars")
    }
    
    /**
     * Add file attachment to draft
     */
    fun addDraftFile(conversationId: Long, file: DraftFile) {
        val draft = activeDrafts.getOrPut(conversationId) {
            DraftData(conversationId = conversationId)
        }
        
        draft.attachedFiles = draft.attachedFiles + file
        draft.lastModified = System.currentTimeMillis()
        draft.isDirty = true
        
        scheduleAutoSave(conversationId)
        
        Timber.d("File added to draft for conversation $conversationId: ${file.name}")
    }
    
    /**
     * Remove file attachment from draft
     */
    fun removeDraftFile(conversationId: Long, fileUri: String) {
        val draft = activeDrafts[conversationId] ?: return
        
        draft.attachedFiles = draft.attachedFiles.filter { it.uri != fileUri }
        draft.lastModified = System.currentTimeMillis()
        draft.isDirty = true
        
        scheduleAutoSave(conversationId)
        
        Timber.d("File removed from draft for conversation $conversationId: $fileUri")
    }
    
    /**
     * Get current draft for conversation
     */
    fun getDraft(conversationId: Long): DraftData? {
        return activeDrafts[conversationId]
    }
    
    /**
     * Check if conversation has draft
     */
    fun hasDraft(conversationId: Long): Boolean {
        val draft = activeDrafts[conversationId]
        return draft != null && (draft.text.isNotEmpty() || draft.attachedFiles.isNotEmpty())
    }
    
    /**
     * Restore draft for conversation from database
     */
    suspend fun restoreDraft(conversationId: Long): DraftData? {
        return withContext(Dispatchers.IO) {
            try {
                val conversation = conversationDao.getConversationById(conversationId)
                
                if (conversation.hasDraft && conversation.draftText.isNotEmpty()) {
                    val attachedFiles = if (conversation.draftFiles.isNotEmpty()) {
                        try {
                            val fileListType: Type = Types.newParameterizedType(List::class.java, DraftFile::class.java)
                            val adapter = moshi.adapter<List<DraftFile>>(fileListType)
                            adapter.fromJson(conversation.draftFiles) ?: emptyList()
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing draft files")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    
                    val draft = DraftData(
                        conversationId = conversationId,
                        text = conversation.draftText,
                        attachedFiles = attachedFiles,
                        lastModified = conversation.draftTimestamp,
                        isDirty = false
                    )
                    
                    activeDrafts[conversationId] = draft
                    
                    withContext(Dispatchers.Main) {
                        notifyDraftRestored(conversationId, draft)
                    }
                    
                    Timber.d("Draft restored for conversation $conversationId")
                    draft
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error restoring draft for conversation $conversationId")
                withContext(Dispatchers.Main) {
                    notifyDraftError(conversationId, e.message ?: "Failed to restore draft")
                }
                null
            }
        }
    }
    
    /**
     * Save draft immediately
     */
    suspend fun saveDraft(conversationId: Long) {
        val draft = activeDrafts[conversationId] ?: return
        
        if (!draft.isDirty) {
            return // No changes to save
        }
        
        withContext(Dispatchers.IO) {
            try {
                val conversation = conversationDao.getConversationById(conversationId)
                
                val filesJson = if (draft.attachedFiles.isNotEmpty()) {
                    val fileListType: Type = Types.newParameterizedType(List::class.java, DraftFile::class.java)
                    val adapter = moshi.adapter<List<DraftFile>>(fileListType)
                    adapter.toJson(draft.attachedFiles)
                } else {
                    ""
                }
                
                val hasDraft = draft.text.isNotEmpty() || draft.attachedFiles.isNotEmpty()
                
                val updatedConversation = conversation.copy(
                    hasDraft = hasDraft,
                    draftText = draft.text,
                    draftFiles = filesJson,
                    draftTimestamp = draft.lastModified
                )
                
                conversationDao.update(updatedConversation)
                
                draft.isDirty = false
                
                withContext(Dispatchers.Main) {
                    notifyDraftSaved(conversationId, draft)
                }
                
                Timber.d("Draft saved for conversation $conversationId")
                
            } catch (e: Exception) {
                Timber.e(e, "Error saving draft for conversation $conversationId")
                withContext(Dispatchers.Main) {
                    notifyDraftError(conversationId, e.message ?: "Failed to save draft")
                }
            }
        }
    }
    
    /**
     * Delete draft
     */
    suspend fun deleteDraft(conversationId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val conversation = conversationDao.getConversationById(conversationId)
                
                val updatedConversation = conversation.copy(
                    hasDraft = false,
                    draftText = "",
                    draftFiles = "",
                    draftTimestamp = 0
                )
                
                conversationDao.update(updatedConversation)
                
                activeDrafts.remove(conversationId)
                autoSaveJobs[conversationId]?.cancel()
                autoSaveJobs.remove(conversationId)
                
                withContext(Dispatchers.Main) {
                    notifyDraftDeleted(conversationId)
                }
                
                Timber.d("Draft deleted for conversation $conversationId")
                
            } catch (e: Exception) {
                Timber.e(e, "Error deleting draft for conversation $conversationId")
                withContext(Dispatchers.Main) {
                    notifyDraftError(conversationId, e.message ?: "Failed to delete draft")
                }
            }
        }
    }
    
    /**
     * Clear draft from memory (when message is sent)
     */
    fun clearDraft(conversationId: Long) {
        activeDrafts.remove(conversationId)
        autoSaveJobs[conversationId]?.cancel()
        autoSaveJobs.remove(conversationId)
        
        // Also clear from database
        coroutineScope.launch {
            deleteDraft(conversationId)
        }
        
        Timber.d("Draft cleared for conversation $conversationId")
    }
    
    /**
     * Schedule auto-save for draft
     */
    private fun scheduleAutoSave(conversationId: Long) {
        // Cancel existing job
        autoSaveJobs[conversationId]?.cancel()
        
        // Schedule new auto-save
        val job = coroutineScope.launch {
            delay(AUTO_SAVE_DELAY)
            saveDraft(conversationId)
        }
        
        autoSaveJobs[conversationId] = job
    }
    
    /**
     * Load existing drafts from database
     */
    private fun loadExistingDrafts() {
        coroutineScope.launch {
            try {
                val conversationsWithDrafts = conversationDao.getConversationsWithDrafts()
                
                for (conversation in conversationsWithDrafts) {
                    if (conversation.hasDraft && conversation.draftText.isNotEmpty()) {
                        val attachedFiles = if (conversation.draftFiles.isNotEmpty()) {
                            try {
                                val fileListType: Type = Types.newParameterizedType(List::class.java, DraftFile::class.java)
                                val adapter = moshi.adapter<List<DraftFile>>(fileListType)
                                adapter.fromJson(conversation.draftFiles) ?: emptyList()
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing draft files for conversation ${conversation.id}")
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                        
                        val draft = DraftData(
                            conversationId = conversation.id,
                            text = conversation.draftText,
                            attachedFiles = attachedFiles,
                            lastModified = conversation.draftTimestamp,
                            isDirty = false
                        )
                        
                        activeDrafts[conversation.id] = draft
                    }
                }
                
                Timber.d("Loaded ${activeDrafts.size} existing drafts")
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading existing drafts")
            }
        }
    }
    
    /**
     * Start periodic cleanup of old drafts
     */
    private fun startPeriodicCleanup() {
        coroutineScope.launch {
            while (true) {
                delay(DRAFT_CLEANUP_INTERVAL)
                cleanupOldDrafts()
            }
        }
    }
    
    /**
     * Clean up old drafts
     */
    private suspend fun cleanupOldDrafts() {
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val cutoffTime = currentTime - MAX_DRAFT_AGE
                
                val conversationsWithDrafts = conversationDao.getConversationsWithDrafts()
                var cleanedCount = 0
                
                for (conversation in conversationsWithDrafts) {
                    if (conversation.draftTimestamp < cutoffTime) {
                        val updatedConversation = conversation.copy(
                            hasDraft = false,
                            draftText = "",
                            draftFiles = "",
                            draftTimestamp = 0
                        )
                        
                        conversationDao.update(updatedConversation)
                        activeDrafts.remove(conversation.id)
                        autoSaveJobs[conversation.id]?.cancel()
                        autoSaveJobs.remove(conversation.id)
                        
                        cleanedCount++
                    }
                }
                
                if (cleanedCount > 0) {
                    Timber.d("Cleaned up $cleanedCount old drafts")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up old drafts")
            }
        }
    }
    
    /**
     * Get draft statistics
     */
    fun getDraftStatistics(): Map<String, Any> {
        return mapOf(
            "activeDrafts" to activeDrafts.size,
            "pendingSaves" to autoSaveJobs.size,
            "totalDraftSize" to activeDrafts.values.sumOf { it.text.length },
            "totalAttachedFiles" to activeDrafts.values.sumOf { it.attachedFiles.size },
            "oldestDraft" to (activeDrafts.values.minByOrNull { it.lastModified }?.lastModified ?: 0),
            "newestDraft" to (activeDrafts.values.maxByOrNull { it.lastModified }?.lastModified ?: 0)
        )
    }
    
    /**
     * Force save all drafts
     */
    suspend fun saveAllDrafts() {
        val draftIds = activeDrafts.keys.toList()
        
        for (conversationId in draftIds) {
            try {
                saveDraft(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Error saving draft for conversation $conversationId")
            }
        }
        
        Timber.d("Saved all ${draftIds.size} drafts")
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        // Cancel all auto-save jobs
        autoSaveJobs.values.forEach { it.cancel() }
        autoSaveJobs.clear()
        
        // Save all pending drafts
        coroutineScope.launch {
            saveAllDrafts()
            activeDrafts.clear()
            draftCallbacks.clear()
        }
        
        Timber.d("Draft manager cleaned up")
    }
    
    // Notification methods
    private fun notifyDraftSaved(conversationId: Long, draft: DraftData) {
        draftCallbacks.forEach { callback ->
            try {
                callback.onDraftSaved(conversationId, draft)
            } catch (e: Exception) {
                Timber.e(e, "Error in draft saved callback")
            }
        }
    }
    
    private fun notifyDraftRestored(conversationId: Long, draft: DraftData) {
        draftCallbacks.forEach { callback ->
            try {
                callback.onDraftRestored(conversationId, draft)
            } catch (e: Exception) {
                Timber.e(e, "Error in draft restored callback")
            }
        }
    }
    
    private fun notifyDraftDeleted(conversationId: Long) {
        draftCallbacks.forEach { callback ->
            try {
                callback.onDraftDeleted(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Error in draft deleted callback")
            }
        }
    }
    
    private fun notifyDraftError(conversationId: Long, error: String) {
        draftCallbacks.forEach { callback ->
            try {
                callback.onDraftError(conversationId, error)
            } catch (e: Exception) {
                Timber.e(e, "Error in draft error callback")
            }
        }
    }
}