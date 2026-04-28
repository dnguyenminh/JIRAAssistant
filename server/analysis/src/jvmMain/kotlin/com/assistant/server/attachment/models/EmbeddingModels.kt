package com.assistant.server.attachment.models

import kotlinx.serialization.Serializable

/**
 * Ollama embedding API request/response models.
 * Ollama v0.20+: POST /api/embed with `input` field.
 * Requirements: 22.9
 */
@Serializable
data class OllamaEmbeddingRequest(
    val model: String = "nomic-embed-text",
    val input: String = ""
)

@Serializable
data class OllamaEmbeddingResponse(
    val embeddings: List<List<Float>> = emptyList(),
    // Legacy field for backward compatibility
    val embedding: List<Float> = emptyList()
)
