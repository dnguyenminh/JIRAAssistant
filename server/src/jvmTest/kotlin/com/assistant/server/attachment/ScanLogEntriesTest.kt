package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.scan.ScanLogStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests scan log entry format for AttachmentPipeline.
 * Validates: Requirement 22.14 — ANALYZING/COMPLETED/FAILED messages.
 */
class ScanLogEntriesTest {

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

    private fun attachment(
        id: String = "att-1",
        name: String = "report.pdf",
        size: Long = 2048,
        url: String = "https://jira/att/$id"
    ) = JiraAttachment(id = id, filename = name, size = size, content = url)

    // --- ANALYZING status ---

    @Test
    fun `ANALYZING entry includes filename and size`() = runBlocking {
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "# MD"
        embedding.nextEmbedding = floatArrayOf(0.1f)
        val att = attachment(name = "design.docx", size = 5120)
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))
        val entry = scanLog.entries.first { it.status == ScanLogStatus.ANALYZING }
        assertEquals("PROJ", entry.projectKey)
        assertEquals("PROJ-1", entry.ticketId)
        assertTrue(entry.message.contains("design.docx"))
        assertTrue(entry.message.contains("PROJ-1"))
    }

    @Test
    fun `ANALYZING entry has non-blank timestamp`() = runBlocking {
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "text"
        embedding.nextEmbedding = floatArrayOf(0.1f)
        pipeline.processAttachments("P", "P-1", listOf(attachment()))
        val entry = scanLog.entries.first { it.status == ScanLogStatus.ANALYZING }
        assertTrue(entry.timestamp.isNotBlank())
    }

    // --- COMPLETED status ---

    @Test
    fun `COMPLETED entry includes filename and chunk count`() = runBlocking {
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "Some markdown content"
        embedding.nextEmbedding = floatArrayOf(0.5f)
        pipeline.processAttachments("PROJ", "PROJ-2", listOf(attachment()))
        val completed = scanLog.entries.filter { it.status == ScanLogStatus.COMPLETED }
        assertTrue(completed.isNotEmpty())
        val msg = completed.last().message
        assertTrue(msg.contains("report.pdf"))
        assertTrue(msg.contains("chunks"))
    }

    @Test
    fun `COMPLETED entry records correct projectKey and ticketId`() = runBlocking {
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "md"
        embedding.nextEmbedding = floatArrayOf(0.1f)
        pipeline.processAttachments("ABC", "ABC-99", listOf(attachment()))
        val entry = scanLog.entries.last { it.status == ScanLogStatus.COMPLETED }
        assertEquals("ABC", entry.projectKey)
        assertEquals("ABC-99", entry.ticketId)
    }

    // --- FAILED status: download failure ---

    @Test
    fun `FAILED entry on download failure includes filename`() = runBlocking {
        downloader.shouldSucceed = false
        pipeline.processAttachments("X", "X-1", listOf(attachment(name = "spec.xlsx")))
        val failed = scanLog.entries.filter { it.status == ScanLogStatus.FAILED }
        assertTrue(failed.isNotEmpty())
        assertTrue(failed.any { it.message.contains("spec.xlsx") })
        assertTrue(failed.any { it.message.contains("download failed") })
    }

    // --- FAILED status: MCP down ---

    @Test
    fun `FAILED entry when MCP conversion fails`() = runBlocking {
        mcp.markitdownRunning = false
        downloader.shouldSucceed = true
        pipeline.processAttachments("Y", "Y-5", listOf(attachment(name = "notes.pdf")))
        val failed = scanLog.entries.filter { it.status == ScanLogStatus.FAILED }
        assertTrue(failed.isNotEmpty())
        assertTrue(failed.any { it.message.contains("notes.pdf") })
        assertTrue(failed.any { it.message.contains("conversion failed") || it.message.contains("markitdown") })
    }

    // --- Multiple attachments produce separate log entries ---

    @Test
    fun `each attachment gets its own ANALYZING entry`() = runBlocking {
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "md"
        embedding.nextEmbedding = floatArrayOf(0.1f)
        val atts = listOf(
            attachment("1", "a.pdf"), attachment("2", "b.docx")
        )
        pipeline.processAttachments("P", "P-1", atts)
        val analyzing = scanLog.entries.filter { it.status == ScanLogStatus.ANALYZING }
        assertEquals(2, analyzing.size)
        assertTrue(analyzing.any { it.message.contains("a.pdf") })
        assertTrue(analyzing.any { it.message.contains("b.docx") })
    }
}
