package com.assistant.server.agent.ba.subprocess.pipeline.cli

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for [ToolCallProtocol].
 *
 * - Property 1: Parsing extracts correct fields from noisy lines
 * - Property 2: Tool result formatting round-trip preserves data
 * - Property 3: Malformed input produces null without exceptions
 */
@OptIn(ExperimentalKotest::class)
class ToolCallProtocolPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)
    private val json = Json { ignoreUnknownKeys = true }

    // -- Generators --

    private fun arbToolName(): Arb<String> =
        Arb.string(1..30, Codepoint.alphanumeric())

    private fun arbArgMap(): Arb<Map<String, String>> =
        Arb.map(
            keyArb = Arb.string(1..15, Codepoint.alphanumeric()),
            valueArb = Arb.string(0..30, Codepoint.alphanumeric()),
            minSize = 0, maxSize = 5
        )

    private fun arbPrefix(): Arb<String> =
        Arb.string(0..40, Codepoint.printableAscii())
            .filter { !it.contains("{\"toolCall") }

    // -- Property 1 --

    /**
     * **Validates: Requirements 2.1, 2.3**
     *
     * For any valid tool call JSON preceded by any prefix,
     * parseToolCall extracts matching name and arguments.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P1 - parsing extracts correct fields from noisy lines`() {
        runBlocking {
            checkAll(cfg, arbToolName(), arbArgMap(), arbPrefix()) {
                    name, args, prefix ->
                val argsJson = args.entries.joinToString(",") { (k, v) ->
                    "\"$k\":\"$v\""
                }
                val line = "$prefix{\"toolCall\":{\"name\":\"$name\"," +
                    "\"arguments\":{$argsJson}}}"

                val result = ToolCallProtocol.parseToolCall(line)

                assertNotNull(result) {
                    "Expected non-null for line: $line"
                }
                assertEquals(name, result!!.name)
                assertEquals(args, result.arguments)
            }
        }
    }

    // -- Property 2 --

    /**
     * **Validates: Requirements 2.2, 2.5, 2.6**
     *
     * For any tool result inputs (including special chars),
     * formatToolResult produces valid JSON whose fields match.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P2 - tool result round-trip preserves data`() {
        runBlocking {
            checkAll(
                cfg,
                arbToolName(),
                Arb.boolean(),
                Arb.string(0..200),
                Arb.string(0..100)
            ) { name, success, data, error ->
                val formatted = ToolCallProtocol.formatToolResult(
                    name, success, data, error
                )
                val root = json.parseToJsonElement(formatted).jsonObject
                val tr = root["toolResult"]?.jsonObject

                assertNotNull(tr) { "Missing toolResult key" }
                assertEquals(name, tr!!["name"]?.jsonPrimitive?.content)
                assertEquals(
                    success, tr["success"]?.jsonPrimitive?.boolean
                )
                assertEquals(data, tr["data"]?.jsonPrimitive?.content)
                assertEquals(error, tr["error"]?.jsonPrimitive?.content)
            }
        }
    }

    // -- Property 3 --

    /**
     * **Validates: Requirements 2.4**
     *
     * For any malformed input, parseToolCall returns null
     * and does not throw.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P3 - malformed input produces null without exceptions`() {
        runBlocking {
            val malformedArb: Arb<String> = arbitrary {
                when (Arb.int(0..2).bind()) {
                    0 -> Arb.string(0..200, Codepoint.printableAscii()).bind()
                    1 -> partialJson(Arb.string(1..20, Codepoint.alphanumeric()).bind())
                    else -> wrongStructureJson(Arb.string(1..20, Codepoint.alphanumeric()).bind())
                }
            }
            checkAll(cfg, malformedArb) { input ->
                val result = runCatching {
                    ToolCallProtocol.parseToolCall(input)
                }
                assertTrue(result.isSuccess) {
                    "parseToolCall threw for: $input"
                }
                assertNull(result.getOrNull()) {
                    "Expected null for malformed: $input"
                }
            }
        }
    }

    private fun partialJson(name: String): String =
        "{\"toolCall\":{\"name\":\"$name\""

    private fun wrongStructureJson(name: String): String =
        "{\"wrongKey\":{\"name\":\"$name\",\"arguments\":{}}}"
}
