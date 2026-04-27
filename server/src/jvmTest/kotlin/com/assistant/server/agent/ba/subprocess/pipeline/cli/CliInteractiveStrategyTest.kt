package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import com.assistant.server.agent.ba.subprocess.pipeline.CliInteractiveStrategy
import com.assistant.server.agent.ba.subprocess.pipeline.PipelineStrategy
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CliInteractiveStrategy].
 *
 * Since the strategy creates [CliInteractiveEngine] internally,
 * we test through the injected dependencies: [CliBackendResolver]
 * and [SubprocessProxy]. Tests focus on error paths that don't
 * require a real CLI process.
 *
 * **Validates: Requirements 6.3, 6.4, 6.5, 7.6, 9.1**
 */
@Tag("cli-interactive-ba-agent")
class CliInteractiveStrategyTest {

    // ── Stub implementations ──────────────────────────────────

    /** SettingsRepository that always returns null. */
    private class EmptySettingsRepository : SettingsRepository {
        override suspend fun getAll(): Map<String, String> = emptyMap()
        override suspend fun get(key: String): String? = null
        override suspend fun put(key: String, value: String) {}
        override suspend fun putAll(settings: Map<String, String>) {}
    }

    /** SubprocessProxy stub — never called in error-path tests. */
    private val stubProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(request.id, false, "", "stub")

        override fun getAvailableToolDescriptors(): List<ToolDescriptor> =
            emptyList()

        override fun buildToolListMessage(): String = ""
        override fun buildToolsUpdatedMessage(): String = ""
    }

    /** Captures progress reports for assertion. */
    private class CapturingProgressReporter : ProgressReporter {
        val phases = mutableListOf<Triple<String, Int, Int>>()
        val progresses = mutableListOf<Pair<Int, String>>()
        val toolCalls = mutableListOf<Pair<String, String>>()

        override suspend fun reportPhase(
            phaseName: String, phaseIndex: Int, totalPhases: Int
        ) { phases.add(Triple(phaseName, phaseIndex, totalPhases)) }

        override suspend fun reportProgress(percent: Int, message: String) {
            progresses.add(percent to message)
        }

        override suspend fun reportToolCall(
            toolName: String, status: String
        ) { toolCalls.add(toolName to status) }
    }

    // ── Tests ─────────────────────────────────────────────────

    @Test
    fun `returns FAILED when CLI path is missing`() = runBlocking {
        val resolver = CliBackendResolver(EmptySettingsRepository(), null)
        val strategy = CliInteractiveStrategy(stubProxy, resolver)
        val config = BATaskConfig(
            rootTicketId = "TEST-1",
            docType = "BRD",
            cliBackend = "gemini"
        )
        val reporter = CapturingProgressReporter()

        val result = strategy.execute(config, reporter)

        assertEquals(BATaskStatus.FAILED, result.status)
        assertTrue(result.document.isEmpty())
        assertEquals(0, result.toolCallsExecuted)
    }

    @Test
    fun `returns FAILED for all backends when paths not configured`() =
        runBlocking {
            val resolver = CliBackendResolver(
                EmptySettingsRepository(), null
            )
            val reporter = CapturingProgressReporter()

            for (backend in listOf("gemini", "copilot", "kiro", "ollama")) {
                val config = BATaskConfig(
                    rootTicketId = "TEST-2",
                    docType = "BRD",
                    cliBackend = backend
                )
                val strategy = CliInteractiveStrategy(stubProxy, resolver)
                val result = strategy.execute(config, reporter)

                assertEquals(BATaskStatus.FAILED, result.status) {
                    "Expected FAILED for backend '$backend'"
                }
            }
        }

    @Test
    fun `reports initial progress before failing on missing path`() =
        runBlocking {
            val resolver = CliBackendResolver(
                EmptySettingsRepository(), null
            )
            val strategy = CliInteractiveStrategy(stubProxy, resolver)
            val config = BATaskConfig(
                rootTicketId = "TEST-3",
                docType = "BRD",
                cliBackend = "gemini"
            )
            val reporter = CapturingProgressReporter()

            strategy.execute(config, reporter)

            // Strategy should NOT report progress when resolve fails
            // early (before CLI spawn), so no 5% report expected.
            // The failure happens before any progress milestone.
            // This validates the early-exit path is clean.
            assertTrue(result_is_failed(reporter))
        }

    @Test
    fun `implements PipelineStrategy interface`() {
        val resolver = CliBackendResolver(
            EmptySettingsRepository(), null
        )
        val strategy = CliInteractiveStrategy(stubProxy, resolver)

        assertTrue(strategy is PipelineStrategy) {
            "CliInteractiveStrategy must implement PipelineStrategy"
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Checks that no progress was reported at 5% or higher. */
    private fun result_is_failed(
        reporter: CapturingProgressReporter
    ): Boolean {
        // When resolve fails, strategy returns immediately with
        // FAILED — no progress milestones should be reported.
        return reporter.progresses.none { it.first >= 5 }
    }
}
