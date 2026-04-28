package com.assistant.server.chat

import com.assistant.chat.UserToolPermissionRepository
import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository

/** In-memory UserToolPermissionRepository for property tests. */
class InMemoryPermissionRepo : UserToolPermissionRepository {
    private val store = mutableMapOf<String, Map<String, String>>()

    override suspend fun findByUserId(userId: String) = store[userId]

    override suspend fun save(userId: String, permissions: Map<String, String>) {
        store[userId] = permissions
    }

    override suspend fun delete(userId: String) { store.remove(userId) }

    fun clear() = store.clear()
}

/** In-memory McpServerRepository stub for property tests. */
class InMemoryMcpServerRepo(
    private val servers: MutableList<McpServerConfig> = mutableListOf()
) : McpServerRepository {
    override suspend fun getAll() = servers.toList()
    override suspend fun findById(id: String) = servers.find { it.id == id }
    override suspend fun findByName(name: String) =
        servers.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun isInternal(id: String) =
        servers.find { it.id == id }?.internal ?: false
    override suspend fun insert(config: McpServerConfig) { servers.add(config) }
    override suspend fun update(config: McpServerConfig) {
        servers.removeAll { it.id == config.id }; servers.add(config)
    }
    override suspend fun updateStatus(id: String, status: String) {
        findById(id)?.let { update(it.copy(status = status)) }
    }
    override suspend fun delete(id: String) { servers.removeAll { it.id == id } }
    override suspend fun deleteAll() { servers.clear() }
}
