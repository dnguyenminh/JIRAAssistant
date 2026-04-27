package com.assistant.agent.home

import kotlinx.serialization.Serializable

/**
 * Configuration for an MCP (Model Context Protocol) server within an Agent Home Directory.
 *
 * Each agent can have multiple MCP servers configured in the `.agent/mcp/` directory.
 * At agent startup, the framework reads these configurations, starts the MCP servers,
 * and automatically registers their tools in the Tool_Registry. MCP tools are then
 * invocable through the same `ToolRegistry.invoke()` interface as native Agent_Tools.
 *
 * Tool names from MCP servers are prefixed with the server name to avoid naming
 * conflicts (e.g., `mcp_jira_search` for a tool named `search` from the `jira` server).
 *
 * @property serverName Unique identifier for the MCP server (e.g., "jira-mcp", "github-mcp")
 * @property command The executable to start the MCP server process (e.g., "uvx", "npx")
 * @property args Command-line arguments passed to the MCP server process
 * @property env Environment variables injected into the MCP server subprocess
 * @property toolDescriptions Maps tool names to their descriptions for discovery —
 *           enables the framework to present available tools to the LLM without
 *           querying the MCP server at runtime
 */
@Serializable
data class AgentMcpConfig(
    val serverName: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val toolDescriptions: Map<String, String> = emptyMap()
)
