package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Output model for a generated BRD, FSD, or REQUIREMENT_SLIDES document.
 * Stored in `generated_documents` table and returned by API endpoints (Req 4.1, 4.2, 5.1).
 */
@Serializable
data class GeneratedDocument(
    val id: Long? = null,
    val documentType: String,
    val ticketId: String,
    val generatedAt: String,
    val markdownContent: String,
    val sourceTicketIds: List<String> = emptyList(),
    val attachmentSources: List<String> = emptyList(),
    val aiProviderUsed: String = "",
    val approvalStatus: String = "DRAFT",
    val versionNumber: Int? = null,
    val rejectReason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: String? = null
)
