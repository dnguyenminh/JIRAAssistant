package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration test: AttachmentPipeline must find markitdown MCP
 * by resolved ID (from DB name lookup), NOT hardcoded "markitdown".
 * Catches the bug where user-created MCP has random ID like "u9eko9fuqla5".
 */
class MarkitdownIdResolutionTest {

    /** ProcessManager that only responds to a specific configId. */
    class IdTrackingProcessManager(private val expectedId: String) : McpProcessManager {
        var startedId: String? = null
        var statusCheckedId: String? = null
        val fakeClient = FakeMcpProtocolClient()

        override suspend fun startServer(configId: String): McpProcessStatus {
            startedId = configId
            return if (configId == expectedId)
                McpProcessStatus(configId, state = McpServerState.RUNNING)
            else McpProcessStatus(configId, state = McpServerState.STOPPED, lastError = "not found")
        }
        override suspend fun stopServer(configId: String) = McpProcessStatus(configId, state = McpServerState.STOPPED)
        override suspend fun restartServer(configId: String) = startServer(configId)
        override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
        override fun getStatus(configId: String): McpProcessStatus? {
            statusCheckedId = configId
            return if (configId == expectedId) McpProcessStatus(configId, state = McpServerState.RUNNING) else null
        }
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = emptyList<McpAggregatedTool>()
        override fun getClient(configId: String): McpProtocolClient? =
            if (configId == expectedId) fakeClient else null
    }

    @Test
    fun `pipeline uses resolved ID not hardcoded markitdown`() = runBlocking {
        val randomId = "u9eko9fuqla5"
        val pm = IdTrackingProcessManager(randomId)

        val pipeline = AttachmentPipeline(
            downloader = FakeDownloader().apply { shouldSucceed = true },
            embeddingService = FakeEmbeddingService(),
            vectorStore = FakeVectorStore(),
            mcpProcessManager = pm,
            scanLogRepository = FakeScanLogRepository(),
            jiraAuthProvider = { "Basic dGVzdDp0ZXN0" },
            markitdownIdResolver = { randomId }
        )

        val att = JiraAttachment(id = "att-1", filename = "test.pdf", size = 1024, content = "https://jira/file/1")
        pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertEquals(randomId, pm.statusCheckedId,
            "Pipeline must check status using resolved ID '$randomId', not hardcoded 'markitdown'")
    }

    @Test
    fun `pipeline with null resolver falls back to markitdown`() = runBlocking {
        val pm = IdTrackingProcessManager("markitdown")

        val pipeline = AttachmentPipeline(
            downloader = FakeDownloader(),
            embeddingService = FakeEmbeddingService(),
            vectorStore = FakeVectorStore(),
            mcpProcessManager = pm,
            scanLogRepository = FakeScanLogRepository(),
            jiraAuthProvider = { "Basic dGVzdDp0ZXN0" },
            markitdownIdResolver = null // no resolver
        )

        val att = JiraAttachment(id = "att-2", filename = "doc.docx", size = 512, content = "https://jira/file/2")
        pipeline.processAttachments("PROJ", "PROJ-2", listOf(att))

        assertEquals("markitdown", pm.statusCheckedId,
            "Without resolver, must fall back to 'markitdown'")
    }
}
