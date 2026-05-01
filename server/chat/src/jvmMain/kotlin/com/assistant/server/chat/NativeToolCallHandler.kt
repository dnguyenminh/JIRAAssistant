package com.assistant.server.chat

import com.assistant.ai.OllamaChatAgent
import com.assistant.ai.OllamaChatResult
import com.assistant.ai.models.OllamaChatMessage
import com.assistant.ai.models.OllamaChatToolCall
import com.assistant.ai.models.OllamaChatToolDef
import com.assistant.chat.ChatResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Agentic loop using OllamaChatAgent with native tool calling.
 * Replaces text-based tool call parsing with structured tool_calls.
 * Requirements: 6.52, 6.53, 19.67
 */
object NativeToolCallHandler {

    private const val MAX_ROUNDS = 5
    private val logger = LoggerFactory.getLogger(NativeToolCallHandler::class.java)

    /**
     * Execute agentic loop with native tool calling.
     * Returns ChatResponse with final AI text or tool-augmented answer.
     */
    suspend fun execute(
        agent: OllamaChatAgent,
        systemPrompt: String,
        userMessage: String,
        tools: List<OllamaChatToolDef>,
        toResponse: (String) -> ChatResponse,
        mcpProcessManager: McpProcessManager?,
        localKBExecutor: LocalKBToolExecutor?,
        internalMcpBridge: InternalMcpBridge?,
        userId: String?,
        permService: UserToolPermissionService?
    ): ChatResponse {
        val messages = mutableListOf(
            OllamaChatMessage(role = "system", content = systemPrompt),
            OllamaChatMessage(role = "user", content = userMessage)
        )
        for (round in 1..MAX_ROUNDS) {
            val result = agent.chat(messages, tools)
            logger.info("[NativeLoop] Round $round — result type: ${result::class.simpleName}")
            when (result) {
                is OllamaChatResult.TextResponse ->
                    return toResponse(result.content)
                is OllamaChatResult.Error ->
                    return toResponse("Error: ${result.message}")
                is OllamaChatResult.ToolCalls ->
                    handleToolCalls(result, messages, round,
                        mcpProcessManager, localKBExecutor,
                        internalMcpBridge, userId, permService)
            }
        }
        return executeFinalRound(agent, messages, toResponse)
    }

    private suspend fun handleToolCalls(
        result: OllamaChatResult.ToolCalls,
        messages: MutableList<OllamaChatMessage>,
        round: Int,
        pm: McpProcessManager?,
        localKB: LocalKBToolExecutor?,
        bridge: InternalMcpBridge?,
        userId: String?,
        permService: UserToolPermissionService?
    ) {
        messages.add(OllamaChatMessage(
            role = "assistant", content = result.rawContent,
            toolCalls = result.calls
        ))
        for (call in result.calls) {
            val toolResult = executeNativeToolCall(
                call, pm, localKB, bridge, userId, permService
            )
            logger.info("[NativeLoop] Round $round — ${call.function.name}: ${toolResult.take(200)}")
            messages.add(OllamaChatMessage(role = "tool", content = toolResult))
        }
    }

    private suspend fun executeFinalRound(
        agent: OllamaChatAgent,
        messages: MutableList<OllamaChatMessage>,
        toResponse: (String) -> ChatResponse
    ): ChatResponse {
        messages.add(OllamaChatMessage(
            role = "user",
            content = McpAgenticLoop.FINAL_ROUND_INSTRUCTION
        ))
        val final = agent.chat(messages, emptyList())
        val text = when (final) {
            is OllamaChatResult.TextResponse -> final.content
            is OllamaChatResult.ToolCalls -> final.rawContent
            is OllamaChatResult.Error -> "Error: ${final.message}"
        }
        return toResponse(text)
    }
}
