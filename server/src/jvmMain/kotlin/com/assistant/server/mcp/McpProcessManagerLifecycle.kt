package com.assistant.server.mcp

import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.models.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Internal lifecycle operations for McpProcessManagerImpl.
 * Split from main class to respect 200-line file limit.
 * Requirements: 6.31, 6.32, 6.33, 6.35, 6.36
 */

/** Spawn process or create HTTP client, initialize protocol, store ManagedProcess. */
internal suspend fun McpProcessManagerImpl.doStartServer(
    configId: String,
    config: McpServerConfig
): McpProcessStatus {
    return try {
        mcpRepo.updateStatus(configId, "STARTING")
        if (config.type == "streamable-http") {
            doStartHttpServer(configId, config)
        } else {
            doStartStdioServer(configId, config)
        }
    } catch (e: Exception) {
        handleStartError(configId, e)
    }
}

/** Start a stdio-based MCP server (local process). */
private suspend fun McpProcessManagerImpl.doStartStdioServer(
    configId: String,
    config: McpServerConfig
): McpProcessStatus {
    val process = ProcessSpawner.spawnProcess(config)
    val client = ProcessSpawner.createClient(process, scope, configId)
    val initResult = initializeClient(client, configId)
    if (initResult != null) return initResult
    val tools = discoverToolsSafe(client, configId)
    storeManagedProcess(configId, config, process, client, tools)
    mcpRepo.updateStatus(configId, "ACTIVE")
    return buildRunningStatus(configId, processes[configId]!!)
}

/** Start a streamable-http MCP server (remote HTTP). */
private suspend fun McpProcessManagerImpl.doStartHttpServer(
    configId: String,
    config: McpServerConfig
): McpProcessStatus {
    val env = ProcessSpawner.parseEnvPublic(config.env)
    val headers = HttpMcpProtocolClient.buildAuthHeaders(env)
    val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO)
    val client = HttpMcpProtocolClient(httpClient, config.url, headers, configId)
    val initResult = initializeHttpClient(client, configId)
    if (initResult != null) return initResult
    val tools = discoverHttpToolsSafe(client, configId)
    storeHttpManagedProcess(configId, config, client, tools)
    mcpRepo.updateStatus(configId, "ACTIVE")
    return buildRunningStatus(configId, processes[configId]!!)
}

/** Try tools/list with 15s timeout. Return empty on failure. */
private suspend fun McpProcessManagerImpl.discoverToolsSafe(
    client: McpProtocolClientImpl,
    configId: String
): List<McpToolInfo> {
    return try {
        withTimeout(30_000) { client.listTools() }
    } catch (e: Exception) {
        logger.warn("[{}] tools/list failed (non-fatal): {}", configId, e.message)
        emptyList()
    }
}

private suspend fun McpProcessManagerImpl.discoverHttpToolsSafe(
    client: HttpMcpProtocolClient,
    configId: String
): List<McpToolInfo> {
    return try {
        withTimeout(30_000) { client.listTools() }
    } catch (e: Exception) {
        logger.warn("[{}] HTTP tools/list failed (non-fatal): {}", configId, e.message)
        emptyList()
    }
}

/** Run initialize with error handling. Returns error status or null on success. */
private suspend fun McpProcessManagerImpl.initializeClient(
    client: McpProtocolClientImpl,
    configId: String
): McpProcessStatus? {
    return try {
        client.initialize()
        null
    } catch (e: Exception) {
        logger.warn("Initialize failed for $configId: ${e.message}")
        client.close()
        McpProcessStatus(configId, state = McpServerState.ERROR, lastError = e.message)
    }
}

private suspend fun McpProcessManagerImpl.initializeHttpClient(
    client: HttpMcpProtocolClient,
    configId: String
): McpProcessStatus? {
    return try {
        client.initialize()
        null
    } catch (e: Exception) {
        logger.warn("HTTP initialize failed for $configId: ${e.message}")
        client.close()
        McpProcessStatus(configId, state = McpServerState.ERROR, lastError = e.message)
    }
}

/** Store ManagedProcess in map and launch health monitor. */
private fun McpProcessManagerImpl.storeManagedProcess(
    configId: String,
    config: McpServerConfig,
    process: Process,
    client: McpProtocolClientImpl,
    tools: List<McpToolInfo>
) {
    val existing = processes[configId]
    val restartCount = existing?.restartCount ?: 0
    val healthJob = launchHealthMonitor(configId)
    val mp = ManagedProcess(
        process = process,
        client = client,
        readerJob = client.readerJob,
        healthJob = healthJob,
        tools = tools,
        configName = config.name,
        startedAt = System.currentTimeMillis(),
        restartCount = restartCount
    )
    processes[configId] = mp
    logger.info("Started MCP server '${config.name}' (${tools.size} tools)")
}

/** Store HTTP ManagedProcess (no OS process). */
private fun McpProcessManagerImpl.storeHttpManagedProcess(
    configId: String,
    config: McpServerConfig,
    client: HttpMcpProtocolClient,
    tools: List<McpToolInfo>
) {
    val existing = processes[configId]
    val restartCount = existing?.restartCount ?: 0
    val healthJob = launchHealthMonitor(configId)
    val mp = ManagedProcess(
        process = null,
        client = client,
        readerJob = null,
        healthJob = healthJob,
        tools = tools,
        configName = config.name,
        startedAt = System.currentTimeMillis(),
        restartCount = restartCount
    )
    processes[configId] = mp
    logger.info("Started HTTP MCP server '${config.name}' (${tools.size} tools)")
}

/** Handle start failure: log and return ERROR status. */
private fun McpProcessManagerImpl.handleStartError(
    configId: String,
    e: Exception
): McpProcessStatus {
    logger.error("Failed to start MCP server $configId: ${e.message}")
    return McpProcessStatus(configId, state = McpServerState.ERROR, lastError = e.message)
}
