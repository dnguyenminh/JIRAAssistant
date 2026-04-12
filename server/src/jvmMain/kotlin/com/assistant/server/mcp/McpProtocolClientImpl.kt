package com.assistant.server.mcp

import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import java.io.BufferedReader
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * JSON-RPC 2.0 client over stdio for MCP server communication.
 * Requirements: 6.38, 6.39, 6.41, 6.43, 6.44, 6.48, 6.60
 */
class McpProtocolClientImpl(
    private val stdin: OutputStream,
    private val stdout: BufferedReader,
    private val scope: CoroutineScope,
    private val serverId: String = "unknown"
) : McpProtocolClient {

    private val logger = LoggerFactory.getLogger(McpProtocolClientImpl::class.java)

    private val requestId = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var toolsCache: List<McpToolInfo>? = null

    /** Reader coroutine — reads stdout line-by-line, dispatches responses. Req: 6.43 */
    val readerJob: Job = scope.launch(Dispatchers.IO) {
        try {
            stdout.lineSequence().forEach { line ->
                if (line.isNotBlank()) {
                    logger.debug("[{}] stdout: {}", serverId, line.take(200))
                    dispatchResponse(line)
                }
            }
        } catch (_: Exception) {
            // Stream closed — process terminated
        }
        logger.debug("[{}] stdout reader ended", serverId)
    }

    /** Initialize handshake with MCP server. Req: 6.39, 6.40 */
    override suspend fun initialize(): McpInitializeResult {
        val params = buildInitializeParams()
        val result = sendRequestWithTimeout("initialize", params, timeoutMs = 30_000)
        sendNotification("notifications/initialized")
        val initResult = json.decodeFromJsonElement<McpInitializeResult>(result)
        logger.info("[{}] Initialized: {}", serverId, initResult.serverInfo)
        return initResult
    }

    /** Send JSON-RPC request and await response. Default 60s timeout. Req: 6.38, 6.41 */
    override suspend fun sendRequest(method: String, params: JsonElement?): JsonElement {
        return sendRequestWithTimeout(method, params, timeoutMs = 60_000)
    }

    /** Send notification (no response expected). Req: 6.39 */
    override suspend fun sendNotification(method: String, params: JsonElement?) {
        // Notifications MUST NOT have "id" field per JSON-RPC spec
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) put("params", params)
        }
        writeLine(json.encodeToString(JsonElement.serializer(), obj))
    }

    /** Discover available tools with in-memory cache. Req: 6.44 */
    override suspend fun listTools(): List<McpToolInfo> {
        toolsCache?.let { return it }
        val result = sendRequest("tools/list", buildJsonObject {})
        val tools = parseToolsList(result)
        toolsCache = tools
        return tools
    }

    /** Execute a tool call with 60s timeout. Req: 6.48 */
    override suspend fun callTool(name: String, arguments: JsonObject): McpToolCallResponse {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val result = sendRequest("tools/call", params)
        return json.decodeFromJsonElement<McpToolCallResponse>(result)
    }

    /** Close client and release resources. */
    override fun close() {
        readerJob.cancel()
        pending.values.forEach { it.cancel() }
        pending.clear()
        toolsCache = null
    }

    // ── Internal helpers ──────────────────────────────────────────────

    internal suspend fun sendRequestWithTimeout(
        method: String,
        params: JsonElement?,
        timeoutMs: Long
    ): JsonElement {
        val id = requestId.incrementAndGet()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        val startMs = System.currentTimeMillis()
        try {
            val request = JsonRpcRequest(id = id, method = method, params = params)
            writeLine(json.encodeToString(request))
            val result = withTimeout(timeoutMs) { deferred.await() }
            val durationMs = System.currentTimeMillis() - startMs
            logger.debug("[{}] {} id={} completed in {}ms", serverId, method, id, durationMs)
            logToBuffer(method, id, durationMs, null)
            return result
        } finally {
            pending.remove(id)
        }
    }

    internal fun dispatchResponse(line: String) {
        val element = try {
            json.parseToJsonElement(line)
        } catch (_: Exception) {
            return // skip unparseable lines
        }
        val obj = element.jsonObject
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return
        val deferred = pending[id] ?: return
        val error = obj["error"]
        if (error != null) {
            val rpcError = parseRpcError(error.jsonObject)
            logger.warn("[{}] Error response id={}: {}", serverId, id, rpcError.message)
            logToBuffer("response", id, 0, rpcError.message)
            deferred.completeExceptionally(rpcError)
        } else {
            logger.debug("[{}] Success response id={}", serverId, id)
            deferred.complete(obj["result"] ?: JsonNull)
        }
    }

    private fun parseRpcError(errorObj: JsonObject): McpError {
        val code = errorObj["code"]?.jsonPrimitive?.intOrNull ?: McpError.INTERNAL_ERROR
        val message = errorObj["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
        val data = errorObj["data"]
        return McpError(code, message, data)
    }

    private fun buildInitializeParams(): JsonElement = buildJsonObject {
        put("protocolVersion", "2024-11-05")
        putJsonObject("clientInfo") {
            put("name", "jira-assistant")
            put("version", "1.0.0")
        }
        putJsonObject("capabilities") {}
    }

    private fun parseToolsList(result: JsonElement): List<McpToolInfo> {
        val toolsArray = result.jsonObject["tools"]?.jsonArray ?: return emptyList()
        return toolsArray.map { json.decodeFromJsonElement<McpToolInfo>(it) }
    }

    @Synchronized
    private fun writeLine(jsonLine: String) {
        logger.debug("[{}] stdin: {}", serverId, jsonLine.take(200))
        stdin.write((jsonLine + "\n").toByteArray())
        stdin.flush()
    }

    private fun logToBuffer(method: String, reqId: Int, durationMs: Long, error: String?) {
        val level = if (error != null) "WARN" else "DEBUG"
        val msg = if (error != null) "$method id=$reqId error=$error"
        else "$method id=$reqId ${durationMs}ms"
        McpLogBuffer.add(McpLogEntry(
            timestamp = java.time.Instant.now().toString(),
            serverId = serverId, level = level, message = msg
        ))
    }
}
