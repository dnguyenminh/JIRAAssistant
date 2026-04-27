package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 8: Tool result POC protocol formatting

/**
 * Property 8: Tool result POC protocol formatting
 *
 * For any tool name, success boolean, data string, and error string,
 * the formatted POC protocol result SHALL be valid JSON containing
 * "type":"tool_result", the tool name, the success value, and the
 * data/error strings.
 *
 * **Validates: Requirements 10.3**
 */
class ToolResultFormattingPropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Property 8 - formatted result is valid POC protocol JSON`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolName(),
                Arb.boolean(),
                arbSafeString(),
                arbSafeString()
            ) { toolName, success, data, error ->
                val result = ToolExecutionBridge.formatToolResult(
                    toolName, success, data, error
                )

                // Must be valid JSON
                val parsed = assertValidJson(result)

                // Must contain "type":"tool_result"
                assertField(parsed, "type", "tool_result")

                // Must contain the tool name
                assertField(parsed, "tool", toolName)

                // Must contain the success value
                val successValue = parsed["success"]?.jsonPrimitive?.boolean
                assertNotNull(successValue, "Missing 'success' field")
                assertEquals(success, successValue)

                // Must contain data and error strings
                assertFieldPresent(parsed, "data")
                assertFieldPresent(parsed, "error")
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbToolName(): Arb<String> =
        Arb.string(1..20, Codepoint.alphanumeric())

    /**
     * Generate strings safe for JSON embedding (no special chars
     * that would break the JSON structure after escaping).
     */
    private fun arbSafeString(): Arb<String> =
        Arb.string(0..50, Codepoint.alphanumeric())

    // ── Helpers ─────────────────────────────────────────────

    private fun assertValidJson(jsonStr: String): JsonObject {
        return try {
            json.decodeFromString<JsonObject>(jsonStr)
        } catch (e: Exception) {
            throw AssertionError(
                "Expected valid JSON but got: $jsonStr", e
            )
        }
    }

    private fun assertField(
        obj: JsonObject, key: String, expected: String
    ) {
        val actual = obj[key]?.jsonPrimitive?.content
        assertEquals(
            expected, actual,
            "Field '$key' expected '$expected' but was '$actual'"
        )
    }

    private fun assertFieldPresent(obj: JsonObject, key: String) {
        assertTrue(
            obj.containsKey(key),
            "Expected field '$key' to be present in JSON"
        )
    }
}
