package com.assistant.server.attachment

/**
 * Creates embedding vectors for text via an LLM embedding model.
 * Requirements: 22.9
 */
interface EmbeddingService {
    /** Generate embedding vector for text. Returns null on failure. */
    suspend fun embed(text: String): FloatArray?
}
