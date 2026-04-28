package com.assistant.server.attachment

import com.assistant.scan.ScanLogRepository
import com.assistant.server.attachment.models.TicketAttachmentGroup
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.models.TicketNode
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Orchestrates attachment processing for all tickets in a TicketGraph.
 * Reuses AttachmentPipeline for each ticket's attachments.
 * Requirements: 1.1-1.5, 3.1, 3.4, 4.1-4.5, 5.1, 5.3, 5.4, 6.1, 6.3, 6.4, 7.5, 8.1-8.4
 */
class LinkedAttachmentProcessor(
    private val attachmentPipeline: AttachmentPipeline,
    private val vectorStore: VectorStore,
    private val scanLogRepository: ScanLogRepository
) {
    private val log = LinkedAttachmentLogger(
        LoggerFactory.getLogger(LinkedAttachmentProcessor::class.java),
        scanLogRepository
    )

    companion object {
        private const val YIELD_DELAY_MS = 100L
    }

    /**
     * Process attachments from all linked tickets in the graph.
     * Top-level catch-all: never propagates exceptions to caller.
     * Requirements: 2.1, 4.1-4.4, 5.1, 5.3, 5.4, 6.1, 6.3, 6.4, 7.5
     */
    suspend fun processLinkedAttachments(
        graph: TicketGraph,
        rootTicketId: String,
        asBackground: Boolean = false,
        timeoutMs: Long = 120_000
    ) {
        try {
            doProcess(graph, rootTicketId, timeoutMs)
        } catch (e: Exception) {
            log.logTopLevelError(rootTicketId, e.message)
        }
    }

    /**
     * Collect attachment groups from all nodes in the TicketGraph.
     * Dedup by attachmentId, KB-First dedup via VectorStore, sort by relevance.
     * Requirements: 1.1-1.5, 3.1, 3.4
     */
    internal suspend fun collectAttachments(
        graph: TicketGraph,
        rootTicketId: String
    ): List<TicketAttachmentGroup> {
        val seenAttachmentIds = mutableSetOf<String>()
        val groups = graph.nodes.values
            .mapNotNull { node -> buildGroup(node, seenAttachmentIds) }
            .filter { it.attachments.isNotEmpty() }

        val sorted = sortGroups(groups, rootTicketId)
        log.logCollectionSummary(rootTicketId, graph, sorted, seenAttachmentIds)
        return sorted
    }

    // --- Internal helpers ---

    private suspend fun buildGroup(
        node: TicketNode,
        seenIds: MutableSet<String>
    ): TicketAttachmentGroup? {
        val dedupedAttachments = node.issue.attachments
            .filter { it.id.isNotBlank() && seenIds.add(it.id) }
            .filter { !vectorStore.existsByAttachmentId(it.id) }
            .map { it.toJiraAttachment() }

        if (dedupedAttachments.isEmpty()) return null
        return TicketAttachmentGroup(
            ticketId = node.ticketId,
            projectKey = node.ticketId.substringBefore("-"),
            depth = node.depth,
            relevanceScore = node.relevanceScore,
            attachments = dedupedAttachments
        )
    }

    private fun sortGroups(
        groups: List<TicketAttachmentGroup>,
        rootTicketId: String
    ): List<TicketAttachmentGroup> {
        return groups.sortedWith(
            compareBy<TicketAttachmentGroup> { if (it.ticketId == rootTicketId) 0 else 1 }
                .thenBy { it.depth }
                .thenByDescending { it.relevanceScore }
        )
    }

    private suspend fun doProcess(graph: TicketGraph, rootTicketId: String, timeoutMs: Long) {
        if (graph.nodes.size <= 1) {
            log.logSingleNodeSkip(rootTicketId)
            return
        }
        val groups = collectAttachments(graph, rootTicketId)
        if (groups.isEmpty()) {
            log.logNoAttachments(rootTicketId)
            return
        }
        log.logStart(rootTicketId, groups.size, groups.sumOf { it.attachments.size })
        processSequentially(groups, rootTicketId, timeoutMs)
    }

    private suspend fun processSequentially(
        groups: List<TicketAttachmentGroup>,
        rootTicketId: String,
        timeoutMs: Long
    ) {
        val startTime = System.currentTimeMillis()
        var processedTickets = 0
        var totalChunks = 0

        for (group in groups) {
            if (isTimeoutExceeded(startTime, timeoutMs, processedTickets, groups.size)) break
            val chunks = processOneGroup(group)
            totalChunks += chunks
            processedTickets++
            delay(YIELD_DELAY_MS)
        }
        log.logCompletion(rootTicketId, totalChunks, processedTickets, startTime)
    }

    private fun isTimeoutExceeded(
        startTime: Long, timeoutMs: Long, processed: Int, total: Int
    ): Boolean {
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed <= timeoutMs) return false
        log.logTimeout(elapsed, processed, total)
        return true
    }

    /**
     * Process one ticket's attachments with error isolation.
     * Requirements: 4.1-4.4, 6.1
     */
    private suspend fun processOneGroup(group: TicketAttachmentGroup): Int {
        log.logTicketDebug(group)
        return try {
            val chunks = attachmentPipeline.processAttachments(
                group.projectKey, group.ticketId, group.attachments
            )
            log.logTicketProcessed(group, chunks)
            chunks
        } catch (e: Exception) {
            log.logTicketError(group.ticketId, e.message ?: "unknown error")
            log.logTicketFailed(group, e.message ?: "unknown error")
            0
        }
    }
}
