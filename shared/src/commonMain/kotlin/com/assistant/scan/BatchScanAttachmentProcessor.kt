package com.assistant.scan

import com.assistant.jira.JiraAttachment
import kotlinx.coroutines.launch

/** Process attachments for each ticket in a batch — parallel I/O. */
internal suspend fun BatchScanEngine.processBatchAttachments(
    projectKey: String,
    ticketIds: List<String>
) {
    val processor = attachmentProcessor ?: return
    kotlinx.coroutines.coroutineScope {
        ticketIds.map { ticketId ->
            launch {
                processTicketAttachment(projectKey, ticketId, processor)
            }
        }.forEach { it.join() }
    }
}

private suspend fun BatchScanEngine.processTicketAttachment(
    projectKey: String,
    ticketId: String,
    processor: suspend (String, String, List<JiraAttachment>) -> Int
) {
    try {
        val issue = jiraClientProvider().getIssueDetails(ticketId) ?: return
        val attachments = issue.fields.attachment ?: emptyList()
        if (attachments.isEmpty()) return
        val chunks = processor(projectKey, ticketId, attachments)
        logToBoth(projectKey, ticketId, ScanLogStatus.COMPLETED,
            "Processed ${attachments.size} attachments ($chunks chunks)")
    } catch (e: Exception) {
        println("[BatchScanEngine] Attachment processing failed for $ticketId: ${e.message}")
    }
}
