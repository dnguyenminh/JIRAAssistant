package com.assistant.server.agent.tool

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
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
 * Property-based tests for ToolRegistryImpl (Properties 10, 11, 12).
 */
@OptIn(ExperimentalKotest::class)
class ToolRegistryPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 10: ToolRegistry registration and listing.
     *
     * For any set of AgentTool instances (including duplicate names),
     * listTools() returns exactly one ToolDescriptor per unique name,
     * reflecting the most recently registered tool.
     *
     * **Validates: Requirements 3.1, 3.7, 3.8**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-10")
    fun `listTools returns one descriptor per unique name`() {
        runBlocking {
            checkAll(cfg, arbToolList()) { tools ->
                val registry = ToolRegistryImpl()
                tools.forEach { registry.register(it) }
                val listed = registry.listTools()
                val uniqueNames = tools.map { it.name }.toSet()
                listed.size shouldBe uniqueNames.size
                assertLastRegistrationWins(listed, tools)
            }
        }
    }

    /**
     * Property 11: ToolRegistry invoke never throws.
     *
     * For any invocation — success, exception, timeout, or
     * invalid params — invoke() returns a ToolResult and
     * never propagates exceptions.
     *
     * **Validates: Requirements 3.2, 3.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-11")
    fun `invoke never throws for any tool behavior`() {
        runBlocking {
            checkAll(cfg, arbToolBehavior()) { behavior ->
                val registry = ToolRegistryImpl(timeoutMs = 500)
                registry.register(behavior.tool)
                val result = registry.invoke(
                    behavior.tool.name, emptyMap()
                )
                (result is ToolResult) shouldBe true
            }
        }
    }

    /**
     * Property 12: ToolRegistry rate limiting.
     *
     * After exactly N successful invocations, the (N+1)th
     * returns RATE_LIMIT_EXCEEDED.
     *
     * **Validates: Requirements 3.4**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-12")
    fun `rate limit triggers after maxCalls invocations`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..10)) { maxCalls ->
                val registry = ToolRegistryImpl(maxCalls = maxCalls)
                registry.register(successTool("t"))
                repeat(maxCalls) {
                    val r = registry.invoke("t", emptyMap())
                    r.success shouldBe true
                }
                val overflow = registry.invoke("t", emptyMap())
                overflow.success shouldBe false
                overflow.errorType shouldBe "RATE_LIMIT_EXCEEDED"
            }
        }
    }

    private fun assertLastRegistrationWins(
        listed: List<ToolDescriptor>,
        tools: List<AgentTool>
    ) {
        val lastByName = tools.associateBy { it.name }
        for (desc in listed) {
            val expected = lastByName[desc.name]!!
            desc.description shouldBe expected.description
        }
    }
}
