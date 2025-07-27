package com.cyberflux.qwinai.tools

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Manager class that handles registration and execution of tools
 */
class ToolManager(private val context: Context) {
    // Use ConcurrentHashMap for thread safety
    private val tools = ConcurrentHashMap<String, Tool>()

    // Default color for tool execution
    private val DEFAULT_COLOR = Color.parseColor("#757575")
    private val WEB_SEARCH_COLOR = Color.parseColor("#2563EB")

    init {
        // Register default tools
        try {
            registerDefaultTools()
            Timber.d("ToolManager initialized with default tools")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing ToolManager default tools: ${e.message}")
        }
    }

    /**
     * Register all default tools
     */
    private fun registerDefaultTools() {
        registerTool(WebSearchTool(context))
        registerTool(CalculatorTool(context))
        registerTool(SpreadsheetTool(context))
        registerTool(WeatherTool(context))
        registerTool(TranslationTool(context))
        registerTool(WikipediaTool(context))
        registerTool(FileGenerationTool(context))

        Timber.d("Registered ${tools.size} default tools")
    }

    /**
     * Register a tool with the manager
     * @param tool Tool implementation to register
     */
    fun registerTool(tool: Tool) {
        tools[tool.id] = tool
        Timber.d("Registered tool: ${tool.id} - ${tool.name}")
    }

    /**
     * Get a tool by its ID
     * @param id Tool ID to retrieve
     * @return Tool instance or null if not found
     */
    fun getToolById(id: String): Tool? {
        return tools[id]
    }

    /**
     * Find a tool that can handle the given message
     * @param message User message to analyze
     * @return First tool that can handle the message, or null if none found
     */
    fun findToolForMessage(message: String): Tool? {
        if (message.isBlank()) {
            return null
        }

        // Skip certain messages that shouldn't be processed by tools
        if (shouldSkipToolProcessing(message)) {
            return null
        }

        val matchingTools = tools.values.filter { it.canHandle(message) }

        if (matchingTools.isEmpty()) {
            return null
        }

        // If multiple tools match, prioritize based on order:
        // 1. Web search for explicit search queries
        // 2. Calculator for math expressions
        // 3. Other tools
        if (matchingTools.size > 1) {
            // Check for explicit web search requests
            if (containsExplicitWebSearchRequest(message)) {
                return matchingTools.find { it.id == "web_search" }
            }

            // Check for calculator queries
            if (containsMathExpression(message)) {
                return matchingTools.find { it.id == "calculator" }
            }
        }

        // Return the first matching tool
        return matchingTools.firstOrNull()
    }

    /**
     * Check if a message should skip tool processing
     */
    private fun shouldSkipToolProcessing(message: String): Boolean {
        // Skip very short messages
        if (message.length < 4) {
            return true
        }

        // Skip greetings and simple responses
        val skipPatterns = listOf(
            Pattern.compile("^\\s*(?:hi|hello|hey|greetings)\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(?:thanks|thank you|thx)\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(?:ok|okay|yes|no|maybe)\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(?:bye|goodbye|see you)\\s*$", Pattern.CASE_INSENSITIVE)
        )

        return skipPatterns.any { it.matcher(message).matches() }
    }

    /**
     * Check if message contains explicit web search request
     */
    private fun containsExplicitWebSearchRequest(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val searchTerms = listOf(
            "search", "google", "look up", "find", "search for",
            "web search", "internet", "online"
        )

        return searchTerms.any { lowerMessage.contains(it) }
    }

    /**
     * Check if message contains math expressions
     */
    private fun containsMathExpression(message: String): Boolean {
        // Simple pattern to detect math expressions
        val mathPattern = Pattern.compile("[0-9]+\\s*[+\\-*/^%]\\s*[0-9]+")
        return mathPattern.matcher(message).find()
    }

    /**
     * Execute a tool by its ID
     * @param id Tool ID to execute
     * @param message User message to process
     * @param parameters Additional parameters for tool execution
     * @return ToolResult with execution results
     */
    suspend fun executeToolById(
        id: String,
        message: String,
        parameters: Map<String, Any> = emptyMap()
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val tool = getToolById(id) ?: return@withContext ToolResult.error(
                errorMessage = "Tool not found: $id",
                content = "The requested tool '$id' is not available."
            )

            Timber.d("Executing tool: ${tool.id} for message: ${message.take(50)}...")
            val startTime = System.currentTimeMillis()

            val result = try {
                tool.execute(message, parameters)
            } catch (e: Exception) {
                Timber.e(e, "Error executing tool ${tool.id}: ${e.message}")
                ToolResult.error(
                    errorMessage = "Tool execution error: ${e.message}",
                    content = "Error executing tool ${tool.name}: ${e.message}"
                )
            }

            val executionTime = System.currentTimeMillis() - startTime
            Timber.d("Tool ${tool.id} execution completed in ${executionTime}ms - Success: ${result.success}")

            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error in executeToolById: ${e.message}")
            return@withContext ToolResult.error(
                errorMessage = "Unexpected error: ${e.message}",
                content = "An unexpected error occurred while executing the tool."
            )
        }
    }

}