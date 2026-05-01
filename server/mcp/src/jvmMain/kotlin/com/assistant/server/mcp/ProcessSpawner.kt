package com.assistant.server.mcp

import com.assistant.mcp.McpServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Helpers for spawning MCP server processes and creating protocol clients.
 * Requirements: 6.31
 */
object ProcessSpawner {

    private val json = Json { ignoreUnknownKeys = true }

    /** Spawn OS process from MCP server config. */
    fun spawnProcess(config: McpServerConfig): Process {
        val args = parseArgs(config.args)
        val env = parseEnv(config.env)
        val cmd = buildCommand(config.command, args)
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(false)
        env.forEach { (k, v) -> pb.environment()[k] = v }
        return pb.start()
    }

    /** Build command list, handling Windows .cmd/.bat scripts. */
    private fun buildCommand(command: String, args: List<String>): List<String> {
        val isWindows = System.getProperty("os.name")
            .lowercase().contains("win")
        val cmdLower = command.lowercase()
        return if (isWindows && (cmdLower.endsWith(".cmd") || cmdLower.endsWith(".bat")
                    || cmdLower == "npx" || cmdLower == "uvx" || cmdLower == "node")) {
            listOf("cmd.exe", "/c", command) + args
        } else {
            listOf(command) + args
        }
    }

    /** Create McpProtocolClientImpl wired to process stdio. */
    fun createClient(process: Process, scope: CoroutineScope, serverId: String = "unknown"): McpProtocolClientImpl {
        val stdin = process.outputStream
        val stdout = process.inputStream.reader().let { java.io.BufferedReader(it, 256 * 1024) } // 256KB for large MCP responses
        // Log stderr in background for debugging
        val logger = org.slf4j.LoggerFactory.getLogger(ProcessSpawner::class.java)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                process.errorStream.bufferedReader().lineSequence().forEach { line ->
                    logger.debug("[{}] stderr: {}", serverId, line)
                    McpLogBuffer.add(McpLogEntry(
                        timestamp = java.time.Instant.now().toString(),
                        serverId = serverId, level = "STDERR", message = line
                    ))
                }
            } catch (_: Exception) { }
        }
        return McpProtocolClientImpl(stdin, stdout, scope, serverId)
    }

    private fun parseArgs(raw: String): List<String> = try {
        json.decodeFromString<List<String>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    fun parseEnvPublic(raw: String): Map<String, String> = parseEnv(raw)

    private fun parseEnv(raw: String): Map<String, String> {
        // Try direct JSON parse
        val result = tryParseJson(raw)
        if (result.isNotEmpty()) {
            // Check if values look like they contain nested JSON (double-encoded)
            if (result.size == 1 && result.keys.first().startsWith("{")) {
                // The whole JSON was stored as a single key — try parsing the key
                val nested = tryParseJson(result.keys.first())
                if (nested.isNotEmpty()) return nested
            }
            return result
        }
        // Fallback: KEY=VALUE lines
        return parseEnvLines(raw)
    }

    private fun tryParseJson(raw: String): Map<String, String> = try {
        json.decodeFromString<Map<String, String>>(raw)
    } catch (_: Exception) {
        emptyMap()
    }

    private fun parseEnvLines(raw: String): Map<String, String> {
        return raw.lines()
            .filter { it.contains("=") }
            .associate { line ->
                val idx = line.indexOf("=")
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
    }
}
