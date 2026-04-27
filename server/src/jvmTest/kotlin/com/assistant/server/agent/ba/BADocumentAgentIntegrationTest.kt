package com.assistant.server.agent.ba

import com.assistant.agent.models.AgentInput
import com.assistant.agent.models.AgentStatus
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.tool.ToolRegistry
import com.assistant.ai.ProviderType
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import com.assistant.server.agent.ba.models.BAAgentPayload
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import com.assistant.server.agent.subprocess.SubprocessManagerImpl
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Integration tests: BADocumentAgent → Orchestrator →
 * CliBackendResolver → ProviderConfigRepository.
 *
 * CLI paths are configured by the tester via system properties:
 *   -Dtest.gemini.cli.path=/path/to/gemini
 *   -Dtest.gemini.cli.model=gemini-2.5-flash
 *   -Dtest.copilot.cli.path=/path/to/copilot
 *   -Dtest.kiro.cli.path=/path/to/kiro
 *
 * Tests that require a CLI path are skipped if not configured.
 */
@Tag("ba-agent-integration")
class BADocumentAgentIntegrationTest {

    private lateinit var providerRepo: InMemoryProviderConfigRepo

    @BeforeEach
    fun setup() {
        providerRepo = InMemoryProviderConfigRepo()
    }

    // ── 1. Full pipeline with tester-configured CLI path ─────────

    @Test
    @Timeout(180, unit = TimeUnit.SECONDS)
    fun `full pipeline produces document via configured CLI`() {
        val cliPath = cliPath("gemini") ?: return skipNoCli("gemini")
        val model = cliModel("gemini")
        providerRepo.addProvider(providerConfig(
            ProviderType.GEMINI_CLI, cliPath, model
        ))
        runBlocking {
            val real = buildRealToolLayer()
            val realMgr = SubprocessManagerImpl()
            val orch = buildOrchestrator(realMgr, real.subprocessProxy)
            val agent = buildAgent(orch, real.toolRegistry)

            agent.onStart(buildInput())
            val output = agent.execute(buildInput())

            output.status shouldBe AgentStatus.SUCCESS
            output.result shouldContain "BRD"
        }
    }

    // ── 2. ProviderConfig takes priority over SettingsRepository ─

    @Test
    fun `ProviderConfig takes priority over SettingsRepository`() {
        val cliPath = cliPath("gemini") ?: return skipNoCli("gemini")
        providerRepo.addProvider(providerConfig(
            ProviderType.GEMINI_CLI, cliPath, cliModel("gemini")
        ))
        val settings = FakeSettingsRepo(
            mutableMapOf("ai_cli_path" to "/should/not/be/used")
        )
        runBlocking {
            val resolver = CliBackendResolver(settings, providerRepo)
            val result = resolver.resolve("gemini")

            result.isSuccess shouldBe true
            result.getOrThrow().cliCommand shouldBe cliPath
        }
    }

    // ── 3. Fallback to SettingsRepository ────────────────────────

    @Test
    fun `falls back to SettingsRepository when no ProviderConfig`() {
        val cliPath = cliPath("gemini") ?: return skipNoCli("gemini")
        // providerRepo is empty
        val settings = FakeSettingsRepo(
            mutableMapOf("ai_cli_path" to cliPath)
        )
        runBlocking {
            val resolver = CliBackendResolver(settings, providerRepo)
            val result = resolver.resolve("gemini")

            result.isSuccess shouldBe true
            result.getOrThrow().cliCommand shouldBe cliPath
        }
    }

    // ── 4. Pipeline with tool calls ──────────────────────────────

    @Test
    fun `pipeline handles tool calls with configured CLI`() {
        val cliPath = cliPath("gemini") ?: return skipNoCli("gemini")
        providerRepo.addProvider(providerConfig(
            ProviderType.GEMINI_CLI, cliPath, cliModel("gemini")
        ))
        runBlocking {
            val real = buildRealToolLayer()
            val fakeMgr = FakeSubprocessManager(toolCallStdoutProvider())
            val orch = buildOrchestrator(fakeMgr, real.subprocessProxy)
            val agent = buildAgent(orch, real.toolRegistry)

            agent.onStart(buildInput())
            val output = agent.execute(buildInput())

            output.status shouldBe AgentStatus.SUCCESS
        }
    }

