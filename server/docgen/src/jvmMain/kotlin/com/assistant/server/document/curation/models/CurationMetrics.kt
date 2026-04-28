package com.assistant.server.document.curation.models

import kotlinx.serialization.Serializable

/**
 * Observability metrics produced by the curation pipeline.
 *
 * Requirements: 9.2, 9.3
 */
@Serializable
data class CurationMetrics(
    val originalContextSizeChars: Int,
    val curatedContextSizeChars: Int,
    val ticketsAsIs: Int,
    val ticketsToBe: Int,
    val ticketsOutdated: Int,
    val ticketsReferenceOnly: Int,
    val commentsSummarized: Int,
    val attachmentsCurated: Int,
    val curationTimeMs: Long
)
