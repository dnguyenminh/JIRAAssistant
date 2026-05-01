package com.assistant.server.document

import com.assistant.server.document.collection.FakeVectorStore
import com.assistant.server.document.models.EnrichedContext
import com.assistant.server.document.models.TraversalConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property test for DeepCollector backward compatibility.
 *
 * **Property 14: Backward Compatibility — linkedTicketAnalyses Populated**
 * Verify linkedTicketAnalyses contains KBRecords of all non-root tickets
 * that have a KBRecord in KBRepository.
 *
 * **Validates: Requirements 12.3**
 */
@OptIn(ExperimentalKotest::class)
class DeepCollectorPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 14: Backward Compatibility")
    fun `linkedTicketAnalyses contains exactly KBRecords of traversed non-root tickets`() {
        runBlocking {
            checkAll(cfg, arbTicketGraph()) { spec ->
                val kbMap = spec.kbTicketIds.associateWith { buildKBRecord(it) }

                val collector = DeepCollector(
                    jiraClientProvider = { GraphJiraClient(spec.ticketLinks) },
                    kbRepository = FakeKBRepository(kbMap),
                    vectorStore = FakeVectorStore(),
                    scanLogRepository = NoOpScanLogRepository(),
                    configProvider = { defaultConfig() },
                    traversalCache = NoOpTraversalCache(),
                    rateLimiter = NoOpRateLimiter(),
                    collectionJobManager = NoOpCollectionJobManager(),
                    jiraApiSemaphore = Semaphore(5),
                    aiAnalysisSemaphore = Semaphore(5)
                )

                val result = collector.aggregate(spec.rootId)
                result.shouldBeInstanceOf<EnrichedContext>()

                val traversedNonRoot = extractTraversedNonRoot(result, spec.rootId)
                val expectedIds = spec.kbTicketIds.filter { it in traversedNonRoot }
                val actualIds = result.linkedTicketAnalyses.map { it.ticketId }

                actualIds.shouldContainExactlyInAnyOrder(expectedIds)
            }
        }
    }

    /** Determine which non-root ticket IDs were actually traversed. */
    private fun extractTraversedNonRoot(ctx: EnrichedContext, rootId: String): Set<String> =
        ctx.ticketDepthMap.keys.filter { it != rootId }.toSet()

    private fun defaultConfig() = TraversalConfig(maxDepth = 10, maxTickets = 200)
}

/** Spec for a random ticket graph with KB records for some tickets. */
data class TicketGraphSpec(
    val rootId: String,
    val ticketLinks: Map<String, List<String>>,
    val kbTicketIds: List<String>
)

/**
 * Generator for random ticket graphs with 2-8 tickets.
 *
 * Produces a root ticket linked to random subsets of other tickets,
 * with a random subset having KBRecords in the repository.
 */
fun arbTicketGraph(): Arb<TicketGraphSpec> = arbitrary {
    val nodeCount = Arb.int(2..8).bind()
    val ids = (1..nodeCount).map { "TEST-$it" }
    val rootId = ids.first()

    val links = mutableMapOf<String, List<String>>()
    for (id in ids) {
        val maxLinks = minOf(2, nodeCount - 1)
        val linkCount = Arb.int(0..maxLinks).bind()
        val targets = ids.filter { it != id }.shuffled().take(linkCount)
        links[id] = targets
    }

    val nonRoot = ids.drop(1)
    val kbCount = Arb.int(0..nonRoot.size).bind()
    val kbIds = nonRoot.shuffled().take(kbCount)

    TicketGraphSpec(rootId, links, kbIds)
}
