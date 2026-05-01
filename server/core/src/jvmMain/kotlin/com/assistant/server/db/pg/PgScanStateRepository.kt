package com.assistant.server.db.pg

import com.assistant.scan.ScanState
import com.assistant.scan.ScanStateRepository
import com.assistant.scan.ScanStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [ScanStateRepository].
 * Uses the scan_states table with JSON-serialized ticketIds.
 * Requirements: 6.1, 6.2
 */
class PgScanStateRepository(
    private val dataSource: DataSource
) : ScanStateRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun findByProjectKey(projectKey: String): ScanState? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgScanStateSql.FIND_BY_PROJECT_KEY).use { ps ->
                ps.setString(1, projectKey)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgScanStateRepository] findByProjectKey failed: ${e.message}")
        null
    }

    override suspend fun save(state: ScanState) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgScanStateSql.UPSERT).use { ps ->
                    bindParams(ps, state)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgScanStateRepository] save failed: ${e.message}")
        }
    }

    override suspend fun delete(projectKey: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgScanStateSql.DELETE).use { ps ->
                    ps.setString(1, projectKey)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgScanStateRepository] delete failed: ${e.message}")
        }
    }

    override suspend fun findAllScanning(): List<ScanState> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgScanStateSql.FIND_ALL_SCANNING).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgScanStateRepository] findAllScanning failed: ${e.message}")
        emptyList()
    }

    private fun bindParams(ps: java.sql.PreparedStatement, state: ScanState) {
        ps.setString(1, state.projectKey)
        ps.setString(2, state.status.name)
        ps.setInt(3, state.totalTickets)
        ps.setInt(4, state.processedCount)
        ps.setString(5, state.currentTicketId)
        ps.setString(6, json.encodeToString(state.ticketIds))
        ps.setString(7, state.startedAt)
        ps.setString(8, state.updatedAt)
    }

    private fun mapRow(rs: ResultSet): ScanState = ScanState(
        projectKey = rs.getString("project_key"),
        status = ScanStatus.valueOf(rs.getString("status")),
        totalTickets = rs.getInt("total_tickets"),
        processedCount = rs.getInt("processed_count"),
        currentTicketId = rs.getString("current_ticket_id"),
        ticketIds = json.decodeFromString(rs.getString("ticket_ids")),
        startedAt = rs.getString("started_at"),
        updatedAt = rs.getString("updated_at")
    )
}
