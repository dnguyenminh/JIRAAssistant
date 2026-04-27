package com.assistant.server.agent.ba.progress

import com.assistant.server.agent.ba.integration.BAProgressAdapter
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

private val VALID_LABELS = setOf(
    "AGGREGATING_DATA", "GENERATING_DOCUMENT"
)

private val BA_PHASE_NAMES = listOf(
    "collect", "expand", "visualize", "synthesize"
)

/**
 * Property 12: Progress phase label mapping.
 *
 * For any BA Agent phase name, mapPhaseToLabel SHALL return
 * AGGREGATING_DATA or GENERATING_DOCUMENT. No null or unmapped.
 * Validates: Requirements 9.7
 */
@OptIn(ExperimentalKotest::class)
class ProgressMappingPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    @Test
    @Tag("agent-document-generation")
    @Tag("Property-12")
    fun `known BA phases map to valid labels`() {
        runBlocking {
            checkAll(cfg, Arb.of(BA_PHASE_NAMES)) { phase ->
                val label = BAProgressAdapter.mapPhaseToLabel(phase)
                label shouldNotBe null
                VALID_LABELS shouldContain label
            }
        }
    }

    @Test
    @Tag("agent-document-generation")
    @Tag("Property-12")
    fun `unknown phases never return null`() {
        runBlocking {
            checkAll(cfg, Arb.string(1..50)) { phase ->
                val label = BAProgressAdapter.mapPhaseToLabel(phase)
                label shouldNotBe null
                VALID_LABELS shouldContain label
            }
        }
    }
}
