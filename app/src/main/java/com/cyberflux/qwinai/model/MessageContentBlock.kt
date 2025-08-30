package com.cyberflux.qwinai.model

/**
 * Sealed class representing different types of content blocks within a message.
 * This enables proper parsing and rendering of mixed content (text + code blocks)
 * while maintaining original order and supporting interactive elements.
 */
sealed class MessageContentBlock {
    
    /**
     * Text content block that may contain inline markdown formatting
     * (bold, italic, links, etc.) but no code blocks.
     */
    data class Text(
        val text: String,
        val hasInlineFormatting: Boolean = true
    ) : MessageContentBlock()
    
    /**
     * Code block with syntax highlighting support and interactive features.
     */
    data class CodeBlock(
        val code: String,
        val language: String = "text",
        val filename: String? = null
    ) : MessageContentBlock()
    
    /**
     * Image block for embedded images.
     */
    data class Image(
        val url: String,
        val altText: String? = null,
        val title: String? = null
    ) : MessageContentBlock()
    
    /**
     * Table block for markdown tables.
     */
    data class Table(
        val rows: kotlin.collections.List<kotlin.collections.List<String>>,
        val headers: kotlin.collections.List<String>? = null,
        val alignment: kotlin.collections.List<TableAlignment>? = null
    ) : MessageContentBlock()
    
    /**
     * Quote block for blockquotes.
     */
    data class Quote(
        val text: String,
        val level: Int = 1
    ) : MessageContentBlock()
    
    /**
     * List block for ordered and unordered lists.
     */
    data class List(
        val items: kotlin.collections.List<ListItem>,
        val isOrdered: Boolean = false
    ) : MessageContentBlock()
    
    /**
     * Math block for LaTeX mathematical expressions.
     */
    data class Math(
        val latex: String,
        val isInline: Boolean = false
    ) : MessageContentBlock()
    
    /**
     * Mermaid diagram block.
     */
    data class Mermaid(
        val diagram: String,
        val type: String = "flowchart"
    ) : MessageContentBlock()
    
    /**
     * Horizontal rule/separator.
     */
    object HorizontalRule : MessageContentBlock()
    
    /**
     * Citation/reference block for web search sources.
     */
    data class Citation(
        val number: Int,
        val title: String,
        val url: String,
        val shortDisplayName: String = ""
    ) : MessageContentBlock()
    
    /**
     * Custom HTML block (with security restrictions).
     */
    data class Html(
        val html: String,
        val isSafe: Boolean = false
    ) : MessageContentBlock()
    
    /**
     * Emoji block for emoji rendering.
     */
    data class Emoji(
        val shortcode: String,
        val unicode: String,
        val description: String? = null
    ) : MessageContentBlock()
    
    /**
     * Footnote reference block.
     */
    data class FootnoteRef(
        val id: String,
        val label: String
    ) : MessageContentBlock()
    
    /**
     * Footnote definition block.
     */
    data class FootnoteDef(
        val id: String,
        val content: String
    ) : MessageContentBlock()
    
    /**
     * Admonition/Callout block (Note, Warning, etc.).
     */
    data class Admonition(
        val type: AdmonitionType,
        val title: String? = null,
        val content: String,
        val isCollapsible: Boolean = false,
        val isCollapsed: Boolean = false
    ) : MessageContentBlock()
    
    /**
     * Subscript text block.
     */
    data class Subscript(
        val text: String
    ) : MessageContentBlock()
    
    /**
     * Superscript text block.
     */
    data class Superscript(
        val text: String
    ) : MessageContentBlock()
    
    /**
     * Definition list block.
     */
    data class DefinitionList(
        val items: kotlin.collections.List<DefinitionItem>
    ) : MessageContentBlock()
    
    /**
     * Keyboard key block.
     */
    data class KeyboardKey(
        val keys: kotlin.collections.List<String>
    ) : MessageContentBlock()
    
    /**
     * Highlighted/marked text block.
     */
    data class Highlight(
        val text: String,
        val color: String? = null
    ) : MessageContentBlock()
    
    /**
     * User mention block.
     */
    data class Mention(
        val username: String,
        val displayName: String? = null,
        val url: String? = null
    ) : MessageContentBlock()
    
    /**
     * Issue reference block (GitHub-style).
     */
    data class IssueRef(
        val number: String,
        val title: String? = null,
        val url: String? = null,
        val status: IssueStatus? = null
    ) : MessageContentBlock()
    
    /**
     * Abbreviation block with definition.
     */
    data class Abbreviation(
        val shortForm: String,
        val definition: String
    ) : MessageContentBlock()
    
