package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.toOllamaTool
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 5: ToolDescriptor to OllamaTool conversion

/**
 * Property 5: ToolDescriptor to OllamaTool conversion with type mapping
 *
 * For any valid ToolDescriptor with N parameter names, toOllamaTool()
 * SHALL produce an OllamaTool where:
 * (a) function.name equals the descriptor's name
 * (b) function.description equals the descriptor's description
 * (c) function.parameters.properties has exactly N entries
 * (d) each property type is "string" (since ToolDescriptor has no type info)
 *
 * **Validates: Requirements 8.1, 8.2, 8.3**
 */
class OllamaToolConverterPropertyTest {

    @Test
    fun `Property 5 - toOllamaTool preserves name and description`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolDescriptor()
            ) { descriptor ->
                val tool = descriptor.toOllamaTool()

                assertEquals(
                    descriptor.name, tool.function.name,
                    "function.name must match descriptor name"
                )
                assertEquals(
                    descriptor.description, tool.function.description,
                    "function.description must match descriptor description"
                )
            }
        }
    }

    @Test
    fun `Property 5 - properties count matches parameter count`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolDescriptor()
            ) { descriptor ->
                val tool = descriptor.toOllamaTool()
                val props = tool.function.parameters.properties

                assertEquals(
                    descriptor.parameterNames.size, props.size,
                    "properties count must match parameter count"
                )
            }
        }
    }

    @Test
    fun `Property 5 - all property types are string`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolDescriptor()
            ) { descriptor ->
                val tool = descriptor.toOllamaTool()
                val props = tool.function.parameters.properties

                for ((paramName, prop) in props) {
                    assertEquals(
                        "string", prop.type,
                        "Property '$paramName' type must be string"
                    )
                }
            }
        }
    }

    @Test
    fun `Property 5 - all parameters are required`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolDescriptor()
            ) { descriptor ->
                val tool = descriptor.toOllamaTool()
                val required = tool.function.parameters.required

                assertEquals(
                    descriptor.parameterNames.toSet(), required.toSet(),
                    "required list must match parameter names"
                )
            }
        }
    }

    @Test
    fun `Property 5 - tool type is function`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolDescriptor()
            ) { descriptor ->
                val tool = descriptor.toOllamaTool()
                assertEquals("function", tool.type)
                assertEquals("object", tool.function.parameters.type)
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbToolDescriptor(): Arb<ToolDescriptor> = arbitrary {
        val name = Arb.string(1..15, Codepoint.alphanumeric()).bind()
        val desc = Arb.string(1..30, Codepoint.alphanumeric()).bind()
        val paramCount = Arb.int(0..10).bind()
        // Generate extra names to ensure enough distinct values after dedup
        val rawParams = (0 until paramCount + 5).map {
            Arb.string(1..10, Codepoint.alphanumeric()).bind()
        }
        val params = rawParams.distinct().take(paramCount)
        ToolDescriptor(name = name, description = desc, parameterNames = params)
    }
}
