package com.assistant.document

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property tests for BRD prompt fallback logic.
 *
 * - Property 3: Fallback to raw ticket data when linkedTicketAnalyses empty
 * - Property 4: Comments inclusion for all tickets
 * - Property 10: Attachment inclusion from all tickets
 * - Property 11: Fallback annotation for unanalyzed tickets
 *
 * **Validates: Requirements 2.2, 2.3, 7.1, 7.2, 7.3, 7.4**
 */
class BrdPromptFallbackPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 3: Fallback to raw ticket data khi linkedTicketAnalyses rỗng.
     *
     * **Validates: Requirements 2.2, 7.1**
     */
    @Test
    fun `Property 3 - fallback includes raw ticket summary and status`() = runTest {
        checkAll(cfg, arbFallbackTickets()) { (allTickets, rootId) ->
            val output = buildString {
                appendFallbackLinkedTickets(allTickets, rootId)
            }
            val nonRoot = allTickets.drop(1).filter { it.summary.isNotBlank() }
            nonRoot.forEach { ticket ->
                assertTrue(
                    output.contains(ticket.summary),
                    "Missing summary '${ticket.summary}'"
                )
                if (ticket.status.isNotBlank()) {
                    assertTrue(
                        output.contains(ticket.status),
                        "Missing status '${ticket.status}'"
                    )
                }
            }
        }
    }

    /**
     * Property 4: Comments inclusion cho tất cả tickets.
     *
     * **Validates: Requirements 2.3, 7.2**
     */
    @Test
    fun `Property 4 - comments from all tickets are included`() = runTest {
        checkAll(cfg, arbCommentMap()) { commentMap ->
            val output = buildString { appendEnrichedComments(commentMap) }
            commentMap.forEach { (ticketId, comments) ->
                if (comments.isNotEmpty()) {
                    assertTrue(output.contains(ticketId), "Missing ticketId '$ticketId'")
                    comments.forEach { comment ->
                        assertTrue(output.contains(comment.body), "Missing comment body")
                        assertTrue(output.contains(comment.author), "Missing author")
                    }
                }
            }
        }
    }

    /**
     * Property 10: Attachment inclusion từ tất cả tickets.
     *
     * **Validates: Requirements 7.3**
     */
    @Test
    fun `Property 10 - all attachment chunks are included`() = runTest {
        checkAll(cfg, arbAttachmentChunks()) { chunks ->
            val output = buildString { appendAttachmentData(chunks) }
            chunks.forEach { chunk ->
                assertTrue(output.contains(chunk.filename), "Missing '${chunk.filename}'")
                assertTrue(output.contains(chunk.content), "Missing content")
            }
        }
    }

    /**
     * Property 11: Fallback annotation cho tickets chưa analyze.
     *
     * **Validates: Requirements 7.4**
     */
    @Test
    fun `Property 11 - fallback annotation present for unanalyzed tickets`() = runTest {
        checkAll(cfg, arbFallbackTickets()) { (allTickets, rootId) ->
            val output = buildString {
                appendFallbackLinkedTickets(allTickets, rootId)
            }
            val nonRoot = allTickets.drop(1).filter { it.summary.isNotBlank() }
            if (nonRoot.isNotEmpty()) {
                assertTrue(
                    output.contains("chưa có deep analysis"),
                    "Missing fallback annotation"
                )
                assertTrue(
                    output.contains("raw Jira data"),
                    "Missing raw Jira data mention"
                )
            }
        }
    }
}
