package com.cyberflux.qwinai.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import timber.log.Timber

/**
 * Fixed ScrollArrowsHelper that shows only ONE arrow at a time
 * Arrows appear when user starts scrolling and auto-hide after scrolling stops
 */
class ScrollArrowsHelper(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val fabScrollToTop: FloatingActionButton,
    private val fabScrollToBottom: FloatingActionButton
) {

    private var isInitialized = false
    private var isScrolling = false
    private var lastScrollDirection = 0 // -1 = up, 1 = down, 0 = none
    private var hideArrowsRunnable: Runnable? = null
    private val hideHandler = Handler(Looper.getMainLooper())
    private val AUTO_HIDE_DELAY = 2000L // Hide arrows after 2 seconds of no scrolling


    /**
     * Initialize the scroll arrows functionality
     */
    fun initialize() {
        try {
            // Set up click listeners
            fabScrollToTop.setOnClickListener {
                scrollToTop()
            }

            fabScrollToBottom.setOnClickListener {
                scrollToBottom()
            }

            // Set up scroll listener for swipe detection
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            // User started scrolling
                            onScrollStarted()
                        }
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            // Scroll is settling (fling motion)
                            onScrollContinuing()
                        }
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            // Scrolling stopped
                            onScrollStopped()
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Track scroll direction
                    if (dy > 0) {
                        lastScrollDirection = 1 // Scrolling down
                    } else if (dy < 0) {
                        lastScrollDirection = -1 // Scrolling up
                    }

                    // Show arrows when user is actively scrolling
                    if (Math.abs(dy) > 0) {
                        onScrollActivity()
                    }
                }
            })

            // Initially hide both arrows
            hideAllArrows()

            isInitialized = true
            Timber.d("Fixed ScrollArrowsHelper initialized successfully")

        } catch (e: Exception) {
            Timber.e(e, "Error initializing ScrollArrowsHelper: ${e.message}")
        }
    }

    /**
     * Called when user starts scrolling
     */
    private fun onScrollStarted() {
        isScrolling = true
        showSingleArrowIfNeeded()
        cancelAutoHide()
        Timber.d("Scroll started - showing single arrow")
    }

    /**
     * Called during scroll activity
     */
    private fun onScrollActivity() {
        if (!isScrolling) {
            isScrolling = true
            showSingleArrowIfNeeded()
        }
        cancelAutoHide()
    }

    /**
     * Called when scrolling continues (settling)
     */
    private fun onScrollContinuing() {
        // Keep arrow visible during settling
        cancelAutoHide()
        scheduleAutoHide()
    }

    /**
     * Called when scrolling stops
     */
    private fun onScrollStopped() {
        isScrolling = false
        scheduleAutoHide()
        Timber.d("Scroll stopped - scheduling arrow hide")
    }

    /**
     * Show ONLY ONE arrow based on scroll direction and position
     */
    private fun showSingleArrowIfNeeded() {
        if (!isInitialized) return

        try {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            if (layoutManager == null) {
                hideAllArrows()
                return
            }

            val adapter = recyclerView.adapter
            val itemCount = adapter?.itemCount ?: 0

            if (itemCount <= 1) {
                hideAllArrows()
                return
            }

            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

            // Determine which arrow to show based on position and scroll direction
            val atTop = firstVisiblePosition == 0
            val atBottom = lastVisiblePosition >= itemCount - 1

            when {
                atTop && atBottom -> {
                    // All content fits on screen - no arrows needed
                    hideAllArrows()
                }
                atTop -> {
                    // At top - only show down arrow
                    showOnlyDownArrow()
                }
                atBottom -> {
                    // At bottom - only show up arrow
                    showOnlyUpArrow()
                }
                else -> {
                    // In middle - show arrow based on scroll direction
                    when (lastScrollDirection) {
                        1 -> showOnlyDownArrow() // Was scrolling down, show down arrow
                        -1 -> showOnlyUpArrow()   // Was scrolling up, show up arrow
                        else -> {
                            // No clear direction, prioritize based on position
                            val middlePosition = itemCount / 2
                            if (firstVisiblePosition < middlePosition) {
                                showOnlyDownArrow() // Closer to top, show down
                            } else {
                                showOnlyUpArrow() // Closer to bottom, show up
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error showing single arrow: ${e.message}")
        }
    }

    /**
     * Show only the UP arrow, hide DOWN arrow
     */
    private fun showOnlyUpArrow() {
        hideScrollToBottomArrow()
        showScrollToTopArrow()
        Timber.d("Showing only UP arrow")
    }

    /**
     * Show only the DOWN arrow, hide UP arrow
     */
    private fun showOnlyDownArrow() {
        hideScrollToTopArrow()
        showScrollToBottomArrow()
        Timber.d("Showing only DOWN arrow")
    }

    /**
     * Schedule auto-hide of arrows
     */
    private fun scheduleAutoHide() {
        cancelAutoHide()

        hideArrowsRunnable = Runnable {
            hideAllArrows()
            Timber.d("Auto-hiding arrows after inactivity")
        }

        hideHandler.postDelayed(hideArrowsRunnable!!, AUTO_HIDE_DELAY)
    }

    /**
     * Cancel scheduled auto-hide
     */
    private fun cancelAutoHide() {
        hideArrowsRunnable?.let { runnable ->
            hideHandler.removeCallbacks(runnable)
            hideArrowsRunnable = null
        }
    }

    /**
     * Scroll to the top of the RecyclerView
     */
    private fun scrollToTop() {
        try {
            val adapter = recyclerView.adapter
            if (adapter != null && adapter.itemCount > 0) {
                recyclerView.smoothScrollToPosition(0)

                // Haptic feedback
                provideHapticFeedback()

                // Hide arrows immediately after user action
                hideAllArrows()

                Timber.d("Scrolled to top")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scrolling to top: ${e.message}")
        }
    }

    /**
     * Scroll to the bottom of the RecyclerView
     */
    private fun scrollToBottom() {
        try {
            val adapter = recyclerView.adapter
            if (adapter != null && adapter.itemCount > 0) {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

                // Haptic feedback
                provideHapticFeedback()

                // Hide arrows immediately after user action
                hideAllArrows()

                Timber.d("Scrolled to bottom")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scrolling to bottom: ${e.message}")
        }
    }

    /**
     * Provide haptic feedback
     */
    private fun provideHapticFeedback() {
        HapticManager.lightVibration(context)
    }

    /**
     * Show scroll to top arrow with animation
     */
    private fun showScrollToTopArrow() {
        if (fabScrollToTop.visibility != View.VISIBLE) {
            fabScrollToTop.show()
        }
    }

    /**
     * Hide scroll to top arrow with animation
     */
    private fun hideScrollToTopArrow() {
        if (fabScrollToTop.visibility == View.VISIBLE) {
            fabScrollToTop.hide()
        }
    }

    /**
     * Show scroll to bottom arrow with animation
     */
    private fun showScrollToBottomArrow() {
        if (fabScrollToBottom.visibility != View.VISIBLE) {
            fabScrollToBottom.show()
        }
    }

    /**
     * Hide scroll to bottom arrow with animation
     */
    private fun hideScrollToBottomArrow() {
        if (fabScrollToBottom.visibility == View.VISIBLE) {
            fabScrollToBottom.hide()
        }
    }

    /**
     * Hide both arrows immediately
     */
    private fun hideAllArrows() {
        hideScrollToTopArrow()
        hideScrollToBottomArrow()
        cancelAutoHide()
        lastScrollDirection = 0 // Reset direction
    }

    /**
     * Force update the buttons visibility (useful when data changes)
     * This version respects the swipe-based behavior and shows only one arrow
     */
    fun updateButtonsState() {
        if (isInitialized) {
            // Only show arrows if user is currently scrolling
            if (isScrolling) {
                recyclerView.post {
                    showSingleArrowIfNeeded()
                }
            } else {
                // Hide arrows when not scrolling
                hideAllArrows()
            }
        }
    }

    /**
     * Temporarily hide buttons (useful during user input)
     */
    fun hideButtonsTemporarily(durationMs: Long = 3000) {
        if (isInitialized) {
            cancelAutoHide()
            hideAllArrows()

            // Don't automatically show them again - wait for user to scroll
            Handler(Looper.getMainLooper()).postDelayed({
                // Only restore if user starts scrolling again
                // No automatic restoration
            }, durationMs)
        }
    }

    /**
     * Force show ONE arrow for a brief moment (useful for indicating scroll capability)
     */
    fun showArrowsBriefly(durationMs: Long = 1500) {
        if (isInitialized) {
            showSingleArrowIfNeeded()

            Handler(Looper.getMainLooper()).postDelayed({
                if (!isScrolling) {
                    hideAllArrows()
                }
            }, durationMs)
        }
    }

    /**
     * Set up automatic updates when adapter data changes
     */
    fun setupAdapterObserver() {
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                // Don't automatically show arrows on data change
                // User needs to scroll to see them
                if (isScrolling) {
                    updateButtonsState()
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                // For new messages at the end, briefly show down arrow only
                val adapter = recyclerView.adapter
                if (adapter != null && positionStart >= adapter.itemCount - itemCount) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isScrolling) {
                            showOnlyDownArrow()
                            scheduleAutoHide()
                        }
                    }, 300)
                }
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                if (isScrolling) {
                    updateButtonsState()
                }
            }
        })
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        isInitialized = false
        cancelAutoHide()
        hideAllArrows()
        isScrolling = false
        lastScrollDirection = 0
    }

    companion object {
        /**
         * Create and initialize ScrollArrowsHelper in one call
         */
        fun setup(
            context: Context,
            recyclerView: RecyclerView,
            fabScrollToTop: FloatingActionButton,
            fabScrollToBottom: FloatingActionButton
        ): ScrollArrowsHelper {
            return ScrollArrowsHelper(context, recyclerView, fabScrollToTop, fabScrollToBottom).apply {
                initialize()
                setupAdapterObserver()
            }
        }
    }
}