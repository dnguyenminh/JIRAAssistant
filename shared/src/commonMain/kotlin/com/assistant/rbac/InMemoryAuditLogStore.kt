package com.assistant.rbac

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory audit log store. Will be replaced by SQLDelight-backed implementation in Task 4.
 */
class InMemoryAuditLogStore : AuditLogStore {
    private val entries = mutableListOf<AuditLogEntry>()
    private val mutex = Mutex()

    override suspend fun append(entry: AuditLogEntry) {
        mutex.withLock { entries.add(entry) }
    }

    override suspend fun getRecent(limit: Int): List<AuditLogEntry> {
        mutex.withLock {
            return entries.sortedByDescending { it.timestamp }.take(limit)
        }
    }

    override suspend fun getAll(): List<AuditLogEntry> {
        mutex.withLock { return entries.toList() }
    }
}
