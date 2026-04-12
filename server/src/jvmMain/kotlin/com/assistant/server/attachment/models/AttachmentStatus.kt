package com.assistant.server.attachment.models

import kotlinx.serialization.Serializable

/**
 * Attachment processing status for frontend display.
 * Requirements: 22.19, 22.20
 */
@Serializable
data class AttachmentStatusResponse(
    val attachmentId: String,
    val filename: String,
    val status: AttachmentProcessingStatus,
    val chunkCount: Int = 0,
    val error: String? = null
)

@Serializable
enum class AttachmentProcessingStatus {
    CONVERTED, PENDING, FAILED
}
