package com.assistant.server.document.models

import com.assistant.server.attachment.models.AttachmentChunk

/**
 * Result of collecting all attachment chunks for a ticket.
 *
 * Contains deduplicated, grouped, and sorted chunks along with
 * any ticket IDs discovered within attachment text content.
 *
 * Requirements: 4.1, 4.3, 4.5, 4.6
 */
data class AttachmentCollectionResult(
    val chunks: List<AttachmentChunk>,
    val discoveredTicketIds: List<String>
)
