package com.assistant.frontend.models

import kotlinx.serialization.Serializable

/**
 * Frontend model for Collection Job API response.
 * Matches server-side CollectionJobResponse DTO.
 *
 * Requirements: 13.5, 13.6, 13.7
 */
@Serializable
data class CollectionJobResponse(
    val jobId: String = "",
    val parentTicketId: String = "",
    val jobType: String = "",
    val status: String = "",
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val failedItems: Int = 0,
    val progressPercent: Int = 0,
    val items: List<CollectionJobItemResponse> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val isActive: Boolean
        get() = status == "QUEUED" || status == "RUNNING"

    val jobTypeLabel: String
        get() = when (jobType) {
            "LINKED_TICKET_ANALYSIS" -> "Linked Ticket Analysis"
            "ATTACHMENT_PROCESSING" -> "Attachment Processing"
            else -> jobType
        }

    val summary: String
        get() = "${completedItems}/${totalItems} items, ${failedItems} failed"
}

/**
 * Frontend model for a single item inside a Collection Job.
 *
 * Requirements: 13.5, 13.7
 */
@Serializable
data class CollectionJobItemResponse(
    val itemId: String = "",
    val status: String = "",
    val skipReason: String? = null,
    val errorMessage: String? = null
) {
    val statusIcon: String
        get() = when (status) {
            "PENDING" -> "⏳"
            "PROCESSING" -> "🔄"
            "COMPLETED" -> "✅"
            "FAILED" -> "❌"
            "SKIPPED" -> "⏭️"
            else -> "❓"
        }
}
