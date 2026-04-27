package com.assistant.server.db

import com.assistant.document.models.GeneratedDocument
import kotlinx.serialization.Serializable

/**
 * Repository interface for generated document persistence (BRD, FSD, REQUIREMENT_SLIDES).
 * Manages CRUD operations on the `generated_documents` table (Req 5.1–5.5).
 */
interface DocumentRepository {
    suspend fun save(document: GeneratedDocument)
    suspend fun findByTicketId(ticketId: String): List<GeneratedDocument>
    suspend fun findByTicketIdAndType(ticketId: String, documentType: String): GeneratedDocument?
    suspend fun listByTicketId(ticketId: String): List<GeneratedDocumentMeta>
    suspend fun findLatestByTicketIdAndType(ticketId: String, documentType: String): GeneratedDocument?
    suspend fun findLatestDraftByTicketIdAndType(ticketId: String, documentType: String): GeneratedDocument?
    suspend fun findAllVersions(ticketId: String, documentType: String): List<GeneratedDocumentMeta>
    suspend fun findByVersion(ticketId: String, documentType: String, versionNumber: Int): GeneratedDocument?
    suspend fun updateApprovalStatus(id: Long, status: String, reviewedBy: String?, reviewedAt: String?, rejectReason: String?)
    suspend fun getNextVersionNumber(ticketId: String, documentType: String): Int
    suspend fun findById(id: Long): GeneratedDocument?
}

/**
 * Lightweight metadata projection — excludes markdownContent (Req 5.4, 8.6).
 */
@Serializable
data class GeneratedDocumentMeta(
    val documentType: String,
    val generatedAt: String,
    val aiProviderUsed: String,
    val approvalStatus: String = "DRAFT",
    val versionNumber: Int? = null,
    val hasDraft: Boolean = false
)
