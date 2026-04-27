package com.assistant.server.agent.ba.subprocess.pipeline.models

/**
 * Result of a single pipeline step execution.
 *
 * Captures the step name, AI-generated content, execution duration,
 * and flags for timeout or empty response conditions.
 */
data class StepResponse(
    val stepName: String,
    val content: String,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val isEmpty: Boolean = false
)
