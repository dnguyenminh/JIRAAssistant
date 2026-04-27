package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Chat tool definitions (send_chat_message, get_chat_history, list_conversations).
 * Requirements: AC 6.86–6.88, AC 6.108
 */
object ChatToolDefs {

    fun all(): List<InternalToolDefinition> = listOf(
        sendChatMessage(),
        getChatHistory(),
        listConversations()
    )

    private fun sendChatMessage() = InternalToolDefinition(
        name = "send_chat_message",
        description = "Send a message to the AI chat and get a response. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("message", stringProp("Chat message text"))
                put("conversationId", stringProp("Existing conversation ID to continue"))
                put("currentScreen", stringProp("Current screen context for the AI"))
            },
            required = listOf("message")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.CHAT
    )

    private fun getChatHistory() = InternalToolDefinition(
        name = "get_chat_history",
        description = "Get chat message history with pagination. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("conversationId", stringProp("Conversation ID (omit for current)"))
                put("limit", intProp("Max messages to return", 20))
                put("offset", intProp("Offset for pagination", 0))
            }
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.CHAT
    )

    private fun listConversations() = InternalToolDefinition(
        name = "list_conversations",
        description = "List all conversations for the current user. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.CHAT
    )
}
