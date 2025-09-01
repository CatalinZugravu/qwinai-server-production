package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.model.MessageContentBlock
import java.util.regex.Pattern
import timber.log.Timber

/**
 * Comprehensive footnote processing system supporting:
 * - Footnote references: [^1], [^note], [^long-identifier]
 * - Footnote definitions: [^1]: This is a footnote
 * - Multi-line footnote content
 * - Automatic numbering and linking
 * - Accessibility support
 */
class FootnoteProcessor(
    private val context: Context
) {
    
    companion object {
        // Pattern for footnote references: [^id]
        private val FOOTNOTE_REF_PATTERN = Pattern.compile("\\[\\^([a-zA-Z0-9_-]+)]")
        
        // Pattern for footnote definitions: [^id]: content
        private val FOOTNOTE_DEF_PATTERN = Pattern.compile(
            "^\\[\\^([a-zA-Z0-9_-]+)]:\\s*(.+)$",
            Pattern.MULTILINE
        )
        
        // Pattern for multi-line footnote continuation (4+ spaces indentation)
        private val FOOTNOTE_CONTINUATION_PATTERN = Pattern.compile(
            "^    (.+)$",
            Pattern.MULTILINE
        )
    }
    
    /**
     * Parse content and extract footnote information.
     */
    fun processFootnotes(content: String): FootnoteResult {
        if (content.isBlank()) {
            return FootnoteResult(content, emptyList(), emptyList())
        }
        
        return try {
            val footnoteDefinitions = extractFootnoteDefinitions(content)
            val processedContent = processFootnoteReferences(content, footnoteDefinitions)
            val footnoteBlocks = createFootnoteBlocks(footnoteDefinitions)
            
            FootnoteResult(
                processedContent = processedContent,
                footnoteReferences = extractFootnoteReferences(content),
                footnoteDefinitions = footnoteBlocks
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process footnotes")
            FootnoteResult(content, emptyList(), emptyList())
        }
    }
    
    /**
     * Extract footnote definitions from content.
     */
    private fun extractFootnoteDefinitions(content: String): Map<String, String> {
        val definitions = mutableMapOf<String, String>()
        val lines = content.lines()
        var i = 0
        
        while (i < lines.size) {
            val line = lines[i]
            val defMatcher = FOOTNOTE_DEF_PATTERN.matcher(line)
            
            if (defMatcher.find()) {
                val id = defMatcher.group(1) ?: continue
                val initialContent = defMatcher.group(2) ?: ""
                val footnoteContent = StringBuilder(initialContent)
                
                // Look for continuation lines (indented with 4+ spaces)
                i++
                while (i < lines.size) {
                    val nextLine = lines[i]
                    val contMatcher = FOOTNOTE_CONTINUATION_PATTERN.matcher(nextLine)
                    
                    if (contMatcher.find()) {
                        footnoteContent.append("\n").append(contMatcher.group(1))
                        i++
                    } else if (nextLine.isBlank()) {
                        footnoteContent.append("\n")
                        i++
                    } else {
                        // End of footnote content
                        break
                    }
                }
                
                definitions[id] = footnoteContent.toString().trim()
                continue
            }
            i++
        }
        
        return definitions
    }
    
    /**
     * Process footnote references in content and add proper linking.
     */
    private fun processFootnoteReferences(
        content: String,
        definitions: Map<String, String>
    ): String {
        return try {
            val matcher = FOOTNOTE_REF_PATTERN.matcher(content)
            val result = StringBuffer()
            var refNumber = 1
            val usedRefs = mutableSetOf<String>()
            
            while (matcher.find()) {
                val id = matcher.group(1) ?: continue
                
                if (definitions.containsKey(id)) {
                    // Create clickable superscript reference
                    val displayNumber = if (usedRefs.add(id)) {
                        refNumber++
                    } else {
                        // Find existing number for this reference
                        usedRefs.indexOf(id) + 1
                    }
                    
                    val replacement = "[$displayNumber](#footnote-$id)"
                    matcher.appendReplacement(result, replacement)
                    
                    Timber.d("✅ Processed footnote reference: [^$id] → [$displayNumber]")
                } else {
                    // Keep original if no definition found
                    matcher.appendReplacement(result, matcher.group())
                    Timber.w("⚠️ Footnote reference [^$id] has no definition")
                }
            }
            
            matcher.appendTail(result)
            result.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to process footnote references")
            content
        }
    }
    
    /**
     * Extract footnote reference information for processing.
     */
    private fun extractFootnoteReferences(content: String): List<MessageContentBlock.FootnoteRef> {
        val references = mutableListOf<MessageContentBlock.FootnoteRef>()
        
        try {
            val matcher = FOOTNOTE_REF_PATTERN.matcher(content)
            var refNumber = 1
            val seenIds = mutableSetOf<String>()
            
            while (matcher.find()) {
                val id = matcher.group(1) ?: continue
                
                if (seenIds.add(id)) {
                    references.add(
                        MessageContentBlock.FootnoteRef(
                            id = id,
                            label = refNumber.toString()
                        )
                    )
                    refNumber++
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract footnote references")
        }
        
        return references
    }
    
    /**
     * Create footnote definition blocks for rendering.
     */
    private fun createFootnoteBlocks(
        definitions: Map<String, String>
    ): List<MessageContentBlock.FootnoteDef> {
        return try {
            definitions.map { (id, content) ->
                MessageContentBlock.FootnoteDef(
                    id = id,
                    content = content
                )
            }.sortedBy { it.id }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create footnote blocks")
            emptyList()
        }
    }
    
    /**
     * Remove footnote definitions from main content.
     */
    fun removeFootnoteDefinitions(content: String): String {
        return try {
            val lines = content.lines().toMutableList()
            var i = 0
            
            while (i < lines.size) {
                val line = lines[i]
                val defMatcher = FOOTNOTE_DEF_PATTERN.matcher(line)
                
                if (defMatcher.find()) {
                    // Remove the definition line
                    lines.removeAt(i)
                    
                    // Remove continuation lines
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        val contMatcher = FOOTNOTE_CONTINUATION_PATTERN.matcher(nextLine)
                        
                        if (contMatcher.find() || nextLine.isBlank()) {
                            lines.removeAt(i)
                        } else {
                            break
                        }
                    }
                    continue
                }
                i++
            }
            
            lines.joinToString("\n")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove footnote definitions")
            content
        }
    }
    
    /**
     * Check if content contains footnotes.
     */
    fun hasFootnotes(content: String): Boolean {
        return FOOTNOTE_REF_PATTERN.matcher(content).find() || 
               FOOTNOTE_DEF_PATTERN.matcher(content).find()
    }
    
    /**
     * Validate footnote structure and report issues.
     */
    fun validateFootnotes(content: String): FootnoteValidationResult {
        val references = mutableSetOf<String>()
        val definitions = mutableSetOf<String>()
        val issues = mutableListOf<String>()
        
        try {
            // Collect all references
            val refMatcher = FOOTNOTE_REF_PATTERN.matcher(content)
            while (refMatcher.find()) {
                val id = refMatcher.group(1)
                if (id != null) references.add(id)
            }
            
            // Collect all definitions
            val defMatcher = FOOTNOTE_DEF_PATTERN.matcher(content)
            while (defMatcher.find()) {
                val id = defMatcher.group(1)
                if (id != null) definitions.add(id)
            }
            
            // Find orphaned references (no definition)
            val orphanedRefs = references - definitions
            if (orphanedRefs.isNotEmpty()) {
                issues.add("Orphaned footnote references: ${orphanedRefs.joinToString(", ")}")
            }
            
            // Find unused definitions (no reference)
            val unusedDefs = definitions - references
            if (unusedDefs.isNotEmpty()) {
                issues.add("Unused footnote definitions: ${unusedDefs.joinToString(", ")}")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate footnotes")
            issues.add("Validation error: ${e.message}")
        }
        
        return FootnoteValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            referenceCount = references.size,
            definitionCount = definitions.size
        )
    }
    
    /**
     * Generate footnote section HTML for rendering.
     */
    fun generateFootnoteSection(footnotes: List<MessageContentBlock.FootnoteDef>): String {
        if (footnotes.isEmpty()) return ""
        
        return buildString {
            appendLine("---")
            appendLine("## Footnotes")
            appendLine()
            
            footnotes.forEachIndexed { index, footnote ->
                val number = index + 1
                appendLine("$number. <span id=\"footnote-${footnote.id}\">${footnote.content}</span>")
            }
        }
    }
    
    /**
     * Auto-number footnotes based on order of appearance.
     */
    fun autoNumberFootnotes(content: String): String {
        return try {
            val references = mutableMapOf<String, Int>()
            var nextNumber = 1
            
            // First pass: assign numbers to references in order of appearance
            val refMatcher = FOOTNOTE_REF_PATTERN.matcher(content)
            while (refMatcher.find()) {
                val id = refMatcher.group(1) ?: continue
                if (!references.containsKey(id)) {
                    references[id] = nextNumber++
                }
            }
            
            // Second pass: replace references with numbers
            val result = StringBuffer()
            val refMatcher2 = FOOTNOTE_REF_PATTERN.matcher(content)
            while (refMatcher2.find()) {
                val id = refMatcher2.group(1) ?: continue
                val number = references[id] ?: continue
                refMatcher2.appendReplacement(result, "[^$number]")
            }
            refMatcher2.appendTail(result)
            
            result.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to auto-number footnotes")
            content
        }
    }
}

/**
 * Result of footnote processing containing processed content and footnote blocks.
 */
data class FootnoteResult(
    val processedContent: String,
    val footnoteReferences: List<MessageContentBlock.FootnoteRef>,
    val footnoteDefinitions: List<MessageContentBlock.FootnoteDef>
)

/**
 * Result of footnote validation with issues and statistics.
 */
data class FootnoteValidationResult(
    val isValid: Boolean,
    val issues: List<String>,
    val referenceCount: Int,
    val definitionCount: Int
)

/**
 * Footnote rendering configuration.
 */
data class FootnoteConfig(
    val enableAutoNumbering: Boolean = true,
    val showFootnoteSection: Boolean = true,
    val enableBacklinks: Boolean = true,
    val footnoteSectionTitle: String = "Footnotes"
)