package com.assistant.server.analysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.BatchInfo
import com.assistant.server.document.models.TicketEdge
import com.assistant.server.document.models.TicketNode

/**
 * Builds AI prompts for individual batches in the Map phase.
 *
 * Each prompt contains:
 * - Root ticket summary + description (context for every batch)
 * - Full ticket data for all tickets in the batch
 * - Relationship info between tickets in the batch
 * - Batch metadata (index, total, depth levels)
 * - JSON output schema for [BatchSummary][com.assistant.server.analysis.models.BatchSummary]
 *
 * Prompt size is capped at [maxPromptChars]. When exceeded, truncates
 * comments first, then attachment content, then ticket descriptions.
 *
 * Requirements: 3.1, 3.2, 3.6, 3.7
 */
class BatchPromptBuilder(private val maxPromptChars: Int = 100_000) {

    /**
     * Build prompt for a single batch.
     *
     * @param batchInfo Batch metadata (index, total batches, ticket nodes)
     * @param rootTicket Root ticket's [StructuredTicketContent]
     * @param edges Edges relevant to tickets in this batch
     * @return Prompt string with length ≤ [maxPromptChars]
     */
    fun buildPrompt(
        batchInfo: BatchInfo,
        rootTicket: StructuredTicketContent,
        edges: List<TicketEdge>
    ): String {
        val batchEdges = filterBatchEdges(batchInfo, edges)
        val full = buildFullPrompt(batchInfo, rootTicket, batchEdges)
        if (full.length <= maxPromptChars) return full
        return buildTruncatedPrompt(batchInfo, rootTicket, batchEdges)
    }

    /** Filter edges to only those connecting tickets in this batch. */
    private fun filterBatchEdges(
        batchInfo: BatchInfo,
        edges: List<TicketEdge>
    ): List<TicketEdge> {
        val ids = batchInfo.ticketIds.toSet()
        return edges.filter { it.sourceId in ids || it.targetId in ids }
    }

    /** Build the full prompt with all comments included. */
    private fun buildFullPrompt(
        batchInfo: BatchInfo,
        rootTicket: StructuredTicketContent,
        edges: List<TicketEdge>
    ): String = buildString {
        appendBatchSystemInstruction()
        appendRootContext(rootTicket)
        appendBatchMetadata(batchInfo)
        appendAllTickets(batchInfo.tickets, includeComments = true)
        appendRelationships(edges)
        appendBatchSummarySchema()
    }

    /** Build a truncated prompt: drop comments to fit maxPromptChars. */
    private fun buildTruncatedPrompt(
        batchInfo: BatchInfo,
        rootTicket: StructuredTicketContent,
        edges: List<TicketEdge>
    ): String {
        val prompt = buildString {
            appendBatchSystemInstruction()
            appendRootContext(rootTicket)
            appendBatchMetadata(batchInfo)
            appendAllTickets(batchInfo.tickets, includeComments = false)
            appendRelationships(edges)
            appendBatchSummarySchema()
        }
        return truncateToLimit(prompt)
    }

    /** Append all ticket sections. */
    private fun StringBuilder.appendAllTickets(
        tickets: List<TicketNode>,
        includeComments: Boolean
    ) {
        appendLine("=== TICKETS ===")
        tickets.forEach { appendTicketSection(it, includeComments) }
        appendLine()
    }

    /** Hard-truncate to maxPromptChars if still over limit. */
    private fun truncateToLimit(prompt: String): String {
        if (prompt.length <= maxPromptChars) return prompt
        return prompt.substring(0, maxPromptChars)
    }
}
