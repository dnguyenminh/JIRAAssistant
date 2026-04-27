package com.assistant.server.document.collection

import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.document.extraction.TicketIdExtractor
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
 * Property tests for [AttachmentContentCollector].
 *
 * **Property 7: Attachment Collection — Grouping, Sorting, and Deduplication**
 *
 * **Validates: Requirements 4.3, 4.5**
 */
@OptIn(ExperimentalKotest::class)
class AttachmentCollectorPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val ticketId = "TEST-1"

    // ── Generators ──

    private val arbFilename: Arb<String> = Arb.element(
        "spec.pdf", "design.docx", "api.yaml",
        "notes.txt", "schema.sql", "readme.md"
    )

    private val arbAttachmentId: Arb<String> = Arb.element(
        "att-100", "att-200", "att-300",
        "att-400", "att-500", "att-600"
    )

    private fun buildChunk(
        attachmentId: String,
        filename: String,
        chunkIndex: Int,
        forTicketId: String = ticketId
    ): AttachmentChunk = AttachmentChunk(
        ticketId = forTicketId,
        attachmentId = attachmentId,
        filename = filename,
        chunkIndex = chunkIndex,
        chunkText = "Content of $filename chunk $chunkIndex",
        embedding = emptyList(),
        createdAt = "2024-01-01T00:00:00Z"
    )

    private fun arbChunkList(): Arb<List<AttachmentChunk>> =
        Arb.list(
            Arb.bind(
                arbAttachmentId,
                arbFilename,
                Arb.int(0..9)
            ) { attId, fname, idx -> buildChunk(attId, fname, idx) },
            range = 0..30
        )

    // ── Property 7a: Chunks of same filename are grouped contiguously ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 7: Grouping")
    fun `chunks of same filename are grouped contiguously`() {
        runBlocking {
            checkAll(cfg, arbChunkList()) { inputChunks ->
                val store = FakeVectorStore(inputChunks)
                val collector = buildCollector(store)

                val result = collector.collectAll(ticketId)
                val filenames = result.chunks.map { it.filename }

                assertGroupedContiguously(filenames)
            }
        }
    }

    // ── Property 7b: Within each group, sorted by chunkIndex ascending ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 7: Sorting")
    fun `within each filename group chunks sorted by chunkIndex`() {
        runBlocking {
            checkAll(cfg, arbChunkList()) { inputChunks ->
                val store = FakeVectorStore(inputChunks)
                val collector = buildCollector(store)

                val result = collector.collectAll(ticketId)
                val grouped = result.chunks.groupBy { it.filename }

                grouped.forEach { (fname, group) ->
                    val indices = group.map { it.chunkIndex }
                    assertEquals(indices.sorted(), indices) {
                        "Chunks for '$fname' not sorted by chunkIndex"
                    }
                }
            }
        }
    }

    // ── Property 7c: Deduplicated by (attachmentId, chunkIndex) ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 7: Deduplication")
    fun `no duplicate (attachmentId, chunkIndex) pairs in result`() {
        runBlocking {
            checkAll(cfg, arbChunkList()) { inputChunks ->
                val store = FakeVectorStore(inputChunks)
                val collector = buildCollector(store)

                val result = collector.collectAll(ticketId)
                val keys = result.chunks.map {
                    "${it.attachmentId}:${it.chunkIndex}"
                }

                assertEquals(keys.size, keys.toSet().size) {
                    "Duplicate (attachmentId, chunkIndex) found"
                }
            }
        }
    }

    // ── Combined: all three sub-properties hold simultaneously ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 7: Combined")
    fun `grouping sorting and deduplication hold together`() {
        runBlocking {
            checkAll(cfg, arbChunkList()) { inputChunks ->
                val store = FakeVectorStore(inputChunks)
                val collector = buildCollector(store)

                val result = collector.collectAll(ticketId)
                val chunks = result.chunks

                // Grouped contiguously
                assertGroupedContiguously(chunks.map { it.filename })

                // Sorted within groups
                chunks.groupBy { it.filename }.forEach { (fname, grp) ->
                    val idx = grp.map { it.chunkIndex }
                    assertEquals(idx.sorted(), idx) {
                        "Not sorted for '$fname'"
                    }
                }

                // Deduplicated
                val keys = chunks.map {
                    "${it.attachmentId}:${it.chunkIndex}"
                }
                assertEquals(keys.size, keys.toSet().size) {
                    "Duplicates found"
                }
            }
        }
    }

    // ── Helpers ──

    private fun buildCollector(store: FakeVectorStore) =
        AttachmentContentCollector(store, TicketIdExtractor)

    /**
     * Assert that equal values in [items] appear contiguously.
     * e.g. [A, A, B, B, C] is OK; [A, B, A] is NOT.
     */
    private fun assertGroupedContiguously(items: List<String>) {
        val seen = mutableSetOf<String>()
        var prev: String? = null
        for (item in items) {
            if (item != prev) {
                assertFalse(item in seen) {
                    "'$item' reappears after gap — not grouped"
                }
                seen.add(item)
                prev = item
            }
        }
    }
}
