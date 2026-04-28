package com.assistant.server.document

import com.assistant.document.models.GeneratedDocument
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Arb generators for document preservation property tests.
 * Generates realistic GeneratedDocument instances matching current schema (without id field).
 */
object DocumentPreservationGenerators {

    fun arbTicketId(): Arb<String> = Arb.stringPattern("[A-Z]{2,5}-[0-9]{1,4}")

    fun arbDocType(): Arb<String> = Arb.of("BRD", "FSD", "REQUIREMENT_SLIDES")

    fun arbAiProvider(): Arb<String> = Arb.of("openai", "anthropic", "gemini")

    fun arbApprovalStatus(): Arb<String> = Arb.of("DRAFT", "APPROVED", "REJECTED")

    fun arbNonDraftStatus(): Arb<String> = Arb.of("APPROVED", "REJECTED")

    fun arbTimestamp(): Arb<String> = Arb.of(
        "2025-01-15T10:30:00", "2025-06-01T12:00:00Z", "2024-12-31T23:59:59"
    )

    fun arbMarkdownContent(): Arb<String> = Arb.of(
        "# BRD\n\nBusiness requirements here.",
        "# FSD\n\n## Overview\n\nFunctional spec content.",
        "# Slides\n\n- Slide 1\n- Slide 2",
        "## Section\n\nMinimal content."
    )

    fun arbGeneratedDocument(): Arb<GeneratedDocument> = arbitrary {
        GeneratedDocument(
            documentType = arbDocType().bind(),
            ticketId = arbTicketId().bind(),
            generatedAt = arbTimestamp().bind(),
            markdownContent = arbMarkdownContent().bind(),
            sourceTicketIds = Arb.list(arbTicketId(), 0..3).bind(),
            attachmentSources = Arb.list(Arb.of("file1.pdf", "img.png", "doc.xlsx"), 0..2).bind(),
            aiProviderUsed = arbAiProvider().bind(),
            approvalStatus = arbApprovalStatus().bind(),
            versionNumber = Arb.int(1..20).orNull(0.5).bind(),
            rejectReason = Arb.of("Not enough detail in section 3", null).bind(),
            reviewedBy = Arb.of("admin@test.com", "reviewer@test.com", null).bind(),
            reviewedAt = arbTimestamp().orNull(0.4).bind()
        )
    }

    fun arbDraftDocument(): Arb<GeneratedDocument> = arbitrary {
        GeneratedDocument(
            documentType = arbDocType().bind(),
            ticketId = arbTicketId().bind(),
            generatedAt = arbTimestamp().bind(),
            markdownContent = arbMarkdownContent().bind(),
            sourceTicketIds = Arb.list(arbTicketId(), 0..2).bind(),
            attachmentSources = emptyList(),
            aiProviderUsed = arbAiProvider().bind(),
            approvalStatus = "DRAFT",
            versionNumber = null,
            rejectReason = null,
            reviewedBy = null,
            reviewedAt = null
        )
    }

    fun arbNonDraftDocument(): Arb<GeneratedDocument> = arbitrary {
        val status = arbNonDraftStatus().bind()
        GeneratedDocument(
            documentType = arbDocType().bind(),
            ticketId = arbTicketId().bind(),
            generatedAt = arbTimestamp().bind(),
            markdownContent = arbMarkdownContent().bind(),
            sourceTicketIds = Arb.list(arbTicketId(), 0..2).bind(),
            attachmentSources = emptyList(),
            aiProviderUsed = arbAiProvider().bind(),
            approvalStatus = status,
            versionNumber = if (status == "APPROVED") Arb.int(1..10).bind() else null,
            rejectReason = if (status == "REJECTED") "Reason must be at least 10 chars" else null,
            reviewedBy = "reviewer@test.com",
            reviewedAt = arbTimestamp().bind()
        )
    }
}
