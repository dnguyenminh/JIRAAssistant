package com.assistant.server.agent.subprocess

import com.assistant.agent.models.ToolResult
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.tool.AgentTool
import com.assistant.server.agent.tool.ToolRegistryImpl
import com.assistant.server.agent.tool.successTool
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for tool call error isolation —
 * subprocess survives failures (Property 39).
 */
@OptIn(ExperimentalKotest::class)
class ErrorIsolationPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 39: Tool call error isolation.
     *
     * For any ToolCallRequest targeting a non-existent tool or a
     * tool that fails, the Orchestrator returns a ToolCallResponse
     * with success=false and a descriptive error message, and the
     * subprocess remains alive (proxy is still usable).
     *
     * **Validates: Requirements 20.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-39")
    fun `failed tool returns error response not exception`() {
        runBlocking {
            checkAll(cfg, arbFailingRequest()) { request ->
                val registry = ToolRegistryImpl()
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val response = proxy.handleToolCallRequest(request)
                response.success shouldBe false
                response.id shouldBe request.id
                response.error.shouldNotBeEmpty()
            }
        }
    }

    /**
     * Property 39 (continued): After a failed tool call, the proxy
     * can still handle subsequent successful tool calls — the
     * subprocess is NOT killed.
     *
     * **Validates: Requirements 20.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-39")
    fun `proxy survives failure and handles next call`() {
        runBlocking {
            checkAll(cfg, arbFailThenSucceed()) { (fail, succeed) ->
                val registry = ToolRegistryImpl()
                registry.register(successTool(succeed.name))
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val failResp = proxy.handleToolCallRequest(fail)
                failResp.success shouldBe false
                val okResp = proxy.handleToolCallRequest(succeed)
                okResp.success shouldBe true
                okResp.id shouldBe succeed.id
            }
        }
    }

    /**
     * Property 39 (continued): A tool that throws an exception
     * is captured as an error response, not propagated.
     *
     * **Validates: Requirements 20.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-39")
    fun `throwing tool captured as error response`() {
        runBlocking {
            checkAll(cfg, arbThrowingRequest()) { (req, tool) ->
                val registry = ToolRegistryImpl()
                registry.register(tool)
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val response = proxy.handleToolCallRequest(req)
                response.success shouldBe false
                response.id shouldBe req.id
                response.error.shouldNotBeEmpty()
            }
        }
    }
}

// ── Arb generators ──────────────────────────────────────────────

private val safeStr = Arb.string(1..12, Codepoint.alphanumeric())

/** Request for a tool that does not exist in the registry. */
private fun arbFailingRequest(): Arb<ToolCallRequest> = arbitrary {
    ToolCallRequest(
        id = safeStr.bind(),
        name = "nonexistent_${safeStr.bind()}",
        arguments = emptyMap()
    )
}

/** Pair: a request for a missing tool, then a valid request. */
private fun arbFailThenSucceed(): Arb<Pair<ToolCallRequest, ToolCallRequest>> =
    arbitrary {
        val toolName = safeStr.bind()
        val fail = ToolCallRequest(
            id = safeStr.bind(),
            name = "missing_${safeStr.bind()}",
            arguments = emptyMap()
        )
        val succeed = ToolCallRequest(
            id = safeStr.bind(),
            name = toolName,
            arguments = emptyMap()
        )
        Pair(fail, succeed)
    }

/** Request paired with a tool that always throws. */
private fun arbThrowingRequest(): Arb<Pair<ToolCallRequest, AgentTool>> =
    arbitrary {
        val name = safeStr.bind()
        val req = ToolCallRequest(
            id = safeStr.bind(),
            name = name,
            arguments = emptyMap()
        )
        val tool = object : AgentTool {
            override val name = name
            override val description = "throws"
            override val parameterNames = emptyList<String>()
            override suspend fun execute(
                params: Map<String, String>
            ): ToolResult = throw RuntimeException("boom")
        }
        Pair(req, tool)
    }
