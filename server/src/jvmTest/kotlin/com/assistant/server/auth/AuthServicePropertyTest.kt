package com.assistant.server.auth

import com.assistant.auth.AuthenticatedUser
import com.assistant.auth.UserRole
import com.assistant.server.config.ServerConfig
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 1: JWT Generation/Validation Round-Trip
 *
 * For any valid AuthenticatedUser (with valid user_id, email, role, project_key),
 * calling generateJwt(user) then validateJwt(token) SHALL return an AuthenticatedUser
 * with the same user_id, email, role, and project_key as the original,
 * and the token must have a 24-hour expiration.
 *
 * **Validates: Requirements 1.7, 10.1, 10.3**
 *
 * Feature: jira-assistant-app, Property 1: JWT Generation/Validation Round-Trip
 */
@OptIn(ExperimentalKotest::class)
class AuthServicePropertyTest {

    private val config = ServerConfig(
        jiraHost = "https://test.atlassian.net",
        aiProviderUrl = "http://localhost:11434",
        dbPath = "./test.db",
        jwtSecret = "test-secret-for-property-testing-minimum-length",
        encryptionKey = "test-encryption-key-for-property-testing",
        port = 8080,
        staticDir = "./static"
    )

    private val authService = AuthServiceImpl(config, HttpClient())

    private val arbUserRole: Arb<UserRole> = Arb.enum<UserRole>()

    private fun arbSafeString(minLength: Int = 1, maxLength: Int = 50): Arb<String> =
        Arb.string(minSize = minLength, maxSize = maxLength, codepoints = Codepoint.alphanumeric())

    private val arbEmail: Arb<String> = Arb.bind(
        arbSafeString(3, 15),
        arbSafeString(3, 10)
    ) { local, domain -> "$local@$domain.com" }

    private val arbAuthenticatedUser: Arb<AuthenticatedUser> = Arb.bind(
        arbSafeString(5, 36),
        arbEmail,
        arbUserRole,
        arbSafeString(2, 10),
        arbSafeString(5, 30)
    ) { userId, email, role, projectKey, jiraDomain ->
        AuthenticatedUser(
            userId = userId,
            email = email,
            role = role,
            projectKey = projectKey,
            jiraDomain = jiraDomain
        )
    }

    @Test
    fun jwtRoundTripPreservesUserFields() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbAuthenticatedUser) { user ->
                val token = authService.generateJwt(user)
                val recovered = authService.validateJwt(token)

                assertNotNull(recovered, "validateJwt should return non-null for a freshly generated token")
                assertEquals(user.userId, recovered!!.userId, "userId must be preserved")
                assertEquals(user.email, recovered.email, "email must be preserved")
                assertEquals(user.role, recovered.role, "role must be preserved")
                assertEquals(user.projectKey, recovered.projectKey, "projectKey must be preserved")
            }
        }
    }

    @Test
    fun jwtTokenHas24HourExpiration() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbAuthenticatedUser) { user ->
                val token = authService.generateJwt(user)

                val decoded = com.auth0.jwt.JWT.decode(token)
                val issuedAt = decoded.issuedAt
                val expiresAt = decoded.expiresAt

                assertNotNull(issuedAt, "Token must have issuedAt claim")
                assertNotNull(expiresAt, "Token must have expiresAt claim")

                val diffMs = expiresAt!!.time - issuedAt!!.time
                val twentyFourHoursMs = 24 * 60 * 60 * 1000L
                assertTrue(
                    diffMs in (twentyFourHoursMs - 1000)..(twentyFourHoursMs + 1000),
                    "Token expiration should be 24 hours, but was ${diffMs}ms"
                )
            }
        }
    }

    @Test
    fun jwtIssuerIsJiraAssistant() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbAuthenticatedUser) { user ->
                val token = authService.generateJwt(user)
                val decoded = com.auth0.jwt.JWT.decode(token)

                assertEquals(ServerConfig.JWT_ISSUER, decoded.issuer, "Issuer must be '${ServerConfig.JWT_ISSUER}'")
            }
        }
    }
}
