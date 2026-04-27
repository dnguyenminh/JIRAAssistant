package com.assistant.server.agent.ba

import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Preservation property tests — verify non-tool test infrastructure
 * remains unchanged before and after the MCP pipeline fix.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 *
 * These tests MUST PASS on unfixed code (baseline behavior).
 * After the fix, they MUST STILL PASS (no regressions).
 */
@Tag("ba-agent-integration")
class BADocumentAgentPreservationTest {

    // ── Test 1: Subprocess simulation ────────────────────────────

    /**
     * **Validates: Requirements 3.1, 3.3**
     *
     * FakeSubprocessManager with simpleDocStdoutProvider() produces
     * Flow<String> containing "---END---" and BRD content markers.
     */
    @Test
    fun `FakeSubprocessManager produces expected stdout flow`() {
        val mgr = FakeSubprocessManager(simpleDocStdoutProvider())
        runBlocking {
            val first = mgr.sendCommand("ba-agent", "init").toList()
            first shouldContain "---END---"

            val second = mgr.sendCommand("ba-agent", "generate").toList()
            second shouldContain "# BRD Document"
            second shouldContain "---END---"
        }
    }

    // ── Test 2: Provider config repo ─────────────────────────────

    /**
     * **Validates: Requirements 3.2**
     *
     * InMemoryProviderConfigRepo.addProvider() + findByType()
     * returns the added provider correctly.
     */
    @Test
    fun `InMemoryProviderConfigRepo stores and retrieves by type`() {
        val repo = InMemoryProviderConfigRepo()
        val config = geminiProviderConfig("/usr/bin/gemini")

        repo.addProvider(config)
        val found = repo.findByType(ProviderType.GEMINI_CLI)

        found shouldNotBe null
        found!!.endpoint shouldBe "/usr/bin/gemini"
        found.type shouldBe ProviderType.GEMINI_CLI
    }

    // ── Test 3: Settings repo ────────────────────────────────────

    /**
     * **Validates: Requirements 3.2**
     *
     * FakeSettingsRepo.put() + get() round-trips values correctly.
     */
    @Test
    fun `FakeSettingsRepo stores and retrieves values`() {
        val settings = FakeSettingsRepo()
        runBlocking {
            settings.put("key", "value")
            settings.get("key") shouldBe "value"
        }
    }

    // ── Test 4: Reporter ─────────────────────────────────────────

    /**
     * **Validates: Requirements 3.4**
     *
     * IntegrationNoOpReporter.reportPhase() completes without error.
     */
    @Test
    fun `IntegrationNoOpReporter reportPhase completes`() {
        runBlocking {
            val result = IntegrationNoOpReporter.reportPhase(
                "Analysis", 1, 3
            )
            result shouldBe Unit
        }
    }

    // ── Test 5: CLI resolution ───────────────────────────────────

    /**
     * **Validates: Requirements 3.2**
     *
     * CliBackendResolver with InMemoryProviderConfigRepo resolves
     * CLI path from provider config correctly.
     */
    @Test
    fun `CliBackendResolver resolves from InMemoryProviderConfigRepo`() {
        val repo = InMemoryProviderConfigRepo()
        repo.addProvider(geminiProviderConfig("/test/gemini-cli"))
        val settings = FakeSettingsRepo()

        runBlocking {
            val resolver = CliBackendResolver(settings, repo)
            val result = resolver.resolve("gemini")

            result.isSuccess shouldBe true
            result.getOrThrow().cliCommand shouldBe "/test/gemini-cli"
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun geminiProviderConfig(endpoint: String) = ProviderConfig(
        providerId = "gemini-cli",
        name = "Gemini CLI",
        type = ProviderType.GEMINI_CLI,
        endpoint = endpoint,
        priority = 1,
        status = ConnectionStatus.ACTIVE
    )
}
