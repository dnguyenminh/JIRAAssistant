package com.assistant.server.mcp

import com.assistant.mcp.models.McpToolInfo
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Integration test: spawn Playwright MCP server via npx,
 * perform JSON-RPC initialize handshake, discover tools.
 *
 * Requires: npx + @playwright/mcp available on PATH.
 * Only runs on Windows (uses cmd.exe /c npx).
 */
@EnabledOnOs(OS.WINDOWS)
class McpPlaywrightIntegrationTest {

    @Test
    fun `initialize and discover tools from playwright MCP`() = runBlocking {
        val process = spawnPlaywright()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val client = createClient(process, scope)
        logStderr(process, scope)

        try {
            val init = withTimeout(60_000) { client.initialize() }
            println("Server: ${init.serverInfo.name}")
            assertEquals("2024-11-05", init.protocolVersion)

            val tools = withTimeout(30_000) { client.listTools() }
            println("Tools: ${tools.size}")
            tools.forEach { println("  - ${it.name}") }

            assertTrue(tools.size >= 10, "Expected 10+ tools, got ${tools.size}")
            assertHasTool(tools, "browser_navigate")
            assertHasTool(tools, "browser_click")
            assertHasTool(tools, "browser_snapshot")
        } finally {
            client.close()
            process.destroyForcibly()
            scope.cancel()
        }
    }

    private fun spawnPlaywright(): Process {
        val cmd = listOf("cmd.exe", "/c", "npx", "@playwright/mcp@latest")
        return ProcessBuilder(cmd).apply {
            redirectErrorStream(false)
        }.start()
    }

    private fun createClient(
        process: Process, scope: CoroutineScope
    ) = McpProtocolClientImpl(
        stdin = process.outputStream,
        stdout = process.inputStream.reader().let { java.io.BufferedReader(it, 256 * 1024) },
        scope = scope,
        serverId = "playwright-test"
    )

    private fun logStderr(process: Process, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().lineSequence().forEach {
                println("[playwright stderr] $it")
            }
        }
    }

    private fun assertHasTool(tools: List<McpToolInfo>, name: String) {
        assertTrue(tools.any { it.name == name }, "Missing tool: $name")
    }
}
