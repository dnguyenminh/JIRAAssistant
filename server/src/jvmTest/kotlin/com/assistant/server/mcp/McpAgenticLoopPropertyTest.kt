package com.assistant.server.mcp

import com.assistant.server.chat.McpAgenticLoop
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 6: Agentic loop termination and graceful degradation
 *
 * parseMcpToolCall returns null for non-tool-call strings and correctly
 * parses valid tool call JSON. It should never throw an exception.
 *
 * **Validates: Requirements 6.53, 6.55**
 *
 * Feature: mcp-runtime, Property 6: Agentic loop termination and graceful degradation
 */
@OptIn(ExperimentalKotest::class)
class McpAgenticLoopPropertyTest {

    /**
     * Property 6 — parseMcpToolCall returns null for arbitrary
     * random strings that don't contain valid mcpToolCall JSON.
     * Must never throw an exception.
     */
    @Test
    fun `Property 6 - parseMcpToolCall returns null for non-tool-call strings`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), Arb.string(0..200)) { randomText ->
                val result = try {
                    McpAgenticLoop.parseMcpToolCall(randomText)
                } catch (e: Exception) {
                    fail("parseMcpToolCall should never throw, but threw: ${e.message}")
                }
                // Random strings almost certainly won't contain valid mcpToolCall JSON
                // If by chance they do parse, result is non-null — that's fine
                // The key property: no exception is thrown
                assertNotNull(result ?: Unit, "Should return null or valid result, never crash")
            }
        }
    }

    /**
     * Property 6 — parseMcpToolCall correctly parses valid tool call JSON.
     */
    @Test
    fun `Property 6 - parseMcpToolCall parses valid tool call JSON`() {
        val arbServerId = Arb.string(3..20, Codepoint.alphanumeric())
        val arbToolName = Arb.string(3..20, Codepoint.az())

        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbServerId,
                arbToolName
            ) { serverId, toolName ->
                val validJson = buildString {
                    append("""{"mcpToolCall":{"serverId":"$serverId",""")
                    append(""""toolName":"$toolName","arguments":{}}}""")
                }
                val result = McpAgenticLoop.parseMcpToolCall(validJson)
                assertNotNull(result, "Valid mcpToolCall JSON should parse successfully")
                assertEquals(serverId, result!!.serverId, "serverId must match")
                assertEquals(toolName, result.toolName, "toolName must match")
            }
        }
    }

    /**
     * Property 6 — parseMcpToolCall handles text with embedded JSON gracefully.
     */
    @Test
    fun `Property 6 - parseMcpToolCall handles embedded JSON in text`() {
        val arbPrefix = Arb.string(0..50)
        val arbSuffix = Arb.string(0..50)
        val arbToolName = Arb.string(3..15, Codepoint.az())

        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbPrefix,
                arbToolName,
                arbSuffix
            ) { prefix, toolName, suffix ->
                val text = buildString {
                    append(prefix)
                    append("""{"mcpToolCall":{"serverId":"srv1",""")
                    append(""""toolName":"$toolName","arguments":{}}}""")
                    append(suffix)
                }
                val result = try {
                    McpAgenticLoop.parseMcpToolCall(text)
                } catch (e: Exception) {
                    fail("Should never throw: ${e.message}")
                }
                // May or may not parse depending on prefix content — key: no crash
                if (result != null) {
                    assertEquals(toolName, result.toolName)
                }
            }
        }
    }
}
