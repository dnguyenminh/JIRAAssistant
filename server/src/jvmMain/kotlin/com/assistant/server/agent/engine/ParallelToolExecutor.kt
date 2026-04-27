package com.assistant.server.agent.engine

import com.assistant.agent.models.ToolCall
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.ToolRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory

/**
 * Coroutine-based batch executor for independent tool calls.
 * Uses [Semaphore] to throttle concurrent execution.
 * Individual failures don't cancel siblings — each async
 * catches its own errors. Results preserve input order.
 */
class ParallelToolExecutor(
    private val toolRegistry: ToolRegistry,
    private val maxConcurrency: Int = 5
) {
    private val logger = LoggerFactory.getLogger(
        ParallelToolExecutor::class.java
    )

    /**
     * Execute a batch of tool calls concurrently.
     * @return results in the same order as input calls
     */
    suspend fun executeBatch(
        calls: List<ToolCall>
    ): List<ToolResult> {
        if (calls.isEmpty()) return emptyList()
        val batchStart = System.currentTimeMillis()
        val semaphore = Semaphore(maxConcurrency)
        val results = executeAll(calls, semaphore)
        logBatchCompletion(calls.size, batchStart)
        return results
    }

    private suspend fun executeAll(
        calls: List<ToolCall>,
        semaphore: Semaphore
    ): List<ToolResult> = coroutineScope {
        calls.map { call ->
            async { executeSingle(call, semaphore) }
        }.map { it.await() }
    }

    private suspend fun executeSingle(
        call: ToolCall,
        semaphore: Semaphore
    ): ToolResult = semaphore.withPermit {
        val start = System.currentTimeMillis()
        try {
            val result = toolRegistry.invoke(call.toolName, call.params)
            logToolExecution(call.toolName, start, result.success)
            result
        } catch (e: Exception) {
            logToolFailure(call.toolName, start, e)
            failureResult(call.toolName, start, e)
        }
    }

    private fun logToolExecution(
        toolName: String,
        startMs: Long,
        success: Boolean
    ) {
        val elapsed = System.currentTimeMillis() - startMs
        logger.debug(
            "Parallel tool [{}] time={}ms success={}",
            toolName, elapsed, success
        )
    }

    private fun logToolFailure(
        toolName: String,
        startMs: Long,
        error: Exception
    ) {
        val elapsed = System.currentTimeMillis() - startMs
        logger.error(
            "Parallel tool [{}] FAILED time={}ms error={}",
            toolName, elapsed, error.message
        )
    }

    private fun logBatchCompletion(count: Int, startMs: Long) {
        val elapsed = System.currentTimeMillis() - startMs
        logger.info(
            "Batch of {} tools completed in {}ms",
            count, elapsed
        )
    }

    private fun failureResult(
        toolName: String,
        startMs: Long,
        error: Exception
    ) = ToolResult(
        toolName = toolName,
        executionTimeMs = System.currentTimeMillis() - startMs,
        success = false,
        errorType = error::class.simpleName ?: "Unknown",
        errorMessage = error.message ?: "Unknown error"
    )
}
