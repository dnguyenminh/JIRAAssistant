package com.assistant.server.agent.ba.state

import com.assistant.agent.models.AgentState
import com.assistant.agent.models.AgentStateStatus
import com.assistant.server.agent.ba.BADocumentAgent
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 11: BA Agent reasoning log cap.
 *
 * For any execution producing > 50 reasoning log entries,
 * AgentState.reasoningLog SHALL contain exactly 50 entries
 * (most recent). Validates: Requirements 7.6
 */
@OptIn(ExperimentalKotest::class)
class BAAgentStatePropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    @Test
    @Tag("agent-document-generation")
    @Tag("Property-11")
    fun `reasoning log capped at 50 most recent entries`() {
        runBlocking {
            checkAll(cfg, Arb.int(51..200)) { logSize ->
                val entries = (1..logSize).map { "entry-$it" }
                val state = buildState(entries)
                val capped = BADocumentAgent.capReasoningLog(state)
                capped.reasoningLog.size shouldBeExactly 50
                capped.reasoningLog shouldBe entries.takeLast(50)
            }
        }
    }

    @Test
    @Tag("agent-document-generation")
    @Tag("Property-11")
    fun `reasoning log at or below 50 entries is unchanged`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..50)) { logSize ->
                val entries = (1..logSize).map { "entry-$it" }
                val state = buildState(entries)
                val capped = BADocumentAgent.capReasoningLog(state)
                capped.reasoningLog.size shouldBeExactly logSize
                capped.reasoningLog shouldBe entries
            }
        }
    }

    private fun buildState(entries: List<String>) = AgentState(
        agentId = "test-agent",
        agentType = "ba-document",
        reasoningLog = entries,
        status = AgentStateStatus.RUNNING
    )
}
