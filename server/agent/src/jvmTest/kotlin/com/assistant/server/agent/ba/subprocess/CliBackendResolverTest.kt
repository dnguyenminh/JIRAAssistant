package com.assistant.server.agent.ba.subprocess

import com.assistant.settings.SettingsRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("agent-subprocess-orchestration")
class CliBackendResolverTest {

    // ── Gemini ──────────────────────────────────────────────

    @Test
    fun `gemini resolves with path and model`(): Unit = runBlocking {
        val settings = fakeSettings(
            "ai_cli_path" to "/usr/bin/gemini",
            "ai_cli_model" to "gemini-pro"
        )
        val result = CliBackendResolver(settings).resolve("gemini")

        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.cliCommand shouldBe "/usr/bin/gemini"
        config.cliArgs shouldBe listOf(
            "/usr/bin/gemini", "-m", "gemini-pro"
        )
        config.agentType shouldBe "ba-agent"
    }

    @Test
    fun `gemini resolves without model`(): Unit = runBlocking {
        val settings = fakeSettings("ai_cli_path" to "/usr/bin/gemini")
        val result = CliBackendResolver(settings).resolve("gemini")

        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.cliArgs shouldBe listOf("/usr/bin/gemini")
    }

    // ── Copilot ─────────────────────────────────────────────

    @Test
    fun `copilot resolves with path`(): Unit = runBlocking {
        val settings = fakeSettings(
            "copilot_cli_path" to "/usr/bin/copilot"
        )
        val result = CliBackendResolver(settings).resolve("copilot")

        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.cliCommand shouldBe "/usr/bin/copilot"
        config.cliArgs shouldBe listOf("/usr/bin/copilot")
    }

    // ── Kiro ────────────────────────────────────────────────

    @Test
    fun `kiro resolves with path`(): Unit = runBlocking {
        val settings = fakeSettings(
            "kiro_cli_path" to "/usr/bin/kiro"
        )
        val result = CliBackendResolver(settings).resolve("kiro")

        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.cliCommand shouldBe "/usr/bin/kiro"
        config.cliArgs shouldBe listOf("/usr/bin/kiro")
    }

    // ── Ollama ──────────────────────────────────────────────

    @Test
    fun `ollama resolves with path and model`(): Unit = runBlocking {
        val settings = fakeSettings(
            "ollama_cli_path" to "/usr/bin/ollama",
            "ollama_cli_model" to "llama3"
        )
        val result = CliBackendResolver(settings).resolve("ollama")

        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.cliCommand shouldBe "/usr/bin/ollama"
        config.cliArgs shouldBe listOf(
            "/usr/bin/ollama", "run", "llama3"
        )
    }

    @Test
    fun `ollama resolves without model`(): Unit = runBlocking {
        val settings = fakeSettings(
            "ollama_cli_path" to "/usr/bin/ollama"
        )
        val result = CliBackendResolver(settings).resolve("ollama")

        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.cliArgs shouldBe listOf("/usr/bin/ollama")
    }

    // ── Error cases ─────────────────────────────────────────

    @Test
    fun `invalid backend returns failure`(): Unit = runBlocking {
        val settings = fakeSettings()
        val result = CliBackendResolver(settings).resolve("chatgpt")

        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message!! shouldContain
            "Unsupported CLI backend"
    }

    @Test
    fun `missing CLI path returns failure`(): Unit = runBlocking {
        val settings = fakeSettings() // no paths configured
        val result = CliBackendResolver(settings).resolve("gemini")

        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message!! shouldContain
            "CLI path not configured"
    }

    @Test
    fun `blank CLI path returns failure`(): Unit = runBlocking {
        val settings = fakeSettings("ai_cli_path" to "  ")
        val result = CliBackendResolver(settings).resolve("gemini")

        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message!! shouldContain
            "CLI path not configured"
    }

    // ── 6.5 isRealCli = true for all backends ───────────

    @Test
    fun `gemini config has isRealCli true`(): Unit = runBlocking {
        val settings = fakeSettings("ai_cli_path" to "/usr/bin/gemini")
        val config = CliBackendResolver(settings).resolve("gemini").getOrThrow()
        config.isRealCli shouldBe true
    }

    @Test
    fun `copilot config has isRealCli true`(): Unit = runBlocking {
        val settings = fakeSettings("copilot_cli_path" to "/usr/bin/copilot")
        val config = CliBackendResolver(settings).resolve("copilot").getOrThrow()
        config.isRealCli shouldBe true
    }

    @Test
    fun `kiro config has isRealCli true`(): Unit = runBlocking {
        val settings = fakeSettings("kiro_cli_path" to "/usr/bin/kiro")
        val config = CliBackendResolver(settings).resolve("kiro").getOrThrow()
        config.isRealCli shouldBe true
    }

    @Test
    fun `ollama config has isRealCli true`(): Unit = runBlocking {
        val settings = fakeSettings("ollama_cli_path" to "/usr/bin/ollama")
        val config = CliBackendResolver(settings).resolve("ollama").getOrThrow()
        config.isRealCli shouldBe true
    }
}

// ── Test double ─────────────────────────────────────────────

private fun fakeSettings(
    vararg pairs: Pair<String, String>
): SettingsRepository {
    val map = pairs.toMap().toMutableMap()
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
