package com.assistant.server.analysis.models

import com.assistant.server.document.models.TicketNode
import kotlinx.serialization.Serializable

/**
 * Metadata for a single batch created by [BatchStrategy][com.assistant.server.analysis.BatchStrategy].
 *
 * Contains the ticket nodes assigned to this batch plus context info
 * used by [BatchPromptBuilder][com.assistant.server.analysis.BatchPromptBuilder]
 * to construct the Map-phase AI prompt.
 *
 * Invariants enforced by BatchStrategy:
 * - Every ticket appears in exactly one batch (no duplicates, no loss)
 * - No empty batches ([tickets] is never empty)
 * - Each batch has at most `maxBatchSize` tickets
 * - Root ticket is always in batch 0
 *
 * Requirements: 2.1-2.6, 3.7
 */
@Serializable
data class BatchInfo(
    /** Batch index (0-based). Batch 0 always contains the root ticket. */
    val batchIndex: Int,
    /** Total number of batches in this analysis run. */
    val totalBatches: Int,
    /** Ticket nodes assigned to this batch. */
    val tickets: List<TicketNode>,
    /** Depth levels represented in this batch (e.g. [0, 1] for batch 0). */
    val depthLevels: List<Int>
) {
    /** Convenience accessor — ticket IDs in this batch. */
    val ticketIds: List<String> get() = tickets.map { it.ticketId }
}
