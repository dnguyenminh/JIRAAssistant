package com.assistant.server.chat

import com.assistant.domain.NetworkGraph
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.attachment.models.ChunkType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LocalKBToolExecutorTest {

    private val embedding = floatArrayOf(0.1f, 0.2f)

    private fun chunk(type: String, file: String, text: String) =
        AttachmentChunk(
            ticketId = "t1", attachmentId = "a1", filename = file,
            chunkIndex = 0, chunkText = text, embedding = emptyList(),
            createdAt = "now", chunkType = type
        )

    private fun buildExecutor(
        embedResult: FloatArray? = embedding,
        chunks: List<AttachmentChunk> = emptyList(),
        kbRecords: Map<String, KBRecord> = emptyMap()
    ): LocalKBToolExecutor {
        val vs = FakeVectorStore(chunks)
        val emb = FakeEmbeddingService(embedResult)
        val kb = MapKBRepository(kbRecords)
        return LocalKBToolExecutor(emb, vs, kb)
    }

    // --- searchKnowledge: grouped by section (AC 19.64) ---

    @Test
    fun `searchKnowledge returns results grouped by section`() = runBlocking {
        val chunks = listOf(
            chunk(ChunkType.TICKET, "T1.ticket", "ticket data"),
            chunk(ChunkType.RELATIONSHIP, "R1.rel", "rel data")
        )
        val exec = buildExecutor(chunks = chunks)

        val result = exec.execute("search_knowledge", mapOf("query" to "test"))

        assertTrue(result.contains("--- RELEVANT TICKETS ---"))
        assertTrue(result.contains("--- RELATIONSHIPS ---"))
        assertTrue(result.contains("ticket data"))
        assertTrue(result.contains("rel data"))
    }

    // --- getTicketInfo: formatted KBRecord (AC 19.65) ---

    @Test
    fun `getTicketInfo returns formatted KBRecord`() = runBlocking {
        val record = KBRecord(
            "ITCM-129", "Login feature",
            listOf(EvolutionEntry("v1", "2024-01-01", "Initial", "CREATE")),
            5.0, 0.9, "Medium complexity",
            listOf("ITCM-130"), "2024-01-01"
        )
        val exec = buildExecutor(kbRecords = mapOf("ITCM-129" to record))

        val result = exec.execute("get_ticket_info", mapOf("ticketId" to "ITCM-129"))

        assertTrue(result.contains("Ticket: ITCM-129"))
        assertTrue(result.contains("Summary: Login feature"))
        assertTrue(result.contains("Scrum Points: 5.0"))
        assertTrue(result.contains("Confidence: 0.9"))
        assertTrue(result.contains("Evolution:"))
        assertTrue(result.contains("Similar: ITCM-130"))
    }

    // --- getTicketInfo: ticket not found (AC 19.65) ---

    @Test
    fun `getTicketInfo returns not found for missing ticket`() = runBlocking {
        val exec = buildExecutor()

        val result = exec.execute("get_ticket_info", mapOf("ticketId" to "NONE-1"))

        assertEquals("Ticket không tìm thấy trong Knowledge Base.", result)
    }

    // --- searchRelationships: chunkType = RELATIONSHIP (AC 19.66) ---

    @Test
    fun `searchRelationships filters by RELATIONSHIP chunkType`() = runBlocking {
        val chunks = listOf(
            chunk(ChunkType.RELATIONSHIP, "r.rel", "depends on X"),
            chunk(ChunkType.TICKET, "t.ticket", "should be filtered")
        )
        val exec = buildExecutor(chunks = chunks)

        val result = exec.execute("search_relationships", mapOf("query" to "deps"))

        assertTrue(result.contains("depends on X"))
        assertFalse(result.contains("should be filtered"))
    }

    // --- Missing required params (AC 19.64, 19.65, 19.66) ---

    @Test
    fun `searchKnowledge missing query returns error`() = runBlocking {
        val result = buildExecutor().execute("search_knowledge", emptyMap())
        assertEquals("Tool error: missing 'query'", result)
    }

    @Test
    fun `getTicketInfo missing ticketId returns error`() = runBlocking {
        val result = buildExecutor().execute("get_ticket_info", emptyMap())
        assertEquals("Tool error: missing 'ticketId'", result)
    }

    @Test
    fun `searchRelationships missing query returns error`() = runBlocking {
        val result = buildExecutor().execute("search_relationships", emptyMap())
        assertEquals("Tool error: missing 'query'", result)
    }

    // --- EmbeddingService unavailable (AC 19.69) ---

    @Test
    fun `searchKnowledge returns error when embedding unavailable`() = runBlocking {
        val exec = buildExecutor(embedResult = null)

        val result = exec.execute("search_knowledge", mapOf("query" to "test"))

        assertEquals("Tool error: EmbeddingService unavailable", result)
    }

    @Test
    fun `searchRelationships returns error when embedding unavailable`() = runBlocking {
        val exec = buildExecutor(embedResult = null)

        val result = exec.execute("search_relationships", mapOf("query" to "test"))

        assertEquals("Tool error: EmbeddingService unavailable", result)
    }

    // --- Unknown toolName (AC 19.69) ---

    @Test
    fun `unknown toolName returns error message`() = runBlocking {
        val result = buildExecutor().execute("nonexistent_tool", emptyMap())
        assertEquals("Tool error: Unknown tool 'nonexistent_tool'", result)
    }

    /** KBRepository backed by a simple map for testing. */
    private class MapKBRepository(
        private val records: Map<String, KBRecord>
    ) : KBRepository {
        override suspend fun findByTicketId(ticketId: String) = records[ticketId]
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }
}
