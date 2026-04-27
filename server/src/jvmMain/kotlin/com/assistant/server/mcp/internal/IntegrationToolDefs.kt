package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Integration, KnowledgeGraph, and Dashboard tool definitions.
 * Requirements: AC 6.95–6.103, AC 6.108
 */
object IntegrationToolDefs {

    private val mcpActionEnum = listOf("start", "stop", "restart", "test")

    fun all(): List<InternalToolDefinition> =
        integrationTools() + knowledgeGraphTools() + dashboardTools()

    private fun integrationTools() = listOf(
        listAiProviders(), testAiProvider(),
        listMcpServers(), manageMcpServer()
    )

    private fun knowledgeGraphTools() = listOf(
        getGraphData(), searchGraphNodes()
    )

    private fun dashboardTools() = listOf(
        getDashboardMetrics(), listProjects(),
        getProjectAnalysisSummary()
    )

    private fun listAiProviders() = InternalToolDefinition(
        name = "list_ai_providers",
        description = "List all configured AI providers and their status. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.INTEGRATIONS
    )

    private fun testAiProvider() = InternalToolDefinition(
        name = "test_ai_provider",
        description = "Test connection to an AI provider. " +
            "[Permission: MANAGE_SETTINGS] [Role: Administrator]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("providerId", stringProp("AI provider ID to test"))
            },
            required = listOf("providerId")
        ),
        requiredPermission = "MANAGE_SETTINGS",
        requiredRole = "Administrator",
        category = ToolCategory.INTEGRATIONS
    )

    private fun listMcpServers() = InternalToolDefinition(
        name = "list_mcp_servers",
        description = "List all MCP servers including internal. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.INTEGRATIONS
    )

    private fun manageMcpServer() = InternalToolDefinition(
        name = "manage_mcp_server",
        description = "Perform an action on an MCP server (start/stop/restart/test). " +
            "Cannot be used on the internal server. " +
            "[Permission: MANAGE_SETTINGS] [Role: Administrator]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("serverId", stringProp("MCP server ID"))
                put("action", enumProp("Action to perform", mcpActionEnum))
            },
            required = listOf("serverId", "action")
        ),
        requiredPermission = "MANAGE_SETTINGS",
        requiredRole = "Administrator",
        category = ToolCategory.INTEGRATIONS
    )

    private fun getGraphData() = InternalToolDefinition(
        name = "get_graph_data",
        description = "Get knowledge graph data (nodes and edges) for visualization. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Filter by project key"))
                put("filters", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("description", JsonPrimitive("Filter criteria (nodeTypes, edgeTypes, minWeight)"))
                    put("properties", buildJsonObject {
                        put("nodeTypes", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put("description", JsonPrimitive("Filter by node types"))
                        })
                        put("edgeTypes", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put("description", JsonPrimitive("Filter by edge types"))
                        })
                        put("minWeight", buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Minimum edge weight"))
                        })
                    })
                })
            }
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.KNOWLEDGE_GRAPH
    )

    private fun searchGraphNodes() = InternalToolDefinition(
        name = "search_graph_nodes",
        description = "Search nodes in the knowledge graph by keyword. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("query", stringProp("Search query keyword"))
                put("nodeType", stringProp("Filter by node type"))
                put("limit", intProp("Max results to return", 20))
            },
            required = listOf("query")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.KNOWLEDGE_GRAPH
    )

    private fun getDashboardMetrics() = InternalToolDefinition(
        name = "get_dashboard_metrics",
        description = "Get project dashboard metrics (tickets, complexity, velocity). " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.DASHBOARD
    )

    private fun listProjects() = InternalToolDefinition(
        name = "list_projects",
        description = "List all available Jira projects. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.DASHBOARD
    )

    private fun getProjectAnalysisSummary() = InternalToolDefinition(
        name = "get_project_analysis_summary",
        description = "Get comprehensive analysis summary for a project " +
            "(sprint analytics, velocity, bottlenecks, top complex tickets). " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.DASHBOARD
    )
}
