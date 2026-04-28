package com.assistant.server.agent.home

import com.assistant.mcp.models.McpAggregatedTool
import org.slf4j.LoggerFactory

/**
 * Detects duplicate tool names across MCP servers and resolves
 * collisions by prefixing with server name.
 */
object McpCollisionDetector {

    private val logger = LoggerFactory.getLogger(McpCollisionDetector::class.java)

    data class ResolvedTool(
        val registeredName: String,
        val originalName: String,
        val serverName: String,
        val wasRenamed: Boolean
    )

    fun resolve(tools: List<McpAggregatedTool>): List<ResolvedTool> {
        if (tools.isEmpty()) return emptyList()
        val duplicateNames = findDuplicateNames(tools)
        return tools.map { tool ->
            if (tool.name in duplicateNames) {
                logCollision(tool, duplicateNames[tool.name]!!)
                ResolvedTool(
                    registeredName = "${tool.serverName}_${tool.name}",
                    originalName = tool.name,
                    serverName = tool.serverName,
                    wasRenamed = true
                )
            } else {
                ResolvedTool(
                    registeredName = tool.name,
                    originalName = tool.name,
                    serverName = tool.serverName,
                    wasRenamed = false
                )
            }
        }
    }

    private fun findDuplicateNames(
        tools: List<McpAggregatedTool>
    ): Map<String, List<String>> {
        return tools.groupBy { it.name }
            .filter { it.value.size > 1 }
            .mapValues { entry -> entry.value.map { it.serverName } }
    }

    private fun logCollision(tool: McpAggregatedTool, servers: List<String>) {
        logger.warn(
            "Tool name collision: '{}' found on servers {}. " +
                "Prefixing with server name for '{}'",
            tool.name, servers, tool.serverName
        )
    }
}
