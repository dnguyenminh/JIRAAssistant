package com.assistant.server.agent.progress

import com.assistant.agent.progress.ProgressReporter

/**
 * Silent [ProgressReporter] that discards all events.
 * Used when no progress reporting is needed (e.g., tests,
 * background agents without UI).
 */
class NoOpProgressReporter : ProgressReporter {

    override suspend fun reportPhase(
        phaseName: String,
        phaseIndex: Int,
        totalPhases: Int
    ) { /* no-op */ }

    override suspend fun reportProgress(
        percent: Int,
        message: String
    ) { /* no-op */ }

    override suspend fun reportToolCall(
        toolName: String,
        status: String
    ) { /* no-op */ }
}
