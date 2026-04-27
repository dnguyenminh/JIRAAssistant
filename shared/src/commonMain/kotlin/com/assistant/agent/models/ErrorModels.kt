package com.assistant.agent.models

import kotlinx.serialization.Serializable

/**
 * Strategy for handling errors during agent execution.
 * Applied at tool-level, phase-level, or agent-level.
 */
@Serializable
enum class ErrorStrategy {
    /** Retry the failed operation with configurable max retries and delay. */
    RETRY,
    /** Skip the failed operation and continue execution. */
    SKIP,
    /** Stop the agent and return a FAILED AgentOutput. */
    ABORT,
    /** Execute a provided fallback function. */
    FALLBACK
}

/**
 * Configuration for retry behavior when ErrorStrategy is RETRY.
 */
@Serializable
data class RetryConfig(
    val maxRetries: Int = 2,
    val delayMs: Long = 2000
)

/**
 * Classification of errors into recoverable and unrecoverable categories.
 *
 * Recoverable: tool timeout, network error, rate limit exceeded.
 * Unrecoverable: authentication failure, invalid agent config, agent not found.
 */
@Serializable
enum class ErrorClassification {
    RECOVERABLE,
    UNRECOVERABLE
}
