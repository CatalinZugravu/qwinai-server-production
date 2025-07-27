package com.cyberflux.qwinai.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.cyberflux.qwinai.R
import timber.log.Timber

/**
 * Handles dialogs related to token limits
 */
object TokenLimitDialogHandler {

    /**
     * Shows a dialog when token limit is approaching
     */
    fun showTokenLimitApproachingDialog(
        context: Context,
        tokenPercentage: Float,
        onContinue: () -> Unit,
        onSummarize: () -> Unit,
        onNewChat: () -> Unit
    ) {
        if (context !is Activity || context.isFinishing) {
            Timber.d("Cannot show dialog - activity not available")
            return
        }

        // Inflate custom view
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_token_limit_warning, null)

        // Get references to views
        val titleText = view.findViewById<TextView>(R.id.dialogTitle)
        val messageText = view.findViewById<TextView>(R.id.dialogMessage)
        val btnContinue = view.findViewById<Button>(R.id.btnContinue)
        val btnSummarize = view.findViewById<Button>(R.id.btnSummarize)
        val btnNewChat = view.findViewById<Button>(R.id.btnNewChat)

        // Format percentage for display
        val percentUsed = (tokenPercentage * 100).toInt()

        // Set content
        titleText.text = "Token Limit Approaching"
        messageText.text = "This conversation is using $percentUsed% of available tokens. " +
                "Soon the AI might not have enough space to provide full responses."

        // Create dialog
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        // Set button actions
        btnContinue.setOnClickListener {
            dialog.dismiss()
            onContinue()
        }

        btnSummarize.setOnClickListener {
            dialog.dismiss()
            onSummarize()
        }

        btnNewChat.setOnClickListener {
            dialog.dismiss()
            onNewChat()
        }

        // Show dialog
        dialog.show()
    }

    /**
     * Shows a dialog when token limit is reached
     */
    fun showTokenLimitReachedDialog(
        context: Context,
        modelId: String,
        isSubscribed: Boolean,
        onSummarize: () -> Unit,
        onNewChat: () -> Unit,
        onUpgrade: () -> Unit
    ) {
        if (context !is Activity || context.isFinishing) {
            Timber.d("Cannot show dialog - activity not available")
            return
        }

        // Inflate custom view
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_token_limit_reached, null)

        // Get references to views
        val titleText = view.findViewById<TextView>(R.id.dialogTitle)
        val messageText = view.findViewById<TextView>(R.id.dialogMessage)
        val btnSummarize = view.findViewById<Button>(R.id.btnSummarize)
        val btnNewChat = view.findViewById<Button>(R.id.btnNewChat)
        val btnUpgrade = view.findViewById<Button>(R.id.btnUpgrade)

        // Set content
        titleText.text = "Token Limit Reached"

        val maxTokens = TokenValidator.getEffectiveMaxInputTokens(modelId, isSubscribed)
        val formattedTokens = TokenValidator.formatTokenCount(maxTokens)

        messageText.text = "This conversation has reached the ${formattedTokens} token limit. " +
                "The AI can't process any more messages in this context."

        // Show/hide upgrade button based on subscription
        btnUpgrade.visibility = if (isSubscribed) View.GONE else View.VISIBLE

        // Create dialog
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false) // Force user to choose an option
            .create()

        // Set button actions
        btnSummarize.setOnClickListener {
            dialog.dismiss()
            onSummarize()
        }

        btnNewChat.setOnClickListener {
            dialog.dismiss()
            onNewChat()
        }

        btnUpgrade.setOnClickListener {
            dialog.dismiss()
            onUpgrade()
        }

        // Show dialog
        dialog.show()
    }
}