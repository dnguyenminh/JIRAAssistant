package com.assistant.scan

/**
 * Repository interface for scan log persistence.
 * Interface lives in commonMain; JVM implementation uses SQLDelight.
 */
interface ScanLogRepository {
    suspend fun addEntry(entry: ScanLogEntry)
    suspend fun getByProjectKey(projectKey: String, limit: Long = 50): List<ScanLogEntry>
    suspend fun getByProjectKeyPaged(projectKey: String, limit: Long, offset: Long): List<ScanLogEntry>
    suspend fun countByProjectKey(projectKey: String): Long
    suspend fun deleteByProjectKey(projectKey: String)
}
