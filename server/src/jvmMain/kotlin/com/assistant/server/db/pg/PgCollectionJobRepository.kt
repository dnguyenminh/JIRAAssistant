package com.assistant.server.db.pg

import com.assistant.server.document.jobs.CollectionJobRepository
import com.assistant.server.document.models.CollectionJob
import com.assistant.server.document.models.CollectionJobItem
import com.assistant.server.document.models.CollectionJobStatus
import com.assistant.server.document.models.CollectionJobType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL implementation of [CollectionJobRepository].
 *
 * Full CRUD operations for collection_jobs table using JDBC.
 * Supports optimistic locking via version field (Req 14.7).
 *
 * Requirements: 13.3, 13.5, 13.6, 14.7
 */
class PgCollectionJobRepository(
    private val dataSource: DataSource
) : CollectionJobRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun save(job: CollectionJob) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgCollectionJobSql.INSERT).use { ps ->
                ps.setString(1, job.jobId)
                ps.setString(2, job.parentTicketId)
                ps.setString(3, job.jobType.name)
                ps.setString(4, job.status.name)
                ps.setInt(5, job.totalItems)
                ps.setInt(6, job.completedItems)
                ps.setInt(7, job.failedItems)
                ps.setString(8, json.encodeToString(job.items))
                ps.setString(9, job.createdAt)
                ps.setString(10, job.updatedAt)
                ps.setInt(11, job.version)
                ps.executeUpdate()
            }
        }
    }

    override fun findById(jobId: String): CollectionJob? {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgCollectionJobSql.FIND_BY_ID).use { ps ->
                ps.setString(1, jobId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    override fun findByParentTicketId(parentTicketId: String): List<CollectionJob> {
        return queryList(PgCollectionJobSql.FIND_BY_PARENT_TICKET) { ps ->
            ps.setString(1, parentTicketId)
        }
    }

    override fun findActive(): List<CollectionJob> {
        return queryList(PgCollectionJobSql.FIND_ACTIVE) { }
    }

    override fun updateJobStatus(
        jobId: String,
        newStatus: CollectionJobStatus,
        updatedAt: String,
        expectedVersion: Int
    ): Boolean {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgCollectionJobSql.UPDATE_STATUS).use { ps ->
                ps.setString(1, newStatus.name)
                ps.setString(2, updatedAt)
                ps.setString(3, jobId)
                ps.setInt(4, expectedVersion)
                ps.executeUpdate() > 0
            }
        }
    }

    override fun updateItemStatus(
        jobId: String,
        completedItems: Int,
        failedItems: Int,
        items: List<CollectionJobItem>,
        updatedAt: String,
        expectedVersion: Int
    ): Boolean {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgCollectionJobSql.UPDATE_PROGRESS).use { ps ->
                ps.setInt(1, completedItems)
                ps.setInt(2, failedItems)
                ps.setString(3, json.encodeToString(items))
                ps.setString(4, updatedAt)
                ps.setString(5, jobId)
                ps.setInt(6, expectedVersion)
                ps.executeUpdate() > 0
            }
        }
    }

    override fun delete(jobId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgCollectionJobSql.DELETE).use { ps ->
                ps.setString(1, jobId)
                ps.executeUpdate()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun queryList(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit
    ): List<CollectionJob> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<CollectionJob>()
                    while (rs.next()) list.add(mapRow(rs))
                    list
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): CollectionJob {
        val itemsRaw = rs.getString("items_json") ?: "[]"
        return CollectionJob(
            jobId = rs.getString("job_id"),
            parentTicketId = rs.getString("parent_ticket_id"),
            jobType = CollectionJobType.valueOf(rs.getString("job_type")),
            status = CollectionJobStatus.valueOf(rs.getString("status")),
            totalItems = rs.getInt("total_items"),
            completedItems = rs.getInt("completed_items"),
            failedItems = rs.getInt("failed_items"),
            items = parseItems(itemsRaw),
            createdAt = rs.getString("created_at"),
            updatedAt = rs.getString("updated_at"),
            version = rs.getInt("version")
        )
    }

    private fun parseItems(raw: String): List<CollectionJobItem> {
        return try {
            if (raw.isBlank() || raw == "[]") emptyList()
            else json.decodeFromString<List<CollectionJobItem>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
