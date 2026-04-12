package com.assistant.auth

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMINISTRATOR,
    NEURAL_ARCHITECT,
    READER
}

@Serializable
data class AuthenticatedUser(
    val userId: String,
    val email: String,
    val role: UserRole,
    val projectKey: String,
    val jiraDomain: String
)

@Serializable
sealed class AuthResult {
    @Serializable
    data class Success(
        val user: AuthenticatedUser,
        val jwt: String,
        val projects: List<com.assistant.jira.JiraProject>
    ) : AuthResult()

    @Serializable
    data class Failure(
        val code: Int,
        val message: String
    ) : AuthResult()
}