    // ── 5. Fails when no CLI configured anywhere ─────────────────

    @Test
    fun `fails when no CLI configured in any source`() {
        // providerRepo empty, settings empty
        runBlocking {
            val orch = buildOrchestrator(
                FakeSubprocessManager(simpleDocStdoutProvider()),
                FakeSubprocessProxy(), FakeSettingsRepo()
            )
            val agent = buildAgent(orch)

            agent.onStart(buildInput())
            val output = agent.execute(buildInput())

            output.status shouldBe AgentStatus.FAILED
        }
    }

    // ── 6. Dynamic config update between resolutions ─────────────

    @Test
    fun `picks up updated config on each resolution`() {
        val cliPath = cliPath("gemini") ?: return skipNoCli("gemini")
        providerRepo.addProvider(providerConfig(
            ProviderType.GEMINI_CLI, cliPath, "model-v1"
        ))
        runBlocking {
            val resolver = CliBackendResolver(FakeSettingsRepo(), providerRepo)
            val cfg1 = resolver.resolve("gemini").getOrThrow()
            cfg1.cliCommand shouldBe cliPath

            providerRepo.clear()
            val newPath = cliPath + "-updated"
            providerRepo.addProvider(providerConfig(
                ProviderType.GEMINI_CLI, newPath, "model-v2"
            ))
            val cfg2 = resolver.resolve("gemini").getOrThrow()
            cfg2.cliCommand shouldBe newPath
        }
    }

    // ── 7. Full pipeline with Ollama backend ────────────────────

    @Test
    @Timeout(600, unit = TimeUnit.SECONDS)
    fun `full pipeline produces BRD via Ollama with tool calls`() {
        val ollamaUrl = cliPath("ollama") ?: return skipNoCli("ollama")
        val ollamaModel = cliModel("ollama") ?: "batiai/gemma4-e2b:q4"
        val settings = FakeSettingsRepo(mutableMapOf(
            "ollama_cli_path" to ollamaUrl,
            "ollama_cli_model" to ollamaModel
        ))
        runBlocking {
            val real = buildRealToolLayer()
            val realMgr = SubprocessManagerImpl()
            val orch = buildOrchestrator(realMgr, real.subprocessProxy, settings)
            val agent = buildAgent(orch, real.toolRegistry)

            val input = buildInput("ollama")
            agent.onStart(input)
            val output = agent.execute(input)

            // Save BRD to file for review
            val brdFile = java.io.File("build/logs/ollama-brd-output.md")
            brdFile.parentFile.mkdirs()
            brdFile.writeText(output.result)
            println("BRD saved to: ${brdFile.absolutePath}")
            println("BRD size: ${output.result.length} chars")

            output.status shouldBe AgentStatus.SUCCESS
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun buildOrchestrator(
        manager: SubprocessManager,
        proxy: SubprocessProxy,
        settings: FakeSettingsRepo = FakeSettingsRepo()
    ) = BASubprocessOrchestrator(
        subprocessManager = manager, subprocessProxy = proxy,
        progressReporter = IntegrationNoOpReporter,
        settingsRepository = settings, providerConfigRepo = providerRepo
    )

    private fun buildAgent(
        orch: BASubprocessOrchestrator,
        toolRegistry: ToolRegistry = IntegrationNoOpToolRegistry()
    ) = BADocumentAgent(
            toolRegistry = toolRegistry,
            memory = JiraContextMemorySchema.createMemory(),
            progressReporter = IntegrationNoOpReporter,
            subprocessOrchestrator = orch
        )

    private fun buildInput(backend: String = "gemini") = AgentInput(
        requestId = "int-req-1", agentType = "ba-document",
        payload = mapOf(
            BAAgentPayload.TICKET_ID to testTicketId(),
            BAAgentPayload.DOC_TYPE to "BRD",
            BAAgentPayload.CLI_BACKEND to backend
        )
    )
}
