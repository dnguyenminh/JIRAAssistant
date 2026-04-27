package com.assistant.server.analysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.BatchInfo
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketNode
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Build a [TicketNode] with minimal fields for prompt testing. */
private fun ticketNode(id: String, depth: Int, summary: String): TicketNode =
    TicketNode(
        ticketId = id,
        depth = depth,
        discoveredVia = RelationshipType.ISSUE_LINK,
        parentDiscoveryId = "ROOT-1",
        issue = StructuredTicketContent(summary = summary, description = "desc-$id")
    )

/** Build a [BatchInfo] with N generated tickets. */
private fun batchInfo(n: Int, batchIndex: Int, totalBatches: Int): BatchInfo {
    val tickets = (1..n).map { ticketNode("T-$it", depth = it % 3, summary = "Summary-$it") }
    val depths = tickets.map { it.depth }.distinct().sorted()
    return BatchInfo(batchIndex = batchIndex, totalBatches = totalBatches, tickets = tickets, depthLevels = depths)
}

/**
 * Property 6: Batch Prompt Content — Root Context và Metadata.
 *
 * For any BatchInfo and root ticket, [BatchPromptBuilder.buildPrompt] SHALL
 * produce a prompt containing: root ticket summary, all ticket IDs,
 * "Batch {X} of {Y}" pattern, JSON schema instruction, and
 * `prompt.length ≤ maxPromptChars`.
 *
 * **Validates: Requirements 3.1, 3.2, 3.6, 3.7**
 */
@OptIn(ExperimentalKotest::class)
class BatchPromptPropertyTest {

    private val cfg = PropTestConfig(iterations = 50)

    /**
     * **Validates: Requirements 3.1**
     *
     * Prompt always contains the root ticket summary text.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 6: Batch Prompt Content")
    fun `prompt contains root ticket summary`() {
        runBlocking {
            checkAll(cfg, Arb.string(10..40), Arb.int(1..20)) { rootSummary, ticketCount ->
                val root = StructuredTicketContent(summary = rootSummary, description = "root desc")
                val batch = batchInfo(ticketCount, batchIndex = 0, totalBatches = 3)
                val prompt = BatchPromptBuilder().buildPrompt(batch, root, emptyList())
                assertTrue(prompt.contains(rootSummary)) {
                    "Prompt missing root summary '$rootSummary'"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.2**
     *
     * Prompt contains every ticket ID from the batch.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 6: Batch Prompt Content")
    fun `prompt contains all ticket IDs from batch`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..15)) { ticketCount ->
                val root = StructuredTicketContent(summary = "Root", description = "root desc")
                val batch = batchInfo(ticketCount, batchIndex = 1, totalBatches = 5)
                val prompt = BatchPromptBuilder().buildPrompt(batch, root, emptyList())
                batch.ticketIds.forEach { id ->
                    assertTrue(prompt.contains(id)) { "Prompt missing ticket ID '$id'" }
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.6**
     *
     * Prompt contains "Batch {batchIndex+1} of {totalBatches}" pattern.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 6: Batch Prompt Content")
    fun `prompt contains batch metadata pattern`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..9), Arb.int(1..10)) { idx, total ->
                val adjustedTotal = maxOf(total, idx + 1)
                val root = StructuredTicketContent(summary = "Root", description = "d")
                val batch = batchInfo(3, batchIndex = idx, totalBatches = adjustedTotal)
                val prompt = BatchPromptBuilder().buildPrompt(batch, root, emptyList())
                val expected = "Batch ${idx + 1} of $adjustedTotal"
                assertTrue(prompt.contains(expected)) {
                    "Prompt missing metadata '$expected'"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.6**
     *
     * Prompt contains JSON schema instruction (OUTPUT FORMAT or JSON).
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 6: Batch Prompt Content")
    fun `prompt contains JSON schema instruction`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..10)) { ticketCount ->
                val root = StructuredTicketContent(summary = "Root", description = "d")
                val batch = batchInfo(ticketCount, batchIndex = 0, totalBatches = 2)
                val prompt = BatchPromptBuilder().buildPrompt(batch, root, emptyList())
                assertTrue(prompt.contains("OUTPUT FORMAT") || prompt.contains("JSON")) {
                    "Prompt missing JSON schema instruction"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.7**
     *
     * Prompt length never exceeds maxPromptChars.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 6: Batch Prompt Content")
    fun `prompt length does not exceed maxPromptChars`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..30), Arb.int(500..5000)) { ticketCount, maxChars ->
                val root = StructuredTicketContent(summary = "Root summary", description = "d")
                val batch = batchInfo(ticketCount, batchIndex = 0, totalBatches = 1)
                val prompt = BatchPromptBuilder(maxPromptChars = maxChars)
                    .buildPrompt(batch, root, emptyList())
                assertTrue(prompt.length <= maxChars) {
                    "Prompt length ${prompt.length} exceeds max $maxChars"
                }
            }
        }
    }
}
