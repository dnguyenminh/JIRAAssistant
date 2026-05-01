package com.assistant.server.document.jobs

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import com.assistant.mcp.models.McpServerState
import com.assistant.server.attachment.AttachmentDownloader
import com.assistant.server.attachment.AttachmentPipeline
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.document.NoOpScanLogRepository
import com.assistant.server.document.collection.FakeVectorStore

/**
 * No-op implementations of external dependencies for
 * CollectionJobManager property tests.
 *
 * These are never invoked during test execution — they exist
 * only to satisfy constructor requirements.
 */

/** Create a real [AttachmentPipeline] wired to no-op dependencies. */
fun noOpAttachmentPipeline(): AttachmentPipeline = AttachmentPipeline(
    downloader = NoOpDownloader(),
    embeddingService = NoOpEmbeddingService(),
    vectorStore = FakeVectorStore(),
    mcpProcessManager = NoOpMcpProcessManager(),
    scanLogRepository = NoOpScanLogRepository(),
    jiraAuthProvider = { null }
)

private class NoOpDownloader : AttachmentDownloader {
    override suspend fun download(
        contentUrl: String, destPath: String, authHeader: String
    ) = false
}

private class NoOpEmbeddingService : EmbeddingService {
    override suspend fun embed(text: String): FloatArray? = null
}

private class NoOpMcpProcessManager : McpProcessManager {
    private val stopped = McpProcessStatus(
        configId = "", state = McpServerState.STOPPED
    )
    override suspend fun startServer(configId: String) = stopped
    override suspend fun stopServer(configId: String) = stopped
    override suspend fun restartServer(configId: String) = stopped
    override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
    override fun getStatus(configId: String): McpProcessStatus? = null
    override suspend fun startAllEnabled() {}
    override suspend fun stopAll() {}
    override fun getActiveTools() = emptyList<McpAggregatedTool>()
    override fun getClient(configId: String): McpProtocolClient? = null
}
