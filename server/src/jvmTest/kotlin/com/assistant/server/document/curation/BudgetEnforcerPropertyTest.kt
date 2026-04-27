package com.assistant.server.document.curation

import com.assistant.kb.KBRecord
import com.assistant.server.document.curation.generators.CurationArbitraries
import com.assistant.server.document.curation.models.*
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property 9: Budget Enforcement Invariant
 * Property 10: Progressive Truncation Correctness
 * Validates: Requirements 6.1, 6.3, 6.5
 */
class BudgetEnforcerPropertyTest {

    private val enforcer = DefaultBudgetEnforcer()

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 9 - final size never exceeds maxChars`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbKBRecord()
        ) { kb ->
            val context = buildLargeContext(kb)
            val result = enforcer.enforce(context, CurationConfig.MAX_PROMPT_CHARS)
            assertTrue(result.finalSize <= CurationConfig.MAX_PROMPT_CHARS,
                "finalSize=${result.finalSize} exceeds ${CurationConfig.MAX_PROMPT_CHARS}")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 10 - truncation annotation appended when truncation occurs`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbKBRecord()
        ) { kb ->
            val context = buildLargeContext(kb)
            val result = enforcer.enforce(context, 1000)
            if (result.truncationApplied) {
                assertNotNull(result.truncationAnnotation)
                assertTrue(result.truncationAnnotation!!.contains("Truncation applied"))
            }
        }
    }

    @Test
    fun `no truncation when context fits within budget`() {
        val smallContext = CuratedContext(
            rootTicket = smallKbRecord(),
            toBeSection = ToBeSection(listOf("req1"), emptyList()),
            asIsSection = AsIsSection(emptyList())
        )
        val result = enforcer.enforce(smallContext, CurationConfig.MAX_PROMPT_CHARS)
        assertFalse(result.truncationApplied)
        assertNull(result.truncationAnnotation)
    }

    private fun buildLargeContext(kb: KBRecord): CuratedContext {
        val largeReqs = List(50) { "Requirement $it: " + "x".repeat(500) }
        val largeAsIs = List(20) { idx ->
            ClassifiedTicketData(
                ticketId = "TICK-$idx",
                classification = ContentClassification.AS_IS,
                businessSummary = "Summary $idx " + "y".repeat(300),
                asIsState = "State " + "z".repeat(500),
                toBeState = "",
                extractedRequirements = List(5) { r -> "Req $r " + "w".repeat(100) }
            )
        }
        return CuratedContext(
            rootTicket = kb,
            toBeSection = ToBeSection(largeReqs, emptyList()),
            asIsSection = AsIsSection(largeAsIs),
            attachments = List(10) { idx ->
                CuratedAttachment("file$idx.pdf", "TICK-$idx", "a".repeat(3000), idx, false)
            },
            commentSummaries = (0..10).associate { idx ->
                "TICK-$idx" to CommentSummary(
                    decisions = List(3) { d -> "Decision $d" },
                    totalChars = 500
                )
            }
        )
    }

    private fun smallKbRecord() = KBRecord(
        ticketId = "TEST-1",
        requirementSummary = "Small test",
        evolutionHistory = emptyList(),
        scrumPoints = 3.0,
        confidenceScore = 0.8,
        rationale = "Test",
        similarTicketRefs = emptyList(),
        timestamp = "2025-01-01T00:00:00Z"
    )
}
