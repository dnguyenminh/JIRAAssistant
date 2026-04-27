package com.assistant.server.agent.ba.subprocess.pipeline.models

/**
 * Reason why the pipeline loop was stopped.
 */
enum class StopReason {
    QUALITY_PASSED,
    LOOP_DETECTED,
    CONSECUTIVE_FAILURES,
    ERROR
}

/**
 * Decision from [PipelineStopCondition] on whether to stop the pipeline.
 *
 * When [shouldStop] is true, [reason] indicates why and [message]
 * provides a human-readable explanation for logging.
 */
data class StopDecision(
    val shouldStop: Boolean,
    val reason: StopReason? = null,
    val message: String = ""
)
