package com.assistant.server.db

import javax.sql.DataSource

/**
 * Stub for SQLite → PostgreSQL data migration.
 * TODO: Full implementation pending postgresql-pgvector-migration spec.
 */
class DataMigrationService(
    private val sqlitePath: String,
    private val dataSource: DataSource
) {
    suspend fun migrate() {
        // Stub — not yet implemented
    }
}
