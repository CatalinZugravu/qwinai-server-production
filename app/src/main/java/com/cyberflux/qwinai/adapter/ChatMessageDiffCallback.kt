package com.cyberflux.qwinai.adapter

import androidx.recyclerview.widget.DiffUtil
import com.cyberflux.qwinai.model.ChatMessage

class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        // Check if they're the same message by ID
        if (oldItem.id == newItem.id) return true

        // Special case: check if they're different versions of the same message
        // by being in the same group
        if (!oldItem.isUser && !newItem.isUser &&
            oldItem.aiGroupId != null && newItem.aiGroupId != null &&
            oldItem.aiGroupId == newItem.aiGroupId) {
            // They're part of the same version group
            return true
        }

        return false
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        // Quick identity check
        if (oldItem === newItem) return true
        
        // Fast streaming path - optimized for high-frequency updates (33ms)
        if (newItem.isGenerating || oldItem.isGenerating) {
            return compareStreamingFields(oldItem, newItem)
        }
        
        // Full comparison for completed messages
        return compareAllFields(oldItem, newItem)
    }
    
    private fun compareStreamingFields(old: ChatMessage, new: ChatMessage): Boolean {
        // Only compare frequently changing fields during streaming
        return old.id == new.id &&
               old.message == new.message &&
               old.isGenerating == new.isGenerating &&
               old.webSearchResults == new.webSearchResults &&
               old.isWebSearchActive == new.isWebSearchActive &&
               old.isLoading == new.isLoading
    }
    
    private fun compareAllFields(old: ChatMessage, new: ChatMessage): Boolean {
        return old == new ||
                (old.id == new.id &&
                        old.message == new.message &&
                        old.isGenerating == new.isGenerating &&
                        old.isLoading == new.isLoading &&
                        old.showButtons == new.showButtons &&
                        old.versionIndex == new.versionIndex &&
                        old.totalVersions == new.totalVersions &&
                        old.initialIndicatorText == new.initialIndicatorText &&
                        old.initialIndicatorColor == new.initialIndicatorColor &&
                        old.webSearchResults == new.webSearchResults &&
                        old.hasWebSearchResults == new.hasWebSearchResults &&
                        old.isWebSearchActive == new.isWebSearchActive &&
                        old.audioData?.content == new.audioData?.content &&
                        old.isAudioPlaying == new.isAudioPlaying &&
                        old.isForceSearch == new.isForceSearch &&
                        old.timestamp == new.timestamp &&
                        old.error == new.error)
    }

    override fun getChangePayload(oldItem: ChatMessage, newItem: ChatMessage): Any? {
        // OPTIMIZED: Fast-track streaming updates with incremental payload
        if (newItem.isGenerating) {
            val hasContentChange = oldItem.message != newItem.message
            val hasSearchChange = oldItem.webSearchResults != newItem.webSearchResults
            
            return when {
                hasContentChange && hasSearchChange -> "STREAMING_UPDATE_WITH_SEARCH"
                hasContentChange -> "STREAMING_CONTENT_UPDATE"
                hasSearchChange -> "STREAMING_SEARCH_UPDATE"
                oldItem.isWebSearchActive != newItem.isWebSearchActive -> "STREAMING_STATUS_UPDATE"
                else -> null
            }
        }

        // Check for content changes (main message text)
        if (oldItem.message != newItem.message) {
            return "CONTENT_UPDATE"
        }

        // Check for web search changes
        if (oldItem.webSearchResults != newItem.webSearchResults ||
            oldItem.hasWebSearchResults != newItem.hasWebSearchResults ||
            oldItem.isWebSearchActive != newItem.isWebSearchActive) {
            return "WEB_SEARCH_UPDATE"
        }

        // Check for loading state changes
        if (oldItem.isGenerating != newItem.isGenerating ||
            oldItem.isLoading != newItem.isLoading ||
            oldItem.isWebSearchActive != newItem.isWebSearchActive) {
            return "LOADING_UPDATE"
        }

        // Check for button state changes
        if (oldItem.showButtons != newItem.showButtons) {
            return "BUTTON_STATE_CHANGE"
        }

        // Check for version navigation changes
        if (oldItem.versionIndex != newItem.versionIndex ||
            oldItem.totalVersions != newItem.totalVersions) {
            return "VERSION_CHANGE"
        }

        // Check for audio state changes
        if (oldItem.audioData?.content != newItem.audioData?.content ||
            oldItem.isAudioPlaying != newItem.isAudioPlaying) {
            return "AUDIO_STATE_CHANGE"
        }

        // Check for status indicator changes
        if (oldItem.initialIndicatorText != newItem.initialIndicatorText ||
            oldItem.initialIndicatorColor != newItem.initialIndicatorColor) {
            return "STATUS_INDICATOR_CHANGE"
        }

        // Check for error state changes
        if (oldItem.error != newItem.error) {
            return "ERROR_STATE_CHANGE"
        }

        // IMPROVED: Optimize multiple changes detection - prioritize streaming
        if (oldItem.message != newItem.message &&
            (newItem.isGenerating || newItem.isWebSearchActive)) {
            return "STREAMING_UPDATE"
        }

        // Return null if no specific change detected (will trigger full rebind)
        return null
    }
}