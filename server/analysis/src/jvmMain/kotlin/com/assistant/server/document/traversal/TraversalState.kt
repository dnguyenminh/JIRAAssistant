package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketEdge
import com.assistant.server.document.models.TicketNode
import com.assistant.server.document.models.TraversalConfig
import java.util.LinkedList

/**
 * Item in the BFS queue representing a ticket to be fetched.
 */
data class BfsQueueItem(
    val ticketId: String,
    val depth: Int,
    val relationshipType: RelationshipType,
    val parentId: String,
    val linkDescription: String
)

/**
 * Mutable state for BFS traversal. Tracks visited nodes, edges,
 * BFS queue, data size, and error counts.
 *
 * Not thread-safe — used within a single coroutine context.
 */
class TraversalState(private val config: TraversalConfig) {

    private val visited = mutableSetOf<String>()
    private val queued = mutableSetOf<String>()
    private val nodeMap = mutableMapOf<String, TicketNode>()
    private val edgeList = mutableListOf<TicketEdge>()
    private val queue = LinkedList<BfsQueueItem>()
    private val skipped = mutableListOf<String>()
    private var permissionDenied = 0
    private var dataSize = 0L
    private var maxDepth = 0
    private var totalDiscoveredCount = 0

    fun addNode(node: TicketNode) {
        visited.add(node.ticketId)
        nodeMap[node.ticketId] = node
        if (node.depth > maxDepth) maxDepth = node.depth
    }

    fun addEdge(sourceId: String, targetId: String, type: RelationshipType, desc: String) {
        edgeList.add(TicketEdge(sourceId, targetId, type, desc))
    }

    fun enqueue(item: BfsQueueItem) {
        if (item.ticketId !in visited && item.ticketId !in queued) {
            totalDiscoveredCount++
            queued.add(item.ticketId)
            queue.add(item)
        }
    }

    fun updateDataSize(content: StructuredTicketContent) {
        dataSize += estimateContentSize(content)
    }

    fun incrementPermissionDenied() { permissionDenied++ }
    fun addSkipped(ticketId: String) { skipped.add(ticketId) }

    fun isVisited(ticketId: String): Boolean = ticketId in visited
    fun visitedIds(): Set<String> = visited.toSet()
    fun hasWork(): Boolean = queue.isNotEmpty()
    fun isMaxTicketsReached(): Boolean = nodeMap.size >= config.maxTickets

    fun isEarlyTermination(): Boolean {
        if (config.disableEarlyTermination) return false
        val threshold = config.maxPromptChars.toLong() * 3
        return dataSize > threshold
    }

    /** Dequeue all items at the current (lowest) depth level. */
    fun dequeueCurrentLevel(): List<BfsQueueItem> {
        if (queue.isEmpty()) return emptyList()
        val currentDepth = queue.peek().depth
        val level = mutableListOf<BfsQueueItem>()
        while (queue.isNotEmpty() && queue.peek().depth == currentDepth) {
            val item = queue.poll()
            if (item.ticketId !in visited) level.add(item)
        }
        return level
    }

    fun nodeCount(): Int = nodeMap.size
    fun skippedCount(): Int = skipped.size
    fun skippedIds(): List<String> = skipped.toList()
    fun permissionDeniedCount(): Int = permissionDenied
    fun maxDepthReached(): Int = maxDepth
    fun dataSize(): Long = dataSize
    fun totalDiscovered(): Int = totalDiscoveredCount + nodeMap.size
    fun nodes(): Map<String, TicketNode> = nodeMap.toMap()
    fun edges(): List<TicketEdge> = edgeList.toList()

    /** Estimate character size of a ticket's content. */
    private fun estimateContentSize(content: StructuredTicketContent): Long {
        var size = content.summary.length.toLong()
        size += content.description.length
        for (comment in content.comments) {
            size += comment.content.length
        }
        return size
    }
}
