package com.assistant.server.chat

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
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 1: Disabled tools not in system prompt
 *
 * For any userId and tool with status disabled, that tool SHALL NOT
 * appear in system prompt injection. Number of tools in prompt =
 * total tools - disabled tools.
 *
 * **Validates: Requirements 1.3, 6.4**
 *
 * Feature: per-user-tool-permissions, Property 1
 */
@OptIn(ExperimentalKotest::class)
class SystemPromptFilterPropertyTest {

    private lateinit var permRepo: InMemoryPermissionRepo
    private lateinit var permService: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        permRepo = InMemoryPermissionRepo()
        permService = UserToolPermissionService(
            permRepo, InMemoryMcpServerRepo()
        )
    }

    private val arbServerId = Arb.string(3..8, Codepoint.az())
    private val arbToolName = Arb.string(3..10, Codepoint.az())
    private val arbUserId = Arb.string(3..8, Codepoint.az())
    private val arbDesc = Arb.string(5..15, Codepoint.az())

    private fun makeTool(sid: String, name: String, desc: String) =
        McpAggregatedTool(sid, sid, name, desc, JsonObject(emptyMap()))

    @Test
    fun `Property 1 - disabled tools absent from prompt`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolName, arbToolName, arbDesc
            ) { userId, sid, enabledName, disabledName, desc ->
                if (enabledName == disabledName) return@checkAll
                permRepo.clear()
                val key = "$sid::$disabledName"
                permService.savePermissions(userId, mapOf(key to "disabled"))
                val mgr = ToolListManager(listOf(
                    makeTool(sid, enabledName, desc),
                    makeTool(sid, disabledName, desc)
                ))
                val prompt = ChatMcpToolsContext.build(
                    null, mgr, emptyList(), userId, permService
                )
                assertFalse(
                    prompt.contains(disabledName),
                    "Disabled tool '$disabledName' must not appear in prompt"
                )
                assertTrue(
                    prompt.contains(enabledName),
                    "Enabled tool '$enabledName' must appear in prompt"
                )
            }
        }
    }

    @Test
    fun `Property 1 - tool count equals total minus disabled`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId,
                Arb.list(arbToolName, 2..5), arbDesc
            ) { userId, sid, names, desc ->
                val unique = names.distinct()
                if (unique.size < 2) return@checkAll
                permRepo.clear()
                val disabledSet = unique.take(unique.size / 2).toSet()
                val perms = disabledSet.associate { "$sid::$it" to "disabled" }
                permService.savePermissions(userId, perms)
                val tools = unique.map { makeTool(sid, it, desc) }
                val mgr = ToolListManager(tools)
                val prompt = ChatMcpToolsContext.build(
                    null, mgr, emptyList(), userId, permService
                )
                val expected = unique.size - disabledSet.size
                assertPromptToolCount(prompt, expected)
            }
        }
    }

    private fun assertPromptToolCount(prompt: String, expected: Int) {
        val regex = Regex("""\[MCP:\w+]\s+\w+:""")
        val actual = regex.findAll(prompt).count()
        assertEquals(expected, actual, "Tool count mismatch in prompt")
    }
}

/** McpProcessManager stub returning a fixed tool list. */
private class ToolListManager(
    private val tools: List<McpAggregatedTool>
) : McpProcessManager {
    private val stopped = McpProcessStatus("x", state = McpServerState.STOPPED)
    override suspend fun startServer(configId: String) = stopped
    override suspend fun stopServer(configId: String) = stopped
    override suspend fun restartServer(configId: String) = stopped
    override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
    override fun getStatus(configId: String): McpProcessStatus? = null
    override suspend fun startAllEnabled() {}
    override suspend fun stopAll() {}
    override fun getActiveTools() = tools
    override fun getClient(configId: String): McpProtocolClient? = null
}
