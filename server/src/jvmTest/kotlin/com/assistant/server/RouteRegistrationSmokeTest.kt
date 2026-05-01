package com.assistant.server

import com.assistant.server.config.ServerConfig
import com.assistant.server.di.serverModule
import com.assistant.server.routes.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.assertNotEquals

/**
 * Route registration smoke test — verifies that key
 * API endpoints from every sub-module are reachable
 * (i.e. NOT returning 404).
 *
 * Uses a lightweight Ktor setup with Koin module
 * composition and JWT auth, but overrides the
 * DataSource with a no-op stub so no real database
 * is needed. A non-404 response (typically 401 or
 * 500) proves the route handler is wired up.
 */
class RouteRegistrationSmokeTest {

    private val testConfig = ServerConfig(
        jiraHost = "https://jira.test.local",
        aiProviderUrl = "http://localhost:11434",
        jwtSecret = "test-jwt-secret-for-route-check",
        encryptionKey = "test-encryption-key-0123456",
        port = 0,
        staticDir = "",
    )

    private data class Endpoint(
        val method: HttpMethod,
        val path: String,
        val module: String,
    )

    private val endpoints = listOf(
        // core — auth, projects
        Endpoint(HttpMethod.Post, "/api/auth/login", "core"),
        Endpoint(HttpMethod.Get, "/api/projects", "core"),
        // dashboard — scan active
        Endpoint(HttpMethod.Get, "/api/scan/active", "dashboard"),
        // analysis — ticket analysis
        Endpoint(
            HttpMethod.Get,
            "/api/analysis/TICKET-1",
            "analysis",
        ),
        // docgen — job listing
        Endpoint(HttpMethod.Get, "/api/jobs", "docgen"),
        // chat — send message
        Endpoint(HttpMethod.Post, "/api/chat/send", "chat"),
        // mcp — list MCP servers
        Endpoint(
            HttpMethod.Get,
            "/api/integrations/mcp",
            "mcp",
        ),
        // user-mgmt — list users
        Endpoint(HttpMethod.Get, "/api/users", "user-mgmt"),
    )

    @Test
    fun `all sub-module routes are registered`() =
        testApplication {
            application { installTestApp(testConfig) }

            for (ep in endpoints) {
                val response = client.request(ep.path) {
                    method = ep.method
                }
                assertNotEquals(
                    HttpStatusCode.NotFound,
                    response.status,
                    "Route ${ep.method.value} ${ep.path}" +
                        " (${ep.module}) returned 404 — " +
                        "route not registered",
                )
            }
        }
}

/**
 * Lightweight Ktor setup for route smoke testing.
 *
 * Installs Koin with the full [serverModule] graph
 * plus a DataSource override so no real PostgreSQL
 * connection is needed. Skips startup hooks (MCP
 * auto-start, scan recovery, etc.).
 */
private fun Application.installTestApp(config: ServerConfig) {
    val dsOverride = module(createdAtStart = true) {
        single<DataSource> { NoOpDataSource() }
    }

    install(Koin) {
        allowOverride(true)
        modules(serverModule(config), dsOverride)
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "jira-assistant"
            verifier(config.jwtVerifier())
            validate { credential ->
                val uid = credential.payload
                    .getClaim("user_id")?.asString()
                val email = credential.payload
                    .getClaim("email")?.asString()
                val role = credential.payload
                    .getClaim("role")?.asString()
                if (uid != null && email != null && role != null)
                    JWTPrincipal(credential.payload)
                else null
            }
            challenge { _, _ ->
                call.respondText(
                    """{"error":"Unauthorized"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized,
                )
            }
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, _ ->
            call.respondText(
                """{"error":"Internal server error"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }

    routing {
        configureCoreRoutes()
        configureDashboardRoutes()
        configureAnalysisRoutes()
        configureDocgenRoutes()
        configureChatRoutes()
        configureMcpRoutes()
        configureKnowledgeGraphRoutes()
        configureUserMgmtRoutes()
    }
}

/**
 * Minimal [DataSource] stub that throws on every
 * connection attempt. Sufficient for route registration
 * verification — handlers that try to query the DB
 * will fail with 500, which is still non-404.
 */
private class NoOpDataSource : DataSource {
    override fun getConnection(): Connection =
        error("NoOpDataSource: no real DB in smoke test")

    override fun getConnection(u: String?, p: String?) =
        getConnection()

    override fun getLogWriter(): PrintWriter? = null
    override fun setLogWriter(out: PrintWriter?) = Unit
    override fun setLoginTimeout(seconds: Int) = Unit
    override fun getLoginTimeout(): Int = 0
    override fun getParentLogger(): Logger =
        Logger.getLogger("NoOpDataSource")

    override fun <T : Any?> unwrap(iface: Class<T>?): T =
        error("unwrap not supported")

    override fun isWrapperFor(iface: Class<*>?): Boolean =
        false
}
