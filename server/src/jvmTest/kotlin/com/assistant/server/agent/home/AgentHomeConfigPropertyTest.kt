package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.config.JsonConfig
import com.assistant.server.agent.generators.agentHomeConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for AgentHomeConfig (Property 23).
 */
@OptIn(ExperimentalKotest::class)
class AgentHomeConfigPropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 23: AgentHomeConfig serialization round-trip.
     *
     * For any valid AgentHomeConfig with arbitrary agentType, model,
     * maxTokens, apiEndpoint, activeSkills, activeRules, cliCommand,
     * cliArgs, and environment, serializing to JSON then deserializing
     * back produces an equivalent AgentHomeConfig.
     *
     * **Validates: Requirements 14.5**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-23")
    fun `AgentHomeConfig serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.agentHomeConfig()) { original ->
                val serialized = json.encodeToString(
                    AgentHomeConfig.serializer(), original
                )
                val restored = json.decodeFromString(
                    AgentHomeConfig.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }
}
