package com.assistant.agent.progress

/**
 * Integration point between the agent framework and the
 * existing progress tracking / frontend progress UI.
 */
interface ProgressReporter {

    /** Report a phase transition. */
    suspend fun reportPhase(
        phaseName: String,
        phaseIndex: Int,
        totalPhases: Int
    )

    /** Report incremental progress within a phase. */
    suspend fun reportProgress(percent: Int, message: String)

    /** Report a tool invocation with its status. */
    suspend fun reportToolCall(toolName: String, status: String)
}
