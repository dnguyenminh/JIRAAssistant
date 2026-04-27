package com.assistant.server.document

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.models.FullComment
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.models.TraversalConfig

/**
 * Logging helpers for [DeepCollector].
 *
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6
 */

/** Log traversal config at start of each traversal (Req 7.5, 11.1). */
internal fun DeepCollector.logTraversalStart(ticketId: String, config: TraversalConfig) {
    logger.info(
        "Deep collection started for {} with config: max_depth={}, max_tickets={}, " +
            "project_scope={}, total_timeout_ms={}, max_comments_per_ticket={}",
        ticketId, config.maxDepth, config.maxTickets,
        config.projectScope.ifEmpty { listOf("ALL") },
        config.totalTimeoutMs, config.maxCommentsPerTicket
    )
}

/** Log summary when complete (Req 1.8, 11.3). */
internal fun DeepCollector.logTraversalComplete(
    ticketId: String,
    graph: TicketGraph,
    commentMap: Map<String, List<FullComment>>,
    attachmentChunks: List<AttachmentChunkInfo>
) {
    val totalComments = commentMap.values.sumOf { it.size }
    logger.info(
        "Deep collection completed for {}: {} tickets, {} comments, " +
            "{} attachment chunks, max_depth_reached={}, elapsed={}ms",
        ticketId,
        graph.metadata.totalFetched,
        totalComments,
        attachmentChunks.size,
        graph.metadata.maxDepthReached,
        graph.metadata.traversalTimeMs
    )

    logSkippedTickets(graph)
    logPermissionDenied(graph)
}

/** Log tickets discovered but not fetched due to limits (Req 11.6). */
private fun DeepCollector.logSkippedTickets(graph: TicketGraph) {
    if (graph.metadata.skippedTicketIds.isNotEmpty()) {
        logger.warn(
            "Skipped {} tickets due to limits: {}",
            graph.metadata.totalSkipped,
            graph.metadata.skippedTicketIds.take(20)
        )
    }
}

/** Log permission-denied count (Req 1.10). */
private fun DeepCollector.logPermissionDenied(graph: TicketGraph) {
    if (graph.metadata.permissionDeniedCount > 0) {
        logger.warn(
            "{} tickets skipped due to insufficient permissions (403)",
            graph.metadata.permissionDeniedCount
        )
    }
}
