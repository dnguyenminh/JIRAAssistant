package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.agent.ba.subprocess.pipeline.AiBackendPipelineStrategy
import com.assistant.server.agent.ba.subprocess.pipeline.MultiTurnPipelineStrategy
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/**
 * Unit tests for [BASubprocessOrchestrator] strategy creation.
 *
 * Verifies:
 * - `createDefaultStrategy()` returns [AiBackendPipelineStrategy]
 * - `createMultiTurnStrategy()` returns [MultiTurnPipelineStrategy]
 *
 * Requirements: 13.1, 13.2, 13.3
 */
class BASubprocessOrchestratorTest {

    @Test
    fun `createDefaultStrategy returns AiBackendPipelineStrategy`() {
        val proxy = stubProxy()
        val settings = stubSettings()

        val strategy = BASubprocessOrchestrator.createDefaultStrategy(
            subprocessProxy = proxy,
            settingsRepository = settings,
            providerConfigRepo = null
        )

        assertIs<AiBackendPipelineStrategy>(strategy)
    }

    @Test
    fun `createMultiTurnStrategy returns MultiTurnPipelineStrategy`() {
        val manager = stubManager()
        val proxy = stubProxy()
        val reporter = stubReporter()

        val strategy = BASubprocessOrchestrator.createMultiTurnStrategy(
            subprocessManager = manager,
            subprocessProxy = proxy,
            progressReporter = reporter
        )

        assertIs<MultiTurnPipelineStrategy>(strategy)
    }

    // ── Stubs ───────────────────────────────────────────────

    private fun stubProxy(): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(request.id, true, "", "")

        override fun getAvailableToolDescriptors() =
            listOf(ToolDescriptor("t", "d", emptyList()))

        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun stubSettings(): SettingsRepository =
        object : SettingsRepository {
            override suspend fun getAll() = emptyMap<String, String>()
            override suspend fun get(key: String): String? = null
            override suspend fun put(key: String, value: String) {}
            override suspend fun putAll(settings: Map<String, String>) {}
        }

    private fun stubManager(): SubprocessManager =
        object : SubprocessManager {
            override suspend fun sendCommand(
                agentType: String, command: String
            ) = flowOf<String>()
            override suspend fun isRunning(agentType: String) = false
            override suspend fun terminate(agentType: String) {}
            override suspend fun terminateAll() {}
            override fun getRunningAgentTypes() = emptyList<String>()
        }

    private fun stubReporter(): ProgressReporter =
        object : ProgressReporter {
            override suspend fun reportPhase(
                phaseName: String, phaseIndex: Int, totalPhases: Int
            ) {}
            override suspend fun reportProgress(
                percent: Int, message: String
            ) {}
            override suspend fun reportToolCall(
                toolName: String, status: String
            ) {}
        }
}
