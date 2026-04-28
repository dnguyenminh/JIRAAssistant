package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogRepository
import com.assistant.scan.ScanLogStatus
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentPipelineTest {

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

    // --- 167.1: AttachmentDownloader mock tests ---

    @Test
    fun `download passes correct URL and auth header`() = runBlocking {
        fakeDownloader.shouldSucceed = true
        val url = "https://jira.example.com/rest/api/3/attachment/content/123"
        val auth = "Basic abc123"
        fakeDownloader.download(url, "/tmp/file.pdf", auth)
        assertEquals(url, fakeDownloader.lastUrl)
        assertEquals(auth, fakeDownloader.lastAuth)
    }

    // --- 167.2: isEligible + getExtension tests ---

    @Test
    fun `isEligible returns true for supported PDF under 50MB`() {
        val att = JiraAttachment(id = "1", filename = "doc.pdf", size = 1024)
        assertTrue(pipeline.isEligible(att))
    }

    @Test
    fun `isEligible returns true for any file type under 50MB`() {
        val att = JiraAttachment(id = "2", filename = "app.exe", size = 1024)
        assertTrue(pipeline.isEligible(att), "No extension filter — all types eligible")
    }

    @Test
    fun `isEligible returns false for file over 50MB`() {
        val att = JiraAttachment(id = "3", filename = "big.pdf", size = 60L * 1024 * 1024)
        assertFalse(pipeline.isEligible(att))
    }

    @Test
    fun `isEligible returns true for all supported extensions`() {
        val exts = listOf("pdf", "docx", "xlsx", "pptx", "txt", "md", "csv", "html", "png", "jpg", "jpeg", "gif")
        for (ext in exts) {
            val att = JiraAttachment(id = ext, filename = "file.$ext", size = 100)
            assertTrue(pipeline.isEligible(att), "Expected $ext to be eligible")
        }
    }

    @Test
    fun `getExtension extracts lowercase extension`() {
        assertEquals("pdf", pipeline.getExtension("Report.PDF"))
        assertEquals("docx", pipeline.getExtension("file.DOCX"))
        assertEquals("", pipeline.getExtension("noext"))
    }

    // --- 167.3: EmbeddingService mock test ---

    @Test
    fun `embedding service returns vector for valid text`() = runBlocking {
        fakeEmbedding.nextEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        val result = fakeEmbedding.embed("hello world")
        assertEquals(3, result?.size)
        assertEquals(0.1f, result!![0])
    }

    @Test
    fun `embedding service returns null on failure`() = runBlocking {
        fakeEmbedding.nextEmbedding = null
        val result = fakeEmbedding.embed("hello")
        assertEquals(null, result)
    }

    // --- 167.4: Scan log entries format ---

    @Test
    fun `log entries contain ANALYZING status`() = runBlocking {
        val att = JiraAttachment(id = "1", filename = "doc.pdf", size = 1024, content = "https://jira/att/1")
        fakeDownloader.shouldSucceed = true
        fakeMcp.markitdownRunning = true
        fakeMcp.fakeClient.markdownResult = "Some markdown"
        fakeEmbedding.nextEmbedding = floatArrayOf(0.1f)
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))
        val analyzing = fakeScanLog.entries.filter { it.status == ScanLogStatus.ANALYZING }
        assertTrue(analyzing.isNotEmpty(), "Should have ANALYZING log entry")
        assertTrue(analyzing.first().message.contains("Processing attachment"))
    }

    @Test
    fun `log entries contain COMPLETED status after success`() = runBlocking {
        val att = JiraAttachment(id = "1", filename = "doc.pdf", size = 1024, content = "https://jira/att/1")
        fakeDownloader.shouldSucceed = true
        fakeMcp.markitdownRunning = true
        fakeMcp.fakeClient.markdownResult = "Some markdown"
        fakeEmbedding.nextEmbedding = floatArrayOf(0.1f)
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))
        val completed = fakeScanLog.entries.filter { it.status == ScanLogStatus.COMPLETED }
        assertTrue(completed.isNotEmpty(), "Should have COMPLETED log entry")
        assertTrue(completed.any { it.message.contains("Attachment converted") })
    }

    @Test
    fun `log entries contain FAILED status on download failure`() = runBlocking {
        val att = JiraAttachment(id = "1", filename = "doc.pdf", size = 1024, content = "https://jira/att/1")
        fakeDownloader.shouldSucceed = false
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))
        val failed = fakeScanLog.entries.filter { it.status == ScanLogStatus.FAILED }
        assertTrue(failed.isNotEmpty(), "Should have FAILED log entry")
        assertTrue(failed.any { it.message.contains("download failed") })
    }

    // --- 167.2 continued: markitdown MCP fallback ---

    @Test
    fun `pipeline skips conversion when markitdown MCP not running`() = runBlocking {
        fakeMcp.markitdownRunning = false
        val result = pipeline.convertViaMarkitdown("/tmp/file.pdf")
        assertEquals(null, result)
    }
}
