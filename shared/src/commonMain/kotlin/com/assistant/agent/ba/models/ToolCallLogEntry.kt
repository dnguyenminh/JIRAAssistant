package com.assistant.agent.ba.models

import kotlinx.serialization.Serializable

/**
 * Log entry for a single tool call made during a BA subprocess task.
 *
 * Records the tool name, execution duration, success/failure status,
 * and the size of the result data in characters.
 */
@Serializable
data class ToolCallLogEntry(
    val toolName: String,
    val durationMs: Long,
    val success: Boolean,
    val resultSizeChars: Int
)
