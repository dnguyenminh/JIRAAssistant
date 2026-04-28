package com.assistant.server.agent.models

import com.assistant.agent.models.AgentOutput
import com.assistant.config.JsonConfig
import com.assistant.server.agent.generators.agentOutput
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for AgentOutput (Property 2).
 */
@OptIn(ExperimentalKotest::class)
class AgentOutputPropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 2: AgentOutput serialization round-trip.
     *
     * For any valid AgentOutput with arbitrary fields,
     * serializing to JSON then deserializing back produces
     * an equivalent AgentOutput.
     *
     * **Validates: Requirements 1.5, 1.7**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-2")
    fun `AgentOutput serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.agentOutput()) { original ->
                val serialized = json.encodeToString(
                    AgentOutput.serializer(), original
                )
                val restored = json.decodeFromString(
                    AgentOutput.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }
}
