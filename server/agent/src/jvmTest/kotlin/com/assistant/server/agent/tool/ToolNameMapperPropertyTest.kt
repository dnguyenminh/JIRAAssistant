package com.assistant.server.agent.tool

import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 8: Tool name mapping round-trip
 *
 * For any configured tool name mapping (agentName → serverName + mcpToolName),
 * the adapter SHALL register with agentName in the ToolRegistry but call
 * McpProtocolClient.callTool() with mcpToolName.
 *
 * **Validates: Requirements 6.1, 6.2, 6.3**
 *
 * Feature: agent-mcp-tool-bridge, Property 8: Tool name mapping round-trip
 */
@OptIn(ExperimentalKotest::class)
class ToolNameMapperPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    private val arbIdentifier: Arb<String> =
        Arb.string(3..15, Codepoint.az()).map { "id_$it" }

    private val arbMapping: Arb<ToolNameMapper.ToolNameMapping> = arbitrary {
        ToolNameMapper.ToolNameMapping(
            agentName = arbIdentifier.bind(),
            serverName = arbIdentifier.bind(),
            mcpToolName = arbIdentifier.bind()
        )
    }

    private val arbMappingList: Arb<List<ToolNameMapper.ToolNameMapping>> =
        Arb.list(arbMapping, 1..8).map { list ->
            list.distinctBy { it.agentName }
        }

    /**
     * Property 8a — resolve() returns the mapping for every configured agentName.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `resolve returns mapping for every configured agentName`() {
        runBlocking {
            checkAll(cfg, arbMappingList) { mappings ->
                val map = mappings.associateBy { it.agentName }
                val mapper = ToolNameMapper(map)
                mappings.forEach { m ->
                    val resolved = mapper.resolve(m.agentName)
                    resolved.shouldNotBeNull()
                    resolved.mcpToolName shouldBe m.mcpToolName
                    resolved.serverName shouldBe m.serverName
                }
            }
        }
    }

    /**
     * Property 8b — getMcpName() returns mcpToolName for mapped agents.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `getMcpName returns mcpToolName for mapped agents`() {
        runBlocking {
            checkAll(cfg, arbMappingList) { mappings ->
                val map = mappings.associateBy { it.agentName }
                val mapper = ToolNameMapper(map)
                mappings.forEach { m ->
                    mapper.getMcpName(m.agentName) shouldBe m.mcpToolName
                }
            }
        }
    }

    /**
     * Property 8c — unmapped agent names return null from resolve().
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `unmapped agent names return null from resolve`() {
        runBlocking {
            checkAll(cfg, arbMappingList, arbIdentifier) { mappings, unknownName ->
                val map = mappings.associateBy { it.agentName }
                val mapper = ToolNameMapper(map)
                if (!map.containsKey(unknownName)) {
                    mapper.resolve(unknownName).shouldBeNull()
                    mapper.hasMapping(unknownName) shouldBe false
                    mapper.getMcpName(unknownName).shouldBeNull()
                }
            }
        }
    }

    /**
     * Property 8d — findByMcpTool() returns mapping when serverName + mcpToolName match.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `findByMcpTool returns mapping when server and tool match`() {
        runBlocking {
            checkAll(cfg, arbMappingList) { mappings ->
                val map = mappings.associateBy { it.agentName }
                val mapper = ToolNameMapper(map)
                mappings.forEach { m ->
                    val found = mapper.findByMcpTool(m.serverName, m.mcpToolName)
                    found.shouldNotBeNull()
                    found.agentName shouldBe m.agentName
                }
            }
        }
    }

    /**
     * Property 8e — fromConfig() builds mapper from config map correctly.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `fromConfig builds mapper from config map correctly`() {
        runBlocking {
            checkAll(cfg, arbMappingList) { mappings ->
                val config = mappings.associate { m ->
                    m.agentName to mapOf(
                        "serverName" to m.serverName,
                        "mcpToolName" to m.mcpToolName
                    )
                }
                val mapper = ToolNameMapper.fromConfig(config)
                mappings.forEach { m ->
                    mapper.hasMapping(m.agentName) shouldBe true
                    mapper.getMcpName(m.agentName) shouldBe m.mcpToolName
                }
            }
        }
    }

    /**
     * Property 8f — fromConfig() skips entries with missing fields.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `fromConfig skips entries with missing fields`() {
        runBlocking {
            checkAll(cfg, arbIdentifier) { agentName ->
                val incompleteConfig = mapOf(
                    agentName to mapOf("serverName" to "srv")
                    // missing "mcpToolName"
                )
                val mapper = ToolNameMapper.fromConfig(incompleteConfig)
                mapper.hasMapping(agentName) shouldBe false
            }
        }
    }

    /**
     * Property 8g — empty mapper returns null for any name.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-8")
    fun `empty mapper returns null for any name`() {
        runBlocking {
            checkAll(cfg, arbIdentifier) { name ->
                val mapper = ToolNameMapper()
                mapper.resolve(name).shouldBeNull()
                mapper.hasMapping(name) shouldBe false
                mapper.getMcpName(name).shouldBeNull()
            }
        }
    }
}
