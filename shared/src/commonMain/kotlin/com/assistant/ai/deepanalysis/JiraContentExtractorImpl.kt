package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.LinkedTicketContent
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.jira.JiraClient
import com.assistant.jira.JiraIssue

/**
 * Implementation of JiraContentExtractor.
 *
 * Calls JiraClient.getIssueDetails() to fetch full ticket data,
 * maps all fields to StructuredTicketContent, and delegates
 * description classification to SectionClassifier.
 *
 * Requirements: 16.1-16.8
 */
class JiraContentExtractorImpl(
    private val jiraClientProvider: () -> JiraClient,
    private val sectionClassifier: SectionClassifier
) : JiraContentExtractor {

    override suspend fun extract(ticketId: String): StructuredTicketContent {
        val issue = fetchIssue(ticketId)
        val base = buildStructuredContent(issue)
        val linked = fetchLinkedContents(base)
        return base.copy(linkedTicketContents = linked)
    }

    /** Fetch issue from Jira, throw if not found. */
    private suspend fun fetchIssue(ticketId: String): JiraIssue {
        val client = jiraClientProvider()
        return client.getIssueDetails(ticketId)
            ?: throw IllegalStateException(
                "Ticket $ticketId not found in Jira"
            )
    }

    /** Req 27.1 — Fetch content of linked tickets. */
    private suspend fun fetchLinkedContents(
        base: StructuredTicketContent
    ): List<LinkedTicketContent> {
        val client = jiraClientProvider()
        val keys = collectLinkedKeys(base)
        return keys.mapNotNull { (key, linkType) ->
            fetchOneLinked(client, key, linkType)
        }
    }

    /** Collect linked keys prioritized: blocking > linked > sub-tasks. */
    private fun collectLinkedKeys(
        base: StructuredTicketContent
    ): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (link in base.issueLinks) {
            val type = link.relationshipType.lowercase()
            if ("block" in type) result.add(0, link.key to link.relationshipType)
            else result.add(link.key to link.relationshipType)
        }
        for (sub in base.subTasks) {
            result.add(sub.key to "sub-task")
        }
        if (base.parentKey.isNotBlank()) {
            result.add(0, base.parentKey to "parent")
        }
        return result
    }

    /** Fetch one linked ticket, return null on failure. Req 27.5 */
    private suspend fun fetchOneLinked(
        client: JiraClient, key: String, linkType: String
    ): LinkedTicketContent? {
        return try {
            val issue = client.getIssueDetails(key) ?: return null
            LinkedTicketContent(
                ticketId = key,
                summary = issue.fields.summary,
                description = issue.fields.descriptionText.take(500),
                status = issue.fields.status?.name ?: "",
                linkType = linkType,
                comments = JiraFieldMappers.mapComments(issue.fields.comment),
                attachments = JiraFieldMappers.mapAttachments(issue.fields.attachment)
            )
        } catch (_: Exception) { null }
    }

    /** Req 16.8, 26.1 — Consolidate all data into StructuredTicketContent. */
    private fun buildStructuredContent(
        issue: JiraIssue
    ): StructuredTicketContent {
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
            components = mapComponentNames(fields),
            parentKey = fields.parent?.key ?: "",
            subTasks = JiraFieldMappers.mapSubTasks(fields.subtasks),
            issueLinks = JiraFieldMappers.mapIssueLinks(fields.issuelinks),
            attachments = JiraFieldMappers.mapAttachments(fields.attachment),
            comments = JiraFieldMappers.mapComments(fields.comment),
            changelog = JiraFieldMappers.mapChangelog(issue.changelog),
            classifiedContent = classified
        )
    }

    private fun mapComponentNames(
        fields: com.assistant.jira.JiraIssueFields
    ): List<String> {
        return fields.components?.map { it.name } ?: emptyList()
    }
}
