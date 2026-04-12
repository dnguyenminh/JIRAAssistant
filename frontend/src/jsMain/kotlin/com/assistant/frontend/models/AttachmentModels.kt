package com.assistant.frontend.models

import kotlinx.serialization.Serializable

/** Attachment processing status for a ticket's attachment. */
@Serializable
data class AttachmentStatusDTO(
    val attachmentId: String = "",
    val filename: String = "",
    val status: String = "PENDING",
    val chunkCount: Int = 0,
    val error: String? = null
)
