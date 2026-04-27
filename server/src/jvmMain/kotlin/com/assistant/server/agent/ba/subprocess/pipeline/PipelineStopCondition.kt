package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.server.agent.ba.subprocess.pipeline.models.StepResponse
import com.assistant.server.agent.ba.subprocess.pipeline.models.StopDecision
import com.assistant.server.agent.ba.subprocess.pipeline.models.StopReason

/**
 * Evaluates whether the pipeline loop should stop based on
 * quality checks, consecutive failures, and loop detection.
 *
 * Requirements: 2.2, 2.3, 2.4
 */
class PipelineStopCondition {

    companion object {
        const val SIMILARITY_THRESHOLD = 0.90
        const val MAX_CONSECUTIVE_FAILURES = 3
    }

    fun evaluate(
        currentResponse: StepResponse,
        previousResponses: List<StepResponse>,
        qualityPassed: Boolean,
        consecutiveFailures: Int
    ): StopDecision {
        if (qualityPassed) {
            return StopDecision(
                shouldStop = true,
                reason = StopReason.QUALITY_PASSED,
                message = "Document quality check passed"
            )
        }
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            return StopDecision(
                shouldStop = true,
                reason = StopReason.CONSECUTIVE_FAILURES,
                message = "$MAX_CONSECUTIVE_FAILURES consecutive failures"
            )
        }
        if (currentResponse.isEmpty || currentResponse.timedOut) {
            return StopDecision(shouldStop = false)
        }
        return checkLoopDetection(currentResponse, previousResponses)
    }

    private fun checkLoopDetection(
        current: StepResponse,
        previous: List<StepResponse>
    ): StopDecision {
        val lastNonEmpty = previous.lastOrNull {
            it.content.isNotBlank()
        } ?: return StopDecision(shouldStop = false)

        val similarity = jaccardSimilarity(
            current.content, lastNonEmpty.content
        )
        if (similarity > SIMILARITY_THRESHOLD) {
            return StopDecision(
                shouldStop = true,
                reason = StopReason.LOOP_DETECTED,
                message = "Response similarity ${String.format("%.2f", similarity)} > ${SIMILARITY_THRESHOLD}"
            )
        }
        return StopDecision(shouldStop = false)
    }
}

/**
 * Jaccard similarity between two strings based on word sets.
 * Internal visibility so tests can verify directly.
 */
internal fun jaccardSimilarity(a: String, b: String): Double {
    if (a.isBlank() && b.isBlank()) return 1.0
    if (a.isBlank() || b.isBlank()) return 0.0
    val setA = a.lowercase().split("\\s+".toRegex()).toSet()
    val setB = b.lowercase().split("\\s+".toRegex()).toSet()
    val intersection = setA.intersect(setB).size
    val union = setA.union(setB).size
    return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
}
