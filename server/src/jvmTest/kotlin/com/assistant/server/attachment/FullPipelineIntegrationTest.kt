package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test: full pipeline (download → MCP → Ollama → VectorStore).
 * Validates: Requirements 22.2–22.12
 */
class FullPipelineIntegrationTest {

    private lateinit var pipeline: AttachmentPipeline
    private lateinit var downloader: FakeDownloader
    private lateinit var embedding: FakeEmbeddingService
    private lateinit var vectorStore: FakeVectorStore
    private lateinit var mcp: FakeMcpProcessManager
    private lateinit var scanLog: FakeScanLogRepository

    @BeforeEach
    fun setup() {
        downloader = FakeDownloader()
        embedding = FakeEmbeddingService()
        vectorStore = FakeVectorStore()
        mcp = FakeMcpProcessManager()
        scanLog = FakeScanLogRepository()
        pipeline = AttachmentPipeline(
            downloader = downloader,
            embeddingService = embedding,
            vectorStore = vectorStore,
            mcpProcessManager = mcp,
            scanLogRepository = scanLog,
            jiraAuthProvider = { "Basic dGVzdDp0ZXN0" }
        )
    }

    @Test
    fun `single attachment produces chunks in VectorStore`() = runBlocking {
        configurePipelineSuccess("Short document content")
        val att = pdfAttachment("att-1", "report.pdf")

        val count = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertEquals(1, count)
        assertEquals(1, vectorStore.chunks.size)
    }

    @Test
    fun `chunk metadata has correct ticketId`() = runBlocking {
        configurePipelineSuccess("Some text")
        val att = pdfAttachment("att-10", "file.pdf")

        pipeline.processAttachments("PROJ", "PROJ-42", listOf(att))

        assertTrue(vectorStore.chunks.all { it.ticketId == "PROJ-42" })
    }

    @Test
    fun `chunk metadata has correct attachmentId`() = runBlocking {
        configurePipelineSuccess("Some text")
        val att = pdfAttachment("att-99", "doc.pdf")

        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertTrue(vectorStore.chunks.all { it.attachmentId == "att-99" })
    }

    @Test
    fun `chunk metadata has correct filename`() = runBlocking {
        configurePipelineSuccess("Some text")
        val att = pdfAttachment("att-1", "design-spec.docx")

        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertTrue(vectorStore.chunks.all { it.filename == "design-spec.docx" })
    }

    @Test
    fun `multi-paragraph text produces multiple chunks`() = runBlocking {
        val longText = buildMultiParagraphText(paragraphCount = 20)
        configurePipelineSuccess(longText)
        val att = pdfAttachment("att-2", "long-report.pdf")

        val count = pipeline.processAttachments("PROJ", "PROJ-5", listOf(att))

        assertTrue(count > 1, "Long text should produce >1 chunk, got $count")
        assertEquals(count, vectorStore.chunks.size)
    }

    @Test
    fun `chunk indices are sequential starting from zero`() = runBlocking {
        val longText = buildMultiParagraphText(paragraphCount = 20)
        configurePipelineSuccess(longText)
        val att = pdfAttachment("att-3", "report.pdf")

        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        val indices = vectorStore.chunks.map { it.chunkIndex }.sorted()
        assertEquals((0 until indices.size).toList(), indices)
    }

    @Test
    fun `multiple attachments each save their own chunks`() = runBlocking {
        configurePipelineSuccess("Content A")
        val att1 = pdfAttachment("att-a", "fileA.pdf")
        val att2 = pdfAttachment("att-b", "fileB.docx")

        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att1, att2))

        val idsA = vectorStore.chunks.filter { it.attachmentId == "att-a" }
        val idsB = vectorStore.chunks.filter { it.attachmentId == "att-b" }
        assertTrue(idsA.isNotEmpty(), "fileA chunks missing")
        assertTrue(idsB.isNotEmpty(), "fileB chunks missing")
    }

    @Test
    fun `chunk count matches TextChunker output for given text`() = runBlocking {
        val text = "Paragraph one.\n\nParagraph two.\n\nParagraph three."
        configurePipelineSuccess(text)
        val expected = TextChunker.chunk(text).size
        val att = pdfAttachment("att-4", "notes.txt")

        val count = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertEquals(expected, count)
        assertEquals(expected, vectorStore.chunks.size)
    }

    // --- Helpers ---

    private fun configurePipelineSuccess(markdownResult: String) {
        downloader.shouldSucceed = true
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = markdownResult
        embedding.nextEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
    }

    private fun pdfAttachment(id: String, name: String) = JiraAttachment(
        id = id, filename = name, size = 2048,
        content = "https://jira.example.com/att/$id"
    )

    private fun buildMultiParagraphText(paragraphCount: Int): String =
        (1..paragraphCount).joinToString("\n\n") { i ->
            "Paragraph $i. " + "This is a sentence with enough words to contribute to the token count. ".repeat(15)
        }
}
