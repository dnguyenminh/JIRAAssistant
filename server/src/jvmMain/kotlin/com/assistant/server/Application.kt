package com.assistant.server

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerRepository
import com.assistant.scan.BatchScanEngine
import com.assistant.server.config.ServerConfig
import com.assistant.server.di.serverModule
import com.assistant.server.routes.configureRouting
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin

fun main() {
    val config = ServerConfig.load()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: ServerConfig = ServerConfig.load()) {

    install(Koin) {
        modules(serverModule(config))
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "jira-assistant"
            verifier(config.jwtVerifier())
            validate { credential ->
                val userId = credential.payload.getClaim("user_id")?.asString()
                val email = credential.payload.getClaim("email")?.asString()
                val role = credential.payload.getClaim("role")?.asString()
                val projectKey = credential.payload.getClaim("project_key")?.asString()
                this@module.log.debug("JWT validate: userId=$userId, email=$email, role=$role" +
                    if (!projectKey.isNullOrBlank()) ", projectKey=$projectKey" else "")
                if (userId != null && email != null && role != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    this@module.log.warn("JWT validation failed: missing claims")
                    null
                }
            }
            challenge { _, _ ->
                call.respondText("{\"error\":\"Token invalid or expired\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            }
        }
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText("{\"error\":\"${cause.message ?: "Bad request"}\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
        exception<kotlinx.serialization.SerializationException> { call, cause ->
            call.respondText("{\"error\":\"${cause.message?.replace("\"", "'") ?: "Invalid request body"}\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Internal server error", cause)
            call.respondText("{\"error\":\"Internal server error\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.Forbidden) { call, _ ->
            call.respondText("{\"error\":\"Forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
        }
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
    }

    configureRouting()

    // Startup recovery: transition any SCANNING states to PAUSED after server restart
    val batchScanEngine by inject<BatchScanEngine>()
    runBlocking { batchScanEngine.recoverOnStartup() }

    // Auto-configure markitdown MCP server if not already registered
    val mcpServerRepository by inject<McpServerRepository>()
    runBlocking { com.assistant.server.attachment.MarkitdownAutoConfig.ensureConfigured(mcpServerRepository) }

    // Auto-configure default embedding provider if not exists
    val providerConfigRepo by inject<com.assistant.kb.ProviderConfigRepository>()
    ensureEmbeddingProvider(providerConfigRepo)

    // MCP: auto-start enabled servers and register shutdown hook
    val processManager by inject<McpProcessManager>()
    launch { processManager.startAllEnabled() }
    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking { processManager.stopAll() }
    }
}

/** Auto-create default embedding provider config if none exists. */
private fun ensureEmbeddingProvider(repo: com.assistant.kb.ProviderConfigRepository) {
    val existing = repo.findByType(com.assistant.ai.ProviderType.EMBEDDING)
    if (existing != null) return
    // Create default embedding config using Ollama endpoint
    val ollamaConfig = repo.findByType(com.assistant.ai.ProviderType.OLLAMA)
    val endpoint = ollamaConfig?.endpoint ?: "http://localhost:11434"
    repo.save(com.assistant.ai.ProviderConfig(
        providerId = "embedding",
        name = "Embedding Model",
        type = com.assistant.ai.ProviderType.EMBEDDING,
        endpoint = endpoint,
        model = "nomic-embed-text",
        priority = 10,
        status = com.assistant.ai.ConnectionStatus.ACTIVE
    ))
    println("[Application] Auto-created default embedding provider (nomic-embed-text @ $endpoint)")
}
