package com.assistant.server.attachment.models

import com.assistant.jira.JiraAttachment

/**
 * Nhóm attachments cần xử lý cho một ticket trong TicketGraph.
 * Được sắp xếp theo depth (ascending) và relevanceScore (descending).
 * Requirements: 1.2, 3.4
 */
data class TicketAttachmentGroup(
    val ticketId: String,
    val projectKey: String,
    val depth: Int,
    val relevanceScore: Double,
    val attachments: List<JiraAttachment>
)
