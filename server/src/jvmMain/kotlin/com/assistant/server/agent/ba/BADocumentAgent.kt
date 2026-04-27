package com.assistant.server.agent.ba

import com.assistant.agent.GenericAgent
import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.models.*
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.tool.ToolRegistry
import com.assistant.server.agent.ba.models.BAAgentPayload
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import org.slf4j.LoggerFactory

/**
 * BA Document Agent — specialized agent on the Generic Agent Framework.
 *
 * Delegates all document generation to [BASubprocessOrchestrator].
 * Returns FAILED when the orchestrator is unavailable or reports failure.
 *
 * Lifecycle: onStart → execute → onComplete.
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
 */
class BADocumentAgent(
    private val toolRegistry: ToolRegistry,
    private val memory: StructuredMemory,
    private val progressReporter: ProgressReporter,
    private val subprocessOrchestrator: BASubprocessOrchestrator?
) : GenericAgent {

    private val logger = LoggerFactory.getLogger(BADocumentAgent::class.java)
    private val agentId = java.util.UUID.randomUUID().toString()
    private var state = AgentState(agentId = agentId, agentType = AGENT_TYPE)
    private var startTime = 0L
    private var rootTicketId = ""
    private var docType = "BRD"
    private var cliBackend = ""

    override fun getAgentId(): String = agentId
    override fun getAgentType(): String = AGENT_TYPE
    override fun getState(): AgentState = capReasoningLog(state)

    override suspend fun onStart(input: AgentInput) {
        startTime = System.currentTimeMillis()
        rootTicketId = input.payload[BAAgentPayload.TICKET_ID] ?: ""
        docType = input.payload[BAAgentPayload.DOC_TYPE] ?: "BRD"
        cliBackend = input.payload[BAAgentPayload.CLI_BACKEND]
            ?.takeIf { it.isNotBlank() } ?: ""
        if (cliBackend.isBlank()) {
            logger.error("No AI backend configured in payload")
        }
        logger.info(
            "BADocumentAgent started: ticket={}, docType={}, backend={}",
            rootTicketId, docType, cliBackend
        )
    }

    override suspend fun execute(input: AgentInput): AgentOutput {
        if (cliBackend.isBlank()) {
            return failedOutput(
                input.requestId,
                "No AI backend configured. Go to Settings and select an AI provider."
            )
        }
        val orchestrator = subprocessOrchestrator
            ?: return failedOutput(input.requestId, "Subprocess orchestrator not available")

        val config = BATaskConfig(
            rootTicketId = rootTicketId,
            docType = docType,
            cliBackend = cliBackend
        )
        return try {
            val result = orchestrator.executeTask(config)
            if (result.status == BATaskStatus.FAILED) {
                failedOutput(input.requestId, "Subprocess failed: ${result.document}")
            } else {
                buildOutput(input.requestId, result.document)
            }
        } catch (e: Exception) {
            logger.error("Subprocess execution error: {}", e.message, e)
            failedOutput(input.requestId, "Subprocess error: ${e.message}")
        }
    }

    override suspend fun onComplete(output: AgentOutput) {
        val duration = System.currentTimeMillis() - startTime
        state = state.copy(
            elapsedTimeMs = duration,
            status = AgentStateStatus.COMPLETED
        )
        logMetrics(output, duration)
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun failedOutput(requestId: String, message: String) = AgentOutput(
        requestId = requestId,
        agentType = AGENT_TYPE,
        result = message,
        status = AgentStatus.FAILED
    )

    private fun buildOutput(requestId: String, result: String) = AgentOutput(
        requestId = requestId,
        agentType = AGENT_TYPE,
        result = result,
        status = AgentStatus.SUCCESS
    )

    private fun logMetrics(output: AgentOutput, duration: Long) {
        logger.info(
            "BADocumentAgent completed: duration={}ms, toolCalls={}, resultSize={}",
            duration, output.toolCallCount, output.result.length
        )
    }

    companion object {
        const val AGENT_TYPE = "ba-document"
        private const val MAX_BA_REASONING_LOG = 50

        fun capReasoningLog(state: AgentState): AgentState {
            if (state.reasoningLog.size <= MAX_BA_REASONING_LOG) {
                return state
            }
            return state.copy(
                reasoningLog = state.reasoningLog
                    .takeLast(MAX_BA_REASONING_LOG)
            )
        }
    }
}
