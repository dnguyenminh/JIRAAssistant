package com.assistant.server.agent.ba.subprocess.pipeline.models

/**
 * Configuration for a single pipeline step, defining its name,
 * per-turn timeout, and progress reporting range.
 */
data class PipelineStepConfig(
    val name: String,
    val timeoutSeconds: Int,
    val progressRange: IntRange
) {
    companion object {
        val DATA_COLLECTION = PipelineStepConfig("data-collection", 30, 5..20)
        val ANALYSIS = PipelineStepConfig("analysis", 60, 25..40)
        val WRITING = PipelineStepConfig("writing", 120, 45..70)
        val REVIEW = PipelineStepConfig("review", 120, 75..85)
    }
}
