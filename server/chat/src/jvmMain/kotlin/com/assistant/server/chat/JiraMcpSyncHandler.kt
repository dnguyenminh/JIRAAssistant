package com.assistant.server.chat

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.kb.KBRepository
import com.assistant.server.chat.models.SyncResult
import com.assistant.server.chat.models.SyncType
import com.assistant.server.indexing.IndexingPipeline
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles graph sync after Jira MCP tool calls.
 * Detects tool type, extracts ticket data, updates graph, indexes into VectorStore.
 * Requirements: 17.2, 17.3, 17.4, 17.5, 17.6, 18.1, 18.2, 18.3, 18.4, 18.5
 */
class JiraMcpSyncHandler(
    private val kbRepository: KBRepository,
    private val indexingPipeline: IndexingPipeline?
) {
    private val logger = LoggerFactory.getLogger(JiraMcpSyncHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val CREATE_TOOLS = setOf("create_issue", "createIssue", "jira_create_issue")
        private val UPDATE_TOOLS = setOf("update_issue", "updateIssue", "jira_update_issue")
        private val LINK_TOOLS = setOf("link_issues", "linkIssues", "jira_link_issues")
        private val JIRA_TOOLS = CREATE_TOOLS + UPDATE_TOOLS + LINK_TOOLS
        const val SYNC_WARNING = "Ticket updated in Jira but graph sync pending. Refresh to see changes."
    }

    /** Check if a tool name is a Jira tool we should sync. */
    fun isJiraTool(toolName: String): Boolean = JIRA_TOOLS.contains(toolName)

    /** Detect the sync type for a given tool name. */
    fun detectSyncType(toolName: String): SyncType = when {
        CREATE_TOOLS.contains(toolName) -> SyncType.CREATE_TICKET
        UPDATE_TOOLS.contains(toolName) -> SyncType.UPDATE_TICKET
        LINK_TOOLS.contains(toolName) -> SyncType.LINK_TICKETS
        else -> SyncType.NONE
    }

    /**
     * Sync graph after a successful Jira MCP tool call.
     * Returns SyncResult with success/failure and optional warning.
     * Req: 18.1, 18.2, 18.3, 18.4, 18.5
     */
    suspend fun syncAfterToolCall(
        projectKey: String, toolName: String, toolResult: String
    ): SyncResult {
        val syncType = detectSyncType(toolName)
        if (syncType == SyncType.NONE) return SyncResult(true, SyncType.NONE)
        return try {
            val resultJson = parseToolResult(toolResult)
            executeSyncByType(projectKey, syncType, resultJson)
        } catch (e: Exception) {
            logger.warn("Graph sync failed for tool=$toolName: ${e.message}")
            SyncResult(false, syncType, warningMessage = SYNC_WARNING)
        }
    }

    private suspend fun executeSyncByType(
        projectKey: String, syncType: SyncType, resultJson: JsonObject?
    ): SyncResult = when (syncType) {
        SyncType.CREATE_TICKET -> handleCreateTicket(projectKey, resultJson)
        SyncType.UPDATE_TICKET -> handleUpdateTicket(projectKey, resultJson)
        SyncType.LINK_TICKETS -> handleLinkTickets(projectKey, resultJson)
        SyncType.NONE -> SyncResult(true, SyncType.NONE)
    }

    internal suspend fun handleCreateTicket(
        projectKey: String, resultJson: JsonObject?
    ): SyncResult {
        val key = extractString(resultJson, "key") ?: return noKeyResult(SyncType.CREATE_TICKET)
        val id = extractString(resultJson, "id") ?: key
        val summary = extractString(resultJson, "summary") ?: extractString(resultJson, "fields", "summary") ?: key
        val status = extractString(resultJson, "status") ?: "Open"
        val node = TicketNode(id = id, key = key, summary = summary, status = status)
        addNodeToGraph(projectKey, node)
        indexTicket(projectKey, node)
        return SyncResult(true, SyncType.CREATE_TICKET, ticketKey = key)
    }

    internal suspend fun handleUpdateTicket(
        projectKey: String, resultJson: JsonObject?
    ): SyncResult {
        val key = extractString(resultJson, "key") ?: return noKeyResult(SyncType.UPDATE_TICKET)
        val id = extractString(resultJson, "id") ?: key
        val summary = extractString(resultJson, "summary")
        val status = extractString(resultJson, "status")
        updateNodeInGraph(projectKey, id, summary, status)
        val node = TicketNode(id = id, key = key, summary = summary ?: key, status = status ?: "Open")
        indexTicket(projectKey, node)
        return SyncResult(true, SyncType.UPDATE_TICKET, ticketKey = key)
    }

    internal suspend fun handleLinkTickets(
        projectKey: String, resultJson: JsonObject?
    ): SyncResult {
        val sourceKey = extractString(resultJson, "inwardIssue", "key")
            ?: extractString(resultJson, "sourceKey")
        val targetKey = extractString(resultJson, "outwardIssue", "key")
            ?: extractString(resultJson, "targetKey")
        val linkType = extractString(resultJson, "type", "name")
            ?: extractString(resultJson, "linkType") ?: "relates to"
        if (sourceKey == null || targetKey == null) return noKeyResult(SyncType.LINK_TICKETS)
        addEdgeToGraph(projectKey, sourceKey, targetKey, linkType)
        return SyncResult(true, SyncType.LINK_TICKETS, ticketKey = "$sourceKey→$targetKey")
    }

    // --- Graph mutation helpers ---

    private suspend fun addNodeToGraph(projectKey: String, node: TicketNode) {
        val graph = kbRepository.getGraphData(projectKey) ?: return
        if (graph.nodes.any { it.id == node.id || it.key == node.key }) return
        val updated = NetworkGraph(graph.nodes + node, graph.edges)
        kbRepository.saveGraphData(projectKey, updated)
    }

    private suspend fun updateNodeInGraph(
        projectKey: String, nodeId: String, summary: String?, status: String?
    ) {
        val graph = kbRepository.getGraphData(projectKey) ?: return
        val updatedNodes = graph.nodes.map { n ->
            if (n.id == nodeId || n.key == nodeId) {
                n.copy(
                    summary = summary ?: n.summary,
                    status = status ?: n.status
                )
            } else n
        }
        kbRepository.saveGraphData(projectKey, NetworkGraph(updatedNodes, graph.edges))
    }

    private suspend fun addEdgeToGraph(
        projectKey: String, sourceKey: String, targetKey: String, linkType: String
    ) {
        val graph = kbRepository.getGraphData(projectKey) ?: return
        val sourceNode = graph.nodes.find { it.key == sourceKey }
        val targetNode = graph.nodes.find { it.key == targetKey }
        if (sourceNode == null || targetNode == null) return
        val exists = graph.edges.any {
            it.fromId == sourceNode.id && it.toId == targetNode.id
        }
        if (exists) return
        val edge = TicketEdge(sourceNode.id, targetNode.id, linkType, isSemantic = false)
        val updated = NetworkGraph(graph.nodes, graph.edges + edge)
        kbRepository.saveGraphData(projectKey, updated)
    }

    private suspend fun indexTicket(projectKey: String, node: TicketNode) {
        indexingPipeline?.indexTickets(projectKey, listOf(node))
    }

    // --- JSON extraction helpers ---

    internal fun parseToolResult(text: String): JsonObject? = try {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        if (start < 0) null
        else json.parseToJsonElement(trimmed.substring(start)).jsonObject
    } catch (_: Exception) { null }

    private fun extractString(obj: JsonObject?, vararg path: String): String? {
        if (obj == null || path.isEmpty()) return null
        var current: JsonElement = obj
        for (key in path) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return (current as? JsonPrimitive)?.contentOrNull
    }

    private fun noKeyResult(type: SyncType) =
        SyncResult(false, type, warningMessage = SYNC_WARNING)
}
