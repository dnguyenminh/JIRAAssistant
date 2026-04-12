package com.assistant.server.routes

import com.assistant.chat.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Chat history and conversation sub-routes.
 * Requirements: 19.22, 19.23, 19.48
 */
internal suspend fun RoutingContext.handleGetHistory(chatRepo: ChatRepository) {
    val (userId, _) = extractUserClaims() ?: return
    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
    val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 50
    val convId = call.request.queryParameters["conversationId"]

    val impl = chatRepo as? ChatRepositoryImpl
    val messages = if (!convId.isNullOrBlank() && impl != null) {
        impl.getHistoryByConversation(userId, convId, page, size)
    } else {
        chatRepo.getHistory(userId, page, size)
    }
    val total = chatRepo.getHistoryCount(userId)
    call.respond(HttpStatusCode.OK, ChatHistoryResponse(messages, total, page, size))
}

internal suspend fun RoutingContext.handleDeleteHistory(chatRepo: ChatRepository) {
    val (userId, _) = extractUserClaims() ?: return
    try {
        chatRepo.deleteHistory(userId)
        call.respond(HttpStatusCode.OK, ChatActionResponse(true, "Chat history deleted"))
    } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Delete failed: ${e.message}"))
    }
}

internal fun Route.chatConversationRoutes(
    convRepo: ChatConversationRepository, chatRepo: ChatRepository
) {
    get("/conversations") {
        val (userId, _) = extractUserClaims() ?: return@get
        val conversations = convRepo.getByUser(userId)
        call.respond(HttpStatusCode.OK, conversations)
    }
    post("/conversations") {
        val (userId, _) = extractUserClaims() ?: return@post
        val conv = convRepo.create(userId)
        call.respond(HttpStatusCode.Created, conv)
    }
    put("/conversations/{id}") {
        extractUserClaims() ?: return@put
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
        val body = call.receive<ConversationRenameRequest>()
        convRepo.updateTitle(id, body.title)
        call.respond(HttpStatusCode.OK, ChatActionResponse(true, "Renamed"))
    }
    delete("/conversations/{id}") {
        extractUserClaims() ?: return@delete
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
        (chatRepo as? ChatRepositoryImpl)?.deleteHistoryByConversation(id)
        convRepo.delete(id)
        call.respond(HttpStatusCode.OK, ChatActionResponse(true, "Deleted"))
    }
}

@kotlinx.serialization.Serializable
data class ConversationRenameRequest(val title: String)
