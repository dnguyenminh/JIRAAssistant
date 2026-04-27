package com.assistant.agent.models

import kotlinx.serialization.Serializable

/**
 * Execution statistics for an agent run.
 * Included in every AgentOutput for performance inspection.
 * All fields default to 0.
 */
@Serializable
data class AgentMetrics(
    val totalDurationMs: Long = 0,
    val phaseCount: Int = 0,
    val toolCallCount: Int = 0,
    val parallelBatchCount: Int = 0,
    val memoryTotalChars: Int = 0,
    val outputSizeChars: Int = 0,
    val retryCount: Int = 0,
    val errorCount: Int = 0
)
