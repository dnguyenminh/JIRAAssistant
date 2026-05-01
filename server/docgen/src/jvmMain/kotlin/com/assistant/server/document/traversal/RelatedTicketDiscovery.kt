package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.document.extraction.TicketIdExtractor
import com.assistant.server.document.models.RelationshipType

/**
 * Discovered ticket reference with its relationship type and priority.
 */
data class DiscoveredTicket(
    val ticketId: String,
    val relationshipType: RelationshipType,
    /** Lower = higher priority. Used for BFS ordering. */
    val priority: Int
)

/**
 * Extracts related ticket IDs from a [StructuredTicketContent] with
 * priority ordering: parent > blocking > other links > sub-tasks > text refs.
 *
 * Requirements: 1.1, 1.4, 1.7
 */
object RelatedTicketDiscovery {

    private const val PRIORITY_PARENT = 0
    private const val PRIORITY_BLOCKING = 1
    private const val PRIORITY_OTHER_LINK = 2
    private const val PRIORITY_SUBTASK = 3
    private const val PRIORITY_TEXT_REF = 4

    /**
     * Discover all related ticket IDs from [content].
     *
     * @param content Structured content of the source ticket.
     * @param sourceTicketId The ticket ID being processed (excluded from results).
     * @param visitedIds Already-visited ticket IDs to exclude.
     * @param projectScope Allowed project keys (empty = all).
     * @return Discovered tickets sorted by priority (parent first).
     */
    fun discover(
        content: StructuredTicketContent,
        sourceTicketId: String,
        visitedIds: Set<String>,
        projectScope: List<String>
    ): List<DiscoveredTicket> {
        val excludeIds = visitedIds + sourceTicketId
        val results = mutableListOf<DiscoveredTicket>()

        addParent(content, excludeIds, results)
        addIssueLinks(content, excludeIds, results)
        addSubTasks(content, excludeIds, results)
        addTextReferences(content, excludeIds, projectScope, results)

        return results
            .distinctBy { it.ticketId }
            .sortedBy { it.priority }
    }

    private fun addParent(
        content: StructuredTicketContent,
        excludeIds: Set<String>,
        results: MutableList<DiscoveredTicket>
    ) {
        val parentKey = content.parentKey
        if (parentKey.isNotBlank() && parentKey !in excludeIds) {
            results.add(DiscoveredTicket(parentKey, RelationshipType.PARENT, PRIORITY_PARENT))
        }
    }

    private fun addIssueLinks(
        content: StructuredTicketContent,
        excludeIds: Set<String>,
        results: MutableList<DiscoveredTicket>
    ) {
        for (link in content.issueLinks) {
            if (link.key.isBlank() || link.key in excludeIds) continue
            val isBlocking = isBlockingLink(link.relationshipType)
            val priority = if (isBlocking) PRIORITY_BLOCKING else PRIORITY_OTHER_LINK
            results.add(DiscoveredTicket(link.key, RelationshipType.ISSUE_LINK, priority))
        }
    }

    private fun addSubTasks(
        content: StructuredTicketContent,
        excludeIds: Set<String>,
        results: MutableList<DiscoveredTicket>
    ) {
        for (sub in content.subTasks) {
            if (sub.key.isBlank() || sub.key in excludeIds) continue
            results.add(DiscoveredTicket(sub.key, RelationshipType.SUB_TASK, PRIORITY_SUBTASK))
        }
    }

    private fun addTextReferences(
        content: StructuredTicketContent,
        excludeIds: Set<String>,
        projectScope: List<String>,
        results: MutableList<DiscoveredTicket>
    ) {
        val existingIds = results.map { it.ticketId }.toSet()
        val allExclude = excludeIds + existingIds
        val text = buildTextForExtraction(content)
        val textIds = TicketIdExtractor.extract(text, allExclude, projectScope)
        for (id in textIds) {
            results.add(DiscoveredTicket(id, RelationshipType.TEXT_REFERENCE, PRIORITY_TEXT_REF))
        }
    }

    /** Build combined text from summary, description, and comments. */
    private fun buildTextForExtraction(
        content: StructuredTicketContent
    ): String {
        val sb = StringBuilder()
        sb.appendLine(content.summary)
        sb.appendLine(content.description)
        for (comment in content.comments) {
            sb.appendLine(comment.content)
        }
        return sb.toString()
    }

    /** Check if a relationship type indicates a blocking link. */
    private fun isBlockingLink(relationshipType: String): Boolean {
        val lower = relationshipType.lowercase()
        return "block" in lower
    }
}
