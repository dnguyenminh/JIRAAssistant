package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 4: Conversation history preservation

/**
 * Property 4: Conversation history preservation in session mode
 *
 * For any sequence of N sendMessage() calls on an OllamaApiClient in
 * session mode, the internal conversation history SHALL contain at
 * least N user messages in the order they were sent.
 *
 * **Validates: Requirements 7.5**
 */
class ConversationHistoryPropertyTest {

    @Test
    fun `Property 4 - history contains N user messages in order`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 50),
                arbMessageSequence()
            ) { messages ->
                val client = createMockClient()
                client.startSession()

                for (msg in messages) {
                    client.sendMessage(msg)
                }

                val userMessages = client.conversationHistory
                    .filter { it.role == "user" }
                    .map { it.content }

                assertTrue(
                    userMessages.size >= messages.size,
                    "Expected >= ${messages.size} user messages, " +
                        "got ${userMessages.size}"
                )

                // Verify order preservation
                for (i in messages.indices) {
                    assertEquals(
                        messages[i], userMessages[i],
                        "User message at index $i must match"
                    )
                }

                client.endSession()
            }
        }
    }

    @Test
    fun `Property 4 - history includes assistant responses`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 50),
                arbMessageSequence()
            ) { messages ->
                val client = createMockClient()
                client.startSession()

                for (msg in messages) {
                    client.sendMessage(msg)
                }

                val assistantMessages = client.conversationHistory
                    .filter { it.role == "assistant" }

                assertEquals(
                    messages.size, assistantMessages.size,
                    "Each sendMessage should add one assistant response"
                )

                client.endSession()
            }
        }
    }

    @Test
    fun `Property 4 - endSession clears history`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 50),
                arbMessageSequence()
            ) { messages ->
                val client = createMockClient()
                client.startSession()

                for (msg in messages) {
                    client.sendMessage(msg)
                }

                client.endSession()

                assertTrue(
                    client.conversationHistory.isEmpty(),
                    "History must be empty after endSession"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbMessageSequence(): Arb<List<String>> = arbitrary {
        val count = Arb.int(1..10).bind()
        (0 until count).map {
            Arb.string(1..30, Codepoint.alphanumeric()).bind()
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun createMockClient(): OllamaApiClient {
        val mockEngine = MockEngine { _ ->
            val body = """
            {
              "model":"test-model",
              "message":{"role":"assistant","content":"ok"},
              "done":true
            }
            """.trimIndent()
            respond(body, HttpStatusCode.OK)
        }
        return OllamaApiClient(
            baseUrl = "http://localhost:11434",
            model = "test-model",
            streaming = false,
            httpClient = HttpClient(mockEngine)
        )
    }
}
