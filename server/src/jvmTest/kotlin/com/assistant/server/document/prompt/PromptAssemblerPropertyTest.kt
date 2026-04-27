package com.assistant.server.document.prompt

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.models.EnrichedContext
import com.assistant.server.document.models.FullComment
import com.assistant.server.document.models.arbEnrichedContext
import com.assistant.server.document.models.arbFullComment
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property tests for PromptAssembler: size limit, priority truncation,
 * and comment/attachment formatting.
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.8**
 */
@OptIn(ExperimentalKotest::class)
class PromptAssemblerPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /** Remove truncation annotation from assembled text. */
    private fun stripAnnotation(text: String): String =
        text.replace(Regex("\\n?\\[TRUNCATED:[^]]*]\\n?"), "")

    // --- Property 9: Prompt Size Limit and No Unnecessary Truncation ---

    /**
     * **Validates: Requirements 6.2**
     *
     * For any EnrichedContext and any budget > 0,
     * the content portion (excluding truncation annotation) SHALL
     * NOT exceed the budget. The annotation is metadata appended
     * after assembly to inform the AI about removed content.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 9: Prompt Size Limit")
    fun `assembled content portion never exceeds budget`() {
        runBlocking {
            checkAll(cfg, Arb.arbEnrichedContext(), Arb.int(200..50_000)) { ctx, budget ->
                val sections = buildSectionMap(ctx)
                val priorities = PromptPriorityConfig.brdPriority
                val result = assembleSections(sections, priorities, budget)

                val contentLength = stripAnnotation(result.text).length
                assertTrue(contentLength <= budget) {
                    "Content length $contentLength exceeds budget $budget"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 6.8**
     *
     * When total content fits within budget, no TRUNCATED annotation
     * appears — content is included without unnecessary truncation.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 9: No Unnecessary Truncation")
    fun `no truncation annotation when content fits within budget`() {
        runBlocking {
            checkAll(cfg, Arb.arbEnrichedContext()) { ctx ->
                val sections = buildSectionMap(ctx)
                val priorities = PromptPriorityConfig.brdPriority
                val totalSize = sections.values.sumOf { it.length } + 100
                val budget = totalSize + 5000
                val result = assembleSections(sections, priorities, budget)

                assertFalse(result.text.contains("[TRUNCATED:")) {
                    "Truncation annotation found when content fits"
                }
                assertEquals(0, result.removedTickets)
                assertEquals(0, result.removedChunks)
            }
        }
    }

    // --- Property 10: Prompt Priority-based Truncation ---

    /**
     * **Validates: Requirements 6.1, 6.3**
     *
     * Root ticket raw data (highest priority) is always present
     * in the assembled output.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 10: Root Data Kept")
    fun `root ticket raw data always present in output`() {
        runBlocking {
            checkAll(cfg, Arb.arbEnrichedContext(), Arb.int(500..50_000)) { ctx, budget ->
                val sections = buildSectionMap(ctx)
                val priorities = PromptPriorityConfig.brdPriority
                val rootSection = sections[PromptSectionType.ROOT_RAW] ?: ""
                if (rootSection.isBlank()) return@checkAll

                val result = assembleSections(sections, priorities, budget)
                val rootId = ctx.mainTicket.ticketId

                assertTrue(result.text.contains(rootId)) {
                    "Root ticket ID $rootId missing from output"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 6.1, 6.3**
     *
     * If deeper tickets (depth>=2) are truncated, depth-1 tickets
     * SHALL still be present in the prompt.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 10: Depth-2 Cut Before Depth-1")
    fun `depth-2 tickets cut before depth-1 tickets`() {
        runBlocking {
            checkAll(cfg, Arb.arbContextWithMultipleDepths()) { ctx ->
                val sections = buildSectionMap(ctx)
                val priorities = PromptPriorityConfig.brdPriority
                val depth1Text = sections[PromptSectionType.DEPTH1_RAW] ?: ""
                val deeperText = sections[PromptSectionType.DEEPER_TICKETS] ?: ""
                if (depth1Text.isBlank() || deeperText.isBlank()) return@checkAll

                val totalSize = sections.values.sumOf { it.length } + 100
                val budget = totalSize - deeperText.length + 50
                val result = assembleSections(sections, priorities, budget)

                val hasDepth1 = result.text.contains("Depth-1 Tickets")
                val hasDeeper = result.text.contains("Deeper Tickets")

                if (!hasDeeper && depth1Text.isNotBlank()) {
                    assertTrue(hasDepth1) {
                        "Depth-1 should be kept when deeper is truncated"
                    }
                }
            }
        }
    }

    // --- Property 11: Prompt Formatting ---

    /**
     * **Validates: Requirements 6.4**
     *
     * For any FullComment with non-empty author, date, body,
     * formatted output SHALL contain all three fields.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 11: Comment Format")
    fun `formatComment contains author date and body`() {
        runBlocking {
            checkAll(cfg, Arb.arbNonEmptyComment()) { comment ->
                val formatted = PromptSectionBuilder.formatComment(comment)

                assertTrue(formatted.contains(comment.author)) {
                    "Missing author '${comment.author}'"
                }
                assertTrue(formatted.contains(comment.createdDate)) {
                    "Missing date '${comment.createdDate}'"
                }
                assertTrue(formatted.contains(comment.body)) {
                    "Missing body '${comment.body}'"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 6.5**
     *
     * For any AttachmentChunkInfo with non-empty filename, ticketId,
     * content, formatted output SHALL contain all three fields.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 11: Attachment Format")
    fun `formatChunk contains filename ticketId and content`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.arbNonEmptyChunk(),
                Arb.string(3..10).map { it.uppercase().take(4) + "-1" }
            ) { chunk, ticketId ->
                val formatted = PromptSectionBuilder.formatChunk(chunk, ticketId)

                assertTrue(formatted.contains(chunk.filename)) {
                    "Missing filename '${chunk.filename}'"
                }
                assertTrue(formatted.contains(ticketId)) {
                    "Missing ticketId '$ticketId'"
                }
                assertTrue(formatted.contains(chunk.content)) {
                    "Missing content '${chunk.content}'"
                }
            }
        }
    }
}
