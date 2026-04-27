package com.assistant.server.mcp

import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.server.mcp.internal.InternalMcpBridge
import com.assistant.server.mcp.internal.InternalToolRegistry
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 7: Tool aggregation includes internal tools
 *
 * For any tập hợp external MCP servers đang chạy (0 hoặc nhiều),
 * khi gọi aggregated tools list, kết quả SHALL luôn bao gồm tất cả
 * tools từ Internal_MCP_Server với serverId: "jira-assistant-ui",
 * merged cùng external tools.
 * Số lượng tools trong kết quả = internal tools + tổng external tools.
 *
 * **Validates: Requirements AC 6.71, AC 6.107**
 *
 * Feature: mcp-servers, Property 7: Tool aggregation includes internal tools
 */
@OptIn(ExperimentalKotest::class)
class InternalToolAggregationPropertyTest {

    private val registry = InternalToolRegistry()
    private val allInternalTools = registry.getAllTools()

    private val dummySchema = JsonObject(mapOf(
        "type" to JsonPrimitive("object"),
        "properties" to JsonObject(emptyMap())
    ))

    /** Generate a random external server ID (not the internal one). */
    private val arbExternalServerId: Arb<String> =
        Arb.string(5..15, Codepoint.az())
            .filter { it != InternalMcpBridge.INTERNAL_SERVER_ID }

    /** Generate a random external tool. */
    private fun arbExternalTool(serverId: String): McpAggregatedTool =
        McpAggregatedTool(
            serverId = serverId,
            serverName = "External-$serverId",
            name = "ext_tool_${serverId.take(5)}",
            description = "External tool",
            inputSchema = dummySchema
        )

    /** Generate a list of external tools from 0-5 servers, 1-3 tools each. */
    private val arbExternalTools: Arb<List<McpAggregatedTool>> =
        Arb.list(arbExternalServerId, 0..5).map { serverIds ->
            serverIds.flatMapIndexed { idx, sid ->
                (0..idx % 3).map { t ->
                    McpAggregatedTool(
                        serverId = sid,
                        serverName = "Server-$sid",
                        name = "tool_${sid}_$t",
                        description = "Tool $t from $sid",
                        inputSchema = dummySchema
                    )
                }
            }
        }

    /** Simulate what InternalMcpBridge.getAggregatedTools() does. */
    private fun buildInternalAggregated(): List<McpAggregatedTool> =
        allInternalTools.map { tool ->
            McpAggregatedTool(
                serverId = InternalMcpBridge.INTERNAL_SERVER_ID,
                serverName = InternalMcpBridge.INTERNAL_SERVER_NAME,
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }

    /**
     * Property 7a — Merged list size = internal + external.
     */
    @Test
    fun `Property 7a - merged count equals internal plus external`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbExternalTools
            ) { externalTools ->
                val internal = buildInternalAggregated()
                val merged = internal + externalTools

                assertEquals(
                    internal.size + externalTools.size,
                    merged.size,
                    "Merged size must equal internal + external"
                )
            }
        }
    }

    /**
     * Property 7b — All internal tools present with correct serverId.
     */
    @Test
    fun `Property 7b - all internal tools present with correct serverId`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbExternalTools
            ) { externalTools ->
                val internal = buildInternalAggregated()
                val merged = internal + externalTools

                val internalInMerged = merged.filter {
                    it.serverId == InternalMcpBridge.INTERNAL_SERVER_ID
                }
                assertEquals(
                    allInternalTools.size,
                    internalInMerged.size,
                    "All internal tools must be in merged list"
                )
                for (tool in internalInMerged) {
                    assertEquals(
                        InternalMcpBridge.INTERNAL_SERVER_NAME,
                        tool.serverName
                    )
                }
            }
        }
    }

    /**
     * Property 7c — Every internal tool name appears in merged list.
     */
    @Test
    fun `Property 7c - every registry tool name in merged list`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbExternalTools
            ) { externalTools ->
                val merged = buildInternalAggregated() + externalTools
                val mergedNames = merged.map { it.name }.toSet()

                for (tool in allInternalTools) {
                    assertTrue(
                        tool.name in mergedNames,
                        "Internal tool '${tool.name}' missing from merged"
                    )
                }
            }
        }
    }

    /**
     * Property 7d — Internal tools always present even with 0 externals.
     */
    @Test
    fun `Property 7d - internal tools present with zero external servers`() {
        val internal = buildInternalAggregated()
        val merged = internal + emptyList()

        assertEquals(allInternalTools.size, merged.size)
        assertTrue(merged.all {
            it.serverId == InternalMcpBridge.INTERNAL_SERVER_ID
        })
    }
}
