package com.assistant.server.mcp

import com.assistant.server.mcp.internal.InternalToolRegistry
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property 9: Tool definitions completeness
 *
 * For any tool trong Internal_MCP_Server, tool definition SHALL have:
 * (a) inputSchema is a valid JSON Schema object with `type: "object"` and `properties`
 * (b) description contains `requiredPermission` and `requiredRole` information
 * (c) all required parameters are listed in schema `required` array
 *
 * **Validates: Requirements AC 6.105, AC 6.108**
 *
 * Feature: mcp-servers, Property 9: Tool definitions completeness
 */
@OptIn(ExperimentalKotest::class)
class InternalToolDefinitionsPropertyTest {

    private val registry = InternalToolRegistry()
    private val allTools = registry.getAllTools()

    /** Arb that picks a random tool from the registry. */
    private val arbTool = Arb.of(allTools)

    /**
     * Property 9a — inputSchema is a valid JSON Schema object
     * with `type: "object"` and `properties` key.
     */
    @Test
    fun `Property 9a - inputSchema has type object and properties`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbTool) { tool ->
                val schema = tool.inputSchema
                assertTrue(
                    schema is JsonObject,
                    "Tool '${tool.name}': inputSchema must be JsonObject"
                )
                val obj = schema as JsonObject

                val type = obj["type"]
                assertNotNull(type, "Tool '${tool.name}': schema missing 'type'")
                assertEquals(
                    "object",
                    type.jsonPrimitive.content,
                    "Tool '${tool.name}': schema type must be 'object'"
                )

                assertTrue(
                    "properties" in obj,
                    "Tool '${tool.name}': schema missing 'properties' key"
                )
                assertTrue(
                    obj["properties"] is JsonObject,
                    "Tool '${tool.name}': 'properties' must be JsonObject"
                )
            }
        }
    }

    /**
     * Property 9b — description contains [Permission:] and [Role:]
     * substrings so AI agents know access requirements.
     */
    @Test
    fun `Property 9b - description contains Permission and Role info`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbTool) { tool ->
                assertTrue(
                    "[Permission:" in tool.description,
                    "Tool '${tool.name}': description missing [Permission:] tag"
                )
                assertTrue(
                    "[Role:" in tool.description,
                    "Tool '${tool.name}': description missing [Role:] tag"
                )
            }
        }
    }

    /**
     * Property 9c — required parameters listed in schema required array,
     * and requiredPermission/requiredRole/name are non-empty.
     */
    @Test
    fun `Property 9c - required array and non-empty metadata`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbTool) { tool ->
                assertTrue(
                    tool.name.isNotEmpty(),
                    "Tool name must be non-empty"
                )
                assertTrue(
                    tool.requiredPermission.isNotEmpty(),
                    "Tool '${tool.name}': requiredPermission must be non-empty"
                )
                assertTrue(
                    tool.requiredRole.isNotEmpty(),
                    "Tool '${tool.name}': requiredRole must be non-empty"
                )

                val schema = tool.inputSchema as JsonObject
                val requiredArr = schema["required"]
                assertNotNull(
                    requiredArr,
                    "Tool '${tool.name}': schema missing 'required' array"
                )
                assertTrue(
                    requiredArr is JsonArray,
                    "Tool '${tool.name}': 'required' must be JsonArray"
                )

                // Every item in required array must exist in properties
                val properties = schema["properties"] as JsonObject
                val requiredNames = requiredArr
                    .map { (it as JsonPrimitive).content }

                for (param in requiredNames) {
                    assertTrue(
                        param in properties,
                        "Tool '${tool.name}': required param '$param' " +
                            "not found in properties"
                    )
                }
            }
        }
    }
}
