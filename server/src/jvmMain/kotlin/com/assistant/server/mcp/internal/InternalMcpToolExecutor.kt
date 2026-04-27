package com.assistant.server.mcp.internal

import com.assistant.ai.AIOrchestrator
import com.assistant.auth.UserRole
import com.assistant.chat.ChatConversationRepository
import com.assistant.chat.ChatRepository
import com.assistant.chat.ChatService
import com.assistant.kb.KBRepository
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.*
import com.assistant.rbac.Permission
import com.assistant.rbac.RBACEngine
import com.assistant.rbac.UserStore
import com.assistant.scan.BatchScanEngine
import com.assistant.server.mcp.internal.handlers.*
import com.assistant.settings.SettingsRepository
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Core executor for internal MCP tool calls.
 * Flow: validate → RBAC check → dispatch → wrap response.
 * Requirements: AC 6.104, AC 6.112
 */
class InternalMcpToolExecutor(
    private val toolRegistry: InternalToolRegistry,
    private val rbacEngine: RBACEngine,
    batchScanEngine: BatchScanEngine,
    aiOrchestrator: AIOrchestrator,
    chatServiceProvider: () -> ChatService,
    chatRepository: ChatRepository,
    conversationRepository: ChatConversationRepository,
    settingsRepository: SettingsRepository,
    providerConfigRepo: ProviderConfigRepository,
    mcpProcessManager: McpProcessManager,
    mcpServerRepo: McpServerRepository,
    kbRepository: KBRepository,
    userStore: UserStore
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Handler instances (chatHandlers lazy to break circular dep)
    private val navHandlers = NavigationHandlers()
    private val scanHandlers = ScanHandlers(batchScanEngine)
    private val analysisHandlers = AnalysisHandlers(aiOrchestrator, kbRepository)
    private val chatHandlers by lazy {
        ChatHandlers(chatServiceProvider(), chatRepository, conversationRepository)
    }
    private val settingsHandlers = SettingsHandlers(settingsRepository)
    private val userMgmtHandlers = UserManagementHandlers(rbacEngine, userStore)
    private val integrationHandlers = IntegrationHandlers(
        providerConfigRepo, aiOrchestrator, mcpProcessManager, mcpServerRepo
    )
    private val graphHandlers = KnowledgeGraphHandlers(kbRepository)
    private val dashboardHandlers = DashboardHandlers(kbRepository, batchScanEngine)

    /** Get all registered tool definitions. Req: 6.73, 6.108 */
    fun getTools(): List<InternalToolDefinition> = toolRegistry.getAllTools()

    /** Execute tool call with validation + RBAC + dispatch. Req: 6.104, 6.112 */
    suspend fun execute(
        toolName: String,
        arguments: JsonObject,
        userId: String,
        userRole: String
    ): McpToolCallResponse {
        return try {
            val toolDef = lookupTool(toolName)
            ArgumentValidator.validate(toolDef, arguments)
            checkPermission(toolDef, userRole)
            dispatch(toolName, arguments, UserContext(userId, userRole))
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            handleSystemError(toolName, e)
        }
    }

    private fun lookupTool(toolName: String): InternalToolDefinition {
        return toolRegistry.getTool(toolName)
            ?: throw McpError(McpError.METHOD_NOT_FOUND, "Tool not found: $toolName")
    }

    private fun checkPermission(toolDef: InternalToolDefinition, userRole: String) {
        val role = parseRole(userRole)
        val permission = parsePermission(toolDef.requiredPermission)
        if (!rbacEngine.hasPermission(role, permission)) {
            throw McpError(McpError.INTERNAL_ERROR, "Access denied: requires ${toolDef.requiredPermission}")
        }
    }

    private fun parseRole(userRole: String): UserRole = try {
        UserRole.valueOf(userRole.uppercase())
    } catch (_: IllegalArgumentException) {
        throw McpError(McpError.INTERNAL_ERROR, "Invalid role: $userRole")
    }

    private fun parsePermission(permissionStr: String): Permission = try {
        Permission.valueOf(permissionStr.uppercase())
    } catch (_: IllegalArgumentException) {
        throw McpError(McpError.INTERNAL_ERROR, "Unknown permission: $permissionStr")
    }

    /** Dispatch tool call to the appropriate handler. */
    private suspend fun dispatch(
        toolName: String,
        arguments: JsonObject,
        ctx: UserContext
    ): McpToolCallResponse {
        logger.info("Agent jira-assistant-ui call jira-assistant-ui.{}: user={}", toolName, ctx.userId)
        return when (toolName) {
            // Navigation
            "navigate_to_page" -> navHandlers.handleNavigateToPage(arguments, ctx)
            "get_current_page" -> navHandlers.handleGetCurrentPage(arguments, ctx)
            "list_available_pages" -> navHandlers.handleListAvailablePages(arguments, ctx)
            // Scan
            "start_scan" -> scanHandlers.handleStartScan(arguments, ctx)
            "pause_scan" -> scanHandlers.handlePauseScan(arguments, ctx)
            "resume_scan" -> scanHandlers.handleResumeScan(arguments, ctx)
            "cancel_scan" -> scanHandlers.handleCancelScan(arguments, ctx)
            "get_scan_status" -> scanHandlers.handleGetScanStatus(arguments, ctx)
            "get_scan_log" -> scanHandlers.handleGetScanLog(arguments, ctx)
            // Analysis
            "analyze_ticket" -> analysisHandlers.handleAnalyzeTicket(arguments, ctx)
            "get_ticket_analysis" -> analysisHandlers.handleGetTicketAnalysis(arguments, ctx)
            "list_analyzed_tickets" -> analysisHandlers.handleListAnalyzedTickets(arguments, ctx)
            // Chat
            "send_chat_message" -> chatHandlers.handleSendChatMessage(arguments, ctx)
            "get_chat_history" -> chatHandlers.handleGetChatHistory(arguments, ctx)
            "list_conversations" -> chatHandlers.handleListConversations(arguments, ctx)
            // Settings
            "get_settings" -> settingsHandlers.handleGetSettings(arguments, ctx)
            "update_setting" -> settingsHandlers.handleUpdateSetting(arguments, ctx)
            "get_setting" -> settingsHandlers.handleGetSetting(arguments, ctx)
            // User Management
            "list_users" -> userMgmtHandlers.handleListUsers(arguments, ctx)
            "update_user_role" -> userMgmtHandlers.handleUpdateUserRole(arguments, ctx)
            "get_user_permissions" -> userMgmtHandlers.handleGetUserPermissions(arguments, ctx)
            // Integrations
            "list_ai_providers" -> integrationHandlers.handleListAiProviders(arguments, ctx)
            "test_ai_provider" -> integrationHandlers.handleTestAiProvider(arguments, ctx)
            "list_mcp_servers" -> integrationHandlers.handleListMcpServers(arguments, ctx)
            "manage_mcp_server" -> integrationHandlers.handleManageMcpServer(arguments, ctx)
            // Knowledge Graph
            "get_graph_data" -> graphHandlers.handleGetGraphData(arguments, ctx)
            "search_graph_nodes" -> graphHandlers.handleSearchGraphNodes(arguments, ctx)
            // Dashboard
            "get_dashboard_metrics" -> dashboardHandlers.handleGetDashboardMetrics(arguments, ctx)
            "list_projects" -> dashboardHandlers.handleListProjects(arguments, ctx)
            "get_project_analysis_summary" -> dashboardHandlers.handleGetProjectAnalysisSummary(arguments, ctx)
            else -> McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Tool handler not implemented: $toolName")),
                isError = true
            )
        }
    }

    private fun handleSystemError(toolName: String, e: Exception): Nothing {
        logger.warn("System error executing tool '{}': {}", toolName, e.message, e)
        throw McpError(McpError.INTERNAL_ERROR, "Internal error executing '$toolName': ${e.message}")
    }
}
