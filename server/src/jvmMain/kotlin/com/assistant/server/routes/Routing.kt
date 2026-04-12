package com.assistant.server.routes

import com.assistant.server.config.ServerConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Configures all route groups for the Ktor application.
 */
fun Application.configureRouting() {
    val config = ServerConfig.load()
    val staticDir = config.staticDir

    routing {
        // Simple JWT test endpoint
        authenticate("auth-jwt") {
            get("/api/test-auth") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString() ?: "unknown"
                call.respondText("{\"authenticated\":true,\"user\":\"$email\"}", ContentType.Application.Json)
            }
        }

        // API routes
        healthRoutes()
        authRoutes()
        userRoutes()
        analysisRoutes()
        estimationRoutes()
        graphRoutes()
        projectRoutes()
        integrationRoutes()
        settingsRoutes()
        scanRoutes()
        chatRoutes()
        chatUploadRoutes()
        mcpRoutes()
        mcpRuntimeRoutes()
        attachmentRoutes()
        ticketDetailRoutes()

        // Serve frontend static files (CSS, JS, templates, assets)
        if (staticDir.isNotBlank()) {
            val staticRoot = File(staticDir)
            // Additional directories to search for static files
            val frontendRoot = File("frontend")
            val jsBuildDir = File("frontend/build/kotlin-webpack/js/developmentExecutable")
            val jsProdDir = File("frontend/build/dist/js/productionExecutable")

            val searchDirs = listOfNotNull(
                staticRoot.takeIf { it.exists() },
                jsBuildDir.takeIf { it.exists() },
                jsProdDir.takeIf { it.exists() },
                frontendRoot.takeIf { it.exists() }
            )

            // Catch-all: serve static files or SPA fallback
            get("{...}") {
                val requestPath = call.request.local.uri
                if (requestPath.startsWith("/api/") || requestPath == "/health") {
                    return@get
                }

                val relativePath = requestPath.removePrefix("/")
                if (relativePath.isNotEmpty()) {
                    // Search all directories for the requested file
                    for (dir in searchDirs) {
                        val file = File(dir, relativePath)
                        if (file.exists() && file.isFile && file.canonicalPath.startsWith(dir.canonicalPath)) {
                            call.respondFile(file)
                            return@get
                        }
                    }
                }

                // SPA fallback: serve index.html
                val indexFile = searchDirs.map { File(it, "index.html") }.firstOrNull { it.exists() }
                if (indexFile != null) {
                    call.respondFile(indexFile)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Frontend not found"))
                }
            }
        }
    }
}
