package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliType
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 1: Tool call parsing round-trip

/**
 * Property 1: Tool call parsing round-trip for CLI backends
 *
 * For any valid POC tool call JSON string of the form
 * {"type":"tool_call","tool":"<name>","params":{<key-value pairs>}},
 * calling isToolCall(json) SHALL return true and parseToolCall(json)
 * SHALL return a ToolRequest with matching tool and params fields.
 * For any string that is not valid tool call JSON, isToolCall SHALL
 * return false.
 *
 * **Validates: Requirements 1.3, 3.7**
 */
class ToolCallParsingPropertyTest {

    private val client = TestCliClient()

    @Test
    fun `Property 1 - valid tool call JSON round-trips correctly`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolName(),
                arbParamMap()
            ) { toolName, params ->
                val json = buildToolCallJson(toolName, params)

                assertTrue(
                    client.isToolCall(json),
                    "isToolCall should return true for valid JSON"
                )

                val parsed = client.parseToolCall(json)
                assertNotNull(parsed, "parseToolCall should not return null")
                assertEquals(toolName, parsed.tool)
                assertEquals(params, parsed.params)
            }
        }
    }

    @Test
    fun `Property 1 - invalid strings return false from isToolCall`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbInvalidInput()
            ) { input ->
                assertFalse(
                    client.isToolCall(input),
                    "isToolCall should return false for: $input"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbToolName(): Arb<String> =
        Arb.string(1..20, Codepoint.alphanumeric())

    private fun arbParamMap(): Arb<Map<String, String>> = arbitrary {
        val size = Arb.int(0..5).bind()
        (0 until size).associate {
            val key = Arb.string(1..10, Codepoint.alphanumeric()).bind()
            val value = Arb.string(0..20, Codepoint.alphanumeric()).bind()
            key to value
        }
    }

    private fun arbInvalidInput(): Arb<String> = Arb.choice(
        Arb.constant(""),
        Arb.string(1..50, Codepoint.alphanumeric()),
        Arb.constant("{\"type\":\"message\"}"),
        Arb.constant("{invalid json}"),
        Arb.constant("null"),
        Arb.constant("{\"type\":\"tool_result\"}")
    )

    // ── Helpers ─────────────────────────────────────────────

    private fun buildToolCallJson(
        tool: String,
        params: Map<String, String>
    ): String {
        val paramsJson = params.entries.joinToString(",") { (k, v) ->
            "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
        }
        return """{"type":"tool_call","tool":"${escapeJson(tool)}","params":{$paramsJson}}"""
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}

/**
 * Concrete test subclass of BaseNodeCliClient for testing
 * isToolCall and parseToolCall methods.
 */
private class TestCliClient : BaseNodeCliClient() {
    override val type = AiCliType.GEMINI
    override val displayName = "Test CLI"
    override val cliConfig = NodeCliConfig(
        commandName = "test",
        npmPackage = "test-pkg",
        jsEntryPath = "test.js"
    )
    override val cliJsPath = "/tmp/test.js"
    override fun buildCommandArgs(prompt: String) = emptyList<String>()
    override fun buildPersistentCommandArgs(isResume: Boolean) =
        emptyList<String>()
    override fun getInstallInstructions() = "Install test"
}
