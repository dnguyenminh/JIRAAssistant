package com.assistant.server.document.models

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.SprintMetadata
import com.assistant.kb.KBRecord
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Kotest Arb generators for [EnrichedContext] property tests.
 *
 * Generators produce random but valid instances of all models
 * referenced by EnrichedContext, enabling serialization round-trip
 * and other property-based tests.
 */

/** Random Jira ticket ID matching `[A-Z]{1,4}-\d{1,4}`. */
fun Arb.Companion.ticketId(): Arb<String> = arbitrary {
    val prefix = Arb.string(1..4, Codepoint.alphanumeric())
        .map { it.uppercase().replace(Regex("[^A-Z]"), "A") }
        .bind()
        .ifEmpty { "A" }
    val number = Arb.int(1..9999).bind()
    "$prefix-$number"
}

/** Minimal [KBRecord] with random fields. */
fun Arb.Companion.arbKBRecord(): Arb<KBRecord> = arbitrary {
    KBRecord(
        ticketId = Arb.ticketId().bind(),
        requirementSummary = Arb.string(0..40).bind(),
        evolutionHistory = emptyList(),
        scrumPoints = Arb.double(0.0..40.0).bind(),
        confidenceScore = Arb.double(0.0..1.0).bind(),
        rationale = Arb.string(0..30).bind(),
        similarTicketRefs = emptyList(),
        timestamp = "2025-01-01T00:00:00Z",
        businessSummary = Arb.string(0..50).bind(),
        asIsState = Arb.string(0..30).bind(),
        toBeState = Arb.string(0..30).bind(),
        extractedRequirements = Arb.list(Arb.string(3..20), 0..3).bind()
    )
}

/** Random [AttachmentChunkInfo]. */
fun Arb.Companion.arbAttachmentChunk(): Arb<AttachmentChunkInfo> = arbitrary {
    AttachmentChunkInfo(
        filename = Arb.string(2..10).bind() + ".pdf",
        content = Arb.string(5..60).bind(),
        similarityScore = Arb.float(0f..1f).bind()
    )
}

/** Random [SprintMetadata]. */
fun Arb.Companion.arbSprintMetadata(): Arb<SprintMetadata> = arbitrary {
    SprintMetadata(
        sprintName = "Sprint-${Arb.int(1..50).bind()}",
        startDate = "2025-01-01",
        endDate = "2025-01-14"
    )
}

/** Random [FullComment] with non-empty fields. */
fun Arb.Companion.arbFullComment(): Arb<FullComment> = arbitrary {
    FullComment(
        author = Arb.string(2..15).bind(),
        createdDate = "2025-01-${Arb.int(10..28).bind()}T10:00:00Z",
        updatedDate = "",
        body = Arb.string(5..80).bind()
    )
}

/** Random [TicketEdge]. */
fun Arb.Companion.arbTicketEdge(): Arb<TicketEdge> = arbitrary {
    TicketEdge(
        sourceId = Arb.ticketId().bind(),
        targetId = Arb.ticketId().bind(),
        relationshipType = Arb.of(RelationshipType.entries).bind(),
        linkDescription = Arb.string(0..20).bind()
    )
}

/** Random [TraversalMetadata]. */
fun Arb.Companion.arbTraversalMetadata(): Arb<TraversalMetadata> = arbitrary {
    val discovered = Arb.int(1..50).bind()
    val fetched = Arb.int(0..discovered).bind()
    TraversalMetadata(
        totalDiscovered = discovered,
        totalFetched = fetched,
        totalSkipped = discovered - fetched,
        maxDepthReached = Arb.int(0..10).bind(),
        traversalTimeMs = Arb.long(10L..30_000L).bind(),
        skippedTicketIds = Arb.list(Arb.ticketId(), 0..3).bind(),
        permissionDeniedCount = Arb.int(0..5).bind(),
        earlyTerminated = Arb.boolean().bind()
    )
}

/** Minimal [StructuredTicketContent] for serialization tests. */
fun Arb.Companion.arbStructuredTicketContent(): Arb<StructuredTicketContent> =
    arbitrary {
        StructuredTicketContent(
            summary = Arb.string(3..30).bind(),
            description = Arb.string(0..50).bind(),
            status = Arb.of("Open", "In Progress", "Done").bind(),
            priority = Arb.of("High", "Medium", "Low").bind(),
            issueType = Arb.of("Story", "Bug", "Task").bind(),
            createdDate = "2025-01-01T00:00:00Z",
            updatedDate = "2025-01-10T00:00:00Z"
        )
    }

/**
 * Random [EnrichedContext] with all fields populated.
 *
 * Generates valid instances suitable for serialization round-trip
 * property testing.
 */
fun Arb.Companion.arbEnrichedContext(): Arb<EnrichedContext> = arbitrary {
    val ticketIds = Arb.list(Arb.ticketId(), 0..5).bind()
    val comments = ticketIds.associateWith {
        Arb.list(Arb.arbFullComment(), 0..3).bind()
    }
    EnrichedContext(
        mainTicket = Arb.arbKBRecord().bind(),
        linkedTicketAnalyses = Arb.list(Arb.arbKBRecord(), 0..5).bind(),
        attachmentChunks = Arb.list(Arb.arbAttachmentChunk(), 0..4).bind(),
        sprintMetadata = if (Arb.boolean().bind()) {
            Arb.arbSprintMetadata().bind()
        } else null,
        allTickets = Arb.list(
            Arb.arbStructuredTicketContent(), 0..5
        ).bind(),
        ticketRelationships = Arb.list(Arb.arbTicketEdge(), 0..5).bind(),
        rawComments = comments,
        allAttachmentChunks = Arb.list(
            Arb.arbAttachmentChunk(), 0..4
        ).bind(),
        traversalMetadata = if (Arb.boolean().bind()) {
            Arb.arbTraversalMetadata().bind()
        } else null,
        ticketDepthMap = ticketIds.associateWith {
            Arb.int(0..5).bind()
        }
    )
}
