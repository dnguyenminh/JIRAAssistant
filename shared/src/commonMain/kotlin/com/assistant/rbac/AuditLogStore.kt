package com.assistant.rbac

/**
 * Interface for persisting audit log entries.
 * In-memory implementation for now; will be backed by SQLDelight in Task 4.
 */
interface AuditLogStore {
    suspend fun append(entry: AuditLogEntry)
    suspend fun getRecent(limit: Int): List<AuditLogEntry>
    suspend fun getAll(): List<AuditLogEntry>
}
