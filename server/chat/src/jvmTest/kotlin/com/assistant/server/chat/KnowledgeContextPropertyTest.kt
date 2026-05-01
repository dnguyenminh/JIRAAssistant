package com.assistant.server.chat

import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.attachment.models.ChunkType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 8: Knowledge Context Output Grouping
 *
 * For any list of AttachmentChunks with various chunkTypes,
 * formatKnowledgeChunks SHALL group chunks by chunkType and output
 * the correct section headers. Chunks of the same type SHALL appear
 * within the same section.
 *
 * **Validates: Requirements 15.2, 15.3**
 */
@OptIn(ExperimentalKotest::class)
class KnowledgeContextPropertyTest {

    companion object {
        private val ALL_CHUNK_TYPES = listOf(
            ChunkType.TICKET, ChunkType.CLUSTER,
            ChunkType.RELATIONSHIP,
            ChunkType.ANALYSIS, ChunkType.EVOLUTION,
            ChunkType.ATTACHMENT, ChunkType.CONFLUENCE
        )

        private val SECTION_ORDER = listOf(
            "--- RELEVANT TICKETS ---",
            "--- RELATIONSHIPS ---",
            "--- ANALYSIS ---",
            "--- CONFLUENCE DOCS ---",
            "--- ATTACHMENTS ---"
        )

        private fun expectedSection(chunkType: String): String = when (chunkType) {
            ChunkType.TICKET, ChunkType.CLUSTER -> "--- RELEVANT TICKETS ---"
            ChunkType.RELATIONSHIP -> "--- RELATIONSHIPS ---"
            ChunkType.ANALYSIS, ChunkType.EVOLUTION -> "--- ANALYSIS ---"
            ChunkType.CONFLUENCE -> "--- CONFLUENCE DOCS ---"
            else -> "--- ATTACHMENTS ---"
        }
    }

    private fun genAttachmentChunk(): Arb<AttachmentChunk> {
        val arbType = Arb.element(ALL_CHUNK_TYPES)
        val arbFilename = Arb.string(3, 12, Codepoint.alphanumeric())
        val arbText = Arb.string(5, 40, Codepoint.alphanumeric())
        return Arb.bind(arbType, arbFilename, arbText) { type, name, text ->
            AttachmentChunk(
                ticketId = "T-1", attachmentId = "att-${name.hashCode()}",
                filename = "$name.txt", chunkIndex = 0,
                chunkText = text, embedding = listOf(0.1f),
                createdAt = "2025-01-01T00:00:00Z", chunkType = type
            )
        }
    }

    private fun buildService(): ChatServiceImpl = ChatServiceImpl(
        aiAgentProvider = { CapturingAIAgent() },
        kbRepository = StubKBRepository(),
        graphEngine = StubGraphEngine(),
        embeddingService = null,
        vectorStore = null
    )

    @Test
    fun `section headers match present chunkTypes`() {
        runBlocking {
            val service = buildService()
            val arbChunks = Arb.list(genAttachmentChunk(), 1..15)

            checkAll(PropTestConfig(iterations = 25), arbChunks) { chunks ->
                val result = service.formatKnowledgeChunks(chunks)
                val expectedSections = chunks
                    .map { expectedSection(it.chunkType) }.toSet()

                for (section in expectedSections) {
                    assertTrue(result.contains(section),
                        "Missing section '$section' for types " +
                        "${chunks.map { it.chunkType }.distinct()}")
                }
                for (section in SECTION_ORDER) {
                    if (section !in expectedSections) {
                        assertFalse(result.contains(section),
                            "Unexpected section '$section' present")
                    }
                }
            }
        }
    }

    @Test
    fun `chunks of same type appear within same section`() {
        runBlocking {
            val service = buildService()
            val arbChunks = Arb.list(genAttachmentChunk(), 2..15)

            checkAll(PropTestConfig(iterations = 25), arbChunks) { chunks ->
                val result = service.formatKnowledgeChunks(chunks)
                val lines = result.split("\n")

                var currentSection: String? = null
                val chunkSectionMap = mutableMapOf<String, String>()

                for (line in lines) {
                    if (line in SECTION_ORDER) {
                        currentSection = line
                    } else if (line.startsWith("[") && currentSection != null) {
                        chunkSectionMap[line] = currentSection
                    }
                }

                for (chunk in chunks) {
                    val expected = expectedSection(chunk.chunkType)
                    val chunkLine = "[${chunk.filename}] ${chunk.chunkText}"
                    val actual = chunkSectionMap[chunkLine]
                    if (actual != null) {
                        assertEquals(expected, actual,
                            "Chunk '${chunk.filename}' (type=${chunk.chunkType}) " +
                            "in wrong section")
                    }
                }
            }
        }
    }

    @Test
    fun `section order is maintained`() {
        runBlocking {
            val service = buildService()
            val arbChunks = Arb.list(genAttachmentChunk(), 2..15)

            checkAll(PropTestConfig(iterations = 25), arbChunks) { chunks ->
                val result = service.formatKnowledgeChunks(chunks)
                val presentSections = SECTION_ORDER.filter { result.contains(it) }
                val indices = presentSections.map { result.indexOf(it) }

                assertEquals(indices, indices.sorted(),
                    "Sections not in expected order: $presentSections")
            }
        }
    }
}
