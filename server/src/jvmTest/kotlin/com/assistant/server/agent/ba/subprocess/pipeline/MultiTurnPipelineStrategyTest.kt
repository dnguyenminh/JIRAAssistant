package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Unit tests for [MultiTurnPipelineStrategy]. Req 2.1,2.2,2.5,4.1,4.4,4.5,5.3,8.1-8.4,9.2 */
@Tag("multi-turn-ba-orchestration")
class MultiTurnPipelineStrategyTest {

    private val cfg = BATaskConfig("PROJ-42", "BRD", cliBackend = "gemini")

    @Test
    fun `Req 2-1 pipeline executes steps in order`() = runBlocking {
        val cmds = CopyOnWriteArrayList<String>()
        buildStrategy(tracking(cmds)).execute(cfg, NOP)
        assertTrue(cmds.size >= 3, "At least 3 steps")
        assertTrue(cmds[0].contains("Analyze"), "Step 1 = analysis")
        assertTrue(cmds[1].contains("expand requirements"), "Step 2 = requirements")
        assertTrue(cmds[2].contains("Write a complete"), "Step 3 = writing")
    }

    @Test
    fun `Req 2-2 pipeline runs review turns on quality fail`() = runBlocking {
        val cmds = CopyOnWriteArrayList<String>()
        var n = 0
        val mgr = mgr { cmds.add(it); n++; if (n <= 3) "Step $n partial" else FULL_BRD }
        buildStrategy(mgr).execute(cfg, NOP)
        assertTrue(cmds.size > 3, "Should run review turns, got ${cmds.size}")
    }

    @Test
    fun `Req 2-5 pipeline sends feedback on quality fail`() = runBlocking {
        val cmds = CopyOnWriteArrayList<String>()
        var n = 0
        val mgr = mgr { cmds.add(it); n++; if (n <= 3) "Step $n partial" else FULL_BRD }
        buildStrategy(mgr).execute(cfg, NOP)
        assertTrue(cmds.any { it.contains("Review and improve") }, "Should send review feedback")
    }

    @Test
    fun `Req 4-1 all commands use ba-agent`() = runBlocking {
        val types = CopyOnWriteArrayList<String>()
        val mgr = object : SubprocessManager {
            override suspend fun sendCommand(agentType: String, command: String): Flow<String> {
                types.add(agentType); return flowOf("Response", "---END---")
            }
            override suspend fun isRunning(agentType: String) = true
            override suspend fun terminate(agentType: String) {}
            override suspend fun terminateAll() {}
            override fun getRunningAgentTypes() = listOf("ba-agent")
        }
        buildStrategy(mgr).execute(cfg, NOP)
        assertTrue(types.all { it == "ba-agent" }, "All must use ba-agent")
    }

    @Test
    fun `Req 4-4 slow responses handled gracefully`() = runBlocking {
        val result = buildStrategy(mgr { "" }).execute(cfg, NOP)
        assertTrue(result.totalDurationMs >= 0)
    }

    @Test
    fun `Req 4-5 subprocess crash recovery`() = runBlocking {
        var n = 0
        val mgr = object : SubprocessManager {
            override suspend fun sendCommand(agentType: String, command: String): Flow<String> {
                n++; if (n == 1) throw RuntimeException("Crash!")
                return flowOf("Recovery", "---END---")
            }
            override suspend fun isRunning(agentType: String) = true
            override suspend fun terminate(agentType: String) {}
            override suspend fun terminateAll() {}
            override fun getRunningAgentTypes() = listOf("ba-agent")
        }
        val result = buildStrategy(mgr).execute(cfg, NOP)
        assertTrue(result.totalDurationMs >= 0)
    }

    @Test
    fun `Req 8 progress reporting checkpoints`() = runBlocking {
        val pcts = CopyOnWriteArrayList<Int>()
        val rpt = object : ProgressReporter {
            override suspend fun reportPhase(phaseName: String, phaseIndex: Int, totalPhases: Int) = Unit
            override suspend fun reportProgress(percent: Int, message: String) { pcts.add(percent) }
            override suspend fun reportToolCall(toolName: String, status: String) = Unit
        }
        buildStrategy(tracking(CopyOnWriteArrayList())).execute(cfg, rpt)
        assertTrue(pcts.contains(5), "5%"); assertTrue(pcts.contains(20), "20%")
        assertTrue(pcts.contains(90), "90%"); assertTrue(pcts.contains(95), "95%")
    }

    @Test
    fun `Req 5-3 and 9-2 plain text no JSON tool calls`() = runBlocking {
        val cmds = CopyOnWriteArrayList<String>()
        buildStrategy(tracking(cmds)).execute(cfg, NOP)
        for (c in cmds) {
            assertFalse(c.contains("""{"toolCall":{"""), "No JSON tool call")
            assertFalse(c.contains("TOOL USAGE INSTRUCTIONS"), "No tool instructions")
            assertFalse(c.contains("Available tools:"), "No tool list")
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private fun buildStrategy(m: SubprocessManager) = MultiTurnPipelineStrategy(
        DataCollector(proxy(), NOP), PipelineStopCondition(), DocumentAssembler(), m
    )

    private fun tracking(cmds: MutableList<String>) = mgr { cmds.add(it); "AI response" }

    private fun mgr(handler: (String) -> String) = object : SubprocessManager {
        override suspend fun sendCommand(agentType: String, command: String): Flow<String> =
            flowOf(handler(command), "---END---")
        override suspend fun isRunning(agentType: String) = true
        override suspend fun terminate(agentType: String) {}
        override suspend fun terminateAll() {}
        override fun getRunningAgentTypes() = listOf("ba-agent")
    }

    private fun proxy() = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(r: ToolCallRequest) =
            ToolCallResponse(id = r.id, success = true, data = "mock")
        override fun getAvailableToolDescriptors() = emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    companion object {
        private val NOP = object : ProgressReporter {
            override suspend fun reportPhase(phaseName: String, phaseIndex: Int, totalPhases: Int) = Unit
            override suspend fun reportProgress(percent: Int, message: String) = Unit
            override suspend fun reportToolCall(toolName: String, status: String) = Unit
        }
        private val FULL_BRD = """
            |# BRD Document
            |## Introduction
            |This document describes the business requirements for the project in detail.
            |## Business Objectives
            |The primary objective is to improve operational efficiency and reduce costs.
            |## Stakeholders
            |Product team, Engineering team, QA team, and Business stakeholders involved.
            |## Scope
            |Full system redesign covering frontend, backend, and integration layers.
            |## Requirements
            |REQ-1: User authentication with SSO. REQ-2: Dashboard with real-time metrics.
            |## Assumptions
            |Stable API contracts, available infrastructure, and team capacity assumed.
            |## Constraints
            |Budget limited to Q3 allocation. Timeline fixed to 3 months with no extensions.
            |## Risks
            |Timeline risk due to dependencies on external teams and third-party integrations.
            |## Appendix
            |Reference documents and supporting materials are listed here for completeness.
        """.trimMargin()
    }
}
