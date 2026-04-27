package com.assistant.agent.models

import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of an agent's execution progress.
 * Enables inspection, pause/resume, and recovery from restarts.
 */
@Serializable
data class AgentState(
    val agentId: String,
    val agentType: String,
    val currentPhase: String = "",
    val phaseIndex: Int = 0,
    val iterationCount: Int = 0,
    val memorySnapshot: String = "",
    val toolCallHistory: List<ToolCallRecord> = emptyList(),
    val reasoningLog: List<String> = emptyList(),
    val elapsedTimeMs: Long = 0,
    val status: AgentStateStatus = AgentStateStatus.RUNNING
) {
    companion object {
        /** Maximum number of reasoning log entries retained. */
        const val MAX_REASONING_LOG_ENTRIES = 100
    }
}

/**
 * Status of an agent's execution lifecycle.
 */
@Serializable
enum class AgentStateStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}
