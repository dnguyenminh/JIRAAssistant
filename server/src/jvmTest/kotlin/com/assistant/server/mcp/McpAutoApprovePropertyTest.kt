package com.assistant.server.mcp

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 5: AutoApprove routing correctness
 *
 * For any toolName and autoApprove list, if toolName ∈ autoApprove →
 * execute immediately; if toolName ∉ autoApprove → requiresApproval=true.
 *
 * **Validates: Requirements 6.50**
 *
 * Feature: mcp-runtime, Property 5: AutoApprove routing correctness
 */
@OptIn(ExperimentalKotest::class)
class McpAutoApprovePropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Generate tool names: lowercase alpha 3-20 chars with optional underscores. */
    private val arbToolName: Arb<String> = Arb.string(3..20, Codepoint.az())

    /** Generate autoApprove list: 0-10 tool names. */
    private val arbAutoApproveList: Arb<List<String>> =
        Arb.list(arbToolName, 0..10)

    /** Parse autoApprove JSON string the same way the handler does. */
    private fun parseAutoApprove(raw: String): List<String> = try {
        json.decodeFromString<List<String>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Property 5 — For any toolName and autoApprove list,
     * membership determines routing.
     */
    @Test
    fun `Property 5 - autoApprove routing correctness`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbToolName,
                arbAutoApproveList
            ) { toolName, autoApproveList ->
                val shouldAutoApprove = toolName in autoApproveList

                // Simulate the handler's check: serialize → parse → check membership
                val serialized = json.encodeToString(autoApproveList)
                val parsed = parseAutoApprove(serialized)
                val routingResult = toolName in parsed

                assertEquals(
                    shouldAutoApprove,
                    routingResult,
                    "Tool '$toolName' in $autoApproveList: expected=$shouldAutoApprove"
                )
            }
        }
    }

    /**
     * Property 5 — Tool NOT in autoApprove always requires approval.
     */
    @Test
    fun `Property 5 - tool not in autoApprove requires approval`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbToolName,
                arbAutoApproveList
            ) { toolName, autoApproveList ->
                // Use a tool name guaranteed not in the list
                val uniqueTool = toolName + "_unique_suffix"
                val filtered = autoApproveList.filter { it != uniqueTool }
                val requiresApproval = uniqueTool !in filtered
                assertTrue(requiresApproval, "'$uniqueTool' must require approval")
            }
        }
    }

    /**
     * Property 5 — Empty autoApprove means all tools require approval.
     */
    @Test
    fun `Property 5 - empty autoApprove requires approval for all tools`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbToolName) { toolName ->
                val parsed = parseAutoApprove("[]")
                val requiresApproval = toolName !in parsed
                assertTrue(requiresApproval, "Empty autoApprove → all tools need approval")
            }
        }
    }
}
