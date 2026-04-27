package com.assistant.server.agent.ba.subprocess.pipeline.models

/**
 * Result of a single MCP tool call during data collection.
 *
 * Captures the tool name, success/failure status, returned data,
 * execution duration, and optional error message for failed calls.
 */
data class ToolCallOutcome(
    val toolName: String,
    val success: Boolean,
    val data: String,
    val durationMs: Long,
    val errorMessage: String? = null
)
