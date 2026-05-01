package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import kotlinx.serialization.json.JsonObject

/**
 * Shared test fakes for BRD pipeline tests.
 * Used by BrdPipelineLocalKBExplorationTest and
 * BrdPipelinePreservationPropertyTest.
 */

fun stubMcpManager(
    tools: List<McpAggregatedTool> = emptyList()
): McpProcessManager = object : McpProcessManager {
    override suspend fun startServer(id: String) = stub()
    override suspend fun stopServer(id: String) = stub()
    override suspend fun restartServer(id: String) = stub()
    override fun getRunningServers() =
        emptyMap<String, McpProcessStatus>()
    override fun getStatus(id: String): McpProcessStatus? = null
    override suspend fun startAllEnabled() {}
    override suspend fun stopAll() {}
    override fun getActiveTools() = tools
    override fun getClient(id: String): McpProtocolClient? = null
    private fun stub(): Nothing = error("not needed")
}

fun stubProxy(): SubprocessProxy = object : SubprocessProxy {
    override suspend fun handleToolCallRequest(
        r: ToolCallRequest
    ) = ToolCallResponse(r.id, true, "ok", "")
    override fun getAvailableToolDescriptors() =
        emptyList<ToolDescriptor>()
    override fun buildToolListMessage() = ""
    override fun buildToolsUpdatedMessage() = ""
}

fun stubReporter(): ProgressReporter =
    object : ProgressReporter {
        override suspend fun reportPhase(
            n: String, i: Int, t: Int
        ) {}
        override suspend fun reportProgress(p: Int, m: String) {}
        override suspend fun reportToolCall(n: String, s: String) {}
    }

fun stubResolver(): com.assistant.server.agent.ba.subprocess.CliBackendResolver {
    val repo = object : com.assistant.settings.SettingsRepository {
        override suspend fun getAll() = emptyMap<String, String>()
        override suspend fun get(key: String): String? = null
        override suspend fun put(key: String, value: String) {}
        override suspend fun putAll(s: Map<String, String>) {}
    }
    return com.assistant.server.agent.ba.subprocess.CliBackendResolver(repo)
}

fun mcpTool(
    server: String, name: String, desc: String
) = McpAggregatedTool(
    server, server, name, desc, JsonObject(emptyMap())
)
