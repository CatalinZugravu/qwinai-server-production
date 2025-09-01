package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.MessageContentBlock
import java.util.regex.Pattern
import timber.log.Timber

/**
 * Comprehensive Mermaid diagram processor supporting multiple diagram types:
 * - Flowcharts
 * - Sequence diagrams  
 * - Gantt charts
 * - Pie charts
 * - Git graphs
 * - Entity relationship diagrams
 * - User journey diagrams
 * - State diagrams
 * - Class diagrams
 */
class MermaidProcessor(private val context: Context) {
    
    companion object {
        // Pattern to detect Mermaid diagrams in code blocks
        private val MERMAID_CODE_PATTERN = Pattern.compile(
            "```mermaid\\s*\\n([\\s\\S]*?)\\n?```",
            Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
        )
        
        // Pattern to detect diagram type from first line
        private val DIAGRAM_TYPE_PATTERN = Pattern.compile(
            "^\\s*(graph|flowchart|sequenceDiagram|gantt|pie|gitgraph|erDiagram|journey|stateDiagram|classDiagram|mindmap|timeline|quadrantChart|requirement|c4Context)\\b",
            Pattern.CASE_INSENSITIVE
        )
        
        // Online Mermaid editor for fallback
        private const val MERMAID_LIVE_EDITOR = "https://mermaid.live/edit"
    }
    
    /**
     * Process Mermaid diagrams in content and return blocks.
     */
    fun processMermaidDiagrams(content: String): List<MessageContentBlock.Mermaid> {
        val diagrams = mutableListOf<MessageContentBlock.Mermaid>()
        
        try {
            val matcher = MERMAID_CODE_PATTERN.matcher(content)
            
            while (matcher.find()) {
                val diagramCode = matcher.group(1)?.trim() ?: continue
                val diagramType = detectDiagramType(diagramCode)
                
                diagrams.add(
                    MessageContentBlock.Mermaid(
                        diagram = diagramCode,
                        type = diagramType
                    )
                )
                
                Timber.d("✅ Detected Mermaid diagram: $diagramType")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to process Mermaid diagrams")
        }
        
        return diagrams
    }
    
    /**
     * Detect the type of Mermaid diagram from content.
     */
    private fun detectDiagramType(diagramCode: String): String {
        return try {
            val matcher = DIAGRAM_TYPE_PATTERN.matcher(diagramCode)
            if (matcher.find()) {
                val type = matcher.group(1)?.lowercase() ?: "unknown"
                
                // Normalize type names
                when (type) {
                    "graph", "flowchart" -> "flowchart"
                    "sequencediagram" -> "sequence"
                    "gitgraph" -> "gitGraph"
                    "erdiagram" -> "erDiagram"
                    "statediagram" -> "stateDiagram"
                    "classdiagram" -> "classDiagram"
                    "c4context" -> "c4Context"
                    "quadrantchart" -> "quadrantChart"
                    else -> type
                }
            } else {
                // Try to infer from content patterns
                when {
                    diagramCode.contains("-->") || diagramCode.contains("---") -> "flowchart"
                    diagramCode.contains("participant") || diagramCode.contains("activate") -> "sequence"
                    diagramCode.contains("dateFormat") || diagramCode.contains("title") -> "gantt"
                    diagramCode.contains("pie title") -> "pie"
                    diagramCode.contains("branch") || diagramCode.contains("commit") -> "gitGraph"
                    diagramCode.contains("entity") || diagramCode.contains("relationship") -> "erDiagram"
                    diagramCode.contains("journey") -> "journey"
                    diagramCode.contains("state") || diagramCode.contains("[*]") -> "stateDiagram"
                    diagramCode.contains("class") || diagramCode.contains("interface") -> "classDiagram"
                    else -> "flowchart" // Default to flowchart
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect diagram type")
            "unknown"
        }
    }
    
    /**
     * Create a rendered Mermaid diagram view.
     */
    fun createDiagramView(
        container: LinearLayout,
        diagram: MessageContentBlock.Mermaid
    ) {
        try {
            createInteractiveFallback(container, diagram)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create diagram view, falling back to text")
            createTextFallback(container, diagram)
        }
    }
    
    /**
     * Create interactive fallback using Mermaid Live Editor.
     */
    private fun createInteractiveFallback(
        container: LinearLayout,
        diagram: MessageContentBlock.Mermaid
    ) {
        try {
            // Create clickable container that opens Mermaid Live Editor
            val diagramContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setPadding(16, 16, 16, 16)
                setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_surfaceVariant))
                isClickable = true
                isFocusable = true
                
                setOnClickListener {
                    openMermaidLiveEditor(diagram.diagram)
                }
            }
            
            // Diagram type header
            val typeText = TextView(context).apply {
                text = "📊 ${diagram.type.replaceFirstChar { it.uppercase() }} Diagram"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            
            // Preview text (first few lines)
            val previewText = TextView(context).apply {
                val preview = diagram.diagram.lines().take(3).joinToString("\n")
                text = if (diagram.diagram.length > 100) "$preview..." else preview
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                maxLines = 3
            }
            
            // Click to view text
            val clickText = TextView(context).apply {
                text = "🔗 Click to view interactive diagram"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12
                }
                setTextColor(ContextCompat.getColor(context, R.color.link_color))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
            }
            
            diagramContainer.addView(typeText)
            diagramContainer.addView(previewText)
            diagramContainer.addView(clickText)
            container.addView(diagramContainer)
            
            Timber.d("✅ Created interactive Mermaid diagram viewer")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create interactive fallback")
            createTextFallback(container, diagram)
        }
    }
    
