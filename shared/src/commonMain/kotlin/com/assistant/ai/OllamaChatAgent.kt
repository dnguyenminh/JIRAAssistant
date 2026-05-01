package com.assistant.ai

import com.assistant.ai.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * AI Agent using Ollama's /api/chat endpoint with native tool calling.
 * Returns structured tool_calls instead of requiring text-based parsing.
 * Falls back to text response when no tools match.
 */
class OllamaChatAgent(
    private val httpClient: HttpClient,
    private val model: String = "qwen2.5",
    private val endpoint: String = "http://localhost:11434"
) : AIAgent {

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    /**
     * Send a chat request with tool definitions.
     * Returns OllamaChatResult with either tool_calls or text content.
     */
    suspend fun chat(
        messages: List<OllamaChatMessage>,
        tools: List<OllamaChatToolDef> = emptyList()
    ): OllamaChatResult {
        return try {
            val request = OllamaChatRequest(
                model = model, messages = messages,
                tools = tools, stream = false
            )
            val body = json.encodeToString(request)
            val httpResponse = httpClient.post("$endpoint/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!httpResponse.status.isSuccess()) {
                return OllamaChatResult.Error("HTTP ${httpResponse.status}")
            }
            parseResponse(httpResponse.bodyAsText())
        } catch (e: Exception) {
            OllamaChatResult.Error("Connection error: ${e.message}")
        }
    }

    private fun parseResponse(responseBody: String): OllamaChatResult {
        val response = json.decodeFromString<OllamaChatResponse>(responseBody)
        val toolCalls = response.message.toolCalls
        if (!toolCalls.isNullOrEmpty()) {
            return OllamaChatResult.ToolCalls(
                calls = toolCalls, rawContent = response.message.content
            )
        }
        return OllamaChatResult.TextResponse(response.message.content)
    }

    /** AIAgent.analyze — delegates to chat() without tools. */
    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        val messages = buildMessages(prompt, context)
        return when (val result = chat(messages)) {
            is OllamaChatResult.TextResponse -> AIResult.Success(result.content)
            is OllamaChatResult.ToolCalls -> AIResult.Success(result.rawContent)
            is OllamaChatResult.Error -> AIResult.Failure(result.message)
        }
    }

    private fun buildMessages(
        prompt: String, context: AIContext?
    ): List<OllamaChatMessage> {
        val userContent = if (context != null) {
            val ctx = context.tickets.joinToString("\n") {
                "[${it.id}] ${it.summary}: ${it.description}"
            }
            "Context:\n$ctx\n\nUser Request: $prompt"
        } else prompt
        return listOf(OllamaChatMessage(role = "user", content = userContent))
    }

    override fun getAgentName(): String = "Ollama Chat - $model"
}

/** Sealed result from OllamaChatAgent.chat(). */
sealed class OllamaChatResult {
    data class TextResponse(val content: String) : OllamaChatResult()
    data class ToolCalls(
        val calls: List<OllamaChatToolCall>,
        val rawContent: String = ""
    ) : OllamaChatResult()
    data class Error(val message: String) : OllamaChatResult()
}
