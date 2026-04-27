package com.assistant.server.attachment

import com.assistant.ai.deepanalysis.models.AttachmentInfo
import com.assistant.jira.JiraAttachment

/**
 * Convert AttachmentInfo (from StructuredTicketContent) to JiraAttachment
 * (for AttachmentPipeline processing).
 * Requirements: 4.1
 */
fun AttachmentInfo.toJiraAttachment(): JiraAttachment = JiraAttachment(
    id = id,
    filename = filename,
    mimeType = mimeType,
    size = size,
    content = content
)
