package com.assistant.agent.models

import kotlinx.serialization.Serializable

/**
 * Typed output envelope from agent execution.
 * Carries the result, execution metadata, and metrics.
 */
@Serializable
data class AgentOutput(
    val requestId: String,
    val agentType: String,
    val result: String,
    val metadata: Map<String, String> = emptyMap(),
    val reasoningLog: List<String> = emptyList(),
    val toolCallCount: Int = 0,
    val totalDurationMs: Long = 0,
    val status: AgentStatus = AgentStatus.SUCCESS,
    val metrics: AgentMetrics = AgentMetrics()
)

/**
 * Overall status of an agent execution.
 */
@Serializable
enum class AgentStatus {
    SUCCESS,
    PARTIAL,
    FAILED
}
