package com.assistant.server.auth

import com.assistant.auth.*
import com.assistant.server.config.ServerConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.client.*
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class AuthServiceImpl(
    private val config: ServerConfig,
    private val httpClient: HttpClient
) : AuthService {

    private val invalidatedSessions = ConcurrentHashMap.newKeySet<String>()

    /** Default users for local authentication. */
    private data class UserCredentials(val password: String, val role: UserRole, val email: String)

    private val defaultUsers = mapOf(
        "admin" to UserCredentials("admin123", UserRole.ADMINISTRATOR, "admin@assistant.local"),
        "user" to UserCredentials("user123", UserRole.READER, "user@assistant.local")
    )

    /**
     * Authenticate against the built-in user store.
     * The [email] parameter is reused as the username field.
     */
    override suspend fun authenticate(email: String, password: String): AuthResult {
        val username = email // frontend sends username in the email field
        val creds = defaultUsers[username]
        if (creds == null || creds.password != password) {
            return AuthResult.Failure(code = 401, message = "Invalid username or password")
        }

        val user = AuthenticatedUser(
            userId = username,
            email = creds.email,
            role = creds.role,
            projectKey = "",
            jiraDomain = config.jiraHost
        )

        val jwt = generateJwt(user)
        return AuthResult.Success(user = user, jwt = jwt, projects = emptyList())
    }

    override fun generateJwt(user: AuthenticatedUser): String {
        val now = Date()
        val expiration = Date(now.time + ServerConfig.JWT_EXPIRATION_MS)

        return JWT.create()
            .withIssuer(ServerConfig.JWT_ISSUER)
            .withAudience(ServerConfig.JWT_AUDIENCE)
            .withClaim("user_id", user.userId)
            .withClaim("email", user.email)
            .withClaim("role", user.role.name)
            .withClaim("project_key", user.projectKey)
            .withClaim("jira_domain", user.jiraDomain)
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .sign(config.jwtAlgorithm())
    }

    override fun validateJwt(token: String): AuthenticatedUser? {
        return try {
            val verifier = config.jwtVerifier()
            val decoded = verifier.verify(token)

            val userId = decoded.getClaim("user_id")?.asString() ?: return null
            val email = decoded.getClaim("email")?.asString() ?: return null
            val roleStr = decoded.getClaim("role")?.asString() ?: return null
            val projectKey = decoded.getClaim("project_key")?.asString() ?: return null
            val jiraDomain = decoded.getClaim("jira_domain")?.asString() ?: ""

            // Check if session was invalidated
            if (userId in invalidatedSessions) return null

            val role = try {
                UserRole.valueOf(roleStr)
            } catch (_: IllegalArgumentException) {
                return null
            }

            AuthenticatedUser(
                userId = userId,
                email = email,
                role = role,
                projectKey = projectKey,
                jiraDomain = jiraDomain
            )
        } catch (_: JWTVerificationException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun invalidateSession(userId: String) {
        invalidatedSessions.add(userId)
    }
}
