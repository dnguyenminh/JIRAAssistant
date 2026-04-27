package com.assistant.server.analysis

import org.slf4j.LoggerFactory

/**
 * Tracks Map-Reduce pipeline progress via callback interface.
 *
 * Phase mapping:
 * - TRAVERSAL: 0-20%
 * - MAP: 20-80% (incremental per batch)
 * - REDUCE: 80-95%
 * - PARSING: 95-100%
 *
 * Requirements: 5.1-5.5
 */
class ProgressTracker(
    private val callback: ((phase: String, detail: String, progressPercent: Int) -> Unit)? = null
) {

    private val logger = LoggerFactory.getLogger(ProgressTracker::class.java)

    fun onTraversalStart(ticketId: String, config: String) {
        logger.info("TRAVERSAL started for ticket {} with config: {}", ticketId, config)
        callback?.invoke("TRAVERSAL", "Starting traversal for $ticketId", 0)
    }

    fun onTraversalComplete(totalTickets: Int) {
        logger.info("TRAVERSAL complete: {} tickets collected", totalTickets)
        callback?.invoke("TRAVERSAL", "Traversal complete: $totalTickets tickets", 20)
    }

    fun onMapStart(totalBatches: Int) {
        logger.info("MAP phase started: {} batches", totalBatches)
        callback?.invoke("MAP", "Starting map phase: $totalBatches batches", 20)
    }

    fun onBatchComplete(batchIndex: Int, totalBatches: Int, ticketCount: Int) {
        val completedBatches = batchIndex + 1
        val progress = calculateMapProgress(completedBatches, totalBatches)
        logger.info("MAP batch {}/{} complete: {} tickets", completedBatches, totalBatches, ticketCount)
        callback?.invoke(
            "MAP",
            "Processing batch $completedBatches/$totalBatches: $ticketCount tickets",
            progress
        )
    }

    fun onBatchFailed(batchIndex: Int, totalBatches: Int, error: String) {
        val completedBatches = batchIndex + 1
        val progress = calculateMapProgress(completedBatches, totalBatches)
        logger.info("MAP batch {}/{} failed: {}", completedBatches, totalBatches, error)
        callback?.invoke(
            "MAP",
            "Batch $completedBatches/$totalBatches failed: $error",
            progress
        )
    }

    fun onReduceStart(summaryCount: Int) {
        logger.info("REDUCE phase started: combining {} batch summaries", summaryCount)
        callback?.invoke("REDUCE", "Combining $summaryCount batch summaries", 80)
    }

    fun onReduceComplete() {
        logger.info("REDUCE phase complete")
        callback?.invoke("REDUCE", "Reduce phase complete", 95)
    }

    fun onParsingStart() {
        logger.info("PARSING phase started")
        callback?.invoke("PARSING", "Parsing final result", 95)
    }

    fun onComplete(totalTimeMs: Long) {
        logger.info("Map-Reduce pipeline complete in {}ms", totalTimeMs)
        callback?.invoke("PARSING", "Analysis complete", 100)
    }

    companion object {
        /**
         * Calculate map phase progress percentage.
         * Formula: 20 + (completedBatches * 60 / totalBatches), clamped to 20..80.
         */
        fun calculateMapProgress(completedBatches: Int, totalBatches: Int): Int {
            if (totalBatches <= 0) return 20
            return (20 + (completedBatches * 60 / totalBatches)).coerceIn(20, 80)
        }
    }
}
