package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.SpannableBuilder
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.CorePlugin
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import timber.log.Timber

/**
 * Custom Markwon plugin to handle code blocks by extracting them from the text
 * and rendering them in a separate LinearLayout container.
 * This solves the positioning issue where code blocks appear at the bottom.
 */
class CodeBlockPlugin private constructor(
    private val context: Context,
    private val codeBlockContainer: LinearLayout?
) : AbstractMarkwonPlugin() {

    private var codeBlockCounter = 0

    companion object {
        @JvmStatic
        fun create(context: Context, codeBlockContainer: LinearLayout?): CodeBlockPlugin {
            return CodeBlockPlugin(context, codeBlockContainer)
        }
    }
    
    fun resetCounter() {
        codeBlockCounter = 0
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder
            .on(FencedCodeBlock::class.java) { visitor, fencedCodeBlock ->
                handleCodeBlock(visitor, fencedCodeBlock.literal, fencedCodeBlock.info ?: "")
            }
            .on(IndentedCodeBlock::class.java) { visitor, indentedCodeBlock ->
                handleCodeBlock(visitor, indentedCodeBlock.literal, "")
            }
    }
    

    private fun handleCodeBlock(visitor: MarkwonVisitor, code: String, language: String) {
        codeBlockCounter++
        Timber.d("CODE_BLOCK_DEBUG: CodeBlockPlugin - Processing code block #$codeBlockCounter")
        Timber.d("CODE_BLOCK_DEBUG: Language: '$language', Code length: ${code.length}")
        Timber.d("CODE_BLOCK_DEBUG: Container available: ${codeBlockContainer != null}")
        
        // Instead of adding the code block to the main text, we'll add it to the separate container
        if (codeBlockContainer != null) {
            addCodeBlockToContainer(code, language.trim(), container = codeBlockContainer, index = codeBlockCounter - 1)
            
            // Add a styled placeholder in the main text to maintain proper spacing
            val spannableBuilder = visitor.builder()
            val placeholder = "\n[Code Block $codeBlockCounter${if (language.isNotEmpty()) ": $language" else ""}]\n"
            spannableBuilder.append(placeholder)
            
            // Style the placeholder to be more subtle
            val start = spannableBuilder.length - placeholder.length
            val end = spannableBuilder.length
            spannableBuilder.setSpan(
                ForegroundColorSpan(Color.parseColor("#888888")),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            // Fallback: hide the code block entirely if no container (don't render it twice)
            Timber.w("CODE_BLOCK_DEBUG: No container available, hiding code block")
            // Don't add anything to the text - this prevents default rendering
        }
    }

    private fun addCodeBlockToContainer(code: String, language: String, container: LinearLayout, index: Int = 0) {
        try {
            Timber.d("CODE_BLOCK_DEBUG: Adding code block #${index + 1} to container")
            
            // Inflate the code block layout
            val inflater = LayoutInflater.from(context)
            val codeBlockView = inflater.inflate(R.layout.item_code_block, container, false)
            
            // Find views in the inflated layout
            val languageLabel: TextView = codeBlockView.findViewById(R.id.tvLanguage)
            val codeContent: TextView = codeBlockView.findViewById(R.id.tvCodeContent)
            val copyButton: View = codeBlockView.findViewById(R.id.btnCopyCode)
            
            // Set language label
            languageLabel.text = if (language.isNotEmpty()) language.uppercase() else "CODE"
            
            // Set code content with syntax highlighting
            codeContent.text = code.trim()
            codeContent.typeface = Typeface.MONOSPACE
            
            // Apply syntax highlighting if available
            try {
                val highlightedCode = CodeSyntaxHighlighter.highlight(context, code.trim(), language)
                codeContent.text = highlightedCode
            } catch (e: Exception) {
                Timber.w("Failed to apply syntax highlighting: ${e.message}")
                codeContent.text = code.trim()
            }
            
            // Set up copy button
            copyButton.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Code", code.trim())
                clipboard.setPrimaryClip(clip)
                
                // Show feedback
                android.widget.Toast.makeText(context, "Code copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // Add the code block view to the container
            container.addView(codeBlockView)
            container.visibility = View.VISIBLE
            
            Timber.d("CODE_BLOCK_DEBUG: Code block #${index + 1} added successfully. Container child count: ${container.childCount}")
            
        } catch (e: Exception) {
            Timber.e(e, "CODE_BLOCK_DEBUG: Failed to add code block to container")
        }
    }
}