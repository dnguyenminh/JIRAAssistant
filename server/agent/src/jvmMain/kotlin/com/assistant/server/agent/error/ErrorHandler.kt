package com.assistant.server.agent.error

import com.assistant.agent.models.ErrorClassification
import com.assistant.agent.models.ErrorStrategy
import com.assistant.agent.models.RetryConfig
import com.assistant.agent.models.ToolResult
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Classifies errors and resolves error handling strategies.
 * Strategy resolution order: tool-level → phase-level → agent default.
 * Supports RETRY with configurable retries/delay, FALLBACK with
 * escalation to SKIP, and ABORT/SKIP strategies.
 */
class ErrorHandler {

    private val logger = LoggerFactory.getLogger(ErrorHandler::class.java)

    fun classifyError(error: Throwable): ErrorClassification =
        when {
            isRecoverable(error) -> ErrorClassification.RECOVERABLE
            else -> ErrorClassification.UNRECOVERABLE
        }

    suspend fun handleError(
        error: Throwable,
        toolStrategy: ErrorStrategy?,
        phaseStrategy: ErrorStrategy,
        retryConfig: RetryConfig,
        retryAction: suspend () -> ToolResult,
        fallbackAction: (suspend () -> ToolResult)?
    ): ToolResult {
        val strategy = toolStrategy ?: phaseStrategy
        logError(error, strategy)
        return executeStrategy(
            strategy, retryConfig, retryAction, fallbackAction
        )
    }

    private suspend fun executeStrategy(
        strategy: ErrorStrategy,
        retryConfig: RetryConfig,
        retryAction: suspend () -> ToolResult,
        fallbackAction: (suspend () -> ToolResult)?
    ): ToolResult = when (strategy) {
        ErrorStrategy.RETRY ->
            executeRetry(retryConfig, retryAction)
        ErrorStrategy.FALLBACK ->
            executeFallback(fallbackAction)
        ErrorStrategy.SKIP -> skipResult()
        ErrorStrategy.ABORT -> abortResult()
    }

    private suspend fun executeRetry(
        config: RetryConfig,
        action: suspend () -> ToolResult
    ): ToolResult {
        repeat(config.maxRetries) { attempt ->
            delay(config.delayMs)
            logger.info("Retry attempt {}/{}", attempt + 1, config.maxRetries)
            val result = tryAction(action)
            if (result.success) return result
        }
        return retryExhaustedResult(config.maxRetries)
    }

    private suspend fun executeFallback(
        fallbackAction: (suspend () -> ToolResult)?
    ): ToolResult {
        if (fallbackAction == null) return skipResult()
        return try {
            fallbackAction()
        } catch (e: Exception) {
            logger.warn("Fallback failed, escalating to SKIP: {}", e.message)
            skipResult()
        }
    }

    private suspend fun tryAction(
        action: suspend () -> ToolResult
    ): ToolResult = try {
        action()
    } catch (e: Exception) {
        logger.warn("Retry action failed: {}", e.message)
        errorResult(e)
    }

    private fun logError(error: Throwable, strategy: ErrorStrategy) {
        logger.error(
            "Error [{}]: {} — strategy={}",
            error::class.simpleName, error.message, strategy
        )
    }
}
