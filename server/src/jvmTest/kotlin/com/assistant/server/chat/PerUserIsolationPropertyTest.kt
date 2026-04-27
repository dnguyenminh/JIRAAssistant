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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 5: Per-user isolation
 *
 * For any user with saved record, changing global auto_approve
 * SHALL NOT affect saved per-user permissions.
 *
 * **Validates: Requirements 3.5, 5.5**
 *
 * Feature: per-user-tool-permissions, Property 5
 */
@OptIn(ExperimentalKotest::class)
class PerUserIsolationPropertyTest {

    private lateinit var permRepo: InMemoryPermissionRepo
    private lateinit var mcpRepo: InMemoryMcpServerRepo
    private lateinit var service: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        permRepo = InMemoryPermissionRepo()
        mcpRepo = InMemoryMcpServerRepo()
        service = UserToolPermissionService(
            permRepo = permRepo,
            mcpServerRepo = mcpRepo
        )
    }

    private val arbServerId = Arb.string(2..10, Codepoint.az())
    private val arbToolName = Arb.string(2..12, Codepoint.az())
    private val arbValue = Arb.element("enabled", "disabled")
    private val arbUserId = Arb.string(3..15, Codepoint.az())

    private val arbEntry = Arb.bind(arbServerId, arbToolName, arbValue) { s, t, v ->
        "$s::$t" to v
    }

    private val arbPerms = Arb.list(arbEntry, 1..6).map { it.toMap() }

    private fun toolsJson(tools: List<String>): String =
        Json.encodeToString(tools)

    /**
     * Property 5 — user A's permissions unchanged after user B saves.
     */
    @Test
    fun `Property 5 - user B save does not affect user A`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbPerms, arbPerms
            ) { permsA, permsB ->
                permRepo.clear()
                service.savePermissions("userA", permsA)
                service.savePermissions("userB", permsB)

                val resultA = service.getEffectivePermissions("userA")
                assertEquals(permsA, resultA.permissions)
            }
        }
    }

    /**
     * Property 5 — changing global auto_approve does not affect saved user perms.
     */
    @Test
    fun `Property 5 - global auto_approve change does not affect saved perms`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbServerId, arbToolName, arbPerms
            ) { serverId, newTool, userPerms ->
                permRepo.clear()
                mcpRepo.deleteAll()

                val server = McpServerConfig(
                    id = serverId,
                    name = serverId,
                    autoApprove = toolsJson(listOf("tool_a"))
                )
                mcpRepo.insert(server)
                service.savePermissions("user1", userPerms)

                // Change global auto_approve
                val updated = server.copy(
                    autoApprove = toolsJson(listOf("tool_a", newTool))
                )
                mcpRepo.update(updated)

                val result = service.getEffectivePermissions("user1")
                assertEquals(userPerms, result.permissions)
            }
        }
    }

    /**
     * Property 5 — isEnabled reflects saved per-user value, not global.
     */
    @Test
    fun `Property 5 - isEnabled uses saved perms not global defaults`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbServerId, arbToolName
            ) { serverId, toolName ->
                permRepo.clear()
                mcpRepo.deleteAll()

                val key = "$serverId::$toolName"
                val perms = mapOf(key to "disabled")
                service.savePermissions("user1", perms)

                // Global says tool is approved
                val server = McpServerConfig(
                    id = serverId,
                    name = serverId,
                    autoApprove = toolsJson(listOf(toolName))
                )
                mcpRepo.insert(server)

                val enabled = service.isEnabled("user1", serverId, toolName)
                assertFalse(enabled, "Saved disabled should override global")
            }
        }
    }
}
