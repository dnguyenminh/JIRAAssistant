package com.assistant.server.agent.error

import com.assistant.agent.models.ToolResult
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Error classification helpers for the ErrorHandler.
 * Recoverable: tool timeout, network error, rate limit.
 * Unrecoverable: auth failure, invalid config.
 */

private val RECOVERABLE_TYPES = setOf(
    TimeoutException::class,
    SocketTimeoutException::class,
    SocketException::class,
    UnknownHostException::class
)

private val RECOVERABLE_MESSAGES = listOf(
    "timeout", "timed out", "network",
    "connection", "rate limit", "rate_limit",
    "too many requests"
)

private val UNRECOVERABLE_MESSAGES = listOf(
    "auth", "unauthorized", "forbidden",
    "invalid config", "invalid agent config",
    "not found"
)

/** Check if an error is recoverable based on type and message. */
internal fun isRecoverable(error: Throwable): Boolean {
    if (error::class in RECOVERABLE_TYPES) return true
    val msg = error.message?.lowercase() ?: return false
    if (UNRECOVERABLE_MESSAGES.any { msg.contains(it) }) return false
    return RECOVERABLE_MESSAGES.any { msg.contains(it) }
}

internal fun skipResult() = ToolResult(
    toolName = "skipped",
    success = false,
    errorType = "SKIPPED",
    errorMessage = "Operation skipped"
)

internal fun abortResult() = ToolResult(
    toolName = "aborted",
    success = false,
    errorType = "ABORTED",
    errorMessage = "Operation aborted"
)

internal fun retryExhaustedResult(maxRetries: Int) = ToolResult(
    toolName = "retry-exhausted",
    success = false,
    errorType = "RETRY_EXHAUSTED",
    errorMessage = "All $maxRetries retries failed"
)

internal fun errorResult(error: Exception) = ToolResult(
    toolName = "error",
    success = false,
    errorType = error::class.simpleName ?: "Unknown",
    errorMessage = error.message ?: "Unknown error"
)
