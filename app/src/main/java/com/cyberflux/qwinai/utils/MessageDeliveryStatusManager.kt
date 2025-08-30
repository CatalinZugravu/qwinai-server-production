package com.cyberflux.qwinai.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.ChatMessage
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive message delivery status manager
 * Shows visual indicators for message sending, delivery, and processing states
 */
class MessageDeliveryStatusManager private constructor(private val context: Context) {
    
    // Track message statuses
    private val messageStatuses = ConcurrentHashMap<String, MessageStatus>()
    private val statusViews = ConcurrentHashMap<String, StatusViews>()
    
    companion object {
        @Volatile
        private var INSTANCE: MessageDeliveryStatusManager? = null
        
        fun getInstance(context: Context): MessageDeliveryStatusManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageDeliveryStatusManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Message delivery status enum
     */
    enum class MessageStatus {
        COMPOSING,      // User is typing
        SENDING,        // Message is being sent
        SENT,          // Message sent successfully
        DELIVERED,     // Message delivered to server
        PROCESSING,    // AI is processing the message
        RECEIVED,      // AI response received
        FAILED,        // Message send failed
        RETRY          // Retrying to send
    }
    
    /**
     * Container for status indicator views
     */
    data class StatusViews(
        val statusIcon: ImageView?,
        val statusText: TextView?,
        val loadingIndicator: View?
    )
    
    /**
     * Update message status and visual indicators
     */
    fun updateMessageStatus(
        messageId: String, 
        status: MessageStatus,
        statusIcon: ImageView? = null,
        statusText: TextView? = null,
        loadingIndicator: View? = null
    ) {
        try {
            // Store the status
            messageStatuses[messageId] = status
            
            // Store view references
            if (statusIcon != null || statusText != null || loadingIndicator != null) {
                statusViews[messageId] = StatusViews(statusIcon, statusText, loadingIndicator)
            }
            
            // Update visual indicators
            val views = statusViews[messageId]
            if (views != null) {
                updateStatusVisuals(status, views)
            }
            
            Timber.d("Updated message status: $messageId -> $status")
            
        } catch (e: Exception) {
            Timber.e(e, "Error updating message status: ${e.message}")
        }
    }
    
    /**
     * Update visual indicators based on status
     */
    private fun updateStatusVisuals(status: MessageStatus, views: StatusViews) {
        val (icon, text, showLoading) = getStatusDisplay(status)
        
        // Update status icon
        views.statusIcon?.let { iconView ->
            if (icon != null) {
                iconView.setImageDrawable(icon)
                iconView.isVisible = true
                animateStatusChange(iconView)
            } else {
                iconView.isVisible = false
            }
        }
        
        // Update status text
        views.statusText?.let { textView ->
            if (text.isNotEmpty()) {
                textView.text = text
                textView.isVisible = true
                animateStatusChange(textView)
            } else {
                textView.isVisible = false
            }
        }
        
        // Update loading indicator
        views.loadingIndicator?.let { loadingView ->
            loadingView.isVisible = showLoading
            if (showLoading) {
                startLoadingAnimation(loadingView)
            } else {
                stopLoadingAnimation(loadingView)
            }
        }
    }
    
    /**
     * Get display elements for a status
     */
    private fun getStatusDisplay(status: MessageStatus): Triple<Drawable?, String, Boolean> {
        return when (status) {
            MessageStatus.COMPOSING -> Triple(
                null,
                "Typing...",
                false
            )
            
            MessageStatus.SENDING -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_schedule),
                "Sending...",
                true
            )
            
            MessageStatus.SENT -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_check),
                "Sent",
                false
            )
            
            MessageStatus.DELIVERED -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_done_all),
                "Delivered",
                false
            )
            
            MessageStatus.PROCESSING -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_auto_awesome),
                "AI is thinking...",
                true
            )
            
            MessageStatus.RECEIVED -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_done_all),
                "Received",
                false
            )
            
            MessageStatus.FAILED -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_error),
                "Failed to send",
                false
            )
            
            MessageStatus.RETRY -> Triple(
                ContextCompat.getDrawable(context, R.drawable.ic_refresh),
                "Retrying...",
                true
            )
        }
    }
    
    /**
     * Animate status change
     */
    private fun animateStatusChange(view: View) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    /**
     * Start loading animation
     */
    private fun startLoadingAnimation(view: View) {
        val rotationAnimator = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f)
        rotationAnimator.duration = 1000
        rotationAnimator.repeatCount = ObjectAnimator.INFINITE
        rotationAnimator.start()
        
        view.tag = rotationAnimator // Store reference to stop later
    }
    
    /**
     * Stop loading animation
     */
    private fun stopLoadingAnimation(view: View) {
        val animator = view.tag as? ObjectAnimator
        animator?.cancel()
        view.rotation = 0f
        view.tag = null
    }
    
    /**
     * Get current status for a message
     */
    fun getMessageStatus(messageId: String): MessageStatus? {
        return messageStatuses[messageId]
    }
    
    /**
     * Remove message status tracking
     */
    fun removeMessageStatus(messageId: String) {
        messageStatuses.remove(messageId)
        statusViews[messageId]?.let { views ->
            stopLoadingAnimation(views.loadingIndicator ?: return@let)
        }
        statusViews.remove(messageId)
    }
    
    /**
     * Update status for user message flow
     */
    fun handleUserMessageFlow(messageId: String, views: StatusViews) {
        // Typical user message flow: SENDING -> SENT -> DELIVERED
        updateMessageStatus(messageId, MessageStatus.SENDING, views.statusIcon, views.statusText, views.loadingIndicator)
        
        // Simulate network delay and update to sent
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateMessageStatus(messageId, MessageStatus.SENT, views.statusIcon, views.statusText, views.loadingIndicator)
            
            // Then to delivered
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateMessageStatus(messageId, MessageStatus.DELIVERED, views.statusIcon, views.statusText, views.loadingIndicator)
            }, 500)
        }, 1000)
    }
    
    /**
     * Update status for AI message flow
     */
    fun handleAiMessageFlow(messageId: String, views: StatusViews) {
        // AI message flow: PROCESSING -> RECEIVED
        updateMessageStatus(messageId, MessageStatus.PROCESSING, views.statusIcon, views.statusText, views.loadingIndicator)
    }
    
    /**
     * Handle message send failure
     */
    fun handleMessageFailure(messageId: String, views: StatusViews, canRetry: Boolean = true) {
        updateMessageStatus(messageId, MessageStatus.FAILED, views.statusIcon, views.statusText, views.loadingIndicator)
        
        if (canRetry) {
            // Add retry action after delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                views.statusIcon?.setOnClickListener {
                    retryMessage(messageId, views)
                }
            }, 2000)
        }
    }
    
    /**
     * Retry sending a message
     */
    private fun retryMessage(messageId: String, views: StatusViews) {
        updateMessageStatus(messageId, MessageStatus.RETRY, views.statusIcon, views.statusText, views.loadingIndicator)
        
        // Clear click listener
        views.statusIcon?.setOnClickListener(null)
        
        // Restart the sending flow
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            handleUserMessageFlow(messageId, views)
        }, 1000)
    }
    
    /**
     * Mark message as completed (final state)
     */
    fun markMessageCompleted(messageId: String) {
        val views = statusViews[messageId]
        if (views != null) {
            updateMessageStatus(messageId, MessageStatus.RECEIVED, views.statusIcon, views.statusText, views.loadingIndicator)
            
            // Hide status after delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                views.statusIcon?.isVisible = false
                views.statusText?.isVisible = false
                views.loadingIndicator?.isVisible = false
            }, 3000)
        }
    }
    
    /**
     * Show typing indicator for user
     */
    fun showTypingIndicator(views: StatusViews) {
        updateMessageStatus("typing", MessageStatus.COMPOSING, views.statusIcon, views.statusText, views.loadingIndicator)
    }
    
    /**
     * Hide typing indicator
     */
    fun hideTypingIndicator(views: StatusViews) {
        views.statusIcon?.isVisible = false
        views.statusText?.isVisible = false
        views.loadingIndicator?.isVisible = false
        removeMessageStatus("typing")
    }
    
    /**
     * Get status summary for debugging
     */
    fun getStatusSummary(): Map<String, Any> {
        return mapOf(
            "activeStatuses" to messageStatuses.size,
            "trackedViews" to statusViews.size,
            "statusBreakdown" to messageStatuses.values.groupBy { it }.mapValues { it.value.size }
        )
    }
    
    /**
     * Clear all status tracking
     */
    fun clearAllStatuses() {
        // Stop all animations
        statusViews.values.forEach { views ->
            stopLoadingAnimation(views.loadingIndicator ?: return@forEach)
        }
        
        messageStatuses.clear()
        statusViews.clear()
        
        Timber.d("Cleared all message statuses")
    }
}