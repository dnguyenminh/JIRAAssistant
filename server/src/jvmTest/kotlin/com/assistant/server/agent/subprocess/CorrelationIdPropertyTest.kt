package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.server.agent.engine.ParallelToolExecutor
import com.assistant.server.agent.tool.ToolRegistryImpl
import com.assistant.server.agent.tool.successTool
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
 * Property-based tests for parallel tool call proxying with
 * correlation ID matching (Property 38).
 */
@OptIn(ExperimentalKotest::class)
class CorrelationIdPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 38: Parallel tool call proxying with correlation
     * ID matching.
     *
     * For any set of N concurrent ToolCallRequest messages, the
     * Orchestrator returns exactly N ToolCallResponse messages,
     * and each response's id matches exactly one request's id —
     * establishing a bijection between requests and responses.
     *
     * **Validates: Requirements 20.5**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-38")
    fun `batch responses match requests by correlation ID`() {
        runBlocking {
            checkAll(cfg, arbToolCallRequests()) { requests ->
                val registry = ToolRegistryImpl(maxCalls = 500)
                registerToolsForRequests(registry, requests)
                val executor = ParallelToolExecutor(registry)
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry,
                    parallelToolExecutor = executor
                )
                val responses = proxy.handleBatchRequests(requests)
                responses.size shouldBe requests.size
                assertBijection(requests, responses)
            }
        }
    }

    /**
     * Property 38 (continued): Even when requests share the same
     * tool name, each response carries the correct correlation ID.
     *
     * **Validates: Requirements 20.5**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-38")
    fun `duplicate tool names still get unique correlation IDs`() {
        runBlocking {
            checkAll(cfg, arbDuplicateToolRequests()) { requests ->
                val registry = ToolRegistryImpl(maxCalls = 500)
                registry.register(successTool("shared_tool"))
                val executor = ParallelToolExecutor(registry)
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry,
                    parallelToolExecutor = executor
                )
                val responses = proxy.handleBatchRequests(requests)
                responses.size shouldBe requests.size
                val responseIds = responses.map { it.id }
                val requestIds = requests.map { it.id }
                responseIds shouldBe requestIds
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun registerToolsForRequests(
        registry: ToolRegistryImpl,
        requests: List<ToolCallRequest>
    ) {
        requests.map { it.name }.toSet().forEach { name ->
            registry.register(successTool(name))
        }
    }

    private fun assertBijection(
        requests: List<ToolCallRequest>,
        responses: List<com.assistant.agent.subprocess.ToolCallResponse>
    ) {
        val requestIds = requests.map { it.id }.toSet()
        val responseIds = responses.map { it.id }.toSet()
        responseIds shouldBe requestIds
        // Order preserved: response[i].id == request[i].id
        requests.zip(responses).forEach { (req, resp) ->
            resp.id shouldBe req.id
        }
    }
}

// ── Arb generators ──────────────────────────────────────────────

private val safeStr = Arb.string(1..12, Codepoint.alphanumeric())

private fun arbToolCallRequests(): Arb<List<ToolCallRequest>> =
    arbitrary {
        val count = Arb.int(1..8).bind()
        val usedIds = mutableSetOf<String>()
        (1..count).map {
            var id = safeStr.bind()
            while (id in usedIds) id = safeStr.bind()
            usedIds.add(id)
            ToolCallRequest(
                id = id,
                name = safeStr.bind(),
                arguments = Arb.map(safeStr, safeStr, 0, 2).bind()
            )
        }
    }

private fun arbDuplicateToolRequests(): Arb<List<ToolCallRequest>> =
    arbitrary {
        val count = Arb.int(2..6).bind()
        val usedIds = mutableSetOf<String>()
        (1..count).map {
            var id = safeStr.bind()
            while (id in usedIds) id = safeStr.bind()
            usedIds.add(id)
            ToolCallRequest(
                id = id,
                name = "shared_tool",
                arguments = emptyMap()
            )
        }
    }
