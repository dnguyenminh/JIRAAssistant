package com.assistant.server.routes

import com.assistant.server.config.ServerConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Configures all route groups for the Ktor application.
 *
 * Delegates domain-specific route registration to each
 * sub-module's `configure*Routes()` extension function.
 * Only the JWT test endpoint and static file serving
 * remain in the aggregator.
 */
fun Application.configureRouting() {
    val config = ServerConfig.load()
    val staticDir = config.staticDir

    routing {
        // Simple JWT test endpoint (aggregator-level)
        authenticate("auth-jwt") {
            get("/api/test-auth") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString() ?: "unknown"
                call.respondText(
                    "{\"authenticated\":true,\"user\":\"$email\"}",
                    ContentType.Application.Json,
                )
            }
        }

        // Sub-module route registration
        configureCoreRoutes()
        configureDashboardRoutes()
        configureAnalysisRoutes()
        configureDocgenRoutes()
        configureChatRoutes()
        configureMcpRoutes()
        configureKnowledgeGraphRoutes()
        configureUserMgmtRoutes()

        // Serve frontend static files (stays in aggregator)
        configureStaticFileServing(staticDir)
    }
}

/**
 * Serves frontend static files (CSS, JS, templates, assets)
 * with SPA fallback to index.html.
 */
private fun Routing.configureStaticFileServing(
    staticDir: String,
) {
    if (staticDir.isBlank()) return

    val staticRoot = File(staticDir)
    val frontendRoot = File("frontend")
    val jsBuildDir = File("frontend/build/kotlin-webpack/js/developmentExecutable")
    val jsProdDir = File("frontend/build/dist/js/productionExecutable")

    val searchDirs = listOfNotNull(
        staticRoot.takeIf { it.exists() },
        jsBuildDir.takeIf { it.exists() },
        jsProdDir.takeIf { it.exists() },
        frontendRoot.takeIf { it.exists() },
    )

    // Catch-all: serve static files or SPA fallback
    get("{...}") {
        val requestPath = call.request.local.uri
        if (requestPath.startsWith("/api/") || requestPath == "/health") {
            return@get
        }

        val relativePath = requestPath.removePrefix("/")
        if (relativePath.isNotEmpty()) {
            for (dir in searchDirs) {
                val file = File(dir, relativePath)
                if (file.exists() && file.isFile &&
                    file.canonicalPath.startsWith(dir.canonicalPath)
                ) {
                    call.respondFile(file)
                    return@get
                }
            }
        }

        // SPA fallback: serve index.html
        val indexFile = searchDirs
            .map { File(it, "index.html") }
            .firstOrNull { it.exists() }
        if (indexFile != null) {
            call.respondFile(indexFile)
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Frontend not found"),
            )
        }
    }
}
