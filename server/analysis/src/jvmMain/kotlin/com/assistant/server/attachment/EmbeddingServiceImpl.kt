package com.assistant.server.attachment

import com.assistant.server.attachment.models.OllamaEmbeddingRequest
import com.assistant.server.attachment.models.OllamaEmbeddingResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ollama-backed embedding service using POST /api/embed (v0.20+).
 * Reads model + endpoint from DB config (ProviderType.EMBEDDING).
 * Requirements: 22.9
 */
class EmbeddingServiceImpl(
    private val httpClient: HttpClient,
    private val configProvider: () -> EmbeddingConfig
) : EmbeddingService {

    data class EmbeddingConfig(
        val model: String = "nomic-embed-text",
        val endpoint: String = "http://localhost:11434"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun embed(text: String): FloatArray? {
        return try {
            val config = configProvider()
            val request = OllamaEmbeddingRequest(
                model = config.model, input = text
            )
            val response = httpClient.post("${config.endpoint}/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            if (!response.status.isSuccess()) return null
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<OllamaEmbeddingResponse>(body)
            val emb = parsed.embeddings.firstOrNull() ?: parsed.embedding
            if (emb.isEmpty()) null else emb.toFloatArray()
        } catch (e: Exception) {
            println("[EmbeddingService] Failed: ${e.message}")
            null
        }
    }
}
