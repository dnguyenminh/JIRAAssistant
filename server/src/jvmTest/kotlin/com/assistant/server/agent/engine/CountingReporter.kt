package com.assistant.server.agent.engine

import com.assistant.agent.progress.ProgressReporter

/**
 * Test-only [ProgressReporter] that counts calls to each method.
 */
class CountingReporter : ProgressReporter {
    var phaseCount = 0
        private set
    var progressCount = 0
        private set
    var toolCallCount = 0
        private set

    override suspend fun reportPhase(
        phaseName: String,
        phaseIndex: Int,
        totalPhases: Int
    ) {
        phaseCount++
    }

    override suspend fun reportProgress(
        percent: Int,
        message: String
    ) {
        progressCount++
    }

    override suspend fun reportToolCall(
        toolName: String,
        status: String
    ) {
        toolCallCount++
    }
}
