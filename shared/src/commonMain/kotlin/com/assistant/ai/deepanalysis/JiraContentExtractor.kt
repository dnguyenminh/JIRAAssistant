package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Extracts structured content from a Jira ticket for deep analysis.
 *
 * Replaces BatchScanTicketProcessor.fetchTicketContent().
 * Calls JiraClient.getIssueDetails(), parses ADF, extracts sub-tasks,
 * issue links, attachments, comments, changelog, and delegates
 * section classification to SectionClassifier.
 *
 * Requirements: 16.1-16.8
 */
interface JiraContentExtractor {

    /**
     * Extract full structured content from a Jira ticket.
     * @param ticketId Jira issue key (e.g., "PROJ-123")
     * @return StructuredTicketContent with all extracted data
     * @throws IllegalStateException if ticket not found
     */
    suspend fun extract(ticketId: String): StructuredTicketContent
}
