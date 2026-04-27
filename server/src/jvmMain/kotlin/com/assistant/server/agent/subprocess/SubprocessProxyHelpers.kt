package com.assistant.server.agent.subprocess

import com.assistant.agent.models.ToolResult
import com.assistant.agent.subprocess.ToolCallResponse

/**
 * Helper functions for [SubprocessProxyImpl].
 * Keeps the main class under the 200-line limit.
 *
 * Requirements: 20.4, 20.6, 20.7, 20.9
 */

/** Tool source classification for logging (Requirement 20.7). */
internal enum class ToolSource { LOCAL, AGENT_MCP, SHARED_MCP }

/**
 * Builds a [ToolCallResponse] from a [ToolResult].
 * Maps success/failure and carries the correlation ID.
 */
internal fun buildResponse(
    correlationId: String,
    result: ToolResult
): ToolCallResponse = if (result.success) {
    ToolCallResponse(
        id = correlationId,
        success = true,
        data = result.data
    )
} else {
    ToolCallResponse(
        id = correlationId,
        success = false,
        error = result.errorMessage ?: "Unknown error"
    )
}

/**
 * Builds an error [ToolCallResponse] from an exception.
 * Never propagates the exception — captures it in the response.
 */
internal fun buildErrorResponse(
    correlationId: String,
    error: Exception
): ToolCallResponse = ToolCallResponse(
    id = correlationId,
    success = false,
    error = error.message ?: "Unknown error"
)

/**
 * Resolves the tool source tier for logging.
 *
 * Three-tier priority (Requirement 20.9):
 * - `mcp_agent_*` prefix → AGENT_MCP (agent home directory MCP)
 * - `mcp_*` prefix → SHARED_MCP (shared MCP bridge)
 * - Everything else → LOCAL (registered in agent code)
 */
internal fun resolveToolSource(toolName: String): ToolSource = when {
    toolName.startsWith("mcp_agent_") -> ToolSource.AGENT_MCP
    toolName.startsWith("mcp_") -> ToolSource.SHARED_MCP
    else -> ToolSource.LOCAL
}
