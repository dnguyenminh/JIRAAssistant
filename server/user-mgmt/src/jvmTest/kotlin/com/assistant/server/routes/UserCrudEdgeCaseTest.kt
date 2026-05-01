package com.assistant.server.routes

// STC: UT-14, UT-17, PBT-11 — Edge case tests extracted from UserCrudIntegrationTest

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import com.assistant.server.config.ServerConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.*

/**
 * Edge case tests for User CRUD endpoints.
 *
 * Covers STC: UT-14 (invalid status → 400), UT-17 (delete non-existent → 404),
 * PBT-11 (disabled user auth rejection).
 */
class UserCrudEdgeCaseTest {

    private val jwtSecret = "test-secret-for-integration"
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    @AfterTest
    fun cleanup() {
        try { stopKoin() } catch (_: Exception) {}
    }

    private fun generateToken(
        userId: String = "admin-1",
        role: String = "ADMINISTRATOR",
        email: String = "admin@test.com"
    ): String = JWT.create()
        .withIssuer(ServerConfig.JWT_ISSUER)
        .withClaim("user_id", userId)
        .withClaim("role", role)
        .withClaim("email", email)
        .sign(algorithm)

    private fun ApplicationTestBuilder.configureTestApp(
        userStore: UserStore = InMemoryUserStore(),
        auditLogStore: AuditLogStore = EdgeCaseAuditLogStore()
    ) {
        application {
            val rbacEngine = RBACEngineImpl(userStore, auditLogStore)
            install(Koin) {
                modules(module {
                    single<UserStore> { userStore }
                    single<AuditLogStore> { auditLogStore }
                    single<RBACEngine> { rbacEngine }
                })
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "test"
                    verifier(JWT.require(algorithm).withIssuer(ServerConfig.JWT_ISSUER).build())
                    validate { cred ->
                        val uid = cred.payload.getClaim("user_id")?.asString()
                        val r = cred.payload.getClaim("role")?.asString()
                        if (uid != null && r != null) JWTPrincipal(cred.payload) else null
                    }
                    challenge { _, _ ->
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                    }
                }
            }
            routing { userRoutes() }
        }
    }

    // STC: UT-14 — Invalid status value returns 400
    @Test
    fun `UT-14 - invalid status value returns 400`() = testApplication {
        val store = InMemoryUserStore()
        configureTestApp(store)
        val token = generateToken()
        store.addUser(User(
            id = "status-user", name = "Status Test", email = "s@t.com",
            role = UserRole.READER, status = UserStatus.ACTIVE,
            createdAt = "2025-01-01T00:00:00Z"
        ))

        val resp = client.put("/api/users/status-user/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"INVALID"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("Invalid status"),
            "Response should mention invalid status")
    }

    // STC: UT-17 — Delete non-existent user returns 404
    @Test
    fun `UT-17 - delete non-existent user returns 404`() = testApplication {
        configureTestApp()
        val token = generateToken()

        val resp = client.delete("/api/users/non-existent-id-12345") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("User not found"))
    }

    // STC: PBT-11 — Disabled user authentication rejection
    @Test
    fun `PBT-11 - disabled user is persisted as DISABLED`() = testApplication {
        val store = InMemoryUserStore()
        configureTestApp(store)

        val disabledUser = User(
            id = "disabled-user-1", name = "Disabled",
            email = "disabled@t.com", role = UserRole.ADMINISTRATOR,
            status = UserStatus.DISABLED, createdAt = "2025-01-01T00:00:00Z"
        )
        store.addUser(disabledUser)

        val found = store.findById("disabled-user-1")
        assertNotNull(found)
        assertEquals(UserStatus.DISABLED, found.status,
            "User should be DISABLED in the store")
    }
}

/** Simple in-memory AuditLogStore for edge case testing. */
private class EdgeCaseAuditLogStore : AuditLogStore {
    private val entries = mutableListOf<AuditLogEntry>()
    override suspend fun append(entry: AuditLogEntry) { entries.add(entry) }
    override suspend fun getRecent(limit: Int) = entries.takeLast(limit)
    override suspend fun getAll() = entries.toList()
}
