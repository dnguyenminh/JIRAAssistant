package com.assistant.server.document.curation

import com.assistant.kb.KBRecord
import com.assistant.server.document.curation.models.AsIsSection
import com.assistant.server.document.curation.models.CuratedContext
import com.assistant.server.document.curation.models.ToBeSection
import com.assistant.server.document.models.EnrichedContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for feature flag routing and fallback.
 * Requirements: 8.2, 8.3, 8.6
 */
class CurationIntegrationTest {

    private val pipeline = DefaultCurationPipeline(
        temporalClassifier = DefaultTemporalClassifier(),
        commentSummarizer = DefaultCommentSummarizer(),
        attachmentCurator = DefaultAttachmentCurator(),
        budgetEnforcer = DefaultBudgetEnforcer()
    )

    @Test
    fun `curation pipeline produces valid CuratedContext`() {
        val context = buildMinimalEnrichedContext()
        val result = pipeline.curate(context)
        assertNotNull(result)
        assertEquals("TEST-1", result.rootTicket.ticketId)
        assertNotNull(result.toBeSection)
        assertNotNull(result.asIsSection)
    }

    @Test
    fun `CuratedPromptAssembler produces non-empty prompt`() {
        val context = CuratedContext(
            rootTicket = testKbRecord(),
            toBeSection = ToBeSection(
                rootRequirements = listOf("Implement feature X"),
                linkedRequirements = emptyList()
            ),
            asIsSection = AsIsSection(emptyList())
        )
        val prompt = CuratedPromptAssembler.buildPrompt(context, "BRD")
        assertTrue(prompt.isNotBlank())
        assertTrue(prompt.contains("TO-BE REQUIREMENTS"))
        assertTrue(prompt.contains("Implement feature X"))
    }

    @Test
    fun `CuratedPromptAssembler includes MCP tool block when provided`() {
        val context = CuratedContext(
            rootTicket = testKbRecord(),
            toBeSection = ToBeSection(listOf("req"), emptyList()),
            asIsSection = AsIsSection(emptyList())
        )
        val toolBlock = "=== TOOLS ===\nkb_search available"
        val prompt = CuratedPromptAssembler.buildPrompt(context, "BRD", toolBlock)
        assertTrue(prompt.contains("kb_search available"))
    }

    @Test
    fun `pipeline handles empty linked tickets gracefully`() {
        val context = EnrichedContext(
            mainTicket = testKbRecord(),
            linkedTicketAnalyses = emptyList(),
            allTickets = emptyList(),
            rawComments = emptyMap(),
            allAttachmentChunks = emptyList()
        )
        val result = pipeline.curate(context)
        assertTrue(result.toBeSection.linkedRequirements.isEmpty())
        assertTrue(result.asIsSection.existingFunctionality.isEmpty())
    }

    private fun testKbRecord() = KBRecord(
        ticketId = "TEST-1",
        requirementSummary = "Test requirement",
        evolutionHistory = emptyList(),
        scrumPoints = 5.0,
        confidenceScore = 0.9,
        rationale = "Test rationale",
        similarTicketRefs = emptyList(),
        timestamp = "2025-01-01T00:00:00Z",
        businessSummary = "Test business summary",
        extractedRequirements = listOf("Req 1", "Req 2")
    )

    private fun buildMinimalEnrichedContext() = EnrichedContext(
        mainTicket = testKbRecord(),
        linkedTicketAnalyses = emptyList(),
        allTickets = emptyList(),
        rawComments = emptyMap(),
        allAttachmentChunks = emptyList()
    )
}
