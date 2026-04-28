package com.assistant.server.mcp.internal.handlers

import com.assistant.mcp.models.McpContent
import com.assistant.mcp.models.McpToolCallResponse
import kotlinx.serialization.json.*

/**
 * Shared extension functions for all MCP tool handlers.
 * Provides argument extraction and response builders.
 */

/** Extract a string argument by key. */
fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

/** Extract an optional int argument by key. */
fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

/** Extract an optional boolean argument by key. */
fun JsonObject.boolOrNull(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

/** Build a success text response. */
fun textResponse(text: String) = McpToolCallResponse(
    content = listOf(McpContent(type = "text", text = text))
)

/** Build an isError=true response for business errors. */
fun errorResponse(msg: String) = McpToolCallResponse(
    isError = true,
    content = listOf(McpContent(type = "text", text = msg))
)

/** Build an error response for a missing required field. */
fun missingField(field: String) = errorResponse("Missing required field: $field")
