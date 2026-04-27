package com.assistant.server.agent.ba

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.models.AgentInput
import com.assistant.agent.models.AgentStatus
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolResult
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import com.assistant.server.agent.ba.models.BAAgentPayload
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.settings.SettingsRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Tests for BADocumentAgent subprocess-only execution.
 *
 * Validates: Requirements 12.1, 12.2
 * - No feature flag routing
 * - No legacy fallback scenarios
 * - Subprocess is the single execution path
 */
class BADocumentAgentSubprocessTest {

    @Test
    fun `subprocess success returns document`() = runBlocking {
        val orch = StubOrchestrator(BATaskStatus.SUCCESS, "# BRD Doc")
        val agent = buildAgent(orch)
        agent.onStart(buildInput())
        val output = agent.execute(buildInput())
        output.status shouldBe AgentStatus.SUCCESS
        output.result shouldBe "# BRD Doc"
    }

    @Test
    fun `subprocess FAILED returns FAILED status`() = runBlocking {
        val orch = StubOrchestrator(BATaskStatus.FAILED, "error detail")
        val agent = buildAgent(orch)
        agent.onStart(buildInput())
        val output = agent.execute(buildInput())
        output.status shouldBe AgentStatus.FAILED
        output.result shouldBe "Subprocess failed: error detail"
    }

    @Test
    fun `null orchestrator returns FAILED status`() = runBlocking {
        val agent = buildAgent(orchestrator = null)
        agent.onStart(buildInput())
        val output = agent.execute(buildInput())
        output.status shouldBe AgentStatus.FAILED
        output.result shouldBe "Subprocess orchestrator not available"
    }

    @Test
    fun `subprocess exception returns FAILED status`() = runBlocking {
        val orch = ThrowingOrchestrator("connection refused")
        val agent = buildAgent(orch)
        agent.onStart(buildInput())
        val output = agent.execute(buildInput())
        output.status shouldBe AgentStatus.FAILED
        output.result shouldBe "Subprocess error: connection refused"
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildAgent(
        orchestrator: BASubprocessOrchestrator?
    ) = BADocumentAgent(
        toolRegistry = NoOpToolRegistry(),
        memory = JiraContextMemorySchema.createMemory(),
        progressReporter = NoOpReporter(),
        subprocessOrchestrator = orchestrator
    )

    private fun buildInput(
        ticketId: String = "PROJ-1",
        docType: String = "BRD"
    ) = AgentInput(
        requestId = "req-1",
        agentType = "ba-document",
        payload = mapOf(
            BAAgentPayload.TICKET_ID to ticketId,
            BAAgentPayload.DOC_TYPE to docType
        )
    )
}

// ── Test doubles ─────────────────────────────────────────────

/** Orchestrator stub returning a fixed result. */
private class StubOrchestrator(
    private val status: BATaskStatus,
    private val document: String
) : BASubprocessOrchestrator(
    subprocessManager = DummySubprocessManager(),
    subprocessProxy = DummySubprocessProxy(),
    progressReporter = NoOpReporter(),
    settingsRepository = EmptySettingsRepo()
) {
    override suspend fun executeTask(
        config: BATaskConfig
    ) = BATaskResult(
        document = document,
        toolCallsExecuted = 0,
        toolCallsFailed = 0,
        totalDurationMs = 0,
        status = status
    )
}

/** Orchestrator stub that throws on executeTask. */
private class ThrowingOrchestrator(
    private val errorMessage: String
) : BASubprocessOrchestrator(
    subprocessManager = DummySubprocessManager(),
    subprocessProxy = DummySubprocessProxy(),
    progressReporter = NoOpReporter(),
    settingsRepository = EmptySettingsRepo()
) {
    override suspend fun executeTask(
        config: BATaskConfig
    ): BATaskResult = throw RuntimeException(errorMessage)
}

private class NoOpReporter : ProgressReporter {
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

private class NoOpToolRegistry : ToolRegistry {
    override fun register(tool: AgentTool) {}
    override fun registerAll(tools: List<AgentTool>) {}
    override fun listTools() = emptyList<ToolDescriptor>()
    override suspend fun invoke(
        toolName: String, params: Map<String, String>
    ) = ToolResult(toolName = toolName, success = false)
    override fun getRemainingCalls() = 50
    override fun resetCallCount() {}
}

private class DummySubprocessManager : SubprocessManager {
    override suspend fun sendCommand(
        agentType: String, command: String
    ) = kotlinx.coroutines.flow.flowOf<String>()
    override suspend fun isRunning(agentType: String) = false
    override suspend fun terminate(agentType: String) {}
    override suspend fun terminateAll() {}
    override fun getRunningAgentTypes() = emptyList<String>()
}

private class DummySubprocessProxy : SubprocessProxy {
    override suspend fun handleToolCallRequest(
        request: ToolCallRequest
    ) = ToolCallResponse(request.id, true, "ok")
    override fun getAvailableToolDescriptors() =
        emptyList<ToolDescriptor>()
    override fun buildToolListMessage() = ""
    override fun buildToolsUpdatedMessage() = ""
}

private class EmptySettingsRepo : SettingsRepository {
    override suspend fun getAll() = emptyMap<String, String>()
    override suspend fun get(key: String): String? = null
    override suspend fun put(key: String, value: String) {}
    override suspend fun putAll(
        settings: Map<String, String>
    ) {}
}
