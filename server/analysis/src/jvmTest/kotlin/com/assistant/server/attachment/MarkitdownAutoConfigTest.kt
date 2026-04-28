package com.assistant.server.attachment

import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration test: MarkitdownAutoConfig must not create duplicates.
 * Catches bug where restart creates new markitdown despite existing one.
 */
class MarkitdownAutoConfigTest {

    class InMemoryMcpRepo : McpServerRepository {
        val configs = mutableListOf<McpServerConfig>()
        override suspend fun getAll() = configs.toList()
        override suspend fun findById(id: String) = configs.find { it.id == id }
        override suspend fun insert(config: McpServerConfig) { configs.add(config) }
        override suspend fun update(config: McpServerConfig) {
            configs.removeAll { it.id == config.id }; configs.add(config)
        }
        override suspend fun updateStatus(id: String, status: String) {}
        override suspend fun delete(id: String) { configs.removeAll { it.id == id } }
        override suspend fun deleteAll() { configs.clear() }
        override suspend fun findByName(name: String) =
            configs.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun isInternal(id: String) =
            configs.find { it.id == id }?.internal == true
    }

    @Test
    fun `does not create duplicate when markitdown exists with different ID`() = runBlocking {
        val repo = InMemoryMcpRepo()
        // User created markitdown with random ID (not "markitdown")
        repo.insert(McpServerConfig(id = "abc123random", name = "markitdown", command = "npx"))

        MarkitdownAutoConfig.ensureConfigured(repo)

        val all = repo.getAll()
        val markitdownCount = all.count { it.name.equals("markitdown", ignoreCase = true) }
        assertEquals(1, markitdownCount,
            "Must not create duplicate — existing markitdown with different ID should be detected")
    }

    @Test
    fun `does not create duplicate when Markitdown exists with capital M`() = runBlocking {
        val repo = InMemoryMcpRepo()
        repo.insert(McpServerConfig(id = "xyz789", name = "Markitdown", command = "uvx"))

        MarkitdownAutoConfig.ensureConfigured(repo)

        val all = repo.getAll()
        val count = all.count { it.name.equals("markitdown", ignoreCase = true) }
        assertEquals(1, count, "Case-insensitive name check must prevent duplicate")
    }

    @Test
    fun `creates markitdown when none exists`() = runBlocking {
        val repo = InMemoryMcpRepo()

        MarkitdownAutoConfig.ensureConfigured(repo)

        val all = repo.getAll()
        assertTrue(all.any { it.name.equals("Markitdown", ignoreCase = true) },
            "Must create markitdown when none exists")
    }

    @Test
    fun `suppression marker prevents auto-create`() = runBlocking {
        val repo = InMemoryMcpRepo()
        // User deleted markitdown → suppression marker exists
        repo.insert(McpServerConfig(id = "markitdown_auto_suppressed", name = "_suppressed", type = "marker"))

        MarkitdownAutoConfig.ensureConfigured(repo)

        val markitdowns = repo.getAll().filter { it.name.equals("Markitdown", ignoreCase = true) }
        assertTrue(markitdowns.isEmpty(),
            "Must not auto-create when suppression marker exists")
    }
}
