package com.assistant.domain

import kotlinx.serialization.Serializable

/**
 * Representation of the Jira ticket network.
 * Nodes are tickets, Edges are relationships.
 */
@Serializable
data class NetworkGraph(
    val nodes: List<TicketNode>,
    val edges: List<TicketEdge>
)

@Serializable
data class TicketNode(
    val id: String,
    val key: String,
    val summary: String,
    val status: String,
    val featureName: String? = null,
    val description: String? = null
)

@Serializable
data class TicketEdge(
    val fromId: String,
    val toId: String,
    val relationshipType: String,
    val isSemantic: Boolean = false // True if identified by AI, False if explicit in Jira
)
