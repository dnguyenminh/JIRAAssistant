package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 5: KB-First deduplication skips existing attachments
 * For any attachment whose ID already exists in VectorStore,
 * AttachmentPipeline must skip processing (no download, no embed, no store).
 * Validates: Requirements 22.15
 */
@OptIn(ExperimentalKotest::class)
class KBFirstDeduplicationPropertyTest {

    @Test
    fun `existing attachments are skipped - no download or embed`() {
        runBlocking {
            val arbAttachmentId = Arb.string(minSize = 3, maxSize = 10, codepoints = Codepoint.alphanumeric())

            checkAll(PropTestConfig(iterations = 25), arbAttachmentId) { attId ->
                val downloader = FakeDownloader()
                val embedding = FakeEmbeddingService()
                val vectorStore = FakeVectorStore().apply { existingAttachmentIds.add(attId) }
                val mcpManager = FakeMcpProcessManager()
                val scanLog = FakeScanLogRepository()

                val pipeline = AttachmentPipeline(
                    downloader = downloader,
                    embeddingService = embedding,
                    vectorStore = vectorStore,
                    mcpProcessManager = mcpManager,
                    scanLogRepository = scanLog,
                    jiraAuthProvider = { "Basic dGVzdDp0ZXN0" }
                )

                val att = JiraAttachment(
                    id = attId, filename = "doc.pdf",
                    mimeType = "application/pdf", size = 1024,
                    content = "https://jira.example.com/file/$attId"
                )

                val chunks = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

                assertEquals(0, chunks, "Existing attachment must produce 0 chunks")
                assertNull(downloader.lastUrl, "Downloader must NOT be called for existing attachment")
                assertNull(embedding.lastText, "EmbeddingService must NOT be called for existing attachment")
                assertTrue(vectorStore.chunks.isEmpty(), "No new chunks must be saved")
            }
        }
    }

    @Test
    fun `new attachments are processed - not skipped`() {
        runBlocking {
            val downloader = FakeDownloader()
            val embedding = FakeEmbeddingService()
            val vectorStore = FakeVectorStore() // empty — no existing IDs
            val mcpManager = FakeMcpProcessManager().apply { markitdownRunning = true }
            val scanLog = FakeScanLogRepository()

            val pipeline = AttachmentPipeline(
                downloader = downloader,
                embeddingService = embedding,
                vectorStore = vectorStore,
                mcpProcessManager = mcpManager,
                scanLogRepository = scanLog,
                jiraAuthProvider = { "Basic dGVzdDp0ZXN0" }
            )

            val att = JiraAttachment(
                id = "new-123", filename = "report.pdf",
                mimeType = "application/pdf", size = 2048,
                content = "https://jira.example.com/file/new-123"
            )

            pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

            assertNotNull(downloader.lastUrl, "Downloader must be called for new attachment")
        }
    }
}
