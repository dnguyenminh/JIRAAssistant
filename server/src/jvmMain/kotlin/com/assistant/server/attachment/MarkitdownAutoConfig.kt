package com.assistant.server.attachment

import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Auto-configures the markitdown MCP server if not already registered.
 * Called during application startup.
 * Requirements: 22.5
 */
object MarkitdownAutoConfig {

    private const val SERVER_ID = "markitdown"
    private const val SUPPRESSED_KEY = "markitdown_auto_suppressed"
    private val json = Json { encodeDefaults = true }

    suspend fun ensureConfigured(mcpRepo: McpServerRepository) {
        // Check if any markitdown server already exists (by ID or name)
        val allServers = mcpRepo.getAll()
        val exists = allServers.any {
            it.id.equals(SERVER_ID, ignoreCase = true) ||
            it.name.equals("markitdown", ignoreCase = true)
        }
        if (exists) {
            println("[MarkitdownAutoConfig] markitdown MCP already configured")
            return
        }
        // Don't auto-create if user previously deleted it
        // (check via a marker config with disabled=true)
        val suppressed = allServers.any {
            it.id == SUPPRESSED_KEY
        }
        if (suppressed) {
            println("[MarkitdownAutoConfig] markitdown auto-config suppressed by user")
            return
        }
        val now = Instant.now().toString()
        val config = McpServerConfig(
            id = SERVER_ID,
            name = "Markitdown",
            type = "stdio",
            command = "uvx",
            args = json.encodeToString(listOf("markitdown")),
            disabled = false,
            status = "OFFLINE",
            createdAt = now,
            updatedAt = now
        )
        mcpRepo.insert(config)
        println("[MarkitdownAutoConfig] Auto-created markitdown MCP config")
    }
}
