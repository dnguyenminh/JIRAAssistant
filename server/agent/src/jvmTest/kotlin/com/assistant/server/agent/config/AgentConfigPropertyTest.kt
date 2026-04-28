package com.assistant.server.agent.config

import com.assistant.agent.config.AgentConfig
import com.assistant.agent.config.InvalidAgentConfigException
import com.assistant.agent.config.agentConfig
import com.assistant.agent.memory.SlotType
import com.assistant.config.JsonConfig
import com.assistant.server.agent.generators.agentConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for AgentConfig (Properties 5, 20).
 */
@OptIn(ExperimentalKotest::class)
class AgentConfigPropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 5: AgentConfig serialization round-trip.
     *
     * For any valid AgentConfig, serializing to JSON then
     * deserializing back produces an equivalent AgentConfig.
     *
     * **Validates: Requirements 10.4, 10.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-5")
    fun `AgentConfig serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.agentConfig()) { original ->
                val serialized = json.encodeToString(
                    AgentConfig.serializer(), original
                )
                val restored = json.decodeFromString(
                    AgentConfig.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }

    /**
     * Property 20: AgentConfig validation rejects invalid configs.
     *
     * Duplicate phase names and duplicate slot names throw
     * InvalidAgentConfigException.
     *
     * **Validates: Requirements 10.2, 10.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-20")
    fun `duplicate phase names are rejected`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.string(1..15, Codepoint.alphanumeric())
            ) { name ->
                val ex = shouldThrow<InvalidAgentConfigException> {
                    agentConfig {
                        phases {
                            phase(name)
                            phase(name)
                        }
                    }
                }
                ex.errors.any {
                    it.contains("Duplicate phase")
                } shouldBe true
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-20")
    fun `duplicate slot names are rejected`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.string(1..15, Codepoint.alphanumeric())
            ) { name ->
                val ex = shouldThrow<InvalidAgentConfigException> {
                    agentConfig {
                        memorySchema {
                            stringSlot(name, maxChars = 100)
                            listSlot(name, maxEntries = 10)
                        }
                    }
                }
                ex.errors.any {
                    it.contains("Duplicate slot")
                } shouldBe true
            }
        }
    }
}
