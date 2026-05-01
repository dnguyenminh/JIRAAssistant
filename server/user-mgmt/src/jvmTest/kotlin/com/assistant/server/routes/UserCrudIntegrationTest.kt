package com.assistant.server.routes

// Feature: user-crud-profile, Property 12: Unauthorized access rejection
// **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7**

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import com.assistant.server.config.ServerConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.*

/**
 * Integration tests for User CRUD endpoints.
 * Tests full lifecycle and authorization via Ktor test engine.
 *
 * **Property 12: Unauthorized access rejection**
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7**
 */
@OptIn(ExperimentalKotest::class)
class UserCrudIntegrationTest {

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
        auditLogStore: AuditLogStore = InMemoryAuditLogStore()
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

    // ── Full CRUD lifecycle ─────────────────────────────────────

    @Test
    fun `full lifecycle - create, get, update, disable, enable, delete`() = testApplication {
        val store = InMemoryUserStore()
        val auditStore = InMemoryAuditLogStore()
        configureTestApp(store, auditStore)
        val token = generateToken()

        // CREATE
        val createResp = client.post("/api/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"John Doe","email":"john@test.com","role":"NEURAL_ARCHITECT"}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val createBody = createResp.bodyAsText()
        assertTrue(createBody.contains("john@test.com"))
        assertTrue(createBody.contains("ACTIVE"))

        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val userId = idRegex.find(createBody)!!.groupValues[1]

        // GET
        val getResp = client.get("/api/users/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getResp.status)
        assertTrue(getResp.bodyAsText().contains("John Doe"))

        // UPDATE
        val updateResp = client.put("/api/users/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jane Doe","email":"jane@test.com"}""")
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)
        assertTrue(updateResp.bodyAsText().contains("Jane Doe"))

        // DISABLE
        val disableResp = client.put("/api/users/$userId/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"DISABLED"}""")
        }
        assertEquals(HttpStatusCode.OK, disableResp.status)
        assertTrue(disableResp.bodyAsText().contains("DISABLED"))

        // ENABLE
        val enableResp = client.put("/api/users/$userId/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"ACTIVE"}""")
        }
        assertEquals(HttpStatusCode.OK, enableResp.status)
        assertTrue(enableResp.bodyAsText().contains("ACTIVE"))

        // DELETE
        val deleteResp = client.delete("/api/users/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResp.status)

        // Verify deleted
        val getAfterDelete = client.get("/api/users/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, getAfterDelete.status)
    }

    // ── Authorization: no JWT → 401 ────────────────────────────

    @Test
    fun `requests without JWT return 401`() = testApplication {
        configureTestApp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/users") {
            contentType(ContentType.Application.Json); setBody("{}")
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users/x").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/users/x") {
            contentType(ContentType.Application.Json); setBody("{}")
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/users/x/status") {
            contentType(ContentType.Application.Json); setBody("{}")
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/users/x").status)
    }

    // ── Authorization: no MANAGE_USERS → 403 ───────────────────

    @Test
    fun `requests without MANAGE_USERS permission return 403`() = testApplication {
        configureTestApp()
        val readerToken = generateToken(userId = "reader-1", role = "READER")

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/users") {
            header(HttpHeaders.Authorization, "Bearer $readerToken")
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/users") {
            header(HttpHeaders.Authorization, "Bearer $readerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"X","email":"x@t.com","role":"READER"}""")
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/users/any-id") {
            header(HttpHeaders.Authorization, "Bearer $readerToken")
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/users/any-id") {
            header(HttpHeaders.Authorization, "Bearer $readerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"X","email":"x@t.com"}""")
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/users/any-id") {
            header(HttpHeaders.Authorization, "Bearer $readerToken")
        }.status)
    }

    // ── Property 12: Unauthorized access rejection ──────────────

    @Test
    fun `Property 12 - random non-admin roles are rejected`() = runTest {
        val nonAdminRoles = listOf("READER", "NEURAL_ARCHITECT")
        checkAll(PropTestConfig(iterations = 50), Arb.element(nonAdminRoles)) { role ->
            testApplication {
                configureTestApp()
                val token = generateToken(role = role)
                val resp = client.get("/api/users") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.Forbidden, resp.status,
                    "Role $role should be forbidden from /api/users")
            }
        }
    }

    // ── Correct status codes ────────────────────────────────────

    @Test
    fun `POST returns 201 with valid data`() = testApplication {
        configureTestApp()
        val token = generateToken()
        val resp = client.post("/api/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test","email":"test@example.com","role":"READER"}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `DELETE returns 204 on success`() = testApplication {
        val store = InMemoryUserStore()
        configureTestApp(store)
        val token = generateToken(userId = "admin-1")
        store.addUser(User(id = "target-1", name = "Target", email = "t@t.com",
            role = UserRole.READER, status = UserStatus.ACTIVE, createdAt = "2025-01-01T00:00:00Z"))

        val resp = client.delete("/api/users/target-1") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `self-deletion returns 403`() = testApplication {
        val store = InMemoryUserStore()
        configureTestApp(store)
        val token = generateToken(userId = "admin-1")
        store.addUser(User(id = "admin-1", name = "Admin", email = "a@t.com",
            role = UserRole.ADMINISTRATOR, status = UserStatus.ACTIVE, createdAt = "2025-01-01T00:00:00Z"))

        val resp = client.delete("/api/users/admin-1") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(resp.bodyAsText().contains("Cannot delete your own account"))
    }
}

/** Simple in-memory AuditLogStore for testing. */
private class InMemoryAuditLogStore : AuditLogStore {
    private val entries = mutableListOf<AuditLogEntry>()
    override suspend fun append(entry: AuditLogEntry) { entries.add(entry) }
    override suspend fun getRecent(limit: Int) = entries.takeLast(limit)
    override suspend fun getAll() = entries.toList()
}
