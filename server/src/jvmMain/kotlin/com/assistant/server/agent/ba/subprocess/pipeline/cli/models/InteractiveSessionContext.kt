package com.assistant.server.agent.ba.subprocess.pipeline.cli.models

import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.ba.models.ToolCallLogEntry

/**
 * Mutable session state for a CLI interactive session.
 *
 * Tracks tool call history, accumulated document content,
 * timing, and consecutive failure counts for circuit breaker logic.
 *
 * Once [toSummary] is called the session is considered completed
 * and all further mutations are rejected with [IllegalStateException].
 */
class InteractiveSessionContext {

    private val toolCallLog = mutableListOf<ToolCallLogEntry>()
    private val documentLines = mutableListOf<String>()
    private var consecutiveFailures = 0
    private var completed = false

    private val startTimeMs: Long = System.currentTimeMillis()

    /**
     * Records a tool call entry and updates the consecutive failure tracker.
     */
    fun recordToolCall(entry: ToolCallLogEntry) {
        ensureMutable()
        toolCallLog.add(entry)
        if (entry.success) {
            consecutiveFailures = 0
        } else {
            consecutiveFailures++
        }
    }

    /** Accumulates a single line of document content. */
    fun appendDocumentLine(line: String) {
        ensureMutable()
        documentLines.add(line)
    }

    /** Increments the consecutive failure counter (circuit breaker). */
    fun recordConsecutiveFailure() {
        ensureMutable()
        consecutiveFailures++
    }

    /** Resets the consecutive failure counter back to zero. */
    fun resetConsecutiveFailures() {
        ensureMutable()
        consecutiveFailures = 0
    }

    /** Current consecutive failure count (read-only). */
    val currentConsecutiveFailures: Int get() = consecutiveFailures

    /**
     * Produces an immutable snapshot of session metrics.
     * Marks the session as completed — further mutations are rejected.
     */
    fun toSummary(): SessionSummary {
        completed = true
        val document = documentLines.joinToString("\n")
        return SessionSummary(
            totalToolCalls = toolCallLog.size,
            failedToolCalls = toolCallLog.count { !it.success },
            documentSizeChars = document.length,
            totalDurationMs = System.currentTimeMillis() - startTimeMs,
            consecutiveFailures = consecutiveFailures
        )
    }

    /**
     * Converts session state to a [BATaskResult] with the given status.
     * Also marks the session as completed.
     */
    fun toBATaskResult(status: BATaskStatus): BATaskResult {
        completed = true
        val document = documentLines.joinToString("\n")
        return BATaskResult(
            document = document,
            toolCallsExecuted = toolCallLog.size,
            toolCallsFailed = toolCallLog.count { !it.success },
            totalDurationMs = System.currentTimeMillis() - startTimeMs,
            status = status,
            toolCallLog = toolCallLog.toList()
        )
    }

    private fun ensureMutable() {
        check(!completed) {
            "Session is completed — mutations are no longer allowed"
        }
    }
}
