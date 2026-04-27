package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Collects all related ticket keys from a StructuredTicketContent.
 * Sources: issue links, sub-tasks, parent ticket, comment mentions.
 *
 * Requirements: 26.1
 */
object RelatedTicketCollector {

    /** Jira ticket key pattern: PROJECT-123 */
    private val TICKET_PATTERN = Regex("[A-Z][A-Z0-9]+-\\d+")

    /**
     * Collect all unique related ticket keys from content.
     * Excludes empty keys and the ticket itself.
     */
    fun collect(
        ticketId: String,
        content: StructuredTicketContent
    ): Set<String> {
        val related = mutableSetOf<String>()
        addIssueLinkKeys(content, related)
        addSubTaskKeys(content, related)
        addParentKey(content, related)
        addCommentMentions(content, related)
        related.remove(ticketId)
        return related
    }

    private fun addIssueLinkKeys(
        content: StructuredTicketContent,
        target: MutableSet<String>
    ) {
        content.issueLinks
            .filter { it.key.isNotBlank() }
            .forEach { target.add(it.key) }
    }

    private fun addSubTaskKeys(
        content: StructuredTicketContent,
        target: MutableSet<String>
    ) {
        content.subTasks
            .filter { it.key.isNotBlank() }
            .forEach { target.add(it.key) }
    }

    private fun addParentKey(
        content: StructuredTicketContent,
        target: MutableSet<String>
    ) {
        if (content.parentKey.isNotBlank()) {
            target.add(content.parentKey)
        }
    }

    private fun addCommentMentions(
        content: StructuredTicketContent,
        target: MutableSet<String>
    ) {
        content.comments.forEach { comment ->
            TICKET_PATTERN.findAll(comment.content)
                .map { it.value }
                .forEach { target.add(it) }
        }
    }
}
