package com.assistant.document

import com.assistant.ai.deepanalysis.models.*
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.document.models.SprintMetadata
import com.assistant.kb.KBRecord
import kotlin.test.*

/**
 * Unit tests for FsdPromptBuilder.
 * Validates prompt contains all 10 section headings, anti-hallucination instructions,
 * source citation format, FSD-specific technical expansion, and 4 diagram instructions.
 *
 * Requirements: 3.1–3.9, 9.1–9.3, 10.2
 */
class FsdPromptBuilderTest {

    private fun createTestContext(): GenerationContext {
        val mainTicket = KBRecord(
            ticketId = "NET-200",
            requirementSummary = "FSD summary",
            evolutionHistory = emptyList(),
            scrumPoints = 8.0,
            confidenceScore = 0.9,
            rationale = "Technical feature",
            similarTicketRefs = emptyList(),
            timestamp = "2024-02-01T00:00:00Z",
            businessSummary = "Implement payment gateway",
            extractedRequirements = listOf("REST API for payments", "Webhook integration"),
            technicalDetails = TechnicalDetails(
                apiSpecifications = listOf(
                    ApiSpecification("POST", "/api/payments", "Create payment")
                ),
                databaseChanges = listOf(
                    DatabaseChange("payments", "CREATE", listOf("id", "amount"), "Payment table")
                ),
                externalIntegrations = listOf(
                    ExternalIntegration("Stripe", "HTTPS", "api.stripe.com", "Payment provider")
                )
            ),
            dependencies = DependencyInfo(
                blockingIssues = listOf(
                    DependencyItem("NET-150", "Auth service", "blocks", "MEDIUM")
                )
            ),
            acceptanceCriteria = listOf(
                AcceptanceCriterion("AC1", "Payment succeeds", "automated")
            )
        )
        return GenerationContext(
            mainTicket = mainTicket,
            linkedTicketAnalyses = listOf(
                mainTicket.copy(ticketId = "NET-201", businessSummary = "Linked FSD task")
            ),
            attachmentChunks = listOf(
                AttachmentChunkInfo("api-spec.pdf", "Payment API details", 0.92f)
            ),
            sprintMetadata = SprintMetadata("Sprint 12", "2024-02-01", "2024-02-14")
        )
    }

    @Test
    fun promptContainsAll10SectionHeadings() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        FsdPromptBuilder.FSD_SECTIONS.forEach { section ->
            assertTrue(prompt.contains(section), "Missing section: $section")
        }
    }

    @Test
    fun promptContainsAntiHallucinationInstructions() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Do NOT fabricate"), "Missing anti-hallucination")
        assertTrue(prompt.contains("ONLY data from CONTEXT"), "Missing context-only instruction")
    }

    @Test
    fun promptContainsSourceCitationFormat() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("[Source:"), "Missing source citation format")
    }

    @Test
    fun promptIncludesTechnicalArchitectRole() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("senior Technical Architect"), "Missing FSD role")
    }

    @Test
    fun promptExpandsApiSpecifications() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("POST /api/payments"), "Missing API spec expansion")
        assertTrue(prompt.contains("Create payment"), "Missing API description")
    }

    @Test
    fun promptExpandsDatabaseChanges() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("payments"), "Missing DB table name")
        assertTrue(prompt.contains("Payment table"), "Missing DB description")
    }

    @Test
    fun promptExpandsExternalIntegrations() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Stripe"), "Missing integration service")
        assertTrue(prompt.contains("api.stripe.com"), "Missing integration endpoint")
    }

    @Test
    fun promptRequestsFourDrawioDiagrams() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Context/Interface Diagram"), "Missing diagram 1")
        assertTrue(prompt.contains("Data Flow Diagram"), "Missing diagram 2")
        assertTrue(prompt.contains("Integration Architecture"), "Missing diagram 3")
        assertTrue(prompt.contains("Data Migration Flow"), "Missing diagram 4")
    }

    @Test
    fun promptRequestsRawXmlFormat() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("<mxGraphModel>"), "Missing mxGraphModel XML example")
        assertTrue(prompt.contains("```xml"), "Missing xml code block instruction")
        assertFalse(
            prompt.contains("Generate draw.io diagrams as JSON metadata"),
            "Should NOT instruct AI to generate JSON metadata"
        )
    }

    @Test
    fun promptIncludesBusinessSummary() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("Implement payment gateway"), "Missing businessSummary")
    }

    @Test
    fun promptIncludesLinkedTickets() {
        val prompt = FsdPromptBuilder.buildPrompt(createTestContext())
        assertTrue(prompt.contains("NET-201"), "Missing linked ticket")
    }
}
