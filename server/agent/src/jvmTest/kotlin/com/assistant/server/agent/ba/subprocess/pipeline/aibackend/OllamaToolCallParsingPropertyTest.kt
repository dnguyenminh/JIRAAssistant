package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClientHelpers
import kotlinx.serialization.json.Json
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

// Feature: poc-agent-replacement, Property 3: Ollama tool_calls parsing

/**
 * Property 3: Ollama tool_calls parsing
 *
 * For any valid OllamaChatResponse JSON containing a non-empty tool_calls
 * array, isToolCall(json) SHALL return true and parseToolCall(json) SHALL
 * return a ToolRequest with tool matching the first tool call's function
 * name and params matching its arguments.
 * When tool_calls absent: isToolCall returns false.
 *
 * **Validates: Requirements 7.3**
 */
class OllamaToolCallParsingPropertyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `Property 3 - isToolCall true when tool_calls present`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolName(),
                arbArguments()
            ) { toolName, args ->
                val responseJson = buildResponseWithToolCalls(toolName, args)
                assertTrue(
                    OllamaApiClientHelpers.isToolCall(json, responseJson),
                    "isToolCall should be true when tool_calls present"
                )
            }
        }
    }

    @Test
    fun `Property 3 - parseToolCall returns matching ToolRequest`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolName(),
                arbArguments()
            ) { toolName, args ->
                val responseJson = buildResponseWithToolCalls(toolName, args)
                val result = OllamaApiClientHelpers.parseToolCall(
                    json, responseJson
                )
                assertNotNull(result, "parseToolCall should not return null")
                assertEquals(toolName, result.tool, "tool name must match")
                assertEquals(args, result.params, "params must match")
            }
        }
    }

    @Test
    fun `Property 3 - isToolCall false when no tool_calls`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbContentText()
            ) { content ->
                val responseJson = buildResponseWithoutToolCalls(content)
                assertFalse(
                    OllamaApiClientHelpers.isToolCall(json, responseJson),
                    "isToolCall should be false without tool_calls"
                )
            }
        }
    }

    @Test
    fun `Property 3 - isToolCall false for invalid JSON`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(0..50, Codepoint.alphanumeric())
            ) { randomText ->
                assertFalse(
                    OllamaApiClientHelpers.isToolCall(json, randomText),
                    "isToolCall should be false for invalid JSON"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbToolName(): Arb<String> =
        Arb.string(1..15, Codepoint.alphanumeric())

    private fun arbContentText(): Arb<String> =
        Arb.string(0..30, Codepoint.alphanumeric())

    private fun arbArguments(): Arb<Map<String, String>> = arbitrary {
        val size = Arb.int(0..4).bind()
        (0 until size).associate {
            val k = Arb.string(1..8, Codepoint.alphanumeric()).bind()
            val v = Arb.string(0..15, Codepoint.alphanumeric()).bind()
            k to v
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun buildResponseWithToolCalls(
        toolName: String,
        args: Map<String, String>
    ): String {
        val argsJson = args.entries.joinToString(",") { (k, v) ->
            "\"${esc(k)}\":\"${esc(v)}\""
        }
        return """
        {
          "model":"test",
          "message":{
            "role":"assistant",
            "content":"",
            "tool_calls":[{
              "function":{
                "name":"${esc(toolName)}",
                "arguments":{$argsJson}
              }
            }]
          },
          "done":true
        }
        """.trimIndent()
    }

    private fun buildResponseWithoutToolCalls(content: String): String =
        """
        {
          "model":"test",
          "message":{
            "role":"assistant",
            "content":"${esc(content)}"
          },
          "done":true
        }
        """.trimIndent()

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
