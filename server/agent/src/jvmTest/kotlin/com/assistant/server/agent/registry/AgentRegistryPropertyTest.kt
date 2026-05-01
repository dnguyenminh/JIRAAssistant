package com.assistant.server.agent.registry

import com.assistant.agent.GenericAgent
import com.assistant.agent.config.AgentConfig
import com.assistant.agent.models.AgentInput
import com.assistant.agent.models.AgentOutput
import com.assistant.agent.models.AgentState
import com.assistant.agent.registry.AgentNotFoundException
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Property-based tests for AgentRegistryImpl (Properties 21, 22).
 */
@OptIn(ExperimentalKotest::class)
class AgentRegistryPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 21: AgentRegistry registration and retrieval.
     *
     * For any set of registered types, getAgent returns a
     * GenericAgent, listAgentTypes returns all registered names.
     *
     * **Validates: Requirements 12.1, 12.4, 12.5**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-21")
    fun `getAgent returns agent for each registered type`() {
        runBlocking {
            checkAll(cfg, arbAgentTypeSet()) { types ->
                val registry = AgentRegistryImpl()
                types.forEach { type ->
                    registry.register(type) { stubAgent(type) }
                }
                val unique = types.toSet()
                registry.listAgentTypes() shouldContainExactlyInAnyOrder
                    unique.toList()
                for (type in unique) {
                    val agent = registry.getAgent(
                        type, AgentConfig()
                    )
                    agent.getAgentType() shouldBe type
                }
            }
        }
    }

    /**
     * Property 22: AgentRegistry unknown type exception.
     *
     * For any unregistered type, getAgent throws
     * AgentNotFoundException with available types.
     *
     * **Validates: Requirements 12.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-22")
    fun `getAgent throws for unregistered type`() {
        runBlocking {
            checkAll(
                cfg,
                arbAgentTypeSet(),
                arbAgentTypeName()
            ) { registered, unknown ->
                if (unknown !in registered) {
                    val registry = AgentRegistryImpl()
                    registered.forEach { type ->
                        registry.register(type) { stubAgent(type) }
                    }
                    val ex = assertThrows<AgentNotFoundException> {
                        registry.getAgent(unknown, AgentConfig())
                    }
                    ex.requestedType shouldBe unknown
                    ex.message shouldContain unknown
                }
            }
        }
    }
}

// ── Generators ──────────────────────────────────────────────────────

private val safeName =
    Arb.string(1..15, Codepoint.alphanumeric())

fun arbAgentTypeName(): Arb<String> = safeName

fun arbAgentTypeSet(): Arb<List<String>> = arbitrary {
    val count = Arb.int(1..6).bind()
    (1..count).map { safeName.bind() }
}

// ── Stub agent ──────────────────────────────────────────────────────

private fun stubAgent(type: String) = object : GenericAgent {
    override fun getAgentId() = "stub-$type"
    override fun getAgentType() = type
    override fun getState() = AgentState(
        agentId = "stub-$type", agentType = type
    )
    override suspend fun onStart(input: AgentInput) {}
    override suspend fun execute(input: AgentInput) =
        AgentOutput(
            requestId = "", agentType = type, result = ""
        )
    override suspend fun onComplete(output: AgentOutput) {}
}
