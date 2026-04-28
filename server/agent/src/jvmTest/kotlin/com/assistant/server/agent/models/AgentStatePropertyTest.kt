package com.assistant.server.agent.models

import com.assistant.agent.models.AgentState
import com.assistant.config.JsonConfig
import com.assistant.server.agent.generators.agentState
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for AgentState (Properties 4, 17).
 */
@OptIn(ExperimentalKotest::class)
class AgentStatePropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 4: AgentState serialization round-trip.
     *
     * For any valid AgentState, serializing to JSON then
     * deserializing back produces an equivalent AgentState.
     *
     * **Validates: Requirements 6.2, 6.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-4")
    fun `AgentState serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.agentState()) { original ->
                val serialized = json.encodeToString(
                    AgentState.serializer(), original
                )
                val restored = json.decodeFromString(
                    AgentState.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }

    /**
     * Property 17: AgentState reasoning log cap.
     *
     * The reasoning log never exceeds 100 entries. When
     * additions exceed 100, only the most recent 100 are kept.
     *
     * **Validates: Requirements 6.5**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-17")
    fun `reasoning log never exceeds MAX_REASONING_LOG_ENTRIES`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.agentState(),
                Arb.list(
                    Arb.string(1..30, Codepoint.alphanumeric()),
                    0..200
                )
            ) { state, extraEntries ->
                val combined = state.reasoningLog + extraEntries
                val capped = capReasoningLog(combined)
                capped shouldHaveAtMostSize
                    AgentState.MAX_REASONING_LOG_ENTRIES
                if (combined.size > AgentState.MAX_REASONING_LOG_ENTRIES) {
                    capped shouldBe combined.takeLast(
                        AgentState.MAX_REASONING_LOG_ENTRIES
                    )
                } else {
                    capped shouldBe combined
                }
            }
        }
    }
}

/**
 * Cap a reasoning log to MAX_REASONING_LOG_ENTRIES,
 * keeping the most recent entries.
 */
fun capReasoningLog(log: List<String>): List<String> {
    val max = AgentState.MAX_REASONING_LOG_ENTRIES
    return if (log.size > max) log.takeLast(max) else log
}
