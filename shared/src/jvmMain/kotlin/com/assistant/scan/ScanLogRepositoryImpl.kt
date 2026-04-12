package com.assistant.scan

import com.assistant.db.JiraDatabase

/**
 * JVM implementation of [ScanLogRepository] backed by SQLDelight [JiraDatabase].
 * Maps SQLDelight generated row types to [ScanLogEntry] domain model.
 */
class ScanLogRepositoryImpl(
    private val database: JiraDatabase
) : ScanLogRepository {

    override suspend fun addEntry(entry: ScanLogEntry) {
        database.knowledgeBaseQueries.insertScanLogEntry(
            project_key = entry.projectKey,
            ticket_id = entry.ticketId,
            status = entry.status.name,
            message = entry.message,
            timestamp = entry.timestamp
        )
    }

    override suspend fun getByProjectKey(projectKey: String, limit: Long): List<ScanLogEntry> {
        return database.knowledgeBaseQueries
            .getScanLogByProjectKey(projectKey, limit)
            .executeAsList()
            .map { row ->
                ScanLogEntry(
                    id = row.id,
                    projectKey = row.project_key,
                    ticketId = row.ticket_id,
                    status = ScanLogStatus.valueOf(row.status),
                    message = row.message,
                    timestamp = row.timestamp
                )
            }
    }

    override suspend fun deleteByProjectKey(projectKey: String) {
        database.knowledgeBaseQueries.deleteScanLogByProjectKey(projectKey)
    }

    override suspend fun getByProjectKeyPaged(projectKey: String, limit: Long, offset: Long): List<ScanLogEntry> {
        return database.knowledgeBaseQueries
            .getScanLogByProjectKeyPaged(projectKey, limit, offset)
            .executeAsList()
            .map { row ->
                ScanLogEntry(
                    id = row.id, projectKey = row.project_key,
                    ticketId = row.ticket_id, status = ScanLogStatus.valueOf(row.status),
                    message = row.message, timestamp = row.timestamp
                )
            }
    }

    override suspend fun countByProjectKey(projectKey: String): Long {
        return database.knowledgeBaseQueries.countScanLogByProjectKey(projectKey).executeAsOne()
    }
}
