package com.assistant.server.agent.tool

import org.slf4j.LoggerFactory

/**
 * Maps agent-facing tool names to MCP server + tool name pairs.
 * Allows agents to use semantic names (e.g., "fetchJiraDetails")
 * while the bridge routes to MCP tools (e.g., server "jira", tool "get_issue").
 */
class ToolNameMapper(
    private val mappings: Map<String, ToolNameMapping> = emptyMap()
) {
    data class ToolNameMapping(
        val agentName: String,
        val serverName: String,
        val mcpToolName: String
    )

    fun resolve(agentName: String): ToolNameMapping? = mappings[agentName]

    fun hasMapping(agentName: String): Boolean = mappings.containsKey(agentName)

    fun getMcpName(agentName: String): String? = mappings[agentName]?.mcpToolName

    /**
     * Find mapping by MCP tool identity (serverName + mcpToolName).
     * Used during registration to check if an MCP tool has a custom agent name.
     */
    fun findByMcpTool(serverName: String, mcpToolName: String): ToolNameMapping? =
        mappings.values.find { it.serverName == serverName && it.mcpToolName == mcpToolName }

    companion object {
        private val logger = LoggerFactory.getLogger(ToolNameMapper::class.java)

        fun fromConfig(config: Map<String, Map<String, String>>): ToolNameMapper {
            val mappings = config.mapNotNull { (agentName, entry) ->
                val serverName = entry["serverName"]
                val mcpToolName = entry["mcpToolName"]
                if (serverName != null && mcpToolName != null) {
                    agentName to ToolNameMapping(agentName, serverName, mcpToolName)
                } else {
                    logger.warn("Skipping incomplete mapping for '{}'", agentName)
                    null
                }
            }.toMap()
            return ToolNameMapper(mappings)
        }
    }
}
