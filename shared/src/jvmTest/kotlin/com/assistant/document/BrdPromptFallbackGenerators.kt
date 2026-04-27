package com.assistant.document

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Kotest Arb generators for BRD fallback property tests.
 *
 * Provides generators for StructuredTicketContent lists,
 * CommentData maps, and AttachmentChunkInfo lists used
 * by [BrdPromptFallbackPropertyTest].
 */

/** Pair of (allTickets with >=2 entries, rootTicketId). */
fun arbFallbackTickets(): Arb<Pair<List<StructuredTicketContent>, String>> =
    arbitrary {
        val rootId = Arb.string(3..8, Codepoint.alphanumeric())
            .map { it.uppercase().take(4) + "-1" }.bind()
        val root = StructuredTicketContent(
            summary = rootId,
            description = Arb.string(5..30, Codepoint.alphanumeric()).bind(),
            status = "Open",
            priority = "High"
        )
        val linked = Arb.list(arbNonEmptyTicket(), 1..4).bind()
        Pair(listOf(root) + linked, rootId)
    }

/** StructuredTicketContent with guaranteed non-empty summary. */
private fun arbNonEmptyTicket(): Arb<StructuredTicketContent> = arbitrary {
    StructuredTicketContent(
        summary = Arb.string(3..20, Codepoint.alphanumeric()).bind(),
        description = Arb.string(0..40, Codepoint.alphanumeric()).bind(),
        status = Arb.of("Open", "In Progress", "Done").bind(),
        priority = Arb.of("High", "Medium", "Low").bind(),
        issueType = Arb.of("Story", "Bug", "Task").bind()
    )
}

/** Map of ticketId → list of CommentData, at least 1 ticket with comments. */
fun arbCommentMap(): Arb<Map<String, List<CommentData>>> = arbitrary {
    val ticketCount = Arb.int(1..4).bind()
    val ids = (1..ticketCount).map {
        Arb.string(3..6, Codepoint.alphanumeric())
            .map { s -> s.uppercase().take(4) + "-$it" }.bind()
    }
    ids.associateWith { Arb.list(arbCommentData(), 1..3).bind() }
}

/** CommentData with non-empty fields. */
private fun arbCommentData(): Arb<CommentData> = arbitrary {
    CommentData(
        author = Arb.string(2..10, Codepoint.alphanumeric()).bind(),
        date = "2025-01-${Arb.int(10..28).bind()}",
        body = Arb.string(5..30, Codepoint.alphanumeric()).bind()
    )
}

/** List of AttachmentChunkInfo with at least 1 chunk. */
fun arbAttachmentChunks(): Arb<List<AttachmentChunkInfo>> =
    Arb.list(arbNonEmptyChunk(), 1..5)

/** AttachmentChunkInfo with non-empty filename and content. */
private fun arbNonEmptyChunk(): Arb<AttachmentChunkInfo> = arbitrary {
    AttachmentChunkInfo(
        filename = Arb.string(2..8, Codepoint.alphanumeric()).bind() + ".pdf",
        content = Arb.string(5..30, Codepoint.alphanumeric()).bind(),
        similarityScore = Arb.float(0f..1f).bind()
    )
}
