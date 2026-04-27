package com.assistant.frontend.models

import kotlinx.serialization.Serializable

/**
 * Metadata for document list display (Req 5.4, 8.6).
 */
@Serializable
data class GeneratedDocumentMeta(
    val documentType: String,
    val generatedAt: String,
    val aiProviderUsed: String = "",
    val approvalStatus: String = "DRAFT",
    val versionNumber: Int? = null,
    val hasDraft: Boolean = false
)

/**
 * Full document content for preview/export (Req 4.1, 8.5).
 */
@Serializable
data class GeneratedDocumentFull(
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

/**
 * Polling status for document generation progress (Req 4.3).
 */
@Serializable
data class DocumentGenerationStatus(
    val phase: String,
    val progressPercent: Int,
    val documentType: String
)

/**
 * Generation job DTO for frontend (Req 2.5).
 */
@Serializable
data class GenerationJobDto(
    val jobId: String,
    val ticketId: String,
    val documentType: String,
    val status: String,
    val progressPercent: Int = 0,
    val phase: String = "QUEUED",
    val chainId: String? = null,
    val errorMessage: String? = null,
    val startedAt: String? = null,
    val phaseLabel: String? = null
)

/**
 * Version metadata for version history (Req 7.3).
 */
@Serializable
data class VersionMeta(
    val versionNumber: Int = 0,
    val generatedAt: String,
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val aiProviderUsed: String = ""
)
