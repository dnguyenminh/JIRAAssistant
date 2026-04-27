package com.assistant.agent.tool

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolResult

/**
 * Manages registration, discovery, validation, and invocation
 * of agent tools. Provides rate limiting, timeouts, and logging.
 *
 * Key contract: [invoke] never throws — all errors are returned
 * as [ToolResult] with `success = false`.
 */
interface ToolRegistry {

    /** Register a single tool. Replaces existing on duplicate name. */
    fun register(tool: AgentTool)

    /** Register multiple tools at once. */
    fun registerAll(tools: List<AgentTool>)

    /** List descriptors for all registered tools. */
    fun listTools(): List<ToolDescriptor>

    /**
     * Invoke a tool by name. Never throws — wraps all errors
     * in [ToolResult] with `success = false`.
     */
    suspend fun invoke(
        toolName: String,
        params: Map<String, String>
    ): ToolResult

    /** Remaining tool calls before rate limit is reached. */
    fun getRemainingCalls(): Int

    /** Reset the per-session call counter. */
    fun resetCallCount()
}