    /**
     * Video embed block.
     */
    data class VideoEmbed(
        val url: String,
        val provider: VideoProvider,
        val title: String? = null,
        val thumbnail: String? = null,
        val duration: String? = null
    ) : MessageContentBlock()
    
    /**
     * Spoiler text block.
     */
    data class Spoiler(
        val text: String,
        val reason: String? = null
    ) : MessageContentBlock()
}

/**
 * Table alignment options.
 */
enum class TableAlignment {
    LEFT, CENTER, RIGHT, NONE
}

/**
 * List item representation.
 */
data class ListItem(
    val text: String,
    val isChecked: Boolean? = null, // For task lists
    val level: Int = 0,
    val subItems: kotlin.collections.List<ListItem> = emptyList()
)

/**
 * Result wrapper for parsing operations.
 */
sealed class ParseResult<T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Error<T>(val message: String, val fallback: T? = null) : ParseResult<T>()
}

/**
 * Configuration for markdown parsing and rendering.
 */
data class MarkdownConfig(
    // Core features
    val enableSyntaxHighlighting: Boolean = true,
    val enableMath: Boolean = true,
    val enableTables: Boolean = true,
    val enableTaskLists: Boolean = true,
    val enableImages: Boolean = true,
    val enableLinks: Boolean = true,
    val enableQuotes: Boolean = true,
    val enableMermaid: Boolean = true,
    
    // NEW: Extended features
    val enableEmoji: Boolean = true,
    val enableFootnotes: Boolean = true,
    val enableAdmonitions: Boolean = true,
    val enableSubscriptSuperscript: Boolean = true,
    val enableDefinitionLists: Boolean = true,
    val enableKeyboardKeys: Boolean = true,
    val enableHighlighting: Boolean = true,
    val enableMentions: Boolean = true,
    val enableIssueReferences: Boolean = true,
    val enableAbbreviations: Boolean = true,
    val enableVideoEmbeds: Boolean = true,
    val enableSpoilers: Boolean = true,
    
    // Limits and security
    val maxCodeBlockLength: Int = 10_000,
    val maxImageSize: Long = 5 * 1024 * 1024, // 5MB
    val maxVideoEmbeds: Int = 5, // Limit video embeds per message
    val isDarkMode: Boolean = false,
    val securityMode: SecurityMode = SecurityMode.STRICT,
    
    // Customization options
    val mentionUrlPattern: String? = null, // e.g., "https://github.com/{username}"
    val issueUrlPattern: String? = null,   // e.g., "https://github.com/owner/repo/issues/{number}"
    val customEmojiProvider: String? = null // Custom emoji provider URL
)

/**
 * Security modes for HTML and link handling.
 */
enum class SecurityMode {
    STRICT,     // Block all unsafe content
    MODERATE,   // Allow some trusted content
    PERMISSIVE  // Allow most content (not recommended)
}

/**
 * Definition list item.
 */
data class DefinitionItem(
    val term: String,
    val definitions: kotlin.collections.List<String>
)

/**
 * Admonition/Callout types with styling.
 */
enum class AdmonitionType(val displayName: String, val icon: String) {
    NOTE("Note", "ℹ️"),
    TIP("Tip", "💡"),
    WARNING("Warning", "⚠️"),
    DANGER("Danger", "🚫"),
    BUG("Bug", "🐛"),
    EXAMPLE("Example", "📋"),
    QUOTE("Quote", "💬"),
    SUCCESS("Success", "✅"),
    FAILURE("Failure", "❌"),
    QUESTION("Question", "❓"),
    INFO("Info", "📘"),
    ABSTRACT("Abstract", "📝"),
    TODO("Todo", "✏️"),
    CUSTOM("Custom", "📌")
}

/**
 * Issue/PR status for GitHub-style references.
 */
enum class IssueStatus {
    OPEN, CLOSED, MERGED, DRAFT
}

/**
 * Video providers for embed support.
 */
enum class VideoProvider(val displayName: String, val domains: kotlin.collections.List<String>) {
    YOUTUBE("YouTube", listOf("youtube.com", "youtu.be", "www.youtube.com")),
    VIMEO("Vimeo", listOf("vimeo.com", "www.vimeo.com")),
    TWITCH("Twitch", listOf("twitch.tv", "www.twitch.tv")),
    LOOM("Loom", listOf("loom.com", "www.loom.com")),
    WISTIA("Wistia", listOf("wistia.com", "fast.wistia.com")),
    GENERIC("Generic", emptyList())
}