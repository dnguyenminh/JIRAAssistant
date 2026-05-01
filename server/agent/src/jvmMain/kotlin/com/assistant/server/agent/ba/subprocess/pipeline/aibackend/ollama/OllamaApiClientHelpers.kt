package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Stateless helper functions for [OllamaApiClient].
 * Extracted to keep OllamaApiClient under 200 lines.
 */
internal object OllamaApiClientHelpers {

    private val log = LoggerFactory.getLogger(OllamaApiClientHelpers::class.java)

    // ── Tool call detection ─────────────────────────────────

    fun isToolCall(json: Json, response: String): Boolean = try {
        val parsed = json.decodeFromString<OllamaChatResponse>(response)
        !parsed.message.toolCalls.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    fun parseToolCall(json: Json, response: String): ToolRequest? = try {
        val parsed = json.decodeFromString<OllamaChatResponse>(response)
        val call = parsed.message.toolCalls?.firstOrNull() ?: return null
        ToolRequest(
            tool = call.function.name,
            params = call.function.argumentsAsStrings()
        )
    } catch (e: Exception) {
        log.debug("Failed to parse tool call: {}", e.message)
        null
    }

    // ── HTTP communication ──────────────────────────────────

    suspend fun sendChatRequest(
        httpClient: HttpClient,
        json: Json,
        baseUrl: String,
        timeoutSeconds: Long,
        chatRequest: OllamaChatRequest
    ): String {
        val requestBody = json.encodeToString(
            OllamaChatRequest.serializer(), chatRequest
        )
        log.debug("Request body length: {} chars", requestBody.length)
        val response = executeChatPost(httpClient, baseUrl, requestBody)
        handleHttpErrors(response, baseUrl)
        return if (chatRequest.stream) {
            readStreamingResponse(json, response)
        } else {
            response.bodyAsText()
        }
    }

    fun checkAvailability(httpClient: HttpClient, baseUrl: String): Boolean =
        try {
            runBlocking {
                val resp = httpClient.get("$baseUrl/")
                resp.status.value in 200..299
            }
        } catch (_: Exception) {
            false
        }

    // ── Response building ───────────────────────────────────

    fun buildAiCliResponse(json: Json, responseBody: String): AiCliResponse {
        val parsed = json.decodeFromString<OllamaChatResponse>(responseBody)
        val text = if (!parsed.message.toolCalls.isNullOrEmpty()) {
            responseBody
        } else {
            parsed.message.content
        }
        return AiCliResponse(response = text, rawJson = responseBody)
    }

    // ── Internal helpers ────────────────────────────────────

    private suspend fun executeChatPost(
        httpClient: HttpClient,
        baseUrl: String,
        body: String
    ): HttpResponse = try {
        httpClient.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    } catch (e: java.net.ConnectException) {
        throw RuntimeException(
            "Cannot connect to Ollama at $baseUrl. Run 'ollama serve'.", e
        )
    } catch (e: Exception) {
        throw RuntimeException(
            "Network error with Ollama at $baseUrl: ${e.message}", e
        )
    }

    private suspend fun handleHttpErrors(
        response: HttpResponse,
        baseUrl: String
    ) {
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            throw RuntimeException(
                "Ollama API error (HTTP ${response.status.value}): $errorBody"
            )
        }
    }

    private suspend fun readStreamingResponse(
        json: Json,
        response: HttpResponse
    ): String {
        val content = StringBuilder()
        var finalResponse: OllamaChatResponse? = null
        val body = response.bodyAsText()

        for (line in body.lines()) {
            val job = kotlin.coroutines.coroutineContext[Job]
            if (job != null && !job.isActive) {
                throw kotlinx.coroutines.CancellationException("Job cancelled during streaming")
            }
            if (line.isBlank()) continue
            val chunk = json.decodeFromString<OllamaChatResponse>(line)
            if (chunk.message.content.isNotEmpty()) {
                content.append(chunk.message.content)
            }
            if (!chunk.message.toolCalls.isNullOrEmpty()) {
                finalResponse = chunk.copy(
                    message = chunk.message.copy(content = content.toString())
                )
            }
            if (chunk.done) {
                finalResponse = chunk.copy(
                    message = chunk.message.copy(content = content.toString())
                )
                break
            }
        }
        val result = finalResponse ?: OllamaChatResponse(
            message = OllamaChatMessage(
                role = "assistant", content = content.toString()
            ),
            done = true
        )
        return json.encodeToString(OllamaChatResponse.serializer(), result)
    }
}
