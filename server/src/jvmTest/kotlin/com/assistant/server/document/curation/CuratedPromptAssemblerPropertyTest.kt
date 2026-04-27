package com.assistant.server.document.curation

import com.assistant.kb.KBRecord
import com.assistant.server.document.curation.generators.CurationArbitraries
import com.assistant.server.document.curation.models.*
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 6: TO-BE Before AS-IS Ordering
 * Validates: Requirements 3.5
 */
class CuratedPromptAssemblerPropertyTest {

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 6 - TO-BE section appears before AS-IS`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbKBRecord()
        ) { kb ->
            val context = CuratedContext(
                rootTicket = kb,
                toBeSection = ToBeSection(
                    rootRequirements = listOf("New requirement 1"),
                    linkedRequirements = listOf(
                        ClassifiedTicketData(
                            ticketId = "NEW-1",
                            classification = ContentClassification.TO_BE,
                            businessSummary = "New feature",
                            asIsState = "",
                            toBeState = "Implement X",
                            extractedRequirements = listOf("Req A")
                        )
                    )
                ),
                asIsSection = AsIsSection(
                    existingFunctionality = listOf(
                        ClassifiedTicketData(
                            ticketId = "OLD-1",
                            classification = ContentClassification.AS_IS,
                            businessSummary = "Existing feature",
                            asIsState = "Currently works",
                            toBeState = "",
                            extractedRequirements = emptyList()
                        )
                    )
                )
            )
            val prompt = CuratedPromptAssembler.buildPrompt(context, "BRD")
            val toBeIdx = prompt.indexOf("TO-BE REQUIREMENTS")
            val asIsIdx = prompt.indexOf("AS-IS CONTEXT")
            assertTrue(toBeIdx >= 0, "TO-BE section not found")
            assertTrue(asIsIdx >= 0, "AS-IS section not found")
            assertTrue(toBeIdx < asIsIdx,
                "TO-BE ($toBeIdx) should appear before AS-IS ($asIsIdx)")
        }
    }

    @Test
    fun `prompt contains role section`() {
        val context = CuratedContext(
            rootTicket = smallKb(),
            toBeSection = ToBeSection(listOf("req"), emptyList()),
            asIsSection = AsIsSection(emptyList())
        )
        val prompt = CuratedPromptAssembler.buildPrompt(context, "BRD")
        assertTrue(prompt.contains("ROLE"))
    }

    @Test
    fun `FSD prompt uses architect role`() {
        val context = CuratedContext(
            rootTicket = smallKb(),
            toBeSection = ToBeSection(listOf("req"), emptyList()),
            asIsSection = AsIsSection(emptyList())
        )
        val prompt = CuratedPromptAssembler.buildPrompt(context, "FSD")
        assertTrue(prompt.contains("Technical Architect"))
    }

    private fun smallKb() = KBRecord(
        ticketId = "TEST-1",
        requirementSummary = "Test",
        evolutionHistory = emptyList(),
        scrumPoints = 3.0,
        confidenceScore = 0.8,
        rationale = "Test",
        similarTicketRefs = emptyList(),
        timestamp = "2025-01-01T00:00:00Z"
    )
}
