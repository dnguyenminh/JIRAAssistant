package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Multi-Phase BRD Pipeline.
 * Uses mock AiBackend and SubprocessProxy to verify
 * end-to-end pipeline flow and mode selection.
 */
class MultiPhasePipelineIntegrationTest {

    // ── 8.7 Multi-phase pipeline with mock backend ──────────

    @Test
    fun `Multi-phase pipeline with KB tools uses multi-phase mode`() = runBlocking {
        val tools = listOf(
            ToolDescriptor("mcp_jira_get_issue", "Get Jira issue"),
            ToolDescriptor("mcp_kb_kb_search", "KB search"),
            ToolDescriptor("mcp_kb_kb_ingest", "KB ingest"),
            ToolDescriptor("mcp_kb_kb_read", "KB read")
        )
        val backend = multiPhaseBackend()
        val proxy = successProxy(tools)
        val progress = TrackingReporter()
        val bridge = ToolExecutionBridge(proxy, progress)
        val promptBuilder = AgenticPromptBuilder(proxy)
        val orchestrator = PipelineOrchestrator(
            AgenticLoopRunner(bridge, promptBuilder),
            promptBuilder,
            PhasePromptBuilder(),
            PhaseToolFilter(),
            BrdAssembler(),
            bridge,
            kbRepository = emptyKBRepository()
        )
        val config = PipelineConfig(
            ticketId = "INT-1", docType = "BRD", allTools = tools,
            enableParallelPhases = false
        )

        val result = orchestrator.executePipeline(backend, config, progress)

        assertTrue(result.document.isNotBlank(), "Should produce a document")
    }

    // ── 8.8 Single-phase fallback ───────────────────────────

    @Test
    fun `Single-phase fallback when no KB tools available`() = runBlocking {
        val tools = listOf(
            ToolDescriptor("mcp_jira_get_issue", "Get Jira issue"),
            ToolDescriptor("mcp_jira_search_jira", "Search Jira")
        )
        val backend = singlePhaseBackend()
        val proxy = successProxy(tools)
        val progress = TrackingReporter()
        val bridge = ToolExecutionBridge(proxy, progress)
        val promptBuilder = AgenticPromptBuilder(proxy)
        val orchestrator = PipelineOrchestrator(
            AgenticLoopRunner(bridge, promptBuilder),
            promptBuilder,
            PhasePromptBuilder(),
            PhaseToolFilter(),
            BrdAssembler(),
            bridge
        )
        val config = PipelineConfig(
            ticketId = "INT-2", docType = "BRD", allTools = tools
        )

        val result = orchestrator.executePipeline(backend, config, progress)

        assertTrue(
            result.document.contains("Single Phase Document"),
            "Should produce single-phase document"
        )
        assertEquals(1, result.toolCallsExecuted)
        assertEquals(0, result.toolCallsFailed)
    }

    // ── Mock backends ───────────────────────────────────────

    /**
     * Mock backend for multi-phase test.
     * Each session: first call returns a tool call, second returns text.
     */
    private fun multiPhaseBackend(): AiBackend {
        var sessionCallCount = 0
        return object : AiBackend {
            override val displayName = "MultiPhaseMock"
            override suspend fun sendPrompt(prompt: String) = sendMessage(prompt)
            override fun startSession() { sessionCallCount = 0 }
            override suspend fun sendMessage(message: String): AiCliResponse {
                sessionCallCount++
                return if (sessionCallCount == 1) {
                    AiCliResponse(
                        response = """{"type":"tool_call","tool":"mcp_kb_kb_search","params":{"query":"test"}}"""
                    )
                } else {
                    AiCliResponse(response = "# BRD Document\nGenerated content")
                }
            }
            override fun endSession() {}
            override fun isSessionActive() = false
            override fun isToolCall(response: String) =
                response.contains("\"type\":\"tool_call\"")
            override fun parseToolCall(response: String): ToolRequest =
                ToolRequest(tool = "mcp_kb_kb_search", params = mapOf("query" to "test"))
            override fun isInstalled() = true
            override fun getInstallInstructions() = ""
        }
    }

    /**
     * Mock backend for single-phase fallback test.
     * Returns tool call on first prompt, document on second.
     */
    private fun singlePhaseBackend(): AiBackend {
        var callCount = 0
        return object : AiBackend {
            override val displayName = "SinglePhaseMock"
            override suspend fun sendPrompt(prompt: String) = sendMessage(prompt)
            override fun startSession() { callCount = 0 }
            override suspend fun sendMessage(message: String): AiCliResponse {
                callCount++
                return if (callCount == 1) {
                    AiCliResponse(
                        response = """{"type":"tool_call","tool":"mcp_jira_get_issue","params":{"id":"INT-2"}}"""
                    )
                } else {
                    AiCliResponse(response = "# Single Phase Document\nContent")
                }
            }
            override fun endSession() {}
            override fun isSessionActive() = false
            override fun isToolCall(response: String) =
                response.contains("\"type\":\"tool_call\"")
            override fun parseToolCall(response: String): ToolRequest =
                ToolRequest(tool = "mcp_jira_get_issue", params = mapOf("id" to "INT-2"))
            override fun isInstalled() = true
            override fun getInstallInstructions() = ""
        }
    }

    // ── Mock proxy ──────────────────────────────────────────

    private fun successProxy(tools: List<ToolDescriptor>): SubprocessProxy =
        object : SubprocessProxy {
            override suspend fun handleToolCallRequest(
                request: ToolCallRequest
            ) = ToolCallResponse(request.id, true, "mock data", "")
            override fun getAvailableToolDescriptors() = tools
            override fun buildToolListMessage() = ""
            override fun buildToolsUpdatedMessage() = ""
        }

    // ── Progress tracker ────────────────────────────────────

    private class TrackingReporter : ProgressReporter {
        val progressPercents = mutableListOf<Int>()
        val toolCalls = mutableListOf<String>()

        override suspend fun reportPhase(
            phaseName: String, phaseIndex: Int, totalPhases: Int
        ) {}

        override suspend fun reportProgress(percent: Int, message: String) {
            progressPercents.add(percent)
        }

        override suspend fun reportToolCall(
            toolName: String, status: String
        ) {
            toolCalls.add("$toolName:$status")
        }
    }

    // ── Mock KBRepository ───────────────────────────────────

    private fun emptyKBRepository(): com.assistant.kb.KBRepository =
        object : com.assistant.kb.KBRepository {
            override suspend fun findByTicketId(ticketId: String) = null
            override suspend fun save(record: com.assistant.kb.KBRecord) = true
            override suspend fun overwrite(record: com.assistant.kb.KBRecord) = true
            override suspend fun saveGraphData(
                projectKey: String,
                graph: com.assistant.domain.NetworkGraph
            ) = true
            override suspend fun getGraphData(
                projectKey: String
            ): com.assistant.domain.NetworkGraph? = null
        }
}
