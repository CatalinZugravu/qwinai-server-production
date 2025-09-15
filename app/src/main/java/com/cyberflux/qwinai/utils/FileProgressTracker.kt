package com.cyberflux.qwinai.utils

import android.annotation.SuppressLint
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cyberflux.qwinai.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles progress tracking and UI updates for file operations
 * Updated with thread safety fixes
 */
class FileProgressTracker {

    // Progress state flows
    private val _progressFlow = MutableStateFlow<Triple<Int, String, ProcessingStage>>(Triple(0, "Initializing...", ProcessingStage.INITIALIZING))
    val progressFlow: StateFlow<Triple<Int, String, ProcessingStage>> = _progressFlow.asStateFlow()

    // Current values
    private var currentProgress = 0
    private var currentMessage = "Initializing..."
    private var currentStage = ProcessingStage.INITIALIZING

    // Item view references
    private var itemView: View? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var progressStageText: TextView? = null
    private var statusTextView: TextView? = null

    // Dot loading animation views
    private var dotLoadingView: DotLoadingTextView? = null

    // Layout containers for switching visibility
    private var infoLayout: View? = null
    private var progressLayout: View? = null

    // Processing state
    private var isProcessing = false

    /**
     * Initialize progress tracker with selected file item (image type)
     */
    fun initWithImageFileItem(itemView: View) {
        this.itemView = itemView

        // Get references to views
        progressBar = itemView.findViewById(R.id.progressBar)
        progressText = itemView.findViewById(R.id.tvProgressPercent)
        progressStageText = itemView.findViewById(R.id.tvProgressStage)
        dotLoadingView = itemView.findViewById(R.id.loadingDotsView)
        statusTextView = itemView.findViewById(R.id.tvProgressStage)

        // Get layout containers
        infoLayout = itemView.findViewById(R.id.file_info_layout)
        progressLayout = itemView.findViewById(R.id.progressLayout)
    }

    /**
     * Show progress in the item view
     * Now thread-safe with MainScope()
     */
    fun showProgress() {
        MainScope().launch {
            if (progressLayout == null || infoLayout == null) {
                Timber.e("FileProgressTracker not properly initialized with a view")
                return@launch
            }

            isProcessing = true
            infoLayout?.visibility = View.GONE
            progressLayout?.visibility = View.VISIBLE

            // Start the dot animation
            dotLoadingView?.startAnimation()

            // Initialize progress
            updateProgress(0, "Initializing...", ProcessingStage.INITIALIZING)
        }
    }

    /**
     * Update progress values
     * Thread-safe implementation using MainScope()
     */
    @SuppressLint("SetTextI18x")
    fun updateProgress(percent: Int, message: String, stage: ProcessingStage) {
        // Store the current values
        currentProgress = percent
        currentMessage = message
        currentStage = stage

        // Update the state flow
        _progressFlow.value = Triple(percent, message, stage)

        // Update UI elements on the main thread
        MainScope().launch {
            try {
                // Update UI directly if available - hide individual elements
                progressBar?.progress = percent
                progressText?.text = "$percent%"
                // Don't show the stage text as it duplicates the dot loading view
                progressStageText?.visibility = View.GONE

                // Update dot loading view with the message
                dotLoadingView?.let { dotView ->
                    dotView.setBaseText(message)

                    // Only animate if still processing
                    if (stage != ProcessingStage.COMPLETE && stage != ProcessingStage.ERROR) {
                        dotView.startAnimation()
                    } else {
                        dotView.stopAnimation()
                    }
                }

                // Also update status text if available
                statusTextView?.text = message

                Timber.d("File progress: $percent% - $message (${stage.displayName})")
            } catch (e: Exception) {
                Timber.e(e, "Error updating progress UI: ${e.message}")
            }
        }
    }

    /**
     * Connect to lifecycle for updates
     */
    @SuppressLint("SetTextI18x")
    fun observeProgress(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            progressFlow.collect { (percent, message, stage) ->
                // Use MainScope to ensure UI updates on main thread
                MainScope().launch {
                    try {
                        // Update progress bar and text
                        progressBar?.progress = percent
                        progressText?.text = "$percent%"
                        progressStageText?.text = message

                        // Update dot loading view base text based on stage
                        val baseText = when (stage) {
                            ProcessingStage.INITIALIZING -> "Init"
                            ProcessingStage.READING_FILE -> "Reading"
                            ProcessingStage.ANALYZING_FORMAT -> "Analyzing"
                            ProcessingStage.CONVERTING -> "Converting"
                            ProcessingStage.PROCESSING_PAGES -> "Processing"
                            ProcessingStage.OPTIMIZING -> "Optimizing"
                            ProcessingStage.ENCODING -> "Encoding"
                            ProcessingStage.FINALIZING -> "Finishing"
                            ProcessingStage.COMPLETE -> "Done"
                            ProcessingStage.ERROR -> "Error"
                        }

                        dotLoadingView?.setBaseText(baseText)
                    } catch (e: Exception) {
                        Timber.e(e, "Error in progress observer: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Hide progress and show file info again
     * Thread-safe implementation
     */
    fun hideProgress() {
        MainScope().launch {
            try {
                if (progressLayout == null || infoLayout == null) {
                    Timber.e("FileProgressTracker not properly initialized with a view")
                    return@launch
                }

                isProcessing = false

                // Stop the animation
                dotLoadingView?.stopAnimation()

                // Hide progress layout, show info layout
                progressLayout?.visibility = View.GONE
                infoLayout?.visibility = View.VISIBLE
            } catch (e: Exception) {
                Timber.e(e, "Error hiding progress: ${e.message}")
            }
        }
    }

    /**
     * Processing stages for files
     */
    enum class ProcessingStage(val displayName: String) {
        INITIALIZING("Initializing"),
        READING_FILE("Reading File"),
        ANALYZING_FORMAT("Analyzing Format"),
        CONVERTING("Converting"),
        PROCESSING_PAGES("Processing Pages"),
        OPTIMIZING("Optimizing"),
        ENCODING("Encoding"),
        FINALIZING("Finalizing"),
        COMPLETE("Complete"),
        ERROR("Error")
    }
}

/**
 * Animated text view that shows loading dots
 */