    /**
     * Create text-based fallback showing raw Mermaid code.
     */
    private fun createTextFallback(
        container: LinearLayout,
        diagram: MessageContentBlock.Mermaid
    ) {
        try {
            val textView = TextView(context).apply {
                text = "```mermaid\n${diagram.diagram}\n```"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setPadding(16, 16, 16, 16)
                setTextColor(ContextCompat.getColor(context, R.color.code_text))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundColor(ContextCompat.getColor(context, R.color.code_background))
                setTextIsSelectable(true)
            }
            
            container.addView(textView)
            Timber.d("✅ Created text fallback for Mermaid diagram")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create text fallback")
        }
    }
    
    /**
     * Open Mermaid Live Editor with diagram code.
     */
    private fun openMermaidLiveEditor(diagramCode: String) {
        try {
            // Encode diagram for URL
            val encodedDiagram = Uri.encode(diagramCode)
            val url = "$MERMAID_LIVE_EDITOR#/edit/$encodedDiagram"
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            
            context.startActivity(intent)
            Timber.d("✅ Opened Mermaid Live Editor")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to open Mermaid Live Editor")
        }
    }
    
    /**
     * Validate Mermaid diagram syntax (basic validation).
     */
    fun validateDiagram(diagram: MessageContentBlock.Mermaid): DiagramValidationResult {
        val issues = mutableListOf<String>()
        
        try {
            val content = diagram.diagram
            
            // Check for empty content
            if (content.isBlank()) {
                issues.add("Diagram content is empty")
                return DiagramValidationResult(false, issues)
            }
            
            // Basic syntax checks based on diagram type
            when (diagram.type.lowercase()) {
                "flowchart", "graph" -> {
                    if (!content.contains("-->") && !content.contains("---")) {
                        issues.add("Flowchart should contain arrows (-->) or connections (---)")
                    }
                }
                "sequence" -> {
                    if (!content.contains("participant") && !content.contains("actor")) {
                        issues.add("Sequence diagram should have participants or actors")
                    }
                }
                "gantt" -> {
                    if (!content.contains("title") && !content.contains("dateFormat")) {
                        issues.add("Gantt chart should have a title or dateFormat")
                    }
                }
                "pie" -> {
                    if (!content.contains("pie title") && !content.contains("pie")) {
                        issues.add("Pie chart should start with 'pie title' or 'pie'")
                    }
                }
            }
            
        } catch (e: Exception) {
            issues.add("Validation error: ${e.message}")
        }
        
        return DiagramValidationResult(issues.isEmpty(), issues)
    }
    
    /**
     * Generate diagram description for accessibility.
     */
    fun generateDiagramDescription(diagram: MessageContentBlock.Mermaid): String {
        return try {
            val type = diagram.type.replaceFirstChar { it.uppercase() }
            buildString {
                append("$type diagram")
                append(". ")
                
                // Add basic structure description
                when (diagram.type.lowercase()) {
                    "flowchart", "graph" -> append("Shows process flow or decision tree.")
                    "sequence" -> append("Shows interaction sequence between entities.")
                    "gantt" -> append("Shows project timeline and task scheduling.")
                    "pie" -> append("Shows data distribution in pie chart format.")
                    "gitgraph" -> append("Shows git branch and merge history.")
                    else -> append("Interactive diagram content.")
                }
            }
        } catch (e: Exception) {
            "Mermaid ${diagram.type} diagram"
        }
    }
}

/**
 * Result of diagram validation.
 */
data class DiagramValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)
