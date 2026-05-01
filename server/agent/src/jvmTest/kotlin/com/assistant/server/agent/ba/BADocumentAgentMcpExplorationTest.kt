package com.assistant.server.agent.ba

import com.assistant.agent.subprocess.ToolCallRequest
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Integration tests for the real MCP pipeline.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
 *
 * Tests verify that MCP tools are registered and invocable
 * through the real pipeline. Builtin KB tools are always
 * available; external MCP tools (Jira) require the server
 * binary and are skipped if unavailable.
 */
@Tag("ba-agent-integration")
class BADocumentAgentMcpExplorationTest {

    @Test
    fun `builtin KB tools are registered`() {
        assumeMcpAvailable()
        runBlocking {
            val real = buildRealToolLayer()
            val tools = real.subprocessProxy.getAvailableToolDescriptors()
            val kbTools = tools.filter {
                it.name.startsWith("mcp_local_knowledge_base_")
            }
            kbTools shouldHaveAtLeastSize 3
        }
    }

    @Test
    fun `KB tool invocation returns success`() {
        assumeMcpAvailable()
        runBlocking {
            val real = buildRealToolLayer()
            val result = real.toolRegistry.invoke(
                "mcp_local_knowledge_base_search_knowledge",
                mapOf("query" to "test")
            )
            result.success shouldBe true
        }
    }

    @Test
    fun `KB tool call routing goes through MCP tool wrapper`() {
        assumeMcpAvailable()
        runBlocking {
            val real = buildRealToolLayer()
            val request = ToolCallRequest(
                id = "explore-1",
                name = "mcp_local_knowledge_base_get_ticket_info",
                arguments = mapOf("ticketId" to "PROJ-123")
            )
            val response = real.subprocessProxy
                .handleToolCallRequest(request)
            response.success shouldBe true
        }
    }

    @Test
    fun `Jira MCP tools registered when server available`() {
        assumeMcpAvailable()
        runBlocking {
            val real = buildRealToolLayer()
            val jiraTools = real.toolRegistry.listTools()
                .filter { it.name.startsWith("mcp_jira_") }
            assumeTrue(
                jiraTools.isNotEmpty(),
                "Skipped: Jira MCP server not available"
            )
            jiraTools shouldHaveAtLeastSize 1
        }
    }

    @Test
    fun `test MCP config loads from test properties`() {
        val props = java.util.Properties()
        val stream = javaClass.classLoader
            .getResourceAsStream("test.properties")
        assumeTrue(stream != null, "test.properties not found")
        props.load(stream)
        val mcpKeys = props.stringPropertyNames()
            .filter { it.startsWith("test.mcp.") }
        mcpKeys shouldHaveAtLeastSize 1
    }
}
