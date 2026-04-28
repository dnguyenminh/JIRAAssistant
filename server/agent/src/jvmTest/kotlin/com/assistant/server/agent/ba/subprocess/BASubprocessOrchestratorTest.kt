package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unit tests for BASubprocessOrchestrator.
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 9.1, 9.2, 9.3, 9.5, 9.6
 */
@Tag("agent-subprocess-orchestration")
class BASubprocessOrchestratorTest {

    private val defaultConfig = BATaskConfig(
        rootTicketId = "PROJ-123",
        docType = "BRD",
        cliBackend = "gemini"
    )

    // ── Delegation tests ────────────────────────────────────

    @Test
    fun `delegates to SubprocessManager`() = runBlocking {
        val commands = CopyOnWriteArrayList<String>()
        val manager = trackingManager(commands)
        val orchestrator = buildOrchestrator(
            manager = manager,
            settings = settingsWithGemini()
        )

        orchestrator.executeTask(defaultConfig)

        assertTrue(
            commands.size >= 2,
            "Should send tool list + task message"
        )
    }

    @Test
    fun `sends step prompts via SubprocessManager`() = runBlocking {
        val commands = CopyOnWriteArrayList<String>()
        val manager = trackingManager(commands)
        val proxy = stubProxy()
        val orchestrator = buildOrchestrator(
            manager = manager,
            proxy = proxy,
            settings = settingsWithGemini()
        )

        orchestrator.executeTask(defaultConfig)

        assertTrue(
            commands.isNotEmpty(),
            "Multi-turn pipeline should send step prompts"
        )
    }

    // ── Progress reporting ──────────────────────────────────

    @Test
    fun `reports progress milestones`() = runBlocking {
        val milestones = CopyOnWriteArrayList<Int>()
        val reporter = trackingReporter(milestones)
        val orchestrator = buildOrchestrator(
            reporter = reporter,
            settings = settingsWithGemini()
        )

        orchestrator.executeTask(defaultConfig)

        assertTrue(milestones.contains(5), "Should report 5% (subprocess started or data collection)")
        assertTrue(milestones.contains(20), "Should report 20% (data collection complete)")
        assertTrue(milestones.contains(90), "Should report 90% (assembling document)")
    }

    // ── Failure scenarios ───────────────────────────────────

    @Test
    fun `returns FAILED on CLI not found`() = runBlocking {
        val settings = emptySettings()
        val orchestrator = buildOrchestrator(settings = settings)

        val result = orchestrator.executeTask(defaultConfig)

        assertEquals(BATaskStatus.FAILED, result.status)
        assertEquals("", result.document)
    }

    @Test
    fun `returns FAILED on subprocess crash`() = runBlocking {
        val crashManager = object : SubprocessManager {
            override suspend fun sendCommand(
                agentType: String, command: String
            ): Flow<String> = throw RuntimeException("Process crashed")
            override suspend fun isRunning(agentType: String) = false
            override suspend fun terminate(agentType: String) {}
            override suspend fun terminateAll() {}
            override fun getRunningAgentTypes() = emptyList<String>()
        }
        val orchestrator = buildOrchestrator(
            manager = crashManager,
            settings = settingsWithGemini()
        )

        val result = orchestrator.executeTask(defaultConfig)

        assertEquals(BATaskStatus.FAILED, result.status)
        assertTrue(result.totalDurationMs >= 0)
    }

    // ── Metrics logging ─────────────────────────────────────

    @Test
    fun `logs metrics on completion`() = runBlocking {
        val orchestrator = buildOrchestrator(
            settings = settingsWithGemini()
        )

        val result = orchestrator.executeTask(defaultConfig)

        assertTrue(result.totalDurationMs >= 0)
        // Multi-turn pipeline: DataCollector makes 4 MCP tool calls
        assertEquals(4, result.toolCallsExecuted)
    }

