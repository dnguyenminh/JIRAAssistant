package com.assistant.agent.subprocess

import com.assistant.agent.models.ToolDescriptor
import kotlinx.serialization.Serializable

/**
 * Request emitted by an Agent_Subprocess on stdout requesting the Orchestrator
 * to execute a tool. Contains tool name, arguments, and a correlation ID
 * for matching the response.
 *
 * Message format: {"toolCall": {"id": "<correlationId>", "name": "<toolName>", "arguments": {<params>}}}
 */
@Serializable
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: Map<String, String> = emptyMap()
)

/**
 * Response sent by the Orchestrator to an Agent_Subprocess on stdin containing
 * the result of a proxied tool call. Contains the correlation ID, success/failure
 * status, and result data.
 *
 * Message format: {"toolResult": {"id": "<correlationId>", "success": <boolean>, "data": "<result>", "error": "<msg>"}}
 */
@Serializable
data class ToolCallResponse(
    val id: String,
    val success: Boolean,
    val data: String = "",
    val error: String = ""
)

/**
 * Envelope for all subprocess communication between the Orchestrator and Agent_Subprocesses.
 *
 * Message types:
 * - "command"      — Orchestrator sends a command to the subprocess
 * - "toolCall"     — Subprocess requests a tool invocation
 * - "toolResult"   — Orchestrator returns a tool call result
 * - "toolsUpdated" — Orchestrator notifies subprocess of updated tool list
 */
@Serializable
data class SubprocessMessage(
    val type: String,
    val toolCall: ToolCallRequest? = null,
    val toolResult: ToolCallResponse? = null,
    val tools: List<ToolDescriptor>? = null,
    val content: String? = null
)
