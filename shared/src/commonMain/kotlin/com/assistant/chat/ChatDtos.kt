package com.assistant.chat

import kotlinx.serialization.Serializable

/**
 * Request gửi tin nhắn chat từ frontend.
 * Requirements: 19.5, 19.20
 */
@Serializable
data class ChatRequest(
    val message: String,
    val context: ChatContext? = null,
    val conversationId: String? = null,
    val attachments: List<ChatAttachment> = emptyList()
)

/**
 * Phản hồi AI cho tin nhắn chat.
 * Requirements: 19.8, 19.12, 19.20, 19.24
 */
@Serializable
data class ChatResponse(
    val reply: String,
    val actions: List<ChatAction> = emptyList(),
    val references: List<ChatReference> = emptyList(),
    val contextUsage: Int = 0
)

/**
 * File attachment in a chat message.
 * Requirements: 19.34
 */
@Serializable
data class ChatAttachment(
    val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileUrl: String
)

/**
 * Ngữ cảnh hiện tại của người dùng khi gửi tin nhắn.
 * Requirements: 19.5, 8.1, 8.2, 8.3
 */
@Serializable
data class ChatContext(
    val projectKey: String,
    val currentScreen: String,
    val userRole: String,
    val userId: String,
    val graphContext: GraphChatContext? = null,
    val ticketContext: TicketChatContext? = null
)

/**
 * Ticket Intelligence screen context sent when user has a ticket selected.
 * Allows AI to answer questions about the currently viewed ticket.
 * Requirements: 19.5, 22.1
 */
@Serializable
data class TicketChatContext(
    val selectedTicketId: String,
    val ticketSummary: String = "",
    val analysisState: String = "",
    val hasAnalysisResult: Boolean = false,
    val activeTab: String = "context"
)

/**
 * Graph state context gửi kèm khi user ở trang Knowledge Graph.
 * Chứa thông tin về focused node, active filters, visible nodes.
 * Requirements: 8.1, 8.2, 8.3
 */
@Serializable
data class GraphChatContext(
    val focusedNodeKey: String? = null,
    val activeTypeFilters: List<String> = emptyList(),
    val selectedClusterId: Int? = null,
    val depthValue: Int = 1,
    val visibleNodeCount: Int = 0,
    val searchQuery: String = ""
)

/**
 * Hành động đề xuất từ AI (navigate/changeConfig/triggerAnalysis).
 * Requirements: 19.12, 19.21
 */
@Serializable
data class ChatAction(
    val type: String,
    val label: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * Tham chiếu đến ticket hoặc màn hình liên quan.
 * Requirements: 19.12
 */
@Serializable
data class ChatReference(
    val type: String,
    val id: String,
    val label: String
)

/**
 * Yêu cầu thực hiện action từ AI đề xuất.
 * Requirements: 19.21
 */
@Serializable
data class ChatActionRequest(
    val actionType: String,
    val parameters: Map<String, String> = emptyMap()
)

/**
 * Kết quả thực hiện action.
 * Requirements: 19.21
 */
@Serializable
data class ChatActionResponse(
    val success: Boolean,
    val details: String
)

/**
 * Phân trang lịch sử hội thoại.
 * Requirements: 19.22
 */
@Serializable
data class ChatHistoryResponse(
    val messages: List<ChatMessage>,
    val total: Long,
    val page: Int,
    val size: Int
)
