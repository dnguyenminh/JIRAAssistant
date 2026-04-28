package com.assistant.server.mcp.internal.handlers

import com.assistant.ai.AIOrchestrator
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * Integration tool handlers — AI providers & MCP servers.
 * manage_mcp_server rejects actions on internal server (serverId = "jira-assistant-ui").
 * Requirements: AC 6.95–6.98, AC 6.110
 */
class IntegrationHandlers(
    private val providerConfigRepo: ProviderConfigRepository,
    private val aiOrchestrator: AIOrchestrator,
    private val mcpProcessManager: McpProcessManager,
    private val mcpServerRepo: McpServerRepository
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    companion object {
        const val INTERNAL_SERVER_ID = "jira-assistant-ui"
    }

    suspend fun handleListAiProviders(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val providers = providerConfigRepo.getAllProviders()
        return textResponse(json.encodeToString(providers))
    }

    suspend fun handleTestAiProvider(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val providerId = args.str("providerId") ?: return missingField("providerId")
        return try {
            val result = aiOrchestrator.testProvider(providerId)
            textResponse(json.encodeToString(result))
        } catch (e: Exception) {
            errorResponse("Provider test failed: ${e.message}")
        }
    }

    suspend fun handleListMcpServers(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val configs = mcpServerRepo.getAll()
        val statuses = mcpProcessManager.getRunningServers()
        val result = configs.map { cfg ->
            buildJsonObject {
                put("id", cfg.id)
                put("name", cfg.name)
                put("type", cfg.type)
                put("status", statuses[cfg.id]?.state?.name ?: cfg.status)
                put("internal", cfg.internal)
                put("disabled", cfg.disabled)
            }
        }
        return textResponse(JsonArray(result).toString())
    }

    suspend fun handleManageMcpServer(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val serverId = args.str("serverId") ?: return missingField("serverId")
        val action = args.str("action") ?: return missingField("action")
        if (serverId == INTERNAL_SERVER_ID) {
            return errorResponse("Cannot manage internal server: $INTERNAL_SERVER_ID")
        }
        return try {
            val status = when (action) {
                "start" -> mcpProcessManager.startServer(serverId)
                "stop" -> mcpProcessManager.stopServer(serverId)
                "restart" -> mcpProcessManager.restartServer(serverId)
                else -> return errorResponse("Unknown action: $action")
            }
            textResponse(json.encodeToString(status))
        } catch (e: Exception) {
            errorResponse("MCP server action failed: ${e.message}")
        }
    }
}
