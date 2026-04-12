package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.scan.ScanLogStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the full attachment pipeline flow with mock dependencies.
 * Requirements: 22.2–22.18
 */
class AttachmentPipelineIntegrationTest {

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

    // --- 168.1: Full pipeline flow ---

    @Test
    fun `full pipeline processes eligible attachment and saves chunks`() = runBlocking {
        downloader.shouldSucceed = true
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "Hello world from the document"
        embedding.nextEmbedding = floatArrayOf(0.5f, 0.5f, 0.5f)

        val att = JiraAttachment(
            id = "att-1", filename = "report.pdf",
            size = 2048, content = "https://jira/att/1"
        )
        val chunks = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertTrue(chunks > 0, "Should save at least one chunk")
        assertTrue(vectorStore.chunks.isNotEmpty(), "VectorStore should have chunks")
        assertEquals("PROJ-1", vectorStore.chunks.first().ticketId)
        assertEquals("att-1", vectorStore.chunks.first().attachmentId)
    }

    @Test
    fun `pipeline skips oversized attachments`() = runBlocking {
        val big = JiraAttachment(id = "att-2", filename = "huge.zip", size = 60L * 1024 * 1024, content = "https://jira/att/2")
        val chunks = pipeline.processAttachments("PROJ", "PROJ-1", listOf(big))

        assertEquals(0, chunks)
        assertTrue(vectorStore.chunks.isEmpty())
        assertTrue(scanLog.entries.any { it.message.contains("skipped") || it.message.contains("too large") })
    }

    @Test
    fun `pipeline skips already-processed attachments (KB-First)`() = runBlocking {
        vectorStore.existingAttachmentIds.add("att-existing")
        val att = JiraAttachment(
            id = "att-existing", filename = "doc.pdf",
            size = 1024, content = "https://jira/att/existing"
        )
        val chunks = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertEquals(0, chunks)
        assertTrue(scanLog.entries.any { it.message.contains("already in KB") })
    }

    @Test
    fun `pipeline creates log entries for each step`() = runBlocking {
        downloader.shouldSucceed = true
        mcp.markitdownRunning = true
        mcp.fakeClient.markdownResult = "Some content here"
        embedding.nextEmbedding = floatArrayOf(0.1f)

        val att = JiraAttachment(id = "att-3", filename = "notes.txt", size = 512, content = "https://jira/att/3")
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        val statuses = scanLog.entries.map { it.status }.toSet()
        assertTrue(ScanLogStatus.ANALYZING in statuses, "Should have ANALYZING entry")
        assertTrue(ScanLogStatus.COMPLETED in statuses, "Should have COMPLETED entry")
    }

    // --- 168.2: Pipeline handles download failure gracefully ---

    @Test
    fun `pipeline logs FAILED when download fails`() = runBlocking {
        downloader.shouldSucceed = false
        val att = JiraAttachment(id = "att-4", filename = "doc.pdf", size = 1024, content = "https://jira/att/4")
        val chunks = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertEquals(0, chunks)
        assertTrue(scanLog.entries.any { it.status == ScanLogStatus.FAILED })
    }

    // --- 168.3: ChatService + VectorStore attachment context ---

    @Test
    fun `buildKnowledgeContext returns formatted chunks from vector store`() = runBlocking {
        val chatService = com.assistant.server.chat.ChatServiceImpl(
            aiAgentProvider = { FakeAIAgentForAttachment() },
            kbRepository = FakeKBRepoForAttachment(),
            graphEngine = FakeGraphEngineForAttachment(),
            embeddingService = embedding,
            vectorStore = vectorStore
        )
        // Pre-populate vector store
        vectorStore.chunks.add(
            com.assistant.server.attachment.models.AttachmentChunk(
                ticketId = "PROJ-1", attachmentId = "att-1",
                filename = "report.pdf", chunkIndex = 0,
                chunkText = "Important finding", embedding = listOf(0.5f, 0.5f),
                createdAt = "2024-01-01"
            )
        )
        embedding.nextEmbedding = floatArrayOf(0.5f, 0.5f)

        val ctx = chatService.buildKnowledgeContext("PROJ", "test query")

        assertTrue(ctx.contains("[report.pdf]"), "Should contain filename")
        assertTrue(ctx.contains("Important finding"), "Should contain chunk text")
    }

    @Test
    fun `buildKnowledgeContext returns no-data when no chunks`() = runBlocking {
        val chatService = com.assistant.server.chat.ChatServiceImpl(
            aiAgentProvider = { FakeAIAgentForAttachment() },
            kbRepository = FakeKBRepoForAttachment(),
            graphEngine = FakeGraphEngineForAttachment(),
            embeddingService = embedding,
            vectorStore = vectorStore
        )
        embedding.nextEmbedding = floatArrayOf(0.1f)

        val ctx = chatService.buildKnowledgeContext("PROJ", "test query")

        assertEquals("No attachment data.", ctx)
    }
}
