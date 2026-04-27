package com.assistant.server.routes

import com.assistant.mcp.*
import com.assistant.mcp.models.McpToolInfo
import com.assistant.server.mcp.HttpMcpProtocolClient
import com.assistant.server.mcp.McpProcessManagerImpl
import com.assistant.server.mcp.ProcessSpawner
import com.assistant.server.mcp.internal.InternalMcpBridge
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

/** Typed response for MCP test connection endpoint. */
@Serializable
data class McpTestResult(
    val success: Boolean,
    val tools: List<McpToolInfo> = emptyList(),
    val error: String? = null,
    val exitCode: Int? = null
)

/**
 * MCP Server management routes.
 * Requirements: 6.26, 6.27, 6.30
 */
fun Routing.mcpRoutes() {
    val mcpRepo by inject<McpServerRepository>()
    val processManager by inject<McpProcessManager>()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    route("/api/integrations/mcp") {
        authenticate("auth-jwt") {
            get { handleListMcp(mcpRepo, processManager) }
            post { handleCreateMcp(mcpRepo) }
            put("/{id}") { handleUpdateMcp(mcpRepo) }
            delete("/{id}") { handleDeleteMcp(mcpRepo, processManager) }
            post("/{id}/test") { handleTestMcp(mcpRepo) }
            get("/export") { handleExportMcp(mcpRepo, json) }
            post("/import") { handleImportMcp(mcpRepo, json) }
        }
    }
}

private suspend fun RoutingContext.handleListMcp(repo: McpServerRepository, pm: McpProcessManager) {
    extractUserClaims() ?: return
    val servers = repo.getAll().filter { it.type != "marker" }
    // Merge runtime status from ProcessManager into DB configs
    val enriched = servers.map { config ->
        val runtime = pm.getStatus(config.id)
        if (runtime != null && runtime.state.name != config.status) {
            config.copy(status = runtime.state.name)
        } else config
    }
    call.respond(HttpStatusCode.OK, enriched)
}

private suspend fun RoutingContext.handleCreateMcp(repo: McpServerRepository) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val config = call.receive<McpServerConfig>()
    val id = if (config.id.isBlank()) generateMcpId() else config.id
    // Duplicate check: reject if name or ID already exists
    if (hasDuplicate(repo, id, config.name)) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("MCP server with this name or ID already exists"))
        return
    }
    repo.insert(config.copy(id = id))
    val saved = repo.findById(id)
    call.respond(HttpStatusCode.Created, saved ?: config.copy(id = id))
}

private suspend fun RoutingContext.handleUpdateMcp(repo: McpServerRepository) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    if (isInternalMcpServer(id, repo)) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot modify Internal MCP Server"))
        return
    }
    val config = call.receive<McpServerConfig>()
    repo.update(config.copy(id = id))
    call.respond(HttpStatusCode.OK, repo.findById(id) ?: config)
}

private suspend fun RoutingContext.handleDeleteMcp(
    repo: McpServerRepository,
    processManager: McpProcessManager
) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    if (isInternalMcpServer(id, repo)) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot modify Internal MCP Server"))
        return
    }
    // Check if this is a markitdown server — suppress auto-recreate
    val server = repo.findById(id)
    processManager.stopServer(id)
    repo.delete(id)
    if (isMarkitdownServer(server)) suppressMarkitdownAutoCreate(repo)
    call.respond(HttpStatusCode.OK, mapOf("success" to "true"))
}

private suspend fun suppressMarkitdownAutoCreate(repo: McpServerRepository) {
    if (repo.findById("markitdown_auto_suppressed") != null) return
    repo.insert(McpServerConfig(
        id = "markitdown_auto_suppressed", name = "_markitdown_suppressed",
        type = "marker", command = "", disabled = true, status = "SUPPRESSED"
    ))
}

private fun isMarkitdownServer(server: McpServerConfig?): Boolean {
    if (server == null) return false
    return server.id.equals("markitdown", ignoreCase = true) ||
        server.name.equals("markitdown", ignoreCase = true)
}

private suspend fun RoutingContext.handleTestMcp(repo: McpServerRepository) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    if (isInternalMcpServer(id, repo)) {
        val bridge by call.application.inject<InternalMcpBridge>()
        val tools = bridge.getAggregatedTools().map {
            McpToolInfo(it.name, it.description, it.inputSchema)
        }
        call.respond(HttpStatusCode.OK, McpTestResult(true, tools))
        return
    }
    val server = repo.findById(id)
    if (server == null) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Server not found"))
        return
    }
    val result = runTestConnection(server, repo, id)
    call.respond(HttpStatusCode.OK, result)
}

