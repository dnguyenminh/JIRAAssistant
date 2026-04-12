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
import java.net.URL

/** Response for the public /api/settings/status endpoint. */
@Serializable
data class SettingsStatusResponse(val configured: Boolean)

/**
 * Settings routes.
 * GET  /api/settings/status — public (no JWT). Returns { "configured": true/false }
 * GET  /api/settings        — admin-only, read current settings (sensitive fields masked)
 * PUT  /api/settings        — admin-only, update settings (dbPath not updatable via API)
 */
fun Routing.settingsRoutes() {
    val settingsRepo by inject<SettingsRepository>()
    val serverConfig by inject<ServerConfig>()
    val providerConfigRepo by inject<ProviderConfigRepository>()

    // ── Public endpoint (no JWT required) ──────────────────────────
    get("/api/settings/status") {
        val configured = providerConfigRepo.existsByType(com.assistant.ai.ProviderType.JIRA)
        call.respond(HttpStatusCode.OK, SettingsStatusResponse(configured))
    }

    // ── Protected endpoints ────────────────────────────────────────
    route("/api/settings") {
        withPermission(Permission.MANAGE_SETTINGS) {
            get {
                val raw = settingsRepo.getAll()
                val current = AppSettings(
                    jiraHost = raw["JIRA_HOST"] ?: serverConfig.jiraHost,
                    aiProviderUrl = raw["AI_PROVIDER_URL"] ?: serverConfig.aiProviderUrl,
                    dbPath = serverConfig.dbPath,
                    jwtSecret = raw["JWT_SECRET"] ?: serverConfig.jwtSecret,
                    encryptionKey = raw["ENCRYPTION_KEY"] ?: serverConfig.encryptionKey,
                    port = raw["PORT"]?.toIntOrNull() ?: serverConfig.port
                )
                call.respond(HttpStatusCode.OK, AppSettingsResponse.fromSettings(current))
            }

            put {
                val request = call.receive<AppSettings>()

                // Validate jiraHost — must be a valid URL if provided
                request.jiraHost?.let { host ->
                    if (!isValidUrl(host)) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("jiraHost must be a valid URL"))
                        return@put
                    }
                }

                // Validate aiProviderUrl — must be a valid URL if provided
                request.aiProviderUrl?.let { url ->
                    if (!isValidUrl(url)) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("aiProviderUrl must be a valid URL"))
                        return@put
                    }
                }

                // Validate port — must be 1–65535 if provided
                request.port?.let { port ->
                    if (port !in 1..65535) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("port must be between 1 and 65535"))
                        return@put
                    }
                }

                // Build settings map — dbPath is never updatable via API
                val settingsMap = buildMap {
                    request.jiraHost?.let { put("JIRA_HOST", it) }
                    request.aiProviderUrl?.let { put("AI_PROVIDER_URL", it) }
                    request.jwtSecret?.let { put("JWT_SECRET", it) }
                    request.encryptionKey?.let { put("ENCRYPTION_KEY", it) }
                    request.port?.let { put("PORT", it.toString()) }
                }

                settingsRepo.putAll(settingsMap)

                // Re-read to build masked response
                val saved = settingsRepo.getAll()
                val updated = AppSettings(
                    jiraHost = saved["JIRA_HOST"] ?: serverConfig.jiraHost,
                    aiProviderUrl = saved["AI_PROVIDER_URL"] ?: serverConfig.aiProviderUrl,
                    dbPath = serverConfig.dbPath,
                    jwtSecret = saved["JWT_SECRET"] ?: serverConfig.jwtSecret,
                    encryptionKey = saved["ENCRYPTION_KEY"] ?: serverConfig.encryptionKey,
                    port = saved["PORT"]?.toIntOrNull() ?: serverConfig.port
                )
                call.respond(HttpStatusCode.OK, AppSettingsResponse.fromSettings(updated))
            }
        }
    }
}

/**
 * Validates that a string is a well-formed URL with http or https scheme.
 */
private fun isValidUrl(value: String): Boolean {
    return try {
        val url = URL(value)
        url.protocol in listOf("http", "https")
    } catch (_: Exception) {
        false
    }
}

