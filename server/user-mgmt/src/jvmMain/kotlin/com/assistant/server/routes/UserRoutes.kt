package com.assistant.server.routes

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class ChangeRoleRequest(val role: String)

@Serializable
data class TogglePermissionRequest(val permission: String, val enabled: Boolean)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val avatarUrl: String?,
    val customPermissions: List<String>
)

fun Routing.userRoutes() {
    val rbacEngine by inject<RBACEngine>()
    val userStore by inject<UserStore>()
    val auditLogStore by inject<AuditLogStore>()

    route("/api/users") {
        withPermission(Permission.MANAGE_USERS) {
            get("/audit-log") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val entries = auditLogStore.getRecent(limit)
                call.respond(HttpStatusCode.OK, entries)
            }

            get {
                val users = userStore.getAll().map { it.toDto() }
                call.respond(HttpStatusCode.OK, users)
            }

            put("/{userId}/role") {
                val userId = call.parameters["userId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId is required"))

                val request = call.receive<ChangeRoleRequest>()
                val newRole = try {
                    UserRole.valueOf(request.role)
                } catch (_: IllegalArgumentException) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid role: ${request.role}")
                    )
                }

                val principal = call.principal<JWTPrincipal>()
                val adminId = principal?.payload?.getClaim("user_id")?.asString() ?: ""

                when (val result = rbacEngine.changeRole(adminId, userId, newRole)) {
                    is RBACResult.Success -> call.respond(HttpStatusCode.OK, mapOf("message" to result.message))
                    is RBACResult.Failure -> call.respond(
                        HttpStatusCode.fromValue(result.code),
                        ErrorResponse(result.message)
                    )
                }
            }

            put("/{userId}/permissions") {
                val userId = call.parameters["userId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId is required"))

                val request = call.receive<TogglePermissionRequest>()
                val permission = try {
                    Permission.valueOf(request.permission)
                } catch (_: IllegalArgumentException) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid permission: ${request.permission}")
                    )
                }

                val principal = call.principal<JWTPrincipal>()
                val adminId = principal?.payload?.getClaim("user_id")?.asString() ?: ""

                when (val result = rbacEngine.togglePermission(adminId, userId, permission, request.enabled)) {
                    is RBACResult.Success -> call.respond(HttpStatusCode.OK, mapOf("message" to result.message))
                    is RBACResult.Failure -> call.respond(
                        HttpStatusCode.fromValue(result.code),
                        ErrorResponse(result.message)
                    )
                }
            }
        }
    }
}

private fun User.toDto() = UserDto(
    id = id,
    name = name,
    email = email,
    role = role.name,
    avatarUrl = avatarUrl,
    customPermissions = customPermissions.map { it.name }
)
