package com.cyberflux.qwinai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.CodeSyntaxHighlighter
import com.cyberflux.qwinai.utils.HapticManager
import timber.log.Timber

class CodeViewerActivity : BaseThemedActivity() {
    private var codeContent: String = ""
    private var languageName: String = ""
    
    companion object {
        const val EXTRA_CODE_CONTENT = "extra_code_content"
        const val EXTRA_LANGUAGE = "extra_language"
        
        fun createIntent(context: Context, codeContent: String, language: String): Intent {
            return Intent(context, CodeViewerActivity::class.java).apply {
                putExtra(EXTRA_CODE_CONTENT, codeContent)
                putExtra(EXTRA_LANGUAGE, language)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Timber.d("CodeViewerActivity: Starting onCreate")
            
            // Enable edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Set layout directly
            setContentView(R.layout.activity_code_viewer)
            Timber.d("CodeViewerActivity: Layout set successfully")
            
            // Handle system window insets for edge-to-edge display
            setupWindowInsets()
            
            // Get data from intent
            codeContent = intent.getStringExtra(EXTRA_CODE_CONTENT) ?: ""
            languageName = intent.getStringExtra(EXTRA_LANGUAGE) ?: "Code"
            
            Timber.d("CodeViewerActivity: Got data - language='$languageName', code length=${codeContent.length}")
            
            setupViews()
            setupClickListeners()
            
            Timber.d("CodeViewerActivity: Setup completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "CodeViewerActivity onCreate failed: ${e.message}")
            e.printStackTrace()
            finish()
        }
    }
    
    private fun setupViews() {
        try {
            // Find views using findViewById
            val tvLanguageTitle = findViewById<TextView>(R.id.tvLanguageTitle)
            val tvCodeContent = findViewById<TextView>(R.id.tvCodeContent)
            
            Timber.d("CodeViewerActivity: Found views - title: ${tvLanguageTitle != null}, content: ${tvCodeContent != null}")
            
            // Set language title
            tvLanguageTitle?.text = languageName
            
            // Apply syntax highlighting to code content
            if (tvCodeContent != null && codeContent.isNotEmpty()) {
                val highlightedCode = CodeSyntaxHighlighter.highlight(this, codeContent, languageName)
                tvCodeContent.text = highlightedCode
                Timber.d("CodeViewerActivity: Applied syntax highlighting for language: $languageName")
            } else {
                // Fallback to plain text
                tvCodeContent?.text = codeContent
            }
            
            // Ensure horizontal scrolling is enabled
            tvCodeContent?.setHorizontallyScrolling(true)
            
            Timber.d("CodeViewerActivity: Views setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up views: ${e.message}")
            // Fallback to plain text if highlighting fails
            findViewById<TextView>(R.id.tvCodeContent)?.text = codeContent
        }
    }
    
    private fun setupClickListeners() {
        try {
            // Find buttons using findViewById
            val btnBack = findViewById<ImageButton>(R.id.btnBack)
            val btnCopyContainer = findViewById<LinearLayout>(R.id.btnCopyContainer)
            
            Timber.d("CodeViewerActivity: Found buttons - back: ${btnBack != null}, copy: ${btnCopyContainer != null}")
            
            // Back button
            btnBack?.setOnClickListener {
                HapticManager.lightVibration(this)
                onBackPressedDispatcher.onBackPressed()
            }
            
            // Copy button
            btnCopyContainer?.setOnClickListener {
                HapticManager.lightVibration(this)
                copyCodeToClipboard()
            }
            
            Timber.d("CodeViewerActivity: Click listeners setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up click listeners: ${e.message}")
        }
    }
    
    private fun copyCodeToClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Code", codeContent)
            clipboard.setPrimaryClip(clip)
            
            // Find UI elements
            val copyText = findViewById<TextView>(R.id.copyText)
            val copyIcon = findViewById<android.widget.ImageView>(R.id.copyIcon)
            
            // Update UI to show copy success
            copyText?.text = "Copied!"
            copyIcon?.setImageResource(R.drawable.ic_checkmark_green)
            
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
            
            // Reset back to "Copy" after 2 seconds
            findViewById<android.view.View>(android.R.id.content)?.postDelayed({
                if (!isFinishing) {
                    copyText?.text = "Copy"
                    copyIcon?.setImageResource(R.drawable.ic_copy_24)
                }
            }, 2000)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy code: ${e.message}")
            Toast.makeText(this, "Failed to copy code", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupWindowInsets() {
        try {
            val topBarLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.topBarLayout)
            val rootView = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.root)
            
            if (topBarLayout != null && rootView != null) {
                ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    
                    // Apply top padding to the header layout to avoid status bar overlap
                    topBarLayout.setPadding(
                        topBarLayout.paddingLeft,
                        insets.top + topBarLayout.paddingTop,
                        topBarLayout.paddingRight,
                        topBarLayout.paddingBottom
                    )
                    
                    // Apply bottom padding to root for navigation bar if needed
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        insets.bottom
                    )
                    
                    Timber.d("CodeViewerActivity: Applied window insets - top: ${insets.top}, bottom: ${insets.bottom}")
                    
                    WindowInsetsCompat.CONSUMED
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up window insets: ${e.message}")
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}