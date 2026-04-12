package com.assistant.server.attachment

import com.assistant.ai.*
import com.assistant.domain.NetworkGraph
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.jira.*
import com.assistant.kb.KBRepository
import com.assistant.scan.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test: BatchScanEngine + AttachmentPipeline.
 * Verifies attachments are processed after AI analysis completes.
 * Validates: Requirements 22.13
 */
class BatchScanAttachmentTest {

    private lateinit var scanStateRepo: InMemoryScanStateRepo
    private lateinit var scanLogRepo: FakeScanLogRepository
    private lateinit var attachmentCalls: MutableList<AttachmentCall>

    @BeforeEach
    fun setup() {
        scanStateRepo = InMemoryScanStateRepo()
        scanLogRepo = FakeScanLogRepository()
        attachmentCalls = Collections.synchronizedList(mutableListOf())
    }

    @Test
    fun `attachment processor called after AI analysis`() = runBlocking {
        val engine = buildEngine(
            ticketAttachments = mapOf("PROJ-1" to listOf(testAttachment("a1"))),
            processor = recordingProcessor()
        )
        engine.startScan("PROJ")
        awaitScanComplete("PROJ")

        assertEquals(1, attachmentCalls.size)
        assertEquals("PROJ-1", attachmentCalls[0].ticketKey)
    }

    @Test
    fun `processor receives correct parameters`() = runBlocking {
        val att = testAttachment("att-42", "spec.pdf")
        val engine = buildEngine(
            ticketAttachments = mapOf("PROJ-1" to listOf(att)),
            processor = recordingProcessor()
        )
        engine.startScan("PROJ")
        awaitScanComplete("PROJ")

        val call = attachmentCalls.single()
        assertEquals("PROJ", call.projectKey)
        assertEquals("PROJ-1", call.ticketKey)
        assertEquals("att-42", call.attachments[0].id)
        assertEquals("spec.pdf", call.attachments[0].filename)
    }

    @Test
    fun `scan continues when attachment processing fails`() = runBlocking {
        val engine = buildEngine(
            ticketKeys = listOf("PROJ-1", "PROJ-2"),
            ticketAttachments = mapOf(
                "PROJ-1" to listOf(testAttachment("a1")),
                "PROJ-2" to listOf(testAttachment("a2"))
            ),
            processor = failingProcessor()
        )
        engine.startScan("PROJ")
        awaitScanComplete("PROJ")

        val state = scanStateRepo.findByProjectKey("PROJ")!!
        assertEquals(ScanStatus.COMPLETED, state.status)
        assertEquals(2, state.processedCount)
    }

    @Test
    fun `scan works without attachment processor (null)`() = runBlocking {
        val engine = buildEngine(processor = null)
        engine.startScan("PROJ")
        awaitScanComplete("PROJ")

        val state = scanStateRepo.findByProjectKey("PROJ")!!
        assertEquals(ScanStatus.COMPLETED, state.status)
        assertTrue(attachmentCalls.isEmpty())
    }

    @Test
    fun `processor called for each ticket with attachments`() = runBlocking {
        val engine = buildEngine(
            ticketKeys = listOf("PROJ-1", "PROJ-2", "PROJ-3"),
            ticketAttachments = mapOf(
                "PROJ-1" to listOf(testAttachment("a1")),
                "PROJ-3" to listOf(testAttachment("a3"))
            ),
            processor = recordingProcessor()
        )
        engine.startScan("PROJ")
        awaitScanComplete("PROJ")

        assertEquals(2, attachmentCalls.size)
        val keys = attachmentCalls.map { it.ticketKey }.toSet()
        assertTrue("PROJ-1" in keys)
        assertTrue("PROJ-3" in keys)
    }

    // --- Helpers ---

    private fun recordingProcessor(): suspend (String, String, List<JiraAttachment>) -> Int =
        { proj, ticket, atts ->
            attachmentCalls.add(AttachmentCall(proj, ticket, atts))
            atts.size
        }

    private fun failingProcessor(): suspend (String, String, List<JiraAttachment>) -> Int =
        { _, _, _ -> throw RuntimeException("Attachment processing error") }

    private fun testAttachment(id: String, name: String = "file.pdf") =
        JiraAttachment(id = id, filename = name, size = 1024, content = "https://jira/att/$id")

    private suspend fun awaitScanComplete(projectKey: String) {
        repeat(200) {
            val s = scanStateRepo.findByProjectKey(projectKey)
            if (s?.status == ScanStatus.COMPLETED) return
            kotlinx.coroutines.delay(50)
        }
    }

    private fun buildEngine(
        ticketKeys: List<String> = listOf("PROJ-1"),
        ticketAttachments: Map<String, List<JiraAttachment>> = emptyMap(),
        processor: (suspend (String, String, List<JiraAttachment>) -> Int)? = recordingProcessor()
    ): BatchScanEngine {
        val jiraClient = StubJiraClient(ticketKeys, ticketAttachments)
        return BatchScanEngine(
            aiOrchestrator = StubAIOrchestrator(),
            kbRepository = FakeKBRepoForAttachment(),
            jiraClientProvider = { jiraClient },
            featureNetworkMapper = stubNetworkMapper(),
            scanStateRepository = scanStateRepo,
            scanLogRepository = scanLogRepo,
            scope = CoroutineScope(Dispatchers.Default),
            attachmentProcessor = processor
        )
    }
}

/** Recorded attachment processor call. */
data class AttachmentCall(
    val projectKey: String,
    val ticketKey: String,
    val attachments: List<JiraAttachment>
)
