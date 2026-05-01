package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.jira.JiraClient
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import org.slf4j.LoggerFactory

/**
 * TicketFetcher that checks Knowledge Base before calling Jira API.
 *
 * If KB has a record with a non-blank requirementSummary for the ticket,
 * constructs [StructuredTicketContent] from KB data without making a
 * Jira API call. Falls back to Jira API via parent [TicketFetcher]
 * when KB has no data.
 *
 * Requirements: KB-First strategy for BRD generation.
 */
class KBFirstTicketFetcher(
    jiraClient: JiraClient,
    sectionClassifier: SectionClassifier,
    private val kbRepository: KBRepository
) : TicketFetcher(jiraClient, sectionClassifier) {

    private val logger = LoggerFactory.getLogger(KBFirstTicketFetcher::class.java)

    override suspend fun fetch(ticketId: String): FetchResult {
        val kbRecord = kbRepository.findByTicketId(ticketId)
        if (kbRecord != null && kbRecord.requirementSummary.isNotBlank()) {
            logger.info("KB hit for ticket {}, skipping Jira API call", ticketId)
            return FetchResult.Success(buildFromKB(kbRecord))
        }
        logger.debug("KB miss for ticket {}, falling back to Jira API", ticketId)
        val result = super.fetch(ticketId)
        if (result is FetchResult.Success) {
            cacheToKB(ticketId, result.content)
        }
        return result
    }

    /** Cache fetched ticket data to KB for future lookups. */
    private suspend fun cacheToKB(ticketId: String, content: StructuredTicketContent) {
        try {
            val record = KBRecord(
                ticketId = ticketId,
                requirementSummary = content.summary,
                evolutionHistory = emptyList(),
                scrumPoints = content.storyPoints ?: 0.0,
                confidenceScore = 0.0,
                rationale = content.description.take(500),
                similarTicketRefs = emptyList(),
                timestamp = System.currentTimeMillis().toString()
            )
            val saved = kbRepository.save(record)
            if (saved) {
                logger.info("Cached ticket {} to KB (summary={})", ticketId, content.summary.take(50))
            } else {
                logger.warn("KB save returned false for ticket {}", ticketId)
            }
        } catch (e: Exception) {
            logger.warn("Failed to cache ticket {} to KB: {} — {}", ticketId, e::class.simpleName, e.message)
        }
    }

    /** Build minimal StructuredTicketContent from a KB record. */
    private fun buildFromKB(record: KBRecord): StructuredTicketContent {
        return StructuredTicketContent(
            summary = record.requirementSummary,
            description = record.rationale,
            storyPoints = record.scrumPoints,
            createdDate = record.timestamp,
            updatedDate = record.timestamp
        )
    }
}
