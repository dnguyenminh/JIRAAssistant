package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Sub-task summary and status from a Jira ticket.
 * Requirements: 16.3, 26.1
 */
@Serializable
data class SubTaskInfo(
    val key: String = "",
    val summary: String = "",
    val status: String = ""
)

/**
 * Linked issue info: key, summary, and relationship type.
 * Requirements: 16.4
 */
@Serializable
data class IssueLinkInfo(
    val key: String = "",
    val summary: String = "",
    val relationshipType: String = ""
)

/**
 * Attachment metadata from a Jira ticket.
 * Requirements: 16.5, 1.2, 3.1
 */
@Serializable
data class AttachmentInfo(
    val id: String = "",
    val filename: String = "",
    val mimeType: String = "",
    val size: Long = 0L,
    val content: String = ""  // Download URL from Jira API
)

/**
 * Comment info: author, date, and content.
 * Requirements: 16.6
 */
@Serializable
data class CommentInfo(
    val author: String = "",
    val createdDate: String = "",
    val content: String = ""
)

/**
 * Changelog entry tracking field changes over time.
 * Tracked fields: status, priority, story points, assignee, summary.
 * Requirements: 16.7
 */
@Serializable
data class ChangelogEntry(
    val field: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val changedBy: String = "",
    val changedDate: String = ""
)


/**
 * Detailed content from a linked/blocking/sub-task ticket.
 * Used to enrich the AI prompt with context from related tickets.
 * Requirements: 27.1, 27.2, 11.1, 11.2, 11.5
 */
@Serializable
data class LinkedTicketContent(
    val ticketId: String = "",
    val summary: String = "",
    val description: String = "",
    val status: String = "",
    val linkType: String = "",
    val comments: List<CommentInfo> = emptyList(),
    val attachments: List<AttachmentInfo> = emptyList()
)
