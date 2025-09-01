package com.cyberflux.qwinai.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * Comprehensive accessibility manager for enhanced app accessibility
 * Provides screen reader support, navigation assistance, and inclusive design
 */
class AppAccessibilityManager private constructor(private val context: Context) {
    
    private val systemAccessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
        as android.view.accessibility.AccessibilityManager
    
    companion object {
        @Volatile
        private var INSTANCE: AppAccessibilityManager? = null
        
        fun getInstance(context: Context): AppAccessibilityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppAccessibilityManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Minimum touch target size in dp (Material Design recommendation)
        private const val MIN_TOUCH_TARGET_SIZE_DP = 48
        
        // Content description prefixes for different UI elements
        private const val BUTTON_PREFIX = "Button: "
        private const val IMAGE_PREFIX = "Image: "
        private const val TEXT_PREFIX = "Text: "
        private const val EDIT_PREFIX = "Text field: "
        private const val CHAT_MESSAGE_PREFIX = "Chat message: "
        private const val LOADING_PREFIX = "Loading: "
    }
    
    /**
     * Check if accessibility services are enabled
     */
    fun isAccessibilityEnabled(): Boolean {
        return systemAccessibilityManager.isEnabled
    }
    
    /**
     * Check if TalkBack (screen reader) is enabled
     */
    fun isTalkBackEnabled(): Boolean {
        return systemAccessibilityManager.isTouchExplorationEnabled
    }
    
    /**
     * Apply comprehensive accessibility features to a view hierarchy
     */
    fun enhanceViewAccessibility(rootView: View) {
        try {
            when (rootView) {
                is ViewGroup -> enhanceViewGroupAccessibility(rootView)
                else -> enhanceIndividualViewAccessibility(rootView)
            }
            
            Timber.d("Enhanced accessibility for view: ${rootView.javaClass.simpleName}")
        } catch (e: Exception) {
            Timber.e(e, "Error enhancing view accessibility: ${e.message}")
        }
    }
    
