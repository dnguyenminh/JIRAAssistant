package com.assistant.server.agent.tool

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolDescriptorWithSource
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Runtime implementation of [ToolRegistry].
 * Provides rate limiting, per-invocation timeout, and logging.
 * [invoke] never throws — all errors are wrapped in [ToolResult].
 */
class ToolRegistryImpl(
    private val maxCalls: Int = 50,
    private val timeoutMs: Long = 30_000
) : ToolRegistry {

    private val logger = LoggerFactory.getLogger(ToolRegistryImpl::class.java)
    private val tools = mutableMapOf<String, AgentTool>()
    private val toolSourceMap = mutableMapOf<String, ToolSourceInfo>()
    private var callCount = 0

    override fun register(tool: AgentTool) {
        if (tools.containsKey(tool.name)) {
            logger.warn("Replacing existing tool: {}", tool.name)
        }
        tools[tool.name] = tool
        toolSourceMap[tool.name] = resolveSourceInfo(tool.name)
    }

    override fun registerAll(tools: List<AgentTool>) {
        tools.forEach { register(it) }
    }

    override fun listTools(): List<ToolDescriptor> =
        tools.values.map { it.toDescriptor() }

    override fun listToolsWithSource(): List<ToolDescriptorWithSource> =
        tools.values.map { tool ->
            val info = toolSourceMap[tool.name] ?: ToolSourceInfo()
            tool.toDescriptorWithSource(info)
        }

    override suspend fun invoke(
        toolName: String,
        params: Map<String, String>
    ): ToolResult = invokeInternal(toolName, params)

    override fun getRemainingCalls(): Int = maxCalls - callCount

    override fun resetCallCount() {
        callCount = 0
    }

    private suspend fun invokeInternal(
        toolName: String,
        params: Map<String, String>
    ): ToolResult {
        if (callCount >= maxCalls) {
            return rateLimitResult(toolName)
        }
        val tool = tools[toolName]
            ?: return toolNotFoundResult(toolName)
        return executeWithTimeout(tool, toolName, params)
    }

    private suspend fun executeWithTimeout(
        tool: AgentTool,
        toolName: String,
        params: Map<String, String>
    ): ToolResult {
        val start = System.currentTimeMillis()
        return try {
            val result = withTimeout(timeoutMs) {
                tool.execute(params)
            }
            callCount++
            logInvocation(toolName, params, start, result)
            result
        } catch (e: Exception) {
            callCount++
            handleInvokeError(toolName, params, start, e)
        }
    }

    private fun logInvocation(
        toolName: String,
        params: Map<String, String>,
        startMs: Long,
        result: ToolResult
    ) {
        val elapsed = System.currentTimeMillis() - startMs
        val truncated = truncateParams(params)
        logger.info(
            "Tool [{}] params={} time={}ms size={} success={}",
            toolName, truncated, elapsed,
            result.dataSizeChars, result.success
        )
    }

    private fun handleInvokeError(
        toolName: String,
        params: Map<String, String>,
        startMs: Long,
        error: Exception
    ): ToolResult {
        val elapsed = System.currentTimeMillis() - startMs
        val truncated = truncateParams(params)
        logger.error(
            "Tool [{}] FAILED params={} time={}ms error={}",
            toolName, truncated, elapsed, error.message
        )
        return errorResult(toolName, elapsed, error)
    }

    companion object {
        /** Prefix-based tool source classification (same as resolveToolSource). */
        internal fun resolveSourceInfo(toolName: String): ToolSourceInfo =
            when {
                toolName.startsWith("mcp_agent_") ->
                    ToolSourceInfo("AGENT_MCP", serverName = extractServerName(toolName))
                toolName.startsWith("mcp_") ->
                    ToolSourceInfo("SHARED_MCP", serverName = extractServerName(toolName))
                else -> ToolSourceInfo("LOCAL")
            }

        private fun extractServerName(toolName: String): String? {
            val withoutMcp = toolName.removePrefix("mcp_agent_").removePrefix("mcp_")
            return withoutMcp.substringBefore("_").ifEmpty { null }
        }
    }
}
