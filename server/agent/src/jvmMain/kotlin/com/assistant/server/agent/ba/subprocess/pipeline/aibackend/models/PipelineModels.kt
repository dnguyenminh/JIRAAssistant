package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.models.ToolDescriptor

/**
 * Identifies a pipeline phase.
 */
enum class PhaseId {
    DATA_COLLECTION,   // Phase 1
    BRD_WRITING,       // Phase 2
    DIAGRAM_GENERATION // Phase 3
}

/**
 * Configuration for a single pipeline phase.
 * Extends AgenticLoopConfig concept with phase-specific settings.
 */
data class PhaseConfig(
    val phaseId: PhaseId,
    val ticketId: String,
    val docType: String,
    val maxToolCalls: Int,
    val timeoutSeconds: Int
)

/**
 * Result of a single pipeline phase execution.
 * Compatible with AgenticLoopResult for aggregation.
 */
data class PhaseResult(
    val phaseId: PhaseId,
    val output: String,
    val toolCallLog: List<ToolCallLogEntry>,
    val toolCallsExecuted: Int,
    val toolCallsFailed: Int,
    val durationMs: Long,
    val success: Boolean,
    val timedOut: Boolean
)

/**
 * Top-level pipeline configuration with per-phase defaults.
 */
data class PipelineConfig(
    val ticketId: String,
    val docType: String,
    val allTools: List<ToolDescriptor>,
    val phase1MaxToolCalls: Int = 25,
    val phase1TimeoutSeconds: Int = 180,
    val phase2MaxToolCalls: Int = 15,
    val phase2TimeoutSeconds: Int = 120,
    val phase3MaxToolCalls: Int = 10,
    val phase3TimeoutSeconds: Int = 90,
    val enableParallelPhases: Boolean = true,
    val maxRetries: Int = 1
)
