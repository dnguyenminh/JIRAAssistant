package com.assistant.server.chat

import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for ResponseFormatDetector.
 * Requirements: 19.87, 19.88, 19.89, 19.90, 19.91, 19.99
 */
class ResponseFormatDetectorTest {

    private val modelCtx = AIModelContext("OLLAMA", "gemma3:4b")

    private fun jsonObj(vararg pairs: Pair<String, JsonElement>): JsonObject =
        JsonObject(pairs.toMap())

    @Test
    fun `detect known key text returns DetectedReply`() = runBlocking {
        val obj = jsonObj("text" to JsonPrimitive("Hello world"))
        val result = ResponseFormatDetector.detect(obj, null, null)
        assertNotNull(result)
        assertEquals("text", result!!.path)
        assertEquals("Hello world", result.text)
    }

    @Test
    fun `detect known key content returns DetectedReply`() = runBlocking {
        val obj = jsonObj("content" to JsonPrimitive("AI response"))
        val result = ResponseFormatDetector.detect(obj, null, null)
        assertNotNull(result)
        assertEquals("content", result!!.path)
    }

    @Test
    fun `detect nested response_text returns correct path`() = runBlocking {
        val nested = JsonObject(mapOf("text" to JsonPrimitive("Nested reply")))
        val obj = jsonObj("response" to nested)
        val result = ResponseFormatDetector.detect(obj, null, null)
        assertNotNull(result)
        assertEquals("response.text", result!!.path)
        assertEquals("Nested reply", result.text)
    }

    @Test
    fun `detect longest string when no known key matches`() = runBlocking {
        val obj = jsonObj(
            "foo" to JsonPrimitive("short"),
            "bar" to JsonPrimitive("this is a much longer string that should be selected")
        )
        val result = ResponseFormatDetector.detect(obj, null, null)
        assertNotNull(result)
        assertEquals("bar", result!!.path)
        assertTrue(result.text.contains("much longer"))
    }

    @Test
    fun `cache hit returns cached path value`() = runBlocking {
        val repo = FakeSettingsRepo()
        repo.put("ai_response_format:OLLAMA:gemma3:4b", "content")
        val obj = jsonObj("content" to JsonPrimitive("Cached result"))
        val result = ResponseFormatDetector.detect(obj, modelCtx, repo)
        assertNotNull(result)
        assertEquals("content", result!!.path)
        assertEquals("Cached result", result.text)
    }

    @Test
    fun `stale cache invalidated and re-detected`() = runBlocking {
        val repo = FakeSettingsRepo()
        repo.put("ai_response_format:OLLAMA:gemma3:4b", "old.path")
        val obj = jsonObj("text" to JsonPrimitive("New response"))
        val result = ResponseFormatDetector.detect(obj, modelCtx, repo)
        assertNotNull(result)
        assertEquals("text", result!!.path)
        // Cache should be updated to new path
        assertEquals("text", repo.get("ai_response_format:OLLAMA:gemma3:4b"))
    }

    @Test
    fun `no string field returns null`() = runBlocking {
        val obj = jsonObj(
            "count" to JsonPrimitive(42),
            "active" to JsonPrimitive(true)
        )
        val result = ResponseFormatDetector.detect(obj, null, null)
        assertNull(result)
    }

    @Test
    fun `extractByPath valid path returns correct value`() {
        val nested = JsonObject(mapOf("text" to JsonPrimitive("deep")))
        val obj = JsonObject(mapOf("response" to nested))
        assertEquals("deep", ResponseFormatDetector.extractByPath(obj, "response.text"))
    }

    @Test
    fun `extractByPath invalid path returns null`() {
        val obj = JsonObject(mapOf("foo" to JsonPrimitive("bar")))
        assertNull(ResponseFormatDetector.extractByPath(obj, "missing.path"))
    }

    @Test
    fun `cache key uses correct prefix`() = runBlocking {
        val repo = FakeSettingsRepo()
        val obj = jsonObj("text" to JsonPrimitive("Hello"))
        ResponseFormatDetector.detect(obj, modelCtx, repo)
        val key = "ai_response_format:OLLAMA:gemma3:4b"
        assertNotNull(repo.get(key))
    }

    @Test
    fun `response as primitive string detected`() = runBlocking {
        val obj = jsonObj("response" to JsonPrimitive("Direct response text"))
        val result = ResponseFormatDetector.detect(obj, null, null)
        assertNotNull(result)
        assertEquals("response", result!!.path)
        assertEquals("Direct response text", result.text)
    }

    /** In-memory fake SettingsRepository for testing. */
    private class FakeSettingsRepo(
        private val store: MutableMap<String, String> = mutableMapOf()
    ) : SettingsRepository {
        override suspend fun getAll() = store.toMap()
        override suspend fun get(key: String) = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }
}
