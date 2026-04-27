package com.assistant.server.jobs

import com.assistant.document.models.GeneratedDocument
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.GeneratedDocumentMeta
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** In-memory DocumentRepository for unit/property tests. */
class InMemoryDocumentRepository : DocumentRepository {

    private val idSeq = AtomicLong(1)
    private val store = ConcurrentHashMap<Long, GeneratedDocument>()
    private val idMap = ConcurrentHashMap<Long, Long>() // internal id tracking

    override suspend fun save(document: GeneratedDocument) {
        val id = idSeq.getAndIncrement()
        store[id] = document
    }

    override suspend fun findByTicketId(ticketId: String) =
        store.values.filter { it.ticketId == ticketId }

    override suspend fun findByTicketIdAndType(
        ticketId: String, documentType: String
    ) = store.values.firstOrNull {
        it.ticketId == ticketId && it.documentType == documentType
    }

    override suspend fun listByTicketId(ticketId: String) =
        store.values.filter { it.ticketId == ticketId }.map { it.toMeta() }

    override suspend fun findLatestByTicketIdAndType(
        ticketId: String, documentType: String
    ): GeneratedDocument? {
        val approved = findApproved(ticketId, documentType)
        if (approved != null) return approved
        return findLatestDraft(ticketId, documentType)
    }

    override suspend fun findLatestDraftByTicketIdAndType(
        ticketId: String, documentType: String
    ): GeneratedDocument? = findLatestDraft(ticketId, documentType)

    override suspend fun findAllVersions(
        ticketId: String, documentType: String
    ) = store.values
        .filter { it.ticketId == ticketId && it.documentType == documentType }
        .map { it.toMeta() }

    override suspend fun findByVersion(
        ticketId: String, documentType: String, versionNumber: Int
    ) = store.values.firstOrNull {
        it.ticketId == ticketId &&
            it.documentType == documentType &&
            it.versionNumber == versionNumber
    }

    override suspend fun updateApprovalStatus(
        id: Long, status: String, reviewedBy: String?,
        reviewedAt: String?, rejectReason: String?
    ) {
        val doc = store[id] ?: return
        val version = if (status == "APPROVED") {
            getNextVersionNumber(doc.ticketId, doc.documentType)
        } else null
        store[id] = doc.copy(
            approvalStatus = status,
            versionNumber = version,
            reviewedBy = reviewedBy,
            reviewedAt = reviewedAt,
            rejectReason = rejectReason
        )
    }

    override suspend fun getNextVersionNumber(
        ticketId: String, documentType: String
    ): Int {
        val max = store.values
            .filter {
                it.ticketId == ticketId &&
                    it.documentType == documentType &&
                    it.versionNumber != null
            }
            .maxOfOrNull { it.versionNumber!! } ?: 0
        return max + 1
    }

    override suspend fun findById(id: Long) = store[id]

    fun saveAndGetId(document: GeneratedDocument): Long {
        val id = idSeq.getAndIncrement()
        store[id] = document
        return id
    }

    fun allDocs() = store.toMap()

    private fun findApproved(ticketId: String, docType: String) =
        store.values
            .filter {
                it.ticketId == ticketId &&
                    it.documentType == docType &&
                    it.approvalStatus == "APPROVED"
            }
            .maxByOrNull { it.versionNumber ?: 0 }

    private fun findLatestDraft(ticketId: String, docType: String) =
        store.values.lastOrNull {
            it.ticketId == ticketId &&
                it.documentType == docType &&
                it.approvalStatus == "DRAFT"
        }

    private fun GeneratedDocument.toMeta() = GeneratedDocumentMeta(
        documentType = documentType,
        generatedAt = generatedAt,
        aiProviderUsed = aiProviderUsed,
        approvalStatus = approvalStatus,
        versionNumber = versionNumber,
        hasDraft = approvalStatus == "DRAFT"
    )
}