private suspend fun runTestConnection(
    server: McpServerConfig,
    repo: McpServerRepository,
    id: String
): McpTestResult {
    return if (server.type == "streamable-http") {
        runHttpTestConnection(server, repo, id)
    } else {
        runStdioTestConnection(server, repo, id)
    }
}

private suspend fun runHttpTestConnection(
    server: McpServerConfig,
    repo: McpServerRepository,
    id: String
): McpTestResult {
    try {
        val env = ProcessSpawner.parseEnvPublic(server.env)
        val headers = HttpMcpProtocolClient.buildAuthHeaders(env)
        val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO)
        val client = HttpMcpProtocolClient(httpClient, server.url, headers, id)
        val tools = withTimeout(30_000) {
            client.initialize()
            client.listTools()
        }
        client.close()
        httpClient.close()
        repo.updateStatus(id, "ACTIVE")
        return McpTestResult(success = true, tools = tools)
    } catch (e: Exception) {
        return McpTestResult(success = false, error = e.message ?: "HTTP connection failed")
    }
}

private suspend fun runStdioTestConnection(
    server: McpServerConfig,
    repo: McpServerRepository,
    id: String
): McpTestResult {
    var process: Process? = null
    try {
        process = ProcessSpawner.spawnProcess(server)
        val scope = CoroutineScope(Dispatchers.IO)
        val client = ProcessSpawner.createClient(process, scope, id)
        val tools = withTimeout(15_000) {
            client.initialize()
            client.listTools()
        }
        client.close()
        repo.updateStatus(id, "ACTIVE")
        return McpTestResult(success = true, tools = tools)
    } catch (e: Exception) {
        return buildTestError(e, process)
    } finally {
        process?.destroyForcibly()
    }
}

private fun buildTestError(e: Exception, process: Process?): McpTestResult {
    val exitCode = try { process?.exitValue() } catch (_: Exception) { null }
    return McpTestResult(
        success = false,
        error = e.message ?: "Unknown error",
        exitCode = exitCode
    )
}

private suspend fun RoutingContext.handleExportMcp(repo: McpServerRepository, json: Json) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val servers = repo.getAll()
    val export = McpConfigExport(
        mcpServers = servers.associate { it.name to toEntry(it, json) }
    )
    call.respond(HttpStatusCode.OK, export)
}

private fun toEntry(s: McpServerConfig, json: Json) = McpServerEntry(
    command = s.command,
    args = try { json.decodeFromString<List<String>>(s.args) } catch (_: Exception) { emptyList() },
    env = try { json.decodeFromString<Map<String, String>>(s.env) } catch (_: Exception) { emptyMap() },
    disabled = s.disabled,
    autoApprove = try { json.decodeFromString<List<String>>(s.autoApprove) } catch (_: Exception) { emptyList() }
)

private suspend fun RoutingContext.handleImportMcp(repo: McpServerRepository, json: Json) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val export = call.receive<McpConfigExport>()
    var imported = 0
    for ((name, entry) in export.mcpServers) {
        val id = generateMcpId()
        if (hasDuplicate(repo, id, name)) continue // skip duplicates
        val config = McpServerConfig(
            id = id, name = name, command = entry.command,
            args = json.encodeToString(entry.args),
            env = json.encodeToString(entry.env),
            autoApprove = json.encodeToString(entry.autoApprove),
            disabled = entry.disabled
        )
        repo.insert(config)
        imported++
    }
    call.respond(HttpStatusCode.OK, mapOf("imported" to imported))
}

private fun generateMcpId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..12).map { chars.random() }.joinToString("")
}

/** Check if an MCP server is internal (protected from modification). Req: 6.70 */
private suspend fun isInternalMcpServer(id: String, repo: McpServerRepository): Boolean =
    id == InternalMcpBridge.INTERNAL_SERVER_ID || repo.isInternal(id)

/** Check if an MCP server with the same ID or name (case-insensitive) already exists. */
private suspend fun hasDuplicate(repo: McpServerRepository, id: String, name: String): Boolean {
    if (repo.findById(id) != null) return true
    return repo.getAll().any { it.name.equals(name, ignoreCase = true) }
}
