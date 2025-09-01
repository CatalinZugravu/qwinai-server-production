package com.cyberflux.qwinai.utils

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.LruCache
import android.util.Patterns
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.cyberflux.qwinai.CodeViewerActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.MarkdownConfig
import com.cyberflux.qwinai.model.MessageContentBlock
import com.cyberflux.qwinai.model.ParseResult
import com.cyberflux.qwinai.model.SecurityMode
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import timber.log.Timber
import java.security.MessageDigest
import java.util.regex.Pattern
import androidx.core.net.toUri
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.IndentedCodeBlock

/**
 * PRODUCTION-READY MARKDOWN PROCESSOR
 *
 * Block-based architecture that parses markdown into ordered content blocks
 * and renders them inline using proper View layouts for interactive elements.
 *
 * Key Features:
 * - Block-based parsing: Text and CodeBlock types maintain original order
 * - Interactive code blocks: Real View layouts with working copy buttons
 * - Security-first: HTML sanitization, URL validation, XSS prevention
 * - Performance-optimized: LRU caching, efficient parsing, memory management
 * - Thread-safe: Proper coroutine usage, no singleton antipattern
 * - Lifecycle-aware: Automatic cleanup and resource management
 * - Error recovery: Graceful fallbacks and comprehensive error handling
 */
