package com.assistant.document

import com.assistant.ai.deepanalysis.models.*
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.document.models.SprintMetadata
import com.assistant.kb.KBRecord
import kotlin.test.*

/**
 * Unit tests for BrdPromptBuilder.
 * Validates prompt contains all 18 section headings (Carleton ITS template),
 * anti-hallucination instructions, source citation format, context data,
 * and diagram instructions.
 *
 * Requirements: 2.1–2.8, 9.1–9.3, 10.1
 */
class BrdPromptBuilderTest {

    private fun createTestContext(): GenerationContext {
        val mainTicket = KBRecord(
            ticketId = "NET-100",
            requirementSummary = "Main summary",
            evolutionHistory = emptyList(),
            scrumPoints = 5.0,
            confidenceScore = 0.85,
            rationale = "Complex feature",
            similarTicketRefs = emptyList(),
            timestamp = "2024-01-01T00:00:00Z",
            businessSummary = "Implement user auth",
            extractedRequirements = listOf("Login via OAuth2", "MFA support"),
            dependencies = DependencyInfo(
                blockingIssues = listOf(
                    DependencyItem("NET-50", "DB migration", "blocks", "HIGH")
                ),
                externalDependencies = listOf("Auth0 service")
            ),
            acceptanceCriteria = listOf(
                AcceptanceCriterion("AC1", "User can login", "automated")
            )
        )
        return GenerationContext(
            mainTicket = mainTicket,
            linkedTicketAnalyses = listOf(
                mainTicket.copy(ticketId = "NET-101", businessSummary = "Linked task")
            ),
            attachmentChunks = listOf(
                AttachmentChunkInfo("spec.pdf", "Auth flow details", 0.95f)
            ),
            sprintMetadata = SprintMetadata("Sprint 10", "2024-01-01", "2024-01-14")
        )
    }

    @Test
    fun promptContainsAllSectionHeadings() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        BrdPromptBuilder.BRD_SECTIONS.forEach { section ->
            assertTrue(prompt.contains(section), "Missing section: $section")
        }
    }

    @Test
    fun promptContainsAntiHallucinationInstructions() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Do NOT fabricate"), "Missing anti-hallucination")
        assertTrue(prompt.contains("ONLY data from CONTEXT"), "Missing context-only instruction")
    }

    @Test
    fun promptContainsSourceCitationFormat() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("[Source:"), "Missing source citation format")
    }

    @Test
    fun promptIncludesBusinessSummary() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Implement user auth"), "Missing businessSummary")
    }

    @Test
    fun promptIncludesDependencyContext() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("NET-50"), "Missing blocking dependency")
        assertTrue(prompt.contains("Auth0 service"), "Missing external dependency")
    }

    @Test
    fun promptIncludesAcceptanceCriteria() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("User can login"), "Missing acceptance criteria")
    }

    @Test
    fun promptIncludesLinkedTickets() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("NET-101"), "Missing linked ticket")
    }

    @Test
    fun promptIncludesAttachmentContent() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("spec.pdf"), "Missing attachment filename")
        assertTrue(prompt.contains("Auth flow details"), "Missing attachment content")
    }

    @Test
    fun promptRequestsThreeDrawioDiagrams() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Process Flow"), "Missing diagram 1")
        assertTrue(prompt.contains("Requirements Traceability"), "Missing diagram 2")
        assertTrue(prompt.contains("Stakeholder Map"), "Missing diagram 3")
    }

    @Test
    fun promptRequestsRawXmlFormat() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("<mxGraphModel>"), "Missing mxGraphModel XML example")
        assertTrue(prompt.contains("```xml"), "Missing xml code block instruction")
        assertFalse(
            prompt.contains("Generate draw.io diagrams as JSON metadata"),
            "Should NOT instruct AI to generate JSON metadata"
        )
    }

    @Test
    fun promptIncludesSprintMetadata() {
        val prompt = BrdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Sprint 10"), "Missing sprint metadata")
    }
}
