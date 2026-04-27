package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext
import com.assistant.server.agent.ba.subprocess.pipeline.models.ToolCallOutcome
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Collects data from MCP tools before sending to AI.
 *
 * Calls 4 MCP tools via [SubprocessProxy.handleToolCallRequest]:
 * 1. mcp_jira_get_issue — root ticket data
 * 2. mcp_jira_search — linked tickets
 * 3. mcp_local_knowledge_base_get_ticket_info — KB analysis
 * 4. mcp_local_knowledge_base_search_relationships — dependencies
 *
 * Each call is wrapped in try-catch so failures never stop collection.
 */
class DataCollector(
    private val subprocessProxy: SubprocessProxy,
    private val progressReporter: ProgressReporter
) {

    private val logger = LoggerFactory.getLogger(DataCollector::class.java)

    suspend fun collectData(rootTicketId: String): CollectedContext {
        logger.info("Starting data collection for ticket: {}", rootTicketId)
        val toolCallLog = mutableListOf<ToolCallLogEntry>()

        val rootTicket = collectRootTicket(rootTicketId, toolCallLog)
        val linkedTickets = collectLinkedTickets(rootTicketId, toolCallLog)
        val kbAnalysis = collectKBAnalysis(rootTicketId, toolCallLog)
        val dependencies = collectDependencies(rootTicketId, toolCallLog)

        logger.info(
            "Data collection complete: {}/{} tools succeeded",
            toolCallLog.count { it.success }, toolCallLog.size
        )
        return CollectedContext(
            rootTicketData = rootTicket,
            linkedTicketsData = linkedTickets,
            kbAnalysisData = kbAnalysis,
            dependenciesData = dependencies,
            toolCallLog = toolCallLog
        )
    }

    private suspend fun collectRootTicket(
        ticketId: String,
        log: MutableList<ToolCallLogEntry>
    ): ToolCallOutcome {
        progressReporter.reportProgress(5, "Fetching root ticket $ticketId")
        return callTool(
            toolName = TOOL_JIRA_GET_ISSUE,
            arguments = mapOf("issue_key" to ticketId),
            log = log
        )
    }

    private suspend fun collectLinkedTickets(
        ticketId: String,
        log: MutableList<ToolCallLogEntry>
    ): ToolCallOutcome {
        progressReporter.reportProgress(8, "Searching linked tickets")
        return callTool(
            toolName = TOOL_JIRA_SEARCH,
            arguments = mapOf("jql" to "issue in linkedIssues($ticketId)"),
            log = log
        )
    }

    private suspend fun collectKBAnalysis(
        ticketId: String,
        log: MutableList<ToolCallLogEntry>
    ): ToolCallOutcome {
        progressReporter.reportProgress(11, "Fetching KB analysis")
        return callTool(
            toolName = TOOL_KB_GET_TICKET_INFO,
            arguments = mapOf("ticketId" to ticketId),
            log = log
        )
    }

    private suspend fun collectDependencies(
        ticketId: String,
        log: MutableList<ToolCallLogEntry>
    ): ToolCallOutcome {
        progressReporter.reportProgress(14, "Searching dependencies")
        return callTool(
            toolName = TOOL_KB_SEARCH_RELATIONSHIPS,
            arguments = mapOf("ticketId" to ticketId),
            log = log
        )
    }

    private suspend fun callTool(
        toolName: String,
        arguments: Map<String, String>,
        log: MutableList<ToolCallLogEntry>
    ): ToolCallOutcome {
        val start = System.currentTimeMillis()
        return try {
            val response = subprocessProxy.handleToolCallRequest(
                ToolCallRequest(
                    id = UUID.randomUUID().toString(),
                    name = toolName,
                    arguments = arguments
                )
            )
            val duration = System.currentTimeMillis() - start
            log.add(ToolCallLogEntry(toolName, duration, response.success, response.data.length))
            ToolCallOutcome(toolName, response.success, response.data, duration, response.error.ifEmpty { null })
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger.warn("Tool call failed: {}", toolName, e)
            log.add(ToolCallLogEntry(toolName, duration, false, 0))
            ToolCallOutcome(toolName, false, "", duration, e.message)
        }
    }

    companion object {
        const val TOOL_JIRA_GET_ISSUE = "mcp_jira_get_issue"
        const val TOOL_JIRA_SEARCH = "mcp_jira_search"
        const val TOOL_KB_GET_TICKET_INFO = "mcp_local_knowledge_base_get_ticket_info"
        const val TOOL_KB_SEARCH_RELATIONSHIPS = "mcp_local_knowledge_base_search_relationships"
    }
}
