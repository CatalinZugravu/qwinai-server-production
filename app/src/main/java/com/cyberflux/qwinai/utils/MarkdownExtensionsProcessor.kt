package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cyberflux.qwinai.model.*
import java.util.regex.Pattern
import timber.log.Timber

/**
 * Comprehensive processor for all extended markdown features:
 * - Admonitions/Callouts
 * - Subscript/Superscript
 * - Definition Lists
 * - Keyboard Keys
 * - Text Highlighting
 * - Mentions & Issue References
 * - Abbreviations
 * - Video Embeds
 * - Spoilers
 */
class MarkdownExtensionsProcessor(
    private val context: Context,
    private val config: MarkdownConfig = MarkdownConfig()
) {
    
    companion object {
        // Admonitions: !!! note "Title"
        private val ADMONITION_PATTERN = Pattern.compile(
            "^!!!\\s+(note|tip|warning|danger|bug|example|quote|success|failure|question|info|abstract|todo)\\s*(?:\"([^\"]*?)\")?\\s*$",
            Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
        )
        
        // Subscript: H~2~O, Superscript: x^2^
        private val SUBSCRIPT_PATTERN = Pattern.compile("~([^~\\s]+?)~")
        private val SUPERSCRIPT_PATTERN = Pattern.compile("\\^([^\\^\\s]+?)\\^")
        
        // Definition Lists: term : definition
        private val DEFINITION_LIST_PATTERN = Pattern.compile(
            "^(.+?)\\s*:\\s*(.+?)$",
            Pattern.MULTILINE
        )
        
        // Keyboard keys: <kbd>Ctrl</kbd> or [[Ctrl+C]]
        private val KBD_PATTERN = Pattern.compile("<kbd>([^<>]+?)</kbd>", Pattern.CASE_INSENSITIVE)
        private val KBD_BRACKET_PATTERN = Pattern.compile("\\[\\[([^\\[\\]]+?)\\]\\]")
        
        // Text highlighting: ==highlighted text==
        private val HIGHLIGHT_PATTERN = Pattern.compile("==([^=]+?)==")
        
        // Mentions: @username
        private val MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_-]+)\\b")
        
        // Issue references: #123, GH-456, ABC-789
        private val ISSUE_PATTERN = Pattern.compile("\\b(?:GH-|#)?(\\d+)\\b|\\b([A-Z]{2,5}-\\d+)\\b")
        
        // Abbreviations: *[HTML]: HyperText Markup Language
        private val ABBR_DEF_PATTERN = Pattern.compile(
            "^\\*\\[([^\\]]+?)\\]\\s*:\\s*(.+?)$",
            Pattern.MULTILINE
        )
        
        // Video embeds: YouTube, Vimeo, etc.
        private val VIDEO_PATTERNS = mapOf(
            "youtube" to Pattern.compile("(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})"),
            "vimeo" to Pattern.compile("(?:https?://)?(?:www\\.)?vimeo\\.com/(\\d+)"),
            "twitch" to Pattern.compile("(?:https?://)?(?:www\\.)?twitch\\.tv/videos/(\\d+)")
        )
        
        // Spoilers: ||spoiler text||
        private val SPOILER_PATTERN = Pattern.compile("\\|\\|([^|]+?)\\|\\|")
    }
    
    /**
     * Process all extended markdown features in content.
     */
    fun processAllExtensions(content: String): ExtensionProcessingResult {
        val results = mutableListOf<MessageContentBlock>()
        val abbreviations = mutableMapOf<String, String>()
        var processedContent = content
        
        try {
            // Process abbreviation definitions first (and remove them)
            if (config.enableAbbreviations) {
                val abbrResult = processAbbreviations(processedContent)
                abbreviations.putAll(abbrResult.definitions)
                processedContent = abbrResult.processedContent
            }
            
            // Process all other extensions
            if (config.enableAdmonitions) {
                results.addAll(processAdmonitions(processedContent))
            }
            
            if (config.enableSubscriptSuperscript) {
                results.addAll(processSubscriptSuperscript(processedContent))
            }
            
            if (config.enableDefinitionLists) {
                results.addAll(processDefinitionLists(processedContent))
            }
            
            if (config.enableKeyboardKeys) {
                results.addAll(processKeyboardKeys(processedContent))
            }
            
            if (config.enableHighlighting) {
                results.addAll(processHighlighting(processedContent))
            }
            
            if (config.enableMentions) {
                results.addAll(processMentions(processedContent))
            }
            
            if (config.enableIssueReferences) {
                results.addAll(processIssueReferences(processedContent))
            }
            
            if (config.enableVideoEmbeds) {
                results.addAll(processVideoEmbeds(processedContent))
            }
            
            if (config.enableSpoilers) {
                results.addAll(processSpoilers(processedContent))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process markdown extensions")
        }
        
        return ExtensionProcessingResult(
            processedContent = processedContent,
            blocks = results,
            abbreviations = abbreviations
        )
    }
    
    /**
     * Process admonitions/callouts: !!! note "Title"
     */
    private fun processAdmonitions(content: String): List<MessageContentBlock.Admonition> {
        val admonitions = mutableListOf<MessageContentBlock.Admonition>()
        
        try {
            val lines = content.lines()
            var i = 0
            
            while (i < lines.size) {
                val line = lines[i]
                val matcher = ADMONITION_PATTERN.matcher(line)
                
                if (matcher.find()) {
                    val typeStr = matcher.group(1)?.lowercase() ?: "note"
                    val title = matcher.group(2)
                    val type = AdmonitionType.values().find { 
                        it.name.lowercase() == typeStr 
                    } ?: AdmonitionType.NOTE
                    
                    // Collect content until next admonition or end
                    val admonitionContent = StringBuilder()
                    i++
                    
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        if (nextLine.trim().startsWith("!!!")) break
                        if (nextLine.trim().startsWith("    ")) {
                            admonitionContent.append(nextLine.removePrefix("    ")).append("\n")
                        } else if (nextLine.isBlank()) {
                            admonitionContent.append("\n")
                        } else {
                            break
                        }
                        i++
                    }
                    
                    admonitions.add(
                        MessageContentBlock.Admonition(
                            type = type,
                            title = title,
                            content = admonitionContent.toString().trim(),
                            isCollapsible = false,
                            isCollapsed = false
                        )
                    )
                    
                    Timber.d("✅ Processed admonition: $type")
                    continue
                }
                i++
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process admonitions")
        }
        
        return admonitions
    }
    
    /**
     * Process subscript/superscript: ~text~ and ^text^
     */
    private fun processSubscriptSuperscript(content: String): List<MessageContentBlock> {
        val results = mutableListOf<MessageContentBlock>()
        
        try {
            // Process subscripts
            val subMatcher = SUBSCRIPT_PATTERN.matcher(content)
            while (subMatcher.find()) {
                val text = subMatcher.group(1) ?: continue
                results.add(MessageContentBlock.Subscript(text))
            }
            
            // Process superscripts
            val supMatcher = SUPERSCRIPT_PATTERN.matcher(content)
            while (supMatcher.find()) {
                val text = supMatcher.group(1) ?: continue
                results.add(MessageContentBlock.Superscript(text))
            }
            
            if (results.isNotEmpty()) {
                Timber.d("✅ Processed ${results.size} sub/superscripts")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process subscript/superscript")
        }
        
        return results
    }
    
    /**
     * Process definition lists: term : definition
     */
    private fun processDefinitionLists(content: String): List<MessageContentBlock.DefinitionList> {
        val definitionLists = mutableListOf<MessageContentBlock.DefinitionList>()
        
        try {
            val lines = content.lines()
            var i = 0
            
            while (i < lines.size) {
                val currentDefinitions = mutableListOf<DefinitionItem>()
                var currentTerm: String? = null
                var termDefinitions = mutableListOf<String>()
                
                // Collect consecutive definition lines
                while (i < lines.size) {
                    val line = lines[i]
                    val matcher = DEFINITION_LIST_PATTERN.matcher(line)
                    
                    if (matcher.find()) {
                        // Save previous term if exists
                        if (currentTerm != null && termDefinitions.isNotEmpty()) {
                            currentDefinitions.add(DefinitionItem(currentTerm, termDefinitions.toList()))
                        }
                        
                        // Start new term
                        currentTerm = matcher.group(1)?.trim()
                        termDefinitions = mutableListOf(matcher.group(2)?.trim() ?: "")
                        
                    } else if (line.trim().startsWith(": ") && currentTerm != null) {
                        // Additional definition for current term
                        termDefinitions.add(line.trim().removePrefix(": "))
                        
                    } else if (line.isBlank()) {
                        // Continue collecting
                        
                    } else {
                        // End of definition list
                        break
                    }
                    
                    i++
                }
                
                // Save final term
                if (currentTerm != null && termDefinitions.isNotEmpty()) {
                    currentDefinitions.add(DefinitionItem(currentTerm, termDefinitions.toList()))
                }
                
                // Create definition list if we have definitions
                if (currentDefinitions.isNotEmpty()) {
                    definitionLists.add(MessageContentBlock.DefinitionList(currentDefinitions))
                    Timber.d("✅ Processed definition list with ${currentDefinitions.size} items")
                }
                
                if (i < lines.size) i++
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process definition lists")
        }
        
        return definitionLists
    }
    
    /**
     * Process keyboard keys: <kbd>Ctrl</kbd> and [[Ctrl+C]]
     */
    private fun processKeyboardKeys(content: String): List<MessageContentBlock.KeyboardKey> {
        val keyboardKeys = mutableListOf<MessageContentBlock.KeyboardKey>()
        
        try {
            // Process <kbd> tags
            val kbdMatcher = KBD_PATTERN.matcher(content)
            while (kbdMatcher.find()) {
                val keys = kbdMatcher.group(1)?.split("+") ?: continue
                keyboardKeys.add(MessageContentBlock.KeyboardKey(keys.map { it.trim() }))
            }
            
            // Process [[key]] format
            val bracketMatcher = KBD_BRACKET_PATTERN.matcher(content)
            while (bracketMatcher.find()) {
                val keys = bracketMatcher.group(1)?.split("+") ?: continue
                keyboardKeys.add(MessageContentBlock.KeyboardKey(keys.map { it.trim() }))
            }
            
            if (keyboardKeys.isNotEmpty()) {
                Timber.d("✅ Processed ${keyboardKeys.size} keyboard key combinations")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process keyboard keys")
        }
        
        return keyboardKeys
    }
    
    /**
     * Process text highlighting: ==text==
     */
    private fun processHighlighting(content: String): List<MessageContentBlock.Highlight> {
        val highlights = mutableListOf<MessageContentBlock.Highlight>()
        
        try {
            val matcher = HIGHLIGHT_PATTERN.matcher(content)
            while (matcher.find()) {
                val text = matcher.group(1) ?: continue
                highlights.add(MessageContentBlock.Highlight(text))
            }
            
            if (highlights.isNotEmpty()) {
                Timber.d("✅ Processed ${highlights.size} text highlights")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process highlights")
        }
        
        return highlights
    }
    
    /**
     * Process user mentions: @username
     */
    private fun processMentions(content: String): List<MessageContentBlock.Mention> {
        val mentions = mutableListOf<MessageContentBlock.Mention>()
        
        try {
            val matcher = MENTION_PATTERN.matcher(content)
            while (matcher.find()) {
                val username = matcher.group(1) ?: continue
                val url = config.mentionUrlPattern?.replace("{username}", username)
                
                mentions.add(
                    MessageContentBlock.Mention(
                        username = username,
                        displayName = "@$username",
                        url = url
                    )
                )
            }
            
            if (mentions.isNotEmpty()) {
                Timber.d("✅ Processed ${mentions.size} user mentions")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process mentions")
        }
        
        return mentions
    }
    
    /**
     * Process issue references: #123, GH-456
     */
    private fun processIssueReferences(content: String): List<MessageContentBlock.IssueRef> {
        val issueRefs = mutableListOf<MessageContentBlock.IssueRef>()
        
        try {
            val matcher = ISSUE_PATTERN.matcher(content)
            while (matcher.find()) {
                val number = matcher.group(1) ?: matcher.group(2) ?: continue
                val url = config.issueUrlPattern?.replace("{number}", number)
                
                issueRefs.add(
                    MessageContentBlock.IssueRef(
                        number = number,
                        url = url,
                        status = IssueStatus.OPEN // Default, could be enhanced with API lookup
                    )
                )
            }
            
            if (issueRefs.isNotEmpty()) {
                Timber.d("✅ Processed ${issueRefs.size} issue references")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process issue references")
        }
        
        return issueRefs
    }
    
    /**
     * Process abbreviation definitions and usage.
     */
    private fun processAbbreviations(content: String): AbbreviationResult {
        val definitions = mutableMapOf<String, String>()
        
        try {
            val matcher = ABBR_DEF_PATTERN.matcher(content)
            while (matcher.find()) {
                val abbr = matcher.group(1) ?: continue
                val definition = matcher.group(2) ?: continue
                definitions[abbr] = definition
            }
            
            // Remove abbreviation definitions from content
            val processedContent = content.replace(ABBR_DEF_PATTERN.toRegex(), "")
            
            if (definitions.isNotEmpty()) {
                Timber.d("✅ Processed ${definitions.size} abbreviation definitions")
            }
            
            return AbbreviationResult(processedContent, definitions)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process abbreviations")
            return AbbreviationResult(content, emptyMap())
        }
    }
    
    /**
     * Process video embeds from various platforms.
     */
    private fun processVideoEmbeds(content: String): List<MessageContentBlock.VideoEmbed> {
        val videos = mutableListOf<MessageContentBlock.VideoEmbed>()
        
        try {
            VIDEO_PATTERNS.forEach { (providerName, pattern) ->
                val matcher = pattern.matcher(content)
                while (matcher.find()) {
                    val videoId = matcher.group(1) ?: continue
                    val provider = VideoProvider.values().find { 
                        it.name.lowercase() == providerName 
                    } ?: VideoProvider.GENERIC
                    
                    val url = when (provider) {
                        VideoProvider.YOUTUBE -> "https://www.youtube.com/watch?v=$videoId"
                        VideoProvider.VIMEO -> "https://vimeo.com/$videoId"
                        VideoProvider.TWITCH -> "https://www.twitch.tv/videos/$videoId"
                        else -> matcher.group(0) // Original URL
                    }
                    
                    videos.add(
                        MessageContentBlock.VideoEmbed(
                            url = url,
                            provider = provider,
                            title = null // Could be enhanced with API lookup
                        )
                    )
                }
            }
            
            if (videos.isNotEmpty()) {
                Timber.d("✅ Processed ${videos.size} video embeds")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process video embeds")
        }
        
        return videos.take(config.maxVideoEmbeds) // Limit number of embeds
    }
    
    /**
     * Process spoilers: ||spoiler text||
     */
    private fun processSpoilers(content: String): List<MessageContentBlock.Spoiler> {
        val spoilers = mutableListOf<MessageContentBlock.Spoiler>()
        
        try {
            val matcher = SPOILER_PATTERN.matcher(content)
            while (matcher.find()) {
                val text = matcher.group(1) ?: continue
                spoilers.add(MessageContentBlock.Spoiler(text))
            }
            
            if (spoilers.isNotEmpty()) {
                Timber.d("✅ Processed ${spoilers.size} spoilers")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process spoilers")
        }
        
        return spoilers
    }
    
    /**
     * Remove processed extension syntax from content.
     */
    fun removeExtensionSyntax(content: String): String {
        return try {
            var cleaned = content
            
            // Remove patterns that should not appear in final rendered content
            if (config.enableAdmonitions) {
                cleaned = cleaned.replace(ADMONITION_PATTERN.toRegex(), "")
            }
            
            if (config.enableAbbreviations) {
                cleaned = cleaned.replace(ABBR_DEF_PATTERN.toRegex(), "")
            }
            
            cleaned.trim()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean extension syntax")
            content
        }
    }
}

/**
 * Result of processing all markdown extensions.
 */
data class ExtensionProcessingResult(
    val processedContent: String,
    val blocks: List<MessageContentBlock>,
    val abbreviations: Map<String, String>
)

/**
 * Result of abbreviation processing.
 */
data class AbbreviationResult(
    val processedContent: String,
    val definitions: Map<String, String>
)