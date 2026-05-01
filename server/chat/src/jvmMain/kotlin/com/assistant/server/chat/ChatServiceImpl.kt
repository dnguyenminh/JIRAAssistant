package com.assistant.server.chat

import com.assistant.ai.AIAgent
import com.assistant.ai.AIResult
import com.assistant.ai.OllamaChatAgent
import com.assistant.chat.*
import com.assistant.graph.GraphEngine
import com.assistant.kb.KBRepository
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.chat.models.AIModelContext
import com.assistant.server.indexing.IndexingPipeline
import com.assistant.settings.SettingsRepository

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
    private val indexingPipeline: IndexingPipeline? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val localKBToolExecutor: LocalKBToolExecutor? = null,
    private val providerConfigRepository: ProviderConfigRepository? = null,
    private val internalMcpBridge: com.assistant.server.mcp.internal.InternalMcpBridge? = null,
    private val mcpServerRepository: com.assistant.mcp.McpServerRepository? = null,
    private val userToolPermissionService: UserToolPermissionService? = null
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
    }

    override suspend fun processChat(
        message: String, context: ChatContext,
        conversationHistory: List<ChatMessage>
    ): ChatResponse {
        val agent = aiAgentProvider()
        if (agent is OllamaChatAgent) {
            return processChatNative(agent, message, context, conversationHistory)
        }
        return processChatLegacy(message, context, conversationHistory)
    }

    /** Native tool calling path via OllamaChatAgent. */
    private suspend fun processChatNative(
        agent: OllamaChatAgent, message: String,
        context: ChatContext, history: List<ChatMessage>
    ): ChatResponse = try {
        val systemPrompt = buildNativeSystemPrompt(message, context, history)
        val tools = OllamaToolConverter.buildToolDefs(
            internalMcpBridge, mcpProcessManager,
            isLocalKBToolEnabled(), context.userId, userToolPermissionService
        )
        val executor = if (isLocalKBToolEnabled()) localKBToolExecutor else null
        NativeToolCallHandler.execute(
            agent, systemPrompt, message, tools,
            { text -> parseAIResponse(text, systemPrompt.length) },
            mcpProcessManager, executor, internalMcpBridge,
            context.userId, userToolPermissionService
        )
    } catch (e: Exception) {
        processChatLegacy(message, context, history)
    }

    /** Legacy text-based tool calling path (fallback). */
    private suspend fun processChatLegacy(
        message: String, context: ChatContext, history: List<ChatMessage>
    ): ChatResponse {
        val prompt = buildFullPrompt(message, context, history)
        val executor = if (isLocalKBToolEnabled()) localKBToolExecutor else null
        val modelCtx = resolveModelContext()
        return McpAgenticLoop.execute(
            prompt, mcpProcessManager, ::callAI, ::toResponse,
            jiraSyncHandler, context.projectKey, confluenceSyncHandler,
            executor, modelCtx, settingsRepository,
            userId = context.userId,
            permService = userToolPermissionService,
            internalMcpBridge = internalMcpBridge
        )
    }

    override fun buildSystemPrompt(context: ChatContext): String = buildBasePrompt(context)

    /** Build system prompt for native tool calling (no MCP tool text injection). */
    private suspend fun buildNativeSystemPrompt(
        message: String, context: ChatContext, history: List<ChatMessage>
    ): String = buildPromptParts(message, context, history, includeMcpTools = false)

    private suspend fun buildFullPrompt(
        message: String, context: ChatContext, history: List<ChatMessage>
    ): String = buildPromptParts(message, context, history, includeMcpTools = true)

    private suspend fun buildPromptParts(
        message: String, context: ChatContext,
        history: List<ChatMessage>, includeMcpTools: Boolean
    ): String {
        val base = buildBasePrompt(context)
        val personalization = buildPersonalization(context.userId)
        val kb = buildKBContext(context.projectKey, message)
        val graph = buildGraphContext(context.projectKey)
        val graphState = buildGraphStateContext(context.graphContext)
        val ticketState = buildTicketStateContext(context.ticketContext)
        val knowledgeCtx = buildKnowledgeContext(context.projectKey, message)
        val hist = formatHistory(history)
        val mcpSection = if (includeMcpTools) "\n--- MCP TOOLS ---\n${buildMcpToolsContext(context.userId)}" else ""
        val userSection = if (includeMcpTools) "\n--- USER ---\n$message" else ""
        return "$base\n$personalization\n--- KB ---\n$kb\n--- GRAPH ---\n$graph\n$graphState" +
            "\n--- TICKET CONTEXT ---\n$ticketState$mcpSection" +
            "\n--- KNOWLEDGE CONTEXT ---\n$knowledgeCtx\n--- HISTORY ---\n$hist$userSection"
    }

    /** Build context string from Ticket Intelligence screen state (selected ticket, analysis). */
    internal fun buildTicketStateContext(tc: com.assistant.chat.TicketChatContext?): String =
        ChatTicketStateContext.build(tc)

    /** Build context string from frontend graph state (focused node, filters, etc.) */
    private fun buildGraphStateContext(gc: com.assistant.chat.GraphChatContext?): String =
        ChatGraphStateContext.build(gc)

    internal fun buildMcpToolsContext(userId: String? = null): String {
        val localLines = buildLocalKBToolsContext()
        val prompt = ChatMcpToolsContext.build(internalMcpBridge, mcpProcessManager, localLines, userId, userToolPermissionService)
        return "$prompt${buildLocalKBPriorityHint()}"
    }

    internal fun isLocalKBToolEnabled(): Boolean = ChatLocalKBContext.isEnabled(settingsRepository)
    internal fun buildLocalKBToolsContext(): List<String> = ChatLocalKBContext.buildToolsContext(isLocalKBToolEnabled())
    internal fun buildLocalKBPriorityHint(): String = ChatLocalKBContext.buildPriorityHint(isLocalKBToolEnabled())

    private fun buildBasePrompt(ctx: ChatContext): String = ChatPromptBuilder.buildBasePrompt(ctx)

    private suspend fun buildPersonalization(userId: String): String =
        ChatPersonalization.build(userId, userAIConfigRepository)

    internal suspend fun buildKBContext(projectKey: String, message: String): String {
        val tickets = TICKET_PATTERN.findAll(message).map { it.value }.toList()
        if (tickets.isEmpty()) return "No KB data."
        val records = tickets.mapNotNull { kbRepository.findByTicketId(it) }
        if (records.isEmpty()) return "No KB data."
        return records.joinToString("\n") { r ->
            val base = "${r.ticketId}: ${r.requirementSummary} | SP:${r.scrumPoints} | Conf:${r.confidenceScore}"
            val deep = ChatDeepAnalysisContext.buildContext(r)
            "$base\n$deep"
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

    internal fun formatKnowledgeChunks(chunks: List<AttachmentChunk>): String =
        com.assistant.server.chat.formatKnowledgeChunks(chunks)
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
            is AIResult.Failure -> {
                val msg = when {
                    result.error.contains("timeout", ignoreCase = true) ->
                        "AI đang xử lý quá lâu. Vui lòng thử lại."
                    result.error.contains("unavailable", ignoreCase = true) ->
                        "AI provider chưa sẵn sàng. Vui lòng thử lại sau vài giây."
                    else -> "Lỗi: ${result.error}"
                }
                ChatResponse(reply = msg, contextUsage = usage)
            }
        }
    }

    internal fun parseAIResponse(raw: String, usage: Int): ChatResponse =
        ChatResponseParser.parse(raw, usage, resolveModelContext(), settingsRepository)

    /** Resolve current provider type + model name. Req: 19.96 */
    internal fun resolveModelContext(): AIModelContext? {
        val repo = providerConfigRepository ?: return null
        val config = repo.getAllProviders().firstOrNull() ?: return null
        return AIModelContext(
            providerType = config.type.name,
            modelName = config.model ?: "unknown"
        )
    }
}
