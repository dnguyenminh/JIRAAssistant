package com.assistant.server.attachment.models

import kotlinx.serialization.Serializable

/**
 * A chunk of text extracted from a Jira attachment, with its embedding vector.
 * Requirements: 22.10, 22.12, 12.2, 13.2, 14.2
 */
@Serializable
data class AttachmentChunk(
    val id: Long = 0,
    val ticketId: String,
    val attachmentId: String,
    val filename: String,
    val chunkIndex: Int,
    val chunkText: String,
    val embedding: List<Float>,
    val createdAt: String,
    val chunkType: String = ChunkType.ATTACHMENT
)
