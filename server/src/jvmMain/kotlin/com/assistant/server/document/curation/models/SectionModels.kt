package com.assistant.server.document.curation.models

import kotlinx.serialization.Serializable

/**
 * TO-BE section: new requirements from root + newer/concurrent tickets.
 *
 * Requirements: 3.1, 3.2
 */
@Serializable
data class ToBeSection(
    val rootRequirements: List<String>,
    val linkedRequirements: List<ClassifiedTicketData>
)

/**
 * AS-IS section: existing functionality from older resolved tickets.
 *
 * Requirements: 3.1, 3.3
 */
@Serializable
data class AsIsSection(
    val existingFunctionality: List<ClassifiedTicketData>
)

/**
 * Classified ticket data included in AS-IS or TO-BE sections.
 *
 * Requirements: 3.2, 3.3
 */
@Serializable
data class ClassifiedTicketData(
    val ticketId: String,
    val classification: ContentClassification,
    val businessSummary: String,
    val asIsState: String,
    val toBeState: String,
    val extractedRequirements: List<String>,
    val annotation: String? = null
)

/**
 * One-line reference for outdated/superseded tickets.
 *
 * Requirements: 3.4
 */
@Serializable
data class OutdatedReference(
    val ticketId: String,
    val supersededBy: String,
    val oneLinerSummary: String
)

/**
 * Brief reference for tickets available via MCP lookup.
 *
 * Requirements: 10.2
 */
@Serializable
data class TicketReference(
    val ticketId: String,
    val oneLinerSummary: String
)
