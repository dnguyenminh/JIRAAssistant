package com.assistant.server.mcp.internal

import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.*
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Bridge between internal MCP tools and the external MCP system.
 * Registers internal server in DB, routes tool calls, aggregates tools.
 * Requirements: AC 6.70, AC 6.71, AC 6.107
 */
open class InternalMcpBridge(
    private val executor: InternalMcpToolExecutor?,
    private val mcpRepo: McpServerRepository?
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INTERNAL_SERVER_ID = "jira-assistant-ui"
        const val INTERNAL_SERVER_NAME = "Jira Assistant UI"
    }

    /** Register/update internal server record in DB. Req: 6.70 */
    suspend fun ensureRegistered() {
        val repo = mcpRepo ?: return
        val existing = repo.findByName(INTERNAL_SERVER_NAME)
        if (existing != null) {
            repo.updateStatus(existing.id, "RUNNING")
            logger.info("Internal MCP server updated to RUNNING: {}", existing.id)
        } else {
            repo.insert(buildInternalConfig())
            logger.info("Internal MCP server registered: {}", INTERNAL_SERVER_ID)
        }
    }

    /** Check if serverId is the internal server. */
    fun isInternalServer(serverId: String): Boolean =
        serverId == INTERNAL_SERVER_ID

    /** Convert internal tools → McpAggregatedTool list. Req: 6.71, 6.107 */
    fun getAggregatedTools(): List<McpAggregatedTool> =
        executor?.getTools()?.map { it.toAggregated() } ?: emptyList()

    /** Delegate tool call to executor. Req: 6.71 */
    open suspend fun callTool(
        toolName: String, arguments: JsonObject,
        userId: String, userRole: String
    ): McpToolCallResponse {
        val exec = executor
            ?: return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "No executor")),
                isError = true
            )
        return exec.execute(toolName, arguments, userId, userRole)
    }

    /** Always returns RUNNING status. Req: 6.70 */
    fun getStatus(): McpProcessStatus = McpProcessStatus(
        configId = INTERNAL_SERVER_ID,
        state = McpServerState.RUNNING,
        toolCount = executor?.getTools()?.size ?: 0
    )

    private fun buildInternalConfig() = McpServerConfig(
        id = INTERNAL_SERVER_ID,
        name = INTERNAL_SERVER_NAME,
        type = "internal",
        status = "RUNNING",
        internal = true
    )

    private fun InternalToolDefinition.toAggregated() = McpAggregatedTool(
        serverId = INTERNAL_SERVER_ID,
        serverName = INTERNAL_SERVER_NAME,
        name = name,
        description = description,
        inputSchema = inputSchema
    )
}
