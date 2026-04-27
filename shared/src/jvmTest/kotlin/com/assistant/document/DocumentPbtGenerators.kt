package com.assistant.document

import com.assistant.ai.deepanalysis.models.*
import com.assistant.document.models.*
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Custom Kotest Arb generators for document PBT tests.
 * Requirements: Property 1–11
 */

fun Arb.Companion.kbRecord(): Arb<KBRecord> = arbitrary {
    KBRecord(
        ticketId = Arb.string(5..12).bind(),
        requirementSummary = Arb.string(0..80).bind(),
        evolutionHistory = emptyList(),
        scrumPoints = Arb.double(0.0..40.0).bind(),
        confidenceScore = Arb.double(0.0..1.0).bind(),
        rationale = Arb.string(0..50).bind(),
        similarTicketRefs = emptyList(),
        timestamp = "2025-01-01T00:00:00Z",
        businessSummary = Arb.string(1..100).bind(),
        asIsState = Arb.string(0..60).bind(),
        toBeState = Arb.string(0..60).bind(),
        extractedRequirements = Arb.list(Arb.string(5..40), 0..5).bind()
    )
}

fun Arb.Companion.attachmentChunkInfo(): Arb<AttachmentChunkInfo> = arbitrary {
    AttachmentChunkInfo(
        filename = Arb.string(3..20).bind() + ".pdf",
        content = Arb.string(10..100).bind(),
        similarityScore = Arb.float(0f..1f).bind()
    )
}

fun Arb.Companion.generationContext(): Arb<GenerationContext> = arbitrary {
    GenerationContext(
        mainTicket = Arb.kbRecord().bind(),
        linkedTicketAnalyses = Arb.list(Arb.kbRecord(), 0..30).bind(),
        attachmentChunks = Arb.list(Arb.attachmentChunkInfo(), 0..15).bind(),
        sprintMetadata = SprintMetadata(
            sprintName = Arb.string(0..20).bind(),
            startDate = "2025-01-01",
            endDate = "2025-01-14"
        )
    )
}

private val BRD_COUNT = BrdPromptBuilder.BRD_SECTIONS.size
private val FSD_COUNT = FsdPromptBuilder.FSD_SECTIONS.size

fun Arb.Companion.brdMarkdown(sectionCount: IntRange = 0..BRD_COUNT): Arb<String> = arbitrary {
    val count = Arb.int(sectionCount).bind()
    val headings = BrdPromptBuilder.BRD_SECTIONS.shuffled().take(count)
    val bodies = Arb.list(Arb.string(10..120), count..count).bind()
    headings.zip(bodies).joinToString("\n\n") { (heading, body) ->
        "## $heading\n\n$body"
    }
}

fun Arb.Companion.fsdMarkdown(sectionCount: IntRange = 0..FSD_COUNT): Arb<String> = arbitrary {
    val count = Arb.int(sectionCount).bind()
    val headings = FsdPromptBuilder.FSD_SECTIONS.shuffled().take(count)
    val bodies = Arb.list(Arb.string(10..120), count..count).bind()
    headings.zip(bodies).joinToString("\n\n") { (heading, body) ->
        "## $heading\n\n$body"
    }
}

fun Arb.Companion.generatedDocument(): Arb<GeneratedDocument> = arbitrary {
    val docType = Arb.element("BRD", "FSD", "REQUIREMENT_SLIDES").bind()
    GeneratedDocument(
        documentType = docType,
        ticketId = Arb.string(3..15).bind(),
        generatedAt = "2025-01-15T10:30:00Z",
        markdownContent = Arb.string(10..200).bind(),
        sourceTicketIds = Arb.list(Arb.string(3..10), 0..5).bind(),
        attachmentSources = Arb.list(Arb.string(3..15), 0..3).bind(),
        aiProviderUsed = Arb.element("gemini", "ollama", "openai").bind()
    )
}

// --- Bug condition generators for heading variation tests ---

fun Arb.Companion.brdMarkdownWithNumberedHeadings(): Arb<String> = arbitrary {
    val bodies = Arb.list(Arb.string(10..120), BRD_COUNT..BRD_COUNT).bind()
    BrdPromptBuilder.BRD_SECTIONS.mapIndexed { i, heading ->
        "## ${i + 1}. $heading\n\n${bodies[i]}"
    }.joinToString("\n\n")
}

fun Arb.Companion.brdMarkdownWithLowercaseHeadings(): Arb<String> = arbitrary {
    val bodies = Arb.list(Arb.string(10..120), BRD_COUNT..BRD_COUNT).bind()
    BrdPromptBuilder.BRD_SECTIONS.mapIndexed { i, heading ->
        "## ${heading.lowercase()}\n\n${bodies[i]}"
    }.joinToString("\n\n")
}

fun Arb.Companion.brdMarkdownWithH1Headings(): Arb<String> = arbitrary {
    val bodies = Arb.list(Arb.string(10..120), BRD_COUNT..BRD_COUNT).bind()
    BrdPromptBuilder.BRD_SECTIONS.mapIndexed { i, heading ->
        "# $heading\n\n${bodies[i]}"
    }.joinToString("\n\n")
}

fun Arb.Companion.brdMarkdownWithBoldHeadings(): Arb<String> = arbitrary {
    val bodies = Arb.list(Arb.string(10..120), BRD_COUNT..BRD_COUNT).bind()
    BrdPromptBuilder.BRD_SECTIONS.mapIndexed { i, heading ->
        "## **$heading**\n\n${bodies[i]}"
    }.joinToString("\n\n")
}

fun Arb.Companion.fsdMarkdownWithNumberedHeadings(): Arb<String> = arbitrary {
    val bodies = Arb.list(Arb.string(10..120), FSD_COUNT..FSD_COUNT).bind()
    FsdPromptBuilder.FSD_SECTIONS.mapIndexed { i, heading ->
        "## ${i + 1}. $heading\n\n${bodies[i]}"
    }.joinToString("\n\n")
}
