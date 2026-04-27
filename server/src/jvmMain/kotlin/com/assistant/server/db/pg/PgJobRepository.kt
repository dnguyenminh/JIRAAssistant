package com.assistant.server.db.pg

import com.assistant.document.models.GenerationJob
import com.assistant.server.db.JobRepository
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/**
 * PostgreSQL implementation of [JobRepository] (Req 2.1, 2.5).
 */
class PgJobRepository(
    private val dataSource: DataSource
) : JobRepository {

    override suspend fun create(job: GenerationJob) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgJobSql.INSERT).use { ps ->
                ps.setObject(1, java.util.UUID.fromString(job.jobId))
                ps.setString(2, job.ticketId)
                ps.setString(3, job.documentType)
                ps.setString(4, job.status)
                ps.setInt(5, job.progressPercent)
                ps.setString(6, job.phase)
                if (job.chainId != null) ps.setObject(7, java.util.UUID.fromString(job.chainId))
                else ps.setNull(7, java.sql.Types.OTHER)
                ps.setString(8, job.createdBy)
                ps.setString(9, job.createdAt)
                ps.setString(10, job.updatedAt)
                ps.setString(11, job.errorMessage)
                ps.setString(12, job.startedAt)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun findById(jobId: String): GenerationJob? {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgJobSql.FIND_BY_ID).use { ps ->
                ps.setString(1, jobId)
                ps.executeQuery().use { rs -> if (rs.next()) mapRow(rs) else null }
            }
        }
    }

    override suspend fun findByTicketIdAndTypeActive(
        ticketId: String, documentType: String
    ): GenerationJob? {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(PgJobSql.FIND_ACTIVE_BY_TICKET_AND_TYPE).use { ps ->
                ps.setString(1, ticketId)
                ps.setString(2, documentType)
                ps.executeQuery().use { rs -> if (rs.next()) mapRow(rs) else null }
            }
        }
    }

    override suspend fun findActiveByTicketId(ticketId: String): List<GenerationJob> {
        return queryList(PgJobSql.FIND_ACTIVE_BY_TICKET) { ps -> ps.setString(1, ticketId) }
    }

    override suspend fun findByUser(userId: String, statusFilter: List<String>?): List<GenerationJob> {
        val sql = buildString {
            append("SELECT job_id,ticket_id,document_type,status,progress_percent,")
            append("phase,chain_id,created_by,created_at,updated_at,error_message,started_at ")
            append("FROM generation_jobs WHERE 1=1")
            if (userId.isNotBlank()) append(" AND created_by = ?")
            if (statusFilter != null) {
                append(" AND status IN (${statusFilter.joinToString(",") { "'$it'" }})")
            }
            append(" ORDER BY created_at DESC")
        }
        return queryList(sql) { ps ->
            if (userId.isNotBlank()) ps.setString(1, userId)
        }
    }

    override suspend fun findByChainId(chainId: String): List<GenerationJob> {
        return queryList(PgJobSql.FIND_BY_CHAIN) { ps -> ps.setString(1, chainId) }
    }

    override suspend fun updateStatus(
        jobId: String, status: String, progress: Int, phase: String, error: String?
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgJobSql.UPDATE_STATUS).use { ps ->
                ps.setString(1, status)
                ps.setInt(2, progress)
                ps.setString(3, phase)
                ps.setString(4, error)
                ps.setString(5, Instant.now().toString())
                ps.setString(6, null) // started_at — only set explicitly via updateStartedAt
                ps.setString(7, jobId)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun updateStartedAt(jobId: String, startedAt: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE generation_jobs SET started_at = ? WHERE job_id = ?::uuid"
            ).use { ps ->
                ps.setString(1, startedAt)
                ps.setString(2, jobId)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun findRunningJobs(): List<GenerationJob> {
        return queryList(PgJobSql.FIND_RUNNING) { }
    }

    private fun queryList(
        sql: String, bind: (java.sql.PreparedStatement) -> Unit
    ): List<GenerationJob> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<GenerationJob>()
                    while (rs.next()) list.add(mapRow(rs))
                    list
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): GenerationJob {
        return GenerationJob(
            jobId = rs.getString("job_id"),
            ticketId = rs.getString("ticket_id"),
            documentType = rs.getString("document_type"),
            status = rs.getString("status"),
            progressPercent = rs.getInt("progress_percent"),
            phase = rs.getString("phase"),
            chainId = rs.getString("chain_id"),
            createdBy = rs.getString("created_by"),
            createdAt = rs.getString("created_at"),
            updatedAt = rs.getString("updated_at"),
            errorMessage = rs.getString("error_message"),
            startedAt = rs.getString("started_at")
        )
    }
}
