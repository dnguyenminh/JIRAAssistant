package com.assistant.server.agent.tool

import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 3: inputSchema parameter extraction
 *
 * For any valid JSON Schema object with a "properties" field,
 * InputSchemaParser.extractParameterNames() SHALL return exactly
 * the set of top-level property keys, in the order they appear.
 *
 * **Validates: Requirements 1.6**
 *
 * Feature: agent-mcp-tool-bridge, Property 3: inputSchema parameter extraction
 */
@OptIn(ExperimentalKotest::class)
class InputSchemaParserPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /** Generate a valid property name (alphanumeric + underscore). */
    private val arbPropertyName: Arb<String> =
        Arb.string(1..20, Codepoint.az()).map { "prop_$it" }

    /** Generate a list of unique property names. */
    private val arbPropertyNames: Arb<List<String>> =
        Arb.list(arbPropertyName, 0..10).map { it.distinct() }

    /** Build a JSON Schema object from a list of property names. */
    private fun buildSchema(names: List<String>): JsonElement {
        val properties = JsonObject(names.associateWith { name ->
            JsonObject(mapOf("type" to JsonPrimitive("string")))
        })
        return JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to properties
        ))
    }

    /**
     * Property 3a — Extracted names match properties keys exactly.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-3")
    fun `extracted names match properties keys exactly`() {
        runBlocking {
            checkAll(cfg, arbPropertyNames) { names ->
                val schema = buildSchema(names)
                val result = InputSchemaParser.extractParameterNames(schema)
                result shouldBe names
            }
        }
    }

    /**
     * Property 3b — Empty properties yields empty list.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-3")
    fun `empty properties yields empty list`() {
        val schema = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(emptyMap())
        ))
        val result = InputSchemaParser.extractParameterNames(schema)
        result shouldBe emptyList()
    }

    /**
     * Property 3c — Missing properties key yields empty list.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-3")
    fun `missing properties key yields empty list`() {
        runBlocking {
            checkAll(cfg, Arb.string(1..10, Codepoint.az())) { typeName ->
                val schema = JsonObject(mapOf(
                    "type" to JsonPrimitive(typeName)
                ))
                val result = InputSchemaParser.extractParameterNames(schema)
                result shouldBe emptyList()
            }
        }
    }

    /**
     * Property 3d — Non-object schema yields empty list.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-3")
    fun `non-object schema yields empty list`() {
        val cases: List<JsonElement> = listOf(
            JsonPrimitive("string"),
            JsonPrimitive(42),
            JsonPrimitive(true),
            JsonNull,
            JsonArray(listOf(JsonPrimitive("a")))
        )
        cases.forEach { schema ->
            val result = InputSchemaParser.extractParameterNames(schema)
            result shouldBe emptyList()
        }
    }

    /**
     * Property 3e — Result count always matches properties count.
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-3")
    fun `result count always matches properties count`() {
        runBlocking {
            checkAll(cfg, arbPropertyNames) { names ->
                val schema = buildSchema(names)
                val result = InputSchemaParser.extractParameterNames(schema)
                result.size shouldBe names.size
            }
        }
    }
}
