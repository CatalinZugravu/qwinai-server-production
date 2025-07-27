package com.cyberflux.qwinai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Core interface for all tools in the app
 * Tools provide functionality like web search, calculation, etc.
 */
interface Tool {
    /**
     * Unique identifier for the tool
     */
    val id: String

    /**
     * Human-readable name of the tool
     */
    val name: String

    /**
     * Description of what the tool does
     */
    val description: String

    /**
     * Check if this tool can handle the given message
     * @param message The user's message
     * @return true if this tool can handle the message
     */
    fun canHandle(message: String): Boolean

    /**
     * Execute the tool with the given message and parameters
     * @param message The user's message
     * @param parameters Additional parameters for tool execution
     * @return ToolResult containing the execution result
     */
    suspend fun execute(message: String, parameters: Map<String, Any> = emptyMap()): ToolResult
}

/**
 * Result of a tool execution
 * @param success Whether the execution was successful
 * @param content Formatted content from the tool execution
 * @param data Raw data from the tool execution
 * @param metadata Additional metadata about the execution
 * @param error Error message if execution failed
 */
data class ToolResult(
    val success: Boolean,
    val content: String,
    val data: Any? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val error: String? = null
) {
    companion object {
        /**
         * Create a success result
         */
        fun success(content: String, data: Any? = null, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return ToolResult(
                success = true,
                content = content,
                data = data,
                metadata = metadata
            )
        }

        /**
         * Create an error result
         */
        fun error(errorMessage: String, content: String = "", metadata: Map<String, Any> = emptyMap()): ToolResult {
            return ToolResult(
                success = false,
                content = content,
                error = errorMessage,
                metadata = metadata
            )
        }
    }
}