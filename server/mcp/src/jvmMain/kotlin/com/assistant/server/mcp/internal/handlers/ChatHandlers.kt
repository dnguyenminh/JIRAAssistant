package com.assistant.server.mcp.internal.handlers

import com.assistant.chat.*
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * Chat tool handlers — send_chat_message, get_chat_history, list_conversations.
 * Requirements: AC 6.86–6.88
 */
class ChatHandlers(
    private val chatService: ChatService,
    private val chatRepository: ChatRepository,
    private val conversationRepository: ChatConversationRepository
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleSendChatMessage(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val message = args.str("message") ?: return missingField("message")
        val projectKey = args.str("projectKey") ?: ""
        val chatCtx = ChatContext(
            projectKey = projectKey,
            currentScreen = "mcp-tool",
            userRole = ctx.userRole,
            userId = ctx.userId
        )
        val history = chatRepository.getHistory(ctx.userId, 0, 20)
        return try {
            val response = chatService.processChat(message, chatCtx, history)
            textResponse(json.encodeToString(response))
        } catch (e: Exception) {
            errorResponse("Chat failed: ${e.message}")
        }
    }

    suspend fun handleGetChatHistory(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val page = args.intOrNull("page") ?: 0
        val size = args.intOrNull("size") ?: 50
        val messages = chatRepository.getHistory(ctx.userId, page, size)
        return textResponse(json.encodeToString(messages))
    }

    suspend fun handleListConversations(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val conversations = conversationRepository.getByUser(ctx.userId)
        return textResponse(json.encodeToString(conversations))
    }
}
