package com.assistant.server.agent.engine

import com.assistant.agent.engine.PhaseDefinition
import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.memory.SlotType
import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.models.ErrorStrategy
import com.assistant.server.agent.progress.NoOpProgressReporter
import com.assistant.server.agent.tool.ToolRegistryImpl
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for ThinkingLoopEngineImpl (Properties 13, 14).
 */
@OptIn(ExperimentalKotest::class)
class ThinkingLoopEnginePropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 13: Phase execution order.
     *
     * For any PhaseConfig with all entry conditions true,
     * phases execute in declared order and the reasoning log
     * reflects this order.
     *
     * **Validates: Requirements 4.1, 4.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-13")
    fun `phases execute in declared order`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..6)) { phaseCount ->
                val executionOrder = mutableListOf<String>()
                val phases = buildOrderedPhases(
                    phaseCount, executionOrder
                )
                val engine = ThinkingLoopEngineImpl()
                val result = engine.execute(
                    phases, emptyMemory(),
                    ToolRegistryImpl(), NoOpProgressReporter()
                )
                executionOrder shouldBe phases.map { it.name }
                result.phasesExecuted shouldBe phaseCount
            }
        }
    }

    /**
     * Property 14: Progress reporting.
     *
     * For N phases, reportPhase() is called at least N times.
     *
     * **Validates: Requirements 4.5, 8.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-14")
    fun `reportPhase called at least N times for N phases`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..6)) { phaseCount ->
                val reporter = CountingReporter()
                val phases = buildSimplePhases(phaseCount)
                val engine = ThinkingLoopEngineImpl()
                engine.execute(
                    phases, emptyMemory(),
                    ToolRegistryImpl(), reporter
                )
                reporter.phaseCount shouldBeGreaterThanOrEqual
                    phaseCount
            }
        }
    }

    private fun buildOrderedPhases(
        count: Int,
        tracker: MutableList<String>
    ): List<PhaseDefinition> = (1..count).map { i ->
        val name = "phase_$i"
        PhaseDefinition(
            name = name,
            entryCondition = { true },
            phaseAction = { _, _ -> tracker.add(name) },
            exitCondition = { true },
            errorStrategy = ErrorStrategy.SKIP
        )
    }

    private fun buildSimplePhases(
        count: Int
    ): List<PhaseDefinition> = (1..count).map { i ->
        PhaseDefinition(
            name = "phase_$i",
            entryCondition = { true },
            phaseAction = { _, _ -> },
            exitCondition = { true },
            errorStrategy = ErrorStrategy.SKIP
        )
    }

    private fun emptyMemory() = StructuredMemory(
        listOf(SlotSchema("default", SlotType.LIST, 10))
    )
}
