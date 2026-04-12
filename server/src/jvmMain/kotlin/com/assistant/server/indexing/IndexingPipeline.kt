package com.assistant.server.indexing

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.ChunkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Indexes ticket data, graph relationships, and cluster summaries into VectorStore
 * for semantic search. Uses BatchEmbedder for batched embedding with retry.
 * Requirements: 12.1, 12.2, 12.4, 12.5, 13.1, 13.2, 13.4, 16.1–16.5
 */
class IndexingPipeline(
    private val embeddingService: EmbeddingService?,
    private val vectorStore: VectorStore,
    private val graphEngine: GraphEngine? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val batchEmbedder = BatchEmbedder(embeddingService, vectorStore)

    /** Index ticket descriptions. Skips already-indexed tickets. Req: 12.1, 12.2, 12.4 */
    suspend fun indexTickets(projectKey: String, tickets: List<TicketNode>) {
        logProgress("TICKET", 0, tickets.size)
        val items = buildTicketItems(tickets)
        val processed = batchEmbedder.embedAndSaveAll(items)
        logProgress("TICKET", processed, tickets.size)
    }

    /** Index graph relationships (edges). Req: 13.1, 13.2 */
    suspend fun indexRelationships(
        projectKey: String, edges: List<TicketEdge>, nodeMap: Map<String, TicketNode>
    ) {
        logProgress("RELATIONSHIP", 0, edges.size)
        val items = buildRelationshipItems(edges, nodeMap)
        val processed = batchEmbedder.embedAndSaveAll(items)
        logProgress("RELATIONSHIP", processed, edges.size)
    }

    /** Index cluster summaries. Req: 13.4 */
    suspend fun indexClusterSummaries(
        projectKey: String, clusters: List<Cluster>, nodeMap: Map<String, TicketNode>
    ) {
        logProgress("CLUSTER", 0, clusters.size)
        val items = clusters.map { buildClusterItem(it, nodeMap) }
        val processed = batchEmbedder.embedAndSaveAll(items)
        logProgress("CLUSTER", processed, clusters.size)
    }

    /** Index analysis results + evolution entries. Req: 14.1, 14.2, 14.4 */
    suspend fun indexAnalysisResults(projectKey: String, records: List<KBRecord>) {
        logProgress("ANALYSIS", 0, records.size)
        val items = buildAnalysisItems(records)
        val processed = batchEmbedder.embedAndSaveAll(items)
        logProgress("ANALYSIS", processed, records.size)
    }

    /** Index Confluence page summaries. Req: 19.4 */
    suspend fun indexConfluencePages(projectKey: String, items: List<EmbedItem>) {
        logProgress("CONFLUENCE", 0, items.size)
        val processed = batchEmbedder.embedAndSaveAll(items)
        logProgress("CONFLUENCE", processed, items.size)
    }

    /** Delete old chunks and re-index all data. Runs async, does not block caller. Req: 12.5, 16.5 */
    fun reindex(projectKey: String, graph: NetworkGraph, records: List<KBRecord>) {
        scope.launch { reindexSuspend(projectKey, graph, records) }
    }

    /** Suspending reindex for direct invocation (e.g. tests). */
    internal suspend fun reindexSuspend(
        projectKey: String, graph: NetworkGraph, records: List<KBRecord>
    ) {
        deleteAllChunks(projectKey)
        val nodeMap = graph.nodes.associateBy { it.id }
        val clusters = graphEngine?.detectClusters(graph) ?: emptyList()
        indexTickets(projectKey, graph.nodes)
        indexRelationships(projectKey, graph.edges, nodeMap)
        indexClusterSummaries(projectKey, clusters, nodeMap)
        indexAnalysisResults(projectKey, records)
    }

    private suspend fun deleteAllChunks(projectKey: String) {
        REINDEX_CHUNK_TYPES.forEach { type ->
            vectorStore.deleteByProjectKey(projectKey, type)
        }
    }

    // --- Item builders ---

    private suspend fun buildTicketItems(tickets: List<TicketNode>): List<EmbedItem> {
        return tickets.filter { !isTicketAlreadyIndexed(it.id) }.map { ticket ->
            EmbedItem(
                text = formatTicketText(ticket),
                ticketId = ticket.key, attachmentId = ticket.id,
                chunkType = ChunkType.TICKET
            )
        }
    }

    private fun buildRelationshipItems(
        edges: List<TicketEdge>, nodeMap: Map<String, TicketNode>
    ): List<EmbedItem> {
        return edges.mapNotNull { edge ->
            val source = nodeMap[edge.fromId] ?: return@mapNotNull null
            val target = nodeMap[edge.toId] ?: return@mapNotNull null
            EmbedItem(
                text = formatRelationshipText(source, edge, target),
                ticketId = source.key,
                attachmentId = "rel:${source.id}-${target.id}",
                chunkType = ChunkType.RELATIONSHIP,
                filename = "${source.key}-${target.key}"
            )
        }
    }

    private fun buildClusterItem(cluster: Cluster, nodeMap: Map<String, TicketNode>) =
        EmbedItem(
            text = formatClusterText(cluster, nodeMap),
            ticketId = "cluster-${cluster.id}",
            attachmentId = "cluster:${cluster.id}",
            chunkType = ChunkType.CLUSTER
        )

    private fun buildAnalysisItems(records: List<KBRecord>): List<EmbedItem> {
        val items = mutableListOf<EmbedItem>()
        for (record in records) {
            items.add(buildAnalysisItem(record))
            items.addAll(buildEvolutionItems(record))
        }
        return items
    }

    private fun buildAnalysisItem(record: KBRecord) = EmbedItem(
        text = formatAnalysisText(record),
        ticketId = record.ticketId,
        attachmentId = "analysis:${record.ticketId}",
        chunkType = ChunkType.ANALYSIS
    )

    private fun buildEvolutionItems(record: KBRecord): List<EmbedItem> {
        return record.evolutionHistory.mapIndexed { index, entry ->
            EmbedItem(
                text = formatEvolutionText(record.ticketId, entry),
                ticketId = record.ticketId,
                attachmentId = "evolution:${record.ticketId}:$index",
                chunkType = ChunkType.EVOLUTION
            )
        }
    }

    private suspend fun isTicketAlreadyIndexed(ticketId: String) =
        vectorStore.existsByAttachmentId("ticket:$ticketId")

    private fun logProgress(type: String, processed: Int, total: Int) {
        println("[IndexingPipeline] Indexing $type: $processed/$total")
    }

    companion object {
        /** ChunkTypes deleted during reindex. */
        val REINDEX_CHUNK_TYPES = listOf(
            ChunkType.TICKET, ChunkType.RELATIONSHIP,
            ChunkType.CLUSTER, ChunkType.ANALYSIS, ChunkType.EVOLUTION
        )

        fun formatTicketText(ticket: TicketNode, description: String? = null): String {
            val desc = description?.takeIf { it.isNotBlank() }
            return if (desc != null) "[${ticket.key}] ${ticket.summary}. $desc"
            else "[${ticket.key}] ${ticket.summary}"
        }

        fun formatRelationshipText(
            source: TicketNode, edge: TicketEdge, target: TicketNode
        ) = "${source.key} ${edge.relationshipType} ${target.key}: ${source.summary} → ${target.summary}"

        fun formatClusterText(cluster: Cluster, nodeMap: Map<String, TicketNode>): String {
            val top5 = cluster.nodeIds.take(5).mapNotNull { nodeMap[it]?.key }.joinToString(", ")
            return "Cluster ${cluster.id}: contains ${cluster.nodeIds.size} tickets — $top5"
        }

        fun formatAnalysisText(record: KBRecord) =
            "[${record.ticketId}] Estimate: ${record.scrumPoints}pts" +
                " (confidence: ${record.confidenceScore})." +
                " ${record.requirementSummary}. Rationale: ${record.rationale}"

        fun formatEvolutionText(ticketId: String, entry: EvolutionEntry) =
            "[$ticketId] v${entry.version} (${entry.date}):" +
                " ${entry.description} [${entry.changeType}]"
    }
}
