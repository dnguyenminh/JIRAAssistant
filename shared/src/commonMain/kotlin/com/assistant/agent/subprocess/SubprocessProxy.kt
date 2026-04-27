package com.assistant.agent.subprocess

import com.assistant.agent.models.ToolDescriptor

/**
 * Proxies tool calls between agent subprocesses and the Orchestrator's ToolRegistry.
 *
 * When an Agent_Subprocess needs to invoke a tool, it emits a [ToolCallRequest]
 * on stdout. The Orchestrator intercepts this request, delegates execution to
 * the ToolRegistry (which transparently routes to local tools, shared MCP bridge
 * tools, or agent-specific MCP tools), and returns a [ToolCallResponse] to the
 * subprocess via stdin.
 *
 * Key contract:
 * - [handleToolCallRequest] executes the requested tool and returns a response.
 *   It never throws — all errors are captured in the [ToolCallResponse].
 * - [getAvailableToolDescriptors] returns the full list of tools the subprocess
 *   can invoke, including local, shared MCP, and agent home directory MCP tools.
 * - [buildToolListMessage] produces the initial tool list injected into the
 *   subprocess context at session start.
 * - [buildToolsUpdatedMessage] produces a notification sent to active sessions
 *   when the available tool set changes at runtime (e.g., MCP server added/removed).
 */
interface SubprocessProxy {

    /**
     * Handles a tool call request emitted by an agent subprocess.
     *
     * Parses the request, executes the tool via ToolRegistry (transparent routing
     * to local, MCP bridge, or agent MCP tools), and returns the result.
     * Supports parallel proxying when the subprocess emits multiple concurrent
     * requests — each is matched by correlation ID.
     *
     * This method never throws. If the tool is not found, times out, or fails,
     * the error is captured in the returned [ToolCallResponse] with `success = false`.
     *
     * @param request the tool call request containing tool name, arguments, and correlation ID
     * @return the tool call response with result data or error details
     */
    suspend fun handleToolCallRequest(request: ToolCallRequest): ToolCallResponse

    /**
     * Returns all tool descriptors available to agent subprocesses.
     *
     * The list includes tools from all sources in priority order:
     * local tools (registered in agent code) > agent home directory MCP tools
     * > shared MCP bridge tools. When tools from different sources share a name,
     * the higher-priority source takes precedence.
     *
     * @return descriptors for all available tools
     */
    fun getAvailableToolDescriptors(): List<ToolDescriptor>

    /**
     * Builds a tool list message for injection into the subprocess context
     * at session start.
     *
     * The message includes each tool's name, description, and parameter schema,
     * enabling the agent's LLM to discover which tools are available.
     *
     * @return formatted tool list message string
     */
    fun buildToolListMessage(): String

    /**
     * Builds a tools-updated notification message sent to active subprocess
     * sessions when the available tool set changes at runtime.
     *
     * This is triggered when shared MCP servers are added or removed via
     * the Integrations UI, ensuring subprocesses stay in sync with the
     * current tool inventory.
     *
     * @return formatted tools-updated notification message string
     */
    fun buildToolsUpdatedMessage(): String
}
