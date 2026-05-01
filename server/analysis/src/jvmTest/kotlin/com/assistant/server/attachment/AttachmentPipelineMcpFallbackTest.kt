package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.scan.ScanLogStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AttachmentPipeline markitdown MCP fallback behavior.
 * Validates: Requirement 22.7 — MCP server down → skip, no crash.
 */
class AttachmentPipelineMcpFallbackTest {

    private lateinit var pipeline: AttachmentPipeline
    private lateinit var fakeDownloader: FakeDownloader
    private lateinit var fakeEmbedding: FakeEmbeddingService
    private lateinit var fakeVectorStore: FakeVectorStore
    private lateinit var fakeMcp: FakeMcpProcessManager
    private lateinit var fakeScanLog: FakeScanLogRepository

    @BeforeEach
    fun setup() {
        fakeDownloader = FakeDownloader()
        fakeEmbedding = FakeEmbeddingService()
        fakeVectorStore = FakeVectorStore()
        fakeMcp = FakeMcpProcessManager()
        fakeScanLog = FakeScanLogRepository()
        pipeline = AttachmentPipeline(
            downloader = fakeDownloader,
            embeddingService = fakeEmbedding,
            vectorStore = fakeVectorStore,
            mcpProcessManager = fakeMcp,
            scanLogRepository = fakeScanLog,
            jiraAuthProvider = { "Basic dGVzdDp0ZXN0" }
        )
    }

    private fun pdfAttachment(id: String = "1", name: String = "doc.pdf") =
        JiraAttachment(id = id, filename = name, size = 1024, content = "https://jira/att/$id")

    @Test
    fun `processAttachments does not crash when MCP server is stopped`() = runBlocking {
        fakeMcp.markitdownRunning = false
        fakeDownloader.shouldSucceed = true
        val result = pipeline.processAttachments("PROJ", "PROJ-1", listOf(pdfAttachment()))
        assertEquals(0, result, "Should return 0 chunks when MCP is down")
    }

    @Test
    fun `convertViaMarkitdown returns null when MCP server is stopped`() = runBlocking {
        fakeMcp.markitdownRunning = false
        val result = pipeline.convertViaMarkitdown("/tmp/file.pdf")
        assertEquals(null, result)
    }

    @Test
    fun `pipeline logs FAILED entry when MCP conversion fails`() = runBlocking {
        fakeMcp.markitdownRunning = false
        fakeDownloader.shouldSucceed = true
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(pdfAttachment()))
        val failed = fakeScanLog.entries.filter { it.status == ScanLogStatus.FAILED }
        assertTrue(failed.isNotEmpty(), "Should log FAILED when MCP conversion fails")
        assertTrue(failed.any { it.message.contains("markitdown") || it.message.contains("conversion failed") })
    }

    @Test
    fun `other attachments continue processing when one fails MCP`() = runBlocking {
        fakeMcp.markitdownRunning = false
        fakeDownloader.shouldSucceed = true
        val attachments = listOf(pdfAttachment("1", "a.pdf"), pdfAttachment("2", "b.pdf"))
        pipeline.processAttachments("PROJ", "PROJ-1", attachments)
        // Both should be attempted — verify 2 ANALYZING entries
        val analyzing = fakeScanLog.entries.filter { it.status == ScanLogStatus.ANALYZING }
        assertEquals(2, analyzing.size, "Both attachments should be attempted")
    }

    @Test
    fun `pipeline returns zero chunks but does not throw when MCP unavailable`() = runBlocking {
        fakeMcp.markitdownRunning = false
        fakeDownloader.shouldSucceed = true
        val attachments = listOf(pdfAttachment("1"), pdfAttachment("2"), pdfAttachment("3"))
        val result = pipeline.processAttachments("PROJ", "PROJ-1", attachments)
        assertEquals(0, result, "No chunks saved when MCP is down")
        assertTrue(fakeVectorStore.chunks.isEmpty(), "VectorStore should be empty")
    }
}
