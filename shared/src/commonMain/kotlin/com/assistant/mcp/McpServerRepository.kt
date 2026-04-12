package com.assistant.mcp

/**
 * Repository for MCP server configurations.
 * Requirements: 6.25, 6.26
 */
interface McpServerRepository {
    suspend fun getAll(): List<McpServerConfig>
    suspend fun findById(id: String): McpServerConfig?
    suspend fun insert(config: McpServerConfig)
    suspend fun update(config: McpServerConfig)
    suspend fun updateStatus(id: String, status: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
