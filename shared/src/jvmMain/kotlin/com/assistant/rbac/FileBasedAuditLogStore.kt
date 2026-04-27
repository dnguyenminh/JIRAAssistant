package com.assistant.rbac

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based audit log store that persists entries to a JSON file.
 * Survives JVM restarts by reading/writing to `$dataDir/audit-log.json`.
 */
class FileBasedAuditLogStore(dataDir: String) : AuditLogStore {

    private val backingFile = File(dataDir, "audit-log.json")
    private val entries = mutableListOf<AuditLogEntry>()
    private val mutex = Mutex()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    init {
        loadFromFile()
    }

    override suspend fun append(entry: AuditLogEntry) {
        mutex.withLock {
            entries.add(entry)
            writeToFile()
        }
    }

    override suspend fun getRecent(limit: Int): List<AuditLogEntry> {
        mutex.withLock {
            return entries
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }

    override suspend fun getAll(): List<AuditLogEntry> {
        mutex.withLock { return entries.toList() }
    }

    private fun loadFromFile() {
        try {
            if (!backingFile.exists()) return
            val text = backingFile.readText().trim()
            if (text.isEmpty()) return
            val loaded = json.decodeFromString<List<AuditLogEntry>>(text)
            entries.addAll(loaded)
        } catch (_: Exception) {
            // Corrupt or unreadable file — start with empty list
        }
    }

    private fun writeToFile() {
        try {
            backingFile.parentFile?.mkdirs()
            backingFile.writeText(json.encodeToString(entries.toList()))
        } catch (_: Exception) {
            // Best-effort write; log in production
        }
    }
}
