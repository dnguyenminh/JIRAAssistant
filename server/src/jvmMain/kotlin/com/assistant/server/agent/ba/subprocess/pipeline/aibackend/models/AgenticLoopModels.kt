package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.subprocess.ToolCallResponse

/**
 * Configuration for a single agentic loop execution.
 */
data class AgenticLoopConfig(
    val ticketId: String,
    val docType: String,
    val maxToolCalls: Int,
    val taskTimeoutSeconds: Int,
    val processMode: ProcessMode = ProcessMode.STATELESS
)

/**
 * Result of an agentic loop execution, containing the generated document
 * and execution metrics.
 */
data class AgenticLoopResult(
    val document: String,
    val toolCallLog: List<ToolCallLogEntry>,
    val toolCallsExecuted: Int,
    val toolCallsFailed: Int,
    val timedOut: Boolean,
    val totalDurationMs: Long
)

/**
 * Result of a single tool call execution through the bridge,
 * containing the formatted result for the AI, the log entry,
 * and the raw subprocess response.
 */
data class ToolBridgeResult(
    val formattedResult: String,
    val logEntry: ToolCallLogEntry,
    val rawResponse: ToolCallResponse
)
