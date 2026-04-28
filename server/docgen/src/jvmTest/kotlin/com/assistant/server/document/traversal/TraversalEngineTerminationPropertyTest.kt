package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.IssueLinkInfo
import com.assistant.server.document.models.TraversalConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 19: Early Termination — Data Size Threshold.
 *
 * When total collected data exceeds 3× maxPromptChars, the engine
 * SHALL stop collecting, and earlyTerminated = true.
 *
 * **Validates: Requirements 7.6**
 */
@OptIn(ExperimentalKotest::class)
class TraversalEngineTerminationPropertyTest {

    private val cfg = PropTestConfig(iterations = 50)

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 19: Early Termination")
    fun `earlyTerminated is true when data exceeds 3x maxPromptChars`() {
        runBlocking {
            checkAll(cfg, Arb.int(100..500)) { maxPrompt ->
                val charPerTicket = maxPrompt * 2
                val tickets = buildChainWithLargeContent(
                    chainLength = 10, charPerTicket = charPerTicket
                )
                val config = TraversalConfig(
                    maxDepth = 10, maxTickets = 200,
                    maxPromptChars = maxPrompt
                ).validated()
                val graph = TraversalEngine(
                    FakeTicketFetcher(tickets), config, Semaphore(5)
                ).traverse("CHAIN-0")

                assertTrue(graph.metadata.earlyTerminated) {
                    "Expected earlyTerminated=true for maxPrompt=$maxPrompt, " +
                        "nodes=${graph.nodes.size}"
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 19: Early Termination")
    fun `earlyTerminated is false when data fits within threshold`() {
        runBlocking {
            val tickets = mapOf(
                "SMALL-1" to buildTicketContent(summary = "Hi", description = "Bye")
            )
            val config = TraversalConfig(
                maxDepth = 5, maxTickets = 50, maxPromptChars = 100_000
            ).validated()
            val graph = TraversalEngine(
                FakeTicketFetcher(tickets), config, Semaphore(5)
            ).traverse("SMALL-1")

            assertTrue(!graph.metadata.earlyTerminated) {
                "Expected earlyTerminated=false for small data"
            }
        }
    }

    /** Build a linear chain of tickets each with large content. */
    private fun buildChainWithLargeContent(
        chainLength: Int,
        charPerTicket: Int
    ): Map<String, com.assistant.ai.deepanalysis.models.StructuredTicketContent> {
        val tickets = mutableMapOf<String, com.assistant.ai.deepanalysis.models.StructuredTicketContent>()
        for (i in 0 until chainLength) {
            val nextId = if (i < chainLength - 1) "CHAIN-${i + 1}" else ""
            val links = if (nextId.isNotBlank()) {
                listOf(IssueLinkInfo(nextId, "Next", "relates to"))
            } else emptyList()
            tickets["CHAIN-$i"] = buildLargeTicketContent(charPerTicket)
                .copy(issueLinks = links)
        }
        return tickets
    }
}
