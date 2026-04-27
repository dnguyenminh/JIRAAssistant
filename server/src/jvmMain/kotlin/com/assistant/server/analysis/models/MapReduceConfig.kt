package com.assistant.server.analysis.models

import kotlinx.serialization.Serializable

/**
 * Configuration for the Map-Reduce analysis pipeline.
 *
 * Stored in application settings (same mechanism as TraversalConfig).
 * Use [validated] to clamp values into allowed ranges.
 *
 * Requirements: 2.7, 6.3, 9.1-9.4
 */
@Serializable
data class MapReduceConfig(
    /** Enable/disable map-reduce pipeline. When false, always use single-prompt. */
    val mapReduceEnabled: Boolean = true,
    /** Max tickets per batch. Clamped to 5..100. */
    val maxBatchSize: Int = 30,
    /** Max concurrent batch AI calls. Clamped to 1..5. */
    val maxConcurrentBatches: Int = 3,
    /** Ticket count threshold to activate map-reduce. Clamped to 50..1000. */
    val mapReduceThreshold: Int = 200,
    /** Timeout per batch AI call in milliseconds. */
    val batchTimeoutMs: Long = 120_000,
    /** Timeout for reduce AI call in milliseconds. */
    val reduceTimeoutMs: Long = 180_000,
    /** Max prompt size per batch in characters. */
    val maxPromptChars: Int = 100_000
) {

    /**
     * Returns a copy with all clamped fields coerced into valid ranges.
     *
     * - [maxBatchSize]: 5..100
     * - [maxConcurrentBatches]: 1..5
     * - [mapReduceThreshold]: 50..1000
     */
    fun validated(): MapReduceConfig = copy(
        maxBatchSize = maxBatchSize.coerceIn(5, 100),
        maxConcurrentBatches = maxConcurrentBatches.coerceIn(1, 5),
        mapReduceThreshold = mapReduceThreshold.coerceIn(50, 1000)
    )
}
