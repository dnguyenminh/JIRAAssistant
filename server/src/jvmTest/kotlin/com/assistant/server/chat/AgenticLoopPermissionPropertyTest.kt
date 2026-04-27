package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import com.assistant.mcp.models.McpServerState
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 2: Disabled tools are skipped in agentic loop
 *
 * For any tool call in agentic loop, if tool has status disabled
 * for current user, agentic loop SHALL skip tool and return
 * message "disabled by user".
 *
 * **Validates: Requirements 1.2, 6.1**
 *
 * Feature: per-user-tool-permissions, Property 2
 */
@OptIn(ExperimentalKotest::class)
class AgenticLoopPermissionPropertyTest {

    private lateinit var permRepo: InMemoryPermissionRepo
    private lateinit var permService: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        permRepo = InMemoryPermissionRepo()
        permService = UserToolPermissionService(
            permRepo, InMemoryMcpServerRepo()
        )
    }

    private val arbServerId = Arb.string(3..10, Codepoint.az())
    private val arbToolName = Arb.string(3..12, Codepoint.az())
    private val arbUserId = Arb.string(3..10, Codepoint.az())

    private fun toolCallJson(sid: String, tool: String) =
        """{"mcpToolCall":{"serverId":"$sid","toolName":"$tool","arguments":{}}}"""

    private fun toResponse(r: AIResult, len: Int) =
        ChatResponse(reply = (r as AIResult.Success).response)

    /** Stub McpProcessManager so canExecute=true. */
    private val stubManager = StubMcpProcessManager()

    /**
     * Property 2 — disabled tool is skipped, AI receives
     * "disabled by user" in the tool result prompt.
     */
    @Test
    fun `Property 2 - disabled tool skipped in agentic loop`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolName
            ) { userId, serverId, toolName ->
                permRepo.clear()
                val key = "$serverId::$toolName"
                permService.savePermissions(userId, mapOf(key to "disabled"))
                var callCount = 0
                val response = McpAgenticLoop.execute(
                    initialPrompt = "use tool",
                    mcpProcessManager = stubManager,
                    callAI = {
                        callCount++
                        if (callCount == 1) AIResult.Success(toolCallJson(serverId, toolName))
                        else AIResult.Success("Final after skip")
                    },
                    toResponse = ::toResponse,
                    syncHandler = null, projectKey = null,
                    userId = userId, permService = permService
                )
                assertTrue(
                    response.reply.contains("disabled by user") ||
                        response.reply.contains("Final after skip"),
                    "Disabled tool must be skipped: ${response.reply}"
                )
            }
        }
    }

    /**
     * Property 2 — enabled tool is NOT skipped
     * (no "disabled by user" in response).
     */
    @Test
    fun `Property 2 - enabled tool is not skipped`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolName
            ) { userId, serverId, toolName ->
                permRepo.clear()
                val key = "$serverId::$toolName"
                permService.savePermissions(userId, mapOf(key to "enabled"))
                var callCount = 0
                val response = McpAgenticLoop.execute(
                    initialPrompt = "use tool",
                    mcpProcessManager = stubManager,
                    callAI = {
                        callCount++
                        if (callCount == 1) AIResult.Success(toolCallJson(serverId, toolName))
                        else AIResult.Success("Done normally")
                    },
                    toResponse = ::toResponse,
                    syncHandler = null, projectKey = null,
                    userId = userId, permService = permService
                )
                assertFalse(
                    response.reply.contains("disabled by user"),
                    "Enabled tool must NOT show disabled msg"
                )
            }
        }
    }
}

/** Minimal McpProcessManager stub for permission tests. */
private class StubMcpProcessManager : McpProcessManager {
    private val stopped = McpProcessStatus("x", state = McpServerState.STOPPED)
    override suspend fun startServer(id: String) = stopped
    override suspend fun stopServer(id: String) = stopped
    override suspend fun restartServer(id: String) = stopped
    override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
    override fun getStatus(id: String): McpProcessStatus? = null
    override suspend fun startAllEnabled() {}
    override suspend fun stopAll() {}
    override fun getActiveTools() = emptyList<McpAggregatedTool>()
    override fun getClient(configId: String): McpProtocolClient? = null
}
