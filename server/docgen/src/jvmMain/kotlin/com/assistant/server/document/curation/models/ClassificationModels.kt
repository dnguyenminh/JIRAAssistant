package com.assistant.server.document.curation.models

import kotlinx.serialization.Serializable

/**
 * Temporal relationship between a linked ticket and the root ticket.
 *
 * Requirements: 2.1
 */
@Serializable
enum class TemporalRelation {
    OLDER,
    NEWER,
    CONCURRENT
}

/**
 * Content classification based on temporal analysis.
 *
 * Requirements: 2.3, 2.4, 2.5
 */
@Serializable
enum class ContentClassification {
    AS_IS,
    TO_BE,
    OUTDATED
}

/**
 * Result of classifying a linked ticket's temporal and content relationship.
 *
 * Requirements: 2.1, 2.3, 2.4, 2.5
 */
@Serializable
data class TicketClassification(
    val ticketId: String,
    val temporalRelation: TemporalRelation,
    val contentClassification: ContentClassification,
    val supersededBy: String? = null
)
