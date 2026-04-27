package com.assistant.server.mcp

import com.assistant.mcp.models.McpError
import com.assistant.server.mcp.internal.ArgumentValidator
import com.assistant.server.mcp.internal.InternalToolRegistry
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 12: Argument validation
 *
 * For any tool có required parameters, khi gọi với arguments thiếu
 * required field hoặc sai type, hệ thống SHALL trả về JSON-RPC error
 * code -32602 (Invalid params) với message chỉ rõ field nào sai,
 * TRƯỚC KHI thực thi bất kỳ logic nào.
 *
 * **Validates: Requirements AC 6.112**
 *
 * Feature: mcp-servers, Property 12: Argument validation
 */
@OptIn(ExperimentalKotest::class)
class InternalArgumentValidationPropertyTest {

    private val registry = InternalToolRegistry()
    private val allTools = registry.getAllTools()

    private val toolsWithRequired = allTools.filter { tool ->
        val schema = tool.inputSchema.jsonObject
        val req = schema["required"]?.jsonArray ?: return@filter false
        req.isNotEmpty()
    }

    private val arbToolWithRequired = Arb.of(toolsWithRequired)

    /** Property 12a — Missing required field → McpError(-32602). */
    @Test
    fun `Property 12a - missing required field throws INVALID_PARAMS`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbToolWithRequired
            ) { tool ->
                val error = assertThrows(McpError::class.java) {
                    ArgumentValidator.validate(tool, JsonObject(emptyMap()))
                }
                assertEquals(McpError.INVALID_PARAMS, error.code)
                assertTrue(
                    error.errorMessage.contains("missing"),
                    "Message should mention 'missing': ${error.errorMessage}"
                )
            }
        }
    }

    /** Property 12b — Wrong type (int for string param) → McpError(-32602). */
    @Test
    fun `Property 12b - wrong type throws INVALID_PARAMS`() {
        val toolsWithStringParam = toolsWithRequired.filter { tool ->
            val props = tool.inputSchema.jsonObject["properties"]?.jsonObject
                ?: return@filter false
            props.any { (_, v) ->
                v.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "string"
            }
        }
        val arbStringTool = Arb.of(toolsWithStringParam)

        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbStringTool,
                Arb.int()
            ) { tool, intVal ->
                val schema = tool.inputSchema.jsonObject
                val props = schema["properties"]!!.jsonObject
                val stringKey = props.entries.first { (_, v) ->
                    v.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "string"
                }.key
                // Fill all required fields correctly, then override target with wrong type
                val args = buildJsonObject {
                    val validArgs = buildValidArgs(schema)
                    validArgs.forEach { (k, v) -> put(k, v) }
                    put(stringKey, intVal) // override with wrong type
                }

                val error = assertThrows(McpError::class.java) {
                    ArgumentValidator.validate(tool, args)
                }
                assertEquals(McpError.INVALID_PARAMS, error.code)
                assertTrue(
                    error.errorMessage.contains(stringKey),
                    "Message should mention field '$stringKey': ${error.errorMessage}"
                )
            }
        }
    }

    /** Property 12c — Invalid enum value → McpError(-32602). */
    @Test
    fun `Property 12c - invalid enum value throws INVALID_PARAMS`() {
        val navigateTool = registry.getTool("navigate_to_page")!!
        val arbInvalidPage = Arb.string(5..15, Codepoint.az())

        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbInvalidPage
            ) { invalidPage ->
                val args = buildJsonObject {
                    put("page", invalidPage)
                }
                val error = assertThrows(McpError::class.java) {
                    ArgumentValidator.validate(navigateTool, args)
                }
                assertEquals(McpError.INVALID_PARAMS, error.code)
                assertTrue(
                    error.errorMessage.contains("page"),
                    "Message should mention 'page': ${error.errorMessage}"
                )
            }
        }
    }

    /** Property 12d — Valid arguments do NOT throw. */
    @Test
    fun `Property 12d - valid arguments pass validation`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbToolWithRequired
            ) { tool ->
                val validArgs = buildValidArgs(tool.inputSchema.jsonObject)
                assertDoesNotThrow {
                    ArgumentValidator.validate(tool, validArgs)
                }
            }
        }
    }

    /** Build a JsonObject with all required fields set to correct types. */
    private fun buildValidArgs(schema: JsonObject): JsonObject {
        val props = schema["properties"]?.jsonObject ?: return JsonObject(emptyMap())
        val required = schema["required"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return buildJsonObject {
            for (key in required) {
                val propSchema = props[key]?.jsonObject ?: continue
                put(key, buildValidValue(propSchema))
            }
        }
    }

    private fun buildValidValue(propSchema: JsonObject): JsonElement {
        val enumVals = propSchema["enum"]?.jsonArray
        if (enumVals != null && enumVals.isNotEmpty()) {
            return enumVals.first()
        }
        return when (propSchema["type"]?.jsonPrimitive?.contentOrNull) {
            "string" -> JsonPrimitive("test_value")
            "integer" -> JsonPrimitive(1)
            "boolean" -> JsonPrimitive(false)
            "object" -> JsonObject(emptyMap())
            "array" -> JsonArray(emptyList())
            else -> JsonPrimitive("fallback")
        }
    }
}
