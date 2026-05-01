package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Integration tests for the multi-turn pipeline. Req 2.1,4.1,9.1-9.4,10.2,10.3 */
@Tag("multi-turn-ba-orchestration")
class MultiTurnPipelineIntegrationTest {

    @Test
    fun `end-to-end pipeline produces document`() = runBlocking {
        val result = buildStrategy(realisticMgr()).execute(
            BATaskConfig("ICL2-15", "BRD", cliBackend = "gemini"), NOP
        )
        assertEquals(BATaskStatus.SUCCESS, result.status)
        assertTrue(result.document.isNotBlank(), "Document should not be blank")
        assertTrue(result.totalDurationMs >= 0)
        assertEquals(4, result.toolCallsExecuted)
    }

    @Test
    fun `legacy strategy produces result`() = runBlocking {
        val legacy = LegacyToolCallStrategy(simpleMgr(), proxy(), NOP)
        val result = legacy.execute(BATaskConfig("PROJ-1", "BRD", cliBackend = "gemini"), NOP)
        assertNotNull(result)
        assertTrue(result.totalDurationMs >= 0)
    }

    @Test
    fun `config resolution works for all backends`() {
        for (b in listOf("gemini", "copilot", "kiro", "ollama")) {
            val cfg = BATaskConfig("TEST-1", "BRD", cliBackend = b)
            assertEquals(b, cfg.cliBackend)
            assertTrue(b in BATaskConfig.VALID_CLI_BACKENDS)
        }
    }

    @Test
    fun `orchestrator resolves all backends`() = runBlocking {
        for (b in listOf("gemini", "copilot", "kiro", "ollama")) {
            val mgr = realisticMgr()
            val prx = proxy()
            val strategy = MultiTurnPipelineStrategy(
                DataCollector(prx, NOP), PipelineStopCondition(), DocumentAssembler(), mgr
            )
            val orch = BASubprocessOrchestrator(
                mgr, prx, NOP, settingsFor(b), strategy = strategy
            )
            val result = orch.executeTask(BATaskConfig("TEST-1", "BRD", cliBackend = b))
            assertNotNull(result, "Should produce result for $b")
        }
    }

    @Test
    fun `delimiter-based reading stops at END`() = runBlocking {
        val mgr = object : SubprocessManager {
            override suspend fun sendCommand(agentType: String, command: String): Flow<String> =
                flowOf("Line 1", "Line 2", "---END---", "IGNORED")
            override suspend fun isRunning(agentType: String) = true
            override suspend fun terminate(agentType: String) {}
            override suspend fun terminateAll() {}
            override fun getRunningAgentTypes() = listOf("ba-agent")
        }
        val result = buildStrategy(mgr).execute(
            BATaskConfig("DELIM-1", "BRD", cliBackend = "gemini"), NOP
        )
        assertFalse(result.document.contains("IGNORED"), "After ---END--- excluded")
    }

    // ── Helpers ──────────────────────────────────────────

    private fun buildStrategy(m: SubprocessManager) = MultiTurnPipelineStrategy(
        DataCollector(proxy(), NOP), PipelineStopCondition(), DocumentAssembler(), m
    )

    private fun realisticMgr() = object : SubprocessManager {
        override suspend fun sendCommand(agentType: String, command: String): Flow<String> = when {
            command.contains("Analyze") -> flowOf(
                "## Analysis", "Business goals: improve efficiency", "---END---"
            )
            command.contains("expand requirements") -> flowOf(
                "## Requirements", "REQ-1: Auth", "REQ-2: Dashboard", "---END---"
            )
            command.contains("Write a complete") -> flowOf(
                "# BRD", "## Introduction", "Detailed intro content here.",
                "## Business Objectives", "Improve efficiency significantly.",
                "## Stakeholders", "Product team and engineering team.",
                "## Scope", "Full system redesign of all layers.",
                "## Requirements", "REQ-1 and REQ-2 details.",
                "## Assumptions", "Stable API and infrastructure.",
                "## Constraints", "Budget limited to Q3 allocation.",
                "## Risks", "Timeline risk from external deps.",
                "## Appendix", "Reference documents listed here.", "---END---"
            )
            else -> flowOf("Revised document", "---END---")
        }
        override suspend fun isRunning(agentType: String) = true
        override suspend fun terminate(agentType: String) {}
        override suspend fun terminateAll() {}
        override fun getRunningAgentTypes() = listOf("ba-agent")
    }

    private fun simpleMgr() = object : SubprocessManager {
        override suspend fun sendCommand(agentType: String, command: String): Flow<String> =
            flowOf("---END---")
        override suspend fun isRunning(agentType: String) = true
        override suspend fun terminate(agentType: String) {}
        override suspend fun terminateAll() {}
        override fun getRunningAgentTypes() = listOf("ba-agent")
    }

    private fun proxy() = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(r: ToolCallRequest) =
            ToolCallResponse(id = r.id, success = true, data = """{"result":"ok"}""")
        override fun getAvailableToolDescriptors() = emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun settingsFor(backend: String): SettingsRepository {
        val m = mutableMapOf<String, String>()
        when (backend) {
            "gemini" -> { m["ai_cli_path"] = "/usr/bin/gemini"; m["ai_cli_model"] = "pro" }
            "copilot" -> m["copilot_cli_path"] = "/usr/bin/copilot"
            "kiro" -> m["kiro_cli_path"] = "/usr/bin/kiro"
            "ollama" -> { m["ollama_cli_path"] = "/usr/bin/ollama"; m["ollama_cli_model"] = "llama3" }
        }
        return object : SettingsRepository {
            override suspend fun getAll() = m.toMap()
            override suspend fun get(key: String) = m[key]
            override suspend fun put(key: String, value: String) { m[key] = value }
            override suspend fun putAll(settings: Map<String, String>) { m.putAll(settings) }
        }
    }

    companion object {
        private val NOP = object : ProgressReporter {
            override suspend fun reportPhase(phaseName: String, phaseIndex: Int, totalPhases: Int) = Unit
            override suspend fun reportProgress(percent: Int, message: String) = Unit
            override suspend fun reportToolCall(toolName: String, status: String) = Unit
        }
    }
}
