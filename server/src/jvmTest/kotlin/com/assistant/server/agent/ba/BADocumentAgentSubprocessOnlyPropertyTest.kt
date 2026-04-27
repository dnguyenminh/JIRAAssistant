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
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import com.assistant.server.agent.ba.models.BAAgentPayload
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.settings.SettingsRepository
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 1: BADocumentAgent subprocess-only delegation.
 *
 * For any valid AgentInput with random ticket IDs and doc types,
 * when execute() is called with a non-null orchestrator,
 * executeTask() is invoked exactly once.
 *
 * **Validates: Requirements 4.1, 4.2, 4.3**
 *
 * Feature: legacy-pipeline-removal, Property 1: BADocumentAgent subprocess-only
 */
@Tag("legacy-pipeline-removal")
class BADocumentAgentSubprocessOnlyPropertyTest {

    @Test
    fun `Property 1 - execute always delegates to subprocess exactly once`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.string(minSize = 1, maxSize = 20),
            Arb.element("BRD", "FSD", "SLIDES")
        ) { ticketId, docType ->
            val orch = CountingOrchestrator()
            val agent = buildAgent(orch)
            val input = buildInput(ticketId, docType)

            agent.onStart(input)
            val output = agent.execute(input)

            assertEquals(1, orch.callCount, "executeTask must be called exactly once")
            assertEquals(AgentStatus.SUCCESS, output.status)
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildAgent(orch: BASubprocessOrchestrator) =
        BADocumentAgent(
            toolRegistry = NoOpToolRegistryProp(),
            memory = JiraContextMemorySchema.createMemory(),
            progressReporter = NoOpReporterProp(),
            subprocessOrchestrator = orch
        )

    private fun buildInput(ticketId: String, docType: String) =
        AgentInput(
            requestId = "req-prop",
            agentType = "ba-document",
            payload = mapOf(
                BAAgentPayload.TICKET_ID to ticketId,
                BAAgentPayload.DOC_TYPE to docType
            )
        )
}

// ── Test doubles ─────────────────────────────────────────────

private class CountingOrchestrator : BASubprocessOrchestrator(
    subprocessManager = StubManager(),
    subprocessProxy = StubProxy(),
    progressReporter = NoOpReporterProp(),
    settingsRepository = StubSettings()
) {
    var callCount = 0

    override suspend fun executeTask(config: BATaskConfig): BATaskResult {
        callCount++
        return BATaskResult(
            document = "# Doc",
            toolCallsExecuted = 0,
            toolCallsFailed = 0,
            totalDurationMs = 0,
            status = BATaskStatus.SUCCESS
        )
    }
}

private class NoOpReporterProp : ProgressReporter {
    override suspend fun reportPhase(phaseName: String, phaseIndex: Int, totalPhases: Int) {}
    override suspend fun reportProgress(percent: Int, message: String) {}
    override suspend fun reportToolCall(toolName: String, status: String) {}
}

private class NoOpToolRegistryProp : ToolRegistry {
    override fun register(tool: AgentTool) {}
    override fun registerAll(tools: List<AgentTool>) {}
    override fun listTools() = emptyList<ToolDescriptor>()
    override suspend fun invoke(toolName: String, params: Map<String, String>) =
        ToolResult(toolName = toolName, success = false)
    override fun getRemainingCalls() = 50
    override fun resetCallCount() {}
}

private class StubManager : SubprocessManager {
    override suspend fun sendCommand(agentType: String, command: String) = emptyFlow<String>()
    override suspend fun isRunning(agentType: String) = false
    override suspend fun terminate(agentType: String) {}
    override suspend fun terminateAll() {}
    override fun getRunningAgentTypes() = emptyList<String>()
}

private class StubProxy : SubprocessProxy {
    override suspend fun handleToolCallRequest(request: ToolCallRequest) =
        ToolCallResponse(request.id, true, "ok")
    override fun getAvailableToolDescriptors() = emptyList<ToolDescriptor>()
    override fun buildToolListMessage() = ""
    override fun buildToolsUpdatedMessage() = ""
}

private class StubSettings : SettingsRepository {
    override suspend fun getAll() = emptyMap<String, String>()
    override suspend fun get(key: String): String? = null
    override suspend fun put(key: String, value: String) {}
    override suspend fun putAll(settings: Map<String, String>) {}
}
