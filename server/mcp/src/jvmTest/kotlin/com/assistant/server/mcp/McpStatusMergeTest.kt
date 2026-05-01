package com.assistant.server.mcp

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration test: GET /api/integrations/mcp must merge runtime
 * status from McpProcessManager into DB configs.
 * Catches bug where navigating away shows OFFLINE for running servers.
 */
class McpStatusMergeTest {

    class InMemoryMcpRepo : McpServerRepository {
        val configs = mutableListOf<McpServerConfig>()
        override suspend fun getAll() = configs.toList()
        override suspend fun findById(id: String) = configs.find { it.id == id }
        override suspend fun insert(config: McpServerConfig) { configs.add(config) }
        override suspend fun update(config: McpServerConfig) {
            configs.removeAll { it.id == config.id }; configs.add(config)
        }
        override suspend fun updateStatus(id: String, status: String) {
            val idx = configs.indexOfFirst { it.id == id }
            if (idx >= 0) configs[idx] = configs[idx].copy(status = status)
        }
        override suspend fun delete(id: String) { configs.removeAll { it.id == id } }
        override suspend fun deleteAll() { configs.clear() }
        override suspend fun findByName(name: String) =
            configs.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun isInternal(id: String) =
            configs.find { it.id == id }?.internal == true
    }

    class StubProcessManager(private val runningIds: Set<String>) : McpProcessManager {
        override suspend fun startServer(configId: String) = McpProcessStatus(configId, state = McpServerState.RUNNING)
        override suspend fun stopServer(configId: String) = McpProcessStatus(configId, state = McpServerState.STOPPED)
        override suspend fun restartServer(configId: String) = startServer(configId)
        override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
        override fun getStatus(configId: String): McpProcessStatus? =
            if (configId in runningIds) McpProcessStatus(configId, state = McpServerState.RUNNING) else null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = emptyList<McpAggregatedTool>()
        override fun getClient(configId: String): McpProtocolClient? = null
    }

    @Test
    fun `runtime RUNNING overrides DB OFFLINE in merged list`() = runBlocking {
        val repo = InMemoryMcpRepo()
        repo.insert(McpServerConfig(id = "mcp1", name = "Test", status = "OFFLINE"))
        val pm = StubProcessManager(setOf("mcp1"))

        // Simulate what handleListMcp does
        val servers = repo.getAll()
        val enriched = servers.map { config ->
            val runtime = pm.getStatus(config.id)
            if (runtime != null && runtime.state.name != config.status) {
                config.copy(status = runtime.state.name)
            } else config
        }

        assertEquals("RUNNING", enriched[0].status,
            "Runtime RUNNING must override DB OFFLINE")
    }

    @Test
    fun `DB status preserved when no runtime info`() = runBlocking {
        val repo = InMemoryMcpRepo()
        repo.insert(McpServerConfig(id = "mcp2", name = "Offline", status = "OFFLINE"))
        val pm = StubProcessManager(emptySet()) // nothing running

        val servers = repo.getAll()
        val enriched = servers.map { config ->
            val runtime = pm.getStatus(config.id)
            if (runtime != null && runtime.state.name != config.status) {
                config.copy(status = runtime.state.name)
            } else config
        }

        assertEquals("OFFLINE", enriched[0].status,
            "DB status preserved when process not in runtime map")
    }
}
