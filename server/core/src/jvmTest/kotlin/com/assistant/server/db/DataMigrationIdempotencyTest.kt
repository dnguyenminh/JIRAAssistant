package com.assistant.server.db

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * Properties 7 and 8 for DataMigrationService.
 *
 * Feature: postgresql-pgvector-migration
 */
@OptIn(ExperimentalKotest::class)
@Tag("sequential")
@Testcontainers
class DataMigrationIdempotencyTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            withDatabaseName("test_idempotency")
            withUsername("test")
            withPassword("test")
        }

        private lateinit var dataSource: DataSource

        @JvmStatic
        @BeforeAll
        fun setup() {
            dataSource = DataSourceFactory.create(
                DatabaseConfig(
                    jdbcUrl = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password,
                    maxPoolSize = 5,
                    connectionTimeout = 30_000L
                )
            )
            FlywayMigrator.migrate(dataSource)
        }
    }

    @BeforeEach
    fun cleanPg() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                DataMigrationTestHelper.ALL_TABLES.forEach {
                    stmt.execute("DELETE FROM $it")
                }
            }
        }
    }

    /**
     * Property 7: Data migration idempotency
     *
     * For any SQLite database with data, running DataMigrationService twice
     * against the same PostgreSQL database SHALL produce the same row counts
     * as running it once — no duplicate rows SHALL be created.
     *
     * **Validates: Requirements 7.6**
     *
     * Feature: postgresql-pgvector-migration, Property 7: Data migration idempotency
     */
    @Test
    fun `Property 7 - data migration idempotency`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(1..3),
                Arb.string(3..6, Codepoint.alphanumeric())
            ) { numRecords, suffix ->
                cleanPg()
                val tmpFile = createSqliteFile(numRecords, suffix)
                try {
                    val svc = DataMigrationService(tmpFile.absolutePath, dataSource)
                    svc.migrate()
                    val first = captureRowCounts()
                    svc.migrate()
                    val second = captureRowCounts()
                    assertEquals(first, second, "Row counts must match after second run")
                } finally {
                    tmpFile.delete()
                }
            }
        }
    }

    /**
     * Property 8: SQLite file immutability during migration
     *
     * For any SQLite database file, after running DataMigrationService,
     * the file's content (byte-level hash) SHALL be identical to its
     * content before migration.
     *
     * **Validates: Requirements 10.1**
     *
     * Feature: postgresql-pgvector-migration, Property 8: SQLite file immutability during migration
     */
    @Test
    fun `Property 8 - SQLite file immutability during migration`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(1..3),
                Arb.string(3..6, Codepoint.alphanumeric())
            ) { numRecords, suffix ->
                cleanPg()
                val tmpFile = createSqliteFile(numRecords, suffix)
                try {
                    val hashBefore = sha256(tmpFile)
                    DataMigrationService(tmpFile.absolutePath, dataSource).migrate()
                    val hashAfter = sha256(tmpFile)
                    assertEquals(hashBefore, hashAfter, "SQLite file must not be modified")
                } finally {
                    tmpFile.delete()
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun createSqliteFile(numRecords: Int, suffix: String): File {
        val f = Files.createTempFile("idem-test-", ".db").toFile()
        DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}").use { c ->
            DataMigrationTestHelper.createSqliteSchema(c)
            for (i in 1..numRecords) {
                val id = "IDEM-$suffix-$i"
                DataMigrationTestHelper.insertKbRecord(c, id, "sum-$id")
                DataMigrationTestHelper.insertAppSetting(c, "k-$id", "v-$id")
            }
        }
        return f
    }

    private fun captureRowCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        dataSource.connection.use { pg ->
            DataMigrationTestHelper.ALL_TABLES.forEach { table ->
                counts[table] = DataMigrationTestHelper.countPgRows(pg, table)
            }
        }
        return counts
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
