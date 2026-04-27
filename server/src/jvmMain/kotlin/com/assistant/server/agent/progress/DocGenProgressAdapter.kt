package com.assistant.server.agent.progress

import com.assistant.agent.progress.ProgressReporter
import com.assistant.server.jobs.DocGenProgressTracker
import org.slf4j.LoggerFactory

/**
 * Adapts the agent framework's [ProgressReporter] to the existing
 * [DocGenProgressTracker]. Maps agent phase names to document
 * generation labels: AGGREGATING_DATA, GENERATING_DOCUMENT,
 * PARSING_RESPONSE, SAVING.
 */
class DocGenProgressAdapter(
    private val tracker: DocGenProgressTracker
) : ProgressReporter {

    private val logger = LoggerFactory.getLogger(
        DocGenProgressAdapter::class.java
    )

    override suspend fun reportPhase(
        phaseName: String,
        phaseIndex: Int,
        totalPhases: Int
    ) {
        val label = mapPhaseToLabel(phaseIndex, totalPhases)
        val percent = computePercent(phaseIndex, totalPhases)
        tracker.updateProgress(percent, label)
        logger.debug(
            "Phase '{}' ({}/{}) → {} at {}%",
            phaseName, phaseIndex + 1, totalPhases, label, percent
        )
    }

    override suspend fun reportProgress(
        percent: Int,
        message: String
    ) {
        tracker.updateProgress(percent.coerceIn(0, 100), message)
    }

    override suspend fun reportToolCall(
        toolName: String,
        status: String
    ) {
        logger.debug("Tool call: {} → {}", toolName, status)
    }

    private fun mapPhaseToLabel(
        phaseIndex: Int,
        totalPhases: Int
    ): String {
        if (totalPhases <= 0) return "GENERATING_DOCUMENT"
        val ratio = phaseIndex.toDouble() / totalPhases
        return when {
            ratio < 0.25 -> "AGGREGATING_DATA"
            ratio < 0.75 -> "GENERATING_DOCUMENT"
            ratio < 0.90 -> "PARSING_RESPONSE"
            else -> "SAVING"
        }
    }

    private fun computePercent(
        phaseIndex: Int,
        totalPhases: Int
    ): Int {
        if (totalPhases <= 0) return 0
        return ((phaseIndex + 1) * 100 / totalPhases)
            .coerceIn(0, 100)
    }
}
