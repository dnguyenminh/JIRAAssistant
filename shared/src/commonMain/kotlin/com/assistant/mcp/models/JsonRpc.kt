package com.assistant.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 request message.
 * Requirements: 6.38, 6.41
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonElement? = null
)

/**
 * JSON-RPC 2.0 response message.
 * Requirements: 6.38, 6.42
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

/** JSON-RPC 2.0 error object. */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * MCP initialize handshake result.
 * Requirements: 6.39
 */
@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val serverInfo: McpServerInfoDto,
    val capabilities: JsonElement? = null
)

/** MCP server info from initialize response. */
@Serializable
data class McpServerInfoDto(
    val name: String,
    val version: String? = null
)

/**
 * MCP protocol error exception.
 * JSON-RPC error codes per spec. Requirements: 6.42
 */
class McpError(
    val code: Int,
    val errorMessage: String,
    val data: JsonElement? = null
) : Exception("MCP Error $code: $errorMessage") {

    companion object {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }
}
