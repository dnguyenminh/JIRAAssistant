package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.AiApiClient
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.AiBackend
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Ollama REST API client implementing [AiApiClient].
 *
 * Supports stateless prompts, multi-turn sessions with conversation
 * history, native tool calling, and NDJSON streaming responses.
 */
class OllamaApiClient(
    override val baseUrl: String = "http://localhost:11434",
    override val model: String = "batiai/gemma4-e2b:q4",
    private val tools: List<OllamaTool> = emptyList(),
    private val timeoutSeconds: Long = 300,
    private val streaming: Boolean = true,
    internal val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutSeconds * 1000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = timeoutSeconds * 1000
        }
    }
) : AiApiClient {

    private val log = LoggerFactory.getLogger(OllamaApiClient::class.java)
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    internal val conversationHistory = mutableListOf<OllamaChatMessage>()
    private var sessionActive = false

    override val displayName: String get() = "Ollama ($model)"

    @Volatile
    private var cancelled = false

    /** Cancel any in-flight HTTP request by closing the client. */
    fun cancel() {
        log.info("Cancelling Ollama HTTP client")
        cancelled = true
        try { httpClient.close() } catch (_: Exception) {}
    }

    /** Check if cancelled before each HTTP call. */
    internal fun checkCancelled() {
        if (cancelled) throw kotlinx.coroutines.CancellationException("Job cancelled")
    }

    /** Suspend-aware cancel check — also checks coroutine cancellation. */
    internal suspend fun ensureCancelledOrActive() {
        val ctx = kotlin.coroutines.coroutineContext
        val job = ctx[kotlinx.coroutines.Job]
        if (job != null && !job.isActive) {
            throw kotlinx.coroutines.CancellationException("Job cancelled")
        }
        checkCancelled()
    }

    // ── Stateless mode ──────────────────────────────────────

    override suspend fun sendPrompt(prompt: String): AiCliResponse {
        ensureCancelledOrActive()
        log.info("Sending stateless prompt to Ollama ($model)")
        val request = buildChatRequest(
            messages = listOf(OllamaChatMessage(role = "user", content = prompt))
        )
        val body = OllamaApiClientHelpers.sendChatRequest(
            httpClient, json, baseUrl, timeoutSeconds, request
        )
        return OllamaApiClientHelpers.buildAiCliResponse(json, body)
    }

    // ── Session mode ────────────────────────────────────────

    override fun startSession() {
        log.info("Starting Ollama session")
        conversationHistory.clear()
        sessionActive = true
    }

    override suspend fun sendMessage(message: String): AiCliResponse {
        ensureCancelledOrActive()
        log.info("Sending message in session, history={}", conversationHistory.size)
        conversationHistory.add(OllamaChatMessage(role = "user", content = message))
        val request = buildChatRequest(messages = conversationHistory.toList())
        val body = OllamaApiClientHelpers.sendChatRequest(
            httpClient, json, baseUrl, timeoutSeconds, request
        )
        val parsed = json.decodeFromString<OllamaChatResponse>(body)
        conversationHistory.add(parsed.message)
        return OllamaApiClientHelpers.buildAiCliResponse(json, body)
    }

    override fun endSession() {
        log.info("Ending Ollama session")
        conversationHistory.clear()
        sessionActive = false
    }

    override fun isSessionActive(): Boolean = sessionActive

    // ── Tool handling ───────────────────────────────────────

    override fun isToolCall(response: String): Boolean =
        OllamaApiClientHelpers.isToolCall(json, response)

    override fun parseToolCall(response: String): ToolRequest? =
        OllamaApiClientHelpers.parseToolCall(json, response)

    // ── Tool result ─────────────────────────────────────────

    suspend fun sendToolResult(toolName: String, result: String): AiCliResponse {
        ensureCancelledOrActive()
        log.info("Sending tool result for '{}'", toolName)
        conversationHistory.add(OllamaChatMessage(role = "tool", content = result))
        val request = buildChatRequest(messages = conversationHistory.toList())
        val body = OllamaApiClientHelpers.sendChatRequest(
            httpClient, json, baseUrl, timeoutSeconds, request
        )
        val parsed = json.decodeFromString<OllamaChatResponse>(body)
        conversationHistory.add(parsed.message)
        return OllamaApiClientHelpers.buildAiCliResponse(json, body)
    }

    // ── Availability ────────────────────────────────────────

    override fun isInstalled(): Boolean =
        OllamaApiClientHelpers.checkAvailability(httpClient, baseUrl)

    override fun getInstallInstructions(): String = buildString {
        appendLine("Ollama Installation:")
        appendLine("1. Install: curl -fsSL https://ollama.com/install.sh | sh")
        appendLine("2. Pull model: ollama pull $model")
        appendLine("3. Start server: ollama serve")
        appendLine("Server will be at $baseUrl")
    }

    // ── Internal ────────────────────────────────────────────

    private fun buildChatRequest(
        messages: List<OllamaChatMessage>
    ): OllamaChatRequest = OllamaChatRequest(
        model = model,
        messages = messages,
        stream = streaming,
        tools = tools.ifEmpty { null },
        options = OllamaOptions(numPredict = 8192)
    )
}
