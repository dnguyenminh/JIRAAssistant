package com.assistant.server.agent.subprocess

import com.assistant.agent.models.ToolCall
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.tool.ToolRegistry
import com.assistant.server.agent.engine.ParallelToolExecutor
import org.slf4j.LoggerFactory

/**
 * Proxies tool calls between agent subprocesses and the [ToolRegistry].
 *
 * When an Agent_Subprocess emits a [ToolCallRequest] on stdout, this class
 * executes the tool via [ToolRegistry.invoke] (transparent routing to local,
 * MCP bridge, or agent MCP tools) and returns a [ToolCallResponse].
 *
 * Key contract:
 * - [handleToolCallRequest] never throws — all errors are captured in the response.
 * - Supports parallel proxying via [ParallelToolExecutor] with correlation ID matching.
 * - Logs every proxied call with agent type, tool name, source, timing, and result.
 *
 * Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 20.9
 */
class SubprocessProxyImpl(
    private val toolRegistry: ToolRegistry,
    private val parallelToolExecutor: ParallelToolExecutor? = null,
    private val agentType: String = "unknown"
) : SubprocessProxy {

    private val logger = LoggerFactory.getLogger(SubprocessProxyImpl::class.java)

    /**
     * Handles a single tool call request from a subprocess.
     * Never throws — errors are captured in the response.
     */
    override suspend fun handleToolCallRequest(
        request: ToolCallRequest
    ): ToolCallResponse {
        val start = System.currentTimeMillis()
        return try {
            val result = toolRegistry.invoke(request.name, request.arguments)
            val response = buildResponse(request.id, result)
            logProxiedCall(request, start, response)
            response
        } catch (e: Exception) {
            val response = buildErrorResponse(request.id, e)
            logProxiedCallError(request, start, e)
            response
        }
    }

    /**
     * Executes multiple tool call requests in parallel, returning
     * responses matched by correlation ID.
     */
    suspend fun handleBatchRequests(
        requests: List<ToolCallRequest>
    ): List<ToolCallResponse> {
        if (requests.isEmpty()) return emptyList()
        val executor = parallelToolExecutor ?: return handleSequentially(requests)
        return handleWithExecutor(requests, executor)
    }

    /**
     * Returns all tool descriptors available to subprocesses.
     * Priority: Local > Agent Home MCP > Shared MCP Bridge.
     * ToolRegistry already applies this priority via registration order.
     */
    override fun getAvailableToolDescriptors(): List<ToolDescriptor> =
        toolRegistry.listTools()

    /**
     * Builds the initial tool list message for session start injection.
     */
    override fun buildToolListMessage(): String =
        MessageProtocol.formatToolList(getAvailableToolDescriptors())

    /**
     * Builds a tools-updated notification for active sessions.
     */
    override fun buildToolsUpdatedMessage(): String =
        MessageProtocol.formatToolsUpdated(getAvailableToolDescriptors())

    private suspend fun handleWithExecutor(
        requests: List<ToolCallRequest>,
        executor: ParallelToolExecutor
    ): List<ToolCallResponse> {
        val calls = requests.map { ToolCall(it.name, it.arguments) }
        val results = executor.executeBatch(calls)
        return requests.zip(results) { req, result ->
            buildResponse(req.id, result)
        }
    }

    private suspend fun handleSequentially(
        requests: List<ToolCallRequest>
    ): List<ToolCallResponse> =
        requests.map { handleToolCallRequest(it) }

    private fun logProxiedCall(
        request: ToolCallRequest,
        startMs: Long,
        response: ToolCallResponse
    ) {
        val elapsed = System.currentTimeMillis() - startMs
        val source = resolveToolSource(request.name)
        logger.info(
            "Proxy [{}] tool={} source={} time={}ms size={} success={}",
            agentType, request.name, source,
            elapsed, response.data.length, response.success
        )
    }

    private fun logProxiedCallError(
        request: ToolCallRequest,
        startMs: Long,
        error: Exception
    ) {
        val elapsed = System.currentTimeMillis() - startMs
        val source = resolveToolSource(request.name)
        logger.error(
            "Proxy [{}] tool={} source={} time={}ms FAILED error={}",
            agentType, request.name, source, elapsed, error.message
        )
    }
}
