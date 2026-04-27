package com.assistant.server.analysis

import com.assistant.server.analysis.models.BatchInfo
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketEdge
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.models.TicketNode

/**
 * Splits a [TicketGraph] into batches for Map phase processing.
 *
 * Strategy: group by depth level, keep related tickets together,
 * root ticket always in batch 0 with highest-relevance depth-1 tickets.
 *
 * Invariants:
 * - Every ticket appears in exactly one batch (no duplicates, no loss)
 * - No empty batches
 * - Each batch has at most [MapReduceConfig.maxBatchSize] tickets
 * - Root ticket is always in batch 0
 *
 * Requirements: 2.1-2.7
 */
class BatchStrategy(private val config: MapReduceConfig) {

    /**
     * Partition [TicketGraph] nodes into batches.
     *
     * @param graph The full TicketGraph
     * @return Ordered list of [BatchInfo] (batch 0 contains root ticket)
     */
    fun partition(graph: TicketGraph): List<BatchInfo> {
        val maxSize = config.validated().maxBatchSize
        val allNodes = graph.nodes.values
        if (allNodes.isEmpty()) return emptyList()

        val groups = groupByDepth(allNodes, graph.edges, maxSize)
        val rootBatch = buildRootBatch(allNodes, graph, maxSize)
        val remaining = buildRemainingBatches(groups, rootBatch.ticketIds.toSet())
        val allBatches = listOf(rootBatch.tickets) + remaining
        return assignBatchMetadata(allBatches)
    }

    /**
     * Group tickets by depth level, then split oversized groups.
     * Keeps parent-child relationships in same batch when possible.
     */
    internal fun groupByDepth(
        nodes: Collection<TicketNode>,
        edges: List<TicketEdge>,
        maxBatchSize: Int
    ): List<List<TicketNode>> {
        val byDepth = nodes.groupBy { it.depth }
            .toSortedMap()
            .values
            .toList()
        return byDepth.flatMap { group ->
            splitGroup(group, edges, maxBatchSize)
        }
    }

    /** Build batch 0: root + highest-relevance depth-1 tickets. */
    private fun buildRootBatch(
        allNodes: Collection<TicketNode>,
        graph: TicketGraph,
        maxSize: Int
    ): BatchInfo {
        val root = allNodes.first { it.ticketId == graph.rootTicketId }
        val depth1 = allNodes
            .filter { it.depth == 1 }
            .sortedByDescending { it.relevanceScore }
        val batch0 = listOf(root) + depth1.take(maxSize - 1)
        return BatchInfo(
            batchIndex = 0, totalBatches = 0,
            tickets = batch0,
            depthLevels = batch0.map { it.depth }.distinct().sorted()
        )
    }

    /** Collect remaining tickets not in batch 0 into batches. */
    private fun buildRemainingBatches(
        groups: List<List<TicketNode>>,
        usedIds: Set<String>
    ): List<List<TicketNode>> {
        return groups.mapNotNull { group ->
            val filtered = group.filter { it.ticketId !in usedIds }
            filtered.ifEmpty { null }
        }
    }

    /** Assign batchIndex, totalBatches, and depthLevels to each batch. */
    private fun assignBatchMetadata(
        batches: List<List<TicketNode>>
    ): List<BatchInfo> {
        val nonEmpty = batches.filter { it.isNotEmpty() }
        val total = nonEmpty.size
        return nonEmpty.mapIndexed { index, tickets ->
            BatchInfo(
                batchIndex = index,
                totalBatches = total,
                tickets = tickets,
                depthLevels = tickets.map { it.depth }.distinct().sorted()
            )
        }
    }

    /**
     * Split a depth-level group into chunks of [maxBatchSize],
     * keeping parent-child pairs together when possible.
     */
    private fun splitGroup(
        group: List<TicketNode>,
        edges: List<TicketEdge>,
        maxBatchSize: Int
    ): List<List<TicketNode>> {
        if (group.size <= maxBatchSize) return listOf(group)
        val sorted = sortByRelationship(group, edges)
        return sorted.chunked(maxBatchSize)
    }

    /**
     * Sort tickets so parent-child pairs are adjacent,
     * increasing the chance they end up in the same chunk.
     */
    private fun sortByRelationship(
        group: List<TicketNode>,
        edges: List<TicketEdge>
    ): List<TicketNode> {
        val ids = group.map { it.ticketId }.toSet()
        val parentOf = buildParentMap(edges, ids)
        val byId = group.associateBy { it.ticketId }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<TicketNode>()
        for (node in group) {
            if (node.ticketId in visited) continue
            appendCluster(node, parentOf, byId, visited, result)
        }
        return result
    }

    /** Build a map: childId → parentId for tickets in the group. */
    private fun buildParentMap(
        edges: List<TicketEdge>,
        ids: Set<String>
    ): Map<String, String> {
        val parentTypes = setOf(RelationshipType.PARENT, RelationshipType.SUB_TASK)
        return edges
            .filter { it.relationshipType in parentTypes }
            .filter { it.sourceId in ids && it.targetId in ids }
            .associate { it.targetId to it.sourceId }
    }

    /** Append a node and its children cluster to the result list. */
    private fun appendCluster(
        node: TicketNode,
        parentOf: Map<String, String>,
        byId: Map<String, TicketNode>,
        visited: MutableSet<String>,
        result: MutableList<TicketNode>
    ) {
        if (node.ticketId in visited) return
        visited.add(node.ticketId)
        result.add(node)
        val children = parentOf
            .filter { it.value == node.ticketId }
            .keys
        for (childId in children) {
            val child = byId[childId] ?: continue
            appendCluster(child, parentOf, byId, visited, result)
        }
    }
}
