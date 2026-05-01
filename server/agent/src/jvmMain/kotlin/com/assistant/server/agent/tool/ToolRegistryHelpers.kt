package com.assistant.server.agent.tool

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolDescriptorWithSource
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool

private const val MAX_PARAM_LENGTH = 200

/** Metadata about a registered tool's source. */
data class ToolSourceInfo(
    val source: String = "LOCAL",
    val serverId: String? = null,
    val serverName: String? = null
)

/** Convert an AgentTool to its ToolDescriptor. */
internal fun AgentTool.toDescriptor() = ToolDescriptor(
    name = name,
    description = description,
    parameterNames = parameterNames
)

/** Convert an AgentTool + source info to an enriched descriptor. */
internal fun AgentTool.toDescriptorWithSource(
    sourceInfo: ToolSourceInfo
) = ToolDescriptorWithSource(
    name = name,
    description = description,
    parameterNames = parameterNames,
    toolSource = sourceInfo.source,
    serverId = sourceInfo.serverId,
    serverName = sourceInfo.serverName
)

/** Truncate params map to a string of at most 200 chars. */
internal fun truncateParams(params: Map<String, String>): String {
    val full = params.toString()
    return if (full.length <= MAX_PARAM_LENGTH) full
    else full.take(MAX_PARAM_LENGTH) + "..."
}

/** Build a rate-limit error result. */
internal fun rateLimitResult(toolName: String) = ToolResult(
    toolName = toolName,
    success = false,
    errorType = "RATE_LIMIT_EXCEEDED",
    errorMessage = "Rate limit exceeded"
)

/** Build a tool-not-found error result. */
internal fun toolNotFoundResult(toolName: String) = ToolResult(
    toolName = toolName,
    success = false,
    errorType = "TOOL_NOT_FOUND",
    errorMessage = "Tool '$toolName' is not registered"
)

/** Build a generic error result from an exception. */
internal fun errorResult(
    toolName: String,
    elapsedMs: Long,
    error: Exception
) = ToolResult(
    toolName = toolName,
    executionTimeMs = elapsedMs,
    success = false,
    errorType = error::class.simpleName ?: "Unknown",
    errorMessage = error.message ?: "Unknown error"
)
