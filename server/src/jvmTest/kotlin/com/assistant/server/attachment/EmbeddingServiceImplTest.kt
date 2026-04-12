package com.assistant.server.attachment

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for EmbeddingServiceImpl using Ktor MockEngine.
 * Validates: Requirements 22.9
 */
class EmbeddingServiceImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val defaultConfig = EmbeddingServiceImpl.EmbeddingConfig(
        model = "nomic-embed-text",
        endpoint = "http://localhost:11434"
    )

    @Test
    fun `embed sends POST to correct endpoint`() = runBlocking {
        var capturedUrl: String? = null
        var capturedMethod: HttpMethod? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedMethod = request.method
            respond(successResponse(), HttpStatusCode.OK)
        }
        createService(engine).embed("hello")
        assertEquals("http://localhost:11434/api/embed", capturedUrl)
        assertEquals(HttpMethod.Post, capturedMethod)
    }

    @Test
    fun `embed sends correct model and input`() = runBlocking {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(successResponse(), HttpStatusCode.OK)
        }
        createService(engine).embed("test input text")
        val parsed = json.parseToJsonElement(capturedBody!!).jsonObject
        assertEquals("nomic-embed-text", parsed["model"]?.jsonPrimitive?.content)
        assertEquals("test input text", parsed["input"]?.jsonPrimitive?.content)
    }

    @Test
    fun `embed parses response into FloatArray`() = runBlocking {
        val engine = MockEngine {
            respond("""{"embeddings":[[0.1,0.2,0.3]]}""", HttpStatusCode.OK)
        }
        val result = createService(engine).embed("hello")
        assertNotNull(result)
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f), result)
    }

    @Test
    fun `embed returns null on HTTP error status`() = runBlocking {
        val engine = MockEngine {
            respond("server error", HttpStatusCode.InternalServerError)
        }
        val result = createService(engine).embed("hello")
        assertNull(result)
    }

    @Test
    fun `embed returns null on empty embedding`() = runBlocking {
        val engine = MockEngine {
            respond("""{"embeddings":[[]]}""", HttpStatusCode.OK)
        }
        val result = createService(engine).embed("hello")
        assertNull(result)
    }

    @Test
    fun `embed returns null when API throws exception`() = runBlocking {
        val engine = MockEngine { throw RuntimeException("Connection refused") }
        val result = createService(engine).embed("hello")
        assertNull(result)
    }

    @Test
    fun `embed sets JSON content type`() = runBlocking {
        var capturedContentType: String? = null
        val engine = MockEngine { request ->
            capturedContentType = request.body.contentType?.toString()
            respond(successResponse(), HttpStatusCode.OK)
        }
        createService(engine).embed("hello")
        assertEquals("application/json", capturedContentType)
    }

    private fun createService(engine: MockEngine) =
        EmbeddingServiceImpl(
            httpClient = HttpClient(engine),
            configProvider = { defaultConfig }
        )

    private fun successResponse() =
        """{"embeddings":[[0.5,0.6,0.7]]}"""
}
