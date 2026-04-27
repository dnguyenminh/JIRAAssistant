package com.assistant.server.agent.ba.integration

import com.assistant.agent.progress.ProgressReporter
import com.assistant.server.jobs.DocGenProgressTracker
import org.slf4j.LoggerFactory

/**
 * BA-specific progress adapter mapping agent phase names to
 * existing DocGenProgressTracker labels.
 *
 * Phase mapping:
 * - collect, expand, visualize → AGGREGATING_DATA
 * - synthesize → GENERATING_DOCUMENT
 *
 * Requirements: 9.7
 */
class BAProgressAdapter(
    private val tracker: DocGenProgressTracker
) : ProgressReporter {

    private val logger = LoggerFactory.getLogger(
        BAProgressAdapter::class.java
    )

    override suspend fun reportPhase(
        phaseName: String,
        phaseIndex: Int,
        totalPhases: Int
    ) {
        val label = mapPhaseToLabel(phaseName)
        val percent = computePercent(phaseIndex, totalPhases)
        tracker.updateProgress(percent, label)
        logger.debug(
            "BA phase '{}' ({}/{}) → {} at {}%",
            phaseName, phaseIndex + 1, totalPhases, label, percent
        )
    }

    override suspend fun reportProgress(
        percent: Int, message: String
    ) {
        tracker.updateProgress(percent.coerceIn(0, 100), message)
    }

    override suspend fun reportToolCall(
        toolName: String, status: String
    ) {
        logger.debug("BA tool call: {} → {}", toolName, status)
    }

    private fun computePercent(
        phaseIndex: Int, totalPhases: Int
    ): Int {
        if (totalPhases <= 0) return 0
        return ((phaseIndex + 1) * 100 / totalPhases)
            .coerceIn(0, 100)
    }

    companion object {
        /**
         * Map BA Agent phase name to existing progress label.
         * Never returns null — unknown phases default to
         * AGGREGATING_DATA.
         */
        fun mapPhaseToLabel(phaseName: String): String =
            when (phaseName) {
                "collect", "expand", "visualize" ->
                    "AGGREGATING_DATA"
                "synthesize" -> "GENERATING_DOCUMENT"
                else -> "AGGREGATING_DATA"
            }
    }
}
