package com.assistant.server.routes

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import com.assistant.server.services.ValidationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.*

/**
 * CRUD route handlers for user management.
 * Each handler is ≤20 lines. Extracted to keep UserRoutes.kt under 200 lines.
 */

suspend fun RoutingContext.handleCreateUser(
    userStore: UserStore,
    auditLogStore: AuditLogStore
) {
    val request = call.receive<CreateUserRequest>()
    val errors = validateCreateRequest(request)
    if (errors != null) return call.respond(HttpStatusCode.BadRequest, ErrorResponse(errors))

    val role = parseRole(request.role)
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid role: ${request.role}"))

    val actorId = call.principal<JWTPrincipal>()?.payload?.getClaim("user_id")?.asString() ?: ""
    val newUser = User(
        id = UUID.randomUUID().toString(),
        name = request.name.trim(),
        email = request.email.trim(),
        role = role,
        status = UserStatus.ACTIVE,
        createdAt = Instant.now().toString()
    )

    try {
        userStore.addUser(newUser)
    } catch (_: IllegalArgumentException) {
        return call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already exists"))
    }

    appendAudit(auditLogStore, actorId, newUser.id, "USER_CREATED", "", "role=${newUser.role.name}")
    call.respond(HttpStatusCode.Created, newUser.toDto())
}

suspend fun RoutingContext.handleGetUser(userStore: UserStore) {
    val userId = call.parameters["userId"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId is required"))

    val user = userStore.findById(userId)
        ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    call.respond(HttpStatusCode.OK, user.toDto())
}

suspend fun RoutingContext.handleUpdateUser(
    userStore: UserStore,
    auditLogStore: AuditLogStore
) {
    val userId = call.parameters["userId"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId is required"))

    val request = call.receive<UpdateUserRequest>()
    val errors = validateUpdateRequest(request)
    if (errors != null) return call.respond(HttpStatusCode.BadRequest, ErrorResponse(errors))

    val existing = userStore.findById(userId)
        ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    val emailOwner = userStore.findByEmail(request.email.trim())
    if (emailOwner != null && emailOwner.id != userId) {
        return call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already exists"))
    }

    val success = userStore.updateUser(userId, request.name.trim(), request.email.trim())
    if (!success) return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    val actorId = call.principal<JWTPrincipal>()?.payload?.getClaim("user_id")?.asString() ?: ""
    val oldValue = "name=${existing.name}, email=${existing.email}"
    val newValue = "name=${request.name.trim()}, email=${request.email.trim()}"
    appendAudit(auditLogStore, actorId, userId, "USER_UPDATED", oldValue, newValue)

    val updated = userStore.findById(userId)!!
    call.respond(HttpStatusCode.OK, updated.toDto())
}

suspend fun RoutingContext.handleUpdateStatus(
    userStore: UserStore,
    auditLogStore: AuditLogStore
) {
    val userId = call.parameters["userId"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId is required"))

    val request = call.receive<UpdateStatusRequest>()
    val newStatus = parseStatus(request.status)
        ?: return call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Invalid status: ${request.status}. Valid values: ACTIVE, DISABLED")
        )

    val existing = userStore.findById(userId)
        ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    val success = userStore.updateStatus(userId, newStatus)
    if (!success) return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    val actorId = call.principal<JWTPrincipal>()?.payload?.getClaim("user_id")?.asString() ?: ""
    val action = if (newStatus == UserStatus.DISABLED) "USER_DISABLED" else "USER_ENABLED"
    appendAudit(auditLogStore, actorId, userId, action, existing.status.name, newStatus.name)

    val updated = userStore.findById(userId)!!
    call.respond(HttpStatusCode.OK, updated.toDto())
}

suspend fun RoutingContext.handleDeleteUser(
    userStore: UserStore,
    auditLogStore: AuditLogStore
) {
    val userId = call.parameters["userId"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId is required"))

    val actorId = call.principal<JWTPrincipal>()?.payload?.getClaim("user_id")?.asString() ?: ""
    if (actorId == userId) {
        return call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cannot delete your own account"))
    }

    val existing = userStore.findById(userId)
        ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    val success = userStore.deleteUser(userId)
    if (!success) return call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

    appendAudit(auditLogStore, actorId, userId, "USER_DELETED", "name=${existing.name}, email=${existing.email}", "")
    call.respond(HttpStatusCode.NoContent)
}

// --- Private helpers ---

private fun validateCreateRequest(request: CreateUserRequest): String? {
    if (!ValidationService.isValidName(request.name)) return "Name is required"
    if (!ValidationService.isValidEmail(request.email)) return "Invalid email format"
    return null
}

private fun validateUpdateRequest(request: UpdateUserRequest): String? {
    if (!ValidationService.isValidName(request.name)) return "Name is required"
    if (!ValidationService.isValidEmail(request.email)) return "Invalid email format"
    return null
}

private fun parseRole(role: String): UserRole? {
    return try { UserRole.valueOf(role) } catch (_: IllegalArgumentException) { null }
}

private fun parseStatus(status: String): UserStatus? {
    return try {
        val parsed = UserStatus.valueOf(status)
        if (parsed == UserStatus.PENDING) null else parsed
    } catch (_: IllegalArgumentException) { null }
}

private suspend fun appendAudit(
    store: AuditLogStore,
    actorId: String,
    targetUserId: String,
    action: String,
    oldValue: String,
    newValue: String
) {
    store.append(
        AuditLogEntry(
            timestamp = Instant.now().toString(),
            actorId = actorId,
            targetUserId = targetUserId,
            action = action,
            oldValue = oldValue,
            newValue = newValue,
            tag = "IAM_SYNC"
        )
    )
}
