package com.assistant.server.document

import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.kb.KBRecord
import com.assistant.server.document.collection.AttachmentContentCollector
import com.assistant.server.document.collection.CommentCollector
import com.assistant.server.document.models.*
import com.assistant.server.document.traversal.KBFirstTicketFetcher
import com.assistant.server.document.traversal.TraversalEngine

/**
 * Internal execution logic for [DeepCollector], split out to keep
 * each file under 200 lines.
 *
 * Phases:
 * 0. Rate limit + cache check
 * 1. BFS Traversal (progress 5-25%)
 * 2. Collect comments + attachments (progress 25-30%)
 * 3. Build EnrichedContext + create background jobs
 */
internal suspend fun DeepCollector.executeCollection(
    ticketId: String,
    progressCallback: ((Int, String) -> Unit)?
): GenerationContext {
    val config = configProvider().validated()
    logTraversalStart(ticketId, config)
    progressCallback?.invoke(0, "Initializing deep collection")

    // Phase 0: Rate limit + cache check (Req 15, 16.1)
    rateLimiter.check("system")
    val cached = traversalCache.get(ticketId, config.cacheTtlMinutes)
    val graph = if (cached != null) {
        logger.info("Reusing cached TicketGraph for {}", ticketId)
        progressCallback?.invoke(25, "Using cached traversal")
        cached
    } else {
        // Phase 1: BFS Traversal (Req 1.1-1.10)
        progressCallback?.invoke(5, "Traversing ticket graph")
        val result = buildTraversalEngine(config).traverse(ticketId)
        traversalCache.put(ticketId, result)
        progressCallback?.invoke(25, "Traversal complete")
        result
    }

    // Phase 2: Collect comments + attachments (Req 3.1-3.7, 4.1-4.6)
    progressCallback?.invoke(25, "Collecting comments and attachments")
    val commentMap = collectAllComments(graph, config)
    val attachmentResult = collectAllAttachments(graph)
    progressCallback?.invoke(30, "Collection complete")

    // Phase 3: Build EnrichedContext + create background jobs
    val context = buildEnrichedContext(ticketId, graph, commentMap, attachmentResult)
    createBackgroundJobs(ticketId, graph)
    rateLimiter.record("system")
    logTraversalComplete(ticketId, graph, commentMap, attachmentResult)
    return context
}

/** Create a TraversalEngine with the current JiraClient and config. */
private fun DeepCollector.buildTraversalEngine(config: TraversalConfig): TraversalEngine {
    val sectionClassifier = getSectionClassifier()
    val fetcher = KBFirstTicketFetcher(jiraClientProvider(), sectionClassifier, kbRepository)
    return TraversalEngine(fetcher, config, jiraApiSemaphore)
}

/** Obtain SectionClassifier — uses a simple default implementation. */
private fun DeepCollector.getSectionClassifier(): SectionClassifier {
    return com.assistant.ai.deepanalysis.SectionClassifierImpl()
}

/** Collect comments for all tickets in the graph (Req 3.6). */
private suspend fun DeepCollector.collectAllComments(
    graph: TicketGraph,
    config: TraversalConfig
): Map<String, List<FullComment>> {
    val collector = CommentCollector(jiraClientProvider(), config)
    val result = mutableMapOf<String, List<FullComment>>()
    for (ticketId in graph.nodes.keys) {
        val comments = collector.collectAll(ticketId)
        if (comments.comments.isNotEmpty()) {
            result[ticketId] = comments.comments
        }
    }
    return result
}

/** Collect attachment chunks for all tickets in the graph (Req 4.2). */
private suspend fun DeepCollector.collectAllAttachments(
    graph: TicketGraph
): List<AttachmentChunkInfo> {
    val collector = AttachmentContentCollector(
        vectorStore,
        com.assistant.server.document.extraction.TicketIdExtractor
    )
    val allChunks = mutableListOf<AttachmentChunkInfo>()
    val seenAttachments = mutableSetOf<String>()
    for (ticketId in graph.nodes.keys) {
        val result = collector.collectAll(ticketId)
        for (chunk in result.chunks) {
            val key = "${chunk.attachmentId}:${chunk.chunkIndex}"
            if (seenAttachments.add(key)) {
                allChunks.add(AttachmentChunkInfo(chunk.filename, chunk.chunkText))
            }
        }
    }
    return allChunks
}
