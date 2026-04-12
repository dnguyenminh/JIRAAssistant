package com.assistant.mcp

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock

/**
 * JVM implementation of [McpServerRepository].
 * Requirements: 6.25, 6.26
 */
class McpServerRepositoryImpl(
    private val database: JiraDatabase
) : McpServerRepository {

    override suspend fun getAll(): List<McpServerConfig> {
        return database.knowledgeBaseQueries
            .getAllMcpServers()
            .executeAsList()
            .map { it.toConfig() }
    }

    override suspend fun findById(id: String): McpServerConfig? {
        return database.knowledgeBaseQueries
            .findMcpServerById(id)
            .executeAsOneOrNull()
            ?.toConfig()
    }

    override suspend fun insert(config: McpServerConfig) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.insertMcpServer(
            id = config.id, name = config.name,
            type = config.type, command = config.command,
            url = config.url, args = config.args,
            env = config.env, auto_approve = config.autoApprove,
            disabled = if (config.disabled) 1L else 0L,
            status = config.status,
            created_at = now, updated_at = now
        )
    }

    override suspend fun update(config: McpServerConfig) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.updateMcpServer(
            name = config.name, type = config.type,
            command = config.command, url = config.url,
            args = config.args, env = config.env,
            auto_approve = config.autoApprove,
            disabled = if (config.disabled) 1L else 0L,
            updated_at = now, id = config.id
        )
    }

    override suspend fun updateStatus(id: String, status: String) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.updateMcpServerStatus(status, now, id)
    }

    override suspend fun delete(id: String) {
        database.knowledgeBaseQueries.deleteMcpServer(id)
    }

    override suspend fun deleteAll() {
        database.knowledgeBaseQueries.deleteAllMcpServers()
    }
}

private fun com.assistant.db.Mcp_servers.toConfig() = McpServerConfig(
    id = id, name = name, type = type, command = command,
    url = url, args = args, env = env, autoApprove = auto_approve,
    disabled = disabled != 0L, status = status,
    createdAt = created_at, updatedAt = updated_at
)
