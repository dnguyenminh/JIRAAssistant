package com.assistant.server.chat

import com.assistant.server.attachment.models.AttachmentChunk
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 6: Knowledge context building — format and limits
 * buildKnowledgeContext must return formatted "[filename] chunkText" lines,
 * grouped by section, top-10 results, and return "" when services are null.
 * Validates: Requirements 15.1, 15.2, 15.3, 15.4, 15.5
 */
@OptIn(ExperimentalKotest::class)
class AttachmentContextPropertyTest {

    private fun makeChunk(filename: String, text: String, idx: Int = 0) = AttachmentChunk(
        ticketId = "T-1", attachmentId = "att-$idx",
        filename = filename, chunkIndex = idx,
        chunkText = text, embedding = listOf(0.1f, 0.2f),
        createdAt = "2025-01-01T00:00:00Z"
    )

    @Test
    fun `context format is bracket-filename bracket-space chunkText per line`() {
        runBlocking {
            val arbFilename = Arb.string(minSize = 3, maxSize = 15, codepoints = Codepoint.alphanumeric())
                .map { "$it.pdf" }
            val arbText = Arb.string(minSize = 5, maxSize = 50, codepoints = Codepoint.alphanumeric())

            checkAll(PropTestConfig(iterations = 25), arbFilename, arbText) { filename, text ->
                val chunks = listOf(makeChunk(filename, text))
                val service = ChatServiceImpl(
                    aiAgentProvider = { CapturingAIAgent() },
                    kbRepository = StubKBRepository(),
                    graphEngine = StubGraphEngine(),
                    embeddingService = FakeEmbeddingService(floatArrayOf(0.1f, 0.2f)),
                    vectorStore = FakeVectorStore(chunks)
                )

                val result = runBlocking { service.buildKnowledgeContext("PROJ", "test query") }
                assertTrue(result.contains("[$filename]"),
                    "Result must contain [filename], got: $result")
                assertTrue(result.contains(text),
                    "Result must contain chunk text")
            }
        }
    }

    @Test
    fun `returns at most 10 chunks`() {
        runBlocking {
            val chunks = (1..15).map { makeChunk("file$it.pdf", "content $it", it) }
            val service = ChatServiceImpl(
                aiAgentProvider = { CapturingAIAgent() },
                kbRepository = StubKBRepository(),
                graphEngine = StubGraphEngine(),
                embeddingService = FakeEmbeddingService(floatArrayOf(0.1f, 0.2f)),
                vectorStore = FakeVectorStore(chunks)
            )

            val result = service.buildKnowledgeContext("PROJ", "query")
            val chunkLines = result.split("\n").filter { it.startsWith("[") }
            assertTrue(chunkLines.size <= 10, "Must return at most 10 chunks, got ${chunkLines.size}")
        }
    }

    @Test
    fun `returns empty string when embedding service is null`() {
        val service = ChatServiceImpl(
            aiAgentProvider = { CapturingAIAgent() },
            kbRepository = StubKBRepository(),
            graphEngine = StubGraphEngine(),
            embeddingService = null,
            vectorStore = FakeVectorStore(emptyList())
        )
        runBlocking {
            val result = service.buildKnowledgeContext("PROJ", "query")
            assertEquals("", result, "Must return empty when embeddingService is null")
        }
    }

    @Test
    fun `returns empty string when vector store is null`() {
        val service = ChatServiceImpl(
            aiAgentProvider = { CapturingAIAgent() },
            kbRepository = StubKBRepository(),
            graphEngine = StubGraphEngine(),
            embeddingService = FakeEmbeddingService(floatArrayOf(0.1f)),
            vectorStore = null
        )
        runBlocking {
            val result = service.buildKnowledgeContext("PROJ", "query")
            assertEquals("", result, "Must return empty when vectorStore is null")
        }
    }

    @Test
    fun `returns no-data message when no chunks found`() {
        val service = ChatServiceImpl(
            aiAgentProvider = { CapturingAIAgent() },
            kbRepository = StubKBRepository(),
            graphEngine = StubGraphEngine(),
            embeddingService = FakeEmbeddingService(floatArrayOf(0.1f, 0.2f)),
            vectorStore = FakeVectorStore(emptyList())
        )
        runBlocking {
            val result = service.buildKnowledgeContext("PROJ", "query")
            assertEquals("No attachment data.", result,
                "Must return 'No attachment data.' when no chunks found")
        }
    }
}
