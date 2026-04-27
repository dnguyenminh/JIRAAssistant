package com.assistant.agent.tool

import com.assistant.agent.models.ToolResult

/**
 * A composable, reusable tool that an agent can invoke during
 * its thinking loop. Each tool declares a name, description,
 * input parameter names, and a suspend execution function.
 */
interface AgentTool {
    /** Unique tool identifier. */
    val name: String

    /** Human-readable description for LLM tool selection. */
    val description: String

    /** Declared input parameter names for validation. */
    val parameterNames: List<String>

    /**
     * Execute the tool with the given parameters.
     * Implementations may throw; the ToolRegistry wraps errors.
     */
    suspend fun execute(params: Map<String, String>): ToolResult
}
