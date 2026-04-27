package com.assistant.server.chat

import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Property-based tests for ResponseFormatDetector.
 * Feature: ai-chat-sidebar, Property 9: Reply format detection with cache lifecycle
 * Feature: ai-chat-sidebar, Property 13: extractByPath round-trip correctness
 * Requirements: 19.87, 19.88, 19.89, 19.90
 */
class ResponseFormatDetectorPropertyTest {

    private val knownKeys = listOf("reply", "text", "content", "message", "answer", "output")
    private val providers = listOf("OLLAMA", "GEMINI", "LM_STUDIO")
    private val models = listOf("gemma3:4b", "llama3", "gemini-2.0-flash", "deepseek-r1")

    /**
     * Property 9: Reply format detection with cache lifecycle.
     * For any JSON with a string field, detection finds it and caches the path.
     * Tag: Feature: ai-chat-sidebar, Property 9: Reply format detection with cache lifecycle
     */
    @Test
    fun `property 9 - detection finds string field and caches path`() {
        repeat(100) { i ->
            val repo = InMemorySettingsRepo()
            val ctx = randomModelContext()
            val (obj, expectedPath) = randomJsonWithKnownString(i)
            val result = runBlocking {
                ResponseFormatDetector.detect(obj, ctx, repo)
            }
            assertNotNull(result, "Iteration $i: should detect string field")
            // Cache should be written with correct prefix
            val cacheKey = "ai_response_format:${ctx.cacheKeySuffix}"
            val cached = runBlocking { repo.get(cacheKey) }
            assertNotNull(cached, "Iteration $i: cache should be written")
            assertTrue(cached!!.isNotBlank(), "Iteration $i: cached path should not be blank")
        }
    }

    /**
     * Property 9 (cache-first): cached path is used when valid.
     */
    @Test
    fun `property 9 - cached path used when valid`() {
        repeat(100) { i ->
            val repo = InMemorySettingsRepo()
            val ctx = randomModelContext()
            val key = knownKeys[i % knownKeys.size]
            val value = "Response text iteration $i with random ${Random.nextInt()}"
            val obj = JsonObject(mapOf(key to JsonPrimitive(value)))
            // Pre-cache the path
            runBlocking { repo.put("ai_response_format:${ctx.cacheKeySuffix}", key) }
            val result = runBlocking {
                ResponseFormatDetector.detect(obj, ctx, repo)
            }
            assertNotNull(result, "Iteration $i: should use cached path")
            assertEquals(key, result!!.path, "Iteration $i: should return cached path")
            assertEquals(value, result.text, "Iteration $i: should return correct text")
        }
    }

    /**
     * Property 9 (stale cache): stale cache is invalidated and re-detected.
     */
    @Test
    fun `property 9 - stale cache invalidated and re-detected`() {
        repeat(100) { i ->
            val repo = InMemorySettingsRepo()
            val ctx = randomModelContext()
            val key = knownKeys[i % knownKeys.size]
            val value = "Fresh value $i"
            val obj = JsonObject(mapOf(key to JsonPrimitive(value)))
            // Pre-cache a stale path
            runBlocking { repo.put("ai_response_format:${ctx.cacheKeySuffix}", "stale.nonexistent.path") }
            val result = runBlocking {
                ResponseFormatDetector.detect(obj, ctx, repo)
            }
            assertNotNull(result, "Iteration $i: should re-detect after stale cache")
            assertEquals(value, result!!.text, "Iteration $i: should find correct text")
            // Cache should be updated
            val newCached = runBlocking { repo.get("ai_response_format:${ctx.cacheKeySuffix}") }
            assertEquals(key, newCached, "Iteration $i: cache should be updated to new path")
        }
    }

    /**
     * Property 13: extractByPath round-trip correctness.
     * For any JSON object and valid path, extractByPath returns the correct value.
     * Tag: Feature: ai-chat-sidebar, Property 13: extractByPath round-trip correctness
     */
    @Test
    fun `property 13 - extractByPath returns correct value for valid paths`() {
        repeat(100) { i ->
            val value = "Value_${Random.nextInt(10000)}"
            val (obj, path) = randomJsonWithPath(value, i)
            val extracted = ResponseFormatDetector.extractByPath(obj, path)
            assertEquals(value, extracted, "Iteration $i: path '$path' should extract '$value'")
        }
    }

    @Test
    fun `property 13 - extractByPath returns null for invalid paths`() {
        repeat(100) { i ->
            val obj = JsonObject(mapOf("a" to JsonPrimitive("x")))
            val invalidPath = "nonexistent.path.${Random.nextInt(100)}"
            val result = ResponseFormatDetector.extractByPath(obj, invalidPath)
            assertNull(result, "Iteration $i: invalid path should return null")
        }
    }

    // --- Generators ---

    private fun randomModelContext(): AIModelContext =
        AIModelContext(providers.random(), models.random())

    private fun randomJsonWithKnownString(seed: Int): Pair<JsonObject, String> {
        val key = knownKeys[seed % knownKeys.size]
        val value = "Generated response text #$seed ${Random.nextInt()}"
        val obj = JsonObject(mapOf(
            key to JsonPrimitive(value),
            "extra" to JsonPrimitive(Random.nextInt())
        ))
        return obj to key
    }

    private fun randomJsonWithPath(value: String, seed: Int): Pair<JsonObject, String> {
        return if (seed % 2 == 0) {
            // Top-level path
            val key = "field_${seed % 5}"
            JsonObject(mapOf(key to JsonPrimitive(value))) to key
        } else {
            // Nested path
            val outer = "outer_${seed % 3}"
            val inner = "inner_${seed % 4}"
            val nested = JsonObject(mapOf(inner to JsonPrimitive(value)))
            JsonObject(mapOf(outer to nested)) to "$outer.$inner"
        }
    }

    private class InMemorySettingsRepo : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun getAll() = store.toMap()
        override suspend fun get(key: String) = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }
}
