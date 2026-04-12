package com.assistant.scan

/**
 * Repository interface for scan state persistence.
 * Interface lives in commonMain; JVM implementation uses SQLDelight.
 */
interface ScanStateRepository {
    suspend fun findByProjectKey(projectKey: String): ScanState?
    suspend fun save(state: ScanState)
    suspend fun delete(projectKey: String)
    suspend fun findAllScanning(): List<ScanState>
}
