package com.assistant.server.agent.models

import com.assistant.agent.models.AgentInput
import com.assistant.config.JsonConfig
import com.assistant.server.agent.generators.agentInput
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for AgentInput (Property 1).
 */
@OptIn(ExperimentalKotest::class)
class AgentInputPropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 1: AgentInput serialization round-trip.
     *
     * For any valid AgentInput with arbitrary requestId, agentType,
     * payload map, and AgentConfig, serializing to JSON then
     * deserializing back produces an equivalent AgentInput.
     *
     * **Validates: Requirements 1.4, 1.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-1")
    fun `AgentInput serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.agentInput()) { original ->
                val serialized = json.encodeToString(
                    AgentInput.serializer(), original
                )
                val restored = json.decodeFromString(
                    AgentInput.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }
}
