package com.assistant.server.document

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.kb.KBRecord
import com.assistant.server.document.models.*

/**
 * Context building and background job creation helpers for [DeepCollector].
 *
 * Separated from [DeepCollectorPhases] to keep each file under 200 lines.
 *
 * Requirements: 5.3, 12.3, 12.5, 13.1
 */

/** Build EnrichedContext from traversal results (Req 5.3, 12.3). */
internal suspend fun DeepCollector.buildEnrichedContext(
    rootTicketId: String,
    graph: TicketGraph,
    commentMap: Map<String, List<FullComment>>,
    attachmentChunks: List<AttachmentChunkInfo>
): EnrichedContext {
    val rootNode = graph.nodes[rootTicketId]
        ?: throw IllegalStateException("Root ticket $rootTicketId not found in traversal graph")

    // Backward compat: populate mainTicket from KB if available (Req 12.5)
    val mainKBRecord = kbRepository.findByTicketId(rootTicketId)
        ?: buildFallbackKBRecord(rootTicketId, rootNode)

    // Backward compat: populate linkedTicketAnalyses from KB (Req 12.3)
    val linkedAnalyses = fetchLinkedKBRecords(graph, rootTicketId)

    val allTickets = graph.nodes.values.map { it.issue }
    val depthMap = graph.nodes.mapValues { it.value.depth }

    return EnrichedContext(
        mainTicket = mainKBRecord,
        linkedTicketAnalyses = linkedAnalyses,
        attachmentChunks = attachmentChunks,
        allTickets = allTickets,
        ticketRelationships = graph.edges,
        rawComments = commentMap,
        allAttachmentChunks = attachmentChunks,
        traversalMetadata = graph.metadata,
        ticketDepthMap = depthMap
    )
}

/** Fetch KBRecords for all non-root tickets that have KB entries (Req 12.3). */
private suspend fun DeepCollector.fetchLinkedKBRecords(
    graph: TicketGraph,
    rootTicketId: String
): List<KBRecord> {
    return graph.nodes.keys
        .filter { it != rootTicketId }
        .mapNotNull { ticketId ->
            try {
                kbRepository.findByTicketId(ticketId)
            } catch (e: Exception) {
                logger.warn("Failed to fetch KBRecord for {}: {}", ticketId, e.message)
                null
            }
        }
}

/** Create a minimal KBRecord when ticket has no KB entry (Req 12.5). */
private fun buildFallbackKBRecord(ticketId: String, node: TicketNode): KBRecord {
    return KBRecord(
        ticketId = ticketId,
        requirementSummary = node.issue.summary,
        evolutionHistory = emptyList(),
        scrumPoints = node.issue.storyPoints ?: 0.0,
        confidenceScore = 0.0,
        rationale = "",
        similarTicketRefs = emptyList(),
        timestamp = node.issue.updatedDate,
        businessSummary = node.issue.description.take(500)
    )
}

/** Extract project key from ticket ID (e.g. "ICL2-100" → "ICL2"). */
private fun extractProjectKey(ticketId: String): String =
    ticketId.substringBeforeLast('-')

/** Create background jobs for missing KB records and unprocessed attachments. */
internal suspend fun DeepCollector.createBackgroundJobs(
    rootTicketId: String,
    graph: TicketGraph
) {
    val missingKB = findMissingKBTickets(graph, rootTicketId)
    if (missingKB.isNotEmpty()) {
        logger.info("Creating background jobs for {} tickets missing KB records", missingKB.size)
    }
    collectionJobManager.createJobs(rootTicketId, graph, missingKB, emptyList())
}

/** Find tickets in the graph that don't have KBRecords. */
private suspend fun DeepCollector.findMissingKBTickets(
    graph: TicketGraph,
    rootTicketId: String
): List<String> {
    return graph.nodes.keys
        .filter { it != rootTicketId }
        .filter { ticketId ->
            try {
                kbRepository.findByTicketId(ticketId) == null
            } catch (_: Exception) {
                true
            }
        }
}
