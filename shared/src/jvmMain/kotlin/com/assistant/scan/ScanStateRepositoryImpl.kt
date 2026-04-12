package com.assistant.scan

import com.assistant.db.JiraDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JVM implementation of [ScanStateRepository] backed by SQLDelight [JiraDatabase].
 * The ticketIds field is serialized to/from a JSON string for storage.
 */
class ScanStateRepositoryImpl(
    private val database: JiraDatabase
) : ScanStateRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun findByProjectKey(projectKey: String): ScanState? {
        val row = database.knowledgeBaseQueries
            .findScanStateByProjectKey(projectKey)
            .executeAsOneOrNull() ?: return null
        return ScanState(
            projectKey = row.project_key,
            status = ScanStatus.valueOf(row.status),
            totalTickets = row.total_tickets.toInt(),
            processedCount = row.processed_count.toInt(),
            currentTicketId = row.current_ticket_id,
            ticketIds = json.decodeFromString(row.ticket_ids),
            startedAt = row.started_at,
            updatedAt = row.updated_at
        )
    }

    override suspend fun save(state: ScanState) {
        database.knowledgeBaseQueries.insertOrReplaceScanState(
            project_key = state.projectKey,
            status = state.status.name,
            total_tickets = state.totalTickets.toLong(),
            processed_count = state.processedCount.toLong(),
            current_ticket_id = state.currentTicketId,
            ticket_ids = json.encodeToString(state.ticketIds),
            started_at = state.startedAt,
            updated_at = state.updatedAt
        )
    }

    override suspend fun delete(projectKey: String) {
        database.knowledgeBaseQueries.deleteScanState(projectKey)
    }

    override suspend fun findAllScanning(): List<ScanState> {
        return database.knowledgeBaseQueries
            .findScanningScanStates()
            .executeAsList()
            .map { row ->
                ScanState(
                    projectKey = row.project_key,
                    status = ScanStatus.valueOf(row.status),
                    totalTickets = row.total_tickets.toInt(),
                    processedCount = row.processed_count.toInt(),
                    currentTicketId = row.current_ticket_id,
                    ticketIds = json.decodeFromString(row.ticket_ids),
                    startedAt = row.started_at,
                    updatedAt = row.updated_at
                )
            }
    }
}
