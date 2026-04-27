package com.assistant.server.document.models

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import kotlinx.serialization.Serializable

/**
 * Type of relationship between two tickets in the [TicketGraph].
 *
 * Used by [TraversalEngine] to classify how a ticket was discovered
 * and by [PromptAssembler] to determine priority ordering.
 *
 * Requirements: 1.3
 */
@Serializable
enum class RelationshipType {
    /** The root ticket that initiated the traversal. */
    ROOT,
    /** Discovered via Jira issue links (outward or inward). */
    ISSUE_LINK,
    /** Discovered as a sub-task of another ticket. */
    SUB_TASK,
    /** Discovered as the parent of another ticket. */
    PARENT,
    /** Discovered via ticket ID mentioned in text fields. */
    TEXT_REFERENCE
}

/**
 * A node in the [TicketGraph] representing a single visited ticket.
 *
 * Each node stores the ticket's structured content along with traversal
 * metadata such as depth, discovery relationship, and relevance score.
 *
 * Requirements: 1.3, 1.9
 */
@Serializable
data class TicketNode(
    /** Jira ticket key (e.g. "ICL2-100"). */
    val ticketId: String,
    /** BFS depth from the root ticket (root = 0). */
    val depth: Int,
    /** How this ticket was discovered during traversal. */
    val discoveredVia: RelationshipType,
    /** Ticket ID of the node that led to discovering this one. */
    val parentDiscoveryId: String,
    /** Full structured content fetched from Jira API. */
    val issue: StructuredTicketContent,
    /**
     * Relevance score computed from depth, relationship type,
     * recency, and status. Higher = more relevant. (Req 1.9)
     */
    val relevanceScore: Double = 0.0
)

/**
 * A directed edge in the [TicketGraph] representing a relationship
 * between two tickets.
 *
 * Requirements: 1.3
 */
@Serializable
data class TicketEdge(
    /** Source ticket key. */
    val sourceId: String,
    /** Target ticket key. */
    val targetId: String,
    /** Type of relationship between source and target. */
    val relationshipType: RelationshipType,
    /** Human-readable link description (e.g. "blocks", "is cloned by"). */
    val linkDescription: String = ""
)

/**
 * Graph of related tickets built by [TraversalEngine] during BFS traversal.
 *
 * Contains all visited ticket nodes, edges representing relationships,
 * and metadata about the traversal process. The root ticket is always
 * present in [nodes] at depth 0.
 *
 * Requirements: 1.3, 1.9, 1.10
 */
@Serializable
data class TicketGraph(
    /** Jira key of the root ticket that initiated the traversal. */
    val rootTicketId: String,
    /** All visited tickets keyed by ticket ID. */
    val nodes: Map<String, TicketNode>,
    /** Directed edges representing relationships between tickets. */
    val edges: List<TicketEdge>,
    /** Statistics and diagnostics from the traversal process. */
    val metadata: TraversalMetadata
)