    /**
     * Enhance accessibility for ViewGroup and its children
     */
    private fun enhanceViewGroupAccessibility(viewGroup: ViewGroup) {
        // Enhance the ViewGroup itself
        enhanceIndividualViewAccessibility(viewGroup)
        
        // Recursively enhance all children
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            enhanceViewAccessibility(child)
        }
    }
    
    /**
     * Enhance accessibility for individual views
     */
    private fun enhanceIndividualViewAccessibility(view: View) {
        // Ensure minimum touch target size
        ensureMinimumTouchTargetSize(view)
        
        // Add appropriate content descriptions
        addContentDescription(view)
        
        // Configure accessibility properties
        configureAccessibilityProperties(view)
        
        // Add accessibility actions
        addAccessibilityActions(view)
        
        // Configure focus behavior
        configureFocusBehavior(view)
    }
    
    /**
     * Ensure view meets minimum touch target size requirements
     */
    private fun ensureMinimumTouchTargetSize(view: View) {
        if (view.isClickable || view.isFocusable) {
            val density = context.resources.displayMetrics.density
            val minSizePx = (MIN_TOUCH_TARGET_SIZE_DP * density).toInt()
            
            ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    // Ensure minimum touch target size
                    val bounds = android.graphics.Rect()
                    host.getHitRect(bounds)
                    
                    if (bounds.width() < minSizePx || bounds.height() < minSizePx) {
                        val extraWidth = maxOf(0, minSizePx - bounds.width()) / 2
                        val extraHeight = maxOf(0, minSizePx - bounds.height()) / 2
                        
                        bounds.left -= extraWidth
                        bounds.right += extraWidth
                        bounds.top -= extraHeight
                        bounds.bottom += extraHeight
                        
                        info.setBoundsInParent(bounds)
                    }
                }
            })
        }
    }
    
    /**
     * Add appropriate content descriptions based on view type
     */
    private fun addContentDescription(view: View) {
        if (view.contentDescription.isNullOrEmpty()) {
            val description = when (view) {
                is Button -> generateButtonDescription(view)
                is ImageButton -> generateImageButtonDescription(view)
                is ImageView -> generateImageDescription(view)
                is TextView -> generateTextDescription(view)
                is EditText -> generateEditTextDescription(view)
                else -> generateGenericDescription(view)
            }
            
            if (description.isNotEmpty()) {
                view.contentDescription = description
                Timber.v("Added content description: $description")
            }
        }
    }
    
    /**
     * Generate content description for buttons
     */
    private fun generateButtonDescription(button: Button): String {
        val text = button.text?.toString()?.trim()
        return if (!text.isNullOrEmpty()) {
            "$BUTTON_PREFIX$text"
        } else {
            when (button.id) {
                android.R.id.button1 -> "${BUTTON_PREFIX}OK"
                android.R.id.button2 -> "${BUTTON_PREFIX}Cancel"
                else -> "${BUTTON_PREFIX}Button"
            }
        }
    }
    
    /**
     * Generate content description for image buttons
     */
    private fun generateImageButtonDescription(imageButton: ImageButton): String {
        return when (imageButton.id) {
            // Add specific descriptions based on common button IDs in your app
            // You can expand this based on your actual button IDs
            else -> "${BUTTON_PREFIX}Action button"
        }
    }
    
    /**
     * Generate content description for images
     */
    private fun generateImageDescription(imageView: ImageView): String {
        return when (imageView.id) {
            // Add specific descriptions based on common image IDs in your app
            else -> "${IMAGE_PREFIX}Image"
        }
    }
    
    /**
     * Generate content description for text views
     */
    private fun generateTextDescription(textView: TextView): String {
        val text = textView.text?.toString()?.trim()
        return if (!text.isNullOrEmpty() && text.length > 100) {
            // For long text, provide a summary
            "${TEXT_PREFIX}Long text content"
        } else if (!text.isNullOrEmpty()) {
            "$TEXT_PREFIX$text"
        } else {
            "${TEXT_PREFIX}Text content"
        }
    }
    
    /**
     * Generate content description for edit text fields
     */
    private fun generateEditTextDescription(editText: EditText): String {
        val hint = editText.hint?.toString()?.trim()
        return if (!hint.isNullOrEmpty()) {
            "$EDIT_PREFIX$hint"
        } else {
            "${EDIT_PREFIX}Text input field"
        }
    }
    
    /**
     * Generate generic content description
     */
    private fun generateGenericDescription(view: View): String {
        return when {
            view.isClickable -> "${BUTTON_PREFIX}Interactive element"
            view.isFocusable -> "Focusable element"
            else -> ""
        }
    }
    
    /**
     * Configure accessibility properties for views
     */
    private fun configureAccessibilityProperties(view: View) {
        // Enable accessibility focus
        if (view.isClickable || view.isFocusable) {
            ViewCompat.setImportantForAccessibility(view, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
        }
        
        // Configure live region for dynamic content
        if (view is TextView && view.id != View.NO_ID) {
            ViewCompat.setAccessibilityLiveRegion(view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
        
        // Mark edit text fields as text input
        if (view is EditText) {
            ViewCompat.setImportantForAccessibility(view, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
        }
    }
    
    /**
     * Add custom accessibility actions
     */
    private fun addAccessibilityActions(view: View) {
        if (view.isClickable) {
            ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    // Add standard click action
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                    
                    // Add long click action if supported
                    if (host.isLongClickable) {
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
                    }
                }
            })
        }
    }
    
    /**
     * Configure focus behavior for accessibility
     */
    private fun configureFocusBehavior(view: View) {
        // Ensure interactive elements are focusable
        if (view.isClickable && !view.isFocusable) {
            view.isFocusable = true
        }
        
        // Configure focus order for complex layouts
        if (view is ViewGroup) {
            view.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    }
    
    /**
     * Enhance chat message accessibility
     */
    fun enhanceChatMessageAccessibility(messageView: View, message: String, isFromUser: Boolean, timestamp: Long) {
        val speaker = if (isFromUser) "You" else "AI Assistant"
        val timeString = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(timestamp))
        
        val description = "${CHAT_MESSAGE_PREFIX}$speaker said: $message. Sent at $timeString"
        messageView.contentDescription = description
        
        // Mark as important for accessibility
        ViewCompat.setImportantForAccessibility(messageView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
        
        // Set up accessibility focus
        ViewCompat.setAccessibilityDelegate(messageView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = description
            }
        })
    }
    
    /**
     * Enhance RecyclerView accessibility
     */
    fun enhanceRecyclerViewAccessibility(recyclerView: RecyclerView, itemCount: Int) {
        recyclerView.contentDescription = "List with $itemCount items"
        ViewCompat.setImportantForAccessibility(recyclerView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
        
        // Configure scrolling announcements
        ViewCompat.setAccessibilityDelegate(recyclerView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                if (recyclerView.canScrollVertically(1) || recyclerView.canScrollVertically(-1)) {
                    info.isScrollable = true
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
                }
            }
        })
    }
    
    /**
     * Announce text to screen reader
     */
    fun announceText(view: View, text: String) {
        if (isTalkBackEnabled()) {
            view.announceForAccessibility(text)
            Timber.d("Announced to screen reader: $text")
        }
    }
    
    /**
     * Send accessibility event
     */
    fun sendAccessibilityEvent(view: View, eventType: Int, text: String? = null) {
        if (isAccessibilityEnabled()) {
            val event = AccessibilityEvent.obtain(eventType)
            event.text.add(text ?: view.contentDescription)
            systemAccessibilityManager.sendAccessibilityEvent(event)
        }
    }
    
    /**
     * Configure loading state accessibility
     */
    fun configureLoadingAccessibility(loadingView: View, loadingText: String = "Loading content") {
        loadingView.contentDescription = "$LOADING_PREFIX$loadingText"
        ViewCompat.setAccessibilityLiveRegion(loadingView, ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
        
        if (isTalkBackEnabled()) {
            loadingView.announceForAccessibility(loadingText)
        }
    }
    
    /**
     * Get accessibility summary for debugging
     */
    fun getAccessibilitySummary(): Map<String, Any> {
        return mapOf(
            "accessibilityEnabled" to isAccessibilityEnabled(),
            "talkBackEnabled" to isTalkBackEnabled(),
            "touchExplorationEnabled" to systemAccessibilityManager.isTouchExplorationEnabled,
            "enabledServices" to systemAccessibilityManager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).size
        )
    }
}