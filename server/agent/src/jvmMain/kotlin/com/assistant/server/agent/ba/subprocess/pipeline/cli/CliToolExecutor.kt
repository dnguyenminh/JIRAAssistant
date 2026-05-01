package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.ParsedToolCall
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Executes tool calls by delegating to [SubprocessProxy] and converting
 * responses to the CLI protocol JSON format via [ToolCallProtocol].
 *
 * Every call is logged with name, duration, success status, and response size.
 * Exceptions are caught and returned as `success=false` tool results — this
 * class never throws.
 */
class CliToolExecutor(
    private val subprocessProxy: SubprocessProxy
) {

    private val logger = LoggerFactory.getLogger(CliToolExecutor::class.java)

    /**
     * Executes a parsed tool call and returns protocol-formatted JSON.
     *
     * Generates a correlation UUID, delegates to [SubprocessProxy.handleToolCallRequest],
     * and converts the response to `{"toolResult":{...}}` format.
     *
     * @param toolCall the parsed tool call from CLI stdout
     * @return protocol JSON string with the tool result
     */
    suspend fun execute(toolCall: ParsedToolCall): String {
        val startTime = System.currentTimeMillis()
        return try {
            val request = ToolCallRequest(
                id = UUID.randomUUID().toString(),
                name = toolCall.name,
                arguments = toolCall.arguments
            )
            val response = subprocessProxy.handleToolCallRequest(request)
            val result = ToolCallProtocol.formatToolResult(
                name = toolCall.name,
                success = response.success,
                data = response.data,
                error = response.error
            )
            logToolCall(toolCall.name, startTime, response.success, result.length)
            result
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            val result = ToolCallProtocol.formatToolResult(
                name = toolCall.name,
                success = false,
                data = "",
                error = errorMessage
            )
            logToolCall(toolCall.name, startTime, false, result.length)
            logger.error("Tool call '{}' threw exception: {}", toolCall.name, errorMessage, e)
            result
        }
    }

    private fun logToolCall(name: String, startTime: Long, success: Boolean, responseSize: Int) {
        val duration = System.currentTimeMillis() - startTime
        logger.info(
            "Tool call completed: name={}, duration={}ms, success={}, responseSize={}",
            name, duration, success, responseSize
        )
    }
}
