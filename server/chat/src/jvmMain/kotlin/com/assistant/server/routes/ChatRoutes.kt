package com.assistant.server.routes

import com.assistant.chat.*
import com.assistant.kb.ProviderConfigRepository
import com.assistant.rbac.Permission
import com.assistant.rbac.RBACEngine
import com.assistant.server.chat.UserToolPermissionService
import com.assistant.settings.SettingsRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Chat routes — AI Chat Sidebar endpoints.
 * Requirements: 19.20–19.23, 19.40, 19.48, 19.54, 19.60
 */
fun Routing.chatRoutes() {
    val chatService by inject<ChatService>()
    val chatRepository by inject<ChatRepository>()
    val rbacEngine by inject<RBACEngine>()
    val providerConfigRepo by inject<ProviderConfigRepository>()
    val settingsRepo by inject<SettingsRepository>()
    val conversationRepo by inject<ChatConversationRepository>()
    val userAIConfigRepo by inject<UserAIConfigRepository>()
    val permService by inject<UserToolPermissionService>()

    route("/api/chat") {
        authenticate("auth-jwt") {
            post("/send") { handleSendMessage(chatService, chatRepository, conversationRepo) }
            post("/execute-action") { handleExecuteAction(rbacEngine, providerConfigRepo, settingsRepo) }
            get("/history") { handleGetHistory(chatRepository) }
            delete("/history") { handleDeleteHistory(chatRepository) }
            chatConversationRoutes(conversationRepo, chatRepository)
            chatConfigRoutes(userAIConfigRepo)
            chatToolPermissionRoutes(permService)
            get("/model-info") { handleModelInfo(providerConfigRepo) }
            get("/tools") { handleGetTools() }
        }
    }
}

private suspend fun RoutingContext.handleSendMessage(
    chatService: ChatService, chatRepo: ChatRepository,
    convRepo: ChatConversationRepository
) {
    val (userId, userRole) = extractUserClaims() ?: return
    val request = call.receive<ChatRequest>()
    val convId = request.conversationId ?: ""

    chatRepo.saveMessageWithConversation(userId, convId, "user", request.message, request.context?.currentScreen)

    if (convId.isNotBlank()) autoTitleConversation(convRepo, convId, request.message)

    val history = if (convId.isNotBlank()) {
        chatRepo.getHistoryByConversation(userId, convId, 0, 20)
    } else {
        chatRepo.getHistory(userId, 0, 20)
    }
    val context = request.context ?: buildDefaultContext(userId, userRole)
    val response = try {
        chatService.processChat(request.message, context, history)
    } catch (e: Exception) {
        ChatResponse(reply = "Error: ${e.message ?: "Unknown"}")
    }

    chatRepo.saveMessageWithConversation(userId, convId, "assistant", response.reply, null)

    if (convId.isNotBlank()) convRepo.updateTimestamp(convId)
    call.respond(HttpStatusCode.OK, response)
}

private suspend fun autoTitleConversation(
    repo: ChatConversationRepository, convId: String, message: String
) {
    val conv = repo.findById(convId) ?: return
    if (conv.title == "New Chat") {
        repo.updateTitle(convId, message.take(50))
    }
}

private fun buildDefaultContext(userId: String, role: String) = ChatContext(
    projectKey = "", currentScreen = "unknown", userRole = role, userId = userId
)
