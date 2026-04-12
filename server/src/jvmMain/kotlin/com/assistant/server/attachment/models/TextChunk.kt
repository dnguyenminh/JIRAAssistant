package com.assistant.server.attachment.models

/**
 * A chunk of text with its index and estimated token count.
 * Requirements: 22.10
 */
data class TextChunk(
    val index: Int,
    val text: String,
    val tokenCount: Int
)
