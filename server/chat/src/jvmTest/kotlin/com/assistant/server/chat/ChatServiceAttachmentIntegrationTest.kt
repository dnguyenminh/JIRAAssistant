package com.assistant.server.chat

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import com.assistant.chat.ChatContext
import com.assistant.chat.ChatMessage
import com.assistant.domain.NetworkGraph
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.GraphLayout
import com.assistant.graph.Bounds
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration test: ChatService + VectorStore attachment context in prompt.
 * Requirements: 22.16, 22.17, 22.18
 */
class ChatServiceAttachmentIntegrationTest {

    private fun buildService(
        embedding: EmbeddingService? = null,
        vectorStore: VectorStore? = null
    ): Pair<ChatServiceImpl, CapturingAIAgent> {
        val agent = CapturingAIAgent()
        val svc = ChatServiceImpl(
            aiAgentProvider = { agent },
            kbRepository = StubKBRepository(),
            graphEngine = StubGraphEngine(),
            embeddingService = embedding,
            vectorStore = vectorStore
        )
        return svc to agent
    }

    @Test
    fun `prompt contains KNOWLEDGE CONTEXT section with chunk data`() = runBlocking {
        val chunks = listOf(
            makeChunk("report.pdf", "Revenue grew 15%"),
            makeChunk("spec.docx", "Login must use OAuth2")
        )
        val (svc, agent) = buildService(
            embedding = FakeEmbeddingService(floatArrayOf(0.1f, 0.2f)),
            vectorStore = FakeVectorStore(chunks)
        )
        agent.nextResponse = AIResult.Success("""{"reply":"ok","actions":[],"references":[]}""")

        svc.processChat("Tell me about revenue", chatCtx(), emptyList())

        val prompt = agent.lastPrompt!!
        assertTrue(prompt.contains("--- KNOWLEDGE CONTEXT ---"))
        assertTrue(prompt.contains("[report.pdf] Revenue grew 15%"))
        assertTrue(prompt.contains("[spec.docx] Login must use OAuth2"))
    }

    @Test
    fun `prompt contains No attachment data when VectorStore returns empty`() = runBlocking {
        val (svc, agent) = buildService(
            embedding = FakeEmbeddingService(floatArrayOf(0.1f)),
            vectorStore = FakeVectorStore(emptyList())
        )
        agent.nextResponse = AIResult.Success("ok")

        svc.processChat("anything", chatCtx(), emptyList())

        assertTrue(agent.lastPrompt!!.contains("No attachment data."))
    }

    @Test
    fun `prompt works without knowledge context when services are null`() = runBlocking {
        val (svc, agent) = buildService(embedding = null, vectorStore = null)
        agent.nextResponse = AIResult.Success("ok")

        svc.processChat("hello", chatCtx(), emptyList())

        val prompt = agent.lastPrompt!!
        assertTrue(prompt.contains("--- KNOWLEDGE CONTEXT ---"))
        val ctxSection = extractKnowledgeSection(prompt)
        assertFalse(ctxSection.contains("["))  // no chunk format in section
    }

    private fun extractKnowledgeSection(prompt: String): String {
        val start = prompt.indexOf("--- KNOWLEDGE CONTEXT ---")
        if (start < 0) return ""
        val after = prompt.substring(start + "--- KNOWLEDGE CONTEXT ---".length)
        val end = after.indexOf("---")
        return if (end >= 0) after.substring(0, end).trim() else after.trim()
    }

    @Test
    fun `prompt works when embedding returns null`() = runBlocking {
        val (svc, agent) = buildService(
            embedding = FakeEmbeddingService(null),
            vectorStore = FakeVectorStore(emptyList())
        )
        agent.nextResponse = AIResult.Success("ok")

        svc.processChat("test", chatCtx(), emptyList())

        val prompt = agent.lastPrompt!!
        assertTrue(prompt.contains("--- KNOWLEDGE CONTEXT ---"))
        assertFalse(prompt.contains("No attachment data."))
    }

    @Test
    fun `knowledge context limited to topK 10 chunks`() = runBlocking {
        val chunks = (1..10).map { makeChunk("file$it.pdf", "chunk $it") }
        val (svc, _) = buildService(
            embedding = FakeEmbeddingService(floatArrayOf(1f)),
            vectorStore = FakeVectorStore(chunks)
        )
        val ctx = svc.buildKnowledgeContext("PROJ", "query")

        val chunkLines = ctx.split("\n").filter { it.startsWith("[") }
        assertEquals(10, chunkLines.size)
        assertTrue(ctx.contains("[file1.pdf]"))
        assertTrue(ctx.contains("[file10.pdf]"))
    }

    @Test
    fun `buildKnowledgeContext formats each chunk correctly`() = runBlocking {
        val chunk = makeChunk("design.pdf", "Architecture overview")
        val (svc, _) = buildService(
            embedding = FakeEmbeddingService(floatArrayOf(0.5f)),
            vectorStore = FakeVectorStore(listOf(chunk))
        )
        val ctx = svc.buildKnowledgeContext("PROJ", "architecture")

        assertTrue(ctx.contains("--- ATTACHMENTS ---"))
        assertTrue(ctx.contains("[design.pdf] Architecture overview"))
    }

    // --- Helpers ---

    private fun chatCtx() = ChatContext("PROJ", "dashboard", "READER", "u1")

    private fun makeChunk(filename: String, text: String) = AttachmentChunk(
        ticketId = "PROJ-1", attachmentId = "att-1",
        filename = filename, chunkIndex = 0,
        chunkText = text, embedding = listOf(0.1f),
        createdAt = "2024-01-01T00:00:00Z"
    )
}
