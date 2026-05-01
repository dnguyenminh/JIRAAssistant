package com.assistant.server.routes

import com.assistant.chat.ToolPermissionsBulkRequest
import com.assistant.chat.ToolPermissionsUpdateRequest
import com.assistant.server.chat.UserToolPermissionService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Tool permission routes — per-user enable/disable MCP tools.
 * Requirements: 3.2, 3.3, 3.6
 */
internal fun Route.chatToolPermissionRoutes(service: UserToolPermissionService) {
    get("/tool-permissions") { handleGetPermissions(service) }
    put("/tool-permissions") { handleUpdatePermissions(service) }
    put("/tool-permissions/bulk") { handleBulkUpdate(service) }
}

/** GET /tool-permissions — lấy effective permissions per-user + defaults. Req: 3.2 */
private suspend fun RoutingContext.handleGetPermissions(service: UserToolPermissionService) {
    val (userId, _) = extractUserClaims() ?: return
    try {
        val response = service.getEffectivePermissions(userId)
        call.respond(HttpStatusCode.OK, response)
    } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to load permissions"))
    }
}

/** PUT /tool-permissions — cập nhật permissions per-user. Req: 3.3 */
private suspend fun RoutingContext.handleUpdatePermissions(service: UserToolPermissionService) {
    val (userId, _) = extractUserClaims() ?: return
    val request = call.receive<ToolPermissionsUpdateRequest>()
    val result = service.savePermissions(userId, request.permissions)
    if (result.isSuccess) {
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    } else {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.exceptionOrNull()?.message ?: "Validation failed"))
    }
}

/** PUT /tool-permissions/bulk — bulk enable/disable tools của 1 server. Req: 3.6 */
private suspend fun RoutingContext.handleBulkUpdate(service: UserToolPermissionService) {
    val (userId, _) = extractUserClaims() ?: return
    val request = call.receive<ToolPermissionsBulkRequest>()
    val result = service.bulkUpdate(userId, request.serverId, request.action)
    respondBulkResult(result)
}

/** Map bulk result to appropriate HTTP status. */
private suspend fun RoutingContext.respondBulkResult(result: Result<Unit>) {
    if (result.isSuccess) {
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
        return
    }
    val error = result.exceptionOrNull()
    when (error) {
        is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, ErrorResponse(error.message ?: "Not found"))
        else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(error?.message ?: "Invalid request"))
    }
}