class UnifiedMarkdownProcessor(
    private val context: Context,
    private val lifecycle: Lifecycle? = null,
    private val config: MarkdownConfig = MarkdownConfig()
) : DefaultLifecycleObserver {
    companion object {
        private const val CACHE_SIZE = 200
        private const val MAX_CONTENT_LENGTH = 100_000
        private const val CACHE_CLEANUP_THRESHOLD = 250
        private val CITATION_PATTERN = Pattern.compile("\\[(\\d+)]")
        private val UNSAFE_HTML_PATTERN = Pattern.compile(
            "<script[^>]*>[\\s\\S]*?</script>|javascript:|data:|vbscript:",
            Pattern.CASE_INSENSITIVE
        )
        private val SAFE_PROTOCOLS = setOf("http", "https", "mailto", "ftp")
        // Programming languages that should get interactive code blocks
        private val PROGRAMMING_LANGUAGES = setOf(
            // Web Technologies
            "javascript", "js", "typescript", "ts", "html", "css", "scss", "sass", "less",
            "json", "xml", "yaml", "yml", "toml", "ini", "php",
            // Systems Programming
            "c", "cpp", "c++", "cxx", "cc", "h", "hpp", "hxx", "rust", "rs", "go", "zig",
            "assembly", "asm", "nasm", "masm",
            // High-Level Languages
            "python", "py", "java", "kotlin", "kt", "scala", "clojure", "clj", "groovy",
            "csharp", "cs", "c#", "fsharp", "fs", "f#", "vb", "vbnet",
            // Functional Languages
            "haskell", "hs", "elm", "erlang", "erl", "elixir", "ex", "ocaml", "ml", "lisp",
            "scheme", "racket",
            // Mobile Development
            "swift", "objc", "objectivec", "dart", "flutter",
            // Data & Analytics
            "r", "matlab", "octave", "julia", "jl", "sql", "postgresql", "mysql", "sqlite",
            "nosql", "mongodb", "redis",
            // Shell & Scripting
            "bash", "sh", "zsh", "fish", "powershell", "ps1", "batch", "cmd", "perl", "pl",
            "ruby", "rb", "lua",
            // Build & Config
            "dockerfile", "docker", "makefile", "make", "cmake", "gradle", "maven",
            "ant", "sbt", "npm", "yarn", "pip",
            // Infrastructure
            "terraform", "tf", "hcl", "ansible", "vagrant", "puppet", "chef",
            "kubernetes", "k8s", "helm",
            // Game Development
            "gdscript", "unity", "unreal", "hlsl", "glsl", "shader",
            // Others
            "latex", "tex", "bibtex", "prolog", "cobol", "fortran", "pascal", "ada",
            "smalltalk", "forth", "tcl", "awk", "sed", "regex", "regexp",
            // Markup that should be treated as code
            "markdown", "md", "rst", "asciidoc", "textile"
        )
        // Non-programming content that should be rendered as text
        private val TEXT_CONTENT_TYPES = setOf(
            "text", "txt", "plain", "output", "console", "log", "diff", "patch",
            "example", "sample", "demo", "snippet", "quote", "citation",
            "description", "note", "comment", "explanation", "summary",
            "", "null" // Empty or unspecified language
        )
        /**
         * Check if a language should be rendered as an interactive code block.
         */
        fun isProgrammingLanguage(language: String?): Boolean {
            if (language.isNullOrBlank()) return false
            val normalizedLang = language.trim().lowercase()
            return normalizedLang in PROGRAMMING_LANGUAGES && normalizedLang !in TEXT_CONTENT_TYPES
        }
        // Factory method for dependency injection
        fun create(context: Context, lifecycle: Lifecycle? = null): UnifiedMarkdownProcessor {
            return UnifiedMarkdownProcessor(context.applicationContext, lifecycle)
        }
    }
    // Web search source for citations (legacy compatibility)
    data class WebSearchSource(
        val title: String,
        val url: String,
        val shortDisplayName: String = ""
    )
    // Performance-optimized caches with lifecycle awareness
    private val blockCache = LruCache<String, List<MessageContentBlock>>(CACHE_SIZE)
    private val renderCache = LruCache<String, Boolean>(CACHE_SIZE / 2)
    // Anti-flickering state tracking (per container)
    private val containerStates = mutableMapOf<LinearLayout, ContainerRenderState>()
    // Container render state to prevent flickering during streaming
    private data class ContainerRenderState(
        var lastRenderedContent: String = "",
        var lastRenderedBlocks: List<MessageContentBlock> = emptyList(),
        var renderedViewCount: Int = 0,
        var lastRenderTime: Long = 0L
    )
    // Thread-safe execution context
    private val processingScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob()
    )
    // Markwon parser for text blocks only
    private val textParser: Markwon by lazy { createTextParser() }
    private val commonMarkParser: Parser by lazy { createCommonMarkParser() }
    // Security manager for safe content handling
    private val securityManager = SecurityManager()
    init {
        lifecycle?.addObserver(this)
        Timber.d("✅ UnifiedMarkdownProcessor initialized with security mode: ${config.securityMode}")
    }
    /**
     * Main rendering method using block-based architecture with anti-flickering.
     * Parses markdown into ordered blocks and renders them inline in the container.
     * Uses incremental rendering during streaming to prevent flickering.
     */
    fun renderToContainer(
        content: String,
        container: LinearLayout,
        webSearchSources: List<WebSearchSource>? = null,
        isStreaming: Boolean = false
    ): ParseResult<Int> {
        return try {
            if (content.isBlank()) {
                clearContainer(container)
                container.visibility = View.GONE
                return ParseResult.Success(0)
            }
            container.visibility = View.VISIBLE
            // Get or create container state for anti-flickering
            val containerState = containerStates.getOrPut(container) { ContainerRenderState() }
            // REAL-TIME OPTIMIZATION: Check if content has changed significantly to avoid unnecessary re-renders
            if (isStreaming && shouldSkipRender(content, containerState, isStreaming)) {
                return ParseResult.Success(containerState.renderedViewCount)
            }
            // REAL-TIME PARSING: Parse content into ordered blocks with streaming optimizations
            val parseResult = parseIntoBlocks(content, webSearchSources, isStreaming)
            when (parseResult) {
                is ParseResult.Success -> {
                    val blocksRendered = if (isStreaming) {
                        renderBlocksIncremental(parseResult.data, container, containerState)
                    } else {
                        renderBlocksComplete(parseResult.data, container)
                    }
                    // Update container state
                    containerState.lastRenderedContent = content
                    containerState.lastRenderedBlocks = parseResult.data
                    containerState.renderedViewCount = blocksRendered
                    containerState.lastRenderTime = System.currentTimeMillis()
                    ParseResult.Success(blocksRendered)
                }
                is ParseResult.Error -> {
                    Timber.e("Parse error: ${parseResult.message}")
                    parseResult.fallback?.let { fallbackBlocks ->
                        val blocksRendered = if (isStreaming) {
                            renderBlocksIncremental(fallbackBlocks, container, containerState)
                        } else {
                            renderBlocksComplete(fallbackBlocks, container)
                        }
                        ParseResult.Success(blocksRendered)
                    } ?: run {
                        renderFallbackText(content, container)
                        ParseResult.Success(1)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Critical error in renderToContainer")
            renderFallbackText(content, container)
            ParseResult.Error(e.message ?: "Unknown error", null)
        }
    }
    /**
     * Legacy compatibility methods
     */
    fun renderSimple(
        content: String,
        container: LinearLayout,
        webSearchSources: List<WebSearchSource>? = null
    ) {
        renderToContainer(content, container, webSearchSources, false)
    }
    fun renderStreaming(
        content: String,
        container: LinearLayout,
        webSearchSources: List<WebSearchSource>? = null
    ) {
        renderToContainer(content, container, webSearchSources, true)
    }
    /**
     * REAL-TIME STREAMING: Parse markdown content into ordered content blocks with streaming optimizations
     */
    private fun parseIntoBlocks(
        content: String,
        webSearchSources: List<WebSearchSource>?,
        isStreaming: Boolean = false
    ): ParseResult<List<MessageContentBlock>> {
        return try {
            // Security validation
            if (content.length > MAX_CONTENT_LENGTH) {
                return ParseResult.Error(
                    "Content too large: ${content.length} chars",
                    listOf(MessageContentBlock.Text(content.take(MAX_CONTENT_LENGTH)))
                )
            }
            // STREAMING OPTIMIZATION: Smart caching strategy for streaming vs complete content
            val cacheKey = generateCacheKey(content, webSearchSources)

            if (!isStreaming) {
                // For non-streaming content, use full cache lookup
                blockCache.get(cacheKey)?.let { cached ->
                    return ParseResult.Success(cached)
                }
            } else {
                // STREAMING CACHE: Look for similar content patterns to speed up incremental parsing
                // For streaming, we're more conservative with cache usage to ensure real-time updates
                if (content.length > 1000) { // Only cache larger streaming content
                    blockCache.get(cacheKey)?.let { cached ->
                        return ParseResult.Success(cached)
                    }
                }
            }
            // Preprocess content
            var processedContent = preprocessContent(content, webSearchSources)
            val allBlocks = mutableListOf<MessageContentBlock>()
            // Parse remaining content with CommonMark AST
            val document = commonMarkParser.parse(processedContent)
            var currentTextBuilder = StringBuilder()
            document.accept(object : AbstractVisitor() {
                override fun visit(fencedCodeBlock: FencedCodeBlock) {
                    // Add accumulated text before code block
                    if (currentTextBuilder.isNotEmpty()) {
                        allBlocks.add(MessageContentBlock.Text(
                            currentTextBuilder.toString().trim()
                        ))
                        currentTextBuilder.clear()
                    }
                    // Add code block
                    val language = fencedCodeBlock.info ?: "text"
                    val code = fencedCodeBlock.literal ?: ""
                    if (code.isNotBlank()) {
                        allBlocks.add(MessageContentBlock.CodeBlock(
                            code = code.trim(),
                            language = language.trim()
                        ))
                    }
                }
                override fun visit(paragraph: Paragraph) {
                    // Accumulate paragraph text
                    val text = extractTextFromNode(paragraph)
                    if (text.isNotBlank()) {
                        if (currentTextBuilder.isNotEmpty()) {
                            currentTextBuilder.append("\n\n")
                        }
                        currentTextBuilder.append(text)
                    }
                }
                override fun visit(heading: Heading) {
                    val text = extractTextFromNode(heading)
                    if (text.isNotBlank()) {
                        if (currentTextBuilder.isNotEmpty()) {
                            currentTextBuilder.append("\n\n")
                        }
                        val prefix = "#".repeat(heading.level) + " "
                        currentTextBuilder.append(prefix + text)
                    }
                }
                override fun visit(bulletList: BulletList) {
                    if (currentTextBuilder.isNotEmpty()) {
                        currentTextBuilder.append("\n\n")
                    }
                    var item = bulletList.firstChild
                    while (item != null) {
                        if (item is ListItem) {
                            val itemText = extractTextFromNode(item).trim()
                            if (itemText.isNotBlank()) {
                                currentTextBuilder.append("- ")
                                currentTextBuilder.append(itemText.replace("\n", "\n  "))
                                currentTextBuilder.append("\n")
                            }
                        }
                        item = item.next
                    }
                }
                override fun visit(orderedList: OrderedList) {
                    if (currentTextBuilder.isNotEmpty()) {
                        currentTextBuilder.append("\n\n")
                    }
                    var num = orderedList.startNumber
                    var item = orderedList.firstChild
                    while (item != null) {
                        if (item is ListItem) {
                            val itemText = extractTextFromNode(item).trim()
                            if (itemText.isNotBlank()) {
                                currentTextBuilder.append("$num. ")
                                currentTextBuilder.append(itemText.replace("\n", "\n   "))
                                currentTextBuilder.append("\n")
                            }
                        }
                        num++
                        item = item.next
                    }
                }
                override fun visit(blockQuote: BlockQuote) {
                    val text = extractTextFromNode(blockQuote).trim()
                    if (text.isNotBlank()) {
                        if (currentTextBuilder.isNotEmpty()) {
                            currentTextBuilder.append("\n\n")
                        }
                        currentTextBuilder.append(text.lines().joinToString("\n") { "> $it" })
                    }
                }
                override fun visit(thematicBreak: ThematicBreak) {
                    // Add accumulated text before horizontal rule
                    if (currentTextBuilder.isNotEmpty()) {
                        allBlocks.add(MessageContentBlock.Text(
                            currentTextBuilder.toString().trim()
                        ))
                        currentTextBuilder.clear()
                    }
                    allBlocks.add(MessageContentBlock.HorizontalRule)
                }
            })
            // Add any remaining text
            if (currentTextBuilder.isNotEmpty()) {
                allBlocks.add(MessageContentBlock.Text(
                    currentTextBuilder.toString().trim()
                ))
            }
            // Handle edge case: no blocks parsed, add as text
            if (allBlocks.isEmpty() && processedContent.isNotBlank()) {
                allBlocks.add(MessageContentBlock.Text(processedContent.trim()))
            }
            // STREAMING CACHE: Smart caching strategy - only cache completed/stable content
            if (!isStreaming || content.length > 2000) {
                // Cache completed content or larger streaming content for performance
                blockCache.put(cacheKey, allBlocks)
                cleanupCacheIfNeeded()
            }
            Timber.d("✅ Parsed content into ${allBlocks.size} blocks")
            ParseResult.Success(allBlocks)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing content into blocks")
            ParseResult.Error(
                e.message ?: "Unknown parsing error",
                listOf(MessageContentBlock.Text(content))
            )
        }
    }
    /**
     * Render parsed blocks with complete refresh (non-streaming).
     */
    private fun renderBlocksComplete(
        blocks: List<MessageContentBlock>,
        container: LinearLayout
    ): Int {
        container.removeAllViews()
        blocks.forEach { block ->
            renderSingleBlock(block, container)
        }
        Timber.d("✅ Complete render: ${blocks.size} blocks into container")
        return blocks.size
    }
    /**
     * Render blocks incrementally to prevent flickering during streaming.
     * Only adds new content, updates last if necessary, doesn't rebuild existing content.
     */
    private fun renderBlocksIncremental(
        newBlocks: List<MessageContentBlock>,
        container: LinearLayout,
        containerState: ContainerRenderState
    ): Int {
        val previousBlocks = containerState.lastRenderedBlocks
        // If structure changed significantly, fallback to complete render
        if (newBlocks.size < previousBlocks.size || !areBlocksPrefix(previousBlocks, newBlocks)) {
            return renderBlocksComplete(newBlocks, container)
        }
        // Update the last block if it has changed (common in streaming)
        if (previousBlocks.isNotEmpty() && newBlocks.size == previousBlocks.size) {
            val lastIndex = previousBlocks.size - 1
            if (newBlocks[lastIndex] != previousBlocks[lastIndex]) {
                val lastView = container.getChildAt(lastIndex)
                if (lastView != null) {
                    updateViewWithBlock(lastView, newBlocks[lastIndex])
                }
            }
        }
        // Add any new blocks
        val blocksToAdd = newBlocks.drop(previousBlocks.size)
        blocksToAdd.forEach { block ->
            renderSingleBlock(block, container)
        }
        if (blocksToAdd.isNotEmpty()) {
            Timber.d("✅ Incremental render: ${blocksToAdd.size} new blocks added (${newBlocks.size} total)")
        }
        return newBlocks.size
    }
    /**
     * Check if previous blocks are a prefix of new blocks (no major structural changes).
     */
    private fun areBlocksPrefix(previous: List<MessageContentBlock>, new: List<MessageContentBlock>): Boolean {
        if (previous.size > new.size) return false
        return previous == new.take(previous.size)
    }
    /**
     * Update an existing view with new block content (for streaming updates).
     */
    private fun updateViewWithBlock(view: View, block: MessageContentBlock) {
        when (block) {
            is MessageContentBlock.Text -> {
                if (view is TextView) {
                    textParser.setMarkdown(view, block.text)
                }
            }
            is MessageContentBlock.CodeBlock -> {
                if (view is LinearLayout) {
                    val tvCodeContent = view.findViewById<TextView>(R.id.tvCodeContent)
                    tvCodeContent?.let {
                        val highlightedCode = if (config.enableSyntaxHighlighting) {
                            CodeSyntaxHighlighter.highlight(context, block.code, block.language)
                        } else {
                            block.code
                        }
                        it.text = highlightedCode
                    }
                }
            }
            else -> {
                // For other types, no update - fallback to complete render if needed
            }
        }
    }
    /**
     * REAL-TIME STREAMING: Ultra-fast rendering decision for fluid streaming experience
     */
    private fun shouldSkipRender(content: String, containerState: ContainerRenderState, isStreaming: Boolean = false): Boolean {
        // Never skip if this is the first render
        if (containerState.lastRenderedContent.isEmpty()) {
            return false
        }

        // Skip if content hasn't changed
        if (content == containerState.lastRenderedContent) {
            return true
        }
        // STREAMING OPTIMIZATION: More aggressive rate limiting during streaming
        val timeSinceLastRender = System.currentTimeMillis() - containerState.lastRenderTime
        val minInterval = if (isStreaming) {
            // 60 FPS target for streaming (16ms per frame)
            when {
                content.length < 500 -> 8L   // Ultra-fast for small content
                content.length < 2000 -> 16L // Standard 60 FPS
                content.length < 10000 -> 25L // Slightly slower for larger content
                else -> 50L // Conservative for very large content
            }
        } else {
            50L // Non-streaming default
        }

        if (timeSinceLastRender < minInterval) {
            return true
        }
        return false
    }
    /**
     * Render a single block into the container.
     */
    private fun renderSingleBlock(block: MessageContentBlock, container: LinearLayout) {
        when (block) {
            is MessageContentBlock.Text -> {
                renderTextBlock(block, container)
            }
            is MessageContentBlock.CodeBlock -> {
                // Only render as interactive code block for programming languages
                if (isProgrammingLanguage(block.language)) {
                    renderCodeBlock(block, container)
                } else {
                    // Render as formatted text for non-programming content
                    renderCodeAsText(block, container)
                }
            }
            is MessageContentBlock.HorizontalRule -> {
                renderHorizontalRule(container)
            }
            else -> {
                // Fallback for unsupported blocks
                renderTextBlock(MessageContentBlock.Text(block.toString()), container)
            }
        }
    }
    /**
     * Clear container and reset state.
     */
    private fun clearContainer(container: LinearLayout) {
        container.removeAllViews()
        containerStates.remove(container)
    }
    /**
     * Render a text block using Markwon for inline formatting.
     */
    private fun renderTextBlock(block: MessageContentBlock.Text, container: LinearLayout) {
        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
        }
        if (block.text.isNotBlank()) {
            // Use Markwon for inline formatting
            textParser.setMarkdown(textView, block.text)
        } else {
            // Plain text
            textView.text = block.text
        }
        setupTextView(textView)
        container.addView(textView)
    }
    /**
     * Render a code block using the item_code_block.xml layout.
     */
    private fun renderCodeBlock(block: MessageContentBlock.CodeBlock, container: LinearLayout) {
        try {
            // Create a themed context to ensure proper attribute resolution
            val themedContext = getThemedContext(container.context)
            val inflater = LayoutInflater.from(themedContext)
            val codeBlockView = inflater.inflate(R.layout.item_code_block, container, false)
            // Set language label
            val tvLanguage = codeBlockView.findViewById<TextView>(R.id.tvLanguage)
            tvLanguage.text = if (block.language.isNotBlank() && block.language != "text") {
                block.language
            } else {
                "code"
            }
            // Set code content with syntax highlighting
            val tvCodeContent = codeBlockView.findViewById<TextView>(R.id.tvCodeContent)
            val highlightedCode = if (config.enableSyntaxHighlighting) {
                CodeSyntaxHighlighter.highlight(context, block.code, block.language)
            } else {
                block.code
            }
            tvCodeContent.text = highlightedCode
            // Set up copy button
            val btnCopyCode = codeBlockView.findViewById<LinearLayout>(R.id.btnCopyCode)
            val copyText = codeBlockView.findViewById<TextView>(R.id.copyText)
            btnCopyCode.setOnClickListener {
                copyToClipboard(block.code, copyText)
            }
            // Set up expand button (optional implementation)
            val btnOpenCode = codeBlockView.findViewById<LinearLayout>(R.id.btnOpenCode)
            btnOpenCode.setOnClickListener {
                openCodeInViewer(block)
            }
            container.addView(codeBlockView)
        } catch (e: Exception) {
            Timber.e(e, "Error rendering code block with layout: ${e.message}")
            // Fallback: create code block programmatically
            createProgrammaticCodeBlock(block, container)
        }
    }
    /**
     * Render code block as formatted text for non-programming languages.
     */
    private fun renderCodeAsText(block: MessageContentBlock.CodeBlock, container: LinearLayout) {
        val context = container.context
        // Create a simple text view with monospace font
        val codeTextView = TextView(context).apply {
            text = block.code
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.code_text))
            typeface = android.graphics.Typeface.MONOSPACE
            // Basic styling
            setPadding(32, 24, 32, 24)
            setBackgroundColor(ContextCompat.getColor(context, R.color.code_background))
            // Make it selectable
            setTextIsSelectable(true)
            // Layout parameters
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
        }
        container.addView(codeTextView)
        Timber.d("✅ Rendered code block as text: ${block.language}")
    }
    /**
     * Create code block programmatically when XML inflation fails.
     */
    @SuppressLint("SetTextI18n")
    private fun createProgrammaticCodeBlock(block: MessageContentBlock.CodeBlock, container: LinearLayout) {
        try {
            val context = container.context
            // Create main container
            val codeBlockContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                orientation = LinearLayout.VERTICAL
                setPadding(24, 16, 24, 16)
                setBackgroundColor(ContextCompat.getColor(context, R.color.code_background))
            }
            // Create header with language and copy button
            val headerLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            // Language label
            val languageText = TextView(context).apply {
                text = if (block.language.isNotBlank() && block.language != "text") {
                    block.language
                } else {
                    "code"
                }
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.code_text))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            // Spacer
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
            }
            // Copy button
            val copyButton = TextView(context).apply {
                text = "Copy"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.link_color))
                setPadding(16, 8, 16, 8)
                background = ContextCompat.getDrawable(context, android.R.drawable.btn_default)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    copyToClipboard(block.code, this)
                }
            }
            headerLayout.addView(languageText)
            headerLayout.addView(spacer)
            headerLayout.addView(copyButton)
            // Code content
            val codeText = TextView(context).apply {
                text = if (config.enableSyntaxHighlighting) {
                    CodeSyntaxHighlighter.highlight(context, block.code, block.language)
                } else {
                    block.code
                }
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.code_text))
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 16, 0, 0)
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            codeBlockContainer.addView(headerLayout)
            codeBlockContainer.addView(codeText)
            container.addView(codeBlockContainer)
            Timber.d("✅ Created programmatic code block for ${block.language}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create programmatic code block, falling back to text")
            // Ultimate fallback: render as text
            renderTextBlock(
                MessageContentBlock.Text("```\${block.language}\n\${block.code}\n```"),
                container
            )
        }
    }
    /**
     * Render horizontal rule.
     */
    private fun renderHorizontalRule(container: LinearLayout) {
        try {
            // Create a visual horizontal rule
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply {
                    topMargin = 16
                    bottomMargin = 16
                    leftMargin = 32
                    rightMargin = 32
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            }
            container.addView(divider)
        } catch (e: Exception) {
            Timber.e(e, "Failed to render horizontal rule, falling back to text")
            renderTextBlock(MessageContentBlock.Text("---"), container)
        }
    }
    /**
     * Create text parser for inline markdown formatting.
     */
    private fun createTextParser(): Markwon {
        // adjust to match your TextView size (in sp)
        val latexTextSize = 16f
        val latexExecutor = java.util.concurrent.Executors.newCachedThreadPool()
        return Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(TablePlugin.create(context))
            // required to parse inline math like $$...$$
            .usePlugin(MarkwonInlineParserPlugin.create())
            // LaTeX math rendering (blocks + inline). Adjust executor / error handling as needed.
            .usePlugin(
                JLatexMathPlugin.create(latexTextSize) { builder ->
                    builder.inlinesEnabled(true)               // enable inline $$..$$ rendering
                    builder.executorService(latexExecutor)     // off-main-thread rendering
                    builder.errorHandler { _latex, _throwable -> null } // fallback on error (return Drawable if desired)
                }
            )
            .usePlugin(MovementMethodPlugin.create())
            .usePlugin(createSecureLinkHandler())
            .usePlugin(createThemePlugin())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(FencedCodeBlock::class.java) { visitor, node ->
                        // Skip rendering - we handle code blocks separately
                        visitor.blockStart(node)
                        visitor.blockEnd(node)
                    }
                    builder.on(IndentedCodeBlock::class.java) { visitor, node ->
                        // Skip rendering - we handle code blocks separately
                        visitor.blockStart(node)
                        visitor.blockEnd(node)
                    }
                }
            })
            .build()
    }
    /**
     * Create CommonMark parser for AST-based block parsing.
     */
    private fun createCommonMarkParser(): Parser {
        return Parser.builder()
            .build()
    }
    /**
     * Copy code to clipboard with user feedback.
     */
    @SuppressLint("SetTextI18n")
    private fun copyToClipboard(code: String, feedbackTextView: TextView) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Code", code)
            clipboard.setPrimaryClip(clip)
            // Visual feedback
            val originalText = feedbackTextView.text.toString()
            feedbackTextView.text = "Copied!"
            Handler(Looper.getMainLooper()).postDelayed({
                feedbackTextView.text = originalText
            }, 1500)
            // Optional toast feedback
            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy code to clipboard")
            Toast.makeText(context, "Failed to copy code", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Open code in full-screen viewer.
     */
    private fun openCodeInViewer(block: MessageContentBlock.CodeBlock) {
        try {
            Timber.d("Opening ${block.language} code in full screen viewer - code length: ${block.code.length}")
            
            val intent = CodeViewerActivity.createIntent(
                context = context,
                codeContent = block.code,
                language = block.language.takeIf { it.isNotBlank() } ?: "Code"
            )
            
            // Add FLAG_ACTIVITY_NEW_TASK to ensure it can launch properly
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
            Timber.d("Successfully launched CodeViewerActivity")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open code viewer: ${e.message}")
            // Fallback: copy to clipboard
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Code - ${block.language}", block.code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Failed to open viewer, code copied to clipboard", Toast.LENGTH_SHORT).show()
            } catch (clipboardError: Exception) {
                Timber.e(clipboardError, "Failed to copy to clipboard as fallback")
                Toast.makeText(context, "Failed to open code viewer", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /**
     * Create theme plugin with beautiful ultra-thin styling and colors.
     */
    private fun createThemePlugin(): AbstractMarkwonPlugin {
        return object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    // Beautiful Ultra-Thin Link Colors
                    .linkColor(ContextCompat.getColor(context, R.color.ai_response_link_color))

                    // Elegant Quote Styling
                    .blockQuoteColor(ContextCompat.getColor(context, R.color.ai_response_quote_color))

                    // Sophisticated Heading Sizes with Ultra-Thin Typography
                    .headingTextSizeMultipliers(floatArrayOf(2.0f, 1.75f, 1.5f, 1.3f, 1.15f, 1.0f))

                    // Premium Spacing for Ultra-Thin Appearance
                    .blockMargin(20)
                    .listItemColor(ContextCompat.getColor(context, R.color.ai_response_list_color))

                    // Beautiful Ultra-Thin Text Color
                    .headingBreakHeight(8)
                    .thematicBreakColor(ContextCompat.getColor(context, R.color.ai_response_border_elegant))
                    .thematicBreakHeight(1)

                    // Enhanced Code Block Styling
                    .codeTextColor(ContextCompat.getColor(context, R.color.ai_response_code_color))
                    .codeBackgroundColor(ContextCompat.getColor(context, R.color.ai_response_highlight_subtle))
                    .codeTypeface(android.graphics.Typeface.MONOSPACE)

                    // Ultra-Elegant List Styling
                    .bulletListItemStrokeWidth(2)
                    .bulletWidth(6)
            }
        }
    }
    /**
     * Create secure link handler that validates URLs.
     */
    private fun createSecureLinkHandler(): AbstractMarkwonPlugin {
        return object : AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    handleLinkSecurely(link)
                }
            }
        }
    }
    /**
     * Handle links with security validation.
     */
    private fun handleLinkSecurely(link: String) {
        if (!securityManager.isUrlSafe(link)) {
            Timber.w("Blocked unsafe URL: $link")
            Toast.makeText(context, "Unsafe link blocked", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, link.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open link: $link")
            Toast.makeText(context, "Failed to open link", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Extract text content from a CommonMark node.
     */
    private fun extractTextFromNode(node: Node): String {
        val builder = StringBuilder()
        fun visitNode(current: Node) {
            when (current) {
                is Text -> builder.append(current.literal)
                is SoftLineBreak -> builder.append(" ")
                is HardLineBreak -> builder.append("\n")
                is Code -> builder.append("`${current.literal}`")
                is Emphasis -> {
                    builder.append("*")
                    current.firstChild?.let { visitNode(it) }
                    builder.append("*")
                }
                is StrongEmphasis -> {
                    builder.append("**")
                    current.firstChild?.let { visitNode(it) }
                    builder.append("**")
                }
                is Link -> {
                    builder.append("[")
                    current.firstChild?.let { visitNode(it) }
                    builder.append("](${current.destination})")
                }
                else -> {
                    // Visit all children for other node types
                    var child = current.firstChild
                    while (child != null) {
                        visitNode(child)
                        child = child.next
                    }
                }
            }
        }
        visitNode(node)
        return builder.toString()
    }
    /**
     * Get a themed context for proper attribute resolution.
     */
    private fun getThemedContext(baseContext: Context): Context {
        return try {
            // Try to get the app theme from the application context
            val packageManager = baseContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(
                baseContext.packageName,
                0
            )
            // Use the application theme or fallback to Material theme
            val themeResId = if (applicationInfo.theme != 0) {
                applicationInfo.theme
            } else {
                // Fallback to a compatible Material theme
                com.google.android.material.R.style.Theme_Material3_DayNight
            }
            ContextThemeWrapper(baseContext, themeResId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to create themed context, using base context")
            // Fallback to base context if theming fails
            baseContext
        }
    }
    /**
     * Security manager for safe content handling.
     */
    private inner class SecurityManager {
        fun isUrlSafe(url: String): Boolean {
            if (config.securityMode == SecurityMode.PERMISSIVE) {
                return true
            }
            return try {
                val uri = url.toUri()
                val scheme = uri.scheme?.lowercase()
                when (config.securityMode) {
                    SecurityMode.STRICT -> {
                        scheme in SAFE_PROTOCOLS &&
                                !url.contains("javascript:", ignoreCase = true) &&
                                !url.contains("data:", ignoreCase = true) &&
                                !url.startsWith("file://") &&
                                Patterns.WEB_URL.matcher(url).matches()
                    }
                    SecurityMode.MODERATE -> {
                        scheme in SAFE_PROTOCOLS &&
                                !url.contains("javascript:", ignoreCase = true)
                    }
                    SecurityMode.PERMISSIVE -> true
                }
            } catch (e: Exception) {
                Timber.w(e, "Invalid URL: $url")
                false
            }
        }
        fun sanitizeHtml(html: String): String {
            return if (config.securityMode == SecurityMode.STRICT) {
                // Remove all potentially dangerous HTML
                UNSAFE_HTML_PATTERN.matcher(html).replaceAll("")
            } else {
                html
            }
        }
    }
    /**
     * Preprocess content before parsing.
     */
    private fun preprocessContent(
        content: String,
        webSearchSources: List<WebSearchSource>?
    ): String {
        var processed = content
        // Handle citations
        if (!webSearchSources.isNullOrEmpty()) {
            processed = processCitations(processed, webSearchSources)
        }
        // Normalize line endings
        processed = processed.replace("\r\n", "\n").replace("\r", "\n")
        // Security sanitization
        processed = securityManager.sanitizeHtml(processed)
        return processed
    }
    /**
     * Process citations in content.
     */
    private fun processCitations(
        content: String,
        sources: List<WebSearchSource>
    ): String {
        val result = StringBuilder()
        val matcher = CITATION_PATTERN.matcher(content)
        var lastEnd = 0
        while (matcher.find()) {
            result.append(content.substring(lastEnd, matcher.start()))
            val citationNumber = matcher.group(1)?.toIntOrNull() ?: continue
            val sourceIndex = citationNumber - 1
            if (sourceIndex in sources.indices) {
                val source = sources[sourceIndex]
                result.append("[${source.shortDisplayName.ifEmpty { citationNumber.toString() }}](${source.url})")
            } else {
                result.append(matcher.group())
            }
            lastEnd = matcher.end()
        }
        result.append(content.substring(lastEnd))
        // Add reference section
        if (sources.isNotEmpty()) {
            result.append("\n\n---\n### References\n")
            sources.forEachIndexed { index, source ->
                result.append("${index + 1}. [${source.title}](${source.url})\n")
            }
        }
        return result.toString()
    }
    /**
     * Render fallback text when parsing fails.
     */
    private fun renderFallbackText(content: String, container: LinearLayout) {
        container.removeAllViews()
        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = content
        }
        setupTextView(textView)
        container.addView(textView)
    }
    /**
     * Setup TextView with beautiful ultra-thin AI message styling.
     */
    private fun setupTextView(textView: TextView) {
        textView.apply {
            movementMethod = LinkMovementMethod.getInstance()

            // ULTRA-THIN BEAUTIFUL AI RESPONSE STYLING
            textSize = 16f

            // Ultra-thin typography for elegant appearance
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)

            // Beautiful sophisticated colors
            setTextColor(ContextCompat.getColor(context, R.color.ai_response_text_primary))

            // Enhanced line spacing for ultra-thin readability
            setLineSpacing(6f, 1.3f) // Increased spacing for elegant appearance

            // Sophisticated letter spacing for premium feel
            letterSpacing = 0.02f

            // Ultra-elegant alpha for refined appearance
            alpha = 0.95f

            // Premium padding for ultra-thin design
            setPadding(
                paddingLeft + 4,
                paddingTop + 2,
                paddingRight + 4,
                paddingBottom + 2
            )

            // Enhanced text selection and readability
            setTextIsSelectable(true)

            // Beautiful link colors
            setLinkTextColor(ContextCompat.getColor(context, R.color.ai_response_link_color))

            // Ultra-smooth text rendering
            // Enable beautiful text rendering on modern devices
            paintFlags = paintFlags or android.graphics.Paint.ANTI_ALIAS_FLAG
            paintFlags = paintFlags or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
        }
    }
    /**
     * Generate cache key for content and options.
     */
    private fun generateCacheKey(
        content: String,
        webSearchSources: List<WebSearchSource>?
    ): String {
        val sourceHash = webSearchSources?.joinToString { it.url }.hashCode()
        val configHash = config.hashCode()
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = "$content|$sourceHash|$configHash"
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate SHA-256 hash, using fallback")
            "${content.hashCode()}_${sourceHash}_$configHash"
        }
    }
    /**
     * Clean up cache if it exceeds threshold.
     */
    private fun cleanupCacheIfNeeded() {
        if (blockCache.size() > CACHE_CLEANUP_THRESHOLD) {
            blockCache.trimToSize(CACHE_SIZE)
            Timber.d("Cache cleaned up, new size: ${blockCache.size()}")
        }
    }
    // ==== Lifecycle Management ====
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
        super.onDestroy(owner)
    }
    /**
     * Clear all caches.
     */
    fun clearCache() {
        blockCache.evictAll()
        renderCache.evictAll()
        Timber.d("All caches cleared")
    }
    /**
     * Cleanup resources and cancel any ongoing operations.
     */
    fun cleanup() {
        processingScope.cancel()
        clearCache()
        containerStates.clear() // Prevent memory leaks from container references
        lifecycle?.removeObserver(this)
        Timber.d("UnifiedMarkdownProcessor cleaned up")
    }
}
