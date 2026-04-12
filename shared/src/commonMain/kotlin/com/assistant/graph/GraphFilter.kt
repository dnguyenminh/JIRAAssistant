package com.assistant.graph

import com.assistant.domain.TicketNode

/**
 * Filters graph nodes by matching query against node.key or node.summary (case-insensitive).
 */
fun filterNodes(nodes: List<TicketNode>, query: String): List<TicketNode> {
    if (query.isBlank()) return nodes
    val lowerQuery = query.lowercase()
    return nodes.filter { node ->
        node.key.lowercase().contains(lowerQuery) ||
            node.summary.lowercase().contains(lowerQuery)
    }
}
