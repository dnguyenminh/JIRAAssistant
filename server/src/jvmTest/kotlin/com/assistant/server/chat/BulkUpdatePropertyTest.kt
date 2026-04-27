package com.assistant.server.chat

import com.assistant.mcp.McpServerConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 7: Bulk update applies to all tools
 *
 * For any server with N tools, enable_all → all N tools enabled.
 * disable_all → all N tools disabled.
 *
 * **Validates: Requirements 3.6**
 *
 * Feature: per-user-tool-permissions, Property 7
 */
@OptIn(ExperimentalKotest::class)
class BulkUpdatePropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val arbUserId: Arb<String> =
        Arb.string(3..15, Codepoint.az())

    private val arbServerId: Arb<String> =
        Arb.string(2..12, Codepoint.az())

    private val arbToolName: Arb<String> =
        Arb.string(2..15, Codepoint.az())

    private val arbToolList: Arb<List<String>> =
        Arb.list(arbToolName, 1..6).map { it.distinct() }

    private fun buildService(
        servers: List<McpServerConfig>
    ): Pair<InMemoryPermissionRepo, UserToolPermissionService> {
        val permRepo = InMemoryPermissionRepo()
        val mcpRepo = InMemoryMcpServerRepo(servers.toMutableList())
        return permRepo to UserToolPermissionService(permRepo, mcpRepo)
    }

    private fun makeServer(id: String, tools: List<String>) =
        McpServerConfig(
            id = id, name = "server-$id",
            autoApprove = json.encodeToString(tools)
        )

    /**
     * Property 7 — enable_all sets all N tools to enabled.
     */
    @Test
    fun `Property 7 - enable_all enables all tools`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolList
            ) { userId, serverId, tools ->
                val server = makeServer(serverId, tools)
                val (_, service) = buildService(listOf(server))

                service.bulkUpdate(userId, serverId, "enable_all")

                for (tool in tools) {
                    assertTrue(
                        service.isEnabled(userId, serverId, tool),
                        "Tool '$tool' should be enabled after enable_all"
                    )
                }
            }
        }
    }

    /**
     * Property 7 — disable_all sets all N tools to disabled.
     */
    @Test
    fun `Property 7 - disable_all disables all tools`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolList
            ) { userId, serverId, tools ->
                val server = makeServer(serverId, tools)
                val (_, service) = buildService(listOf(server))

                service.bulkUpdate(userId, serverId, "disable_all")

                for (tool in tools) {
                    assertFalse(
                        service.isEnabled(userId, serverId, tool),
                        "Tool '$tool' should be disabled after disable_all"
                    )
                }
            }
        }
    }

    /**
     * Property 7 — enable_all after disable_all restores all.
     */
    @Test
    fun `Property 7 - enable_all after disable_all restores all`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolList
            ) { userId, serverId, tools ->
                val server = makeServer(serverId, tools)
                val (_, service) = buildService(listOf(server))

                service.bulkUpdate(userId, serverId, "disable_all")
                service.bulkUpdate(userId, serverId, "enable_all")

                for (tool in tools) {
                    assertTrue(
                        service.isEnabled(userId, serverId, tool),
                        "Tool '$tool' should be enabled after re-enable"
                    )
                }
            }
        }
    }
}
