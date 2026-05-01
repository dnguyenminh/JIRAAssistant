package com.assistant.server.db

import com.pgvector.PGvector
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
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource
import kotlin.math.abs

/**
 * Property 6: Data migration preserves row values.
 *
 * Feature: postgresql-pgvector-migration
 */
@OptIn(ExperimentalKotest::class)
@Tag("sequential")
@Testcontainers
class DataMigrationPropertyTest {

    companion object {
        private const val DIM = 768
        private const val EPSILON = 1e-6f

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            withDatabaseName("test_migration")
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
                DataMigrationTestHelper.ALL_TABLES.forEach { stmt.execute("DELETE FROM $it") }
            }
        }
    }

    private val arbEmb: Arb<List<Float>> =
        Arb.list(Arb.float(-1f, 1f).filter { it.isFinite() }, DIM..DIM)

    /**
     * Property 6: Data migration preserves row values
     *
     * For any set of rows written to a SQLite database across all 14 tables,
     * running the DataMigrationService SHALL produce identical rows in the
     * corresponding PostgreSQL tables (with embedding columns converted from
     * JSON text to vector values that are numerically equivalent).
     *
     * **Validates: Requirements 7.2, 7.3**
     *
     * Feature: postgresql-pgvector-migration, Property 6: Data migration preserves row values
     */
    @Test
    fun `Property 6 - data migration preserves row values`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(3..8, Codepoint.alphanumeric()),
                Arb.string(5..10, Codepoint.alphanumeric()),
                arbEmb
            ) { suffix, settingVal, embedding ->
                cleanPg()
                val ticketId = "MIG-$suffix"
                val settingKey = "key-$suffix"
                val attId = "att-$suffix"
                val tmpFile = createSqliteFile(ticketId, settingKey, settingVal, attId, embedding)
                try {
                    DataMigrationService(tmpFile.absolutePath, dataSource).migrate()
                    verifyKbRecord(ticketId)
                    verifyAppSetting(settingKey, settingVal)
                    verifyEmbedding(attId, embedding)
                } finally {
                    tmpFile.delete()
                }
            }
        }
    }

    private fun createSqliteFile(
        ticketId: String, key: String, value: String,
        attId: String, emb: List<Float>
    ): File {
        val f = Files.createTempFile("mig-test-", ".db").toFile()
        DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}").use { c ->
            DataMigrationTestHelper.createSqliteSchema(c)
            DataMigrationTestHelper.insertKbRecord(c, ticketId, "summary-$ticketId")
            DataMigrationTestHelper.insertAppSetting(c, key, value)
            DataMigrationTestHelper.insertAttachmentChunk(c, ticketId, attId, emb)
        }
        return f
    }

    private fun verifyKbRecord(ticketId: String) {
        dataSource.connection.use { pg ->
            pg.prepareStatement("SELECT requirement_summary FROM kb_records WHERE ticket_id = ?").use { ps ->
                ps.setString(1, ticketId)
                ps.executeQuery().use { rs ->
                    assertTrue(rs.next(), "kb_record must exist for $ticketId")
                    assertEquals("summary-$ticketId", rs.getString(1))
                }
            }
        }
    }

    private fun verifyAppSetting(key: String, value: String) {
        dataSource.connection.use { pg ->
            pg.prepareStatement("SELECT setting_value FROM app_settings WHERE setting_key = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    assertTrue(rs.next(), "app_setting must exist for $key")
                    assertEquals(value, rs.getString(1))
                }
            }
        }
    }

    private fun verifyEmbedding(attId: String, expected: List<Float>) {
        dataSource.connection.use { pg ->
            pg.prepareStatement("SELECT embedding FROM attachment_chunks WHERE attachment_id = ?").use { ps ->
                ps.setString(1, attId)
                ps.executeQuery().use { rs ->
                    assertTrue(rs.next(), "chunk must exist for $attId")
                    val raw = rs.getString("embedding")
                    val vec = raw.removePrefix("[").removeSuffix("]")
                        .split(",").map { it.trim().toFloat() }
                    assertEquals(DIM, vec.size)
                    expected.forEachIndexed { i, exp ->
                        assertTrue(abs(exp - vec[i]) < EPSILON, "Embedding[$i] mismatch")
                    }
                }
            }
        }
    }
}
