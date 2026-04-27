package com.assistant.server.db

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Runs Flyway schema migrations against the given [DataSource].
 *
 * Configured to load SQL files from `classpath:db/migration`.
 * Throws on failure so the application exits at startup.
 *
 * Requirements: 3.1, 3.3, 3.7
 */
object FlywayMigrator {

    private val logger = LoggerFactory.getLogger(FlywayMigrator::class.java)

    private const val MIGRATION_LOCATION = "classpath:db/migration"

    fun migrate(dataSource: DataSource) {
        logger.info("Starting Flyway migration from {}", MIGRATION_LOCATION)
        val result = buildFlyway(dataSource).migrate()
        logger.info(
            "Flyway migration completed: {} migrations applied (schema version: {})",
            result.migrationsExecuted,
            result.targetSchemaVersion
        )
        syncSequences(dataSource)
    }

    /**
     * Ensures BIGSERIAL sequences are in sync with MAX(id).
     * Prevents "duplicate key" errors after bulk imports or restores.
     */
    private fun syncSequences(dataSource: DataSource) {
        val pairs = listOf(
            "chat_messages_id_seq" to "chat_messages",
            "scan_log_id_seq" to "scan_log",
            "attachment_chunks_id_seq" to "attachment_chunks"
        )
        try {
            dataSource.connection.use { conn ->
                for ((seq, table) in pairs) {
                    val maxId = conn.prepareStatement(
                        "SELECT COALESCE(MAX(id), 0) FROM $table"
                    ).use { ps -> ps.executeQuery().use { if (it.next()) it.getLong(1) else 0L } }
                    val seqVal = conn.prepareStatement(
                        "SELECT last_value FROM $seq"
                    ).use { ps -> ps.executeQuery().use { if (it.next()) it.getLong(1) else 0L } }
                    if (seqVal < maxId) {
                        conn.prepareStatement("SELECT setval('$seq', $maxId)").use { it.execute() }
                        logger.warn("Sequence {} was behind ({}), reset to {}", seq, seqVal, maxId)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Sequence sync check failed: {}", e.message)
        }
    }

    private fun buildFlyway(dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations(MIGRATION_LOCATION)
            .load()
}
