package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Metadata about the Map-Reduce pipeline execution.
 *
 * Tracks batch processing statistics and phase timing.
 * Stored in [AnalysisMetadata.mapReduceInfo] for downstream consumers
 * to understand how the analysis was produced.
 *
 * This is the shared-module version — the server module's
 * [com.assistant.server.analysis.models.MapReduceInfo] mirrors this structure.
 *
 * Requirements: 4.5
 */
@Serializable
data class MapReduceInfo(
    /** Total batches created by BatchStrategy. */
    val totalBatches: Int = 0,
    /** Batches that completed successfully. */
    val successfulBatches: Int = 0,
    /** Batches that failed after all retries. */
    val failedBatches: Int = 0,
    /** Total tickets analyzed across all successful batches. */
    val totalTicketsAnalyzed: Int = 0,
    /** Time spent in map phase (milliseconds). */
    val mapPhaseTimeMs: Long = 0,
    /** Time spent in reduce phase (milliseconds). */
    val reducePhaseTimeMs: Long = 0,
    /** Whether reduce was skipped (single batch optimization). */
    val reduceSkipped: Boolean = false
)
