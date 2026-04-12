package com.assistant.mcp

import com.assistant.mcp.models.McpInitializeResult
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.mcp.models.McpToolInfo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC 2.0 client for MCP server communication.
 * Requirements: 6.38, 6.39, 6.44, 6.48
 */
interface McpProtocolClient {

    /** Initialize handshake with MCP server. Req: 6.39 */
    suspend fun initialize(): McpInitializeResult

    /** Send JSON-RPC request and await response. Req: 6.38, 6.41 */
    suspend fun sendRequest(
        method: String,
        params: JsonElement? = null
    ): JsonElement

    /** Send notification (no response expected). Req: 6.39 */
    suspend fun sendNotification(
        method: String,
        params: JsonElement? = null
    )

    /** Discover available tools. Req: 6.44 */
    suspend fun listTools(): List<McpToolInfo>

    /** Execute a tool call. Req: 6.48 */
    suspend fun callTool(
        name: String,
        arguments: JsonObject
    ): McpToolCallResponse

    /** Close client and release resources. */
    fun close()
}
