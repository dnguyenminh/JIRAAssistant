package com.assistant.server.agent.home

import com.assistant.mcp.models.McpAggregatedTool
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Property 5: Collision detection prefixes only duplicates.
 *
 * For any list of MCP tools from multiple servers,
 * `McpCollisionDetector.resolve()` SHALL prefix tool names with
 * `{serverName}_` only when two or more servers expose a tool
 * with the same name. Tools with unique names SHALL keep their
 * original name.
 *
 * **Validates: Requirements 2.7**
 *
 * Feature: agent-mcp-tool-bridge,
 * Property 5: Collision detection prefixes only duplicates
 */
@OptIn(ExperimentalKotest::class)
class McpCollisionDetectorPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val emptySchema = JsonObject(emptyMap())
    private val safeStr = Arb.string(1..8, Codepoint.alphanumeric())

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-5")
    fun `unique names keep original name`() {
        runBlocking {
            checkAll(cfg, arbToolList()) { tools ->
                val resolved = McpCollisionDetector.resolve(tools)
                resolved.size shouldBe tools.size
                val nameCount = tools.groupBy { it.name }
                resolved.forEach { r ->
                    val isUnique = (nameCount[r.originalName]?.size ?: 0) <= 1
                    if (isUnique) {
                        r.registeredName shouldBe r.originalName
                        r.wasRenamed shouldBe false
                    }
                }
            }
        }
    }

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-5")
    fun `duplicates get server prefix`() {
        runBlocking {
            checkAll(cfg, arbToolListWithCollisions()) { tools ->
                val resolved = McpCollisionDetector.resolve(tools)
                val nameCount = tools.groupBy { it.name }
                resolved.forEach { r ->
                    val isDuplicate = (nameCount[r.originalName]?.size ?: 0) > 1
                    if (isDuplicate) {
                        r.registeredName shouldBe "${r.serverName}_${r.originalName}"
                        r.wasRenamed shouldBe true
                    }
                }
            }
        }
    }

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-5")
    fun `no two resolved tools share a registeredName`() {
        runBlocking {
            checkAll(cfg, arbToolList()) { tools ->
                val resolved = McpCollisionDetector.resolve(tools)
                val byKey = resolved.groupBy { "${it.serverName}::${it.originalName}" }
                byKey.values.forEach { group ->
                    assertTrue(
                        group.map { it.registeredName }.toSet().size == 1,
                        "Same server+tool should resolve to same name"
                    )
                }
                val distinctNames = byKey.values.map { it.first().registeredName }.toSet()
                assertTrue(
                    distinctNames.size == byKey.keys.size,
                    "Different server+tool combos should have unique names"
                )
            }
        }
    }

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-5")
    fun `empty tool list returns empty`() {
        val resolved = McpCollisionDetector.resolve(emptyList())
        resolved.size shouldBe 0
    }

    // ── Generators ──────────────────────────────────────────────

    private fun arbToolList(): Arb<List<McpAggregatedTool>> = arbitrary {
        val serverCount = Arb.int(1..4).bind()
        val servers = (1..serverCount).map { safeStr.bind() }
        val toolCount = Arb.int(1..10).bind()
        (1..toolCount).map {
            McpAggregatedTool(
                serverId = Arb.element(servers).bind(),
                serverName = Arb.element(servers).bind(),
                name = safeStr.bind(),
                description = "desc",
                inputSchema = emptySchema
            )
        }
    }

    private fun arbToolListWithCollisions(): Arb<List<McpAggregatedTool>> = arbitrary {
        val sharedName = safeStr.bind()
        val server1 = safeStr.bind()
        val server2 = safeStr.bind() + "_2"
        val colliding = listOf(
            makeTool(server1, sharedName),
            makeTool(server2, sharedName)
        )
        val extraCount = Arb.int(0..5).bind()
        val extras = (1..extraCount).map { makeTool(safeStr.bind(), safeStr.bind()) }
        colliding + extras
    }

    private fun makeTool(server: String, name: String) = McpAggregatedTool(
        serverId = server, serverName = server,
        name = name, description = "desc", inputSchema = emptySchema
    )
}
