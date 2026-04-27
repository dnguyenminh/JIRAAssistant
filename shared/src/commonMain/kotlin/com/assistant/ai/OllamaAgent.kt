package com.assistant.ai

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Local AI Agent implementation using Ollama's REST API.
 */
class OllamaAgent(
    private val httpClient: HttpClient,
    private val model: String = "llama3", // default to llama3
    private val endpoint: String = "http://localhost:11434"
) : AIAgent {

    @Serializable
    private data class OllamaRequest(val model: String, val prompt: String, val stream: Boolean = false)

    @Serializable
    private data class OllamaResponse(val response: String)

    @Serializable
    private data class OllamaTagsResponse(val models: List<OllamaModel> = emptyList())

    @Serializable
    private data class OllamaModel(val name: String = "", val model: String = "")

    /**
     * Lightweight connection test using GET /api/tags (lists available models).
     * Returns a success message with model count, or null on failure.
     */
    suspend fun testConnection(): String? {
        return try {
            val httpResponse = httpClient.get("$endpoint/api/tags")
            if (httpResponse.status.isSuccess()) {
                val body = httpResponse.bodyAsText()
                val json = Json { ignoreUnknownKeys = true }
                val tags = json.decodeFromString<OllamaTagsResponse>(body)
                val modelNames = tags.models.map { it.name }
                "Connected — ${modelNames.size} models: ${modelNames.take(3).joinToString(", ")}${if (modelNames.size > 3) "..." else ""}"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        return try {
            val fullPrompt = buildFullPrompt(prompt, context)
            val httpResponse = httpClient.post("$endpoint/api/generate") {
                contentType(ContentType.Application.Json)
                val jsonBody = Json.encodeToString(OllamaRequest.serializer(), OllamaRequest(model, fullPrompt))
                setBody(jsonBody)
            }

            if (httpResponse.status.isSuccess()) {
                val responseBody = httpResponse.bodyAsText()
                val json = Json { ignoreUnknownKeys = true }
                // Ollama may return NDJSON (multiple JSON objects per line)
                // Concatenate all "response" fields
                val fullResponse = responseBody.lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        try {
                            json.decodeFromString<OllamaResponse>(line).response
                        } catch (_: Exception) { null }
                    }
                    .joinToString("")
                AIResult.Success(fullResponse)
            } else {
                AIResult.Failure("Ollama HTTP Error: ${httpResponse.status}")
            }
        } catch (e: Exception) {
            AIResult.Failure("Ollama Connection Error: ${e.message}")
        }
    }

    /**
     * Streaming analysis: sends prompt with stream=true, reads NDJSON lines,
     * accumulates response text, reports progress via callback.
     *
     * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
     */
    suspend fun analyzeStreaming(
        prompt: String,
        onProgress: (Int) -> Unit,
        context: AIContext? = null
    ): AIResult {
        return try {
            val fullPrompt = buildFullPrompt(prompt, context)
            val body = Json.encodeToString(
                OllamaRequest.serializer(),
                OllamaRequest(model, fullPrompt, stream = true)
            )
            executeStreamingRequest(body, onProgress)
        } catch (e: Exception) {
            AIResult.Failure("Ollama Streaming Error: ${e.message}")
        }
    }

    private suspend fun executeStreamingRequest(
        body: String,
        onProgress: (Int) -> Unit
    ): AIResult {
        val statement = httpClient.preparePost("$endpoint/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return statement.execute { response ->
            if (!response.status.isSuccess()) {
                return@execute AIResult.Failure("Ollama HTTP Error: ${response.status}")
            }
            val channel: ByteReadChannel = response.bodyAsChannel()
            val reader = OllamaStreamReader()
            val result = reader.readStream(channel, onProgress)
            AIResult.Success(result)
        }
    }

    private fun buildFullPrompt(prompt: String, context: AIContext?): String {
        if (context == null) return prompt
        val ctxText = context.tickets.joinToString("\n") {
            "[${it.id}] ${it.summary}: ${it.description}"
        }
        return "Context:\n$ctxText\n\nUser Request: $prompt"
    }

    override fun getAgentName(): String = "Local Ollama - $model"
}
