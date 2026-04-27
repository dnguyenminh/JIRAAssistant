package com.assistant.server.db.pg

import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogRepository
import com.assistant.scan.ScanLogStatus
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [ScanLogRepository].
 * Uses the scan_log table with index on (project_key, timestamp DESC).
 * Requirements: 6.1, 6.2
 */
class PgScanLogRepository(
    private val dataSource: DataSource
) : ScanLogRepository {

    override suspend fun addEntry(entry: ScanLogEntry) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgScanLogSql.INSERT).use { ps ->
                    ps.setString(1, entry.projectKey)
                    ps.setString(2, entry.ticketId)
                    ps.setString(3, entry.status.name)
                    ps.setString(4, entry.message)
                    ps.setString(5, entry.timestamp)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgScanLogRepository] addEntry failed: ${e.message}")
        }
    }

    override suspend fun getByProjectKey(
        projectKey: String, limit: Long
    ): List<ScanLogEntry> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgScanLogSql.SELECT_BY_PROJECT_KEY).use { ps ->
                ps.setString(1, projectKey)
                ps.setLong(2, limit)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgScanLogRepository] getByProjectKey failed: ${e.message}")
        emptyList()
    }

    override suspend fun getByProjectKeyPaged(
        projectKey: String, limit: Long, offset: Long
    ): List<ScanLogEntry> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgScanLogSql.SELECT_BY_PROJECT_KEY_PAGED).use { ps ->
                ps.setString(1, projectKey)
                ps.setLong(2, limit)
                ps.setLong(3, offset)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgScanLogRepository] getByProjectKeyPaged failed: ${e.message}")
        emptyList()
    }

    override suspend fun countByProjectKey(projectKey: String): Long = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgScanLogSql.COUNT_BY_PROJECT_KEY).use { ps ->
                ps.setString(1, projectKey)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        }
    } catch (e: Exception) {
        println("[PgScanLogRepository] countByProjectKey failed: ${e.message}")
        0L
    }

    override suspend fun deleteByProjectKey(projectKey: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgScanLogSql.DELETE_BY_PROJECT_KEY).use { ps ->
                    ps.setString(1, projectKey)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgScanLogRepository] deleteByProjectKey failed: ${e.message}")
        }
    }

    private fun mapRow(rs: ResultSet): ScanLogEntry = ScanLogEntry(
        id = rs.getLong("id"),
        projectKey = rs.getString("project_key"),
        ticketId = rs.getString("ticket_id"),
        status = ScanLogStatus.valueOf(rs.getString("status")),
        message = rs.getString("message"),
        timestamp = rs.getString("timestamp")
    )
}
