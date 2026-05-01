package com.assistant.server.agent.home

import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.models.ToolResult
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.McpLogBuffer
import com.assistant.server.mcp.McpLogEntry
import com.assistant.server.mcp.McpProtocolClientImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.Logger

/**
 * Starts an MCP server process and creates a protocol client.
 * Falls back to a no-client [McpServerState] if the process fails.
 */
internal object McpProcessStarter {

    suspend fun startProcess(
        config: AgentMcpConfig, serverName: String,
        scope: CoroutineScope, logger: Logger
    ): McpServerState {
        if (config.command.isBlank()) {
            logger.warn("No command for MCP server '{}'", serverName)
            return McpServerState(serverName = serverName)
        }
        if (config.command == "builtin") {
            logger.info("MCP server '{}' is built-in (in-process)", serverName)
            return McpServerState(serverName = serverName)
        }
        return tryStartWithRetry(config, serverName, scope, logger)
    }

    private suspend fun tryStartWithRetry(
        config: AgentMcpConfig, serverName: String,
        scope: CoroutineScope, logger: Logger,
        maxRetries: Int = 2
    ): McpServerState {
        for (attempt in 1..maxRetries) {
            val result = tryStartProcess(config, serverName, scope, logger, attempt)
            if (result.client != null) return result
            if (attempt < maxRetries) {
                logger.info("Retrying MCP server '{}' (attempt {}/{})",
                    serverName, attempt + 1, maxRetries)
            }
        }
        logger.warn("MCP server '{}' failed after {} attempts",
            serverName, maxRetries)
        return McpServerState(serverName = serverName)
    }

    private suspend fun tryStartProcess(
        config: AgentMcpConfig, serverName: String,
        scope: CoroutineScope, logger: Logger, attempt: Int
    ): McpServerState {
        return try {
            val cmd = buildCommand(config.command, config.args)
            val process = startOsProcess(cmd, config.env)
            val client = createClient(process, scope, serverName, logger)
            client.initialize()
            logger.info("MCP server '{}' started (attempt {})", serverName, attempt)
            McpServerState(serverName, client = client, process = process)
        } catch (e: Exception) {
            logger.warn("MCP server '{}' attempt {} failed: {}",
                serverName, attempt, e.message)
            McpServerState(serverName = serverName)
        }
    }

    private fun buildCommand(command: String, args: List<String>): List<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val cmdLower = command.lowercase()
        val needsShell = isWindows && (cmdLower.endsWith(".cmd") ||
            cmdLower.endsWith(".bat") || cmdLower == "npx" ||
            cmdLower == "uvx" || cmdLower == "node")
        return if (needsShell) listOf("cmd.exe", "/c", command) + args
        else listOf(command) + args
    }

    private fun startOsProcess(
        cmd: List<String>, env: Map<String, String>
    ): Process {
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(false)
        env.forEach { (k, v) -> pb.environment()[k] = v }
        return pb.start()
    }

    private fun createClient(
        process: Process, scope: CoroutineScope,
        serverId: String, logger: Logger
    ): McpProtocolClientImpl {
        val stdin = process.outputStream
        val stdout = java.io.BufferedReader(
            process.inputStream.reader(), 256 * 1024
        )
        logStderr(process, scope, serverId, logger)
        return McpProtocolClientImpl(stdin, stdout, scope, serverId)
    }

    private fun logStderr(
        process: Process, scope: CoroutineScope,
        serverId: String, logger: Logger
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                process.errorStream.bufferedReader().lineSequence()
                    .forEach { line ->
                        logger.debug("[{}] stderr: {}", serverId, line)
                        McpLogBuffer.add(McpLogEntry(
                            timestamp = java.time.Instant.now().toString(),
                            serverId = serverId, level = "STDERR",
                            message = line
                        ))
                    }
            } catch (_: Exception) { /* stream closed */ }
        }
    }
}

/**
 * Discovers tools from a running MCP client via listTools().
 * For builtin servers (no client), uses toolDescriptions from config.
 * For external servers, tools only come from the MCP server.
 */
internal object McpToolDiscovery {

    suspend fun discoverTools(
        state: McpServerState, config: AgentMcpConfig, logger: Logger
    ): List<Triple<String, String, JsonElement>> {
        if (config.command == "builtin") {
            return useToolDescriptions(config, state.serverName, logger)
        }
        val client = state.client ?: run {
            logger.warn("No MCP client for '{}' — no tools registered",
                state.serverName)
            return emptyList()
        }
        return tryListTools(client, state.serverName, logger)
    }

    private fun useToolDescriptions(
        config: AgentMcpConfig, serverName: String, logger: Logger
    ): List<Triple<String, String, JsonElement>> {
        val emptySchema: JsonElement = JsonObject(emptyMap())
        val tools = config.toolDescriptions.map { (k, v) ->
            Triple(k, v, emptySchema)
        }
        logger.info("Builtin '{}': {} tools from toolDescriptions",
            serverName, tools.size)
        return tools
    }

    private suspend fun tryListTools(
        client: McpProtocolClient, serverName: String, logger: Logger
    ): List<Triple<String, String, JsonElement>> {
        return try {
            val tools = client.listTools()
            if (tools.isEmpty()) {
                logger.warn("listTools() returned empty for '{}'", serverName)
                return emptyList()
            }
            logger.info("Discovered {} tools from MCP server '{}'",
                tools.size, serverName)
            tools.map { Triple(it.name, it.description, it.inputSchema) }
        } catch (e: Exception) {
            logger.warn("listTools() failed for '{}': {}",
                serverName, e.message)
            emptyList()
        }
    }
}

/**
 * Resolves original tool names and converts MCP responses.
 */
internal object McpToolNameResolver {

    /** Strips prefix to get original MCP tool name. */
    fun stripPrefix(prefixed: String, serverName: String): String {
        // buildToolName adds only "mcp_" when tool already has serverName prefix
        // So always strip just "mcp_" to get the original tool name
        return if (prefixed.startsWith("mcp_"))
            prefixed.removePrefix("mcp_")
        else prefixed
    }

    /** Converts Map<String, String> params to JsonObject. */
    fun toJsonObject(params: Map<String, String>): JsonObject =
        JsonObject(params.mapValues { (_, v) -> JsonPrimitive(v) })

    /** Converts McpToolCallResponse to ToolResult. */
    fun toToolResult(toolName: String, response: McpToolCallResponse): ToolResult {
        val text = response.content
            .mapNotNull { it.text }
            .joinToString("\n")
            .ifEmpty { "OK" }
        return ToolResult(
            toolName = toolName, data = text,
            dataSizeChars = text.length,
            success = !response.isError
        )
    }
}
