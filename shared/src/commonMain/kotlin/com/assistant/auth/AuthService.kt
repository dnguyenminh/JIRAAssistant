package com.assistant.auth

/**
 * Authentication service interface.
 * Interface lives in commonMain; JVM implementation uses com.auth0:java-jwt.
 */
interface AuthService {
    /**
     * Authenticate user and create a JWT session.
     * No longer validates Jira credentials — Jira config is managed via Integrations page.
     * Accepts email/password for simple auth, or empty values for auto-login
     * when Jira is already configured in the database.
     */
    suspend fun authenticate(email: String, password: String): AuthResult

    /**
     * Generate a JWT token for the given authenticated user.
     * Token contains user_id, email, role, project_key with 24h expiration.
     */
    fun generateJwt(user: AuthenticatedUser): String

    /**
     * Validate a JWT token and extract the AuthenticatedUser.
     * Returns null if the token is invalid or expired.
     */
    fun validateJwt(token: String): AuthenticatedUser?

    /**
     * Invalidate the session for the given user.
     */
    fun invalidateSession(userId: String)
}
