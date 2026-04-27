package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Progress status for document generation.
 * Phases: AGGREGATING_DATA (0-30%), GENERATING_DOCUMENT (30-90%), COMPLETE (90-100%), FAILED.
 * Returned by GET /api/analysis/{ticketId}/document-status (Req 4.3).
 */
@Serializable
data class DocumentStatus(
    val phase: String,
    val progressPercent: Int,
    val documentType: String
)
