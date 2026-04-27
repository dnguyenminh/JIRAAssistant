package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from a CLI-based AI backend.
 * Used for protocol communication — all default values are serialized.
 */
@Serializable
data class AiCliResponse(
    val response: String,
    val sessionId: String? = null,
    val rawJson: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Supported CLI backend types.
 */
enum class AiCliType { GEMINI, COPILOT, KIRO }

/**
 * Process lifecycle mode for CLI backends.
 * - STATELESS: new process per prompt, full context resent each time
 * - PERSISTENT: process uses resume flags to maintain conversation context
 */
enum class ProcessMode { STATELESS, PERSISTENT }

/**
 * Configuration for locating a Node.js CLI tool on disk.
 */
data class NodeCliConfig(
    val commandName: String,
    val npmPackage: String,
    val jsEntryPath: String,
    val jsPathPatterns: List<Regex> = emptyList()
)

/**
 * Resolved filesystem paths for Node.js and the CLI JS entry point.
 */
data class ResolvedPaths(
    val nodePath: String,
    val jsPath: String?
)

/**
 * Tool call request in the POC protocol format.
 * Used for protocol communication — all default values are serialized.
 */
@Serializable
data class ToolRequest(
    @SerialName("type") val type: String = "tool_call",
    val tool: String,
    val params: Map<String, String> = emptyMap()
)
