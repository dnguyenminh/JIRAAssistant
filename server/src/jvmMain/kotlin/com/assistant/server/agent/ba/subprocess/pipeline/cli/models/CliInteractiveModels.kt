package com.assistant.server.agent.ba.subprocess.pipeline.cli.models

/**
 * Parsed tool call extracted from CLI stdout.
 *
 * Lightweight representation without an ID field — the ID is generated
 * internally when delegating to SubprocessProxy.
 */
data class ParsedToolCall(
    val name: String,
    val arguments: Map<String, String>
)

/**
 * Immutable configuration for the interactive loop,
 * decoupled from BA-specific config types.
 */
data class LoopConfig(
    val maxToolCalls: Int,
    val timeoutSeconds: Int
)

/**
 * Outcome of the interactive loop — raw data before
 * conversion to [com.assistant.agent.ba.models.BATaskResult].
 */
data class LoopResult(
    val document: String,
    val timedOut: Boolean,
    val toolCallsExecuted: Int,
    val toolCallsFailed: Int
)

/**
 * Immutable snapshot of session metrics after completion.
 */
data class SessionSummary(
    val totalToolCalls: Int,
    val failedToolCalls: Int,
    val documentSizeChars: Int,
    val totalDurationMs: Long,
    val consecutiveFailures: Int
)
