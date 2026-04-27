package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClient
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [AiBackendFactory].
 *
 * Verifies each backend name maps to the correct client type
 * and unsupported names return failure.
 */
class AiBackendFactoryTest {

    @Test
    fun `gemini creates GeminiCliClientImpl`(): Unit = runBlocking {
        val factory = createFactory()
        val result = factory.create("gemini")
        assertTrue(result.isSuccess)
        assertIs<GeminiCliClientImpl>(result.getOrThrow())
    }

    @Test
    fun `copilot creates CopilotCliClientImpl`(): Unit = runBlocking {
        val factory = createFactory()
        val result = factory.create("copilot")
        assertTrue(result.isSuccess)
        assertIs<CopilotCliClientImpl>(result.getOrThrow())
    }

    @Test
    fun `kiro creates KiroCliClientImpl`(): Unit = runBlocking {
        val factory = createFactory()
        val result = factory.create("kiro")
        assertTrue(result.isSuccess)
        assertIs<KiroCliClientImpl>(result.getOrThrow())
    }

    @Test
    fun `ollama creates OllamaApiClient`(): Unit = runBlocking {
        val factory = createFactory()
        val result = factory.create("ollama")
        assertTrue(result.isSuccess)
        assertIs<OllamaApiClient>(result.getOrThrow())
    }

    @Test
    fun `unknown backend returns failure`(): Unit = runBlocking {
        val factory = createFactory()
        val result = factory.create("unknown")
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun `empty backend name returns failure`() = runBlocking {
        val factory = createFactory()
        val result = factory.create("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `failure message contains supported backends`() = runBlocking {
        val factory = createFactory()
        val result = factory.create("unsupported")
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Supported"))
    }

    @Test
    fun `ollama uses model from settings`() = runBlocking {
        val settings = fakeSettings("ollama_cli_model" to "llama3")
        val factory = createFactory(settings)
        val result = factory.create("ollama")
        val client = result.getOrThrow() as OllamaApiClient
        assertEquals("llama3", client.model)
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun createFactory(
        settings: SettingsRepository? = null
    ): AiBackendFactory {
        val resolver = CliBackendResolver(
            settingsRepository = settings ?: fakeSettings()
        )
        return AiBackendFactory(resolver)
    }

    private fun fakeSettings(
        vararg pairs: Pair<String, String>
    ): SettingsRepository {
        val map = mutableMapOf(*pairs)
        return object : SettingsRepository {
            override suspend fun getAll() = map.toMap()
            override suspend fun get(key: String) = map[key]
            override suspend fun put(key: String, value: String) {
                map[key] = value
            }
            override suspend fun putAll(settings: Map<String, String>) {
                map.putAll(settings)
            }
        }
    }
}
