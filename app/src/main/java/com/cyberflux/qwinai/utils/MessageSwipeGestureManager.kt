package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.ChatMessage
import timber.log.Timber
import kotlin.math.*

/**
 * Comprehensive swipe gesture manager for message actions
 * Provides intuitive swipe actions for copy, delete, reply, regenerate, etc.
 */
class MessageSwipeGestureManager(
    private val context: Context,
    private val swipeCallback: SwipeActionCallback
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeThreshold = 0.7f
    private val actionIconSize = 24.dpToPx()
    private val actionIconMargin = 16.dpToPx()
    private val maxSwipeDistance = 200.dpToPx()
    
    // Paint objects for drawing
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 14.spToPx()
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    
    // Action configurations
    private val leftActions = mutableListOf<SwipeAction>()
    private val rightActions = mutableListOf<SwipeAction>()
    
    companion object {
        private const val ANIMATION_DURATION = 300L
    }
    
    /**
     * Swipe action definition
     */
    data class SwipeAction(
        val id: String,
        val label: String,
        val icon: Drawable,
        val backgroundColor: Int,
        val textColor: Int = Color.WHITE,
        val threshold: Float = 0.3f,
        val isDestructive: Boolean = false
    )
    
    /**
     * Callback interface for swipe actions
     */
    interface SwipeActionCallback {
        fun onSwipeAction(action: SwipeAction, message: ChatMessage, position: Int)
        fun canSwipeMessage(message: ChatMessage, direction: Int): Boolean
        fun getMessageAtPosition(position: Int): ChatMessage?
    }
    
    init {
        setupDefaultActions()
    }
    
    /**
     * Setup default swipe actions
     */
    private fun setupDefaultActions() {
        // Left swipe actions (destructive)
        leftActions.add(
            SwipeAction(
                id = "delete",
                label = "Delete",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_delete)!!,
                backgroundColor = ContextCompat.getColor(context, R.color.error_color),
                isDestructive = true,
                threshold = 0.5f
            )
        )
        
        leftActions.add(
            SwipeAction(
                id = "archive",
                label = "Archive",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_archive)!!,
                backgroundColor = ContextCompat.getColor(context, R.color.warning_color),
                threshold = 0.3f
            )
        )
        
        // Right swipe actions (non-destructive)
        rightActions.add(
            SwipeAction(
                id = "copy",
                label = "Copy",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)!!,
                backgroundColor = ContextCompat.getColor(context, R.color.info_color),
                threshold = 0.2f
            )
        )
        
        rightActions.add(
            SwipeAction(
                id = "reply",
                label = "Reply",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_reply)!!,
                backgroundColor = ContextCompat.getColor(context, R.color.success_color),
                threshold = 0.3f
            )
        )
        
        rightActions.add(
            SwipeAction(
                id = "regenerate",
                label = "Regenerate",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_refresh)!!,
                backgroundColor = ContextCompat.getColor(context, R.color.colorPrimary),
                threshold = 0.4f
            )
        )
    }
    
    /**
     * Add custom swipe action
     */
    fun addLeftAction(action: SwipeAction) {
        leftActions.add(action)
    }
    
    /**
     * Add custom right action
     */
    fun addRightAction(action: SwipeAction) {
        rightActions.add(action)
    }
    
    /**
     * Clear all actions
     */
    fun clearActions() {
        leftActions.clear()
        rightActions.clear()
    }
    
    /**
     * Remove action by ID
     */
    fun removeAction(actionId: String) {
        leftActions.removeAll { it.id == actionId }
        rightActions.removeAll { it.id == actionId }
    }
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false // We don't support drag and drop
    }
    
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        val message = swipeCallback.getMessageAtPosition(position) ?: return
        
        val actions = if (direction == ItemTouchHelper.LEFT) leftActions else rightActions
        if (actions.isNotEmpty()) {
            // Execute the first action that meets the threshold
            val action = actions.first()
            swipeCallback.onSwipeAction(action, message, position)
        }
    }
    
    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.adapterPosition
        val message = swipeCallback.getMessageAtPosition(position) ?: return 0
        
        var swipeDirs = 0
        
        if (leftActions.isNotEmpty() && swipeCallback.canSwipeMessage(message, ItemTouchHelper.LEFT)) {
            swipeDirs = swipeDirs or ItemTouchHelper.LEFT
        }
        
        if (rightActions.isNotEmpty() && swipeCallback.canSwipeMessage(message, ItemTouchHelper.RIGHT)) {
            swipeDirs = swipeDirs or ItemTouchHelper.RIGHT
        }
        
        return swipeDirs
    }
    
    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val position = viewHolder.adapterPosition
            val message = swipeCallback.getMessageAtPosition(position)
            
            if (message != null) {
                drawSwipeBackground(canvas, itemView, dX, dY, message)
            }
        }
        
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
    
    /**
     * Draw swipe background with actions
     */
    private fun drawSwipeBackground(
        canvas: Canvas,
        itemView: View,
        dX: Float,
        dY: Float,
        message: ChatMessage
    ) {
        val itemHeight = itemView.height.toFloat()
        val itemTop = itemView.top.toFloat()
        val itemBottom = itemView.bottom.toFloat()
        
        when {
            dX > 0 -> drawRightSwipe(canvas, itemView, dX, itemTop, itemBottom, message)
            dX < 0 -> drawLeftSwipe(canvas, itemView, dX, itemTop, itemBottom, message)
        }
    }
    
    /**
     * Draw right swipe actions
     */
    private fun drawRightSwipe(
        canvas: Canvas,
        itemView: View,
        dX: Float,
        itemTop: Float,
        itemBottom: Float,
        message: ChatMessage
    ) {
        if (rightActions.isEmpty()) return
        
        val swipeDistance = min(dX, maxSwipeDistance.toFloat())
        val actionWidth = swipeDistance / rightActions.size
        
        for (i in rightActions.indices) {
            val action = rightActions[i]
            val actionLeft = itemView.left + (i * actionWidth)
            val actionRight = actionLeft + actionWidth
            val actionProgress = (swipeDistance / maxSwipeDistance).coerceIn(0f, 1f)
            
            // Only draw if we've crossed the threshold
            if (actionProgress >= action.threshold) {
                drawAction(
                    canvas = canvas,
                    action = action,
                    left = actionLeft,
                    top = itemTop,
                    right = actionRight,
                    bottom = itemBottom,
                    progress = actionProgress
                )
            }
        }
    }
    
    /**
     * Draw left swipe actions
     */
    private fun drawLeftSwipe(
        canvas: Canvas,
        itemView: View,
        dX: Float,
        itemTop: Float,
        itemBottom: Float,
        message: ChatMessage
    ) {
        if (leftActions.isEmpty()) return
        
        val swipeDistance = min(abs(dX), maxSwipeDistance.toFloat())
        val actionWidth = swipeDistance / leftActions.size
        
        for (i in leftActions.indices) {
            val action = leftActions[i]
            val actionRight = itemView.right - (i * actionWidth)
            val actionLeft = actionRight - actionWidth
            val actionProgress = (swipeDistance / maxSwipeDistance).coerceIn(0f, 1f)
            
            // Only draw if we've crossed the threshold
            if (actionProgress >= action.threshold) {
                drawAction(
                    canvas = canvas,
                    action = action,
                    left = actionLeft,
                    top = itemTop,
                    right = actionRight,
                    bottom = itemBottom,
                    progress = actionProgress
                )
            }
        }
    }
    
    /**
     * Draw individual action
     */
    private fun drawAction(
        canvas: Canvas,
        action: SwipeAction,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        progress: Float
    ) {
        // Draw background with alpha based on progress
        val alpha = (progress * 255).toInt().coerceIn(0, 255)
        backgroundPaint.color = action.backgroundColor
        backgroundPaint.alpha = alpha
        
        canvas.drawRect(left, top, right, bottom, backgroundPaint)
        
        // Calculate center position
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        
        // Draw icon
        if (progress >= action.threshold) {
            val iconLeft = (centerX - actionIconSize / 2).toInt()
            val iconTop = (centerY - actionIconSize / 2 - 8.dpToPx()).toInt()
            val iconRight = iconLeft + actionIconSize
            val iconBottom = iconTop + actionIconSize
            
            action.icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            action.icon.alpha = alpha
            action.icon.draw(canvas)
            
            // Draw label
            textPaint.alpha = alpha
            textPaint.color = action.textColor
            canvas.drawText(
                action.label,
                centerX,
                centerY + actionIconSize / 2 + 12.dpToPx(),
                textPaint
            )
        }
    }
    
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return swipeThreshold
    }
    
    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return defaultValue * 0.5f // Make it easier to trigger swipe
    }
    
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * 1.5f // Make it harder to accidentally complete swipe
    }
    
    /**
     * Create pre-configured swipe manager for messages
     */
    object Presets {
        
        /**
         * Create swipe manager for user messages
         */
        fun createForUserMessages(
            context: Context,
            callback: SwipeActionCallback
        ): MessageSwipeGestureManager {
            val manager = MessageSwipeGestureManager(context, callback)
            
            // Clear default actions and add user-specific ones
            manager.clearActions()
            
            // Right swipe: Copy and Reply
            manager.addRightAction(
                SwipeAction(
                    id = "copy",
                    label = "Copy",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.info_color)
                )
            )
            
            manager.addRightAction(
                SwipeAction(
                    id = "edit",
                    label = "Edit",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_edit)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.colorPrimary)
                )
            )
            
            // Left swipe: Delete
            manager.addLeftAction(
                SwipeAction(
                    id = "delete",
                    label = "Delete",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_delete)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.error_color),
                    isDestructive = true,
                    threshold = 0.6f
                )
            )
            
            return manager
        }
        
        /**
         * Create swipe manager for AI messages
         */
        fun createForAiMessages(
            context: Context,
            callback: SwipeActionCallback
        ): MessageSwipeGestureManager {
            val manager = MessageSwipeGestureManager(context, callback)
            
            // Clear default actions and add AI-specific ones
            manager.clearActions()
            
            // Right swipe: Copy and Regenerate
            manager.addRightAction(
                SwipeAction(
                    id = "copy",
                    label = "Copy",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.info_color)
                )
            )
            
            manager.addRightAction(
                SwipeAction(
                    id = "regenerate",
                    label = "Retry",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_refresh)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.success_color)
                )
            )
            
            manager.addRightAction(
                SwipeAction(
                    id = "continue",
                    label = "Continue",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_play_arrow)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.colorPrimary)
                )
            )
            
            // Left swipe: Rate and Save
            manager.addLeftAction(
                SwipeAction(
                    id = "save",
                    label = "Save",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_bookmark)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.accent_gold)
                )
            )
            
            return manager
        }
        
        /**
         * Create minimal swipe manager with only copy action
         */
        fun createMinimal(
            context: Context,
            callback: SwipeActionCallback
        ): MessageSwipeGestureManager {
            val manager = MessageSwipeGestureManager(context, callback)
            
            // Clear default actions and add only copy
            manager.clearActions()
            
            manager.addRightAction(
                SwipeAction(
                    id = "copy",
                    label = "Copy",
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)!!,
                    backgroundColor = ContextCompat.getColor(context, R.color.info_color),
                    threshold = 0.2f
                )
            )
            
            return manager
        }
    }
    
    // Extension functions for unit conversions
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    
    private fun Int.spToPx(): Float {
        return this * context.resources.displayMetrics.scaledDensity
    }
}