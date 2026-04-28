package com.assistant.server.attachment.models

/**
 * Constants for chunk type classification in VectorStore.
 * Each type represents a different source of knowledge data.
 * Requirements: 12.2, 13.2, 14.2
 */
object ChunkType {
    const val ATTACHMENT = "ATTACHMENT"
    const val TICKET = "TICKET"
    const val RELATIONSHIP = "RELATIONSHIP"
    const val ANALYSIS = "ANALYSIS"
    const val EVOLUTION = "EVOLUTION"
    const val CLUSTER = "CLUSTER"
    const val CONFLUENCE = "CONFLUENCE"
}