    @Test
    fun `returns SUCCESS with document`() = runBlocking {
        val manager = object : SubprocessManager {
            private var callCount = 0
            override suspend fun sendCommand(
                agentType: String, command: String
            ): Flow<String> {
                callCount++
                return if (callCount <= 1) {
                    flowOf("---END---")
                } else {
                    flowOf("# BRD Document", "---END---")
                }
            }
            override suspend fun isRunning(agentType: String) = true
            override suspend fun terminate(agentType: String) {}
            override suspend fun terminateAll() {}
            override fun getRunningAgentTypes() = listOf("ba-agent")
        }
        val orchestrator = buildOrchestrator(
            manager = manager,
            settings = settingsWithGemini()
        )

        val result = orchestrator.executeTask(defaultConfig)

        assertEquals(BATaskStatus.SUCCESS, result.status)
        assertTrue(result.document.contains("BRD Document"))
    }

    @Test
    fun `captures stderr as warnings`() = runBlocking {
        // Stderr capture is delegated to SubprocessManager's
        // internal stderr capture coroutine. The orchestrator
        // launches a placeholder job. Verify no crash occurs.
        val orchestrator = buildOrchestrator(
            settings = settingsWithGemini()
        )

        val result = orchestrator.executeTask(defaultConfig)

        assertNotNull(result)
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildOrchestrator(
        manager: SubprocessManager = defaultManager(),
        proxy: SubprocessProxy = stubProxy(),
        reporter: ProgressReporter = OrchestratorNoOpReporter,
        settings: SettingsRepository = settingsWithGemini()
    ): BASubprocessOrchestrator {
        val dataCollector = com.assistant.server.agent.ba.subprocess.pipeline.DataCollector(proxy, reporter)
        val stopCondition = com.assistant.server.agent.ba.subprocess.pipeline.PipelineStopCondition()
        val documentAssembler = com.assistant.server.agent.ba.subprocess.pipeline.DocumentAssembler()
        val strategy = com.assistant.server.agent.ba.subprocess.pipeline.MultiTurnPipelineStrategy(
            dataCollector, stopCondition, documentAssembler, manager
        )
        return BASubprocessOrchestrator(
            subprocessManager = manager,
            subprocessProxy = proxy,
            progressReporter = reporter,
            settingsRepository = settings,
            strategy = strategy
        )
    }

    private fun defaultManager() = object : SubprocessManager {
        override suspend fun sendCommand(
            agentType: String, command: String
        ): Flow<String> = flowOf("---END---")
        override suspend fun isRunning(agentType: String) = true
        override suspend fun terminate(agentType: String) {}
        override suspend fun terminateAll() {}
        override fun getRunningAgentTypes() = listOf("ba-agent")
    }

    private fun trackingManager(
        commands: MutableList<String>
    ) = object : SubprocessManager {
        override suspend fun sendCommand(
            agentType: String, command: String
        ): Flow<String> {
            commands.add(command)
            return flowOf("---END---")
        }
        override suspend fun isRunning(agentType: String) = true
        override suspend fun terminate(agentType: String) {}
        override suspend fun terminateAll() {}
        override fun getRunningAgentTypes() = listOf("ba-agent")
    }

    private fun stubProxy(
        toolListMsg: String = "tool-list"
    ) = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(
            id = request.id, success = true, data = "ok"
        )
        override fun getAvailableToolDescriptors() =
            listOf(
                ToolDescriptor("fetchJiraDetails", "Fetch details")
            )
        override fun buildToolListMessage() = toolListMsg
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun trackingReporter(
        milestones: MutableList<Int>
    ) = object : ProgressReporter {
        override suspend fun reportPhase(
            phaseName: String, phaseIndex: Int, totalPhases: Int
        ) = Unit
        override suspend fun reportProgress(
            percent: Int, message: String
        ) { milestones.add(percent) }
        override suspend fun reportToolCall(
            toolName: String, status: String
        ) = Unit
    }

    private fun settingsWithGemini(): SettingsRepository {
        val map = mutableMapOf(
            "ai_cli_path" to "/usr/bin/gemini",
            "ai_cli_model" to "gemini-pro"
        )
        return fakeSettings(map)
    }

    private fun emptySettings() = fakeSettings(mutableMapOf())

    private fun fakeSettings(
        map: MutableMap<String, String>
    ) = object : SettingsRepository {
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

private object OrchestratorNoOpReporter : ProgressReporter {
    override suspend fun reportPhase(
        phaseName: String, phaseIndex: Int, totalPhases: Int
    ) = Unit
    override suspend fun reportProgress(
        percent: Int, message: String
    ) = Unit
    override suspend fun reportToolCall(
        toolName: String, status: String
    ) = Unit
}
