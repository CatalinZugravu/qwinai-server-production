package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.annotation.NonNull
import com.cyberflux.qwinai.adapter.CodeBlockSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import timber.log.Timber

class EnhancedCustomCodeBlockPlugin private constructor(
    private val context: Context,
    private val codeContainer: TextView,
    private val enableSyntaxHighlighting: Boolean = true
) : AbstractMarkwonPlugin() {

    companion object {
        fun create(
            context: Context,
            codeContainer: TextView,
            enableSyntaxHighlighting: Boolean = true
        ): EnhancedCustomCodeBlockPlugin {
            return EnhancedCustomCodeBlockPlugin(context, codeContainer, enableSyntaxHighlighting)
        }
    }

    override fun configureVisitor(@NonNull builder: MarkwonVisitor.Builder) {
        builder
            .on(FencedCodeBlock::class.java) { visitor, fencedCodeBlock ->
                handleFencedCodeBlock(visitor, fencedCodeBlock)
            }
            .on(IndentedCodeBlock::class.java) { visitor, indentedCodeBlock ->
                handleIndentedCodeBlock(visitor, indentedCodeBlock)
            }
    }

    private fun handleFencedCodeBlock(visitor: MarkwonVisitor, fencedCodeBlock: FencedCodeBlock) {
        try {
            val codeContent = fencedCodeBlock.literal ?: ""
            val language = fencedCodeBlock.info?.trim()?.split("\\s+".toRegex())?.firstOrNull()?.lowercase() ?: ""
            
            Timber.d("Creating fenced code block: language='$language', content length=${codeContent.length}")
            
            // Get the start position for this node in the text
            val start = visitor.length()
            
            // Create our custom span and add it
            val codeBlockSpan = CodeBlockSpan(context, codeContent, language, codeContainer)
            
            // Add placeholder text for the span to cover
            visitor.builder().append("[CODE_BLOCK]")
            
            // Apply our custom span using the builder's setSpan method
            visitor.setSpansForNodeOptional(fencedCodeBlock, start)
            visitor.builder().setSpan(codeBlockSpan, start, visitor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling fenced code block: ${e.message}")
            // Fallback to default behavior - just append the content as plain text
            visitor.builder().append(fencedCodeBlock.literal ?: "")
        }
    }

    private fun handleIndentedCodeBlock(visitor: MarkwonVisitor, indentedCodeBlock: IndentedCodeBlock) {
        try {
            val codeContent = indentedCodeBlock.literal ?: ""
            
            Timber.d("Creating indented code block: content length=${codeContent.length}")
            
            // Get the start position for this node in the text
            val start = visitor.length()
            
            // Create our custom span and add it
            val codeBlockSpan = CodeBlockSpan(context, codeContent, "", codeContainer)
            
            // Add placeholder text for the span to cover
            visitor.builder().append("[CODE_BLOCK]")
            
            // Apply our custom span using the builder's setSpan method
            visitor.setSpansForNodeOptional(indentedCodeBlock, start)
            visitor.builder().setSpan(codeBlockSpan, start, visitor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
        } catch (e: Exception) {
            Timber.e(e, "Error handling indented code block: ${e.message}")
            // Fallback to default behavior - just append the content as plain text
            visitor.builder().append(indentedCodeBlock.literal ?: "")
        }
    }
}
