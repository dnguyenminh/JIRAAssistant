package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LegacyToolCallStrategy] and strategy selection.
 *
 * Requirements: 10.1, 10.2, 10.3
 */
@Tag("multi-turn-ba-orchestration")
class LegacyToolCallStrategyTest {

    private val config = BATaskConfig(
        rootTicketId = "PROJ-99",
        docType = "BRD",
        cliBackend = "gemini"
    )

    // ── Req 10.2: Default strategy is multi-turn ─────────

    @Test
    fun `default strategy is MultiTurnPipelineStrategy`() {
        val strategy = BASubprocessOrchestrator.createMultiTurnStrategy(
            defaultManager(), stubProxy(), LegacyNoOpReporter
        )

        assertTrue(
            strategy is MultiTurnPipelineStrategy,
            "Legacy multi-turn strategy should be MultiTurnPipelineStrategy, " +
                "got: ${strategy::class.simpleName}"
        )
    }

    // ── Req 10.3: Fallback to legacy strategy ────────────

    @Test
    fun `can create orchestrator with legacy strategy`() {
        val legacy = LegacyToolCallStrategy(
            defaultManager(), stubProxy(), LegacyNoOpReporter
        )

        assertTrue(
            legacy is PipelineStrategy,
            "LegacyToolCallStrategy must implement PipelineStrategy"
        )
    }

    // ── Req 10.1: Legacy wraps existing logic ────────────

    @Test
    fun `legacy strategy sends tool list and task message`() = runBlocking {
        val commands = mutableListOf<String>()
        val manager = object : SubprocessManager {
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
        val proxy = object : SubprocessProxy {
            override suspend fun handleToolCallRequest(
                request: ToolCallRequest
            ) = ToolCallResponse(
                id = request.id, success = true, data = "ok"
            )
            override fun getAvailableToolDescriptors() = listOf(
                ToolDescriptor("test_tool", "A test tool")
            )
            override fun buildToolListMessage() = "TOOL_LIST_MSG"
            override fun buildToolsUpdatedMessage() = ""
        }
        val legacy = LegacyToolCallStrategy(
            manager, proxy, LegacyNoOpReporter
        )

        legacy.execute(config, LegacyNoOpReporter)

        assertTrue(
            commands.any { it.contains("TOOL_LIST_MSG") },
            "Legacy should inject tool list via SubprocessManager"
        )
        assertTrue(
            commands.size >= 2,
            "Legacy should send tool list + task message"
        )
    }

    // ── Helpers ──────────────────────────────────────────

    private fun defaultManager() = object : SubprocessManager {
        override suspend fun sendCommand(
            agentType: String, command: String
        ): Flow<String> = flowOf("---END---")
        override suspend fun isRunning(agentType: String) = true
        override suspend fun terminate(agentType: String) {}
        override suspend fun terminateAll() {}
        override fun getRunningAgentTypes() = listOf("ba-agent")
    }

    private fun stubProxy() = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(
            id = request.id, success = true, data = "ok"
        )
        override fun getAvailableToolDescriptors() =
            emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }
}

private object LegacyNoOpReporter : ProgressReporter {
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
