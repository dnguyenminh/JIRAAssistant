package com.assistant.server.chat

import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Property-based tests for Auto-Detect cache isolation and parser bypass.
 * Feature: ai-chat-sidebar, Property 11: Cache key isolation per provider+model
 * Feature: ai-chat-sidebar, Property 12: Standard format and plain text bypass detection
 * Requirements: 19.97, 19.100, 19.101
 */
class AutoDetectPropertyTest {

    private val providers = listOf("OLLAMA", "GEMINI", "LM_STUDIO")
    private val models = listOf("gemma3:4b", "llama3", "gemini-2.0-flash", "deepseek-r1", "qwen2:7b")

    /**
     * Property 11: Cache key isolation per provider+model.
     * For any two distinct AIModelContext values, their cache keys are different.
     * Tag: Feature: ai-chat-sidebar, Property 11: Cache key isolation per provider+model
     */
    @Test
    fun `property 11 - distinct contexts produce distinct cache keys`() {
        repeat(100) {
            val ctx1 = randomModelContext()
            var ctx2 = randomModelContext()
            // Ensure they're different
            while (ctx1 == ctx2) ctx2 = randomModelContext()
            assertNotEquals(
                ctx1.cacheKeySuffix, ctx2.cacheKeySuffix,
                "Distinct contexts ($ctx1 vs $ctx2) should have distinct cache keys"
            )
        }
    }

    /**
     * Property 11: Cache entries for different providers don't interfere.
     */
    @Test
    fun `property 11 - cache entries are isolated per provider+model`() {
        repeat(100) { i ->
            val repo = InMemorySettingsRepo()
            val ctx1 = AIModelContext("OLLAMA", "model_$i")
            val ctx2 = AIModelContext("GEMINI", "model_$i")
            runBlocking {
                repo.put("ai_response_format:${ctx1.cacheKeySuffix}", "text")
                repo.put("ai_response_format:${ctx2.cacheKeySuffix}", "content")
            }
            val val1 = runBlocking { repo.get("ai_response_format:${ctx1.cacheKeySuffix}") }
            val val2 = runBlocking { repo.get("ai_response_format:${ctx2.cacheKeySuffix}") }
            assertEquals("text", val1, "Iteration $i: ctx1 cache should be 'text'")
            assertEquals("content", val2, "Iteration $i: ctx2 cache should be 'content'")
        }
    }

    /**
     * Property 12: Standard format ChatResponse bypasses detection.
     * For any valid ChatResponse JSON with "reply" key, parser decodes directly.
     * Tag: Feature: ai-chat-sidebar, Property 12: Standard format and plain text bypass detection
     */
    @Test
    fun `property 12 - standard reply key decoded directly`() {
        repeat(100) { i ->
            val replyText = "Standard reply #$i with random ${Random.nextInt()}"
            val json = """{"reply":"$replyText","actions":[],"references":[]}"""
            val result = ChatResponseParser.parse(json, 50)
            assertEquals(replyText, result.reply, "Iteration $i: standard format should decode directly")
            assertEquals(50, result.contextUsage)
        }
    }

    /**
     * Property 12: Plain text (non-JSON) returned as-is without detection.
     */
    @Test
    fun `property 12 - plain text returned as reply unchanged`() {
        repeat(100) { i ->
            val plainText = "Plain text response #$i — no JSON here ${Random.nextInt()}"
            val result = ChatResponseParser.parse(plainText, 30)
            assertEquals(plainText, result.reply, "Iteration $i: plain text should be returned as-is")
            assertEquals(30, result.contextUsage)
        }
    }

    /**
     * Property 12: Blank input returns startup message.
     */
    @Test
    fun `property 12 - blank input returns startup message`() {
        repeat(100) { i ->
            val blank = " ".repeat(i % 5) // "", " ", "  ", etc.
            val result = ChatResponseParser.parse(blank, 10)
            assertTrue(result.reply.contains("AI đang khởi động"), "Iteration $i: blank should return startup msg")
        }
    }

    private fun randomModelContext(): AIModelContext =
        AIModelContext(providers.random(), models.random())

    private class InMemorySettingsRepo : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun getAll() = store.toMap()
        override suspend fun get(key: String) = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }
}
