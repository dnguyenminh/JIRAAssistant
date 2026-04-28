package com.assistant.server.mcp

import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * JSON-RPC 2.0 client over HTTP POST for remote MCP servers (streamable-http).
 * Requirements: 17.2, 17.3, 17.6
 */
class HttpMcpProtocolClient(
    private val httpClient: HttpClient,
    private val serverUrl: String,
    private val authHeaders: Map<String, String> = emptyMap(),
    private val serverId: String = "http-mcp"
) : McpProtocolClient {

    private val logger = LoggerFactory.getLogger(HttpMcpProtocolClient::class.java)
    private val reqId = AtomicInteger(0)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var toolsCache: List<McpToolInfo>? = null
    private var sessionId: String? = null

    override suspend fun initialize(): McpInitializeResult {
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") { put("name", "jira-assistant"); put("version", "1.0.0") }
        }
        val result = sendRequest("initialize", params)
        sendNotification("notifications/initialized")
        return json.decodeFromJsonElement(result)
    }

    override suspend fun sendRequest(method: String, params: JsonElement?): JsonElement {
        val id = reqId.incrementAndGet()
        val bodyStr = buildRequestBody(id, method, params)
        logger.info("[{}] → {} id={} body={}", serverId, method, id, bodyStr.take(500))
        val response = doPost(bodyStr)
        response.headers["Mcp-Session-Id"]?.let { sessionId = it }
        val contentType = response.headers["Content-Type"] ?: "unknown"
        logger.info("[{}] ← {} status={} contentType={}", serverId, method, response.status.value, contentType)
        if (!response.status.isSuccess()) {
            val err = response.bodyAsText()
            logger.error("[{}] HTTP error {}: {}", serverId, response.status.value, err.take(500))
            throw McpError(McpError.INTERNAL_ERROR, "HTTP ${response.status.value}: ${err.take(200)}")
        }
        val respBody = response.bodyAsText()
        logger.info("[{}] raw response ({} chars): {}", serverId, respBody.length, respBody.take(300))
        val jsonBody = extractJsonFromResponse(respBody)
        logger.info("[{}] extracted JSON: {}", serverId, jsonBody.take(300))
        val rpcResp = json.decodeFromString<JsonRpcResponse>(jsonBody)
        val rpcErr = rpcResp.error
        if (rpcErr != null) throw McpError(rpcErr.code, rpcErr.message, rpcErr.data)
        return rpcResp.result ?: JsonNull
    }

    override suspend fun sendNotification(method: String, params: JsonElement?) {
        val bodyStr = buildNotificationBody(method, params)
        try { doPost(bodyStr) } catch (e: Exception) {
            logger.debug("[{}] notification {} failed: {}", serverId, method, e.message)
        }
    }

    override suspend fun listTools(): List<McpToolInfo> {
        toolsCache?.let { return it }
        val result = withTimeout(30_000) { sendRequest("tools/list") }
        val tools = parseToolsList(result)
        toolsCache = tools
        return tools
    }

    override suspend fun callTool(name: String, arguments: JsonObject): McpToolCallResponse {
        val params = buildJsonObject { put("name", name); put("arguments", arguments) }
        val startMs = System.currentTimeMillis()
        logger.info("Agent {} call {}.{}: arguments={}", serverId, serverId, name, arguments.toString().take(200))
        val result = withTimeout(60_000) { sendRequest("tools/call", params) }
        val durationMs = System.currentTimeMillis() - startMs
        val response = parseToolCallResult(result)
        if (response.isError) {
            logger.warn("Agent {} call {}.{}: FAILED in {}ms", serverId, serverId, name, durationMs)
        } else {
            logger.info("Agent {} call {}.{}: OK in {}ms", serverId, serverId, name, durationMs)
        }
        return response
    }

    override fun close() { logger.debug("[{}] HTTP MCP client closed", serverId) }

    private suspend fun doPost(body: String): HttpResponse {
        logger.info("[{}] POST body: {}", serverId, body.take(500))
        return httpClient.post(serverUrl) {
            header("Accept", "application/json, text/event-stream")
            for ((k, v) in authHeaders) header(k, v)
            val sid = sessionId
            if (sid != null) header("Mcp-Session-Id", sid)
            setBody(io.ktor.http.content.TextContent(body, ContentType.Application.Json))
        }
    }

    private fun parseToolsList(result: JsonElement): List<McpToolInfo> {
        val obj = result as? JsonObject ?: return emptyList()
        val arr = obj["tools"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { parseTool(it) }
    }

    private fun parseTool(el: JsonElement): McpToolInfo? {
        val obj = el as? JsonObject ?: return null
        val name = (obj["name"] as? JsonPrimitive)?.content ?: return null
        val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
        val schema = obj["inputSchema"] ?: JsonObject(emptyMap())
        return McpToolInfo(name = name, description = desc, inputSchema = schema)
    }

    private fun parseToolCallResult(result: JsonElement): McpToolCallResponse {
        val obj = result as? JsonObject ?: return McpToolCallResponse(isError = true)
        val arr = obj["content"] as? JsonArray ?: return McpToolCallResponse()
        val content = arr.mapNotNull { parseContent(it) }
        val isErr = (obj["isError"] as? JsonPrimitive)?.booleanOrNull ?: false
        return McpToolCallResponse(content = content, isError = isErr)
    }

    private fun parseContent(el: JsonElement): McpContent? {
        val obj = el as? JsonObject ?: return null
        return McpContent(
            type = (obj["type"] as? JsonPrimitive)?.content ?: "text",
            text = (obj["text"] as? JsonPrimitive)?.content,
            data = (obj["data"] as? JsonPrimitive)?.content,
            mimeType = (obj["mimeType"] as? JsonPrimitive)?.content
        )
    }

    /**
     * Extract JSON from response body. Handles both plain JSON and SSE format.
     * SSE format: "event: message\ndata: {json}\n\n"
     */
    private fun extractJsonFromResponse(body: String): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
        // Parse SSE: collect all "data:" lines and join
        val dataLines = trimmed.lines()
            .filter { it.startsWith("data:") || it.startsWith("data: ") }
            .map { it.removePrefix("data:").trim() }
        if (dataLines.isNotEmpty()) return dataLines.joinToString("")
        // Fallback: return as-is
        return trimmed
    }

    /** Build JSON-RPC request body, omitting "params" when null. */
    private fun buildRequestBody(id: Int, method: String, params: JsonElement?): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }.toString()
    }

    /** Build JSON-RPC notification body (no id), omitting "params" when null. */
    private fun buildNotificationBody(method: String, params: JsonElement?): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) put("params", params)
        }.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(HttpMcpProtocolClient::class.java)

        fun buildAuthHeaders(env: Map<String, String>): Map<String, String> {
            val authType = env["MCP_AUTH_TYPE"] ?: "token"
            log.info("Building auth headers: type={}, envKeys={}", authType, env.keys)
            val headers = if (authType == "oauth") buildOAuthHeaders(env) else buildTokenHeaders(env)
            log.info("Auth headers built: {}", headers.keys)
            return headers
        }

        private fun buildTokenHeaders(env: Map<String, String>): Map<String, String> {
            val email = env["ATLASSIAN_EMAIL"]
            val token = env["ATLASSIAN_API_TOKEN"]
            if (email.isNullOrBlank() || token.isNullOrBlank()) {
                log.warn("Token auth: missing email={} token={}", email != null, token != null)
                return emptyMap()
            }
            val encoded = java.util.Base64.getEncoder().encodeToString("$email:$token".toByteArray())
            return mapOf("Authorization" to "Basic $encoded")
        }

        private fun buildOAuthHeaders(env: Map<String, String>): Map<String, String> {
            val clientId = env["OAUTH_CLIENT_ID"]
            val secret = env["OAUTH_CLIENT_SECRET"]
            if (clientId.isNullOrBlank() || secret.isNullOrBlank()) {
                log.warn("OAuth auth: missing clientId={} secret={}", clientId != null, secret != null)
                return emptyMap()
            }
            val encoded = java.util.Base64.getEncoder().encodeToString("$clientId:$secret".toByteArray())
            return mapOf("Authorization" to "Basic $encoded")
        }
    }
}
