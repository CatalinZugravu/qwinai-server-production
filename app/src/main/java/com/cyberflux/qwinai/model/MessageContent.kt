package com.cyberflux.qwinai.model

/**
 * Represents different types of content within a chat message
 */
sealed class MessageContent {
    
    /**
     * Regular text content (markdown processed)
     */
    data class TextContent(
        val text: String,
        val processedText: CharSequence? = null
    ) : MessageContent()
    
    /**
     * Code block content with language and syntax highlighting
     */
    data class CodeBlockContent(
        val language: String,
        val code: String,
        val highlightedCode: CharSequence? = null
    ) : MessageContent()
    
    /**
     * Image content
     */
    data class ImageContent(
        val imageUrl: String,
        val altText: String? = null
    ) : MessageContent()
    
    companion object {
        const val VIEW_TYPE_TEXT = 0
        const val VIEW_TYPE_CODE_BLOCK = 1
        const val VIEW_TYPE_IMAGE = 2
    }
}

/**
 * Represents a parsed chat message with multiple content types
 */
data class ParsedMessage(
    val originalMessage: com.cyberflux.qwinai.model.ChatMessage,
    val contentItems: List<MessageContent>
)