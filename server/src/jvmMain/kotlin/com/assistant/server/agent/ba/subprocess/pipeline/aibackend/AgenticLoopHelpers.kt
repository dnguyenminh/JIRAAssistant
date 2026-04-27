package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopResult
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ProcessMode

/**
 * Internal mutable state tracker for a single agentic loop execution.
 *
 * Accumulates tool call results, tracks failures, and produces
 * the final [AgenticLoopResult] when the loop completes.
 */
internal class LoopState(val maxToolCalls: Int) {
    var document: String = ""
    var toolCallsExecuted: Int = 0
    var toolCallsFailed: Int = 0
    var consecutiveFailures: Int = 0
    val toolCallLog = mutableListOf<ToolCallLogEntry>()
    val allToolResults = mutableListOf<String>()

    fun addToolCall(entry: ToolCallLogEntry, success: Boolean) {
        toolCallLog.add(entry)
        toolCallsExecuted++
        if (!success) {
            toolCallsFailed++
            consecutiveFailures++
        } else {
            consecutiveFailures = 0
        }
    }

    fun toResult() = AgenticLoopResult(
        document = document,
        toolCallLog = toolCallLog.toList(),
        toolCallsExecuted = toolCallsExecuted,
        toolCallsFailed = toolCallsFailed,
        timedOut = false,
        totalDurationMs = 0
    )
}

/**
 * Determines whether the loop should use session mode based on
 * the backend type and config.
 *
 * - [AiApiClient] → always session mode
 * - [AiCliClient] with PERSISTENT → session mode
 * - Otherwise → stateless mode
 */
internal fun shouldUseSessionMode(
    backend: AiBackend,
    config: AgenticLoopConfig
): Boolean = when (backend) {
    is AiApiClient -> true
    is AiCliClient -> config.processMode == ProcessMode.PERSISTENT
    else -> false
}

/**
 * Builds a timeout result with zero tool calls and the given duration.
 */
internal fun buildTimeoutResult(durationMs: Long) = AgenticLoopResult(
    document = "",
    toolCallLog = emptyList(),
    toolCallsExecuted = 0,
    toolCallsFailed = 0,
    timedOut = true,
    totalDurationMs = durationMs
)
