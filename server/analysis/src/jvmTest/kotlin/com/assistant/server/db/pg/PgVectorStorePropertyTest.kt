package com.assistant.server.db.pg

import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.attachment.models.ChunkType
import com.assistant.server.db.DataSourceFactory
import com.assistant.server.db.DatabaseConfig
import com.assistant.server.db.FlywayMigrator
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
import javax.sql.DataSource
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Property-based tests for PgVectorStoreImpl.
 *
 * Properties 2–5 from the postgresql-pgvector-migration design document.
 * Uses Testcontainers with pgvector/pgvector:pg16 for real PostgreSQL.
 *
 * Feature: postgresql-pgvector-migration
 */
@OptIn(ExperimentalKotest::class)
@Tag("sequential")
@Testcontainers
class PgVectorStorePropertyTest {

    companion object {
        private const val EMBEDDING_DIM = 768
        private const val EPSILON = 1e-6f

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            withDatabaseName("test_vectorstore")
            withUsername("test")
            withPassword("test")
        }

        private lateinit var dataSource: DataSource
        private lateinit var store: PgVectorStoreImpl

        @JvmStatic
        @BeforeAll
        fun setup() {
            val config = DatabaseConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                maxPoolSize = 5,
                connectionTimeout = 30_000L
            )
            dataSource = DataSourceFactory.create(config)
            FlywayMigrator.migrate(dataSource)
            store = PgVectorStoreImpl(dataSource)
        }
    }

    @BeforeEach
    fun cleanTable() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.execute("DELETE FROM attachment_chunks") }
        }
    }

    // ── Generators ───────────────────────────────────────────────────

    /** Generate a valid 768-dim float array with values in [-1, 1]. */
    private val arbEmbedding768: Arb<List<Float>> =
        Arb.list(Arb.float(-1f, 1f).filter { it.isFinite() }, EMBEDDING_DIM..EMBEDDING_DIM)

    /** Generate a simple alphanumeric string for IDs. */
    private fun arbId(prefix: String = ""): Arb<String> =
        Arb.string(3..10, Codepoint.alphanumeric()).map { "$prefix$it" }

    /** Generate a valid AttachmentChunk with a given embedding. */
    private fun arbChunk(
        ticketId: String = "PROJ-1",
        attachmentId: String = "att-1",
        embedding: List<Float>? = null
    ): Arb<AttachmentChunk> = Arb.bind(
        arbEmbedding768,
        Arb.int(0..100)
    ) { emb, idx ->
        AttachmentChunk(
            ticketId = ticketId,
            attachmentId = attachmentId,
            filename = "file.txt",
            chunkIndex = idx,
            chunkText = "chunk text",
            embedding = embedding ?: emb,
            createdAt = "2024-01-01T00:00:00Z",
            chunkType = ChunkType.ATTACHMENT
        )
    }

    private val arbChunkType: Arb<String> = Arb.element(
        ChunkType.ATTACHMENT, ChunkType.TICKET,
        ChunkType.RELATIONSHIP, ChunkType.ANALYSIS
    )

    // ── Property 2: Embedding vector round-trip ──────────────────────

    /**
     * Property 2: Embedding vector round-trip
     *
     * For any valid 768-dimensional float array, inserting an AttachmentChunk
     * with that embedding into PgVectorStoreImpl and then reading it back via
     * findByTicketId SHALL produce an embedding whose values are equal to the
     * original (within floating-point epsilon of 1e-6).
     *
     * **Validates: Requirements 4.4, 5.4, 7.3**
     *
     * Feature: postgresql-pgvector-migration, Property 2: Embedding vector round-trip
     */
    @Test
    fun `Property 2 - embedding vector round-trip preserves values`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbEmbedding768
            ) { embedding ->
                cleanTable()
                val ticketId = "RT-${embedding.hashCode()}"
                val chunk = AttachmentChunk(
                    ticketId = ticketId,
                    attachmentId = "att-rt-${embedding.hashCode()}",
                    filename = "test.txt",
                    chunkIndex = 0,
                    chunkText = "round trip test",
                    embedding = embedding,
                    createdAt = "2024-01-01T00:00:00Z",
                    chunkType = ChunkType.ATTACHMENT
                )

                val saved = store.saveChunk(chunk)
                assertTrue(saved, "saveChunk should succeed")

                val found = store.findByTicketId(ticketId)
                assertEquals(1, found.size, "Should find exactly 1 chunk")

                val readBack = found.first().embedding
                assertEquals(EMBEDDING_DIM, readBack.size, "Dimension must be 768")

                embedding.forEachIndexed { i, expected ->
                    assertTrue(
                        abs(expected - readBack[i]) < EPSILON,
                        "Embedding[$i]: expected=$expected, got=${readBack[i]}"
                    )
                }
            }
        }
    }


    // ── Property 3: Wrong dimension rejection ────────────────────────

    /**
     * Property 3: Wrong dimension rejection
     *
     * For any float array whose length is not 768, attempting to insert an
     * AttachmentChunk with that embedding via PgVectorStoreImpl SHALL fail
     * with a descriptive error and SHALL NOT persist the chunk.
     *
     * **Validates: Requirements 4.5**
     *
     * Feature: postgresql-pgvector-migration, Property 3: Wrong dimension rejection
     */
    @Test
    fun `Property 3 - wrong dimension embedding is rejected`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(0..2000).filter { it != EMBEDDING_DIM }
            ) { dim ->
                cleanTable()
                val embedding = List(dim) { 0.1f }
                val ticketId = "WD-$dim"
                val chunk = AttachmentChunk(
                    ticketId = ticketId,
                    attachmentId = "att-wd-$dim",
                    filename = "bad.txt",
                    chunkIndex = 0,
                    chunkText = "wrong dim",
                    embedding = embedding,
                    createdAt = "2024-01-01T00:00:00Z",
                    chunkType = ChunkType.ATTACHMENT
                )

                val result = store.saveChunk(chunk)
                assertFalse(result, "saveChunk should fail for dim=$dim")

                val found = store.findByTicketId(ticketId)
                assertTrue(found.isEmpty(), "No chunk should be persisted for dim=$dim")
            }
        }
    }

    // ── Property 4: Vector search correctness ────────────────────────

    /**
     * Property 4: Vector search correctness
     *
     * For any set of inserted chunks with random 768-dim embeddings and for
     * any random query embedding, calling search(queryEmbedding, topK, chunkType)
     * SHALL return at most topK results, all results SHALL have chunkType
     * matching the filter (when non-null), and results SHALL be ordered by
     * ascending cosine distance from the query embedding.
     *
     * **Validates: Requirements 5.2, 5.3**
     *
     * Feature: postgresql-pgvector-migration, Property 4: Vector search correctness
     */
    @Test
    fun `Property 4 - vector search returns correct topK ordered by cosine distance`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(2..8),       // number of chunks to insert
                Arb.int(1..5),       // topK
                Arb.boolean(),       // whether to filter by chunkType
                arbChunkType         // chunkType to use for filter
            ) { numChunks, topK, useFilter, filterType ->
                cleanTable()

                val chunkTypes = listOf(
                    ChunkType.ATTACHMENT, ChunkType.TICKET,
                    ChunkType.RELATIONSHIP, ChunkType.ANALYSIS
                )
                val insertedChunks = mutableListOf<AttachmentChunk>()

                // Insert chunks with random embeddings and varied types
                for (i in 0 until numChunks) {
                    val emb = generateRandomEmbedding()
                    val ct = chunkTypes[i % chunkTypes.size]
                    val chunk = AttachmentChunk(
                        ticketId = "SEARCH-$i",
                        attachmentId = "att-search-$i",
                        filename = "f$i.txt",
                        chunkIndex = 0,
                        chunkText = "text $i",
                        embedding = emb,
                        createdAt = "2024-01-01T00:00:00Z",
                        chunkType = ct
                    )
                    store.saveChunk(chunk)
                    insertedChunks.add(chunk)
                }

                val queryEmb = generateRandomEmbedding().toFloatArray()
                val typeFilter = if (useFilter) filterType else null
                val results = store.search(queryEmb, topK, typeFilter)

                // At most topK results
                assertTrue(
                    results.size <= topK,
                    "Expected at most $topK results, got ${results.size}"
                )

                // chunkType filter
                if (typeFilter != null) {
                    results.forEach { r ->
                        assertEquals(
                            typeFilter, r.chunkType,
                            "All results must match filter type=$typeFilter"
                        )
                    }
                    val matchingCount = insertedChunks.count { it.chunkType == typeFilter }
                    assertTrue(
                        results.size <= matchingCount,
                        "Results (${results.size}) should not exceed matching chunks ($matchingCount)"
                    )
                }

                // Ordered by ascending cosine distance
                if (results.size >= 2) {
                    val distances = results.map { cosineDistance(queryEmb, it.embedding.toFloatArray()) }
                    for (i in 0 until distances.size - 1) {
                        assertTrue(
                            distances[i] <= distances[i + 1] + EPSILON,
                            "Results not ordered: dist[$i]=${distances[i]} > dist[${i+1}]=${distances[i+1]}"
                        )
                    }
                }
            }
        }
    }


    // ── Property 5: VectorStore CRUD round-trip ──────────────────────

    /**
     * Property 5: VectorStore CRUD round-trip
     *
     * For any set of AttachmentChunk objects with distinct ticket IDs and
     * attachment IDs, after saving all chunks via saveChunk:
     * - existsByAttachmentId(id) SHALL return true for every saved attachment
     *   ID and false for any unsaved ID
     * - findByTicketId(ticketId) SHALL return exactly the chunks saved for
     *   that ticket
     * - deleteByTicketId(ticketId) SHALL remove all chunks for that ticket,
     *   and subsequent findByTicketId SHALL return empty
     *
     * **Validates: Requirements 5.5, 6.2**
     *
     * Feature: postgresql-pgvector-migration, Property 5: VectorStore CRUD round-trip
     */
    @Test
    fun `Property 5 - CRUD round-trip for VectorStore`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(1..5) // number of distinct tickets
            ) { numTickets ->
                cleanTable()

                val tickets = (1..numTickets).map { "CRUD-$it-${System.nanoTime()}" }
                val savedChunks = mutableMapOf<String, MutableList<AttachmentChunk>>()

                // Save 1-3 chunks per ticket
                for (ticketId in tickets) {
                    val chunksForTicket = mutableListOf<AttachmentChunk>()
                    val numChunks = (ticketId.hashCode().mod(3)) + 1
                    for (j in 0 until numChunks) {
                        val emb = generateRandomEmbedding()
                        val attId = "$ticketId-att-$j"
                        val chunk = AttachmentChunk(
                            ticketId = ticketId,
                            attachmentId = attId,
                            filename = "file$j.txt",
                            chunkIndex = j,
                            chunkText = "text $j",
                            embedding = emb,
                            createdAt = "2024-01-01T00:00:00Z",
                            chunkType = ChunkType.ATTACHMENT
                        )
                        val saved = store.saveChunk(chunk)
                        assertTrue(saved, "saveChunk should succeed for $attId")
                        chunksForTicket.add(chunk)
                    }
                    savedChunks[ticketId] = chunksForTicket
                }

                // existsByAttachmentId: true for saved, false for unsaved
                for ((_, chunks) in savedChunks) {
                    for (chunk in chunks) {
                        assertTrue(
                            store.existsByAttachmentId(chunk.attachmentId),
                            "existsByAttachmentId should be true for ${chunk.attachmentId}"
                        )
                    }
                }
                assertFalse(
                    store.existsByAttachmentId("nonexistent-att-${System.nanoTime()}"),
                    "existsByAttachmentId should be false for unsaved ID"
                )

                // findByTicketId: returns exactly the saved chunks
                for ((ticketId, chunks) in savedChunks) {
                    val found = store.findByTicketId(ticketId)
                    assertEquals(
                        chunks.size, found.size,
                        "findByTicketId($ticketId) count mismatch"
                    )
                    val foundAttIds = found.map { it.attachmentId }.toSet()
                    val expectedAttIds = chunks.map { it.attachmentId }.toSet()
                    assertEquals(expectedAttIds, foundAttIds, "Attachment IDs mismatch")
                }

                // deleteByTicketId: removes all chunks for that ticket
                val ticketToDelete = tickets.first()
                val deleted = store.deleteByTicketId(ticketToDelete)
                assertTrue(deleted, "deleteByTicketId should succeed")

                val afterDelete = store.findByTicketId(ticketToDelete)
                assertTrue(
                    afterDelete.isEmpty(),
                    "findByTicketId after delete should be empty"
                )

                // Other tickets remain
                for (ticketId in tickets.drop(1)) {
                    val found = store.findByTicketId(ticketId)
                    assertEquals(
                        savedChunks[ticketId]!!.size, found.size,
                        "Other ticket $ticketId should still have its chunks"
                    )
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun generateRandomEmbedding(): List<Float> {
        val rng = java.util.Random()
        return List(EMBEDDING_DIM) { (rng.nextFloat() * 2f - 1f) }
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom == 0.0) return 1.0
        return 1.0 - (dot / denom)
    }
}
