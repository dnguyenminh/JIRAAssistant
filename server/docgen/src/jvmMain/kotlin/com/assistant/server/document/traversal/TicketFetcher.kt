package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.JiraFieldMappers
import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.jira.JiraClient
import com.assistant.jira.JiraIssue
import org.slf4j.LoggerFactory

/**
 * Result of fetching a single ticket from Jira API.
 */
sealed class FetchResult {
    data class Success(val content: StructuredTicketContent) : FetchResult()
    data class PermissionDenied(val ticketId: String) : FetchResult()
    data class Failed(val ticketId: String, val error: String) : FetchResult()
}

/**
 * Fetches tickets from Jira API and maps to [StructuredTicketContent].
 *
 * Handles errors gracefully: null response → Failed, exceptions → Failed.
 * Subclasses or wrappers can override [fetch] for 403/429 detection
 * when using a JiraClient that exposes HTTP status codes.
 *
 * Requirements: 1.4, 1.6, 10.3
 */
open class TicketFetcher(
    private val jiraClient: JiraClient,
    private val sectionClassifier: SectionClassifier
) {
    private val logger = LoggerFactory.getLogger(TicketFetcher::class.java)

    /**
     * Fetch a ticket from Jira API.
     * Returns [FetchResult.Success] with mapped content, or [FetchResult.Failed].
     */
    open suspend fun fetch(ticketId: String): FetchResult {
        return try {
            val issue = jiraClient.getIssueDetails(ticketId)
            if (issue == null) {
                logger.warn("Ticket {} not found or inaccessible", ticketId)
                FetchResult.Failed(ticketId, "Not found or inaccessible")
            } else {
                FetchResult.Success(mapToContent(issue))
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch ticket {}: {}", ticketId, e.message)
            classifyException(ticketId, e)
        }
    }

    /** Classify exception into appropriate FetchResult. */
    private fun classifyException(ticketId: String, e: Exception): FetchResult {
        val message = e.message?.lowercase() ?: ""
        return when {
            "403" in message || "forbidden" in message -> {
                logger.warn("Skipped {}: insufficient permissions (403)", ticketId)
                FetchResult.PermissionDenied(ticketId)
            }
            else -> FetchResult.Failed(ticketId, e.message ?: "Unknown error")
        }
    }

    /** Map JiraIssue to StructuredTicketContent using JiraFieldMappers. */
    internal fun mapToContent(issue: JiraIssue): StructuredTicketContent {
        val fields = issue.fields
        val descriptionText = fields.descriptionText
        val classified = sectionClassifier.classify(descriptionText)
        return StructuredTicketContent(
            summary = fields.summary,
            description = descriptionText,
            status = fields.status?.name ?: "",
            priority = fields.priority?.name ?: "",
            storyPoints = fields.storyPoints,
            issueType = fields.issuetype?.name ?: "",
            assignee = fields.assignee?.displayName ?: "",
            reporter = fields.reporter?.displayName ?: "",
            createdDate = fields.created ?: "",
            updatedDate = fields.updated ?: "",
            labels = fields.labels ?: emptyList(),
            components = fields.components?.map { it.name } ?: emptyList(),
            parentKey = fields.parent?.key ?: "",
            subTasks = JiraFieldMappers.mapSubTasks(fields.subtasks),
            issueLinks = JiraFieldMappers.mapIssueLinks(fields.issuelinks),
            attachments = JiraFieldMappers.mapAttachments(fields.attachment),
            comments = JiraFieldMappers.mapComments(fields.comment),
            changelog = JiraFieldMappers.mapChangelog(issue.changelog),
            classifiedContent = classified
        )
    }
}
