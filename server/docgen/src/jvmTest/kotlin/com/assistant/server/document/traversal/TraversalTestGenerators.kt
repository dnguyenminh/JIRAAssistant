package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.IssueLinkInfo
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.ai.deepanalysis.models.SubTaskInfo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Shared Kotest generators for TraversalEngine property tests.
 */

/** Pair of (ticketMap, rootId) representing a random linked graph. */
data class LinkedGraph(
    val tickets: Map<String, StructuredTicketContent>,
    val rootId: String
)

/**
 * Generates a random linked graph of 2-15 tickets with cycles,
 * mixed relationship types (issue links, sub-tasks, parent).
 */
fun arbLinkedGraph(): Arb<LinkedGraph> = arbitrary {
    val nodeCount = Arb.int(2..15).bind()
    val ids = (1..nodeCount).map { "TEST-$it" }
    val rootId = ids.first()

    val tickets = mutableMapOf<String, StructuredTicketContent>()
    for ((index, id) in ids.withIndex()) {
        val linkCount = Arb.int(0..3).bind()
        val links = (0 until linkCount).mapNotNull {
            val targetIdx = Arb.int(0 until nodeCount).bind()
            if (ids[targetIdx] != id) {
                val relType = Arb.of("blocks", "relates to", "cloned by").bind()
                IssueLinkInfo(ids[targetIdx], "Link", relType)
            } else null
        }
        val subCount = Arb.int(0..2).bind()
        val subs = (0 until subCount).mapNotNull {
            val targetIdx = Arb.int(0 until nodeCount).bind()
            if (ids[targetIdx] != id) {
                SubTaskInfo(ids[targetIdx], "Sub", "Open")
            } else null
        }
        val parentKey = if (index > 0 && Arb.boolean().bind()) {
            ids[Arb.int(0 until index).bind()]
        } else ""

        tickets[id] = buildTicketContent(
            summary = "Ticket $id",
            description = "Desc for $id",
            parentKey = parentKey,
            issueLinks = links,
            subTasks = subs
        )
    }
    LinkedGraph(tickets, rootId)
}
