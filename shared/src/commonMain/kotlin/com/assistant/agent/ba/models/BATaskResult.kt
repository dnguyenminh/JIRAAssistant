package com.assistant.agent.ba.models

import kotlinx.serialization.Serializable

/**
 * Execution outcome status for a BA subprocess task.
 *
 * - SUCCESS  — Document generated, all tool calls completed normally
 * - PARTIAL  — Document generated but some tool calls failed
 * - TIMEOUT  — Task exceeded timeout; partial document returned
 * - FAILED   — Subprocess crashed or could not start; no document produced
 */
@Serializable
enum class BATaskStatus {
    SUCCESS, PARTIAL, TIMEOUT, FAILED
}

/**
 * Result of a BA subprocess task execution.
 *
 * Contains the generated document, execution metrics,
 * status, and an ordered log of all tool calls made.
 */
@Serializable
data class BATaskResult(
    val document: String,
    val toolCallsExecuted: Int,
    val toolCallsFailed: Int,
    val totalDurationMs: Long,
    val status: BATaskStatus,
    val toolCallLog: List<ToolCallLogEntry> = emptyList()
)
