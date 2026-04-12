package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration test: AttachmentPipeline retries markitdown after crash.
 * Catches bug where markitdown crash fails all subsequent attachments.
 */
class MarkitdownRetryTest {

    /** MCP that fails first call, succeeds after restart. */
    class CrashThenRecoverPM : McpProcessManager {
        var callCount = 0
        var restartCount = 0
        private val client = object : McpProtocolClient {
            override suspend fun initialize() = McpInitializeResult("2024-11-05", McpServerInfoDto("fake", "1.0"))
            override suspend fun sendRequest(method: String, params: JsonElement?) = JsonObject(emptyMap())
            override suspend fun sendNotification(method: String, params: JsonElement?) {}
            override suspend fun listTools() = emptyList<McpToolInfo>()
            override suspend fun callTool(name: String, arguments: JsonObject): McpToolCallResponse {
                callCount++
                return if (callCount <= 1) throw RuntimeException("Process crashed")
                else McpToolCallResponse(content = listOf(McpContent(type = "text", text = "# Converted")))
            }
            override fun close() {}
        }

        override suspend fun startServer(configId: String) = McpProcessStatus(configId, state = McpServerState.RUNNING)
        override suspend fun stopServer(configId: String) = McpProcessStatus(configId, state = McpServerState.STOPPED)
        override suspend fun restartServer(configId: String): McpProcessStatus {
            restartCount++
            return McpProcessStatus(configId, state = McpServerState.RUNNING)
        }
        override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
        override fun getStatus(configId: String) = McpProcessStatus(configId, state = McpServerState.RUNNING)
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = emptyList<McpAggregatedTool>()
        override fun getClient(configId: String): McpProtocolClient = client
    }

    @Test
    fun `retries after markitdown crash and succeeds`() = runBlocking {
        val pm = CrashThenRecoverPM()
        val pipeline = AttachmentPipeline(
            downloader = FakeDownloader().apply { shouldSucceed = true },
            embeddingService = FakeEmbeddingService(),
            vectorStore = FakeVectorStore(),
            mcpProcessManager = pm,
            scanLogRepository = FakeScanLogRepository(),
            jiraAuthProvider = { "Basic dGVzdDp0ZXN0" }
        )

        val att = JiraAttachment(id = "att-1", filename = "doc.pdf", size = 1024, content = "https://jira/file/1")
        val chunks = pipeline.processAttachments("PROJ", "PROJ-1", listOf(att))

        assertTrue(pm.restartCount > 0, "Must attempt restart after crash")
        assertTrue(chunks > 0, "Must produce chunks after successful retry")
    }
}
