package com.assistant.server.chat

import com.assistant.ai.AIAgent
import com.assistant.ai.AIResult
import com.assistant.chat.*
import com.assistant.graph.GraphEngine
import com.assistant.kb.KBRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.attachment.models.ChunkType
import com.assistant.server.indexing.IndexingPipeline
import kotlinx.serialization.json.Json

/**
 * ChatService implementation with KB + Graph context + AI personalization + MCP tools.
 * Requirements: 19.5, 19.6, 19.9, 19.10, 19.11, 19.41, 19.42, 6.52, 6.53, 6.55, 17.2, 18.1
 */
class ChatServiceImpl(
    private val aiAgentProvider: () -> AIAgent,
    private val kbRepository: KBRepository,
    private val graphEngine: GraphEngine,
    private val userAIConfigRepository: UserAIConfigRepository? = null,
    private val mcpProcessManager: McpProcessManager? = null,
    private val embeddingService: EmbeddingService? = null,
    private val vectorStore: VectorStore? = null,
    private val indexingPipeline: IndexingPipeline? = null
) : ChatService {

    /** Sync handler for Jira MCP tool responses → graph updates. Req: 18.1 */
    internal val jiraSyncHandler: JiraMcpSyncHandler? by lazy {
        indexingPipeline?.let { JiraMcpSyncHandler(kbRepository, it) }
            ?: JiraMcpSyncHandler(kbRepository, null)
    }

    /** Sync handler for Confluence MCP tool responses → VectorStore indexing. Req: 19.4 */
    internal val confluenceSyncHandler: ConfluenceMcpSyncHandler by lazy {
        ConfluenceMcpSyncHandler(indexingPipeline)
    }

    companion object {
        private const val MAX_HISTORY = 20
        private val TICKET_PATTERN = Regex("[A-Z]+-\\d+")
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun processChat(
        message: String, context: ChatContext,
        conversationHistory: List<ChatMessage>
    ): ChatResponse {
        val prompt = buildFullPrompt(message, context, conversationHistory)
        return McpAgenticLoop.execute(
            prompt, mcpProcessManager, ::callAI, ::toResponse,
            jiraSyncHandler, context.projectKey, confluenceSyncHandler
        )
    }

    override fun buildSystemPrompt(context: ChatContext): String = buildBasePrompt(context)

    private suspend fun buildFullPrompt(
        message: String, context: ChatContext, history: List<ChatMessage>
    ): String {
        val base = buildBasePrompt(context)
        val personalization = buildPersonalization(context.userId)
        val kb = buildKBContext(context.projectKey, message)
        val graph = buildGraphContext(context.projectKey)
        val graphState = buildGraphStateContext(context.graphContext)
        val mcpTools = buildMcpToolsContext()
        val knowledgeCtx = buildKnowledgeContext(context.projectKey, message)
        val hist = formatHistory(history)
        return "$base\n$personalization\n--- KB ---\n$kb\n--- GRAPH ---\n$graph\n$graphState\n--- MCP TOOLS ---\n$mcpTools\n--- KNOWLEDGE CONTEXT ---\n$knowledgeCtx\n--- HISTORY ---\n$hist\n--- USER ---\n$message"
    }

    /** Build context string from frontend graph state (focused node, filters, etc.) */
    private fun buildGraphStateContext(gc: com.assistant.chat.GraphChatContext?): String {
        if (gc == null) return "User is NOT on the Knowledge Graph page."
        val parts = mutableListOf("User is viewing the Knowledge Graph.")
        gc.focusedNodeKey?.let { parts.add("Focused on node: $it") }
        if (gc.activeTypeFilters.isNotEmpty()) parts.add("Active type filters: ${gc.activeTypeFilters.joinToString(", ")}")
        gc.selectedClusterId?.let { parts.add("Selected cluster: $it") }
        if (gc.searchQuery.isNotBlank()) parts.add("Search query: ${gc.searchQuery}")
        parts.add("Visible nodes: ${gc.visibleNodeCount}, Depth: ${gc.depthValue}")
        return parts.joinToString(" ")
    }

    internal fun buildMcpToolsContext(): String {
        val pm = mcpProcessManager ?: return ""
        val tools = pm.getActiveTools()
        if (tools.isEmpty()) return ""
        val lines = tools.joinToString("\n") { "[MCP:${it.serverName}] ${it.name}: ${it.description}" }
        val instruction = """To use a tool, respond with JSON: {"mcpToolCall": {"serverId": "...", "toolName": "...", "arguments": {...}}}"""
        val confluenceHint = buildConfluenceHint(tools)
        return "Available MCP Tools:\n$lines\n$instruction$confluenceHint"
    }

    private fun buildConfluenceHint(tools: List<com.assistant.mcp.models.McpAggregatedTool>): String {
        val hasConfluence = tools.any {
            it.name.contains("confluence", ignoreCase = true) ||
                it.serverName.contains("atlassian", ignoreCase = true)
        }
        if (!hasConfluence) return ""
        return "\nConfluence tools are available. When user asks about documentation, " +
            "use Confluence search tools. Include openUrl actions for each page found."
    }

    private fun buildBasePrompt(ctx: ChatContext): String {
        val actions = """
            Available actions (include in "actions" array when relevant):
            - {"type":"focusNode","label":"Focus ICL2-XX","params":{"nodeKey":"ICL2-XX"}} — focus on a node in graph
            - {"type":"filterByType","label":"Show only Bugs","params":{"types":"Bug"}} — filter by node type
            - {"type":"filterByCluster","label":"Show cluster N","params":{"clusterId":"N"}} — filter by cluster
            - {"type":"resetFilters","label":"Show All"} — reset all filters
            - {"type":"searchNodes","label":"Search X","params":{"query":"X"}} — search nodes
            - {"type":"navigateToGraph","label":"Go to Graph"} — navigate to Knowledge Graph
            - {"type":"navigate","label":"Go to Page","params":{"screen":"dashboard|analysis|integrations"}} — navigate to page
            - {"type":"openUrl","label":"Open Link","params":{"url":"https://..."}} — open URL in new tab
        """.trimIndent()
        return """
            Bạn là trợ lý AI của Jira Assistant, hỗ trợ quản lý dự án Agile.
            Project: ${ctx.projectKey} | Screen: ${ctx.currentScreen} | Role: ${ctx.userRole}
            ALWAYS respond in JSON format: {"reply":"your text here","actions":[...],"references":["ticketKey1",...]}
            - "reply": your response text (markdown supported)
            - "actions": clickable buttons for user (use action types below)
            - "references": ticket keys mentioned in your reply
            $actions
            When user asks about a specific ticket, include focusNode action.
            When user is on knowledge_graph, use graph context to give relevant answers.
        """.trimIndent()
    }

    private suspend fun buildPersonalization(userId: String): String {
        val config = userAIConfigRepository?.findByUserId(userId) ?: return ""
        return buildString {
            if (config.skills.isNotBlank()) appendLine("Skills: ${config.skills}")
            if (config.workflow.isNotBlank()) appendLine("Workflow: ${config.workflow}")
            if (config.instructions.isNotBlank()) appendLine("Instructions: ${config.instructions}")
            if (config.rules.isNotBlank()) appendLine("RULES: ${config.rules}")
        }
    }

    internal suspend fun buildKBContext(projectKey: String, message: String): String {
        val tickets = TICKET_PATTERN.findAll(message).map { it.value }.toList()
        if (tickets.isEmpty()) return "No KB data."
        val records = tickets.mapNotNull { kbRepository.findByTicketId(it) }
        if (records.isEmpty()) return "No KB data."
        return records.joinToString("\n") { r ->
            "${r.ticketId}: ${r.requirementSummary} | SP:${r.scrumPoints} | Conf:${r.confidenceScore}"
        }
    }

    internal suspend fun buildGraphContext(projectKey: String): String {
        val graph = kbRepository.getGraphData(projectKey) ?: return "No graph data."
        val clusters = graphEngine.detectClusters(graph)
        return "Graph: ${graph.nodes.size} nodes, ${graph.edges.size} edges, ${clusters.size} clusters"
    }

    internal suspend fun buildKnowledgeContext(projectKey: String, message: String): String {
        val es = embeddingService ?: return ""
        val vs = vectorStore ?: return ""
        val queryEmb = es.embed(message) ?: return ""
        val chunks = vs.search(queryEmb, topK = 10, chunkType = null)
        if (chunks.isEmpty()) return "No attachment data."
        return formatKnowledgeChunks(chunks)
    }

    internal fun formatKnowledgeChunks(chunks: List<AttachmentChunk>): String {
        val grouped = chunks.groupBy { mapChunkTypeToSection(it.chunkType) }
        val sectionOrder = listOf(
            "--- RELEVANT TICKETS ---",
            "--- RELATIONSHIPS ---",
            "--- ANALYSIS ---",
            "--- CONFLUENCE DOCS ---",
            "--- ATTACHMENTS ---"
        )
        return sectionOrder
            .filter { grouped.containsKey(it) }
            .joinToString("\n") { section ->
                val items = grouped[section]!!.joinToString("\n") { "[${it.filename}] ${it.chunkText}" }
                "$section\n$items"
            }
    }

    private fun mapChunkTypeToSection(chunkType: String): String = when (chunkType) {
        ChunkType.TICKET, ChunkType.CLUSTER -> "--- RELEVANT TICKETS ---"
        ChunkType.RELATIONSHIP -> "--- RELATIONSHIPS ---"
        ChunkType.ANALYSIS, ChunkType.EVOLUTION -> "--- ANALYSIS ---"
        ChunkType.CONFLUENCE -> "--- CONFLUENCE DOCS ---"
        else -> "--- ATTACHMENTS ---"
    }

    private fun formatHistory(history: List<ChatMessage>): String =
        history.takeLast(MAX_HISTORY).joinToString("\n") { "${it.role}: ${it.message}" }

    internal suspend fun callAI(prompt: String): AIResult = try {
        val agent = aiAgentProvider()
        agent.analyze(prompt)
    } catch (e: Exception) {
        AIResult.Failure("AI provider unavailable: ${e.message}")
    }

    internal fun toResponse(result: AIResult, promptLen: Int): ChatResponse {
        val usage = ((promptLen.toDouble() / 32000) * 100).toInt().coerceIn(0, 100)
        return when (result) {
            is AIResult.Success -> parseAIResponse(result.response, usage)
            is AIResult.Failure -> ChatResponse(
                reply = "Error: ${result.error}", contextUsage = usage
            )
        }
    }

    internal fun parseAIResponse(raw: String, usage: Int): ChatResponse = try {
        val cleaned = stripCodeFences(raw)
        json.decodeFromString<ChatResponse>(cleaned).copy(contextUsage = usage)
    } catch (_: Exception) {
        // Try to find JSON object anywhere in the response
        val extracted = extractJsonObject(raw)
        if (extracted != null) {
            try {
                json.decodeFromString<ChatResponse>(extracted).copy(contextUsage = usage)
            } catch (_: Exception) {
                ChatResponse(reply = raw, contextUsage = usage)
            }
        } else {
            ChatResponse(reply = raw, contextUsage = usage)
        }
    }

    /** Extract first JSON object {...} from text, handling mixed text+JSON responses. */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun stripCodeFences(text: String): String {
        val t = text.trim()
        val start = t.indexOf("```")
        if (start < 0) return t
        val after = t.substring(start + 3)
        val nl = after.indexOf('\n')
        if (nl < 0) return after.trim()
        val content = after.substring(nl + 1)
        val end = content.lastIndexOf("```")
        return if (end >= 0) content.substring(0, end).trim() else content.trim()
    }
}
