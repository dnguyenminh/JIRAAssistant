package com.assistant.server.analysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.BatchSummary
import com.assistant.server.analysis.models.arbBatchSummary
import com.assistant.server.document.models.TraversalMetadata
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

private fun Arb.Companion.arbRootTicket(): Arb<StructuredTicketContent> = arbitrary {
    StructuredTicketContent(
        summary = Arb.string(5..80).bind(),
        description = Arb.string(10..200).bind(),
        status = Arb.string(3..20).bind(),
        priority = Arb.string(3..20).bind()
    )
}

private fun Arb.Companion.arbDistinctSummaries(): Arb<List<BatchSummary>> = arbitrary {
    val count = Arb.int(2..6).bind()
    (0..50).shuffled().take(count).map { idx ->
        Arb.arbBatchSummary().bind().copy(batchIndex = idx)
    }
}

private fun Arb.Companion.arbMetadata(): Arb<TraversalMetadata> = arbitrary {
    TraversalMetadata(
        totalDiscovered = Arb.int(1..500).bind(),
        totalFetched = Arb.int(1..500).bind(),
        totalSkipped = Arb.int(0..50).bind(),
        maxDepthReached = Arb.int(0..20).bind(),
        traversalTimeMs = Arb.int(100..60_000).bind().toLong()
    )
}

/**
 * Property 9: Reduce Prompt — Content and Ordering.
 * Property 12: Incomplete Analysis Warning.
 *
 * **Validates: Requirements 4.1, 4.6, 7.4**
 */
@OptIn(ExperimentalKotest::class)
class ReducePromptPropertyTest {

    private val builder = ReducePromptBuilder()
    private val cfg = PropTestConfig(iterations = 100)

    // ── Property 9: Content and Ordering ────────────────────

    /** **Validates: Requirements 4.1** */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 9: Content")
    fun `prompt contains root ticket summary and description`() {
        runBlocking {
            checkAll(
                cfg, Arb.arbRootTicket(),
                Arb.list(Arb.arbBatchSummary(), 1..5), Arb.arbMetadata()
            ) { root, summaries, meta ->
                val prompt = builder.buildPrompt(root, summaries, meta, summaries.size)
                assertTrue(prompt.contains(root.summary)) {
                    "Prompt missing root summary"
                }
                if (root.description.isNotBlank()) {
                    assertTrue(prompt.contains(root.description)) {
                        "Prompt missing root description"
                    }
                }
            }
        }
    }

    /** **Validates: Requirements 4.6** — summaries in batchIndex ascending order. */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 9: Ordering")
    fun `prompt contains all summaries in batchIndex ascending order`() {
        runBlocking {
            checkAll(
                cfg, Arb.arbRootTicket(),
                Arb.arbDistinctSummaries(), Arb.arbMetadata()
            ) { root, summaries, meta ->
                val prompt = builder.buildPrompt(root, summaries, meta, summaries.size)
                val sorted = summaries.sortedBy { it.batchIndex }
                val positions = sorted.map { s ->
                    prompt.indexOf("--- Batch ${s.batchIndex} ---")
                }
                for (i in 0 until positions.size - 1) {
                    assertTrue(positions[i] < positions[i + 1]) {
                        "Batch ${sorted[i].batchIndex} not before ${sorted[i + 1].batchIndex}"
                    }
                }
            }
        }
    }

    /** **Validates: Requirements 4.1** — graph metadata present. */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 9: Content")
    fun `prompt contains graph metadata`() {
        runBlocking {
            checkAll(
                cfg, Arb.arbRootTicket(),
                Arb.list(Arb.arbBatchSummary(), 1..3), Arb.arbMetadata()
            ) { root, summaries, meta ->
                val prompt = builder.buildPrompt(root, summaries, meta, summaries.size)
                assertTrue(prompt.contains("${meta.totalDiscovered}")) {
                    "Prompt missing totalDiscovered"
                }
                assertTrue(prompt.contains("${meta.maxDepthReached}")) {
                    "Prompt missing maxDepthReached"
                }
            }
        }
    }

    /** **Validates: Requirements 4.1** — JSON schema instruction present. */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 9: Content")
    fun `prompt contains JSON schema instruction`() {
        runBlocking {
            checkAll(
                cfg, Arb.arbRootTicket(),
                Arb.list(Arb.arbBatchSummary(), 1..3), Arb.arbMetadata()
            ) { root, summaries, meta ->
                val prompt = builder.buildPrompt(root, summaries, meta, summaries.size)
                assertTrue(prompt.contains("JSON")) {
                    "Prompt missing JSON schema instruction"
                }
            }
        }
    }

    // ── Property 12: Incomplete Analysis Warning ────────────

    /** **Validates: Requirements 7.4** — WARNING when < 50% batches succeeded. */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 12: Warning")
    fun `warning present when successful batches below half`() {
        runBlocking {
            checkAll(
                cfg, Arb.arbRootTicket(),
                Arb.list(Arb.arbBatchSummary(), 1..3), Arb.arbMetadata()
            ) { root, summaries, meta ->
                val totalBatches = summaries.size * 3 + 1
                val prompt = builder.buildPrompt(root, summaries, meta, totalBatches)
                assertTrue(prompt.contains("WARNING")) {
                    "Expected WARNING for ${summaries.size}/$totalBatches"
                }
            }
        }
    }

    /** **Validates: Requirements 7.4** — no WARNING when >= 50% succeeded. */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 12: No Warning")
    fun `no warning when successful batches at or above half`() {
        runBlocking {
            checkAll(
                cfg, Arb.arbRootTicket(),
                Arb.list(Arb.arbBatchSummary(), 1..5), Arb.arbMetadata()
            ) { root, summaries, meta ->
                val prompt = builder.buildPrompt(root, summaries, meta, summaries.size)
                assertFalse(prompt.contains("WARNING")) {
                    "Unexpected WARNING for ${summaries.size}/${summaries.size}"
                }
            }
        }
    }
}
