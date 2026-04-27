package com.assistant.server.agent.engine

import com.assistant.agent.models.ToolCall
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
import com.assistant.server.agent.tool.ToolRegistryImpl
import com.assistant.server.agent.tool.successTool
import com.assistant.server.agent.tool.throwingTool
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for ParallelToolExecutor (Properties 15, 16).
 */
@OptIn(ExperimentalKotest::class)
class ParallelToolExecutorPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 15: Order preservation and count.
     *
     * For any batch of N tool calls, result has exactly N
     * ToolResults and result[i].toolName == calls[i].toolName.
     *
     * **Validates: Requirements 5.1, 5.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-15")
    fun `executeBatch preserves order and count`() {
        runBlocking {
            checkAll(cfg, arbToolCallBatch()) { calls ->
                val registry = buildRegistryForCalls(calls)
                val executor = ParallelToolExecutor(registry)
                val results = executor.executeBatch(calls)
                results.size shouldBe calls.size
                results.forEachIndexed { i, result ->
                    result.toolName shouldBe calls[i].toolName
                }
            }
        }
    }

    /**
     * Property 16: Failure isolation.
     *
     * Failing calls don't affect other calls' success.
     *
     * **Validates: Requirements 5.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-16")
    fun `failing calls do not affect sibling success`() {
        runBlocking {
            checkAll(cfg, arbMixedBatch()) { (calls, failNames) ->
                val registry = buildMixedRegistry(calls, failNames)
                val executor = ParallelToolExecutor(registry)
                val results = executor.executeBatch(calls)
                results.forEachIndexed { i, result ->
                    val name = calls[i].toolName
                    if (name !in failNames) {
                        result.success shouldBe true
                    }
                }
            }
        }
    }

    private fun buildRegistryForCalls(
        calls: List<ToolCall>
    ): ToolRegistryImpl {
        val registry = ToolRegistryImpl(maxCalls = 1000)
        calls.map { it.toolName }.toSet().forEach { name ->
            registry.register(successTool(name))
        }
        return registry
    }

    private fun buildMixedRegistry(
        calls: List<ToolCall>,
        failNames: Set<String>
    ): ToolRegistryImpl {
        val registry = ToolRegistryImpl(maxCalls = 1000)
        calls.map { it.toolName }.toSet().forEach { name ->
            val tool = if (name in failNames) throwingTool(name)
            else successTool(name)
            registry.register(tool)
        }
        return registry
    }
}
