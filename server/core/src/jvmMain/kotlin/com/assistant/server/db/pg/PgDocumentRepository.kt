package com.assistant.server.db.pg

import com.assistant.document.models.GeneratedDocument
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.GeneratedDocumentMeta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL-backed [DocumentRepository] with versioning + approval (Req 7.1–7.4).
 */
class PgDocumentRepository(
    private val dataSource: DataSource
) : DocumentRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun save(document: GeneratedDocument) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.INSERT).use { ps ->
                ps.setString(1, document.ticketId)
                ps.setString(2, document.documentType)
                ps.setString(3, document.markdownContent)
                ps.setString(4, document.generatedAt)
                ps.setString(5, json.encodeToString(document.sourceTicketIds))
                ps.setString(6, json.encodeToString(document.attachmentSources))
                ps.setString(7, document.aiProviderUsed)
                ps.setString(8, document.approvalStatus)
                if (document.versionNumber != null) ps.setInt(9, document.versionNumber!!)
                else ps.setNull(9, java.sql.Types.INTEGER)
                ps.setString(10, document.rejectReason)
                ps.setString(11, document.reviewedBy)
                ps.setString(12, document.reviewedAt)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun findByTicketId(ticketId: String): List<GeneratedDocument> {
        return queryDocs(PgDocumentSql.FIND_BY_TICKET_ID) { it.setString(1, ticketId) }
    }

    override suspend fun findByTicketIdAndType(ticketId: String, documentType: String): GeneratedDocument? {
        return querySingle(PgDocumentSql.FIND_BY_TICKET_ID_AND_TYPE) {
            it.setString(1, ticketId); it.setString(2, documentType)
        }
    }

    override suspend fun findLatestByTicketIdAndType(ticketId: String, documentType: String): GeneratedDocument? {
        return querySingle(PgDocumentSql.FIND_LATEST) {
            it.setString(1, ticketId); it.setString(2, documentType)
        }
    }

    override suspend fun findLatestDraftByTicketIdAndType(ticketId: String, documentType: String): GeneratedDocument? {
        return querySingle(PgDocumentSql.FIND_LATEST_DRAFT) {
            it.setString(1, ticketId); it.setString(2, documentType)
        }
    }

    override suspend fun findAllVersions(ticketId: String, documentType: String): List<GeneratedDocumentMeta> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.FIND_ALL_VERSIONS).use { ps ->
                ps.setString(1, ticketId); ps.setString(2, documentType)
                ps.executeQuery().use { rs -> collectMeta(rs) }
            }
        }
    }

    override suspend fun findByVersion(ticketId: String, documentType: String, versionNumber: Int): GeneratedDocument? {
        return querySingle(PgDocumentSql.FIND_BY_VERSION) {
            it.setString(1, ticketId); it.setString(2, documentType); it.setInt(3, versionNumber)
        }
    }

    override suspend fun updateApprovalStatus(
        id: Long, status: String, reviewedBy: String?, reviewedAt: String?, rejectReason: String?
    ) {
        val versionNum = if (status == "APPROVED") getNextVersionFromId(id) else null
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.UPDATE_APPROVAL).use { ps ->
                ps.setString(1, status)
                ps.setString(2, reviewedBy)
                ps.setString(3, reviewedAt)
                ps.setString(4, rejectReason)
                if (versionNum != null) ps.setInt(5, versionNum) else ps.setNull(5, java.sql.Types.INTEGER)
                ps.setLong(6, id)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun getNextVersionNumber(ticketId: String, documentType: String): Int {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.NEXT_VERSION).use { ps ->
                ps.setString(1, ticketId); ps.setString(2, documentType)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 1 }
            }
        }
    }

    override suspend fun findById(id: Long): GeneratedDocument? {
        return querySingle(PgDocumentSql.FIND_BY_ID) { it.setLong(1, id) }
    }

    override suspend fun listByTicketId(ticketId: String): List<GeneratedDocumentMeta> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.LIST_META_BY_TICKET_ID).use { ps ->
                ps.setString(1, ticketId)
                ps.executeQuery().use { rs -> collectMeta(rs) }
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun getNextVersionFromId(id: Long): Int? {
        val doc = dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.FIND_BY_ID).use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) mapRow(rs) else null }
            }
        } ?: return null
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgDocumentSql.NEXT_VERSION).use { ps ->
                ps.setString(1, doc.ticketId); ps.setString(2, doc.documentType)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 1 }
            }
        }
    }

    private fun queryDocs(sql: String, bind: (java.sql.PreparedStatement) -> Unit): List<GeneratedDocument> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<GeneratedDocument>()
                    while (rs.next()) list.add(mapRow(rs))
                    list
                }
            }
        }
    }

    private fun querySingle(sql: String, bind: (java.sql.PreparedStatement) -> Unit): GeneratedDocument? {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs -> if (rs.next()) mapRow(rs) else null }
            }
        }
    }

    private fun collectMeta(rs: ResultSet): List<GeneratedDocumentMeta> {
        val list = mutableListOf<GeneratedDocumentMeta>()
        while (rs.next()) {
            list.add(GeneratedDocumentMeta(
                documentType = rs.getString("document_type"),
                generatedAt = rs.getString("generated_at"),
                aiProviderUsed = rs.getString("ai_provider_used"),
                approvalStatus = rs.getString("approval_status"),
                versionNumber = rs.getObject("version_number") as? Int,
                hasDraft = rs.getBoolean("has_draft")
            ))
        }
        return list
    }

    private fun mapRow(rs: ResultSet): GeneratedDocument {
        return GeneratedDocument(
            id = rs.getLong("id"),
            documentType = rs.getString("document_type"),
            ticketId = rs.getString("ticket_id"),
            generatedAt = rs.getString("generated_at"),
            markdownContent = rs.getString("markdown_content"),
            sourceTicketIds = parseJsonList(rs.getString("source_ticket_ids")),
            attachmentSources = parseJsonList(rs.getString("attachment_sources")),
            aiProviderUsed = rs.getString("ai_provider_used"),
            approvalStatus = rs.getString("approval_status"),
            versionNumber = rs.getObject("version_number") as? Int,
            rejectReason = rs.getString("reject_reason"),
            reviewedBy = rs.getString("reviewed_by"),
            reviewedAt = rs.getString("reviewed_at")
        )
    }

    private fun parseJsonList(raw: String): List<String> = try {
        if (raw.isBlank() || raw == "[]") emptyList() else json.decodeFromString(raw)
    } catch (_: Exception) { emptyList() }
}
