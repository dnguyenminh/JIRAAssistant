package com.assistant.server.routes

import com.assistant.kb.ProviderConfigRepository
import com.assistant.rbac.Permission
import com.assistant.server.config.ServerConfig
import com.assistant.server.middleware.withPermission
import com.assistant.settings.AppSettings
import com.assistant.settings.AppSettingsResponse
import com.assistant.settings.SettingsRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class SettingsStatusResponse(val configured: Boolean)

@Serializable
data class FeatureToggleRequest(val key: String, val value: String)

@Serializable
data class FeatureToggleResponse(val key: String, val value: String)

/**
 * Settings routes.
 * GET  /api/settings/status          — public, { configured: bool }
 * GET  /api/settings                 — admin, masked settings
 * PUT  /api/settings                 — admin, update settings
 * GET  /api/settings/feature?key=X   — feature toggle read. Req: 19.76
 * PUT  /api/settings/feature         — feature toggle write. Req: 19.76
 */
fun Routing.settingsRoutes() {
    val settingsRepo by inject<SettingsRepository>()
    val serverConfig by inject<ServerConfig>()
    val providerConfigRepo by inject<ProviderConfigRepository>()

    get("/api/settings/status") {
        val configured = providerConfigRepo.existsByType(com.assistant.ai.ProviderType.JIRA)
        call.respond(HttpStatusCode.OK, SettingsStatusResponse(configured))
    }

    route("/api/settings") {
        withPermission(Permission.MANAGE_SETTINGS) {
            get { handleGetSettings(settingsRepo, serverConfig) }
            put { handlePutSettings(settingsRepo, serverConfig) }
        }
    }

    // Feature toggle — lighter permission for integration toggles. Req: 19.76
    route("/api/settings/feature") {
        withPermission(Permission.CONFIG_INTEGRATIONS) {
            get {
                val key = call.request.queryParameters["key"]
                if (key.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'key'"))
                    return@get
                }
                val value = settingsRepo.get(key) ?: "true"
                call.respond(HttpStatusCode.OK, FeatureToggleResponse(key, value))
            }
            put {
                val body = call.receive<FeatureToggleRequest>()
                if (body.key.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("'key' must not be blank"))
                    return@put
                }
                // Validate numeric settings that require >= 1. Req: AC 49, AC 50
                if (body.key in listOf("batch_prompt_size", "scan_concurrency")) {
                    val intVal = body.value.toIntOrNull()
                    if (intVal == null || intVal < 1) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("${body.key} must be >= 1"))
                        return@put
                    }
                }
                settingsRepo.put(body.key, body.value)
                call.respond(HttpStatusCode.OK, FeatureToggleResponse(body.key, body.value))
            }
        }
    }
}

private suspend fun RoutingContext.handleGetSettings(
    settingsRepo: SettingsRepository, serverConfig: ServerConfig
) {
    val raw = settingsRepo.getAll()
    val current = AppSettings(
        jwtSecret = raw["JWT_SECRET"] ?: serverConfig.jwtSecret,
        encryptionKey = raw["ENCRYPTION_KEY"] ?: serverConfig.encryptionKey,
        port = raw["PORT"]?.toIntOrNull() ?: serverConfig.port
    )
    call.respond(HttpStatusCode.OK, AppSettingsResponse.fromSettings(current))
}

private suspend fun RoutingContext.handlePutSettings(
    settingsRepo: SettingsRepository, serverConfig: ServerConfig
) {
    val request = call.receive<AppSettings>()
    request.port?.let { if (it !in 1..65535) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("port must be between 1 and 65535")); return
    }}
    val settingsMap = buildMap {
        request.jwtSecret?.let { put("JWT_SECRET", it) }
        request.encryptionKey?.let { put("ENCRYPTION_KEY", it) }
        request.port?.let { put("PORT", it.toString()) }
    }
    settingsRepo.putAll(settingsMap)
    val saved = settingsRepo.getAll()
    val updated = AppSettings(
        jwtSecret = saved["JWT_SECRET"] ?: serverConfig.jwtSecret,
        encryptionKey = saved["ENCRYPTION_KEY"] ?: serverConfig.encryptionKey,
        port = saved["PORT"]?.toIntOrNull() ?: serverConfig.port
    )
    call.respond(HttpStatusCode.OK, AppSettingsResponse.fromSettings(updated))
}
