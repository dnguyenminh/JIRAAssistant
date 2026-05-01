package com.assistant.server.agent.ba.models

import kotlinx.serialization.Serializable

/**
 * Execution metrics for the BA Agent pipeline.
 * Requirements: 10.1, 10.2
 */
@Serializable
data class AgentPipelineMetrics(
    val thinkingLoopTimeMs: Long,
    val iterationCount: Int,
    val toolCallCount: Int,
    val parallelBatchCount: Int,
    val totalDataCollectedChars: Int,
    val masterPromptSizeChars: Int,
    val compressionRatio: Double,
    val memoryCompletenessAtSynthesis: Map<String, Double>,
    val phaseBreakdown: List<PhaseMetric>
)

/**
 * Per-phase execution metrics within the BA Agent pipeline.
 * Requirements: 10.2
 */
@Serializable
data class PhaseMetric(
    val phaseName: String,
    val durationMs: Long,
    val toolCallsInPhase: Int,
    val memoryCompletenessBefore: Map<String, Double>,
    val memoryCompletenessAfter: Map<String, Double>
)
