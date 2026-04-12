package com.assistant.server.routes

import com.assistant.auth.UserRole
import com.assistant.chat.*
import com.assistant.kb.ProviderConfigRepository
import com.assistant.rbac.Permission
import com.assistant.rbac.RBACEngine
import com.assistant.settings.SettingsRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Chat action execution handler (execute-action endpoint).
 * Requirements: 19.21, 19.13, 19.14
 */
internal suspend fun RoutingContext.handleExecuteAction(
    rbacEngine: RBACEngine,
    providerConfigRepo: ProviderConfigRepository,
    settingsRepo: SettingsRepository
) {
    val (userId, userRole) = extractUserClaims() ?: return
    val request = call.receive<ChatActionRequest>()

    when (request.actionType) {
        "changeConfig" -> handleConfigChange(userRole, request, providerConfigRepo, settingsRepo)
        "triggerAnalysis" -> handleTriggerAnalysis(userRole, rbacEngine, request)
        "navigate" -> handleNavigate(request)
        else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown action: ${request.actionType}"))
    }
}

private suspend fun RoutingContext.handleConfigChange(
    userRole: String, request: ChatActionRequest,
    providerConfigRepo: ProviderConfigRepository, settingsRepo: SettingsRepository
) {
    if (userRole != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator role required."))
        return
    }
    val result = executeConfigChange(request.parameters, providerConfigRepo, settingsRepo)
    call.respond(HttpStatusCode.OK, result)
}

private suspend fun RoutingContext.handleTriggerAnalysis(
    userRole: String, rbacEngine: RBACEngine, request: ChatActionRequest
) {
    val role = try { UserRole.valueOf(userRole) } catch (_: Exception) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid role"))
        return
    }
    if (!rbacEngine.hasPermission(role, Permission.ANALYZE_AI)) {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Insufficient permissions."))
        return
    }
    val ticketId = request.parameters["ticketId"] ?: "unknown"
    call.respond(HttpStatusCode.OK, ChatActionResponse(true, "Analysis triggered for $ticketId"))
}

private suspend fun RoutingContext.handleNavigate(request: ChatActionRequest) {
    val screen = request.parameters["screen"] ?: "dashboard"
    call.respond(HttpStatusCode.OK, ChatActionResponse(true, "Navigate to $screen"))
}

internal suspend fun executeConfigChange(
    params: Map<String, String>,
    providerConfigRepo: ProviderConfigRepository,
    settingsRepo: SettingsRepository
): ChatActionResponse {
    val configType = params["configType"]
        ?: return ChatActionResponse(false, "Missing configType")
    return try {
        when (configType) {
            "provider" -> executeProviderChange(params, providerConfigRepo)
            "setting" -> executeSettingChange(params, settingsRepo)
            else -> ChatActionResponse(false, "Unknown configType: $configType")
        }
    } catch (e: Exception) {
        ChatActionResponse(false, "Config change failed: ${e.message}")
    }
}

private suspend fun executeProviderChange(
    params: Map<String, String>, repo: ProviderConfigRepository
): ChatActionResponse {
    val id = params["providerId"] ?: return ChatActionResponse(false, "Missing providerId")
    val field = params["field"] ?: return ChatActionResponse(false, "Missing field")
    val value = params["value"] ?: return ChatActionResponse(false, "Missing value")
    val existing = repo.findById(id) ?: return ChatActionResponse(false, "Provider '$id' not found")
    val updated = when (field) {
        "endpoint" -> existing.copy(endpoint = value)
        "model" -> existing.copy(model = value)
        else -> return ChatActionResponse(false, "Unknown field: $field")
    }
    repo.save(updated)
    return ChatActionResponse(true, "Updated $id.$field to '$value'")
}

private suspend fun executeSettingChange(
    params: Map<String, String>, repo: SettingsRepository
): ChatActionResponse {
    val key = params["key"] ?: return ChatActionResponse(false, "Missing key")
    val value = params["value"] ?: return ChatActionResponse(false, "Missing value")
    val old = repo.get(key) ?: "(not set)"
    repo.put(key, value)
    return ChatActionResponse(true, "Setting '$key': '$old' → '$value'")
}
