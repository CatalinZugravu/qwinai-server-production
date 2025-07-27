package com.cyberflux.qwinai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.utils.BaseThemedActivity
import timber.log.Timber

class TextSelectionActivity : BaseThemedActivity() {

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleCloseAction()
        }
    }

    private lateinit var etEditableText: EditText
    private lateinit var tvSelectableText: TextView
    private lateinit var btnEdit: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var btnCancel: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var tvTitle: TextView

    private var isEditMode = false
    private var originalText = ""
    private var isEditable = false
    private var viewMode = "view" // "view" for AI responses, "edit" for user input
    private var source = "" // "input" or "dialog" to track where it was launched from

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_text_selection)

        // Get intent data
        originalText = intent.getStringExtra("MESSAGE_TEXT") ?: ""
        isEditable = intent.getBooleanExtra("IS_EDITABLE", false)
        viewMode = if (isEditable) "edit" else "view"
        source = intent.getStringExtra("SOURCE") ?: "input" // Default to input if not specified

        val title = intent.getStringExtra("TITLE") ?: getDefaultTitle()
        val hint = intent.getStringExtra("HINT") ?: "Enter your text here..."

        // Initialize views
        initializeViews()

        // Set up based on mode
        if (isEditable) {
            setupEditableMode(title, hint)
        } else {
            setupViewOnlyMode(title)
        }

        setupClickListeners()
        
        // Register the back pressed callback
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        Timber.d("TextSelectionActivity opened in mode: $viewMode, editable: $isEditable, source: $source")
    }

    private fun getDefaultTitle(): String {
        return if (isEditable) "Edit Text" else "Select Text"
    }

    private fun initializeViews() {
        tvSelectableText = findViewById(R.id.tvSelectableText)
        etEditableText = findViewById(R.id.etEditableText)
        btnEdit = findViewById(R.id.btnEdit)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        btnClose = findViewById(R.id.btnClose)
        tvTitle = findViewById(R.id.tvTitle)

        btnClose.setOnClickListener {
            handleCloseAction()
        }
    }

    private fun setupEditableMode(title: String, hint: String) {
        tvSelectableText.text = originalText
        etEditableText.setText(originalText)
        etEditableText.hint = hint

        // Show edit button initially
        btnEdit.visibility = View.VISIBLE
        btnSave.visibility = View.GONE
        btnCancel.visibility = View.GONE

        // Show read-only view initially
        tvSelectableText.visibility = View.VISIBLE
        etEditableText.visibility = View.GONE

        updateTitle(title)

        Timber.d("Set up editable mode with title: $title")
    }

    private fun setupViewOnlyMode(title: String) {
        tvSelectableText.text = originalText
        tvSelectableText.visibility = View.VISIBLE
        etEditableText.visibility = View.GONE

        // Configure text view for proper selection
        tvSelectableText.setTextIsSelectable(true)
        tvSelectableText.isLongClickable = true
        tvSelectableText.isClickable = true
        tvSelectableText.isFocusable = true
        tvSelectableText.isFocusableInTouchMode = true

        // Configure text selection action mode
        tvSelectableText.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: Menu?): Boolean {
                return true // Allow selection action mode
            }

            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: android.view.ActionMode?, item: MenuItem?): Boolean {
                return false
            }

            override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                // Do nothing
            }
        }

        // Add a hint toast for user guidance
        Toast.makeText(this, "Tap and hold on text to select and copy", Toast.LENGTH_LONG).show()

        // Force focus for better selection
        tvSelectableText.requestFocus()

        // Hide ALL edit controls for view-only mode if not editable
        btnEdit.visibility = if (isEditable) View.VISIBLE else View.GONE
        btnSave.visibility = View.GONE
        btnCancel.visibility = View.GONE

        updateTitle(title)

        Timber.d("Set up view-only mode with title: $title")

        // Register clipboard change listener for feedback
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            runOnUiThread {
                Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        // Force focus on text view after a delay to ensure UI is ready
        Handler(Looper.getMainLooper()).postDelayed({
            tvSelectableText.requestFocus()
        }, 300)
    }

    private fun setupClickListeners() {
        btnEdit.setOnClickListener {
            enterEditMode()
        }

        btnSave.setOnClickListener {
            saveChanges()
        }

        btnCancel.setOnClickListener {
            cancelEditing()
        }

        // Add text change listener for edit mode
        etEditableText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSaveButtonState()
            }
        })
    }

    private fun enterEditMode() {
        if (!isEditable) {
            Timber.w("Attempted to enter edit mode when not editable")
            return
        }

        isEditMode = true

        // Switch to edit view
        tvSelectableText.visibility = View.GONE
        etEditableText.visibility = View.VISIBLE

        // Update buttons
        btnEdit.visibility = View.GONE
        btnSave.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE

        // Focus and select all text
        etEditableText.requestFocus()
        etEditableText.selectAll()

        updateTitle("Edit Text")
        updateSaveButtonState()

        Timber.d("Entered edit mode")
    }

    private fun saveChanges() {
        val newText = etEditableText.text.toString()

        // Return the edited text
        val resultIntent = Intent().apply {
            putExtra("EDITED_TEXT", newText)
            putExtra("TEXT_CHANGED", newText != originalText)
            putExtra("ORIGINAL_TEXT", originalText)
            putExtra("SOURCE", source) // Pass back the source to know where to update
        }

        Timber.d("Saving changes: original=${originalText.length} chars, new=${newText.length} chars, changed=${newText != originalText}, source=$source")

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun cancelEditing() {
        if (hasTextChanged()) {
            showUnsavedChangesDialog()
        } else {
            exitEditMode()
        }
    }

    private fun exitEditMode() {
        isEditMode = false

        // Reset text to original
        etEditableText.setText(originalText)

        // Switch back to read-only view
        etEditableText.visibility = View.GONE
        tvSelectableText.visibility = View.VISIBLE

        // Update buttons
        btnSave.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnEdit.visibility = View.VISIBLE

        updateTitle(if (isEditable) "Edit Text" else "View Text")

        Timber.d("Exited edit mode")
    }

    private fun hasTextChanged(): Boolean {
        return isEditMode && etEditableText.text.toString() != originalText
    }

    private fun updateSaveButtonState() {
        if (!isEditMode) return

        val hasChanges = hasTextChanged()
        val isNotEmpty = etEditableText.text.toString().isNotEmpty()

        btnSave.isEnabled = hasChanges && isNotEmpty
        btnSave.alpha = if (btnSave.isEnabled) 1.0f else 0.5f
    }

    private fun handleCloseAction() {
        if (isEditMode && hasTextChanged()) {
            showUnsavedChangesDialog()
        } else {
            // Return with no changes
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. What would you like to do?")
            .setPositiveButton("Save") { _, _ -> saveChanges() }
            .setNegativeButton("Discard") { _, _ ->
                if (isEditMode) {
                    exitEditMode()
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun updateTitle(title: String) {
        tvTitle.text = title
    }

}