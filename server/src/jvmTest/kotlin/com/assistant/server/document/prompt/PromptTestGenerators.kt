package com.assistant.server.document.prompt

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.models.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Custom Arb generators for PromptAssembler property tests.
 *
 * Provides generators that produce EnrichedContext instances
 * with controlled depth maps and non-empty comment/chunk data.
 */

/** FullComment with guaranteed non-empty author, date, body. */
fun Arb.Companion.arbNonEmptyComment(): Arb<FullComment> = arbitrary {
    FullComment(
        author = Arb.string(2..10, Codepoint.alphanumeric()).bind(),
        createdDate = "2025-01-${Arb.int(10..28).bind()}T10:00:00Z",
        updatedDate = "",
        body = Arb.string(5..40, Codepoint.alphanumeric()).bind()
    )
}

/** AttachmentChunkInfo with guaranteed non-empty filename and content. */
fun Arb.Companion.arbNonEmptyChunk(): Arb<AttachmentChunkInfo> = arbitrary {
    AttachmentChunkInfo(
        filename = Arb.string(2..8, Codepoint.alphanumeric()).bind() + ".pdf",
        content = Arb.string(5..30, Codepoint.alphanumeric()).bind(),
        similarityScore = Arb.float(0f..1f).bind()
    )
}

/**
 * EnrichedContext with tickets at depth 0, 1, and >=2,
 * ensuring both DEPTH1_RAW and DEEPER_TICKETS sections are populated.
 */
fun Arb.Companion.arbContextWithMultipleDepths(): Arb<EnrichedContext> =
    arbitrary {
        val base = Arb.arbEnrichedContext().bind()
        val rootId = base.mainTicket.ticketId
        val d1Id = Arb.ticketId().bind()
        val d2Id = Arb.ticketId().bind()
        val depthMap = mapOf(rootId to 0, d1Id to 1, d2Id to 2)
        base.copy(ticketDepthMap = depthMap)
    }
