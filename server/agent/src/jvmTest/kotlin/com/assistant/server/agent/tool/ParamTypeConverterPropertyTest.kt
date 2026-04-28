package com.assistant.server.agent.tool

import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 9: Schema-aware parameter type conversion
 *
 * For any parameter where the inputSchema specifies type "integer",
 * "number", or "boolean", and the string value is a valid representation,
 * ParamTypeConverter.convert() SHALL produce the corresponding typed
 * JsonPrimitive. For invalid conversions, the original string value
 * SHALL be preserved as a JsonPrimitive string.
 *
 * **Validates: Requirements 8.2, 8.3, 8.4**
 *
 * Feature: agent-mcp-tool-bridge, Property 9: Schema-aware parameter type conversion
 */
@OptIn(ExperimentalKotest::class)
class ParamTypeConverterPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    private val arbParamName: Arb<String> =
        Arb.string(1..15, Codepoint.az()).map { "p_$it" }

    private fun buildSchema(props: Map<String, String>): JsonElement {
        val properties = JsonObject(props.mapValues { (_, type) ->
            JsonObject(mapOf("type" to JsonPrimitive(type)))
        })
        return JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to properties
        ))
    }

    // ── Property 9a: valid integers convert to number ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `valid integer strings convert to JsonPrimitive number`() {
        runBlocking {
            checkAll(cfg, arbParamName, Arb.long(-999999L..999999L)) { name, num ->
                val schema = buildSchema(mapOf(name to "integer"))
                val result = ParamTypeConverter.convert(
                    mapOf(name to num.toString()), schema
                )
                val prim = result[name]!!.jsonPrimitive
                prim.longOrNull shouldBe num
            }
        }
    }

    // ── Property 9b: valid number strings convert to number ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `valid number strings convert to JsonPrimitive number`() {
        runBlocking {
            checkAll(cfg, arbParamName, Arb.double(-1e6..1e6)) { name, num ->
                if (!num.isNaN() && !num.isInfinite()) {
                    val schema = buildSchema(mapOf(name to "number"))
                    val result = ParamTypeConverter.convert(
                        mapOf(name to num.toString()), schema
                    )
                    val prim = result[name]!!.jsonPrimitive
                    (prim.longOrNull != null || prim.doubleOrNull != null) shouldBe true
                }
            }
        }
    }

    // ── Property 9c: valid boolean strings convert to boolean ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `valid boolean strings convert to JsonPrimitive boolean`() {
        val boolStrings = listOf("true", "false", "True", "False", "TRUE", "FALSE")
        runBlocking {
            checkAll(cfg, arbParamName, Arb.element(boolStrings)) { name, boolStr ->
                val schema = buildSchema(mapOf(name to "boolean"))
                val result = ParamTypeConverter.convert(
                    mapOf(name to boolStr), schema
                )
                val prim = result[name]!!.jsonPrimitive
                prim.booleanOrNull shouldBe boolStr.lowercase().toBooleanStrict()
            }
        }
    }

    // ── Property 9d: invalid conversions fall back to string ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `invalid numeric strings fall back to string primitive`() {
        val invalidNums = listOf("abc", "12.34.56", "", "not_a_number", "true")
        runBlocking {
            checkAll(cfg, arbParamName, Arb.element(invalidNums)) { name, bad ->
                val schema = buildSchema(mapOf(name to "integer"))
                val result = ParamTypeConverter.convert(
                    mapOf(name to bad), schema
                )
                val prim = result[name]!!.jsonPrimitive
                prim.isString shouldBe true
                prim.content shouldBe bad
            }
        }
    }

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `invalid boolean strings fall back to string primitive`() {
        val invalidBools = listOf("yes", "no", "1", "0", "maybe", "")
        runBlocking {
            checkAll(cfg, arbParamName, Arb.element(invalidBools)) { name, bad ->
                val schema = buildSchema(mapOf(name to "boolean"))
                val result = ParamTypeConverter.convert(
                    mapOf(name to bad), schema
                )
                val prim = result[name]!!.jsonPrimitive
                prim.isString shouldBe true
                prim.content shouldBe bad
            }
        }
    }

    // ── Property 9e: missing schema keeps all as strings ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `missing schema keeps all values as strings`() {
        runBlocking {
            checkAll(cfg, arbParamName, Arb.string(0..20)) { name, value ->
                val emptySchema: JsonElement = JsonObject(emptyMap())
                val result = ParamTypeConverter.convert(
                    mapOf(name to value), emptySchema
                )
                val prim = result[name]!!.jsonPrimitive
                prim.isString shouldBe true
                prim.content shouldBe value
            }
        }
    }

    // ── Property 9f: string type keeps value as string ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `string schema type keeps value as string`() {
        runBlocking {
            checkAll(cfg, arbParamName, Arb.string(0..20)) { name, value ->
                val schema = buildSchema(mapOf(name to "string"))
                val result = ParamTypeConverter.convert(
                    mapOf(name to value), schema
                )
                val prim = result[name]!!.jsonPrimitive
                prim.isString shouldBe true
                prim.content shouldBe value
            }
        }
    }

    // ── Property 9g: output keys match input keys ──

    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-9")
    fun `output keys always match input keys`() {
        val arbType = Arb.element("integer", "number", "boolean", "string")
        runBlocking {
            checkAll(cfg, Arb.list(arbParamName, 1..5).map { it.distinct() }, arbType) { names, type ->
                val params = names.associateWith { "42" }
                val schema = buildSchema(names.associateWith { type })
                val result = ParamTypeConverter.convert(params, schema)
                result.keys shouldBe params.keys
            }
        }
    }
}
