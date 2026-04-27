package com.assistant.ai

import com.assistant.ai.models.OllamaStreamLine
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for OllamaAgent streaming behavior.
 *
 * Tests cover:
 * - analyzeStreaming() success with full text accumulation
 * - analyzeStreaming() failure on network error
 * - Default estimatedTotalLines = 1000 (via OllamaStreamReader)
 * - Existing analyze() uses stream: false (unchanged)
 *
 * **Validates: Requirements 1.1, 1.4, 3.1, 4.2**
 */
class OllamaAgentStreamingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildNdjsonBody(
        texts: List<String>
    ): String {
        val sb = StringBuilder()
        for (text in texts) {
            val line = OllamaStreamLine(
                model = "llama3",
                response = text,
                done = false,
                createdAt = "2025-01-01T00:00:00Z"
            )
            sb.appendLine(json.encodeToString(line))
        }
        val done = OllamaStreamLine(
            model = "llama3",
            response = "",
            done = true,
            doneReason = "stop",
            createdAt = "2025-01-01T00:00:00Z"
        )
        sb.appendLine(json.encodeToString(done))
        return sb.toString()
    }

    private fun createMockClient(
        handler: MockRequestHandler
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    /**
     * Validates: Req 1.1 — analyzeStreaming returns Success
     * with full accumulated text from all NDJSON lines.
     */
    @Test
    fun `analyzeStreaming returns Success with accumulated text`() =
        runTest {
            val chunks = listOf("Hello ", "world", "!")
            val body = buildNdjsonBody(chunks)
            val client = createMockClient { request ->
                respond(body, HttpStatusCode.OK)
            }
            val agent = OllamaAgent(client)
            val progress = mutableListOf<Int>()

            val result = agent.analyzeStreaming(
                prompt = "test",
                onProgress = { progress.add(it) }
            )

            assertIs<AIResult.Success>(result)
            assertEquals("Hello world!", result.response)
            assertTrue(progress.isNotEmpty())
            assertEquals(100, progress.last())
        }

    /**
     * Validates: Req 1.4 — analyzeStreaming returns Failure
     * on network/connection error.
     */
    @Test
    fun `analyzeStreaming returns Failure on network error`() =
        runTest {
            val client = createMockClient { _ ->
                throw java.io.IOException("Connection refused")
            }
            val agent = OllamaAgent(client)

            val result = agent.analyzeStreaming(
                prompt = "test",
                onProgress = {}
            )

            assertIs<AIResult.Failure>(result)
            assertTrue(
                result.error.contains("Error"),
                "Error message should describe the failure"
            )
        }

    /**
     * Validates: Req 3.1 — default estimatedTotalLines is 1000.
     * With 10 lines received, progress = min(95, 10*100/1000) = 1%.
     */
    @Test
    fun `default estimatedTotalLines is 1000`() = runTest {
        val chunks = (1..10).map { "token$it " }
        val body = buildNdjsonBody(chunks)
        val client = createMockClient { _ ->
            respond(body, HttpStatusCode.OK)
        }
        val agent = OllamaAgent(client)
        val progress = mutableListOf<Int>()

        agent.analyzeStreaming(
            prompt = "test",
            onProgress = { progress.add(it) }
        )

        // 10 lines / 1000 estimated = 1% progress before done
        val beforeDone = progress.dropLast(1)
        assertTrue(beforeDone.isNotEmpty())
        assertTrue(
            beforeDone.all { it <= 1 },
            "10 lines with 1000 estimate → progress ≤ 1%"
        )
    }

    /**
     * Validates: Req 4.2 — existing analyze() does NOT use
     * streaming and returns full response unchanged.
     */
    @Test
    fun `analyze does not use streaming and returns response`() =
        runTest {
            var capturedBody = ""
            val client = createMockClient { request ->
                capturedBody = request.body.toByteArray().decodeToString()
                val resp = """{"response":"Full response"}"""
                respond(resp, HttpStatusCode.OK)
            }
            val agent = OllamaAgent(client)

            val result = agent.analyze("test prompt")

            assertIs<AIResult.Success>(result)
            assertEquals("Full response", result.response)
            assertTrue(
                !capturedBody.contains("\"stream\":true"),
                "analyze() must not send stream:true"
            )
        }
}
