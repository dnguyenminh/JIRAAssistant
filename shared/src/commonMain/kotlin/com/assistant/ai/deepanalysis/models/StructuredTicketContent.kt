package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Consolidated structured content extracted from a Jira ticket.
 * This is an intermediate model — NOT serialized to KB.
 *
 * Aggregates all data from Jira REST API: metadata, sub-tasks,
 * issue links, attachments, comments, changelog, and classified
 * description sections.
 *
 * Requirements: 16.1-16.8
 */
@Serializable
data class StructuredTicketContent(
    // Core fields (Req 16.1)
    val summary: String = "",
    val description: String = "",
    val status: String = "",
    val priority: String = "",
    val storyPoints: Double? = null,
    val issueType: String = "",
    val assignee: String = "",
    val reporter: String = "",
    val createdDate: String = "",
    val updatedDate: String = "",
    val labels: List<String> = emptyList(),
    val components: List<String> = emptyList(),

    // Parent ticket key (Req 26.1)
    val parentKey: String = "",

    // Sub-tasks (Req 16.3)
    val subTasks: List<SubTaskInfo> = emptyList(),

    // Issue links (Req 16.4)
    val issueLinks: List<IssueLinkInfo> = emptyList(),

    // Attachments metadata (Req 16.5)
    val attachments: List<AttachmentInfo> = emptyList(),

    // Comments — max 20 most recent (Req 16.6)
    val comments: List<CommentInfo> = emptyList(),

    // Changelog — tracked field changes (Req 16.7)
    val changelog: List<ChangelogEntry> = emptyList(),

    // Classified description sections (Req 17.1-17.6)
    val classifiedContent: ClassifiedContent = ClassifiedContent(),

    // Linked ticket detailed content (Req 27.1, 27.2)
    val linkedTicketContents: List<LinkedTicketContent> = emptyList()
)
